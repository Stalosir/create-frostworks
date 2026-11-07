package com.frostworks.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.frostworks.FwConfig;
import com.frostworks.content.vent.VentBlock;
import com.frostworks.content.vent.VentBlockEntity;
import com.frostworks.room.Room;
import com.frostworks.room.RoomManager;
import com.frostworks.room.RoomWalls;
import com.frostworks.room.core.RoomScan;
import com.frostworks.room.core.RoomStatus;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** What the goggles show of a wall, door, passage or vent, the same on both loaders. */
public final class RoomInfo {
    private RoomInfo() {}

    /** Builds the overlay data for a wall, door, passage or vent. Also used by /frostworks room. */
    public static RoomInfoPayload describe(ServerLevel level, BlockPos pos, BlockState state) {
        RoomManager manager = RoomManager.get(level);
        List<Room> rooms = new ArrayList<>();
        RoomInfoPayload.VentView vent = null;
        if (state.getBlock() instanceof VentBlock && level.getBlockEntity(pos) instanceof VentBlockEntity be) {
            Room room = manager.roomOfVent(pos);
            boolean blocked = RoomWalls.isWall(level.getBlockState(pos.relative(state.getValue(VentBlock.FACING))));
            vent = new RoomInfoPayload.VentView(be.isActive(), be.gas().getAmount(), be.bufferCapacity(),
                    room == null ? -1 : room.id, blocked, be.coverage(),
                    be.coldFluid() == null ? 0 : be.coldFluid().temperature(), be.fluidKey());
            if (room != null)
                rooms.add(room);
        } else {
            // A vent shows its own room only: the open air behind it may be another (OPEN) room, which made one vent
            // look like it belonged to two rooms (After our tests)
            rooms.addAll(manager.roomsAround(pos));
            // closed rooms first: the room a wall belongs to before the open air on its other side
            rooms.sort(Comparator.comparing(r -> r.outcome != RoomScan.Outcome.CLOSED));
        }
        // The vents still needed, counted in vents of the warmest cold fluid (cold gas)
        List<com.frostworks.content.cold.ColdFluid> fluids = com.frostworks.content.cold.ColdFluids.all(level);
        int coverage = fluids.isEmpty() ? 1 : fluids.get(fluids.size() - 1).coverage();
        int cap = FwConfig.get(FwConfig.ROOM_SEARCH_CAP);
        List<RoomInfoPayload.RoomView> views = new ArrayList<>(rooms.size());
        for (Room r : rooms)
            views.add(new RoomInfoPayload.RoomView(r.id, r.temperature(), r.volume, r.capacity(), r.status(),
                    r.activeVents(), r.ventCount(), coverage, cap, manager.isDirty(r),
                    // fastest speed inside: faster in the cold, faster still on Frosium alone
                    com.frostworks.overspeed.Overspeed.allowedRpm(r),
                    com.frostworks.overspeed.Overspeed.createLimit(),
                    shortName(manager.name(r)), coldSweat(r),
                    // The speed line only while the bonus is on; the lower stress inside
                    com.frostworks.overspeed.Overspeed.enabled(),
                    com.frostworks.overspeed.ColdStress.percent(
                            com.frostworks.overspeed.ColdStress.stepAt(r.status(), r.temperature()))));
        return new RoomInfoPayload(pos, views, vent, FwConfig.get(FwConfig.OVERSPEED_ENABLED)
                && !com.frostworks.CreateHooks.ready(com.frostworks.CreateHooks.Hook.OVERSPEED));
    }

    /** What Cold Sweat feels in this closed room (deg C), NaN without Cold Sweat, the option off, or an open room. */
    private static float coldSweat(Room room) {
        if (!com.frostworks.platform.Loader.isModLoaded(com.frostworks.compat.coldsweat.ColdSweatCompat.MODID)
                || !FwConfig.get(FwConfig.CS_ENABLED)
                || room.status() != RoomStatus.COLD && room.status() != RoomStatus.INSUFFICIENT)
            return Float.NaN;
        return com.frostworks.compat.coldsweat.ColdRoomTempModifier.feltCelsius(room.temperature());
    }

    /** A room name as sent to the goggles (a name over {@link RoomInfoPayload#NAME_MAX} characters is cut). */
    @org.jetbrains.annotations.Nullable
    private static String shortName(@org.jetbrains.annotations.Nullable String name) {
        return name == null || name.length() <= RoomInfoPayload.NAME_MAX ? name : name.substring(0, RoomInfoPayload.NAME_MAX);
    }
}
