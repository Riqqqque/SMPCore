package me.rique.smpcore.backpack;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.InventoryRecipeUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom backpack system backed by a tagged flower pot item.
 * Older backpacks are normalized forward when touched.
 */
public final class BackpackListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final int BACKPACK_SIZE = 27;
    private static final int UPGRADED_BACKPACK_SIZE = 54;
    private static final Map<Material, Integer> BACKPACK_INGREDIENTS = Map.of(
        Material.LEATHER, 4,
        Material.STRING, 4,
        Material.CHEST, 1
    );
    private static final Map<Material, Integer> UPGRADED_BACKPACK_INGREDIENTS = Map.of(
        Material.LEATHER, 16,
        Material.DIAMOND, 8
    );

    private final SMPCore plugin;
    private final NamespacedKey backpackFlagKey;
    private final NamespacedKey backpackIdKey;
    private final NamespacedKey backpackDataKey;
    private final NamespacedKey backpackSizeKey;
    private final NamespacedKey backpackTierKey;
    private final NamespacedKey backpackRecipeKey;
    private final NamespacedKey menuPreviewKey;
    private final Map<UUID, OpenBackpackSession> openBackpacks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> warnCooldown = new ConcurrentHashMap<>();

    public BackpackListener(SMPCore plugin) {
        this.plugin = plugin;
        this.backpackFlagKey = new NamespacedKey(plugin, "backpack_flag");
        this.backpackIdKey = new NamespacedKey(plugin, "backpack_id");
        this.backpackDataKey = new NamespacedKey(plugin, "backpack_data");
        this.backpackSizeKey = new NamespacedKey(plugin, "backpack_size");
        this.backpackTierKey = new NamespacedKey(plugin, "backpack_tier");
        this.backpackRecipeKey = new NamespacedKey(plugin, "backpack_recipe");
        this.menuPreviewKey = new NamespacedKey(plugin, "menu_preview_item");
        Bukkit.removeRecipe(backpackRecipeKey);
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::migratePlayerBackpacks));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        BackpackUpgradeCraft upgradeCraft = matchBackpackUpgrade(inv.getMatrix());
        if (upgradeCraft != null) {
            inv.setResult(createUpgradedBackpackFrom(upgradeCraft.backpack()));
            return;
        }
        if (containsBackpack(inv.getMatrix())) {
            inv.setResult(null);
            return;
        }
        if (matchesBackpackIngredients(inv.getMatrix())) {
            inv.setResult(null);
            return;
        }
        if (event.getRecipe() instanceof Keyed keyed && backpackRecipeKey.equals(keyed.getKey())) {
            inv.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCraftBackpack(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        handleBackpackCraftClick(event, player);
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
        migrateBackpackSlot(player, player.getInventory(), slot);
        openBackpack(player, slot);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlaceBackpack(BlockPlaceEvent event) {
        if (!isBackpack(event.getItemInHand())) {
            return;
        }
        event.setCancelled(true);
        maybeWarn(event.getPlayer(), "Backpacks cannot be placed.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        migratePlayerBackpacks(event.getPlayer());
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

        ItemStack current = event.getCurrentItem();
        if (event.getClickedInventory() == player.getInventory()
            && isBackpack(current)
            && event.isRightClick()) {
            event.setCancelled(true);
            migrateBackpackSlot(player, player.getInventory(), event.getSlot());
            openBackpack(player, event.getSlot());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        OpenBackpackSession session = openBackpacks.remove(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }

        if (event.getKeepInventory()) {
            persistBackpack(event.getPlayer(), session, session.inventory());
            return;
        }

        if (!syncOpenBackpackToDeathDrops(event.getPlayer(), event.getDrops(), session)) {
            plugin.getLogger().warning("Backpack session could not be safely reconciled on death for " + event.getPlayer().getName() + ".");
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
                if (rawSlot < topSize && isBackpack(event.getOldCursor())) {
                    event.setCancelled(true);
                    maybeWarn(player, "Backpacks cannot be stored inside backpacks.");
                    return;
                }
                if (rawSlot >= topSize && event.getView().convertSlot(rawSlot) == session.sourceSlot()) {
                    event.setCancelled(true);
                    return;
                }
            }
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
    public void onKick(PlayerKickEvent event) {
        finishOpenBackpack(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        finishOpenBackpack(event.getPlayer());
        warnCooldown.remove(event.getPlayer().getUniqueId());
    }

    private void finishOpenBackpack(Player player) {
        OpenBackpackSession session = openBackpacks.remove(player.getUniqueId());
        if (session != null) {
            persistBackpack(player, session, session.inventory());
        }
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
            if (event.isShiftClick() && isBackpack(event.getCurrentItem())) {
                event.setCancelled(true);
                maybeWarn(player, "Backpacks cannot be stored inside backpacks.");
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
            if (isUnsafeStorageCloneClick(event)) {
                event.setCancelled(true);
                return;
            }
            if (isBackpack(event.getCursor())) {
                event.setCancelled(true);
                maybeWarn(player, "Backpacks cannot be stored inside backpacks.");
                return;
            }
            if (event.getClick() == ClickType.NUMBER_KEY) {
                ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
                if (isBackpack(hotbarItem)) {
                    event.setCancelled(true);
                    maybeWarn(player, "Backpacks cannot be stored inside backpacks.");
                    return;
                }
            }
            if (event.getClick() == ClickType.SWAP_OFFHAND && isBackpack(player.getInventory().getItemInOffHand())) {
                event.setCancelled(true);
                maybeWarn(player, "Backpacks cannot be stored inside backpacks.");
                return;
            }
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && isBackpack(event.getCurrentItem())) {
                event.setCancelled(true);
                maybeWarn(player, "Backpacks cannot be stored inside backpacks.");
            }
        }
    }

    private boolean isUnsafeStorageCloneClick(InventoryClickEvent event) {
        return event.getAction() == InventoryAction.CLONE_STACK
            || event.getAction() == InventoryAction.UNKNOWN
            || event.getClick() == ClickType.CREATIVE
            || event.getClick() == ClickType.MIDDLE
            || event.getClick() == ClickType.UNKNOWN;
    }

    private void openBackpack(Player player, int sourceSlot) {
        if (openBackpacks.containsKey(player.getUniqueId())) return;
        ItemStack source = migrateBackpackSlot(player, player.getInventory(), sourceSlot);
        if (!isBackpack(source)) return;

        ItemMeta sourceMeta = source.getItemMeta();
        if (sourceMeta == null) return;

        String backpackId = sourceMeta.getPersistentDataContainer().get(backpackIdKey, PersistentDataType.STRING);
        if (backpackId == null || backpackId.isBlank()) {
            backpackId = UUID.randomUUID().toString();
            sourceMeta.getPersistentDataContainer().set(backpackIdKey, PersistentDataType.STRING, backpackId);
            source.setItemMeta(sourceMeta);
        }

        byte[] raw = sourceMeta.getPersistentDataContainer().get(backpackDataKey, PersistentDataType.BYTE_ARRAY);
        int size = backpackSize(source);
        Inventory inv = Bukkit.createInventory(
            new BackpackHolder(),
            size,
            Component.text(size > BACKPACK_SIZE ? "Expanded Backpack" : "Backpack")
        );
        ItemStack[] contents = deserialize(raw, size);
        List<ItemStack> removedBackpacks = stripNestedBackpacks(contents);
        inv.setContents(contents);

        if (!removedBackpacks.isEmpty()) {
            if (writeBackpackData(source, backpackId, contents)) {
                player.getInventory().setItem(sourceSlot, source);
            } else {
                plugin.getLogger().warning("Could not persist sanitized nested backpack contents for " + player.getName() + ".");
                maybeWarn(player, "Nested backpacks were found, but could not be removed safely. Try again in a moment.");
                return;
            }
            returnBackpackOverflow(player, removedBackpacks);
            maybeWarn(player, "Nested backpacks were removed to prevent duplicated storage.");
        }

        openBackpacks.put(player.getUniqueId(), new OpenBackpackSession(backpackId, sourceSlot, inv));
        player.openInventory(inv);
    }

    private void persistBackpack(Player player, OpenBackpackSession session, Inventory inventory) {
        ItemStack[] sanitizedContents = cloneContents(inventory.getContents());
        List<ItemStack> removedBackpacks = stripNestedBackpacks(sanitizedContents);
        int slot = findBackpackSlot(player, session.backpackId(), session.sourceSlot());
        if (slot < 0) {
            returnBackpackOverflow(player, removedBackpacks);
            plugin.getLogger().warning("Skipped unsafe backpack persistence because the source item went missing for " + player.getName() + ".");
            player.sendMessage(MessageUtil.error("Backpack could not be saved safely because the item moved while open."));
            return;
        }

        ItemStack stack = player.getInventory().getItem(slot);
        if (!isBackpack(stack)) {
            returnBackpackOverflow(player, removedBackpacks);
            plugin.getLogger().warning("Skipped unsafe backpack persistence because the source item was no longer valid for " + player.getName() + ".");
            player.sendMessage(MessageUtil.error("Backpack could not be saved safely because the item changed while open."));
            return;
        }

        if (!writeBackpackData(stack, session.backpackId(), sanitizedContents)) {
            returnBackpackOverflow(player, removedBackpacks);
            player.sendMessage(MessageUtil.error("Backpack could not be saved safely because the item metadata was invalid."));
            return;
        }

        player.getInventory().setItem(slot, stack);
        returnBackpackOverflow(player, removedBackpacks);
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

    public boolean isBackpack(ItemStack item) {
        if (item == null || !isBackpackCarrier(item.getType())) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(menuPreviewKey, PersistentDataType.BYTE)) {
            return false;
        }
        if (hasRecipeMenuHint(meta)) {
            return false;
        }
        return hasBackpackFlag(pdc) || hasLegacyBackpackSignature(meta);
    }

    private boolean isBackpackCarrier(Material material) {
        return material == Material.FLOWER_POT || material == Material.MINECART;
    }

    public Map<Material, Integer> tradeIngredients() {
        return BACKPACK_INGREDIENTS;
    }

    public Map<Material, Integer> upgradedTradeIngredients() {
        return UPGRADED_BACKPACK_INGREDIENTS;
    }

    public boolean isBackpackUpgradeCraft(ItemStack[] matrix) {
        return matchBackpackUpgrade(matrix) != null;
    }

    public boolean isExpandedBackpack(ItemStack item) {
        return isUpgradedBackpack(item);
    }

    public String backpackDisplayName(ItemStack item) {
        return isExpandedBackpack(item) ? "Expanded Backpack" : "Backpack";
    }

    public boolean canTradeBackpack(Player player) {
        return InventoryRecipeUtil.hasPlainMaterials(plugin, player, BACKPACK_INGREDIENTS)
            && InventoryRecipeUtil.canFitRewardAfterRemovingIngredients(
                player,
                InventoryRecipeUtil.plainMaterials(plugin, BACKPACK_INGREDIENTS),
                createNewBackpack()
            );
    }

    public boolean tradeBackpack(Player player) {
        if (!InventoryRecipeUtil.canFitRewardAfterRemovingIngredients(
            player,
            InventoryRecipeUtil.plainMaterials(plugin, BACKPACK_INGREDIENTS),
            createNewBackpack()
        )) {
            player.sendMessage(MessageUtil.warn("Clear enough inventory space before trading for a Backpack."));
            return false;
        }
        if (!removeTradeMaterials(player, BACKPACK_INGREDIENTS)) {
            player.sendMessage(MessageUtil.error("You do not have all the materials for a backpack."));
            return false;
        }

        ItemStack backpack = createNewBackpack();
        if (plugin.getItemAuditManager() != null) {
            plugin.getItemAuditManager().recordKnownAcquisition(
                player,
                backpack,
                "backpack_trade",
                "Traded materials for a Backpack."
            );
        }
        player.getInventory().addItem(backpack);
        player.sendMessage(MessageUtil.success("Traded materials for a <white>Backpack</white>."));
        return true;
    }

    public boolean canTradeUpgradedBackpack(Player player) {
        return findUpgradeableBackpackSlot(player) >= 0
            && InventoryRecipeUtil.hasPlainMaterials(plugin, player, UPGRADED_BACKPACK_INGREDIENTS);
    }

    public boolean tradeUpgradedBackpack(Player player) {
        if (openBackpacks.containsKey(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("Close your open backpack before upgrading it."));
            return false;
        }

        int sourceSlot = findUpgradeableBackpackSlot(player);
        if (sourceSlot < 0) {
            player.sendMessage(MessageUtil.error("You need a normal Backpack to upgrade."));
            return false;
        }
        if (!InventoryRecipeUtil.hasPlainMaterials(plugin, player, UPGRADED_BACKPACK_INGREDIENTS)) {
            player.sendMessage(MessageUtil.error("You do not have all the materials for an Expanded Backpack."));
            return false;
        }

        migrateBackpackSlot(player, player.getInventory(), sourceSlot);
        ItemStack source = player.getInventory().getItem(sourceSlot);
        if (!isUpgradeableBackpack(source)) {
            player.sendMessage(MessageUtil.error("That backpack could not be upgraded safely."));
            return false;
        }
        ItemStack upgraded = createUpgradedBackpackFrom(source);
        if (!isBackpack(upgraded) || !isUpgradedBackpack(upgraded)) {
            player.sendMessage(MessageUtil.error("That backpack could not be upgraded safely."));
            return false;
        }

        if (!removeTradeMaterials(player, UPGRADED_BACKPACK_INGREDIENTS)) {
            player.sendMessage(MessageUtil.error("You do not have all the materials for an Expanded Backpack."));
            return false;
        }

        if (plugin.getItemAuditManager() != null) {
            plugin.getItemAuditManager().recordKnownAcquisition(
                player,
                upgraded,
                "backpack_upgrade",
                "Upgraded a Backpack into an Expanded Backpack."
            );
        }
        player.getInventory().setItem(sourceSlot, upgraded);
        player.sendMessage(MessageUtil.success("Upgraded your Backpack into an <white>Expanded Backpack</white>."));
        return true;
    }

    public ItemStack createNewBackpack() {
        return createBackpackItem();
    }

    public ItemStack createNewUpgradedBackpack() {
        return createBackpackItem(UPGRADED_BACKPACK_SIZE);
    }

    public List<ItemStack> auditContents(Player owner, ItemStack backpack) {
        if (!isBackpack(backpack)) {
            return List.of();
        }

        ItemMeta meta = backpack.getItemMeta();
        if (meta == null) {
            return List.of();
        }

        String backpackId = meta.getPersistentDataContainer().get(backpackIdKey, PersistentDataType.STRING);
        if (backpackId == null || backpackId.isBlank()) {
            return List.of();
        }

        OpenBackpackSession openSession = owner == null ? null : openBackpacks.get(owner.getUniqueId());
        if (openSession != null && backpackId.equals(openSession.backpackId())) {
            return Arrays.asList(cloneContents(openSession.inventory().getContents()));
        }

        byte[] raw = meta.getPersistentDataContainer().get(backpackDataKey, PersistentDataType.BYTE_ARRAY);
        if (raw == null || raw.length == 0) {
            return List.of();
        }

        ItemStack[] contents = deserialize(raw, backpackSize(backpack));
        return Arrays.asList(cloneContents(contents));
    }

    public boolean rewriteAuditContents(ItemStack backpack, ItemStack[] contents) {
        if (!isBackpack(backpack)) {
            return false;
        }
        ItemMeta meta = backpack.getItemMeta();
        if (meta == null) {
            return false;
        }

        String backpackId = meta.getPersistentDataContainer().get(backpackIdKey, PersistentDataType.STRING);
        if (backpackId == null || backpackId.isBlank()) {
            backpackId = UUID.randomUUID().toString();
        }
        return writeBackpackData(backpack, backpackId, contents == null ? new ItemStack[0] : cloneContents(contents));
    }

    private boolean hasBackpackId(ItemStack item, String expectedId) {
        if (!isBackpack(item) || expectedId == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        String id = meta.getPersistentDataContainer().get(backpackIdKey, PersistentDataType.STRING);
        return expectedId.equals(id);
    }

    private boolean isTaggedBackpack(ItemStack item) {
        if (item == null || !isBackpackCarrier(item.getType())) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return !pdc.has(menuPreviewKey, PersistentDataType.BYTE) && hasBackpackFlag(pdc);
    }

    private ItemStack createBackpackItem() {
        return createBackpackItem(BACKPACK_SIZE);
    }

    private ItemStack createBackpackItem(int size) {
        ItemStack item = new ItemStack(Material.FLOWER_POT);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        int normalizedSize = normalizeBackpackSize(size);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(backpackFlagKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(backpackIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        pdc.set(backpackDataKey, PersistentDataType.BYTE_ARRAY, serialize(new ItemStack[normalizedSize]));
        pdc.set(backpackSizeKey, PersistentDataType.INTEGER, normalizedSize);
        pdc.set(backpackTierKey, PersistentDataType.STRING, backpackTierName(normalizedSize));
        applyBackpackPresentation(meta, normalizedSize);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createUpgradedBackpackFrom(ItemStack source) {
        if (!isBackpack(source)) {
            return null;
        }
        ItemMeta meta = source.getItemMeta();
        if (meta == null) {
            return null;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String backpackId = pdc.get(backpackIdKey, PersistentDataType.STRING);
        if (backpackId == null || backpackId.isBlank()) {
            backpackId = UUID.randomUUID().toString();
        }
        byte[] data = pdc.get(backpackDataKey, PersistentDataType.BYTE_ARRAY);
        return createNormalizedBackpack(backpackId, data == null ? new byte[0] : data, UPGRADED_BACKPACK_SIZE);
    }

    private void maybeWarn(Player player, String message) {
        long now = System.currentTimeMillis();
        long last = warnCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 1000L) return;
        warnCooldown.put(player.getUniqueId(), now);
        player.sendMessage(MessageUtil.warn(message));
    }

    private void dropContents(Player player, ItemStack[] contents) {
        if (player.getWorld() == null || contents == null) return;
        for (ItemStack item : contents) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) continue;
            player.getWorld().dropItemNaturally(player.getLocation(), item.clone());
        }
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] cloned = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            cloned[i] = contents[i] == null ? null : contents[i].clone();
        }
        return cloned;
    }

    private List<ItemStack> stripNestedBackpacks(ItemStack[] contents) {
        List<ItemStack> removed = new ArrayList<>();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (!isBackpack(item)) {
                continue;
            }
            removed.add(item.clone());
            contents[i] = null;
        }
        return removed;
    }

    private void returnBackpackOverflow(Player player, List<ItemStack> removedBackpacks) {
        if (removedBackpacks.isEmpty()) {
            return;
        }

        for (ItemStack item : removedBackpacks) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    private boolean writeBackpackData(ItemStack stack, String backpackId, ItemStack[] contents) {
        if (!isBackpack(stack)) {
            return false;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }

        ItemStack[] safeContents = contents == null ? new ItemStack[0] : contents;
        int size = Math.max(backpackSize(stack), normalizeBackpackSize(safeContents.length));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(backpackFlagKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(backpackIdKey, PersistentDataType.STRING, backpackId);
        pdc.set(backpackDataKey, PersistentDataType.BYTE_ARRAY, serialize(safeContents));
        pdc.set(backpackSizeKey, PersistentDataType.INTEGER, size);
        pdc.set(backpackTierKey, PersistentDataType.STRING, backpackTierName(size));
        applyBackpackPresentation(meta, size);
        stack.setItemMeta(meta);
        return true;
    }

    private boolean syncOpenBackpackToDeathDrops(Player player, List<ItemStack> drops, OpenBackpackSession session) {
        ItemStack[] contents = cloneContents(session.inventory().getContents());
        List<ItemStack> removedBackpacks = stripNestedBackpacks(contents);
        boolean updated = false;
        if (drops != null) {
            for (ItemStack drop : drops) {
                if (!hasBackpackId(drop, session.backpackId())) {
                    continue;
                }
                updated |= writeBackpackData(drop, session.backpackId(), contents);
            }
        }

        if (!updated) {
            int slot = findBackpackSlot(player, session.backpackId(), session.sourceSlot());
            if (slot >= 0) {
                ItemStack source = player.getInventory().getItem(slot);
                if (isBackpack(source)) {
                    ItemStack syncedDrop = source.clone();
                    if (writeBackpackData(syncedDrop, session.backpackId(), contents)) {
                        if (drops != null) {
                            drops.add(syncedDrop);
                        }
                        updated = true;
                    }
                }
            }
        }

        if (!removedBackpacks.isEmpty()) {
            if (updated && drops != null) {
                drops.addAll(removedBackpacks);
            } else if (!updated) {
                plugin.getLogger().warning("Skipped returning nested backpacks on death because the parent backpack could not be sanitized for " + player.getName() + ".");
            }
        }
        return updated;
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
        if (!(event.getView().getTopInventory() instanceof CraftingInventory inv)) return false;
        if (event.getSlotType() != InventoryType.SlotType.RESULT && event.getRawSlot() != 0) return false;

        BackpackUpgradeCraft upgradeCraft = matchBackpackUpgrade(inv.getMatrix());
        if (upgradeCraft != null) {
            event.setCancelled(true);

            ItemStack upgraded = createUpgradedBackpackFrom(upgradeCraft.backpack());
            if (upgraded == null || !isBackpack(upgraded) || !isUpgradedBackpack(upgraded)) {
                clearCustomCraftState(inv);
                player.updateInventory();
                player.sendMessage(MessageUtil.error("That backpack could not be upgraded safely."));
                return true;
            }

            if (!canReceiveCraftedBackpack(player, event, upgraded)) {
                player.sendMessage(MessageUtil.error("Use a normal click with an empty cursor, or shift-click with inventory space."));
                return true;
            }

            if (!consumeBackpackUpgradeIngredients(inv)) {
                clearCustomCraftState(inv);
                player.updateInventory();
                player.sendMessage(MessageUtil.error("The Expanded Backpack recipe changed before it could finish."));
                return true;
            }

            if (plugin.getItemAuditManager() != null) {
                plugin.getItemAuditManager().recordKnownAcquisition(
                    player,
                    upgraded,
                    "backpack_upgrade_craft",
                    "Crafted an Expanded Backpack from a Backpack."
                );
            }
            event.setCurrentItem(null);
            giveCraftedBackpack(player, event, upgraded);
            player.updateInventory();
            player.sendMessage(MessageUtil.success("Upgraded your Backpack into an <white>Expanded Backpack</white>."));
            return true;
        }

        ItemStack[] matrix = inv.getMatrix();
        ItemStack current = event.getCurrentItem();
        if (!containsBackpack(matrix)
            && !matchesBackpackIngredients(matrix)
            && !isBackpack(current)) {
            return false;
        }

        event.setCancelled(true);
        clearCustomCraftState(inv);
        player.updateInventory();
        player.sendMessage(MessageUtil.info("Use <white>/reliquary</white> to trade materials for a backpack."));
        return true;
    }

    private BackpackUpgradeCraft matchBackpackUpgrade(ItemStack[] matrix) {
        if (matrix == null || matrix.length == 0) {
            return null;
        }

        ItemStack backpack = null;
        Map<Material, Integer> materials = new EnumMap<>(Material.class);
        for (ItemStack item : matrix) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
                continue;
            }

            if (isBackpack(item)) {
                if (backpack != null || !isUpgradeableBackpack(item)) {
                    return null;
                }
                backpack = item.clone();
                continue;
            }

            if (!UPGRADED_BACKPACK_INGREDIENTS.containsKey(item.getType())) {
                return null;
            }
            if (!InventoryRecipeUtil.isPlainMaterial(plugin, item, item.getType())) {
                return null;
            }
            materials.merge(item.getType(), item.getAmount(), Integer::sum);
        }

        if (backpack == null) {
            return null;
        }
        for (Map.Entry<Material, Integer> required : UPGRADED_BACKPACK_INGREDIENTS.entrySet()) {
            if (materials.getOrDefault(required.getKey(), 0) < required.getValue()) {
                return null;
            }
        }
        return new BackpackUpgradeCraft(backpack);
    }

    private boolean canReceiveCraftedBackpack(Player player, InventoryClickEvent event, ItemStack backpack) {
        ClickType click = event.getClick();
        if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
            return canFitSingleBackpackReward(player, backpack);
        }

        if (click != ClickType.LEFT && click != ClickType.RIGHT) {
            return false;
        }

        ItemStack cursor = event.getCursor();
        return cursor == null || cursor.getType() == Material.AIR || cursor.getAmount() <= 0;
    }

    private void giveCraftedBackpack(Player player, InventoryClickEvent event, ItemStack backpack) {
        ClickType click = event.getClick();
        if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
            player.getInventory().addItem(backpack);
            return;
        }

        player.setItemOnCursor(backpack);
    }

    private boolean canFitSingleBackpackReward(Player player, ItemStack backpack) {
        if (backpack == null || backpack.getType() == Material.AIR || backpack.getAmount() <= 0) {
            return false;
        }

        ItemStack[] storage = player.getInventory().getStorageContents();
        for (ItemStack item : storage) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
                return true;
            }
        }
        return false;
    }

    private boolean consumeBackpackUpgradeIngredients(CraftingInventory inv) {
        ItemStack[] next = cloneContents(inv.getMatrix());
        if (!removeOneBackpackFromMatrix(next)) {
            return false;
        }

        for (Map.Entry<Material, Integer> required : UPGRADED_BACKPACK_INGREDIENTS.entrySet()) {
            if (!consumeMaterialFromMatrix(next, required.getKey(), required.getValue())) {
                return false;
            }
        }

        inv.setMatrix(next);
        clearCustomCraftState(inv);
        return true;
    }

    private boolean removeOneBackpackFromMatrix(ItemStack[] matrix) {
        for (int i = 0; i < matrix.length; i++) {
            ItemStack item = matrix[i];
            if (isUpgradeableBackpack(item)) {
                matrix[i] = null;
                return true;
            }
        }
        return false;
    }

    private boolean consumeMaterialFromMatrix(ItemStack[] matrix, Material material, int amount) {
        int remaining = amount;
        for (int i = 0; i < matrix.length && remaining > 0; i++) {
            ItemStack item = matrix[i];
            if (item == null || item.getType() != material || item.getAmount() <= 0) {
                continue;
            }

            int take = Math.min(remaining, item.getAmount());
            int left = item.getAmount() - take;
            matrix[i] = left <= 0 ? null : item.asQuantity(left);
            remaining -= take;
        }
        return remaining <= 0;
    }

    private boolean containsBackpack(ItemStack[] matrix) {
        if (matrix == null) {
            return false;
        }
        for (ItemStack item : matrix) {
            if (isBackpack(item)) {
                return true;
            }
        }
        return false;
    }

    private void applyBackpackPresentation(ItemMeta meta) {
        applyBackpackPresentation(meta, BACKPACK_SIZE);
    }

    private void applyBackpackPresentation(ItemMeta meta, int size) {
        int normalizedSize = normalizeBackpackSize(size);
        boolean upgraded = normalizedSize > BACKPACK_SIZE;
        String name = upgraded ? "Expanded Backpack" : "Backpack";
        CustomLoreUtil.Rarity rarity = upgraded ? CustomLoreUtil.Rarity.RARE : CustomLoreUtil.Rarity.UNCOMMON;
        meta.setItemModel(null);
        meta.setMaxStackSize(1);
        meta.displayName(CustomLoreUtil.displayName(rarity, name));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.FLOWER_POT,
            rarity.label(),
            "STORAGE",
            List.of("<gray>Portable storage.</gray>"),
            List.of(CustomLoreUtil.section(
                "Use",
                upgraded ? "Deep Pocket Vault" : "Pocket Vault",
                "<gray>Right-click to open.</gray>",
                "<gray>Holds <white>" + normalizedSize + "</white> items safely in its own saved storage.</gray>"
            ))
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }

    private int backpackSize(ItemStack backpack) {
        if (!isBackpack(backpack)) {
            return BACKPACK_SIZE;
        }
        ItemMeta meta = backpack.getItemMeta();
        if (meta == null) {
            return BACKPACK_SIZE;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String tier = pdc.get(backpackTierKey, PersistentDataType.STRING);
        if ("expanded".equalsIgnoreCase(tier) || "upgraded".equalsIgnoreCase(tier)) {
            return UPGRADED_BACKPACK_SIZE;
        }

        Integer stored = pdc.get(backpackSizeKey, PersistentDataType.INTEGER);
        int storedSize = normalizeBackpackSize(stored == null ? inferLegacyBackpackSize(meta) : stored);
        if (stored != null && storedSize < UPGRADED_BACKPACK_SIZE && hasExpandedBackpackLore(meta)) {
            return UPGRADED_BACKPACK_SIZE;
        }
        if (storedSize >= UPGRADED_BACKPACK_SIZE) {
            return storedSize;
        }
        return Math.max(storedSize, inferBackpackSizeFromData(pdc.get(backpackDataKey, PersistentDataType.BYTE_ARRAY)));
    }

    private int inferLegacyBackpackSize(ItemMeta meta) {
        if (meta == null) {
            return BACKPACK_SIZE;
        }
        if (meta.hasDisplayName()) {
            Component displayName = meta.displayName();
            if (displayName != null) {
                String plainName = PLAIN.serialize(displayName);
                if (plainName.toLowerCase(java.util.Locale.ROOT).contains("expanded backpack")) {
                    return UPGRADED_BACKPACK_SIZE;
                }
            }
        }
        if (meta.hasLore() && meta.lore() != null) {
            return hasExpandedBackpackLore(meta) ? UPGRADED_BACKPACK_SIZE : BACKPACK_SIZE;
        }
        return BACKPACK_SIZE;
    }

    private boolean hasExpandedBackpackLore(ItemMeta meta) {
        if (meta == null || !meta.hasLore() || meta.lore() == null) {
            return false;
        }
        for (Component line : meta.lore()) {
            String plainLine = PLAIN.serialize(line).toLowerCase(java.util.Locale.ROOT);
            if (plainLine.contains("deep pocket vault")
                || plainLine.contains("expanded backpack")
                || plainLine.contains("double chest")
                || (plainLine.contains("54") && (plainLine.contains("items") || plainLine.contains("slots")))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasLegacyBackpackSignature(ItemMeta meta) {
        if (meta == null || !meta.hasDisplayName() || meta.displayName() == null || !meta.hasLore() || meta.lore() == null) {
            return false;
        }

        String plainName = PLAIN.serialize(meta.displayName()).toLowerCase(java.util.Locale.ROOT);
        if (!plainName.contains("backpack")) {
            return false;
        }

        for (Component line : meta.lore()) {
            String plainLine = PLAIN.serialize(line).toLowerCase(java.util.Locale.ROOT);
            if (plainLine.contains("pocket vault")
                || plainLine.contains("portable storage")
                || (plainLine.contains("holds") && plainLine.contains("items safely"))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRecipeMenuHint(ItemMeta meta) {
        if (meta == null || !meta.hasLore() || meta.lore() == null) {
            return false;
        }
        for (Component line : meta.lore()) {
            String plainLine = PLAIN.serialize(line).toLowerCase(java.util.Locale.ROOT);
            if (plainLine.contains("click to view recipe")
                || plainLine.contains("click to trade")
                || plainLine.contains("click to craft")
                || plainLine.contains("use /reliquary")
                || plainLine.contains("recipe preview")
                || plainLine.contains("preview only")) {
                return true;
            }
        }
        return false;
    }

    private int normalizeBackpackSize(int size) {
        return size >= UPGRADED_BACKPACK_SIZE ? UPGRADED_BACKPACK_SIZE : BACKPACK_SIZE;
    }

    private int inferBackpackSizeFromData(byte[] data) {
        int storedSlots = serializedBackpackSlotCount(data);
        return storedSlots > BACKPACK_SIZE ? UPGRADED_BACKPACK_SIZE : BACKPACK_SIZE;
    }

    private int serializedBackpackSlotCount(byte[] data) {
        if (data == null || data.length < Integer.BYTES) {
            return 0;
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int storedSlots = in.readInt();
            if (storedSlots < 0 || storedSlots > UPGRADED_BACKPACK_SIZE) {
                return 0;
            }
            return storedSlots;
        } catch (IOException ignored) {
            return 0;
        }
    }

    private boolean isUpgradedBackpack(ItemStack item) {
        return isBackpack(item) && backpackSize(item) >= UPGRADED_BACKPACK_SIZE;
    }

    private boolean isUpgradeableBackpack(ItemStack item) {
        return item != null
            && item.getAmount() == 1
            && isTaggedBackpack(item)
            && !isUpgradedBackpack(item);
    }

    private int findUpgradeableBackpackSlot(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isUpgradeableBackpack(contents[i])) {
                return i;
            }
        }
        return -1;
    }

    private void migratePlayerBackpacks(Player player) {
        BackpackMigrationResult migration = new BackpackMigrationResult();
        migrateInventoryBackpacks(player, player.getInventory(), migration);
        migrateInventoryBackpacks(player, player.getEnderChest(), migration);
        notifyBackpackMigration(player, migration);
    }

    private void migrateInventoryBackpacks(Player player, Inventory inventory, BackpackMigrationResult migration) {
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isBackpack(contents[slot])) {
                migrateBackpackSlot(player, inventory, slot, migration);
            }
        }
    }

    private ItemStack migrateBackpackSlot(Player player, Inventory inventory, int slot) {
        BackpackMigrationResult migration = new BackpackMigrationResult();
        migrateBackpackSlot(player, inventory, slot, migration);
        notifyBackpackMigration(player, migration);
        return inventory.getItem(slot);
    }

    private void migrateBackpackSlot(Player player, Inventory inventory, int slot, BackpackMigrationResult migration) {
        ItemStack item = inventory.getItem(slot);
        if (!isBackpack(item)) return;

        List<ItemStack> normalized = expandBackpackItems(item, migration);
        if (normalized.isEmpty()) {
            inventory.setItem(slot, null);
            return;
        }

        inventory.setItem(slot, normalized.get(0));
        for (int i = 1; i < normalized.size(); i++) {
            placeMigratedBackpack(player, inventory, normalized.get(i), migration);
        }
    }

    private List<ItemStack> expandBackpackItems(ItemStack item, BackpackMigrationResult migration) {
        if (!isBackpack(item)) {
            return List.of();
        }

        ItemMeta sourceMeta = item.getItemMeta();
        if (sourceMeta == null) {
            return List.of(item);
        }

        PersistentDataContainer sourcePdc = sourceMeta.getPersistentDataContainer();
        byte[] storedData = sourcePdc.get(backpackDataKey, PersistentDataType.BYTE_ARRAY);
        byte[] primaryData = storedData == null ? new byte[0] : storedData.clone();
        int sourceSize = Math.max(backpackSize(item), inferBackpackSizeFromData(primaryData));
        boolean taggedBackpack = hasBackpackFlag(sourcePdc);
        int amount = taggedBackpack ? Math.max(1, item.getAmount()) : 1;
        if (!taggedBackpack && item.getAmount() > 1) {
            migration.clearedDuplicateStorage = true;
        }
        boolean stackedStoredBackpacks = amount > 1 && primaryData.length > 0;
        if (stackedStoredBackpacks) {
            migration.clearedDuplicateStorage = true;
        }

        String backpackId = sourcePdc.get(backpackIdKey, PersistentDataType.STRING);
        if (backpackId == null || backpackId.isBlank()) {
            backpackId = UUID.randomUUID().toString();
        }

        List<ItemStack> normalized = new ArrayList<>(amount);
        normalized.add(createNormalizedBackpack(backpackId, primaryData, sourceSize));
        for (int i = 1; i < amount; i++) {
            byte[] extraData = stackedStoredBackpacks ? new byte[0] : primaryData;
            normalized.add(createNormalizedBackpack(UUID.randomUUID().toString(), extraData, sourceSize));
        }
        return normalized;
    }

    private boolean hasBackpackFlag(PersistentDataContainer pdc) {
        Byte flag = pdc.get(backpackFlagKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    private ItemStack createNormalizedBackpack(String backpackId, byte[] data, int size) {
        ItemStack normalized = new ItemStack(Material.FLOWER_POT);
        ItemMeta meta = normalized.getItemMeta();
        if (meta == null) {
            return normalized;
        }

        int normalizedSize = Math.max(normalizeBackpackSize(size), inferBackpackSizeFromData(data));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(backpackFlagKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(backpackIdKey, PersistentDataType.STRING, backpackId);
        pdc.set(backpackDataKey, PersistentDataType.BYTE_ARRAY, normalizedBackpackData(data, normalizedSize));
        pdc.set(backpackSizeKey, PersistentDataType.INTEGER, normalizedSize);
        pdc.set(backpackTierKey, PersistentDataType.STRING, backpackTierName(normalizedSize));
        applyBackpackPresentation(meta, normalizedSize);
        normalized.setItemMeta(meta);
        return normalized;
    }

    private String backpackTierName(int size) {
        return normalizeBackpackSize(size) >= UPGRADED_BACKPACK_SIZE ? "expanded" : "normal";
    }

    private byte[] normalizedBackpackData(byte[] data, int targetSize) {
        int normalizedSize = normalizeBackpackSize(targetSize);
        if (data == null || data.length == 0) {
            return serialize(new ItemStack[normalizedSize]);
        }

        int sourceSize = Math.max(normalizedSize, inferBackpackSizeFromData(data));
        ItemStack[] sourceContents = deserialize(data, sourceSize);
        ItemStack[] resizedContents = new ItemStack[normalizedSize];
        System.arraycopy(sourceContents, 0, resizedContents, 0, Math.min(sourceContents.length, resizedContents.length));
        return serialize(resizedContents);
    }

    private void placeMigratedBackpack(Player player, Inventory inventory, ItemStack backpack, BackpackMigrationResult migration) {
        int emptySlot = inventory.firstEmpty();
        if (emptySlot >= 0) {
            inventory.setItem(emptySlot, backpack);
            return;
        }

        if (inventory != player.getInventory()) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(backpack);
            if (leftovers.isEmpty()) {
                return;
            }
            backpack = leftovers.values().iterator().next();
        }

        player.getWorld().dropItemNaturally(player.getLocation(), backpack);
        migration.droppedOverflow = true;
    }

    private void notifyBackpackMigration(Player player, BackpackMigrationResult migration) {
        if (migration.clearedDuplicateStorage) {
            player.sendMessage(MessageUtil.warn(
                "Stacked backpacks were split into separate items. Only one kept stored contents to prevent duplicated storage."
            ));
        }
        if (migration.droppedOverflow) {
            player.sendMessage(MessageUtil.warn(
                "Some backpacks were dropped at your feet because there was not enough room to split the stack safely."
            ));
        }
    }

    private static final class BackpackMigrationResult {
        private boolean clearedDuplicateStorage;
        private boolean droppedOverflow;
    }

    private boolean removeTradeMaterials(Player player, Map<Material, Integer> required) {
        return InventoryRecipeUtil.removePlainMaterials(plugin, player, required);
    }

    private record OpenBackpackSession(String backpackId, int sourceSlot, Inventory inventory) {}

    private record BackpackUpgradeCraft(ItemStack backpack) {}

    private record BackpackHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
