package me.rique.smpcore.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinerManagerTest {
    @Test
    void oreTriplesStackAsAdditiveBonusCopies() {
        assertEquals(0, MinerManager.bonusCopies(false, false));
        assertEquals(2, MinerManager.bonusCopies(true, false));
        assertEquals(2, MinerManager.bonusCopies(false, true));
        assertEquals(4, MinerManager.bonusCopies(true, true));
    }
}
