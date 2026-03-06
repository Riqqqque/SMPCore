package me.rique.smpcore.item;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MaceLimitListener implements Listener {

    private static final long WARN_COOLDOWN_MS = 2000L;

    private final SMPCore plugin;
    private final Map<UUID, Long> warnedAt = new ConcurrentHashMap<>();

    public MaceLimitListener(SMPCore plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::auditOnlinePlayers, 40L, 40L);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isMace(event.getItem())) return;
        if (canReceiveMace(player)) return;

        event.setCancelled(true);
        warnDenied(player);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isMace(event.getCurrentItem())) return;
        if (canReceiveMace(player)) return;

        event.setCancelled(true);
        warnDenied(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!wouldAcquireMace(event, player)) return;
        if (canReceiveMace(player)) return;

        event.setCancelled(true);
        warnDenied(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isMace(event.getOldCursor())) return;
        if (!touchesPlayerInventory(event, player)) return;
        if (canReceiveMace(player)) return;

        event.setCancelled(true);
        warnDenied(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, this::auditOnlinePlayers);
    }

    private boolean wouldAcquireMace(InventoryClickEvent event, Player player) {
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return false;
        if (clicked.equals(player.getInventory())) return false;
        return isMace(event.getCurrentItem());
    }

    private boolean touchesPlayerInventory(InventoryDragEvent event, Player player) {
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= topSize) {
                return true;
            }
        }
        return false;
    }

    private boolean canReceiveMace(Player player) {
        return findOtherCarrier(player.getUniqueId()) == null && !hasStoredMace(player);
    }

    private Player findOtherCarrier(UUID playerId) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(playerId)) continue;
            if (carriesMace(online)) return online;
        }
        return null;
    }

    private boolean carriesMace(Player player) {
        return hasStoredMace(player) || isMace(player.getOpenInventory().getCursor());
    }

    private boolean hasStoredMace(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isMace(item)) return true;
        }
        return false;
    }

    private void auditOnlinePlayers() {
        Player primaryCarrier = null;
        boolean keptPrimaryMace = false;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!carriesMace(player)) continue;
            if (primaryCarrier == null) {
                primaryCarrier = player;
            }

            List<ItemStack> removed = new ArrayList<>();
            ItemStack[] contents = player.getInventory().getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack item = contents[slot];
                if (!isMace(item)) continue;

                if (player.equals(primaryCarrier) && !keptPrimaryMace) {
                    keptPrimaryMace = true;
                    continue;
                }

                removed.add(item.clone());
                player.getInventory().setItem(slot, null);
            }

            ItemStack cursor = player.getOpenInventory().getCursor();
            if (isMace(cursor)) {
                if (player.equals(primaryCarrier) && !keptPrimaryMace) {
                    keptPrimaryMace = true;
                } else {
                    removed.add(cursor.clone());
                    player.getOpenInventory().setCursor(null);
                }
            }

            if (removed.isEmpty()) continue;

            for (ItemStack item : removed) {
                Item dropped = player.getWorld().dropItemNaturally(player.getLocation(), item);
                dropped.setPickupDelay(40);
            }

            player.updateInventory();
            player.sendMessage(MessageUtil.warn("Only one mace can be carried across the server at a time. Extra maces were dropped."));
        }
    }

    private void warnDenied(Player player) {
        long now = System.currentTimeMillis();
        long last = warnedAt.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < WARN_COOLDOWN_MS) return;

        warnedAt.put(player.getUniqueId(), now);
        Player holder = findOtherCarrier(player.getUniqueId());
        if (holder != null) {
            player.sendMessage(MessageUtil.error("Only one player can carry a mace at a time. <white>" + holder.getName() + "</white> currently has one."));
            return;
        }
        player.sendMessage(MessageUtil.error("You can only carry one mace at a time."));
    }

    private boolean isMace(Item item) {
        return item != null && isMace(item.getItemStack());
    }

    private boolean isMace(ItemStack item) {
        return item != null && item.getType() == Material.MACE;
    }
}
