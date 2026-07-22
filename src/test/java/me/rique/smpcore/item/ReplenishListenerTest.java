package me.rique.smpcore.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplenishListenerTest {

    @Test
    void replenishBooksRequireOneCleanBookAndTheExactMarker() {
        assertTrue(ReplenishListener.isValidReplenishBookPayload(1, (byte) 1, false, false, false));

        assertFalse(ReplenishListener.isValidReplenishBookPayload(0, (byte) 1, false, false, false));
        assertFalse(ReplenishListener.isValidReplenishBookPayload(2, (byte) 1, false, false, false));
        assertFalse(ReplenishListener.isValidReplenishBookPayload(1, null, false, false, false));
        assertFalse(ReplenishListener.isValidReplenishBookPayload(1, (byte) 0, false, false, false));
        assertFalse(ReplenishListener.isValidReplenishBookPayload(1, (byte) 1, true, false, false));
        assertFalse(ReplenishListener.isValidReplenishBookPayload(1, (byte) 1, false, true, false));
        assertFalse(ReplenishListener.isValidReplenishBookPayload(1, (byte) 1, false, false, true));
    }
}
