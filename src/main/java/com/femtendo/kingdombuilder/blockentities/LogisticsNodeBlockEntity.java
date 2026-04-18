package com.femtendo.kingdombuilder.blockentities;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.femtendo.kingdombuilder.capability.KingdomVaultItemHandler;
import com.femtendo.kingdombuilder.kingdom.KingdomData;
import com.femtendo.kingdombuilder.kingdom.KingdomManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

/**
 * BlockEntity for the Logistics Node (System 4 of
 * {@code kingdom_builder_architecture_plan.md}).
 *
 * <h2>Role</h2>
 *
 * <p>Pure capability proxy. This BE stores NO per-block inventory state — its
 * only job is to forward the {@code ForgeCapabilities.ITEM_HANDLER} request
 * from adjacent tech-mod pipes (Create, Mekanism, hoppers) to the owning
 * kingdom's shared vault via {@link KingdomVaultItemHandler}.</p>
 *
 * <h2>Capability resolution</h2>
 *
 * <p>POINTER (tech alignment, Forge 1.21.1): The issue template referenced
 * NeoForge's Block Capabilities API (where providers are registered and
 * queried directly). We are on <strong>Forge 1.21.1</strong>, which still uses
 * the classic {@link Capability} + {@link LazyOptional} pattern. Confirmed by
 * inspecting {@code net.minecraftforge.common.capabilities.ForgeCapabilities}
 * and {@code net.minecraftforge.common.util.LazyOptional} in the mapped Forge
 * jar. Do NOT "modernize" this code to NeoForge's API — it will not compile.</p>
 *
 * <h2>Ownership lookup</h2>
 *
 * <p>The node is NOT bound to a kingdom at placement time. Instead it lazily
 * scans {@link KingdomManager#getAllKingdoms()} on the first capability query
 * and caches the resulting handler. See {@link #buildVaultCapability} for the
 * exact heuristic.</p>
 *
 * <h2>Lifecycle / memory</h2>
 *
 * <p>POINTER (mandatory): {@link #setRemoved} MUST call
 * {@link LazyOptional#invalidate}. Tech-mod pipe networks hold onto the
 * {@code LazyOptional} returned from {@code getCapability} across ticks; if we
 * don't invalidate when the block is broken or the chunk unloads, those
 * networks keep dispatching to a dead handler and the GC cannot collect us.
 * This is the #1 capability leak in Forge mods.</p>
 */
public class LogisticsNodeBlockEntity extends BlockEntity {

    /**
     * Cached capability handle. Lazily populated on first
     * {@link #getCapability} call via {@link #buildVaultCapability}. Reset to
     * {@link LazyOptional#empty()} after {@link #invalidateCaps()} fires so a
     * subsequent query (e.g. after a kingdom is claimed AFTER the node was
     * placed) can retry.
     *
     * POINTER: {@code LazyOptional.empty()} is a sentinel; calling
     * {@code .invalidate()} on it is a safe no-op per Forge impl.
     */
    private LazyOptional<IItemHandler> vaultCapability = LazyOptional.empty();

    public LogisticsNodeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOGISTICS_NODE_BE.get(), pos, state);
    }

    // ------------------------------------------------------------------
    //  Capability proxy
    // ------------------------------------------------------------------

    /**
     * Forge 1.21.1 capability entry-point. Only ITEM_HANDLER is advertised —
     * any other capability falls through to super, which returns empty.
     *
     * <p>POINTER (side parameter ignored): The vault exposes the same handler
     * on every face. Kingdom vault access is semantic, not geometric —
     * restricting to specific faces would invite grief builds that "seal" a
     * node. The side argument is the standard Forge API but we deliberately
     * ignore it; {@code @Nullable Direction side} may also be null (face-less
     * query from BlockCapabilityCache-style callers).</p>
     *
     * <p>POINTER (server-only gating): Tech-mod pipes only tick server-side
     * so we gate the lazy build on {@code level instanceof ServerLevel}. A
     * client-side query returns the current (possibly empty) LazyOptional
     * without attempting to resolve a kingdom — the client has no
     * {@link KingdomManager}. Clients generally do not call
     * {@code getCapability} for item handlers anyway.</p>
     */
    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            if (!vaultCapability.isPresent() && level instanceof ServerLevel serverLevel) {
                vaultCapability = buildVaultCapability(serverLevel);
            }
            return vaultCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    /**
     * Linear scan of registered kingdoms to find which one this node belongs
     * to. Matches by dimension key.
     *
     * <p>POINTER (heuristic, MVP-only): Per the issue, the current lookup is
     * "linear scan of {@link KingdomManager#getAllKingdoms()} to find owning
     * kingdom by dimKey". When System 12 (dynamic territory) lands, this must
     * be replaced with the planned
     * {@code KingdomManager.getOwnerOfChunk(ChunkPos, String)} lookup so that
     * multiple kingdoms in the same dimension each own their slice of space.
     * For now — with at most one kingdom per dimension in playtests — matching
     * by dimKey is sufficient.</p>
     *
     * <p>POINTER (tie-break for future-proofing): If multiple kingdoms already
     * coexist in the same dimension (e.g. admin-seeded test worlds), we pick
     * the kingdom whose core is closest (Euclidean squared distance). This is
     * stable and deterministic. Still a heuristic — System 12 replaces it.</p>
     *
     * <p>POINTER (empty LazyOptional vs missing): If NO kingdom matches
     * (dimension has no claims yet), we return {@link LazyOptional#empty}.
     * Tech-mod pipes treat that identically to "no capability here" and route
     * items elsewhere. No error spam.</p>
     */
    private LazyOptional<IItemHandler> buildVaultCapability(ServerLevel serverLevel) {
        KingdomManager manager = KingdomManager.get(serverLevel);
        String dimKey = serverLevel.dimension().location().toString();

        KingdomData best = null;
        double bestDistSq = Double.MAX_VALUE;
        BlockPos here = getBlockPos();

        for (KingdomData data : manager.getAllKingdoms()) {
            if (!data.getDimensionKey().equals(dimKey)) {
                continue;
            }
            double distSq = data.getCorePos().distSqr(here);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = data;
            }
        }

        if (best == null) {
            return LazyOptional.empty();
        }

        final KingdomData resolved = best;
        // POINTER (LazyOptional.of contract): The supplier MUST return the
        // same IItemHandler instance each time it is called (vanilla contract).
        // KingdomVaultItemHandler wraps the live KingdomData reference, so
        // handing out a fresh instance each call would still be semantically
        // correct — but LazyOptional.of memoizes the first result anyway, so
        // it's a moot distinction.
        return LazyOptional.of(() -> new KingdomVaultItemHandler(resolved, serverLevel));
    }

    // ------------------------------------------------------------------
    //  Lifecycle — capability invalidation
    // ------------------------------------------------------------------

    /**
     * Called by Minecraft when the BE is removed (block broken, chunk unload,
     * replaced by another block, etc.).
     *
     * <p>POINTER: This is the critical leak-prevention hook. See class javadoc
     * "Lifecycle / memory" section. Invalidating BEFORE super ensures that
     * capability consumers see the invalidation in the same tick as our
     * removal — post-super, {@code level} becomes null-ish and timing is
     * fiddlier.</p>
     */
    @Override
    public void setRemoved() {
        super.setRemoved();
        vaultCapability.invalidate();
    }

    /**
     * Called by Forge when a capability-scope event (chunk unload, dimension
     * unload, or direct invalidation) occurs. We override to reset the cached
     * capability so a subsequent reload reconstructs it.
     *
     * <p>POINTER: {@link #invalidateCaps} fires more often than
     * {@link #setRemoved} — notably on chunk unload when the BE itself is not
     * destroyed. Forge's default implementation invalidates all registered
     * capabilities, but we hold our own LazyOptional outside that registry,
     * so we must invalidate it explicitly here too.</p>
     */
    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        vaultCapability.invalidate();
    }

    /**
     * Reset the cached capability so the next {@code getCapability} call
     * re-resolves. Called by Forge on chunk reload.
     *
     * <p>POINTER: This is the symmetric counterpart to {@link #invalidateCaps}.
     * Forge calls it to "wake up" a BE whose caps were previously invalidated.
     * Default super impl re-registers attached caps; ours re-inits the lazy
     * field so the next pipe query triggers a fresh kingdom lookup (the owning
     * kingdom may have been claimed AFTER the node was placed).</p>
     */
    @Override
    public void reviveCaps() {
        super.reviveCaps();
        vaultCapability = LazyOptional.empty();
    }

    // ------------------------------------------------------------------
    //  Convenience accessor for future systems
    // ------------------------------------------------------------------

    /**
     * POINTER: Exposed for potential future callers (e.g. a debug tool or an
     * admin command showing which kingdom this node currently resolves to).
     * Not used by the capability path itself. Returns null if no kingdom
     * matches or the level is client-side.
     */
    @Nullable
    public KingdomData resolveOwningKingdom() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        KingdomManager manager = KingdomManager.get(serverLevel);
        String dimKey = serverLevel.dimension().location().toString();
        KingdomData best = null;
        double bestDistSq = Double.MAX_VALUE;
        BlockPos here = getBlockPos();
        for (KingdomData data : manager.getAllKingdoms()) {
            if (!data.getDimensionKey().equals(dimKey)) continue;
            double distSq = data.getCorePos().distSqr(here);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = data;
            }
        }
        return best;
    }
}
