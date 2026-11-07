package com.frostworks.mixin.ambiental;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.frostworks.compat.ambiental.AmbientalRooms;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.player.Player;
import su.terrafirmagreg.tfcambiental.modifier.TempModifierStorage;

/**
 * TFC Ambiental (TerraFirmaGreg's, in TerraFirmaGreg-Core; Forge 1.20.1 only, optional): once a second, the
 * player's temperature capability gathers the environment's modifiers ({@code EnvironmentalTemperatureProvider.evaluateAll},
 * where TerraFirmaGreg's heat pump replaces them all). This wraps that call: in a closed cold room, the room's
 * temperature in their place ({@link AmbientalRooms}). Named, never compiled against the pack ({@code @Pseudo}, the
 * three classes it names are stand-ins outside our jar).
 */
@Pseudo
@Mixin(targets = "su.terrafirmagreg.tfcambiental.capability.TemperatureCapability", remap = false)
public abstract class TemperatureCapabilityMixin {

    @WrapOperation(method = "evaluateModifiers", require = 0, remap = false, at = @At(value = "INVOKE",
            target = "Lsu/terrafirmagreg/tfcambiental/api/EnvironmentalTemperatureProvider;evaluateAll(Lnet/minecraft/world/entity/player/Player;Lsu/terrafirmagreg/tfcambiental/modifier/TempModifierStorage;Z)V"))
    private void frostworks$coldRoom(Player player, TempModifierStorage storage, boolean nether, Operation<Void> original) {
        AmbientalRooms.environment(player, storage, nether, () -> original.call(player, storage, nether),
                into -> original.call(player, into, nether));
    }
}
