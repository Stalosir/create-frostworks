package com.frostworks.content.door;

import java.util.List;

import com.frostworks.FwConfig;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Lower half of the strip curtain door. Same mechanism as Create's funnel flaps ({@code FunnelBlockEntity}):
 * <ul>
 * <li>a {@link LerpedFloat} set to +1 or -1 when something passes, then chased back to 0 ({@code Chaser.EXP});</li>
 * <li>the angle of each of the four strips comes from Create's {@code FlapStuffs.flapAngle} (one intensity per strip:
 * the curtain effect), drawn by {@code StripDoorRenderer};</li>
 * <li>the server detects (the block's {@code entityInside}) and tells the clients. Create sends its own packet to the
 * clients tracking the chunk; here a vanilla block event does the same with no packet to register.</li>
 * </ul>
 * Cost when nothing passes: no server work at all (entityInside is only called for entities inside the block), and on
 * the client one multiplication per tick while the strips settle.
 */
public class StripDoorBlockEntity extends SmartBlockEntity {

    public static final int EVENT_SWING = 1;
    /**
     * Length of one swing on the client, in ticks: the flap value chases 0 by 12 % a tick (Chaser.EXP .12), so it is
     * under 2 % of its start after 30 ticks. The server sends no new swing before that.
     */
    private static final int SWING_TICKS = 30;

    /** -1..1, sign = direction of travel along the door's FACING; decays to 0 (client). */
    public final LerpedFloat flap = LerpedFloat.linear().startWithValue(0).chase(0, .12f, Chaser.EXP);
    /**
     * Base swing of the current push, in degrees (45 to 60, from the speed) (client). The renderer scales it by
     * {@code flapSwingFactor} (client config, 0.1 by default: 4.5 to 6 degrees).
     */
    private float amplitude = 45;
    private long lastSent = Long.MIN_VALUE / 2;

    public StripDoorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(InsulatedDoorBlock.FACING) ? state.getValue(InsulatedDoorBlock.FACING) : Direction.NORTH;
    }

    // ---- Server: detection ----------------------------------------------------------------------------------------

    /**
     * The strips swing only for an entity that really moves. Before, any entity inside the door sent
     * a swing every 8 ticks, and one standing still was even pushed "away from the side it came from": the strips
     * beat for ever around a player standing in the door.
     * <ul>
     * <li>speed = real displacement since the previous tick (position - xOld/yOld/zOld), not the motion vector, which
     * is unreliable on the server for a walking player;</li>
     * <li>moving above {@code flapMinSpeed}, still again below half of it (hysteresis: an entity hovering around the
     * threshold does not trigger in a loop);</li>
     * <li>no new swing until the previous one is over ({@link #SWING_TICKS}).</li>
     * </ul>
     */
    public void onEntityInside(Entity entity) {
        long now = level.getGameTime();
        Vec3 move = new Vec3(entity.getX() - entity.xOld, entity.getY() - entity.yOld, entity.getZ() - entity.zOld);
        double speed = move.length();
        double min = FwConfig.get(FwConfig.FLAP_MIN_SPEED);
        Mover mover = movers.computeIfAbsent(entity.getId(), id -> new Mover());
        mover.lastSeen = now;
        if (speed > min)
            mover.moving = true;
        else if (speed < min / 2)
            mover.moving = false;
        if (!mover.moving || now < swingOverAt)
            return;
        Vec3 normal = Vec3.atLowerCornerOf(facing().getNormal());
        double along = move.dot(normal);
        double strengthSpeed = Math.abs(along);
        int strength = Mth.clamp((int) Math.round((strengthSpeed - 0.08) / 0.2 * 15), 0, 15);
        int param = (along >= 0 ? 1 : 0) | strength << 1;
        lastSent = now;
        swingOverAt = now + SWING_TICKS;
        swingsSent++;
        level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_SWING, param);
    }

    /** Server: whether an entity in the door counts as moving (hysteresis), forgotten once it left. */
    private static final class Mover {
        boolean moving;
        long lastSeen;
    }

    private final Int2ObjectOpenHashMap<Mover> movers = new Int2ObjectOpenHashMap<>();
    /** Server: no new swing before this tick (the strips are still moving). */
    private long swingOverAt = Long.MIN_VALUE / 2;
    private int swingsSent;

    /** For tests: swings sent by the server since the door was placed. */
    public int swingsSent() {
        return swingsSent;
    }

    // ---- Both sides: the event ----------------------------------------------------------------------------------

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id != EVENT_SWING)
            return super.triggerEvent(id, param);
        if (level != null && level.isClientSide) {
            int sign = (param & 1) == 1 ? 1 : -1;
            amplitude = 45 + 15 * ((param >> 1) & 15) / 15f;
            flap.setValue(sign);
        }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide)
            flap.tickChaser();
        else if (!movers.isEmpty() && level.getGameTime() % 20 == 0)
            movers.values().removeIf(m -> level.getGameTime() - m.lastSeen > 20); // entities that left
    }

    /** The strips hang down to the floor and up into the upper block: draw them when either block is visible. */
    @Override
    protected net.minecraft.world.phys.AABB createRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).expandTowards(0, 1, 0);
    }

    public float amplitude() {
        return amplitude;
    }

    /** For tests: the server sent a swing at this game tick (or a very old one). */
    public long lastSwingSent() {
        return lastSent;
    }
}
