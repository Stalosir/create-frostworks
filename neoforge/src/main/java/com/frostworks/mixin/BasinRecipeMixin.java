package com.frostworks.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.frostworks.content.cold.ColdProcessing;
import com.frostworks.content.cold.ColdVersion;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;

import net.minecraft.core.NonNullList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Cold processing at the basin ({@code create:mixing}, {@code create:compacting}, {@code create:basin}): the mixer
 * and the press over a basin. {@code apply} rolls the recipe's items and reads its fluids once, while it simulates the
 * craft, and keeps both lists for the real craft: these two calls are wrapped there, from the room the basin is in.
 * What the craft uses is read then: the items and fluids counted as taken from the basin. Optional ({@code require = 0}).
 */
@Mixin(BasinRecipe.class)
public abstract class BasinRecipeMixin {

    @WrapOperation(method = "apply(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;Lnet/minecraft/world/item/crafting/Recipe;Z)Z",
            require = 0, at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/processing/basin/BasinRecipe;rollResults(Lnet/minecraft/util/RandomSource;)Ljava/util/List;"))
    private static List<ItemStack> frostworks$coldItems(BasinRecipe recipe, RandomSource random,
                                                        Operation<List<ItemStack>> original,
                                                        @Local(argsOnly = true) BasinBlockEntity basin,
                                                        @Local IItemHandler items, @Local IFluidHandler fluids,
                                                        @Local(ordinal = 0) int[] takenItems,
                                                        @Local(ordinal = 1) int[] takenFluids) {
        return ColdProcessing.roll(recipe, used(items, takenItems), used(fluids, takenFluids), ColdVersion.results(recipe),
                basin.getLevel(), basin.getBlockPos(), results -> ColdVersion.roll(recipe, results, random),
                () -> original.call(recipe, random));
    }

    @WrapOperation(method = "apply(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;Lnet/minecraft/world/item/crafting/Recipe;Z)Z",
            require = 0, at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/processing/basin/BasinRecipe;getFluidResults()Lnet/minecraft/core/NonNullList;"))
    private static NonNullList<FluidStack> frostworks$coldFluids(BasinRecipe recipe,
                                                                 Operation<NonNullList<FluidStack>> original,
                                                                 @Local(argsOnly = true) BasinBlockEntity basin,
                                                                 @Local IItemHandler items, @Local IFluidHandler fluids,
                                                                 @Local(ordinal = 0) int[] takenItems,
                                                                 @Local(ordinal = 1) int[] takenFluids) {
        NonNullList<FluidStack> recipeFluids = original.call(recipe);
        List<FluidStack> results = ColdProcessing.fluids(recipe, used(items, takenItems), used(fluids, takenFluids),
                recipeFluids, FluidStack::getFluid, FluidStack::getAmount, FluidStack::new, basin.getLevel(),
                basin.getBlockPos(), basin.getLevel().random);
        if (results == recipeFluids)
            return recipeFluids;
        NonNullList<FluidStack> list = NonNullList.create();
        list.addAll(results);
        return list;
    }

    /** The items the craft takes from the basin (the slots counted). */
    private static List<ItemStack> used(IItemHandler items, int[] taken) {
        List<ItemStack> used = new ArrayList<>();
        for (int slot = 0; slot < Math.min(taken.length, items.getSlots()); slot++)
            if (taken[slot] > 0)
                used.add(items.getStackInSlot(slot));
        return used;
    }

    /** The fluids the craft takes from the basin (the tanks counted). */
    private static List<Fluid> used(IFluidHandler fluids, int[] taken) {
        List<Fluid> used = new ArrayList<>();
        for (int tank = 0; tank < Math.min(taken.length, fluids.getTanks()); tank++)
            if (taken[tank] > 0)
                used.add(fluids.getFluidInTank(tank).getFluid());
        return used;
    }
}
