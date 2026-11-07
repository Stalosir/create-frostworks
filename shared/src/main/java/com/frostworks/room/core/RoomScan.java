package com.frostworks.room.core;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Flood fill (6 neighbours) of the non-wall cells reachable from a start cell.
 * <p>
 * The scan is resumable: {@link #advance(int)} visits at most a given number of cells and can be called again on a
 * later tick, so a big search can be spread over several ticks. Pure Java (fastutil only), no Minecraft class.
 */
public final class RoomScan {

    public enum Outcome {
        /** Still running. */
        RUNNING,
        /** The room is closed: every reachable cell was visited. */
        CLOSED,
        /** More than {@code cap} cells are reachable, or the search left the world: the room is open. */
        OPEN,
        /** The search touched an unloaded chunk: no result, try again once it is loaded. */
        UNLOADED
    }

    /** Why a search is {@link Outcome#OPEN} or {@link Outcome#UNLOADED} ({@code debug.roomLeaks}). */
    public enum StopReason {
        /** Not stopped early. */
        NONE,
        /** More than {@code cap} cells: no wall was met in time. */
        CAP,
        /** A cell above or below the world: a floor or a ceiling is missing. */
        OUTSIDE,
        /** A cell in a chunk that is not loaded. */
        UNLOADED
    }

    private final RoomWorld world;
    private final int cap;
    private final long start;
    private final LongOpenHashSet cells = new LongOpenHashSet();
    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    private Outcome outcome = Outcome.RUNNING;
    private StopReason stopReason = StopReason.NONE;
    /** The cell that stopped the search (see {@link #stopReason}). */
    private long stopCell;
    private long cellChecks;

    public RoomScan(RoomWorld world, int startX, int startY, int startZ, int cap) {
        this.world = world;
        this.cap = cap;
        this.start = PosKey.pack(startX, startY, startZ);
        switch (world.cellAt(startX, startY, startZ)) {
            case WALL -> outcome = Outcome.CLOSED; // empty room: the start cell is itself a wall
            case UNLOADED -> stop(Outcome.UNLOADED, StopReason.UNLOADED, start);
            case OUTSIDE -> stop(Outcome.OPEN, StopReason.OUTSIDE, start);
            case PASSABLE -> {
                cells.add(start);
                queue.enqueue(start);
            }
        }
        cellChecks = 1;
    }

    /**
     * Visits up to {@code budget} cells.
     *
     * @return the outcome so far ({@link Outcome#RUNNING} if the budget ran out first)
     */
    public Outcome advance(int budget) {
        int visited = 0;
        while (outcome == Outcome.RUNNING && visited < budget) {
            if (queue.isEmpty()) {
                outcome = Outcome.CLOSED;
                break;
            }
            long current = queue.dequeueLong();
            visited++;
            int x = PosKey.x(current), y = PosKey.y(current), z = PosKey.z(current);
            for (int[] d : PosKey.NEIGHBOURS) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                long key = PosKey.pack(nx, ny, nz);
                if (cells.contains(key))
                    continue;
                cellChecks++;
                RoomWorld.Cell cell = world.cellAt(nx, ny, nz);
                if (cell == RoomWorld.Cell.WALL)
                    continue;
                if (cell == RoomWorld.Cell.UNLOADED) {
                    stop(Outcome.UNLOADED, StopReason.UNLOADED, key);
                    break;
                }
                if (cell == RoomWorld.Cell.OUTSIDE) {
                    stop(Outcome.OPEN, StopReason.OUTSIDE, key);
                    break;
                }
                cells.add(key);
                if (cells.size() > cap) {
                    stop(Outcome.OPEN, StopReason.CAP, key);
                    break;
                }
                queue.enqueue(key);
            }
        }
        return outcome;
    }

    private void stop(Outcome outcome, StopReason reason, long cell) {
        this.outcome = outcome;
        this.stopReason = reason;
        this.stopCell = cell;
    }

    public StopReason stopReason() {
        return stopReason;
    }

    /** The cell that stopped the search early (meaningless for {@link StopReason#NONE}). */
    public long stopCell() {
        return stopCell;
    }

    /**
     * The found cell farthest from the start (straight-line distance), to find where a room leaks; the start if none.
     */
    public long farthestCell() {
        int sx = PosKey.x(start), sy = PosKey.y(start), sz = PosKey.z(start);
        long best = start;
        long bestDistance = -1;
        for (it.unimi.dsi.fastutil.longs.LongIterator it = cells.iterator(); it.hasNext(); ) {
            long c = it.nextLong();
            long dx = PosKey.x(c) - sx, dy = PosKey.y(c) - sy, dz = PosKey.z(c) - sz;
            long d = dx * dx + dy * dy + dz * dz;
            if (d > bestDistance) {
                bestDistance = d;
                best = c;
            }
        }
        return best;
    }

    /** Runs the scan to the end in one go. */
    public Outcome run() {
        return advance(Integer.MAX_VALUE);
    }

    public Outcome outcome() {
        return outcome;
    }

    public boolean isDone() {
        return outcome != Outcome.RUNNING;
    }

    /** Cells found so far (all the room's cells once {@link Outcome#CLOSED}). */
    public LongOpenHashSet cells() {
        return cells;
    }

    /** Number of interior cells, empty or occupied. Only meaningful once {@link Outcome#CLOSED}. */
    public int volume() {
        return cells.size();
    }

    public long start() {
        return start;
    }

    /** Number of calls made to {@link RoomWorld#cellAt}, to measure the cost of a scan. */
    public long cellChecks() {
        return cellChecks;
    }
}
