package me.rique.smpcore.power;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
    DEADEYE("Deadeye", 0.05, Material.CROSSBOW, null),
    RIFTWARDEN("Riftwarden", 0.035, Material.ENDER_CHEST, null),
    OATHBOUND("Oathbound", 0.04, Material.BELL, null),
    RUNESMITH("Runesmith", 0.04, Material.SMITHING_TABLE, null),
    GRAVEBORN("Graveborn", 0.035, Material.SOUL_LANTERN, null),
    STORMCALLER("Stormcaller", 0.035, Material.LIGHTNING_ROD, "Use /stormcaller off to quiet your lightning"),
    BLOODMENDER("Bloodmender", 0.03, Material.REDSTONE, null);

    private final String displayName;
    private final double chance;
    private final Material icon;
    private final String commandHint;
    private static final Map<String, SuperpowerType> BY_ID = createLookup();

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
        return BY_ID.get(normalizeId(raw));
    }

    private static Map<String, SuperpowerType> createLookup() {
        Map<String, SuperpowerType> lookup = new HashMap<>();
        for (SuperpowerType type : values()) {
            addAlias(lookup, type, type.name(), type.displayName());
        }

        addAlias(lookup, IMMORTALITY, "immortal");
        addAlias(lookup, FLASH, "speed", "haste");
        addAlias(lookup, ENCHANTER, "enchanting");
        addAlias(lookup, BERSERK, "berserker");
        addAlias(lookup, HUMAN, "no power", "no powers", "none", "normal", "no_power", "no_powers");
        addAlias(lookup, TRAVELER, "traveller");
        addAlias(lookup, FLORIST, "florest", "forest");
        addAlias(lookup, THE_WORLD, "world", "theworld", "time stop", "timestop");
        addAlias(lookup, XRAY_VISION, "xray", "x ray", "x-ray", "oracle");
        addAlias(lookup, GIANT, "giant");
        addAlias(lookup, SUPERMAN, "superman");
        addAlias(lookup, WATERMAN, "waterman", "water man");
        addAlias(lookup, VOIDWALKER, "void walker");
        addAlias(lookup, STORMCALLER, "storm caller");
        addAlias(lookup, BLOODMENDER, "blood mender");
        return Map.copyOf(lookup);
    }

    private static void addAlias(Map<String, SuperpowerType> lookup, SuperpowerType type, String... aliases) {
        for (String alias : aliases) {
            String normalized = normalizeId(alias);
            if (!normalized.isBlank()) {
                lookup.put(normalized, type);
            }
        }
    }

    private static String normalizeId(String raw) {
        String normalized = raw.trim().toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+", "_")
            .replaceAll("_+", "_");
        if (normalized.startsWith("_")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("_")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
