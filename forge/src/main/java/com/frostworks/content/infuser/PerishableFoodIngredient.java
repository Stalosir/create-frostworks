package com.frostworks.content.infuser;

import java.util.stream.Stream;

import com.frostworks.Frostworks;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.crafting.AbstractIngredient;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.IIngredientSerializer;

/**
 * {@code {"type": "frostworks:perishable_food"}}, any TerraFirmaCraft food that can rot
 * ({@link FoodPreserving#perishable}): known by TFC's food capability, not by a tag. Its items (JEI) are some of TFC's
 * foods, as display stacks. Registered always; without TFC it takes nothing.
 */
public final class PerishableFoodIngredient extends AbstractIngredient {
    public static final ResourceLocation ID = Frostworks.id("perishable_food");
    public static final PerishableFoodIngredient INSTANCE = new PerishableFoodIngredient();

    private ItemStack[] items;

    private PerishableFoodIngredient() {
        super(Stream.empty());
    }

    public static void register() {
        CraftingHelper.register(ID, Serializer.INSTANCE);
    }

    @Override
    public boolean test(ItemStack stack) {
        return stack != null && FoodPreserving.perishable(stack);
    }

    /** TFC's foods, once known (an empty list is not kept: the definitions may arrive after the first ask). */
    @Override
    public ItemStack[] getItems() {
        if (items == null || items.length == 0)
            items = FoodPreserving.examples().toArray(ItemStack[]::new);
        return items;
    }

    @Override
    public boolean isEmpty() {
        return false; // no fixed list: never "empty" to a recipe
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    protected void invalidate() {
        super.invalidate();
        items = null;
    }

    @Override
    public IIngredientSerializer<? extends net.minecraft.world.item.crafting.Ingredient> getSerializer() {
        return Serializer.INSTANCE;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", ID.toString());
        return json;
    }

    public static final class Serializer implements IIngredientSerializer<PerishableFoodIngredient> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public PerishableFoodIngredient parse(FriendlyByteBuf buffer) {
            return PerishableFoodIngredient.INSTANCE;
        }

        @Override
        public PerishableFoodIngredient parse(JsonObject json) {
            return PerishableFoodIngredient.INSTANCE;
        }

        @Override
        public void write(FriendlyByteBuf buffer, PerishableFoodIngredient ingredient) {}
    }
}
