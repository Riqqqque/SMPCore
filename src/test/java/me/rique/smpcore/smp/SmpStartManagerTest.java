package me.rique.smpcore.smp;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmpStartManagerTest {

    @Test
    void dayThreeAndDayFiveUseRealTwentyFourHourDays() {
        long startedAt = 1_000_000L;

        assertEquals(startedAt + Duration.ofDays(2).toMillis(), SmpStartManager.unlockAtEpochMillis(startedAt, 3));
        assertEquals(startedAt + Duration.ofDays(4).toMillis(), SmpStartManager.unlockAtEpochMillis(startedAt, 5));
    }

    @Test
    void remainingTimeStopsAtZero() {
        long startedAt = 1_000_000L;
        long unlockAt = startedAt + Duration.ofDays(2).toMillis();

        assertEquals(Duration.ofDays(1).toMillis(), SmpStartManager.millisUntilUnlock(startedAt + Duration.ofDays(1).toMillis(), startedAt, 3));
        assertEquals(0L, SmpStartManager.millisUntilUnlock(unlockAt, startedAt, 3));
        assertEquals(0L, SmpStartManager.millisUntilUnlock(unlockAt + 1L, startedAt, 3));
    }

    @Test
    void earlyUnlockOnlyAppliesAfterTheSmpHasStarted() {
        long startedAt = 1_000_000L;
        long now = startedAt + Duration.ofHours(1).toMillis();

        assertEquals(0L, SmpStartManager.dimensionUnlockMillisRemaining(now, true, startedAt, 5, true));
        assertEquals(Long.MAX_VALUE, SmpStartManager.dimensionUnlockMillisRemaining(now, false, 0L, 5, true));
        assertEquals(Duration.ofDays(4).minusHours(1).toMillis(),
            SmpStartManager.dimensionUnlockMillisRemaining(now, true, startedAt, 5, false));
    }

    @Test
    void exactUnlockTimestampOverridesTheDayBasedSchedule() {
        long startedAt = 1_000_000L;
        long exactUnlockAt = 50_000_000L;

        assertEquals(10_000L,
            SmpStartManager.dimensionUnlockMillisRemaining(
                exactUnlockAt - 10_000L,
                true,
                startedAt,
                5,
                exactUnlockAt,
                false
            ));
        assertEquals(0L,
            SmpStartManager.dimensionUnlockMillisRemaining(
                exactUnlockAt,
                true,
                startedAt,
                5,
                exactUnlockAt,
                false
            ));
    }

    @Test
    void exactUnlockStillRequiresSmpStartAndHonorsEarlyUnlock() {
        long exactUnlockAt = 50_000_000L;

        assertEquals(Long.MAX_VALUE,
            SmpStartManager.dimensionUnlockMillisRemaining(1_000L, false, 0L, 5, exactUnlockAt, false));
        assertEquals(0L,
            SmpStartManager.dimensionUnlockMillisRemaining(1_000L, true, 500L, 5, exactUnlockAt, true));
    }

    @Test
    void preStartBarrierCoversTheWholeFifteenBlockSquare() {
        assertTrue(SmpStartManager.isInsideSquareBarrier(15.0D, 15.0D, 0.0D, 0.0D, 15.0D, 0.0D));
        assertTrue(SmpStartManager.isInsideSquareBarrier(-15.0D, -15.0D, 0.0D, 0.0D, 15.0D, 0.0D));
        assertFalse(SmpStartManager.isInsideSquareBarrier(15.01D, 0.0D, 0.0D, 0.0D, 15.0D, 0.0D));
        assertFalse(SmpStartManager.isInsideSquareBarrier(0.0D, -15.01D, 0.0D, 0.0D, 15.0D, 0.0D));
    }

    @Test
    void barrierPaddingIsExplicitAndRejectsInvalidCoordinates() {
        assertTrue(SmpStartManager.isInsideSquareBarrier(16.0D, 16.0D, 0.0D, 0.0D, 15.0D, 1.0D));
        assertFalse(SmpStartManager.isInsideSquareBarrier(Double.NaN, 0.0D, 0.0D, 0.0D, 15.0D, 0.0D));
    }

    @Test
    void seasonIntroductionWaitsForSmpStart() {
        assertFalse(SmpStartManager.shouldDeliverSeasonIntroduction(true, false, 0L, null));
        assertTrue(SmpStartManager.shouldDeliverSeasonIntroduction(true, true, 1234L, null));
    }

    @Test
    void seasonIntroductionRunsOncePerStartGeneration() {
        assertFalse(SmpStartManager.shouldDeliverSeasonIntroduction(true, true, 1234L, 1234L));
        assertTrue(SmpStartManager.shouldDeliverSeasonIntroduction(true, true, 5678L, 1234L));
        assertTrue(SmpStartManager.shouldDeliverSeasonIntroduction(false, false, 0L, null));
        assertFalse(SmpStartManager.shouldDeliverSeasonIntroduction(false, false, 0L, 1L));
    }
}
