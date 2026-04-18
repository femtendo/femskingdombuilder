package com.femtendo.kingdombuilder.kingdom;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

/**
 * World-scoped registry of every active kingdom. Persisted via Minecraft's
 * {@link SavedData} mechanism to {@code data/kingdom_registry.dat} on the
 * Overworld.
 *
 * <h2>Architectural intent (System 2 of kingdom_builder_architecture_plan.md)</h2>
 *
 * POINTER: One canonical kingdom-registry per <em>save</em>, anchored to the
 * Overworld's {@link net.minecraft.world.level.storage.DimensionDataStorage}.
 * Even when callers hand in a Nether or End {@link ServerLevel}, {@link #get}
 * forwards to {@code level.getServer().overworld()} so all dimensions read and
 * write the same registry. This is what enforces the <strong>one kingdom per
 * player UUID, globally</strong> contract — without the overworld redirect,
 * each dimension would carry its own copy and a player could claim once per
 * dimension.
 *
 * POINTER: Every mutation method calls {@link #setDirty()}. Outside callers
 * that mutate {@link KingdomData} fields directly (via its setters) MUST also
 * call {@code KingdomManager.get(level).setDirty()} themselves. We did not
 * back-reference KingdomData → KingdomManager because that would create a
 * cycle that complicates serialization. See {@link KingdomData} class javadoc.
 *
 * <h2>Forge 1.21.1 SavedData notes</h2>
 *
 * In 1.21.1 {@link SavedData#save(CompoundTag, HolderLookup.Provider)} takes a
 * {@link HolderLookup.Provider} and {@link SavedData.Factory} is a record
 * {@code (Supplier<T>, BiFunction<CompoundTag, HolderLookup.Provider, T>,
 * DataFixTypes)}. We pass {@code null} for the {@link
 * net.minecraft.util.datafix.DataFixTypes} slot because this is mod data and
 * vanilla's data-fixer pipeline knows nothing about it; if/when our schema
 * changes we will hand-version the NBT in {@link #load} rather than register a
 * datafixer.
 */
public class KingdomManager extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The on-disk filename (sans extension) under each dimension's data folder.
     * POINTER: Renaming this string ORPHANS every existing save — only change
     * it alongside an explicit migration.
     */
    public static final String DATA_NAME = "kingdom_registry";

    private static final String KEY_KINGDOMS = "kingdoms";

    /**
     * Owner UUID → KingdomData. The map IS the source of truth for the
     * "one kingdom per player" rule: a duplicate {@link #claimKingdom} for the
     * same UUID is rejected here.
     */
    private final Map<UUID, KingdomData> kingdomsByOwner = new HashMap<>();

    public KingdomManager() {
        // POINTER: SavedData has no required constructor args. The
        // SavedData.Factory created in #factory wires both
        // (a) the no-arg constructor for first-time creation and
        // (b) the static load method for deserialization. Keep both in sync.
    }

    // ------------------------------------------------------------------
    //  Factory / accessor
    // ------------------------------------------------------------------

    /**
     * The {@link SavedData.Factory} consumed by
     * {@link net.minecraft.world.level.storage.DimensionDataStorage#computeIfAbsent}.
     *
     * POINTER: Built fresh on every {@link #get} call; the storage layer
     * memoizes the loaded SavedData by DATA_NAME so this allocation only
     * matters once per server lifetime per dimension.
     *
     * The third (DataFixTypes) slot is intentionally null — see class javadoc.
     */
    private static SavedData.Factory<KingdomManager> factory() {
        return new SavedData.Factory<>(
                KingdomManager::new,
                (tag, lookup) -> KingdomManager.load(tag),
                null
        );
    }

    /**
     * Canonical accessor. Always reads from the Overworld's data storage
     * regardless of which level the caller is operating in.
     *
     * POINTER: Safe to call from any dimension. Will NPE if invoked on a
     * client-side level (no MinecraftServer) — callers must guard with
     * {@code !level.isClientSide()} or accept that this is a server-only API.
     * The kingdom registry is server-authoritative; clients receive
     * relevant slices via packets (see Systems 7 and 12 in the plan).
     */
    public static KingdomManager get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    // ------------------------------------------------------------------
    //  Public API
    // ------------------------------------------------------------------

    /**
     * Attempt to register a new kingdom for the given player.
     *
     * @return {@code true} if the kingdom was created, {@code false} if the
     *         player already owns one (ENFORCES the "one kingdom per player"
     *         acceptance criterion of System 2). Callers should surface the
     *         false result to the player as a chat message.
     *
     * POINTER: Does NOT validate that {@code corePos} is unclaimed by another
     * kingdom. Pre-flight that check with {@link #getKingdomAtPos} from the
     * caller (System 3's SettlementHearthBlock#use is the intended caller).
     * Splitting the two checks lets the block layer produce distinct error
     * messages ("you already have a kingdom" vs. "this hearth is already
     * claimed by Player X").
     */
    public boolean claimKingdom(UUID ownerUUID, BlockPos corePos, String dimensionKey, String kingdomName) {
        if (kingdomsByOwner.containsKey(ownerUUID)) {
            return false;
        }
        kingdomsByOwner.put(ownerUUID, new KingdomData(ownerUUID, corePos, dimensionKey, kingdomName));
        setDirty();
        return true;
    }

    /**
     * Drop a kingdom from the registry. No-op if the player owned no kingdom.
     *
     * POINTER: This deletes the registry entry but does NOT remove the
     * SettlementHearth block in-world (System 3's responsibility) nor any
     * BlueprintRegistry zones (System 5's responsibility). Hooking those is
     * the next agent's job — leave a note in this method when wiring is added.
     */
    public void abandonKingdom(UUID ownerUUID) {
        if (kingdomsByOwner.remove(ownerUUID) != null) {
            setDirty();
        }
    }

    @Nullable
    public KingdomData getKingdom(UUID ownerUUID) {
        return kingdomsByOwner.get(ownerUUID);
    }

    /**
     * Find any kingdom whose CORE block is at {@code pos} in the given
     * dimension.
     *
     * POINTER: This is a literal core-pos equality check, NOT a territory
     * containment check — it answers "is this exact block a kingdom hearth?"
     * not "what kingdom claims this chunk?". Territory queries arrive in
     * System 12 via {@code KingdomManager#getOwnerOfChunk(ChunkPos, String)}.
     *
     * Linear scan is acceptable: the expected upper bound is a few dozen
     * kingdoms per server. If load testing later shows N > ~1000 we should
     * add a {@code Map<DimensionedPos, UUID>} reverse index.
     */
    @Nullable
    public KingdomData getKingdomAtPos(BlockPos pos, String dimensionKey) {
        for (KingdomData data : kingdomsByOwner.values()) {
            if (data.getDimensionKey().equals(dimensionKey) && data.getCorePos().equals(pos)) {
                return data;
            }
        }
        return null;
    }

    /**
     * Read-only view of every registered kingdom.
     *
     * POINTER: Returned collection is unmodifiable to prevent callers from
     * silently mutating the backing map (which would skip the {@link #setDirty}
     * call). To mutate, use the public API methods on this manager.
     */
    public Collection<KingdomData> getAllKingdoms() {
        return Collections.unmodifiableCollection(kingdomsByOwner.values());
    }

    // ------------------------------------------------------------------
    //  SavedData (de)serialization
    // ------------------------------------------------------------------

    /**
     * Forge 1.21.1 SavedData#save signature: {@code (CompoundTag, HolderLookup.Provider)}.
     *
     * POINTER: We don't consume the {@link HolderLookup.Provider} because none
     * of our persisted data references holder-backed registries (no
     * BlockStates, no ItemStacks here). If/when a future field DOES reference
     * a registry holder (e.g. caching a Block or Item), pass {@code lookup}
     * down to the appropriate codec.
     */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        ListTag list = new ListTag();
        for (KingdomData data : kingdomsByOwner.values()) {
            list.add(data.save(new CompoundTag()));
        }
        tag.put(KEY_KINGDOMS, list);
        return tag;
    }

    /**
     * Static deserializer wired into {@link SavedData.Factory} via {@link #factory()}.
     *
     * POINTER (Tag.TAG_COMPOUND): {@link ListTag#getCompound(int)} requires the
     * list's element type to be COMPOUND. Passing the wrong type id to
     * {@link CompoundTag#getList} returns an empty list silently — this is the
     * #1 footgun in vanilla NBT loading. Always pair {@code put(ListTag of
     * compounds)} with {@code getList(name, Tag.TAG_COMPOUND)}.
     *
     * Edge case: malformed individual entries (returned as null by
     * {@link KingdomData#load}) are skipped with a warning rather than aborting
     * the whole load. Losing one corrupt record is preferable to losing the
     * registry; an op can rebuild via the planned {@code /kingdom abandon} +
     * re-claim flow (System 13).
     */
    public static KingdomManager load(CompoundTag tag) {
        KingdomManager manager = new KingdomManager();
        ListTag list = tag.getList(KEY_KINGDOMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            KingdomData data = KingdomData.load(entry);
            if (data == null) {
                LOGGER.warn("[KingdomManager] Skipping malformed kingdom entry at index {} in {}", i, DATA_NAME);
                continue;
            }
            manager.kingdomsByOwner.put(data.getOwnerUUID(), data);
        }
        return manager;
    }
}
