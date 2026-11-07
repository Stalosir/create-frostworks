package com.frostworks.content.door;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the strip curtain door does differently on Minecraft 1.20.1 (common-1.21.1 has the same
 * class): a player breaking a block returns void here.
 */
public abstract class DoorBase extends HorizontalDirectionalBlock {
    protected DoorBase(Properties properties) {
        super(properties);
    }

    /** Before a player breaks it: the shared door clears its other half. */
    protected abstract void beforePlayerDestroys(Level level, BlockPos pos, BlockState state, Player player);

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        beforePlayerDestroys(level, pos, state, player);
        super.playerWillDestroy(level, pos, state, player);
    }
}
