package me.rique.smpcore.recovery;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskyInventoryRecoveryRepositoryTest {

    @Test
    void retentionIsBoundedByCountAgeAndTotalBytes() {
        long now = 4_000_000_000L;
        long recent = now - Duration.ofDays(1).toMillis();

        assertTrue(RiskyInventoryRecoveryRepository.shouldRetain(49, recent, now, 0L, 1L));
        assertFalse(RiskyInventoryRecoveryRepository.shouldRetain(50, recent, now, 0L, 1L));
        assertFalse(RiskyInventoryRecoveryRepository.shouldRetain(0,
            now - Duration.ofDays(14).toMillis() - 1L, now, 0L, 1L));
        assertFalse(RiskyInventoryRecoveryRepository.shouldRetain(0, recent, now,
            128L * 1024L * 1024L, 1L));
    }

    @Test
    void selectorsOnlyMatchSafePrefixes() {
        assertTrue(RiskyInventoryRecoveryRepository.selectorMatches("abc123def", "ABC123"));
        assertFalse(RiskyInventoryRecoveryRepository.selectorMatches("abc123def", "123"));
        assertFalse(RiskyInventoryRecoveryRepository.selectorMatches(null, "abc"));
    }

    @Test
    void adjacentCopiesShareOnlyTheFiveMinuteTransactionWindow() {
        long now = 10_000_000L;
        assertTrue(RiskyInventoryRecoveryRepository.sameTransactionWindow(now, now + Duration.ofMinutes(5).toMillis()));
        assertFalse(RiskyInventoryRecoveryRepository.sameTransactionWindow(now,
            now + Duration.ofMinutes(5).toMillis() + 1L));
        assertFalse(RiskyInventoryRecoveryRepository.sameTransactionWindow(Long.MIN_VALUE, Long.MAX_VALUE));
        assertFalse(RiskyInventoryRecoveryRepository.sameTransactionWindow(Long.MIN_VALUE, 0L));
    }

    @Test
    void restoreMarkersRoundTripAndRejectMalformedInput() {
        UUID snapshot = UUID.randomUUID();
        RiskyInventoryRecoveryRepository.MarkerParts parsed =
            RiskyInventoryRecoveryRepository.parseMarker(snapshot + ":7");

        assertEquals(snapshot, parsed.snapshotId());
        assertEquals(7, parsed.itemIndex());
        assertNull(RiskyInventoryRecoveryRepository.parseMarker("bad"));
        assertNull(RiskyInventoryRecoveryRepository.parseMarker(snapshot + ":-1"));
    }

    @Test
    void snapshotDigestIncludesSourceSlotAndExactPayload() {
        var first = new RiskyInventoryRecoveryRepository.SerializedItem("menu", 20, (byte) 0, new byte[] {1, 2, 3});
        var same = new RiskyInventoryRecoveryRepository.SerializedItem("menu", 20, (byte) 0, new byte[] {1, 2, 3});
        var moved = new RiskyInventoryRecoveryRepository.SerializedItem("menu", 24, (byte) 0, new byte[] {1, 2, 3});
        var changed = new RiskyInventoryRecoveryRepository.SerializedItem("menu", 20, (byte) 0, new byte[] {1, 2, 4});

        assertEquals(RiskyInventoryRecoveryManager.snapshotDigest(List.of(first)),
            RiskyInventoryRecoveryManager.snapshotDigest(List.of(same)));
        assertNotEquals(RiskyInventoryRecoveryManager.snapshotDigest(List.of(first)),
            RiskyInventoryRecoveryManager.snapshotDigest(List.of(moved)));
        assertNotEquals(RiskyInventoryRecoveryManager.snapshotDigest(List.of(first)),
            RiskyInventoryRecoveryManager.snapshotDigest(List.of(changed)));
    }
}
