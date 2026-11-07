package su.terrafirmagreg.tfcambiental.modifier;

/**
 * A stand-in for TFC Ambiental's (TerraFirmaGreg-Core) class, with only what Frostworks calls. Compile only
 * (source set ambientalStub), never in our jar: at run time, the pack's own class.
 */
public class TempModifier {
    public TempModifier(float change, float potency) {
        throw new UnsupportedOperationException("stub");
    }

    public float getChange() {
        throw new UnsupportedOperationException("stub");
    }
}
