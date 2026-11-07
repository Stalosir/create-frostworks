package com.frostworks.content.cold;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * One cold processing file, read and checked. The same fields on every machine: a process (a recipe type of
 * Create or Greate), a recipe of it ({@code recipe}, empty: every recipe of the process), a mode, ingredients (items,
 * tags, and at the basin fluids; each item may ask to be chilled) and steps: a temperature (a whole number below 0 deg C)
 * and its results.
 * <p>
 * The step a room uses: of the steps at least as warm as the room ({@code temperature >= room}), the coldest one. A
 * room at -15 with steps at -10 and -20 takes -10; a room at -8 takes none (the normal recipe). A step without a
 * temperature (one at most) applies anywhere: out of any closed room, or in one warmer than every cold step; the cold
 * steps take its place once reached.
 * <ul>
 * <li>{@code replace}: the step's results take the place of the normal recipe's (the normal recipe must exist); at the
 * basin, its items and its fluids.</li>
 * <li>{@code bonus}: the step's results are added to any recipe of the process the input goes through.</li>
 * </ul>
 * A result is Create's own ({@code id} or {@code item}, {@code count}, {@code chance}: without a chance it always
 * drops, with one it is rolled on its own), or {@code {"copy": "main", "count": n, "chance": c}}: n of the recipe's
 * main (first) result. At the basin, a fluid: {@code amount} in mB in place of {@code count} ({@code {"id": fluid,
 * "amount": 250}}, {@code "fluid"} in 1.20.1; {@code {"copy": "main", "amount": 250}}: the recipe's first fluid).
 * {@code "replaces": "last"} (bonus only) leaves out the recipe's last item result, so that a bonus can take the place
 * of a roll instead of adding one.
 */
public record ColdProcess(ResourceLocation id, ResourceLocation process, @Nullable ResourceLocation recipe, Mode mode,
                          List<Entry> ingredients, List<Step> steps) {

    public enum Mode { REPLACE, BONUS }

    /** The basin's processes: several items and fluids in, items and fluids out. */
    public static final Set<String> BASIN = Set.of("create:mixing", "create:compacting", "create:basin",
            "greate:mixing", "greate:compacting", "greate:brewing", "greate:basin");

    /** The processes the machines know (the crushing wheels, the millstone, the basin; Greate's machines). */
    public static final Set<String> SUPPORTED;

    static {
        Set<String> supported = new HashSet<>(Set.of("create:crushing", "create:milling", "greate:milling",
                "greate:crushing", "greate:pressing", "greate:splashing", "greate:haunting", "greate:cutting"));
        supported.addAll(BASIN);
        SUPPORTED = Set.copyOf(supported);
    }

    /**
     * One ingredient of the file: an item or a tag (chilled or not), or at the basin a fluid or a fluid tag. The file
     * takes an input when one of its entries takes one of the items or fluids the craft uses.
     */
    public record Entry(@Nullable Ingredient item, boolean chilled, @Nullable Fluid fluid, @Nullable TagKey<Fluid> fluidTag) {
        public boolean takes(ItemStack stack) {
            return item != null && item.test(stack) && (!chilled || ChilledMark.is(stack));
        }

        public boolean takes(Fluid other) {
            if (item != null || other == Fluids.EMPTY)
                return false;
            return fluid != null ? other.isSame(fluid) : other.defaultFluidState().is(fluidTag);
        }
    }

    /** A copy of the recipe's main result: {@code count} of its item, at {@code chance} (1: always). */
    public record Copy(int count, float chance) {}

    /** A fluid result (basin): {@code amount} mB, at {@code chance}. */
    public record FluidOut(Fluid fluid, int amount, float chance) {}

    /** A copy of the recipe's first fluid result (basin): {@code amount} mB of that fluid, at {@code chance}. */
    public record FluidCopy(int amount, float chance) {}

    /** The temperature of the step that applies anywhere (it sorts after every cold step). */
    public static final int ANYWHERE = Integer.MAX_VALUE;

    public record Step(int temperature, List<ProcessingOutput> outputs, List<Copy> copies, List<FluidOut> fluids,
                       List<FluidCopy> fluidCopies, boolean replacesLast) {
        /** A step without a temperature: it applies anywhere. */
        public boolean anywhere() {
            return temperature == ANYWHERE;
        }

        /** This step's item results for a recipe whose results are {@code recipe} (the copies take its first one). */
        public List<ProcessingOutput> results(List<ProcessingOutput> recipe) {
            List<ProcessingOutput> results = new ArrayList<>(outputs);
            if (!recipe.isEmpty())
                for (Copy copy : copies)
                    results.add(ColdVersion.output(recipe.get(0).getStack().copyWithCount(copy.count()), copy.chance()));
            return results;
        }

        public boolean hasFluids() {
            return !fluids.isEmpty() || !fluidCopies.isEmpty();
        }
    }

    /** Every ingredient an item that must be chilled (the chilled ores' files). */
    public boolean chilledOnly() {
        return !ingredients.isEmpty() && ingredients.stream().allMatch(e -> e.item() != null && e.chilled());
    }

    public boolean atBasin() {
        return BASIN.contains(process.toString());
    }

    /** Does this file take one of these inputs (no ingredients: any input)? */
    public boolean accepts(List<ItemStack> items, List<Fluid> fluids) {
        if (ingredients.isEmpty())
            return true;
        for (Entry entry : ingredients) {
            for (ItemStack stack : items)
                if (entry.takes(stack))
                    return true;
            for (Fluid fluid : fluids)
                if (entry.takes(fluid))
                    return true;
        }
        return false;
    }

    /** Does this file go with this recipe (no recipe named: every recipe of the process)? */
    public boolean goesWith(@Nullable ResourceLocation recipeId) {
        return recipe == null || recipe.equals(recipeId);
    }

    /**
     * The step a machine uses, its closed room at this temperature (NaN: no closed room), or null (warmer than every
     * step, and none applies anywhere).
     */
    @Nullable
    public Step stepAt(float temperature) {
        for (Step step : steps) // coldest first, the step that applies anywhere last
            if (step.anywhere() || step.temperature() >= temperature)
                return step;
        return null;
    }

    /** Reads a file; any mistake is an {@link IllegalArgumentException} that says which field and why. */
    public static ColdProcess read(ResourceLocation id, JsonObject json, HolderLookup.Provider registries) {
        String process = GsonHelper.getAsString(json, "process");
        if (!SUPPORTED.contains(process))
            throw new IllegalArgumentException("process \"" + process + "\" is not supported yet (supported: "
                    + String.join(", ", SUPPORTED.stream().sorted().toList()) + ")");
        boolean basin = BASIN.contains(process);
        // The recipe of the process this file is for; empty (or left out): every recipe of the process
        ResourceLocation recipe = null;
        String recipeName = GsonHelper.getAsString(json, "recipe", "");
        if (!recipeName.isEmpty()) {
            recipe = ResourceLocation.tryParse(recipeName);
            if (recipe == null)
                throw new IllegalArgumentException("recipe \"" + recipeName + "\" is not a recipe id");
        }
        String modeName = GsonHelper.getAsString(json, "mode");
        Mode mode;
        try {
            mode = Mode.valueOf(modeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("mode \"" + modeName + "\" is not one of replace, bonus");
        }
        if (json.has("base")) {
            if (!json.get("base").isJsonObject())
                throw new IllegalArgumentException("base must be an object");
            throw new IllegalArgumentException("base (a craft that only exists in the cold) is not supported yet");
        }
        if (json.has("chilled"))
            throw new IllegalArgumentException("\"chilled\" goes on each item ingredient: {\"tag\": ..., \"chilled\": true}");
        if (json.has("fluid_results") || json.has("fluidResults"))
            throw new IllegalArgumentException("fluid results go in a step's results, with \"amount\" (mB)");

        List<Entry> ingredients = new ArrayList<>();
        JsonArray ingredientArray = json.has("ingredients") ? GsonHelper.getAsJsonArray(json, "ingredients") : new JsonArray();
        for (JsonElement element : ingredientArray)
            ingredients.add(entry(element, process, basin, registries));

        JsonArray stepArray = GsonHelper.getAsJsonArray(json, "steps");
        if (stepArray.isEmpty())
            throw new IllegalArgumentException("steps is empty");
        List<Step> steps = new ArrayList<>();
        Set<Integer> temperatures = new HashSet<>();
        for (JsonElement element : stepArray) {
            JsonObject step = GsonHelper.convertToJsonObject(element, "step");
            JsonElement t = step.get("temperature");
            if (t == null) {
                if (!temperatures.add(ANYWHERE))
                    throw new IllegalArgumentException("two steps without a temperature (only one applies anywhere)");
                steps.add(readStep(ANYWHERE, GsonHelper.getAsJsonArray(step, "results"), mode, process, basin, registries));
                continue;
            }
            if (!t.isJsonPrimitive() || !t.getAsJsonPrimitive().isNumber()
                    || t.getAsDouble() != Math.rint(t.getAsDouble()))
                throw new IllegalArgumentException("a step's temperature must be a whole number (deg C), got " + t);
            int temperature = t.getAsInt();
            if (temperature >= 0)
                throw new IllegalArgumentException("step temperature " + temperature + ": a cold step must be below 0 deg C");
            if (!temperatures.add(temperature))
                throw new IllegalArgumentException("two steps at " + temperature + " deg C");
            steps.add(readStep(temperature, GsonHelper.getAsJsonArray(step, "results"), mode, process, basin, registries));
        }
        steps.sort(Comparator.comparingInt(Step::temperature)); // coldest first, the step that applies anywhere last
        return new ColdProcess(id, ResourceLocation.tryParse(process), recipe, mode, List.copyOf(ingredients),
                List.copyOf(steps));
    }

    /** One ingredient: an item or a tag (with "chilled"), or at the basin a fluid or a fluid tag. */
    private static Entry entry(JsonElement element, String process, boolean basin, HolderLookup.Provider registries) {
        if (!element.isJsonObject())
            throw new IllegalArgumentException("ingredient " + element + " is not an object");
        JsonObject json = element.getAsJsonObject();
        String type = GsonHelper.getAsString(json, "type", "");
        boolean fluidTag = json.has("fluidTag") || type.equals("neoforge:tag");
        if (json.has("fluid") || fluidTag || type.equals("neoforge:single")) {
            if (!basin)
                throw new IllegalArgumentException("fluid ingredients are for the basin only, not " + process);
            if (json.has("chilled"))
                throw new IllegalArgumentException("ingredient " + json + ": a fluid cannot be chilled");
            if (fluidTag) {
                String name = GsonHelper.getAsString(json, json.has("fluidTag") ? "fluidTag" : "tag");
                return new Entry(null, false, null, TagKey.create(Registries.FLUID, location(name)));
            }
            ResourceLocation fluidId = location(GsonHelper.getAsString(json, "fluid"));
            Fluid fluid = BuiltInRegistries.FLUID.getOptional(fluidId).orElse(null);
            if (fluid == null || fluid == Fluids.EMPTY)
                throw new IllegalArgumentException("unknown fluid " + fluidId);
            return new Entry(null, false, fluid, null);
        }
        boolean chilled = GsonHelper.getAsBoolean(json, "chilled", false);
        JsonObject plain = json.deepCopy();
        plain.remove("chilled");
        try {
            return new Entry(ColdVersion.ingredient(plain, registries), chilled, null, null);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("ingredient " + json + ": " + e.getMessage());
        }
    }

    private static Step readStep(int temperature, JsonArray array, Mode mode, String process, boolean basin,
                                 HolderLookup.Provider registries) {
        String where = (temperature == ANYWHERE ? "the step without a temperature" : "step " + temperature) + ": ";
        if (array.isEmpty())
            throw new IllegalArgumentException(where + "results is empty");
        List<ProcessingOutput> outputs = new ArrayList<>();
        List<Copy> copies = new ArrayList<>();
        List<FluidOut> fluids = new ArrayList<>();
        List<FluidCopy> fluidCopies = new ArrayList<>();
        boolean replacesLast = false;
        for (JsonElement element : array) {
            JsonObject result = GsonHelper.convertToJsonObject(element, "result");
            boolean fluid = result.has("amount") || result.has("fluid");
            if (fluid && !basin)
                throw new IllegalArgumentException(where + "fluid results are for the basin only, not " + process);
            if (fluid && result.has("count"))
                throw new IllegalArgumentException(where + "a result has \"count\" (items) or \"amount\" (mB of a fluid), not both");
            if (result.has("replaces")) {
                if (mode != Mode.BONUS)
                    throw new IllegalArgumentException(where + "\"replaces\" only goes with the bonus mode");
                if (!"last".equals(GsonHelper.getAsString(result, "replaces")))
                    throw new IllegalArgumentException(where + "\"replaces\" can only be \"last\"");
                replacesLast = true;
            }
            float chance = GsonHelper.getAsFloat(result, "chance", 1);
            if (!(chance > 0 && chance <= 1))
                throw new IllegalArgumentException(where + "chance " + chance + " is not between 0 (excluded) and 1");
            if (result.has("copy")) {
                if (!"main".equals(GsonHelper.getAsString(result, "copy")))
                    throw new IllegalArgumentException(where + "\"copy\" can only be \"main\" (the recipe's first result)");
                if (fluid) {
                    fluidCopies.add(new FluidCopy(amount(result, where), chance));
                    continue;
                }
                int count = GsonHelper.getAsInt(result, "count", 1);
                if (count < 1)
                    throw new IllegalArgumentException(where + "count " + count + " is below 1");
                copies.add(new Copy(count, chance));
                continue;
            }
            if (fluid) {
                String name = result.has("fluid") ? GsonHelper.getAsString(result, "fluid") : GsonHelper.getAsString(result, "id");
                ResourceLocation fluidId = location(name);
                Fluid out = BuiltInRegistries.FLUID.getOptional(fluidId).orElse(null);
                if (out == null || out == Fluids.EMPTY)
                    throw new IllegalArgumentException(where + "unknown fluid " + fluidId);
                fluids.add(new FluidOut(out, amount(result, where), chance));
                continue;
            }
            JsonObject plain = result.deepCopy();
            plain.remove("replaces");
            try {
                outputs.add(ColdVersion.output(plain, registries));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(where + "result " + plain + ": " + e.getMessage());
            }
        }
        return new Step(temperature, List.copyOf(outputs), List.copyOf(copies), List.copyOf(fluids),
                List.copyOf(fluidCopies), replacesLast);
    }

    private static int amount(JsonObject result, String where) {
        int amount = GsonHelper.getAsInt(result, "amount", 0);
        if (amount < 1)
            throw new IllegalArgumentException(where + "amount " + amount + " (mB) is below 1");
        return amount;
    }

    private static ResourceLocation location(String text) {
        ResourceLocation location = ResourceLocation.tryParse(text);
        if (location == null)
            throw new IllegalArgumentException("\"" + text + "\" is not an id");
        return location;
    }
}
