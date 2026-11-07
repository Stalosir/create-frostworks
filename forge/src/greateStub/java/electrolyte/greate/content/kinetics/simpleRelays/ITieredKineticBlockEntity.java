package electrolyte.greate.content.kinetics.simpleRelays;

import net.minecraft.world.level.block.Block;

/**
 * Compile-time stand-in for Greate's interface of the same name, the one method our Greate shaft passage needs
 * (compile only, never in our jar: at run time, Greate's own interface and its own default method). Greate caps a
 * kinetic network at the lowest {@code getMaxCapacityFromBlock} of its members that implement it.
 */
public interface ITieredKineticBlockEntity {
    default float getMaxCapacityFromBlock(Block block) {
        return Float.MAX_VALUE;
    }
}
