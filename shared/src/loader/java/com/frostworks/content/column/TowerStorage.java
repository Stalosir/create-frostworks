package com.frostworks.content.column;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.frostworks.FwConfig;
import com.frostworks.platform.Mc;
import com.frostworks.registry.FwFluids;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * The fluids of a tower, held by its controller: up to {@link Distillation#MAX_INPUTS} inputs in the order they came,
 * one product, and the recipe it makes. Amounts are whole mB; the fraction of a mB taken but not used yet (or owed)
 * waits in a credit.
 * <p>
 * Room: the product takes its place first; what it leaves is shared by the inputs, as the recipe says. A fluid goes in
 * only if, with what is held, it still makes a recipe.
 */
final class TowerStorage {

    /** One input: its mB and its credit. An emptied input stays in the list while it has a credit. */
    static final class Held {
        final Fluid fluid;
        int amount;
        double credit;

        Held(Fluid fluid) {
            this.fluid = fluid;
        }
    }

    private final TowerBlockEntity tower;

    final List<Held> inputs = new ArrayList<>();
    @Nullable
    Fluid outputFluid;
    int output;
    double outputRemainder;
    /** The recipe it makes, or made last; kept while it still can. */
    @Nullable
    ResourceLocation recipeId;

    TowerStorage(TowerBlockEntity tower) {
        this.tower = tower;
    }

    /** The distilling recipes known here (none without a level). */
    List<Distillation> recipes() {
        return tower.getLevel() == null ? List.of() : DistillingRecipe.all(tower.getLevel());
    }

    // ---- What is held ----

    @Nullable
    Held entry(Fluid fluid) {
        for (Held h : inputs)
            if (h.fluid.isSame(fluid))
                return h;
        return null;
    }

    int amountOf(Fluid fluid) {
        Held h = entry(fluid);
        return h == null ? 0 : h.amount;
    }

    /** The input held that this recipe input takes (null if none). */
    @Nullable
    Held taking(Distillation.Input in) {
        for (Held h : inputs)
            if (h.amount > 0 && in.takes(h.fluid))
                return h;
        return null;
    }

    /** The fluids held (an empty input does not count). */
    List<Fluid> held() {
        List<Fluid> held = new ArrayList<>();
        for (Held h : inputs)
            if (h.amount > 0)
                held.add(h.fluid);
        return held;
    }

    // ---- Recipes ----

    @Nullable
    Distillation current(List<Distillation> all) {
        for (Distillation r : all)
            if (r.id.equals(recipeId))
                return r;
        return null;
    }

    /**
     * The recipe to make: the current one while every one of its inputs is held, otherwise the one with every input
     * held that has the most inputs. Null if none.
     */
    @Nullable
    Distillation choose(List<Distillation> all) {
        List<Fluid> held = held();
        Distillation cur = current(all);
        if (cur != null && cur.fits(held) && cur.complete(held))
            return cur;
        Distillation best = null;
        for (Distillation r : all)
            if (r.fits(held) && r.complete(held) && (best == null || r.inputs.size() > best.inputs.size()))
                best = r;
        return best;
    }

    /** The recipe the tower is heading to with what it holds (the current one first), or null. */
    @Nullable
    Distillation heading(List<Distillation> all) {
        List<Fluid> held = held();
        Distillation cur = current(all);
        if (cur != null && cur.fits(held))
            return cur;
        for (Distillation r : all)
            if (r.fits(held))
                return r;
        return null;
    }

    /** Ice goes in while a recipe that still fits what is held uses it (any ice without a recipe known). */
    boolean takesIce() {
        List<Distillation> all = recipes();
        if (all.isEmpty())
            return true;
        List<Fluid> held = held();
        for (Distillation r : all)
            if (r.ice && r.fits(held))
                return true;
        return false;
    }

    // ---- Room ----

    /**
     * The recipe whose shares give this fluid its room: the current one if it still fits with the fluid, otherwise the
     * first that does. Null: with what is held, this fluid makes no recipe (refused).
     */
    @Nullable
    private Distillation shareRecipe(Fluid fluid, List<Distillation> all) {
        List<Fluid> set = held();
        Held h = entry(fluid);
        if (h == null || h.amount <= 0)
            set.add(fluid);
        Distillation cur = current(all);
        if (cur != null && cur.fits(set))
            return cur;
        for (Distillation r : all)
            if (r.fits(set))
                return r;
        return null;
    }

    /** The room of this fluid in mB, or -1 if it is refused. */
    int roomFor(Fluid fluid, List<Distillation> all) {
        Distillation r = shareRecipe(fluid, all);
        return r == null ? -1 : rooms(r)[r.index(fluid)];
    }

    /**
     * Each input's room: its share of what the product leaves. The energy recipe keeps the settings:
     * {@code towerGasShare} for the cold gas, the rest shared by the others (the last one takes what rounding leaves).
     */
    private int[] rooms(Distillation r) {
        int free = Math.max(0, tower.capacity() - output), n = r.inputs.size(), sum = 0;
        int[] rooms = new int[n];
        double gasShare = FwConfig.get(FwConfig.TOWER_GAS_SHARE);
        for (int i = 0; i < n; i++) {
            double share = !r.energy ? r.inputs.get(i).share() : i == 0 ? gasShare : (1 - gasShare) / (n - 1);
            rooms[i] = r.energy && i == n - 1 ? free - sum : (int) (free * share);
            sum += rooms[i];
        }
        return rooms;
    }

    /** The surplus is lost, as when a layer of a Create tank is taken away. */
    void clampToCapacity() {
        output = Math.min(output, tower.capacity());
        List<Distillation> all = recipes();
        if (all.isEmpty())
            return; // no recipe known (a Ponder scene): the content stays
        for (Held h : inputs) {
            int room = roomFor(h.fluid, all);
            if (room >= 0)
                h.amount = Math.min(h.amount, room);
        }
    }

    // ---- Merging ----

    /** Takes the fluids of another tower's storage; a fluid that makes no recipe with this content is lost. */
    void absorb(TowerStorage other) {
        List<Distillation> all = recipes();
        for (Held o : other.inputs) {
            Held h = entry(o.fluid);
            if (h == null && (inputs.size() >= Distillation.MAX_INPUTS || !all.isEmpty() && roomFor(o.fluid, all) < 0))
                continue;
            if (h == null)
                inputs.add(h = new Held(o.fluid));
            h.amount += o.amount;
            h.credit += o.credit;
        }
        if (other.outputFluid != null && (outputFluid == null || output == 0 && outputRemainder == 0))
            outputFluid = other.outputFluid;
        if (other.outputFluid != null && other.outputFluid.isSame(outputFluid)) {
            output += other.output;
            outputRemainder += other.outputRemainder;
        }
        if (recipeId == null)
            recipeId = other.recipeId;
        other.inputs.clear();
        other.outputFluid = null;
        other.output = 0;
        other.outputRemainder = 0;
    }

    // ---- NBT ----

    void write(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Held h : inputs) {
            CompoundTag e = new CompoundTag();
            e.putString("Fluid", key(h.fluid));
            e.putInt("Amount", h.amount);
            e.putDouble("Credit", h.credit);
            list.add(e);
        }
        tag.put("Inputs", list);
        if (outputFluid != null) {
            tag.putString("OutputFluid", key(outputFluid));
            tag.putInt("Output", output);
            tag.putDouble("OutputRemainder", outputRemainder);
        }
        if (recipeId != null)
            tag.putString("Recipe", recipeId.toString());
    }

    void read(CompoundTag tag) {
        inputs.clear();
        for (Tag t : tag.getList("Inputs", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            Fluid fluid = fluid(e.getString("Fluid"));
            if (fluid == null)
                continue; // a fluid gone with its mod
            Held h = new Held(fluid);
            h.amount = e.getInt("Amount");
            h.credit = e.getDouble("Credit");
            inputs.add(h);
        }
        outputFluid = tag.contains("OutputFluid") ? fluid(tag.getString("OutputFluid")) : null;
        output = outputFluid == null ? 0 : tag.getInt("Output");
        outputRemainder = outputFluid == null ? 0 : tag.getDouble("OutputRemainder");
        recipeId = tag.contains("Recipe") ? Mc.parse(tag.getString("Recipe")) : null;
        // older saves (and the Ponder scenes): cold gas, water and the product had keys of their own
        readOldInput(FwFluids.gas(), tag, "Gas", "GasCredit");
        readOldInput(Fluids.WATER, tag, "Water", "WaterCredit");
        if (tag.contains("Air")) {
            outputFluid = FwFluids.frosium();
            output = tag.getInt("Air");
            outputRemainder = tag.getDouble("AirRemainder");
            if (recipeId == null)
                recipeId = Distillation.FROSIUM;
        }
    }

    private void readOldInput(Fluid fluid, CompoundTag tag, String amountKey, String creditKey) {
        if (!tag.contains(amountKey))
            return;
        Held h = entry(fluid);
        if (h == null)
            inputs.add(h = new Held(fluid));
        h.amount = tag.getInt(amountKey);
        h.credit = tag.getDouble(creditKey);
    }

    private static String key(Fluid fluid) {
        return BuiltInRegistries.FLUID.getKey(fluid).toString();
    }

    @Nullable
    private static Fluid fluid(String name) {
        Fluid f = BuiltInRegistries.FLUID.get(Mc.parse(name));
        return f == null || f == Fluids.EMPTY ? null : f;
    }
}
