package com.frostworks.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server -> client: the temperature of the closed cold room the player stands in (NaN: none), for Tough As Nails'
 * thermometer, which works it out on the client, where the rooms are not. Sent when it changes the player's cold, never
 * each tick ({@link com.frostworks.compat.tan.RoomColdSync}).
 */
public record RoomColdPayload(float temperature) {
    public void write(FriendlyByteBuf buf) {
        buf.writeFloat(temperature);
    }

    public static RoomColdPayload read(FriendlyByteBuf buf) {
        return new RoomColdPayload(buf.readFloat());
    }
}
