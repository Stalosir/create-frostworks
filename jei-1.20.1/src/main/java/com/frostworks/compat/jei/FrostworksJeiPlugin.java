package com.frostworks.compat.jei;

import java.util.List;

import com.frostworks.Frostworks;
import com.frostworks.registry.FwBlocks;
import com.frostworks.registry.FwRecipeTypes;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Our JEI plugin, found by JEI alone through {@link JeiPlugin} (nothing else in the mod refers to this
 * package, so without JEI none of it is ever loaded). The "Cryogenic infusion" category with the infuser as its
 * catalyst; the two passages (made by a right click, not crafted) hidden from the list.
 */
@JeiPlugin
public class FrostworksJeiPlugin implements IModPlugin {

    /** The runtime once JEI is ready (the pilot opens the category through it). */
    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return Frostworks.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new InfusingCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new FreezingCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new DistillingCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new ColdProcessingCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new ColdFluidCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new PreservingCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            registration.addRecipes(InfusingCategory.TYPE,
                    level.getRecipeManager().getAllRecipesFor(FwRecipeTypes.INFUSING.<net.minecraft.world.item.crafting.RecipeType<com.frostworks.content.infuser.InfusingRecipe>>getType()));
            // The infuser over a basin
            registration.addRecipes(FreezingCategory.TYPE, level.getRecipeManager().getAllRecipesFor(
                    FwRecipeTypes.FREEZING.<net.minecraft.world.item.crafting.RecipeType<com.frostworks.content.infuser.FreezingRecipe>>getType()));
            // Food preserving (TerraFirmaCraft only: without it, no such recipe is loaded)
            registration.addRecipes(PreservingCategory.TYPE, level.getRecipeManager().getAllRecipesFor(
                    FwRecipeTypes.FOOD_PRESERVING.<net.minecraft.world.item.crafting.RecipeType<com.frostworks.content.infuser.FoodPreservingRecipe>>getType()));
        }
        // The distillation tower (its rate, with the server's settings once a world is open)
        registration.addRecipes(DistillingCategory.TYPE, DistillingCategory.recipes());
        // The cold processing files (the client has them once a world is open)
        registration.addRecipes(ColdProcessingCategory.TYPE, ColdProcessingCategory.recipes());
        // The cold fluids, what a vent does with each
        registration.addRecipes(ColdFluidCategory.TYPE, ColdFluidCategory.recipes());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(FwBlocks.CRYO_INFUSER.get()), InfusingCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(FwBlocks.CRYO_INFUSER.get()), PreservingCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(FwBlocks.CRYO_INFUSER.get()), FreezingCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(com.simibubi.create.AllBlocks.BASIN.get()), FreezingCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(FwBlocks.COLUMN_BASE.get()), DistillingCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(FwBlocks.RESERVOIR.get()), DistillingCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(com.simibubi.create.AllBlocks.CRUSHING_WHEEL.get()), ColdProcessingCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(com.simibubi.create.AllBlocks.MILLSTONE.get()), ColdProcessingCategory.TYPE);
        // The basin (mixing, compacting)
        registration.addRecipeCatalyst(new ItemStack(com.simibubi.create.AllBlocks.BASIN.get()), ColdProcessingCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(com.simibubi.create.AllBlocks.MECHANICAL_MIXER.get()), ColdProcessingCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(com.simibubi.create.AllBlocks.MECHANICAL_PRESS.get()), ColdProcessingCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(FwBlocks.VENT.get()), ColdFluidCategory.TYPE);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        jeiRuntime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, List.of(
                new ItemStack(FwBlocks.SHAFT_PASSAGE.get()), new ItemStack(FwBlocks.PIPE_PASSAGE.get())));
        // The old chilled ores of the 1.0, kept for the saved worlds (FwChilledOres)
        jeiRuntime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK,
                com.frostworks.registry.FwChilledOres.hidden());
    }

    /** Pilot: the recipes of chilled raw iron (raw iron, chilled). */
    public static void showChilled() {
        if (runtime != null)
            runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory().createFocus(
                    mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK,
                    com.frostworks.content.cold.ChilledMark.mark(new ItemStack(net.minecraft.world.item.Items.RAW_IRON))));
    }

    /** Pilot: what chilled raw iron goes into (its crushing recipe). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void showChilledUses() {
        if (runtime == null)
            return;
        var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(mezz.jei.api.recipe.RecipeIngredientRole.INPUT,
                VanillaTypes.ITEM_STACK, com.frostworks.content.cold.ChilledMark.mark(new ItemStack(net.minecraft.world.item.Items.RAW_IRON)));
        runtime.getJeiHelpers().getRecipeType(new ResourceLocation("create", "crushing")).ifPresent(type -> {
            var manager = runtime.getRecipeManager();
            mezz.jei.api.recipe.RecipeType t = type;
            List recipes = manager.createRecipeLookup(t).limitFocus(List.of(focus)).get().toList();
            runtime.getRecipesGui().showRecipes(manager.getRecipeCategory(t), recipes, List.of(focus));
        });
    }

    /** Pilot: the infusion recipes of the chilled ores, in their category. */
    public static void showChilledInfusion() {
        if (runtime == null)
            return;
        var manager = runtime.getRecipeManager();
        var category = manager.getRecipeCategory(InfusingCategory.TYPE);
        List<com.frostworks.content.infuser.InfusingRecipe> chilled =
                manager.createRecipeLookup(InfusingCategory.TYPE).get()
                        .filter(r -> r.frosiumAmount() > 0).toList();
        runtime.getRecipesGui().showRecipes(category, chilled, List.of());
    }

    /** Pilot: opens the "Distillation" category. */
    public static void showDistilling() {
        if (runtime != null)
            runtime.getRecipesGui().showTypes(List.of(DistillingCategory.TYPE));
    }

    /** Pilot: opens the "Freezing" category. */
    public static void showFreezing() {
        if (runtime != null)
            runtime.getRecipesGui().showTypes(List.of(FreezingCategory.TYPE));
    }

    /** Pilot: opens the "Cryogenic infusion" category. */
    public static void showInfusing() {
        if (runtime != null)
            runtime.getRecipesGui().showTypes(List.of(InfusingCategory.TYPE));
    }
}
