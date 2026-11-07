package com.frostworks.client;

import org.joml.Quaternionf;

import com.frostworks.Frostworks;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.Affine;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * The vent's propeller, our {@code models/block/vent/propeller.json}, drawn apart from the
 * block like Create draws its fan ({@code EncasedFanRenderer}, {@code FanVisual}).
 * <p>
 * The model is Create's fan propeller: drawn with its back towards south (+z), blades in an x-y plane, turning about
 * z. Like Create's fan, it is turned so that south points to the back of the vent (its pipe side), then spun about
 * its own z axis. The same transform serves the Flywheel visual and the classic renderer.
 */
public final class VentPropeller {
    private VentPropeller() {}

    public static final PartialModel MODEL = PartialModel.of(Frostworks.id("block/vent/propeller"));

    /** Rotation that takes the model's south (+z) to the back of a vent facing {@code facing}. */
    public static Quaternionf orientation(Direction facing) {
        Direction back = facing.getOpposite();
        return new Quaternionf().rotationTo(0, 0, 1, back.getStepX(), back.getStepY(), back.getStepZ());
    }

    /** Places the propeller in its block (block corner at the origin) and turns it by {@code angle} degrees. */
    public static <T extends Affine<T>> T transform(T t, Direction facing, float angle) {
        return t.translate(.5f, .5f, .5f)
                .rotate(orientation(facing))
                .rotateZ(angle * Mth.DEG_TO_RAD)
                .translate(-.5f, -.5f, -.5f);
    }
}
