package com.frostworks.room.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/** Rooms saved with the world, read back, then matched by the first searches. */
class RoomRecordsTest {
    static final int CAP = 20_000;

    record Found(String name, LongSet cells) {}

    private static LongSet scan(FakeWorld world, int x, int y, int z, RoomScan.Outcome expected) {
        RoomScan scan = new RoomScan(world, x, y, z, CAP);
        assertEquals(expected, scan.run());
        return scan.cells();
    }

    private static RoomRecords.Record saved(int id, LongSet cells, long vent) {
        return new RoomRecords.Record(id, RoomStatus.COLD, -10f, cells.toLongArray(), new long[]{vent});
    }

    /** Through the bytes and back, as the world save does. */
    private static RoomRecords.Record roundTrip(RoomRecords.Record r) {
        List<RoomRecords.Record> back = RoomRecords.decode(RoomRecords.encode(List.of(r)));
        assertEquals(1, back.size());
        return back.get(0);
    }

    /** A restored room is set aside like a room searched again; the first search matches it. */
    private static RoomIdentities<Found> restored(RoomRecords.Record r) {
        RoomIdentities<Found> ids = new RoomIdentities<>();
        ids.reserve(r.id());
        ids.retire(r.id(), new LongOpenHashSet(r.cells()), new LongOpenHashSet(r.vents()), 0);
        return ids;
    }

    @Test
    void roundTripKeepsEverything() {
        FakeWorld world = new FakeWorld();
        world.shell(0, 0, 0, 6, 4, 4);
        LongSet cells = scan(world, 1, 1, 1, RoomScan.Outcome.CLOSED);
        RoomRecords.Record r = new RoomRecords.Record(42, RoomStatus.COLD, -9.75f, cells.toLongArray(),
                new long[]{PosKey.pack(2, -1, 2), PosKey.pack(3, -1, 2)});
        RoomRecords.Record back = roundTrip(r);
        assertEquals(42, back.id());
        assertEquals(RoomStatus.COLD, back.status());
        assertEquals(-9.75f, back.temperature());
        assertArrayEquals(r.cells(), back.cells());
        assertArrayEquals(r.vents(), back.vents());
        assertEquals(96, back.cells().length);
    }

    @Test
    void severalRoomsAndStatuses() {
        List<RoomRecords.Record> rooms = List.of(
                new RoomRecords.Record(1, RoomStatus.COLD, -10f, new long[]{1, 2, 3}, new long[]{4}),
                new RoomRecords.Record(7, RoomStatus.INSUFFICIENT, 12.5f, new long[]{9}, new long[0]));
        List<RoomRecords.Record> back = RoomRecords.decode(RoomRecords.encode(rooms));
        assertEquals(2, back.size());
        assertEquals(RoomStatus.INSUFFICIENT, back.get(1).status());
        assertEquals(12.5f, back.get(1).temperature());
    }

    @Test
    void damagedOrUnknownDataGivesNoRoom() {
        assertTrue(RoomRecords.decode(new byte[0]).isEmpty());
        assertTrue(RoomRecords.decode(new byte[]{0, 0, 0, 99, 1, 2}).isEmpty()); // unknown version
        byte[] good = RoomRecords.encode(List.of(new RoomRecords.Record(1, RoomStatus.COLD, 0, new long[]{1}, new long[0])));
        byte[] cut = java.util.Arrays.copyOf(good, good.length - 3);
        assertTrue(RoomRecords.decode(cut).isEmpty());
    }

    @Test
    void sameRoomAfterRestartKeepsItsNumber() {
        FakeWorld world = new FakeWorld();
        world.shell(0, 0, 0, 6, 4, 4);
        long vent = PosKey.pack(1, 1, 1);
        RoomRecords.Record r = roundTrip(saved(5, scan(world, 1, 1, 1, RoomScan.Outcome.CLOSED), vent));
        RoomIdentities<Found> ids = restored(r);
        Found room = new Found("searched", scan(world, 1, 1, 1, RoomScan.Outcome.CLOSED));
        assertEquals(5, ids.found(room, room.cells(), vent).get(room));
        assertEquals(6, ids.freshId(), "a new room after a restart does not get a saved number");
    }

    @Test
    void wallAddedWhileOfflineKeepsTheNumber() {
        FakeWorld world = new FakeWorld();
        world.shell(0, 0, 0, 6, 4, 4);
        long vent = PosKey.pack(1, 1, 1);
        RoomRecords.Record r = roundTrip(saved(5, scan(world, 1, 1, 1, RoomScan.Outcome.CLOSED), vent));
        world.wall(4, 2, 2); // placed while the world was closed
        RoomIdentities<Found> ids = restored(r);
        Found room = new Found("searched", scan(world, 1, 1, 1, RoomScan.Outcome.CLOSED));
        assertEquals(95, room.cells().size());
        assertEquals(5, ids.found(room, room.cells(), vent).get(room));
    }

    @Test
    void wallBrokenWhileOfflineKeepsTheNumberOfTheNowOpenRoom() {
        FakeWorld world = new FakeWorld();
        world.shell(0, 0, 0, 6, 4, 4);
        long vent = PosKey.pack(1, 1, 1);
        RoomRecords.Record r = roundTrip(saved(5, scan(world, 1, 1, 1, RoomScan.Outcome.CLOSED), vent));
        world.remove(6, 1, 1); // a hole made while the world was closed
        RoomIdentities<Found> ids = restored(r);
        Found room = new Found("searched", scan(world, 1, 1, 1, RoomScan.Outcome.OPEN));
        assertEquals(5, ids.found(room, room.cells(), vent).get(room),
                "the open room is the same room (it warms up from its saved temperature)");
    }

    @Test
    void roomGoneWhileOfflineLeavesItsNumber() {
        FakeWorld world = new FakeWorld();
        world.shell(0, 0, 0, 6, 4, 4);
        world.shell(20, 0, 0, 3, 3, 3);
        long vent = PosKey.pack(1, 1, 1);
        RoomRecords.Record r = roundTrip(saved(5, scan(world, 1, 1, 1, RoomScan.Outcome.CLOSED), vent));
        RoomIdentities<Found> ids = restored(r);
        // the vent was moved to another room while offline: no overlap, a new number
        Found other = new Found("elsewhere", scan(world, 20, 0, 0, RoomScan.Outcome.CLOSED));
        int id = ids.found(other, other.cells(), vent).get(other);
        assertNotEquals(5, id);
        assertEquals(0, ids.openGroups(), "the saved room is forgotten once its vent was searched");
    }

    /** The cold target goes through, and a save of version 1 (before the cold liquid flag) still reads, at -10. */
    @Test
    void coldTargetAndVersionOne() throws java.io.IOException {
        var r = new RoomRecords.Record(3, RoomStatus.COLD, -20f, -30, new long[]{1}, new long[]{2});
        assertEquals(-30, RoomRecords.decode(RoomRecords.encode(List.of(r))).get(0).coldTarget());
        var bytes = new java.io.ByteArrayOutputStream();
        try (var out = new java.io.DataOutputStream(bytes)) {
            out.writeInt(1);
            out.writeInt(1);
            out.writeInt(5);
            out.writeUTF("COLD");
            out.writeFloat(-10f);
            out.writeInt(1);
            out.writeLong(7);
            out.writeInt(0);
        }
        List<RoomRecords.Record> old = RoomRecords.decode(bytes.toByteArray());
        assertEquals(1, old.size(), "version 1 still read");
        assertEquals(5, old.get(0).id());
        assertEquals(-10, old.get(0).coldTarget(), "version 1: the cold gas target");
    }
}
