package me.rique.smpcore.player;

import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerControlListener implements Listener {

    private final SMPCore plugin;
    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();

    public PlayerControlListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    public boolean toggleFrozen(Player player) {
        return setFrozen(player, !isFrozen(player.getUniqueId()));
    }

    public boolean setFrozen(Player player, boolean frozen) {
        if (player == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        boolean changed = frozen ? frozenPlayers.add(playerId) : frozenPlayers.remove(playerId);
        if (frozen) {
            player.setVelocity(player.getVelocity().zero());
            player.setSprinting(false);
            player.setGliding(false);
            player.sendMessage(MessageUtil.warn("You have been frozen by staff."));
        } else {
            player.sendMessage(MessageUtil.success("You are no longer frozen."));
        }
        return changed;
    }

    public boolean isFrozen(UUID playerId) {
        return playerId != null && frozenPlayers.contains(playerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!isFrozen(event.getPlayer().getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (!from.getWorld().equals(to.getWorld()) || movedPosition(from, to)) {
            Location locked = from.clone();
            locked.setYaw(to.getYaw());
            locked.setPitch(to.getPitch());
            event.setTo(locked);
            event.getPlayer().setVelocity(event.getPlayer().getVelocity().zero());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (cancelIfFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (cancelIfFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (cancelIfFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (cancelIfFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && cancelIfFrozen(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onStartSpectatingEntity(PlayerStartSpectatingEntityEvent event) {
        event.setCancelled(true);
        event.getPlayer().setSpectatorTarget(null);
        event.getPlayer().sendActionBar(MessageUtil.warn("Entity-camera teleporting is disabled while spectating."));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSpectatorTeleport(PlayerTeleportEvent event) {
        if (!isNativeSpectatorTeleport(event.getCause())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().setSpectatorTarget(null);
        event.getPlayer().setFallDistance(0.0F);
        event.getPlayer().sendActionBar(MessageUtil.warn("Entity-camera teleporting is disabled while spectating."));
    }

    static boolean isNativeSpectatorTeleport(PlayerTeleportEvent.TeleportCause cause) {
        return cause == PlayerTeleportEvent.TeleportCause.SPECTATE;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) {
            event.getPlayer().sendMessage(MessageUtil.warn("You are still frozen by staff."));
        }
        reconcileFlightNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onFlightToggle(PlayerToggleFlightEvent event) {
        if (!event.isFlying() || plugin.getPlayerManager().isFlightAuthorized(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().setFlying(false);
        event.getPlayer().setAllowFlight(false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (!event.isCancelled() && event.getNewGameMode() != GameMode.CREATIVE) {
            reconcileFlightNextTick(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        reconcileFlightNextTick(event.getPlayer());
    }

    private void reconcileFlightNextTick(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.isEnabled() && player.isOnline()) {
                plugin.getPlayerManager().reconcileFlightAccess(player);
            }
        });
    }

    private boolean cancelIfFrozen(Player player) {
        if (player == null || !isFrozen(player.getUniqueId())) {
            return false;
        }
        player.sendActionBar(MessageUtil.warn("You are frozen."));
        return true;
    }

    private boolean movedPosition(Location from, Location to) {
        return Math.abs(from.getX() - to.getX()) > 0.0001D
            || Math.abs(from.getY() - to.getY()) > 0.0001D
            || Math.abs(from.getZ() - to.getZ()) > 0.0001D;
    }
}
