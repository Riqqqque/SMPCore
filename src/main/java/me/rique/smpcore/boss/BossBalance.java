package me.rique.smpcore.boss;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BossBalance {
    public static final double BRIARHEART_DROP_CHANCE = 0.70;
    public static final double TIDEVEIL_HEART_DROP_CHANCE = 0.70;
    public static final double RUNEBLOOM_WITCH_ORB_DROP_CHANCE = 0.05;
    private static final double GROUP_LOOT_DAMAGE_SHARE = 0.025;
    private static final double GROUP_LOOT_MINIMUM_DAMAGE = 10.0;
    private static final double GROUP_LOOT_PER_EXTRA_PLAYER = 0.25;
    private static final int GROUP_LOOT_PLAYER_CAP = 5;

    private static final List<Profile> PROGRESSION = List.of(
        new Profile("yule_the_minion", 1, 570.0, 9.25, "enchanted diamond gear"),
        new Profile("kael_the_ashen", 2, 685.0, 10.0, "Marshal relics or strong diamond gear"),
        new Profile("vesper_the_widow_queen", 3, 855.0, 11.75, "Cinderveil gear and fire control"),
        new Profile("mirewood_the_root_tyrant", 4, 1045.0, 12.75, "Gloam Court gear"),
        new Profile("nereida_the_abyss_mother", 5, 1235.0, 14.5, "Gloam or Briarveil gear"),
        new Profile("iron_saint", 6, 1475.0, 16.25, "Depthveil Pact gear"),
        new Profile("aurelion_the_rift_seraph", 7, 1710.0, 18.0, "Cinder Confessor gear"),
        new Profile("morvessa_the_runebloom_witch", 8, 2000.0, 27.0, "Riftveil Step gear"),
        new Profile("voralith_the_crimson_warden", 9, 2280.0, 29.0, "Riftveil Step gear with upgraded enchants"),
        new Profile("corrupted_oathkeeper", 10, 3990.0, 31.5, "Nocturne Guard or Eclipse gear")
    );
    private static final Map<String, Profile> BY_ID;

    static {
        Map<String, Profile> byId = new LinkedHashMap<>();
        for (Profile profile : PROGRESSION) {
            byId.put(profile.bossId(), profile);
        }
        BY_ID = Map.copyOf(byId);
    }

    private BossBalance() {
    }

    public static List<Profile> progression() {
        return PROGRESSION;
    }

    public static Profile profile(String bossId) {
        Profile profile = bossId == null ? null : BY_ID.get(bossId);
        if (profile == null) {
            throw new IllegalArgumentException("No boss balance profile for " + bossId);
        }
        return profile;
    }

    public static List<MiscDrop> miscDrops(String bossId) {
        if (bossId == null) {
            return List.of();
        }
        return switch (bossId) {
            case "yule_the_minion" -> List.of(
                new MiscDrop(Material.GOLD_INGOT, 6, 10, 1.0)
            );
            case "kael_the_ashen" -> List.of(
                new MiscDrop(Material.ARROW, 16, 24, 1.0),
                new MiscDrop(Material.GUNPOWDER, 4, 6, 0.50)
            );
            case "vesper_the_widow_queen" -> List.of(
                new MiscDrop(Material.COBWEB, 3, 5, 1.0),
                new MiscDrop(Material.EMERALD, 3, 5, 0.50)
            );
            case "mirewood_the_root_tyrant" -> List.of(
                new MiscDrop(Material.GOLDEN_CARROT, 8, 12, 1.0),
                new MiscDrop(Material.BONE_BLOCK, 4, 6, 0.50)
            );
            case "nereida_the_abyss_mother" -> List.of(
                new MiscDrop(Material.PRISMARINE_CRYSTALS, 8, 12, 1.0),
                new MiscDrop(Material.NAUTILUS_SHELL, 1, 2, 0.50)
            );
            case "iron_saint" -> List.of(
                new MiscDrop(Material.IRON_BLOCK, 3, 5, 1.0),
                new MiscDrop(Material.DIAMOND, 2, 3, 0.50)
            );
            case "aurelion_the_rift_seraph" -> List.of(
                new MiscDrop(Material.ENDER_PEARL, 8, 12, 1.0),
                new MiscDrop(Material.SHULKER_SHELL, 1, 2, 0.50)
            );
            case "morvessa_the_runebloom_witch" -> List.of(
                new MiscDrop(Material.EXPERIENCE_BOTTLE, 12, 18, 1.0),
                new MiscDrop(Material.GHAST_TEAR, 2, 4, 0.50)
            );
            case "voralith_the_crimson_warden" -> List.of(
                new MiscDrop(Material.ECHO_SHARD, 3, 5, 1.0),
                new MiscDrop(Material.DIAMOND, 4, 6, 0.50)
            );
            case "corrupted_oathkeeper" -> List.of(
                new MiscDrop(Material.NETHERITE_SCRAP, 2, 3, 1.0),
                new MiscDrop(Material.ENCHANTED_GOLDEN_APPLE, 1, 1, 0.20)
            );
            default -> List.of();
        };
    }

    public static boolean allowsRelicDrop(String bossId, String relicId) {
        if (bossId == null || relicId == null) {
            return false;
        }
        Set<String> allowed = switch (bossId) {
            case "yule_the_minion" -> Set.of("gilded_skull", "oathbound_plate");
            case "kael_the_ashen" -> Set.of("solar_ember");
            case "vesper_the_widow_queen" -> Set.of("widow_silk");
            case "voralith_the_crimson_warden" -> Set.of("crimson_rib", "sculk_heart");
            case "aurelion_the_rift_seraph" -> Set.of("rift_lens", "void_halo", "awakening_shard");
            case "morvessa_the_runebloom_witch" -> Set.of("rift_lens", "runebloom_orb");
            case "nereida_the_abyss_mother" -> Set.of("abyssal_pearl", "tideheart");
            case "iron_saint" -> Set.of("titan_gear", "saint_alloy");
            case "mirewood_the_root_tyrant" -> Set.of("living_bark", "verdant_heart");
            case "corrupted_oathkeeper" -> Set.of("corrupted_essence", "soul_imprint");
            default -> Set.of();
        };
        return allowed.contains(relicId);
    }

    public static double mechanicFailureHealthRatio(int tier) {
        return switch (Math.max(1, tier)) {
            case 1 -> 0.45;
            case 2 -> 0.72;
            case 3 -> 0.78;
            case 4 -> 0.84;
            case 5 -> 0.88;
            case 6 -> 0.92;
            case 7 -> 0.96;
            default -> 0.98;
        };
    }

    public static double mechanicHazardHealthRatio(int tier) {
        return Math.min(0.30, 0.12 + Math.max(0, tier - 2) * 0.025);
    }

    public static double multiplayerHealthScale(int tier, int playerCount) {
        int extraPlayers = Math.min(6, Math.max(0, playerCount - 1));
        double perExtraPlayer = tier <= 1 ? 0.44 : Math.min(0.64, 0.50 + Math.max(1, tier) * 0.015);
        return 1.0 + extraPlayers * perExtraPlayer;
    }

    public static double multiplayerDamageScale(int tier, int playerCount) {
        int extraPlayers = Math.min(6, Math.max(0, playerCount - 1));
        double perExtraPlayer = tier <= 1 ? 0.09 : Math.min(0.13, 0.075 + Math.max(1, tier) * 0.005);
        return 1.0 + extraPlayers * perExtraPlayer;
    }

    public static boolean qualifiesForGroupLoot(double damageDone, double totalDamage) {
        if (!Double.isFinite(damageDone)
            || !Double.isFinite(totalDamage)
            || damageDone <= 0.0
            || totalDamage <= 0.0) {
            return false;
        }
        double requiredDamage = Math.max(GROUP_LOOT_MINIMUM_DAMAGE, totalDamage * GROUP_LOOT_DAMAGE_SHARE);
        return damageDone >= requiredDamage;
    }

    public static double multiplayerLootScale(int qualifiedPlayers) {
        int extraPlayers = Math.min(GROUP_LOOT_PLAYER_CAP - 1, Math.max(0, qualifiedPlayers - 1));
        return 1.0 + extraPlayers * GROUP_LOOT_PER_EXTRA_PLAYER;
    }

    public static int scaledBossMaterialAmount(int baseAmount, int qualifiedPlayers, double roundingRoll) {
        if (baseAmount <= 0) {
            return 0;
        }
        double scaledAmount = baseAmount * multiplayerLootScale(qualifiedPlayers);
        int amount = (int) Math.floor(scaledAmount);
        double fractionalAmount = scaledAmount - amount;
        double normalizedRoll = Double.isFinite(roundingRoll)
            ? Math.max(0.0, Math.min(1.0, roundingRoll))
            : 1.0;
        return amount + (normalizedRoll < fractionalAmount ? 1 : 0);
    }

    public static double routineAbilityDamageScale(int tier) {
        int clampedTier = Math.max(1, tier);
        if (clampedTier <= 4) {
            return 0.94;
        }
        if (clampedTier <= 7) {
            return 0.92;
        }
        return 0.90;
    }

    public static int requiredArmorPathTier(int bossTier) {
        return switch (Math.max(1, bossTier)) {
            case 1, 2, 3 -> 0;
            case 4, 5 -> 3;
            case 6 -> 5;
            case 7 -> 6;
            case 8, 9 -> 7;
            default -> 9;
        };
    }

    public static double missingArmorDamageMultiplier(int bossTier, boolean hasRequiredArmor) {
        int requiredTier = requiredArmorPathTier(bossTier);
        if (requiredTier <= 0 || hasRequiredArmor) {
            return 1.0;
        }
        int clampedTier = Math.max(4, Math.min(10, bossTier));
        return 1.0 + (clampedTier - 1) * 0.05;
    }

    public static double reportedDamage(double currentHealth, double actualDamage, double actualMaxHealth, double displayMaxHealth) {
        if (!Double.isFinite(currentHealth)
            || !Double.isFinite(actualDamage)
            || !Double.isFinite(actualMaxHealth)
            || !Double.isFinite(displayMaxHealth)
            || currentHealth <= 0.0
            || actualDamage <= 0.0
            || actualMaxHealth <= 0.0
            || displayMaxHealth <= 0.0) {
            return 0.0;
        }

        double effectiveDamage = Math.min(currentHealth, actualDamage);
        double reported = effectiveDamage * (displayMaxHealth / actualMaxHealth);
        return Double.isFinite(reported) ? Math.max(0.0, reported) : 0.0;
    }

    public record Profile(String bossId, int tier, double maxHealth, double attackDamage, String recommendedGear) {
    }

    public record MiscDrop(Material material, int minimumAmount, int maximumAmount, double chance) {
    }
}
