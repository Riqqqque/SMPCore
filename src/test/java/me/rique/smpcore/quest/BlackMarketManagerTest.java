package me.rique.smpcore.quest;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void trophyGlowIsPrivateToOwnerAndDirectTeam() {
        UUID owner = UUID.randomUUID();
        UUID teammate = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();

        assertTrue(BlackMarketManager.canViewerSeeTrophyGlow(owner, owner, null, null));
        assertTrue(BlackMarketManager.canViewerSeeTrophyGlow(teammate, owner, "Wardens", "Wardens"));
        assertFalse(BlackMarketManager.canViewerSeeTrophyGlow(outsider, owner, "Raiders", "Wardens"));
        assertFalse(BlackMarketManager.canViewerSeeTrophyGlow(outsider, owner, null, null));
        assertFalse(BlackMarketManager.canViewerSeeTrophyGlow(outsider, null, "Wardens", "Wardens"));
    }
}
