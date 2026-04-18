package com.femtendo.kingdombuilder.blockentities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stub BlockEntity for the Settlement Hearth.
 *
 * POINTER: Created in System 1 solely so {@link ModBlockEntities#SETTLEMENT_HEARTH_BE}
 * can reference a real constructor via {@code SettlementHearthBlockEntity::new}.
 * System 3 specifies the full behavior: store claiming player's UUID,
 * addAdditionalSaveData / readAdditionalSaveData, called from SettlementHearthBlock.use()
 * after KingdomManager.claimKingdom().
 *
 * POINTER: For this BE to actually spawn when the block is placed, the block class
 * must implement EntityBlock and override newBlockEntity(pos, state). That wiring
 * comes with System 3, NOT System 1.
 */
public class SettlementHearthBlockEntity extends BlockEntity {
    public SettlementHearthBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SETTLEMENT_HEARTH_BE.get(), pos, state);
    }
}
