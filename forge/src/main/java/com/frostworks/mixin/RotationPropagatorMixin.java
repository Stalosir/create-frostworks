package com.frostworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.frostworks.overspeed.Overspeed;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

/**
 * The overspeed hook (on Create's limit, no longer on a local variable by its name). In {@code
 * propagateNewSource}, Create compares the conveyed speeds with its limit ({@code maxRotationSpeed.get}, read twice,
 * once a way) and breaks the block when one is over it. We change the limit read there, for the pair of blocks at
 * hand only, when they may overspeed ({@link Overspeed#limitFor}): Create's comparison then lets the speed through.
 * The neighbour and the two speeds are taken by their place among the locals of their type, not by their names.
 * Optional ({@code require = 0}): if another Create version has no such read, the game loads and the overspeed turns
 * itself off ({@link com.frostworks.CreateHooks}).
 */
@Mixin(value = RotationPropagator.class, remap = false)
public abstract class RotationPropagatorMixin {

    @ModifyExpressionValue(method = "propagateNewSource", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/createmod/catnip/config/ConfigBase$ConfigInt;get()Ljava/lang/Object;"))
    private static Object frostworks$coldRoomLimit(Object limit,
                                                   @Local(argsOnly = true) KineticBlockEntity currentTE,
                                                   @Local(ordinal = 1) KineticBlockEntity neighbourTE,
                                                   @Local(ordinal = 2) float newSpeed,
                                                   @Local(ordinal = 3) float oppositeSpeed) {
        return Overspeed.limitFor(limit, currentTE, neighbourTE, newSpeed, oppositeSpeed);
    }
}
