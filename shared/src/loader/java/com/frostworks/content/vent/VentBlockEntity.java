package com.frostworks.content.vent;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.frostworks.FwClientConfig;
import com.frostworks.FwConfig;
import com.frostworks.content.cold.ColdFluid;
import com.frostworks.content.cold.ColdFluids;
import com.frostworks.registry.FwFluids;
import com.frostworks.room.Room;
import com.frostworks.room.RoomManager;
import com.frostworks.room.core.RoomScan;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * Vent block entity: a small gas buffer, filled from the back by Create pipes, consumed at a fixed rate while it
 * diffuses. A vent is active if it consumed gas within the last {@code activeWindowTicks}.
 */
public class VentBlockEntity extends com.frostworks.platform.SmartBase {

    /**
     * One fluid at a time (a FluidTank only takes more of what it holds), and one a cold fluid recipe takes. A fluid
     * whose recipe went away (a /reload) stays in the tank, unused, until its recipe comes back.
     */
    protected final FluidTank tank = new FluidTank(250, s -> ColdFluids.of(level, s.getFluid()) != null) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final IFluidHandler input = new InputHandler();
    private float consumptionRemainder;
    private long lastConsumedTick = Long.MIN_VALUE / 2;
    /** Test helper: the vent gets free gas (set by /frostworks testroom and GameTests). */
    private boolean creativeSupply;
    /** Fluid given for free when {@link #creativeSupply} is set (cold gas by default). */
    private Fluid creativeFluid;
    private boolean active;
    /**
     * Active and blowing into a closed room, the same condition as its real effect (an active vent
     * of an OPEN room cools nothing). Synced to the client, where it drives the propeller.
     */
    private boolean blowing;
    /** Client: 0 (still) to 1 (full speed), eased so the propeller starts and stops smoothly. */
    public final LerpedFloat spin = LerpedFloat.linear().startWithValue(0).chase(0, SPIN_EASING, Chaser.EXP);
    /** Client: propeller angle (degrees) at this tick and the previous one, for the frame interpolation. */
    private float angle, prevAngle;
    private static final float SPIN_EASING = .05f;
    /**
     * What it diffuses (the cold fluid recipe of the fluid it holds, or of the last one while it is empty): its
     * consumption, its coverage, the target of its room. Null: nothing it can use.
     */
    @Nullable
    private ColdFluid cold;
    /** The fluid it diffused last (saved: the recipe is looked up again once the world is loaded). */
    @Nullable
    private Fluid lastFluid;
    private boolean registered;

    public VentBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(VentBlock.FACING) ? state.getValue(VentBlock.FACING) : Direction.UP;
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level instanceof ServerLevel server) {
            RoomManager.get(server).ventAdded(worldPosition);
            registered = true;
        }
    }

    /** The chunk unloads: the vent is not gone (its room is kept as it is). */
    private boolean unloading;

    @Override
    public void onChunkUnloaded() {
        unloading = true;
        super.onChunkUnloaded();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (registered && level instanceof ServerLevel server) {
            if (unloading)
                RoomManager.get(server).ventUnloaded(worldPosition);
            else
                RoomManager.get(server).ventRemoved(worldPosition);
            registered = false;
        }
    }

    /**
     * Ponder scenes only: what the client shows (mist, turning propeller), set by the scene. The
     * Ponder world runs no room code, so nothing else would ever set it there.
     */
    public void showInScene(boolean active, boolean blowing) {
        this.active = active;
        this.blowing = blowing;
    }

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide) {
            if (active)
                spawnMist();
            tickPropeller();
            return;
        }

        tank.setCapacity(FwConfig.get(FwConfig.VENT_BUFFER));
        if (creativeSupply)
            tank.fill(new FluidStack(creativeFluid != null ? creativeFluid : FwFluids.gas(), tank.getCapacity()),
                    IFluidHandler.FluidAction.EXECUTE);

        long now = level.getGameTime();
        if (!tank.isEmpty())
            lastFluid = tank.getFluid().getFluid();
        ColdFluid current = lastFluid == null ? null : ColdFluids.of(level, lastFluid);
        if (!java.util.Objects.equals(current, cold)) {
            cold = current;
            consumptionRemainder = 0;
            sendData();
            RoomManager.get((ServerLevel) level).ventActivityChanged(worldPosition);
        }
        float perTick = cold == null ? 0 : cold.amount() / 20f;
        consumptionRemainder += perTick;
        int need = (int) consumptionRemainder;
        if (need > 0) {
            if (tank.getFluidAmount() >= need) {
                tank.drain(need, IFluidHandler.FluidAction.EXECUTE);
                consumptionRemainder -= need;
                lastConsumedTick = now;
            } else {
                // Not enough gas: no debt (a vent that waited for gas must not owe it all at once), the vent goes
                // idle once the window is over. Fixed in prototype 3: the debt used to grow without limit.
                consumptionRemainder = Math.min(consumptionRemainder, Math.max(1, perTick));
            }
        } else if (tank.getFluidAmount() > 0 && perTick > 0) {
            lastConsumedTick = now; // fractional consumption: still diffusing this tick
        }

        boolean nowActive = now - lastConsumedTick <= FwConfig.get(FwConfig.VENT_ACTIVE_TICKS);
        if (nowActive != active) {
            active = nowActive;
            sendData();
            RoomManager.get((ServerLevel) level).ventActivityChanged(worldPosition);
        }

        boolean nowBlowing = active && coolsARoom();
        if (nowBlowing != blowing) {
            blowing = nowBlowing;
            sendData();
        }
    }

    /** Its room (the one in front of it) is closed: its gas really cools something. */
    private boolean coolsARoom() {
        Room room = RoomManager.get((ServerLevel) level).roomOfVent(worldPosition);
        return room != null && room.outcome == RoomScan.Outcome.CLOSED;
    }

    /** Client: eases the speed towards full or still, and turns the propeller ({@code ventPropellerRpm}). */
    private void tickPropeller() {
        spin.chase(blowing ? 1 : 0, SPIN_EASING, Chaser.EXP);
        spin.tickChaser();
        float degreesPerTick = (float) FwClientConfig.ventPropellerRpm() * 360f / 60f / 20f;
        prevAngle = angle;
        angle += degreesPerTick * spin.getValue();
        if (angle >= 360) {
            angle -= 360;
            prevAngle -= 360;
        }
    }

    /** Client: propeller angle in degrees for this frame. */
    public float propellerAngle(float partialTicks) {
        return Mth.lerp(partialTicks, prevAngle, angle);
    }

    public boolean isBlowing() {
        return blowing;
    }

    private void spawnMist() {
        RandomSource r = level.random;
        if (r.nextInt(2) != 0)
            return;
        Direction facing = facing();
        Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 p = Vec3.atCenterOf(worldPosition).add(normal.scale(.6))
                .add((r.nextFloat() - .5f) * .7f * (1 - Math.abs(normal.x)),
                        (r.nextFloat() - .5f) * .7f * (1 - Math.abs(normal.y)),
                        (r.nextFloat() - .5f) * .7f * (1 - Math.abs(normal.z)));
        Vec3 v = normal.scale(.04 + r.nextFloat() * .03);
        level.addParticle(r.nextInt(3) == 0 ? ParticleTypes.CLOUD : ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, v.x, v.y,
                v.z);
    }

    /** Debug text (GameTests). */
    public String debug() {
        return "cold fluid " + (cold == null ? "none" : cold.id() + " " + cold.amount() + " mB/s") + " last " + lastConsumedTick + " now "
                + (level == null ? -1 : level.getGameTime()) + " remainder " + consumptionRemainder;
    }

    public boolean isActive() {
        return active;
    }

    public FluidStack gas() {
        return tank.getFluid();
    }

    public int bufferCapacity() {
        return tank.getCapacity();
    }

    /** Volume this vent covers while active, for the fluid it diffuses (cells; 0 for a fluid it cannot use). */
    public int coverage() {
        return cold == null ? 0 : cold.coverage();
    }

    /** The translation key of the fluid it holds, or held last (the goggles); empty if none. */
    public String fluidKey() {
        Fluid fluid = tank.isEmpty() ? lastFluid : tank.getFluid().getFluid();
        return fluid == null ? "" : fluid.getFluidType().getDescriptionId();
    }

    /** The cold fluid recipe of what it diffuses, or null. */
    @Nullable
    public ColdFluid coldFluid() {
        return cold;
    }

    /** Free supply of a given fluid (tests): cold gas or Frosium. */
    public void setCreativeSupply(Fluid fluid) {
        this.creativeFluid = fluid;
        this.creativeSupply = true;
        setChanged();
    }

    public void setCreativeSupply(boolean creativeSupply) {
        this.creativeSupply = creativeSupply;
        setChanged();
    }

    public boolean hasCreativeSupply() {
        return creativeSupply;
    }

    @Nullable
    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        return side == null || side == facing().getOpposite() ? input : null;
    }

    /** Fill-only view of the buffer, exposed on the back face. */
    private class InputHandler implements IFluidHandler {
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
            return tank.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return tank.fill(resource, action);
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

    @Override
    protected void writeFw(CompoundTag tag, @Nullable HolderLookup.Provider registries, boolean clientPacket) {
        tag.put("Gas", com.frostworks.platform.Loader.writeTank(tank, registries, new CompoundTag()));
        if (creativeSupply)
            tag.putBoolean("CreativeSupply", true);
        if (creativeFluid != null)
            tag.putString("CreativeFluid", BuiltInRegistries.FLUID.getKey(creativeFluid).toString());
        if (lastFluid != null)
            tag.putString("LastFluid", BuiltInRegistries.FLUID.getKey(lastFluid).toString());
        if (clientPacket) {
            tag.putBoolean("Active", active);
            tag.putBoolean("Blowing", blowing);
        }
    }

    @Override
    protected void readFw(CompoundTag tag, @Nullable HolderLookup.Provider registries, boolean clientPacket) {
        com.frostworks.platform.Loader.readTank(tank, registries, tag.getCompound("Gas"));
        creativeSupply = tag.getBoolean("CreativeSupply");
        creativeFluid = tag.contains("CreativeFluid")
                ? BuiltInRegistries.FLUID.get(com.frostworks.platform.Mc.parse(tag.getString("CreativeFluid"))) : null;
        lastFluid = tag.contains("LastFluid")
                ? BuiltInRegistries.FLUID.get(com.frostworks.platform.Mc.parse(tag.getString("LastFluid"))) : null;
        if (clientPacket) {
            active = tag.getBoolean("Active");
            blowing = tag.getBoolean("Blowing");
        }
    }
}
