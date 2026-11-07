package com.frostworks.room;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.frostworks.network.FwNetwork;
import com.frostworks.network.RoomInfoPayload;
import com.frostworks.room.core.RoomStatus;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Text lines describing rooms, for the goggles overlay (client) and /frostworks room (server). */
public final class RoomText {
    private RoomText() {}

    /** As Cold Sweat's gauge writes it when Cold Sweat is here (its units), else in deg C. */
    private static String coldSweatTemperature(float celsius) {
        if (com.frostworks.platform.Loader.isModLoaded(com.frostworks.compat.coldsweat.ColdSweatCompat.MODID))
            return com.frostworks.compat.coldsweat.ColdSweatDisplay.format(celsius);
        return String.format(Locale.ROOT, "%.1f °C", celsius);
    }

    public static List<MutableComponent> lines(RoomInfoPayload info) {
        List<MutableComponent> lines = new ArrayList<>();
        RoomInfoPayload.VentView vent = info.vent();
        if (vent != null) {
            lines.add(Component.translatable("frostworks.goggles.vent").withStyle(ChatFormatting.WHITE));
            lines.add(indent(Component.translatable(vent.active() ? "frostworks.goggles.vent.active"
                            : "frostworks.goggles.vent.idle")
                    .withStyle(vent.active() ? ChatFormatting.AQUA : ChatFormatting.GOLD)));
            // The fluid it holds, by its own name, and what its cold fluid recipe does with it
            lines.add(indent((vent.fluid().isEmpty()
                    ? Component.translatable("frostworks.goggles.vent.empty", vent.gas(), vent.capacity())
                    : Component.translatable("frostworks.goggles.vent.fluid", Component.translatable(vent.fluid()),
                            vent.gas(), vent.capacity())).withStyle(ChatFormatting.GRAY)));
            boolean deep = vent.temperature() <= com.frostworks.overspeed.Overspeed.DEEP_COLD;
            if (vent.temperature() < 0)
                lines.add(indent(Component.translatable("frostworks.goggles.vent.temperature", vent.temperature())
                        .withStyle(deep ? ChatFormatting.WHITE : ChatFormatting.GRAY)));
            else if (!vent.fluid().isEmpty())
                lines.add(indent(Component.translatable("frostworks.goggles.vent.no_recipe")
                        .withStyle(ChatFormatting.RED)));
            lines.add(indent(Component.translatable("frostworks.goggles.vent.coverage", vent.coverage())
                    .withStyle(deep ? ChatFormatting.WHITE : ChatFormatting.GRAY)));
            if (vent.blocked())
                lines.add(indent(Component.translatable("frostworks.goggles.vent.blocked")
                        .withStyle(ChatFormatting.RED)));
            else if (vent.roomId() < 0)
                lines.add(indent(Component.translatable("frostworks.goggles.vent.no_room")
                        .withStyle(ChatFormatting.GRAY)));
        }
        if (info.rooms().isEmpty()) {
            if (vent == null)
                lines.add(Component.translatable("frostworks.goggles.no_room").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        for (RoomInfoPayload.RoomView room : info.rooms()) {
            // A named room shows its name in place of its number
            lines.add((room.name() != null ? Component.translatable("frostworks.goggles.room_named", room.name())
                    : Component.translatable("frostworks.goggles.room", room.id())).withStyle(ChatFormatting.WHITE));
            MutableComponent temperature = Component.translatable("frostworks.goggles.temperature",
                    String.format(Locale.ROOT, "%.1f", room.temperature()));
            // With Cold Sweat, what it feels inside, so that players know what to expect, in the units of its gauge
            if (room.hasColdSweat())
                temperature.append(Component.translatable("frostworks.goggles.cold_sweat_temperature",
                        coldSweatTemperature(room.coldSweat())));
            lines.add(indent(temperature.withStyle(ChatFormatting.AQUA)));
            if (room.status() == RoomStatus.OPEN) {
                // The search stopped early: its cell count is not a volume
                lines.add(indent(Component.translatable("frostworks.goggles.volume_open", room.searchCap())
                        .withStyle(ChatFormatting.GRAY)));
                lines.add(indent(Component.translatable("frostworks.goggles.capacity_open", room.capacity(),
                        room.activeVents(), room.vents()).withStyle(ChatFormatting.GRAY)));
            } else {
                lines.add(indent(Component.translatable("frostworks.goggles.volume", room.volume())
                        .withStyle(ChatFormatting.GRAY)));
                lines.add(indent(Component.translatable("frostworks.goggles.capacity", room.capacity(),
                        room.volume(), room.activeVents(), room.vents()).withStyle(ChatFormatting.GRAY)));
            }
            lines.add(indent(Component.translatable("frostworks.goggles.status", status(room.status()))
                    .withStyle(ChatFormatting.GRAY)));
            if (room.status() != RoomStatus.COLD)
                lines.add(indent(reason(room).withStyle(ChatFormatting.YELLOW)));
            // The max speed only while the cold's speed bonus is on (off by default in TerraFirmaGreg)
            if (room.speedBonus()) {
                MutableComponent speed = Component.translatable("frostworks.goggles.max_speed", room.maxRpm())
                        .withStyle(ChatFormatting.AQUA);
                int bonus = room.baseRpm() <= 0 ? 0 : Math.round((room.maxRpm() * 100f) / room.baseRpm()) - 100;
                if (bonus > 0) // The cold's bonus, orange for the cold, red from +50 % (Frosium)
                    speed.append(Component.literal(" (+" + bonus + " %)")
                            .withStyle(bonus >= 50 ? ChatFormatting.RED : ChatFormatting.GOLD));
                lines.add(indent(speed));
            }
            // The machines' lower stress inside, with its share, the same colours as the speed bonus
            if (room.stressCut() > 0)
                lines.add(indent(Component.translatable("frostworks.goggles.low_stress").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(" (-" + room.stressCut() + " %)")
                                .withStyle(room.stressCut() >= 20 ? ChatFormatting.RED : ChatFormatting.GOLD))));
            if (info.overspeedOff()) // The overspeed hook is missing (another Create version)
                lines.add(indent(Component.translatable("frostworks.goggles.overspeed_off").withStyle(ChatFormatting.RED)));
            if (room.recomputing())
                lines.add(indent(Component.translatable("frostworks.goggles.recomputing")
                        .withStyle(ChatFormatting.DARK_GRAY)));
        }
        return lines;
    }

    public static MutableComponent status(RoomStatus status) {
        ChatFormatting color = switch (status) {
            case COLD -> ChatFormatting.AQUA;
            case INSUFFICIENT -> ChatFormatting.GOLD;
            case OPEN -> ChatFormatting.RED;
            case PENDING -> ChatFormatting.DARK_GRAY;
        };
        return Component.translatable("frostworks.status." + status.name().toLowerCase(Locale.ROOT))
                .withStyle(color);
    }

    /** "capacity 80/96: 1 vent of 16 missing", etc. */
    public static MutableComponent reason(RoomInfoPayload.RoomView room) {
        String key = FwNetwork.reasonKey(room.status());
        return switch (room.status()) {
            case INSUFFICIENT -> Component.translatable(key, room.capacity(), room.volume(), room.missingVents(),
                    room.coverage());
            case OPEN -> Component.translatable(key, room.searchCap());
            default -> Component.translatable(key);
        };
    }

    private static MutableComponent indent(MutableComponent c) {
        return Component.literal(" ").append(c);
    }
}
