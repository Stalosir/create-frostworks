package com.frostworks.client;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.frostworks.Frostworks;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * The frost layer drawn over a chilled stack ({@code ChilledMark}), whatever its item, with our textures,
 * translucent. Drawn by {@code ItemRendererMixin} wherever the item is drawn (slots, the ground, belts, depots, hands).
 * <ul>
 * <li>A block: the frost cube ({@code block/chilled_overlay}), a hair bigger than the block.</li>
 * <li>A flat item: the frost sprite cut to the item's own outline. Built once per item model: a small square of frost
 * on each pixel where the item's sprites (all its layers) are opaque and the frost is not transparent, on its front and
 * back faces, with the item's own transforms. No edges stick out, and the quads carry no tint index: the item's colour
 * handler (GregTech colours its raw ores by material) never touches the frost.</li>
 * </ul>
 * NeoForge's {@link BakedModelWrapper}, like Forge's, hands back the wrapped model when the item is placed for display
 * ({@code applyTransform}): the layer overrides it to stay itself, or the game would draw the wrapped quads instead.
 */
public final class ChilledOverlay {
    private ChilledOverlay() {}

    public static final ResourceLocation BLOCK = Frostworks.id("block/chilled_overlay");
    public static final ResourceLocation ITEM = Frostworks.id("item/chilled_overlay");
    private static final ResourceLocation ITEM_TEXTURE = Frostworks.id("item/ore/raw_frigorifie_texture");

    /** The frost layer of each flat item model, built on first use; emptied after a resource reload. */
    private static final Map<BakedModel, BakedModel> FLAT = new IdentityHashMap<>();
    private static BakedModel itemLayerModel;

    public static void register(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(BLOCK));
        event.register(ModelResourceLocation.standalone(ITEM));
    }

    /** The layer for this item model (drawn as a block or as a flat item); null if the layer did not load. */
    @Nullable
    public static BakedModel layerFor(BakedModel itemModel) {
        var models = Minecraft.getInstance().getModelManager();
        if (itemModel.isGui3d()) {
            BakedModel block = models.getModel(ModelResourceLocation.standalone(BLOCK));
            return block == models.getMissingModel() ? null : block;
        }
        BakedModel layer = models.getModel(ModelResourceLocation.standalone(ITEM));
        if (layer == models.getMissingModel())
            return null;
        if (layer != itemLayerModel) { // a resource reload: new models, new sprites
            itemLayerModel = layer;
            FLAT.clear();
        }
        return FLAT.computeIfAbsent(itemModel, model -> new Cutout(model, layer, frostSprite()));
    }

    private static TextureAtlasSprite frostSprite() {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ITEM_TEXTURE);
    }

    /** The frost sprite on an item's outline: the item's transforms, the layer's render type, quads of its own. */
    private static final class Cutout extends BakedModelWrapper<BakedModel> {
        /** How far the frost stands off the item's faces (item units: a sixteenth of a pixel, unseen from the side). */
        private static final float LIFT = 1f / 16 / 16;

        private final BakedModel layer;
        private final List<BakedQuad> quads;

        Cutout(BakedModel item, BakedModel layer, TextureAtlasSprite frost) {
            super(item);
            this.layer = layer;
            this.quads = build(item, frost);
        }

        /** Each opaque pixel of the item that has frost on it: one square on the front, one on the back. */
        private static List<BakedQuad> build(BakedModel item, TextureAtlasSprite frost) {
            // the item's sprites: those of its front and back faces (item/generated: one per layer)
            Set<TextureAtlasSprite> sprites = new LinkedHashSet<>();
            for (BakedQuad q : item.getQuads(null, null, RandomSource.create(42)))
                if (q.getDirection().getAxis() == Direction.Axis.Z)
                    sprites.add(q.getSprite());
            int w = 16, h = 16;
            for (TextureAtlasSprite s : sprites) { // the finest grid of the item's layers
                w = Math.max(w, s.contents().width());
                h = Math.max(h, s.contents().height());
            }
            int fw = frost.contents().width(), fh = frost.contents().height();
            List<BakedQuad> out = new ArrayList<>();
            for (int py = 0; py < h; py++)
                for (int px = 0; px < w; px++) {
                    int fx = px * fw / w, fy = py * fh / h;
                    if (frost.contents().isTransparent(0, fx, fy) || !opaque(sprites, px, py, w, h))
                        continue;
                    float x0 = (float) px / w, x1 = (float) (px + 1) / w;
                    float y1 = 1 - (float) py / h, y0 = 1 - (float) (py + 1) / h;
                    float u0 = frost.getU((float) fx / fw), u1 = frost.getU((float) (fx + 1) / fw); // 1.21: 0 to 1
                    float v0 = frost.getV((float) fy / fh), v1 = frost.getV((float) (fy + 1) / fh);
                    // item/generated's faces: the front at z 8.5, the back at z 7.5 (same pixel, seen from behind),
                    // each a hair outside the item's own (on the same plane, the two fought as the camera moved)
                    out.add(quad(Direction.SOUTH, frost, 8.5f / 16 + LIFT,
                            x0, y1, u0, v0, x0, y0, u0, v1, x1, y0, u1, v1, x1, y1, u1, v0));
                    out.add(quad(Direction.NORTH, frost, 7.5f / 16 - LIFT,
                            x1, y1, u1, v0, x1, y0, u1, v1, x0, y0, u0, v1, x0, y1, u0, v0));
                }
            return out;
        }

        /** Is the item opaque at this pixel of its grid, in any of its layers? No sprite known: everywhere. */
        private static boolean opaque(Set<TextureAtlasSprite> sprites, int px, int py, int w, int h) {
            if (sprites.isEmpty())
                return true;
            for (TextureAtlasSprite s : sprites) {
                int sx = px * s.contents().width() / w, sy = py * s.contents().height() / h;
                if (!s.contents().isTransparent(0, sx, sy))
                    return true;
            }
            return false;
        }

        /** One square, four corners (x, y, u, v) in the order the game bakes this face, white, no tint. */
        private static BakedQuad quad(Direction face, TextureAtlasSprite sprite, float z, float... c) {
            int[] data = new int[32];
            int normal = (face.getStepZ() * 127 & 0xFF) << 16;
            for (int i = 0; i < 4; i++) {
                int o = i * 8;
                data[o] = Float.floatToRawIntBits(c[i * 4]);
                data[o + 1] = Float.floatToRawIntBits(c[i * 4 + 1]);
                data[o + 2] = Float.floatToRawIntBits(z);
                data[o + 3] = -1; // white
                data[o + 4] = Float.floatToRawIntBits(c[i * 4 + 2]);
                data[o + 5] = Float.floatToRawIntBits(c[i * 4 + 3]);
                data[o + 6] = 0;
                data[o + 7] = normal;
            }
            return new BakedQuad(data, -1, face, sprite, true);
        }

        /** Places the pose as the item does, and stays itself (the wrapper's own would hand back the item). */
        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
            originalModel.applyTransform(context, pose, leftHand);
            return this;
        }

        @Override
        public @NotNull List<BakedModel> getRenderPasses(@NotNull ItemStack stack, boolean fabulous) {
            return List.of(this);
        }

        /** The layer's render type (translucent), not the item's. */
        @Override
        public @NotNull List<RenderType> getRenderTypes(@NotNull ItemStack stack, boolean fabulous) {
            return layer.getRenderTypes(stack, fabulous);
        }

        @Override
        public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                                 @NotNull RandomSource rand) {
            return side == null ? quads : List.of();
        }

        @Override
        public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                                 @NotNull RandomSource rand, @NotNull ModelData data,
                                                 @Nullable RenderType renderType) {
            return getQuads(state, side, rand);
        }
    }
}
