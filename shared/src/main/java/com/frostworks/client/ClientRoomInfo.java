package com.frostworks.client;

import org.jetbrains.annotations.Nullable;

import com.frostworks.network.RoomInfoPayload;
import com.frostworks.network.RoomQueryPayload;
import com.frostworks.room.RoomWalls;
import com.simibubi.create.content.equipment.goggles.GogglesItem;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Client cache of the room info for the looked-at block. While the player wears goggles and looks at a wall, door,
 * passage or vent, the client asks the server every {@value #REFRESH_TICKS} ticks (and at once when the target
 * changes); the overlay draws the last answer for that block.
 */
public final class ClientRoomInfo {
    private ClientRoomInfo() {}

    private static final int REFRESH_TICKS = 10;
    private static final int MAX_AGE_TICKS = 40;
    /** How long the last answer stays shown while the answer for a new target is on its way. */
    private static final int PENDING_TICKS = 10;

    @Nullable
    private static RoomInfoPayload last;
    private static long lastReceived;
    @Nullable
    private static BlockPos target;
    private static long lastQuery;
    /** The answer last shown, and when the target last changed. */
    @Nullable
    private static RoomInfoPayload shown;
    private static long targetChanged;
    private static long clientTicks;

    public static void receive(RoomInfoPayload payload) {
        last = payload;
        lastReceived = clientTicks;
    }

    public static void tick() {
        clientTicks++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            target = null;
            last = null;
            return;
        }
        BlockPos looked = lookedAtWall(mc);
        if (looked == null || !GogglesItem.isWearingGoggles(mc.player)) {
            target = null;
            return;
        }
        if (!looked.equals(target) || clientTicks - lastQuery >= REFRESH_TICKS) {
            if (!looked.equals(target))
                targetChanged = clientTicks;
            target = looked;
            lastQuery = clientTicks;
            com.frostworks.network.FwNetwork.sendToServer(new RoomQueryPayload(looked));
        }
    }

    @Nullable
    static BlockPos lookedAtWall(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK)
            return null;
        BlockPos pos = blockHit.getBlockPos();
        // A belt passage too (its mark is not in the client's block entity: the list the server sends)
        return RoomWalls.isWall(mc.level.getBlockState(pos)) || BeltPassageModel.isListed(pos) ? pos : null;
    }

    /**
     * The answer for the current target, or null if there is none yet (or it is stale). While the
     * answer for a new target is on its way (a few ticks), the last one shown stays: the eye moving along the walls of
     * a room no longer blanks the tooltip for those ticks (which restarted its fade).
     */
    @Nullable
    static RoomInfoPayload current() {
        if (target == null || clientTicks - lastReceived > MAX_AGE_TICKS)
            return null;
        if (last != null && last.pos().equals(target))
            return shown = last;
        return clientTicks - targetChanged <= PENDING_TICKS ? shown : null;
    }

    /** What a tooltip is about: its rooms (or, outside any room, the block itself). Same key, no new fade. */
    static Object subject(RoomInfoPayload info) {
        if (info.rooms().isEmpty())
            return info.pos();
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        for (RoomInfoPayload.RoomView room : info.rooms())
            ids.add(room.id());
        java.util.Collections.sort(ids);
        return ids;
    }
}
