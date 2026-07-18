package me.rique.smpcore.combat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityDamageContextTest {

    @Test
    void nestedAbilityDamageKeepsTheContextUntilTheOuterCallFinishes() {
        UUID attackerId = UUID.randomUUID();

        AbilityDamageContext.run(attackerId, () -> {
            assertTrue(AbilityDamageContext.isActive(attackerId));
            AbilityDamageContext.run(attackerId, () -> assertTrue(AbilityDamageContext.isActive(attackerId)));
            assertTrue(AbilityDamageContext.isActive(attackerId));
        });

        assertFalse(AbilityDamageContext.isActive(attackerId));
    }

    @Test
    void failedAbilityDamageCannotLeakItsContext() {
        UUID attackerId = UUID.randomUUID();

        assertThrows(IllegalStateException.class, () -> AbilityDamageContext.run(attackerId, () -> {
            throw new IllegalStateException("test");
        }));

        assertFalse(AbilityDamageContext.isActive(attackerId));
    }
}
