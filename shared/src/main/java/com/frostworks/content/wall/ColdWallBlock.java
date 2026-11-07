package com.frostworks.content.wall;

import java.util.EnumMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.frostworks.Frostworks;
import com.frostworks.FwConfig;
import com.simibubi.create.AllTags.AllBlockTags;
import com.simibubi.create.AllTags.AllItemTags;
import com.simibubi.create.content.decoration.encasing.EncasedCTBehaviour;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.SharedProperties;
import com.simibubi.create.foundation.data.TagGen;
import com.tterrag.registrate.builders.BlockBuilder;
import com.tterrag.registrate.util.nullness.NonNullUnaryOperator;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Cold wall: a Create casing (same class as Create's casings), so it gets Create's connected textures. A plain full
 * block, without block entity.
 * <p>
 * Connected texture declared exactly like Create declares a casing ({@code AllSpriteShifts.omni}): OMNIDIRECTIONAL,
 * base texture {@code frostworks:block/cold_wall} (16x16) and sheet {@code frostworks:block/cold_wall_connected}
 * (128x128, 8 x 8 tiles of 16x16).
 * <p>
 * The tint: a dye on a wall colours the whole structure ({@link WallTinting}), as the block state {@link #TINT}.
 * A coloured wall's model has a second, translucent layer (our {@code block/cold_wall_tint/cold_wall_<colour>}
 * sheets, on the same 8 x 8 layout), which shifts with the wall: {@link #TINT_SHIFTS}. The colour is only on the
 * placed block: the item, the drops and the picked block are the plain wall.
 */
public class ColdWallBlock extends WallBase {
    public static final CTSpriteShiftEntry SHIFT = CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL,
            Frostworks.id("block/cold_wall"), Frostworks.id("block/cold_wall_connected"));

    public static final EnumProperty<WallTint> TINT = EnumProperty.create("tint", WallTint.class);

    /** The colour layer of each tint: its lone-wall tile (16x16, cut from the sheet) to our sheet. */
    public static final Map<WallTint, CTSpriteShiftEntry> TINT_SHIFTS = new EnumMap<>(WallTint.class);

    static {
        for (WallTint tint : WallTint.values())
            if (tint != WallTint.NONE) {
                String name = "block/cold_wall_tint/cold_wall_" + tint.getSerializedName();
                TINT_SHIFTS.put(tint, CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL, Frostworks.id(name + "_single"),
                        Frostworks.id(name)));
            }
    }

    public ColdWallBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(TINT, WallTint.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TINT);
    }

    /**
     * Create's {@code BuilderTransformers.casing}, with our connected texture behaviour (the wall's shift and the colour
     * layers'): Create wraps the model once per behaviour registered, so the casing's own must not be added too.
     */
    public static NonNullUnaryOperator<BlockBuilder<ColdWallBlock, CreateRegistrate>> casing() {
        return b -> b.initialProperties(SharedProperties::stone)
                .properties(p -> p.sound(SoundType.WOOD))
                .transform(TagGen.axeOrPickaxe())
                // a lambda, not ColdWallCTBehaviour::new: the behaviour (client classes) loads on the client only
                .onRegister(CreateRegistrate.connectedTextures(() -> new ColdWallCTBehaviour()))
                .onRegister(CreateRegistrate.casingConnectivity((block, cc) -> cc.makeCasing(block, SHIFT)))
                .tag(AllBlockTags.CASING.tag)
                .item()
                .tag(AllItemTags.CASING.tag)
                .build();
    }

    /** The wall's shift for the wall's quads, a colour's shift for its layer's (each layer has its own base sprite). */
    public static class ColdWallCTBehaviour extends EncasedCTBehaviour {
        public ColdWallCTBehaviour() {
            super(SHIFT);
        }

        @Override
        public CTSpriteShiftEntry getShift(BlockState state, Direction direction, @Nullable TextureAtlasSprite sprite) {
            if (sprite != null && state.hasProperty(TINT) && state.getValue(TINT) != WallTint.NONE) {
                CTSpriteShiftEntry layer = TINT_SHIFTS.get(state.getValue(TINT));
                if (layer != null && layer.getOriginal() == sprite)
                    return layer;
            }
            return super.getShift(state, direction, sprite);
        }
    }

    /**
     * A dye: the structure takes its colour (gray takes it away). One dye for the whole structure, none in creative;
     * nothing if it already has that colour, nor if it is bigger than {@code wallTintMaxBlocks} (the player is told).
     */
    @Override
    protected InteractionResult useItem(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                        InteractionHand hand) {
        if (stack.is(net.minecraft.world.item.Items.NAME_TAG))
            return rename(stack, level, pos, player);
        if (!(stack.getItem() instanceof DyeItem dye))
            return InteractionResult.PASS;
        int max = FwConfig.get(FwConfig.WALL_TINT_MAX_BLOCKS);
        WallTinting.Outcome outcome = WallTinting.dye(level, pos, WallTint.of(dye.getDyeColor()), max);
        if (outcome == WallTinting.Outcome.TOO_BIG) {
            if (!level.isClientSide)
                player.displayClientMessage(Component.translatable("frostworks.wall_tint.too_big", max), true);
            return InteractionResult.FAIL;
        }
        if (outcome == WallTinting.Outcome.UNCHANGED)
            return InteractionResult.FAIL;
        level.playSound(player, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1, 1);
        if (!level.isClientSide && !player.getAbilities().instabuild)
            stack.shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * A renamed name tag names the room this wall belongs to (the closed room first, as the goggles).
     * The tag is kept: a room can open and close again, and a name tag is rare. A name tag without a name does nothing.
     */
    private static InteractionResult rename(ItemStack stack, Level level, BlockPos pos, Player player) {
        String name = com.frostworks.platform.Mc.customName(stack);
        if (name == null || name.isBlank())
            return InteractionResult.PASS;
        if (!(level instanceof net.minecraft.server.level.ServerLevel server))
            return InteractionResult.SUCCESS; // the server says whether there was a room
        var room = com.frostworks.compat.display.RoomReading.roomOf(server, pos);
        if (room == null) {
            player.displayClientMessage(Component.translatable("frostworks.room.rename.no_room"), true);
            return InteractionResult.FAIL;
        }
        com.frostworks.room.RoomManager.get(server).rename(room, name);
        player.displayClientMessage(Component.translatable("frostworks.room.rename.done", name), true);
        return InteractionResult.SUCCESS;
    }
}
