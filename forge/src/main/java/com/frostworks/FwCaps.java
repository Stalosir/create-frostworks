package com.frostworks;

import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * Forge 1.20.1's capabilities, for what NeoForge does with its capability registry at the root: each of our block
 * entities answers {@code getCapability} with one of these (a handler per side, from the same method as at the root),
 * and invalidates it when its handlers change ({@code level.invalidateCapabilities} at the root).
 */
public final class FwCaps<H> {
    private final Capability<H> capability;
    private final Function<Direction, H> source;
    @SuppressWarnings("unchecked")
    private final LazyOptional<H>[] cache = new LazyOptional[7];
    private final Object[] handlers = new Object[7];

    private FwCaps(Capability<H> capability, Function<Direction, H> source) {
        this.capability = capability;
        this.source = source;
    }

    public static FwCaps<IFluidHandler> fluids(Function<Direction, IFluidHandler> source) {
        return new FwCaps<>(ForgeCapabilities.FLUID_HANDLER, source);
    }

    public static FwCaps<IItemHandler> items(Function<Direction, IItemHandler> source) {
        return new FwCaps<>(ForgeCapabilities.ITEM_HANDLER, source);
    }

    /** This capability on this side, or null if it is not ours (the caller then asks its super class). */
    @Nullable
    public <T> LazyOptional<T> get(Capability<T> asked, @Nullable Direction side) {
        if (asked != capability)
            return null;
        int i = side == null ? 6 : side.ordinal();
        // the handler of a side can change with the block state (a compressor turned): NeoForge invalidates then by
        // itself, here the side is asked again each time
        H handler = source.apply(side);
        if (handler != handlers[i] && cache[i] != null) {
            cache[i].invalidate();
            cache[i] = null;
        }
        handlers[i] = handler;
        if (handler == null)
            return LazyOptional.empty();
        if (cache[i] == null || !cache[i].isPresent())
            cache[i] = LazyOptional.of(() -> handler);
        return cache[i].cast();
    }

    /** The handlers changed (or the block entity is gone): whoever holds one asks again. */
    public void invalidate() {
        for (int i = 0; i < cache.length; i++)
            if (cache[i] != null) {
                cache[i].invalidate();
                cache[i] = null;
                handlers[i] = null;
            }
    }

    /** The fluid handler of the block at this position on this side, or null (NeoForge: level.getCapability). */
    @Nullable
    public static IFluidHandler fluidAt(BlockGetter level, BlockPos pos, @Nullable Direction side) {
        BlockEntity be = level.getBlockEntity(pos);
        return be == null ? null : be.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElse(null);
    }

    /** The item handler of the block at this position on this side, or null. */
    @Nullable
    public static IItemHandler itemsAt(BlockGetter level, BlockPos pos, @Nullable Direction side) {
        BlockEntity be = level.getBlockEntity(pos);
        return be == null ? null : be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
    }
}
