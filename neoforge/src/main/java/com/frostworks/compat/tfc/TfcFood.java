package com.frostworks.compat.tfc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.frostworks.Frostworks;
import com.frostworks.content.infuser.FoodPreserving;

import net.dries007.tfc.common.component.food.FoodCapability;
import net.dries007.tfc.common.component.food.FoodDefinition;
import net.dries007.tfc.common.component.food.FoodTrait;
import net.dries007.tfc.common.component.food.FoodTraits;
import net.dries007.tfc.common.component.food.IFood;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Food preserving on TerraFirmaCraft 4.2 (NeoForge 1.21.1). Our two traits in TFC's registry
 * ({@code tfc:food_trait}, synced), passed to TFC as holders; the food is TFC's data component. Loaded only when TFC is
 * ({@link #register}): no class of TFC is touched without it.
 */
public final class TfcFood implements FoodPreserving.Food {
    public static final String MODID = "tfc";

    private static final DeferredRegister<FoodTrait> TRAITS = DeferredRegister.create(FoodTraits.KEY, Frostworks.MODID);
    private static final DeferredHolder<FoodTrait, FoodTrait> COLD = trait(FoodPreserving.COLD);
    private static final DeferredHolder<FoodTrait, FoodTrait> DEEP = trait(FoodPreserving.DEEP);
    /** Display only, when TFC's food definitions are not there yet (JEI built before they arrive). */
    private static final TagKey<Item> FOODS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(MODID, "foods"));

    private static DeferredHolder<FoodTrait, FoodTrait> trait(int level) {
        String name = FoodPreserving.traitOf(level);
        return TRAITS.register(name, () -> new FoodTrait(() -> FoodPreserving.modifier(level),
                Frostworks.MODID + ".tooltip.food_trait." + name));
    }

    private TfcFood() {}

    /** The traits in TFC's registry, and the food logic installed. */
    public static void register(IEventBus modBus) {
        TRAITS.register(modBus);
        FoodPreserving.install(new TfcFood());
    }

    private static Holder<FoodTrait> holder(int level) {
        return level == FoodPreserving.DEEP ? DEEP : COLD;
    }

    @Override
    public boolean perishable(ItemStack stack) {
        IFood food = FoodCapability.get(stack);
        // a negative date is one of TFC's flags: rotten, never decaying (the display stacks among them)
        return food != null && !food.isRotten() && food.getCreationDate() >= 0 && food.getDecayDateModifier() > 0;
    }

    @Override
    public int level(ItemStack stack) {
        return FoodCapability.hasTrait(stack, DEEP) ? FoodPreserving.DEEP
                : FoodCapability.hasTrait(stack, COLD) ? FoodPreserving.COLD : FoodPreserving.NONE;
    }

    @Override
    public void setLevel(ItemStack stack, int from, int to) {
        if (from != FoodPreserving.NONE)
            FoodCapability.removeTrait(stack, holder(from));
        FoodCapability.applyTrait(stack, holder(to));
        FoodCapability.roundCreationDate(stack);
    }

    @Override
    public List<ItemStack> examples() {
        Map<Item, ItemStack> found = new LinkedHashMap<>();
        for (FoodDefinition definition : FoodCapability.MANAGER.getValues())
            for (ItemStack stack : definition.ingredient().getItems())
                found.putIfAbsent(stack.getItem(), new ItemStack(stack.getItem()));
        if (found.isEmpty())
            BuiltInRegistries.ITEM.getTagOrEmpty(FOODS).forEach(item -> found.putIfAbsent(item.value(), new ItemStack(item)));
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : found.values())
            out.add(FoodCapability.setNonDecaying(stack));
        return out;
    }

    /** A display stack of this food at this level (JEI): the trait on a fresh one, then never decaying. */
    @Override
    public ItemStack display(ItemStack example, int level) {
        ItemStack stack = new ItemStack(example.getItem());
        if (level != FoodPreserving.NONE)
            FoodCapability.applyTrait(stack, holder(level));
        return FoodCapability.setNonDecaying(stack);
    }
}
