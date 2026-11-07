package com.frostworks.client;

import com.frostworks.Frostworks;
import com.frostworks.content.expander.ExpanderBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;

import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The shaft through the expander, as Create's shaft renderer and visual draw it, but from our own copy
 * of the shaft model ({@code models/block/expander/shaft.json}: Create's shaft with the textures copied into
 * {@code textures/block/expander/}), so they can be retouched.
 */
public class ExpanderShaftRenderer extends KineticBlockEntityRenderer<ExpanderBlockEntity> {

    /** Create's shaft model (along Y) on our textures. */
    public static final PartialModel SHAFT = PartialModel.of(Frostworks.id("block/expander/shaft"));

    public ExpanderShaftRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(ExpanderBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacingVertical(SHAFT, state,
                Direction.fromAxisAndDirection(getRotationAxisOf(be), Direction.AxisDirection.POSITIVE));
    }

    /** Flywheel path, as Create's {@code SingleAxisRotatingVisual.shaft}. */
    public static SimpleBlockEntityVisualizer.Factory<ExpanderBlockEntity> visual() {
        return (context, be, partialTick) -> new SingleAxisRotatingVisual<>(context, be, partialTick,
                Models.partial(SHAFT));
    }
}
