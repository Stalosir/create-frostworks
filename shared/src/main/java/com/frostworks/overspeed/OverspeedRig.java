package com.frostworks.overspeed;

import com.frostworks.registry.FwBlocks;
import com.frostworks.room.TestRoomBuilder;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Test rig for . Positions are relative to the corner of a test room shell (8 x 6 x 6, interior x 1..6, y 1..4,
 * z 1..4):
 * <pre>
 * outside   creative motor (-2,3,1) -> stressometer (-1,3,1) -> shaft passage in the west wall (0,3,1)
 * inside    large cogwheel (1,3,1) -> cogwheel (1,4,2): ratio x2 -> shaft (2,4,2) -> gearbox (3,4,2)
 *           gearbox -> down: vertical cogwheel (3,3,2) -> meshes with the mechanical mixer (4,3,2), basin (4,1,2)
 *           gearbox -> east: speedometer (4,4,2), dial facing south -> mechanical saw (5,4,2), facing up
 * (a mixer has no shaft input: it is driven as a vertical cogwheel)
 * </pre>
 * The motor runs at half the wanted speed: 160 rpm gives 320 rpm on the machines, 128 gives 256.
 * The witness rig is the same without the room.
 */
public final class OverspeedRig {
    private OverspeedRig() {}

    public record Layout(BlockPos corner) {
        public BlockPos motor() { return corner.offset(-2, 3, 1); }
        public BlockPos gauge() { return corner.offset(-1, 3, 1); }
        public BlockPos passage() { return corner.offset(0, 3, 1); }
        public BlockPos largeCog() { return corner.offset(1, 3, 1); }
        public BlockPos smallCog() { return corner.offset(1, 4, 2); }
        public BlockPos shaft1() { return corner.offset(2, 4, 2); }
        public BlockPos gearbox() { return corner.offset(3, 4, 2); }
        public BlockPos mixerCog() { return corner.offset(3, 3, 2); }
        public BlockPos mixer() { return corner.offset(4, 3, 2); }
        public BlockPos basin() { return corner.offset(4, 1, 2); }
        public BlockPos speedometer() { return corner.offset(4, 4, 2); }
        public BlockPos saw() { return corner.offset(5, 4, 2); }
        public BlockPos interior() { return corner.offset(2, 1, 3); }
        public BlockPos eastWall() { return corner.offset(TestRoomBuilder.SIZE_X - 1, 2, 3); }
    }

    /** Room + rig. The room gets 6 vents with free gas: it becomes COLD after the usual search delay. */
    public static Layout buildInRoom(ServerLevel level, BlockPos corner) {
        TestRoomBuilder.build(level, corner, 6);
        Layout layout = new Layout(corner);
        placeChain(level, layout);
        return layout;
    }

    /** Same rig in the open (witness), the area is cleared first. */
    public static Layout buildWitness(ServerLevel level, BlockPos corner) {
        for (int x = -2; x < TestRoomBuilder.SIZE_X; x++)
            for (int y = 0; y < TestRoomBuilder.SIZE_Y; y++)
                for (int z = 0; z < TestRoomBuilder.SIZE_Z; z++)
                    level.setBlock(corner.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        Layout layout = new Layout(corner);
        placeChain(level, layout);
        return layout;
    }

    private static BlockState shaftX() {
        return AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
    }

    private static void placeChain(ServerLevel level, Layout l) {
        int flags = Block.UPDATE_ALL;
        // Machines first, then the transmission from the machines back to the motor, so nothing turns before the
        // chain is complete (the motor starts at 16 rpm).
        level.setBlock(l.basin(), AllBlocks.BASIN.getDefaultState(), flags);
        level.setBlock(l.mixer(), AllBlocks.MECHANICAL_MIXER.getDefaultState(), flags);
        level.setBlock(l.saw(), AllBlocks.MECHANICAL_SAW.getDefaultState()
                .setValue(DirectionalAxisKineticBlock.FACING, Direction.UP)
                .setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true), flags);
        level.setBlock(l.mixerCog(), AllBlocks.COGWHEEL.getDefaultState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y), flags);
        level.setBlock(l.speedometer(), AllBlocks.SPEEDOMETER.getDefaultState()
                .setValue(DirectionalAxisKineticBlock.FACING, Direction.SOUTH)
                .setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true), flags);
        level.setBlock(l.gearbox(), AllBlocks.GEARBOX.getDefaultState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z), flags);
        level.setBlock(l.shaft1(), shaftX(), flags);
        level.setBlock(l.smallCog(), AllBlocks.COGWHEEL.getDefaultState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), flags);
        level.setBlock(l.largeCog(), AllBlocks.LARGE_COGWHEEL.getDefaultState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), flags);
        level.setBlock(l.passage(), FwBlocks.SHAFT_PASSAGE.getDefaultState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), flags);
        level.setBlock(l.gauge(), AllBlocks.STRESSOMETER.getDefaultState()
                .setValue(DirectionalAxisKineticBlock.FACING, Direction.UP)
                .setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true), flags);
        level.setBlock(l.motor(), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(DirectionalBlock.FACING, Direction.EAST), flags);
    }

    /** Sets the motor speed (the machines turn at twice this speed). */
    public static boolean setMotorSpeed(ServerLevel level, Layout layout, int rpm) {
        if (level.getBlockEntity(layout.motor()) instanceof CreativeMotorBlockEntity motor) {
            motor.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(rpm);
            return true;
        }
        return false;
    }

    /** Corner of a room built in front of the player, and of the witness rig next to it (on the right). */
    public static BlockPos witnessCorner(BlockPos roomCorner) {
        return roomCorner.offset(0, 0, TestRoomBuilder.SIZE_Z + 3);
    }
}
