package me.rique.smpcore.tavern;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TavernManagerTest {

    @Test
    void slotBoundariesMatchPublishedPayouts() {
        assertEquals(25, TavernManager.slotMultiplier(0));
        assertEquals(25, TavernManager.slotMultiplier(9));
        assertEquals(15, TavernManager.slotMultiplier(10));
        assertEquals(15, TavernManager.slotMultiplier(49));
        assertEquals(8, TavernManager.slotMultiplier(50));
        assertEquals(8, TavernManager.slotMultiplier(199));
        assertEquals(4, TavernManager.slotMultiplier(200));
        assertEquals(4, TavernManager.slotMultiplier(499));
        assertEquals(3, TavernManager.slotMultiplier(500));
        assertEquals(3, TavernManager.slotMultiplier(1199));
        assertEquals(0, TavernManager.slotMultiplier(1200));
        assertEquals(0, TavernManager.slotMultiplier(3449));
        assertEquals(0, TavernManager.slotMultiplier(3450));
        assertEquals(0, TavernManager.slotMultiplier(9999));
    }

    @Test
    void slotReturnMatchesPublishedOdds() {
        long returned = 0;
        for (int roll = 0; roll < 10_000; roll++) returned += TavernManager.slotMultiplier(roll);
        assertEquals(5_350L, returned);
    }

    @Test
    void slotHitRateMatchesThePublishedTwelvePercent() {
        int wins = 0;
        for (int roll = 0; roll < 10_000; roll++) if (TavernManager.slotMultiplier(roll) > 1) wins++;
        assertEquals(1_200, wins);
        assertEquals(0.12D, TavernManager.slotHitRate(0.0D), 0.000001D);
        assertEquals(0.13056D, TavernManager.slotHitRate(0.10D), 0.000001D);
        assertThrows(IllegalArgumentException.class, () -> TavernManager.slotHitRate(0.11D));
    }

    @Test
    void slotRejectsRollsOutsideItsRandomRange() {
        assertThrows(IllegalArgumentException.class, () -> TavernManager.slotMultiplier(-1));
        assertThrows(IllegalArgumentException.class, () -> TavernManager.slotMultiplier(10_000));
    }

    @Test
    void tavernLuckOnlyKeepsTheSaferBonusRollWhenTriggered() {
        assertEquals(100, TavernManager.luckySlotRoll(5000, 100, 0.05, 0.10));
        assertEquals(5000, TavernManager.luckySlotRoll(5000, 9000, 0.05, 0.10));
        assertEquals(5000, TavernManager.luckySlotRoll(5000, 100, 0.10, 0.10));
        assertThrows(IllegalArgumentException.class, () -> TavernManager.luckySlotRoll(0, 0, 0.0, 0.11));
    }

    @Test
    void slotReelsStopInOrder() {
        assertEquals(false, TavernManager.slotReelStopped(17, 0));
        assertEquals(true, TavernManager.slotReelStopped(18, 0));
        assertEquals(false, TavernManager.slotReelStopped(23, 1));
        assertEquals(true, TavernManager.slotReelStopped(24, 1));
        assertEquals(false, TavernManager.slotReelStopped(29, 2));
        assertEquals(true, TavernManager.slotReelStopped(30, 2));
        assertThrows(IllegalArgumentException.class, () -> TavernManager.slotReelStopped(30, 3));
    }

    @Test
    void dartsRewardTimingAtTheCenter() {
        assertEquals(50, TavernManager.dartScoreForCursor(4));
        assertEquals(25, TavernManager.dartScoreForCursor(3));
        assertEquals(25, TavernManager.dartScoreForCursor(5));
        assertEquals(2, TavernManager.dartScoreForCursor(0));
        assertThrows(IllegalArgumentException.class, () -> TavernManager.dartScoreForCursor(9));
    }

    @Test
    void dartCursorReflectsInwardWhenAThrowResetsAtAnEdge() {
        assertEquals(new TavernManager.DartStep(1, 1), TavernManager.advanceDartCursor(0, -1));
        assertEquals(new TavernManager.DartStep(7, -1), TavernManager.advanceDartCursor(8, 1));
        assertEquals(new TavernManager.DartStep(0, 1), TavernManager.advanceDartCursor(1, -1));
        assertEquals(new TavernManager.DartStep(8, -1), TavernManager.advanceDartCursor(7, 1));
        assertThrows(IllegalArgumentException.class, () -> TavernManager.advanceDartCursor(0, 0));
    }

    @Test
    void dartBullseyeAlwaysRemainsVisibleForAtLeastTwoTicks() {
        assertEquals(2, TavernManager.dartDelayForCursor(4, 1));
        assertEquals(2, TavernManager.dartDelayForCursor(4, 2));
        assertEquals(1, TavernManager.dartDelayForCursor(3, 1));
        assertEquals(4, TavernManager.dartDelayForCursor(5, 4));
        assertThrows(IllegalArgumentException.class, () -> TavernManager.dartDelayForCursor(4, 0));
    }

    @Test
    void oversizedMaterialPayoutsSplitWithoutLoss() {
        assertEquals(List.of(64, 64, 2), TavernManager.splitPlainPayoutAmounts(130, 64));
        assertEquals(List.of(16, 16, 1), TavernManager.splitPlainPayoutAmounts(33, 16));
        assertEquals(List.of(), TavernManager.splitPlainPayoutAmounts(0, 64));
    }
}
