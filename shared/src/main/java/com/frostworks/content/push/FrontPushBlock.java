package com.frostworks.content.push;

import com.simibubi.create.content.fluids.pump.PumpBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Base of the blocks that push a fluid out of their front face into Create pipes (compressor, column base, expander).
 * They are Create pumps (FACING = output) so that Create's pipe network finds them when pipes change; rotation comes
 * through a shaft on the back face, and, if {@code cog} is true, through the pump's cogwheel as well.
 */
public abstract class FrontPushBlock extends PumpBlock {
    private final boolean cog;

    protected FrontPushBlock(Properties properties, boolean cog) {
        super(properties);
        this.cog = cog;
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    /** Without the cogwheel, the back shaft is the only rotation connection (like a Create generator). */
    @Override
    public boolean isSmallCog() {
        return cog;
    }
}
