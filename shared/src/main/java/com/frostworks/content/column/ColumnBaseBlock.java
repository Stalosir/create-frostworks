package com.frostworks.content.column;

import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Column base: layer 1 of the distillation tower, 2 x 2 of them. The only block of the tower that
 * connects: pipes on its 12 outer and lower faces, a shaft from below. The tower's controller is always one of them.
 * <p>
 * {@link #SHAPE} is the look only, read on the neighbours as the block is placed (alone, pair, corner of a 2 x 2),
 * whether the tower works or not; {@link #FORMED} and {@link #CORNER} keep driving pipes and shaft.
 */
public class ColumnBaseBlock extends TowerBlock {
    /** Part of a valid tower: pipes, shaft and storage only then. */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    /**
     * The look: alone, half of a pair (towards its neighbour), a corner of a 2 x 2, or the elbow of an L of 3 (a 2 x 2
     * with its diagonal missing); corners and elbows are named after their place in the square.
     */
    public enum Shape implements StringRepresentable {
        SINGLE, PAIR_EAST, PAIR_SOUTH, PAIR_WEST, PAIR_NORTH, CORNER_NW, CORNER_NE, CORNER_SE, CORNER_SW,
        ELBOW_NW, ELBOW_NE, ELBOW_SE, ELBOW_SW;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);

    public ColumnBaseBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CORNER, Corner.NORTH_WEST).setValue(FORMED, false)
                .setValue(SHAPE, Shape.SINGLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FORMED, SHAPE);
    }

    @Override
    public BlockState inTower(BlockState state, boolean valid, int layer, int height, Corner corner) {
        return state.setValue(CORNER, valid ? corner : Corner.NORTH_WEST).setValue(FORMED, valid);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(SHAPE, shape(context.getLevel(), context.getClickedPos()));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction side, BlockState neighbour, LevelAccessor level,
                                  BlockPos pos, BlockPos neighbourPos) {
        state = super.updateShape(state, side, neighbour, level, pos, neighbourPos);
        return side.getAxis().isHorizontal() ? state.setValue(SHAPE, shape(level, pos)) : state;
    }

    // A block placed or broken changes the look of its diagonal neighbours too (corner <-> elbow), which get no
    // shape update from it: refresh them.

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!oldState.is(this))
            refreshDiagonals(level, pos);
        BlockState now = level.getBlockState(pos); // placed without a placement context (/fill, setBlock): its own look too
        if (now.is(this) && now.getValue(SHAPE) != shape(level, pos))
            level.setBlock(pos, now.setValue(SHAPE, shape(level, pos)), Block.UPDATE_ALL);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!newState.is(this)) {
            refreshDiagonals(level, pos);
            Corner corner = switch (state.getValue(SHAPE)) {
                case CORNER_NE -> Corner.NORTH_EAST;
                case CORNER_SE -> Corner.SOUTH_EAST;
                case CORNER_SW -> Corner.SOUTH_WEST;
                default -> Corner.NORTH_WEST;
            };
            ColumnLayers.detach(level, pos, corner, pos.getY() + 1); // the column above has nothing to stand on
        }
    }

    private static void refreshDiagonals(Level level, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx += 2)
            for (int dz = -1; dz <= 1; dz += 2) {
                BlockPos p = pos.offset(dx, 0, dz);
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof ColumnBaseBlock && s.getValue(SHAPE) != shape(level, p))
                    level.setBlock(p, s.setValue(SHAPE, shape(level, p)), Block.UPDATE_ALL);
            }
    }

    /** One neighbour on X and one on Z: a corner (an elbow without the diagonal); a single neighbour: a pair. */
    static Shape shape(BlockGetter level, BlockPos pos) {
        boolean e = same(level, pos.east()), w = same(level, pos.west());
        boolean s = same(level, pos.south()), n = same(level, pos.north());
        if (e != w && n != s) {
            boolean full = same(level, pos.offset(e ? 1 : -1, 0, s ? 1 : -1));
            if (full)
                return e ? (s ? Shape.CORNER_NW : Shape.CORNER_SW) : (s ? Shape.CORNER_NE : Shape.CORNER_SE);
            return e ? (s ? Shape.ELBOW_NW : Shape.ELBOW_SW) : (s ? Shape.ELBOW_NE : Shape.ELBOW_SE);
        }
        int count = (e ? 1 : 0) + (w ? 1 : 0) + (s ? 1 : 0) + (n ? 1 : 0);
        if (count != 1)
            return Shape.SINGLE;
        return e ? Shape.PAIR_EAST : w ? Shape.PAIR_WEST : s ? Shape.PAIR_SOUTH : Shape.PAIR_NORTH;
    }

    private static boolean same(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof ColumnBaseBlock;
    }
}
