package me.rique.smpcore.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityNullifierListenerTest {

    @Test
    void onlyMaceSmashDamageWithAMaceQualifies() {
        NamespacedKey maceSmash = NamespacedKey.minecraft("mace_smash");

        assertTrue(VelocityNullifierListener.isQualifyingMaceHit(maceSmash, Material.MACE));
        assertFalse(VelocityNullifierListener.isQualifyingMaceHit(
            NamespacedKey.minecraft("player_attack"),
            Material.MACE
        ));
        assertFalse(VelocityNullifierListener.isQualifyingMaceHit(maceSmash, Material.DIAMOND_SWORD));
        assertFalse(VelocityNullifierListener.isQualifyingMaceHit(null, Material.MACE));
    }

    @Test
    void cooldownUsesAFullFifteenSecondSharedWindow() {
        long now = 1_000_000L;
        long cooldownUntil = now + VelocityNullifierListener.COOLDOWN_MILLIS;

        assertEquals(15_000L, VelocityNullifierListener.remainingCooldownMillis(now, cooldownUntil));
        assertEquals(15L, VelocityNullifierListener.remainingCooldownSeconds(now, cooldownUntil));
        assertEquals(1L, VelocityNullifierListener.remainingCooldownSeconds(now, now + 1L));
        assertEquals(0L, VelocityNullifierListener.remainingCooldownSeconds(now, now));
        assertEquals(0L, VelocityNullifierListener.remainingCooldownMillis(now, now - 1L));
    }
}
