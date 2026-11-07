package com.frostworks.mixin.greate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.frostworks.content.cold.ColdProcessing;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;

import net.minecraft.world.entity.item.ItemEntity;

/**
 * Greate (Forge 1.20.1 only, optional): its fans' washing and haunting ({@code greate:splashing}, {@code
 * greate:haunting}) of an item on the ground roll through {@code TieredRecipeApplier.applyRecipeOn}, then {@code
 * TieredRecipeHelper.getItemResults}; this tells that roll which item it is for and where it lies (its own cell: the
 * room is the item's, not the fan's). {@link GreateRecipeHelperMixin} does the rest. The fan's class is Greate's, so
 * its argument is taken as an Object ({@code @Coerce}).
 * <p>
 * The context opens at the start of {@code applyProcessing} and closes at its return. A call that never returned
 * (Greate's code threw) left its context open: the next call closes it first, so it never leaks into later rolls.
 * <p>
 * Not on a belt: an item on a belt has no cold processing (the call gives only the fan's position, which may blow into
 * another room).
 */
@Pseudo
@Mixin(targets = "electrolyte.greate.content.kinetics.fan.TieredFanProcessing", remap = false)
public abstract class GreateFanMixin {

    private static final String ON_THE_GROUND = "applyProcessing(FLnet/minecraft/world/entity/item/ItemEntity;"
            + "Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessingType;I"
            + "Lelectrolyte/greate/content/kinetics/fan/TieredEncasedFanBlockEntity;)Z";

    /** A fan's context is open on this thread. */
    private static final ThreadLocal<Boolean> OPEN = ThreadLocal.withInitial(() -> false);

    @Inject(method = ON_THE_GROUND, at = @At("HEAD"), require = 0, remap = false)
    private static void frostworks$coldContext(float time, ItemEntity entity, FanProcessingType type, int tier,
                                              @Coerce Object fan, CallbackInfoReturnable<Boolean> cir) {
        if (OPEN.get())
            ColdProcessing.leave(); // the last call did not return
        ColdProcessing.enter(entity.level(), entity.blockPosition(), entity.getItem());
        OPEN.set(true);
    }

    @Inject(method = ON_THE_GROUND, at = @At("RETURN"), require = 0, remap = false)
    private static void frostworks$coldContextEnd(float time, ItemEntity entity, FanProcessingType type, int tier,
                                                 @Coerce Object fan, CallbackInfoReturnable<Boolean> cir) {
        if (OPEN.get()) {
            ColdProcessing.leave();
            OPEN.set(false);
        }
    }
}
