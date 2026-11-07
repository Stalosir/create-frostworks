package com.frostworks.client.ponder;

import com.frostworks.Frostworks;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * The mod's Ponder scenes (the "W" key on an item), added to Ponder's index from the client setup, as
 * Create adds its own. Schematics are in {@code assets/frostworks/ponder/<path>.nbt}, the texts in the lang files under
 * {@code frostworks.ponder.<scene>.header} and {@code .text_N}.
 * <p>
 * The two cold room scenes, on one schematic. Ponder links scenes to items: the belt passage, which
 * is no item, cannot be linked.
 */
public class FwPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return Frostworks.MODID;
    }

    /** The speed labels of the gears. */
    @Override
    public void registerSharedText(SharedTextRegistrationHelper helper) {
        helper.registerSharedText("rpm", "%1$s RPM");
        helper.registerSharedText("bonus", "+%1$s %%"); // the chilled ores
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        // The machines, each on its block (registered first: on the vent, its own scene comes first)
        helper.forComponents(Frostworks.id("compressor")).addStoryBoard("compressor", MachineScenes::compressor);
        helper.forComponents(Frostworks.id("cryo_infuser")).addStoryBoard("cryo_infuser", MachineScenes::infuser);
        helper.forComponents(Frostworks.id("column_base"), Frostworks.id("column_segment"))
                .addStoryBoard("cryogenic_tower", MachineScenes::tower);
        helper.forComponents(Frostworks.id("reservoir")).addStoryBoard("frost_tank", MachineScenes::frostTank);
        helper.forComponents(Frostworks.id("expander")).addStoryBoard("expander", MachineScenes::expander);
        helper.forComponents(Frostworks.id("vent")).addStoryBoard("cold_vent", MachineScenes::vent);
        // On the cold wall, the first scene comes before the second (registration order)
        helper.forComponents(Frostworks.id("cold_wall"), Frostworks.id("vent"), Frostworks.id("insulated_door"),
                        Frostworks.id("shaft_passage"), Frostworks.id("pipe_passage"))
                .addStoryBoard("cold_room", ColdRoomScenes::build);
        helper.forComponents(Frostworks.id("cold_wall"), Frostworks.id("vent"))
                .addStoryBoard("cold_room", ColdRoomScenes::speed);
        // Also on Create's belt, where a player looks for what a belt can do
        helper.forComponents(Frostworks.id("cold_wall"), com.frostworks.platform.Mc.id("create", "belt_connector"))
                .addStoryBoard("belt_rooms", ColdRoomScenes::belt);
        // The chilled ores, on the infuser (the old chilled items are hidden)
        helper.forComponents(Frostworks.id("cryo_infuser")).addStoryBoard("chilled_ores", MachineScenes::chilledOres);
    }
}
