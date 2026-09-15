package me.rique.smpcore.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VeilOrbManagerTest {

    @Test
    void runicEnchantPagesNeverBecomeEmptyOrLoseOverflowOptions() {
        assertEquals(1, VeilOrbManager.runicPageCount(0));
        assertEquals(1, VeilOrbManager.runicPageCount(1));
        assertEquals(1, VeilOrbManager.runicPageCount(13));
        assertEquals(2, VeilOrbManager.runicPageCount(14));
        assertEquals(3, VeilOrbManager.runicPageCount(27));
    }
}
