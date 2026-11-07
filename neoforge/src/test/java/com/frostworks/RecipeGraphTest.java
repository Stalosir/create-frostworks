package com.frostworks;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The graph of how the mod's things are made, read from {@code data/frostworks/recipe/} (crafting,
 * item application, infusing) plus the ways that are not recipes:
 * <ul>
 * <li>cold gas comes from the compressor, Frosium from the tower (column bases + frost tanks);</li>
 * <li>the passages come from a right click of a cold wall on a Create block;</li>
 * <li>the column comes from frost tanks.</li>
 * </ul>
 * Everything of the mod must be reachable from things that are not the mod's, without a cycle, and the two first
 * machines (compressor, infuser) need nothing of the mod.
 */
class RecipeGraphTest {

    /** A way to make something: its inputs (items, tags, fluids) and what it gives. */
    record Way(String name, Set<String> inputs, String output) {}

    static final List<Way> ways = new ArrayList<>();
    static final Set<String> modItems = new LinkedHashSet<>();

    static boolean ours(String id) {
        return id.startsWith("frostworks:");
    }

    @BeforeAll
    static void read() throws IOException {
        Path resources = Path.of(System.getProperty("frostworks.resourcesDir"));
        try (Stream<Path> files = Files.list(resources.resolve("assets/frostworks/models/item"))) {
            files.forEach(p -> modItems.add("frostworks:" + p.getFileName().toString().replace(".json", "")));
        }
        Path dir = resources.resolve("data/frostworks/recipe");
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                JsonObject r;
                try (Reader reader = Files.newBufferedReader(p)) {
                    r = JsonParser.parseReader(reader).getAsJsonObject();
                }
                String name = dir.relativize(p).toString().replace('\\', '/');
                Set<String> inputs = new LinkedHashSet<>();
                if (r.has("key"))
                    r.getAsJsonObject("key").entrySet().forEach(e -> inputs.add(id(e.getValue())));
                if (r.has("ingredients"))
                    r.getAsJsonArray("ingredients").forEach(e -> inputs.add(id(e)));
                // Freezing is done by the infuser, with the cold gas of its own tank
                if (r.get("type").getAsString().equals("frostworks:freezing")) {
                    inputs.add("frostworks:cryo_infuser");
                    inputs.add("frostworks:cold_gas");
                }
                // A distilling recipe is made in the tower (its blocks, its fluids, its ice)
                if (r.get("type").getAsString().equals("frostworks:distilling")) {
                    inputs.addAll(Set.of("frostworks:column_base", "frostworks:reservoir"));
                    r.getAsJsonArray("inputs").forEach(e -> inputs.add(id(e)));
                    if (r.has("ice") && r.get("ice").getAsBoolean())
                        inputs.add("minecraft:packed_ice");
                    ways.add(new Way(name, inputs, r.get("result").getAsString()));
                    continue;
                }
                String output = r.has("result") ? r.getAsJsonObject("result").get("id").getAsString()
                        : r.getAsJsonArray("results").get(0).getAsJsonObject().get("id").getAsString();
                ways.add(new Way(name, inputs, output));
            }
        }
        // what is not a recipe
        ways.add(new Way("the compressor makes it", Set.of("frostworks:compressor"), "frostworks:cold_gas"));
        ways.add(new Way("frost tanks on the tower", Set.of("frostworks:reservoir"), "frostworks:column_segment"));
        ways.add(new Way("an empty bucket filled with Frosium (frost tank, item drain, spout)",
                Set.of("frostworks:frosium"), "frostworks:frosium_bucket"));
        for (String passage : List.of("shaft_passage", "pipe_passage", "belt_passage"))
            ways.add(new Way("right click of a cold wall", Set.of("frostworks:cold_wall"), "frostworks:" + passage));
    }

    /** The item, tag or fluid of an ingredient (a tag is written "#tag"). */
    static String id(JsonElement e) {
        JsonObject o = e.getAsJsonObject();
        if (o.has("item"))
            return o.get("item").getAsString();
        if (o.has("tag"))
            return "#" + o.get("tag").getAsString();
        return o.get("fluid").getAsString();
    }

    /** Everything that can be made, from what is not the mod's. */
    static Set<String> reachable() {
        Set<String> made = new HashSet<>();
        boolean more = true;
        while (more) {
            more = false;
            for (Way w : ways)
                if (!made.contains(w.output()) && w.inputs().stream().allMatch(i -> !ours(i) || made.contains(i))) {
                    made.add(w.output());
                    more = true;
                }
        }
        return made;
    }

    @Test
    void everythingOfTheModCanBeMade() {
        Set<String> made = reachable();
        List<String> missing = new ArrayList<>();
        for (String item : modItems)
            if (!made.contains(item)) {
                StringBuilder why = new StringBuilder(item + " cannot be made:");
                for (Way w : ways)
                    if (w.output().equals(item))
                        why.append("\n  ").append(w.name()).append(" needs ")
                                .append(w.inputs().stream().filter(i -> ours(i) && !made.contains(i)).toList());
                if (ways.stream().noneMatch(w -> w.output().equals(item)))
                    why.append(" no way at all");
                missing.add(why.toString());
            }
        assertTrue(missing.isEmpty(), String.join("\n", missing));
        System.out.println("RECIPE GRAPH " + modItems.size() + " items of the mod, all reachable");
    }

    @Test
    void noCycle() {
        Map<String, Set<String>> next = new HashMap<>();
        for (Way w : ways)
            for (String i : w.inputs())
                if (ours(i))
                    next.computeIfAbsent(i, k -> new HashSet<>()).add(w.output());
        Set<String> done = new HashSet<>();
        for (String start : next.keySet()) {
            List<String> path = new ArrayList<>();
            String cycle = cycleFrom(start, next, path, new HashSet<>(), done);
            assertTrue(cycle == null, "cycle: " + cycle);
        }
    }

    static String cycleFrom(String node, Map<String, Set<String>> next, List<String> path, Set<String> onPath,
                            Set<String> done) {
        if (onPath.contains(node))
            return String.join(" -> ", path.subList(path.indexOf(node), path.size())) + " -> " + node;
        if (done.contains(node))
            return null;
        path.add(node);
        onPath.add(node);
        for (String n : next.getOrDefault(node, Set.of())) {
            String cycle = cycleFrom(n, next, path, onPath, done);
            if (cycle != null)
                return cycle;
        }
        path.remove(path.size() - 1);
        onPath.remove(node);
        done.add(node);
        return null;
    }

    /** The ice the tower uses is made from water and cold gas only, before any Frosium. */
    @Test
    void theIceComesFromWaterAndGasOnly() {
        List<Way> freezing = ways.stream().filter(w -> w.name().startsWith("freezing/")).toList();
        assertTrue(freezing.stream().anyMatch(w -> w.output().equals("minecraft:ice")), "no freezing recipe for ice");
        for (Way w : freezing)
            assertTrue(w.inputs().equals(Set.of("minecraft:water", "frostworks:cold_gas", "frostworks:cryo_infuser")),
                    w.name() + " needs " + w.inputs());
        // what it needs comes before the tower: the gas from the compressor, the infuser from nothing of the mod
        Set<String> made = reachable();
        assertTrue(made.contains("frostworks:cold_gas") && made.contains("frostworks:cryo_infuser"));
        assertTrue(ways.stream().filter(w -> w.output().equals("frostworks:cold_gas"))
                .noneMatch(w -> w.inputs().contains("frostworks:frosium")), "the gas needs Frosium");
    }

    @Test
    void theFirstMachinesNeedNothingOfTheMod() {
        for (String machine : List.of("frostworks:compressor", "frostworks:cryo_infuser")) {
            List<Way> recipes = ways.stream().filter(w -> w.output().equals(machine)).toList();
            assertTrue(!recipes.isEmpty(), "no recipe for " + machine);
            for (Way w : recipes)
                assertTrue(w.inputs().stream().noneMatch(RecipeGraphTest::ours),
                        machine + " (" + w.name() + ") needs something of the mod: " + w.inputs());
        }
    }
}
