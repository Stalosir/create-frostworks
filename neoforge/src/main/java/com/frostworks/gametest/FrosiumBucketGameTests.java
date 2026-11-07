package com.frostworks.gametest;

import com.frostworks.Frostworks;
import com.frostworks.registry.FwBlocks;
import com.frostworks.registry.FwFluids;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Frosium bucket: into a frost tank and out again, refused by Create's tank, emptied by an item drain. */
@GameTestHolder(Frostworks.MODID)
@PrefixGameTestTemplate(false)
public final class FrosiumBucketGameTests {
    private FrosiumBucketGameTests() {}

    private static final String SMALL = "empty_16x10x16";

    private static ItemStack bucket() {
        return new ItemStack(FwFluids.frosiumBucket());
    }

    /** Right click with the bucket, in creative: Create's tanks take a bucket by hand in creative only (in survival, an
     * item drain or a pump does it), and keep the bucket in the hand. */
    private static void click(GameTestHelper helper, BlockPos pos, ItemStack held) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        level.getBlockState(pos).useItemOn(held, level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static int amount(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getBlockEntity(pos) instanceof FluidTankBlockEntity tank
                ? tank.getControllerBE().getTankInventory().getFluidAmount() : -1;
    }

    /** A frost tank takes the bucket's 1000 mB and an empty bucket can be filled with Frosium; Create's tank refuses it. */
    @GameTest(template = SMALL, timeoutTicks = 40)
    public static void theBucketGoesIntoAFrostTankOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos frost = helper.absolutePos(new BlockPos(2, 1, 2)), create = helper.absolutePos(new BlockPos(6, 1, 2));
        level.setBlock(frost, FwBlocks.RESERVOIR.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(create, AllBlocks.FLUID_TANK.getDefaultState(), Block.UPDATE_ALL);
        helper.runAfterDelay(5, () -> { // the tanks form on their first tick
            click(helper, frost, bucket());
            helper.assertTrue(amount(helper, frost) == 1000, "frost tank: " + amount(helper, frost) + " mB");
            // an empty bucket filled with Frosium, as a spout does (in creative, a tank does not lose what it gives)
            var frosium = new net.neoforged.neoforge.fluids.FluidStack(FwFluids.frosium(), 1000);
            ItemStack filled = com.simibubi.create.content.fluids.transfer.GenericItemFilling.fillItem(level,
                    1000, new ItemStack(Items.BUCKET), frosium);
            helper.assertTrue(filled.is(FwFluids.frosiumBucket()), "an empty bucket filled with Frosium gives " + filled);

            click(helper, create, bucket());
            helper.assertTrue(amount(helper, create) == 0, "Create's tank took " + amount(helper, create) + " mB");
            helper.succeed();
        });
    }

    /** The bucket pours Frosium out in the world, and a pig in it freezes as in powder snow. */
    @GameTest(template = SMALL, timeoutTicks = 200)
    public static void pouredFrosiumFreezesWhatIsInIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var bucket = (net.minecraft.world.item.BucketItem) FwFluids.frosiumBucket();
        helper.assertTrue(bucket.emptyContents(player, level, at, null, bucket()), "not poured");
        helper.assertTrue(level.getFluidState(at).getType().isSame(FwFluids.frosium()) && level.getFluidState(at).isSource(),
                "in the world: " + level.getFluidState(at));
        var pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG, new BlockPos(4, 1, 4));
        helper.succeedWhen(() -> helper.assertTrue(pig.getTicksFrozen() > 40, "frozen " + pig.getTicksFrozen() + " ticks"));
    }

    /**
     * The level drawn in a frost tank comes from its own capacity, not Create's: with Create's fluidTankCapacity at 800
     * (our setting), a half-full frost tank, read again as the client reads it, is drawn half full.
     */
    @GameTest(template = SMALL, timeoutTicks = 40, batch = "create_tank_capacity")
    public static void theLevelDrawnIsTheFrostTanksOwn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos, FwBlocks.RESERVOIR.get().defaultBlockState(), Block.UPDATE_ALL);
        var capacity = com.simibubi.create.infrastructure.config.AllConfigs.server().fluids.fluidTankCapacity;
        int before = capacity.get();
        helper.runAfterDelay(5, () -> {
            capacity.set(800);
            try {
                var tank = ((FluidTankBlockEntity) level.getBlockEntity(pos)).getControllerBE();
                tank.getTankInventory().fill(new net.neoforged.neoforge.fluids.FluidStack(FwFluids.frosium(),
                        tank.getTankInventory().getCapacity() / 2), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                tank.loadWithComponents(tank.saveWithoutMetadata(level.registryAccess()), level.registryAccess());
                float drawn = tank.getFluidLevel().getChaseTarget();
                helper.assertTrue(Math.abs(drawn - .5f) < .01f, "drawn at " + drawn + " of the height");
            } finally {
                capacity.set(before);
            }
            helper.succeed();
        });
    }

    /** An item drain empties the bucket as any bucket: 1000 mB of Frosium, an empty bucket left. */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void anItemDrainEmptiesTheBucket(GameTestHelper helper) {
        var result = GenericItemEmptying.emptyItem(helper.getLevel(), bucket(), false);
        helper.assertTrue(FwFluids.isFrosium(result.getFirst()) && result.getFirst().getAmount() == 1000,
                "drained: " + result.getFirst().getFluid() + " " + result.getFirst().getAmount() + " from " + bucket()
                        + ", capability " + bucket().getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM)
                        + ", content " + ((net.minecraft.world.item.BucketItem) bucket().getItem()).content);
        helper.assertTrue(result.getSecond().is(Items.BUCKET), "left: " + result.getSecond());
        helper.succeed();
    }
}
