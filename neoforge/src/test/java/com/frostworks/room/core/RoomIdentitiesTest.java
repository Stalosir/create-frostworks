package com.frostworks.room.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/** A room keeps its number when it is searched again. */
class RoomIdentitiesTest {
    static final int CAP = 20_000;

    /** A found room: the scan's cells, under a name for the test. */
    record Found(String name, LongSet cells) {}

    private static LongSet scan(FakeWorld world, int x, int y, int z) {
        RoomScan scan = new RoomScan(world, x, y, z, CAP);
        assertEquals(RoomScan.Outcome.CLOSED, scan.run());
        return scan.cells();
    }

    private static long seed(int x, int y, int z) {
        return PosKey.pack(x, y, z);
    }

    private static LongSet seeds(long... s) {
        return new LongOpenHashSet(s);
    }

    /** First search of a room: a new number. */
    private static int first(RoomIdentities<Found> ids, Found room, long seed) {
        Map<Found, Integer> r = ids.found(room, room.cells(), seed);
        return r.get(room);
    }

    @Test
    void sameRoomSearchedAHundredTimesKeepsItsNumber() {
        FakeWorld world = new FakeWorld();
        world.shell(0, 0, 0, 4, 3, 4);
        RoomIdentities<Found> ids = new RoomIdentities<>();
        long vent = seed(1, 1, 1);
        Found room = new Found("room", scan(world, 1, 1, 1));
        int id = first(ids, room, vent);
        for (int i = 0; i < 100; i++) {
            ids.retire(id, room.cells(), seeds(vent), i);
            room = new Found("room " + i, scan(world, 1, 1, 1));
            assertEquals(id, ids.found(room, room.cells(), vent).get(room), "search " + (i + 1));
        }
        assertEquals(0, ids.openGroups());
    }

    @Test
    void wallPlacedInsideKeepsTheNumber() {
        FakeWorld world = new FakeWorld();
        world.shell(0, 0, 0, 4, 3, 4);
        RoomIdentities<Found> ids = new RoomIdentities<>();
        ids.freshId(); // another room exists: the number is not 1 by chance
        long vent = seed(1, 1, 1);
        Found room = new Found("room", scan(world, 1, 1, 1));
        int id = first(ids, room, vent);
        world.wall(2, 1, 2); // a wall inside, the room shrinks by one cell
        ids.retire(id, room.cells(), seeds(vent), 0);
        Found after = new Found("after", scan(world, 1, 1, 1));
        assertEquals(47, after.cells().size());
        assertEquals(id, ids.found(after, after.cells(), vent).get(after));
    }

    /** A 7 x 3 x 3 room cut by a wall at x = 2: a 2-long piece (west) and a 4-long piece (east). */
    private static FakeWorld splitWorld() {
        FakeWorld world = new FakeWorld();
        world.shell(0, 0, 0, 7, 3, 3);
        return world;
    }

    private static void cut(FakeWorld world) {
        for (int y = 0; y < 3; y++)
            for (int z = 0; z < 3; z++)
                world.wall(2, y, z);
    }

    @Test
    void splitTheBiggestPieceKeepsTheNumberSmallFoundFirst() {
        FakeWorld world = splitWorld();
        RoomIdentities<Found> ids = new RoomIdentities<>();
        long west = seed(0, 1, 1), east = seed(6, 1, 1);
        Found room = new Found("room", scan(world, 0, 1, 1));
        int id = first(ids, room, west);
        cut(world);
        ids.retire(id, room.cells(), seeds(west, east), 0);
        Found small = new Found("small", scan(world, 0, 1, 1));
        Found big = new Found("big", scan(world, 6, 1, 1));
        assertEquals(18, small.cells().size());
        assertEquals(36, big.cells().size());
        int smallFirst = ids.found(small, small.cells(), west).get(small); // provisional: the big piece is not known yet
        Map<Found, Integer> group = ids.found(big, big.cells(), east);
        assertEquals(id, group.get(big), "the biggest piece keeps the number");
        assertNotEquals(id, group.get(small), "the small piece gets a new number");
        assertEquals(smallFirst == id ? group.get(small) : smallFirst, group.get(small));
        assertEquals(0, ids.openGroups());
    }

    @Test
    void splitTheBiggestPieceKeepsTheNumberBigFoundFirst() {
        FakeWorld world = splitWorld();
        RoomIdentities<Found> ids = new RoomIdentities<>();
        long west = seed(0, 1, 1), east = seed(6, 1, 1);
        Found room = new Found("room", scan(world, 0, 1, 1));
        int id = first(ids, room, west);
        cut(world);
        ids.retire(id, room.cells(), seeds(west, east), 0);
        Found big = new Found("big", scan(world, 6, 1, 1));
        assertEquals(id, ids.found(big, big.cells(), east).get(big));
        Found small = new Found("small", scan(world, 0, 1, 1));
        Map<Found, Integer> group = ids.found(small, small.cells(), west);
        assertEquals(id, group.get(big));
        assertNotEquals(id, group.get(small));
    }

    /** A room set aside twice under the same number (searched again before its last search settled): one entry. */
    @Test
    void sameNumberSetAsideTwiceIsOneEntry() {
        RoomIdentities<Found> ids = new RoomIdentities<>();
        LongOpenHashSet a = new LongOpenHashSet(new long[]{seed(0, 0, 0), seed(1, 0, 0)});
        LongOpenHashSet b = new LongOpenHashSet(new long[]{seed(1, 0, 0), seed(2, 0, 0)});
        ids.retire(7, a, seeds(seed(0, 0, 0)), 0);
        ids.retire(7, b, seeds(seed(2, 0, 0)), 1);
        assertEquals(1, ids.openGroups());
        Found room = new Found("room", new LongOpenHashSet(new long[]{seed(2, 0, 0)}));
        assertEquals(7, ids.found(room, room.cells(), seed(2, 0, 0)).get(room), "cells of the second one count");
    }

    @Test
    void mergeKeepsTheNumberOfTheBiggestOverlap() {
        // Two rooms side by side, sharing the wall at x = 3: west 3 x 3 x 3 (27), east 5 x 3 x 3 (45)
        FakeWorld world = new FakeWorld();
        world.shell(0, 0, 0, 9, 3, 3);
        for (int y = 0; y < 3; y++)
            for (int z = 0; z < 3; z++)
                world.wall(3, y, z);
        RoomIdentities<Found> ids = new RoomIdentities<>();
        long westVent = seed(0, 1, 1), eastVent = seed(8, 1, 1);
        Found west = new Found("west", scan(world, 0, 1, 1));
        Found east = new Found("east", scan(world, 8, 1, 1));
        int westId = first(ids, west, westVent);
        int eastId = first(ids, east, eastVent);
        assertNotEquals(westId, eastId);
        world.remove(3, 1, 1); // a hole in the shared wall: one room
        ids.retire(westId, west.cells(), seeds(westVent), 0);
        ids.retire(eastId, east.cells(), seeds(eastVent), 0);
        Found merged = new Found("merged", scan(world, 0, 1, 1)); // found from the small side
        assertEquals(73, merged.cells().size());
        assertEquals(eastId, ids.found(merged, merged.cells(), westVent).get(merged));
        ids.seedDone(eastVent); // the east vent now blows into the merged room: no new search
        assertEquals(0, ids.openGroups());
    }

    @Test
    void aNewRoomGetsANewNumber() {
        FakeWorld world = new FakeWorld();
        world.shell(0, 0, 0, 3, 3, 3);
        world.shell(10, 0, 0, 3, 3, 3);
        RoomIdentities<Found> ids = new RoomIdentities<>();
        Found a = new Found("a", scan(world, 0, 0, 0));
        Found b = new Found("b", scan(world, 10, 0, 0));
        int idA = first(ids, a, seed(0, 0, 0));
        int idB = first(ids, b, seed(10, 0, 0));
        assertNotEquals(idA, idB);
        // Searching a again does not give it b's number, nor a new one
        ids.retire(idA, a.cells(), seeds(seed(0, 0, 0)), 0);
        Found again = new Found("a again", scan(world, 0, 0, 0));
        assertEquals(idA, ids.found(again, again.cells(), seed(0, 0, 0)).get(again));
    }
}
