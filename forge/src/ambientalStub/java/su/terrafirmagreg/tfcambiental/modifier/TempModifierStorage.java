package su.terrafirmagreg.tfcambiental.modifier;

/**
 * A stand-in for TFC Ambiental's (TerraFirmaGreg-Core) class, with only what Frostworks calls. Compile only
 * (source set ambientalStub), never in our jar: at run time, the pack's own class.
 */
public class TempModifierStorage implements Iterable<TempModifier> {
    public TempModifierStorage() {
        throw new UnsupportedOperationException("stub");
    }

    public void add(TempModifier value) {
        throw new UnsupportedOperationException("stub");
    }

    /** The sum of the changes, good and bad. */
    public float getTargetTemperature() {
        throw new UnsupportedOperationException("stub");
    }

    @Override
    public java.util.Iterator<TempModifier> iterator() {
        throw new UnsupportedOperationException("stub");
    }
}
