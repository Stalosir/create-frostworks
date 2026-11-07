package com.frostworks.compat.tan;

import java.lang.ref.WeakReference;

import com.frostworks.network.RoomColdPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Client only: the temperature of the cold room the player stands in, as the server last sent it
 * ({@link RoomColdSync}), for Tough As Nails' thermometer. Used for the player's own place only: the thermometer reads
 * it at the feet of whoever holds it, and any other reading on the client (a thermometer in an item frame, another
 * player's) is left to Tough As Nails alone. The temperature gauge works it out on the server, where the rooms are.
 */
public final class ClientRoomCold {
    private ClientRoomCold() {}

    private static volatile float temperature = Float.NaN;
    /** The client level it was sent in: another world or dimension forgets it (until the server sends the new one). */
    private static volatile WeakReference<Level> sentIn = new WeakReference<>(null);

    /** The server's message, on the client's main thread. */
    public static void receive(RoomColdPayload message) {
        sentIn = new WeakReference<>(Minecraft.getInstance().level);
        temperature = message.temperature();
    }

    /** The room's temperature at {@code pos} if it is where the player stands, else NaN. */
    static float temperatureAt(Level level, BlockPos pos) {
        Player player = Minecraft.getInstance().player;
        if (player == null || player.level() != level || sentIn.get() != level || !pos.equals(player.blockPosition()))
            return Float.NaN;
        return temperature;
    }
}
