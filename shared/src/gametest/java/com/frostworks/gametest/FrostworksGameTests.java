package com.frostworks.gametest;

import java.util.Locale;

import com.frostworks.Frostworks;
import com.frostworks.FwConfig;
import com.frostworks.content.compressor.CompressorBlock;
import com.frostworks.content.compressor.CompressorBlockEntity;
import com.frostworks.content.vent.VentBlock;
import com.frostworks.content.vent.VentBlockEntity;
import com.frostworks.registry.FwBlocks;
import com.frostworks.room.LevelRoomWorld;
import com.frostworks.room.Room;
import com.frostworks.room.RoomManager;
import com.frostworks.room.TestRoomBuilder;
import com.frostworks.room.core.RoomScan;
import com.frostworks.room.core.RoomStatus;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.gauge.StressGaugeBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * In-game tests, run by {@code ./gradlew runGameTestServer}.
 * Rooms, compressor stress, gas from compressor to vent through Create pipes, and the cost of a scan in a
 * real world.
 */
public final class FrostworksGameTests {
    private FrostworksGameTests() {}

    private static final String SMALL = "empty_16x10x16";
    private static final String BIG = "empty_56x24x24";

    // ---- Rooms ---------------------------------------------------------------------------------------------

    private static TestRoomBuilder.Layout room(GameTestHelper helper, int vents) {
        return TestRoomBuilder.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)), vents);
    }

    private static Room roomAt(GameTestHelper helper, BlockPos absolute) {
        return RoomManager.get(helper.getLevel()).roomAt(absolute);
    }

    private static void assertStatus(GameTestHelper helper, BlockPos interior, RoomStatus expected) {
        Room r = roomAt(helper, interior);
        if (r == null)
            helper.fail("no room yet");
        else if (r.status() != expected)
            helper.fail("room is " + r.status() + ", expected " + expected + " (volume " + r.volume + ", capacity "
                    + r.capacity() + ")");
    }

    @GameTest(template = SMALL, timeoutTicks = 200)
    public static void sixVentsCold(GameTestHelper helper) {
        TestRoomBuilder.Layout layout = room(helper, 6);
        helper.succeedWhen(() -> {
            assertStatus(helper, layout.interior(), RoomStatus.COLD);
            Room r = roomAt(helper, layout.interior());
            helper.assertTrue(r.volume == TestRoomBuilder.VOLUME, "volume " + r.volume);
            helper.assertTrue(r.capacity() == 96, "capacity " + r.capacity());
        });
    }

    @GameTest(template = SMALL, timeoutTicks = 200)
    public static void fiveVentsInsufficient(GameTestHelper helper) {
        TestRoomBuilder.Layout layout = room(helper, 5);
        helper.succeedWhen(() -> {
            assertStatus(helper, layout.interior(), RoomStatus.INSUFFICIENT);
            Room r = roomAt(helper, layout.interior());
            helper.assertTrue(r.capacity() == 80, "capacity " + r.capacity());
            helper.assertTrue(RoomStatus.missingVents(r.volume, r.capacity(), 16) == 1, "one vent missing");
        });
    }

    @GameTest(template = SMALL, timeoutTicks = 300)
    public static void brokenWallOpensQuickly(GameTestHelper helper) {
        TestRoomBuilder.Layout layout = room(helper, 6);
        long[] brokenAt = {0};
        helper.startSequence()
                .thenWaitUntil(() -> assertStatus(helper, layout.interior(), RoomStatus.COLD))
                .thenExecute(() -> {
                    brokenAt[0] = helper.getTick();
                    helper.getLevel().setBlock(layout.eastWall(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                })
                .thenWaitUntil(() -> assertStatus(helper, layout.interior(), RoomStatus.OPEN))
                .thenExecute(() -> {
                    long delay = helper.getTick() - brokenAt[0];
                    Frostworks.LOGGER.info("GAMETEST broken wall -> OPEN after {} ticks", delay);
                    helper.assertTrue(delay < 40, "OPEN after " + delay + " ticks (limit: 40 = 2 s)");
                })
                .thenSucceed();
    }

    @GameTest(template = SMALL, timeoutTicks = 200)
    public static void plankInsteadOfWallIsOpen(GameTestHelper helper) {
        TestRoomBuilder.Layout layout = room(helper, 6);
        helper.getLevel().setBlock(layout.eastWall(), Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
        helper.succeedWhen(() -> assertStatus(helper, layout.interior(), RoomStatus.OPEN));
    }

    @GameTest(template = SMALL, timeoutTicks = 300)
    public static void stripDoorIsAWall(GameTestHelper helper) {
        // The door has no open state any more; both halves always count as a wall
        TestRoomBuilder.Layout layout = room(helper, 6);
        BlockPos low = layout.doorLower();
        helper.assertTrue(com.frostworks.room.RoomWalls.isWall(helper.getLevel().getBlockState(low))
                && com.frostworks.room.RoomWalls.isWall(helper.getLevel().getBlockState(low.above())), "door halves");
        helper.succeedWhen(() -> assertStatus(helper, layout.interior(), RoomStatus.COLD));
    }

    @GameTest(template = SMALL, timeoutTicks = 300)
    public static void temperatureDrops(GameTestHelper helper) {
        TestRoomBuilder.Layout layout = room(helper, 6);
        helper.succeedWhen(() -> {
            Room r = roomAt(helper, layout.interior());
            helper.assertTrue(r != null && r.temperature() <= 18.5f,
                    "temperature " + (r == null ? "-" : r.temperature()));
        });
    }

    // ---- Compressor ----------------------------------------------------------------------------------------

    /** Creative motor -> stressometer -> compressor, along X. Returns {motor, gauge, compressor} (absolute). */
    private static BlockPos[] kineticLine(GameTestHelper helper) {
        BlockPos motor = helper.absolutePos(new BlockPos(1, 1, 3));
        BlockPos gauge = motor.east();
        BlockPos compressor = gauge.east();
        var level = helper.getLevel();
        level.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(DirectionalBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(gauge, AllBlocks.STRESSOMETER.getDefaultState()
                .setValue(DirectionalAxisKineticBlock.FACING, Direction.UP)
                .setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true), Block.UPDATE_ALL);
        level.setBlock(compressor, FwBlocks.COMPRESSOR.getDefaultState()
                .setValue(CompressorBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        return new BlockPos[]{motor, gauge, compressor};
    }

    private static void setSpeed(GameTestHelper helper, BlockPos motor, int rpm) {
        if (helper.getLevel().getBlockEntity(motor) instanceof CreativeMotorBlockEntity be)
            be.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(rpm);
        else
            helper.fail("no creative motor");
    }

    private static float gaugeStress(GameTestHelper helper, BlockPos gauge) {
        if (helper.getLevel().getBlockEntity(gauge) instanceof StressGaugeBlockEntity be)
            return be.getNetworkStress();
        helper.fail("no stressometer");
        return -1;
    }

    private static void assertStress(GameTestHelper helper, BlockPos gauge, float expected, String what) {
        float stress = gaugeStress(helper, gauge);
        if (Math.abs(stress - expected) > 1)
            helper.fail(what + ": stressometer shows " + stress + " su, expected " + expected);
    }

    @GameTest(template = SMALL, timeoutTicks = 300)
    public static void compressorTotalModeAtTwoSpeeds(GameTestHelper helper) {
        BlockPos[] line = kineticLine(helper);
        float[] seen = new float[2];
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    if (helper.getLevel().getBlockEntity(line[2]) instanceof CompressorBlockEntity c)
                        helper.assertTrue(c.setting() == 2048, "default setting " + c.setting());
                    setSpeed(helper, line[0], 32);
                })
                .thenWaitUntil(() -> assertStress(helper, line[1], 2048, "TOTAL at 32 rpm"))
                .thenExecute(() -> {
                    seen[0] = gaugeStress(helper, line[1]);
                    setSpeed(helper, line[0], 128);
                })
                .thenIdle(5)
                .thenWaitUntil(() -> assertStress(helper, line[1], 2048, "TOTAL at 128 rpm"))
                .thenExecute(() -> {
                    seen[1] = gaugeStress(helper, line[1]);
                    Frostworks.LOGGER.info("GAMETEST T3 TOTAL 2048 su: {} su at 32 rpm, {} su at 128 rpm",
                            seen[0], seen[1]);
                })
                .thenSucceed();
    }

    @GameTest(template = SMALL, timeoutTicks = 300, batch = "per_rpm")
    public static void compressorPerRpmMode(GameTestHelper helper) {
        FwConfig.StressMode before = FwConfig.get(FwConfig.STRESS_MODE);
        FwConfig.STRESS_MODE.set(FwConfig.StressMode.PER_RPM);
        BlockPos[] line = kineticLine(helper);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    if (helper.getLevel().getBlockEntity(line[2]) instanceof CompressorBlockEntity c)
                        c.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue
                                .ScrollValueBehaviour.TYPE).setValue(256);
                    setSpeed(helper, line[0], 32);
                })
                .thenWaitUntil(() -> assertStress(helper, line[1], 256 * 32, "PER_RPM 256 su/rpm at 32 rpm"))
                .thenExecute(() -> setSpeed(helper, line[0], 64))
                .thenWaitUntil(() -> assertStress(helper, line[1], 256 * 64, "PER_RPM 256 su/rpm at 64 rpm"))
                .thenExecute(() -> {
                    Frostworks.LOGGER.info("GAMETEST T3 PER_RPM 256 su/rpm: {} su at 64 rpm",
                            gaugeStress(helper, line[1]));
                    FwConfig.STRESS_MODE.set(before);
                })
                .thenSucceed();
    }

    @GameTest(template = SMALL, timeoutTicks = 200)
    public static void compressorIdleAtZeroSpeed(GameTestHelper helper) {
        BlockPos[] line = kineticLine(helper);
        setSpeed(helper, line[0], 0); // a creative motor starts at 16 rpm
        helper.runAfterDelay(20, () -> {
            if (helper.getLevel().getBlockEntity(line[2]) instanceof CompressorBlockEntity c) {
                helper.assertTrue(c.appliedStress() == 0, "stress at 0 rpm: " + c.appliedStress());
                helper.assertTrue(c.gasPerSecond() == 0, "gas at 0 rpm: " + c.gasPerSecond());
                helper.succeed();
            } else
                helper.fail("no compressor");
        });
    }

    // ---- Gas: compressor -> Create pipes -> vent ----------------------------------------------------------------

    @GameTest(template = SMALL, timeoutTicks = 400)
    public static void compressorFeedsVentThroughPipes(GameTestHelper helper) {
        BlockPos[] line = kineticLine(helper);
        var level = helper.getLevel();
        BlockPos pipe1 = line[2].east(), pipe2 = pipe1.east(), pipe3 = pipe2.east(), vent = pipe3.east();
        level.setBlock(vent, FwBlocks.VENT.getDefaultState().setValue(VentBlock.FACING, Direction.EAST),
                Block.UPDATE_ALL);
        for (BlockPos p : new BlockPos[]{pipe1, pipe2, pipe3}) {
            BlockState pipe = Block.updateFromNeighbourShapes(AllBlocks.FLUID_PIPE.getDefaultState(), level, p);
            level.setBlock(p, pipe, Block.UPDATE_ALL);
        }
        setSpeed(helper, line[0], 64);
        helper.succeedWhen(() -> {
            if (!(level.getBlockEntity(vent) instanceof VentBlockEntity be))
                throw new IllegalStateException("no vent");
            if (!(level.getBlockEntity(line[2]) instanceof CompressorBlockEntity c))
                throw new IllegalStateException("no compressor");
            helper.assertTrue(c.gasPerSecond() > 0, "compressor does not produce");
            helper.assertTrue(be.isActive(), "vent not active, gas in vent: " + be.gas().getAmount()
                    + " mB, in compressor: " + c.gas().getAmount() + " mB");
            Frostworks.LOGGER.info("GAMETEST gas reached the vent after {} ticks", helper.getTick());
        });
    }

    /** No blocked intake: walled in on every free face, a powered compressor still makes gas. */
    @GameTest(template = SMALL, timeoutTicks = 200)
    public static void walledInCompressorStillProduces(GameTestHelper helper) {
        BlockPos[] line = kineticLine(helper);
        var level = helper.getLevel();
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN, Direction.EAST})
            level.setBlock(line[2].relative(d), FwBlocks.COLD_WALL.getDefaultState(), Block.UPDATE_ALL);
        setSpeed(helper, line[0], 64);
        helper.succeedWhen(() -> {
            if (!(level.getBlockEntity(line[2]) instanceof CompressorBlockEntity c))
                throw new IllegalStateException("no compressor");
            helper.assertTrue(c.gasPerSecond() > 0, "walled-in compressor does not produce");
        });
    }

    // ---- Cost of a scan in a real world -------------------------------------------------------------------------

    @GameTest(template = BIG, timeoutTicks = 100, batch = "bench")
    public static void scanBenchmark(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockState wall = FwBlocks.COLD_WALL.getDefaultState();
        int[][] rooms = {{5, 4, 5}, {10, 10, 10}, {50, 20, 20}};
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 1));
        StringBuilder report = new StringBuilder();
        for (int[] r : rooms) {
            // shell around an interior of r[0] x r[1] x r[2], interior from origin + (1,1,1)
            for (int x = 0; x <= r[0] + 1; x++)
                for (int y = 0; y <= r[1] + 1; y++)
                    for (int z = 0; z <= r[2] + 1; z++) {
                        boolean shell = x == 0 || y == 0 || z == 0 || x == r[0] + 1 || y == r[1] + 1 || z == r[2] + 1;
                        level.setBlock(origin.offset(x, y, z), shell ? wall : Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_CLIENTS);
                    }
            int expected = r[0] * r[1] * r[2];
            BlockPos start = origin.offset(1, 1, 1);
            LevelRoomWorld world = new LevelRoomWorld(level);
            for (int i = 0; i < 10; i++)
                new RoomScan(world, start.getX(), start.getY(), start.getZ(), 20_000).run();
            long best = Long.MAX_VALUE, total = 0;
            int runs = 30;
            for (int i = 0; i < runs; i++) {
                world.resetCache();
                long t0 = System.nanoTime();
                RoomScan scan = new RoomScan(world, start.getX(), start.getY(), start.getZ(), 20_000);
                scan.run();
                long dt = System.nanoTime() - t0;
                if (scan.outcome() != RoomScan.Outcome.CLOSED || scan.volume() != expected) {
                    helper.fail("scan of " + expected + " cells gave " + scan.outcome() + " " + scan.volume());
                    return;
                }
                best = Math.min(best, dt);
                total += dt;
            }
            report.append(String.format(Locale.ROOT, "%d cells: best %.3f ms, mean %.3f ms; ", expected, best / 1e6,
                    total / 1e6 / runs));
            // clear
            for (int x = 0; x <= r[0] + 1; x++)
                for (int y = 0; y <= r[1] + 1; y++)
                    for (int z = 0; z <= r[2] + 1; z++)
                        level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        Frostworks.LOGGER.info("GAMETEST BENCH world scan: {}", report);
        helper.succeed();
    }

    /**
     * At 16 rpm the compressor makes all the gas its stress pays for (the pipes no longer hold it back,
     * as they did with a pump's pressure at 16 rpm), and under 8 rpm it does not work at all.
     */
    @GameTest(template = SMALL, timeoutTicks = 200)
    public static void compressorIgnoresSpeedAboveTheMinimum(GameTestHelper helper) {
        BlockPos[] line = kineticLine(helper);
        var level = helper.getLevel();
        BlockPos pipe = line[2].east(), tank = pipe.east();
        level.setBlock(tank, AllBlocks.FLUID_TANK.getDefaultState(), Block.UPDATE_ALL);
        level.setBlock(pipe, Block.updateFromNeighbourShapes(AllBlocks.FLUID_PIPE.getDefaultState(), level, pipe),
                Block.UPDATE_ALL);
        setSpeed(helper, line[0], 4);
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    CompressorBlockEntity c = (CompressorBlockEntity) level.getBlockEntity(line[2]);
                    helper.assertTrue(c.appliedStress() == 0 && !c.turnsEnough(), "works at 4 rpm");
                    c.setSettingForTest(8192); // four times the default: more than a pump at 16 rpm could carry
                    setSpeed(helper, line[0], 16);
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    CompressorBlockEntity c = (CompressorBlockEntity) level.getBlockEntity(line[2]);
                    double paid = c.appliedStress() * com.frostworks.energy.Energy.kGas();
                    Frostworks.LOGGER.info("GAMETEST compressor at 16 rpm: {} mB/s made, {} mB/s paid for",
                            c.gasPerSecond(), paid);
                    helper.assertTrue(paid > 0 && c.gasPerSecond() >= paid * 0.9,
                            "made " + c.gasPerSecond() + " mB/s of " + paid);
                })
                .thenSucceed();
    }
}
