package com.frostworks.compat.jei;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.frostworks.Frostworks;
import com.frostworks.content.infuser.FoodPreserving;
import com.frostworks.content.infuser.FoodPreservingRecipe;
import com.frostworks.registry.FwBlocks;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * JEI category "Preserving" ({@code frostworks:food_preserving}), laid out as the infusion's: TFC's
 * foods in (the examples cycle), the fluid with its amount, the arrow, the same food with our trait out (in step with the
 * input), the infuser, and how many times longer it lasts (read from the config).
 */
public class PreservingCategory implements IRecipeCategory<RecipeHolder<FoodPreservingRecipe>> {

    public static final RecipeType<RecipeHolder<FoodPreservingRecipe>> TYPE =
            RecipeType.createRecipeHolderType(Frostworks.id("food_preserving"));

    private static final int WIDTH = 177, HEIGHT = 70;

    private final IDrawable icon;
    private final IDrawable slot;
    private final AnimatedInfuser infuser = new AnimatedInfuser();

    public PreservingCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemStack(new ItemStack(FwBlocks.CRYO_INFUSER.get()));
        slot = gui.getSlotDrawable();
    }

    @Override
    public RecipeType<RecipeHolder<FoodPreservingRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("frostworks.jei.food_preserving");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<FoodPreservingRecipe> holder, IFocusGroup focuses) {
        FoodPreservingRecipe recipe = holder.value();
        List<ItemStack> inputs = new ArrayList<>(), outputs = new ArrayList<>();
        for (ItemStack example : FoodPreserving.examples()) {
            inputs.add(example);
            outputs.add(FoodPreserving.display(example, recipe.level()));
        }
        var input = builder.addSlot(RecipeIngredientRole.INPUT, 27, 51).setBackground(slot, -1, -1)
                .addItemStacks(inputs);
        List<FluidStack> fluids = fluids(recipe);
        builder.addSlot(RecipeIngredientRole.INPUT, 7, 51).setBackground(slot, -1, -1)
                .addIngredients(NeoForgeTypes.FLUID_STACK, fluids)
                .setFluidRenderer(Math.max(1, amount(recipe)), false, 16, 16);
        var output = builder.addSlot(RecipeIngredientRole.OUTPUT, 131, 50).setBackground(slot, -1, -1)
                .addItemStacks(outputs);
        builder.createFocusLink(input, output);
    }

    @Override
    public void draw(RecipeHolder<FoodPreservingRecipe> holder, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        AllGuiTextures.JEI_SHADOW.render(graphics, 61, 41);
        AllGuiTextures.JEI_LONG_ARROW.render(graphics, 52, 54);
        infuser.draw(graphics, WIDTH / 2 - 17, 22);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable("frostworks.jei.infusing.gas", amount(holder.value())), 5, 41,
                0xFF606060, false);
        Component lasts = Component.translatable("frostworks.jei.food_preserving.lasts",
                FoodPreserving.lastsText(holder.value().level()));
        graphics.drawString(font, lasts, WIDTH - 4 - font.width(lasts), 5, 0xFF606060, false);
    }

    private static List<FluidStack> fluids(FoodPreservingRecipe recipe) {
        if (recipe.getFluidIngredients().isEmpty())
            return List.of();
        return Arrays.asList(recipe.getFluidIngredients().get(0).getFluids());
    }

    private static int amount(FoodPreservingRecipe recipe) {
        return recipe.gasAmount() + recipe.liquidAmount();
    }
}
