package me.rique.smpcore.config;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigManagerTest {

    @Test
    void goldenAppleIngredientAcceptsRealItemsAndRejectsInvalidMaterials() {
        assertEquals(Material.GOLD_NUGGET,
            ConfigManager.craftingIngredient("GOLD_NUGGET", Material.GOLD_INGOT));
        assertEquals(Material.GOLD_NUGGET,
            ConfigManager.craftingIngredient("gold_nugget", Material.GOLD_INGOT));
        assertEquals(Material.GOLD_INGOT,
            ConfigManager.craftingIngredient("not_a_material", Material.GOLD_INGOT));
        assertEquals(Material.GOLD_INGOT,
            ConfigManager.craftingIngredient("AIR", Material.GOLD_INGOT));
        assertEquals(Material.GOLD_INGOT,
            ConfigManager.craftingIngredient("WATER", Material.GOLD_INGOT));
        assertEquals(Material.GOLD_INGOT,
            ConfigManager.craftingIngredient("not_a_material", Material.AIR));
    }
}
