package me.rique.smpcore.crafting;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.item.CustomEnchantListener;
import me.rique.smpcore.item.ReplenishListener;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
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
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.Locale;

/**
 * Global crafting rules:
 * - Golden apple uses nuggets instead of ingots.
 * - 9 Rotten Flesh can be crafted into Leather.
 * - Optional block for netherite armor smithing upgrades.
 */
public final class CraftingRulesListener implements Listener {

    private final SMPCore plugin;
    private final NamespacedKey goldenAppleNuggetRecipeKey;
    private final NamespacedKey leatherFromRottenFleshRecipeKey;

    public CraftingRulesListener(SMPCore plugin) {
        this.plugin = plugin;
        this.goldenAppleNuggetRecipeKey = new NamespacedKey(plugin, "golden_apple_nugget_recipe");
        this.leatherFromRottenFleshRecipeKey = new NamespacedKey(plugin, "leather_from_rotten_flesh");
        registerGoldenAppleRecipe();
        registerLeatherRecipe();

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                discoverCustomRecipes(player);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        discoverCustomRecipes(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        if (shouldBlockProtectedIngredientInVanillaCraft(event)) {
            inv.setResult(null);
            return;
        }

        ItemStack result = inv.getResult();
        if (result == null || result.getType() != Material.GOLDEN_APPLE) return;

        Material surround = plugin.getConfigManager().goldenAppleSurroundMaterial;
        boolean hasInvalidIngredient = false;
        for (ItemStack item : inv.getMatrix()) {
            if (item == null || item.getType() == Material.AIR) continue;
            Material type = item.getType();
            if (type != Material.APPLE && type != surround) {
                hasInvalidIngredient = true;
                break;
            }
        }

        if (hasInvalidIngredient) {
            inv.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        ItemStack source = null;
        boolean preserveCustomEnchants = false;
        if (event.getInventory() instanceof SmithingInventory smithing) {
            source = smithing.getInputEquipment();
            preserveCustomEnchants = isCustomEnchantedVanillaGear(source);
            if (isProtectedCustomItem(source) && !preserveCustomEnchants) {
                event.setResult(null);
                return;
            }
        }

        ItemStack result = event.getResult();
        if (result == null || result.getType() == Material.AIR) return;
        if (plugin.getConfigManager().blockNetheriteArmorUpgrade && isNetheriteArmor(result.getType())) {
            event.setResult(null);
            return;
        }
        if (!preserveCustomEnchants) return;

        CustomEnchantListener enchants = plugin.getCustomEnchantListener();
        ItemStack preserved = result.clone();
        if (enchants != null) {
            preserved = enchants.preserveManagedEnchants(source, preserved);
        }
        ReplenishListener replenish = plugin.getReplenishListener();
        if (replenish != null) {
            preserved = replenish.preserveReplenish(source, preserved);
        }
        event.setResult(preserved);
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
        shaped.setIngredient('N', plugin.getConfigManager().goldenAppleSurroundMaterial);
        shaped.setIngredient('A', Material.APPLE);
        shaped.setGroup("smpcore_crafting");
        Bukkit.addRecipe(shaped);
    }

    private void registerLeatherRecipe() {
        Bukkit.removeRecipe(leatherFromRottenFleshRecipeKey);

        ItemStack result = new ItemStack(Material.LEATHER, 1);
        ShapedRecipe shaped = new ShapedRecipe(leatherFromRottenFleshRecipeKey, result);
        shaped.shape("RRR", "RRR", "RRR");
        shaped.setIngredient('R', Material.ROTTEN_FLESH);
        shaped.setGroup("smpcore_crafting");
        Bukkit.addRecipe(shaped);
    }

    public void reloadConfig() {
        registerGoldenAppleRecipe();
        registerLeatherRecipe();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                discoverCustomRecipes(player);
            }
        });
    }

    private void discoverCustomRecipes(Player player) {
        player.discoverRecipe(goldenAppleNuggetRecipeKey);
        player.discoverRecipe(leatherFromRottenFleshRecipeKey);
    }

    private boolean shouldBlockProtectedIngredientInVanillaCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        ItemStack result = event.getInventory().getResult();
        if ((result == null || result.getType().isAir()) && recipe == null) {
            return false;
        }
        if (!containsProtectedCustomItem(event.getInventory().getMatrix())) {
            return false;
        }
        return recipe == null || isMinecraftRecipe(recipe);
    }

    private boolean isMinecraftRecipe(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) {
            return false;
        }
        NamespacedKey key = keyed.getKey();
        return key != null && "minecraft".equals(key.getNamespace());
    }

    private boolean containsProtectedCustomItem(ItemStack[] contents) {
        if (contents == null) {
            return false;
        }
        for (ItemStack item : contents) {
            if (isProtectedCustomItem(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean isCustomEnchantedVanillaGear(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        String namespace = plugin.getName().toLowerCase(Locale.ROOT);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        boolean hasAllowedEnchantData = false;
        CustomEnchantListener enchants = plugin.getCustomEnchantListener();
        ReplenishListener replenish = plugin.getReplenishListener();
        for (NamespacedKey key : pdc.getKeys()) {
            if (!namespace.equals(key.getNamespace())) {
                continue;
            }
            if (enchants != null && enchants.isManagedEnchantDataKey(key)) {
                hasAllowedEnchantData = true;
                continue;
            }
            if (replenish != null && replenish.isReplenishEnchantDataKey(key)) {
                hasAllowedEnchantData = true;
                continue;
            }
            return false;
        }
        return hasAllowedEnchantData;
    }

    private boolean isProtectedCustomItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (hasPluginPersistentData(item)) {
            return true;
        }
        if (plugin.getLegendaryListener() != null
            && (plugin.getLegendaryListener().isLegendaryItem(item)
                || plugin.getLegendaryListener().isEnderBoneItem(item)
                || plugin.getLegendaryListener().isOrbOfTheMysticsItem(item))) {
            return true;
        }
        if (plugin.getBackpackListener() != null && plugin.getBackpackListener().isBackpack(item)) {
            return true;
        }
        return plugin.getCustomToolListener() != null && plugin.getCustomToolListener().isCustomTool(item);
    }

    private boolean hasPluginPersistentData(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        String namespace = plugin.getName().toLowerCase(Locale.ROOT);
        return meta.getPersistentDataContainer().getKeys().stream()
            .anyMatch(key -> namespace.equals(key.getNamespace()));
    }

    private static boolean isNetheriteArmor(Material type) {
        return switch (type) {
            case NETHERITE_HELMET, NETHERITE_CHESTPLATE, NETHERITE_LEGGINGS, NETHERITE_BOOTS -> true;
            default -> false;
        };
    }
}
