package me.rique.smpcore.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TntMinecartDamageListenerTest {

    @Test
    void capsDamageAtFiveHearts() {
        assertEquals(10.0D, TntMinecartDamageListener.cappedDamage(80.0D));
        assertEquals(10.0D, TntMinecartDamageListener.cappedDamage(Double.POSITIVE_INFINITY));
    }

    @Test
    void leavesLowerDamageAloneAndRejectsInvalidValues() {
        assertEquals(7.5D, TntMinecartDamageListener.cappedDamage(7.5D));
        assertEquals(0.0D, TntMinecartDamageListener.cappedDamage(-4.0D));
        assertEquals(0.0D, TntMinecartDamageListener.cappedDamage(Double.NaN));
    }
}
