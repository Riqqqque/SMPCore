package me.rique.smpcore.audit;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.awakening.AwakeningTableListener;
import me.rique.smpcore.backpack.BackpackListener;
import me.rique.smpcore.boss.BossManager;
import me.rique.smpcore.database.DatabaseManager;
import me.rique.smpcore.item.CustomToolListener;
import me.rique.smpcore.item.RewardLanternListener;
import me.rique.smpcore.item.SustenanceTalismanListener;
import me.rique.smpcore.legendary.LegendaryListener;
import me.rique.smpcore.legendary.MythicForgeListener;
import me.rique.smpcore.power.SuperpowerManager;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class ItemAuditManager implements Listener {

    private static final long SCAN_INTERVAL_TICKS = 20L * 90L;
    private static final long ANOMALY_COOLDOWN_MS = 5L * 60L * 1000L;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private final SMPCore plugin;
    private final NamespacedKey keyInstanceId;
    private final NamespacedKey keyItemKey;
    private final Map<String, KnownInstanceState> knownInstances = new ConcurrentHashMap<>();
    private final Map<String, Long> anomalyCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<PendingAcquisition>> pendingAcquisitions = new ConcurrentHashMap<>();
    private BukkitTask scanTask;

    public ItemAuditManager(SMPCore plugin) {
        this.plugin = plugin;
        this.keyInstanceId = new NamespacedKey(plugin, "audit_instance_id");
        this.keyItemKey = new NamespacedKey(plugin, "audit_item_key");
    }

    public void start() {
        try {
            for (var record : plugin.getDatabase().loadManagedItemInstances().join()) {
                knownInstances.put(record.instanceId(), KnownInstanceState.from(record));
            }
        } catch (Exception ex) {
            plugin.getLogger().severe("Failed to load item audit state: " + ex.getMessage());
        }

        Bukkit.getScheduler().runTask(plugin, this::scanOnlinePlayers);
        scanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scanOnlinePlayers, 100L, SCAN_INTERVAL_TICKS);
    }

    public void shutdown() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        pendingAcquisitions.clear();
        anomalyCooldowns.clear();
    }

    public void scheduleAudit(Player player) {
        if (player == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> auditPlayer(player, "scheduled"));
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
        rememberPending(player, descriptor.itemKey(), "craft", "Craft result click");
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            auditPlayer(player, "scheduled");
        }
    }

    private void auditPlayer(Player player, String reason) {
        if (player == null || !player.isOnline()) {
            return;
        }

        Map<String, List<String>> seenInstanceLocations = new LinkedHashMap<>();
        Map<String, Integer> legendaryCounts = new LinkedHashMap<>();

        scanInventory(player, player.getInventory(), "inventory", seenInstanceLocations, legendaryCounts, new LinkedHashSet<>());
        scanInventory(player, player.getEnderChest(), "ender_chest", seenInstanceLocations, legendaryCounts, new LinkedHashSet<>());
        scanItem(player, player.getItemOnCursor(), "cursor", seenInstanceLocations, legendaryCounts, new LinkedHashSet<>());

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
                "Holding " + entry.getValue() + " copies of " + entry.getKey()
            );
        }
    }

    private void scanInventory(
        Player owner,
        Inventory inventory,
        String scope,
        Map<String, List<String>> seenInstanceLocations,
        Map<String, Integer> legendaryCounts,
        Set<String> backpackTrail
    ) {
        if (inventory == null) {
            return;
        }
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            scanItem(owner, contents[slot], scope + "[" + slot + "]", seenInstanceLocations, legendaryCounts, backpackTrail);
        }
    }

    private void scanItem(
        Player owner,
        ItemStack item,
        String location,
        Map<String, List<String>> seenInstanceLocations,
        Map<String, Integer> legendaryCounts,
        Set<String> backpackTrail
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

        String instanceId = ensureTrackedIdentity(item, descriptor);
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
            String method = pending == null ? "unknown" : pending.method();
            String details = pending == null
                ? "First seen in " + location
                : pending.details() + " at " + location;
            KnownInstanceState created = new KnownInstanceState(
                instanceId,
                descriptor.itemKey(),
                now,
                method,
                owner.getUniqueId(),
                owner.getName(),
                owner.getUniqueId(),
                owner.getName(),
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
                pending == null ? "unknown_origin" : "acquired",
                method,
                details
            ));
        } else if (!Objects.equals(state.currentOwnerUuid, owner.getUniqueId())) {
            String previousOwnerName = state.currentOwnerName;
            state.currentOwnerUuid = owner.getUniqueId();
            state.currentOwnerName = owner.getName();
            state.lastSeenAt = now;
            persistState(state);
            persistEvent(new DatabaseManager.ManagedItemEventRecord(
                0L,
                now,
                instanceId,
                descriptor.itemKey(),
                owner.getUniqueId(),
                owner.getName(),
                owner.getUniqueId(),
                owner.getName(),
                "owner_changed_unknown",
                "unknown_transfer",
                "Seen in " + location + " after belonging to " + safeName(previousOwnerName)
            ));
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
                scanItem(owner, contents.get(i), location + "/backpack[" + i + "]", seenInstanceLocations, legendaryCounts, backpackTrail);
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

    private void rememberPending(Player player, String itemKey, String method, String details) {
        if (player == null || itemKey == null || itemKey.isBlank()) {
            return;
        }
        pendingAcquisitions.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>())
            .addLast(new PendingAcquisition(itemKey, method, details, System.currentTimeMillis()));
    }

    private PendingAcquisition consumePending(Player player, String itemKey) {
        Deque<PendingAcquisition> queue = pendingAcquisitions.get(player.getUniqueId());
        if (queue == null || queue.isEmpty()) {
            return null;
        }
        long cutoff = System.currentTimeMillis() - 10_000L;
        while (!queue.isEmpty() && queue.peekFirst().createdAt() < cutoff) {
            queue.removeFirst();
        }
        for (PendingAcquisition acquisition : queue) {
            if (acquisition.itemKey().equals(itemKey)) {
                queue.remove(acquisition);
                return acquisition;
            }
        }
        return null;
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
            state.createdAt,
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
                record.lastSeenAt()
            );
        }

        private String currentOwnerName() {
            return currentOwnerName;
        }
    }
}
