package me.rique.smpcore.item;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstDragonSigilListenerTest {

    @Test
    void onlyTheBoundChampionCanUseTheSigil() {
        UUID owner = UUID.randomUUID();
        assertTrue(FirstDragonSigilListener.isAuthorizedOwner(owner, owner));
        assertFalse(FirstDragonSigilListener.isAuthorizedOwner(owner, UUID.randomUUID()));
        assertFalse(FirstDragonSigilListener.isAuthorizedOwner(null, owner));
    }

    @Test
    void cooldownTextRoundsUpInsteadOfShowingReadyEarly() {
        assertEquals("1s", FirstDragonSigilListener.formatRemaining(1L));
        assertEquals("2s", FirstDragonSigilListener.formatRemaining(1_001L));
        assertEquals("3m 0s", FirstDragonSigilListener.formatRemaining(180_000L));
    }

    @Test
    void shockwaveNeverTargetsPlayersPassiveMobsOrBosses() {
        assertTrue(FirstDragonSigilListener.shouldBlast(true, false));
        assertFalse(FirstDragonSigilListener.shouldBlast(false, false));
        assertFalse(FirstDragonSigilListener.shouldBlast(true, true));
    }

    @Test
    void fallProtectionExpiresAtItsExactDeadline() {
        assertTrue(FirstDragonSigilListener.isFallProtected(10_001L, 10_000L));
        assertFalse(FirstDragonSigilListener.isFallProtected(10_000L, 10_000L));
        assertFalse(FirstDragonSigilListener.isFallProtected(9_999L, 10_000L));
    }
}
