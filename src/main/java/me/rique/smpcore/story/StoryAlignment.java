package me.rique.smpcore.story;

import java.util.Locale;

public enum StoryAlignment {
    UNDECIDED,
    MEND,
    BIND,
    SEVER;

    public static StoryAlignment parse(String value) {
        if (value == null || value.isBlank()) return UNDECIDED;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return UNDECIDED;
        }
    }
}
