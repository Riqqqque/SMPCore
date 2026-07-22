package me.rique.smpcore.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XpLecternListenerTest {

    @Test
    void bottlingAlwaysConsumesTenXpPerBottle() {
        assertEquals(10L, XpLecternListener.bottlingXpCost(1));
        assertEquals(80L, XpLecternListener.bottlingXpCost(8));
    }

    @Test
    void menuOnlyAcceptsItsPublishedBatchSizes() {
        assertEquals(1, XpLecternListener.normalizedBottleCount(1));
        assertEquals(8, XpLecternListener.normalizedBottleCount(8));
        assertEquals(0, XpLecternListener.normalizedBottleCount(0));
        assertEquals(0, XpLecternListener.normalizedBottleCount(64));
    }

    @Test
    void invalidBottleCountsCannotConsumeXp() {
        assertEquals(0L, XpLecternListener.bottlingXpCost(0));
        assertEquals(0L, XpLecternListener.bottlingXpCost(-1));
    }
}
