package com.frostworks.compat.jei;

import java.util.ArrayList;
import java.util.List;

import com.frostworks.Frostworks;
import com.frostworks.content.cold.ColdFluid;
import com.frostworks.content.cold.ColdFluids;
import com.frostworks.registry.FwBlocks;

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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * The "Cold fluids" category, one page per cold fluid recipe the client received: the fluid a vent takes (all the
 * fluids of a tag), the temperature it cools a room to, the cells one vent covers with it, what it uses per second. The
 * vent is its catalyst: its uses show the page, and so do the fluid's.
 */
public class ColdFluidCategory implements IRecipeCategory<ColdFluidCategory.Page> {

    public static final RecipeType<Page> TYPE = RecipeType.create(Frostworks.MODID, "cold_fluids", Page.class);

    private static final int WIDTH = 177, HEIGHT = 36, TEXT = 0xFF606060;

    /** One recipe and the fluids it takes (source fluids only). */
    public record Page(ColdFluid cold, List<Fluid> fluids) {}

    /**
     * The pages of the recipes the client knows, warmest first, each with the fluids it is the vent's recipe for; a
     * recipe left without any fluid (none in this game, or all taken by a colder or earlier recipe) has no page.
     */
    public static List<Page> recipes() {
        var level = Minecraft.getInstance().level;
        if (level == null)
            return List.of();
        List<Page> pages = new ArrayList<>();
        List<ColdFluid> all = new ArrayList<>(ColdFluids.all(level));
        java.util.Collections.reverse(all); // warmest first
        for (ColdFluid cold : all) {
            List<Fluid> fluids = new ArrayList<>();
            if (cold.fluid() != null)
                fluids.add(cold.fluid());
            else
                BuiltInRegistries.FLUID.getTag(cold.tag()).ifPresent(set -> set.forEach(holder -> {
                    Fluid fluid = holder.value();
                    if (fluid != Fluids.EMPTY && fluid.isSource(fluid.defaultFluidState()) && !fluids.contains(fluid))
                        fluids.add(fluid);
                }));
            // a fluid on the page of the recipe a vent uses for it only (a fluid in two recipes' tags)
            fluids.removeIf(fluid -> ColdFluids.of(level, fluid) != cold);
            if (!fluids.isEmpty())
                pages.add(new Page(cold, fluids));
        }
        return pages;
    }

    private final IDrawable icon;
    private final IDrawable slot;

    public ColdFluidCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemStack(new ItemStack(FwBlocks.VENT.get()));
        slot = gui.getSlotDrawable();
    }

    @Override
    public RecipeType<Page> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("frostworks.jei.cold_fluids");
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
    public void setRecipe(IRecipeLayoutBuilder builder, Page recipe, IFocusGroup focuses) {
        var input = builder.addSlot(RecipeIngredientRole.INPUT, 3, 10).setBackground(slot, -1, -1);
        for (Fluid fluid : recipe.fluids())
            input.addFluidStack(fluid, recipe.cold().amount()); // its use per second
        input.setFluidRenderer(recipe.cold().amount(), false, 16, 16);
    }

    @Override
    public void draw(Page recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        ColdFluid cold = recipe.cold();
        graphics.drawString(font, Component.translatable("frostworks.jei.cold_fluids.temperature", cold.temperature()),
                26, 2, TEXT, false);
        graphics.drawString(font, Component.translatable("frostworks.jei.cold_fluids.coverage", cold.coverage()),
                26, 13, TEXT, false);
        graphics.drawString(font, Component.translatable("frostworks.jei.cold_fluids.rate", cold.amount()),
                26, 24, TEXT, false);
    }
}
