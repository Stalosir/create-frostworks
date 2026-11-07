package com.frostworks.room;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.frostworks.platform.SavedDataBase;
import com.frostworks.room.core.RoomRecords;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * The rooms of one dimension, saved with the world ({@code data/frostworks_rooms.dat} of the
 * dimension), server side: the same in single player and on a dedicated server. The bytes are
 * {@link RoomRecords#encode}; the {@link RoomManager} gives its rooms when the world is saved.
 */
public final class RoomSavedData extends SavedDataBase {
    public static final String NAME = "frostworks_rooms";

    private List<RoomRecords.Record> records = new ArrayList<>();
    /** The names given to rooms (a name tag on a cold wall), by room number: a number survives the searches, the
     * name with it (a split: the biggest piece keeps it; a merge: the room it overlaps the most). */
    private final java.util.Map<Integer, String> names = new java.util.HashMap<>();
    @Nullable
    private Supplier<List<RoomRecords.Record>> live;

    public static RoomSavedData get(ServerLevel level) {
        return SavedDataBase.get(level, RoomSavedData::new, RoomSavedData::load, NAME);
    }

    private static RoomSavedData load(CompoundTag tag) {
        RoomSavedData data = new RoomSavedData();
        data.records = RoomRecords.decode(tag.getByteArray("Rooms"));
        data.readNames(tag);
        return data;
    }

    @Override
    public CompoundTag write(CompoundTag tag) {
        tag.putInt("Version", RoomRecords.VERSION);
        tag.putByteArray("Rooms", RoomRecords.encode(snapshot()));
        CompoundTag named = new CompoundTag();
        names.forEach((id, name) -> named.putString(String.valueOf(id), name));
        tag.put("Names", named);
        return tag;
    }

    private void readNames(CompoundTag tag) {
        names.clear();
        CompoundTag named = tag.getCompound("Names");
        for (String key : named.getAllKeys())
            try {
                names.put(Integer.parseInt(key), named.getString(key));
            } catch (NumberFormatException ignored) {
            }
    }

    /** A room's name, or null. */
    @Nullable
    String name(int id) {
        return names.get(id);
    }

    /** Names a room (null or empty: no name). */
    void rename(int id, @Nullable String name) {
        boolean changed = name == null || name.isEmpty() ? names.remove(id) != null : !name.equals(names.put(id, name));
        if (changed)
            setDirty();
    }

    /** What would be saved now: the live rooms if a manager runs, else what was loaded. */
    List<RoomRecords.Record> snapshot() {
        if (live != null)
            records = live.get();
        return records;
    }

    /** The rooms as loaded (or as last saved). */
    List<RoomRecords.Record> records() {
        return records;
    }

    /** The manager that holds the live rooms from now on. */
    void attach(Supplier<List<RoomRecords.Record>> live) {
        this.live = live;
    }

    /**
     * Tests: what a restart does to the data in memory: encode the live rooms, forget the manager, decode them back.
     * The disk is not involved.
     */
    void reloadThroughBytes() {
        CompoundTag tag = write(new CompoundTag());
        live = null;
        records = RoomRecords.decode(tag.getByteArray("Rooms"));
        readNames(tag);
    }
}
