package com.frostworks.gametest;

import com.frostworks.Frostworks;
import com.frostworks.content.door.InsulatedDoorBlock;
import com.frostworks.content.door.StripDoorBlockEntity;
import com.frostworks.overspeed.Overspeed;
import com.frostworks.overspeed.OverspeedRig;
import com.frostworks.registry.FwBlocks;
import com.frostworks.room.Room;
import com.frostworks.room.RoomManager;
import com.frostworks.room.core.RoomStatus;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/** Rooms kept through a restart (A), strips that swing only for a moving entity (B). */
public final class Modif4GameTests {
    private Modif4GameTests() {}

    private static final String SMALL = "empty_16x10x16";

    // ---- A: a COLD room at -10 deg C with a 320 rpm train survives a restart --------------------------------------

    /**
     * What is really tested: a restart IN MEMORY. The rooms are encoded into the bytes saved with the world, the room
     * manager and the overspeed rights are forgotten, the rooms are decoded back from those bytes, then the kinetic
     * network is propagated again from its motor (as when a chunk loads). The disk and a real reload of the world are
     * not part of it (the pilot does a real reload). Own batch: the restart forgets the rooms of the whole test level.
     */
    @GameTest(template = SMALL, timeoutTicks = 4000, batch = "modif4_restart")
    public static void coldRoomSurvivesARestart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OverspeedRig.Layout rig = OverspeedRig.buildInRoom(level, helper.absolutePos(new BlockPos(3, 1, 0)));
        float[] saved = new float[1];
        int[] id = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    Room r = RoomManager.get(level).roomAt(rig.interior());
                    helper.assertTrue(r != null && r.status() == RoomStatus.COLD, "not COLD yet");
                })
                .thenExecute(() -> OverspeedRig.setMotorSpeed(level, rig, 160))
                .thenWaitUntil(() -> helper.assertTrue(speed(level, rig.saw()) == 320, "saw not at 320 rpm"))
                .thenWaitUntil(() -> {
                    Room r = RoomManager.get(level).roomAt(rig.interior());
                    helper.assertTrue(r.temperature() <= -10f, "room at " + r.temperature());
                })
                .thenExecute(() -> {
                    Room r = RoomManager.get(level).roomAt(rig.interior());
                    saved[0] = r.temperature();
                    id[0] = r.id;
                    RoomManager.restartForTest(level);
                    Overspeed.clear();
                    // the very first question after the restart, as the overspeed mixin asks it
                    helper.assertTrue(Overspeed.inColdRoom(level, rig.largeCog()), "not COLD at the first question");
                    Room back = RoomManager.get(level).roomAt(rig.interior());
                    helper.assertTrue(back != null && back.isRestored() && back.id == id[0], "room not restored");
                    helper.assertTrue(Math.abs(back.temperature() - saved[0]) <= 0.5f,
                            "temperature " + back.temperature() + " instead of " + saved[0]);
                    // a chunk load propagates the network again: stop and start the motor
                    OverspeedRig.setMotorSpeed(level, rig, 0);
                })
                .thenIdle(2)
                .thenExecute(() -> OverspeedRig.setMotorSpeed(level, rig, 160))
                .thenIdle(1200) // 60 s
                .thenExecute(() -> {
                    helper.assertTrue(AllBlocks.LARGE_COGWHEEL.has(level.getBlockState(rig.largeCog())),
                            "the large cogwheel broke");
                    helper.assertTrue(speed(level, rig.saw()) == 320, "saw at " + speed(level, rig.saw()));
                    Room r = RoomManager.get(level).roomAt(rig.interior());
                    helper.assertTrue(r != null && !r.isRestored() && r.status() == RoomStatus.COLD,
                            "room after its search: " + (r == null ? "none" : r.status()));
                    helper.assertTrue(r.id == id[0], "number " + r.id + " instead of " + id[0]);
                    helper.assertTrue(r.temperature() <= saved[0] + 0.5f, "temperature " + r.temperature());
                    Frostworks.LOGGER.info("GAMETEST A restart in memory: saved {} deg C, room #{} after 60 s: {} deg C, "
                            + "large cogwheel intact, saw {} rpm", saved[0], r.id, r.temperature(), speed(level, rig.saw()));
                })
                .thenSucceed();
    }

    private static float speed(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof KineticBlockEntity be ? Math.abs(be.getSpeed()) : Float.NaN;
    }

    // ---- B: strips -------------------------------------------------------------------------------------------------

    private static StripDoorBlockEntity door(ServerLevel level, BlockPos low) {
        BlockState door = FwBlocks.INSULATED_DOOR.getDefaultState().setValue(InsulatedDoorBlock.FACING, Direction.NORTH);
        level.setBlock(low, door.setValue(InsulatedDoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_ALL);
        level.setBlock(low.above(), door.setValue(InsulatedDoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        return (StripDoorBlockEntity) level.getBlockEntity(low);
    }

    private static Villager villager(ServerLevel level, double x, double y, double z) {
        Villager v = EntityType.VILLAGER.create(level);
        v.setNoAi(true);
        v.moveTo(x, y, z, 0, 0);
        level.addFreshEntity(v);
        return v;
    }

    @GameTest(template = SMALL, timeoutTicks = 300)
    public static void stillEntityInTheDoorSwingsNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos low = helper.absolutePos(new BlockPos(5, 1, 5));
        StripDoorBlockEntity door = door(level, low);
        villager(level, low.getX() + .5, low.getY(), low.getZ() + .5); // standing in the opening
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(door.swingsSent() == 0, door.swingsSent() + " swing(s) for an entity standing still");
            helper.succeed();
        });
    }

    @GameTest(template = SMALL, timeoutTicks = 200)
    public static void walkingThroughTheDoorSwings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos low = helper.absolutePos(new BlockPos(5, 1, 5));
        StripDoorBlockEntity door = door(level, low);
        Villager v = villager(level, low.getX() + .5, low.getY(), low.getZ() + 2.5);
        for (int t = 1; t <= 40; t++)
            helper.runAfterDelay(t, () -> v.move(MoverType.SELF, new Vec3(0, -0.08, -0.16)));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(v.getZ() < low.getZ(), "the villager did not cross the door");
            helper.assertTrue(door.swingsSent() >= 1, "no swing for an entity walking through");
            Frostworks.LOGGER.info("GAMETEST B swings while crossing: {}", door.swingsSent());
            helper.succeed();
        });
    }
}
