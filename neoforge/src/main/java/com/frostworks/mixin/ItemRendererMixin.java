package com.frostworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.frostworks.client.ChilledOverlay;
import com.frostworks.content.cold.ChilledMark;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * A chilled stack ({@link ChilledMark}) gets the frost layer drawn over it, wherever the item is drawn: in slots,
 * on the ground, on a belt or a depot, in a hand. After the item, the same call again with the layer's model, a hair
 * bigger for a block, on the very faces for a flat item. NeoForge 1.21.1: official names in dev and in a real game.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    /** While the layer itself is drawn (the same method, called again). */
    @Unique
    private static boolean frostworks$drawingFrost;

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void frostworks$chilledLayer(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack pose,
                                        MultiBufferSource buffers, int light, int overlay, BakedModel model,
                                        CallbackInfo ci) {
        if (frostworks$drawingFrost || !ChilledMark.is(stack) || model.isCustomRenderer())
            return;
        BakedModel frost = ChilledOverlay.layerFor(model);
        if (frost == null)
            return;
        frostworks$drawingFrost = true;
        pose.pushPose();
        try {
            // the item is drawn now, so that its layer comes after it, never under it. Seen in game: a GregTech raw
            // ore, in another buffer than the layer, was drawn over it; a block drawn after its frost cube (which
            // writes depth) was hidden behind it, the world showing through the frost
            if (buffers instanceof MultiBufferSource.BufferSource source)
                source.endBatch();
            // a cube a hair bigger, over the block's faces; a flat item's layer on its very faces (same depth: it
            // passes when drawn after the item)
            if (model.isGui3d())
                pose.scale(1.01f, 1.01f, 1.01f);
            ((ItemRenderer) (Object) this).render(stack, context, leftHand, pose, buffers, light, overlay, frost);
        } finally {
            pose.popPose();
            frostworks$drawingFrost = false;
        }
    }
}
