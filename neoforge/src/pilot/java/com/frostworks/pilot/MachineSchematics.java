package com.frostworks.pilot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BiConsumer;

import com.frostworks.content.passage.BeltPassage;
import com.frostworks.content.vent.VentBlock;
import com.frostworks.registry.FwBlocks;
import com.frostworks.registry.FwFluids;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.crank.HandCrankBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * The schematics of the six machine scenes, built in the pilot's world and saved like the cold room
 * ({@link ColdRoomSchematic}). Each one stands on a small snow plate at y = 0; the Ponder camera looks from the x = 0,
 * z = 0 corner (north and west faces are seen).
 */
public final class MachineSchematics {
    private MachineSchematics() {}

    /** A schematic: its name (file in {@code ponder/}), its size and how it is built. */
    public record Schematic(String name, Vec3i size, BiConsumer<ServerLevel, BlockPos> build) {}

    public static final Schematic[] ALL = {
            new Schematic("compressor", new Vec3i(5, 3, 5), MachineSchematics::compressor),
            new Schematic("cryo_infuser", new Vec3i(5, 8, 5), MachineSchematics::infuser),
            new Schematic("cryogenic_tower", new Vec3i(8, 7, 8), MachineSchematics::tower),
            new Schematic("frost_tank", new Vec3i(5, 4, 5), MachineSchematics::frostTank),
            new Schematic("expander", new Vec3i(5, 4, 5), MachineSchematics::expander),
            new Schematic("cold_vent", new Vec3i(7, 6, 7), MachineSchematics::vent),
            new Schematic("belt_rooms", new Vec3i(14, 7, 6), MachineSchematics::beltRooms),
            new Schematic("chilled_ores", new Vec3i(11, 7, 9), MachineSchematics::chilledOres)};

    /**
     * On the left, the infuser (1,3,3) facing north over a depot (1,1,3), its motor (1,3,2); on the right,
     * a cold room (walls x 3..9, y 1..5, z 1..6, inside 5 x 3 x 4) around two crushing wheels (axis Z) at (5,3,3) and
     * (7,3,3), driven through two shaft passages in the south wall (z 6) by motors outside (z 7); a gas vent (4,5,5) and
     * a Frosium vent (8,5,5) in the roof, with their pipes.
     */
    private static void chilledOres(ServerLevel level, BlockPos o) {
        set(level, o, 1, 1, 3, AllBlocks.DEPOT.getDefaultState());
        set(level, o, 1, 3, 3, FwBlocks.CRYO_INFUSER.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        set(level, o, 1, 3, 2, motor(Direction.SOUTH));
        BlockState wall = FwBlocks.COLD_WALL.get().defaultBlockState();
        for (int x = 3; x <= 9; x++)
            for (int y = 1; y <= 5; y++)
                for (int z = 1; z <= 6; z++)
                    if (x == 3 || x == 9 || y == 1 || y == 5 || z == 1 || z == 6)
                        set(level, o, x, y, z, wall);
        for (int[] vent : new int[][]{{4, 5, 5}, {8, 5, 5}}) {
            set(level, o, vent[0], vent[1], vent[2], FwBlocks.VENT.get().defaultBlockState()
                    .setValue(VentBlock.FACING, Direction.DOWN));
            pipe(level, o, vent[0], vent[1] + 1, vent[2]);
        }
        for (int x : new int[]{5, 7}) {
            set(level, o, x, 3, 7, motor(Direction.NORTH));
            set(level, o, x, 3, 6, FwBlocks.SHAFT_PASSAGE.get().defaultBlockState()
                    .setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
            set(level, o, x, 3, 5, shaft(Direction.Axis.Z));
            set(level, o, x, 3, 4, shaft(Direction.Axis.Z));
            set(level, o, x, 3, 3, AllBlocks.CRUSHING_WHEEL.getDefaultState()
                    .setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        }
    }

    public static void build(ServerLevel level, BlockPos o, Schematic s) {
        for (BlockPos p : BlockPos.betweenClosed(o, o.offset(s.size().getX() - 1, s.size().getY() - 1,
                s.size().getZ() - 1)))
            level.setBlock(p, p.getY() == o.getY() ? Blocks.SNOW_BLOCK.defaultBlockState()
                    : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        stopMotors(level, o, s.size());
        s.build().accept(level, o);
        stopMotors(level, o, s.size());
        ColdRoomSchematic.settlePipes(level, o, s.size());
    }

    /**
     * Every creative motor at 0 rpm before it first ticks. At its default speed it turned the pumps and
     * compressors of the pilot's world while the schematic was built: the tower took water and gas, the pipes held
     * flows, and all of it was saved and shown by the scene. The scenes set every speed themselves.
     */
    static void stopMotors(ServerLevel level, BlockPos o, Vec3i size) {
        for (BlockPos p : BlockPos.betweenClosed(o, o.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1)))
            if (level.getBlockEntity(p) instanceof CreativeMotorBlockEntity motor) {
                CompoundTag tag = motor.saveWithFullMetadata(level.registryAccess());
                tag.putInt("ScrollValue", 0);
                motor.loadWithComponents(tag, level.registryAccess());
            }
    }

    public static void save(ServerLevel level, BlockPos o, Schematic s, Path dir) throws IOException {
        ColdRoomSchematic.save(level, o, s.size(), dir.resolve(s.name() + ".nbt"));
    }

    // ---- The six machines -----------------------------------------------------------------------------------------

    /** Motor, shaft, compressor facing north, a glass pipe and Create's tank in front. */
    private static void compressor(ServerLevel level, BlockPos o) {
        set(level, o, 2, 1, 4, motor(Direction.NORTH));
        set(level, o, 2, 1, 3, shaft(Direction.Axis.Z));
        set(level, o, 2, 1, 2, FwBlocks.COMPRESSOR.get().defaultBlockState().setValue(PumpBlock.FACING,
                Direction.NORTH));
        set(level, o, 2, 1, 1, glass(Direction.Axis.Z));
        set(level, o, 2, 1, 0, AllBlocks.FLUID_TANK.getDefaultState());
    }

    /**
     * A depot, the infuser two blocks above it (facing north: shaft along Z, gas inlet holes on its east and west faces), its shaft and
     * motor; a compressor above the inlet pipe feeds it through its west hole.
     */
    private static void infuser(ServerLevel level, BlockPos o) {
        set(level, o, 2, 1, 2, AllBlocks.DEPOT.getDefaultState());
        set(level, o, 2, 3, 2, FwBlocks.CRYO_INFUSER.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        set(level, o, 2, 3, 3, shaft(Direction.Axis.Z));
        set(level, o, 2, 3, 4, motor(Direction.NORTH));
        // Into the hole the camera sees (west; both holes take the gas now)
        pipe(level, o, 1, 3, 2);
        set(level, o, 1, 4, 2, FwBlocks.COMPRESSOR.get().defaultBlockState().setValue(PumpBlock.FACING,
                Direction.DOWN));
        set(level, o, 1, 5, 2, shaft(Direction.Axis.Y));
        set(level, o, 1, 6, 2, motor(Direction.DOWN));
    }

    /**
     * A 2 x 2 tower: bases (3..4,1,3..4), three layers of frost tanks turned into column on top. Rotation in from the
     * west (motor and shaft), cold gas from a compressor on the north, water pumped from a tank on the south; liquid
     * Frosium pumped out on the east into a frost tank.
     */
    private static void tower(ServerLevel level, BlockPos o) {
        for (int x = 3; x <= 4; x++)
            for (int z = 3; z <= 4; z++)
                set(level, o, x, 1, z, FwBlocks.COLUMN_BASE.get().defaultBlockState());
        for (int y = 2; y <= 4; y++)
            for (int x = 3; x <= 4; x++)
                for (int z = 3; z <= 4; z++)
                    set(level, o, x, y, z, FwBlocks.RESERVOIR.get().defaultBlockState());
        // rotation
        set(level, o, 1, 1, 4, motor(Direction.EAST));
        set(level, o, 2, 1, 4, shaft(Direction.Axis.X));
        // cold gas
        set(level, o, 0, 1, 2, motor(Direction.EAST));
        set(level, o, 1, 1, 2, shaft(Direction.Axis.X));
        set(level, o, 2, 1, 2, FwBlocks.COMPRESSOR.get().defaultBlockState().setValue(PumpBlock.FACING,
                Direction.EAST));
        pipe(level, o, 3, 1, 2);
        // water, pumped in from a tank on the south (away from the gas pipe: two pipes side by side would join)
        pipe(level, o, 3, 1, 5);
        set(level, o, 3, 1, 6, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING,
                Direction.NORTH));
        set(level, o, 3, 1, 7, AllBlocks.FLUID_TANK.getDefaultState());
        set(level, o, 2, 1, 6, cog(Direction.Axis.Z));
        set(level, o, 2, 1, 7, motor(Direction.NORTH));
        if (level.getBlockEntity(o.offset(3, 1, 7)) instanceof FluidTankBlockEntity tank)
            tank.getTankInventory().fill(new FluidStack(Fluids.WATER, 8000), IFluidHandler.FluidAction.EXECUTE);
        // Frosium, pumped out into a frost tank
        set(level, o, 5, 1, 3, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING,
                Direction.EAST));
        set(level, o, 5, 2, 3, cog(Direction.Axis.X));
        set(level, o, 6, 2, 3, motor(Direction.WEST));
        set(level, o, 6, 1, 3, glass(Direction.Axis.X));
        set(level, o, 7, 1, 3, FwBlocks.RESERVOIR.get().defaultBlockState());
        // Ice, by a belt one step lower (sunk in the plate), into a funnel on the east side of the base
        set(level, o, 7, 0, 4, shaft(Direction.Axis.Z));
        set(level, o, 5, 0, 4, shaft(Direction.Axis.Z));
        BeltConnectorItem.createBelts(level, o.offset(7, 0, 4), o.offset(5, 0, 4));
        BlockPos funnel = o.offset(5, 1, 4);
        level.setBlock(funnel, Block.updateFromNeighbourShapes(AllBlocks.ANDESITE_BELT_FUNNEL.getDefaultState()
                .setValue(com.simibubi.create.content.logistics.funnel.BeltFunnelBlock.HORIZONTAL_FACING, Direction.EAST),
                level, funnel), Block.UPDATE_ALL);
    }

    /** A frost tank two blocks high. */
    private static void frostTank(ServerLevel level, BlockPos o) {
        set(level, o, 2, 1, 2, FwBlocks.RESERVOIR.get().defaultBlockState());
        set(level, o, 2, 2, 2, FwBlocks.RESERVOIR.get().defaultBlockState());
    }

    /**
     * The expander facing west (as drawn): cold gas out of its front flange (west) into
     * Create's tank through a glass pipe; Frosium in at its back flange (east), pumped from a frost tank; its
     * shaft across (north and south faces), on a hand crank to the north, the network's own source.
     */
    private static void expander(ServerLevel level, BlockPos o) {
        set(level, o, 2, 1, 2, FwBlocks.EXPANDER.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.WEST));
        set(level, o, 1, 1, 2, glass(Direction.Axis.X));
        set(level, o, 0, 1, 2, AllBlocks.FLUID_TANK.getDefaultState());
        set(level, o, 2, 1, 1, shaft(Direction.Axis.Z));
        set(level, o, 2, 1, 0, AllBlocks.HAND_CRANK.getDefaultState().setValue(HandCrankBlock.FACING,
                Direction.NORTH));
        set(level, o, 3, 1, 2, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING,
                Direction.WEST));
        set(level, o, 3, 2, 2, cog(Direction.Axis.X));
        set(level, o, 4, 2, 2, motor(Direction.WEST));
        set(level, o, 4, 1, 2, FwBlocks.RESERVOIR.get().defaultBlockState());
        if (level.getBlockEntity(o.offset(4, 1, 2)) instanceof FluidTankBlockEntity tank)
            tank.getTankInventory().fill(new FluidStack(FwFluids.frosium(), 8000),
                    IFluidHandler.FluidAction.EXECUTE);
    }

    /**
     * A small room of cold walls (x 1..5, y 1..4, z 1..5: inside 3 x 2 x 3 = 18 cells, one vent covers it), the vent
     * in the south wall blowing north into it, its pipe behind.
     */
    private static void vent(ServerLevel level, BlockPos o) {
        BlockState wall = FwBlocks.COLD_WALL.get().defaultBlockState();
        for (int x = 1; x <= 5; x++)
            for (int y = 1; y <= 4; y++)
                for (int z = 1; z <= 5; z++)
                    if (x == 1 || x == 5 || y == 1 || y == 4 || z == 1 || z == 5)
                        set(level, o, x, y, z, wall);
        set(level, o, 3, 2, 5, FwBlocks.VENT.get().defaultBlockState().setValue(VentBlock.FACING, Direction.NORTH));
        pipe(level, o, 3, 2, 6);
    }

    /**
     * Two cold rooms three blocks apart and a belt from one to the other. Room A: walls x 0..5, y 1..5,
     * z 0..5 (inside 4 x 3 x 4 = 48 cells, two roof vents); room B: walls x 9..13, y 1..4, z 0..5 (inside 3 x 2 x 4 = 24,
     * one roof vent). The belt runs along X at y 2, z 2, from (3,2,2) to (11,2,2), through passages at x 5 and x 9; out
     * in the open at x 6..8. In A, behind the belt, a motor (2,3,4) drives a large cogwheel (2,3,3) and a small one
     * (3,2,3) at x2, on the belt's first pulley.
     */
    private static void beltRooms(ServerLevel level, BlockPos o) {
        BlockState wall = FwBlocks.COLD_WALL.get().defaultBlockState();
        for (int[] room : new int[][]{{0, 5, 5}, {9, 13, 4}})
            for (int x = room[0]; x <= room[1]; x++)
                for (int y = 1; y <= room[2]; y++)
                    for (int z = 0; z <= 5; z++)
                        if (x == room[0] || x == room[1] || y == 1 || y == room[2] || z == 0 || z == 5)
                            set(level, o, x, y, z, wall);
        for (int[] vent : new int[][]{{1, 5, 1}, {4, 5, 1}, {11, 4, 3}}) {
            set(level, o, vent[0], vent[1], vent[2], FwBlocks.VENT.get().defaultBlockState()
                    .setValue(VentBlock.FACING, Direction.DOWN));
            pipe(level, o, vent[0], vent[1] + 1, vent[2]);
        }
        for (int x = 3; x <= 11; x++)
            set(level, o, x, 2, 2, Blocks.AIR.defaultBlockState());
        set(level, o, 3, 2, 2, shaft(Direction.Axis.Z));
        set(level, o, 11, 2, 2, shaft(Direction.Axis.Z));
        BeltConnectorItem.createBelts(level, o.offset(3, 2, 2), o.offset(11, 2, 2));
        BeltPassage.encase(level, o.offset(5, 2, 2));
        BeltPassage.encase(level, o.offset(9, 2, 2));
        set(level, o, 3, 2, 3, cog(Direction.Axis.Z));
        set(level, o, 2, 3, 3, AllBlocks.LARGE_COGWHEEL.getDefaultState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        set(level, o, 2, 3, 4, motor(Direction.NORTH));
    }

    // ---- Pieces -------------------------------------------------------------------------------------------------

    private static BlockState motor(Direction facing) {
        return AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, facing);
    }

    private static BlockState shaft(Direction.Axis axis) {
        return AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, axis);
    }

    private static BlockState cog(Direction.Axis axis) {
        return AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, axis);
    }

    private static BlockState glass(Direction.Axis axis) {
        return AllBlocks.GLASS_FLUID_PIPE.getDefaultState().setValue(BlockStateProperties.AXIS, axis);
    }

    private static void pipe(ServerLevel level, BlockPos o, int x, int y, int z) {
        set(level, o, x, y, z, AllBlocks.FLUID_PIPE.getDefaultState());
    }

    private static void set(ServerLevel level, BlockPos o, int x, int y, int z, BlockState state) {
        level.setBlock(o.offset(x, y, z), state, Block.UPDATE_ALL);
    }
}
