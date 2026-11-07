package com.frostworks.client;

import java.lang.reflect.Constructor;

import com.frostworks.Frostworks;
import com.simibubi.create.content.fluids.tank.FluidTankModel;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;

import net.minecraft.client.resources.model.BakedModel;

/**
 * The frost tank's model: Create's tank model (connected textures, windows, culling between blocks) on our
 * sheets of {@code textures/block/frost_tank/}, laid out as Create's own. Create builds its tank model only with its
 * own sheets (private constructor): built here with ours.
 */
public final class FrostTankModel {
    private FrostTankModel() {}

    private static final CTSpriteShiftEntry SIDE = sheet("frost_tank");
    private static final CTSpriteShiftEntry TOP = sheet("frost_tank_top");
    private static final CTSpriteShiftEntry INNER = sheet("frost_tank_inner");

    private static CTSpriteShiftEntry sheet(String name) {
        return CTSpriteShifter.getCT(AllCTTypes.RECTANGLE, Frostworks.id("block/frost_tank/" + name),
                Frostworks.id("block/frost_tank/" + name + "_connected"));
    }

    public static FluidTankModel create(BakedModel original) {
        try {
            Constructor<FluidTankModel> c = FluidTankModel.class.getDeclaredConstructor(BakedModel.class,
                    CTSpriteShiftEntry.class, CTSpriteShiftEntry.class, CTSpriteShiftEntry.class);
            c.setAccessible(true);
            return c.newInstance(original, SIDE, TOP, INNER);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Create's tank model changed", e);
        }
    }
}
