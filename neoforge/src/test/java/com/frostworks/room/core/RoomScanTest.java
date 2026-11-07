package com.frostworks.room.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoomScanTest {
    static final int CAP = 20_000;
    static final int COVERAGE = 16;

    /** The reference room of the spec: 6 x 4 x 4 interior = 96 cells. */
    private static FakeWorld testRoom() {
        FakeWorld world = new FakeWorld();
        world.shell(0, 0, 0, 6, 4, 4);
        return world;
    }

    private static RoomStatus statusWithVents(FakeWorld world, int vents) {
        RoomScan scan = new RoomScan(world, 2, 1, 2, CAP);
        RoomScan.Outcome outcome = scan.run();
        return RoomStatus.evaluate(outcome, scan.volume(), vents * COVERAGE);
    }

    @Test
    void closedRoomVolume() {
        RoomScan scan = new RoomScan(testRoom(), 0, 0, 0, CAP);
        assertEquals(RoomScan.Outcome.CLOSED, scan.run());
        assertEquals(96, scan.volume());
    }

    @Test
    void sixVentsMakeItCold() {
        assertEquals(RoomStatus.COLD, statusWithVents(testRoom(), 6));
    }

    @Test
    void fiveVentsAreInsufficient() {
        FakeWorld world = testRoom();
        assertEquals(RoomStatus.INSUFFICIENT, statusWithVents(world, 5));
        assertEquals(1, RoomStatus.missingVents(96, 5 * COVERAGE, COVERAGE));
    }

    @Test
    void brokenWallOpensTheRoom() {
        FakeWorld world = testRoom();
        world.remove(-1, 1, 1); // a hole in the west wall
        assertEquals(RoomStatus.OPEN, statusWithVents(world, 6));
    }

    @Test
    void woodenPlankInsteadOfWallIsOpen() {
        // A plank is not in the cold_walls tag: for the search it is a passable cell, exactly like a hole.
        FakeWorld world = testRoom();
        world.remove(6, 2, 2);
        assertEquals(RoomStatus.OPEN, statusWithVents(world, 6));
    }

    @Test
    void machinesInsideDoNotChangeVolume() {
        // Nothing but walls can remove a cell: the fake world only knows walls, so this documents the rule
        // that the volume counts every interior cell, empty or occupied.
        RoomScan scan = new RoomScan(testRoom(), 3, 2, 1, CAP);
        scan.run();
        assertEquals(96, scan.volume());
    }

    @Test
    void capReachedIsOpen() {
        FakeWorld world = new FakeWorld();
        world.shell(0, 0, 0, 30, 30, 30); // 27 000 cells > 20 000
        RoomScan scan = new RoomScan(world, 1, 1, 1, CAP);
        assertEquals(RoomScan.Outcome.OPEN, scan.run());
    }

    @Test
    void justUnderCapIsClosed() {
        FakeWorld world = new FakeWorld();
        world.shell(0, 0, 0, 50, 20, 20); // exactly 20 000 cells
        RoomScan scan = new RoomScan(world, 1, 1, 1, CAP);
        assertEquals(RoomScan.Outcome.CLOSED, scan.run());
        assertEquals(20_000, scan.volume());
    }

    @Test
    void unloadedChunkGivesUp() {
        FakeWorld world = testRoom();
        world.remove(6, 1, 1); // open towards east...
        world.loadedMaxX = 10; // ...where the world is not loaded
        RoomScan scan = new RoomScan(world, 1, 1, 1, CAP);
        assertEquals(RoomScan.Outcome.UNLOADED, scan.run());
        assertEquals(RoomStatus.PENDING, RoomStatus.evaluate(scan.outcome(), scan.volume(), 1000));
    }

    @Test
    void outsideBuildHeightIsOpen() {
        FakeWorld world = new FakeWorld();
        world.maxY = 10;
        world.shell(0, 0, 0, 4, 20, 4); // the shell's top is above the world
        for (int x = -1; x <= 4; x++)
            for (int z = -1; z <= 4; z++)
                world.remove(x, 20, z);
        RoomScan scan = new RoomScan(world, 1, 1, 1, CAP);
        assertEquals(RoomScan.Outcome.OPEN, scan.run());
    }

    @Test
    void startInsideAWallIsAnEmptyRoom() {
        FakeWorld world = testRoom();
        RoomScan scan = new RoomScan(world, -1, 0, 0, CAP);
        assertEquals(RoomScan.Outcome.CLOSED, scan.outcome());
        assertEquals(0, scan.volume());
    }

    @Test
    void spreadScanGivesTheSameResult() {
        FakeWorld world = new FakeWorld();
        world.shell(0, 0, 0, 20, 10, 15);
        RoomScan oneGo = new RoomScan(world, 5, 5, 5, CAP);
        oneGo.run();
        RoomScan spread = new RoomScan(world, 5, 5, 5, CAP);
        int steps = 0;
        while (spread.advance(97) == RoomScan.Outcome.RUNNING)
            steps++;
        assertTrue(steps > 10);
        assertEquals(oneGo.outcome(), spread.outcome());
        assertEquals(oneGo.volume(), spread.volume());
        assertEquals(oneGo.cells(), spread.cells());
    }

    @Test
    void posKeyRoundTrip() {
        int[][] samples = {{0, 0, 0}, {-1, -64, -1}, {30_000_000, 319, -30_000_000}, {123, -5, 456}};
        for (int[] s : samples) {
            long key = PosKey.pack(s[0], s[1], s[2]);
            assertEquals(s[0], PosKey.x(key));
            assertEquals(s[1], PosKey.y(key));
            assertEquals(s[2], PosKey.z(key));
        }
    }

    @Test
    void temperatureMovesTowardsTargets() {
        float t = 20f;
        for (int i = 0; i < 100; i++)
            t = Temperature.step(t, RoomStatus.COLD, 1f, 20f, -10f, 0.5f, 0.5f);
        assertEquals(-10f, t, 1e-4);
        t = Temperature.step(t, RoomStatus.OPEN, 4f, 20f, -10f, 0.5f, 0.5f);
        assertEquals(-8f, t, 1e-4);
    }

    // ---- A closed box with a vent, a door and a shaft passage in its walls ----------------

    /**
     * Interior 3 x 3 x 3 at (0, 0, 0), walls of cold wall. In the walls: a vent at (1, 1, -1) blowing south into the
     * room, a door (2 blocks high) at (1, 0..1, 3), a shaft passage at (3, 1, 1). For the search they are all walls
     * ({@code RoomWalls.isWall}: cold_walls tag, InsulatedDoorBlock (both halves), VentBlock, ShaftPassageBlock;
     * checked in the game by ModifGameTests and the pilot). {@code door} false: a cold wall where the door is;
     * {@code hole} true: the passage is taken away (a real hole).
     */
    private static FakeWorld box(boolean door, boolean hole) {
        FakeWorld world = new FakeWorld();
        world.shell(0, 0, 0, 3, 3, 3);
        // vent (1, 1, -1), door (1, 0, 3) + (1, 1, 3), passage (3, 1, 1): wall cells of the shell already; door or
        // wall, it is a wall cell either way
        if (!door)
            world.wall(1, 0, 3);
        if (hole)
            world.remove(3, 1, 1);
        return world;
    }

    /** The search starts in front of the vent, as in the game. */
    private static RoomScan fromVent(FakeWorld world) {
        RoomScan scan = new RoomScan(world, 1, 1, 0, CAP);
        scan.run();
        return scan;
    }

    @Test
    void closedBoxWithVentDoorAndPassageIsNeverOpen() {
        RoomScan scan = fromVent(box(true, false));
        assertEquals(RoomScan.Outcome.CLOSED, scan.outcome());
        assertEquals(27, scan.volume());
        assertEquals(RoomStatus.INSUFFICIENT, RoomStatus.evaluate(scan.outcome(), scan.volume(), COVERAGE));
        assertEquals(RoomStatus.COLD, RoomStatus.evaluate(scan.outcome(), scan.volume(), 2 * COVERAGE));
    }

    @Test
    void closedBoxWithAWallInsteadOfTheDoor() {
        RoomScan scan = fromVent(box(false, false));
        assertEquals(RoomScan.Outcome.CLOSED, scan.outcome());
        assertEquals(27, scan.volume());
    }

    @Test
    void boxWithARealHoleIsOpen() {
        RoomScan scan = fromVent(box(true, true));
        assertEquals(RoomScan.Outcome.OPEN, scan.outcome());
        assertEquals(RoomScan.StopReason.CAP, scan.stopReason());
        assertEquals(RoomStatus.OPEN, RoomStatus.evaluate(scan.outcome(), scan.volume(), 2 * COVERAGE));
    }

    /** The cause found for C2: a vent turned outwards searches the open air, whatever the box. */
    @Test
    void ventBlowingOutsideSearchesTheOpenAir() {
        RoomScan scan = new RoomScan(box(true, false), 1, 1, -2, CAP);
        assertEquals(RoomScan.Outcome.OPEN, scan.run());
    }

    @Test
    void stopReasons() {
        FakeWorld world = new FakeWorld();
        world.minY = 0;
        world.shell(0, 1, 0, 3, 3, 3);
        world.remove(1, 0, 1); // no floor under the room: the search goes below the world
        RoomScan scan = new RoomScan(world, 1, 1, 1, CAP);
        assertEquals(RoomScan.Outcome.OPEN, scan.run());
        assertEquals(RoomScan.StopReason.OUTSIDE, scan.stopReason());
        assertEquals(PosKey.pack(1, -1, 1), scan.stopCell());
        world.loadedMaxX = 1;
        world.wall(1, 0, 1);
        world.remove(3, 1, 1); // a hole towards x = 3, not loaded
        RoomScan unloaded = new RoomScan(world, 1, 1, 1, CAP);
        assertEquals(RoomScan.Outcome.UNLOADED, unloaded.run());
        assertEquals(RoomScan.StopReason.UNLOADED, unloaded.stopReason());
    }
}
