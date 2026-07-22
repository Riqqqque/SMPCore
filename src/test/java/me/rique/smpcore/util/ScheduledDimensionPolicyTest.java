package me.rique.smpcore.util;

import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScheduledDimensionPolicyTest {

    @Test
    void blocksOnlyTravelIntoLockedScheduledDimensions() {
        assertTrue(ScheduledDimensionPolicy.blocksTravel(
            World.Environment.NORMAL,
            World.Environment.NETHER,
            false,
            false
        ));
        assertTrue(ScheduledDimensionPolicy.blocksTravel(
            World.Environment.NORMAL,
            World.Environment.THE_END,
            false,
            false
        ));
        assertFalse(ScheduledDimensionPolicy.blocksTravel(
            World.Environment.NETHER,
            World.Environment.NORMAL,
            false,
            false
        ));
    }

    @Test
    void permitsBypassUnlockedAndSameDimensionTravel() {
        assertFalse(ScheduledDimensionPolicy.blocksTravel(
            World.Environment.NORMAL,
            World.Environment.NETHER,
            true,
            false
        ));
        assertFalse(ScheduledDimensionPolicy.blocksTravel(
            World.Environment.NORMAL,
            World.Environment.NETHER,
            false,
            true
        ));
        assertFalse(ScheduledDimensionPolicy.blocksTravel(
            World.Environment.NETHER,
            World.Environment.NETHER,
            false,
            false
        ));
    }

    @Test
    void handlesMissingEnvironmentDataDefensively() {
        assertFalse(ScheduledDimensionPolicy.blocksTravel(
            null,
            World.Environment.NETHER,
            false,
            false
        ));
        assertFalse(ScheduledDimensionPolicy.blocksTravel(
            World.Environment.NORMAL,
            null,
            false,
            false
        ));
    }
}
