package com.frostworks.content.compressor;

import com.frostworks.registry.FwBlockEntities;
import com.frostworks.content.push.FrontPushBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Compressor. It is a Create pump (FACING = gas output) so that Create's pipe network finds it like any pump
 * (FluidPropagator only notifies {@code PumpBlock}s when pipes change). It draws air in wherever it is set.
 * <p>
 * Our model: gas out through the pipe on the front (+Y of the model) only, rotation in
 * through a shaft on the back (-Y of the model) only. It is no longer a cogwheel for its neighbours (it used to mesh with
 * cogwheels on its sides, like Create's pump): its cogwheel turns inside the cage. Air goes in through the four sides.
 */
public class CompressorBlock extends FrontPushBlock {

    public CompressorBlock(Properties properties) {
        super(properties, false);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Class<PumpBlockEntity> getBlockEntityClass() {
        return (Class) CompressorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PumpBlockEntity> getBlockEntityType() {
        return FwBlockEntities.COMPRESSOR.get();
    }
}
