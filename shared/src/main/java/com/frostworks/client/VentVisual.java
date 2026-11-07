package com.frostworks.client;

import java.util.function.Consumer;

import com.frostworks.content.vent.VentBlock;
import com.frostworks.content.vent.VentBlockEntity;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.Direction;

/**
 * Flywheel path of the vent's propeller (instanced), as Create's {@code FanVisual}. Create's fan turns with a
 * kinetic speed known to the shader; the vent's speed eases up and down, so its angle is set each frame instead.
 * The material comes from the model's {@code render_type} (cutout), as for any Flywheel partial.
 */
public class VentVisual extends AbstractBlockEntityVisual<VentBlockEntity> implements SimpleDynamicVisual {
    private final TransformedInstance propeller;
    private final Direction facing;

    public VentVisual(VisualizationContext context, VentBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
        facing = blockState.getValue(VentBlock.FACING);
        propeller = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(VentPropeller.MODEL))
                .createInstance();
        place(partialTick);
    }

    private void place(float partialTick) {
        propeller.setIdentityTransform().translate(getVisualPosition());
        VentPropeller.transform(propeller, facing, blockEntity.propellerAngle(partialTick)).setChanged();
    }

    @Override
    public void beginFrame(DynamicVisual.Context context) {
        place(context.partialTick());
    }

    @Override
    public void updateLight(float partialTick) {
        relight(pos.relative(facing), propeller);
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(propeller);
    }

    @Override
    protected void _delete() {
        propeller.delete();
    }
}
