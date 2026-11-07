package com.frostworks.compat.coldsweat;

import com.frostworks.Frostworks;
import com.momosoftworks.coldsweat.api.event.core.init.DefaultTempModifiersEvent;
import com.momosoftworks.coldsweat.api.event.core.registry.TempModifierRegisterEvent;
import com.momosoftworks.coldsweat.api.temperature.modifier.BlockTempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.api.util.placement.Matcher;
import com.momosoftworks.coldsweat.api.util.placement.Mode;
import com.momosoftworks.coldsweat.api.util.placement.Order;
import com.momosoftworks.coldsweat.api.util.placement.Placement;

import net.minecraft.world.entity.player.Player;

/**
 * Cold Sweat (optional): its two events, wired to its bus by each loader ({@code ColdSweatBus}), only when Cold
 * Sweat is loaded: none of these classes is loaded without it.
 */
public final class ColdSweatCompat {
    private ColdSweatCompat() {}

    public static final String MODID = "cold_sweat";

    /** Our modifier's id: Cold Sweat saves it with the player and builds it again from it. */
    public static void onRegister(TempModifierRegisterEvent event) {
        event.register(Frostworks.id("cold_room"), ColdRoomTempModifier::new);
    }

    /**
     * Each time a player joins a level (connection, respawn, another dimension), Cold Sweat gives it its default
     * modifiers again. Ours goes on the WORLD layer after the biome, the elevation and the caves, before the nearby
     * blocks: a campfire in the room still warms. Worked out once a second.
     */
    public static void onDefaults(DefaultTempModifiersEvent event) {
        if (!(event.getEntity() instanceof Player))
            return;
        event.addModifier(Temperature.Trait.WORLD, new ColdRoomTempModifier().tickRate(20),
                Placement.of(Mode.ADD_BEFORE, Order.FIRST, modifier -> modifier instanceof BlockTempModifier)
                        .orElse(Placement.LAST)
                        .noDuplicates(Matcher.SAME_CLASS));
    }
}
