package com.frostworks.client;

import com.frostworks.Frostworks;
import com.frostworks.client.ponder.FwPonderPlugin;

import net.createmod.ponder.foundation.PonderIndex;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Client entry point: nothing here is loaded on a dedicated server. Forge 1.20.1: the mod bus's events here, the game
 * bus's in {@link Game}.
 */
@Mod.EventBusSubscriber(modid = Frostworks.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FrostworksClient {
    private FrostworksClient() {}

    private static final IGuiOverlay ROOM_OVERLAY = (gui, graphics, partialTick, width, height) ->
            RoomGoggleOverlay.render(graphics, partialTick);
    /** Loads the strip partial model at mod construction, before models are baked (as Create does). */
    @SuppressWarnings("unused")
    private static final Object DOOR_FLAP = StripDoorRenderer.FLAP;
    /** Same for the vent's propeller. */
    @SuppressWarnings("unused")
    private static final Object VENT_PROPELLER = VentPropeller.MODEL;
    /** Same for the compressor's cogwheel. */
    @SuppressWarnings("unused")
    private static final Object COMPRESSOR_COG = CompressorCog.MODEL;
    /** Same for the cryogenic infuser's arm and the expander's shaft. */
    @SuppressWarnings("unused")
    private static final Object INFUSER_ARM = CryoInfuserRenderer.ARM;
    @SuppressWarnings("unused")
    private static final Object EXPANDER_SHAFT = ExpanderShaftRenderer.SHAFT;
    /** The belt passage's casing sheet: its sprite is resolved when the atlas is stitched, so made at construction too. */
    @SuppressWarnings("unused")
    private static final Object BELT_PASSAGE_SHEET = BeltPassageModel.SHIFT;

    @SubscribeEvent
    public static void registerLayers(RegisterGuiOverlaysEvent event) {
        // Same slot as Create's goggle overlay (above the hotbar)
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "room_goggle_info", ROOM_OVERLAY);
    }

    /** Our Ponder scenes (Ponder reads its plugins once loading is complete). */
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new FwPonderPlugin());
        // The "Config" button of the mod list opens Create's config screen, with our title
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new FwConfigScreen(parent)));
        // Create's "Hold [Shift] for Summary" tooltip, on the items whose role is not plain from their name
        for (var block : java.util.List.of(com.frostworks.registry.FwBlocks.COLD_WALL, com.frostworks.registry.FwBlocks.VENT,
                com.frostworks.registry.FwBlocks.EXPANDER, com.frostworks.registry.FwBlocks.CRYO_INFUSER,
                com.frostworks.registry.FwBlocks.INSULATED_DOOR)) {
            net.minecraft.world.item.Item item = block.get().asItem();
            com.simibubi.create.foundation.item.TooltipModifier.REGISTRY.register(item,
                    new com.simibubi.create.foundation.item.ItemDescription.Modifier(item,
                            net.createmod.catnip.lang.FontHelper.Palette.STANDARD_CREATE));
        }
        // The column base's stress impact, as Create gives it on its own machines
        com.simibubi.create.foundation.item.TooltipModifier.REGISTRY.register(
                com.frostworks.registry.FwBlocks.COLUMN_BASE.get().asItem(), new TowerStats());
    }

    @SubscribeEvent
    public static void registerBeltFlaps(net.minecraftforge.client.event.ModelEvent.RegisterAdditional event) {
        BeltPassageModel.registerFlaps(event);
        ChilledOverlay.register(event); // The frost layer of chilled stacks
    }

    /** After Create (lowest priority): our belt passage wraps Create's belt model. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void wrapBeltModel(net.minecraftforge.client.event.ModelEvent.ModifyBakingResult event) {
        BeltPassageModel.wrap(event);
    }

    /** The game bus's client events. */
    @Mod.EventBusSubscriber(modid = Frostworks.MODID, value = Dist.CLIENT)
    public static final class Game {
    private Game() {}

    @SubscribeEvent
    public static void renderBeltFlaps(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        BeltFlapAnimator.render(event);
    }

    /**
     * On every chilled stack (marked by the cryogenic infuser), what it gives at Create's crushing
     * wheels (the cold processing file the client received; its 1.1 values before a world is open).
     */
    @SubscribeEvent
    public static void chilledTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
        if (!com.frostworks.content.cold.ChilledMark.is(event.getItemStack()))
            return;
        event.getToolTip().addAll(com.frostworks.content.ore.ChilledCrushing.tooltip(
                net.minecraft.client.gui.screens.Screen.hasShiftDown()));
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        ClientRoomInfo.tick();
        BeltFlapAnimator.tick();
    }
    }
}
