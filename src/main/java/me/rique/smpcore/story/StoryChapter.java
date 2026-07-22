package me.rique.smpcore.story;

import java.util.Locale;

public enum StoryChapter {
    PROLOGUE,
    ACT_1,
    ACT_2,
    ACT_3,
    ACT_4,
    ACT_5,
    EPILOGUE;

    public static StoryChapter parse(String value) {
        if (value == null || value.isBlank()) return PROLOGUE;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException ignored) {
            return PROLOGUE;
        }
    }
}
