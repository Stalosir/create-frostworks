package com.frostworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.frostworks.overspeed.ColdStress;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * The cold lowers the stress ({@link ColdStress}). The stress a kinetic block applies, as {@code
 * calculateStressApplied} returns it (the network, the goggles and the Display Link all read it there), scaled by the
 * block's cold step. Priority over the default: applied after the other mods' hooks on that method (TerraFirmaGreg-Core
 * adds the impact of the devices next to it, with a cancellable return), so that it scales the final value, on every
 * return. The step is checked by the server once a second and sent to the client with the block's data. Optional
 * ({@code require = 0}).
 */
@Mixin(value = KineticBlockEntity.class, priority = 1500, remap = false)
public abstract class KineticStressMixin {

    @Shadow protected float lastStressApplied;

    @Unique private int frostworks$coldStep;

    @ModifyReturnValue(method = "calculateStressApplied", at = @At("RETURN"), require = 0)
    private float frostworks$coldStress(float stress) {
        if (frostworks$coldStep == ColdStress.NONE)
            return stress;
        float reduced = stress * ColdStress.factor(frostworks$coldStep);
        lastStressApplied = reduced; // saved with the block, read back by its network when it loads
        return reduced;
    }

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void frostworks$checkColdStep(CallbackInfo ci) {
        KineticBlockEntity self = (KineticBlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel level) || !ColdStress.due(level, self))
            return;
        int step = ColdStress.stepFor(level, self);
        if (step != frostworks$coldStep) {
            frostworks$coldStep = step;
            ColdStress.apply(self);
        }
    }

    @Inject(method = "write", at = @At("RETURN"), require = 0)
    private void frostworks$writeColdStep(CompoundTag tag, boolean clientPacket,
                                          CallbackInfo ci) {
        if (clientPacket && frostworks$coldStep != ColdStress.NONE)
            tag.putByte(ColdStress.SYNC_KEY, (byte) frostworks$coldStep);
    }

    @Inject(method = "read", at = @At("RETURN"), require = 0)
    private void frostworks$readColdStep(CompoundTag tag, boolean clientPacket,
                                         CallbackInfo ci) {
        if (clientPacket)
            frostworks$coldStep = tag.getByte(ColdStress.SYNC_KEY);
    }
}
