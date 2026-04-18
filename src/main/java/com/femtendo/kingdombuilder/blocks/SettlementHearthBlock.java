package com.femtendo.kingdombuilder.blocks;

import com.femtendo.kingdombuilder.blockentities.SettlementHearthBlockEntity;
import com.femtendo.kingdombuilder.kingdom.KingdomData;
import com.femtendo.kingdombuilder.kingdom.KingdomManager;
import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Settlement Hearth — the anchor block a player right-clicks to turn an
 * in-world location into a Kingdom Core (System 3 of
 * {@code kingdom_builder_architecture_plan.md}).
 *
 * <p>On a successful claim, this block:</p>
 * <ol>
 *   <li>Reserves a fresh {@link KingdomData} entry in {@link KingdomManager}
 *       under the clicking player's UUID.</li>
 *   <li>Writes the clicking player's UUID into the local
 *       {@link SettlementHearthBlockEntity} for display/query convenience.</li>
 *   <li>Messages the player with either a success line or one of two distinct
 *       rejection messages.</li>
 * </ol>
 *
 * <p>POINTER (System 2 integration): The two-stage pre-flight
 * ({@code getKingdomAtPos} THEN {@code getKingdom(UUID)}) is deliberate — it
 * lets us surface distinct chat errors for the two failure modes without
 * parsing a compound return code from {@link KingdomManager#claimKingdom}.
 * See {@link KingdomManager#claimKingdom} javadoc for why the manager itself
 * only enforces the one-kingdom-per-UUID rule; pos-collision is the block
 * layer's responsibility.</p>
 *
 * <p>POINTER (future System 6 integration): When a Hearth is broken or an
 * explosion destroys it, a FORGE-bus event subscriber (System 6 /
 * {@code KingdomBlockEvents}) should call
 * {@link KingdomManager#abandonKingdom} to cascade the kingdom's destruction.
 * That wiring does NOT live here — this class only handles the claim path.</p>
 */
public class SettlementHearthBlock extends BaseEntityBlock {

    /**
     * BaseEntityBlock is abstract in 1.21.1 and requires a MapCodec for
     * block-state data-pack loading. The CODEC field plus {@link #codec()}
     * override is the canonical shape — see {@code EnchantingTableBlock} in
     * vanilla for an identical, no-constructor-args example.
     *
     * POINTER: {@code simpleCodec(MyBlock::new)} is the one-line helper for
     * blocks whose only ctor argument is {@link net.minecraft.world.level.block.state.BlockBehaviour.Properties}.
     * If we later add constructor args (variants, dye colors, etc.) this will
     * need an explicit RecordCodecBuilder.
     */
    public static final MapCodec<SettlementHearthBlock> CODEC = simpleCodec(SettlementHearthBlock::new);

    public SettlementHearthBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    // ------------------------------------------------------------------
    //  Block entity wiring (EntityBlock contract)
    // ------------------------------------------------------------------

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SettlementHearthBlockEntity(pos, state);
    }

    /**
     * POINTER: BaseEntityBlock's default {@code getRenderShape} is
     * {@link RenderShape#INVISIBLE} (because most vanilla BE blocks render via
     * a BlockEntityRenderer). We want the normal JSON block model to render,
     * so we override back to {@link RenderShape#MODEL}. This matches the
     * System 3 spec: {@code getRenderShape() returns RenderShape.MODEL}.
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // ------------------------------------------------------------------
    //  Claim interaction
    // ------------------------------------------------------------------

    /**
     * Handle an empty-hand right-click. In Forge 1.21.1 the old
     * {@code use(...)} hook was split into {@code useItemOn} (player held an
     * item) and {@code useWithoutItem} (empty hand). We overload
     * {@code useWithoutItem} because claiming a kingdom should not consume or
     * depend on any held item — if the player is holding an item, the default
     * {@code useItemOn} returns {@code PASS_TO_DEFAULT_BLOCK_INTERACTION},
     * which then falls through to this method. Net effect: right-click
     * regardless of hand contents triggers the claim path.
     *
     * <p>POINTER: All state mutation is server-side only. Calling
     * {@link KingdomManager#get} on a client level would NPE (no
     * {@code MinecraftServer} reachable from a client-side {@link Level}).
     * On the client we return {@link InteractionResult#SUCCESS} so the
     * animation / sound plays; the authoritative result comes from the
     * server tick.</p>
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            // Client-side: indicate success so the hand-swing animation plays.
            // Real logic runs on the server.
            return InteractionResult.SUCCESS;
        }

        KingdomManager manager = KingdomManager.get(serverLevel);
        // POINTER: dimensionKey format matches KingdomData/KingdomManager —
        // stringified ResourceLocation of the current dimension
        // (e.g. "minecraft:overworld"). MUST stay in sync with the format
        // used by any future {@code getKingdomAtPos} / {@code getOwnerOfChunk}
        // callers (System 12).
        String dimensionKey = level.dimension().location().toString();

        // --- Pre-flight 1: is this exact hearth already a kingdom core? -----
        // Exact-pos equality (not territory containment). System 12 will add a
        // separate chunk-containment check; this is the correct guard for the
        // "player tries to re-claim an existing hearth" case.
        KingdomData existingAtPos = manager.getKingdomAtPos(pos, dimensionKey);
        if (existingAtPos != null) {
            player.sendSystemMessage(Component.literal(
                    "This hearth is already the core of " + existingAtPos.getKingdomName() + ".")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        // --- Pre-flight 2: does the player already rule a kingdom? ----------
        // KingdomManager#claimKingdom also enforces this, but checking here
        // lets us surface a clearer message AND short-circuit before we spend
        // work constructing the default kingdom name string.
        KingdomData existingForPlayer = manager.getKingdom(player.getUUID());
        if (existingForPlayer != null) {
            player.sendSystemMessage(Component.literal(
                    "You already rule " + existingForPlayer.getKingdomName()
                            + ". Abandon it before founding another.")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        // --- Claim -----------------------------------------------------------
        // Default kingdom name template from the System 3 issue:
        //   "<PlayerName>'s Kingdom"
        // player.getName().getString() resolves to the vanilla display name —
        // works for both real players and fake/command players.
        String defaultName = player.getName().getString() + "'s Kingdom";
        boolean claimed = manager.claimKingdom(player.getUUID(), pos, dimensionKey, defaultName);
        if (!claimed) {
            // Theoretically unreachable — pre-flight 2 already covered the
            // one-kingdom-per-player case. Defensive branch in case a future
            // change to KingdomManager#claimKingdom adds another rejection
            // path (e.g. server-wide kingdom cap).
            player.sendSystemMessage(Component.literal("Your kingdom could not be founded. Please try again.")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        // Mirror the owner UUID into the BE for cheap local queries. POINTER:
        // BlockEntity lookup MUST go through level.getBlockEntity; we cannot
        // hold a reference across the claim call because chunk unload is
        // possible on slow paths.
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SettlementHearthBlockEntity hearthBE) {
            hearthBE.setOwnerUUID(player.getUUID());
        }

        player.sendSystemMessage(Component.literal(defaultName + " has been founded!")
                .withStyle(ChatFormatting.GOLD));
        return InteractionResult.SUCCESS;
    }
}
