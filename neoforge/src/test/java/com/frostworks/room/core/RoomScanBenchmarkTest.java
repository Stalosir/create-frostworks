package com.frostworks.room.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Cost of a scan of the pure algorithm (no Minecraft world access). The in-game cost, with real block lookups, is
 * measured by the GameTest {@code scan_benchmark}. Results go to the report.
 */
class RoomScanBenchmarkTest {

    @Test
    void measure() {
        int[][] rooms = {{5, 4, 5}, {10, 10, 10}, {50, 20, 20}}; // 100, 1 000, 20 000 cells
        for (int[] r : rooms) {
            FakeWorld world = new FakeWorld();
            world.shell(0, 0, 0, r[0], r[1], r[2]);
            int expected = r[0] * r[1] * r[2];
            // warm-up
            for (int i = 0; i < 20; i++)
                new RoomScan(world, 0, 0, 0, 20_000).run();
            int runs = 50;
            long best = Long.MAX_VALUE, total = 0;
            for (int i = 0; i < runs; i++) {
                long t0 = System.nanoTime();
                RoomScan scan = new RoomScan(world, 0, 0, 0, 20_000);
                scan.run();
                long dt = System.nanoTime() - t0;
                assertEquals(expected, scan.volume());
                best = Math.min(best, dt);
                total += dt;
            }
            System.out.printf("BENCH pure scan %6d cells: best %.3f ms, mean %.3f ms%n",
                    expected, best / 1e6, total / 1e6 / runs);
        }
    }
}
