package com.frostworks;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config (config/frostworks-common.toml): what is read before a world loads, such as the recipe
 * conditions. Read when used; the default while the config is not loaded yet.
 */
// The only copy. Forge's is generated from it (forge/build.gradle, generateConfigFromNeoForge).
public final class FwCommonConfig {
    private FwCommonConfig() {}

    public enum TfgCompat {
        /** On when TerraFirmaGreg (mod id {@code tfg}) is loaded. */
        AUTO,
        ON,
        OFF
    }

    public enum SpeedBonus {
        /** Off when TerraFirmaGreg (mod id {@code tfg}) is loaded, on otherwise. */
        AUTO,
        ON,
        OFF
    }

    public enum GreateStress {
        /** On when Greate (mod id {@code greate}) is loaded. */
        AUTO,
        ON,
        OFF
    }

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.EnumValue<TfgCompat> TFG_COMPAT;

    public static final ModConfigSpec.EnumValue<SpeedBonus> COLD_SPEED_BONUS;

    public static final ModConfigSpec.EnumValue<GreateStress> GREATE_STRESS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Integrations with modpacks").push("integrations");
        TFG_COMPAT = b.comment("TerraFirmaGreg recipes (condition frostworks:tfg_compat): the pack's materials in place of",
                        "Create's and Minecraft's, and no recipes for the Minecraft ores the pack does not have.",
                        "AUTO: on when the mod tfg is loaded. ON / OFF: always / never. Read when recipes load (/reload).")
                .defineEnum("tfgCompat", TfgCompat.AUTO);
        COLD_SPEED_BONUS = b.comment("The cold room's speed bonus (machines past Create's speed limit in a COLD room). The cold's",
                        "lower stress stays on either way. AUTO: off when the mod tfg is loaded, on otherwise.",
                        "ON / OFF: always / never. Read when used.")
                .defineEnum("coldSpeedBonus", SpeedBonus.AUTO);
        GREATE_STRESS = b.comment("Greate's stress scale: the stress of our machines divided by 10 (compressor, tower, cryogenic",
                        "infuser) and what the expander gives back divided by 10 too, for the small networks of Greate's",
                        "first tiers (32 su). The same fluids for the same setting: the loop stays balanced.",
                        "AUTO: on when the mod greate is loaded. ON / OFF: always / never. Read when used.")
                .defineEnum("greateStress", GreateStress.AUTO);
        b.pop();

        SPEC = b.build();
    }

    /** Are the TerraFirmaGreg recipes on? AUTO (the default, also before the config loads): is the mod tfg loaded. */
    public static boolean tfgCompat() {
        TfgCompat value;
        try {
            value = TFG_COMPAT.get();
        } catch (IllegalStateException notLoaded) {
            value = TfgCompat.AUTO;
        }
        return switch (value) {
            case ON -> true;
            case OFF -> false;
            case AUTO -> com.frostworks.platform.Loader.isModLoaded("tfg");
        };
    }

    /** Is the cold room's speed bonus on? AUTO (the default, also before the config loads): is the mod tfg absent. */
    public static boolean coldSpeedBonus() {
        SpeedBonus value;
        try {
            value = COLD_SPEED_BONUS.get();
        } catch (IllegalStateException notLoaded) {
            value = SpeedBonus.AUTO;
        }
        return switch (value) {
            case ON -> true;
            case OFF -> false;
            case AUTO -> !com.frostworks.platform.Loader.isModLoaded("tfg");
        };
    }

    /** The stress of our machines is multiplied by this (0.1 with Greate's scale on, see {@code greateStress}). */
    public static double stressScale() {
        GreateStress value;
        try {
            value = GREATE_STRESS.get();
        } catch (IllegalStateException notLoaded) {
            value = GreateStress.AUTO;
        }
        boolean on = switch (value) {
            case ON -> true;
            case OFF -> false;
            case AUTO -> com.frostworks.platform.Loader.isModLoaded("greate");
        };
        return on ? 0.1 : 1;
    }
}
