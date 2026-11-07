package com.frostworks.content.column;

import java.util.List;
import java.util.Locale;

import com.frostworks.Frostworks;
import com.frostworks.registry.FwFluids;

import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * The tower's goggle tooltip, read from its controller: its size, its room and each fluid held, the ice left, then
 * what it makes per second while it runs, or why it stopped.
 */
final class TowerGoggles {
    private TowerGoggles() {}

    static void add(TowerBlockEntity tower, List<Component> tooltip) {
        line("goggles.tower").forGoggles(tooltip);
        if (!tower.valid) {
            line("goggles.tower.invalid").style(ChatFormatting.RED).forGoggles(tooltip, 1);
            return;
        }
        TowerStorage storage = tower.storage;
        line("goggles.tower.layers", tower.height).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);

        // the room, then one line per fluid held: its mB and its room
        int room = tower.capacity();
        List<Distillation> all = storage.recipes();
        int total = storage.output;
        for (TowerStorage.Held h : storage.inputs)
            total += Math.max(0, h.amount);
        line("goggles.tower.container", room, percent(total, room)).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        for (TowerStorage.Held h : storage.inputs)
            if (h.amount > 0)
                line("goggles.tower.fluid", name(h.fluid), h.amount, Math.max(h.amount, storage.roomFor(h.fluid, all)))
                        .style(color(h.fluid)).forGoggles(tooltip, 2);
        if (storage.outputFluid != null && storage.output > 0)
            line("goggles.tower.fluid", name(storage.outputFluid), storage.output, room)
                    .style(ChatFormatting.WHITE).forGoggles(tooltip, 2);

        line("goggles.tower.ice", String.format(Locale.ROOT, "%.2f", tower.iceStock()))
                .style(tower.stop == TowerBlockEntity.Stop.NO_ICE ? ChatFormatting.GOLD : ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        Distillation r = storage.current(all);
        if (tower.running && r != null)
            rates(tower, r, tooltip);
        else if (!tower.running)
            line("goggles.tower.stopped." + tower.stop.name().toLowerCase()).style(ChatFormatting.GOLD)
                    .forGoggles(tooltip, 1);
    }

    /** One value a line: what goes in, what comes out, what it costs. */
    private static void rates(TowerBlockEntity tower, Distillation r, List<Component> tooltip) {
        line("goggles.tower.running").style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        double liquid = tower.liquidRate();
        for (int i = 0; i < r.inputs.size(); i++) {
            TowerStorage.Held h = tower.storage.taking(r.inputs.get(i));
            if (h == null)
                continue;
            String rate = i == 0 ? String.valueOf(tower.gasRate())
                    : String.format("%.1f", liquid * r.inputPerOutput(i));
            line("goggles.tower.rate.in", name(h.fluid), rate).style(ChatFormatting.GRAY).forGoggles(tooltip, 2);
        }
        line("goggles.tower.rate.out", name(r.result), String.format("%.1f", liquid))
                .style(ChatFormatting.GRAY).forGoggles(tooltip, 2);
        if (r.ice)
            line("goggles.tower.rate.ice", String.format(Locale.ROOT, "%.2f", liquid * r.icePerLiquid() * 60))
                    .style(ChatFormatting.GRAY).forGoggles(tooltip, 2);
        line("goggles.tower.rate.stress", String.format("%.0f", tower.targetStress()))
                .style(ChatFormatting.GRAY).forGoggles(tooltip, 2);
    }

    private static LangBuilder line(String key, Object... args) {
        return new LangBuilder(Frostworks.MODID).translate(key, args);
    }

    private static Component name(Fluid fluid) {
        return fluid.getFluidType().getDescription();
    }

    private static ChatFormatting color(Fluid fluid) {
        if (fluid.isSame(FwFluids.gas()))
            return ChatFormatting.AQUA;
        return fluid.isSame(Fluids.WATER) ? ChatFormatting.BLUE : ChatFormatting.WHITE;
    }

    private static int percent(int amount, int room) {
        return room <= 0 ? 0 : (int) Math.round(amount * 100.0 / room);
    }
}
