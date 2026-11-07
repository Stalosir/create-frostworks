package com.frostworks.room;

import com.frostworks.room.core.RoomWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * {@link RoomWorld} over a server level. Never loads a chunk: a cell in a chunk that is not loaded is
 * {@link Cell#UNLOADED}. Caches the last chunk, since a flood fill stays in the same chunk most of the time.
 */
public final class LevelRoomWorld implements RoomWorld {
    private final ServerLevel level;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private LevelChunk chunk;
    private int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;

    public LevelRoomWorld(ServerLevel level) {
        this.level = level;
    }

    /** Forgets the cached chunk: call once per tick, a chunk may have been loaded or unloaded since. */
    public void resetCache() {
        chunk = null;
        chunkX = chunkZ = Integer.MIN_VALUE;
    }

    @Override
    public Cell cellAt(int x, int y, int z) {
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight())
            return Cell.OUTSIDE;
        int cx = x >> 4, cz = z >> 4;
        if (cx != chunkX || cz != chunkZ) {
            chunk = level.getChunkSource().getChunkNow(cx, cz);
            chunkX = cx;
            chunkZ = cz;
        }
        if (chunk == null)
            return Cell.UNLOADED;
        return RoomWalls.isWall(chunk, cursor.set(x, y, z), chunk.getBlockState(cursor)) ? Cell.WALL : Cell.PASSABLE;
    }
}
