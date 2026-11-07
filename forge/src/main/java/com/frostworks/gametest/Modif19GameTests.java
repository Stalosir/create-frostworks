package com.frostworks.gametest;

import com.frostworks.Frostworks;
import com.frostworks.FwConfig;
import com.frostworks.content.infuser.CryoInfuserBlockEntity;
import com.frostworks.registry.FwBlocks;
import com.frostworks.registry.FwChilledOres;
import com.frostworks.registry.FwFluids;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.Tags;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** The chilled ores. */
@GameTestHolder(Frostworks.MODID)
@PrefixGameTestTemplate(false)
public final class Modif19GameTests {
    private Modif19GameTests() {}

    private static final String SMALL = "empty_16x10x16";

    private static Item chilled(String base) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Frostworks.id("chilled_" + base));
    }

    // ---- Create's crushing wheels ---------------------------------------------------------------------------------

    /**
     * Two crushing wheels (axis Z) at x 4 and x 6, y 3, each on its own creative motor, turning so that the controller
     * between them (x 5) faces down; a floor under it. Returns the controller.
     */
    private static BlockPos wheels(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 3; x <= 7; x++)
            for (int z = 3; z <= 7; z++)
                level.setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        int[][] wheels = {{4, -128}, {6, 128}};
        for (int[] w : wheels) {
            BlockPos wheel = helper.absolutePos(new BlockPos(w[0], 3, 5)), motor = wheel.north();
            level.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                    .setValue(DirectionalKineticBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
            if (level.getBlockEntity(motor) instanceof CreativeMotorBlockEntity m)
                m.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(w[1]);
            level.setBlock(wheel, AllBlocks.CRUSHING_WHEEL.getDefaultState()
                    .setValue(BlockStateProperties.AXIS, Direction.Axis.Z), Block.UPDATE_ALL);
        }
        return helper.absolutePos(new BlockPos(5, 3, 5));
    }

    private static int count(GameTestHelper helper, Item item) {
        int n = 0;
        for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(16, 10, 16)))
            if (e.getItem().is(item))
                n += e.getItem().getCount();
        return n;
    }

    /** Drops 64 of {@code input} on the wheels and waits until all are crushed. Returns the crushed raw iron out. */
    private static void crush(GameTestHelper helper, Item input, java.util.function.IntConsumer then) {
        BlockPos controller = wheels(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getLevel().getBlockState(controller).is(AllBlocks.CRUSHING_WHEEL_CONTROLLER.get())
                        && helper.getLevel().getBlockState(controller).getValue(CrushingWheelControllerBlock.VALID)
                        && helper.getLevel().getBlockState(controller).getValue(CrushingWheelControllerBlock.FACING) == Direction.DOWN,
                        "the wheels are not crushing downwards"))
                .thenIdle(20)
                .thenExecute(() -> {
                    // right in the controller, without the random push an item entity gets (it could land on a wheel)
                    ItemEntity item = new ItemEntity(helper.getLevel(), controller.getX() + .5, controller.getY() + .5,
                            controller.getZ() + .5, new ItemStack(input, 64));
                    item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    helper.getLevel().addFreshEntity(item);
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, input) == 0 && count(helper, AllItems.CRUSHED_IRON.get()) > 0,
                        "not all crushed yet: " + count(helper, input) + " left; controller " + helper.getLevel().getBlockState(controller)
                                + ", items at " + helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(controller).inflate(3))
                                .stream().map(e -> e.blockPosition().subtract(controller).toShortString()).toList()))
                .thenIdle(20)
                .thenExecute(() -> then.accept(count(helper, AllItems.CRUSHED_IRON.get())))
                .thenSucceed();
    }

    /** Create's raw iron: exactly one crushed raw iron each, as in Create (the bonus is only for chilled ores). */
    @GameTest(template = SMALL, timeoutTicks = 1200, batch = "chilled_on")
    public static void plainRawIronCrushesAsInCreate(GameTestHelper helper) {
        crush(helper, Items.RAW_IRON, n -> helper.assertTrue(n == 64, n + " crushed raw iron for 64 raw iron"));
    }

    /**
     * Chilled raw iron out of any cold room: one crushed raw iron each, plus 15 % (64 + Binomial(64, 0.15): 9.6 more
     * on average; between 1 and 25 more nearly always).
     */
    @GameTest(template = SMALL, timeoutTicks = 1200, batch = "chilled_on")
    public static void chilledRawIronGivesMore(GameTestHelper helper) {
        crush(helper, chilled("raw_iron"), n -> helper.assertTrue(n >= 65 && n <= 89,
                n + " crushed raw iron for 64 chilled raw iron (64 + about 10 expected)"));
    }

    /** The hook into Create's crushing wheels is in (the startup log says so too). */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void theCrushingHookIsIn(GameTestHelper helper) {
        helper.assertTrue(com.frostworks.CreateHooks.ready(com.frostworks.CreateHooks.Hook.CRUSHING), "hook: " + System.getProperty(com.frostworks.CreateHooks.Hook.CRUSHING.property));
        helper.succeed();
    }

    // ---- The infuser ----------------------------------------------------------------------------------------------

    private static BlockPos[] infuser(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos infuser = helper.absolutePos(new BlockPos(5, 3, 5)), depot = infuser.below(2), motor = infuser.north();
        level.setBlock(depot, AllBlocks.DEPOT.getDefaultState(), Block.UPDATE_ALL);
        level.setBlock(infuser, FwBlocks.CRYO_INFUSER.getDefaultState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), Block.UPDATE_ALL);
        level.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(DirectionalKineticBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
        if (level.getBlockEntity(motor) instanceof CreativeMotorBlockEntity m)
            m.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(64);
        return new BlockPos[]{infuser, depot};
    }

    /** Raw iron and 100 mB of Frosium (no gas any more): chilled raw iron, the tank empty. */
    @GameTest(template = SMALL, timeoutTicks = 400)
    public static void theInfuserChillsRawIron(GameTestHelper helper) {
        BlockPos[] rig = infuser(helper);
        helper.runAfterDelay(5, () -> {
            CryoInfuserBlockEntity be = (CryoInfuserBlockEntity) helper.getLevel().getBlockEntity(rig[0]);
            be.frosiumTank().fill(new FluidStack(FwFluids.frosium(), 100), IFluidHandler.FluidAction.EXECUTE);
            BlockEntityBehaviour.get(helper.getLevel(), rig[1], DepotBehaviour.TYPE)
                    .setHeldItem(new TransportedItemStack(new ItemStack(Items.RAW_IRON)));
        });
        helper.succeedWhen(() -> {
            CryoInfuserBlockEntity be = (CryoInfuserBlockEntity) helper.getLevel().getBlockEntity(rig[0]);
            ItemStack held = BlockEntityBehaviour.get(helper.getLevel(), rig[1], DepotBehaviour.TYPE).getHeldItemStack();
            helper.assertTrue(held.is(chilled("raw_iron")), "on the depot: " + held);
            helper.assertTrue(be.tank().isEmpty() && be.frosiumTank().isEmpty(),
                    "left: gas " + be.tank().getFluidAmount() + ", Frosium " + be.frosiumTank().getFluidAmount());
        });
    }

    /** Cold gas but no Frosium: the ore stays as it is, the gas is not touched, the goggles say Frosium is missing. */
    @GameTest(template = SMALL, timeoutTicks = 300)
    public static void noFrosiumNoChill(GameTestHelper helper) {
        BlockPos[] rig = infuser(helper);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    ((CryoInfuserBlockEntity) helper.getLevel().getBlockEntity(rig[0])).tank()
                            .fill(FwFluids.gas(250), IFluidHandler.FluidAction.EXECUTE);
                    BlockEntityBehaviour.get(helper.getLevel(), rig[1], DepotBehaviour.TYPE)
                            .setHeldItem(new TransportedItemStack(new ItemStack(Items.IRON_ORE)));
                })
                .thenIdle(150)
                .thenExecute(() -> {
                    CryoInfuserBlockEntity be = (CryoInfuserBlockEntity) helper.getLevel().getBlockEntity(rig[0]);
                    ItemStack held = BlockEntityBehaviour.get(helper.getLevel(), rig[1], DepotBehaviour.TYPE).getHeldItemStack();
                    helper.assertTrue(held.is(Items.IRON_ORE), "on the depot: " + held);
                    helper.assertTrue(be.state() == CryoInfuserBlockEntity.State.NO_FROSIUM, "goggles: " + be.state());
                    helper.assertTrue(be.tank().getFluidAmount() == 250, "gas used without Frosium");
                })
                .thenSucceed();
    }

    /** The 24 chilling recipes take Frosium only, its amount from the settings (100 mB). */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void chillingTakesFrosiumOnly(GameTestHelper helper) {
        int n = 0;
        for (var holder : helper.getLevel().getRecipeManager().getAllRecipesFor(com.frostworks.registry.FwRecipeTypes.INFUSING
                .<net.minecraft.world.item.crafting.RecipeType<com.frostworks.content.infuser.InfusingRecipe>>getType())) {
            if (!holder.getId().getPath().contains("chilled"))
                continue;
            helper.assertTrue(holder.gasAmount() == 0, holder.getId() + " takes gas");
            helper.assertTrue(holder.frosiumAmount() == FwConfig.get(FwConfig.CHILLED_INFUSION_FROSIUM),
                    holder.getId() + ": " + holder.frosiumAmount() + " mB of Frosium");
            n++;
        }
        helper.assertTrue(n == 24, n + " chilling recipes");
        helper.succeed();
    }

    // ---- The blocks -----------------------------------------------------------------------------------------------

    /** Silk touch: the chilled ore itself; a plain pickaxe: the base ore's loot (raw iron); fortune works. */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void chilledOreDrops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 5));
        Block ore = ((net.minecraft.world.item.BlockItem) chilled("iron_ore")).getBlock();
        level.setBlock(pos, ore.defaultBlockState(), Block.UPDATE_ALL);
        ItemStack silk = new ItemStack(Items.IRON_PICKAXE), plain = new ItemStack(Items.IRON_PICKAXE),
                fortune = new ItemStack(Items.IRON_PICKAXE);
        silk.enchant(Enchantments.SILK_TOUCH, 1);
        fortune.enchant(Enchantments.BLOCK_FORTUNE, 3);
        var drops = Block.getDrops(ore.defaultBlockState(), level, pos, null, null, silk);
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(ore.asItem()), "silk touch: " + drops);
        drops = Block.getDrops(ore.defaultBlockState(), level, pos, null, null, plain);
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(Items.RAW_IRON) && drops.get(0).getCount() == 1,
                "plain pickaxe: " + drops);
        int raw = 0;
        for (int i = 0; i < 200; i++)
            for (ItemStack d : Block.getDrops(ore.defaultBlockState(), level, pos, null, null, fortune))
                raw += d.is(Items.RAW_IRON) ? d.getCount() : 0;
        helper.assertTrue(raw > 300, "fortune III: " + raw + " raw iron for 200 ores (about 440 expected)");
        helper.assertTrue(ore.defaultBlockState().getDestroySpeed(level, pos) == Blocks.IRON_ORE.defaultBlockState()
                .getDestroySpeed(level, pos), "not as hard as iron ore");
        helper.succeed();
    }

    /** In the furnace and the blast furnace, as the base: an iron ingot from chilled raw iron or chilled iron ore. */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void chilledOresSmeltAsTheBase(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        for (String base : new String[]{"raw_iron", "iron_ore", "deepslate_gold_ore", "zinc_ore", "nether_quartz_ore"})
            for (RecipeType<?> type : new RecipeType<?>[]{RecipeType.SMELTING, RecipeType.BLASTING}) {
                @SuppressWarnings("unchecked")
                var found = recipes.getRecipeFor((RecipeType<net.minecraft.world.item.crafting.AbstractCookingRecipe>) type,
                        new net.minecraft.world.SimpleContainer(new ItemStack(chilled(base))), helper.getLevel());
                helper.assertTrue(found.isPresent(), "no " + type + " recipe for chilled " + base);
            }
        helper.succeed();
    }

    /** Never in c:ores (Create's crushing recipes for c:ores would take them as other mods' ores). */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void chilledOresAreNotOres(GameTestHelper helper) {
        for (var block : FwChilledOres.blocks()) {
            ItemStack stack = new ItemStack(block.get());
            helper.assertTrue(!stack.is(Tags.Items.ORES), block.getId() + " in c:ores");
        }
        helper.assertTrue(FwChilledOres.blocks().size() == 20 && FwChilledOres.raws().size() == 4, "20 ores and 4 raws");
        helper.succeed();
    }
}
