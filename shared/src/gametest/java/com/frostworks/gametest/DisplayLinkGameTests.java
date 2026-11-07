package com.frostworks.gametest;

import java.util.ArrayList;
import java.util.List;

import com.frostworks.compat.display.RoomReading;
import com.frostworks.content.vent.VentBlock;
import com.frostworks.overspeed.Overspeed;
import com.frostworks.registry.FwBlocks;
import com.frostworks.room.Room;
import com.frostworks.room.RoomManager;
import com.frostworks.room.TestRoomBuilder;
import com.frostworks.room.core.RoomStatus;
import com.simibubi.create.api.behaviour.display.DisplaySource;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

/** The Display Link on a cold vent: what each of its four readings says, as translatable text. */
public final class DisplayLinkGameTests {
    private DisplayLinkGameTests() {}

    private static final String SMALL = "empty_16x10x16";

    /** The first vent of a TestRoomBuilder room (first row of the ceiling). */
    private static BlockPos vent(TestRoomBuilder.Layout layout, int i) {
        return layout.corner().offset(1 + i, TestRoomBuilder.SIZE_Y - 1, 1);
    }

    /** The key of a translatable text, and its arguments as strings. */
    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : "literal: " + c.getString();
    }

    private static List<String> args(Component c) {
        List<String> args = new ArrayList<>();
        if (c.getContents() instanceof TranslatableContents t)
            for (Object a : t.getArgs())
                args.add(String.valueOf(a));
        return args;
    }

    private static Room room(ServerLevel level, TestRoomBuilder.Layout layout) {
        return RoomManager.get(level).roomOfVent(vent(layout, 0));
    }

    /** The four sources are on the vent, under their names. */
    @GameTest(template = SMALL)
    public static void theVentHasItsFourSources(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlock(pos, FwBlocks.VENT.getDefaultState(), Block.UPDATE_ALL);
        List<String> names = new ArrayList<>();
        for (DisplaySource source : DisplaySource.getAll(level, pos))
            names.add(key(source.getName()));
        for (RoomReading reading : RoomReading.values())
            helper.assertTrue(names.contains("frostworks.display_source." + reading.id()), "missing " + reading.id()
                    + " in " + names);
        helper.succeed();
    }

    /** A cold room: COLD, its temperature, the cold's speed, 6 vents out of 6. */
    @GameTest(template = SMALL, timeoutTicks = 200)
    public static void aColdRoomIsRead(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestRoomBuilder.Layout layout = TestRoomBuilder.build(level, helper.absolutePos(new BlockPos(2, 1, 2)), 6);
        helper.succeedWhen(() -> {
            Room r = room(level, layout);
            helper.assertTrue(r != null && r.status() == RoomStatus.COLD, "room not COLD yet");
            BlockPos v = vent(layout, 0);
            helper.assertTrue(key(RoomReading.STATUS.readAt(level, v)).equals("frostworks.status.cold"),
                    "status " + key(RoomReading.STATUS.readAt(level, v)));
            var temperature = RoomReading.TEMPERATURE.readAt(level, v);
            helper.assertTrue(key(temperature).equals("frostworks.display_source.value.temperature")
                    && args(temperature).equals(List.of(String.valueOf(Math.round(r.temperature())))),
                    "temperature " + key(temperature) + " " + args(temperature));
            var speed = RoomReading.MAX_SPEED.readAt(level, v);
            helper.assertTrue(args(speed).equals(List.of(String.valueOf(Overspeed.allowedRpm(r))))
                    && Overspeed.allowedRpm(r) > Overspeed.createLimit(), "speed " + args(speed));
            var vents = RoomReading.VENTS.readAt(level, v);
            helper.assertTrue(args(vents).equals(List.of("6", "6")), "vents " + args(vents));
        });
    }

    /** A room short of vents: INSUFFICIENT, and Create's own speed. */
    @GameTest(template = SMALL, timeoutTicks = 200)
    public static void aRoomThatIsNotColdIsRead(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestRoomBuilder.Layout layout = TestRoomBuilder.build(level, helper.absolutePos(new BlockPos(2, 1, 2)), 5);
        helper.succeedWhen(() -> {
            Room r = room(level, layout);
            helper.assertTrue(r != null && r.status() == RoomStatus.INSUFFICIENT, "room not INSUFFICIENT yet: "
                    + (r == null ? "none" : r.status()));
            BlockPos v = vent(layout, 0);
            helper.assertTrue(key(RoomReading.STATUS.readAt(level, v)).equals("frostworks.status.insufficient"),
                    "status " + key(RoomReading.STATUS.readAt(level, v)));
            helper.assertTrue(args(RoomReading.MAX_SPEED.readAt(level, v)).equals(List.of(String.valueOf(Overspeed.createLimit()))),
                    "speed " + args(RoomReading.MAX_SPEED.readAt(level, v)));
            helper.assertTrue(args(RoomReading.VENTS.readAt(level, v)).equals(List.of("5", "5")),
                    "vents " + args(RoomReading.VENTS.readAt(level, v)));
        });
    }

    /**
     * A vent with cold walls on both of its sides belongs to no room (in the open air it would be in an OPEN room, as the
     * goggles say; only cold walls stop a room, and with one side free the vent turns to blow there): every reading says
     * "no room", without failing.
     */
    @GameTest(template = SMALL, timeoutTicks = 60)
    public static void aVentOutOfAnyRoomSaysSo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlock(pos.above(), FwBlocks.COLD_WALL.getDefaultState(), Block.UPDATE_ALL);
        level.setBlock(pos.below(), FwBlocks.COLD_WALL.getDefaultState(), Block.UPDATE_ALL);
        level.setBlock(pos, FwBlocks.VENT.getDefaultState().setValue(VentBlock.FACING, Direction.UP), Block.UPDATE_ALL);
        helper.runAfterDelay(20, () -> {
            for (RoomReading reading : RoomReading.values())
                helper.assertTrue(key(reading.readAt(level, pos)).equals("frostworks.display_source.no_room"),
                        reading + ": " + key(reading.readAt(level, pos)));
            helper.succeed();
        });
    }

    /** The room changes between two readings (a vent taken away): the next reading is the new room, nothing kept. */
    @GameTest(template = SMALL, timeoutTicks = 300)
    public static void aRoomThatChangesIsReadAgain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestRoomBuilder.Layout layout = TestRoomBuilder.build(level, helper.absolutePos(new BlockPos(2, 1, 2)), 6);
        BlockPos v = vent(layout, 0);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(key(RoomReading.STATUS.readAt(level, v)).equals("frostworks.status.cold"),
                        "not COLD yet"))
                .thenExecute(() -> {
                    helper.assertTrue(args(RoomReading.VENTS.readAt(level, v)).equals(List.of("6", "6")), "vents before");
                    level.setBlock(vent(layout, 5), FwBlocks.COLD_WALL.getDefaultState(), Block.UPDATE_ALL);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(key(RoomReading.STATUS.readAt(level, v)).equals("frostworks.status.insufficient"),
                            "status after " + key(RoomReading.STATUS.readAt(level, v)));
                    helper.assertTrue(args(RoomReading.VENTS.readAt(level, v)).equals(List.of("5", "5")),
                            "vents after " + args(RoomReading.VENTS.readAt(level, v)));
                })
                .thenSucceed();
    }
}
