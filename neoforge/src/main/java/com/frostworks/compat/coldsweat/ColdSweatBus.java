package com.frostworks.compat.coldsweat;

import com.momosoftworks.coldsweat.api.event.core.init.DefaultTempModifiersEvent;
import com.momosoftworks.coldsweat.api.event.core.registry.TempModifierRegisterEvent;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;

/** Cold Sweat's events on NeoForge's game bus, where it posts them. Called only when Cold Sweat is loaded. */
public final class ColdSweatBus {
    private ColdSweatBus() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TempModifierRegisterEvent.class,
                ColdSweatCompat::onRegister);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, DefaultTempModifiersEvent.class,
                ColdSweatCompat::onDefaults);
    }
}
