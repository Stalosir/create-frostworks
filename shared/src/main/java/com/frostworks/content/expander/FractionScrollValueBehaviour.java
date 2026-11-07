package com.frostworks.content.expander;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

/** Share of the expander's energy given back as stress capacity: 0 to 100 %, by steps of 5 % (value = percent). */
public class FractionScrollValueBehaviour extends ScrollValueBehaviour {
    public static final int STEP = 5;

    public FractionScrollValueBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
        super(label, be, slot);
        between(0, 100);
        value = 100;
        withFormatter(v -> v + " %");
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        return new ValueSettingsBoard(label, 100 / STEP, 4, ImmutableList.of(Component.literal("%")),
                new ValueSettingsFormatter(this::format));
    }

    @Override
    public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlHeld) {
        if (!valueSetting.equals(getValueSettings()))
            playFeedbackSound(this);
        setValue(valueSetting.value() * STEP);
    }

    @Override
    public ValueSettings getValueSettings() {
        return new ValueSettings(0, value / STEP);
    }

    private MutableComponent format(ValueSettings settings) {
        return Component.literal(settings.value() * STEP + " %");
    }

    @Override
    public String getClipboardKey() {
        return "StressShare";
    }
}
