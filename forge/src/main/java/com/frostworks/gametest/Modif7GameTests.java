package com.frostworks.gametest;

import com.frostworks.Frostworks;
import com.frostworks.content.infuser.CryoInfuserBlockEntity;
import com.frostworks.content.infuser.InfusingRecipe;
import com.frostworks.registry.FwBlocks;
import com.frostworks.registry.FwFluids;
import com.frostworks.registry.FwItems;
import com.frostworks.room.core.RoomStatus;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Recipes, cryogenic infuser, pipe passage. */
@GameTestHolder(Frostworks.MODID)
@PrefixGameTestTemplate(false)
public final class Modif7GameTests {
    private Modif7GameTests() {}

    private static final String SMALL = "empty_16x10x16";

    // ---- Part B: recipes ----------------------------------------------------------------------------------------

    private static Recipe<?> recipe(GameTestHelper helper, String path) {
        var holder = helper.getLevel().getRecipeManager().byKey(Frostworks.id(path)).orElse(null);
        helper.assertTrue(holder != null, "recipe " + path + " not loaded");
        return holder;
    }

    private static void assertResult(GameTestHelper helper, String path, Block block) {
        ItemStack result = recipe(helper, path).getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(result.is(block.asItem()) && result.getCount() == 1, path + " gives " + result);
    }

    /** Every crafting recipe of the prototype is loaded and gives one of the right block. */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void craftingRecipesGiveTheRightBlock(GameTestHelper helper) {
        assertResult(helper, "crafting/compressor", FwBlocks.COMPRESSOR.get());
        assertResult(helper, "crafting/vent", FwBlocks.VENT.get());
        assertResult(helper, "crafting/insulated_door", FwBlocks.INSULATED_DOOR.get());
        assertResult(helper, "crafting/reservoir", FwBlocks.RESERVOIR.get());
        assertResult(helper, "crafting/column_base", FwBlocks.COLUMN_BASE.get());
        assertResult(helper, "crafting/expander", FwBlocks.EXPANDER.get());
        assertResult(helper, "crafting/cryo_infuser", FwBlocks.CRYO_INFUSER.get());
        assertResult(helper, "item_application/cold_wall", FwBlocks.COLD_WALL.get());
        // the infusing recipes: their gas, their result
        var sheet = (InfusingRecipe) recipe(helper, "infusing/cold_iron_sheet");
        var kelp = (InfusingRecipe) recipe(helper, "infusing/cold_dried_kelp");
        helper.assertTrue(sheet.gasAmount() == 500 && kelp.gasAmount() == 250,
                "gas " + sheet.gasAmount() + " / " + kelp.gasAmount());
        helper.assertTrue(sheet.getFluidIngredients().get(0).test(FwFluids.gas(500)), "sheet: not cold gas");
        helper.assertTrue(sheet.getRollableResultsAsItemStacks().get(0).is(FwItems.COLD_IRON_SHEET.get()), "sheet result");
        helper.assertTrue(kelp.getRollableResultsAsItemStacks().get(0).is(FwItems.COLD_DRIED_KELP.get()), "kelp result");
        helper.succeed();
    }

    // ---- Part A: cryogenic infuser ------------------------------------------------------------------------------

    /** Creative motor -> infuser (facing north, shaft along Z), depot two blocks below. Returns {motor, infuser, depot}. */
    private static BlockPos[] infuserRig(GameTestHelper helper, int rpm) {
        ServerLevel level = helper.getLevel();
        BlockPos infuser = helper.absolutePos(new BlockPos(5, 3, 5));
        BlockPos motor = infuser.north();
        BlockPos depot = infuser.below(2);
        level.setBlock(depot, AllBlocks.DEPOT.getDefaultState(), Block.UPDATE_ALL);
        level.setBlock(infuser, FwBlocks.CRYO_INFUSER.getDefaultState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), Block.UPDATE_ALL);
        level.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(DirectionalKineticBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
        if (level.getBlockEntity(motor) instanceof CreativeMotorBlockEntity m)
            m.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(rpm);
        return new BlockPos[]{motor, infuser, depot};
    }

    private static DepotBehaviour depot(GameTestHelper helper, BlockPos pos) {
        return BlockEntityBehaviour.get(helper.getLevel(), pos, DepotBehaviour.TYPE);
    }

    private static CryoInfuserBlockEntity infuser(GameTestHelper helper, BlockPos pos) {
        return (CryoInfuserBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    /** An iron sheet on the depot and 500 mB of cold gas: a cold iron sheet, and the tank empty. */
    @GameTest(template = SMALL, timeoutTicks = 400)
    public static void infuserMakesAColdIronSheet(GameTestHelper helper) {
        BlockPos[] rig = infuserRig(helper, 64);
        helper.runAfterDelay(5, () -> {
            infuser(helper, rig[1]).tank().fill(FwFluids.gas(500), IFluidHandler.FluidAction.EXECUTE);
            depot(helper, rig[2]).setHeldItem(new TransportedItemStack(AllItems.IRON_SHEET.asStack()));
        });
        helper.succeedWhen(() -> {
            ItemStack held = depot(helper, rig[2]).getHeldItemStack();
            helper.assertTrue(held.is(FwItems.COLD_IRON_SHEET.get()), "on the depot: " + held);
            helper.assertTrue(infuser(helper, rig[1]).tank().getFluidAmount() == 0,
                    "gas left: " + infuser(helper, rig[1]).tank().getFluidAmount());
        });
    }

    /** The arm stays down on the item at least infuserDwellTicks (2 s) before it turns into a cold sheet. */
    @GameTest(template = SMALL, timeoutTicks = 400)
    public static void infuserArmDwellsOnTheItem(GameTestHelper helper) {
        BlockPos[] rig = infuserRig(helper, 64);
        long[] down = {-1};
        helper.startSequence()
                .thenExecute(() -> {
                    infuser(helper, rig[1]).tank().fill(FwFluids.gas(500), IFluidHandler.FluidAction.EXECUTE);
                    depot(helper, rig[2]).setHeldItem(new TransportedItemStack(AllItems.IRON_SHEET.asStack()));
                })
                .thenWaitUntil(() -> helper.assertTrue(infuser(helper, rig[1]).getPressingBehaviour().runningTicks
                        == com.simibubi.create.content.kinetics.press.PressingBehaviour.CYCLE / 2 - 1, "arm not down"))
                .thenExecute(() -> down[0] = helper.getLevel().getGameTime())
                .thenWaitUntil(() -> helper.assertTrue(depot(helper, rig[2]).getHeldItemStack()
                        .is(FwItems.COLD_IRON_SHEET.get()), "not infused yet"))
                .thenExecute(() -> {
                    long held = helper.getLevel().getGameTime() - down[0];
                    helper.assertTrue(held >= com.frostworks.FwConfig.get(com.frostworks.FwConfig.INFUSER_DWELL_TICKS),
                            "arm down only " + held + " ticks");
                })
                .thenSucceed();
    }

    /** Dried kelp takes 250 mB: from 1000, 750 stay. */
    @GameTest(template = SMALL, timeoutTicks = 400)
    public static void infuserMakesChilledKelp(GameTestHelper helper) {
        BlockPos[] rig = infuserRig(helper, 64);
        helper.runAfterDelay(5, () -> {
            infuser(helper, rig[1]).tank().fill(FwFluids.gas(1000), IFluidHandler.FluidAction.EXECUTE);
            depot(helper, rig[2]).setHeldItem(new TransportedItemStack(new ItemStack(Items.DRIED_KELP)));
        });
        helper.succeedWhen(() -> {
            ItemStack held = depot(helper, rig[2]).getHeldItemStack();
            helper.assertTrue(held.is(FwItems.COLD_DRIED_KELP.get()), "on the depot: " + held);
            helper.assertTrue(infuser(helper, rig[1]).tank().getFluidAmount() == 750,
                    "gas left: " + infuser(helper, rig[1]).tank().getFluidAmount());
        });
    }

    /** Its tank takes cold gas only, through its inlet only (the right face). */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void infuserTakesOnlyColdGas(GameTestHelper helper) {
        BlockPos[] rig = infuserRig(helper, 0);
        helper.runAfterDelay(2, () -> {
            ServerLevel level = helper.getLevel();
            IFluidHandler side = com.frostworks.FwCaps.fluidAt(level, rig[1], Direction.EAST);
            helper.assertTrue(side != null, "no handler on the east face");
            // the inlet holes are across the shaft (east and west for north) and nowhere else
            for (Direction d : Direction.values())
                if (d.getAxis() != Direction.Axis.X) // Both holes (east and west) take the gas
                    helper.assertTrue(com.frostworks.FwCaps.fluidAt(level, rig[1], d) == null,
                            "a handler on face " + d);
            var sim = IFluidHandler.FluidAction.SIMULATE;
            helper.assertTrue(side.fill(new FluidStack(Fluids.WATER, 100), sim) == 0, "took water");
            helper.assertTrue(side.fill(FwFluids.gas(1500), sim) == 1000, "cold gas: tank of 1000 mB");
            // Frosium goes to its own tank (the chilled ores), never into the gas tank
            helper.assertTrue(side.fill(new FluidStack(FwFluids.frosium(), 1500), sim) == 1000, "Frosium: tank of 1000 mB");
            var infuser = (com.frostworks.content.infuser.CryoInfuserBlockEntity) level.getBlockEntity(rig[1]);
            side.fill(new FluidStack(FwFluids.frosium(), 100), IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(infuser.tank().isEmpty() && infuser.frosiumTank().getFluidAmount() == 100, "Frosium in the gas tank");
            // one fluid at a time: no gas while Frosium is in, and no Frosium while gas is in
            helper.assertTrue(side.fill(FwFluids.gas(100), sim) == 0, "took gas with Frosium in");
            side.drain(100, IFluidHandler.FluidAction.EXECUTE);
            side.fill(FwFluids.gas(100), IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(infuser.tank().getFluidAmount() == 100, "no gas once the Frosium is out");
            helper.assertTrue(side.fill(new FluidStack(FwFluids.frosium(), 100), sim) == 0, "took Frosium with gas in");
            helper.succeed();
        });
    }

    /** Without gas, or without rotation, the sheet stays as it is and the goggles say why. */
    @GameTest(template = SMALL, timeoutTicks = 300)
    public static void infuserWaitsForGasAndRotation(GameTestHelper helper) {
        BlockPos[] rig = infuserRig(helper, 64);
        helper.startSequence()
                .thenExecute(() -> depot(helper, rig[2])
                        .setHeldItem(new TransportedItemStack(AllItems.IRON_SHEET.asStack())))
                .thenIdle(100)
                .thenExecute(() -> {
                    helper.assertTrue(depot(helper, rig[2]).getHeldItemStack().is(AllItems.IRON_SHEET.get()),
                            "infused without gas");
                    helper.assertTrue(infuser(helper, rig[1]).state() == CryoInfuserBlockEntity.State.NO_GAS,
                            "state " + infuser(helper, rig[1]).state());
                    if (helper.getLevel().getBlockEntity(rig[0]) instanceof CreativeMotorBlockEntity m)
                        m.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(0);
                    infuser(helper, rig[1]).tank().fill(FwFluids.gas(500), IFluidHandler.FluidAction.EXECUTE);
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    helper.assertTrue(depot(helper, rig[2]).getHeldItemStack().is(AllItems.IRON_SHEET.get()),
                            "infused without rotation");
                    helper.assertTrue(infuser(helper, rig[1]).tank().getFluidAmount() == 500, "gas used");
                    helper.assertTrue(infuser(helper, rig[1]).state() == CryoInfuserBlockEntity.State.NO_ROTATION,
                            "state " + infuser(helper, rig[1]).state());
                })
                .thenSucceed();
    }

    /** The whole chain of /frostworks infusion rig: the compressor's gas reaches the infuser, which infuses a sheet. */
    @GameTest(template = SMALL, timeoutTicks = 600)
    public static void infusionRigMakesAColdSheet(GameTestHelper helper) {
        BlockPos corner = helper.absolutePos(new BlockPos(2, 0, 4));
        com.frostworks.content.infuser.InfusionRig.build(helper.getLevel(), corner);
        BlockPos depot = com.frostworks.content.infuser.InfusionRig.depot(corner);
        helper.runAfterDelay(5, () -> depot(helper, depot)
                .setHeldItem(new TransportedItemStack(AllItems.IRON_SHEET.asStack())));
        helper.succeedWhen(() -> {
            ItemStack held = depot(helper, depot).getHeldItemStack();
            helper.assertTrue(held.is(FwItems.COLD_IRON_SHEET.get()), "on the depot: " + held + ", infuser gas "
                    + infuser(helper, com.frostworks.content.infuser.InfusionRig.infuser(corner)).tank().getFluidAmount());
        });
    }

    /** Below -15 deg C a survival player freezes (vanilla frost), up to 37.5 % of the full frost: never hurt. */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void coldRoomFreezesButNeverHurts(GameTestHelper helper) {
        Player warm = MockPlayers.survival(helper);
        for (int i = 0; i < 300; i++)
            com.frostworks.room.ColdExposure.expose(warm, -10f);
        helper.assertTrue(warm.getTicksFrozen() == 0, "frozen at -10: " + warm.getTicksFrozen());
        Player cold = MockPlayers.survival(helper);
        for (int i = 0; i < 300; i++)
            com.frostworks.room.ColdExposure.expose(cold, -20f);
        helper.assertTrue(cold.getTicksFrozen() == (int) (cold.getTicksRequiredToFreeze() * 0.375f) && !cold.isFullyFrozen(),
                "frozen " + cold.getTicksFrozen() + " of " + cold.getTicksRequiredToFreeze());
        helper.succeed();
    }

    /** The expander lines up with its neighbours: a shaft behind it wins, then a pipe in front, then pipes on its sides. */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void expanderLinesUpWithItsNeighbours(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 5));
        var align = (java.util.function.Supplier<Direction>) () ->
                com.frostworks.content.expander.ExpanderBlock.alignedFacing(level, pos, Direction.NORTH);
        helper.assertTrue(align.get() == Direction.NORTH, "nothing around: as placed");
        level.setBlock(pos.south(), AllBlocks.FLUID_PIPE.getDefaultState(), Block.UPDATE_ALL);
        helper.assertTrue(align.get() == Direction.SOUTH, "a pipe south: gas out south, got " + align.get());
        level.setBlock(pos.east(), AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X),
                Block.UPDATE_ALL);
        // The shaft goes across (the faces beside the flanges), the pipe stays at the gas outlet
        helper.assertTrue(align.get() == Direction.SOUTH, "a shaft east: shaft across, gas still south, got " + align.get());
        level.setBlock(pos.south(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos.west(), AllBlocks.FLUID_PIPE.getDefaultState(), Block.UPDATE_ALL);
        Direction d = align.get();
        helper.assertTrue(d.getAxis() == Direction.Axis.Z, "a shaft east, a pipe west: shaft across, got " + d);
        helper.succeed();
    }

    // ---- Part C: pipe passage -----------------------------------------------------------------------------------

    /**
     * Creative tank of water -> pump (east) -> pipe passage (axis X) -> Create tank: the water goes through, straight;
     * a tank beside the passage gets nothing.
     */
    @GameTest(template = SMALL, timeoutTicks = 300)
    public static void pipePassageCarriesFluidStraight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(2, 1, 5));
        BlockPos pump = source.east(), passage = pump.east(), target = passage.east(), beside = passage.north();
        level.setBlock(source, AllBlocks.CREATIVE_FLUID_TANK.getDefaultState(), Block.UPDATE_ALL);
        level.setBlock(target, AllBlocks.FLUID_TANK.getDefaultState(), Block.UPDATE_ALL);
        level.setBlock(beside, AllBlocks.FLUID_TANK.getDefaultState(), Block.UPDATE_ALL);
        level.setBlock(passage, FwBlocks.PIPE_PASSAGE.getDefaultState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), Block.UPDATE_ALL);
        level.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.EAST),
                Block.UPDATE_ALL);
        level.setBlock(pump.above(), AllBlocks.COGWHEEL.getDefaultState()
                .setValue(CogWheelBlock.AXIS, Direction.Axis.X), Block.UPDATE_ALL);
        level.setBlock(pump.above().west(), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(DirectionalKineticBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (level.getBlockEntity(source) instanceof CreativeFluidTankBlockEntity tank
                    && tank.getTankInventory() instanceof CreativeFluidTankBlockEntity.CreativeSmartFluidTank creative)
                creative.setContainedFluid(new FluidStack(Fluids.WATER, 1000));
        });
        helper.succeedWhen(() -> {
            IFluidHandler out = com.frostworks.FwCaps.fluidAt(level, target, Direction.WEST);
            IFluidHandler side = com.frostworks.FwCaps.fluidAt(level, beside, Direction.SOUTH);
            helper.assertTrue(out != null && out.getFluidInTank(0).getAmount() > 0, "nothing came through");
            helper.assertTrue(side != null && side.getFluidInTank(0).isEmpty(), "water leaked sideways");
        });
    }

    /** The pipe passage in the wall of a cooled box: the room stays closed and COLD. */
    @GameTest(template = SMALL, timeoutTicks = 200)
    public static void pipePassageKeepsTheRoomCold(GameTestHelper helper) {
        Modif3GameTests.Box box = Modif3GameTests.box(helper);
        helper.getLevel().setBlock(box.passage(), FwBlocks.PIPE_PASSAGE.getDefaultState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), Block.UPDATE_ALL);
        helper.succeedWhen(() -> {
            var room = Modif3GameTests.room(helper, box);
            helper.assertTrue(room != null && room.status() == RoomStatus.COLD,
                    "room " + (room == null ? "none" : room.status()));
        });
    }

    /** Right click on a straight Create pipe with a cold wall: the pipe becomes a passage on its axis. */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void coldWallEncasesAPipe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 5));
        for (BlockPos p : new BlockPos[]{pos.west(), pos, pos.east()})
            level.setBlock(p, AllBlocks.FLUID_PIPE.getDefaultState(), Block.UPDATE_ALL);
        for (BlockPos p : new BlockPos[]{pos.west(), pos, pos.east()})
            level.setBlock(p, Block.updateFromNeighbourShapes(level.getBlockState(p), level, p), Block.UPDATE_ALL);
        Player player = MockPlayers.survival(helper);
        ItemStack walls = new ItemStack(FwBlocks.COLD_WALL.asItem(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, walls);
        BlockState pipe = level.getBlockState(pos);
        var result = pipe.use(level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        BlockState after = level.getBlockState(pos);
        helper.assertTrue(result.consumesAction(), "not encased: " + result);
        helper.assertTrue(FwBlocks.PIPE_PASSAGE.has(after), "block after: " + after);
        helper.assertTrue(after.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X, "axis " + after);
        helper.assertTrue(walls.getCount() == 1, "cold walls left: " + walls.getCount());
        helper.succeed();
    }

    /**
     * The cold wall as Create's casings are made, in the world: a cold iron sheet used on a placed stone
     * block turns it into a cold wall (Create's item application, through the right-click event). Survival uses up the
     * sheet, creative keeps it.
     */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void sheetOnPlacedStoneMakesAColdWall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (GameType mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) {
            BlockPos stone = helper.absolutePos(new BlockPos(mode == GameType.SURVIVAL ? 2 : 4, 1, 2));
            level.setBlock(stone, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            Player player = mode == GameType.SURVIVAL ? MockPlayers.survival(helper) : MockPlayers.creative(helper);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FwItems.COLD_IRON_SHEET.get(), 2));
            var event = net.minecraftforge.common.ForgeHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND,
                    stone, new BlockHitResult(Vec3.atCenterOf(stone), Direction.UP, stone, false));
            helper.assertTrue(event.isCanceled(), mode + ": the right click was not taken by a recipe");
            helper.assertTrue(level.getBlockState(stone).is(FwBlocks.COLD_WALL.get()),
                    mode + ": the stone became " + level.getBlockState(stone));
            int left = player.getMainHandItem().getCount();
            helper.assertTrue(left == (mode == GameType.SURVIVAL ? 1 : 2), mode + ": sheets left " + left);
        }
        helper.succeed();
    }
}
