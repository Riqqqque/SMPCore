package me.rique.smpcore.recovery;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.item.SalvagingDepotListener;
import me.rique.smpcore.recovery.RiskyInventoryRecoveryRepository.RestoreToken;
import me.rique.smpcore.recovery.RiskyInventoryRecoveryRepository.SerializedItem;
import me.rique.smpcore.recovery.RiskyInventoryRecoveryRepository.SerializedSnapshot;
import me.rique.smpcore.recovery.RiskyInventoryRecoveryRepository.SnapshotHandle;
import me.rique.smpcore.recovery.RiskyInventoryRecoveryRepository.SnapshotLookup;
import me.rique.smpcore.util.MenuDupeGuardListener;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class RiskyInventoryRecoveryManager implements Listener {

    private static final int FLUSH_DELAY_TICKS = 10;
    private static final int MAX_ITEM_BYTES = 2 * 1024 * 1024;
    private static final int MAX_SNAPSHOT_BYTES = 10 * 1024 * 1024;
    private static final int MAX_PENDING_ITEMS = 96;

    private final SMPCore plugin;
    private final RiskyInventoryRecoveryRepository repository;
    private final NamespacedKey restoreMarkerKey;
    private final Map<UUID, PendingCapture> pending = new LinkedHashMap<>();
    private final Map<UUID, BukkitTask> flushTasks = new LinkedHashMap<>();
    private final Map<String, String> lastSnapshotDigests = new ConcurrentHashMap<>();
    private final Map<Class<?>, Surface> trackedSurfaceCache = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor writer;

    public RiskyInventoryRecoveryManager(SMPCore plugin) {
        this.plugin = plugin;
        this.repository = new RiskyInventoryRecoveryRepository(plugin);
        this.restoreMarkerKey = new NamespacedKey(plugin, "inventory_recovery_restore");
        this.writer = new ThreadPoolExecutor(
            1,
            1,
            30L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(256),
            runnable -> {
                Thread thread = new Thread(runnable, "SMPCore-InventoryRecovery");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Surface surface = surface(event.getView().getTopInventory());
        if (surface == null || surface.bulkStorage()) return;
        captureTrackedSlots(player, event.getView().getTopInventory(), surface, "menu opened", false);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        Surface surface = surface(top);
        if (surface == null) return;

        List<Candidate> items = new ArrayList<>();
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < top.getSize() && surface.tracks(rawSlot)) {
            add(items, event.getCurrentItem(), "menu", rawSlot);
        } else if (rawSlot >= top.getSize()) {
            add(items, event.getCurrentItem(), "player", event.getSlot());
        }
        add(items, event.getCursor(), "cursor", -1);
        int hotbar = event.getHotbarButton();
        if (hotbar >= 0) add(items, player.getInventory().getItem(hotbar), "hotbar", hotbar);
        if (event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND) {
            add(items, player.getInventory().getItemInOffHand(), "offhand", 40);
        }
        queue(player, surface, "click " + event.getAction().name().toLowerCase(java.util.Locale.ROOT), items, false);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        Surface surface = surface(top);
        if (surface == null || event.getRawSlots().stream().noneMatch(surface::tracks)) return;
        List<Candidate> items = new ArrayList<>();
        add(items, event.getOldCursor(), "cursor", -1);
        queue(player, surface, "inventory drag", items, false);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        Surface surface = surface(top);
        if (surface == null) return;
        if (!surface.bulkStorage()) captureTrackedSlots(player, top, surface, "menu closed", true);
        else {
            List<Candidate> items = new ArrayList<>();
            add(items, player.getItemOnCursor(), "cursor", -1);
            if (!items.isEmpty()) queue(player, surface, "storage closed", items, true);
            else flushNow(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        reconcilePlayer(event.getPlayer());
    }

    public void start() {
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::reconcilePlayer));
    }

    private void reconcilePlayer(Player player) {
        UUID playerId = player.getUniqueId();
        List<String> deliveredMarkers = recoveryMarkers(player);
        submitWriter(() -> {
            Set<String> acknowledged = new java.util.HashSet<>();
            for (String marker : deliveredMarkers) {
                if (repository.reconcileDeliveredMarker(playerId, marker)) acknowledged.add(marker);
            }
            for (RestoreToken token : repository.restoring(playerId)) {
                if (!deliveredMarkers.contains(marker(token))) repository.rollbackRestore(token);
            }
            if (!acknowledged.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> stripAcknowledgedMarkers(player, acknowledged));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        flushNow(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        flushNow(event.getPlayer().getUniqueId());
    }

    public List<SnapshotInfo> listSnapshots(UUID playerId) {
        return repository.list(playerId).stream().map(this::info).toList();
    }

    public SnapshotResult findSnapshot(UUID playerId, String selector) {
        SnapshotLookup lookup = repository.find(playerId, selector);
        if (lookup.handle() == null) return new SnapshotResult(null, lookup.error());
        return new SnapshotResult(detail(lookup.handle()), null);
    }

    public RestoreResult restore(Player player, String selector, int itemNumber) {
        if (player == null || !player.isOnline()) return new RestoreResult(false, "The player must be online.", null);
        if (player.getOpenInventory().getTopInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
            return new RestoreResult(false, "The player must close every inventory first.", null);
        }
        ItemStack cursor = player.getItemOnCursor();
        if (!empty(cursor)) return new RestoreResult(false, "The player must clear their cursor first.", null);
        if (player.getInventory().firstEmpty() < 0) return new RestoreResult(false, "The player needs one completely empty inventory slot.", null);

        RiskyInventoryRecoveryRepository.RestoreTransition transition = repository.beginRestore(
            player.getUniqueId(), selector, itemNumber
        );
        RestoreToken token = transition.token();
        if (token == null) return new RestoreResult(false, transition.error(), null);
        SerializedItem stored = token.snapshot().items().get(token.itemIndex());
        ItemStack item;
        try {
            item = ItemStack.deserializeBytes(stored.bytes()).clone();
        } catch (RuntimeException ex) {
            repository.rollbackRestore(token);
            return new RestoreResult(false, "The recorded item could not be decoded safely.", null);
        }
        if (empty(item)) {
            repository.rollbackRestore(token);
            return new RestoreResult(false, "The recorded item failed validation.", null);
        }
        if (plugin.getBackpackListener() != null && plugin.getBackpackListener().isBackpack(item)) {
            repository.rollbackRestore(token);
            return new RestoreResult(false, "Backpacks must be restored with /backpackadmin so their storage ID stays unique.", null);
        }
        if (!setRestoreMarker(item, token)) {
            repository.rollbackRestore(token);
            return new RestoreResult(false, "The recorded item failed validation.", null);
        }

        int slot = player.getInventory().firstEmpty();
        player.getInventory().setItem(slot, item);
        try {
            player.saveData();
        } catch (RuntimeException ex) {
            player.getInventory().setItem(slot, null);
            repository.rollbackRestore(token);
            return new RestoreResult(false, "The player save failed; nothing was restored.", null);
        }
        if (!repository.completeRestore(token)) {
            player.getInventory().setItem(slot, null);
            try { player.saveData(); } catch (RuntimeException ignored) { }
            repository.rollbackRestore(token);
            return new RestoreResult(false, "The recovery record could not be finalized; the item was removed again.", null);
        }
        stripRestoreMarker(item);
        player.getInventory().setItem(slot, item);
        try { player.saveData(); }
        catch (RuntimeException ex) {
            plugin.getLogger().warning("Restored item remained safely marked for reconciliation for " + player.getName() + ".");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                try { player.saveData(); } catch (RuntimeException ignored) { }
            }, 20L);
        }
        player.updateInventory();
        plugin.getLogger().warning("Restored risky-inventory snapshot " + token.snapshot().id() + " item "
            + (token.itemIndex() + 1) + " to " + player.getName() + ".");
        return new RestoreResult(true, "Restored " + displayName(item) + " from " + token.snapshot().surface() + ".", item.clone());
    }

    public void shutdown() {
        for (UUID playerId : List.copyOf(pending.keySet())) flushNow(playerId);
        flushTasks.values().forEach(BukkitTask::cancel);
        flushTasks.clear();
        writer.shutdown();
        try {
            if (!writer.awaitTermination(10L, TimeUnit.SECONDS)) {
                List<Runnable> cancelled = writer.shutdownNow();
                plugin.getLogger().severe("Inventory recovery writer did not finish within 10 seconds; cancelled "
                    + cancelled.size() + " queued snapshot(s).");
                writer.awaitTermination(2L, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        } finally {
            pending.clear();
            flushTasks.clear();
            lastSnapshotDigests.clear();
            trackedSurfaceCache.clear();
        }
    }

    private void captureTrackedSlots(Player player, Inventory inventory, Surface surface, String reason, boolean immediate) {
        List<Candidate> items = new ArrayList<>();
        for (int slot : surface.slots()) {
            if (slot >= 0 && slot < inventory.getSize()) add(items, inventory.getItem(slot), "menu", slot);
        }
        add(items, player.getItemOnCursor(), "cursor", -1);
        queue(player, surface, reason, items, immediate);
    }

    private void queue(Player player, Surface surface, String reason, List<Candidate> items, boolean immediate) {
        if (player == null || surface == null || items == null || items.isEmpty()) return;
        UUID playerId = player.getUniqueId();
        PendingCapture current = pending.get(playerId);
        if (current != null && !current.surface().name().equals(surface.name())) flushNow(playerId);
        current = pending.computeIfAbsent(playerId, ignored -> new PendingCapture(
            playerId, player.getName(), surface, reason, new ArrayList<>()
        ));
        current.reason(reason);
        for (Candidate item : items) {
            if (current.items().size() >= MAX_PENDING_ITEMS) break;
            current.items().add(item);
        }
        if (immediate) {
            flushNow(playerId);
            return;
        }
        if (!flushTasks.containsKey(playerId)) {
            BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> flushNow(playerId), FLUSH_DELAY_TICKS);
            flushTasks.put(playerId, task);
        }
    }

    private void flushNow(UUID playerId) {
        BukkitTask task = flushTasks.remove(playerId);
        if (task != null) task.cancel();
        PendingCapture capture = pending.remove(playerId);
        if (capture == null || capture.items().isEmpty()) return;

        Map<String, SerializedItem> unique = new LinkedHashMap<>();
        int total = 0;
        for (Candidate candidate : capture.items()) {
            byte[] bytes;
            try { bytes = candidate.item().serializeAsBytes(); }
            catch (RuntimeException ex) { continue; }
            if (bytes.length < 1 || bytes.length > MAX_ITEM_BYTES || total + bytes.length > MAX_SNAPSHOT_BYTES) continue;
            String digest = sha256(bytes);
            String key = digest + "|" + candidate.source() + "|" + candidate.slot();
            if (unique.putIfAbsent(key, new SerializedItem(candidate.source(), candidate.slot(),
                RiskyInventoryRecoveryRepository.AVAILABLE, bytes)) == null) total += bytes.length;
        }
        if (unique.isEmpty()) return;
        String snapshotDigest = snapshotDigest(unique.values());
        String digestKey = capture.playerId() + "|" + capture.surface().name();
        String previousDigest = lastSnapshotDigests.put(digestKey, snapshotDigest);
        if (snapshotDigest.equals(previousDigest)) return;
        SerializedSnapshot snapshot = new SerializedSnapshot(
            UUID.randomUUID(), capture.playerId(), capture.playerName(), System.currentTimeMillis(),
            capture.surface().name(), capture.reason(), List.copyOf(unique.values())
        );
        if (!submitWriter(() -> {
            if (!repository.save(snapshot)) lastSnapshotDigests.remove(digestKey, snapshotDigest);
        })) lastSnapshotDigests.remove(digestKey, snapshotDigest);
    }

    private Surface surface(Inventory inventory) {
        if (inventory == null) return null;
        InventoryHolder holder = inventory.getHolder(false);
        if (holder instanceof MenuDupeGuardListener.RecoveryTrackedMenuHolder tracked) {
            return trackedSurfaceCache.computeIfAbsent(holder.getClass(), ignored -> {
                int[] slots = tracked.recoverySlots();
                return new Surface(tracked.recoverySurface(), slots == null ? new int[0] : slots.clone(),
                    slots != null && slots.length > 9);
            });
        }
        if (inventory.getType() != org.bukkit.event.inventory.InventoryType.CHEST) return null;
        SalvagingDepotListener depot = plugin.getSalvagingDepotListener();
        if (depot != null && depot.isRecoveryTrackedInventory(inventory)) {
            return new Surface("Salvaging Depot", new int[0], true);
        }
        return null;
    }

    private List<String> recoveryMarkers(Player player) {
        List<String> markers = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            String marker = restoreMarker(item);
            if (marker != null && !marker.isBlank()) markers.add(marker);
        }
        return List.copyOf(markers);
    }

    private void stripAcknowledgedMarkers(Player player, Set<String> markers) {
        if (player == null || !player.isOnline()) return;
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (markers.contains(restoreMarker(item))) {
                stripRestoreMarker(item);
                player.getInventory().setItem(slot, item);
                changed = true;
            }
        }
        if (changed) {
            try { player.saveData(); } catch (RuntimeException ignored) { }
            player.updateInventory();
        }
    }

    private boolean submitWriter(Runnable operation) {
        try {
            writer.execute(operation);
            return true;
        } catch (RejectedExecutionException ex) {
            plugin.getLogger().severe("Inventory recovery writer queue filled; a recovery operation was not queued.");
            return false;
        }
    }

    private boolean setRestoreMarker(ItemStack item, RestoreToken token) {
        if (empty(item)) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        meta.getPersistentDataContainer().set(restoreMarkerKey, PersistentDataType.STRING, marker(token));
        item.setItemMeta(meta);
        return true;
    }

    private void stripRestoreMarker(ItemStack item) {
        if (empty(item)) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().remove(restoreMarkerKey);
        item.setItemMeta(meta);
    }

    private String restoreMarker(ItemStack item) {
        if (empty(item)) return null;
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(restoreMarkerKey, PersistentDataType.STRING);
    }

    private static String marker(RestoreToken token) {
        return token.snapshot().id() + ":" + token.itemIndex();
    }

    private SnapshotInfo info(SnapshotHandle handle) {
        SerializedSnapshot snapshot = handle.snapshot();
        long available = snapshot.items().stream().filter(item -> item.state() == RiskyInventoryRecoveryRepository.AVAILABLE).count();
        return new SnapshotInfo(RiskyInventoryRecoveryRepository.shortId(snapshot.id()), snapshot.id(), snapshot.createdAt(),
            snapshot.surface(), snapshot.reason(), snapshot.items().size(), (int) available);
    }

    private SnapshotDetail detail(SnapshotHandle handle) {
        SerializedSnapshot snapshot = handle.snapshot();
        List<RecoveryItem> items = new ArrayList<>();
        for (int index = 0; index < snapshot.items().size(); index++) {
            SerializedItem value = snapshot.items().get(index);
            try {
                ItemStack item = ItemStack.deserializeBytes(value.bytes());
                items.add(new RecoveryItem(index + 1, value.source(), value.slot(), value.state(), item));
            } catch (RuntimeException ex) {
                items.add(new RecoveryItem(index + 1, value.source(), value.slot(), value.state(), null));
            }
        }
        return new SnapshotDetail(info(handle), List.copyOf(items));
    }

    private static void add(List<Candidate> items, ItemStack item, String source, int slot) {
        if (!empty(item)) items.add(new Candidate(source, slot, item.clone()));
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    public static String displayName(ItemStack item) {
        if (empty(item)) return "invalid item";
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        }
        return item.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    public static String stateName(byte state) {
        return switch (state) {
            case RiskyInventoryRecoveryRepository.AVAILABLE -> "available";
            case RiskyInventoryRecoveryRepository.RESTORING -> "restoring";
            case RiskyInventoryRecoveryRepository.CONSUMED -> "restored";
            default -> "invalid";
        };
    }

    static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }

    static String snapshotDigest(Iterable<SerializedItem> items) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (SerializedItem item : items) {
                digest.update(item.source().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(item.slot()).array());
                digest.update(item.bytes());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record Surface(String name, int[] slots, boolean bulkStorage) {
        boolean tracks(int slot) { return bulkStorage || Arrays.stream(slots).anyMatch(value -> value == slot); }
    }
    private record Candidate(String source, int slot, ItemStack item) { }
    private static final class PendingCapture {
        private final UUID playerId;
        private final String playerName;
        private final Surface surface;
        private String reason;
        private final List<Candidate> items;
        private PendingCapture(UUID playerId, String playerName, Surface surface, String reason, List<Candidate> items) {
            this.playerId = playerId; this.playerName = playerName; this.surface = surface; this.reason = reason; this.items = items;
        }
        UUID playerId() { return playerId; }
        String playerName() { return playerName; }
        Surface surface() { return surface; }
        String reason() { return reason; }
        void reason(String value) { reason = value; }
        List<Candidate> items() { return items; }
    }

    public record SnapshotInfo(String shortId, UUID id, long createdAt, String surface, String reason,
                               int itemCount, int availableCount) { }
    public record RecoveryItem(int number, String source, int slot, byte state, ItemStack item) { }
    public record SnapshotDetail(SnapshotInfo info, List<RecoveryItem> items) { }
    public record SnapshotResult(SnapshotDetail snapshot, String error) { }
    public record RestoreResult(boolean restored, String message, ItemStack item) { }
}
