package com.frostworks.registry;

import com.frostworks.Frostworks;
import com.frostworks.FwCommonConfig;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

/**
 * The recipe conditions of the mod. {@code frostworks:tfg_compat}: the TerraFirmaGreg recipes are on
 * ({@link FwCommonConfig#tfgCompat()}). What needs TerraFirmaCraft only uses {@code forge:mod_loaded tfc}.
 */
public final class FwConditions {
    private FwConditions() {}

    public static final ResourceLocation TFG_COMPAT = Frostworks.id("tfg_compat");

    public static void register() {
        CraftingHelper.register(TfgCompat.SERIALIZER);
    }

    public static final class TfgCompat implements ICondition {
        public static final TfgCompat INSTANCE = new TfgCompat();
        public static final IConditionSerializer<TfgCompat> SERIALIZER = new IConditionSerializer<>() {
            @Override
            public void write(JsonObject json, TfgCompat value) {}

            @Override
            public TfgCompat read(JsonObject json) {
                return INSTANCE;
            }

            @Override
            public ResourceLocation getID() {
                return TFG_COMPAT;
            }
        };

        private TfgCompat() {}

        @Override
        public ResourceLocation getID() {
            return TFG_COMPAT;
        }

        @Override
        public boolean test(IContext context) {
            return FwCommonConfig.tfgCompat();
        }
    }
}
