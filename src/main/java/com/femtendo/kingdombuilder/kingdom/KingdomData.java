package com.femtendo.kingdombuilder.kingdom;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

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
     */
    public CompoundTag save(CompoundTag tag) {
        tag.putUUID(KEY_OWNER_UUID, ownerUUID);
        tag.put(KEY_CORE_POS, NbtUtils.writeBlockPos(corePos));
        tag.putString(KEY_DIMENSION, dimensionKey);
        tag.putString(KEY_NAME, kingdomName);
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
    public static KingdomData load(CompoundTag tag) {
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

        return new KingdomData(ownerUUID, corePos, dimensionKey, kingdomName);
    }
}
