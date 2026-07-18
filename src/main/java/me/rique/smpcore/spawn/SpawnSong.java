package me.rique.smpcore.spawn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class SpawnSong {

    private static final int STEP_TICKS = 6;
    private static final List<Cue> VEILWARD_WELCOME = buildVeilwardWelcome();
    private static final int LENGTH_TICKS = VEILWARD_WELCOME.getLast().tick() + 24;

    private SpawnSong() {
    }

    static List<Cue> veilwardWelcome() {
        return VEILWARD_WELCOME;
    }

    static int lengthTicks() {
        return LENGTH_TICKS;
    }

    static float pitchForNote(int note) {
        if (note < 0 || note > 24) {
            throw new IllegalArgumentException("Note-block pitch must be between 0 and 24.");
        }
        return (float) Math.pow(2.0, (note - 12) / 12.0);
    }

    private static List<Cue> buildVeilwardWelcome() {
        List<Cue> cues = new ArrayList<>();
        int[][] melody = {
            {12, -1, 16, 19, 16, 14, 12, -1},
            {9, 12, 14, 16, 14, 12, 9, -1},
            {7, 11, 12, 16, 14, 12, 11, -1},
            {11, 14, 16, 19, 18, 16, 14, -1},
            {16, 19, 21, 19, 16, 14, 12, 14},
            {14, 16, 19, 16, 14, 12, 9, 12},
            {12, 16, 19, 21, 19, 16, 14, 11},
            {14, 16, 19, 18, 16, 14, 12, 12}
        };
        int[][] chords = {
            {9, 12, 16},
            {5, 9, 12},
            {7, 11, 14},
            {4, 7, 11},
            {9, 12, 16},
            {5, 9, 12},
            {7, 11, 14},
            {9, 12, 16}
        };
        int[] bassRoots = {9, 5, 7, 4, 9, 5, 7, 9};

        for (int bar = 0; bar < melody.length; bar++) {
            int barStartStep = bar * 8;
            for (int step = 0; step < melody[bar].length; step++) {
                int note = melody[bar][step];
                if (note >= 0) {
                    add(cues, barStartStep + step, Voice.FLUTE, note, 0.72f);
                    if ((step == 2 || step == 6) && note >= 12) {
                        add(cues, barStartStep + step, Voice.HARP, note - 12, 0.30f);
                    }
                }
            }
            for (int note : chords[bar]) {
                add(cues, barStartStep, Voice.CHIME, note, 0.42f);
            }
            add(cues, barStartStep, Voice.BASS, bassRoots[bar], 0.56f);
            add(cues, barStartStep + 4, Voice.BASS, bassRoots[bar], 0.44f);
        }

        int finalStep = 63;
        add(cues, finalStep, Voice.BELL, 12, 0.52f);
        add(cues, finalStep, Voice.BELL, 16, 0.44f);
        add(cues, finalStep, Voice.CHIME, 21, 0.36f);
        cues.sort(Comparator.comparingInt(Cue::tick).thenComparing(cue -> cue.voice().ordinal()));
        return List.copyOf(cues);
    }

    private static void add(List<Cue> cues, int step, Voice voice, int note, float volume) {
        cues.add(new Cue(step * STEP_TICKS, voice, note, volume));
    }

    enum Voice {
        HARP,
        CHIME,
        FLUTE,
        BASS,
        BELL
    }

    record Cue(int tick, Voice voice, int note, float volume) {
        Cue {
            if (tick < 0 || voice == null || note < 0 || note > 24 || volume <= 0.0f || volume > 1.0f) {
                throw new IllegalArgumentException("Invalid spawn song cue.");
            }
        }
    }
}
