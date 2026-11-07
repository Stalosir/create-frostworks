package com.frostworks.content.wall;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Vector3f;

import com.frostworks.platform.Mc;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A dye on a cold wall colours the whole structure: every cold wall joined to it face to face, found from block to
 * block. Not the room: two rooms sharing a wall, or a wall belonging to none, are coloured alike. Walls in unloaded
 * chunks are left out.
 * <p>
 * The colour spreads as a wave: the clicked wall at once, then every {@link #TICKS_PER_STEP} ticks the walls one step
 * further (across a face), each with a puff of dye-coloured dust. The waves are kept in memory only: a server stopped
 * in the middle of one leaves the rest of the structure as it was.
 */
public final class WallTinting {
    private WallTinting() {}

    /** Ticks between one ring of the wave and the next. */
    public static final int TICKS_PER_STEP = 2;
    /** Dust particles on each wall the wave reaches (one packet per wall). */
    static final int PARTICLES_PER_WALL = 2;

    public enum Outcome {
        /** The structure takes the colour (all of it at once on the client's side, ring by ring on the server's). */
        DYED,
        /** It already has it everywhere, or a wave of that colour is already on its way through it: nothing done. */
        UNCHANGED,
        /** More walls than the limit: nothing done. */
        TOO_BIG
    }

    /** A wave on its way: the rings still to colour, the next one's tick. */
    private static final class Wave {
        final WallTint tint;
        final List<List<BlockPos>> rings;
        final LongOpenHashSet walls = new LongOpenHashSet();
        int next = 1;
        long nextTick;

        Wave(WallTint tint, List<List<BlockPos>> rings, long now) {
            this.tint = tint;
            this.rings = rings;
            this.nextTick = now + TICKS_PER_STEP;
            for (List<BlockPos> ring : rings)
                for (BlockPos p : ring)
                    walls.add(p.asLong());
        }
    }

    /** The waves of each server level (server thread only). */
    private static final Map<Level, List<Wave>> WAVES = new WeakHashMap<>();

    /**
     * The walls of the structure from {@code start}, ring by ring (ring n: n faces away from the start), or null if
     * there are more than {@code max}.
     */
    public static List<List<BlockPos>> rings(Level level, BlockPos start, int max) {
        List<List<BlockPos>> rings = new ArrayList<>();
        LongOpenHashSet seen = new LongOpenHashSet();
        seen.add(start.asLong());
        List<BlockPos> ring = List.of(start);
        int count = 0;
        while (!ring.isEmpty()) {
            rings.add(ring);
            count += ring.size();
            if (count > max)
                return null;
            List<BlockPos> next = new ArrayList<>();
            for (BlockPos pos : ring)
                for (Direction d : Direction.values()) {
                    BlockPos n = pos.relative(d);
                    if (seen.add(n.asLong()) && level.isLoaded(n) && level.getBlockState(n).getBlock() instanceof ColdWallBlock)
                        next.add(n);
                }
            ring = next;
        }
        return rings;
    }

    /** The walls of the structure from {@code start}, or null if there are more than {@code max}. */
    public static List<BlockPos> structure(Level level, BlockPos start, int max) {
        List<List<BlockPos>> rings = rings(level, start, max);
        if (rings == null)
            return null;
        List<BlockPos> walls = new ArrayList<>();
        rings.forEach(walls::addAll);
        return walls;
    }

    /**
     * Colours the structure from {@code start}: on the server, the clicked wall now and a wave for the rest; the client
     * only works out the outcome. The blocks are changed for the clients only, without neighbour updates: the rooms,
     * the overspeed and the connected textures see the same cold walls as before.
     */
    public static Outcome dye(Level level, BlockPos start, WallTint tint, int max) {
        List<List<BlockPos>> rings = rings(level, start, max);
        if (rings == null)
            return Outcome.TOO_BIG;
        boolean change = false;
        for (List<BlockPos> ring : rings)
            for (BlockPos pos : ring)
                change |= level.getBlockState(pos).getValue(ColdWallBlock.TINT) != tint;
        if (!(level instanceof ServerLevel server))
            return change ? Outcome.DYED : Outcome.UNCHANGED; // the client: the waves are the server's only
        List<Wave> waves = WAVES.computeIfAbsent(level, l -> new ArrayList<>());
        for (Wave w : waves)
            if (w.walls.contains(start.asLong()) && w.tint == tint)
                return Outcome.UNCHANGED; // already on its way
        if (!change)
            return Outcome.UNCHANGED;
        // a new colour on a structure being coloured: the older wave stops where it is
        waves.removeIf(w -> w.walls.contains(start.asLong()));
        colour(server, rings.get(0), tint);
        if (rings.size() > 1)
            waves.add(new Wave(tint, rings, level.getGameTime()));
        return Outcome.DYED;
    }

    /** Server, every tick: the waves move on one ring when their time comes. */
    public static void tick(ServerLevel level) {
        List<Wave> waves = WAVES.get(level);
        if (waves == null || waves.isEmpty())
            return;
        long now = level.getGameTime();
        for (Iterator<Wave> it = waves.iterator(); it.hasNext(); ) {
            Wave w = it.next();
            if (now < w.nextTick)
                continue;
            colour(level, w.rings.get(w.next), w.tint);
            w.next++;
            w.nextTick = now + TICKS_PER_STEP;
            if (w.next >= w.rings.size())
                it.remove();
        }
    }

    /** Is a wave still on its way through this wall? (tests) */
    public static boolean waveThrough(Level level, BlockPos pos) {
        List<Wave> waves = WAVES.get(level);
        if (waves != null)
            for (Wave w : waves)
                if (w.walls.contains(pos.asLong()))
                    return true;
        return false;
    }

    /** One ring: the walls still there take the colour, with their dust. */
    private static void colour(ServerLevel level, List<BlockPos> ring, WallTint tint) {
        int rgb = Mc.dyeRgb(tint.dye == null ? DyeColor.GRAY : tint.dye);
        DustParticleOptions dust = new DustParticleOptions(
                new Vector3f((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f), 1);
        for (BlockPos pos : ring) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof ColdWallBlock) || state.getValue(ColdWallBlock.TINT) == tint)
                continue;
            level.setBlock(pos, state.setValue(ColdWallBlock.TINT, tint), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            puff(level, pos, dust);
        }
    }

    /** A little dust on one face of the wall that can be seen (none for a wall buried on all six sides). */
    private static void puff(ServerLevel level, BlockPos pos, DustParticleOptions dust) {
        List<Direction> open = new ArrayList<>(6);
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (!level.getBlockState(n).isSolidRender(level, n))
                open.add(d);
        }
        if (open.isEmpty())
            return;
        Direction face = open.get(level.random.nextInt(open.size()));
        double x = pos.getX() + 0.5 + face.getStepX() * 0.6, y = pos.getY() + 0.5 + face.getStepY() * 0.6,
                z = pos.getZ() + 0.5 + face.getStepZ() * 0.6;
        // spread over the face, not away from it
        double sx = face.getAxis() == Direction.Axis.X ? 0 : 0.25, sy = face.getAxis() == Direction.Axis.Y ? 0 : 0.25,
                sz = face.getAxis() == Direction.Axis.Z ? 0 : 0.25;
        level.sendParticles(dust, x, y, z, PARTICLES_PER_WALL, sx, sy, sz, 0);
    }
}
