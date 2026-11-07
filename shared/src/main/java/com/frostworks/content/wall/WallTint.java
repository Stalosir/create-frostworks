package com.frostworks.content.wall;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;

/**
 * The colour of a placed cold wall (block state {@code tint}). Gray is the wall's own colour: no layer, the gray
 * dye takes the colour away. The colour lives only on the placed block: the item, the drops and the room ignore it.
 */
public enum WallTint implements StringRepresentable {
    NONE(null),
    WHITE(DyeColor.WHITE), ORANGE(DyeColor.ORANGE), MAGENTA(DyeColor.MAGENTA), LIGHT_BLUE(DyeColor.LIGHT_BLUE),
    YELLOW(DyeColor.YELLOW), LIME(DyeColor.LIME), PINK(DyeColor.PINK), LIGHT_GRAY(DyeColor.LIGHT_GRAY),
    CYAN(DyeColor.CYAN), PURPLE(DyeColor.PURPLE), BLUE(DyeColor.BLUE), BROWN(DyeColor.BROWN), GREEN(DyeColor.GREEN),
    RED(DyeColor.RED), BLACK(DyeColor.BLACK);

    @Nullable
    public final DyeColor dye;

    WallTint(@Nullable DyeColor dye) {
        this.dye = dye;
    }

    /** The colour a dye gives (gray: none). */
    public static WallTint of(DyeColor dye) {
        for (WallTint t : values())
            if (t.dye == dye)
                return t;
        return NONE;
    }

    @Override
    public String getSerializedName() {
        return dye == null ? "none" : dye.getSerializedName();
    }
}
