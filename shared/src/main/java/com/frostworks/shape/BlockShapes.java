package com.frostworks.shape;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.frostworks.Frostworks;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A block's shape (collision and outline) built from the boxes of our model for its state
 * ({@link ModelBoxes}), read once from the mod's own files (in the jar on both sides) and kept per state.
 */
public final class BlockShapes {
    private BlockShapes() {}

    private static final Map<BlockState, VoxelShape> CACHE = new ConcurrentHashMap<>();

    public static VoxelShape of(BlockState state) {
        return CACHE.computeIfAbsent(state, BlockShapes::build);
    }

    private static VoxelShape build(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        Map<String, String> properties = new HashMap<>();
        for (Property<?> property : state.getProperties())
            properties.put(property.getName(), name(state, property));
        List<ModelBoxes.Box> boxes = ModelBoxes.forState(BlockShapes::read, id.getNamespace(), id.getPath(), properties);
        if (boxes.isEmpty())
            return Shapes.block();
        VoxelShape shape = Shapes.empty();
        for (ModelBoxes.Box b : boxes)
            shape = Shapes.or(shape, Block.box(clamp(b.x1()), clamp(b.y1()), clamp(b.z1()), clamp(b.x2()),
                    clamp(b.y2()), clamp(b.z2())));
        return shape.optimize();
    }

    private static <T extends Comparable<T>> String name(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    /** Collision boxes stay within the block. */
    private static double clamp(double v) {
        return Math.max(0, Math.min(16, v));
    }

    private static JsonObject read(String path) {
        try (InputStream in = BlockShapes.class.getResourceAsStream("/assets/" + path)) {
            if (in == null)
                return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            Frostworks.LOGGER.warn("Shape of {}: cannot read {}", path, e.toString());
            return null;
        }
    }
}
