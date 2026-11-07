package com.frostworks.compat.display;

import static com.frostworks.Frostworks.REGISTRATE;

import com.frostworks.registry.FwBlocks;
import com.simibubi.create.api.behaviour.display.DisplaySource;

import net.minecraft.core.registries.Registries;

/**
 * The cold room's display sources, one per {@link RoomReading}, on the blocks of a room's structure (the cold vent
 * and the cold wall), tied to them once the blocks are registered (they do not exist yet when this runs).
 */
public final class FwDisplaySources {
    private FwDisplaySources() {}

    static {
        for (RoomReading reading : RoomReading.values())
            REGISTRATE.displaySource(reading.id(), () -> new RoomDisplaySource(reading))
                    .onRegisterAfter(Registries.BLOCK, source -> {
                        DisplaySource.BY_BLOCK.add(FwBlocks.VENT.get(), source);
                        DisplaySource.BY_BLOCK.add(FwBlocks.COLD_WALL.get(), source);
                    })
                    .register();
    }

    public static void init() {}
}
