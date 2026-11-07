package com.frostworks;

import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

import com.frostworks.network.FwNetwork;
import com.frostworks.registry.FwBlockEntities;
import com.frostworks.registry.FwBlocks;
import com.frostworks.registry.FwCreativeTab;
import com.frostworks.registry.FwFluids;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(Frostworks.MODID)
public class Frostworks {
    public static final String MODID = "frostworks";
    public static final Logger LOGGER = LogUtils.getLogger();
    // No default tab: Registrate would add every item to the search tab, where vanilla already puts the items of our
    // own tab (crash "already exists in the tab's list" when the creative inventory opens).
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID)
            .defaultCreativeTab((ResourceKey<CreativeModeTab>) null);

    public Frostworks(IEventBus modBus, ModContainer container) {
        REGISTRATE.registerEventListeners(modBus);
        // Load the registry classes in dependency order (fluids, blocks, block entities)
        FwFluids.init();
        FwBlocks.init();
        FwBlockEntities.init();
        com.frostworks.registry.FwItems.init();
        com.frostworks.registry.FwChilledOres.init();
        com.frostworks.compat.display.FwDisplaySources.init(); // The Display Link on the cold vent
        // Cold Sweat, if loaded: its events (none of its classes is touched without it)
        if (com.frostworks.platform.Loader.isModLoaded(com.frostworks.compat.coldsweat.ColdSweatCompat.MODID))
            com.frostworks.compat.coldsweat.ColdSweatBus.register();
        com.frostworks.registry.FwAliases.register(modBus); // the ids renamed since the 1.0, kept as aliases (the table is in FwAliases)
        com.frostworks.registry.FwRecipeTypes.register(modBus);
        // The ingredient "perishable_food" (always: its registry is synced), TFC's side if loaded
        com.frostworks.content.infuser.PerishableFoodIngredient.register(modBus);
        if (com.frostworks.platform.Loader.isModLoaded(com.frostworks.compat.tfc.TfcFood.MODID))
            com.frostworks.compat.tfc.TfcFood.register(modBus);
        com.frostworks.registry.FwComponents.register(modBus); // frostworks:chilled
        com.frostworks.registry.FwSounds.register(modBus);
        FwCreativeTab.register(modBus);

        container.registerConfig(ModConfig.Type.SERVER, FwConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, FwClientConfig.SPEC);
        // The common config (read by the recipe conditions) and the conditions
        container.registerConfig(ModConfig.Type.COMMON, FwCommonConfig.SPEC);
        com.frostworks.registry.FwConditions.register(modBus);
        modBus.addListener(FwBlockEntities::registerCapabilities);
        modBus.addListener(EventPriority.HIGH, FwBlockEntities::guardCreateTanks);
        modBus.addListener(FwNetwork::register);
        modBus.addListener((FMLCommonSetupEvent e) -> FwBlocks.commonSetup(e::enqueueWork)); // the shared registry, the loader's event
        // Tough As Nails, if loaded (its list of modifiers is a plain list: on the main thread)
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(com.frostworks.compat.tan.ToughAsNailsCompat::init));
        modBus.addListener(com.frostworks.registry.FwDataMaps::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
