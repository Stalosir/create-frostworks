package com.frostworks.client.ponder;

import static com.frostworks.client.ponder.ColdRoomScenes.value;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.frostworks.FwConfig;
import com.frostworks.content.compressor.CompressorBlockEntity;
import com.frostworks.content.column.TowerBlockEntity;
import com.frostworks.content.expander.ExpanderBlockEntity;
import com.frostworks.content.infuser.CryoInfuserBlockEntity;
import com.frostworks.content.infuser.InfusingRecipe;
import com.frostworks.network.RoomInfoPayload;
import com.frostworks.registry.FwBlocks;
import com.frostworks.registry.FwFluids;
import com.frostworks.registry.FwItems;
import com.frostworks.registry.FwRecipeTypes;
import com.frostworks.room.RoomText;
import com.frostworks.room.core.RoomStatus;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour.Mode;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The six machine scenes, each on its own small schematic ({@code MachineSchematics} in the pilot).
 * For players who know Create: what the machine has of its own, nothing about rotation or pipes. Every number comes
 * from the config, the code or the recipes.
 */
public final class MachineScenes {
    private MachineScenes() {}

    // ---- Helpers --------------------------------------------------------------------------------------------------

    private static String one(double v) {
        return String.format(Locale.ROOT, "%.1f", v).replace(".0", "");
    }

    /** Puts {@code amount} mB of {@code fluid} in the tank of a block entity, under the key {@code key}. */
    private static void fillTankNbt(CompoundTag nbt, String key, Fluid fluid, int amount) {
        FluidTank tank = new FluidTank(Math.max(1, amount));
        tank.fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE);
        nbt.put(key, tank.writeToNBT(Minecraft.getInstance().level.registryAccess(), new CompoundTag()));
    }

    /** The gas an infusing recipe takes for an input item, read from the recipes; -1 if there is none. */
    private static int gasFor(ItemStack input) {
        try {
            var level = Minecraft.getInstance().level;
            for (var holder : level.getRecipeManager().getAllRecipesFor(FwRecipeTypes.INFUSING
                    .<net.minecraft.world.item.crafting.SingleRecipeInput, InfusingRecipe>getType())) {
                InfusingRecipe recipe = holder.value();
                if (recipe.getIngredients().get(0).test(input))
                    return recipe.gasAmount();
            }
        } catch (RuntimeException e) {
            // no level yet: no number
        }
        return -1;
    }

    // ---- Compressor: from stress to cold gas --------------------------------------------------------------------

    public static void compressor(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("compressor", "From Stress to Cold Gas");
        scene.configureBasePlate(0, 0, 5);
        BlockPos compressor = util.grid().at(2, 1, 2);
        Selection drive = util.select().fromTo(2, 1, 3, 2, 1, 4).add(util.select().position(compressor));
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(0, 1, 0, 4, 2, 4), Direction.DOWN);
        scene.idle(15);

        // 1. Rotation in, cold gas out of the front
        scene.world().modifyBlockEntityNBT(util.select().position(compressor), CompressorBlockEntity.class,
                nbt -> fillTankNbt(nbt, "Gas", FwFluids.gas(), value(FwConfig.COMPRESSOR_BUFFER)));
        scene.world().setKineticSpeed(drive, 64);
        scene.world().propagatePipeChange(compressor);
        scene.idle(10);
        scene.overlay().showText(90)
                .text("The Compressor turns rotation into cold gas, which leaves through its front. It needs at least %1$s RPM.",
                        value(FwConfig.COMPRESSOR_MIN_RPM))
                .pointAt(util.vector().blockSurface(compressor, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        // 2. The stress it takes is set on its mark, as a Create value box
        int min = value(FwConfig.STRESS_MIN), max = value(FwConfig.STRESS_MAX), step = value(FwConfig.STRESS_STEP);
        int setting = Math.min(max, 4 * value(FwConfig.STRESS_DEFAULT) / step * step);
        scene.overlay().showControls(util.vector().topOf(compressor), Pointing.DOWN, 60).scroll();
        scene.idle(20);
        scene.world().modifyBlockEntityNBT(util.select().position(compressor), CompressorBlockEntity.class,
                nbt -> nbt.putInt("ScrollValue", setting));
        scene.overlay().showText(100)
                .text("The stress it takes is set on its mark, from %1$s to %2$s su by steps of %3$s. The network sees exactly that, whatever the speed.",
                        // In real su, Greate's scale included (the setting itself stays in its own su)
                        com.frostworks.energy.Energy.su(min * com.frostworks.energy.Energy.stressScale()),
                        com.frostworks.energy.Energy.su(max * com.frostworks.energy.Energy.stressScale()),
                        com.frostworks.energy.Energy.su(step * com.frostworks.energy.Energy.stressScale()))
                .pointAt(util.vector().topOf(compressor))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(110);

        // 3. More stress, more gas
        double kGas = value(FwConfig.GAS_PER_STRESS);
        scene.overlay().showText(90)
                .text("The more it takes, the more gas it makes: %1$s mB per second at %2$s su.", one(setting * kGas),
                        com.frostworks.energy.Energy.su(setting * com.frostworks.energy.Energy.stressScale()))
                .pointAt(util.vector().centerOf(1, 1, 1).add(.5, 0, 0))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);
        scene.markAsFinished();
    }

    // ---- Cryogenic infuser --------------------------------------------------------------------------------------

    public static void infuser(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("cryo_infuser", "Infusing Cold");
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(.8f); // the compressor stands high over the infuser
        BlockPos depot = util.grid().at(2, 1, 2), infuser = util.grid().at(2, 3, 2), compressor = util.grid().at(1, 4, 2);
        Selection drive = util.select().fromTo(2, 3, 2, 2, 3, 4);
        Selection gasSide = util.select().fromTo(1, 3, 2, 1, 6, 2);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().position(depot), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(drive, Direction.DOWN);
        scene.idle(10);

        // 1. Rotation but no gas: nothing happens
        ItemStack kelp = new ItemStack(Items.DRIED_KELP), sheet = AllItems.IRON_SHEET.asStack();
        scene.world().setKineticSpeed(drive, 64);
        scene.world().createItemOnBeltLike(depot, Direction.NORTH, kelp);
        scene.idle(10);
        scene.overlay().showText(80)
                .text("Turning is not enough: without cold gas, the Infuser does not come down.")
                .pointAt(util.vector().blockSurface(infuser, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        // 2. Gas in through its side: it comes down on the item, holds, and the item comes out cold
        scene.world().showSection(gasSide, Direction.DOWN);
        scene.idle(10);
        scene.world().setKineticSpeed(util.select().fromTo(1, 4, 2, 1, 6, 2), 64);
        scene.world().modifyBlockEntityNBT(util.select().position(compressor), CompressorBlockEntity.class,
                nbt -> fillTankNbt(nbt, "Gas", FwFluids.gas(), value(FwConfig.COMPRESSOR_BUFFER)));
        scene.world().propagatePipeChange(compressor);
        scene.world().modifyBlockEntityNBT(util.select().position(infuser), CryoInfuserBlockEntity.class,
                nbt -> fillTankNbt(nbt, "Gas", FwFluids.gas(), value(FwConfig.INFUSER_TANK_CAPACITY)));
        scene.idle(10);
        int dwell = value(FwConfig.INFUSER_DWELL_TICKS);
        scene.overlay().showText(110)
                .text("With rotation and cold gas (into its side), it comes down on the item below and holds it %1$s s: %2$s mB of gas turn Dried Kelp into Chilled Dried Kelp.",
                        one(dwell / 20.0), gasFor(kelp))
                .pointAt(util.vector().topOf(depot))
                .placeNearTarget()
                .attachKeyFrame();
        press(scene, infuser, depot, kelp, new ItemStack(FwItems.COLD_DRIED_KELP.get()), dwell);
        scene.idle(50);

        // 3. The other recipe
        scene.world().removeItemsFromBelt(depot);
        scene.world().createItemOnBeltLike(depot, Direction.NORTH, sheet);
        scene.idle(10);
        scene.overlay().showText(90)
                .text("An Iron Sheet takes %1$s mB and comes out a Cold Iron Sheet.", gasFor(sheet))
                .pointAt(util.vector().topOf(depot))
                .placeNearTarget()
                .attachKeyFrame();
        press(scene, infuser, depot, sheet, new ItemStack(FwItems.COLD_IRON_SHEET.get()), dwell);
        scene.idle(60);

        // 4. Over a basin of water, it freezes ice (as the press compacts: the same cycle, a little
        // deeper, the same frost)
        scene.world().removeItemsFromBelt(depot);
        scene.world().setBlock(depot, com.simibubi.create.AllBlocks.BASIN.getDefaultState(), true);
        scene.idle(10);
        int water = freezingWater();
        scene.world().modifyBlockEntity(depot, BasinBlockEntity.class, basin -> basin.getTanks().getFirst()
                .getPrimaryHandler().fill(new FluidStack(Fluids.WATER, water), IFluidHandler.FluidAction.EXECUTE));
        scene.idle(10);
        scene.overlay().showText(110)
                .text("Over a Basin, it freezes what is in it: %1$s mB of water and %2$s mB of cold gas make one Ice, which comes out of the basin as the press's results do.",
                        water, value(FwConfig.INFUSER_ICE_GAS))
                .pointAt(util.vector().blockSurface(depot, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.world().modifyBlockEntity(infuser, CryoInfuserBlockEntity.class,
                be -> be.getPressingBehaviour().start(Mode.BASIN));
        scene.idle(15 + dwell);
        ItemStack ice = new ItemStack(Items.ICE);
        scene.world().modifyBlockEntity(depot, BasinBlockEntity.class, basin -> {
            basin.getTanks().getFirst().getPrimaryHandler().setFluid(FluidStack.EMPTY);
            basin.getOutputInventory().setStackInSlot(0, ice.copy());
        });
        scene.idle(10);
        scene.overlay().showControls(util.vector().topOf(depot), Pointing.DOWN, 40).withItem(ice);
        scene.idle(70);
        scene.markAsFinished();
    }

    // ---- Chilled ores ----------------------------------------------------------------------------

    /**
     * The tenth scene, on the schematic {@code chilled_ores}: the infuser chills raw iron (gas and Frosium), then the
     * crushing wheels crush it, out of any cold room, in a room on cold gas, in a room on Frosium, the bonus on screen.
     * The crushing is shown as Create's own scene shows it (the item taken, particles, the results); the numbers are the
     * mod's rule ({@code ChilledCrushing.displayed}), from the settings.
     */
    public static void chilledOres(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("chilled_ores", "Chilled Ores");
        scene.configureBasePlate(0, 0, 11);
        scene.scaleSceneView(.75f);
        BlockPos depot = util.grid().at(1, 1, 3), infuser = util.grid().at(1, 3, 3), controller = util.grid().at(6, 3, 3);
        Selection infuserPart = util.select().fromTo(1, 1, 2, 1, 3, 3);
        Selection westDrive = util.select().fromTo(5, 3, 3, 5, 3, 7), eastDrive = util.select().fromTo(7, 3, 3, 7, 3, 7);
        // the room, cut open on the north (the camera's side): its north wall and the first two rows of its roof left
        // out, so that the wheels show whole (the vents are at the back); its shaft passages come with the drive
        Selection room = util.select().fromTo(3, 1, 1, 9, 6, 6).substract(util.select().fromTo(3, 1, 1, 9, 5, 1))
                .substract(util.select().fromTo(3, 2, 2, 9, 5, 2))
                .substract(util.select().fromTo(4, 2, 2, 8, 4, 5)).substract(util.select().position(5, 3, 6))
                .substract(util.select().position(7, 3, 6));
        BlockPos gasVent = util.grid().at(4, 5, 5), frosiumVent = util.grid().at(8, 5, 5);
        // {15 %, 25 % at the gas room's target, that target, 35 % at the Frosium target, that target}
        long[] p = com.frostworks.content.ore.ChilledCrushing.displayed();
        ItemStack raw = new ItemStack(Items.RAW_IRON),
                chilled = com.frostworks.content.cold.ChilledMark.mark(new ItemStack(Items.RAW_IRON)), // Raw iron, chilled
                crushed = com.simibubi.create.AllItems.CRUSHED_IRON.asStack();

        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(infuserPart, Direction.DOWN);
        scene.idle(15);

        // 1. The infuser chills the ore
        int dwell = value(FwConfig.INFUSER_DWELL_TICKS);
        scene.world().modifyBlockEntityNBT(util.select().position(infuser), CryoInfuserBlockEntity.class, nbt -> {
            fillTankNbt(nbt, "Frosium", FwFluids.frosium(), value(FwConfig.INFUSER_FROSIUM_CAPACITY));
        });
        scene.world().setKineticSpeed(infuserPart, 64);
        scene.world().createItemOnBeltLike(depot, Direction.NORTH, raw);
        scene.idle(10);
        int frosium = chillingFrosium();
        scene.overlay().showText(110)
                .text("With %1$s mB of Frosium in its second tank, the Cryogenic Infuser chills an ore: Raw Iron comes out Chilled Raw Iron.",
                        frosium)
                .pointAt(util.vector().topOf(depot))
                .placeNearTarget()
                .attachKeyFrame();
        press(scene, infuser, depot, raw, chilled, dwell);
        scene.idle(60);

        // 2. Crushing wheels, out of any cold room
        scene.world().showSection(westDrive.add(eastDrive), Direction.DOWN);
        scene.idle(15);
        scene.world().setKineticSpeed(westDrive, -64);
        scene.world().setKineticSpeed(eastDrive, 64);
        scene.idle(10);
        scene.overlay().showText(100)
                .text("Create's Crushing Wheels crush it as Raw Iron, and give one more Crushed Raw Iron %1$s %% of the time.",
                        p[0])
                .independent(0)
                .attachKeyFrame();
        crushOnce(scene, util, controller, chilled, crushed);
        bonusLabel(scene, util, controller, p[0], PonderPalette.WHITE);
        scene.idle(70);

        // 3. In a room on cold gas
        scene.world().showSection(room, Direction.DOWN);
        scene.idle(15);
        ColdRoomScenes.vents(scene, true, gasVent);
        scene.overlay().showText(100)
                .text("The colder the room the wheels are in, the more often: %1$s %% in a room on cold gas (%2$s °C)...",
                        p[1], p[2])
                .independent(0)
                .attachKeyFrame();
        crushOnce(scene, util, controller, chilled, crushed);
        bonusLabel(scene, util, controller, p[1], PonderPalette.MEDIUM);
        scene.idle(70);

        // 4. On Frosium
        ColdRoomScenes.vents(scene, true, frosiumVent);
        scene.overlay().showText(100)
                .text("...and %1$s %% on Frosium (%2$s °C).", p[3], p[4])
                .independent(0)
                .attachKeyFrame();
        crushOnce(scene, util, controller, chilled, crushed);
        bonusLabel(scene, util, controller, p[3], PonderPalette.FAST);
        scene.idle(80);
        scene.markAsFinished();
    }

    /** One item in at the top, crushed (Create's scene's way), crushed raw iron out below. */
    private static void crushOnce(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos controller, ItemStack in,
                                  ItemStack out) {
        Vec3 top = util.vector().topOf(controller.above());
        var entity = scene.world().createItemEntity(top, util.vector().of(0, 0.2, 0), in);
        scene.idle(18);
        scene.world().modifyEntity(entity, net.minecraft.world.entity.Entity::discard);
        scene.effects().emitParticles(util.vector().centerOf(controller).add(0, -0.2, 0),
                scene.effects().particleEmitterWithinBlockSpace(new net.minecraft.core.particles.ItemParticleOption(
                        net.minecraft.core.particles.ParticleTypes.ITEM, in), util.vector().of(0, 0, 0)), 3, 30);
        scene.idle(30);
        scene.world().createItemEntity(util.vector().centerOf(controller.below()), util.vector().of(0, 0, 0), out);
    }

    /** The bonus, by the wheels, in Create's speed colours. */
    private static void bonusLabel(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos controller, double percent,
                                   PonderPalette colour) {
        scene.overlay().showText(70)
                .colored(colour)
                .sharedText("bonus", one(percent))
                .pointAt(util.vector().blockSurface(controller.below(), Direction.NORTH))
                .placeNearTarget();
    }

    /** The Frosium that chills raw iron (from its recipe and the settings; 100 mB if none is found). */
    private static int chillingFrosium() {
        try {
            for (var holder : Minecraft.getInstance().level.getRecipeManager().getAllRecipesFor(FwRecipeTypes.INFUSING
                    .<net.minecraft.world.item.crafting.SingleRecipeInput, InfusingRecipe>getType()))
                if (holder.value().getIngredients().get(0).test(new ItemStack(Items.RAW_IRON)))
                    return holder.value().frosiumAmount();
        } catch (RuntimeException e) {
            // no level yet
        }
        return 100;
    }

    /** Waits {@code ticks}, the ice that reaches the end of the belt going into the funnel (Ponder moves no item into it). */
    private static void iceIntoFunnel(CreateSceneBuilder scene, SceneBuildingUtil util, int ticks) {
        for (int t = 0; t < ticks; t += 5) {
            scene.idle(5);
            scene.world().removeItemsFromBelt(util.grid().at(5, 0, 4));
        }
    }

    /** The water of the freezing recipe that makes ice (read from the recipes; 1000 mB if none is found). */
    private static int freezingWater() {
        try {
            for (var holder : Minecraft.getInstance().level.getRecipeManager().getAllRecipesFor(FwRecipeTypes.FREEZING
                    .<net.minecraft.world.item.crafting.RecipeInput, com.frostworks.content.infuser.FreezingRecipe>getType()))
                if (!holder.value().getFluidIngredients().isEmpty())
                    return holder.value().getFluidIngredients().get(0).amount();
        } catch (RuntimeException e) {
            // no level yet
        }
        return 1000;
    }

    /** The infuser comes down, holds for its dwell (its own client side: the arm and the frost), and the item changes. */
    private static void press(CreateSceneBuilder scene, BlockPos infuser, BlockPos depot, ItemStack in, ItemStack out,
                              int dwell) {
        scene.world().modifyBlockEntity(infuser, CryoInfuserBlockEntity.class,
                be -> be.getPressingBehaviour().start(Mode.BELT));
        // down in about 15 ticks at 64 rpm, then the dwell
        scene.idle(15 + dwell);
        scene.world().removeItemsFromBelt(depot);
        scene.world().createItemOnBeltLike(depot, Direction.UP, out);
        scene.idle(10);
        scene.overlay().showControls(onDepot(depot), Pointing.UP, 40).withItem(out);
    }

    /** Just above an item lying on a depot. */
    private static Vec3 onDepot(BlockPos pos) {
        return Vec3.atCenterOf(pos).add(0, .4, 0);
    }

    // ---- Cryogenic tower: making Frosium -------------------------------------------------------------------

    public static void tower(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("cryogenic_tower", "Making Frosium");
        scene.configureBasePlate(0, 0, 8);
        scene.scaleSceneView(.85f);
        BlockPos controller = util.grid().at(3, 1, 3), tank = util.grid().at(7, 1, 3);
        // The column is first a stack of frost tanks, as the player sets them down
        var frostTank = FwBlocks.RESERVOIR.get().defaultBlockState();
        Selection column = util.select().fromTo(3, 2, 3, 4, 4, 4);
        scene.world().setBlocks(column, frostTank, false);
        // the ice belt is sunk in the plate: snow until its step
        Selection iceBelt = util.select().fromTo(5, 0, 4, 7, 0, 4);
        scene.world().setBlocks(iceBelt, net.minecraft.world.level.block.Blocks.SNOW_BLOCK.defaultBlockState(), false);
        scene.showBasePlate();
        scene.idle(10);

        // 1. The base: 2 x 2, each block takes its corner shape
        scene.world().showSection(util.select().fromTo(3, 1, 3, 4, 1, 4), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(80)
                .text("The tower stands on a base of 2 x 2 Column Bases; each one takes the shape of its corner.")
                .pointAt(util.vector().blockSurface(controller, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        // 2. Frost tanks set 2 x 2 on the base: once a layer is whole, it becomes a layer of the column (as in game:
        // ColumnLayers converts the four tanks when the fourth one is set)
        int[][] corners = {{3, 3}, {4, 3}, {3, 4}, {4, 4}};
        for (int y = 2; y <= 4; y++) {
            for (int[] c : corners) {
                scene.world().showSection(util.select().position(c[0], y, c[1]), Direction.DOWN);
                scene.idle(4);
            }
            scene.idle(8);
            scene.world().restoreBlocks(util.select().fromTo(3, y, 3, 4, y, 4));
            scene.idle(10);
        }
        scene.overlay().showText(110)
                .text("Frost Tanks set 2 x 2 on the base become the column, up to %1$s layers with the base. Each layer handles %2$s mB of gas a second at %3$s RPM; each column block holds %4$s mB, the base nothing.",
                        value(FwConfig.TOWER_MAX_LAYERS), value(FwConfig.TOWER_GAS_PER_LAYER),
                        value(FwConfig.TOWER_REFERENCE_RPM), value(FwConfig.TOWER_CAPACITY_PER_BLOCK))
                .pointAt(util.vector().blockSurface(util.grid().at(3, 3, 3), Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(120);

        // 3. In: rotation on a side of the base, cold gas and water through the pipes of the base; they rise in the
        // column (Create's fluid rendering of the tower, from its content)
        Selection rotation = util.select().fromTo(1, 1, 4, 2, 1, 4);
        Selection gas = util.select().fromTo(0, 1, 2, 3, 1, 2);
        Selection water = util.select().fromTo(2, 1, 5, 3, 1, 7);
        scene.world().showSection(rotation, Direction.EAST);
        scene.world().showSection(gas, Direction.SOUTH);
        scene.world().showSection(water, Direction.NORTH);
        scene.idle(15);
        scene.world().setKineticSpeed(rotation, value(FwConfig.TOWER_REFERENCE_RPM));
        scene.world().setKineticSpeed(util.select().fromTo(0, 1, 2, 2, 1, 2), 64);
        scene.world().setKineticSpeed(util.select().fromTo(2, 1, 6, 3, 1, 7), 32);
        scene.overlay().showText(100)
                .text("In: rotation on a side of the base, cold gas and water through pipes on the base (pumped: the tower pulls nothing).")
                .pointAt(util.vector().blockSurface(util.grid().at(2, 1, 4), Direction.NORTH))
                .placeNearTarget()
                .attachKeyFrame();
        int room = 4 * 3 * value(FwConfig.TOWER_CAPACITY_PER_BLOCK);
        double gasShare = value(FwConfig.TOWER_GAS_SHARE);
        int gasFull = (int) (room * gasShare * .7), waterFull = (int) (room * (1 - gasShare) * .6);
        for (int i = 1; i <= 20; i++) {
            int g = gasFull * i / 20, w = waterFull * i / 20;
            tankContent(scene, util, controller, g, w, 0);
            scene.idle(5);
        }
        scene.idle(10);

        // 3b. The ice, by a belt one step lower into a funnel on the east side of the base (any side: funnel or
        // mechanical arm; a belt alone does not go in); the tower stops without it
        // the camera turns to that side for the ice, then back for the Frosium
        scene.rotateCameraY(90);
        scene.idle(20);
        BlockPos funnel = util.grid().at(5, 1, 4);
        scene.world().restoreBlocks(iceBelt);
        scene.world().showSection(util.select().position(funnel), Direction.DOWN);
        scene.idle(15);
        scene.world().setKineticSpeed(iceBelt, 32);
        for (int i = 0; i < 3; i++) {
            scene.world().createItemOnBelt(util.grid().at(7, 0, 4), Direction.EAST, new ItemStack(Items.PACKED_ICE));
            if (i == 0)
                scene.overlay().showText(110)
                        .text("The tower also needs ice, the matter its Frosium is made of: through a side of the base, by funnel or mechanical arm. A Packed Ice gives %1$s mB; plain Ice counts for a ninth, Blue Ice for nine.",
                                value(FwConfig.TOWER_FROSIUM_PER_ICE))
                        .pointAt(util.vector().blockSurface(funnel, Direction.EAST))
                        .placeNearTarget()
                        .attachKeyFrame();
            iceIntoFunnel(scene, util, 30);
        }
        iceIntoFunnel(scene, util, 60);
        scene.overlay().showText(80)
                .text("Without ice, the tower stops (nothing breaks); its goggles say so, with the ice left.")
                .pointAt(util.vector().blockSurface(controller, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);
        scene.rotateCameraY(-90);
        scene.idle(20);

        // 4. Out: Frosium appears at the bottom, is drawn from the base by a pump into a frost tank
        Selection out = util.select().fromTo(5, 1, 3, 7, 2, 3);
        scene.world().showSection(out, Direction.WEST);
        scene.idle(15);
        scene.world().setKineticSpeed(util.select().fromTo(5, 2, 3, 6, 2, 3), 32);
        scene.world().setKineticSpeed(util.select().position(5, 1, 3), -32);
        int frosiumMax = room / 5, capacity = value(FwConfig.RESERVOIR_CAPACITY);
        for (int i = 1; i <= 20; i++) {
            tankContent(scene, util, controller, gasFull - gasFull * i / 60, waterFull - waterFull * i / 60,
                    frosiumMax * Math.min(i, 10) / 10);
            if (i > 6) // the pump draws it off into the frost tank
                scene.world().modifyBlockEntity(tank, FluidTankBlockEntity.class, t -> t.getTankInventory()
                        .fill(new FluidStack(FwFluids.frosium(), capacity / 14), IFluidHandler.FluidAction.EXECUTE));
            if (i == 6)
                scene.overlay().showText(110)
                        .text("Out: Frosium, drawn from the base by a pump, into a Frost Tank.")
                        .pointAt(util.vector().topOf(tank))
                        .placeNearTarget()
                        .attachKeyFrame();
            scene.idle(6);
        }
        scene.idle(60);
        scene.markAsFinished();
    }

    /** The content of the tower, written on its controller (its renderer draws it through the column windows). */
    private static void tankContent(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos controller, int gas,
                                    int water, int frosium) {
        scene.world().modifyBlockEntityNBT(util.select().position(controller), TowerBlockEntity.class, nbt -> {
            nbt.putInt("Gas", gas);
            nbt.putInt("Water", water);
            nbt.putString("OutputFluid", "frostworks:frosium"); // the tower's output, as it saves it
            nbt.putInt("Output", frosium);
        });
    }

    // ---- Frost tank -------------------------------------------------------------------------------------------

    public static void frostTank(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("frost_tank", "The Frost Tank");
        scene.configureBasePlate(0, 0, 5);
        BlockPos tank = util.grid().at(2, 1, 2);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(2, 1, 2, 2, 2, 2), Direction.DOWN);
        scene.idle(15);

        // 1. Frosium only, with its capacity
        int perBlock = value(FwConfig.RESERVOIR_CAPACITY), total = 2 * perBlock;
        scene.overlay().showText(90)
                .text("The Frost Tank holds cold liquids: Frosium, and any liquid tagged frostworks:cold_liquid. %1$s mB a block, %2$s here.",
                        perBlock, total)
                .pointAt(util.vector().blockSurface(util.grid().at(2, 2, 2), Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        // 2. It fills
        // fill, as Create's tank scenes do: it moves the level the renderer draws (setFluid does not)
        for (int i = 1; i <= 20; i++) {
            scene.world().modifyBlockEntity(tank, FluidTankBlockEntity.class, t -> t.getTankInventory()
                    .fill(new FluidStack(FwFluids.frosium(), total / 20), IFluidHandler.FluidAction.EXECUTE));
            scene.idle(5);
        }
        scene.idle(10);

        // 3. Anything else stays out; Create's tanks refuse Frosium
        scene.overlay().showControls(util.vector().blockSurface(util.grid().at(2, 2, 2), Direction.NORTH), Pointing.RIGHT,
                60).rightClick().withItem(new ItemStack(Items.WATER_BUCKET));
        scene.overlay().showOutline(PonderPalette.RED, "refused", util.select().fromTo(2, 1, 2, 2, 2, 2), 60);
        scene.idle(10);
        scene.overlay().showText(90)
                .text("Any other fluid stays out. Create's Fluid Tanks, for their part, refuse Frosium.")
                .pointAt(util.vector().topOf(util.grid().at(2, 2, 2)))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);
        scene.markAsFinished();
    }

    // ---- Expander: giving rotation back -----------------------------------------------------------------------

    public static void expander(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("expander", "Giving Rotation Back");
        scene.configureBasePlate(0, 0, 5);
        BlockPos expander = util.grid().at(2, 1, 2), pump = util.grid().at(3, 1, 2);
        Selection network = util.select().fromTo(2, 1, 0, 2, 1, 2);
        Selection pumpDrive = util.select().fromTo(3, 2, 2, 4, 2, 2);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(0, 1, 0, 4, 2, 4), Direction.DOWN);
        scene.idle(15);

        double rate = value(FwConfig.EXPANDER_LIQUID_RATE), kLiquid = value(FwConfig.LIQUID_PER_ENERGY),
                kGas = value(FwConfig.GAS_PER_STRESS) / com.frostworks.energy.Energy.stressScale();
        // Greate's scale: the rotation given back divided by it, the same gas (kGas above)
        double power = rate / kLiquid * com.frostworks.energy.Energy.stressScale();

        // 1. Frosium in through its sides
        scene.world().setKineticSpeed(pumpDrive, 32);
        scene.world().setKineticSpeed(util.select().position(pump), -32);
        scene.world().propagatePipeChange(pump);
        scene.world().setKineticSpeed(network, 32);
        scene.overlay().showText(90)
                .text("The Expander takes Frosium in at its back: %1$s mB a second. The cold gas leaves at its front.", one(rate))
                .pointAt(util.vector().topOf(pump))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        // 2. The rotation it gives back, on the network that already turns
        GogglesDisplay goggles = new GogglesDisplay(0, 0, 0, 0);
        goggles.showLines(scene, () -> expanderLines(100, power, kGas));
        scene.overlay().showText(110)
                .text("It turns nothing by itself: it adds %1$s su of capacity, through the shaft across it, to the network that already turns (a Hand Crank here), at any speed.",
                        one(power))
                .pointAt(util.vector().topOf(util.grid().at(2, 1, 1)))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(120);

        // 3. The share given as rotation is set on it; the rest leaves the front as cold gas
        int share = 50;
        scene.overlay().showControls(util.vector().topOf(expander), Pointing.DOWN, 60).scroll();
        scene.idle(20);
        scene.world().modifyBlockEntityNBT(util.select().position(expander), ExpanderBlockEntity.class, nbt -> {
            nbt.putInt("ScrollValue", share);
            fillTankNbt(nbt, "Gas", FwFluids.gas(), value(FwConfig.EXPANDER_BUFFER));
        });
        scene.world().propagatePipeChange(expander);
        goggles.setLines(scene, () -> expanderLines(share, power, kGas));
        scene.overlay().showText(110)
                .text("The share given as rotation is set on it, 0 to 100 %%; the rest leaves the front as cold gas: %1$s su and %2$s mB a second at %3$s %%.",
                        one(share / 100.0 * power), one((1 - share / 100.0) * power * kGas), share)
                .pointAt(util.vector().blockSurface(util.grid().at(1, 1, 2), Direction.NORTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(120);
        scene.markAsFinished();
    }

    /** The expander's goggles while it runs, as {@code ExpanderBlockEntity} writes them. */
    private static List<MutableComponent> expanderLines(int share, double power, double kGas) {
        double capacity = share / 100.0 * power;
        List<MutableComponent> lines = new ArrayList<>();
        lines.add(Component.translatable("create.gui.goggles.generator_stats"));
        lines.add(Component.translatable("create.tooltip.capacityProvided").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal(" ").append(Component.literal(one(capacity) + " ")
                        .append(Component.translatable("create.generic.unit.stress")).withStyle(ChatFormatting.AQUA))
                .append(" ").append(Component.translatable("create.gui.goggles.at_current_speed")
                        .withStyle(ChatFormatting.DARK_GRAY)));
        lines.add(Component.translatable("frostworks.goggles.expander"));
        lines.add(Component.literal(" ").append(Component.translatable("frostworks.goggles.expander.running")
                .withStyle(ChatFormatting.AQUA)));
        lines.add(Component.literal(" ").append(Component.translatable("frostworks.goggles.expander.split", share,
                String.format(Locale.ROOT, "%.0f", capacity),
                String.format(Locale.ROOT, "%.1f", (1 - share / 100.0) * power * kGas)).withStyle(ChatFormatting.GRAY)));
        return lines;
    }

    // ---- Cold vent ----------------------------------------------------------------------------------------------

    public static void vent(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("cold_vent", "The Cold Vent");
        scene.configureBasePlate(0, 0, 7);
        BlockPos vent = util.grid().at(3, 2, 5);
        // The room open toward the camera: north wall and roof left out
        Selection room = util.select().fromTo(1, 1, 1, 5, 3, 5).substract(util.select().fromTo(2, 2, 1, 4, 3, 1))
                .substract(util.select().fromTo(2, 2, 2, 4, 3, 4));
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(room.add(util.select().position(3, 2, 6)), Direction.DOWN);
        scene.idle(15);

        // 1. Blowing: the vent is on
        int volume = 3 * 2 * 3, gas = ColdRoomScenes.gas().coverage(),
                frosium = ColdRoomScenes.frosium().coverage();
        int buffer = value(FwConfig.VENT_BUFFER);
        ColdRoomScenes.vents(scene, true, vent);
        GogglesDisplay goggles = new GogglesDisplay(0, 0, 0, 0);
        goggles.showLines(scene, () -> ventLines(false, gas, buffer, volume));
        scene.overlay().showText(90)
                .text("When cold air comes out, the vent is on: it cools the closed room it blows into.")
                .pointAt(util.vector().blockSurface(vent, Direction.NORTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        // 2. What one vent covers, by fluid
        goggles.setLines(scene, () -> ventLines(true, frosium, buffer, volume));
        scene.overlay().showText(110)
                .text("A vent covers %1$s cells with cold gas, %2$s with Frosium.", gas, frosium)
                .pointAt(util.vector().blockSurface(vent, Direction.NORTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        // 3. The total of the vents against the volume of the room
        scene.overlay().showText(100)
                .text("The room is cold when the total capacity of its vents reaches its volume: %1$s cells here, for %2$s.",
                        frosium, volume)
                .pointAt(util.vector().topOf(util.grid().at(3, 1, 3)))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(110);
        scene.markAsFinished();
    }

    /** The vent's goggles (and its room's), the real lines of {@link RoomText}. */
    private static List<MutableComponent> ventLines(boolean frosium, int coverage, int buffer, int volume) {
        com.frostworks.content.cold.ColdFluid cold = frosium ? ColdRoomScenes.frosium() : ColdRoomScenes.gas();
        String fluid = (frosium ? FwFluids.frosium() : FwFluids.gas()).getFluidType().getDescriptionId();
        RoomInfoPayload.VentView vent = new RoomInfoPayload.VentView(true, buffer, buffer, 1, false, coverage,
                cold.temperature(), fluid);
        int rpm = ColdRoomScenes.coldLimit(frosium);
        float target = cold.temperature();
        RoomInfoPayload.RoomView room = new RoomInfoPayload.RoomView(1, target, volume, coverage, RoomStatus.COLD, 1,
                1, ColdRoomScenes.gas().coverage(), value(FwConfig.ROOM_SEARCH_CAP), false, rpm,
                ColdRoomScenes.createLimit());
        return RoomText.lines(new RoomInfoPayload(BlockPos.ZERO, List.of(room), vent));
    }
}
