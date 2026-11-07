package com.frostworks.client;

import com.frostworks.content.column.TowerBlock;
import com.frostworks.content.column.TowerBlockEntity;
import com.frostworks.registry.FwFluids;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The distillation tower:
 * <ul>
 * <li>on a base block of a valid tower, Create's half shaft on each outer side where a kinetic block offers a shaft,
 * turning with the tower (lit at the base itself, which lets light through);</li>
 * <li>on the controller, the content seen through the windows of the column: the product (Frosium) at the bottom, the liquids it is made of above it, cold gas hanging from the top, each as high as its share of the
 * tower's room.</li>
 * </ul>
 */
public class TowerRenderer extends SafeBlockEntityRenderer<TowerBlockEntity> {

    /** Fluid kept inside the walls (blocks): our column walls stand 3 to 4 px in from the outer edge. */
    private static final float INSET = 4.1f / 16, FLOOR = 1 / 16f;

    public TowerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(TowerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light,
                              int overlay) {
        BlockState state = be.getBlockState();
        if (TowerBlock.isBase(state))
            shafts(be, state, ms, buffer, light);
        if (be.isController() && be.isValid())
            content(be, ms, buffer, light);
    }

    private static void shafts(TowerBlockEntity be, BlockState state, PoseStack ms, MultiBufferSource buffer, int light) {
        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (!state.getValue(TowerBlock.CORNER).isOutside(side))
                continue;
            BlockPos np = pos.relative(side);
            BlockState neighbour = level.getBlockState(np);
            if (!(neighbour.getBlock() instanceof IRotate r) || !r.hasShaftTowards(level, np, neighbour, side.getOpposite()))
                continue;
            Direction.Axis axis = side.getAxis();
            KineticBlockEntityRenderer.kineticRotationTransform(
                            CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, side), be, axis,
                            KineticBlockEntityRenderer.getAngleForBe(be, pos, axis), light)
                    .renderInto(ms, buffer.getBuffer(RenderType.solid()));
        }
    }

    private static void content(TowerBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
        int room = be.capacity();
        int layers = be.layers() - 1; // the column, above the bases
        if (room <= 0 || layers <= 0)
            return;
        float bottom = 1 + FLOOR, top = 1 + layers - FLOOR, span = top - bottom;
        float lo = INSET, hi = 2 - INSET;
        float y = bottom;
        // The product at the bottom, then each input that is a liquid, the gas last
        TowerBlockEntity.Content product = be.product();
        if (product != null)
            y = box(product.fluid(), product.amount(), room, y, top, span, lo, hi, ms, buffer, light);
        for (TowerBlockEntity.Content c : be.contents())
            if (!c.fluid().isSame(FwFluids.gas()))
                y = box(c.fluid(), c.amount(), room, y, top, span, lo, hi, ms, buffer, light);
        float waterTop = y;
        // the gas hangs from the top, down to what is under it at most
        int gas = be.gas();
        if (gas > 0) {
            float h = Math.min(span * gas / room, top - waterTop);
            if (h > 1e-3f)
                com.frostworks.platform.Loader.renderFluidBox(FwFluids.gas(), gas, lo, top - h, lo, hi, top, hi,
                        buffer, ms, light, true, false);
        }
    }

    /** One fluid from {@code y} up, as high as its share of the room; returns its top. */
    private static float box(net.minecraft.world.level.material.Fluid fluid, int amount, int room, float y, float top, float span, float lo, float hi,
                             PoseStack ms, MultiBufferSource buffer, int light) {
        if (amount <= 0 || y >= top)
            return y;
        float h = Math.min(span * amount / room, top - y);
        if (h <= 1e-3f)
            return y;
        com.frostworks.platform.Loader.renderFluidBox(fluid, amount, lo, y, lo, hi, y + h, hi, buffer, ms, light, false,
                false);
        return y + h;
    }
}
