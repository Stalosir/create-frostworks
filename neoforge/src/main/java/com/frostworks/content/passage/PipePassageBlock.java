package com.frostworks.content.passage;

import com.frostworks.registry.FwBlockEntities;
import com.frostworks.registry.FwBlocks;
import com.simibubi.create.content.decoration.encasing.EncasedBlock;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.AxisPipeBlock;
import com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Insulated pipe passage: carries a fluid through the wall of a cold room, as the shaft passage carries
 * rotation, and counts as a wall.
 * <p>
 * It is Create's straight pipe ({@code AxisPipeBlock}, the base of the glass pipe): {@code axis} property placed like a
 * pillar, fluid passed straight along its axis only (Create's straight-pipe transport, open on the two faces of the
 * axis), with Create's {@code StraightPipeBlockEntity} keeping the fluid on its way. It is made like Create's encased
 * pipe: a right click on a Create fluid pipe with a cold wall in hand ({@code EncasableBlock} on the pipe, this block
 * registered as its variant in {@code EncasingRegistry}). Unlike the glass pipe, a copper casing does nothing on it.
 */
public class PipePassageBlock extends AxisPipeBlock implements IBE<StraightPipeBlockEntity>, EncasedBlock {

    public PipePassageBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Block getCasing() {
        return FwBlocks.COLD_WALL.get();
    }

    /**
     * The pipe becomes a passage along its own axis (or, for a bent pipe, the axis of the clicked face), keeping its
     * fluid as Create does when it encases a pipe. The cold wall is used up (outside creative): the passage drops
     * itself when broken.
     */
    @Override
    public void handleEncasing(BlockState state, Level level, BlockPos pos, ItemStack heldItem, Player player,
                               InteractionHand hand, BlockHitResult ray) {
        Direction.Axis axis = FluidPropagator.getStraightPipeAxis(state);
        if (axis == null)
            axis = ray.getDirection().getAxis();
        FluidTransportBehaviour.cacheFlows(level, pos);
        level.setBlockAndUpdate(pos, defaultBlockState().setValue(AXIS, axis));
        FluidTransportBehaviour.loadFlows(level, pos);
        if (!player.isCreative())
            heldItem.shrink(1);
    }

    /** A full block: it is part of the wall. */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    /** No copper casing on it (the glass pipe's encasing). */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos,
                                       Player player) {
        return new ItemStack(this);
    }

    @Override
    public Class<StraightPipeBlockEntity> getBlockEntityClass() {
        return StraightPipeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends StraightPipeBlockEntity> getBlockEntityType() {
        return FwBlockEntities.PIPE_PASSAGE.get();
    }
}
