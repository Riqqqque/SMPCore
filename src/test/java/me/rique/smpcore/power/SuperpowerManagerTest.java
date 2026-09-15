package me.rique.smpcore.power;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuperpowerManagerTest {

    @Test
    void backstabRequiresTheAttackerToBeBehindTheTarget() {
        Vector facingNorth = new Vector(0.0D, 0.0D, -1.0D);

        assertTrue(SuperpowerManager.isBehindTarget(facingNorth, new Vector(0.0D, 0.0D, 1.0D)));
        assertFalse(SuperpowerManager.isBehindTarget(facingNorth, new Vector(0.0D, 0.0D, -1.0D)));
        assertFalse(SuperpowerManager.isBehindTarget(facingNorth, new Vector(1.0D, 0.0D, 0.0D)));
        assertFalse(SuperpowerManager.isBehindTarget(facingNorth, new Vector(0.0D, 2.0D, 0.0D)));
    }

    @Test
    void backstabDealsNinetyPercentCurrentHealthWithOneDamageMinimum() {
        assertEquals(18.0D, SuperpowerManager.veilAssassinBackstabDamage(20.0D), 0.0001D);
        assertEquals(4.5D, SuperpowerManager.veilAssassinBackstabDamage(5.0D), 0.0001D);
        assertEquals(1.0D, SuperpowerManager.veilAssassinBackstabDamage(1.0D), 0.0001D);
        assertEquals(1.0D, SuperpowerManager.veilAssassinBackstabDamage(-5.0D), 0.0001D);
    }

    @Test
    void successfulAttackBreaksVeilAssassinConcealment() {
        assertTrue(SuperpowerManager.shouldBreakVeilAssassinConcealment(true, true));
        assertFalse(SuperpowerManager.shouldBreakVeilAssassinConcealment(true, false));
        assertFalse(SuperpowerManager.shouldBreakVeilAssassinConcealment(false, true));
    }

    @Test
    void veiledAssassinsAreIgnoredByOrdinaryMobsButNotBosses() {
        assertTrue(SuperpowerManager.shouldIgnoreVeiledPlayerTarget(true, false));
        assertFalse(SuperpowerManager.shouldIgnoreVeiledPlayerTarget(true, true));
        assertFalse(SuperpowerManager.shouldIgnoreVeiledPlayerTarget(false, false));
    }

    @Test
    void classCommandParserRecognizesAliasesAndNamespacedCommands() {
        assertTrue(SuperpowerManager.isClassAbilityCommand("/shadow toggle"));
        assertTrue(SuperpowerManager.isClassAbilityCommand("/sb"));
        assertTrue(SuperpowerManager.isClassAbilityCommand("/bulwark"));
        assertTrue(SuperpowerManager.isClassAbilityCommand("/smpcore:msummon 4"));
        assertTrue(SuperpowerManager.isClassAbilityCommand("  /SMPCORE:DOMAINEXP  "));
        assertFalse(SuperpowerManager.isClassAbilityCommand("/spawn"));
        assertFalse(SuperpowerManager.isClassAbilityCommand("shadow toggle"));
        assertFalse(SuperpowerManager.isClassAbilityCommand(null));
    }

    @Test
    void classEffectsAndTargetImmunityNeverApplyToBosses() {
        assertTrue(SuperpowerManager.classEffectsCanModifyTarget(false));
        assertFalse(SuperpowerManager.classEffectsCanModifyTarget(true));
        assertTrue(SuperpowerManager.shouldIgnorePassiveMobTarget(true, false));
        assertFalse(SuperpowerManager.shouldIgnorePassiveMobTarget(true, true));
        assertFalse(SuperpowerManager.shouldIgnorePassiveMobTarget(false, false));
    }

    @Test
    void sentinelBraceReducesIncomingDamageByFortyPercent() {
        assertEquals(6.0D, SuperpowerManager.sentinelBraceDamage(10.0D), 0.0001D);
        assertEquals(0.0D, SuperpowerManager.sentinelBraceDamage(-5.0D), 0.0001D);
    }

    @Test
    void physicallyGiftedUsesDistinctMobPlayerAndBossMultipliers() {
        assertEquals(2.0D, SuperpowerManager.physicallyGiftedDamageMultiplier(false, false), 0.0001D);
        assertEquals(1.75D, SuperpowerManager.physicallyGiftedDamageMultiplier(true, false), 0.0001D);
        assertEquals(1.50D, SuperpowerManager.physicallyGiftedDamageMultiplier(false, true), 0.0001D);
        assertEquals(1.50D, SuperpowerManager.physicallyGiftedDamageMultiplier(true, true), 0.0001D);
    }

    @Test
    void physicallyGiftedAliasesResolveToTheSameClass() {
        assertEquals(SuperpowerType.PHYSICALLY_GIFTED, SuperpowerType.fromId("physically gifted"));
        assertEquals(SuperpowerType.PHYSICALLY_GIFTED, SuperpowerType.fromId("heavenly restriction"));
    }

    @Test
    void voidstepRequiresSneakingRightClickWithAnEmptyMainHand() {
        assertTrue(SuperpowerManager.isVoidstepGesture(true, true, true, true));

        assertFalse(SuperpowerManager.isVoidstepGesture(false, true, true, true));
        assertFalse(SuperpowerManager.isVoidstepGesture(true, false, true, true));
        assertFalse(SuperpowerManager.isVoidstepGesture(true, true, false, true));
        assertFalse(SuperpowerManager.isVoidstepGesture(true, true, true, false));
    }

    @Test
    void fullVirtualLapisDoesNotTriggerAnotherEnchantInventoryMutation() {
        assertTrue(SuperpowerManager.needsVirtualLapisRefill(true, true, false, 0));
        assertTrue(SuperpowerManager.needsVirtualLapisRefill(true, false, true, 12));
        assertFalse(SuperpowerManager.needsVirtualLapisRefill(true, false, true, 64));
        assertFalse(SuperpowerManager.needsVirtualLapisRefill(true, false, false, 12));
        assertFalse(SuperpowerManager.needsVirtualLapisRefill(false, true, false, 0));
    }

    @Test
    void druidGrimoireOnlyStaysWithItsBoundDruid() {
        UUID druid = UUID.randomUUID();
        UUID otherPlayer = UUID.randomUUID();

        assertTrue(SuperpowerManager.canKeepDruidGrimoire(true, druid, druid));
        assertTrue(SuperpowerManager.canKeepDruidGrimoire(true, druid, null));
        assertFalse(SuperpowerManager.canKeepDruidGrimoire(true, otherPlayer, druid));
        assertFalse(SuperpowerManager.canKeepDruidGrimoire(false, druid, druid));
        assertFalse(SuperpowerManager.canKeepDruidGrimoire(true, null, druid));
    }
}
