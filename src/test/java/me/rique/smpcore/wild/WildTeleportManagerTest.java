package me.rique.smpcore.wild;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildTeleportManagerTest {

    @Test
    void searchBoundsRespectOffCenterBorderAndPadding() {
        WildTeleportManager.SearchBounds bounds = WildTeleportManager.searchBounds(100.0D, -50.0D, 1000.0D, 32);

        assertEquals(-368, bounds.minX());
        assertEquals(567, bounds.maxX());
        assertEquals(-518, bounds.minZ());
        assertEquals(417, bounds.maxZ());
        assertTrue(bounds.contains(-368, 417));
        assertFalse(bounds.contains(-369, 417));
        assertFalse(bounds.contains(567, 418));
    }

    @Test
    void borderTooSmallForPaddingHasNoSearchArea() {
        assertNull(WildTeleportManager.searchBounds(0.0D, 0.0D, 64.0D, 32));
        assertNull(WildTeleportManager.searchBounds(Double.NaN, 0.0D, 5000.0D, 32));
    }

    @Test
    void coordinateLimitClampsHugeBorders() {
        WildTeleportManager.SearchBounds bounds = WildTeleportManager.searchBounds(0.0D, 0.0D, 80_000_000.0D, 0);

        assertEquals(-29_999_984, bounds.minX());
        assertEquals(29_999_983, bounds.maxX());
        assertEquals(-29_999_984, bounds.minZ());
        assertEquals(29_999_983, bounds.maxZ());
    }

    @Test
    void hazardousLandingMaterialsAreRejected() {
        assertTrue(WildTeleportManager.isUnsafeSurface(Material.WATER));
        assertTrue(WildTeleportManager.isUnsafeSurface(Material.MAGMA_BLOCK));
        assertTrue(WildTeleportManager.isUnsafeSurface(Material.POINTED_DRIPSTONE));
        assertTrue(WildTeleportManager.isUnsafeBody(Material.SWEET_BERRY_BUSH));
        assertTrue(WildTeleportManager.isUnsafeBody(Material.COBWEB));
        assertFalse(WildTeleportManager.isUnsafeSurface(Material.GRASS_BLOCK));
        assertFalse(WildTeleportManager.isUnsafeBody(Material.AIR));
    }

    @Test
    void farthestDistanceUsesTheActualOffCenterRectangle() {
        WildTeleportManager.SearchBounds bounds = new WildTeleportManager.SearchBounds(100, 200, -50, 50);

        assertEquals(42_750.5D, bounds.farthestDistanceSquared(0.0D, 0.0D), 0.0001D);
    }
}
