package me.rique.smpcore.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BossPotionListenerTest {

    @Test
    void bossBrewStrengthDurationIsEightMinutes() {
        assertEquals(9_600, BossPotionListener.STRENGTH_DURATION_TICKS);
    }
}
