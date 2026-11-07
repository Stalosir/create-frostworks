package com.frostworks.compat.tan;

import java.util.Map;
import java.util.WeakHashMap;

import com.frostworks.FwConfig;
import com.frostworks.network.FwNetwork;
import com.frostworks.network.RoomColdPayload;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells each player the temperature of the closed cold room they stand in, for Tough As Nails' thermometer
 * ({@link ClientRoomCold}). Once a second, a table look-up per player; a message only when it changes their cold:
 * entering, leaving, another room, a threshold crossed, another dimension, a new connection (a new player object), the
 * option turned off. Nothing at all without Tough As Nails.
 */
public final class RoomColdSync {
    private RoomColdSync() {}

    /** What each player was last told, and in which level (a player object is new on each connection and respawn). */
    private static final Map<ServerPlayer, Sent> SENT = new WeakHashMap<>();

    private record Sent(ServerLevel level, int band, float temperature) {}

    /** Each tick of a server level ({@code FwEvents}). */
    public static void tick(ServerLevel level) {
        if (!ToughAsNailsCompat.active() || level.getGameTime() % 20 != 0)
            return;
        boolean enabled = FwConfig.get(FwConfig.TAN_ENABLED);
        for (ServerPlayer player : level.players()) {
            float temperature = enabled ? ToughAsNailsCompat.roomTemperature(level, player.blockPosition()) : Float.NaN;
            int band = ToughAsNailsCompat.band(temperature);
            if (band == 0)
                temperature = Float.NaN; // a room too warm to change anything: as no room
            Sent last = SENT.get(player);
            // the client's rule gives the same result for any temperature of the same band
            if (last != null && last.level == level && last.band == band)
                continue;
            SENT.put(player, new Sent(level, band, temperature));
            FwNetwork.sendToPlayer(player, new RoomColdPayload(temperature));
        }
    }
}
