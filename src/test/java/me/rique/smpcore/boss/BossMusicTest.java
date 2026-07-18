package me.rique.smpcore.boss;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossMusicTest {

    @Test
    void everyBossHasAPlayableDistinctTheme() {
        Set<String> bossIds = Set.of(
            "yule_the_minion",
            "kael_the_ashen",
            "vesper_the_widow_queen",
            "mirewood_the_root_tyrant",
            "nereida_the_abyss_mother",
            "iron_saint",
            "aurelion_the_rift_seraph",
            "morvessa_the_runebloom_witch",
            "voralith_the_crimson_warden",
            "corrupted_oathkeeper"
        );

        assertEquals(10, bossIds.size());
        assertEquals(bossIds, BossMusic.tracks().keySet());

        Set<String> titles = new HashSet<>();
        Set<String> fingerprints = new HashSet<>();
        for (String bossId : bossIds) {
            BossMusic.Track track = BossMusic.track(bossId);
            assertNotNull(track);
            assertTrue(titles.add(track.title()));
            assertTrue(track.lengthTicks() >= 20 * 25);
            assertTrue(track.lengthTicks() <= 20 * 50);
            assertTrue(track.cues().size() >= 150);
            assertTrue(track.cues().getFirst().tick() == 0);
            assertTrue(track.cues().getLast().tick() < track.lengthTicks());
            assertTrue(track.cues().stream().allMatch(cue -> cue.note() >= 0 && cue.note() <= 24));
            assertTrue(track.cues().stream().allMatch(cue -> cue.volume() > 0.0f && cue.volume() <= 1.0f));
            assertTrue(track.cues().stream().map(BossMusic.Cue::voice).distinct().count() >= 5);
            for (int index = 1; index < track.cues().size(); index++) {
                assertTrue(track.cues().get(index - 1).tick() <= track.cues().get(index).tick());
            }
            String fingerprint = track.cues().stream()
                .map(cue -> cue.tick() + ":" + cue.voice() + ":" + cue.note())
                .collect(java.util.stream.Collectors.joining("|"));
            assertTrue(fingerprints.add(fingerprint));
        }
    }

    @Test
    void lookupAndPitchValidationAreSafe() {
        assertEquals(0.5f, BossMusic.pitchForNote(0), 0.0001f);
        assertEquals(1.0f, BossMusic.pitchForNote(12), 0.0001f);
        assertEquals(2.0f, BossMusic.pitchForNote(24), 0.0001f);
        assertThrows(IllegalArgumentException.class, () -> BossMusic.pitchForNote(-1));
        assertThrows(IllegalArgumentException.class, () -> BossMusic.pitchForNote(25));
        assertNotNull(BossMusic.track("corrupted_oathkeeper"));
        assertNull(BossMusic.track("missing_boss"));
    }
}
