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
        assertEquals(0.04, BossMasteryManager.huntmarkDamageBonusForRank(4));
        assertEquals(0.04, BossMasteryManager.huntmarkDamageBonusForRank(5));
    }
}
