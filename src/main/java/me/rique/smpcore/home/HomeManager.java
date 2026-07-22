package me.rique.smpcore.home;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.LocationUtil;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.util.ScheduledDimensionPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages per-player homes with async DB backing.
 * Homes are loaded lazily and cached for the current session.
 */
public final class HomeManager {

    private static final int HOME_SAFE_HORIZONTAL_RADIUS = 5;
    private static final int HOME_SAFE_VERTICAL_RADIUS = 8;

    private final SMPCore plugin;
    private final Map<UUID, Map<String, HomeEntry>> cache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Map<String, HomeEntry>>> loading = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionTokens = new ConcurrentHashMap<>();
    private final Set<UUID> mutationsInProgress = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingTeleports = ConcurrentHashMap.newKeySet();
    private final AtomicLong sessionSequence = new AtomicLong();

    public HomeManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void setHome(Player player, String name, Location loc) {
        setHome(player, name, loc, () -> {}, () -> {}, () -> {});
    }

    public void setHome(Player player, String name, Location loc, Runnable onSuccess, Runnable onLimitReached) {
        setHome(player, name, loc, onSuccess, onLimitReached, () -> {});
    }

    public void setHome(Player player, String name, Location loc, Runnable onSuccess, Runnable onLimitReached, Runnable onFailure) {
        UUID uuid = player.getUniqueId();
        ensureLoaded(player, () -> {
            if (!mutationsInProgress.add(uuid)) {
                onFailure.run();
                return;
            }
            Map<String, HomeEntry> homes = cache.get(uuid);
            String normalized = name.toLowerCase(Locale.ROOT);
            boolean exists = homes.containsKey(normalized);
            int max = maxHomes(player);

            if (!exists && homes.size() >= max) {
                mutationsInProgress.remove(uuid);
                onLimitReached.run();
                return;
            }

            HomeEntry entry = new HomeEntry(
                normalized,
                loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch()
            );
            long token = sessionToken(uuid);
            HomeEntry previous = homes.put(entry.name(), entry);
            plugin.getDatabase().saveHome(uuid, entry).whenComplete((ignored, ex) -> {
                mutationsInProgress.remove(uuid);
                runForActiveCache(uuid, token, currentHomes -> {
                    HomeEntry current = currentHomes.get(entry.name());
                    if (ex == null) {
                        if (entry.equals(current)) {
                            onSuccess.run();
                        }
                        return;
                    }

                    if (!entry.equals(current)) return;

                    if (previous == null) {
                        currentHomes.remove(entry.name());
                    } else {
                        currentHomes.put(entry.name(), previous);
                    }
                    onFailure.run();
                });
            });
        });
    }

    public void deleteHome(Player player, String name, Runnable onSuccess, Runnable onNotFound) {
        deleteHome(player, name, onSuccess, onNotFound, () -> {});
    }

    public void deleteHome(Player player, String name, Runnable onSuccess, Runnable onNotFound, Runnable onFailure) {
        UUID uuid = player.getUniqueId();
        ensureLoaded(player, () -> {
            if (!mutationsInProgress.add(uuid)) {
                onFailure.run();
                return;
            }
            Map<String, HomeEntry> homes = cache.get(uuid);
            String normalized = name.toLowerCase(Locale.ROOT);
            HomeEntry removed = homes.remove(normalized);
            if (removed != null) {
                long token = sessionToken(uuid);
                plugin.getDatabase().deleteHome(uuid, normalized).whenComplete((ignored, ex) -> {
                    mutationsInProgress.remove(uuid);
                    runForActiveCache(uuid, token, currentHomes -> {
                        if (ex == null) {
                            if (!currentHomes.containsKey(normalized)) {
                                onSuccess.run();
                            }
                            return;
                        }

                        if (currentHomes.containsKey(normalized)) return;

                        currentHomes.put(normalized, removed);
                        onFailure.run();
                    });
                });
            } else {
                mutationsInProgress.remove(uuid);
                onNotFound.run();
            }
        });
    }

    public void teleportHome(Player player, String name) {
        if (isInPlayerCombat(player)) return;
        UUID uuid = player.getUniqueId();
        if (!pendingTeleports.add(uuid)) {
            player.sendMessage(MessageUtil.warn("Your last home teleport is still loading."));
            return;
        }
        ensureLoaded(
            player,
            () -> beginHomeTeleport(player, uuid, name),
            () -> pendingTeleports.remove(uuid)
        );
    }

    private void beginHomeTeleport(Player player, UUID playerId, String inputName) {
        if (!player.isOnline() || isInPlayerCombat(player)) {
            pendingTeleports.remove(playerId);
            return;
        }
        String initialRestriction = activeTeleportRestriction(player);
        if (initialRestriction != null) {
            pendingTeleports.remove(playerId);
            player.sendMessage(MessageUtil.warn(initialRestriction));
            return;
        }

        String normalized = inputName.toLowerCase(Locale.ROOT);
        Map<String, HomeEntry> homes = cache.get(playerId);
        HomeEntry entry = homes == null ? null : homes.get(normalized);
        if (entry == null) {
            pendingTeleports.remove(playerId);
            player.sendMessage(MessageUtil.error(notFoundMessage(inputName, normalized)));
            return;
        }

        Location destination = entry.toLocation();
        if (destination == null || destination.getWorld() == null) {
            pendingTeleports.remove(playerId);
            player.sendMessage(MessageUtil.error(worldMissingMessage(inputName, normalized)));
            return;
        }
        if (isScheduledDimensionLocked(player, destination.getWorld())) {
            pendingTeleports.remove(playerId);
            return;
        }

        World world = destination.getWorld();
        if (!canBypassHomeBorder(player) && !world.getWorldBorder().isInside(destination)) {
            pendingTeleports.remove(playerId);
            player.sendMessage(MessageUtil.warn("That home is outside the current world border."));
            return;
        }

        long token = sessionToken(playerId);
        CompletableFuture<Void> chunkLoad;
        try {
            chunkLoad = loadSafetyChunks(world, destination);
        } catch (RuntimeException ex) {
            pendingTeleports.remove(playerId);
            player.sendMessage(MessageUtil.error("That home's chunk could not be loaded. Try again shortly."));
            plugin.getLogger().warning("Could not start home chunk load for " + player.getName() + ": " + ex.getMessage());
            return;
        }
        chunkLoad.whenComplete((ignored, loadError) -> runSync(() -> {
            if (!player.isOnline() || !sameSession(playerId, token)) {
                pendingTeleports.remove(playerId);
                return;
            }
            Map<String, HomeEntry> currentHomes = cache.get(playerId);
            if (currentHomes == null || !entry.equals(currentHomes.get(normalized))) {
                pendingTeleports.remove(playerId);
                player.sendMessage(MessageUtil.warn("That home changed while it was loading. Try again."));
                return;
            }
            if (loadError != null) {
                pendingTeleports.remove(playerId);
                player.sendMessage(MessageUtil.error("That home's chunk could not be loaded. Try again shortly."));
                return;
            }
            if (isInPlayerCombat(player)) {
                pendingTeleports.remove(playerId);
                return;
            }
            String restriction = activeTeleportRestriction(player);
            if (restriction != null) {
                pendingTeleports.remove(playerId);
                player.sendMessage(MessageUtil.warn(restriction));
                return;
            }
            if (isScheduledDimensionLocked(player, world)) {
                pendingTeleports.remove(playerId);
                return;
            }

            Location safe = LocationUtil.findNearestSafeStandingLocation(
                destination,
                HOME_SAFE_HORIZONTAL_RADIUS,
                HOME_SAFE_VERTICAL_RADIUS
            );
            if (safe == null) {
                pendingTeleports.remove(playerId);
                player.sendMessage(MessageUtil.error(unsafeMessage(inputName, normalized)));
                return;
            }
            if (!canBypassHomeBorder(player) && !world.getWorldBorder().isInside(safe)) {
                pendingTeleports.remove(playerId);
                player.sendMessage(MessageUtil.warn("That home's safe landing spot is outside the current world border."));
                return;
            }

            Location returnLocation = player.getLocation().clone();
            beginValidatedTeleport(player, playerId, safe, returnLocation, inputName, normalized, true);
        }));
    }

    private void beginValidatedTeleport(
        Player player,
        UUID playerId,
        Location destination,
        Location returnLocation,
        String inputName,
        String normalized,
        boolean retryAvailable
    ) {
        if (!player.isOnline()) {
            pendingTeleports.remove(playerId);
            return;
        }
        if (isInPlayerCombat(player)) {
            pendingTeleports.remove(playerId);
            return;
        }
        String restriction = activeTeleportRestriction(player);
        if (restriction != null) {
            pendingTeleports.remove(playerId);
            player.sendMessage(MessageUtil.warn(restriction));
            return;
        }

        boolean crossWorld = !player.getWorld().getUID().equals(destination.getWorld().getUID());
        if (crossWorld && !prepareCrossWorldTeleport(player)) {
            pendingTeleports.remove(playerId);
            player.sendMessage(MessageUtil.warn("Leave your seat or mount, then try /home again."));
            return;
        }

        player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)
            .whenComplete((success, teleportError) -> runSync(() -> {
                if (!player.isOnline()) {
                    pendingTeleports.remove(playerId);
                    return;
                }
                if (teleportError == null && Boolean.TRUE.equals(success)) {
                    pendingTeleports.remove(playerId);
                    plugin.getPlayerManager().saveBackLocation(playerId, returnLocation);
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.65F, 1.15F);
                    player.sendMessage(MessageUtil.success(teleportedMessage(inputName, normalized)));
                    return;
                }

                boolean combatBlocked = isCurrentlyInPlayerCombat(player);
                String currentRestriction = activeTeleportRestriction(player);
                if (shouldRetryCrossWorldTeleport(
                    crossWorld,
                    player.isOnline(),
                    combatBlocked,
                    currentRestriction != null,
                    retryAvailable
                )) {
                    Bukkit.getScheduler().runTaskLater(
                        plugin,
                        () -> beginValidatedTeleport(
                            player,
                            playerId,
                            destination,
                            returnLocation,
                            inputName,
                            normalized,
                            false
                        ),
                        1L
                    );
                    return;
                }

                pendingTeleports.remove(playerId);
                if (combatBlocked) {
                    player.sendMessage(MessageUtil.warn("You cannot teleport while in combat."));
                } else if (currentRestriction != null) {
                    player.sendMessage(MessageUtil.warn(currentRestriction));
                } else {
                    player.sendMessage(MessageUtil.error("Home teleport failed. Dismount and try again."));
                }
                String errorName = teleportError == null ? "none" : teleportError.getClass().getSimpleName();
                plugin.getLogger().warning(
                    "Home teleport rejected for " + player.getName()
                        + " from=" + player.getWorld().getName()
                        + " to=" + destination.getWorld().getName()
                        + " crossWorld=" + crossWorld
                        + " vehicle=" + player.isInsideVehicle()
                        + " passengers=" + player.getPassengers().size()
                        + " error=" + errorName
                );
            }));
    }

    private static boolean prepareCrossWorldTeleport(Player player) {
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        if (!player.getPassengers().isEmpty()) {
            player.eject();
        }
        return !player.isInsideVehicle() && player.getPassengers().isEmpty();
    }

    private String activeTeleportRestriction(Player player) {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().blocksExternalTeleport(player)) {
            return "You cannot use /home during a duel.";
        }
        if ((plugin.getBossManager() != null && plugin.getBossManager().isActiveBossFight(player))
            || (plugin.getBossDungeonManager() != null && plugin.getBossDungeonManager().blocksExternalTeleport(player))) {
            return "You cannot leave an active boss fight.";
        }
        return null;
    }

    static boolean shouldRetryCrossWorldTeleport(
        boolean crossWorld,
        boolean online,
        boolean combatBlocked,
        boolean arenaBlocked,
        boolean retryAvailable
    ) {
        return crossWorld && online && !combatBlocked && !arenaBlocked && retryAvailable;
    }

    private boolean isScheduledDimensionLocked(Player player, World destination) {
        World.Environment from = player.getWorld().getEnvironment();
        World.Environment to = destination.getEnvironment();
        if (to != World.Environment.NETHER && to != World.Environment.THE_END) {
            return false;
        }
        boolean bypass = player.hasPermission("smpcore.startsmp.bypass-dimension-lock");
        boolean unlocked = plugin.getSmpStartManager() == null || plugin.getSmpStartManager().isDimensionUnlocked(to);
        if (!ScheduledDimensionPolicy.blocksTravel(from, to, bypass, unlocked)) {
            return false;
        }
        String dimension = to == World.Environment.NETHER ? "Nether" : "End";
        player.sendMessage(MessageUtil.warn("The <white>" + dimension + "</white> is still locked."));
        return true;
    }

    private boolean canBypassHomeBorder(Player player) {
        return player.hasPermission("smpcore.home.bypass-border");
    }

    private static CompletableFuture<Void> loadSafetyChunks(World world, Location destination) {
        int minChunkX = (destination.getBlockX() - HOME_SAFE_HORIZONTAL_RADIUS) >> 4;
        int maxChunkX = (destination.getBlockX() + HOME_SAFE_HORIZONTAL_RADIUS) >> 4;
        int minChunkZ = (destination.getBlockZ() - HOME_SAFE_HORIZONTAL_RADIUS) >> 4;
        int maxChunkZ = (destination.getBlockZ() + HOME_SAFE_HORIZONTAL_RADIUS) >> 4;
        CompletableFuture<?>[] loads = new CompletableFuture<?>[(maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1)];
        int index = 0;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                loads[index++] = world.getChunkAtAsync(chunkX, chunkZ, false);
            }
        }
        return CompletableFuture.allOf(loads);
    }

    private boolean isInPlayerCombat(Player player) {
        if (!isCurrentlyInPlayerCombat(player)) {
            return false;
        }
        player.sendMessage(MessageUtil.warn("You cannot teleport while in combat."));
        return true;
    }

    private boolean isCurrentlyInPlayerCombat(Player player) {
        return plugin.getCombatLogListener() != null && plugin.getCombatLogListener().isInPlayerCombat(player);
    }

    public void listHomes(Player player) {
        UUID uuid = player.getUniqueId();
        ensureLoaded(player, () -> {
            Map<String, HomeEntry> homes = cache.get(uuid);
            if (homes.isEmpty()) {
                player.sendMessage(MessageUtil.info("You have no homes set."));
                return;
            }
            player.sendMessage(MessageUtil.info("Your homes: <white>" + String.join(", ", homes.keySet()) + "</white>"));
        });
    }

    /** Returns how many homes this player has (cache-only). */
    public int homeCount(UUID uuid) {
        Map<String, HomeEntry> homes = cache.get(uuid);
        return homes == null ? 0 : homes.size();
    }

    /** Returns cached home names for command suggestions. */
    public List<String> cachedHomeNames(UUID uuid) {
        Map<String, HomeEntry> homes = cache.get(uuid);
        if (homes == null || homes.isEmpty()) {
            return List.of();
        }
        return List.copyOf(homes.keySet());
    }

    /** Max homes allowed for this player based on permissions. */
    public int maxHomes(Player player) {
        return player.hasPermission("smpcore.home.multiple")
            ? plugin.getConfigManager().homeMultipleMax
            : plugin.getConfigManager().homeDefaultMax;
    }

    /** Pre-load homes for a player (called on join). */
    public void preload(UUID uuid) {
        sessionToken(uuid);
        loadHomesIntoCache(uuid);
    }

    /** Remove cached homes when player leaves. */
    public void unload(UUID uuid) {
        cache.remove(uuid);
        loading.remove(uuid);
        sessionTokens.remove(uuid);
        pendingTeleports.remove(uuid);
    }

    public void shutdown() {
        cache.clear();
        loading.clear();
        sessionTokens.clear();
        mutationsInProgress.clear();
        pendingTeleports.clear();
    }

    private void ensureLoaded(Player player, Runnable action) {
        ensureLoaded(player, action, () -> {});
    }

    private void ensureLoaded(Player player, Runnable action, Runnable onFailure) {
        UUID uuid = player.getUniqueId();
        long token = sessionToken(uuid);
        if (cache.containsKey(uuid)) {
            runForActiveSession(uuid, token, action);
            return;
        }
        loadHomesIntoCache(uuid).whenComplete((ignored, ex) -> {
            if (ex != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (sameSession(uuid, token) && player.isOnline()) {
                        player.sendMessage(MessageUtil.error("Your homes could not be loaded right now. Try again in a moment."));
                        onFailure.run();
                    }
                });
                return;
            }
            if (!sameSession(uuid, token) || !cache.containsKey(uuid)) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (sameSession(uuid, token) && cache.containsKey(uuid)) {
                    action.run();
                }
            });
        });
    }

    private void runSync(Runnable action) {
        if (!plugin.isEnabled()) return;
        if (Bukkit.isPrimaryThread()) {
            action.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, action);
    }

    private CompletableFuture<Map<String, HomeEntry>> loadHomesIntoCache(UUID uuid) {
        Map<String, HomeEntry> existing = cache.get(uuid);
        if (existing != null) return CompletableFuture.completedFuture(existing);
        CompletableFuture<Map<String, HomeEntry>> inFlight = loading.get(uuid);
        if (inFlight != null) return inFlight;

        long token = sessionToken(uuid);
        CompletableFuture<Map<String, HomeEntry>> future = new CompletableFuture<>();
        CompletableFuture<Map<String, HomeEntry>> previous = loading.putIfAbsent(uuid, future);
        if (previous != null) return previous;

        plugin.getDatabase().loadHomes(uuid)
            .thenApply(list -> {
                Map<String, HomeEntry> loaded = new LinkedHashMap<>();
                for (HomeEntry entry : list) {
                    loaded.put(entry.name().toLowerCase(Locale.ROOT), entry);
                }
                return loaded;
            })
            .whenComplete((loaded, ex) -> {
                try {
                    if (ex != null) {
                        future.completeExceptionally(ex);
                        return;
                    }

                    if (sameSession(uuid, token) && loading.get(uuid) == future) {
                        cache.put(uuid, loaded);
                        future.complete(cache.get(uuid));
                        return;
                    }

                    future.complete(loaded);
                } finally {
                    loading.remove(uuid, future);
                }
            });

        return future;
    }

    private long sessionToken(UUID uuid) {
        return sessionTokens.computeIfAbsent(uuid, ignored -> sessionSequence.incrementAndGet());
    }

    private boolean sameSession(UUID uuid, long token) {
        Long current = sessionTokens.get(uuid);
        return current != null && current == token;
    }

    private void runForActiveSession(UUID uuid, long token, Runnable action) {
        if (Bukkit.isPrimaryThread()) {
            if (sameSession(uuid, token)) {
                action.run();
            }
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (sameSession(uuid, token)) {
                action.run();
            }
        });
    }

    private void runForActiveCache(UUID uuid, long token, java.util.function.Consumer<Map<String, HomeEntry>> action) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!sameSession(uuid, token)) return;
            Map<String, HomeEntry> homes = cache.get(uuid);
            if (homes == null) return;
            action.accept(homes);
        });
    }

    private static String notFoundMessage(String inputName, String normalized) {
        return isDefaultHome(normalized)
            ? "No default home is set."
            : "No home named <white>" + inputName + "</white>.";
    }

    private static String worldMissingMessage(String inputName, String normalized) {
        return isDefaultHome(normalized)
            ? "World for your default home is not loaded."
            : "World for <white>" + inputName + "</white> is not loaded.";
    }

    private static String unsafeMessage(String inputName, String normalized) {
        return isDefaultHome(normalized)
            ? "Your default home is not safe right now."
            : "Home <white>" + inputName + "</white> is not safe right now.";
    }

    private static String teleportedMessage(String inputName, String normalized) {
        return isDefaultHome(normalized)
            ? "Teleported to your default home."
            : "Teleported to <white>" + inputName + "</white>.";
    }

    private static boolean isDefaultHome(String normalized) {
        return "home".equals(normalized);
    }
}
