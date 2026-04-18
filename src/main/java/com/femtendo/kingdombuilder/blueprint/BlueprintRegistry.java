package com.femtendo.kingdombuilder.blueprint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.logging.LogUtils;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

/**
 * World-scoped registry of every placed blueprint zone. Persisted via
 * Minecraft's {@link SavedData} mechanism to
 * {@code data/kingdom_blueprint_registry.dat} on the Overworld.
 *
 * <h2>Architectural intent (System 5 of kingdom_builder_architecture_plan.md)</h2>
 *
 * POINTER: Mirrors {@link com.femtendo.kingdombuilder.kingdom.KingdomManager}
 * structurally — same overworld anchoring, same {@code (tag, lookup)} 1.21.1
 * SavedData signature, same {@code setDirty} discipline. If you are looking
 * for rationale on why this is anchored to Overworld instead of per-dimension,
 * or why the SavedData.Factory passes null for the DataFixTypes slot, read
 * that class's javadoc — the reasoning is identical and not duplicated here.
 *
 * POINTER: This registry persists {@link ZoneData} (the placed footprints),
 * NOT {@link BuildTask} (the rasterized per-block work queue). Tasks are
 * transient and rebuilt on demand from the zone's blueprint template. See
 * {@link BuildTask} class javadoc for the rationale.
 *
 * POINTER: Every mutation method calls {@link #setDirty()}. Callers mutating
 * {@link ZoneData} setters directly (e.g. the block-break handler flipping
 * {@code integrityState} to DAMAGED) MUST call {@code setDirty()} themselves.
 * We did not add a back-reference from ZoneData → BlueprintRegistry for the
 * same reason KingdomData has none — see that class's javadoc.
 */
public class BlueprintRegistry extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The on-disk filename (sans extension) under each dimension's data folder.
     * POINTER: Renaming orphans every existing save — only change alongside
     * an explicit migration.
     */
    public static final String DATA_NAME = "kingdom_blueprint_registry";

    private static final String KEY_ZONES = "zones";

    /**
     * ZoneId → ZoneData. The map is the source of truth; {@link #zones.keySet}
     * is identical to the set of UUIDs ever returned from {@link #addZone}.
     *
     * POINTER: Keyed by zoneId (not kingdomOwnerUUID) because a kingdom can
     * own many zones. {@link #getZonesForKingdom(UUID)} does a linear scan
     * across this map — acceptable for expected bounds (a few dozen zones per
     * kingdom, at most a few thousand zones server-wide).
     */
    private final Map<UUID, ZoneData> zones = new HashMap<>();

    public BlueprintRegistry() {
        // POINTER: SavedData has no required constructor args. The factory
        // wires both the no-arg constructor (first-time creation) and the
        // static load method (deserialization); keep both in sync.
    }

    // ------------------------------------------------------------------
    //  Factory / accessor
    // ------------------------------------------------------------------

    /**
     * POINTER: Third {@link SavedData.Factory} slot (DataFixTypes) is null
     * because vanilla's data-fixer pipeline knows nothing about mod data. If
     * we ever rev the schema, hand-version inside {@link #load} rather than
     * register a vanilla DataFixer.
     */
    private static SavedData.Factory<BlueprintRegistry> factory() {
        return new SavedData.Factory<>(
                BlueprintRegistry::new,
                BlueprintRegistry::load,
                null
        );
    }

    /**
     * Canonical accessor. Always reads from the Overworld's data storage
     * regardless of which level the caller passes in, mirroring
     * {@link com.femtendo.kingdombuilder.kingdom.KingdomManager#get}.
     *
     * POINTER: Anchored to Overworld so zones in the Nether / End still
     * persist under the same registry entry — this aligns with the plan's
     * "one global registry per save" model and keeps BlueprintRegistry
     * trivially joinable with KingdomManager (both are overworld-anchored).
     *
     * POINTER: Server-only. Will NPE on a client-side level. Client-side code
     * needing zone info must receive it via a dedicated packet.
     */
    public static BlueprintRegistry get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    // ------------------------------------------------------------------
    //  Public API
    // ------------------------------------------------------------------

    /**
     * Register a new zone. Returns the same ZoneData for fluent chaining by
     * callers that want to further mutate it before a tick boundary.
     *
     * POINTER: Does NOT validate overlap with existing zones — overlap is a
     * legitimate state (housing upgrades drag a tier-2 blueprint over a
     * tier-1 footprint, per System 12 "Upgrade Process"). Callers that need
     * overlap rejection must check {@link #getAllZones} themselves before
     * adding.
     */
    public ZoneData addZone(ZoneData zone) {
        zones.put(zone.getZoneId(), zone);
        setDirty();
        return zone;
    }

    /**
     * Remove a zone by its id. No-op if the id was unknown.
     *
     * POINTER: Returns the removed ZoneData (or null) so the caller can do a
     * cleanup sweep (e.g. cancel in-flight BuildTasks whose
     * {@code kingdomOwnerUUID} equals the removed zone's owner AND whose
     * position falls inside the removed footprint). The registry itself does
     * NOT cascade — BuildTask queues live elsewhere (BuilderActivity state).
     */
    @Nullable
    public ZoneData removeZone(UUID zoneId) {
        ZoneData removed = zones.remove(zoneId);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    @Nullable
    public ZoneData getZone(UUID zoneId) {
        return zones.get(zoneId);
    }

    /**
     * Return every zone owned by the given kingdom.
     *
     * POINTER: The acceptance criterion on this issue specifies that this
     * method "returns only zones belonging to the specified kingdom UUID" —
     * the filter below is the load-bearing code for that contract. Do not
     * weaken to a broader predicate (e.g. "any zone in the dimension") — the
     * BlockEvents and InfluenceManager paths depend on ownership isolation.
     *
     * POINTER: Returns a newly-allocated {@link ArrayList} rather than a view,
     * so mutations to the returned list do not reflect back into the registry
     * and callers can freely sort / filter the result. O(zones.size()) per
     * call — linear scan is fine for expected scales.
     */
    public List<ZoneData> getZonesForKingdom(UUID kingdomOwnerUUID) {
        List<ZoneData> result = new ArrayList<>();
        for (ZoneData data : zones.values()) {
            if (data.getKingdomOwnerUUID().equals(kingdomOwnerUUID)) {
                result.add(data);
            }
        }
        return result;
    }

    /**
     * Read-only view of every registered zone. Used by the block-break event
     * handler (System 6) to find zones containing the broken position.
     *
     * POINTER: Unmodifiable to prevent callers from silently mutating the
     * backing map and skipping {@link #setDirty}. To mutate, use the public
     * API methods on this registry.
     */
    public Collection<ZoneData> getAllZones() {
        return Collections.unmodifiableCollection(zones.values());
    }

    // ------------------------------------------------------------------
    //  SavedData (de)serialization
    // ------------------------------------------------------------------

    /**
     * Forge 1.21.1 SavedData#save signature: {@code (CompoundTag, HolderLookup.Provider)}.
     *
     * POINTER: {@code lookup} is unused here because ZoneData contains no
     * ItemStack fields (unlike {@link com.femtendo.kingdombuilder.kingdom.KingdomData}
     * which forwards it for vault serialization). Kept in the method signature
     * because it's mandated by the SavedData contract. If a future field on
     * ZoneData ever stores ItemStacks or other codec-backed registry-bound
     * data, plumb {@code lookup} down into {@link ZoneData#save}.
     */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        ListTag list = new ListTag();
        for (ZoneData data : zones.values()) {
            list.add(data.save(new CompoundTag()));
        }
        tag.put(KEY_ZONES, list);
        return tag;
    }

    /**
     * Static deserializer wired into {@link SavedData.Factory}.
     *
     * POINTER (Tag.TAG_COMPOUND): {@link CompoundTag#getList} requires the
     * element type id; passing the wrong id returns an empty list silently.
     * Always pair a put-of-compound-ListTag with {@code getList(name,
     * Tag.TAG_COMPOUND)}. Same footgun called out in KingdomManager#load.
     *
     * Edge case: malformed individual entries (returned null by
     * {@link ZoneData#load}) are skipped with a warning rather than aborting
     * the entire load. Losing one orphaned zone is preferable to losing every
     * zone an admin can rebuild a single zone via the Zoning Tool.
     */
    public static BlueprintRegistry load(CompoundTag tag, HolderLookup.Provider lookup) {
        BlueprintRegistry registry = new BlueprintRegistry();
        ListTag list = tag.getList(KEY_ZONES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ZoneData data = ZoneData.load(entry);
            if (data == null) {
                LOGGER.warn("[BlueprintRegistry] Skipping malformed zone entry at index {} in {}", i, DATA_NAME);
                continue;
            }
            registry.zones.put(data.getZoneId(), data);
        }
        return registry;
    }
}
