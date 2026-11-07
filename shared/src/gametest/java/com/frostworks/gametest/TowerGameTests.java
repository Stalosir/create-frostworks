package com.frostworks.gametest;

import com.frostworks.Frostworks;
import com.frostworks.content.column.ColumnSegmentBlock;
import com.frostworks.content.column.TowerBlock;
import com.frostworks.content.column.TowerBlockEntity;
import com.frostworks.registry.FwBlocks;
import com.frostworks.registry.FwFluids;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** The distillation tower: a layer of column bases, layers of column segments above. */
public final class TowerGameTests {
    private TowerGameTests() {}

    private static final String SMALL = "empty_16x10x16";

    /** A 2 x 2 layer of one block, north-west block at {@code corner}. */
    static void layer(ServerLevel level, BlockPos corner, BlockState block) {
        for (int x = 0; x < 2; x++)
            for (int z = 0; z < 2; z++)
                level.setBlock(corner.offset(x, 0, z), block, Block.UPDATE_ALL);
    }

    /** A tower: a layer of bases at {@code corner}, then {@code layers - 1} layers of segments. */
    static void tower(ServerLevel level, BlockPos corner, int layers) {
        layer(level, corner, FwBlocks.COLUMN_BASE.getDefaultState());
        for (int y = 1; y < layers; y++)
            layer(level, corner.above(y), FwBlocks.COLUMN_SEGMENT.getDefaultState());
    }

    private static IFluidHandler cap(ServerLevel level, BlockPos pos, Direction side) {
        return com.frostworks.FwCaps.fluidAt(level, pos, side);
    }

    /** Not part of a valid tower: no storage, no fluid handler on any face, no base or top state. */
    private static boolean inert(ServerLevel level, BlockPos pos) {
        TowerBlockEntity t = TowerBlock.at(level, pos);
        if (t == null || t.isValid())
            return false;
        for (Direction d : Direction.values())
            if (cap(level, pos, d) != null)
                return false;
        BlockState state = level.getBlockState(pos);
        return !TowerBlock.isBase(state) && !(state.getBlock() instanceof ColumnSegmentBlock
                && state.getValue(ColumnSegmentBlock.TOP));
    }

    /** A belt alone does not put ice into a base (by design): a funnel on its side, or an arm, does. */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void aBeltAloneDoesNotFeedTheBase(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.getLevel().setBlockAndUpdate(base, FwBlocks.COLUMN_BASE.getDefaultState());
        helper.assertTrue(com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(helper.getLevel(),
                base, com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour.TYPE) == null,
                "a belt can still insert into the base");
        helper.succeed();
    }

    /** The schematic cannon asks a frost tank for a column block (what it is made of and drops), not the column itself. */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void theCannonAsksAFrostTankForTheColumn(GameTestHelper helper) {
        var needed = com.simibubi.create.content.schematics.requirement.ItemRequirement
                .of(FwBlocks.COLUMN_SEGMENT.getDefaultState(), null).getRequiredItems();
        helper.assertTrue(needed.size() == 1 && needed.get(0).stack.is(FwBlocks.RESERVOIR.get().asItem())
                && needed.get(0).stack.getCount() == 1, "the cannon asks for " + needed.stream().map(r -> r.stack).toList());
        helper.succeed();
    }

    @GameTest(template = SMALL, timeoutTicks = 60)
    public static void baseAndTwoSegmentsForm(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos c = helper.absolutePos(new BlockPos(4, 1, 4));
        tower(level, c, 3);
        helper.succeedWhen(() -> {
            TowerBlockEntity t = TowerBlock.at(level, c);
            helper.assertTrue(t != null && t.isController() && t.layers() == 3, "not a 3-layer tower yet");
            for (int x = 0; x < 2; x++)
                for (int z = 0; z < 2; z++) {
                    helper.assertTrue(TowerBlock.isBase(level.getBlockState(c.offset(x, 0, z))), "base " + x + z);
                    helper.assertTrue(!level.getBlockState(c.offset(x, 1, z)).getValue(ColumnSegmentBlock.TOP),
                            "middle " + x + z);
                    helper.assertTrue(level.getBlockState(c.offset(x, 2, z)).getValue(ColumnSegmentBlock.TOP),
                            "top " + x + z);
                    helper.assertTrue(TowerBlock.at(level, c.offset(x, 2, z)).getController().equals(c), "controller");
                }
            helper.assertTrue(level.getBlockState(c.offset(1, 0, 1)).getValue(TowerBlock.CORNER)
                    == TowerBlock.Corner.SOUTH_EAST, "corner of the south-east block");
        });
    }

    /** Two layers of frost tanks on the bases turn into two layers of column: a tower of 3 layers. */
    @GameTest(template = SMALL, timeoutTicks = 60)
    public static void frostTanksOnTheBaseBecomeColumn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos c = helper.absolutePos(new BlockPos(4, 1, 4));
        tower(level, c, 1);
        layer(level, c.above(), FwBlocks.RESERVOIR.getDefaultState());
        layer(level, c.above(2), FwBlocks.RESERVOIR.getDefaultState());
        helper.succeedWhen(() -> {
            for (int x = 0; x < 2; x++)
                for (int z = 0; z < 2; z++)
                    for (int y = 1; y <= 2; y++)
                        helper.assertTrue(level.getBlockState(c.offset(x, y, z)).getBlock() instanceof ColumnSegmentBlock,
                                "column at " + x + y + z);
            TowerBlockEntity t = TowerBlock.at(level, c);
            helper.assertTrue(t != null && t.layers() == 3, "not a 3-layer tower yet");
            helper.assertTrue(level.getBlockState(c.above()).getValue(ColumnSegmentBlock.PART) == ColumnSegmentBlock.Part.BOTTOM
                    && level.getBlockState(c.above(2)).getValue(ColumnSegmentBlock.PART) == ColumnSegmentBlock.Part.TOP,
                    "bottom and top models");
        });
    }

    /** Breaking a block of the lower layer of column turns that layer and the one above back into frost tanks. */
    @GameTest(template = SMALL, timeoutTicks = 80)
    public static void brokenColumnTurnsBackIntoFrostTanks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos c = helper.absolutePos(new BlockPos(4, 1, 4));
        tower(level, c, 3);
        helper.runAfterDelay(10, () -> {
            level.destroyBlock(c.offset(1, 1, 1), false);
            helper.succeedWhen(() -> {
                for (int x = 0; x < 2; x++)
                    for (int z = 0; z < 2; z++)
                        for (int y = 1; y <= 2; y++) {
                            if (x == 1 && z == 1 && y == 1)
                                continue;
                            helper.assertTrue(level.getBlockState(c.offset(x, y, z)).is(FwBlocks.RESERVOIR.get()),
                                    "frost tank again at " + x + y + z);
                        }
                helper.assertTrue(TowerBlock.isBase(level.getBlockState(c)), "the base stays");
            });
        });
    }

    /** Four frost tanks in hand, a click on top of the bases: the whole layer is laid, and becomes column. */
    @GameTest(template = SMALL, timeoutTicks = 60)
    public static void oneClickLaysAWholeLayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos c = helper.absolutePos(new BlockPos(4, 1, 4));
        tower(level, c, 1);
        net.minecraft.world.entity.player.Player player = MockPlayers.survival(helper);
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(FwBlocks.RESERVOIR.get(), 5);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        helper.runAfterDelay(5, () -> {
            BlockPos clicked = c.offset(1, 0, 1);
            stack.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(clicked).add(0, .5, 0),
                            Direction.UP, clicked, false)));
            helper.assertTrue(stack.getCount() == 1, "4 frost tanks used, " + stack.getCount() + " left");
            helper.succeedWhen(() -> {
                for (int x = 0; x < 2; x++)
                    for (int z = 0; z < 2; z++)
                        helper.assertTrue(level.getBlockState(c.offset(x, 1, z)).getBlock() instanceof ColumnSegmentBlock,
                                "column at " + x + z);
            });
        });
    }

    /** Create's own tank refuses Frosium, and still takes water. */
    @GameTest(template = SMALL, timeoutTicks = 40)
    public static void createTankRefusesOurLiquids(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos p = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(p, AllBlocks.FLUID_TANK.getDefaultState(), Block.UPDATE_ALL);
        helper.runAfterDelay(5, () -> {
            IFluidHandler h = com.frostworks.FwCaps.fluidAt(level, p, Direction.UP);
            helper.assertTrue(h != null, "no fluid handler");
            IFluidHandler.FluidAction sim = IFluidHandler.FluidAction.SIMULATE;
            helper.assertTrue(h.fill(new FluidStack(FwFluids.frosium(), 1000), sim) == 0, "took Frosium");
            helper.assertTrue(h.fill(new FluidStack(Fluids.WATER, 1000), sim) == 1000, "refused water");
            helper.succeed();
        });
    }

    /** A base alone is a tower of one layer, with everything a base has but no room: only the column holds. */
    @GameTest(template = SMALL, timeoutTicks = 60)
    public static void baseAloneIsAValidTower(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos c = helper.absolutePos(new BlockPos(4, 1, 4));
        tower(level, c, 1);
        helper.succeedWhen(() -> {
            TowerBlockEntity t = TowerBlock.at(level, c);
            helper.assertTrue(t.layers() == 1 && t.isValid(), "not a tower of one layer");
            IFluidHandler h = cap(level, c, Direction.NORTH);
            helper.assertTrue(h != null && h.getTankCapacity(0) == 0, "the bases hold nothing (modification 6)");
        });
    }

    /**
     * Every group that is not a layer of bases topped by full layers of segments stays inert: a base above layer 1,
     * segments on the ground, width 1, an L. A segment beside a base and an incomplete layer stay inert too, the full
     * base next to them stays a tower.
     */
    @GameTest(template = SMALL, timeoutTicks = 80)
    public static void invalidGroupsAreInert(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState base = FwBlocks.COLUMN_BASE.getDefaultState(), segment = FwBlocks.COLUMN_SEGMENT.getDefaultState();
        // a) segments on the ground with a layer of bases above them
        BlockPos a = helper.absolutePos(new BlockPos(1, 1, 1));
        layer(level, a, segment);
        layer(level, a.above(), base);
        // b) a layer of segments alone on the ground
        BlockPos b = helper.absolutePos(new BlockPos(5, 1, 1));
        layer(level, b, segment);
        // c) width 1: a base with two segments on it
        BlockPos w = helper.absolutePos(new BlockPos(9, 1, 1));
        level.setBlock(w, base, Block.UPDATE_ALL);
        level.setBlock(w.above(), segment, Block.UPDATE_ALL);
        level.setBlock(w.above(2), segment, Block.UPDATE_ALL);
        // d) an L of three bases
        BlockPos d = helper.absolutePos(new BlockPos(12, 1, 1));
        for (BlockPos p : new BlockPos[]{d, d.east(), d.south()})
            level.setBlock(p, base, Block.UPDATE_ALL);
        // e) a full base with a segment beside it
        BlockPos e = helper.absolutePos(new BlockPos(1, 1, 6));
        layer(level, e, base);
        level.setBlock(e.offset(2, 0, 0), segment, Block.UPDATE_ALL);
        // f) a full base with an incomplete layer of segments (3 of 4) on it
        BlockPos f = helper.absolutePos(new BlockPos(6, 1, 6));
        layer(level, f, base);
        for (BlockPos p : new BlockPos[]{f.above(), f.above().east(), f.above().south()})
            level.setBlock(p, segment, Block.UPDATE_ALL);
        helper.runAfterDelay(30, () -> {
            for (int x = 0; x < 2; x++)
                for (int z = 0; z < 2; z++) {
                    helper.assertTrue(inert(level, a.offset(x, 0, z)) && inert(level, a.offset(x, 1, z)),
                            "a) segments under bases not inert");
                    helper.assertTrue(inert(level, b.offset(x, 0, z)), "b) segments on the ground not inert");
                }
            for (int y = 0; y < 3; y++)
                helper.assertTrue(inert(level, w.above(y)), "c) width 1 not inert at " + y);
            for (BlockPos p : new BlockPos[]{d, d.east(), d.south()})
                helper.assertTrue(inert(level, p), "d) L not inert");
            helper.assertTrue(inert(level, e.offset(2, 0, 0)), "e) segment beside a base not inert");
            helper.assertTrue(TowerBlock.at(level, e).isValid() && TowerBlock.at(level, e).layers() == 1,
                    "e) the base next to it is not a tower any more");
            for (BlockPos p : new BlockPos[]{f.above(), f.above().east(), f.above().south()})
                helper.assertTrue(inert(level, p), "f) incomplete layer not inert");
            helper.assertTrue(TowerBlock.at(level, f).isValid() && TowerBlock.at(level, f).layers() == 1,
                    "f) the base under it is not a tower of one layer");
            helper.succeed();
        });
    }

    /** At most 8 layers: a base and 8 layers of segments make a tower of 8, the 9th layer stays inert. */
    @GameTest(template = SMALL, timeoutTicks = 80)
    public static void eightLayersAtMost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos c = helper.absolutePos(new BlockPos(4, 0, 4));
        tower(level, c, 9);
        helper.runAfterDelay(30, () -> {
            TowerBlockEntity t = TowerBlock.at(level, c);
            helper.assertTrue(t.isValid() && t.layers() == 8, "layers " + t.layers());
            helper.assertTrue(level.getBlockState(c.above(7)).getValue(ColumnSegmentBlock.TOP), "layer 8 is not the top");
            for (int x = 0; x < 2; x++)
                for (int z = 0; z < 2; z++)
                    helper.assertTrue(inert(level, c.offset(x, 8, z)), "layer 9 not inert");
            helper.succeed();
        });
    }

    /** Base + 1 layer of column = 4 blocks x 4000 mB (the bases hold nothing), half gas half water; nothing else. */
    @GameTest(template = SMALL, timeoutTicks = 60)
    public static void capacityAndShare(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos c = helper.absolutePos(new BlockPos(4, 1, 4));
        tower(level, c, 2);
        helper.runAfterDelay(10, () -> {
            IFluidHandler h = cap(level, c, Direction.WEST);
            helper.assertTrue(h != null, "no fluid handler on the west face of the base");
            // The shares of the recipe (no fixed tanks any more), seen by what goes in
            var sim = IFluidHandler.FluidAction.SIMULATE;
            helper.assertTrue(h.fill(FwFluids.gas(20000), sim) == 8000 && h.fill(new FluidStack(Fluids.WATER, 20000), sim) == 8000,
                    "rooms " + h.fill(FwFluids.gas(20000), sim) + " / " + h.fill(new FluidStack(Fluids.WATER, 20000), sim));
            int gas = h.fill(FwFluids.gas(20000), IFluidHandler.FluidAction.EXECUTE);
            int water = h.fill(new FluidStack(Fluids.WATER, 20000), IFluidHandler.FluidAction.EXECUTE);
            int lava = h.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);
            int air = h.fill(new FluidStack(FwFluids.frosium(), 1000), IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(gas == 8000 && water == 8000, "filled gas " + gas + " water " + water);
            helper.assertTrue(lava == 0 && air == 0, "other fluids accepted: lava " + lava + " Frosium " + air);
            IFluidHandler other = cap(level, c.offset(1, 0, 1), Direction.DOWN);
            helper.assertTrue(other != null && other.getFluidInTank(0).getAmount() == 8000, "one storage for all");
            helper.succeed();
        });
    }

    /** A pipe on any of the 12 outer or lower faces of the base connects; on a segment, on top, inside: nothing. */
    @GameTest(template = SMALL, timeoutTicks = 80)
    public static void pipesOnTheTwelveFacesOfTheBase(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos c = helper.absolutePos(new BlockPos(5, 2, 5));
        tower(level, c, 2);
        java.util.List<BlockPos[]> faces = new java.util.ArrayList<>(); // {tower block, pipe}
        for (TowerBlock.Corner corner : TowerBlock.Corner.values()) {
            BlockPos base = c.offset(corner.dx, 0, corner.dz);
            faces.add(new BlockPos[]{base, base.relative(corner.outA)});
            faces.add(new BlockPos[]{base, base.relative(corner.outB)});
            faces.add(new BlockPos[]{base, base.below()});
        }
        helper.runAfterDelay(10, () -> {
            for (BlockPos[] f : faces)
                level.setBlock(f[1], AllBlocks.FLUID_PIPE.getDefaultState(), Block.UPDATE_ALL);
            // one pipe on top of the tower and one beside the segment layer
            level.setBlock(c.above(2), AllBlocks.FLUID_PIPE.getDefaultState(), Block.UPDATE_ALL);
            level.setBlock(c.above().west(), AllBlocks.FLUID_PIPE.getDefaultState(), Block.UPDATE_ALL);
            for (int pass = 0; pass < 2; pass++)
                for (BlockPos p : new BlockPos[]{c.above(2), c.above().west()})
                    level.setBlock(p, Block.updateFromNeighbourShapes(level.getBlockState(p), level, p),
                            Block.UPDATE_ALL);
        });
        helper.runAfterDelay(20, () -> {
            int n = 0;
            for (BlockPos[] f : faces) {
                Direction towards = Direction.getNearest(f[0].getX() - f[1].getX(), f[0].getY() - f[1].getY(),
                        f[0].getZ() - f[1].getZ());
                helper.assertTrue(cap(level, f[0], towards.getOpposite()) != null,
                        "no fluid handler on face " + towards.getOpposite() + " of " + f[0].toShortString());
                helper.assertTrue(level.getBlockState(f[1]).getValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(towards)),
                        "pipe at " + f[1].toShortString() + " not connected " + towards);
                n++;
            }
            helper.assertTrue(n == 12, "faces " + n);
            helper.assertTrue(!level.getBlockState(c.above(2)).getValue(FluidPipeBlock.PROPERTY_BY_DIRECTION
                    .get(Direction.DOWN)), "a pipe on top connects");
            helper.assertTrue(!level.getBlockState(c.above().west()).getValue(FluidPipeBlock.PROPERTY_BY_DIRECTION
                    .get(Direction.EAST)), "a pipe beside a segment connects");
            helper.assertTrue(cap(level, c, Direction.EAST) == null && cap(level, c, Direction.SOUTH) == null,
                    "inner faces of the north-west base block");
            helper.assertTrue(cap(level, c, Direction.UP) == null, "top face of the base");
            for (Direction d : Direction.values())
                helper.assertTrue(cap(level, c.above(), d) == null, "segment, face " + d);
            helper.succeed();
        });
    }

    /** The liquid made takes its room: of 16000 mB, 2000 of liquid leave 14000 to share, 7000 of gas and 7000 of water. A shaft
     * under the tower turns nothing. */
    @GameTest(template = SMALL, timeoutTicks = 60)
    public static void liquidTakesItsRoomAndNoShaftFromBelow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos c = helper.absolutePos(new BlockPos(5, 2, 5));
        BlockPos motor = c.below();
        level.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalBlock.FACING, Direction.UP),
                Block.UPDATE_ALL);
        if (level.getBlockEntity(motor) instanceof CreativeMotorBlockEntity m)
            m.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(64);
        tower(level, c, 2);
        helper.runAfterDelay(20, () -> {
            TowerBlockEntity t = TowerBlock.at(level, c);
            helper.assertTrue(t.layers() == 2 && t.getSpeed() == 0, "turned from below: " + t.getSpeed());
            t.setLiquidForTest(2000);
            IFluidHandler h = cap(level, c, Direction.WEST);
            var sim = IFluidHandler.FluidAction.SIMULATE;
            helper.assertTrue(h.fill(FwFluids.gas(20000), sim) == 7000 && h.fill(new FluidStack(Fluids.WATER, 20000), sim) == 7000,
                    "rooms " + h.fill(FwFluids.gas(20000), sim) + " / " + h.fill(new FluidStack(Fluids.WATER, 20000), sim));
            helper.succeed();
        });
    }

    /** A shaft on an outer side of any base block drives the whole tower; production uses the inner storage. */
    @GameTest(template = SMALL, timeoutTicks = 200)
    public static void productionFromTheInnerStorage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos c = helper.absolutePos(new BlockPos(5, 2, 5));
        BlockPos motor = c.offset(2, 0, 1); // east of the south-east base block, not beside the controller
        level.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalBlock.FACING,
                Direction.WEST), Block.UPDATE_ALL);
        if (level.getBlockEntity(motor) instanceof CreativeMotorBlockEntity m)
            m.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(64);
        tower(level, c, 2);
        int[] before = new int[2];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(TowerBlock.at(level, c).layers() == 2, "not formed"))
                .thenExecute(() -> {
                    IFluidHandler h = cap(level, c, Direction.WEST);
                    h.fill(FwFluids.gas(8000), IFluidHandler.FluidAction.EXECUTE);
                    h.fill(new FluidStack(Fluids.WATER, 8000), IFluidHandler.FluidAction.EXECUTE);
                    TowerBlock.at(level, c).getControllerBE().stockIceForRig();
                })
                .thenWaitUntil(() -> {
                    TowerBlockEntity t = TowerBlock.at(level, c);
                    for (TowerBlock.Corner corner : TowerBlock.Corner.values())
                        helper.assertTrue(((KineticBlockEntity) level.getBlockEntity(c.offset(corner.dx, 0, corner.dz)))
                                .getSpeed() != 0, "base block " + corner + " does not turn");
                    helper.assertTrue(t.isRunning(), "not running: " + t.stopReason());
                    // 2 layers at 64 rpm (towerReferenceRpm): 2 x 20 mB/s; twice as fast would be twice as much
                    helper.assertTrue(t.gasRate() == 40, "gas rate at 64 rpm: " + t.gasRate());
                })
                .thenExecute(() -> {
                    TowerBlockEntity t = TowerBlock.at(level, c);
                    before[0] = t.gas();
                    before[1] = t.water();
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    TowerBlockEntity t = TowerBlock.at(level, c);
                    helper.assertTrue(t.gas() < before[0] && t.water() < before[1], "inner storage not used: gas "
                            + before[0] + " -> " + t.gas() + ", water " + before[1] + " -> " + t.water());
                    helper.assertTrue(t.frosium() > 0, "no liquid made");
                    helper.assertTrue(t.calculateStressApplied() > 0, "no stress while running");
                    // one impact for the whole tower: the other base blocks (the one on the shaft too) draw nothing
                    for (TowerBlock.Corner corner : TowerBlock.Corner.values())
                        if (corner != TowerBlock.Corner.NORTH_WEST)
                            helper.assertTrue(TowerBlock.at(level, c.offset(corner.dx, 0, corner.dz))
                                    .calculateStressApplied() == 0, "second impact on " + corner);
                    t.fillOutputForTest();
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    TowerBlockEntity t = TowerBlock.at(level, c);
                    helper.assertTrue(!t.isRunning() && t.stopReason() == TowerBlockEntity.Stop.OUTPUT_FULL,
                            "still running with a full output: " + t.stopReason());
                    helper.assertTrue(t.calculateStressApplied() == 0, "draws stress while stopped");
                    int gas = t.gas();
                    helper.runAfterDelay(20, () -> helper.assertTrue(TowerBlock.at(level, c).gas() == gas,
                            "gas used while stopped"));
                })
                .thenIdle(25)
                .thenSucceed();
    }
}
