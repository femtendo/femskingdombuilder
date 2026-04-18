package com.femtendo.kingdombuilder.events;

import java.util.List;
import java.util.UUID;

import com.femtendo.kingdombuilder.KingdomBuilder;
import com.femtendo.kingdombuilder.blueprint.BlueprintRegistry;
import com.femtendo.kingdombuilder.blueprint.ZoneData;
import com.femtendo.kingdombuilder.blueprint.ZoneData.ZoneIntegrityState;
import com.femtendo.kingdombuilder.kingdom.KingdomData;
import com.femtendo.kingdombuilder.kingdom.KingdomManager;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Forge-bus event subscriber that protects kingdom blueprint zones.
 *
 * <h2>Architectural intent (System 6 of kingdom_builder_architecture_plan.md)</h2>
 *
 * <p>Whenever a block is broken by a player OR destroyed by an explosion, scan
 * every kingdom matching the event's dimension, pull each kingdom's zones from
 * {@link BlueprintRegistry}, and for every <em>completed</em> zone whose AABB
 * contains the affected position, downgrade the integrity to
 * {@link ZoneIntegrityState#DAMAGED} and notify the ruler if they are online.
 *
 * <h2>Bus wiring</h2>
 *
 * POINTER: {@link Mod.EventBusSubscriber} defaults to the FORGE bus (game
 * lifecycle events like block break / explosion / player interaction). The MOD
 * bus is reserved for mod-loading-lifecycle events (registries, setup, datagen)
 * and would never fire these handlers. Do NOT change the bus attribute — the
 * default is correct.
 *
 * POINTER: Class-level registration via {@link Mod.EventBusSubscriber} is
 * automatic at mod-load time — {@link KingdomBuilder}'s constructor explicitly
 * also does {@code MinecraftForge.EVENT_BUS.register(KingdomBlockEvents.class)}
 * per the issue spec. The double-register is idempotent (Forge deduplicates on
 * identity); keeping both makes the wiring explicit and survives any future
 * refactor that drops one or the other.
 *
 * <h2>Why scan by kingdom instead of zone?</h2>
 *
 * POINTER: Zones do NOT store their own dimension key — they inherit it from
 * their owning {@link KingdomData}. To filter broken blocks by dimension we
 * must first filter kingdoms by dim, then iterate each kingdom's zones. A
 * future spatial index could flatten this to a single chunk→zone lookup, but
 * the linear scan is fine for realistic scales (per-kingdom zone count is
 * small, and block-break is already an expensive event path).
 */
@Mod.EventBusSubscriber(modid = KingdomBuilder.MODID)
public final class KingdomBlockEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    private KingdomBlockEvents() {
        // POINTER: Static event handler — never instantiated. Private ctor keeps
        // accidental `new KingdomBlockEvents()` calls from compiling.
    }

    // ------------------------------------------------------------------
    //  Block break
    // ------------------------------------------------------------------

    /**
     * Fires on the FORGE bus when any block is broken (player dig, pickaxe,
     * creative-mode click, etc.).
     *
     * POINTER: {@link BlockEvent#getLevel()} returns a {@link LevelAccessor}, not
     * a {@link Level} — so we check {@code isClientSide()} via the LevelAccessor
     * contract, then cast to {@link ServerLevel}. Skipping the client side is
     * critical: the block-break event fires on both logical sides and
     * {@link BlueprintRegistry#get} NPEs on a client level (no MinecraftServer).
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        LevelAccessor accessor = event.getLevel();
        if (accessor.isClientSide()) {
            return;
        }
        if (!(accessor instanceof ServerLevel serverLevel)) {
            // Defensive: BreakEvent.getLevel() is typed LevelAccessor on the
            // base class even though BreakEvent's ctor takes a concrete Level.
            // Bail if the cast fails rather than risk a ClassCastException.
            return;
        }

        applyZoneDamage(serverLevel, event.getPos());
    }

    // ------------------------------------------------------------------
    //  Explosion detonate
    // ------------------------------------------------------------------

    /**
     * Fires on the FORGE bus after an explosion has computed its affected block
     * list but before blocks are actually removed. We iterate every affected
     * position and apply the same zone-damage logic as for manual breaks.
     *
     * POINTER: We subscribe to {@link ExplosionEvent.Detonate} (not
     * {@link ExplosionEvent.Start}) because Start fires before the block list is
     * computed; Detonate is the canonical "list of blocks is final" hook. The
     * Detonate event is also non-cancelable by design, so our handler is pure
     * side-effect (marking zones damaged) and does NOT attempt to prevent the
     * explosion itself.
     *
     * POINTER: {@link ExplosionEvent#getLevel()} returns a concrete
     * {@link Level} (not LevelAccessor), but we still must verify ServerLevel
     * because creeper explosions CAN originate on client mirror levels for
     * prediction purposes in some mod combinations. Same guard as BreakEvent.
     */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        List<BlockPos> affected = event.getAffectedBlocks();
        if (affected.isEmpty()) {
            return;
        }

        // POINTER: We resolve the registry/manager ONCE per explosion instead of
        // once per affected block. A single TNT can affect dozens of blocks and
        // the overworld data-storage lookup is memoized but not free.
        BlueprintRegistry registry = BlueprintRegistry.get(serverLevel);
        KingdomManager kingdoms = KingdomManager.get(serverLevel);
        String dimKey = serverLevel.dimension().location().toString();

        for (BlockPos pos : affected) {
            applyZoneDamage(serverLevel, pos, registry, kingdoms, dimKey);
        }
    }

    // ------------------------------------------------------------------
    //  Shared damage path
    // ------------------------------------------------------------------

    /**
     * Convenience overload that resolves the registry/manager/dimKey itself.
     * Used by the block-break path where only one position is affected and the
     * per-event lookup cost is trivial.
     */
    private static void applyZoneDamage(ServerLevel level, BlockPos brokenPos) {
        BlueprintRegistry registry = BlueprintRegistry.get(level);
        KingdomManager kingdoms = KingdomManager.get(level);
        String dimKey = level.dimension().location().toString();
        applyZoneDamage(level, brokenPos, registry, kingdoms, dimKey);
    }

    /**
     * Core damage logic shared by both event handlers.
     *
     * <p>For every kingdom matching the dimension, iterate its zones and flag
     * any <em>completed</em> zone whose AABB contains the position as DAMAGED.
     *
     * <h3>Edge cases considered</h3>
     * <ul>
     *   <li><strong>Zone not yet completed</strong> — ignored. Unfinished
     *       construction is already represented by {@code completed=false}; a
     *       broken scaffold or mid-build block is not "damage".</li>
     *   <li><strong>Zone already DAMAGED</strong> — skipped to avoid spamming
     *       the ruler with duplicate chat messages and avoid redundant
     *       {@code setDirty()} calls on every subsequent break.</li>
     *   <li><strong>Zone already DESTROYED</strong> — skipped for the same
     *       reason; also, a DESTROYED zone should not be able to regress to
     *       DAMAGED (DESTROYED is the worse state).</li>
     *   <li><strong>Overlapping zones</strong> — both are damaged. Overlap is
     *       a legitimate state in the housing-upgrade flow (System 12) where a
     *       tier-2 blueprint is dropped on top of a tier-1 footprint.</li>
     * </ul>
     *
     * POINTER: We call {@link BlueprintRegistry#setDirty} ONCE after the loop
     * (via the {@code anyChanged} flag) rather than once per damaged zone. The
     * SavedData dirty flag is idempotent so either pattern is correct, but
     * batching avoids noise in profiling.
     */
    private static void applyZoneDamage(ServerLevel level,
                                        BlockPos brokenPos,
                                        BlueprintRegistry registry,
                                        KingdomManager kingdoms,
                                        String dimKey) {
        boolean anyChanged = false;

        for (KingdomData kingdom : kingdoms.getAllKingdoms()) {
            // POINTER: Per-kingdom dimension filter. Without this a block broken
            // in the Nether would scan every Overworld kingdom's zones. The dim
            // string format ("minecraft:overworld") matches the canonical form
            // used by KingdomData — see System 2 dev notes on dimension key
            // consistency.
            if (!kingdom.getDimensionKey().equals(dimKey)) {
                continue;
            }

            List<ZoneData> zones = registry.getZonesForKingdom(kingdom.getOwnerUUID());
            for (ZoneData zone : zones) {
                if (!zone.isCompleted()) {
                    continue;
                }
                if (!zone.contains(brokenPos)) {
                    continue;
                }
                ZoneIntegrityState current = zone.getIntegrityState();
                if (current == ZoneIntegrityState.DAMAGED
                        || current == ZoneIntegrityState.DESTROYED) {
                    continue;
                }

                zone.setIntegrityState(ZoneIntegrityState.DAMAGED);
                anyChanged = true;

                // POINTER: Human-readable blueprint id fallback. Empty
                // blueprintId can legitimately occur on admin-created or
                // legacy zones; reporting "your zone" is friendlier than
                // printing an empty bracket pair.
                String label = (zone.getBlueprintId() == null || zone.getBlueprintId().isEmpty())
                        ? "one of your zones"
                        : "zone [" + zone.getBlueprintId() + "]";
                notifyRuler(level, kingdom.getOwnerUUID(),
                        "\u00A7c[Kingdom] A block in " + label
                                + " was damaged at " + brokenPos.getX()
                                + ", " + brokenPos.getY()
                                + ", " + brokenPos.getZ() + ".");
            }
        }

        if (anyChanged) {
            // POINTER: ZoneData setters do NOT auto-mark the registry dirty
            // (same contract as KingdomData/KingdomManager — see System 5 dev
            // notes). The block-event subscriber MUST call setDirty itself.
            registry.setDirty();
        }
    }

    // ------------------------------------------------------------------
    //  Chat notification
    // ------------------------------------------------------------------

    /**
     * Send a chat message to the kingdom's owner if they are currently online.
     * No-op (not even a log entry) when the owner is offline.
     *
     * POINTER: {@link MinecraftServer#getPlayerList} → {@code getPlayer(UUID)}
     * returns {@code null} when the player is offline — null-checked before
     * {@link ServerPlayer#sendSystemMessage}. Omitting the null check NPEs
     * during raids / creeper griefing while the kingdom owner is logged out,
     * which is the most common real-world scenario.
     *
     * POINTER: Deliberately uses {@code sendSystemMessage} (not chat) so the
     * message cannot be blocked by the target's ignore list and is rendered in
     * the server-message channel. Player chat channels can be muted by
     * third-party mods; system messages cannot.
     */
    private static void notifyRuler(ServerLevel level, UUID ownerUUID, String message) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        ServerPlayer ruler = server.getPlayerList().getPlayer(ownerUUID);
        if (ruler == null) {
            // POINTER: Ruler offline — no queue / no log. A future "kingdom
            // mailbox" feature could queue missed notifications to KingdomData
            // and replay them on next login; out of scope for System 6.
            return;
        }
        ruler.sendSystemMessage(Component.literal(message));
    }
}
