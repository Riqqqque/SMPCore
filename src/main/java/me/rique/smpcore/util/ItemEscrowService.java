package me.rique.smpcore.util;

import me.rique.smpcore.SMPCore;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemEscrowService {

    private final SMPCore plugin;
    private final NamespacedKey escrowIdKey;
    private final NamespacedKey escrowOwnerKey;
    private final NamespacedKey menuPreviewKey;
    private final File escrowFile;
    private final Object lock = new Object();
    private final Map<UUID, List<EscrowRecovery>> pendingRecoveries = new ConcurrentHashMap<>();
    private final Map<UUID, EscrowedItem> retainedEscrows = new ConcurrentHashMap<>();
    private final Collection<UUID> knownEscrowIds = ConcurrentHashMap.newKeySet();
    private YamlConfiguration escrowConfig;

    public ItemEscrowService(SMPCore plugin, String id, String fileName) {
        this.plugin = plugin;
        this.escrowIdKey = new NamespacedKey(plugin, id + "_escrow_id");
        this.escrowOwnerKey = new NamespacedKey(plugin, id + "_escrow_owner");
        this.menuPreviewKey = new NamespacedKey(plugin, "menu_preview_item");
        this.escrowFile = new File(plugin.getDataFolder(), fileName);
        this.escrowConfig = YamlConfiguration.loadConfiguration(escrowFile);
    }

    public void start(Iterable<? extends Player> players) {
        start(players, java.util.Set.of());
    }

    public void start(Iterable<? extends Player> players, java.util.Set<UUID> retainEscrowIds) {
        loadEscrows(retainEscrowIds == null ? java.util.Set.of() : retainEscrowIds);
        for (Player player : players) {
            restorePendingRecovery(player);
            sanitizeOrphanedEscrowMarkers(player);
        }
    }

    public EscrowedItem retainedEscrow(UUID escrowId) {
        return escrowId == null ? null : retainedEscrows.get(escrowId);
    }

    public void retain(EscrowedItem escrowed) {
        if (escrowed != null && knownEscrowIds.contains(escrowed.escrowId())) retainedEscrows.put(escrowed.escrowId(), escrowed);
    }

    public void shutdown() {
        saveEscrowFile();
    }

    public EscrowedItem capture(UUID transactionId, Player player, int slot, ItemStack originalItem) {
        if (isEmpty(originalItem)) {
            return null;
        }
        return capturePartial(transactionId, player, slot, originalItem, originalItem.getAmount());
    }

    public EscrowedItem capturePartial(UUID transactionId, Player player, int slot, ItemStack originalItem, int amount) {
        if (player == null || isEmpty(originalItem) || amount <= 0 || amount > originalItem.getAmount()) {
            return null;
        }
        ItemStack[] storage = player.getInventory().getStorageContents();
        if (slot < 0 || slot >= storage.length) {
            return null;
        }
        ItemStack liveItem = player.getInventory().getItem(slot);
        if (!sameStackSnapshot(liveItem, originalItem)) {
            return null;
        }
        if (hasMenuPreviewMarker(originalItem) || hasAnyEscrowMarker(originalItem)) {
            return null;
        }
        UUID escrowId = UUID.randomUUID();
        ItemStack wagerItem = cleanEscrowMarkers(originalItem);
        wagerItem.setAmount(amount);
        ItemStack marked = cleanEscrowMarkers(originalItem);
        if (!markEscrow(marked, escrowId, player.getUniqueId())) {
            return null;
        }
        ItemStack remainder = null;
        if (originalItem.getAmount() > amount) {
            remainder = cleanEscrowMarkers(originalItem);
            remainder.setAmount(originalItem.getAmount() - amount);
        }

        EscrowedItem escrowed = new EscrowedItem(
            escrowId,
            transactionId,
            player.getUniqueId(),
            player.getName(),
            wagerItem,
            slot
        );

        player.getInventory().setItem(slot, marked);
        player.updateInventory();
        if (!persistPlayerData(player, "preparing escrow " + escrowId)) {
            restoreUnjournaledItem(player, slot, originalItem, escrowId);
            return null;
        }
        if (!saveEscrowRecord(escrowed, player.getUniqueId(), player.getName(), "PREPARED")) {
            knownEscrowIds.remove(escrowId);
            restoreUnjournaledItem(player, slot, originalItem, escrowId);
            return null;
        }

        ItemStack current = player.getInventory().getItem(slot);
        if (!hasEscrowMarker(current, escrowId)) {
            rollbackPreparedCapture(player, slot, originalItem, escrowed);
            return null;
        }

        player.getInventory().setItem(slot, remainder);
        player.updateInventory();
        if (!persistPlayerData(player, "removing prepared escrow " + escrowId)) {
            rollbackPreparedCapture(player, slot, originalItem, escrowed);
            return null;
        }
        if (!saveEscrowRecord(escrowed, player.getUniqueId(), player.getName(), "ESCROWED")) {
            rollbackPreparedCapture(player, slot, originalItem, escrowed);
            return null;
        }
        return escrowed;
    }

    public boolean consume(EscrowedItem escrowed) {
        return escrowed != null && removeEscrowRecord(escrowed.escrowId());
    }

    public List<EscrowedItem> replaceWithRecoveries(
        EscrowedItem consumed,
        UUID ownerId,
        String ownerName,
        List<ItemStack> items,
        String state
    ) {
        if (consumed == null || ownerId == null || items == null || items.isEmpty()) {
            return List.of();
        }

        List<EscrowedItem> replacements = new ArrayList<>();
        for (ItemStack rawItem : items) {
            if (isEmpty(rawItem)) {
                continue;
            }
            replacements.add(new EscrowedItem(
                UUID.randomUUID(),
                consumed.transactionId(),
                ownerId,
                ownerName,
                cleanEscrowMarkers(rawItem),
                -1
            ));
        }
        if (replacements.isEmpty()) {
            return List.of();
        }

        synchronized (lock) {
            escrowConfig.set("escrows." + consumed.escrowId(), null);
            for (EscrowedItem replacement : replacements) {
                setEscrowRecord(replacement, ownerId, ownerName, state);
            }
            if (!saveEscrowFile()) {
                escrowConfig = YamlConfiguration.loadConfiguration(escrowFile);
                rebuildKnownEscrowIds();
                return List.of();
            }
            knownEscrowIds.remove(consumed.escrowId());
            for (EscrowedItem replacement : replacements) {
                knownEscrowIds.add(replacement.escrowId());
            }
            return replacements;
        }
    }

    public boolean replaceEscrowsWithRecoveries(
        Collection<EscrowedItem> consumed,
        Collection<EscrowPayout> payouts,
        String state
    ) {
        if (consumed == null || consumed.isEmpty() || payouts == null || payouts.isEmpty()) {
            return false;
        }

        Map<UUID, EscrowedItem> consumedById = new java.util.LinkedHashMap<>();
        for (EscrowedItem escrowed : consumed) {
            if (escrowed == null || escrowed.escrowId() == null || isEmpty(escrowed.item())) {
                return false;
            }
            consumedById.put(escrowed.escrowId(), escrowed);
        }
        if (consumedById.size() != consumed.size()) {
            return false;
        }

        List<EscrowedItem> replacements = new ArrayList<>();
        for (EscrowPayout payout : payouts) {
            if (payout == null || payout.ownerId() == null || isEmpty(payout.item())
                || hasMenuPreviewMarker(payout.item()) || hasAnyEscrowMarker(payout.item())) {
                return false;
            }
            replacements.add(new EscrowedItem(
                UUID.randomUUID(),
                null,
                payout.ownerId(),
                payout.ownerName() == null || payout.ownerName().isBlank() ? "Player" : payout.ownerName(),
                cleanEscrowMarkers(payout.item()),
                -1
            ));
        }
        if (replacements.isEmpty()) {
            return false;
        }

        synchronized (lock) {
            for (UUID escrowId : consumedById.keySet()) {
                if (!knownEscrowIds.contains(escrowId) || !escrowConfig.contains("escrows." + escrowId)) {
                    return false;
                }
            }
            for (UUID escrowId : consumedById.keySet()) {
                escrowConfig.set("escrows." + escrowId, null);
            }
            for (EscrowedItem replacement : replacements) {
                setEscrowRecord(replacement, replacement.ownerId(), replacement.ownerName(), state == null ? "PAYOUT" : state);
            }
            if (!saveEscrowFile()) {
                escrowConfig = YamlConfiguration.loadConfiguration(escrowFile);
                rebuildKnownEscrowIds();
                return false;
            }
            consumedById.keySet().forEach(knownEscrowIds::remove);
            consumedById.keySet().forEach(retainedEscrows::remove);
            for (EscrowedItem replacement : replacements) {
                knownEscrowIds.add(replacement.escrowId());
            }
        }

        for (EscrowedItem replacement : replacements) {
            addPendingRecovery(new EscrowRecovery(
                replacement.escrowId(),
                replacement.ownerId(),
                replacement.ownerName(),
                cleanEscrowMarkers(replacement.item())
            ));
        }
        return true;
    }

    public boolean give(Player player, EscrowedItem escrowed) {
        if (player == null || escrowed == null || escrowed.item() == null || escrowed.item().getType().isAir()) {
            return false;
        }
        if (!player.isOnline() || !hasEmptyStorageSlot(player)) {
            return false;
        }
        ItemStack marked = escrowed.item().clone();
        if (!markEscrow(marked, escrowed.escrowId(), player.getUniqueId())) {
            return false;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(marked);
        if (!leftovers.isEmpty()) {
            removeEscrowMarkedItems(player.getInventory(), escrowed.escrowId());
            player.updateInventory();
            return false;
        }
        player.updateInventory();
        if (!persistPlayerData(player, "delivering escrow " + escrowed.escrowId())) {
            removeEscrowMarkedItems(player.getInventory(), escrowed.escrowId());
            player.updateInventory();
            persistPlayerData(player, "rolling back escrow delivery " + escrowed.escrowId());
            return false;
        }
        if (!removeEscrowRecord(escrowed.escrowId())) {
            removeEscrowMarkedItems(player.getInventory(), escrowed.escrowId());
            player.updateInventory();
            persistPlayerData(player, "rolling back escrow delivery " + escrowed.escrowId());
            return false;
        }
        stripEscrowMarker(player.getInventory(), escrowed.escrowId());
        player.updateInventory();
        persistPlayerData(player, "finalizing escrow delivery " + escrowed.escrowId());
        return true;
    }

    public boolean retarget(EscrowedItem escrowed, UUID ownerId, String ownerName, String state) {
        return saveEscrowRecord(escrowed, ownerId, ownerName, state);
    }

    public void queueRecovery(UUID ownerId, String ownerName, EscrowedItem escrowed) {
        boolean saved = saveEscrowRecord(escrowed, ownerId, ownerName, "RETURNING");
        if (!saved) {
            plugin.getLogger().severe("Failed to save escrow recovery for " + ownerName + " (" + escrowed.escrowId() + ").");
        }
        addPendingRecovery(new EscrowRecovery(escrowed.escrowId(), ownerId, ownerName, cleanEscrowMarkers(escrowed.item())));
    }

    public boolean deliverOrQueueGenerated(Player player, UUID ownerId, String ownerName, ItemStack item, String state) {
        if (ownerId == null || isEmpty(item) || hasMenuPreviewMarker(item) || hasAnyEscrowMarker(item)) return false;
        EscrowedItem generated = new EscrowedItem(
            UUID.randomUUID(), UUID.randomUUID(), ownerId,
            ownerName == null || ownerName.isBlank() ? "Player" : ownerName,
            cleanEscrowMarkers(item), -1
        );
        if (!saveEscrowRecord(generated, ownerId, generated.ownerName(), state == null ? "PAYOUT" : state)) {
            plugin.getLogger().severe("Failed to journal generated payout for " + generated.ownerName() + ".");
            return false;
        }
        if (player != null && player.isOnline() && give(player, generated)) return true;
        addPendingRecovery(new EscrowRecovery(generated.escrowId(), ownerId, generated.ownerName(), cleanEscrowMarkers(item)));
        return false;
    }

    public boolean restorePendingRecovery(Player player) {
        List<EscrowRecovery> recoveries = pendingRecoveries.get(player.getUniqueId());
        if (recoveries == null || recoveries.isEmpty()) {
            return false;
        }

        List<EscrowRecovery> remaining = new ArrayList<>();
        boolean changed = false;
        for (EscrowRecovery recovery : recoveries) {
            boolean foundMarked = containsEscrowMarker(player, recovery.escrowId());
            if (foundMarked) {
                if (!persistPlayerData(player, "confirming recovered escrow " + recovery.escrowId())) {
                    remaining.add(recovery);
                } else if (removeEscrowRecord(recovery.escrowId())) {
                    stripEscrowMarker(player, recovery.escrowId());
                    player.updateInventory();
                    persistPlayerData(player, "finalizing recovered escrow " + recovery.escrowId());
                    changed = true;
                } else {
                    removeEscrowMarkedItems(player, recovery.escrowId());
                    player.updateInventory();
                    persistPlayerData(player, "rolling back unacknowledged escrow recovery " + recovery.escrowId());
                    remaining.add(recovery);
                }
                continue;
            }
            if (!hasEmptyStorageSlot(player)) {
                remaining.add(recovery);
                continue;
            }
            EscrowedItem escrowed = new EscrowedItem(
                recovery.escrowId(),
                null,
                recovery.ownerId(),
                recovery.ownerName(),
                recovery.item(),
                -1
            );
            if (give(player, escrowed)) {
                changed = true;
            } else {
                remaining.add(recovery);
            }
        }

        if (remaining.isEmpty()) {
            pendingRecoveries.remove(player.getUniqueId());
        } else {
            pendingRecoveries.put(player.getUniqueId(), remaining);
        }
        if (changed) {
            player.updateInventory();
        }
        return changed;
    }

    private boolean hasEmptyStorageSlot(Player player) {
        if (player == null) {
            return false;
        }
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (isEmpty(item)) {
                return true;
            }
        }
        return false;
    }

    static boolean sameStackSnapshot(ItemStack first, ItemStack second) {
        if (isEmptyStack(first) || isEmptyStack(second)) {
            return isEmptyStack(first) && isEmptyStack(second);
        }
        return first.getAmount() == second.getAmount() && first.isSimilar(second);
    }

    private static boolean isEmptyStack(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    public boolean hasPendingRecovery(Player player) {
        List<EscrowRecovery> recoveries = pendingRecoveries.get(player.getUniqueId());
        return recoveries != null && !recoveries.isEmpty();
    }

    public boolean hasKnownEscrowMarker(ItemStack item) {
        UUID itemEscrowId = escrowId(item);
        return itemEscrowId != null && knownEscrowIds.contains(itemEscrowId);
    }

    public boolean hasAnyEscrowMarker(ItemStack item) {
        if (isEmpty(item)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        String namespace = escrowIdKey.getNamespace();
        return meta.getPersistentDataContainer().getKeys().stream()
            .filter(key -> key.getNamespace().equals(namespace))
            .map(NamespacedKey::getKey)
            .anyMatch(key -> key.endsWith("_escrow_id") || key.endsWith("_escrow_owner"));
    }

    public boolean hasMenuPreviewMarker(ItemStack item) {
        if (isEmpty(item)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(menuPreviewKey, PersistentDataType.BYTE);
    }

    public ItemStack cleanEscrowMarkers(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemStack clean = item.clone();
        ItemMeta meta = clean.getItemMeta();
        if (meta != null) {
            String namespace = escrowIdKey.getNamespace();
            for (NamespacedKey key : List.copyOf(meta.getPersistentDataContainer().getKeys())) {
                if (!key.getNamespace().equals(namespace)) {
                    continue;
                }
                String rawKey = key.getKey();
                if (rawKey.endsWith("_escrow_id") || rawKey.endsWith("_escrow_owner")) {
                    meta.getPersistentDataContainer().remove(key);
                }
            }
            clean.setItemMeta(meta);
        }
        return clean;
    }

    public void sanitizeOrphanedEscrowMarkers(Player player) {
        boolean changed = false;
        changed |= sanitizeOrphanedEscrowMarkers(player.getInventory());
        changed |= sanitizeOrphanedEscrowMarkers(player.getEnderChest());
        ItemStack cursor = player.getItemOnCursor();
        UUID cursorEscrowId = escrowId(cursor);
        if (cursorEscrowId != null && !knownEscrowIds.contains(cursorEscrowId)) {
            player.setItemOnCursor(cleanEscrowMarkers(cursor));
            changed = true;
        }
        if (changed) {
            player.updateInventory();
            persistPlayerData(player, "cleaning orphaned escrow markers");
        }
    }

    private boolean sanitizeOrphanedEscrowMarkers(Inventory inventory) {
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            UUID itemEscrowId = escrowId(item);
            if (itemEscrowId == null || knownEscrowIds.contains(itemEscrowId)) {
                continue;
            }
            inventory.setItem(slot, cleanEscrowMarkers(item));
            changed = true;
        }
        return changed;
    }

    public boolean hasRoomForItems(Player player, int clearedSlot, List<ItemStack> items) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        ItemStack[] simulated = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            simulated[i] = contents[i] == null ? null : contents[i].clone();
        }
        if (clearedSlot >= 0 && clearedSlot < simulated.length) {
            simulated[clearedSlot] = null;
        }

        for (ItemStack rawItem : items) {
            if (isEmpty(rawItem)) {
                continue;
            }
            ItemStack item = cleanEscrowMarkers(rawItem);
            int remaining = item.getAmount();
            int maxStack = Math.max(1, item.getMaxStackSize());
            for (int i = 0; i < simulated.length && remaining > 0; i++) {
                ItemStack existing = simulated[i];
                if (isEmpty(existing) || !existing.isSimilar(item)) {
                    continue;
                }
                int room = Math.max(0, Math.min(maxStack, existing.getMaxStackSize()) - existing.getAmount());
                int moved = Math.min(room, remaining);
                existing.setAmount(existing.getAmount() + moved);
                remaining -= moved;
            }
            for (int i = 0; i < simulated.length && remaining > 0; i++) {
                if (!isEmpty(simulated[i])) {
                    continue;
                }
                int moved = Math.min(maxStack, remaining);
                ItemStack placed = item.clone();
                placed.setAmount(moved);
                simulated[i] = placed;
                remaining -= moved;
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    public boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private void restoreUnjournaledItem(Player player, int slot, ItemStack originalItem, UUID escrowId) {
        player.getInventory().setItem(slot, cleanEscrowMarkers(originalItem));
        player.updateInventory();
        persistPlayerData(player, "rolling back unjournaled escrow " + escrowId);
    }

    private void rollbackPreparedCapture(Player player, int slot, ItemStack originalItem, EscrowedItem escrowed) {
        ItemStack markedOriginal = cleanEscrowMarkers(originalItem);
        if (!markEscrow(markedOriginal, escrowed.escrowId(), player.getUniqueId())) {
            plugin.getLogger().severe("Failed to mark rollback item for escrow " + escrowed.escrowId() + ".");
            deferPreparedRecovery(player, slot, originalItem, escrowed);
            return;
        }

        player.getInventory().setItem(slot, markedOriginal);
        player.updateInventory();
        if (!persistPlayerData(player, "restoring prepared escrow " + escrowed.escrowId())) {
            deferPreparedRecovery(player, slot, originalItem, escrowed);
            return;
        }
        if (!removeEscrowRecord(escrowed.escrowId())) {
            deferPreparedRecovery(player, slot, originalItem, escrowed);
            return;
        }

        stripEscrowMarker(player.getInventory(), escrowed.escrowId());
        player.updateInventory();
        persistPlayerData(player, "finalizing prepared escrow rollback " + escrowed.escrowId());
    }

    private void deferPreparedRecovery(Player player, int slot, ItemStack originalItem, EscrowedItem escrowed) {
        int remainderAmount = Math.max(0, originalItem.getAmount() - escrowed.item().getAmount());
        ItemStack remainder = null;
        if (remainderAmount > 0) {
            remainder = cleanEscrowMarkers(originalItem);
            remainder.setAmount(remainderAmount);
        }
        player.getInventory().setItem(slot, remainder);
        player.updateInventory();
        persistPlayerData(player, "deferring prepared escrow recovery " + escrowed.escrowId());
        addPendingRecovery(new EscrowRecovery(
            escrowed.escrowId(),
            player.getUniqueId(),
            player.getName(),
            cleanEscrowMarkers(escrowed.item())
        ));
    }

    private boolean persistPlayerData(Player player, String action) {
        try {
            player.saveData();
            return true;
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Failed to save player data while " + action + " for "
                + player.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    private void loadEscrows(java.util.Set<UUID> retainEscrowIds) {
        synchronized (lock) {
            escrowConfig = YamlConfiguration.loadConfiguration(escrowFile);
            ConfigurationSection section = escrowConfig.getConfigurationSection("escrows");
            if (section == null) {
                return;
            }
            for (String key : section.getKeys(false)) {
                UUID escrowId = parseUuid(key);
                if (escrowId == null) {
                    continue;
                }
                String path = "escrows." + key;
                UUID ownerId = parseUuid(escrowConfig.getString(path + ".owner"));
                if (ownerId == null) {
                    continue;
                }
                String ownerName = escrowConfig.getString(path + ".owner-name", "Player");
                ItemStack item = escrowConfig.getItemStack(path + ".item");
                if (isEmpty(item) || hasMenuPreviewMarker(item)) {
                    escrowConfig.set(path, null);
                    continue;
                }
                knownEscrowIds.add(escrowId);
                UUID transactionId = parseUuid(escrowConfig.getString(path + ".transaction"));
                EscrowedItem escrowed = new EscrowedItem(escrowId, transactionId, ownerId, ownerName, cleanEscrowMarkers(item), -1);
                if (retainEscrowIds.contains(escrowId)) retainedEscrows.put(escrowId, escrowed);
                else addPendingRecovery(new EscrowRecovery(escrowId, ownerId, ownerName, cleanEscrowMarkers(item)));
            }
            saveEscrowFile();
        }
    }

    private boolean saveEscrowRecord(EscrowedItem escrowed, UUID ownerId, String ownerName, String state) {
        synchronized (lock) {
            setEscrowRecord(escrowed, ownerId, ownerName, state);
            boolean saved = saveEscrowFile();
            if (saved) {
                knownEscrowIds.add(escrowed.escrowId());
            } else {
                escrowConfig = YamlConfiguration.loadConfiguration(escrowFile);
                rebuildKnownEscrowIds();
            }
            return saved;
        }
    }

    private void setEscrowRecord(EscrowedItem escrowed, UUID ownerId, String ownerName, String state) {
        String path = "escrows." + escrowed.escrowId();
        escrowConfig.set(path + ".owner", ownerId.toString());
        escrowConfig.set(path + ".owner-name", ownerName);
        escrowConfig.set(path + ".transaction", escrowed.transactionId() == null ? null : escrowed.transactionId().toString());
        escrowConfig.set(path + ".state", state);
        escrowConfig.set(path + ".item", cleanEscrowMarkers(escrowed.item()));
    }

    private void rebuildKnownEscrowIds() {
        knownEscrowIds.clear();
        ConfigurationSection section = escrowConfig.getConfigurationSection("escrows");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            UUID escrowId = parseUuid(key);
            if (escrowId != null) {
                knownEscrowIds.add(escrowId);
            }
        }
    }

    private void addPendingRecovery(EscrowRecovery recovery) {
        pendingRecoveries.compute(recovery.ownerId(), (ignored, existing) -> {
            List<EscrowRecovery> updated = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            updated.removeIf(entry -> entry.escrowId().equals(recovery.escrowId()));
            updated.add(recovery);
            return updated;
        });
    }

    private boolean removeEscrowRecord(UUID escrowId) {
        synchronized (lock) {
            escrowConfig.set("escrows." + escrowId, null);
            boolean saved = saveEscrowFile();
            if (saved) {
                knownEscrowIds.remove(escrowId);
                retainedEscrows.remove(escrowId);
            } else {
                escrowConfig = YamlConfiguration.loadConfiguration(escrowFile);
                rebuildKnownEscrowIds();
            }
            return saved;
        }
    }

    private boolean saveEscrowFile() {
        synchronized (lock) {
            Path temporary = null;
            try {
                File parent = escrowFile.getParentFile();
                Path parentPath = parent == null ? escrowFile.toPath().toAbsolutePath().getParent() : parent.toPath();
                if (parentPath == null) {
                    throw new IOException("No parent directory is available");
                }
                Files.createDirectories(parentPath);
                temporary = Files.createTempFile(parentPath, escrowFile.getName() + ".", ".tmp");
                escrowConfig.save(temporary.toFile());
                try {
                    Files.move(temporary, escrowFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, escrowFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                return true;
            } catch (IOException ex) {
                plugin.getLogger().severe("Failed to save item escrow: " + ex.getMessage());
                return false;
            } finally {
                if (temporary != null) {
                    try {
                        Files.deleteIfExists(temporary);
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    private boolean markEscrow(ItemStack item, UUID escrowId, UUID ownerId) {
        if (isEmpty(item)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        meta.getPersistentDataContainer().set(escrowIdKey, PersistentDataType.STRING, escrowId.toString());
        meta.getPersistentDataContainer().set(escrowOwnerKey, PersistentDataType.STRING, ownerId.toString());
        item.setItemMeta(meta);
        return true;
    }

    private boolean hasEscrowMarker(ItemStack item, UUID escrowId) {
        UUID itemEscrowId = escrowId(item);
        return itemEscrowId != null && itemEscrowId.equals(escrowId);
    }

    private UUID escrowId(ItemStack item) {
        if (isEmpty(item)) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return parseUuid(meta.getPersistentDataContainer().get(escrowIdKey, PersistentDataType.STRING));
    }

    private boolean stripEscrowMarker(Inventory inventory, UUID escrowId) {
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!hasEscrowMarker(item, escrowId)) {
                continue;
            }
            inventory.setItem(slot, cleanEscrowMarkers(item));
            changed = true;
        }
        return changed;
    }

    private boolean stripEscrowMarker(Player player, UUID escrowId) {
        boolean changed = stripEscrowMarker(player.getInventory(), escrowId);
        changed |= stripEscrowMarker(player.getEnderChest(), escrowId);
        if (hasEscrowMarker(player.getItemOnCursor(), escrowId)) {
            player.setItemOnCursor(cleanEscrowMarkers(player.getItemOnCursor()));
            changed = true;
        }
        return changed;
    }

    private boolean containsEscrowMarker(Inventory inventory, UUID escrowId) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (hasEscrowMarker(inventory.getItem(slot), escrowId)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsEscrowMarker(Player player, UUID escrowId) {
        return containsEscrowMarker(player.getInventory(), escrowId)
            || containsEscrowMarker(player.getEnderChest(), escrowId)
            || hasEscrowMarker(player.getItemOnCursor(), escrowId);
    }

    private int removeEscrowMarkedItems(Inventory inventory, UUID escrowId) {
        int removed = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!hasEscrowMarker(item, escrowId)) {
                continue;
            }
            inventory.setItem(slot, null);
            removed++;
        }
        return removed;
    }

    private int removeEscrowMarkedItems(Player player, UUID escrowId) {
        int removed = removeEscrowMarkedItems(player.getInventory(), escrowId);
        removed += removeEscrowMarkedItems(player.getEnderChest(), escrowId);
        if (hasEscrowMarker(player.getItemOnCursor(), escrowId)) {
            player.setItemOnCursor(null);
            removed++;
        }
        return removed;
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record EscrowedItem(UUID escrowId, UUID transactionId, UUID ownerId, String ownerName, ItemStack item, int sourceSlot) {}

    public record EscrowPayout(UUID ownerId, String ownerName, ItemStack item) {}

    private record EscrowRecovery(UUID escrowId, UUID ownerId, String ownerName, ItemStack item) {}
}
