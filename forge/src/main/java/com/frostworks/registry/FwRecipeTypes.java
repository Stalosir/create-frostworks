package com.frostworks.registry;

import java.util.function.Supplier;

import com.frostworks.Frostworks;
import com.frostworks.content.infuser.FreezingRecipe;
import com.frostworks.content.infuser.InfusingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeSerializer;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

/**
 * Our recipe types, built on Create's processing recipes (same JSON as Create's: {@code ingredients}
 * with items and fluid ingredients, {@code results}).
 */
public enum FwRecipeTypes implements IRecipeTypeInfo {
    /** Cryogenic infuser: an item + cold gas -> an item. */
    INFUSING(() -> new ProcessingRecipeSerializer<>(InfusingRecipe::new)),
    /** Cryogenic infuser over a basin: its content frozen with cold gas (water -> ice). */
    FREEZING(() -> new ProcessingRecipeSerializer<>(FreezingRecipe::new)),
    /** Distillation tower: its crafts, picked from what it holds. */
    DISTILLING(com.frostworks.content.column.DistillingRecipe.Serializer::new),
    /** Cold processing, a machine's results changed in a closed cold room (read by ColdProcessing). */
    COLD_PROCESS(com.frostworks.content.cold.ColdProcessRecipe.Serializer::new),
    /** Cold fluids, what a vent does with each fluid (read by ColdFluids). */
    COLD_FLUID(com.frostworks.content.cold.ColdFluidRecipe.Serializer::new),
    /** Cryogenic infuser, a TerraFirmaCraft food given a preservation trait (FoodPreserving). */
    FOOD_PRESERVING(() -> new ProcessingRecipeSerializer<>(com.frostworks.content.infuser.FoodPreservingRecipe::new));

    private final ResourceLocation id;
    private final Supplier<RecipeSerializer<?>> serializer;
    private final Supplier<RecipeType<?>> type;

    FwRecipeTypes(Supplier<RecipeSerializer<?>> serializer) {
        String name = name().toLowerCase(java.util.Locale.ROOT);
        this.id = Frostworks.id(name);
        this.serializer = Registers.SERIALIZERS.register(name, serializer);
        this.type = Registers.TYPES.register(name, () -> RecipeType.simple(id));
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends RecipeSerializer<?>> T getSerializer() {
        return (T) serializer.get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends RecipeType<?>> T getType() {
        return (T) type.get();
    }

    public static void register(IEventBus modBus) {
        Registers.SERIALIZERS.register(modBus);
        Registers.TYPES.register(modBus);
    }

    /** Apart from the enum: its constants are built before its own static fields. */
    private static final class Registers {
        static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
                DeferredRegister.create(Registries.RECIPE_SERIALIZER, Frostworks.MODID);
        static final DeferredRegister<RecipeType<?>> TYPES =
                DeferredRegister.create(Registries.RECIPE_TYPE, Frostworks.MODID);
    }
}
