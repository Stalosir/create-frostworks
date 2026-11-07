package com.frostworks.client.ponder;

import com.frostworks.Frostworks;
import com.frostworks.FwConfig;
import com.frostworks.content.cold.ColdFluid;
import com.frostworks.content.cold.ColdFluids;
import com.frostworks.content.compressor.CompressorBlockEntity;
import com.frostworks.content.door.StripDoorBlockEntity;
import com.frostworks.content.vent.VentBlockEntity;
import com.frostworks.overspeed.ColdStress;
import com.frostworks.overspeed.Overspeed;
import com.frostworks.registry.FwBlocks;
import com.frostworks.registry.FwFluids;
import com.frostworks.room.core.RoomStatus;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The two scenes of the cold room, on the schematic {@code cold_room} (built and saved by the pilot,
 * {@code ColdRoomSchematic}). The Ponder world runs no room code: what the room does (cold, speed limit) is told by the
 * scene, through {@link GogglesDisplay}. Every number comes from the config or the code.
 * <p>
 * Schematic (see {@code ColdRoomSchematic}): snow plate 11 x 11, cold walls x 3..8, y 1..6, z 3..8 (inside 4 x 4 x 4 =
 * 64 cells); three gas vents (4..6,6,6) and a Frosium vent (4,6,4) in the roof; compressor
 * (1,1,7), frost tank (1,1,4) and its pump (1,2,4), all on one side; door (3,2..3,5); through simple holes a shaft
 * (8,2,4), a pipe (8,3,7), a belt (4,2,8); inside, in the front row, a motor and two cogwheels driving x2 a mixer two
 * blocks above its basin.
 */
public final class ColdRoomScenes {
    private ColdRoomScenes() {}

    // ---- The schematic ------------------------------------------------------------------------------------------

    /** The roof one block higher (inside 4 x 4 x 4), the mixer two blocks above its basin. */
    static final int ROOF = 6;
    static final BlockPos GAS_VENT_1 = new BlockPos(4, ROOF, 6), GAS_VENT_2 = new BlockPos(5, ROOF, 6),
            GAS_VENT_3 = new BlockPos(6, ROOF, 6), FROSIUM_VENT = new BlockPos(4, ROOF, 4), DOOR = new BlockPos(3, 2, 5),
            SHAFT = new BlockPos(8, 2, 4),
            PIPE = new BlockPos(8, 3, 7), BELT = new BlockPos(4, 2, 8), COMPRESSOR = new BlockPos(1, 1, 7),
            TANK = new BlockPos(1, 1, 4), PUMP = new BlockPos(1, 2, 4), LARGE_COG = new BlockPos(7, 5, 4),
            INSIDE_MOTOR = new BlockPos(7, 4, 4), SMALL_COG = new BlockPos(6, 5, 5), MIXER = new BlockPos(6, 4, 5);
    static final int VOLUME = 4 * 4 * 4;

    private static Selection walls(SceneBuildingUtil util, int y) {
        Selection layer = util.select().fromTo(3, y, 3, 8, y, 8);
        return y == 1 || y == ROOF ? layer : layer.substract(util.select().fromTo(4, y, 4, 7, y, 7));
    }

    private static Selection northWall(SceneBuildingUtil util) {
        return util.select().fromTo(3, 2, 3, 8, ROOF - 1, 3);
    }

    private static Selection westWall(SceneBuildingUtil util) {
        return util.select().fromTo(3, 2, 4, 3, ROOF - 1, 8);
    }

    /** The roof and the pipe over its two gas vents (scene 1 shows no more of the gas line). */
    private static Selection roof(SceneBuildingUtil util) {
        return util.select().fromTo(3, ROOF, 3, 8, ROOF, 8)
                .add(util.select().fromTo(4, ROOF + 1, 6, 6, ROOF + 1, 6));
    }

    static Selection cells(SceneBuildingUtil util, BlockPos... positions) {
        Selection s = util.select().position(positions[0]);
        for (int i = 1; i < positions.length; i++)
            s = s.add(util.select().position(positions[i]));
        return s;
    }

    // ---- Values read from the config and the code ----------------------------------------------------------------

    static int value(ModConfigSpec.IntValue v) {
        try {
            return FwConfig.get(v);
        } catch (RuntimeException e) {
            return v.getDefault();
        }
    }

    static float value(ModConfigSpec.DoubleValue v) {
        try {
            return (float) (double) FwConfig.get(v);
        } catch (RuntimeException e) {
            return (float) (double) v.getDefault();
        }
    }

    static int createLimit() {
        try {
            return Overspeed.createLimit();
        } catch (RuntimeException e) {
            return 256;
        }
    }

    /** Create's limit raised by the cold bonus, as {@link Overspeed#coldLimit} (the deeper one on Frosium). */
    static int coldLimit(boolean frosium) {
        int bonus = value(frosium ? FwConfig.OVERSPEED_BONUS_FROSIUM : FwConfig.OVERSPEED_BONUS);
        return Math.round(createLimit() * (100 + bonus) / 100f);
    }

    /** The cold gas recipe as the client has it (its 1.1 values before a world is open). */
    static ColdFluid gas() {
        return ColdFluids.shown(FwFluids.gas(), new ColdFluid(Frostworks.id("cold_gas"), FwFluids.gas(), null, 15, -10, 24));
    }

    /** The Frosium recipe as the client has it (its 1.1 values before a world is open). */
    static ColdFluid frosium() {
        return ColdFluids.shown(FwFluids.frosium(),
                new ColdFluid(Frostworks.id("frosium"), FwFluids.frosium(), null, 4, -20, 64));
    }

    /** Degrees per scene tick: the scene runs the real 0.5 deg C per second faster, to stay short. */
    static final float SCENE_RATE = 0.5f;

    static void vents(CreateSceneBuilder scene, boolean blowing, BlockPos... vents) {
        for (BlockPos vent : vents)
            scene.world().modifyBlockEntity(vent, VentBlockEntity.class, v -> v.showInScene(blowing, blowing));
    }

    private static GogglesDisplay display() {
        return new GogglesDisplay(VOLUME, gas().coverage(), createLimit(), value(FwConfig.ROOM_SEARCH_CAP));
    }

    /** The room with a hole in a wall: OPEN, it warms up again, Create's limit. */
    private static GogglesDisplay.State open() {
        return new GogglesDisplay.State(RoomStatus.OPEN, 3, 3, 3 * gas().coverage(), createLimit(),
                value(FwConfig.AMBIENT_TEMPERATURE));
    }

    /**
     * The room as the mod would see it, from its active vents: COLD when their capacity covers the volume, at the
     * target of its warmest cold fluid: Frosium's only without gas (the rules of {@code RoomManager}).
     */
    private static GogglesDisplay.State room(int gasVents, int frosiumVents, int vents) {
        int gas = gasVents * gas().coverage();
        int frosium = frosiumVents * frosium().coverage();
        boolean cold = gas + frosium >= VOLUME, alone = cold && gasVents == 0 && frosiumVents > 0;
        return new GogglesDisplay.State(cold ? RoomStatus.COLD : RoomStatus.INSUFFICIENT, gasVents + frosiumVents,
                vents, gas + frosium,
                !cold ? createLimit() : coldLimit(alone),
                !cold ? value(FwConfig.AMBIENT_TEMPERATURE)
                        : (alone ? frosium() : gas()).temperature());
    }

    // ---- Scene 1: building a cold room ----------------------------------------------------------------------------

    public static void build(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("cold_room_build", "Building a Cold Room");
        scene.configureBasePlate(0, 0, 11);
        scene.scaleSceneView(.7f);
        scene.setSceneOffsetY(-1); // The room is one block higher
        var wall = FwBlocks.COLD_WALL.get().defaultBlockState();
        // A closed box of walls first: vents, door and holes come later
        Selection later = cells(util, GAS_VENT_1, GAS_VENT_2, GAS_VENT_3, FROSIUM_VENT, DOOR, DOOR.above(), SHAFT,
                PIPE, BELT);
        scene.world().setBlocks(later, wall, false);
        scene.showBasePlate();
        scene.idle(10);

        // 1. The walls, layer by layer
        for (int y = 1; y <= ROOF; y++) {
            scene.world().showSection(walls(util, y), Direction.DOWN);
            scene.idle(5);
        }
        scene.idle(10);
        scene.overlay().showText(80)
                .text("Cold Walls connect to each other. They close off the room you want to keep cold.")
                .pointAt(util.vector().blockSurface(new BlockPos(3, 3, 6), Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        // 2. The vents in the roof, fed from behind by a pipe
        Selection gasVents = cells(util, GAS_VENT_1, GAS_VENT_2, GAS_VENT_3);
        scene.world().restoreBlocks(gasVents);
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(4, ROOF + 1, 6, 6, ROOF + 1, 6), Direction.DOWN);
        vents(scene, true, GAS_VENT_1, GAS_VENT_2, GAS_VENT_3);
        scene.idle(10);
        scene.overlay().showText(80)
                .text("A Cold Vent takes the place of a wall. It spreads into the room the cold that reaches it through its pipe.")
                .pointAt(util.vector().topOf(GAS_VENT_1))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        // 3. What the goggles say. The display comes in once (it stays to the end) and the text
        // waits for the room to reach its temperature, so that the numbers agree
        float ambient = value(FwConfig.AMBIENT_TEMPERATURE), gasTarget = gas().temperature();
        GogglesDisplay goggles = display();
        goggles.show(scene, room(3, 0, 3), ambient);
        goggles.set(scene, room(3, 0, 3), SCENE_RATE);
        scene.idle(ticksBetween(ambient, gasTarget) + 5);
        scene.overlay().showText(90)
                .text("The room is cold when the capacity of its vents covers its volume. Engineer's Goggles, on a wall, tell where it stands.")
                .pointAt(util.vector().blockSurface(new BlockPos(3, 2, 7), Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        // 4. The door. The roof and the north wall open (the door's wall stays), the camera turns
        // with the villager, who is seen coming, going through the door and standing in the room
        scene.world().restoreBlocks(cells(util, DOOR, DOOR.above()));
        scene.idle(10);
        scene.world().hideSection(roof(util), Direction.UP);
        scene.world().hideSection(northWall(util), Direction.NORTH);
        scene.idle(15);
        ElementLink<EntityElement> villager = scene.world().createEntity(level -> {
            Villager v = EntityType.VILLAGER.create(level);
            v.setPos(0.5, 1, 5.5);
            v.xo = 0.5;
            v.yo = 1;
            v.zo = 5.5;
            v.setYRot(-90);
            v.yRotO = -90;
            v.yHeadRot = v.yHeadRotO = -90;
            v.yBodyRot = v.yBodyRotO = -90;
            return v;
        });
        scene.overlay().showText(100)
                .text("The Strip Curtain Door counts as a wall, yet you walk through it: players, villagers, animals. The cold does not get out.")
                .independent(0)
                .attachKeyFrame();
        // The camera turns toward the open north side, to see into the room as the villager comes in
        scene.rotateCameraY(30);
        for (int i = 1; i <= 60; i++) {
            double x = 0.5 + i * 0.08;
            // a hop onto the floor of the room, in front of the door
            double y = x < 2.2 ? 1 : x < 2.8 ? 1 + (x - 2.2) / 0.6 + 0.25 * Math.sin(Math.PI * (x - 2.2) / 0.6) : 2;
            scene.world().modifyEntity(villager, e -> {
                e.xo = e.getX();
                e.yo = e.getY();
                e.zo = e.getZ();
                e.setPos(x, y, 5.5);
                if (e instanceof Villager v)
                    v.walkAnimation.update(0.6f, 0.4f);
            });
            if (i == 30) // in the frame: the strips swing
                scene.world().modifyBlockEntity(DOOR, StripDoorBlockEntity.class,
                        d -> d.triggerEvent(StripDoorBlockEntity.EVENT_SWING, 1 | 15 << 1));
            scene.idle(1);
        }
        scene.idle(40);
        settleCamera(scene);

        // 5. Cut away: the west wall opens too, the camera turns; a shaft, a pipe and a belt go through simple holes
        scene.world().hideSection(westWall(util), Direction.WEST);
        scene.idle(10);
        scene.rotateCameraY(-10);
        scene.idle(TURN_TICKS);
        settleCamera(scene);
        Selection holes = cells(util, SHAFT, PIPE, BELT);
        scene.world().restoreBlocks(holes);
        scene.world().showSection(util.select().fromTo(7, 2, 4, 9, 2, 4).substract(util.select().position(SHAFT)),
                Direction.WEST);
        scene.world().showSection(util.select().fromTo(7, 3, 7, 9, 3, 7).substract(util.select().position(PIPE)),
                Direction.WEST);
        scene.world().showSection(util.select().fromTo(4, 2, 6, 4, 2, 10).substract(util.select().position(BELT)),
                Direction.NORTH);
        Selection kinetics = util.select().fromTo(7, 2, 4, 9, 2, 4).add(util.select().fromTo(4, 2, 6, 4, 2, 10));
        scene.world().setKineticSpeed(kinetics, 32);
        goggles.set(scene, open(), SCENE_RATE);
        scene.idle(15);
        scene.overlay().showText(70)
                .text("A hole in a wall opens the room: the cold escapes.")
                .pointAt(util.vector().centerOf(SHAFT))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        // 6. The passages: a right click with a Cold Wall on each
        ItemStack coldWall = new ItemStack(FwBlocks.COLD_WALL.get());
        scene.overlay().showText(140)
                .text("To take a shaft, a pipe or a belt through a wall, right-click it with a Cold Wall. The passage counts as a wall: the room stays closed.")
                .independent(0) // no pointer: at the top, over the right-clicks (6 lines in French)
                .attachKeyFrame();
        scene.idle(20);
        BlockPos[] passages = {SHAFT, PIPE, BELT};
        for (BlockPos pos : passages) {
            scene.overlay().showControls(util.vector().centerOf(pos), Pointing.RIGHT, 30).rightClick()
                    .withItem(coldWall);
            scene.idle(20);
            if (pos == SHAFT) {
                scene.world().setBlock(pos, FwBlocks.SHAFT_PASSAGE.get().defaultBlockState()
                        .setValue(BlockStateProperties.AXIS, Direction.Axis.X), true);
                scene.world().setKineticSpeed(util.select().position(pos), 32);
            } else if (pos == PIPE) {
                scene.world().setBlock(pos, FwBlocks.PIPE_PASSAGE.get().defaultBlockState()
                        .setValue(BlockStateProperties.AXIS, Direction.Axis.X), true);
            } else {
                scene.world().modifyBlock(pos, s -> s.setValue(BeltBlock.CASING, true), true);
                scene.world().modifyBlockEntityNBT(util.select().position(pos), BeltBlockEntity.class,
                        nbt -> {
                            nbt.putString("Casing", BeltBlockEntity.CasingType.ANDESITE.name());
                            // The passage mark too, as the right click puts it in game: our habit, not Create's casing
                            CompoundTag own = nbt.getCompound("NeoForgeData");
                            own.putBoolean(com.frostworks.content.passage.BeltPassage.KEY, true);
                            nbt.put("NeoForgeData", own);
                        }, true);
            }
            scene.idle(25);
        }
        // Closed again: cold again
        goggles.set(scene, room(3, 0, 3), SCENE_RATE);
        scene.idle(30);

        // 7. The walls come back: the room is closed
        scene.world().modifyEntity(villager, e -> e.discard());
        scene.rotateCameraY(-20);
        scene.world().showSection(northWall(util), Direction.SOUTH);
        scene.world().showSection(westWall(util), Direction.EAST);
        scene.world().showSection(roof(util), Direction.DOWN);
        scene.idle(TURN_TICKS);
        settleCamera(scene);
        scene.overlay().showText(90)
                .text("Walls, vents, door and passages: that is all a cold room needs.")
                .pointAt(util.vector().topOf(new BlockPos(5, ROOF, 5)))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);
        scene.markAsFinished();
    }

    // ---- Scene 2: faster in a cold room ---------------------------------------------------------------------------

    /**
     * The mechanism is the subject. The room is cut open (north and west walls left out); the roof
     * and the pipes over it come in only while the scene talks about the cold, and go away when it talks about the
     * gears, whose speeds are labelled (motor, machine) in Create's colours.
     */
    public static void speed(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("cold_room_speed", "Going Faster in a Cold Room");
        scene.configureBasePlate(0, 0, 11);
        scene.scaleSceneView(.7f);
        scene.setSceneOffsetY(-1); // The room is one block higher
        var wall = FwBlocks.COLD_WALL.get().defaultBlockState();
        // A closed room: no holes in this one, the Frosium vent comes later
        scene.world().setBlocks(cells(util, SHAFT, PIPE, BELT, FROSIUM_VENT), wall, false);
        int limit = createLimit(), cold = coldLimit(false), frosium = coldLimit(true);
        float ambient = value(FwConfig.AMBIENT_TEMPERATURE), gasTarget = gas().temperature(),
                frosiumTarget = frosium().temperature();

        Selection walls = util.select().fromTo(3, 1, 3, 8, ROOF - 1, 8).substract(northWall(util))
                .substract(westWall(util))
                .substract(util.select().fromTo(4, 2, 4, 7, ROOF - 1, 7));
        Selection roof = util.select().fromTo(3, ROOF, 3, 8, ROOF, 8)
                .add(util.select().fromTo(2, ROOF + 1, 6, 6, ROOF + 1, 6));
        Selection compressorDrive = util.select().fromTo(1, 1, 7, 1, 1, 9);
        Selection gasSide = util.select().fromTo(1, 1, 6, 1, ROOF + 1, 6).add(compressorDrive);
        Selection pumpDrive = util.select().fromTo(2, 1, 4, 2, 2, 4);
        Selection frosiumSide = util.select().fromTo(1, 1, 4, 1, ROOF + 1, 4)
                .add(util.select().fromTo(2, ROOF + 1, 4, 4, ROOF + 1, 4))
                .add(pumpDrive);
        Selection machine = util.select().fromTo(6, 2, 5, 6, 5, 5).add(util.select().fromTo(7, 4, 4, 7, 5, 4));
        Selection slow = util.select().position(INSIDE_MOTOR).add(util.select().position(LARGE_COG));
        Selection fast = util.select().position(SMALL_COG).add(util.select().position(MIXER));

        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(walls, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(machine, Direction.DOWN);
        scene.idle(10);

        // 1. Not cold: Create's limit
        scene.world().setKineticSpeed(slow, limit / 2f);
        scene.world().setKineticSpeed(fast, -limit);
        GogglesDisplay goggles = display();
        goggles.show(scene, room(0, 0, 3), ambient);
        speeds(scene, util, limit / 2, limit, 90);
        scene.overlay().showText(80)
                .text("In Create, a machine does not go faster than %1$s RPM.", limit)
                .independent(0)
                .attachKeyFrame();
        scene.idle(100);

        // 2. The cold: the roof with its vents, the compressor and its gas
        scene.world().showSection(roof, Direction.DOWN);
        scene.world().showSection(gasSide, Direction.DOWN);
        scene.idle(15);
        scene.world().modifyBlockEntityNBT(util.select().position(COMPRESSOR), CompressorBlockEntity.class, nbt -> {
            FluidTank tank = new FluidTank(value(FwConfig.COMPRESSOR_BUFFER));
            tank.fill(FwFluids.gas(tank.getCapacity()), IFluidHandler.FluidAction.EXECUTE);
            nbt.put("Gas", tank.writeToNBT(Minecraft.getInstance().level.registryAccess(), new CompoundTag()));
        });
        scene.world().setKineticSpeed(compressorDrive, 64);
        scene.world().propagatePipeChange(COMPRESSOR);
        scene.idle(20);
        vents(scene, true, GAS_VENT_1, GAS_VENT_2, GAS_VENT_3);
        goggles.set(scene, room(3, 0, 3), SCENE_RATE);
        scene.overlay().showText(90)
                .text("A Compressor feeds the vent with cold gas. The temperature of the room drops.")
                .pointAt(util.vector().topOf(COMPRESSOR))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(Math.max(100, ticksBetween(ambient, gasTarget) + 10));

        // 3. Cold: 320 rpm. The roof goes away again to see the gears
        scene.world().hideSection(roof, Direction.UP);
        scene.idle(15);
        scene.world().setKineticSpeed(slow, cold / 2f);
        scene.world().setKineticSpeed(fast, -cold);
        speeds(scene, util, cold / 2, cold, 100);
        scene.overlay().showText(100)
                .text("Once the room is cold, it allows %1$s RPM. Motor at %2$s RPM, gears at x2: the machine turns at %1$s.",
                        cold, cold / 2)
                .independent(90) // under the speed labels: at the top, its four lines covered the machine's label
                .attachKeyFrame();
        scene.idle(110);

        // 4. Frosium: the roof, a third vent and the tank with its pump; then the gears at 384 rpm
        scene.world().restoreBlocks(util.select().position(FROSIUM_VENT));
        scene.world().showSection(roof, Direction.DOWN);
        scene.world().showSection(frosiumSide, Direction.DOWN);
        scene.idle(15);
        // The frost tank pushes nothing: a mechanical pump lifts the Frosium, as it must in game
        scene.world().setKineticSpeed(pumpDrive, 32);
        scene.world().setKineticSpeed(util.select().position(PUMP), -32);
        scene.world().propagatePipeChange(PUMP);
        scene.idle(15);
        // A room takes the target of its warmest fluid: the gas vents stop, the Frosium vent alone cools it
        vents(scene, true, FROSIUM_VENT);
        vents(scene, false, GAS_VENT_1, GAS_VENT_2, GAS_VENT_3);
        goggles.set(scene, room(0, 1, 4), SCENE_RATE);
        scene.idle(ticksBetween(gasTarget, frosiumTarget) + 5);
        scene.overlay().showText(90)
                .text("With Frosium, the room goes down to %1$s °C and allows %2$s RPM.", fmt(frosiumTarget),
                        frosium)
                .pointAt(util.vector().topOf(TANK))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);
        scene.world().hideSection(roof, Direction.UP);
        scene.world().hideSection(frosiumSide, Direction.UP);
        scene.idle(15);
        scene.world().setKineticSpeed(slow, frosium / 2f);
        scene.world().setKineticSpeed(fast, -frosium);
        speeds(scene, util, frosium / 2, frosium, 80);
        // The stress: Create's machines (and other mods') take less in a closed cold room, ours keep theirs
        scene.overlay().showText(90)
                .text("In a closed cold room, machines also take less stress: %1$s %% less from %2$s °C, %3$s %% less from %4$s °C. Frostworks' own machines keep theirs.",
                        ColdStress.percent(ColdStress.COLD), fmt(-10), ColdStress.percent(ColdStress.DEEP),
                        fmt(Overspeed.DEEP_COLD))
                .independent(90)
                .attachKeyFrame();
        scene.idle(100);

        // 5. The cold goes away: after the grace period, Create's rule breaks the gear that is too fast
        scene.world().showSection(roof, Direction.DOWN);
        scene.world().showSection(frosiumSide, Direction.DOWN);
        scene.idle(15);
        scene.world().setKineticSpeed(compressorDrive, 0);
        scene.world().setKineticSpeed(pumpDrive.add(util.select().position(PUMP)), 0);
        scene.world().modifyBlockEntity(TANK, FluidTankBlockEntity.class,
                t -> t.getTankInventory().setFluid(FluidStack.EMPTY));
        vents(scene, false, GAS_VENT_1, GAS_VENT_2, GAS_VENT_3, FROSIUM_VENT);
        goggles.set(scene, room(0, 0, 4), SCENE_RATE);
        scene.overlay().showText(110)
                .text("If the cold goes away, the room turns normal again after a grace period: Create's rule applies and gears that are too fast break.")
                .independent(0)
                .attachKeyFrame();
        scene.idle(60);
        scene.world().hideSection(roof, Direction.UP);
        scene.world().hideSection(frosiumSide, Direction.UP);
        scene.idle(30);
        scene.world().destroyBlock(LARGE_COG);
        scene.world().setKineticSpeed(fast, 0);
        scene.idle(50);
        scene.markAsFinished();
    }

    // ---- Scene 3: a belt between two cold rooms -------------------------------------------------------------------

    /**
     * On the schematic {@code belt_rooms} ({@code MachineSchematics.beltRooms}): two cold rooms cut
     * open on the north, a belt from one to the other through two passages, out in the open between them. At 320 rpm it
     * holds, its two ends being in the cold; a shaft put on it out in the open breaks it there.
     */
    public static void belt(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("cold_room_belt", "A Belt Between Two Cold Rooms");
        scene.configureBasePlate(0, 0, 14);
        scene.scaleSceneView(.6f);
        int cold = coldLimit(false);
        BlockPos start = new BlockPos(3, 2, 2), end = new BlockPos(11, 2, 2), outside = new BlockPos(7, 2, 2),
                motor = new BlockPos(2, 3, 4);
        Selection belt = util.select().fromTo(start, end);
        // cut open as in the speed scene: the north walls, and the west wall of the room with the gears
        Selection rooms = util.select().fromTo(0, 1, 0, 5, 6, 5).substract(util.select().fromTo(0, 2, 0, 4, 4, 4))
                .add(util.select().fromTo(9, 1, 0, 13, 5, 5).substract(util.select().fromTo(10, 2, 0, 12, 3, 4)))
                .substract(belt);
        // the roof of the room with the gears (and its pipes) hides them: it goes away once the cold is told
        Selection roof = util.select().fromTo(0, 5, 0, 5, 6, 5);
        rooms = rooms.substract(roof);
        Selection slow = cells(util, motor, new BlockPos(2, 3, 3));
        Selection fast = util.select().position(3, 2, 3).add(belt);

        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(rooms, Direction.DOWN);
        scene.world().showSection(roof, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(cells(util, motor, new BlockPos(2, 3, 3), new BlockPos(3, 2, 3)).add(belt),
                Direction.DOWN);
        vents(scene, true, new BlockPos(1, 5, 1), new BlockPos(4, 5, 1), new BlockPos(11, 4, 3));
        scene.idle(15);

        // 1. Out of one cold room, into another
        scene.overlay().showText(80)
                .text("A belt can leave a Cold Room through a belt passage and go into another Cold Room.")
                .pointAt(util.vector().topOf(outside))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        // 2. Fast: it holds, its two ends being in the cold
        scene.world().hideSection(roof, Direction.UP);
        scene.idle(15);
        scene.world().setKineticSpeed(slow, cold / 2f);
        scene.world().setKineticSpeed(fast, -cold);
        scene.overlay().showText(100)
                .colored(PonderPalette.MEDIUM)
                .sharedText("rpm", cold / 2)
                .pointAt(util.vector().topOf(motor))
                .placeNearTarget();
        scene.overlay().showText(100)
                .colored(PonderPalette.FAST)
                .sharedText("rpm", cold)
                .pointAt(util.vector().topOf(end))
                .placeNearTarget();
        scene.overlay().showOutline(PonderPalette.GREEN, "ends", cells(util, start, end), 100);
        scene.overlay().showText(100)
                .text("At %1$s RPM it holds as long as both of its ends are in the cold. The part in between, out in the open, does not matter.",
                        cold)
                .independent(0)
                .attachKeyFrame();
        scene.idle(110);
        scene.overlay().showText(80)
                .text("If one of its ends is out of the cold, the belt breaks.")
                .independent(0);
        scene.idle(90);

        // 3. A shaft put on it out in the open turns there too: it breaks at that point
        scene.overlay().showControls(util.vector().topOf(outside), Pointing.DOWN, 40).rightClick()
                .withItem(new ItemStack(com.simibubi.create.AllBlocks.SHAFT.get()));
        scene.idle(10);
        scene.world().modifyBlock(outside, s -> s.setValue(BeltBlock.PART, BeltPart.PULLEY), false);
        scene.overlay().showOutline(PonderPalette.RED, "pulley", util.select().position(outside), 70);
        scene.overlay().showText(70)
                .text("A shaft put on the belt out of the cold turns too fast there: the belt breaks at that point.")
                .pointAt(util.vector().topOf(outside))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);
        scene.world().destroyBlock(outside);
        for (int x = start.getX(); x <= end.getX(); x++)
            if (x != outside.getX())
                scene.world().destroyBlock(new BlockPos(x, 2, 2)); // Create's belt goes all at once
        scene.idle(40);
        scene.markAsFinished();
    }

    /** The speeds of the motor and of the machine, in Create's colours, for {@code ticks}. */
    private static void speeds(CreateSceneBuilder scene, SceneBuildingUtil util, int motor, int machine, int ticks) {
        scene.overlay().showText(ticks)
                .colored(PonderPalette.MEDIUM)
                .sharedText("rpm", motor)
                .pointAt(util.vector().blockSurface(INSIDE_MOTOR, Direction.NORTH))
                .placeNearTarget();
        scene.overlay().showText(ticks)
                .colored(PonderPalette.FAST)
                .sharedText("rpm", machine)
                .pointAt(util.vector().topOf(SMALL_COG))
                .placeNearTarget();
    }

    /**
     * Ponder turns its camera by an exponential chase (10 % of what is left each tick), which keeps
     * creeping for about 115 ticks after a 20 degree turn. While it creeps, the texts and their lines sit on fractions of
     * a pixel and their lines hop from one row to the next: they shook. After the visible part of a turn
     * ({@value #TURN_TICKS} ticks, less than 0.2 degree left) the camera is set on its target, and texts with a line
     * come only after that.
     */
    static final int TURN_TICKS = 40;

    static void settleCamera(SceneBuilder scene) {
        scene.addInstruction(ps -> {
            var transform = ps.getTransform();
            for (LerpedFloat rotation : new LerpedFloat[]{transform.xRotation, transform.yRotation}) {
                float target = rotation.getChaseTarget();
                rotation.setValue(target);
                rotation.setValue(target); // twice: the previous value too, so that it counts as settled
            }
        });
    }

    /** Scene ticks for the display to go from one temperature to another. */
    private static int ticksBetween(float from, float to) {
        return (int) Math.ceil(Math.abs(from - to) / SCENE_RATE);
    }

    /** -10.0 -> "-10", as the config writes it. */
    static String fmt(float degrees) {
        return degrees == Math.round(degrees) ? Integer.toString(Math.round(degrees)) : Float.toString(degrees);
    }
}
