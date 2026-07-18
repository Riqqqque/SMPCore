package me.rique.smpcore.boss;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BossBalance {
    public static final double RUNEBLOOM_WITCH_ORB_DROP_CHANCE = 0.05;

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
}
