package me.rique.smpcore.boss;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.config.ConfigManager;
import me.rique.smpcore.database.DatabaseManager;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.util.VisualRangeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.TileState;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Central registry and control surface for custom bosses.
 * Future bosses only need to be added to BossType to appear in the GUI and commands.
 */
public final class BossManager implements Listener {

    public static final String DOMINION_CORE_ITEM_ID = "dominion_core";
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Component MENU_TITLE = MM.deserialize("<gradient:#ff5e5b:#ff914d><bold>Boss Control</bold></gradient>");
    private static final Component RITUAL_MENU_TITLE = MM.deserialize("<gradient:#f97316:#dc2626><bold>Boss Rituals</bold></gradient>");
    private static final int[] BOSS_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };
    private static final int[] RITUAL_SLOTS = {
        10, 12, 14, 16,
        28, 30, 32, 34
    };
    private static final String SCOREBOARD_TAG = "smpcore_custom_boss";
    private static final long ORPHAN_MINION_CLEANUP_INTERVAL_MS = 30_000L;
    private static final int HOLOGRAM_TELEPORT_DURATION_TICKS = 8;
    private static final double BOSS_SCALE_HEALTH_PER_EXTRA_PLAYER = 0.35;
    private static final double BOSS_SCALE_DAMAGE_PER_EXTRA_PLAYER = 0.08;
    private static final int BOSS_SCALE_MAX_EXTRA_PLAYERS = 4;
    private static final long BOSS_FAILURE_GRACE_MS = 3_000L;
    private static final ZoneId DEFAULT_DOUBLE_DROP_ZONE = ZoneId.of("America/Denver");
    private static final long DOUBLE_DROP_ANNOUNCEMENT_CHECK_INTERVAL_MS = 30_000L;

    private final SMPCore plugin;
    private final NamespacedKey keyBossId;
    private final NamespacedKey keyBossInstanceId;
    private final NamespacedKey keyBossMarker;
    private final NamespacedKey keyBossPhase;
    private final NamespacedKey keyBossPrimaryCooldown;
    private final NamespacedKey keyBossSecondaryCooldown;
    private final NamespacedKey keyBossMinionMarker;
    private final NamespacedKey keyBossMinionOwner;
    private final NamespacedKey keyBossScaledPlayerCount;
    private final NamespacedKey keyBossArenaHazardCooldown;
    private final NamespacedKey keyDominionCoreItem;
    private final NamespacedKey keyBossLootChest;
    private final NamespacedKey keyBossLootHologram;
    private final NamespacedKey keyBossLootHologramBlock;

    private final Map<UUID, BossRecord> trackedBosses = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> trackedByBossId = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> holograms = new ConcurrentHashMap<>();
    private final Map<UUID, BossArena> bossArenas = new ConcurrentHashMap<>();
    private final Map<UUID, BossFightState> bossFightStates = new ConcurrentHashMap<>();
    private final Set<UUID> allowedBossTeleports = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingRituals = ConcurrentHashMap.newKeySet();
    private BukkitTask heartbeatTask;
    private long nextOrphanMinionCleanupAt;
    private long nextDoubleDropAnnouncementCheckAt;
    private boolean doubleDropAnnouncementStateInitialized;
    private boolean lastDoubleDropActive;
    private long lastDoubleDropActiveWindow = Long.MIN_VALUE;
    private long announcedDoubleDropStartWindow = Long.MIN_VALUE;
    private long announcedDoubleDropEndingWindow = Long.MIN_VALUE;
    private long announcedDoubleDropEndWindow = Long.MIN_VALUE;

    public BossManager(SMPCore plugin) {
        this.plugin = plugin;
        this.keyBossId = new NamespacedKey(plugin, "boss_id");
        this.keyBossInstanceId = new NamespacedKey(plugin, "boss_instance_id");
        this.keyBossMarker = new NamespacedKey(plugin, "boss_marker");
        this.keyBossPhase = new NamespacedKey(plugin, "boss_phase");
        this.keyBossPrimaryCooldown = new NamespacedKey(plugin, "boss_primary_cd");
        this.keyBossSecondaryCooldown = new NamespacedKey(plugin, "boss_secondary_cd");
        this.keyBossMinionMarker = new NamespacedKey(plugin, "boss_minion_marker");
        this.keyBossMinionOwner = new NamespacedKey(plugin, "boss_minion_owner");
        this.keyBossScaledPlayerCount = new NamespacedKey(plugin, "boss_scaled_player_count");
        this.keyBossArenaHazardCooldown = new NamespacedKey(plugin, "boss_arena_hazard_cd");
        this.keyDominionCoreItem = new NamespacedKey(plugin, DOMINION_CORE_ITEM_ID);
        this.keyBossLootChest = new NamespacedKey(plugin, "boss_loot_chest");
        this.keyBossLootHologram = new NamespacedKey(plugin, "boss_loot_hologram");
        this.keyBossLootHologramBlock = new NamespacedKey(plugin, "boss_loot_hologram_block");
    }

    public void start() {
        plugin.getDatabase().loadAllBosses().whenComplete((loaded, throwable) -> {
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> finishBossRecordLoad(loaded, throwable));
        });
        startHeartbeat();
    }

    private void finishBossRecordLoad(List<BossRecord> loaded, Throwable throwable) {
        if (throwable != null) {
            Throwable root = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
            plugin.getLogger().severe("Failed to load custom boss records: " + root.getMessage());
            return;
        }

        if (loaded != null) {
            for (BossRecord record : loaded) {
                trackRecord(record);
            }
        }
        reconcileLoadedBosses();
    }

    public void shutdown() {
        stopHeartbeat();
        destroyAllBossVisuals();
        trackedBosses.clear();
        trackedByBossId.clear();
        bossArenas.clear();
        bossFightStates.clear();
        allowedBossTeleports.clear();
        pendingRituals.clear();
    }

    public List<String> bossIds() {
        List<String> ids = new ArrayList<>();
        for (BossType type : BossType.values()) {
            ids.add(type.id());
        }
        return ids;
    }

    public String normalizeBossId(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.isBlank()) {
            return null;
        }
        BossType type = BossType.fromInput(normalized);
        return type == null ? normalized : type.id();
    }

    public String displayNameForBoss(String input) {
        String normalized = normalizeBossId(input);
        BossType type = BossType.fromId(normalized);
        if (type != null) {
            return type.plainDisplayName();
        }
        return prettyBossName(normalized);
    }

    public Set<String> bossCommandOptions() {
        LinkedHashSet<String> options = new LinkedHashSet<>();
        for (BossType type : BossType.values()) {
            options.add(type.commandToken());
            options.add(type.id());
        }
        return options;
    }

    public ItemStack createDominionCoreItem() {
        ItemStack item = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.LEGENDARY, "Dominion Core"));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.ECHO_SHARD,
            CustomLoreUtil.Rarity.LEGENDARY.label(),
            "RELIC",
            List.of("<gray>A pulsing shard torn from a fallen dominion warden.</gray>"),
            List.of(CustomLoreUtil.section(
                "Use",
                "Dominion Repair",
                "<gray>Use it in an <white>Anvil</white> with <white>Crimson Dominion</white>.</gray>",
                "<gray>Fully restores the blade's durability.</gray>"
            ))
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(keyDominionCoreItem, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isDominionCore(ItemStack item) {
        if (item == null || item.getType() != Material.ECHO_SHARD) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte tagged = meta.getPersistentDataContainer().get(keyDominionCoreItem, PersistentDataType.BYTE);
        return tagged != null && tagged == (byte) 1;
    }

    public void openBossMenu(Player player) {
        reconcileLoadedBosses();

        Inventory inventory = Bukkit.createInventory(
            new BossMenuHolder(),
            54,
            BedrockCompat.menuTitle(player, MENU_TITLE, "Boss Control")
        );
        ItemStack filler = menuItem(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        inventory.setItem(4, createOverviewItem());
        inventory.setItem(45, menuItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to /menu.</gray>")));
        inventory.setItem(49, createClearAllItem());
        inventory.setItem(53, createRefreshItem());

        BossType[] types = BossType.values();
        if (types.length == 0) {
            inventory.setItem(22, menuItem(
                Material.PAPER,
                "<yellow><bold>No Bosses Registered</bold></yellow>",
                List.of(
                    "<gray>The boss framework is live.</gray>",
                    "<gray>Add future boss entries in the boss registry,</gray>",
                    "<gray>and they will appear here automatically.</gray>"
                )
            ));
        } else {
            for (int i = 0; i < types.length && i < BOSS_SLOTS.length; i++) {
                inventory.setItem(BOSS_SLOTS[i], createBossEntryItem(types[i]));
            }
        }
        player.openInventory(inventory);
    }

    public void openRitualMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(
            new BossRitualMenuHolder(),
            54,
            BedrockCompat.menuTitle(player, RITUAL_MENU_TITLE, "Boss Rituals")
        );
        ItemStack filler = menuItem(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        inventory.setItem(4, menuItem(
            Material.WRITABLE_BOOK,
            "<gradient:#facc15:#f97316><bold>Ritual Codex</bold></gradient>",
            List.of(
                "<gray>Each boss has its own shrine pattern.</gray>",
                "<gray>Build the pattern, then right-click the focus block</gray>",
                "<gray>or any shrine block with the listed catalyst.</gray>",
                "<dark_gray>The shrine is consumed on use. Catalysts refund only if the boss fails to form.</dark_gray>"
            )
        ));
        inventory.setItem(49, menuItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to /menu.</gray>")));

        BossType[] types = BossType.values();
        for (int i = 0; i < types.length && i < RITUAL_SLOTS.length; i++) {
            inventory.setItem(RITUAL_SLOTS[i], createRitualEntryItem(types[i]));
        }
        player.openInventory(inventory);
    }

    public BossActionResult spawnBoss(Player player, String requestedBossId) {
        BossType type = BossType.fromId(normalizeBossId(requestedBossId));
        if (type == null) {
            return new BossActionResult(false, "Unknown boss.");
        }
        String restriction = spawnRestrictionMessage(type, player.getWorld());
        if (restriction != null) {
            return new BossActionResult(false, restriction);
        }
        Location spawnLocation = findBossSpawnLocation(player, type);
        if (spawnLocation == null) {
            return new BossActionResult(false, "No safe spot was found nearby to spawn " + type.plainDisplayName() + ".");
        }
        return spawnBoss(type, spawnLocation);
    }

    public BossActionResult spawnBoss(BossType type, Location location) {
        return spawnBoss(type, location, null, false);
    }

    private BossActionResult spawnBoss(BossType type, Location location, Player summoner, boolean fromRitual) {
        if (type == null) {
            return new BossActionResult(false, "Unknown boss.");
        }
        if (BossType.values().length == 0) {
            return new BossActionResult(false, "No custom bosses are registered yet.");
        }
        World world = location == null ? null : location.getWorld();
        if (world == null) {
            return new BossActionResult(false, "Spawn location is invalid.");
        }
        String restriction = spawnRestrictionMessage(type, world);
        if (restriction != null) {
            return new BossActionResult(false, restriction);
        }

        LivingEntity spawned;
        try {
            Entity entity = world.spawnEntity(location, type.entityType());
            if (!(entity instanceof LivingEntity living)) {
                entity.remove();
                return new BossActionResult(false, "That boss type does not spawn as a living entity.");
            }
            spawned = living;
        } catch (Exception ex) {
            return new BossActionResult(false, "Failed to spawn " + type.plainDisplayName() + ".");
        }

        applyBossState(spawned, type);

        BossRecord record = new BossRecord(
            spawned.getUniqueId(),
            type.id(),
            world.getName(),
            spawned.getLocation().getX(),
            spawned.getLocation().getY(),
            spawned.getLocation().getZ(),
            spawned.getChunk().getX(),
            spawned.getChunk().getZ(),
            System.currentTimeMillis()
        );
        trackRecord(record);
        plugin.getDatabase().saveBossRecord(record);
        try {
            startBossArena(spawned, type);
            playBossSpawnBurst(spawned, type, fromRitual);
            if (summoner != null) {
                spawned.getWorld().playSound(spawned.getLocation(), type.ritual().arrivalSound(), 1.35f, 0.72f);
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Boss spawn visuals failed for " + type.id() + ": " + ex.getMessage());
        }

        return new BossActionResult(
            true,
            "Spawned <white>" + type.plainDisplayName() + "</white> at <white>"
                + spawned.getLocation().getBlockX() + ", "
                + spawned.getLocation().getBlockY() + ", "
                + spawned.getLocation().getBlockZ() + "</white>."
        );
    }

    public BossActionResult despawnBoss(String requestedBossId) {
        String normalized = normalizeBossId(requestedBossId);
        if (normalized == null || normalized.isBlank()) {
            return new BossActionResult(false, "Unknown boss.");
        }
        int removed = despawnBossRecords(record -> record.bossId().equalsIgnoreCase(normalized));
        if (removed <= 0) {
            return new BossActionResult(false, "No active <white>" + displayNameForBoss(normalized) + "</white> bosses were found.");
        }
        return new BossActionResult(true, "Removed <white>" + removed + "</white> active <white>" + displayNameForBoss(normalized) + "</white> boss" + (removed == 1 ? "" : "es") + ".");
    }

    public BossActionResult despawnAllBosses() {
        int removed = despawnBossRecords(record -> true);
        if (removed <= 0) {
            return new BossActionResult(true, "There were no active custom bosses to remove.");
        }
        return new BossActionResult(true, "Removed <white>" + removed + "</white> active custom boss" + (removed == 1 ? "" : "es") + ".");
    }

    public boolean isCustomBoss(Entity entity) {
        return bossRecord(entity) != null;
    }

    public List<String> statusLines() {
        reconcileLoadedBosses();
        List<String> lines = new ArrayList<>();
        lines.add("<gold><bold>Boss Status</bold></gold>");
        lines.add("<gray>Total tracked bosses:</gray> <white>" + trackedBosses.size() + "</white>");

        BossType[] types = BossType.values();
        if (types.length == 0) {
            lines.add("<gray>No custom bosses are registered yet.</gray>");
            return lines;
        }

        for (BossType type : types) {
            lines.add("<gray>" + type.plainDisplayName() + ":</gray> <white>" + activeCount(type.id()) + "</white>");
        }
        return lines;
    }

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTrackedBosses, 1L, 10L);
    }

    private void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }
    }

    private void tickTrackedBosses() {
        tickBossDoubleDropAnnouncements();

        List<BossRecord> snapshot = new ArrayList<>(trackedBosses.values());
        for (BossRecord record : snapshot) {
            World world = Bukkit.getWorld(record.world());
            if (world == null) {
                destroyBossVisuals(record.entityUuid());
                untrackRecord(record.entityUuid());
                plugin.getDatabase().deleteBossRecord(record.entityUuid());
                continue;
            }

            if (!world.isChunkLoaded(record.chunkX(), record.chunkZ())) {
                continue;
            }

            Entity entity = Bukkit.getEntity(record.entityUuid());
            if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
                destroyBossVisuals(record.entityUuid());
                untrackRecord(record.entityUuid());
                plugin.getDatabase().deleteBossRecord(record.entityUuid());
                continue;
            }

            BossType type = BossType.fromId(record.bossId());
            if (type == null) {
                destroyBossVisuals(record.entityUuid());
                living.remove();
                untrackRecord(record.entityUuid());
                plugin.getDatabase().deleteBossRecord(record.entityUuid());
                continue;
            }
            if (!isAllowedBossWorld(type, living.getWorld())) {
                plugin.getLogger().warning("Removed " + type.id() + " from invalid world " + living.getWorld().getName() + ".");
                destroyBossVisuals(record.entityUuid());
                living.remove();
                untrackRecord(record.entityUuid());
                plugin.getDatabase().deleteBossRecord(record.entityUuid());
                continue;
            }

            ensureBossVisuals(living, type);
            updateBossScaling(living, type);
            updateBossBar(living, type);
            updateBossHologram(living, type);
            tickBossBehavior(living, type);
            tickBossArena(living, type);
            tickBossFailure(record, living, type);
        }

        maybeCleanupOrphanBossMinions();
    }

    private void maybeCleanupOrphanBossMinions() {
        long now = System.currentTimeMillis();
        if (now < nextOrphanMinionCleanupAt) {
            return;
        }

        nextOrphanMinionCleanupAt = now + ORPHAN_MINION_CLEANUP_INTERVAL_MS;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                cleanupOrphanBossMinions(chunk);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        BossRecord record = bossRecord(entity);
        if (record != null) {
            BossType type = BossType.fromId(record.bossId());
            Player killer = event.getEntity().getKiller();
            event.getDrops().clear();
            BossFightState state = bossFightStates.get(record.entityUuid());
            if (killer == null) {
                killer = topOnlineParticipant(state);
            }
            boolean doubleDrops = isBossDoubleDropsActive();
            announceBossKill(type, killer, event.getEntity().getLocation(), doubleDrops);
            if (killer != null && plugin.getLeaderboardManager() != null) {
                plugin.getLeaderboardManager().recordBossKill(killer, record.bossId());
            }
            int dropMultiplier = doubleDrops ? 2 : 1;
            List<ItemStack> rewardDrops = new ArrayList<>();
            if (type == BossType.VORALITH_THE_CRIMSON_WARDEN) {
                ItemStack coreDrop = createDominionCoreItem();
                addBossDrop(rewardDrops, killer, coreDrop, dropMultiplier, "Dropped from Voralith the Crimson Warden.");
            }
            if (type == BossType.AURELION_THE_RIFT_SERAPH
                && plugin.getAwakeningTableListener() != null
                && plugin.getConfigManager().awakeningTableEnabled
                && ThreadLocalRandom.current().nextDouble() < plugin.getConfigManager().awakeningTableRiftSeraphDropChance) {
                addBossDrop(
                    rewardDrops,
                    killer,
                    plugin.getAwakeningTableListener().createAwakeningTableItem(),
                    dropMultiplier,
                    "Dropped from Aurelion the Rift Seraph."
                );
            }
            if (type != null && plugin.getSeasonRelicManager() != null) {
                for (ItemStack drop : plugin.getSeasonRelicManager().createBossDrops(type.id())) {
                    if (drop == null || drop.getType().isAir()) {
                        continue;
                    }
                    addBossDrop(rewardDrops, killer, drop, dropMultiplier, "Dropped from " + type.plainDisplayName() + ".");
                }
            }
            if (rewardDrops.isEmpty()) {
                addBossDrop(rewardDrops, killer, guaranteedBossFallbackDrop(type), dropMultiplier, "Guaranteed fallback drop from " + (type == null ? "a custom boss" : type.plainDisplayName()) + ".");
            }
            spawnBossLootChest(type, event.getEntity().getLocation(), rewardDrops);
            event.setDroppedExp(Math.max(event.getDroppedExp(), bossExperience(type)));
            finishBossFight(record, type, true, doubleDrops, event.getEntity().getLocation());
            despawnBossMinions(record.entityUuid());
            destroyBossVisuals(record.entityUuid());
            untrackRecord(record.entityUuid());
            plugin.getDatabase().deleteBossRecord(record.entityUuid());
            return;
        }

        if (isBossMinion(entity)) {
            entity.getPersistentDataContainer().remove(keyBossMinionMarker);
            entity.getPersistentDataContainer().remove(keyBossMinionOwner);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossLootChestBreak(BlockBreakEvent event) {
        if (isBossLootChest(event.getBlock())) {
            removeBossLootHologram(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossCombust(EntityCombustEvent event) {
        if (bossRecord(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    private void announceBossKill(BossType type, Player killer, Location location, boolean doubleDrops) {
        if (type == null) {
            return;
        }
        String killerName = killer == null ? "Someone" : killer.getName();
        String bonus = doubleDrops ? " <gold><bold>Double drops are active.</bold></gold>" : "";
        Bukkit.broadcast(MessageUtil.prefixedRaw(
            "<gradient:#ff4d6d:#facc15><bold>" + killerName + "</bold></gradient>"
                + " <gray>has slain</gray> <red><bold>" + type.plainDisplayName() + "</bold></red><gray>.</gray>" + bonus
        ));
        World world = location == null ? null : location.getWorld();
        if (world != null) {
            world.playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.1f, 0.8f);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, location.clone().add(0.0, 1.2, 0.0), 55, 0.75, 0.9, 0.75, 0.03);
            world.spawnParticle(Particle.DUST, location.clone().add(0.0, 1.2, 0.0), 34, 0.8, 0.9, 0.8, 0.0, new Particle.DustOptions(type.ritual().color(), 1.4f));
        }
    }

    private void addBossDrop(List<ItemStack> rewardDrops, Player owner, ItemStack drop, int multiplier, String auditDetails) {
        for (ItemStack multiplied : multipliedDrops(drop, multiplier)) {
            if (multiplied == null || multiplied.getType().isAir()) {
                continue;
            }
            if (owner != null && plugin.getItemAuditManager() != null) {
                plugin.getItemAuditManager().recordKnownAcquisition(
                    owner,
                    multiplied,
                    "boss_drop",
                    auditDetails
                );
            }
            rewardDrops.add(multiplied);
        }
    }

    private List<ItemStack> multipliedDrops(ItemStack source, int multiplier) {
        if (source == null || source.getType().isAir()) {
            return List.of();
        }
        int total = Math.max(1, source.getAmount()) * Math.max(1, multiplier);
        int maxStack = Math.max(1, source.getMaxStackSize());
        List<ItemStack> drops = new ArrayList<>();
        while (total > 0) {
            int amount = Math.min(maxStack, total);
            ItemStack next = source.clone();
            next.setAmount(amount);
            drops.add(next);
            total -= amount;
        }
        return drops;
    }

    private ItemStack guaranteedBossFallbackDrop(BossType type) {
        if (type != null && plugin.getSeasonRelicManager() != null) {
            String relicId = primaryBossRelicId(type);
            if (relicId != null) {
                ItemStack relic = plugin.getSeasonRelicManager().createRelicItem(relicId);
                if (relic != null && !relic.getType().isAir()) {
                    return relic;
                }
            }
        }

        ItemStack fallback = new ItemStack(type == null ? Material.DIAMOND : type.menuIcon(), 1);
        ItemMeta meta = fallback.getItemMeta();
        if (meta != null) {
            String bossName = type == null ? "Unknown Boss" : type.plainDisplayName();
            meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.RARE, bossName + " Trophy"));
            meta.lore(CustomLoreUtil.buildStyledLore(
                meta,
                fallback.getType(),
                CustomLoreUtil.Rarity.RARE.label(),
                "BOSS TROPHY",
                List.of("<gray>Guaranteed fallback reward from a custom boss.</gray>"),
                List.of()
            ));
            fallback.setItemMeta(meta);
        }
        return fallback;
    }

    private String primaryBossRelicId(BossType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case YULE_THE_MINION -> "gilded_skull";
            case KAEL_THE_ASHEN -> "solar_ember";
            case VESPER_THE_WIDOW_QUEEN -> "widow_silk";
            case VORALITH_THE_CRIMSON_WARDEN -> "crimson_rib";
            case AURELION_THE_RIFT_SERAPH -> "rift_lens";
            case NEREIDA_THE_ABYSS_MOTHER -> "abyssal_pearl";
            case IRON_SAINT -> "titan_gear";
            case MIREWOOD_THE_ROOT_TYRANT -> "living_bark";
        };
    }

    private void spawnBossLootChest(BossType type, Location location, List<ItemStack> rewards) {
        if (location == null || location.getWorld() == null || rewards == null || rewards.isEmpty()) {
            return;
        }

        Block block = findBossLootChestBlock(location);
        if (block == null) {
            block = findForcedBossLootChestBlock(location);
        }

        Chest chest = placeBossLootChest(block);
        if (chest == null) {
            dropBossLootNaturally(location, rewards);
            plugin.getLogger().warning("Dropped boss loot naturally because no loot chest spot could be prepared at "
                + location.getWorld().getName() + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + ".");
            return;
        }

        String bossName = type == null ? "Unknown Boss" : type.plainDisplayName();
        chest.customName(MM.deserialize("<gradient:#ff4d6d:#facc15><bold>" + escapeMiniMessage(bossName) + " Loot</bold></gradient>"));
        chest.getPersistentDataContainer().set(keyBossLootChest, PersistentDataType.STRING, bossName);
        chest.update(true, false);

        Inventory inventory = chest.getBlockInventory();
        Map<Integer, ItemStack> leftovers = inventory.addItem(rewards.stream().map(ItemStack::clone).toArray(ItemStack[]::new));
        Block lootBlock = block;
        leftovers.values().forEach(leftover -> lootBlock.getWorld().dropItemNaturally(lootBlock.getLocation().add(0.5, 1.0, 0.5), leftover));
        spawnBossLootHologram(block, bossName);
        World world = block.getWorld();
        Location center = block.getLocation().add(0.5, 0.9, 0.5);
        world.playSound(center, Sound.BLOCK_CHEST_OPEN, 0.9f, 1.25f);
        world.spawnParticle(Particle.END_ROD, center, 24, 0.35, 0.35, 0.35, 0.025);
        world.spawnParticle(Particle.DUST, center, 26, 0.45, 0.35, 0.45, 0.0, new Particle.DustOptions(type == null ? Color.fromRGB(255, 214, 96) : type.ritual().color(), 1.15f));
    }

    private Chest placeBossLootChest(Block block) {
        if (block == null) {
            return null;
        }

        try {
            prepareBossLootChestSpace(block);
            block.setType(Material.CHEST, false);
            if (block.getBlockData() instanceof org.bukkit.block.data.type.Chest chestData) {
                chestData.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
                block.setBlockData(chestData, false);
            }
            BlockState state = block.getState();
            return state instanceof Chest chest ? chest : null;
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Could not place boss loot chest at " + block.getWorld().getName()
                + " " + block.getX() + "," + block.getY() + "," + block.getZ() + ": " + ex.getMessage());
            return null;
        }
    }

    private void prepareBossLootChestSpace(Block block) {
        if (block == null) {
            return;
        }
        if (!isBossLootProtectedBlock(block)) {
            block.setType(Material.AIR, false);
        }
        Block above = block.getRelative(BlockFace.UP);
        if (!above.getType().isAir() && !isBossLootProtectedBlock(above)) {
            above.setType(Material.AIR, false);
        }
    }

    private void dropBossLootNaturally(Location location, List<ItemStack> rewards) {
        if (location == null || location.getWorld() == null || rewards == null) {
            return;
        }
        for (ItemStack reward : rewards) {
            if (reward != null && !reward.getType().isAir()) {
                location.getWorld().dropItemNaturally(location, reward.clone());
            }
        }
    }

    private Block findBossLootChestBlock(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        int baseX = location.getBlockX();
        int baseY = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 1, location.getBlockY()));
        int baseZ = location.getBlockZ();
        int[][] offsets = {
            {0, 0, 0}, {0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
            {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1}, {1, 0, 1}, {-1, 0, -1},
            {1, 0, -1}, {-1, 0, 1}, {0, -1, 0}
        };
        for (int[] offset : offsets) {
            int y = baseY + offset[1];
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                continue;
            }
            Block candidate = world.getBlockAt(baseX + offset[0], y, baseZ + offset[2]);
            if (isBossLootChestSpot(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private Block findForcedBossLootChestBlock(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }

        int baseX = location.getBlockX();
        int baseY = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 1, location.getBlockY()));
        int baseZ = location.getBlockZ();
        for (int radius = 0; radius <= 3; radius++) {
            for (int yOffset = -1; yOffset <= 2; yOffset++) {
                int y = baseY + yOffset;
                if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                    continue;
                }
                for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                    for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                        if (Math.max(Math.abs(xOffset), Math.abs(zOffset)) != radius) {
                            continue;
                        }
                        Block candidate = world.getBlockAt(baseX + xOffset, y, baseZ + zOffset);
                        if (canForceBossLootChestSpot(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isBossLootChestSpot(Block block) {
        if (block == null) {
            return false;
        }
        BlockState state = block.getState();
        if (state instanceof InventoryHolder) {
            return false;
        }
        if (state instanceof TileState tileState
            && tileState.getPersistentDataContainer().has(keyBossLootChest, PersistentDataType.STRING)) {
            return false;
        }
        Material type = block.getType();
        if (isBossLootProtectedMaterial(type)) {
            return false;
        }
        return type.isAir() || block.isReplaceable() || block.isPassable();
    }

    private boolean canForceBossLootChestSpot(Block block) {
        if (block == null || isBossLootProtectedBlock(block)) {
            return false;
        }
        Block above = block.getRelative(BlockFace.UP);
        return above.getType().isAir()
            || above.isReplaceable()
            || above.isPassable()
            || !isBossLootProtectedBlock(above);
    }

    private boolean isBossLootProtectedBlock(Block block) {
        if (block == null) {
            return true;
        }
        BlockState state = block.getState();
        if (state instanceof InventoryHolder) {
            return true;
        }
        if (state instanceof TileState tileState
            && tileState.getPersistentDataContainer().has(keyBossLootChest, PersistentDataType.STRING)) {
            return true;
        }
        return isBossLootProtectedMaterial(block.getType());
    }

    private boolean isBossLootProtectedMaterial(Material type) {
        return type == Material.BEDROCK || type == Material.BARRIER || type == Material.END_PORTAL || type == Material.END_GATEWAY
            || type == Material.COMMAND_BLOCK || type == Material.CHAIN_COMMAND_BLOCK || type == Material.REPEATING_COMMAND_BLOCK
            || type == Material.STRUCTURE_BLOCK || type == Material.JIGSAW;
    }

    private void spawnBossLootHologram(Block block, String bossName) {
        if (block == null || block.getWorld() == null) {
            return;
        }
        String blockKey = bossLootBlockKey(block);
        block.getWorld().spawn(block.getLocation().add(0.5, 1.45, 0.5), TextDisplay.class, display -> {
            display.text(Component.empty()
                .append(MM.deserialize("<gradient:#ff4d6d:#facc15><bold>" + escapeMiniMessage(bossName) + " Loot</bold></gradient>"))
                .append(Component.newline())
                .append(MM.deserialize("<gray>Boss rewards chest</gray>")));
            display.setGravity(false);
            display.setPersistent(false);
            display.setInvulnerable(true);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(false);
            display.setShadowed(false);
            display.setLineWidth(180);
            display.setTextOpacity((byte) 255);
            display.setBackgroundColor(Color.fromARGB(92, 12, 8, 10));
            VisualRangeUtil.applyHologramRange(display);
            display.getPersistentDataContainer().set(keyBossLootHologram, PersistentDataType.BYTE, (byte) 1);
            display.getPersistentDataContainer().set(keyBossLootHologramBlock, PersistentDataType.STRING, blockKey);
        });
    }

    private void removeBossLootHologram(Block block) {
        if (block == null || block.getWorld() == null) {
            return;
        }
        String blockKey = bossLootBlockKey(block);
        for (Entity entity : block.getWorld().getNearbyEntities(block.getLocation().add(0.5, 1.45, 0.5), 2.0, 2.0, 2.0)) {
            if (!(entity instanceof TextDisplay display)) {
                continue;
            }
            String stored = display.getPersistentDataContainer().get(keyBossLootHologramBlock, PersistentDataType.STRING);
            if (blockKey.equals(stored)) {
                display.remove();
            }
        }
    }

    private boolean isBossLootChest(Block block) {
        return block != null
            && block.getState() instanceof TileState tileState
            && tileState.getPersistentDataContainer().has(keyBossLootChest, PersistentDataType.STRING);
    }

    private String bossLootBlockKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private int bossExperience(BossType type) {
        if (type == null) {
            return 80;
        }
        return switch (type) {
            case YULE_THE_MINION -> 225;
            case KAEL_THE_ASHEN -> 300;
            case VESPER_THE_WIDOW_QUEEN -> 320;
            case MIREWOOD_THE_ROOT_TYRANT -> 440;
            case NEREIDA_THE_ABYSS_MOTHER -> 475;
            case AURELION_THE_RIFT_SERAPH -> 600;
            case IRON_SAINT -> 700;
            case VORALITH_THE_CRIMSON_WARDEN -> 950;
        };
    }

    private boolean isBossDoubleDropsActive() {
        return currentBossDoubleDropWindow().active();
    }

    private void tickBossDoubleDropAnnouncements() {
        long nowMillis = System.currentTimeMillis();
        if (nowMillis < nextDoubleDropAnnouncementCheckAt) {
            return;
        }
        nextDoubleDropAnnouncementCheckAt = nowMillis + DOUBLE_DROP_ANNOUNCEMENT_CHECK_INTERVAL_MS;

        BossDoubleDropWindow window = currentBossDoubleDropWindow();
        if (!window.enabled()) {
            doubleDropAnnouncementStateInitialized = false;
            lastDoubleDropActive = false;
            lastDoubleDropActiveWindow = Long.MIN_VALUE;
            return;
        }

        if (!doubleDropAnnouncementStateInitialized) {
            doubleDropAnnouncementStateInitialized = true;
            lastDoubleDropActive = false;
        }

        long windowKey = window.startMillis();
        if (window.active()) {
            lastDoubleDropActiveWindow = windowKey;
            if (announcedDoubleDropStartWindow != windowKey) {
                announcedDoubleDropStartWindow = windowKey;
                announceBossDoubleDropStart(window);
            }
            if (!window.allDay()
                && announcedDoubleDropEndingWindow != windowKey
                && !window.now().isBefore(window.warningAt())
                && window.now().isBefore(window.end())) {
                announcedDoubleDropEndingWindow = windowKey;
                announceBossDoubleDropEnding(window);
            }
        } else if (lastDoubleDropActive
            && lastDoubleDropActiveWindow != Long.MIN_VALUE
            && announcedDoubleDropEndWindow != lastDoubleDropActiveWindow) {
            announcedDoubleDropEndWindow = lastDoubleDropActiveWindow;
            announceBossDoubleDropEnd();
        }

        lastDoubleDropActive = window.active();
    }

    private BossDoubleDropWindow currentBossDoubleDropWindow() {
        ConfigManager config = plugin.getConfigManager();
        boolean enabled = config == null
            ? plugin.getConfig().getBoolean("bosses.double-drops.enabled", true)
            : config.bossDoubleDropsEnabled;
        ZoneId zone = bossDoubleDropZone(config);
        ZonedDateTime now = ZonedDateTime.now(zone);
        int startHour = config == null
            ? Math.max(0, Math.min(23, plugin.getConfig().getInt("bosses.double-drops.start-hour", 16)))
            : config.bossDoubleDropsStartHour;
        int endHour = config == null
            ? Math.max(0, Math.min(24, plugin.getConfig().getInt("bosses.double-drops.end-hour", 18)))
            : config.bossDoubleDropsEndHour;
        int warningMinutes = config == null
            ? Math.max(1, Math.min(180, plugin.getConfig().getInt("bosses.double-drops.ending-warning-minutes", 10)))
            : config.bossDoubleDropsEndingWarningMinutes;
        if (!enabled) {
            return new BossDoubleDropWindow(false, false, false, now, now, now, now);
        }

        ZonedDateTime start;
        ZonedDateTime end;
        boolean active;
        if (startHour == endHour) {
            start = startOfDay(now);
            end = start.plusDays(1);
            return new BossDoubleDropWindow(true, true, true, now, start, start, end);
        }

        ZonedDateTime todayStart = startOfDay(now).plusHours(startHour);
        ZonedDateTime todayEnd = endHour == 24 ? startOfDay(now).plusDays(1) : startOfDay(now).plusHours(endHour);
        if (startHour < endHour) {
            if (now.isBefore(todayStart)) {
                start = todayStart;
                end = todayEnd;
                active = false;
            } else if (now.isBefore(todayEnd)) {
                start = todayStart;
                end = todayEnd;
                active = true;
            } else {
                start = todayStart.plusDays(1);
                end = todayEnd.plusDays(1);
                active = false;
            }
        } else if (!now.isBefore(todayStart)) {
            start = todayStart;
            end = todayEnd.plusDays(1);
            active = true;
        } else if (now.isBefore(todayEnd)) {
            start = todayStart.minusDays(1);
            end = todayEnd;
            active = true;
        } else {
            start = todayStart;
            end = todayEnd.plusDays(1);
            active = false;
        }

        long durationMinutes = Math.max(1L, (end.toInstant().toEpochMilli() - start.toInstant().toEpochMilli()) / 60_000L);
        long safeWarningMinutes = Math.min(Math.max(1, warningMinutes), durationMinutes);
        ZonedDateTime warningAt = end.minusMinutes(safeWarningMinutes);
        return new BossDoubleDropWindow(true, active, false, now, start, warningAt, end);
    }

    private ZoneId bossDoubleDropZone(ConfigManager config) {
        String zoneRaw = config == null
            ? plugin.getConfig().getString("bosses.double-drops.timezone", "America/Denver")
            : config.bossDoubleDropsTimezone;
        try {
            return ZoneId.of(zoneRaw == null || zoneRaw.isBlank() ? "America/Denver" : zoneRaw);
        } catch (DateTimeException ignored) {
            return DEFAULT_DOUBLE_DROP_ZONE;
        }
    }

    private ZonedDateTime startOfDay(ZonedDateTime time) {
        return time.withHour(0).withMinute(0).withSecond(0).withNano(0);
    }

    private void announceBossDoubleDropStart(BossDoubleDropWindow window) {
        String endText = window.allDay() ? "until tomorrow" : "until " + formatDoubleDropTime(window.end());
        Bukkit.broadcast(MessageUtil.prefixedRaw(
            "<gradient:#facc15:#fb7185><bold>Boss double loot is active!</bold></gradient> "
                + "<gray>All custom boss reward chests are doubled " + endText + ".</gray>"
        ));
        playDoubleDropAnnouncementEffects(Sound.ENTITY_ENDER_DRAGON_GROWL, Particle.TOTEM_OF_UNDYING);
    }

    private void announceBossDoubleDropEnding(BossDoubleDropWindow window) {
        Bukkit.broadcast(MessageUtil.prefixedRaw(
            "<gold><bold>Boss double loot is ending soon.</bold></gold> "
                + "<gray>It ends at <white>" + formatDoubleDropTime(window.end()) + "</white>.</gray>"
        ));
        playDoubleDropAnnouncementEffects(Sound.BLOCK_BEACON_DEACTIVATE, Particle.WAX_ON);
    }

    private void announceBossDoubleDropEnd() {
        Bukkit.broadcast(MessageUtil.prefixedRaw(
            "<gray><bold>Boss double loot has ended.</bold></gray> "
                + "<dark_gray>Bosses are back to normal rewards.</dark_gray>"
        ));
        playDoubleDropAnnouncementEffects(Sound.BLOCK_BEACON_DEACTIVATE, Particle.ASH);
    }

    private void playDoubleDropAnnouncementEffects(Sound sound, Particle particle) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location location = player.getLocation().add(0.0, 1.1, 0.0);
            player.playSound(player.getLocation(), sound, 0.75f, 1.0f);
            player.spawnParticle(particle, location, 18, 0.55, 0.45, 0.55, 0.02);
        }
    }

    private String formatDoubleDropTime(ZonedDateTime time) {
        return DateTimeFormatter.ofPattern("h:mm a z", Locale.ROOT).format(time);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossEnvironmentalDamage(EntityDamageEvent event) {
        BossRecord record = bossRecord(event.getEntity());
        if (record == null) {
            return;
        }
        if (isEnvironmentalCheeseDamage(event.getCause())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossTeleport(EntityTeleportEvent event) {
        BossRecord record = bossRecord(event.getEntity());
        if (record == null) {
            return;
        }
        BossType type = BossType.fromId(record.bossId());
        if (type == BossType.AURELION_THE_RIFT_SERAPH
            && !allowedBossTeleports.contains(record.entityUuid())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof LivingEntity shooter)) {
            return;
        }
        BossRecord record = bossRecord(shooter);
        if (record == null || !(event.getProjectile() instanceof Projectile projectile)) {
            return;
        }
        BossType type = BossType.fromId(record.bossId());
        if (type == BossType.KAEL_THE_ASHEN) {
            handleKaelBowShot(shooter, projectile, bossPhase(shooter));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossDamage(EntityDamageByEntityEvent event) {
        LivingEntity bossEntity;
        boolean projectileHit = false;
        if (event.getDamager() instanceof LivingEntity attacker) {
            bossEntity = attacker;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            bossEntity = shooter;
            projectileHit = true;
        } else {
            return;
        }

        BossRecord record = bossRecord(bossEntity);
        if (record == null) {
            return;
        }
        BossType type = BossType.fromId(record.bossId());
        if (type == null || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        if (target instanceof Player player) {
            recordBossFightEngagement(record.entityUuid(), player);
        }

        int phase = bossPhase(bossEntity);
        if (type == BossType.YULE_THE_MINION && !projectileHit) {
            spawnYuleAttackParticles(bossEntity, target, phase);
            if (phase >= 2) {
                applyYulePhaseTwoKnockback(bossEntity, target);
            }
            return;
        }

        if (type == BossType.KAEL_THE_ASHEN && projectileHit) {
            handleKaelProjectileHit(bossEntity, target, phase, event);
            return;
        }

        if (type == BossType.VESPER_THE_WIDOW_QUEEN && !projectileHit) {
            handleVesperMeleeHit(bossEntity, target, phase);
            return;
        }

        if (type == BossType.VORALITH_THE_CRIMSON_WARDEN && !projectileHit) {
            handleVoralithMeleeHit(bossEntity, target, phase, event);
            return;
        }

        if (type == BossType.AURELION_THE_RIFT_SERAPH && !projectileHit) {
            handleAurelionMeleeHit(bossEntity, target, phase, event);
            return;
        }

        if (type == BossType.NEREIDA_THE_ABYSS_MOTHER && !projectileHit) {
            handleNereidaMeleeHit(bossEntity, target, phase, event);
            return;
        }

        if (type == BossType.IRON_SAINT && !projectileHit) {
            handleIronSaintMeleeHit(bossEntity, target, phase, event);
            return;
        }

        if (type == BossType.MIREWOOD_THE_ROOT_TYRANT && !projectileHit) {
            handleMirewoodMeleeHit(bossEntity, target, phase, event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossTakenDamage(EntityDamageByEntityEvent event) {
        BossRecord record = bossRecord(event.getEntity());
        if (record == null) {
            return;
        }

        Player attacker = attackingPlayer(event.getDamager());
        if (attacker == null) {
            return;
        }

        double finalDamage = Math.max(0.0, event.getFinalDamage());
        if (finalDamage <= 0.0) {
            recordBossFightEngagement(record.entityUuid(), attacker);
            return;
        }

        BossFightState state = fightState(record);
        state.addDamage(attacker, finalDamage);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossFightHealing(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        BossFightState state = activeFightStateFor(player);
        if (state == null) {
            return;
        }
        state.addHealing(player, Math.max(0.0, event.getAmount()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBossFightPlayerDeath(PlayerDeathEvent event) {
        markBossFightLossCheck(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBossFightPlayerQuit(PlayerQuitEvent event) {
        markBossFightLossCheck(event.getPlayer());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        reconcileChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BossMenuHolder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() < 0 || event.getRawSlot() >= topSize) {
            return;
        }

        if (event.getRawSlot() == 45) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> player.performCommand("menu"));
            return;
        }

        if (event.getRawSlot() == 49) {
            BossActionResult result = despawnAllBosses();
            player.sendMessage(result.success() ? MessageUtil.success(result.message()) : MessageUtil.error(result.message()));
            Bukkit.getScheduler().runTask(plugin, () -> openBossMenu(player));
            return;
        }

        if (event.getRawSlot() == 53) {
            reconcileLoadedBosses();
            Bukkit.getScheduler().runTask(plugin, () -> openBossMenu(player));
            return;
        }

        BossType type = bossTypeForSlot(event.getRawSlot());
        if (type == null) {
            return;
        }

        if (event.isRightClick()) {
            BossActionResult result = despawnBoss(type.id());
            player.sendMessage(result.success() ? MessageUtil.success(result.message()) : MessageUtil.error(result.message()));
        } else if (event.isLeftClick()) {
            BossActionResult result = spawnBoss(player, type.id());
            player.sendMessage(result.success() ? MessageUtil.success(result.message()) : MessageUtil.error(result.message()));
        }

        Bukkit.getScheduler().runTask(plugin, () -> openBossMenu(player));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossMenuDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BossMenuHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossRitualMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BossRitualMenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() == 49) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> player.performCommand("menu"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossRitualMenuDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BossRitualMenuHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossRitualInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getClickedBlock() == null || event.getItem() == null || event.getItem().getType() == Material.AIR) {
            return;
        }

        RitualMatch match = ritualMatchFor(event.getClickedBlock(), event.getItem());
        if (match == null) {
            sendRitualHint(event.getPlayer(), event.getClickedBlock(), event.getItem());
            return;
        }

        event.setCancelled(true);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        beginBossRitual(event.getPlayer(), match.type(), match.focus(), event.getItem());
    }

    private BossType bossTypeForSlot(int rawSlot) {
        BossType[] types = BossType.values();
        for (int i = 0; i < types.length && i < BOSS_SLOTS.length; i++) {
            if (BOSS_SLOTS[i] == rawSlot) {
                return types[i];
            }
        }
        return null;
    }

    private BossType ritualTypeFor(Block focus, ItemStack catalyst) {
        if (plugin.getSeasonRelicManager() != null && plugin.getSeasonRelicManager().isSeasonRelic(catalyst)) {
            return null;
        }
        Material catalystType = catalyst.getType();
        Material focusType = focus.getType();
        for (BossType type : BossType.values()) {
            BossRitual ritual = type.ritual();
            if (ritual.catalyst() == catalystType && ritual.focusBlock() == focusType) {
                return type;
            }
        }
        return null;
    }

    private RitualMatch ritualMatchFor(Block clicked, ItemStack catalyst) {
        if (plugin.getSeasonRelicManager() != null && plugin.getSeasonRelicManager().isSeasonRelic(catalyst)) {
            return null;
        }
        BossType directType = ritualTypeFor(clicked, catalyst);
        if (directType != null) {
            return new RitualMatch(directType, clicked);
        }

        Material catalystType = catalyst.getType();
        RitualMatch closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (BossType type : BossType.values()) {
            BossRitual ritual = type.ritual();
            if (ritual.catalyst() != catalystType) {
                continue;
            }
            for (Block focus : nearbyFocusBlocks(clicked, ritual.focusBlock())) {
                double distance = clicked.getLocation().distanceSquared(focus.getLocation());
                if (distance < closestDistance) {
                    closest = new RitualMatch(type, focus);
                    closestDistance = distance;
                }
            }
        }
        return closest;
    }

    private List<Block> nearbyFocusBlocks(Block clicked, Material focusMaterial) {
        List<Block> matches = new ArrayList<>();
        World world = clicked.getWorld();
        int baseX = clicked.getX();
        int baseY = clicked.getY();
        int baseZ = clicked.getZ();
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Block candidate = world.getBlockAt(baseX + x, baseY + y, baseZ + z);
                    if (candidate.getType() == focusMaterial) {
                        matches.add(candidate);
                    }
                }
            }
        }
        return matches;
    }

    private void sendRitualHint(Player player, Block clicked, ItemStack catalyst) {
        BossType focusType = ritualFocusType(clicked);
        if (focusType != null) {
            BossRitual ritual = focusType.ritual();
            if (ritual.catalyst() != catalyst.getType()) {
                player.sendMessage(MessageUtil.warn("That is the <white>" + ritual.name() + "</white> focus, but it needs <white>"
                    + prettyBossName(ritual.catalyst().name()) + "</white> as the catalyst."));
                player.sendMessage(MessageUtil.info("Use <white>/bossrituals</white> if you need the shrine steps."));
            }
            return;
        }

        RitualMatch nearby = nearbyRitualFocus(clicked);
        if (nearby == null || (!isKnownRitualCatalyst(catalyst.getType()) && !isRitualComponent(nearby.type(), clicked.getType()))) {
            return;
        }

        BossRitual ritual = nearby.type().ritual();
        if (ritual.catalyst() != catalyst.getType()) {
            player.sendMessage(MessageUtil.warn("That looks like part of <white>" + ritual.name() + "</white>, but it needs <white>"
                + prettyBossName(ritual.catalyst().name()) + "</white> as the catalyst."));
        } else {
            player.sendMessage(MessageUtil.warn("That looks like part of <white>" + ritual.name() + "</white>. Right-click the focus block or any shrine block near it."));
        }
        player.sendMessage(MessageUtil.prefixedRaw("<gray>Focus block: <white>"
            + nearby.focus().getX() + " " + nearby.focus().getY() + " " + nearby.focus().getZ()
            + "</white> <dark_gray>(" + prettyBossName(ritual.focusBlock().name()) + ")</dark_gray></gray>"));
        player.sendMessage(MessageUtil.info("Use <white>/bossrituals</white> if you need the full pattern."));
    }

    private BossType ritualFocusType(Block block) {
        Material material = block.getType();
        for (BossType type : BossType.values()) {
            if (type.ritual().focusBlock() == material) {
                return type;
            }
        }
        return null;
    }

    private RitualMatch nearbyRitualFocus(Block clicked) {
        RitualMatch closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (BossType type : BossType.values()) {
            for (Block focus : nearbyFocusBlocks(clicked, type.ritual().focusBlock())) {
                double distance = clicked.getLocation().distanceSquared(focus.getLocation());
                if (distance < closestDistance) {
                    closest = new RitualMatch(type, focus);
                    closestDistance = distance;
                }
            }
        }
        return closest;
    }

    private boolean isKnownRitualCatalyst(Material material) {
        for (BossType type : BossType.values()) {
            if (type.ritual().catalyst() == material) {
                return true;
            }
        }
        return false;
    }

    private boolean isRitualComponent(BossType type, Material material) {
        return switch (type) {
            case YULE_THE_MINION -> material == Material.BELL
                || material == Material.SOUL_SAND
                || material == Material.GOLD_BLOCK;
            case KAEL_THE_ASHEN -> material == Material.SOUL_CAMPFIRE
                || material == Material.BONE_BLOCK;
            case VESPER_THE_WIDOW_QUEEN -> material == Material.COBWEB
                || material == Material.MOSS_BLOCK
                || material == Material.BLACK_CANDLE;
            case VORALITH_THE_CRIMSON_WARDEN -> material == Material.SCULK_SHRIEKER
                || material == Material.SCULK
                || material == Material.SCULK_SENSOR
                || material == Material.CALIBRATED_SCULK_SENSOR
                || material == Material.SCULK_VEIN
                || material == Material.REINFORCED_DEEPSLATE
                || material == Material.SCULK_CATALYST
                || material == Material.REDSTONE_BLOCK
                || material == Material.SOUL_LANTERN;
            case AURELION_THE_RIFT_SERAPH -> material == Material.END_ROD
                || material == Material.PURPUR_BLOCK
                || material == Material.END_STONE_BRICKS;
            case NEREIDA_THE_ABYSS_MOTHER -> material == Material.CONDUIT
                || material == Material.PRISMARINE
                || material == Material.SEA_LANTERN;
            case IRON_SAINT -> material == Material.ANVIL
                || material == Material.SMITHING_TABLE
                || material == Material.IRON_BLOCK;
            case MIREWOOD_THE_ROOT_TYRANT -> material == Material.MANGROVE_ROOTS
                || material == Material.MOSS_BLOCK
                || material == Material.OAK_SAPLING;
        };
    }

    private void beginBossRitual(Player player, BossType type, Block focus, ItemStack catalyst) {
        BossRitual ritual = type.ritual();
        String restriction = spawnRestrictionMessage(type, focus.getWorld());
        if (restriction != null) {
            player.sendMessage(MessageUtil.error(restriction));
            return;
        }
        List<String> problems = ritualProblems(type, focus);
        if (!problems.isEmpty()) {
            player.sendMessage(MessageUtil.error("The <white>" + ritual.name() + "</white> is not complete."));
            for (String problem : problems) {
                player.sendMessage(MessageUtil.prefixedRaw("<gray>- " + problem + "</gray>"));
            }
            player.sendMessage(MessageUtil.info("Use <white>/bossrituals</white> if you need the shrine steps."));
            return;
        }

        String ritualKey = ritualKey(type, focus.getLocation());
        if (!pendingRituals.add(ritualKey)) {
            player.sendMessage(MessageUtil.warn("That ritual is already waking up."));
            return;
        }

        Location seed = focus.getLocation().add(0.5, 1.0, 0.5);
        Location spawnLocation = findSafeBossSpawnLocation(seed, type);
        if (spawnLocation == null) {
            pendingRituals.remove(ritualKey);
            player.sendMessage(MessageUtil.error("The ritual needs clear space above the focus block."));
            return;
        }

        ItemStack refund = catalyst.clone();
        refund.setAmount(1);
        boolean consumed = consumeRitualCatalyst(player, catalyst);
        Location center = focus.getLocation().add(0.5, 0.75, 0.5);
        World focusWorld = focus.getWorld();
        int focusChunkX = focus.getChunk().getX();
        int focusChunkZ = focus.getChunk().getZ();
        consumeRitualShrine(type, focus);
        announceRitualStart(player, type, center);
        playRitualWarmup(type, center);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (focusWorld == null || !focusWorld.isChunkLoaded(focusChunkX, focusChunkZ)) {
                    if (consumed) {
                        refundRitualCatalyst(player, center, refund);
                    }
                    plugin.getLogger().warning("Boss ritual for " + type.plainDisplayName() + " fizzled because the shrine chunk unloaded before spawn.");
                    if (player.isOnline()) {
                        player.sendMessage(MessageUtil.error("The ritual fizzled because the shrine area unloaded before the boss formed. Your catalyst was returned."));
                    }
                    return;
                }

                BossActionResult result;
                try {
                    result = spawnBoss(type, spawnLocation, player, true);
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Boss ritual spawn failed after shrine cleanup: " + ex.getMessage());
                    result = new BossActionResult(false, "The ritual failed while the boss was forming.");
                }
                if (!result.success()) {
                    if (consumed) {
                        refundRitualCatalyst(player, center, refund);
                    }
                }
                if (player.isOnline()) {
                    player.sendMessage(result.success() ? MessageUtil.success(result.message()) : MessageUtil.error(result.message()));
                }
            } finally {
                pendingRituals.remove(ritualKey);
            }
        }, ritual.warmupTicks());
    }

    private String ritualKey(BossType type, Location location) {
        return type.id() + "|" + location.getWorld().getUID() + "|"
            + location.getBlockX() + "|" + location.getBlockY() + "|" + location.getBlockZ();
    }

    private boolean consumeRitualCatalyst(Player player, ItemStack item) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return false;
        }
        if (item.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(item.getAmount() - 1);
        }
        return true;
    }

    private void refundRitualCatalyst(Player player, Location fallback, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        if (player.isOnline()) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            return;
        }
        World world = fallback.getWorld();
        if (world != null) {
            world.dropItemNaturally(fallback, item);
        }
    }

    private void consumeRitualShrine(BossType type, Block focus) {
        Set<Block> blocks = ritualFootprintBlocks(type, focus);
        World world = focus.getWorld();
        Location center = focus.getLocation().add(0.5, 0.8, 0.5);
        for (Block block : blocks) {
            if (block.getType() == Material.AIR) {
                continue;
            }
            Location blockCenter = block.getLocation().add(0.5, 0.5, 0.5);
            world.spawnParticle(Particle.BLOCK, blockCenter, 12, 0.22, 0.22, 0.22, 0.02, block.getBlockData());
            world.spawnParticle(Particle.DUST, blockCenter, 5, 0.18, 0.18, 0.18, 0.0, new Particle.DustOptions(type.ritual().color(), 0.85f));
            block.setType(Material.AIR, true);
        }
        world.spawnParticle(Particle.REVERSE_PORTAL, center, 70, 1.0, 0.8, 1.0, 0.18);
        world.spawnParticle(Particle.DUST, center, 42, 0.85, 0.55, 0.85, 0.0, new Particle.DustOptions(type.ritual().color(), 1.35f));
        world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.25f, 0.65f);
    }

    private Set<Block> ritualFootprintBlocks(BossType type, Block focus) {
        Set<Block> blocks = ritualBlocks(type, focus);
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -2; z <= 2; z++) {
                    Block block = focus.getRelative(x, y, z);
                    if (isRitualComponent(type, block.getType())) {
                        blocks.add(block);
                    }
                }
            }
        }
        return blocks;
    }

    private Set<Block> ritualBlocks(BossType type, Block focus) {
        Set<Block> blocks = new LinkedHashSet<>();
        blocks.add(focus);
        switch (type) {
            case YULE_THE_MINION -> {
                Block base = focus.getRelative(BlockFace.DOWN);
                blocks.add(base);
                addCardinalBlocks(blocks, base);
            }
            case KAEL_THE_ASHEN -> {
                Block base = focus.getRelative(BlockFace.DOWN);
                blocks.add(base);
                addCardinalBlocks(blocks, base);
            }
            case VESPER_THE_WIDOW_QUEEN -> {
                blocks.add(focus.getRelative(BlockFace.DOWN));
                addCardinalBlocks(blocks, focus);
            }
            case VORALITH_THE_CRIMSON_WARDEN -> {
                blocks.add(focus.getRelative(BlockFace.DOWN));
                blocks.add(focus.getRelative(BlockFace.NORTH));
                blocks.add(focus.getRelative(BlockFace.SOUTH));
                blocks.add(focus.getRelative(BlockFace.EAST));
                blocks.add(focus.getRelative(BlockFace.WEST));
                addCornerBlocks(blocks, focus);
            }
            case AURELION_THE_RIFT_SERAPH -> {
                Block base = focus.getRelative(BlockFace.DOWN);
                blocks.add(base);
                addCardinalBlocks(blocks, base);
            }
            case NEREIDA_THE_ABYSS_MOTHER -> {
                Block base = focus.getRelative(BlockFace.DOWN);
                blocks.add(base);
                addCardinalBlocks(blocks, base);
            }
            case IRON_SAINT -> {
                Block base = focus.getRelative(BlockFace.DOWN);
                blocks.add(base);
                addCardinalBlocks(blocks, base);
            }
            case MIREWOOD_THE_ROOT_TYRANT -> {
                Block base = focus.getRelative(BlockFace.DOWN);
                blocks.add(base);
                addCardinalBlocks(blocks, base);
            }
        }
        return blocks;
    }

    private void addCardinalBlocks(Set<Block> blocks, Block center) {
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            blocks.add(center.getRelative(face));
        }
    }

    private void addCornerBlocks(Set<Block> blocks, Block center) {
        int[][] offsets = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] offset : offsets) {
            blocks.add(center.getRelative(offset[0], 0, offset[1]));
        }
    }

    private List<String> ritualProblems(BossType type, Block focus) {
        BossRitual ritual = type.ritual();
        List<String> problems = new ArrayList<>();
        if (focus.getType() != ritual.focusBlock()) {
            problems.add("Focus block must be <white>" + prettyBossName(ritual.focusBlock().name()) + "</white>.");
            return problems;
        }

        switch (type) {
            case YULE_THE_MINION -> {
                requireRelative(problems, focus, BlockFace.DOWN, Material.SOUL_SAND, "Soul Sand beneath the bell");
                requireCardinals(problems, focus.getRelative(BlockFace.DOWN), Material.GOLD_BLOCK, "Gold Blocks around the Soul Sand base");
            }
            case KAEL_THE_ASHEN -> {
                requireRelative(problems, focus, BlockFace.DOWN, Material.BONE_BLOCK, "Bone Block beneath the soul campfire");
                requireCardinals(problems, focus.getRelative(BlockFace.DOWN), Material.BONE_BLOCK, "Bone Blocks around the center Bone Block");
            }
            case VESPER_THE_WIDOW_QUEEN -> {
                requireRelative(problems, focus, BlockFace.DOWN, Material.MOSS_BLOCK, "Moss Block beneath the web");
                requireCardinals(problems, focus, Material.BLACK_CANDLE, "Black Candles on all four sides");
            }
            case VORALITH_THE_CRIMSON_WARDEN -> {
                requireRelative(problems, focus, BlockFace.DOWN, Material.REINFORCED_DEEPSLATE, "Reinforced Deepslate beneath the shrieker");
                requireRelative(problems, focus, BlockFace.NORTH, Material.SCULK_CATALYST, "Sculk Catalysts north and south");
                requireRelative(problems, focus, BlockFace.SOUTH, Material.SCULK_CATALYST, "Sculk Catalysts north and south");
                requireRelative(problems, focus, BlockFace.EAST, Material.REDSTONE_BLOCK, "Redstone Blocks east and west");
                requireRelative(problems, focus, BlockFace.WEST, Material.REDSTONE_BLOCK, "Redstone Blocks east and west");
                requireCorners(problems, focus, Material.SOUL_LANTERN, "Soul Lanterns on the four corners");
            }
            case AURELION_THE_RIFT_SERAPH -> {
                requireRelative(problems, focus, BlockFace.DOWN, Material.PURPUR_BLOCK, "Purpur Block beneath the End Rod");
                requireCardinals(problems, focus.getRelative(BlockFace.DOWN), Material.END_STONE_BRICKS, "End Stone Bricks around the Purpur base");
            }
            case NEREIDA_THE_ABYSS_MOTHER -> {
                requireRelative(problems, focus, BlockFace.DOWN, Material.PRISMARINE, "Prismarine beneath the Conduit");
                requireCardinals(problems, focus.getRelative(BlockFace.DOWN), Material.SEA_LANTERN, "Sea Lanterns around the Prismarine base");
            }
            case IRON_SAINT -> {
                requireRelative(problems, focus, BlockFace.DOWN, Material.SMITHING_TABLE, "Smithing Table beneath the Anvil");
                requireCardinals(problems, focus.getRelative(BlockFace.DOWN), Material.IRON_BLOCK, "Iron Blocks around the Smithing Table");
            }
            case MIREWOOD_THE_ROOT_TYRANT -> {
                requireRelative(problems, focus, BlockFace.DOWN, Material.MOSS_BLOCK, "Moss Block beneath the Mangrove Roots");
                requireCardinals(problems, focus.getRelative(BlockFace.DOWN), Material.OAK_SAPLING, "Oak Saplings around the Moss Block");
            }
        }
        return problems;
    }

    private void requireRelative(List<String> problems, Block focus, BlockFace face, Material material, String label) {
        if (focus.getRelative(face).getType() != material && !problems.contains(label)) {
            problems.add(label);
        }
    }

    private void requireCardinals(List<String> problems, Block focus, Material material, String label) {
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            if (focus.getRelative(face).getType() != material) {
                problems.add(label);
                return;
            }
        }
    }

    private void requireCorners(List<String> problems, Block focus, Material material, String label) {
        int[][] offsets = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] offset : offsets) {
            if (focus.getRelative(offset[0], 0, offset[1]).getType() != material) {
                problems.add(label);
                return;
            }
        }
    }

    private void announceRitualStart(Player player, BossType type, Location center) {
        String message = "<gold><white>" + player.getName() + "</white> began <white>"
            + type.ritual().name() + "</white> for " + type.displayName() + "<gold>.</gold>";
        for (Player viewer : center.getWorld().getPlayers()) {
            if (viewer.getLocation().distanceSquared(center) <= 64.0 * 64.0) {
                viewer.sendMessage(MessageUtil.prefixedRaw(message));
            }
        }
        center.getWorld().playSound(center, type.ritual().startSound(), 1.25f, 0.75f);
    }

    private void playRitualWarmup(BossType type, Location center) {
        BossRitual ritual = type.ritual();
        int pulses = Math.max(3, (int) (ritual.warmupTicks() / 8));
        for (int i = 0; i <= pulses; i++) {
            int pulse = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (center.getWorld() == null) {
                    return;
                }
                double progress = Math.min(1.0, pulse / (double) pulses);
                spawnRitualRing(center, 1.2 + (progress * 2.2), ritual.color(), 36, 0.10 + progress * 0.65);
                spawnRitualParticle(center.getWorld(), ritual.primaryParticle(), center, 18 + pulse * 2, 0.35, 0.45, 0.35, 0.02, ritual.color());
                center.getWorld().spawnParticle(Particle.ENCHANT, center.clone().add(0.0, 0.5 + progress, 0.0), 12, 0.45, 0.25, 0.45, 0.18);
                playBossSpecificRitualPulse(type, center, progress, pulse);
                center.getWorld().playSound(center, ritual.pulseSound(), 0.85f, 0.65f + (float) progress * 0.55f);
            }, i * 8L);
        }
    }

    private void playRitualFizzle(BossType type, Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.SMOKE, center, 40, 0.75, 0.55, 0.75, 0.04);
        world.spawnParticle(Particle.DUST, center, 18, 0.55, 0.35, 0.55, 0.0, new Particle.DustOptions(type.ritual().color(), 0.9f));
        world.playSound(center, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.65f);
    }

    private void playBossSpawnBurst(LivingEntity entity, BossType type, boolean fromRitual) {
        Location center = entity.getLocation().clone().add(0.0, Math.min(1.4, entity.getHeight() * 0.65), 0.0);
        BossRitual ritual = type.ritual();
        World world = entity.getWorld();
        spawnRitualParticle(world, Particle.FLASH, center, 1, 0.0, 0.0, 0.0, 0.0, ritual.color());
        spawnRitualParticle(world, ritual.primaryParticle(), center, fromRitual ? 90 : 42, 1.1, 0.85, 1.1, 0.04, ritual.color());
        world.spawnParticle(Particle.DUST, center, fromRitual ? 70 : 28, 1.2, 0.75, 1.2, 0.0, new Particle.DustOptions(ritual.color(), fromRitual ? 1.55f : 1.1f));
        spawnRitualRing(center, fromRitual ? 4.8 : 2.6, ritual.color(), fromRitual ? 72 : 36, 0.15);
        playBossSpecificSpawnBurst(type, center, fromRitual);
        world.playSound(center, ritual.arrivalSound(), fromRitual ? 1.8f : 1.1f, fromRitual ? 0.65f : 0.9f);
    }

    private void spawnRitualRing(Location center, double radius, Color color, int points, double yOffset) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.15f);
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i) / points;
            Location point = center.clone().add(Math.cos(angle) * radius, yOffset, Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, dust);
        }
    }

    private void spawnRitualParticle(
        World world,
        Particle particle,
        Location location,
        int count,
        double offsetX,
        double offsetY,
        double offsetZ,
        double extra,
        Color color
    ) {
        if (world == null || particle == null || location == null) {
            return;
        }
        Color safeColor = color == null ? Color.WHITE : color;
        Class<?> dataType = particle.getDataType();
        try {
            if (dataType == Void.class) {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
            } else if (dataType == Color.class) {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, safeColor);
            } else if (dataType == Particle.DustOptions.class) {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, new Particle.DustOptions(safeColor, 1.1f));
            } else if (dataType == Particle.DustTransition.class) {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, new Particle.DustTransition(safeColor, Color.WHITE, 1.1f));
            } else if (dataType == org.bukkit.block.data.BlockData.class) {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, Material.STONE.createBlockData());
            } else if (dataType == ItemStack.class) {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, new ItemStack(Material.NETHER_STAR));
            } else if (dataType == Float.class) {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, Float.valueOf(1.0f));
            } else if (dataType == Integer.class) {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, Integer.valueOf(0));
            } else if (dataType == Particle.Spell.class) {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, new Particle.Spell(safeColor, 1.0f));
            } else if (dataType == Particle.Trail.class) {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, new Particle.Trail(location.clone().add(0.0, 1.0, 0.0), safeColor, 20));
            } else {
                plugin.getLogger().fine("Skipped ritual particle " + particle + " because it requires " + dataType.getName() + ".");
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().fine("Skipped ritual particle " + particle + ": " + ex.getMessage());
        }
    }

    private void playBossSpecificRitualPulse(BossType type, Location center, double progress, int pulse) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        Location raised = center.clone().add(0.0, 0.35 + progress * 1.25, 0.0);
        switch (type) {
            case YULE_THE_MINION -> {
                world.spawnParticle(Particle.ANGRY_VILLAGER, raised, 5 + pulse, 0.42, 0.22, 0.42, 0.01);
                world.spawnParticle(Particle.CRIT, raised, 8, 0.35, 0.18, 0.35, 0.03);
                spawnRitualRing(center, 0.85 + progress * 1.7, Color.fromRGB(255, 174, 66), 24, 0.30 + progress * 0.55);
                if (pulse % 2 == 0) {
                    world.playSound(center, Sound.BLOCK_BELL_USE, 0.45f, 1.45f);
                }
            }
            case KAEL_THE_ASHEN -> {
                world.spawnParticle(Particle.ASH, raised, 26, 0.85, 0.28, 0.85, 0.02);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, raised, 8 + pulse, 0.38, 0.20, 0.38, 0.02);
                spawnRitualRing(center, 0.65 + progress * 2.0, Color.fromRGB(170, 190, 210), 28, 0.18 + progress * 0.75);
            }
            case VESPER_THE_WIDOW_QUEEN -> {
                world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, raised, 18, 0.95, 0.30, 0.95, 0.03);
                world.spawnParticle(Particle.WITCH, raised, 10, 0.62, 0.18, 0.62, 0.02);
                if (pulse % 2 == 1) {
                    world.spawnParticle(Particle.SQUID_INK, center.clone().add(0.0, 0.45, 0.0), 10, 0.70, 0.14, 0.70, 0.01);
                }
            }
            case VORALITH_THE_CRIMSON_WARDEN -> {
                world.spawnParticle(Particle.SCULK_SOUL, raised, 18 + pulse * 2, 0.62, 0.38, 0.62, 0.03);
                world.spawnParticle(Particle.ELECTRIC_SPARK, raised, 8, 0.45, 0.20, 0.45, 0.05);
                spawnRitualRing(center, 1.0 + progress * 2.4, Color.fromRGB(210, 35, 65), 36, 0.25 + progress * 0.9);
                if (pulse % 3 == 0) {
                    world.playSound(center, Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.65f + (float) progress * 0.25f);
                }
            }
            case AURELION_THE_RIFT_SERAPH -> {
                world.spawnParticle(Particle.PORTAL, raised, 36, 0.95, 0.42, 0.95, 0.35);
                world.spawnParticle(Particle.REVERSE_PORTAL, raised, 18, 0.55, 0.22, 0.55, 0.08);
                spawnRitualRing(center, 0.85 + progress * 2.4, Color.fromRGB(185, 100, 255), 36, 0.20 + progress * 0.85);
            }
            case NEREIDA_THE_ABYSS_MOTHER -> {
                world.spawnParticle(Particle.NAUTILUS, raised, 20 + pulse, 0.75, 0.32, 0.75, 0.04);
                world.spawnParticle(Particle.SPLASH, raised, 18, 0.65, 0.18, 0.65, 0.05);
                spawnRitualRing(center, 0.75 + progress * 2.1, Color.fromRGB(45, 190, 230), 34, 0.18 + progress * 0.7);
            }
            case IRON_SAINT -> {
                world.spawnParticle(Particle.CRIT, raised, 16 + pulse, 0.65, 0.28, 0.65, 0.04);
                world.spawnParticle(Particle.DUST, raised, 12, 0.55, 0.24, 0.55, 0.0, new Particle.DustOptions(Color.fromRGB(205, 200, 170), 1.2f));
                if (pulse % 2 == 0) {
                    world.playSound(center, Sound.BLOCK_ANVIL_USE, 0.55f, 0.65f);
                }
            }
            case MIREWOOD_THE_ROOT_TYRANT -> {
                world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, raised, 22, 0.95, 0.36, 0.95, 0.04);
                world.spawnParticle(Particle.HAPPY_VILLAGER, raised, 8, 0.45, 0.22, 0.45, 0.02);
                spawnRitualRing(center, 0.80 + progress * 2.0, Color.fromRGB(70, 180, 70), 30, 0.12 + progress * 0.65);
            }
        }
    }

    private void playBossSpecificSpawnBurst(BossType type, Location center, boolean fromRitual) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        switch (type) {
            case YULE_THE_MINION -> {
                world.spawnParticle(Particle.CRIT, center, fromRitual ? 34 : 16, 1.35, 1.0, 1.35, 0.08);
                world.spawnParticle(Particle.ANGRY_VILLAGER, center.clone().add(0.0, 1.0, 0.0), fromRitual ? 28 : 12, 0.85, 0.45, 0.85, 0.02);
                world.playSound(center, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.25f, 0.7f);
                scheduleSpawnHelix(center, Color.fromRGB(255, 184, 77), Color.fromRGB(190, 40, 28), Particle.CRIT, 2.2, 3.2, 18, 2);
            }
            case KAEL_THE_ASHEN -> {
                world.spawnParticle(Particle.ASH, center, fromRitual ? 90 : 38, 1.5, 0.95, 1.5, 0.05);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, fromRitual ? 54 : 24, 1.0, 0.75, 1.0, 0.04);
                world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.55f);
                scheduleSpawnHelix(center, Color.fromRGB(185, 205, 225), Color.fromRGB(80, 120, 150), Particle.SOUL_FIRE_FLAME, 2.5, 3.4, 20, 3);
            }
            case VESPER_THE_WIDOW_QUEEN -> {
                world.spawnParticle(Particle.WITCH, center, fromRitual ? 70 : 30, 1.35, 0.8, 1.35, 0.06);
                world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, center.clone().add(0.0, 0.75, 0.0), fromRitual ? 90 : 36, 1.6, 0.55, 1.6, 0.05);
                world.playSound(center, Sound.ENTITY_SPIDER_DEATH, 1.15f, 0.55f);
                scheduleSpawnHelix(center, Color.fromRGB(45, 220, 95), Color.fromRGB(95, 20, 130), Particle.WITCH, 2.35, 2.8, 18, 4);
            }
            case VORALITH_THE_CRIMSON_WARDEN -> {
                if (fromRitual) {
                    world.strikeLightningEffect(center);
                }
                world.spawnParticle(Particle.SONIC_BOOM, center.clone().add(0.0, 1.1, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
                world.spawnParticle(Particle.SCULK_SOUL, center, fromRitual ? 120 : 52, 1.7, 1.1, 1.7, 0.06);
                world.spawnParticle(Particle.ELECTRIC_SPARK, center.clone().add(0.0, 1.0, 0.0), fromRitual ? 64 : 26, 1.2, 0.65, 1.2, 0.08);
                world.playSound(center, Sound.ENTITY_WARDEN_ROAR, 1.65f, 0.62f);
                world.playSound(center, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.25f, 0.78f);
                scheduleSpawnHelix(center, Color.fromRGB(230, 35, 65), Color.fromRGB(15, 190, 205), Particle.SCULK_SOUL, 3.0, 4.1, 24, 4);
            }
            case AURELION_THE_RIFT_SERAPH -> {
                world.spawnParticle(Particle.PORTAL, center, fromRitual ? 160 : 70, 1.8, 1.2, 1.8, 0.65);
                world.spawnParticle(Particle.REVERSE_PORTAL, center.clone().add(0.0, 1.1, 0.0), fromRitual ? 80 : 32, 1.2, 0.75, 1.2, 0.12);
                world.playSound(center, Sound.ENTITY_ENDERMAN_SCREAM, 1.1f, 0.7f);
                scheduleSpawnHelix(center, Color.fromRGB(190, 110, 255), Color.fromRGB(40, 20, 90), Particle.REVERSE_PORTAL, 2.8, 3.8, 22, 5);
            }
            case NEREIDA_THE_ABYSS_MOTHER -> {
                world.spawnParticle(Particle.NAUTILUS, center, fromRitual ? 120 : 54, 1.5, 0.9, 1.5, 0.08);
                world.spawnParticle(Particle.SPLASH, center.clone().add(0.0, 0.75, 0.0), fromRitual ? 110 : 40, 1.7, 0.55, 1.7, 0.08);
                world.playSound(center, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.2f, 0.82f);
                scheduleSpawnHelix(center, Color.fromRGB(40, 210, 240), Color.fromRGB(20, 80, 130), Particle.NAUTILUS, 2.5, 3.1, 20, 4);
            }
            case IRON_SAINT -> {
                world.spawnParticle(Particle.CRIT, center, fromRitual ? 90 : 34, 1.4, 0.75, 1.4, 0.08);
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center.clone().add(0.0, 0.8, 0.0), fromRitual ? 40 : 16, 1.0, 0.45, 1.0, 0.03);
                world.playSound(center, Sound.BLOCK_ANVIL_LAND, 1.6f, 0.55f);
                scheduleSpawnHelix(center, Color.fromRGB(210, 210, 190), Color.fromRGB(235, 175, 60), Particle.CRIT, 2.7, 3.5, 22, 3);
            }
            case MIREWOOD_THE_ROOT_TYRANT -> {
                world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, center, fromRitual ? 120 : 46, 1.8, 0.75, 1.8, 0.08);
                world.spawnParticle(Particle.HAPPY_VILLAGER, center.clone().add(0.0, 1.0, 0.0), fromRitual ? 46 : 18, 1.0, 0.4, 1.0, 0.04);
                world.playSound(center, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.15f, 0.7f);
                scheduleSpawnHelix(center, Color.fromRGB(70, 190, 70), Color.fromRGB(120, 75, 30), Particle.SPORE_BLOSSOM_AIR, 2.6, 3.2, 20, 4);
            }
        }
    }

    private void scheduleSpawnHelix(Location center, Color primary, Color secondary, Particle accent, double radius, double height, int ticks, int arms) {
        for (int tick = 0; tick <= ticks; tick++) {
            int step = tick;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    World world = center.getWorld();
                    if (world == null) {
                        return;
                    }
                    double progress = step / (double) Math.max(1, ticks);
                    double y = 0.15 + height * progress;
                    for (int arm = 0; arm < arms; arm++) {
                        double angle = (progress * Math.PI * 4.0) + ((Math.PI * 2.0 * arm) / arms);
                        double curl = radius * (1.0 - progress * 0.55);
                        Location point = center.clone().add(Math.cos(angle) * curl, y, Math.sin(angle) * curl);
                        Color color = arm % 2 == 0 ? primary : secondary;
                        world.spawnParticle(Particle.DUST, point, 2, 0.02, 0.02, 0.02, 0.0, new Particle.DustOptions(color, 1.25f));
                        if (step % 2 == 0) {
                            spawnRitualParticle(world, accent, point, 1, 0.04, 0.04, 0.04, 0.01, color);
                        }
                    }
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Boss spawn helix particle failed: " + ex.getMessage());
                }
            }, step);
        }
    }

    private ItemStack createOverviewItem() {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Total tracked bosses:</gray> <white>" + trackedBosses.size() + "</white>");
        lore.add("<gray>Left-click a boss to spawn it.</gray>");
        lore.add("<gray>Right-click a boss to despawn every copy of it.</gray>");
        lore.add("<gray>Future boss definitions will appear here automatically.</gray>");
        return menuItem(Material.NETHER_STAR, "<gold><bold>Boss Console</bold></gold>", lore);
    }

    private ItemStack createClearAllItem() {
        return menuItem(
            Material.BARRIER,
            "<red><bold>Delete All Bosses</bold></red>",
            List.of(
                "<gray>Removes every tracked custom boss.</gray>",
                "<gray>This also clears persisted boss records.</gray>"
            )
        );
    }

    private ItemStack createRefreshItem() {
        return menuItem(
            Material.ENDER_EYE,
            "<aqua><bold>Refresh</bold></aqua>",
            List.of(
                "<gray>Rebuild the live boss snapshot</gray>",
                "<gray>and refresh the current counts.</gray>"
            )
        );
    }

    private ItemStack createBossEntryItem(BossType type) {
        List<String> lore = new ArrayList<>(type.description());
        if (!lore.isEmpty()) {
            lore.add("<dark_gray> ");
        }
        lore.add("<gray>Active:</gray> <white>" + activeCount(type.id()) + "</white>");
        lore.add("<gray>Entity:</gray> <white>" + prettyBossName(type.entityType().name()) + "</white>");
        lore.add("<gray>Health:</gray> <white>" + trimNumber(type.maxHealth()) + "</white>");
        String worldRestriction = requiredEnvironmentLabel(type);
        if (worldRestriction != null) {
            lore.add("<gray>World:</gray> <white>" + worldRestriction + "</white>");
        }
        lore.add("<gray>Left-click:</gray> <white>Spawn at your position</white>");
        lore.add("<gray>Right-click:</gray> <white>Despawn all copies</white>");
        lore.add("<dark_gray>Use /bossrituals to view the survival summon ritual.</dark_gray>");
        return menuItem(type.menuIcon(), type.displayName(), lore);
    }

    private ItemStack createRitualEntryItem(BossType type) {
        BossRitual ritual = type.ritual();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + ritual.name() + "</gray>");
        lore.add("<gray>Focus:</gray> <white>" + prettyBossName(ritual.focusBlock().name()) + "</white>");
        lore.add("<gray>Catalyst:</gray> <white>" + prettyBossName(ritual.catalyst().name()) + "</white>");
        String worldRestriction = requiredEnvironmentLabel(type);
        if (worldRestriction != null) {
            lore.add("<gray>World:</gray> <white>" + worldRestriction + "</white>");
        }
        lore.add("<dark_gray> ");
        lore.add("<gold><bold>Steps</bold></gold>");
        lore.addAll(ritual.steps());
        if (ritual.arenaRadius() > 0.0) {
            lore.add("<dark_gray> ");
            lore.add("<red>Summons a live arena boundary.</red>");
        }
        lore.add("<dark_gray> ");
        lore.add("<yellow>North/east/south/west are real Minecraft directions.</yellow>");
        lore.add("<yellow>Use F3 if you need to check which way is north.</yellow>");
        lore.add("<yellow>Right-click the focus block or nearby shrine block with the catalyst.</yellow>");
        return menuItem(ritual.icon(), type.displayName(), lore);
    }

    private ItemStack menuItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(MM.deserialize(name));
        if (!loreLines.isEmpty()) {
            List<Component> lore = new ArrayList<>(loreLines.size());
            for (String line : loreLines) {
                lore.add(MM.deserialize(line));
            }
            meta.lore(lore);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void applyBossState(LivingEntity entity, BossType type) {
        entity.setPersistent(true);
        entity.customName(MM.deserialize(type.displayName()));
        entity.setCustomNameVisible(false);
        entity.setGlowing(type.glowing());

        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(keyBossMarker, PersistentDataType.BYTE, (byte) 1);
        pdc.set(keyBossId, PersistentDataType.STRING, type.id());
        pdc.set(keyBossInstanceId, PersistentDataType.STRING, entity.getUniqueId().toString());
        pdc.set(keyBossPhase, PersistentDataType.INTEGER, 1);
        entity.addScoreboardTag(SCOREBOARD_TAG);
        entity.addScoreboardTag(SCOREBOARD_TAG + ":" + type.id());

        if (entity instanceof Mob mob) {
            mob.setRemoveWhenFarAway(false);
            mob.setCanPickupItems(false);
        }
        clearBossEquipment(entity);

        setAttributeBase(entity, Attribute.MAX_HEALTH, type.maxHealth());
        AttributeInstance healthAttribute = entity.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = healthAttribute == null ? type.maxHealth() : healthAttribute.getValue();
        entity.setHealth(Math.max(1.0, maxHealth));
        setAttributeBase(entity, Attribute.ATTACK_DAMAGE, type.attackDamage());
        setAttributeBase(entity, Attribute.MOVEMENT_SPEED, type.movementSpeed());
        setAttributeBase(entity, Attribute.FOLLOW_RANGE, type.followRange());
        setAttributeBase(entity, Attribute.KNOCKBACK_RESISTANCE, type.knockbackResistance());

        type.configurer().apply(this, entity);
        ensureBossVisuals(entity, type);
    }

    private void setAttributeBase(LivingEntity entity, Attribute attribute, double baseValue) {
        if (baseValue <= 0.0) {
            return;
        }
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(baseValue);
        }
    }

    private boolean isAllowedBossWorld(BossType type, World world) {
        World.Environment required = requiredEnvironment(type);
        return required == null || (world != null && world.getEnvironment() == required);
    }

    private String spawnRestrictionMessage(BossType type, World world) {
        World.Environment required = requiredEnvironment(type);
        if (required == null || (world != null && world.getEnvironment() == required)) {
            return null;
        }
        return "<white>" + type.plainDisplayName() + "</white> can only be spawned in <white>" + environmentDisplayName(required) + "</white>.";
    }

    private World.Environment requiredEnvironment(BossType type) {
        return switch (type) {
            case AURELION_THE_RIFT_SERAPH -> World.Environment.THE_END;
            default -> null;
        };
    }

    private String requiredEnvironmentLabel(BossType type) {
        World.Environment required = requiredEnvironment(type);
        return required == null ? null : environmentDisplayName(required) + " only";
    }

    private String environmentDisplayName(World.Environment environment) {
        return switch (environment) {
            case NORMAL -> "the Overworld";
            case NETHER -> "the Nether";
            case THE_END -> "the End";
            case CUSTOM -> "a custom world";
        };
    }

    private boolean isEnvironmentalCheeseDamage(EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case CONTACT, SUFFOCATION, FALL, FIRE, FIRE_TICK, MELTING, LAVA, DROWNING,
                FALLING_BLOCK, HOT_FLOOR, CAMPFIRE, CRAMMING, DRYOUT, FREEZE -> true;
            default -> false;
        };
    }

    private boolean teleportBoss(LivingEntity boss, Location target) {
        UUID entityId = boss.getUniqueId();
        allowedBossTeleports.add(entityId);
        try {
            return boss.teleport(target);
        } finally {
            allowedBossTeleports.remove(entityId);
        }
    }

    private void updateBossScaling(LivingEntity entity, BossType type) {
        int nearbyPlayers = countScalingPlayers(entity, type);
        int previousPlayers = entity.getPersistentDataContainer().getOrDefault(keyBossScaledPlayerCount, PersistentDataType.INTEGER, -1);
        if (nearbyPlayers == previousPlayers) {
            return;
        }

        int extraPlayers = Math.min(BOSS_SCALE_MAX_EXTRA_PLAYERS, Math.max(0, nearbyPlayers - 1));
        double healthScale = 1.0 + (extraPlayers * BOSS_SCALE_HEALTH_PER_EXTRA_PLAYER);
        double damageScale = 1.0 + (extraPlayers * BOSS_SCALE_DAMAGE_PER_EXTRA_PLAYER);

        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        double oldMax = health == null ? type.maxHealth() : Math.max(1.0, health.getValue());
        double healthRatio = Math.max(0.01, Math.min(1.0, entity.getHealth() / oldMax));
        double scaledMaxHealth = Math.max(type.maxHealth(), type.maxHealth() * healthScale);
        setAttributeBase(entity, Attribute.MAX_HEALTH, scaledMaxHealth);
        AttributeInstance updatedHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        double nextMax = updatedHealth == null ? scaledMaxHealth : Math.max(1.0, updatedHealth.getValue());
        entity.setHealth(Math.max(1.0, Math.min(nextMax, nextMax * healthRatio)));

        setAttributeBase(entity, Attribute.ATTACK_DAMAGE, type.attackDamage() * damageScale);
        entity.getPersistentDataContainer().set(keyBossScaledPlayerCount, PersistentDataType.INTEGER, nearbyPlayers);

        if (nearbyPlayers > 1) {
            entity.getWorld().spawnParticle(Particle.DUST, entity.getLocation().clone().add(0.0, 1.1, 0.0), 18, 0.55, 0.45, 0.55, 0.0, new Particle.DustOptions(type.ritual().color(), 1.0f));
            if (nearbyPlayers > previousPlayers) {
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.65f, 0.85f);
                entity.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, entity.getLocation().clone().add(0.0, 1.2, 0.0), 22, 0.55, 0.55, 0.55, 0.02);
                entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0, false, true, true));
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, false, true, true));
            }
        }
    }

    private int countScalingPlayers(LivingEntity entity, BossType type) {
        double ritualRadius = type.ritual().arenaRadius();
        double radius = Math.max(28.0, ritualRadius > 0.0 ? ritualRadius + 8.0 : 36.0);
        double radiusSquared = radius * radius;
        int count = 0;
        for (Player player : entity.getWorld().getPlayers()) {
            if (!player.isValid() || player.isDead()) {
                continue;
            }
            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            if (player.getLocation().distanceSquared(entity.getLocation()) <= radiusSquared) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    private void clearBossEquipment(LivingEntity entity) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }

        equipment.setHelmet(null);
        equipment.setChestplate(null);
        equipment.setLeggings(null);
        equipment.setBoots(null);
        equipment.setItemInMainHand(null);
        equipment.setItemInOffHand(null);

        equipment.setHelmetDropChance(0.0f);
        equipment.setChestplateDropChance(0.0f);
        equipment.setLeggingsDropChance(0.0f);
        equipment.setBootsDropChance(0.0f);
        equipment.setItemInMainHandDropChance(0.0f);
        equipment.setItemInOffHandDropChance(0.0f);
    }

    private void equipBossArmor(LivingEntity entity, ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }

        equipment.setHelmet(helmet);
        equipment.setChestplate(chestplate);
        equipment.setLeggings(leggings);
        equipment.setBoots(boots);

        equipment.setHelmetDropChance(0.0f);
        equipment.setChestplateDropChance(0.0f);
        equipment.setLeggingsDropChance(0.0f);
        equipment.setBootsDropChance(0.0f);
    }

    private void equipBossHands(LivingEntity entity, ItemStack mainHand, ItemStack offHand) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }

        equipment.setItemInMainHand(mainHand);
        equipment.setItemInOffHand(offHand);
        equipment.setItemInMainHandDropChance(0.0f);
        equipment.setItemInOffHandDropChance(0.0f);
    }

    private void ensureBossVisuals(LivingEntity entity, BossType type) {
        ensureBossBar(entity, type);
        ensureBossHologram(entity, type);
    }

    private void ensureBossBar(LivingEntity entity, BossType type) {
        bossBars.computeIfAbsent(entity.getUniqueId(), uuid -> Bukkit.createBossBar("", BarColor.RED, BarStyle.SEGMENTED_10));
    }

    private void ensureBossHologram(LivingEntity entity, BossType type) {
        UUID hologramId = holograms.get(entity.getUniqueId());
        Entity existing = hologramId == null ? null : Bukkit.getEntity(hologramId);
        if (existing instanceof TextDisplay) {
            VisualRangeUtil.applyHologramRange((TextDisplay) existing);
            return;
        }

        TextDisplay display = entity.getWorld().spawn(entity.getLocation().clone().add(0.0, entity.getHeight() + 0.85, 0.0), TextDisplay.class, textDisplay -> {
            textDisplay.setPersistent(false);
            textDisplay.setGravity(false);
            textDisplay.setBillboard(Display.Billboard.CENTER);
            textDisplay.setSeeThrough(false);
            textDisplay.setShadowed(true);
            textDisplay.setDefaultBackground(false);
            textDisplay.setTextOpacity((byte) 255);
            textDisplay.setTeleportDuration(HOLOGRAM_TELEPORT_DURATION_TICKS);
            textDisplay.setInterpolationDelay(0);
            textDisplay.setInterpolationDuration(HOLOGRAM_TELEPORT_DURATION_TICKS);
            VisualRangeUtil.applyHologramRange(textDisplay);
        });
        holograms.put(entity.getUniqueId(), display.getUniqueId());
    }

    private void updateBossBar(LivingEntity entity, BossType type) {
        BossBar bossBar = bossBars.get(entity.getUniqueId());
        if (bossBar == null) {
            return;
        }

        int phase = bossPhase(entity);
        int scaledPlayers = entity.getPersistentDataContainer().getOrDefault(keyBossScaledPlayerCount, PersistentDataType.INTEGER, 1);
        String rage = scaledPlayers > 1 ? " | Rage +" + Math.min(BOSS_SCALE_MAX_EXTRA_PLAYERS, scaledPlayers - 1) : "";
        bossBar.setTitle(type.plainDisplayName() + " | Phase " + phase + rage);
        double maxHealth = Math.max(1.0, entity.getAttribute(Attribute.MAX_HEALTH) == null ? type.maxHealth() : entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, entity.getHealth() / maxHealth)));
        bossBar.setColor(phase >= 2 ? BarColor.PURPLE : BarColor.RED);
        bossBar.setVisible(true);

        Set<Player> nearby = new HashSet<>();
        for (Player player : entity.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(entity.getLocation()) <= 64 * 64) {
                nearby.add(player);
                if (!bossBar.getPlayers().contains(player)) {
                    bossBar.addPlayer(player);
                }
            }
        }
        for (Player viewer : new ArrayList<>(bossBar.getPlayers())) {
            if (!nearby.contains(viewer)) {
                bossBar.removePlayer(viewer);
            }
        }
    }

    private void updateBossHologram(LivingEntity entity, BossType type) {
        UUID hologramId = holograms.get(entity.getUniqueId());
        Entity existing = hologramId == null ? null : Bukkit.getEntity(hologramId);
        TextDisplay display;
        if (existing instanceof TextDisplay current && current.isValid()) {
            display = current;
            VisualRangeUtil.applyHologramRange(display);
        } else {
            holograms.remove(entity.getUniqueId());
            ensureBossHologram(entity, type);
            hologramId = holograms.get(entity.getUniqueId());
            existing = hologramId == null ? null : Bukkit.getEntity(hologramId);
            if (!(existing instanceof TextDisplay refreshed) || !refreshed.isValid()) {
                return;
            }
            display = refreshed;
        }

        Location target = entity.getLocation().clone().add(0.0, entity.getHeight() + 0.85, 0.0);
        if (display.getWorld() != target.getWorld() || display.getLocation().distanceSquared(target) > 25.0) {
            display.setTeleportDuration(0);
            display.teleport(target);
            display.setTeleportDuration(HOLOGRAM_TELEPORT_DURATION_TICKS);
        } else {
            display.teleport(target);
        }
        int phase = bossPhase(entity);
        double maxHealth = Math.max(1.0, entity.getAttribute(Attribute.MAX_HEALTH) == null ? type.maxHealth() : entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        display.text(MM.deserialize(
            type.displayName()
                + "\n<gray>Phase <white>" + phase + "</white> <dark_gray>|</dark_gray> HP <white>"
                + trimNumber(entity.getHealth()) + "/" + trimNumber(maxHealth) + "</white></gray>"
        ));
    }

    private void destroyBossVisuals(UUID bossId) {
        bossArenas.remove(bossId);
        BossBar bar = bossBars.remove(bossId);
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }
        UUID hologramId = holograms.remove(bossId);
        Entity hologram = hologramId == null ? null : Bukkit.getEntity(hologramId);
        if (hologram != null) {
            hologram.remove();
        }
    }

    private void destroyAllBossVisuals() {
        for (UUID bossId : new HashSet<>(bossBars.keySet())) {
            destroyBossVisuals(bossId);
        }
        for (UUID bossId : new HashSet<>(holograms.keySet())) {
            destroyBossVisuals(bossId);
        }
    }

    private int bossPhase(LivingEntity entity) {
        return entity.getPersistentDataContainer().getOrDefault(keyBossPhase, PersistentDataType.INTEGER, 1);
    }

    private void setBossPhase(LivingEntity entity, int phase) {
        entity.getPersistentDataContainer().set(keyBossPhase, PersistentDataType.INTEGER, Math.max(1, phase));
    }

    private long bossCooldownAt(LivingEntity entity, NamespacedKey key) {
        return entity.getPersistentDataContainer().getOrDefault(key, PersistentDataType.LONG, 0L);
    }

    private boolean bossCooldownReady(LivingEntity entity, NamespacedKey key) {
        return System.currentTimeMillis() >= bossCooldownAt(entity, key);
    }

    private void setBossCooldown(LivingEntity entity, NamespacedKey key, long cooldownMs) {
        long readyAt = Math.max(System.currentTimeMillis(), System.currentTimeMillis() + Math.max(0L, cooldownMs));
        entity.getPersistentDataContainer().set(key, PersistentDataType.LONG, readyAt);
    }

    private LivingEntity currentBossTarget(LivingEntity entity) {
        if (!(entity instanceof Mob mob)) {
            return null;
        }
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isValid() || target.isDead() || target.getWorld() != entity.getWorld()) {
            return null;
        }
        return target;
    }

    private void tickBossBehavior(LivingEntity entity, BossType type) {
        switch (type) {
            case YULE_THE_MINION -> tickYuleTheMinion(entity);
            case KAEL_THE_ASHEN -> tickKaelTheAshen(entity);
            case VESPER_THE_WIDOW_QUEEN -> tickVesperTheWidowQueen(entity);
            case VORALITH_THE_CRIMSON_WARDEN -> tickVoralithTheCrimsonWarden(entity);
            case AURELION_THE_RIFT_SERAPH -> tickAurelionTheRiftSeraph(entity);
            case NEREIDA_THE_ABYSS_MOTHER -> tickNereidaTheAbyssMother(entity);
            case IRON_SAINT -> tickIronSaint(entity);
            case MIREWOOD_THE_ROOT_TYRANT -> tickMirewoodTheRootTyrant(entity);
        }
    }

    private void startBossArena(LivingEntity entity, BossType type) {
        double radius = type.ritual().arenaRadius();
        if (radius <= 0.0) {
            return;
        }
        bossArenas.put(entity.getUniqueId(), new BossArena(entity.getLocation().clone(), radius, type.ritual().color()));
    }

    private void tickBossArena(LivingEntity entity, BossType type) {
        double radius = type.ritual().arenaRadius();
        if (radius <= 0.0) {
            return;
        }
        BossArena arena = bossArenas.computeIfAbsent(
            entity.getUniqueId(),
            ignored -> new BossArena(entity.getLocation().clone(), radius, type.ritual().color())
        );
        if (arena.center().getWorld() == null || entity.getWorld() != arena.center().getWorld()) {
            bossArenas.put(entity.getUniqueId(), new BossArena(entity.getLocation().clone(), radius, type.ritual().color()));
            return;
        }

        drawArenaBoundary(arena);
        enforceArenaBoundary(entity, arena);
        tickArenaHazards(entity, arena);
    }

    private void drawArenaBoundary(BossArena arena) {
        Location center = arena.center();
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        Particle.DustOptions dust = new Particle.DustOptions(arena.color(), 1.1f);
        int points = Math.max(48, (int) Math.round(arena.radius() * 4.0));
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i) / points;
            double x = Math.cos(angle) * arena.radius();
            double z = Math.sin(angle) * arena.radius();
            for (double y = 0.2; y <= 3.2; y += 1.0) {
                world.spawnParticle(Particle.DUST, center.clone().add(x, y, z), 1, 0.0, 0.0, 0.0, 0.0, dust);
            }
        }
    }

    private void enforceArenaBoundary(LivingEntity boss, BossArena arena) {
        Location center = arena.center();
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        double radiusSquared = arena.radius() * arena.radius();
        double warningSquared = (arena.radius() + 3.0) * (arena.radius() + 3.0);
        if (boss.getLocation().distanceSquared(center) > warningSquared) {
            teleportBoss(boss, center);
            world.spawnParticle(Particle.DUST, center.clone().add(0.0, 1.0, 0.0), 28, 0.45, 0.55, 0.45, 0.0, new Particle.DustOptions(arena.color(), 1.2f));
        }

        for (Player player : world.getPlayers()) {
            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            double distanceSquared = player.getLocation().distanceSquared(center);
            if (distanceSquared <= radiusSquared || distanceSquared > warningSquared) {
                continue;
            }

            Vector inward = center.toVector().subtract(player.getLocation().toVector());
            if (inward.lengthSquared() <= 1.0E-6) {
                continue;
            }
            inward.normalize().multiply(0.75);
            inward.setY(Math.max(0.18, player.getVelocity().getY()));
            player.setVelocity(player.getVelocity().add(inward));
            player.sendActionBar(MM.deserialize("<red>The arena refuses to let you leave.</red>"));
            world.spawnParticle(Particle.DUST, player.getLocation().clone().add(0.0, 1.0, 0.0), 8, 0.25, 0.35, 0.25, 0.0, new Particle.DustOptions(arena.color(), 0.9f));
        }
    }

    private void tickArenaHazards(LivingEntity boss, BossArena arena) {
        long now = System.currentTimeMillis();
        long nextHazard = boss.getPersistentDataContainer().getOrDefault(keyBossArenaHazardCooldown, PersistentDataType.LONG, 0L);
        if (nextHazard > now) {
            return;
        }
        boss.getPersistentDataContainer().set(keyBossArenaHazardCooldown, PersistentDataType.LONG, now + 4_000L);

        Location center = arena.center();
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        double innerSafeRadius = Math.max(4.0, arena.radius() - 4.0);
        double innerSafeSquared = innerSafeRadius * innerSafeRadius;
        for (Player player : world.getPlayers()) {
            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            double distanceSquared = player.getLocation().distanceSquared(center);
            if (distanceSquared > arena.radius() * arena.radius()) {
                continue;
            }

            boolean campingRim = distanceSquared > innerSafeSquared;
            boolean verticalCheese = player.getLocation().getY() > center.getY() + 8.0;
            if (!campingRim && !verticalCheese) {
                continue;
            }

            player.damage(2.0, boss);
            player.sendActionBar(MM.deserialize("<red>The arena lashes out at unsafe ground.</red>"));
            world.spawnParticle(Particle.DUST, player.getLocation().clone().add(0.0, 0.8, 0.0), 18, 0.35, 0.45, 0.35, 0.0, new Particle.DustOptions(arena.color(), 1.1f));
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation().clone().add(0.0, 0.8, 0.0), 8, 0.25, 0.35, 0.25, 0.015);
        }
    }

    private void tickBossFailure(BossRecord record, LivingEntity boss, BossType type) {
        BossFightState state = bossFightStates.get(record.entityUuid());
        if (state == null || !state.engaged() || state.finished()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (hasEligiblePlayerInFightArea(boss, type)) {
            state.clearLossCheck();
            return;
        }

        if (state.lossCheckAt() <= 0L) {
            state.scheduleLossCheck(now + BOSS_FAILURE_GRACE_MS);
            return;
        }
        if (now < state.lossCheckAt()) {
            return;
        }

        finishBossFight(record, type, false, false, boss.getLocation());
        announceBossFailure(type, boss.getLocation());
        despawnBossMinions(record.entityUuid());
        destroyBossVisuals(record.entityUuid());
        boss.remove();
        untrackRecord(record.entityUuid());
        plugin.getDatabase().deleteBossRecord(record.entityUuid());
    }

    private boolean hasEligiblePlayerInFightArea(LivingEntity boss, BossType type) {
        if (boss == null || boss.getWorld() == null) {
            return false;
        }
        for (Player player : boss.getWorld().getPlayers()) {
            if (!isFightEligiblePlayer(player)) {
                continue;
            }
            if (isPlayerInFightArea(player, boss, type)) {
                return true;
            }
        }
        return false;
    }

    private boolean isFightEligiblePlayer(Player player) {
        if (player == null || !player.isOnline() || player.isDead() || !player.isValid()) {
            return false;
        }
        GameMode gameMode = player.getGameMode();
        return gameMode != GameMode.CREATIVE && gameMode != GameMode.SPECTATOR;
    }

    private boolean isPlayerInFightArea(Player player, LivingEntity boss, BossType type) {
        if (player == null || boss == null || player.getWorld() != boss.getWorld()) {
            return false;
        }

        BossArena arena = bossArenas.get(boss.getUniqueId());
        Location center = arena == null ? boss.getLocation() : arena.center();
        if (center.getWorld() == null || center.getWorld() != player.getWorld()) {
            return false;
        }
        double radius = arena == null
            ? Math.max(28.0, type == null ? 36.0 : type.ritual().arenaRadius() + 8.0)
            : arena.radius() + 3.0;
        return player.getLocation().distanceSquared(center) <= radius * radius;
    }

    private BossFightState activeFightStateFor(Player player) {
        if (player == null) {
            return null;
        }
        for (BossRecord record : new ArrayList<>(trackedBosses.values())) {
            BossFightState state = bossFightStates.get(record.entityUuid());
            if (state == null || !state.engaged() || state.finished()) {
                continue;
            }
            Entity entity = Bukkit.getEntity(record.entityUuid());
            BossType type = BossType.fromId(record.bossId());
            if (!(entity instanceof LivingEntity boss) || type == null) {
                continue;
            }
            if (state.hasParticipant(player.getUniqueId()) || isPlayerInFightArea(player, boss, type)) {
                return state;
            }
        }
        return null;
    }

    private void markBossFightLossCheck(Player player) {
        long checkAt = System.currentTimeMillis() + BOSS_FAILURE_GRACE_MS;
        for (BossRecord record : new ArrayList<>(trackedBosses.values())) {
            BossFightState state = bossFightStates.get(record.entityUuid());
            if (state == null || !state.engaged() || state.finished()) {
                continue;
            }
            Entity entity = Bukkit.getEntity(record.entityUuid());
            BossType type = BossType.fromId(record.bossId());
            if (!(entity instanceof LivingEntity boss) || type == null) {
                continue;
            }
            if (state.hasParticipant(player.getUniqueId()) || isPlayerInFightArea(player, boss, type)) {
                state.scheduleLossCheck(checkAt);
            }
        }
    }

    private BossFightState fightState(BossRecord record) {
        return bossFightStates.computeIfAbsent(
            record.entityUuid(),
            ignored -> new BossFightState(Math.max(1L, record.spawnedAt()))
        );
    }

    private void recordBossFightEngagement(UUID bossId, Player player) {
        if (bossId == null || player == null) {
            return;
        }
        BossRecord record = trackedBosses.get(bossId);
        BossFightState state = record == null
            ? bossFightStates.computeIfAbsent(bossId, ignored -> new BossFightState(System.currentTimeMillis()))
            : fightState(record);
        state.touch(player);
    }

    private Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private Player topOnlineParticipant(BossFightState state) {
        if (state == null) {
            return null;
        }
        for (BossFightParticipant participant : state.sortedParticipants()) {
            Player player = Bukkit.getPlayer(participant.playerUuid());
            if (player != null && player.isOnline()) {
                return player;
            }
        }
        return null;
    }

    private void finishBossFight(BossRecord record, BossType type, boolean victory, boolean doubleDrops, Location location) {
        BossFightState state = bossFightStates.get(record.entityUuid());
        if (state == null || !state.markFinished()) {
            return;
        }

        long endedAt = System.currentTimeMillis();
        List<BossFightParticipant> participants = state.sortedParticipants();
        broadcastFightLeaderboard(type, victory, participants);
        sendPersonalFightReports(type, victory, participants);
        saveBossFightReport(record, type, victory, doubleDrops, endedAt, participants);
        if (location != null && location.getWorld() != null) {
            location.getWorld().spawnParticle(victory ? Particle.TOTEM_OF_UNDYING : Particle.ASH, location.clone().add(0.0, 1.2, 0.0), 28, 0.55, 0.6, 0.55, 0.02);
        }
    }

    private void saveBossFightReport(BossRecord record, BossType type, boolean victory, boolean doubleDrops, long endedAt, List<BossFightParticipant> participants) {
        if (participants.isEmpty()) {
            return;
        }
        String fightId = UUID.randomUUID().toString();
        long startedAt = bossFightStates.getOrDefault(record.entityUuid(), new BossFightState(record.spawnedAt())).startedAt();
        double totalDamage = participants.stream().mapToDouble(BossFightParticipant::damageDone).sum();
        double totalHealing = participants.stream().mapToDouble(BossFightParticipant::healingReceived).sum();
        DatabaseManager.BossFightRecord fight = new DatabaseManager.BossFightRecord(
            fightId,
            type == null ? record.bossId() : type.id(),
            victory ? "victory" : "failure",
            startedAt,
            endedAt,
            Math.max(0L, endedAt - startedAt),
            doubleDrops,
            totalDamage,
            totalHealing
        );

        List<DatabaseManager.BossFightParticipantRecord> dbParticipants = new ArrayList<>();
        int rank = 1;
        for (BossFightParticipant participant : participants) {
            dbParticipants.add(new DatabaseManager.BossFightParticipantRecord(
                fightId,
                participant.playerUuid(),
                participant.playerName(),
                participant.damageDone(),
                participant.healingReceived(),
                rank
            ));
            if (plugin.getLeaderboardManager() != null) {
                plugin.getLeaderboardManager().recordBossFightParticipant(
                    participant.playerUuid(),
                    participant.playerName(),
                    participant.damageDone()
                );
            }
            rank++;
        }
        plugin.getDatabase().saveBossFightReport(fight, dbParticipants);
    }

    private void broadcastFightLeaderboard(BossType type, boolean victory, List<BossFightParticipant> participants) {
        String bossName = type == null ? "Unknown Boss" : type.plainDisplayName();
        Bukkit.broadcast(MessageUtil.prefixedRaw(
            (victory ? "<gold><bold>Boss Report:</bold></gold> " : "<red><bold>Boss Failed:</bold></red> ")
                + "<white>" + escapeMiniMessage(bossName) + "</white> "
                + (victory ? "<gray>was defeated.</gray>" : "<gray>overwhelmed the arena.</gray>")
        ));

        List<BossFightParticipant> damageParticipants = participants.stream()
            .filter(participant -> participant.damageDone() > 0.0)
            .limit(5)
            .toList();
        if (damageParticipants.isEmpty()) {
            Bukkit.broadcast(MessageUtil.prefixedRaw("<dark_gray>No player damage was recorded for this fight.</dark_gray>"));
            return;
        }

        int rank = 1;
        for (BossFightParticipant participant : damageParticipants) {
            Bukkit.broadcast(MessageUtil.prefixedRaw(
                "<gold>#" + rank + "</gold> <white>" + escapeMiniMessage(participant.playerName()) + "</white>"
                    + " <gray>-</gray> <red>" + trimNumber(participant.damageDone()) + " dmg</red>"
                    + " <dark_gray>|</dark_gray> <green>" + trimNumber(participant.healingReceived()) + " healing received</green>"
            ));
            rank++;
        }
    }

    private void sendPersonalFightReports(BossType type, boolean victory, List<BossFightParticipant> participants) {
        String bossName = type == null ? "Unknown Boss" : type.plainDisplayName();
        int rank = 1;
        for (BossFightParticipant participant : participants) {
            Player player = Bukkit.getPlayer(participant.playerUuid());
            if (player == null || !player.isOnline()) {
                rank++;
                continue;
            }
            player.sendMessage(MessageUtil.prefixedRaw(
                "<gradient:#fb7185:#facc15><bold>After Action:</bold></gradient> <white>"
                    + escapeMiniMessage(bossName) + "</white> <gray>"
                    + (victory ? "victory" : "failure") + "</gray>"
            ));
            player.sendMessage(MessageUtil.prefixedRaw(
                "<gray>Your rank:</gray> <white>#" + rank + "</white>"
                    + " <dark_gray>|</dark_gray> <gray>Damage:</gray> <red>" + trimNumber(participant.damageDone()) + "</red>"
                    + " <dark_gray>|</dark_gray> <gray>Healing received:</gray> <green>" + trimNumber(participant.healingReceived()) + "</green>"
            ));
            rank++;
        }
    }

    private void announceBossFailure(BossType type, Location location) {
        if (type == null) {
            return;
        }
        Bukkit.broadcast(MessageUtil.prefixedRaw(
            "<red><bold>" + type.plainDisplayName() + "</bold></red> <gray>has consumed the arena. The fight is lost.</gray>"
        ));
        World world = location == null ? null : location.getWorld();
        if (world != null) {
            world.playSound(location, Sound.ENTITY_WITHER_DEATH, 1.0f, 0.55f);
            world.spawnParticle(Particle.SOUL, location.clone().add(0.0, 1.2, 0.0), 45, 0.9, 0.7, 0.9, 0.05);
            world.spawnParticle(Particle.SMOKE, location.clone().add(0.0, 1.0, 0.0), 32, 0.7, 0.5, 0.7, 0.04);
        }
    }

    private String escapeMiniMessage(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("<", "\\<");
    }

    private void tickYuleTheMinion(LivingEntity entity) {
        if (bossPhase(entity) >= 2) {
            return;
        }
        double maxHealth = Math.max(1.0, entity.getAttribute(Attribute.MAX_HEALTH) == null ? 1.0 : entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        if (entity.getHealth() > maxHealth * 0.50) {
            return;
        }

        setBossPhase(entity, 2);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0, false, true, true));
        spawnYulePhaseTwoMinions(entity);
        entity.getWorld().spawnParticle(Particle.DUST, entity.getLocation().clone().add(0.0, 1.0, 0.0), 28, 0.35, 0.55, 0.35, 0.0, new Particle.DustOptions(Color.fromRGB(178, 34, 34), 1.2f));
        entity.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, entity.getLocation().clone().add(0.0, 1.1, 0.0), 12, 0.25, 0.40, 0.25, 0.0);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.9f, 0.65f);
    }

    private void spawnYulePhaseTwoMinions(LivingEntity bossEntity) {
        BossRecord record = bossRecord(bossEntity);
        if (record == null || bossEntity.getWorld() == null) {
            return;
        }

        LivingEntity currentTarget = currentBossTarget(bossEntity);
        World world = bossEntity.getWorld();
        Location origin = bossEntity.getLocation();
        double[][] offsets = {
            {2.0, 0.0},
            {-2.0, 0.0},
            {0.0, 2.0}
        };

        for (double[] offset : offsets) {
            Location spawn = origin.clone().add(offset[0], 0.0, offset[1]);
            spawn = findGroundedSpawn(world, spawn);
            Zombie minion = world.spawn(spawn, Zombie.class, zombie -> {
                zombie.setAdult();
                zombie.setPersistent(true);
                zombie.setRemoveWhenFarAway(false);
                zombie.setCanPickupItems(false);
                zombie.setConversionTime(-1);
                zombie.customName(MM.deserialize("<red>Yule's Thrall</red>"));
                zombie.setCustomNameVisible(false);
            });

            clearBossEquipment(minion);
            equipBossArmor(
                minion,
                new ItemStack(Material.GOLDEN_HELMET),
                new ItemStack(Material.GOLDEN_CHESTPLATE),
                new ItemStack(Material.GOLDEN_LEGGINGS),
                new ItemStack(Material.GOLDEN_BOOTS)
            );
            equipBossHands(minion, new ItemStack(Material.GOLDEN_SWORD), null);
            setAttributeBase(minion, Attribute.MAX_HEALTH, 40.0);
            minion.setHealth(40.0);
            setAttributeBase(minion, Attribute.ATTACK_DAMAGE, 8.0);
            setAttributeBase(minion, Attribute.MOVEMENT_SPEED, 0.33);
            setAttributeBase(minion, Attribute.FOLLOW_RANGE, 28.0);
            setAttributeBase(minion, Attribute.KNOCKBACK_RESISTANCE, 0.20);
            markBossMinion(minion, record.entityUuid());
            if (currentTarget != null && currentTarget.isValid() && !currentTarget.isDead()) {
                minion.setTarget(currentTarget);
            }

            Location center = minion.getLocation().clone().add(0.0, 1.0, 0.0);
            world.spawnParticle(Particle.SMOKE, center, 10, 0.25, 0.35, 0.25, 0.02);
            world.spawnParticle(
                Particle.DUST,
                center,
                10,
                0.18,
                0.25,
                0.18,
                0.0,
                new Particle.DustOptions(Color.fromRGB(196, 80, 40), 1.0f)
            );
        }
    }

    private Location findGroundedSpawn(World world, Location preferred) {
        Location candidate = preferred.clone();
        int baseY = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 2, candidate.getBlockY()));
        candidate.setY(baseY);

        for (int offset = 0; offset <= 4; offset++) {
            int[] checks = offset == 0 ? new int[]{0} : new int[]{offset, -offset};
            for (int dy : checks) {
                int y = baseY + dy;
                if (y < world.getMinHeight() || y >= world.getMaxHeight() - 1) {
                    continue;
                }
                Block feet = world.getBlockAt(candidate.getBlockX(), y, candidate.getBlockZ());
                Block head = feet.getRelative(BlockFace.UP);
                Block below = feet.getRelative(BlockFace.DOWN);
                if (!feet.isPassable() || !head.isPassable() || below.isPassable()) {
                    continue;
                }
                return feet.getLocation().add(0.5, 0.0, 0.5);
            }
        }
        return preferred.clone().add(0.0, 0.1, 0.0);
    }

    private void tickKaelTheAshen(LivingEntity entity) {
        double maxHealth = Math.max(1.0, entity.getAttribute(Attribute.MAX_HEALTH) == null ? 1.0 : entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        if (bossPhase(entity) < 2 && entity.getHealth() <= maxHealth * 0.45) {
            setBossPhase(entity, 2);
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, true, true));
            entity.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, entity.getLocation().clone().add(0.0, 1.0, 0.0), 36, 0.35, 0.55, 0.35, 0.02);
            entity.getWorld().spawnParticle(Particle.SMOKE, entity.getLocation().clone().add(0.0, 1.0, 0.0), 18, 0.30, 0.45, 0.30, 0.03);
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.75f, 1.35f);
        }

        LivingEntity target = currentBossTarget(entity);
        if (target == null || !bossCooldownReady(entity, keyBossSecondaryCooldown)) {
            return;
        }
        if (entity.getLocation().distanceSquared(target.getLocation()) > 18 * 18 || !entity.hasLineOfSight(target)) {
            return;
        }

        entity.getWorld().spawnParticle(Particle.SOUL, entity.getEyeLocation(), 10, 0.16, 0.12, 0.16, 0.01);
        entity.getWorld().spawnParticle(Particle.ENCHANT, entity.getEyeLocation(), 12, 0.22, 0.18, 0.22, 0.2);
        setBossCooldown(entity, keyBossSecondaryCooldown, bossPhase(entity) >= 2 ? 2200L : 3600L);
    }

    private void tickVesperTheWidowQueen(LivingEntity entity) {
        double maxHealth = Math.max(1.0, entity.getAttribute(Attribute.MAX_HEALTH) == null ? 1.0 : entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        if (bossPhase(entity) < 2 && entity.getHealth() <= maxHealth * 0.50) {
            setBossPhase(entity, 2);
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, true, true));
            entity.getWorld().spawnParticle(Particle.SQUID_INK, entity.getLocation().clone().add(0.0, 0.8, 0.0), 18, 0.35, 0.28, 0.35, 0.02);
            entity.getWorld().spawnParticle(Particle.DUST, entity.getLocation().clone().add(0.0, 1.0, 0.0), 26, 0.35, 0.45, 0.35, 0.0, new Particle.DustOptions(Color.fromRGB(88, 160, 64), 1.1f));
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 0.9f, 0.6f);
        }

        LivingEntity target = currentBossTarget(entity);
        if (target == null || !entity.isOnGround() || !bossCooldownReady(entity, keyBossPrimaryCooldown)) {
            return;
        }

        double distanceSquared = entity.getLocation().distanceSquared(target.getLocation());
        if (distanceSquared < 3.5 * 3.5 || distanceSquared > 12.0 * 12.0) {
            return;
        }

        Vector leap = target.getEyeLocation().toVector().subtract(entity.getLocation().toVector());
        if (leap.lengthSquared() <= 1.0E-6) {
            return;
        }
        leap.normalize().multiply(bossPhase(entity) >= 2 ? 1.08 : 0.92);
        leap.setY(bossPhase(entity) >= 2 ? 0.52 : 0.42);
        entity.setVelocity(leap);
        entity.getWorld().spawnParticle(Particle.SQUID_INK, entity.getLocation().clone().add(0.0, 0.5, 0.0), 10, 0.24, 0.12, 0.24, 0.01);
        entity.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, entity.getLocation().clone().add(0.0, 0.6, 0.0), 16, 0.26, 0.18, 0.26, 0.02);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_SPIDER_STEP, 1.0f, bossPhase(entity) >= 2 ? 0.6f : 0.8f);
        setBossCooldown(entity, keyBossPrimaryCooldown, bossPhase(entity) >= 2 ? 3000L : 4600L);
    }

    private void tickVoralithTheCrimsonWarden(LivingEntity entity) {
        double maxHealth = Math.max(1.0, entity.getAttribute(Attribute.MAX_HEALTH) == null ? 1.0 : entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        if (bossPhase(entity) < 2 && entity.getHealth() <= maxHealth * 0.45) {
            setBossPhase(entity, 2);
            entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0, false, true, true));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, true, true));
            entity.getWorld().spawnParticle(Particle.SONIC_BOOM, entity.getLocation().clone().add(0.0, 1.35, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
            entity.getWorld().spawnParticle(Particle.SCULK_SOUL, entity.getLocation().clone().add(0.0, 1.0, 0.0), 32, 0.45, 0.65, 0.45, 0.02);
            entity.getWorld().spawnParticle(
                Particle.DUST,
                entity.getLocation().clone().add(0.0, 1.0, 0.0),
                28,
                0.40,
                0.55,
                0.40,
                0.0,
                new Particle.DustOptions(Color.fromRGB(180, 30, 55), 1.2f)
            );
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.6f, 0.72f);
        }

        LivingEntity target = currentBossTarget(entity);
        if (target == null) {
            return;
        }

        double distanceSquared = entity.getLocation().distanceSquared(target.getLocation());
        if (bossCooldownReady(entity, keyBossPrimaryCooldown) && distanceSquared <= 8.0 * 8.0) {
            unleashDominionPulse(entity, bossPhase(entity));
        }
        if (bossPhase(entity) >= 2
            && bossCooldownReady(entity, keyBossSecondaryCooldown)
            && distanceSquared <= 18.0 * 18.0
            && entity.hasLineOfSight(target)) {
            unleashCrimsonResonance(entity, target);
        }
    }

    private void spawnYuleAttackParticles(LivingEntity attacker, LivingEntity target, int phase) {
        World world = target.getWorld();
        Location center = target.getLocation().clone().add(0.0, 1.0, 0.0);
        if (phase >= 2) {
            world.spawnParticle(Particle.DUST, center, 14, 0.28, 0.38, 0.28, 0.0, new Particle.DustOptions(Color.fromRGB(185, 35, 35), 1.05f));
            world.spawnParticle(Particle.CRIT, center, 10, 0.25, 0.35, 0.25, 0.02);
            world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.85f, 0.75f);
        } else {
            world.spawnParticle(Particle.CRIT, center, 8, 0.22, 0.32, 0.22, 0.02);
            world.spawnParticle(Particle.SMOKE, center, 6, 0.18, 0.22, 0.18, 0.01);
            world.playSound(center, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.65f, 1.1f);
        }
    }

    private void applyYulePhaseTwoKnockback(LivingEntity attacker, LivingEntity target) {
        Vector direction = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
        if (direction.lengthSquared() <= 1.0E-6) {
            return;
        }
        Vector knockback = direction.normalize().multiply(0.85);
        knockback.setY(Math.max(0.22, target.getVelocity().getY()));
        target.setVelocity(target.getVelocity().add(knockback));
    }

    private void handleKaelBowShot(LivingEntity shooter, Projectile projectile, int phase) {
        Location eye = shooter.getEyeLocation();
        World world = shooter.getWorld();
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, eye, phase >= 2 ? 14 : 8, 0.10, 0.10, 0.10, 0.01);
        world.spawnParticle(Particle.SMOKE, eye, phase >= 2 ? 8 : 4, 0.08, 0.08, 0.08, 0.01);
        world.playSound(shooter.getLocation(), Sound.ENTITY_SKELETON_SHOOT, 1.0f, phase >= 2 ? 0.7f : 0.95f);

        if (projectile instanceof org.bukkit.entity.AbstractArrow arrow) {
            arrow.setCritical(true);
            arrow.setDamage(arrow.getDamage() + (phase >= 2 ? 3.0 : 1.5));
            if (phase >= 2) {
                arrow.setFireTicks(100);
            } else {
                arrow.setPierceLevel(Math.max(arrow.getPierceLevel(), 1));
            }
        }
    }

    private void handleKaelProjectileHit(LivingEntity attacker, LivingEntity target, int phase, EntityDamageByEntityEvent event) {
        Location center = target.getLocation().clone().add(0.0, 1.0, 0.0);
        World world = target.getWorld();
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, phase >= 2 ? 16 : 10, 0.22, 0.30, 0.22, 0.01);
        world.spawnParticle(Particle.CRIT, center, 8, 0.20, 0.26, 0.20, 0.02);
        if (phase >= 2) {
            target.setFireTicks(Math.max(target.getFireTicks(), 100));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 70, 0, false, true, true));
            event.setDamage(event.getDamage() + 2.0);
            Vector push = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
            if (push.lengthSquared() > 1.0E-6) {
                push.normalize().multiply(0.45).setY(Math.max(0.18, target.getVelocity().getY()));
                target.setVelocity(target.getVelocity().add(push));
            }
        } else {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0, false, true, true));
            event.setDamage(event.getDamage() + 1.0);
        }
    }

    private void handleVesperMeleeHit(LivingEntity attacker, LivingEntity target, int phase) {
        World world = target.getWorld();
        Location center = target.getLocation().clone().add(0.0, 0.9, 0.0);
        world.spawnParticle(Particle.SQUID_INK, center, 10, 0.18, 0.20, 0.18, 0.01);
        world.spawnParticle(Particle.DUST, center, 12, 0.22, 0.25, 0.22, 0.0, new Particle.DustOptions(Color.fromRGB(88, 160, 64), phase >= 2 ? 1.15f : 0.9f));
        world.playSound(center, Sound.ENTITY_SPIDER_HURT, 0.8f, phase >= 2 ? 0.7f : 0.95f);

        if (phase >= 2) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 90, 1, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, false, true, true));
            Vector drag = attacker.getLocation().toVector().subtract(target.getLocation().toVector());
            if (drag.lengthSquared() > 1.0E-6) {
                target.setVelocity(target.getVelocity().add(drag.normalize().multiply(0.25).setY(0.12)));
            }
        } else {
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0, false, true, true));
        }
    }

    private void unleashDominionPulse(LivingEntity entity, int phase) {
        Location center = entity.getLocation().clone().add(0.0, 1.0, 0.0);
        World world = entity.getWorld();
        double radius = phase >= 2 ? 7.0 : 6.0;

        world.spawnParticle(Particle.SONIC_BOOM, center, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticle(Particle.SCULK_SOUL, center, phase >= 2 ? 42 : 26, 0.90, 0.40, 0.90, 0.03);
        world.spawnParticle(
            Particle.DUST,
            center,
            phase >= 2 ? 30 : 18,
            1.10,
            0.45,
            1.10,
            0.0,
            new Particle.DustOptions(Color.fromRGB(190, 25, 45), phase >= 2 ? 1.35f : 1.05f)
        );
        world.playSound(entity.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.7f, phase >= 2 ? 0.72f : 0.9f);

        for (Entity nearby : entity.getNearbyEntities(radius, 2.5, radius)) {
            if (!(nearby instanceof LivingEntity target) || target.equals(entity) || target.isDead() || !target.isValid()) {
                continue;
            }
            if (target instanceof Player player
                && (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR)) {
                continue;
            }

            Vector push = target.getLocation().toVector().subtract(entity.getLocation().toVector());
            if (push.lengthSquared() > 1.0E-6) {
                push.normalize().multiply(phase >= 2 ? 1.05 : 0.65);
                push.setY(phase >= 2 ? 0.30 : 0.20);
                target.setVelocity(target.getVelocity().add(push));
            }
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, phase >= 2 ? 100 : 60, 0, false, true, true));
            target.damage(phase >= 2 ? 8.0 : 5.0, entity);
        }

        setBossCooldown(entity, keyBossPrimaryCooldown, phase >= 2 ? 5200L : 7000L);
    }

    private void unleashCrimsonResonance(LivingEntity entity, LivingEntity target) {
        Location eye = entity.getEyeLocation();
        Vector direction = target.getEyeLocation().toVector().subtract(eye.toVector());
        double length = Math.min(18.0, direction.length());
        if (length <= 0.5) {
            return;
        }

        direction.normalize();
        World world = entity.getWorld();
        for (double step = 0.5; step <= length; step += 0.65) {
            Location point = eye.clone().add(direction.clone().multiply(step));
            world.spawnParticle(Particle.SCULK_SOUL, point, 2, 0.04, 0.04, 0.04, 0.0);
            world.spawnParticle(
                Particle.DUST,
                point,
                1,
                0.0,
                0.0,
                0.0,
                0.0,
                new Particle.DustOptions(Color.fromRGB(220, 40, 65), 1.15f)
            );
        }
        world.spawnParticle(Particle.SONIC_BOOM, target.getLocation().clone().add(0.0, 1.0, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
        world.playSound(entity.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.35f, 0.56f);
        world.playSound(target.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 0.82f);

        target.damage(10.0, entity);
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 120, 0, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, false, true, true));

        Vector shove = target.getLocation().toVector().subtract(entity.getLocation().toVector());
        if (shove.lengthSquared() > 1.0E-6) {
            shove.normalize().multiply(0.70);
            shove.setY(Math.max(0.24, target.getVelocity().getY()));
            target.setVelocity(target.getVelocity().add(shove));
        }

        setBossCooldown(entity, keyBossSecondaryCooldown, 9500L);
    }

    private void handleVoralithMeleeHit(LivingEntity attacker, LivingEntity target, int phase, EntityDamageByEntityEvent event) {
        World world = target.getWorld();
        Location center = target.getLocation().clone().add(0.0, 1.0, 0.0);
        world.spawnParticle(Particle.SCULK_SOUL, center, 16, 0.28, 0.35, 0.28, 0.02);
        world.spawnParticle(
            Particle.DUST,
            center,
            14,
            0.25,
            0.30,
            0.25,
            0.0,
            new Particle.DustOptions(Color.fromRGB(190, 32, 45), phase >= 2 ? 1.2f : 0.95f)
        );
        world.playSound(center, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.0f, phase >= 2 ? 0.7f : 0.88f);

        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, phase >= 2 ? 100 : 60, 0, false, true, true));
        if (phase >= 2) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 0, false, true, true));
            event.setDamage(event.getDamage() + 2.0);
            Vector slam = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
            if (slam.lengthSquared() > 1.0E-6) {
                slam.normalize().multiply(0.85);
                slam.setY(Math.max(0.28, target.getVelocity().getY()));
                target.setVelocity(target.getVelocity().add(slam));
            }
        } else {
            event.setDamage(event.getDamage() + 1.0);
        }
    }

    private void tickAurelionTheRiftSeraph(LivingEntity entity) {
        double maxHealth = Math.max(1.0, entity.getAttribute(Attribute.MAX_HEALTH) == null ? 1.0 : entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        if (bossPhase(entity) < 2 && entity.getHealth() <= maxHealth * 0.50) {
            setBossPhase(entity, 2);
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, true, true));
            entity.getWorld().spawnParticle(Particle.PORTAL, entity.getLocation().clone().add(0.0, 1.0, 0.0), 90, 1.2, 0.9, 1.2, 0.55);
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 1.1f, 0.65f);
        }

        LivingEntity target = currentBossTarget(entity);
        if (target == null || !bossCooldownReady(entity, keyBossSecondaryCooldown)) {
            return;
        }
        double radius = bossPhase(entity) >= 2 ? 9.0 : 7.0;
        if (entity.getLocation().distanceSquared(target.getLocation()) > radius * radius) {
            return;
        }
        Location center = entity.getLocation().clone().add(0.0, 1.0, 0.0);
        World world = entity.getWorld();
        world.spawnParticle(Particle.REVERSE_PORTAL, center, 60, 1.0, 0.45, 1.0, 0.14);
        world.playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.72f);
        for (Entity nearby : entity.getNearbyEntities(radius, 2.8, radius)) {
            if (!(nearby instanceof LivingEntity victim) || victim.equals(entity) || victim.isDead() || !victim.isValid()) {
                continue;
            }
            Vector pull = entity.getLocation().toVector().subtract(victim.getLocation().toVector());
            if (pull.lengthSquared() > 1.0E-6) {
                victim.setVelocity(victim.getVelocity().add(pull.normalize().multiply(bossPhase(entity) >= 2 ? 0.55 : 0.35).setY(0.18)));
            }
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, bossPhase(entity) >= 2 ? 80 : 50, 0, false, true, true));
        }
        setBossCooldown(entity, keyBossSecondaryCooldown, bossPhase(entity) >= 2 ? 5200L : 7200L);
    }

    private void tickNereidaTheAbyssMother(LivingEntity entity) {
        double maxHealth = Math.max(1.0, entity.getAttribute(Attribute.MAX_HEALTH) == null ? 1.0 : entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        if (bossPhase(entity) < 2 && entity.getHealth() <= maxHealth * 0.45) {
            setBossPhase(entity, 2);
            entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, true, true));
            entity.getWorld().spawnParticle(Particle.NAUTILUS, entity.getLocation().clone().add(0.0, 1.0, 0.0), 72, 1.0, 0.7, 1.0, 0.08);
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.82f);
        }
        if ((entity.isInWater() || entity.isInRain()) && entity.getHealth() < maxHealth) {
            entity.setHealth(Math.min(maxHealth, entity.getHealth() + (bossPhase(entity) >= 2 ? 1.2 : 0.6)));
        }
        LivingEntity target = currentBossTarget(entity);
        if (target == null || !bossCooldownReady(entity, keyBossSecondaryCooldown)) {
            return;
        }
        if (entity.getLocation().distanceSquared(target.getLocation()) > 10.0 * 10.0) {
            return;
        }
        unleashAbyssWave(entity);
    }

    private void unleashAbyssWave(LivingEntity entity) {
        int phase = bossPhase(entity);
        double radius = phase >= 2 ? 8.0 : 6.0;
        Location center = entity.getLocation().clone().add(0.0, 0.8, 0.0);
        World world = entity.getWorld();
        world.spawnParticle(Particle.SPLASH, center, 80, radius * 0.25, 0.45, radius * 0.25, 0.08);
        world.spawnParticle(Particle.NAUTILUS, center, 36, radius * 0.18, 0.35, radius * 0.18, 0.04);
        world.playSound(center, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.1f, 0.72f);
        for (Entity nearby : entity.getNearbyEntities(radius, 2.5, radius)) {
            if (!(nearby instanceof LivingEntity target) || target.equals(entity) || target.isDead()) {
                continue;
            }
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, phase >= 2 ? 100 : 70, 1, false, true, true));
            target.damage(phase >= 2 ? 7.0 : 4.0, entity);
            Vector away = target.getLocation().toVector().subtract(entity.getLocation().toVector());
            if (away.lengthSquared() > 1.0E-6) {
                target.setVelocity(target.getVelocity().add(away.normalize().multiply(0.45).setY(0.16)));
            }
        }
        setBossCooldown(entity, keyBossSecondaryCooldown, phase >= 2 ? 6200L : 8200L);
    }

    private void tickIronSaint(LivingEntity entity) {
        double maxHealth = Math.max(1.0, entity.getAttribute(Attribute.MAX_HEALTH) == null ? 1.0 : entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        if (bossPhase(entity) < 2 && entity.getHealth() <= maxHealth * 0.50) {
            setBossPhase(entity, 2);
            entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0, false, true, true));
            entity.getWorld().spawnParticle(Particle.CRIT, entity.getLocation().clone().add(0.0, 1.2, 0.0), 54, 0.8, 0.6, 0.8, 0.08);
            entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.4f, 0.6f);
        }
        LivingEntity target = currentBossTarget(entity);
        if (target == null || !bossCooldownReady(entity, keyBossSecondaryCooldown)) {
            return;
        }
        if (entity.getLocation().distanceSquared(target.getLocation()) <= 7.0 * 7.0) {
            unleashIronSlam(entity);
        }
    }

    private void unleashIronSlam(LivingEntity entity) {
        int phase = bossPhase(entity);
        double radius = phase >= 2 ? 7.0 : 5.0;
        Location center = entity.getLocation();
        World world = entity.getWorld();
        world.spawnParticle(Particle.BLOCK, center.clone().add(0.0, 0.25, 0.0), 55, 1.2, 0.25, 1.2, 0.08, Material.IRON_BLOCK.createBlockData());
        world.spawnParticle(Particle.CRIT, center.clone().add(0.0, 1.0, 0.0), 32, 0.85, 0.35, 0.85, 0.06);
        world.playSound(center, Sound.BLOCK_ANVIL_LAND, 1.35f, 0.52f);
        for (Entity nearby : entity.getNearbyEntities(radius, 2.4, radius)) {
            if (!(nearby instanceof LivingEntity target) || target.equals(entity) || target.isDead()) {
                continue;
            }
            target.damage(phase >= 2 ? 8.0 : 5.0, entity);
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, phase >= 2 ? 100 : 60, 0, false, true, true));
            Vector away = target.getLocation().toVector().subtract(entity.getLocation().toVector());
            if (away.lengthSquared() > 1.0E-6) {
                target.setVelocity(target.getVelocity().add(away.normalize().multiply(phase >= 2 ? 0.95 : 0.65).setY(0.30)));
            }
        }
        setBossCooldown(entity, keyBossSecondaryCooldown, phase >= 2 ? 6800L : 9000L);
    }

    private void tickMirewoodTheRootTyrant(LivingEntity entity) {
        double maxHealth = Math.max(1.0, entity.getAttribute(Attribute.MAX_HEALTH) == null ? 1.0 : entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        if (bossPhase(entity) < 2 && entity.getHealth() <= maxHealth * 0.50) {
            setBossPhase(entity, 2);
            entity.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0, false, true, true));
            entity.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, entity.getLocation().clone().add(0.0, 1.0, 0.0), 80, 1.0, 0.7, 1.0, 0.07);
            entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_ROOTED_DIRT_PLACE, 1.1f, 0.55f);
        }
        LivingEntity target = currentBossTarget(entity);
        if (target == null || !bossCooldownReady(entity, keyBossSecondaryCooldown)) {
            return;
        }
        if (entity.getLocation().distanceSquared(target.getLocation()) <= 9.0 * 9.0) {
            unleashRootSnare(entity);
        }
    }

    private void unleashRootSnare(LivingEntity entity) {
        int phase = bossPhase(entity);
        double radius = phase >= 2 ? 8.0 : 6.0;
        Location center = entity.getLocation().clone().add(0.0, 0.7, 0.0);
        World world = entity.getWorld();
        world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, center, 56, 1.1, 0.35, 1.1, 0.04);
        world.spawnParticle(Particle.HAPPY_VILLAGER, center, 16, 0.75, 0.28, 0.75, 0.02);
        world.playSound(center, Sound.BLOCK_ROOTED_DIRT_BREAK, 1.0f, 0.75f);
        for (Entity nearby : entity.getNearbyEntities(radius, 2.5, radius)) {
            if (!(nearby instanceof LivingEntity target) || target.equals(entity) || target.isDead()) {
                continue;
            }
            target.damage(phase >= 2 ? 6.0 : 3.5, entity);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, phase >= 2 ? 120 : 80, 1, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, phase >= 2 ? 80 : 50, 0, false, true, true));
        }
        setBossCooldown(entity, keyBossSecondaryCooldown, phase >= 2 ? 6200L : 8500L);
    }

    private void handleAurelionMeleeHit(LivingEntity attacker, LivingEntity target, int phase, EntityDamageByEntityEvent event) {
        event.setDamage(event.getDamage() + (phase >= 2 ? 2.0 : 1.0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, phase >= 2 ? 70 : 45, 0, false, true, true));
        target.getWorld().spawnParticle(Particle.PORTAL, target.getLocation().clone().add(0.0, 1.0, 0.0), 24, 0.35, 0.35, 0.35, 0.25);
    }

    private void handleNereidaMeleeHit(LivingEntity attacker, LivingEntity target, int phase, EntityDamageByEntityEvent event) {
        event.setDamage(event.getDamage() + (phase >= 2 ? 2.0 : 1.0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, phase >= 2 ? 80 : 50, 0, false, true, true));
        target.getWorld().spawnParticle(Particle.SPLASH, target.getLocation().clone().add(0.0, 1.0, 0.0), 18, 0.30, 0.35, 0.30, 0.05);
    }

    private void handleIronSaintMeleeHit(LivingEntity attacker, LivingEntity target, int phase, EntityDamageByEntityEvent event) {
        event.setDamage(event.getDamage() + (phase >= 2 ? 3.0 : 1.5));
        Vector away = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
        if (away.lengthSquared() > 1.0E-6) {
            target.setVelocity(target.getVelocity().add(away.normalize().multiply(phase >= 2 ? 0.75 : 0.45).setY(0.25)));
        }
        target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().clone().add(0.0, 1.0, 0.0), 18, 0.28, 0.34, 0.28, 0.06);
    }

    private void handleMirewoodMeleeHit(LivingEntity attacker, LivingEntity target, int phase, EntityDamageByEntityEvent event) {
        event.setDamage(event.getDamage() + (phase >= 2 ? 2.0 : 1.0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, phase >= 2 ? 80 : 45, 0, false, true, true));
        if (phase >= 2) {
            double maxHealth = attacker.getAttribute(Attribute.MAX_HEALTH) == null
                ? attacker.getHealth()
                : attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
            attacker.setHealth(Math.min(maxHealth, attacker.getHealth() + 2.0));
        }
        target.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, target.getLocation().clone().add(0.0, 1.0, 0.0), 18, 0.28, 0.34, 0.28, 0.04);
    }

    private Location findBossSpawnLocation(Player player, BossType type) {
        Block targetBlock = player.getTargetBlockExact(16);
        Location seed = targetBlock != null
            ? targetBlock.getLocation().clone().add(0.5, 1.0, 0.5)
            : player.getLocation().clone();
        Location safe = findSafeBossSpawnLocation(seed, type);
        if (safe != null) {
            return safe;
        }
        return findSafeBossSpawnLocation(player.getLocation().clone(), type);
    }

    private Location findSafeBossSpawnLocation(Location seed, BossType type) {
        World world = seed.getWorld();
        if (world == null) {
            return null;
        }

        int baseX = seed.getBlockX();
        int baseY = Math.max(world.getMinHeight() + 1, Math.min(seed.getBlockY(), world.getMaxHeight() - type.requiredAirBlocks() - 1));
        int baseZ = seed.getBlockZ();

        for (int radius = 0; radius <= 2; radius++) {
            for (int dy = -1; dy <= 3; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                            continue;
                        }
                        Location candidate = new Location(world, baseX + dx + 0.5, baseY + dy, baseZ + dz + 0.5, seed.getYaw(), seed.getPitch());
                        if (isSafeBossSpawnLocation(candidate, type.requiredAirBlocks())) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isSafeBossSpawnLocation(Location location, int requiredAirBlocks) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        int blockY = location.getBlockY();
        if (blockY <= world.getMinHeight() || blockY + requiredAirBlocks >= world.getMaxHeight()) {
            return false;
        }

        Block floor = location.getBlock().getRelative(BlockFace.DOWN);
        if (floor.isPassable() || floor.isLiquid() || floor.getType() == Material.LAVA || floor.getType() == Material.MAGMA_BLOCK) {
            return false;
        }

        Block current = location.getBlock();
        for (int i = 0; i < requiredAirBlocks; i++) {
            if (!current.isPassable() || current.isLiquid()) {
                return false;
            }
            current = current.getRelative(BlockFace.UP);
        }
        return true;
    }

    private int despawnBossRecords(java.util.function.Predicate<BossRecord> predicate) {
        reconcileLoadedBosses();

        Set<UUID> removedIds = new HashSet<>();
        int removed = 0;
        List<BossRecord> snapshot = new ArrayList<>(trackedBosses.values());
        snapshot.sort(Comparator.comparing(BossRecord::spawnedAt));
        for (BossRecord record : snapshot) {
            if (!predicate.test(record)) {
                continue;
            }
            removed += removeBossRecord(record, removedIds) ? 1 : 0;
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                BossRecord record = bossRecord(entity);
                if (record == null || !predicate.test(record) || !removedIds.add(record.entityUuid())) {
                    continue;
                }
                destroyBossVisuals(record.entityUuid());
                entity.remove();
                removed++;
                untrackRecord(record.entityUuid());
                plugin.getDatabase().deleteBossRecord(record.entityUuid());
            }
        }

        return removed;
    }

    private boolean removeBossRecord(BossRecord record, Set<UUID> removedIds) {
        if (record == null || !removedIds.add(record.entityUuid())) {
            return false;
        }

        World world = Bukkit.getWorld(record.world());
        if (world != null) {
            world.getChunkAt(record.chunkX(), record.chunkZ());
        }

        Entity entity = Bukkit.getEntity(record.entityUuid());
        if (entity != null && entity.isValid()) {
            entity.remove();
        }

        despawnBossMinions(record.entityUuid());
        destroyBossVisuals(record.entityUuid());
        untrackRecord(record.entityUuid());
        plugin.getDatabase().deleteBossRecord(record.entityUuid());
        return true;
    }

    private void reconcileLoadedBosses() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                reconcileChunk(chunk);
            }
        }
    }

    private void reconcileChunk(Chunk chunk) {
        cleanupOrphanBossMinions(chunk);
        Map<UUID, Entity> foundEntities = new HashMap<>();
        for (Entity entity : chunk.getEntities()) {
            BossRecord record = bossRecord(entity);
            if (record == null) {
                continue;
            }
            foundEntities.put(record.entityUuid(), entity);
            BossRecord existing = trackedBosses.get(record.entityUuid());
            if (existing == null || !sameRecord(existing, record)) {
                trackRecord(record);
                plugin.getDatabase().saveBossRecord(record);
            }
        }

        List<BossRecord> snapshot = new ArrayList<>(trackedBosses.values());
        for (BossRecord record : snapshot) {
            if (!record.world().equals(chunk.getWorld().getName())
                || record.chunkX() != chunk.getX()
                || record.chunkZ() != chunk.getZ()) {
                continue;
            }
            if (foundEntities.containsKey(record.entityUuid())) {
                continue;
            }
            destroyBossVisuals(record.entityUuid());
            untrackRecord(record.entityUuid());
            plugin.getDatabase().deleteBossRecord(record.entityUuid());
        }
    }

    private void markBossMinion(LivingEntity entity, UUID ownerBossId) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(keyBossMinionMarker, PersistentDataType.BYTE, (byte) 1);
        pdc.set(keyBossMinionOwner, PersistentDataType.STRING, ownerBossId.toString());
        entity.addScoreboardTag(SCOREBOARD_TAG + "_minion");
    }

    private boolean isBossMinion(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(keyBossMinionMarker, PersistentDataType.BYTE);
    }

    private UUID bossMinionOwner(Entity entity) {
        if (!isBossMinion(entity)) {
            return null;
        }
        String raw = entity.getPersistentDataContainer().get(keyBossMinionOwner, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void despawnBossMinions(UUID ownerBossId) {
        if (ownerBossId == null) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!isBossMinion(entity) || !ownerBossId.equals(bossMinionOwner(entity))) {
                    continue;
                }
                entity.remove();
            }
        }
    }

    private void cleanupOrphanBossMinions(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            UUID ownerBossId = bossMinionOwner(entity);
            if (ownerBossId == null) {
                continue;
            }
            Entity owner = Bukkit.getEntity(ownerBossId);
            if (owner instanceof LivingEntity living && living.isValid() && !living.isDead() && trackedBosses.containsKey(ownerBossId)) {
                continue;
            }
            entity.remove();
        }
    }

    private boolean sameRecord(BossRecord left, BossRecord right) {
        return left.entityUuid().equals(right.entityUuid())
            && left.bossId().equals(right.bossId())
            && left.world().equals(right.world())
            && left.chunkX() == right.chunkX()
            && left.chunkZ() == right.chunkZ();
    }

    private BossRecord bossRecord(Entity entity) {
        if (entity == null) {
            return null;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (!pdc.has(keyBossMarker, PersistentDataType.BYTE)) {
            return null;
        }
        String bossId = pdc.get(keyBossId, PersistentDataType.STRING);
        String instanceId = pdc.get(keyBossInstanceId, PersistentDataType.STRING);
        if (bossId == null || bossId.isBlank() || instanceId == null || instanceId.isBlank()) {
            return null;
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(instanceId);
        } catch (IllegalArgumentException ex) {
            return null;
        }

        Location location = entity.getLocation();
        return new BossRecord(
            uuid,
            bossId.trim().toLowerCase(Locale.ROOT),
            location.getWorld().getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            entity.getChunk().getX(),
            entity.getChunk().getZ(),
            System.currentTimeMillis()
        );
    }

    private void trackRecord(BossRecord record) {
        trackedBosses.put(record.entityUuid(), record);
        trackedByBossId.computeIfAbsent(record.bossId(), key -> ConcurrentHashMap.newKeySet()).add(record.entityUuid());
        bossFightStates.computeIfAbsent(record.entityUuid(), ignored -> new BossFightState(Math.max(1L, record.spawnedAt())));
    }

    private void untrackRecord(UUID entityUuid) {
        BossRecord removed = trackedBosses.remove(entityUuid);
        bossFightStates.remove(entityUuid);
        if (removed == null) {
            return;
        }
        Set<UUID> bossSet = trackedByBossId.get(removed.bossId());
        if (bossSet != null) {
            bossSet.remove(entityUuid);
            if (bossSet.isEmpty()) {
                trackedByBossId.remove(removed.bossId());
            }
        }
    }

    private int activeCount(String bossId) {
        String normalized = normalizeBossId(bossId);
        if (normalized == null) {
            return 0;
        }
        Set<UUID> ids = trackedByBossId.getOrDefault(normalized, Collections.emptySet());
        return ids.size();
    }

    private String trimNumber(double value) {
        if (Math.rint(value) == value) {
            return Integer.toString((int) value);
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private String prettyBossName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Unknown";
        }
        String[] parts = raw.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            out.append(part.substring(1));
        }
        return out.toString();
    }

    public record BossActionResult(boolean success, String message) {
    }

    public record BossRitual(
        String name,
        Material focusBlock,
        Material catalyst,
        Material icon,
        List<String> steps,
        long warmupTicks,
        double arenaRadius,
        Color color,
        Particle primaryParticle,
        Sound startSound,
        Sound pulseSound,
        Sound arrivalSound
    ) {
        public BossRitual {
            steps = List.copyOf(steps);
            warmupTicks = Math.max(20L, warmupTicks);
            arenaRadius = Math.max(0.0, arenaRadius);
        }
    }

    private record BossArena(Location center, double radius, Color color) {
    }

    private record BossDoubleDropWindow(
        boolean enabled,
        boolean active,
        boolean allDay,
        ZonedDateTime now,
        ZonedDateTime start,
        ZonedDateTime warningAt,
        ZonedDateTime end
    ) {
        private long startMillis() {
            return start.toInstant().toEpochMilli();
        }
    }

    private static final class BossFightState {
        private final long startedAt;
        private final Map<UUID, BossFightParticipant> participants = new LinkedHashMap<>();
        private long lossCheckAt;
        private boolean finished;

        private BossFightState(long startedAt) {
            this.startedAt = Math.max(1L, startedAt);
        }

        private long startedAt() {
            return startedAt;
        }

        private boolean engaged() {
            return !participants.isEmpty();
        }

        private boolean finished() {
            return finished;
        }

        private boolean markFinished() {
            if (finished) {
                return false;
            }
            finished = true;
            return true;
        }

        private long lossCheckAt() {
            return lossCheckAt;
        }

        private void scheduleLossCheck(long lossCheckAt) {
            this.lossCheckAt = Math.max(this.lossCheckAt, lossCheckAt);
        }

        private void clearLossCheck() {
            lossCheckAt = 0L;
        }

        private boolean hasParticipant(UUID playerUuid) {
            return participants.containsKey(playerUuid);
        }

        private void touch(Player player) {
            participant(player);
        }

        private void addDamage(Player player, double amount) {
            participant(player).addDamage(amount);
            lossCheckAt = 0L;
        }

        private void addHealing(Player player, double amount) {
            participant(player).addHealing(amount);
            lossCheckAt = 0L;
        }

        private BossFightParticipant participant(Player player) {
            return participants.compute(player.getUniqueId(), (uuid, existing) -> {
                if (existing == null) {
                    return new BossFightParticipant(player.getUniqueId(), player.getName());
                }
                existing.updateName(player.getName());
                return existing;
            });
        }

        private List<BossFightParticipant> sortedParticipants() {
            return participants.values().stream()
                .sorted(Comparator
                    .comparingDouble(BossFightParticipant::damageDone).reversed()
                    .thenComparing(BossFightParticipant::playerName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        }
    }

    private static final class BossFightParticipant {
        private final UUID playerUuid;
        private String playerName;
        private double damageDone;
        private double healingReceived;

        private BossFightParticipant(UUID playerUuid, String playerName) {
            this.playerUuid = playerUuid;
            this.playerName = playerName == null || playerName.isBlank() ? "Unknown" : playerName;
        }

        private UUID playerUuid() {
            return playerUuid;
        }

        private String playerName() {
            return playerName == null || playerName.isBlank() ? "Unknown" : playerName;
        }

        private double damageDone() {
            return damageDone;
        }

        private double healingReceived() {
            return healingReceived;
        }

        private void updateName(String playerName) {
            if (playerName != null && !playerName.isBlank()) {
                this.playerName = playerName;
            }
        }

        private void addDamage(double amount) {
            damageDone += Math.max(0.0, amount);
        }

        private void addHealing(double amount) {
            healingReceived += Math.max(0.0, amount);
        }
    }

    private record RitualMatch(BossType type, Block focus) {
    }

    @FunctionalInterface
    public interface BossConfigurer {
        void apply(BossManager manager, LivingEntity entity);
    }

    // Add future bosses here. The boss GUI and boss commands populate from this enum automatically.
    public enum BossType {
        YULE_THE_MINION(
            "yule_the_minion",
            EntityType.ZOMBIE,
            Material.ZOMBIE_HEAD,
            "<gradient:#d97706:#ef4444><bold>Yule the Minion</bold></gradient>",
            300.0,
            14.0,
            0.33,
            40.0,
            0.35,
            2,
            false,
            List.of(
                "<gray>A disciplined undead bruiser that stays dangerous into late gear.</gray>",
                "<gray>Phase One:</gray> <white>fast melee pressure with steady damage</white>",
                "<gray>Phase Two:</gray> <white>awakens with Strength and much heavier knockback</white>",
                "<gray>Every custom boss gets a boss bar and live hologram automatically.</gray>"
            ),
            new BossRitual(
                "Gilded Muster",
                Material.BELL,
                Material.GOLDEN_SWORD,
                Material.GOLDEN_SWORD,
                List.of(
                    "<gold><bold>Build Guide</bold></gold>",
                    "<gray>1. Put <white>Soul Sand</white> on the ground.</gray>",
                    "<gray>2. Place the <white>Bell</white> directly on top of the Soul Sand.</gray>",
                    "<gray>3. Put <white>Gold Blocks</white> touching the Soul Sand on north, south, east, and west.</gray>",
                    "<gray>4. Hold a <white>Golden Sword</white> and right-click the Bell or any shrine block.</gray>",
                    "<dark_gray>Top-down base layer: Gold / Gold + Soul Sand + Gold / Gold.</dark_gray>"
                ),
                48L,
                0.0,
                Color.fromRGB(225, 92, 28),
                Particle.ANGRY_VILLAGER,
                Sound.BLOCK_BELL_USE,
                Sound.BLOCK_NOTE_BLOCK_CHIME,
                Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED
            ),
            (manager, entity) -> {
                if (entity instanceof Zombie zombie) {
                    zombie.setConversionTime(-1);
                }
                manager.equipBossArmor(
                    entity,
                    new ItemStack(Material.GOLDEN_HELMET),
                    new ItemStack(Material.GOLDEN_CHESTPLATE),
                    new ItemStack(Material.GOLDEN_LEGGINGS),
                    new ItemStack(Material.GOLDEN_BOOTS)
                );
            }
        ),
        KAEL_THE_ASHEN(
            "kael_the_ashen",
            EntityType.SKELETON,
            Material.BOW,
            "<gradient:#94a3b8:#e2e8f0><bold>Kael the Ashen</bold></gradient>",
            240.0,
            8.0,
            0.29,
            48.0,
            0.20,
            2,
            false,
            List.of(
                "<gray>An ash-marked marksman that punishes space and line of sight.</gray>",
                "<gray>Phase One:</gray> <white>piercing arrows that sap your strength</white>",
                "<gray>Phase Two:</gray> <white>faster pace, burning arrows, and stronger control</white>",
                "<gray>Stay mobile or he will pin fights down from range.</gray>"
            ),
            new BossRitual(
                "Ashen Wake",
                Material.SOUL_CAMPFIRE,
                Material.BOW,
                Material.SOUL_CAMPFIRE,
                List.of(
                    "<gold><bold>Build Guide</bold></gold>",
                    "<gray>1. Put a <white>Bone Block</white> on the ground.</gray>",
                    "<gray>2. Place a <white>Soul Campfire</white> directly on top of that Bone Block.</gray>",
                    "<gray>3. Put more <white>Bone Blocks</white> touching the center Bone Block on north, south, east, and west.</gray>",
                    "<gray>4. Hold a <white>Bow</white> and right-click the Soul Campfire or any shrine block.</gray>",
                    "<dark_gray>Top-down base layer is a plus sign made of 5 Bone Blocks.</dark_gray>"
                ),
                56L,
                0.0,
                Color.fromRGB(148, 163, 184),
                Particle.SOUL_FIRE_FLAME,
                Sound.BLOCK_SOUL_SAND_STEP,
                Sound.ENTITY_SKELETON_SHOOT,
                Sound.ENTITY_WITHER_SPAWN
            ),
            (manager, entity) -> {
                if (entity instanceof Skeleton skeleton) {
                    skeleton.setConversionTime(-1);
                }
                manager.equipBossArmor(
                    entity,
                    new ItemStack(Material.CHAINMAIL_HELMET),
                    new ItemStack(Material.CHAINMAIL_CHESTPLATE),
                    new ItemStack(Material.CHAINMAIL_LEGGINGS),
                    new ItemStack(Material.CHAINMAIL_BOOTS)
                );
                manager.equipBossHands(entity, new ItemStack(Material.BOW), null);
            }
        ),
        VESPER_THE_WIDOW_QUEEN(
            "vesper_the_widow_queen",
            EntityType.SPIDER,
            Material.COBWEB,
            "<gradient:#22c55e:#84cc16><bold>Vesper the Widow Queen</bold></gradient>",
            280.0,
            11.0,
            0.38,
            36.0,
            0.45,
            2,
            false,
            List.of(
                "<gray>A relentless predator that dives through gaps and mauls stragglers.</gray>",
                "<gray>Phase One:</gray> <white>venom bites and measured leap pressure</white>",
                "<gray>Phase Two:</gray> <white>faster leaps, harsher poison, and a dragging strike</white>",
                "<gray>Do not let her control the gap or the fight snowballs fast.</gray>"
            ),
            new BossRitual(
                "Widow's Bloom",
                Material.COBWEB,
                Material.FERMENTED_SPIDER_EYE,
                Material.COBWEB,
                List.of(
                    "<gold><bold>Build Guide</bold></gold>",
                    "<gray>1. Put a <white>Moss Block</white> on the ground.</gray>",
                    "<gray>2. Place a <white>Cobweb</white> directly on top of the Moss Block.</gray>",
                    "<gray>3. Put <white>Black Candles</white> on the same height as the Cobweb, touching it north, south, east, and west.</gray>",
                    "<gray>4. Hold a <white>Fermented Spider Eye</white> and right-click the Cobweb or any shrine block.</gray>",
                    "<dark_gray>Top-down top layer is candles around the Cobweb.</dark_gray>"
                ),
                60L,
                0.0,
                Color.fromRGB(80, 190, 88),
                Particle.SPORE_BLOSSOM_AIR,
                Sound.ENTITY_SPIDER_AMBIENT,
                Sound.BLOCK_NOTE_BLOCK_CHIME,
                Sound.ENTITY_SPIDER_DEATH
            ),
            (manager, entity) -> { }
        ),
        VORALITH_THE_CRIMSON_WARDEN(
            "voralith_the_crimson_warden",
            EntityType.WARDEN,
            Material.SCULK_SHRIEKER,
            "<gradient:#991b1b:#ef4444><bold>Voralith the Crimson Warden</bold></gradient>",
            650.0,
            16.0,
            0.31,
            48.0,
            0.90,
            4,
            false,
            List.of(
                "<gray>A deep-dark tyrant that mixes Warden pressure with crimson shockwaves.</gray>",
                "<gray>Phase One:</gray> <white>dominion pulses, darkness, and bruising melee hits</white>",
                "<gray>Phase Two:</gray> <white>resonance blasts, harder slams, and much heavier punishment</white>",
                "<gray>Drops a <white>Dominion Core</white> used to repair <white>Crimson Dominion</white>.</gray>"
            ),
            new BossRitual(
                "Crimson Dominion Gate",
                Material.SCULK_SHRIEKER,
                Material.ECHO_SHARD,
                Material.SCULK_SHRIEKER,
                List.of(
                    "<gold><bold>Build Guide</bold></gold>",
                    "<gray>1. Put <white>Reinforced Deepslate</white> on the ground.</gray>",
                    "<gray>2. Place a <white>Sculk Shrieker</white> directly on top of the Reinforced Deepslate.</gray>",
                    "<gray>3. Put <white>Sculk Catalysts</white> touching the Shrieker on the north and south sides.</gray>",
                    "<gray>4. Put <white>Redstone Blocks</white> touching the Shrieker on the east and west sides.</gray>",
                    "<gray>5. Put <white>Soul Lanterns</white> on all four diagonal corners from the Shrieker.</gray>",
                    "<gray>6. Hold an <white>Echo Shard</white> and right-click the Shrieker or any shrine block.</gray>",
                    "<dark_gray>Top layer: Lantern / Catalyst / Lantern</dark_gray>",
                    "<dark_gray>           Redstone / Shrieker / Redstone</dark_gray>",
                    "<dark_gray>           Lantern / Catalyst / Lantern</dark_gray>",
                    "<dark_gray>The deepslate is hidden directly underneath the Shrieker.</dark_gray>"
                ),
                80L,
                18.0,
                Color.fromRGB(205, 30, 55),
                Particle.SCULK_SOUL,
                Sound.BLOCK_SCULK_SHRIEKER_SHRIEK,
                Sound.ENTITY_WARDEN_HEARTBEAT,
                Sound.ENTITY_WARDEN_EMERGE
            ),
            (manager, entity) -> {
                if (entity instanceof Warden warden) {
                    warden.setCanPickupItems(false);
                }
            }
        ),
        AURELION_THE_RIFT_SERAPH(
            "aurelion_the_rift_seraph",
            EntityType.ENDERMAN,
            Material.ENDER_EYE,
            "<gradient:#8b5cf6:#f0abfc><bold>Aurelion the Rift Seraph</bold></gradient>",
            420.0,
            13.0,
            0.36,
            52.0,
            0.35,
            3,
            true,
            List.of(
                "<gray>A void-touched seraph that bends distance into a weapon.</gray>",
                "<gray>Phase One:</gray> <white>teleports, slows, and pulls players through unstable rifts</white>",
                "<gray>Phase Two:</gray> <white>faster rift pulses and heavier displacement</white>",
                "<gray>Drops Rift Lenses, rare Void Halos, and can drop an Awakening Table.</gray>"
            ),
            new BossRitual(
                "Rift Coronation",
                Material.END_ROD,
                Material.ENDER_EYE,
                Material.END_ROD,
                List.of(
                    "<gold><bold>Build Guide</bold></gold>",
                    "<gray>Only works in <white>the End</white>. The rift will not answer anywhere else.</gray>",
                    "<gray>1. Put a <white>Purpur Block</white> on the ground.</gray>",
                    "<gray>2. Place an <white>End Rod</white> directly on top of the Purpur Block.</gray>",
                    "<gray>3. Put <white>End Stone Bricks</white> touching the Purpur Block north, south, east, and west.</gray>",
                    "<gray>4. Hold an <white>Eye of Ender</white> and right-click the End Rod or any shrine block.</gray>",
                    "<dark_gray>Base layer is a plus sign around Purpur. The End Rod is the focus.</dark_gray>"
                ),
                72L,
                16.0,
                Color.fromRGB(170, 90, 255),
                Particle.PORTAL,
                Sound.BLOCK_END_PORTAL_FRAME_FILL,
                Sound.ENTITY_ENDERMAN_TELEPORT,
                Sound.BLOCK_END_PORTAL_SPAWN
            ),
            (manager, entity) -> { }
        ),
        NEREIDA_THE_ABYSS_MOTHER(
            "nereida_the_abyss_mother",
            EntityType.DROWNED,
            Material.HEART_OF_THE_SEA,
            "<gradient:#38bdf8:#0f766e><bold>Nereida the Abyss Mother</bold></gradient>",
            380.0,
            12.0,
            0.32,
            44.0,
            0.45,
            2,
            false,
            List.of(
                "<gray>A drowned matron that turns rain and water into a battlefield.</gray>",
                "<gray>Phase One:</gray> <white>slows and drags targets under pressure</white>",
                "<gray>Phase Two:</gray> <white>surging waves, stronger hits, and regeneration in water</white>",
                "<gray>Drops Abyssal Pearls and rare Tidehearts.</gray>"
            ),
            new BossRitual(
                "Abyssal Baptism",
                Material.CONDUIT,
                Material.HEART_OF_THE_SEA,
                Material.CONDUIT,
                List.of(
                    "<gold><bold>Build Guide</bold></gold>",
                    "<gray>1. Put a <white>Prismarine</white> block on the ground.</gray>",
                    "<gray>2. Place a <white>Conduit</white> directly on top of the Prismarine.</gray>",
                    "<gray>3. Put <white>Sea Lanterns</white> touching the Prismarine north, south, east, and west.</gray>",
                    "<gray>4. Hold a <white>Heart of the Sea</white> and right-click the Conduit or any shrine block.</gray>",
                    "<dark_gray>The shrine works on land or underwater.</dark_gray>"
                ),
                68L,
                14.0,
                Color.fromRGB(35, 180, 220),
                Particle.NAUTILUS,
                Sound.BLOCK_CONDUIT_ACTIVATE,
                Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE,
                Sound.ENTITY_ELDER_GUARDIAN_CURSE
            ),
            (manager, entity) -> {
                manager.equipBossHands(entity, new ItemStack(Material.TRIDENT), null);
            }
        ),
        IRON_SAINT(
            "iron_saint",
            EntityType.IRON_GOLEM,
            Material.ANVIL,
            "<gradient:#d1d5db:#facc15><bold>The Iron Saint</bold></gradient>",
            520.0,
            16.0,
            0.25,
            42.0,
            0.80,
            4,
            false,
            List.of(
                "<gray>A slow cathedral of iron, built to punish greedy spacing.</gray>",
                "<gray>Phase One:</gray> <white>heavy melee and crushing knockback</white>",
                "<gray>Phase Two:</gray> <white>slam pulses that weaken nearby players</white>",
                "<gray>Drops Titan Gears and rare Saint Alloy.</gray>"
            ),
            new BossRitual(
                "Iron Litany",
                Material.ANVIL,
                Material.IRON_BLOCK,
                Material.ANVIL,
                List.of(
                    "<gold><bold>Build Guide</bold></gold>",
                    "<gray>1. Put a <white>Smithing Table</white> on the ground.</gray>",
                    "<gray>2. Place an <white>Anvil</white> directly on top of the Smithing Table.</gray>",
                    "<gray>3. Put <white>Iron Blocks</white> touching the Smithing Table north, south, east, and west.</gray>",
                    "<gray>4. Hold an <white>Iron Block</white> and right-click the Anvil or any shrine block.</gray>",
                    "<dark_gray>Bring real armor. The Saint does not negotiate.</dark_gray>"
                ),
                76L,
                17.0,
                Color.fromRGB(190, 190, 170),
                Particle.CRIT,
                Sound.BLOCK_ANVIL_LAND,
                Sound.BLOCK_ANVIL_USE,
                Sound.ENTITY_IRON_GOLEM_REPAIR
            ),
            (manager, entity) -> { }
        ),
        MIREWOOD_THE_ROOT_TYRANT(
            "mirewood_the_root_tyrant",
            EntityType.HUSK,
            Material.MANGROVE_ROOTS,
            "<gradient:#16a34a:#854d0e><bold>Mirewood the Root Tyrant</bold></gradient>",
            400.0,
            12.0,
            0.30,
            40.0,
            0.55,
            2,
            false,
            List.of(
                "<gray>An old root wearing a corpse as a crown.</gray>",
                "<gray>Phase One:</gray> <white>roots and slows players that let it close distance</white>",
                "<gray>Phase Two:</gray> <white>regenerates and grows harsher root pulses</white>",
                "<gray>Drops Living Bark and rare Verdant Hearts.</gray>"
            ),
            new BossRitual(
                "Root Tyrant's Wake",
                Material.MANGROVE_ROOTS,
                Material.SPORE_BLOSSOM,
                Material.MANGROVE_ROOTS,
                List.of(
                    "<gold><bold>Build Guide</bold></gold>",
                    "<gray>1. Put a <white>Moss Block</white> on the ground.</gray>",
                    "<gray>2. Place <white>Mangrove Roots</white> directly on top of the Moss Block.</gray>",
                    "<gray>3. Put <white>Oak Saplings</white> touching the Moss Block north, south, east, and west.</gray>",
                    "<gray>4. Hold a <white>Spore Blossom</white> and right-click the Mangrove Roots or any shrine block.</gray>",
                    "<dark_gray>The roots vanish on success. If they remain, the pattern is wrong.</dark_gray>"
                ),
                70L,
                15.0,
                Color.fromRGB(60, 175, 75),
                Particle.SPORE_BLOSSOM_AIR,
                Sound.BLOCK_ROOTED_DIRT_BREAK,
                Sound.BLOCK_AZALEA_LEAVES_PLACE,
                Sound.ENTITY_ZOMBIE_VILLAGER_CURE
            ),
            (manager, entity) -> {
                if (entity instanceof Zombie zombie) {
                    zombie.setAdult();
                    zombie.setConversionTime(-1);
                }
                manager.equipBossArmor(
                    entity,
                    new ItemStack(Material.MOSS_BLOCK),
                    new ItemStack(Material.LEATHER_CHESTPLATE),
                    new ItemStack(Material.LEATHER_LEGGINGS),
                    new ItemStack(Material.LEATHER_BOOTS)
                );
            }
        );

        private static final Map<String, BossType> BY_ID = new HashMap<>();
        static {
            for (BossType type : values()) {
                BY_ID.put(type.id, type);
            }
        }

        private final String id;
        private final EntityType entityType;
        private final Material menuIcon;
        private final String displayName;
        private final double maxHealth;
        private final double attackDamage;
        private final double movementSpeed;
        private final double followRange;
        private final double knockbackResistance;
        private final int requiredAirBlocks;
        private final boolean glowing;
        private final List<String> description;
        private final BossRitual ritual;
        private final BossConfigurer configurer;

        BossType(
            String id,
            EntityType entityType,
            Material menuIcon,
            String displayName,
            double maxHealth,
            double attackDamage,
            double movementSpeed,
            double followRange,
            double knockbackResistance,
            int requiredAirBlocks,
            boolean glowing,
            List<String> description,
            BossRitual ritual,
            BossConfigurer configurer
        ) {
            this.id = id;
            this.entityType = entityType;
            this.menuIcon = menuIcon;
            this.displayName = displayName;
            this.maxHealth = maxHealth;
            this.attackDamage = attackDamage;
            this.movementSpeed = movementSpeed;
            this.followRange = followRange;
            this.knockbackResistance = knockbackResistance;
            this.requiredAirBlocks = Math.max(2, requiredAirBlocks);
            this.glowing = glowing;
            this.description = List.copyOf(description);
            this.ritual = ritual;
            this.configurer = configurer == null ? (manager, entity) -> { } : configurer;
        }

        public static BossType fromId(String id) {
            return id == null ? null : BY_ID.get(id.trim().toLowerCase(Locale.ROOT));
        }

        public static BossType fromInput(String input) {
            if (input == null || input.isBlank()) {
                return null;
            }
            String normalized = input.trim().toLowerCase(Locale.ROOT).replace('-', '_');
            for (BossType type : values()) {
                if (type.id.equals(normalized) || type.commandToken().equals(normalized)) {
                    return type;
                }
            }
            return null;
        }

        public String id() {
            return id;
        }

        public EntityType entityType() {
            return entityType;
        }

        public Material menuIcon() {
            return menuIcon;
        }

        public String displayName() {
            return displayName;
        }

        public double maxHealth() {
            return maxHealth;
        }

        public double attackDamage() {
            return attackDamage;
        }

        public double movementSpeed() {
            return movementSpeed;
        }

        public double followRange() {
            return followRange;
        }

        public double knockbackResistance() {
            return knockbackResistance;
        }

        public int requiredAirBlocks() {
            return requiredAirBlocks;
        }

        public boolean glowing() {
            return glowing;
        }

        public List<String> description() {
            return description;
        }

        public BossRitual ritual() {
            return ritual;
        }

        public BossConfigurer configurer() {
            return configurer;
        }

        public String plainDisplayName() {
            return MiniMessage.miniMessage().stripTags(displayName);
        }

        public String commandToken() {
            return plainDisplayName()
                .toLowerCase(Locale.ROOT)
                .replace("'", "")
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        }
    }

    private record BossMenuHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record BossRitualMenuHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
