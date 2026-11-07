package com.frostworks.energy;

import java.util.ArrayList;
import java.util.List;

import com.frostworks.content.compressor.CompressorBlockEntity;
import com.frostworks.content.vent.VentBlock;
import com.frostworks.registry.FwBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * The complete stress loop of built from a corner (x to the east, z to the north = negative z):
 * <pre>
 * kinetic line (z = 0): creative motor (0) -> stressometer (2) -> gearboxes at x = 4, 9, 16, each with a shaft to the
 *                       north (z = -1):
 *                       - x 4 and x 16: into the back face of a machine at z = -2, front (output) facing north:
 *                         compressor (x 4), expander (x 16);
 *                       - x 9: two gearboxes (9,0,-1) and (9,1,-1) lift it into the south face of the distillation
 *                         tower: 2 x 2 x 3, x 9..10, y 1..3, z -3..-2, controller (9,1,-3)
 * gas:      compressor -> pipes (up at y 1) -> west face of the tower base (8,1,-3)
 * water:    creative tank (12,1,-2) -> mechanical pump (11,1,-2, own motor) -> east face of the tower base
 * liquid:   tower north face (9,1,-4): mechanical pump (own motor; the tower does not push)
 *           -> pipes -> reservoir (9,0,-6) -> mechanical pump (10,0,-6, own motor)
 *           -> pipes along x 13 and z -1 -> west face (back) of the expander
 * gas back: expander front (east) -> pipes north along x 17 -> 2 vents in the south wall of a small room (interior 4 x 2 x 4)
 * </pre>
 * Pipes never touch another line (Create pipes join any neighbour pipe), and every line is shorter than Create's
 * pump range (16 pipes). The three pumps have their own motors, so the stress seen on the main line is only
 * compressor + tower - expander.
 */
public final class LoopRig {
    private LoopRig() {}

    public static final int SIZE_X = 21, SIZE_Y = 6, SIZE_Z = 13;

    public record Layout(BlockPos origin) {
        public BlockPos at(int x, int y, int z) {
            return origin.offset(x, y, z);
        }

        public BlockPos motor() { return at(0, 0, 0); }
        public BlockPos gauge() { return at(2, 0, 0); }
        public BlockPos compressor() { return at(4, 0, -2); }
        /** Controller of the distillation tower (north-west block of its base). */
        public BlockPos column() { return at(9, 1, -3); }
        public BlockPos expander() { return at(16, 0, -2); }
        public BlockPos reservoir() { return at(9, 0, -6); }
        public BlockPos roomInterior() { return at(16, 1, -8); }
        public BlockPos center() { return at(9, 0, -4); }
    }

    public static Layout build(ServerLevel level, BlockPos origin, int motorRpm, int compressorSu, int sharePercent) {
        Layout l = new Layout(origin);
        // clear the area (x -1..19, z -11..1)
        for (int x = -1; x < SIZE_X - 1; x++)
            for (int y = 0; y < SIZE_Y; y++)
                for (int z = -SIZE_Z + 2; z <= 1; z++)
                    level.setBlock(l.at(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);

        // ---- room first: shell x 14..19, y 0..3, z -11..-6, vents in the south wall blowing north ----
        BlockState wall = FwBlocks.COLD_WALL.getDefaultState();
        for (int x = 14; x <= 19; x++)
            for (int y = 0; y <= 3; y++)
                for (int z = -11; z <= -6; z++) {
                    boolean shell = x == 14 || x == 19 || y == 0 || y == 3 || z == -11 || z == -6;
                    if (shell)
                        set(level, l.at(x, y, z), wall);
                }
        BlockState vent = FwBlocks.VENT.getDefaultState().setValue(VentBlock.FACING, Direction.NORTH);
        set(level, l.at(16, 1, -6), vent);
        set(level, l.at(17, 1, -6), vent);

        // ---- main kinetic line ----
        set(level, l.motor(), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        set(level, l.at(1, 0, 0), shaft(Direction.Axis.X));
        set(level, l.gauge(), AllBlocks.STRESSOMETER.getDefaultState()
                .setValue(DirectionalAxisKineticBlock.FACING, Direction.UP)
                .setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true));
        for (int x = 3; x <= 16; x++)
            set(level, l.at(x, 0, 0), x == 4 || x == 9 || x == 16
                    ? AllBlocks.GEARBOX.getDefaultState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y)
                    : shaft(Direction.Axis.X));
        for (int x : new int[]{4, 16})
            set(level, l.at(x, 0, -1), shaft(Direction.Axis.Z));
        // the tower takes its shaft on a side of its base: two gearboxes (axis X: north, south, up,
        // down) lift the line to y 1, into the south face of the base
        set(level, l.at(9, 0, -1), AllBlocks.GEARBOX.getDefaultState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        set(level, l.at(9, 1, -1), AllBlocks.GEARBOX.getDefaultState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));

        // ---- machines (front = output, facing north) ----
        set(level, l.compressor(), FwBlocks.COMPRESSOR.getDefaultState().setValue(PumpBlock.FACING, Direction.NORTH));
        // distillation tower 2 x 2 x 3: a layer of column bases and two of segments
        for (int y = 1; y <= 3; y++)
            for (int x = 9; x <= 10; x++)
                for (int z = -3; z <= -2; z++)
                    set(level, l.at(x, y, z), (y == 1 ? FwBlocks.COLUMN_BASE : FwBlocks.COLUMN_SEGMENT).getDefaultState());
        // Its ice, a full reserve of blue ice (the future controller keeps it when the tower forms)
        if (level.getBlockEntity(l.column()) instanceof com.frostworks.content.column.TowerBlockEntity t)
            t.stockIceForRig();
        set(level, l.expander(), FwBlocks.EXPANDER.getDefaultState().setValue(PumpBlock.FACING, Direction.EAST));
        set(level, l.reservoir(), FwBlocks.RESERVOIR.getDefaultState());

        // ---- water: creative tank -> pump (own motor) -> tower east face ----
        set(level, l.at(12, 1, -2), AllBlocks.CREATIVE_FLUID_TANK.getDefaultState());
        set(level, l.at(11, 1, -2), AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.WEST));
        set(level, l.at(11, 2, -2), cog(Direction.Axis.X));
        set(level, l.at(12, 2, -2), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(DirectionalKineticBlock.FACING, Direction.WEST));
        // ---- liquid out of the tower: pump on its north face (own motor) ----
        set(level, l.at(9, 1, -4), AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.NORTH));
        set(level, l.at(9, 2, -4), cog(Direction.Axis.Z));
        set(level, l.at(9, 2, -5), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(DirectionalKineticBlock.FACING, Direction.SOUTH));
        // ---- reservoir -> pump (own motor) -> expander ----
        set(level, l.at(10, 0, -6), AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.EAST));
        set(level, l.at(10, 0, -7), cog(Direction.Axis.X));
        set(level, l.at(9, 0, -7), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(DirectionalKineticBlock.FACING, Direction.EAST));

        // ---- pipes ----
        List<BlockPos> pipes = new ArrayList<>();
        // gas: compressor -> tower west face
        int[][] gas = {{4, 0, -3}, {4, 1, -3}, {5, 1, -3}, {6, 1, -3}, {7, 1, -3}, {8, 1, -3}};
        // Frosium: tower pump -> frost tank
        int[][] liquid = {{9, 1, -5}, {9, 0, -5}};
        // reservoir pump -> expander west face (its back)
        int[][] toExpander = {{11, 0, -6}, {12, 0, -6}, {13, 0, -6}, {13, 0, -5}, {13, 0, -4}, {13, 0, -3},
                {13, 0, -2}, {13, 0, -1}, {14, 0, -1}, {15, 0, -1}, {15, 0, -2}};
        // gas back: expander -> backs of the two vents
        int[][] back = {{17, 0, -2}, {17, 0, -3}, {17, 0, -4}, {17, 0, -5}, {17, 1, -5}, {16, 1, -5}};
        for (int[][] line : new int[][][]{gas, liquid, toExpander, back})
            for (int[] p : line)
                pipes.add(l.at(p[0], p[1], p[2]));
        for (BlockPos p : pipes)
            set(level, p, AllBlocks.FLUID_PIPE.getDefaultState());
        // connect them once all are placed (twice: a pipe only sees its neighbours' final shape on the second pass)
        for (int pass = 0; pass < 2; pass++)
            for (BlockPos p : pipes)
                level.setBlock(p, Block.updateFromNeighbourShapes(level.getBlockState(p), level, p), Block.UPDATE_ALL);

        // ---- settings ----
        if (level.getBlockEntity(l.at(12, 1, -2)) instanceof CreativeFluidTankBlockEntity tank
                && tank.getTankInventory() instanceof CreativeFluidTankBlockEntity.CreativeSmartFluidTank creative)
            com.frostworks.platform.Loader.setCreativeFluid(creative, Fluids.WATER, 1000);
        if (level.getBlockEntity(l.compressor()) instanceof CompressorBlockEntity c)
            c.getBehaviour(ScrollValueBehaviour.TYPE).setValue(compressorSu);
        setShare(level, l, sharePercent);
        setMotor(level, l.motor(), motorRpm);
        setMotor(level, l.at(12, 2, -2), 32);
        setMotor(level, l.at(9, 2, -5), 32);
        setMotor(level, l.at(9, 0, -7), 32);
        return l;
    }

    public static void setShare(ServerLevel level, Layout l, int percent) {
        if (level.getBlockEntity(l.expander()) instanceof com.frostworks.content.expander.ExpanderBlockEntity e)
            e.getBehaviour(ScrollValueBehaviour.TYPE).setValue(percent);
    }

    public static void setMotor(ServerLevel level, BlockPos pos, int rpm) {
        if (level.getBlockEntity(pos) instanceof CreativeMotorBlockEntity motor)
            motor.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(rpm);
    }

    private static BlockState shaft(Direction.Axis axis) {
        return AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    private static BlockState cog(Direction.Axis axis) {
        return AllBlocks.COGWHEEL.getDefaultState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }
}
