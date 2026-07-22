package me.rique.smpcore.boss;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BossMusic {

    private static final int BAR_STEPS = 8;
    private static final int BAR_COUNT = 16;
    private static final int[] DORIAN = {0, 2, 3, 5, 7, 9, 10, 12};
    private static final int[] PHRYGIAN = {0, 1, 3, 5, 7, 8, 10, 12};
    private static final int[] HARMONIC_MINOR = {0, 2, 3, 5, 7, 8, 11, 12};
    private static final int[] PENTATONIC = {0, 2, 4, 7, 9, 12, 14, 16};
    private static final Map<String, Track> TRACKS = buildTracks();

    private BossMusic() {
    }

    static Track track(String bossId) {
        return bossId == null ? null : TRACKS.get(bossId);
    }

    static Map<String, Track> tracks() {
        return TRACKS;
    }

    static Set<Voice> voices() {
        return EnumSet.allOf(Voice.class);
    }

    static float pitchForNote(int note) {
        if (note < 0 || note > 24) {
            throw new IllegalArgumentException("Note-block pitch must be between 0 and 24.");
        }
        return (float) Math.pow(2.0, (note - 12) / 12.0);
    }

    private static Map<String, Track> buildTracks() {
        List<Theme> themes = List.of(
            theme(
                "yule_the_minion", "Marshal's Last Muster", 5, 6, DORIAN,
                new int[]{0, 3, 5, 2, 0, 5, 3, 2},
                new int[]{0, 2, 4, 5, 4, 2, 1, 0},
                new int[]{4, 4, 2, 3, 4, 2, 1, 0},
                Voice.BELL, Voice.CHIME, Voice.BASS, Voice.BASEDRUM, Voice.SNARE, Rhythm.MARCH
            ),
            theme(
                "kael_the_ashen", "Ash Between Heartbeats", 4, 7, PHRYGIAN,
                new int[]{0, 1, 5, 1, 6, 2, 5, 1},
                new int[]{7, -1, 5, 3, -1, 6, 2, 1},
                new int[]{0, 1, 3, 1, 0, 5, 3, 1},
                Voice.GUITAR, Voice.BIT, Voice.BASS, Voice.BASEDRUM, Voice.HAT, Rhythm.GALLOP
            ),
            theme(
                "vesper_the_widow_queen", "Silk in the Gloam", 4, 6, HARMONIC_MINOR,
                new int[]{0, 3, 6, 5, 2, 6, 1, -1},
                new int[]{4, 2, 5, 1, 6, 3, 1, 0},
                new int[]{0, 3, 1, 4, 0, 5, 3, 0},
                Voice.FLUTE, Voice.XYLOPHONE, Voice.DIDGERIDOO, Voice.HAT, Voice.SNARE, Rhythm.WEB
            ),
            theme(
                "mirewood_the_root_tyrant", "Roots Beneath the Crown", 6, 5, DORIAN,
                new int[]{0, 2, 4, 2, 5, 3, 1, 0},
                new int[]{0, -1, 3, 5, 4, -1, 2, 1},
                new int[]{0, 3, 5, 3, 2, 4, 1, 0},
                Voice.BANJO, Voice.GUITAR, Voice.BASS, Voice.DIDGERIDOO, Voice.HAT, Rhythm.ROOTS
            ),
            theme(
                "nereida_the_abyss_mother", "Drowned Procession", 6, 4, PENTATONIC,
                new int[]{0, 2, 4, 6, 4, 2, 3, 1},
                new int[]{5, 4, 2, 0, 3, 5, 4, -1},
                new int[]{0, 2, 4, 2, 0, 3, 1, 0},
                Voice.CHIME, Voice.FLUTE, Voice.DIDGERIDOO, Voice.BASEDRUM, Voice.COW_BELL, Rhythm.TIDE
            ),
            theme(
                "iron_saint", "Argent Litany", 5, 6, HARMONIC_MINOR,
                new int[]{0, 4, 2, 5, 3, 6, 2, 1},
                new int[]{0, -1, 5, 4, 2, -1, 6, 1},
                new int[]{0, 5, 3, 1, 4, 2, 1, 0},
                Voice.IRON_XYLOPHONE, Voice.BELL, Voice.BASS, Voice.BASEDRUM, Voice.COW_BELL, Rhythm.FORGE
            ),
            theme(
                "aurelion_the_rift_seraph", "The Rift Watches", 4, 6, HARMONIC_MINOR,
                new int[]{0, 5, 2, 7, 3, 6, 1, 4},
                new int[]{7, 3, 6, 2, 5, 1, 4, 0},
                new int[]{0, 4, 1, 5, 3, 1, 6, 0},
                Voice.PLING, Voice.CHIME, Voice.BIT, Voice.HAT, Voice.IRON_XYLOPHONE, Rhythm.RIFT
            ),
            theme(
                "morvessa_the_runebloom_witch", "Runebloom Hex", 5, 6, PHRYGIAN,
                new int[]{0, 2, 6, 3, 5, 1, 7, 2},
                new int[]{4, 6, 2, 5, 1, 3, 0, -1},
                new int[]{0, 1, 4, 2, 5, 3, 1, 0},
                Voice.FLUTE, Voice.BANJO, Voice.DIDGERIDOO, Voice.HAT, Voice.COW_BELL, Rhythm.HEX
            ),
            theme(
                "voralith_the_crimson_warden", "Dominion Below", 5, 5, PHRYGIAN,
                new int[]{0, 1, 4, 1, 5, 2, 6, 1},
                new int[]{0, -1, 6, 5, 2, -1, 4, 1},
                new int[]{0, 1, 3, 1, 5, 2, 1, 0},
                Voice.DIDGERIDOO, Voice.BIT, Voice.BASS, Voice.BASEDRUM, Voice.SNARE, Rhythm.HEARTBEAT
            ),
            theme(
                "corrupted_oathkeeper", "Oath in Cinders", 4, 6, HARMONIC_MINOR,
                new int[]{0, 4, 6, 3, 7, 5, 2, 1},
                new int[]{7, 6, 4, 2, 5, 3, 1, 0},
                new int[]{0, 5, 3, 1, 6, 4, 2, 0},
                Voice.COW_BELL, Voice.IRON_XYLOPHONE, Voice.DIDGERIDOO, Voice.BASEDRUM, Voice.SNARE, Rhythm.OATH
            )
        );

        Map<String, Track> tracks = new LinkedHashMap<>();
        for (Theme theme : themes) {
            Track previous = tracks.put(theme.bossId(), buildTrack(theme));
            if (previous != null) {
                throw new IllegalStateException("Duplicate boss music track: " + theme.bossId());
            }
        }
        return Map.copyOf(tracks);
    }

    private static Theme theme(
        String bossId,
        String title,
        int stepTicks,
        int baseNote,
        int[] scale,
        int[] melody,
        int[] answer,
        int[] progression,
        Voice leadVoice,
        Voice harmonyVoice,
        Voice bassVoice,
        Voice pulseVoice,
        Voice accentVoice,
        Rhythm rhythm
    ) {
        return new Theme(
            bossId,
            title,
            stepTicks,
            baseNote,
            scale,
            melody,
            answer,
            progression,
            leadVoice,
            harmonyVoice,
            bassVoice,
            pulseVoice,
            accentVoice,
            rhythm
        );
    }

    private static Track buildTrack(Theme theme) {
        List<Cue> cues = new ArrayList<>();
        for (int bar = 0; bar < BAR_COUNT; bar++) {
            int section = bar / 8;
            int progressionIndex = bar % theme.progression().length;
            int transposition = theme.progression()[progressionIndex];
            int[] motif = ((bar + section) & 1) == 0 ? theme.melody() : theme.answer();
            int barStartStep = bar * BAR_STEPS;

            for (int step = 0; step < BAR_STEPS; step++) {
                int scaleIndex = motif[step];
                if (scaleIndex < 0) {
                    continue;
                }
                if (section == 1 && (step == 0 || step == 6)) {
                    scaleIndex = Math.min(theme.scale().length - 1, scaleIndex + 1);
                }
                int note = theme.baseNote() + transposition + theme.scale()[scaleIndex];
                add(cues, theme, barStartStep + step, theme.leadVoice(), note, step % 4 == 0 ? 0.78f : 0.66f);
            }

            int harmonyRoot = theme.baseNote() - 2 + transposition;
            add(cues, theme, barStartStep, theme.harmonyVoice(), harmonyRoot, 0.30f);
            add(cues, theme, barStartStep, theme.harmonyVoice(), harmonyRoot + theme.scale()[2], 0.27f);
            add(cues, theme, barStartStep, theme.harmonyVoice(), harmonyRoot + theme.scale()[4], 0.24f);

            int bassRoot = Math.max(0, theme.baseNote() - 5 + transposition);
            add(cues, theme, barStartStep, theme.bassVoice(), bassRoot, 0.50f);
            add(cues, theme, barStartStep + 4, theme.bassVoice(), bassRoot, 0.40f);
            addRhythm(cues, theme, barStartStep, bar);
        }

        cues.sort(
            Comparator.comparingInt(Cue::tick)
                .thenComparing(cue -> cue.voice().ordinal())
                .thenComparingInt(Cue::note)
        );
        return new Track(theme.bossId(), theme.title(), BAR_COUNT * BAR_STEPS * theme.stepTicks(), cues);
    }

    private static void addRhythm(List<Cue> cues, Theme theme, int barStartStep, int bar) {
        int[] pulses;
        int[] accents;
        switch (theme.rhythm()) {
            case MARCH -> {
                pulses = new int[]{0, 4};
                accents = new int[]{2, 6};
            }
            case GALLOP -> {
                pulses = new int[]{0, 3, 6};
                accents = new int[]{2, 5, 7};
            }
            case WEB -> {
                pulses = new int[]{0, 5};
                accents = new int[]{2, 4, 7};
            }
            case ROOTS -> {
                pulses = new int[]{0, 4, 7};
                accents = new int[]{2, 6};
            }
            case TIDE -> {
                pulses = new int[]{0, 5};
                accents = new int[]{3, 7};
            }
            case FORGE -> {
                pulses = new int[]{0, 4};
                accents = new int[]{1, 3, 5, 7};
            }
            case RIFT -> {
                pulses = new int[]{0, 3, 7};
                accents = new int[]{2, 5};
            }
            case HEX -> {
                pulses = new int[]{0, 4, 6};
                accents = new int[]{1, 3, 7};
            }
            case HEARTBEAT -> {
                pulses = new int[]{0, 2, 5, 6};
                accents = new int[]{3, 7};
            }
            case OATH -> {
                pulses = new int[]{0, 3, 4, 7};
                accents = new int[]{1, 2, 5, 6};
            }
            default -> throw new IllegalStateException("Unknown boss rhythm: " + theme.rhythm());
        }

        for (int step : pulses) {
            add(cues, theme, barStartStep + step, theme.pulseVoice(), 7 + (bar & 1), 0.34f);
        }
        for (int step : accents) {
            add(cues, theme, barStartStep + step, theme.accentVoice(), 12 + (bar % 3), 0.28f);
        }
    }

    private static void add(List<Cue> cues, Theme theme, int step, Voice voice, int note, float volume) {
        cues.add(new Cue(step * theme.stepTicks(), voice, note, volume));
    }

    enum Voice {
        HARP,
        BASS,
        BASEDRUM,
        SNARE,
        HAT,
        GUITAR,
        BELL,
        CHIME,
        XYLOPHONE,
        IRON_XYLOPHONE,
        COW_BELL,
        DIDGERIDOO,
        BIT,
        BANJO,
        PLING,
        FLUTE
    }

    private enum Rhythm {
        MARCH,
        GALLOP,
        WEB,
        ROOTS,
        TIDE,
        FORGE,
        RIFT,
        HEX,
        HEARTBEAT,
        OATH
    }

    record Cue(int tick, Voice voice, int note, float volume) {
        Cue {
            if (tick < 0 || voice == null || note < 0 || note > 24 || volume <= 0.0f || volume > 1.0f) {
                throw new IllegalArgumentException("Invalid boss music cue.");
            }
        }
    }

    record Track(String bossId, String title, int lengthTicks, List<Cue> cues) {
        Track {
            if (bossId == null || bossId.isBlank() || title == null || title.isBlank() || lengthTicks < 20 || cues == null || cues.isEmpty()) {
                throw new IllegalArgumentException("Invalid boss music track.");
            }
            cues = List.copyOf(cues);
        }
    }

    private record Theme(
        String bossId,
        String title,
        int stepTicks,
        int baseNote,
        int[] scale,
        int[] melody,
        int[] answer,
        int[] progression,
        Voice leadVoice,
        Voice harmonyVoice,
        Voice bassVoice,
        Voice pulseVoice,
        Voice accentVoice,
        Rhythm rhythm
    ) {
        private Theme {
            if (bossId == null || bossId.isBlank() || title == null || title.isBlank() || stepTicks < 2
                || baseNote < 0 || baseNote > 12 || scale == null || scale.length != 8
                || melody == null || melody.length != BAR_STEPS || answer == null || answer.length != BAR_STEPS
                || progression == null || progression.length != 8 || leadVoice == null || harmonyVoice == null
                || bassVoice == null || pulseVoice == null || accentVoice == null || rhythm == null) {
                throw new IllegalArgumentException("Invalid boss music theme.");
            }
            scale = Arrays.copyOf(scale, scale.length);
            melody = Arrays.copyOf(melody, melody.length);
            answer = Arrays.copyOf(answer, answer.length);
            progression = Arrays.copyOf(progression, progression.length);
        }
    }
}
