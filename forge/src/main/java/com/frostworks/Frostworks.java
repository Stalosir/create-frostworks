package com.frostworks;

import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
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
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Frostworks.MODID)
public class Frostworks {
    public static final String MODID = "frostworks";
    public static final Logger LOGGER = LogUtils.getLogger();
    // No default tab: Registrate would add every item to the search tab, where vanilla already puts the items of our
    // own tab (crash "already exists in the tab's list" when the creative inventory opens).
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID)
            .defaultCreativeTab((ResourceKey<CreativeModeTab>) null);

    public Frostworks() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        REGISTRATE.registerEventListeners(modBus);
        // Load the registry classes in dependency order (fluids, blocks, block entities)
        FwFluids.init();
        FwBlocks.init();
        FwBlockEntities.init();
        // Greate, if loaded: the shaft passage for its shafts (its block entity implements an interface of Greate's)
        if (com.frostworks.platform.Loader.isModLoaded(com.frostworks.compat.greate.GreatePassages.MODID))
            com.frostworks.compat.greate.GreatePassages.init();
        com.frostworks.registry.FwItems.init();
        com.frostworks.registry.FwChilledOres.init();
        com.frostworks.compat.display.FwDisplaySources.init(); // The Display Link on the cold vent
        // Cold Sweat, if loaded: its events (none of its classes is touched without it)
        if (com.frostworks.platform.Loader.isModLoaded(com.frostworks.compat.coldsweat.ColdSweatCompat.MODID))
            com.frostworks.compat.coldsweat.ColdSweatBus.register();
        com.frostworks.registry.FwAliases.register(modBus); // the ids renamed since the 1.0, kept as aliases (the table is in FwAliases)
        com.frostworks.registry.FwRecipeTypes.register(modBus);
        // The ingredient "perishable_food" (always), TFC's traits if loaded
        com.frostworks.content.infuser.PerishableFoodIngredient.register();
        if (com.frostworks.platform.Loader.isModLoaded(com.frostworks.compat.tfc.TfcFood.MODID))
            com.frostworks.compat.tfc.TfcFood.register();
        com.frostworks.registry.FwSounds.register(modBus);
        FwCreativeTab.register(modBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, FwConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, FwClientConfig.SPEC);
        // The common config (read by the recipe conditions) and the conditions
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FwCommonConfig.SPEC);
        com.frostworks.registry.FwConditions.register();
        FwNetwork.register();
        modBus.addListener((FMLCommonSetupEvent e) -> FwBlocks.commonSetup(e::enqueueWork)); // the shared registry, the loader's event
        if (com.frostworks.platform.Loader.isModLoaded(com.frostworks.compat.greate.GreatePassages.MODID))
            modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(com.frostworks.compat.greate.GreatePassages::commonSetup));
        // Tough As Nails, if loaded (its list of modifiers is a plain list: on the main thread)
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(com.frostworks.compat.tan.ToughAsNailsCompat::init));
        // the ice values (a NeoForge data map at the root): read from the same file by a reload listener
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(com.frostworks.registry.FwDataMaps::register);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
