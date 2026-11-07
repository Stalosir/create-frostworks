package com.frostworks.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/** Client -> server: "I wear goggles and look at this wall/vent/door, what room is it?" */
public record RoomQueryPayload(BlockPos pos) {
    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public static RoomQueryPayload read(FriendlyByteBuf buf) {
        return new RoomQueryPayload(buf.readBlockPos());
    }
}
