package me.rique.smpcore.legendary;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void chronoOnlyRescuesReadyOrdinaryLethalDamage() {
        long now = 10_000L;

        assertTrue(LegendaryListener.shouldTriggerChronoRescue(true, now, now, true, false));
        assertFalse(LegendaryListener.shouldTriggerChronoRescue(false, now, now, true, false));
        assertFalse(LegendaryListener.shouldTriggerChronoRescue(true, now + 1L, now, true, false));
        assertFalse(LegendaryListener.shouldTriggerChronoRescue(true, now, now, false, false));
        assertFalse(LegendaryListener.shouldTriggerChronoRescue(true, now, now, true, true));
    }

    @Test
    void wardenBladePreservesDamageAlreadyCalculatedFromLegalBonuses() {
        assertEquals(8.0D, LegendaryListener.wardenBladeMeleeDamage(8.0D));
        assertEquals(15.5D, LegendaryListener.wardenBladeMeleeDamage(15.5D));
        assertEquals(0.0D, LegendaryListener.wardenBladeMeleeDamage(-1.0D));
        assertEquals(0.0D, LegendaryListener.wardenBladeMeleeDamage(Double.NaN));
    }
}
