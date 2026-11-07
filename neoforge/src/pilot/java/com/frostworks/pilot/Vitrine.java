package com.frostworks.pilot;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * The CurseForge captures: the showcase world of {@link Showcase}, cooled, then one view per image,
 * stills as {@code cf_N.png} and GIF frames as {@code cf_N_000.png}... (one every 2 ticks, 10 per second), all in
 * {@code run-pilote/screenshots}. Run {@code ./gradlew runClientPilote -Pscenario=vitrine}.
 */
final class Vitrine {
    private Vitrine() {}

    /** The room is cold and the mixer at 384 rpm by then ({@link Showcase} reports it at 1900). */
    private static final int READY = 1900;
    private static final int SETTLE = 40;
    private static final int FRAME_TICKS = 2;

    /** name, camera x eyeY z, target x y z (from the showcase origin), frames (0: a still), gui shown. */
    private record Shot(String name, double[] eye, double[] target, int frames, boolean gui) {}

    private static final Shot[] SHOTS = {
            new Shot("cf_1_salle", new double[]{11.3, 2.7, 13.2}, new double[]{16, 2.2, 8}, 60, false),
            new Shot("cf_4_infuseur", new double[]{17.6, 2.7, 11.2}, new double[]{16, 1.6, 9}, 40, false),
            new Shot("cf_2_lunettes", new double[]{15.5, 2.8, 19}, new double[]{16, 2.2, 14}, 0, true),
            new Shot("cf_5_tour", new double[]{29.5, 3.6, 14}, new double[]{25, 2.2, 9}, 0, false),
            new Shot("cf_6_minerais", new double[]{6, 2.1, 24.3}, new double[]{6, 0.9, 20}, 0, false),
            new Shot("cf_8_vue", new double[]{-12, 24, -14}, new double[]{17, 2, 9}, 0, false),
            // last in the world: it opens the room. Outside the west wall, the mixer at 384 rpm behind it
            new Shot("cf_3_casse", new double[]{5.5, 3.9, 7.0}, new double[]{12.5, 3.4, 6.8}, 70, false),
            new Shot("cf_7_ponder", new double[]{15.5, 2.6, 17.5}, new double[]{17, 2, 14}, 60, true)};
    /** The break GIF: the frame at which a hole opens in the west wall, in front of the mixer. */
    private static final int HOLE_FRAME = 10;

    /** The ore showcase on the snow, south-west of the room: base ore below, chilled ore above. */
    private static final String[] ORES = {"iron_ore", "copper_ore", "gold_ore", "diamond_ore", "emerald_ore"};

    static int run(Minecraft mc, int t) {
        // the window may lose focus while it runs: no pause menu, and the server keeps ticking
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen)
            mc.setScreen(null);
        if (t < READY && t != 320) // not at 320: the showcase speeds the mixer up there, before the room is at -20 C
            Showcase.run(mc, t);
        if (t == 25)
            ClientPilot.cmd(mc, "effect clear @a"); // night vision washes the colours out
        if (t == READY) {
            BlockPos o = Showcase.origin();
            for (int i = 0; i < ORES.length; i++) {
                int x = o.getX() + 2 + 2 * i, y = o.getY(), z = o.getZ() + 20;
                ClientPilot.cmd(mc, "setblock " + x + " " + y + " " + z + " minecraft:" + ORES[i]);
                ClientPilot.cmd(mc, "setblock " + x + " " + (y + 1) + " " + z + " frostworks:chilled_" + ORES[i]);
            }
            ClientPilot.cmd(mc, "gamerule sendCommandFeedback false");
            ClientPilot.server(mc, level -> Showcase.motor(level, 13, 3, 6, 192)); // the mixer at 384, the room at -20 C
            ClientPilot.server(mc, Vitrine::entrance);
        }
        int at = READY + 10;
        for (Shot s : SHOTS) {
            int length = SETTLE + Math.max(1, s.frames) * FRAME_TICKS + 10;
            if (t == at)
                place(mc, s);
            if (t == at && s.name.equals("cf_3_casse"))
                ClientPilot.server(mc, level -> {
                    // the drive again, in case it broke while the room was cooling: large cogwheel, then the motor at 192
                    BlockPos o = Showcase.origin();
                    if (!(level.getBlockState(o.offset(13, 4, 6)).getBlock() instanceof com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock))
                        level.setBlockAndUpdate(o.offset(13, 4, 6), com.simibubi.create.AllBlocks.LARGE_COGWHEEL.getDefaultState()
                                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,
                                        net.minecraft.core.Direction.Axis.Y));
                    Showcase.motor(level, 13, 3, 6, 192);
                });
            if (s.name.equals("cf_3_casse") && t == at + SETTLE + (HOLE_FRAME - 1) * FRAME_TICKS)
                ClientPilot.server(mc, level -> System.out.println("PILOT casse before the hole: large cog "
                        + level.getBlockState(Showcase.origin().offset(13, 4, 6)).getBlock()));
            if (t == at && s.name.equals("cf_7_ponder"))
                mc.setScreen(net.createmod.ponder.foundation.ui.PonderUI.of(new net.minecraft.world.item.ItemStack(
                        com.frostworks.registry.FwBlocks.COLD_WALL.get())));
            if (s.name.equals("cf_3_casse") && (t == at + SETTLE + 30 * FRAME_TICKS || t == at + SETTLE + 69 * FRAME_TICKS))
                ClientPilot.server(mc, level -> {
                    BlockPos o = Showcase.origin();
                    var room = com.frostworks.room.RoomManager.get(level).roomAt(o.offset(15, 2, 9));
                    String speeds = "";
                    for (BlockPos p : new BlockPos[]{o.offset(13, 3, 6), o.offset(13, 4, 6), o.offset(12, 4, 7), o.offset(12, 3, 7)})
                        if (level.getBlockEntity(p) instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity k)
                            speeds += level.getBlockState(p).getBlock().getDescriptionId() + "=" + k.getSpeed() + " ";
                    String grants = "?";
                    try {
                        var field = com.frostworks.overspeed.Overspeed.class.getDeclaredField("GRANTS");
                        field.setAccessible(true);
                        grants = String.valueOf(((java.util.Map<?, ?>) field.get(null)).get(level));
                    } catch (ReflectiveOperationException e) {
                        grants = e.toString();
                    }
                    System.out.println("PILOT casse speeds " + speeds + "grants " + grants);
                    System.out.println("PILOT casse: room " + (room == null ? "none" : room.status()) + ", mixer "
                            + level.getBlockState(o.offset(12, 3, 7)) + ", cog " + level.getBlockState(o.offset(12, 4, 7)) + ", grace " + com.frostworks.FwConfig.OVERSPEED_GRACE_SECONDS.get());
                });
            if (t == at + SETTLE + HOLE_FRAME * FRAME_TICKS && s.name.equals("cf_3_casse"))
                ClientPilot.server(mc, level -> {
                    // no grace period: the room stops being cold, and the overspeed rule breaks the mixer at once
                    com.frostworks.FwConfig.OVERSPEED_GRACE_SECONDS.set(0);
                    BlockPos o = Showcase.origin();
                    for (int y = 2; y <= 4; y++)
                        for (int z = 6; z <= 7; z++)
                            level.destroyBlock(o.offset(10, y, z), false);
                });
            int k = t - at - SETTLE;
            if (k >= 0 && k % FRAME_TICKS == 0) {
                int frame = k / FRAME_TICKS;
                if (s.frames == 0 && frame == 0)
                    ClientPilot.capture(mc, s.name);
                else if (frame < s.frames)
                    ClientPilot.capture(mc, String.format("%s_%03d", s.name, frame));
            }
            at += length;
        }
        if (t == at)
            mc.setScreen(null);
        if (t == at)
            mc.options.hideGui = false;
        return at + 10;
    }

    /** The front of the room: steps up to the door, brick pillars and low brick walls, an awning with two lanterns. */
    private static void entrance(net.minecraft.server.level.ServerLevel level) {
        BlockPos o = Showcase.origin();
        var stairs = net.minecraft.world.level.block.Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.NORTH);
        for (int x = 14; x <= 16; x++)
            level.setBlockAndUpdate(o.offset(x, 0, 15), stairs);
        for (int x : new int[]{13, 17})
            for (int y = 0; y <= 4; y++)
                level.setBlockAndUpdate(o.offset(x, y, 15), net.minecraft.world.level.block.Blocks.BRICKS.defaultBlockState());
        for (int x = 10; x <= 20; x++)
            if (x < 13 || x > 17)
                level.setBlockAndUpdate(o.offset(x, 0, 15), net.minecraft.world.level.block.Blocks.BRICK_WALL.defaultBlockState());
        for (int x = 13; x <= 17; x++)
            level.setBlockAndUpdate(o.offset(x, 5, 15), net.minecraft.world.level.block.Blocks.STONE_BRICK_SLAB.defaultBlockState());
        var lantern = net.minecraft.world.level.block.Blocks.LANTERN.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true);
        for (int x : new int[]{14, 16}) {
            level.setBlockAndUpdate(o.offset(x, 4, 15), net.minecraft.world.level.block.Blocks.CHAIN.defaultBlockState());
            level.setBlockAndUpdate(o.offset(x, 3, 15), lantern);
        }
        for (int x : new int[]{12, 18})
            level.setBlockAndUpdate(o.offset(x, 1, 15), net.minecraft.world.level.block.Blocks.POTTED_SPRUCE_SAPLING.defaultBlockState());
    }

    private static void place(Minecraft mc, Shot s) {
        BlockPos o = Showcase.origin();
        mc.options.hideGui = !s.gui;
        mc.gameRenderer.setRenderHand(false); // no hand in front of the goggles view
        // spectator: no hand, no collision with the walls; the goggles shot needs a player who wears them
        ClientPilot.cmd(mc, "gamemode " + (s.gui ? "creative" : "spectator") + " @a");
        ClientPilot.cmd(mc, String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f", o.getX() + s.eye[0],
                o.getY() + s.eye[1] - 1.62, o.getZ() + s.eye[2]));
        ClientPilot.lookTarget = new Vec3(o.getX() + s.target[0], o.getY() + s.target[1], o.getZ() + s.target[2]);
        if (s.gui) {
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        }
    }
}
