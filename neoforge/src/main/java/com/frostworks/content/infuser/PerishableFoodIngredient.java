package com.frostworks.content.infuser;

import java.util.stream.Stream;

import com.frostworks.Frostworks;
import com.mojang.serialization.MapCodec;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * {@code {"type": "frostworks:perishable_food"}}, any TerraFirmaCraft food that can rot
 * ({@link FoodPreserving#perishable}): known by TFC's food component, not by a tag. Its items (JEI) are some of TFC's
 * foods, as display stacks. Registered always (the registry of ingredient types is synced); without TFC it takes
 * nothing.
 */
public final class PerishableFoodIngredient implements ICustomIngredient {
    public static final PerishableFoodIngredient INSTANCE = new PerishableFoodIngredient();

    private static final DeferredRegister<IngredientType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, Frostworks.MODID);
    public static final DeferredHolder<IngredientType<?>, IngredientType<PerishableFoodIngredient>> TYPE =
            TYPES.register("perishable_food", () -> new IngredientType<>(MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE)));

    private PerishableFoodIngredient() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    @Override
    public boolean test(ItemStack stack) {
        return FoodPreserving.perishable(stack);
    }

    @Override
    public Stream<ItemStack> getItems() {
        return FoodPreserving.examples().stream();
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return TYPE.get();
    }
}
