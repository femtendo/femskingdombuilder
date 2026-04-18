package com.femtendo.kingdombuilder.blockentities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stub BlockEntity for the Iron Tube.
 *
 * POINTER: System 1 registry stub. System 8 introduces connectedFaces /
 * forcedDisconnects EnumSets (bitmask-serialized) and a facades EnumMap
 * (NbtUtils.writeBlockState / readBlockState). When mutating facade state you MUST
 * call {@code level.sendBlockUpdated(pos, old, new, flags)} so the client re-bakes
 * the facade-aware baked model; {@code setChanged()} alone will NOT trigger a
 * model re-bake on the client.
 */
public class IronTubeBlockEntity extends BlockEntity {
    public IronTubeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IRON_TUBE_BE.get(), pos, state);
    }
}
