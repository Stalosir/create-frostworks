package com.frostworks.registry;

import org.jetbrains.annotations.Nullable;

import com.frostworks.Frostworks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * {@code frostworks:ice_value}, what an item is worth to the distillation tower, in packed ice (the
 * unit; {@code towerFrosiumPerPackedIce} mB of Frosium each). Ours: ice 1/9, packed ice 1, blue ice 9
 * ({@code data/frostworks/data_maps/item/ice_value.json}). Another add-on adds its own matter in its data pack, e.g.
 * <pre>
 * {"values": {"mymod:dry_ice": {"value": 2.5}}}
 * </pre>
 */
public final class FwDataMaps {
    private FwDataMaps() {}

    /** An item's worth, in packed ice (more than 0). */
    public record IceValue(float value) {
        public static final Codec<IceValue> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.POSITIVE_FLOAT.fieldOf("value").forGetter(IceValue::value)).apply(i, IceValue::new));
    }

    public static final DataMapType<Item, IceValue> ICE_VALUE = DataMapType
            .builder(Frostworks.id("ice_value"), Registries.ITEM, IceValue.CODEC).build();

    public static void register(RegisterDataMapTypesEvent event) {
        event.register(ICE_VALUE);
    }

    /** What this stack's item is worth to the tower (packed ice), or 0 if it is not in the table. */
    public static float iceValue(ItemStack stack) {
        if (stack.isEmpty())
            return 0;
        @Nullable IceValue value = stack.getItemHolder().getData(ICE_VALUE);
        return value == null ? 0 : value.value();
    }
}
