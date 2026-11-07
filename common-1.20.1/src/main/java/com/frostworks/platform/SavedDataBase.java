package com.frostworks.platform;

import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World data as Minecraft 1.20.1 saves and finds it (common-1.21.1 has the same class). The shared data
 * only writes its tag ({@link #write}) and reads it back.
 */
public abstract class SavedDataBase extends SavedData {
    /** Writes this data into {@code tag} and returns it. */
    public abstract CompoundTag write(CompoundTag tag);

    @Override
    public CompoundTag save(CompoundTag tag) {
        return write(tag);
    }

    /** The data of this level under {@code name}: loaded from the save, or made new. */
    public static <T extends SavedData> T get(ServerLevel level, Supplier<T> create, Function<CompoundTag, T> load,
                                              String name) {
        return level.getDataStorage().computeIfAbsent(load, create, name);
    }
}
