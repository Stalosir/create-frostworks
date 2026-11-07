package com.frostworks.content.cold;

import com.frostworks.registry.FwRecipeTypes;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * A cold fluid ({@code frostworks:cold_fluid}): what a vent does with one fluid (temperature, consumption,
 * coverage), kept as it was written. It is never crafted: it sits in the recipe manager so that {@code /reload}, data
 * packs and the client's copy (for JEI) come for free; {@link ColdFluids} reads it ({@link ColdFluid#read}).
 */
public final class ColdFluidRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    public final JsonObject json;

    public ColdFluidRecipe(ResourceLocation id, JsonObject json) {
        this.id = id;
        this.json = json;
    }

    @Override
    public boolean matches(Container container, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public boolean isSpecial() {
        return true; // not in the recipe book
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return FwRecipeTypes.COLD_FLUID.getSerializer();
    }

    @Override
    public RecipeType<?> getType() {
        return FwRecipeTypes.COLD_FLUID.getType();
    }

    /** The file as it is (checked when the index is built); sent to the client as its text. */
    public static final class Serializer implements RecipeSerializer<ColdFluidRecipe> {
        @Override
        public ColdFluidRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new ColdFluidRecipe(id, json);
        }

        @Override
        public ColdFluidRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new ColdFluidRecipe(id, JsonParser.parseString(buf.readUtf()).getAsJsonObject());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, ColdFluidRecipe recipe) {
            buf.writeUtf(recipe.json.toString());
        }
    }
}
