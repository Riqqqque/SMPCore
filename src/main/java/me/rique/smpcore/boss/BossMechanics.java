package me.rique.smpcore.boss;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BossMechanics {
    private static final List<Signature> SIGNATURES = List.of(
        new Signature("yule_the_minion", "marshal_stack", "Hold the Line", "Stack on the blue-marked player.", List.of("Hold the Line", "Veilbound Muster")),
        new Signature("kael_the_ashen", "ashen_crossfire", "Ashen Crossfire", "Spread marked shots before they land.", List.of("Ashen Crossfire", "Deadeye")),
        new Signature("vesper_the_widow_queen", "widows_trail", "Widow's Claim", "Keep moving and leave the venom trail behind.", List.of("Widow's Claim", "Webbreak")),
        new Signature("mirewood_the_root_tyrant", "root_wards", "Root Wards", "Stand inside every green ward before it closes.", List.of("Root Wards", "Briar Lattice")),
        new Signature("nereida_the_abyss_mother", "undertow", "Undertow", "Recover from the pull and escape the tide ring.", List.of("Undertow", "Tidal Divide")),
        new Signature("iron_saint", "saints_stagger", "Saint's Stagger", "Break the guard before the counter-slam.", List.of("Saint's Stagger", "Counterstance")),
        new Signature("aurelion_the_rift_seraph", "rift_sectors", "Rift Sectors", "Move into the green wedge before reality folds.", List.of("Rift Step", "Rift Sectors")),
        new Signature("morvessa_the_runebloom_witch", "runebloom_ritual", "Runebloom Ritual", "Soak the sigils, then dodge the rotating Petalstorm.", List.of("Runebloom Sigils", "Petalstorm", "Runebloom Familiars")),
        new Signature("voralith_the_crimson_warden", "resonance_lock", "Resonance Lock", "Look away before the resonance detonates.", List.of("Dominion Pulse", "Resonance Lock")),
        new Signature("corrupted_oathkeeper", "oath_rings", "Oath Rings", "Read IN or OUT and cross the ring before impact.", List.of("Oath Rings", "Corrupted Brand", "Corrupted Embers"))
    );
    private static final Map<String, Signature> BY_BOSS_ID;

    static {
        Map<String, Signature> byBossId = new LinkedHashMap<>();
        for (Signature signature : SIGNATURES) {
            byBossId.put(signature.bossId(), signature);
        }
        BY_BOSS_ID = Map.copyOf(byBossId);
    }

    private BossMechanics() {
    }

    public static List<Signature> signatures() {
        return SIGNATURES;
    }

    public static Signature signature(String bossId) {
        Signature signature = bossId == null ? null : BY_BOSS_ID.get(bossId);
        if (signature == null) {
            throw new IllegalArgumentException("No boss mechanic profile for " + bossId);
        }
        return signature;
    }

    public static int scaledObjectiveCount(int playerCount, int maximum) {
        int safeMaximum = Math.max(1, maximum);
        int players = Math.max(1, playerCount);
        return Math.min(safeMaximum, Math.max(1, (players + 1) / 2));
    }

    public static double splitDamage(double totalDamage, int stackSize, double soloCap) {
        double safeTotal = Math.max(0.0, totalDamage);
        int players = Math.max(1, stackSize);
        if (players == 1) {
            return Math.min(safeTotal, Math.max(0.0, soloCap));
        }
        return safeTotal / players;
    }

    public static double staggerThreshold(double maxHealth, int playerCount) {
        double safeHealth = Math.max(1.0, maxHealth);
        int extraPlayers = Math.min(6, Math.max(0, playerCount - 1));
        return safeHealth * (0.035 + extraPlayers * 0.0035);
    }

    public static boolean isLookingToward(double directionDotProduct) {
        return directionDotProduct >= 0.55;
    }

    public static boolean isAngleInSector(double angle, double sectorCenter, double halfWidth) {
        double safeHalfWidth = Math.max(0.0, Math.min(Math.PI, halfWidth));
        return Math.abs(normalizeRadians(angle - sectorCenter)) <= safeHalfWidth;
    }

    public static boolean isInsideForwardLane(
        double pointX,
        double pointZ,
        double originX,
        double originZ,
        double angle,
        double length,
        double halfWidth
    ) {
        double dx = pointX - originX;
        double dz = pointZ - originZ;
        double forward = Math.cos(angle) * dx + Math.sin(angle) * dz;
        double sideways = -Math.sin(angle) * dx + Math.cos(angle) * dz;
        return forward >= 0.0
            && forward <= Math.max(0.0, length)
            && Math.abs(sideways) <= Math.max(0.0, halfWidth);
    }

    public static boolean isInsideCenteredLane(
        double pointX,
        double pointZ,
        double centerX,
        double centerZ,
        double angle,
        double halfLength,
        double halfWidth
    ) {
        double dx = pointX - centerX;
        double dz = pointZ - centerZ;
        double along = Math.cos(angle) * dx + Math.sin(angle) * dz;
        double sideways = -Math.sin(angle) * dx + Math.cos(angle) * dz;
        return Math.abs(along) <= Math.max(0.0, halfLength)
            && Math.abs(sideways) <= Math.max(0.0, halfWidth);
    }

    public static boolean isOnSafeSide(double offsetX, double offsetZ, double safeSideAngle) {
        return Math.cos(safeSideAngle) * offsetX + Math.sin(safeSideAngle) * offsetZ >= 0.0;
    }

    public static boolean isMechanicStale(long now, long expiresAt, long graceMillis) {
        return now > expiresAt && now - expiresAt > Math.max(0L, graceMillis);
    }

    public static double arenaEdgePressure(double horizontalDistance, double arenaRadius) {
        double radius = Math.max(1.0, arenaRadius);
        double steeringStart = radius * 0.70;
        double distance = Math.max(0.0, horizontalDistance);
        if (distance <= steeringStart) {
            return 0.0;
        }
        return Math.min(1.0, (distance - steeringStart) / (radius - steeringStart));
    }

    public static double edgeKnockbackResistance(double baseResistance, double edgePressure) {
        double base = Math.max(0.0, Math.min(1.0, baseResistance));
        double pressure = Math.max(0.0, Math.min(1.0, edgePressure));
        double maximum = base >= 0.95 ? 1.0 : 0.95;
        return base + (maximum - base) * pressure;
    }

    public static double retainedOutwardKnockback(double edgePressure) {
        double pressure = Math.max(0.0, Math.min(1.0, edgePressure));
        return 1.0 - pressure * 0.95;
    }

    public static double arenaRecoveryRadius(double arenaRadius) {
        return Math.max(1.0, arenaRadius * 0.62);
    }

    public static boolean needsHardArenaRecovery(double horizontalDistance, double arenaRadius) {
        double radius = Math.max(1.0, arenaRadius);
        return horizontalDistance > radius + Math.max(2.5, radius * 0.12);
    }

    public static boolean isArenaRestrictedPlayer(
        boolean alreadyTracked,
        double horizontalDistance,
        double arenaRadius
    ) {
        double radius = Math.max(1.0, arenaRadius);
        return alreadyTracked || Math.max(0.0, horizontalDistance) <= radius;
    }

    public static boolean shouldRecoverArenaPlayer(double horizontalDistance, double arenaRadius, double escapeBuffer) {
        double radius = Math.max(1.0, arenaRadius);
        return Math.max(0.0, horizontalDistance) > radius + Math.max(0.0, escapeBuffer);
    }

    public static double playerArenaRecoveryRadius(double arenaRadius) {
        return Math.max(1.0, arenaRadius - 1.5D);
    }

    public static boolean shouldSwitchAggroTarget(double currentScore, double challengerScore, double switchMargin) {
        return challengerScore >= currentScore + Math.max(0.0, switchMargin);
    }

    public static SuccessReward successReward(String mechanicId, int tier) {
        if (mechanicId == null || tier < 5) {
            return SuccessReward.NONE;
        }
        return switch (mechanicId.trim().toLowerCase(Locale.ROOT)) {
            case "undertow", "runebloom_sigils" -> SuccessReward.INSTANT_HEAL;
            case "tidal_divide", "rift_sectors", "oath_rings" -> SuccessReward.SPEED_I;
            default -> SuccessReward.NONE;
        };
    }

    static double normalizeRadians(double angle) {
        double normalized = angle % (Math.PI * 2.0);
        if (normalized > Math.PI) {
            normalized -= Math.PI * 2.0;
        } else if (normalized < -Math.PI) {
            normalized += Math.PI * 2.0;
        }
        return normalized;
    }

    public record Signature(String bossId, String mechanicId, String displayName, String counterplay, List<String> phaseTwoMechanics) {
        public Signature {
            phaseTwoMechanics = List.copyOf(phaseTwoMechanics);
        }
    }

    public enum SuccessReward {
        NONE(""),
        SPEED_I("Success grants Speed I for 4s."),
        INSTANT_HEAL("Success restores 2 hearts.");

        private final String description;

        SuccessReward(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }
}
