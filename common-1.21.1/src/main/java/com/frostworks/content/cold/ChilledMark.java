package com.frostworks.content.cold;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;

/**
 * The "chilled" state of one stack, on any item (another mod's raw ore or ore block): no new item, a component of
 * ours, {@code frostworks:chilled} (registered by the loader: {@code FwComponents}). Recipes still take the item; a
 * placed block loses it. Given by the cryogenic infuser, read by the cold processing files that ask for it
 * ({@code "chilled": true}), drawn as a frost layer over the item.
 */
public final class ChilledMark {
    private ChilledMark() {}

    public static final String KEY = "frostworks:chilled";

    /** The component: no value, there or not ({@code "frostworks:chilled": {}} in a recipe's result). */
    public static final DataComponentType<Unit> TYPE = DataComponentType.<Unit>builder()
            .persistent(Unit.CODEC).networkSynchronized(StreamCodec.unit(Unit.INSTANCE)).build();

    public static boolean is(ItemStack stack) {
        return !stack.isEmpty() && stack.has(TYPE);
    }

    /** Marks this stack (changed in place, returned). */
    public static ItemStack mark(ItemStack stack) {
        stack.set(TYPE, Unit.INSTANCE);
        return stack;
    }
}
