package com.frostworks.content.cold;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * One cold fluid file ({@code frostworks:cold_fluid}), read and checked: the one fluid a vent takes (a fluid or a
 * fluid tag), what it uses of it ({@code amount}, mB per second), the target of a room it cools ({@code temperature},
 * a whole number below 0 deg C) and the cells it covers ({@code coverage}).
 * <pre>
 * {"type": "frostworks:cold_fluid",
 *  "ingredients": [{"type": "neoforge:single", "fluid": "frostworks:cold_gas", "amount": 15}],
 *  "temperature": -10, "coverage": 24}
 * </pre>
 * The ingredient: {@code neoforge:single} ({@code fluid}) or {@code neoforge:tag} ({@code tag}) in 1.21.1; Create's
 * {@code fluid} or {@code fluidTag} in 1.20.1 (the Forge jar's files, converted).
 */
public record ColdFluid(ResourceLocation id, @Nullable Fluid fluid, @Nullable TagKey<Fluid> tag, int amount,
                        int temperature, int coverage) {

    private static final Set<String> SINGLE = Set.of("type", "fluid", "amount");
    private static final Set<String> TAG = Set.of("type", "tag", "fluidTag", "amount");

    /** Does a vent fed with this fluid use this file? */
    public boolean takes(Fluid other) {
        if (other == Fluids.EMPTY)
            return false;
        return fluid != null ? other.isSame(fluid) : other.defaultFluidState().is(tag);
    }

    /** The fluid or the tag, as written ({@code #} before a tag). */
    public String ingredientText() {
        return fluid != null ? String.valueOf(BuiltInRegistries.FLUID.getKey(fluid)) : "#" + tag.location();
    }

    /** Reads a file; any mistake is an {@link IllegalArgumentException} that says which field and why. */
    public static ColdFluid read(ResourceLocation id, JsonObject json) {
        JsonArray ingredients = GsonHelper.getAsJsonArray(json, "ingredients");
        if (ingredients.size() != 1 || !ingredients.get(0).isJsonObject())
            throw new IllegalArgumentException("ingredients must be one fluid ingredient, got " + ingredients);
        JsonObject ingredient = ingredients.get(0).getAsJsonObject();
        String type = GsonHelper.getAsString(ingredient, "type", "");
        int amount = GsonHelper.getAsInt(ingredient, "amount", 0);
        if (amount <= 0)
            throw new IllegalArgumentException("ingredient amount " + amount + " (mB per second) must be above 0");
        Fluid fluid = null;
        TagKey<Fluid> tag = null;
        if (ingredient.has("fluid") && (type.isEmpty() || type.equals("neoforge:single"))) {
            only(ingredient, SINGLE);
            ResourceLocation fluidId = location(GsonHelper.getAsString(ingredient, "fluid"));
            fluid = BuiltInRegistries.FLUID.getOptional(fluidId).orElse(null);
            if (fluid == null || fluid == Fluids.EMPTY)
                throw new IllegalArgumentException("unknown fluid " + fluidId);
        } else if (ingredient.has("tag") && type.equals("neoforge:tag")
                || ingredient.has("fluidTag") && type.isEmpty()) {
            only(ingredient, TAG);
            String name = GsonHelper.getAsString(ingredient, ingredient.has("tag") ? "tag" : "fluidTag");
            tag = TagKey.create(Registries.FLUID, location(name));
        } else {
            throw new IllegalArgumentException("ingredient " + ingredient + " is not one fluid (neoforge:single, "
                    + "neoforge:tag)");
        }
        JsonElement t = json.get("temperature");
        if (t == null || !t.isJsonPrimitive() || !t.getAsJsonPrimitive().isNumber()
                || t.getAsDouble() != Math.rint(t.getAsDouble()))
            throw new IllegalArgumentException("temperature must be a whole number (deg C), got " + t);
        int temperature = t.getAsInt();
        if (temperature >= 0)
            throw new IllegalArgumentException("temperature " + temperature + ": a cold fluid must be below 0 deg C");
        int coverage = GsonHelper.getAsInt(json, "coverage", 0);
        if (coverage <= 0)
            throw new IllegalArgumentException("coverage " + coverage + " (cells) must be above 0");
        return new ColdFluid(id, fluid, tag, amount, temperature, coverage);
    }

    private static void only(JsonObject ingredient, Set<String> keys) {
        for (String key : ingredient.keySet())
            if (!keys.contains(key))
                throw new IllegalArgumentException("ingredient key \"" + key + "\" is not read (one fluid, its amount)");
    }

    private static ResourceLocation location(String text) {
        ResourceLocation location = ResourceLocation.tryParse(text);
        if (location == null)
            throw new IllegalArgumentException("\"" + text + "\" is not an id");
        return location;
    }
}
