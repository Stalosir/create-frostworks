package com.frostworks.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.frostworks.Frostworks;
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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class FwNetwork {
    private FwNetwork() {}

    /** Bump on any change of a payload. */
    public static final String PROTOCOL_VERSION = "4"; // 4 since the goggles' Cold Sweat temperature

    /**
     * The messages are plain records of the shared code (write and read on a FriendlyByteBuf, as
     * Forge 1.20.1 sends them). NeoForge 1.21.1 sends payloads: each message travels in this envelope, of its own type.
     */
    record Envelope<T>(Type<Envelope<T>> type, T message) implements CustomPacketPayload {}

    private static final Type<Envelope<RoomQueryPayload>> QUERY = new Type<>(Frostworks.id("room_query"));
    private static final Type<Envelope<RoomInfoPayload>> INFO = new Type<>(Frostworks.id("room_info"));
    private static final Type<Envelope<BeltPassagePayload>> BELT = new Type<>(Frostworks.id("belt_passage"));
    private static final Type<Envelope<RoomColdPayload>> ROOM_COLD = new Type<>(Frostworks.id("room_cold"));

    private static <T> StreamCodec<FriendlyByteBuf, Envelope<T>> codec(Type<Envelope<T>> type,
                                                                     BiConsumer<T, FriendlyByteBuf> write,
                                                                     Function<FriendlyByteBuf, T> read) {
        return StreamCodec.of((buf, envelope) -> write.accept(envelope.message(), buf),
                buf -> new Envelope<>(type, read.apply(buf)));
    }

    /** The envelope of a message (of one of the records). */
    private static CustomPacketPayload wrap(Object message) {
        if (message instanceof RoomQueryPayload m)
            return new Envelope<>(QUERY, m);
        if (message instanceof RoomInfoPayload m)
            return new Envelope<>(INFO, m);
        if (message instanceof BeltPassagePayload m)
            return new Envelope<>(BELT, m);
        if (message instanceof RoomColdPayload m)
            return new Envelope<>(ROOM_COLD, m);
        throw new IllegalArgumentException("not a message of the mod: " + message);
    }

    /** The shared code sends through here on both loaders (Forge: its SimpleChannel). */
    public static void sendToServer(Object message) {
        PacketDistributor.sendToServer(wrap(message));
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        PacketDistributor.sendToPlayer(player, wrap(message));
    }

    public static void sendToTracking(ServerLevel level, ChunkPos chunk, Object message) {
        PacketDistributor.sendToPlayersTrackingChunk(level, chunk, wrap(message));
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(QUERY, codec(QUERY, RoomQueryPayload::write, RoomQueryPayload::read),
                (envelope, context) -> handleQuery(envelope.message(), context));
        registrar.playToClient(INFO, codec(INFO, RoomInfoPayload::write, RoomInfoPayload::read),
                (envelope, context) -> ClientRoomInfo.receive(envelope.message()));
        registrar.playToClient(BELT, codec(BELT, BeltPassagePayload::write, BeltPassagePayload::read),
                (envelope, context) -> com.frostworks.client.BeltPassageModel.receive(envelope.message()));
        registrar.playToClient(ROOM_COLD, codec(ROOM_COLD, RoomColdPayload::write, RoomColdPayload::read),
                (envelope, context) -> com.frostworks.compat.tan.ClientRoomCold.receive(envelope.message()));
    }

    private static void handleQuery(RoomQueryPayload query, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player))
            return;
        BlockPos pos = query.pos();
        ServerLevel level = player.serverLevel();
        if (player.distanceToSqr(pos.getCenter()) > 32 * 32 || !level.isLoaded(pos))
            return;
        BlockState state = level.getBlockState(pos);
        if (!RoomWalls.isWall(level, pos, state)) // A belt passage too
            return;
        context.reply(wrap(describe(level, pos, state)));
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
