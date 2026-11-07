package com.frostworks.compat.coldsweat;

import java.util.function.Function;

import com.frostworks.FwConfig;
import com.frostworks.room.Room;
import com.frostworks.room.RoomManager;
import com.frostworks.room.core.RoomStatus;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.config.ConfigSettings;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Cold Sweat: the world temperature of a player in a closed cold room. The room counts as it is down to Cold
 * Sweat's Minimum Habitable Temperature (its config, read at each calculation), and below it as
 * {@code threshold - (threshold - room) x coldSweatFactor}: softened, -20 deg C still colder than -10 deg C. Never
 * warmer than the temperature it is given. The body is Cold Sweat's alone: it gets cold, then hurt, by its rules.
 * <p>
 * On Cold Sweat's WORLD layer, before its nearby blocks ({@link ColdSweatCompat}), worked out once a second (its tick
 * rate) and on the server only: its displays and thermometer read the world temperature the server sends.
 */
public class ColdRoomTempModifier extends TempModifier {
    /** Cold Sweat builds a modifier from its id with this constructor (a saved player, a new one). */
    public ColdRoomTempModifier() {}

    @Override
    protected Function<Double, Double> calculate(LivingEntity entity, Temperature.Trait trait) {
        double effective = effectiveTemperature(entity);
        if (Double.isNaN(effective))
            return temperature -> temperature;
        return temperature -> Math.min(temperature, effective);
    }

    /** The room's temperature as Cold Sweat should feel it (its own units), NaN if none. */
    static double effectiveTemperature(LivingEntity entity) {
        if (!FwConfig.get(FwConfig.CS_ENABLED) || !(entity.level() instanceof ServerLevel level))
            return Double.NaN;
        Room room = RoomManager.get(level).roomAt(entity.blockPosition());
        if (room == null || room.status() != RoomStatus.COLD && room.status() != RoomStatus.INSUFFICIENT)
            return Double.NaN; // an OPEN room's cells reach far outside: never
        return felt(Temperature.convert(room.temperature(), Temperature.Units.C, Temperature.Units.MC, true));
    }

    /**
     * The temperature Cold Sweat feels in a room at this temperature (deg C), for the goggles: what a player meets
     * inside, unless the place is colder already. Read only when Cold Sweat is loaded.
     */
    public static float feltCelsius(float roomCelsius) {
        double felt = felt(Temperature.convert(roomCelsius, Temperature.Units.C, Temperature.Units.MC, true));
        return (float) Temperature.convert(felt, Temperature.Units.MC, Temperature.Units.C, true);
    }

    private static double felt(double room) {
        return soften(room, ConfigSettings.MIN_TEMP.get(), FwConfig.get(FwConfig.CS_FACTOR));
    }

    /**
     * The room as it is down to the threshold, {@code threshold - (threshold - room) x factor} below it: no jump at the
     * threshold (seen in game: the world temperature fell from the biome's to the threshold's at once), and as smooth
     * as the room cools (0.5 deg C a second by default, recalculated each second).
     */
    static double soften(double room, double threshold, double factor) {
        if (room >= threshold)
            return room;
        return threshold - (threshold - room) * factor;
    }
}
