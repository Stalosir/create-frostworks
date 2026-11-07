package su.terrafirmagreg.tfcambiental.api;

import java.util.Optional;

import net.minecraft.world.entity.player.Player;
import su.terrafirmagreg.tfcambiental.modifier.TempModifierStorage;

/**
 * A stand-in for TFC Ambiental's (TerraFirmaGreg-Core) interface, with only what Frostworks calls. Compile only
 * (source set ambientalStub), never in our jar: at run time, the pack's own interface.
 */
public interface EnvironmentalTemperatureProvider {
    /** The environment's modifiers (climate, time of day, rain, wind, cosy...), or a heat pump's alone. */
    static void evaluateAll(Player player, TempModifierStorage storage, boolean nether) {
        throw new UnsupportedOperationException("stub");
    }

    /** The temperature a TerraFirmaGreg heat pump gives this player's position, if one covers it. */
    static Optional<Float> getHeatPumpTemperature(Player player) {
        throw new UnsupportedOperationException("stub");
    }
}
