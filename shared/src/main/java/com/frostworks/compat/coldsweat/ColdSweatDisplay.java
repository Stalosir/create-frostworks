package com.frostworks.compat.coldsweat;

import java.util.Locale;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.config.ConfigSettings;

/**
 * A temperature written as Cold Sweat's own gauge writes it, in the units the player chose in Cold Sweat
 * (Fahrenheit by default) with its display offset, so that the goggles and the gauge say the same thing. Called only
 * when Cold Sweat is loaded.
 */
public final class ColdSweatDisplay {
    private ColdSweatDisplay() {}

    public static String format(float celsius) {
        Temperature.Units units = ConfigSettings.UNITS.get();
        double value = Temperature.convert(celsius, Temperature.Units.C, units, true);
        // the gauge cuts to a whole number, then adds the offset; its own units have no whole numbers worth showing
        String number = units == Temperature.Units.MC ? String.format(Locale.ROOT, "%.2f", value)
                : String.valueOf((int) value + ConfigSettings.TEMP_OFFSET.get());
        return number + " " + units.getFormattedName().getString();
    }
}
