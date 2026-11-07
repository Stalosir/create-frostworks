package com.frostworks.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.frostworks.content.cold.ColdProcessing;
import com.frostworks.content.cold.ColdVersion;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;

import net.minecraft.world.item.ItemStack;

/**
 * Cold processing at Create's crushing wheels ({@code create:crushing}, and {@code create:milling} when the wheels
 * take a milling recipe). Wraps the one call that rolls a crushed item's results ({@code applyRecipe}, once per item):
 * {@link ColdProcessing} may change the list the wheels roll, from the room they are in. Optional ({@code require = 0}):
 * without that call (another Create version), the wheels keep their recipes ({@link com.frostworks.CreateHooks}).
 * Forge 1.20.1 (Create 6.0.4): the roll is {@code ProcessingRecipe.rollResults()}, with Create's own random.
 */
@Mixin(value = CrushingWheelControllerBlockEntity.class, remap = false)
public abstract class CrushingWheelControllerMixin {

    @WrapOperation(method = "applyRecipe", require = 0, remap = false, at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/processing/recipe/ProcessingRecipe;rollResults()Ljava/util/List;"))
    private List<ItemStack> frostworks$coldProcessing(ProcessingRecipe<?> recipe, Operation<List<ItemStack>> original) {
        CrushingWheelControllerBlockEntity self = (CrushingWheelControllerBlockEntity) (Object) this;
        // slot 0 still holds the item being crushed when its results are rolled
        return ColdProcessing.roll(recipe, self.inventory.getStackInSlot(0), ColdVersion.results(recipe),
                self.getLevel(), self.getBlockPos(), results -> ColdVersion.roll(recipe, results, null),
                () -> original.call(recipe));
    }
}
