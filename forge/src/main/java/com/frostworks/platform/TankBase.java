package com.frostworks.platform;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's {@code FluidTankBlockEntity} as the shared machines see it on Forge 1.20.1 (Create 6.0.4); neoforge/ has the
 * same class. Their NBT goes through {@link #writeFw} and {@link #readFw} (the registries: null on Minecraft 1.20.1),
 * their handlers through {@link #getFluidHandler} and {@link #getItemHandler} (Create's own tank capability).
 */
public abstract class TankBase extends FluidTankBlockEntity {
    protected TankBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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
        // Create's own tank capability follows the controller
    }

    /** The fluid handler pipes see on this side, or null. */
    public @Nullable net.minecraftforge.fluids.capability.IFluidHandler getFluidHandler(@Nullable Direction side) {
        return null;
    }

    /** The item handler funnels and arms see on this side, or null. */
    public @Nullable net.minecraftforge.items.IItemHandler getItemHandler(@Nullable Direction side) {
        return null;
    }
}
