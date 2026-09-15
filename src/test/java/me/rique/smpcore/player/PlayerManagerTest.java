package me.rique.smpcore.player;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerManagerTest {

    @Test
    void survivalFlightRequiresAnActiveOwnedSource() {
        assertFalse(PlayerManager.isFlightAuthorized(GameMode.SURVIVAL, false, false, false));
        assertFalse(PlayerManager.isFlightAuthorized(GameMode.ADVENTURE, false, false, false));
        assertTrue(PlayerManager.isFlightAuthorized(GameMode.SURVIVAL, true, false, false));
        assertTrue(PlayerManager.isFlightAuthorized(GameMode.SURVIVAL, false, true, false));
        assertTrue(PlayerManager.isFlightAuthorized(GameMode.SURVIVAL, false, false, true));
    }

    @Test
    void nativeFlyingModesRemainAuthorized() {
        assertTrue(PlayerManager.isFlightAuthorized(GameMode.CREATIVE, false, false, false));
        assertTrue(PlayerManager.isFlightAuthorized(GameMode.SPECTATOR, false, false, false));
    }

    @Test
    void transientOwnerKeysCoverPetsSummonsAndPlayerVisuals() {
        assertTrue(PlayerManager.isTransientEntityOwnerKey("mayor_pet_owner"));
        assertTrue(PlayerManager.isTransientEntityOwnerKey("miner_pet_owner"));
        assertTrue(PlayerManager.isTransientEntityOwnerKey("beast_mount_owner"));
        assertTrue(PlayerManager.isTransientEntityOwnerKey("superpower_monarch_owner"));
        assertTrue(PlayerManager.isTransientEntityOwnerKey("ender_sword_dragon_owner"));
        assertTrue(PlayerManager.isTransientEntityOwnerKey("player_name_hologram_owner"));
        assertFalse(PlayerManager.isTransientEntityOwnerKey("boss_souvenir_owner"));
        assertFalse(PlayerManager.isTransientEntityOwnerKey(null));
    }

    @Test
    void ownerIdsRejectMissingOrMalformedData() {
        UUID ownerId = UUID.randomUUID();
        assertEquals(ownerId, PlayerManager.parseUuid(ownerId.toString()));
        assertNull(PlayerManager.parseUuid(""));
        assertNull(PlayerManager.parseUuid("not-a-uuid"));
        assertNull(PlayerManager.parseUuid(null));
    }
}
