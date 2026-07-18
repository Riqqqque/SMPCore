package me.rique.smpcore.crafting;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.item.CustomEnchantListener;
import me.rique.smpcore.item.ReplenishListener;
import me.rique.smpcore.util.InventoryRecipeUtil;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
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
import org.bukkit.inventory.SmithingTrimRecipe;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.Locale;

/**
 * Global crafting rules:
 * - Golden apple uses gold ingots around an apple.
 * - 9 Rotten Flesh can be crafted into Leather.
 * - Iron, copper, and gold can be crafted into Bells.
 * - Optional block for netherite armor smithing upgrades.
 */
public final class CraftingRulesListener implements Listener {

    private final SMPCore plugin;
    private final NamespacedKey goldenAppleRecipeKey;
    private final NamespacedKey leatherFromRottenFleshRecipeKey;
    private final NamespacedKey bellRecipeKey;

    public CraftingRulesListener(SMPCore plugin) {
        this.plugin = plugin;
        // Keep the original key so existing player recipe books do not retain an unknown recipe ID.
        this.goldenAppleRecipeKey = new NamespacedKey(plugin, "golden_apple_nugget_recipe");
        this.leatherFromRottenFleshRecipeKey = new NamespacedKey(plugin, "leather_from_rotten_flesh");
        this.bellRecipeKey = new NamespacedKey(plugin, "bell_recipe");
        registerGoldenAppleRecipe();
        registerLeatherRecipe();
        registerBellRecipe();

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
        if (isManagedConvenienceRecipe(event.getRecipe()) && !usesOnlyPlainRecipeIngredients(inv.getMatrix())) {
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (isManagedConvenienceRecipe(event.getRecipe())) {
            if (usesOnlyPlainRecipeIngredients(event.getInventory().getMatrix())) {
                return;
            }

            event.setCancelled(true);
            player.sendMessage(MessageUtil.warn("Custom items cannot be used in SMPCore crafting recipes."));
            return;
        }
        if (!isMinecraftRecipe(event.getRecipe())) {
            return;
        }
        if (!containsProtectedCustomItem(event.getInventory().getMatrix())) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(MessageUtil.warn("Custom items cannot be used in vanilla crafting recipes."));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        ItemStack source = null;
        boolean preserveCustomEnchants = false;
        boolean preserveVeilArmorTrim = false;
        if (event.getInventory() instanceof SmithingInventory smithing) {
            source = smithing.getInputEquipment();
            preserveCustomEnchants = isCustomEnchantedVanillaGear(source);
            if (isProtectedCustomItem(source) && !preserveCustomEnchants) {
                preserveVeilArmorTrim = isVeilArmorTrimRecipe(smithing, source, event.getResult());
                if (!preserveVeilArmorTrim) {
                    event.setResult(null);
                    return;
                }
            }
        }

        ItemStack result = event.getResult();
        if (result == null || result.getType() == Material.AIR) return;
        if (plugin.getConfigManager().blockNetheriteArmorUpgrade
            && event.getInventory() instanceof SmithingInventory smithing
            && !isArmorTrimRecipe(smithing)
            && isNetheriteArmor(result.getType())) {
            event.setResult(null);
            return;
        }
        if (preserveVeilArmorTrim) {
            event.setResult(preserveVeilArmorTrim(source, result));
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
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getType() != InventoryType.SMITHING) return;
        if (!isSmithingResultSlot(event)) return;
        if (!(event.getView().getTopInventory() instanceof SmithingInventory smith)) return;

        ItemStack source = smith.getInputEquipment();
        if (isProtectedCustomItem(source) && !isCustomEnchantedVanillaGear(source)) {
            if (!isVeilArmorTrimRecipe(smith, source, smith.getResult())) {
                event.setCancelled(true);
                player.sendMessage(MessageUtil.warn("Custom items cannot be used in vanilla smithing recipes."));
                return;
            }
        }

        ItemStack result = smith.getResult();
        if (result == null || result.getType() == Material.AIR) return;
        if (!plugin.getConfigManager().blockNetheriteArmorUpgrade) return;
        if (isArmorTrimRecipe(smith)) return;
        if (!isNetheriteArmor(result.getType())) return;

        event.setCancelled(true);
        player.sendMessage(MessageUtil.warn("Netherite armor upgrading is disabled on this server."));
    }

    private void registerGoldenAppleRecipe() {
        Bukkit.removeRecipe(NamespacedKey.minecraft("golden_apple"));
        Bukkit.removeRecipe(goldenAppleRecipeKey);

        ItemStack result = new ItemStack(Material.GOLDEN_APPLE, 1);
        ShapedRecipe shaped = new ShapedRecipe(goldenAppleRecipeKey, result);
        shaped.shape("GGG", "GAG", "GGG");
        shaped.setIngredient('G', plugin.getConfigManager().goldenAppleSurroundMaterial);
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

    private void registerBellRecipe() {
        Bukkit.removeRecipe(bellRecipeKey);

        ItemStack result = new ItemStack(Material.BELL, 1);
        ShapedRecipe shaped = new ShapedRecipe(bellRecipeKey, result);
        shaped.shape(" I ", "ICI", " G ");
        shaped.setIngredient('I', Material.IRON_INGOT);
        shaped.setIngredient('C', Material.COPPER_INGOT);
        shaped.setIngredient('G', Material.GOLD_INGOT);
        shaped.setGroup("smpcore_crafting");
        Bukkit.addRecipe(shaped);
    }

    public void reloadConfig() {
        registerGoldenAppleRecipe();
        registerLeatherRecipe();
        registerBellRecipe();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                discoverCustomRecipes(player);
            }
        });
    }

    private void discoverCustomRecipes(Player player) {
        player.discoverRecipe(goldenAppleRecipeKey);
        player.discoverRecipe(leatherFromRottenFleshRecipeKey);
        player.discoverRecipe(bellRecipeKey);
    }

    private boolean shouldBlockProtectedIngredientInVanillaCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        ItemStack result = event.getInventory().getResult();
        if ((result == null || result.getType().isAir()) && recipe == null) {
            return false;
        }
        if (isAllowedCustomCraft(event.getInventory().getMatrix())) {
            return false;
        }
        if (!containsProtectedCustomItem(event.getInventory().getMatrix())) {
            return false;
        }
        return recipe == null || isMinecraftRecipe(recipe);
    }

    private boolean isAllowedCustomCraft(ItemStack[] matrix) {
        if (plugin.getBackpackListener() != null && plugin.getBackpackListener().isBackpackUpgradeCraft(matrix)) {
            return true;
        }
        return plugin.getSuperpowerManager() != null && plugin.getSuperpowerManager().isAncientScrollCraft(matrix);
    }

    private boolean isMinecraftRecipe(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) {
            return false;
        }
        NamespacedKey key = keyed.getKey();
        return key != null && "minecraft".equals(key.getNamespace());
    }

    private boolean isManagedConvenienceRecipe(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) {
            return false;
        }
        NamespacedKey key = keyed.getKey();
        return goldenAppleRecipeKey.equals(key)
            || leatherFromRottenFleshRecipeKey.equals(key)
            || bellRecipeKey.equals(key);
    }

    private boolean usesOnlyPlainRecipeIngredients(ItemStack[] matrix) {
        if (matrix == null) {
            return false;
        }
        for (ItemStack item : matrix) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            if (!InventoryRecipeUtil.isPlainMaterial(plugin, item, item.getType())) {
                return false;
            }
        }
        return true;
    }

    private boolean isSmithingResultSlot(InventoryClickEvent event) {
        return event.getView().getTopInventory().getType() == InventoryType.SMITHING
            && (event.getClickedInventory() == event.getView().getTopInventory() || event.getRawSlot() == 3)
            && (event.getSlotType() == InventoryType.SlotType.RESULT || event.getRawSlot() == 3);
    }

    private boolean isVeilArmorTrimRecipe(SmithingInventory smithing, ItemStack source, ItemStack result) {
        if (!isArmorTrimRecipe(smithing)) {
            return false;
        }
        if (plugin.getSeasonRelicManager() == null || !plugin.getSeasonRelicManager().isVeilArmor(source)) {
            return false;
        }
        if (!(source.getItemMeta() instanceof ArmorMeta) || result == null || result.getType().isAir()) {
            return false;
        }
        return result.getItemMeta() instanceof ArmorMeta armorMeta && armorMeta.hasTrim();
    }

    private boolean isArmorTrimRecipe(SmithingInventory smithing) {
        return smithing != null && smithing.getRecipe() instanceof SmithingTrimRecipe;
    }

    private ItemStack preserveVeilArmorTrim(ItemStack source, ItemStack result) {
        ItemStack updated = source.clone();
        updated.setAmount(1);

        ItemMeta resultMeta = result.getItemMeta();
        ItemMeta updatedMeta = updated.getItemMeta();
        if (!(resultMeta instanceof ArmorMeta resultArmor) || !resultArmor.hasTrim()
            || !(updatedMeta instanceof ArmorMeta updatedArmor)) {
            return result.clone();
        }

        updatedArmor.setTrim(resultArmor.getTrim());
        updated.setItemMeta(updatedArmor);
        return updated;
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
