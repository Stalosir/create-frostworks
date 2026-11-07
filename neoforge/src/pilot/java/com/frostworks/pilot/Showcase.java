package com.frostworks.pilot;

import com.frostworks.content.compressor.CompressorBlock;
import com.frostworks.content.door.InsulatedDoorBlock;
import com.frostworks.content.passage.BeltPassage;
import com.frostworks.content.vent.VentBlock;
import com.frostworks.registry.FwBlocks;
import com.frostworks.registry.FwFluids;
import com.frostworks.room.Room;
import com.frostworks.room.RoomManager;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Our showcase world ("monde de rendu"): a normal world from a seed (snowy taiga at spawn), a cold room with
 * its whole cold chain and Create's iron sheet line inside (press over a belt, as the Create wiki shows it), followed
 * by our cryogenic infuser. Built by code; run {@code ./gradlew runClientPilote -Pscenario=showcase}, then copy
 * {@code run-pilote/saves/monde de rendu} into {@code run/saves}.
 * <p>
 * Coordinates from {@link #origin} (x east, z south, y 0 = first block over the levelled ground):
 * <ul>
 * <li>room: cold walls x 10..20, y 0..5, z 4..14 (inside 9 x 4 x 9 = 324 cells), strip door (15,1..2,14);</li>
 * <li>six Frosium vents in the roof (12/15/18, 5, 7/11), pipes over them, fed from a frost tank 2 x 2 x 4
 * (x 14..15, z 0..1) by a pump on its top;</li>
 * <li>line: chest and hopper (5,2..3,9) over a belt x 4..21 z 9, through a belt passage (10,1,9); press (13,3,9) and
 * infuser (16,3,9) over it, the infuser fed by a compressor (17,3,11); out of the east wall in a second belt passage
 * (20,1,9), into a chute (22,1,9) over a chest (22,0,9);</li>
 * <li>overspeed: motor (13,3,6) -> large cogwheel (13,4,6) -> small cogwheel (12,4,7) -> mixer (12,3,7) over a basin;</li>
 * <li>tower: base x 24..25 z 8..9, three layers of column, its shaft from the west, a compressor north of it, water
 * pumped from a creative tank east of it, Frosium pumped south into a frost tank (x 24..25, z 12..13).</li>
 * </ul>
 */
public final class Showcase {
    private Showcase() {}

    public static final String WORLD_NAME = "monde de rendu";
    public static final long SEED = -738014095L;

    private static BlockPos origin;

    static BlockPos origin() {
        return origin;
    }

    static int run(Minecraft mc, int t) {
        if (t == 30)
            ClientPilot.server(mc, level -> {
                BlockPos spawn = level.getSharedSpawnPos();
                origin = new BlockPos(spawn.getX(), 68, spawn.getZ());
                level(level);
                build(level);
                System.out.println("PILOT showcase built at " + origin);
            });
        if (t == 60)
            ClientPilot.server(mc, Showcase::start);
        if (t == 300)
            ClientPilot.server(mc, level -> report(level, "after 15 s"));
        if (t == 320)
            ClientPilot.server(mc, level -> motor(level, 13, 3, 6, 192)); // the mixer at 384 once the room is cold
        if (t == 1900)
            ClientPilot.server(mc, level -> report(level, "after 95 s"));
        String[][] views = {
                {"1950", "2.5 2 21.5", "15 2 9", "showcase_outside"},
                {"2000", "28 6 -6", "18 1 8", "showcase_chain"},
                {"2050", "11.5 1 12.5", "17 2 8", "showcase_inside"},
                {"2100", "14.5 1 5.5", "12 3 8", "showcase_mixer"},
                {"2150", "15.5 1 22.5", "15 2 14", "showcase_door"}};
        for (String[] v : views) {
            int at = Integer.parseInt(v[0]);
            if (t == at - 30) {
                String[] p = v[1].split(" "), l = v[2].split(" ");
                ClientPilot.cmd(mc, "tp @a " + (origin.getX() + Double.parseDouble(p[0])) + " "
                        + (origin.getY() + Double.parseDouble(p[1])) + " " + (origin.getZ() + Double.parseDouble(p[2])));
                ClientPilot.lookTarget = new Vec3(origin.getX() + Double.parseDouble(l[0]),
                        origin.getY() + Double.parseDouble(l[1]), origin.getZ() + Double.parseDouble(l[2]));
            }
            if (t == at)
                ClientPilot.capture(mc, v[3]);
        }
        if (t == 2160) {
            // where the player arrives: in front of the door, the room ahead
            ClientPilot.cmd(mc, "setworldspawn " + (origin.getX() + 15) + " " + origin.getY() + " " + (origin.getZ() + 22));
            ClientPilot.cmd(mc, "tp @a " + (origin.getX() + 15.5) + " " + origin.getY() + " " + (origin.getZ() + 22.5)
                    + " 180 10");
            ClientPilot.cmd(mc, "gamerule doDaylightCycle true");
            ClientPilot.cmd(mc, "effect clear @a"); // the pilot's night vision must not stay in the showcase world
            ClientPilot.cmd(mc, "time set 6000");
            ClientPilot.lookTarget = null;
        }
        return 2180;
    }

    // ---- Ground ---------------------------------------------------------------------------------------------------

    /** Levels x -4..34, z -6..24 at y 0: snowy grass under a stone brick yard, the trees over it cut away. */
    private static void level(ServerLevel level) {
        for (int x = -4; x <= 34; x++)
            for (int z = -6; z <= 24; z++) {
                for (int y = -8; y <= 30; y++) {
                    BlockState state = y < -3 ? Blocks.STONE.defaultBlockState()
                            : y < -1 ? Blocks.DIRT.defaultBlockState()
                            : y == -1 ? yard(x, z) : Blocks.AIR.defaultBlockState();
                    if (y < -3 && !level.getBlockState(at(x, y, z)).isAir())
                        continue; // keep the natural ground underneath
                    set(level, x, y, z, state);
                }
            }
    }

    private static BlockState yard(int x, int z) {
        boolean yard = x >= 1 && x <= 30 && z >= -3 && z <= 18;
        boolean path = x >= 14 && x <= 16 && z > 18; // from the door to the arrival point
        return yard || path ? Blocks.STONE_BRICKS.defaultBlockState()
                : Blocks.GRASS_BLOCK.defaultBlockState().setValue(BlockStateProperties.SNOWY, true);
    }

    // ---- The build ------------------------------------------------------------------------------------------------

    private static void build(ServerLevel level) {
        BlockState wall = FwBlocks.COLD_WALL.get().defaultBlockState();
        for (int x = 10; x <= 20; x++)
            for (int y = 0; y <= 5; y++)
                for (int z = 4; z <= 14; z++)
                    if (x == 10 || x == 20 || y == 0 || y == 5 || z == 4 || z == 14)
                        set(level, x, y, z, wall);
        // door
        BlockState door = FwBlocks.INSULATED_DOOR.get().defaultBlockState().setValue(InsulatedDoorBlock.FACING,
                Direction.SOUTH);
        set(level, 15, 1, 14, door.setValue(InsulatedDoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, 15, 2, 14, door.setValue(InsulatedDoorBlock.HALF, DoubleBlockHalf.UPPER));
        // lanterns in the four corners
        for (int[] c : new int[][]{{11, 5}, {19, 5}, {11, 13}, {19, 13}})
            set(level, c[0], 1, c[1], Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));

        // Frosium: tank 2 x 2 x 4 north of the room, pump on top, pipes over the roof, six vents
        for (int y = 0; y <= 3; y++)
            for (int x = 14; x <= 15; x++)
                for (int z = 0; z <= 1; z++)
                    set(level, x, y, z, FwBlocks.RESERVOIR.get().defaultBlockState());
        set(level, 15, 4, 1, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.UP));
        set(level, 14, 4, 1, cog(Direction.Axis.Y));
        set(level, 14, 5, 1, motor(Direction.DOWN));
        pipe(level, 15, 5, 1);
        for (int z = 1; z <= 11; z++)
            pipe(level, 15, 6, z);
        for (int z : new int[]{7, 11}) {
            for (int x = 12; x <= 18; x++)
                pipe(level, x, 6, z);
            for (int x : new int[]{12, 15, 18})
                set(level, x, 5, z, FwBlocks.VENT.get().defaultBlockState().setValue(VentBlock.FACING, Direction.DOWN));
        }

        // the iron sheet line: chest -> hopper -> belt -> press -> infuser -> chest
        set(level, 4, 1, 9, shaft(Direction.Axis.Z));
        set(level, 21, 1, 9, shaft(Direction.Axis.Z));
        set(level, 4, 1, 8, motor(Direction.SOUTH));
        // the belt goes through the west wall and out of the east one, in two belt passages
        set(level, 10, 1, 9, Blocks.AIR.defaultBlockState());
        set(level, 20, 1, 9, Blocks.AIR.defaultBlockState());
        BeltConnectorItem.createBelts(level, at(4, 1, 9), at(21, 1, 9));
        BeltPassage.encase(level, at(10, 1, 9));
        BeltPassage.encase(level, at(20, 1, 9));
        set(level, 5, 2, 9, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        set(level, 5, 3, 9, Blocks.CHEST.defaultBlockState());
        // outside, at the belt's end: a chute (a belt hands its items to a chute, not to a chest) over the chest
        set(level, 22, 1, 9, AllBlocks.CHUTE.getDefaultState());
        set(level, 22, 0, 9, Blocks.CHEST.defaultBlockState());
        set(level, 13, 3, 9, AllBlocks.MECHANICAL_PRESS.getDefaultState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        set(level, 13, 3, 8, motor(Direction.SOUTH));
        set(level, 16, 3, 9, FwBlocks.CRYO_INFUSER.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        set(level, 16, 3, 8, motor(Direction.SOUTH));
        pipe(level, 17, 3, 9);
        pipe(level, 17, 3, 10);
        set(level, 17, 3, 11, FwBlocks.COMPRESSOR.get().defaultBlockState().setValue(CompressorBlock.FACING,
                Direction.NORTH));
        set(level, 17, 3, 12, shaft(Direction.Axis.Z));
        set(level, 17, 3, 13, motor(Direction.NORTH));

        // overspeed: the mixer at x2 behind two cogwheels
        set(level, 12, 1, 7, AllBlocks.BASIN.getDefaultState());
        set(level, 12, 3, 7, AllBlocks.MECHANICAL_MIXER.getDefaultState());
        set(level, 12, 4, 7, cog(Direction.Axis.Y));
        set(level, 13, 4, 6, AllBlocks.LARGE_COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,
                Direction.Axis.Y));
        set(level, 13, 3, 6, motor(Direction.UP));

        // the distillation tower and its chain, east of the room
        for (int x = 24; x <= 25; x++)
            for (int z = 8; z <= 9; z++) {
                set(level, x, 0, z, FwBlocks.COLUMN_BASE.get().defaultBlockState());
                for (int y = 1; y <= 3; y++)
                    set(level, x, y, z, FwBlocks.RESERVOIR.get().defaultBlockState());
            }
        set(level, 22, 0, 8, motor(Direction.EAST));
        set(level, 23, 0, 8, shaft(Direction.Axis.X));
        set(level, 24, 0, 4, motor(Direction.SOUTH));
        set(level, 24, 0, 5, shaft(Direction.Axis.Z));
        set(level, 24, 0, 6, FwBlocks.COMPRESSOR.get().defaultBlockState().setValue(CompressorBlock.FACING,
                Direction.SOUTH));
        pipe(level, 24, 0, 7);
        set(level, 27, 0, 9, AllBlocks.CREATIVE_FLUID_TANK.getDefaultState());
        set(level, 26, 0, 9, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.WEST));
        set(level, 26, 1, 9, cog(Direction.Axis.X));
        set(level, 27, 1, 9, motor(Direction.WEST));
        set(level, 25, 0, 10, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.SOUTH));
        set(level, 25, 1, 10, cog(Direction.Axis.Z));
        set(level, 25, 1, 11, motor(Direction.NORTH));
        set(level, 25, 0, 11, AllBlocks.GLASS_FLUID_PIPE.getDefaultState().setValue(BlockStateProperties.AXIS,
                Direction.Axis.Z));
        for (int y = 0; y <= 1; y++)
            for (int x = 24; x <= 25; x++)
                for (int z = 12; z <= 13; z++)
                    set(level, x, y, z, FwBlocks.RESERVOIR.get().defaultBlockState());

        ColdRoomSchematic.settlePipes(level, at(-4, -1, -6), new Vec3i(40, 12, 32));
    }

    /** Speeds, contents: once the multiblocks have formed. */
    private static void start(ServerLevel level) {
        motor(level, 14, 5, 1, 32); // Frosium pump
        motor(level, 4, 1, 8, -32); // belt (negative: toward the room)
        motor(level, 13, 3, 8, 64); // press
        motor(level, 16, 3, 8, 64); // infuser
        motor(level, 17, 3, 13, 64); // its compressor
        motor(level, 22, 0, 8, 64); // tower
        motor(level, 24, 0, 4, 64); // tower's compressor
        motor(level, 27, 1, 9, 32); // water pump
        motor(level, 25, 1, 11, 32); // Frosium out of the tower
        motor(level, 13, 3, 6, 0); // the mixer waits for the cold
        if (level.getBlockEntity(at(14, 0, 0)) instanceof FluidTankBlockEntity tank) {
            FluidTankBlockEntity c = tank.getControllerBE();
            c.getTankInventory().fill(new FluidStack(FwFluids.frosium(), c.getTankInventory().getCapacity()),
                    IFluidHandler.FluidAction.EXECUTE);
        }
        if (level.getBlockEntity(at(27, 0, 9)) instanceof CreativeFluidTankBlockEntity tank
                && tank.getTankInventory() instanceof CreativeFluidTankBlockEntity.CreativeSmartFluidTank creative)
            creative.setContainedFluid(new FluidStack(Fluids.WATER, 1000));
        if (level.getBlockEntity(at(5, 3, 9)) instanceof ChestBlockEntity chest)
            for (int i = 0; i < 27; i++)
                chest.setItem(i, new ItemStack(Items.IRON_INGOT, 64));
        if (level.getBlockEntity(at(12, 1, 7)) instanceof BasinBlockEntity basin) {
            var items = basin.getInputInventory();
            items.insertItem(0, new ItemStack(Items.ANDESITE, 64), false);
            items.insertItem(1, new ItemStack(Items.IRON_NUGGET, 64), false);
        }
    }

    private static void report(ServerLevel level, String when) {
        Room room = RoomManager.get(level).roomAt(at(15, 2, 9));
        String status = room == null ? "no room" : room.status() + " " + room.volume + " cells, "
                + String.format("%.1f", room.temperature()) + " deg C, target " + room.coldTarget();
        String out = level.getBlockEntity(at(22, 0, 9)) instanceof ChestBlockEntity chest
                ? chest.getItem(0) + " " + chest.getItem(1) : "?";
        String in = level.getBlockEntity(at(5, 3, 9)) instanceof ChestBlockEntity chest ? "" + chest.getItem(0).getCount() : "?";
        String hopper = level.getBlockEntity(at(5, 2, 9)) instanceof net.minecraft.world.level.block.entity.HopperBlockEntity h
                ? h.getItem(0) + "" : "?";
        String belt = level.getBlockEntity(at(5, 1, 9)) instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity b
                && b.getControllerBE() != null ? b.getControllerBE().getInventory().getTransportedItems().size() + " items, speed "
                + b.getSpeed() : "no belt " + level.getBlockState(at(5, 1, 9));
        System.out.println("PILOT showcase " + when + ": room " + status + "; end chest " + out + "; input chest slot 0 "
                + in + "; hopper " + hopper + "; belt " + belt);
    }

    // ---- Pieces -------------------------------------------------------------------------------------------------

    private static BlockPos at(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    static void motor(ServerLevel level, int x, int y, int z, int rpm) {
        if (level.getBlockEntity(at(x, y, z)) instanceof CreativeMotorBlockEntity motor)
            motor.generatedSpeed.setValue(rpm);
    }

    private static BlockState motor(Direction facing) {
        return AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, facing);
    }

    private static BlockState shaft(Direction.Axis axis) {
        return AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, axis);
    }

    private static BlockState cog(Direction.Axis axis) {
        return AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, axis);
    }

    private static void pipe(ServerLevel level, int x, int y, int z) {
        set(level, x, y, z, AllBlocks.FLUID_PIPE.getDefaultState());
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(at(x, y, z), state, Block.UPDATE_ALL);
    }
}
