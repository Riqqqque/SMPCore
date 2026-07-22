package me.rique.smpcore.spawner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

final class SpawnerDataTest {

    @Test
    void snapshotDoesNotChangeWhenLiveDataMutates() {
        SpawnerData live = new SpawnerData("world", 1, 2, 3, "ZOMBIE", 4, 5, false, false);
        SpawnerData snapshot = live.snapshot();

        live.setEntityType("SKELETON");
        live.setStackCount(12);
        live.addSugar(10, 32);
        live.toggleRedstone();

        assertNotSame(live, snapshot);
        assertEquals("ZOMBIE", snapshot.entityType());
        assertEquals(4, snapshot.stackCount());
        assertEquals(5, snapshot.sugarCount());
        assertFalse(snapshot.redstoneControlled());
    }

    @Test
    void speedAndDelayStayWithinConfiguredBounds() {
        SpawnerData data = new SpawnerData("world", 0, 64, 0, "PIG", 1, 64, false, false);

        assertEquals(16.0, data.speedMultiplier(32, 16.0), 0.0001);
        assertEquals(13, data.adjustedDelay(200, 32, 16.0));
        assertEquals(50, data.adjustedDelay(800, 32, 16.0));
        assertEquals(1000.0 / 90.0, data.effectiveSpeedMultiplier(200, 800, 32, 16.0, 40), 0.0001);
    }

    @Test
    void effectiveSpeedReportsTheActualConfiguredCycleRate() {
        SpawnerData noSugar = new SpawnerData("world", 0, 64, 0, "PIG", 1, 0, false, false);
        SpawnerData maxSugar = new SpawnerData("world", 0, 64, 0, "PIG", 1, 32, false, false);

        assertEquals(1.0, noSugar.effectiveSpeedMultiplier(200, 800, 32, 16.0, 40), 0.0001);
        assertEquals(1000.0 / 90.0, maxSugar.effectiveSpeedMultiplier(200, 800, 32, 16.0, 40), 0.0001);
    }

    @Test
    void malformedValuesAreNormalizedBeforeUse() {
        assertEquals("PIG", SpawnerManager.normalizeEntityType(null));
        assertEquals("PIG", SpawnerManager.normalizeEntityType("player"));
        assertEquals("ZOMBIE", SpawnerManager.normalizeEntityType(" zombie "));
        assertEquals(1, SpawnerManager.clampStackCount(Integer.MIN_VALUE, 64));
        assertEquals(64, SpawnerManager.clampStackCount(Integer.MAX_VALUE, 64));
        assertEquals(0, SpawnerManager.clampSugarCount(Integer.MIN_VALUE, 32));
        assertEquals(32, SpawnerManager.clampSugarCount(Integer.MAX_VALUE, 32));
        assertEquals(330, SpawnerManager.breakExperience(Integer.MAX_VALUE, 64));
    }
}
