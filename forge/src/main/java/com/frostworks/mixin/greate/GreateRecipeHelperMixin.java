package com.frostworks.mixin.greate;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.frostworks.content.cold.ColdProcessing;
import com.frostworks.content.cold.ColdVersion;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;

import net.minecraft.world.item.ItemStack;

/**
 * Greate (Forge 1.20.1 only, optional): cold processing in Greate's roll. {@code getItemResults} gives Greate's
 * results their tier rule, then rolls them; this wraps that roll, for the machine {@link GreateCrusherMixin} or {@link
 * GreateMillstoneMixin} named. Greate's results keep their rule; ours are added after it, without it.
 */
@Pseudo
@Mixin(targets = "electrolyte.greate.foundation.recipe.TieredRecipeHelper", remap = false)
public abstract class GreateRecipeHelperMixin {

    @WrapOperation(method = "getItemResults", require = 0, remap = false, at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/processing/recipe/ProcessingRecipe;rollResults(Ljava/util/List;)Ljava/util/List;"))
    private List<ItemStack> frostworks$coldProcessing(ProcessingRecipe<?> recipe, List<ProcessingOutput> results,
                                                    Operation<List<ItemStack>> original) {
        ColdProcessing.Context machine = ColdProcessing.context();
        if (machine == null)
            return original.call(recipe, results);
        return ColdProcessing.roll(recipe, machine.input(), results, machine.level(), machine.pos(),
                list -> original.call(recipe, list), () -> original.call(recipe, results));
    }
}
