package me.rique.smpcore.power;

import org.bukkit.Material;

import java.util.Locale;

public enum SuperpowerType {
    IMMORTALITY("Deathless", 0.0001, Material.TOTEM_OF_UNDYING, null),
    FLASH("Stormrunner", 0.07, Material.SUGAR, null),
    ENCHANTER("Arcanist", 0.04, Material.EXPERIENCE_BOTTLE, null),
    BERSERK("Warborn", 0.07, Material.NETHERITE_SWORD, null),
    TANK("Juggernaut", 0.09, Material.SHIELD, null),
    HUMAN("Mortal", 0.0149, Material.PAPER, null),
    TRAVELER("Wayfarer", 0.025, Material.ENDER_PEARL, "Use /travel [x] [y] [z] [dimension] or /travel close"),
    FLORIST("Verdant", 0.09, Material.OAK_SAPLING, null),
    DRUID("Druid", 0.045, Material.ENCHANTED_BOOK, null),
    MONARCH("Sovereign", 0.04, Material.ZOMBIE_HEAD, "Use /msummon [amount]"),
    SHADOW("Nightshade", 0.055, Material.BLACK_DYE, "Use /shadow toggle"),
    THE_WORLD("The World", 0.04, Material.CLOCK, null),
    XRAY_VISION("Oracle Eye", 0.03, Material.SPYGLASS, "Use /xray"),
    MINER("Prospector", 0.065, Material.DIAMOND_PICKAXE, null),
    GIANT("Titan", 0.075, Material.ANVIL, null),
    SUPERMAN("Skybound", 0.025, Material.FEATHER, null),
    WATERMAN("Tideborn", 0.02, Material.HEART_OF_THE_SEA, null),
    PHOENIX("Ashen Soul", 0.02, Material.BLAZE_POWDER, null),
    VOIDWALKER("Voidwalker", 0.035, Material.ENDER_EYE, "Use /voidstep and /voidvision"),
    SENTINEL("Sentinel", 0.05, Material.IRON_CHESTPLATE, null),
    FROSTBORN("Frostborn", 0.05, Material.BLUE_ICE, null),
    DEADEYE("Deadeye", 0.05, Material.CROSSBOW, null);

    private final String displayName;
    private final double chance;
    private final Material icon;
    private final String commandHint;

    SuperpowerType(String displayName, double chance, Material icon, String commandHint) {
        this.displayName = displayName;
        this.chance = chance;
        this.icon = icon;
        this.commandHint = commandHint;
    }

    public String displayName() {
        return displayName;
    }

    public double chance() {
        return chance;
    }

    public Material icon() {
        return icon;
    }

    public String commandHint() {
        return commandHint;
    }

    public boolean hasCommandHint() {
        return commandHint != null && !commandHint.isBlank();
    }

    public static SuperpowerType fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
