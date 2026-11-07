package com.frostworks.room;

import com.frostworks.content.vent.VentBlock;
import com.frostworks.content.vent.VentBlockEntity;
import com.frostworks.registry.FwBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import com.frostworks.content.door.InsulatedDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Builds the reference room: 6 x 4 x 4 interior (96 cells) in a shell of insulated walls (8 x 6 x 6), with an
 * strip curtain door in the north wall and N vents in the ceiling, blowing down, with free gas.
 * Used by /frostworks testroom and by the GameTests.
 */
public final class TestRoomBuilder {
    private TestRoomBuilder() {}

    public static final int SIZE_X = 8, SIZE_Y = 6, SIZE_Z = 6;
    public static final int VOLUME = 6 * 4 * 4;
    public static final int MAX_VENTS = 6 * 4;

    /**
     * @param corner lowest corner of the shell
     * @return the layout of the room that was built
     */
    public static Layout build(ServerLevel level, BlockPos corner, int vents) {
        vents = Math.max(0, Math.min(vents, MAX_VENTS));
        BlockState wall = FwBlocks.COLD_WALL.getDefaultState();

        // Clear the area first, interior included
        for (int x = 0; x < SIZE_X; x++)
            for (int y = 0; y < SIZE_Y; y++)
                for (int z = 0; z < SIZE_Z; z++)
                    level.setBlock(corner.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        for (int x = 0; x < SIZE_X; x++)
            for (int y = 0; y < SIZE_Y; y++)
                for (int z = 0; z < SIZE_Z; z++) {
                    boolean shell = x == 0 || x == SIZE_X - 1 || y == 0 || y == SIZE_Y - 1 || z == 0 || z == SIZE_Z - 1;
                    if (shell)
                        level.setBlock(corner.offset(x, y, z), wall,
                                Block.UPDATE_ALL);
                }

        // Door in the north wall (z = 0)
        BlockPos doorLow = corner.offset(3, 1, 0);
        BlockState door = FwBlocks.INSULATED_DOOR.getDefaultState().setValue(InsulatedDoorBlock.FACING, Direction.NORTH);
        level.setBlock(doorLow, door.setValue(InsulatedDoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
        level.setBlock(doorLow.above(), door.setValue(InsulatedDoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
        level.blockUpdated(doorLow, door.getBlock());
        level.blockUpdated(doorLow.above(), door.getBlock());

        // Vents in the ceiling, row by row
        BlockState vent = FwBlocks.VENT.getDefaultState().setValue(VentBlock.FACING, Direction.DOWN);
        int placed = 0;
        for (int z = 1; z <= 4 && placed < vents; z++)
            for (int x = 1; x <= 6 && placed < vents; x++) {
                BlockPos pos = corner.offset(x, SIZE_Y - 1, z);
                level.setBlock(pos, vent, Block.UPDATE_ALL);
                if (level.getBlockEntity(pos) instanceof VentBlockEntity be)
                    be.setCreativeSupply(true);
                placed++;
            }
        return new Layout(corner);
    }

    public record Layout(BlockPos corner) {
        /** A cell inside the room. */
        public BlockPos interior() {
            return corner.offset(2, 1, 2);
        }

        /** A plain wall block of the east side. */
        public BlockPos eastWall() {
            return corner.offset(SIZE_X - 1, 2, 2);
        }

        public BlockPos doorLower() {
            return corner.offset(3, 1, 0);
        }
    }

    /** Corner of a room built in front of a player looking in {@code facing}. */
    public static BlockPos cornerInFront(BlockPos feet, Direction facing) {
        BlockPos floor = feet.below();
        return switch (facing) {
            case EAST -> floor.offset(2, 0, -2);
            case WEST -> floor.offset(-2 - SIZE_X + 1, 0, -3);
            case SOUTH -> floor.offset(-3, 0, 2);
            default -> floor.offset(-4, 0, -2 - SIZE_Z + 1); // NORTH
        };
    }
}
