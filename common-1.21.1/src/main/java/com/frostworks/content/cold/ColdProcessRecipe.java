package com.frostworks.content.cold;

import com.frostworks.registry.FwRecipeTypes;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * A cold processing file ({@code frostworks:cold_process}), kept as it was written. It is never crafted: it sits in
 * the recipe manager so that {@code /reload}, data packs and the client's copy (for JEI) come for free; {@link
 * ColdProcessing} reads it (with Create's own parsers) into its index, built apart.
 */
public final class ColdProcessRecipe implements Recipe<RecipeInput> {
    public final JsonObject json;

    public ColdProcessRecipe(JsonObject json) {
        this.json = json;
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
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

    /** The file as it is (checked when the index is built, with the registries); sent to the client as its text. */
    public static final class Serializer implements RecipeSerializer<ColdProcessRecipe> {
        private static final MapCodec<ColdProcessRecipe> CODEC = MapCodec.assumeMapUnsafe(Codec.PASSTHROUGH.flatXmap(
                dynamic -> {
                    JsonElement json = dynamic.convert(JsonOps.INSTANCE).getValue();
                    return json.isJsonObject() ? DataResult.success(new ColdProcessRecipe(json.getAsJsonObject()))
                            : DataResult.error(() -> "cold process: not a JSON object");
                },
                recipe -> DataResult.error(() -> "cold process recipes are not written back")));
        private static final StreamCodec<RegistryFriendlyByteBuf, ColdProcessRecipe> STREAM_CODEC =
                ByteBufCodecs.STRING_UTF8.map(text -> new ColdProcessRecipe(JsonParser.parseString(text).getAsJsonObject()),
                        recipe -> recipe.json.toString()).cast();

        @Override
        public MapCodec<ColdProcessRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ColdProcessRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
