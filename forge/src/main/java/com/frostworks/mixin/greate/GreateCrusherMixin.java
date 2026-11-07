package com.frostworks.mixin.greate;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import com.frostworks.content.cold.ColdProcessing;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Greate (Forge 1.20.1 only, optional): its crushing wheels roll through {@code TieredRecipeHelper.getItemResults};
 * this tells that roll which machine it is for, and what it crushes ({@link GreateRecipeHelperMixin} does the rest).
 * Named, never compiled against Greate ({@code @Pseudo}): without Greate, nothing.
 */
@Pseudo
@Mixin(targets = "electrolyte.greate.content.kinetics.crusher.TieredCrushingWheelControllerBlockEntity", remap = false)
public abstract class GreateCrusherMixin {

    @WrapOperation(method = "applyValidRecipe", require = 0, remap = false, at = @At(value = "INVOKE",
            target = "Lelectrolyte/greate/foundation/recipe/TieredRecipeHelper;getItemResults(Lnet/minecraft/world/item/crafting/Recipe;I)Ljava/util/List;"))
    private List<ItemStack> frostworks$coldContext(@Coerce Object helper, Recipe<?> recipe, int tier,
                                                 Operation<List<ItemStack>> original) {
        CrushingWheelControllerBlockEntity self = (CrushingWheelControllerBlockEntity) (Object) this;
        return ColdProcessing.within(self.getLevel(), self.getBlockPos(), self.inventory.getStackInSlot(0),
                () -> original.call(helper, recipe, tier));
    }
}
