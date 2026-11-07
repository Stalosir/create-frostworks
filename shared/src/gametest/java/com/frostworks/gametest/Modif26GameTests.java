package com.frostworks.gametest;

import com.frostworks.Frostworks;
import com.frostworks.overspeed.OverspeedRig;
import com.frostworks.room.Room;
import com.frostworks.room.RoomManager;
import com.frostworks.room.core.RoomStatus;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Ice and snow layers do not melt in a cold room at −10 °C, even under a light. */
public final class Modif26GameTests {
    private Modif26GameTests() {}

    private static final String SMALL = "empty_16x10x16";

    /** Ice, and a snow layer, each under a glowstone (block light 14, over vanilla's 11). */
    private static void place(ServerLevel level, BlockPos ice, BlockPos snow) {
        for (BlockPos p : new BlockPos[]{ice, snow}) {
            level.setBlock(p.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(p.above(), Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(ice, Blocks.ICE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(snow, Blocks.SNOW.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void randomTicks(ServerLevel level, BlockPos pos) {
        for (int i = 0; i < 3; i++)
            level.getBlockState(pos).randomTick(level, pos, level.random);
    }

    @GameTest(template = SMALL, timeoutTicks = 2400)
    public static void iceAndSnowDoNotMeltInAColdRoom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OverspeedRig.Layout rig = OverspeedGameTests.roomRig(helper);
        // in the room, on its floor (the floor is a cold wall: the stone goes one up, the room keeps its walls)
        BlockPos ice = rig.corner().offset(5, 2, 4), snow = rig.corner().offset(6, 2, 4);
        // the witness, out of any room
        BlockPos outIce = helper.absolutePos(new BlockPos(12, 2, 12)), outSnow = helper.absolutePos(new BlockPos(14, 2, 12));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    Room r = RoomManager.get(level).roomAt(rig.interior());
                    helper.assertTrue(r != null && r.status() == RoomStatus.COLD && r.temperature() <= -10f,
                            "room not COLD at -10 yet: " + (r == null ? "none" : r.status() + " " + r.temperature()));
                })
                .thenExecute(() -> {
                    place(level, ice, snow);
                    place(level, outIce, outSnow);
                })
                // the light spreads (it can take a few ticks more on a busy server)
                .thenWaitUntil(() -> {
                    for (BlockPos p : new BlockPos[]{ice, snow, outIce, outSnow})
                        helper.assertTrue(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p) > 11,
                                "no light over 11 yet at " + p);
                })
                .thenExecute(() -> {
                    for (BlockPos p : new BlockPos[]{ice, snow, outIce, outSnow})
                        randomTicks(level, p);
                    helper.assertTrue(level.getBlockState(outIce).is(Blocks.WATER), "witness ice: "
                            + level.getBlockState(outIce) + " (the light does not melt it?)");
                    helper.assertTrue(level.getBlockState(outSnow).isAir(), "witness snow: " + level.getBlockState(outSnow));
                    helper.assertTrue(level.getBlockState(ice).is(Blocks.ICE), "the ice melted in the cold room: "
                            + level.getBlockState(ice));
                    helper.assertTrue(level.getBlockState(snow).is(Blocks.SNOW), "the snow melted in the cold room: "
                            + level.getBlockState(snow));
                })
                .thenSucceed();
    }
}
