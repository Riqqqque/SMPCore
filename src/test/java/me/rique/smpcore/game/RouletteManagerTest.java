package me.rique.smpcore.game;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouletteManagerTest {

    @Test
    void europeanWheelContainsEveryPocketExactlyOnce() {
        List<Integer> wheel = RouletteManager.wheelOrder();

        assertEquals(37, wheel.size());
        assertEquals(37, new HashSet<>(wheel).size());
        assertEquals(Set.copyOf(range(0, 36)), Set.copyOf(wheel));
    }

    @Test
    void tableHasEighteenRedAndEighteenBlackNumbers() {
        long red = range(0, 36).stream().filter(RouletteManager::isRed).count();
        long black = range(0, 36).stream().filter(RouletteManager::isBlack).count();

        assertEquals(18, red);
        assertEquals(18, black);
        assertFalse(RouletteManager.isRed(0));
        assertFalse(RouletteManager.isBlack(0));
    }

    @Test
    void everyOutsideBetHasItsAdvertisedWinnerCountAndZeroLoses() {
        for (RouletteManager.BetKind kind : RouletteManager.BetKind.values()) {
            if (kind == RouletteManager.BetKind.STRAIGHT) continue;
            RouletteManager.RouletteBet bet = RouletteManager.RouletteBet.outside(kind);
            long winners = range(0, 36).stream().filter(bet::wins).count();

            assertEquals(bet.winningPockets(), winners, kind.name());
            assertFalse(bet.wins(0), kind.name());
        }
    }

    @Test
    void everyOfferedBetHasTheEuropeanHouseEdge() {
        for (RouletteManager.BetKind kind : RouletteManager.BetKind.values()) {
            RouletteManager.RouletteBet bet = kind == RouletteManager.BetKind.STRAIGHT
                ? RouletteManager.RouletteBet.straight(17)
                : RouletteManager.RouletteBet.outside(kind);
            double expectedReturn = (double) bet.winningPockets() * bet.payoutMultiplier() / 37.0D;

            assertEquals(36.0D / 37.0D, expectedReturn, 0.0000001D, kind.name());
        }
    }

    @Test
    void straightBetOnlyWinsOnItsExactNumber() {
        RouletteManager.RouletteBet bet = RouletteManager.RouletteBet.straight(23);

        assertTrue(bet.wins(23));
        assertFalse(bet.wins(22));
        assertFalse(bet.wins(0));
        assertEquals(36, bet.payoutMultiplier());
        assertEquals(1, bet.winningPockets());
    }

    @Test
    void invalidStraightNumbersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> RouletteManager.RouletteBet.straight(-1));
        assertThrows(IllegalArgumentException.class, () -> RouletteManager.RouletteBet.straight(37));
        assertThrows(IllegalArgumentException.class, () -> RouletteManager.drawResult(-1));
        assertThrows(IllegalArgumentException.class, () -> RouletteManager.drawResult(37));
    }

    @Test
    void largePayoutsSplitWithoutExceedingStackSize() {
        List<Integer> stacks = RouletteManager.splitPayoutStackAmounts(2304, 64);

        assertEquals(36, stacks.size());
        assertEquals(2304, stacks.stream().mapToInt(Integer::intValue).sum());
        assertTrue(stacks.stream().allMatch(amount -> amount >= 1 && amount <= 64));
        assertTrue(RouletteManager.splitPayoutStackAmounts(0, 64).isEmpty());
    }

    @Test
    void animationOnlySlowsNearTheWinningPocket() {
        assertEquals(1L, RouletteManager.animationDelay(10, 100));
        assertEquals(2L, RouletteManager.animationDelay(60, 100));
        assertEquals(3L, RouletteManager.animationDelay(85, 100));
        assertEquals(5L, RouletteManager.animationDelay(95, 100));
    }

    private static List<Integer> range(int start, int end) {
        return java.util.stream.IntStream.rangeClosed(start, end).boxed().toList();
    }
}
