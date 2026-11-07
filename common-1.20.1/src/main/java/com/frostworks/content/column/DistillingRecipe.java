package com.frostworks.content.column;

import java.util.List;

import com.frostworks.registry.FwRecipeTypes;
import com.google.gson.JsonObject;

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
 * A distilling recipe as Minecraft 1.20.1 holds it (common-1.21.1 has the same class), around the
 * shared {@link Distillation}. The tower picks its recipe itself (from what it holds): this is only how the file is
 * read, kept and sent to the clients (JEI).
 */
public final class DistillingRecipe implements Recipe<Container> {
    public final Distillation distillation;

    public DistillingRecipe(Distillation distillation) {
        this.distillation = distillation;
    }

    /** The distilling recipes of this level (server or client), each under its id. */
    public static List<Distillation> all(Level level) {
        return level.getRecipeManager().getAllRecipesFor(FwRecipeTypes.DISTILLING.<RecipeType<DistillingRecipe>>getType())
                .stream().map(recipe -> recipe.distillation).toList();
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
        return distillation.id;
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

    /** Read by {@link Distillation#fromJson}; sent as its fields. */
    public static final class Serializer implements RecipeSerializer<DistillingRecipe> {
        @Override
        public DistillingRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new DistillingRecipe(Distillation.fromJson(id, json));
        }

        @Override
        public DistillingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new DistillingRecipe(Distillation.fromNetwork(id, buf));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, DistillingRecipe recipe) {
            recipe.distillation.toNetwork(buf);
        }
    }
}
