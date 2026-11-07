package com.frostworks.content.cold;

import net.minecraft.world.item.ItemStack;

/**
 * The "chilled" state of one stack, on any item (another mod's raw ore or ore block): no new item, a mark in the
 * stack's NBT. Recipes still take the item; a placed block loses it. Given by the cryogenic infuser, read by the cold
 * processing files that ask for it ({@code "chilled": true}), drawn as a frost layer over the item.
 */
public final class ChilledMark {
    private ChilledMark() {}

    public static final String KEY = "frostworks:chilled";

    public static boolean is(ItemStack stack) {
        return !stack.isEmpty() && stack.getTag() != null && stack.getTag().getBoolean(KEY);
    }

    /** Marks this stack (changed in place, returned). */
    public static ItemStack mark(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(KEY, true);
        return stack;
    }
}
