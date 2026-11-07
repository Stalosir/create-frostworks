package com.frostworks.client;

import static net.minecraft.ChatFormatting.GRAY;

import java.util.ArrayList;
import java.util.List;

import com.frostworks.FwConfig;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.content.kinetics.base.IRotate.StressImpact;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * The column base's "Stress impact" line, as Create gives it on its machines (low, medium, high; the
 * number per rpm with goggles on). The tower draws its stress from what it processes, not from a fixed value Create
 * knows, so the line is worked out here: one layer, {@code towerStressPerRpm}.
 */
public final class TowerStats implements TooltipModifier {

    /** Stress per rpm of a one-layer tower (su/rpm). */
    public static double impactPerRpm() {
        return value(FwConfig.TOWER_STRESS_PER_RPM) * com.frostworks.energy.Energy.stressScale(); // Greate's scale
    }

    /** The server setting when a world is open (synced to the client), else its default. */
    private static double value(ModConfigSpec.ConfigValue<? extends Number> v) {
        try {
            return v.get().doubleValue();
        } catch (RuntimeException e) {
            return v.getDefault().doubleValue();
        }
    }

    @Override
    public void modify(ItemTooltipEvent context) {
        if (!StressImpact.isEnabled())
            return;
        double impact = impactPerRpm();
        var config = AllConfigs.server().kinetics;
        StressImpact level = impact >= config.highStressImpact.get() ? StressImpact.HIGH
                : impact >= config.mediumStressImpact.get() ? StressImpact.MEDIUM : StressImpact.LOW;
        List<Component> lines = new ArrayList<>();
        CreateLang.translate("tooltip.stressImpact").style(GRAY).addTo(lines);
        LangBuilder builder = CreateLang.builder().add(CreateLang.text(TooltipHelper.makeProgressBar(3,
                level.ordinal() + 1)).style(level.getAbsoluteColor()));
        if (GogglesItem.isWearingGoggles(context.getEntity()))
            builder.add(CreateLang.number(impact)).text("x ").add(CreateLang.translate("generic.unit.rpm")).addTo(lines);
        else
            builder.translate("tooltip.stressImpact." + Lang.asId(level.name())).addTo(lines);
        context.getToolTip().add(CommonComponents.EMPTY);
        context.getToolTip().addAll(lines);
    }
}
