package com.frostworks.content.expander;

import com.frostworks.content.push.FrontPushBlock;
import com.frostworks.registry.FwBlockEntities;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;

import com.simibubi.create.content.fluids.pipes.AxisPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.kinetics.base.IRotate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Expander: takes Frosium through its back flange, pushes cold gas out of its front flange, and gives stress
 * capacity back through a shaft across it (its two other horizontal faces). It consumes no rotation. Top and bottom
 * take nothing (Before, the shaft was at the back and the liquid came in on the four other faces,
 * top and bottom included).
 * <p>
 * Our model has its two flanges on one axis (the fluids) and its two frames on the other (the shaft).
 * {@code FACING} is the gas outlet; the blockstate turns the flanges onto the facing axis.
 * <p>
 * It is only ever placed and turned <b>horizontally</b>, so its shaft is horizontal and the black screen
 * of our model (the model's north face) looks at the sky (rotations in the blockstate, as the compressor).
 */
public class ExpanderBlock extends FrontPushBlock {

    public ExpanderBlock(Properties properties) {
        super(properties, false);
    }

    /** The shaft goes across, through the two faces beside the flanges. */
    public static Direction.Axis shaftAxis(BlockState state) {
        return state.getValue(FACING).getClockWise().getAxis();
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return shaftAxis(state);
    }

    @Override
    public boolean hasShaftTowards(net.minecraft.world.level.LevelReader world, BlockPos pos, BlockState state,
                                   Direction face) {
        return face.getAxis() == shaftAxis(state);
    }

    /**
     * A vertical facing becomes the player's horizontal one; then it lines up with what is around it, if anything: of
     * the four horizontal facings, the one that best fits its neighbours (see {@link #fit}). Nothing around: as placed.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null)
            return null;
        if (state.getValue(FACING).getAxis().isVertical())
            state = state.setValue(FACING, context.getHorizontalDirection());
        return state.setValue(FACING, alignedFacing(context.getLevel(), context.getClickedPos(), state.getValue(FACING)));
    }

    /** The horizontal facing that best fits the neighbours; {@code start} if none fits better. */
    public static Direction alignedFacing(Level level, BlockPos pos, Direction start) {
        Direction best = start;
        int bestFit = fit(level, pos, best);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            int f = fit(level, pos, facing);
            if (f > bestFit) {
                best = facing;
                bestFit = f;
            }
        }
        return best;
    }

    /**
     * How well a facing fits the neighbours: a shaft (of a shaft, a cogwheel, a gearbox...) meeting one
     * of the two shaft faces, 4 each; a pipe or a tank at the front, where the gas goes out, 3; at the back, where the
     * liquid comes in, 2.
     */
    static int fit(Level level, BlockPos pos, Direction facing) {
        int fit = 0;
        for (Direction side : new Direction[]{facing.getClockWise(), facing.getCounterClockWise()}) {
            BlockPos at = pos.relative(side);
            BlockState kinetic = level.getBlockState(at);
            if (kinetic.getBlock() instanceof IRotate rotate && rotate.hasShaftTowards(level, at, kinetic, side.getOpposite()))
                fit += 4;
        }
        if (fluidAt(level, pos, facing))
            fit += 3;
        if (fluidAt(level, pos, facing.getOpposite()))
            fit += 2;
        return fit;
    }

    /** A pipe (or anything with a fluid handler) on this side. */
    private static boolean fluidAt(Level level, BlockPos pos, Direction side) {
        BlockPos p = pos.relative(side);
        BlockState state = level.getBlockState(p);
        return FluidPipeBlock.isPipe(state) || state.getBlock() instanceof AxisPipeBlock
                || com.frostworks.FwCaps.fluidAt(level, p, side.getOpposite()) != null;
    }

    /** The hitbox follows our model, in each of its orientations. */
    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                                              BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return com.frostworks.shape.BlockShapes.of(state);
    }

    /** The wrench turns it a quarter around the vertical, never up or down. */
    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        Direction facing = originalState.getValue(FACING);
        return originalState.setValue(FACING, facing.getAxis().isVertical() ? Direction.NORTH : facing.getClockWise());
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Class<PumpBlockEntity> getBlockEntityClass() {
        return (Class) ExpanderBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PumpBlockEntity> getBlockEntityType() {
        return FwBlockEntities.EXPANDER.get();
    }
}
