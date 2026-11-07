package com.frostworks.platform;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.fluids.pump.PumpBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's {@code PumpBlockEntity} as the shared machines see it on NeoForge 1.21.1 (Create 6.0.10); forge/ has the
 * same class. Their NBT goes through {@link #writeFw} and {@link #readFw} (the registries: null on Minecraft 1.20.1),
 * their handlers through {@link #getFluidHandler} and {@link #getItemHandler} (registered in {@code FwBlockEntities}).
 */
public abstract class PumpBase extends PumpBlockEntity {
    protected PumpBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** What this machine adds to its tag, after Create's. */
    protected void writeFw(CompoundTag tag, @Nullable HolderLookup.Provider registries, boolean clientPacket) {}

    /** What this machine reads from its tag, after Create. */
    protected void readFw(CompoundTag tag, @Nullable HolderLookup.Provider registries, boolean clientPacket) {}

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        writeFw(tag, registries, clientPacket);
    }

    @Override
    public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        readFw(tag, registries, clientPacket);
    }

    /** The handlers changed (the machine formed, broke, or its tanks moved): the pipes ask for them again. */
    protected void refreshHandlers() {
        if (level != null)
            level.invalidateCapabilities(worldPosition);
    }

    /** The fluid handler pipes see on this side, or null. */
    public @Nullable net.neoforged.neoforge.fluids.capability.IFluidHandler getFluidHandler(@Nullable Direction side) {
        return null;
    }

    /** The item handler funnels and arms see on this side, or null. */
    public @Nullable net.neoforged.neoforge.items.IItemHandler getItemHandler(@Nullable Direction side) {
        return null;
    }
}
