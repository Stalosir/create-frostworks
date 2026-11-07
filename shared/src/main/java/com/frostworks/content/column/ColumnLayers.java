package com.frostworks.content.column;

import com.frostworks.FwConfig;
import com.frostworks.content.reservoir.ReservoirBlock;
import com.frostworks.registry.FwBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The column is built with frost tanks: a 2 x 2 of frost tanks right on top of the tower (on its 2 x 2 of bases, or
 * on its highest full layer of column) becomes a layer of column. A layer missing a block is mended the same way: the
 * frost tanks of a layer whose other blocks are already column become column too. Detached column turns back into
 * frost tanks ({@link #detach}).
 * <p>
 * Only empty frost tanks turn into column (their content would be lost), and the tower keeps its
 * {@code towerMaxLayers}. Breaking a column block gives a frost tank back (loot table).
 */
public final class ColumnLayers {
    private ColumnLayers() {}

    private static boolean detaching;

    /**
     * A block of the tower is gone: the column from {@code fromY} up has lost its footing (the rest of a broken layer,
     * everything above it, or all the column over a broken base). It turns back into frost tanks; putting the missing
     * block back as a frost tank rebuilds it, layer after layer.
     */
    public static void detach(Level level, BlockPos pos, TowerBlock.Corner corner, int fromY) {
        if (detaching || level.isClientSide)
            return; // a column block turning into a tank calls this again: one pass is enough
        detaching = true;
        try {
            for (BlockPos layer = new BlockPos(pos.getX() - corner.dx, fromY, pos.getZ() - corner.dz); ;
                 layer = layer.above()) {
                boolean any = false;
                for (int x = 0; x < 2; x++)
                    for (int z = 0; z < 2; z++) {
                        BlockPos p = layer.offset(x, 0, z);
                        if (level.getBlockState(p).getBlock() instanceof ColumnSegmentBlock) {
                            level.setBlock(p, FwBlocks.RESERVOIR.getDefaultState(), Block.UPDATE_ALL);
                            any = true;
                        }
                    }
                if (!any)
                    return;
            }
        } finally {
            detaching = false;
        }
    }

    /** Called a tick after a frost tank is placed or its neighbours change. */
    public static void tryConvert(ServerLevel level, BlockPos pos) {
        // down the column to the base under this block
        BlockPos below = pos.below();
        int layer = 2;
        while (level.getBlockState(below).getBlock() instanceof ColumnSegmentBlock) {
            below = below.below();
            layer++;
        }
        BlockState base = level.getBlockState(below);
        if (!(base.getBlock() instanceof ColumnBaseBlock) || layer > FwConfig.get(FwConfig.TOWER_MAX_LAYERS))
            return;
        // the base's corner tells where the 2 x 2 is
        BlockPos corner = switch (base.getValue(ColumnBaseBlock.SHAPE)) {
            case CORNER_NW -> below;
            case CORNER_NE -> below.west();
            case CORNER_SE -> below.west().north();
            case CORNER_SW -> below.north();
            default -> null;
        };
        if (corner == null)
            return;
        int top = layer - 1; // height of this layer above the bases
        for (int x = 0; x < 2; x++)
            for (int z = 0; z < 2; z++) {
                for (int y = 1; y < top; y++)
                    if (!(level.getBlockState(corner.offset(x, y, z)).getBlock() instanceof ColumnSegmentBlock))
                        return; // a lower layer is not full column
                BlockPos p = corner.offset(x, top, z);
                BlockState here = level.getBlockState(p);
                if (here.getBlock() instanceof ReservoirBlock) {
                    if (level.getBlockEntity(p) instanceof FluidTankBlockEntity tank) {
                        FluidTankBlockEntity controller = tank.getControllerBE();
                        if (controller != null && !controller.getTankInventory().isEmpty())
                            return; // never lose liquid
                    }
                } else if (!(here.getBlock() instanceof ColumnSegmentBlock))
                    return;
            }
        for (int x = 0; x < 2; x++)
            for (int z = 0; z < 2; z++) {
                BlockPos p = corner.offset(x, top, z);
                if (level.getBlockState(p).getBlock() instanceof ReservoirBlock)
                    level.setBlock(p, FwBlocks.COLUMN_SEGMENT.getDefaultState(), Block.UPDATE_ALL);
            }
    }
}
