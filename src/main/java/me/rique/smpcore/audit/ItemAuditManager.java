package me.rique.smpcore.audit;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.awakening.AwakeningTableListener;
import me.rique.smpcore.backpack.BackpackListener;
import me.rique.smpcore.boss.BossManager;
import me.rique.smpcore.database.DatabaseManager;
import me.rique.smpcore.item.CustomEnchantListener;
import me.rique.smpcore.item.CustomToolListener;
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
    private static final long SCAN_INTERVAL_TICKS = 20L * 90L;
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
        scanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scanOnlinePlayers, 100L, SCAN_INTERVAL_TICKS);
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

        Map<String, List<String>> seenInstanceLocations = new LinkedHashMap<>();
        Map<String, Integer> legendaryCounts = new LinkedHashMap<>();
        String safeScope = scope == null || scope.isBlank() ? "shared_storage" : scope;
        scanInventory(observer, inventory, safeScope, seenInstanceLocations, legendaryCounts, new LinkedHashSet<>(), true, false);
        finishAudit(observer, "shared_storage", seenInstanceLocations, legendaryCounts);
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
        options.add("custom:xp_lectern");
        options.add("custom_enchant:kingslayer");
        options.add("custom_enchant:soul_siphon");
        options.add("custom_enchant:echoing");

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
        plugin.getDatabase().loadManagedItemEvents(target.getUniqueId(), filter, 25).whenComplete((records, throwable) -> {
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
                    "Item audit for <white>" + targetName + "</white>"
                        + (filter == null ? "" : " <gray>(" + filter + ")</gray>")
                ));

                if (records == null || records.isEmpty()) {
                    sender.sendMessage(me.rique.smpcore.util.MessageUtil.warn("No audit records were found."));
                    return;
                }

                for (var record : records) {
                    String time = TIME_FORMAT.format(Instant.ofEpochMilli(record.loggedAt()));
                    String details = record.details() == null || record.details().isBlank() ? "" : " <dark_gray>- " + record.details() + "</dark_gray>";
                    String actor = record.actorName() == null || record.actorName().isBlank() ? "system" : record.actorName();
                    sender.sendMessage(me.rique.smpcore.util.MessageUtil.prefixedRaw(
                        "<gray>[" + time + "]</gray> <white>" + escape(record.itemKey()) + "</white> "
                            + "<gold>" + escape(record.eventType()) + "</gold> "
                            + "<gray>(" + escape(record.method()) + ")</gray> "
                            + "<gray>actor:</gray> <white>" + escape(actor) + "</white>"
                            + details
                    ));
                }
            });
        });
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            auditPlayer(player, "scheduled");
        }
    }

    private void auditPlayer(Player player, String reason) {
        if (!auditStateLoaded || player == null || !player.isOnline()) {
            return;
        }

        Map<String, List<String>> seenInstanceLocations = new LinkedHashMap<>();
        Map<String, Integer> legendaryCounts = new LinkedHashMap<>();

        scanInventory(player, player.getInventory(), "inventory", seenInstanceLocations, legendaryCounts, new LinkedHashSet<>(), true, true);
        scanInventory(player, player.getEnderChest(), "ender_chest", seenInstanceLocations, legendaryCounts, new LinkedHashSet<>(), true, true);
        scanItem(player, player.getItemOnCursor(), "cursor", seenInstanceLocations, legendaryCounts, new LinkedHashSet<>(), true, true);
        finishAudit(player, reason, seenInstanceLocations, legendaryCounts);
    }

    private void finishAudit(
        Player player,
        String reason,
        Map<String, List<String>> seenInstanceLocations,
        Map<String, Integer> legendaryCounts
    ) {
        for (Map.Entry<String, List<String>> entry : seenInstanceLocations.entrySet()) {
            if (entry.getValue().size() <= 1) {
                continue;
            }
            logAnomaly(
                player,
                entry.getKey(),
                "duplicate_instance",
                "scan",
                "Duplicate tracked instance seen at " + String.join(", ", entry.getValue())
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
        Map<String, List<String>> seenInstanceLocations,
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
            scanItem(owner, contents[slot], scope + "[" + slot + "]", seenInstanceLocations, legendaryCounts, backpackTrail, canMutateIdentity, updateOwner);
        }
    }

    private void scanItem(
        Player owner,
        ItemStack item,
        String location,
        Map<String, List<String>> seenInstanceLocations,
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
        seenInstanceLocations.computeIfAbsent(instanceId, ignored -> new ArrayList<>()).add(location);

        if (item != null && item.getAmount() > 1) {
            logAnomaly(
                owner,
                descriptor.itemKey(),
                "stacked_item",
                "scan",
                "Tracked item stack amount was " + item.getAmount() + " at " + location
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
                    "First seen in " + location + " with no known acquisition path."
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
                    "Seen in " + location + " after belonging to " + safeName(previousOwnerName)
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
                scanItem(owner, contents.get(i), location + "/backpack[" + i + "]", seenInstanceLocations, legendaryCounts, backpackTrail, false, updateOwner);
            }
        } finally {
            backpackTrail.remove(backpackId);
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
                return new ManagedItemDescriptor("special:ender_bone", "Ender Bone", true);
            }
            if (legendary.isOrbOfTheMysticsItem(item)) {
                return new ManagedItemDescriptor("special:orb_of_the_mystics", "Orb of the Mystics", true);
            }
        }

        BackpackListener backpacks = plugin.getBackpackListener();
        if (backpacks != null && backpacks.isBackpack(item)) {
            return new ManagedItemDescriptor("custom:backpack", "Backpack", true);
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

        XpLecternListener xpLectern = plugin.getXpLecternListener();
        if (xpLectern != null && xpLectern.isLecternItem(item)) {
            return new ManagedItemDescriptor("custom:xp_lectern", "XP Lectern", false);
        }

        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers != null) {
            if (powers.isAncientScroll(item)) {
                return new ManagedItemDescriptor("power:" + SuperpowerManager.ANCIENT_SCROLL_ITEM_ID, "Ancient Scroll", true);
            }
            if (powers.isWardenHeart(item)) {
                return new ManagedItemDescriptor("relic:" + SuperpowerManager.WARDEN_HEART_ITEM_ID, "Warden Heart", false);
            }
            if (powers.isMotherNatureStick(item)) {
                return new ManagedItemDescriptor("power:" + SuperpowerManager.MOTHER_NATURE_STICK_ITEM_ID, "Stick from Mother Nature", true);
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
            return new ManagedItemDescriptor("relic:" + BossManager.DOMINION_CORE_ITEM_ID, "Dominion Core", false);
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
        String dedupeKey = subject.getUniqueId() + "|" + itemKey + "|" + eventType + "|" + details;
        long now = System.currentTimeMillis();
        Long previous = anomalyCooldowns.get(dedupeKey);
        if (previous != null && now - previous < ANOMALY_COOLDOWN_MS) {
            return;
        }
        anomalyCooldowns.put(dedupeKey, now);
        notifyStaffAnomaly(subject, itemKey, eventType, details);
        persistEvent(new DatabaseManager.ManagedItemEventRecord(
            0L,
            now,
            "",
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
        String message = "<red><bold>Audit Alert</bold></red> "
            + "<gray><white>" + escape(subject.getName()) + "</white> has suspicious custom item activity.</gray> "
            + "<gray>Type:</gray> <yellow>" + escape(eventType) + "</yellow> "
            + "<gray>Item:</gray> <white>" + escape(itemKey) + "</white> "
            + "<gray>At:</gray> <white>" + escape(location) + "</white>"
            + (details == null || details.isBlank() ? "" : " <dark_gray>- " + escape(details) + "</dark_gray>")
            + " <gray>Use <white>/itemaudit " + escape(subject.getName()) + " " + escape(itemKey) + "</white>.</gray>";

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.isOp() || staff.hasPermission(STAFF_PERMISSION)) {
                staff.sendMessage(me.rique.smpcore.util.MessageUtil.prefixedRaw(message));
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

    private String normalizeFilter(String rawFilter) {
        if (rawFilter == null) {
            return null;
        }
        String normalized = rawFilter.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
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
