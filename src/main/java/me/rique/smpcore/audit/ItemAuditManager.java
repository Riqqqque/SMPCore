package me.rique.smpcore.audit;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.awakening.AwakeningTableListener;
import me.rique.smpcore.backpack.BackpackListener;
import me.rique.smpcore.boss.BossManager;
import me.rique.smpcore.database.DatabaseManager;
import me.rique.smpcore.item.AgriculturalPylonListener;
import me.rique.smpcore.item.CorruptionManager;
import me.rique.smpcore.item.CustomEnchantListener;
import me.rique.smpcore.item.CustomToolListener;
import me.rique.smpcore.item.ReforgeManager;
import me.rique.smpcore.item.RewardLanternListener;
import me.rique.smpcore.item.SalvagingDepotListener;
import me.rique.smpcore.item.SustenanceTalismanListener;
import me.rique.smpcore.item.XpLecternListener;
import me.rique.smpcore.legendary.LegendaryListener;
import me.rique.smpcore.legendary.MythicForgeListener;
import me.rique.smpcore.power.SuperpowerManager;
import me.rique.smpcore.season.SeasonRelicManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class ItemAuditManager implements Listener {

    private static final String STAFF_PERMISSION = "smpcore.staff";
    private static final int DEFAULT_SCAN_INTERVAL_SECONDS = 180;
    private static final long ANOMALY_COOLDOWN_MS = 5L * 60L * 1000L;
    private static final long PENDING_ACQUISITION_TTL_MS = 5L * 60L * 1000L;
    private static final int MAX_PENDING_ACQUISITIONS_PER_PLAYER = 96;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private final SMPCore plugin;
    private final NamespacedKey keyInstanceId;
    private final NamespacedKey keyItemKey;
    private final Map<String, KnownInstanceState> knownInstances = new ConcurrentHashMap<>();
    private final Map<String, Long> anomalyCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<PendingAcquisition>> pendingAcquisitions = new ConcurrentHashMap<>();
    private BukkitTask scanTask;
    private volatile boolean auditStateLoaded;

    public ItemAuditManager(SMPCore plugin) {
        this.plugin = plugin;
        this.keyInstanceId = new NamespacedKey(plugin, "audit_instance_id");
        this.keyItemKey = new NamespacedKey(plugin, "audit_item_key");
    }

    public void start() {
        plugin.getDatabase().loadManagedItemInstances().whenComplete((records, throwable) -> {
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> finishInitialLoad(records, throwable));
        });
    }

    private void finishInitialLoad(List<DatabaseManager.ManagedItemInstanceRecord> records, Throwable throwable) {
        if (throwable != null) {
            Throwable root = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
            plugin.getLogger().severe("Failed to load item audit state: " + root.getMessage());
            plugin.getLogger().severe("Item audit scans are disabled until the next successful restart.");
            return;
        }

        if (records != null) {
            for (var record : records) {
                knownInstances.putIfAbsent(record.instanceId(), KnownInstanceState.from(record));
            }
        }

        auditStateLoaded = true;
        Bukkit.getScheduler().runTask(plugin, this::scanOnlinePlayers);
        long intervalTicks = scanIntervalTicks();
        scanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scanOnlinePlayers, 100L, intervalTicks);
    }

    public void shutdown() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        auditStateLoaded = false;
        pendingAcquisitions.clear();
        anomalyCooldowns.clear();
    }

    public void scheduleAudit(Player player) {
        if (player == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (auditStateLoaded) {
                auditPlayer(player, "scheduled");
            }
        });
    }

    public void auditSharedInventory(Player observer, Inventory inventory, String scope) {
        if (!auditStateLoaded || observer == null || !observer.isOnline() || inventory == null) {
            return;
        }

        Map<String, SeenInstance> seenInstances = new LinkedHashMap<>();
        Map<String, Integer> legendaryCounts = new LinkedHashMap<>();
        String safeScope = scope == null || scope.isBlank() ? "shared_storage" : scope;
        scanInventory(observer, inventory, safeScope, seenInstances, legendaryCounts, new LinkedHashSet<>(), true, false);
        finishAudit(observer, "shared_storage", seenInstances, legendaryCounts);
    }

    public void recordKnownAcquisition(Player subject, ItemStack item, CommandSender actor, String method, String details) {
        if (subject == null || item == null) {
            return;
        }
        UUID actorUuid = actor instanceof Player player ? player.getUniqueId() : null;
        String actorName = actor == null ? null : actor.getName();
        recordKnownAcquisition(subject, item, actorUuid, actorName, method, details);
    }

    public void recordKnownAcquisition(Player subject, ItemStack item, String method, String details) {
        if (subject == null || item == null) {
            return;
        }
        recordKnownAcquisition(subject, item, subject.getUniqueId(), subject.getName(), method, details);
    }

    public List<String> itemAuditOptions() {
        LinkedHashSet<String> options = new LinkedHashSet<>();

        LegendaryListener legendary = plugin.getLegendaryListener();
        if (legendary != null) {
            for (String id : legendary.legendaryIds()) {
                options.add("legendary:" + id);
            }
            options.add("special:ender_bone");
            options.add("special:orb_of_the_mystics");
        }

        options.add("custom:backpack");
        options.add("custom:awakening_table");
        options.add("custom:mythic_forge");
        options.add("custom:reward_soul_lantern");
        options.add("custom:talisman_of_sustenance");
        options.add("custom:salvaging_depot");
        options.add("custom:agricultural_pylon");
        options.add("custom:xp_lectern");
        options.add("custom:" + CorruptionManager.STATION_ITEM_ID);
        options.add("corruption:locked_item");
        options.add("reforge:" + ReforgeManager.STONE_ID);

        CustomEnchantListener enchants = plugin.getCustomEnchantListener();
        if (enchants != null) {
            for (String enchantId : enchants.managedEnchantIds()) {
                options.add("custom_enchant:" + enchantId);
            }
        }

        CustomToolListener tools = plugin.getCustomToolListener();
        if (tools != null) {
            for (String toolId : tools.craftableToolIds()) {
                options.add("custom_tool:" + toolId);
            }
        }

        options.add("power:" + SuperpowerManager.ANCIENT_SCROLL_ITEM_ID);
        options.add("power:" + SuperpowerManager.THE_WORLD_CLOCK_ITEM_ID);
        options.add("power:" + SuperpowerManager.MOTHER_NATURE_STICK_ITEM_ID);
        options.add("power:" + SuperpowerManager.DRUID_GRIMOIRE_ITEM_ID);
        options.add("relic:" + MythicForgeListener.ASCENDANT_CORE_ITEM_ID);
        options.add("relic:" + SuperpowerManager.WARDEN_HEART_ITEM_ID);
        options.add("relic:" + BossManager.DOMINION_CORE_ITEM_ID);

        SeasonRelicManager season = plugin.getSeasonRelicManager();
        if (season != null) {
            for (String relicId : season.relicIds()) {
                options.add("season:" + relicId);
            }
        }
        return List.copyOf(options);
    }

    public void sendAuditLog(CommandSender sender, OfflinePlayer target, String rawFilter) {
        if (sender == null || target == null || target.getUniqueId() == null) {
            return;
        }

        String filter = normalizeFilter(rawFilter);
        int limit = 15;
        plugin.getDatabase().loadManagedItemEvents(target.getUniqueId(), filter, limit).whenComplete((records, throwable) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!plugin.isEnabled()) {
                    return;
                }
                if (throwable != null) {
                    sender.sendMessage(me.rique.smpcore.util.MessageUtil.error("Failed to load item audit logs."));
                    return;
                }

                String targetName = target.getName() == null || target.getName().isBlank()
                    ? target.getUniqueId().toString()
                    : target.getName();
                sender.sendMessage(me.rique.smpcore.util.MessageUtil.info(
                    "Item audit for <white>" + escape(targetName) + "</white>"
                        + (filter == null ? "" : " <gray>filtered by <white>" + escape(filterLabel(filter)) + "</white></gray>")
                        + " <dark_gray>(latest " + limit + ")</dark_gray>"
                ));

                if (records == null || records.isEmpty()) {
                    sender.sendMessage(me.rique.smpcore.util.MessageUtil.warn("No audit records were found."));
                    return;
                }

                int index = 1;
                for (var record : records) {
                    sendAuditRecord(sender, record, index++);
                }
            });
        });
    }

    private void sendAuditRecord(CommandSender sender, DatabaseManager.ManagedItemEventRecord record, int index) {
        String itemKey = record.itemKey() == null || record.itemKey().isBlank() ? "unknown" : record.itemKey();
        String instanceId = shortInstanceId(record.instanceId());
        String time = TIME_FORMAT.format(Instant.ofEpochMilli(record.loggedAt()));
        String source = methodLabel(record.method());
        String actor = record.actorName() == null || record.actorName().isBlank() ? "system" : record.actorName();
        String detail = humanDetails(record.details());

        sender.sendMessage(me.rique.smpcore.util.MessageUtil.prefixedRaw(
            "<gray>" + index + ". [" + escape(time) + "]</gray> "
                + "<white>" + escape(displayNameForItemKey(itemKey)) + "</white> "
                + "<yellow>" + escape(eventLabel(record.eventType())) + "</yellow> "
                + "<dark_gray>(" + escape(itemKey) + ")</dark_gray>"
        ));
        sender.sendMessage(me.rique.smpcore.util.MessageUtil.prefixedRaw(
            "<dark_gray>   " + escape(detail)
                + " Source: " + escape(source)
                + (instanceId.isBlank() ? "" : " | ID: " + escape(instanceId))
                + " | Actor: " + escape(actor)
                + "</dark_gray>"
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> auditPlayer(event.getPlayer(), "join"), 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ManagedItemDescriptor descriptor = describe(event.getCurrentItem());
        if (descriptor == null) {
            return;
        }
        int craftedAmount = estimateCraftedResultAmount(event);
        String details = craftedAmount > 1
            ? "Craft result click x" + craftedAmount
            : "Craft result click";
        if (!descriptor.instanceTracked()) {
            recordKnownAcquisition(player, event.getCurrentItem(), "craft", details);
            return;
        }
        rememberPending(player, descriptor.itemKey(), "craft", details, craftedAmount);
        scheduleAudit(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Item dropped = event.getItemDrop();
        if (dropped == null) {
            return;
        }
        ItemStack stack = dropped.getItemStack();
        ManagedItemDescriptor descriptor = describe(stack);
        if (descriptor == null || !descriptor.instanceTracked()) {
            return;
        }

        String instanceId = ensureTrackedIdentity(stack, descriptor);
        dropped.setItemStack(stack);
        KnownInstanceState state = knownInstances.get(instanceId);
        if (state != null) {
            state.currentOwnerUuid = event.getPlayer().getUniqueId();
            state.currentOwnerName = event.getPlayer().getName();
            state.lastSeenAt = System.currentTimeMillis();
            persistState(state);
        }
        persistEvent(new DatabaseManager.ManagedItemEventRecord(
            0L,
            System.currentTimeMillis(),
            instanceId,
            descriptor.itemKey(),
            event.getPlayer().getUniqueId(),
            event.getPlayer().getName(),
            event.getPlayer().getUniqueId(),
            event.getPlayer().getName(),
            "dropped",
            "ground_drop",
            "Dropped from inventory"
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        ManagedItemDescriptor descriptor = describe(stack);
        if (descriptor == null || !descriptor.instanceTracked()) {
            return;
        }

        String instanceId = currentInstanceId(stack);
        if (instanceId != null && !instanceId.isBlank()) {
            KnownInstanceState state = knownInstances.get(instanceId);
            if (state != null && !player.getUniqueId().equals(state.currentOwnerUuid)) {
                state.currentOwnerUuid = player.getUniqueId();
                state.currentOwnerName = player.getName();
                state.lastSeenAt = System.currentTimeMillis();
                persistState(state);
                persistEvent(new DatabaseManager.ManagedItemEventRecord(
                    0L,
                    System.currentTimeMillis(),
                    instanceId,
                    descriptor.itemKey(),
                    player.getUniqueId(),
                    player.getName(),
                    player.getUniqueId(),
                    player.getName(),
                    "picked_up",
                    "ground_pickup",
                    "Picked up from the ground"
                ));
            }
        }
        scheduleAudit(player);
    }

    private void recordKnownAcquisition(Player subject, ItemStack item, UUID actorUuid, String actorName, String method, String details) {
        ManagedItemDescriptor descriptor = describe(item);
        if (descriptor == null) {
            return;
        }

        if (!descriptor.instanceTracked()) {
            persistEvent(new DatabaseManager.ManagedItemEventRecord(
                0L,
                System.currentTimeMillis(),
                "",
                descriptor.itemKey(),
                subject.getUniqueId(),
                subject.getName(),
                actorUuid,
                actorName,
                "acquired",
                method,
                details
            ));
            return;
        }

        String instanceId = ensureTrackedIdentity(item, descriptor);
        long now = System.currentTimeMillis();
        KnownInstanceState state = new KnownInstanceState(
            instanceId,
            descriptor.itemKey(),
            now,
            method,
            actorUuid,
            actorName,
            subject.getUniqueId(),
            subject.getName(),
            now,
            now
        );
        knownInstances.put(instanceId, state);
        persistState(state);
        persistEvent(new DatabaseManager.ManagedItemEventRecord(
            0L,
            now,
            instanceId,
            descriptor.itemKey(),
            subject.getUniqueId(),
            subject.getName(),
            actorUuid,
            actorName,
            "acquired",
            method,
            details
        ));
    }

    private void scanOnlinePlayers() {
        if (!auditStateLoaded) {
            return;
        }
        pruneTransientState();
        for (Player player : Bukkit.getOnlinePlayers()) {
            auditPlayer(player, "scheduled");
        }
    }

    private void pruneTransientState() {
        long now = System.currentTimeMillis();
        anomalyCooldowns.entrySet().removeIf(entry -> entry.getValue() < now - ANOMALY_COOLDOWN_MS);
        pendingAcquisitions.entrySet().removeIf(entry -> {
            Deque<PendingAcquisition> queue = entry.getValue();
            prunePending(queue);
            return queue.isEmpty();
        });
    }

    private long scanIntervalTicks() {
        int seconds = plugin.getConfigManager() == null
            ? DEFAULT_SCAN_INTERVAL_SECONDS
            : plugin.getConfigManager().itemAuditScanIntervalSeconds;
        return Math.max(60L, seconds) * 20L;
    }

    private void auditPlayer(Player player, String reason) {
        if (!auditStateLoaded || player == null || !player.isOnline()) {
            return;
        }

        Map<String, SeenInstance> seenInstances = new LinkedHashMap<>();
        Map<String, Integer> legendaryCounts = new LinkedHashMap<>();

        scanInventory(player, player.getInventory(), "inventory", seenInstances, legendaryCounts, new LinkedHashSet<>(), true, true);
        scanInventory(player, player.getEnderChest(), "ender_chest", seenInstances, legendaryCounts, new LinkedHashSet<>(), true, true);
        ItemStack cursor = player.getItemOnCursor();
        scanItem(player, cursor, "cursor", seenInstances, legendaryCounts, new LinkedHashSet<>(), true, true);
        if (shouldWriteBackScannedItem(cursor)) {
            player.setItemOnCursor(cursor);
        }
        finishAudit(player, reason, seenInstances, legendaryCounts);
    }

    private void finishAudit(
        Player player,
        String reason,
        Map<String, SeenInstance> seenInstances,
        Map<String, Integer> legendaryCounts
    ) {
        for (SeenInstance seen : seenInstances.values()) {
            if (seen.locations().size() <= 1) {
                continue;
            }
            logAnomaly(
                player,
                seen.itemKey(),
                "duplicate_instance",
                "scan",
                "Tracked ID " + shortInstanceId(seen.instanceId()) + " appears in " + friendlyLocationList(seen.locations()) + ".",
                seen.instanceId()
            );
        }

        for (Map.Entry<String, Integer> entry : legendaryCounts.entrySet()) {
            if (entry.getValue() <= 1) {
                continue;
            }
            logAnomaly(
                player,
                entry.getKey(),
                "multiple_copies_held",
                reason,
                (reason != null && reason.startsWith("shared_storage") ? "Storage contains " : "Holding ")
                    + entry.getValue() + " copies of " + entry.getKey()
            );
        }
    }

    private void scanInventory(
        Player owner,
        Inventory inventory,
        String scope,
        Map<String, SeenInstance> seenInstances,
        Map<String, Integer> legendaryCounts,
        Set<String> backpackTrail,
        boolean canMutateIdentity,
        boolean updateOwner
    ) {
        if (inventory == null) {
            return;
        }
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            scanItem(owner, item, scope + "[" + slot + "]", seenInstances, legendaryCounts, backpackTrail, canMutateIdentity, updateOwner);
            if (canMutateIdentity && shouldWriteBackScannedItem(item)) {
                inventory.setItem(slot, item);
            }
        }
    }

    private boolean shouldWriteBackScannedItem(ItemStack item) {
        ManagedItemDescriptor descriptor = describe(item);
        return descriptor != null && descriptor.instanceTracked() && currentInstanceId(item) != null;
    }

    private void scanItem(
        Player owner,
        ItemStack item,
        String location,
        Map<String, SeenInstance> seenInstances,
        Map<String, Integer> legendaryCounts,
        Set<String> backpackTrail,
        boolean canMutateIdentity,
        boolean updateOwner
    ) {
        ManagedItemDescriptor descriptor = describe(item);
        if (descriptor == null) {
            return;
        }

        if (descriptor.itemKey().startsWith("legendary:")) {
            legendaryCounts.merge(descriptor.itemKey(), 1, Integer::sum);
        }

        if (!descriptor.instanceTracked()) {
            return;
        }

        String instanceId = currentInstanceId(item);
        if ((instanceId == null || instanceId.isBlank()) && !canMutateIdentity) {
            PendingAcquisition pending = consumePending(owner, descriptor.itemKey());
            if (pending != null) {
                persistEvent(new DatabaseManager.ManagedItemEventRecord(
                    0L,
                    System.currentTimeMillis(),
                    "",
                    descriptor.itemKey(),
                    owner.getUniqueId(),
                    owner.getName(),
                    owner.getUniqueId(),
                    owner.getName(),
                    "acquired",
                    pending.method(),
                    pending.details() + " before stored backpack scan at " + location
                ));
            } else if (!isQuietCraftableFirstSeen(descriptor)) {
                logAnomaly(
                    owner,
                    descriptor.itemKey(),
                    "unknown_origin",
                    "backpack_scan",
                    "Untracked item seen inside stored backpack data at " + location
                );
            }
            return;
        }

        instanceId = ensureTrackedIdentity(item, descriptor);
        rememberSeenInstance(seenInstances, instanceId, descriptor, location);

        if (item != null && item.getAmount() > 1) {
            logAnomaly(
                owner,
                descriptor.itemKey(),
                "stacked_item",
                "scan",
                "Tracked item stack amount was " + item.getAmount() + " at " + location,
                instanceId
            );
        }

        KnownInstanceState state = knownInstances.get(instanceId);
        PendingAcquisition pending = state == null ? consumePending(owner, descriptor.itemKey()) : null;
        long now = System.currentTimeMillis();
        if (state == null) {
            boolean trustedStaffFirstSeen = pending == null && updateOwner && isTrustedStaff(owner);
            boolean quietCraftableFirstSeen = pending == null && !trustedStaffFirstSeen && isQuietCraftableFirstSeen(descriptor);
            String method = pending != null
                ? pending.method()
                : trustedStaffFirstSeen
                    ? "staff_inventory_seed"
                    : quietCraftableFirstSeen ? "craftable_first_seen" : "unknown";
            String details = pending != null
                ? pending.details() + " at " + location
                : trustedStaffFirstSeen
                    ? "First seen in staff inventory at " + location
                    : quietCraftableFirstSeen
                        ? "First seen craftable item at " + location
                    : "First seen in " + location;
            boolean hasPlayerAcquisitionContext = updateOwner || pending != null || trustedStaffFirstSeen;
            KnownInstanceState created = new KnownInstanceState(
                instanceId,
                descriptor.itemKey(),
                now,
                method,
                hasPlayerAcquisitionContext ? owner.getUniqueId() : null,
                hasPlayerAcquisitionContext ? owner.getName() : "Shared Storage",
                updateOwner ? owner.getUniqueId() : null,
                updateOwner ? owner.getName() : "Shared Storage",
                now,
                now
            );
            knownInstances.put(instanceId, created);
            persistState(created);
            persistEvent(new DatabaseManager.ManagedItemEventRecord(
                0L,
                now,
                instanceId,
                descriptor.itemKey(),
                owner.getUniqueId(),
                owner.getName(),
                owner.getUniqueId(),
                owner.getName(),
                pending != null
                    ? "acquired"
                    : trustedStaffFirstSeen ? "staff_seeded" : quietCraftableFirstSeen ? "first_seen_craftable" : "unknown_origin",
                method,
                details
            ));
            if (pending == null && !trustedStaffFirstSeen && !quietCraftableFirstSeen) {
                notifyStaffAnomaly(
                    owner,
                    descriptor.itemKey(),
                    "unknown_origin",
                    "First seen in " + location + " with no known acquisition path.",
                    instanceId
                );
            }
        } else if (updateOwner && !Objects.equals(state.currentOwnerUuid, owner.getUniqueId())) {
            String previousOwnerName = state.currentOwnerName;
            state.currentOwnerUuid = owner.getUniqueId();
            state.currentOwnerName = owner.getName();
            state.lastSeenAt = now;
            persistState(state);
            boolean notifyTransfer = shouldNotifyOwnerTransfer(descriptor);
            persistEvent(new DatabaseManager.ManagedItemEventRecord(
                0L,
                now,
                instanceId,
                descriptor.itemKey(),
                owner.getUniqueId(),
                owner.getName(),
                owner.getUniqueId(),
                owner.getName(),
                notifyTransfer ? "owner_changed_unknown" : "owner_changed",
                notifyTransfer ? "unknown_transfer" : "inventory_transfer",
                "Seen in " + location + " after belonging to " + safeName(previousOwnerName)
            ));
            if (notifyTransfer) {
                notifyStaffAnomaly(
                    owner,
                    descriptor.itemKey(),
                    "unknown_transfer",
                    "Seen in " + location + " after belonging to " + safeName(previousOwnerName),
                    instanceId
                );
            }
        } else {
            state.lastSeenAt = now;
        }

        if (!"custom:backpack".equals(descriptor.itemKey()) || item == null) {
            return;
        }

        BackpackListener backpacks = plugin.getBackpackListener();
        if (backpacks == null) {
            return;
        }
        String backpackId = instanceId;
        if (!backpackTrail.add(backpackId)) {
            return;
        }
        try {
            List<ItemStack> contents = backpacks.auditContents(owner, item);
            for (int i = 0; i < contents.size(); i++) {
                scanItem(owner, contents.get(i), location + "/backpack[" + i + "]", seenInstances, legendaryCounts, backpackTrail, false, updateOwner);
            }
        } finally {
            backpackTrail.remove(backpackId);
        }
    }

    private void rememberSeenInstance(
        Map<String, SeenInstance> seenInstances,
        String instanceId,
        ManagedItemDescriptor descriptor,
        String location
    ) {
        if (seenInstances == null || instanceId == null || instanceId.isBlank() || descriptor == null) {
            return;
        }
        SeenInstance seen = seenInstances.computeIfAbsent(instanceId, ignored ->
            new SeenInstance(instanceId, descriptor.itemKey(), new ArrayList<>())
        );
        if (location != null && !location.isBlank() && !seen.locations().contains(location)) {
            seen.locations().add(location);
        }
    }

    private ManagedItemDescriptor describe(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }

        LegendaryListener legendary = plugin.getLegendaryListener();
        if (legendary != null) {
            String legendaryId = legendary.legendaryId(item);
            if (legendaryId != null) {
                String normalized = legendary.normalizeLegendaryId(legendaryId);
                String displayName = legendary.displayNameForLegendary(normalized);
                return new ManagedItemDescriptor("legendary:" + normalized, defaultDisplay(displayName, normalized), true);
            }
            if (legendary.isEnderBoneItem(item)) {
                return new ManagedItemDescriptor("special:ender_bone", "Ender Bone", false);
            }
            if (legendary.isOrbOfTheMysticsItem(item)) {
                return new ManagedItemDescriptor("special:orb_of_the_mystics", "Orb of the Mystics", false);
            }
        }

        BackpackListener backpacks = plugin.getBackpackListener();
        if (backpacks != null && backpacks.isBackpack(item)) {
            return new ManagedItemDescriptor("custom:backpack", backpacks.backpackDisplayName(item), true);
        }

        CustomEnchantListener enchants = plugin.getCustomEnchantListener();
        if (enchants != null) {
            String enchantId = enchants.customEnchantBookId(item);
            if (enchantId != null) {
                String displayName = enchants.customEnchantBookDisplayName(item);
                return new ManagedItemDescriptor("custom_enchant:" + enchantId, defaultDisplay(displayName, enchantId), true);
            }
        }

        CustomToolListener tools = plugin.getCustomToolListener();
        if (tools != null) {
            String toolId = tools.customToolId(item);
            if (toolId != null) {
                return new ManagedItemDescriptor("custom_tool:" + toolId, defaultDisplay(tools.displayNameFor(toolId), toolId), true);
            }
        }

        AwakeningTableListener awakening = plugin.getAwakeningTableListener();
        if (awakening != null && awakening.isAwakeningTableCustomItem(item)) {
            return new ManagedItemDescriptor("custom:awakening_table", "Awakening Table", true);
        }

        MythicForgeListener forge = plugin.getMythicForgeListener();
        if (forge != null) {
            if (forge.isMythicForgeItemStack(item)) {
                return new ManagedItemDescriptor("custom:mythic_forge", "Mythic Forge", true);
            }
            if (forge.isAscendantCoreItem(item)) {
                return new ManagedItemDescriptor("relic:" + MythicForgeListener.ASCENDANT_CORE_ITEM_ID, "Ascendant Core", false);
            }
        }

        RewardLanternListener lanterns = plugin.getRewardLanternListener();
        if (lanterns != null && lanterns.isRewardLantern(item)) {
            return new ManagedItemDescriptor("custom:reward_soul_lantern", "Reward Soul Lantern", true);
        }

        SustenanceTalismanListener talismans = plugin.getSustenanceTalismanListener();
        if (talismans != null && talismans.isTalisman(item)) {
            return new ManagedItemDescriptor("custom:talisman_of_sustenance", "Talisman of Sustenance", true);
        }

        SalvagingDepotListener depot = plugin.getSalvagingDepotListener();
        if (depot != null && depot.isDepotItem(item)) {
            return new ManagedItemDescriptor("custom:salvaging_depot", "Salvaging Depot", false);
        }

        AgriculturalPylonListener pylon = plugin.getAgriculturalPylonListener();
        if (pylon != null && pylon.isPylonItem(item)) {
            return new ManagedItemDescriptor("custom:agricultural_pylon", "Agricultural Pylon", false);
        }

        XpLecternListener xpLectern = plugin.getXpLecternListener();
        if (xpLectern != null && xpLectern.isLecternItem(item)) {
            return new ManagedItemDescriptor("custom:xp_lectern", "XP Lectern", false);
        }

        CorruptionManager corruption = plugin.getCorruptionManager();
        if (corruption != null) {
            if (corruption.isStationItem(item)) {
                return new ManagedItemDescriptor("custom:" + CorruptionManager.STATION_ITEM_ID, "Corruption Anchor", false);
            }
            String corruptionName = corruption.corruptionDisplayName(item);
            if (corruptionName != null) {
                return new ManagedItemDescriptor("corruption:locked_item", corruptionName, false);
            }
        }

        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers != null) {
            if (powers.isAncientScroll(item)) {
                return new ManagedItemDescriptor("power:" + SuperpowerManager.ANCIENT_SCROLL_ITEM_ID, "Ancient Scroll", false);
            }
            if (powers.isWardenHeart(item)) {
                return new ManagedItemDescriptor("relic:" + SuperpowerManager.WARDEN_HEART_ITEM_ID, "Warden Heart", false);
            }
            if (powers.isMotherNatureStick(item)) {
                return new ManagedItemDescriptor("power:" + SuperpowerManager.MOTHER_NATURE_STICK_ITEM_ID, "Wand of Mother Nature", true);
            }
            if (powers.isTheWorldClock(item)) {
                return new ManagedItemDescriptor("power:" + SuperpowerManager.THE_WORLD_CLOCK_ITEM_ID, "The World Clock", true);
            }
            if (powers.isDruidGrimoire(item)) {
                return new ManagedItemDescriptor("power:" + SuperpowerManager.DRUID_GRIMOIRE_ITEM_ID, "Druid's Grimoire", true);
            }
        }

        BossManager bosses = plugin.getBossManager();
        if (bosses != null && bosses.isDominionCore(item)) {
            return new ManagedItemDescriptor("relic:" + BossManager.DOMINION_CORE_ITEM_ID, "Veil Core", false);
        }

        ReforgeManager reforges = plugin.getReforgeManager();
        if (reforges != null) {
            String stoneId = reforges.reforgeStoneId(item);
            if (stoneId != null) {
                String displayName = reforges.displayNameForStone(stoneId);
                return new ManagedItemDescriptor("reforge:" + stoneId, defaultDisplay(displayName, stoneId), false);
            }
        }

        SeasonRelicManager season = plugin.getSeasonRelicManager();
        if (season != null) {
            String relicId = season.relicId(item);
            if (relicId != null) {
                String displayName = season.displayNameFor(relicId);
                boolean trackedInstance = !season.isMaterialRelicId(relicId);
                return new ManagedItemDescriptor("season:" + relicId, defaultDisplay(displayName, relicId), trackedInstance);
            }
        }

        return null;
    }

    private void logAnomaly(Player subject, String itemKey, String eventType, String method, String details) {
        logAnomaly(subject, itemKey, eventType, method, details, "");
    }

    private void logAnomaly(Player subject, String itemKey, String eventType, String method, String details, String instanceId) {
        if (subject == null || itemKey == null || itemKey.isBlank()) {
            return;
        }
        String safeInstanceId = instanceId == null ? "" : instanceId;
        String dedupeKey = subject.getUniqueId() + "|" + itemKey + "|" + safeInstanceId + "|" + eventType + "|" + details;
        long now = System.currentTimeMillis();
        Long previous = anomalyCooldowns.get(dedupeKey);
        if (previous != null && now - previous < ANOMALY_COOLDOWN_MS) {
            return;
        }
        anomalyCooldowns.put(dedupeKey, now);
        notifyStaffAnomaly(subject, itemKey, eventType, details, safeInstanceId);
        persistEvent(new DatabaseManager.ManagedItemEventRecord(
            0L,
            now,
            safeInstanceId,
            itemKey,
            subject.getUniqueId(),
            subject.getName(),
            subject.getUniqueId(),
            subject.getName(),
            eventType,
            method,
            details
        ));
    }

    private boolean isTrustedStaff(Player player) {
        return player != null && (player.isOp() || player.hasPermission(STAFF_PERMISSION));
    }

    private boolean isQuietCraftableFirstSeen(ManagedItemDescriptor descriptor) {
        if (descriptor == null) {
            return false;
        }
        String itemKey = descriptor.itemKey();
        return itemKey.startsWith("custom_tool:")
            || itemKey.startsWith("custom_enchant:")
            || "custom:backpack".equals(itemKey)
            || "custom:mythic_forge".equals(itemKey)
            || "custom:talisman_of_sustenance".equals(itemKey)
            || "custom:salvaging_depot".equals(itemKey)
            || "custom:agricultural_pylon".equals(itemKey)
            || "custom:xp_lectern".equals(itemKey)
            || ("power:" + SuperpowerManager.ANCIENT_SCROLL_ITEM_ID).equals(itemKey);
    }

    private boolean shouldNotifyOwnerTransfer(ManagedItemDescriptor descriptor) {
        if (descriptor == null) {
            return false;
        }
        String itemKey = descriptor.itemKey();
        return itemKey.startsWith("legendary:")
            || itemKey.startsWith("special:")
            || itemKey.startsWith("power:")
            || itemKey.startsWith("season:")
            || "custom:awakening_table".equals(itemKey)
            || "custom:reward_soul_lantern".equals(itemKey);
    }

    private void notifyStaffAnomaly(Player subject, String itemKey, String eventType, String details) {
        notifyStaffAnomaly(subject, itemKey, eventType, details, "");
    }

    private void notifyStaffAnomaly(Player subject, String itemKey, String eventType, String details, String instanceId) {
        if (subject == null || itemKey == null || itemKey.isBlank()) {
            return;
        }

        String location = subject.getWorld().getName()
            + " "
            + subject.getLocation().getBlockX()
            + ","
            + subject.getLocation().getBlockY()
            + ","
            + subject.getLocation().getBlockZ();
        String shortId = shortInstanceId(instanceId);
        String itemMeta = itemKey + (shortId.isBlank() ? "" : ", ID " + shortId);
        String subjectName = safeName(subject.getName());

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.isOp() || staff.hasPermission(STAFF_PERMISSION)) {
                staff.sendMessage(me.rique.smpcore.util.MessageUtil.prefixedRaw(
                    "<red><bold>Audit Alert</bold></red> <white>" + escape(subjectName)
                        + "</white> <gray>-</gray> <yellow>" + escape(eventLabel(eventType)) + "</yellow>"
                ));
                staff.sendMessage(me.rique.smpcore.util.MessageUtil.prefixedRaw(
                    "<gray>Item:</gray> <white>" + escape(displayNameForItemKey(itemKey)) + "</white> "
                        + "<dark_gray>(" + escape(itemMeta) + ")</dark_gray> "
                        + "<gray>At:</gray> <white>" + escape(location) + "</white>"
                ));
                if (details != null && !details.isBlank()) {
                    staff.sendMessage(me.rique.smpcore.util.MessageUtil.prefixedRaw(
                        "<gray>Why:</gray> <white>" + escape(humanDetails(details)) + "</white>"
                    ));
                }
                staff.sendMessage(me.rique.smpcore.util.MessageUtil.prefixedRaw(
                    "<gray>Check:</gray> <white>/itemaudit " + escape(subjectName) + " " + escape(itemKey) + "</white>"
                ));
            }
        }
    }

    private void rememberPending(Player player, String itemKey, String method, String details, int amount) {
        if (player == null || itemKey == null || itemKey.isBlank()) {
            return;
        }
        int safeAmount = Math.max(1, Math.min(amount, MAX_PENDING_ACQUISITIONS_PER_PLAYER));
        Deque<PendingAcquisition> queue = pendingAcquisitions.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
        prunePending(queue);
        while (queue.size() + safeAmount > MAX_PENDING_ACQUISITIONS_PER_PLAYER && !queue.isEmpty()) {
            queue.removeFirst();
        }
        long now = System.currentTimeMillis();
        for (int i = 0; i < safeAmount; i++) {
            queue.addLast(new PendingAcquisition(itemKey, method, details, now));
        }
    }

    private PendingAcquisition consumePending(Player player, String itemKey) {
        if (player == null || itemKey == null || itemKey.isBlank()) {
            return null;
        }
        Deque<PendingAcquisition> queue = pendingAcquisitions.get(player.getUniqueId());
        if (queue == null || queue.isEmpty()) {
            return null;
        }
        prunePending(queue);
        Iterator<PendingAcquisition> iterator = queue.iterator();
        while (iterator.hasNext()) {
            PendingAcquisition acquisition = iterator.next();
            if (acquisition.itemKey().equals(itemKey)) {
                iterator.remove();
                return acquisition;
            }
        }
        return null;
    }

    private void prunePending(Deque<PendingAcquisition> queue) {
        long cutoff = System.currentTimeMillis() - PENDING_ACQUISITION_TTL_MS;
        while (!queue.isEmpty() && queue.peekFirst().createdAt() < cutoff) {
            queue.removeFirst();
        }
    }

    private int estimateCraftedResultAmount(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        int resultAmount = result == null || result.getType().isAir() ? 1 : Math.max(1, result.getAmount());
        if (!event.isShiftClick()) {
            return resultAmount;
        }

        int crafts = Integer.MAX_VALUE;
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (ingredient == null || ingredient.getType().isAir()) {
                continue;
            }
            crafts = Math.min(crafts, ingredient.getAmount());
        }
        if (crafts == Integer.MAX_VALUE) {
            return resultAmount;
        }
        return Math.max(1, Math.min(resultAmount * crafts, MAX_PENDING_ACQUISITIONS_PER_PLAYER));
    }

    private String ensureTrackedIdentity(ItemStack item, ManagedItemDescriptor descriptor) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return UUID.randomUUID().toString();
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String instanceId = pdc.get(keyInstanceId, PersistentDataType.STRING);
        if (instanceId == null || instanceId.isBlank()) {
            instanceId = UUID.randomUUID().toString();
            pdc.set(keyInstanceId, PersistentDataType.STRING, instanceId);
        }
        String storedKey = pdc.get(keyItemKey, PersistentDataType.STRING);
        if (!descriptor.itemKey().equals(storedKey)) {
            pdc.set(keyItemKey, PersistentDataType.STRING, descriptor.itemKey());
        }
        item.setItemMeta(meta);
        return instanceId;
    }

    private String currentInstanceId(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(keyInstanceId, PersistentDataType.STRING);
    }

    private void persistState(KnownInstanceState state) {
        plugin.getDatabase().saveManagedItemInstance(new DatabaseManager.ManagedItemInstanceRecord(
            state.instanceId,
            state.itemKey,
            state.createdAt,
            state.createdMethod,
            state.createdByUuid,
            state.createdByName,
            state.currentOwnerUuid,
            state.currentOwnerName,
            state.firstSeenAt,
            state.lastSeenAt
        ));
    }

    private void persistEvent(DatabaseManager.ManagedItemEventRecord event) {
        plugin.getDatabase().saveManagedItemEvent(event);
    }

    private String filterLabel(String filter) {
        if (filter == null || filter.isBlank()) {
            return "all items";
        }
        if (looksLikeUuid(filter)) {
            return "tracked ID " + shortInstanceId(filter);
        }
        String displayName = displayNameForItemKey(filter);
        return displayName + " (" + filter + ")";
    }

    private String normalizeFilter(String rawFilter) {
        if (rawFilter == null) {
            return null;
        }
        String normalized = rawFilter.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private String displayNameForItemKey(String itemKey) {
        if (itemKey == null || itemKey.isBlank()) {
            return "Unknown Item";
        }

        String key = itemKey.trim().toLowerCase(Locale.ROOT);
        if (looksLikeUuid(key)) {
            return "Tracked Item " + shortInstanceId(key);
        }

        int separator = key.indexOf(':');
        String category = separator > 0 ? key.substring(0, separator) : "";
        String id = separator > 0 && separator + 1 < key.length() ? key.substring(separator + 1) : key;

        return switch (category) {
            case "legendary" -> {
                LegendaryListener legendary = plugin.getLegendaryListener();
                String displayName = legendary == null ? null : legendary.displayNameForLegendary(id);
                yield defaultDisplay(displayName, id);
            }
            case "season" -> {
                SeasonRelicManager season = plugin.getSeasonRelicManager();
                String displayName = season == null ? null : season.displayNameFor(id);
                yield defaultDisplay(displayName, id);
            }
            case "custom_tool" -> {
                CustomToolListener tools = plugin.getCustomToolListener();
                String displayName = tools == null ? null : tools.displayNameFor(id);
                yield defaultDisplay(displayName, id);
            }
            case "custom_enchant" -> defaultDisplay(null, id) + " Book";
            case "custom" -> switch (id) {
                case "backpack" -> "Backpack";
                case "awakening_table" -> "Awakening Table";
                case "mythic_forge" -> "Mythic Forge";
                case "reward_soul_lantern" -> "Reward Soul Lantern";
                case "talisman_of_sustenance" -> "Talisman of Sustenance";
                case "salvaging_depot" -> "Salvaging Depot";
                case "agricultural_pylon" -> "Agricultural Pylon";
                case "xp_lectern" -> "XP Lectern";
                default -> defaultDisplay(null, id);
            };
            case "special" -> switch (id) {
                case "ender_bone" -> "Ender Bone";
                case "orb_of_the_mystics" -> "Orb of the Mystics";
                default -> defaultDisplay(null, id);
            };
            case "power" -> switch (id) {
                case SuperpowerManager.ANCIENT_SCROLL_ITEM_ID -> "Ancient Scroll";
                case SuperpowerManager.THE_WORLD_CLOCK_ITEM_ID -> "The World Clock";
                case SuperpowerManager.MOTHER_NATURE_STICK_ITEM_ID -> "Wand of Mother Nature";
                case SuperpowerManager.DRUID_GRIMOIRE_ITEM_ID -> "Druid's Grimoire";
                default -> defaultDisplay(null, id);
            };
            case "relic" -> switch (id) {
                case MythicForgeListener.ASCENDANT_CORE_ITEM_ID -> "Ascendant Core";
                case SuperpowerManager.WARDEN_HEART_ITEM_ID -> "Warden Heart";
                case BossManager.DOMINION_CORE_ITEM_ID -> "Veil Core";
                default -> defaultDisplay(null, id);
            };
            default -> defaultDisplay(null, key);
        };
    }

    private String eventLabel(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return "Audit record";
        }
        return switch (eventType.toLowerCase(Locale.ROOT)) {
            case "duplicate_instance" -> "Duplicate tracked item";
            case "multiple_copies_held" -> "Too many copies";
            case "stacked_item" -> "Tracked item was stacked";
            case "unknown_origin" -> "Unknown origin";
            case "owner_changed_unknown", "unknown_transfer" -> "Moved without a known handoff";
            case "owner_changed" -> "Owner updated";
            case "acquired" -> "Acquired";
            case "picked_up" -> "Picked up";
            case "dropped" -> "Dropped";
            case "staff_seeded" -> "First seen with staff";
            case "first_seen_craftable" -> "First seen";
            default -> defaultDisplay(null, eventType);
        };
    }

    private String methodLabel(String method) {
        if (method == null || method.isBlank()) {
            return "unknown";
        }
        return switch (method.toLowerCase(Locale.ROOT)) {
            case "scan", "scheduled", "join" -> "inventory scan";
            case "unknown_transfer", "inventory_transfer" -> "owner scan";
            case "ground_drop" -> "item drop";
            case "ground_pickup" -> "item pickup";
            case "craft" -> "crafting";
            case "backpack_scan" -> "backpack scan";
            case "staff_inventory_seed" -> "staff inventory scan";
            case "craftable_first_seen" -> "first scan";
            case "season_craft" -> "Armory of the Veil craft";
            case "custom_enchant_reliquary_craft" -> "custom enchant craft";
            case "reliquary_craft" -> "Reliquary craft";
            case "mythic_forge" -> "Mythic Forge";
            case "admin_give", "admin_custom_item_give", "admin_legendary_give" -> "admin give";
            default -> defaultDisplay(null, method);
        };
    }

    private String humanDetails(String details) {
        if (details == null || details.isBlank()) {
            return "No details saved.";
        }

        String text = details.trim();
        String transferMarker = " after belonging to ";
        if (text.startsWith("Seen in ") && text.contains(transferMarker)) {
            int markerIndex = text.indexOf(transferMarker);
            String location = text.substring("Seen in ".length(), markerIndex);
            String previousOwner = text.substring(markerIndex + transferMarker.length());
            return ensurePeriod("Found in " + friendlyLocation(location) + " after belonging to " + safeName(previousOwner));
        }

        String noPathSuffix = " with no known acquisition path.";
        if (text.startsWith("First seen in ") && text.endsWith(noPathSuffix)) {
            String location = text.substring("First seen in ".length(), text.length() - noPathSuffix.length());
            return "First seen in " + friendlyLocation(location) + "; no craft, drop, pickup, or admin record matched it.";
        }

        if (text.startsWith("First seen in staff inventory at ")) {
            String location = text.substring("First seen in staff inventory at ".length());
            return "First seen in staff inventory at " + friendlyLocation(location) + ".";
        }

        if (text.startsWith("First seen craftable item at ")) {
            String location = text.substring("First seen craftable item at ".length());
            return "First seen as a normal craftable item in " + friendlyLocation(location) + ".";
        }

        if (text.startsWith("First seen in ")) {
            String location = text.substring("First seen in ".length());
            return "First seen in " + friendlyLocation(location) + ".";
        }

        if (text.startsWith("Untracked item seen inside stored backpack data at ")) {
            String location = text.substring("Untracked item seen inside stored backpack data at ".length());
            return "Untracked item found inside stored backpack data at " + friendlyLocation(location) + ".";
        }

        if (text.startsWith("Tracked item stack amount was ")) {
            String rest = text.substring("Tracked item stack amount was ".length());
            int atIndex = rest.lastIndexOf(" at ");
            if (atIndex > 0) {
                String amount = rest.substring(0, atIndex);
                String location = rest.substring(atIndex + " at ".length());
                return "Stack amount " + amount + " at " + friendlyLocation(location) + "; tracked items should not stack.";
            }
        }

        if (text.startsWith("Duplicate tracked instance seen at ")) {
            String locations = text.substring("Duplicate tracked instance seen at ".length());
            return "Same tracked item exists in " + friendlyLocationList(Arrays.asList(locations.split(","))) + ".";
        }

        String copyMarker = " copies of ";
        if ((text.startsWith("Holding ") || text.startsWith("Storage contains ")) && text.contains(copyMarker)) {
            int markerIndex = text.lastIndexOf(copyMarker);
            String prefix = text.substring(0, markerIndex + copyMarker.length());
            String item = text.substring(markerIndex + copyMarker.length());
            return ensurePeriod(prefix + displayNameForItemKey(item) + " (" + item + ")");
        }

        return ensurePeriod(text);
    }

    private String shortInstanceId(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return "";
        }
        String trimmed = instanceId.trim();
        return trimmed.length() <= 8 ? trimmed : trimmed.substring(0, 8);
    }

    private String friendlyLocationList(List<String> locations) {
        if (locations == null || locations.isEmpty()) {
            return "an unknown location";
        }
        List<String> readable = locations.stream()
            .filter(location -> location != null && !location.isBlank())
            .map(this::friendlyLocation)
            .distinct()
            .collect(Collectors.toCollection(ArrayList::new));
        if (readable.isEmpty()) {
            return "an unknown location";
        }
        if (readable.size() > 4) {
            int extra = readable.size() - 4;
            readable = new ArrayList<>(readable.subList(0, 4));
            readable.add(extra + " more place" + (extra == 1 ? "" : "s"));
        }
        return naturalList(readable);
    }

    private String friendlyLocation(String location) {
        if (location == null || location.isBlank()) {
            return "unknown location";
        }
        return Arrays.stream(location.trim().split("/"))
            .filter(part -> !part.isBlank())
            .map(this::friendlyLocationPart)
            .collect(Collectors.joining(" -> "));
    }

    private String friendlyLocationPart(String locationPart) {
        String part = locationPart == null ? "" : locationPart.trim();
        if (part.isBlank()) {
            return "unknown location";
        }
        if ("cursor".equalsIgnoreCase(part)) {
            return "cursor";
        }

        int openBracket = part.indexOf('[');
        int closeBracket = part.indexOf(']', openBracket + 1);
        if (openBracket > 0 && closeBracket > openBracket) {
            String scope = part.substring(0, openBracket);
            int slot = parseSlot(part.substring(openBracket + 1, closeBracket));
            if (slot >= 0) {
                return locationLabel(scope) + " slot " + (slot + 1);
            }
            return locationLabel(scope);
        }

        return locationLabel(part);
    }

    private String locationLabel(String scope) {
        if (scope == null || scope.isBlank()) {
            return "unknown location";
        }
        return switch (scope.toLowerCase(Locale.ROOT)) {
            case "inventory" -> "Inventory";
            case "ender_chest" -> "Ender chest";
            case "backpack" -> "Backpack";
            case "shared_storage" -> "Shared storage";
            case "cursor" -> "cursor";
            default -> defaultDisplay(null, scope);
        };
    }

    private int parseSlot(String rawSlot) {
        try {
            return Integer.parseInt(rawSlot.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String naturalList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        if (values.size() == 2) {
            return values.get(0) + " and " + values.get(1);
        }
        return String.join(", ", values.subList(0, values.size() - 1)) + ", and " + values.get(values.size() - 1);
    }

    private String ensurePeriod(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = text.trim();
        char last = trimmed.charAt(trimmed.length() - 1);
        return last == '.' || last == '!' || last == '?' ? trimmed : trimmed + ".";
    }

    private boolean looksLikeUuid(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(input.trim());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String defaultDisplay(String displayName, String fallbackKey) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        if (fallbackKey == null || fallbackKey.isBlank()) {
            return "Unknown Item";
        }
        return Arrays.stream(fallbackKey.split("_"))
            .filter(part -> !part.isBlank())
            .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1).toLowerCase(Locale.ROOT))
            .collect(Collectors.joining(" "));
    }

    private String safeName(String input) {
        return input == null || input.isBlank() ? "unknown" : input;
    }

    private String escape(String input) {
        return input == null ? "" : input.replace("<", "&lt;").replace(">", "&gt;");
    }

    private record ManagedItemDescriptor(String itemKey, String displayName, boolean instanceTracked) {
    }

    private record SeenInstance(String instanceId, String itemKey, List<String> locations) {
    }

    private record PendingAcquisition(String itemKey, String method, String details, long createdAt) {
    }

    private static final class KnownInstanceState {
        private final String instanceId;
        private final String itemKey;
        private final long createdAt;
        private final String createdMethod;
        private final UUID createdByUuid;
        private final String createdByName;
        private final long firstSeenAt;
        private UUID currentOwnerUuid;
        private String currentOwnerName;
        private long lastSeenAt;

        private KnownInstanceState(
            String instanceId,
            String itemKey,
            long createdAt,
            String createdMethod,
            UUID createdByUuid,
            String createdByName,
            UUID currentOwnerUuid,
            String currentOwnerName,
            long firstSeenAt,
            long lastSeenAt
        ) {
            this.instanceId = instanceId;
            this.itemKey = itemKey;
            this.createdAt = createdAt;
            this.createdMethod = createdMethod;
            this.createdByUuid = createdByUuid;
            this.createdByName = createdByName;
            this.currentOwnerUuid = currentOwnerUuid;
            this.currentOwnerName = currentOwnerName;
            this.firstSeenAt = firstSeenAt;
            this.lastSeenAt = lastSeenAt;
        }

        private static KnownInstanceState from(DatabaseManager.ManagedItemInstanceRecord record) {
            return new KnownInstanceState(
                record.instanceId(),
                record.itemKey(),
                record.createdAt(),
                record.createdMethod(),
                record.createdByUuid(),
                record.createdByName(),
                record.currentOwnerUuid(),
                record.currentOwnerName(),
                record.firstSeenAt(),
                record.lastSeenAt()
            );
        }

        private String currentOwnerName() {
            return currentOwnerName;
        }
    }
}
