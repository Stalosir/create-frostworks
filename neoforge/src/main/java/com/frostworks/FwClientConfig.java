package com.frostworks;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client config (config/frostworks-client.toml): looks only, nothing the server needs. NeoForge reloads the file when
 * it changes on disk, and every value is read when used, so an edit shows in game without a restart.
 */
// The only copy. Forge's is generated from it (forge/build.gradle, generateConfigFromNeoForge).
public final class FwClientConfig {
    private FwClientConfig() {}

    public static final ModConfigSpec SPEC;

    // Strip curtain door
    public static final ModConfigSpec.DoubleValue FLAP_SWING_FACTOR;
    // Vent
    public static final ModConfigSpec.IntValue VENT_PROPELLER_RPM;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Strip curtain door").push("door");
        FLAP_SWING_FACTOR = b.comment("Swing of the strips when something walks through, as a share of the base swing",
                        "(45 to 60 degrees, from the speed): 0.1 gives 4.5 to 6 degrees, 1 the full swing, 0 no swing")
                .defineInRange("flapSwingFactor", 0.1, 0.0, 1.0);
        b.pop();

        b.comment("Vent").push("vent");
        VENT_PROPELLER_RPM = b.comment("Speed of the propeller of a vent that cools a room (rpm)",
                        "0: the propeller stays still")
                .defineInRange("ventPropellerRpm", 64, 0, 256);
        b.pop();

        SPEC = b.build();
    }

    /** Read at each tick; the default until the config is loaded. */
    public static int ventPropellerRpm() {
        try {
            return SPEC.isLoaded() ? VENT_PROPELLER_RPM.get() : VENT_PROPELLER_RPM.getDefault();
        } catch (IllegalStateException e) {
            return VENT_PROPELLER_RPM.getDefault();
        }
    }

    /** Read at each frame; the default until the config is loaded. */
    public static double flapSwingFactor() {
        try {
            return SPEC.isLoaded() ? FLAP_SWING_FACTOR.get() : FLAP_SWING_FACTOR.getDefault();
        } catch (IllegalStateException e) {
            return FLAP_SWING_FACTOR.getDefault();
        }
    }
}
