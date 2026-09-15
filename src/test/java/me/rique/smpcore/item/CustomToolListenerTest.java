package me.rique.smpcore.item;

import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomToolListenerTest {

    @Test
    void restockTokensDropOnlyFromFireDeaths() {
        assertTrue(CustomToolListener.isRestockTokenBurnCause(EntityDamageEvent.DamageCause.FIRE));
        assertTrue(CustomToolListener.isRestockTokenBurnCause(EntityDamageEvent.DamageCause.FIRE_TICK));
        assertTrue(CustomToolListener.isRestockTokenBurnCause(EntityDamageEvent.DamageCause.LAVA));
        assertFalse(CustomToolListener.isRestockTokenBurnCause(EntityDamageEvent.DamageCause.FALL));
        assertFalse(CustomToolListener.isRestockTokenBurnCause(EntityDamageEvent.DamageCause.ENTITY_ATTACK));
    }
}
