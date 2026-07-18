package me.rique.smpcore.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerVisualListenerTest {

    @Test
    void followerInterpolationOverlapsItsUpdatePeriod() {
        assertEquals(1, PlayerVisualListener.smoothTeleportDuration(-5));
        assertEquals(1, PlayerVisualListener.smoothTeleportDuration(0));
        assertEquals(6, PlayerVisualListener.smoothTeleportDuration(5));
        assertEquals(59, PlayerVisualListener.smoothTeleportDuration(100));
        assertEquals(59, PlayerVisualListener.smoothTeleportDuration(Long.MAX_VALUE));
    }

    @Test
    void privateTeamGlowNeverHighlightsEnemiesOrConcealedPlayers() {
        assertTrue(PlayerVisualListener.privateTeamGlowEligible(true, false, true));
        assertFalse(PlayerVisualListener.privateTeamGlowEligible(false, false, true));
        assertFalse(PlayerVisualListener.privateTeamGlowEligible(true, true, true));
        assertFalse(PlayerVisualListener.privateTeamGlowEligible(true, false, false));
    }
}
