package com.frostworks.registry;

import static com.frostworks.Frostworks.REGISTRATE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.frostworks.content.cold.ChilledMark;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.entry.ItemEntry;

import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;

/**
 * The chilled ores, one per ore of Minecraft or Create that Create's crushing wheels take (stone,
 * deepslate and Nether), and the chilled raw iron, copper, gold and zinc. Each block copies its base ore (hardness,
 * tool, sound; the experience it drops) and never generates in the world.
 * <p>
 * They gave way to the chilled state ({@link ChilledMark}) on the base ore itself. They stay registered for the
 * worlds of the 1.0, hidden (out of the creative tab, out of JEI and EMI), and turn into their base item, chilled: in a
 * player's inventory ({@link #convert}), by a shapeless recipe (chests, machines), and a placed block drops it (loot
 * tables in the resources). To be removed in a later version.
 */
public final class FwChilledOres {
    private FwChilledOres() {}

    private static final List<BlockEntry<DropExperienceBlock>> BLOCKS = new ArrayList<>();
    private static final List<ItemEntry<Item>> RAWS = new ArrayList<>();
    /** Each old item's base: its ore block's item, or its raw material. */
    private static final Map<String, Supplier<? extends ItemLike>> BASES = new HashMap<>();
    private static Map<Item, Item> byItem;

    /** The base ore's experience when broken without silk touch (vanilla's own values). */
    private static BlockEntry<DropExperienceBlock> ore(String base, Supplier<Block> copy, IntProvider xp) {
        BlockEntry<DropExperienceBlock> entry = REGISTRATE.block("chilled_" + base, p -> com.frostworks.platform.Mc.dropExperience(xp, p))
                .initialProperties(copy::get)
                // a redstone ore glows by its LIT state, which ours has not: no light
                .properties(p -> p.lightLevel(state -> 0))
                .simpleItem()
                .register();
        BLOCKS.add(entry);
        BASES.put("chilled_" + base, copy);
        return entry;
    }

    private static ItemEntry<Item> raw(String base, Supplier<? extends ItemLike> item) {
        ItemEntry<Item> entry = REGISTRATE.item("chilled_" + base, Item::new).register();
        RAWS.add(entry);
        BASES.put("chilled_" + base, item);
        return entry;
    }

    static {
        IntProvider none = ConstantInt.of(0);
        ore("coal_ore", () -> Blocks.COAL_ORE, UniformInt.of(0, 2));
        ore("deepslate_coal_ore", () -> Blocks.DEEPSLATE_COAL_ORE, UniformInt.of(0, 2));
        ore("copper_ore", () -> Blocks.COPPER_ORE, none);
        ore("deepslate_copper_ore", () -> Blocks.DEEPSLATE_COPPER_ORE, none);
        ore("iron_ore", () -> Blocks.IRON_ORE, none);
        ore("deepslate_iron_ore", () -> Blocks.DEEPSLATE_IRON_ORE, none);
        ore("gold_ore", () -> Blocks.GOLD_ORE, none);
        ore("deepslate_gold_ore", () -> Blocks.DEEPSLATE_GOLD_ORE, none);
        ore("redstone_ore", () -> Blocks.REDSTONE_ORE, UniformInt.of(1, 5));
        ore("deepslate_redstone_ore", () -> Blocks.DEEPSLATE_REDSTONE_ORE, UniformInt.of(1, 5));
        ore("lapis_ore", () -> Blocks.LAPIS_ORE, UniformInt.of(2, 5));
        ore("deepslate_lapis_ore", () -> Blocks.DEEPSLATE_LAPIS_ORE, UniformInt.of(2, 5));
        ore("diamond_ore", () -> Blocks.DIAMOND_ORE, UniformInt.of(3, 7));
        ore("deepslate_diamond_ore", () -> Blocks.DEEPSLATE_DIAMOND_ORE, UniformInt.of(3, 7));
        ore("emerald_ore", () -> Blocks.EMERALD_ORE, UniformInt.of(3, 7));
        ore("deepslate_emerald_ore", () -> Blocks.DEEPSLATE_EMERALD_ORE, UniformInt.of(3, 7));
        ore("nether_gold_ore", () -> Blocks.NETHER_GOLD_ORE, UniformInt.of(0, 1));
        ore("nether_quartz_ore", () -> Blocks.NETHER_QUARTZ_ORE, UniformInt.of(2, 5));
        ore("zinc_ore", AllBlocks.ZINC_ORE::get, none);
        ore("deepslate_zinc_ore", AllBlocks.DEEPSLATE_ZINC_ORE::get, none);
        raw("raw_iron", () -> Items.RAW_IRON);
        raw("raw_copper", () -> Items.RAW_COPPER);
        raw("raw_gold", () -> Items.RAW_GOLD);
        raw("raw_zinc", AllItems.RAW_ZINC::get);
    }

    public static List<BlockEntry<DropExperienceBlock>> blocks() {
        return Collections.unmodifiableList(BLOCKS);
    }

    public static List<ItemEntry<Item>> raws() {
        return Collections.unmodifiableList(RAWS);
    }

    /** Every old item, hidden from the creative tab, JEI and EMI. */
    public static List<ItemStack> hidden() {
        List<ItemStack> all = new ArrayList<>();
        BLOCKS.forEach(b -> all.add(new ItemStack(b.get())));
        RAWS.forEach(r -> all.add(new ItemStack(r.get())));
        return all;
    }

    /** The base item of an old chilled item, or null (not one of ours). */
    @Nullable
    public static Item base(Item item) {
        Map<Item, Item> map = byItem;
        if (map == null) { // once the registries are frozen
            map = new HashMap<>();
            for (var b : BLOCKS)
                map.put(b.get().asItem(), BASES.get(b.getId().getPath()).get().asItem());
            for (var r : RAWS)
                map.put(r.get(), BASES.get(r.getId().getPath()).get().asItem());
            byItem = map;
        }
        return map.get(item);
    }

    /** An old chilled stack as its base item, chilled (same count); any other stack as it is. */
    public static ItemStack converted(ItemStack stack) {
        Item base = stack.isEmpty() ? null : base(stack.getItem());
        return base == null ? stack : ChilledMark.mark(new ItemStack(base, stack.getCount()));
    }

    /** Turns every old chilled stack of a player's inventory into its base item, chilled (server side). */
    public static void convert(Inventory inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            ItemStack converted = converted(stack);
            if (converted != stack)
                inventory.setItem(i, converted);
        }
    }

    public static void init() {}
}
