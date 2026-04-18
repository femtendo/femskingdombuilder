package com.femtendo.kingdombuilder.blueprint;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

/**
 * Plain-Old-Java-Object describing a single blueprint zone placed in the world.
 *
 * <p>A zone is an AABB footprint (minPos .. maxPos) that a kingdom has marked
 * for construction via the Zoning Tool (System 11). Builder NPCs (System 10 /
 * BuilderActivity) pull {@link BuildTask}s that fall inside the zone, and the
 * {@link com.femtendo.kingdombuilder.events.KingdomBlockEvents} subscriber
 * (System 6) uses {@link #contains(BlockPos)} to decide whether a broken block
 * should downgrade the zone's integrity.
 *
 * <h2>Architectural intent (System 5 of kingdom_builder_architecture_plan.md)</h2>
 *
 * POINTER: This class is INTENTIONALLY NOT a
 * {@link net.minecraft.world.level.saveddata.SavedData}. Persistence lives in
 * {@link BlueprintRegistry}; ZoneData is just the value type stored in the
 * registry's {@code Map<UUID, ZoneData>}. Mirrors the KingdomData/KingdomManager
 * split from System 2 — see {@link com.femtendo.kingdombuilder.kingdom.KingdomData}
 * class javadoc for the rationale (avoids circular back-references, keeps
 * test instances trivially constructible).
 *
 * POINTER (TECH ALIGNMENT / Forge 1.21.1): {@link NbtUtils#writeBlockPos} and
 * {@link NbtUtils#readBlockPos} are still the canonical helpers for SavedData
 * world-layer persistence on 1.21.1. Data Components (the 1.21.1 modernization
 * the issue flagged) only apply to item stacks — the item-tier Zoning Tool /
 * Wrench (System 11) should use Components to store drag state, but THIS class
 * and {@link BlueprintRegistry} remain NBT-based because SavedData has no
 * Components equivalent. Do not "modernize" this into a DataComponentType.
 *
 * POINTER: Mutating any field on this object does NOT automatically mark the
 * owning {@link BlueprintRegistry} as dirty. Callers MUST invoke
 * {@code BlueprintRegistry.get(level).setDirty()} after touching a setter, or
 * go through the registry's higher-level API (which calls {@code setDirty()}
 * for you). Same contract as KingdomData/KingdomManager.
 */
public class ZoneData {

    // --- NBT keys (centralized to keep save() and load() in lock-step) ----
    private static final String KEY_ZONE_ID = "zoneId";
    private static final String KEY_OWNER_UUID = "kingdomOwnerUUID";
    private static final String KEY_MIN_POS = "minPos";
    private static final String KEY_MAX_POS = "maxPos";
    private static final String KEY_BLUEPRINT_ID = "blueprintId";
    private static final String KEY_COMPLETED = "completed";
    private static final String KEY_INTEGRITY = "integrityState";

    /**
     * Tri-state integrity ladder maintained by
     * {@link com.femtendo.kingdombuilder.events.KingdomBlockEvents} (System 6).
     *
     * <ul>
     *   <li>{@link #COMPLETE} — zone has finished construction and all blocks
     *       are intact. Contributes to {@code InfluenceManager.calculateScore}
     *       (System 12).</li>
     *   <li>{@link #DAMAGED} — at least one block inside the footprint has been
     *       broken or destroyed by an explosion. Stops contributing to
     *       influence; builder NPCs should prioritize repair.</li>
     *   <li>{@link #DESTROYED} — the footprint has been catastrophically lost
     *       (reserved for future use; currently unused by the block-break
     *       handler which only issues DAMAGED).</li>
     * </ul>
     *
     * POINTER: The enum is ORDERED by severity. If you add intermediate tiers
     * (e.g. PARTIAL_DAMAGE), insert them in ascending severity order so
     * comparative checks via {@code ordinal()} remain meaningful.
     */
    public enum ZoneIntegrityState {
        COMPLETE,
        DAMAGED,
        DESTROYED
    }

    // POINTER: zoneId is the *immutable* identity of a zone — it is the key
    // used in BlueprintRegistry.zones. There is no setter. If you need to
    // "re-key" a zone (e.g. during an upgrade from tier 1 → tier 2 housing,
    // System 12), remove the old zone and add a new one with a fresh UUID.
    private final UUID zoneId;

    // POINTER: kingdomOwnerUUID is the stable key linking a zone back to its
    // KingdomData entry in KingdomManager. Immutable by design — zones do not
    // transfer between kingdoms. A future "conquest" mechanic would delete the
    // zone and recreate it under the new owner.
    private final UUID kingdomOwnerUUID;

    // min/max form an inclusive AABB. Both are defensive-immutable to protect
    // against MutableBlockPos aliasing (same pattern as KingdomData.corePos).
    private BlockPos minPos;
    private BlockPos maxPos;

    // blueprintId is the ResourceLocation-like string identifier of the
    // blueprint template (e.g. "kingdombuilder:housing/tier1_tent"). Stored as
    // a plain String — resolved against the datapack blueprint registry lazily
    // by consumers (BuilderActivity, System 12 housing tier lookup). Keeping
    // it as a String avoids dragging ResourceLocation through every save/load.
    private String blueprintId;

    // Whether the zone has finished construction. Flips to true when the last
    // BuildTask in the zone is consumed. Distinct from integrityState: a zone
    // can be `completed=true` AND `integrityState=DAMAGED` (built then broken).
    // completed=false means construction is still in progress.
    private boolean completed;

    // See ZoneIntegrityState javadoc above. Defaults to COMPLETE on a
    // freshly-added zone with completed=true; new unfinished zones should be
    // treated as COMPLETE-until-damaged once they finish building.
    private ZoneIntegrityState integrityState;

    /**
     * Primary constructor used by the Zoning Tool flow when a new zone is
     * placed in the world.
     *
     * POINTER: Callers should prefer {@link #ZoneData(UUID, UUID, BlockPos,
     * BlockPos, String)} for zone creation (it defaults completed=false and
     * integrityState=COMPLETE). This full-arity constructor exists for
     * deserialization and admin-tool paths only.
     */
    public ZoneData(UUID zoneId,
                    UUID kingdomOwnerUUID,
                    BlockPos minPos,
                    BlockPos maxPos,
                    String blueprintId,
                    boolean completed,
                    ZoneIntegrityState integrityState) {
        this.zoneId = zoneId;
        this.kingdomOwnerUUID = kingdomOwnerUUID;
        // Defensive immutable() copies — see KingdomData ctor for rationale.
        this.minPos = minPos.immutable();
        this.maxPos = maxPos.immutable();
        this.blueprintId = blueprintId;
        this.completed = completed;
        this.integrityState = integrityState;
    }

    /**
     * Convenience constructor for newly-placed zones. Generates a fresh
     * {@code zoneId}, defaults {@code completed} to false, and sets integrity
     * to {@link ZoneIntegrityState#COMPLETE} (will stay there until a block is
     * broken inside the footprint).
     *
     * POINTER: integrityState is COMPLETE on creation even though construction
     * has not started. This is intentional — DAMAGED/DESTROYED are signals
     * from {@link com.femtendo.kingdombuilder.events.KingdomBlockEvents}, not
     * construction state. The {@code completed} flag is the "is construction
     * finished" signal.
     */
    public ZoneData(UUID kingdomOwnerUUID, BlockPos minPos, BlockPos maxPos, String blueprintId) {
        this(UUID.randomUUID(), kingdomOwnerUUID, minPos, maxPos, blueprintId,
                false, ZoneIntegrityState.COMPLETE);
    }

    // --- Getters -----------------------------------------------------------

    public UUID getZoneId() {
        return zoneId;
    }

    public UUID getKingdomOwnerUUID() {
        return kingdomOwnerUUID;
    }

    public BlockPos getMinPos() {
        return minPos;
    }

    public BlockPos getMaxPos() {
        return maxPos;
    }

    public String getBlueprintId() {
        return blueprintId;
    }

    public boolean isCompleted() {
        return completed;
    }

    public ZoneIntegrityState getIntegrityState() {
        return integrityState;
    }

    // --- Setters (mutators MUST be paired with BlueprintRegistry#setDirty) --

    public void setMinPos(BlockPos minPos) {
        this.minPos = minPos.immutable();
    }

    public void setMaxPos(BlockPos maxPos) {
        this.maxPos = maxPos.immutable();
    }

    public void setBlueprintId(String blueprintId) {
        this.blueprintId = blueprintId;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public void setIntegrityState(ZoneIntegrityState integrityState) {
        this.integrityState = integrityState;
    }

    // --- Core behaviour ----------------------------------------------------

    /**
     * AABB containment check used by
     * {@link com.femtendo.kingdombuilder.events.KingdomBlockEvents} to decide
     * whether a broken block should downgrade this zone's integrity.
     *
     * <p>Inclusive on all six faces — a block at exactly {@code maxPos} is
     * considered inside. The Zoning Tool records the drag endpoints as the
     * corners of the zone, so including both extremes is what players expect.
     *
     * POINTER: This is a cheap per-zone check (6 integer comparisons). The
     * block-break event iterates every zone in the registry via
     * {@link BlueprintRegistry#getAllZones} and tests each with this method.
     * If zone counts grow past ~10k server-wide a chunk→zone reverse index
     * should be added, but the linear scan is fine for realistic scales.
     */
    public boolean contains(BlockPos pos) {
        return pos.getX() >= minPos.getX() && pos.getX() <= maxPos.getX()
                && pos.getY() >= minPos.getY() && pos.getY() <= maxPos.getY()
                && pos.getZ() >= minPos.getZ() && pos.getZ() <= maxPos.getZ();
    }

    // --- NBT (de)serialization --------------------------------------------

    /**
     * Serialize this zone into the supplied CompoundTag and return it.
     *
     * POINTER: Returns the same tag instance for fluent chaining inside
     * {@link BlueprintRegistry#save} (each zone is appended to a ListTag of
     * compounds). Do NOT change the return type without updating the registry
     * save loop.
     *
     * POINTER (Forge 1.21.1): {@link NbtUtils#writeBlockPos(BlockPos)} returns
     * a {@link net.minecraft.nbt.Tag} (IntArrayTag in vanilla's current impl)
     * and is stored as a child under KEY_MIN_POS / KEY_MAX_POS. Do NOT use
     * the removed pre-1.21 {@code writeBlockPos(CompoundTag, BlockPos)}
     * overload — that signature is gone.
     *
     * POINTER: integrityState serializes as its enum {@link Enum#name()}
     * rather than ordinal, so reordering the enum later does not silently
     * corrupt saves. If you must rename an enum value, write a migration in
     * {@link #load}.
     */
    public CompoundTag save(CompoundTag tag) {
        tag.putUUID(KEY_ZONE_ID, zoneId);
        tag.putUUID(KEY_OWNER_UUID, kingdomOwnerUUID);
        tag.put(KEY_MIN_POS, NbtUtils.writeBlockPos(minPos));
        tag.put(KEY_MAX_POS, NbtUtils.writeBlockPos(maxPos));
        tag.putString(KEY_BLUEPRINT_ID, blueprintId == null ? "" : blueprintId);
        tag.putBoolean(KEY_COMPLETED, completed);
        tag.putString(KEY_INTEGRITY, integrityState.name());
        return tag;
    }

    /**
     * Deserialize a ZoneData from a CompoundTag previously produced by
     * {@link #save(CompoundTag)}.
     *
     * POINTER: Returns {@code null} if the tag is missing required fields
     * (zoneId, ownerUUID, min/max). Callers in {@link BlueprintRegistry#load}
     * will skip and log a warning rather than abort the whole save load —
     * losing one orphaned zone is preferable to losing every zone.
     *
     * Edge cases considered:
     *  - Missing UUIDs → return null (record is unkeyable).
     *  - Missing min/max → return null (zone footprint is undefined).
     *  - Unknown integrityState string → default to COMPLETE (safest — an
     *    unknown tier won't spuriously flag the zone as damaged).
     *  - Missing blueprintId → default to empty string. Builder NPCs treat
     *    an empty blueprintId as "no template" and skip the zone.
     */
    public static ZoneData load(CompoundTag tag) {
        if (!tag.hasUUID(KEY_ZONE_ID) || !tag.hasUUID(KEY_OWNER_UUID)) {
            return null;
        }
        UUID zoneId = tag.getUUID(KEY_ZONE_ID);
        UUID ownerUUID = tag.getUUID(KEY_OWNER_UUID);

        BlockPos minPos = NbtUtils.readBlockPos(tag, KEY_MIN_POS).orElse(null);
        BlockPos maxPos = NbtUtils.readBlockPos(tag, KEY_MAX_POS).orElse(null);
        if (minPos == null || maxPos == null) {
            return null;
        }

        String blueprintId = tag.contains(KEY_BLUEPRINT_ID) ? tag.getString(KEY_BLUEPRINT_ID) : "";
        boolean completed = tag.getBoolean(KEY_COMPLETED);

        ZoneIntegrityState integrity = ZoneIntegrityState.COMPLETE;
        if (tag.contains(KEY_INTEGRITY)) {
            String raw = tag.getString(KEY_INTEGRITY);
            // POINTER: Use Enum.valueOf wrapped in try/catch because unknown
            // values (from future schema versions rolled back to this one)
            // should degrade to COMPLETE rather than crash the load.
            try {
                integrity = ZoneIntegrityState.valueOf(raw);
            } catch (IllegalArgumentException ignored) {
                integrity = ZoneIntegrityState.COMPLETE;
            }
        }

        return new ZoneData(zoneId, ownerUUID, minPos, maxPos, blueprintId, completed, integrity);
    }
}
