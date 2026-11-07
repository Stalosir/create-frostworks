package com.frostworks.gametest;

import java.util.Locale;

import com.frostworks.Frostworks;
import com.frostworks.content.column.TowerBlockEntity;
import com.frostworks.content.expander.ExpanderBlockEntity;
import com.frostworks.content.vent.VentBlock;
import com.frostworks.content.vent.VentBlockEntity;
import com.frostworks.energy.LoopLedger;
import com.frostworks.energy.LoopRig;
import com.frostworks.registry.FwBlocks;
import com.frostworks.registry.FwFluids;
import com.frostworks.room.Room;
import com.frostworks.room.RoomManager;
import com.frostworks.room.TestRoomBuilder;
import com.frostworks.room.core.RoomStatus;
import com.simibubi.create.content.kinetics.gauge.StressGaugeBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** the full loop compressor -> distillation tower -> reservoir -> expander, and its energy balance. */
public final class LoopGameTests {
    private LoopGameTests() {}

    private static final String LOOP = "empty_24x8x24";
    private static final String SMALL = "empty_16x10x16";
    /** Ticks the loop runs before the balance is read. */
    private static final int RUN_TICKS = 1200;
    /** Covers the whole rig from its center, and nothing of a neighbour test. */
    private static final int LEDGER_RADIUS = 16;

    private static LoopRig.Layout rig(GameTestHelper helper, int share) {
        return LoopRig.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 15)), 64, 1024, share);
    }

    private static LoopLedger ledger(GameTestHelper helper, LoopRig.Layout rig) {
        return LoopLedger.around(helper.getLevel(), rig.center(), LEDGER_RADIUS);
    }

    private static float gauge(GameTestHelper helper, LoopRig.Layout rig) {
        return helper.getLevel().getBlockEntity(rig.gauge()) instanceof StressGaugeBlockEntity g ? g.getNetworkStress()
                : Float.NaN;
    }

    // ---- The chain works --------------------------------------------------------------------------------------

    @GameTest(template = LOOP, timeoutTicks = 2400, batch = "loop_chain")
    public static void loopChainWorks(GameTestHelper helper) {
        LoopRig.Layout rig = rig(helper, 50);
        ServerLevel level = helper.getLevel();
        // The tower no longer pushes, a pump pulls the liquid into the reservoir while another pump
        // empties it towards the expander: it can be empty at any given tick. Remember whether liquid went through.
        boolean[] reservoirSawLiquid = {false};
        helper.onEachTick(() -> {
            IFluidHandler r = com.frostworks.FwCaps.fluidAt(level, rig.reservoir(), null);
            if (r != null && FwFluids.isLiquid(r.getFluidInTank(0)))
                reservoirSawLiquid[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(level.getBlockEntity(rig.column()) instanceof TowerBlockEntity c && c.layers() == 3,
                    "tower of 3 layers");
            IFluidHandler reservoir = com.frostworks.FwCaps.fluidAt(level, rig.reservoir(), null);
            TowerBlockEntity t = (TowerBlockEntity) level.getBlockEntity(rig.column());
            helper.assertTrue(reservoir != null && reservoirSawLiquid[0],
                    "no liquid through the reservoir yet; tower " + t.layers() + " layers, gas " + t.gas() + " water "
                            + t.water() + " Frosium " + t.frosium() + " speed " + t.getSpeed() + " stop " + t.stopReason()
                            + " state " + level.getBlockState(rig.column()));
            ExpanderBlockEntity e = (ExpanderBlockEntity) level.getBlockEntity(rig.expander());
            helper.assertTrue(e.stressOut() > 0, "expander gave no stress back yet");
            helper.assertTrue(e.gasOutEnergy() > 0, "no gas left the expander yet");
            Room room = RoomManager.get(level).roomAt(rig.roomInterior());
            VentBlockEntity v1 = (VentBlockEntity) level.getBlockEntity(rig.at(16, 1, -6));
            helper.assertTrue(room != null && room.activeVents() > 0, "the room vents got no gas yet: room "
                    + (room == null ? "none" : room.status() + " vol " + room.volume + " vents " + room.ventCount())
                    + ", vent gas " + v1.gas().getAmount() + " active " + v1.isActive() + " " + v1.debug());
            Frostworks.LOGGER.info("GAMETEST T3 chain works after {} ticks: reservoir {} mB, stressometer {} su, {}",
                    helper.getTick(), reservoir.getFluidInTank(0).getAmount(), gauge(helper, rig),
                    describe(ledger(helper, rig)));
        });
    }

    // ---- Balance: what enters comes out, once ------------------------------------------------------------------

    private static void balance(GameTestHelper helper, int share) {
        LoopRig.Layout rig = rig(helper, share);
        float[] stress = new float[2];
        helper.runAfterDelay(RUN_TICKS / 2, () -> stress[0] = gauge(helper, rig));
        helper.runAfterDelay(RUN_TICKS, () -> {
            LoopLedger l = ledger(helper, rig);
            ExpanderBlockEntity e = (ExpanderBlockEntity) helper.getLevel().getBlockEntity(rig.expander());
            Frostworks.LOGGER.info("GAMETEST T3 balance f={}%: {} | stressometer at mid-run {} su, expander running {}",
                    share, describe(l), stress[0], e.isRunning());
            helper.assertTrue(l.in() > 10_000, "too little energy entered: " + l.in());
            helper.assertTrue(l.out() > 0, "nothing came back");
            if (share == 100)
                helper.assertTrue(l.gasOut() == 0, "gas came out at f = 100 %");
            if (share == 0)
                helper.assertTrue(l.stressOut() == 0, "stress came out at f = 0 %");
            double ratio = l.ratio();
            if (Math.abs(ratio - 1) > 0.02)
                helper.fail(String.format(Locale.ROOT, "(returned + stock) / entered = %.4f", ratio));
            helper.succeed();
        });
    }

    @GameTest(template = LOOP, timeoutTicks = RUN_TICKS + 100, batch = "loop_f100")
    public static void balanceShare100(GameTestHelper helper) {
        balance(helper, 100);
    }

    @GameTest(template = LOOP, timeoutTicks = RUN_TICKS + 100, batch = "loop_f50")
    public static void balanceShare50(GameTestHelper helper) {
        balance(helper, 50);
    }

    @GameTest(template = LOOP, timeoutTicks = RUN_TICKS + 100, batch = "loop_f0")
    public static void balanceShare0(GameTestHelper helper) {
        balance(helper, 0);
    }

    static String describe(LoopLedger l) {
        return String.format(Locale.ROOT,
                "entered %.0f su.s, returned %.0f (stress %.0f + gas %.0f), stock %.0f, returned/entered %.1f %%, "
                        + "(returned + stock)/entered %.3f %%",
                l.in(), l.out(), l.stressOut(), l.gasOut(), l.stock(), 100 * l.returnedRatio(), 100 * l.ratio());
    }

    // ---- Reservoir and liquid-fed vents ------------------------------------------------------------------------

    @GameTest(template = SMALL, timeoutTicks = 100)
    public static void reservoirRefusesColdGas(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 4));
        helper.getLevel().setBlock(pos, FwBlocks.RESERVOIR.getDefaultState(), Block.UPDATE_ALL);
        helper.runAfterDelay(5, () -> {
            IFluidHandler tank = com.frostworks.FwCaps.fluidAt(helper.getLevel(), pos, Direction.UP);
            helper.assertTrue(tank != null, "no fluid handler on the reservoir");
            int gas = tank.fill(FwFluids.gas(1000), IFluidHandler.FluidAction.EXECUTE);
            int water = tank.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000),
                    IFluidHandler.FluidAction.EXECUTE);
            int air = tank.fill(new FluidStack(FwFluids.frosium(), 1000), IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(gas == 0, "cold gas accepted: " + gas);
            helper.assertTrue(water == 0, "water accepted: " + water);
            helper.assertTrue(air == 1000, "Frosium refused: " + air);
            helper.assertTrue(tank.getTankCapacity(0) == 8000, "capacity " + tank.getTankCapacity(0));
            helper.succeed();
        });
    }

    /** Our reservoirs merge with each other (Create's multiblock code), never with a Create tank next to them. */
    @GameTest(template = SMALL, timeoutTicks = 100)
    public static void reservoirsMergeOnlyTogether(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos low = helper.absolutePos(new BlockPos(4, 1, 4));
        level.setBlock(low, FwBlocks.RESERVOIR.getDefaultState(), Block.UPDATE_ALL);
        level.setBlock(low.above(), FwBlocks.RESERVOIR.getDefaultState(), Block.UPDATE_ALL);
        BlockPos create = low.east();
        level.setBlock(create, com.simibubi.create.AllBlocks.FLUID_TANK.getDefaultState(), Block.UPDATE_ALL);
        helper.succeedWhen(() -> {
            IFluidHandler ours = com.frostworks.FwCaps.fluidAt(level, low, Direction.UP);
            IFluidHandler theirs = com.frostworks.FwCaps.fluidAt(level, create, Direction.UP);
            helper.assertTrue(ours != null && ours.getTankCapacity(0) == 16000,
                    "two reservoirs: capacity " + (ours == null ? -1 : ours.getTankCapacity(0)));
            helper.assertTrue(theirs != null && theirs.getTankCapacity(0) == 8000,
                    "Create tank next to them: capacity " + (theirs == null ? -1 : theirs.getTankCapacity(0)));
            helper.assertTrue(theirs.fill(FwFluids.gas(100), IFluidHandler.FluidAction.SIMULATE) == 100,
                    "the Create tank must stay an ordinary tank (it takes cold gas)");
        });
    }

    @GameTest(template = SMALL, timeoutTicks = 300)
    public static void threeFrosiumVentsCoolTheRoom(GameTestHelper helper) {
        BlockPos corner = helper.absolutePos(new BlockPos(2, 1, 2));
        TestRoomBuilder.build(helper.getLevel(), corner, 3);
        for (int x = 1; x <= 3; x++)
            if (helper.getLevel().getBlockEntity(corner.offset(x, TestRoomBuilder.SIZE_Y - 1, 1)) instanceof VentBlockEntity v)
                v.setCreativeSupply(FwFluids.frosium());
        helper.succeedWhen(() -> {
            Room room = RoomManager.get(helper.getLevel()).roomAt(corner.offset(2, 1, 2));
            helper.assertTrue(room != null, "no room");
            helper.assertTrue(room.volume == 96, "volume " + room.volume);
            helper.assertTrue(room.status() == RoomStatus.COLD,
                    "status " + room.status() + ", capacity " + room.capacity());
            helper.assertTrue(room.capacity() == 192, "capacity " + room.capacity()); // 3 x 64 cells, room of 96
            // Frosium alone: deeper cold, faster machines (its target, -20 deg C)
            helper.assertTrue(room.coldTarget() == -20 && com.frostworks.overspeed.Overspeed.maxRpm(room) == 384,
                    "target " + room.coldTarget() + ", max " + com.frostworks.overspeed.Overspeed.maxRpm(room));
            helper.assertTrue(helper.getLevel().getBlockState(corner.offset(1, TestRoomBuilder.SIZE_Y - 1, 1))
                    .getBlock() instanceof VentBlock, "vent");
        });
    }
}
