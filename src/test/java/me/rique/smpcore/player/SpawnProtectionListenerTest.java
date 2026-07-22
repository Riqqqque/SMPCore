package me.rique.smpcore.player;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Stairs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnProtectionListenerTest {

    @Test
    void spawnTrampleProtectionCoversFarmlandAndTurtleEggs() {
        assertTrue(SpawnProtectionListener.isTrampleSensitiveMaterial(Material.FARMLAND));
        assertTrue(SpawnProtectionListener.isTrampleSensitiveMaterial(Material.TURTLE_EGG));
        assertFalse(SpawnProtectionListener.isTrampleSensitiveMaterial(Material.DIRT));
    }

    @Test
    void gsitStairSeatsIncludeMangroveWithoutOpeningOtherBlocks() {
        assertTrue(SpawnProtectionListener.isGsitSeatMaterial(Material.MANGROVE_STAIRS));
        assertTrue(SpawnProtectionListener.isGsitSeatMaterial(Material.OAK_STAIRS));
        assertFalse(SpawnProtectionListener.isGsitSeatMaterial(Material.MANGROVE_PLANKS));
        assertFalse(SpawnProtectionListener.isGsitSeatMaterial(Material.MANGROVE_DOOR));
    }

    @Test
    void gsitSeatExceptionRequiresAnEmptyHand() {
        assertTrue(SpawnProtectionListener.isEmptyHandGsitSeat(Material.MANGROVE_STAIRS, null));
        assertTrue(SpawnProtectionListener.isEmptyHandGsitSeat(Material.OAK_STAIRS, Material.AIR));
        assertFalse(SpawnProtectionListener.isEmptyHandGsitSeat(Material.MANGROVE_STAIRS, Material.MANGROVE_PLANKS));
        assertFalse(SpawnProtectionListener.isEmptyHandGsitSeat(Material.OAK_STAIRS, Material.TORCH));
        assertFalse(SpawnProtectionListener.isEmptyHandGsitSeat(Material.MANGROVE_PLANKS, Material.AIR));
    }

    @Test
    void gsitFallbackOnlyHandlesSideClicks() {
        assertTrue(GsitSeatBridge.shouldUseSideClickFallback(BlockFace.NORTH));
        assertTrue(GsitSeatBridge.shouldUseSideClickFallback(BlockFace.EAST));
        assertFalse(GsitSeatBridge.shouldUseSideClickFallback(BlockFace.UP));
        assertFalse(GsitSeatBridge.shouldUseSideClickFallback(null));
    }

    @Test
    void gsitFallbackMatchesStraightStairFacing() {
        GsitSeatBridge.SeatPose east = GsitSeatBridge.seatPose(BlockFace.EAST, Stairs.Shape.STRAIGHT);
        GsitSeatBridge.SeatPose north = GsitSeatBridge.seatPose(BlockFace.NORTH, Stairs.Shape.STRAIGHT);

        assertNotNull(east);
        assertEquals(0.123D, east.xOffset());
        assertEquals(0D, east.zOffset());
        assertEquals(-90F, east.yaw());
        assertNotNull(north);
        assertEquals(0D, north.xOffset());
        assertEquals(-0.123D, north.zOffset());
        assertEquals(180F, north.yaw());
    }
}
