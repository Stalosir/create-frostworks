package com.frostworks.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;

/**
 * Forge 1.20.1: {@code GameTestHelper.makeMockPlayer()} takes no game mode (its player is always creative); the root's
 * tests ask for one. A player of either mode, standing nowhere, as the root's mock players.
 */
final class MockPlayers {
    private MockPlayers() {}

    static Player survival(GameTestHelper helper) {
        return mock(helper, false);
    }

    static Player creative(GameTestHelper helper) {
        return mock(helper, true);
    }

    private static Player mock(GameTestHelper helper, boolean creative) {
        return new Player(helper.getLevel(), BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "test-mock-player")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return creative;
            }
        };
    }
}
