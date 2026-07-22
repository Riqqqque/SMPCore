package me.rique.smpcore.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemEnchantConflictPolicyTest {

    @Test
    void smeltingTouchConflictsOnlyWithSilkTouch() {
        assertTrue(ItemEnchantConflictPolicy.customConflictsWithVanilla(
            "smelting_touch_enchant", "silk_touch"
        ));
        assertFalse(ItemEnchantConflictPolicy.customConflictsWithVanilla(
            "smelting_touch_enchant", "fortune"
        ));
        assertFalse(ItemEnchantConflictPolicy.customConflictsWithVanilla(
            "telekinesis_enchant", "silk_touch"
        ));
    }

    @Test
    void invalidVanillaPairsKeepTheStrongerThenMoreUsefulEnchant() {
        assertTrue(ItemEnchantConflictPolicy.compareVanillaCandidates("density", 6, "breach", 4) < 0);
        assertTrue(ItemEnchantConflictPolicy.compareVanillaCandidates("fortune", 3, "silk_touch", 1) < 0);
        assertTrue(ItemEnchantConflictPolicy.compareVanillaCandidates("mending", 1, "infinity", 1) < 0);
        assertTrue(ItemEnchantConflictPolicy.compareVanillaCandidates("sharpness", 5, "smite", 5) < 0);
        assertTrue(ItemEnchantConflictPolicy.compareVanillaCandidates("protection", 4, "fire_protection", 4) < 0);
    }
}
