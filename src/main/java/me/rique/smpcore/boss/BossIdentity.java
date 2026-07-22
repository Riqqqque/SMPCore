package me.rique.smpcore.boss;

import java.util.Locale;

final class BossIdentity {
    private static final String ASTERION_SAVE_ID = "aurelion_the_rift_seraph";

    private BossIdentity() { }

    static String normalizeInput(String input) {
        if (input == null) return "";
        return input.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    static String canonicalId(String input) {
        return switch (normalizeInput(input)) {
            case "asterion", "asterion_the_rift_oracle", "rift_oracle", "aurelion" -> ASTERION_SAVE_ID;
            default -> normalizeInput(input);
        };
    }
}
