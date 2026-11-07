package com.frostworks;

import com.frostworks.command.FrostworksCommands;
import com.frostworks.energy.LoopStatus;
import com.frostworks.overspeed.Overspeed;
import com.frostworks.room.RoomManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;

/** Game events (server side) feeding the room managers. */
@Mod.EventBusSubscriber(modid = Frostworks.MODID)
public final class FwEvents {
    private FwEvents() {}

    /** Fired by Level.updateNeighborsAt: every setBlock with neighbour updates, /setblock, explosions... */
    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RoomManager.get(level).onBlockChanged(event.getPos(), event.getState());
            Overspeed.onBlockChanged(level, event.getPos(), event.getState());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END)
            return;
        com.frostworks.room.ColdExposure.tick(event.player);
        // An old chilled ore (1.0) turns into its base ore, chilled, once a second
        if (!event.player.level().isClientSide() && event.player.tickCount % 20 == 0)
            com.frostworks.registry.FwChilledOres.convert(event.player.getInventory());
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            RoomManager.get(level).tick();
            Overspeed.tick(level);
            com.frostworks.content.wall.WallTinting.tick(level); // The tint waves
            com.frostworks.compat.tan.RoomColdSync.tick(level); // Tough As Nails' thermometer
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level)
            RoomManager.get(level).onChunkLoaded(event.getChunk().getPos());
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level)
            RoomManager.get(level).onChunkUnloaded(event.getChunk().getPos());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level)
            RoomManager.forget(level);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END)
            LoopStatus.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RoomManager.clearAll();
        Overspeed.clear();
        LoopStatus.clear();
    }

    /** The tags (and the recipes) were reloaded on the server: the cold processing index is built again. */
    @SubscribeEvent
    public static void onTagsUpdated(net.minecraftforge.event.TagsUpdatedEvent event) {
        if (event.getUpdateCause() == net.minecraftforge.event.TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD)
            com.frostworks.content.cold.ColdProcessing.invalidate();
        com.frostworks.content.cold.ColdFluids.invalidate(); // On the client too (its copy of the recipes)
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        FrostworksCommands.register(event.getDispatcher());
    }
}
