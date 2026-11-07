package com.frostworks.registry;

import com.frostworks.Frostworks;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Our sounds, declared in {@code assets/frostworks/sounds.json}. The infusion borrows vanilla's powder
 * snow sounds for now (by event name): they can be pointed to other files there without touching the code.
 */
public final class FwSounds {
    private FwSounds() {}

    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT,
            Frostworks.MODID);

    /** The cryogenic infuser blowing cold gas on the item. */
    public static final DeferredHolder<SoundEvent, SoundEvent> INFUSE = SOUNDS.register("cryo_infuser.infuse",
            () -> SoundEvent.createVariableRangeEvent(Frostworks.id("cryo_infuser.infuse")));

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
