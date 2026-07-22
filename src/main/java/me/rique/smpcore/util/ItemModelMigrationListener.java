package me.rique.smpcore.util;

import me.rique.smpcore.SMPCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class ItemModelMigrationListener implements Listener {

    private final SMPCore plugin;

    public ItemModelMigrationListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ItemModelUtil.clearVanillaBackedModels(player.getInventory());
        ItemModelUtil.clearVanillaBackedModels(player.getEnderChest());
        normalizeLore(player.getInventory());
        normalizeLore(player.getEnderChest());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        ItemModelUtil.clearVanillaBackedModels(event.getInventory());
        normalizeLore(event.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        boolean modelChanged = ItemModelUtil.clearVanillaBackedModel(item);
        boolean enchantDataChanged = normalizeEnchantData(item);
        boolean loreChanged = CustomLoreUtil.normalizeItemLore(item);
        if (modelChanged || enchantDataChanged || loreChanged) {
            event.getItem().setItemStack(item);
        }
    }

    private void normalizeLore(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            normalizeEnchantData(item);
            CustomLoreUtil.normalizeItemLore(item);
        }
    }

    private boolean normalizeEnchantData(ItemStack item) {
        boolean changed = false;
        if (plugin.getCustomEnchantListener() != null) {
            changed |= plugin.getCustomEnchantListener().normalizeManagedEnchantData(item);
        }
        if (plugin.getReplenishListener() != null) {
            changed |= plugin.getReplenishListener().normalizeReplenishData(item);
        }
        return changed;
    }
}
