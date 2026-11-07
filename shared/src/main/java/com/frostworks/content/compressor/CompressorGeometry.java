package com.frostworks.content.compressor;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Where things are on our compressor model ({@code models/block/compressor/block.json}), and
 * how its model space turns into the world for each {@code facing}.
 * <p>
 * The model is drawn with the gas outlet up (+Y) and the rotation input down (-Y). The blockstate turns it like
 * Create's pump: facing up as drawn, down x180, north x90, south x90 y180, east x90 y90, west x90 y270. Minecraft
 * applies the x turn first, then the y turn, both clockwise seen from the positive axis end, so a model vector v goes
 * to Ry(-y) Rx(-x) v.
 * <p>
 * Measured on the model and its texture ({@code textures/block/compressor/compressor.png}, 32 x 32):
 * <ul>
 * <li>the cage (third element) spans x 2..14, y 3..13, z 2..14 (pixels);</li>
 * <li>the black mark of the setting is on its north face (z = 2), UV 9.5..15.5 x 6.5..12.5 there, black texture pixels
 * x 23..26, y 17..20: in the model x 6..10, y 6.33..9.67, centre (8, 8, 2), 4 x 3.33 pixels;</li>
 * <li>the openings of the grille on the other three faces, centres along the face 5, 8, 11 and height 5.5, 8, 10.5
 * (texture holes 2 x 2 pixels, rows of the side texture at v 0..6 stretched over the 10 pixels of the cage).</li>
 * </ul>
 */
public final class CompressorGeometry {
    private CompressorGeometry() {}

    /** Centre of the black mark in the model, pixels. */
    public static final Vec3 MARK_CENTER = new Vec3(8, 8, 2);
    public static final float MARK_WIDTH = 4, MARK_HEIGHT = 10 / 3f;
    /** The cage's faces are 2 pixels in from the block's faces. */
    public static final float CAGE_INSET = 2;
    /** Openings of the grille on a side face: positions along the face and heights (pixels). */
    public static final float[] OPENING_ALONG = {5, 8, 11};
    public static final float[] OPENING_HEIGHT = {5.5f, 8, 10.5f};

    /** Blockstate rotation (x, y in degrees) of the model for a facing, as in blockstates/compressor.json. */
    public static int[] rotation(Direction facing) {
        return switch (facing) {
            case UP -> new int[]{0, 0};
            case DOWN -> new int[]{180, 0};
            case NORTH -> new int[]{270, 180};
            case SOUTH -> new int[]{270, 0};
            case EAST -> new int[]{270, 270};
            case WEST -> new int[]{270, 90};
        };
    }

    /** The turn from model space to the world for a facing (about the block centre). */
    public static Quaternionf turn(Direction facing) {
        int[] r = rotation(facing);
        return new Quaternionf().rotationY((float) Math.toRadians(-r[1])).rotateX((float) Math.toRadians(-r[0]));
    }

    /** A point of the model (pixels) in the block (0..1), for a facing. */
    public static Vec3 toBlock(Vec3 modelPixels, Direction facing) {
        Vector3f v = new Vector3f((float) modelPixels.x / 16 - .5f, (float) modelPixels.y / 16 - .5f,
                (float) modelPixels.z / 16 - .5f);
        turn(facing).transform(v);
        return new Vec3(v.x + .5, v.y + .5, v.z + .5);
    }

    /** A face of the model turned into the world for a facing. */
    public static Direction toWorld(Direction modelFace, Direction facing) {
        Vector3f v = new Vector3f(modelFace.getStepX(), modelFace.getStepY(), modelFace.getStepZ());
        turn(facing).transform(v);
        return Direction.getNearest(v.x, v.y, v.z);
    }

    /** The face that carries the black mark, in the world. */
    public static Direction markFace(Direction facing) {
        return toWorld(Direction.NORTH, facing);
    }
}
