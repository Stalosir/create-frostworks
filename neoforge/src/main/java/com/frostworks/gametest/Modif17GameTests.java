package com.frostworks.gametest;

import com.frostworks.Frostworks;
import com.frostworks.FwConfig;
import com.frostworks.content.column.TowerBlock;
import com.frostworks.content.column.TowerBlockEntity;
import com.frostworks.content.infuser.CryoInfuserBlockEntity;
import com.frostworks.registry.FwBlocks;
import com.frostworks.registry.FwDataMaps;
import com.frostworks.registry.FwFluids;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** Ice, frozen by the infuser over a basin, compacted, and used by the tower. */
@GameTestHolder(Frostworks.MODID)
@PrefixGameTestTemplate(false)
public final class Modif17GameTests {
    private Modif17GameTests() {}

    private static final String SMALL = "empty_16x10x16";

    // ---- The infuser over a basin ---------------------------------------------------------------------------------

    /** Creative motor -> a machine (facing north, shaft along Z) two blocks above a basin. Returns {machine, basin}. */
    private static BlockPos[] basinRig(GameTestHelper helper, net.minecraft.world.level.block.state.BlockState machine) {
        ServerLevel level = helper.getLevel();
        BlockPos top = helper.absolutePos(new BlockPos(5, 3, 5)), basin = top.below(2), motor = top.north();
        level.setBlock(basin, AllBlocks.BASIN.getDefaultState(), Block.UPDATE_ALL);
        level.setBlock(top, machine, Block.UPDATE_ALL);
        level.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(DirectionalKineticBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
        if (level.getBlockEntity(motor) instanceof CreativeMotorBlockEntity m)
            m.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(64);
        return new BlockPos[]{top, basin};
    }

    private static BlockPos[] infuserOverBasin(GameTestHelper helper) {
        return basinRig(helper, FwBlocks.CRYO_INFUSER.getDefaultState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    private static BasinBlockEntity basin(GameTestHelper helper, BlockPos pos) {
        return (BasinBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    private static int count(BasinBlockEntity basin, Item item) {
        int n = 0;
        IItemHandler out = basin.getOutputInventory();
        for (int i = 0; i < out.getSlots(); i++)
            if (out.getStackInSlot(i).is(item))
                n += out.getStackInSlot(i).getCount();
        return n;
    }

    private static int water(BasinBlockEntity basin) {
        IFluidHandler tanks = helper(basin);
        int n = 0;
        for (int i = 0; i < tanks.getTanks(); i++)
            if (tanks.getFluidInTank(i).getFluid().isSame(Fluids.WATER))
                n += tanks.getFluidInTank(i).getAmount();
        return n;
    }

    private static IFluidHandler helper(BasinBlockEntity basin) {
        return com.frostworks.FwCaps.fluidAt(basin.getLevel(), basin.getBlockPos(), Direction.UP);
    }

    /** 1000 mB of water in the basin, the gas of one ice in the infuser: one ice, the water and the gas used. */
    @GameTest(template = SMALL, timeoutTicks = 400)
    public static void infuserFreezesWaterInABasin(GameTestHelper helper) {
        BlockPos[] rig = infuserOverBasin(helper);
        int gas = FwConfig.get(FwConfig.INFUSER_ICE_GAS);
        helper.runAfterDelay(5, () -> {
            ((CryoInfuserBlockEntity) helper.getLevel().getBlockEntity(rig[0])).tank()
                    .fill(FwFluids.gas(gas), IFluidHandler.FluidAction.EXECUTE);
            helper(basin(helper, rig[1])).fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        });
        helper.succeedWhen(() -> {
            BasinBlockEntity b = basin(helper, rig[1]);
            CryoInfuserBlockEntity infuser = (CryoInfuserBlockEntity) helper.getLevel().getBlockEntity(rig[0]);
            helper.assertTrue(count(b, Items.ICE) == 1, "ice in the basin: " + count(b, Items.ICE));
            helper.assertTrue(water(b) == 0, "water left: " + water(b));
            helper.assertTrue(infuser.tank().getFluidAmount() == 0, "gas left: " + infuser.tank().getFluidAmount());
        });
    }

    /** Water but no gas: no cycle, no ice, the goggles say the gas is missing; the gas arrives, the ice comes. */
    @GameTest(template = SMALL, timeoutTicks = 500)
    public static void noGasNoIce(GameTestHelper helper) {
        BlockPos[] rig = infuserOverBasin(helper);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> helper(basin(helper, rig[1]))
                        .fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE))
                .thenIdle(150)
                .thenExecute(() -> {
                    CryoInfuserBlockEntity infuser = (CryoInfuserBlockEntity) helper.getLevel().getBlockEntity(rig[0]);
                    helper.assertTrue(count(basin(helper, rig[1]), Items.ICE) == 0, "ice without gas");
                    helper.assertTrue(water(basin(helper, rig[1])) == 1000, "water used without gas");
                    helper.assertTrue(infuser.state() == CryoInfuserBlockEntity.State.NO_GAS,
                            "goggles: " + infuser.state());
                    infuser.tank().fill(FwFluids.gas(FwConfig.get(FwConfig.INFUSER_ICE_GAS)),
                            IFluidHandler.FluidAction.EXECUTE);
                })
                .thenWaitUntil(() -> helper.assertTrue(count(basin(helper, rig[1]), Items.ICE) == 1, "no ice once the gas came"))
                .thenSucceed();
    }

    /** Create's press compacts 9 ice into packed ice and 9 packed into blue ice (vanilla's 3 x 3 recipes, in a basin). */
    @GameTest(template = SMALL, timeoutTicks = 600)
    public static void thePressCompactsIce(GameTestHelper helper) {
        BlockPos[] rig = basinRig(helper, AllBlocks.MECHANICAL_PRESS.getDefaultState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        helper.runAfterDelay(5, () -> {
            IItemHandler in = basin(helper, rig[1]).getInputInventory();
            ItemHandlerHelper.insertItemStacked(in, new ItemStack(Items.ICE, 9), false);
            ItemHandlerHelper.insertItemStacked(in, new ItemStack(Items.PACKED_ICE, 9), false);
        });
        helper.succeedWhen(() -> {
            BasinBlockEntity b = basin(helper, rig[1]);
            helper.assertTrue(count(b, Items.BLUE_ICE) == 1, "blue ice: " + count(b, Items.BLUE_ICE));
            helper.assertTrue(count(b, Items.PACKED_ICE) == 1, "packed ice: " + count(b, Items.PACKED_ICE));
        });
    }

    /**
     * The infuser is one more way to make ice, not the only one (by design): Create's own ice recipes stay,
     * washing ice into packed ice and compacting snow into ice.
     */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void createsIceRecipesStay(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        for (String id : new String[]{"splashing/ice", "compacting/ice"})
            helper.assertTrue(recipes.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", id))
                    .isPresent(), "Create's " + id + " is gone");
        helper.succeed();
    }

    // ---- The tower --------------------------------------------------------------------------------------------------

    /** A 2-layer tower turning at 64 rpm (as the tower tests), gas and water in. Returns its controller. */
    private static BlockPos turningTower(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos c = helper.absolutePos(new BlockPos(5, 2, 5));
        BlockPos motor = c.offset(2, 0, 1);
        level.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalBlock.FACING,
                Direction.WEST), Block.UPDATE_ALL);
        if (level.getBlockEntity(motor) instanceof CreativeMotorBlockEntity m)
            m.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(64);
        TowerGameTests.tower(level, c, 2);
        return c;
    }

    private static IItemHandler items(ServerLevel level, BlockPos pos, Direction side) {
        return com.frostworks.FwCaps.itemsAt(level, pos, side);
    }

    /**
     * One plain ice, by the base's west side: the tower runs and makes its worth of Frosium (2000 / 9 mB), then
     * stops for want of ice, without breaking anything; the gas and the water stay.
     */
    @GameTest(template = SMALL, timeoutTicks = 800)
    public static void theTowerUsesItsIceThenStops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos c = turningTower(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(TowerBlock.at(level, c).layers() == 2, "not formed"))
                .thenExecute(() -> {
                    IFluidHandler h = com.frostworks.FwCaps.fluidAt(level, c, Direction.WEST);
                    h.fill(FwFluids.gas(8000), IFluidHandler.FluidAction.EXECUTE);
                    h.fill(new FluidStack(Fluids.WATER, 8000), IFluidHandler.FluidAction.EXECUTE);
                })
                .thenIdle(40)
                .thenExecute(() -> helper.assertTrue(TowerBlock.at(level, c).stopReason() == TowerBlockEntity.Stop.NO_ICE,
                        "without ice: " + TowerBlock.at(level, c).stopReason()))
                .thenExecute(() -> {
                    ItemStack rest = ItemHandlerHelper.insertItemStacked(items(level, c, Direction.WEST),
                            new ItemStack(Items.ICE), false);
                    helper.assertTrue(rest.isEmpty(), "the base refused the ice");
                })
                .thenWaitUntil(() -> helper.assertTrue(TowerBlock.at(level, c).isRunning(), "not running with ice"))
                .thenWaitUntil(() -> helper.assertTrue(TowerBlock.at(level, c).stopReason() == TowerBlockEntity.Stop.NO_ICE,
                        "still running: " + TowerBlock.at(level, c).stopReason()))
                .thenExecute(() -> {
                    TowerBlockEntity t = TowerBlock.at(level, c);
                    double expected = FwConfig.get(FwConfig.TOWER_FROSIUM_PER_ICE) / 9.0;
                    helper.assertTrue(Math.abs(t.frosium() - expected) <= 3,
                            "Frosium " + t.frosium() + " for one ice, expected " + expected);
                    helper.assertTrue(t.gas() > 0 && t.water() > 0, "gas " + t.gas() + " water " + t.water());
                    helper.assertTrue(t.iceStock() < 1e-3, "ice left: " + t.iceStock());
                    helper.assertTrue(level.getBlockState(c).getBlock() instanceof TowerBlock, "the tower broke");
                })
                .thenSucceed();
    }

    /**
     * The base's input: ice on the outer sides (and on top, for the mechanical arm), nothing underneath or on the inner
     * sides; anything that is not ice is refused; nothing comes out (a funnel does not empty it); 4 x 16 at most.
     */
    @GameTest(template = SMALL, timeoutTicks = 200)
    public static void theBaseTakesIceOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos c = turningTower(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(TowerBlock.at(level, c).layers() == 2, "not formed"))
                .thenExecute(() -> {
                    // c is the north-west base: west and north are outside, east and south inside
                    helper.assertTrue(items(level, c, Direction.WEST) != null && items(level, c, Direction.NORTH) != null,
                            "no input on the outer sides");
                    helper.assertTrue(items(level, c, Direction.UP) != null, "no input for the arm");
                    helper.assertTrue(items(level, c, Direction.DOWN) == null, "an input underneath");
                    helper.assertTrue(items(level, c, Direction.EAST) == null && items(level, c, Direction.SOUTH) == null,
                            "an input on an inner side");
                    helper.assertTrue(items(level, c.above(), Direction.WEST) == null, "an input on the column");
                    IItemHandler in = items(level, c, Direction.WEST);
                    helper.assertTrue(!ItemHandlerHelper.insertItemStacked(in, new ItemStack(Items.STONE), true).isEmpty(),
                            "stone taken");
                    ItemStack rest = ItemHandlerHelper.insertItemStacked(in, new ItemStack(Items.PACKED_ICE, 64), false);
                    rest = ItemHandlerHelper.insertItemStacked(in, rest, false);
                    helper.assertTrue(rest.getCount() == 0, "64 packed ice did not fit: " + rest);
                    helper.assertTrue(ItemHandlerHelper.insertItemStacked(in, new ItemStack(Items.PACKED_ICE), true)
                            .getCount() == 1, "more than 4 x 16 taken");
                    for (int i = 0; i < in.getSlots(); i++)
                        helper.assertTrue(in.extractItem(i, 1, false).isEmpty(), "ice taken out of slot " + i);
                })
                .thenSucceed();
    }

    // ---- Audit: a belt that goes at once gives the walls of all its passages back ----------------------------------

    /**
     * A belt of 6 with two passages (x 2 and x 4); a plain segment (x 3) destroyed as Create does it (no player): the
     * whole belt goes, and both walls drop, once each.
     */
    @GameTest(template = SMALL, timeoutTicks = 100)
    public static void aBeltGoneAtOnceGivesItsWallsBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 5)), end = helper.absolutePos(new BlockPos(6, 2, 5));
        for (BlockPos p : new BlockPos[]{start, end})
            level.setBlock(p, AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z),
                    Block.UPDATE_ALL);
        com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem.createBelts(level, start, end);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    for (int x : new int[]{2, 4}) {
                        BlockPos p = helper.absolutePos(new BlockPos(x, 2, 5));
                        helper.assertTrue(com.frostworks.content.passage.BeltPassage.encase(level, p), "not encased at " + x);
                        helper.assertTrue(com.frostworks.content.passage.BeltPassage.isMarked(level, p), "not listed at " + x);
                    }
                    level.destroyBlock(helper.absolutePos(new BlockPos(3, 2, 5)), true);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(!AllBlocks.BELT.has(level.getBlockState(helper.absolutePos(new BlockPos(5, 2, 5)))),
                            "the belt is still there");
                    int walls = 0;
                    for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            new net.minecraft.world.phys.AABB(start).inflate(8)))
                        if (item.getItem().is(FwBlocks.COLD_WALL.get().asItem()))
                            walls += item.getItem().getCount();
                    helper.assertTrue(walls == 2, walls + " cold wall(s) dropped, 2 expected");
                })
                .thenSucceed();
    }

    // ---- The table of values and the config ---------------------------------------------------------------------

    /** Plain ice 1/9, packed ice 1, blue ice 9 (the number of packed its recipe takes); stone nothing. */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void iceValues(GameTestHelper helper) {
        helper.assertTrue(Math.abs(FwDataMaps.iceValue(new ItemStack(Items.ICE)) - 1 / 9f) < 1e-6, "ice");
        helper.assertTrue(FwDataMaps.iceValue(new ItemStack(Items.PACKED_ICE)) == 1, "packed ice");
        helper.assertTrue(FwDataMaps.iceValue(new ItemStack(Items.BLUE_ICE)) == 9, "blue ice");
        helper.assertTrue(FwDataMaps.iceValue(new ItemStack(Items.STONE)) == 0, "stone");
        helper.assertTrue(Math.abs(9 * FwDataMaps.iceValue(new ItemStack(Items.ICE))
                - FwDataMaps.iceValue(new ItemStack(Items.PACKED_ICE))) < 1e-6, "9 ice is not a packed ice");
        helper.succeed();
    }

    /** The two new settings: their defaults, and nothing at 0 or below (no free ice, no free Frosium). */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void iceSettings(GameTestHelper helper) {
        check(helper, FwConfig.INFUSER_ICE_GAS, 250);
        check(helper, FwConfig.TOWER_FROSIUM_PER_ICE, 2000);
        helper.succeed();
    }

    private static void check(GameTestHelper helper, ModConfigSpec.IntValue value, int byDefault) {
        helper.assertTrue(value.getDefault() == byDefault, value.getPath() + " default " + value.getDefault());
        helper.assertTrue(!value.getSpec().test(0) && !value.getSpec().test(-1), value.getPath() + " takes 0");
        helper.assertTrue(value.getSpec().test(1) && value.getSpec().test(byDefault), value.getPath() + " refuses " + byDefault);
    }
}
