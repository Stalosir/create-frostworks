package com.frostworks.platform;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What Minecraft 1.21.1 names or builds differently from 1.20.1, for the shared code. Same class, same
 * methods in common-1.20.1.
 */
public final class Mc {
    private Mc() {}

    /** The namespace of the common tags ("c" since 1.21, "forge" in 1.20.1). */
    public static final String COMMON_TAGS = "c";

    public static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    /** A block position saved with NbtUtils.writeBlockPos under this key (Create reads both of its forms). */
    public static net.minecraft.core.BlockPos readBlockPos(net.minecraft.nbt.CompoundTag tag, String key) {
        return net.createmod.catnip.nbt.NBTHelper.readBlockPos(tag, key);
    }

    /** An id written as text ("namespace:path"). */
    public static ResourceLocation parse(String id) {
        return ResourceLocation.parse(id);
    }

    public static DropExperienceBlock dropExperience(IntProvider xp, BlockBehaviour.Properties properties) {
        return new DropExperienceBlock(xp, properties);
    }

    /** The codec 1.21.1 asks of a block that has properties only (BlockBehaviour.simpleCodec). */
    public static <B extends net.minecraft.world.level.block.Block> com.mojang.serialization.MapCodec<B> simpleCodec(
            java.util.function.Function<BlockBehaviour.Properties, B> factory) {
        return net.minecraft.world.level.block.state.BlockBehaviour.simpleCodec(factory);
    }

    /** A player uses the item in their hand on this block (the block's own answer); true if it took the action. */
    public static boolean useItemOn(BlockState state, net.minecraft.world.item.ItemStack stack,
                                    net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player,
                                    net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        return state.useItemOn(stack, level, player, hand, hit).consumesAction();
    }

    /** A recipe by its id, as the recipe manager holds it. */
    public static java.util.Optional<? extends net.minecraft.world.item.crafting.Recipe<?>> recipe(
            net.minecraft.world.level.Level level, ResourceLocation id) {
        return level.getRecipeManager().byKey(id).map(net.minecraft.world.item.crafting.RecipeHolder::value);
    }

    /** A block entity's tag, with its id and position (as when it is saved). */
    public static net.minecraft.nbt.CompoundTag saveBlockEntity(net.minecraft.world.level.block.entity.BlockEntity be,
                                                                net.minecraft.world.level.Level level) {
        return be.saveWithFullMetadata(level.registryAccess());
    }

    /** A block entity made from such a tag. */
    public static net.minecraft.world.level.block.entity.BlockEntity loadBlockEntity(net.minecraft.core.BlockPos pos,
            BlockState state, net.minecraft.nbt.CompoundTag tag, net.minecraft.world.level.Level level) {
        return net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos, state, tag, level.registryAccess());
    }

    /** A dye's colour, 0xRRGGBB (the wall tint's particles). */
    public static int dyeRgb(net.minecraft.world.item.DyeColor dye) {
        return dye.getTextureDiffuseColor() & 0xFFFFFF;
    }

    /** The name given to an item in an anvil (a name tag's), or null. */
    @org.jetbrains.annotations.Nullable
    public static String customName(net.minecraft.world.item.ItemStack stack) {
        net.minecraft.network.chat.Component name = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        return name == null ? null : name.getString();
    }
}
