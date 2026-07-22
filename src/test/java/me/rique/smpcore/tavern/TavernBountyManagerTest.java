package me.rique.smpcore.tavern;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TavernBountyManagerTest {

    @Test
    void requestedMiningCurrenciesRequireTheirExactSmeltedMaterial() {
        assertTrue(TavernBountyManager.matchesRequestedOffer("iron", Material.IRON_INGOT, null, false));
        assertTrue(TavernBountyManager.matchesRequestedOffer("gold", Material.GOLD_INGOT, null, false));
        assertTrue(TavernBountyManager.matchesRequestedOffer("diamond", Material.DIAMOND, null, false));
        assertTrue(TavernBountyManager.matchesRequestedOffer("netherite", Material.NETHERITE_INGOT, null, false));

        assertFalse(TavernBountyManager.matchesRequestedOffer("iron", Material.RAW_IRON, null, false));
        assertFalse(TavernBountyManager.matchesRequestedOffer("gold", Material.RAW_GOLD, null, false));
        assertFalse(TavernBountyManager.matchesRequestedOffer("netherite", Material.NETHERITE_SCRAP, null, false));
    }

    @Test
    void allSeasonOrbsAndTheMysticsOrbAreAccepted() {
        assertTrue(TavernBountyManager.matchesRequestedOffer("orb", Material.ECHO_SHARD, "warden_lure_orb", false));
        assertTrue(TavernBountyManager.matchesRequestedOffer("orb", Material.ENDER_EYE, "veilshift_orb", false));
        assertTrue(TavernBountyManager.matchesRequestedOffer("orb", Material.EXPERIENCE_BOTTLE, "runebloom_orb", false));
        assertTrue(TavernBountyManager.matchesRequestedOffer("orb", Material.ENDER_PEARL, null, true));

        assertFalse(TavernBountyManager.matchesRequestedOffer("orb", Material.ENDER_EYE, null, false));
        assertFalse(TavernBountyManager.matchesRequestedOffer("orb", Material.END_CRYSTAL, "soul_imprint", false));
    }

    @Test
    void soulImprintSelectionOnlyAcceptsTheManagedRelic() {
        assertTrue(TavernBountyManager.matchesRequestedOffer("soul_imprint", Material.END_CRYSTAL, "soul_imprint", false));
        assertFalse(TavernBountyManager.matchesRequestedOffer("soul_imprint", Material.END_CRYSTAL, null, false));
        assertFalse(TavernBountyManager.matchesRequestedOffer("soul_imprint", Material.END_CRYSTAL, "other_relic", false));
    }

    @Test
    void generalHeldItemOptionStillSupportsOtherRewards() {
        assertTrue(TavernBountyManager.matchesRequestedOffer("any", Material.COAL, null, false));
        assertFalse(TavernBountyManager.matchesRequestedOffer("any", Material.AIR, null, false));
        assertFalse(TavernBountyManager.matchesRequestedOffer("unknown", Material.DIAMOND, null, false));
    }

    @Test
    void cancelConfirmationIsNeverParsedAsABountyUuid() {
        UUID bountyId = UUID.randomUUID();

        assertEquals(bountyId, TavernBountyManager.cancelTargetId("bounty:cancel:" + bountyId));
        assertNull(TavernBountyManager.cancelTargetId("bounty:cancel:confirm"));
        assertNull(TavernBountyManager.cancelTargetId("bounty:cancel:not-a-uuid"));
        assertNull(TavernBountyManager.cancelTargetId("close"));
    }

    @Test
    void activeBountyPagesNeverDropEntries() {
        assertEquals(1, TavernBountyManager.pageCount(0));
        assertEquals(1, TavernBountyManager.pageCount(45));
        assertEquals(2, TavernBountyManager.pageCount(46));
        assertEquals(3, TavernBountyManager.pageCount(135));
    }

    @Test
    void pageActionsStayInsideKnownReadOnlyAndBoardViews() {
        assertEquals("bounty:list:page:0", TavernBountyManager.pageAction("list", -1));
        assertEquals("bounty:mine:page:2", TavernBountyManager.pageAction("mine", 2));
        assertEquals("bounty:view:page:3", TavernBountyManager.pageAction("view", 3));
        assertEquals("bounty:list:page:1", TavernBountyManager.pageAction("unknown", 1));
    }
}
