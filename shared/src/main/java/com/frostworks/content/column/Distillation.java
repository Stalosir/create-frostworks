package com.frostworks.content.column;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.frostworks.Frostworks;
import com.frostworks.FwConfig;
import com.frostworks.platform.Mc;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * One craft of the distillation tower, as its recipe file says it ({@code data/frostworks/recipe/distilling/}, type
 * {@code frostworks:distilling}). Written once; each Minecraft version wraps it in its own {@code DistillingRecipe}.
 * <pre>
 * {"type": "frostworks:distilling",
 *  "inputs": [{"fluid": "frostworks:cold_gas"}, {"fluid": "minecraft:water"}],
 *  "ice": true,
 *  "result": "frostworks:frosium",
 *  "yield": "energy"}
 * </pre>
 * The first input drives the tower: it is taken at the tower's rate ({@code towerGasPerLayer} a layer at
 * {@code towerReferenceRpm}). Two yields:
 * <ul>
 * <li>{@code "energy"}: the product holds the energy of the gas and of the stress, and the expander gives it back. Only
 * for cold gas into one of the mod's cold liquids (otherwise a data pack could make energy out of nothing); the other
 * inputs, the ice and the shares are the tower's settings ({@code towerWaterPerLiquid}, {@code towerFrosiumPerPackedIce},
 * {@code towerGasShare});</li>
 * <li>{@code {"per_input": x}}: x mB of product for each mB of the first input, no energy kept. Each input then says its
 * {@code share} of the room (equal shares if none) and its {@code per_output} (mB used per mB made, the first input
 * aside), and {@code ice_per_output} the packed ice used per mB made.</li>
 * </ul>
 * Inputs are a fluid ({@code "fluid"}) or a fluid tag ({@code "tag"}). Up to {@link #MAX_INPUTS}.
 */
public final class Distillation {
    public static final int MAX_INPUTS = 4;
    /** The mod's own craft, which old towers (before recipes) were making. */
    public static final ResourceLocation FROSIUM = Frostworks.id("distilling/frosium");

    /** One input: a fluid or a fluid tag, its share of the room, what it takes per mB made. */
    public record Input(@Nullable Fluid fluid, @Nullable TagKey<Fluid> tag, double share, double perOutput) {
        public boolean takes(Fluid f) {
            return fluid != null ? f.isSame(fluid) : tag != null && f.defaultFluidState().is(tag);
        }

        /** A fluid it takes (the fluid, or the first of the tag), for the JEI page. */
        public @Nullable Fluid shown() {
            if (fluid != null)
                return fluid;
            for (Fluid f : BuiltInRegistries.FLUID)
                if (f.isSource(f.defaultFluidState()) && f.defaultFluidState().is(tag))
                    return f;
            return null;
        }
    }

    public final ResourceLocation id;
    public final List<Input> inputs;
    public final boolean ice;
    public final Fluid result;
    /** True: the energy yield; false: {@link #perInput} mB made per mB of the first input. */
    public final boolean energy;
    public final double perInput;
    public final double icePerOutput;

    public Distillation(ResourceLocation id, List<Input> inputs, boolean ice, Fluid result, boolean energy,
                        double perInput, double icePerOutput) {
        this.id = id;
        this.inputs = List.copyOf(inputs);
        this.ice = ice;
        this.result = result;
        this.energy = energy;
        this.perInput = perInput;
        this.icePerOutput = icePerOutput;
    }

    /** The same craft under its recipe id (Minecraft 1.21.1 gives the id to the recipe's holder, not to the file). */
    public Distillation withId(ResourceLocation id) {
        return new Distillation(id, inputs, ice, result, energy, perInput, icePerOutput);
    }

    /** Does this craft take this fluid as one of its inputs? */
    public boolean takes(Fluid f) {
        return index(f) >= 0;
    }

    /** The input that takes this fluid, or -1. */
    public int index(Fluid f) {
        for (int i = 0; i < inputs.size(); i++)
            if (inputs.get(i).takes(f))
                return i;
        return -1;
    }

    /** Can it still be made with what is held: every fluid held is one of its inputs. */
    public boolean fits(Collection<Fluid> held) {
        if (held.size() > inputs.size())
            return false;
        for (Fluid f : held)
            if (!takes(f))
                return false;
        return true;
    }

    /** Is every one of its inputs held? */
    public boolean complete(Collection<Fluid> held) {
        outer:
        for (Input in : inputs) {
            for (Fluid f : held)
                if (in.takes(f))
                    continue outer;
            return false;
        }
        return true;
    }

    /** Packed ice used per mB made: the tower's setting for the energy yield, the file's number otherwise. */
    public double icePerLiquid() {
        if (!ice)
            return 0;
        return energy ? 1.0 / FwConfig.get(FwConfig.TOWER_FROSIUM_PER_ICE) : icePerOutput;
    }

    /** mB of input {@code i} (not the first) used per mB made: the tower's setting for the energy yield, unless the
     * file says. */
    public double inputPerOutput(int i) {
        double v = inputs.get(i).perOutput();
        return energy && v <= 0 ? FwConfig.get(FwConfig.TOWER_WATER_PER_LIQUID) : v;
    }

    // ---- The file ----------------------------------------------------------------------------------------------

    /** Reads a recipe file; throws IllegalArgumentException with the reason if it is wrong. */
    public static Distillation fromJson(ResourceLocation id, JsonObject json) {
        List<Input> inputs = new ArrayList<>();
        var array = json.getAsJsonArray("inputs");
        if (array == null || array.isEmpty() || array.size() > MAX_INPUTS)
            throw new IllegalArgumentException("between 1 and " + MAX_INPUTS + " inputs are needed");
        double defaultShare = 1.0 / array.size();
        for (JsonElement e : array) {
            JsonObject in = e.getAsJsonObject();
            Fluid fluid = null;
            TagKey<Fluid> tag = null;
            if (in.has("fluid"))
                fluid = fluid(in.get("fluid").getAsString());
            else if (in.has("tag"))
                tag = TagKey.create(Registries.FLUID, Mc.parse(in.get("tag").getAsString()));
            else
                throw new IllegalArgumentException("an input needs a \"fluid\" or a \"tag\"");
            inputs.add(new Input(fluid, tag, in.has("share") ? in.get("share").getAsDouble() : defaultShare,
                    in.has("per_output") ? in.get("per_output").getAsDouble() : 0));
        }
        Fluid result = fluid(json.get("result").getAsString());
        boolean ice = json.has("ice") && json.get("ice").getAsBoolean();
        JsonElement yield = json.get("yield");
        boolean energy = yield != null && yield.isJsonPrimitive() && "energy".equals(yield.getAsString());
        double perInput = !energy && yield != null && yield.isJsonObject() && yield.getAsJsonObject().has("per_input")
                ? yield.getAsJsonObject().get("per_input").getAsDouble() : 0;
        if (!energy && perInput <= 0)
            throw new IllegalArgumentException("\"yield\" is \"energy\" or {\"per_input\": more than 0}");
        // the energy yield gives back what went in: only from cold gas, only into one of the mod's cold liquids
        if (energy && (inputs.get(0).fluid() == null || !inputs.get(0).fluid().isSame(com.frostworks.registry.FwFluids.gas())
                || !(result.isSame(com.frostworks.registry.FwFluids.frosium())
                || result.defaultFluidState().is(com.frostworks.registry.FwTags.COLD_LIQUID))))
            throw new IllegalArgumentException("the \"energy\" yield takes cold gas first and makes a cold liquid");
        double icePerOutput = json.has("ice_per_output") ? json.get("ice_per_output").getAsDouble() : 0;
        return new Distillation(id, inputs, ice, result, energy, perInput, icePerOutput);
    }

    private static Fluid fluid(String name) {
        Fluid f = BuiltInRegistries.FLUID.get(Mc.parse(name));
        if (f == null || f == Fluids.EMPTY)
            throw new IllegalArgumentException("unknown fluid " + name);
        return f;
    }

    /** Sent to the clients with the other recipes (JEI). */
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeVarInt(inputs.size());
        for (Input in : inputs) {
            buf.writeBoolean(in.fluid() != null);
            buf.writeResourceLocation(in.fluid() != null ? BuiltInRegistries.FLUID.getKey(in.fluid()) : in.tag().location());
            buf.writeDouble(in.share());
            buf.writeDouble(in.perOutput());
        }
        buf.writeBoolean(ice);
        buf.writeResourceLocation(BuiltInRegistries.FLUID.getKey(result));
        buf.writeBoolean(energy);
        buf.writeDouble(perInput);
        buf.writeDouble(icePerOutput);
    }

    public static Distillation fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Input> inputs = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            boolean isFluid = buf.readBoolean();
            ResourceLocation name = buf.readResourceLocation();
            double share = buf.readDouble(), perOutput = buf.readDouble();
            inputs.add(isFluid ? new Input(BuiltInRegistries.FLUID.get(name), null, share, perOutput)
                    : new Input(null, TagKey.create(Registries.FLUID, name), share, perOutput));
        }
        boolean ice = buf.readBoolean();
        Fluid result = BuiltInRegistries.FLUID.get(buf.readResourceLocation());
        boolean energy = buf.readBoolean();
        double perInput = buf.readDouble(), icePerOutput = buf.readDouble();
        return new Distillation(id, inputs, ice, result, energy, perInput, icePerOutput);
    }
}
