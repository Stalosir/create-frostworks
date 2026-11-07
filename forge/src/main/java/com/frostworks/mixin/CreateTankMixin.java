package com.frostworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.frostworks.registry.FwFluids;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.fluid.SmartFluidTank;

/**
 * Forge 1.20.1: Create's own fluid tank refuses Frosium, which only our frost tank holds (at the root, a
 * capability provider wraps Create's; here Create hands its tank out itself, so its tank is told at creation). Our
 * frost tank and Create's creative tank make their own inventory and are not touched.
 */
@Mixin(value = FluidTankBlockEntity.class, remap = false)
public abstract class CreateTankMixin {
    @Inject(method = "createInventory", at = @At("RETURN"), remap = false)
    private void frostworks$refuseFrosium(CallbackInfoReturnable<SmartFluidTank> cir) {
        SmartFluidTank tank = cir.getReturnValue();
        if (tank != null && ((Object) this).getClass() == FluidTankBlockEntity.class)
            tank.setValidator(stack -> !FwFluids.isFrosium(stack));
    }
}
