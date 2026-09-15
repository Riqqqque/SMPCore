package me.rique.smpcore.crafting;

import me.rique.smpcore.quest.BeastwardenManager;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingRulesListenerTest {

    @Test
    void cobwebRecipeUsesFiveStringInAnX() {
        String[] shape = CraftingRulesListener.cobwebRecipeShape();

        assertArrayEquals(new String[]{"S S", " S ", "S S"}, shape);
        assertEquals(5, Arrays.stream(shape)
            .flatMapToInt(String::chars)
            .filter(character -> character == 'S')
            .count());
    }

    @Test
    void wildboundArmorMarkersMustMatchTheActualArmorSlot() {
        assertTrue(BeastwardenManager.isValidWildboundArmorPiece(Material.NETHERITE_HELMET, "helm"));
        assertTrue(BeastwardenManager.isValidWildboundArmorPiece(Material.NETHERITE_CHESTPLATE, "chestplate"));
        assertFalse(BeastwardenManager.isValidWildboundArmorPiece(Material.NETHERITE_BOOTS, "helm"));
        assertFalse(BeastwardenManager.isValidWildboundArmorPiece(Material.DIAMOND_HELMET, "helm"));
    }
}
