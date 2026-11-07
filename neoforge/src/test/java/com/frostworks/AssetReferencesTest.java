package com.frostworks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * No missing model or texture: every blockstate, model and texture referenced by our JSON (and the fluid textures of
 * the code) must exist in our resources, in Create (unpacked {@code create/} folder or Create jar) or in vanilla.
 * No new texture is allowed in our own resources.
 */
class AssetReferencesTest {
    static final List<String> BLOCKS = List.of("compressor", "vent", "cold_wall", "insulated_door", "shaft_passage",
            "column_base", "column_segment", "reservoir", "expander", "cryo_infuser", "pipe_passage");
    /** Literal texture paths used by the code (FwFluids). */
    static final List<String> CODE_TEXTURES = List.of("minecraft:block/water_still", "minecraft:block/water_flow");
    /** Connected-texture sprites used by the code (ColdWallBlock): base texture and connected sheet. */
    static final List<String> CT_TEXTURES = List.of("frostworks:block/cold_wall", "frostworks:block/cold_wall_connected",
            // the frost tank (FrostTankModel)
            "frostworks:block/frost_tank/frost_tank", "frostworks:block/frost_tank/frost_tank_connected",
            "frostworks:block/frost_tank/frost_tank_top", "frostworks:block/frost_tank/frost_tank_top_connected",
            "frostworks:block/frost_tank/frost_tank_inner", "frostworks:block/frost_tank/frost_tank_inner_connected",
            // The colour layers of the cold wall (ColdWallBlock.TINT_SHIFTS), our sheets and their bases
            "frostworks:block/cold_wall_tint/cold_wall_white", "frostworks:block/cold_wall_tint/cold_wall_black",
            "frostworks:block/cold_wall_tint/cold_wall_white_single", "frostworks:block/cold_wall_tint/cold_wall_black_single");
    /** Partial models drawn by code (the four flaps of the strip curtain door and the vent's propeller). */
    static final List<String> CODE_MODELS = List.of("frostworks:block/door_flap", "frostworks:block/vent/propeller",
            "frostworks:block/compressor/cog");
    /**
     * Art made for the mod (the only images allowed in our resources). copper_casing.png is our working
     * copy of a Create template: allowed to stay, used nowhere.
     */
    static final List<String> AUTHOR_ART = List.of("cold_wall.png", "cold_wall_connected.png", "frost_door.png",
            // The mod's icon and title (branding/, shrunk by Claude: no new drawing)
            "logo.png", "title.png",
            // The column's sprite in the JEI page of the tower (gui/)
            "colum_spitre.png",
            "door_flap.png", "copper_casing.png",
            // The vent (vent/) and the shaft passage
            "vent.png", "fan_blades.png", "axis.png", "axis_top.png", "shaft_passage.png",
            // The compressor (compressor/), body and cogwheel
            "compressor.png",
            // the column base (column_base/): one image per shape
            "single.png", "pair.png", "corner.png", "elbow.png",
            // the frost tank and column segment work in progress (column_segment/, frost_tank/)
            "frost_tank.png", "frost_tank_connected.png", "frost_tank_inner.png", "frost_tank_inner_connected.png",
            "frost_tank_top.png", "frost_tank_top_connected.png", "frost_tank_window.png",
            // Frosium placeholder (fluid/): vanilla lava, copied at our request for recolouring
            "frosium_still.png", "frosium_flow.png",
            "frost_tank_window_single.png",
            // Copied at our request for retouching: the infused items (copies of Create's iron
            // sheet and vanilla dried kelp) and the expander (copies of Create's fluid valve and shaft textures)
            "cold_iron_sheet.png", "cold_dried_kelp.png", "valve_body.png", "valve_handle.png", "underside.png",
            "shaft.png", "shaft_end.png",
            // copies of Create's press textures (cryo_infuser/) and encased pipe opening (pipe_passage/), at our
            // request: full models to open in Blockbench
            "side.png", "gearbox.png", "gearbox_top.png", "bottom.png", "top.png", "head.png", "pole.png", "opening.png",
            // our gas inlet of the cryogenic infuser
            "entry.png",
            // Placeholder asked by the brief: a copy of Create's andesite belt casing (belt/), for the
            // belt passage
            "belt_passage.png",
            // the 20 chilled ore blocks (block/ore/), repainted: a translucent frost layer over their base ore
            "coal_ore_frigorifie.png", "deepslate_coal_ore_frigorifie.png", "copper_ore_frigorifie.png",
            "deepslate_copper_ore_frigorifie.png", "iron_ore_frigorifie.png", "deepslate_iron_ore_frigorifie.png",
            "gold_ore_frigorifie.png", "deepslate_gold_ore_frigorifie.png", "redstone_ore_frigorifie.png",
            "deepslate_redstone_ore_frigorifie.png", "lapis_ore_frigorifie.png", "deepslate_lapis_ore_frigorifie.png",
            "diamond_ore_frigorifie.png", "deepslate_diamond_ore_frigorifie.png", "emerald_ore_frigorifie.png",
            "deepslate_emerald_ore_frigorifie.png", "nether_gold_ore_frigorifie.png", "nether_quartz_ore_frigorifie.png",
            "zinc_ore_frigorifie.png", "deepslate_zinc_ore_frigorifie.png",
            // the 4 chilled raw ores (item/ore/), repainted: a translucent frost layer over their base raw ore
            "raw_iron_frigorifie.png", "raw_copper_frigorifie.png", "raw_gold_frigorifie.png", "raw_zinc_frigorifie.png",
            // The colour layers of the cold wall (block/cold_wall_tint/), translucent sheets on the connected
            // texture's 8 x 8 layout; gray is the wall's own colour (Claude only set the colour hidden under their
            // clear pixels, against the mipmaps' dark edges)
            "cold_wall_white.png", "cold_wall_orange.png", "cold_wall_magenta.png", "cold_wall_light_blue.png",
            "cold_wall_yellow.png", "cold_wall_lime.png", "cold_wall_pink.png", "cold_wall_light_gray.png",
            "cold_wall_cyan.png", "cold_wall_purple.png", "cold_wall_blue.png", "cold_wall_brown.png",
            "cold_wall_green.png", "cold_wall_red.png", "cold_wall_black.png");
    /** Sprites registered under another name by assets/minecraft/atlases/blocks.json (sprite -> resource). */
    static final java.util.Map<String, String> ALIASES = new java.util.HashMap<>();

    static Path resources;
    static Path createDir;
    static ZipFile createJar;
    static ZipFile vanillaJar;
    final List<String> problems = new ArrayList<>();
    final Set<String> checkedModels = new HashSet<>();

    @BeforeAll
    static void open() throws IOException {
        resources = Path.of(System.getProperty("frostworks.resourcesDir"));
        Path dir = Path.of(System.getProperty("frostworks.createDir"));
        try (Stream<Path> s = Files.exists(dir) ? Files.list(dir) : Stream.empty()) {
            createDir = s.map(p -> p.resolve("assets")).filter(Files::isDirectory)
                    .filter(p -> Files.isDirectory(p.resolve("create/blockstates"))).findFirst().orElse(null);
        }
        createJar = new ZipFile(System.getProperty("frostworks.createJar"));
        vanillaJar = new ZipFile(System.getProperty("frostworks.vanillaJar"));
        Path atlas = resources.resolve("assets/minecraft/atlases/blocks.json");
        if (Files.exists(atlas))
            try (Reader r = Files.newBufferedReader(atlas)) {
                for (JsonElement e : JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("sources")) {
                    JsonObject source = e.getAsJsonObject();
                    if ("single".equals(source.get("type").getAsString()) && source.has("sprite"))
                        ALIASES.put(source.get("sprite").getAsString(), source.get("resource").getAsString());
                }
            }
    }

    @AfterAll
    static void close() throws IOException {
        createJar.close();
        vanillaJar.close();
    }

    /** Does {@code assets/<path>} exist in our resources, Create or vanilla? */
    static boolean assetExists(String path) {
        if (Files.exists(resources.resolve("assets").resolve(path)))
            return true;
        if (path.startsWith("create/")) {
            boolean inJar = createJar.getEntry("assets/" + path) != null;
            if (createDir != null)
                assertEquals(inJar, Files.exists(createDir.resolve(path)), "create/ folder and Create jar disagree: " + path);
            return inJar;
        }
        return vanillaJar.getEntry("assets/" + path) != null;
    }

    static JsonObject readAsset(String path) throws IOException {
        Path own = resources.resolve("assets").resolve(path);
        if (Files.exists(own))
            try (Reader r = Files.newBufferedReader(own)) {
                return JsonParser.parseReader(r).getAsJsonObject();
            }
        ZipFile zip = path.startsWith("create/") ? createJar : vanillaJar;
        var entry = zip.getEntry("assets/" + path);
        try (var in = zip.getInputStream(entry)) {
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    static String[] split(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? new String[]{"minecraft", id} : new String[]{id.substring(0, colon), id.substring(colon + 1)};
    }

    void checkModel(String id, String from) throws IOException {
        // neoforge:item/bucket: NeoForge's own, drawn from the fluid (the Frosium bucket)
        if (id.startsWith("builtin/") || id.equals("neoforge:item/bucket") || !checkedModels.add(id))
            return;
        String[] ns = split(id);
        String path = ns[0] + "/models/" + ns[1] + ".json";
        if (!assetExists(path)) {
            problems.add("missing model " + id + " (from " + from + ")");
            return;
        }
        JsonObject model = readAsset(path);
        if (model.has("parent"))
            checkModel(model.get("parent").getAsString(), id);
        if (model.has("textures"))
            for (Map.Entry<String, JsonElement> e : model.getAsJsonObject("textures").entrySet())
                checkTexture(e.getValue().getAsString(), id);
    }

    void checkTexture(String value, String from) {
        if (value.startsWith("#"))
            return;
        if (ALIASES.containsKey(value)) { // alias: the file it points to must exist
            checkTexture(ALIASES.get(value), from + " via atlas alias " + value);
            return;
        }
        String[] ns = split(value);
        if (!assetExists(ns[0] + "/textures/" + ns[1] + ".png"))
            problems.add("missing texture " + value + " (from " + from + ")");
    }

    void collectModels(JsonElement element, Set<String> out) {
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : element.getAsJsonObject().entrySet()) {
                if (e.getKey().equals("model") && e.getValue().isJsonPrimitive())
                    out.add(e.getValue().getAsString());
                else
                    collectModels(e.getValue(), out);
            }
        } else if (element.isJsonArray())
            element.getAsJsonArray().forEach(child -> collectModels(child, out));
    }

    @Test
    void everyReferenceExists() throws IOException {
        for (String block : BLOCKS) {
            String blockstate = "frostworks/blockstates/" + block + ".json";
            if (!assetExists(blockstate)) {
                problems.add("missing blockstate " + block);
                continue;
            }
            Set<String> models = new TreeSet<>();
            collectModels(readAsset(blockstate), models);
            assertTrue(!models.isEmpty(), "blockstate without model: " + block);
            for (String model : models)
                checkModel(model, blockstate);
            checkModel("frostworks:item/" + block, "item " + block);
        }
        // Every model file we ship is checked, even if no blockstate uses it
        try (Stream<Path> files = Files.walk(resources.resolve("assets/frostworks/models"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                String rel = resources.resolve("assets/frostworks/models").relativize(p).toString().replace('\\', '/');
                checkModel("frostworks:" + rel.substring(0, rel.length() - 5), "file " + rel);
            }
        }
        for (String texture : CODE_TEXTURES)
            checkTexture(texture, "code");
        for (String texture : CT_TEXTURES)
            checkTexture(texture, "connected texture of a cold wall");
        for (String model : CODE_MODELS)
            checkModel(model, "partial model drawn by code");
        for (java.util.Map.Entry<String, String> alias : ALIASES.entrySet())
            checkTexture(alias.getValue(), "atlas alias " + alias.getKey());
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        System.out.println("ASSETS checked " + checkedModels.size() + " models, no missing reference");
    }

    /** Our fluids (registered in FwFluids): nothing else of ours may be named as a fluid. */
    static final List<String> OUR_FLUIDS = List.of("frostworks:cold_gas", "frostworks:frosium");

    /** Does this item exist? Ours, Create's and vanilla's all have an item model under their namespace. */
    static boolean itemExists(String id) {
        String[] ns = split(id);
        return assetExists(ns[0] + "/models/item/" + ns[1] + ".json");
    }

    /** Collects every "item", result "id" and "fluid" of a recipe. */
    void collectRecipeIds(JsonElement element, String file, List<String[]> out) {
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : element.getAsJsonObject().entrySet()) {
                String key = e.getKey();
                if ((key.equals("item") || key.equals("id") || key.equals("fluid")) && e.getValue().isJsonPrimitive())
                    out.add(new String[]{key, e.getValue().getAsString(), file});
                else
                    collectRecipeIds(e.getValue(), file, out);
            }
        } else if (element.isJsonArray())
            element.getAsJsonArray().forEach(child -> collectRecipeIds(child, file, out));
    }

    /**
     * Every item and fluid our recipes name exists (ours, Create's or vanilla's); every recipe type is
     * a known one.
     */
    @Test
    void everyRecipeReferenceExists() throws IOException {
        Path dir = resources.resolve("data/frostworks/recipe");
        List<String[]> ids = new ArrayList<>();
        int recipes = 0;
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                recipes++;
                JsonObject recipe;
                try (Reader r = Files.newBufferedReader(p)) {
                    recipe = JsonParser.parseReader(r).getAsJsonObject();
                }
                String file = dir.relativize(p).toString().replace('\\', '/');
                String type = recipe.get("type").getAsString();
                if (!List.of("minecraft:crafting_shaped", "minecraft:crafting_shapeless", "create:item_application",
                        "frostworks:infusing", "frostworks:freezing",
                        // The distillation tower's crafts
                        "frostworks:distilling",
                        // The chilled ores in the furnaces and at Create's crushing wheels
                        "minecraft:smelting", "minecraft:blasting", "create:crushing").contains(type))
                    problems.add("unknown recipe type " + type + " in " + file);
                collectRecipeIds(recipe, file, ids);
            }
        }
        for (String[] id : ids) {
            boolean ok = id[0].equals("fluid") ? OUR_FLUIDS.contains(id[1]) || !id[1].startsWith("frostworks:")
                    && assetExists(split(id[1])[0] + "/lang/en_us.json") : itemExists(id[1]);
            if (!ok)
                problems.add("missing " + id[0] + " " + id[1] + " in " + id[2]);
        }
        assertTrue(recipes >= 9, "recipes found: " + recipes);
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        System.out.println("RECIPES checked " + recipes + " recipes, " + ids.size() + " references, none missing");
    }

    @Test
    void noNewArtInResources() throws IOException {
        // Only our art may be in our resources: Claude creates no image, sound or Blockbench file
        try (Stream<Path> files = Files.walk(resources)) {
            List<Path> art = files.filter(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return n.endsWith(".png") || n.endsWith(".ogg") || n.endsWith(".bbmodel");
            }).filter(p -> !AUTHOR_ART.contains(p.getFileName().toString())).filter(p -> !isOrePlaceholder(p)).filter(p -> !isTintBase(p)).toList();
            assertTrue(art.isEmpty(), "art files not provided by the author: " + art);
        }
    }

    /**
     * A colour layer's base ({@code block/cold_wall_tint/cold_wall_<colour>_single.png}, the sprite its connected
     * texture shifts from) is allowed only as tile (0, 0), the lone wall, cut from our sheet of that colour.
     */
    static boolean isTintBase(Path p) {
        String name = p.getFileName().toString();
        if (!name.endsWith("_single.png") || !p.getParent().getFileName().toString().equals("cold_wall_tint"))
            return false;
        try {
            var tile = javax.imageio.ImageIO.read(p.toFile());
            var sheet = javax.imageio.ImageIO.read(p.resolveSibling(name.replace("_single.png", ".png")).toFile());
            if (tile.getWidth() != 16 || tile.getHeight() != 16 || sheet.getWidth() != 128)
                return false;
            for (int x = 0; x < 16; x++)
                for (int y = 0; y < 16; y++)
                    if (tile.getRGB(x, y) != sheet.getRGB(x, y))
                        return false;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * A chilled ore placeholder ({@code textures/block/ore/<base>_frigorifie.png}, or {@code item/ore/}
     * for the raw ones) is allowed only as an identical copy of its base ore's texture, vanilla's or Create's. Once the
     * author repaints one, its name goes in {@link #AUTHOR_ART}.
     */
    static boolean isOrePlaceholder(Path p) {
        String name = p.getFileName().toString(), folder = p.getParent().getParent().getFileName().toString();
        if (!name.endsWith("_frigorifie.png") || !p.getParent().getFileName().toString().equals("ore"))
            return false;
        String base = name.substring(0, name.length() - "_frigorifie.png".length());
        try {
            byte[] ours = Files.readAllBytes(p);
            for (String ns : new String[]{"minecraft", "create"}) {
                ZipFile zip = ns.equals("create") ? createJar : vanillaJar;
                var entry = zip.getEntry("assets/" + ns + "/textures/" + folder + "/" + base + ".png");
                if (entry != null)
                    try (var in = zip.getInputStream(entry)) {
                        if (java.util.Arrays.equals(ours, in.readAllBytes()))
                            return true;
                    }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    @Test
    void authorTexturesHaveTheExpectedSize() throws IOException {
        Path dir = resources.resolve("assets/frostworks/textures/block");
        int[][] expected = {{16, 16}, {128, 128}};
        String[] names = {"cold_wall.png", "cold_wall_connected.png"};
        for (int i = 0; i < names.length; i++) {
            var image = javax.imageio.ImageIO.read(dir.resolve(names[i]).toFile());
            assertEquals(expected[i][0], image.getWidth(), names[i] + " width");
            assertEquals(expected[i][1], image.getHeight(), names[i] + " height");
        }
    }

    /** The strips are translucent, drawn from our own image (no alias, no stand-in). */
    @Test
    void stripTextureIsTranslucent() throws IOException {
        var image = javax.imageio.ImageIO.read(
                resources.resolve("assets/frostworks/textures/block/door_flap.png").toFile());
        assertTrue(image.getColorModel().hasAlpha(), "door_flap.png has an alpha channel");
        boolean partial = false;
        for (int x = 0; x < image.getWidth(); x++)
            for (int y = 0; y < image.getHeight(); y++) {
                int alpha = image.getRGB(x, y) >>> 24;
                partial |= alpha > 0 && alpha < 255;
            }
        assertTrue(partial, "door_flap.png has partly transparent pixels");
        assertFalse(ALIASES.containsKey("frostworks:block/door_flap"), "no atlas alias for the strip texture");
        JsonObject flap = JsonParser.parseString(Files.readString(
                resources.resolve("assets/frostworks/models/block/door_flap.json"))).getAsJsonObject();
        for (var entry : flap.getAsJsonObject("textures").entrySet())
            assertEquals("frostworks:block/door_flap", entry.getValue().getAsString(), "strip texture " + entry.getKey());
    }

    @Test
    void langFilesMatch() throws IOException {
        JsonObject en, fr;
        try (Reader r = Files.newBufferedReader(resources.resolve("assets/frostworks/lang/en_us.json"))) {
            en = JsonParser.parseReader(r).getAsJsonObject();
        }
        try (Reader r = Files.newBufferedReader(resources.resolve("assets/frostworks/lang/fr_fr.json"))) {
            fr = JsonParser.parseReader(r).getAsJsonObject();
        }
        assertEquals(en.keySet(), fr.keySet(), "en_us and fr_fr must have the same keys");
        for (String block : BLOCKS)
            assertTrue(en.has("block.frostworks." + block), "missing name of " + block);
        assertTrue(en.has("fluid_type.frostworks.cold_gas"));
    }
}
