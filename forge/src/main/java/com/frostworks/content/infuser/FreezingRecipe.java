package com.frostworks.content.infuser;

import com.frostworks.registry.FwRecipeTypes;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;

/**
 * {@code frostworks:freezing}: what is in a basin (Create's basin recipe: items and fluids) frozen by
 * the cryogenic infuser above it, as the press compacts. The cold gas is not part of the recipe: the infuser takes
 * {@code infuserIceGas} from its own tank for each one. The JSON is Create's, e.g.
 * <pre>
 * {"type": "frostworks:freezing",
 *  "ingredients": [{"amount": 1000, "fluid": "minecraft:water"}],
 *  "results": [{"item": "minecraft:ice"}]}
 * </pre>
 */
public class FreezingRecipe extends BasinRecipe {

    public FreezingRecipe(ProcessingRecipeParams params) {
        super(FwRecipeTypes.FREEZING, params);
    }
}
