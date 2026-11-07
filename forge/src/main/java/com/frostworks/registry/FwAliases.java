package com.frostworks.registry;

import java.util.Map;

import com.frostworks.Frostworks;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.RegisterEvent;

/**
 * The old identifiers of the 1.0 that were renamed. Forge 1.20.1 has two ways, both used:
 * <ul>
 * <li>a registry alias ({@code ForgeRegistry.addAlias}, while the registry is open): a lookup by an old name resolves
 * to the new entry, as for the fluid in a saved tank or pipe (ours, Create's, any mod's), a bucket in a chest, a pack's
 * recipe that names it;</li>
 * <li>the missing mappings of a saved world ({@code MissingMappingsEvent}), remapped to the new entries (the ids of
 * the world's registry snapshot, the fluid placed in it).</li>
 * </ul>
 * Nothing is rewritten: a stack takes its new name when it is saved again. This table is, with the changelog and the
 * historical reports, the only place the old name is kept.
 */
public final class FwAliases {
    private FwAliases() {}

    /** The old name, then the new one, per registry. */
    private static final Map<ResourceKey<?>, Map<String, String>> ALIASES = Map.of(
            Registries.FLUID, Map.of("liquid_nitrogen", "frosium", "flowing_liquid_nitrogen", "flowing_frosium"),
            ForgeRegistries.Keys.FLUID_TYPES, Map.of("liquid_nitrogen", "frosium"),
            Registries.BLOCK, Map.of("liquid_nitrogen", "frosium"),
            Registries.ITEM, Map.of("liquid_nitrogen_bucket", "frosium_bucket"));

    public static void register(IEventBus modBus) {
        modBus.addListener(FwAliases::onRegister);
        MinecraftForge.EVENT_BUS.addListener(FwAliases::onMissing);
    }

    private static void onRegister(RegisterEvent event) {
        Map<String, String> aliases = ALIASES.get(event.getRegistryKey());
        if (aliases == null || !(event.getForgeRegistry() instanceof ForgeRegistry<?> registry))
            return;
        aliases.forEach((from, to) -> registry.addAlias(Frostworks.id(from), Frostworks.id(to)));
        if (event.getRegistryKey() == Registries.FLUID)
            Frostworks.LOGGER.info("Frostworks 1.1: frostworks:liquid_nitrogen is now frostworks:frosium; the old ids "
                    + "are aliases (saved worlds and recipes that name them get Frosium)");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onMissing(MissingMappingsEvent event) {
        ALIASES.forEach((key, aliases) -> {
            for (Object entry : event.getMappings((ResourceKey) key, Frostworks.MODID)) {
                MissingMappingsEvent.Mapping mapping = (MissingMappingsEvent.Mapping) entry;
                String to = aliases.get(mapping.getKey().getPath());
                IForgeRegistry registry = mapping.getRegistry();
                Object target = to == null ? null : registry.getValue(Frostworks.id(to));
                if (target != null)
                    mapping.remap(target);
            }
        });
    }
}
