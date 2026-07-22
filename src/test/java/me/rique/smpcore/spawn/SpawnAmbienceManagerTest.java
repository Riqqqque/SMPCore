package me.rique.smpcore.spawn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnAmbienceManagerTest {

    @Test
    void spawnSongStaysFullThroughRadiusThenFadesSmoothly() {
        assertEquals(1.0, SpawnAmbienceManager.songFalloff(0.0, 100.0, 20.0));
        assertEquals(1.0, SpawnAmbienceManager.songFalloff(100.0, 100.0, 20.0));
        assertEquals(0.5, SpawnAmbienceManager.songFalloff(110.0, 100.0, 20.0));
        assertEquals(0.0, SpawnAmbienceManager.songFalloff(120.0, 100.0, 20.0));
        assertEquals(0.0, SpawnAmbienceManager.songFalloff(140.0, 100.0, 20.0));
    }

    @Test
    void spawnSongFadeIsMonotonicAndRejectsInvalidDistances() {
        double nearEdge = SpawnAmbienceManager.songFalloff(104.0, 100.0, 20.0);
        double middle = SpawnAmbienceManager.songFalloff(110.0, 100.0, 20.0);
        double farEdge = SpawnAmbienceManager.songFalloff(116.0, 100.0, 20.0);

        assertTrue(nearEdge > middle);
        assertTrue(middle > farEdge);
        assertEquals(0.0, SpawnAmbienceManager.songFalloff(Double.NaN, 100.0, 20.0));
        assertEquals(0.0, SpawnAmbienceManager.songFalloff(-1.0, 100.0, 20.0));
    }
}
