package me.rique.smpcore.backpack;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom backpack system based on bundles.
 * Only PDC-marked backpacks use custom behavior; normal bundles remain vanilla.
 */
public final class BackpackListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int BACKPACK_SIZE = 27;
    private static final Map<Material, Integer> BACKPACK_INGREDIENTS = Map.of(
        Material.LEATHER, 4,
        Material.STRING, 4,
        Material.CHEST, 1
    );

    private final SMPCore plugin;
    private final NamespacedKey backpackFlagKey;
    private final NamespacedKey backpackIdKey;
    private final NamespacedKey backpackDataKey;
    private final NamespacedKey backpackRecipeKey;

    private final Map<UUID, OpenBackpackSession> openBackpacks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> warnCooldown = new ConcurrentHashMap<>();

    public BackpackListener(SMPCore plugin) {
        this.plugin = plugin;
        this.backpackFlagKey = new NamespacedKey(plugin, "backpack_flag");
        this.backpackIdKey = new NamespacedKey(plugin, "backpack_id");
        this.backpackDataKey = new NamespacedKey(plugin, "backpack_data");
        this.backpackRecipeKey = new NamespacedKey(plugin, "backpack_bundle_recipe");
        Bukkit.removeRecipe(backpackRecipeKey);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        if (matchesBackpackIngredients(inv.getMatrix())) {
            inv.setResult(null);
            return;
        }
        if (event.getRecipe() instanceof Keyed keyed && backpackRecipeKey.equals(keyed.getKey())) {
            inv.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUseBackpack(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) return;

        ItemStack held = hand == EquipmentSlot.HAND
            ? player.getInventory().getItemInMainHand()
            : player.getInventory().getItemInOffHand();
        if (!isBackpack(held)) return;

        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);

        if (!player.hasPermission("smpcore.backpack.use")) {
            player.sendMessage(MessageUtil.error("You do not have permission to use backpacks."));
            return;
        }

        int slot = hand == EquipmentSlot.HAND ? player.getInventory().getHeldItemSlot() : 40;
        openBackpack(player, slot);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (handleBackpackCraftClick(event, player)) {
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof BackpackHolder) {
            handleBackpackMenuClick(event, player);
            return;
        }

        if (!involvesBackpack(event.getCurrentItem(), event.getCursor())) return;
        if (!isBackpackAction(event)) return;

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (event.getClickedInventory() == player.getInventory()
            && isBackpack(current)
            && event.isRightClick()) {
            event.setCancelled(true);

            int recovered = recoverVanillaBundleContents(player, current);
            event.setCurrentItem(current);
            if (!isEmpty(cursor)) {
                player.updateInventory();
                maybeWarn(
                    player,
                    recovered > 0
                        ? "Backpacks must be opened with an empty cursor. Trapped items were returned."
                        : "Backpacks must be opened with an empty cursor."
                );
                return;
            }

            openBackpack(player, event.getSlot());
            return;
        }

        if (isBackpack(cursor)) {
            event.setCancelled(true);
            int recovered = recoverVanillaBundleContents(player, cursor);
            player.setItemOnCursor(cursor);
            player.updateInventory();
            maybeWarn(
                player,
                recovered > 0
                    ? "Backpacks cannot be used while held on your cursor. Trapped items were returned."
                    : "Backpacks cannot be used while held on your cursor."
            );
            return;
        }

        if (event.isRightClick()) {
            event.setCancelled(true);
            int recovered = recoverVanillaBundleContents(player, current);
            if (recovered > 0) {
                event.setCurrentItem(current);
                player.updateInventory();
                maybeWarn(player, "Recovered items that were trapped in the backpack.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getView().getTopInventory().getHolder() instanceof BackpackHolder) {
            OpenBackpackSession session = openBackpacks.get(player.getUniqueId());
            if (session == null) {
                event.setCancelled(true);
                return;
            }

            int topSize = event.getView().getTopInventory().getSize();
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < topSize && isBundle(event.getOldCursor())) {
                    event.setCancelled(true);
                    maybeWarn(player, "Bundles cannot be stored inside backpacks.");
                    return;
                }
                if (rawSlot >= topSize && event.getView().convertSlot(rawSlot) == session.sourceSlot()) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }

        // Only custom backpacks suppress vanilla drag behavior.
        if (isBackpack(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof BackpackHolder)) return;

        OpenBackpackSession session = openBackpacks.remove(player.getUniqueId());
        if (session == null) return;
        persistBackpack(player, session, event.getInventory());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        OpenBackpackSession session = openBackpacks.get(event.getPlayer().getUniqueId());
        if (session == null) return;
        if (!hasBackpackId(event.getItemDrop().getItemStack(), session.backpackId())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        OpenBackpackSession session = openBackpacks.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            persistBackpack(event.getPlayer(), session, session.inventory());
        }
        warnCooldown.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        for (Map.Entry<UUID, OpenBackpackSession> entry : List.copyOf(openBackpacks.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;

            OpenBackpackSession session = openBackpacks.remove(entry.getKey());
            if (session == null) continue;
            persistBackpack(player, session, session.inventory());
        }
        warnCooldown.clear();
    }

    private void handleBackpackMenuClick(InventoryClickEvent event, Player player) {
        OpenBackpackSession session = openBackpacks.get(player.getUniqueId());
        if (session == null) {
            event.setCancelled(true);
            return;
        }

        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;

        if (clicked == player.getInventory()) {
            if (event.getSlot() == session.sourceSlot()) {
                event.setCancelled(true);
                return;
            }
            if (event.isShiftClick() && isBundle(event.getCurrentItem())) {
                event.setCancelled(true);
                maybeWarn(player, "Bundles cannot be stored inside backpacks.");
                return;
            }
            if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() == session.sourceSlot()) {
                event.setCancelled(true);
                return;
            }
            if ((event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP)
                && event.getSlot() == session.sourceSlot()) {
                event.setCancelled(true);
                return;
            }
            return;
        }

        if (clicked == top) {
            if (isBundle(event.getCursor())) {
                event.setCancelled(true);
                maybeWarn(player, "Bundles cannot be stored inside backpacks.");
                return;
            }
            if (event.getClick() == ClickType.NUMBER_KEY) {
                ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
                if (isBundle(hotbarItem)) {
                    event.setCancelled(true);
                    maybeWarn(player, "Bundles cannot be stored inside backpacks.");
                    return;
                }
            }
            if (event.getClick() == ClickType.SWAP_OFFHAND && isBundle(player.getInventory().getItemInOffHand())) {
                event.setCancelled(true);
                maybeWarn(player, "Bundles cannot be stored inside backpacks.");
                return;
            }
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && isBundle(event.getCurrentItem())) {
                event.setCancelled(true);
                maybeWarn(player, "Bundles cannot be stored inside backpacks.");
            }
        }
    }

    private void openBackpack(Player player, int sourceSlot) {
        if (openBackpacks.containsKey(player.getUniqueId())) return;
        ItemStack source = player.getInventory().getItem(sourceSlot);
        if (!isBackpack(source)) return;

        if (recoverVanillaBundleContents(player, source) > 0) {
            player.getInventory().setItem(sourceSlot, source);
            maybeWarn(player, "Recovered items that were trapped in the backpack.");
        }

        ItemMeta sourceMeta = source.getItemMeta();
        if (sourceMeta == null) return;

        String backpackId = sourceMeta.getPersistentDataContainer().get(backpackIdKey, PersistentDataType.STRING);
        if (backpackId == null || backpackId.isBlank()) {
            backpackId = UUID.randomUUID().toString();
            sourceMeta.getPersistentDataContainer().set(backpackIdKey, PersistentDataType.STRING, backpackId);
            source.setItemMeta(sourceMeta);
        }

        byte[] raw = sourceMeta.getPersistentDataContainer().get(backpackDataKey, PersistentDataType.BYTE_ARRAY);
        Inventory inv = Bukkit.createInventory(new BackpackHolder(), BACKPACK_SIZE, Component.text("Backpack"));
        inv.setContents(deserialize(raw, BACKPACK_SIZE));

        openBackpacks.put(player.getUniqueId(), new OpenBackpackSession(backpackId, sourceSlot, inv));
        player.openInventory(inv);
    }

    private void persistBackpack(Player player, OpenBackpackSession session, Inventory inventory) {
        int slot = findBackpackSlot(player, session.backpackId(), session.sourceSlot());
        if (slot < 0) {
            dropContents(player, inventory.getContents());
            player.sendMessage(MessageUtil.error("Backpack moved while open. Contents were dropped on the ground."));
            return;
        }

        ItemStack stack = player.getInventory().getItem(slot);
        if (!isBackpack(stack)) {
            dropContents(player, inventory.getContents());
            player.sendMessage(MessageUtil.error("Backpack missing. Contents were dropped on the ground."));
            return;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        if (meta instanceof BundleMeta bundleMeta) {
            bundleMeta.setItems(List.of());
            meta = bundleMeta;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(backpackFlagKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(backpackIdKey, PersistentDataType.STRING, session.backpackId());
        pdc.set(backpackDataKey, PersistentDataType.BYTE_ARRAY, serialize(inventory.getContents()));
        stack.setItemMeta(meta);
        player.getInventory().setItem(slot, stack);
    }

    private int findBackpackSlot(Player player, String backpackId, int preferredSlot) {
        ItemStack preferred = player.getInventory().getItem(preferredSlot);
        if (hasBackpackId(preferred, backpackId)) return preferredSlot;

        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (hasBackpackId(contents[i], backpackId)) return i;
        }
        return -1;
    }

    private boolean involvesBackpack(ItemStack current, ItemStack cursor) {
        return isBackpack(current) || isBackpack(cursor);
    }

    private boolean isBackpackAction(InventoryClickEvent event) {
        if (!involvesBackpack(event.getCurrentItem(), event.getCursor())) return false;
        return event.isRightClick();
    }

    private static boolean isBundle(ItemStack item) {
        return item != null && item.getType() == Material.BUNDLE;
    }

    private boolean isBackpack(ItemStack item) {
        if (!isBundle(item)) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Byte flag = meta.getPersistentDataContainer().get(backpackFlagKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    public Map<Material, Integer> tradeIngredients() {
        return BACKPACK_INGREDIENTS;
    }

    public boolean canTradeBackpack(Player player) {
        for (Map.Entry<Material, Integer> entry : BACKPACK_INGREDIENTS.entrySet()) {
            if (countTradeMaterial(player, entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public boolean tradeBackpack(Player player) {
        if (!removeTradeMaterials(player, BACKPACK_INGREDIENTS)) {
            player.sendMessage(MessageUtil.error("You do not have all the materials for a backpack."));
            return false;
        }

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(createBackpackItem());
        leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.sendMessage(MessageUtil.success("Traded materials for a <white>Backpack</white>."));
        return true;
    }

    private boolean hasBackpackId(ItemStack item, String expectedId) {
        if (!isBackpack(item) || expectedId == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        String id = meta.getPersistentDataContainer().get(backpackIdKey, PersistentDataType.STRING);
        return expectedId.equals(id);
    }

    private ItemStack createBackpackItem() {
        ItemStack item = new ItemStack(Material.BUNDLE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        if (meta instanceof BundleMeta bundleMeta) {
            bundleMeta.setItems(List.of());
            meta = bundleMeta;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(backpackFlagKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(backpackIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        pdc.set(backpackDataKey, PersistentDataType.BYTE_ARRAY, new byte[0]);

        meta.displayName(MM.deserialize("<gold><bold>Backpack</bold></gold>"));
        meta.lore(List.of(
            MM.deserialize("<dark_gray>Portable Storage</dark_gray>"),
            MM.deserialize("<gray>Right-click to open.</gray>"),
            MM.deserialize("<gray>Normal bundles still work normally.</gray>")
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void maybeWarn(Player player, String message) {
        long now = System.currentTimeMillis();
        long last = warnCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 1000L) return;
        warnCooldown.put(player.getUniqueId(), now);
        player.sendMessage(MessageUtil.warn(message));
    }

    private int recoverVanillaBundleContents(Player player, ItemStack backpack) {
        if (!isBackpack(backpack)) return 0;

        ItemMeta meta = backpack.getItemMeta();
        if (!(meta instanceof BundleMeta bundleMeta) || !bundleMeta.hasItems()) {
            return 0;
        }

        List<ItemStack> recovered = bundleMeta.getItems().stream()
            .filter(item -> item != null && item.getType() != Material.AIR && item.getAmount() > 0)
            .map(ItemStack::clone)
            .toList();
        bundleMeta.setItems(List.of());
        backpack.setItemMeta(bundleMeta);

        for (ItemStack item : recovered) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        return recovered.size();
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    private void dropContents(Player player, ItemStack[] contents) {
        if (player.getWorld() == null || contents == null) return;
        for (ItemStack item : contents) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) continue;
            player.getWorld().dropItemNaturally(player.getLocation(), item.clone());
        }
    }

    private byte[] serialize(ItemStack[] contents) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(baos)) {
            out.writeInt(contents.length);
            for (ItemStack item : contents) {
                if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
                    out.writeInt(0);
                    continue;
                }
                byte[] raw = item.serializeAsBytes();
                out.writeInt(raw.length);
                out.write(raw);
            }
            out.flush();
            return baos.toByteArray();
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to serialize backpack data: " + ex.getMessage());
            return new byte[0];
        }
    }

    private ItemStack[] deserialize(byte[] data, int size) {
        ItemStack[] out = new ItemStack[size];
        if (data == null || data.length == 0) return out;

        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             DataInputStream in = new DataInputStream(bais)) {
            int stored = in.readInt();
            for (int i = 0; i < stored; i++) {
                int length = in.readInt();
                if (length < 0) {
                    throw new IOException("Negative backpack item length");
                }
                if (length == 0) continue;

                byte[] raw = in.readNBytes(length);
                if (raw.length != length) {
                    throw new IOException("Unexpected end of backpack data");
                }

                if (i < size) {
                    out[i] = ItemStack.deserializeBytes(raw);
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Backpack data was invalid and has been reset: " + ex.getMessage());
        }
        return out;
    }

    private static boolean matchesBackpackIngredients(ItemStack[] matrix) {
        Map<Material, Integer> provided = new EnumMap<>(Material.class);
        for (ItemStack item : matrix) {
            if (item == null || item.getType() == Material.AIR) continue;
            provided.merge(item.getType(), item.getAmount(), Integer::sum);
        }
        if (provided.size() != BACKPACK_INGREDIENTS.size()) return false;
        for (Map.Entry<Material, Integer> entry : BACKPACK_INGREDIENTS.entrySet()) {
            if (!entry.getValue().equals(provided.get(entry.getKey()))) return false;
        }
        return true;
    }

    private static void clearCustomCraftState(CraftingInventory inv) {
        inv.setResult(null);
    }

    private boolean handleBackpackCraftClick(InventoryClickEvent event, Player player) {
        if (event.getClickedInventory() == null) return false;
        if (!(event.getView().getTopInventory() instanceof CraftingInventory inv)) return false;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return false;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return false;

        ItemStack current = event.getCurrentItem();
        if (!matchesBackpackIngredients(inv.getMatrix())
            && !(current != null && current.getType() == Material.BUNDLE && isBackpack(current))) {
            return false;
        }

        event.setCancelled(true);
        clearCustomCraftState(inv);
        player.updateInventory();
        player.sendMessage(MessageUtil.info("Use <white>/lrecipe</white> to trade materials for a backpack."));
        return true;
    }

    private int countTradeMaterial(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() != material) continue;
            count += item.getAmount();
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() == material) {
            count += offhand.getAmount();
        }
        return count;
    }

    private boolean removeTradeMaterials(Player player, Map<Material, Integer> required) {
        ItemStack[] storage = player.getInventory().getStorageContents().clone();
        ItemStack offhand = player.getInventory().getItemInOffHand();
        ItemStack nextOffhand = offhand == null ? null : offhand.clone();

        for (Map.Entry<Material, Integer> entry : required.entrySet()) {
            int remaining = entry.getValue();

            for (int i = 0; i < storage.length && remaining > 0; i++) {
                ItemStack item = storage[i];
                if (item == null || item.getType() != entry.getKey()) continue;

                int take = Math.min(remaining, item.getAmount());
                int left = item.getAmount() - take;
                storage[i] = left <= 0 ? null : item.asQuantity(left);
                remaining -= take;
            }

            if (remaining > 0 && nextOffhand != null && nextOffhand.getType() == entry.getKey()) {
                int take = Math.min(remaining, nextOffhand.getAmount());
                int left = nextOffhand.getAmount() - take;
                nextOffhand = left <= 0 ? null : nextOffhand.asQuantity(left);
                remaining -= take;
            }

            if (remaining > 0) {
                return false;
            }
        }

        player.getInventory().setStorageContents(storage);
        player.getInventory().setItemInOffHand(nextOffhand);
        return true;
    }

    private record OpenBackpackSession(String backpackId, int sourceSlot, Inventory inventory) {}

    private record BackpackHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
