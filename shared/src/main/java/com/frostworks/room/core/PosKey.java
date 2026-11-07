package com.frostworks.room.core;

/**
 * Packs block coordinates into a long, with the same layout as Minecraft's BlockPos.asLong
 * (26 bits x, 26 bits z, 12 bits y), so keys can be exchanged with the game without conversion.
 * Pure Java: no Minecraft class here.
 */
public final class PosKey {
    private static final int XZ_BITS = 26;
    private static final int Y_BITS = 12;
    private static final long XZ_MASK = (1L << XZ_BITS) - 1;
    private static final long Y_MASK = (1L << Y_BITS) - 1;
    private static final int X_OFFSET = Y_BITS + XZ_BITS;
    private static final int Z_OFFSET = Y_BITS;

    private PosKey() {}

    public static long pack(int x, int y, int z) {
        return ((x & XZ_MASK) << X_OFFSET) | ((z & XZ_MASK) << Z_OFFSET) | (y & Y_MASK);
    }

    public static int x(long key) {
        return (int) (key << (64 - X_OFFSET - XZ_BITS) >> (64 - XZ_BITS));
    }

    public static int y(long key) {
        return (int) (key << (64 - Y_BITS) >> (64 - Y_BITS));
    }

    public static int z(long key) {
        return (int) (key << (64 - Z_OFFSET - XZ_BITS) >> (64 - XZ_BITS));
    }

    public static long offset(long key, int dx, int dy, int dz) {
        return pack(x(key) + dx, y(key) + dy, z(key) + dz);
    }

    /** The six face neighbours, in the order down, up, north, south, west, east. */
    public static final int[][] NEIGHBOURS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };
}
