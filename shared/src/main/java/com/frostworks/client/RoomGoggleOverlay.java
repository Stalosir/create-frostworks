package com.frostworks.client;

import java.util.ArrayList;
import java.util.List;

import com.frostworks.Frostworks;
import com.frostworks.network.RoomInfoPayload;
import com.frostworks.room.RoomText;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.foundation.gui.RemovedGuiUtils;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.infrastructure.config.CClient;

import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.lang.LangBuilder;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;

/**
 * goggles tooltip for blocks without a block entity (cold walls, doors, passages) and for vents. Same look and
 * placement as Create's {@code GoggleOverlayRenderer}, which only knows block entities; fed by
 * {@link RoomInfoPayload} from the server.
 */
public final class RoomGoggleOverlay {
    private RoomGoggleOverlay() {}

    private static int hoverTicks;
    /** What the tooltip shows (its rooms): from one wall to another of the same room, no new fade. */
    private static Object subject;

    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.gameMode == null
                || mc.gameMode.getPlayerMode() == GameType.SPECTATOR || mc.screen != null) {
            hoverTicks = 0;
            return;
        }
        RoomInfoPayload info = ClientRoomInfo.current();
        if (info == null || !GogglesItem.isWearingGoggles(mc.player)) {
            hoverTicks = 0;
            subject = null;
            return;
        }
        Object now = ClientRoomInfo.subject(info);
        if (!now.equals(subject))
            hoverTicks = 0;
        subject = now;
        hoverTicks++;

        // Every line starts with Create's goggles indent (LangBuilder.forGoggles, as
        // IHaveGoggleInformation tooltips do). The goggles icon is drawn in that indent, left of the text: without it
        // the icon covered the first characters.
        List<Component> tooltip = new ArrayList<>();
        for (MutableComponent line : RoomText.lines(info))
            new LangBuilder(Frostworks.MODID).add(line).forGoggles(tooltip);
        if (tooltip.isEmpty())
            return;

        int textWidth = 0;
        for (FormattedText line : tooltip)
            textWidth = Math.max(textWidth, mc.font.width(line));
        // A tooltip wider than the screen would be moved left of the cross by the tooltip code, away from the icon:
        // wrap it instead
        int maxTextWidth = -1;
        if (textWidth > graphics.guiWidth() - 40) {
            maxTextWidth = graphics.guiWidth() - 40;
            textWidth = maxTextWidth;
        }
        int tooltipHeight = 8 + (tooltip.size() > 1 ? 2 + (tooltip.size() - 1) * 10 : 0);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        CClient cfg = AllConfigs.client();
        int posX = width / 2 + cfg.overlayOffsetX.get();
        int posY = height / 2 + cfg.overlayOffsetY.get();
        // Create's own tooltip on the same block (a shaft passage of an overstressed network): ours goes under it,
        // the two were drawn one over the other
        int createHeight = createTooltipHeight(mc);
        if (createHeight > 0)
            posY += createHeight + 22; // past its box and the goggles icon above ours
        posX = Math.min(posX, width - textWidth - 20);
        posY = Math.min(posY, height - tooltipHeight - 20);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        float fade = Mth.clamp((hoverTicks + partialTick) / 24f, 0, 1);
        boolean custom = cfg.overlayCustomColor.get();
        Color background = custom ? new Color(cfg.overlayBackgroundColor.get())
                : BoxElement.COLOR_VANILLA_BACKGROUND.copy().scaleAlpha(.75f);
        Color borderTop = custom ? new Color(cfg.overlayBorderColorTop.get())
                : BoxElement.COLOR_VANILLA_BORDER.getFirst().copy();
        Color borderBottom = custom ? new Color(cfg.overlayBorderColorBot.get())
                : BoxElement.COLOR_VANILLA_BORDER.getSecond().copy();
        if (fade < 1) {
            pose.translate(Math.pow(1 - fade, 3) * Math.signum(cfg.overlayOffsetX.get() + .5f) * 8, 0, 0);
            background.scaleAlpha(fade);
            borderTop.scaleAlpha(fade);
            borderBottom.scaleAlpha(fade);
        }

        GuiGameElement.of(AllItems.GOGGLES.asStack()).at(posX + 10, posY - 16, 450).render(graphics);
        RemovedGuiUtils.drawHoveringText(graphics, tooltip, posX, posY, width, height, maxTextWidth, background.getRGB(),
                borderTop.getRGB(), borderBottom.getRGB(), mc.font);
        pose.popPose();
    }

    /**
     * The height of the tooltip Create's {@code GoggleOverlayRenderer} draws for the looked-at block, 0 if none.
     * The same lines it gathers: the goggles' information (goggles on) and the hovering one (overstressed network...).
     */
    private static int createTooltipHeight(Minecraft mc) {
        if (!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit) || mc.level == null)
            return 0;
        net.minecraft.core.BlockPos pos = com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer
                .proxiedOverlayPosition(mc.level, hit.getBlockPos());
        net.minecraft.world.level.block.entity.BlockEntity be = mc.level.getBlockEntity(pos);
        boolean shift = mc.player.isShiftKeyDown();
        List<Component> lines = new ArrayList<>();
        boolean goggles = false, hovering = false;
        if (be instanceof com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation info)
            goggles = info.addToGoggleTooltip(lines, shift);
        if (be instanceof com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation info) {
            if (!lines.isEmpty())
                lines.add(Component.empty());
            hovering = info.addToTooltip(lines, shift);
            if (goggles && !hovering)
                lines.remove(lines.size() - 1);
        }
        if (lines.isEmpty())
            return 0;
        return 8 + (lines.size() > 1 ? 2 + (lines.size() - 1) * 10 : 0);
    }
}
