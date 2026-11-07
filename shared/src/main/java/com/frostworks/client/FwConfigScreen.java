package com.frostworks.client;

import java.util.List;

import com.frostworks.Frostworks;
import com.mojang.blaze3d.systems.RenderSystem;

import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

/**
 * Our settings in Create's own config screen (the "Config" button of the mod list), with our
 * title ({@code branding/frostworks-title-source.png}, shrunk) where Create writes the mod's name.
 */
public class FwConfigScreen extends BaseConfigScreen {

    private static final ResourceLocation TITLE = Frostworks.id("textures/gui/title.png");
    private static final int TITLE_W = 557, TITLE_H = 170;
    /** Drawn height of the title (GUI pixels): above the three buttons, which start 45 above the middle. */
    private static final int DRAWN_H = 60;

    public FwConfigScreen(Screen parent) {
        super(parent, Frostworks.MODID);
    }

    @Override
    protected void init() {
        super.init();
        // Create's title box (the mod's name in text), the one widget it lays out at x = -5, makes room for ours
        for (GuiEventListener widget : List.copyOf(children()))
            if (widget instanceof BoxWidget box && box.getX() == -5)
                removeWidget(box);
    }

    /** The title in place of the name (not Create's own text here, which its box used to cover). */
    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int w = DRAWN_H * TITLE_W / TITLE_H;
        RenderSystem.enableBlend();
        graphics.blit(TITLE, (width - w) / 2, height / 2 - 52 - DRAWN_H, w, DRAWN_H, 0, 0, TITLE_W, TITLE_H, TITLE_W,
                TITLE_H);
        RenderSystem.disableBlend();
    }
}
