package com.frostworks;

import java.util.function.Supplier;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config (config/frostworks-server.toml, or serverconfig/ of the world). Values are read when used, and fall
 * back to their default while the config is not loaded yet (a client builds block entities before it receives the
 * server config).
 * <p>
 * The file is sorted in five groups: {@code rooms} (detection, temperature, overspeed), {@code blocks} (one section
 * per block), {@code processing} (the energy rates, the chilled ores), {@code integrations} (other mods: Tough As Nails, Cold Sweat) and
 * {@code debug}. The fields below keep their old grouping; the builder gives each one's section.
 */
// The only copy. Forge's is generated from it (forge/build.gradle, generateConfigFromNeoForge).
public final class FwConfig {
    private FwConfig() {}

    public enum StressMode {
        /** The network sees exactly the set amount of su, whatever the speed (impact = setting / rpm). */
        TOTAL,
        /** The setting is the impact per rpm, like a regular Create machine. */
        PER_RPM
    }

    public static final ModConfigSpec SPEC;

    // Compressor
    public static final ModConfigSpec.IntValue STRESS_MIN;
    public static final ModConfigSpec.IntValue STRESS_MAX;
    public static final ModConfigSpec.IntValue STRESS_STEP;
    public static final ModConfigSpec.IntValue STRESS_DEFAULT;
    public static final ModConfigSpec.EnumValue<StressMode> STRESS_MODE;
    public static final ModConfigSpec.DoubleValue GAS_PER_STRESS;
    public static final ModConfigSpec.IntValue COMPRESSOR_BUFFER;
    public static final ModConfigSpec.IntValue COMPRESSOR_MIN_RPM;
    public static final ModConfigSpec.DoubleValue INFUSER_STRESS_IMPACT;
    public static final ModConfigSpec.IntValue INFUSER_TANK_CAPACITY;
    public static final ModConfigSpec.IntValue INFUSER_DWELL_TICKS;
    public static final ModConfigSpec.IntValue INFUSER_ICE_GAS;
    public static final ModConfigSpec.IntValue INFUSER_FROSIUM_CAPACITY;
    // Chilled ores
    public static final ModConfigSpec.IntValue CHILLED_INFUSION_FROSIUM;
    // The cold processing recipes (frostworks:cold_process), all of them
    public static final ModConfigSpec.BooleanValue COLD_PROCESSING_ENABLED;

    // Vent
    public static final ModConfigSpec.IntValue VENT_ACTIVE_TICKS;
    public static final ModConfigSpec.IntValue VENT_BUFFER;

    // Rooms
    public static final ModConfigSpec.IntValue ROOM_SEARCH_CAP;
    public static final ModConfigSpec.IntValue RESCAN_DELAY;
    public static final ModConfigSpec.IntValue MAX_RESCANS_PER_TICK;
    public static final ModConfigSpec.IntValue MAX_CELLS_PER_TICK;
    // Debug
    public static final ModConfigSpec.BooleanValue DEBUG_ROOM_LEAKS;

    // Stress loop
    public static final ModConfigSpec.DoubleValue LIQUID_PER_ENERGY;
    public static final ModConfigSpec.IntValue RESERVOIR_CAPACITY;
    public static final ModConfigSpec.IntValue EXPANDER_LIQUID_RATE;
    public static final ModConfigSpec.IntValue EXPANDER_BUFFER;

    // Distillation tower (replaces the 1-wide column)
    public static final ModConfigSpec.IntValue TOWER_MAX_LAYERS;
    public static final ModConfigSpec.IntValue TOWER_CAPACITY_PER_BLOCK;
    public static final ModConfigSpec.DoubleValue TOWER_GAS_SHARE;
    public static final ModConfigSpec.IntValue TOWER_GAS_PER_LAYER;
    public static final ModConfigSpec.IntValue TOWER_REFERENCE_RPM;
    public static final ModConfigSpec.DoubleValue TOWER_STRESS_PER_RPM;
    public static final ModConfigSpec.DoubleValue TOWER_WATER_PER_LIQUID;
    // Cold gas used per mB of Frosium made (our round ratio)
    public static final ModConfigSpec.DoubleValue TOWER_GAS_PER_LIQUID;
    public static final ModConfigSpec.IntValue TOWER_FROSIUM_PER_ICE;

    // Strip curtain door
    public static final ModConfigSpec.DoubleValue FLAP_MIN_SPEED;

    // Cold wall tint
    public static final ModConfigSpec.IntValue WALL_TINT_MAX_BLOCKS;
    // Tough As Nails
    public static final ModConfigSpec.BooleanValue TAN_ENABLED;
    public static final ModConfigSpec.DoubleValue TAN_COOLER_AT;
    public static final ModConfigSpec.DoubleValue TAN_COLD_AT;
    // Cold Sweat
    public static final ModConfigSpec.BooleanValue CS_ENABLED;
    public static final ModConfigSpec.DoubleValue CS_FACTOR;
    // Food preserving (TerraFirmaCraft): the decay modifiers of our two traits
    public static final ModConfigSpec.DoubleValue TFC_COLD_PRESERVED;
    public static final ModConfigSpec.DoubleValue TFC_DEEP_COLD_PRESERVED;
    // TFC Ambiental (TerraFirmaGreg, Forge 1.20.1): how much the closed room imposes its temperature on the player
    public static final ModConfigSpec.IntValue TFC_AMBIENTAL_PERCENT;

    // Overspeed
    public static final ModConfigSpec.BooleanValue OVERSPEED_ENABLED;
    public static final ModConfigSpec.IntValue OVERSPEED_BONUS;
    public static final ModConfigSpec.IntValue OVERSPEED_BONUS_FROSIUM;
    public static final ModConfigSpec.IntValue OVERSPEED_GRACE_SECONDS;

    // Temperature
    public static final ModConfigSpec.DoubleValue AMBIENT_TEMPERATURE;
    public static final ModConfigSpec.DoubleValue COLD_EXPOSURE_TEMPERATURE;
    public static final ModConfigSpec.DoubleValue COOLING_RATE;
    public static final ModConfigSpec.DoubleValue WARMING_RATE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        // The sections sorted in five groups (rooms, blocks, processing, integrations, debug); every setting kept,
        // with its name and its value

        b.comment("Cold rooms: how they are found, their temperature, and what their cold allows").push("rooms");

        b.comment("Room detection").push("detection");
        ROOM_SEARCH_CAP = b.comment("A search that finds more cells than this declares the room OPEN")
                .defineInRange("roomSearchCap", 20_000, 1, 2_000_000);
        RESCAN_DELAY = b.comment("Delay between a wall/vent/door change and the new search (ticks)")
                .defineInRange("rescanDelayTicks", 10, 0, 1200);
        MAX_RESCANS_PER_TICK = b.comment("Searches started at most per tick and per dimension")
                .defineInRange("maxRescansPerTick", 4, 1, 1000);
        MAX_CELLS_PER_TICK = b.comment("Cells visited at most per tick and per dimension; a longer search goes on next tick")
                .defineInRange("maxCellsPerTick", 50_000, 100, 10_000_000);
        b.pop();

        b.comment("Room temperature (deg C)").push("temperature");
        AMBIENT_TEMPERATURE = b.defineInRange("ambient", 20.0, -100, 100);
        COLD_EXPOSURE_TEMPERATURE = b.comment("At or below this, a player in the room slowly freezes (vanilla frost, never deadly)")
                .defineInRange("coldExposureTemperature", -15.0, -273, 100);
        COOLING_RATE = b.comment("deg C per second while COLD").defineInRange("coolingRate", 0.5, 0, 1000);
        WARMING_RATE = b.comment("deg C per second otherwise").defineInRange("warmingRate", 0.5, 0, 1000);
        b.pop();

        b.comment("Overspeed: in a COLD room, machines of the frostworks:overspeed_machines tag and the blocks of",
                "frostworks:overspeed_transmission may turn faster than Create's maxRotationSpeed, by overspeedBonusPercent.")
                .push("overspeed");
        OVERSPEED_ENABLED = b.define("overspeedEnabled", true);
        OVERSPEED_BONUS = b.comment("Speed bonus in a COLD room, in percent of Create's maxRotationSpeed:",
                        "25 = 125 % of it, 320 rpm with Create's default 256")
                .defineInRange("overspeedBonusPercent", 25, 0, 1000);
        OVERSPEED_BONUS_FROSIUM = b.comment("Speed bonus in a COLD room at -20 deg C or colder (its cold fluids: Frosium), in percent of",
                        "Create's maxRotationSpeed: 50 = 150 % of it, 384 rpm with Create's default 256")
                .defineInRange("overspeedBonusPercentFrosium", 50, 0, 1000);
        OVERSPEED_GRACE_SECONDS = b.comment("After the room stops being COLD, overspeed stays allowed this long (s);",
                        "then the overspeeding block breaks, like in plain Create")
                .defineInRange("overspeedGraceSeconds", 30, 0, 3600);
        b.pop();

        b.pop(); // rooms

        b.comment("Blocks: each machine and building block of the mod").push("blocks");

        b.comment("Compressor: a Create kinetic block that takes stress and outputs cold gas").push("compressor");
        STRESS_MIN = b.comment("Lowest stress setting (su)").defineInRange("stressMin", 256, 1, 1_000_000);
        STRESS_MAX = b.comment("Highest stress setting (su)").defineInRange("stressMax", 32_768, 1, 1_000_000);
        STRESS_STEP = b.comment("Step of the value panel (su)").defineInRange("stressStep", 256, 1, 1_000_000);
        STRESS_DEFAULT = b.comment("Setting of a newly placed compressor (su)")
                .defineInRange("stressDefault", 256, 1, 1_000_000); // was 2048
        STRESS_MODE = b.comment("TOTAL: the network sees exactly the setting (su), whatever the speed.",
                        "PER_RPM: the setting is the impact per rpm, like a regular machine (network sees setting x rpm).")
                .defineEnum("stressMode", StressMode.TOTAL);
        GAS_PER_STRESS = b.comment("Cold gas produced per second, per su actually applied (mB/s/su); the other rate of",
                        "the stress loop is processing.energy.liquidPerEnergy.",
                        "Default: 2048 su -> 153.6 mB/s, enough for 6 vents at 25 mB/s.")
                .defineInRange("gasPerStressUnit", 0.075, 0, 1000);
        COMPRESSOR_BUFFER = b.comment("Internal gas buffer of the compressor (mB)")
                .defineInRange("bufferCapacity", 1000, 1, 1_000_000);
        COMPRESSOR_MIN_RPM = b.comment("Least speed the compressor works at; above it, only the stress taken counts (rpm)")
                .defineInRange("compressorMinRpm", 8, 1, 256);
        b.pop();

        b.comment("Cryogenic infuser").push("infuser");
        INFUSER_STRESS_IMPACT = b.comment("Stress impact of the cryogenic infuser (su per rpm), Create's press by default")
                .defineInRange("infuserStressImpact", 8.0, 0, 1_000);
        INFUSER_TANK_CAPACITY = b.comment("Cold gas tank of the cryogenic infuser (mB)")
                .defineInRange("infuserTankCapacity", 1000, 1, 1_000_000);
        INFUSER_DWELL_TICKS = b.comment("Time the infuser's arm stays down on the item before it is infused (ticks)")
                .defineInRange("infuserDwellTicks", 40, 0, 1_200);
        INFUSER_ICE_GAS = b.comment("Cold gas the infuser uses to freeze one ice in a basin of water (mB)")
                .defineInRange("infuserIceGas", 250, 1, 100_000);
        INFUSER_FROSIUM_CAPACITY = b.comment("Frosium tank of the cryogenic infuser, for the chilled ores (mB)")
                .defineInRange("infuserFrosiumTankCapacity", 1000, 1, 1_000_000);
        b.pop();

        // What a vent uses, covers and cools to is in the cold fluid recipes (frostworks:cold_fluid)
        b.comment("Vent: diffuses a cold fluid into a room (consumption, coverage, target: the cold fluid recipes)")
                .push("vent");
        VENT_ACTIVE_TICKS = b.comment("A vent stays active this long after it last had gas (ticks)")
                .defineInRange("activeWindowTicks", 20, 1, 12_000);
        VENT_BUFFER = b.comment("Internal gas buffer of a vent (mB)").defineInRange("bufferCapacity", 250, 1, 100_000);
        b.pop();

        b.comment("Insulated reservoir").push("reservoir");
        RESERVOIR_CAPACITY = b.comment("Insulated reservoir: capacity per block (mB)")
                .defineInRange("reservoirCapacityPerBlock", 8000, 1, 1_000_000);
        b.pop();

        b.comment("Expander: Frosium back into cold gas and stress").push("expander");
        EXPANDER_LIQUID_RATE = b.comment("Liquid expanded per second by the expander while it has some (mB/s)")
                .defineInRange("expanderLiquidRate", 20, 1, 100_000);
        EXPANDER_BUFFER = b.comment("Expander buffers: liquid in, cold gas out (mB)")
                .defineInRange("expanderBuffer", 1000, 1, 1_000_000);
        b.pop();

        b.comment("Distillation tower: a 2 x 2 multiblock, a layer of column bases and up to towerMaxLayers - 1 layers of",
                "column segments. Cold gas +",
                "water + rotation -> Frosium. Energy as in processing.energy.").push("tower");
        TOWER_MAX_LAYERS = b.comment("Layers of a tower, at most, base included (a layer = 4 blocks)")
                .defineInRange("towerMaxLayers", 8, 1, 64);
        TOWER_CAPACITY_PER_BLOCK = b.comment("Inner storage per block of the column, in mB (the bases hold nothing).",
                        "The product takes its room first; the inputs of the recipe share what it leaves")
                .defineInRange("towerCapacityPerBlock", 4000, 1, 1_000_000);
        TOWER_GAS_SHARE = b.comment("For the Frosium recipe only (the energy yield): share of the inputs' room given to cold gas,",
                        "the rest to its other inputs. Other distilling recipes use the shares written in their files")
                .defineInRange("towerGasShare", 0.5, 0.01, 0.99);
        TOWER_GAS_PER_LAYER = b.comment("Cold gas processed per second by each layer of the tower at towerReferenceRpm (mB/s)")
                .defineInRange("towerGasPerLayer", 20, 1, 100_000);
        TOWER_REFERENCE_RPM = b.comment("Speed at which the tower processes towerGasPerLayer; it goes as fast as it turns (rpm)")
                .defineInRange("towerReferenceRpm", 64, 1, 256);
        TOWER_STRESS_PER_RPM = b.comment("Stress drawn by each layer of the tower, per rpm (su): a round 3,",
                        "as Create's machines (it was a share of the energy of the gas processed, 2.08 su per rpm)")
                .defineInRange("towerStressPerRpm", 3.0, 0, 1000);
        TOWER_WATER_PER_LIQUID = b.comment("Water used per mB of liquid produced (lost, not counted as energy)")
                .defineInRange("towerWaterPerLiquid", 0.5, 0, 100);
        TOWER_GAS_PER_LIQUID = b.comment("Cold gas used per mB of Frosium made. The tower still takes towerGasPerLayer a",
                        "layer and its stress; it makes the Frosium this ratio allows, never more than the energy it gets",
                        "(the gas beyond that energy is lost, as the water). Default: 1.75, so one Packed Ice makes 2000 mB",
                        "of Frosium out of 3500 mB of cold gas and 1000 mB of water")
                .defineInRange("towerGasPerLiquid", 1.75, 0.01, 1000);
        TOWER_FROSIUM_PER_ICE = b.comment("Frosium made per packed ice used, the unit of the ice values (data map",
                        "frostworks:ice_value; plain ice is worth 1/9 of it), in mB")
                .defineInRange("towerFrosiumPerPackedIce", 2000, 1, 1_000_000);
        b.pop();

        b.comment("Strip curtain door").push("door");
        FLAP_MIN_SPEED = b.comment("Least real movement of an entity in the door that swings the strips (blocks per",
                        "tick; walking is about 0.2, sneaking about 0.07). It stops counting as moving below half of it.")
                .defineInRange("flapMinSpeed", 0.02, 0, 10);
        b.pop();

        b.comment("Cold wall tint: a dye on a cold wall colours every cold wall joined to it face to face").push("wall");
        WALL_TINT_MAX_BLOCKS = b.comment("Most cold walls one dye colours; a bigger structure is left as it is, and the",
                        "player is told")
                .defineInRange("wallTintMaxBlocks", 4096, 1, 65536);
        b.pop();

        b.pop(); // blocks

        b.comment("Processing: the energy rates and the recipes worked out from the settings").push("processing");

        b.comment("Stress loop. Energy unit: su.s (stress x seconds). One set of rates, used both ways:",
                "compressor: 1 su.s -> gasPerStressUnit mB of cold gas (blocks.compressor);",
                "liquid: 1 su.s <-> liquidPerEnergy mB of Frosium.").push("energy");
        LIQUID_PER_ENERGY = b.comment("kLiquide: mB of liquid per su.s of energy")
                .defineInRange("liquidPerEnergy", 0.025, 1e-6, 1000);
        b.pop();

        b.comment("Chilled ores: their infusion").push("chilledOres");
        CHILLED_INFUSION_FROSIUM = b.comment("Frosium the infuser uses to chill one ore (mB)")
                .defineInRange("chilledInfusionFrosium", 100, 1, 100_000);
        b.pop();

        b.comment("Cold processing: the frostworks:cold_process recipes, which change what a machine gives in a closed",
                "cold room (data/<namespace>/recipe/...; the chilled ores' crushing is one of them)").push("coldProcessing");
        COLD_PROCESSING_ENABLED = b.comment("Off: every machine gives its normal recipe, whatever the room")
                .define("coldProcessingEnabled", true);
        b.pop();

        b.pop(); // processing

        b.comment("Integrations with other mods: each one does nothing when its mod is absent").push("integrations");

        b.comment("Tough As Nails: a player in a closed cold room is colder by Tough As Nails' own rules (its",
                "armour, campfires and drinks work as usual). Never colder than COLD (never ICY), never warmer than",
                "the place already is, nothing where it is COLD or ICY already. Without Tough As Nails, nothing.")
                .push("toughAsNails");
        TAN_ENABLED = b.define("toughAsNailsEnabled", true);
        TAN_COOLER_AT = b.comment("At or below this, the room makes the place one level colder (deg C)")
                .defineInRange("toughAsNailsCoolerAt", 0.0, -273, 100);
        TAN_COLD_AT = b.comment("At or below this, the room makes the place COLD, whatever the biome (deg C)")
                .defineInRange("toughAsNailsColdAt", -15.0, -273, 100);
        b.pop();

        b.comment("Cold Sweat: in a closed cold room, the world temperature of Cold Sweat is the room's, softened:",
                "below Cold Sweat's Minimum Habitable Temperature, threshold - (threshold - room) x coldSweatFactor; never",
                "warmer than it was. Cold Sweat's own rules apply: an unprotected player gets cold, then hurt, as in a",
                "cold biome, and its gear protects as usual. Without Cold Sweat, nothing.")
                .push("coldSweat");
        CS_ENABLED = b.define("coldSweatEnabled", true);
        CS_FACTOR = b.comment("How much of the room's cold below the threshold Cold Sweat feels: 1 = all of it, 0 = none")
                .defineInRange("coldSweatFactor", 0.5, 0, 1);
        b.pop();

        b.comment("TerraFirmaCraft food preserving: the cryogenic infuser gives a food a preservation trait for good",
                "(frostworks:food_preserving recipes). Each value is the trait's decay modifier: 0.4 = lasts 2.5 times",
                "longer. It multiplies with the food's other traits, as every TFC trait does. Changing it changes the",
                "dates of the foods already preserved. Without TerraFirmaCraft, nothing.").push("tfcFood");
        TFC_COLD_PRESERVED = b.comment("Cryo-preserved (cold gas), level 1")
                .defineInRange("coldPreservedModifier", 0.4, 0.01, 1);
        TFC_DEEP_COLD_PRESERVED = b.comment("Cryo-preserved (Frosium), level 2")
                .defineInRange("deepColdPreservedModifier", 0.25, 0.01, 1);
        b.pop();

        b.comment("TFC Ambiental (TerraFirmaGreg's, Forge 1.20.1 only): in a closed cold room, the player's environment",
                "is the room's temperature, as TerraFirmaGreg's heat pump does (climate, time of day, rain, wind and indoor",
                "warmth replaced; clothes, effects and nearby blocks still count). A heat pump covering the player wins.",
                "Never warmer than the player would be without the room. Without TFC Ambiental, nothing.").push("tfcAmbiental");
        TFC_AMBIENTAL_PERCENT = b.comment("How much of the room: 100 = the room's temperature (as a heat pump), 50 = halfway",
                        "between the normal environment and the room, 0 = the room does nothing")
                .defineInRange("tfcAmbientalRoomPercent", 100, 0, 100);
        b.pop();

        b.pop(); // integrations

        b.comment("Diagnostics").push("debug");
        DEBUG_ROOM_LEAKS = b.comment("Log every room found OPEN (or waiting for an unloaded chunk): bounding box of the",
                        "search, reason (cell cap reached, left the world, unloaded chunk) and the cell farthest from the vent")
                .define("roomLeaks", false);
        b.pop();

        SPEC = b.build();
    }

    public static int get(ModConfigSpec.IntValue value) {
        return safe(value::get, value::getDefault);
    }

    public static boolean get(ModConfigSpec.BooleanValue value) {
        return safe(value::get, value::getDefault);
    }

    public static double get(ModConfigSpec.DoubleValue value) {
        return safe(value::get, value::getDefault);
    }

    public static <E extends Enum<E>> E get(ModConfigSpec.EnumValue<E> value) {
        return safe(value::get, value::getDefault);
    }

    private static <T> T safe(Supplier<T> getter, Supplier<T> fallback) {
        try {
            return SPEC.isLoaded() ? getter.get() : fallback.get();
        } catch (IllegalStateException e) {
            return fallback.get();
        }
    }
}
