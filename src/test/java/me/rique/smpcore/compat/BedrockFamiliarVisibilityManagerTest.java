package me.rique.smpcore.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockFamiliarVisibilityManagerTest {

    @Test
    void viewersReceiveExactlyOneFamiliarBody() {
        assertTrue(BedrockFamiliarVisibilityManager.shouldShow(false, false));
        assertFalse(BedrockFamiliarVisibilityManager.shouldShow(false, true));
        assertFalse(BedrockFamiliarVisibilityManager.shouldShow(true, false));
        assertTrue(BedrockFamiliarVisibilityManager.shouldShow(true, true));
    }
}
