package com.frostworks.compat.jei;

import com.frostworks.Frostworks;
import com.frostworks.content.infuser.InfusingRecipe;
import com.frostworks.registry.FwBlocks;
import com.frostworks.registry.FwFluids;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * JEI category "Cryogenic infusion" ({@code frostworks:infusing}), laid out as Create's pressing
 * category: the item in, the cold gas with its amount (read from the recipe), the arrow, the item out, and the infuser
 * drawn in 3D, animated, over its shadow.
 */
public class InfusingCategory implements IRecipeCategory<InfusingRecipe> {

    public static final RecipeType<InfusingRecipe> TYPE =
            RecipeType.create(Frostworks.MODID, "infusing", InfusingRecipe.class);

    private static final int WIDTH = 177, HEIGHT = 70;

    private final IDrawable icon;
    private final IDrawable slot;
    private final AnimatedInfuser infuser = new AnimatedInfuser();

    public InfusingCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemStack(new ItemStack(FwBlocks.CRYO_INFUSER.get()));
        slot = gui.getSlotDrawable();
    }

    @Override
    public RecipeType<InfusingRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("frostworks.jei.infusing");
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
    public void setRecipe(IRecipeLayoutBuilder builder, InfusingRecipe holder, IFocusGroup focuses) {
        InfusingRecipe recipe = holder;
        var input = builder.addSlot(RecipeIngredientRole.INPUT, 27, 51).setBackground(slot, -1, -1)
                .addIngredients(recipe.getIngredients().get(0));
        // One fluid, the cold gas or (the chilled ores) the Frosium
        net.minecraftforge.fluids.FluidStack fluid = fluidOf(recipe);
        builder.addSlot(RecipeIngredientRole.INPUT, 7, 51).setBackground(slot, -1, -1)
                .addFluidStack(fluid.getFluid(), fluid.getAmount())
                .setFluidRenderer(Math.max(1, fluid.getAmount()), false, 16, 16);
        if (recipe.marks()) { // The same item, chilled, in step with the input
            java.util.List<ItemStack> chilled = new java.util.ArrayList<>();
            for (ItemStack stack : recipe.getIngredients().get(0).getItems())
                chilled.add(com.frostworks.content.cold.ChilledMark.mark(stack.copy()));
            var output = builder.addSlot(RecipeIngredientRole.OUTPUT, 131, 50).setBackground(slot, -1, -1)
                    .addItemStacks(chilled);
            builder.createFocusLink(input, output);
            return;
        }
        ItemStack result = recipe.getRollableResultsAsItemStacks().isEmpty() ? ItemStack.EMPTY
                : recipe.getRollableResultsAsItemStacks().get(0);
        builder.addSlot(RecipeIngredientRole.OUTPUT, 131, 50).setBackground(slot, -1, -1).addItemStack(result);
    }

    @Override
    public void draw(InfusingRecipe holder, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX,
                     double mouseY) {
        AllGuiTextures.JEI_SHADOW.render(graphics, 61, 41);
        AllGuiTextures.JEI_LONG_ARROW.render(graphics, 52, 54);
        infuser.draw(graphics, WIDTH / 2 - 17, 22);
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
                Component.translatable("frostworks.jei.infusing.gas", fluidOf(holder).getAmount()), 5, 41,
                0xFF606060, false);
    }

    /** The recipe's fluid: Frosium for the chilled ores, cold gas for the others. */
    private static net.minecraftforge.fluids.FluidStack fluidOf(InfusingRecipe recipe) {
        return recipe.frosiumAmount() > 0
                ? new net.minecraftforge.fluids.FluidStack(FwFluids.frosium(), recipe.frosiumAmount())
                : FwFluids.gas(recipe.gasAmount());
    }
}
