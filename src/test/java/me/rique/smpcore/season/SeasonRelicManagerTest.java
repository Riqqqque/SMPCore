package me.rique.smpcore.season;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonRelicManagerTest {

    @Test
    void playerDamageBonusesRiseWithTheBossArmorPath() {
        assertEquals(0.03, SeasonRelicManager.fullSetPlayerDamageBonus("widow_court"));
        assertEquals(0.03, SeasonRelicManager.fullSetPlayerDamageBonus("tidebound"));
        assertEquals(0.04, SeasonRelicManager.fullSetPlayerDamageBonus("ashen_saint"));
        assertEquals(0.05, SeasonRelicManager.fullSetPlayerDamageBonus("riftwalker"));
        assertEquals(0.05, SeasonRelicManager.fullSetPlayerDamageBonus("crimson_guard"));
        assertEquals(0.06, SeasonRelicManager.fullSetPlayerDamageBonus("eclipse_mantle"));
    }

    @Test
    void unknownOrIncompleteSetsGetNoPlayerDamageBonus() {
        assertEquals(0.0, SeasonRelicManager.fullSetPlayerDamageBonus(null));
        assertEquals(0.0, SeasonRelicManager.fullSetPlayerDamageBonus("unknown"));
    }

    @Test
    void soulImprintNameStaysObfuscatedUntilHeld() {
        assertEquals("<obfuscated>Soul Imprint</obfuscated>", SeasonRelicManager.soulImprintDisplayName(false));
        assertEquals("Soul Imprint", SeasonRelicManager.soulImprintDisplayName(true));
    }

    @Test
    void whetstoneRepairsHalfOfMaximumDurability() {
        assertEquals(781, SeasonRelicManager.whetstoneRepairAmount(1562));
        assertEquals(50, SeasonRelicManager.whetstoneRepairAmount(100));
        assertEquals(1, SeasonRelicManager.whetstoneRepairAmount(1));
    }

    @Test
    void forgedStatsUseCompactReadableNumbers() {
        assertEquals("5", SeasonRelicManager.compactNumber(5.0));
        assertEquals("0.28", SeasonRelicManager.compactNumber(0.28));
        assertEquals("1.5", SeasonRelicManager.compactNumber(1.5));
    }

    @Test
    void confessorLedgerExpiresAfterFourSuccessfulRepairs() {
        int uses = 4;
        uses = SeasonRelicManager.remainingLedgerUses(uses);
        assertEquals(3, uses);
        uses = SeasonRelicManager.remainingLedgerUses(uses);
        uses = SeasonRelicManager.remainingLedgerUses(uses);
        uses = SeasonRelicManager.remainingLedgerUses(uses);
        assertEquals(0, uses);
        assertEquals(0, SeasonRelicManager.remainingLedgerUses(uses));
    }

    @Test
    void delayedRelicActivationRequiresTheSameItemInTheSameHand() {
        assertTrue(SeasonRelicManager.canActivateHeldRelic("saints_ledger", "saints_ledger"));
        assertFalse(SeasonRelicManager.canActivateHeldRelic("saints_ledger", null));
        assertFalse(SeasonRelicManager.canActivateHeldRelic("saints_ledger", "saint_whetstone"));
    }

    @Test
    void untaggedProjectilesCannotBorrowTheCurrentlyHeldRelic() {
        assertTrue(SeasonRelicManager.shouldResolveHeldRelic(true, false));
        assertFalse(SeasonRelicManager.shouldResolveHeldRelic(false, false));
        assertFalse(SeasonRelicManager.shouldResolveHeldRelic(true, true));
    }
}
