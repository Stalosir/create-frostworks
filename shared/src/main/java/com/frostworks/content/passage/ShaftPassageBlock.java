package com.frostworks.content.passage;

import com.frostworks.registry.FwBlockEntities;
import com.frostworks.registry.FwBlocks;
import com.simibubi.create.content.decoration.encasing.EncasedBlock;
import com.simibubi.create.content.kinetics.base.AbstractEncasedShaftBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Shaft passage: an encased shaft that carries rotation through the wall of a cold room, and counts as a wall.
 * <p>
 * It behaves like Create's encased shaft ({@code EncasedShaftBlock}): {@code axis} property placed
 * like any {@code RotatedPillarKineticBlock}, rotation passed straight along its axis, no stress impact, the turning
 * shaft drawn by Create's {@code ShaftRenderer} / {@code ShaftVisual}. It is made like an encased shaft too: a right
 * click on a Create shaft with a cold wall in hand ({@code EncasableBlock} on the shaft, this block registered as its
 * variant in {@code EncasingRegistry}, no mixin).
 */
public class ShaftPassageBlock extends AbstractEncasedShaftBlock implements IBE<KineticBlockEntity>, EncasedBlock {
    public ShaftPassageBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Block getCasing() {
        return FwBlocks.COLD_WALL.get();
    }

    /**
     * Same switch as Create's encased shaft (the block entity keeps its kinetic state). Unlike Create's casings, the
     * cold wall is used up (outside creative): the passage drops itself when broken, so a free casing would be a
     * free passage.
     */
    @Override
    public void handleEncasing(BlockState state, Level level, BlockPos pos, ItemStack heldItem, Player player,
                               InteractionHand hand, BlockHitResult ray) {
        KineticBlockEntity.switchToBlockState(level, pos, defaultBlockState()
                .setValue(RotatedPillarKineticBlock.AXIS, state.getValue(RotatedPillarKineticBlock.AXIS)));
        if (!player.isCreative())
            heldItem.shrink(1);
    }

    @Override
    public Class<KineticBlockEntity> getBlockEntityClass() {
        return KineticBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
        return FwBlockEntities.SHAFT_PASSAGE.get();
    }
}
