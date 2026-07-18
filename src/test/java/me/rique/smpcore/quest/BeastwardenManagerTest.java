package me.rique.smpcore.quest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
