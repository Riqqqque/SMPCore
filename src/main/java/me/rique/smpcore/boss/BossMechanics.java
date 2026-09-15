package me.rique.smpcore.boss;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class BossMechanics {
    static final long SAINTS_STAGGER_RESPONSE_MILLIS = 7_000L;
    static final long SAINTS_STAGGER_EXPOSURE_MILLIS = 6_000L;
    static final long SAINTS_STAGGER_MINIMUM_BREAK_MILLIS = 1_000L;
    static final long IRON_COUNTERSTANCE_RESPONSE_MILLIS = 5_000L;
    static final long IRON_COUNTERSTANCE_EXPOSURE_MILLIS = 5_000L;
    static final long OATH_RING_RESPONSE_MILLIS = 3_500L;
    static final long OATH_RING_REVERSAL_RESPONSE_MILLIS = 3_000L;

    private static final List<Signature> SIGNATURES = List.of(
        new Signature("yule_the_minion", "marshal_stack", "Blue Isolation", "Leave the blue-marked player alone inside the circle.", List.of("Blue Isolation", "Veilbound Muster")),
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
    private static final Map<String, List<GuideMechanic>> GUIDE_MECHANICS = Map.ofEntries(
        Map.entry("yule_the_minion", List.of(
            new GuideMechanic("All phases", "Blue Isolation", "The blue mark stays in its circle; a clean isolation briefly exposes the Marshal."),
            new GuideMechanic("Phase 2", "Veilbound Thralls", "Clear the three thralls summoned at half health.")
        )),
        Map.entry("kael_the_ashen", List.of(
            new GuideMechanic("All phases", "Ashen Crossfire", "Spread every red mark cleanly to briefly expose Kael."),
            new GuideMechanic("Phase 2", "Deadeye", "Bait the aim line, then leave it after it turns red.")
        )),
        Map.entry("vesper_the_widow_queen", List.of(
            new GuideMechanic("All phases", "Widow's Claim", "The hunted player keeps moving while everyone avoids the trail."),
            new GuideMechanic("Phase 2", "Webbreak", "Every marked player breaks their web to briefly expose Vesper.")
        )),
        Map.entry("mirewood_the_root_tyrant", List.of(
            new GuideMechanic("All phases", "Root Wards", "Fill every green ward to briefly expose Mirewood."),
            new GuideMechanic("Phase 2", "Briar Lattice", "Leave both red root lines before they erupt.")
        )),
        Map.entry("nereida_the_abyss_mother", List.of(
            new GuideMechanic("All phases", "Undertow", "Brace for the pull, then escape the expanding red tide."),
            new GuideMechanic("Phase 2", "Tidal Divide", "Get everyone onto green to briefly expose Nereida.")
        )),
        Map.entry("iron_saint", List.of(
            new GuideMechanic("All phases", "Saint's Stagger", "Damage the Saint until his guard breaks, then burst him."),
            new GuideMechanic("Phase 2", "Counterstance", "Stop attacking until the stance breaks, then burst him.")
        )),
        Map.entry("aurelion_the_rift_seraph", List.of(
            new GuideMechanic("All phases", "Rift Step", "Expect teleports around distant targets and keep sight of him."),
            new GuideMechanic("All phases", "Rift Sectors", "Move into the green wedge or the safe center.")
        )),
        Map.entry("morvessa_the_runebloom_witch", List.of(
            new GuideMechanic("All phases", "Runebloom Sigils", "Put at least one player inside every green sigil."),
            new GuideMechanic("Phase 2", "Petalstorm", "Keep moving around the rotating beam."),
            new GuideMechanic("Phase 2", "Runebloom Familiars", "Clear the capped familiar wave before Morvessa replaces it.")
        )),
        Map.entry("voralith_the_crimson_warden", List.of(
            new GuideMechanic("All phases", "Dominion Pulse", "Leave the red pulse circle before the shockwave lands."),
            new GuideMechanic("All phases", "Resonance Lock", "Turn your camera away before the resonance detonates.")
        )),
        Map.entry("corrupted_oathkeeper", List.of(
            new GuideMechanic("All phases", "Oath Rings", "Obey IN or OUT. Phase 3 reverses once; missing both rings is fatal."),
            new GuideMechanic("Phase 2+", "Corrupted Brand", "Leave the purple circle before it detonates and pulls inward."),
            new GuideMechanic("Phase 2+", "Corrupted Embers", "Break the capped Ember wave to expose the Oathkeeper.")
        ))
    );

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

    public static List<GuideMechanic> guideMechanics(String bossId) {
        List<GuideMechanic> mechanics = bossId == null ? null : GUIDE_MECHANICS.get(bossId);
        if (mechanics == null) {
            throw new IllegalArgumentException("No boss mechanic guide for " + bossId);
        }
        return mechanics;
    }

    public static int scaledObjectiveCount(int playerCount, int maximum) {
        int safeMaximum = Math.max(1, maximum);
        int players = Math.max(1, playerCount);
        return Math.min(safeMaximum, Math.max(1, (players + 1) / 2));
    }

    public static boolean isOtherPlayerInsideMarker(
        UUID markerOwnerId,
        UUID candidateId,
        double distanceSquared,
        double radius
    ) {
        if (markerOwnerId == null || candidateId == null || markerOwnerId.equals(candidateId)) {
            return false;
        }
        double safeRadius = Math.max(0.0, radius);
        return Math.max(0.0, distanceSquared) <= safeRadius * safeRadius;
    }

    public static int settledTrailPointCount(int totalPoints, boolean pointPlacedThisTick) {
        int safeTotal = Math.max(0, totalPoints);
        return pointPlacedThisTick ? Math.max(0, safeTotal - 1) : safeTotal;
    }

    public static double staggerThreshold(double maxHealth, int playerCount) {
        double safeHealth = Math.max(1.0, maxHealth);
        int extraPlayers = Math.min(6, Math.max(0, playerCount - 1));
        return safeHealth * (0.035 + extraPlayers * 0.0035);
    }

    public static boolean canBreakSaintsGuard(double progress, double threshold, long elapsedMillis) {
        return progress >= Math.max(1.0, threshold)
            && elapsedMillis >= SAINTS_STAGGER_MINIMUM_BREAK_MILLIS;
    }

    public static int secondsRemaining(long deadlineMillis, long nowMillis) {
        return (int) Math.max(0L, (deadlineMillis - nowMillis + 999L) / 1_000L);
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

    public static double oathRingFailureHealthRatio(int phase) {
        if (phase >= 3) {
            return 0.58;
        }
        return phase >= 2 ? 0.78 : 0.72;
    }

    public static long oathRingWarningMillis(int phase, int stage) {
        if (phase >= 3 && stage > 0) {
            return OATH_RING_REVERSAL_RESPONSE_MILLIS;
        }
        return OATH_RING_RESPONSE_MILLIS;
    }

    public static long oathRingDurationMillis(int phase) {
        long duration = oathRingWarningMillis(phase, 0);
        return phase >= 3 ? duration + oathRingWarningMillis(phase, 1) : duration;
    }

    public static long oathRingCooldownMillis(int phase) {
        if (phase >= 3) {
            return 15_000L;
        }
        return phase >= 2 ? 14_000L : 16_000L;
    }

    public static double corruptedBrandRadius(int phase, double usableArenaRadius) {
        double requested = phase >= 3 ? 17.0 : 15.0;
        double maximum = Math.max(6.0, usableArenaRadius - 3.5);
        return Math.max(6.0, Math.min(requested, maximum));
    }

    public static long corruptedBrandCooldownMillis(int phase) {
        return phase >= 3 ? 15_000L : 17_000L;
    }

    public static long corruptedBrandTelegraphTicks(int phase) {
        return phase >= 3 ? 44L : 48L;
    }

    public static int corruptedEmberCap(int phase, int playerCount) {
        int base = phase >= 3 ? 3 : 2;
        int maximum = phase >= 3 ? 5 : 4;
        int extra = Math.max(0, playerCount - 1) / 2;
        return Math.min(maximum, base + extra);
    }

    public static double corruptedEmberBaseHealth(int phase) {
        return phase >= 3 ? 58.0 : 42.0;
    }

    public static long corruptedEmberLifetimeMillis(int phase) {
        return phase >= 3 ? 36_000L : 42_000L;
    }

    public static long corruptedEmberCooldownMillis(int phase) {
        return phase >= 3 ? 28_000L : 34_000L;
    }

    public static int runebloomFamiliarCap(int playerCount) {
        int extra = Math.max(0, playerCount - 1) / 2;
        return Math.min(5, 2 + extra);
    }

    public static int runebloomFamiliarSpawnCount(int activeCount, int playerCount) {
        return Math.max(0, runebloomFamiliarCap(playerCount) - Math.max(0, activeCount));
    }

    public static long runebloomFamiliarLifetimeMillis() {
        return 45_000L;
    }

    public static OffensiveWindow corruptedEmberExposure(int phase) {
        return phase >= 3
            ? new OffensiveWindow(1.25, 6_000L)
            : new OffensiveWindow(1.15, 5_000L);
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

    public static OffensiveWindow offensiveWindow(String mechanicId, int tier) {
        if (mechanicId == null || tier < 1) {
            return OffensiveWindow.NONE;
        }
        return switch (mechanicId.trim().toLowerCase(Locale.ROOT)) {
            case "marshal_stack" -> tier >= 1 ? new OffensiveWindow(1.10, 4_000L) : OffensiveWindow.NONE;
            case "ashen_crossfire" -> tier >= 2 ? new OffensiveWindow(1.12, 4_500L) : OffensiveWindow.NONE;
            case "widows_webbreak" -> tier >= 3 ? new OffensiveWindow(1.15, 5_000L) : OffensiveWindow.NONE;
            case "root_wards" -> tier >= 4 ? new OffensiveWindow(1.18, 5_500L) : OffensiveWindow.NONE;
            case "tidal_divide" -> tier >= 5 ? new OffensiveWindow(1.20, 5_500L) : OffensiveWindow.NONE;
            case "rift_sectors" -> tier >= 7 ? new OffensiveWindow(1.35, 6_500L) : OffensiveWindow.NONE;
            case "runebloom_sigils" -> tier >= 8 ? new OffensiveWindow(1.35, 7_000L) : OffensiveWindow.NONE;
            case "resonance_lock" -> tier >= 9 ? new OffensiveWindow(1.40, 6_500L) : OffensiveWindow.NONE;
            case "oath_rings" -> tier >= 10 ? new OffensiveWindow(1.40, 7_000L) : OffensiveWindow.NONE;
            default -> OffensiveWindow.NONE;
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

    public record GuideMechanic(String phase, String displayName, String counterplay) {
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

    public record OffensiveWindow(double damageMultiplier, long durationMillis) {
        public static final OffensiveWindow NONE = new OffensiveWindow(1.0, 0L);

        public boolean active() {
            return damageMultiplier > 1.0 && durationMillis > 0L;
        }

        public int bonusPercent() {
            return (int) Math.round((damageMultiplier - 1.0) * 100.0);
        }
    }
}
