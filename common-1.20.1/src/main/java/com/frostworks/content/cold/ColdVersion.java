package com.frostworks.content.cold;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.frostworks.registry.FwRecipeTypes;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

/** Minecraft 1.20.1 (Create 6.0.4): what cold processing needs from the recipe API of this version. */
public final class ColdVersion {
    private ColdVersion() {}

    /** Every cold processing file of this recipe manager, by id (sorted later). */
    public static Map<ResourceLocation, JsonObject> files(RecipeManager recipes) {
        Map<ResourceLocation, JsonObject> files = new LinkedHashMap<>();
        for (ColdProcessRecipe recipe : recipes.getAllRecipesFor(
                FwRecipeTypes.COLD_PROCESS.<RecipeType<ColdProcessRecipe>>getType()))
            files.put(recipe.getId(), recipe.json);
        return files;
    }

    /** Every cold fluid file of this recipe manager, by id. */
    public static Map<ResourceLocation, JsonObject> fluidFiles(RecipeManager recipes) {
        Map<ResourceLocation, JsonObject> files = new LinkedHashMap<>();
        for (ColdFluidRecipe recipe : recipes.getAllRecipesFor(
                FwRecipeTypes.COLD_FLUID.<RecipeType<ColdFluidRecipe>>getType()))
            files.put(recipe.getId(), recipe.json);
        return files;
    }

    /** An ingredient as recipes write it (an item or a tag), read by Minecraft's own reader. */
    public static Ingredient ingredient(JsonElement json, HolderLookup.Provider registries) {
        return Ingredient.fromJson(json);
    }

    /** A result as Create's processing recipes write it ({@code item}, {@code count}, {@code chance}), read by Create. */
    public static ProcessingOutput output(JsonElement json, HolderLookup.Provider registries) {
        return ProcessingOutput.deserialize(json);
    }

    public static ProcessingOutput output(ItemStack stack, float chance) {
        return new ProcessingOutput(stack, chance);
    }

    /** The results of a processing recipe (empty for any other recipe). */
    public static List<ProcessingOutput> results(Recipe<?> recipe) {
        return recipe instanceof ProcessingRecipe<?> processing ? processing.getRollableResults() : List.of();
    }

    /** Create's own roll of these results (with Create's own random in this version). */
    public static List<ItemStack> roll(Recipe<?> recipe, List<ProcessingOutput> results, RandomSource random) {
        return ((ProcessingRecipe<?>) recipe).rollResults(results);
    }

    public static ResourceLocation typeId(Recipe<?> recipe) {
        return BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
    }

    /** The first fluid result of a processing recipe (null: none), for the JEI page. */
    @org.jetbrains.annotations.Nullable
    public static net.minecraft.world.level.material.Fluid mainFluid(@org.jetbrains.annotations.Nullable Recipe<?> recipe) {
        if (!(recipe instanceof ProcessingRecipe<?> processing) || processing.getFluidResults().isEmpty())
            return null;
        return processing.getFluidResults().get(0).getFluid();
    }

    /** The normal recipe of this type for this item (null: none), for the JEI page. */
    @org.jetbrains.annotations.Nullable
    public static Recipe<?> normalRecipeFor(RecipeManager recipes, ResourceLocation type, ItemStack stack) {
        return normalRecipe(recipes, type, stack);
    }

    /** The recipes of this type, by id (the files that name a recipe). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Map<ResourceLocation, Recipe<?>> recipesOf(RecipeManager recipes, ResourceLocation type) {
        Map<ResourceLocation, Recipe<?>> byId = new LinkedHashMap<>();
        RecipeType recipeType = BuiltInRegistries.RECIPE_TYPE.get(type);
        if (recipeType != null)
            for (Object o : recipes.getAllRecipesFor(recipeType))
                byId.put(((Recipe<?>) o).getId(), (Recipe<?>) o);
        return byId;
    }

    /** Is there a normal recipe of this type with an ingredient that takes this item? */
    public static boolean hasNormalRecipe(RecipeManager recipes, ResourceLocation type, ItemStack stack) {
        return normalRecipe(recipes, type, stack) != null;
    }

    /** The main (first) result of that normal recipe, empty if none (the JEI page). */
    public static ItemStack mainResult(RecipeManager recipes, ResourceLocation type, ItemStack stack) {
        Recipe<?> recipe = normalRecipe(recipes, type, stack);
        List<ProcessingOutput> results = recipe == null ? List.of() : results(recipe);
        return results.isEmpty() ? ItemStack.EMPTY : results.get(0).getStack().copy();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Recipe<?> normalRecipe(RecipeManager recipes, ResourceLocation type, ItemStack stack) {
        RecipeType recipeType = BuiltInRegistries.RECIPE_TYPE.get(type);
        if (recipeType == null)
            return null;
        for (Object o : recipes.getAllRecipesFor(recipeType)) {
            Recipe<?> recipe = (Recipe<?>) o;
            for (Ingredient ingredient : recipe.getIngredients())
                if (ingredient.test(stack))
                    return recipe;
        }
        return null;
    }
}
