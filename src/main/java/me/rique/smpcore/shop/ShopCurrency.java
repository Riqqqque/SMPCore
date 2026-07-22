package me.rique.smpcore.shop;

import org.bukkit.Material;

import java.util.Locale;

public enum ShopCurrency {
    COAL(Material.COAL, "coal", "coal"),
    COPPER(Material.COPPER_INGOT, "copper", "copper ingot"),
    IRON(Material.IRON_INGOT, "iron", "iron ingot"),
    GOLD(Material.GOLD_INGOT, "gold", "gold ingot"),
    REDSTONE(Material.REDSTONE, "redstone", "redstone"),
    LAPIS(Material.LAPIS_LAZULI, "lapis", "lapis lazuli"),
    EMERALD(Material.EMERALD, "emerald", "emerald"),
    DIAMOND(Material.DIAMOND, "diamond", "diamond"),
    NETHERITE(Material.NETHERITE_INGOT, "netherite", "netherite ingot"),
    ESSENCE(null, "essence", "Essence");

    private final Material material;
    private final String shortName;
    private final String displayName;

    ShopCurrency(Material material, String shortName, String displayName) {
        this.material = material;
        this.shortName = shortName;
        this.displayName = displayName;
    }

    public Material material() {
        return material;
    }

    public boolean isEssence() {
        return this == ESSENCE;
    }

    public String shortName() {
        return shortName;
    }

    public String display(long amount) {
        if (this == ESSENCE || amount == 1L || displayName.endsWith("lazuli") || displayName.equals("coal") || displayName.equals("redstone")) {
            return displayName;
        }
        return displayName + "s";
    }

    public static ShopCurrency parse(String line) {
        String clean = line == null ? "" : line.toLowerCase(Locale.ROOT);
        if (clean.contains("essence")) return ESSENCE;
        if (clean.contains("netherite")) return NETHERITE;
        if (clean.contains("diamond")) return DIAMOND;
        if (clean.contains("emerald")) return EMERALD;
        if (clean.contains("lapis")) return LAPIS;
        if (clean.contains("redstone")) return REDSTONE;
        if (clean.contains("copper")) return COPPER;
        if (clean.contains("gold")) return GOLD;
        if (clean.contains("iron")) return IRON;
        if (clean.contains("coal")) return COAL;
        return null;
    }
}
