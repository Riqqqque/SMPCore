package me.rique.smpcore.legendary;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryListenerTest {

    @Test
    void heldWeaponEffectsOnlyApplyToOrdinaryDirectHits() {
        assertTrue(LegendaryListener.shouldApplyHeldWeaponEffects(true, false));
        assertFalse(LegendaryListener.shouldApplyHeldWeaponEffects(false, false));
        assertFalse(LegendaryListener.shouldApplyHeldWeaponEffects(true, true));
    }

    @Test
    void heldWeaponKillRewardsRequireTheActualDirectKillingBlow() {
        assertTrue(LegendaryListener.shouldRewardHeldWeaponKill(true, true, false));
        assertFalse(LegendaryListener.shouldRewardHeldWeaponKill(false, true, false));
        assertFalse(LegendaryListener.shouldRewardHeldWeaponKill(true, false, false));
        assertFalse(LegendaryListener.shouldRewardHeldWeaponKill(true, true, true));
    }
}
