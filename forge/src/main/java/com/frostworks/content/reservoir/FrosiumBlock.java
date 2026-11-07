package com.frostworks.content.reservoir;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

/** Frosium in the world: whatever is in it freezes as in powder snow (frost on the screen, then damage). */
public class FrosiumBlock extends LiquidBlock {
    public FrosiumBlock(java.util.function.Supplier<? extends FlowingFluid> fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        entity.setIsInPowderSnow(true);
    }
}
