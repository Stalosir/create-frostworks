package com.frostworks.compat.tan;

import java.lang.reflect.Proxy;

import com.frostworks.Frostworks;
import com.frostworks.FwConfig;
import com.frostworks.platform.Loader;
import com.frostworks.room.Room;
import com.frostworks.room.RoomManager;
import com.frostworks.room.core.RoomStatus;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Tough As Nails: a player in a closed cold room is colder by Tough As Nails' own rules. A positional modifier of
 * its API ({@code TemperatureHelper.registerPositionalTemperatureModifier}), applied after the biome and the night and
 * before everything of the player's own (armour, nearby campfires, drinks), which works as usual.
 * <p>
 * Its API is one interface, one method and one enum, the same in Tough As Nails 9.2 (Forge 1.20.1) and 10.1 (NeoForge
 * 1.21.1): it is called by reflection. No compile dependency, and none of its classes is touched
 * unless it is loaded.
 * <p>
 * The rule ({@link #colder}): never colder than COLD (ICY, its freezing, is the 1.3's "glacial"), never warmer than the
 * place already is, nothing where it is COLD or ICY already.
 */
public final class ToughAsNailsCompat {
    private ToughAsNailsCompat() {}

    public static final String MODID = "toughasnails";
    private static final String API = "toughasnails.api.temperature.";

    /** Tough As Nails' levels ({@code TemperatureLevel}), coldest first, found by name. */
    static final String[] LEVELS = {"ICY", "COLD", "NEUTRAL", "WARM", "HOT"};
    static final int COLD = 1;

    private static boolean registered;

    /** Is the modifier registered (Tough As Nails loaded, its API found)? The option is read at each use. */
    public static boolean active() {
        return registered;
    }

    /**
     * Common setup (on the main thread: Tough As Nails' list of modifiers is a plain list): registers the modifier if
     * Tough As Nails is loaded. The option {@code toughAsNailsEnabled} is read at each use, so that it can be changed in
     * a running world (and the server config is not loaded yet at this point).
     */
    public static void init() {
        if (!Loader.isModLoaded(MODID))
            return;
        try {
            Class<?> modifier = Class.forName(API + "IPositionalTemperatureModifier");
            Object proxy = Proxy.newProxyInstance(modifier.getClassLoader(), new Class<?>[]{modifier},
                    (self, method, args) -> switch (method.getName()) {
                        case "modify" -> modify((Level) args[0], (BlockPos) args[1], (Enum<?>) args[2]);
                        case "hashCode" -> System.identityHashCode(self);
                        case "equals" -> self == args[0];
                        case "toString" -> "Frostworks cold rooms";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            Class.forName(API + "TemperatureHelper").getMethod("registerPositionalTemperatureModifier", modifier)
                    .invoke(null, proxy);
            registered = true;
            Frostworks.LOGGER.info("Tough As Nails found: closed cold rooms make their players cold");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            Frostworks.LOGGER.warn("Tough As Nails found, but not the temperature API this version knows: no cold rooms"
                    + " for it", e);
        }
    }

    /**
     * Tough As Nails' call: the level of the place {@code pos}. On the server, the closed room there; on the client
     * (its thermometer), only the player's own place, from what the server sent ({@link ClientRoomCold}).
     */
    static Enum<?> modify(Level level, BlockPos pos, Enum<?> current) {
        if (!FwConfig.get(FwConfig.TAN_ENABLED))
            return current;
        float temperature;
        if (level instanceof ServerLevel server)
            temperature = roomTemperature(server, pos);
        else if (level.isClientSide())
            temperature = ClientRoomCold.temperatureAt(level, pos);
        else
            return current;
        return Float.isNaN(temperature) ? current : colder(current, temperature);
    }

    /** The temperature of the closed room (COLD or INSUFFICIENT) whose cell is {@code pos}, NaN if none. A table look-up. */
    public static float roomTemperature(ServerLevel level, BlockPos pos) {
        Room room = RoomManager.get(level).roomAt(pos);
        if (room == null || room.status() != RoomStatus.COLD && room.status() != RoomStatus.INSUFFICIENT)
            return Float.NaN; // an OPEN room's cells reach far outside: never
        return room.temperature();
    }

    /** Which of the two thresholds a room at this temperature is past: 0 none, 1 one level colder, 2 COLD. */
    public static int band(float temperature) {
        if (Float.isNaN(temperature))
            return 0;
        if (temperature <= FwConfig.get(FwConfig.TAN_COLD_AT))
            return 2;
        return temperature <= FwConfig.get(FwConfig.TAN_COOLER_AT) ? 1 : 0;
    }

    /** {@code current} made colder by a room at this temperature, by the rule above. */
    static Enum<?> colder(Enum<?> current, float temperature) {
        int now = index(current.name());
        int target = colderIndex(now, band(temperature));
        if (target == now)
            return current;
        for (Enum<?> level : current.getDeclaringClass().getEnumConstants())
            if (level.name().equals(LEVELS[target]))
                return level;
        return current;
    }

    /** The rule on indexes of {@link #LEVELS}: COLD or colder (or a level unknown here) as it is. */
    static int colderIndex(int now, int band) {
        if (now <= COLD)
            return now;
        return switch (band) {
            case 2 -> COLD;
            case 1 -> now - 1;
            default -> now;
        };
    }

    private static int index(String name) {
        for (int i = 0; i < LEVELS.length; i++)
            if (LEVELS[i].equals(name))
                return i;
        return -1;
    }
}
