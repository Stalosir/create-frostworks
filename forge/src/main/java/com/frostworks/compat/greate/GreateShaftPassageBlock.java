package com.frostworks.compat.greate;

import java.util.ArrayList;
import java.util.List;

import com.frostworks.content.passage.ShaftPassageBlock;
import com.frostworks.registry.FwBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Greate (Forge 1.20.1 only): the shaft passage made on one of Greate's shafts (a cold wall used on it: this block
 * is an encased variant of every Greate shaft, {@link GreatePassages#commonSetup}). A wall like ours; it keeps the
 * shaft ({@link GreateShaftPassageBlockEntity}). Broken, it gives back the shaft and the cold wall (it has no item of
 * its own: an item would lose the shaft's tier).
 */
public class GreateShaftPassageBlock extends ShaftPassageBlock {
    public GreateShaftPassageBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void handleEncasing(BlockState state, Level level, BlockPos pos, ItemStack heldItem, Player player,
                               InteractionHand hand, BlockHitResult ray) {
        Block shaft = state.getBlock(); // Greate's shaft, before the switch
        super.handleEncasing(state, level, pos, heldItem, player, hand, ray);
        if (level.getBlockEntity(pos) instanceof GreateShaftPassageBlockEntity be)
            be.setShaft(shaft);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>(2);
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof GreateShaftPassageBlockEntity passage && passage.shaft() != null)
            drops.add(new ItemStack(passage.shaft()));
        drops.add(new ItemStack(FwBlocks.COLD_WALL.get()));
        return drops;
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof GreateShaftPassageBlockEntity be && be.shaft() != null)
            return new ItemStack(be.shaft());
        return new ItemStack(FwBlocks.COLD_WALL.get());
    }

    @Override
    public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
        return GreatePassages.BLOCK_ENTITY.get();
    }
}
