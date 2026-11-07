package com.frostworks.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

/**
 * A mock player of either mode for the shared GameTests (NeoForge's helper takes the mode; Forge's
 * {@code MockPlayers} builds one).
 */
final class MockPlayers {
    private MockPlayers() {}

    static Player survival(GameTestHelper helper) {
        return helper.makeMockPlayer(GameType.SURVIVAL);
    }

    static Player creative(GameTestHelper helper) {
        return helper.makeMockPlayer(GameType.CREATIVE);
    }
}
