package com.frostworks.overspeed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.frostworks.Frostworks;
import com.frostworks.CreateHooks;
import com.frostworks.FwConfig;
import com.frostworks.content.passage.BeltPassage;
import com.frostworks.content.passage.ShaftPassageBlock;
import com.frostworks.room.Room;
import com.frostworks.room.RoomManager;
import com.frostworks.room.core.RoomStatus;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.infrastructure.config.AllConfigs;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * in a COLD room, every kinetic block (machines and the transmission blocks that feed them) may turn faster than
 * Create's {@code maxRotationSpeed}, by {@code overspeedBonusPercent} of it.
 * <p>
 * Create breaks the transmitting block in {@code RotationPropagator.propagateNewSource} when a speed conveyed between two
 * blocks goes over the limit ({@code tooFast}). {@code RotationPropagatorMixin} asks {@link #allow} before that.
 * <p>
 * Withdrawal: every block that got the right is remembered with the last tick it was in a COLD room. Once the room has
 * not been COLD for {@code overspeedGraceSeconds}, the block is propagated again through Create, which breaks it like
 * it would without this mod (the speed comes from a gear ratio: it cannot be lowered without changing the build).
 */
public final class Overspeed {
    private Overspeed() {}

    public static final TagKey<Block> MACHINES = TagKey.create(Registries.BLOCK, Frostworks.id("overspeed_machines"));
    public static final TagKey<Block> TRANSMISSION =
            TagKey.create(Registries.BLOCK, Frostworks.id("overspeed_transmission"));

    /** Blocks that got the right to overspeed: position -> last game tick they were in a COLD room. */
    private static final Map<ServerLevel, Long2LongOpenHashMap> GRANTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static Long2LongOpenHashMap grants(ServerLevel level) {
        return GRANTS.computeIfAbsent(level, l -> new Long2LongOpenHashMap());
    }

    public static int createLimit() {
        return AllConfigs.server().kinetics.maxRotationSpeed.get();
    }

    /**
     * Every kinetic block (Create's {@code IRotate}: all the machines, mixer included, and all the
     * transmissions, Create's and ours) turns at the network's speed up to the room's limit; the two tags add blocks
     * that are not kinetic blocks.
     */
    public static boolean isCandidate(BlockState state) {
        return state.getBlock() instanceof IRotate || state.is(MACHINES) || state.is(TRANSMISSION);
    }

    /**
     * Is this block in a COLD room right now? A shaft passage is part of the wall, never a room cell;
     * it counts as in the room as soon as one of its two open faces (along its axis) opens on a COLD room.
     */
    public static boolean inColdRoom(ServerLevel level, BlockPos pos) {
        List<BlockPos> joints = beltJoints(level, pos);
        if (joints == null)
            return cellsCold(level, pos);
        // A belt is cold when its ends and pulleys are (the rest may go out between rooms)
        for (BlockPos joint : joints)
            if (!cellsCold(level, joint))
                return false;
        return true;
    }

    private static boolean cellsCold(ServerLevel level, BlockPos pos) {
        RoomManager rooms = RoomManager.get(level);
        for (BlockPos cell : roomCells(level, pos))
            if (isCold(rooms.roomAt(cell)))
                return true;
        return false;
    }

    /**
     * The segments of the belt this segment is part of that turn on a shaft (its two ends, and the pulleys put on it),
     * or null if it is no belt.
     */
    private static List<BlockPos> beltJoints(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof BeltBlockEntity segment))
            return null;
        BeltBlockEntity controller = segment.getControllerBE();
        if (controller == null || controller.beltLength <= 0)
            return null;
        List<BlockPos> joints = new ArrayList<>();
        for (int i = 0; i < controller.beltLength; i++) {
            BlockPos at = BeltHelper.getPositionForOffset(controller, i);
            BlockState state = level.getBlockState(at);
            if (i == 0 || i == controller.beltLength - 1
                    || state.getBlock() instanceof BeltBlock && state.getValue(BeltBlock.PART) != BeltPart.MIDDLE)
                joints.add(at);
        }
        return joints;
    }

    /**
     * The cells whose room a block belongs to: itself; for a passage (shaft or belt) the cells around it and the two it
     * opens on along its axis or its belt.
     */
    private static List<BlockPos> roomCells(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ShaftPassageBlock) {
            // Like a belt passage, the rooms all around it too, and along its axis
            // past the passages next to it (a shaft through a thick wall, passages in a row as decoration)
            Direction.Axis axis = state.getValue(RotatedPillarKineticBlock.AXIS);
            List<BlockPos> cells = new ArrayList<>(8);
            for (Direction d : Direction.values())
                cells.add(pos.relative(d));
            for (int step : new int[]{1, -1}) {
                BlockPos at = pos.relative(axis, step);
                for (int n = 0; n < 64 && level.getBlockState(at).getBlock() instanceof ShaftPassageBlock; n++)
                    at = at.relative(axis, step);
                cells.add(at);
            }
            return cells;
        }
        if (BeltPassage.is(level, pos, state)) {
            // A belt passage, like the shaft passage, belongs to the rooms on its two belt sides.
            // And to the rooms all around it, for a belt encased as decoration inside a room (its
            // ends against other encased belts, or a wall): the room's air above it is its room.
            List<BlockPos> cells = new ArrayList<>(8);
            for (Direction d : Direction.values())
                cells.add(pos.relative(d));
            List<BlockPos> sides = beltPassageSides(level, pos);
            if (sides != null)
                cells.addAll(sides);
            return cells;
        }
        return List.of(pos);
    }

    /**
     * The two cells a belt passage opens on, followed along the belt: past the passages next to it (a
     * belt encased over several segments in a row), the first segment that is no passage, or the cell past the belt's
     * end. Slopes included. Null while the belt is not formed.
     */
    private static List<BlockPos> beltPassageSides(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof BeltBlockEntity segment))
            return null;
        BeltBlockEntity controller = segment.getControllerBE();
        if (controller == null || controller.beltLength <= 0)
            return null;
        List<BlockPos> sides = new ArrayList<>(2);
        for (int step : new int[]{-1, 1}) {
            int i = segment.index + step;
            BlockPos at = BeltHelper.getPositionForOffset(controller, i);
            while (i >= 0 && i < controller.beltLength && BeltPassage.is(level, at, level.getBlockState(at))) {
                i += step;
                at = BeltHelper.getPositionForOffset(controller, i);
            }
            sides.add(at);
        }
        return sides;
    }

    private static boolean isCold(Room room) {
        return room != null && room.status() == RoomStatus.COLD;
    }

    /** No room known yet for this block (its room is being rebuilt, or it has none). */
    private static boolean noRoom(ServerLevel level, BlockPos pos) {
        RoomManager rooms = RoomManager.get(level);
        for (BlockPos cell : roomCells(level, pos))
            if (rooms.roomAt(cell) != null)
                return false;
        return true;
    }

    /**
     * Highest speed in a COLD room: Create's limit plus {@code overspeedBonusPercent}, or plus
     * {@code overspeedBonusPercentFrosium} from {@link #DEEP_COLD} (by the room's target, its cold fluids).
     */
    public static int maxRpm(Room room) {
        return coldLimit(room != null && room.status() == com.frostworks.room.core.RoomStatus.COLD
                ? room.coldTarget() : com.frostworks.room.core.RoomRecords.DEFAULT_COLD_TARGET);
    }

    /**
     * The fastest speed a room allows, as the goggles and the Display Link show it: the cold's in a COLD room (overspeed
     * on), never below Create's own limit (set above our 320 or 384, Create's limit is what holds, in the cold too).
     */
    public static int allowedRpm(Room room) {
        return room.status() == com.frostworks.room.core.RoomStatus.COLD && enabled()
                ? Math.max(maxRpm(room), createLimit()) : createLimit();
    }

    /** The bonus steps by temperature: from -20 deg C (Frosium), the deeper one. */
    public static final int DEEP_COLD = -20;

    /** Create's limit raised by the cold bonus of a room at this target (deg C), the deeper one from {@link #DEEP_COLD}. */
    public static int coldLimit(int coldTarget) {
        int bonus = FwConfig.get(coldTarget <= DEEP_COLD ? FwConfig.OVERSPEED_BONUS_FROSIUM : FwConfig.OVERSPEED_BONUS);
        return Math.round(createLimit() * (100 + bonus) / 100f);
    }

    /**
     * The highest speed this block may reach, from the coldest of its COLD rooms; in its grace period
     * (no COLD room any more), the ordinary limit.
     */
    public static int maxRpm(ServerLevel level, BlockPos pos) {
        List<BlockPos> joints = beltJoints(level, pos);
        if (joints == null)
            return cellsMaxRpm(level, pos);
        int max = Integer.MAX_VALUE; // the least cold of its ends and pulleys
        for (BlockPos joint : joints)
            max = Math.min(max, cellsMaxRpm(level, joint));
        return max;
    }

    private static int cellsMaxRpm(ServerLevel level, BlockPos pos) {
        RoomManager rooms = RoomManager.get(level);
        int best = coldLimit(com.frostworks.room.core.RoomRecords.DEFAULT_COLD_TARGET);
        for (BlockPos cell : roomCells(level, pos)) {
            Room room = rooms.roomAt(cell);
            if (isCold(room))
                best = Math.max(best, maxRpm(room));
        }
        return best;
    }

    /** May this block overspeed right now (COLD room, or still in its grace period)? Updates its grant. */
    public static boolean permitted(ServerLevel level, BlockPos pos, BlockState state) {
        if (!enabled() || !isCandidate(state))
            return false;
        long now = level.getGameTime();
        Long2LongOpenHashMap grants = grants(level);
        if (inColdRoom(level, pos)) {
            if (grants.isEmpty())
                logFirstGrant(level, pos);
            grants.put(pos.asLong(), now);
            limits(level).put(pos.asLong(), maxRpm(level, pos));
            return true;
        }
        long key = pos.asLong();
        return grants.containsKey(key) && now - grants.get(key) <= graceTicks();
    }

    /**
     * The limit each granted block had when its right was last refreshed. In its grace period it may
     * still turn up to it, even when its room's limit fell in between (Frosium gone, still cold on gas).
     */
    private static final Map<ServerLevel, Long2IntOpenHashMap> LIMITS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static Long2IntOpenHashMap limits(ServerLevel level) {
        return LIMITS.computeIfAbsent(level, l -> new Long2IntOpenHashMap());
    }

    /** In its grace period, and no faster than the limit it had then. */
    private static boolean inGrace(ServerLevel level, BlockPos pos, float fastest) {
        long key = pos.asLong();
        Long2LongOpenHashMap grants = grants(level);
        return grants.containsKey(key) && level.getGameTime() - grants.get(key) <= graceTicks()
                && fastest <= limits(level).getOrDefault(key, coldLimit(com.frostworks.room.core.RoomRecords.DEFAULT_COLD_TARGET));
    }

    /**
     * May this block turn at {@code fastest} right now: in a COLD room up to its limit (its right is granted or
     * refreshed), or in its grace period (up to the limit it had, even over its room's lower limit).
     */
    private static boolean mayTurn(ServerLevel level, KineticBlockEntity be, float fastest) {
        BlockPos pos = be.getBlockPos();
        if (fastest <= maxRpm(level, pos) && permitted(level, pos, be.getBlockState()))
            return true;
        return enabled() && isCandidate(be.getBlockState()) && inGrace(level, pos, fastest);
    }

    /**
     * A block joining a network whose room's limit just fell (a shaft placed during the grace period):
     * in the same cold, it takes the grace period of the block it joins, so that it does not break before the rest.
     */
    private static boolean joinsGrace(ServerLevel level, KineticBlockEntity joining, KineticBlockEntity granted,
                                      float fastest) {
        BlockPos pos = joining.getBlockPos(), from = granted.getBlockPos();
        Long2LongOpenHashMap grants = grants(level);
        if (!enabled() || !isCandidate(joining.getBlockState()) || grants.containsKey(pos.asLong())
                || fastest <= maxRpm(level, from) || !inGrace(level, from, fastest) || !inColdRoom(level, pos))
            return false;
        grants.put(pos.asLong(), grants.get(from.asLong()));
        limits(level).put(pos.asLong(), limits(level).get(from.asLong()));
        return true;
    }

    /** Once per level and run, which room gave the first right (restored from the save or searched). */
    private static void logFirstGrant(ServerLevel level, BlockPos pos) {
        RoomManager rooms = RoomManager.get(level);
        for (BlockPos cell : roomCells(level, pos)) {
            Room room = rooms.roomAt(cell);
            if (room != null)
                Frostworks.LOGGER.info("Overspeed: first right of {} at {} (game tick {}), room #{} {} at {} deg C, {}",
                        level.dimension().location(), pos.toShortString(), level.getGameTime(), room.id, room.status(),
                        String.format(java.util.Locale.ROOT, "%.1f", room.temperature()),
                        room.isRestored() ? "restored from the save" : "searched");
        }
    }

    private static long graceTicks() {
        return FwConfig.get(FwConfig.OVERSPEED_GRACE_SECONDS) * 20L;
    }

    /**
     * The overspeed is on (the setting) and its hook is in Create's code ({@link CreateHooks}). Off,
     * nothing of the mod lets a machine past Create's limit, and the goggles say why. The common setting {@code
     * coldSpeedBonus} too (AUTO: off in TerraFirmaGreg); the cold's lower stress ({@link ColdStress}) does not depend on it.
     */
    public static boolean enabled() {
        return FwConfig.get(FwConfig.OVERSPEED_ENABLED) && com.frostworks.FwCommonConfig.coldSpeedBonus()
                && CreateHooks.ready(CreateHooks.Hook.OVERSPEED);
    }

    /**
     * Called by the mixin: the limit Create compares the pair's speeds with. Under Create's own
     * limit, nothing to decide; over it, no limit for this pair if it may overspeed ({@link #allow}), Create's otherwise.
     */
    public static Object limitFor(Object createLimit, KineticBlockEntity current, KineticBlockEntity neighbour,
                                  float newSpeed, float oppositeSpeed) {
        if (!(createLimit instanceof Integer limit) || Math.max(Math.abs(newSpeed), Math.abs(oppositeSpeed)) <= limit)
            return createLimit;
        return allow(current, neighbour, newSpeed, oppositeSpeed) ? Integer.valueOf(Integer.MAX_VALUE) : createLimit;
    }

    /**
     * Called by the mixin when Create found a conveyed speed over its limit between {@code current} and
     * {@code neighbour}.
     *
     * @return true to let the speed through (the block does not break)
     */
    public static boolean allow(KineticBlockEntity current, KineticBlockEntity neighbour, float newSpeed,
                                float oppositeSpeed) {
        if (!(current.getLevel() instanceof ServerLevel level))
            return false;
        float fastest = Math.max(Math.abs(newSpeed), Math.abs(oppositeSpeed));
        boolean currentOk = mayTurn(level, current, fastest);
        boolean neighbourOk = mayTurn(level, neighbour, fastest);
        if (currentOk && neighbourOk
                || currentOk && joinsGrace(level, neighbour, current, fastest)
                || neighbourOk && joinsGrace(level, current, neighbour, fastest))
            return true;
        // A belt with an end out of the cold, driven from the cold: that end breaks (next tick, taking
        // the whole belt with it, as Create does), not what drives it
        for (KineticBlockEntity be : new KineticBlockEntity[]{current, neighbour}) {
            BlockPos out = outsideBeltEnd(level, be.getBlockPos());
            if (out != null && (be == current ? neighbourOk : currentOk)) {
                BELTS_TO_BREAK.computeIfAbsent(level, l -> new LongOpenHashSet()).add(out.asLong());
                return true;
            }
        }
        return false;
    }

    /** The belts to break next tick, by level; wrapped and emptied with the server as the grants and the limits. */
    private static final Map<ServerLevel, LongOpenHashSet> BELTS_TO_BREAK =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * A shaft put on a fast belt where it is out of the cold. The segments joining it again still hold
     * their right (grace period), so the speed check lets it through: the belt breaks at that pulley, next tick.
     */
    public static void onBlockChanged(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof BeltBlock && state.getValue(BeltBlock.PART) == BeltPart.PULLEY
                && level.getBlockEntity(pos) instanceof BeltBlockEntity be && be.getControllerBE() != null
                // the controller: the pulley itself was just taken off its network, it turns at 0 until it joins again
                && Math.abs(be.getControllerBE().getTheoreticalSpeed()) > createLimit() && !cellsCold(level, pos))
            BELTS_TO_BREAK.computeIfAbsent(level, l -> new LongOpenHashSet()).add(pos.asLong());
    }

    /** The end or pulley of this belt that is out of the cold, or null (no belt, or all of them cold). */
    private static BlockPos outsideBeltEnd(ServerLevel level, BlockPos pos) {
        List<BlockPos> joints = beltJoints(level, pos);
        if (joints != null)
            for (BlockPos joint : joints)
                if (!cellsCold(level, joint))
                    return joint;
        return null;
    }

    /** Every second: refreshes grants, and withdraws the right once the grace period is over. */
    public static void tick(ServerLevel level) {
        ColdStress.flush(); // The goggles of the networks the cold changed
        LongOpenHashSet breaks = BELTS_TO_BREAK.remove(level);
        if (breaks != null)
            for (long key : breaks)
                if (level.getBlockState(BlockPos.of(key)).getBlock() instanceof BeltBlock)
                    level.destroyBlock(BlockPos.of(key), true);
        if (level.getGameTime() % 20 != 0)
            return;
        Long2LongOpenHashMap grants = GRANTS.get(level);
        if (grants == null || grants.isEmpty())
            return;
        int limit = createLimit();
        long now = level.getGameTime();
        RoomManager rooms = RoomManager.get(level);
        java.util.List<KineticBlockEntity> withdraw = new java.util.ArrayList<>();
        for (ObjectIterator<Long2LongMap.Entry> it = grants.long2LongEntrySet().fastIterator(); it.hasNext(); ) {
            Long2LongMap.Entry e = it.next();
            BlockPos pos = BlockPos.of(e.getLongKey());
            if (!level.isLoaded(pos)) {
                it.remove();
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof KineticBlockEntity kbe) || Math.abs(kbe.getTheoreticalSpeed()) <= limit) {
                it.remove(); // gone, or back under Create's limit: nothing to watch
                continue;
            }
            boolean cold = inColdRoom(level, pos);
            if (cold && Math.abs(kbe.getTheoreticalSpeed()) <= maxRpm(level, pos)) {
                e.setValue(now);
                limits(level).put(e.getLongKey(), maxRpm(level, pos));
                continue;
            }
            // Still cold, but over the room's limit (it fell: Frosium gone, still cold on gas): the
            // grace period, as for a loss of cold (it used to break at once).
            // Only when this block has no room right now while a search is running (its room is being rebuilt) is
            // its state unknown: wait. (the check used to wait whenever any room of the level was
            // being searched, which delayed the withdrawal by up to several seconds.)
            if (!cold && noRoom(level, pos) && rooms.isBusy())
                continue;
            if (now - e.getLongValue() > graceTicks()) {
                it.remove();
                withdraw.add(kbe);
            }
        }
        // Let Create propagate again from these blocks: without the right, its own tooFast check breaks them.
        // Break like Create would: propagate again from the block that feeds the first overspeeding block (its source
        // turns under the limit). Create's tooFast check then breaks that source, exactly as in the witness rig, and every
        // block after it loses its source and stops. Leaves (a saw at the end) must not be broken one by one.
        for (KineticBlockEntity kbe : withdraw) {
            if (kbe.isRemoved() || Math.abs(kbe.getTheoreticalSpeed()) <= limit)
                continue; // already stopped by an earlier break
            KineticBlockEntity feeder = kbe.hasSource() && level.getBlockEntity(kbe.source) instanceof KineticBlockEntity s
                    && Math.abs(s.getTheoreticalSpeed()) <= limit ? s : null;
            if (feeder != null)
                feeder.attachKinetics();
        }
        // Fallback: no feeder found under the limit (should not happen), let Create check the blocks themselves
        for (KineticBlockEntity kbe : withdraw)
            if (!kbe.isRemoved() && Math.abs(kbe.getTheoreticalSpeed()) > limit)
                kbe.attachKinetics();
    }

    /** State shown by /frostworks overspeed status. */
    public record Status(boolean candidate, boolean coldRoom, boolean granted, long ticksSinceCold, int effectiveLimit) {}

    public static Status status(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        boolean candidate = isCandidate(state) && enabled();
        boolean cold = inColdRoom(level, pos);
        Long2LongOpenHashMap grants = grants(level);
        long key = pos.asLong();
        boolean granted = grants.containsKey(key);
        long since = granted ? level.getGameTime() - grants.get(key) : -1;
        boolean allowed = candidate && (cold || granted && since <= graceTicks());
        return new Status(candidate, cold, granted, since,
                allowed ? maxRpm(level, pos) : createLimit());
    }

    public static void clear() {
        GRANTS.clear();
        LIMITS.clear();
        BELTS_TO_BREAK.clear();
    }
}
