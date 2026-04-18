package com.femtendo.kingdombuilder.blockentities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stub BlockEntity for the Kingdom Silo.
 *
 * POINTER: System 1 registry stub. System 9 will attach a SiloItemHandler (food-only
 * insert, EMPTY-only extract) and wire it as an ITEM_HANDLER capability. The silo's
 * inventory feeds the TubeNetwork teleport-based delivery system — see
 * food/TubeNetwork.java (stub, System 9).
 */
public class KingdomSiloBlockEntity extends BlockEntity {
    public KingdomSiloBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KINGDOM_SILO_BE.get(), pos, state);
    }
}
