package com.frostworks.content.infuser;

import java.util.function.Function;

import com.frostworks.registry.FwFluids;
import com.frostworks.registry.FwRecipeTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * {@code frostworks:food_preserving}: in the cryogenic infuser, a TerraFirmaCraft food and a fluid give
 * the same food with our preservation trait of {@code level} ({@link FoodPreserving}). Create's processing JSON, empty
 * results, and {@code level} (1 or 2) as Create's item application has {@code keep_held_item}:
 * <pre>
 * {"neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "tfc"}],
 *  "type": "frostworks:food_preserving",
 *  "ingredients": [{"type": "frostworks:perishable_food"},
 *                  {"type": "neoforge:single", "amount": 50, "fluid": "frostworks:cold_gas"}],
 *  "results": [],
 *  "level": 1}
 * </pre>
 * The fluid only says which tank the infuser draws from (gas, or a cold liquid): the level is the recipe's.
 */
public class FoodPreservingRecipe extends ProcessingRecipe<SingleRecipeInput, FoodPreservingRecipe.Params> {
    private final int level;

    public FoodPreservingRecipe(Params params) {
        super(FwRecipeTypes.FOOD_PRESERVING, params);
        this.level = params.level;
    }

    /** The level it gives (1: cold gas, 2: Frosium). */
    public int level() {
        return level;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level world) {
        return !ingredients.isEmpty() && ingredients.get(0).test(input.item())
                && gasAmount() + liquidAmount() > 0 // a fluid neither tank takes: never for free
                && FoodPreserving.accepts(input.item(), level);
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
    public boolean canUse(net.neoforged.neoforge.fluids.FluidStack gasTank, net.neoforged.neoforge.fluids.FluidStack liquidTank) {
        for (var fluid : fluidIngredients) {
            var stacks = fluid.getFluids();
            var tank = stacks.length > 0 && FwFluids.isGas(stacks[0]) ? gasTank : liquidTank;
            if (tank.isEmpty() || !fluid.ingredient().test(tank) || tank.getAmount() < fluid.amount())
                return false;
        }
        return !fluidIngredients.isEmpty();
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
        return 0;
    }

    @Override
    protected int getMaxFluidInputCount() {
        return 1;
    }

    /** Create's processing parameters and {@code level}. */
    public static class Params extends ProcessingRecipeParams {
        public static final MapCodec<Params> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                codec(Params::new).forGetter(Function.identity()),
                Codec.intRange(FoodPreserving.COLD, FoodPreserving.DEEP).fieldOf("level").forGetter(p -> p.level)
        ).apply(instance, (params, level) -> {
            params.level = level;
            return params;
        }));
        public static final StreamCodec<RegistryFriendlyByteBuf, Params> STREAM_CODEC = streamCodec(Params::new);

        protected int level = FoodPreserving.COLD;

        @Override
        protected void encode(RegistryFriendlyByteBuf buffer) {
            super.encode(buffer);
            ByteBufCodecs.VAR_INT.encode(buffer, level);
        }

        @Override
        protected void decode(RegistryFriendlyByteBuf buffer) {
            super.decode(buffer);
            level = ByteBufCodecs.VAR_INT.decode(buffer);
        }
    }

    public static class Serializer implements RecipeSerializer<FoodPreservingRecipe> {
        private final MapCodec<FoodPreservingRecipe> codec =
                ProcessingRecipe.codec(FoodPreservingRecipe::new, Params.CODEC);
        private final StreamCodec<RegistryFriendlyByteBuf, FoodPreservingRecipe> streamCodec =
                ProcessingRecipe.streamCodec(FoodPreservingRecipe::new, Params.STREAM_CODEC);

        @Override
        public MapCodec<FoodPreservingRecipe> codec() {
            return codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FoodPreservingRecipe> streamCodec() {
            return streamCodec;
        }
    }
}
