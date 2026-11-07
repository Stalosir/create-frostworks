package com.frostworks.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.frostworks.Frostworks;
import com.frostworks.content.cold.ColdProcess;
import com.frostworks.content.cold.ColdVersion;
import com.google.gson.JsonObject;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * The "Cold processing" category, one page per cold processing file the client received: its ingredients, what
 * it does (replace or bonus, for which process and recipe), then a row per step, warmest first: the temperature and its results,
 * each with its chance under it. A copy of the recipe's main result is shown as that result, in step with the input
 * (JEI's focus link: the input shown and its result go together). At the basin, its fluids (ingredients and results).
 */
public class ColdProcessingCategory implements IRecipeCategory<ColdProcessingCategory.Page> {

    public static final RecipeType<Page> TYPE = RecipeType.create(Frostworks.MODID, "cold_processing", Page.class);

    private static final int WIDTH = 177, HEADER = 34, ROW = 28, MAX_STEPS = 3, MAX_SLOTS = 6;
    private static final int HEIGHT = HEADER + MAX_STEPS * ROW;
    private static final int SLOTS_X = 52, SLOT_STEP = 21, TEXT = 0xFF606060;
    /** The amount a fluid ingredient is shown with (the craft decides how much it uses). */
    private static final int FLUID_SHOWN = 1000;

    /** One result slot: its items (several when it follows the input) or its fluid (basin, mB), its chance. */
    public record Slot(List<ItemStack> stacks, @Nullable Fluid fluid, int amount, float chance, boolean linked) {
        Slot(List<ItemStack> stacks, float chance, boolean linked) {
            this(stacks, null, 0, chance, linked);
        }
    }

    public record Row(int temperature, List<Slot> slots) {}

    /** One file: the inputs shown (those with a normal recipe when a copy follows them), its rows, warmest first. */
    public record Page(ColdProcess cold, List<ItemStack> inputs, List<Fluid> fluidInputs, boolean linked,
                       boolean replacesLast, List<Row> rows) {}

    /** The pages of the files the client knows, in the order of their ids; a broken file has no page. */
    public static List<Page> recipes() {
        var level = Minecraft.getInstance().level;
        if (level == null)
            return List.of();
        RecipeManager recipes = level.getRecipeManager();
        List<Page> pages = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonObject> file : new TreeMap<>(ColdVersion.files(recipes)).entrySet()) {
            try {
                Page page = page(ColdProcess.read(file.getKey(), file.getValue(), level.registryAccess()), recipes);
                // a file with nothing to show here (a copy of a result no recipe of this game gives) has no page
                if (page.rows().stream().anyMatch(row -> !row.slots().isEmpty()))
                    pages.add(page);
            } catch (RuntimeException e) {
                // the server's log says why (ColdProcessing); nothing to show
            }
        }
        return pages;
    }

    private static Page page(ColdProcess cold, RecipeManager recipes) {
        // the recipe the file names (null: every recipe of the process)
        Recipe<?> named = cold.recipe() == null ? null : ColdVersion.recipesOf(recipes, cold.process()).get(cold.recipe());
        List<ItemStack> all = new ArrayList<>();
        List<Fluid> fluidInputs = new ArrayList<>();
        for (ColdProcess.Entry entry : cold.ingredients()) {
            if (entry.item() != null) {
                for (ItemStack stack : entry.item().getItems())
                    // a chilled ingredient: its items shown chilled
                    all.add(entry.chilled() ? com.frostworks.content.cold.ChilledMark.mark(stack.copy()) : stack.copy());
            } else if (entry.fluid() != null) {
                fluidInputs.add(entry.fluid());
            } else {
                for (var holder : BuiltInRegistries.FLUID.getTagOrEmpty(entry.fluidTag()))
                    if (holder.value().isSource(holder.value().defaultFluidState()))
                        fluidInputs.add(holder.value());
            }
        }
        // no item ingredient, a recipe named: that recipe's first ingredient
        if (all.isEmpty() && named != null && !named.getIngredients().isEmpty())
            for (ItemStack stack : named.getIngredients().get(0).getItems())
                all.add(stack.copy());
        boolean copies = cold.steps().stream().anyMatch(s -> !s.copies().isEmpty());
        // with a copy of the main result: only the inputs that have a normal recipe, each with that recipe's result
        List<ItemStack> inputs = all, mains = List.of();
        boolean linked = false;
        if (copies && named != null) {
            List<ProcessingOutput> results = ColdVersion.results(named);
            if (!results.isEmpty())
                mains = List.of(results.get(0).getStack().copy());
        } else if (copies) {
            List<ItemStack> withRecipe = new ArrayList<>(), main = new ArrayList<>();
            for (ItemStack stack : all) {
                ItemStack result = ColdVersion.mainResult(recipes, cold.process(), stack);
                if (!result.isEmpty()) {
                    withRecipe.add(stack);
                    main.add(result);
                }
            }
            if (!withRecipe.isEmpty()) {
                inputs = withRecipe;
                mains = main;
                linked = true;
            }
        }
        // a copy of the first fluid result: the named recipe's, or the first input's recipe's
        Fluid mainFluid = null;
        if (cold.steps().stream().anyMatch(s -> !s.fluidCopies().isEmpty()))
            mainFluid = named != null ? ColdVersion.mainFluid(named) : inputs.isEmpty() ? null
                    : ColdVersion.mainFluid(ColdVersion.normalRecipeFor(recipes, cold.process(), inputs.get(0)));
        List<Row> rows = new ArrayList<>();
        for (int s = cold.steps().size() - 1; s >= 0; s--) { // warmest first
            ColdProcess.Step step = cold.steps().get(s);
            List<Slot> slots = new ArrayList<>();
            for (ProcessingOutput output : step.outputs())
                slots.add(new Slot(List.of(output.getStack().copy()), output.getChance(), false));
            if (!mains.isEmpty())
                for (ColdProcess.Copy copy : step.copies()) {
                    List<ItemStack> stacks = new ArrayList<>();
                    for (ItemStack main : mains)
                        stacks.add(main.copyWithCount(copy.count()));
                    slots.add(new Slot(stacks, copy.chance(), linked));
                }
            for (ColdProcess.FluidOut out : step.fluids())
                slots.add(new Slot(List.of(), out.fluid(), out.amount(), out.chance(), false));
            if (mainFluid != null)
                for (ColdProcess.FluidCopy copy : step.fluidCopies())
                    slots.add(new Slot(List.of(), mainFluid, copy.amount(), copy.chance(), false));
            rows.add(new Row(step.temperature(), slots));
        }
        boolean replacesLast = cold.steps().stream().anyMatch(ColdProcess.Step::replacesLast);
        return new Page(cold, inputs, List.copyOf(fluidInputs), linked, replacesLast, rows);
    }

    private final IDrawable icon;
    private final IDrawable slot;

    public ColdProcessingCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemStack(new ItemStack(AllBlocks.CRUSHING_WHEEL.get()));
        slot = gui.getSlotDrawable();
    }

    @Override
    public RecipeType<Page> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("frostworks.jei.cold_processing");
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
        IRecipeSlotBuilder input = builder.addSlot(RecipeIngredientRole.INPUT, 2, 2).setBackground(slot, -1, -1)
                .addItemStacks(recipe.inputs());
        // the basin: the fluid ingredients, under the items
        if (!recipe.fluidInputs().isEmpty()) {
            IRecipeSlotBuilder fluids = builder.addSlot(RecipeIngredientRole.INPUT, 2, recipe.inputs().isEmpty() ? 2 : 20)
                    .setBackground(slot, -1, -1).setFluidRenderer(FLUID_SHOWN, false, 16, 16);
            for (Fluid fluid : recipe.fluidInputs())
                fluids.addFluidStack(fluid, FLUID_SHOWN);
        }
        List<IRecipeSlotBuilder> linked = new ArrayList<>();
        linked.add(input);
        for (int r = 0; r < Math.min(recipe.rows().size(), MAX_STEPS); r++) {
            List<Slot> slots = recipe.rows().get(r).slots();
            for (int i = 0; i < Math.min(slots.size(), MAX_SLOTS); i++) {
                Slot shown = slots.get(i);
                IRecipeSlotBuilder out = builder.addSlot(RecipeIngredientRole.OUTPUT, SLOTS_X + i * SLOT_STEP,
                        HEADER + r * ROW + 1).setBackground(slot, -1, -1);
                if (shown.fluid() != null)
                    out.setFluidRenderer(shown.amount(), false, 16, 16).addFluidStack(shown.fluid(), shown.amount());
                else
                    out.addItemStacks(shown.stacks());
                if (slots.get(i).linked())
                    linked.add(out);
            }
        }
        if (recipe.linked() && linked.size() > 1)
            builder.createFocusLink(linked.toArray(new IRecipeSlotBuilder[0]));
    }

    @Override
    public void draw(Page recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        // what the file does, next to its input, on three lines at most (with the recipe it is for)
        ResourceLocation process = recipe.cold().process();
        Component name = Component.translatableWithFallback(
                "frostworks.jei.cold_processing.process." + process.getNamespace() + "." + process.getPath(),
                process.toString());
        // a file with a step that applies anywhere says so (its row comes first, before the cold steps)
        boolean anywhere = recipe.rows().stream().anyMatch(row -> row.temperature() == ColdProcess.ANYWHERE);
        boolean one = recipe.cold().recipe() != null;
        String key = recipe.cold().mode() == ColdProcess.Mode.REPLACE ? "frostworks.jei.cold_processing.replace"
                : one ? "frostworks.jei.cold_processing.bonus_one" : "frostworks.jei.cold_processing.bonus";
        Component what = Component.translatable(anywhere ? key + "_anywhere" : key, name);
        List<FormattedCharSequence> lines = new ArrayList<>(font.split(what, WIDTH - 24));
        if (one) // the recipe it is for, on a line of its own (cut to the width)
            lines.add(net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(Component.translatable(
                    "frostworks.jei.cold_processing.recipe", recipe.cold().recipe().toString()), WIDTH - 24)));
        if (recipe.replacesLast())
            lines.addAll(font.split(Component.translatable("frostworks.jei.cold_processing.replaces_last"), WIDTH - 24));
        for (int i = 0; i < Math.min(lines.size(), 3); i++)
            graphics.drawString(font, lines.get(i), 24, 1 + i * 10, TEXT, false);
        // a row per step: its temperature, then each result's chance under it
        for (int r = 0; r < Math.min(recipe.rows().size(), MAX_STEPS); r++) {
            Row row = recipe.rows().get(r);
            int y = HEADER + r * ROW;
            graphics.drawString(font, row.temperature() == ColdProcess.ANYWHERE
                            ? Component.translatable("frostworks.jei.cold_processing.anywhere")
                            : Component.translatable("frostworks.jei.cold_processing.step", row.temperature()),
                    2, y + 6, TEXT, false);
            for (int i = 0; i < Math.min(row.slots().size(), MAX_SLOTS); i++) {
                float chance = row.slots().get(i).chance();
                if (chance >= 1)
                    continue;
                String percent = Math.round(chance * 100) + "%";
                int x = SLOTS_X + i * SLOT_STEP + 8 - font.width(percent) / 2;
                graphics.drawString(font, percent, x, y + 19, TEXT, false);
            }
            if (row.slots().size() > MAX_SLOTS)
                graphics.drawString(font, "+" + (row.slots().size() - MAX_SLOTS), WIDTH - 10, y + 6, TEXT, false);
        }
        if (recipe.rows().size() > MAX_STEPS)
            graphics.drawString(font, Component.translatable("frostworks.jei.cold_processing.more",
                    recipe.rows().size() - MAX_STEPS), 2, HEIGHT - 9, TEXT, false);
    }
}
