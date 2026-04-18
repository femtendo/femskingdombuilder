package com.femtendo.kingdombuilder.blockentities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stub BlockEntity for the Logistics Node.
 *
 * POINTER: System 1 registry stub. System 4 will extend this to override
 * {@code getCapability(Capability, Direction)} and expose a LazyOptional-wrapped
 * KingdomVaultItemHandler. Remember to call {@code vaultCapability.invalidate()}
 * from {@code setRemoved()} in the System 4 implementation — failing to do so
 * leaks capability references on chunk unload.
 */
public class LogisticsNodeBlockEntity extends BlockEntity {
    public LogisticsNodeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOGISTICS_NODE_BE.get(), pos, state);
    }
}
