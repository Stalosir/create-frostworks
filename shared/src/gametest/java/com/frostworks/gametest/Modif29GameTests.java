package com.frostworks.gametest;

import java.util.List;

import com.frostworks.content.column.Distillation;
import com.frostworks.content.column.DistillingRecipe;
import com.frostworks.content.column.TowerBlock;
import com.frostworks.content.column.TowerBlockEntity;
import com.frostworks.platform.Mc;
import com.frostworks.registry.FwFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** The tower's crafts are distilling recipes, in JSON. */
public final class Modif29GameTests {
    private Modif29GameTests() {}

    private static final String SMALL = "empty_16x10x16";
    private static final IFluidHandler.FluidAction SIM = IFluidHandler.FluidAction.SIMULATE;

    /** The mod's recipe file is read: cold gas and water, with ice, into Frosium, on the energy yield. */
    @GameTest(template = SMALL)
    public static void theFrosiumRecipeIsAFile(GameTestHelper helper) {
        List<Distillation> all = DistillingRecipe.all(helper.getLevel());
        Distillation r = all.stream().filter(d -> d.id.equals(Distillation.FROSIUM)).findFirst().orElse(null);
        helper.assertTrue(r != null, "no " + Distillation.FROSIUM + " among " + all.size() + " recipes");
        helper.assertTrue(r.energy && r.ice && r.inputs.size() == 2, "energy " + r.energy + " ice " + r.ice
                + " inputs " + r.inputs.size());
        helper.assertTrue(r.takes(FwFluids.gas()) && r.index(Fluids.WATER) == 1, "inputs: cold gas then water");
        helper.assertTrue(r.result.isSame(FwFluids.frosium()), "makes " + r.result);
        helper.succeed();
    }

    /**
     * Our rule: empty, the tower takes the first input of any recipe; then only what still makes a recipe with
     * what it holds. Holding water, it takes cold gas and ice; Frosium (no recipe's input) and lava never.
     */
    @GameTest(template = SMALL, timeoutTicks = 60)
    public static void itTakesOnlyWhatMakesARecipe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos c = helper.absolutePos(new BlockPos(4, 1, 4));
        TowerGameTests.tower(level, c, 2);
        helper.runAfterDelay(10, () -> {
            IFluidHandler h = com.frostworks.FwCaps.fluidAt(level, c, Direction.WEST);
            helper.assertTrue(h.fill(new FluidStack(FwFluids.frosium(), 1000), SIM) == 0, "empty: took Frosium");
            helper.assertTrue(h.fill(new FluidStack(Fluids.LAVA, 1000), SIM) == 0, "empty: took lava");
            helper.assertTrue(h.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 1000,
                    "empty: refused water");
            // holding water: the Frosium recipe still fits, cold gas goes in (its share: 8000 of 16000)
            helper.assertTrue(h.fill(FwFluids.gas(20000), SIM) == 8000, "with water: gas " + h.fill(FwFluids.gas(20000), SIM));
            helper.assertTrue(h.fill(new FluidStack(FwFluids.frosium(), 1000), SIM) == 0, "with water: took Frosium");
            var items = com.frostworks.FwCaps.itemsAt(level, c, Direction.WEST);
            helper.assertTrue(items.insertItem(0, new ItemStack(Items.PACKED_ICE), true).isEmpty(), "with water: refused ice");
            TowerBlockEntity t = TowerBlock.at(level, c);
            helper.assertTrue(t.contents().size() == 1 && t.water() == 1000, "contents " + t.contents());
            helper.succeed();
        });
    }

    /** A tower saved before recipes (cold gas, water and Frosium under their own keys) loads into the Frosium recipe. */
    @GameTest(template = SMALL, timeoutTicks = 60)
    public static void anOldTowerLoads(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos c = helper.absolutePos(new BlockPos(4, 1, 4));
        TowerGameTests.tower(level, c, 2);
        helper.runAfterDelay(10, () -> {
            TowerBlockEntity t = TowerBlock.at(level, c);
            CompoundTag tag = Mc.saveBlockEntity(t, level);
            for (String key : new String[]{"Inputs", "OutputFluid", "Output", "OutputRemainder", "Recipe"})
                tag.remove(key);
            tag.putInt("Gas", 3000);
            tag.putDouble("GasCredit", 0.5);
            tag.putInt("Water", 2000);
            tag.putInt("Air", 500);
            tag.putDouble("AirRemainder", 0.25);
            TowerBlockEntity old = (TowerBlockEntity) Mc.loadBlockEntity(c, t.getBlockState(), tag, level);
            old.setLevel(level);
            helper.assertTrue(old.gas() == 3000 && old.water() == 2000 && old.frosium() == 500,
                    "gas " + old.gas() + " water " + old.water() + " Frosium " + old.frosium());
            helper.assertTrue(old.recipe() != null && old.recipe().id.equals(Distillation.FROSIUM),
                    "recipe " + (old.recipe() == null ? "none" : old.recipe().id));
            // saved again: the new keys only, the same content
            CompoundTag again = Mc.saveBlockEntity(old, level);
            helper.assertTrue(!again.contains("Gas") && again.contains("Inputs"), "saved with the old keys");
            TowerBlockEntity back = (TowerBlockEntity) Mc.loadBlockEntity(c, t.getBlockState(), again, level);
            helper.assertTrue(back.gas() == 3000 && back.water() == 2000 && back.frosium() == 500,
                    "again: gas " + back.gas() + " water " + back.water() + " Frosium " + back.frosium());
            helper.succeed();
        });
    }
}
