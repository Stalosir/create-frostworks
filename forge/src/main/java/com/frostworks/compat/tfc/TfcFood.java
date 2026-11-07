package com.frostworks.compat.tfc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.frostworks.Frostworks;
import com.frostworks.content.infuser.FoodPreserving;

import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.capabilities.food.FoodDefinition;
import net.dries007.tfc.common.capabilities.food.FoodTrait;
import net.dries007.tfc.common.capabilities.food.IFood;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Food preserving on TerraFirmaCraft 3.2 (Forge 1.20.1). Our two traits in TFC's own list ({@code
 * FoodTrait.register}, at mod construction, as TFG, Firmalife and TFC Lunch Box do); the food is TFC's capability.
 * Loaded only when TFC is ({@link #register}): no class of TFC is touched without it.
 */
public final class TfcFood implements FoodPreserving.Food {
    public static final String MODID = "tfc";

    private static FoodTrait cold, deep;
    /** Display only, when TFC's food definitions are not there yet (JEI built before they arrive). */
    private static final TagKey<Item> FOODS = TagKey.create(Registries.ITEM, new ResourceLocation(MODID, "foods"));

    private TfcFood() {}

    /** The traits in TFC's list, and the food logic installed. */
    public static void register() {
        cold = trait(FoodPreserving.COLD);
        deep = trait(FoodPreserving.DEEP);
        FoodPreserving.install(new TfcFood());
    }

    private static FoodTrait trait(int level) {
        String name = FoodPreserving.traitOf(level);
        return FoodTrait.register(Frostworks.id(name), new FoodTrait(() -> (float) FoodPreserving.modifier(level),
                Frostworks.MODID + ".tooltip.food_trait." + name));
    }

    private static FoodTrait of(int level) {
        return level == FoodPreserving.DEEP ? deep : cold;
    }

    @Override
    public boolean perishable(ItemStack stack) {
        IFood food = FoodCapability.get(stack);
        // a negative date is one of TFC's flags: rotten, never decaying (the display stacks among them)
        return food != null && !food.isRotten() && !food.isTransientNonDecaying() && food.getCreationDate() >= 0
                && food.getDecayDateModifier() > 0;
    }

    @Override
    public int level(ItemStack stack) {
        return FoodCapability.hasTrait(stack, deep) ? FoodPreserving.DEEP
                : FoodCapability.hasTrait(stack, cold) ? FoodPreserving.COLD : FoodPreserving.NONE;
    }

    @Override
    public void setLevel(ItemStack stack, int from, int to) {
        IFood food = FoodCapability.get(stack);
        if (food == null)
            return;
        if (from != FoodPreserving.NONE)
            FoodCapability.removeTrait(food, of(from));
        FoodCapability.applyTrait(food, of(to));
        if (!food.isRotten() && food.getCreationDate() >= 0) // as TFG's shelf: rounded, so the stacks stack
            food.setCreationDate(FoodCapability.getRoundedCreationDate(food.getCreationDate()));
        // Forge 1.20.1 only: TFC's traits live in a capability, which Create's depot and belt do not compare
        // (isSameItemSameTags): a single food looked unchanged, its result was dropped and the infuser went on for
        // ever (seen in game). Our level in the tag too makes the change seen; same level, same tag: it stacks.
        stack.getOrCreateTag().putInt(LEVEL_TAG, to);
    }

    /** Our level, also in the stack's tag (see setLevel). */
    private static final String LEVEL_TAG = "frostworks:cold_preserved";

    @Override
    public List<ItemStack> examples() {
        Map<Item, ItemStack> found = new LinkedHashMap<>();
        for (FoodDefinition definition : FoodCapability.MANAGER.getValues())
            for (Item item : definition.getValidItems())
                found.putIfAbsent(item, new ItemStack(item));
        if (found.isEmpty() && ForgeRegistries.ITEMS.tags() != null)
            ForgeRegistries.ITEMS.tags().getTag(FOODS).forEach(item -> found.putIfAbsent(item, new ItemStack(item)));
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : found.values())
            out.add(FoodCapability.setStackNonDecaying(stack));
        return out;
    }

    @Override
    public ItemStack display(ItemStack example, int level) {
        ItemStack stack = new ItemStack(example.getItem());
        if (level != FoodPreserving.NONE)
            FoodCapability.applyTrait(stack, of(level));
        return FoodCapability.setStackNonDecaying(stack);
    }
}
