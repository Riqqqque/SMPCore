package me.rique.smpcore.boss;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossBalanceTest {

    @Test
    void progressionOrderHasAContinuousDifficultyCurve() {
        var bosses = BossBalance.progression();

        assertEquals("yule_the_minion", bosses.getFirst().bossId());
        assertEquals("corrupted_oathkeeper", bosses.getLast().bossId());
        assertEquals(10, bosses.size());

        double previousHealth = 0.0;
        double previousDamage = 0.0;
        for (int i = 0; i < bosses.size(); i++) {
            BossBalance.Profile boss = bosses.get(i);
            assertEquals(i + 1, boss.tier());
            assertTrue(boss.maxHealth() >= previousHealth, boss.bossId() + " should not lose health versus the prior tier");
            assertTrue(boss.attackDamage() >= previousDamage, boss.bossId() + " should not lose damage versus the prior tier");
            previousHealth = boss.maxHealth();
            previousDamage = boss.attackDamage();
        }
    }

    @Test
    void runebloomWitchSitsBetweenOracleAndWardenWithFivePercentOrbChance() {
        var bosses = BossBalance.progression();

        assertEquals("aurelion_the_rift_seraph", bosses.get(6).bossId());
        assertEquals("morvessa_the_runebloom_witch", bosses.get(7).bossId());
        assertEquals("voralith_the_crimson_warden", bosses.get(8).bossId());
        assertEquals(27.0, bosses.get(7).attackDamage());
        assertEquals(0.05, BossBalance.RUNEBLOOM_WITCH_ORB_DROP_CHANCE);
    }

    @Test
    void asterionAliasesResolveToOneBossWithoutChangingTheSaveId() {
        assertEquals("aurelion_the_rift_seraph", BossIdentity.canonicalId("asterion"));
        assertEquals("aurelion_the_rift_seraph", BossIdentity.canonicalId("Asterion the Rift Oracle"));
        assertEquals("aurelion_the_rift_seraph", BossIdentity.canonicalId("rift-oracle"));
        assertEquals("aurelion_the_rift_seraph", BossIdentity.canonicalId("aurelion"));
        assertEquals(10, BossBalance.progression().size());
    }

    @Test
    void ignoredMechanicsBecomeLethalAcrossTheProgression() {
        assertEquals(0.45, BossBalance.mechanicFailureHealthRatio(1));
        assertEquals(0.72, BossBalance.mechanicFailureHealthRatio(2));
        assertEquals(0.92, BossBalance.mechanicFailureHealthRatio(6));
        assertEquals(0.98, BossBalance.mechanicFailureHealthRatio(8));
        assertEquals(0.98, BossBalance.mechanicFailureHealthRatio(20));

        assertTrue(BossBalance.mechanicHazardHealthRatio(8) > BossBalance.mechanicHazardHealthRatio(2));
        assertTrue(BossBalance.mechanicHazardHealthRatio(10) <= 0.30);
    }

    @Test
    void groupsAddEnoughHealthAndPressureToOffsetExtraDamageDealers() {
        assertEquals(1.0, BossBalance.multiplayerHealthScale(8, 1));
        assertEquals(2.86, BossBalance.multiplayerHealthScale(8, 4), 0.001);
        assertEquals(1.0, BossBalance.multiplayerDamageScale(8, 1));
        assertEquals(1.345, BossBalance.multiplayerDamageScale(8, 4), 0.001);
        assertTrue(BossBalance.multiplayerHealthScale(8, 7) > BossBalance.multiplayerHealthScale(2, 7));
    }

    @Test
    void sharedBossMaterialsScaleWithoutRewardingTokenParticipation() {
        assertEquals(1.0, BossBalance.multiplayerLootScale(1));
        assertEquals(1.25, BossBalance.multiplayerLootScale(2));
        assertEquals(1.75, BossBalance.multiplayerLootScale(4));
        assertEquals(2.0, BossBalance.multiplayerLootScale(5));
        assertEquals(2.0, BossBalance.multiplayerLootScale(20));

        assertFalse(BossBalance.qualifiesForGroupLoot(9.99, 200.0));
        assertFalse(BossBalance.qualifiesForGroupLoot(20.0, 1_000.0));
        assertTrue(BossBalance.qualifiesForGroupLoot(25.0, 1_000.0));
        assertFalse(BossBalance.qualifiesForGroupLoot(Double.NaN, 1_000.0));
    }

    @Test
    void fractionalBossMaterialScalingRoundsFairlyAndNeverChangesSoloLoot() {
        assertEquals(4, BossBalance.scaledBossMaterialAmount(4, 1, 0.0));
        assertEquals(5, BossBalance.scaledBossMaterialAmount(4, 2, 0.99));
        assertEquals(3, BossBalance.scaledBossMaterialAmount(2, 2, 0.49));
        assertEquals(2, BossBalance.scaledBossMaterialAmount(2, 2, 0.50));
        assertEquals(8, BossBalance.scaledBossMaterialAmount(4, 5, 0.99));
        assertEquals(0, BossBalance.scaledBossMaterialAmount(0, 5, 0.0));
    }

    @Test
    void routineAbilitiesEaseWithoutWeakeningFailedMechanicPenalties() {
        assertEquals(0.94, BossBalance.routineAbilityDamageScale(1));
        assertEquals(0.92, BossBalance.routineAbilityDamageScale(6));
        assertEquals(0.90, BossBalance.routineAbilityDamageScale(10));
        assertEquals(0.98, BossBalance.mechanicFailureHealthRatio(10));
    }

    @Test
    void reportedDamageMatchesTheDisplayedBossHealthLoss() {
        assertEquals(100.0, BossBalance.reportedDamage(50.0, 10.0, 100.0, 1_000.0), 0.001);
        assertEquals(20.0, BossBalance.reportedDamage(2.0, 10.0, 100.0, 1_000.0), 0.001);
        assertEquals(10.0, BossBalance.reportedDamage(50.0, 10.0, 100.0, 100.0), 0.001);
        assertEquals(0.0, BossBalance.reportedDamage(50.0, Double.NaN, 100.0, 1_000.0), 0.001);
        assertEquals(0.0, BossBalance.reportedDamage(0.0, 10.0, 100.0, 1_000.0), 0.001);
    }
}
