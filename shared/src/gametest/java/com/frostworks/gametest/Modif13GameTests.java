package com.frostworks.gametest;

import com.frostworks.Frostworks;
import com.frostworks.registry.FwBlocks;
import com.frostworks.registry.FwFluids;
import com.frostworks.registry.FwTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** The cold liquids of the frost tank, the infuser's two inlet holes. */
public final class Modif13GameTests {
    private Modif13GameTests() {}

    private static final String SMALL = "empty_16x10x16";

    private static int fill(GameTestHelper helper, BlockPos pos, FluidStack stack) {
        IFluidHandler tank = com.frostworks.FwCaps.fluidAt(helper.getLevel(), helper.absolutePos(pos),
                Direction.UP);
        helper.assertTrue(tank != null, "no fluid handler at " + pos);
        return tank.fill(stack, IFluidHandler.FluidAction.SIMULATE);
    }

    /**
     * A frost tank takes our Frosium (it is in {@code frostworks:cold_liquid}); water and cold gas stay out.
     */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void frostTankTakesColdLiquidsOnly(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, FwBlocks.RESERVOIR.get().defaultBlockState());
        helper.runAfterDelay(2, () -> {
            FluidStack frosium = new FluidStack(FwFluids.frosium(), 1000);
            helper.assertTrue(frosium.getFluid().defaultFluidState().is(FwTags.COLD_LIQUID), "our Frosium is a cold liquid");
            helper.assertTrue(fill(helper, pos, frosium) == 1000, "Frosium goes in");
            helper.assertTrue(fill(helper, pos, new FluidStack(Fluids.WATER, 1000)) == 0, "water stays out");
            helper.assertTrue(fill(helper, pos, FwFluids.gas(1000)) == 0, "cold gas stays out");
            helper.succeed();
        });
    }

    /**
     * The rule of the frost tank with fake fluids: one in our tag is taken (an add-on's cold liquid), one in no tag
     * is refused.
     */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void frostTankRuleWithFakeFluids(GameTestHelper helper) {
        helper.assertTrue(FwTags.frostTankTakes(tag -> tag.equals(FwTags.COLD_LIQUID)),
                "a fluid in frostworks:cold_liquid only is taken");
        helper.assertTrue(!FwTags.frostTankTakes(tag -> false), "a fluid in no tag is refused");
        helper.assertTrue(!FwTags.frostTankTakes(FluidStack.EMPTY), "nothing is not a cold liquid");
        helper.succeed();
    }

    /**
     * The vent (from behind) and the expander (at its back) take what the frost tank takes, through
     * {@code FwFluids.isLiquid}, which follows the same tags; water stays out.
     */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void ventAndExpanderTakeColdLiquids(GameTestHelper helper) {
        BlockPos vent = new BlockPos(2, 2, 2), expander = new BlockPos(5, 2, 2);
        helper.setBlock(vent, FwBlocks.VENT.get().defaultBlockState()
                .setValue(com.frostworks.content.vent.VentBlock.FACING, Direction.UP));
        helper.setBlock(expander, FwBlocks.EXPANDER.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH));
        helper.runAfterDelay(2, () -> {
            FluidStack frosium = new FluidStack(FwFluids.frosium(), 100);
            helper.assertTrue(FwFluids.isLiquid(frosium) == FwTags.frostTankTakes(frosium),
                    "the vent and expander rule is the frost tank's");
            for (var at : new Object[][]{{vent, Direction.DOWN}, {expander, Direction.SOUTH}}) {
                IFluidHandler h = com.frostworks.FwCaps.fluidAt(helper.getLevel(), helper.absolutePos((BlockPos) at[0]), (Direction) at[1]);
                helper.assertTrue(h != null, "no handler at " + at[0]);
                helper.assertTrue(h.fill(frosium, IFluidHandler.FluidAction.SIMULATE) > 0, at[0] + " takes Frosium");
                helper.assertTrue(h.fill(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.SIMULATE) == 0,
                        at[0] + " refuses water");
            }
            helper.succeed();
        });
    }

    /** The infuser takes gas through its two hole faces (across the shaft) and nowhere else. */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void infuserTakesGasThroughBothHoles(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, FwBlocks.CRYO_INFUSER.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        BlockPos abs = helper.absolutePos(pos);
        for (Direction side : Direction.values()) {
            boolean open = com.frostworks.FwCaps.fluidAt(helper.getLevel(), abs, side) != null;
            boolean hole = side == Direction.EAST || side == Direction.WEST; // the model's X faces when facing north
            helper.assertTrue(open == hole, side + (hole ? " should take gas" : " should take nothing"));
        }
        helper.setBlock(pos, Block.stateById(0));
        helper.succeed();
    }
}
