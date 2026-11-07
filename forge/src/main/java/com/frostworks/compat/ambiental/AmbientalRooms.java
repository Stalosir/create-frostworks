package com.frostworks.compat.ambiental;

import java.util.function.Consumer;

import com.frostworks.FwConfig;
import com.frostworks.content.cold.ColdProcessing;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import su.terrafirmagreg.tfcambiental.api.EnvironmentalTemperatureProvider;
import su.terrafirmagreg.tfcambiental.modifier.TempModifier;
import su.terrafirmagreg.tfcambiental.modifier.TempModifierStorage;

/**
 * TFC Ambiental (TerraFirmaGreg's, Forge 1.20.1 only): a closed cold room in the player's body temperature, as
 * TerraFirmaGreg's heat pump does it. Called only by {@code TemperatureCapabilityMixin}, so only with TFC Ambiental.
 * <ul>
 * <li>a heat pump covers the player: TerraFirmaGreg's rule, nothing of ours (the heat pump is made for it, and heats);</li>
 * <li>in a closed room (COLD or INSUFFICIENT): the environment's modifiers (climate, time of day, rain, wind, indoor
 * warmth...) are replaced by one, the room's temperature, at the heat pump's potency (10); clothes, effects, carried
 * items and nearby blocks still count, as with the heat pump;</li>
 * <li>{@code tfcAmbientalRoomPercent} (100 by default): 100 = the room's temperature, 50 = halfway between the normal
 * environment and the room, 0 = nothing;</li>
 * <li>never warmer: if the normal environment is colder than the room (a cold biome's winter), it stays;</li>
 * <li>server only: TFC Ambiental sends the client what the server worked out.</li>
 * </ul>
 */
public final class AmbientalRooms {
    private AmbientalRooms() {}

    /** The heat pump's potency (TerraFirmaGreg's EnvironmentalTemperatureProvider.evaluateAll). */
    private static final float POTENCY = 10f;

    /**
     * @param original the environment as TFC Ambiental gathers it, into the player's storage
     * @param originalInto the same, into another storage (to compare)
     */
    public static void environment(Player player, TempModifierStorage storage, boolean nether, Runnable original,
                                   Consumer<TempModifierStorage> originalInto) {
        int percent = FwConfig.get(FwConfig.TFC_AMBIENTAL_PERCENT);
        if (percent <= 0 || !(player.level() instanceof ServerLevel level)
                || EnvironmentalTemperatureProvider.getHeatPumpTemperature(player).isPresent()) {
            original.run();
            return;
        }
        float room = ColdProcessing.closedRoomTemperature(level, player.blockPosition());
        if (Float.isNaN(room)) {
            original.run();
            return;
        }
        TempModifierStorage normal = new TempModifierStorage();
        originalInto.accept(normal);
        float normalTemperature = normal.getTargetTemperature();
        float target = normalTemperature + (room - normalTemperature) * percent / 100f;
        if (target >= normalTemperature) { // never warmer than without the room
            for (TempModifier modifier : normal)
                storage.add(modifier);
            return;
        }
        storage.add(new TempModifier(target, POTENCY));
    }
}
