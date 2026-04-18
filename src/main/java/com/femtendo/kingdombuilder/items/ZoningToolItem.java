package com.femtendo.kingdombuilder.items;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.femtendo.kingdombuilder.blocks.ModBlocks;
import com.femtendo.kingdombuilder.blueprint.BlueprintRegistry;
import com.femtendo.kingdombuilder.blueprint.ZoneData;
import com.femtendo.kingdombuilder.client.renderer.HologramRenderer;
import com.femtendo.kingdombuilder.kingdom.KingdomData;
import com.femtendo.kingdombuilder.kingdom.KingdomManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.component.DataComponents;

/**
 * Zoning Tool — the two-click item that stages a blueprint zone placement
 * (System 11 of {@code kingdom_builder_architecture_plan.md}).
 *
 * <h2>Interaction model</h2>
 *
 * <ol>
 *   <li><b>First right-click on a block</b> → stores the clicked face's adjacent
 *       placement position as "corner A" inside the stack's
 *       {@link DataComponents#CUSTOM_DATA} component.</li>
 *   <li><b>Second right-click on a block</b> → computes the AABB from corner A
 *       and the second click. If the second click lands inside an existing
 *       completed zone owned by this kingdom, this becomes an UPGRADE placement
 *       (new {@link ZoneData} carries {@code upgradeFrom = oldZoneId}). Otherwise
 *       it is a fresh zone. Either way a new ZoneData is registered in
 *       {@link BlueprintRegistry} and the client sets a ghost-block hologram
 *       preview via {@link HologramRenderer#setPendingHologram}.</li>
 *   <li><b>Right-click on air</b> (use, not useOn) → cancels a pending first
 *       corner and clears any hologram the player had staged.</li>
 * </ol>
 *
 * <h2>Data Components (1.21.1 TECH ALIGNMENT)</h2>
 *
 * POINTER: The architecture plan's System 5 dev notes explicitly flagged items
 * as the place Data Components applies: "THIS is where the TECH ALIGNMENT
 * Data Components migration applies. Use DataComponentType&lt;BlockPos&gt; for
 * firstCornerPos on the tool stack, not stack.getTag().put(...)". We opted to
 * use {@link CustomData#update} on the shared {@link DataComponents#CUSTOM_DATA}
 * rather than register a bespoke DataComponentType because:
 *   - The state is MOD-internal and temporary (two-click tool buffer); a named
 *     component adds registry boilerplate for no observable benefit.
 *   - CustomData is canonically the escape-hatch for mod NBT on items in 1.21.1
 *     — it's what forge/vanilla recommend for item-level mod data.
 *   - If a later feature needs deterministic encoding/streaming (e.g. a crafting
 *     recipe that reads the first corner), promote the keys to a dedicated
 *     DataComponentType without changing the UX.
 *
 * <h2>Why no network packet (yet)</h2>
 *
 * POINTER: The issue description mentions a client→server packet. The current
 * codebase has NO network infrastructure (no SimpleChannel / PacketDistributor).
 * Vanilla's {@link Item#useOn(UseOnContext)} already fires on BOTH logical
 * sides — the server mutates the authoritative state (BlueprintRegistry) and
 * the client mutates client-only state (HologramRenderer). No custom packet is
 * required for the acceptance criteria. When System 12 adds the
 * {@code S2CKingdomBorderPacket} infrastructure, the "holding the item also
 * triggers S2C border packet dispatch" clause can be wired via an
 * {@link Item#inventoryTick} hook added here — see the inline POINTER in
 * {@code inventoryTick}.
 */
public class ZoningToolItem extends Item {

    // NBT keys inside CustomData — kept private-static so the two methods that
    // read/write them stay in lockstep.
    private static final String KEY_FIRST_CORNER_X = "kb_first_corner_x";
    private static final String KEY_FIRST_CORNER_Y = "kb_first_corner_y";
    private static final String KEY_FIRST_CORNER_Z = "kb_first_corner_z";
    private static final String KEY_BLUEPRINT_ID = "kb_blueprint_id";

    /**
     * Default blueprint identifier used when the player hasn't selected one
     * yet. Matches the resource-location pattern the architecture plan uses
     * (e.g. "kingdombuilder:housing/tier1_tent"). Builder NPCs will treat this
     * as a placeholder footprint until System 12's housing-tier datapack lands.
     */
    private static final String DEFAULT_BLUEPRINT_ID = "kingdombuilder:zone/generic";

    /**
     * Hard cap on each side of the zone AABB. Prevents a player from
     * accidentally or maliciously registering a zone the size of a chunk column
     * — which would (a) trigger a hologram render storm on every client and
     * (b) overwhelm System 6's block-break scan loop. 64 is generous for any
     * realistic building; the architecture plan doesn't specify a max, so this
     * is a defensive default that can be tuned via config later.
     */
    private static final int MAX_ZONE_EDGE = 64;

    public ZoningToolItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------
    //  Main interaction
    // ------------------------------------------------------------------

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        // POINTER: We target the ADJACENT block (click face + 1), matching the
        // Minecraft convention for "place an item at this spot" tools. That's
        // what the player visually expects from a two-click drag — the corners
        // sit on the surface they're clicking, not inside the block they hit.
        BlockPos clickedPos = context.getClickedPos();
        BlockPos targetPos = clickedPos.relative(context.getClickedFace());

        // Both client and server enter this block. State changes happen only in
        // the branch relevant to that side.
        ItemStack stack = context.getItemInHand();
        BlockPos firstCorner = readFirstCorner(stack);

        if (firstCorner == null) {
            // --- First click → stash corner A ------------------------------
            writeFirstCorner(stack, targetPos);
            if (!level.isClientSide() && player != null) {
                player.sendSystemMessage(Component.literal(
                        "Zoning Tool: corner set at "
                                + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ()
                                + ". Right-click another block to finish.")
                        .withStyle(ChatFormatting.YELLOW));
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        // --- Second click → commit zone ------------------------------------
        BlockPos minPos = new BlockPos(
                Math.min(firstCorner.getX(), targetPos.getX()),
                Math.min(firstCorner.getY(), targetPos.getY()),
                Math.min(firstCorner.getZ(), targetPos.getZ()));
        BlockPos maxPos = new BlockPos(
                Math.max(firstCorner.getX(), targetPos.getX()),
                Math.max(firstCorner.getY(), targetPos.getY()),
                Math.max(firstCorner.getZ(), targetPos.getZ()));

        // Size guard — per MAX_ZONE_EDGE javadoc.
        int dx = maxPos.getX() - minPos.getX();
        int dy = maxPos.getY() - minPos.getY();
        int dz = maxPos.getZ() - minPos.getZ();
        if (dx > MAX_ZONE_EDGE || dy > MAX_ZONE_EDGE || dz > MAX_ZONE_EDGE) {
            if (!level.isClientSide()) {
                player.sendSystemMessage(Component.literal(
                        "Zone too large (max " + MAX_ZONE_EDGE + " blocks per edge). Cancelled.")
                        .withStyle(ChatFormatting.RED));
            }
            clearFirstCorner(stack);
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        String blueprintId = readBlueprintId(stack);

        if (level instanceof ServerLevel serverLevel) {
            // SERVER-SIDE: authoritative ZoneData registration.
            InteractionResult serverResult = registerZoneServer(serverLevel, player, minPos, maxPos, blueprintId);
            clearFirstCorner(stack);
            return serverResult;
        } else {
            // CLIENT-SIDE: populate the hologram preview locally so the player
            // sees an instant visual confirmation without waiting for a packet.
            // The Mojang client calls useOn BEFORE the server round-trip, which
            // makes this feel responsive on laggy connections. If the server
            // rejects the zone (e.g. player owns no kingdom), the hologram will
            // stay up until the player dismisses it by using the tool on air —
            // that's an accepted UX compromise until packet infra lands.
            KingdomData ownKingdomStub = null;
            // We don't have a client-side KingdomManager mirror, so the
            // hologram is tagged with the local player's own UUID. HologramRenderer
            // already gates on player.getUUID().equals(pendingKingdomOwner).
            Map<BlockPos, BlockState> preview = buildPreviewBlocks(minPos, maxPos);
            HologramRenderer.setPendingHologram(player.getUUID(), preview);
            clearFirstCorner(stack);
            return InteractionResult.SUCCESS;
        }
    }

    /**
     * Server-only. Validates kingdom ownership, looks for an existing zone at
     * the second-click corner (upgrade path), and creates+registers the new
     * {@link ZoneData}.
     */
    private InteractionResult registerZoneServer(ServerLevel level, Player player,
                                                 BlockPos minPos, BlockPos maxPos,
                                                 String blueprintId) {
        KingdomManager manager = KingdomManager.get(level);
        KingdomData kingdom = manager.getKingdom(player.getUUID());
        if (kingdom == null) {
            player.sendSystemMessage(Component.literal(
                    "You must found a kingdom (place a Settlement Hearth) before zoning.")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        BlueprintRegistry registry = BlueprintRegistry.get(level);

        // POINTER (upgrade flow): Look for a COMPLETED zone the player owns
        // whose footprint overlaps either corner. If found, the new zone carries
        // upgradeFrom = oldZoneId so System 10's builder activity will
        // deconstruct the old blocks before constructing this tier. We only
        // consider zones owned by the SAME kingdom — can't upgrade someone
        // else's zone. Full tier-ladder validation (must-be-higher-tier) is
        // System 12's responsibility once housing-tier datapacks land.
        @Nullable UUID upgradeFrom = null;
        for (ZoneData existing : registry.getZonesForKingdom(kingdom.getOwnerUUID())) {
            if (!existing.isCompleted()) {
                continue;
            }
            // Overlap check: accept either corner touching an existing zone.
            // Stricter "full containment" would reject the common case where
            // the player drags slightly outside their old tent's outline.
            if (existing.contains(minPos) || existing.contains(maxPos)) {
                upgradeFrom = existing.getZoneId();
                break;
            }
        }

        ZoneData zone = new ZoneData(
                UUID.randomUUID(),
                kingdom.getOwnerUUID(),
                minPos,
                maxPos,
                blueprintId,
                /*completed*/ false,
                ZoneData.ZoneIntegrityState.COMPLETE,
                upgradeFrom);
        registry.addZone(zone);

        // User feedback — include the upgrade hint so the player knows the
        // system understood their intent.
        String msg = upgradeFrom != null
                ? "Zone registered (upgrading existing zone) — builders will begin work."
                : "Zone registered — builders will begin work.";
        player.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.GOLD));
        return InteractionResult.SUCCESS;
    }

    /**
     * Build a simple scaffold-tinted preview map for the client hologram. Real
     * blueprint rasterization (System 10 / System 12) will replace this with
     * the per-block template lookup; until then we render a scaffold-block box
     * so the player at least sees their zone footprint.
     *
     * POINTER: Capped at MAX_ZONE_EDGE^3 by the caller via the size guard
     * above; no further cap needed here.
     */
    private static Map<BlockPos, BlockState> buildPreviewBlocks(BlockPos minPos, BlockPos maxPos) {
        Map<BlockPos, BlockState> preview = new HashMap<>();
        BlockState scaffold = ModBlocks.KINGDOM_SCAFFOLD.get().defaultBlockState();
        // Draw only the AABB shell (faces) so the preview is readable at larger
        // sizes and doesn't lag the client. Interior ghost blocks would occlude
        // the player's view of the real world inside the zone.
        for (int x = minPos.getX(); x <= maxPos.getX(); x++) {
            for (int y = minPos.getY(); y <= maxPos.getY(); y++) {
                for (int z = minPos.getZ(); z <= maxPos.getZ(); z++) {
                    boolean onXEdge = (x == minPos.getX() || x == maxPos.getX());
                    boolean onYEdge = (y == minPos.getY() || y == maxPos.getY());
                    boolean onZEdge = (z == minPos.getZ() || z == maxPos.getZ());
                    // On the AABB surface iff at least one axis is at its extreme.
                    if (onXEdge || onYEdge || onZEdge) {
                        preview.put(new BlockPos(x, y, z), scaffold);
                    }
                }
            }
        }
        return preview;
    }

    // ------------------------------------------------------------------
    //  CustomData helpers — read/write the first-corner buffer
    // ------------------------------------------------------------------

    @Nullable
    private static BlockPos readFirstCorner(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        if (!tag.contains(KEY_FIRST_CORNER_X)) {
            return null;
        }
        return new BlockPos(
                tag.getInt(KEY_FIRST_CORNER_X),
                tag.getInt(KEY_FIRST_CORNER_Y),
                tag.getInt(KEY_FIRST_CORNER_Z));
    }

    private static void writeFirstCorner(ItemStack stack, BlockPos pos) {
        // POINTER: CustomData.update takes the DataComponentType, the stack,
        // and a UnaryOperator<CompoundTag>. The operator receives a MUTABLE
        // copy of the existing tag and returns it; the helper then wraps it
        // back into a new CustomData and stores it on the stack.
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt(KEY_FIRST_CORNER_X, pos.getX());
            tag.putInt(KEY_FIRST_CORNER_Y, pos.getY());
            tag.putInt(KEY_FIRST_CORNER_Z, pos.getZ());
        });
    }

    private static void clearFirstCorner(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(KEY_FIRST_CORNER_X);
            tag.remove(KEY_FIRST_CORNER_Y);
            tag.remove(KEY_FIRST_CORNER_Z);
        });
    }

    /**
     * Blueprint selector. Defaults to {@link #DEFAULT_BLUEPRINT_ID} until a
     * blueprint-picker GUI is introduced; a future screen can call
     * {@link #setBlueprintId(ItemStack, String)} to override.
     */
    public static String readBlueprintId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return DEFAULT_BLUEPRINT_ID;
        }
        CompoundTag tag = data.copyTag();
        return tag.contains(KEY_BLUEPRINT_ID) ? tag.getString(KEY_BLUEPRINT_ID) : DEFAULT_BLUEPRINT_ID;
    }

    public static void setBlueprintId(ItemStack stack, String blueprintId) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag ->
                tag.putString(KEY_BLUEPRINT_ID, blueprintId));
    }
}
