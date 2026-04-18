package com.femtendo.kingdombuilder.items;

import com.femtendo.kingdombuilder.blockentities.IronTubeBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Wrench — toggles force-disconnect on an Iron Tube face (System 11 of
 * {@code kingdom_builder_architecture_plan.md}).
 *
 * <h2>Interaction model</h2>
 *
 * <p>Right-click an {@link IronTubeBlockEntity} face:
 * <ul>
 *   <li>If the face is currently allowed to auto-connect → it becomes
 *       <b>force-disconnected</b> (renders red in the X-ray overlay).</li>
 *   <li>If the face is already force-disconnected → <b>clear</b> the
 *       disconnect (face auto-connects again, renders white).</li>
 * </ul>
 *
 * <p>Holding the Wrench also enables System 8's {@link
 * com.femtendo.kingdombuilder.blockentities.IronTubeBlockEntity} X-ray renderer
 * overlay. The X-ray logic lives in the renderer itself — it checks the player's
 * held item via {@code mc.player.getMainHandItem().getItem() instanceof WrenchItem}.
 * No logic here is required for the render-path side of that feature; this
 * class's only runtime job is the right-click toggle.
 *
 * <h2>Right-click on a non-Iron-Tube block</h2>
 *
 * POINTER: Returns {@link InteractionResult#PASS} so vanilla / other mods'
 * right-click handling continues normally. Returning CONSUME would swallow
 * unrelated right-clicks (e.g. player wanting to open a chest while holding
 * the wrench). PASS is the canonical "not my click" return.
 *
 * <h2>Dependency notes</h2>
 *
 * POINTER: This class REQUIRES that IRON_TUBE spawn an IronTubeBlockEntity.
 * System 11 promoted {@code ModBlocks.IRON_TUBE}'s supplier from a plain
 * {@code Block} to {@link com.femtendo.kingdombuilder.blocks.IronTubeBlock}
 * (a {@code BaseEntityBlock}) to satisfy this. If a future refactor reverts
 * that swap, the Wrench click will silently do nothing — the {@code instanceof}
 * guard below would just fall through. Protect the IronTubeBlock subclass in
 * any System 8 expansion.
 */
public class WrenchItem extends Item {

    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        BlockPos pos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof IronTubeBlockEntity tube)) {
            // Right-click on something that isn't a tube — don't swallow the
            // click. Returning PASS lets vanilla / other mods handle it.
            return InteractionResult.PASS;
        }

        // POINTER: context.getClickedFace() returns the face of the TUBE the
        // player hit. That's the correct axis to toggle — hitting the top face
        // toggles the UP connection. If we used the face the player is *looking
        // at* (player-relative), the mapping would flip when the player stands
        // on the other side of the pipe. The UseOnContext convention matches
        // what techmod wrenches (Create, Mekanism) do.
        Direction face = context.getClickedFace();

        if (level.isClientSide()) {
            // Client-side swing + sound; authoritative toggle happens server-side
            // on the same event dispatch.
            return InteractionResult.SUCCESS;
        }

        // Server-side authoritative mutation. toggleForcedDisconnect:
        //   - marks the BE dirty
        //   - calls level.sendBlockUpdated(...) so the client re-syncs and the
        //     System 8 X-ray renderer reads fresh forcedDisconnects state.
        boolean nowDisconnected = tube.toggleForcedDisconnect(face);

        // Feedback — short chat hint so the player can confirm the toggle
        // without leaning on the render overlay (useful in single-face changes).
        String faceName = face.getName(); // e.g. "up", "north"
        String msg = nowDisconnected
                ? "Disconnected " + faceName + " face."
                : "Reconnected " + faceName + " face.";
        player.sendSystemMessage(Component.literal(msg).withStyle(
                nowDisconnected ? ChatFormatting.RED : ChatFormatting.GREEN));
        return InteractionResult.SUCCESS;
    }
}
