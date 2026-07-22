package me.rique.smpcore.backpack;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackpackRecoveryJournalTest {

    @Test
    void skipsDiskWritesWhenTheSerializedPayloadDidNotChange() {
        byte[] first = {1, 2, 3, 4};
        byte[] same = {1, 2, 3, 4};
        byte[] changed = {1, 2, 3, 5};

        assertFalse(BackpackRecoveryJournal.contentChanged(first, same));
        assertTrue(BackpackRecoveryJournal.contentChanged(first, changed));
        assertTrue(BackpackRecoveryJournal.contentChanged(null, same));
    }

    @Test
    void rollingRetentionKeepsOnlyFiveRecentSnapshotsPerBackpack() {
        long now = 2_000_000_000L;

        assertTrue(BackpackRecoveryJournal.shouldRetainSnapshot(0, 0, now, now));
        assertTrue(BackpackRecoveryJournal.shouldRetainSnapshot(4, 99, now, now));
        assertFalse(BackpackRecoveryJournal.shouldRetainSnapshot(5, 0, now, now));
        assertFalse(BackpackRecoveryJournal.shouldRetainSnapshot(0, 100, now, now));
    }

    @Test
    void rollingRetentionExpiresSnapshotsAfterFourteenDays() {
        long now = 2_000_000_000L;
        long exactlyFourteenDays = now - Duration.ofDays(14).toMillis();

        assertTrue(BackpackRecoveryJournal.shouldRetainSnapshot(0, 0, exactlyFourteenDays, now));
        assertFalse(BackpackRecoveryJournal.shouldRetainSnapshot(0, 0, exactlyFourteenDays - 1L, now));
    }

    @Test
    void snapshotSelectorsAllowSafeUnambiguousPrefixes() {
        assertTrue(BackpackRecoveryJournal.selectorMatches("mabc1234-deadbeef", "mabc1234"));
        assertTrue(BackpackRecoveryJournal.selectorMatches("mabc1234-deadbeef", "MABC"));
        assertFalse(BackpackRecoveryJournal.selectorMatches("mabc1234-deadbeef", "beef"));
        assertFalse(BackpackRecoveryJournal.selectorMatches(null, "mabc"));
    }
}
