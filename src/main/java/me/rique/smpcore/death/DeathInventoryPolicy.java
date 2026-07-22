package me.rique.smpcore.death;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

final class DeathInventoryPolicy {

    static final String KIND_DEATH = "DEATH";
    static final String KIND_PRE_RESTORE_BACKUP = "PRE_RESTORE_BACKUP";
    static final String STATE_AVAILABLE = "AVAILABLE";
    static final String STATE_RESTORING = "RESTORING";
    static final String STATE_RESTORED = "RESTORED";
    static final String STATE_REVIEW_REQUIRED = "REVIEW_REQUIRED";
    static final String STATE_ARCHIVED = "ARCHIVED";

    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter
        .ofPattern("yyyyMMdd-HHmmss.SSS'Z'", Locale.ROOT)
        .withZone(ZoneOffset.UTC);

    private DeathInventoryPolicy() {
    }

    static RestoreEligibility restoreEligibility(String kind, String state, boolean keepInventory, int itemsToKeep) {
        if (!KIND_DEATH.equalsIgnoreCase(kind)) {
            return new RestoreEligibility(false, "Only death snapshots can be restored.");
        }
        if (keepInventory) {
            return new RestoreEligibility(false, "This death kept the inventory, so restoring it would duplicate items.");
        }
        if (itemsToKeep > 0) {
            return new RestoreEligibility(false, "This death retained individual items, so a full restore would duplicate them.");
        }
        if (STATE_RESTORED.equalsIgnoreCase(state)) {
            return new RestoreEligibility(false, "This snapshot has already been restored.");
        }
        if (STATE_RESTORING.equalsIgnoreCase(state)) {
            return new RestoreEligibility(false, "This snapshot has an interrupted restore pending reconciliation.");
        }
        if (STATE_REVIEW_REQUIRED.equalsIgnoreCase(state)) {
            return new RestoreEligibility(false, "This snapshot needs manual review before any further restore.");
        }
        if (!STATE_AVAILABLE.equalsIgnoreCase(state)) {
            return new RestoreEligibility(false, "This snapshot is not available for restoration.");
        }
        return new RestoreEligibility(true, "");
    }

    static String snapshotFileName(long createdAtEpochMillis, UUID snapshotId) {
        return FILE_TIMESTAMP.format(Instant.ofEpochMilli(createdAtEpochMillis)) + "-" + snapshotId + ".yml";
    }

    static String shortId(String snapshotId) {
        if (snapshotId == null) {
            return "unknown";
        }
        String compact = snapshotId.replace("-", "");
        return compact.substring(0, Math.min(12, compact.length()));
    }

    static boolean selectorMatches(String snapshotId, String selector) {
        if (snapshotId == null || selector == null || selector.isBlank()) {
            return false;
        }
        String normalizedId = snapshotId.replace("-", "").toLowerCase(Locale.ROOT);
        String normalizedSelector = selector.replace("-", "").toLowerCase(Locale.ROOT);
        return normalizedId.equals(normalizedSelector) || normalizedId.startsWith(normalizedSelector);
    }

    static boolean compatibleSlotCounts(
        int savedStorage,
        int savedArmor,
        int savedExtra,
        int savedCrafting,
        int liveStorage,
        int liveArmor,
        int liveExtra,
        int liveCrafting
    ) {
        return savedStorage == liveStorage
            && savedArmor == liveArmor
            && savedExtra == liveExtra
            && savedCrafting == liveCrafting;
    }

    record RestoreEligibility(boolean allowed, String reason) {
    }
}
