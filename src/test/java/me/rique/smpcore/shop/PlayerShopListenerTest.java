package me.rique.smpcore.shop;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopListenerTest {

    @Test
    void singularAndPluralShopHeadersAreAccepted() {
        assertTrue(PlayerShopListener.isPlayerShopHeader("[shop]"));
        assertTrue(PlayerShopListener.isPlayerShopHeader(" [Shops] "));
        assertFalse(PlayerShopListener.isPlayerShopHeader("[adminshop]"));
        assertFalse(PlayerShopListener.isPlayerShopHeader("shop"));
    }

    @Test
    void legacyRecoveryRequiresTheDisplayedStockNameToMatch() {
        assertTrue(PlayerShopListener.storedItemNameMatches("Diamond Blocks", "Diamond Blocks"));
        assertTrue(PlayerShopListener.storedItemNameMatches("diamond blocks", "Diamond Blocks"));
        assertTrue(PlayerShopListener.storedItemNameMatches("Very Long Custo", "Very Long Custom Item Name"));
        assertFalse(PlayerShopListener.storedItemNameMatches("Diamond", "Diamond Blocks"));
        assertFalse(PlayerShopListener.storedItemNameMatches("", "Diamond Blocks"));
    }

    @Test
    void enchantedBookLabelsShowTheEnchantAndLevel() {
        assertEquals("Mending I", PlayerShopListener.compactVanillaEnchantName("mending", 1));
        assertEquals("Efficiency V", PlayerShopListener.compactVanillaEnchantName("efficiency", 5));
        assertEquals("Proj. Prot IV", PlayerShopListener.compactVanillaEnchantName("projectile_protection", 4));
        assertEquals("Vanishing Curse", PlayerShopListener.compactVanillaEnchantName("vanishing_curse", 1));
    }

    @Test
    void multiEnchantBookLabelsKeepTheirAdditionalEnchantCount() {
        assertEquals("Efficiency V +1", PlayerShopListener.trimSign("Efficiency V +1"));
        assertEquals("Projectile +12", PlayerShopListener.trimSign("Projectile Protection IV +12"));
    }

    @Test
    void stallManagersRemainCustomersInsteadOfBecomingShopOwners() {
        UUID owner = UUID.randomUUID();
        UUID manager = UUID.randomUUID();

        assertTrue(PlayerShopListener.blocksOwnerPurchase(false, false, owner, owner));
        assertFalse(PlayerShopListener.blocksOwnerPurchase(false, false, manager, owner));
        assertFalse(PlayerShopListener.blocksOwnerPurchase(true, false, owner, owner));
        assertFalse(PlayerShopListener.blocksOwnerPurchase(false, true, owner, owner));
    }
}
