package com.frostworks.client.ponder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.frostworks.Frostworks;
import com.frostworks.network.RoomInfoPayload;
import com.frostworks.room.RoomText;
import com.frostworks.room.core.RoomStatus;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.gui.RemovedGuiUtils;

import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.lang.LangBuilder;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedOverlayElementBase;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

/**
 * A goggles tooltip drawn in a Ponder scene, near the middle of the screen as the goggles show it
 * next to the crosshair in game (it may pass in front of the room, never over the texts). The Ponder world runs no
 * room code: the scene says what the room is (status, vents, Frosium, temperature target) and this element shows it
 * with the real goggles lines ({@link RoomText}, the same translation keys), the temperature easing to its target at a
 * scene speed.
 */
public class GogglesDisplay extends AnimatedOverlayElementBase {

    /** What the scene says the room is. */
    public record State(RoomStatus status, int activeVents, int vents, int capacity, int maxRpm, float target) {}

    private final int volume, coverage, baseRpm, searchCap;
    /**
     * The opacity, raised every tick. Ponder's fade interpolates between the last two values set: set
     * only once (0 then 1), it followed the frame's partial tick and the display blinked.
     */
    private float opacity;
    private State state;
    /** Other lines than a room's (a vent, a machine), given by the scene; null for a room. */
    @Nullable
    private Supplier<List<MutableComponent>> lines;
    private float temperature, previous;
    /** Degrees per tick (a scene speed: the real room changes by 0.5 deg C per second). */
    private float rate;

    /** Scale of the tooltip, a little smaller than in game. */
    private static final float SCALE = .85f;
    private static final float FADE_PER_TICK = .1f;

    public GogglesDisplay(int volume, int coverage, int baseRpm, int searchCap) {
        this.searchCap = searchCap;
        this.volume = volume;
        this.coverage = coverage;
        this.baseRpm = baseRpm;
    }

    /** Adds the display to the scene, showing {@code state} at {@code temperature}. */
    public void show(SceneBuilder scene, State state, float temperature) {
        scene.addInstruction(ps -> {
            this.state = state;
            lines = null;
            this.temperature = previous = temperature;
            rate = 0;
            opacity = 0;
            setFade(0);
            setFade(0);
            setVisible(true);
            ps.addElement(this);
        });
    }

    /** Adds the display with other lines than a room's (they are read again every frame). */
    public void showLines(SceneBuilder scene, Supplier<List<MutableComponent>> lines) {
        scene.addInstruction(ps -> {
            this.lines = lines;
            state = null;
            opacity = 0;
            setFade(0);
            setFade(0);
            setVisible(true);
            ps.addElement(this);
        });
    }

    /** New lines, without a new fade. */
    public void setLines(SceneBuilder scene, Supplier<List<MutableComponent>> lines) {
        scene.addInstruction(ps -> this.lines = lines);
    }

    /** The room changes; its temperature moves to the new target at {@code rate} degrees per tick. */
    public void set(SceneBuilder scene, State state, float rate) {
        scene.addInstruction(ps -> {
            this.state = state;
            this.rate = rate;
        });
    }

    @Override
    public void tick(PonderScene scene) {
        if (isVisible()) {
            opacity = Math.min(1, opacity + FADE_PER_TICK);
            setFade(opacity);
        }
        previous = temperature;
        if (state != null && rate > 0)
            temperature = temperature < state.target() ? Math.min(state.target(), temperature + rate)
                    : Math.max(state.target(), temperature - rate);
    }

    @Override
    public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float partialTicks, float fade) {
        if (state == null && lines == null || fade < 1 / 16f)
            return;
        List<MutableComponent> source;
        if (lines != null)
            source = lines.get();
        else {
            float shown = Mth.lerp(partialTicks, previous, temperature);
            RoomInfoPayload.RoomView room = new RoomInfoPayload.RoomView(1, shown, volume, state.capacity(),
                    state.status(), state.activeVents(), state.vents(), coverage, searchCap, false, state.maxRpm(),
                    baseRpm);
            source = RoomText.lines(new RoomInfoPayload(BlockPos.ZERO, List.of(room), null));
        }
        List<Component> tooltip = new ArrayList<>();
        for (MutableComponent line : source)
            new LangBuilder(Frostworks.MODID).add(line).forGoggles(tooltip);

        Minecraft mc = Minecraft.getInstance();
        int textWidth = 0;
        for (Component line : tooltip)
            textWidth = Math.max(textWidth, mc.font.width(line));
        int height = 8 + (tooltip.size() > 1 ? 2 + (tooltip.size() - 1) * 10 : 0);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        // Near the middle, just left of it: Ponder puts its texts beside what they point at, mostly on the right
        // (never past the left edge: the OPEN lines are the widest)
        float left = Math.max(6, screen.width / 2f - 12 - (textWidth + 12) * SCALE);
        // Depth 200, under Ponder's texts (400 and up): where they cross, the text of the scene is drawn over it
        pose.translate(left, screen.height / 2f - height * SCALE / 2 + 20, 200);
        pose.scale(SCALE, SCALE, 1);
        Color background = BoxElement.COLOR_VANILLA_BACKGROUND.copy().scaleAlpha(.75f * fade);
        Color top = BoxElement.COLOR_VANILLA_BORDER.getFirst().copy().scaleAlpha(fade);
        Color bottom = BoxElement.COLOR_VANILLA_BORDER.getSecond().copy().scaleAlpha(fade);
        // drawHoveringText puts the box 12 px right of and 12 px above the point it is given
        int x = -12, y = 12 + 4;
        GuiGameElement.of(AllItems.GOGGLES.asStack()).at(x + 10, y - 16, 450).render(graphics);
        RemovedGuiUtils.drawHoveringText(graphics, tooltip, x, y, textWidth + 40, 10_000, -1,
                background.getRGB(), top.getRGB(), bottom.getRGB(), mc.font);
        pose.popPose();
    }
}
