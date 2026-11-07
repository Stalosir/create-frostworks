package com.frostworks.room.core;

/**
 * The only thing the room search needs to know about the world: what a cell is.
 * Implemented over a Minecraft level in the game, and over a plain set of walls in unit tests.
 */
public interface RoomWorld {

    enum Cell {
        /** Not a wall: belongs to the room (air, machines, vanilla blocks...). */
        PASSABLE,
        /** Closes the room: cold walls, insulated doors, passages, vents. */
        WALL,
        /** In a chunk that is not loaded: the search gives up and waits. */
        UNLOADED,
        /** Outside the world's build height: the room is open to the sky or the void. */
        OUTSIDE
    }

    Cell cellAt(int x, int y, int z);
}
