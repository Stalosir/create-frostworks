package com.frostworks.registry;

import static com.frostworks.Frostworks.REGISTRATE;

import com.tterrag.registrate.util.entry.ItemEntry;

import net.minecraft.world.item.Item;

/**
 * The two infused items. Neither has a crafting recipe: they are only made in the cryogenic infuser
 * (recipes of type {@code frostworks:infusing}). Item models and textures are in the resources (placeholders copied
 * from Create and vanilla to be retouched).
 */
public final class FwItems {
    private FwItems() {}

    /** Iron sheet infused with cold gas: the material of everything cold (walls, frost tanks, bases, expander). */
    public static final ItemEntry<Item> COLD_IRON_SHEET = REGISTRATE.item("cold_iron_sheet", Item::new)
            .register();

    /** Dried kelp infused with cold gas: the strips of the strip curtain door. */
    public static final ItemEntry<Item> COLD_DRIED_KELP = REGISTRATE.item("cold_dried_kelp", Item::new)
            .register();

    public static void init() {}
}
