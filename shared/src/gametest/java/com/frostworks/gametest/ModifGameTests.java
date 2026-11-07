package com.frostworks.gametest;

import com.frostworks.Frostworks;
import com.frostworks.registry.FwBlocks;
import com.frostworks.room.Room;
import com.frostworks.room.RoomManager;
import com.frostworks.room.TestRoomBuilder;
import com.frostworks.room.core.RoomStatus;
import com.simibubi.create.AllItems;

import com.frostworks.content.door.InsulatedDoorBlock;
import com.frostworks.content.door.StripDoorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;

/** The single cold wall and the strip curtain door. */
public final class ModifGameTests {
    private ModifGameTests() {}

    private static final String SMALL = "empty_16x10x16";

    // ---- Part A: cold wall ------------------------------------------------------------------------------------

    @GameTest(template = SMALL, timeoutTicks = 200)
    public static void roomOfColdWallsIsCold(GameTestHelper helper) {
        BlockPos corner = helper.absolutePos(new BlockPos(2, 1, 2));
        TestRoomBuilder.build(helper.getLevel(), corner, 6);
        helper.assertTrue(FwBlocks.COLD_WALL.has(helper.getLevel().getBlockState(corner.offset(7, 2, 2))),
                "the test room is not built with cold_wall");
        helper.assertTrue(FwBlocks.COLD_WALL.has(helper.getLevel().getBlockState(corner)), "corner");
        helper.succeedWhen(() -> {
            Room room = RoomManager.get(helper.getLevel()).roomAt(corner.offset(2, 1, 2));
            helper.assertTrue(room != null && room.status() == RoomStatus.COLD && room.volume == 96,
                    "room " + (room == null ? "none" : room.status() + " " + room.volume));
        });
    }

    /** The recipe is loaded, is Create's item application, and turns stone + iron sheet into a cold wall. */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void coldWallRecipeIsLoaded(GameTestHelper helper) {
        var id = Frostworks.id("item_application/cold_wall");
        Recipe<?> recipe = com.frostworks.platform.Mc.recipe(helper.getLevel(), id).orElse(null);
        helper.assertTrue(recipe != null, "recipe " + id + " not loaded");
        String type = String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()));
        helper.assertTrue(type.equals("create:item_application"), "type " + type);
        var ingredients = recipe.getIngredients();
        helper.assertTrue(ingredients.size() == 2, "ingredients " + ingredients.size());
        helper.assertTrue(ingredients.get(0).test(new ItemStack(Items.STONE)), "first ingredient is not stone");
        helper.assertTrue(!ingredients.get(0).test(new ItemStack(Items.COBBLESTONE)), "cobblestone accepted");
        // The cold iron sheet, and no longer the plain one
        helper.assertTrue(ingredients.get(1).test(new ItemStack(com.frostworks.registry.FwItems.COLD_IRON_SHEET.get())),
                "second ingredient is not a cold iron sheet");
        helper.assertTrue(!ingredients.get(1).test(AllItems.IRON_SHEET.asStack()), "plain iron sheet still accepted");
        ItemStack result = recipe.getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(result.is(FwBlocks.COLD_WALL.asItem()) && result.getCount() == 1, "result " + result);
        helper.succeed();
    }

    // ---- Part B: strip curtain door ---------------------------------------------------------------------------

    /** Places the door (2 blocks) facing north, lower half at {@code low}. */
    private static void placeDoor(ServerLevel level, BlockPos low) {
        BlockState door = FwBlocks.INSULATED_DOOR.getDefaultState().setValue(InsulatedDoorBlock.FACING, Direction.NORTH);
        level.setBlock(low, door.setValue(InsulatedDoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_ALL);
        level.setBlock(low.above(), door.setValue(InsulatedDoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /**
     * Pushes an entity north through the door, like walking: {@code move(SELF)} applies the collisions.
     *
     * @return true if it ended on the other side
     */
    private static boolean walkThrough(Entity e, BlockPos door, double step) {
        for (int i = 0; i < 40; i++)
            e.move(MoverType.SELF, new Vec3(0, -0.08, -step));
        return e.getZ() < door.getZ() - 0.4;
    }

    @GameTest(template = SMALL, timeoutTicks = 400)
    public static void roomStaysColdWhileCrossed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos corner = helper.absolutePos(new BlockPos(2, 1, 2));
        TestRoomBuilder.Layout layout = TestRoomBuilder.build(level, corner, 6);
        BlockPos door = layout.doorLower();
        Zombie[] zombie = {null};
        long[] crossedAt = {-1};
        helper.startSequence()
                .thenWaitUntil(() -> {
                    Room r = RoomManager.get(level).roomAt(layout.interior());
                    helper.assertTrue(r != null && r.status() == RoomStatus.COLD, "not COLD yet");
                })
                .thenExecute(() -> {
                    zombie[0] = EntityType.ZOMBIE.create(level);
                    zombie[0].setNoAi(true);
                    zombie[0].moveTo(door.getX() + 0.5, door.getY(), door.getZ() + 1.5, 180, 0);
                    level.addFreshEntity(zombie[0]);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(walkThrough(zombie[0], door, 0.15), "the zombie did not get through: z = "
                            + zombie[0].getZ() + ", door z = " + door.getZ());
                    crossedAt[0] = helper.getTick();
                    StripDoorBlockEntity be = (StripDoorBlockEntity) level.getBlockEntity(door);
                    helper.assertTrue(level.getGameTime() - be.lastSwingSent() < 60, "the strips were not triggered");
                })
                .thenIdle(40) // well over the room rescan delay
                .thenExecute(() -> {
                    Room r = RoomManager.get(level).roomAt(layout.interior());
                    helper.assertTrue(r != null && r.status() == RoomStatus.COLD,
                            "room after the crossing: " + (r == null ? "none" : r.status()));
                    zombie[0].discard();
                })
                .thenSucceed();
    }

    /** Who fits through the frame: the collision is the two posts (opening 12.05 px = 0.753 block), no lintel. */
    @GameTest(template = SMALL, timeoutTicks = 100)
    public static void creaturesWalkThrough(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos door = helper.absolutePos(new BlockPos(6, 0, 6));
        placeDoor(level, door);
        StringBuilder report = new StringBuilder();
        Vec3 start = new Vec3(door.getX() + 0.5, door.getY(), door.getZ() + 1.6);

        Player walker = MockPlayers.survival(helper);
        walker.moveTo(start);
        boolean walk = walkThrough(walker, door, 0.2);
        Player sprinter = MockPlayers.survival(helper);
        sprinter.moveTo(start);
        boolean sprint = walkThrough(sprinter, door, 0.28);
        Player croucher = MockPlayers.survival(helper);
        croucher.setPose(Pose.CROUCHING);
        croucher.refreshDimensions();
        croucher.moveTo(start);
        boolean crouch = walkThrough(croucher, door, 0.08);
        report.append("player walking ").append(walk).append(", sprinting ").append(sprint).append(", crouching ")
                .append(crouch);

        boolean zombie = false, villager = false, cow = true;
        for (EntityType<?> type : new EntityType<?>[]{EntityType.ZOMBIE, EntityType.VILLAGER, EntityType.COW}) {
            Entity e = type.create(level);
            if (e instanceof Mob mob)
                mob.setNoAi(true);
            e.moveTo(start);
            level.addFreshEntity(e);
            boolean through = walkThrough(e, door, 0.15);
            report.append(", ").append(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath()).append(" (")
                    .append(e.getBbWidth()).append(" x ").append(e.getBbHeight()).append(") ").append(through);
            if (type == EntityType.ZOMBIE)
                zombie = through;
            else if (type == EntityType.VILLAGER)
                villager = through;
            else
                cow = through;
            e.discard();
        }
        Frostworks.LOGGER.info("GAMETEST MODIF1 through the strip door: {}", report);
        helper.assertTrue(walk && sprint && crouch, "player blocked: " + report);
        helper.assertTrue(zombie && villager, "normal-size creature blocked: " + report);
        helper.assertTrue(!cow, "a cow (0.9 wide) should not fit through a 0.75 opening: " + report);
        helper.succeed();
    }

    /** Informative: does a zombie find its way through the door by itself (pathfinding)? Logged, not required. */
    @GameTest(template = SMALL, timeoutTicks = 300, required = false)
    public static void zombiePathfindsThroughDoor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos door = helper.absolutePos(new BlockPos(6, 0, 6));
        // a wall across the template, the door in the middle
        for (int x = 1; x <= 14; x++)
            for (int y = 0; y <= 2; y++)
                if (x != 6 || y == 2)
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, 6)), FwBlocks.COLD_WALL.getDefaultState(),
                            Block.UPDATE_ALL);
        placeDoor(level, door);
        Zombie zombie = EntityType.ZOMBIE.create(level);
        zombie.moveTo(door.getX() + 0.5, door.getY(), door.getZ() + 4.5, 180, 0);
        level.addFreshEntity(zombie);
        Vec3 target = new Vec3(door.getX() + 0.5, door.getY(), door.getZ() - 4.5);
        helper.onEachTick(() -> zombie.getNavigation().moveTo(target.x, target.y, target.z, 1.0));
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.getZ() < door.getZ() - 0.5, "zombie at z " + zombie.getZ());
            Frostworks.LOGGER.info("GAMETEST MODIF1 zombie pathfinding: went through the door after {} ticks",
                    helper.getTick());
        });
    }

    @GameTest(template = SMALL, timeoutTicks = 300)
    public static void removingTheDoorOpensTheRoom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestRoomBuilder.Layout layout = TestRoomBuilder.build(level, helper.absolutePos(new BlockPos(2, 1, 2)), 6);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    Room r = RoomManager.get(level).roomAt(layout.interior());
                    helper.assertTrue(r != null && r.status() == RoomStatus.COLD, "not COLD yet");
                })
                .thenExecute(() -> level.setBlock(layout.doorLower(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL))
                .thenExecute(() -> helper.assertTrue(level.getBlockState(layout.doorLower().above()).isAir(),
                        "the upper half should go with the lower one"))
                .thenWaitUntil(() -> {
                    Room r = RoomManager.get(level).roomAt(layout.interior());
                    helper.assertTrue(r != null && r.status() == RoomStatus.OPEN,
                            "room " + (r == null ? "none" : r.status()));
                })
                .thenSucceed();
    }
}
