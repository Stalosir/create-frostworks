package com.frostworks.command;

import java.util.List;
import java.util.Locale;

import com.frostworks.FwConfig;
import com.frostworks.network.FwNetwork;
import com.frostworks.network.RoomInfoPayload;
import com.frostworks.energy.LoopLedger;
import com.frostworks.energy.LoopRig;
import com.frostworks.energy.LoopStatus;
import com.frostworks.overspeed.Overspeed;
import com.frostworks.overspeed.OverspeedRig;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.frostworks.room.Room;
import com.frostworks.room.RoomManager;
import com.frostworks.room.RoomText;
import com.frostworks.room.RoomWalls;
import com.frostworks.room.TestRoomBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * /frostworks testroom [vents] : builds the 6 x 4 x 4 test room in front of the player (vents get free gas).
 * /frostworks room             : state of the room the player looks at (or stands in).
 * /frostworks stressmode <mode>: switches the compressor stress mode (TOTAL / PER_RPM) for tests.
 */
public final class FrostworksCommands {
    private FrostworksCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("frostworks")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("testroom")
                        .executes(c -> testRoom(c, 6))
                        .then(Commands.argument("vents", IntegerArgumentType.integer(0, TestRoomBuilder.MAX_VENTS))
                                .executes(c -> testRoom(c, IntegerArgumentType.getInteger(c, "vents")))))
                .then(Commands.literal("room").executes(FrostworksCommands::room)
                        .then(Commands.literal("rescan").executes(FrostworksCommands::roomRescan)))
                .then(Commands.literal("loop")
                        .then(Commands.literal("rig").executes(FrostworksCommands::loopRig))
                        .then(Commands.literal("status").executes(FrostworksCommands::loopStatus)))
                .then(Commands.literal("infusion")
                        .then(Commands.literal("rig").executes(FrostworksCommands::infusionRig)))
                .then(Commands.literal("overspeed")
                        .then(Commands.literal("rig").executes(FrostworksCommands::overspeedRig))
                        .then(Commands.literal("status").executes(FrostworksCommands::overspeedStatus)))
                .then(Commands.literal("stressmode")
                        .then(Commands.literal("TOTAL").executes(c -> stressMode(c, FwConfig.StressMode.TOTAL)))
                        .then(Commands.literal("PER_RPM").executes(c -> stressMode(c, FwConfig.StressMode.PER_RPM)))));
    }

    private static int testRoom(CommandContext<CommandSourceStack> c, int vents) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        BlockPos corner = TestRoomBuilder.cornerInFront(player.blockPosition(), player.getDirection());
        TestRoomBuilder.build(player.serverLevel(), corner, vents);
        c.getSource().sendSuccess(() -> Component.translatable("frostworks.command.testroom", vents,
                corner.getX(), corner.getY(), corner.getZ()), true);
        return 1;
    }

    private static int room(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        RoomManager manager = RoomManager.get(level);
        HitResult hit = player.pick(20, 1, false);
        RoomInfoPayload info = null;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockState state = level.getBlockState(blockHit.getBlockPos());
            if (RoomWalls.isWall(state))
                info = FwNetwork.describe(level, blockHit.getBlockPos(), state);
            else {
                Room room = manager.roomAt(blockHit.getBlockPos());
                if (room != null)
                    info = FwNetwork.describe(level, blockHit.getBlockPos(), state);
            }
        }
        if (info == null || info.rooms().isEmpty() && info.vent() == null) {
            BlockPos feet = player.blockPosition();
            info = FwNetwork.describe(level, feet, level.getBlockState(feet));
        }
        List<MutableComponent> lines = RoomText.lines(info);
        lines.forEach(line -> c.getSource().sendSuccess(() -> line, false));
        String stats = String.format(Locale.ROOT, "%d vents, %d rooms, %d scans, last scan %d cells in %.3f ms",
                manager.ventCount(), manager.rooms().size(), manager.scansDone(), manager.lastScanVolume(),
                manager.lastScanNanos() / 1e6);
        c.getSource().sendSuccess(() -> Component.literal(stats), false);
        for (Room room : manager.roomsAround(info.pos()))
            c.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    " room #%d: scan %.3f ms over %d tick(s)", room.id, room.scanNanos / 1e6, room.scanTicks)), false);
        return 1;
    }

    /** Searches every room of the level again, on request (numbers are kept). */
    private static int roomRescan(CommandContext<CommandSourceStack> c) {
        RoomManager manager = RoomManager.get(c.getSource().getLevel());
        int n = manager.rooms().size();
        List.copyOf(manager.rooms()).forEach(manager::markDirty);
        c.getSource().sendSuccess(() -> Component.translatable("frostworks.command.room.rescan", n), true);
        return n;
    }

    /** Builds the infusion rig 2 blocks east of the player (look east). Nothing else is started. */
    private static int infusionRig(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        BlockPos corner = player.blockPosition().offset(2, -1, 0);
        com.frostworks.content.infuser.InfusionRig.build(player.serverLevel(), corner);
        c.getSource().sendSuccess(() -> Component.translatable("frostworks.command.infusion.rig", corner.getX(),
                corner.getY(), corner.getZ()), true);
        return 1;
    }

    /**
     * Builds the loop north-east of the player (motor 2 blocks east and 2 blocks north, the rest further north and
     * east): look north. Nothing else is started.
     */
    private static int loopRig(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        BlockPos origin = player.blockPosition().offset(2, 0, -2);
        int rpm = 64;
        LoopRig.build(player.serverLevel(), origin, rpm, 1024, 50);
        BlockPos o = origin;
        c.getSource().sendSuccess(() -> Component.translatable("frostworks.command.loop.rig", o.getX(), o.getY(),
                o.getZ(), rpm), true);
        return 1;
    }

    private static int loopStatus(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        boolean on = LoopStatus.toggle(player);
        c.getSource().sendSuccess(() -> LoopStatus.text(
                LoopLedger.around(player.serverLevel(), player.blockPosition(), LoopStatus.RADIUS)), false);
        c.getSource().sendSuccess(() -> Component.translatable(on ? "frostworks.command.loop.status.on"
                : "frostworks.command.loop.status.off"), false);
        return 1;
    }

    private static int overspeedRig(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        // Room in front of the player, motor side towards the player's left, witness rig next to it
        BlockPos corner = TestRoomBuilder.cornerInFront(player.blockPosition(), player.getDirection()).offset(2, 0, 0);
        OverspeedRig.buildInRoom(level, corner);
        BlockPos witness = OverspeedRig.witnessCorner(corner);
        OverspeedRig.buildWitness(level, witness);
        c.getSource().sendSuccess(() -> Component.translatable("frostworks.command.overspeed.rig", corner.getX(),
                corner.getY(), corner.getZ(), witness.getX(), witness.getY(), witness.getZ()), true);
        return 1;
    }

    private static int overspeedStatus(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        HitResult hit = player.pick(20, 1, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK
                || !(level.getBlockEntity(blockHit.getBlockPos()) instanceof KineticBlockEntity be)) {
            c.getSource().sendFailure(Component.translatable("frostworks.command.overspeed.no_target"));
            return 0;
        }
        BlockPos pos = blockHit.getBlockPos();
        Overspeed.Status s = Overspeed.status(level, pos);
        Room room = RoomManager.get(level).roomAt(pos);
        Component roomText = room == null ? Component.translatable("frostworks.command.overspeed.no_room")
                : Component.translatable("frostworks.goggles.room", room.id).append(" ").append(RoomText.status(room.status()));
        String speeds = String.format(Locale.ROOT, "%.1f / %.1f", be.getSpeed(), be.getTheoreticalSpeed());
        c.getSource().sendSuccess(() -> Component.translatable("frostworks.command.overspeed.status",
                level.getBlockState(pos).getBlock().getName(), speeds, s.effectiveLimit(), Overspeed.createLimit(),
                roomText, Component.translatable(s.candidate() ? "frostworks.command.overspeed.tagged"
                        : "frostworks.command.overspeed.untagged"),
                s.granted() ? String.format(Locale.ROOT, "%.1f s", s.ticksSinceCold() / 20f) : "-"), false);
        return 1;
    }

    private static int stressMode(CommandContext<CommandSourceStack> c, FwConfig.StressMode mode) {
        FwConfig.STRESS_MODE.set(mode);
        c.getSource().sendSuccess(() -> Component.translatable("frostworks.command.stressmode", mode.name()), true);
        return 1;
    }
}
