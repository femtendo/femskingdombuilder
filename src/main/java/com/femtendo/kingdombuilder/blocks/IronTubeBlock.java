package com.femtendo.kingdombuilder.blocks;

import com.femtendo.kingdombuilder.blockentities.IronTubeBlockEntity;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Iron Tube block. Thin shell over {@link BaseEntityBlock} so placing an
 * IRON_TUBE spawns the matching {@link IronTubeBlockEntity} that the Wrench
 * (System 11) interacts with.
 *
 * <h2>System 11 scope</h2>
 *
 * POINTER: This class was introduced by System 11 so the Wrench acceptance
 * criterion ("Right-clicking an Iron Tube face with Wrench toggles force-
 * disconnect for that face") is reachable. The architecture plan's System 1
 * dev notes explicitly warned: "The 5 blocks are currently plain Block
 * instances, NOT BaseEntityBlock ... System 8 will each swap their supplier
 * to a dedicated BaseEntityBlock subclass." System 11 required this swap
 * early so we promoted it here. System 8 should KEEP this class and add the
 * X-ray renderer + facade-aware baked model on top — do not re-create the
 * block class.
 *
 * POINTER: Mirrors {@link SettlementHearthBlock} / {@link LogisticsNodeBlock}
 * shape intentionally — {@code simpleCodec} + {@code codec()} + RenderShape
 * override + {@code newBlockEntity}. Keep that consistency when extending.
 */
public class IronTubeBlock extends BaseEntityBlock {

    public static final MapCodec<IronTubeBlock> CODEC = simpleCodec(IronTubeBlock::new);

    public IronTubeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IronTubeBlockEntity(pos, state);
    }

    /**
     * POINTER: BaseEntityBlock defaults to {@link RenderShape#INVISIBLE} because
     * most vanilla BE blocks render via a BER. We force MODEL so the JSON block
     * model renders at all. System 8's X-ray overlay is an ADDITIONAL BER on
     * top of the JSON model, not a replacement — if it ever swaps to INVISIBLE,
     * the wrench's click target face will stop registering hit results against
     * the model geometry.
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
