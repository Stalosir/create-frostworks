package com.frostworks.platform;

import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;

import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.Materials;
import dev.engine_room.flywheel.lib.model.baked.BakedModelBuilder;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * What NeoForge (and its builds of Create and Flywheel) does differently from Forge, for the shared
 * code. Same class, same methods in forge/.
 */
public final class Loader {
    private Loader() {}

    /** Front pumps (compressor, vent feeds) work their pressure out again every second while their pipe has none. Create 6.0.10 reaches a network that forms after the pump: nothing to do. */
    public static final boolean NUDGE_PUMP_PRESSURE = false;

    /** A partial model baked for Flywheel, cutout (shaded or not), as Create's own cogs. */
    public static Model cutout(PartialModel partial) {
        return new BakedModelBuilder(partial.get())
                .materialFunc((renderType, shaded) -> shaded ? Materials.CUTOUT_BLOCK : Materials.CUTOUT_UNSHADED_BLOCK)
                .build();
    }

    /** A tank into a tag (the registries: null on Minecraft 1.20.1). */
    public static net.minecraft.nbt.CompoundTag writeTank(net.neoforged.neoforge.fluids.capability.templates.FluidTank tank,
            @org.jetbrains.annotations.Nullable net.minecraft.core.HolderLookup.Provider registries,
            net.minecraft.nbt.CompoundTag tag) {
        return tank.writeToNBT(registries, tag);
    }

    public static void readTank(net.neoforged.neoforge.fluids.capability.templates.FluidTank tank,
            @org.jetbrains.annotations.Nullable net.minecraft.core.HolderLookup.Provider registries,
            net.minecraft.nbt.CompoundTag tag) {
        tank.readFromNBT(registries, tag);
    }

    /** Create's goggles line for a fluid handler (the tank's content). */
    public static boolean fluidTooltip(com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation self,
            java.util.List<net.minecraft.network.chat.Component> tooltip, boolean sneaking, net.neoforged.neoforge.fluids.capability.IFluidHandler handler) {
        return self.containedFluidTooltip(tooltip, sneaking, handler);
    }

    /** Every recipe of this type that matches one item (the infuser picks by the fluid it holds). */
    public static <R extends net.minecraft.world.item.crafting.Recipe<net.minecraft.world.item.crafting.SingleRecipeInput>>
    java.util.List<R> recipesFor(net.minecraft.world.level.Level level,
                                 net.minecraft.world.item.crafting.RecipeType<R> type,
                                 net.minecraft.world.item.ItemStack stack) {
        return level.getRecipeManager().getRecipesFor(type,
                new net.minecraft.world.item.crafting.SingleRecipeInput(stack), level)
                .stream().map(net.minecraft.world.item.crafting.RecipeHolder::value).toList();
    }

    /** The first recipe of this type for one item (as Create's press looks one up). */
    public static <R extends net.minecraft.world.item.crafting.Recipe<net.minecraft.world.item.crafting.SingleRecipeInput>>
    java.util.Optional<R> recipeFor(net.minecraft.world.level.Level level,
                                   net.minecraft.world.item.crafting.RecipeType<R> type,
                                   net.minecraft.world.item.ItemStack stack) {
        return level.getRecipeManager().getRecipeFor(type,
                new net.minecraft.world.item.crafting.SingleRecipeInput(stack), level)
                .map(net.minecraft.world.item.crafting.RecipeHolder::value);
    }

    /** A processing recipe's results, rolled (Create 6.0.10 takes the random). */
    public static java.util.List<net.minecraft.world.item.ItemStack> rollResults(
            com.simibubi.create.content.processing.recipe.ProcessingRecipe<?, ?> recipe,
            net.minecraft.util.RandomSource random) {
        return recipe.rollResults(random);
    }

    /** An item handler into a tag (the registries: null on Minecraft 1.20.1). */
    public static net.minecraft.nbt.CompoundTag writeItems(net.neoforged.neoforge.items.ItemStackHandler handler,
            @org.jetbrains.annotations.Nullable net.minecraft.core.HolderLookup.Provider registries) {
        return handler.serializeNBT(registries);
    }

    public static void readItems(net.neoforged.neoforge.items.ItemStackHandler handler,
            @org.jetbrains.annotations.Nullable net.minecraft.core.HolderLookup.Provider registries,
            net.minecraft.nbt.CompoundTag tag) {
        handler.deserializeNBT(registries, tag);
    }

    /** Is this mod loaded? */
    public static boolean isModLoaded(String id) {
        return net.neoforged.fml.ModList.get().isLoaded(id);
    }

    /** A box of fluid, as Create draws the contents of its tanks (catnip's fluid renderer). */
    public static void renderFluidBox(Fluid fluid, int amount, float x0, float y0, float z0, float x1, float y1, float z1,
                                      net.minecraft.client.renderer.MultiBufferSource buffer,
                                      com.mojang.blaze3d.vertex.PoseStack ms, int light, boolean renderBottom,
                                      boolean invertGasses) {
        net.createmod.catnip.platform.NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(new FluidStack(fluid, amount),
                x0, y0, z0, x1, y1, z1, buffer, ms, light, renderBottom, invertGasses);
    }

    public static void setCreativeFluid(CreativeFluidTankBlockEntity.CreativeSmartFluidTank tank, Fluid fluid,
                                        int amount) {
        tank.setContainedFluid(new FluidStack(fluid, amount));
    }
}
