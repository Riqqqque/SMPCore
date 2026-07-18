package me.rique.smpcore.season;

import me.rique.smpcore.util.CustomLoreUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
