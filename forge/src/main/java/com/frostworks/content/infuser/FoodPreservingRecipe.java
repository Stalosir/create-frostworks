package com.frostworks.content.infuser;

import com.frostworks.registry.FwFluids;
import com.frostworks.registry.FwRecipeTypes;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.wrapper.RecipeWrapper;

/**
 * {@code frostworks:food_preserving}: in the cryogenic infuser, a TerraFirmaCraft food and a fluid give
 * the same food with our preservation trait of {@code level} ({@link FoodPreserving}). Create's processing JSON, empty
 * results, and {@code level} (1 or 2), read as Create reads its recipes' own fields ({@code readAdditional}):
 * <pre>
 * {"conditions": [{"type": "forge:mod_loaded", "modid": "tfc"}],
 *  "type": "frostworks:food_preserving",
 *  "ingredients": [{"type": "frostworks:perishable_food"}, {"amount": 50, "fluid": "frostworks:cold_gas"}],
 *  "results": [],
 *  "level": 1}
 * </pre>
 * The fluid only says which tank the infuser draws from (gas, or a cold liquid): the level is the recipe's.
 */
public class FoodPreservingRecipe extends ProcessingRecipe<RecipeWrapper> {
    private int level = FoodPreserving.COLD;

    public FoodPreservingRecipe(ProcessingRecipeParams params) {
        super(FwRecipeTypes.FOOD_PRESERVING, params);
    }

    /** The level it gives (1: cold gas, 2: Frosium). */
    public int level() {
        return level;
    }

    @Override
    public boolean matches(RecipeWrapper input, Level world) {
        return !ingredients.isEmpty() && ingredients.get(0).test(input.getItem(0))
                && gasAmount() + liquidAmount() > 0 // a fluid neither tank takes: never for free
                && FoodPreserving.accepts(input.getItem(0), level);
    }

    /** Cold gas it uses (mB). */
    public int gasAmount() {
        return amountOf(true);
    }

    /** Cold liquid it uses, from the infuser's second tank (mB). */
    public int liquidAmount() {
        return amountOf(false);
    }

    /**
     * Does the infuser hold this recipe's fluid, enough of it? The gas tank for a gas, the liquid tank for a cold
     * liquid: two recipes match the same food (gas, Frosium), the infuser takes the one its fluid allows.
     */
    public boolean canUse(net.minecraftforge.fluids.FluidStack gasTank, net.minecraftforge.fluids.FluidStack liquidTank) {
        for (var fluid : fluidIngredients) {
            var stacks = fluid.getMatchingFluidStacks();
            var tank = !stacks.isEmpty() && FwFluids.isGas(stacks.get(0)) ? gasTank : liquidTank;
            if (tank.isEmpty() || !fluid.test(tank) || tank.getAmount() < fluid.getRequiredAmount())
                return false;
        }
        return !fluidIngredients.isEmpty();
    }

    private int amountOf(boolean gas) {
        int amount = 0;
        for (var fluid : fluidIngredients) {
            var stacks = fluid.getMatchingFluidStacks();
            if (!stacks.isEmpty() && (gas ? FwFluids.isGas(stacks.get(0)) : FwFluids.isLiquid(stacks.get(0))))
                amount += fluid.getRequiredAmount();
        }
        return amount;
    }

    @Override
    public void readAdditional(JsonObject json) {
        level = GsonHelper.getAsInt(json, "level");
        if (level < FoodPreserving.COLD || level > FoodPreserving.DEEP)
            throw new JsonSyntaxException("food_preserving: level must be 1 or 2, not " + level);
    }

    @Override
    public void readAdditional(FriendlyByteBuf buffer) {
        level = buffer.readVarInt();
    }

    @Override
    public void writeAdditional(JsonObject json) {
        json.addProperty("level", level);
    }

    @Override
    public void writeAdditional(FriendlyByteBuf buffer) {
        buffer.writeVarInt(level);
    }

    @Override
    protected int getMaxInputCount() {
        return 1;
    }

    @Override
    protected int getMaxOutputCount() {
        return 0;
    }

    @Override
    protected int getMaxFluidInputCount() {
        return 1;
    }
}
