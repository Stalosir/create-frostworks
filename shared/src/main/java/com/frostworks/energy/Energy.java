package com.frostworks.energy;

import com.frostworks.FwConfig;


/**
 * energy bookkeeping. Unit: su.s (stress x seconds). One set of rates, used both ways (no efficiency factor):
 * {@code kGaz} mB of cold gas per su.s, {@code kLiquide} mB of liquid (air or Frosium) per su.s.
 */
public final class Energy {
    private Energy() {}

    /**
     * Greate's scale ({@code greateStress}, common config): our machines' stress times this (0.1 on, 1 off). The
     * rates below are divided by it, so that the same setting moves the same fluids: what the loop takes and what it
     * gives back are both divided by 10, and it stays balanced.
     */
    public static double stressScale() {
        return com.frostworks.FwCommonConfig.stressScale();
    }

    public static double kGas() {
        return FwConfig.get(FwConfig.GAS_PER_STRESS) / stressScale();
    }

    public static double kLiquid() {
        return FwConfig.get(FwConfig.LIQUID_PER_ENERGY) / stressScale();
    }

    /** A stress for the player (su): whole, or with one decimal (Greate's scale: 25.6). */
    public static String su(double su) {
        double tenth = Math.round(su * 10) / 10.0;
        return tenth == Math.rint(tenth) ? String.format(java.util.Locale.ROOT, "%.0f", tenth)
                : String.format(java.util.Locale.ROOT, "%.1f", tenth);
    }

    public static double ofGas(double mB) {
        return mB / kGas();
    }

    public static double ofLiquid(double mB) {
        return mB / kLiquid();
    }

    /**
     * A block that takes part in the loop. Counters are cumulative since the block was placed (saved with it).
     * Entered: stress turned into gas or liquid. Returned: stress capacity given back and gas let out by the expander.
     * Stock: energy still held inside (buffers, fractions not yet turned into whole mB).
     */
    public interface Account {
        double energyIn();

        double energyOut();

        double energyStock();
    }
}
