package com.frostworks.content.reservoir;

import com.frostworks.content.column.ColumnBaseBlock;
import com.frostworks.content.column.ColumnSegmentBlock;
import com.frostworks.content.column.TowerBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The frost tank's item, with Create's placement help: clicking a frost tank fills its whole layer (Create's tank
 * item), and clicking the top of the distillation tower (its bases or its highest column) lays a full 2 x 2 of frost
 * tanks there, which then becomes column. Four tanks in hand needed, or creative.
 */
public class FrostTankItem extends FluidTankItem {

    public FrostTankItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        layOnTower(context);
        layOnFrostTank(context);
        return super.place(context); // the clicked block, as usual
    }

    /**
     * Create's help on its own tank, for ours (Create's only looks for its own tank type): clicking the top or the bottom
     * of a frost tank wider than one block lays the whole layer above or below it.
     */
    private void layOnFrostTank(BlockPlaceContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        if (player == null || player.isShiftKeyDown() || !face.getAxis().isVertical() || context.replacingClickedOnBlock())
            return;
        if (!(level.getBlockEntity(pos.relative(face.getOpposite())) instanceof ReservoirBlockEntity tank))
            return;
        FluidTankBlockEntity controller = tank.getControllerBE();
        if (controller == null || controller.getWidth() == 1)
            return;
        int width = controller.getWidth();
        BlockPos start = face == Direction.DOWN ? controller.getBlockPos().below()
                : controller.getBlockPos().above(controller.getHeight());
        if (start.getY() != pos.getY())
            return;
        int needed = 0;
        for (int x = 0; x < width; x++)
            for (int z = 0; z < width; z++) {
                BlockState s = level.getBlockState(start.offset(x, 0, z));
                if (s.is(getBlock()))
                    continue;
                if (!s.canBeReplaced())
                    return;
                needed++;
            }
        ItemStack stack = context.getItemInHand();
        if (!player.isCreative() && stack.getCount() < needed)
            return;
        for (int x = 0; x < width; x++)
            for (int z = 0; z < width; z++) {
                BlockPos p = start.offset(x, 0, z);
                if (p.equals(pos) || level.getBlockState(p).is(getBlock()))
                    continue;
                if (!level.isClientSide)
                    level.setBlock(p, getBlock().defaultBlockState(), Block.UPDATE_ALL);
                if (!player.isCreative())
                    stack.shrink(1);
            }
    }

    private void layOnTower(BlockPlaceContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (player == null || player.isShiftKeyDown() || context.getClickedFace() != Direction.UP
                || context.replacingClickedOnBlock())
            return;
        BlockState below = level.getBlockState(pos.below());
        TowerBlock.Corner corner;
        if (below.getBlock() instanceof ColumnBaseBlock)
            corner = switch (below.getValue(ColumnBaseBlock.SHAPE)) {
                case CORNER_NW -> TowerBlock.Corner.NORTH_WEST;
                case CORNER_NE -> TowerBlock.Corner.NORTH_EAST;
                case CORNER_SE -> TowerBlock.Corner.SOUTH_EAST;
                case CORNER_SW -> TowerBlock.Corner.SOUTH_WEST;
                default -> null;
            };
        else if (below.getBlock() instanceof ColumnSegmentBlock)
            corner = below.getValue(TowerBlock.CORNER);
        else
            return;
        if (corner == null)
            return;
        ItemStack stack = context.getItemInHand();
        if (!player.isCreative() && stack.getCount() < 4)
            return;
        BlockPos origin = pos.offset(-corner.dx, 0, -corner.dz);
        for (int x = 0; x < 2; x++)
            for (int z = 0; z < 2; z++)
                if (!level.getBlockState(origin.offset(x, 0, z)).canBeReplaced())
                    return;
        // the three others here, the clicked one by the usual placement
        for (int x = 0; x < 2; x++)
            for (int z = 0; z < 2; z++) {
                BlockPos p = origin.offset(x, 0, z);
                if (p.equals(pos))
                    continue;
                if (!level.isClientSide)
                    level.setBlock(p, getBlock().defaultBlockState(), Block.UPDATE_ALL);
                if (!player.isCreative())
                    stack.shrink(1);
            }
    }
}
