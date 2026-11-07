package com.frostworks.room;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.jetbrains.annotations.Nullable;

import com.frostworks.Frostworks;
import com.frostworks.FwConfig;
import com.frostworks.content.vent.VentBlock;
import com.frostworks.content.vent.VentBlockEntity;
import com.frostworks.room.core.PosKey;
import com.frostworks.room.core.RoomRecords;
import com.frostworks.room.core.RoomIdentities;
import com.frostworks.room.core.RoomScan;
import com.frostworks.room.core.RoomStatus;
import com.frostworks.room.core.Temperature;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rooms of one server level.
 * <ul>
 * <li>Rooms are found from the vents: a room is the flood fill (see {@link RoomScan}) started at the cell in front of
 * a vent. Vents register themselves when their block entity loads.</li>
 * <li>Results are cached: cell -> room. A block update next to a room cell (wall broken or placed, NeoForge
 * {@code NeighborNotifyEvent}) marks the room dirty; after {@code rescanDelayTicks} the room is dismantled and its
 * vents searched again.</li>
 * <li>Cost control: at most {@code maxRescansPerTick} searches started and {@code maxCellsPerTick} cells visited per
 * tick; a longer search goes on next tick.</li>
 * <li>Unloaded chunks: a search that touches one stops (PENDING) and starts again when a chunk loads; a room whose
 * chunk unloads is searched again.</li>
 * </ul>
 */
public final class RoomManager {
    private static final Map<ServerLevel, RoomManager> MANAGERS = Collections.synchronizedMap(new WeakHashMap<>());

    public static RoomManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level, RoomManager::new);
    }

    public static void forget(ServerLevel level) {
        MANAGERS.remove(level);
    }

    public static void clearAll() {
        MANAGERS.clear();
    }

    private final ServerLevel level;
    private final LevelRoomWorld world;

    private final LongOpenHashSet vents = new LongOpenHashSet();
    private final Long2ObjectOpenHashMap<Room> cellToRoom = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Room> ventToRoom = new Long2ObjectOpenHashMap<>();
    /** Vents facing a wall: front cell -> vent. Re-searched when that cell changes. */
    private final Long2LongOpenHashMap blockedFronts = new Long2LongOpenHashMap();
    private final List<Room> rooms = new ArrayList<>();
    private final Map<Room, Long> dirtyRooms = new IdentityHashMap<>();
    /** Vents waiting for a search: vent -> tick it was queued. */
    private final Long2LongLinkedOpenHashMap pendingSeeds = new Long2LongLinkedOpenHashMap();
    /** Temperature a vent's next room inherits from the room it belonged to. */
    private final Long2FloatOpenHashMap seedTemperatures = new Long2FloatOpenHashMap();
    private final LongArrayFIFOQueue jobSeeds = new LongArrayFIFOQueue();
    private final ConcurrentLinkedQueue<Long> loadedChunks = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Long> unloadedChunks = new ConcurrentLinkedQueue<>();

    @Nullable
    private RoomScan currentScan;
    private long currentSeed;
    private long currentScanNanos;
    private int currentScanTicks;
    /**
     * Back side of vents that blow into no room: watched cell -> vents. Several vents can watch
     * the same cells (several roof vents of one room): a single vent per cell left the others facing out.
     */
    private final Long2ObjectOpenHashMap<LongOpenHashSet> backWatch = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<LongOpenHashSet> backWatchOfVent = new Long2ObjectOpenHashMap<>();
    /** Room numbers that survive a new search. */
    private final RoomIdentities<Room> identities = new RoomIdentities<>();
    /** A rescan group whose vents did not all come back within this delay is closed (ticks). */
    private static final long IDENTITY_MAX_AGE = 600;

    // Statistics, shown by /frostworks room
    private long scansDone;
    /** Searches done from each vent (tests: a room searched again, told apart from the searches of the others). */
    private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap scansBySeed = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    private long lastScanNanos;
    private int lastScanVolume;

    private RoomManager(ServerLevel level) {
        this.level = level;
        this.world = new LevelRoomWorld(level);
        // The rooms saved with the world are back at once, with their state and temperature. The
        // manager is created by the first question asked about the level (RoomManager.get), at the latest by the
        // overspeed mixin when the first kinetic block of a loading chunk propagates: the restored rooms always answer
        // first, whatever the order chunks load in.
        this.saved = RoomSavedData.get(level);
        restore(saved.records());
        saved.attach(this::snapshot);
    }

    // ---- Saved rooms -------------------------------------------------------------------------------

    private final RoomSavedData saved;
    /** Vents whose search stopped at an unloaded chunk inside a restored room: searched again when a chunk loads. */
    private final LongOpenHashSet waitingForChunks = new LongOpenHashSet();
    /** A restored room no search claimed after this long, all its chunks loaded, is stale (ticks). */
    private static final long RESTORED_MAX_AGE = 200;

    private void restore(List<RoomRecords.Record> records) {
        long now = level.getGameTime();
        for (RoomRecords.Record record : records) {
            Room room = new Room(record, now);
            for (LongIterator it = room.cells.iterator(); it.hasNext(); ) {
                long cell = it.nextLong();
                cellToRoom.put(cell, room);
                room.chunks.add(ChunkPos.asLong(PosKey.x(cell) >> 4, PosKey.z(cell) >> 4));
            }
            rooms.add(room);
            identities.reserve(room.id);
        }
        if (!records.isEmpty())
            Frostworks.LOGGER.info("Restored {} room(s) of {} from the save", records.size(), level.dimension().location());
    }

    /** What is saved: the closed rooms (searched or still restored), with their state and temperature. */
    private List<RoomRecords.Record> snapshot() {
        List<RoomRecords.Record> out = new ArrayList<>();
        for (Room room : rooms)
            if (room.outcome == RoomScan.Outcome.CLOSED && room.volume > 0)
                out.add(room.toRecord());
        return out;
    }

    /**
     * A restored room is kept as saved (state, temperature) until one of its vents is searched again: that search
     * replaces it, with the same number and temperature when the cells match (RoomIdentities, stale rooms).
     */
    private void freeze(Room room) {
        if (room.restored)
            return;
        room.restored = true;
        room.restoredAt = level.getGameTime();
    }

    private boolean allChunksLoaded(Room room) {
        for (LongIterator it = room.chunks.iterator(); it.hasNext(); ) {
            long c = it.nextLong();
            if (level.getChunkSource().getChunkNow(ChunkPos.getX(c), ChunkPos.getZ(c)) == null)
                return false;
        }
        return true;
    }

    /** Tests: a restart in memory (rooms encoded and decoded back, the manager made again). No disk involved. */
    public static RoomManager restartForTest(ServerLevel level) {
        RoomManager old = get(level);
        long[] knownVents = old.vents.toLongArray();
        old.saved.reloadThroughBytes();
        forget(level);
        RoomManager fresh = get(level);
        // in a real restart the vents register again as their chunks load (VentBlockEntity.initialize)
        for (long vent : knownVents)
            fresh.ventAdded(BlockPos.of(vent));
        return fresh;
    }

    // ---- Events -------------------------------------------------------------------------------------------------

    public void ventAdded(BlockPos pos) {
        long key = pos.asLong();
        vents.add(key);
        pendingSeeds.putIfAbsent(key, level.getGameTime());
    }

    public void ventRemoved(BlockPos pos) {
        long key = pos.asLong();
        vents.remove(key);
        pendingSeeds.remove(key);
        seedTemperatures.remove(key);
        identities.seedDone(key);
        clearBackWatch(key);
        blockedFronts.values().removeIf((java.util.function.LongPredicate) v -> v == key);
        waitingForChunks.remove(key);
        Room room = ventToRoom.remove(key);
        if (room != null) {
            room.vents.remove(key);
            updateStatus(room);
        }
    }

    /**
     * The vent's chunk unloads (the vent is not broken). Its room is frozen as it is and keeps the
     * vent, which searches it again when it loads back.
     */
    public void ventUnloaded(BlockPos pos) {
        long key = pos.asLong();
        vents.remove(key);
        pendingSeeds.remove(key);
        clearBackWatch(key);
        Room room = ventToRoom.remove(key);
        if (room != null && room.outcome == RoomScan.Outcome.CLOSED)
            freeze(room);
        else if (room != null)
            room.vents.remove(key);
    }

    public void ventActivityChanged(BlockPos pos) {
        Room room = ventToRoom.get(pos.asLong());
        if (room != null)
            updateStatus(room);
    }

    /** Called for every block update of the level: must stay cheap. */
    public void onBlockChanged(BlockPos pos, BlockState state) {
        if (cellToRoom.isEmpty() && blockedFronts.isEmpty() && backWatch.isEmpty() && currentScan == null)
            return;
        long key = pos.asLong();
        boolean wall = RoomWalls.isWall(level, pos, state);

        if (vents.contains(key) && state.getBlock() instanceof VentBlock)
            ventMaybeTurned(pos, state);

        if (blockedFronts.containsKey(key) && !wall)
            pendingSeeds.putIfAbsent(blockedFronts.remove(key), level.getGameTime());

        if (wall && backWatch.containsKey(key)) {
            // A wall placed behind a vent that blows into no room: the back side may be a closed room now
            for (long vent : backWatch.get(key).toLongArray()) {
                clearBackWatch(vent);
                Room room = ventToRoom.get(vent);
                if (room != null)
                    markDirty(room);
                else
                    pendingSeeds.putIfAbsent(vent, level.getGameTime());
            }
        }

        Room inside = cellToRoom.get(key);
        if (inside != null) {
            // A room cell became a wall (a non-wall block placed inside changes nothing)
            if (wall)
                markDirty(inside);
        } else if (!wall) {
            // Not a room cell, next to one: it was a wall, it is not any more. An OPEN room stays OPEN whatever goes
            // at its border (its cells reach far outside, where blocks change all the time; only a
            // wall placed in one of its cells can close it, see above).
            for (Direction d : Direction.values()) {
                Room next = cellToRoom.get(BlockPos.offset(key, d));
                if (next != null && next.outcome != RoomScan.Outcome.OPEN)
                    markDirty(next);
            }
        }

        if (currentScan != null && touches(currentScan, key))
            restartCurrentScan();
    }

    /**
     * A vent turned in place (Create's wrench keeps its block entity, so it is not registered again).
     * If its front is no longer in its room, or no longer the wall it was blowing into, it leaves its room and is
     * searched again.
     */
    private void ventMaybeTurned(BlockPos pos, BlockState state) {
        long key = pos.asLong();
        long front = pos.relative(state.getValue(VentBlock.FACING)).asLong();
        Room room = ventToRoom.get(key);
        if (room != null) {
            if (room.restored || room.cells.contains(front))
                return;
            ventToRoom.remove(key);
            room.vents.remove(key);
            updateStatus(room);
        } else if (!blockedFronts.long2LongEntrySet().removeIf(e -> e.getLongValue() == key && e.getLongKey() != front))
            return;
        pendingSeeds.putIfAbsent(key, level.getGameTime());
    }

    public void onChunkLoaded(ChunkPos pos) {
        loadedChunks.add(pos.toLong());
    }

    public void onChunkUnloaded(ChunkPos pos) {
        unloadedChunks.add(pos.toLong());
    }

    private static boolean touches(RoomScan scan, long key) {
        if (scan.cells().contains(key))
            return true;
        for (Direction d : Direction.values())
            if (scan.cells().contains(BlockPos.offset(key, d)))
                return true;
        return false;
    }

    public void markDirty(Room room) {
        if (rooms.contains(room))
            dirtyRooms.putIfAbsent(room, level.getGameTime());
    }

    // ---- Tick ---------------------------------------------------------------------------------------------------

    public void tick() {
        long now = level.getGameTime();
        int delay = FwConfig.get(FwConfig.RESCAN_DELAY);

        handleChunkEvents();

        // Dirty rooms whose delay is over are dismantled; their vents go back to the search queue
        if (!dirtyRooms.isEmpty()) {
            List<Room> due = new ArrayList<>();
            for (Map.Entry<Room, Long> e : dirtyRooms.entrySet())
                if (now - e.getValue() >= delay)
                    due.add(e.getKey());
            due.forEach(this::dismantle);
        }

        for (Iterator<Long2LongLinkedOpenHashMap.Entry> it = pendingSeeds.long2LongEntrySet().fastIterator();
             it.hasNext(); ) {
            Long2LongLinkedOpenHashMap.Entry e = it.next();
            if (now - e.getLongValue() < delay)
                break; // insertion order = time order
            jobSeeds.enqueue(e.getLongKey());
            it.remove();
        }

        runSearches();

        if (now % 20 == 0) {
            updateRoomsEverySecond();
            identities.expire(now, IDENTITY_MAX_AGE);
        }
        // OPEN rooms are no longer searched again every 100 ticks. Each search built a new room
        // with a new number, so an OPEN room's number kept going up while nothing changed. A search runs again only
        // when a block changes in the room or at its border (onBlockChanged), when a chunk loads (UNLOADED) or on
        // request (/frostworks room rescan, rescanAll). A hole is always among the cells of an OPEN room (the flood fill
        // goes through it), so closing it is seen there.
    }

    private void handleChunkEvents() {
        Long chunk;
        boolean anyLoaded = false;
        while ((chunk = loadedChunks.poll()) != null)
            anyLoaded = true;
        if (anyLoaded) {
            for (Room room : new ArrayList<>(rooms)) {
                if (room.outcome == RoomScan.Outcome.UNLOADED)
                    markDirty(room);
                // a frozen room back in full: its vents still known search it again (the others will when they load)
                if (room.restored && allChunksLoaded(room))
                    for (LongIterator it = room.vents.iterator(); it.hasNext(); ) {
                        long vent = it.nextLong();
                        if (vents.contains(vent) && ventToRoom.get(vent) == room) {
                            ventToRoom.remove(vent);
                            pendingSeeds.putIfAbsent(vent, level.getGameTime());
                        }
                    }
            }
            for (LongIterator it = waitingForChunks.iterator(); it.hasNext(); )
                pendingSeeds.putIfAbsent(it.nextLong(), level.getGameTime());
            waitingForChunks.clear();
        }
        while ((chunk = unloadedChunks.poll()) != null) {
            long c = chunk;
            for (Room room : rooms)
                if (room.chunks.contains(c)) {
                    // A closed room that goes partly or wholly out of the loaded area keeps its state
                    // and temperature (frozen) instead of being searched again into PENDING
                    if (room.outcome == RoomScan.Outcome.CLOSED)
                        freeze(room);
                    else
                        markDirty(room);
                }
        }
    }

    private void runSearches() {
        world.resetCache();
        int budget = FwConfig.get(FwConfig.MAX_CELLS_PER_TICK);
        int maxStarts = FwConfig.get(FwConfig.MAX_RESCANS_PER_TICK);
        int cap = FwConfig.get(FwConfig.ROOM_SEARCH_CAP);
        int started = 0;
        boolean ticked = false;

        while (budget > 0) {
            if (currentScan == null) {
                if (jobSeeds.isEmpty() || started >= maxStarts)
                    return;
                long vent = jobSeeds.dequeueLong();
                if (!vents.contains(vent) || ventToRoom.containsKey(vent)) {
                    identities.seedDone(vent);
                    continue;
                }
                BlockPos ventPos = BlockPos.of(vent);
                BlockState state = level.getBlockState(ventPos);
                if (!(state.getBlock() instanceof VentBlock)) {
                    identities.seedDone(vent);
                    continue;
                }
                BlockPos front = ventPos.relative(state.getValue(VentBlock.FACING));
                Room existing = cellToRoom.get(front.asLong());
                if (existing != null && !existing.restored && !dirtyRooms.containsKey(existing)) {
                    // A vent that blows into the open air another vent already searched: it may still have a closed
                    // room behind it (several roof vents placed from above all faced up; only the first one turned)
                    if (existing.outcome == RoomScan.Outcome.OPEN) {
                        clearBackWatch(vent);
                        if (turnTowardsClosedRoom(vent))
                            continue;
                    }
                    attach(vent, existing);
                    updateStatus(existing);
                    identities.seedDone(vent);
                    continue;
                }
                currentScan = new RoomScan(world, front.getX(), front.getY(), front.getZ(), cap);
                currentSeed = vent;
                currentScanNanos = 0;
                currentScanTicks = 0;
                started++;
            }
            if (!ticked) {
                currentScanTicks++;
                ticked = true;
            }
            int before = currentScan.volume();
            long t0 = System.nanoTime();
            currentScan.advance(budget);
            currentScanNanos += System.nanoTime() - t0;
            budget -= Math.max(1, currentScan.volume() - before);
            if (!currentScan.isDone())
                return; // goes on next tick
            finishScan();
            ticked = false;
        }
    }

    private void restartCurrentScan() {
        RoomScan old = currentScan;
        long s = old.start();
        currentScan = new RoomScan(world, PosKey.x(s), PosKey.y(s), PosKey.z(s), FwConfig.get(FwConfig.ROOM_SEARCH_CAP));
    }

    private void finishScan() {
        RoomScan scan = currentScan;
        currentScan = null;
        long seed = currentSeed;
        scansDone++;
        scansBySeed.addTo(seed, 1);
        lastScanNanos = currentScanNanos;
        lastScanVolume = scan.volume();

        if (!vents.contains(seed)) {
            identities.seedDone(seed);
            return;
        }
        clearBackWatch(seed);
        boolean frontUseless = scan.outcome() == RoomScan.Outcome.OPEN
                || scan.outcome() == RoomScan.Outcome.CLOSED && scan.volume() == 0;
        if (frontUseless && turnTowardsClosedRoom(seed))
            return; // searched again from its new front
        if (scan.outcome() == RoomScan.Outcome.CLOSED && scan.volume() == 0) {
            // The vent blows into a wall: no room, search again when that wall goes
            blockedFronts.put(scan.start(), seed);
            seedTemperatures.remove(seed);
            identities.seedDone(seed);
            return;
        }
        if (scan.outcome() != RoomScan.Outcome.CLOSED && FwConfig.get(FwConfig.DEBUG_ROOM_LEAKS))
            logLeak(scan, seed);
        // A search that met an unloaded chunk inside a restored room leaves it as saved (never OPEN,
        // never warmer because of that); it is searched again when a chunk loads
        if (scan.outcome() == RoomScan.Outcome.UNLOADED && touchesRestored(scan)) {
            waitingForChunks.add(seed);
            return;
        }

        float ambient = (float) FwConfig.get(FwConfig.AMBIENT_TEMPERATURE);
        float temperature = seedTemperatures.containsKey(seed) ? seedTemperatures.get(seed) : ambient;
        Room room = new Room(0, scan, temperature, currentScanNanos, currentScanTicks);

        // Rooms that were cached for some of these cells are stale (e.g. two rooms joined by a broken wall): they are
        // retired before the new room gets its number, so that it can inherit one of theirs (merge)
        Map<Room, Boolean> stale = new IdentityHashMap<>();
        for (LongIterator it = room.cells.iterator(); it.hasNext(); ) {
            long cell = it.nextLong();
            Room old = cellToRoom.get(cell);
            if (old != null)
                stale.put(old, true);
        }
        // A named room found again as it was (closed, the same cells) keeps its name; opened, cut, joined to another
        // or reshaped, it loses it (the name tag is not used up: naming it again is cheap)
        Room same = stale.size() == 1 && room.outcome == RoomScan.Outcome.CLOSED ? stale.keySet().iterator().next() : null;
        String keptName = same != null && sameCells(same, room) ? saved.name(same.id) : null;
        for (Room old : stale.keySet()) {
            room.temperature = Math.min(room.temperature, old.temperature);
            dismantle(old);
        }
        for (Room old : stale.keySet())
            saved.rename(old.id, null);
        for (LongIterator it = room.cells.iterator(); it.hasNext(); ) {
            long cell = it.nextLong();
            cellToRoom.put(cell, room);
            room.chunks.add(ChunkPos.asLong(PosKey.x(cell) >> 4, PosKey.z(cell) >> 4));
        }
        identities.found(room, room.cells, seed).forEach((r, id) -> r.id = id);
        if (keptName != null && room.id == same.id)
            saved.rename(room.id, keptName);
        rooms.add(room);
        attach(seed, room);
        updateStatus(room);
    }

    /**
     * The cause of the rooms always OPEN: a vent set into a wall from outside the room faced
     * outside, so the room searched from its front was the open air. When its front gives no room (OPEN, or a wall),
     * its back side is searched: if the back is a closed room, the vent is turned to blow into it (its block entity is
     * kept; the pipe outside is then at its back, where gas comes in). Otherwise the cells of the back side are
     * watched: a wall placed there (the room being finished) makes this check run again.
     *
     * @return true if the vent was turned (it is searched again from its new front)
     */
    private boolean turnTowardsClosedRoom(long seed) {
        BlockPos ventPos = BlockPos.of(seed);
        BlockState state = level.getBlockState(ventPos);
        if (!(state.getBlock() instanceof VentBlock))
            return false;
        Direction back = state.getValue(VentBlock.FACING).getOpposite();
        BlockPos start = ventPos.relative(back);
        RoomScan behind = new RoomScan(world, start.getX(), start.getY(), start.getZ(),
                FwConfig.get(FwConfig.ROOM_SEARCH_CAP));
        behind.run();
        if (behind.outcome() == RoomScan.Outcome.CLOSED && behind.volume() > 0) {
            Frostworks.LOGGER.info("Vent {} blew out of a room ({}): turned {} to blow into the closed room behind it "
                    + "({} cells)", ventPos.toShortString(), state.getValue(VentBlock.FACING), back, behind.volume());
            level.setBlock(ventPos, state.setValue(VentBlock.FACING, back), Block.UPDATE_ALL);
            pendingSeeds.putIfAbsent(seed, level.getGameTime());
            return true;
        }
        LongOpenHashSet watched = new LongOpenHashSet(behind.cells());
        watched.add(start.asLong());
        backWatchOfVent.put(seed, watched);
        for (LongIterator it = watched.iterator(); it.hasNext(); )
            backWatch.computeIfAbsent(it.nextLong(), c -> new LongOpenHashSet()).add(seed);
        return false;
    }

    private void clearBackWatch(long vent) {
        LongOpenHashSet watched = backWatchOfVent.remove(vent);
        if (watched != null)
            for (LongIterator it = watched.iterator(); it.hasNext(); ) {
                long cell = it.nextLong();
                LongOpenHashSet vents = backWatch.get(cell);
                if (vents != null && vents.remove(vent) && vents.isEmpty())
                    backWatch.remove(cell);
            }
    }

    private boolean touchesRestored(RoomScan scan) {
        for (LongIterator it = scan.cells().iterator(); it.hasNext(); ) {
            Room r = cellToRoom.get(it.nextLong());
            if (r != null && r.restored)
                return true;
        }
        return false;
    }

    /** debug.roomLeaks: where and why a search did not find a closed room. */
    private void logLeak(RoomScan scan, long seed) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (LongIterator it = scan.cells().iterator(); it.hasNext(); ) {
            long c = it.nextLong();
            minX = Math.min(minX, PosKey.x(c));
            minY = Math.min(minY, PosKey.y(c));
            minZ = Math.min(minZ, PosKey.z(c));
            maxX = Math.max(maxX, PosKey.x(c));
            maxY = Math.max(maxY, PosKey.y(c));
            maxZ = Math.max(maxZ, PosKey.z(c));
        }
        String reason = switch (scan.stopReason()) {
            case CAP -> "cell cap reached (" + FwConfig.get(FwConfig.ROOM_SEARCH_CAP) + ")";
            case OUTSIDE -> "left the world (above or below the build height)";
            case UNLOADED -> "unloaded chunk";
            case NONE -> "other";
        };
        long stop = scan.stopCell(), far = scan.farthestCell();
        Frostworks.LOGGER.info("[roomLeaks] vent {} in {}: room {} with {} cells, box ({} {} {}) to ({} {} {}); "
                        + "reason: {} at ({} {} {}); farthest cell from the vent: ({} {} {})",
                BlockPos.of(seed).toShortString(), level.dimension().location(), scan.outcome(), scan.volume(),
                minX, minY, minZ, maxX, maxY, maxZ, reason, PosKey.x(stop), PosKey.y(stop), PosKey.z(stop),
                PosKey.x(far), PosKey.y(far), PosKey.z(far));
    }

    /** Removes a room from the cache and queues its vents for a new search. */
    private void dismantle(Room room) {
        dirtyRooms.remove(room);
        if (!rooms.remove(room))
            return;
        identities.forget(room);
        identities.retire(room.id, room.cells, room.vents, level.getGameTime());
        for (LongIterator it = room.cells.iterator(); it.hasNext(); ) {
            long cell = it.nextLong();
            if (cellToRoom.get(cell) == room)
                cellToRoom.remove(cell);
        }
        for (LongIterator it = room.vents.iterator(); it.hasNext(); ) {
            long vent = it.nextLong();
            if (ventToRoom.get(vent) == room)
                ventToRoom.remove(vent);
            seedTemperatures.put(vent, room.temperature);
            jobSeeds.enqueue(vent);
        }
    }

    private void attach(long vent, Room room) {
        Room previous = ventToRoom.put(vent, room);
        if (previous != null && previous != room)
            previous.vents.remove(vent);
        room.vents.add(vent);
        seedTemperatures.remove(vent);
    }

    private void updateStatus(Room room) {
        if (room.restored)
            return; // as saved until searched again
        int capacity = 0;
        int active = 0;
        int warmest = Integer.MIN_VALUE;
        for (LongIterator it = room.vents.iterator(); it.hasNext(); ) {
            BlockPos pos = BlockPos.of(it.nextLong());
            if (!level.isLoaded(pos))
                continue;
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof VentBlockEntity vent && vent.isActive()) {
                active++;
                capacity += vent.coverage();
                if (vent.coldFluid() != null)
                    warmest = Math.max(warmest, vent.coldFluid().temperature());
            }
        }
        room.activeVents = active;
        room.capacity = capacity;
        room.status = RoomStatus.evaluate(room.outcome, room.volume, room.capacity);
        // The warmest cold fluid of its active vents (Frosium alone); none: kept as it was
        if (warmest != Integer.MIN_VALUE)
            room.coldTarget = warmest;
    }

    private void updateRoomsEverySecond() {
        float ambient = (float) FwConfig.get(FwConfig.AMBIENT_TEMPERATURE);
        float cooling = (float) FwConfig.get(FwConfig.COOLING_RATE);
        float warming = (float) FwConfig.get(FwConfig.WARMING_RATE);
        long now = level.getGameTime();
        List<Room> stale = new ArrayList<>();
        for (Room room : rooms) {
            if (room.restored) {
                // as saved (no catching up on the time away), until its search replaces it; dropped if no search ever
                // claims it once everything around it is loaded (its vents are gone, or blow elsewhere now)
                if (now - room.restoredAt > RESTORED_MAX_AGE && allChunksLoaded(room) && !awaitsASearch(room))
                    stale.add(room);
                continue;
            }
            updateStatus(room);
            room.temperature = Temperature.step(room.temperature, room.status, 1f, ambient,
                    room.coldTarget, cooling, warming);
        }
        for (Room room : stale)
            dropRestored(room);
        if (!rooms.isEmpty())
            saved.setDirty(); // temperatures move: save the rooms with the world
    }

    /** One of the room's vents is still to be searched (queued, running, or waiting for a chunk). */
    private boolean awaitsASearch(Room room) {
        for (LongIterator it = room.vents.iterator(); it.hasNext(); ) {
            long vent = it.nextLong();
            if (pendingSeeds.containsKey(vent) || waitingForChunks.contains(vent)
                    || currentScan != null && currentSeed == vent)
                return true;
            // A vent of the room is not registered while its block is still there (its chunk not
            // loaded, or its block entity not ticking yet): the room waits for it
            if (!vents.contains(vent) && ventStillThere(vent))
                return true;
        }
        return false;
    }

    private boolean ventStillThere(long vent) {
        BlockPos pos = BlockPos.of(vent);
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null || chunk.getBlockState(pos).getBlock() instanceof VentBlock;
    }

    private static boolean sameCells(Room a, Room b) {
        if (a.cells.size() != b.cells.size())
            return false;
        for (LongIterator it = b.cells.iterator(); it.hasNext(); )
            if (!a.cells.contains(it.nextLong()))
                return false;
        return true;
    }

    /** A restored room that matches nothing any more: out of the cache and of the save. */
    private void dropRestored(Room room) {
        saved.rename(room.id, null);
        rooms.remove(room);
        dirtyRooms.remove(room);
        for (LongIterator it = room.cells.iterator(); it.hasNext(); ) {
            long cell = it.nextLong();
            if (cellToRoom.get(cell) == room)
                cellToRoom.remove(cell);
        }
        for (LongIterator it = room.vents.iterator(); it.hasNext(); ) {
            long vent = it.nextLong();
            if (ventToRoom.get(vent) == room)
                ventToRoom.remove(vent);
        }
        Frostworks.LOGGER.info("Saved room #{} of {} matches nothing any more: dropped", room.id,
                level.dimension().location());
    }

    // ---- Queries ------------------------------------------------------------------------------------------------

    @Nullable
    public Room roomAt(BlockPos cell) {
        return cellToRoom.get(cell.asLong());
    }

    @Nullable
    public Room roomOfVent(BlockPos vent) {
        return ventToRoom.get(vent.asLong());
    }

    /**
     * The room's name (a name tag on one of its cold walls), or null. Saved with the rooms; lost when the room is
     * opened, cut, joined to another or reshaped ({@link #finishScan}).
     */
    @Nullable
    public String name(Room room) {
        return saved.name(room.id);
    }

    /** Names the room (null or empty: no name any more). */
    public void rename(Room room, @Nullable String name) {
        saved.rename(room.id, name);
    }

    /**
     * Rooms touching a block: the room the block is in (if any), and the rooms of the 26 cells around it.
     * Edges and corners of a room touch its inside only by an edge or a corner; with the six sides only, the
     * goggles found no room on them.
     */
    public List<Room> roomsAround(BlockPos pos) {
        List<Room> found = new ArrayList<>(2);
        Room self = roomAt(pos);
        if (self != null)
            found.add(self);
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            Room r = roomAt(p);
            if (r != null && !found.contains(r))
                found.add(r);
        }
        return found;
    }

    public List<Room> rooms() {
        return Collections.unmodifiableList(rooms);
    }

    /** True while a search or a rebuild is waiting: results may still change. */
    public boolean isBusy() {
        return currentScan != null || !jobSeeds.isEmpty() || !pendingSeeds.isEmpty() || !dirtyRooms.isEmpty();
    }

    public boolean isDirty(Room room) {
        return dirtyRooms.containsKey(room);
    }

    public long scansDone() {
        return scansDone;
    }

    /** Searches done from this vent so far. */
    public long scansFrom(BlockPos vent) {
        return scansBySeed.get(vent.asLong());
    }

    public long lastScanNanos() {
        return lastScanNanos;
    }

    public int lastScanVolume() {
        return lastScanVolume;
    }

    public int ventCount() {
        return vents.size();
    }

    /** For tests: forget the cache and search every vent again right now. */
    public void rescanAll() {
        new ArrayList<>(rooms).forEach(this::dismantle);
        for (LongIterator it = vents.iterator(); it.hasNext(); )
            jobSeeds.enqueue(it.nextLong());
    }
}
