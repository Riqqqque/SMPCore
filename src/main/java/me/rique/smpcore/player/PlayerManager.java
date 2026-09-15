package me.rique.smpcore.player;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.LocationUtil;
import me.rique.smpcore.util.OperatorGameplayMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player transient state (vanish, god, fly, back location, dragon-egg effect).
 * Persistent data (nickname) is stored in the DB via DatabaseManager.
 */
public final class PlayerManager {

    private static final Set<String> TRANSIENT_ENTITY_OWNER_KEYS = Set.of(
        "beast_mount_owner",
        "mayor_pet_owner",
        "miner_pet_owner",
        "tiller_familiar_owner",
        "morrow_familiar_owner",
        "superpower_monarch_owner",
        "ender_sword_dragon_owner",
        "ender_sword_seat_owner",
        "player_name_hologram_owner",
        "telekinesis_projectile_owner",
        "essence_capture_projectile_owner"
    );

    private final SMPCore plugin;
    private final List<NamespacedKey> transientEntityOwnerKeys;

    private final Set<UUID> godPlayers     = ConcurrentHashMap.newKeySet();
    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> spectatorVanishedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, VanishSnapshot> vanishSnapshots = new ConcurrentHashMap<>();
    private final Set<UUID> flightPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Location> backLocations = new ConcurrentHashMap<>();

    public PlayerManager(SMPCore plugin) {
        this.plugin = plugin;
        this.transientEntityOwnerKeys = TRANSIENT_ENTITY_OWNER_KEYS.stream()
            .map(key -> new NamespacedKey(plugin, key))
            .toList();
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
        if (isVanished(player.getUniqueId())) {
            disableVanish(player);
            return false;
        }
        enableStandardVanish(player);
        return true;
    }

    public boolean setVanished(Player player, boolean vanished) {
        if (player == null) {
            return false;
        }

        if (vanished) {
            return enableStandardVanish(player);
        }
        return disableVanish(player);
    }

    public boolean setSpectatorVanished(Player player) {
        if (player == null) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        boolean changed = !spectatorVanishedPlayers.contains(playerId);
        captureVanishSnapshot(player);
        vanishedPlayers.add(playerId);
        spectatorVanishedPlayers.add(playerId);
        applyHiddenPlayerState(player);
        player.closeInventory();
        if (plugin.getBeastwardenManager() != null) {
            plugin.getBeastwardenManager().recallSteed(player);
        }
        suppressOwnedGameplayTraces(player);
        player.setSpectatorTarget(null);
        if (player.getGameMode() != GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SPECTATOR);
        }
        hidePlayer(player);
        refreshVanishVisuals(player);
        return changed;
    }

    /** Make sure a newly joined player cannot see vanished staff. */
    public void applyVanishToNewPlayer(Player newPlayer) {
        refreshVanishForViewer(newPlayer);
        refreshOwnedEntityVisibilityForViewer(newPlayer);
    }

    public void refreshVanishForViewer(Player viewer) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        for (UUID uid : vanishedPlayers) {
            Player vanished = org.bukkit.Bukkit.getPlayer(uid);
            if (vanished == null || vanished.equals(viewer)) continue;
            if (OperatorGameplayMode.allowsPassiveBypass(viewer, "smpcore.vanish.see")) {
                viewer.showPlayer(plugin, vanished);
                if (!viewer.isListed(vanished)) {
                    try {
                        viewer.listPlayer(vanished);
                    } catch (IllegalStateException ignored) {
                        // The next visual sync retries after the join settles.
                    }
                }
            } else {
                viewer.hidePlayer(plugin, vanished);
                if (viewer.isListed(vanished)) {
                    viewer.unlistPlayer(vanished);
                }
            }
        }
    }

    public boolean isVanished(UUID uuid) { return vanishedPlayers.contains(uuid); }

    public Set<UUID> vanishedPlayerIds() {
        return Set.copyOf(vanishedPlayers);
    }

    public boolean hasVanishedPlayers() {
        return !vanishedPlayers.isEmpty();
    }

    public boolean isSpectatorVanished(UUID uuid) {
        return uuid != null && spectatorVanishedPlayers.contains(uuid);
    }

    public boolean isOwnedByVanishedPlayer(Entity entity) {
        UUID ownerId = transientEntityOwner(entity);
        return ownerId != null && isVanished(ownerId);
    }

    public void refreshOwnedEntityVisibility(Entity entity) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        UUID ownerId = transientEntityOwner(entity);
        if (ownerId == null) {
            return;
        }
        boolean hidden = isVanished(ownerId);
        for (Player viewer : org.bukkit.Bukkit.getOnlinePlayers()) {
            applyOwnedEntityVisibility(viewer, entity, ownerId, hidden);
        }
    }

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

    public boolean isFlightAuthorized(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }
        boolean classFlight = plugin.getSuperpowerManager() != null
            && plugin.getSuperpowerManager().shouldRetainFlightAccess(player);
        boolean enchantFlight = plugin.getCustomEnchantListener() != null
            && plugin.getCustomEnchantListener().shouldRetainFlightAccess(player);
        return isFlightAuthorized(
            player.getGameMode(),
            hasFlightEnabled(player.getUniqueId()),
            classFlight,
            enchantFlight
        );
    }

    public boolean reconcileFlightAccess(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        boolean authorized = isFlightAuthorized(player);
        boolean changed = false;
        if (player.getAllowFlight() != authorized) {
            player.setAllowFlight(authorized);
            changed = true;
        }
        if (!authorized && player.isFlying()) {
            player.setFlying(false);
            changed = true;
        }
        return changed;
    }

    static boolean isFlightAuthorized(
        GameMode gameMode,
        boolean staffFlight,
        boolean classFlight,
        boolean enchantFlight
    ) {
        return gameMode == GameMode.CREATIVE
            || gameMode == GameMode.SPECTATOR
            || staffFlight
            || classFlight
            || enchantFlight;
    }

    private void hidePlayer(Player target) {
        for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (online.equals(target)) continue;
            if (!OperatorGameplayMode.allowsPassiveBypass(online, "smpcore.vanish.see")) {
                online.hidePlayer(plugin, target);
                if (online.isListed(target)) {
                    online.unlistPlayer(target);
                }
            }
        }
        refreshOwnedEntityVisibility(target.getUniqueId(), true);
        refreshVanishVisuals(target);
    }

    private void showPlayer(Player target) {
        for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, target);
            if (!online.isListed(target)) {
                try {
                    online.listPlayer(target);
                } catch (IllegalStateException ignored) {
                    // The regular visual sync retries once visibility settles.
                }
            }
        }
        refreshOwnedEntityVisibility(target.getUniqueId(), false);
        refreshVanishVisuals(target);
    }

    private boolean enableStandardVanish(Player player) {
        UUID playerId = player.getUniqueId();
        if (spectatorVanishedPlayers.remove(playerId)) {
            restoreVanishSnapshot(player, vanishSnapshots.get(playerId));
            applyHiddenPlayerState(player);
            hidePlayer(player);
            return true;
        }
        if (vanishedPlayers.contains(playerId)) {
            hidePlayer(player);
            return false;
        }
        captureVanishSnapshot(player);
        vanishedPlayers.add(playerId);
        applyHiddenPlayerState(player);
        player.closeInventory();
        if (plugin.getBeastwardenManager() != null) {
            plugin.getBeastwardenManager().recallSteed(player);
        }
        suppressOwnedGameplayTraces(player);
        hidePlayer(player);
        return true;
    }

    private boolean disableVanish(Player player) {
        UUID playerId = player.getUniqueId();
        boolean changed = vanishedPlayers.remove(playerId);
        spectatorVanishedPlayers.remove(playerId);
        VanishSnapshot snapshot = vanishSnapshots.remove(playerId);
        if (!changed && snapshot == null) {
            return false;
        }
        restoreVanishSnapshot(player, snapshot);
        showPlayer(player);
        return true;
    }

    private void captureVanishSnapshot(Player player) {
        vanishSnapshots.computeIfAbsent(player.getUniqueId(), ignored -> new VanishSnapshot(
            player.getGameMode(),
            player.getLocation().clone(),
            player.isFlying(),
            player.isSilent(),
            player.isCollidable(),
            player.getCanPickupItems(),
            player.getAffectsSpawning()
        ));
    }

    private void applyHiddenPlayerState(Player player) {
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        if (!player.getPassengers().isEmpty()) {
            player.eject();
        }
        player.setSilent(true);
        player.setCollidable(false);
        player.setCanPickupItems(false);
        player.setAffectsSpawning(false);
    }

    private void restoreVanishSnapshot(Player player, VanishSnapshot snapshot) {
        if (snapshot == null) {
            reconcileFlightAccess(player);
            return;
        }

        player.setSpectatorTarget(null);
        if (player.getGameMode() == GameMode.SPECTATOR && snapshot.gameMode() != GameMode.SPECTATOR) {
            Location safeReturn = LocationUtil.findNearestSafeStandingLocation(snapshot.returnLocation(), 2, 4);
            player.teleport(safeReturn == null ? snapshot.returnLocation() : safeReturn);
        }
        if (player.getGameMode() != snapshot.gameMode()) {
            player.setGameMode(snapshot.gameMode());
        }
        player.setSilent(snapshot.silent());
        player.setCollidable(snapshot.collidable());
        player.setCanPickupItems(snapshot.canPickupItems());
        player.setAffectsSpawning(snapshot.affectsSpawning());
        reconcileFlightAccess(player);
        player.setFlying(snapshot.flying() && player.getAllowFlight());
    }

    private void refreshVanishVisuals(Player target) {
        if (plugin.getPlayerVisualListener() != null) {
            plugin.getPlayerVisualListener().refreshPlayerConcealment(target);
            plugin.getPlayerVisualListener().refreshPlayerFinderDefense();
            plugin.getPlayerVisualListener().requestTeamGlowRefresh();
        }
        if (plugin.getTabListManager() != null) {
            plugin.getTabListManager().requestRefresh();
        }
    }

    private void suppressOwnedGameplayTraces(Player player) {
        if (plugin.getSuperpowerManager() != null) {
            plugin.getSuperpowerManager().suppressPlayerTracesForVanish(player);
        }
    }

    private void refreshOwnedEntityVisibility(UUID ownerId, boolean hidden) {
        if (ownerId == null) {
            return;
        }
        for (World world : org.bukkit.Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!ownerId.equals(transientEntityOwner(entity))) {
                    continue;
                }
                for (Player viewer : org.bukkit.Bukkit.getOnlinePlayers()) {
                    applyOwnedEntityVisibility(viewer, entity, ownerId, hidden);
                }
            }
        }
    }

    private void refreshOwnedEntityVisibilityForViewer(Player viewer) {
        if (viewer == null || !viewer.isOnline() || vanishedPlayers.isEmpty()) {
            return;
        }
        for (World world : org.bukkit.Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                UUID ownerId = transientEntityOwner(entity);
                if (ownerId != null && isVanished(ownerId)) {
                    applyOwnedEntityVisibility(viewer, entity, ownerId, true);
                }
            }
        }
    }

    private void applyOwnedEntityVisibility(Player viewer, Entity entity, UUID ownerId, boolean hidden) {
        if (viewer == null || !viewer.isOnline() || entity == null || !entity.isValid()) {
            return;
        }
        boolean canSeeVanish = !viewer.getUniqueId().equals(ownerId)
            && OperatorGameplayMode.allowsPassiveBypass(viewer, "smpcore.vanish.see");
        if (hidden && !canSeeVanish) {
            viewer.hideEntity(plugin, entity);
            return;
        }
        if (entity.isVisibleByDefault()) {
            viewer.showEntity(plugin, entity);
        }
    }

    private UUID transientEntityOwner(Entity entity) {
        return transientEntityOwner(entity, 0);
    }

    private UUID transientEntityOwner(Entity entity, int depth) {
        if (entity == null || entity instanceof Player || depth > 2) {
            return null;
        }
        if (entity instanceof EnderDragonPart part) {
            return transientEntityOwner(part.getParent(), depth + 1);
        }
        if (entity instanceof Tameable tameable && tameable.getOwner() != null) {
            return tameable.getOwner().getUniqueId();
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            UUID shooterOwner = shooter instanceof Player player
                ? player.getUniqueId()
                : transientEntityOwner(shooter, depth + 1);
            if (shooterOwner != null) {
                return shooterOwner;
            }
        }

        PersistentDataContainer data = entity.getPersistentDataContainer();
        for (NamespacedKey ownerKey : transientEntityOwnerKeys) {
            UUID ownerId = parseUuid(data.get(ownerKey, PersistentDataType.STRING));
            if (ownerId != null) {
                return ownerId;
            }
        }
        return null;
    }

    static boolean isTransientEntityOwnerKey(String key) {
        return key != null && TRANSIENT_ENTITY_OWNER_KEYS.contains(key);
    }

    static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // ── /back ─────────────────────────────────────────────────────────────────

    public void saveBackLocation(Player player) {
        if (player == null) return;
        saveBackLocation(player.getUniqueId(), player.getLocation());
    }

    public void saveBackLocation(UUID playerId, Location location) {
        if (plugin.getConfigManager().backOnTeleport) {
            rememberSafeBackLocation(playerId, location, 2, 4);
        }
    }

    public void saveDeathLocation(Player player) {
        if (plugin.getConfigManager().backOnDeath) {
            rememberSafeBackLocation(player.getUniqueId(), player.getLocation(), 6, 8);
        }
    }

    public Location getBackLocation(UUID uuid) {
        return backLocations.get(uuid);
    }

    private void rememberSafeBackLocation(UUID playerId, Location location, int horizontalRadius, int verticalRadius) {
        Location safe = LocationUtil.findNearestSafeStandingLocation(location, horizontalRadius, verticalRadius);
        if (safe == null) {
            return;
        }
        backLocations.put(playerId, safe.clone());
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    public void onDisconnect(Player player) {
        UUID playerId = player.getUniqueId();
        if (vanishedPlayers.contains(playerId)) {
            refreshOwnedEntityVisibility(playerId, false);
        }
        vanishedPlayers.remove(playerId);
        spectatorVanishedPlayers.remove(playerId);
        restoreVanishSnapshot(player, vanishSnapshots.remove(playerId));
        if (godPlayers.remove(playerId)) {
            player.setInvulnerable(false);
        }
        if (flightPlayers.remove(playerId)
            && player.getGameMode() != GameMode.CREATIVE
            && player.getGameMode() != GameMode.SPECTATOR) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        // back location intentionally kept so /back works after relog in future;
        // removed here to keep memory clean for now
        backLocations.remove(playerId);
    }

    public void shutdown() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (godPlayers.remove(playerId)) {
                player.setInvulnerable(false);
            }
            if (flightPlayers.remove(playerId)
                && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
            if (vanishedPlayers.remove(playerId)) {
                spectatorVanishedPlayers.remove(playerId);
                restoreVanishSnapshot(player, vanishSnapshots.remove(playerId));
                showPlayer(player);
            }
        }
        godPlayers.clear();
        flightPlayers.clear();
        vanishedPlayers.clear();
        spectatorVanishedPlayers.clear();
        vanishSnapshots.clear();
        backLocations.clear();
    }

    private record VanishSnapshot(
        GameMode gameMode,
        Location returnLocation,
        boolean flying,
        boolean silent,
        boolean collidable,
        boolean canPickupItems,
        boolean affectsSpawning
    ) {
        private VanishSnapshot {
            returnLocation = returnLocation == null ? null : returnLocation.clone();
        }
    }
}
