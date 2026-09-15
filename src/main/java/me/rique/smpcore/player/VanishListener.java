package me.rique.smpcore.player;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Keeps vanished staff out of player-facing visibility and gameplay traces. */
public final class VanishListener implements Listener {
    private static final Set<String> LIST_COMMANDS = Set.of("list", "minecraft:list", "bukkit:list");

    private final SMPCore plugin;
    private final Set<UUID> pendingOwnedEntityChecks = new HashSet<>();
    private boolean ownedEntityCheckQueued;

    public VanishListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (isVanishedOwnedEntity(event.getEntity())
            || event.getTarget() instanceof Player player && isVanished(player)
            || event.getTarget() != null && isVanishedOwnedEntity(event.getTarget())) {
            event.setCancelled(true);
            event.setTarget(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isVanished(player)) {
            event.setCancelled(true);
            return;
        }
        if (isVanishedOwnedEntity(event.getEntity())) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && isVanishedDamager(byEntity)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (isVanished(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isVanished(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isVanished(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isVanished(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (isVanished(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (isVanished(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isVanished(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isVanished(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExperiencePickup(PlayerPickupExperienceEvent event) {
        if (isVanished(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isVanished(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && isVanished(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        ProjectileSource shooter = event.getEntity().getShooter();
        if (shooter instanceof Player player && isVanished(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (isVanished(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (isVanished(event.getPlayer())) event.message(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (plugin.getPlayerManager().isSpectatorVanished(event.getPlayer().getUniqueId())
            && event.getNewGameMode() != org.bukkit.GameMode.SPECTATOR) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.warn("Use <white>/vanish off</white> before changing game mode."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onListCommand(PlayerCommandPreprocessEvent event) {
        String root = commandRoot(event.getMessage());
        if (!LIST_COMMANDS.contains(root)) return;

        event.setCancelled(true);
        Player viewer = event.getPlayer();
        List<String> visibleNames = Bukkit.getOnlinePlayers().stream()
            .filter(Player::isOnline)
            .filter(target -> viewer.equals(target) || viewer.canSee(target))
            .map(Player::getName)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        String names = visibleNames.isEmpty() ? "none" : String.join(", ", visibleNames);
        viewer.sendMessage(MessageUtil.info(
            "Online <white>" + visibleNames.size() + "/" + Bukkit.getMaxPlayers() + "</white>: <gray>" + names + "</gray>"
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerListPing(PaperServerListPingEvent event) {
        Set<UUID> hidden = plugin.getPlayerManager().vanishedPlayerIds();
        if (hidden.isEmpty()) return;

        int onlineHidden = hidden.size();
        event.setNumPlayers(Math.max(0, event.getNumPlayers() - onlineHidden));
        event.getListedPlayers().removeIf(info -> info.id() != null && hidden.contains(info.id()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityAdded(EntityAddToWorldEvent event) {
        if (!plugin.getPlayerManager().hasVanishedPlayers()) {
            return;
        }
        plugin.getPlayerManager().refreshOwnedEntityVisibility(event.getEntity());
        pendingOwnedEntityChecks.add(event.getEntity().getUniqueId());
        if (ownedEntityCheckQueued) {
            return;
        }
        ownedEntityCheckQueued = true;
        Bukkit.getScheduler().runTask(plugin, this::flushOwnedEntityChecks);
    }

    static String commandRoot(String message) {
        if (message == null) return "";
        String normalized = message.trim();
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        int space = normalized.indexOf(' ');
        if (space >= 0) normalized = normalized.substring(0, space);
        return normalized.toLowerCase(Locale.ROOT);
    }

    private boolean isVanished(Player player) {
        return player != null && plugin.getPlayerManager().isVanished(player.getUniqueId());
    }

    private boolean isVanishedDamager(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) return isVanished(player);
        if (isVanishedOwnedEntity(event.getDamager())) return true;
        if (!(event.getDamager() instanceof Projectile projectile)) return false;
        return projectile.getShooter() instanceof Player player && isVanished(player);
    }

    private boolean isVanishedOwnedEntity(Entity entity) {
        return entity != null && plugin.getPlayerManager().isOwnedByVanishedPlayer(entity);
    }

    private void flushOwnedEntityChecks() {
        ownedEntityCheckQueued = false;
        if (pendingOwnedEntityChecks.isEmpty()) {
            return;
        }
        Set<UUID> pending = Set.copyOf(pendingOwnedEntityChecks);
        pendingOwnedEntityChecks.clear();
        for (UUID entityId : pending) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) {
                plugin.getPlayerManager().refreshOwnedEntityVisibility(entity);
            }
        }
    }
}
