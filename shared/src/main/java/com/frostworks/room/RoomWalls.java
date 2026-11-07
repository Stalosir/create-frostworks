package com.frostworks.room;

import com.frostworks.FwConfig;
import com.frostworks.content.door.InsulatedDoorBlock;
import com.frostworks.content.passage.BeltPassage;
import com.frostworks.content.passage.ShaftPassageBlock;
import com.frostworks.content.vent.VentBlock;
import com.frostworks.registry.FwTags;
import com.frostworks.room.core.RoomScan;
import com.frostworks.room.core.RoomWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** What closes a room: cold walls (tag), insulated doors (open or closed), passages and the vents themselves. */
public final class RoomWalls {
    private RoomWalls() {}

    public static boolean isWall(BlockState state) {
        if (state.is(FwTags.COLD_WALLS))
            return true;
        Block block = state.getBlock();
        return block instanceof InsulatedDoorBlock || block instanceof VentBlock || block instanceof ShaftPassageBlock
                || block instanceof com.frostworks.content.passage.PipePassageBlock;
    }

    /** With the position, the belt passages too (a Create belt encased by a cold wall). */
    public static boolean isWall(BlockGetter level, BlockPos pos, BlockState state) {
        return isWall(state) || BeltPassage.is(level, pos, state);
    }

    /**
     * Would a block placed at {@code pos} close a room on its {@code side}? Searches the cells from
     * {@code pos.relative(side)}, with {@code pos} counted as a wall (the block is not placed yet). Works on the client
     * too (block placement), never loads a chunk.
     */
    public static boolean closesARoom(LevelReader level, BlockPos pos, Direction side) {
        BlockPos start = pos.relative(side);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        RoomWorld world = (x, y, z) -> {
            if (x == pos.getX() && y == pos.getY() && z == pos.getZ())
                return RoomWorld.Cell.WALL;
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight())
                return RoomWorld.Cell.OUTSIDE;
            if (!level.hasChunk(x >> 4, z >> 4))
                return RoomWorld.Cell.UNLOADED;
            return isWall(level, cursor.set(x, y, z), level.getBlockState(cursor)) ? RoomWorld.Cell.WALL : RoomWorld.Cell.PASSABLE;
        };
        RoomScan scan = new RoomScan(world, start.getX(), start.getY(), start.getZ(),
                FwConfig.get(FwConfig.ROOM_SEARCH_CAP));
        return scan.run() == RoomScan.Outcome.CLOSED && scan.volume() > 0;
    }
}
