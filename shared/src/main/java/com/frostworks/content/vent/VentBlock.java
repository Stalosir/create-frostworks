package com.frostworks.content.vent;

import com.frostworks.registry.FwBlockEntities;
import com.frostworks.room.RoomWalls;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import com.mojang.serialization.MapCodec;

/**
 * Vent: gas comes in through the back (Create pipe), and is diffused out of the front (FACING) into the room.
 * A vent is part of the room's walls. No rotation needed.
 */
public class VentBlock extends DirectionalBlock implements IBE<VentBlockEntity>, IWrenchable {
    /** Minecraft 1.21.1 asks a directional block for its codec; on 1.20.1 the method below is unused. */
    public static final MapCodec<VentBlock> CODEC = com.frostworks.platform.Mc.simpleCodec(VentBlock::new);

    public VentBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * The front faces the player, unless only the other side closes into a room: then it blows into that room.
     * <p>
     * Cause of the rooms always OPEN: a vent set into a wall from outside the room used to face
     * outside, so the room searched from its front was the open air. The player now gets the closed side whatever
     * side of the wall they stand on (a wrench still turns it).
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction towardsPlayer = context.getNearestLookingDirection().getOpposite();
        Direction away = towardsPlayer.getOpposite();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction facing = towardsPlayer;
        if (RoomWalls.closesARoom(level, pos, away) && !RoomWalls.closesARoom(level, pos, towardsPlayer))
            facing = away;
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<VentBlockEntity> getBlockEntityClass() {
        return VentBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends VentBlockEntity> getBlockEntityType() {
        return FwBlockEntities.VENT.get();
    }
}
