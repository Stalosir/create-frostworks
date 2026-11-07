package com.frostworks.content.passage;

import com.frostworks.Frostworks;
import com.frostworks.registry.FwBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity.CasingType;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import com.frostworks.room.RoomManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.frostworks.network.BeltPassagePayload;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraft.world.level.chunk.LevelChunk;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Belt passage prototype: a Create belt segment encased by a cold wall (right click with a cold wall).
 * It stays Create's belt (same block, same block entity, it keeps moving items and saving), with Create's andesite
 * casing as its look for now, and a mark in the block entity's NeoForge data (saved with it, gone with it) that makes
 * rooms count it as a wall. No mixin: the right click is caught before Create's own.
 */
@Mod.EventBusSubscriber(modid = Frostworks.MODID)
public final class BeltPassage {
    private BeltPassage() {}

    public static final String KEY = Frostworks.MODID + ":belt_passage";

    /** Is the block at {@code pos} (of state {@code state}) a belt passage? Cheap for every other block. */
    public static boolean is(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof BeltBlock) || !state.getValue(BeltBlock.CASING))
            return false;
        return level.getBlockEntity(pos) instanceof BeltBlockEntity be && be.getPersistentData().getBoolean(KEY);
    }

    /** Encases the belt segment at {@code pos} as a passage (server side); false if there is no unencased belt. */
    public static boolean encase(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BeltBlock belt) || state.getValue(BeltBlock.CASING)
                || !(level.getBlockEntity(pos) instanceof BeltBlockEntity be))
            return false;
        // The mark first: the block update of the casing reaches the rooms with the passage already a wall
        be.getPersistentData().putBoolean(KEY, true);
        be.setCasingType(CasingType.ANDESITE);
        belt.updateCoverProperty(level, pos, level.getBlockState(pos));
        tellClients(level, pos, true);
        if (level instanceof ServerLevel server)
            marked(server).add(pos.asLong());
        return true;
    }

    /** Takes the passage mark off (the casing stays as it is). */
    public static void unmark(BeltBlockEntity be) {
        be.getPersistentData().remove(KEY);
        be.setChanged();
        if (be.getLevel() instanceof ServerLevel server) {
            marked(server).remove(be.getBlockPos().asLong());
            // No block changes (the casing stays), so the rooms are told the wall is gone
            RoomManager.get(server).onBlockChanged(be.getBlockPos(), be.getBlockState());
        }
        tellClients(be.getLevel(), be.getBlockPos(), false);
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BeltBlock) || !(level.getBlockEntity(pos) instanceof BeltBlockEntity be))
            return;
        ItemStack stack = event.getItemStack();

        if (stack.is(FwBlocks.COLD_WALL.get().asItem())) {
            if (state.getValue(BeltBlock.CASING) || event.getEntity().isShiftKeyDown())
                return; // already encased, or the player wants to place the wall: vanilla goes on
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (level.isClientSide)
                return;
            encase(level, pos);
            SoundType sound = FwBlocks.COLD_WALL.get().defaultBlockState().getSoundType(level, pos, event.getEntity());
            level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2,
                    sound.getPitch() * 0.8f);
            if (!event.getEntity().isCreative())
                stack.shrink(1);
            return;
        }

        if (level.isClientSide || !be.getPersistentData().getBoolean(KEY))
            return;
        // Create's own casings or the wrench (sneaking too: Create then dismantles the belt without a break event) take
        // the belt back: no passage any more, and the wall comes back in every case
        if (AllItems.WRENCH.isIn(stack) || AllBlocks.ANDESITE_CASING.isIn(stack) || AllBlocks.BRASS_CASING.isIn(stack)) {
            unmark(be);
            if (!event.getEntity().isCreative())
                event.getEntity().getInventory().placeItemBackInInventory(new ItemStack(FwBlocks.COLD_WALL.get()));
        }
    }

    /** The clients that see this segment learn it is (no longer) a passage: they draw its casing. */
    private static void tellClients(Level level, BlockPos pos, boolean passage) {
        if (level instanceof ServerLevel server)
            com.frostworks.network.FwNetwork.sendToTracking(server, new ChunkPos(pos), new BeltPassagePayload(pos, passage));
    }

    /** A player receives a chunk: the passages in it, for that player (the mark is not in Create's own sync). */
    @SubscribeEvent
    public static void onChunkSent(ChunkWatchEvent.Watch event) {
        for (BlockEntity be : event.getChunk().getBlockEntities().values())
            if (be instanceof BeltBlockEntity belt && belt.getPersistentData().getBoolean(KEY))
            {
                com.frostworks.network.FwNetwork.sendToPlayer(event.getPlayer(), new BeltPassagePayload(be.getBlockPos(), true));
                marked(event.getLevel()).add(be.getBlockPos().asLong());
            }
    }

    /**
     * The segment broken by hand gives its cold wall back (Create's belt drops only itself). Last in line: a break that
     * another mod cancels (a claim, a protection) never reaches it, so it never gives a wall for nothing.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level))
            return;
        // A creative player's break takes the whole belt without giving walls back
        if (event.getPlayer().isCreative() && event.getState().getBlock() instanceof BeltBlock)
            CREATIVE_BREAK.put(level, level.getGameTime());
        if (!is(level, event.getPos(), event.getState()))
            return;
        if (!event.getPlayer().isCreative())
            Block.popResource(level, event.getPos(), new ItemStack(FwBlocks.COLD_WALL.get()));
        marked(level).remove(event.getPos().asLong()); // its wall is given: not again when it goes
        tellClients(level, event.getPos(), false);
    }

    // ---- The walls of every passage of a belt that goes at once -----------------------------------

    /**
     * The marked segments of each level. Create takes a whole belt away when one segment goes (its other segments lose
     * their block entity first, without any event), so the mark is kept here too: a marked position that stops being a
     * passage, whatever took it (the rest of a broken belt, an explosion, overspeed), gives its cold wall back.
     */
    private static final Map<ServerLevel, LongOpenHashSet> MARKED = new WeakHashMap<>();
    /** Chunks loaded this tick: scanned on the next one, once their block entities are in the level. */
    private static final Map<ServerLevel, LongOpenHashSet> TO_SCAN = new WeakHashMap<>();
    private static final Map<ServerLevel, Long> CREATIVE_BREAK = new WeakHashMap<>();

    private static LongOpenHashSet marked(ServerLevel level) {
        return MARKED.computeIfAbsent(level, l -> new LongOpenHashSet());
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level)
            TO_SCAN.computeIfAbsent(level, l -> new LongOpenHashSet()).add(event.getChunk().getPos().toLong());
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !MARKED.containsKey(level))
            return;
        long chunk = event.getChunk().getPos().toLong();
        marked(level).removeIf(p -> ChunkPos.asLong(BlockPos.of(p)) == chunk);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level))
            return;
        LongOpenHashSet chunks = TO_SCAN.remove(level);
        if (chunks == null)
            return;
        for (long c : chunks) {
            ChunkPos pos = new ChunkPos(c);
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk != null)
                for (BlockEntity be : chunk.getBlockEntities().values())
                    if (be instanceof BeltBlockEntity belt && belt.getPersistentData().getBoolean(KEY))
                        marked(level).add(be.getBlockPos().asLong());
        }
    }

    /** A block changed: a marked segment that is no longer a passage drops its wall. */
    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !MARKED.containsKey(level))
            return;
        BlockPos pos = event.getPos();
        if (!marked(level).contains(pos.asLong()) || is(level, pos, event.getState()))
            return;
        marked(level).remove(pos.asLong());
        if (!Long.valueOf(level.getGameTime()).equals(CREATIVE_BREAK.get(level)))
            Block.popResource(level, pos, new ItemStack(FwBlocks.COLD_WALL.get()));
        tellClients(level, pos, false);
    }

    /** Tests: is this position in the list of marked segments? */
    public static boolean isMarked(ServerLevel level, BlockPos pos) {
        return MARKED.containsKey(level) && marked(level).contains(pos.asLong());
    }
}
