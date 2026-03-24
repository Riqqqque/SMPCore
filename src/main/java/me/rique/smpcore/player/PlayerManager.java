package me.rique.smpcore.player;

import me.rique.smpcore.SMPCore;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player transient state (vanish, god, fly, back location, dragon-egg effect).
 * Persistent data (nickname) is stored in the DB via DatabaseManager.
 */
public final class PlayerManager {

    private final SMPCore plugin;

    private final Set<UUID> godPlayers     = ConcurrentHashMap.newKeySet();
    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> flightPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Location> backLocations = new ConcurrentHashMap<>();

    public PlayerManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    // ── God mode ──────────────────────────────────────────────────────────────

    public boolean toggleGod(Player player) {
        if (godPlayers.remove(player.getUniqueId())) {
            player.setInvulnerable(false);
            return false;
        }
        godPlayers.add(player.getUniqueId());
        player.setInvulnerable(true);
        return true;
    }

    public boolean isGod(UUID uuid) { return godPlayers.contains(uuid); }

    // ── Vanish ────────────────────────────────────────────────────────────────

    public boolean toggleVanish(Player player) {
        if (vanishedPlayers.remove(player.getUniqueId())) {
            showPlayer(player);
            return false; // now visible
        }
        vanishedPlayers.add(player.getUniqueId());
        hidePlayer(player);
        return true; // now vanished
    }

    public boolean setVanished(Player player, boolean vanished) {
        if (player == null) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        if (vanished) {
            if (!vanishedPlayers.add(playerId)) {
                return false;
            }
            hidePlayer(player);
            return true;
        }

        if (!vanishedPlayers.remove(playerId)) {
            return false;
        }
        showPlayer(player);
        return true;
    }

    /** Make sure a newly joined player cannot see vanished staff. */
    public void applyVanishToNewPlayer(Player newPlayer) {
        for (UUID uid : vanishedPlayers) {
            Player vanished = org.bukkit.Bukkit.getPlayer(uid);
            if (vanished == null) continue;
            if (!newPlayer.hasPermission("smpcore.vanish.see")) {
                newPlayer.hidePlayer(plugin, vanished);
            }
        }
    }

    public boolean isVanished(UUID uuid) { return vanishedPlayers.contains(uuid); }

    public boolean toggleFlight(Player player) {
        UUID playerId = player.getUniqueId();
        if (flightPlayers.remove(playerId)) {
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                player.setAllowFlight(false);
            }
            player.setFlying(false);
            return false;
        }

        flightPlayers.add(playerId);
        player.setAllowFlight(true);
        return true;
    }

    public boolean hasFlightEnabled(UUID uuid) {
        return flightPlayers.contains(uuid);
    }

    private void hidePlayer(Player target) {
        for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (online.equals(target)) continue;
            if (!online.hasPermission("smpcore.vanish.see")) {
                online.hidePlayer(plugin, target);
            }
        }
    }

    private void showPlayer(Player target) {
        for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, target);
        }
    }

    // ── /back ─────────────────────────────────────────────────────────────────

    public void saveBackLocation(Player player) {
        if (plugin.getConfigManager().backOnTeleport) {
            backLocations.put(player.getUniqueId(), player.getLocation().clone());
        }
    }

    public void saveDeathLocation(Player player) {
        if (plugin.getConfigManager().backOnDeath) {
            backLocations.put(player.getUniqueId(), player.getLocation().clone());
        }
    }

    public Location getBackLocation(UUID uuid) {
        return backLocations.get(uuid);
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    public void onDisconnect(Player player) {
        vanishedPlayers.remove(player.getUniqueId());
        godPlayers.remove(player.getUniqueId());
        flightPlayers.remove(player.getUniqueId());
        // back location intentionally kept so /back works after relog in future;
        // removed here to keep memory clean for now
        backLocations.remove(player.getUniqueId());
    }
}
