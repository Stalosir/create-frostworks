package com.frostworks.client;

import java.util.function.Consumer;

import com.frostworks.Frostworks;
import com.frostworks.content.compressor.CompressorBlock;
import com.frostworks.content.compressor.CompressorBlockEntity;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.simibubi.create.foundation.render.AllInstanceTypes;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.material.Materials;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.util.RendererReloadCache;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.core.Direction;

/**
 * The compressor's cogwheel, our {@code models/block/compressor/cog.json}, drawn apart and
 * turned like the cogwheel of Create's pump (same visual, {@code SingleAxisRotatingVisual} with the model drawn along Z,
 * same renderer logic): about the compressor's axis, at the network speed, in the pump's direction. Drawn as it is, at
 * scale 1, no offset.
 * <p>
 * Its texture has see-through pixels. The model sets no {@code render_type}, which Flywheel reads as solid: the
 * instanced model is built here with Flywheel's cutout material (the classic renderer uses the block's cutout layer).
 * <p>
 * The cogwheel sits on Create's half shaft, from the back face (the rotation input) to the centre, turning with it.
 * Unlike Create's fan (lit from the block behind, dark against a full block), both are lit at the compressor's own
 * position: the block lets light through.
 */
public final class CompressorCog {
    private CompressorCog() {}

    public static final PartialModel MODEL = PartialModel.of(Frostworks.id("block/compressor/cog"));

    private static final RendererReloadCache<PartialModel, Model> CUTOUT =
            new RendererReloadCache<>(com.frostworks.platform.Loader::cutout);

    /** Flywheel path (instanced), as Create's {@code SingleAxisRotatingVisual.ofZ(MECHANICAL_PUMP_COG)}, plus the shaft. */
    public static SimpleBlockEntityVisualizer.Factory<CompressorBlockEntity> visual() {
        return Visual::new;
    }

    static final class Visual extends SingleAxisRotatingVisual<CompressorBlockEntity> {
        private final RotatingInstance shaft;

        Visual(VisualizationContext context, CompressorBlockEntity be, float partialTick) {
            super(context, be, partialTick, Direction.SOUTH, CUTOUT.get(MODEL));
            Direction back = blockState.getValue(CompressorBlock.FACING).getOpposite();
            shaft = instancerProvider().instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF))
                    .createInstance();
            shaft.setup(be).setPosition(getVisualPosition()).rotateToFace(Direction.SOUTH, back).setChanged();
        }

        @Override
        public void update(float partialTick) {
            super.update(partialTick);
            shaft.setup(blockEntity).setChanged();
        }

        @Override
        public void updateLight(float partialTick) {
            super.updateLight(partialTick);
            relight(pos, shaft);
        }

        @Override
        protected void _delete() {
            super._delete();
            shaft.delete();
        }

        @Override
        public void collectCrumblingInstances(Consumer<Instance> consumer) {
            super.collectCrumblingInstances(consumer);
            consumer.accept(shaft);
        }
    }
}
