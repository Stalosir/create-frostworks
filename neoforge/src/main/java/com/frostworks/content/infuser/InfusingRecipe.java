package com.frostworks.content.infuser;

import com.frostworks.registry.FwFluids;
import com.frostworks.registry.FwRecipeTypes;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;

import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * {@code frostworks:infusing}: one item and an amount of cold gas give one item, in the cryogenic
 * infuser. The JSON is Create's processing recipe JSON, e.g.
 * <pre>
 * {"type": "frostworks:infusing",
 *  "ingredients": [{"item": "create:iron_sheet"}, {"type": "neoforge:single", "amount": 500, "fluid": "frostworks:cold_gas"}],
 *  "results": [{"id": "frostworks:cold_iron_sheet"}]}
 * </pre>
 */
public class InfusingRecipe extends StandardProcessingRecipe<SingleRecipeInput> {

    public InfusingRecipe(ProcessingRecipeParams params) {
        super(FwRecipeTypes.INFUSING, params);
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return !ingredients.isEmpty() && ingredients.get(0).test(input.item())
                && !(marks() && com.frostworks.content.cold.ChilledMark.is(input.item()));
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
            var stacks = fluid.getFluids();
            if (stacks.length > 0 && (gas ? FwFluids.isGas(stacks[0]) : FwFluids.isLiquid(stacks[0])))
                amount += fluid.amount();
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
