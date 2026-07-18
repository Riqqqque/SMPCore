package me.rique.smpcore.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WitchManagerTest {
    @Test
    void durationBonusUsesTheRolledPercentage() {
        assertEquals(1_150, WitchManager.extendedDuration(1_000, 15));
        assertEquals(1_300, WitchManager.extendedDuration(1_000, 30));
    }

    @Test
    void durationBonusClampsToTheFamiliarRange() {
        assertEquals(1_150, WitchManager.extendedDuration(1_000, 1));
        assertEquals(1_360, WitchManager.extendedDuration(1_000, 80));
    }

    @Test
    void potionUpgradeNeverExceedsTheVanillaFamilyMaximum() {
        assertEquals(1, WitchManager.upgradedAmplifier(0, 2));
        assertEquals(1, WitchManager.upgradedAmplifier(1, 2));
    }
}
