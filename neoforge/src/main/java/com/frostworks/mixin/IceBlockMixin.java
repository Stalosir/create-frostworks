package com.frostworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.frostworks.room.KeptFrozen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ice does not melt in a cold room ({@link KeptFrozen}). {@code melt} is where vanilla's ice turns to
 * water under a torch's light, and where frosted ice ends.
 */
@Mixin(IceBlock.class)
public abstract class IceBlockMixin {
    @Inject(method = "melt", at = @At("HEAD"), cancellable = true)
    private void frostworks$keptFrozen(BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        if (KeptFrozen.at(level, pos))
            ci.cancel();
    }
}
