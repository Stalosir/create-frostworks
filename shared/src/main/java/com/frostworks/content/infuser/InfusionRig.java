package com.frostworks.content.infuser;

import com.frostworks.content.compressor.CompressorBlock;
import com.frostworks.registry.FwBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Test rig of {@code /frostworks infusion rig}, built from a corner (x to the east, z to the south):
 * <pre>
 * y 1: creative motor (0) -> shaft (1) -> compressor (2, front east) -> pipe (3) ; depot (4) ; chest (5)
 * y 2: pipe (3)
 * y 3: pipe (3) -> west face of the infuser (4, facing south: its inlet, on its right, is west; shaft along Z), its
 *      own creative motor north of it (4,3,-1)
 * </pre>
 * The depot is two blocks under the infuser, as Create's press works. The chest holds 16 iron sheets and 16 dried
 * kelp. Nothing is started: put an item on the depot.
 */
public final class InfusionRig {
    private InfusionRig() {}

    public static final int RPM = 64;

    public static BlockPos infuser(BlockPos corner) {
        return corner.offset(4, 3, 0);
    }

    public static BlockPos depot(BlockPos corner) {
        return corner.offset(4, 1, 0);
    }

    public static void build(ServerLevel level, BlockPos corner) {
        // clear the space first (6 x 4 x 2)
        for (BlockPos p : BlockPos.betweenClosed(corner.offset(0, 1, -1), corner.offset(5, 4, 0)))
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        // compressor line
        set(level, corner.offset(0, 1, 0), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        set(level, corner.offset(1, 1, 0), AllBlocks.SHAFT.getDefaultState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        set(level, corner.offset(2, 1, 0), FwBlocks.COMPRESSOR.getDefaultState()
                .setValue(CompressorBlock.FACING, Direction.EAST));
        // gas up to the infuser's west face
        for (int y = 1; y <= 3; y++)
            set(level, corner.offset(3, y, 0), AllBlocks.FLUID_PIPE.getDefaultState());
        for (int y = 1; y <= 3; y++) {
            BlockPos p = corner.offset(3, y, 0);
            level.setBlock(p, Block.updateFromNeighbourShapes(level.getBlockState(p), level, p), Block.UPDATE_ALL);
        }
        // infuser over the depot, its own motor
        set(level, depot(corner), AllBlocks.DEPOT.getDefaultState());
        set(level, infuser(corner), FwBlocks.CRYO_INFUSER.getDefaultState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)); // its inlet (right) is west
        set(level, infuser(corner).north(), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(DirectionalKineticBlock.FACING, Direction.SOUTH));
        for (BlockPos motor : new BlockPos[]{corner.offset(0, 1, 0), infuser(corner).north()})
            if (level.getBlockEntity(motor) instanceof CreativeMotorBlockEntity m)
                m.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(RPM);
        // the chest with what to infuse
        set(level, corner.offset(5, 1, 0), Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(corner.offset(5, 1, 0)) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(AllItems.IRON_SHEET.get(), 16));
            chest.setItem(1, new ItemStack(Items.DRIED_KELP, 16));
        }
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }
}
