package com.frostworks.compat.greate;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import electrolyte.greate.content.kinetics.simpleRelays.ITieredKineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Greate (Forge 1.20.1 only): the shaft passage made on one of Greate's shafts. It keeps that shaft: Greate caps
 * the network by it, as by the shaft itself ({@link ITieredKineticBlockEntity}, Greate's interface: this class is only
 * loaded with Greate), it is drawn turning inside with Greate's own shaft model (its material's colour), and given back when the passage breaks.
 */
public class GreateShaftPassageBlockEntity extends KineticBlockEntity implements ITieredKineticBlockEntity {

    @Nullable private Block shaft;

    public GreateShaftPassageBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The Greate shaft inside, null until set (a passage placed by a command). */
    @Nullable
    public Block shaft() {
        return shaft;
    }

    /** Called right after the encasing: the shaft that was there. The network takes its cap. */
    public void setShaft(Block shaft) {
        this.shaft = shaft;
        setChanged();
        sendData();
        if (hasNetwork())
            getOrCreateNetwork().updateNetwork(); // Greate works its cap out again there
    }

    /** Greate asks every tiered member of the network: the cap of the shaft inside, not of the passage. */
    @Override
    public float getMaxCapacityFromBlock(Block ignored) {
        return ITieredKineticBlockEntity.super.getMaxCapacityFromBlock(shaft != null ? shaft : ignored);
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        if (shaft != null)
            tag.putString("Shaft", BuiltInRegistries.BLOCK.getKey(shaft).toString());
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        Block before = shaft;
        ResourceLocation id = tag.contains("Shaft") ? ResourceLocation.tryParse(tag.getString("Shaft")) : null;
        Block read = id == null ? null : BuiltInRegistries.BLOCK.get(id);
        shaft = read == null || read == Blocks.AIR ? null : read;
        // the client drew the passage before it knew its shaft: drawn again with it
        if (clientPacket && before != shaft && level != null && level.isClientSide)
            GreateShaftPassageVisual.redraw(this);
    }
}
