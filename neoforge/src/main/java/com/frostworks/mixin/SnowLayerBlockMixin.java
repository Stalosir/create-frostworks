package com.frostworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.frostworks.room.KeptFrozen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A snow layer does not melt in a cold room ({@link KeptFrozen}); its random tick only melts it. */
@Mixin(SnowLayerBlock.class)
public abstract class SnowLayerBlockMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void frostworks$keptFrozen(BlockState state, ServerLevel level, BlockPos pos, RandomSource random,
                                       CallbackInfo ci) {
        if (KeptFrozen.at(level, pos))
            ci.cancel();
    }
}
