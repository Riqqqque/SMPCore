package me.rique.smpcore.home;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages per-player homes with async DB backing.
 * Homes are loaded lazily and cached for the current session.
 */
public final class HomeManager {

    private final SMPCore plugin;
    private final Map<UUID, Map<String, HomeEntry>> cache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Map<String, HomeEntry>>> loading = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionTokens = new ConcurrentHashMap<>();
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
            Map<String, HomeEntry> homes = cache.get(uuid);
            String normalized = name.toLowerCase(Locale.ROOT);
            boolean exists = homes.containsKey(normalized);
            int max = maxHomes(player);

            if (!exists && homes.size() >= max) {
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
            plugin.getDatabase().saveHome(uuid, entry).whenComplete((ignored, ex) ->
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
                })
            );
        });
    }

    public void deleteHome(Player player, String name, Runnable onSuccess, Runnable onNotFound) {
        deleteHome(player, name, onSuccess, onNotFound, () -> {});
    }

    public void deleteHome(Player player, String name, Runnable onSuccess, Runnable onNotFound, Runnable onFailure) {
        UUID uuid = player.getUniqueId();
        ensureLoaded(player, () -> {
            Map<String, HomeEntry> homes = cache.get(uuid);
            String normalized = name.toLowerCase(Locale.ROOT);
            HomeEntry removed = homes.remove(normalized);
            if (removed != null) {
                long token = sessionToken(uuid);
                plugin.getDatabase().deleteHome(uuid, normalized).whenComplete((ignored, ex) ->
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
                    })
                );
            } else {
                onNotFound.run();
            }
        });
    }

    public void teleportHome(Player player, String name) {
        UUID uuid = player.getUniqueId();
        ensureLoaded(player, () -> {
            String normalized = name.toLowerCase(Locale.ROOT);
            Map<String, HomeEntry> homes = cache.get(uuid);
            HomeEntry entry = homes.get(normalized);
            if (entry == null) {
                player.sendMessage(MessageUtil.error(notFoundMessage(name, normalized)));
                return;
            }

            Location loc = entry.toLocation();
            if (loc == null) {
                player.sendMessage(MessageUtil.error(worldMissingMessage(name, normalized)));
                return;
            }

            plugin.getPlayerManager().saveBackLocation(player);
            player.teleportAsync(loc).thenAccept(success -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (success) {
                        player.sendMessage(MessageUtil.success(teleportedMessage(name, normalized)));
                    } else {
                        player.sendMessage(MessageUtil.error("Teleport failed."));
                    }
                });
            });
        });
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
    }

    private void ensureLoaded(Player player, Runnable action) {
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

    private static String teleportedMessage(String inputName, String normalized) {
        return isDefaultHome(normalized)
            ? "Teleported to your default home."
            : "Teleported to <white>" + inputName + "</white>.";
    }

    private static boolean isDefaultHome(String normalized) {
        return "home".equals(normalized);
    }
}
