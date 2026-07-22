package me.rique.smpcore.duel;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DuelRulesTest {

    @Test
    void parimutuelPayoutConservesTheEntirePool() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID winnerA = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID winnerB = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID loser = UUID.fromString("00000000-0000-0000-0000-000000000012");
        Map<UUID, DuelRules.Wager> wagers = new LinkedHashMap<>();
        wagers.put(winnerA, new DuelRules.Wager(first, 10L));
        wagers.put(winnerB, new DuelRules.Wager(first, 5L));
        wagers.put(loser, new DuelRules.Wager(second, 16L));

        Map<UUID, Long> payouts = DuelRules.settleParimutuel(wagers, first);

        assertEquals(31L, payouts.values().stream().mapToLong(Long::longValue).sum());
        assertEquals(2, payouts.size());
        assertEquals(21L, payouts.get(winnerA));
        assertEquals(10L, payouts.get(winnerB));
    }

    @Test
    void oneSidedPoolRefundsInsteadOfCreatingProfit() {
        UUID side = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000010");
        Map<UUID, DuelRules.Wager> wagers = Map.of(player, new DuelRules.Wager(side, 64L));

        assertEquals(Map.of(player, 64L), DuelRules.settleParimutuel(wagers, side));
    }

    @Test
    void arbitraryLargeStakesStillConserveThePool() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID winner = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID loser = UUID.fromString("00000000-0000-0000-0000-000000000011");
        Map<UUID, DuelRules.Wager> wagers = Map.of(
            winner, new DuelRules.Wager(first, 4_500_000_123L),
            loser, new DuelRules.Wager(second, 5_499_999_876L)
        );

        Map<UUID, Long> payouts = DuelRules.settleParimutuel(wagers, first);

        assertEquals(9_999_999_999L, payouts.get(winner));
    }

    @Test
    void timeoutUsesDamageBeforeHealth() {
        assertEquals(DuelRules.TimeoutResult.FIRST, DuelRules.timeoutWinner(50.0D, 40.0D, 0.1D, 1.0D));
        assertEquals(DuelRules.TimeoutResult.SECOND, DuelRules.timeoutWinner(40.0D, 40.0D, 0.4D, 0.8D));
        assertEquals(DuelRules.TimeoutResult.DRAW, DuelRules.timeoutWinner(40.0D, 40.0D, 0.8D, 0.8D));
    }

    @Test
    void onlyOneThroughThreeRoundWinsAreAccepted() {
        assertEquals(-1, DuelRules.normalizeRoundsToWin(0));
        assertEquals(1, DuelRules.normalizeRoundsToWin(1));
        assertEquals(2, DuelRules.normalizeRoundsToWin(2));
        assertEquals(3, DuelRules.normalizeRoundsToWin(3));
        assertEquals(-1, DuelRules.normalizeRoundsToWin(4));
    }

    @Test
    void onlySoloDuoAndTrioTeamsAreAccepted() {
        assertEquals(-1, DuelRules.normalizeTeamSize(0));
        assertEquals(1, DuelRules.normalizeTeamSize(1));
        assertEquals(2, DuelRules.normalizeTeamSize(2));
        assertEquals(3, DuelRules.normalizeTeamSize(3));
        assertEquals(-1, DuelRules.normalizeTeamSize(4));
    }

    @Test
    void duelHealthModifierNormalizesEveryFighterToTwentyHealth() {
        assertEquals(0.0D, DuelRules.healthNormalizationModifier(20.0D), 0.000001D);
        assertEquals(-0.5D, DuelRules.healthNormalizationModifier(40.0D), 0.000001D);
        assertEquals(1.0D, DuelRules.healthNormalizationModifier(10.0D), 0.000001D);
        assertEquals(20.0D, 40.0D * (1.0D + DuelRules.healthNormalizationModifier(40.0D)), 0.000001D);
        assertEquals(20.0D, 10.0D * (1.0D + DuelRules.healthNormalizationModifier(10.0D)), 0.000001D);
    }

    @Test
    void everyRoundStartsFromTheVanillaHungerBaseline() {
        assertEquals(20, DuelRules.ROUND_START_FOOD_LEVEL);
        assertEquals(5.0F, DuelRules.ROUND_START_SATURATION, 0.0001F);
        assertEquals(0.0F, DuelRules.ROUND_START_EXHAUSTION, 0.0001F);
    }
}
