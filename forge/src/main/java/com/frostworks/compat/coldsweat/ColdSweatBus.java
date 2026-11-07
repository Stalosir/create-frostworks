package com.frostworks.compat.coldsweat;

import com.momosoftworks.coldsweat.api.event.core.init.DefaultTempModifiersEvent;
import com.momosoftworks.coldsweat.api.event.core.registry.TempModifierRegisterEvent;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

/** Cold Sweat's events on Forge's game bus, where it posts them. Called only when Cold Sweat is loaded. */
public final class ColdSweatBus {
    private ColdSweatBus() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TempModifierRegisterEvent.class,
                ColdSweatCompat::onRegister);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, DefaultTempModifiersEvent.class,
                ColdSweatCompat::onDefaults);
    }
}
