package me.rique.smpcore.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FamiliarMotionTest {
    @Test
    void followSpeedScalesWithDistance() {
        assertEquals(0.42D, FamiliarMotion.movementBlend(1.0D));
        assertEquals(0.58D, FamiliarMotion.movementBlend(2.0D));
        assertEquals(0.78D, FamiliarMotion.movementBlend(7.0D));
    }

    @Test
    void yawAlwaysTakesTheShortestTurn() {
        assertEquals(20.0F, FamiliarMotion.normalizedYawDelta(380.0F));
        assertEquals(-20.0F, FamiliarMotion.normalizedYawDelta(-380.0F));
        assertEquals(180.0F, FamiliarMotion.normalizedYawDelta(-180.0F));
    }

    @Test
    void bobMovesTheModelAndStaysWithinTheFairyRange() {
        double first = FamiliarMotion.bobOffset(0, 0);
        double second = FamiliarMotion.bobOffset(FamiliarMotion.UPDATE_TICKS, 1);

        assertNotEquals(first, second);
        assertTrue(Math.abs(first) <= 0.12D);
        assertTrue(Math.abs(second) <= 0.12D);
    }

    @Test
    void respawningResetsTheSharedMotionClock() {
        FamiliarMotion.State state = new FamiliarMotion.State(30.0F);
        state.advance();
        assertEquals(FamiliarMotion.UPDATE_TICKS, state.ticks());

        state.reset(-45.0F);
        assertEquals(0, state.ticks());
    }
}
