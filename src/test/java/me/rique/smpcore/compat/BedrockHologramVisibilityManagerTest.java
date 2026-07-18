package me.rique.smpcore.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockHologramVisibilityManagerTest {

    @Test
    void onlyMarksKnownPrivateHolograms() {
        assertTrue(BedrockHologramVisibilityManager.isOcclusionSensitiveKey("smpcore", "salvaging_depot_hologram"));
        assertTrue(BedrockHologramVisibilityManager.isOcclusionSensitiveKey("smpcore", "veil_orb_station_hologram"));
        assertTrue(BedrockHologramVisibilityManager.isOcclusionSensitiveKey("smpcore", "spawner_hologram"));
        assertFalse(BedrockHologramVisibilityManager.isOcclusionSensitiveKey("smpcore", "npc_hologram"));
        assertFalse(BedrockHologramVisibilityManager.isOcclusionSensitiveKey("other", "salvaging_depot_hologram"));
    }

    @Test
    void spawnStationsHaveMoreRangeThanPrivateStations() {
        assertEquals(24.0D, BedrockHologramVisibilityManager.viewDistance(true));
        assertEquals(12.0D, BedrockHologramVisibilityManager.viewDistance(false));
    }
}
