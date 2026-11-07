package com.frostworks.content.column;

import java.util.Locale;

import com.frostworks.registry.FwBlockEntities;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Distillation tower, common part of its two blocks (two blocks):
 * {@link ColumnBaseBlock} (layer 1 only, the only block with pipes and shaft) and {@link ColumnSegmentBlock} (layers 2
 * to {@code towerMaxLayers}). Both have the same block entity type, so they form one 2 x 2 multiblock with Create's
 * multiblock code ({@code ConnectivityHandler}, as Create's fluid tank). The tower is valid only with a full 2 x 2 of
 * bases at the bottom and full 2 x 2 layers of segments above; anything else stays inert.
 * <p>
 * {@link #CORNER} is set by the block entity once the tower is formed: the models are drawn for the north-west corner
 * and turned for the others.
 */
public abstract class TowerBlock extends KineticBlock implements IBE<TowerBlockEntity>, IWrenchable {

    /** Which corner of the 2 x 2 tower a block is (north = -z, west = -x). */
    public enum Corner implements StringRepresentable {
        NORTH_WEST(0, 0, Direction.NORTH, Direction.WEST),
        NORTH_EAST(1, 0, Direction.NORTH, Direction.EAST),
        SOUTH_EAST(1, 1, Direction.SOUTH, Direction.EAST),
        SOUTH_WEST(0, 1, Direction.SOUTH, Direction.WEST);

        public final int dx, dz;
        /** The two side faces that look out of the tower. */
        public final Direction outA, outB;

        Corner(int dx, int dz, Direction outA, Direction outB) {
            this.dx = dx;
            this.dz = dz;
            this.outA = outA;
            this.outB = outB;
        }

        public static Corner of(int dx, int dz) {
            for (Corner c : values())
                if (c.dx == dx && c.dz == dz)
                    return c;
            return NORTH_WEST;
        }

        public boolean isOutside(Direction side) {
            return side == outA || side == outB;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Corner> CORNER = EnumProperty.create("corner", Corner.class);

    protected TowerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CORNER);
    }

    /** The hitbox follows our model of this state (shape, corner, part). */
    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                                                 net.minecraft.world.phys.shapes.CollisionContext context) {
        return com.frostworks.shape.BlockShapes.of(state);
    }

    // No ambient-occlusion shading between tower blocks, as Create's tank (whose collision shape, for the lighting
    // engine, is not a full block): the inside of the windows and the joins stay lit.
    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    /** A base block of a valid tower: the only one with pipes and a shaft face. */
    public static boolean isBase(BlockState state) {
        return state.getBlock() instanceof ColumnBaseBlock && state.getValue(ColumnBaseBlock.FORMED);
    }

    /** The block state of this block for its place in a tower (validity, layer from the bottom, height, corner). */
    public abstract BlockState inTower(BlockState state, boolean valid, int layer, int height, Corner corner);

    // ---- Rotation: on the outer sides of the base blocks ---------------------------------------------------------------

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        // On the outer sides of a valid tower's bases, never underneath
        return isBase(state) && face.getAxis().isHorizontal() && state.getValue(CORNER).isOutside(face);
    }

    @Override
    protected boolean areStatesKineticallyEquivalent(BlockState oldState, BlockState newState) {
        return super.areStatesKineticallyEquivalent(oldState, newState) && isBase(oldState) == isBase(newState)
                && oldState.getValue(CORNER) == newState.getValue(CORNER);
    }

    // ---- Forming, as Create's fluid tank ---------------------------------------------------------------------------

    // No forming in onPlace, unlike Create's tank: asking for the block entity there creates it at once, the tower
    // forms inside the placement, and the placement then gives the block entity back its first (unformed) state.
    // The new block forms on its first tick instead (TowerBlockEntity.initialize).

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (state.hasBlockEntity() && (state.getBlock() != newState.getBlock() || !newState.hasBlockEntity())
                && level.getBlockEntity(pos) instanceof TowerBlockEntity tower) {
            TowerBlockEntity heir = tower.isController() ? tower.anotherPart() : null;
            level.removeBlockEntity(pos);
            ConnectivityHandler.splitMulti(tower);
            // The controller holds the whole content: hand it to another block of the tower, which gives it to the new
            // controller when the tower forms again (the surplus is lost if the tower is now smaller)
            if (heir != null)
                heir.absorb(tower);
            else if (tower.isController())
                tower.dropIce(); // The last block of the tower
            return;
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public Class<TowerBlockEntity> getBlockEntityClass() {
        return TowerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TowerBlockEntity> getBlockEntityType() {
        return FwBlockEntities.TOWER.get();
    }

    /** For tests: the block entity at a position, if it is a tower block. */
    public static TowerBlockEntity at(LevelReader level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof TowerBlockEntity t ? t : null;
    }
}
