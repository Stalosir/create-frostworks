package com.frostworks.registry;

import com.frostworks.Frostworks;
import com.frostworks.content.cold.ChilledMark;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Our item components: {@code frostworks:chilled} ({@link ChilledMark}). */
public final class FwComponents {
    private FwComponents() {}

    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Frostworks.MODID);

    static {
        COMPONENTS.register("chilled", () -> ChilledMark.TYPE);
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
