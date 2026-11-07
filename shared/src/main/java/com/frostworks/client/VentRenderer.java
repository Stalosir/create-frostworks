package com.frostworks.client;

import com.frostworks.content.vent.VentBlock;
import com.frostworks.content.vent.VentBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/**
 * Classic path (Flywheel off) of the vent's propeller, as {@code EncasedFanRenderer}: cutout, lit by the cell in front
 * of the vent. With a Flywheel backend on, {@link VentVisual} draws it instead.
 */
public class VentRenderer extends SmartBlockEntityRenderer<VentBlockEntity> {

    public VentRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(VentBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
        if (VisualizationManager.supportsVisualization(be.getLevel()))
            return;
        Direction facing = be.getBlockState().getValue(VentBlock.FACING);
        int lightInFront = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().relative(facing));
        ms.pushPose();
        VentPropeller.transform(TransformStack.of(ms), facing, be.propellerAngle(partialTicks));
        // our propeller.json asks for cutout (fan_blades.png: alpha 0 or 255)
        CachedBuffers.partial(VentPropeller.MODEL, be.getBlockState())
                .light(lightInFront)
                .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
        ms.popPose();
    }
}
