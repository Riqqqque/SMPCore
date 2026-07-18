package me.rique.smpcore.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FamiliarSkillTreeTest {

    @Test
    void exposesExactlyFiftyOrderedNodes() {
        assertEquals(50, FamiliarSkillTree.NODE_COUNT);
        assertEquals(0, FamiliarSkillTree.nodeIndex(0, 0));
        assertEquals(49, FamiliarSkillTree.nodeIndex(4, 9));
        assertEquals(-1, FamiliarSkillTree.nodeIndex(5, 0));
    }

    @Test
    void branchProgressionCannotSkipOrRepurchaseNodes() {
        long mask = 0L;
        assertFalse(FamiliarSkillTree.canUnlock(mask, 1));
        assertTrue(FamiliarSkillTree.canUnlock(mask, 0));

        mask = FamiliarSkillTree.unlock(mask, 0);
        assertTrue(FamiliarSkillTree.unlocked(mask, 0));
        assertFalse(FamiliarSkillTree.canUnlock(mask, 0));
        assertTrue(FamiliarSkillTree.canUnlock(mask, 1));
        assertFalse(FamiliarSkillTree.canUnlock(mask, 2));
    }

    @Test
    void eachBranchUnlocksIndependently() {
        long mask = 0L;
        for (int branch = 0; branch < FamiliarSkillTree.BRANCH_COUNT; branch++) {
            int first = FamiliarSkillTree.nodeIndex(branch, 0);
            assertTrue(FamiliarSkillTree.canUnlock(mask, first));
            mask = FamiliarSkillTree.unlock(mask, first);
        }
        assertEquals(5, FamiliarSkillTree.unlockedCount(mask));
    }

    @Test
    void fullTreeStaysWithinBalancedCaps() {
        long mask = 0L;
        for (int branch = 0; branch < FamiliarSkillTree.BRANCH_COUNT; branch++) {
            for (int rank = 0; rank < FamiliarSkillTree.RANKS_PER_BRANCH; rank++) {
                mask = FamiliarSkillTree.unlock(mask, FamiliarSkillTree.nodeIndex(branch, rank));
            }
        }

        assertEquals(50, FamiliarSkillTree.unlockedCount(mask));
        assertEquals(1.14D, FamiliarSkillTree.mobDamageMultiplier(mask), 0.0001D);
        assertEquals(0.8825D, FamiliarSkillTree.mobDamageTakenMultiplier(mask), 0.0001D);
        assertEquals(0.095D, FamiliarSkillTree.movementSpeedBonus(mask), 0.0001D);
        assertEquals(1.12D, FamiliarSkillTree.coreEffectMultiplier(mask, false), 0.0001D);
        assertEquals(1.17D, FamiliarSkillTree.coreEffectMultiplier(mask, true), 0.0001D);
        assertEquals(1.25D, FamiliarSkillTree.experienceMultiplier(mask), 0.0001D);
    }

    @Test
    void costsRiseAndKeystonesCostMore() {
        assertEquals(175L, FamiliarSkillTree.cost(0));
        assertEquals(775L, FamiliarSkillTree.cost(8));
        assertEquals(1_500L, FamiliarSkillTree.cost(9));
    }
}
