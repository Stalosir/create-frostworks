package com.frostworks.compat.jei;

import com.frostworks.FwConfig;
import com.frostworks.client.CryoInfuserRenderer;
import com.frostworks.content.infuser.CryoInfuserBlockEntity;
import com.frostworks.registry.FwBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;

/**
 * The cryogenic infuser drawn in JEI, as Create draws its press ({@code AnimatedPress}: same view, scale
 * and turning shaft), with our own body and arm. The arm hangs 6 px down at rest, comes down onto the item,
 * stays there and goes back up, over one cycle as long as {@code infuserDwellTicks} (a simple curve).
 */
public class AnimatedInfuser extends AnimatedKinetics {

    private static final int SCALE = 23;
    /** Fully down: Create's press stroke on a depot (19 px), or into a basin (22 px). */
    private final float stroke;
    private final boolean basin;

    public AnimatedInfuser() {
        this(false);
    }

    /** Over a basin, as Create draws its press for compacting. */
    public AnimatedInfuser(boolean basin) {
        this.basin = basin;
        this.stroke = (basin ? 22 : 19) / 16f;
    }

    @Override
    public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
        PoseStack ms = graphics.pose();
        ms.pushPose();
        ms.translate(xOffset, yOffset, 200);
        ms.mulPose(Axis.XP.rotationDegrees(-15.5f));
        ms.mulPose(Axis.YP.rotationDegrees(22.5f));
        blockElement(shaft(Direction.Axis.Z)).rotateBlock(0, 0, getCurrentAngle()).scale(SCALE).render(graphics);
        blockElement(FwBlocks.CRYO_INFUSER.getDefaultState()).scale(SCALE).render(graphics);
        blockElement(CryoInfuserRenderer.ARM).atLocal(0, armOffset(), 0).scale(SCALE).render(graphics);
        if (basin)
            blockElement(com.simibubi.create.AllBlocks.BASIN.getDefaultState()).atLocal(0, 1.65, 0).scale(SCALE)
                    .render(graphics);
        ms.popPose();
    }

    /** The server setting when a world is open (synced to the client), else its default. */
    private static int dwellTicks() {
        try {
            return FwConfig.get(FwConfig.INFUSER_DWELL_TICKS);
        } catch (RuntimeException e) {
            return 40;
        }
    }

    /** Blocks down: 6 px at rest, the stroke at the bottom; down a quarter, held half, up a quarter of the cycle. */
    private float armOffset() {
        int cycle = Math.max(10, dwellTicks());
        float t = (AnimationTickHolder.getRenderTime() % cycle) / cycle;
        float down = t < 0.25f ? t / 0.25f : t < 0.75f ? 1 : (1 - t) / 0.25f;
        down = down * down * (3 - 2 * down); // smooth start and end
        return CryoInfuserBlockEntity.ARM_REST + down * (stroke - CryoInfuserBlockEntity.ARM_REST);
    }
}
