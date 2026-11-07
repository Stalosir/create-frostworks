package com.frostworks.content.infuser;

import com.frostworks.registry.FwFluids;
import com.frostworks.registry.FwRecipeTypes;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;

import net.minecraftforge.items.wrapper.RecipeWrapper;
import net.minecraft.world.level.Level;

/**
 * {@code frostworks:infusing}: one item and an amount of cold gas give one item, in the cryogenic
 * infuser. The JSON is Create's processing recipe JSON, e.g.
 * <pre>
 * {"type": "frostworks:infusing",
 *  "ingredients": [{"item": "create:iron_sheet"}, {"amount": 500, "fluid": "frostworks:cold_gas"}],
 *  "results": [{"item": "frostworks:cold_iron_sheet"}]}
 * </pre>
 */
public class InfusingRecipe extends ProcessingRecipe<RecipeWrapper> {

    public InfusingRecipe(ProcessingRecipeParams params) {
        super(FwRecipeTypes.INFUSING, params);
    }

    @Override
    public boolean matches(RecipeWrapper input, Level level) {
        return !ingredients.isEmpty() && ingredients.get(0).test(input.getItem(0))
                && !(marks() && com.frostworks.content.cold.ChilledMark.is(input.getItem(0)));
    }

    /**
     * A recipe without results marks its input chilled ({@code ChilledMark}), the same item: one recipe on a tag
     * covers every ore of a modpack. An input already chilled is left alone.
     */
    public boolean marks() {
        return getRollableResults().isEmpty();
    }

    /** Cold gas the recipe uses (mB). */
    public int gasAmount() {
        return amountOf(true);
    }

    /**
     * Frosium (any cold liquid) the recipe uses, in the infuser's second tank (mB).
     * The chilled ores' recipes are the only ones that take it, and the amount is the setting {@code
     * chilledInfusionFrosium} (the recipe's own amount only says it takes Frosium; the config may not be loaded yet
     * when JEI asks: then the recipe's).
     */
    public int frosiumAmount() {
        int inRecipe = amountOf(false);
        if (inRecipe == 0)
            return 0;
        try {
            return com.frostworks.FwConfig.get(com.frostworks.FwConfig.CHILLED_INFUSION_FROSIUM);
        } catch (RuntimeException e) {
            return inRecipe;
        }
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
    protected int getMaxInputCount() {
        return 1;
    }

    @Override
    protected int getMaxOutputCount() {
        return 1;
    }

    @Override
    protected int getMaxFluidInputCount() {
        return 2; // Cold gas and Frosium
    }
}
