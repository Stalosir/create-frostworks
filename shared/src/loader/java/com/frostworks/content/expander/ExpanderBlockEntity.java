package com.frostworks.content.expander;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.frostworks.Frostworks;
import com.frostworks.FwConfig;
import com.frostworks.content.push.FrontPushBlockEntity;
import com.frostworks.energy.Energy;
import com.frostworks.registry.FwFluids;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import net.createmod.catnip.lang.LangBuilder;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.content.kinetics.base.IRotate;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * Expander. It consumes no rotation: it is a source. While it has liquid, it expands {@code expanderLiquidRate}
 * mB/s. The energy of that liquid (mB / kLiquide) is given back once: a share f (panel) as stress capacity on the
 * network it sits on, the rest as cold gas (x kGaz mB), pushed out of the front face like the compressor does.
 * <p>
 * Stress capacity without imposing a speed: a NEGATIVE stress impact. Create's network adds impact x |speed| over its
 * members, so -f.P / rpm takes exactly f.P su off the stress of the network, at the network's own speed. Nothing turns
 * without another source (speed 0), and then the expander gives nothing and waits.
 */
public class ExpanderBlockEntity extends FrontPushBlockEntity implements Energy.Account {

    protected FractionScrollValueBehaviour share;
    private final FluidTank liquid = new FluidTank(1000, FwFluids::isLiquid) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final FluidTank gas = new FluidTank(1000, FwFluids::isGas) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final IFluidHandler input = new Input();
    private final IFluidHandler output = new Output();

    private double liquidCredit;
    private double gasRemainder;
    /** Energy given back as stress capacity (su.s). */
    private double stressOut;
    /** Gas that has left the expander (mB, counted when a pipe drains it). */
    private double gasOut;
    /** Gas made with no room left in the buffer, let out into the air (mB). */
    private double gasLost;
    private boolean running;

    public ExpanderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        share = new FractionScrollValueBehaviour(Component.translatable("frostworks.expander.stress_share"), this,
                new SideSlot());
        share.withCallback(v -> pushStress());
        behaviours.add(share);
    }

    public int sharePercent() {
        return share == null ? 100 : share.getValue();
    }

    /** Power of the liquid expanded per second at full run (su.s per s = su). */
    public double power() {
        return Energy.ofLiquid(FwConfig.get(FwConfig.EXPANDER_LIQUID_RATE));
    }

    /** Stress capacity given back while running (su). */
    public float capacityGiven() {
        return (float) (sharePercent() / 100.0 * power());
    }

    private float computeImpact() {
        float rpm = Math.abs(getTheoreticalSpeed());
        return running && rpm != 0 ? -capacityGiven() / rpm : 0;
    }

    @Override
    public float calculateStressApplied() {
        float impact = computeImpact();
        lastStressApplied = impact;
        return impact;
    }

    private void pushStress() {
        if (level != null && !level.isClientSide && hasNetwork())
            getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        pushStress();
    }

    // ---- Tick ---------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide) {
            if (running)
                releaseParticles();
            return;
        }
        int buffer = FwConfig.get(FwConfig.EXPANDER_BUFFER);
        liquid.setCapacity(buffer);
        gas.setCapacity(buffer);

        double f = sharePercent() / 100.0;
        double perTick = FwConfig.get(FwConfig.EXPANDER_LIQUID_RATE) / 20.0;
        int need = (int) Math.ceil(liquidCredit + perTick - 1e-9);
        // Stress capacity needs a turning network (speed given by another source)
        // A full gas buffer does not stop it, the gas that does not fit is lost (counted as gone out)
        boolean canRun = liquid.getFluidAmount() >= need && (f == 0 || getTheoreticalSpeed() != 0);
        if (canRun != running) {
            running = canRun;
            pushStress();
            sendData();
        }
        if (!running)
            return;

        liquidCredit += perTick;
        int taken = (int) liquidCredit;
        liquidCredit -= taken;
        liquid.drain(taken, IFluidHandler.FluidAction.EXECUTE);
        double energy = Energy.ofLiquid(taken);
        // f of it as stress capacity: what the network really gets from us this tick, the rest as gas
        double stress = -lastStressApplied * Math.abs(getTheoreticalSpeed()) / 20.0;
        stressOut += stress;
        gasRemainder += (energy - stress) * Energy.kGas();
        int whole = (int) gasRemainder;
        if (whole > 0) {
            int kept = gas.fill(FwFluids.gas(whole), IFluidHandler.FluidAction.EXECUTE);
            gasRemainder -= whole;
            gasLost += whole - kept; // no room: let out into the air
        }
        if (level.getGameTime() % 10 == 0)
            sendData();
    }

    /**
     * Client: the reverse of the compressor, which draws air in: a little cold air let out of the two
     * shaft holes, on its sides (2 or 3 puffs a second), while it works.
     */
    private void releaseParticles() {
        var r = level.random;
        if (r.nextInt(8) != 0)
            return;
        Direction front = getFront();
        if (front == null)
            return;
        // The shaft holes are on the two faces beside the flanges
        Direction side = r.nextBoolean() ? front.getClockWise() : front.getCounterClockWise();
        double x = worldPosition.getX() + 0.5 + side.getStepX() * 0.55;
        double y = worldPosition.getY() + 0.5 + side.getStepY() * 0.55;
        double z = worldPosition.getZ() + 0.5 + side.getStepZ() * 0.55;
        level.addParticle(r.nextBoolean() ? net.minecraft.core.particles.ParticleTypes.CLOUD
                        : net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, x, y, z,
                side.getStepX() * 0.04, 0.01, side.getStepZ() * 0.04);
    }

    // ---- Energy -------------------------------------------------------------------------------------------------

    @Override
    public double energyIn() {
        return 0;
    }

    @Override
    public double energyOut() {
        return stressOut + Energy.ofGas(gasOut + gasLost);
    }

    public double stressOut() {
        return stressOut;
    }

    public double gasOutEnergy() {
        return Energy.ofGas(gasOut);
    }

    @Override
    public double energyStock() {
        return Energy.ofLiquid(liquid.getFluidAmount()) + Energy.ofGas(gas.getFluidAmount() + gasRemainder);
    }

    public boolean isRunning() {
        return running;
    }

    // ---- Fluids -------------------------------------------------------------------------------------------------

    // Create counts a pipe on a pump's back as an endpoint only if the pump pulls there: without this, no Frosium in
    @Override
    public boolean isPullingOnSide(boolean front) {
        return !front;
    }

    @Nullable
    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        Direction front = getFront();
        if (side == null)
            return input;
        if (front == null)
            return null;
        // Gas out of the front flange, liquid in at the back flange, nothing elsewhere
        if (side == front)
            return output;
        return side == front.getOpposite() ? input : null;
    }

    /** The back flange: Frosium (any cold liquid) in. */
    private class Input implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int i) {
            return liquid.getFluid();
        }

        @Override
        public int getTankCapacity(int i) {
            return liquid.getCapacity();
        }

        @Override
        public boolean isFluidValid(int i, FluidStack stack) {
            return FwFluids.isLiquid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return liquid.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }

    /** Front face: cold gas out, drain only. What leaves is counted as returned. */
    private class Output implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int i) {
            return gas.getFluid();
        }

        @Override
        public int getTankCapacity(int i) {
            return gas.getCapacity();
        }

        @Override
        public boolean isFluidValid(int i, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return counted(gas.drain(resource, action), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return counted(gas.drain(maxDrain, action), action);
        }

        private FluidStack counted(FluidStack drained, FluidAction action) {
            if (action.execute())
                gasOut += drained.getAmount();
            return drained;
        }
    }

    // ---- Goggles ------------------------------------------------------------------------------------------------

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        // Shown as a stress source, as Create shows a water wheel or a steam engine (its capacity, positive),
        // not as Create's kinetic stats of a negative impact; the impact the network holds is unchanged
        float capacity = (float) capacityGiven();
        if (IRotate.StressImpact.isEnabled() && running && capacity > 0) {
            CreateLang.translate("gui.goggles.generator_stats").forGoggles(tooltip);
            CreateLang.translate("tooltip.capacityProvided").style(ChatFormatting.GRAY).forGoggles(tooltip);
            CreateLang.number(capacity).translate("generic.unit.stress").style(ChatFormatting.AQUA).space()
                    .add(CreateLang.translate("gui.goggles.at_current_speed").style(ChatFormatting.DARK_GRAY))
                    .forGoggles(tooltip, 1);
        }
        new LangBuilder(Frostworks.MODID).translate("goggles.expander").forGoggles(tooltip);
        new LangBuilder(Frostworks.MODID).translate(running ? "goggles.expander.running" : "goggles.expander.idle")
                .style(running ? ChatFormatting.AQUA : ChatFormatting.GOLD).forGoggles(tooltip, 1);
        if (!running) {
            // what it needs, one line each: green when there, red when missing
            new LangBuilder(Frostworks.MODID).translate("goggles.expander.needs").style(ChatFormatting.GRAY)
                    .forGoggles(tooltip, 1);
            boolean hasLiquid = liquid.getFluidAmount() > 0;
            new LangBuilder(Frostworks.MODID).translate("goggles.expander.needs.liquid")
                    .style(hasLiquid ? ChatFormatting.GREEN : ChatFormatting.RED).forGoggles(tooltip, 2);
            if (sharePercent() > 0) {
                boolean turning = getTheoreticalSpeed() != 0;
                new LangBuilder(Frostworks.MODID).translate("goggles.expander.needs.network")
                        .style(turning ? ChatFormatting.GREEN : ChatFormatting.RED).forGoggles(tooltip, 2);
            }
        }
        new LangBuilder(Frostworks.MODID).translate("goggles.expander.split", sharePercent(),
                        String.format("%.0f", capacityGiven()),
                        String.format("%.1f", (1 - sharePercent() / 100.0) * power() * Energy.kGas()))
                .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        com.frostworks.platform.Loader.fluidTooltip(this, tooltip, isPlayerSneaking, new IFluidHandler() {
            @Override
            public int getTanks() {
                return 2;
            }

            @Override
            public FluidStack getFluidInTank(int i) {
                return (i == 0 ? liquid : gas).getFluid();
            }

            @Override
            public int getTankCapacity(int i) {
                return (i == 0 ? liquid : gas).getCapacity();
            }

            @Override
            public boolean isFluidValid(int i, FluidStack stack) {
                return false;
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                return 0;
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                return FluidStack.EMPTY;
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                return FluidStack.EMPTY;
            }
        });
        return true;
    }

    // ---- NBT ----------------------------------------------------------------------------------------------------

    @Override
    protected void writeFw(CompoundTag tag, @Nullable HolderLookup.Provider registries, boolean clientPacket) {
        tag.put("Liquid", com.frostworks.platform.Loader.writeTank(liquid, registries, new CompoundTag()));
        tag.put("Gas", com.frostworks.platform.Loader.writeTank(gas, registries, new CompoundTag()));
        tag.putDouble("LiquidCredit", liquidCredit);
        tag.putDouble("GasRemainder", gasRemainder);
        tag.putDouble("StressOut", stressOut);
        tag.putDouble("GasOut", gasOut);
        tag.putDouble("GasLost", gasLost);
        tag.putBoolean("Running", running);
    }

    @Override
    protected void readFw(CompoundTag tag, @Nullable HolderLookup.Provider registries, boolean clientPacket) {
        com.frostworks.platform.Loader.readTank(liquid, registries, tag.getCompound("Liquid"));
        com.frostworks.platform.Loader.readTank(gas, registries, tag.getCompound("Gas"));
        liquidCredit = tag.getDouble("LiquidCredit");
        gasRemainder = tag.getDouble("GasRemainder");
        stressOut = tag.getDouble("StressOut");
        gasOut = tag.getDouble("GasOut");
        gasLost = tag.getDouble("GasLost");
        running = tag.getBoolean("Running");
    }

    /** The value box on the screen (the top) only: every other face stays free to build against. */
    private static class SideSlot extends ValueBoxTransform.Sided {
        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8, 8, 14.5f);
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return direction == Direction.UP;
        }

        @Override
        public float getScale() {
            return 0.5f;
        }
    }
}
