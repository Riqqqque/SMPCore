package me.rique.smpcore.waystone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WaystoneManagerTest {

    @Test
    void paginatesEveryKnownWaystone() {
        assertEquals(1, WaystoneManager.pageCount(0));
        assertEquals(1, WaystoneManager.pageCount(45));
        assertEquals(2, WaystoneManager.pageCount(46));
        assertEquals(2, WaystoneManager.pageCount(90));
        assertEquals(3, WaystoneManager.pageCount(91));
    }

    @Test
    void clampsRequestedPagesToAvailableRange() {
        assertEquals(0, WaystoneManager.clampPage(-1, 3));
        assertEquals(1, WaystoneManager.clampPage(1, 3));
        assertEquals(2, WaystoneManager.clampPage(8, 3));
        assertEquals(0, WaystoneManager.clampPage(4, 0));
    }

    @Test
    void retriesOnlyOneSafeCrossWorldRejection() {
        assertTrue(WaystoneManager.shouldRetryCrossWorldTeleport(true, true, false, false, true));
        assertFalse(WaystoneManager.shouldRetryCrossWorldTeleport(false, true, false, false, true));
        assertFalse(WaystoneManager.shouldRetryCrossWorldTeleport(true, false, false, false, true));
        assertFalse(WaystoneManager.shouldRetryCrossWorldTeleport(true, true, true, false, true));
        assertFalse(WaystoneManager.shouldRetryCrossWorldTeleport(true, true, false, true, true));
        assertFalse(WaystoneManager.shouldRetryCrossWorldTeleport(true, true, false, false, false));
    }
}
