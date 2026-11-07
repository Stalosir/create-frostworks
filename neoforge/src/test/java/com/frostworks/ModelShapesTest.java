package com.frostworks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.frostworks.shape.ModelBoxes;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * In every state of the blockstate (each shape, corner, part, facing), the boxes the game builds the
 * hitbox from are our model boxes, turned as the blockstate turns the model; worked out here a second way
 * (each corner of each box turned by quarter turns), to 1/16 px.
 */
class ModelShapesTest {

    static final Path ASSETS = Path.of(System.getProperty("frostworks.resourcesDir")).resolve("assets");

    static JsonObject json(String path) {
        Path p = ASSETS.resolve(path);
        if (!Files.exists(p))
            return null;
        try (Reader r = Files.newBufferedReader(p)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A quarter turn about X (top to north) and about Y (north to east), point by point. */
    static double[] quarterX(double[] p) {
        return new double[]{p[0], p[2], 16 - p[1]};
    }

    static double[] quarterY(double[] p) {
        return new double[]{16 - p[2], p[1], p[0]};
    }

    /** The boxes of a model turned by x then y, the second way. */
    static List<double[]> expected(JsonObject model, int x, int y) {
        List<double[]> out = new ArrayList<>();
        for (JsonElement e : model.getAsJsonArray("elements")) {
            JsonObject el = e.getAsJsonObject();
            double[] from = new double[3], to = new double[3];
            for (int i = 0; i < 3; i++) {
                from[i] = el.getAsJsonArray("from").get(i).getAsDouble();
                to[i] = el.getAsJsonArray("to").get(i).getAsDouble();
            }
            if (Math.abs(to[0] - from[0]) < 1e-6 || Math.abs(to[1] - from[1]) < 1e-6 || Math.abs(to[2] - from[2]) < 1e-6)
                continue; // a flat face: no volume
            double[] min = {99, 99, 99}, max = {-99, -99, -99};
            for (int c = 0; c < 8; c++) {
                double[] p = {(c & 1) == 0 ? from[0] : to[0], (c & 2) == 0 ? from[1] : to[1], (c & 4) == 0 ? from[2] : to[2]};
                for (int i = 0; i < Math.floorMod(x, 360) / 90; i++)
                    p = quarterX(p);
                for (int i = 0; i < Math.floorMod(y, 360) / 90; i++)
                    p = quarterY(p);
                for (int k = 0; k < 3; k++) {
                    min[k] = Math.min(min[k], p[k]);
                    max[k] = Math.max(max[k], p[k]);
                }
            }
            out.add(new double[]{min[0], min[1], min[2], max[0], max[1], max[2]});
        }
        return out;
    }

    static final Comparator<double[]> ORDER = (a, b) -> {
        for (int i = 0; i < 6; i++) {
            int c = Double.compare(Math.round(a[i] * 16), Math.round(b[i] * 16));
            if (c != 0)
                return c;
        }
        return 0;
    };

    @ParameterizedTest
    @ValueSource(strings = {"column_base", "column_segment", "expander"})
    void hitboxIsTheModel(String block) {
        JsonObject blockstate = json("frostworks/blockstates/" + block + ".json");
        assertTrue(blockstate != null, "no blockstate for " + block);
        int states = 0;
        for (Map.Entry<String, JsonElement> e : blockstate.getAsJsonObject("variants").entrySet()) {
            Map<String, String> properties = ModelBoxes.properties(e.getKey());
            List<ModelBoxes.Box> game = ModelBoxes.forState(ModelShapesTest::json, "frostworks", block, properties);
            JsonObject v = e.getValue().getAsJsonObject();
            String[] model = v.get("model").getAsString().split(":", 2);
            List<double[]> want = expected(json(model[0] + "/models/" + model[1] + ".json"),
                    v.has("x") ? v.get("x").getAsInt() : 0, v.has("y") ? v.get("y").getAsInt() : 0);
            List<double[]> got = new ArrayList<>();
            for (ModelBoxes.Box b : game)
                got.add(new double[]{b.x1(), b.y1(), b.z1(), b.x2(), b.y2(), b.z2()});
            assertFalse(got.isEmpty(), block + " " + e.getKey() + ": no box");
            want.sort(ORDER);
            got.sort(ORDER);
            assertEquals(want.size(), got.size(), block + " " + e.getKey() + ": number of boxes");
            for (int i = 0; i < want.size(); i++)
                for (int k = 0; k < 6; k++)
                    assertEquals(want.get(i)[k], got.get(i)[k], 1 / 16.0,
                            block + " " + e.getKey() + ": box " + i + " coordinate " + k);
            states++;
        }
        System.out.println("SHAPES " + block + ": " + states + " states follow the model");
    }
}
