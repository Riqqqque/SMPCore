package me.rique.smpcore.quest;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FamiliarMenuStateTest {
    @Test
    void summonedFamiliarOnlyOffersDismiss() {
        FamiliarMenuState state = FamiliarMenuState.from(true);

        assertFalse(state.canSummon());
        assertTrue(state.canDismiss());
        assertEquals(Material.LIME_STAINED_GLASS_PANE, state.summonIcon());
        assertEquals(Material.RED_DYE, state.dismissIcon());
        assertNull(state.summonAction("summon"));
        assertEquals("dismiss", state.dismissAction("dismiss"));
    }

    @Test
    void dismissedFamiliarOnlyOffersSummon() {
        FamiliarMenuState state = FamiliarMenuState.from(false);

        assertTrue(state.canSummon());
        assertFalse(state.canDismiss());
        assertEquals(Material.LIME_DYE, state.summonIcon());
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, state.dismissIcon());
        assertEquals("summon", state.summonAction("summon"));
        assertNull(state.dismissAction("dismiss"));
    }

    @Test
    void interactionCooldownAllowsFirstClickAndExactBoundary() {
        assertTrue(FamiliarFollower.interactionAllowed(null, 1_000L));
        assertFalse(FamiliarFollower.interactionAllowed(1_001L, 1_000L));
        assertTrue(FamiliarFollower.interactionAllowed(1_000L, 1_000L));
    }
}
