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

import net.minecraft.world.item.ItemStack;

/**
 * Cold processing at Create's millstone ({@code create:milling}). Wraps the call that rolls a milled item's results
 * ({@code process}): {@link ColdProcessing} may change the list the millstone rolls, from the room it is in. Optional
 * ({@code require = 0}), as the crushing wheels' hook. Forge 1.20.1 (Create 6.0.4): {@code rollResults()}, Create's random.
 */
@Mixin(value = MillstoneBlockEntity.class, remap = false)
public abstract class MillstoneMixin {

    @WrapOperation(method = "process", require = 0, remap = false, at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/kinetics/millstone/MillingRecipe;rollResults()Ljava/util/List;"))
    private List<ItemStack> frostworks$coldProcessing(MillingRecipe recipe,
                                                    Operation<List<ItemStack>> original) {
        MillstoneBlockEntity self = (MillstoneBlockEntity) (Object) this;
        // the input slot still holds the item being milled when its results are rolled
        return ColdProcessing.roll(recipe, self.inputInv.getStackInSlot(0), ColdVersion.results(recipe),
                self.getLevel(), self.getBlockPos(), results -> ColdVersion.roll(recipe, results, null),
                () -> original.call(recipe));
    }
}
