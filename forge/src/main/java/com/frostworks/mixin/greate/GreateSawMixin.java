package com.frostworks.mixin.greate;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.frostworks.content.cold.ColdProcessing;
import com.frostworks.content.cold.ColdVersion;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;

import net.minecraft.world.item.ItemStack;

/**
 * Greate (Forge 1.20.1 only, optional): cold processing at Greate's saw ({@code greate:cutting}). Its {@code
 * applyValidRecipe} rolls Create's way ({@code rollResults()}, no tier rule of Greate's), once per item of the stack it
 * holds; this wraps that roll, with the saw's position and the stack in its inventory. Named, never compiled against
 * Greate ({@code @Pseudo}); its saw is Create's, extended.
 */
@Pseudo
@Mixin(targets = "electrolyte.greate.content.kinetics.saw.TieredSawBlockEntity", remap = false)
public abstract class GreateSawMixin {

    @WrapOperation(method = "applyValidRecipe", require = 0, remap = false, at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/processing/recipe/ProcessingRecipe;rollResults()Ljava/util/List;"))
    private List<ItemStack> frostworks$coldProcessing(ProcessingRecipe<?> recipe, Operation<List<ItemStack>> original) {
        SawBlockEntity saw = (SawBlockEntity) (Object) this;
        return ColdProcessing.roll(recipe, saw.inventory.getStackInSlot(0),
                recipe.getRollableResults(), saw.getLevel(), saw.getBlockPos(), recipe::rollResults,
                () -> original.call(recipe));
    }
}
