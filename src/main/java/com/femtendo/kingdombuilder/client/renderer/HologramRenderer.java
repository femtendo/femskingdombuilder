package com.femtendo.kingdombuilder.client.renderer;

import com.femtendo.kingdombuilder.KingdomBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * System 7 — Client-only hologram / ghost-block previewer for blueprint placement.
 *
 * <p>Purpose: while the player holds the Zoning Tool (System 11) and has staged a blueprint
 * placement, this renderer draws the blueprint's {@link BlockState}s as translucent "ghost"
 * blocks at their target world positions so the player can visually confirm the footprint
 * before committing the zone.
 *
 * <p>POINTER (upstream producer): This class is passive — it only exposes static
 * {@link #setPendingHologram(UUID, Map)} / {@link #clearPendingHologram()}. The Zoning Tool's
 * client-side use-callback (System 11) is the intended caller. A server→client packet that
 * carries the target block map should unpack on the client thread and invoke
 * {@link #setPendingHologram(UUID, Map)}; the equipped-item tick hook should call
 * {@link #clearPendingHologram()} when the tool is unequipped, the player switches kingdoms,
 * or the blueprint is committed. DO NOT call the setters from the server side — this class
 * is {@link Dist#CLIENT} only and will not exist on dedicated-server jars.
 *
 * <p>POINTER (kingdom-scope guard): The pending hologram is tagged with the owning kingdom's
 * UUID. {@link #onRenderLevelStage(RenderLevelStageEvent)} verifies the local player's UUID
 * matches {@code pendingKingdomOwner} before drawing a single block. This enforces the
 * acceptance criterion "Hologram is invisible to players who don't own the kingdom" even in
 * split-screen / multiplayer scenarios where the pending state was populated for another
 * client earlier in the session (e.g. stale static state after a rejoin). The authoritative
 * visibility gate is still the packet dispatcher on the server — this check is defence in
 * depth.
 *
 * <p>POINTER (stage choice): We draw at {@link RenderLevelStageEvent.Stage#AFTER_TRANSLUCENT_BLOCKS}
 * because that's after opaque terrain AND vanilla translucent blocks (water, stained glass)
 * have been rasterized, so the translucent ghost blend correctly composites with whatever is
 * behind them. Drawing earlier (e.g. AFTER_SOLID_BLOCKS) would let water paint over the
 * hologram; drawing later (AFTER_PARTICLES) would let particles land behind the ghost blocks,
 * breaking depth cues.
 *
 * <p>POINTER (camera offset — MANDATORY): Minecraft's render path applies camera translation
 * to the view matrix, NOT to the PoseStack supplied to RenderLevelStageEvent. To draw in
 * world space we must first {@code poseStack.translate(-cam.x, -cam.y, -cam.z)}. Without this,
 * blocks render offset-from-origin in camera space and drift as the player moves. This is
 * the single most common mistake in custom world-space renderers; see vanilla
 * {@code LevelRenderer.renderLevel} for the canonical pattern.
 *
 * <p>POINTER (lighting): We hardcode {@code 0xF000F0} (packedLight = sky 15 / block 15) so
 * ghost blocks are fully bright regardless of ambient lighting. A blueprint preview at night
 * would otherwise be invisible — and we want these to read as "UI overlay on the world",
 * not "a block the world has placed that can be lit/shadowed".
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = KingdomBuilder.MODID, value = Dist.CLIENT)
public final class HologramRenderer {

    // POINTER: Static mutable state is acceptable here because (a) this class is @OnlyIn(CLIENT)
    // and Minecraft renders on a single thread, (b) there is only ever one local player per
    // client JVM, and (c) producers (Zoning Tool client-side handler / S2C packet receiver)
    // all run on the client thread. DO NOT expose these fields — mutations must go through
    // the public setters so the UUID and map stay in sync.
    private static final Map<BlockPos, BlockState> pendingHologram = new HashMap<>();
    private static UUID pendingKingdomOwner = null;

    private HologramRenderer() {
        // Utility-class + static-event-subscriber. Not instantiable.
    }

    /**
     * Publishes a blueprint preview for the given kingdom. Replaces any previously-staged
     * hologram in its entirety (we don't merge; stale positions must vanish atomically when
     * the player drags the Zoning Tool across a new region).
     *
     * <p>POINTER: Keys are expected to be {@link BlockPos#immutable()}. Callers handing in
     * mutable cursor positions (e.g. from a blueprint iterator reusing a MutableBlockPos)
     * must immutable-copy BEFORE calling this method; we defensively immutable-copy here too
     * so a caller that forgets can't corrupt the map, but the cost of a second copy per block
     * is small and the safety is worth it.
     *
     * @param kingdomOwnerUUID owning kingdom's UUID — only the matching local player sees the hologram
     * @param blocks           map of target world position → desired {@link BlockState} at that position
     */
    public static void setPendingHologram(UUID kingdomOwnerUUID, Map<BlockPos, BlockState> blocks) {
        pendingHologram.clear();
        if (blocks != null) {
            for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
                // Defensive immutable() — cf. KingdomData's constructor-level copy pattern.
                pendingHologram.put(e.getKey().immutable(), e.getValue());
            }
        }
        pendingKingdomOwner = kingdomOwnerUUID;
    }

    /** Drops the current preview. Called when the Zoning Tool is unequipped or the zone commits. */
    public static void clearPendingHologram() {
        pendingHologram.clear();
        pendingKingdomOwner = null;
    }

    /** Read-only view for debug/inspection (e.g. a future /kingdom debug command). */
    public static Map<BlockPos, BlockState> getPendingHologramView() {
        return Collections.unmodifiableMap(pendingHologram);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // POINTER: Gate on exact stage — this handler fires for every stage of every frame.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (pendingHologram.isEmpty() || pendingKingdomOwner == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            // Renderer can fire once during the title-screen→world transition before the
            // player reference settles. Bail rather than NPE.
            return;
        }

        // Kingdom-scope guard — see class javadoc POINTER.
        if (!player.getUUID().equals(pendingKingdomOwner)) {
            return;
        }

        // POINTER (1.21.1 API drift): RenderLevelStageEvent.getPoseStack() was deprecated in
        // 1.21 and now returns a Matrix4f (see the javadoc: "Mojang has stopped passing
        // around PoseStacks"). The canonical replacement in Forge 52.x mods is to
        // construct a fresh PoseStack locally — vanilla's internal PoseStack is no longer
        // exposed. Our fresh PoseStack starts at identity; applying the -camera translation
        // puts subsequent draw ops into world space, matching the coordinate frame vanilla
        // was using when it rendered the level geometry earlier this frame.
        PoseStack poseStack = new PoseStack();
        Camera camera = event.getCamera();
        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        poseStack.pushPose();

        // MANDATORY: Convert camera-local space to world space. See class javadoc POINTER
        // (camera offset). Without this translation the hologram "floats" near the player
        // and drifts as they move.
        var cam = camera.getPosition();
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        // POINTER: enableBlend + defaultBlendFunc lets the ghost blocks composite with the
        // world at the texture's own alpha. Blocks rendered via translucent RenderTypes
        // (glass, water) already handle blending internally, but renderSingleBlock defaults
        // to the block's *declared* RenderType — and most blueprint targets (stone, planks)
        // declare SOLID, which ignores the fragment alpha. Forcing blend here gives us a
        // uniform see-through look regardless of the target block's own render type.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        try {
            for (Map.Entry<BlockPos, BlockState> entry : pendingHologram.entrySet()) {
                renderGhostBlock(poseStack, entry.getKey(), entry.getValue(), blockRenderer, bufferSource);
            }
        } finally {
            // Symmetric cleanup: always restore the GL state we touched, even on exception,
            // so a single bad block can't leave blend permanently enabled and wreck
            // subsequent vanilla rendering.
            RenderSystem.disableBlend();
            poseStack.popPose();
        }
    }

    private static void renderGhostBlock(PoseStack poseStack,
                                         BlockPos pos,
                                         BlockState state,
                                         BlockRenderDispatcher blockRenderer,
                                         MultiBufferSource.BufferSource bufferSource) {
        poseStack.pushPose();
        // Move origin to the target block's corner, matching vanilla's per-block render
        // convention (blocks are modelled in the [0,1]^3 unit cube).
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());

        // POINTER: 0xF000F0 is the packed-light format (sky<<20 | block<<4) with both
        // channels maxed at 15. This makes ghost blocks glow at full brightness regardless
        // of world light — deliberate because a blueprint preview at night would otherwise
        // be invisible. OverlayTexture.NO_OVERLAY (0) disables hurt-flash / entity overlays
        // which are meaningless for static block renders.
        blockRenderer.renderSingleBlock(state, poseStack, bufferSource, 0xF000F0, OverlayTexture.NO_OVERLAY);

        // POINTER: endBatch per block is the issue spec's requirement. It's technically
        // less efficient than a single endBatch after the loop (each call flushes all
        // buffered RenderTypes), but per-block flushing avoids a subtle depth-ordering bug
        // where translucent faces of multiple ghost blocks would z-fight if batched — the
        // per-block flush writes them in iteration order, which is deterministic.
        bufferSource.endBatch();

        poseStack.popPose();
    }
}
