package com.frostworks.room;

import java.util.Locale;

import com.frostworks.CreateHooks;
import com.frostworks.Frostworks;
import com.frostworks.FwConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * A player in a room at {@code coldExposureTemperature} or below (-15 deg C by default, the deep cold
 * of Frosium) slowly freezes, with vanilla's own freezing (the frost on the screen's edges, the slowness and its
 * zoom), but it never hurts: the freeze stops at {@link #MAX_FROST} of the full frost, far from "fully frozen", the
 * only state where vanilla deals freezing damage. A line above the hotbar gives the temperature.
 * <p>
 * Vanilla thaws 2 a tick out of powder snow; this adds 3, so the frost builds up by 1 a tick (about 2.6 s to where it stops).
 * Whoever cannot freeze (leather armour, creative, spectator: {@code canFreeze}) is left alone.
 * <p>
 * With TFC Ambiental (TerraFirmaGreg's), once our room is in its body temperature (the hook ready), none of this:
 * TFC Ambiental has its own frost on the screen and its own effects, and the room now acts through them.
 */
public final class ColdExposure {
    private ColdExposure() {}

    /** The freeze stops at 37.5 % (the slowness was too strong at the full frost). */
    static final float MAX_FROST = 0.375f;

    /** Each tick of a player (the loader's tick event, in {@code FwEvents}). */
    public static void tick(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.canFreeze() || player.isDeadOrDying()
                || CreateHooks.ready(CreateHooks.Hook.TFC_AMBIENTAL))
            return;
        Room room = RoomManager.get(level).roomAt(player.blockPosition());
        if (room != null)
            expose(player, room.temperature());
    }

    /** One tick of a player in a room at this temperature. */
    public static void expose(Player player, float temperature) {
        if (temperature > FwConfig.get(FwConfig.COLD_EXPOSURE_TEMPERATURE) || !player.canFreeze())
            return;
        // vanilla slows by the share frozen: held at MAX_FROST, the slowness (and the frost on the screen) is that share
        int cap = Math.min(player.getTicksRequiredToFreeze() - 1, (int) (player.getTicksRequiredToFreeze() * MAX_FROST));
        player.setTicksFrozen(Math.min(cap, player.getTicksFrozen() + 3));
        if (player.tickCount % 20 == 0)
            player.displayClientMessage(Component.translatable("frostworks.cold.low_temperature",
                    String.format(Locale.ROOT, "%.1f", temperature)).withStyle(ChatFormatting.AQUA), true);
    }
}
