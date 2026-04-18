package com.femtendo.kingdombuilder.capability;

import org.jetbrains.annotations.NotNull;

import com.femtendo.kingdombuilder.kingdom.KingdomData;
import com.femtendo.kingdombuilder.kingdom.KingdomManager;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * Item-handler view of a kingdom's vault (System 4 of
 * {@code kingdom_builder_architecture_plan.md}).
 *
 * <h2>Role</h2>
 *
 * This class is the bridge between tech mods (Create, Mekanism, pipes, hoppers,
 * etc.) and the per-kingdom vault stored inside {@link KingdomData}. It is
 * exposed to the world via the {@code ForgeCapabilities.ITEM_HANDLER}
 * capability attached to every Logistics Node block-entity that resolves to
 * this kingdom.
 *
 * <h2>One-way valve semantics</h2>
 *
 * POINTER: {@link #extractItem} always returns {@link ItemStack#EMPTY}. The
 * Logistics Node is designed as a <em>deposit-only</em> endpoint: tech-mod
 * pipes may shove items in, but they cannot siphon the kingdom's stockpile
 * back out. Player-side extraction happens via GUI (future task). This
 * deliberately prevents automated raiding of a kingdom's resources through
 * pipe networks.
 *
 * <h2>Persistence</h2>
 *
 * POINTER: The list we mutate is the same {@link NonNullList} instance held
 * inside {@link KingdomData#getVaultItems()}. There is NO local copy — every
 * commit touches the authoritative store directly. On every real (non-simulate)
 * mutation we call {@link KingdomManager#setDirty()} so the world's
 * DimensionDataStorage flushes the update on the next save tick. Skipping
 * {@code setDirty} would let items drop on server crash.
 *
 * <h2>Capacity</h2>
 *
 * POINTER: {@code getSlotLimit} returns {@link Integer#MAX_VALUE} (per issue
 * spec). This lets a single slot hold effectively unbounded stack counts —
 * think of the vault as a "pile" rather than a shulker-grid. The merge logic
 * in {@link #insertItem} therefore never "caps out" an existing stack; the
 * only reason to overflow into a new slot is when the existing item TYPE does
 * not match.
 */
public class KingdomVaultItemHandler implements IItemHandler {

    /**
     * Total number of slots. Referenced from {@link KingdomData#VAULT_SIZE} so
     * there is a single source of truth shared with the on-disk layout.
     *
     * POINTER: Changing this value without a save migration will shift slot
     * indices on disk and corrupt existing vaults. Leave it tied to
     * {@code KingdomData.VAULT_SIZE}.
     */
    public static final int VAULT_SIZE = KingdomData.VAULT_SIZE;

    /**
     * POINTER: Held as a direct reference — shared with KingdomData. See class
     * javadoc "Persistence" section.
     */
    private final KingdomData kingdomData;

    /**
     * Used ONLY to resolve the KingdomManager for {@link KingdomManager#setDirty}.
     *
     * POINTER: We intentionally hold a {@link ServerLevel} rather than a
     * {@link KingdomManager} reference because the manager is a
     * {@link net.minecraft.world.level.saveddata.SavedData} that is re-looked-up
     * on every call via {@code level.getDataStorage().computeIfAbsent}. Caching
     * the manager would work today but is brittle against future changes that
     * might recreate the SavedData (e.g. level unload/reload). The level
     * reference is stable for the lifetime of any block-entity capability.
     */
    private final ServerLevel level;

    public KingdomVaultItemHandler(KingdomData kingdomData, ServerLevel level) {
        this.kingdomData = kingdomData;
        this.level = level;
    }

    // ------------------------------------------------------------------
    //  IItemHandler — slot metadata
    // ------------------------------------------------------------------

    @Override
    public int getSlots() {
        return VAULT_SIZE;
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= VAULT_SIZE) {
            return ItemStack.EMPTY;
        }
        // POINTER (IItemHandler contract): The returned ItemStack MUST NOT be
        // modified by the caller. NonNullList#get returns a live reference —
        // this is consistent with ItemStackHandler's own behavior. Pipe code
        // in Create/Mekanism/etc. respects this contract.
        return kingdomData.getVaultItems().get(slot);
    }

    @Override
    public int getSlotLimit(int slot) {
        // POINTER: Integer.MAX_VALUE per issue spec. Vault acts as a pile, not
        // a grid of 64-stack shulker slots.
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        // POINTER: Always true — the vault accepts any item. Food-only
        // filtering lives on the Silo (System 9), not here.
        return true;
    }

    // ------------------------------------------------------------------
    //  IItemHandler — mutation
    // ------------------------------------------------------------------

    /**
     * Insert an item into the vault. Strategy: merge into the first compatible
     * existing stack, else place into the first empty slot.
     *
     * <p>POINTER (merge-then-append): The issue spec explicitly calls for
     * "merges with existing stacks then appends". We implement this by walking
     * the list twice when needed — once looking for a compatible existing
     * stack (via {@link ItemHandlerHelper#canItemStacksStack}), and, if the
     * merge filled nothing, a second pass looking for the first empty slot.
     * This is O(N) per insert with N=10000; acceptable because Logistics
     * Nodes see pipe-tick-bounded traffic (a few ops per tick at most).</p>
     *
     * <p>POINTER (simulate): When {@code simulate=true} we compute the return
     * value without ANY list mutation or {@code setDirty} call. Tech-mod pipe
     * networks simulate heavily to decide routing — a failure to respect
     * simulate-only semantics would corrupt the vault silently.</p>
     *
     * <p>POINTER (capacity): Because {@link #getSlotLimit} is
     * {@link Integer#MAX_VALUE} and we don't cap at
     * {@code stack.getMaxStackSize()} (the vault is a pile, not a shulker),
     * a successful merge always absorbs the ENTIRE input stack. The only
     * failure path is "no compatible existing slot AND no empty slot" — i.e.
     * the vault has {@value #VAULT_SIZE} distinct item types already.</p>
     */
    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // The `slot` argument is effectively ignored — the vault treats every
        // slot as interchangeable (merge-then-append semantics). We deliberately
        // do NOT honor the specific slot index the caller asks for because
        // doing so would defeat the pile model and let pipes scatter items
        // across slot 0..N. This is the Forge pattern used by single-purpose
        // vault/backpack implementations (see e.g. Thermal Dynamics' vault).

        NonNullList<ItemStack> items = kingdomData.getVaultItems();

        // --- Pass 1: merge into an existing compatible slot -------------------
        for (int i = 0; i < items.size(); i++) {
            ItemStack existing = items.get(i);
            if (existing.isEmpty()) {
                continue;
            }
            if (ItemHandlerHelper.canItemStacksStack(existing, stack)) {
                if (!simulate) {
                    // In-place grow. existing is a live reference from the
                    // NonNullList — mutating it directly is the canonical
                    // pattern used by ItemStackHandler.
                    existing.grow(stack.getCount());
                    markDirty();
                }
                return ItemStack.EMPTY;
            }
        }

        // --- Pass 2: append to first empty slot --------------------------------
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isEmpty()) {
                if (!simulate) {
                    // Store a copy so the caller can't keep a live handle that
                    // mutates our slot (tech-mod pipe code sometimes holds the
                    // reference for a tick longer than we'd like). copy() is
                    // cheap — ItemStack has minimal field state.
                    items.set(i, stack.copy());
                    markDirty();
                }
                return ItemStack.EMPTY;
            }
        }

        // --- No room: return the input unchanged so callers can route elsewhere.
        // POINTER: Returning the original stack instance (not a copy) signals
        // "nothing was inserted, use your original reference". Pipe code
        // checks ItemStack equality/count to decide next action.
        return stack;
    }

    /**
     * Extraction is disabled — this is a one-way valve. Always empty.
     *
     * POINTER: Do NOT add extraction logic here without a design review.
     * Exposing extract via capability would let hostile kingdoms' pipe
     * networks siphon a rival's vault through a single misplaced Logistics
     * Node. Player extraction must go through a GUI with owner checks (future
     * task — probably tied to Settlement Hearth GUI).
     */
    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------
    //  Internal helpers
    // ------------------------------------------------------------------

    /**
     * POINTER: Central mutation-hook — every real (non-simulate) change routes
     * through here. Currently only calls {@link KingdomManager#setDirty}. If a
     * later system needs to broadcast vault changes to UIs, advisors, or the
     * kingdom owner's client, hook into THIS method rather than each mutation
     * site.
     */
    private void markDirty() {
        KingdomManager.get(level).setDirty();
    }
}
