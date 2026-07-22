package me.rique.smpcore.compat;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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

    @Test
    void bedrockAnvilReceivesProtectedAndPreCancelledInteractions() throws NoSuchMethodException {
        EventHandler interactionHandler = CrossplayManager.class
            .getDeclaredMethod("onBedrockAnvilUse", PlayerInteractEvent.class)
            .getAnnotation(EventHandler.class);
        EventHandler menuHandler = CrossplayManager.class
            .getDeclaredMethod("onAnvilClick", InventoryClickEvent.class)
            .getAnnotation(EventHandler.class);

        assertFalse(interactionHandler.ignoreCancelled());
        assertFalse(menuHandler.ignoreCancelled());
    }

    @Test
    void resultPreviewAndCombineButtonBothFinishRecipes() {
        assertTrue(CrossplayManager.isAnvilResultActionSlot(13));
        assertTrue(CrossplayManager.isAnvilResultActionSlot(22));
        assertFalse(CrossplayManager.isAnvilResultActionSlot(11));
        assertFalse(CrossplayManager.isAnvilResultActionSlot(15));
    }

    @Test
    void ordinaryAnvilInputsAlwaysReceiveTheNativeFallback() {
        assertFalse(CrossplayManager.shouldOfferNativeAnvil(false, false, false, false));
        assertTrue(CrossplayManager.shouldOfferNativeAnvil(true, false, false, false));
        assertTrue(CrossplayManager.shouldOfferNativeAnvil(false, true, false, false));
        assertTrue(CrossplayManager.shouldOfferNativeAnvil(true, true, false, false));
        assertFalse(CrossplayManager.shouldOfferNativeAnvil(true, true, true, false));
        assertFalse(CrossplayManager.shouldOfferNativeAnvil(true, true, false, true));
    }
}
