package com.frostworks.mixin.greate;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.frostworks.content.cold.ColdProcessing;
import com.frostworks.content.cold.ColdVersion;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * Greate (Forge 1.20.1 only, optional): cold processing at Greate's basin ({@code greate:mixing},
 * {@code greate:compacting}, {@code greate:brewing}, {@code greate:basin}, and Create's basin recipes in it). Its
 * {@code apply} is Create's, copied: it rolls the recipe's items and reads its fluids once, while it simulates the craft,
 * Create's way (no tier rule of Greate's for the results). The values those calls give are changed here, from the room
 * the basin is in; Greate's classes are not needed to compile ({@code @Pseudo}, the recipe read from the argument).
 * When a file applies, the items are rolled again from the new list (the first roll is left unused).
 */
@Pseudo
@Mixin(targets = "electrolyte.greate.content.processing.basin.TieredBasinRecipe", remap = false)
public abstract class GreateBasinMixin {

    private static final String APPLY = "apply(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;Lnet/minecraft/world/item/crafting/Recipe;Z)Z";

    @ModifyExpressionValue(method = APPLY, require = 0, remap = false, at = {
            @At(value = "INVOKE", remap = false,
                    target = "Lelectrolyte/greate/content/processing/basin/TieredBasinRecipe;rollResults()Ljava/util/List;"),
            @At(value = "INVOKE", remap = false,
                    target = "Lcom/simibubi/create/content/processing/basin/BasinRecipe;rollResults()Ljava/util/List;")})
    private static List<ItemStack> frostworks$coldItems(List<ItemStack> rolled,
                                                        @Local(argsOnly = true) BasinBlockEntity basin,
                                                        @Local(argsOnly = true) Recipe<?> recipe,
                                                        @Local IItemHandler items, @Local IFluidHandler fluids,
                                                        @Local(ordinal = 0) int[] takenItems,
                                                        @Local(ordinal = 1) int[] takenFluids) {
        return ColdProcessing.roll(recipe, used(items, takenItems), used(fluids, takenFluids), ColdVersion.results(recipe),
                basin.getLevel(), basin.getBlockPos(), results -> ColdVersion.roll(recipe, results, null), () -> rolled);
    }

    @ModifyExpressionValue(method = APPLY, require = 0, remap = false, at = {
            @At(value = "INVOKE", remap = false,
                    target = "Lelectrolyte/greate/content/processing/basin/TieredBasinRecipe;getFluidResults()Lnet/minecraft/core/NonNullList;"),
            @At(value = "INVOKE", remap = false,
                    target = "Lcom/simibubi/create/content/processing/basin/BasinRecipe;getFluidResults()Lnet/minecraft/core/NonNullList;")})
    private static NonNullList<FluidStack> frostworks$coldFluids(NonNullList<FluidStack> recipeFluids,
                                                                 @Local(argsOnly = true) BasinBlockEntity basin,
                                                                 @Local(argsOnly = true) Recipe<?> recipe,
                                                                 @Local IItemHandler items, @Local IFluidHandler fluids,
                                                                 @Local(ordinal = 0) int[] takenItems,
                                                                 @Local(ordinal = 1) int[] takenFluids) {
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
