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
                    "com/simibubi/create/content/processing/recipe/StandardProcessingRecipe", "rollResults",
                    "(Lnet/minecraft/util/RandomSource;)Ljava/util/List;"),
            // Cold processing at the millstone
            new Target("MillstoneMixin", "frostworks.hook.milling", "process",
                    "com/simibubi/create/content/kinetics/millstone/MillingRecipe", "rollResults",
                    "(Lnet/minecraft/util/RandomSource;)Ljava/util/List;"),
            // Cold processing at the basin (its items; its fluids are wrapped in the same method)
            new Target("BasinRecipeMixin", "frostworks.hook.basin", "apply",
                    "com/simibubi/create/content/processing/basin/BasinRecipe", "rollResults",
                    "(Lnet/minecraft/util/RandomSource;)Ljava/util/List;"));

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
