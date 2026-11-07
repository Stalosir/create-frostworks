package com.frostworks.pilot;

import com.frostworks.Frostworks;

import java.lang.reflect.Method;

import com.frostworks.content.compressor.CompressorBlock;
import com.frostworks.content.vent.VentBlock;
import com.frostworks.registry.FwBlocks;
import com.frostworks.registry.FwCreativeTab;
import com.simibubi.create.AllBlocks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** TEMPORARY client pilot (never committed): builds a scene, takes screenshots, quits. */
@EventBusSubscriber(modid = "frostworks_pilot", value = Dist.CLIENT)
public final class ClientPilot {
    private static int titleTicks;
    private static boolean created;
    private static int t;
    static Vec3 lookTarget;
    private static net.minecraft.world.entity.Entity pushed;
    private static int placeStep;

    static void cmd(Minecraft mc, String c) {
        MinecraftServer s = mc.getSingleplayerServer();
        s.execute(() -> s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), c));
    }

    private static void playerCmd(Minecraft mc, String c) {
        mc.player.connection.sendCommand(c);
    }

    static void capture(Minecraft mc, String name) {
        mc.gui.getChat().clearMessages(false);
        mc.getToasts().clear();
        System.out.println("PILOT capture " + name);
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> {});
    }

    static void server(Minecraft mc, java.util.function.Consumer<ServerLevel> action) {
        MinecraftServer s = mc.getSingleplayerServer();
        s.execute(() -> action.accept(s.overworld()));
    }

    private static final long DEADLINE_MS = 360_000;
    private static volatile String stage = "start";
    private static Thread watchdog;

    /** Stops the client by itself if the scenario is not over in time, with a report of where it is stuck. */
    private static void startWatchdog() {
        watchdog = new Thread(() -> {
            try {
                Thread.sleep(DEADLINE_MS);
            } catch (InterruptedException e) {
                return;
            }
            StringBuilder sb = new StringBuilder("PILOT WATCHDOG: not finished after " + DEADLINE_MS / 1000
                    + " s, stage=" + stage + ", t=" + t + ", screen=" + Minecraft.getInstance().screen + '\n');
            Thread.getAllStackTraces().forEach((th, st) -> {
                if (!th.getName().contains("Render") && !th.getName().contains("Server"))
                    return;
                sb.append("--- ").append(th.getName()).append(" ").append(th.getState()).append('\n');
                for (StackTraceElement e : st)
                    sb.append("    at ").append(e).append('\n');
            });
            System.out.println(sb);
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of("pilot-watchdog.txt"), sb);
            } catch (java.io.IOException ignored) {
            }
            Runtime.getRuntime().halt(3);
        }, "pilot-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (watchdog == null)
            startWatchdog();
        if (!created && mc.screen instanceof TitleScreen) {
            titleTicks++;
            if (titleTicks == 1) {
                // the CurseForge captures are in English
                String language = "vitrine".equals(System.getProperty("frostworks.pilot.scenario")) ? "en_us" : "fr_fr";
                mc.getLanguageManager().setSelected(language);
                mc.options.languageCode = language;
                mc.reloadResourcePacks();
            }
            // Wait for the language reload to finish before creating the world
            if (titleTicks >= 60 && mc.getOverlay() == null) {
                stage = "creating world";
                created = true;
                // the showcase is a normal world from a seed, named for our client; every other scenario a flat one
                String scenario = System.getProperty("frostworks.pilot.scenario");
                boolean showcase = "showcase".equals(scenario) || "vitrine".equals(scenario);
                String name = "vitrine".equals(scenario) ? "vitrine" : showcase ? Showcase.WORLD_NAME : "fwpilot";
                LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL,
                        true, new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(name, settings,
                        new WorldOptions(showcase ? Showcase.SEED : 42L, showcase, false),
                        ra -> ra.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(showcase ? WorldPresets.NORMAL : WorldPresets.FLAT).value()
                                .createWorldDimensions(), mc.screen);
            }
            return;
        }
        // restart scenario: back on the title screen after leaving the world, open the same world again
        if (reloadStage == 1 && mc.player == null && mc.getSingleplayerServer() == null
                && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            reloadStage = 2;
            stage = "reopening world " + levelId;
            System.out.println("PILOT reopening world " + levelId);
            mc.createWorldOpenFlows().openWorld(levelId, () -> System.out.println("PILOT reopening cancelled"));
            return;
        }
        if (mc.player == null || mc.level == null)
            return;
        t++;
        stage = "in world";
        if (lookTarget != null)
            mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, lookTarget);

        if (t == 20) {
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1600, 900);
            mc.options.guiScale().set(3);
            mc.resizeDisplay();
            for (String c : new String[]{"gamemode creative @a", "time set 6000", "weather clear",
                    "gamerule doDaylightCycle false", "gamerule doMobSpawning false", "effect give @a night_vision infinite 0 true",
                    "item replace entity @a armor.head with create:goggles",
                    "tp @a 0.5 -60 0.5 0 0"})
                cmd(mc, c);
        }
        int end = switch (System.getProperty("frostworks.pilot.scenario", "t1")) {
            case "walls" -> walls(mc);
            case "loop" -> loop(mc);
            case "door" -> door(mc);
            case "tint" -> tint(mc);
            case "display" -> display(mc);
            case "rooms" -> rooms(mc);
            case "modif3" -> modif3(mc);
            case "modif5" -> modif5(mc);
            case "infusion" -> infusion(mc);
            case "jei" -> jei(mc);
            case "ponder" -> ponder(mc);
            case "belt" -> belt(mc);
            case "schematic" -> schematic(mc);
            case "goggles" -> goggles(mc);
            case "machines" -> machines(mc);
            case "modlist" -> modlist(mc);
            case "creative" -> creative(mc);
            case "ores" -> ores(mc);
            case "raws" -> raws(mc);
            case "tanks" -> tanks(mc);
            case "showcase" -> Showcase.run(mc, t);
            case "vitrine" -> Vitrine.run(mc, t);
            case "restart" -> restart(mc);
            default -> t1(mc);
        };
        if (t == end) {
            mc.options.languageCode = "en_us";
            mc.options.guiScale().set(0);
            mc.options.save();
            stage = "stopping";
            watchdog.interrupt();
            mc.stop();
        }
    }

    /** Connected cold walls: flat 3x3, wall with a hole, corner. */
    private static int walls(Minecraft mc) {
        if (t == 30)
            for (String c : new String[]{
                    "fill 18 -61 -6 40 -50 12 air",
                    // flat 3 x 3
                    "fill 20 -60 5 22 -58 5 frostworks:cold_wall",
                    // 5 x 3 with a hole in the middle
                    "fill 24 -60 5 28 -58 5 frostworks:cold_wall",
                    "setblock 26 -59 5 air",
                    // corner (L shape)
                    "fill 30 -60 5 32 -58 5 frostworks:cold_wall",
                    "fill 30 -60 5 30 -58 7 frostworks:cold_wall"})
                cmd(mc, c);
        if (t == 50) {
            playerCmd(mc, "tp @s 24.5 -59 -2.5");
            lookTarget = new Vec3(24.5, -59, 5);
        }
        if (t == 90)
            capture(mc, "murs_plat_3x3_et_trou");
        if (t == 100) {
            playerCmd(mc, "tp @s 34.5 -59 -1.5");
            lookTarget = new Vec3(33.5, -59, 5);
        }
        if (t == 140)
            capture(mc, "murs_angle");
        if (t == 150) { // close-up on the hole, to see the inner borders
            playerCmd(mc, "tp @s 26.5 -59 3");
            lookTarget = new Vec3(26.5, -58.5, 5);
        }
        if (t == 190)
            capture(mc, "murs_trou_gros_plan");
        if (t == 200) { // corner seen at 45 degrees
            playerCmd(mc, "tp @s 27.5 -58 1.5");
            lookTarget = new Vec3(30.5, -59, 5.5);
        }
        if (t == 240)
            capture(mc, "murs_angle_gros_plan");
        // ---- strip curtain door: a cold wall 5 x 3 at z = 12 with the door in the middle (x 22) ----
        if (t == 250) {
            for (String c : new String[]{
                    "fill 20 -60 12 24 -58 12 frostworks:cold_wall",
                    "fill 22 -60 12 22 -59 12 air"})
                cmd(mc, c);
            server(mc, level -> {
                BlockPos low = new BlockPos(22, -60, 12);
                var door = com.frostworks.registry.FwBlocks.INSULATED_DOOR.getDefaultState()
                        .setValue(com.frostworks.content.door.InsulatedDoorBlock.FACING, Direction.NORTH);
                level.setBlock(low, door.setValue(com.frostworks.content.door.InsulatedDoorBlock.HALF,
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER), Block.UPDATE_ALL);
                level.setBlock(low.above(), door.setValue(com.frostworks.content.door.InsulatedDoorBlock.HALF,
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
            });
        }
        if (t == 260) {
            playerCmd(mc, "tp @s 22.5 -60 8.5");
            lookTarget = new Vec3(22.5, -59.0, 12.5);
        }
        if (t == 300)
            capture(mc, "porte_repos_face");
        if (t == 305) { // three-quarter view
            playerCmd(mc, "tp @s 24.8 -59.4 9.6");
            lookTarget = new Vec3(22.5, -59.2, 12.4);
        }
        if (t == 340)
            capture(mc, "porte_repos_biais");
        if (t == 341) // a villager (no AI, zombie-sized) pushed through the door, from behind the wall towards the player
            server(mc, level -> {
                var zombie = net.minecraft.world.entity.EntityType.VILLAGER.create(level); // peaceful world: no zombie
                zombie.setNoAi(true);
                zombie.setSilent(true);
                zombie.moveTo(22.5, -60, 14.5, 0, 0);
                level.addFreshEntity(zombie);
                pushed = zombie;
            });
        if (t > 342 && t < 380 && pushed != null)
            server(mc, level -> pushed.move(net.minecraft.world.entity.MoverType.SELF,
                    new Vec3(0, -0.08, -0.16)));
        if (t == 355)
            capture(mc, "porte_balancement_1");
        if (t == 359)
            capture(mc, "porte_balancement_2");
        if (t == 366)
            capture(mc, "porte_balancement_3");
        if (t == 400)
            capture(mc, "porte_apres_passage");
        return 410;
    }

    private static void placeDoor(Minecraft mc, BlockPos low) {
        server(mc, level -> {
            var door = com.frostworks.registry.FwBlocks.INSULATED_DOOR.getDefaultState()
                    .setValue(com.frostworks.content.door.InsulatedDoorBlock.FACING, Direction.NORTH);
            level.setBlock(low, door.setValue(com.frostworks.content.door.InsulatedDoorBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER), Block.UPDATE_ALL);
            level.setBlock(low.above(), door.setValue(com.frostworks.content.door.InsulatedDoorBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        });
    }

    /** A villager (no AI, zombie-sized) behind the door, pushed through it towards the north on the next ticks. */
    private static void spawnPushed(Minecraft mc) {
        server(mc, level -> {
            if (pushed != null)
                pushed.discard();
            var villager = net.minecraft.world.entity.EntityType.VILLAGER.create(level); // peaceful: no zombie
            villager.setNoAi(true);
            villager.setSilent(true);
            villager.moveTo(22.5, -60, 14.5, 0, 0);
            level.addFreshEntity(villager);
            pushed = villager;
        });
    }

    private static void push(Minecraft mc) {
        if (pushed != null)
            server(mc, level -> pushed.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0, -0.08, -0.16)));
    }

    /** Takes away the coloured columns (x 20 to 24) at z. */
    /**
     * The cold wall tint: the 16 colours as 3 x 3 panels (none first), a lone wall, a gray structure against a red
     * one, a blue room seen from inside and from outside, everything from afar (the mipmaps); then a real right click
     * with a red dye in survival: one dye used, the whole L red, the same dye again does nothing.
     */
    private static int tint(Minecraft mc) {
        String[] tints = {"none", "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "light_gray",
                "cyan", "purple", "blue", "brown", "green", "red", "black"};
        if (t == 30) {
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            cmd(mc, "gamerule sendCommandFeedback false");
            cmd(mc, "item replace entity @a armor.head with air");
            cmd(mc, "fill 14 -60 2 90 -48 30 air");
            for (int i = 0; i < tints.length; i++) {
                int x = 20 + (i % 8) * 4, y = i < 8 ? -56 : -60;
                cmd(mc, "fill " + x + " " + y + " 12 " + (x + 2) + " " + (y + 2) + " 12 frostworks:cold_wall[tint=" + tints[i] + "]");
            }
            cmd(mc, "setblock 54 -59 12 frostworks:cold_wall[tint=red]"); // a lone wall
            cmd(mc, "fill 58 -60 12 60 -58 12 frostworks:cold_wall[tint=none]"); // gray against red
            cmd(mc, "fill 61 -60 12 63 -58 12 frostworks:cold_wall[tint=red]");
            // a blue room, lit inside
            cmd(mc, "fill 70 -60 10 76 -55 16 frostworks:cold_wall[tint=blue] hollow");
            cmd(mc, "setblock 73 -59 13 minecraft:sea_lantern");
            // the L for the real click
            cmd(mc, "fill 40 -60 20 42 -60 20 frostworks:cold_wall");
            cmd(mc, "fill 42 -59 20 42 -58 20 frostworks:cold_wall");
        }
        if (t == 60) {
            playerCmd(mc, "tp @s 34 -56 2");
            lookTarget = new Vec3(34, -56.5, 12);
        }
        if (t == 100)
            capture(mc, "teinte_16_couleurs");
        if (t == 105) { // afar: the mipmaps
            playerCmd(mc, "tp @s 34 -54 -30");
            lookTarget = new Vec3(34, -56.5, 12);
        }
        if (t == 140)
            capture(mc, "teinte_loin");
        if (t == 145) { // close, at an angle: the frame lines and the layer's edges
            playerCmd(mc, "tp @s 57.5 -59 9.5");
            lookTarget = new Vec3(60.5, -59, 12);
        }
        if (t == 180)
            capture(mc, "teinte_gris_contre_rouge");
        if (t == 185) {
            playerCmd(mc, "tp @s 54.5 -59 10");
            lookTarget = new Vec3(54.5, -58.5, 12);
        }
        if (t == 215)
            capture(mc, "teinte_mur_seul");
        if (t == 220) { // inside the blue room
            playerCmd(mc, "tp @s 71.5 -59 11.5");
            lookTarget = new Vec3(75, -57, 16);
        }
        if (t == 255)
            capture(mc, "teinte_salle_interieur");
        if (t == 260) { // outside, grazing
            playerCmd(mc, "tp @s 68 -58 8");
            lookTarget = new Vec3(76, -57.5, 10);
        }
        if (t == 295)
            capture(mc, "teinte_salle_exterieur");
        // ---- the real click, in survival ----
        if (t == 300) {
            cmd(mc, "gamemode survival @a");
            cmd(mc, "clear @a");
            cmd(mc, "give @a minecraft:red_dye 2");
            cmd(mc, "tp @a 41 -60 17");
            lookTarget = new Vec3(41.5, -59.5, 20);
        }
        if (t == 330 || t == 345) {
            mc.player.getInventory().selected = 0;
            var hit = new net.minecraft.world.phys.BlockHitResult(new Vec3(41.5, -59.5, 20), net.minecraft.core.Direction.NORTH,
                    new BlockPos(41, -60, 20), false);
            mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        }
        if (t == 340 || t == 355)
            server(mc, level -> {
                var player = level.getServer().getPlayerList().getPlayers().get(0);
                StringBuilder sb = new StringBuilder("PILOT tint click " + (t == 340 ? "1" : "2") + ": dyes "
                        + player.getInventory().countItem(net.minecraft.world.item.Items.RED_DYE));
                for (BlockPos p : new BlockPos[]{new BlockPos(40, -60, 20), new BlockPos(41, -60, 20),
                        new BlockPos(42, -60, 20), new BlockPos(42, -59, 20), new BlockPos(42, -58, 20)})
                    sb.append(' ').append(p.toShortString()).append('=')
                            .append(level.getBlockState(p).getValue(com.frostworks.content.wall.ColdWallBlock.TINT).getSerializedName());
                System.out.println(sb);
            });
        if (t == 360) {
            capture(mc, "teinte_clic_survie");
            cmd(mc, "give @a minecraft:light_blue_dye 1");
            cmd(mc, "fill 36 -60 30 52 -54 30 frostworks:cold_wall");
            cmd(mc, "tp @a 44.5 -58 26.5"); // within reach (4.5 in survival)
            lookTarget = new Vec3(44.5, -57, 30);
        }
        // the wave: a 17 x 7 panel clicked in its middle, seen as it spreads (one ring every 2 ticks)
        if (t == 390) {
            mc.player.getInventory().selected = 1;
            var hit = new net.minecraft.world.phys.BlockHitResult(new Vec3(44.5, -56.5, 30), net.minecraft.core.Direction.NORTH,
                    new BlockPos(44, -57, 30), false);
            mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        }
        if (t == 393)
            capture(mc, "teinte_vague_1");
        if (t == 397)
            capture(mc, "teinte_vague_2");
        if (t == 403)
            capture(mc, "teinte_vague_3");
        if (t == 420) {
            capture(mc, "teinte_vague_fin");
            cmd(mc, "gamemode creative @a");
        }
        return 430;
    }

    /**
     * The Display Link: a cold room (6 vents), a Display Link on four of its vents, and on its roof a sign
     * (temperature), a display board (status), nixie tubes (max speed) and a second sign (active vents); close, from
     * afar, then with a vent taken away (the room no longer cold).
     */
    private static int display(Minecraft mc) {
        // link on a vent -> {target, source}
        int[][] links = {{101, 100, 13}, {102, 102, 13}, {105, 104, 13}, {106, 106, 15}};
        String[] sources = {"room_temperature", "room_status", "room_max_speed", "room_vents"};
        if (t == 30) {
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            cmd(mc, "gamerule sendCommandFeedback false");
            cmd(mc, "item replace entity @a armor.head with air");
            cmd(mc, "fill 96 -60 2 112 -50 20 air");
            server(mc, level -> com.frostworks.room.TestRoomBuilder.build(level, new BlockPos(100, -60, 10), 6));
        }
        if (t == 40) {
            cmd(mc, "setblock 100 -54 13 minecraft:oak_sign[rotation=8]");
            cmd(mc, "fill 101 -54 13 103 -54 13 create:display_board[facing=north]");
            cmd(mc, "fill 104 -54 13 107 -54 13 create:nixie_tube[facing=north,double_face=floor]");
            cmd(mc, "setblock 106 -54 15 minecraft:oak_sign[rotation=8]");
            for (int[] l : links)
                cmd(mc, "setblock " + l[0] + " -54 11 create:display_link[facing=up]");
        }
        if (t == 50)
            server(mc, level -> {
                for (int i = 0; i < links.length; i++) {
                    BlockPos link = new BlockPos(links[i][0], -54, 11);
                    var be = level.getBlockEntity(link);
                    var tag = be.saveWithoutMetadata(level.registryAccess());
                    tag.put("TargetOffset", net.minecraft.nbt.NbtUtils.writeBlockPos(
                            new BlockPos(links[i][1], -54, links[i][2]).subtract(link)));
                    var source = new net.minecraft.nbt.CompoundTag();
                    source.putString("Id", "frostworks:" + sources[i]);
                    tag.put("Source", source);
                    be.loadWithComponents(tag, level.registryAccess());
                    be.setChanged();
                }
            });
        // ---- waits on the room itself, not on a tick count ----
        // 1: cold and at its target (it cools by coolingRate, 0.5 deg a second: about a minute from the ambient)
        if (t >= 60 && displayPhase == 0 && t % 10 == 0)
            server(mc, level -> {
                var room = com.frostworks.room.RoomManager.get(level).roomOfVent(new BlockPos(101, -55, 11));
                if (room == null || room.status() != com.frostworks.room.core.RoomStatus.COLD
                        || room.temperature() > room.coldTarget() + 0.05)
                    return;
                for (int[] l : links) // read now rather than at their next passive refresh
                    if (level.getBlockEntity(new BlockPos(l[0], -54, 11))
                            instanceof com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity link)
                        link.tickSource();
                System.out.println("PILOT display room " + room.status() + " " + room.temperature() + " deg, vents "
                        + room.activeVents() + "/" + room.ventCount() + ", max " + com.frostworks.overspeed.Overspeed.allowedRpm(room));
                displayPhase = 1;
            });
        if (displayPhase == 1) {
            displayPhase = 2;
            displayAt = t;
            cmd(mc, "tp @a 103.5 -54 10.5"); // standing on the roof's edge (not flying: a player in the air falls)
            lookTarget = new Vec3(103.5, -54, 13.5);
        }
        if (displayPhase == 2 && t == displayAt + 40)
            capture(mc, "affichage_pres");
        if (displayPhase == 2 && t == displayAt + 45) {
            cmd(mc, "setblock 103 -50 -6 minecraft:stone"); // a stand above the roof, from afar
            cmd(mc, "tp @a 103.5 -49 -5.5");
            lookTarget = new Vec3(103.5, -54, 13.5);
        }
        if (displayPhase == 2 && t == displayAt + 85)
            capture(mc, "affichage_loin");
        // 2: a hole in a side wall: the room is OPEN at once; the links read it again within their second
        if (displayPhase == 2 && t == displayAt + 90) {
            cmd(mc, "setblock 100 -58 12 minecraft:air");
            cmd(mc, "tp @a 103.5 -54 10.5");
            lookTarget = new Vec3(103.5, -54, 13.5);
        }
        if (displayPhase == 2 && t > displayAt + 90 && t % 5 == 0)
            server(mc, level -> {
                var room = com.frostworks.room.RoomManager.get(level).roomOfVent(new BlockPos(101, -55, 11));
                if (room != null && room.status() == com.frostworks.room.core.RoomStatus.OPEN && displayPhase == 2) {
                    System.out.println("PILOT display room after " + room.status() + ", vents " + room.activeVents()
                            + "/" + room.ventCount() + ", max " + com.frostworks.overspeed.Overspeed.allowedRpm(room));
                    displayPhase = 3;
                }
            });
        if (displayPhase == 3) {
            displayPhase = 4;
            displayAt = t;
        }
        if (displayPhase == 4 && t == displayAt + 45) // two passive refreshes
            capture(mc, "affichage_apres");
        // the pilot stops when t reaches this exactly: a fixed tick once the last capture is planned
        return displayPhase == 4 ? displayAt + 50 : Integer.MAX_VALUE;
    }

    private static volatile int displayPhase;
    private static int displayAt;

    private static String clearDecor(int z) {
        return "fill 20 -60 " + z + " 24 -57 " + z + " air";
    }

    private static void logBackend(String when) {
        var backend = dev.engine_room.flywheel.api.backend.BackendManager.currentBackend();
        System.out.println("PILOT flywheel backend " + when + ": "
                + dev.engine_room.flywheel.api.backend.Backend.REGISTRY.getId(backend));
    }

    /**
     * Translucent strips in front of a coloured decor, from the front, at an angle, edge-on and from
     * behind; the smaller swing; the same with Flywheel's backend off.
     */
    private static int door(Minecraft mc) {
        String[] wools = {"red_wool", "yellow_wool", "lime_wool", "light_blue_wool", "magenta_wool"};
        if (t == 30) {
            // a clean view: no villager left from an earlier run, no command feedback, no goggles overlay
            cmd(mc, "kill @e[type=villager]");
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            cmd(mc, "gamerule sendCommandFeedback false");
            cmd(mc, "item replace entity @a armor.head with air");
            cmd(mc, "fill 14 -60 4 30 -50 20 air");
            cmd(mc, "fill 20 -60 12 24 -58 12 frostworks:cold_wall");
            cmd(mc, "fill 22 -60 12 22 -59 12 air");
            for (int i = 0; i < wools.length; i++)
                cmd(mc, "fill " + (20 + i) + " -60 15 " + (20 + i) + " -57 15 " + wools[i]);
            placeDoor(mc, new BlockPos(22, -60, 12));
        }
        if (t == 60) {
            logBackend("start");
            playerCmd(mc, "tp @s 22.5 -60 8.5");
            lookTarget = new Vec3(22.5, -59.0, 12.5);
        }
        if (t == 100)
            capture(mc, "porte_translucide_face");
        if (t == 105) { // three-quarter view: the strips overlap each other
            playerCmd(mc, "tp @s 24.8 -59.4 9.6");
            lookTarget = new Vec3(22.5, -59.2, 12.4);
        }
        if (t == 140)
            capture(mc, "porte_translucide_biais");
        if (t == 145) { // grazing view, close: up to four strips one behind the other
            playerCmd(mc, "tp @s 23.9 -59.4 10.6");
            lookTarget = new Vec3(22.4, -59.2, 12.5);
        }
        if (t == 180)
            capture(mc, "porte_translucide_rasant");
        // ---- swing, from the front ----
        if (t == 185) {
            playerCmd(mc, "tp @s 22.5 -60 8.5");
            lookTarget = new Vec3(22.5, -59.0, 12.5);
        }
        if (t == 190)
            spawnPushed(mc);
        if (t > 191 && t < 230)
            push(mc);
        if (t == 204)
            capture(mc, "porte_balancement_faible_1");
        if (t == 208)
            capture(mc, "porte_balancement_faible_2");
        if (t == 215)
            capture(mc, "porte_balancement_faible_3");
        // ---- swing, low three-quarter view (the bottom of the strips moves the most) ----
        if (t == 235) {
            playerCmd(mc, "tp @s 24.3 -60 10.2");
            lookTarget = new Vec3(22.5, -59.7, 12.5);
        }
        if (t == 270)
            capture(mc, "porte_biais_bas_repos");
        if (t == 272)
            spawnPushed(mc);
        if (t > 273 && t < 310)
            push(mc);
        if (t == 286)
            capture(mc, "porte_balancement_faible_biais_1");
        if (t == 290)
            capture(mc, "porte_balancement_faible_biais_2");
        // ---- from behind: the decor moved to the north side ----
        if (t == 315) {
            cmd(mc, clearDecor(15));
            for (int i = 0; i < wools.length; i++)
                cmd(mc, "fill " + (20 + i) + " -60 8 " + (20 + i) + " -57 8 " + wools[i]);
            server(mc, level -> { if (pushed != null) pushed.discard(); pushed = null; });
            playerCmd(mc, "tp @s 22.5 -60 16.5");
            lookTarget = new Vec3(22.5, -59.0, 12.5);
        }
        if (t == 355)
            capture(mc, "porte_translucide_dos");
        // ---- Flywheel backend off: Create's fallback rendering ----
        if (t == 360) {
            playerCmd(mc, "flywheel backend flywheel:off");
            cmd(mc, clearDecor(8));
            for (int i = 0; i < wools.length; i++)
                cmd(mc, "fill " + (20 + i) + " -60 15 " + (20 + i) + " -57 15 " + wools[i]);
            playerCmd(mc, "tp @s 22.5 -60 8.5");
        }
        if (t == 395)
            logBackend("after /flywheel backend flywheel:off");
        if (t == 400)
            capture(mc, "porte_translucide_face_flywheel_off");
        if (t == 405)
            spawnPushed(mc);
        if (t > 406 && t < 445)
            push(mc);
        if (t == 419)
            capture(mc, "porte_balancement_faible_flywheel_off");
        if (t == 455)
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.FULL);
        return 460;
    }

    /**
     * Places a block like a player does: item in the main hand, eyes at {@code eye} looking at the clicked face, then
     * the normal client interaction ({@code useItemOn}, so {@code getStateForPlacement} sees a real player).
     */
    private static void place(Minecraft mc, String item, Vec3 eye, BlockPos clicked, Direction face) {
        Vec3 hit = Vec3.atCenterOf(clicked).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5));
        if (placeStep == 0) { // first call: move there with the item in hand; the click comes 5 ticks later
            cmd(mc, "item replace entity @a weapon.mainhand with " + item);
            playerCmd(mc, String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f", eye.x,
                    eye.y - mc.player.getEyeHeight(), eye.z));
            lookTarget = hit;
            placeStep = 1;
            return;
        }
        placeStep = 0;
        mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, hit);
        var result = mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(hit, face, clicked, false));
        System.out.println("PILOT place " + item + " on " + clicked.toShortString() + " face " + face + " from "
                + eye + " looking " + mc.player.getNearestViewDirection() + ": " + result);
    }

    /** Server-side state of the room seen from a vent, as the game computes it. */
    private static void reportRoom(Minecraft mc, String label, BlockPos vent) {
        server(mc, level -> {
            var manager = com.frostworks.room.RoomManager.get(level);
            BlockState state = level.getBlockState(vent);
            var room = manager.roomOfVent(vent);
            System.out.println("PILOT room " + label + ": vent " + state + (room == null ? " no room"
                    : " room #" + room.id + " " + room.status() + " outcome " + room.outcome + " volume "
                    + room.volume + " vents " + room.ventCount()));
        });
    }

    /**
     * C2: two closed rooms of cold walls (inside 3 x 3 x 3), built like a player would: walls by
     * command, then the vent, the door and the shaft passage placed by hand, from outside (room A) and from inside
     * (room B). Reads the state the real code gives.
     */
    private static int rooms(Minecraft mc) {
        int[] xs = {40, 50};
        if (t == 30) {
            cmd(mc, "kill @e[type=item]");
            cmd(mc, "fill 36 -60 -6 58 -50 10 air");
            for (int x0 : xs) {
                cmd(mc, "fill " + x0 + " -60 0 " + (x0 + 4) + " -56 4 frostworks:cold_wall hollow");
                cmd(mc, "setblock " + (x0 + 2) + " -58 0 air");            // vent hole, north wall
                cmd(mc, "fill " + (x0 + 2) + " -59 4 " + (x0 + 2) + " -58 4 air"); // door hole, south wall
                cmd(mc, "setblock " + (x0 + 4) + " -58 2 air");            // passage hole, east wall
            }
        }
        // Room A: everything placed from outside the room
        int a = xs[0];
        if (t == 40 || t == 45)
            place(mc, "frostworks:vent", new Vec3(a + 2.5, -57.5, -2.5), new BlockPos(a + 3, -58, 0), Direction.WEST);
        if (t == 46 || t == 51)
            place(mc, "frostworks:insulated_door", new Vec3(a + 2.5, -57.4, 7.0), new BlockPos(a + 2, -60, 4),
                    Direction.UP);
        if (t == 52 || t == 57)
            place(mc, "frostworks:shaft_passage", new Vec3(a + 7.0, -57.5, 2.5), new BlockPos(a + 4, -58, 1),
                    Direction.SOUTH);
        // Room B: vent and door placed from inside the room, passage from outside
        int b = xs[1];
        if (t == 58 || t == 63)
            place(mc, "frostworks:vent", new Vec3(b + 2.5, -57.5, 2.5), new BlockPos(b + 3, -58, 0), Direction.WEST);
        if (t == 64 || t == 69)
            place(mc, "frostworks:insulated_door", new Vec3(b + 2.5, -57.4, 2.0), new BlockPos(b + 2, -60, 4),
                    Direction.UP);
        if (t == 70 || t == 75)
            place(mc, "frostworks:shaft_passage", new Vec3(b + 7.0, -57.5, 2.5), new BlockPos(b + 4, -58, 1),
                    Direction.SOUTH);
        if (t == 80)
            server(mc, level -> {
                for (int x0 : xs)
                    if (level.getBlockEntity(new BlockPos(x0 + 2, -58, 0))
                            instanceof com.frostworks.content.vent.VentBlockEntity vent)
                        vent.setCreativeSupply(true);
            });
        if (t == 140) {
            reportRoom(mc, "A (placed from outside)", new BlockPos(a + 2, -58, 0));
            reportRoom(mc, "B (placed from inside)", new BlockPos(b + 2, -58, 0));
            server(mc, level -> {
                for (int x0 : xs) {
                    System.out.println("PILOT door " + x0 + ": " + level.getBlockState(new BlockPos(x0 + 2, -59, 4))
                            + " / " + level.getBlockState(new BlockPos(x0 + 2, -58, 4)));
                    System.out.println("PILOT passage " + x0 + ": " + level.getBlockState(new BlockPos(x0 + 4, -58, 2)));
                }
            });
        }
        if (t == 240) {
            reportRoom(mc, "A after 100 more ticks", new BlockPos(a + 2, -58, 0));
            reportRoom(mc, "B after 100 more ticks", new BlockPos(b + 2, -58, 0));
        }
        return 250;
    }

    private static void guiScale(Minecraft mc, int scale) {
        mc.options.guiScale().set(scale);
        mc.resizeDisplay();
    }

    private static void logVent(Minecraft mc, String label, BlockPos pos) {
        if (mc.level.getBlockEntity(pos) instanceof com.frostworks.content.vent.VentBlockEntity vent)
            System.out.println("PILOT vent " + label + " (client): active " + vent.isActive() + " blowing "
                    + vent.isBlowing() + " spin " + String.format(java.util.Locale.ROOT, "%.3f", vent.spin.getValue())
                    + " angle " + String.format(java.util.Locale.ROOT, "%.1f", vent.propellerAngle(0)));
    }

    /**
     * Everything in one run: C2 (rooms built by hand, then the goggles on the closed room), C3 (the
     * goggles at GUI scales 1 to 4, short and long text), the vent's propeller (turning, Flywheel on and off, then
     * stopped) and the shaft passage on its three axes with a turning Create shaft.
     */
    private static int modif3(Minecraft mc) {
        int a = 40, b = 50;
        BlockPos ventA = new BlockPos(a + 2, -58, 0), ventB = new BlockPos(b + 2, -58, 0);
        if (t <= 245)
            rooms(mc); // builds and reports rooms A and B (vents placed by hand from outside and from inside)
        // ---- C2 + C3: goggles on room A (vent placed from outside), long text; a lone wall, short text ----
        if (t == 250) {
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            // big enough for GUI scale 4 (Minecraft caps the scale to what the window can hold)
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1920, 1080);
            mc.resizeDisplay();
            cmd(mc, "gamerule sendCommandFeedback false");
            cmd(mc, "item replace entity @a armor.head with create:goggles");
            cmd(mc, "item replace entity @a weapon.mainhand with air");
            cmd(mc, "setblock " + (a - 3) + " -59 -3 frostworks:cold_wall");
            playerCmd(mc, "tp @s " + (a + 2.5) + " -59 -2.5");
            lookTarget = Vec3.atCenterOf(ventA);
        }
        for (int s = 1; s <= 4; s++) {
            int t0 = 260 + (s - 1) * 60;
            if (t == t0)
                guiScale(mc, s);
            if (t == t0 + 20)
                capture(mc, "hud_long_scale" + s);
            if (t == t0 + 25) {
                playerCmd(mc, "tp @s " + (a - 2.5) + " -59 -0.5");
                lookTarget = Vec3.atCenterOf(new BlockPos(a - 3, -59, -3));
            }
            if (t == t0 + 50)
                capture(mc, "hud_short_scale" + s);
            if (t == t0 + 55) {
                playerCmd(mc, "tp @s " + (a + 2.5) + " -59 -2.5");
                lookTarget = Vec3.atCenterOf(ventA);
            }
        }
        if (t == 500) {
            guiScale(mc, 3);
            playerCmd(mc, "tp @s " + (a + 2.5) + " -59 -1.6"); // the C2 room, closed: goggles on its wall
            lookTarget = Vec3.atCenterOf(new BlockPos(a + 1, -58, 0));
        }
        if (t == 530)
            capture(mc, "c2_salle_fermee_lunettes");
        // ---- A: the vent of room B seen from inside, propeller turning ----
        if (t == 540) {
            // the vent's own mist hides the propeller: fewer particles for these pictures
            mc.options.particles().set(net.minecraft.client.ParticleStatus.MINIMAL);
            cmd(mc, "item replace entity @a armor.head with air");
            playerCmd(mc, "tp @s " + (b + 2.5) + " -59 2.6");
            lookTarget = Vec3.atCenterOf(ventB);
        }
        if (t == 600) {
            logVent(mc, "B", ventB);
            capture(mc, "event_actif_1");
        }
        if (t == 603) {
            logVent(mc, "B", ventB);
            capture(mc, "event_actif_2");
        }
        if (t == 610) {
            logBackend("before");
            playerCmd(mc, "flywheel backend flywheel:off");
        }
        if (t == 650) {
            logBackend("after /flywheel backend flywheel:off");
            logVent(mc, "B", ventB);
            capture(mc, "event_actif_flywheel_off_1");
        }
        if (t == 653)
            capture(mc, "event_actif_flywheel_off_2");
        if (t == 660) { // no more gas: once its buffer is empty the vent goes idle and its propeller slows down
            playerCmd(mc, "flywheel backend flywheel:indirect");
            server(mc, level -> {
                if (level.getBlockEntity(ventB) instanceof com.frostworks.content.vent.VentBlockEntity vent)
                    vent.setCreativeSupply(false);
            });
        }
        if (t > 660 && t < 1600 && t % 20 == 0 && mc.level.getBlockEntity(ventB)
                instanceof com.frostworks.content.vent.VentBlockEntity vent && !vent.isActive() && stopAt < 0) {
            stopAt = t;
            logVent(mc, "B gone idle", ventB);
        }
        if (stopAt > 0 && t == stopAt + 15)
            logVent(mc, "B slowing down", ventB);
        if (stopAt > 0 && t == stopAt + 120) {
            logVent(mc, "B stopped", ventB);
            capture(mc, "event_arrete");
        }
        // ---- B: shaft passages on the three axes, a Create shaft turning through each ----
        int p = 60;
        if (stopAt > 0 && t == stopAt + 130) {
            mc.options.particles().set(net.minecraft.client.ParticleStatus.ALL);
            cmd(mc, "fill " + p + " -60 -4 " + (p + 16) + " -55 6 air");
            cmd(mc, "setblock " + p + " -59 0 create:creative_motor[facing=east]");
            cmd(mc, "setblock " + (p + 1) + " -59 0 frostworks:shaft_passage[axis=x]");
            cmd(mc, "setblock " + (p + 2) + " -59 0 create:shaft[axis=x]");
            cmd(mc, "setblock " + (p + 6) + " -59 -1 create:creative_motor[facing=south]");
            cmd(mc, "setblock " + (p + 6) + " -59 0 frostworks:shaft_passage[axis=z]");
            cmd(mc, "setblock " + (p + 6) + " -59 1 create:shaft[axis=z]");
            cmd(mc, "setblock " + (p + 11) + " -60 0 create:creative_motor[facing=up]");
            cmd(mc, "setblock " + (p + 11) + " -59 0 frostworks:shaft_passage[axis=y]");
            cmd(mc, "setblock " + (p + 11) + " -58 0 create:shaft[axis=y]");
        }
        if (stopAt > 0 && t == stopAt + 140) {
            playerCmd(mc, "tp @s " + (p + 6.5) + " -58 5.5");
            lookTarget = new Vec3(p + 6.5, -59, 0.5);
        }
        if (stopAt > 0 && t == stopAt + 180)
            capture(mc, "traversee_3_axes");
        String[] axes = {"x", "z", "y"};
        // each passage seen at an angle on the face where the shaft comes out of its hole
        Vec3[] eyes = {new Vec3(p + 3.4, -57.9, 1.9), new Vec3(p + 7.9, -57.9, 2.4), new Vec3(p + 13.6, -55.4, 2.6)};
        Vec3[] looks = {new Vec3(p + 2.0, -58.5, 0.5), new Vec3(p + 6.5, -58.5, 1.0), new Vec3(p + 11.5, -58.0, 0.5)};
        for (int i = 0; i < 3; i++) {
            int t0 = stopAt + 190 + i * 50;
            if (stopAt > 0 && t == t0) {
                mc.player.getAbilities().flying = true; // stays where it is put (creative flight)
                mc.player.onUpdateAbilities();
                playerCmd(mc, String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f", eyes[i].x, eyes[i].y - 1.62,
                        eyes[i].z));
                lookTarget = looks[i];
            }
            if (stopAt > 0 && t == t0 + 40) {
                server(mc, level -> System.out.println("PILOT passage speeds: " + speeds(level, p)));
                capture(mc, "traversee_axe_" + axes[i]);
            }
        }
        if (stopAt > 0 && t == stopAt + 355)
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.FULL);
        return stopAt > 0 ? stopAt + 360 : 1700;
    }

    private static int stopAt = -1;

    private static String speeds(ServerLevel level, int p) {
        StringBuilder s = new StringBuilder();
        for (BlockPos pos : new BlockPos[]{new BlockPos(p + 1, -59, 0), new BlockPos(p + 2, -59, 0),
                new BlockPos(p + 6, -59, 0), new BlockPos(p + 6, -59, 1), new BlockPos(p + 11, -59, 0),
                new BlockPos(p + 11, -58, 0)})
            if (level.getBlockEntity(pos) instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity k)
                s.append(pos.toShortString()).append('=').append(k.getSpeed()).append(' ');
        return s.toString();
    }

    private static void view(Minecraft mc, double x, double eyeY, double z, Vec3 target) {
        mc.player.getAbilities().flying = true; // stays where it is put
        mc.player.onUpdateAbilities();
        playerCmd(mc, String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f", x, eyeY - 1.62, z));
        lookTarget = target;
    }

    /**
     * The infusion rig (compressor -> pipes -> cryogenic infuser over a depot), an iron sheet put on the
     * depot, the infuser before, while its arm is down, and the cold iron sheet after; then an expander on its
     * textures copied into our folder.
     */
    /** The JEI category "Cryogenic infusion", captured at several moments of the arm cycle. */
    private static int jei(Minecraft mc) {
        if (t == 80) {
            stage = "jei open";
            try {
                Class.forName("com.frostworks.compat.jei.FrostworksJeiPlugin").getMethod("showInfusing").invoke(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        if (t == 100 || t == 105 || t == 110 || t == 115 || t == 125)
            capture(mc, "jei_infusing_" + t);
        if (t == 90)
            try {
                var f = Class.forName("com.frostworks.compat.jei.FrostworksJeiPlugin").getDeclaredField("runtime");
                f.setAccessible(true);
                Object runtime = f.get(null);
                Object manager = runtime.getClass().getMethod("getIngredientManager").invoke(runtime);
                Object type = Class.forName("mezz.jei.api.constants.VanillaTypes").getField("ITEM_STACK").get(null);
                java.util.Collection<?> all = (java.util.Collection<?>) manager.getClass()
                        .getMethod("getAllIngredients", Class.forName("mezz.jei.api.ingredients.IIngredientType"))
                        .invoke(manager, type);
                long passages = all.stream().filter(s -> s instanceof net.minecraft.world.item.ItemStack st
                        && st.getItem().toString().contains("passage")).count();
                long ours = all.stream().filter(s -> s instanceof net.minecraft.world.item.ItemStack st
                        && st.getItem().toString().startsWith("frostworks:")).count();
                System.out.println("PILOT jei items of the mod " + ours + ", passages " + passages);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        if (t == 190) // What a chilled ore is made of, and its information page
            try {
                Class.forName("com.frostworks.compat.jei.FrostworksJeiPlugin").getMethod("showChilled").invoke(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        if (t == 210 || t == 225)
            capture(mc, "jei_chilled_" + t);
        if (t == 230) // the chilled ores' infusion recipes (the information page comes first above)
            try {
                Class.forName("com.frostworks.compat.jei.FrostworksJeiPlugin").getMethod("showChilledInfusion").invoke(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        if (t == 238)
            capture(mc, "jei_chilled_infusion");
        if (t == 245) // The crushing recipe of chilled raw iron, its 15 % on the second crushed raw iron
            try {
                Class.forName("com.frostworks.compat.jei.FrostworksJeiPlugin").getMethod("showChilledUses").invoke(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        if (t == 252) // the mouse over our result (the third one): Create's tooltip gives its chance
            try {
                for (String f : new String[]{"xpos", "ypos"}) {
                    var field = net.minecraft.client.MouseHandler.class.getDeclaredField(f);
                    field.setAccessible(true);
                    field.setDouble(mc.mouseHandler, f.equals("xpos") ? 833 : 648);
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        if (t == 258)
            capture(mc, "jei_chilled_crushing");
        if (t == 140) // The freezing category
            try {
                Class.forName("com.frostworks.compat.jei.FrostworksJeiPlugin").getMethod("showFreezing").invoke(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        if (t == 160 || t == 175)
            capture(mc, "jei_freezing_" + t);
        if (t == 262) // The distillation tower
            try {
                Class.forName("com.frostworks.compat.jei.FrostworksJeiPlugin").getMethod("showDistilling").invoke(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        if (t == 278 || t == 290)
            capture(mc, "jei_distilling_" + t);
        if (t == 292) // the column base's stress impact line (Create's, worked out for one layer)
            for (var line : new net.minecraft.world.item.ItemStack(com.frostworks.registry.FwBlocks.COLUMN_BASE.get())
                    .getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player,
                            net.minecraft.world.item.TooltipFlag.NORMAL))
                System.out.println("PILOT tooltip column base: " + line.getString());
        return 300;
    }

    /** The witness Ponder scene of the cold wall. */
    private static int ponder(Minecraft mc) {
        // Every Ponder scene of the mod, one capture per step: {item, scene of that item, time
        // in the scene, name}. A new item opens its Ponder screen; a new scene of the same item scrolls to it. After
        // either, two short waits: Ponder draws both scenes while it switches.
        Object[][] shots = {
                {"cold_wall", 0, 100, "1_walls"}, {"cold_wall", 0, 205, "2_vent"}, {"cold_wall", 0, 295, "3_cooling"},
                {"cold_wall", 0, 335, "3_goggles"}, {"cold_wall", 0, 445, "4_door_out"},
                {"cold_wall", 0, 467, "4_door_in"}, {"cold_wall", 0, 515, "4_inside"}, {"cold_wall", 0, 631, "5_hole"},
                {"cold_wall", 0, 716, "6_shaft"}, {"cold_wall", 0, 771, "6_pipe"}, {"cold_wall", 0, 846, "6_belt"},
                {"cold_wall", 0, 946, "7_closed"},
                {"cold_wall", 1, 10, "s0_switch"}, {"cold_wall", 1, 30, "s0_wait"}, {"cold_wall", 1, 60, "s1_limit"},
                {"cold_wall", 1, 220, "s2_gas"}, {"cold_wall", 1, 320, "s3_320"}, {"cold_wall", 1, 470, "s4_frosium"},
                {"cold_wall", 1, 600, "s4_384"}, {"cold_wall", 1, 690, "s5_grace"}, {"cold_wall", 1, 780, "s5_broken"},
                {"cold_wall", 2, 10, "b0_switch"}, {"cold_wall", 2, 30, "b0_wait"}, {"cold_wall", 2, 80, "b1_rooms"},
                {"cold_wall", 2, 195, "b2_320"}, {"cold_wall", 2, 300, "b3_end_out"}, {"cold_wall", 2, 385, "b4_pulley"},
                {"cold_wall", 2, 455, "b5_broken"},
                {"compressor", 0, 10, "c0"}, {"compressor", 0, 30, "c0b"}, {"compressor", 0, 80, "c1_gas"},
                {"compressor", 0, 200, "c2_setting"}, {"compressor", 0, 300, "c3_rate"},
                {"cryo_infuser", 0, 10, "i0"}, {"cryo_infuser", 0, 30, "i0b"}, {"cryo_infuser", 0, 80, "i1_no_gas"},
                {"cryo_infuser", 0, 175, "i2_dwell"}, {"cryo_infuser", 0, 215, "i2_result"},
                {"cryo_infuser", 0, 300, "i3_dwell"}, {"cryo_infuser", 0, 345, "i3_result"},
                {"cryo_infuser", 0, 440, "i4_basin"}, {"cryo_infuser", 0, 470, "i4_dwell"}, {"cryo_infuser", 0, 505, "i4_ice"},
                {"cryo_infuser", 1, 10, "co0_switch"}, {"cryo_infuser", 1, 30, "co0_wait"}, {"cryo_infuser", 1, 80, "co1_infuse"},
                {"cryo_infuser", 1, 150, "co1_chilled"}, {"cryo_infuser", 1, 250, "co2_crush"}, {"cryo_infuser", 1, 390, "co3_gas"},
                {"cryo_infuser", 1, 520, "co4_frosium"},
                {"column_base", 0, 10, "t0"}, {"column_base", 0, 30, "t0b"}, {"column_base", 0, 60, "t1_base"},
                {"column_base", 0, 133, "t2_tanks"}, {"column_base", 0, 155, "t2_layer"}, {"column_base", 0, 260, "t2_column"},
                {"column_base", 0, 420, "t3_in"}, {"column_base", 0, 510, "t3b_ice"}, {"column_base", 0, 640, "t3b_no_ice"},
                {"column_base", 0, 770, "t4_out"},
                {"reservoir", 0, 10, "f0"}, {"reservoir", 0, 30, "f0b"}, {"reservoir", 0, 60, "f1_tank"},
                {"reservoir", 0, 110, "f2_filling"}, {"reservoir", 0, 170, "f3_refused"},
                {"expander", 0, 10, "e0"}, {"expander", 0, 30, "e0b"}, {"expander", 0, 80, "e1_in"},
                {"expander", 0, 200, "e2_capacity"}, {"expander", 0, 320, "e3_share"},
                {"vent", 0, 10, "v0"}, {"vent", 0, 30, "v0b"}, {"vent", 0, 80, "v1_on"}, {"vent", 0, 180, "v2_coverage"},
                {"vent", 0, 280, "v3_total"}};
        int k = (t - 120) / 12;
        if (t == 80) {
            stage = "ponder open";
            mc.setScreen(net.createmod.ponder.foundation.ui.PonderUI.of(new net.minecraft.world.item.ItemStack(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Frostworks.id((String) shots[0][0])))));
        }
        if (t >= 120 && (t - 120) % 12 == 0 && k < shots.length) {
            String item = (String) shots[k][0];
            int scene = (int) shots[k][1];
            if (k > 0 && !item.equals(shots[k - 1][0]))
                mc.setScreen(net.createmod.ponder.foundation.ui.PonderUI.of(new net.minecraft.world.item.ItemStack(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Frostworks.id(item)))));
            else if (k > 0 && scene != (int) shots[k - 1][1] && mc.screen instanceof net.createmod.ponder.foundation.ui.PonderUI ui)
                try {
                    var scroll = net.createmod.ponder.foundation.ui.PonderUI.class.getDeclaredMethod("scroll", boolean.class);
                    scroll.setAccessible(true);
                    scroll.invoke(ui, true);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            stage = "ponder " + shots[k][3];
        }
        if (t >= 120 && (t - 120) % 12 == 2 && k < shots.length
                && mc.screen instanceof net.createmod.ponder.foundation.ui.PonderUI ui) {
            var active = ui.getActiveScene();
            int time = (int) shots[k][2];
            try {
                var current = net.createmod.ponder.foundation.PonderScene.class.getDeclaredField("currentTime");
                current.setAccessible(true);
                if ((int) current.get(active) < time)
                    active.seekToTime(time);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        if (t >= 120 && (t - 120) % 12 == 8 && k < shots.length)
            capture(mc, "ponder_" + shots[k][3]);
        return 120 + 12 * shots.length + 10;
    }

    /** A belt passage made by a real right click with a cold wall. */
    private static int belt(Minecraft mc) {
        BlockPos a = new BlockPos(38, -60, 42), b = new BlockPos(44, -60, 42);
        if (t == 30)
            for (String c : new String[]{"fill 36 -60 39 46 -56 45 air", "setblock 38 -60 42 create:shaft[axis=z]",
                    "setblock 44 -60 42 create:shaft[axis=z]", "setblock 38 -60 41 create:creative_motor[facing=south]",
                    "item replace entity @a weapon.mainhand with frostworks:cold_wall 8"})
                cmd(mc, c);
        if (t == 36)
            server(mc, level -> com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem.createBelts(level, a, b));
        if (t == 40) {
            cmd(mc, "tp @a 41.5 -60 37.5");
            lookTarget = new Vec3(41, -59.5, 42.5);
        }
        if (t == 50 || t == 54)
            mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(new Vec3(t == 50 ? 40.5 : 41.5, -59.2, 42.5),
                            net.minecraft.core.Direction.UP, new BlockPos(t == 50 ? 40 : 41, -60, 42), false));
        if (t == 70)
            server(mc, level -> System.out.println("PILOT belt passages " + com.frostworks.content.passage.BeltPassage
                    .is(level, new BlockPos(40, -60, 42), level.getBlockState(new BlockPos(40, -60, 42))) + " "
                    + com.frostworks.content.passage.BeltPassage.is(level, new BlockPos(41, -60, 42),
                    level.getBlockState(new BlockPos(41, -60, 42))) + ", cold walls placed above: "
                    + level.getBlockState(new BlockPos(40, -59, 42)).getBlock()));
        if (t == 80)
            capture(mc, "belt_passage");
        // our strips: a wall above a flat passage (top strip); a belt on its side, encased, walls beside it
        BlockPos sa = new BlockPos(38, -60, 46), sb = new BlockPos(44, -60, 46);
        if (t == 82)
            for (String c : new String[]{"setblock 40 -59 42 frostworks:cold_wall", "fill 36 -60 45 46 -58 47 air",
                    "setblock 38 -60 46 create:shaft[axis=y]", "setblock 44 -60 46 create:shaft[axis=y]"})
                cmd(mc, c);
        if (t == 86)
            server(mc, level -> com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem.createBelts(level, sa, sb));
        if (t == 90)
            server(mc, level -> {
                com.frostworks.content.passage.BeltPassage.encase(level, new BlockPos(41, -60, 46));
                System.out.println("PILOT sideways belt " + level.getBlockState(new BlockPos(41, -60, 46)));
            });
        if (t == 92)
            for (String c : new String[]{"setblock 41 -60 45 frostworks:cold_wall", "setblock 41 -60 47 frostworks:cold_wall"})
                cmd(mc, c);
        if (t == 100) {
            cmd(mc, "tp @a 38.3 -58.2 43.6");
            lookTarget = new Vec3(40.5, -59.6, 42.5);
        }
        if (t == 115)
            capture(mc, "belt_flap_top");
        if (t == 120) {
            cmd(mc, "tp @a 38.6 -58.6 46.5");
            lookTarget = new Vec3(41.0, -59.5, 46.5);
        }
        if (t == 135)
            capture(mc, "belt_flap_sides");
        if (t == 140) { // along the flat belt, at its height, toward the strip under the wall
            cmd(mc, "gamemode spectator @a");
            cmd(mc, "tp @a 38.9 -60.95 42.5");
            lookTarget = new Vec3(41.0, -59.25, 42.5);
        }
        if (t == 155)
            capture(mc, "belt_flap_top_inside");
        if (t == 157)
            cmd(mc, "gamemode creative @a");
        return 165;
    }

    /** Builds the cold room of the Ponder scenes and saves it as their schematic. */
    private static int schematic(Minecraft mc) {
        BlockPos o = new BlockPos(100, -61, 100);
        if (t == 30)
            server(mc, level -> ColdRoomSchematic.build(level, o));
        if (t == 40) {
            cmd(mc, "tp @a 98 -59 98");
            lookTarget = new Vec3(105, -58, 105);
        }
        if (t == 70)
            server(mc, level -> {
                java.nio.file.Path file = mc.gameDirectory.toPath().toAbsolutePath().normalize().getParent()
                        .resolve("common-1.21.1/src/main/resources/assets/frostworks/ponder/cold_room.nbt");
                try {
                    ColdRoomSchematic.save(level, o, file);
                    System.out.println("PILOT schematic saved " + file);
                } catch (java.io.IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        if (t == 80)
            capture(mc, "cold_room_world");
        return 90;
    }

    /** The goggles tooltip from one wall to the next one of the same room: shown all along, no new fade. */
    private static int goggles(Minecraft mc) {
        if (t == 30)
            for (String c : new String[]{"fill 58 -61 18 66 -54 26 air", "fill 60 -60 20 64 -56 24 frostworks:cold_wall hollow",
                    "setblock 62 -58 20 frostworks:vent[facing=south]", "tp @a 62.5 -59 15.5"})
                cmd(mc, c);
        if (t == 60)
            lookTarget = new Vec3(61.5, -57.5, 20);
        if (t == 90)
            lookTarget = new Vec3(63.5, -57.5, 20);
        if (t == 120)
            lookTarget = new Vec3(62.5, -50, 10); // away from the room
        if (t == 140)
            lookTarget = new Vec3(61.5, -57.5, 20);
        if (t >= 80 && t <= 160)
            try {
                var current = Class.forName("com.frostworks.client.ClientRoomInfo").getDeclaredMethod("current");
                current.setAccessible(true);
                var hover = Class.forName("com.frostworks.client.RoomGoggleOverlay").getDeclaredField("hoverTicks");
                hover.setAccessible(true);
                Object info = current.invoke(null);
                System.out.println("PILOT goggles t=" + t + " shown=" + (info != null) + " hover=" + hover.get(null)
                        + " target=" + (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult b ? b.getBlockPos() : null));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        return 170;
    }

    /** The creative inventory on the mod's tab (the chilled ores at the end). */
    private static int creative(Minecraft mc) {
        if (t == 40) {
            cmd(mc, "gamemode creative");
        }
        if (t == 60) {
            stage = "creative tab";
            mc.setScreen(new net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(mc.player,
                    mc.player.connection.enabledFeatures(), true));
        }
        if (t == 70 && mc.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen s)
            try {
                var select = s.getClass().getDeclaredMethod("selectTab", net.minecraft.world.item.CreativeModeTab.class);
                select.setAccessible(true);
                select.invoke(s, com.frostworks.registry.FwCreativeTab.MAIN.get());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        if (t == 85) // Create's summary tooltip on our items
            for (var b : java.util.List.of(com.frostworks.registry.FwBlocks.VENT, com.frostworks.registry.FwBlocks.CRYO_INFUSER))
                System.out.println("PILOT tooltip " + net.minecraft.client.gui.screens.Screen.getTooltipFromItem(mc,
                        new net.minecraft.world.item.ItemStack(b.get())).stream().map(c -> c.getString()).toList());
        if (t == 90)
            capture(mc, "creative_tab");
        if (t == 100 && mc.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen s)
            try {
                var scroll = s.getClass().getDeclaredField("scrollOffs");
                scroll.setAccessible(true);
                scroll.setFloat(s, 1f);
                var menu = s.getMenu();
                menu.getClass().getMethod("scrollTo", float.class).invoke(menu, 1f);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        if (t == 110)
            capture(mc, "creative_tab_end");
        return 120;
    }

    /** Frost tanks 1, 2 and 3 high (2 x 2), full, next to Create's tanks with water: the fluid level drawn. */
    private static int tanks(Minecraft mc) {
        if (t == 30) {
            cmd(mc, "fill 96 -61 156 120 -50 166 air");
            server(mc, level -> {
                for (int h = 1; h <= 3; h++)
                    for (int y = 0; y < h; y++)
                        for (int dx = 0; dx < 2; dx++)
                            for (int dz = 0; dz < 2; dz++) {
                                level.setBlockAndUpdate(new BlockPos(100 + 4 * h + dx, -60 + y, 160 + dz),
                                        com.frostworks.registry.FwBlocks.RESERVOIR.get().defaultBlockState());
                                level.setBlockAndUpdate(new BlockPos(100 + 4 * h + dx, -60 + y, 164 + dz),
                                        com.simibubi.create.AllBlocks.FLUID_TANK.getDefaultState());
                            }
            });
        }
        if (t >= 50 && t < 170 && t % 2 == 0) // 1000 mB at a time, as buckets do
            server(mc, level -> {
                for (int h = 1; h <= 3; h++) {
                    for (int z : new int[]{160, 164})
                        if (level.getBlockEntity(new BlockPos(100 + 4 * h, -60, z)) instanceof com.simibubi.create.content.fluids.tank.FluidTankBlockEntity tank) {
                            var c = tank.getControllerBE();
                            var fluid = z == 160 ? com.frostworks.registry.FwFluids.frosium() : net.minecraft.world.level.material.Fluids.WATER;
                            // through the capability of the top block, as a bucket on it or a pipe does
                            var top = new BlockPos(100 + 4 * h + 1, -60 + h - 1, z + 1);
                            var cap = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, top, null);
                            int filled = cap == null ? -1 : cap.fill(new net.neoforged.neoforge.fluids.FluidStack(fluid, 1000),
                                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                            if (t == 168)
                                System.out.println("PILOT tank h" + h + " z" + z + ": " + c.getTankInventory().getFluidAmount() + " cap " + (cap == null ? "none" : cap.getFluidInTank(0).getAmount() + " same " + (cap == c.getTankInventory()))
                                        + " / " + c.getTankInventory().getCapacity());
                        }
                }
            });
        if (t == 172)
            view(mc, 110, -57.5, 155.5, new Vec3(110, -59, 161));
        if (t == 230)
            capture(mc, "tanks_half_frost");
        if (t == 232)
            view(mc, 110, -57.5, 170.5, new Vec3(110, -59, 165));
        if (t == 290)
            capture(mc, "tanks_half_create");
        if (t == 292) { // a pool of Frosium, then the player in it
            cmd(mc, "fill 98 -61 176 104 -61 182 stone");
            cmd(mc, "fill 99 -60 177 103 -60 181 frostworks:frosium");
            view(mc, 101, -57.5, 186, new Vec3(101, -60, 179));
        }
        if (t == 340)
            capture(mc, "frosium_pool");
        if (t == 342) {
            lookTarget = null;
            mc.player.getAbilities().flying = false;
            mc.player.onUpdateAbilities();
            cmd(mc, "gamemode survival ");
            cmd(mc, "tp  101 -60 179");
        }
        if (t == 500)
            capture(mc, "frosium_inside");
        return 510;
    }

    /** The chilled raw ores: in item frames, on the ground, in the hand, from several angles (two-layer models). */
    private static int raws(Minecraft mc) {
        String[] raws = {"raw_iron", "raw_copper", "raw_gold", "raw_zinc"};
        if (t == 30) {
            cmd(mc, "fill 96 -61 156 112 -55 166 air");
            cmd(mc, "fill 98 -60 160 108 -57 160 stone");
            cmd(mc, "gamerule sendCommandFeedback false");
            for (int i = 0; i < raws.length; i++) {
                cmd(mc, "summon item_frame " + (100 + 2 * i) + " -59 161 {Facing:3b,Item:{id:\"frostworks:chilled_" + raws[i] + "\",count:1}}");
                cmd(mc, "summon item " + (100.5 + 2 * i) + " -60 163.5 {Item:{id:\"frostworks:chilled_" + raws[i] + "\",count:1},PickupDelay:32767,NoGravity:1b,Age:-32768}");
            }
            cmd(mc, "item replace entity  weapon.mainhand with frostworks:chilled_raw_iron");
        }
        double[][] views = {{103.5, -58.6, 167.5}, {98.0, -58.0, 165.0}, {109.0, -59.2, 164.0}, {103.5, -56.5, 164.0}};
        for (int i = 0; i < views.length; i++) {
            if (t == 50 + 40 * i)
                view(mc, views[i][0], views[i][1], views[i][2], new Vec3(103.5, -59.4, 162));
            if (t == 85 + 40 * i)
                capture(mc, "raws_" + i);
        }
        if (t == 220)
            mc.setScreen(new net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(mc.player,
                    mc.player.connection.enabledFeatures(), true));
        if (t == 240)
            capture(mc, "raws_inventory");
        return 250;
    }

    /** The chilled ores in the world, next to their base ores, close up and from afar (mipmaps). */
    private static int ores(Minecraft mc) {
        String[] ores = {"iron_ore", "emerald_ore", "deepslate_diamond_ore", "nether_quartz_ore", "zinc_ore"};
        if (t == 30) {
            cmd(mc, "fill 96 -61 156 112 -55 166 air");
            for (int i = 0; i < ores.length; i++) {
                String base = (ores[i].contains("zinc") ? "create:" : "minecraft:") + ores[i];
                cmd(mc, "setblock " + (100 + 2 * i) + " -60 160 " + base);
                cmd(mc, "setblock " + (100 + 2 * i) + " -60 162 frostworks:chilled_" + ores[i]);
            }
        }
        if (t == 40)
            view(mc, 104.5, -58.5, 166.5, new Vec3(104.5, -59.5, 161.5));
        if (t == 80)
            capture(mc, "ores_close");
        if (t == 82)
            view(mc, 104.5, -50.5, 190.5, new Vec3(104.5, -59.5, 161.5));
        if (t == 120)
            capture(mc, "ores_far");
        double[][] angles = {{100.6, -58.3, 164.2}, {104.6, -58.0, 164.4}, {101.0, -59.6, 160.6}, {103.4, -57.6, 161.0}};
        for (int i = 0; i < angles.length; i++) {
            if (t == 125 + 30 * i)
                view(mc, angles[i][0], angles[i][1], angles[i][2], new Vec3(102.5, -59.5, 162.5));
            if (t == 150 + 30 * i)
                capture(mc, "ores_angle_" + i);
        }
        return 250;
    }

    /** The mod list with our icon, then our config screen (Create's, with our title). */
    private static int modlist(Minecraft mc) {
        try {
            Class<?> list = net.neoforged.neoforge.client.gui.ModListScreen.class;
            if (t == 40) {
                stage = "mod list";
                mc.setScreen(new net.neoforged.neoforge.client.gui.ModListScreen(null));
            }
            if (t == 50 && mc.screen instanceof net.neoforged.neoforge.client.gui.ModListScreen s) {
                var search = list.getDeclaredField("search");
                search.setAccessible(true);
                ((net.minecraft.client.gui.components.EditBox) search.get(s)).setValue("frostworks");
            }
            if (t == 60 && mc.screen instanceof net.neoforged.neoforge.client.gui.ModListScreen s) {
                var field = list.getDeclaredField("modList");
                field.setAccessible(true);
                var widget = (net.neoforged.neoforge.client.gui.widget.ModListWidget) field.get(s);
                for (var entry : widget.children())
                    if (entry.getInfo().getModId().equals(Frostworks.MODID))
                        s.setSelected(entry);
            }
            if (t == 75)
                capture(mc, "modlist_icon");
            if (t == 80 && mc.screen instanceof net.neoforged.neoforge.client.gui.ModListScreen s) {
                stage = "config screen";
                var open = list.getDeclaredMethod("displayModConfig");
                open.setAccessible(true);
                open.invoke(s);
            }
            if (t == 100) {
                System.out.println("PILOT config screen " + (mc.screen == null ? null : mc.screen.getClass().getName()));
                capture(mc, "config_title");
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return 110;
    }

    /** Builds the six machine schematics side by side and saves them. */
    private static int machines(Minecraft mc) {
        if (t == 30)
            server(mc, level -> {
                for (int i = 0; i < MachineSchematics.ALL.length; i++)
                    MachineSchematics.build(level, new BlockPos(100 + 12 * i, -61, 130), MachineSchematics.ALL[i]);
            });
        if (t == 40) {
            cmd(mc, "tp @a 120 -55 120");
            lookTarget = new Vec3(130, -59, 133);
        }
        if (t == 80)
            server(mc, level -> {
                java.nio.file.Path dir = mc.gameDirectory.toPath().toAbsolutePath().normalize().getParent()
                        .resolve("common-1.21.1/src/main/resources/assets/frostworks/ponder");
                try {
                    for (int i = 0; i < MachineSchematics.ALL.length; i++)
                        MachineSchematics.save(level, new BlockPos(100 + 12 * i, -61, 130), MachineSchematics.ALL[i], dir);
                    System.out.println("PILOT machines saved in " + dir);
                } catch (java.io.IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        if (t == 90)
            capture(mc, "machines_world");
        return 100;
    }

    private static int infusion(Minecraft mc) {
        BlockPos corner = new BlockPos(100, -61, 40);
        BlockPos depot = com.frostworks.content.infuser.InfusionRig.depot(corner);
        if (t == 30) {
            // chat shown: a hidden chat, once saved in the options, makes the server refuse the player's /tp (the views)
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.FULL);
            mc.options.broadcastOptions();
            cmd(mc, "gamerule sendCommandFeedback false");
            cmd(mc, "fill 96 -60 34 110 -50 48 air");
            server(mc, level -> com.frostworks.content.infuser.InfusionRig.build(level, corner));
            cmd(mc, "setblock 100 -60 45 frostworks:expander[facing=east]"); // screen up, shaft along X
            cmd(mc, "setblock 101 -60 45 create:fluid_pipe");
        }
        if (t == 40)
            view(mc, 103.5, -56.8, 46.5, new Vec3(102.5, -59.0, 40.5));
        if (t == 80)
            capture(mc, "infusion_montage");
        if (t == 82)
            view(mc, 106.6, -58.4, 42.6, new Vec3(104.5, -59.2, 40.5));
        if (t == 110) {
            capture(mc, "infuseur_avant");
            server(mc, level -> com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
                    .get(level, depot, com.simibubi.create.content.logistics.depot.DepotBehaviour.TYPE)
                    .setHeldItem(new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(
                            com.simibubi.create.AllItems.IRON_SHEET.asStack())));
        }
        if (t == 128)
            capture(mc, "infuseur_en_marche_1");
        if (t == 140)
            capture(mc, "infuseur_en_marche_2");
        if (t == 220)
            capture(mc, "plaque_froide_depot");
        if (t == 222)
            view(mc, 102.0, -58.6, 47.6, new Vec3(100.5, -59.5, 45.5));
        if (t == 260)
            capture(mc, "detendeur_textures");
        return 265;
    }

    /**
     * The tower in two blocks (a base alone, a base with two layers of segments), and three compressors
     * (facing up, east, south), each turned by a creative motor at its back and pushing into a tank at its front.
     */
    private static int modif5(Minecraft mc) {
        int x0 = 60, y = -60, z0 = 20;
        if (t == 30) {
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            mc.options.particles().set(net.minecraft.client.ParticleStatus.ALL);
            cmd(mc, "gamerule sendCommandFeedback false");
            cmd(mc, "item replace entity @a armor.head with air");
            cmd(mc, "fill 55 -60 14 90 -50 30 air");
            // towers: a base alone (x 60), a base + 2 layers of segments (x 64)
            cmd(mc, "fill 60 -60 20 61 -60 21 frostworks:column_base");
            // base shapes: one alone (x 60), a pair (x 62-63), in front of the 2 x 2
            cmd(mc, "setblock 60 -60 24 frostworks:column_base");
            cmd(mc, "fill 62 -60 24 63 -60 24 frostworks:column_base");
            // an L: a 2 x 2 with one block broken (x 66-67, its south-west missing)
            cmd(mc, "fill 66 -60 24 67 -60 25 frostworks:column_base");
            cmd(mc, "setblock 66 -60 25 air");
            // frost tanks alone: two stacked (x 58)
            cmd(mc, "fill 58 -60 24 58 -59 24 frostworks:reservoir");
            cmd(mc, "fill 64 -60 20 65 -60 21 frostworks:column_base");
            cmd(mc, "fill 64 -59 20 65 -58 21 frostworks:reservoir"); // frost tanks: they turn into column
            // compressor facing up: motor under it, tank above
            cmd(mc, "setblock 70 -60 20 create:creative_motor[facing=up]");
            cmd(mc, "setblock 70 -59 20 frostworks:compressor[facing=up]");
            cmd(mc, "setblock 70 -58 20 create:fluid_tank");
            // facing east, 2 blocks up: motor west of it, tank east
            cmd(mc, "setblock 75 -58 20 create:creative_motor[facing=east]");
            cmd(mc, "setblock 76 -58 20 frostworks:compressor[facing=east]");
            cmd(mc, "setblock 77 -58 20 create:fluid_tank");
            // facing south: motor north of it, tank south
            cmd(mc, "setblock 82 -59 19 create:creative_motor[facing=south]");
            cmd(mc, "setblock 82 -59 20 frostworks:compressor[facing=south]");
            cmd(mc, "setblock 82 -59 21 create:fluid_tank");
        }
        if (t == 40)
            view(mc, 63.5, -56.5, 29.5, new Vec3(63.5, -59.5, 23.0));
        if (t == 80)
            capture(mc, "base_formes");
        if (t == 82)
            view(mc, 64.9, -56.6, 16.0, new Vec3(65.0, -58.5, 21.0));
        if (t == 120)
            capture(mc, "tour_base_segments");
        // compressor facing up: its mark faces north, the side grilles all around
        if (t == 125)
            view(mc, 70.5, -58.5, 18.4, new Vec3(70.5, -58.5, 20.4));
        if (t == 165)
            capture(mc, "compresseur_haut_marque");
        if (t == 167)
            view(mc, 72.1, -58.2, 18.9, new Vec3(70.5, -58.5, 20.5));
        if (t == 200)
            capture(mc, "compresseur_haut_biais");
        // facing east: the mark faces down, a side grille faces up
        if (t == 205)
            view(mc, 76.5, -59.0, 18.6, new Vec3(76.5, -57.9, 20.5));
        if (t == 245)
            capture(mc, "compresseur_est_dessous_marque");
        if (t == 247)
            view(mc, 76.5, -55.8, 20.5, new Vec3(76.5, -57.5, 20.5));
        if (t == 285)
            capture(mc, "compresseur_est_dessus_1");
        if (t == 289)
            capture(mc, "compresseur_est_dessus_2");
        if (t == 291)
            view(mc, 76.5, -57.5, 18.3, new Vec3(76.5, -57.5, 20.5));
        if (t == 325)
            capture(mc, "compresseur_est_cote");
        // facing south: seen from the east (a side grille) and from below (the mark faces down)
        if (t == 330)
            view(mc, 84.4, -58.4, 20.5, new Vec3(82.5, -58.5, 20.5));
        if (t == 370)
            capture(mc, "compresseur_sud_cote");
        if (t == 372)
            view(mc, 82.5, -59.4, 18.7, new Vec3(82.5, -59.0, 20.5));
        if (t == 410)
            capture(mc, "compresseur_sud_marque");
        // classic path: Flywheel off
        if (t == 412) {
            playerCmd(mc, "flywheel backend flywheel:off");
            view(mc, 76.5, -55.8, 20.5, new Vec3(76.5, -57.5, 20.5));
        }
        if (t == 450)
            capture(mc, "compresseur_est_dessus_flywheel_off");
        if (t == 452)
            view(mc, 70.5, -58.5, 18.4, new Vec3(70.5, -58.5, 20.4));
        if (t == 490)
            capture(mc, "compresseur_haut_marque_flywheel_off");
        if (t == 495) {
            playerCmd(mc, "flywheel backend flywheel:indirect");
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.FULL);
        }
        return 500;
    }

    private static int reloadStage;
    private static String levelId;
    private static int coldAt = -1, rejoinAt = -1;
    private static com.frostworks.overspeed.OverspeedRig.Layout restartRig;

    private static String roomLine(ServerLevel level) {
        var room = com.frostworks.room.RoomManager.get(level).roomAt(restartRig.interior());
        boolean cog = com.simibubi.create.AllBlocks.LARGE_COGWHEEL.has(level.getBlockState(restartRig.largeCog()));
        float saw = level.getBlockEntity(restartRig.saw())
                instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity k ? k.getSpeed() : Float.NaN;
        return (room == null ? "no room" : String.format(java.util.Locale.ROOT, "room #%d %s %.2f deg C %s",
                room.id, room.status(), room.temperature(), room.isRestored() ? "(restored)" : "(searched)"))
                + ", large cogwheel " + (cog ? "intact" : "BROKEN") + ", saw " + saw + " rpm, game tick "
                + level.getGameTime();
    }

    /**
     * A real restart: a COLD room with a 320 rpm train (the overspeed rig) cools to -10 deg C, the player
     * leaves the world (saved and closed by the game), the same world is opened again, and the room, its temperature
     * and the large cogwheel are checked right after and 60 s later.
     */
    private static int restart(Minecraft mc) {
        BlockPos corner = new BlockPos(10, -60, 4);
        if (t == 30 && reloadStage == 0) {
            cmd(mc, "fill 6 -60 2 22 -52 14 air");
            server(mc, level -> restartRig = com.frostworks.overspeed.OverspeedRig.buildInRoom(level, corner));
        }
        if (t == 200 && reloadStage == 0)
            server(mc, level -> com.frostworks.overspeed.OverspeedRig.setMotorSpeed(level, restartRig, 160));
        if (reloadStage == 0 && t > 200 && t % 20 == 0 && coldAt < 0)
            server(mc, level -> {
                var room = com.frostworks.room.RoomManager.get(level).roomAt(restartRig.interior());
                if (room != null && room.temperature() <= -10f && coldAt < 0) {
                    coldAt = t;
                    System.out.println("PILOT before leaving: " + roomLine(level));
                }
            });
        BlockPos eye = corner.offset(com.frostworks.room.TestRoomBuilder.SIZE_X + 1, 1, 3);
        if (reloadStage == 0 && coldAt > 0 && t == coldAt + 5) {
            playerCmd(mc, "tp @s " + (eye.getX() + .5) + " " + eye.getY() + " " + (eye.getZ() + .5));
            lookTarget = Vec3.atCenterOf(restartRig.eastWall());
        }
        if (reloadStage == 0 && coldAt > 0 && t == coldAt + 40)
            capture(mc, "redemarrage_1_avant");
        if (reloadStage == 0 && coldAt > 0 && t == coldAt + 45) {
            levelId = mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .toAbsolutePath().normalize().getFileName().toString();
            System.out.println("PILOT leaving world " + levelId + " at game tick " + mc.level.getGameTime());
            reloadStage = 1;
            stage = "leaving world";
            // as the pause menu's "Save and quit": close the connection, then leave (the game saves the world)
            mc.level.disconnect();
            mc.disconnect(new net.minecraft.client.gui.screens.GenericMessageScreen(
                    net.minecraft.network.chat.Component.translatable("menu.savingLevel")));
            mc.setScreen(new TitleScreen());
            return Integer.MAX_VALUE;
        }
        if (reloadStage == 2) { // first tick back in the world
            reloadStage = 3;
            rejoinAt = t;
            server(mc, level -> System.out.println("PILOT first server tick after reopening: " + roomLine(level)));
            lookTarget = Vec3.atCenterOf(restartRig.eastWall());
        }
        if (reloadStage == 3 && t == rejoinAt + 40)
            capture(mc, "redemarrage_2_apres");
        if (reloadStage == 3 && t == rejoinAt + 1200) {
            server(mc, level -> System.out.println("PILOT 60 s after reopening: " + roomLine(level)));
            capture(mc, "redemarrage_3_60s");
        }
        return reloadStage == 3 ? rejoinAt + 1210 : Integer.MAX_VALUE;
    }

    /** Stress loop: rig built north-east of the player (motor at 2,-60,-2), live balance, goggles on the machines. */
    private static int loop(Minecraft mc) {
        if (t == 30) {
            playerCmd(mc, "tp @s 0.5 -60 0.5 0 0");
            playerCmd(mc, "frostworks loop rig");
        }
        if (t == 40)
            playerCmd(mc, "frostworks loop status");
        if (t == 400) { // overview from above, south of the rig
            playerCmd(mc, "tp @s 11.5 -52 7.5");
            lookTarget = new Vec3(11.5, -60, -6);
        }
        if (t == 460)
            capture(mc, "boucle_vue_ensemble");
        if (t == 470) { // stressometer on the main line
            playerCmd(mc, "tp @s 4.5 -59 1.5");
            lookTarget = new Vec3(4.5, -59.45, -1.6);
        }
        if (t == 520)
            capture(mc, "boucle_stressometre");
        // distillation tower: 2 x 2 x 3, x 11..12, y -59..-57, z -5..-4
        if (t == 530) { // seen from the south-east
            playerCmd(mc, "tp @s 16.5 -58 0.5");
            lookTarget = new Vec3(12.0, -58.2, -4.5);
        }
        if (t == 560)
            capture(mc, "tour_cote");
        if (t == 562) { // low, from the north-west: gas pipe on the west face, pump on the north face
            playerCmd(mc, "tp @s 7.5 -60 -9.5");
            lookTarget = new Vec3(11.0, -59.2, -5.0);
        }
        if (t == 590)
            capture(mc, "tour_raccords");
        if (t == 592) { // goggles on the south face of the tower
            playerCmd(mc, "tp @s 11.9 -59.8 -1.2");
            lookTarget = new Vec3(11.9, -58.0, -3.9);
        }
        if (t == 620)
            capture(mc, "tour_lunettes");
        if (t == 625) { // expander, top face near a corner (away from the value box)
            playerCmd(mc, "tp @s 21.5 -60 -3.5");
            lookTarget = new Vec3(19.0, -59.88, -3.1);
        }
        if (t == 640)
            capture(mc, "boucle_detendeur");
        if (t == 645)
            playerCmd(mc, "frostworks loop status"); // hide the action bar, show the full text in chat
        if (t == 646)
            playerCmd(mc, "frostworks loop status");
        if (t == 650) { // reservoir
            playerCmd(mc, "tp @s 9.5 -59 -10.5");
            lookTarget = new Vec3(11.5, -59.5, -7.5);
        }
        if (t == 700)
            capture(mc, "boucle_reservoir");
        return 720;
    }

    /** overspeed rig in a COLD room, and the witness rig without room. */
    private static int t1(Minecraft mc) {
        BlockPos corner = new BlockPos(2, -61, 2);
        if (t == 30)
            server(mc, level -> {
                com.frostworks.overspeed.OverspeedRig.buildInRoom(level, corner);
                com.frostworks.overspeed.OverspeedRig.buildWitness(level, corner.offset(0, 0, 9));
            });
        if (t == 200)
            server(mc, level -> {
                var room = new com.frostworks.overspeed.OverspeedRig.Layout(corner);
                var witness = new com.frostworks.overspeed.OverspeedRig.Layout(corner.offset(0, 0, 9));
                com.frostworks.overspeed.OverspeedRig.setMotorSpeed(level, room, 160);
                com.frostworks.overspeed.OverspeedRig.setMotorSpeed(level, witness, 160);
            });
        if (t == 220) {
            playerCmd(mc, "tp @s 6.5 -60 6.5");
            lookTarget = new Vec3(6.5, -56.6, 4.95);
        }
        if (t == 280) {
            playerCmd(mc, "frostworks overspeed status");
            capture(mc, "t1_compteur_320");
        }
        if (t == 290)
            lookTarget = new Vec3(6.5, -57.5, 4.5);
        if (t == 340) {
            playerCmd(mc, "frostworks overspeed status");
            capture(mc, "t1_melangeur_320");
        }
        if (t == 350) {
            playerCmd(mc, "tp @s 5.5 -59 16.5");
            lookTarget = new Vec3(3.5, -57.5, 12.5);
        }
        if (t == 420) {
            playerCmd(mc, "frostworks overspeed status");
            capture(mc, "t1_temoin_casse");
        }
        return 440;
    }
}
