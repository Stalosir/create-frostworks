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
 * A cold fluid ({@code frostworks:cold_fluid}): what a vent does with one fluid (temperature, consumption,
 * coverage), kept as it was written. It is never crafted: it sits in the recipe manager so that {@code /reload}, data
 * packs and the client's copy (for JEI) come for free; {@link ColdFluids} reads it ({@link ColdFluid#read}).
 */
public final class ColdFluidRecipe implements Recipe<RecipeInput> {
    public final JsonObject json;

    public ColdFluidRecipe(JsonObject json) {
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
        return FwRecipeTypes.COLD_FLUID.getSerializer();
    }

    @Override
    public RecipeType<?> getType() {
        return FwRecipeTypes.COLD_FLUID.getType();
    }

    /** The file as it is (checked when the index is built); sent to the client as its text. */
    public static final class Serializer implements RecipeSerializer<ColdFluidRecipe> {
        private static final MapCodec<ColdFluidRecipe> CODEC = MapCodec.assumeMapUnsafe(Codec.PASSTHROUGH.flatXmap(
                dynamic -> {
                    JsonElement json = dynamic.convert(JsonOps.INSTANCE).getValue();
                    return json.isJsonObject() ? DataResult.success(new ColdFluidRecipe(json.getAsJsonObject()))
                            : DataResult.error(() -> "cold fluid: not a JSON object");
                },
                recipe -> DataResult.error(() -> "cold fluid recipes are not written back")));
        private static final StreamCodec<RegistryFriendlyByteBuf, ColdFluidRecipe> STREAM_CODEC =
                ByteBufCodecs.STRING_UTF8.map(text -> new ColdFluidRecipe(JsonParser.parseString(text).getAsJsonObject()),
                        recipe -> recipe.json.toString()).cast();

        @Override
        public MapCodec<ColdFluidRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ColdFluidRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
