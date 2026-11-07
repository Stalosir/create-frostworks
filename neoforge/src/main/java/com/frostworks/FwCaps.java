package com.frostworks;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * The capability questions the shared code asks, answered by NeoForge's capability registry. Forge's
 * {@code FwCaps} answers the same questions through {@code getCapability}.
 */
public final class FwCaps {
    private FwCaps() {}

    /** The fluid handler of the block at {@code pos} on {@code side}, or null. */
    public static @Nullable IFluidHandler fluidAt(Level level, BlockPos pos, @Nullable Direction side) {
        return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
    }

    /** The item handler of the block at {@code pos} on {@code side}, or null. */
    public static @Nullable net.neoforged.neoforge.items.IItemHandler itemsAt(Level level, BlockPos pos,
                                                                           @Nullable Direction side) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
    }
}
