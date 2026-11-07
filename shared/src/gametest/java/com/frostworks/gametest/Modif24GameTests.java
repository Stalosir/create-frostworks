package com.frostworks.gametest;

import com.frostworks.Frostworks;
import com.frostworks.content.passage.BeltPassage;
import com.frostworks.content.vent.VentBlock;
import com.frostworks.overspeed.Overspeed;
import com.frostworks.overspeed.OverspeedRig;
import com.frostworks.registry.FwBlocks;
import com.frostworks.room.Room;
import com.frostworks.room.RoomManager;
import com.frostworks.room.TestRoomBuilder;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The first four findings of the cold room audit, each on
 * the overspeed rig's cold room.
 */
public final class Modif24GameTests {
    private Modif24GameTests() {}

    private static final String SMALL = "empty_16x10x16";

    private static OverspeedRig.Layout rig(GameTestHelper helper) {
        return OverspeedGameTests.roomRig(helper);
    }

    /** A1: three shaft passages in a row inside the room, as decoration: the middle one is in the room too. */
    @GameTest(template = SMALL, timeoutTicks = 300)
    public static void shaftPassagesInARowAreInTheRoom(GameTestHelper helper) {
        OverspeedRig.Layout rig = rig(helper);
        ServerLevel level = helper.getLevel();
        BlockState passage = FwBlocks.SHAFT_PASSAGE.getDefaultState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        BlockPos middle = rig.corner().offset(2, 2, 4);
        helper.startSequence()
                .thenExecute(() -> {
                    for (int dx = -1; dx <= 1; dx++)
                        level.setBlock(middle.offset(dx, 0, 0), passage, Block.UPDATE_ALL);
                })
                .thenWaitUntil(() -> OverspeedGameTests.assertCold(helper, rig))
                .thenExecute(() -> helper.assertTrue(Overspeed.inColdRoom(level, middle),
                        "the middle shaft passage is in no cold room"))
                .thenSucceed();
    }

    /**
     * B1: a Create casing put on a belt passage takes its mark off with no block change: the room is told, the belt
     * segment is a room cell again.
     */
    @GameTest(template = SMALL, timeoutTicks = 300)
    public static void casingOnABeltPassageOpensItInTheRoom(GameTestHelper helper) {
        OverspeedRig.Layout rig = rig(helper);
        ServerLevel level = helper.getLevel();
        BlockPos end = OverspeedGameTests.belt(helper, rig, 4), segment = end.north();
        RoomManager rooms = RoomManager.get(level);
        helper.startSequence()
                .thenExecute(() -> helper.assertTrue(BeltPassage.encase(level, segment), "not encased"))
                .thenWaitUntil(() -> {
                    OverspeedGameTests.assertCold(helper, rig);
                    helper.assertTrue(rooms.roomAt(segment) == null, "the passage is still a room cell");
                })
                .thenExecute(() -> {
                    if (level.getBlockEntity(segment) instanceof BeltBlockEntity be)
                        BeltPassage.unmark(be);
                })
                .thenWaitUntil(() -> helper.assertTrue(rooms.roomAt(segment) != null,
                        "the belt is no passage any more, but the room still counts it as a wall"))
                .thenSucceed();
    }

    /** B2: a vent of the room turned in place (as with the wrench) blows into a wall now: it leaves its room. */
    @GameTest(template = SMALL, timeoutTicks = 300)
    public static void turnedVentLeavesItsRoom(GameTestHelper helper) {
        OverspeedRig.Layout rig = rig(helper);
        ServerLevel level = helper.getLevel();
        BlockPos vent = rig.corner().offset(1, TestRoomBuilder.SIZE_Y - 1, 1);
        RoomManager rooms = RoomManager.get(level);
        Room[] before = {null};
        helper.startSequence()
                .thenWaitUntil(() -> OverspeedGameTests.assertCold(helper, rig))
                .thenExecute(() -> {
                    before[0] = rooms.roomOfVent(vent);
                    helper.assertTrue(before[0] != null, "the vent has no room");
                    level.setBlock(vent, level.getBlockState(vent).setValue(VentBlock.FACING, Direction.NORTH),
                            Block.UPDATE_ALL);
                })
                .thenWaitUntil(() -> helper.assertTrue(rooms.roomOfVent(vent) == null,
                        "the vent blows into a wall, its old room still counts it"))
                .thenSucceed();
    }

    /**
     * C1: a vent of a saved room unloads (its chunk) while the room's own cells stay loaded: the room waits for it,
     * past the 10 s a saved room is given.
     */
    @GameTest(template = SMALL, timeoutTicks = 400)
    public static void savedRoomWaitsForItsUnloadedVent(GameTestHelper helper) {
        OverspeedRig.Layout rig = rig(helper);
        ServerLevel level = helper.getLevel();
        BlockPos vent = rig.corner().offset(1, TestRoomBuilder.SIZE_Y - 1, 1);
        RoomManager rooms = RoomManager.get(level);
        helper.startSequence()
                .thenWaitUntil(() -> OverspeedGameTests.assertCold(helper, rig))
                .thenExecute(() -> rooms.ventUnloaded(vent))
                .thenIdle(260)
                .thenExecute(() -> {
                    Room room = rooms.roomAt(rig.interior());
                    rooms.ventAdded(vent); // back, as when its chunk loads
                    helper.assertTrue(room != null, "the saved room was dropped while its vent was unloaded");
                })
                .thenSucceed();
    }
}
