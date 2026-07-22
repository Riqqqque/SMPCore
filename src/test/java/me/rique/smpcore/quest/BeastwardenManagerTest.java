package me.rique.smpcore.quest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeastwardenManagerTest {

    @Test
    void lockedArmorPreviewExplainsEverySetBenefit() {
        List<String> lore = BeastwardenManager.wildboundMenuLore(false);

        assertTrue(lore.stream().anyMatch(line -> line.contains("4x damage")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("35% less damage")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("Tame valid animals")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("/steed")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("Complete all eight lessons")));
    }

    @Test
    void completedArmorPreviewShowsUnlockedState() {
        assertTrue(BeastwardenManager.wildboundMenuLore(true).stream()
            .anyMatch(line -> line.contains("Unlocked through Beastwarden Training")));
    }

    @Test
    void missingActiveFamiliarIsHandledAsInactive() {
        assertFalse(BeastwardenManager.isKnownFamiliarId(null));
        assertFalse(BeastwardenManager.isKnownFamiliarId("unknown"));
        assertTrue(BeastwardenManager.isKnownFamiliarId("veil_wisp"));
    }

    @Test
    void mountedDistanceAcceptsNormalMovementButRejectsTeleportsAndMountChanges() {
        assertEquals(5.0D, BeastwardenManager.acceptedRideSampleDistance(true, true, 3.0D, 4.0D), 0.0001D);
        assertEquals(0.0D, BeastwardenManager.acceptedRideSampleDistance(false, true, 3.0D, 4.0D));
        assertEquals(0.0D, BeastwardenManager.acceptedRideSampleDistance(true, false, 3.0D, 4.0D));
        assertEquals(0.0D, BeastwardenManager.acceptedRideSampleDistance(true, true, 20.0D, 0.0D));
        assertEquals(0.0D, BeastwardenManager.acceptedRideSampleDistance(true, true, Double.NaN, 1.0D));
    }

    @Test
    void mountedProgressReportsAtIntervalsWithoutSpammingOrRepeatingCompletion() {
        assertFalse(BeastwardenManager.shouldReportRideProgress(0.0D, 24.9D, 1_500.0D));
        assertTrue(BeastwardenManager.shouldReportRideProgress(24.9D, 25.1D, 1_500.0D));
        assertFalse(BeastwardenManager.shouldReportRideProgress(25.1D, 25.2D, 1_500.0D));
        assertFalse(BeastwardenManager.shouldReportRideProgress(1_499.0D, 1_500.0D, 1_500.0D));
    }

    @Test
    void corruptQuestProgressCannotBecomeNegativeOrNonFinite() {
        assertEquals(42.5D, BeastwardenManager.sanitizedQuestProgress(42.5D));
        assertEquals(0.0D, BeastwardenManager.sanitizedQuestProgress(-1.0D));
        assertEquals(0.0D, BeastwardenManager.sanitizedQuestProgress(Double.NaN));
        assertEquals(0.0D, BeastwardenManager.sanitizedQuestProgress(Double.POSITIVE_INFINITY));
    }
}
