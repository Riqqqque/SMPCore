package me.rique.smpcore.duel;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DuelRules {

    private DuelRules() {
    }

    public static int normalizeRoundsToWin(int requested) {
        return requested >= 1 && requested <= 3 ? requested : -1;
    }

    public static int normalizeTeamSize(int requested) {
        return requested >= 1 && requested <= 3 ? requested : -1;
    }

    public static TimeoutResult timeoutWinner(double firstDamage, double secondDamage, double firstHealthRatio, double secondHealthRatio) {
        int damage = Double.compare(safe(firstDamage), safe(secondDamage));
        if (damage != 0) {
            return damage > 0 ? TimeoutResult.FIRST : TimeoutResult.SECOND;
        }
        int health = Double.compare(clampRatio(firstHealthRatio), clampRatio(secondHealthRatio));
        if (health != 0) {
            return health > 0 ? TimeoutResult.FIRST : TimeoutResult.SECOND;
        }
        return TimeoutResult.DRAW;
    }

    public static Map<UUID, Long> settleParimutuel(Map<UUID, Wager> wagers, UUID winningSide) {
        if (wagers == null || wagers.isEmpty() || winningSide == null) {
            return Map.of();
        }

        long winningPool = poolFor(wagers, winningSide);
        long totalPool = wagers.values().stream().mapToLong(wager -> Math.max(0L, wager.amount())).sum();
        if (winningPool <= 0L || totalPool <= winningPool) {
            return refunds(wagers);
        }

        List<Map.Entry<UUID, Wager>> winners = wagers.entrySet().stream()
            .filter(entry -> winningSide.equals(entry.getValue().side()))
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
            .toList();
        Map<UUID, Long> payouts = new LinkedHashMap<>();
        long distributed = 0L;
        for (Map.Entry<UUID, Wager> winner : winners) {
            long payout = multiplyDivideFloor(totalPool, Math.max(0L, winner.getValue().amount()), winningPool);
            payouts.put(winner.getKey(), payout);
            distributed += payout;
        }

        long remainder = totalPool - distributed;
        for (int index = 0; remainder > 0L && !winners.isEmpty(); index = (index + 1) % winners.size()) {
            UUID playerId = winners.get(index).getKey();
            payouts.merge(playerId, 1L, Long::sum);
            remainder--;
        }
        return Map.copyOf(payouts);
    }

    public static Map<UUID, Long> refunds(Map<UUID, Wager> wagers) {
        if (wagers == null || wagers.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> refunds = new LinkedHashMap<>();
        wagers.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
            .forEach(entry -> {
                long amount = Math.max(0L, entry.getValue().amount());
                if (amount > 0L) {
                    refunds.put(entry.getKey(), amount);
                }
            });
        return Map.copyOf(refunds);
    }

    public static long poolFor(Map<UUID, Wager> wagers, UUID side) {
        if (wagers == null || side == null) {
            return 0L;
        }
        return wagers.values().stream()
            .filter(wager -> side.equals(wager.side()))
            .mapToLong(wager -> Math.max(0L, wager.amount()))
            .sum();
    }

    private static long multiplyDivideFloor(long left, long right, long divisor) {
        if (left <= 0L || right <= 0L || divisor <= 0L) {
            return 0L;
        }
        return java.math.BigInteger.valueOf(left)
            .multiply(java.math.BigInteger.valueOf(right))
            .divide(java.math.BigInteger.valueOf(divisor))
            .longValueExact();
    }

    private static double safe(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, value) : 0.0D;
    }

    private static double clampRatio(double value) {
        return Math.max(0.0D, Math.min(1.0D, safe(value)));
    }

    public record Wager(UUID side, long amount) {
    }

    public enum TimeoutResult {
        FIRST,
        SECOND,
        DRAW
    }
}
