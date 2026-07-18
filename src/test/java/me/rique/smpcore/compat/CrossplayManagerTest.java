package me.rique.smpcore.compat;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossplayManagerTest {

    @Test
    void recognizesEveryVanillaAnvilState() {
        assertTrue(CrossplayManager.isAnvilBlock(Material.ANVIL));
        assertTrue(CrossplayManager.isAnvilBlock(Material.CHIPPED_ANVIL));
        assertTrue(CrossplayManager.isAnvilBlock(Material.DAMAGED_ANVIL));
        assertFalse(CrossplayManager.isAnvilBlock(Material.SMITHING_TABLE));
    }
}
