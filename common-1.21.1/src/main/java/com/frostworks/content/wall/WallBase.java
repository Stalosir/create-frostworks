package com.frostworks.content.wall;

import com.simibubi.create.content.decoration.encasing.CasingBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * What the cold wall does differently on Minecraft 1.21.1 (common-1.20.1 has the same class): a right click with
 * an item is useItemOn here.
 */
public abstract class WallBase extends CasingBlock {
    protected WallBase(Properties properties) {
        super(properties);
    }

    /** A right click with this item: PASS if the wall does nothing with it. */
    protected abstract InteractionResult useItem(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                 Player player, InteractionHand hand);

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = useItem(stack, state, level, pos, player, hand);
        if (result == InteractionResult.PASS)
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (result.consumesAction() && result.shouldSwing())
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        return result == InteractionResult.FAIL ? ItemInteractionResult.FAIL : ItemInteractionResult.CONSUME;
    }
}
