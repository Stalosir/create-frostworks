package com.frostworks.content.column;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.frostworks.FwConfig;
import com.frostworks.content.column.TowerBlock.Corner;
import com.frostworks.energy.Energy;
import com.frostworks.platform.KineticBase;
import com.frostworks.platform.Mc;
import com.frostworks.registry.FwFluids;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The distillation tower: a 2 x 2 multiblock, a layer of column bases under layers of column segments. Every block has
 * this block entity; the controller (the north-west block of the bottom layer, chosen by Create's
 * {@code ConnectivityHandler}, as for a fluid tank) holds and runs everything:
 * <ul>
 * <li><b>fluids</b> ({@link TowerStorage}): only the column holds anything, {@code towerCapacityPerBlock} a block;</li>
 * <li><b>pipes</b> ({@link TowerFluidHandler}): only the bottom layer, on its outer faces and underneath, all alike;
 * the product is pulled out by a pump, the tower does not push;</li>
 * <li><b>ice</b> ({@link TowerIce}): a reserve filled by funnels or arms;</li>
 * <li><b>rotation</b>: a shaft on an outer side of any base; the four bases turn together, and only the controller
 * draws stress, once for the whole tower;</li>
 * <li><b>production</b>: the recipe's first input is taken at a rate set by the layers and the speed; the product, the
 * other inputs and the ice follow. It stops, without drawing stress, when something is missing or the product has no
 * room ({@link Stop}).</li>
 * </ul>
 * Minecraft's {@code Fluid} is written in full here: Create's {@code IMultiBlockEntityContainer} has a {@code Fluid} of
 * its own on Forge.
 */
public class TowerBlockEntity extends KineticBase implements IMultiBlockEntityContainer, Energy.Account {

    /** Why the tower does not run (goggles). */
    public enum Stop {
        NONE, INVALID, NO_ROTATION, NO_GAS, NO_WATER, NO_ICE, OUTPUT_FULL,
        /** An input that is neither cold gas nor water is missing. */
        NO_INPUT,
        /** What it holds makes no recipe (or there is none). */
        NO_RECIPE
    }

    /** What a renderer or the goggles see of a fluid held. */
    public record Content(net.minecraft.world.level.material.Fluid fluid, int amount) {}

    // ---- Multiblock (as Create's FluidTankBlockEntity) ----
    @Nullable
    protected BlockPos controller;
    @Nullable
    protected BlockPos lastKnownPos;
    protected boolean updateConnectivity;
    protected int width = 1;
    protected int height = 1;
    /** The controller's: the group it heads is a valid tower (worked out again whenever the group changes). */
    boolean valid;

    // ---- The controller's content and state ----
    final TowerStorage storage = new TowerStorage(this);
    private final TowerIce ice = new TowerIce(this::changed, () -> valid && storage.takesIce());
    private final IFluidHandler fluids = new TowerFluidHandler(this, storage);
    /** The recipe of this tick, chosen by {@link #tryStart}. */
    @Nullable
    private Distillation active;
    private double energyIn;
    boolean running;
    Stop stop = Stop.INVALID;

    public TowerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // =================================================================================================================
    // Multiblock
    // =================================================================================================================

    protected void updateConnectivity() {
        updateConnectivity = false;
        if (level.isClientSide || !isController())
            return;
        ConnectivityHandler.formMulti(this);
    }

    @Override
    public void initialize() {
        super.initialize();
        if (!level.isClientSide && isController() && width == 1 && height == 1)
            updateConnectivity = true; // a block on its own (just placed, or left alone): look for its tower
        sendData();
    }

    @Override
    public BlockPos getController() {
        return isController() ? worldPosition : controller;
    }

    @Override
    @SuppressWarnings("unchecked")
    public TowerBlockEntity getControllerBE() {
        if (isController() || !hasLevel())
            return this;
        BlockEntity be = level.getBlockEntity(controller);
        return be instanceof TowerBlockEntity t ? t : null;
    }

    @Override
    public boolean isController() {
        return controller == null || worldPosition.equals(controller);
    }

    @Override
    public void setController(BlockPos newController) {
        if (level.isClientSide && !isVirtual())
            return;
        if (newController.equals(this.controller))
            return;
        // a former controller joins a bigger tower: its content goes to the new controller
        if (isController() && !newController.equals(worldPosition)
                && level.getBlockEntity(newController) instanceof TowerBlockEntity target && target != this)
            target.absorb(this);
        this.controller = newController;
        refreshHandlers();
        setChanged();
        sendData();
    }

    @Override
    public void removeController(boolean keepContents) {
        if (level.isClientSide)
            return;
        updateConnectivity = true;
        controller = null;
        width = 1;
        height = 1;
        applyBlockState();
        refreshHandlers();
        setChanged();
        sendData();
    }

    @Override
    public BlockPos getLastKnownPos() {
        return lastKnownPos;
    }

    @Override
    public void preventConnectivityUpdate() {
        updateConnectivity = false;
    }

    @Override
    public void notifyMultiUpdated() {
        // capabilities first: the state change below updates the neighbours (pipes, pumps), which ask for them again
        refreshHandlers();
        applyBlockState();
        if (isController())
            storage.clampToCapacity();
        refreshHandlers();
        setChanged();
        sendData();
    }

    @Override
    public Direction.Axis getMainConnectionAxis() {
        return Direction.Axis.Y;
    }

    @Override
    public int getMaxLength(Direction.Axis longAxis, int width) {
        return longAxis == Direction.Axis.Y ? FwConfig.get(FwConfig.TOWER_MAX_LAYERS) : getMaxWidth();
    }

    /** At most 2 wide; a group of width 1 forms too, but stays invalid (inert). */
    @Override
    public int getMaxWidth() {
        return 2;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public void setHeight(int height) {
        this.height = height;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public void setWidth(int width) {
        this.width = width;
    }

    /** Part of a valid tower (see {@link #isValidTower}). */
    public boolean isValid() {
        TowerBlockEntity c = getControllerBE();
        return c != null && c.valid;
    }

    public int layers() {
        TowerBlockEntity c = getControllerBE();
        return c != null && c.valid ? c.height : 0;
    }

    /**
     * A group is a valid tower if it is exactly 2 wide, its bottom layer is a full 2 x 2 of column bases and every
     * layer above a full 2 x 2 of column segments (Create forms full, aligned layers only). Anything else (a base above
     * the bottom, segments on the ground, width 1...) stays inert. Every part works it out the same way.
     */
    static boolean isValidTower(Level level, BlockPos corner, int width, int height) {
        if (width != 2)
            return false;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < 2; x++)
                for (int z = 0; z < 2; z++) {
                    var block = level.getBlockState(corner.offset(x, y, z)).getBlock();
                    if (y == 0 ? !(block instanceof ColumnBaseBlock) : !(block instanceof ColumnSegmentBlock))
                        return false;
                }
        return true;
    }

    /** Layer and corner of this block in its tower, written into the block state (models, shaft, pipes). */
    private void applyBlockState() {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof TowerBlock block))
            return;
        // this block's own size: Create sets it on every part before notifying it, the controller's comes last
        BlockPos c = getController();
        boolean ok = isValidTower(level, c, width, height);
        if (isController())
            valid = ok;
        Corner corner = Corner.of(worldPosition.getX() - c.getX(), worldPosition.getZ() - c.getZ());
        BlockState next = block.inTower(state, ok, worldPosition.getY() - c.getY(), height, corner);
        if (next != state)
            // through Create: a block that becomes (or stops being) a base gains (or loses) its shaft face
            switchToBlockState(level, worldPosition, next);
    }

    /** Another block of this block's tower (to keep the content when the controller is broken), or null. */
    @Nullable
    TowerBlockEntity anotherPart() {
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                for (int z = 0; z < width; z++) {
                    BlockPos p = worldPosition.offset(x, y, z);
                    if (!p.equals(worldPosition) && level.getBlockEntity(p) instanceof TowerBlockEntity t)
                        return t;
                }
        return null;
    }

    /** Takes the content of another controller (towers joining, or the controller broken). */
    void absorb(TowerBlockEntity other) {
        storage.absorb(other.storage);
        energyIn += other.energyIn;
        other.energyIn = 0;
        ice.absorb(other.ice, level, worldPosition); // what does not fit (a block that already had some) drops here
        // no clamp here: this block may not have its final size yet (notifyMultiUpdated clamps once it has)
        setChanged();
    }

    /** The last block of a tower broken: its ice drops (as a basin drops its items). */
    void dropIce() {
        ice.drop(level, worldPosition);
    }

    // =================================================================================================================
    // Room, pipes and ice
    // =================================================================================================================

    /** The whole room of the tower (the column; the bases hold nothing): the product first, the rest for the inputs. */
    public int capacity() {
        int blocks = valid ? width * width * (height - 1) : 0;
        return blocks * FwConfig.get(FwConfig.TOWER_CAPACITY_PER_BLOCK);
    }

    /**
     * The fluid handler of this face: only on the bottom layer of a valid tower, on its outer faces and underneath
     * (null side: the same, for tools). Everything else exposes nothing, so no pipe connects there.
     */
    @Nullable
    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        BlockState state = getBlockState();
        if (!TowerBlock.isBase(state))
            return null;
        if (side != null && side != Direction.DOWN && !state.getValue(TowerBlock.CORNER).isOutside(side))
            return null;
        TowerBlockEntity c = getControllerBE();
        return c == null ? null : c.fluids;
    }

    /**
     * The ice input: on the bases only, on their outer sides; and on top for Create's mechanical arm, which always asks
     * with that face (the column covers it, nothing else reaches it). Null side: the same, for tools.
     */
    @Nullable
    public IItemHandler getItemHandler(@Nullable Direction side) {
        BlockState state = getBlockState();
        if (!TowerBlock.isBase(state))
            return null;
        if (side == Direction.DOWN || side != null && side != Direction.UP
                && !state.getValue(TowerBlock.CORNER).isOutside(side))
            return null;
        TowerBlockEntity c = getControllerBE();
        return c == null ? null : c.ice.input;
    }

    /** Packed ice left: the share taken out and the reserve. */
    public double iceStock() {
        TowerBlockEntity c = getControllerBE();
        return c == null ? 0 : c.ice.stock();
    }

    /** Saved at once, sent to the clients a few times a second at most. */
    void changed() {
        setChanged();
        if (level != null && !level.isClientSide && level.getGameTime() % 5 == 0)
            sendData();
    }

    // =================================================================================================================
    // Rotation and stress
    // =================================================================================================================

    /** The other base blocks of the same tower (including the diagonal one) are rotation neighbours. */
    @Override
    public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours) {
        if (TowerBlock.isBase(state) && isValid()) {
            BlockPos c = getController();
            for (Corner corner : Corner.values()) {
                BlockPos p = c.offset(corner.dx, 0, corner.dz);
                if (!p.equals(worldPosition) && !neighbours.contains(p))
                    neighbours.add(p);
            }
        }
        return neighbours;
    }

    /** Two base blocks of the same tower turn together, at the same speed. */
    @Override
    public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff,
                                     boolean connectedViaAxes, boolean connectedViaCogs) {
        if (target instanceof TowerBlockEntity t && TowerBlock.isBase(stateFrom) && TowerBlock.isBase(stateTo)
                && getController().equals(t.getController()))
            return 1;
        return 0;
    }

    /**
     * First input (cold gas) taken per second at full run (mB/s): {@code towerGasPerLayer} a layer at
     * {@code towerReferenceRpm}, as fast as it turns.
     */
    public int gasRate() {
        return (int) Math.round(layers() * FwConfig.get(FwConfig.TOWER_GAS_PER_LAYER)
                * Math.abs(getTheoreticalSpeed()) / FwConfig.get(FwConfig.TOWER_REFERENCE_RPM));
    }

    /** Stress drawn while running (su): {@code towerStressPerRpm} a layer, at its speed, on Greate's scale if set. */
    public float targetStress() {
        return (float) (layers() * FwConfig.get(FwConfig.TOWER_STRESS_PER_RPM) * Energy.stressScale()
                * Math.abs(getTheoreticalSpeed()));
    }

    /** Only the controller has an impact (su per rpm, the same at any speed): once for the whole tower. */
    @Override
    public float calculateStressApplied() {
        float rpm = Math.abs(getTheoreticalSpeed());
        float impact = isController() && running && rpm != 0 ? targetStress() / rpm : 0;
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

    // =================================================================================================================
    // Production (the controller, server side)
    // =================================================================================================================

    @Override
    public void tick() {
        super.tick();
        if (lastKnownPos == null)
            lastKnownPos = worldPosition;
        else if (!lastKnownPos.equals(worldPosition)) {
            removeController(true);
            lastKnownPos = worldPosition;
            return;
        }
        if (updateConnectivity)
            updateConnectivity();
        if (level.isClientSide && isController() && running && valid)
            workParticles();
        if (level.isClientSide || !isController())
            return;

        Stop why = tryStart();
        boolean canRun = why == Stop.NONE;
        if (canRun != running || why != stop) {
            running = canRun;
            stop = why;
            pushStress();
            sendData();
        }
        if (running)
            produce(active);
    }

    /**
     * Gets this tick ready, and says why it cannot run ({@link Stop#NONE}: it can). It needs a valid tower, rotation, a
     * recipe it holds every input of, enough of each input for one tick, the ice and room for the product.
     * <p>
     * Not a pure check: it picks the recipe ({@link #active}, and the one remembered), and takes the ice of one tick
     * out of the reserve as it is needed.
     */
    private Stop tryStart() {
        active = null;
        if (!isValid())
            return Stop.INVALID;
        if (getSpeed() == 0)
            return Stop.NO_ROTATION;
        List<Distillation> all = storage.recipes();
        Distillation r = storage.choose(all);
        if (r == null) {
            // say which input is missing, for the recipe it is heading to
            Distillation towards = storage.heading(all);
            if (towards != null)
                for (Distillation.Input in : towards.inputs)
                    if (storage.taking(in) == null)
                        return missing(in);
            return Stop.NO_RECIPE;
        }
        storage.recipeId = r.id;
        double perTick = gasRate() / 20.0;
        TowerStorage.Held driver = storage.taking(r.inputs.get(0));
        if (driver.amount < (int) Math.ceil(driver.credit + perTick - 1e-9))
            return missing(r.inputs.get(0));
        double liquidPerTick = r.energy ? energyLiquid(perTick, targetStress() / 20.0) : perTick * r.perInput;
        for (int i = 1; i < r.inputs.size(); i++) {
            TowerStorage.Held h = storage.taking(r.inputs.get(i));
            if (h.amount < (int) Math.ceil(h.credit + liquidPerTick * r.inputPerOutput(i)))
                return missing(r.inputs.get(i));
        }
        if (r.ice && !ice.take(liquidPerTick * r.icePerLiquid() - 1e-9))
            return Stop.NO_ICE;
        // another product still in: it waits to be pumped out
        if (storage.output > 0 && storage.outputFluid != null && !storage.outputFluid.isSame(r.result))
            return Stop.OUTPUT_FULL;
        // the inputs' rooms shrink as the product grows (pipes then stop filling): only the product's room counts
        if (capacity() - storage.output < liquidPerTick + 1)
            return Stop.OUTPUT_FULL;
        active = r;
        return Stop.NONE;
    }

    /** One tick of production with this recipe ({@link #tryStart} checked there is enough of everything). */
    private void produce(Distillation r) {
        // the first input: whole mB only, the fraction waits in the credit
        TowerStorage.Held driver = storage.taking(r.inputs.get(0));
        driver.credit += gasRate() / 20.0;
        int taken = (int) driver.credit;
        driver.credit -= taken;
        driver.amount -= taken;
        double liquid;
        if (r.energy) {
            // the stress the network really sees from us this tick
            double stressEnergy = lastStressApplied * Math.abs(getTheoreticalSpeed()) / 20.0;
            energyIn += stressEnergy;
            liquid = energyLiquid(taken, stressEnergy);
        } else
            liquid = taken * r.perInput; // no energy kept: the stress turns nothing into it

        if (storage.outputFluid == null || !storage.outputFluid.isSame(r.result)) {
            storage.outputFluid = r.result;
            storage.outputRemainder = 0;
        }
        storage.outputRemainder += liquid;
        int made = (int) storage.outputRemainder;
        storage.output += made;
        storage.outputRemainder -= made;
        // the other inputs, as the product is made (whole mB, the rest owed)
        for (int i = 1; i < r.inputs.size(); i++) {
            TowerStorage.Held h = storage.taking(r.inputs.get(i));
            if (h == null)
                continue;
            h.credit += liquid * r.inputPerOutput(i);
            int used = Math.min((int) h.credit, h.amount);
            h.amount -= used;
            h.credit -= used;
        }
        ice.use(liquid * r.icePerLiquid()); // the ice holds no energy, as the water

        setChanged();
        if (level.getGameTime() % 10 == 0)
            sendData();
    }

    /** What the goggles say when this input of the recipe is missing. */
    private static Stop missing(Distillation.Input in) {
        if (in.takes(FwFluids.gas()))
            return Stop.NO_GAS;
        return in.takes(Fluids.WATER) ? Stop.NO_WATER : Stop.NO_INPUT;
    }

    /**
     * The product an energy recipe makes from this gas and this stress energy (same time unit):
     * {@code towerGasPerLiquid} mB of gas per mB, never more than the energy allows (kLiquid x (energy of the gas +
     * stress)). With the defaults the gas ratio is the lower: one packed ice, 2000 mB of Frosium, takes 3500 mB of gas.
     */
    public static double energyLiquid(double gas, double stressEnergy) {
        return Math.min(Energy.kLiquid() * (Energy.ofGas(gas) + stressEnergy),
                gas / FwConfig.get(FwConfig.TOWER_GAS_PER_LIQUID));
    }

    /** Client, while it runs: cold mist on the sides of the column, slowly rising, and a few flakes from the top. */
    private void workParticles() {
        var r = level.random;
        double h = height - 1; // the column, above the bases
        if (h <= 0 || r.nextInt(3) != 0)
            return;
        double along = r.nextDouble() * 2, up = 1 + r.nextDouble() * h;
        double x, z;
        switch (r.nextInt(4)) {
            case 0 -> { x = along; z = -0.05; }
            case 1 -> { x = along; z = 2.05; }
            case 2 -> { x = -0.05; z = along; }
            default -> { x = 2.05; z = along; }
        }
        BlockPos p = worldPosition;
        level.addParticle(ParticleTypes.CLOUD, p.getX() + x, p.getY() + up, p.getZ() + z, 0, 0.01, 0);
        if (r.nextInt(4) == 0)
            level.addParticle(ParticleTypes.SNOWFLAKE, p.getX() + r.nextDouble() * 2, p.getY() + height + 0.1,
                    p.getZ() + r.nextDouble() * 2, 0, 0.04, 0);
    }

    // =================================================================================================================
    // Energy: only the controller holds anything
    // =================================================================================================================

    @Override
    public double energyIn() {
        return isController() ? energyIn : 0;
    }

    @Override
    public double energyOut() {
        return 0;
    }

    /** Gas held and a cold liquid made count as stock; water holds no energy. */
    @Override
    public double energyStock() {
        if (!isController())
            return 0;
        double stock = Energy.ofGas(storage.amountOf(FwFluids.gas()));
        if (storage.outputFluid != null && FwFluids.isLiquid(new FluidStack(storage.outputFluid, 1)))
            stock += Energy.ofLiquid(storage.output + storage.outputRemainder);
        return stock;
    }

    // =================================================================================================================
    // Read by the renderer, the goggles, JEI, Ponder and the tests (any block of the tower: its controller's)
    // =================================================================================================================

    public boolean isRunning() {
        TowerBlockEntity c = getControllerBE();
        return c != null && c.running;
    }

    public Stop stopReason() {
        TowerBlockEntity c = getControllerBE();
        return c == null ? Stop.INVALID : c.stop;
    }

    public int gas() {
        return getControllerBE().storage.amountOf(FwFluids.gas());
    }

    public int water() {
        return getControllerBE().storage.amountOf(Fluids.WATER);
    }

    /** Frosium made and not pumped out yet. */
    public int frosium() {
        TowerStorage s = getControllerBE().storage;
        return s.outputFluid != null && s.outputFluid.isSame(FwFluids.frosium()) ? s.output : 0;
    }

    /** The inputs held, in the order they came. */
    public List<Content> contents() {
        List<Content> list = new ArrayList<>();
        for (TowerStorage.Held h : getControllerBE().storage.inputs)
            if (h.amount > 0)
                list.add(new Content(h.fluid, h.amount));
        return list;
    }

    /** The product held (null if none). */
    @Nullable
    public Content product() {
        TowerStorage s = getControllerBE().storage;
        return s.outputFluid != null && s.output > 0 ? new Content(s.outputFluid, s.output) : null;
    }

    /** The recipe it makes or made last (null if none known). */
    @Nullable
    public Distillation recipe() {
        TowerStorage s = getControllerBE().storage;
        return s.current(s.recipes());
    }

    /** Product made per second at full run (mB/s), for the goggles. */
    public double liquidRate() {
        Distillation r = recipe();
        if (r != null && !r.energy)
            return gasRate() * r.perInput;
        return energyLiquid(gasRate(), targetStress());
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        TowerBlockEntity c = getControllerBE();
        if (c == null)
            return false;
        TowerGoggles.add(c, tooltip);
        return true;
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return isController() ? super.createRenderBoundingBox().expandTowards(width - 1, height - 1, width - 1)
                : super.createRenderBoundingBox();
    }

    // ---- Tests and montages ----

    /** Fills the tower's room with Frosium (a full output stops the tower). */
    public void fillOutputForTest() {
        setLiquidForTest(getControllerBE().capacity());
    }

    /** Frosium in the tower. */
    public void setLiquidForTest(int amount) {
        TowerBlockEntity c = getControllerBE();
        c.storage.outputFluid = FwFluids.frosium();
        c.storage.output = amount;
        c.setChanged();
    }

    /** The reserve full of blue ice, on this block whatever its tower. */
    public void stockIceForRig() {
        ice.fillWithBlueIce();
        setChanged();
    }

    // =================================================================================================================
    // NBT (the parts write their size and link; the controller its content)
    // =================================================================================================================

    @Override
    protected void writeFw(CompoundTag tag, @Nullable HolderLookup.Provider registries, boolean clientPacket) {
        if (updateConnectivity)
            tag.putBoolean("Uninitialized", true);
        if (lastKnownPos != null)
            tag.put("LastKnownPos", NbtUtils.writeBlockPos(lastKnownPos));
        if (!isController())
            tag.put("Controller", NbtUtils.writeBlockPos(controller));
        if (isController()) {
            tag.putInt("Size", width);
            tag.putInt("Height", height);
            tag.putBoolean("Valid", valid);
            storage.write(tag);
            ice.write(tag, registries);
            tag.putDouble("EnergyIn", energyIn);
            tag.putBoolean("Running", running);
            tag.putString("Stop", stop.name());
        }
    }

    @Override
    protected void readFw(CompoundTag tag, @Nullable HolderLookup.Provider registries, boolean clientPacket) {
        BlockPos before = controller;
        int widthBefore = width, heightBefore = height;
        updateConnectivity = tag.contains("Uninitialized");
        lastKnownPos = tag.contains("LastKnownPos") ? Mc.readBlockPos(tag, "LastKnownPos") : null;
        controller = tag.contains("Controller") ? Mc.readBlockPos(tag, "Controller") : null;
        if (isController()) {
            width = Math.max(1, tag.getInt("Size"));
            height = Math.max(1, tag.getInt("Height"));
            valid = tag.getBoolean("Valid");
            storage.read(tag);
            ice.read(tag, registries);
            energyIn = tag.getDouble("EnergyIn");
            running = tag.getBoolean("Running");
            try {
                stop = Stop.valueOf(tag.getString("Stop"));
            } catch (IllegalArgumentException e) {
                stop = Stop.INVALID;
            }
        }
        if (clientPacket && hasLevel() && (!Objects.equals(before, controller) || widthBefore != width
                || heightBefore != height)) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 16); // connected textures
            invalidateRenderBoundingBox();
        }
    }
}
