package com.frostworks.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/** Server -> client: this belt segment is (or is no longer) a belt passage, so the client draws its casing. */
public record BeltPassagePayload(BlockPos pos, boolean passage) {
    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(passage);
    }

    public static BeltPassagePayload read(FriendlyByteBuf buf) {
        return new BeltPassagePayload(buf.readBlockPos(), buf.readBoolean());
    }
}
