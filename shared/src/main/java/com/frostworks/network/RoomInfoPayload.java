package com.frostworks.network;

import java.util.ArrayList;
import java.util.List;

import com.frostworks.room.core.RoomStatus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Server -> client: state of the rooms around a looked-at block, for the goggles overlay.
 *
 * @param vent vent details when the block is a vent, else null
 */
public record RoomInfoPayload(BlockPos pos, List<RoomView> rooms, VentView vent, boolean overspeedOff) {
    /** The longest room name sent (longer ones are cut). */
    public static final int NAME_MAX = 64;

    /** {@code overspeedOff}, the overspeed hook is missing (another Create version): the goggles say it. */
    public RoomInfoPayload(BlockPos pos, List<RoomView> rooms, VentView vent) {
        this(pos, rooms, vent, false);
    }

    public record RoomView(int id, float temperature, int volume, int capacity, RoomStatus status, int activeVents,
                           int vents, int coverage, int searchCap, boolean recomputing, int maxRpm,
                           int baseRpm, @org.jetbrains.annotations.Nullable String name,
                           float coldSweat, boolean speedBonus, int stressCut) {
        /** A room without a name, nor Cold Sweat (the Ponder scene's goggles). */
        public RoomView(int id, float temperature, int volume, int capacity, RoomStatus status, int activeVents,
                        int vents, int coverage, int searchCap, boolean recomputing, int maxRpm, int baseRpm) {
            this(id, temperature, volume, capacity, status, activeVents, vents, coverage, searchCap, recomputing,
                    maxRpm, baseRpm, null, Float.NaN, true,
                    com.frostworks.overspeed.ColdStress.percent(com.frostworks.overspeed.ColdStress.stepAt(status, temperature)));
        }

        /*
         * {@code speedBonus}, the cold's speed bonus is on (the goggles show the max speed only then); {@code
         * stressCut}, how much less stress the machines inside apply (0, 10 or 20 %).
         */
        /** {@code coldSweat}, the temperature Cold Sweat feels inside (deg C), NaN without Cold Sweat. */
        public boolean hasColdSweat() {
            return !Float.isNaN(coldSweat);
        }

        public int missingVents() {
            return RoomStatus.missingVents(volume, capacity, coverage);
        }
    }

    /**
     * @param roomId room the vent blows into, -1 if none
     * @param blocked true if the vent blows into a wall
     * @param coverage cells it cools with what it holds
     * @param temperature the target of its cold fluid (deg C), 0 if what it holds has no cold fluid recipe
     * @param fluid the translation key of the fluid it holds (or held last), empty if none
     */
    public record VentView(boolean active, int gas, int capacity, int roomId, boolean blocked, int coverage,
                           int temperature, String fluid) {}

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(rooms.size());
        for (RoomView r : rooms) {
            buf.writeVarInt(r.id);
            buf.writeFloat(r.temperature);
            buf.writeVarInt(r.volume);
            buf.writeVarInt(r.capacity);
            buf.writeEnum(r.status);
            buf.writeVarInt(r.activeVents);
            buf.writeVarInt(r.vents);
            buf.writeVarInt(r.coverage);
            buf.writeVarInt(r.searchCap);
            buf.writeBoolean(r.recomputing);
            buf.writeVarInt(r.maxRpm);
            buf.writeVarInt(r.baseRpm);
            // The name a name tag gave the room (an anvil allows 50 characters)
            buf.writeBoolean(r.name != null);
            if (r.name != null)
                buf.writeUtf(r.name, NAME_MAX);
            buf.writeFloat(r.coldSweat); // NaN without Cold Sweat
            buf.writeBoolean(r.speedBonus);
            buf.writeVarInt(r.stressCut);
        }
        buf.writeBoolean(vent != null);
        if (vent != null) {
            buf.writeBoolean(vent.active);
            buf.writeVarInt(vent.gas);
            buf.writeVarInt(vent.capacity);
            buf.writeVarInt(vent.roomId);
            buf.writeBoolean(vent.blocked);
            buf.writeVarInt(vent.coverage);
            buf.writeInt(vent.temperature);
            buf.writeUtf(vent.fluid);
        }
        buf.writeBoolean(overspeedOff);
    }

    public static RoomInfoPayload read(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int n = Math.min(buf.readVarInt(), 6);
        List<RoomView> rooms = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            rooms.add(new RoomView(buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(),
                    buf.readEnum(RoomStatus.class), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean() ? buf.readUtf(NAME_MAX) : null, buf.readFloat(), buf.readBoolean(),
                    buf.readVarInt()));
        VentView vent = buf.readBoolean()
                ? new VentView(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readBoolean(), buf.readVarInt(), buf.readInt(), buf.readUtf())
                : null;
        return new RoomInfoPayload(pos, rooms, vent, buf.readBoolean());
    }
}
