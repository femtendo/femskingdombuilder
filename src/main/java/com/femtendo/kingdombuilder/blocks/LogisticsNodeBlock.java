package com.femtendo.kingdombuilder.blocks;

import com.femtendo.kingdombuilder.blockentities.LogisticsNodeBlockEntity;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Logistics Node — an in-world deposit endpoint that bridges tech-mod item
 * pipes (Create, Mekanism, hoppers, etc.) to the owning kingdom's shared vault
 * (System 4 of {@code kingdom_builder_architecture_plan.md}).
 *
 * <p>The block itself has NO gameplay behavior of its own. All work lives on
 * the {@link LogisticsNodeBlockEntity} which publishes an
 * {@link net.minecraftforge.items.IItemHandler} capability lazily. This class
 * is the minimum shape needed to satisfy the {@code EntityBlock} /
 * {@code BaseEntityBlock} contract in Forge 1.21.1:
 * {@link #newBlockEntity}, {@link #getRenderShape}, and the
 * {@link #CODEC}/{@link #codec()} pair.</p>
 *
 * <p>POINTER (mirror of SettlementHearthBlock): Kept structurally identical to
 * {@link SettlementHearthBlock} on purpose — both are "BaseEntityBlock shells
 * that only exist to spawn a BE". When System 8 and 9 add KingdomSiloBlock /
 * IronTubeBlock, follow this same template: {@code simpleCodec}, overridden
 * {@code codec()}, {@code RenderShape.MODEL}, and a one-line
 * {@code newBlockEntity}.</p>
 */
public class LogisticsNodeBlock extends BaseEntityBlock {

    /**
     * POINTER (Forge 1.21.1): {@link BaseEntityBlock} is abstract and requires
     * a {@link MapCodec} for block-state data-pack loading. {@link #simpleCodec}
     * is the one-liner for blocks whose only ctor arg is
     * {@link net.minecraft.world.level.block.state.BlockBehaviour.Properties}.
     * See {@code EnchantingTableBlock} in vanilla for the same pattern.
     */
    public static final MapCodec<LogisticsNodeBlock> CODEC = simpleCodec(LogisticsNodeBlock::new);

    public LogisticsNodeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LogisticsNodeBlockEntity(pos, state);
    }

    /**
     * POINTER: Force {@link RenderShape#MODEL}. BaseEntityBlock defaults to
     * {@link RenderShape#INVISIBLE} (vanilla pattern for BE-rendered blocks
     * like Shulker Box / Enchanting Table). We want the regular JSON block
     * model, so we override. Same rationale as SettlementHearthBlock — see its
     * comments for a longer explanation.
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
