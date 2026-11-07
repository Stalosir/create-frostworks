package com.frostworks.compat.greate;

import static com.frostworks.Frostworks.REGISTRATE;

import com.frostworks.Frostworks;
import com.simibubi.create.content.decoration.encasing.EncasingRegistry;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;

/**
 * Greate (Forge 1.20.1 only): the shaft passage for Greate's shafts. Registered only when Greate is loaded
 * ({@link #init}, from the mod's constructor): its block entity implements one of Greate's interfaces.
 */
public final class GreatePassages {
    private GreatePassages() {}

    public static final String MODID = "greate";
    /** Greate's plain shaft (not its encased, girder or powered ones). */
    private static final String SHAFT_CLASS = "electrolyte.greate.content.kinetics.simpleRelays.TieredShaftBlock";

    public static BlockEntry<GreateShaftPassageBlock> BLOCK;
    public static BlockEntityEntry<GreateShaftPassageBlockEntity> BLOCK_ENTITY;

    public static void init() {
        BLOCK = REGISTRATE.block("greate_shaft_passage", GreateShaftPassageBlock::new)
                .initialProperties(SharedProperties::copperMetal)
                .properties(p -> p.mapColor(MapColor.COLOR_ORANGE).noOcclusion()) // as our shaft passage
                .register(); // no item: it is made on a shaft, and gives the shaft back
        BLOCK_ENTITY = REGISTRATE.blockEntity("greate_shaft_passage", GreateShaftPassageBlockEntity::new)
                .visual(() -> GreateShaftPassageVisual::create, false)
                .validBlocks(BLOCK)
                .renderer(() -> GreateShaftPassageVisual.Renderer::new)
                .register();
    }

    /** The passage becomes an encased variant of each of Greate's shafts (a cold wall used on one makes it). */
    public static void commonSetup() {
        int count = 0;
        for (Block block : BuiltInRegistries.BLOCK)
            if (block.getClass().getName().equals(SHAFT_CLASS)
                    && block instanceof com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock shaft) {
                EncasingRegistry.addVariant(shaft, BLOCK.get());
                count++;
            }
        Frostworks.LOGGER.info("Greate: the shaft passage on {} of Greate's shafts", count);
    }
}
