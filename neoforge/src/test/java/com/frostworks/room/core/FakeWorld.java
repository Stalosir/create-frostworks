package com.frostworks.room.core;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/** Test world: a set of wall cells, a loaded box, and a build height. Everything else is air. */
final class FakeWorld implements RoomWorld {
    final LongOpenHashSet walls = new LongOpenHashSet();
    int minY = -64, maxY = 319;
    // Loaded area (inclusive), huge by default
    int loadedMinX = -100_000, loadedMaxX = 100_000, loadedMinZ = -100_000, loadedMaxZ = 100_000;
    long calls;

    @Override
    public Cell cellAt(int x, int y, int z) {
        calls++;
        if (y < minY || y > maxY)
            return Cell.OUTSIDE;
        if (x < loadedMinX || x > loadedMaxX || z < loadedMinZ || z > loadedMaxZ)
            return Cell.UNLOADED;
        return walls.contains(PosKey.pack(x, y, z)) ? Cell.WALL : Cell.PASSABLE;
    }

    void wall(int x, int y, int z) {
        walls.add(PosKey.pack(x, y, z));
    }

    void remove(int x, int y, int z) {
        walls.remove(PosKey.pack(x, y, z));
    }

    /**
     * Builds the shell of a closed box whose interior is {@code sx * sy * sz} cells, interior starting at (x0, y0, z0).
     */
    void shell(int x0, int y0, int z0, int sx, int sy, int sz) {
        for (int x = x0 - 1; x <= x0 + sx; x++)
            for (int y = y0 - 1; y <= y0 + sy; y++)
                for (int z = z0 - 1; z <= z0 + sz; z++) {
                    boolean inside = x >= x0 && x < x0 + sx && y >= y0 && y < y0 + sy && z >= z0 && z < z0 + sz;
                    if (!inside)
                        wall(x, y, z);
                }
    }
}
