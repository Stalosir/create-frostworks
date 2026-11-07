package com.frostworks.registry;

import com.frostworks.Frostworks;

import static com.frostworks.Frostworks.REGISTRATE;

import java.util.function.Consumer;

import com.simibubi.create.content.fluids.VirtualFluid;
import com.tterrag.registrate.util.entry.FluidEntry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

public final class FwFluids {
    private FwFluids() {}

    /** Ice blue tint applied to the vanilla water textures. */
    public static final int GAS_TINT = 0xC8BFEFFF;

    private static final ResourceLocation STILL = com.frostworks.platform.Mc.id("minecraft", "block/water_still");
    private static final ResourceLocation FLOW = com.frostworks.platform.Mc.id("minecraft", "block/water_flow");

    /**
     * Cold gas: a virtual fluid (no bucket, no block in the world), it only lives in pipes and internal buffers.
     */
    public static final FluidEntry<VirtualFluid> COLD_GAS = virtual("cold_gas", GAS_TINT, 120, -500, 200);

    /**
     * Frosium, the one cold liquid (liquid air is gone): made by the distillation tower, stored
     * in frost tanks. Its own textures ({@code textures/fluid/frosium_still|flow.png}): for now vanilla lava's,
     * copied to be recoloured, drawn as they are (no tint).
     */
    public static final int FROSIUM_TINT = 0xFFFFFFFF;
    // a real liquid (a block in the world, a bucket that pours it), swum in like water; it freezes what is in it
    public static final FluidEntry<net.neoforged.neoforge.fluids.BaseFlowingFluid.Flowing> FROSIUM = REGISTRATE
            .fluid("frosium", Frostworks.id("fluid/frosium_still"), Frostworks.id("fluid/frosium_flow"),
                    (p, still, flow) -> fluidType(p.canSwim(true).canDrown(true).canExtinguish(true).supportsBoating(true)
                                    .sound(net.neoforged.neoforge.common.SoundActions.BUCKET_FILL, net.minecraft.sounds.SoundEvents.BUCKET_FILL)
                                    .sound(net.neoforged.neoforge.common.SoundActions.BUCKET_EMPTY, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY),
                            still, flow, "frosium", FROSIUM_TINT, 77, 808, 300))
            .source(net.neoforged.neoforge.fluids.BaseFlowingFluid.Source::new)
            .block(com.frostworks.content.reservoir.FrosiumBlock::new).build()
            .bucket().properties(p -> p.stacksTo(1).craftRemainder(net.minecraft.world.item.Items.BUCKET)).build()
            .register();

    private static FluidEntry<VirtualFluid> virtual(String name, int tint, int temperature, int density, int viscosity) {
        return virtual(name, STILL, FLOW, tint, temperature, density, viscosity);
    }

    private static FluidEntry<VirtualFluid> virtual(String name, ResourceLocation stillTexture, ResourceLocation flowTexture,
                                                    int tint, int temperature, int density, int viscosity) {
        return REGISTRATE.virtualFluid(name, stillTexture, flowTexture,
                        (p, still, flow) -> fluidType(p, still, flow, name, tint, temperature, density, viscosity),
                        VirtualFluid::createSource, VirtualFluid::createFlowing)
                .register();
    }

    private static FluidType fluidType(FluidType.Properties properties, ResourceLocation still, ResourceLocation flow,
                                       String name, int tint, int temperature, int density, int viscosity) {
        properties.temperature(temperature).density(density).viscosity(viscosity)
                .descriptionId("fluid_type.frostworks." + name);
        return new FluidType(properties) {
            @Override
            @SuppressWarnings("removal")
            public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                consumer.accept(new IClientFluidTypeExtensions() {
                    @Override
                    public ResourceLocation getStillTexture() {
                        return still;
                    }

                    @Override
                    public ResourceLocation getFlowingTexture() {
                        return flow;
                    }

                    @Override
                    public int getTintColor() {
                        return tint;
                    }
                });
            }
        };
    }

    /** The Frosium bucket (it pours the liquid out in the world, as water). */
    public static net.minecraft.world.item.Item frosiumBucket() {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Frostworks.id("frosium_bucket"));
    }

    public static Fluid gas() {
        return COLD_GAS.get().getSource();
    }

    public static boolean isGas(FluidStack stack) {
        return !stack.isEmpty() && stack.getFluid().isSame(gas());
    }

    public static Fluid frosium() {
        return FROSIUM.get().getSource();
    }

    /**
     * A cold liquid: our Frosium, and any fluid of {@code frostworks:cold_liquid} (an
     * add-on's cold liquid). The frost tank stores them,
     * the vent diffuses them (at the Frosium's coverage) and the expander expands them (at the Frosium's energy).
     */
    public static boolean isLiquid(FluidStack stack) {
        return isFrosium(stack) || FwTags.frostTankTakes(stack);
    }

    public static boolean isFrosium(FluidStack stack) {
        return !stack.isEmpty() && stack.getFluid().isSame(frosium());
    }

    /** Energy held by a stack of one of our fluids (0 for anything else, water included), as {@code Energy} counts it. */
    public static double energy(FluidStack stack) {
        if (isGas(stack))
            return com.frostworks.energy.Energy.ofGas(stack.getAmount());
        if (isLiquid(stack))
            return com.frostworks.energy.Energy.ofLiquid(stack.getAmount());
        return 0;
    }

    public static FluidStack gas(int amount) {
        return new FluidStack(gas(), amount);
    }

    public static void init() {}
}
