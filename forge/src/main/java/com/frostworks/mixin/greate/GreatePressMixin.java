package com.frostworks.mixin.greate;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.frostworks.content.cold.ColdProcessing;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;

/**
 * Greate (Forge 1.20.1 only, optional): its press (on the ground and on a belt, out of a basin) rolls through
 * {@code TieredRecipeApplier.applyRecipeOn}, then {@code TieredRecipeHelper.getItemResults}; this tells that roll which
 * machine it is for, and what it presses ({@link GreateRecipeHelperMixin} does the rest). The press's own position:
 * the item it presses lies under it, in the same room. Named, never compiled against Greate ({@code @Pseudo}).
 */
@Pseudo
@Mixin(targets = "electrolyte.greate.content.kinetics.press.TieredMechanicalPressBlockEntity", remap = false)
public abstract class GreatePressMixin {

    /** On a belt, and on the ground (the result count): the stack the press works on. */
    @WrapOperation(method = {"tryProcessOnBelt", "tryProcessInWorld"}, require = 0, remap = false, at = @At(value = "INVOKE",
            target = "Lelectrolyte/greate/foundation/recipe/TieredRecipeApplier;applyRecipeOn(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/crafting/Recipe;IZ)Ljava/util/List;"))
    private List<ItemStack> frostworks$coldContext(Level level, ItemStack stack, Recipe<?> recipe, int tier,
                                                 boolean returnRemainder, Operation<List<ItemStack>> original) {
        MechanicalPressBlockEntity self = (MechanicalPressBlockEntity) (Object) this;
        return ColdProcessing.within(self.getLevel(), self.getBlockPos(), stack,
                () -> original.call(level, stack, recipe, tier, returnRemainder));
    }

    /** On the ground: the item entity under the press. */
    @WrapOperation(method = "tryProcessInWorld", require = 0, remap = false, at = @At(value = "INVOKE",
            target = "Lelectrolyte/greate/foundation/recipe/TieredRecipeApplier;applyRecipeOn(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/crafting/Recipe;IZ)V"))
    private void frostworks$coldContextInWorld(ItemEntity entity, Recipe<?> recipe, int tier, boolean returnRemainder,
                                              Operation<Void> original) {
        MechanicalPressBlockEntity self = (MechanicalPressBlockEntity) (Object) this;
        ColdProcessing.within(self.getLevel(), self.getBlockPos(), entity.getItem(), () -> {
            original.call(entity, recipe, tier, returnRemainder);
            return null;
        });
    }
}
