package me.rique.smpcore.legendary;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.team.TeamManager;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class LegendaryStorageGuardListener implements Listener {

    private final SMPCore plugin;

    public LegendaryStorageGuardListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onProtectedStorageClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!isProtectedStorage(topInventory) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int topSize = topInventory.getSize();
        boolean clickedTop = event.getRawSlot() >= 0 && event.getRawSlot() < topSize;
        boolean clickedBottom = event.getClickedInventory() != null
            && event.getClickedInventory().equals(event.getView().getBottomInventory());

        if (isUnsafeStorageClick(event)) {
            event.setCancelled(true);
            scheduleProtectedStorageCleanup(topInventory, player, "unsafe_click");
            return;
        }

        if (clickedTop && containsRestrictedLegendary(event.getCursor())) {
            block(event, player, topInventory);
            return;
        }

        if (clickedTop && event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
            ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
            if (containsRestrictedLegendary(hotbarItem)) {
                block(event, player, topInventory);
                return;
            }
        }

        if (clickedTop && event.getClick() == ClickType.SWAP_OFFHAND
            && containsRestrictedLegendary(player.getInventory().getItemInOffHand())) {
            block(event, player, topInventory);
            return;
        }

        if (clickedBottom
            && (event.isShiftClick() || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY)
            && containsRestrictedLegendary(event.getCurrentItem())) {
            block(event, player, topInventory);
            return;
        }

        scheduleProtectedStorageCleanup(topInventory, player, "click");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onProtectedStorageDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!isProtectedStorage(topInventory) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int topSize = topInventory.getSize();
        boolean touchesTopInventory = false;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topSize) {
                touchesTopInventory = true;
                break;
            }
        }
        if (!touchesTopInventory) {
            return;
        }

        if (containsRestrictedLegendary(event.getOldCursor())
            || event.getNewItems().values().stream().anyMatch(this::containsRestrictedLegendary)) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.warn(storageName(topInventory) + " cannot store unique legendaries or containers holding them."));
            return;
        }

        scheduleProtectedStorageCleanup(topInventory, player, "drag");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedStorageOpen(InventoryOpenEvent event) {
        Inventory topInventory = event.getInventory();
        if (!isProtectedStorage(topInventory) || !(event.getPlayer() instanceof Player player)) {
            return;
        }

        cleanupProtectedStorage(topInventory, player, "open");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProtectedStorageClose(InventoryCloseEvent event) {
        Inventory topInventory = event.getInventory();
        if (!isProtectedStorage(topInventory) || !(event.getPlayer() instanceof Player player)) {
            return;
        }

        cleanupProtectedStorage(topInventory, player, "close");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                cleanupProtectedStorage(player.getEnderChest(), player, "join");
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedStorageMove(InventoryMoveItemEvent event) {
        if (isProtectedStorage(event.getDestination()) && containsRestrictedLegendary(event.getItem())) {
            event.setCancelled(true);
            return;
        }

        if (isProtectedStorage(event.getSource()) && containsRestrictedLegendary(event.getItem())) {
            event.setCancelled(true);
        }
    }

    private void scheduleProtectedStorageCleanup(Inventory inventory, Player player, String reason) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !isProtectedStorage(inventory)) {
                return;
            }

            cleanupProtectedStorage(inventory, player, "cleanup_" + reason);
        });
    }

    private void block(InventoryClickEvent event, Player player, Inventory topInventory) {
        event.setCancelled(true);
        player.sendMessage(MessageUtil.warn(storageName(topInventory) + " cannot store unique legendaries or containers holding them."));
    }

    private boolean containsRestrictedLegendary(ItemStack item) {
        LegendaryListener legendaryListener = plugin.getLegendaryListener();
        return legendaryListener != null && legendaryListener.containsStorageRestrictedLegendary(item);
    }

    private int ejectRestrictedItems(Inventory inventory, Player player) {
        int moved = 0;
        boolean inventoryFull = false;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!containsRestrictedLegendary(item)) {
                continue;
            }
            if (!canFitInPlayerStorage(player, item)) {
                inventoryFull = true;
                continue;
            }

            ItemStack[] playerStorageBefore = cloneContents(player.getInventory().getStorageContents());
            inventory.setItem(slot, null);
            if (!player.getInventory().addItem(item.clone()).isEmpty()) {
                player.getInventory().setStorageContents(playerStorageBefore);
                inventory.setItem(slot, item);
                inventoryFull = true;
                continue;
            }
            moved++;
        }
        if (inventoryFull) {
            player.sendMessage(MessageUtil.warn("Free some inventory space to remove protected legendaries from this container."));
        }
        return moved;
    }

    private void cleanupProtectedStorage(Inventory inventory, Player player, String reason) {
        int moved = ejectRestrictedItems(inventory, player);
        if (moved <= 0) {
            return;
        }

        TeamManager teamManager = plugin.getTeamManager();
        if (teamManager != null) {
            teamManager.requestTeamVaultSave(inventory, "restricted_legendary_" + reason);
        }
        LegendaryListener legendaryListener = plugin.getLegendaryListener();
        if (legendaryListener != null) {
            legendaryListener.resyncLegendaryOwnership(player);
        }
        player.updateInventory();
        player.sendMessage(MessageUtil.warn(storageName(inventory)
            + " cannot store unique legendaries. Moved " + moved + " protected item"
            + (moved == 1 ? "" : "s") + " back to you."));
    }

    private boolean canFitInPlayerStorage(Player player, ItemStack item) {
        if (player == null || item == null || item.getType().isAir()) {
            return false;
        }
        int remaining = item.getAmount();
        int maxStackSize = Math.max(1, item.getMaxStackSize());
        for (ItemStack existing : player.getInventory().getStorageContents()) {
            if (existing == null || existing.getType().isAir()) {
                remaining -= maxStackSize;
            } else if (existing.isSimilar(item)) {
                remaining -= Math.max(0, maxStackSize - existing.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] cloned = new ItemStack[contents == null ? 0 : contents.length];
        for (int slot = 0; slot < cloned.length; slot++) {
            ItemStack item = contents[slot];
            cloned[slot] = item == null ? null : item.clone();
        }
        return cloned;
    }

    private boolean isUnsafeStorageClick(InventoryClickEvent event) {
        InventoryAction action = event.getAction();
        ClickType click = event.getClick();
        return action == InventoryAction.CLONE_STACK
            || action == InventoryAction.COLLECT_TO_CURSOR
            || action == InventoryAction.UNKNOWN
            || click == ClickType.CREATIVE
            || click == ClickType.MIDDLE
            || click == ClickType.UNKNOWN;
    }

    private boolean isProtectedStorage(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        if (inventory.getType() == InventoryType.ENDER_CHEST) {
            return true;
        }
        TeamManager teamManager = plugin.getTeamManager();
        return teamManager != null && teamManager.isTeamVaultInventory(inventory);
    }

    private String storageName(Inventory inventory) {
        if (inventory != null && inventory.getType() == InventoryType.ENDER_CHEST) {
            return "Ender chests";
        }
        return "Team vaults";
    }
}
