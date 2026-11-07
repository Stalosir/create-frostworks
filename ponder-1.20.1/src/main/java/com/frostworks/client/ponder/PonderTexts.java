package com.frostworks.client.ponder;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.function.Supplier;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.TextElementBuilder;
import net.createmod.ponder.foundation.element.TextWindowElement;
import net.minecraft.world.phys.Vec3;

/**
 * Forge 1.20.1: Ponder 1.0.52 takes plain texts only ({@code text(String)}), where the root's Ponder takes their
 * arguments too (the settings' numbers, written %1$s in the lang files). This keeps the same calls: the text is
 * registered as usual (its key, its English default), then shown formatted with its arguments, in the player's language.
 */
public final class PonderTexts implements TextElementBuilder {
    private static final Field ELEMENT, TEXT;

    static {
        try {
            ELEMENT = Class.forName("net.createmod.ponder.foundation.element.TextWindowElement$Builder").getDeclaredField("this$0");
            ELEMENT.setAccessible(true);
            TEXT = TextWindowElement.class.getDeclaredField("textGetter");
            TEXT.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Ponder's text window changed", e);
        }
    }

    private final TextElementBuilder builder;

    private PonderTexts(TextElementBuilder builder) {
        this.builder = builder;
    }

    public static PonderTexts of(TextElementBuilder builder) {
        return new PonderTexts(builder);
    }

    public PonderTexts text(String text, Object... args) {
        builder.text(text);
        return withArgs(args);
    }

    public PonderTexts sharedText(String key, Object... args) {
        builder.sharedText(key);
        return withArgs(args);
    }

    @SuppressWarnings("unchecked")
    private PonderTexts withArgs(Object[] args) {
        if (args.length == 0)
            return this;
        try {
            Object element = ELEMENT.get(builder);
            Supplier<String> localized = (Supplier<String>) TEXT.get(element);
            TEXT.set(element, (Supplier<String>) () -> format(localized.get(), args));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Ponder's text window changed", e);
        }
        return this;
    }

    /** Ponder reads the text with I18n.get(key) and no argument: Minecraft answers "Format error: " and the raw text. */
    private static String format(String localized, Object[] args) {
        String raw = localized.startsWith(FORMAT_ERROR) ? localized.substring(FORMAT_ERROR.length()) : localized;
        try {
            return String.format(Locale.ROOT, raw, args);
        } catch (java.util.IllegalFormatException e) {
            return raw;
        }
    }

    private static final String FORMAT_ERROR = "Format error: ";

    @Override
    public PonderTexts text(String text) {
        builder.text(text);
        return this;
    }

    @Override
    public PonderTexts sharedText(net.minecraft.resources.ResourceLocation key) {
        builder.sharedText(key);
        return this;
    }

    @Override
    public PonderTexts sharedText(String key) {
        builder.sharedText(key);
        return this;
    }

    @Override
    public PonderTexts colored(PonderPalette color) {
        builder.colored(color);
        return this;
    }

    @Override
    public PonderTexts pointAt(Vec3 vec) {
        builder.pointAt(vec);
        return this;
    }

    @Override
    public PonderTexts independent(int y) {
        builder.independent(y);
        return this;
    }

    @Override
    public PonderTexts placeNearTarget() {
        builder.placeNearTarget();
        return this;
    }

    @Override
    public PonderTexts attachKeyFrame() {
        builder.attachKeyFrame();
        return this;
    }
}
