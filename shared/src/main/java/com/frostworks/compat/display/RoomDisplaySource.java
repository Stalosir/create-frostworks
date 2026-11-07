package com.frostworks.compat.display;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;

/**
 * A Display Link on a cold vent or a cold wall shows one {@link RoomReading} of its room on a sign, a display
 * board or a nixie tube, about once a second. One line, which the player may label.
 */
public class RoomDisplaySource extends SingleLineDisplaySource {
    private final RoomReading reading;

    public RoomDisplaySource(RoomReading reading) {
        this.reading = reading;
    }

    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        if (!(context.level() instanceof ServerLevel level))
            return EMPTY_LINE;
        return reading.readAt(level, context.getSourcePos());
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        return true;
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 20;
    }
}
