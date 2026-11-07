package com.frostworks.room.core;

/** State of a room as shown to the player. */
public enum RoomStatus {
    /** Active vents cover the whole volume. */
    COLD,
    /** The room is closed but the active vents cover less than its volume. */
    INSUFFICIENT,
    /** The search went over {@code roomSearchCap} cells or left the world: a wall is missing. */
    OPEN,
    /** The search touched an unloaded chunk, or has not run yet. */
    PENDING;

    /** Evaluates a scanned room. Pure function, used by the game and by unit tests. */
    public static RoomStatus evaluate(RoomScan.Outcome outcome, int volume, int capacity) {
        return switch (outcome) {
            case OPEN -> OPEN;
            case UNLOADED, RUNNING -> PENDING;
            case CLOSED -> capacity >= volume ? COLD : INSUFFICIENT;
        };
    }

    /** Number of vents of the given coverage that are missing to cool the room (0 if none). */
    public static int missingVents(int volume, int capacity, int coveragePerVent) {
        if (capacity >= volume || coveragePerVent <= 0)
            return 0;
        return (volume - capacity + coveragePerVent - 1) / coveragePerVent;
    }
}
