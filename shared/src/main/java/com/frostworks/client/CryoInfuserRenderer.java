package com.frostworks.client;

import java.util.function.Consumer;

import com.frostworks.Frostworks;
import com.frostworks.content.infuser.CryoInfuserBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.ShaftVisual;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.OrientedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The cryogenic infuser's arm: drawn apart and moved down and up like the head of Create's press, in
 * both paths (Flywheel {@link Visual} and this classic renderer). The arm is {@code models/block/cryo_infuser/arm.json}
 * (for now a child of Create's press head, until it has its own model), turned with the block's facing.
 */
public class CryoInfuserRenderer extends KineticBlockEntityRenderer<CryoInfuserBlockEntity> {

    public static final PartialModel ARM = PartialModel.of(Frostworks.id("block/cryo_infuser/arm"));

    public CryoInfuserRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    /** How far down the arm is (blocks): 6 px at rest, down to the item, held there while it infuses. */
    static float armOffset(CryoInfuserBlockEntity be, float partialTicks) {
        return be.armOffset(partialTicks);
    }

    @Override
    protected void renderSafe(CryoInfuserBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay); // the shaft, unless Flywheel draws it
        if (VisualizationManager.supportsVisualization(be.getLevel()))
            return;
        BlockState state = be.getBlockState();
        CachedBuffers.partialFacing(ARM, state, state.getValue(BlockStateProperties.HORIZONTAL_FACING))
                .translate(0, -armOffset(be, partialTicks), 0)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    @Override
    protected BlockState getRenderedBlockState(CryoInfuserBlockEntity be) {
        return shaft(getRotationAxisOf(be));
    }

    /** Flywheel path: the shaft (as Create's press) and the arm. */
    public static class Visual extends ShaftVisual<CryoInfuserBlockEntity> implements SimpleDynamicVisual {
        private final OrientedInstance arm;

        public Visual(VisualizationContext context, CryoInfuserBlockEntity be, float partialTick) {
            super(context, be, partialTick);
            arm = instancerProvider().instancer(InstanceTypes.ORIENTED, Models.partial(ARM)).createInstance();
            arm.rotation(Axis.YP.rotationDegrees(AngleHelper.horizontalAngle(
                    blockState.getValue(BlockStateProperties.HORIZONTAL_FACING))));
            move(partialTick);
        }

        @Override
        public void beginFrame(DynamicVisual.Context context) {
            move(context.partialTick());
        }

        private void move(float partialTick) {
            arm.position(getVisualPosition()).translatePosition(0, -armOffset(blockEntity, partialTick), 0)
                    .setChanged();
        }

        @Override
        public void updateLight(float partialTick) {
            super.updateLight(partialTick);
            relight(arm);
        }

        @Override
        protected void _delete() {
            super._delete();
            arm.delete();
        }

        @Override
        public void collectCrumblingInstances(Consumer<Instance> consumer) {
            super.collectCrumblingInstances(consumer);
            consumer.accept(arm);
        }
    }
}
