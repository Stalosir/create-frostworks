package com.frostworks.overspeed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import com.frostworks.Frostworks;
import com.frostworks.content.cold.ColdProcessing;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

/**
 * The cold lowers the stress. A machine in a closed room (COLD or INSUFFICIENT) applies 10 % less stress from -10
 * deg C, 20 % less from -20 deg C, in steps, by the room's real temperature. Every kinetic block of Create or of its
 * addons (Greate included), except this mod's own: their stress is part of the energy loop. The capacity is never
 * raised (Greate's cap neither).
 * <p>
 * The mixins on {@code KineticBlockEntity} scale what {@code calculateStressApplied} returns, after every other mod
 * (TerraFirmaGreg-Core's additions included), by the step the block holds. The server checks that step once a second
 * per block ({@link #due}, {@link #stepFor}); on a change, the network takes the block's new stress and the client gets the step, for
 * the goggles. When the room warms up, nothing else: the network goes back to its stress, overloaded or not.
 */
public final class ColdStress {
    private ColdStress() {}

    /** The steps: none, -10 deg C, -20 deg C. */
    public static final int NONE = 0, COLD = 1, DEEP = 2;

    /** The tag key the client gets the step under (absent: none). */
    public static final String SYNC_KEY = "frostworks:cold_stress";

    /** What the stress is multiplied by at this step. */
    public static float factor(int step) {
        return step == DEEP ? 0.8f : step == COLD ? 0.9f : 1f;
    }

    /** The step for a block at this position now: its closed room's temperature (none out of any closed room). */
    public static int stepFor(ServerLevel level, KineticBlockEntity be) {
        if (Frostworks.MODID.equals(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getNamespace()))
            return NONE;
        return stepAt(ColdProcessing.closedRoomTemperature(level, be.getBlockPos()));
    }

    /** The step at this closed room temperature (NaN, no closed room: none). */
    public static int stepAt(float temperature) {
        if (temperature <= Overspeed.DEEP_COLD)
            return DEEP;
        return temperature <= -10 ? COLD : NONE; // NaN: false, none
    }

    /** The step inside a room of this status, at this temperature (closed: COLD or INSUFFICIENT). */
    public static int stepAt(com.frostworks.room.core.RoomStatus status, float temperature) {
        boolean closed = status == com.frostworks.room.core.RoomStatus.COLD
                || status == com.frostworks.room.core.RoomStatus.INSUFFICIENT;
        return closed ? stepAt(temperature) : NONE;
    }

    /** How much less stress at this step, in percent (the goggles). */
    public static int percent(int step) {
        return step == DEEP ? 20 : step == COLD ? 10 : 0;
    }

    /** Once a second per block, spread over the ticks by position. */
    public static boolean due(ServerLevel level, KineticBlockEntity be) {
        return Math.floorMod(level.getGameTime() + be.getBlockPos().asLong(), 20L) == 0;
    }

    /** The block's step changed (already stored): its network takes its new stress, the client the new step. */
    public static void apply(KineticBlockEntity be) {
        if (be.hasNetwork()) {
            KineticNetwork network = be.getOrCreateNetwork();
            network.updateStressFor(be, be.calculateStressApplied());
            PENDING.add(network);
        }
        be.sendData();
    }

    /**
     * Networks whose stress the cold changed this tick. Create sends a block's network figures to the client only with
     * that block's own data: the blocks of the network whose step did not change (a gearbox, a shaft out of the room)
     * kept showing the old stress in the goggles until broken and placed again (seen in game). Once a tick, every
     * block of these networks sends its data again ({@link #flush}).
     */
    private static final Set<KineticNetwork> PENDING = Collections.newSetFromMap(new WeakHashMap<>());

    /** Called once a tick (server): the blocks of the networks the cold changed send their network figures. */
    public static void flush() {
        if (PENDING.isEmpty())
            return;
        List<KineticNetwork> networks = new ArrayList<>(PENDING);
        PENDING.clear();
        for (KineticNetwork network : networks)
            for (KineticBlockEntity member : new ArrayList<>(network.members.keySet()))
                if (!member.isRemoved())
                    member.sendData();
    }
}
