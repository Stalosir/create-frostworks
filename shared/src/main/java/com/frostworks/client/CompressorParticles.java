package com.frostworks.client;

import com.frostworks.content.compressor.CompressorBlock;
import com.frostworks.content.compressor.CompressorGeometry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Air drawn in through the grille of the compressor's four side faces (the faces of the cage around its
 * axis), towards the cogwheel in the middle. Before, the particles came from the back. Same particles as before (cloud
 * or snowflake), one a tick at most, only while it produces (the caller decides).
 * <p>
 * A particle starts just outside an opening of the grille (positions read on our texture, see
 * {@link CompressorGeometry}), flies straight to the centre of the cage and lives just long enough to get there. No
 * particle crosses the black mark (on its face: corner openings, straight in), none on a face against a full block.
 */
public final class CompressorParticles {
    private CompressorParticles() {}

    /** Ticks to reach the centre. */
    private static final int TRAVEL_TICKS = 8;
    /** Starting distance outside the cage face (pixels). */
    private static final float OUTSIDE = 2.5f;

    public static void spawn(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof CompressorBlock))
            return;
        RandomSource r = level.random;
        Direction facing = state.getValue(CompressorBlock.FACING);
        // one of the four sides of the model, then one opening of its grille
        Direction modelSide = Direction.from2DDataValue(r.nextInt(4));
        Direction worldSide = CompressorGeometry.toWorld(modelSide, facing);
        BlockPos next = pos.relative(worldSide);
        if (level.getBlockState(next).isCollisionShapeFullBlock(level, next))
            return; // nothing can be drawn in through a face against a full block
        float along = CompressorGeometry.OPENING_ALONG[r.nextInt(3)];
        float height = CompressorGeometry.OPENING_HEIGHT[r.nextInt(3)];
        boolean markFace = modelSide == Direction.NORTH;
        if (markFace) {
            // the black mark is in the middle of this face: corner openings only, and straight in (a particle flying to
            // the centre from there would cross in front of the mark)
            along = r.nextBoolean() ? 5 : 11;
            height = r.nextBoolean() ? 5.5f : 10.5f;
        }
        float depth = CompressorGeometry.CAGE_INSET - OUTSIDE;
        Vec3 start = switch (modelSide) {
            case NORTH -> new Vec3(along, height, depth);
            case SOUTH -> new Vec3(along, height, 16 - depth);
            case WEST -> new Vec3(depth, height, along);
            default -> new Vec3(16 - depth, height, along); // EAST
        };
        Vec3 from = CompressorGeometry.toBlock(start, facing).add(Vec3.atLowerCornerOf(pos));
        Vec3 to = markFace ? CompressorGeometry.toBlock(new Vec3(along, height, 8), facing).add(Vec3.atLowerCornerOf(pos))
                : Vec3.atCenterOf(pos);
        Vec3 v = to.subtract(from).scale(1.0 / TRAVEL_TICKS);
        Particle p = Minecraft.getInstance().particleEngine.createParticle(
                r.nextBoolean() ? ParticleTypes.CLOUD : ParticleTypes.SNOWFLAKE, from.x, from.y, from.z, v.x, v.y, v.z);
        if (p != null) {
            p.setParticleSpeed(v.x, v.y, v.z); // the particle types damp the speed given at creation: set it as is
            p.setLifetime(TRAVEL_TICKS);
        }
    }
}
