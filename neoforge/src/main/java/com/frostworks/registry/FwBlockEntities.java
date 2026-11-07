package com.frostworks.registry;

import static com.frostworks.Frostworks.REGISTRATE;

import com.frostworks.content.column.TowerBlockEntity;
import com.frostworks.content.compressor.CompressorBlockEntity;
import com.frostworks.content.door.StripDoorBlockEntity;
import com.frostworks.content.expander.ExpanderBlockEntity;
import com.frostworks.content.reservoir.ReservoirBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankRenderer;
import com.frostworks.content.vent.VentBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import com.frostworks.content.reservoir.CreateTankGuard;
import com.simibubi.create.AllBlockEntityTypes;

public final class FwBlockEntities {
    private FwBlockEntities() {}

    /**
     * Our cogwheel, turned like the cogwheel of Create's pump (instanced with Flywheel, classic
     * renderer without); the renderer also writes the setting on the black mark, in both paths.
     */
    public static final BlockEntityEntry<CompressorBlockEntity> COMPRESSOR = REGISTRATE
            .blockEntity("compressor", CompressorBlockEntity::new)
            .visual(() -> com.frostworks.client.CompressorCog.visual(), true)
            .validBlocks(FwBlocks.COMPRESSOR)
            .renderer(() -> com.frostworks.client.CompressorRenderer::new)
            .register();

    public static final BlockEntityEntry<VentBlockEntity> VENT = REGISTRATE
            .blockEntity("vent", VentBlockEntity::new)
            // The propeller, drawn like Create's fan (instanced with Flywheel, classic renderer without)
            .visual(() -> com.frostworks.client.VentVisual::new, false)
            .validBlocks(FwBlocks.VENT)
            .renderer(() -> com.frostworks.client.VentRenderer::new)
            .register();

    /** Same as Create's encased shaft. */
    public static final BlockEntityEntry<KineticBlockEntity> SHAFT_PASSAGE = REGISTRATE
            .blockEntity("shaft_passage", KineticBlockEntity::new)
            .visual(() -> SingleAxisRotatingVisual::shaft, false)
            .validBlocks(FwBlocks.SHAFT_PASSAGE)
            .renderer(() -> ShaftRenderer::new)
            .register();

    // ---- Prototype 3: stress loop -------------------------------------------------------------------------------

    /** Distillation tower: every block of it, base and segments; the controller holds the content. */
    public static final BlockEntityEntry<TowerBlockEntity> TOWER = REGISTRATE
            .blockEntity("tower", TowerBlockEntity::new)
            .validBlocks(FwBlocks.COLUMN_BASE, FwBlocks.COLUMN_SEGMENT)
            .renderer(() -> com.frostworks.client.TowerRenderer::new)
            .register();

    /** Pipe passage, Create's straight pipe block entity (keeps the fluid on its way). */
    public static final BlockEntityEntry<com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity> PIPE_PASSAGE =
            REGISTRATE.blockEntity("pipe_passage", com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity::new)
                    .validBlocks(FwBlocks.PIPE_PASSAGE)
                    .register();

    /** Cryogenic infuser, the shaft and the arm drawn as Create's press (both paths). */
    public static final BlockEntityEntry<com.frostworks.content.infuser.CryoInfuserBlockEntity> CRYO_INFUSER = REGISTRATE
            .blockEntity("cryo_infuser", com.frostworks.content.infuser.CryoInfuserBlockEntity::new)
            .visual(() -> com.frostworks.client.CryoInfuserRenderer.Visual::new)
            .validBlocks(FwBlocks.CRYO_INFUSER)
            .renderer(() -> com.frostworks.client.CryoInfuserRenderer::new)
            .register();

    /** Shaft drawn through the expander, like an encased shaft (on our copy of its textures). */
    public static final BlockEntityEntry<ExpanderBlockEntity> EXPANDER = REGISTRATE
            .blockEntity("expander", ExpanderBlockEntity::new)
            .visual(() -> com.frostworks.client.ExpanderShaftRenderer.visual(), false)
            .validBlocks(FwBlocks.EXPANDER)
            .renderer(() -> com.frostworks.client.ExpanderShaftRenderer::new)
            .register();

    /** Same renderer as Create's tank (fluid level). */
    public static final BlockEntityEntry<ReservoirBlockEntity> RESERVOIR = REGISTRATE
            .blockEntity("reservoir", ReservoirBlockEntity::new)
            .validBlocks(FwBlocks.RESERVOIR)
            .renderer(() -> FluidTankRenderer::new)
            .register();

    /** Strip curtain door (lower half): draws the four strips. */
    public static final BlockEntityEntry<StripDoorBlockEntity> STRIP_DOOR = REGISTRATE
            .blockEntity("strip_door", StripDoorBlockEntity::new)
            .validBlocks(FwBlocks.INSULATED_DOOR)
            .renderer(() -> com.frostworks.client.StripDoorRenderer::new)
            .register();

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, COMPRESSOR.get(),
                (be, side) -> be.getFluidHandler(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, VENT.get(), (be, side) -> be.getFluidHandler(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, TOWER.get(), (be, side) -> be.getFluidHandler(side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, TOWER.get(), (be, side) -> be.getItemHandler(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, EXPANDER.get(),
                (be, side) -> be.getFluidHandler(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, RESERVOIR.get(), (be, side) -> be.capability());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CRYO_INFUSER.get(), (be, side) -> be.getFluidHandler(side));
        // the Frosium bucket as a fluid container (NeoForge does this for the plain BucketItem class only)
        event.registerItem(Capabilities.FluidHandler.ITEM,
                (stack, ctx) -> new net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper(stack),
                FwFluids.frosiumBucket());
    }

    /** Create's own tank refuses our liquids (listened to before Create's provider, see {@link CreateTankGuard}). */
    public static void guardCreateTanks(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, AllBlockEntityTypes.FLUID_TANK.get(),
                (be, side) -> new CreateTankGuard(be));
    }

    public static void init() {}
}
