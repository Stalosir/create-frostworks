package com.frostworks.content.cold;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import org.jetbrains.annotations.Nullable;

import com.frostworks.Frostworks;
import com.frostworks.FwConfig;
import com.frostworks.room.Room;
import com.frostworks.room.RoomManager;
import com.frostworks.room.core.RoomStatus;
import com.google.gson.JsonObject;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

/**
 * The cold processing recipes at work. The files ({@link ColdProcess}) are read into an index (by process), built
 * again after each reload of the tags (a {@code /reload} too), on first use; a roll then looks through the files of its
 * process and, when one applies, reads the machine's room.
 * <p>
 * At the moment a machine rolls its results ({@link #roll}): the room the machine is in, closed (COLD or INSUFFICIENT,
 * never OPEN), at its real temperature, read then and only then; out of any closed room, only a step without a
 * temperature applies. A file applies when its recipe is the machine's (or it names none) and one of its ingredients
 * takes what the machine uses (or it has none). In {@code replace}, the first such file by id takes the place of the
 * recipe's results; in {@code bonus}, every file that applies adds its step's results (and leaves out the recipe's last
 * one when it says so). The machine then rolls the whole list as it always does, with its own random: nothing else
 * changes. At the basin, the fluids go the same way ({@link #fluids}).
 */
public final class ColdProcessing {
    private ColdProcessing() {}

    /** A process's files (replace and bonus, by id) and the ids of its recipes (for the files that name one). */
    private record ProcessIndex(List<ColdProcess> replace, List<ColdProcess> bonus, Map<Recipe<?>, ResourceLocation> ids) {}

    /** The steps that apply to one craft: the replace step (null: the recipe's results stay), the bonus steps. */
    private record Choice(@Nullable ColdProcess.Step replace, List<ColdProcess.Step> bonus) {}

    private static volatile Map<ResourceLocation, ProcessIndex> index = null;

    /** The tags were reloaded (server start, /reload): the index is built again on its next use. */
    public static void invalidate() {
        index = null;
    }

    /** A machine that works one item (crushing wheels, millstone, Greate's machines): see the other {@code roll}. */
    public static List<ItemStack> roll(Recipe<?> recipe, ItemStack input, List<ProcessingOutput> recipeResults,
                                       @Nullable Level level, BlockPos pos,
                                       Function<List<ProcessingOutput>, List<ItemStack>> roller,
                                       Supplier<List<ItemStack>> asIs) {
        if (input.isEmpty())
            return asIs.get();
        return roll(recipe, List.of(input), List.of(), recipeResults, level, pos, roller, asIs);
    }

    /**
     * A machine's roll. {@code items} and {@code fluids} are what the craft uses; {@code recipeResults} the results the
     * machine was going to roll; {@code roller} rolls a list the machine's way; {@code asIs} is the machine's own roll,
     * used when no cold file applies.
     */
    public static List<ItemStack> roll(Recipe<?> recipe, List<ItemStack> items, List<Fluid> fluids,
                                       List<ProcessingOutput> recipeResults, @Nullable Level level, BlockPos pos,
                                       Function<List<ProcessingOutput>, List<ItemStack>> roller,
                                       Supplier<List<ItemStack>> asIs) {
        Choice choice = choose(recipe, items, fluids, level, pos);
        if (choice == null)
            return asIs.get();
        List<ProcessingOutput> results;
        boolean replaced = choice.replace() != null;
        results = replaced ? choice.replace().results(recipeResults) : new ArrayList<>(recipeResults);
        boolean lastLeftOut = false;
        for (ColdProcess.Step step : choice.bonus()) {
            // the recipe's last result, left out once (only while the recipe's own list is kept)
            if (step.replacesLast() && !replaced && !lastLeftOut && !recipeResults.isEmpty()) {
                results.remove(recipeResults.size() - 1);
                lastLeftOut = true;
            }
            results.addAll(step.results(recipeResults));
        }
        return roller.apply(results);
    }

    /**
     * The basin's fluid results: {@code recipeFluids} as they are when no file applies; otherwise, the replace step's
     * fluids in their place (none if it gives none), and every bonus step's added, each rolled at its chance. {@code F}
     * is the loader's fluid stack ({@code fluidOf}, {@code amountOf} and {@code make} read and make one).
     */
    public static <F> List<F> fluids(Recipe<?> recipe, List<ItemStack> items, List<Fluid> fluids, List<F> recipeFluids,
                                     Function<F, Fluid> fluidOf, ToIntFunction<F> amountOf,
                                     BiFunction<Fluid, Integer, F> make, @Nullable Level level, BlockPos pos,
                                     RandomSource random) {
        Choice choice = choose(recipe, items, fluids, level, pos);
        if (choice == null)
            return recipeFluids;
        boolean any = choice.replace() != null;
        for (ColdProcess.Step step : choice.bonus())
            any |= step.hasFluids();
        if (!any)
            return recipeFluids;
        List<F> results = new ArrayList<>();
        if (choice.replace() != null)
            add(results, choice.replace(), recipeFluids, fluidOf, amountOf, make, random);
        else
            results.addAll(recipeFluids);
        for (ColdProcess.Step step : choice.bonus())
            add(results, step, recipeFluids, fluidOf, amountOf, make, random);
        return results;
    }

    private static <F> void add(List<F> results, ColdProcess.Step step, List<F> recipeFluids, Function<F, Fluid> fluidOf,
                                ToIntFunction<F> amountOf, BiFunction<Fluid, Integer, F> make, RandomSource random) {
        for (ColdProcess.FluidOut out : step.fluids())
            if (out.chance() >= 1 || random.nextFloat() < out.chance())
                results.add(make.apply(out.fluid(), out.amount()));
        if (recipeFluids.isEmpty() || amountOf.applyAsInt(recipeFluids.get(0)) <= 0)
            return;
        for (ColdProcess.FluidCopy copy : step.fluidCopies())
            if (copy.chance() >= 1 || random.nextFloat() < copy.chance())
                results.add(make.apply(fluidOf.apply(recipeFluids.get(0)), copy.amount()));
    }

    /** The steps that apply to this craft at the machine's place, or null when none does. */
    @Nullable
    private static Choice choose(Recipe<?> recipe, List<ItemStack> items, List<Fluid> fluids, @Nullable Level level,
                                 BlockPos pos) {
        if (!(level instanceof ServerLevel server) || !FwConfig.get(FwConfig.COLD_PROCESSING_ENABLED))
            return null;
        ProcessIndex files = index(server).get(ColdVersion.typeId(recipe));
        if (files == null)
            return null;
        ResourceLocation recipeId = files.ids().get(recipe);
        float temperature = Float.NaN;
        boolean read = false;
        ColdProcess.Step replace = null;
        for (ColdProcess cold : files.replace()) {
            if (!cold.goesWith(recipeId) || !cold.accepts(items, fluids))
                continue;
            if (!read) {
                temperature = closedRoomTemperature(server, pos); // NaN: only a step without a temperature applies
                read = true;
            }
            replace = cold.stepAt(temperature);
            if (replace != null)
                break;
        }
        List<ColdProcess.Step> bonus = new ArrayList<>();
        for (ColdProcess cold : files.bonus()) {
            if (!cold.goesWith(recipeId) || !cold.accepts(items, fluids))
                continue;
            if (!read) {
                temperature = closedRoomTemperature(server, pos);
                read = true;
            }
            ColdProcess.Step step = cold.stepAt(temperature);
            if (step != null)
                bonus.add(step);
        }
        return replace == null && bonus.isEmpty() ? null : new Choice(replace, bonus);
    }

    /** A machine of another mod that rolls through a shared helper (Greate): where it is and what it processes. */
    public record Context(Level level, BlockPos pos, ItemStack input) {}

    /** The machines at work on this thread, the innermost first. */
    private static final ThreadLocal<java.util.ArrayDeque<Context>> CONTEXT = ThreadLocal.withInitial(java.util.ArrayDeque::new);

    /** Runs {@code work} (the machine's call to its roll helper) with this machine as the context. */
    public static <T> T within(Level level, BlockPos pos, ItemStack input, Supplier<T> work) {
        enter(level, pos, input);
        try {
            return work.get();
        } finally {
            leave();
        }
    }

    /**
     * This machine is the context until {@link #leave}: for a hook that cannot wrap the call, only its start and its
     * returns (Greate's fans). Every {@code enter} has its {@code leave}.
     */
    public static void enter(Level level, BlockPos pos, ItemStack input) {
        CONTEXT.get().push(new Context(level, pos, input.copy()));
    }

    public static void leave() {
        CONTEXT.get().poll();
    }

    @Nullable
    public static Context context() {
        return CONTEXT.get().peek();
    }

    /** The temperature of the closed room at this position, NaN if none (an OPEN room's cells reach far outside). */
    public static float closedRoomTemperature(ServerLevel level, BlockPos pos) {
        Room room = RoomManager.get(level).roomAt(pos);
        if (room == null || room.status() != RoomStatus.COLD && room.status() != RoomStatus.INSUFFICIENT)
            return Float.NaN;
        return room.temperature();
    }

    private static Map<ResourceLocation, ProcessIndex> index(ServerLevel level) {
        Map<ResourceLocation, ProcessIndex> built = index;
        if (built == null) {
            built = build(level);
            index = built;
        }
        return built;
    }

    /** Reads every file, in the order of their ids; a broken file is left out, with a message that says why. */
    private static Map<ResourceLocation, ProcessIndex> build(ServerLevel level) {
        var recipes = level.getServer().getRecipeManager();
        Map<ResourceLocation, ProcessIndex> built = new HashMap<>();
        Map<ResourceLocation, Map<ResourceLocation, Recipe<?>>> byProcess = new HashMap<>();
        Map<String, ColdProcess> replaced = new HashMap<>(); // what a replace file takes: "recipe id" or "item"
        int count = 0;
        for (Map.Entry<ResourceLocation, JsonObject> file : new TreeMap<>(ColdVersion.files(recipes)).entrySet()) {
            ColdProcess cold;
            try {
                cold = ColdProcess.read(file.getKey(), file.getValue(), level.registryAccess());
            } catch (RuntimeException e) {
                Frostworks.LOGGER.error("Cold processing recipe {} left out: {}", file.getKey(), e.getMessage());
                continue;
            }
            Map<ResourceLocation, Recipe<?>> ofProcess = byProcess.computeIfAbsent(cold.process(),
                    p -> ColdVersion.recipesOf(recipes, p));
            if (cold.recipe() != null && !ofProcess.containsKey(cold.recipe())) {
                Frostworks.LOGGER.error("Cold processing recipe {} left out: there is no {} recipe {}", cold.id(),
                        cold.process(), cold.recipe());
                continue;
            }
            ProcessIndex at = built.computeIfAbsent(cold.process(), p -> {
                Map<Recipe<?>, ResourceLocation> ids = new IdentityHashMap<>();
                ofProcess.forEach((id, recipe) -> ids.put(recipe, id));
                return new ProcessIndex(new ArrayList<>(), new ArrayList<>(), ids);
            });
            if (cold.mode() == ColdProcess.Mode.BONUS) {
                at.bonus().add(cold);
                count++;
                continue;
            }
            if (cold.recipe() != null) {
                conflict(replaced, cold.process() + " recipe " + cold.recipe(), cold);
            } else {
                boolean anyItem = false, anyNormal = false;
                for (ColdProcess.Entry entry : cold.ingredients()) {
                    if (entry.item() == null)
                        continue;
                    anyItem = true;
                    for (ItemStack stack : entry.item().getItems()) {
                        if (!ColdVersion.hasNormalRecipe(recipes, cold.process(), stack))
                            continue;
                        anyNormal = true;
                        conflict(replaced, cold.process() + " " + stack.getItem(), cold);
                    }
                }
                if (anyItem && !anyNormal) {
                    Frostworks.LOGGER.error("Cold processing recipe {} left out: replace needs a normal {} recipe for "
                            + "its ingredients, and there is none", cold.id(), cold.process());
                    continue;
                }
            }
            at.replace().add(cold);
            count++;
        }
        Frostworks.LOGGER.info("Cold processing: {} recipe(s) read", count);
        return built;
    }

    /** Two replace files for the same thing: the log names both; the first by id is used. */
    private static void conflict(Map<String, ColdProcess> replaced, String what, ColdProcess cold) {
        ColdProcess before = replaced.putIfAbsent(what, cold);
        if (before != null && before != cold)
            Frostworks.LOGGER.error("Cold processing recipes {} and {} both replace {}: {} is used (first by id)",
                    before.id(), cold.id(), what, before.id());
    }
}
