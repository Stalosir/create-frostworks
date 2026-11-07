package com.frostworks.content.reservoir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.frostworks.registry.FwFluids;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Create's own fluid tank refuses Frosium: only our frost tank (the reservoir) holds them. This
 * wraps the handler Create gives for its tank (the tank, or the boiler's when the tank is a boiler) and refuses those
 * two liquids; everything else goes through as is. Registered before Create's own provider, which it then hides.
 */
public final class CreateTankGuard implements IFluidHandler {
    // Create's provider returns this field, filled by refreshCapability() (package-private): same here
    private static final Field CAPABILITY;
    private static final Method REFRESH;

    static {
        try {
            CAPABILITY = FluidTankBlockEntity.class.getDeclaredField("fluidCapability");
            CAPABILITY.setAccessible(true);
            REFRESH = FluidTankBlockEntity.class.getDeclaredMethod("refreshCapability");
            REFRESH.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Create's fluid tank changed", e);
        }
    }

    private final FluidTankBlockEntity tank;

    public CreateTankGuard(FluidTankBlockEntity tank) {
        this.tank = tank;
    }

    private IFluidHandler create() {
        try {
            if (CAPABILITY.get(tank) == null)
                REFRESH.invoke(tank);
            return (IFluidHandler) CAPABILITY.get(tank);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Create's fluid tank changed", e);
        }
    }

    @Override
    public int getTanks() {
        return create().getTanks();
    }

    @Override
    public FluidStack getFluidInTank(int i) {
        return create().getFluidInTank(i);
    }

    @Override
    public int getTankCapacity(int i) {
        return create().getTankCapacity(i);
    }

    @Override
    public boolean isFluidValid(int i, FluidStack stack) {
        return !FwFluids.isFrosium(stack) && create().isFluidValid(i, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return FwFluids.isFrosium(resource) ? 0 : create().fill(resource, action);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return create().drain(resource, action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return create().drain(maxDrain, action);
    }
}
