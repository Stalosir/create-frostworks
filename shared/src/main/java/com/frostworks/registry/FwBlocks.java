package com.frostworks.registry;

import static com.frostworks.Frostworks.REGISTRATE;

import com.frostworks.content.column.ColumnBaseBlock;
import com.frostworks.content.column.ColumnSegmentBlock;
import com.frostworks.content.compressor.CompressorBlock;
import com.frostworks.content.expander.ExpanderBlock;
import com.frostworks.content.reservoir.ReservoirBlock;
import net.minecraft.client.renderer.RenderType;
import com.frostworks.content.door.InsulatedDoorBlock;
import com.frostworks.content.passage.ShaftPassageBlock;
import com.frostworks.content.vent.VentBlock;
import com.frostworks.content.wall.ColdWallBlock;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.encasing.EncasingRegistry;
import com.simibubi.create.infrastructure.config.CStress;
import com.simibubi.create.content.fluids.PipeAttachmentModel;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;

/**
 * Blocks. Registrate only registers them here: blockstates, models, lang and loot tables are hand-written JSON in
 * src/main/resources (no data generation in this prototype).
 */
public final class FwBlocks {
    private FwBlocks() {}

    public static final BlockEntry<CompressorBlock> COMPRESSOR = REGISTRATE.block("compressor", CompressorBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).noOcclusion())
            // Our cage has see-through parts (alpha 0 or 255)
            .addLayer(() -> RenderType::cutout)
            // Same baked model wrapper as Create's pump: draws the rims of the pipes connected to it
            .onRegister(CreateRegistrate.blockModel(() -> PipeAttachmentModel::withAO))
            .simpleItem()
            .register();

    public static final BlockEntry<VentBlock> VENT = REGISTRATE.block("vent", VentBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).noOcclusion())
            // our vent.png has see-through parts (alpha 0 or 255): the grille
            .addLayer(() -> RenderType::cutout)
            .simpleItem()
            .register();

    // Cold wall: a Create casing (same builder, same connected textures), in the frostworks:cold_walls tag.
    public static final BlockEntry<ColdWallBlock> COLD_WALL = REGISTRATE.block("cold_wall", ColdWallBlock::new)
            .transform(ColdWallBlock.casing()) // Create's casing transform, with the tint layers' shifts
            .properties(p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).sound(net.minecraft.world.level.block.SoundType.STONE)) // stone, not the casing's wood
            .register();

    public static final BlockEntry<InsulatedDoorBlock> INSULATED_DOOR =
            REGISTRATE.block("insulated_door", InsulatedDoorBlock::new)
                    .initialProperties(SharedProperties::copperMetal)
                    .properties(p -> p.mapColor(MapColor.COLOR_ORANGE).noOcclusion())
                    // our frost_door.png has transparent parts
                    .addLayer(() -> RenderType::cutout)
                    .simpleItem()
                    .register();

    public static final BlockEntry<ShaftPassageBlock> SHAFT_PASSAGE =
            REGISTRATE.block("shaft_passage", ShaftPassageBlock::new)
                    .initialProperties(SharedProperties::copperMetal)
                    .properties(p -> p.mapColor(MapColor.COLOR_ORANGE).noOcclusion()) // as Create's encased shaft: the shaft inside stays lit
                    .simpleItem()
                    .register();

    /** Insulated pipe passage, Create's straight pipe in a cold wall (made on a pipe, like the shaft one). */
    public static final BlockEntry<com.frostworks.content.passage.PipePassageBlock> PIPE_PASSAGE =
            REGISTRATE.block("pipe_passage", com.frostworks.content.passage.PipePassageBlock::new)
                    .initialProperties(SharedProperties::copperMetal)
                    .properties(p -> p.mapColor(MapColor.COLOR_ORANGE).noOcclusion()) // as Create's encased pipe
                    .simpleItem()
                    .register();

    /** Cryogenic infuser, Create's press that infuses with cold gas (creative only for now). */
    public static final BlockEntry<com.frostworks.content.infuser.CryoInfuserBlock> CRYO_INFUSER =
            REGISTRATE.block("cryo_infuser", com.frostworks.content.infuser.CryoInfuserBlock::new)
                    .initialProperties(SharedProperties::copperMetal)
                    .properties(p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).noOcclusion())
                    .simpleItem()
                    .register();

    // ---- Prototype 3: stress loop -------------------------------------------------------------------------------

    /**
     * Distillation tower (two blocks again). Column base: layer 1, 2 x 2, the only block with pipes and
     * shaft. Column segment ("Colonne"): layers 2 and up, made of frost tanks. Both on our models.
     */
    public static final BlockEntry<ColumnBaseBlock> COLUMN_BASE = REGISTRATE.block("column_base", ColumnBaseBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).noOcclusion()
                    .isRedstoneConductor((s, l, pos) -> true))
            .addLayer(() -> RenderType::cutoutMipped)
            .simpleItem()
            .register();

    public static final BlockEntry<ColumnSegmentBlock> COLUMN_SEGMENT =
            REGISTRATE.block("column_segment", ColumnSegmentBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).noOcclusion()
                    .isRedstoneConductor((s, l, pos) -> true))
            .addLayer(() -> RenderType::cutoutMipped)
            .simpleItem()
            .register();

    public static final BlockEntry<ReservoirBlock> RESERVOIR = REGISTRATE.block("reservoir", ReservoirBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).noOcclusion()
                    .isRedstoneConductor((s, l, pos) -> true))
            // Create's tank model (connected textures, windows) on our frost tank sheets
            .onRegister(CreateRegistrate.blockModel(() -> com.frostworks.client.FrostTankModel::create))
            .addLayer(() -> RenderType::cutoutMipped)
            .item(com.frostworks.content.reservoir.FrostTankItem::new).build()
            .register();

    public static final BlockEntry<ExpanderBlock> EXPANDER = REGISTRATE.block("expander", ExpanderBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).noOcclusion())
            .onRegister(CreateRegistrate.blockModel(() -> PipeAttachmentModel::withAO))
            .simpleItem()
            .register();

    public static void init() {}

    /**
     * The shaft passage becomes an encased variant of Create's shaft (a cold wall used on a shaft makes
     * it). Done here: with Create's EncasingRegistry.addVariantTo (a Registrate onRegisterAfter callback) on our
     * builder, the shaft's variant list never got the passage (seen in GameTest); the direct call works.
     */
    public static void commonSetup(java.util.function.Consumer<Runnable> enqueueWork) {
        enqueueWork.accept(() -> EncasingRegistry.addVariant(AllBlocks.SHAFT.get(), SHAFT_PASSAGE.get()));
        enqueueWork.accept(() -> EncasingRegistry.addVariant(AllBlocks.FLUID_PIPE.get(), PIPE_PASSAGE.get()));
        enqueueWork.accept(com.frostworks.CreateHooks::logAll); // Both hooks, in the same words
    }
}
