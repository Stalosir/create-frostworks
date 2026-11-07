package com.frostworks.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.frostworks.content.cold.ColdProcessing;
import com.frostworks.content.cold.ColdVersion;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Cold processing at Create's millstone ({@code create:milling}). Wraps the call that rolls a milled item's results
 * ({@code process}): {@link ColdProcessing} may change the list the millstone rolls, from the room it is in. Optional
 * ({@code require = 0}), as the crushing wheels' hook.
 */
@Mixin(MillstoneBlockEntity.class)
public abstract class MillstoneMixin {

    @WrapOperation(method = "process", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/kinetics/millstone/MillingRecipe;rollResults(Lnet/minecraft/util/RandomSource;)Ljava/util/List;"))
    private List<ItemStack> frostworks$coldProcessing(MillingRecipe recipe, RandomSource random,
                                                    Operation<List<ItemStack>> original) {
        MillstoneBlockEntity self = (MillstoneBlockEntity) (Object) this;
        // the input slot still holds the item being milled when its results are rolled
        return ColdProcessing.roll(recipe, self.inputInv.getStackInSlot(0), ColdVersion.results(recipe),
                self.getLevel(), self.getBlockPos(), results -> ColdVersion.roll(recipe, results, random),
                () -> original.call(recipe, random));
    }
}
