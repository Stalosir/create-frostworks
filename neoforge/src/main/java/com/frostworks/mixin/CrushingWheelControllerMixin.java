package com.frostworks.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.frostworks.content.cold.ColdProcessing;
import com.frostworks.content.cold.ColdVersion;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Cold processing at Create's crushing wheels ({@code create:crushing}, and {@code create:milling} when the wheels
 * take a milling recipe). Wraps the one call that rolls a crushed item's results ({@code applyRecipe}, once per item):
 * {@link ColdProcessing} may change the list the wheels roll, from the room they are in. Optional ({@code require = 0}):
 * without that call (another Create version), the wheels keep their recipes ({@link com.frostworks.CreateHooks}).
 */
@Mixin(CrushingWheelControllerBlockEntity.class)
public abstract class CrushingWheelControllerMixin {

    @WrapOperation(method = "applyRecipe", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/processing/recipe/StandardProcessingRecipe;rollResults(Lnet/minecraft/util/RandomSource;)Ljava/util/List;"))
    private List<ItemStack> frostworks$coldProcessing(StandardProcessingRecipe<?> recipe, RandomSource random,
                                                    Operation<List<ItemStack>> original,
                                                    @Local(ordinal = 0) ItemStack input) {
        BlockEntity self = (BlockEntity) (Object) this;
        return ColdProcessing.roll(recipe, input, ColdVersion.results(recipe), self.getLevel(),
                self.getBlockPos(), results -> ColdVersion.roll(recipe, results, random), () -> original.call(recipe, random));
    }
}
