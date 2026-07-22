package me.rique.smpcore.player;

import me.rique.smpcore.quest.OverseerManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabListManagerTest {

    @Test
    void overseerAuthorityRanksUseTheExpectedThresholds() {
        assertEquals(OverseerManager.AuthorityRank.VEILMARKED, OverseerManager.authorityRankFor(0));
        assertEquals(OverseerManager.AuthorityRank.VEILMARKED, OverseerManager.authorityRankFor(-10));
        assertEquals(OverseerManager.AuthorityRank.VEIL_DEPUTY, OverseerManager.authorityRankFor(1));
        assertEquals(OverseerManager.AuthorityRank.VEIL_DEPUTY, OverseerManager.authorityRankFor(2));
        assertEquals(OverseerManager.AuthorityRank.VEIL_MARSHAL, OverseerManager.authorityRankFor(3));
        assertEquals(OverseerManager.AuthorityRank.VEIL_MARSHAL, OverseerManager.authorityRankFor(4));
        assertEquals(OverseerManager.AuthorityRank.SEASON_WARDEN, OverseerManager.authorityRankFor(5));
        assertEquals(OverseerManager.AuthorityRank.SEASON_WARDEN, OverseerManager.authorityRankFor(50));
    }

    @Test
    void higherAuthorityRanksSortFirst() {
        assertEquals(0, TabListManager.authoritySort(OverseerManager.AuthorityRank.SEASON_WARDEN));
        assertEquals(1, TabListManager.authoritySort(OverseerManager.AuthorityRank.VEIL_MARSHAL));
        assertEquals(2, TabListManager.authoritySort(OverseerManager.AuthorityRank.VEIL_DEPUTY));
        assertEquals(3, TabListManager.authoritySort(OverseerManager.AuthorityRank.VEILMARKED));
    }

    @Test
    void baselineAuthorityRankIsNotAppendedToStaffTitles() {
        assertFalse(TabListManager.hasEarnedAuthorityRank(-1));
        assertFalse(TabListManager.hasEarnedAuthorityRank(0));
        assertTrue(TabListManager.hasEarnedAuthorityRank(1));
    }

    @Test
    void tpsDisplayIsClampedAndCompact() {
        assertEquals("20.0", TabListManager.formatTps(20.8D));
        assertEquals("19.7", TabListManager.formatTps(19.66D));
        assertEquals("0.0", TabListManager.formatTps(-1.0D));
    }
}
