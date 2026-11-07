package com.frostworks;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The mod's two hooks into Create, and what happens when Create changes. Each one is an optional
 * MixinExtras injection ({@code require = 0}): the mixin plugin ({@code mixinplugin/FwMixinPlugin}) checks, as Create's
 * class is loaded, that the call it wraps is still there, and writes {@code ready} or {@code missing} in a system
 * property. A missing hook never stops the game: the feature it carries turns itself off, the log says so once at
 * start-up, and (for the overspeed) the goggles too.
 */
public final class CreateHooks {
    private CreateHooks() {}

    /** One hook: its property, the Create class it goes into, what it carries and what is lost without it. */
    public enum Hook {
        OVERSPEED("frostworks.hook.overspeed", "com.simibubi.create.content.kinetics.RotationPropagator",
                "overspeed in cold rooms", "machines keep Create's limit everywhere"),
        CRUSHING("frostworks.hook.crushing", "com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity",
                "cold processing at the crushing wheels", "the crushing wheels keep their recipes, whatever the room"),
        // Cold processing at the millstone, and in Greate (Forge 1.20.1 only, when Greate is there)
        MILLING("frostworks.hook.milling", "com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity",
                "cold processing at the millstone", "the millstone keeps its recipes, whatever the room"),
        // Cold processing at the basin (the mixer and the press over it), and at Greate's basin
        BASIN("frostworks.hook.basin", "com.simibubi.create.content.processing.basin.BasinRecipe",
                "cold processing at the basin (mixing, compacting)", "the basin keeps its recipes, whatever the room"),
        GREATE_BASIN("frostworks.hook.greate_basin", "greate", "electrolyte.greate.content.processing.basin.TieredBasinRecipe",
                "cold processing at Greate's basin (mixing, compacting, brewing)", "Greate's basin keeps its recipes"),
        GREATE_CRUSHING("frostworks.hook.greate_crushing", "greate",
                "electrolyte.greate.content.kinetics.crusher.TieredCrushingWheelControllerBlockEntity",
                "cold processing at Greate's crushing wheels", "Greate's crushing wheels keep their recipes"),
        GREATE_MILLING("frostworks.hook.greate_milling", "greate",
                "electrolyte.greate.content.kinetics.millstone.TieredMillstoneBlockEntity",
                "cold processing at Greate's millstone", "Greate's millstone keeps its recipes"),
        GREATE_PRESSING("frostworks.hook.greate_pressing", "greate",
                "electrolyte.greate.content.kinetics.press.TieredMechanicalPressBlockEntity",
                "cold processing at Greate's press", "Greate's press keeps its recipes"),
        GREATE_FAN("frostworks.hook.greate_fan", "greate", "electrolyte.greate.content.kinetics.fan.TieredFanProcessing",
                "cold processing at Greate's fans (items on the ground)", "Greate's fans keep their recipes"),
        GREATE_SAW("frostworks.hook.greate_saw", "greate", "electrolyte.greate.content.kinetics.saw.TieredSawBlockEntity",
                "cold processing at Greate's saw", "Greate's saw keeps its recipes"),
        GREATE_ROLL("frostworks.hook.greate_roll", "greate", "electrolyte.greate.foundation.recipe.TieredRecipeHelper",
                "cold processing in Greate's roll", "Greate's machines keep their recipes"),
        // TFC Ambiental, TerraFirmaGreg's (in TerraFirmaGreg-Core, mod "tfg"; Forge 1.20.1 only)
        TFC_AMBIENTAL("frostworks.hook.tfc_ambiental", "tfg", "su.terrafirmagreg.tfcambiental.capability.TemperatureCapability",
                "cold rooms in TFC Ambiental's body temperature", "TFC Ambiental ignores the cold rooms");

        public final String property, mod, targetClass, feature, without;

        Hook(String property, String targetClass, String feature, String without) {
            this(property, "create", targetClass, feature, without);
        }

        Hook(String property, String mod, String targetClass, String feature, String without) {
            this.property = property;
            this.mod = mod;
            this.targetClass = targetClass;
            this.feature = feature;
            this.without = without;
        }
    }

    /** Tests: hooks made to look missing (what a Create version without the call would give). */
    private static final Set<Hook> SIMULATED_MISSING = ConcurrentHashMap.newKeySet();

    /** Is this hook in Create's code? */
    public static boolean ready(Hook hook) {
        return !SIMULATED_MISSING.contains(hook) && "ready".equals(System.getProperty(hook.property));
    }

    /** Tests only: make a hook look missing, or put it back. */
    public static void simulateMissing(Hook hook, boolean missing) {
        if (missing)
            SIMULATED_MISSING.add(hook);
        else
            SIMULATED_MISSING.remove(hook);
    }

    /** At start-up: loads Create's classes (their mixins apply then) and logs each hook, in the same words. */
    public static void logAll() {
        for (Hook hook : Hook.values()) {
            if (!com.frostworks.platform.Loader.isModLoaded(hook.mod))
                continue; // another mod's hook (Greate), without that mod: nothing to say
            try {
                Class.forName(hook.targetClass);
            } catch (ClassNotFoundException e) {
                // the class is gone: the property stays unset, the hook counts as missing
            }
            if (ready(hook))
                Frostworks.LOGGER.info("Create hook ready: {}", hook.feature);
            else
                Frostworks.LOGGER.warn("Create hook missing ({}), another version of Create? {} is off: {}. Nothing else "
                        + "changes.", System.getProperty(hook.property, "not checked"), hook.feature, hook.without);
        }
    }
}
