package me.rique.smpcore.warp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarpManagerTest {

    @Test
    void namesBecomeLowercaseCommands() {
        assertEquals("tavern", WarpManager.normalizeName(" Tavern "));
        assertEquals("market-square", WarpManager.normalizeName("/Market-Square"));
    }

    @Test
    void commandNamesStayShortAndSafe() {
        assertTrue(WarpManager.isValidName("tavern"));
        assertTrue(WarpManager.isValidName("market_square-2"));
        assertFalse(WarpManager.isValidName("two words"));
        assertFalse(WarpManager.isValidName("_hidden"));
        assertFalse(WarpManager.isValidName("this-warp-name-is-far-too-long"));
    }

    @Test
    void managementAndPluginCommandsCannotBeShadowed() {
        assertTrue(WarpManager.isReservedName("create"));
        assertTrue(WarpManager.isReservedName("warp"));
        assertTrue(WarpManager.isReservedName("menu"));
        assertFalse(WarpManager.isReservedName("tavern"));
    }

    @Test
    void crossWorldTeleportOnlyRetriesARecoverableRejection() {
        assertTrue(WarpManager.shouldRetryCrossWorldTeleport(true, true, false, false, true));
        assertFalse(WarpManager.shouldRetryCrossWorldTeleport(false, true, false, false, true));
        assertFalse(WarpManager.shouldRetryCrossWorldTeleport(true, false, false, false, true));
        assertFalse(WarpManager.shouldRetryCrossWorldTeleport(true, true, true, false, true));
        assertFalse(WarpManager.shouldRetryCrossWorldTeleport(true, true, false, true, true));
        assertFalse(WarpManager.shouldRetryCrossWorldTeleport(true, true, false, false, false));
    }
}
