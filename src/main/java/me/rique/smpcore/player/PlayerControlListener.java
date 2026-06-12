package me.rique.smpcore.player;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerControlListener implements Listener {

    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();

    public PlayerControlListener(SMPCore plugin) {
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

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) {
            event.getPlayer().sendMessage(MessageUtil.warn("You are still frozen by staff."));
        }
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
