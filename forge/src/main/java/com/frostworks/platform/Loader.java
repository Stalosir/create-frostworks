package com.frostworks.platform;

import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;

import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.Materials;
import dev.engine_room.flywheel.lib.model.baked.ForgeBakedModelBuilder;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

/**
 * What Forge (and its builds of Create and Flywheel) does differently from NeoForge, for the shared
 * code. Same class, same methods in neoforge/.
 */
public final class Loader {
    private Loader() {}

    /** Front pumps (compressor, vent feeds) work their pressure out again every second while their pipe has none. Create 6.0.4 works out a pump's pressure only when its speed changes: a network that forms after it is never reached. */
    public static final boolean NUDGE_PUMP_PRESSURE = true;

    /** A partial model baked for Flywheel, cutout (shaded or not), as Create's own cogs. */
    public static Model cutout(PartialModel partial) {
        return new ForgeBakedModelBuilder(partial.get())
                .materialFunc((renderType, shaded) -> shaded ? Materials.CUTOUT_BLOCK : Materials.CUTOUT_UNSHADED_BLOCK)
                .build();
    }

    /** A tank into a tag (the registries: null on Minecraft 1.20.1). */
    public static net.minecraft.nbt.CompoundTag writeTank(net.minecraftforge.fluids.capability.templates.FluidTank tank,
            @org.jetbrains.annotations.Nullable net.minecraft.core.HolderLookup.Provider registries,
            net.minecraft.nbt.CompoundTag tag) {
        return tank.writeToNBT(tag);
    }

    public static void readTank(net.minecraftforge.fluids.capability.templates.FluidTank tank,
            @org.jetbrains.annotations.Nullable net.minecraft.core.HolderLookup.Provider registries,
            net.minecraft.nbt.CompoundTag tag) {
        tank.readFromNBT(tag);
    }

    /** Create's goggles line for a fluid handler (the tank's content). */
    public static boolean fluidTooltip(com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation self,
            java.util.List<net.minecraft.network.chat.Component> tooltip, boolean sneaking, net.minecraftforge.fluids.capability.IFluidHandler handler) {
        return self.containedFluidTooltip(tooltip, sneaking, net.minecraftforge.common.util.LazyOptional.of(() -> handler));
    }

    /** Every recipe of this type that matches one item (the infuser picks by the fluid it holds). */
    public static <R extends net.minecraft.world.item.crafting.Recipe<net.minecraftforge.items.wrapper.RecipeWrapper>>
    java.util.List<R> recipesFor(net.minecraft.world.level.Level level,
                                 net.minecraft.world.item.crafting.RecipeType<R> type,
                                 net.minecraft.world.item.ItemStack stack) {
        var input = new net.minecraftforge.items.wrapper.RecipeWrapper(new net.minecraftforge.items.ItemStackHandler(1));
        input.setItem(0, stack);
        return level.getRecipeManager().getRecipesFor(type, input, level);
    }

    /** The first recipe of this type for one item (Create 6.0.4's press recipes take a one-slot RecipeWrapper). */
    public static <R extends net.minecraft.world.item.crafting.Recipe<net.minecraftforge.items.wrapper.RecipeWrapper>>
    java.util.Optional<R> recipeFor(net.minecraft.world.level.Level level,
                                   net.minecraft.world.item.crafting.RecipeType<R> type,
                                   net.minecraft.world.item.ItemStack stack) {
        var input = new net.minecraftforge.items.wrapper.RecipeWrapper(new net.minecraftforge.items.ItemStackHandler(1));
        input.setItem(0, stack);
        return level.getRecipeManager().getRecipeFor(type, input, level);
    }

    /** A processing recipe's results, rolled (Create 6.0.4 rolls with its own random). */
    public static java.util.List<net.minecraft.world.item.ItemStack> rollResults(
            com.simibubi.create.content.processing.recipe.ProcessingRecipe<?> recipe,
            net.minecraft.util.RandomSource random) {
        return recipe.rollResults();
    }

    /** An item handler into a tag (the registries: null on Minecraft 1.20.1). */
    public static net.minecraft.nbt.CompoundTag writeItems(net.minecraftforge.items.ItemStackHandler handler,
            @org.jetbrains.annotations.Nullable net.minecraft.core.HolderLookup.Provider registries) {
        return handler.serializeNBT();
    }

    public static void readItems(net.minecraftforge.items.ItemStackHandler handler,
            @org.jetbrains.annotations.Nullable net.minecraft.core.HolderLookup.Provider registries,
            net.minecraft.nbt.CompoundTag tag) {
        handler.deserializeNBT(tag);
    }

    /** Is this mod loaded? */
    public static boolean isModLoaded(String id) {
        return net.minecraftforge.fml.ModList.get().isLoaded(id);
    }

    /** A box of fluid, as Create draws the contents of its tanks (catnip's fluid renderer). */
    public static void renderFluidBox(Fluid fluid, int amount, float x0, float y0, float z0, float x1, float y1, float z1,
                                      net.minecraft.client.renderer.MultiBufferSource buffer,
                                      com.mojang.blaze3d.vertex.PoseStack ms, int light, boolean renderBottom,
                                      boolean invertGasses) {
        net.createmod.catnip.render.BasicFluidRenderer.renderFluidBox(fluid, amount, x0, y0, z0, x1, y1, z1, buffer, ms,
                light, renderBottom, invertGasses);
    }

    public static void setCreativeFluid(CreativeFluidTankBlockEntity.CreativeSmartFluidTank tank, Fluid fluid,
                                        int amount) {
        tank.setContainedFluid(new FluidStack(fluid, amount));
    }
}
