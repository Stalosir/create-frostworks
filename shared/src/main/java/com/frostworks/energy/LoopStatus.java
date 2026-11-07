package com.frostworks.energy;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Live loop balance above the hotbar, for the players who asked for it with /frostworks loop status. */
public final class LoopStatus {
    private LoopStatus() {}

    public static final int RADIUS = 32;
    private static final Set<UUID> WATCHERS = new HashSet<>();

    /** @return true if the player now watches */
    public static boolean toggle(ServerPlayer player) {
        if (WATCHERS.remove(player.getUUID()))
            return false;
        WATCHERS.add(player.getUUID());
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (WATCHERS.isEmpty() || server.getTickCount() % 10 != 0)
            return;
        WATCHERS.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        for (UUID id : WATCHERS) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            player.displayClientMessage(text(LoopLedger.around(player.serverLevel(), player.blockPosition(), RADIUS)),
                    true);
        }
    }

    public static Component text(LoopLedger l) {
        if (l.blocks() == 0)
            return Component.translatable("frostworks.command.loop.none", RADIUS);
        return Component.translatable("frostworks.command.loop.status", fmt(l.in()), fmt(l.out()),
                fmt(l.stressOut()), fmt(l.gasOut()), fmt(l.stock()), String.format(Locale.ROOT, "%.2f", 100 * l.ratio()));
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }

    public static void clear() {
        WATCHERS.clear();
    }
}
