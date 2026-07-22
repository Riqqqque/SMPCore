package me.rique.smpcore.util;

import java.util.Map;

public final class ItemEnchantConflictPolicy {

    private static final Map<String, Integer> VANILLA_PREFERENCES = Map.ofEntries(
        Map.entry("mending", 100),
        Map.entry("fortune", 90),
        Map.entry("sharpness", 80),
        Map.entry("protection", 80),
        Map.entry("density", 80),
        Map.entry("piercing", 70),
        Map.entry("loyalty", 70),
        Map.entry("depth_strider", 70)
    );

    private ItemEnchantConflictPolicy() {
    }

    public static boolean customConflictsWithVanilla(String customKey, String vanillaKey) {
        return "smelting_touch_enchant".equals(customKey)
            && "silk_touch".equals(vanillaKey);
    }

    static int compareVanillaCandidates(String leftKey, int leftLevel, String rightKey, int rightLevel) {
        int byLevel = Integer.compare(Math.max(0, rightLevel), Math.max(0, leftLevel));
        if (byLevel != 0) {
            return byLevel;
        }
        int byPreference = Integer.compare(preference(rightKey), preference(leftKey));
        if (byPreference != 0) {
            return byPreference;
        }
        return safeKey(leftKey).compareTo(safeKey(rightKey));
    }

    private static int preference(String key) {
        return VANILLA_PREFERENCES.getOrDefault(safeKey(key), 0);
    }

    private static String safeKey(String key) {
        return key == null ? "" : key;
    }
}
