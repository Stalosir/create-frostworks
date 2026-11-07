package com.frostworks.registry;

import java.util.function.Predicate;

import com.frostworks.Frostworks;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FwTags {
    private FwTags() {}

    /** Blocks that close a cold room (data pack editable). */
    public static final TagKey<Block> COLD_WALLS = TagKey.create(Registries.BLOCK, Frostworks.id("cold_walls"));

    /**
     * Cold liquids the frost tank takes ({@code frostworks:cold_liquid}, our Frosium in it). An
     * add-on puts its own cold liquid in this tag to store it in our tank.
     */
    public static final TagKey<Fluid> COLD_LIQUID = TagKey.create(Registries.FLUID, Frostworks.id("cold_liquid"));
    /** What the frost tank takes: a fluid of the tag. Pure, for the tests (they can ask with a fake fluid). */
    public static boolean frostTankTakes(Predicate<TagKey<Fluid>> isIn) {
        return isIn.test(COLD_LIQUID);
    }

    public static boolean frostTankTakes(FluidStack stack) {
        return !stack.isEmpty() && frostTankTakes(tag -> stack.getFluid().defaultFluidState().is(tag));
    }
}
