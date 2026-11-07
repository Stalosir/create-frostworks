package com.frostworks.gametest;

import com.frostworks.CreateHooks;
import com.frostworks.Frostworks;
import com.frostworks.network.FwNetwork;
import com.frostworks.network.RoomInfoPayload;
import com.frostworks.overspeed.Overspeed;
import com.frostworks.overspeed.OverspeedRig;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The two hooks into Create, and what happens without them. Only Create 6.0.10 is at hand, where both
 * hooks are in: a missing hook is simulated ({@link CreateHooks#simulateMissing}), which gives the mod what another
 * version of Create without the call would give it. In their own batches: the simulation holds for the whole server.
 */
public final class Modif20GameTests {
    private Modif20GameTests() {}

    private static final String SMALL = "empty_16x10x16";

    /** With Create 6.0.10, both hooks are in. */
    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void bothHooksAreReady(GameTestHelper helper) {
        for (CreateHooks.Hook hook : CreateHooks.Hook.values())
            helper.assertTrue(CreateHooks.ready(hook), hook + ": " + System.getProperty(hook.property));
        helper.succeed();
    }

    /**
     * The overspeed hook missing: in a COLD room, a machine past Create's limit breaks as in Create alone (nothing
     * crashes), the overspeed says it is off, and the goggles get the room's limit at Create's with their warning line.
     */
    @GameTest(template = SMALL, timeoutTicks = 400, batch = "hook_missing_overspeed")
    public static void withoutTheOverspeedHookCreatesRuleApplies(GameTestHelper helper) {
        OverspeedRig.Layout rig = OverspeedGameTests.roomRig(helper);
        helper.startSequence()
                .thenWaitUntil(() -> OverspeedGameTests.assertCold(helper, rig))
                .thenExecute(() -> {
                    CreateHooks.simulateMissing(CreateHooks.Hook.OVERSPEED, true);
                    helper.assertTrue(!Overspeed.enabled(), "the overspeed is still on");
                    var level = helper.getLevel();
                    RoomInfoPayload info = FwNetwork.describe(level, rig.passage(), level.getBlockState(rig.passage()));
                    helper.assertTrue(info.overspeedOff(), "the goggles do not say the overspeed is off");
                    helper.assertTrue(info.rooms().stream().allMatch(r -> r.maxRpm() == Overspeed.createLimit()),
                            "the goggles give a limit over Create's");
                    OverspeedRig.setMotorSpeed(level, rig, 160);
                })
                .thenWaitUntil(() -> helper.assertTrue(!OverspeedGameTests.intact(helper, rig),
                        "nothing broke at 320 rpm without the hook"))
                .thenExecute(() -> CreateHooks.simulateMissing(CreateHooks.Hook.OVERSPEED, false))
                .thenSucceed();
    }

}
