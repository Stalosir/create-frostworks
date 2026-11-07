package com.frostworks.room;

import com.frostworks.room.core.RoomStatus;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * A COLD room down to its target (from its cold fluids, −10 °C on cold gas) keeps ice and snow
 * layers from melting, whatever the biome or the light (vanilla melts them under a block light above 11, a torch next
 * to them). Asked by {@code IceBlockMixin} and {@code SnowLayerBlockMixin}.
 */
public final class KeptFrozen {
    private KeptFrozen() {}

    public static boolean at(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server))
            return false;
        Room room = RoomManager.get(server).roomAt(pos);
        return room != null && room.status() == RoomStatus.COLD
                && room.temperature() <= room.coldTarget() + 0.001; // Its target, from its cold fluids
    }
}
