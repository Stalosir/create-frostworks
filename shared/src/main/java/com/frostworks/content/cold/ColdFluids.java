package com.frostworks.content.cold;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import com.frostworks.Frostworks;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

/**
 * The cold fluids at work, what a vent does with the fluid it holds. The files ({@link ColdFluid}) are read once
 * per recipe manager (the server's, the client's), again after each reload of the tags or the recipes; a broken file is
 * left out, with a line in the server's log. Two files that take the same fluid: the colder one.
 */
public final class ColdFluids {
    private ColdFluids() {}

    /** Read files of each recipe manager, coldest first. */
    private static final Map<RecipeManager, List<ColdFluid>> READ = new WeakHashMap<>();

    /** The tags or the recipes were reloaded: the files are read again on their next use. */
    public static synchronized void invalidate() {
        READ.clear();
    }

    /** Every cold fluid this level knows, coldest first (then by id). */
    public static synchronized List<ColdFluid> all(Level level) {
        RecipeManager recipes = level.getRecipeManager();
        List<ColdFluid> read = READ.get(recipes);
        if (read == null) {
            read = read(recipes, !level.isClientSide());
            READ.put(recipes, read);
        }
        return read;
    }

    /** The file a vent uses for this fluid (the coldest that takes it), or null: the vent does not take it. */
    @Nullable
    public static ColdFluid of(@Nullable Level level, Fluid fluid) {
        if (level == null)
            return null;
        for (ColdFluid cold : all(level))
            if (cold.takes(fluid))
                return cold;
        return null;
    }

    /**
     * For the texts (Ponder): the file for this fluid in the client's level, or {@code fallback} (its 1.1 values) before
     * a world is open or without a file for it.
     */
    public static ColdFluid shown(Fluid fluid, ColdFluid fallback) {
        try {
            ColdFluid read = of(ClientLevel.get(), fluid);
            return read != null ? read : fallback;
        } catch (RuntimeException | LinkageError e) {
            return fallback;
        }
    }

    private static List<ColdFluid> read(RecipeManager recipes, boolean log) {
        List<ColdFluid> read = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonObject> file : new TreeMap<>(ColdVersion.fluidFiles(recipes)).entrySet()) {
            try {
                read.add(ColdFluid.read(file.getKey(), file.getValue()));
            } catch (RuntimeException e) {
                if (log)
                    Frostworks.LOGGER.error("Cold fluid recipe {} left out: {}", file.getKey(), e.getMessage());
            }
        }
        read.sort(Comparator.comparingInt(ColdFluid::temperature)); // stable: by id within a temperature
        if (log)
            Frostworks.LOGGER.info("Cold fluids: {} recipe(s) read", read.size());
        return List.copyOf(read);
    }

    /** Apart, so that a dedicated server never loads the client's classes. */
    private static final class ClientLevel {
        static Level get() {
            return net.minecraft.client.Minecraft.getInstance().level;
        }
    }
}
