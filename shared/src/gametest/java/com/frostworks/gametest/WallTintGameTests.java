package com.frostworks.gametest;

import java.util.List;

import com.frostworks.content.wall.ColdWallBlock;
import com.frostworks.content.wall.WallTint;
import com.frostworks.content.wall.WallTinting;
import com.frostworks.registry.FwBlocks;
import com.frostworks.room.Room;
import com.frostworks.room.RoomManager;
import com.frostworks.room.TestRoomBuilder;
import com.frostworks.room.core.RoomStatus;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** The cold wall tint: a dye colours the walls joined face to face, as a block state the item never carries. */
public final class WallTintGameTests {
    private WallTintGameTests() {}

    private static final String SMALL = "empty_16x10x16";

    private static void walls(GameTestHelper helper, BlockPos... relative) {
        for (BlockPos p : relative)
            helper.getLevel().setBlock(helper.absolutePos(p), FwBlocks.COLD_WALL.getDefaultState(), Block.UPDATE_ALL);
    }

    private static WallTint tint(GameTestHelper helper, BlockPos relative) {
        return helper.getLevel().getBlockState(helper.absolutePos(relative)).getValue(ColdWallBlock.TINT);
    }

    /**
     * An L of walls takes the colour as a wave: the clicked wall at once, then one wall further every 2 ticks; a
     * structure touching it only by a corner keeps its own.
     */
    @GameTest(template = SMALL)
    public static void anLTakesTheColourNotWhatTouchesItByACorner(GameTestHelper helper) {
        BlockPos[] l = {new BlockPos(2, 1, 2), new BlockPos(3, 1, 2), new BlockPos(4, 1, 2), new BlockPos(4, 2, 2),
                new BlockPos(4, 3, 2), new BlockPos(4, 3, 3), new BlockPos(4, 3, 4)};
        // only the corner of (4, 3, 4) touches (5, 4, 5)
        BlockPos[] other = {new BlockPos(5, 4, 5), new BlockPos(6, 4, 5), new BlockPos(6, 4, 6)};
        walls(helper, l);
        walls(helper, other);
        helper.startSequence()
                .thenExecute(() -> {
                    WallTinting.Outcome outcome = WallTinting.dye(helper.getLevel(), helper.absolutePos(l[0]),
                            WallTint.RED, 4096);
                    helper.assertTrue(outcome == WallTinting.Outcome.DYED, "outcome " + outcome);
                    helper.assertTrue(tint(helper, l[0]) == WallTint.RED, "the clicked wall is not red at once");
                    helper.assertTrue(tint(helper, l[1]) == WallTint.NONE, "the wave did not wait");
                })
                .thenIdle(3) // ring 1 at +2 ticks, ring 2 at +4
                .thenExecute(() -> {
                    helper.assertTrue(tint(helper, l[1]) == WallTint.RED, "ring 1 not red after 3 ticks");
                    helper.assertTrue(tint(helper, l[2]) == WallTint.NONE, "ring 2 red too early");
                })
                .thenWaitUntil(() -> {
                    for (BlockPos p : l)
                        helper.assertTrue(tint(helper, p) == WallTint.RED, "L wall " + p.toShortString() + " is "
                                + tint(helper, p));
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    for (BlockPos p : other)
                        helper.assertTrue(tint(helper, p) == WallTint.NONE, "corner wall " + p.toShortString() + " got "
                                + tint(helper, p));
                })
                .thenSucceed();
    }

    /** More walls than the limit: nothing is dyed. */
    @GameTest(template = SMALL)
    public static void aStructureOverTheLimitStaysAsItIs(GameTestHelper helper) {
        BlockPos[] line = new BlockPos[10];
        for (int i = 0; i < line.length; i++)
            line[i] = new BlockPos(2 + i, 1, 3);
        walls(helper, line);
        WallTinting.Outcome outcome = WallTinting.dye(helper.getLevel(), helper.absolutePos(line[0]), WallTint.BLUE, 5);
        helper.assertTrue(outcome == WallTinting.Outcome.TOO_BIG, "outcome " + outcome);
        for (BlockPos p : line)
            helper.assertTrue(tint(helper, p) == WallTint.NONE, "dyed over the limit: " + p.toShortString());
        helper.assertTrue(WallTinting.dye(helper.getLevel(), helper.absolutePos(line[0]), WallTint.BLUE, 10)
                == WallTinting.Outcome.DYED, "not dyed at the limit itself");
        helper.succeed();
    }

    /**
     * The same colour again does nothing, even while its wave is on its way; another colour changes all of it (the older
     * wave stops); gray takes the colour away.
     */
    @GameTest(template = SMALL)
    public static void theSameColourDoesNothingAnotherChangesItAll(GameTestHelper helper) {
        BlockPos[] wall = {new BlockPos(3, 1, 3), new BlockPos(3, 2, 3), new BlockPos(4, 2, 3)};
        walls(helper, wall);
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(wall[0]);
        Runnable allDone = () -> helper.assertTrue(!WallTinting.waveThrough(level, start), "wave still on its way");
        helper.startSequence()
                .thenExecute(() -> {
                    helper.assertTrue(WallTinting.dye(level, start, WallTint.GREEN, 4096) == WallTinting.Outcome.DYED,
                            "first dye");
                    helper.assertTrue(WallTinting.dye(level, start, WallTint.GREEN, 4096) == WallTinting.Outcome.UNCHANGED,
                            "the same colour, its wave on its way, changed something");
                })
                .thenWaitUntil(allDone)
                .thenExecute(() -> {
                    helper.assertTrue(WallTinting.dye(level, start, WallTint.GREEN, 4096) == WallTinting.Outcome.UNCHANGED,
                            "the same colour changed something");
                    helper.assertTrue(WallTinting.dye(level, start, WallTint.PURPLE, 4096) == WallTinting.Outcome.DYED,
                            "second dye");
                })
                .thenWaitUntil(allDone)
                .thenExecute(() -> {
                    for (BlockPos p : wall)
                        helper.assertTrue(tint(helper, p) == WallTint.PURPLE, "wall " + p.toShortString() + " is "
                                + tint(helper, p));
                    helper.assertTrue(WallTint.of(net.minecraft.world.item.DyeColor.GRAY) == WallTint.NONE,
                            "gray is not the plain wall");
                    helper.assertTrue(WallTinting.dye(level, start, WallTint.NONE, 4096) == WallTinting.Outcome.DYED,
                            "gray dye");
                })
                .thenWaitUntil(allDone)
                .thenExecute(() -> {
                    for (BlockPos p : wall)
                        helper.assertTrue(tint(helper, p) == WallTint.NONE, "gray left " + p.toShortString() + " "
                                + tint(helper, p));
                })
                .thenSucceed();
    }

    /** A coloured wall drops, and is picked as, the plain cold wall: the colour never reaches an item. */
    @GameTest(template = SMALL)
    public static void aColouredWallDropsAndIsPickedAsThePlainWall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockState coloured = FwBlocks.COLD_WALL.getDefaultState().setValue(ColdWallBlock.TINT, WallTint.BLACK);
        level.setBlock(pos, coloured, Block.UPDATE_ALL);
        ItemStack plain = new ItemStack(FwBlocks.COLD_WALL.get());
        List<ItemStack> drops = Block.getDrops(coloured, level, pos, null);
        helper.assertTrue(drops.size() == 1 && ItemStack.matches(drops.get(0), plain), "drops " + drops);
        ItemStack picked = coloured.getBlock().getCloneItemStack(level, pos, coloured);
        helper.assertTrue(ItemStack.matches(picked, plain), "picked " + picked);
        helper.succeed();
    }

    /**
     * The searches started from the 6 vents of this room (TestRoomBuilder: first row of the ceiling); the other tests
     * run beside it, with rooms of their own.
     */
    private static long scansFromItsVents(ServerLevel level, TestRoomBuilder.Layout layout) {
        long n = 0;
        for (int x = 1; x <= 6; x++)
            n += RoomManager.get(level).scansFrom(layout.corner().offset(x, TestRoomBuilder.SIZE_Y - 1, 1));
        return n;
    }

    /** Dyeing the walls of a cold room changes nothing for the room: same number, still cold, no new search. */
    @GameTest(template = SMALL, timeoutTicks = 300)
    public static void dyeingARoomsWallsLeavesTheRoomAlone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestRoomBuilder.Layout layout = TestRoomBuilder.build(level, helper.absolutePos(new BlockPos(2, 1, 2)), 6);
        int[] before = new int[1];
        long[] scans = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    Room r = RoomManager.get(level).roomAt(layout.interior());
                    helper.assertTrue(r != null && r.status() == RoomStatus.COLD, "room not COLD yet");
                    helper.assertTrue(!RoomManager.get(level).isBusy(), "still searching");
                })
                .thenExecute(() -> {
                    before[0] = RoomManager.get(level).roomAt(layout.interior()).id;
                    scans[0] = scansFromItsVents(level, layout);
                    WallTinting.Outcome outcome = WallTinting.dye(level, helper.absolutePos(new BlockPos(2, 1, 2)),
                            WallTint.CYAN, 4096);
                    helper.assertTrue(outcome == WallTinting.Outcome.DYED, "outcome " + outcome);
                })
                .thenWaitUntil(() -> helper.assertTrue(!WallTinting.waveThrough(level,
                        helper.absolutePos(new BlockPos(2, 1, 2))), "the wave is still on its way"))
                .thenIdle(20)
                .thenExecute(() -> {
                    Room r = RoomManager.get(level).roomAt(layout.interior());
                    helper.assertTrue(r != null && r.id == before[0] && r.status() == RoomStatus.COLD,
                            "room after the dye: " + (r == null ? "none" : r.id + " " + r.status()));
                    long after = scansFromItsVents(level, layout);
                    helper.assertTrue(after == scans[0], "searched again: " + scans[0] + " -> " + after);
                })
                .thenSucceed();
    }
}
