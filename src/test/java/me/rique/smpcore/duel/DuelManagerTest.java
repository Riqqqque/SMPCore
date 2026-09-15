package me.rique.smpcore.duel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelManagerTest {

    @Test
    void bossDungeonSpectatorsCanAcceptDuelsWithoutPullingActiveFightersOut() {
        assertFalse(DuelManager.bossStateBlocksDuel(true, false, true));
        assertTrue(DuelManager.bossStateBlocksDuel(true, true, true));
        assertTrue(DuelManager.bossStateBlocksDuel(false, false, true));
        assertFalse(DuelManager.bossStateBlocksDuel(false, false, false));
    }
}
