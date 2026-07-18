package me.rique.smpcore.compat;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BedrockHologramVisibilityManager implements Listener {

    private static final double SCAN_RANGE_BLOCKS = 32.0D;
    private static final double SPAWN_VIEW_RANGE_BLOCKS = 24.0D;
    private static final double PRIVATE_VIEW_RANGE_BLOCKS = 12.0D;
    private static final double HIDDEN_RETENTION_RANGE_SQUARED = (SCAN_RANGE_BLOCKS + 8.0D) * (SCAN_RANGE_BLOCKS + 8.0D);
    private static final long VISIBILITY_INTERVAL_TICKS = 10L;
    private static final Set<String> OCCLUSION_SENSITIVE_KEYS = Set.of(
        "agricultural_pylon_hologram",
        "awakening_table_hologram",
        "boss_loot_hologram",
        "corruption_hologram",
        "mythic_forge_hologram",
        "rare_drop_hologram",
        "reward_lantern_hologram",
        "salvaging_depot_hologram",
        "spawner_hologram",
        "veil_orb_station_hologram",
        "xp_lectern_hologram"
    );

    private final SMPCore plugin;
    private final String namespace;
    private final Map<UUID, Set<UUID>> hiddenByPlayer = new ConcurrentHashMap<>();
    private BukkitTask visibilityTask;

    public BedrockHologramVisibilityManager(SMPCore plugin) {
        this.plugin = plugin;
        this.namespace = plugin.getName().toLowerCase(Locale.ROOT);
    }

    public void start() {
        if (visibilityTask != null) {
            visibilityTask.cancel();
        }
        visibilityTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            () -> updateVisibility(),
            20L,
            VISIBILITY_INTERVAL_TICKS
        );
    }

    public void shutdown() {
        if (visibilityTask != null) {
            visibilityTask.cancel();
            visibilityTask = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            restoreHiddenHolograms(player);
        }
        hiddenByPlayer.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hiddenByPlayer.remove(event.getPlayer().getUniqueId());
    }

    private void updateVisibility() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!BedrockCompat.isBedrockPlayer(player)) {
                restoreHiddenHolograms(player);
                continue;
            }
            updateVisibility(player);
        }
    }

    private void updateVisibility(Player player) {
        Set<UUID> hidden = hiddenByPlayer.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
        releaseDistantHiddenHolograms(player, hidden);

        for (Entity entity : player.getNearbyEntities(SCAN_RANGE_BLOCKS, SCAN_RANGE_BLOCKS, SCAN_RANGE_BLOCKS)) {
            if (!(entity instanceof TextDisplay display) || !isOcclusionSensitive(display)) {
                continue;
            }
            boolean inSpawn = plugin.getSpawnProtectionListener() != null
                && plugin.getSpawnProtectionListener().isProtected(display.getLocation().clone().subtract(0.0D, 1.0D, 0.0D));
            double maxDistance = viewDistance(inSpawn);
            boolean visible = player.getWorld().equals(display.getWorld())
                && player.getLocation().distanceSquared(display.getLocation()) <= maxDistance * maxDistance
                && hasClearSight(player, display);
            if (visible) {
                if (hidden.remove(display.getUniqueId())) {
                    player.showEntity(plugin, display);
                }
            } else if (hidden.add(display.getUniqueId())) {
                player.hideEntity(plugin, display);
            }
        }

        if (hidden.isEmpty()) {
            hiddenByPlayer.remove(player.getUniqueId(), hidden);
        }
    }

    private void releaseDistantHiddenHolograms(Player player, Set<UUID> hidden) {
        Location playerLocation = player.getLocation();
        for (UUID entityId : new ArrayList<>(hidden)) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity == null) {
                hidden.remove(entityId);
                continue;
            }
            if (entity.getWorld() == player.getWorld()
                && entity.getLocation().distanceSquared(playerLocation) <= HIDDEN_RETENTION_RANGE_SQUARED) {
                continue;
            }
            player.showEntity(plugin, entity);
            hidden.remove(entityId);
        }
    }

    private boolean hasClearSight(Player player, TextDisplay display) {
        Location eye = player.getEyeLocation();
        Location target = display.getLocation().clone().add(0.0D, 0.1D, 0.0D);
        World world = eye.getWorld();
        if (world == null || !world.equals(target.getWorld())) {
            return false;
        }
        Vector direction = target.toVector().subtract(eye.toVector());
        double distance = direction.length();
        if (distance < 0.25D) {
            return true;
        }
        RayTraceResult hit = world.rayTraceBlocks(
            eye,
            direction.multiply(1.0D / distance),
            distance,
            FluidCollisionMode.NEVER,
            true
        );
        return hit == null || hit.getHitPosition().distanceSquared(target.toVector()) <= 0.36D;
    }

    private boolean isOcclusionSensitive(TextDisplay display) {
        for (NamespacedKey key : display.getPersistentDataContainer().getKeys()) {
            if (isOcclusionSensitiveKey(key.getNamespace(), key.getKey()) && namespace.equals(key.getNamespace())) {
                return true;
            }
        }
        return false;
    }

    private void restoreHiddenHolograms(Player player) {
        Set<UUID> hidden = hiddenByPlayer.remove(player.getUniqueId());
        if (hidden == null) {
            return;
        }
        for (UUID entityId : new ArrayList<>(hidden)) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) {
                player.showEntity(plugin, entity);
            }
        }
    }

    static boolean isOcclusionSensitiveKey(String namespace, String key) {
        return "smpcore".equals(namespace) && OCCLUSION_SENSITIVE_KEYS.contains(key);
    }

    static double viewDistance(boolean protectedSpawn) {
        return protectedSpawn ? SPAWN_VIEW_RANGE_BLOCKS : PRIVATE_VIEW_RANGE_BLOCKS;
    }
}
