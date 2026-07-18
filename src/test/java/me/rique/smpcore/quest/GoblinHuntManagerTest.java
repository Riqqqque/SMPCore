package me.rique.smpcore.quest;

import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoblinHuntManagerTest {

    @Test
    void miningLuckScalesAgainstTheCurrentPlacedCount() {
        assertEquals(0.0D, GoblinHuntManager.scaledMiningLuck(0, 100), 0.000001D);
        assertEquals(0.01D, GoblinHuntManager.scaledMiningLuck(5, 100), 0.000001D);
        assertEquals(0.20D, GoblinHuntManager.scaledMiningLuck(100, 100), 0.000001D);
        assertEquals(0.005D, GoblinHuntManager.scaledMiningLuck(5, 200), 0.000001D);
        assertEquals(0.10D, GoblinHuntManager.scaledMiningLuck(100, 200), 0.000001D);
        assertEquals(0.20D, GoblinHuntManager.scaledMiningLuck(400, 200), 0.000001D);
        assertEquals(0.0D, GoblinHuntManager.scaledMiningLuck(5, 0), 0.000001D);
    }

    @Test
    void damageBonusRequiresEveryCurrentlyActiveGoblin() {
        BitSet found = new BitSet();
        found.set(2);
        found.set(7);
        found.set(99);
        assertTrue(GoblinHuntManager.completedActiveHunt(found, Set.of(2, 7)));
        assertFalse(GoblinHuntManager.completedActiveHunt(found, Set.of(2, 7, 8)));
        assertFalse(GoblinHuntManager.completedActiveHunt(found, Set.of()));
    }
}
