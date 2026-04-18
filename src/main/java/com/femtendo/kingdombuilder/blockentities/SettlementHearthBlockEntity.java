package com.femtendo.kingdombuilder.blockentities;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockEntity for the Settlement Hearth — the anchor block that turns a single
 * in-world location into a Kingdom Core (System 3 of
 * {@code kingdom_builder_architecture_plan.md}).
 *
 * <p>The authoritative source of kingdom ownership lives in
 * {@link com.femtendo.kingdombuilder.kingdom.KingdomManager}. This BE only
 * caches the claiming player's UUID for <em>display / query convenience</em>
 * (e.g. name-plate text, tool-tip, future GUI). Do NOT rely on this field as
 * the source of truth — always consult {@code KingdomManager.getKingdomAtPos}
 * when determining whether a block is "owned" by a kingdom.</p>
 *
 * <p>POINTER: Why a separate field instead of always looking up the kingdom by
 * {@code pos}? Two reasons:</p>
 * <ol>
 *   <li>A hearth can persist after its registry entry has been deleted (admin
 *       {@code /kingdom abandon} or save corruption). Keeping a local ownerUUID
 *       lets us show "formerly owned by X" rather than a blank.</li>
 *   <li>Avoids a ServerLevel round-trip for every single render/tooltip query.</li>
 * </ol>
 *
 * <p>POINTER (next agent): When the Settlement Hearth needs a GUI (currently
 * out of scope per System 3 acceptance criteria), register a MenuProvider via
 * the block's {@code getMenuProvider} and wire up a new AbstractContainerMenu
 * in {@code inventory/ModMenus.java}.</p>
 */
public class SettlementHearthBlockEntity extends BlockEntity {

    // NBT keys — centralized so save/load stay in lock-step.
    private static final String KEY_OWNER_UUID = "ownerUUID";

    /**
     * UUID of the player whose right-click created this hearth's kingdom.
     * Nullable because (a) the BE spawns with the block before any claim
     * happens, and (b) legacy saves from earlier development may lack it.
     */
    @Nullable
    private UUID ownerUUID;

    public SettlementHearthBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SETTLEMENT_HEARTH_BE.get(), pos, state);
    }

    /**
     * Set the claiming player after a successful {@code claimKingdom} call.
     *
     * POINTER: Callers MUST invoke this from a server-side context (typically
     * {@code SettlementHearthBlock#useWithoutItem}) and then call
     * {@link #setChanged()} to mark the chunk dirty so the UUID is written to
     * disk. We call {@code setChanged()} here too so callers can't forget.
     */
    public void setOwnerUUID(@Nullable UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
        setChanged();
    }

    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    // ------------------------------------------------------------------
    //  NBT (de)serialization — Forge 1.21.1 signatures
    // ------------------------------------------------------------------
    //
    // POINTER (Forge 1.21.1): The method pair is
    // {@code saveAdditional(CompoundTag, HolderLookup.Provider)} and
    // {@code loadAdditional(CompoundTag, HolderLookup.Provider)}. The old
    // 1.20-style names {@code addAdditionalSaveData} / {@code readAdditionalSaveData}
    // are entity-only; BlockEntity uses the *-Additional variants. Super
    // MUST be called to preserve forge capability data.

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        if (ownerUUID != null) {
            tag.putUUID(KEY_OWNER_UUID, ownerUUID);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        if (tag.hasUUID(KEY_OWNER_UUID)) {
            this.ownerUUID = tag.getUUID(KEY_OWNER_UUID);
        } else {
            this.ownerUUID = null;
        }
    }
}
