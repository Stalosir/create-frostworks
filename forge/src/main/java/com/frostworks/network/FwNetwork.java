package com.frostworks.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.frostworks.FwConfig;
import com.frostworks.client.ClientRoomInfo;
import com.frostworks.content.vent.VentBlock;
import com.frostworks.content.vent.VentBlockEntity;
import com.frostworks.room.Room;
import com.frostworks.room.RoomManager;
import com.frostworks.room.RoomWalls;
import com.frostworks.room.core.RoomScan;
import com.frostworks.room.core.RoomStatus;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class FwNetwork {
    private FwNetwork() {}

    /** Bump on any change of a payload. */
    public static final String PROTOCOL_VERSION = "4"; // 4 since the goggles' Cold Sweat temperature

    // Forge refuses a client of another protocol version by itself (the channel's version check)
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(com.frostworks.Frostworks.id("main"),
            () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(RoomQueryPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RoomQueryPayload::write).decoder(RoomQueryPayload::read)
                .consumerMainThread((query, context) -> handleQuery(query, context.get())).add();
        CHANNEL.messageBuilder(RoomInfoPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RoomInfoPayload::write).decoder(RoomInfoPayload::read)
                .consumerMainThread((info, context) -> ClientRoomInfo.receive(info)).add();
        CHANNEL.messageBuilder(BeltPassagePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BeltPassagePayload::write).decoder(BeltPassagePayload::read)
                .consumerMainThread((belt, context) -> com.frostworks.client.BeltPassageModel.receive(belt)).add();
        CHANNEL.messageBuilder(RoomColdPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RoomColdPayload::write).decoder(RoomColdPayload::read)
                .consumerMainThread((cold, context) -> com.frostworks.compat.tan.ClientRoomCold.receive(cold)).add();
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendToTracking(ServerLevel level, ChunkPos chunk, Object message) {
        CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunk(chunk.x, chunk.z)), message);
    }

    private static void handleQuery(RoomQueryPayload query, NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null)
            return;
        BlockPos pos = query.pos();
        ServerLevel level = player.serverLevel();
        if (player.distanceToSqr(pos.getCenter()) > 32 * 32 || !level.isLoaded(pos))
            return;
        BlockState state = level.getBlockState(pos);
        if (!RoomWalls.isWall(level, pos, state)) // A belt passage too
            return;
        sendToPlayer(player, describe(level, pos, state));
    }

    /** Builds the overlay data for a wall, door, passage or vent (shared: {@link RoomInfo}). Also used by /frostworks room. */
    public static RoomInfoPayload describe(ServerLevel level, BlockPos pos, BlockState state) {
        return RoomInfo.describe(level, pos, state);
    }

    /** Plain-text reason a room is not cold, shared by the overlay and the command (translation keys). */
    public static String reasonKey(RoomStatus status) {
        return switch (status) {
            case COLD -> "frostworks.room.reason.cold";
            case INSUFFICIENT -> "frostworks.room.reason.insufficient";
            case OPEN -> "frostworks.room.reason.open";
            case PENDING -> "frostworks.room.reason.pending";
        };
    }
}
