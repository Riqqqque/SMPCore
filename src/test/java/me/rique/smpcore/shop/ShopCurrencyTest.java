package me.rique.smpcore.shop;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopCurrencyTest {

    @Test
    void parsesEveryPlayerFacingCurrencyWithoutAmbiguousMatches() {
        assertEquals(ShopCurrency.COAL, ShopCurrency.parse("64 coal"));
        assertEquals(ShopCurrency.COPPER, ShopCurrency.parse("32 copper"));
        assertEquals(ShopCurrency.IRON, ShopCurrency.parse("16 iron"));
        assertEquals(ShopCurrency.GOLD, ShopCurrency.parse("8 gold"));
        assertEquals(ShopCurrency.REDSTONE, ShopCurrency.parse("24 redstone"));
        assertEquals(ShopCurrency.LAPIS, ShopCurrency.parse("12 lapis"));
        assertEquals(ShopCurrency.EMERALD, ShopCurrency.parse("4 emerald"));
        assertEquals(ShopCurrency.DIAMOND, ShopCurrency.parse("2 diamonds"));
        assertEquals(ShopCurrency.NETHERITE, ShopCurrency.parse("1 netherite"));
        assertEquals(ShopCurrency.ESSENCE, ShopCurrency.parse("250 essence"));
        assertNull(ShopCurrency.parse("5 dirt"));
    }

    @Test
    void essenceIsVirtualAndMaterialCurrenciesRemainConcrete() {
        assertTrue(ShopCurrency.ESSENCE.isEssence());
        assertNull(ShopCurrency.ESSENCE.material());
        assertEquals(Material.COPPER_INGOT, ShopCurrency.COPPER.material());
        assertEquals(Material.NETHERITE_INGOT, ShopCurrency.NETHERITE.material());
    }
}
