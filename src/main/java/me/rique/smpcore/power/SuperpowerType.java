package me.rique.smpcore.power;

import org.bukkit.Material;

import java.util.Locale;

public enum SuperpowerType {
    IMMORTALITY("Deathless", 0.0001, Material.TOTEM_OF_UNDYING, null),
    FLASH("Stormrunner", 0.08, Material.SUGAR, null),
    ENCHANTER("Arcanist", 0.045, Material.EXPERIENCE_BOTTLE, null),
    BERSERK("Warborn", 0.08, Material.NETHERITE_SWORD, null),
    TANK("Juggernaut", 0.11, Material.SHIELD, null),
    HUMAN("Mortal", 0.0899, Material.PAPER, null),
    TRAVELER("Wayfarer", 0.025, Material.ENDER_PEARL, "Use /travel [x] [y] [z] [dimension] or /travel close"),
    FLORIST("Verdant", 0.12, Material.OAK_SAPLING, null),
    DRUID("Druid", 0.05, Material.ENCHANTED_BOOK, null),
    MONARCH("Sovereign", 0.045, Material.ZOMBIE_HEAD, "Use /msummon [amount]"),
    SHADOW("Nightshade", 0.06, Material.BLACK_DYE, "Use /shadow toggle"),
    THE_WORLD("The World", 0.05, Material.CLOCK, null),
    XRAY_VISION("Oracle Eye", 0.035, Material.SPYGLASS, "Use /xray"),
    MINER("Prospector", 0.08, Material.DIAMOND_PICKAXE, null),
    GIANT("Titan", 0.10, Material.ANVIL, null),
    SUPERMAN("Skybound", 0.03, Material.FEATHER, null);

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
