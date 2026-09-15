package me.rique.smpcore.legendary;

import org.junit.jupiter.api.Test;
import org.bukkit.persistence.PersistentDataContainer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class LegendaryListenerTest {

    @Test
    void heldWeaponEffectsOnlyApplyToOrdinaryDirectHits() {
        assertTrue(LegendaryListener.shouldApplyHeldWeaponEffects(true, false));
        assertFalse(LegendaryListener.shouldApplyHeldWeaponEffects(false, false));
        assertFalse(LegendaryListener.shouldApplyHeldWeaponEffects(true, true));
    }

    @Test
    void heldWeaponKillRewardsRequireTheActualDirectKillingBlow() {
        assertTrue(LegendaryListener.shouldRewardHeldWeaponKill(true, true, false));
        assertFalse(LegendaryListener.shouldRewardHeldWeaponKill(false, true, false));
        assertFalse(LegendaryListener.shouldRewardHeldWeaponKill(true, false, false));
        assertFalse(LegendaryListener.shouldRewardHeldWeaponKill(true, true, true));
    }

    @Test
    void witherBladeNeverTargetsManagedNpcs() {
        assertTrue(LegendaryListener.canWitherBladeAffect(false, false, false));
        assertFalse(LegendaryListener.canWitherBladeAffect(false, true, false));
        assertFalse(LegendaryListener.canWitherBladeAffect(true, false, false));
        assertFalse(LegendaryListener.canWitherBladeAffect(false, false, true));
    }

    @Test
    void chronoOnlyRescuesReadyOrdinaryLethalDamage() {
        long now = 10_000L;

        assertTrue(LegendaryListener.shouldTriggerChronoRescue(true, now, now, true, false));
        assertFalse(LegendaryListener.shouldTriggerChronoRescue(false, now, now, true, false));
        assertFalse(LegendaryListener.shouldTriggerChronoRescue(true, now + 1L, now, true, false));
        assertFalse(LegendaryListener.shouldTriggerChronoRescue(true, now, now, false, false));
        assertFalse(LegendaryListener.shouldTriggerChronoRescue(true, now, now, true, true));
    }

    @Test
    void wardenBladePreservesDamageAlreadyCalculatedFromLegalBonuses() {
        assertEquals(8.0D, LegendaryListener.wardenBladeMeleeDamage(8.0D));
        assertEquals(15.5D, LegendaryListener.wardenBladeMeleeDamage(15.5D));
        assertEquals(0.0D, LegendaryListener.wardenBladeMeleeDamage(-1.0D));
        assertEquals(0.0D, LegendaryListener.wardenBladeMeleeDamage(Double.NaN));
    }

    @Test
    void legendaryMenuShowsAvailabilityAndClaimingTeam() {
        assertEquals(
            List.of("<green>Status:</green> <white>Available to craft</white>"),
            LegendaryListener.legendaryClaimLore(new LegendaryAltarManager.LegendaryClaimStatus(
                true, false, 0, 1, List.of(), null
            ))
        );

        assertEquals(
            List.of(
                "<red>Status:</red> <white>Already crafted</white>",
                "<gray>Claimed team:</gray> <gold>The Honored Ones</gold>"
            ),
            LegendaryListener.legendaryClaimLore(new LegendaryAltarManager.LegendaryClaimStatus(
                true, true, 1, 1, List.of("The Honored Ones"), "Riq"
            ))
        );
    }

    @Test
    void legendaryTeamVaultSourceOnlyAcceptsRealTeamVaultKeys() {
        assertEquals("thehonoredones", LegendaryAltarManager.teamKeyFromSource("team_vault:TheHonoredOnes"));
        assertNull(LegendaryAltarManager.teamKeyFromSource("ender_chest:thehonoredones"));
        assertNull(LegendaryAltarManager.teamKeyFromSource("team_vault:"));
    }

    @Test
    void killProgressLevelsAreDerivedOnlyFromEligibleTrackedKills() {
        assertEquals(5, LegendaryListener.derivedKillProgressLevel(5, 20, 0));
        assertEquals(8, LegendaryListener.derivedKillProgressLevel(5, 20, 3));
        assertEquals(20, LegendaryListener.derivedKillProgressLevel(5, 20, 100));
        assertEquals(5, LegendaryListener.derivedKillProgressLevel(5, 20, -2));
    }

    @Test
    void formerTeammateCooldownUsesReadableMonthScaleDurations() {
        assertEquals("30d 0h", LegendaryListener.formatLongCooldown(30L * 24L * 60L * 60L * 1000L));
        assertEquals("2h 1m", LegendaryListener.formatLongCooldown((2L * 60L + 1L) * 60L * 1000L));
        assertEquals("1m", LegendaryListener.formatLongCooldown(1L));
    }

    @Test
    void legendaryUtilityResultsCopyAllPersistentWeaponMetadata() {
        AtomicBoolean copied = new AtomicBoolean();
        AtomicBoolean replacedExistingValues = new AtomicBoolean();
        PersistentDataContainer target = pdcProxy((proxy, method, args) -> defaultValue(method.getReturnType()));
        PersistentDataContainer source = pdcProxy((proxy, method, args) -> {
            if ("copyTo".equals(method.getName())) {
                assertSame(target, args[0]);
                copied.set(true);
                replacedExistingValues.set(Boolean.TRUE.equals(args[1]));
            }
            return defaultValue(method.getReturnType());
        });

        LegendaryListener.copyPersistentItemMetadata(source, target);

        assertTrue(copied.get());
        assertTrue(replacedExistingValues.get());
    }

    private static PersistentDataContainer pdcProxy(java.lang.reflect.InvocationHandler handler) {
        return (PersistentDataContainer) Proxy.newProxyInstance(
            LegendaryListenerTest.class.getClassLoader(),
            new Class<?>[] {PersistentDataContainer.class},
            handler
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        if (type == double.class) {
            return 0.0D;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}
