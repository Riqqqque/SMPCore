package me.rique.smpcore.season;

import me.rique.smpcore.util.CustomLoreUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonRelicManagerTest {

    @Test
    void playerDamageBonusesRiseWithTheBossArmorPath() {
        assertEquals(0.03, SeasonRelicManager.armorSetPlayerDamageBonus("widow_court"));
        assertEquals(0.03, SeasonRelicManager.armorSetPlayerDamageBonus("tidebound"));
        assertEquals(0.04, SeasonRelicManager.armorSetPlayerDamageBonus("ashen_saint"));
        assertEquals(0.05, SeasonRelicManager.armorSetPlayerDamageBonus("riftwalker"));
        assertEquals(0.05, SeasonRelicManager.armorSetPlayerDamageBonus("crimson_guard"));
        assertEquals(0.06, SeasonRelicManager.armorSetPlayerDamageBonus("eclipse_mantle"));
    }

    @Test
    void unknownOrIncompleteSetsGetNoPlayerDamageBonus() {
        assertEquals(0.0, SeasonRelicManager.armorSetPlayerDamageBonus(null));
        assertEquals(0.0, SeasonRelicManager.armorSetPlayerDamageBonus("unknown"));
    }

    @Test
    void tierFiveAndHigherSetsGainTheSmallBossDamageBonus() {
        assertEquals(0.0, SeasonRelicManager.armorSetBossDamageBonus("widow_court"));
        assertEquals(0.05, SeasonRelicManager.armorSetBossDamageBonus("tidebound"));
        assertEquals(0.05, SeasonRelicManager.armorSetBossDamageBonus("ashen_saint"));
        assertEquals(0.05, SeasonRelicManager.armorSetBossDamageBonus("riftwalker"));
        assertEquals(0.05, SeasonRelicManager.armorSetBossDamageBonus("crimson_guard"));
        assertEquals(0.05, SeasonRelicManager.armorSetBossDamageBonus("eclipse_mantle"));
        assertEquals(0.0, SeasonRelicManager.armorSetBossDamageBonus(null));
    }

    @Test
    void mixedBossArmorKeepsTheStrongestOffenseBonusWithoutStacking() {
        assertEquals(
            0.06,
            SeasonRelicManager.strongestArmorSetPlayerDamageBonus(List.of("widow_court", "eclipse_mantle"))
        );
        assertEquals(
            0.05,
            SeasonRelicManager.strongestArmorSetBossDamageBonus(List.of("tidebound", "riftwalker", "eclipse_mantle"))
        );
        assertEquals(0.0, SeasonRelicManager.strongestArmorSetPlayerDamageBonus(List.of()));
        assertEquals(0.0, SeasonRelicManager.strongestArmorSetBossDamageBonus(null));
    }

    @Test
    void requiredBossArmorAllowsFourQualifiedMixedPieces() {
        assertTrue(SeasonRelicManager.meetsRequiredArmorTier(0, List.of()));
        assertTrue(SeasonRelicManager.meetsRequiredArmorTier(5, List.of(5, 6, 7, 10)));
        assertFalse(SeasonRelicManager.meetsRequiredArmorTier(5, List.of(5, 6, 7)));
        assertFalse(SeasonRelicManager.meetsRequiredArmorTier(5, List.of(5, 6, 3, 10)));
        assertFalse(SeasonRelicManager.meetsRequiredArmorTier(5, List.of(5, 6, 7, Integer.MAX_VALUE)));
        assertFalse(SeasonRelicManager.meetsRequiredArmorTier(5, null));
    }

    @Test
    void bossWardRequiresFourMatchingPieces() {
        assertEquals(
            0.0,
            SeasonRelicManager.matchingSetBossDamageReduction(CustomLoreUtil.Rarity.LEGENDARY, false)
        );
        assertEquals(
            0.34,
            SeasonRelicManager.matchingSetBossDamageReduction(CustomLoreUtil.Rarity.LEGENDARY, true)
        );
        assertEquals(
            0.40,
            SeasonRelicManager.matchingSetBossDamageReduction(CustomLoreUtil.Rarity.MYTHIC, true)
        );
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
    void veilRecipesAcceptValidUsedWeaponsAndArmor() {
        assertTrue(SeasonRelicManager.acceptsRecipeIngredientDamage(0));
        assertTrue(SeasonRelicManager.acceptsRecipeIngredientDamage(1));
        assertTrue(SeasonRelicManager.acceptsRecipeIngredientDamage(1_500));
        assertFalse(SeasonRelicManager.acceptsRecipeIngredientDamage(-1));
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
    void ledgerPreventsTheOppositeHandArmorFromAutoEquipping() {
        assertTrue(SeasonRelicManager.shouldBlockLedgerEquipmentSwap("saints_ledger", null));
        assertTrue(SeasonRelicManager.shouldBlockLedgerEquipmentSwap(null, "saints_ledger"));
        assertFalse(SeasonRelicManager.shouldBlockLedgerEquipmentSwap("saints_whetstone", null));
        assertFalse(SeasonRelicManager.shouldBlockLedgerEquipmentSwap(null, null));
    }

    @Test
    void untaggedProjectilesCannotBorrowTheCurrentlyHeldRelic() {
        assertTrue(SeasonRelicManager.shouldResolveHeldRelic(true, false));
        assertFalse(SeasonRelicManager.shouldResolveHeldRelic(false, false));
        assertFalse(SeasonRelicManager.shouldResolveHeldRelic(true, true));
    }

    @Test
    void bossMechanicsCannotConvertLethalDamageIntoExtremeArmorWear() {
        assertEquals(1, SeasonRelicManager.protectedBossDurabilityDamage(500, true, false));
        assertEquals(1, SeasonRelicManager.protectedBossDurabilityDamage(500, true, true));
        assertEquals(0, SeasonRelicManager.protectedBossDurabilityDamage(0, true, true));
    }

    @Test
    void bossArmorTakesHalfDurabilityWearFromOrdinaryBossHits() {
        assertEquals(5, SeasonRelicManager.protectedBossDurabilityDamage(10, false, true));
        assertEquals(3, SeasonRelicManager.protectedBossDurabilityDamage(5, false, true));
        assertEquals(1, SeasonRelicManager.protectedBossDurabilityDamage(1, false, true));
        assertEquals(10, SeasonRelicManager.protectedBossDurabilityDamage(10, false, false));
    }

    @Test
    void craftableSeasonalSupportItemsAppearWithUtilityRelics() {
        assertEquals(
            SeasonRelicManager.RelicCategory.UTILITIES,
            SeasonRelicManager.supportRelicCategory(true)
        );
        assertEquals(
            SeasonRelicManager.RelicCategory.MATERIALS,
            SeasonRelicManager.supportRelicCategory(false)
        );
    }

    @Test
    void veilWeaponTiersFollowTheBossPath() {
        assertEquals(1, SeasonRelicManager.weaponTier("oathbreaker_mattock"));
        assertEquals(2, SeasonRelicManager.weaponTier("cindershard_dagger"));
        assertEquals(5, SeasonRelicManager.weaponTier("tidebreaker"));
        assertEquals(8, SeasonRelicManager.weaponTier("hollowsong_bow"));
        assertEquals(10, SeasonRelicManager.weaponTier("starfall_mace"));
        assertEquals(50, SeasonRelicManager.weaponTier("unknown"));
    }

    @Test
    void everyBossTierMapsToTheNextAvailableArmorSet() {
        assertEquals("widow_court", SeasonRelicManager.bossArmorRewardSet(1));
        assertEquals("widow_court", SeasonRelicManager.bossArmorRewardSet(2));
        assertEquals("widow_court", SeasonRelicManager.bossArmorRewardSet(3));
        assertEquals("tidebound", SeasonRelicManager.bossArmorRewardSet(4));
        assertEquals("tidebound", SeasonRelicManager.bossArmorRewardSet(5));
        assertEquals("ashen_saint", SeasonRelicManager.bossArmorRewardSet(6));
        assertEquals("riftwalker", SeasonRelicManager.bossArmorRewardSet(7));
        assertEquals("crimson_guard", SeasonRelicManager.bossArmorRewardSet(8));
        assertEquals("crimson_guard", SeasonRelicManager.bossArmorRewardSet(9));
        assertEquals("eclipse_mantle", SeasonRelicManager.bossArmorRewardSet(10));
        assertNull(SeasonRelicManager.bossArmorRewardSet(11));
    }
}
