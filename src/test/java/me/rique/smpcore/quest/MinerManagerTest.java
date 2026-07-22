package me.rique.smpcore.quest;

import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinerManagerTest {
    @Test
    void oreTriplesStackAsAdditiveBonusCopies() {
        assertEquals(0, MinerManager.bonusCopies(false, false));
        assertEquals(2, MinerManager.bonusCopies(true, false));
        assertEquals(2, MinerManager.bonusCopies(false, true));
        assertEquals(4, MinerManager.bonusCopies(true, true));
    }

    @Test
    void miningFeverAcceptsSneakRightClickInAirOrOnBlocks() {
        assertTrue(MinerManager.shouldActivateMiningFever(Action.RIGHT_CLICK_AIR, true));
        assertTrue(MinerManager.shouldActivateMiningFever(Action.RIGHT_CLICK_BLOCK, true));
        assertFalse(MinerManager.shouldActivateMiningFever(Action.LEFT_CLICK_AIR, true));
        assertFalse(MinerManager.shouldActivateMiningFever(Action.RIGHT_CLICK_AIR, false));
        assertFalse(MinerManager.shouldActivateMiningFever(null, true));
    }

    @Test
    void miningFeverReceivesPredictedCancelledAirInteractions() throws NoSuchMethodException {
        EventHandler handler = MinerManager.class
            .getDeclaredMethod("onPickUse", PlayerInteractEvent.class)
            .getAnnotation(EventHandler.class);

        assertFalse(handler.ignoreCancelled());
    }

    @Test
    void cooldownStatusShowsActiveAndReadyTimers() {
        assertEquals(
            "<gold><bold>Mining Fever</bold></gold> <green>ACTIVE 45s</green> <dark_gray>|</dark_gray> <yellow>Ready in 5m 55s</yellow>",
            MinerManager.miningFeverCooldownStatus(1_000L, 46_000L, 356_000L)
        );
        assertEquals(
            "<gold><bold>Mining Fever</bold></gold> <yellow>Ready in 1m 0s</yellow>",
            MinerManager.miningFeverCooldownStatus(1_000L, 500L, 61_000L)
        );
    }
}
