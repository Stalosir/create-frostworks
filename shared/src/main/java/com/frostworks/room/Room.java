package com.frostworks.room;

import com.frostworks.room.core.RoomRecords;
import com.frostworks.room.core.RoomScan;
import com.frostworks.room.core.RoomStatus;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/** A room found by a scan, as kept by the {@link RoomManager}. Server side only. */
public final class Room {
    /**
     * Room number shown to the player. Kept across new searches of the same room
     * ({@link com.frostworks.room.core.RoomIdentities}); set by the {@link RoomManager} only.
     */
    public int id;
    /** Interior cells (for an OPEN or PENDING room: the cells found before the search stopped). */
    final LongOpenHashSet cells;
    /** Chunks (ChunkPos.asLong) that contain cells, to drop the room when one of them unloads. */
    final LongOpenHashSet chunks = new LongOpenHashSet();
    /** Vents whose front opens on this room. */
    final LongOpenHashSet vents = new LongOpenHashSet();
    public final RoomScan.Outcome outcome;
    public final int volume;
    float temperature;
    RoomStatus status = RoomStatus.PENDING;
    /**
     * The target of the room while COLD (deg C): the temperature of the warmest cold fluid its active vents diffuse
     * (to reach a colder step, every vent must take the colder fluid). Before the cold fluid recipes, a flag for the cold liquid gave -20 deg C.
     */
    int coldTarget = RoomRecords.DEFAULT_COLD_TARGET;
    int capacity;
    int activeVents;
    /** Cost of the scan that built this room. */
    public final long scanNanos;
    public final int scanTicks;

    /**
     * A room read back from the world save, not searched yet. Its status and temperature stay as saved
     * until the search of one of its vents replaces it (same number, same temperature when the cells match).
     */
    boolean restored;
    long restoredAt;

    Room(int id, RoomScan scan, float temperature, long scanNanos, int scanTicks) {
        this.id = id;
        this.cells = scan.cells();
        this.outcome = scan.outcome();
        this.volume = scan.volume();
        this.temperature = temperature;
        this.scanNanos = scanNanos;
        this.scanTicks = scanTicks;
    }

    /** A room read back from the world save, waiting for its search. */
    Room(RoomRecords.Record record, long now) {
        this.id = record.id();
        this.cells = new LongOpenHashSet(record.cells());
        this.outcome = RoomScan.Outcome.CLOSED;
        this.volume = cells.size();
        this.temperature = record.temperature();
        this.coldTarget = record.coldTarget();
        this.status = record.status();
        this.vents.addAll(LongArrayList.wrap(record.vents()));
        this.scanNanos = 0;
        this.scanTicks = 0;
        this.restored = true;
        this.restoredAt = now;
    }

    RoomRecords.Record toRecord() {
        return new RoomRecords.Record(id, status, temperature, coldTarget, cells.toLongArray(), vents.toLongArray());
    }

    public boolean isRestored() {
        return restored;
    }

    public float temperature() {
        return temperature;
    }

    public RoomStatus status() {
        return status;
    }

    /** The target of the room while COLD (deg C), from the cold fluids of its active vents. */
    public int coldTarget() {
        return coldTarget;
    }

    public int capacity() {
        return capacity;
    }

    public int activeVents() {
        return activeVents;
    }

    public int ventCount() {
        return vents.size();
    }

    public boolean contains(long cell) {
        return cells.contains(cell);
    }
}
