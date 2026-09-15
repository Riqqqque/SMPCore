package me.rique.smpcore.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaceDamageListenerTest {

    @Test
    void fullyChargedMaceCapsAtFiveHearts() {
        assertEquals(10.0D, MaceDamageListener.damageCap(1.0F));
        assertEquals(10.0D, MaceDamageListener.damageCap(2.0F));
    }

    @Test
    void partialChargesHaveLowerCaps() {
        assertEquals(4.0D, MaceDamageListener.damageCap(0.5F));
        assertEquals(2.0D, MaceDamageListener.damageCap(0.0F));
        assertEquals(2.0D, MaceDamageListener.damageCap(-1.0F));
    }
}
