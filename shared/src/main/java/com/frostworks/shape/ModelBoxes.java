package com.frostworks.shape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The boxes of a block's shape, read from our models, so the hitbox (collision and outline)
 * follows the 3D model and never drifts from it. Pure Java (only Gson), tested without the game.
 * <ul>
 * <li>a model's boxes are the {@code from} / {@code to} of its elements, in pixels; a flat element (zero thickness, a
 * face drawn alone) has no volume and is left out; a tilted element counts as its bounding box;</li>
 * <li>the blockstate gives, for a state, the model and its rotation ({@code x} then {@code y}, in steps of 90 degrees,
 * as Minecraft turns the model).</li>
 * </ul>
 */
public final class ModelBoxes {
    private ModelBoxes() {}

    /** A box in pixels: x1, y1, z1, x2, y2, z2. */
    public record Box(double x1, double y1, double z1, double x2, double y2, double z2) {}

    /** The variant of a state: its model and rotation. */
    public record Variant(String model, int x, int y) {}

    /** The boxes of a model JSON (no parent is followed: our models have none). */
    public static List<Box> ofModel(JsonObject model) {
        List<Box> boxes = new ArrayList<>();
        if (!model.has("elements"))
            return boxes;
        for (JsonElement e : model.getAsJsonArray("elements")) {
            JsonObject element = e.getAsJsonObject();
            double[] from = triple(element.getAsJsonArray("from"));
            double[] to = triple(element.getAsJsonArray("to"));
            Box box = new Box(Math.min(from[0], to[0]), Math.min(from[1], to[1]), Math.min(from[2], to[2]),
                    Math.max(from[0], to[0]), Math.max(from[1], to[1]), Math.max(from[2], to[2]));
            if (element.has("rotation")) {
                JsonObject r = element.getAsJsonObject("rotation");
                double angle = r.has("angle") ? r.get("angle").getAsDouble() : 0;
                if (angle != 0)
                    box = tilted(box, r.get("axis").getAsString(), angle, triple(r.getAsJsonArray("origin")));
            }
            if (box.x2() - box.x1() > 1e-6 && box.y2() - box.y1() > 1e-6 && box.z2() - box.z1() > 1e-6)
                boxes.add(box);
        }
        return boxes;
    }

    private static double[] triple(JsonArray a) {
        return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
    }

    /** The bounding box of a box turned by {@code angle} degrees about an axis through {@code origin}. */
    static Box tilted(Box b, String axis, double angle, double[] o) {
        double rad = Math.toRadians(angle), c = Math.cos(rad), s = Math.sin(rad);
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int i = 0; i < 8; i++) {
            double[] p = {(i & 1) == 0 ? b.x1() : b.x2(), (i & 2) == 0 ? b.y1() : b.y2(), (i & 4) == 0 ? b.z1() : b.z2()};
            double x = p[0] - o[0], y = p[1] - o[1], z = p[2] - o[2];
            double[] q = switch (axis) {
                case "x" -> new double[]{x, y * c - z * s, y * s + z * c};
                case "y" -> new double[]{x * c + z * s, y, -x * s + z * c};
                default -> new double[]{x * c - y * s, x * s + y * c, z};
            };
            for (int k = 0; k < 3; k++) {
                min[k] = Math.min(min[k], q[k] + o[k]);
                max[k] = Math.max(max[k], q[k] + o[k]);
            }
        }
        return new Box(min[0], min[1], min[2], max[0], max[1], max[2]);
    }

    /**
     * The boxes turned as the blockstate turns the model: {@code x} then {@code y} (degrees, multiples of 90), about the
     * block's centre; {@code x 90} takes the top to the north, {@code y 90} the north to the east.
     */
    public static List<Box> rotate(List<Box> boxes, int x, int y) {
        List<Box> out = new ArrayList<>(boxes.size());
        for (Box b : boxes) {
            double[] p1 = turn(b.x1(), b.y1(), b.z1(), x, y), p2 = turn(b.x2(), b.y2(), b.z2(), x, y);
            out.add(new Box(Math.min(p1[0], p2[0]), Math.min(p1[1], p2[1]), Math.min(p1[2], p2[2]),
                    Math.max(p1[0], p2[0]), Math.max(p1[1], p2[1]), Math.max(p1[2], p2[2])));
        }
        return out;
    }

    private static double[] turn(double px, double py, double pz, int x, int y) {
        double vx = px - 8, vy = py - 8, vz = pz - 8;
        for (int i = 0; i < Math.floorMod(x, 360) / 90; i++) { // top -> north
            double ny = vz, nz = -vy;
            vy = ny;
            vz = nz;
        }
        for (int i = 0; i < Math.floorMod(y, 360) / 90; i++) { // north -> east
            double nx = -vz, nz = vx;
            vx = nx;
            vz = nz;
        }
        return new double[]{vx + 8, vy + 8, vz + 8};
    }

    /** The variant of a blockstate JSON whose key matches these property values, or null. */
    public static Variant variant(JsonObject blockstate, Map<String, String> properties) {
        for (Map.Entry<String, JsonElement> e : blockstate.getAsJsonObject("variants").entrySet()) {
            if (!matches(e.getKey(), properties))
                continue;
            JsonElement v = e.getValue();
            JsonObject o = v.isJsonArray() ? v.getAsJsonArray().get(0).getAsJsonObject() : v.getAsJsonObject();
            return new Variant(o.get("model").getAsString(), o.has("x") ? o.get("x").getAsInt() : 0,
                    o.has("y") ? o.get("y").getAsInt() : 0);
        }
        return null;
    }

    static boolean matches(String key, Map<String, String> properties) {
        if (key.isEmpty())
            return true;
        for (String pair : key.split(",")) {
            String[] kv = pair.split("=");
            if (!kv[1].equals(properties.get(kv[0])))
                return false;
        }
        return true;
    }

    /** The property values written in a variant key ("corner=north_west,part=top"). */
    public static Map<String, String> properties(String key) {
        Map<String, String> map = new HashMap<>();
        if (!key.isEmpty())
            for (String pair : key.split(",")) {
                String[] kv = pair.split("=");
                map.put(kv[0], kv[1]);
            }
        return map;
    }

    /**
     * The boxes of a block state, from its blockstate and model files ({@code json} gives a file's JSON from its path
     * under {@code assets/}, e.g. {@code frostworks/blockstates/expander.json}); empty if nothing matches.
     */
    public static List<Box> forState(Function<String, JsonObject> json, String namespace, String block,
                                     Map<String, String> properties) {
        JsonObject blockstate = json.apply(namespace + "/blockstates/" + block + ".json");
        Variant v = blockstate == null ? null : variant(blockstate, properties);
        if (v == null)
            return List.of();
        String[] model = v.model().split(":", 2);
        JsonObject m = json.apply(model[0] + "/models/" + model[1] + ".json");
        return m == null ? List.of() : rotate(ofModel(m), v.x(), v.y());
    }
}
