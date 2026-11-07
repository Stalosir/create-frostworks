package com.frostworks.content.infuser;

import java.util.List;

import com.frostworks.Frostworks;
import com.frostworks.FwConfig;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Food preserving ({@code frostworks:food_preserving}). The cryogenic infuser gives a TerraFirmaCraft
 * food one of our two preservation traits, for good: level 1 (cold gas, {@code coldPreservedModifier}, 0.4 by
 * default: lasts 2.5 times longer) or level 2 (Frosium, {@code deepColdPreservedModifier}, 0.25: 4 times).
 * <ul>
 * <li>one trait per level, its value read from the server config each time TFC computes a date (TFC reads the trait's
 * value on every computation: a single trait of varying strength would make the dates jump);</li>
 * <li>a level replaces the one below it (Frosium on a food done with gas: it goes up); never down, never twice;</li>
 * <li>with the traits of other mods, the pack's rule: they multiply (TFC has no exclusion between traits);</li>
 * <li>no renewal: TFC's applyTrait keeps the share of life already used; the date is then rounded as TFG and Firmalife
 * do, so stacks done a few hours apart stack together;</li>
 * <li>refused: no TFC food, rotten, never decaying (decay modifier 0, JEI's display stacks), already at this level or
 * above.</li>
 * </ul>
 * What touches TFC is the loader's ({@link Food}, in {@code compat/tfc}), installed only when TFC is loaded: without
 * it, nothing is food and no recipe of this type matches (our files are also under the condition {@code tfc}).
 */
public final class FoodPreserving {
    private FoodPreserving() {}

    /** The levels: none, cold gas, Frosium. */
    public static final int NONE = 0, COLD = 1, DEEP = 2;

    /** The trait ids, under our namespace. */
    public static final String COLD_TRAIT = "cold_preserved", DEEP_TRAIT = "deep_cold_preserved";

    /** TFC's food, by the loader's version of TFC. */
    public interface Food {
        /** A TFC food that decays: not rotten, not never-decaying. */
        boolean perishable(ItemStack stack);

        /** Our level on this food (NONE without our traits). */
        int level(ItemStack stack);

        /** Takes our trait of level {@code from} off (if any), puts the one of level {@code to}, rounds the date. */
        void setLevel(ItemStack stack, int from, int to);

        /** Some foods, for JEI (display stacks, never decaying). */
        List<ItemStack> examples();

        /** A display stack of this food at this level (JEI): the trait on a fresh one, then never decaying. */
        ItemStack display(ItemStack example, int level);
    }

    private static Food food;

    /** Called by the loader when TFC is loaded. */
    public static void install(Food tfc) {
        food = tfc;
    }

    public static boolean available() {
        return food != null;
    }

    /**
     * Foods that are never preserved, whatever they are to TFC: dried kelp (Minecraft's and TFC's), which the infuser
     * turns into cold dried kelp for the strip curtain door.
     */
    public static final TagKey<Item> NEVER_PRESERVED = TagKey.create(Registries.ITEM, Frostworks.id("never_preserved"));

    /** A food that can rot (the ingredient {@code frostworks:perishable_food}), and not one of {@link #NEVER_PRESERVED}. */
    public static boolean perishable(ItemStack stack) {
        return food != null && !stack.isEmpty() && !stack.is(NEVER_PRESERVED) && food.perishable(stack);
    }

    /** Can this level be given to this stack? A perishable food below that level. */
    public static boolean accepts(ItemStack stack, int level) {
        return perishable(stack) && food.level(stack) < level;
    }

    /** One item of the stack, with this level (the level below taken off). */
    public static ItemStack preserve(ItemStack input, int level) {
        ItemStack out = input.copyWithCount(1);
        if (food != null)
            food.setLevel(out, food.level(out), level);
        return out;
    }

    /** Some foods for JEI (empty without TFC). */
    public static List<ItemStack> examples() {
        return food == null ? List.of() : food.examples().stream().filter(s -> !s.is(NEVER_PRESERVED)).toList();
    }

    /** A display stack of this example at this level (JEI). */
    public static ItemStack display(ItemStack example, int level) {
        return food == null ? example : food.display(example, level);
    }

    /** The decay modifier of a level (server config, synced; its default before a world is loaded). */
    public static double modifier(int level) {
        return level == DEEP ? FwConfig.get(FwConfig.TFC_DEEP_COLD_PRESERVED)
                : level == COLD ? FwConfig.get(FwConfig.TFC_COLD_PRESERVED) : 1;
    }

    /** How many times longer a food of this level lasts, for JEI (e.g. "2.5"). */
    public static String lastsText(int level) {
        double times = 1 / modifier(level);
        return times == Math.rint(times) ? String.valueOf((int) times)
                : String.format(java.util.Locale.ROOT, "%.1f", times);
    }

    /** The trait id of a level (null for none). */
    public static String traitOf(int level) {
        return level == DEEP ? DEEP_TRAIT : level == COLD ? COLD_TRAIT : null;
    }
}
