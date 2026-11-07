package com.frostworks.client;


import com.frostworks.content.compressor.CompressorBlock;
import com.frostworks.content.compressor.CompressorBlockEntity;
import com.frostworks.content.compressor.CompressorGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;

import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Classic path of the compressor's cogwheel, as Create's {@code PumpRenderer} (skipped when Flywheel
 * draws it, see {@link CompressorCog}); the layer is the block's, cutout. Also, in both paths, the current setting written
 * on the black mark of the cage: light text on black, centred on the mark, turned with the face.
 */
public class CompressorRenderer extends KineticBlockEntityRenderer<CompressorBlockEntity> {

    /** Height of the text on the mark (pixels): the mark is 3.33 pixels high. */
    private static final float TEXT_HEIGHT = 1.6f;
    /** At most this wide (pixels): the cage face is 12 pixels wide. */
    private static final float TEXT_MAX_WIDTH = 5;

    public CompressorRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(CompressorBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing(CompressorCog.MODEL, state);
    }

    @Override
    protected void renderSafe(CompressorBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay); // the cogwheel, unless Flywheel draws it
        if (!VisualizationManager.supportsVisualization(be.getLevel())) // its shaft, lit like it (see CompressorCog)
            standardKineticRotationTransform(CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, be.getBlockState(),
                    be.getBlockState().getValue(CompressorBlock.FACING).getOpposite()), be, light)
                    .renderInto(ms, buffer.getBuffer(RenderType.solid()));
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof CompressorBlock))
            return;
        Direction facing = state.getValue(CompressorBlock.FACING);
        Font font = Minecraft.getInstance().font;
        String text = be.settingText();
        int width = font.width(text);
        float scale = Math.min(TEXT_HEIGHT / font.lineHeight, TEXT_MAX_WIDTH / width) / 16f;

        ms.pushPose();
        // into the model's space (as the blockstate turns the model), then onto the mark, facing out (north, -Z)
        ms.translate(.5, .5, .5);
        ms.mulPose(CompressorGeometry.turn(facing));
        ms.translate(-.5, -.5, -.5);
        ms.translate(CompressorGeometry.MARK_CENTER.x / 16, CompressorGeometry.MARK_CENTER.y / 16,
                CompressorGeometry.MARK_CENTER.z / 16);
        ms.mulPose(Axis.YP.rotationDegrees(180)); // the text faces out of the model's north face
        int lit = LightTexture.FULL_BRIGHT; // readable in the dark, like a lit display
        float left = -width / 2f, top = -font.lineHeight / 2f;
        // no ground of its own: the black mark of the texture is behind it
        ms.translate(0, 0, 0.006);
        ms.scale(scale, -scale, scale);
        font.drawInBatch(text, left, top + 1, 0xFFE8F4FF, false, ms.last().pose(), buffer, Font.DisplayMode.NORMAL, 0,
                lit);
        ms.popPose();
    }
}
