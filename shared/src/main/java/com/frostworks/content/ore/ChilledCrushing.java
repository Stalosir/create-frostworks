package com.frostworks.content.ore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.frostworks.content.cold.ColdProcess;
import com.frostworks.content.cold.ColdVersion;
import com.google.gson.JsonObject;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * The chilled ores at the crushing wheels, for the texts (tooltip, Ponder). Their bonus is a cold processing file
 * for chilled stacks ({@code data/frostworks/recipe/cold_process/chilled_crushing.json}): one more of the main result
 * anywhere, more in a closed cold room. The numbers shown are read from that file, as the client received it; their 1.1
 * values before a world is open.
 */
public final class ChilledCrushing {
    private ChilledCrushing() {}

    /** The 1.1 file's numbers: {15 % anywhere, 25 % at -10 deg C, -10, 35 % at -20 deg C, -20}. */
    private static final long[] DEFAULTS = {15, 25, -10, 35, -20};

    /**
     * {percent anywhere, percent at the warmest cold step, its temperature, percent at the coldest step, its
     * temperature}, from the cold processing file that gives chilled stacks their crushing bonus (client side only).
     */
    public static long[] displayed() {
        try {
            long[] read = fromFiles(ClientLevel.get());
            return read != null ? read : DEFAULTS;
        } catch (RuntimeException | LinkageError e) {
            return DEFAULTS;
        }
    }

    /**
     * The tooltip lines of a chilled stack: "Chilled", then what it gives when crushed, one line per step, only while
     * Shift is held. The hint is Create's own look ("Hold [Shift] for Summary"): dark gray, its key (Create's name for
     * it) light gray, white while held.
     */
    public static List<Component> tooltip(boolean shift) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("frostworks.tooltip.chilled").withStyle(ChatFormatting.AQUA));
        Component key = Component.translatable("create.tooltip.keyShift")
                .withStyle(shift ? ChatFormatting.WHITE : ChatFormatting.GRAY);
        lines.add(Component.translatable("frostworks.tooltip.chilled_shift", key).withStyle(ChatFormatting.DARK_GRAY));
        if (!shift)
            return lines;
        long[] p = displayed();
        lines.add(Component.translatable("frostworks.tooltip.chilled_ore").withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("frostworks.tooltip.chilled_ore.anywhere", p[0]).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("frostworks.tooltip.chilled_ore.step", p[1], p[2]).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("frostworks.tooltip.chilled_ore.step", p[3], p[4]).withStyle(ChatFormatting.GRAY));
        return lines;
    }

    @Nullable
    private static long[] fromFiles(@Nullable Level level) {
        if (level == null)
            return null;
        for (Map.Entry<ResourceLocation, JsonObject> file : ColdVersion.files(level.getRecipeManager()).entrySet()) {
            ColdProcess cold;
            try {
                cold = ColdProcess.read(file.getKey(), file.getValue(), level.registryAccess());
            } catch (RuntimeException e) {
                continue;
            }
            if (cold.mode() != ColdProcess.Mode.BONUS || !cold.chilledOnly()
                    || !"create:crushing".equals(cold.process().toString()) || cold.steps().size() < 3)
                continue;
            // coldest first, the step that applies anywhere last
            ColdProcess.Step coldest = cold.steps().get(0), warmest = cold.steps().get(cold.steps().size() - 2),
                    anywhere = cold.steps().get(cold.steps().size() - 1);
            if (!anywhere.anywhere() || coldest.copies().isEmpty() || warmest.copies().isEmpty()
                    || anywhere.copies().isEmpty())
                continue;
            return new long[]{percent(anywhere), percent(warmest), warmest.temperature(), percent(coldest),
                    coldest.temperature()};
        }
        return null;
    }

    private static long percent(ColdProcess.Step step) {
        return Math.round(step.copies().get(0).chance() * 100);
    }

    /** Apart, so that a dedicated server never loads the client's classes. */
    private static final class ClientLevel {
        static Level get() {
            return net.minecraft.client.Minecraft.getInstance().level;
        }
    }
}
