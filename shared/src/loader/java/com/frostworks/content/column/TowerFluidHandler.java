package com.frostworks.content.column;

import java.util.List;

import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * The tower as pipes see it: the inputs go in (tanks 0 to 3, in the order they came), the product comes out (tank 4).
 * A fluid goes in if, with what is held, it still makes a recipe, up to its share of the room; only the product is
 * drained.
 */
final class TowerFluidHandler implements IFluidHandler {
    private static final int OUTPUT_TANK = Distillation.MAX_INPUTS;

    private final TowerBlockEntity tower;
    private final TowerStorage storage;

    TowerFluidHandler(TowerBlockEntity tower, TowerStorage storage) {
        this.tower = tower;
        this.storage = storage;
    }

    @Override
    public int getTanks() {
        return OUTPUT_TANK + 1;
    }

    @Override
    public FluidStack getFluidInTank(int i) {
        List<TowerStorage.Held> inputs = storage.inputs;
        if (i < inputs.size())
            return inputs.get(i).amount > 0 ? new FluidStack(inputs.get(i).fluid, inputs.get(i).amount)
                    : FluidStack.EMPTY;
        if (i == OUTPUT_TANK)
            return storage.outputFluid != null && storage.output > 0
                    ? new FluidStack(storage.outputFluid, storage.output) : FluidStack.EMPTY;
        return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int i) {
        if (i == OUTPUT_TANK)
            return tower.capacity();
        List<Distillation> all = storage.recipes();
        List<TowerStorage.Held> inputs = storage.inputs;
        if (i < inputs.size() && inputs.get(i).amount > 0)
            return Math.max(0, storage.roomFor(inputs.get(i).fluid, all));
        // a free tank: what the inputs held leave
        int left = Math.max(0, tower.capacity() - storage.output);
        for (TowerStorage.Held h : inputs)
            if (h.amount > 0)
                left -= Math.max(0, storage.roomFor(h.fluid, all));
        return Math.max(0, left);
    }

    @Override
    public boolean isFluidValid(int i, FluidStack stack) {
        return i < OUTPUT_TANK && !stack.isEmpty() && storage.roomFor(stack.getFluid(), storage.recipes()) >= 0;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !tower.isValid())
            return 0;
        Fluid fluid = resource.getFluid();
        int room = storage.roomFor(fluid, storage.recipes());
        if (room < 0)
            return 0; // makes no recipe with what is held: refused
        TowerStorage.Held h = storage.entry(fluid);
        if (h == null) {
            storage.inputs.removeIf(e -> e.amount <= 0 && Math.abs(e.credit) < 1e-9); // room for a new input
            if (storage.inputs.size() >= Distillation.MAX_INPUTS)
                return 0;
        }
        int n = Math.min(resource.getAmount(), room - (h == null ? 0 : h.amount));
        if (n <= 0)
            return 0;
        if (action.execute()) {
            if (h == null)
                storage.inputs.add(h = new TowerStorage.Held(fluid));
            h.amount += n;
            tower.changed();
        }
        return n;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || storage.outputFluid == null || !resource.getFluid().isSame(storage.outputFluid))
            return FluidStack.EMPTY; // the inputs stay in
        return drain(resource.getAmount(), action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        int n = Math.min(maxDrain, storage.output);
        if (n <= 0 || storage.outputFluid == null)
            return FluidStack.EMPTY;
        FluidStack out = new FluidStack(storage.outputFluid, n);
        if (action.execute()) {
            storage.output -= n;
            tower.changed();
        }
        return out;
    }
}
