package me.rique.smpcore.quest;

import org.bukkit.Material;

import java.util.List;

final class FamiliarSkillTree {

    static final int BRANCH_COUNT = 5;
    static final int RANKS_PER_BRANCH = 10;
    static final int NODE_COUNT = BRANCH_COUNT * RANKS_PER_BRANCH;

    private static final List<Branch> BRANCHES = List.of(
        new Branch(
            "Instinct",
            Material.IRON_SWORD,
            "Damage dealt to ordinary mobs",
            List.of(
                "Keen Senses", "Hunter's Focus", "Predatory Rhythm", "Sharp Fang", "Pack Tactics",
                "Relentless Pursuit", "Alpha's Call", "Blood Trail", "Apex Instinct", "The Wild Hunt"
            )
        ),
        new Branch(
            "Endurance",
            Material.TURTLE_HELMET,
            "Damage taken from ordinary mobs",
            List.of(
                "Thick Hide", "Sure Footing", "Steady Heart", "Weathered", "Guarded Flank",
                "Iron Nerve", "Shared Burden", "Lasting Bond", "Unbroken", "Second Wind"
            )
        ),
        new Branch(
            "Pace",
            Material.RABBIT_FOOT,
            "Movement speed while this familiar is active",
            List.of(
                "Trail Step", "Light Tread", "Open Ground", "Quick Turn", "Long Stride",
                "Wind Reader", "Pathfinder", "Untiring", "Fleet Bond", "Packstride"
            )
        ),
        new Branch(
            "Bond",
            Material.AMETHYST_SHARD,
            "This familiar's original perk",
            List.of(
                "First Trust", "Quiet Signal", "Shared Instinct", "Steady Presence", "Kindred Spirit",
                "Wordless Call", "True Companion", "Soul Thread", "Perfect Accord", "Deep Bond"
            )
        ),
        new Branch(
            "Fortune",
            Material.EXPERIENCE_BOTTLE,
            "Experience earned while this familiar is active",
            List.of(
                "Curious Eye", "Bright Find", "Lucky Trail", "Good Omen", "Hidden Lesson",
                "Fortunate Step", "Rare Insight", "Guiding Star", "Golden Memory", "Fated Journey"
            )
        )
    );

    private FamiliarSkillTree() {
    }

    static Branch branch(int index) {
        if (index < 0 || index >= BRANCHES.size()) {
            throw new IllegalArgumentException("Unknown familiar skill branch: " + index);
        }
        return BRANCHES.get(index);
    }

    static int nodeIndex(int branch, int rank) {
        if (branch < 0 || branch >= BRANCH_COUNT || rank < 0 || rank >= RANKS_PER_BRANCH) {
            return -1;
        }
        return branch * RANKS_PER_BRANCH + rank;
    }

    static int branchOf(int node) {
        return validNode(node) ? node / RANKS_PER_BRANCH : -1;
    }

    static int rankOf(int node) {
        return validNode(node) ? node % RANKS_PER_BRANCH : -1;
    }

    static boolean validNode(int node) {
        return node >= 0 && node < NODE_COUNT;
    }

    static boolean unlocked(long mask, int node) {
        return validNode(node) && (mask & (1L << node)) != 0L;
    }

    static boolean canUnlock(long mask, int node) {
        if (!validNode(node) || unlocked(mask, node)) {
            return false;
        }
        int rank = rankOf(node);
        return rank == 0 || unlocked(mask, node - 1);
    }

    static long unlock(long mask, int node) {
        return canUnlock(mask, node) ? mask | (1L << node) : mask;
    }

    static int unlockedCount(long mask) {
        long validBits = mask & ((1L << NODE_COUNT) - 1L);
        return Long.bitCount(validBits);
    }

    static int branchRanks(long mask, int branch) {
        if (branch < 0 || branch >= BRANCH_COUNT) {
            return 0;
        }
        int count = 0;
        for (int rank = 0; rank < RANKS_PER_BRANCH - 1; rank++) {
            if (unlocked(mask, nodeIndex(branch, rank))) {
                count++;
            }
        }
        return count;
    }

    static boolean hasKeystone(long mask, int branch) {
        return unlocked(mask, nodeIndex(branch, RANKS_PER_BRANCH - 1));
    }

    static long cost(int node) {
        int rank = rankOf(node);
        if (rank < 0) {
            return Long.MAX_VALUE;
        }
        return rank == RANKS_PER_BRANCH - 1 ? 1_500L : 100L + (rank + 1L) * 75L;
    }

    static double mobDamageMultiplier(long mask) {
        return 1.0D + branchRanks(mask, 0) * 0.01D + (hasKeystone(mask, 0) ? 0.05D : 0.0D);
    }

    static double mobDamageTakenMultiplier(long mask) {
        return Math.max(0.75D, 1.0D - branchRanks(mask, 1) * 0.0075D - (hasKeystone(mask, 1) ? 0.05D : 0.0D));
    }

    static double movementSpeedBonus(long mask) {
        return branchRanks(mask, 2) * 0.005D + (hasKeystone(mask, 2) ? 0.05D : 0.0D);
    }

    static double coreEffectMultiplier(long mask, boolean evolved) {
        return 1.0D
            + branchRanks(mask, 3) * 0.01D
            + (hasKeystone(mask, 3) ? 0.03D : 0.0D)
            + (evolved ? 0.05D : 0.0D);
    }

    static double experienceMultiplier(long mask) {
        return 1.0D + branchRanks(mask, 4) * 0.02D + (hasKeystone(mask, 4) ? 0.07D : 0.0D);
    }

    static String effectLine(int branch, int rank) {
        boolean keystone = rank == RANKS_PER_BRANCH - 1;
        return switch (branch) {
            case 0 -> keystone ? "+5% damage to ordinary mobs." : "+1% damage to ordinary mobs.";
            case 1 -> keystone ? "Second Wind saves you at low health every 90s." : "-0.75% damage from ordinary mobs.";
            case 2 -> keystone ? "+5% movement speed." : "+0.5% movement speed.";
            case 3 -> keystone ? "+3% to this familiar's original perk." : "+1% to this familiar's original perk.";
            case 4 -> keystone ? "+7% experience earned." : "+2% experience earned.";
            default -> "Unknown familiar skill.";
        };
    }

    record Branch(String name, Material icon, String summary, List<String> nodeNames) {
        String nodeName(int rank) {
            return nodeNames.get(rank);
        }
    }
}
