package me.rique.smpcore.duel;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelArenaCleanupTest {

    @Test
    void looseFloorFluidsAreRemovedWithoutTouchingEmbeddedDecoration() {
        assertTrue(DuelManager.shouldRemoveLooseArenaFluid(Material.WATER, true, true, 4));
        assertTrue(DuelManager.shouldRemoveLooseArenaFluid(Material.LAVA, true, true, 3));
        assertFalse(DuelManager.shouldRemoveLooseArenaFluid(Material.LAVA, true, true, 1));
        assertFalse(DuelManager.shouldRemoveLooseArenaFluid(Material.WATER, false, true, 4));
        assertFalse(DuelManager.shouldRemoveLooseArenaFluid(Material.WATER, true, false, 4));
        assertFalse(DuelManager.shouldRemoveLooseArenaFluid(Material.BLUE_CONCRETE, true, true, 4));
    }
}
