package com.frostworks.registry;

import java.util.Map;

import com.frostworks.Frostworks;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * The old identifiers of the 1.0 that were renamed, as registry aliases (NeoForge's {@code Registry.addAlias}). A
 * lookup by an old name resolves to the new entry: the fluid in a saved tank or pipe (ours, Create's, any mod's), a
 * bucket in a chest, the fluid placed in the world, a pack's recipe that names it. Nothing is rewritten: a stack takes
 * its new name when it is saved again.
 * <p>
 * This table is, with the changelog and the historical reports, the only place the old name is kept.
 */
public final class FwAliases {
    private FwAliases() {}

    /** The old name, then the new one, per registry. */
    private static final Map<ResourceKey<?>, Map<String, String>> ALIASES = Map.of(
            Registries.FLUID, Map.of("liquid_nitrogen", "frosium", "flowing_liquid_nitrogen", "flowing_frosium"),
            NeoForgeRegistries.Keys.FLUID_TYPES, Map.of("liquid_nitrogen", "frosium"),
            Registries.BLOCK, Map.of("liquid_nitrogen", "frosium"),
            Registries.ITEM, Map.of("liquid_nitrogen_bucket", "frosium_bucket"));

    public static void register(IEventBus modBus) {
        modBus.addListener(FwAliases::onRegister);
    }

    private static void onRegister(RegisterEvent event) {
        Map<String, String> aliases = ALIASES.get(event.getRegistryKey());
        if (aliases == null)
            return;
        aliases.forEach((from, to) -> event.getRegistry().addAlias(Frostworks.id(from), Frostworks.id(to)));
        if (event.getRegistryKey() == Registries.FLUID)
            Frostworks.LOGGER.info("Frostworks 1.1: frostworks:liquid_nitrogen is now frostworks:frosium; the old ids "
                    + "are aliases (saved worlds and recipes that name them get Frosium)");
    }
}
