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
 * A cold processing file ({@code frostworks:cold_process}), kept as it was written. It is never crafted: it sits in
 * the recipe manager so that {@code /reload}, data packs and the client's copy (for JEI) come for free; {@link
 * ColdProcessing} reads it (with Create's own parsers) into its index, built apart.
 */
public final class ColdProcessRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    public final JsonObject json;

    public ColdProcessRecipe(ResourceLocation id, JsonObject json) {
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
        return FwRecipeTypes.COLD_PROCESS.getSerializer();
    }

    @Override
    public RecipeType<?> getType() {
        return FwRecipeTypes.COLD_PROCESS.getType();
    }

    /** The file as it is (checked when the index is built); sent to the client as its text. */
    public static final class Serializer implements RecipeSerializer<ColdProcessRecipe> {
        @Override
        public ColdProcessRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new ColdProcessRecipe(id, json);
        }

        @Override
        public ColdProcessRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new ColdProcessRecipe(id, JsonParser.parseString(buf.readUtf()).getAsJsonObject());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, ColdProcessRecipe recipe) {
            buf.writeUtf(recipe.json.toString());
        }
    }
}
