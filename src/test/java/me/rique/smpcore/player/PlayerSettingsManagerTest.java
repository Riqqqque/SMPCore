package me.rique.smpcore.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSettingsManagerTest {

    @Test
    void storedTogglesDefaultOnAndOnlyZeroDisablesThem() {
        assertTrue(PlayerSettingsManager.storedToggleEnabled(null));
        assertTrue(PlayerSettingsManager.storedToggleEnabled((byte) 1));
        assertTrue(PlayerSettingsManager.storedToggleEnabled((byte) -1));
        assertFalse(PlayerSettingsManager.storedToggleEnabled((byte) 0));
    }

    @Test
    void protectedDropPickupLockUsesTheFullRemainingWindow() {
        long now = 1_000L;
        long unlockAt = now + 3_000L;

        assertTrue(PlayerSettingsManager.protectedGroundPickupLocked(unlockAt, now));
        assertEquals(60, PlayerSettingsManager.protectedGroundPickupDelayTicks(0, unlockAt, now));
        assertEquals(80, PlayerSettingsManager.protectedGroundPickupDelayTicks(80, unlockAt, now));
    }

    @Test
    void protectedDropPickupLockExpiresAtTheBoundary() {
        long now = 4_000L;

        assertFalse(PlayerSettingsManager.protectedGroundPickupLocked(null, now));
        assertFalse(PlayerSettingsManager.protectedGroundPickupLocked(now, now));
        assertEquals(0, PlayerSettingsManager.protectedGroundPickupDelayTicks(0, now, now));
    }
}
