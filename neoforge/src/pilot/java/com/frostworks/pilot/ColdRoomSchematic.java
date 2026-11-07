package com.frostworks.pilot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.frostworks.content.door.InsulatedDoorBlock;
import com.frostworks.content.vent.VentBlock;
import com.frostworks.registry.FwBlocks;
import com.frostworks.registry.FwFluids;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Builds the cold room of the two Ponder scenes in the pilot's world and saves it as the scene's
 * schematic ({@code assets/frostworks/ponder/cold_room.nbt}). Building it in a real world gives Create's blocks their
 * true states and block entity data (belt chain, pipe connections, tank contents).
 * <p>
 * Coordinates of the schematic (the Ponder camera looks from the x = 0, z = 0 corner: north and west faces are seen):
 * <ul>
 * <li>snow plate 11 x 11 at y = 0; room of cold walls x 3..8, y 1..6, z 3..8, inside 4 x 4 x 4 = 64 cells
 * (one block higher);</li>
 * <li>three gas vents in the roof (4..6,6,6), fed by a pipe (glass on its straight runs) from the compressor (1,1,7)
 * outside, driven by a shaft and a creative motor; a Frosium vent (4,6,4) fed from a frost tank of Frosium
 * (1,1,4) by a mechanical pump (1,2,4), its cogwheel (2,2,4) and motor (2,1,4), on the compressor's side;</li>
 * <li>insulated door in the west wall (3,2..3,5);</li>
 * <li>through simple holes: a shaft in the east wall (8,2,4), a pipe in the east wall (8,3,7), a belt in the south
 * wall (4,2,8);</li>
 * <li>inside: basin (6,2,5) and mixer two blocks above it (6,4,5), small cogwheel (6,5,5), large cogwheel (7,5,4),
 * creative motor (7,4,4), in the front row.</li>
 * </ul>
 */
public final class ColdRoomSchematic {
    private ColdRoomSchematic() {}

    public static final Vec3i SIZE = new Vec3i(11, 8, 11);
    /** The roof, one block higher (inside 4 x 4 x 4, room for a mixer over its basin). */
    static final int ROOF = 6;

    public static void build(ServerLevel level, BlockPos o) {
        for (int x = 0; x < SIZE.getX(); x++)
            for (int y = 0; y < SIZE.getY(); y++)
                for (int z = 0; z < SIZE.getZ(); z++)
                    level.setBlock(o.offset(x, y, z), y == 0 ? Blocks.SNOW_BLOCK.defaultBlockState()
                            : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        BlockState wall = FwBlocks.COLD_WALL.get().defaultBlockState();
        for (int x = 3; x <= 8; x++)
            for (int y = 1; y <= ROOF; y++)
                for (int z = 3; z <= 8; z++)
                    if (x == 3 || x == 8 || y == 1 || y == ROOF || z == 3 || z == 8)
                        set(level, o, x, y, z, wall);

        // Vents, pipes, compressor, frost tank
        BlockState vent = FwBlocks.VENT.get().defaultBlockState().setValue(VentBlock.FACING, Direction.DOWN);
        // Three gas vents for the 64 cells of the raised room (24 each), one of Frosium (64)
        set(level, o, 4, ROOF, 6, vent);
        set(level, o, 5, ROOF, 6, vent);
        set(level, o, 6, ROOF, 6, vent);
        set(level, o, 4, ROOF, 4, vent);
        set(level, o, 1, 1, 9, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING,
                Direction.NORTH));
        set(level, o, 1, 1, 8, shaft(Direction.Axis.Z));
        set(level, o, 1, 1, 7, FwBlocks.COMPRESSOR.get().defaultBlockState().setValue(PumpBlock.FACING,
                Direction.NORTH));
        // The straight runs of the gas line in glass, to see the gas go (as Create's pump scenes do)
        pipe(level, o, 1, 1, 6);
        for (int y = 2; y <= ROOF; y++)
            set(level, o, 1, y, 6, glass(Direction.Axis.Y));
        pipe(level, o, 1, ROOF + 1, 6);
        for (int x = 2; x <= 3; x++)
            set(level, o, x, ROOF + 1, 6, glass(Direction.Axis.X));
        for (int x = 4; x <= 6; x++)
            pipe(level, o, x, ROOF + 1, 6);
        // The Frosium on the same side as the compressor (x = 1), nothing in front of the room
        set(level, o, 1, 1, 4, FwBlocks.RESERVOIR.get().defaultBlockState());
        // The tank pushes nothing: a mechanical pump (driven by a cogwheel and a motor) lifts the Frosium
        set(level, o, 1, 2, 4, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.UP));
        set(level, o, 2, 2, 4, AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,
                Direction.Axis.Y));
        set(level, o, 2, 1, 4, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING,
                Direction.UP));
        for (int y = 3; y <= ROOF; y++)
            set(level, o, 1, y, 4, glass(Direction.Axis.Y));
        pipe(level, o, 1, ROOF + 1, 4);
        for (int x = 2; x <= 3; x++)
            set(level, o, x, ROOF + 1, 4, glass(Direction.Axis.X));
        pipe(level, o, 4, ROOF + 1, 4);
        if (level.getBlockEntity(o.offset(1, 1, 4)) instanceof FluidTankBlockEntity tank)
            tank.getTankInventory().fill(new FluidStack(FwFluids.frosium(), 6000),
                    IFluidHandler.FluidAction.EXECUTE);

        // Door in the west wall
        BlockState door = FwBlocks.INSULATED_DOOR.get().defaultBlockState()
                .setValue(InsulatedDoorBlock.FACING, Direction.EAST);
        set(level, o, 3, 2, 5, door.setValue(InsulatedDoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, o, 3, 3, 5, door.setValue(InsulatedDoorBlock.HALF, DoubleBlockHalf.UPPER));

        // Through simple holes: shaft and pipe (east wall), belt (south wall)
        for (int x = 7; x <= 9; x++) {
            set(level, o, x, 2, 4, shaft(Direction.Axis.X));
            pipe(level, o, x, 3, 7);
        }
        set(level, o, 4, 2, 6, shaft(Direction.Axis.X));
        set(level, o, 4, 2, 10, shaft(Direction.Axis.X));
        BeltConnectorItem.createBelts(level, o.offset(4, 2, 6), o.offset(4, 2, 10));

        // The machine inside: motor -> large cogwheel -> small cogwheel (x2) -> mixer over a basin. The
        // mixer two blocks above the basin, as in Create (one block of air between them)
        set(level, o, 6, 2, 5, AllBlocks.BASIN.getDefaultState());
        set(level, o, 6, 4, 5, AllBlocks.MECHANICAL_MIXER.getDefaultState());
        set(level, o, 6, 5, 5, AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,
                Direction.Axis.Y));
        set(level, o, 7, 5, 4, AllBlocks.LARGE_COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,
                Direction.Axis.Y));
        set(level, o, 7, 4, 4, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING,
                Direction.UP));

        MachineSchematics.stopMotors(level, o, SIZE);
        settlePipes(level, o, SIZE);
    }

    /**
     * Gives every pipe the state Create would give it if a player placed it now, all neighbours
     * there. Placed with its default state, a pipe only learns its connections from neighbour updates, which leave one
     * side out each time (Create's {@code updateShape} ignores the side opposite the change): ends opened while a pipe
     * had one neighbour stayed open toward the air, and the gas leaked there in the scene (open end particles).
     */
    static void settlePipes(ServerLevel level, BlockPos o, Vec3i size) {
        for (int pass = 0; pass < 2; pass++)
            for (BlockPos pos : BlockPos.betweenClosed(o, o.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof FluidPipeBlock pipe) {
                    BlockState settled = pipe.updateBlockState(state, Direction.UP, null, level, pos);
                    if (settled != state)
                        level.setBlock(pos, settled, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
    }

    /** Saves the built room as a structure, the format Ponder loads. */
    public static void save(ServerLevel level, BlockPos o, Path file) throws IOException {
        save(level, o, SIZE, file);
    }

    static void save(ServerLevel level, BlockPos o, Vec3i size, Path file) throws IOException {
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, o, size, false, null);
        CompoundTag tag = template.save(new CompoundTag());
        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(tag, file);
    }

    private static BlockState shaft(Direction.Axis axis) {
        return AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, axis);
    }

    private static BlockState glass(Direction.Axis axis) {
        return AllBlocks.GLASS_FLUID_PIPE.getDefaultState().setValue(BlockStateProperties.AXIS, axis);
    }

    private static void pipe(ServerLevel level, BlockPos o, int x, int y, int z) {
        set(level, o, x, y, z, AllBlocks.FLUID_PIPE.getDefaultState());
    }

    private static void set(ServerLevel level, BlockPos o, int x, int y, int z, BlockState state) {
        level.setBlock(o.offset(x, y, z), state, Block.UPDATE_ALL);
    }
}
