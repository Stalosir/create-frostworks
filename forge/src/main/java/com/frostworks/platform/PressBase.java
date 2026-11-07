package com.frostworks.platform;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's {@code MechanicalPressBlockEntity} as the shared machines see it on Forge 1.20.1 (Create 6.0.4); neoforge/ has the
 * same class. Their NBT goes through {@link #writeFw} and {@link #readFw} (the registries: null on Minecraft 1.20.1),
 * their handlers through {@link #getFluidHandler} and {@link #getItemHandler} (given as capabilities here).
 */
public abstract class PressBase extends MechanicalPressBlockEntity {
    protected PressBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** What this machine adds to its tag, after Create's. */
    protected void writeFw(CompoundTag tag, @Nullable HolderLookup.Provider registries, boolean clientPacket) {}

    /** What this machine reads from its tag, after Create. */
    protected void readFw(CompoundTag tag, @Nullable HolderLookup.Provider registries, boolean clientPacket) {}

    @Override
    public void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        writeFw(tag, null, clientPacket);
    }

    @Override
    public void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        readFw(tag, null, clientPacket);
    }

    /** The handlers changed (the machine formed, broke, or its tanks moved): the pipes ask for them again. */
    protected void refreshHandlers() {
        fluidCaps.invalidate();
        itemCaps.invalidate();
    }

    /** The fluid handler pipes see on this side, or null. */
    public @Nullable net.minecraftforge.fluids.capability.IFluidHandler getFluidHandler(@Nullable Direction side) {
        return null;
    }

    /** The item handler funnels and arms see on this side, or null. */
    public @Nullable net.minecraftforge.items.IItemHandler getItemHandler(@Nullable Direction side) {
        return null;
    }

    private final com.frostworks.FwCaps<?> fluidCaps = com.frostworks.FwCaps.fluids(this::getFluidHandler);
    private final com.frostworks.FwCaps<?> itemCaps = com.frostworks.FwCaps.items(this::getItemHandler);

    @Override
    public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
            net.minecraftforge.common.capabilities.Capability<T> cap, @Nullable Direction side) {
        var found = fluidCaps.get(cap, side);
        if (found == null)
            found = itemCaps.get(cap, side);
        return found != null ? found : super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCaps.invalidate();
        itemCaps.invalidate();
    }

    /** Which recipe types this press takes from a basin (Create's static filter), or null for Create's own. */
    protected @Nullable Boolean fwRecipeType(net.minecraft.world.item.crafting.RecipeType<?> type) {
        return null;
    }

    @Override
    protected <C extends net.minecraft.world.Container> boolean matchStaticFilters(
            net.minecraft.world.item.crafting.Recipe<C> recipe) {
        Boolean ours = fwRecipeType(recipe.getType());
        return ours != null ? ours : super.matchStaticFilters(recipe);
    }
}
