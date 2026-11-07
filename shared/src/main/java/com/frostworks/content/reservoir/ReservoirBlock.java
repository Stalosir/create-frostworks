package com.frostworks.content.reservoir;

import com.frostworks.content.column.ColumnLayers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.frostworks.registry.FwBlockEntities;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Frost tank (id {@code reservoir}): Create's fluid tank with a block entity type of its own. Create forms its multiblocks per block
 * entity type ({@code ConnectivityHandler.formMulti(be.getType(), ...)}), so reservoirs merge with each other and never
 * with Create's tanks.
 */
public class ReservoirBlock extends FluidTankBlock {

    public ReservoirBlock(Properties properties) {
        super(properties, false);
    }

    // A 2 x 2 of frost tanks on top of the tower becomes column (see ColumnLayers): checked a tick after the tank is
    // placed or a neighbour changes (the tower below, the tanks beside it).

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        level.scheduleTick(pos, this, 1);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour, BlockPos neighbourPos,
                                   boolean moved) {
        super.neighborChanged(state, level, pos, neighbour, neighbourPos, moved);
        level.scheduleTick(pos, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ColumnLayers.tryConvert(level, pos);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Class<FluidTankBlockEntity> getBlockEntityClass() {
        return (Class) ReservoirBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FluidTankBlockEntity> getBlockEntityType() {
        return FwBlockEntities.RESERVOIR.get();
    }
}
