package me.rique.smpcore.util;

import me.rique.smpcore.SMPCore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MenuDupeGuardListener implements Listener {

    private static final String PLUGIN_PACKAGE_PREFIX = "me.rique.smpcore.";
    private static final long PREVIEW_LOG_WINDOW_MILLIS = 30_000L;
    private static final Set<String> WARNING_PREVIEW_REASONS = Set.of(
        "drop",
        "world spawn",
        "pickup",
        "inventory pickup",
        "inventory transfer"
    );
    private static final Set<String> MUTABLE_MENU_HOLDERS = Set.of(
        "AwakeningMenuHolder",
        "BackpackHolder",
        "MythicForgeMenuHolder",
        "TeamVaultHolder"
    );

    private final SMPCore plugin;
    private final NamespacedKey menuPreviewKey;
    private final Map<String, PreviewLogState> previewLogStates = new ConcurrentHashMap<>();

    public MenuDupeGuardListener(SMPCore plugin) {
        this.plugin = plugin;
        this.menuPreviewKey = new NamespacedKey(plugin, "menu_preview_item");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (isReadOnlyPluginMenu(event.getInventory())) {
            markMenuItems(event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!isReadOnlyPluginMenu(top)) {
            purgeIllegalPreviewCursor(event.getWhoClicked() instanceof Player player ? player : null);
            return;
        }

        markMenuItems(top);
        boolean hadCursorBeforeClick = !isEmpty(event.getCursor());
        ItemStack clicked = event.getCurrentItem() == null ? null : event.getCurrentItem().clone();

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            boolean clearedCursor = clearPreviewCursor(player, "read-only menu click");
            if (!hadCursorBeforeClick && clicked != null && hasMenuPreviewMarker(clicked)
                && isSamePreview(clicked, player.getItemOnCursor())) {
                clearedCursor = clearPreviewCursor(player, "read-only menu click") || clearedCursor;
            }
            boolean clearedInventory = purgeMarkedItems(player.getInventory(), player, "read-only menu inventory leak");
            if (clearedCursor || clearedInventory) {
                player.updateInventory();
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!isReadOnlyPluginMenu(top)) {
            return;
        }

        markMenuItems(top);
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            plugin.getServer().getScheduler().runTask(plugin, player::updateInventory);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        // The top inventory is being discarded by Bukkit on close, so clearing its
        // marked preview items only creates noisy false-positive warnings.
        boolean clearedCursor = clearPreviewCursor(player, "inventory close");
        boolean clearedInventory = purgeMarkedItems(player.getInventory(), player, "inventory close");
        if (clearedCursor || clearedInventory) {
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        purgeMarkedItems(event.getPlayer().getInventory(), event.getPlayer(), "player join");
        clearPreviewCursor(event.getPlayer(), "player join");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerKick(PlayerKickEvent event) {
        purgeMarkedItems(event.getPlayer().getInventory(), event.getPlayer(), "player kick");
        clearPreviewCursor(event.getPlayer(), "player kick");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        purgeMarkedItems(event.getPlayer().getInventory(), event.getPlayer(), "player quit");
        clearPreviewCursor(event.getPlayer(), "player quit");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!hasMenuPreviewMarker(event.getItemDrop().getItemStack())) {
            return;
        }
        logBlockedPreview(event.getPlayer(), event.getItemDrop().getItemStack(), "drop");
        event.getItemDrop().remove();
        clearPreviewCursor(event.getPlayer(), "drop");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (hasMenuPreviewMarker(event.getEntity().getItemStack())) {
            logBlockedPreview(null, event.getEntity().getItemStack(), "world spawn");
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!hasMenuPreviewMarker(event.getItem().getItemStack())) {
            return;
        }
        logBlockedPreview(event.getEntity() instanceof Player player ? player : null, event.getItem().getItemStack(), "pickup");
        event.setCancelled(true);
        event.getItem().remove();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryPickupItem(InventoryPickupItemEvent event) {
        if (!hasMenuPreviewMarker(event.getItem().getItemStack())) {
            return;
        }
        logBlockedPreview(null, event.getItem().getItemStack(), "inventory pickup");
        event.setCancelled(true);
        event.getItem().remove();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (!hasMenuPreviewMarker(event.getItem())) {
            return;
        }
        logBlockedPreview(null, event.getItem(), "inventory transfer");
        event.setCancelled(true);
        purgeMarkedItems(event.getSource(), null, "inventory transfer source cleanup");
    }

    private boolean isReadOnlyPluginMenu(Inventory inventory) {
        if (inventory == null) {
            return false;
        }

        InventoryHolder holder = inventory.getHolder(false);
        if (holder == null) {
            return false;
        }

        Class<?> holderClass = holder.getClass();
        String className = holderClass.getName();
        if (!className.startsWith(PLUGIN_PACKAGE_PREFIX)) {
            return false;
        }

        String simpleName = holderClass.getSimpleName();
        if (MUTABLE_MENU_HOLDERS.contains(simpleName)) {
            return false;
        }

        return simpleName.endsWith("MenuHolder")
            || simpleName.endsWith("RecipeHolder")
            || simpleName.endsWith("GuideHolder")
            || simpleName.endsWith("InfoHolder")
            || simpleName.endsWith("GrimoireHolder")
            || simpleName.endsWith("InspectorHolder");
    }

    private void markMenuItems(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isEmpty(item) || hasMenuPreviewMarker(item)) {
                continue;
            }

            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                continue;
            }

            meta.getPersistentDataContainer().set(menuPreviewKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
        }
    }

    private void purgeIllegalPreviewCursor(Player player) {
        if (player != null && clearPreviewCursor(player, "non-menu cursor cleanup")) {
            player.updateInventory();
        }
    }

    private boolean purgeMarkedItems(Inventory inventory, Player player, String reason) {
        boolean changed = false;
        int removed = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (hasMenuPreviewMarker(item)) {
                inventory.setItem(slot, null);
                changed = true;
                removed++;
            }
        }
        if (removed > 0) {
            logBlockedPreviewBatch(player, reason, removed);
        }
        return changed;
    }

    private boolean clearPreviewCursor(Player player, String reason) {
        ItemStack cursor = player.getItemOnCursor();
        if (!hasMenuPreviewMarker(cursor)) {
            return false;
        }
        logBlockedPreview(player, cursor, reason);
        player.setItemOnCursor(null);
        return true;
    }

    private void logBlockedPreview(Player player, ItemStack item, String reason) {
        String owner = player == null ? "unknown" : player.getName();
        String itemName = item == null ? "unknown" : item.getType() + "x" + item.getAmount();
        logPreviewRemoval(owner, reason, itemName, 1);
    }

    private void logBlockedPreviewBatch(Player player, String reason, int removed) {
        String owner = player == null ? "unknown" : player.getName();
        logPreviewRemoval(owner, reason, "mixed menu previews", removed);
    }

    private void logPreviewRemoval(String owner, String reason, String itemName, int removed) {
        if (removed <= 0) {
            return;
        }

        String key = owner + "|" + reason;
        PreviewLogState state = previewLogStates.computeIfAbsent(key, ignored -> new PreviewLogState());
        long now = System.currentTimeMillis();
        int total;
        synchronized (state) {
            if (now - state.lastLogAt < PREVIEW_LOG_WINDOW_MILLIS) {
                state.suppressed += removed;
                return;
            }
            total = removed + state.suppressed;
            state.suppressed = 0;
            state.lastLogAt = now;
        }

        String message = total == 1
            ? "Blocked leaked menu preview item for " + owner + " via " + reason + ": " + itemName
            : "Blocked " + total + " leaked menu preview items for " + owner
                + " via " + reason + " over the recent window. Sample: " + itemName + ".";
        if (shouldWarnPreviewRemoval(reason)) {
            plugin.getLogger().warning(message);
        } else {
            plugin.getLogger().fine(message);
        }
    }

    private boolean shouldWarnPreviewRemoval(String reason) {
        return reason != null && WARNING_PREVIEW_REASONS.contains(reason);
    }

    private boolean hasMenuPreviewMarker(ItemStack item) {
        if (isEmpty(item)) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(menuPreviewKey, PersistentDataType.BYTE);
    }

    private boolean isSamePreview(ItemStack expected, ItemStack actual) {
        return !isEmpty(expected) && !isEmpty(actual) && expected.isSimilar(actual);
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    private static final class PreviewLogState {
        private long lastLogAt;
        private int suppressed;
    }
}
