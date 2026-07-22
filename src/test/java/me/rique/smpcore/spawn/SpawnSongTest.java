package me.rique.smpcore.spawn;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnSongTest {

    @Test
    void welcomeSongStaysPlayableAndUsesEveryVoice() {
        var cues = SpawnSong.veilwardWelcome();

        assertTrue(cues.size() >= 80);
        assertTrue(SpawnSong.lengthTicks() >= 20 * 15);
        assertTrue(SpawnSong.lengthTicks() <= 20 * 25);
        assertTrue(cues.stream().allMatch(cue -> cue.tick() >= 0 && cue.note() >= 0 && cue.note() <= 24));
        assertTrue(cues.stream().allMatch(cue -> cue.volume() > 0.0f && cue.volume() <= 1.0f));
        assertEquals(
            EnumSet.allOf(SpawnSong.Voice.class),
            EnumSet.copyOf(cues.stream().map(SpawnSong.Cue::voice).toList())
        );
        for (int index = 1; index < cues.size(); index++) {
            assertTrue(cues.get(index - 1).tick() <= cues.get(index).tick());
        }
    }

    @Test
    void notePitchCoversTheVanillaTwoOctaveRange() {
        assertEquals(0.5f, SpawnSong.pitchForNote(0), 0.0001f);
        assertEquals(1.0f, SpawnSong.pitchForNote(12), 0.0001f);
        assertEquals(2.0f, SpawnSong.pitchForNote(24), 0.0001f);
        assertThrows(IllegalArgumentException.class, () -> SpawnSong.pitchForNote(-1));
        assertThrows(IllegalArgumentException.class, () -> SpawnSong.pitchForNote(25));
    }
}
