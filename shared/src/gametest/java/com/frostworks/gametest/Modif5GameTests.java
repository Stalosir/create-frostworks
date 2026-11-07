package com.frostworks.gametest;

import com.frostworks.Frostworks;
import com.frostworks.content.compressor.CompressorBlock;
import com.frostworks.content.compressor.CompressorGeometry;
import com.frostworks.registry.FwBlocks;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** The compressor on our model. */
public final class Modif5GameTests {
    private Modif5GameTests() {}

    private static final String SMALL = "empty_16x10x16";

    /**
     * Pipe only on the front (+Y of the model), shaft only on the back (-Y of the model), no cogwheel for its
     * neighbours; the black mark on the face the model's north turns to (as the blockstate turns the model).
     */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void compressorConnectsFrontAndBackOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(5, 3, 5));
        for (Direction facing : Direction.values()) {
            BlockState state = FwBlocks.COMPRESSOR.getDefaultState().setValue(CompressorBlock.FACING, facing);
            level.setBlock(pos, state, Block.UPDATE_ALL);
            CompressorBlock block = (CompressorBlock) state.getBlock();
            for (Direction side : Direction.values()) {
                boolean pipe = com.frostworks.FwCaps.fluidAt(level, pos, side) != null;
                helper.assertTrue(pipe == (side == facing), "facing " + facing + ": fluid handler on " + side + " " + pipe);
                boolean shaft = block.hasShaftTowards(level, pos, state, side);
                helper.assertTrue(shaft == (side == facing.getOpposite()),
                        "facing " + facing + ": shaft on " + side + " " + shaft);
            }
            helper.assertTrue(!ICogWheel.isSmallCog(state), "facing " + facing + ": meshes with cogwheels on its sides");
        }
        // the model turns its outlet (+Y) to facing; the mark as high as it can: on top when horizontal
        for (Direction facing : Direction.values())
            helper.assertTrue(CompressorGeometry.toWorld(Direction.UP, facing) == facing, "outlet facing " + facing);
        helper.assertTrue(CompressorGeometry.markFace(Direction.UP) == Direction.NORTH, "mark facing up");
        helper.assertTrue(CompressorGeometry.markFace(Direction.NORTH) == Direction.UP, "mark facing north");
        helper.assertTrue(CompressorGeometry.markFace(Direction.EAST) == Direction.UP, "mark facing east");
        helper.assertTrue(CompressorGeometry.markFace(Direction.DOWN) == Direction.SOUTH, "mark facing down");
        helper.succeed();
    }

    /** In creative, breaking the upper half of the strip door drops nothing. */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void creativeBreakDropsNoDoor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(5, 1, 5));
        level.setBlock(pos, FwBlocks.INSULATED_DOOR.getDefaultState(), Block.UPDATE_ALL);
        FwBlocks.INSULATED_DOOR.get().setPlacedBy(level, pos, level.getBlockState(pos), null,
                net.minecraft.world.item.ItemStack.EMPTY);
        // what a creative player breaking the upper half does: the block hears it first, then is removed
        net.minecraft.world.entity.player.Player player = MockPlayers.creative(helper);
        BlockState upper = level.getBlockState(pos.above());
        upper.getBlock().playerWillDestroy(level, pos.above(), upper, player);
        level.removeBlock(pos.above(), false);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir(), "door still there");
            helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(3)).isEmpty(), "the door dropped");
            helper.succeed();
        });
    }
}
