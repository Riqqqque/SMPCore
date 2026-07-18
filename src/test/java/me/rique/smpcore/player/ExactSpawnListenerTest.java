package me.rique.smpcore.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExactSpawnListenerTest {

    @Test
    void exactSpawnOnlyOverridesFirstJoinLocation() {
        assertTrue(ExactSpawnListener.shouldOverrideLoginLocation(true));
        assertFalse(ExactSpawnListener.shouldOverrideLoginLocation(false));
    }
}
