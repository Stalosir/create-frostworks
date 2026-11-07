package com.frostworks.gametest;

import com.frostworks.Frostworks;
import com.frostworks.content.door.InsulatedDoorBlock;
import com.frostworks.content.vent.VentBlock;
import com.frostworks.content.vent.VentBlockEntity;
import com.frostworks.overspeed.Overspeed;
import com.frostworks.registry.FwBlocks;
import com.frostworks.registry.FwFluids;
import com.frostworks.room.Room;
import com.frostworks.room.RoomManager;
import com.frostworks.room.core.RoomStatus;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The vent and the shaft passage, room numbers and closed rooms, in the real game.
 * <p>
 * The box: walls of cold wall 5 x 5 x 5 (inside 3 x 3 x 3) with, in its walls, a vent in the north wall blowing
 * south into the box, a door in the south wall and a shaft passage (axis X) in the east wall.
 */
public final class Modif3GameTests {
    private Modif3GameTests() {}

    private static final String SMALL = "empty_16x10x16";

    record Box(BlockPos corner) {
        BlockPos vent() { return corner.offset(2, 2, 0); }
        BlockPos doorLower() { return corner.offset(2, 1, 4); }
        BlockPos passage() { return corner.offset(4, 2, 2); }
        BlockPos interior() { return corner.offset(2, 2, 2); }
    }

    /** The shell only, with the three holes (vent, door, passage) left open. */
    static Box shell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Box box = new Box(helper.absolutePos(new BlockPos(4, 1, 4)));
        BlockPos c = box.corner();
        for (int x = 0; x < 5; x++)
            for (int y = 0; y < 5; y++)
                for (int z = 0; z < 5; z++) {
                    boolean inside = x > 0 && x < 4 && y > 0 && y < 4 && z > 0 && z < 4;
                    level.setBlock(c.offset(x, y, z), inside ? Blocks.AIR.defaultBlockState()
                            : FwBlocks.COLD_WALL.getDefaultState(), Block.UPDATE_ALL);
                }
        level.setBlock(box.vent(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(box.doorLower(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(box.doorLower().above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(box.passage(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        return box;
    }

    /** The full box: vent blowing in (free Frosium: 64 cells, enough for 27), door, passage. */
    static Box box(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Box box = shell(helper);
        level.setBlock(box.vent(), FwBlocks.VENT.getDefaultState().setValue(VentBlock.FACING, Direction.SOUTH),
                Block.UPDATE_ALL);
        if (level.getBlockEntity(box.vent()) instanceof VentBlockEntity vent)
            vent.setCreativeSupply(FwFluids.frosium());
        BlockState door = FwBlocks.INSULATED_DOOR.getDefaultState().setValue(InsulatedDoorBlock.FACING, Direction.NORTH);
        level.setBlock(box.doorLower(), door.setValue(InsulatedDoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_ALL);
        level.setBlock(box.doorLower().above(), door.setValue(InsulatedDoorBlock.HALF, DoubleBlockHalf.UPPER),
                Block.UPDATE_ALL);
        level.setBlock(box.passage(), FwBlocks.SHAFT_PASSAGE.getDefaultState()
                .setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X), Block.UPDATE_ALL);
        return box;
    }

    static Room room(GameTestHelper helper, Box box) {
        return RoomManager.get(helper.getLevel()).roomAt(box.interior());
    }

    // ---- C2: closed rooms ---------------------------------------------------------------------------------------

    /** A player sets the vent into the wall, from outside or from inside: it blows into the box either way. */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void ventPlacedFromEitherSideBlowsIntoTheRoom(GameTestHelper helper) {
        Box box = shell(helper);
        for (BlockPos p : new BlockPos[]{box.doorLower(), box.doorLower().above(), box.passage()})
            helper.getLevel().setBlock(p, FwBlocks.COLD_WALL.getDefaultState(), Block.UPDATE_ALL);
        BlockPos hole = box.vent();
        BlockPos side = hole.east(); // the player clicks the side of the hole, as when filling a gap in a wall
        Vec3 hit = Vec3.atCenterOf(side).add(-.5, 0, 0);
        for (boolean outside : new boolean[]{true, false}) {
            Player player = MockPlayers.survival(helper);
            Vec3 eye = Vec3.atCenterOf(hole).add(0, 0, outside ? -2.5 : 2);
            player.moveTo(eye.x, eye.y - player.getEyeHeight(), eye.z, outside ? 0 : 180, 0); // 0 = south
            ItemStack stack = new ItemStack(FwBlocks.VENT.asItem());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                    new BlockHitResult(hit, Direction.WEST, side, false));
            helper.assertTrue(context.getClickedPos().equals(hole), "clicked " + context.getClickedPos());
            BlockState placed = FwBlocks.VENT.get().getStateForPlacement(context);
            helper.assertTrue(placed != null && placed.getValue(VentBlock.FACING) == Direction.SOUTH,
                    (outside ? "from outside" : "from inside") + ": vent facing " + placed);
        }
        helper.succeed();
    }

    /**
     * The vent was set facing outside before the room was finished (seen in game): once the last
     * wall is placed, the vent turns towards the closed room and the room is found.
     */
    @GameTest(template = SMALL, timeoutTicks = 300)
    public static void ventFacingOutTurnsWhenTheRoomCloses(GameTestHelper helper) {
        Box box = shell(helper);
        ServerLevel level = helper.getLevel();
        level.setBlock(box.vent(), FwBlocks.VENT.getDefaultState().setValue(VentBlock.FACING, Direction.NORTH),
                Block.UPDATE_ALL);
        if (level.getBlockEntity(box.vent()) instanceof VentBlockEntity vent)
            vent.setCreativeSupply(FwFluids.frosium());
        helper.startSequence()
                .thenWaitUntil(() -> {
                    Room r = RoomManager.get(level).roomOfVent(box.vent());
                    helper.assertTrue(r != null && r.status() == RoomStatus.OPEN, "not OPEN yet");
                })
                .thenExecute(() -> {
                    for (BlockPos p : new BlockPos[]{box.doorLower(), box.doorLower().above(), box.passage()})
                        level.setBlock(p, FwBlocks.COLD_WALL.getDefaultState(), Block.UPDATE_ALL);
                })
                .thenWaitUntil(() -> {
                    BlockState vent = level.getBlockState(box.vent());
                    helper.assertTrue(vent.getValue(VentBlock.FACING) == Direction.SOUTH, "vent " + vent);
                    Room r = room(helper, box);
                    helper.assertTrue(r != null && r.status() == RoomStatus.COLD && r.volume == 27,
                            "room " + (r == null ? "none" : r.status() + " " + r.volume));
                })
                .thenSucceed();
    }

    /**
     * Our case: several roof vents placed from above, all blowing up into the sky. Each one turns towards
     * the closed room (only the first one did), they all belong to that one room, the goggles show one room on a vent
     * and the room on an edge block of the cube.
     */
    @GameTest(template = SMALL, timeoutTicks = 300)
    public static void roofVentsFacingUpAllTurnIntoTheRoom(GameTestHelper helper) {
        Box box = shell(helper);
        ServerLevel level = helper.getLevel();
        for (BlockPos p : new BlockPos[]{box.doorLower(), box.doorLower().above(), box.passage(), box.vent()})
            level.setBlock(p, FwBlocks.COLD_WALL.getDefaultState(), Block.UPDATE_ALL);
        BlockPos[] roof = {box.corner().offset(1, 4, 2), box.corner().offset(2, 4, 2), box.corner().offset(3, 4, 2)};
        // First a vent standing on the roof, blowing up: its back is the roof, it can only blow into the open air,
        // whose search is an OPEN room
        BlockPos onRoof = box.corner().offset(2, 5, 0);
        level.setBlock(onRoof, FwBlocks.VENT.getDefaultState().setValue(VentBlock.FACING, Direction.UP), Block.UPDATE_ALL);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    Room open = RoomManager.get(level).roomOfVent(onRoof);
                    helper.assertTrue(open != null && open.status() == RoomStatus.OPEN, "no open-air room yet");
                })
                .thenExecute(() -> {
                    for (BlockPos p : roof) {
                        level.setBlock(p, FwBlocks.VENT.getDefaultState().setValue(VentBlock.FACING, Direction.UP),
                                Block.UPDATE_ALL);
                        if (level.getBlockEntity(p) instanceof VentBlockEntity vent)
                            vent.setCreativeSupply(true);
                    }
                })
                .thenWaitUntil(() -> {
            RoomManager manager = RoomManager.get(level);
            Room inside = room(helper, box);
            helper.assertTrue(inside != null && inside.status() == RoomStatus.COLD && inside.ventCount() == 3,
                    "room " + (inside == null ? "none" : inside.status() + " vents " + inside.ventCount()));
            for (BlockPos p : roof) {
                BlockState vent = level.getBlockState(p);
                helper.assertTrue(vent.getValue(VentBlock.FACING) == Direction.DOWN, "vent " + p + " " + vent);
                helper.assertTrue(manager.roomOfVent(p) == inside, "vent " + p + " in another room");
                var info = com.frostworks.network.FwNetwork.describe(level, p, vent);
                helper.assertTrue(info.rooms().size() == 1, "goggles on a vent: " + info.rooms().size() + " rooms");
            }
            BlockPos edge = box.corner().offset(0, 4, 2); // edge of the cube: touches the inside by an edge only
            var info = com.frostworks.network.FwNetwork.describe(level, edge, level.getBlockState(edge));
            // the edge is a wall between the inside and the open air of the vent on the roof: both, inside first
            helper.assertTrue(!info.rooms().isEmpty() && info.rooms().get(0).id() == inside.id,
                    "goggles on an edge: " + info.rooms().stream().map(r -> r.id() + " " + r.status()).toList()
                            + ", inside " + inside.id);
                })
                .thenSucceed();
    }

    /** Cold walls, vent, door (both halves) and passage close the room: never OPEN; a real hole opens it. */
    @GameTest(template = SMALL, timeoutTicks = 300)
    public static void boxWithVentDoorAndPassageIsClosed(GameTestHelper helper) {
        Box box = box(helper);
        ServerLevel level = helper.getLevel();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    Room r = room(helper, box);
                    helper.assertTrue(r != null && r.status() == RoomStatus.COLD && r.volume == 27,
                            "room " + (r == null ? "none" : r.status() + " " + r.volume));
                })
                // a cold wall instead of the door: still closed
                .thenExecute(() -> {
                    level.setBlock(box.doorLower().above(), FwBlocks.COLD_WALL.getDefaultState(), Block.UPDATE_ALL);
                    level.setBlock(box.doorLower(), FwBlocks.COLD_WALL.getDefaultState(), Block.UPDATE_ALL);
                })
                .thenWaitUntil(() -> {
                    Room r = room(helper, box);
                    helper.assertTrue(r != null && r.status() == RoomStatus.COLD && r.volume == 27,
                            "without door: " + (r == null ? "none" : r.status()));
                })
                // a real hole where the passage was: OPEN
                .thenExecute(() -> level.setBlock(box.passage(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL))
                .thenWaitUntil(() -> {
                    Room r = RoomManager.get(level).roomOfVent(box.vent());
                    helper.assertTrue(r != null && r.status() == RoomStatus.OPEN,
                            "with a hole: " + (r == null ? "none" : r.status()));
                })
                .thenSucceed();
    }

    // ---- C1: room numbers ---------------------------------------------------------------------------------------

    @GameTest(template = SMALL, timeoutTicks = 600)
    public static void roomNumberIsKept(GameTestHelper helper) {
        Box box = box(helper);
        ServerLevel level = helper.getLevel();
        RoomManager manager = RoomManager.get(level);
        int[] id = {-1};
        Room[] seen = {null};
        // Other tests of the batch run searches of their own at the same time: the checks look at this room only
        // (the same Room object = not searched again), never at the level's search counter.
        java.util.function.Supplier<Room> settled = () -> {
            Room r = room(helper, box);
            return r != null && !r.isRestored() && !manager.isDirty(r) ? r : null;
        };
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(settled.get() != null, "no room yet"))
                .thenExecute(() -> {
                    seen[0] = settled.get();
                    id[0] = seen[0].id;
                })
                // Nothing changes for 5 s: the room is not searched again, same number
                .thenIdle(100)
                .thenExecute(() -> {
                    helper.assertTrue(room(helper, box) == seen[0], "searched again while nothing changed");
                    level.setBlock(box.interior().below(), FwBlocks.COLD_WALL.getDefaultState(), Block.UPDATE_ALL);
                })
                .thenWaitUntil(() -> {
                    Room r = settled.get();
                    helper.assertTrue(r != null && r != seen[0], "not searched again after the wall");
                })
                .thenExecute(() -> {
                    Room r = settled.get();
                    helper.assertTrue(r.volume == 26, "volume " + r.volume);
                    helper.assertTrue(r.id == id[0], "wall inside: number " + r.id + " instead of " + id[0]);
                    seen[0] = r;
                    manager.markDirty(r); // searched again on request
                })
                .thenWaitUntil(() -> {
                    Room r = settled.get();
                    helper.assertTrue(r != null && r != seen[0], "not searched again on request");
                })
                .thenExecute(() -> helper.assertTrue(settled.get().id == id[0],
                        "after a search on request: number " + settled.get().id + " instead of " + id[0]))
                .thenSucceed();
    }

    /** The symptom of C1: an OPEN room was searched again every 100 ticks, with a new number each time. */
    // own batch: an OPEN room reaches the neighbouring tests, whose walls would rightly make it searched again
    @GameTest(template = SMALL, timeoutTicks = 400, batch = "modif3_open_room")
    public static void openRoomIsNotSearchedAgainForNothing(GameTestHelper helper) {
        Box box = box(helper);
        ServerLevel level = helper.getLevel();
        RoomManager manager = RoomManager.get(level);
        level.setBlock(box.passage(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        int[] id = {-1};
        long[] scans = {-1};
        helper.startSequence()
                .thenWaitUntil(() -> {
                    Room r = manager.roomOfVent(box.vent());
                    helper.assertTrue(r != null && r.status() == RoomStatus.OPEN && !manager.isBusy(), "not OPEN yet");
                })
                .thenExecute(() -> {
                    id[0] = manager.roomOfVent(box.vent()).id;
                    scans[0] = manager.scansFrom(box.vent()); // its own room's searches (other tests search theirs)
                })
                .thenIdle(250)
                .thenExecute(() -> {
                    helper.assertTrue(manager.scansFrom(box.vent()) == scans[0],
                            (manager.scansFrom(box.vent()) - scans[0]) + " search(es) of an OPEN room with no change");
                    helper.assertTrue(manager.roomOfVent(box.vent()).id == id[0], "number changed");
                })
                .thenSucceed();
    }

    // ---- Part A: the vent's propeller turns only when the vent cools a closed room ----------------------------

    @GameTest(template = SMALL, timeoutTicks = 300)
    public static void ventBlowsOnlyIntoAClosedRoom(GameTestHelper helper) {
        Box box = box(helper);
        ServerLevel level = helper.getLevel();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(level.getBlockEntity(box.vent()) instanceof VentBlockEntity v
                        && v.isBlowing(), "not blowing in the closed box"))
                .thenExecute(() -> level.setBlock(box.passage(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL))
                .thenWaitUntil(() -> helper.assertTrue(level.getBlockEntity(box.vent()) instanceof VentBlockEntity v
                        && v.isActive() && !v.isBlowing(), "still blowing with a hole in the room"))
                .thenSucceed();
    }

    // ---- Part B: shaft passage ----------------------------------------------------------------------------------

    /** Right click on a Create shaft with a cold wall: the shaft becomes a passage on the same axis. */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void coldWallEncasesAShaft(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(pos, AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS,
                Direction.Axis.Z), Block.UPDATE_ALL);
        Player player = MockPlayers.survival(helper);
        ItemStack walls = new ItemStack(FwBlocks.COLD_WALL.asItem(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, walls);
        BlockState shaft = level.getBlockState(pos);
        boolean used = com.frostworks.platform.Mc.useItemOn(shaft, walls, level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        BlockState after = level.getBlockState(pos);
        helper.assertTrue(used, "not encased");
        helper.assertTrue(FwBlocks.SHAFT_PASSAGE.has(after), "block after: " + after);
        helper.assertTrue(after.getValue(RotatedPillarKineticBlock.AXIS) == Direction.Axis.Z, "axis " + after);
        helper.assertTrue(walls.getCount() == 1, "cold walls left: " + walls.getCount());
        helper.succeed();
    }

    /** It passes the rotation straight along its axis, adds no stress, and drops itself. */
    @GameTest(template = SMALL, timeoutTicks = 100)
    public static void passageTransmitsAndDropsItself(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos motor = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalBlock.FACING,
                Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(motor.east(), FwBlocks.SHAFT_PASSAGE.getDefaultState()
                .setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X), Block.UPDATE_ALL);
        level.setBlock(motor.east(2), AllBlocks.SHAFT.getDefaultState()
                .setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X), Block.UPDATE_ALL);
        if (level.getBlockEntity(motor) instanceof CreativeMotorBlockEntity be)
            be.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(64);
        helper.assertTrue(BlockStressValues.getImpact(FwBlocks.SHAFT_PASSAGE.get()) == 0, "stress impact");
        var drops = Block.getDrops(level.getBlockState(motor.east()), level, motor.east(),
                level.getBlockEntity(motor.east()));
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(FwBlocks.SHAFT_PASSAGE.asItem()), "drops " + drops);
        helper.succeedWhen(() -> helper.assertTrue(level.getBlockEntity(motor.east(2)) instanceof KineticBlockEntity s
                && Math.abs(s.getSpeed()) == 64, "the shaft after the passage does not turn at 64 rpm"));
    }

    /** the passage is in the wall, not in the room; it counts as in the COLD room it opens on. */
    @GameTest(template = SMALL, timeoutTicks = 300)
    public static void passageBelongsToTheColdRoomItOpensOn(GameTestHelper helper) {
        Box box = box(helper);
        ServerLevel level = helper.getLevel();
        helper.succeedWhen(() -> {
            Room r = room(helper, box);
            helper.assertTrue(r != null && r.status() == RoomStatus.COLD, "not COLD yet");
            helper.assertTrue(Overspeed.inColdRoom(level, box.passage()), "passage not in the COLD room");
            helper.assertTrue(!Overspeed.inColdRoom(level, box.passage().above()), "a plain wall counts as in it");
        });
    }
}
