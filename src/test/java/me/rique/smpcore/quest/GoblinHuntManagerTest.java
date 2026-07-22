package me.rique.smpcore.quest;

import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;
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
    void retiredOrReplacedHeadsDoNotEraseEarnedCompletion() {
        BitSet found = new BitSet();
        found.set(2);
        found.set(7);
        found.set(99);
        assertTrue(GoblinHuntManager.completedActiveHunt(found, Set.of(2, 7)));
        assertTrue(GoblinHuntManager.completedActiveHunt(found, Set.of(2, 7, 8)));
        assertFalse(GoblinHuntManager.completedActiveHunt(found, Set.of(2, 7, 8, 10)));
        assertFalse(GoblinHuntManager.completedActiveHunt(found, Set.of()));
        assertEquals(3, GoblinHuntManager.activeMapProgress(4, 3));
        assertEquals(3, GoblinHuntManager.activeMapProgress(3, 4));
        assertEquals(0, GoblinHuntManager.activeMapProgress(-1, 4));
    }

    @Test
    void oreBonusCopiesStayAdditiveAndSplitAtTheVanillaStackLimit() {
        assertEquals(List.of(64, 16), GoblinHuntManager.splitBonusAmounts(40, 2, 64));
        assertEquals(List.of(10), GoblinHuntManager.splitBonusAmounts(5, 2, 64));
        assertTrue(GoblinHuntManager.splitBonusAmounts(40, 0, 64).isEmpty());
        assertTrue(GoblinHuntManager.splitBonusAmounts(0, 2, 64).isEmpty());
        assertTrue(GoblinHuntManager.splitBonusAmounts(40, 2, 0).isEmpty());
    }

    @Test
    void goblinNeedsAtLeastOnePassableAdjacentFace() {
        assertFalse(GoblinHuntManager.hasClaimableFace(false, false, false, false, false, false));
        assertTrue(GoblinHuntManager.hasClaimableFace(false, false, true, false, false, false));
        assertFalse(GoblinHuntManager.hasClaimableFace((boolean[]) null));
    }

    @Test
    void registeredGoblinTextureMustMatchExactly() {
        assertTrue(GoblinHuntManager.sameTexture("https://textures.minecraft.net/texture/goblin", "https://textures.minecraft.net/texture/GOBLIN"));
        assertFalse(GoblinHuntManager.sameTexture("https://textures.minecraft.net/texture/goblin", "https://textures.minecraft.net/texture/other"));
        assertFalse(GoblinHuntManager.sameTexture(null, "https://textures.minecraft.net/texture/goblin"));
    }

    @Test
    void adminMarkersRequireTheRealLoadedHeadInTheCurrentWorld() {
        assertTrue(GoblinHuntManager.canDisplayAdminMarker(true, true, true));
        assertFalse(GoblinHuntManager.canDisplayAdminMarker(false, true, true));
        assertFalse(GoblinHuntManager.canDisplayAdminMarker(true, false, true));
        assertFalse(GoblinHuntManager.canDisplayAdminMarker(true, true, false));
    }
}
