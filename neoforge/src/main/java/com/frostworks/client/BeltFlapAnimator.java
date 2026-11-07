package com.frostworks.client;

import com.frostworks.FwClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * The top strip of a flat belt passage, drawn every frame so it can swing: as the door's strips, it is thrown to 1
 * (sign = way the item goes) when an item comes into the segment, and settles back by 12 % a tick, with the door's
 * amplitude (45 degrees scaled by {@code flapSwingFactor}).
 */
public final class BeltFlapAnimator {
    private BeltFlapAnimator() {}

    private static final class Flap {
        final LerpedFloat value = LerpedFloat.linear().startWithValue(0).chase(0, .12f, Chaser.EXP);
        boolean itemUnder;
    }

    private static final Long2ObjectOpenHashMap<Flap> FLAPS = new Long2ObjectOpenHashMap<>();
    /** Where our strip hangs from, as the door's (the top of the block, mid-depth). */
    private static final Vec3 PIVOT = new Vec3(0, 16 / 16.0, 8.45 / 16);

    public static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            FLAPS.clear();
            return;
        }
        FLAPS.keySet().removeIf(key -> !BeltPassageModel.hasTopFlap(level, BlockPos.of(key)));
        for (long key : BeltPassageModel.passages()) {
            BlockPos pos = BlockPos.of(key);
            if (!BeltPassageModel.hasTopFlap(level, pos) || !(level.getBlockEntity(pos) instanceof BeltBlockEntity be))
                continue;
            Flap flap = FLAPS.computeIfAbsent(key, k -> new Flap());
            flap.value.tickChaser();
            BeltBlockEntity controller = be.getControllerBE();
            boolean under = false;
            if (controller != null)
                for (TransportedItemStack item : controller.getInventory().getTransportedItems())
                    if (item.beltPosition >= be.index && item.beltPosition < be.index + 1)
                        under = true;
            if (under && !flap.itemUnder)
                flap.value.setValue(Math.signum(be.getDirectionAwareBeltMovementSpeed()) >= 0 ? 1 : -1);
            flap.itemUnder = under;
        }
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || FLAPS.isEmpty())
            return;
        ClientLevel level = Minecraft.getInstance().level;
        BakedModel model = BeltPassageModel.flapTop();
        if (level == null || model == null)
            return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ms = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vb = buffers.getBuffer(RenderType.translucent());
        float amplitude = 45 * (float) FwClientConfig.flapSwingFactor();
        for (var entry : FLAPS.long2ObjectEntrySet()) {
            BlockPos pos = BlockPos.of(entry.getLongKey());
            var state = level.getBlockState(pos);
            if (!BeltPassageModel.hasTopFlap(level, pos))
                continue; // gone since the last tick
            int light = LevelRenderer.getLightColor(level, pos); // its own block: the wall above, opaque, holds no light
            ms.pushPose();
            ms.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
            // the strip stands across a belt along Z: turned for a belt along X, then tilted about its top edge
            ms.translate(.5, 0, .5);
            if (state.getValue(com.simibubi.create.content.kinetics.belt.BeltBlock.HORIZONTAL_FACING).getAxis()
                    == Direction.Axis.X)
                ms.mulPose(Axis.YP.rotationDegrees(90));
            ms.translate(-.5, 0, -.5);
            ms.translate(PIVOT.x, PIVOT.y, PIVOT.z);
            ms.mulPose(Axis.XP.rotationDegrees(
                    StripDoorRenderer.stripAngle(entry.getValue().value.getValue(pt), StripDoorRenderer.STRIPS - 1,
                            amplitude)));
            ms.translate(-PIVOT.x, -PIVOT.y, -PIVOT.z);
            for (BakedQuad quad : model.getQuads(state, null, RandomSource.create(42), ModelData.EMPTY, null))
                vb.putBulkData(ms.last(), quad, 1, 1, 1, 1, light, OverlayTexture.NO_OVERLAY);
            ms.popPose();
        }
        buffers.endBatch(RenderType.translucent());
    }
}
