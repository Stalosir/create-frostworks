package com.frostworks.content.reservoir;

import org.jetbrains.annotations.Nullable;
import com.frostworks.FwConfig;
import com.frostworks.energy.Energy;
import com.frostworks.registry.FwFluids;
import com.frostworks.registry.FwTags;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.fluid.SmartFluidTank;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * Create's tank logic (multiblock, rendering, goggles), restricted to Frosium, with our own
 * capacity per block. Create reads its capacity from a static method in three places; the three instance methods that
 * use it are overridden here.
 */
public class ReservoirBlockEntity extends com.frostworks.platform.TankBase implements Energy.Account {

    public ReservoirBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static int perBlock() {
        return FwConfig.get(FwConfig.RESERVOIR_CAPACITY);
    }

    @Override
    protected SmartFluidTank createInventory() {
        SmartFluidTank tank = new SmartFluidTank(perBlock(), this::onFluidStackChanged);
        // The cold liquids of the tags (ours, an add-on's); cold gas,
        // water and anything else are refused
        tank.setValidator(FwTags::frostTankTakes);
        return tank;
    }

    @Override
    public void applyFluidTankSize(int blocks) {
        tankInventory.setCapacity(blocks * perBlock());
        int overflow = tankInventory.getFluidAmount() - tankInventory.getCapacity();
        if (overflow > 0)
            tankInventory.drain(overflow, IFluidHandler.FluidAction.EXECUTE);
        forceFluidLevelUpdate = true;
    }

    @Override
    public int getTankSize(int tank) {
        return perBlock();
    }

    @Override
    protected void readFw(CompoundTag compound, @Nullable HolderLookup.Provider registries, boolean clientPacket) {
        if (isController()) {
            // Create sized the tank with its own capacity: size it again with ours, and read the content again
            tankInventory.setCapacity(getTotalTankSize() * perBlock());
            com.frostworks.platform.Loader.readTank(tankInventory, registries, compound.getCompound("TankContent"));
            // and the level drawn: Create set it from its own capacity (with Create's fluidTankCapacity at 800, a full
            // frost tank was drawn almost empty)
            var level = getFluidLevel();
            if (level != null && compound.contains("ForceFluidLevel"))
                level.startWithValue(getFillState());
            else if (level != null)
                level.chase(getFillState(), .5f, net.createmod.catnip.animation.LerpedFloat.Chaser.EXP);
        }
    }

    /** Fluid handler for the capability (the controller's tank, as Create does). */
    public IFluidHandler capability() {
        FluidTankBlockEntity controller = getControllerBE();
        return controller != null ? controller.getTankInventory() : new FluidTank(0);
    }

    @Override
    public double energyIn() {
        return 0;
    }

    @Override
    public double energyOut() {
        return 0;
    }

    /** Only the controller holds the content of the whole reservoir. */
    @Override
    public double energyStock() {
        return isController() ? FwFluids.energy(tankInventory.getFluid()) : 0;
    }
}
