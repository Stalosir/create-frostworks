package com.frostworks.mixin.greate;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import com.frostworks.content.cold.ColdProcessing;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Greate (Forge 1.20.1 only, optional): its millstone rolls through {@code TieredRecipeHelper.getItemResults};
 * this tells that roll which machine it is for, and what it mills ({@link GreateRecipeHelperMixin} does the rest).
 * Named, never compiled against Greate ({@code @Pseudo}): without Greate, nothing.
 */
@Pseudo
@Mixin(targets = "electrolyte.greate.content.kinetics.millstone.TieredMillstoneBlockEntity", remap = false)
public abstract class GreateMillstoneMixin {

    @WrapOperation(method = "processRecipe", require = 0, remap = false, at = @At(value = "INVOKE",
            target = "Lelectrolyte/greate/foundation/recipe/TieredRecipeHelper;getItemResults(Lnet/minecraft/world/item/crafting/Recipe;I)Ljava/util/List;"))
    private List<ItemStack> frostworks$coldContext(@Coerce Object helper, Recipe<?> recipe, int tier,
                                                 Operation<List<ItemStack>> original) {
        MillstoneBlockEntity self = (MillstoneBlockEntity) (Object) this;
        return ColdProcessing.within(self.getLevel(), self.getBlockPos(), self.inputInv.getStackInSlot(0),
                () -> original.call(helper, recipe, tier));
    }
}
