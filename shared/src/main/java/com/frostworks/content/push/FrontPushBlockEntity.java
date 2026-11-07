package com.frostworks.content.push;

import java.util.List;

import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.foundation.advancement.AdvancementBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Create pump that only pushes, on its front face. The block exposes its output buffer as a drain-only fluid handler
 * on that face: the first pipe sees it as a source, and the pressure of this "pump" carries the fluid through Create's
 * pipe network. No external pump needed.
 */
public abstract class FrontPushBlockEntity extends com.frostworks.platform.PumpBase {

    protected FrontPushBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        // The pump's advancements do not make sense here
        behaviours.removeIf(b -> b instanceof AdvancementBehaviour);
    }

    @Override
    public boolean isSideAccessible(Direction side) {
        Direction front = getFront();
        return front != null && side == front;
    }

    @Override
    public boolean isPullingOnSide(boolean front) {
        return false;
    }

    /** Speed whose pressure carries the fluid through the pipes (rpm): Create's pump uses its own speed. */
    protected float pushSpeed() {
        return getSpeed();
    }

    @Override
    protected void distributePressureTo(Direction side) {
        if (side != getFront())
            return;
        // Create's pump reads its own speed for the pressure: lend it the push speed for that call only
        float own = speed;
        speed = pushSpeed();
        try {
            super.distributePressureTo(side);
        } finally {
            speed = own;
        }
    }

    /**
     * Forge 1.20.1 only ({@code Loader.NUDGE_PUMP_PRESSURE}; Create 6.0.10 does it itself): the pump's pressure is
     * worked out when its speed changes, and only reaches a network
     * whose far end can take the fluid then. A tower that forms after it (a few ticks later) was never reached. Every
     * second, a turning block whose front pipe has no pressure works it out again.
     */
    @Override
    public void tick() {
        super.tick();
        if (!com.frostworks.platform.Loader.NUDGE_PUMP_PRESSURE || level == null || level.isClientSide || getSpeed() == 0
                || level.getGameTime() % 20 != 0)
            return;
        Direction front = getFront();
        if (front == null)
            return;
        var pipe = BlockEntityBehaviour.get(level, worldPosition.relative(front),
                com.simibubi.create.content.fluids.FluidTransportBehaviour.TYPE);
        if (pipe != null && !pipe.hasAnyPressure())
            updatePressureChange();
    }

    public Direction front() {
        return getFront();
    }

    /** The stress this block puts on (or, if negative, gives back to) the network: impact x |speed| (su). */
    public float networkStress() {
        return lastStressApplied * Math.abs(getTheoreticalSpeed());
    }
}
