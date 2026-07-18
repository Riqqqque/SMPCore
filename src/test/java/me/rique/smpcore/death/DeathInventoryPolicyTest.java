package me.rique.smpcore.death;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathInventoryPolicyTest {

    @Test
    void restoreIsOneTimeAndRejectsPartialKeepDeaths() {
        assertTrue(DeathInventoryPolicy.restoreEligibility("DEATH", "AVAILABLE", false, 0).allowed());
        assertFalse(DeathInventoryPolicy.restoreEligibility("DEATH", "RESTORED", false, 0).allowed());
        assertFalse(DeathInventoryPolicy.restoreEligibility("DEATH", "RESTORING", false, 0).allowed());
        assertFalse(DeathInventoryPolicy.restoreEligibility("DEATH", "REVIEW_REQUIRED", false, 0).allowed());
        assertFalse(DeathInventoryPolicy.restoreEligibility("DEATH", "AVAILABLE", true, 0).allowed());
        assertFalse(DeathInventoryPolicy.restoreEligibility("DEATH", "AVAILABLE", false, 1).allowed());
        assertFalse(DeathInventoryPolicy.restoreEligibility("PRE_RESTORE_BACKUP", "ARCHIVED", false, 0).allowed());
    }

    @Test
    void snapshotNamesAreUtcSafeAndContainTheFullId() {
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
        assertEquals(
            "20260715-123456.789Z-11111111-2222-3333-4444-555555555555.yml",
            DeathInventoryPolicy.snapshotFileName(Instant.parse("2026-07-15T12:34:56.789Z").toEpochMilli(), id)
        );
    }

    @Test
    void selectorsAllowUniqueShortIdsWithoutIgnoringHyphens() {
        String id = "11111111-2222-3333-4444-555555555555";
        assertEquals("111111112222", DeathInventoryPolicy.shortId(id));
        assertTrue(DeathInventoryPolicy.selectorMatches(id, "11111111-2222"));
        assertTrue(DeathInventoryPolicy.selectorMatches(id, id));
        assertFalse(DeathInventoryPolicy.selectorMatches(id, "22222222"));
    }

    @Test
    void slotLayoutsMustMatchExactlyBeforeRestore() {
        assertTrue(DeathInventoryPolicy.compatibleSlotCounts(36, 4, 3, 4, 36, 4, 3, 4));
        assertFalse(DeathInventoryPolicy.compatibleSlotCounts(36, 4, 1, 4, 36, 4, 3, 4));
        assertFalse(DeathInventoryPolicy.compatibleSlotCounts(36, 4, 3, 4, 41, 4, 3, 4));
        assertFalse(DeathInventoryPolicy.compatibleSlotCounts(36, 4, 3, 4, 36, 4, 3, 0));
    }
}
