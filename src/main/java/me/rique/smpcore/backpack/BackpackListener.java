package me.rique.smpcore.backpack;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.InventoryRecipeUtil;
import me.rique.smpcore.util.ItemModelUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
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
import org.bukkit.event.block.BlockDispenseEvent;
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
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
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
    private static final int MAX_SERIALIZED_ITEM_BYTES = 2 * 1024 * 1024;
    private static final int MAX_SUFFIX_LENGTH = 24;
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
    private final NamespacedKey backpackSuffixKey;
    private final NamespacedKey backpackSessionKey;
    private final NamespacedKey backpackRecipeKey;
    private final NamespacedKey menuPreviewKey;
    private final BackpackRecoveryJournal recoveryJournal;
    private final Map<UUID, OpenBackpackSession> openBackpacks = new ConcurrentHashMap<>();
    private final Map<String, UUID> openBackpackOwners = new ConcurrentHashMap<>();
    private final Map<UUID, Long> warnCooldown = new ConcurrentHashMap<>();

    public BackpackListener(SMPCore plugin) {
        this.plugin = plugin;
        this.backpackFlagKey = new NamespacedKey(plugin, "backpack_flag");
        this.backpackIdKey = new NamespacedKey(plugin, "backpack_id");
        this.backpackDataKey = new NamespacedKey(plugin, "backpack_data");
        this.backpackSizeKey = new NamespacedKey(plugin, "backpack_size");
        this.backpackTierKey = new NamespacedKey(plugin, "backpack_tier");
        this.backpackSuffixKey = new NamespacedKey(plugin, "backpack_suffix");
        this.backpackSessionKey = new NamespacedKey(plugin, "backpack_open_session");
        this.backpackRecipeKey = new NamespacedKey(plugin, "backpack_recipe");
        this.menuPreviewKey = new NamespacedKey(plugin, "menu_preview_item");
        this.recoveryJournal = new BackpackRecoveryJournal(plugin);
        Bukkit.removeRecipe(backpackRecipeKey);
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(player -> {
            recoverInterruptedBackpack(player);
            migratePlayerBackpacks(player);
        }));
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

    public boolean activateHeldCrossplayAbility(Player player) {
        if (player == null) {
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isBackpack(held)) {
            return false;
        }
        if (!player.hasPermission("smpcore.backpack.use")) {
            player.sendMessage(MessageUtil.error("You do not have permission to use backpacks."));
            return true;
        }
        int slot = player.getInventory().getHeldItemSlot();
        migrateBackpackSlot(player, player.getInventory(), slot);
        openBackpack(player, slot);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlaceBackpack(BlockPlaceEvent event) {
        if (!isBackpack(event.getItemInHand())) {
            return;
        }
        event.setCancelled(true);
        maybeWarn(event.getPlayer(), "Backpacks cannot be placed.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispenseBackpack(BlockDispenseEvent event) {
        if (!isBackpack(event.getItem())) {
            return;
        }
        // Dispensers bypass BlockPlaceEvent for legacy minecart backpacks.
        event.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        recoverInterruptedBackpack(event.getPlayer());
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
            OpenBackpackSession session = openBackpacks.get(player.getUniqueId());
            if (session != null && !event.isCancelled()) {
                scheduleBackpackAutosave(player, session);
            }
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
        OpenBackpackSession session = removeOpenBackpack(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }

        if (event.getKeepInventory()) {
            finalizeBackpack(event.getPlayer(), session, session.inventory());
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
            scheduleBackpackAutosave(player, session);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof BackpackHolder)) return;

        OpenBackpackSession session = removeOpenBackpack(player.getUniqueId());
        if (session == null) return;
        finalizeBackpack(player, session, event.getInventory());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        OpenBackpackSession session = openBackpacks.get(event.getPlayer().getUniqueId());
        if (session == null) return;
        if (!hasBackpackId(event.getItemDrop().getItemStack(), session.backpackId())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        OpenBackpackSession session = openBackpacks.get(event.getPlayer().getUniqueId());
        if (session == null) return;
        if (!hasBackpackId(event.getMainHandItem(), session.backpackId())
            && !hasBackpackId(event.getOffHandItem(), session.backpackId())) {
            return;
        }
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
        OpenBackpackSession session = removeOpenBackpack(player.getUniqueId());
        if (session != null) {
            finalizeBackpack(player, session, session.inventory());
        }
    }

    private void scheduleBackpackAutosave(Player player, OpenBackpackSession session) {
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            OpenBackpackSession current = openBackpacks.get(playerId);
            if (current != session) {
                return;
            }
            ItemStack cursor = player.getItemOnCursor();
            if (cursor != null && !cursor.getType().isAir() && cursor.getAmount() > 0) {
                return;
            }
            if (!journalBackpack(player, current, current.inventory().getContents())) {
                plugin.getLogger().severe("Backpack autosave journal failed for " + player.getName() + ".");
                player.sendMessage(MessageUtil.error("Backpack safety save failed. Close it now so the server can recover the contents."));
            }
        });
    }

    public void shutdown() {
        for (Map.Entry<UUID, OpenBackpackSession> entry : List.copyOf(openBackpacks.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;

            OpenBackpackSession session = removeOpenBackpack(entry.getKey());
            if (session == null) continue;
            finalizeBackpack(player, session, session.inventory());
        }
        warnCooldown.clear();
    }

    private void handleBackpackMenuClick(InventoryClickEvent event, Player player) {
        OpenBackpackSession session = openBackpacks.get(player.getUniqueId());
        if (session == null) {
            event.setCancelled(true);
            return;
        }

        if (isUnsafeStorageCloneClick(event)) {
            event.setCancelled(true);
            return;
        }

        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;

        if (clicked == player.getInventory()) {
            if (event.getSlot() == session.sourceSlot()
                || isSealedSessionSource(event.getCurrentItem(), session)) {
                event.setCancelled(true);
                return;
            }
            if (event.isShiftClick() && isBackpack(event.getCurrentItem())) {
                event.setCancelled(true);
                maybeWarn(player, "Backpacks cannot be stored inside backpacks.");
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
            if (isBackpack(event.getCursor())) {
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
        InventoryAction action = event.getAction();
        String actionName = action == null ? "" : action.name();
        ClickType click = event.getClick();
        return action == InventoryAction.CLONE_STACK
            || action == InventoryAction.COLLECT_TO_CURSOR
            || action == InventoryAction.HOTBAR_SWAP
            || "HOTBAR_MOVE_AND_READD".equals(actionName)
            || action == InventoryAction.UNKNOWN
            || click == ClickType.CREATIVE
            || click == ClickType.DOUBLE_CLICK
            || click == ClickType.MIDDLE
            || click == ClickType.NUMBER_KEY
            || click == ClickType.SWAP_OFFHAND
            || click == ClickType.UNKNOWN;
    }

    private void openBackpack(Player player, int sourceSlot) {
        if (openBackpacks.containsKey(player.getUniqueId())) return;
        if (recoveryJournal.exists(player.getUniqueId())) {
            recoverInterruptedBackpack(player);
            if (recoveryJournal.exists(player.getUniqueId())) {
                player.sendMessage(MessageUtil.error("A previous backpack recovery is still pending. Nothing was overwritten."));
                return;
            }
        }
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
        if (hasDuplicateVisibleBackpack(player, sourceSlot, backpackId)
            || openBackpackOwners.putIfAbsent(backpackId, player.getUniqueId()) != null) {
            maybeWarn(player, "That backpack has another visible copy and cannot be opened safely.");
            return;
        }

        byte[] raw = sourceMeta.getPersistentDataContainer().get(backpackDataKey, PersistentDataType.BYTE_ARRAY);
        int size = backpackSize(source);
        Inventory inv = Bukkit.createInventory(
            new BackpackHolder(),
            size,
            Component.text(backpackDisplayName(source))
        );
        ItemStack[] contents = deserialize(raw, size);
        if (contents == null) {
            openBackpackOwners.remove(backpackId, player.getUniqueId());
            plugin.getLogger().severe("Refused to open backpack " + backpackId + " for " + player.getName() + " because its stored data was invalid.");
            player.sendMessage(MessageUtil.error("This backpack has invalid saved data. An admin must recover it; nothing was overwritten."));
            return;
        }
        List<ItemStack> removedBackpacks = stripNestedBackpacks(contents);
        inv.setContents(contents);
        String sessionToken = UUID.randomUUID().toString();
        OpenBackpackSession session = new OpenBackpackSession(backpackId, sessionToken, sourceSlot, inv, source.clone());
        if (!journalBackpack(player, session, contents)) {
            openBackpackOwners.remove(backpackId, player.getUniqueId());
            player.sendMessage(MessageUtil.error("Backpack could not open because its recovery save failed."));
            return;
        }

        ItemStack sealedSource = source.clone();
        if (!writeBackpackData(sealedSource, backpackId, new ItemStack[size])
            || !setBackpackSessionToken(sealedSource, sessionToken)) {
            recoveryJournal.delete(player.getUniqueId());
            openBackpackOwners.remove(backpackId, player.getUniqueId());
            player.sendMessage(MessageUtil.error("Backpack could not be sealed safely before opening."));
            return;
        }
        player.getInventory().setItem(sourceSlot, sealedSource);

        if (!removedBackpacks.isEmpty()) {
            returnBackpackOverflow(player, removedBackpacks);
            maybeWarn(player, "Nested backpacks were removed to prevent duplicated storage.");
        }

        openBackpacks.put(player.getUniqueId(), session);
        player.openInventory(inv);
    }

    private OpenBackpackSession removeOpenBackpack(UUID playerId) {
        OpenBackpackSession session = openBackpacks.remove(playerId);
        if (session != null) {
            openBackpackOwners.remove(session.backpackId(), playerId);
        }
        return session;
    }

    private boolean hasDuplicateVisibleBackpack(Player openingPlayer, int sourceSlot, String backpackId) {
        for (Player candidate : Bukkit.getOnlinePlayers()) {
            for (Inventory inventory : List.of(candidate.getInventory(), candidate.getEnderChest())) {
                ItemStack[] contents = inventory.getContents();
                for (int slot = 0; slot < contents.length; slot++) {
                    if (candidate.equals(openingPlayer) && inventory.equals(candidate.getInventory()) && slot == sourceSlot) {
                        continue;
                    }
                    if (hasBackpackId(contents[slot], backpackId)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean finalizeBackpack(Player player, OpenBackpackSession session, Inventory inventory) {
        ItemStack[] sanitizedContents = cloneContents(inventory.getContents());
        List<ItemStack> removedBackpacks = stripNestedBackpacks(sanitizedContents);
        boolean journaled = journalBackpack(player, session, sanitizedContents);
        if (!journaled) {
            plugin.getLogger().severe("Backpack finalization journal failed for " + player.getName() + "; attempting direct recovery.");
        }
        int slot = findBackpackSlot(player, session);
        ItemStack stack = slot < 0 ? session.sourceTemplate().clone() : player.getInventory().getItem(slot);
        if (!isBackpack(stack)
            || !writeBackpackData(stack, session.backpackId(), sanitizedContents)
            || !clearBackpackSessionToken(stack)) {
            player.sendMessage(MessageUtil.error("Backpack could not be finalized. Its recovery journal was kept on disk."));
            plugin.getLogger().severe("Could not finalize backpack " + session.backpackId() + " for " + player.getName() + ".");
            returnBackpackOverflow(player, removedBackpacks);
            return false;
        }

        if (slot >= 0) {
            player.getInventory().setItem(slot, stack);
        } else {
            InventoryRecipeUtil.giveOrDrop(player, stack);
            plugin.getLogger().warning("Recovered backpack " + session.backpackId() + " because its sealed source item went missing for " + player.getName() + ".");
            player.sendMessage(MessageUtil.warn("Your backpack moved unexpectedly, so a recovered copy containing the current items was returned."));
        }
        try {
            player.saveData();
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Could not force-save recovered backpack data for " + player.getName() + ": " + ex.getMessage());
            returnBackpackOverflow(player, removedBackpacks);
            return false;
        }
        if (!recoveryJournal.delete(player.getUniqueId())) {
            plugin.getLogger().severe("Backpack " + session.backpackId() + " was saved, but its recovery journal could not be cleared.");
        }
        returnBackpackOverflow(player, removedBackpacks);
        return true;
    }

    private int findBackpackSlot(Player player, OpenBackpackSession session) {
        if (player == null || session == null) {
            return -1;
        }
        ItemStack preferred = player.getInventory().getItem(session.sourceSlot());
        if (isSealedSessionSource(preferred, session)) {
            return session.sourceSlot();
        }
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isSealedSessionSource(contents[slot], session)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isSessionSource(Player owner, OpenBackpackSession session, ItemStack backpack) {
        return owner != null && session != null
            && isSealedSessionSource(backpack, session)
            && findBackpackSlot(owner, session) >= 0;
    }

    private boolean isSealedSessionSource(ItemStack item, OpenBackpackSession session) {
        if (item == null || session == null || !isBackpack(item)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return matchesSessionIdentity(
            session.backpackId(),
            session.sessionToken(),
            pdc.get(backpackIdKey, PersistentDataType.STRING),
            pdc.get(backpackSessionKey, PersistentDataType.STRING)
        );
    }

    static boolean matchesSessionIdentity(
        String expectedBackpackId,
        String expectedSessionToken,
        String actualBackpackId,
        String actualSessionToken
    ) {
        return expectedBackpackId != null && !expectedBackpackId.isBlank()
            && expectedSessionToken != null && !expectedSessionToken.isBlank()
            && expectedBackpackId.equals(actualBackpackId)
            && expectedSessionToken.equals(actualSessionToken);
    }

    private boolean journalBackpack(Player player, OpenBackpackSession session, ItemStack[] contents) {
        if (player == null || session == null) {
            return false;
        }
        ItemStack recovered = session.sourceTemplate().clone();
        if (!writeBackpackData(recovered, session.backpackId(), cloneContents(contents))
            || !clearBackpackSessionToken(recovered)) {
            return false;
        }
        return recoveryJournal.write(
            player.getUniqueId(),
            session.backpackId(),
            session.sessionToken(),
            session.sourceSlot(),
            recovered
        );
    }

    private boolean setBackpackSessionToken(ItemStack item, String token) {
        if (!isBackpack(item) || token == null || token.isBlank()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        meta.getPersistentDataContainer().set(backpackSessionKey, PersistentDataType.STRING, token);
        item.setItemMeta(meta);
        return true;
    }

    private boolean clearBackpackSessionToken(ItemStack item) {
        if (!isBackpack(item)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        meta.getPersistentDataContainer().remove(backpackSessionKey);
        item.setItemMeta(meta);
        return true;
    }

    private String backpackSessionToken(ItemStack item) {
        if (!isBackpack(item)) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(backpackSessionKey, PersistentDataType.STRING);
    }

    private void recoverInterruptedBackpack(Player player) {
        if (player == null || openBackpacks.containsKey(player.getUniqueId())) {
            return;
        }
        BackpackRecoveryJournal.Recovery recovery = recoveryJournal.read(player.getUniqueId());
        if (recovery == null) {
            return;
        }

        ItemStack recovered = recovery.backpack() == null ? null : recovery.backpack().clone();
        if (!isBackpack(recovered)
            || !hasBackpackId(recovered, recovery.backpackId())
            || !clearBackpackSessionToken(recovered)) {
            plugin.getLogger().severe("Backpack recovery journal was invalid for " + player.getName() + "; it was retained for manual recovery.");
            return;
        }

        BackpackLocation source = findRecoverySource(player, recovery);
        if (source != null) {
            source.inventory().setItem(source.slot(), recovered);
        } else {
            InventoryRecipeUtil.giveOrDrop(player, recovered);
        }
        neutralizeDuplicateSessionShells(player, recovery, source);

        try {
            player.saveData();
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Could not force-save recovered backpack for " + player.getName() + ": " + ex.getMessage());
            return;
        }
        if (!recoveryJournal.delete(player.getUniqueId())) {
            return;
        }
        plugin.getLogger().warning("Recovered interrupted backpack " + recovery.backpackId() + " for " + player.getName() + ".");
        player.sendMessage(MessageUtil.success("Your backpack and its saved contents were recovered safely."));
    }

    private BackpackLocation findRecoverySource(Player player, BackpackRecoveryJournal.Recovery recovery) {
        Inventory inventory = player.getInventory();
        ItemStack preferred = inventory.getItem(recovery.sourceSlot());
        if (matchesRecoverySource(preferred, recovery, true)) {
            return new BackpackLocation(inventory, recovery.sourceSlot());
        }
        for (Inventory candidate : List.of(player.getInventory(), player.getEnderChest())) {
            ItemStack[] contents = candidate.getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                if (matchesRecoverySource(contents[slot], recovery, true)) {
                    return new BackpackLocation(candidate, slot);
                }
            }
        }
        if (hasBackpackId(preferred, recovery.backpackId())) {
            return new BackpackLocation(inventory, recovery.sourceSlot());
        }
        for (Inventory candidate : List.of(player.getInventory(), player.getEnderChest())) {
            ItemStack[] contents = candidate.getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                if (hasBackpackId(contents[slot], recovery.backpackId())) {
                    return new BackpackLocation(candidate, slot);
                }
            }
        }
        return null;
    }

    private boolean matchesRecoverySource(
        ItemStack item,
        BackpackRecoveryJournal.Recovery recovery,
        boolean requireToken
    ) {
        if (!hasBackpackId(item, recovery.backpackId())) {
            return false;
        }
        return !requireToken || recovery.sessionToken().equals(backpackSessionToken(item));
    }

    private void neutralizeDuplicateSessionShells(
        Player player,
        BackpackRecoveryJournal.Recovery recovery,
        BackpackLocation restored
    ) {
        for (Inventory inventory : List.of(player.getInventory(), player.getEnderChest())) {
            ItemStack[] contents = inventory.getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                if (restored != null && restored.inventory() == inventory && restored.slot() == slot) {
                    continue;
                }
                ItemStack item = contents[slot];
                if (!matchesRecoverySource(item, recovery, true)) {
                    continue;
                }
                ItemStack shell = createNormalizedBackpack(
                    UUID.randomUUID().toString(),
                    new byte[0],
                    backpackSize(item),
                    normalizedStoredSuffix(item.getItemMeta())
                );
                if (shell != null) {
                    inventory.setItem(slot, shell);
                    plugin.getLogger().warning("Neutralized an extra sealed backpack shell while recovering " + recovery.backpackId() + " for " + player.getName() + ".");
                }
            }
        }
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

    public boolean isOpenBackpackInventory(Player player, Inventory inventory) {
        if (player == null || inventory == null) {
            return false;
        }
        OpenBackpackSession session = openBackpacks.get(player.getUniqueId());
        return session != null && session.inventory() == inventory;
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
        String baseName = isExpandedBackpack(item) ? "Expanded Backpack" : "Backpack";
        if (item == null || !item.hasItemMeta()) {
            return baseName;
        }
        String suffix = normalizedStoredSuffix(item.getItemMeta());
        return suffix.isEmpty() ? baseName : baseName + " - " + suffix;
    }

    public boolean setBackpackSuffix(Player player, String requestedSuffix) {
        if (openBackpacks.containsKey(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("Close your open backpack before changing its label."));
            return false;
        }

        int slot = player.getInventory().getHeldItemSlot();
        ItemStack backpack = migrateBackpackSlot(player, player.getInventory(), slot);
        if (!isBackpack(backpack)) {
            player.sendMessage(MessageUtil.error("Hold the backpack you want to label in your main hand."));
            return false;
        }

        String suffix = normalizeBackpackSuffix(requestedSuffix);
        if (suffix == null || suffix.isEmpty()) {
            player.sendMessage(MessageUtil.warn("Use 1-" + MAX_SUFFIX_LENGTH + " visible plain-text characters for the label."));
            return false;
        }

        ItemMeta meta = backpack.getItemMeta();
        if (meta == null) {
            player.sendMessage(MessageUtil.error("That backpack could not be labeled safely."));
            return false;
        }
        meta.getPersistentDataContainer().set(backpackSuffixKey, PersistentDataType.STRING, suffix);
        applyBackpackPresentation(meta, backpackSize(backpack));
        backpack.setItemMeta(meta);
        player.getInventory().setItem(slot, backpack);
        player.sendMessage(MessageUtil.success("Backpack label set to <white>" + MM.escapeTags(suffix) + "</white>."));
        return true;
    }

    public boolean clearBackpackSuffix(Player player) {
        if (openBackpacks.containsKey(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("Close your open backpack before changing its label."));
            return false;
        }

        int slot = player.getInventory().getHeldItemSlot();
        ItemStack backpack = migrateBackpackSlot(player, player.getInventory(), slot);
        if (!isBackpack(backpack)) {
            player.sendMessage(MessageUtil.error("Hold the backpack you want to clear in your main hand."));
            return false;
        }

        ItemMeta meta = backpack.getItemMeta();
        if (meta == null) {
            player.sendMessage(MessageUtil.error("That backpack label could not be cleared safely."));
            return false;
        }
        meta.getPersistentDataContainer().remove(backpackSuffixKey);
        applyBackpackPresentation(meta, backpackSize(backpack));
        backpack.setItemMeta(meta);
        player.getInventory().setItem(slot, backpack);
        player.sendMessage(MessageUtil.success("Backpack label cleared."));
        return true;
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
        if (openSession != null && backpackId.equals(openSession.backpackId())
            && isSessionSource(owner, openSession, backpack)) {
            return Arrays.asList(cloneContents(openSession.inventory().getContents()));
        }

        byte[] raw = meta.getPersistentDataContainer().get(backpackDataKey, PersistentDataType.BYTE_ARRAY);
        if (raw == null || raw.length == 0) {
            return List.of();
        }

        ItemStack[] contents = deserialize(raw, backpackSize(backpack));
        if (contents == null) {
            plugin.getLogger().severe("Backpack " + backpackId + " could not be decoded during an audit; its original data was retained.");
            return List.of();
        }
        return Arrays.asList(cloneContents(contents));
    }

    public boolean rewriteAuditContents(ItemStack backpack, ItemStack[] contents) {
        return rewriteAuditContents(null, backpack, contents);
    }

    public boolean rewriteAuditContents(Player owner, ItemStack backpack, ItemStack[] contents) {
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
        ItemStack[] safeContents = contents == null ? new ItemStack[0] : cloneContents(contents);
        OpenBackpackSession openSession = owner == null ? null : openBackpacks.get(owner.getUniqueId());
        if (openSession != null && backpackId.equals(openSession.backpackId())
            && isSessionSource(owner, openSession, backpack)) {
            openSession.inventory().setContents(safeContents);
            return journalBackpack(owner, openSession, safeContents);
        }
        return writeBackpackData(backpack, backpackId, safeContents);
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
        byte[] emptyData = serialize(new ItemStack[normalizedSize]);
        if (emptyData == null) {
            throw new IllegalStateException("Could not initialize backpack storage data");
        }
        pdc.set(backpackFlagKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(backpackIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        pdc.set(backpackDataKey, PersistentDataType.BYTE_ARRAY, emptyData);
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
        String suffix = normalizedStoredSuffix(meta);
        return createNormalizedBackpack(backpackId, data == null ? new byte[0] : data, UPGRADED_BACKPACK_SIZE, suffix);
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
        byte[] serialized = serialize(safeContents);
        if (serialized == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(backpackFlagKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(backpackIdKey, PersistentDataType.STRING, backpackId);
        pdc.set(backpackDataKey, PersistentDataType.BYTE_ARRAY, serialized);
        pdc.set(backpackSizeKey, PersistentDataType.INTEGER, size);
        pdc.set(backpackTierKey, PersistentDataType.STRING, backpackTierName(size));
        applyBackpackPresentation(meta, size);
        stack.setItemMeta(meta);
        return true;
    }

    private boolean syncOpenBackpackToDeathDrops(Player player, List<ItemStack> drops, OpenBackpackSession session) {
        ItemStack[] contents = cloneContents(session.inventory().getContents());
        List<ItemStack> removedBackpacks = stripNestedBackpacks(contents);
        journalBackpack(player, session, contents);
        boolean updated = false;
        if (drops != null) {
            for (int index = 0; index < drops.size(); index++) {
                ItemStack drop = drops.get(index);
                if (!isSealedSessionSource(drop, session)) {
                    continue;
                }
                if (!updated) {
                    updated = writeBackpackData(drop, session.backpackId(), contents)
                        && clearBackpackSessionToken(drop);
                    continue;
                }
                ItemStack shell = createNormalizedBackpack(
                    UUID.randomUUID().toString(),
                    new byte[0],
                    backpackSize(drop),
                    normalizedStoredSuffix(drop.getItemMeta())
                );
                if (shell != null) {
                    drops.set(index, shell);
                }
            }
        }

        if (!updated) {
            ItemStack recovered = session.sourceTemplate().clone();
            if (writeBackpackData(recovered, session.backpackId(), contents)
                && clearBackpackSessionToken(recovered)
                && drops != null) {
                drops.add(recovered);
                updated = true;
                plugin.getLogger().warning("Added a recovered backpack to the death drops for " + player.getName() + ".");
            }
        }

        if (!removedBackpacks.isEmpty()) {
            if (updated && drops != null) {
                drops.addAll(removedBackpacks);
            } else if (!updated) {
                plugin.getLogger().warning("Skipped returning nested backpacks on death because the parent backpack could not be sanitized for " + player.getName() + ".");
            }
        }
        if (updated && !recoveryJournal.delete(player.getUniqueId())) {
            plugin.getLogger().severe("A finalized death-drop backpack retained its recovery journal for " + player.getName() + ".");
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
        } catch (Exception ex) {
            plugin.getLogger().severe("Failed to serialize backpack data: " + ex.getMessage());
            return null;
        }
    }

    private ItemStack[] deserialize(byte[] data, int size) {
        ItemStack[] out = new ItemStack[size];
        if (data == null || data.length == 0) return out;

        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
            DataInputStream in = new DataInputStream(bais)) {
            int stored = in.readInt();
            if (stored < 0 || stored > UPGRADED_BACKPACK_SIZE) {
                throw new IOException("Invalid backpack slot count: " + stored);
            }
            for (int i = 0; i < stored; i++) {
                int length = in.readInt();
                if (!isSafeSerializedItemLength(length)) {
                    throw new IOException("Backpack item data length is outside the safe range");
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
            plugin.getLogger().severe("Backpack data was invalid and was retained without modification: " + ex.getMessage());
            return null;
        }
        return out;
    }

    static boolean isSafeSerializedItemLength(int length) {
        return length >= 0 && length <= MAX_SERIALIZED_ITEM_BYTES;
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
        String baseName = upgraded ? "Expanded Backpack" : "Backpack";
        String suffix = normalizedStoredSuffix(meta);
        String name = suffix.isEmpty() ? baseName : baseName + " - " + suffix;
        CustomLoreUtil.Rarity rarity = upgraded ? CustomLoreUtil.Rarity.RARE : CustomLoreUtil.Rarity.UNCOMMON;
        ItemModelUtil.apply(meta, upgraded ? "expanded_backpack" : "backpack");
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

    static String normalizeBackpackSuffix(String input) {
        if (input == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        boolean pendingSpace = false;
        for (int offset = 0; offset < input.length();) {
            int codePoint = input.codePointAt(offset);
            offset += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint) || type == Character.FORMAT || codePoint == '\u00a7') {
                return null;
            }
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingSpace = result.length() > 0;
                continue;
            }
            if (pendingSpace) {
                result.append(' ');
                pendingSpace = false;
            }
            result.appendCodePoint(codePoint);
            if (result.codePointCount(0, result.length()) > MAX_SUFFIX_LENGTH) {
                return null;
            }
        }
        return result.toString();
    }

    private String normalizedStoredSuffix(ItemMeta meta) {
        if (meta == null) {
            return "";
        }
        String stored = meta.getPersistentDataContainer().get(backpackSuffixKey, PersistentDataType.STRING);
        String normalized = normalizeBackpackSuffix(stored);
        return normalized == null ? "" : normalized;
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

        String sessionToken = backpackSessionToken(item);
        if (sessionToken != null && !sessionToken.isBlank()) {
            OpenBackpackSession session = openBackpacks.get(player.getUniqueId());
            if (session != null && isSealedSessionSource(item, session)) {
                return;
            }
            if (recoveryJournal.exists(player.getUniqueId())) {
                return;
            }
            ItemMeta meta = item.getItemMeta();
            byte[] data = meta == null ? null : meta.getPersistentDataContainer().get(backpackDataKey, PersistentDataType.BYTE_ARRAY);
            ItemStack rekeyed = createNormalizedBackpack(
                UUID.randomUUID().toString(),
                data == null ? new byte[0] : data,
                backpackSize(item),
                meta == null ? "" : normalizedStoredSuffix(meta)
            );
            if (rekeyed == null) {
                return;
            }
            inventory.setItem(slot, rekeyed);
            item = rekeyed;
            plugin.getLogger().warning("Rekeyed an orphaned sealed backpack shell for " + player.getName() + ".");
        }

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

        String suffix = normalizedStoredSuffix(sourceMeta);
        List<ItemStack> normalized = new ArrayList<>(amount);
        ItemStack primary = createNormalizedBackpack(backpackId, primaryData, sourceSize, suffix);
        if (primary == null) {
            return List.of(item);
        }
        normalized.add(primary);
        for (int i = 1; i < amount; i++) {
            byte[] extraData = stackedStoredBackpacks ? new byte[0] : primaryData;
            ItemStack extra = createNormalizedBackpack(UUID.randomUUID().toString(), extraData, sourceSize, suffix);
            if (extra == null) {
                return List.of(item);
            }
            normalized.add(extra);
        }
        return normalized;
    }

    private boolean hasBackpackFlag(PersistentDataContainer pdc) {
        Byte flag = pdc.get(backpackFlagKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    private ItemStack createNormalizedBackpack(String backpackId, byte[] data, int size, String suffix) {
        ItemStack normalized = new ItemStack(Material.FLOWER_POT);
        ItemMeta meta = normalized.getItemMeta();
        if (meta == null) {
            return normalized;
        }

        int normalizedSize = Math.max(normalizeBackpackSize(size), inferBackpackSizeFromData(data));
        byte[] normalizedData = normalizedBackpackData(data, normalizedSize);
        if (normalizedData == null) {
            return null;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(backpackFlagKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(backpackIdKey, PersistentDataType.STRING, backpackId);
        pdc.set(backpackDataKey, PersistentDataType.BYTE_ARRAY, normalizedData);
        pdc.set(backpackSizeKey, PersistentDataType.INTEGER, normalizedSize);
        pdc.set(backpackTierKey, PersistentDataType.STRING, backpackTierName(normalizedSize));
        if (suffix != null && !suffix.isEmpty()) {
            pdc.set(backpackSuffixKey, PersistentDataType.STRING, suffix);
        }
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
        if (sourceContents == null) {
            return null;
        }
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

    private record OpenBackpackSession(
        String backpackId,
        String sessionToken,
        int sourceSlot,
        Inventory inventory,
        ItemStack sourceTemplate
    ) {}

    private record BackpackLocation(Inventory inventory, int slot) {}

    private record BackpackUpgradeCraft(ItemStack backpack) {}

    private record BackpackHolder() implements InventoryHolder, MenuDupeGuardListener.MutableMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
