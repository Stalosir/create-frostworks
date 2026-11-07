package com.frostworks.content.compressor;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.frostworks.Frostworks;
import com.frostworks.FwConfig;
import com.frostworks.FwConfig.StressMode;
import com.frostworks.registry.FwFluids;
import com.frostworks.content.push.FrontPushBlockEntity;
import com.frostworks.energy.Energy;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.level.LevelAccessor;

import net.createmod.catnip.lang.LangBuilder;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * Compressor: takes a set amount of stress from a Create network and turns it into cold gas.
 * <ul>
 * <li>Stress: {@link #calculateStressApplied} returns the impact per rpm that Create multiplies by the speed. In
 * {@link StressMode#TOTAL} the impact is setting / |speed|, so the network sees the setting whatever the speed.</li>
 * <li>Gas: produced in an internal buffer, exposed as a drain-only fluid handler on the front face. The compressor
 * is a pump that pushes on its front face only: the first pipe sees the buffer as a fluid source, and Create's
 * pipe network carries the gas to the vents. No external pump needed.</li>
 * </ul>
 */
public class CompressorBlockEntity extends FrontPushBlockEntity implements Energy.Account {

    protected StressScrollValueBehaviour targetStress;
    protected final FluidTank tank = new FluidTank(1000, FwFluids::isGas) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final IFluidHandler output = new OutputHandler();
    /** Gas made but not yet a whole mB (counted in the stock). */
    private double productionRemainder;
    /** Energy turned into gas since the block was placed (su.s). */
    private double energyIn;
    /** Draws stress only while it can store what it makes (stops when full, starts again below 75 %). */
    private boolean producing = true;
    private int producedThisWindow;
    private int windowTicks;
    private float measuredRate;

    // Synced to the client for the goggles and particles
    private float appliedStress;
    private float gasPerSecond;
    private float maxGasPerSecond;
    private StressMode clientMode = StressMode.TOTAL;

    public CompressorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        targetStress = new StressScrollValueBehaviour(
                Component.translatable("frostworks.compressor.target_stress"), this, new MarkSlot());
        targetStress.withCallback(v -> pushStressToNetwork());
        behaviours.add(targetStress);
    }

    // ---- Stress -------------------------------------------------------------------------------------------------

    public StressMode mode() {
        return level != null && level.isClientSide ? clientMode : FwConfig.get(FwConfig.STRESS_MODE);
    }

    public int setting() {
        return targetStress == null ? FwConfig.get(FwConfig.STRESS_DEFAULT) : targetStress.getValue();
    }

    /** Impact per rpm wanted right now. */
    protected float computeImpact() {
        if (!producing)
            return 0; // buffer full or too slow: no stress drawn, nothing wasted
        // Greate's scale (Energy.stressScale): 256 su set, 25.6 drawn; the same gas, Energy.kGas follows
        float setting = (float) (setting() * Energy.stressScale());
        if (mode() == StressMode.PER_RPM)
            return setting;
        float rpm = Math.abs(getTheoreticalSpeed());
        return rpm == 0 ? 0 : setting / rpm;
    }

    @Override
    public float calculateStressApplied() {
        // Every path that stores our impact in the network (add, addSilently, updateStressFor) goes through here,
        // so lastStressApplied is always what the network holds.
        float impact = computeImpact();
        lastStressApplied = impact;
        return impact;
    }

    protected void pushStressToNetwork() {
        if (level == null || level.isClientSide || !hasNetwork())
            return;
        getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        pushStressToNetwork();
    }

    /**
     * The compressor does not care how fast it turns, only about the stress it takes. From
     * {@code compressorMinRpm} on, it pushes its gas as hard as a pump at full speed, so the pipes never hold back
     * what the stress makes.
     */
    @Override
    protected float pushSpeed() {
        float own = getSpeed();
        return turnsEnough() ? Math.copySign(256f, own) : 0;
    }

    /** Fast enough to work ({@code compressorMinRpm}, 8 by default). */
    public boolean turnsEnough() {
        return Math.abs(getSpeed()) >= FwConfig.get(FwConfig.COMPRESSOR_MIN_RPM);
    }

    /** Stress the network sees from this block, in su. */
    public float appliedStress() {
        return computeImpact() * Math.abs(getTheoreticalSpeed());
    }

    // ---- Tick ---------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide) {
            if (gasPerSecond > 0)
                spawnIntakeParticles();
            return;
        }

        // A changed config (mode, setting bounds) or a missed speed update must not leave a stale impact
        if (hasNetwork() && computeImpact() != lastStressApplied)
            pushStressToNetwork();

        Direction front = getFront();
        if (front == null)
            return;
        tank.setCapacity(FwConfig.get(FwConfig.COMPRESSOR_BUFFER));
        // No blocked intake any more, it draws air in wherever it is set
        boolean nowProducing = Math.abs(getTheoreticalSpeed()) >= FwConfig.get(FwConfig.COMPRESSOR_MIN_RPM)
                && (producing ? tank.getSpace() > 0 : tank.getFluidAmount() <= tank.getCapacity() * 3 / 4);
        if (nowProducing != producing) {
            producing = nowProducing;
            pushStressToNetwork();
        }

        float applied = appliedStress();
        // getSpeed is 0 when the network is overstressed: no production then
        boolean running = producing && getSpeed() != 0;
        float rate = running ? (float) (applied * Energy.kGas()) : 0;
        if (running) {
            double energy = applied / 20.0;
            energyIn += energy;
            productionRemainder += energy * Energy.kGas();
            int whole = (int) productionRemainder;
            if (whole > 0) {
                int produced = tank.fill(FwFluids.gas(whole), IFluidHandler.FluidAction.EXECUTE);
                productionRemainder -= produced; // what did not fit stays here, never thrown away
                producedThisWindow += produced;
            }
        }
        // Gas actually produced, measured over the last second
        boolean windowEnd = ++windowTicks >= 20;
        if (windowEnd) {
            windowTicks = 0;
            measuredRate = producedThisWindow;
            producedThisWindow = 0;
        }

        boolean changed = Math.abs(applied - appliedStress) > 0.01f
                || Math.abs(rate - maxGasPerSecond) > 0.01f || clientMode != mode()
                || windowEnd && measuredRate != gasPerSecond;
        appliedStress = applied;
        maxGasPerSecond = rate;
        gasPerSecond = measuredRate;
        clientMode = mode();
        if (changed)
            sendData();
    }

    @Override
    public double energyIn() {
        return energyIn;
    }

    @Override
    public double energyOut() {
        return 0;
    }

    @Override
    public double energyStock() {
        return Energy.ofGas(tank.getFluidAmount() + productionRemainder);
    }

    /**
     * The air goes in through the grille of the four sides of the cage (the faces around the axis), not
     * the back any more. Same particles, same rate (one tick in two) while it produces; the client part places them.
     */
    private void spawnIntakeParticles() {
        if (level.random.nextInt(2) != 0)
            return;
        com.frostworks.client.CompressorParticles.spawn(level, worldPosition, getBlockState());
    }

    // ---- Fluid --------------------------------------------------------------------------------------------------

    @Nullable
    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        return side == null || side == getFront() ? output : null;
    }

    public FluidStack gas() {
        return tank.getFluid();
    }

    /** Drain-only view of the buffer, exposed on the front face. */
    private class OutputHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int i) {
            return tank.getFluid();
        }

        @Override
        public int getTankCapacity(int i) {
            return tank.getCapacity();
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
            return tank.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return tank.drain(maxDrain, action);
        }
    }

    // ---- Goggles ------------------------------------------------------------------------------------------------

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        LangBuilder lang = lang();
        lang.translate("goggles.compressor").forGoggles(tooltip);
        lang().translate("goggles.compressor.applied").style(ChatFormatting.GRAY).forGoggles(tooltip);
        lang().text(Energy.su(appliedStress) + " su").style(ChatFormatting.AQUA)
                .add(lang().text(" (" + clientMode.name() + ", " + Energy.su(setting() * Energy.stressScale()) + ")").style(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);
        lang().translate("goggles.compressor.production").style(ChatFormatting.GRAY).forGoggles(tooltip);
        lang().text(String.format("%.1f mB/s", gasPerSecond)).style(ChatFormatting.AQUA)
                .add(lang().translate("goggles.compressor.production.max", String.format("%.1f", maxGasPerSecond))
                        .style(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);
        if (getSpeed() != 0 && !turnsEnough())
            lang().translate("goggles.compressor.too_slow", FwConfig.get(FwConfig.COMPRESSOR_MIN_RPM))
                    .style(ChatFormatting.GOLD).forGoggles(tooltip);
        com.frostworks.platform.Loader.fluidTooltip(this, tooltip, isPlayerSneaking, output);
        return true;
    }

    private static LangBuilder lang() {
        return new LangBuilder(Frostworks.MODID);
    }

    // ---- NBT ----------------------------------------------------------------------------------------------------

    @Override
    protected void writeFw(CompoundTag tag, @Nullable HolderLookup.Provider registries, boolean clientPacket) {
        tag.put("Gas", com.frostworks.platform.Loader.writeTank(tank, registries, new CompoundTag()));
        tag.putDouble("EnergyIn", energyIn);
        tag.putDouble("GasRemainder", productionRemainder);
        tag.putBoolean("Producing", producing);
        if (clientPacket) {
            tag.putFloat("Applied", appliedStress);
            tag.putFloat("GasRate", gasPerSecond);
            tag.putFloat("GasMax", maxGasPerSecond);
            tag.putString("Mode", clientMode.name());
        }
    }

    @Override
    protected void readFw(CompoundTag tag, @Nullable HolderLookup.Provider registries, boolean clientPacket) {
        com.frostworks.platform.Loader.readTank(tank, registries, tag.getCompound("Gas"));
        energyIn = tag.getDouble("EnergyIn");
        productionRemainder = tag.getDouble("GasRemainder");
        producing = !tag.contains("Producing") || tag.getBoolean("Producing");
        if (clientPacket) {
            appliedStress = tag.getFloat("Applied");
            gasPerSecond = tag.getFloat("GasRate");
            maxGasPerSecond = tag.getFloat("GasMax");
            try {
                clientMode = StressMode.valueOf(tag.getString("Mode"));
            } catch (IllegalArgumentException e) {
                clientMode = StressMode.TOTAL;
            }
        }
    }

    /** Tests: the stress setting (su). */
    public void setSettingForTest(int su) {
        targetStress.setValue(su);
    }

    public float gasPerSecond() {
        return gasPerSecond;
    }

    /**
     * The setting sits on the black mark drawn on the north face of the cage (see
     * {@link CompressorGeometry}), at its size, and turns with the block exactly as the model does.
     */
    static class MarkSlot extends ValueBoxTransform {
        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            Direction facing = state.getValue(CompressorBlock.FACING);
            // a hair out of the cage face, so the box is drawn over the mark
            Vec3 centre = CompressorGeometry.MARK_CENTER.add(0, 0, -0.05);
            return CompressorGeometry.toBlock(centre, facing);
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            // model space, then the box (drawn facing +Z) turned to face the model's north
            ms.mulPose(CompressorGeometry.turn(state.getValue(CompressorBlock.FACING)));
            ms.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
        }

        /** The mark is 4 pixels wide. */
        @Override
        public float getScale() {
            return CompressorGeometry.MARK_WIDTH / 16f;
        }

        /** A hit on the cage face, or on the block face in front of it: only the distance across the face counts. */
        @Override
        public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
            Direction mark = CompressorGeometry.markFace(state.getValue(CompressorBlock.FACING));
            Vec3 d = localHit.subtract(getLocalOffset(level, pos, state));
            double across = d.subtract(Vec3.atLowerCornerOf(mark.getNormal()).scale(d.dot(Vec3.atLowerCornerOf(mark.getNormal()))))
                    .length();
            return across < getScale() / 2 && d.dot(Vec3.atLowerCornerOf(mark.getNormal())) > -0.05;
        }
    }

    /** Text of the current setting, shown on the mark; In real su, Greate's scale included. */
    public String settingText() {
        return Energy.su(setting() * Energy.stressScale()) + " su";
    }
}
