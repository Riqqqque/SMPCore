package me.rique.smpcore.quest;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FisherManagerTest {

    @Test
    void onlyTheCurrentFishingCatchCounts() {
        assertTrue(FisherManager.countsForStage(0, Material.COD));
        assertTrue(FisherManager.countsForStage(1, Material.SALMON));
        assertTrue(FisherManager.countsForStage(2, Material.PUFFERFISH));
        assertFalse(FisherManager.countsForStage(0, Material.SALMON));
        assertFalse(FisherManager.countsForStage(-1, Material.COD));
        assertFalse(FisherManager.countsForStage(3, Material.PUFFERFISH));
        assertFalse(FisherManager.countsForStage(0, null));
    }
}
