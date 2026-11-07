package com.frostworks.mixinplugin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Tells whether each of the mod's two optional hooks into Create can go in. MixinExtras
 * applies its injections late (after the mixins of the class, so that they can wrap redirects): what is checked here
 * is that Create's method still has the call our injection targets. The GameTests prove the hooks themselves. The
 * answer ({@code ready} or {@code missing}) goes in a system property per hook, read by {@code CreateHooks} (the plugin
 * may be loaded apart from the game's classes, a property reaches both).
 */
public class FwMixinPlugin implements IMixinConfigPlugin {

    /** A hook: its mixin, its property, the method of Create it goes into and the call it targets there. */
    private record Target(String mixin, String property, String method, String owner, String name, String desc) {}

    private static final List<Target> TARGETS = List.of(
            new Target("RotationPropagatorMixin", "frostworks.hook.overspeed", "propagateNewSource",
                    "net/createmod/catnip/config/ConfigBase$ConfigInt", "get", "()Ljava/lang/Object;"),
            new Target("CrushingWheelControllerMixin", "frostworks.hook.crushing", "applyRecipe",
                    // Forge 1.20.1, Create 6.0.4: the roll with Create's own random
                    "com/simibubi/create/content/processing/recipe/ProcessingRecipe", "rollResults", "()Ljava/util/List;"),
            // Cold processing at the millstone, and in Greate (its crushing wheels, its millstone, its roll)
            new Target("MillstoneMixin", "frostworks.hook.milling", "process",
                    "com/simibubi/create/content/kinetics/millstone/MillingRecipe", "rollResults", "()Ljava/util/List;"),
            // Cold processing at the basin (its items; its fluids are wrapped in the same method), Create's and Greate's
            new Target("BasinRecipeMixin", "frostworks.hook.basin", "apply",
                    "com/simibubi/create/content/processing/basin/BasinRecipe", "rollResults", "()Ljava/util/List;"),
            new Target("GreateBasinMixin", "frostworks.hook.greate_basin", "apply",
                    "electrolyte/greate/content/processing/basin/TieredBasinRecipe", "rollResults", "()Ljava/util/List;"),
            new Target("GreateCrusherMixin", "frostworks.hook.greate_crushing", "applyValidRecipe",
                    "electrolyte/greate/foundation/recipe/TieredRecipeHelper", "getItemResults",
                    "(Lnet/minecraft/world/item/crafting/Recipe;I)Ljava/util/List;"),
            new Target("GreateMillstoneMixin", "frostworks.hook.greate_milling", "processRecipe",
                    "electrolyte/greate/foundation/recipe/TieredRecipeHelper", "getItemResults",
                    "(Lnet/minecraft/world/item/crafting/Recipe;I)Ljava/util/List;"),
            // Greate's press (on a belt and on the ground, out of a basin)
            new Target("GreatePressMixin", "frostworks.hook.greate_pressing", "tryProcessOnBelt",
                    "electrolyte/greate/foundation/recipe/TieredRecipeApplier", "applyRecipeOn",
                    "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/crafting/Recipe;IZ)Ljava/util/List;"),
            // Greate's fans (an item on the ground) and its saw
            new Target("GreateFanMixin", "frostworks.hook.greate_fan", "applyProcessing",
                    "electrolyte/greate/content/kinetics/fan/processing/GreateFanProcessingTypes$TieredSplashingType", "process",
                    "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;ILelectrolyte/greate/content/kinetics/fan/TieredEncasedFanBlockEntity;)Ljava/util/List;"),
            new Target("GreateSawMixin", "frostworks.hook.greate_saw", "applyValidRecipe",
                    "com/simibubi/create/content/processing/recipe/ProcessingRecipe", "rollResults", "()Ljava/util/List;"),
            new Target("GreateRecipeHelperMixin", "frostworks.hook.greate_roll", "getItemResults",
                    "com/simibubi/create/content/processing/recipe/ProcessingRecipe", "rollResults",
                    "(Ljava/util/List;)Ljava/util/List;"),
            // TFC Ambiental (TerraFirmaGreg-Core): the environment of the player's temperature
            new Target("TemperatureCapabilityMixin", "frostworks.hook.tfc_ambiental", "evaluateModifiers",
                    "su/terrafirmagreg/tfcambiental/api/EnvironmentalTemperatureProvider", "evaluateAll",
                    "(Lnet/minecraft/world/entity/player/Player;Lsu/terrafirmagreg/tfcambiental/modifier/TempModifierStorage;Z)V"));

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        for (Target t : TARGETS) {
            if (!mixinClassName.endsWith("." + t.mixin))
                continue;
            boolean found = false;
            for (MethodNode method : targetClass.methods)
                if (method.name.equals(t.method))
                    for (AbstractInsnNode insn : method.instructions)
                        if (insn instanceof MethodInsnNode call && call.owner.equals(t.owner) && call.name.equals(t.name)
                                && call.desc.equals(t.desc))
                            found = true;
            System.setProperty(t.property, found ? "ready" : "missing");
        }
    }
}
