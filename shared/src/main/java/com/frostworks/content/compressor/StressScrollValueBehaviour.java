package com.frostworks.content.compressor;

import com.frostworks.FwConfig;
import com.google.common.collect.ImmutableList;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Stress setting of the compressor, in su. Same panel as Create's speed controller, but each notch of the panel is
 * one config step (256 su by default) and the stored value is in su.
 */
public class StressScrollValueBehaviour extends ScrollValueBehaviour {

    public StressScrollValueBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
        super(label, be, slot);
        withFormatter(v -> com.frostworks.energy.Energy.su(v * com.frostworks.energy.Energy.stressScale()) + " su");
        refreshBounds();
        value = FwConfig.get(FwConfig.STRESS_DEFAULT);
    }

    /** Re-reads min/max from the config (it may arrive after the block entity was built on the client). */
    public void refreshBounds() {
        between(FwConfig.get(FwConfig.STRESS_MIN), Math.max(FwConfig.get(FwConfig.STRESS_MIN),
                FwConfig.get(FwConfig.STRESS_MAX)));
    }

    private static int step() {
        return Math.max(1, FwConfig.get(FwConfig.STRESS_STEP));
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        refreshBounds();
        int notches = Math.max(1, max / step());
        return new ValueSettingsBoard(label, notches, Math.max(1, notches / 8),
                ImmutableList.of(Component.literal("su")), new ValueSettingsFormatter(this::formatSettings));
    }

    @Override
    public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlHeld) {
        refreshBounds();
        if (!valueSetting.equals(getValueSettings()))
            playFeedbackSound(this);
        setValue(valueSetting.value() * step());
    }

    @Override
    public ValueSettings getValueSettings() {
        return new ValueSettings(0, Math.round(value / (float) step()));
    }

    private MutableComponent formatSettings(ValueSettings settings) {
        refreshBounds();
        int su = Math.max(FwConfig.get(FwConfig.STRESS_MIN), settings.value() * step());
        return CreateLang.text(com.frostworks.energy.Energy.su(su * com.frostworks.energy.Energy.stressScale()) + " su").component(); // Greate's scale
    }

    @Override
    public String getClipboardKey() {
        return "Stress";
    }
}
