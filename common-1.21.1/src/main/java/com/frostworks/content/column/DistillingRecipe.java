package com.frostworks.content.column;

import java.util.List;

import com.frostworks.registry.FwRecipeTypes;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * A distilling recipe as Minecraft 1.21.1 holds it (common-1.20.1 has the same class), around the
 * shared {@link Distillation}. The tower picks its recipe itself (from what it holds): this is only how the file is
 * read, kept and sent to the clients (JEI).
 */
public final class DistillingRecipe implements Recipe<RecipeInput> {
    public final Distillation distillation;

    public DistillingRecipe(Distillation distillation) {
        this.distillation = distillation;
    }

    /** The distilling recipes of this level (server or client), each under its id. */
    public static List<Distillation> all(Level level) {
        return level.getRecipeManager().getAllRecipesFor(FwRecipeTypes.DISTILLING.<RecipeInput, DistillingRecipe>getType())
                .stream().map(holder -> holder.value().distillation.withId(holder.id())).toList();
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
        return FwRecipeTypes.DISTILLING.getSerializer();
    }

    @Override
    public RecipeType<?> getType() {
        return FwRecipeTypes.DISTILLING.getType();
    }

    /** Read by {@link Distillation#fromJson} (the id comes later, from the holder); sent as its fields. */
    public static final class Serializer implements RecipeSerializer<DistillingRecipe> {
        private static final MapCodec<DistillingRecipe> CODEC = MapCodec.assumeMapUnsafe(Codec.PASSTHROUGH.flatXmap(
                dynamic -> {
                    try {
                        JsonElement json = dynamic.convert(JsonOps.INSTANCE).getValue();
                        return DataResult.success(
                                new DistillingRecipe(Distillation.fromJson(null, json.getAsJsonObject())));
                    } catch (RuntimeException e) {
                        return DataResult.error(() -> "distilling recipe: " + e.getMessage());
                    }
                },
                recipe -> DataResult.error(() -> "distilling recipes are not written back")));
        private static final StreamCodec<RegistryFriendlyByteBuf, DistillingRecipe> STREAM_CODEC = StreamCodec.of(
                (buf, recipe) -> recipe.distillation.toNetwork(buf),
                buf -> new DistillingRecipe(Distillation.fromNetwork(null, buf)));

        @Override
        public MapCodec<DistillingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, DistillingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
