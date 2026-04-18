package com.femtendo.kingdombuilder.kingdom;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

/**
 * Plain-Old-Java-Object data carrier representing a single player's kingdom.
 *
 * POINTER (System 2 of kingdom_builder_architecture_plan.md): This class is
 * INTENTIONALLY NOT a {@link net.minecraft.world.level.saveddata.SavedData}.
 * Persistence lives in {@link KingdomManager}; this object is just the value
 * type stored inside that manager's {@code Map<UUID, KingdomData>}. Keeping it
 * as a POJO means we can freely construct test instances, copy them, and pass
 * them to clients without worrying about a {@code setDirty()} ladder.
 *
 * POINTER: Mutating any field in this object does NOT automatically mark the
 * owning {@link KingdomManager} as dirty. Callers MUST invoke
 * {@code KingdomManager.get(level).setDirty()} after touching a setter, or use
 * the manager's higher-level API (which already calls {@code setDirty()} for
 * you). This was deliberate — the alternative was a back-pointer from
 * KingdomData → KingdomManager, which complicates serialization and risks
 * cycles. See {@link KingdomManager} class javadoc for the rationale.
 *
 * POINTER (Forge 1.21.1 / TECH ALIGNMENT note from issue): {@link NbtUtils}
 * remains the canonical helper for {@link BlockPos} (de)serialization on the
 * SavedData side of the world. Data Components are an item-level concept and
 * do NOT replace NBT for persistent server-side world data. Keep this class
 * NBT-based even as item-tier code migrates to Components.
 */
public class KingdomData {

    // --- NBT keys (centralized to keep save() and load() in lock-step) ----
    private static final String KEY_OWNER_UUID = "ownerUUID";
    private static final String KEY_CORE_POS = "corePos";
    private static final String KEY_DIMENSION = "dimensionKey";
    private static final String KEY_NAME = "kingdomName";
    private static final String KEY_VAULT = "vault";

    /**
     * Size of the per-kingdom vault backing store (System 4).
     *
     * POINTER: 10000 slots per the issue spec. Empty slots serialize to nothing
     * via {@link ContainerHelper#saveAllItems} so on-disk cost is proportional
     * to occupied slots, not VAULT_SIZE. The backing {@link NonNullList} does
     * hold 10000 {@code ItemStack.EMPTY} references in RAM — that's ~80 KB per
     * kingdom on a 64-bit JVM, negligible compared to chunk state.
     *
     * POINTER: Referenced by
     * {@link com.femtendo.kingdombuilder.capability.KingdomVaultItemHandler} as
     * its slot count. Do NOT change this value without a save migration — the
     * slot index of each stored stack is the key on disk.
     */
    public static final int VAULT_SIZE = 10000;

    // POINTER: ownerUUID is the *immutable* identity of a kingdom. There is no
    // setter on purpose — re-assigning a kingdom to a different player would
    // also require re-keying the KingdomManager map, which is a different
    // operation (claim/abandon, not "rename owner"). A future "transfer
    // kingdom" feature should call abandonKingdom() then claimKingdom().
    private final UUID ownerUUID;

    // corePos is the block position of the SettlementHearth that anchors the
    // kingdom (System 3 will place it on right-click). It is mutable so admin
    // commands or future "move capital" features can relocate it without
    // re-creating the data record. KingdomManager.setDirty() must be called
    // by the mutator.
    private BlockPos corePos;

    // dimensionKey is the stringified ResourceLocation of the world the core
    // sits in (e.g. "minecraft:overworld"). Stored as a String so we don't
    // have to drag a ResourceKey<Level>/ResourceLocation registry lookup
    // through every save/load roundtrip. Compared as a plain string in
    // KingdomManager#getKingdomAtPos.
    // POINTER: Once set on creation we treat this as immutable; cross-dimension
    // kingdom moves are out of scope for the current design.
    private final String dimensionKey;

    // Display name shown in HUDs, name-tags, and chat messages. Mutable.
    private String kingdomName;

    /**
     * Backing store for the kingdom vault (System 4). Exposed to the tech-mod
     * world as an {@link net.minecraftforge.items.IItemHandler} via
     * {@link com.femtendo.kingdombuilder.capability.KingdomVaultItemHandler}
     * attached to every Logistics Node BE in the kingdom's dimension.
     *
     * POINTER: The handler holds a DIRECT reference to this list — it mutates
     * the list in place on insert. This is intentional (no copy-on-write), so
     * multiple Logistics Nodes in the same kingdom share one authoritative
     * vault. Any code mutating this list MUST route through
     * {@code KingdomVaultItemHandler} so we go through a consistent
     * {@link KingdomManager#setDirty()} call.
     *
     * POINTER: Not final — re-assigned during {@link #load} to swap in the
     * deserialized list.
     */
    private NonNullList<ItemStack> vaultItems = NonNullList.withSize(VAULT_SIZE, ItemStack.EMPTY);

    public KingdomData(UUID ownerUUID, BlockPos corePos, String dimensionKey, String kingdomName) {
        // Defensive copy of BlockPos: BlockPos.MutableBlockPos is a subclass and
        // callers occasionally hand one in. Storing the mutable variant would
        // cause silent corruption if the caller later mutates it. immutable() is
        // a no-op on already-immutable BlockPos instances, so this is cheap.
        this.ownerUUID = ownerUUID;
        this.corePos = corePos.immutable();
        this.dimensionKey = dimensionKey;
        this.kingdomName = kingdomName;
    }

    // --- Getters -----------------------------------------------------------

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public BlockPos getCorePos() {
        return corePos;
    }

    public String getDimensionKey() {
        return dimensionKey;
    }

    public String getKingdomName() {
        return kingdomName;
    }

    // --- Setters (mutators MUST be paired with KingdomManager#setDirty) ----

    public void setCorePos(BlockPos corePos) {
        // POINTER: see ctor comment — defensive immutable() copy guards against
        // mutable BlockPos aliasing bugs.
        this.corePos = corePos.immutable();
    }

    public void setKingdomName(String kingdomName) {
        this.kingdomName = kingdomName;
    }

    /**
     * Live reference to the vault backing store (System 4).
     *
     * POINTER: Returned reference is NOT copied — callers must not mutate it
     * outside of {@link com.femtendo.kingdombuilder.capability.KingdomVaultItemHandler}.
     * We expose it directly so the handler can implement O(1) slot lookups;
     * wrapping in {@code unmodifiableList} would force a copy on every
     * handler-instance construction (one per Logistics Node BE, potentially
     * hundreds per kingdom).
     */
    public NonNullList<ItemStack> getVaultItems() {
        return vaultItems;
    }

    // --- NBT (de)serialization --------------------------------------------

    /**
     * Serialize this kingdom into the supplied CompoundTag and return it.
     *
     * POINTER: Returns the same tag instance for fluent chaining inside
     * KingdomManager#save (where each kingdom is appended to a ListTag of
     * compounds). Do NOT change to {@code void} without updating the manager's
     * save loop.
     *
     * POINTER (Forge 1.21.1): {@link NbtUtils#writeBlockPos(BlockPos)} returns
     * a {@link net.minecraft.nbt.Tag} (specifically an IntArrayTag in the
     * current vanilla impl). It is stored as a child tag under KEY_CORE_POS,
     * NOT merged into the parent compound. Reading back uses
     * {@link NbtUtils#readBlockPos(CompoundTag, String)} which returns
     * {@code Optional<BlockPos>}.
     *
     * POINTER (System 4): {@link HolderLookup.Provider} is required for
     * ItemStack (de)serialization in 1.21.1 because ItemStack now uses
     * codec-backed NBT that can reference registry holders (enchantments,
     * block components, etc.). It is forwarded from
     * {@link KingdomManager#save(CompoundTag, HolderLookup.Provider)} which
     * receives it from vanilla's SavedData pipeline.
     */
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putUUID(KEY_OWNER_UUID, ownerUUID);
        tag.put(KEY_CORE_POS, NbtUtils.writeBlockPos(corePos));
        tag.putString(KEY_DIMENSION, dimensionKey);
        tag.putString(KEY_NAME, kingdomName);

        // POINTER (System 4): Vault items serialize via ContainerHelper which
        // skips empty slots — so a mostly-empty 10k-slot vault costs only a few
        // bytes on disk. ContainerHelper#saveAllItems takes a HolderLookup.Provider
        // because ItemStack serialization in 1.21.1 is codec-backed and needs
        // registry access (components may reference block/item/enchant holders).
        // If lookup is null (shouldn't happen from SavedData), we skip vault
        // persistence to avoid crashing the whole save.
        if (lookup != null) {
            CompoundTag vaultTag = new CompoundTag();
            ContainerHelper.saveAllItems(vaultTag, vaultItems, lookup);
            tag.put(KEY_VAULT, vaultTag);
        }
        return tag;
    }

    /**
     * Deserialize a KingdomData from a CompoundTag previously produced by
     * {@link #save(CompoundTag)}.
     *
     * POINTER: Returns null if the tag is missing required fields. Callers in
     * KingdomManager#load will skip and log a warning rather than crash the
     * entire save load — losing one orphaned kingdom record is preferable to
     * losing the whole save.
     *
     * Edge cases considered:
     *  - Missing ownerUUID → return null (record is unrecoverable; without an
     *    owner UUID it can't be keyed in the manager map).
     *  - Missing/corrupt corePos → return null (a kingdom with no anchor is
     *    meaningless; better to drop than to default to (0,0,0) and corrupt
     *    spawn).
     *  - Missing dimensionKey → default to "minecraft:overworld". Old-format
     *    saves from earlier development branches may not have this field.
     *  - Missing kingdomName → default to "Kingdom of " + first 8 chars of UUID
     *    so the player still has *something* to see.
     */
    public static KingdomData load(CompoundTag tag, HolderLookup.Provider lookup) {
        if (!tag.hasUUID(KEY_OWNER_UUID)) {
            return null;
        }
        UUID ownerUUID = tag.getUUID(KEY_OWNER_UUID);

        BlockPos corePos = NbtUtils.readBlockPos(tag, KEY_CORE_POS).orElse(null);
        if (corePos == null) {
            return null;
        }

        String dimensionKey = tag.contains(KEY_DIMENSION)
                ? tag.getString(KEY_DIMENSION)
                : "minecraft:overworld";

        String kingdomName = tag.contains(KEY_NAME)
                ? tag.getString(KEY_NAME)
                : "Kingdom of " + ownerUUID.toString().substring(0, 8);

        KingdomData data = new KingdomData(ownerUUID, corePos, dimensionKey, kingdomName);

        // POINTER (System 4): Vault items load into the already-constructed
        // NonNullList (ContainerHelper#loadAllItems mutates in place). A missing
        // KEY_VAULT tag is expected for legacy saves from before System 4 —
        // just leave the default all-empty list. Null lookup also skips the
        // load (defensive; SavedData always provides one).
        if (lookup != null && tag.contains(KEY_VAULT)) {
            ContainerHelper.loadAllItems(tag.getCompound(KEY_VAULT), data.vaultItems, lookup);
        }

        return data;
    }
}
