package me.rique.smpcore.item;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SustenanceTalismanListener implements Listener {

    public static final String ITEM_ID = "talisman_of_sustenance";

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Material TALISMAN_BASE_MATERIAL = Material.TOTEM_OF_UNDYING;
    private static final Map<Material, Integer> RECIPE_INGREDIENTS = Map.of(
        Material.TOTEM_OF_UNDYING, 1,
        Material.GOLDEN_APPLE, 64,
        Material.NETHER_STAR, 1,
        Material.GOLDEN_CARROT, 32
    );

    private final SMPCore plugin;
    private final NamespacedKey keyTalismanId;
    private final Map<UUID, Long> nextPassiveTick = new ConcurrentHashMap<>();

    public SustenanceTalismanListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyTalismanId = new NamespacedKey(plugin, ITEM_ID);

        Bukkit.getScheduler().runTaskTimer(plugin, this::tickTalismans, 20L, 20L);
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::refreshPlayerTalismans));
    }

    public Map<Material, Integer> recipeIngredients() {
        return RECIPE_INGREDIENTS;
    }

    public ItemStack createTalismanItem() {
        ItemStack item = new ItemStack(TALISMAN_BASE_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        applyTalismanState(meta);
        applyTalismanPresentation(meta);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isTalisman(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return ITEM_ID.equals(meta.getPersistentDataContainer().get(keyTalismanId, PersistentDataType.STRING));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refreshPlayerTalismans(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        nextPassiveTick.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return;
        }

        ItemStack usedItem = hand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
        if (!isTalisman(usedItem)) {
            return;
        }

        EquipmentSlot otherHand = hand == EquipmentSlot.OFF_HAND ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;
        ItemStack otherItem = otherHand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();

        if (isRealTotem(otherItem)) {
            ItemStack talismanSnapshot = usedItem.clone();
            Bukkit.getScheduler().runTask(plugin, () -> completeTotemFallbackResurrection(player, hand, otherHand, talismanSnapshot));
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        if (matchesRecipe(inventory.getMatrix())) {
            inventory.setResult(createTalismanItem());
            return;
        }

        ItemStack result = inventory.getResult();
        if (isTalisman(result)) {
            inventory.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory() instanceof CraftingInventory inventory)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;

        ItemStack current = event.getCurrentItem();
        if (!isTalisman(current) && !matchesRecipe(inventory.getMatrix())) {
            return;
        }

        event.setCancelled(true);
        if (!canReceiveCraftResult(player, event)) {
            return;
        }
        if (!consumeRecipeIngredients(inventory)) {
            player.sendMessage(MessageUtil.error("The talisman recipe ingredients were invalid."));
            return;
        }
        ItemStack result = createTalismanItem();
        if (plugin.getItemAuditManager() != null) {
            plugin.getItemAuditManager().recordKnownAcquisition(
                player,
                result,
                "talisman_craft",
                "Crafted a Talisman of Sustenance."
            );
        }
        giveCraftResult(player, event, result);

        inventory.setResult(null);
        player.updateInventory();
        refreshPlayerTalismans(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getFirstItem();
        ItemStack right = event.getInventory().getSecondItem();
        if (!isTalisman(left) && !isTalisman(right)) {
            return;
        }

        ItemStack source = isTalisman(left) ? left : right;
        ItemStack result = event.getResult();
        if (source == null || result == null || result.getType() == Material.AIR) {
            return;
        }

        event.setResult(preserveResult(result));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (!(event.getInventory() instanceof GrindstoneInventory grindstone)) {
            return;
        }

        ItemStack top = grindstone.getUpperItem();
        ItemStack bottom = grindstone.getLowerItem();
        if (!isTalisman(top) && !isTalisman(bottom)) {
            return;
        }

        ItemStack source = isTalisman(top) ? top : bottom;
        ItemStack result = event.getResult();
        if (source == null || result == null || result.getType() == Material.AIR) {
            return;
        }

        event.setResult(preserveResult(result));
    }

    private void tickTalismans() {
        long intervalMs = plugin.getConfigManager().sustenanceTalismanIntervalSeconds * 1000L;
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (player.isDead() || player.getGameMode() == org.bukkit.GameMode.SPECTATOR || !hasTalisman(player)) {
                nextPassiveTick.remove(playerId);
                continue;
            }

            long next = nextPassiveTick.getOrDefault(playerId, now + intervalMs);
            if (next > now) {
                nextPassiveTick.put(playerId, next);
                continue;
            }

            applyPassiveSustain(player);
            nextPassiveTick.put(playerId, now + intervalMs);
        }
    }

    private boolean hasTalisman(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isTalisman(item)) {
                return true;
            }
        }
        return isTalisman(player.getItemOnCursor());
    }

    private void applyPassiveSustain(Player player) {
        int hungerGain = plugin.getConfigManager().sustenanceTalismanHungerGain;
        if (hungerGain > 0 && player.getFoodLevel() < 20) {
            player.setFoodLevel(Math.min(20, player.getFoodLevel() + hungerGain));
        }

        double healAmount = plugin.getConfigManager().sustenanceTalismanHealHearts * 2.0;
        if (healAmount <= 0.0 || player.getHealth() <= 0.0) {
            return;
        }

        double maxHealth = 20.0;
        var maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttribute != null) {
            maxHealth = maxHealthAttribute.getValue();
        }
        if (player.getHealth() < maxHealth) {
            player.setHealth(Math.min(maxHealth, player.getHealth() + healAmount));
        }
    }

    private ItemStack preserveResult(ItemStack result) {
        ItemStack updated = new ItemStack(TALISMAN_BASE_MATERIAL, Math.max(1, Math.min(result.getAmount(), TALISMAN_BASE_MATERIAL.getMaxStackSize())));
        ItemMeta meta = updated.getItemMeta();
        if (meta == null) {
            return updated;
        }

        applyTalismanState(meta);
        applyTalismanPresentation(meta);
        updated.setItemMeta(meta);
        return updated;
    }

    private void refreshPlayerTalismans(Player player) {
        List<ItemStack> overflow = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack original = player.getInventory().getItem(slot);
            ItemStack normalized = normalizeTalismanItem(original);
            if (normalized != null) {
                player.getInventory().setItem(slot, normalized);
                collectOverflowTalismans(overflow, original);
            }
        }

        ItemStack cursor = player.getItemOnCursor();
        ItemStack normalizedCursor = normalizeTalismanItem(cursor);
        if (normalizedCursor != null) {
            player.setItemOnCursor(normalizedCursor);
            collectOverflowTalismans(overflow, cursor);
        }

        if (!overflow.isEmpty()) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(overflow.toArray(ItemStack[]::new));
            leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    private ItemStack normalizeTalismanItem(ItemStack item) {
        if (!isTalisman(item)) {
            return null;
        }

        ItemStack normalized = new ItemStack(TALISMAN_BASE_MATERIAL, 1);
        ItemMeta meta = normalized.getItemMeta();
        if (meta == null) {
            return normalized;
        }

        applyTalismanState(meta);
        applyTalismanPresentation(meta);
        normalized.setItemMeta(meta);
        return normalized;
    }

    private void applyTalismanState(ItemMeta meta) {
        meta.getPersistentDataContainer().set(keyTalismanId, PersistentDataType.STRING, ITEM_ID);
        meta.setItemModel(null);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        CustomLoreUtil.applyStyledItemFlags(meta);
    }

    private void applyTalismanPresentation(ItemMeta meta) {
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.EPIC, "Talisman of Sustenance"));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.TOTEM_OF_UNDYING,
            CustomLoreUtil.Rarity.EPIC.label(),
            "TALISMAN",
            List.of("<gray>Works anywhere in your inventory.</gray>"),
            List.of(CustomLoreUtil.section(
                "Passive",
                "Sustenance",
                "<gray>Restores <white>1 hunger</white> and <white>1 heart</white> every <white>"
                    + plugin.getConfigManager().sustenanceTalismanIntervalSeconds + "s</white>.</gray>"
            ))
        ));
    }

    private boolean matchesRecipe(ItemStack[] matrix) {
        Map<Material, Integer> provided = new EnumMap<>(Material.class);
        for (ItemStack item : matrix) {
            if (item == null || item.getType() == Material.AIR) continue;
            if (!isValidRecipeIngredient(item)) {
                return false;
            }
            provided.merge(item.getType(), item.getAmount(), Integer::sum);
        }

        if (provided.size() != RECIPE_INGREDIENTS.size()) {
            return false;
        }
        for (Map.Entry<Material, Integer> entry : RECIPE_INGREDIENTS.entrySet()) {
            if (!entry.getValue().equals(provided.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidRecipeIngredient(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return true;
        }
        if (!RECIPE_INGREDIENTS.containsKey(item.getType())) {
            return false;
        }
        return item.getType() != Material.TOTEM_OF_UNDYING || !isTalisman(item);
    }

    private boolean consumeRecipeIngredients(CraftingInventory inventory) {
        ItemStack[] matrix = inventory.getMatrix();
        if (!matchesRecipe(matrix)) {
            return false;
        }

        ItemStack[] nextMatrix = new ItemStack[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            nextMatrix[i] = matrix[i] == null ? null : matrix[i].clone();
        }

        for (Map.Entry<Material, Integer> entry : RECIPE_INGREDIENTS.entrySet()) {
            int remaining = entry.getValue();
            for (int i = 0; i < nextMatrix.length && remaining > 0; i++) {
                ItemStack item = nextMatrix[i];
                if (item == null || item.getType() != entry.getKey()) {
                    continue;
                }

                int take = Math.min(remaining, item.getAmount());
                int left = item.getAmount() - take;
                nextMatrix[i] = left <= 0 ? null : item.asQuantity(left);
                remaining -= take;
            }

            if (remaining > 0) {
                return false;
            }
        }

        inventory.setMatrix(nextMatrix);
        return true;
    }

    private boolean giveCraftResult(Player player, InventoryClickEvent event, ItemStack result) {
        if (event.isShiftClick()) {
            if (player.getInventory().firstEmpty() == -1) {
                player.sendMessage(MessageUtil.warn("You need at least one empty inventory slot."));
                return false;
            }
            player.getInventory().addItem(result);
            return true;
        }

        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            player.sendMessage(MessageUtil.warn("Your cursor must be empty."));
            return false;
        }

        player.setItemOnCursor(result);
        return true;
    }

    private boolean canReceiveCraftResult(Player player, InventoryClickEvent event) {
        if (event.isShiftClick()) {
            if (player.getInventory().firstEmpty() != -1) {
                return true;
            }
            player.sendMessage(MessageUtil.warn("You need at least one empty inventory slot."));
            return false;
        }

        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType() == Material.AIR) {
            return true;
        }
        player.sendMessage(MessageUtil.warn("Your cursor must be empty."));
        return false;
    }

    private boolean isRealTotem(ItemStack item) {
        return item != null && item.getType() == Material.TOTEM_OF_UNDYING && !isTalisman(item);
    }

    private void consumeOne(Player player, EquipmentSlot hand, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        ItemStack updated = item.getAmount() > 1 ? item.asQuantity(item.getAmount() - 1) : null;
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(updated);
        } else {
            player.getInventory().setItemInMainHand(updated);
        }
    }

    private void completeTotemFallbackResurrection(Player player, EquipmentSlot talismanHand, EquipmentSlot preferredTotemHand, ItemStack originalTalisman) {
        if (player == null || !player.isOnline() || player.isDead() || originalTalisman == null || !isTalisman(originalTalisman)) {
            return;
        }

        if (!removeOneRealTotem(player, preferredTotemHand)) {
            return;
        }

        restoreTalismanAfterResurrection(player, talismanHand, originalTalisman);
    }

    private void restoreTalismanAfterResurrection(Player player, EquipmentSlot hand, ItemStack original) {
        if (player == null || !player.isOnline() || original == null || !isTalisman(original)) {
            return;
        }

        ItemStack restored = original.clone();
        ItemMeta meta = restored.getItemMeta();
        if (meta != null) {
            applyTalismanState(meta);
            applyTalismanPresentation(meta);
            restored.setItemMeta(meta);
        }

        ItemStack current = hand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
        if (current == null || current.getType() == Material.AIR) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(restored);
            } else {
                player.getInventory().setItemInMainHand(restored);
            }
        } else {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(restored);
            leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        player.updateInventory();
    }

    private boolean removeOneRealTotem(Player player, EquipmentSlot preferredHand) {
        if (preferredHand == EquipmentSlot.OFF_HAND && isRealTotem(player.getInventory().getItemInOffHand())) {
            consumeOne(player, EquipmentSlot.OFF_HAND, player.getInventory().getItemInOffHand());
            return true;
        }
        if (preferredHand == EquipmentSlot.HAND && isRealTotem(player.getInventory().getItemInMainHand())) {
            consumeOne(player, EquipmentSlot.HAND, player.getInventory().getItemInMainHand());
            return true;
        }

        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (!isRealTotem(item)) {
                continue;
            }

            ItemStack updated = item.getAmount() > 1 ? item.asQuantity(item.getAmount() - 1) : null;
            player.getInventory().setItem(slot, updated);
            return true;
        }
        return false;
    }

    private void collectOverflowTalismans(List<ItemStack> overflow, ItemStack original) {
        if (!isTalisman(original)) {
            return;
        }

        for (int i = 1; i < original.getAmount(); i++) {
            overflow.add(createTalismanItem());
        }
    }
}
