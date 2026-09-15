package me.rique.smpcore.event;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimedLocatorBarEventTest {

    @Test
    void eventUsesInclusiveStartAndExclusiveEnd() {
        assertFalse(TimedLocatorBarEvent.isActive(TimedLocatorBarEvent.START.minusMillis(1)));
        assertTrue(TimedLocatorBarEvent.isActive(TimedLocatorBarEvent.START));
        assertTrue(TimedLocatorBarEvent.isActive(TimedLocatorBarEvent.END.minusMillis(1)));
        assertFalse(TimedLocatorBarEvent.isActive(TimedLocatorBarEvent.END));
    }

    @Test
    void eventLastsSixHours() {
        assertTrue(Duration.between(TimedLocatorBarEvent.START, TimedLocatorBarEvent.END)
            .equals(Duration.ofHours(6)));
    }
}
