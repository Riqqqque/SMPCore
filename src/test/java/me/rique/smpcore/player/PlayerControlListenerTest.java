package me.rique.smpcore.player;

import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerControlListenerTest {

    @Test
    void nativeSpectatorTargetTeleportsAreBlockedWithoutAffectingNormalTeleports() {
        assertTrue(PlayerControlListener.isNativeSpectatorTeleport(PlayerTeleportEvent.TeleportCause.SPECTATE));
        assertFalse(PlayerControlListener.isNativeSpectatorTeleport(PlayerTeleportEvent.TeleportCause.COMMAND));
        assertFalse(PlayerControlListener.isNativeSpectatorTeleport(PlayerTeleportEvent.TeleportCause.PLUGIN));
        assertFalse(PlayerControlListener.isNativeSpectatorTeleport(PlayerTeleportEvent.TeleportCause.ENDER_PEARL));
        assertFalse(PlayerControlListener.isNativeSpectatorTeleport(null));
    }
}
