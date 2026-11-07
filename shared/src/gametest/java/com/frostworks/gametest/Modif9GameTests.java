package com.frostworks.gametest;

import com.frostworks.Frostworks;
import com.frostworks.content.passage.BeltPassage;
import com.frostworks.registry.FwBlocks;
import com.frostworks.room.LevelRoomWorld;
import com.frostworks.room.RoomWalls;
import com.frostworks.room.core.RoomScan;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity.CasingType;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** The belt passage. */
public final class Modif9GameTests {
    private Modif9GameTests() {}

    private static final String SMALL = "empty_16x10x16";

    private static RoomScan.Outcome scan(ServerLevel level, BlockPos start) {
        RoomScan scan = new RoomScan(new LevelRoomWorld(level), start.getX(), start.getY(), start.getZ(), 5_000);
        return scan.run();
    }

    /**
     * A closed room of cold walls (inside 3x2x3) crossed through two opposite walls by a Create belt: closed while the
     * two wall segments are belt passages (also once saved and loaded again), open with the belt not encased, and
     * open with Create's own andesite casing (a casing is not a cold wall).
     */
    @GameTest(template = SMALL, timeoutTicks = 40)
    public static void beltPassageKeepsTheRoomClosed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos o = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockState wall = FwBlocks.COLD_WALL.get().defaultBlockState();
        for (int x = 0; x <= 4; x++)
            for (int y = 0; y <= 3; y++)
                for (int z = 0; z <= 4; z++) {
                    boolean shell = x == 0 || y == 0 || z == 0 || x == 4 || y == 3 || z == 4;
                    level.setBlock(o.offset(x, y, z), shell ? wall : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
        BlockPos inside = o.offset(2, 2, 2);
        helper.assertTrue(scan(level, inside) == RoomScan.Outcome.CLOSED, "the room without the belt is closed");

        // The belt along X, through the west and east walls, between two shafts outside
        BlockState shaft = AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
        BlockPos start = o.offset(-1, 1, 2), end = o.offset(5, 1, 2);
        level.setBlock(start, shaft, Block.UPDATE_ALL);
        level.setBlock(end, shaft, Block.UPDATE_ALL);
        BeltConnectorItem.createBelts(level, start, end);
        BlockPos west = o.offset(0, 1, 2), east = o.offset(4, 1, 2);
        helper.assertTrue(level.getBlockState(west).getBlock() instanceof BeltBlock
                && level.getBlockState(east).getBlock() instanceof BeltBlock, "belt through both walls");
        helper.assertTrue(scan(level, inside) != RoomScan.Outcome.CLOSED, "a bare belt opens the room");

        BlockEntity before = level.getBlockEntity(west);
        helper.assertTrue(BeltPassage.encase(level, west) && BeltPassage.encase(level, east), "encased");
        helper.assertTrue(level.getBlockEntity(west) == before, "same belt block entity (items, speed, saving kept)");
        helper.assertTrue(RoomWalls.isWall(level, west, level.getBlockState(west)), "the passage is a wall");
        helper.assertTrue(scan(level, inside) == RoomScan.Outcome.CLOSED, "closed with two belt passages");

        // Saved and loaded again (a chunk reload goes through the same tag)
        BlockEntity be = level.getBlockEntity(west);
        CompoundTag saved = com.frostworks.platform.Mc.saveBlockEntity(be, level);
        BlockEntity loaded = com.frostworks.platform.Mc.loadBlockEntity(west, level.getBlockState(west), saved, level);
        helper.assertTrue(loaded instanceof BeltBlockEntity
                && BeltPassage.is(blockGetter(level, west, loaded), west, level.getBlockState(west)),
                "still a passage once loaded again");

        // Create's andesite casing alone, then no casing at all: open
        BeltPassage.unmark((BeltBlockEntity) level.getBlockEntity(west));
        helper.assertTrue(level.getBlockState(west).getValue(BeltBlock.CASING), "andesite casing kept");
        helper.assertTrue(scan(level, inside) != RoomScan.Outcome.CLOSED, "open with Create's casing");
        ((BeltBlockEntity) level.getBlockEntity(west)).setCasingType(CasingType.NONE);
        helper.assertTrue(scan(level, inside) != RoomScan.Outcome.CLOSED, "open with the belt not encased");
        helper.succeed();
    }

    /** The level, with one block entity swapped for the given one (to read a reloaded block entity). */
    private static net.minecraft.world.level.BlockGetter blockGetter(ServerLevel level, BlockPos pos, BlockEntity be) {
        return new net.minecraft.world.level.BlockGetter() {
            @Override
            public BlockEntity getBlockEntity(BlockPos p) {
                return p.equals(pos) ? be : level.getBlockEntity(p);
            }

            @Override
            public BlockState getBlockState(BlockPos p) {
                return level.getBlockState(p);
            }

            @Override
            public net.minecraft.world.level.material.FluidState getFluidState(BlockPos p) {
                return level.getFluidState(p);
            }

            @Override
            public int getHeight() {
                return level.getHeight();
            }

            @Override
            public int getMinBuildHeight() {
                return level.getMinBuildHeight();
            }
        };
    }
}
