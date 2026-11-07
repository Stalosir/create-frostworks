package com.frostworks.energy;

import com.frostworks.content.expander.ExpanderBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Energy balance of the loop blocks around a point: what entered (compressor and column stress), what came out
 * (expander: stress capacity + gas that left it), what is still held (buffers, reservoirs, fractions).
 * Create's pipes and pumps hold no fluid (a pipe network moves fluid straight from a source handler to the targets),
 * so the blocks below hold everything there is.
 */
public record LoopLedger(double in, double stressOut, double gasOut, double stock, int blocks) {

    public double out() {
        return stressOut + gasOut;
    }

    /** (returned + stock) / entered: 1 when nothing is lost and nothing is made out of nothing. */
    public double ratio() {
        return in <= 0 ? 1 : (out() + stock) / in;
    }

    /** Only returned / entered, without the stock. */
    public double returnedRatio() {
        return in <= 0 ? 0 : out() / in;
    }

    public static LoopLedger around(ServerLevel level, BlockPos center, int radius) {
        double in = 0, stressOut = 0, gasOut = 0, stock = 0;
        int blocks = 0;
        int r2 = radius * radius;
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++)
            for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null)
                    continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof Energy.Account account) || be.getBlockPos().distSqr(center) > r2)
                        continue;
                    blocks++;
                    in += account.energyIn();
                    stock += account.energyStock();
                    if (be instanceof ExpanderBlockEntity expander) {
                        stressOut += expander.stressOut();
                        gasOut += expander.gasOutEnergy();
                    } else {
                        stressOut += account.energyOut();
                    }
                }
            }
        return new LoopLedger(in, stressOut, gasOut, stock, blocks);
    }
}
