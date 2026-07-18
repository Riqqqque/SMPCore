package me.rique.smpcore.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackMarketManagerTest {

    @Test
    void replacementPricesScaleWithBossProgression() {
        assertEquals(30L, BlackMarketManager.replacementPrice(1));
        assertEquals(120L, BlackMarketManager.replacementPrice(10));
        for (int tier = 2; tier <= 10; tier++) {
            assertTrue(
                BlackMarketManager.replacementPrice(tier) > BlackMarketManager.replacementPrice(tier - 1)
            );
        }
    }

    @Test
    void invalidTiersCannotCreateCheaperReplacements() {
        assertEquals(30L, BlackMarketManager.replacementPrice(0));
        assertEquals(30L, BlackMarketManager.replacementPrice(-10));
    }
}
