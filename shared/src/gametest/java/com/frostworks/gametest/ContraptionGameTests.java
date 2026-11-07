package com.frostworks.gametest;

import java.lang.reflect.Field;

import com.frostworks.Frostworks;
import com.frostworks.FwConfig;
import com.frostworks.network.FwNetwork;
import com.frostworks.network.RoomInfoPayload;
import com.frostworks.overspeed.Overspeed;
import com.frostworks.overspeed.OverspeedRig;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's contraptions (the structures that turn: a mechanical bearing, a clockwork bearing, with the block they carry)
 * driven past Create's limit in a cold room; and how the overspeed behaves when Create's own limit
 * ({@code maxRotationSpeed}) is set to something else than 256. On the overspeed rig ({@link OverspeedRig}): the saw
 * at its end is replaced by the bearing (facing east, the speedometer behind it), with an oak plank in front of it.
 */
public final class ContraptionGameTests {
    private ContraptionGameTests() {}

    private static final String SMALL = "empty_16x10x16";

    /** Values the batches below change, put back after them whatever happened. */
    private static final int CREATE_LIMIT = 256, COLD_BONUS = 25;

    @AfterBatch(batch = "contraption_384")
    public static void restoreColdLimit(ServerLevel level) {
        FwConfig.OVERSPEED_BONUS.set(COLD_BONUS);
    }

    @AfterBatch(batch = "create_limit_512")
    public static void restoreCreateLimit512(ServerLevel level) {
        AllConfigs.server().kinetics.maxRotationSpeed.set(CREATE_LIMIT);
    }

    @AfterBatch(batch = "create_limit_128")
    public static void restoreCreateLimit128(ServerLevel level) {
        AllConfigs.server().kinetics.maxRotationSpeed.set(CREATE_LIMIT);
    }

    // ---- The rig ----------------------------------------------------------------------------------------------

    private static BlockPos bearing(OverspeedRig.Layout rig) {
        return rig.saw();
    }

    private static BlockPos carried(OverspeedRig.Layout rig) {
        return rig.saw().east();
    }

    /** The bearing (of this block) where the saw was, facing east, an oak plank in front. */
    private static void placeBearing(ServerLevel level, OverspeedRig.Layout rig, Block bearingBlock) {
        level.setBlock(carried(rig), Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
        BlockState bearing = bearingBlock.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST);
        level.setBlock(bearing(rig), bearing, Block.UPDATE_ALL);
    }

    private static OverspeedRig.Layout coldRig(GameTestHelper helper, Block bearingBlock) {
        OverspeedRig.Layout rig = OverspeedGameTests.roomRig(helper);
        placeBearing(helper.getLevel(), rig, bearingBlock);
        return rig;
    }

    private static OverspeedRig.Layout warmRig(GameTestHelper helper, Block bearingBlock) {
        OverspeedRig.Layout rig = OverspeedRig.buildWitness(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 9)));
        placeBearing(helper.getLevel(), rig, bearingBlock);
        return rig;
    }

    /** Create's own field, by its name, up the class tree (the bearings keep it protected). */
    private static Object field(Object be, String name) {
        for (Class<?> c = be.getClass(); c != null; c = c.getSuperclass())
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(be);
            } catch (NoSuchFieldException e) {
                // up
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        throw new IllegalStateException("no field " + name + " in " + be.getClass());
    }

    private static void setField(Object be, String name, Object value) {
        for (Class<?> c = be.getClass(); c != null; c = c.getSuperclass())
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(be, value);
                return;
            } catch (NoSuchFieldException e) {
                // up
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        throw new IllegalStateException("no field " + name + " in " + be.getClass());
    }

    /** As a player's click on a turning bearing: it assembles on its next tick. */
    private static void assemble(GameTestHelper helper, OverspeedRig.Layout rig) {
        setField(helper.getLevel().getBlockEntity(bearing(rig)), "assembleNextTick", true);
    }

    private static AbstractContraptionEntity contraption(GameTestHelper helper, OverspeedRig.Layout rig) {
        var be = helper.getLevel().getBlockEntity(bearing(rig));
        if (be == null)
            return null;
        Object moved = field(be, "movedContraption");
        return moved instanceof AbstractContraptionEntity e && e.isAlive() ? e : null;
    }

    /** The shell cells of the test room that are not one of our walls (for a room that stays open). */
    private static String holes(GameTestHelper helper, OverspeedRig.Layout rig) {
        StringBuilder s = new StringBuilder();
        int sx = com.frostworks.room.TestRoomBuilder.SIZE_X, sy = com.frostworks.room.TestRoomBuilder.SIZE_Y,
                sz = com.frostworks.room.TestRoomBuilder.SIZE_Z;
        for (int x = 0; x < sx; x++)
            for (int y = 0; y < sy; y++)
                for (int z = 0; z < sz; z++)
                    if (x == 0 || y == 0 || z == 0 || x == sx - 1 || y == sy - 1 || z == sz - 1) {
                        BlockPos p = rig.corner().offset(x, y, z);
                        if (!com.frostworks.room.RoomWalls.isWall(helper.getLevel(), p, helper.getLevel().getBlockState(p)))
                            s.append(" (").append(x).append(',').append(y).append(',').append(z).append(") ")
                                    .append(helper.getLevel().getBlockState(p).getBlock());
                    }
        return s.length() == 0 ? "none" : s.toString();
    }

    /**
     * After a test: the motor stopped and the contraption put back down, now, rather than when the test area is
     * cleared (the plank set back then is a block change, and a room search, in the middle of another test).
     */
    private static void stop(GameTestHelper helper, OverspeedRig.Layout rig, BlockPos bearingPos) {
        OverspeedRig.setMotorSpeed(helper.getLevel(), rig, 0);
        var be = helper.getLevel().getBlockEntity(bearingPos);
        if (be instanceof com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity b)
            b.disassemble();
        if (be instanceof com.simibubi.create.content.contraptions.bearing.ClockworkBearingBlockEntity c)
            c.disassemble();
    }

    private static float speed(GameTestHelper helper, BlockPos pos) {
        return OverspeedGameTests.speed(helper, pos);
    }

    /**
     * Turning and holding: the bearing at {@code rpm}, its contraption assembled (the plank gone from the world, in the
     * contraption), and its angle going round.
     */
    private static void assertTurning(GameTestHelper helper, OverspeedRig.Layout rig, int rpm, float[] lastAngle) {
        helper.assertTrue(Math.abs(speed(helper, bearing(rig))) == rpm,
                "bearing at " + speed(helper, bearing(rig)) + " rpm, not " + rpm + " (" + OverspeedGameTests.broken(helper, rig) + ")");
        AbstractContraptionEntity e = contraption(helper, rig);
        helper.assertTrue(e != null, "no contraption on the bearing");
        helper.assertTrue(helper.getLevel().getBlockState(carried(rig)).isAir(), "the plank is still in the world");
        float angle = (float) field(helper.getLevel().getBlockEntity(bearing(rig)), "angle");
        helper.assertTrue(angle != lastAngle[0], "the contraption does not turn (angle " + angle + ")");
        lastAngle[0] = angle;
    }

    private static void logTurning(GameTestHelper helper, OverspeedRig.Layout rig, String what) {
        var be = helper.getLevel().getBlockEntity(bearing(rig));
        Frostworks.LOGGER.info("GAMETEST contraption {}: bearing {} rpm, angular speed {} deg/tick, contraption {}", what,
                speed(helper, bearing(rig)), be instanceof com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity b
                        ? b.getAngularSpeed() : "(clockwork)", contraption(helper, rig));
    }

    /** A turning contraption in a cold room: spun up to {@code rpm} (motor at half), then assembled or assembled first. */
    private static void bearingInTheCold(GameTestHelper helper, Block bearingBlock, int rpm, boolean assembleFirst) {
        OverspeedRig.Layout rig = coldRig(helper, bearingBlock);
        ServerLevel level = helper.getLevel();
        float[] angle = {Float.NaN};
        var sequence = helper.startSequence().thenWaitUntil(() -> OverspeedGameTests.assertCold(helper, rig));
        if (assembleFirst)
            sequence = sequence.thenExecute(() -> assemble(helper, rig)).thenIdle(20)
                    .thenExecute(() -> helper.assertTrue(contraption(helper, rig) != null, "not assembled at 32 rpm"));
        sequence = sequence.thenExecute(() -> OverspeedRig.setMotorSpeed(level, rig, rpm / 2)).thenIdle(40);
        if (!assembleFirst)
            sequence = sequence.thenExecute(() -> assemble(helper, rig)).thenIdle(20);
        sequence.thenExecute(() -> assertTurning(helper, rig, rpm, angle))
                .thenIdle(100) // five seconds at full speed
                .thenExecute(() -> assertTurning(helper, rig, rpm, angle))
                .thenExecute(() -> logTurning(helper, rig, bearingBlock + " at " + rpm + (assembleFirst ? ", assembled first" : "")))
                .thenExecute(() -> stop(helper, rig, bearing(rig)))
                .thenSucceed();
    }

    // ---- 1 to 5: contraptions past Create's limit ---------------------------------------------------------------

    /** 1. A mechanical bearing turning at 320 rpm, then assembled: its contraption turns, nothing breaks. */
    @GameTest(template = SMALL, timeoutTicks = 600, batch = "contraption")
    public static void bearingAssembledAt320Turns(GameTestHelper helper) {
        bearingInTheCold(helper, AllBlocks.MECHANICAL_BEARING.get(), 320, false);
    }

    /** 2. A contraption assembled at 32 rpm, then spun up to 320: it holds. */
    @GameTest(template = SMALL, timeoutTicks = 600, batch = "contraption")
    public static void bearingSpunUpTo320Turns(GameTestHelper helper) {
        bearingInTheCold(helper, AllBlocks.MECHANICAL_BEARING.get(), 320, true);
    }

    /**
     * 3. A clockwork bearing at 320 rpm (its hands go round at its own pace): it holds. Where the speedometer was, its
     * hand (a plank) where the saw was, and air before the east wall: a clockwork bearing assembles by itself as soon as
     * it turns, and takes every block in line in front of it, the cold wall included (the room then opens).
     */
    @GameTest(template = SMALL, timeoutTicks = 600, batch = "contraption_clockwork")
    public static void clockworkBearingAt320Holds(GameTestHelper helper) {
        OverspeedRig.Layout rig = OverspeedGameTests.roomRig(helper);
        ServerLevel level = helper.getLevel();
        BlockPos clock = rig.speedometer(), hand = rig.saw();
        level.setBlock(hand.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(hand, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(clock, AllBlocks.CLOCKWORK_BEARING.getDefaultState().setValue(DirectionalKineticBlock.FACING,
                Direction.EAST), Block.UPDATE_ALL);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    try {
                        OverspeedGameTests.assertCold(helper, rig);
                    } catch (RuntimeException e) {
                        helper.fail(e.getMessage() + "; shell holes " + holes(helper, rig));
                    }
                })
                .thenExecute(() -> OverspeedRig.setMotorSpeed(level, rig, 160))
                .thenIdle(60)
                .thenExecute(() -> {
                    var be = level.getBlockEntity(clock);
                    helper.assertTrue(Math.abs(speed(helper, clock)) == 320, "clockwork bearing at " + speed(helper, clock) + " rpm");
                    Object moved = be == null ? null : field(be, "hourHand");
                    helper.assertTrue(moved instanceof AbstractContraptionEntity e && e.isAlive(), "no hour hand on the clockwork bearing");
                    helper.assertTrue(holes(helper, rig).equals("none"), "the room has holes: " + holes(helper, rig));
                    Frostworks.LOGGER.info("GAMETEST contraption clockwork bearing at 320: {} rpm, hour hand {}", speed(helper, clock), moved);
                })
                .thenExecute(() -> stop(helper, rig, clock))
                .thenSucceed();
    }

    /** 4. The Frosium's limit (384, the cold room's bonus raised to 50 %): the contraption turns at 384. */
    @GameTest(template = SMALL, timeoutTicks = 600, batch = "contraption_384")
    public static void bearingAt384Turns(GameTestHelper helper) {
        FwConfig.OVERSPEED_BONUS.set(50);
        bearingInTheCold(helper, AllBlocks.MECHANICAL_BEARING.get(), 384, false);
    }

    /** 5. Witness: the same bearing at 320 out of the cold: Create's rule, the drive breaks, the bearing stops. */
    @GameTest(template = SMALL, timeoutTicks = 300, batch = "contraption")
    public static void bearingAt320OutOfTheColdBreaks(GameTestHelper helper) {
        OverspeedRig.Layout rig = warmRig(helper, AllBlocks.MECHANICAL_BEARING.get());
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> OverspeedRig.setMotorSpeed(helper.getLevel(), rig, 160))
                .thenIdle(40)
                .thenExecute(() -> helper.assertTrue(Math.abs(speed(helper, bearing(rig))) != 320,
                        "the bearing turns at 320 out of the cold"))
                .thenSucceed();
    }

    // ---- 6 to 8: Create's limit set to something else -----------------------------------------------------------

    /**
     * 6. Create's limit at 512: the cold's bonus follows it (25 %, 640 rpm): 600 rpm hold in the cold room, more than
     * Create allows. The goggles of the cold room give 640 as its limit.
     */
    @GameTest(template = SMALL, timeoutTicks = 600, batch = "create_limit_512")
    public static void createAt512TheColdAllows600(GameTestHelper helper) {
        AllConfigs.server().kinetics.maxRotationSpeed.set(512);
        OverspeedRig.Layout rig = coldRig(helper, AllBlocks.MECHANICAL_BEARING.get());
        ServerLevel level = helper.getLevel();
        float[] angle = {Float.NaN};
        helper.startSequence()
                .thenWaitUntil(() -> OverspeedGameTests.assertCold(helper, rig))
                .thenExecute(() -> {
                    RoomInfoPayload info = FwNetwork.describe(level, rig.passage(), level.getBlockState(rig.passage()));
                    Frostworks.LOGGER.info("GAMETEST create at 512: goggles maxRpm {} baseRpm {}, Overspeed.maxRpm {}",
                            info.rooms().get(0).maxRpm(), info.rooms().get(0).baseRpm(), Overspeed.createLimit());
                    helper.assertTrue(info.rooms().get(0).baseRpm() == 512, "Create's limit seen at " + info.rooms().get(0).baseRpm());
                    helper.assertTrue(info.rooms().get(0).maxRpm() == 640, "the goggles give " + info.rooms().get(0).maxRpm()
                            + " rpm as the cold room's limit, not 512 + 25 %");
                    // a creative motor stops at 256: let this one go to 300
                    if (level.getBlockEntity(rig.motor()) instanceof CreativeMotorBlockEntity motor)
                        motor.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).between(-300, 300);
                    OverspeedRig.setMotorSpeed(level, rig, 300); // 600 rpm: more than Create's, less than ours
                })
                .thenIdle(40)
                .thenExecute(() -> assemble(helper, rig))
                .thenIdle(40)
                .thenExecute(() -> assertTurning(helper, rig, 600, angle))
                .thenIdle(41) // not 40: at 180 deg a tick, 40 ticks are 20 full turns, the same angle
                .thenExecute(() -> assertTurning(helper, rig, 600, angle))
                .thenExecute(() -> stop(helper, rig, bearing(rig)))
                .thenSucceed();
    }

    /** 7. Create's limit at 512, out of the cold: 480 rpm hold (Create alone allows them). */
    @GameTest(template = SMALL, timeoutTicks = 300, batch = "create_limit_512")
    public static void createAt512WarmHolds480(GameTestHelper helper) {
        AllConfigs.server().kinetics.maxRotationSpeed.set(512);
        OverspeedRig.Layout rig = warmRig(helper, AllBlocks.MECHANICAL_BEARING.get());
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> OverspeedRig.setMotorSpeed(helper.getLevel(), rig, 240))
                .thenIdle(40)
                .thenExecute(() -> helper.assertTrue(Math.abs(speed(helper, bearing(rig))) == 480,
                        "out of the cold at 480 with Create at 512: " + speed(helper, bearing(rig))))
                .thenSucceed();
    }

    /**
     * 8. Create's limit at 128: the cold's bonus follows it down (25 %, 160 rpm): 160 rpm hold in the cold room (the
     * motor, out of the cold, turns at 80, the gears inside double it), and out of the cold 160 break.
     */
    @GameTest(template = SMALL, timeoutTicks = 600, batch = "create_limit_128")
    public static void createAt128TheColdAllows160(GameTestHelper helper) {
        AllConfigs.server().kinetics.maxRotationSpeed.set(128);
        OverspeedRig.Layout cold = coldRig(helper, AllBlocks.MECHANICAL_BEARING.get());
        OverspeedRig.Layout warm = warmRig(helper, AllBlocks.MECHANICAL_BEARING.get());
        ServerLevel level = helper.getLevel();
        float[] angle = {Float.NaN};
        helper.startSequence()
                .thenWaitUntil(() -> OverspeedGameTests.assertCold(helper, cold))
                .thenExecute(() -> {
                    RoomInfoPayload info = FwNetwork.describe(level, cold.passage(), level.getBlockState(cold.passage()));
                    Frostworks.LOGGER.info("GAMETEST create at 128: goggles maxRpm {} baseRpm {}",
                            info.rooms().get(0).maxRpm(), info.rooms().get(0).baseRpm());
                    helper.assertTrue(info.rooms().get(0).maxRpm() == 160, "the goggles give " + info.rooms().get(0).maxRpm()
                            + " rpm as the cold room's limit, not 128 + 25 %");
                    OverspeedRig.setMotorSpeed(level, cold, 80);
                    OverspeedRig.setMotorSpeed(level, warm, 80);
                })
                .thenIdle(40)
                .thenExecute(() -> assemble(helper, cold))
                .thenIdle(40)
                .thenExecute(() -> {
                    assertTurning(helper, cold, 160, angle);
                    helper.assertTrue(Math.abs(speed(helper, bearing(warm))) != 160,
                            "out of the cold at 160 with Create at 128: it holds");
                })
                .thenExecute(() -> {
                    stop(helper, cold, bearing(cold));
                    stop(helper, warm, bearing(warm));
                })
                .thenSucceed();
    }
}
