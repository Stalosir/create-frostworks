package com.frostworks.content.column;

import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Column segment: layers 2 to {@code towerMaxLayers} of the distillation tower, 2 x 2 per layer. No
 * connection on any face (no pipe, no shaft). Made of frost tanks (see {@link ColumnLayers}), and frost tanks again
 * once detached from the tower.
 * <p>
 * {@link #PART} picks our model: the only layer of column, its bottom, a middle layer or its top.
 */
public class ColumnSegmentBlock extends TowerBlock
        implements com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement {
    /** Highest layer of a valid tower. */
    public static final BooleanProperty TOP = BooleanProperty.create("top");

    public enum Part implements StringRepresentable {
        SINGLE, BOTTOM, MIDDLE, TOP;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    public ColumnSegmentBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CORNER, Corner.NORTH_WEST).setValue(TOP, false)
                .setValue(PART, Part.SINGLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TOP, PART);
    }

    @Override
    public BlockState inTower(BlockState state, boolean valid, int layer, int height, Corner corner) {
        // the corner even when inert: a layer of column is always a full 2 x 2 (made of frost tanks), it keeps its look
        boolean first = layer <= 1, last = layer >= height - 1;
        Part part = first && last ? Part.SINGLE : first ? Part.BOTTOM : last ? Part.TOP : Part.MIDDLE;
        return state.setValue(CORNER, corner).setValue(TOP, valid && layer == height - 1).setValue(PART, part);
    }

    /** The schematic cannon asks for what the column is made of and drops: a frost tank, not the column itself. */
    @Override
    public com.simibubi.create.content.schematics.requirement.ItemRequirement getRequiredItems(BlockState state,
            net.minecraft.world.level.block.entity.BlockEntity be) {
        return new com.simibubi.create.content.schematics.requirement.ItemRequirement(
                com.simibubi.create.content.schematics.requirement.ItemRequirement.ItemUseType.CONSUME,
                com.frostworks.registry.FwBlocks.RESERVOIR.get().asItem());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!newState.is(this))
            ColumnLayers.detach(level, pos, state.getValue(CORNER), pos.getY());
    }
}
