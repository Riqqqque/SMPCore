package me.rique.smpcore.quest;

import me.rique.smpcore.boss.BossBalance;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BossMasteryManagerTest {

    @Test
    void masteryRanksUseCumulativeNonRepeatingThresholds() {
        assertEquals(List.of(1, 3, 6, 10, 15), BossMasteryManager.RANK_REQUIREMENTS);
        assertEquals(5, new HashSet<>(BossMasteryManager.RANK_REQUIREMENTS).size());
    }

    @Test
    void allTenBossesParticipateInTheMasteryPath() {
        List<BossBalance.Profile> bosses = BossBalance.progression();
        assertEquals(10, bosses.size());
        for (int index = 0; index < bosses.size(); index++) {
            assertEquals(index + 1, bosses.get(index).tier());
        }
    }

    @Test
    void masteryDoesNotHandOutCorruptedEssenceOrOtherRareRankFourMaterials() {
        for (int tier = 1; tier <= 9; tier++) {
            assertNotEquals("corrupted_essence", BossMasteryManager.commonRewardId(tier));
        }
        assertNull(BossMasteryManager.commonRewardId(10));
        assertEquals(0.0, BossMasteryManager.huntmarkDamageBonusForRank(3));
        assertEquals(0.05, BossMasteryManager.huntmarkDamageBonusForRank(4));
        assertEquals(0.05, BossMasteryManager.huntmarkDamageBonusForRank(5));
    }

    @Test
    void masteryPayoutsScaleIntoWorthwhileLateBossRewards() {
        assertEquals(110L, BossMasteryManager.rankOneEssence(1));
        assertEquals(380L, BossMasteryManager.rankOneEssence(10));
        assertEquals(5, BossMasteryManager.rankTwoMaterialAmount(1));
        assertEquals(10, BossMasteryManager.rankTwoMaterialAmount(10));
        assertEquals(260L, BossMasteryManager.rankFiveEssence(1));
        assertEquals(800L, BossMasteryManager.rankFiveEssence(10));
    }

    @Test
    void pursuitOffenseWorksWithOnePieceButMitigationNeedsTheFullSet() {
        assertEquals(0.0, BossMasteryManager.pursuitBossDamageBonus(0));
        assertEquals(0.15, BossMasteryManager.pursuitBossDamageBonus(1));
        assertEquals(0.15, BossMasteryManager.pursuitBossDamageBonus(4));

        assertEquals(0.0, BossMasteryManager.pursuitBossDamageReduction(3, false));
        assertEquals(0.12, BossMasteryManager.pursuitBossDamageReduction(4, false));
        assertEquals(0.0, BossMasteryManager.pursuitBossDamageReduction(4, true));
    }
}
