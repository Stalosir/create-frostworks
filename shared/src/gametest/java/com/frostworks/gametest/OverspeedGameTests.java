package com.frostworks.gametest;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RotatedPillarBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.frostworks.room.TestRoomBuilder;
import com.frostworks.content.passage.BeltPassage;
import com.frostworks.content.vent.VentBlock;
import com.frostworks.content.vent.VentBlockEntity;
import java.util.Locale;

import com.frostworks.Frostworks;
import com.frostworks.FwConfig;
import com.frostworks.registry.FwBlocks;
import com.frostworks.overspeed.Overspeed;
import com.frostworks.overspeed.OverspeedRig;
import com.frostworks.room.Room;
import com.frostworks.room.RoomManager;
import com.frostworks.room.core.RoomStatus;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.gauge.StressGaugeBlockEntity;
import com.simibubi.create.content.kinetics.gauge.SpeedGaugeBlockEntity;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** overspeed in a COLD room (method A). Rigs: see {@link OverspeedRig}. */
public final class OverspeedGameTests {
    private OverspeedGameTests() {}

    private static final String SMALL = "empty_16x10x16";

    static OverspeedRig.Layout roomRig(GameTestHelper helper) {
        return OverspeedRig.buildInRoom(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 0)));
    }

    private static OverspeedRig.Layout witnessRig(GameTestHelper helper) {
        return OverspeedRig.buildWitness(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 9)));
    }

    static void assertCold(GameTestHelper helper, OverspeedRig.Layout rig) {
        Room room = RoomManager.get(helper.getLevel()).roomAt(rig.interior());
        if (room == null || room.status() != RoomStatus.COLD)
            helper.fail("room not COLD yet: " + (room == null ? "none" : room.status() + " " + room.capacity() + "/"
                    + room.volume));
    }

    static float speed(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getBlockEntity(pos) instanceof KineticBlockEntity be ? be.getSpeed() : Float.NaN;
    }

    private static float stress(GameTestHelper helper, OverspeedRig.Layout rig) {
        return helper.getLevel().getBlockEntity(rig.gauge()) instanceof StressGaugeBlockEntity g
                ? g.getNetworkStress() : Float.NaN;
    }

    private static String sawContent(GameTestHelper helper, OverspeedRig.Layout rig) {
        return helper.getLevel().getBlockEntity(rig.saw()) instanceof SawBlockEntity saw
                ? saw.inventory.getStackInSlot(0).toString() : "-";
    }

    static boolean intact(GameTestHelper helper, OverspeedRig.Layout rig) {
        ServerLevel level = helper.getLevel();
        return AllBlocks.LARGE_COGWHEEL.has(level.getBlockState(rig.largeCog()))
                && AllBlocks.COGWHEEL.has(level.getBlockState(rig.smallCog()))
                && AllBlocks.SHAFT.has(level.getBlockState(rig.shaft1()))
                && AllBlocks.GEARBOX.has(level.getBlockState(rig.gearbox()))
                && AllBlocks.COGWHEEL.has(level.getBlockState(rig.mixerCog()))
                && AllBlocks.SPEEDOMETER.has(level.getBlockState(rig.speedometer()))
                && AllBlocks.MECHANICAL_MIXER.has(level.getBlockState(rig.mixer()))
                && AllBlocks.MECHANICAL_SAW.has(level.getBlockState(rig.saw()));
    }

    static String broken(GameTestHelper helper, OverspeedRig.Layout rig) {
        ServerLevel level = helper.getLevel();
        StringBuilder sb = new StringBuilder();
        BlockPos[] chain = {rig.passage(), rig.largeCog(), rig.smallCog(), rig.shaft1(), rig.gearbox(), rig.mixerCog(),
                rig.mixer(), rig.speedometer(), rig.saw()};
        String[] names = {"passage", "large cog", "cog", "shaft", "gearbox", "mixer cog", "mixer", "speedometer", "saw"};
        for (int i = 0; i < chain.length; i++)
            if (level.getBlockState(chain[i]).isAir())
                sb.append(names[i]).append(' ');
        return sb.toString().trim();
    }

    // ---- A belt overspeeds when its two ends are cold --------------------------------------------

    /**
     * A belt on the rig's fast shaft (x2, along +z from it), ending at {@code endZ} (relative to the room corner);
     * through the room's south wall (z 5) and, with a second room, its north wall (z 6), in belt passages.
     */
    static BlockPos belt(GameTestHelper helper, OverspeedRig.Layout rig, int endZ) {
        ServerLevel level = helper.getLevel();
        BlockPos start = rig.shaft1(), end = rig.corner().offset(2, 4, endZ);
        for (int z = 3; z <= endZ; z++)
            level.setBlock(rig.corner().offset(2, 4, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(end, AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X),
                Block.UPDATE_ALL);
        BeltConnectorItem.createBelts(level, start, end);
        for (int z : new int[]{5, 6})
            if (z < endZ && AllBlocks.BELT.has(level.getBlockState(rig.corner().offset(2, 4, z))))
                BeltPassage.encase(level, rig.corner().offset(2, 4, z));
        return end;
    }

    private static void beltAt320(GameTestHelper helper, int endZ, boolean secondRoom, boolean holds) {
        OverspeedRig.Layout rig = roomRig(helper);
        if (secondRoom)
            TestRoomBuilder.build(helper.getLevel(), rig.corner().offset(0, 0, 6), 6);
        BlockPos end = belt(helper, rig, endZ);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    assertCold(helper, rig);
                    if (secondRoom) {
                        Room b = RoomManager.get(helper.getLevel()).roomAt(rig.corner().offset(2, 1, 8));
                        if (b == null || b.status() != RoomStatus.COLD)
                            helper.fail("second room not COLD yet");
                    }
                })
                .thenExecute(() -> OverspeedRig.setMotorSpeed(helper.getLevel(), rig, 160))
                .thenIdle(60)
                .thenExecute(() -> {
                    boolean belt = AllBlocks.BELT.has(helper.getLevel().getBlockState(end));
                    if (holds) {
                        helper.assertTrue(belt, "the belt broke");
                        helper.assertTrue(Math.abs(speed(helper, end)) == 320, "belt end at " + speed(helper, end));
                    } else
                        helper.assertTrue(!belt, "the belt holds with an end out of the cold, end at " + speed(helper, end) + " rpm, start at " + speed(helper, rig.shaft1()));
                })
                .thenSucceed();
    }

    @GameTest(template = SMALL, timeoutTicks = 500, batch = "overspeed")
    public static void beltInsideColdRoomHolds(GameTestHelper helper) {
        beltAt320(helper, 4, false, true);
    }

    /**
     * In the room, its end and the segment before it encased with cold walls (two passages in a row:
     * the end's own sides are a passage and the wall): the belt still reaches the room, it holds.
     */
    @GameTest(template = SMALL, timeoutTicks = 500, batch = "overspeed")
    public static void beltEncasedOverTwoSegmentsHolds(GameTestHelper helper) {
        OverspeedRig.Layout rig = roomRig(helper);
        ServerLevel level = helper.getLevel();
        BlockPos end = belt(helper, rig, 4), before = end.north();
        helper.startSequence()
                .thenExecute(() -> {
                    helper.assertTrue(BeltPassage.encase(level, before) && BeltPassage.encase(level, end),
                            "the two segments are not encased");
                })
                .thenWaitUntil(() -> assertCold(helper, rig))
                .thenExecute(() -> helper.assertTrue(Overspeed.inColdRoom(level, end),
                        "the encased end is not in the cold room"))
                .thenExecute(() -> OverspeedRig.setMotorSpeed(level, rig, 160))
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(AllBlocks.BELT.has(level.getBlockState(end)), "the belt broke");
                    helper.assertTrue(Math.abs(speed(helper, end)) == 320, "belt end at " + speed(helper, end));
                })
                .thenSucceed();
    }

    /**
     * The trailer's room: in the room, encased all along as decoration, a cold wall past each end
     * (the room's south wall, and one placed in the room): nothing along the belt is the room, the air under it is.
     * It holds.
     */
    @GameTest(template = SMALL, timeoutTicks = 500, batch = "overspeed")
    public static void beltEncasedAllAlongInTheRoomHolds(GameTestHelper helper) {
        OverspeedRig.Layout rig = roomRig(helper);
        ServerLevel level = helper.getLevel();
        BlockPos end = belt(helper, rig, 4);
        helper.startSequence()
                .thenExecute(() -> {
                    // the wall takes the front of the ceiling vent above it: one more vent, further east
                    level.setBlock(rig.shaft1().north(), FwBlocks.COLD_WALL.get().defaultBlockState(), Block.UPDATE_ALL);
                    BlockPos vent = rig.corner().offset(5, TestRoomBuilder.SIZE_Y - 1, 3);
                    level.setBlock(vent, FwBlocks.VENT.getDefaultState().setValue(VentBlock.FACING, Direction.DOWN),
                            Block.UPDATE_ALL);
                    if (level.getBlockEntity(vent) instanceof VentBlockEntity be)
                        be.setCreativeSupply(true);
                    for (BlockPos segment = rig.shaft1(); segment.getZ() <= end.getZ(); segment = segment.south())
                        helper.assertTrue(BeltPassage.encase(level, segment), "not encased at " + segment);
                })
                .thenWaitUntil(() -> assertCold(helper, rig))
                .thenExecute(() -> {
                    for (BlockPos segment = rig.shaft1(); segment.getZ() <= end.getZ(); segment = segment.south())
                        helper.assertTrue(Overspeed.inColdRoom(level, segment), "not in the cold room at " + segment);
                })
                .thenExecute(() -> OverspeedRig.setMotorSpeed(level, rig, 160))
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(AllBlocks.BELT.has(level.getBlockState(end)), "the belt broke");
                    helper.assertTrue(Math.abs(speed(helper, end)) == 320, "belt end at " + speed(helper, end));
                })
                .thenSucceed();
    }

    /** Out of a cold room through its wall, into another cold room: both ends cold, the middle out: it holds. */
    @GameTest(template = SMALL, timeoutTicks = 500, batch = "overspeed")
    public static void beltBetweenTwoColdRoomsHolds(GameTestHelper helper) {
        beltAt320(helper, 8, true, true);
    }

    /** One end out of the cold: Create's rule, the belt breaks. */
    @GameTest(template = SMALL, timeoutTicks = 500, batch = "overspeed")
    public static void beltWithAnEndOutsideBreaks(GameTestHelper helper) {
        beltAt320(helper, 8, false, false);
    }

    /**
     * Between two cold rooms with two segments out in the open (z 6, 7): it holds; a shaft put on the segment at z 7,
     * out of the cold, makes a pulley there, and the belt breaks at that pulley.
     */
    @GameTest(template = SMALL, timeoutTicks = 600, batch = "overspeed")
    public static void beltPulleyOutsideBreaks(GameTestHelper helper) {
        OverspeedRig.Layout rig = roomRig(helper);
        ServerLevel level = helper.getLevel();
        TestRoomBuilder.build(level, rig.corner().offset(0, 0, 8), 6);
        BlockPos end = rig.corner().offset(2, 4, 10), pulley = rig.corner().offset(2, 4, 7);
        for (int z = 3; z <= 10; z++)
            level.setBlock(rig.corner().offset(2, 4, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(end, AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X),
                Block.UPDATE_ALL);
        BeltConnectorItem.createBelts(level, rig.shaft1(), end);
        for (int z : new int[]{5, 8})
            BeltPassage.encase(level, rig.corner().offset(2, 4, z));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    assertCold(helper, rig);
                    Room b = RoomManager.get(level).roomAt(rig.corner().offset(2, 1, 10));
                    if (b == null || b.status() != RoomStatus.COLD)
                        helper.fail("second room not COLD yet");
                })
                .thenExecute(() -> OverspeedRig.setMotorSpeed(level, rig, 160))
                .thenIdle(60)
                .thenExecute(() -> helper.assertTrue(Math.abs(speed(helper, end)) == 320,
                        "the belt between the rooms does not hold, end at " + speed(helper, end)))
                .thenExecute(() -> KineticBlockEntity.switchToBlockState(level, pulley,
                        level.getBlockState(pulley).setValue(BeltBlock.PART, BeltPart.PULLEY)))
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(!AllBlocks.BELT.has(level.getBlockState(end)),
                            "the belt holds with a pulley out of the cold");
                    helper.assertTrue(Math.abs(speed(helper, rig.shaft1())) == 320, "the drive broke instead");
                })
                .thenSucceed();
    }

    // ---- 320 rpm in a COLD room, not outside -------------------------------------------------------------------

    @GameTest(template = SMALL, timeoutTicks = 400, batch = "overspeed")
    public static void overspeedInColdRoom(GameTestHelper helper) {
        OverspeedRig.Layout rig = roomRig(helper);
        helper.startSequence()
                .thenWaitUntil(() -> assertCold(helper, rig))
                .thenExecute(() -> OverspeedRig.setMotorSpeed(helper.getLevel(), rig, 160))
                .thenWaitUntil(() -> {
                    helper.assertTrue(intact(helper, rig), "broken: " + broken(helper, rig));
                    helper.assertTrue(Math.abs(speed(helper, rig.mixer())) == 320, "mixer at " + speed(helper, rig.mixer()));
                    helper.assertTrue(Math.abs(speed(helper, rig.saw())) == 320, "saw at " + speed(helper, rig.saw()));
                })
                .thenIdle(40) // still there 2 s later
                .thenExecute(() -> {
                    helper.assertTrue(intact(helper, rig), "broken later: " + broken(helper, rig));
                    float dial = helper.getLevel().getBlockEntity(rig.speedometer()) instanceof SpeedGaugeBlockEntity g
                            ? g.dialTarget : Float.NaN;
                    Frostworks.LOGGER.info("GAMETEST T1 room: mixer {} rpm, saw {} rpm, small cog {} rpm, large cog {} rpm, "
                                    + "speedometer {} rpm, dial {} (1.125 = end of scale at 256)",
                            speed(helper, rig.mixer()), speed(helper, rig.saw()), speed(helper, rig.smallCog()),
                            speed(helper, rig.largeCog()), speed(helper, rig.speedometer()), dial);
                })
                .thenSucceed();
    }

    /** Plain Create: the same rig without a room breaks at 320 rpm. */
    @GameTest(template = SMALL, timeoutTicks = 200, batch = "overspeed")
    public static void witnessBreaksLikeCreate(GameTestHelper helper) {
        OverspeedRig.Layout rig = witnessRig(helper);
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> OverspeedRig.setMotorSpeed(helper.getLevel(), rig, 160))
                .thenWaitUntil(() -> helper.assertTrue(!intact(helper, rig), "witness rig still intact at 320"))
                .thenExecute(() -> {
                    helper.assertTrue(Math.abs(speed(helper, rig.mixer())) <= 256 || Float.isNaN(speed(helper, rig.mixer())),
                            "mixer turns at " + speed(helper, rig.mixer()));
                    Frostworks.LOGGER.info("GAMETEST T1 witness: broken = [{}], mixer {} rpm", broken(helper, rig),
                            speed(helper, rig.mixer()));
                })
                .thenSucceed();
    }

    /** The cap: 512 rpm (motor at 256) breaks even in a COLD room. */
    @GameTest(template = SMALL, timeoutTicks = 400, batch = "overspeed")
    public static void overCapBreaksEvenInColdRoom(GameTestHelper helper) {
        OverspeedRig.Layout rig = roomRig(helper);
        helper.startSequence()
                .thenWaitUntil(() -> assertCold(helper, rig))
                .thenExecute(() -> OverspeedRig.setMotorSpeed(helper.getLevel(), rig, 256))
                .thenWaitUntil(() -> helper.assertTrue(!intact(helper, rig), "rig intact at 512 rpm"))
                .thenExecute(() -> Frostworks.LOGGER.info("GAMETEST T1 cap: 512 rpm in COLD room, broken = [{}]",
                        broken(helper, rig)))
                .thenSucceed();
    }

    /** The cold's bonus the two batches below change, put back after them whatever happened. */
    private static final int COLD_BONUS = 25;

    @AfterBatch(batch = "overspeed_limit_drop")
    public static void restoreBonusAfterDrop(ServerLevel level) {
        FwConfig.OVERSPEED_BONUS.set(COLD_BONUS);
    }

    @AfterBatch(batch = "overspeed_limit_dip")
    public static void restoreBonusAfterDip(ServerLevel level) {
        FwConfig.OVERSPEED_BONUS.set(COLD_BONUS);
    }

    /**
     * The room's limit falls under the speed while it stays COLD (Frosium gone, gas still covering it): no new
     * propagation happens, yet the gear over the new limit breaks, as Create would break it. After
     * the grace period, as for a loss of cold (it used to break at once).
     */
    @GameTest(template = SMALL, timeoutTicks = 1000, batch = "overspeed_limit_drop")
    public static void limitDropBreaksInColdRoomAfterTheGrace(GameTestHelper helper) {
        OverspeedRig.Layout rig = roomRig(helper);
        helper.startSequence()
                .thenWaitUntil(() -> assertCold(helper, rig))
                .thenExecute(() -> OverspeedRig.setMotorSpeed(helper.getLevel(), rig, 160))
                .thenWaitUntil(() -> helper.assertTrue(Math.abs(speed(helper, rig.mixer())) == 320, "not at 320"))
                .thenExecute(() -> FwConfig.OVERSPEED_BONUS.set(10)) // 282 rpm, under the 320 it turns at
                .thenIdle(100)
                .thenExecute(() -> helper.assertTrue(intact(helper, rig), "broke within the grace period"))
                .thenWaitUntil(() -> helper.assertTrue(!intact(helper, rig), "still intact over the new limit"))
                .thenSucceed();
    }

    /**
     * The limit falls for 5 s (Frosium short for a moment, gas still covering the room), then comes
     * back: nothing breaks. A cogwheel put on the network during the dip joins it without breaking.
     */
    @GameTest(template = SMALL, timeoutTicks = 1200, batch = "overspeed_limit_dip")
    public static void limitDipShorterThanTheGraceHolds(GameTestHelper helper) {
        OverspeedRig.Layout rig = roomRig(helper);
        ServerLevel level = helper.getLevel();
        BlockPos cog = rig.mixerCog().south(); // meshes with the mixer's cogwheel
        helper.startSequence()
                .thenWaitUntil(() -> assertCold(helper, rig))
                .thenExecute(() -> OverspeedRig.setMotorSpeed(level, rig, 160))
                .thenWaitUntil(() -> helper.assertTrue(Math.abs(speed(helper, rig.mixer())) == 320, "not at 320"))
                .thenExecute(() -> FwConfig.OVERSPEED_BONUS.set(10)) // 282 rpm, under the 320 it turns at
                .thenIdle(20)
                .thenExecute(() -> level.setBlock(cog, AllBlocks.COGWHEEL.getDefaultState()
                        .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y), Block.UPDATE_ALL))
                .thenIdle(20)
                .thenExecute(() -> helper.assertTrue(AllBlocks.COGWHEEL.has(level.getBlockState(cog))
                        && Math.abs(speed(helper, cog)) == 320, "the cogwheel put during the dip broke, or does not turn at 320: " + speed(helper, cog)))
                .thenIdle(60)
                .thenExecute(() -> FwConfig.OVERSPEED_BONUS.set(COLD_BONUS))
                .thenIdle(700) // past the grace period
                .thenExecute(() -> {
                    helper.assertTrue(intact(helper, rig), "the rig broke after a dip shorter than the grace");
                    helper.assertTrue(AllBlocks.COGWHEEL.has(level.getBlockState(cog)), "the cogwheel broke after the dip");
                })
                .thenSucceed();
    }

    // ---- Loss of cold: grace period, then Create's rule -------------------------------------------------------

    @GameTest(template = SMALL, timeoutTicks = 600, batch = "overspeed_grace")
    public static void graceThenBreak(GameTestHelper helper) {
        int before = FwConfig.get(FwConfig.OVERSPEED_GRACE_SECONDS);
        FwConfig.OVERSPEED_GRACE_SECONDS.set(3); // 60 ticks instead of 30 s
        OverspeedRig.Layout rig = roomRig(helper);
        long[] lostAt = {0};
        helper.startSequence()
                .thenWaitUntil(() -> assertCold(helper, rig))
                .thenExecute(() -> OverspeedRig.setMotorSpeed(helper.getLevel(), rig, 160))
                .thenWaitUntil(() -> helper.assertTrue(Math.abs(speed(helper, rig.mixer())) == 320, "not at 320"))
                .thenExecute(() -> {
                    lostAt[0] = helper.getTick();
                    helper.getLevel().setBlock(rig.eastWall(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                })
                .thenIdle(50) // room OPEN after ~10 ticks, still inside the 60-tick grace
                .thenExecute(() -> helper.assertTrue(intact(helper, rig), "broke during the grace period: "
                        + broken(helper, rig)))
                .thenWaitUntil(() -> helper.assertTrue(!intact(helper, rig), "still intact after the grace period"))
                .thenExecute(() -> {
                    long delay = helper.getTick() - lostAt[0];
                    Frostworks.LOGGER.info("GAMETEST T1 grace 3 s: wall broken, rig broke after {} ticks, broken = [{}]",
                            delay, broken(helper, rig));
                    helper.assertTrue(delay >= 60, "broke after " + delay + " ticks (< grace)");
                    helper.assertTrue(delay <= 60 + 10 + 40, "broke too late: " + delay);
                    FwConfig.OVERSPEED_GRACE_SECONDS.set(before);
                })
                .thenSucceed();
    }

    // ---- Measurements at 256 and 320 rpm --------------------------------------------------------------------------

    @GameTest(template = SMALL, timeoutTicks = 3000, batch = "overspeed_measure")
    public static void measureAt256And320(GameTestHelper helper) {
        OverspeedRig.Layout rig = roomRig(helper);
        ServerLevel level = helper.getLevel();
        int[] motorSpeeds = {128, 160, 128, 160};
        // state: [phase index, start tick, saw done tick, mixer done tick]
        long[] st = {-1, 0, -1, -1, 0};
        StringBuilder report = new StringBuilder();
        helper.onEachTick(() -> {
            if (st[0] < 0) {
                Room room = RoomManager.get(level).roomAt(rig.interior());
                if (room != null && room.status() == RoomStatus.COLD)
                    st[0] = 0;
                return;
            }
            if (st[0] >= motorSpeeds.length * 3)
                return;
            int run = (int) (st[0] / 3);
            int step = (int) (st[0] % 3);
            long now = helper.getTick();
            if (step == 0) { // set speed, let it settle
                OverspeedRig.setMotorSpeed(level, rig, motorSpeeds[run]);
                st[1] = now;
                st[0]++;
            } else if (step == 1 && now - st[1] >= 40) { // load both machines
                if (level.getBlockEntity(rig.saw()) instanceof SawBlockEntity saw)
{
                    saw.inventory.clear();
                    saw.inventory.insertItem(0, new ItemStack(AllItems.ANDESITE_ALLOY.get()), false);
                    st[4] = (long) saw.inventory.recipeDuration;
                }
                if (level.getBlockEntity(rig.basin()) instanceof BasinBlockEntity basin) {
                    basin.getOutputInventory().setStackInSlot(0, ItemStack.EMPTY);
                    basin.getInputInventory().setStackInSlot(0, new ItemStack(Items.ANDESITE));
                    basin.getInputInventory().setStackInSlot(1, new ItemStack(Items.IRON_NUGGET));
                }
                st[1] = now;
                st[2] = st[3] = -1;
                st[0]++;
            } else if (step == 2) {
                if (st[2] < 0 && level.getBlockEntity(rig.saw()) instanceof SawBlockEntity saw
                        && saw.inventory.appliedRecipe)
                    st[2] = now - st[1];
                if (st[3] < 0 && level.getBlockEntity(rig.basin()) instanceof BasinBlockEntity basin
                        && basin.getOutputInventory().getStackInSlot(0).is(AllItems.ANDESITE_ALLOY.get()))
                    st[3] = now - st[1];
                if (st[2] >= 0 && st[3] >= 0 || now - st[1] > 500) {
                    report.append(String.format(Locale.ROOT,
                            "[%d rpm: mixer getSpeed %.0f, saw getSpeed %.0f, stress %.0f su, saw cycle %d t for a %d recipe (%.1f per tick), mixer cycle %d t, intact %s] ",
                            motorSpeeds[run] * 2, speed(helper, rig.mixer()), speed(helper, rig.saw()), stress(helper, rig),
                            st[2], st[4], st[2] > 0 ? (st[4] - 5f) / st[2] : 0f, st[3], intact(helper, rig)));
                    if (level.getBlockEntity(rig.saw()) instanceof SawBlockEntity saw)
                        saw.inventory.clear();
                    st[0]++;
                    if (st[0] >= motorSpeeds.length * 3)
                        Frostworks.LOGGER.info("GAMETEST T1 measures: {}", report);
                }
            }
        });
        helper.succeedWhen(() -> helper.assertTrue(st[0] >= motorSpeeds.length * 3, "measuring"));
    }
}
