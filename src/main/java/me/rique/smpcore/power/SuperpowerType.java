package me.rique.smpcore.power;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum SuperpowerType {
    HONORED_ONE("The Honored One", 0.0001, Material.END_CRYSTAL, "Use /domainexpansion"),
    VEIL_ASSASSIN("Veil Assassin", 0.07, Material.BLACK_DYE, "Use /smokebomb"),
    ARCANIST("Arcanist", 0.04, Material.EXPERIENCE_BOTTLE, "Use /arcanebook"),
    BERSERKER("Berserker", 0.07, Material.NETHERITE_SWORD, null),
    JUGGERNAUT("Juggernaut", 0.09, Material.SHIELD, "Use /unstoppableforce"),
    MORTAL("Mortal", 0.0149, Material.PAPER, null),
    WAYFARER("Wayfarer", 0.025, Material.ENDER_PEARL, "Use /travel [x] [y] [z] [dimension] or /travel close"),
    VERDANT("Verdant", 0.09, Material.OAK_SAPLING, null),
    DRUID("Druid", 0.045, Material.ENCHANTED_BOOK, null),
    MONARCH("Shadow Monarch", 0.04, Material.ZOMBIE_HEAD, "Use /msummon [amount] or /msummon despawn"),
    NIGHTSHADE("Nightshade", 0.055, Material.BLACK_DYE, "Use /shadow toggle and /nightshadevision"),
    THE_WORLD("The World", 0.04, Material.CLOCK, null),
    ORACLE_EYE("Oracle Eye", 0.03, Material.SPYGLASS, "Use /xray"),
    PROSPECTOR("Prospector", 0.065, Material.DIAMOND_PICKAXE, null),
    TITAN("Titan", 0.075, Material.ANVIL, null),
    SKYBOUND("Skybound", 0.025, Material.FEATHER, null),
    TIDEBORN("Tideborn", 0.02, Material.HEART_OF_THE_SEA, null),
    ASHEN_SOUL("Ashen Soul", 0.02, Material.BLAZE_POWDER, null),
    VOIDWALKER("Voidwalker", 0.035, Material.ENDER_EYE, "Sneak-right-click with an empty hand to Voidstep; use /voidvision for night vision"),
    SENTINEL("Sentinel", 0.05, Material.IRON_CHESTPLATE, null),
    FROSTBORN("Frostborn", 0.05, Material.BLUE_ICE, null),
    DEADEYE("Deadeye", 0.05, Material.CROSSBOW, "Use /deadeyearrows"),
    RIFTWARDEN("Riftwarden", 0.035, Material.ENDER_CHEST, null),
    OATHBOUND("Oathbound", 0.04, Material.BELL, "Use /oathsummon <player>"),
    RUNESMITH("Runesmith", 0.04, Material.SMITHING_TABLE, null),
    GRAVEBORN("Graveborn", 0.035, Material.SOUL_LANTERN, null),
    STORMCALLER("Stormcaller", 0.035, Material.LIGHTNING_ROD, "Use /stormcaller off to quiet your lightning"),
    BLOODMENDER("Bloodmender", 0.03, Material.REDSTONE, "Use /bloodsacrifice and /curse");

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

        addAlias(lookup, HONORED_ONE, "deathless", "immortality", "immortal", "honored", "honoured", "the honored one", "the honoured one", "limitless", "infinity", "gojo");
        addAlias(lookup, VEIL_ASSASSIN, "stormrunner", "storm runner", "flash", "speed", "haste", "assassin", "veil", "veil assassin");
        addAlias(lookup, ARCANIST, "enchanter", "enchanting");
        addAlias(lookup, BERSERKER, "berserk", "berzerker", "warborn", "war born");
        addAlias(lookup, JUGGERNAUT, "tank");
        addAlias(lookup, MORTAL, "human", "no power", "no powers", "none", "normal", "no_power", "no_powers");
        addAlias(lookup, WAYFARER, "traveler", "traveller");
        addAlias(lookup, VERDANT, "florist", "florest", "forest");
        addAlias(lookup, MONARCH, "monarch", "sovereign");
        addAlias(lookup, NIGHTSHADE, "shadow");
        addAlias(lookup, THE_WORLD, "world", "theworld", "time stop", "timestop");
        addAlias(lookup, ORACLE_EYE, "xray vision", "xray", "x ray", "x-ray", "oracle");
        addAlias(lookup, PROSPECTOR, "miner");
        addAlias(lookup, TITAN, "giant");
        addAlias(lookup, SKYBOUND, "superman");
        addAlias(lookup, TIDEBORN, "waterman", "water man");
        addAlias(lookup, ASHEN_SOUL, "phoenix");
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
