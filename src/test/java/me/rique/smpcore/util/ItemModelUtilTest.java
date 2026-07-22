package me.rique.smpcore.util;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemModelUtilTest {

    @Test
    void recognizesVanillaBackedSmpcoreModels() {
        assertTrue(ItemModelUtil.isVanillaBackedItemModel(new NamespacedKey("smpcore", "advanced_pickaxe")));
        assertTrue(ItemModelUtil.isVanillaBackedItemModel(new NamespacedKey("smpcore", "runic_loom")));
        assertTrue(ItemModelUtil.isVanillaBackedItemModel(new NamespacedKey("smpcore", "faradays_magnet")));
        assertTrue(ItemModelUtil.isVanillaBackedItemModel(new NamespacedKey("smpcore", "the_world_clock")));
    }

    @Test
    void leavesOtherModelsAlone() {
        assertFalse(ItemModelUtil.isVanillaBackedItemModel(new NamespacedKey("smpcore", "warden_blade")));
        assertFalse(ItemModelUtil.isVanillaBackedItemModel(new NamespacedKey("other", "runic_loom")));
        assertFalse(ItemModelUtil.isVanillaBackedItemModel(null));
    }
}
