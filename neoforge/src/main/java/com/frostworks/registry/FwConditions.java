package com.frostworks.registry;

import com.frostworks.Frostworks;
import com.frostworks.FwCommonConfig;
import com.mojang.serialization.MapCodec;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The recipe conditions of the mod. {@code frostworks:tfg_compat}: the TerraFirmaGreg recipes are on
 * ({@link FwCommonConfig#tfgCompat()}). What needs TerraFirmaCraft only uses {@code mod_loaded tfc}.
 */
public final class FwConditions {
    private FwConditions() {}

    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, Frostworks.MODID);

    static {
        CONDITIONS.register("tfg_compat", () -> TfgCompat.CODEC);
    }

    public static void register(IEventBus modBus) {
        CONDITIONS.register(modBus);
    }

    public record TfgCompat() implements ICondition {
        public static final TfgCompat INSTANCE = new TfgCompat();
        public static final MapCodec<TfgCompat> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public boolean test(IContext context) {
            return FwCommonConfig.tfgCompat();
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return CODEC;
        }
    }
}
