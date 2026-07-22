package me.rique.smpcore.home;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeManagerTest {

    @Test
    void onlyAnUnblockedCrossWorldFailureGetsOneRetry() {
        assertTrue(HomeManager.shouldRetryCrossWorldTeleport(true, true, false, false, true));

        assertFalse(HomeManager.shouldRetryCrossWorldTeleport(false, true, false, false, true));
        assertFalse(HomeManager.shouldRetryCrossWorldTeleport(true, false, false, false, true));
        assertFalse(HomeManager.shouldRetryCrossWorldTeleport(true, true, true, false, true));
        assertFalse(HomeManager.shouldRetryCrossWorldTeleport(true, true, false, true, true));
        assertFalse(HomeManager.shouldRetryCrossWorldTeleport(true, true, false, false, false));
    }
}
