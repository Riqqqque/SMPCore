package me.rique.smpcore.shop;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketStallManagerTest {

    @Test
    void onlyChestShopEssentialsCanBePlaced() {
        assertTrue(MarketStallManager.isPlaceableStorageMaterial(Material.CHEST));
        assertTrue(MarketStallManager.isPlaceableStorageMaterial(Material.TRAPPED_CHEST));
        assertFalse(MarketStallManager.isPlaceableStorageMaterial(Material.BARREL));
        assertTrue(MarketStallManager.isWallShopSign(Material.OAK_WALL_SIGN));
        assertFalse(MarketStallManager.isWallShopSign(Material.OAK_SIGN));
        assertFalse(MarketStallManager.isWallShopSign(Material.SPRUCE_HANGING_SIGN));
        assertTrue(MarketStallManager.isShopSignItem(Material.OAK_SIGN));
        assertFalse(MarketStallManager.isShopSignItem(Material.OAK_HANGING_SIGN));
    }

    @Test
    void editableDecorCannotOverrideLockedStallMaterials() {
        assertTrue(MarketStallManager.isOwnerEditableMaterial(Material.BARREL));
        assertTrue(MarketStallManager.isOwnerEditableMaterial(Material.LANTERN));
        assertTrue(MarketStallManager.isOwnerEditableMaterial(Material.SOUL_LANTERN));
        assertTrue(MarketStallManager.isOwnerEditableMaterial(Material.FURNACE));
        assertTrue(MarketStallManager.isOwnerEditableMaterial(Material.SMOKER));
        assertTrue(MarketStallManager.isOwnerEditableMaterial(Material.BLAST_FURNACE));
        assertTrue(MarketStallManager.isOwnerEditableMaterial(Material.BOOKSHELF));
        assertTrue(MarketStallManager.isOwnerEditableMaterial(Material.CHISELED_BOOKSHELF));
        assertTrue(MarketStallManager.isOwnerEditableMaterial(Material.CRAFTING_TABLE));
        assertTrue(MarketStallManager.isOwnerEditableMaterial(Material.DECORATED_POT));
        assertTrue(MarketStallManager.isOwnerEditableMaterial(Material.POTTED_DANDELION));

        assertFalse(MarketStallManager.isOwnerEditableMaterial(Material.SPRUCE_TRAPDOOR));
        assertFalse(MarketStallManager.isOwnerEditableMaterial(Material.CHERRY_TRAPDOOR));
        assertFalse(MarketStallManager.isOwnerEditableMaterial(Material.PALE_OAK_TRAPDOOR));
        assertFalse(MarketStallManager.isOwnerEditableMaterial(Material.POTTED_CHERRY_SAPLING));
        assertFalse(MarketStallManager.isOwnerEditableMaterial(Material.HOPPER));
        assertFalse(MarketStallManager.isOwnerEditableMaterial(Material.TNT));
        assertFalse(MarketStallManager.isOwnerEditableMaterial(Material.SHULKER_BOX));

        Material shelf = Material.matchMaterial("OAK_SHELF");
        if (shelf != null) assertFalse(MarketStallManager.isOwnerEditableMaterial(shelf));
    }

    @Test
    void cuboidsOnlyOverlapInTheSameWorld() {
        UUID world = UUID.randomUUID();
        assertTrue(MarketStallManager.boxesOverlap(
            world, 0, 10, 60, 70, 0, 10,
            world, 10, 20, 65, 75, 10, 20
        ));
        assertFalse(MarketStallManager.boxesOverlap(
            world, 0, 10, 60, 70, 0, 10,
            world, 11, 20, 60, 70, 0, 10
        ));
        assertFalse(MarketStallManager.boxesOverlap(
            world, 0, 10, 60, 70, 0, 10,
            UUID.randomUUID(), 0, 10, 60, 70, 0, 10
        ));
    }

    @Test
    void resaleReturnsSeventyFivePercentWithoutRoundingUp() {
        assertEquals(750L, MarketStallManager.resaleRefund(1_000L));
        assertEquals(1L, MarketStallManager.resaleRefund(1L));
        assertEquals(76L, MarketStallManager.resaleRefund(102L));
    }

    @Test
    void purchaseSignsKeepLargePricesReadable() {
        assertEquals("25,000 Essence", MarketStallManager.signPrice(25_000L));
        assertEquals("1.5m Essence", MarketStallManager.signPrice(1_500_000L));
        assertEquals("100m Essence", MarketStallManager.signPrice(100_000_000L));
    }

    @Test
    void ownersCanRemoveChestSignsButNeverTheStallPurchaseSign() {
        assertTrue(MarketStallManager.canOwnerBreakStallBlock(false, false, false, true));
        assertTrue(MarketStallManager.canOwnerBreakStallBlock(false, true, false, false));
        assertFalse(MarketStallManager.canOwnerBreakStallBlock(true, true, true, true));
        assertFalse(MarketStallManager.canOwnerBreakStallBlock(false, false, false, false));
    }

    @Test
    void stallPurchaseSignsNeverRouteThroughChestShopPurchases() {
        assertTrue(PlayerShopListener.shouldHandleShopPurchase(true, false));
        assertFalse(PlayerShopListener.shouldHandleShopPurchase(true, true));
        assertFalse(PlayerShopListener.shouldHandleShopPurchase(false, true));
    }

    @Test
    void onlyOwnersAndAdminsCanEditStallSigns() {
        assertTrue(MarketStallManager.canEditStallSign(false, false, true));
        assertTrue(MarketStallManager.canEditStallSign(true, true, false));
        assertFalse(MarketStallManager.canEditStallSign(false, false, false));
        assertFalse(MarketStallManager.canEditStallSign(false, true, true));
    }

    @Test
    void signModificationItemsAreNotTreatedAsPurchases() {
        assertTrue(MarketStallManager.isSignModificationItem(Material.HONEYCOMB));
        assertTrue(MarketStallManager.isSignModificationItem(Material.GLOW_INK_SAC));
        assertTrue(MarketStallManager.isSignModificationItem(Material.INK_SAC));
        assertTrue(MarketStallManager.isSignModificationItem(Material.RED_DYE));
        assertFalse(MarketStallManager.isSignModificationItem(Material.STONE));
    }

    @Test
    void playersCanOnlyAcquireOneStallAtATime() {
        assertTrue(MarketStallManager.canAcquireStall(true, false));
        assertFalse(MarketStallManager.canAcquireStall(true, true));
        assertFalse(MarketStallManager.canAcquireStall(false, false));
        assertFalse(MarketStallManager.canAcquireStall(false, true));
    }
}
