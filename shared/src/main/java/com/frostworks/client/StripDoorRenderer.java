package com.frostworks.client;

import com.frostworks.Frostworks;
import com.frostworks.FwClientConfig;
import com.frostworks.content.door.InsulatedDoorBlock;
import com.frostworks.content.door.StripDoorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.logistics.FlapStuffs;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the four strips of the strip curtain door with our {@code door_flap.json}, like Create draws the
 * flaps of its funnels ({@code FunnelRenderer} + {@code FlapStuffs}): one partial model, drawn four times, each turned
 * about its top edge by an angle from {@code FlapStuffs.flapAngle}.
 * <p>
 * Geometry read from our models (pixels, model facing north):
 * <ul>
 * <li>opening of the body: x 1.95 to 14 (12.05 px), up to y 28.4;</li>
 * <li>{@code door_flap.json} is the strip "F4": x 11.075 to 13.825 (2.75 px wide), y 1 to 29.05, z 7.95 to 8.95;</li>
 * <li>the four strips are side by side, F4 as drawn and the others shifted by 2.75 px each towards -x (F1 starts at
 * 2.825); they cover 11 of the 12.05 px: 0.875 px of gap on the left, 0.175 px on the right;</li>
 * <li>pivot (hinge): the top edge of the strip, y = 29.05, z = 8.45 (centre of its thickness).</li>
 * </ul>
 * Our {@code door_flap.png} is translucent (alpha 142 on every pixel), so the strips are drawn
 * with {@link RenderType#translucent}, as Create does for its translucent partials ({@code LinkBulbRenderer},
 * {@code FactoryPanelRenderer}). The cutout type used before only keeps the alpha test (a pixel is drawn or not): the
 * strips came out opaque. The translucent type is sorted back to front each frame, so a strip behind another one still
 * shows. The door has no Flywheel visual (as Create's link bulb): this renderer draws the strips whatever the Flywheel
 * backend. The swing is scaled by {@code flapSwingFactor} (client config), read at each frame.
 */
public class StripDoorRenderer extends SmartBlockEntityRenderer<StripDoorBlockEntity> {

    /** Our strip, loaded as a Flywheel partial model (like Create's {@code AllPartialModels.FUNNEL_FLAP}). */
    public static final PartialModel FLAP = PartialModel.of(Frostworks.id("block/door_flap"));

    public static final int STRIPS = 4;
    public static final float STRIP_STEP = -2.75f / 16f;
    public static final Vec3 PIVOT = new Vec3(0, 29.05 / 16, 8.45 / 16);
    /** Peak of {@code FlapStuffs.flapAngle(f, 0) / 30} over f: scales Create's curve to the wanted amplitude. */
    private static final float CREATE_PEAK = 0.58f;

    public StripDoorRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    /** Angle of one strip, in degrees, for a flap value f in [-1, 1] (sign = direction of travel). */
    public static float stripAngle(float f, int strip, float amplitude) {
        float createAngle = FlapStuffs.flapAngle(Math.abs(f), strip); // Create's curve, 30 degrees scale
        return Math.signum(f) * createAngle / 30f / CREATE_PEAK * amplitude;
    }

    @Override
    protected void renderSafe(StripDoorBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof InsulatedDoorBlock))
            return;
        Direction facing = state.getValue(InsulatedDoorBlock.FACING);
        float yRot = switch (facing) { // same turn as the blockstate (degrees, clockwise seen from above)
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        float f = be.flap.getValue(partialTicks);
        VertexConsumer vb = buffer.getBuffer(RenderType.translucent());
        SuperByteBuffer strip = CachedBuffers.partial(FLAP, state);
        float amplitude = be.amplitude() * (float) FwClientConfig.flapSwingFactor();

        var msr = TransformStack.of(ms);
        ms.pushPose();
        msr.center().rotateYDegrees(-yRot).uncenter();
        for (int i = 0; i < STRIPS; i++) {
            ms.pushPose();
            // F4 (i = 3) where the model draws it, the others shifted to its left
            ms.translate((STRIPS - 1 - i) * STRIP_STEP, 0, 0);
            msr.translate(PIVOT).rotateXDegrees(stripAngle(f, i, amplitude)).translateBack(PIVOT);
            strip.light(light).renderInto(ms, vb);
            ms.popPose();
        }
        ms.popPose();
    }
}
