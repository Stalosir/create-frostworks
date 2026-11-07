package com.frostworks.compat.display;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.frostworks.overspeed.Overspeed;
import com.frostworks.room.Room;
import com.frostworks.room.RoomManager;
import com.frostworks.room.core.RoomStatus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;

/**
 * The Display Link: what a block of a cold room's structure (a cold vent or a cold wall) tells of its room, one
 * reading per display source ({@link FwDisplaySources}). Each is a translatable text (each player reads it in their own
 * language, even from a dedicated server), short and plain enough for a nixie tube: ASCII minus, short units.
 * <p>
 * Another reading is one more constant here and its lang keys ({@code frostworks.display_source.room_<name>} and the
 * value's), nothing else.
 */
public enum RoomReading {
    /** COLD, INSUFFICIENT, OPEN, PENDING: the goggles' words (their lang keys), without the goggles' colours. */
    STATUS {
        @Override
        MutableComponent read(Room room, RoomManager rooms) {
            return Component.translatable("frostworks.status." + room.status().name().toLowerCase(java.util.Locale.ROOT));
        }
    },
    /** In degrees, rounded: "-10 °C". */
    TEMPERATURE {
        @Override
        MutableComponent read(Room room, RoomManager rooms) {
            return Component.translatable("frostworks.display_source.value.temperature",
                    String.valueOf(Math.round(room.temperature())));
        }
    },
    /** The fastest speed the room allows, as the goggles say it: "320 RPM". */
    MAX_SPEED {
        @Override
        MutableComponent read(Room room, RoomManager rooms) {
            return Component.translatable("frostworks.display_source.value.max_speed",
                    String.valueOf(Overspeed.allowedRpm(room)));
        }
    },
    /** Active vents out of all of the room's: "3/4". */
    VENTS {
        @Override
        MutableComponent read(Room room, RoomManager rooms) {
            return Component.translatable("frostworks.display_source.value.vents", String.valueOf(room.activeVents()),
                    String.valueOf(room.ventCount()));
        }
    },
    /**
     * The goggles' capacity line: the cells the active vents cover out of the room's volume, "72 / 96" (an OPEN room has
     * no volume, its search stopped early: the capacity alone).
     */
    CAPACITY {
        @Override
        MutableComponent read(Room room, RoomManager rooms) {
            if (room.status() == RoomStatus.OPEN)
                return Component.translatable("frostworks.display_source.value.capacity_open", String.valueOf(room.capacity()));
            return Component.translatable("frostworks.display_source.value.capacity", String.valueOf(room.capacity()),
                    String.valueOf(room.volume));
        }
    },
    /** The name a name tag gave the room (as typed, not translated), or "Unnamed". */
    NAME {
        @Override
        MutableComponent read(Room room, RoomManager rooms) {
            String name = rooms.name(room);
            return name == null ? Component.translatable("frostworks.display_source.value.no_name") : Component.literal(name);
        }
    };

    /** This reading of a room found just now (never kept: the next reading looks again). */
    abstract MutableComponent read(Room room, RoomManager rooms);

    /** The display source's id: {@code frostworks:room_<name>}. */
    public String id() {
        return "room_" + name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * The room of a block of the structure: a vent's own room, otherwise the room the block touches (a cold wall: the
     * closed room first, as the goggles do). Plain look-ups in the room manager's tables, no search started.
     */
    @Nullable
    public static Room roomOf(ServerLevel level, BlockPos pos) {
        RoomManager rooms = RoomManager.get(level);
        Room room = rooms.roomOfVent(pos);
        if (room != null)
            return room;
        List<Room> around = rooms.roomsAround(pos);
        return around.isEmpty() ? null : around.get(0);
    }

    /** What the block at {@code pos} shows now, or the "no room" text. */
    public MutableComponent readAt(ServerLevel level, BlockPos pos) {
        @Nullable Room room = roomOf(level, pos);
        return room == null ? Component.translatable("frostworks.display_source.no_room") : read(room, RoomManager.get(level));
    }
}
