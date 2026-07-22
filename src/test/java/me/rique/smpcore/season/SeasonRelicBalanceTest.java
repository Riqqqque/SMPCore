package me.rique.smpcore.season;

import me.rique.smpcore.util.CustomLoreUtil;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonRelicBalanceTest {

    @Test
    void bossWeaponScalingRisesWithRarity() {
        assertEquals(1.12, SeasonRelicManager.bossDamageMultiplier(CustomLoreUtil.Rarity.RARE));
        assertEquals(1.20, SeasonRelicManager.bossDamageMultiplier(CustomLoreUtil.Rarity.EPIC));
        assertEquals(1.30, SeasonRelicManager.bossDamageMultiplier(CustomLoreUtil.Rarity.LEGENDARY));
        assertEquals(1.40, SeasonRelicManager.bossDamageMultiplier(CustomLoreUtil.Rarity.MYTHIC));
    }

    @Test
    void bossWardScalingRisesWithRarity() {
        double rare = SeasonRelicManager.bossDamageReduction(CustomLoreUtil.Rarity.RARE);
        double epic = SeasonRelicManager.bossDamageReduction(CustomLoreUtil.Rarity.EPIC);
        double legendary = SeasonRelicManager.bossDamageReduction(CustomLoreUtil.Rarity.LEGENDARY);
        double mythic = SeasonRelicManager.bossDamageReduction(CustomLoreUtil.Rarity.MYTHIC);

        assertTrue(rare < epic);
        assertTrue(epic < legendary);
        assertTrue(legendary < mythic);
    }

    @Test
    void meleeSeasonWeaponsAcceptPermanentCombatBooks() {
        assertTrue(SeasonRelicManager.allowsExtraCombatEnchant(Material.DIAMOND_PICKAXE, "sharpness"));
        assertTrue(SeasonRelicManager.allowsExtraCombatEnchant(Material.NETHERITE_AXE, "looting"));
        assertTrue(SeasonRelicManager.allowsExtraCombatEnchant(Material.TRIDENT, "fire_aspect"));
        assertTrue(SeasonRelicManager.allowsExtraCombatEnchant(Material.MACE, "smite"));
        assertFalse(SeasonRelicManager.allowsExtraCombatEnchant(Material.BOW, "sharpness"));
        assertFalse(SeasonRelicManager.allowsExtraCombatEnchant(Material.CROSSBOW, "looting"));
        assertFalse(SeasonRelicManager.allowsExtraCombatEnchant(Material.DIAMOND_PICKAXE, "efficiency"));
    }

    @Test
    void permanentEnchantBooksCombineAtVanillaLevels() {
        assertEquals(3, SeasonRelicManager.combinedEnchantLevel(1, 3, 5));
        assertEquals(4, SeasonRelicManager.combinedEnchantLevel(3, 3, 5));
        assertEquals(5, SeasonRelicManager.combinedEnchantLevel(5, 5, 5));
        assertEquals(0, SeasonRelicManager.combinedEnchantLevel(0, 0, 5));
    }
}
