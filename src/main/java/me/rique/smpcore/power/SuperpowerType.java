package me.rique.smpcore.power;

import org.bukkit.Material;

import java.util.Locale;

public enum SuperpowerType {
    IMMORTALITY("Immortality", 0.0001, Material.TOTEM_OF_UNDYING, null),
    FLASH("Flash", 0.10, Material.SUGAR, null),
    ENCHANTER("Enchanter", 0.05, Material.EXPERIENCE_BOTTLE, null),
    BERSERK("Berserk", 0.10, Material.NETHERITE_SWORD, null),
    TANK("Tank", 0.15, Material.SHIELD, null),
    HUMAN("Human", 0.2499, Material.PAPER, null),
    TRAVELER("Traveler", 0.025, Material.ENDER_PEARL, "Use /travel [x] [y] [z] [dimension] or /travel close"),
    FLORIST("Florist", 0.20, Material.OAK_SAPLING, null),
    MONARCH("Monarch", 0.05, Material.ZOMBIE_HEAD, "Use /msummon [amount]"),
    SHADOW("Shadow", 0.075, Material.BLACK_DYE, "Use /shadow toggle");

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
