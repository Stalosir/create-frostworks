package com.frostworks.content.door;

import org.jetbrains.annotations.Nullable;

import com.frostworks.registry.FwBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Strip curtain door: a fixed frame 2 blocks high with four plastic strips hanging in its opening. It never opens or
 * closes: you walk through the strips, which swing aside and fall back (animated like the flaps of Create's funnels).
 * <ul>
 * <li>Rooms: both halves always count as a wall ({@code RoomWalls}).</li>
 * <li>Model: the lower half draws our whole body model (32 px high); the upper half draws nothing and only
 * carries the outline and the collision of the upper part.</li>
 * <li>Collision: the two posts only. The lintel of the model starts at 28.4 px (1.78 blocks), lower than a standing
 * player (1.8) or a zombie (1.95): it has no collision, so they walk through.</li>
 * </ul>
 * Model space (FACING = NORTH): frame across X, passage along Z, body from z = 3 to z = 14.
 */
public class InsulatedDoorBlock extends DoorBase implements IBE<StripDoorBlockEntity> {
    /** Minecraft 1.21.1 asks a directional block for its codec; on 1.20.1 the method below is unused. */
    public static final MapCodec<InsulatedDoorBlock> CODEC = com.frostworks.platform.Mc.simpleCodec(InsulatedDoorBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    /** Opening of our body model, in pixels: x from 1.95 to 14, up to y = 28.4. */
    public static final double OPENING_MIN_X = 1.95, OPENING_MAX_X = 14, BODY_MIN_Z = 3, BODY_MAX_Z = 14;

    private static final VoxelShape[] OUTLINE = new VoxelShape[4];
    private static final VoxelShape[] POSTS = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            OUTLINE[d.get2DDataValue()] = rotated(d, 0, 16);
            POSTS[d.get2DDataValue()] = Shapes.or(rotated(d, 0, OPENING_MIN_X), rotated(d, OPENING_MAX_X, 16));
        }
    }

    /** Box from x1 to x2 (model space, facing north) across the body depth, full height, turned to {@code facing}. */
    private static VoxelShape rotated(Direction facing, double x1, double x2) {
        double z1 = BODY_MIN_Z, z2 = BODY_MAX_Z;
        return switch (facing) {
            case SOUTH -> Block.box(16 - x2, 0, 16 - z2, 16 - x1, 16, 16 - z1);
            case EAST -> Block.box(16 - z2, 0, x1, 16 - z1, 16, x2);
            case WEST -> Block.box(z1, 0, 16 - x2, z2, 16, 16 - x1);
            default -> Block.box(x1, 0, z1, x2, 16, z2);
        };
    }

    public InsulatedDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    // ---- Shapes -------------------------------------------------------------------------------------------------

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return POSTS[state.getValue(FACING).get2DDataValue()];
    }

    /** Minecraft 1.21.1 (no @Override: on 1.20.1 it is the other one below). */
    public boolean isPathfindable(BlockState state, PathComputationType type) {
        return type == PathComputationType.LAND || type == PathComputationType.AIR;
    }

    /** Minecraft 1.20.1. */
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return isPathfindable(state, type);
    }

    // ---- Placing and breaking: a plain 2-high block ------------------------------------------------------------

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context))
            return null;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection()).setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /** One half disappears with the other (the item drops once, from whichever half was broken). */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean towardsOther = direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);
        if (towardsOther && !(neighbour.is(this) && neighbour.getValue(HALF) != half))
            return Blocks.AIR.defaultBlockState();
        return state;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // ---- Strips ---------------------------------------------------------------------------------------------------

    /** Server: something is inside the frame, the strips swing (event sent to the clients by the block entity). */
    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide)
            return;
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        if (level.getBlockEntity(lower) instanceof StripDoorBlockEntity door)
            door.onEntityInside(entity);
    }

    /** Block events carry the swing to every client in range (and must return true on the server to be sent). */
    @Override
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.triggerEvent(id, param);
    }

    // In creative, breaking the upper half removes the lower one without dropping the door (as vanilla doors): left to
    // the shape update, the lower half would be destroyed with its drops.
    @Override
    protected void beforePlayerDestroys(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState lower = level.getBlockState(below);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, below, Block.getId(lower));
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // Only the lower half has a block entity (it draws the strips)
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? IBE.super.newBlockEntity(pos, state) : null;
    }

    @Override
    public Class<StripDoorBlockEntity> getBlockEntityClass() {
        return StripDoorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends StripDoorBlockEntity> getBlockEntityType() {
        return FwBlockEntities.STRIP_DOOR.get();
    }
}
