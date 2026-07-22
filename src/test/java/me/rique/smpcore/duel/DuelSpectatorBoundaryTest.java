package me.rique.smpcore.duel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelSpectatorBoundaryTest {

    @Test
    void viewingBubbleAllowsWatchingButRejectsUndergroundAndDistantMovement() {
        assertTrue(DuelManager.spectatorOffsetAllowed(0.0D, 0.0D, 0.0D));
        assertTrue(DuelManager.spectatorOffsetAllowed(29.0D, 8.0D, 0.0D));
        assertFalse(DuelManager.spectatorOffsetAllowed(29.01D, 0.0D, 0.0D));
        assertFalse(DuelManager.spectatorOffsetAllowed(0.0D, -0.26D, 0.0D));
        assertFalse(DuelManager.spectatorOffsetAllowed(0.0D, 8.01D, 0.0D));
        assertFalse(DuelManager.spectatorOffsetAllowed(Double.NaN, 0.0D, 0.0D));
    }
}
