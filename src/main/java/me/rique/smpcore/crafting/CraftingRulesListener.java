package me.rique.smpcore.crafting;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.SmithingInventory;

/**
 * Global crafting rules:
 * - Golden apple uses nuggets instead of ingots.
 * - Optional block for netherite armor smithing upgrades.
 */
public final class CraftingRulesListener implements Listener {

    private final SMPCore plugin;
    private final NamespacedKey goldenAppleNuggetRecipeKey;

    public CraftingRulesListener(SMPCore plugin) {
        this.plugin = plugin;
        this.goldenAppleNuggetRecipeKey = new NamespacedKey(plugin, "golden_apple_nugget_recipe");
        registerGoldenAppleRecipe();

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                discoverGoldenAppleRecipe(player);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        discoverGoldenAppleRecipe(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        ItemStack result = inv.getResult();
        if (result == null || result.getType() != Material.GOLDEN_APPLE) return;

        boolean hasIngot = false;
        for (ItemStack item : inv.getMatrix()) {
            if (item == null || item.getType() == Material.AIR) continue;
            if (item.getType() == Material.GOLD_INGOT) {
                hasIngot = true;
                break;
            }
        }

        if (hasIngot) {
            inv.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (!plugin.getConfigManager().blockNetheriteArmorUpgrade) return;
        ItemStack result = event.getResult();
        if (result == null || result.getType() == Material.AIR) return;
        if (!isNetheriteArmor(result.getType())) return;
        event.setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSmithingClick(InventoryClickEvent event) {
        if (!plugin.getConfigManager().blockNetheriteArmorUpgrade) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        if (event.getView().getTopInventory().getType() != InventoryType.SMITHING) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;
        if (!(event.getView().getTopInventory() instanceof SmithingInventory smith)) return;

        ItemStack result = smith.getResult();
        if (result == null || result.getType() == Material.AIR) return;
        if (!isNetheriteArmor(result.getType())) return;

        event.setCancelled(true);
        player.sendMessage(MessageUtil.warn("Netherite armor upgrading is disabled on this server."));
    }

    private void registerGoldenAppleRecipe() {
        Bukkit.removeRecipe(NamespacedKey.minecraft("golden_apple"));
        Bukkit.removeRecipe(goldenAppleNuggetRecipeKey);

        ItemStack result = new ItemStack(Material.GOLDEN_APPLE, 1);
        ShapedRecipe shaped = new ShapedRecipe(goldenAppleNuggetRecipeKey, result);
        shaped.shape("NNN", "NAN", "NNN");
        shaped.setIngredient('N', Material.GOLD_NUGGET);
        shaped.setIngredient('A', Material.APPLE);
        shaped.setGroup("smpcore_crafting");
        Bukkit.addRecipe(shaped);
    }

    private void discoverGoldenAppleRecipe(Player player) {
        player.discoverRecipe(goldenAppleNuggetRecipeKey);
    }

    private static boolean isNetheriteArmor(Material type) {
        return switch (type) {
            case NETHERITE_HELMET, NETHERITE_CHESTPLATE, NETHERITE_LEGGINGS, NETHERITE_BOOTS -> true;
            default -> false;
        };
    }
}
