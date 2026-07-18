package me.rique.smpcore.item;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SalvagingDepotListenerTest {

    @Test
    void netheriteUpgradeComponentsCannotRoundBackIntoAFullIngot() {
        Map<Material, Integer> base = new LinkedHashMap<>();
        base.put(Material.DIAMOND, 3);
        base.put(Material.NETHERITE_SCRAP, 4);
        base.put(Material.GOLD_INGOT, 4);
        base.put(Material.STICK, 2);

        Map<Material, Integer> returned = SalvagingDepotListener.calculateReturnedMaterials(
            base,
            Material.DIAMOND,
            1,
            1.0D,
            0.66D
        );

        assertEquals(Map.of(
            Material.DIAMOND, 1,
            Material.NETHERITE_SCRAP, 2,
            Material.GOLD_INGOT, 2,
            Material.STICK, 1
        ), returned);
    }

    @Test
    void nearlyBrokenGearOnlyReturnsItsPrimaryScrap() {
        Map<Material, Integer> returned = SalvagingDepotListener.calculateReturnedMaterials(
            Map.of(Material.BREEZE_ROD, 1, Material.HEAVY_CORE, 1),
            Material.BREEZE_ROD,
            1,
            0.08D,
            0.66D
        );

        assertEquals(Map.of(Material.BREEZE_ROD, 1), returned);
    }

    @Test
    void zeroReturnRateNeverConsumesGear() {
        Map<Material, Integer> returned = SalvagingDepotListener.calculateReturnedMaterials(
            Map.of(Material.IRON_INGOT, 8),
            Material.IRON_INGOT,
            1,
            1.0D,
            0.0D
        );

        assertEquals(Map.of(), returned);
    }
}
