package com.frostworks.compat.jei;

import com.frostworks.Frostworks;
import com.frostworks.FwConfig;
import com.frostworks.content.infuser.FreezingRecipe;
import com.frostworks.registry.FwBlocks;
import com.frostworks.registry.FwFluids;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * JEI category "Freezing" ({@code frostworks:freezing}), laid out as the infusion one: what goes in
 * the basin (its items and fluids, read from the recipe), the cold gas the infuser uses (from the config), the arrow,
 * the result, and the infuser drawn over its basin, animated.
 */
public class FreezingCategory implements IRecipeCategory<FreezingRecipe> {

    public static final RecipeType<FreezingRecipe> TYPE =
            RecipeType.create(Frostworks.MODID, "freezing", FreezingRecipe.class);

    private static final int WIDTH = 177, HEIGHT = 80;

    private final IDrawable icon;
    private final IDrawable slot;
    private final AnimatedInfuser infuser = new AnimatedInfuser(true);

    public FreezingCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemStack(new ItemStack(FwBlocks.CRYO_INFUSER.get()));
        slot = gui.getSlotDrawable();
    }

    /** The server setting when a world is open (synced to the client), else its default. */
    private static int iceGas() {
        try {
            return FwConfig.get(FwConfig.INFUSER_ICE_GAS);
        } catch (RuntimeException e) {
            return FwConfig.INFUSER_ICE_GAS.getDefault();
        }
    }

    @Override
    public RecipeType<FreezingRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("frostworks.jei.freezing");
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
    public void setRecipe(IRecipeLayoutBuilder builder, FreezingRecipe holder, IFocusGroup focuses) {
        FreezingRecipe recipe = holder;
        builder.addSlot(RecipeIngredientRole.CATALYST, 7, 61).setBackground(slot, -1, -1)
                .addFluidStack(FwFluids.gas(), iceGas())
                .setFluidRenderer(Math.max(1, iceGas()), false, 16, 16);
        int x = 27;
        for (var fluid : recipe.getFluidIngredients()) {
            var fluidSlot = builder.addSlot(RecipeIngredientRole.INPUT, x, 61).setBackground(slot, -1, -1);
            for (var stack : fluid.getMatchingFluidStacks())
                fluidSlot.addFluidStack(stack.getFluid(), fluid.getRequiredAmount());
            fluidSlot.setFluidRenderer(fluid.getRequiredAmount(), false, 16, 16);
            x += 20;
        }
        for (var ingredient : recipe.getIngredients()) {
            builder.addSlot(RecipeIngredientRole.INPUT, x, 61).setBackground(slot, -1, -1).addIngredients(ingredient);
            x += 20;
        }
        int y = 60;
        for (ProcessingOutput output : recipe.getRollableResults()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 141, y).setBackground(slot, -1, -1)
                    .addItemStack(output.getStack());
            y -= 20;
        }
    }

    @Override
    public void draw(FreezingRecipe holder, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX,
                     double mouseY) {
        AllGuiTextures.JEI_SHADOW.render(graphics, 61, 63);
        AllGuiTextures.JEI_LONG_ARROW.render(graphics, 62, 66);
        infuser.draw(graphics, WIDTH / 2 - 7, 14);
        graphics.drawString(Minecraft.getInstance().font,
                Component.translatable("frostworks.jei.infusing.gas", iceGas()), 5, 51, 0xFF606060, false);
    }
}
