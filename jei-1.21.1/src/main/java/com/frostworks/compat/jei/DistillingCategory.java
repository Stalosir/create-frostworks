package com.frostworks.compat.jei;

import java.util.ArrayList;
import java.util.List;

import com.frostworks.Frostworks;
import com.frostworks.FwConfig;
import com.frostworks.content.column.Distillation;
import com.frostworks.content.column.DistillingRecipe;
import com.frostworks.registry.FwBlocks;
import com.frostworks.registry.FwDataMaps;
import com.frostworks.registry.FwFluids;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
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
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * JEI category "Distillation" ({@code frostworks:distilling}): what the distillation tower takes and
 * makes. One page per distilling recipe (the recipe files, {@link DistillingRecipe}); the tower runs on
 * a rate (as fast as it turns, one share per layer, ice used along), so a page shows the amounts for one packed ice,
 * the unit of the ice table (1000 mB made for a recipe without ice), worked out with the tower's own formulas and the
 * server's settings.
 * <p>
 * Our layout: the rotation on top in a dashed frame, its stress above it; under it the inputs one above the
 * other, liquids first, the ice last; our sprite of the column in the middle; the arrow going behind it to
 * the Frosium.
 */
public class DistillingCategory implements IRecipeCategory<DistillingCategory.Page> {

    public static final RecipeType<Page> TYPE = RecipeType.create(Frostworks.MODID, "distilling", Page.class);

    private static final ResourceLocation SPRITE = Frostworks.id("textures/gui/colum_spitre.png");
    private static final int SPRITE_W = 32, SPRITE_H = 65;
    private static final int WIDTH = 177, HEIGHT = 93;
    /**
     * Our draft: the inputs' column 11 px from the left edge (its slots drawn from x - 1), the output as far
     * from the right edge; the sprite midway between them, an arrow of the same length on each side, as far from both.
     */
    private static final int LEFT = 12, OUT = 147, SLOTS_Y = 32, ROW = 52, ARROW_LENGTH = 28;
    private static final int SPRITE_X = 72, SPRITE_Y = 28;
    /** The dashed frame of the rotation, over the inputs' column, and its rotor's centre. */
    private static final int FRAME_X = 10, FRAME_Y = 5, FRAME = 20;

    /** One craft of the tower: fluids and ice in (no ice: empty), a fluid out; the stress is that of a one-layer tower. */
    public record Page(List<FluidStack> fluids, List<ItemStack> ice, FluidStack result, int stress) {}

    /** The pages of the recipes the client knows (the mod's own Frosium before a world is open). */
    public static List<Page> recipes() {
        var level = Minecraft.getInstance().level;
        List<Distillation> all = level == null ? List.of() : DistillingRecipe.all(level);
        if (all.isEmpty())
            all = List.of(new Distillation(Distillation.FROSIUM, List.of(
                    new Distillation.Input(FwFluids.gas(), null, 0, 0), new Distillation.Input(Fluids.WATER, null, 0, 0)),
                    true, FwFluids.frosium(), true, 0, 0));
        List<Page> pages = new ArrayList<>();
        for (Distillation r : all)
            pages.add(page(r));
        return pages;
    }

    private static Page page(Distillation r) {
        double kGas = value(FwConfig.GAS_PER_STRESS), kLiquid = value(FwConfig.LIQUID_PER_ENERGY);
        int perLayer = value(FwConfig.TOWER_GAS_PER_LAYER), rpm = value(FwConfig.TOWER_REFERENCE_RPM);
        double stress = value(FwConfig.TOWER_STRESS_PER_RPM) * rpm;
        // what is made: for one packed ice, as TowerBlockEntity counts it
        double icePer = !r.ice ? 0 : r.energy ? 1.0 / value(FwConfig.TOWER_FROSIUM_PER_ICE) : r.icePerOutput;
        int made = icePer > 0 ? (int) Math.round(1 / icePer) : 1000;
        // the first input: one layer at its reference speed; energy yield: liquid = kLiquid x (energy of the gas + stress),
        // At most one mB per towerGasPerLiquid mB of gas (TowerBlockEntity.energyLiquid): 3500 mB for 2000 mB
        double first = r.energy
                ? made * Math.max(value(FwConfig.TOWER_GAS_PER_LIQUID), perLayer / (kLiquid * (perLayer / kGas + stress)))
                : made / r.perInput;
        List<FluidStack> fluids = new ArrayList<>();
        for (int i = 0; i < r.inputs.size(); i++) {
            double per = r.inputs.get(i).perOutput();
            if (r.energy && per <= 0)
                per = value(FwConfig.TOWER_WATER_PER_LIQUID);
            var shown = r.inputs.get(i).shown();
            if (shown != null)
                fluids.add(new FluidStack(shown, Math.max(1, (int) Math.round(i == 0 ? first : made * per))));
        }
        return new Page(fluids, icePer > 0 ? iceForOne() : List.of(), new FluidStack(r.result, made),
                (int) Math.round(stress));
    }

    /**
     * What makes one packed ice: the items of the ice table worth a whole fraction of it (9 ice, 1 packed ice). A
     * client on a server may not know the table (it stays on the server): then the mod's own values.
     */
    private static List<ItemStack> iceForOne() {
        List<ItemStack> ice = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            double v = FwDataMaps.iceValue(new ItemStack(item));
            long count = v > 0 ? Math.round(1 / v) : 0;
            if (v > 0 && v <= 1 && Math.abs(1 / v - count) < 0.01)
                ice.add(new ItemStack(item, (int) count));
        }
        if (ice.isEmpty())
            return List.of(new ItemStack(Items.PACKED_ICE), new ItemStack(Items.ICE, 9));
        ice.sort(java.util.Comparator.comparingInt(ItemStack::getCount));
        return ice;
    }

    /** The server setting when a world is open (synced to the client), else its default. */
    private static int value(ModConfigSpec.IntValue v) {
        try {
            return FwConfig.get(v);
        } catch (RuntimeException e) {
            return v.getDefault();
        }
    }

    private static double value(ModConfigSpec.DoubleValue v) {
        try {
            return FwConfig.get(v);
        } catch (RuntimeException e) {
            return v.getDefault();
        }
    }

    private final IDrawable icon;
    private final IDrawable slot;
    private final Rotor rotor = new Rotor();

    public DistillingCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemStack(new ItemStack(FwBlocks.COLUMN_BASE.get()));
        slot = gui.getSlotDrawable();
    }

    @Override
    public RecipeType<Page> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("frostworks.jei.distilling");
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
        // the inputs one above the other, the liquids first, the ice last
        int y = SLOTS_Y;
        for (FluidStack fluid : recipe.fluids()) {
            builder.addSlot(RecipeIngredientRole.INPUT, LEFT, y).setBackground(slot, -1, -1)
                    .addIngredient(NeoForgeTypes.FLUID_STACK, fluid)
                    .setFluidRenderer(fluid.getAmount(), false, 16, 16);
            y += 20;
        }
        if (!recipe.ice().isEmpty())
            builder.addSlot(RecipeIngredientRole.INPUT, LEFT, y).setBackground(slot, -1, -1)
                    .addItemStacks(recipe.ice());
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUT, ROW).setBackground(slot, -1, -1)
                .addIngredient(NeoForgeTypes.FLUID_STACK, recipe.result())
                .setFluidRenderer(recipe.result().getAmount(), false, 16, 16);
    }

    @Override
    public void draw(Page recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        // the rotation: the turning rotor in a dashed frame, its stress on the right
        var font = Minecraft.getInstance().font;
        dashedFrame(graphics, FRAME_X, FRAME_Y, FRAME, FRAME);
        rotor.draw(graphics, FRAME_X + 3, FRAME_Y + 17); // the model's centre lands 7 px right, 7 px up of this point
        Component stress = Component.translatable("frostworks.jei.distilling.stress", recipe.stress());
        graphics.drawString(font, stress, FRAME_X + FRAME + 4, FRAME_Y + (FRAME - 8) / 2, 0xFF606060, false);
        // an arrow in, the column, an arrow out, all on the output's row, each arrow midway in its gap
        int arrowY = ROW + 4;
        arrow(graphics, (LEFT + 17 + SPRITE_X) / 2 - ARROW_LENGTH / 2 + 2, arrowY); // + 2: as wide gaps as on the right (seen)
        graphics.blit(SPRITE, SPRITE_X, SPRITE_Y, 0, 0, SPRITE_W, SPRITE_H, SPRITE_W, SPRITE_H);
        arrow(graphics, (SPRITE_X + SPRITE_W + OUT - 1) / 2 - ARROW_LENGTH / 2, arrowY);
    }

    /** Create's JEI arrow, its tail cut to {@link #ARROW_LENGTH} (the head kept). */
    private static void arrow(GuiGraphics graphics, int x, int y) {
        AllGuiTextures a = AllGuiTextures.JEI_ARROW;
        graphics.blit(a.getLocation(), x, y, a.getStartX() + a.getWidth() - ARROW_LENGTH, a.getStartY(), ARROW_LENGTH,
                a.getHeight());
    }

    /** A frame of dashes around the rotation (2 px on, 2 px off), JEI's grey. */
    private static void dashedFrame(GuiGraphics graphics, int x, int y, int w, int h) {
        int color = 0xFF8B8B8B;
        for (int i = 0; i < w; i += 4) {
            graphics.fill(x + i, y, x + Math.min(i + 2, w), y + 1, color);
            graphics.fill(x + i, y + h - 1, x + Math.min(i + 2, w), y + h, color);
        }
        for (int i = 0; i < h; i += 4) {
            graphics.fill(x, y + i, x + 1, y + Math.min(i + 2, h), color);
            graphics.fill(x + w - 1, y + i, x + w, y + Math.min(i + 2, h), color);
        }
    }

    /** Create's cogwheel and its shaft, turning, as its JEI categories draw their rotation. */
    static class Rotor extends AnimatedKinetics {
        @Override
        public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
            PoseStack ms = graphics.pose();
            ms.pushPose();
            ms.translate(xOffset, yOffset, 100);
            // facing the viewer (its axis turned from Y to Z), spinning about its own axis, its shaft in the middle
            blockElement(shaft(Direction.Axis.Y)).rotateBlock(90, getCurrentAngle(), 0).scale(14).render(graphics);
            blockElement(AllPartialModels.SHAFTLESS_COGWHEEL).rotateBlock(90, getCurrentAngle(), 0).scale(14)
                    .render(graphics);
            ms.popPose();
        }
    }
}
