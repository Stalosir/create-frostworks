package com.frostworks.room.core;

/** Temperature model of a room (°C, server side, not persisted). Pure Java. */
public final class Temperature {
    private Temperature() {}

    /**
     * Moves the temperature towards the cold target when the room is {@link RoomStatus#COLD}, towards the ambient
     * temperature otherwise.
     *
     * @param coolingRate °C per second while cold
     * @param warmingRate °C per second otherwise
     */
    public static float step(float current, RoomStatus status, float seconds, float ambient, float coldTarget,
                             float coolingRate, float warmingRate) {
        if (status == RoomStatus.COLD)
            // colder than its target (Frosium gave way to gas): it warms up to it, no jump
            return current < coldTarget ? Math.min(coldTarget, current + warmingRate * seconds)
                    : Math.max(coldTarget, current - coolingRate * seconds);
        if (current < ambient)
            return Math.min(ambient, current + warmingRate * seconds);
        return Math.max(ambient, current - warmingRate * seconds);
    }
}
