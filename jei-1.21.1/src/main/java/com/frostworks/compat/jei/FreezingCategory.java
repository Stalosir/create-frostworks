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
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * JEI category "Freezing" ({@code frostworks:freezing}), laid out as the infusion one: what goes in
 * the basin (its items and fluids, read from the recipe), the cold gas the infuser uses (from the config), the arrow,
 * the result, and the infuser drawn over its basin, animated.
 */
public class FreezingCategory implements IRecipeCategory<RecipeHolder<FreezingRecipe>> {

    public static final RecipeType<RecipeHolder<FreezingRecipe>> TYPE =
            RecipeType.createRecipeHolderType(Frostworks.id("freezing"));

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
    public RecipeType<RecipeHolder<FreezingRecipe>> getRecipeType() {
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
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<FreezingRecipe> holder, IFocusGroup focuses) {
        FreezingRecipe recipe = holder.value();
        builder.addSlot(RecipeIngredientRole.CATALYST, 7, 61).setBackground(slot, -1, -1)
                .addIngredient(NeoForgeTypes.FLUID_STACK, FwFluids.gas(iceGas()))
                .setFluidRenderer(Math.max(1, iceGas()), false, 16, 16);
        int x = 27;
        for (SizedFluidIngredient fluid : recipe.getFluidIngredients()) {
            builder.addSlot(RecipeIngredientRole.INPUT, x, 61).setBackground(slot, -1, -1)
                    .addIngredients(NeoForgeTypes.FLUID_STACK, java.util.List.of(fluid.getFluids()))
                    .setFluidRenderer(fluid.amount(), false, 16, 16);
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
    public void draw(RecipeHolder<FreezingRecipe> holder, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX,
                     double mouseY) {
        AllGuiTextures.JEI_SHADOW.render(graphics, 61, 63);
        AllGuiTextures.JEI_LONG_ARROW.render(graphics, 62, 66);
        infuser.draw(graphics, WIDTH / 2 - 7, 14);
        graphics.drawString(Minecraft.getInstance().font,
                Component.translatable("frostworks.jei.infusing.gas", iceGas()), 5, 51, 0xFF606060, false);
    }
}
