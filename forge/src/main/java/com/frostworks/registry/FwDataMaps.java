package com.frostworks.registry;

import java.util.HashMap;
import java.util.Map;

import com.frostworks.Frostworks;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AddReloadListenerEvent;

/**
 * {@code frostworks:ice_value}, what an item is worth to the distillation tower, in packed ice (the
 * unit; {@code towerFrosiumPerPackedIce} mB of Frosium each). Ours: ice 1/9, packed ice 1, blue ice 9
 * ({@code data/frostworks/data_maps/item/ice_value.json}). Another add-on adds its own matter in its data pack, e.g.
 * <pre>
 * {"values": {"mymod:dry_ice": {"value": 2.5}}}
 * </pre>
 * Forge 1.20.1 has no data maps: the same files ({@code data/<namespace>/data_maps/item/ice_value.json}, the format of
 * NeoForge's data maps) are read here at each data reload.
 */
public final class FwDataMaps {
    private FwDataMaps() {}

    private static final Map<Item, Float> VALUES = new HashMap<>();

    public static void register(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    /** What this stack's item is worth to the tower (packed ice), or 0 if it is not in the table. */
    public static float iceValue(ItemStack stack) {
        if (stack.isEmpty())
            return 0;
        return VALUES.getOrDefault(stack.getItem(), 0f);
    }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(new Gson(), "data_maps/item");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            VALUES.clear();
            files.forEach((file, json) -> {
                if (!file.getPath().equals("ice_value") || !json.isJsonObject())
                    return;
                JsonObject values = json.getAsJsonObject().getAsJsonObject("values");
                if (values == null)
                    return;
                values.entrySet().forEach(e -> {
                    Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(e.getKey()));
                    float value = e.getValue().isJsonObject() ? e.getValue().getAsJsonObject().get("value").getAsFloat()
                            : e.getValue().getAsFloat();
                    if (value > 0 && item != net.minecraft.world.item.Items.AIR)
                        VALUES.put(item, value);
                });
            });
            Frostworks.LOGGER.debug("Ice values: {}", VALUES.size());
        }
    }
}
