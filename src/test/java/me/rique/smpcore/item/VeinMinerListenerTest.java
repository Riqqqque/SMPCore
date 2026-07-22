package me.rique.smpcore.item;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeinMinerListenerTest {

    @Test
    void recognizesPickaxeMaterialsWithoutDependingOnlyOnTheRuntimeTagRegistry() {
        assertTrue(VeinMinerListener.hasPickaxeMaterialName(Material.DIAMOND_PICKAXE));
        assertTrue(VeinMinerListener.hasPickaxeMaterialName(Material.NETHERITE_PICKAXE));
        assertFalse(VeinMinerListener.hasPickaxeMaterialName(Material.DIAMOND_AXE));
        assertFalse(VeinMinerListener.hasPickaxeMaterialName(Material.AIR));
        assertFalse(VeinMinerListener.hasPickaxeMaterialName(null));
    }

    @Test
    void limitsVeinwakeTerrainChainsToStoneAndDeepslate() {
        assertTrue(VeinMinerListener.isVeinwakeTerrain(Material.STONE));
        assertTrue(VeinMinerListener.isVeinwakeTerrain(Material.DEEPSLATE));
        assertFalse(VeinMinerListener.isVeinwakeTerrain(Material.COBBLESTONE));
        assertFalse(VeinMinerListener.isVeinwakeTerrain(Material.COBBLED_DEEPSLATE));
        assertFalse(VeinMinerListener.isVeinwakeTerrain(Material.DEEPSLATE_DIAMOND_ORE));
        assertFalse(VeinMinerListener.isVeinwakeTerrain(null));
    }

    @Test
    void alwaysExcludesNetherrackFromVeinwakeChains() {
        assertTrue(VeinMinerListener.isVeinwakeExcludedMaterial(Material.NETHERRACK));
        assertFalse(VeinMinerListener.isVeinwakeExcludedMaterial(Material.STONE));
        assertFalse(VeinMinerListener.isVeinwakeExcludedMaterial(Material.NETHER_QUARTZ_ORE));
        assertFalse(VeinMinerListener.isVeinwakeExcludedMaterial(null));
    }
}
