package com.frostworks.registry;

import java.util.List;
import java.util.function.Supplier;

import com.frostworks.Frostworks;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public final class FwCreativeTab {
    private FwCreativeTab() {}

    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Frostworks.MODID);

    /** Everything the prototype adds, in display order. */
    private static List<Supplier<? extends ItemLike>> contents() {
        return List.of(FwBlocks.COMPRESSOR, FwBlocks.VENT, FwBlocks.COLD_WALL,
                FwBlocks.INSULATED_DOOR, FwBlocks.COLUMN_BASE,
                FwBlocks.RESERVOIR, FwBlocks.EXPANDER, FwBlocks.CRYO_INFUSER, FwItems.COLD_IRON_SHEET,
                FwItems.COLD_DRIED_KELP, () -> FwFluids.frosiumBucket());
    }

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.frostworks"))
                    .icon(() -> new ItemStack(FwBlocks.COMPRESSOR.get()))
                    .displayItems((params, output) -> {
                        contents().forEach(entry -> output.accept(entry.get()));
                    })
                    .build());

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
