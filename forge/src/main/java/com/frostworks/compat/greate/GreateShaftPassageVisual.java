package com.frostworks.compat.greate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.frostworks.Frostworks;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;

import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Greate: the Greate shaft passage drawn with the shaft it holds, turning (Flywheel's visual; the renderer below
 * when Flywheel is off). Greate draws its shafts with a partial model per material, {@code greate:block/<material>/shaft}
 * (its {@code TieredShaftVisual}, {@code GreateModelUtils.getPartialModel}): their block model is empty, which left the
 * passage's openings black (seen in game). The same partial here: the material's colour shows at both openings.
 */
public final class GreateShaftPassageVisual {
    private GreateShaftPassageVisual() {}

    /** Material name of each Greate shaft (read once, by reflection: no compile dependency on Greate nor GregTech). */
    private static final Map<Block, String> MATERIALS = new ConcurrentHashMap<>();

    /** The shaft's partial model, Create's shaft while none is known (or its material cannot be read). */
    static PartialModel partial(@Nullable Block shaft) {
        if (shaft == null)
            return AllPartialModels.SHAFT;
        String material = MATERIALS.computeIfAbsent(shaft, GreateShaftPassageVisual::materialName);
        return material.isEmpty() ? AllPartialModels.SHAFT
                : PartialModel.of(new ResourceLocation(GreatePassages.MODID, "block/" + material + "/shaft"));
    }

    /** {@code shaft.getMaterial.getName}, empty if it fails. */
    private static String materialName(Block shaft) {
        try {
            Object material = shaft.getClass().getMethod("getMaterial").invoke(shaft);
            return material == null ? "" : String.valueOf(material.getClass().getMethod("getName").invoke(material));
        } catch (ReflectiveOperationException | RuntimeException e) {
            Frostworks.LOGGER.warn("Greate shaft passage: no material for {} ({})", shaft, e.toString());
            return "";
        }
    }

    /** The visual: the shaft's partial (laid along Y, like Create's), turned to the passage's axis. */
    public static SingleAxisRotatingVisual<GreateShaftPassageBlockEntity> create(VisualizationContext context,
                                                                               GreateShaftPassageBlockEntity be,
                                                                               float partialTick) {
        return new SingleAxisRotatingVisual<>(context, be, partialTick, Models.partial(partial(be.shaft())));
    }

    /** Its shaft changed (known on the client after the passage was first drawn): the visual made again. */
    static void redraw(GreateShaftPassageBlockEntity be) {
        if (!VisualizationManager.supportsVisualization(be.getLevel()))
            return;
        var visuals = VisualizationManager.get(be.getLevel()).blockEntities();
        visuals.queueRemove(be);
        visuals.queueAdd(be);
    }

    /** Without Flywheel's visuals: Create's kinetic renderer, with the shaft's partial (as Create's shaft renderer). */
    public static class Renderer extends KineticBlockEntityRenderer<GreateShaftPassageBlockEntity> {
        public Renderer(BlockEntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        protected SuperByteBuffer getRotatedModel(GreateShaftPassageBlockEntity be, BlockState state) {
            return CachedBuffers.partialFacingVertical(partial(be.shaft()), state,
                    Direction.fromAxisAndDirection(getRotationAxisOf(be), Direction.AxisDirection.POSITIVE));
        }
    }
}
