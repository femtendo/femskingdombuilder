package com.femtendo.kingdombuilder.blockentities;

import java.util.EnumSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Iron Tube block entity. Tracks which faces have been manually force-disconnected
 * by the Wrench (System 11) and is future-proofed for the facade + connection
 * inference logic that System 8 will layer on top.
 *
 * <h2>System 11 scope (what this file CURRENTLY carries)</h2>
 *
 * POINTER: This BE was a one-line stub until System 11 needed the Wrench
 * integration. The minimal {@code forcedDisconnects} {@link EnumSet} + save/load
 * was added here (not in System 8) because:
 *   - The Wrench's acceptance criterion ("Right-clicking an Iron Tube face with
 *     Wrench toggles force-disconnect for that face") requires it.
 *   - The data has bitmask NBT serialization as specified in the architecture
 *     plan's System 8 section — adding it now lets System 8 keep the exact same
 *     on-disk format without a migration.
 *
 * System 8 is still responsible for:
 *   - {@code connectedFaces} auto-inference (neighbour scan → EnumSet&lt;Direction&gt;)
 *   - {@code facades} EnumMap&lt;Direction, BlockState&gt; for visual camouflage
 *   - The {@link net.minecraft.world.level.block.entity.BlockEntityRenderer}
 *     that draws the X-ray pipe overlay when the player holds the Wrench.
 *
 * <h2>System 8 expectations (DO NOT break in future edits)</h2>
 *
 * POINTER: When mutating facade state (System 8) you MUST call
 * {@code level.sendBlockUpdated(pos, old, new, flags)} so the client re-bakes
 * the facade-aware baked model; {@code setChanged()} alone will NOT trigger a
 * client-side model re-bake. The current {@link #toggleForcedDisconnect(Direction)}
 * uses {@code setChanged()} + {@code level.sendBlockUpdated(...)} together — the
 * update packet propagates the forcedDisconnects state to the client so the
 * Wrench X-ray overlay colour (red = force-disconnected, white = active) stays
 * accurate without a custom packet.
 */
public class IronTubeBlockEntity extends BlockEntity {

    private static final String KEY_FORCED_DISCONNECTS = "forcedDisconnects";

    /**
     * Faces the player has manually forced OPEN (no pipe connection) with the
     * Wrench. EnumSet is the canonical container for {@link Direction} sets in
     * MC modding — O(1) ops backed by a bitmask internally.
     *
     * POINTER: This is an OVERRIDE layer on top of whatever System 8's
     * neighbour-inference logic decides. "Face X is connected" is computed as:
     *   {@code connectedFaces.contains(X) && !forcedDisconnects.contains(X)}
     * The Wrench toggles the forcedDisconnects membership, never the
     * connectedFaces membership. This mirrors tech-mod conventions (Create,
     * Mekanism, Thermal) where the wrench is an exception-layer over
     * auto-connect.
     */
    private final EnumSet<Direction> forcedDisconnects = EnumSet.noneOf(Direction.class);

    public IronTubeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IRON_TUBE_BE.get(), pos, state);
    }

    // ------------------------------------------------------------------
    //  Wrench API (System 11)
    // ------------------------------------------------------------------

    /**
     * Toggle the force-disconnect flag for a single face.
     *
     * <p>Called from {@link com.femtendo.kingdombuilder.items.WrenchItem#useOn}.
     * If the face was force-disconnected, clear it; otherwise set it. After the
     * mutation this method marks the BE dirty AND dispatches a block-update so
     * the client side of the pipe network and the X-ray renderer (System 8) see
     * the change without a custom packet.
     *
     * POINTER: sendBlockUpdated is called with {@code Block.UPDATE_ALL} (3).
     * UPDATE_CLIENTS (2) alone would reach the client but not trigger neighbor
     * updates on the server — we want both because Create/Mekanism pipes hook
     * neighbor updates to re-route their graphs, and toggling a tube face is a
     * graph-changing event.
     *
     * @param face the face whose force-disconnect state should be toggled.
     *             Must be non-null.
     * @return {@code true} if the face is now force-disconnected, {@code false}
     *         if it was cleared.
     */
    public boolean toggleForcedDisconnect(Direction face) {
        boolean nowDisconnected;
        if (forcedDisconnects.contains(face)) {
            forcedDisconnects.remove(face);
            nowDisconnected = false;
        } else {
            forcedDisconnects.add(face);
            nowDisconnected = true;
        }
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            // POINTER: sendBlockUpdated(pos, oldState, newState, flags) with the
            // same state for old and new is the canonical "please resync this
            // block entity to clients" call when block state hasn't changed but
            // BE data has. UPDATE_ALL covers both client resync and neighbor
            // updates. Required for System 8's X-ray renderer to read fresh
            // forcedDisconnects after a Wrench click.
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
        return nowDisconnected;
    }

    /**
     * Read-only check for a single face.
     *
     * <p>POINTER: Used by System 8's renderer to colour X-ray strands
     * (red if disconnected, white if connected). Do NOT return a live view of
     * the EnumSet — callers must not mutate the internal state except through
     * {@link #toggleForcedDisconnect(Direction)}.
     */
    public boolean isFaceForcedDisconnected(Direction face) {
        return forcedDisconnects.contains(face);
    }

    /**
     * Defensive copy of the disconnected faces for read-only consumers (debug
     * commands, renderers that want to iterate, etc.).
     */
    public Set<Direction> getForcedDisconnectsView() {
        return EnumSet.copyOf(forcedDisconnects);
    }

    // ------------------------------------------------------------------
    //  NBT persistence (1.21.1 shape: saveAdditional / loadAdditional with
    //  HolderLookup.Provider)
    // ------------------------------------------------------------------

    /**
     * POINTER: We serialise the EnumSet as a 6-bit integer bitmask. The
     * architecture plan's System 8 section explicitly calls for this format.
     * Keeping the same format now means System 8 can layer facades + connections
     * onto this BE without a save migration. Layout: bit N corresponds to
     * {@code Direction.values()[N]} (down=0, up=1, north=2, south=3, west=4,
     * east=5) — matching {@link Direction#get3DDataValue()}.
     */
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        int mask = 0;
        for (Direction d : forcedDisconnects) {
            mask |= (1 << d.get3DDataValue());
        }
        tag.putInt(KEY_FORCED_DISCONNECTS, mask);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        forcedDisconnects.clear();
        if (tag.contains(KEY_FORCED_DISCONNECTS)) {
            int mask = tag.getInt(KEY_FORCED_DISCONNECTS);
            for (Direction d : Direction.values()) {
                if ((mask & (1 << d.get3DDataValue())) != 0) {
                    forcedDisconnects.add(d);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  Client-sync packet hooks
    // ------------------------------------------------------------------

    /**
     * Include the forcedDisconnects bitmask in the initial chunk-load payload so
     * newly-loaded clients see the correct Wrench state without waiting for the
     * first block update.
     *
     * POINTER: Without this override, chunks loaded AFTER a Wrench click would
     * render the X-ray overlay with stale data until the next toggle. System 8
     * MUST preserve this call (and extend it with connectedFaces/facades) when
     * it fleshes out the BE.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        CompoundTag tag = super.getUpdateTag(lookup);
        saveAdditional(tag, lookup);
        return tag;
    }

    /**
     * Paired with {@link #getUpdateTag} — applied on the client when the chunk
     * payload arrives. {@code super.handleUpdateTag} routes through
     * {@link #loadAdditional}, which we've already implemented.
     */
    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider lookup) {
        super.handleUpdateTag(tag, lookup);
    }
}
