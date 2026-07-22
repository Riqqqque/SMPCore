package me.rique.smpcore.power;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

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
}
