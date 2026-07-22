package me.rique.smpcore.boss;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossMechanicsTest {

    @Test
    void everyBossHasOneUniqueSignatureMechanic() {
        var mechanics = BossMechanics.signatures();

        assertEquals(10, mechanics.size());
        assertEquals(10, new HashSet<>(mechanics.stream().map(BossMechanics.Signature::bossId).toList()).size());
        assertEquals(10, new HashSet<>(mechanics.stream().map(BossMechanics.Signature::mechanicId).toList()).size());
        assertTrue(mechanics.stream().allMatch(profile -> !profile.displayName().isBlank() && !profile.counterplay().isBlank()));
        assertTrue(mechanics.stream().allMatch(profile -> profile.phaseTwoMechanics().size() >= 2));
        assertTrue(mechanics.stream().allMatch(profile -> new HashSet<>(profile.phaseTwoMechanics()).size() == profile.phaseTwoMechanics().size()));
    }

    @Test
    void objectivesScaleWithoutMakingSoloFightsImpossible() {
        assertEquals(1, BossMechanics.scaledObjectiveCount(1, 3));
        assertEquals(1, BossMechanics.scaledObjectiveCount(2, 3));
        assertEquals(2, BossMechanics.scaledObjectiveCount(3, 3));
        assertEquals(3, BossMechanics.scaledObjectiveCount(6, 3));
        assertEquals(3, BossMechanics.scaledObjectiveCount(20, 3));

        int previous = 0;
        for (int playerCount = 1; playerCount <= 7; playerCount++) {
            int objectives = BossMechanics.scaledObjectiveCount(playerCount, 3);
            assertTrue(objectives >= previous);
            assertTrue(objectives <= playerCount);
            previous = objectives;
        }
    }

    @Test
    void markedPlayersNeverDamageThemselvesButNearbyAlliesAreHit() {
        UUID marked = UUID.randomUUID();
        UUID ally = UUID.randomUUID();

        assertFalse(BossMechanics.isOtherPlayerInsideMarker(marked, marked, 0.0, 3.25));
        assertTrue(BossMechanics.isOtherPlayerInsideMarker(marked, ally, 3.25 * 3.25, 3.25));
        assertFalse(BossMechanics.isOtherPlayerInsideMarker(marked, ally, 3.26 * 3.26, 3.25));
        assertFalse(BossMechanics.isOtherPlayerInsideMarker(null, ally, 0.0, 3.25));
    }

    @Test
    void newWidowTrailPointsTelegraphBeforeBecomingHazardous() {
        assertEquals(2, BossMechanics.settledTrailPointCount(3, true));
        assertEquals(3, BossMechanics.settledTrailPointCount(3, false));
        assertEquals(0, BossMechanics.settledTrailPointCount(0, true));
    }

    @Test
    void marshalInstructionsMatchIsolationBehavior() {
        BossMechanics.Signature marshal = BossMechanics.signature("yule_the_minion");
        assertEquals("Blue Isolation", marshal.displayName());
        assertTrue(marshal.counterplay().toLowerCase().contains("alone"));
        assertFalse(marshal.counterplay().toLowerCase().contains("stack"));
    }

    @Test
    void gazeAndSectorChecksHandleBoundaryAngles() {
        assertTrue(BossMechanics.isLookingToward(0.9));
        assertFalse(BossMechanics.isLookingToward(0.3));

        double center = Math.toRadians(175.0);
        assertTrue(BossMechanics.isAngleInSector(Math.toRadians(-175.0), center, Math.toRadians(20.0)));
        assertFalse(BossMechanics.isAngleInSector(Math.toRadians(-120.0), center, Math.toRadians(20.0)));
    }

    @Test
    void deadeyeLaneOnlyHitsPlayersInFrontOfTheShot() {
        assertTrue(BossMechanics.isInsideForwardLane(8.0, 0.5, 0.0, 0.0, 0.0, 12.0, 1.25));
        assertFalse(BossMechanics.isInsideForwardLane(8.0, 2.0, 0.0, 0.0, 0.0, 12.0, 1.25));
        assertFalse(BossMechanics.isInsideForwardLane(-2.0, 0.0, 0.0, 0.0, 0.0, 12.0, 1.25));
        assertFalse(BossMechanics.isInsideForwardLane(13.0, 0.0, 0.0, 0.0, 0.0, 12.0, 1.25));
    }

    @Test
    void briarLanesDoNotDamagePastTheirVisibleEndpoints() {
        assertTrue(BossMechanics.isInsideCenteredLane(5.0, 1.0, 0.0, 0.0, 0.0, 8.0, 1.65));
        assertFalse(BossMechanics.isInsideCenteredLane(9.0, 0.0, 0.0, 0.0, 0.0, 8.0, 1.65));
        assertFalse(BossMechanics.isInsideCenteredLane(5.0, 2.0, 0.0, 0.0, 0.0, 8.0, 1.65));
    }

    @Test
    void tidalDivideUsesTheTelegraphedHalfOfTheArena() {
        assertTrue(BossMechanics.isOnSafeSide(4.0, 0.0, 0.0));
        assertTrue(BossMechanics.isOnSafeSide(0.0, 0.0, 0.0));
        assertFalse(BossMechanics.isOnSafeSide(-4.0, 0.0, 0.0));
        assertTrue(BossMechanics.isOnSafeSide(0.0, 4.0, Math.PI / 2.0));
    }

    @Test
    void staleMechanicsAreCancelledAfterAGraceWindow() {
        assertFalse(BossMechanics.isMechanicStale(12_000L, 10_000L, 2_000L));
        assertTrue(BossMechanics.isMechanicStale(12_001L, 10_000L, 2_000L));
        assertFalse(BossMechanics.isMechanicStale(9_000L, 10_000L, 2_000L));
    }

    @Test
    void staggerCheckScalesMoreSlowlyThanBossHealth() {
        double baseHealth = BossBalance.profile("iron_saint").maxHealth();
        double solo = BossMechanics.staggerThreshold(baseHealth, 1);
        double groupHealth = baseHealth * BossBalance.multiplayerHealthScale(6, 4);
        double group = BossMechanics.staggerThreshold(groupHealth, 4);

        assertEquals(51.625, solo, 0.001);
        assertTrue(group > solo);
        assertTrue(group < 200.0);
        assertTrue(group / 4.0 <= solo * 1.10);
    }

    @Test
    void arenaSteeringRampsBeforeTheBossTouchesTheWall() {
        assertEquals(0.0, BossMechanics.arenaEdgePressure(9.8, 14.0), 0.001);
        assertEquals(0.5, BossMechanics.arenaEdgePressure(11.9, 14.0), 0.001);
        assertEquals(1.0, BossMechanics.arenaEdgePressure(14.0, 14.0), 0.001);
        assertEquals(1.0, BossMechanics.arenaEdgePressure(20.0, 14.0), 0.001);
    }

    @Test
    void edgeProtectionDampensOnlyAsTheWallGetsCloser() {
        assertEquals(0.20, BossMechanics.edgeKnockbackResistance(0.20, 0.0), 0.001);
        assertEquals(0.575, BossMechanics.edgeKnockbackResistance(0.20, 0.5), 0.001);
        assertEquals(0.95, BossMechanics.edgeKnockbackResistance(0.20, 1.0), 0.001);
        assertEquals(1.0, BossMechanics.retainedOutwardKnockback(0.0), 0.001);
        assertEquals(0.525, BossMechanics.retainedOutwardKnockback(0.5), 0.001);
        assertEquals(0.05, BossMechanics.retainedOutwardKnockback(1.0), 0.001);
    }

    @Test
    void hardRecoveryIsReservedForLargeBoundaryEscapes() {
        assertFalse(BossMechanics.needsHardArenaRecovery(14.5, 14.0));
        assertTrue(BossMechanics.needsHardArenaRecovery(17.0, 14.0));
        assertEquals(8.68, BossMechanics.arenaRecoveryRadius(14.0), 0.001);
    }

    @Test
    void arenaPlayersGetGraceAtTheWallButCannotEscape() {
        assertTrue(BossMechanics.isArenaRestrictedPlayer(false, 14.0, 14.0));
        assertFalse(BossMechanics.isArenaRestrictedPlayer(false, 14.01, 14.0));
        assertTrue(BossMechanics.isArenaRestrictedPlayer(true, 30.0, 14.0));

        assertFalse(BossMechanics.shouldRecoverArenaPlayer(15.75, 14.0, 1.75));
        assertTrue(BossMechanics.shouldRecoverArenaPlayer(15.76, 14.0, 1.75));
        assertEquals(12.5, BossMechanics.playerArenaRecoveryRadius(14.0), 0.001);
    }

    @Test
    void aggroOnlySwitchesWhenTheChallengerClearlyWins() {
        assertFalse(BossMechanics.shouldSwitchAggroTarget(8.0, 9.24, 1.25));
        assertTrue(BossMechanics.shouldSwitchAggroTarget(8.0, 9.25, 1.25));
        assertTrue(BossMechanics.shouldSwitchAggroTarget(8.0, 8.0, -1.0));
    }

    @Test
    void positionalSuccessRewardsStartAtTierFiveAndStayLimitedToPositionChecks() {
        assertEquals(BossMechanics.SuccessReward.NONE, BossMechanics.successReward("undertow", 4));
        assertEquals(BossMechanics.SuccessReward.INSTANT_HEAL, BossMechanics.successReward("undertow", 5));
        assertEquals(BossMechanics.SuccessReward.SPEED_I, BossMechanics.successReward("tidal_divide", 5));
        assertEquals(BossMechanics.SuccessReward.SPEED_I, BossMechanics.successReward("rift_sectors", 7));
        assertEquals(BossMechanics.SuccessReward.INSTANT_HEAL, BossMechanics.successReward("runebloom_sigils", 8));
        assertEquals(BossMechanics.SuccessReward.SPEED_I, BossMechanics.successReward("oath_rings", 10));
        assertEquals(BossMechanics.SuccessReward.NONE, BossMechanics.successReward("saints_stagger", 6));
        assertEquals(BossMechanics.SuccessReward.NONE, BossMechanics.successReward("resonance_lock", 9));
        assertEquals(BossMechanics.SuccessReward.NONE, BossMechanics.successReward(null, 10));
    }
}
