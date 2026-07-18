package me.rique.smpcore.boss;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.season.SeasonRelicManager;
import me.rique.smpcore.database.DatabaseManager;
import me.rique.smpcore.essence.PriestManager;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.ItemModelUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.util.VisualRangeUtil;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
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
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Warden;
import org.bukkit.entity.WindCharge;
import org.bukkit.entity.Witch;
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
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
import org.bukkit.event.inventory.ClickType;
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

import java.time.Duration;
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
        19, 21, 23, 25,
        30, 32
    };
    private static final String SCOREBOARD_TAG = "smpcore_custom_boss";
    private static final long ORPHAN_MINION_CLEANUP_INTERVAL_MS = 30_000L;
    private static final int HOLOGRAM_TELEPORT_DURATION_TICKS = 8;
    private static final double BOSS_SCALE_RADIUS_PER_EXTRA_PLAYER = 0.35;
    private static final double BOSS_SCALE_COOLDOWN_REDUCTION_PER_EXTRA_PLAYER = 0.025;
    private static final double BOSS_SCALE_MIN_COOLDOWN_MULTIPLIER = 0.84;
    private static final int BOSS_SCALE_MAX_EXTRA_PLAYERS = 6;
    private static final int ACTIVE_MECHANIC_SLOWNESS_TICKS = 15;
    private static final int ACTIVE_MECHANIC_SLOWNESS_AMPLIFIER = 4;
    private static final double ACTIVE_MECHANIC_MOMENTUM_MULTIPLIER = 0.15;
    private static final long BOSS_FAILURE_GRACE_MS = 3_000L;
    private static final double PLAYER_ARENA_ESCAPE_BUFFER = 1.75D;
    private static final int BOSS_ENTRANCE_DURATION_TICKS = 44;
    private static final long BOSS_ATTACK_VISUAL_COOLDOWN_MS = 325L;

    private final SMPCore plugin;
    private final NamespacedKey keyBossId;
    private final NamespacedKey keyBossInstanceId;
    private final NamespacedKey keyBossMarker;
    private final NamespacedKey keyBossPhase;
    private final NamespacedKey keyBossPrimaryCooldown;
    private final NamespacedKey keyBossSecondaryCooldown;
    private final NamespacedKey keyBossTertiaryCooldown;
    private final NamespacedKey keyBossPressureCooldown;
    private final NamespacedKey keyBossDialogueCooldown;
    private final NamespacedKey keyBossStoryLowHealth;
    private final NamespacedKey keyBossMinionMarker;
    private final NamespacedKey keyBossMinionOwner;
    private final NamespacedKey keyBossScaledPlayerCount;
    private final NamespacedKey keyBossDisplayMaxHealth;
    private final NamespacedKey keyBossArenaHazardCooldown;
    private final NamespacedKey keyBossAggroRetargetAt;
    private final NamespacedKey keyDominionCoreItem;
    private final NamespacedKey keyBossLootChest;
    private final NamespacedKey keyBossLootOwner;
    private final NamespacedKey keyBossLootHologram;
    private final NamespacedKey keyBossLootHologramBlock;

    private final Map<UUID, BossRecord> trackedBosses = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> trackedByBossId = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> holograms = new ConcurrentHashMap<>();
    private final Map<UUID, BossArena> bossArenas = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> bossArenaPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, BossFightState> bossFightStates = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveBossMechanic> activeBossMechanics = new ConcurrentHashMap<>();
    private final Set<UUID> bossMechanicDamageTargets = new HashSet<>();
    private final Set<UUID> bossMechanicFailureTargets = new HashSet<>();
    private final Set<UUID> allowedBossTeleports = ConcurrentHashMap.newKeySet();
    private final Set<UUID> telegraphingBosses = ConcurrentHashMap.newKeySet();
    private final Set<UUID> bossEntranceAnimations = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> bossAttackVisualCooldowns = new ConcurrentHashMap<>();
    private final Set<String> pendingRituals = ConcurrentHashMap.newKeySet();
    private BukkitTask heartbeatTask;
    private long nextOrphanMinionCleanupAt;

    public BossManager(SMPCore plugin) {
        this.plugin = plugin;
        this.keyBossId = new NamespacedKey(plugin, "boss_id");
        this.keyBossInstanceId = new NamespacedKey(plugin, "boss_instance_id");
        this.keyBossMarker = new NamespacedKey(plugin, "boss_marker");
        this.keyBossPhase = new NamespacedKey(plugin, "boss_phase");
        this.keyBossPrimaryCooldown = new NamespacedKey(plugin, "boss_primary_cd");
        this.keyBossSecondaryCooldown = new NamespacedKey(plugin, "boss_secondary_cd");
        this.keyBossTertiaryCooldown = new NamespacedKey(plugin, "boss_tertiary_cd");
        this.keyBossPressureCooldown = new NamespacedKey(plugin, "boss_pressure_cd");
        this.keyBossDialogueCooldown = new NamespacedKey(plugin, "boss_dialogue_cd");
        this.keyBossStoryLowHealth = new NamespacedKey(plugin, "boss_story_low_health");
        this.keyBossMinionMarker = new NamespacedKey(plugin, "boss_minion_marker");
        this.keyBossMinionOwner = new NamespacedKey(plugin, "boss_minion_owner");
        this.keyBossScaledPlayerCount = new NamespacedKey(plugin, "boss_scaled_player_count");
        this.keyBossDisplayMaxHealth = new NamespacedKey(plugin, "boss_display_max_health");
        this.keyBossArenaHazardCooldown = new NamespacedKey(plugin, "boss_arena_hazard_cd");
        this.keyBossAggroRetargetAt = new NamespacedKey(plugin, "boss_aggro_retarget_at");
        this.keyDominionCoreItem = new NamespacedKey(plugin, DOMINION_CORE_ITEM_ID);
        this.keyBossLootChest = new NamespacedKey(plugin, "boss_loot_chest");
        this.keyBossLootOwner = new NamespacedKey(plugin, "boss_loot_owner");
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
        bossArenaPlayers.clear();
        bossFightStates.clear();
        activeBossMechanics.clear();
        bossMechanicDamageTargets.clear();
        bossMechanicFailureTargets.clear();
        allowedBossTeleports.clear();
        telegraphingBosses.clear();
        bossEntranceAnimations.clear();
        bossAttackVisualCooldowns.clear();
        pendingRituals.clear();
    }

    public List<String> bossIds() {
        List<String> ids = new ArrayList<>();
        for (BossType type : BossType.progressionOrder()) {
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
        for (BossType type : BossType.progressionOrder()) {
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

        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.LEGENDARY, "Veil Core"));
        ItemModelUtil.apply(meta, DOMINION_CORE_ITEM_ID);
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.ECHO_SHARD,
            CustomLoreUtil.Rarity.LEGENDARY.label(),
            "RELIC",
            List.of("<gray>A pulsing shard torn from a fallen Veil warden.</gray>"),
            List.of(CustomLoreUtil.section(
                "Use",
                "Veil Repair",
                "<gray>Use it in an <white>Anvil</white> with <white>Veil Dominion</white>.</gray>",
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
        openBossMenu(player, false);
    }

    private void openBossMenu(Player player, boolean despawnMode) {
        reconcileLoadedBosses();

        Inventory inventory = Bukkit.createInventory(
            new BossMenuHolder(despawnMode),
            54,
            BedrockCompat.menuTitle(player, MENU_TITLE, "Boss Control")
        );
        ItemStack filler = menuItem(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isFrameSlot(slot, inventory.getSize())) {
                inventory.setItem(slot, filler);
            }
        }

        inventory.setItem(4, createOverviewItem(despawnMode));
        inventory.setItem(45, menuItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to /menu.</gray>")));
        inventory.setItem(47, createBossActionModeItem(despawnMode));
        inventory.setItem(49, createClearAllItem());
        inventory.setItem(53, createRefreshItem());

        List<BossType> types = BossType.progressionOrder();
        if (types.isEmpty()) {
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
            for (int i = 0; i < types.size() && i < BOSS_SLOTS.length; i++) {
                inventory.setItem(BOSS_SLOTS[i], createBossEntryItem(player, types.get(i), despawnMode));
            }
        }
        player.openInventory(inventory);
    }

    public void openRitualMenu(Player player) {
        if (plugin.getBossDungeonManager() != null) {
            plugin.getBossDungeonManager().openBossCatalog(player);
            return;
        }
        Inventory inventory = Bukkit.createInventory(
            new BossRitualMenuHolder(),
            54,
            BedrockCompat.menuTitle(player, RITUAL_MENU_TITLE, "Boss Rituals")
        );
        ItemStack filler = menuItem(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isFrameSlot(slot, inventory.getSize())) {
                inventory.setItem(slot, filler);
            }
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

        List<BossType> types = BossType.progressionOrder();
        for (int i = 0; i < types.size() && i < RITUAL_SLOTS.length; i++) {
            inventory.setItem(RITUAL_SLOTS[i], createRitualEntryItem(player, types.get(i)));
        }
        player.openInventory(inventory);
    }

    private boolean isFrameSlot(int slot, int size) {
        return slot < 9 || slot >= size - 9 || slot % 9 == 0 || slot % 9 == 8;
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

    public BossActionResult spawnDungeonBoss(BossType type, Location location, Player summoner) {
        if (plugin.getBossDungeonManager() == null
            || location == null
            || !plugin.getBossDungeonManager().isDungeonWorld(location.getWorld())) {
            return new BossActionResult(false, "Bosses can only be summoned in the boss dungeon.");
        }
        if (hasActiveBossInWorld(location.getWorld())) {
            return new BossActionResult(false, "The arena is already occupied.");
        }
        return spawnBoss(type, location, summoner, true);
    }

    public boolean hasActiveBossInWorld(World world) {
        if (world == null) {
            return false;
        }
        reconcileLoadedBosses();
        return trackedBosses.values().stream().anyMatch(record -> world.getName().equals(record.world()));
    }

    public int despawnBossesInWorld(World world) {
        if (world == null) return 0;
        return despawnBossRecords(record -> world.getName().equals(record.world()));
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
        if (summoner != null) {
            recordBossFightEngagement(record.entityUuid(), summoner);
        }
        try {
            startBossArena(spawned, type);
            playBossSpawnBurst(spawned, type, fromRitual);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Boss spawn visuals failed for " + type.id() + ": " + ex.getMessage());
        }
        BossDialogue.Profile dialogue = BossDialogue.profile(type.id());
        String entranceLine = plugin.getStoryService() == null
            ? dialogue.entranceLine()
            : plugin.getStoryService().bossEntrance(type.id(), dialogue.entranceLine());
        sendBossLine(spawned, type, entranceLine);
        scheduleNextBossDialogue(spawned, 14_000L, 22_000L);

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

    public String customBossId(Entity entity) {
        BossRecord record = bossRecord(entity);
        return record == null ? null : record.bossId();
    }

    public boolean isBossEncounterEntity(Entity entity) {
        return bossRecord(entity) != null || isBossMinion(entity);
    }

    public boolean isActiveBossFight(Player player) {
        return activeFightStateFor(player) != null;
    }

    public BossMusicContext bossMusicContext(Player player) {
        if (player == null || !player.isOnline() || !player.isValid()) {
            return null;
        }

        BossDungeonManager dungeon = plugin.getBossDungeonManager();
        BossType dungeonType = dungeon != null && dungeon.isDungeonWorld(player.getWorld())
            ? dungeon.activeEncounterType()
            : null;
        BossMusicContext closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (BossRecord record : trackedBosses.values()) {
            Entity entity = Bukkit.getEntity(record.entityUuid());
            BossType type = BossType.fromId(record.bossId());
            if (!(entity instanceof LivingEntity boss) || type == null || boss.isDead() || !boss.isValid()
                || boss.getWorld() != player.getWorld()) {
                continue;
            }
            if (dungeonType != null) {
                if (type != dungeonType) {
                    continue;
                }
            } else if (!isPlayerInFightArea(player, boss, type)) {
                continue;
            }

            double distance = player.getLocation().distanceSquared(boss.getLocation());
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = new BossMusicContext(record.entityUuid(), type.id());
            }
        }
        return closest;
    }

    public boolean isLethalBossMechanicDamage(Player player) {
        return player != null && bossMechanicFailureTargets.contains(player.getUniqueId());
    }

    public boolean isBossOwnedProjectile(Projectile projectile) {
        return projectile != null
            && projectile.getShooter() instanceof Entity shooter
            && isBossEncounterEntity(shooter);
    }

    public List<String> statusLines() {
        reconcileLoadedBosses();
        List<String> lines = new ArrayList<>();
        lines.add("<gold><bold>Boss Status</bold></gold>");
        lines.add("<gray>Total tracked bosses:</gray> <white>" + trackedBosses.size() + "</white>");

        List<BossType> types = BossType.progressionOrder();
        if (types.isEmpty()) {
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
            maybeSendStoryLowHealthLine(living, type);
            ensureBossAiState(living, type);
            tickBossArena(living, type);
            retargetBossByAggro(living, type);
            tickBossBehavior(living, type);
            maybeBossDialogue(living, type);
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
            boolean adminTest = plugin.getBossDungeonManager() != null
                && plugin.getBossDungeonManager().isDungeonWorld(event.getEntity().getWorld())
                && plugin.getBossDungeonManager().isAdminTestEncounter();
            if (adminTest) {
                event.setDroppedExp(0);
                if (type != null) {
                    String fallback = BossDialogue.profile(type.id()).defeatLine();
                    sendBossLine(event.getEntity(), type, plugin.getStoryService() == null ? fallback : plugin.getStoryService().bossDefeat(type.id(), fallback));
                }
                plugin.getBossDungeonManager().onBossFightFinished(true, null);
                despawnBossMinions(record.entityUuid());
                destroyBossVisuals(record.entityUuid());
                untrackRecord(record.entityUuid());
                plugin.getDatabase().deleteBossRecord(record.entityUuid());
                return;
            }
            BossFightState state = bossFightStates.get(record.entityUuid());
            if (killer == null) {
                killer = topOnlineParticipant(state);
            }
            WispBossLootBonus wispBonus = rollWispBossLootBonus(state, killer);
            boolean doubleDrops = wispBonus.success();
            if (type != null) {
                String fallback = BossDialogue.profile(type.id()).defeatLine();
                sendBossLine(event.getEntity(), type, plugin.getStoryService() == null ? fallback : plugin.getStoryService().bossDefeat(type.id(), fallback));
            }
            announceBossKill(type, killer, event.getEntity().getLocation(), wispBonus);
            if (killer != null && plugin.getLeaderboardManager() != null) {
                plugin.getLeaderboardManager().recordBossKill(killer, record.bossId());
            }
            Set<UUID> questParticipants = state == null ? new LinkedHashSet<>() : state.participantIds();
            if (killer != null) {
                questParticipants.add(killer.getUniqueId());
            }
            if (type != null) {
                if (plugin.getMayorQuestManager() != null) {
                    plugin.getMayorQuestManager().recordBossDefeat(type.id(), questParticipants);
                }
                if (plugin.getOverseerManager() != null) {
                    plugin.getOverseerManager().recordBossDefeat(questParticipants);
                }
                if (plugin.getBossMasteryManager() != null) {
                    plugin.getBossMasteryManager().recordBossDefeat(type.id(), questParticipants);
                }
                if (plugin.getBlackMarketManager() != null) {
                    plugin.getBlackMarketManager().recordBossDefeat(type, questParticipants);
                }
            }
            int dropMultiplier = wispBonus.success() ? 2 : 1;
            List<ItemStack> rewardDrops = new ArrayList<>();
            boolean soulImprintDropped = false;
            if (type == BossType.VORALITH_THE_CRIMSON_WARDEN) {
                ItemStack coreDrop = createDominionCoreItem();
                addBossDrop(rewardDrops, killer, coreDrop, dropMultiplier, "Dropped from Noctyr the Veil Warden.");
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
                    "Dropped from Asterion the Rift Oracle."
                );
            }
            if (type != null && plugin.getSeasonRelicManager() != null) {
                for (ItemStack drop : plugin.getSeasonRelicManager().createBossDrops(type.id())) {
                    if (drop == null || drop.getType().isAir()) {
                        continue;
                    }
                    if (plugin.getSeasonRelicManager().isSoulImprint(drop)) {
                        soulImprintDropped = true;
                    }
                    addBossDrop(rewardDrops, killer, drop, dropMultiplier, "Dropped from " + type.plainDisplayName() + ".");
                }
            }
            if (rewardDrops.isEmpty() && type != BossType.CORRUPTED_OATHKEEPER) {
                addBossDrop(rewardDrops, killer, guaranteedBossFallbackDrop(type), dropMultiplier, "Guaranteed fallback drop from " + (type == null ? "a custom boss" : type.plainDisplayName()) + ".");
            }
            UUID dungeonOwner = plugin.getBossDungeonManager() != null
                && plugin.getBossDungeonManager().isDungeonWorld(event.getEntity().getWorld())
                ? plugin.getBossDungeonManager().currentEncounterOwnerId()
                : null;
            Block dungeonLootChest = rewardDrops.isEmpty() ? null : spawnBossLootChest(type, event.getEntity().getLocation(), rewardDrops, dungeonOwner);
            if (soulImprintDropped) {
                announceSoulImprintDrop(killer, state, event.getEntity().getLocation());
            }
            event.setDroppedExp(Math.max(event.getDroppedExp(), bossExperience(type)));
            finishBossFight(record, type, true, doubleDrops, event.getEntity().getLocation());
            if (type != null && plugin.getStoryService() != null) {
                plugin.getStoryService().onBossDefeated(type.id(), record.entityUuid(), questParticipants);
            }
            if (plugin.getBossDungeonManager() != null && plugin.getBossDungeonManager().isDungeonWorld(event.getEntity().getWorld())) {
                plugin.getBossDungeonManager().onBossFightFinished(true, dungeonLootChest);
            }
            despawnBossMinions(record.entityUuid());
            destroyBossVisuals(record.entityUuid());
            untrackRecord(record.entityUuid());
            plugin.getDatabase().deleteBossRecord(record.entityUuid());
            return;
        }

        if (isBossMinion(entity)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossSlimeSplit(SlimeSplitEvent event) {
        if (bossRecord(event.getEntity()) != null || isBossMinion(event.getEntity())) {
            event.setCount(0);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossArenaGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player) || !event.isGliding() || !isPlayerRestrictedByArena(player)) {
            return;
        }
        event.setCancelled(true);
        player.setGliding(false);
        player.sendActionBar(MM.deserialize("<red>Elytras are disabled during boss fights.</red>"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossArenaWindChargeUse(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if ((event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            || item == null
            || item.getType() != Material.WIND_CHARGE
            || !isPlayerRestrictedByArena(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendActionBar(MM.deserialize("<red>Wind charges are disabled during boss fights.</red>"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossArenaWindChargeLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof LivingEntity boss
            && bossEntranceAnimations.contains(boss.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof WindCharge charge)
            || !(charge.getShooter() instanceof Player player)
            || !isPlayerRestrictedByArena(player)) {
            return;
        }
        event.setCancelled(true);
        player.sendActionBar(MM.deserialize("<red>Wind charges are disabled during boss fights.</red>"));
    }

    private void announceBossKill(BossType type, Player killer, Location location, WispBossLootBonus wispBonus) {
        if (type == null) {
            return;
        }
        String killerName = killer == null ? "Someone" : killer.getName();
        List<String> bonuses = new ArrayList<>();
        if (wispBonus != null && wispBonus.success()) {
            bonuses.add("Veil Wisp doubled the loot.");
        }
        String bonus = bonuses.isEmpty() ? "" : " <gold><bold>" + String.join(" ", bonuses) + "</bold></gold>";
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

    private WispBossLootBonus rollWispBossLootBonus(BossFightState state, Player killer) {
        if (plugin.getMayorQuestManager() == null) {
            return WispBossLootBonus.none();
        }
        Set<UUID> participants = state == null ? new LinkedHashSet<>() : state.participantIds();
        if (killer != null) {
            participants.add(killer.getUniqueId());
        }
        int activePets = plugin.getMayorQuestManager().activeVeilWispCount(participants);
        if (activePets <= 0) {
            return WispBossLootBonus.none();
        }
        double strongestCore = 1.0D;
        if (plugin.getBeastwardenManager() != null) {
            for (UUID participantId : participants) {
                Player participant = Bukkit.getPlayer(participantId);
                if (participant != null && plugin.getMayorQuestManager().hasActiveVeilWisp(participant)) {
                    strongestCore = Math.max(strongestCore, plugin.getBeastwardenManager().familiarCoreMultiplier(participant, "veil_wisp"));
                }
            }
        }
        double chance = Math.min(1.0D, (0.50D + (activePets * 0.01D)) * strongestCore);
        return new WispBossLootBonus(activePets, chance, ThreadLocalRandom.current().nextDouble() < chance);
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
            if (owner != null && plugin.getSeasonRelicManager() != null) {
                String relicId = plugin.getSeasonRelicManager().relicId(multiplied);
                if (relicId != null) {
                    plugin.getSeasonRelicManager().markRelicDiscovered(owner, relicId);
                }
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
            case MORVESSA_THE_RUNEBLOOM_WITCH -> "rift_lens";
            case NEREIDA_THE_ABYSS_MOTHER -> "abyssal_pearl";
            case IRON_SAINT -> "titan_gear";
            case MIREWOOD_THE_ROOT_TYRANT -> "living_bark";
            case CORRUPTED_OATHKEEPER -> "corrupted_essence";
        };
    }

    private Block spawnBossLootChest(BossType type, Location location, List<ItemStack> rewards, UUID dungeonOwner) {
        if (location == null || location.getWorld() == null || rewards == null || rewards.isEmpty()) {
            return null;
        }

        Block block = findBossLootChestBlock(location);
        Chest chest = placeBossLootChest(block);
        if (chest == null) {
            dropBossLootNaturally(location, rewards);
            plugin.getLogger().warning("Dropped boss loot naturally because no loot chest spot could be prepared at "
                + location.getWorld().getName() + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + ".");
            return null;
        }

        String bossName = type == null ? "Unknown Boss" : type.plainDisplayName();
        chest.customName(MM.deserialize("<gradient:#ff4d6d:#facc15><bold>" + escapeMiniMessage(bossName) + " Loot</bold></gradient>"));
        chest.getPersistentDataContainer().set(keyBossLootChest, PersistentDataType.STRING, bossName);
        if (dungeonOwner != null) {
            chest.getPersistentDataContainer().set(keyBossLootOwner, PersistentDataType.STRING, dungeonOwner.toString());
        }
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
        return block;
    }

    private Chest placeBossLootChest(Block block) {
        if (!isBossLootChestSpot(block)) {
            return null;
        }

        try {
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
        if (plugin.getBossDungeonManager() != null
            && plugin.getBossDungeonManager().isDungeonWorld(block.getWorld())
            && !type.isAir()) {
            return false;
        }
        if (!type.isAir() && !block.isReplaceable()) {
            return false;
        }
        Block above = block.getRelative(BlockFace.UP);
        return !above.getType().isOccluding();
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

    public boolean isDungeonLootChest(Block block) {
        return isBossLootChest(block);
    }

    public UUID dungeonLootOwner(Block block) {
        if (block == null || !(block.getState() instanceof TileState tileState)) {
            return null;
        }
        String raw = tileState.getPersistentDataContainer().get(keyBossLootOwner, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public void clearDungeonLootChest(Block block) {
        if (!isBossLootChest(block)) return;
        removeBossLootHologram(block);
        if (block.getState() instanceof Chest chest) chest.getBlockInventory().clear();
        block.setType(Material.AIR, false);
    }

    private String bossLootBlockKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private int bossExperience(BossType type) {
        if (type == null) {
            return 80;
        }
        return switch (type) {
            case YULE_THE_MINION -> 200;
            case KAEL_THE_ASHEN -> 260;
            case VESPER_THE_WIDOW_QUEEN -> 340;
            case MIREWOOD_THE_ROOT_TYRANT -> 430;
            case NEREIDA_THE_ABYSS_MOTHER -> 525;
            case AURELION_THE_RIFT_SERAPH -> 800;
            case MORVESSA_THE_RUNEBLOOM_WITCH -> 950;
            case IRON_SAINT -> 650;
            case VORALITH_THE_CRIMSON_WARDEN -> 1100;
            case CORRUPTED_OATHKEEPER -> 1800;
        };
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
    public void onBossIncomingDamage(EntityDamageEvent event) {
        BossRecord record = bossRecord(event.getEntity());
        if (record == null || !(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        BossType type = BossType.fromId(record.bossId());
        if (type == null) {
            return;
        }
        if (bossEntranceAnimations.contains(entity.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        ActiveBossMechanic mechanic = activeBossMechanics.get(entity.getUniqueId());
        if (type == BossType.IRON_SAINT
            && mechanic != null
            && mechanic.kind == BossMechanicKind.IRON_COUNTERSTANCE
            && mechanic.stage == 0) {
            event.setCancelled(true);
            Player attacker = event instanceof EntityDamageByEntityEvent byEntityEvent
                ? attackingPlayer(byEntityEvent.getDamager())
                : null;
            if (attacker != null) {
                mechanic.progress += 1.0;
                long now = System.currentTimeMillis();
                long nextReflection = mechanic.hitCooldowns.getOrDefault(attacker.getUniqueId(), 0L);
                if (now >= nextReflection) {
                    mechanic.hitCooldowns.put(attacker.getUniqueId(), now + 650L);
                    punishMechanicHazard(attacker, entity, type);
                    attacker.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 70, 0, false, true, true));
                    attacker.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1, false, true, true));
                    attacker.getWorld().spawnParticle(Particle.CRIT, attacker.getLocation().clone().add(0.0, 1.0, 0.0), 24, 0.35, 0.5, 0.35, 0.12);
                    attacker.getWorld().playSound(attacker.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.2f, 0.45f);
                }
            }
            return;
        }
        applyBossWardDamageBonus(event);
        scaleBossIncomingDamage(entity, type, event);
        if (type == BossType.IRON_SAINT
            && mechanic != null
            && (mechanic.kind == BossMechanicKind.SAINTS_STAGGER
                || mechanic.kind == BossMechanicKind.IRON_COUNTERSTANCE)
            && mechanic.stage >= 1) {
            event.setDamage(event.getDamage() * 1.25);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossKnockback(EntityKnockbackEvent event) {
        if (!(event.getEntity() instanceof LivingEntity boss) || bossRecord(boss) == null) {
            return;
        }
        BossArena arena = bossArenas.get(boss.getUniqueId());
        if (arena == null || arena.center().getWorld() != boss.getWorld()) {
            return;
        }

        Location center = arena.center();
        double distance = Math.sqrt(horizontalDistanceSquared(boss.getLocation(), center));
        double pressure = BossMechanics.arenaEdgePressure(distance, arena.radius());
        if (pressure <= 0.0) {
            return;
        }

        Vector outward = boss.getLocation().toVector().subtract(center.toVector()).setY(0.0);
        if (outward.lengthSquared() <= 1.0E-6) {
            return;
        }
        outward.normalize();
        Vector adjusted = event.getKnockback().clone();
        double outwardAmount = adjusted.dot(outward);
        if (outwardAmount > 0.0) {
            double retained = BossMechanics.retainedOutwardKnockback(pressure);
            adjusted.subtract(outward.clone().multiply(outwardAmount * (1.0 - retained)));
        }
        if (pressure >= 0.45) {
            adjusted.subtract(outward.clone().multiply(0.04 + pressure * 0.08));
        }
        event.setKnockback(adjusted);
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
        if (bossEntranceAnimations.contains(shooter.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        BossType type = BossType.fromId(record.bossId());
        ActiveBossMechanic mechanic = activeBossMechanics.get(shooter.getUniqueId());
        if (mechanic != null && isBossHeldByMechanic(mechanic.kind)) {
            event.setCancelled(true);
            return;
        }
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
        if (bossEntranceAnimations.contains(bossEntity.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        BossType type = BossType.fromId(record.bossId());
        if (type == null || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        boolean mechanicDamage = target instanceof Player player
            && bossMechanicDamageTargets.contains(player.getUniqueId());
        ActiveBossMechanic mechanic = activeBossMechanics.get(bossEntity.getUniqueId());
        if (mechanic != null && isBossHeldByMechanic(mechanic.kind) && !mechanicDamage) {
            event.setCancelled(true);
            return;
        }

        if (target instanceof Player player) {
            recordBossFightEngagement(record.entityUuid(), player);
        }
        if (mechanicDamage) {
            return;
        }

        int phase = bossPhase(bossEntity);
        if (type == BossType.YULE_THE_MINION && !projectileHit) {
            playBossAttackAnimation(bossEntity, target, type, phase, false);
            spawnYuleAttackParticles(bossEntity, target, phase);
            if (phase >= 2) {
                applyYulePhaseTwoKnockback(bossEntity, target);
            }
            return;
        }

        if (type == BossType.KAEL_THE_ASHEN && projectileHit) {
            playBossAttackAnimation(bossEntity, target, type, phase, true);
            handleKaelProjectileHit(bossEntity, target, phase, event);
            return;
        }

        if (type == BossType.VESPER_THE_WIDOW_QUEEN && !projectileHit) {
            playBossAttackAnimation(bossEntity, target, type, phase, false);
            handleVesperMeleeHit(bossEntity, target, phase);
            return;
        }

        if (type == BossType.VORALITH_THE_CRIMSON_WARDEN && !projectileHit) {
            playBossAttackAnimation(bossEntity, target, type, phase, false);
            handleVoralithMeleeHit(bossEntity, target, phase, event);
            return;
        }

        if (type == BossType.AURELION_THE_RIFT_SERAPH && !projectileHit) {
            playBossAttackAnimation(bossEntity, target, type, phase, false);
            handleAurelionMeleeHit(bossEntity, target, phase, event);
            return;
        }

        if (type == BossType.MORVESSA_THE_RUNEBLOOM_WITCH && projectileHit) {
            playBossAttackAnimation(bossEntity, target, type, phase, true);
            handleMorvessaProjectileHit(bossEntity, target, phase, event);
            return;
        }

        if (type == BossType.NEREIDA_THE_ABYSS_MOTHER) {
            playBossAttackAnimation(bossEntity, target, type, phase, projectileHit);
            handleNereidaAttackHit(bossEntity, target, phase, event);
            return;
        }

        if (type == BossType.IRON_SAINT && !projectileHit) {
            playBossAttackAnimation(bossEntity, target, type, phase, false);
            handleIronSaintMeleeHit(bossEntity, target, phase, event);
            return;
        }

        if (type == BossType.MIREWOOD_THE_ROOT_TYRANT && !projectileHit) {
            playBossAttackAnimation(bossEntity, target, type, phase, false);
            handleMirewoodMeleeHit(bossEntity, target, phase, event);
            return;
        }

        if (type == BossType.CORRUPTED_OATHKEEPER && !projectileHit) {
            playBossAttackAnimation(bossEntity, target, type, phase, false);
            handleCorruptedOathkeeperMeleeHit(bossEntity, target, phase, event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDamageRecorded(EntityDamageByEntityEvent event) {
        LivingEntity boss;
        if (event.getDamager() instanceof LivingEntity living) {
            boss = living;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            boss = shooter;
        } else {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        BossRecord record = bossRecord(boss);
        BossFightState state = record == null ? null : bossFightStates.get(record.entityUuid());
        if (state != null) {
            state.addDamageTaken(player, Math.max(0.0, event.getFinalDamage()));
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

        BossType type = BossType.fromId(record.bossId());
        double finalDamage = reportedDamageFor((LivingEntity) event.getEntity(), event.getFinalDamage());
        recordBossFightEngagement(record.entityUuid(), attacker);
        if (finalDamage <= 0.0) {
            return;
        }

        ActiveBossMechanic mechanic = activeBossMechanics.get(record.entityUuid());
        if (type == BossType.IRON_SAINT
            && mechanic != null
            && mechanic.kind == BossMechanicKind.SAINTS_STAGGER
            && mechanic.stage == 0) {
            mechanic.progress += event.getFinalDamage();
        }

        BossFightState state = fightState(record);
        state.addDamage(attacker, finalDamage);
    }

    public double reportedDamageFor(LivingEntity target, double actualDamage) {
        if (target == null || !Double.isFinite(actualDamage) || actualDamage <= 0.0) {
            return 0.0;
        }
        BossRecord record = bossRecord(target);
        BossType type = record == null ? null : BossType.fromId(record.bossId());
        if (type == null) {
            return actualDamage;
        }
        return BossBalance.reportedDamage(
            target.getHealth(),
            actualDamage,
            actualBossMaxHealth(target, type),
            displayBossMaxHealth(target, type)
        );
    }

    public void recordDirectBossDamage(Player attacker, LivingEntity target, double actualDamage) {
        if (attacker == null || target == null) {
            return;
        }
        BossRecord record = bossRecord(target);
        if (record == null) {
            return;
        }
        double reportedDamage = reportedDamageFor(target, actualDamage);
        recordBossFightEngagement(record.entityUuid(), attacker);
        if (reportedDamage > 0.0) {
            fightState(record).addDamage(attacker, reportedDamage);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBossFightHealing(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!event.isCancelled() && blockHealingIfSuppressed(player, event.getAmount())) {
            event.setCancelled(true);
            return;
        }
        if (event.isCancelled()) {
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
        removePlayerFromBossArenaRosters(event.getEntity().getUniqueId());
        markBossFightLossCheck(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBossFightPlayerQuit(PlayerQuitEvent event) {
        removePlayerFromBossArenaRosters(event.getPlayer().getUniqueId());
        markBossFightLossCheck(event.getPlayer());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        reconcileChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof BossMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() < 0 || event.getRawSlot() >= topSize) {
            return;
        }
        if (!MenuItemUtil.isVisibleItem(event.getCurrentItem())) {
            return;
        }

        if (event.getRawSlot() == 45) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                player.closeInventory();
                player.performCommand("menu");
            });
            return;
        }

        if (event.getRawSlot() == 47) {
            Bukkit.getScheduler().runTask(plugin, () -> openBossMenu(player, !holder.despawnMode()));
            return;
        }

        if (event.getRawSlot() == 49) {
            BossActionResult result = despawnAllBosses();
            player.sendMessage(result.success() ? MessageUtil.success(result.message()) : MessageUtil.error(result.message()));
            Bukkit.getScheduler().runTask(plugin, () -> openBossMenu(player, holder.despawnMode()));
            return;
        }

        if (event.getRawSlot() == 53) {
            reconcileLoadedBosses();
            Bukkit.getScheduler().runTask(plugin, () -> openBossMenu(player, holder.despawnMode()));
            return;
        }

        BossType type = bossTypeForSlot(event.getRawSlot());
        if (type == null) {
            return;
        }

        if (holder.despawnMode()) {
            BossActionResult result = despawnBoss(type.id());
            player.sendMessage(result.success() ? MessageUtil.success(result.message()) : MessageUtil.error(result.message()));
        } else {
            BossActionResult result = spawnBoss(player, type.id());
            player.sendMessage(result.success() ? MessageUtil.success(result.message()) : MessageUtil.error(result.message()));
        }

        Bukkit.getScheduler().runTask(plugin, () -> openBossMenu(player, holder.despawnMode()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossMenuDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof BossMenuHolder)) {
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
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof BossRitualMenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return;
        }
        if (!MenuItemUtil.isVisibleItem(event.getCurrentItem())) {
            return;
        }
        if (rawSlot == 49) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                player.closeInventory();
                player.performCommand("menu");
            });
            return;
        }
        BossType type = ritualBossTypeForSlot(rawSlot);
        if (type != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    openBossDropPreviewMenu(player, type);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossDropPreviewMenuClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof BossDropPreviewMenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return;
        }
        if (!MenuItemUtil.isVisibleItem(event.getCurrentItem())) {
            return;
        }
        if (rawSlot == 40) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    openRitualMenu(player);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossRitualMenuDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof BossRitualMenuHolder)) {
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
    public void onBossDropPreviewMenuDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof BossDropPreviewMenuHolder)) {
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
        List<BossType> types = BossType.progressionOrder();
        for (int i = 0; i < types.size() && i < BOSS_SLOTS.length; i++) {
            if (BOSS_SLOTS[i] == rawSlot) {
                return types.get(i);
            }
        }
        return null;
    }

    private BossType ritualBossTypeForSlot(int rawSlot) {
        List<BossType> types = BossType.progressionOrder();
        for (int i = 0; i < types.size() && i < RITUAL_SLOTS.length; i++) {
            if (RITUAL_SLOTS[i] == rawSlot) {
                return types.get(i);
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
            case MORVESSA_THE_RUNEBLOOM_WITCH -> material == Material.BREWING_STAND
                || material == Material.AMETHYST_BLOCK
                || material == Material.FLOWERING_AZALEA_LEAVES;
            case NEREIDA_THE_ABYSS_MOTHER -> material == Material.CONDUIT
                || material == Material.PRISMARINE
                || material == Material.SEA_LANTERN;
            case IRON_SAINT -> material == Material.ANVIL
                || material == Material.SMITHING_TABLE
                || material == Material.IRON_BLOCK;
            case MIREWOOD_THE_ROOT_TYRANT -> material == Material.MANGROVE_ROOTS
                || material == Material.MOSS_BLOCK
                || material == Material.OAK_SAPLING;
            case CORRUPTED_OATHKEEPER -> material == Material.RESPAWN_ANCHOR
                || material == Material.CRYING_OBSIDIAN
                || material == Material.MAGMA_BLOCK
                || material == Material.SCULK_CATALYST;
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
            case MORVESSA_THE_RUNEBLOOM_WITCH -> {
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
            case CORRUPTED_OATHKEEPER -> {
                Block base = focus.getRelative(BlockFace.DOWN);
                blocks.add(base);
                addCardinalBlocks(blocks, base);
                addCornerBlocks(blocks, base);
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
            case MORVESSA_THE_RUNEBLOOM_WITCH -> {
                requireRelative(problems, focus, BlockFace.DOWN, Material.AMETHYST_BLOCK, "Amethyst Block beneath the Brewing Stand");
                requireCardinals(problems, focus.getRelative(BlockFace.DOWN), Material.FLOWERING_AZALEA_LEAVES, "Flowering Azalea Leaves around the Amethyst base");
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
            case CORRUPTED_OATHKEEPER -> {
                Block base = focus.getRelative(BlockFace.DOWN);
                requireRelative(problems, focus, BlockFace.DOWN, Material.CRYING_OBSIDIAN, "Crying Obsidian beneath the respawn anchor");
                requireCardinals(problems, base, Material.MAGMA_BLOCK, "Magma Blocks around the Crying Obsidian base");
                requireCorners(problems, base, Material.SCULK_CATALYST, "Sculk Catalysts on the four corners");
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
        playBossSpecificSpawnBurst(entity, type, center, fromRitual);
        beginBossEntranceAnimation(entity, type, fromRitual);
    }

    private void beginBossEntranceAnimation(LivingEntity entity, BossType type, boolean fromRitual) {
        UUID entityId = entity.getUniqueId();
        if (!bossEntranceAnimations.add(entityId)) {
            return;
        }
        telegraphingBosses.add(entityId);
        entity.setVelocity(new Vector());
        entity.addPotionEffect(new PotionEffect(
            PotionEffectType.SLOWNESS,
            BOSS_ENTRANCE_DURATION_TICKS + 6,
            10,
            false,
            false,
            false
        ));
        entity.addPotionEffect(new PotionEffect(
            PotionEffectType.GLOWING,
            BOSS_ENTRANCE_DURATION_TICKS + 6,
            0,
            false,
            false,
            false
        ));

        int[] beats = {8, 20, 32, BOSS_ENTRANCE_DURATION_TICKS};
        for (int i = 0; i < beats.length; i++) {
            int beat = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isActiveBossAnimationEntity(entity, type)) {
                    finishBossEntranceAnimation(entityId);
                    return;
                }

                BossRitual ritual = type.ritual();
                World world = entity.getWorld();
                double progress = beat / (double) (beats.length - 1);
                double bodyHeight = Math.min(2.2, Math.max(0.9, entity.getHeight() * 0.55));
                Location base = entity.getLocation().clone();
                Location center = base.clone().add(0.0, bodyHeight, 0.0);
                double contractingRadius = 4.2 - progress * 2.7;

                spawnRitualRing(base, contractingRadius, ritual.color(), 34 + beat * 6, 0.18 + progress * 0.7);
                spawnRitualRing(base, 1.2 + progress * 2.5, ritual.color(), 26 + beat * 4, bodyHeight);
                spawnRitualParticle(
                    world,
                    ritual.primaryParticle(),
                    center,
                    12 + beat * 5,
                    0.35 + progress * 0.55,
                    0.35 + progress * 0.4,
                    0.35 + progress * 0.55,
                    0.03,
                    ritual.color()
                );

                if (beat == 0) {
                    world.playSound(center, ritual.pulseSound(), 0.8f, 0.72f);
                } else if (beat == beats.length - 2) {
                    BossAttackVisual visual = bossAttackVisual(type);
                    world.playSound(center, visual.sound(), 0.85f, Math.max(0.45f, visual.pitch() - 0.12f));
                } else if (beat == beats.length - 1) {
                    spawnRitualParticle(world, Particle.FLASH, center, 1, 0.0, 0.0, 0.0, 0.0, ritual.color());
                    spawnRitualRing(base, fromRitual ? 5.4 : 4.2, ritual.color(), fromRitual ? 64 : 48, 0.25);
                    spawnRitualParticle(world, ritual.primaryParticle(), center, fromRitual ? 50 : 34, 1.15, 0.85, 1.15, 0.07, ritual.color());
                    world.playSound(center, ritual.arrivalSound(), fromRitual ? 1.55f : 1.2f, fromRitual ? 0.72f : 0.9f);
                    finishBossEntranceAnimation(entityId);
                }
            }, beats[i]);
        }
    }

    private boolean isActiveBossAnimationEntity(LivingEntity entity, BossType type) {
        if (!plugin.isEnabled() || entity == null || entity.isDead() || !entity.isValid()) {
            return false;
        }
        BossRecord record = bossRecord(entity);
        return record != null && type.id().equals(record.bossId());
    }

    private void finishBossEntranceAnimation(UUID entityId) {
        bossEntranceAnimations.remove(entityId);
        telegraphingBosses.remove(entityId);
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
            case MORVESSA_THE_RUNEBLOOM_WITCH -> {
                world.spawnParticle(Particle.WITCH, raised, 28, 0.82, 0.34, 0.82, 0.06);
                world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, raised, 20, 0.95, 0.28, 0.95, 0.04);
                spawnRitualRing(center, 0.90 + progress * 2.3, Color.fromRGB(120, 190, 45), 36, 0.18 + progress * 0.8);
                if (pulse % 2 == 0) {
                    world.playSound(center, Sound.BLOCK_BREWING_STAND_BREW, 0.6f, 0.8f + (float) progress * 0.25f);
                }
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
            case CORRUPTED_OATHKEEPER -> {
                world.spawnParticle(Particle.LAVA, raised, 18 + pulse * 2, 0.95, 0.34, 0.95, 0.07);
                world.spawnParticle(Particle.FLAME, raised, 20, 0.75, 0.28, 0.75, 0.04);
                world.spawnParticle(Particle.DUST, raised, 18, 0.62, 0.26, 0.62, 0.0, new Particle.DustOptions(Color.fromRGB(225, 48, 26), 1.35f));
                spawnRitualRing(center, 1.15 + progress * 2.8, Color.fromRGB(225, 48, 26), 42, 0.18 + progress * 0.9);
                if (pulse % 2 == 0) {
                    world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.6f, 0.52f);
                }
            }
        }
    }

    private void playBossSpecificSpawnBurst(LivingEntity entity, BossType type, Location center, boolean fromRitual) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        switch (type) {
            case YULE_THE_MINION -> {
                world.spawnParticle(Particle.CRIT, center, fromRitual ? 34 : 16, 1.35, 1.0, 1.35, 0.08);
                world.spawnParticle(Particle.ANGRY_VILLAGER, center.clone().add(0.0, 1.0, 0.0), fromRitual ? 28 : 12, 0.85, 0.45, 0.85, 0.02);
                world.playSound(center, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.25f, 0.7f);
                scheduleSpawnHelix(entity, type, center, Color.fromRGB(255, 184, 77), Color.fromRGB(190, 40, 28), Particle.CRIT, 2.2, 3.2, 18, 2);
            }
            case KAEL_THE_ASHEN -> {
                world.spawnParticle(Particle.ASH, center, fromRitual ? 90 : 38, 1.5, 0.95, 1.5, 0.05);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, fromRitual ? 54 : 24, 1.0, 0.75, 1.0, 0.04);
                world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.55f);
                scheduleSpawnHelix(entity, type, center, Color.fromRGB(185, 205, 225), Color.fromRGB(80, 120, 150), Particle.SOUL_FIRE_FLAME, 2.5, 3.4, 20, 3);
            }
            case VESPER_THE_WIDOW_QUEEN -> {
                world.spawnParticle(Particle.WITCH, center, fromRitual ? 70 : 30, 1.35, 0.8, 1.35, 0.06);
                world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, center.clone().add(0.0, 0.75, 0.0), fromRitual ? 90 : 36, 1.6, 0.55, 1.6, 0.05);
                world.playSound(center, Sound.ENTITY_SPIDER_DEATH, 1.15f, 0.55f);
                scheduleSpawnHelix(entity, type, center, Color.fromRGB(45, 220, 95), Color.fromRGB(95, 20, 130), Particle.WITCH, 2.35, 2.8, 18, 4);
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
                scheduleSpawnHelix(entity, type, center, Color.fromRGB(230, 35, 65), Color.fromRGB(15, 190, 205), Particle.SCULK_SOUL, 3.0, 4.1, 24, 4);
            }
            case AURELION_THE_RIFT_SERAPH -> {
                world.spawnParticle(Particle.PORTAL, center, fromRitual ? 160 : 70, 1.8, 1.2, 1.8, 0.65);
                world.spawnParticle(Particle.REVERSE_PORTAL, center.clone().add(0.0, 1.1, 0.0), fromRitual ? 80 : 32, 1.2, 0.75, 1.2, 0.12);
                world.playSound(center, Sound.ENTITY_ENDERMAN_SCREAM, 1.1f, 0.7f);
                scheduleSpawnHelix(entity, type, center, Color.fromRGB(190, 110, 255), Color.fromRGB(40, 20, 90), Particle.REVERSE_PORTAL, 2.8, 3.8, 22, 5);
            }
            case MORVESSA_THE_RUNEBLOOM_WITCH -> {
                world.spawnParticle(Particle.WITCH, center, fromRitual ? 150 : 64, 1.8, 1.0, 1.8, 0.10);
                world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, center.clone().add(0.0, 0.8, 0.0), fromRitual ? 120 : 48, 1.7, 0.65, 1.7, 0.08);
                world.playSound(center, Sound.ENTITY_WITCH_CELEBRATE, 1.25f, 0.68f);
                world.playSound(center, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.0f, 1.2f);
                scheduleSpawnHelix(entity, type, center, Color.fromRGB(125, 205, 55), Color.fromRGB(160, 65, 210), Particle.WITCH, 2.9, 3.9, 24, 5);
            }
            case NEREIDA_THE_ABYSS_MOTHER -> {
                world.spawnParticle(Particle.NAUTILUS, center, fromRitual ? 120 : 54, 1.5, 0.9, 1.5, 0.08);
                world.spawnParticle(Particle.SPLASH, center.clone().add(0.0, 0.75, 0.0), fromRitual ? 110 : 40, 1.7, 0.55, 1.7, 0.08);
                world.playSound(center, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.2f, 0.82f);
                scheduleSpawnHelix(entity, type, center, Color.fromRGB(40, 210, 240), Color.fromRGB(20, 80, 130), Particle.NAUTILUS, 2.5, 3.1, 20, 4);
            }
            case IRON_SAINT -> {
                world.spawnParticle(Particle.CRIT, center, fromRitual ? 90 : 34, 1.4, 0.75, 1.4, 0.08);
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center.clone().add(0.0, 0.8, 0.0), fromRitual ? 40 : 16, 1.0, 0.45, 1.0, 0.03);
                world.playSound(center, Sound.BLOCK_ANVIL_LAND, 1.6f, 0.55f);
                scheduleSpawnHelix(entity, type, center, Color.fromRGB(210, 210, 190), Color.fromRGB(235, 175, 60), Particle.CRIT, 2.7, 3.5, 22, 3);
            }
            case MIREWOOD_THE_ROOT_TYRANT -> {
                world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, center, fromRitual ? 120 : 46, 1.8, 0.75, 1.8, 0.08);
                world.spawnParticle(Particle.HAPPY_VILLAGER, center.clone().add(0.0, 1.0, 0.0), fromRitual ? 46 : 18, 1.0, 0.4, 1.0, 0.04);
                world.playSound(center, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.15f, 0.7f);
                scheduleSpawnHelix(entity, type, center, Color.fromRGB(70, 190, 70), Color.fromRGB(120, 75, 30), Particle.SPORE_BLOSSOM_AIR, 2.6, 3.2, 20, 4);
            }
            case CORRUPTED_OATHKEEPER -> {
                if (fromRitual) {
                    world.strikeLightningEffect(center);
                }
                world.spawnParticle(Particle.LAVA, center, fromRitual ? 150 : 70, 2.2, 1.2, 2.2, 0.18);
                world.spawnParticle(Particle.FLAME, center.clone().add(0.0, 1.1, 0.0), fromRitual ? 180 : 80, 2.0, 1.0, 2.0, 0.08);
                world.spawnParticle(Particle.REVERSE_PORTAL, center.clone().add(0.0, 1.3, 0.0), fromRitual ? 90 : 36, 1.5, 0.8, 1.5, 0.18);
                world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 1.55f, 0.55f);
                world.playSound(center, Sound.ENTITY_MAGMA_CUBE_SQUISH, 1.5f, 0.38f);
                scheduleSpawnHelix(entity, type, center, Color.fromRGB(245, 75, 28), Color.fromRGB(95, 15, 125), Particle.FLAME, 3.4, 5.5, 28, 5);
            }
        }
    }

    private void scheduleSpawnHelix(
        LivingEntity entity,
        BossType type,
        Location center,
        Color primary,
        Color secondary,
        Particle accent,
        double radius,
        double height,
        int ticks,
        int arms
    ) {
        int safeTicks = Math.max(1, ticks);
        int safeArms = Math.max(1, arms);
        int[] step = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (step[0] > safeTicks || !isActiveBossAnimationEntity(entity, type)) {
                task[0].cancel();
                return;
            }
            try {
                World world = entity.getWorld();
                double progress = step[0] / (double) safeTicks;
                double y = 0.15 + height * progress;
                for (int arm = 0; arm < safeArms; arm++) {
                    double angle = (progress * Math.PI * 4.0) + ((Math.PI * 2.0 * arm) / safeArms);
                    double curl = radius * (1.0 - progress * 0.55);
                    Location point = center.clone().add(Math.cos(angle) * curl, y, Math.sin(angle) * curl);
                    Color color = arm % 2 == 0 ? primary : secondary;
                    world.spawnParticle(Particle.DUST, point, 2, 0.02, 0.02, 0.02, 0.0, new Particle.DustOptions(color, 1.25f));
                    if (step[0] % 2 == 0) {
                        spawnRitualParticle(world, accent, point, 1, 0.04, 0.04, 0.04, 0.01, color);
                    }
                }
                step[0]++;
            } catch (RuntimeException ex) {
                task[0].cancel();
                plugin.getLogger().warning("Boss spawn helix particle failed: " + ex.getMessage());
            }
        }, 0L, 1L);
    }

    private void playBossAttackAnimation(
        LivingEntity attacker,
        LivingEntity target,
        BossType type,
        int phase,
        boolean projectileHit
    ) {
        long now = System.currentTimeMillis();
        long readyAt = bossAttackVisualCooldowns.getOrDefault(attacker.getUniqueId(), 0L);
        if (now < readyAt || attacker.getWorld() != target.getWorld()) {
            return;
        }
        bossAttackVisualCooldowns.put(attacker.getUniqueId(), now + BOSS_ATTACK_VISUAL_COOLDOWN_MS);

        BossAttackVisual visual = bossAttackVisual(type);
        World world = attacker.getWorld();
        Location from = attacker.getEyeLocation();
        Location to = target.getLocation().clone().add(0.0, Math.min(1.15, target.getHeight() * 0.55), 0.0);
        Vector path = to.toVector().subtract(from.toVector());
        double distance = path.length();
        if (distance > 0.01 && distance <= 32.0) {
            int points = Math.max(4, Math.min(11, (int) Math.ceil(distance * 1.25)));
            Vector step = path.multiply(1.0 / points);
            Particle.DustOptions dust = new Particle.DustOptions(type.ritual().color(), phase >= 2 ? 1.15f : 0.95f);
            for (int i = 1; i <= points; i++) {
                Location point = from.clone().add(step.clone().multiply(i));
                world.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, dust);
                if (i % 3 == 0) {
                    spawnRitualParticle(world, visual.accent(), point, 1, 0.03, 0.03, 0.03, 0.01, type.ritual().color());
                }
            }
        }

        spawnRitualParticle(
            world,
            visual.accent(),
            to,
            phase >= 2 ? 8 : 5,
            0.22,
            0.28,
            0.22,
            0.025,
            type.ritual().color()
        );
        if (!projectileHit) {
            world.spawnParticle(Particle.SWEEP_ATTACK, to, 1, 0.0, 0.0, 0.0, 0.0);
        }
        world.playSound(
            projectileHit ? to : attacker.getLocation(),
            visual.sound(),
            projectileHit ? 0.48f : 0.72f,
            Math.max(0.4f, visual.pitch() - (phase >= 2 ? 0.08f : 0.0f))
        );
    }

    private BossAttackVisual bossAttackVisual(BossType type) {
        return switch (type) {
            case YULE_THE_MINION -> new BossAttackVisual(Particle.CRIT, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.82f);
            case KAEL_THE_ASHEN -> new BossAttackVisual(Particle.SOUL_FIRE_FLAME, Sound.ENTITY_BLAZE_SHOOT, 0.92f);
            case VESPER_THE_WIDOW_QUEEN -> new BossAttackVisual(Particle.WITCH, Sound.ENTITY_SPIDER_STEP, 0.68f);
            case MIREWOOD_THE_ROOT_TYRANT -> new BossAttackVisual(Particle.SPORE_BLOSSOM_AIR, Sound.BLOCK_ROOTED_DIRT_BREAK, 0.62f);
            case NEREIDA_THE_ABYSS_MOTHER -> new BossAttackVisual(Particle.NAUTILUS, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.78f);
            case IRON_SAINT -> new BossAttackVisual(Particle.CRIT, Sound.BLOCK_ANVIL_LAND, 0.72f);
            case AURELION_THE_RIFT_SERAPH -> new BossAttackVisual(Particle.REVERSE_PORTAL, Sound.ENTITY_ENDERMAN_TELEPORT, 1.18f);
            case MORVESSA_THE_RUNEBLOOM_WITCH -> new BossAttackVisual(Particle.WITCH, Sound.BLOCK_BREWING_STAND_BREW, 0.72f);
            case VORALITH_THE_CRIMSON_WARDEN -> new BossAttackVisual(Particle.SCULK_SOUL, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.70f);
            case CORRUPTED_OATHKEEPER -> new BossAttackVisual(Particle.FLAME, Sound.ENTITY_BLAZE_SHOOT, 0.52f);
        };
    }

    private ItemStack createOverviewItem(boolean despawnMode) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Total tracked bosses:</gray> <white>" + trackedBosses.size() + "</white>");
        lore.add("<gray>Current mode:</gray> <white>" + (despawnMode ? "Despawn" : "Spawn") + "</white>");
        lore.add("<gray>Use the mode button before selecting a boss.</gray>");
        lore.add("<gray>Future boss definitions will appear here automatically.</gray>");
        return menuItem(Material.NETHER_STAR, "<gold><bold>Boss Console</bold></gold>", lore);
    }

    private ItemStack createBossActionModeItem(boolean despawnMode) {
        return menuItem(
            despawnMode ? Material.RED_CONCRETE : Material.LIME_CONCRETE,
            despawnMode ? "<red><bold>Mode: Despawn</bold></red>" : "<green><bold>Mode: Spawn</bold></green>",
            List.of(
                "<gray>Boss buttons will " + (despawnMode ? "remove every active copy." : "spawn one at your position.") + "</gray>",
                "<yellow>Tap or click to switch modes.</yellow>"
            )
        );
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

    private ItemStack createBossEntryItem(Player player, BossType type, boolean despawnMode) {
        List<String> lore = new ArrayList<>(type.description());
        BossMechanics.Signature signature = BossMechanics.signature(type.id());
        lore.add("<gold>Signature:</gold> <white>" + signature.displayName() + "</white>");
        lore.add("<gray>Counter:</gray> <white>" + signature.counterplay() + "</white>");
        addBossDangerLore(lore, type);
        if (!lore.isEmpty()) {
            lore.add("<dark_gray> ");
        }
        lore.add("<gray>Active:</gray> <white>" + activeCount(type.id()) + "</white>");
        lore.add("<gray>Progression:</gray> <white>Tier " + type.progressionTier() + " of " + BossType.values().length + "</white>");
        lore.add("<gray>Recommended:</gray> <white>" + type.recommendedGear() + "</white>");
        lore.add("<gray>Entity:</gray> <white>" + prettyBossName(type.entityType().name()) + "</white>");
        lore.add("<gray>Health:</gray> <red><bold>" + trimNumber(type.maxHealth()) + "</bold></red>");
        lore.add("<gray>Phases:</gray> <white>" + type.maxPhases() + "</white>");
        lore.add("<gray>Drops:</gray> <white>" + dropPreviewText(player, type) + "</white>");
        String worldRestriction = requiredEnvironmentLabel(type);
        if (worldRestriction != null) {
            lore.add("<gray>World:</gray> <white>" + worldRestriction + "</white>");
        }
        lore.add("<gray>Action:</gray> <white>" + (despawnMode ? "Despawn all copies" : "Spawn at your position") + "</white>");
        lore.add("<yellow>Tap or click to run this action.</yellow>");
        lore.add("<dark_gray>Use /bossrituals to view the survival summon ritual.</dark_gray>");
        return menuItem(type.menuIcon(), type.displayName(), lore);
    }

    private ItemStack createRitualEntryItem(Player player, BossType type) {
        BossRitual ritual = type.ritual();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + ritual.name() + "</gray>");
        lore.add("<gray>Progression:</gray> <white>Tier " + type.progressionTier() + " of " + BossType.values().length + "</white>");
        lore.add("<gray>Recommended:</gray> <white>" + type.recommendedGear() + "</white>");
        lore.add("<gray>Health:</gray> <red><bold>" + trimNumber(type.maxHealth()) + "</bold></red>");
        lore.add("<gray>Phases:</gray> <white>" + type.maxPhases() + "</white>");
        BossMechanics.Signature signature = BossMechanics.signature(type.id());
        lore.add("<gold>Signature:</gold> <white>" + signature.displayName() + "</white>");
        lore.add("<gray>Counter:</gray> <white>" + signature.counterplay() + "</white>");
        addBossDangerLore(lore, type);
        lore.add("<gray>Drops:</gray> <white>" + dropPreviewText(player, type) + "</white>");
        lore.add("<yellow>Click to view full drop table.</yellow>");
        lore.add("<dark_gray> ");
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

    private void addBossDangerLore(List<String> lore, BossType type) {
        if (type.progressionTier() < 2) {
            return;
        }
        int failurePercent = (int) Math.round(BossBalance.mechanicFailureHealthRatio(type.progressionTier()) * 100.0);
        lore.add("<red>Failed mechanic:</red> <white>" + failurePercent + "% max health</white>");
        if (type.progressionTier() >= 5) {
            lore.add("<dark_red>Some casts seal all healing.</dark_red>");
        }
    }

    private void openBossDropPreviewMenu(Player player, BossType type) {
        Inventory inventory = Bukkit.createInventory(
            new BossDropPreviewMenuHolder(type),
            45,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#f97316:#dc2626><bold>" + type.plainDisplayName() + " Drops</bold></gradient>"), "Boss Drops")
        );
        ItemStack filler = menuItem(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isFrameSlot(slot, inventory.getSize())) {
                inventory.setItem(slot, filler);
            }
        }

        inventory.setItem(4, menuItem(type.menuIcon(), type.displayName(), List.of(
            "<gray>Health:</gray> <red><bold>" + trimNumber(type.maxHealth()) + "</bold></red>",
            "<gray>Phases:</gray> <white>" + type.maxPhases() + "</white>",
            "<gray>Ritual:</gray> <white>" + type.ritual().name() + "</white>",
            "<dark_gray>Preview only. Items cannot be taken.</dark_gray>"
        )));
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 20, 21, 22, 23, 24};
        List<DropPreview> drops = dropPreviews(type);
        for (int i = 0; i < drops.size() && i < slots.length; i++) {
            inventory.setItem(slots[i], dropPreviewItem(player, drops.get(i)));
        }
        inventory.setItem(40, menuItem(Material.ARROW, "<yellow><bold>Back</bold></yellow>", List.of("<gray>Return to boss rituals.</gray>")));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.45f, 1.25f);
    }

    private ItemStack dropPreviewItem(Player player, DropPreview preview) {
        if (isHiddenRareDrop(player, preview)) {
            boolean soulImprint = SeasonRelicManager.SOUL_IMPRINT_ID.equals(preview.relicId());
            ItemStack hidden = new ItemStack(soulImprint ? Material.END_CRYSTAL : Material.GRAY_DYE);
            ItemMeta meta = hidden.getItemMeta();
            if (meta != null) {
                String hiddenName = soulImprint
                    ? "<light_purple><bold>" + soulImprintName(player) + "</bold></light_purple>"
                    : "<dark_gray><bold>Unknown Drop</bold></dark_gray>";
                meta.displayName(MM.deserialize(hiddenName));
                meta.lore(List.of(
                    MM.deserialize("<gold><bold>Boss Drop</bold></gold>"),
                    MM.deserialize("<gray>Chance:</gray> <white>" + formatPercent(preview.chance()) + "</white>"),
                    MM.deserialize("<gray>Hold it once to reveal its name.</gray>"),
                    MM.deserialize("<dark_gray>Preview only.</dark_gray>")
                ));
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                hidden.setItemMeta(meta);
            }
            return hidden;
        }
        ItemStack item = dropPreviewBaseItem(preview);
        if (item == null || item.getType().isAir()) {
            item = new ItemStack(preview.icon());
        }
        item.setAmount(Math.max(1, Math.min(preview.amount(), item.getMaxStackSize())));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (!meta.hasDisplayName()) {
                meta.displayName(Component.text(preview.name(), net.kyori.adventure.text.format.NamedTextColor.GOLD)
                    .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            }
            List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            if (!lore.isEmpty()) {
                lore.add(Component.empty());
            }
            lore.add(MM.deserialize("<gold><bold>Boss Drop</bold></gold>"));
            lore.add(MM.deserialize("<gray>" + preview.note() + "</gray>"));
            lore.add(MM.deserialize("<dark_gray>Preview only.</dark_gray>"));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack dropPreviewBaseItem(DropPreview preview) {
        if (preview == null) {
            return null;
        }
        if (preview.relicId() != null && plugin.getSeasonRelicManager() != null) {
            return plugin.getSeasonRelicManager().createRelicItem(preview.relicId());
        }
        if ("Awakening Table".equals(preview.name()) && plugin.getAwakeningTableListener() != null) {
            return plugin.getAwakeningTableListener().createAwakeningTableItem();
        }
        if ("Veil Core".equals(preview.name())) {
            return createDominionCoreItem();
        }
        return null;
    }

    private boolean isHiddenRareDrop(Player player, DropPreview preview) {
        return player != null
            && preview != null
            && preview.relicId() != null
            && preview.chance() > 0.0D
            && preview.chance() < 0.01D
            && plugin.getSeasonRelicManager() != null
            && (SeasonRelicManager.SOUL_IMPRINT_ID.equals(preview.relicId())
                ? !plugin.getSeasonRelicManager().hasHeldSoulImprint(player)
                : !plugin.getSeasonRelicManager().hasRelicDiscovered(player, preview.relicId()));
    }

    private String dropPreviewText(Player player, BossType type) {
        List<DropPreview> drops = dropPreviews(type);
        if (drops.isEmpty()) {
            return "No configured drops";
        }
        List<String> names = new ArrayList<>();
        for (int i = 0; i < Math.min(3, drops.size()); i++) {
            DropPreview preview = drops.get(i);
            names.add(SeasonRelicManager.SOUL_IMPRINT_ID.equals(preview.relicId())
                ? soulImprintName(player)
                : preview.name());
        }
        if (drops.size() > names.size()) {
            names.add("more");
        }
        return String.join(", ", names);
    }

    private String soulImprintName(Player player) {
        return plugin.getSeasonRelicManager() == null
            ? "<obfuscated>Soul Imprint</obfuscated>"
            : plugin.getSeasonRelicManager().soulImprintDisplayName(player);
    }

    private List<DropPreview> dropPreviews(BossType type) {
        if (type == null) {
            return List.of();
        }
        List<DropPreview> drops = new ArrayList<>();
        switch (type) {
            case YULE_THE_MINION -> {
                addRelicDrop(drops, "gilded_skull", 2, "Guaranteed Veiled Skulls.");
                addRelicDrop(drops, "oathbound_plate", 1, "Guaranteed Veilmarked Plate.");
            }
            case KAEL_THE_ASHEN -> {
                addRelicDrop(drops, "solar_ember", 4, "Guaranteed Cinderveil Embers.");
                addRelicDrop(drops, "titan_gear", 1, "30% chance.");
            }
            case VESPER_THE_WIDOW_QUEEN -> {
                addRelicDrop(drops, "widow_silk", 4, "Guaranteed Gloam Silk.");
                addRelicDrop(drops, "verdant_heart", 1, "35% chance.");
            }
            case VORALITH_THE_CRIMSON_WARDEN -> {
                drops.add(new DropPreview(Material.ECHO_SHARD, "Veil Core", "Guaranteed repair core.", null, 1, 1.0D));
                addRelicDrop(drops, "crimson_rib", 4, "Guaranteed Nocturne Ribs.");
                addRelicDrop(drops, "sculk_heart", 1, "Guaranteed Veil Heart.");
            }
            case AURELION_THE_RIFT_SERAPH -> {
                addRelicDrop(drops, "rift_lens", 4, "Guaranteed Riftglass Lenses.");
                addRelicDrop(drops, "void_halo", 1, "35% chance.");
                addRelicDrop(drops, "awakening_shard", 1, formatPercent(plugin.getConfigManager().awakeningTableRiftSeraphShardDropChance) + " chance.");
                drops.add(new DropPreview(
                    Material.ENCHANTING_TABLE,
                    "Awakening Table",
                    formatPercent(plugin.getConfigManager().awakeningTableRiftSeraphDropChance) + " chance.",
                    null,
                    1,
                    plugin.getConfigManager().awakeningTableRiftSeraphDropChance
                ));
            }
            case MORVESSA_THE_RUNEBLOOM_WITCH -> {
                addRelicDrop(drops, "rift_lens", 2, "Guaranteed Riftglass Lenses.");
                addRelicDrop(drops, "runebloom_orb", 1, formatPercent(BossBalance.RUNEBLOOM_WITCH_ORB_DROP_CHANCE) + " chance.");
            }
            case NEREIDA_THE_ABYSS_MOTHER -> {
                addRelicDrop(drops, "abyssal_pearl", 4, "Guaranteed Depthveil Pearls.");
                addRelicDrop(drops, "tideheart", 1, "40% chance.");
            }
            case IRON_SAINT -> {
                addRelicDrop(drops, "titan_gear", 4, "Guaranteed Argent Gears.");
                addRelicDrop(drops, "saint_alloy", 1, "40% chance.");
            }
            case MIREWOOD_THE_ROOT_TYRANT -> {
                addRelicDrop(drops, "living_bark", 4, "Guaranteed Briarwake Bark.");
                addRelicDrop(drops, "verdant_heart", 1, "40% chance.");
            }
            case CORRUPTED_OATHKEEPER -> {
                addRelicDrop(drops, "corrupted_essence", 2, "2 guaranteed, with a 25% chance for a third.");
                addRelicDrop(drops, "soul_imprint", 1, "0.5% chance.");
            }
        }
        return drops;
    }

    private void addRelicDrop(List<DropPreview> drops, String relicId, int amount, String note) {
        Material icon = Material.CHEST;
        String name = prettyBossName(relicId);
        if (plugin.getSeasonRelicManager() != null) {
            ItemStack relic = plugin.getSeasonRelicManager().createRelicItem(relicId);
            if (relic != null && !relic.getType().isAir()) {
                icon = relic.getType();
            }
            String display = plugin.getSeasonRelicManager().displayNameFor(relicId);
            if (display != null && !display.isBlank()) {
                name = display;
            }
        }
        drops.add(new DropPreview(icon, name, note, relicId, amount, chanceFromDropNote(note)));
    }

    private double chanceFromDropNote(String note) {
        if (note == null || note.isBlank()) {
            return 0.0D;
        }
        String lower = note.toLowerCase(Locale.ROOT);
        if (lower.startsWith("guaranteed")) {
            return 1.0D;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)%").matcher(note);
        if (!matcher.find()) {
            return 0.0D;
        }
        try {
            return Math.max(0.0D, Double.parseDouble(matcher.group(1)) / 100.0D);
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
    }

    private ItemStack menuItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        List<String> visibleLore = MenuItemUtil.visibleMiniLore(name, loreLines);
        meta.displayName(MM.deserialize(MenuItemUtil.visibleMiniName(name)));
        if (!visibleLore.isEmpty()) {
            List<Component> lore = new ArrayList<>(visibleLore.size());
            for (String line : visibleLore) {
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
        pdc.set(keyBossStoryLowHealth, PersistentDataType.BYTE, (byte) 0);
        pdc.set(keyBossDisplayMaxHealth, PersistentDataType.DOUBLE, type.maxHealth());
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
        if (plugin.getBossDungeonManager() != null && plugin.getBossDungeonManager().isDungeonWorld(world)) {
            return true;
        }
        World.Environment required = requiredEnvironment(type);
        return required == null || (world != null && world.getEnvironment() == required);
    }

    private String spawnRestrictionMessage(BossType type, World world) {
        if (plugin.getBossDungeonManager() != null && plugin.getBossDungeonManager().isDungeonWorld(world)) {
            return null;
        }
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

        double healthScale = BossBalance.multiplayerHealthScale(type.progressionTier(), nearbyPlayers);
        double damageScale = BossBalance.multiplayerDamageScale(type.progressionTier(), nearbyPlayers);

        double oldMax = actualBossMaxHealth(entity, type);
        double healthRatio = Math.max(0.01, Math.min(1.0, entity.getHealth() / oldMax));
        double scaledDisplayMaxHealth = Math.max(type.maxHealth(), type.maxHealth() * healthScale);
        entity.getPersistentDataContainer().set(keyBossDisplayMaxHealth, PersistentDataType.DOUBLE, scaledDisplayMaxHealth);
        setAttributeBase(entity, Attribute.MAX_HEALTH, scaledDisplayMaxHealth);
        AttributeInstance updatedHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        double nextMax = updatedHealth == null ? scaledDisplayMaxHealth : Math.max(1.0, updatedHealth.getValue());
        entity.setHealth(Math.max(1.0, Math.min(nextMax, nextMax * healthRatio)));

        setAttributeBase(entity, Attribute.ATTACK_DAMAGE, type.attackDamage() * damageScale);
        entity.getPersistentDataContainer().set(keyBossScaledPlayerCount, PersistentDataType.INTEGER, nearbyPlayers);
        BossFightState state = bossFightStates.get(entity.getUniqueId());
        if (state != null) {
            state.observeScaledPlayers(nearbyPlayers);
        }

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

    private double actualBossMaxHealth(LivingEntity entity, BossType type) {
        AttributeInstance health = entity == null ? null : entity.getAttribute(Attribute.MAX_HEALTH);
        return Math.max(1.0, health == null ? type.maxHealth() : health.getValue());
    }

    private double displayBossMaxHealth(LivingEntity entity, BossType type) {
        Double displayMax = entity.getPersistentDataContainer().get(keyBossDisplayMaxHealth, PersistentDataType.DOUBLE);
        return Math.max(1.0, displayMax == null ? type.maxHealth() : displayMax);
    }

    private double displayBossHealth(LivingEntity entity, BossType type) {
        double actualMax = actualBossMaxHealth(entity, type);
        double displayMax = displayBossMaxHealth(entity, type);
        double ratio = Math.max(0.0, Math.min(1.0, entity.getHealth() / actualMax));
        return Math.max(0.0, Math.min(displayMax, displayMax * ratio));
    }

    private void scaleBossIncomingDamage(LivingEntity entity, BossType type, EntityDamageEvent event) {
        double actualMax = actualBossMaxHealth(entity, type);
        double displayMax = displayBossMaxHealth(entity, type);
        if (displayMax <= actualMax + 0.001) {
            return;
        }

        double finalDamage = event.getFinalDamage();
        double baseDamage = event.getDamage();
        if (finalDamage <= 0.0 || baseDamage <= 0.0) {
            return;
        }

        double scaledBaseDamage = baseDamage * (actualMax / displayMax);
        if (!Double.isFinite(scaledBaseDamage)) {
            return;
        }
        event.setDamage(Math.max(0.0, scaledBaseDamage));
    }

    private void applyBossWardDamageBonus(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntityEvent)) {
            return;
        }

        Player attacker = attackingPlayer(byEntityEvent.getDamager());
        if (attacker == null) {
            return;
        }

        PriestManager priestManager = plugin.getPriestManager();
        if (priestManager == null) {
            return;
        }

        double multiplier = priestManager.bossWardDamageMultiplier(attacker);
        double damage = event.getDamage();
        if (multiplier <= 1.0D || damage <= 0.0D) {
            return;
        }

        double boostedDamage = damage * multiplier;
        if (Double.isFinite(boostedDamage)) {
            event.setDamage(boostedDamage);
        }
    }

    private void updateBossBar(LivingEntity entity, BossType type) {
        BossBar bossBar = bossBars.get(entity.getUniqueId());
        if (bossBar == null) {
            return;
        }

        int phase = bossPhase(entity);
        int scaledPlayers = entity.getPersistentDataContainer().getOrDefault(keyBossScaledPlayerCount, PersistentDataType.INTEGER, 1);
        String rage = scaledPlayers > 1 ? " | Rage +" + Math.min(BOSS_SCALE_MAX_EXTRA_PLAYERS, scaledPlayers - 1) : "";
        double maxHealth = displayBossMaxHealth(entity, type);
        double health = displayBossHealth(entity, type);
        entity.customName(MM.deserialize(type.displayName()
            + " <dark_gray>|</dark_gray> <red><bold>"
            + trimNumber(health)
            + "/"
            + trimNumber(maxHealth)
            + " HP</bold></red>"));
        entity.setCustomNameVisible(false);
        bossBar.setTitle(type.plainDisplayName() + " | Phase " + phase + " | HP " + trimNumber(health) + "/" + trimNumber(maxHealth) + rage);
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, health / maxHealth)));
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
        entity.setCustomNameVisible(false);
        int phase = bossPhase(entity);
        double maxHealth = displayBossMaxHealth(entity, type);
        double health = displayBossHealth(entity, type);
        display.text(MM.deserialize(
            "<gray>Phase <white>" + phase + "</white> <dark_gray>|</dark_gray> HP <white>"
                + trimNumber(health) + "/" + trimNumber(maxHealth) + "</white></gray>"
        ));
    }

    private void destroyBossVisuals(UUID bossId) {
        bossArenas.remove(bossId);
        bossArenaPlayers.remove(bossId);
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

    private void maybeSendStoryLowHealthLine(LivingEntity entity, BossType type) {
        if (entity.getPersistentDataContainer().getOrDefault(keyBossStoryLowHealth, PersistentDataType.BYTE, (byte) 0) != 0) {
            return;
        }
        AttributeInstance healthAttribute = entity.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = healthAttribute == null ? entity.getHealth() : healthAttribute.getValue();
        double threshold = plugin.getStoryService() == null ? 0.20 : plugin.getStoryService().bossLowHealthThreshold();
        if (maxHealth <= 0.0 || entity.getHealth() / maxHealth > threshold) {
            return;
        }
        entity.getPersistentDataContainer().set(keyBossStoryLowHealth, PersistentDataType.BYTE, (byte) 1);
        if (plugin.getStoryService() == null) {
            return;
        }
        String line = plugin.getStoryService().bossLowHealth(type.id());
        if (!line.isBlank()) {
            sendBossLine(entity, type, line);
            scheduleNextBossDialogue(entity, 10_000L, 16_000L);
        }
    }

    private void setBossPhase(LivingEntity entity, int phase) {
        int previous = bossPhase(entity);
        int next = Math.max(1, phase);
        entity.getPersistentDataContainer().set(keyBossPhase, PersistentDataType.INTEGER, next);
        if (next <= previous) {
            return;
        }
        BossRecord record = bossRecord(entity);
        BossType type = record == null ? null : BossType.fromId(record.bossId());
        if (type == null) {
            return;
        }
        playBossPhaseAnimation(entity, type, next);
        String fallback = BossDialogue.profile(type.id()).phaseLine(next);
        String line = plugin.getStoryService() == null ? fallback : plugin.getStoryService().bossPhase(type.id(), next, fallback);
        if (!line.isBlank()) {
            sendBossLine(entity, type, line);
            scheduleNextBossDialogue(entity, 12_000L, 18_000L);
        }
    }

    private void playBossPhaseAnimation(LivingEntity entity, BossType type, int phase) {
        int[] beats = {5, 13, 22};
        for (int i = 0; i < beats.length; i++) {
            int beat = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isActiveBossAnimationEntity(entity, type) || bossPhase(entity) != phase) {
                    return;
                }
                BossRitual ritual = type.ritual();
                World world = entity.getWorld();
                double progress = beat / (double) (beats.length - 1);
                double height = Math.min(2.6, Math.max(1.0, entity.getHeight() * 0.58));
                Location base = entity.getLocation().clone();
                Location center = base.clone().add(0.0, height, 0.0);

                spawnRitualRing(base, 1.8 + progress * 3.0, ritual.color(), 32 + beat * 10, 0.25 + progress * 0.55);
                spawnRitualRing(base, 3.8 - progress * 1.7, ritual.color(), 28 + beat * 8, height);
                spawnRitualParticle(
                    world,
                    ritual.primaryParticle(),
                    center,
                    14 + beat * 9,
                    0.45 + progress * 0.45,
                    0.4,
                    0.45 + progress * 0.45,
                    0.04,
                    ritual.color()
                );
                if (beat == beats.length - 1) {
                    BossAttackVisual visual = bossAttackVisual(type);
                    spawnRitualParticle(world, Particle.FLASH, center, 1, 0.0, 0.0, 0.0, 0.0, ritual.color());
                    spawnRitualParticle(world, visual.accent(), center, 24 + phase * 5, 0.85, 0.6, 0.85, 0.06, ritual.color());
                    world.playSound(center, ritual.arrivalSound(), 1.05f, Math.max(0.45f, 0.92f - (phase - 2) * 0.10f));
                }
            }, beats[i]);
        }
    }

    private long bossCooldownAt(LivingEntity entity, NamespacedKey key) {
        return entity.getPersistentDataContainer().getOrDefault(key, PersistentDataType.LONG, 0L);
    }

    private boolean bossCooldownReady(LivingEntity entity, NamespacedKey key) {
        return System.currentTimeMillis() >= bossCooldownAt(entity, key);
    }

    private void setBossCooldown(LivingEntity entity, NamespacedKey key, long cooldownMs) {
        long now = System.currentTimeMillis();
        long readyAt = Math.max(now, now + scaledBossCooldownMs(entity, cooldownMs));
        entity.getPersistentDataContainer().set(key, PersistentDataType.LONG, readyAt);
    }

    private int scaledBossPlayerCount(LivingEntity entity) {
        return Math.max(1, entity.getPersistentDataContainer().getOrDefault(keyBossScaledPlayerCount, PersistentDataType.INTEGER, 1));
    }

    private int scaledBossExtraPlayers(LivingEntity entity) {
        return Math.min(BOSS_SCALE_MAX_EXTRA_PLAYERS, Math.max(0, scaledBossPlayerCount(entity) - 1));
    }

    private double scaledBossAbilityDamage(LivingEntity entity, double baseDamage) {
        BossRecord record = bossRecord(entity);
        BossType type = record == null ? null : BossType.fromId(record.bossId());
        int tier = type == null ? 1 : type.progressionTier();
        return baseDamage
            * BossBalance.routineAbilityDamageScale(tier)
            * BossBalance.multiplayerDamageScale(tier, scaledBossPlayerCount(entity));
    }

    private double scaledBossSummonHealth(LivingEntity entity, double baseHealth) {
        BossRecord record = bossRecord(entity);
        BossType type = record == null ? null : BossType.fromId(record.bossId());
        int tier = type == null ? 1 : type.progressionTier();
        return baseHealth * BossBalance.multiplayerDamageScale(tier, scaledBossPlayerCount(entity));
    }

    private double scaledBossAbilityRadius(LivingEntity entity, double baseRadius) {
        return baseRadius + scaledBossExtraPlayers(entity) * BOSS_SCALE_RADIUS_PER_EXTRA_PLAYER;
    }

    private long scaledBossCooldownMs(LivingEntity entity, long baseCooldownMs) {
        long clampedBase = Math.max(0L, baseCooldownMs);
        if (clampedBase == 0L) {
            return 0L;
        }
        double multiplier = Math.max(
            BOSS_SCALE_MIN_COOLDOWN_MULTIPLIER,
            1.0 - scaledBossExtraPlayers(entity) * BOSS_SCALE_COOLDOWN_REDUCTION_PER_EXTRA_PLAYER
        );
        return Math.max(1L, Math.round(clampedBase * multiplier));
    }

    private int scaledBossMinionCount(LivingEntity entity, int baseCount) {
        return Math.max(1, baseCount + Math.min(3, scaledBossExtraPlayers(entity) / 2));
    }

    private boolean telegraphAreaAbility(
        LivingEntity boss,
        String warning,
        double radius,
        long delayTicks,
        Color color,
        Runnable ability
    ) {
        if (boss == null || boss.isDead() || !boss.isValid() || ability == null
            || activeBossMechanics.containsKey(boss.getUniqueId())
            || !telegraphingBosses.add(boss.getUniqueId())) {
            return false;
        }

        double safeRadius = Math.max(1.0, radius);
        Location center = boss.getLocation().clone().add(0.0, 0.18, 0.0);
        World world = boss.getWorld();
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.15f);
        int points = Math.max(32, (int) Math.ceil(safeRadius * 6.0));
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            Location point = center.clone().add(Math.cos(angle) * safeRadius, 0.0, Math.sin(angle) * safeRadius);
            world.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, dust);
        }
        world.spawnParticle(Particle.SMOKE, center.clone().add(0.0, 0.8, 0.0), 22, safeRadius * 0.18, 0.25, safeRadius * 0.18, 0.015);
        world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.1f, 0.65f);
        boss.setVelocity(new Vector());
        boss.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) Math.max(5L, delayTicks + 5L), 10, false, false, false));

        double warningRadiusSquared = (safeRadius + 6.0) * (safeRadius + 6.0);
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(center) <= warningRadiusSquared) {
                player.sendActionBar(MM.deserialize("<red><bold>" + escapeMiniMessage(warning) + "</bold></red>"));
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            telegraphingBosses.remove(boss.getUniqueId());
            if (!plugin.isEnabled() || boss.isDead() || !boss.isValid() || bossRecord(boss) == null) {
                return;
            }
            try {
                ability.run();
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Boss ability failed after telegraph: " + ex.getMessage());
            }
        }, Math.max(1L, delayTicks));
        return true;
    }

    private LivingEntity currentBossTarget(LivingEntity entity) {
        if (!(entity instanceof Mob mob)) {
            return null;
        }
        LivingEntity target = mob.getTarget();
        BossRecord record = bossRecord(entity);
        BossType type = record == null ? null : BossType.fromId(record.bossId());
        if (!(target instanceof Player player)
            || type == null
            || !isFightEligiblePlayer(player)
            || !isPlayerInFightArea(player, entity, type)) {
            if (target != null) {
                mob.setTarget(null);
            }
            return null;
        }
        return player;
    }

    private void tickBossBehavior(LivingEntity entity, BossType type) {
        if (tickActiveBossMechanic(entity, type)) {
            return;
        }
        if (telegraphingBosses.contains(entity.getUniqueId())) {
            return;
        }
        switch (type) {
            case YULE_THE_MINION -> tickYuleTheMinion(entity);
            case KAEL_THE_ASHEN -> tickKaelTheAshen(entity);
            case VESPER_THE_WIDOW_QUEEN -> tickVesperTheWidowQueen(entity);
            case VORALITH_THE_CRIMSON_WARDEN -> tickVoralithTheCrimsonWarden(entity);
            case AURELION_THE_RIFT_SERAPH -> tickAurelionTheRiftSeraph(entity);
            case MORVESSA_THE_RUNEBLOOM_WITCH -> tickMorvessaTheRunebloomWitch(entity);
            case NEREIDA_THE_ABYSS_MOTHER -> tickNereidaTheAbyssMother(entity);
            case IRON_SAINT -> tickIronSaint(entity);
            case MIREWOOD_THE_ROOT_TYRANT -> tickMirewoodTheRootTyrant(entity);
            case CORRUPTED_OATHKEEPER -> tickCorruptedOathkeeper(entity);
        }
    }

    private boolean tickActiveBossMechanic(LivingEntity boss, BossType type) {
        ActiveBossMechanic mechanic = activeBossMechanics.get(boss.getUniqueId());
        if (mechanic == null) {
            return false;
        }
        if (mechanic.origin.getWorld() == null || mechanic.origin.getWorld() != boss.getWorld()) {
            activeBossMechanics.remove(boss.getUniqueId(), mechanic);
            return false;
        }
        if (eligibleMechanicPlayers(boss, type, mechanic).isEmpty()) {
            activeBossMechanics.remove(boss.getUniqueId(), mechanic);
            return false;
        }

        long now = System.currentTimeMillis();
        if (BossMechanics.isMechanicStale(now, mechanic.expiresAt, 2_000L)) {
            activeBossMechanics.remove(boss.getUniqueId(), mechanic);
            return false;
        }
        slowBossForMechanic(boss, mechanic.kind);
        enforceHealingSuppression(boss, type, mechanic);
        boolean complete;
        try {
            complete = switch (mechanic.kind) {
                case MARSHAL_STACK -> tickMarshalStack(boss, mechanic, now);
                case ASHEN_CROSSFIRE -> tickAshenCrossfire(boss, mechanic, now);
                case ASHEN_DEADEYE -> tickAshenDeadeye(boss, mechanic, now);
                case WIDOWS_TRAIL -> tickWidowsTrail(boss, mechanic, now);
                case WIDOWS_WEBBREAK -> tickWidowsWebbreak(boss, mechanic, now);
                case ROOT_WARDS -> tickRootWards(boss, mechanic, now);
                case BRIAR_LATTICE -> tickBriarLattice(boss, mechanic, now);
                case UNDERTOW -> tickUndertow(boss, mechanic, now);
                case TIDAL_DIVIDE -> tickTidalDivide(boss, mechanic, now);
                case SAINTS_STAGGER -> tickSaintsStagger(boss, mechanic, now);
                case IRON_COUNTERSTANCE -> tickIronCounterstance(boss, mechanic, now);
                case RIFT_SECTORS -> tickRiftSectors(boss, mechanic, now);
                case RUNEBLOOM_SIGILS -> tickRunebloomSigils(boss, mechanic, now);
                case PETALSTORM -> tickPetalstorm(boss, mechanic, now);
                case RESONANCE_LOCK -> tickResonanceLock(boss, mechanic, now);
                case OATH_RINGS -> tickOathRings(boss, mechanic, now);
            };
        } catch (RuntimeException ex) {
            activeBossMechanics.remove(boss.getUniqueId(), mechanic);
            plugin.getLogger().log(
                java.util.logging.Level.SEVERE,
                "Cancelled failed " + mechanic.kind + " mechanic for " + type.id() + ".",
                ex
            );
            return true;
        }
        if (complete) {
            activeBossMechanics.remove(boss.getUniqueId(), mechanic);
        }
        return true;
    }

    private boolean beginBossMechanic(
        LivingEntity boss,
        ActiveBossMechanic mechanic,
        NamespacedKey cooldownKey,
        long cooldownMs,
        String warning,
        Sound sound,
        float pitch
    ) {
        if (boss == null || boss.isDead() || !boss.isValid()
            || telegraphingBosses.contains(boss.getUniqueId())) {
            return false;
        }
        BossRecord record = bossRecord(boss);
        BossType type = record == null ? null : BossType.fromId(record.bossId());
        if (type == null) {
            return false;
        }
        List<Player> participants = eligibleBossPlayers(boss, type);
        if (participants.isEmpty() || activeBossMechanics.putIfAbsent(boss.getUniqueId(), mechanic) != null) {
            return false;
        }
        participants.forEach(player -> mechanic.participants.add(player.getUniqueId()));
        setBossCooldown(boss, cooldownKey, cooldownMs);
        slowBossForMechanic(boss, mechanic.kind);
        initializeHealingSuppression(boss, mechanic);
        announceMechanicStart(boss, mechanic, warning, sound, pitch);
        return true;
    }

    private void announceMechanicStart(
        LivingEntity boss,
        ActiveBossMechanic mechanic,
        String warning,
        Sound sound,
        float pitch
    ) {
        BossRecord record = bossRecord(boss);
        BossType type = record == null ? null : BossType.fromId(record.bossId());
        if (type == null) {
            return;
        }

        MechanicNotice notice = mechanicNotice(mechanic.kind);
        Component actionBar = MM.deserialize(warning);
        Component title = MM.deserialize("<gold><bold>" + notice.title() + "</bold></gold>");
        Component subtitle = MM.deserialize("<white>" + notice.subtitle() + "</white>");
        Component explanation = MM.deserialize(
            "<gold><bold>MECHANIC: " + notice.title() + "</bold></gold> <white>" + notice.instruction() + "</white>"
        );
        String consequence = mechanicConsequence(mechanic.kind, type.progressionTier());
        BossMechanics.SuccessReward successReward = BossMechanics.successReward(mechanic.kind.name(), type.progressionTier());
        Component stakes = MM.deserialize(
            "<red>" + consequence + "</red>"
                + (suppressesHealing(mechanic.kind, mechanic.phase)
                    ? " <dark_red><bold>Healing is sealed.</bold></dark_red>"
                    : "")
                + (successReward == BossMechanics.SuccessReward.NONE
                    ? ""
                    : " <green>" + successReward.description() + "</green>")
        );
        Title.Times times = Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1_650), Duration.ofMillis(300));
        for (Player player : eligibleMechanicPlayers(boss, type, mechanic)) {
            player.sendActionBar(actionBar);
            player.showTitle(Title.title(title, subtitle, times));
            player.sendMessage(explanation);
            player.sendMessage(stakes);
            player.playSound(player.getLocation(), sound, 1.15f, pitch);
        }
    }

    private void announceMechanicUpdate(
        LivingEntity boss,
        String title,
        String subtitle,
        String actionBar,
        Sound sound,
        float pitch
    ) {
        BossRecord record = bossRecord(boss);
        BossType type = record == null ? null : BossType.fromId(record.bossId());
        if (type == null) {
            return;
        }
        Component titleComponent = MM.deserialize(title);
        Component subtitleComponent = MM.deserialize(subtitle);
        Component actionBarComponent = MM.deserialize(actionBar);
        Title.Times times = Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1_250), Duration.ofMillis(250));
        ActiveBossMechanic mechanic = activeBossMechanics.get(boss.getUniqueId());
        for (Player player : eligibleMechanicPlayers(boss, type, mechanic)) {
            player.sendActionBar(actionBarComponent);
            player.showTitle(Title.title(titleComponent, subtitleComponent, times));
            player.sendMessage(MM.deserialize(title + " <white>" + MiniMessage.miniMessage().stripTags(subtitle) + "</white>"));
            player.playSound(player.getLocation(), sound, 1.0f, pitch);
        }
    }

    private MechanicNotice mechanicNotice(BossMechanicKind kind) {
        return switch (kind) {
            case MARSHAL_STACK -> new MechanicNotice("HOLD THE LINE", "Stack on the blue-marked player.", "Stack on blue");
            case ASHEN_CROSSFIRE -> new MechanicNotice("ASHEN CROSSFIRE", "Spread red markers away from the group.", "Spread apart");
            case ASHEN_DEADEYE -> new MechanicNotice("DEADEYE", "Watch the aim line, then leave it when it locks.", "Bait, then dodge the line");
            case WIDOWS_TRAIL -> new MechanicNotice("WIDOW'S CLAIM", "The hunted player keeps moving; everyone avoids the trail.", "Keep moving; avoid the trail");
            case WIDOWS_WEBBREAK -> new MechanicNotice("WEBBREAK", "Marked players run six blocks from their web.", "Run out of your circle");
            case ROOT_WARDS -> new MechanicNotice("ROOT WARDS", "Put at least one player in every green circle.", "Fill every green circle");
            case BRIAR_LATTICE -> new MechanicNotice("BRIAR LATTICE", "Leave both red root lines before they erupt.", "Leave the red lines");
            case UNDERTOW -> new MechanicNotice("UNDERTOW", "Brace for the pull, then escape the red tide.", "Brace, then escape");
            case TIDAL_DIVIDE -> new MechanicNotice("TIDAL DIVIDE", "Cross to the green side before the wave lands.", "Cross to green");
            case SAINTS_STAGGER -> new MechanicNotice("SAINT'S STAGGER", "Damage the Saint until his guard breaks.", "Break his guard");
            case IRON_COUNTERSTANCE -> new MechanicNotice("COUNTERSTANCE", "Stop attacking until the stance breaks.", "Stop attacking");
            case RIFT_SECTORS -> new MechanicNotice("RIFT SECTORS", "Move into the green wedge or the center.", "Find green");
            case RUNEBLOOM_SIGILS -> new MechanicNotice("RUNEBLOOM SIGILS", "Put at least one player in every green sigil.", "Soak every sigil");
            case PETALSTORM -> new MechanicNotice("PETALSTORM", "Keep moving around the rotating beam.", "Dodge the rotating beam");
            case RESONANCE_LOCK -> new MechanicNotice("RESONANCE LOCK", "Turn your camera away from Voralith.", "Look away");
            case OATH_RINGS -> new MechanicNotice("OATH RINGS", "Obey IN or OUT and spread when marked.", "Follow IN or OUT");
        };
    }

    private String mechanicConsequence(BossMechanicKind kind, int tier) {
        int failurePercent = (int) Math.round(BossBalance.mechanicFailureHealthRatio(tier) * 100.0);
        int hazardPercent = (int) Math.round(BossBalance.mechanicHazardHealthRatio(tier) * 100.0);
        return switch (kind) {
            case MARSHAL_STACK -> "The hit is divided between nearby players.";
            case WIDOWS_TRAIL, PETALSTORM -> "Contact removes " + hazardPercent + "% max health per hit.";
            case IRON_COUNTERSTANCE -> "Attacking reflects " + hazardPercent + "% and marks you for a " + failurePercent + "% failure hit.";
            default -> "Failure removes " + failurePercent + "% of max health.";
        };
    }

    private List<Player> eligibleBossPlayers(LivingEntity boss, BossType type) {
        if (boss == null || boss.getWorld() == null) {
            return List.of();
        }
        List<Player> players = new ArrayList<>();
        for (Player player : boss.getWorld().getPlayers()) {
            if (isFightEligiblePlayer(player) && isPlayerInFightArea(player, boss, type)) {
                players.add(player);
            }
        }
        return players;
    }

    private List<Player> eligibleMechanicPlayers(LivingEntity boss, BossType type, ActiveBossMechanic mechanic) {
        if (mechanic == null || mechanic.participants.isEmpty()) {
            return eligibleBossPlayers(boss, type);
        }
        List<Player> players = new ArrayList<>();
        for (UUID participantId : mechanic.participants) {
            Player player = Bukkit.getPlayer(participantId);
            if (isEligibleMechanicTarget(boss, type, player)) {
                players.add(player);
            }
        }
        return players;
    }

    private Player primaryMechanicTarget(LivingEntity boss, BossType type, ActiveBossMechanic mechanic) {
        Player target = mechanic.targets.isEmpty() ? null : Bukkit.getPlayer(mechanic.targets.getFirst());
        if (isEligibleMechanicTarget(boss, type, target)) {
            return target;
        }
        List<Player> participants = eligibleMechanicPlayers(boss, type, mechanic);
        List<Player> candidates = participants.stream()
            .filter(player -> !mechanic.targets.contains(player.getUniqueId()))
            .toList();
        if (candidates.isEmpty()) {
            candidates = participants;
        }
        if (candidates.isEmpty()) {
            return null;
        }
        target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        if (mechanic.targets.isEmpty()) {
            mechanic.targets.add(target.getUniqueId());
        } else {
            mechanic.targets.set(0, target.getUniqueId());
        }
        return target;
    }

    private void capSharedObjectivesToSurvivors(ActiveBossMechanic mechanic, int survivingPlayers) {
        int maximumObjectives = Math.max(1, survivingPlayers);
        while (mechanic.points.size() > maximumObjectives) {
            mechanic.points.removeLast();
        }
    }

    private List<UUID> randomMechanicTargets(List<Player> players, int count) {
        List<Player> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);
        return shuffled.stream()
            .limit(Math.max(0, Math.min(count, shuffled.size())))
            .map(Player::getUniqueId)
            .toList();
    }

    private boolean isEligibleMechanicTarget(LivingEntity boss, BossType type, Player player) {
        return player != null
            && player.getWorld() == boss.getWorld()
            && isFightEligiblePlayer(player)
            && isPlayerInFightArea(player, boss, type);
    }

    private void sendMechanicActionBar(LivingEntity boss, String message) {
        BossRecord record = bossRecord(boss);
        BossType type = record == null ? null : BossType.fromId(record.bossId());
        if (type == null) {
            return;
        }
        Component component = MM.deserialize(message);
        ActiveBossMechanic mechanic = activeBossMechanics.get(boss.getUniqueId());
        for (Player player : eligibleMechanicPlayers(boss, type, mechanic)) {
            player.sendActionBar(component);
        }
    }

    private Location bossMechanicCenter(LivingEntity boss) {
        BossArena arena = bossArenas.get(boss.getUniqueId());
        return arena == null ? boss.getLocation().clone() : arena.center().clone();
    }

    private double bossMechanicArenaRadius(LivingEntity boss, double fallback) {
        BossArena arena = bossArenas.get(boss.getUniqueId());
        return arena == null ? fallback : Math.max(6.0, arena.radius() - 1.5);
    }

    private void drawMechanicCircle(World world, Location center, double radius, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.1f);
        int points = Math.max(18, Math.min(42, (int) Math.ceil(radius * 4.0)));
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.15, Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, dust);
        }
    }

    private void drawMechanicLine(World world, Location center, double angle, double length, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.25f);
        Vector direction = new Vector(Math.cos(angle), 0.0, Math.sin(angle));
        for (double distance = -length; distance <= length; distance += 0.85) {
            Location point = center.clone().add(direction.clone().multiply(distance)).add(0.0, 0.22, 0.0);
            world.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, dust);
        }
    }

    private void drawMechanicRay(World world, Location origin, double angle, double length, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.35f);
        Vector direction = new Vector(Math.cos(angle), 0.0, Math.sin(angle));
        for (double distance = 0.5; distance <= length; distance += 0.65) {
            Location point = origin.clone().add(direction.clone().multiply(distance)).add(0.0, 0.7, 0.0);
            world.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, dust);
        }
    }

    private void drawMechanicTether(World world, Location from, Location to, Color color) {
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length <= 1.0E-6) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.1f);
        Vector step = delta.normalize().multiply(0.6);
        Location point = from.clone().add(0.0, 0.35, 0.0);
        for (double distance = 0.0; distance <= length; distance += 0.6) {
            world.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, dust);
            point.add(step);
        }
    }

    private void slowBossForMechanic(LivingEntity boss, BossMechanicKind kind) {
        Vector velocity = boss.getVelocity();
        if (isBossHeldByMechanic(kind)) {
            velocity.setX(0.0);
            velocity.setZ(0.0);
        } else {
            velocity.setX(velocity.getX() * ACTIVE_MECHANIC_MOMENTUM_MULTIPLIER);
            velocity.setZ(velocity.getZ() * ACTIVE_MECHANIC_MOMENTUM_MULTIPLIER);
        }
        boss.setVelocity(velocity);
        boss.addPotionEffect(new PotionEffect(
            PotionEffectType.SLOWNESS,
            ACTIVE_MECHANIC_SLOWNESS_TICKS,
            isBossHeldByMechanic(kind) ? 10 : ACTIVE_MECHANIC_SLOWNESS_AMPLIFIER,
            false,
            false,
            false
        ));
    }

    private boolean isBossHeldByMechanic(BossMechanicKind kind) {
        return switch (kind) {
            case ASHEN_DEADEYE, WIDOWS_WEBBREAK, BRIAR_LATTICE, TIDAL_DIVIDE, IRON_COUNTERSTANCE -> true;
            default -> false;
        };
    }

    private void damagePlayerWithBossMechanic(Player player, double damage, LivingEntity boss) {
        damagePlayerWithBossMechanic(player, damage, boss, false);
    }

    private void damagePlayerWithBossMechanic(Player player, double damage, LivingEntity boss, boolean failedMechanic) {
        if (player == null || boss == null || player.isDead() || !player.isValid() || damage <= 0.0) {
            return;
        }
        UUID targetId = player.getUniqueId();
        boolean added = bossMechanicDamageTargets.add(targetId);
        boolean failureAdded = failedMechanic && bossMechanicFailureTargets.add(targetId);
        try {
            player.damage(damage, boss);
        } finally {
            if (failureAdded) {
                bossMechanicFailureTargets.remove(targetId);
            }
            if (added) {
                bossMechanicDamageTargets.remove(targetId);
            }
        }
    }

    private void punishMechanicFailure(Player player, LivingEntity boss, BossType type) {
        punishMechanicPenalty(player, boss, type, BossBalance.mechanicFailureHealthRatio(type.progressionTier()), true);
    }

    private void punishMechanicHazard(Player player, LivingEntity boss, BossType type) {
        punishMechanicPenalty(player, boss, type, BossBalance.mechanicHazardHealthRatio(type.progressionTier()), false);
    }

    private void punishMechanicPenalty(Player player, LivingEntity boss, BossType type, double healthRatio, boolean failedMechanic) {
        if (player == null || boss == null || type == null || player.isDead() || !player.isValid()) {
            return;
        }
        ActiveBossMechanic activeMechanic = activeBossMechanics.get(boss.getUniqueId());
        if (failedMechanic && activeMechanic != null && !activeMechanic.failedTargets.add(player.getUniqueId())) {
            return;
        }
        AttributeInstance healthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = Math.max(1.0, healthAttribute == null ? 20.0 : healthAttribute.getValue());
        double intendedLoss = maxHealth * Math.max(0.0, Math.min(1.0, healthRatio));
        if (intendedLoss <= 0.0) {
            return;
        }

        recordMechanicFailure(boss, player);
        if (failedMechanic) {
            int percent = (int) Math.round(Math.max(0.0, Math.min(1.0, healthRatio)) * 100.0);
            String loss = "-" + percent + "% max health";
            player.showTitle(Title.title(
                MM.deserialize("<red><bold>MECHANIC FAILED</bold></red>"),
                MM.deserialize("<dark_red>" + loss + "</dark_red>"),
                Title.Times.times(Duration.ofMillis(75), Duration.ofMillis(1_000), Duration.ofMillis(225))
            ));
            player.sendActionBar(MM.deserialize("<red><bold>MECHANIC FAILED</bold></red> <dark_red>" + loss + "</dark_red>"));
            player.sendMessage(MM.deserialize("<red><bold>Mechanic failed:</bold></red> <white>" + loss + ".</white>"));
        }
        double targetHealth = player.getHealth() - intendedLoss;
        if (targetHealth <= 0.0) {
            damagePlayerWithBossMechanic(player, maxHealth * 100.0, boss, failedMechanic);
        } else {
            damagePlayerWithBossMechanic(player, intendedLoss, boss, failedMechanic);
            if (!player.isDead() && player.isValid() && player.getHealth() > targetHealth) {
                player.setHealth(Math.max(0.01, targetHealth));
            }
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.9f, 0.55f);
    }

    private void recordMechanicFailure(LivingEntity boss, Player player) {
        BossFightState state = bossFightStates.get(boss.getUniqueId());
        if (state != null) {
            state.addMechanicFailure(player);
        }
    }

    private void grantMechanicSuccessReward(Player player, BossType type, BossMechanicKind kind) {
        if (player == null || type == null || kind == null || player.isDead() || !player.isValid()) {
            return;
        }
        BossMechanics.SuccessReward reward = BossMechanics.successReward(kind.name(), type.progressionTier());
        if (reward == BossMechanics.SuccessReward.NONE) {
            return;
        }

        String rewardText;
        if (reward == BossMechanics.SuccessReward.SPEED_I) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 0, false, true, true));
            rewardText = "<aqua>Speed I - 4s</aqua>";
        } else {
            AttributeInstance healthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
            double maxHealth = Math.max(1.0, healthAttribute == null ? 20.0 : healthAttribute.getValue());
            double healing = Math.min(4.0, Math.max(0.0, maxHealth - player.getHealth()));
            if (healing > 0.01) {
                player.heal(healing, EntityRegainHealthEvent.RegainReason.CUSTOM);
            }
            rewardText = "<green>Instant Heal - 2 hearts</green>";
        }

        player.sendActionBar(MM.deserialize("<green><bold>MECHANIC CLEARED</bold></green> <dark_gray>|</dark_gray> " + rewardText));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.65f, 1.45f);
        player.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().clone().add(0.0, 1.0, 0.0), 7, 0.3, 0.45, 0.3, 0.02);
    }

    private boolean suppressesHealing(BossMechanicKind kind, int phase) {
        return switch (kind) {
            case TIDAL_DIVIDE, IRON_COUNTERSTANCE, PETALSTORM, RESONANCE_LOCK, OATH_RINGS -> true;
            case RIFT_SECTORS -> phase >= 2;
            default -> false;
        };
    }

    private void initializeHealingSuppression(LivingEntity boss, ActiveBossMechanic mechanic) {
        if (!suppressesHealing(mechanic.kind, mechanic.phase)) {
            return;
        }
        BossRecord record = bossRecord(boss);
        BossType type = record == null ? null : BossType.fromId(record.bossId());
        if (type == null) {
            return;
        }
        for (Player player : eligibleMechanicPlayers(boss, type, mechanic)) {
            mechanic.healingCaps.put(player.getUniqueId(), player.getHealth());
        }
    }

    private void enforceHealingSuppression(LivingEntity boss, BossType type, ActiveBossMechanic mechanic) {
        if (!suppressesHealing(mechanic.kind, mechanic.phase)) {
            return;
        }
        BossFightState state = bossFightStates.get(boss.getUniqueId());
        for (Player player : eligibleMechanicPlayers(boss, type, mechanic)) {
            double cap = mechanic.healingCaps.computeIfAbsent(player.getUniqueId(), ignored -> player.getHealth());
            if (player.getHealth() > cap + 0.01) {
                double blocked = player.getHealth() - cap;
                player.setHealth(Math.max(0.01, cap));
                if (state != null) {
                    state.addBlockedHealing(player, blocked);
                }
                warnHealingSuppressed(player, mechanic);
            } else if (player.getHealth() < cap) {
                mechanic.healingCaps.put(player.getUniqueId(), player.getHealth());
            }
        }
    }

    private HealingSuppression activeHealingSuppressionFor(Player player) {
        if (player == null) {
            return null;
        }
        for (BossRecord record : new ArrayList<>(trackedBosses.values())) {
            ActiveBossMechanic mechanic = activeBossMechanics.get(record.entityUuid());
            BossFightState state = bossFightStates.get(record.entityUuid());
            Entity entity = Bukkit.getEntity(record.entityUuid());
            BossType type = BossType.fromId(record.bossId());
            if (mechanic == null || state == null || !(entity instanceof LivingEntity boss) || type == null
                || !suppressesHealing(mechanic.kind, mechanic.phase)
                || !mechanic.participants.contains(player.getUniqueId())
                || !isEligibleMechanicTarget(boss, type, player)) {
                continue;
            }
            return new HealingSuppression(state, mechanic);
        }
        return null;
    }

    public boolean blockHealingIfSuppressed(Player player, double attemptedAmount) {
        HealingSuppression suppression = activeHealingSuppressionFor(player);
        if (suppression == null) {
            return false;
        }
        suppression.state().addBlockedHealing(player, Math.max(0.0, attemptedAmount));
        warnHealingSuppressed(player, suppression.mechanic());
        return true;
    }

    private void warnHealingSuppressed(Player player, ActiveBossMechanic mechanic) {
        long now = System.currentTimeMillis();
        long nextWarning = mechanic.healingWarningCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < nextWarning) {
            return;
        }
        mechanic.healingWarningCooldowns.put(player.getUniqueId(), now + 1_500L);
        player.sendActionBar(MM.deserialize("<dark_red><bold>HEALING SEALED</bold></dark_red>"));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.7f, 0.55f);
    }

    private void drawSafeSector(World world, Location center, double angle, double radius, double halfWidth) {
        Particle.DustOptions green = new Particle.DustOptions(Color.fromRGB(70, 225, 115), 1.25f);
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(230, 55, 70), 0.95f);
        for (double side : new double[]{-halfWidth, halfWidth}) {
            Vector direction = new Vector(Math.cos(angle + side), 0.0, Math.sin(angle + side));
            for (double distance = 0.5; distance <= radius; distance += 0.8) {
                world.spawnParticle(Particle.DUST, center.clone().add(direction.clone().multiply(distance)).add(0.0, 0.2, 0.0), 1, 0.0, 0.0, 0.0, 0.0, green);
            }
        }
        int arcPoints = 24;
        for (int i = 0; i <= arcPoints; i++) {
            double pointAngle = angle - halfWidth + (halfWidth * 2.0 * i / arcPoints);
            Location point = center.clone().add(Math.cos(pointAngle) * radius, 0.2, Math.sin(pointAngle) * radius);
            world.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, green);
        }
        for (int i = 0; i < 20; i++) {
            double pointAngle = Math.PI * 2.0 * i / 20.0;
            if (BossMechanics.isAngleInSector(pointAngle, angle, halfWidth)) {
                continue;
            }
            Location point = center.clone().add(Math.cos(pointAngle) * radius, 0.18, Math.sin(pointAngle) * radius);
            world.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, red);
        }
    }

    private double horizontalDistanceToLine(Location point, Location center, double angle) {
        double dx = point.getX() - center.getX();
        double dz = point.getZ() - center.getZ();
        return Math.abs(-Math.sin(angle) * dx + Math.cos(angle) * dz);
    }

    private boolean tickMarshalStack(LivingEntity boss, ActiveBossMechanic mechanic, long now) {
        Player marked = primaryMechanicTarget(boss, BossType.YULE_THE_MINION, mechanic);
        if (marked == null) {
            return true;
        }

        double radius = 3.25;
        drawMechanicCircle(boss.getWorld(), marked.getLocation(), radius, Color.fromRGB(65, 155, 255));
        marked.getWorld().spawnParticle(Particle.END_ROD, marked.getLocation().clone().add(0.0, 1.5, 0.0), 3, 0.18, 0.35, 0.18, 0.01);
        if (now < mechanic.warningEndsAt) {
            marked.sendActionBar(MM.deserialize("<aqua><bold>HOLD THE LINE - GROUP ON YOU</bold></aqua>"));
            return false;
        }

        List<Player> stack = eligibleMechanicPlayers(boss, BossType.YULE_THE_MINION, mechanic).stream()
            .filter(player -> horizontalDistanceSquared(player.getLocation(), marked.getLocation()) <= radius * radius)
            .toList();
        if (stack.isEmpty()) {
            stack = List.of(marked);
        }
        double total = scaledBossAbilityDamage(boss, mechanic.phase >= 2 ? 28.0 : 22.0);
        double damage = BossMechanics.splitDamage(total, stack.size(), mechanic.phase >= 2 ? 17.0 : 14.0);
        for (Player player : stack) {
            damagePlayerWithBossMechanic(player, damage, boss);
        }
        boss.getWorld().spawnParticle(Particle.FLASH, marked.getLocation().clone().add(0.0, 1.0, 0.0), 1);
        boss.getWorld().playSound(marked.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.2f, stack.size() > 1 ? 1.25f : 0.72f);
        return true;
    }

    private boolean tickAshenCrossfire(LivingEntity boss, ActiveBossMechanic mechanic, long now) {
        List<Location> centers = new ArrayList<>();
        for (UUID targetId : mechanic.targets) {
            Player target = Bukkit.getPlayer(targetId);
            if (isEligibleMechanicTarget(boss, BossType.KAEL_THE_ASHEN, target)) {
                Location center = target.getLocation().clone();
                centers.add(center);
                drawMechanicCircle(boss.getWorld(), center, 3.0, Color.fromRGB(235, 70, 45));
                target.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(0.0, 1.4, 0.0), 3, 0.15, 0.25, 0.15, 0.01);
            }
        }
        if (centers.isEmpty()) {
            return true;
        }
        if (now < mechanic.warningEndsAt) {
            return false;
        }

        for (Location center : centers) {
            boss.getWorld().spawnParticle(Particle.EXPLOSION, center.clone().add(0.0, 0.5, 0.0), 3, 0.45, 0.2, 0.45, 0.02);
            for (Player player : eligibleMechanicPlayers(boss, BossType.KAEL_THE_ASHEN, mechanic)) {
                if (horizontalDistanceSquared(player.getLocation(), center) > 9.0) {
                    continue;
                }
                punishMechanicFailure(player, boss, BossType.KAEL_THE_ASHEN);
                player.setFireTicks(Math.max(player.getFireTicks(), mechanic.phase >= 2 ? 100 : 50));
            }
        }
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 0.72f);
        return true;
    }

    private boolean tickAshenDeadeye(LivingEntity boss, ActiveBossMechanic mechanic, long now) {
        Player marked = mechanic.stage == 0
            ? primaryMechanicTarget(boss, BossType.KAEL_THE_ASHEN, mechanic)
            : (mechanic.targets.isEmpty() ? null : Bukkit.getPlayer(mechanic.targets.getFirst()));
        if (mechanic.stage == 0 && marked == null) {
            return true;
        }
        Location origin = boss.getLocation().clone();
        if (mechanic.stage == 0) {
            double dx = marked.getLocation().getX() - origin.getX();
            double dz = marked.getLocation().getZ() - origin.getZ();
            mechanic.angle = Math.atan2(dz, dx);
            if (now >= mechanic.nextStepAt) {
                mechanic.stage = 1;
                announceMechanicUpdate(
                    boss,
                    "<red><bold>DEADEYE LOCKED</bold></red>",
                    "<yellow>Leave the red line now.</yellow>",
                    "<red><bold>DEADEYE LOCKED - LEAVE THE RED LINE</bold></red>",
                    Sound.BLOCK_NOTE_BLOCK_HAT,
                    0.45f
                );
            }
        }

        double length = bossMechanicArenaRadius(boss, 16.0) * 2.0 + 4.0;
        drawMechanicRay(
            boss.getWorld(),
            origin,
            mechanic.angle,
            length,
            mechanic.stage == 0 ? Color.fromRGB(245, 190, 55) : Color.fromRGB(245, 45, 35)
        );
        if (isEligibleMechanicTarget(boss, BossType.KAEL_THE_ASHEN, marked)) {
            marked.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, marked.getLocation().clone().add(0.0, 1.3, 0.0), 3, 0.12, 0.25, 0.12, 0.01);
        }
        if (now < mechanic.warningEndsAt) {
            return false;
        }

        for (Player player : eligibleMechanicPlayers(boss, BossType.KAEL_THE_ASHEN, mechanic)) {
            if (!BossMechanics.isInsideForwardLane(
                player.getLocation().getX(),
                player.getLocation().getZ(),
                origin.getX(),
                origin.getZ(),
                mechanic.angle,
                length,
                1.3
            )) {
                continue;
            }
            punishMechanicFailure(player, boss, BossType.KAEL_THE_ASHEN);
            player.setFireTicks(Math.max(player.getFireTicks(), 120));
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1, false, true, true));
        }
        boss.getWorld().spawnParticle(Particle.SONIC_BOOM, origin.clone().add(0.0, 1.1, 0.0), 1);
        boss.getWorld().playSound(origin, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.4f, 0.5f);
        return true;
    }

    private boolean tickWidowsTrail(LivingEntity boss, ActiveBossMechanic mechanic, long now) {
        Player hunted = primaryMechanicTarget(boss, BossType.VESPER_THE_WIDOW_QUEEN, mechanic);
        if (hunted == null) {
            return true;
        }
        if (now >= mechanic.warningEndsAt && now >= mechanic.nextStepAt) {
            mechanic.points.add(hunted.getLocation().clone());
            mechanic.nextStepAt = now + 1_000L;
        }

        for (Location puddle : mechanic.points) {
            drawMechanicCircle(boss.getWorld(), puddle, 2.15, Color.fromRGB(105, 190, 55));
            boss.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, puddle.clone().add(0.0, 0.25, 0.0), 5, 0.75, 0.08, 0.75, 0.01);
        }
        if (now < mechanic.warningEndsAt) {
            hunted.sendActionBar(MM.deserialize("<red><bold>WIDOW'S CLAIM - KEEP MOVING</bold></red>"));
        } else {
            for (Player player : eligibleMechanicPlayers(boss, BossType.VESPER_THE_WIDOW_QUEEN, mechanic)) {
                boolean insidePuddle = mechanic.points.stream().anyMatch(point -> point.getWorld() == player.getWorld()
                    && horizontalDistanceSquared(player.getLocation(), point) <= 2.15 * 2.15);
                long nextHit = mechanic.hitCooldowns.getOrDefault(player.getUniqueId(), 0L);
                if (!insidePuddle || now < nextHit) {
                    continue;
                }
                punishMechanicHazard(player, boss, BossType.VESPER_THE_WIDOW_QUEEN);
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, mechanic.phase >= 2 ? 80 : 55, 0, false, true, true));
                mechanic.hitCooldowns.put(player.getUniqueId(), now + 1_250L);
            }
        }
        return now >= mechanic.expiresAt;
    }

    private boolean tickWidowsWebbreak(LivingEntity boss, ActiveBossMechanic mechanic, long now) {
        boolean hasTarget = false;
        for (int i = 0; i < mechanic.targets.size() && i < mechanic.points.size(); i++) {
            UUID targetId = mechanic.targets.get(i);
            Player target = Bukkit.getPlayer(targetId);
            if (!isEligibleMechanicTarget(boss, BossType.VESPER_THE_WIDOW_QUEEN, target)) {
                continue;
            }
            hasTarget = true;
            Location anchor = mechanic.points.get(i);
            boolean broken = mechanic.hitCooldowns.containsKey(targetId)
                || horizontalDistanceSquared(target.getLocation(), anchor) >= mechanic.radius * mechanic.radius;
            if (broken && !mechanic.hitCooldowns.containsKey(targetId)) {
                mechanic.hitCooldowns.put(targetId, Long.MAX_VALUE);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, false, true, true));
                target.getWorld().playSound(target.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.1f, 1.25f);
            }
            Color color = broken ? Color.fromRGB(65, 225, 105) : Color.fromRGB(220, 45, 65);
            drawMechanicCircle(boss.getWorld(), anchor, mechanic.radius, color);
            drawMechanicTether(boss.getWorld(), anchor, target.getLocation(), color);
            target.sendActionBar(MM.deserialize(broken
                ? "<green><bold>WEB BROKEN</bold></green>"
                : "<red><bold>WEBBREAK - RUN OUT OF THE CIRCLE</bold></red>"));
        }
        if (!hasTarget) {
            return true;
        }
        if (now < mechanic.warningEndsAt) {
            return false;
        }

        for (UUID targetId : mechanic.targets) {
            if (mechanic.hitCooldowns.containsKey(targetId)) {
                continue;
            }
            Player target = Bukkit.getPlayer(targetId);
            if (!isEligibleMechanicTarget(boss, BossType.VESPER_THE_WIDOW_QUEEN, target)) {
                continue;
            }
            punishMechanicFailure(target, boss, BossType.VESPER_THE_WIDOW_QUEEN);
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 120, 1, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 4, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, true, true));
            target.getWorld().spawnParticle(Particle.SQUID_INK, target.getLocation().clone().add(0.0, 1.0, 0.0), 32, 0.45, 0.55, 0.45, 0.04);
        }
        boss.getWorld().playSound(mechanic.origin, Sound.ENTITY_SPIDER_DEATH, 1.25f, 0.55f);
        return true;
    }

    private boolean tickRootWards(LivingEntity boss, ActiveBossMechanic mechanic, long now) {
        List<Player> players = eligibleMechanicPlayers(boss, BossType.MIREWOOD_THE_ROOT_TYRANT, mechanic);
        capSharedObjectivesToSurvivors(mechanic, players.size());
        for (Location ward : mechanic.points) {
            drawMechanicCircle(boss.getWorld(), ward, 2.1, Color.fromRGB(65, 215, 105));
            boss.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, ward.clone().add(0.0, 0.35, 0.0), 3, 0.45, 0.25, 0.45, 0.01);
        }
        if (now < mechanic.warningEndsAt) {
            return false;
        }

        Set<UUID> warded = new HashSet<>();
        int failed = 0;
        for (Location ward : mechanic.points) {
            List<Player> occupants = players.stream()
                .filter(player -> horizontalDistanceSquared(player.getLocation(), ward) <= 2.1 * 2.1)
                .toList();
            if (occupants.isEmpty()) {
                failed++;
            } else {
                occupants.forEach(player -> warded.add(player.getUniqueId()));
            }
        }
        if (failed == 0) {
            for (Player player : players) {
                if (warded.contains(player.getUniqueId())) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0, false, true, true));
                }
            }
            announceMechanicUpdate(
                boss,
                "<green><bold>WARDS SATISFIED</bold></green>",
                "<green>Mirewood is exposed.</green>",
                "<green><bold>ROOT WARDS SATISFIED</bold></green>",
                Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                1.25f
            );
        } else {
            for (Player player : players) {
                punishMechanicFailure(player, boss, BossType.MIREWOOD_THE_ROOT_TYRANT);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 70, 1, false, true, true));
            }
            double maxHealth = boss.getAttribute(Attribute.MAX_HEALTH) == null ? boss.getHealth() : boss.getAttribute(Attribute.MAX_HEALTH).getValue();
            boss.setHealth(Math.min(maxHealth, boss.getHealth() + maxHealth * 0.025 * failed));
            announceMechanicUpdate(
                boss,
                "<red><bold>WARDS FAILED</bold></red>",
                "<dark_red>The empty wards healed Mirewood.</dark_red>",
                "<red><bold>THE EMPTY WARDS FEED MIREWOOD</bold></red>",
                Sound.BLOCK_ROOTED_DIRT_BREAK,
                0.55f
            );
        }
        return true;
    }

    private boolean tickBriarLattice(LivingEntity boss, ActiveBossMechanic mechanic, long now) {
        drawMechanicLine(boss.getWorld(), mechanic.origin, mechanic.angle, mechanic.radius, Color.fromRGB(220, 45, 55));
        drawMechanicLine(boss.getWorld(), mechanic.origin, mechanic.angle + Math.PI / 2.0, mechanic.radius, Color.fromRGB(220, 45, 55));
        boss.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, mechanic.origin.clone().add(0.0, 0.5, 0.0), 8, 1.0, 0.25, 1.0, 0.02);
        if (now < mechanic.warningEndsAt) {
            return false;
        }

        for (Player player : eligibleMechanicPlayers(boss, BossType.MIREWOOD_THE_ROOT_TYRANT, mechanic)) {
            Location location = player.getLocation();
            boolean inRoots = BossMechanics.isInsideCenteredLane(
                location.getX(), location.getZ(), mechanic.origin.getX(), mechanic.origin.getZ(),
                mechanic.angle, mechanic.radius, 1.65
            ) || BossMechanics.isInsideCenteredLane(
                location.getX(), location.getZ(), mechanic.origin.getX(), mechanic.origin.getZ(),
                mechanic.angle + Math.PI / 2.0, mechanic.radius, 1.65
            );
            if (!inRoots) {
                continue;
            }
            punishMechanicFailure(player, boss, BossType.MIREWOOD_THE_ROOT_TYRANT);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 3, false, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 120, 1, false, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1, false, true, true));
            player.getWorld().spawnParticle(Particle.BLOCK, player.getLocation().clone().add(0.0, 0.6, 0.0), 34, 0.35, 0.55, 0.35, 0.05, Material.MANGROVE_ROOTS.createBlockData());
        }
        boss.getWorld().playSound(mechanic.origin, Sound.BLOCK_ROOTED_DIRT_BREAK, 1.35f, 0.5f);
        return true;
    }

    private boolean tickUndertow(LivingEntity boss, ActiveBossMechanic mechanic, long now) {
        double radius = mechanic.radius;
        Color color = mechanic.stage == 0 ? Color.fromRGB(55, 175, 235) : Color.fromRGB(225, 60, 65);
        drawMechanicCircle(boss.getWorld(), mechanic.origin, radius, color);
        if (mechanic.stage == 0 && now >= mechanic.warningEndsAt) {
            for (Player player : eligibleMechanicPlayers(boss, BossType.NEREIDA_THE_ABYSS_MOTHER, mechanic)) {
                Vector pull = mechanic.origin.toVector().subtract(player.getLocation().toVector());
                if (pull.lengthSquared() > 1.0E-6) {
                    player.setVelocity(player.getVelocity().add(pull.normalize().multiply(1.15).setY(0.24)));
                }
            }
            mechanic.stage = 1;
            mechanic.nextStepAt = now + 2_250L;
            announceMechanicUpdate(
                boss,
                "<red><bold>UNDERTOW PULLED</bold></red>",
                "<yellow>Escape the red tide now.</yellow>",
                "<red><bold>UNDERTOW - ESCAPE THE RED TIDE</bold></red>",
                Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED,
                0.65f
            );
            return false;
        }
        if (mechanic.stage == 0 || now < mechanic.nextStepAt) {
            return false;
        }

        for (Player player : eligibleMechanicPlayers(boss, BossType.NEREIDA_THE_ABYSS_MOTHER, mechanic)) {
            if (horizontalDistanceSquared(player.getLocation(), mechanic.origin) > radius * radius) {
                grantMechanicSuccessReward(player, BossType.NEREIDA_THE_ABYSS_MOTHER, mechanic.kind);
                continue;
            }
            punishMechanicFailure(player, boss, BossType.NEREIDA_THE_ABYSS_MOTHER);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 90, 1, false, true, true));
        }
        boss.getWorld().spawnParticle(Particle.SPLASH, mechanic.origin.clone().add(0.0, 0.6, 0.0), 100, radius * 0.35, 0.4, radius * 0.35, 0.12);
        return true;
    }

    private boolean tickTidalDivide(LivingEntity boss, ActiveBossMechanic mechanic, long now) {
        drawMechanicLine(
            boss.getWorld(),
            mechanic.origin,
            mechanic.angle + Math.PI / 2.0,
            mechanic.radius,
            Color.fromRGB(75, 190, 245)
        );
        Particle.DustOptions safeDust = new Particle.DustOptions(Color.fromRGB(65, 225, 120), 1.15f);
        for (double forward = 2.0; forward <= mechanic.radius; forward += 2.0) {
            for (double side = -mechanic.radius * 0.75; side <= mechanic.radius * 0.75; side += 2.25) {
                double x = Math.cos(mechanic.angle) * forward - Math.sin(mechanic.angle) * side;
                double z = Math.sin(mechanic.angle) * forward + Math.cos(mechanic.angle) * side;
                if (x * x + z * z <= mechanic.radius * mechanic.radius) {
                    boss.getWorld().spawnParticle(Particle.DUST, mechanic.origin.clone().add(x, 0.18, z), 1, 0.0, 0.0, 0.0, 0.0, safeDust);
                }
            }
        }
        if (now < mechanic.warningEndsAt) {
            return false;
        }

        for (Player player : eligibleMechanicPlayers(boss, BossType.NEREIDA_THE_ABYSS_MOTHER, mechanic)) {
            double dx = player.getLocation().getX() - mechanic.origin.getX();
            double dz = player.getLocation().getZ() - mechanic.origin.getZ();
            if (BossMechanics.isOnSafeSide(dx, dz, mechanic.angle)) {
                grantMechanicSuccessReward(player, BossType.NEREIDA_THE_ABYSS_MOTHER, mechanic.kind);
                continue;
            }
            punishMechanicFailure(player, boss, BossType.NEREIDA_THE_ABYSS_MOTHER);
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0, false, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2, false, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 1, false, true, true));
            player.getWorld().spawnParticle(Particle.SPLASH, player.getLocation().clone().add(0.0, 0.8, 0.0), 44, 0.5, 0.6, 0.5, 0.15);
        }
        boss.getWorld().playSound(mechanic.origin, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.3f, 0.55f);
        return true;
    }

    private boolean tickSaintsStagger(LivingEntity boss, ActiveBossMechanic mechanic, long now) {
        if (mechanic.stage == 0) {
            drawMechanicCircle(boss.getWorld(), boss.getLocation(), 4.2, Color.fromRGB(235, 190, 65));
            int percent = (int) Math.min(100.0, Math.round(mechanic.progress * 100.0 / Math.max(1.0, mechanic.threshold)));
            sendMechanicActionBar(boss, "<yellow><bold>BREAK THE SAINT'S GUARD: " + percent + "%</bold></yellow>");
            if (now < mechanic.warningEndsAt) {
                return false;
            }
            if (mechanic.progress >= mechanic.threshold) {
                mechanic.stage = 1;
                mechanic.nextStepAt = now + 5_000L;
                boss.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 110, 10, false, true, true));
                announceMechanicUpdate(
                    boss,
                    "<green><bold>GUARD BROKEN</bold></green>",
                    "<green>Deal 25% more damage now.</green>",
                    "<green><bold>GUARD BROKEN - 25% MORE DAMAGE</bold></green>",
                    Sound.ITEM_SHIELD_BREAK,
                    0.7f
                );
                boss.getWorld().spawnParticle(Particle.CRIT, boss.getLocation().clone().add(0.0, 1.2, 0.0), 60, 0.8, 0.6, 0.8, 0.12);
                return false;
            }

            for (Player player : eligibleMechanicPlayers(boss, BossType.IRON_SAINT, mechanic)) {
                punishMechanicFailure(player, boss, BossType.IRON_SAINT);
                Vector away = player.getLocation().toVector().subtract(boss.getLocation().toVector());
                if (away.lengthSquared() > 1.0E-6) {
                    player.setVelocity(player.getVelocity().add(away.normalize().multiply(0.9).setY(0.28)));
                }
            }
            announceMechanicUpdate(
                boss,
                "<red><bold>GUARD HELD</bold></red>",
                "<dark_red>Counter-slam incoming.</dark_red>",
                "<red><bold>GUARD HELD - COUNTER-SLAM</bold></red>",
                Sound.BLOCK_ANVIL_LAND,
                0.45f
            );
            return true;
        }

        boss.getWorld().spawnParticle(Particle.CRIT, boss.getLocation().clone().add(0.0, 1.0, 0.0), 5, 0.45, 0.5, 0.45, 0.05);
        return now >= mechanic.nextStepAt;
    }

    private boolean tickIronCounterstance(LivingEntity boss, ActiveBossMechanic mechanic, long now) {
        if (mechanic.stage == 0) {
            drawMechanicCircle(boss.getWorld(), boss.getLocation(), 4.5, Color.fromRGB(235, 70, 55));
            boss.getWorld().spawnParticle(Particle.ENCHANTED_HIT, boss.getLocation().clone().add(0.0, 1.35, 0.0), 7, 0.45, 0.65, 0.45, 0.04);
            sendMechanicActionBar(boss, mechanic.progress <= 0.0
                ? "<red><bold>COUNTERSTANCE - STOP ATTACKING</bold></red>"
                : "<dark_red><bold>THE SAINT HAS MARKED YOUR GREED</bold></dark_red>");
            if (now < mechanic.warningEndsAt) {
                return false;
            }
            if (mechanic.progress > 0.0) {
                for (UUID attackerId : mechanic.hitCooldowns.keySet()) {
                    Player attacker = Bukkit.getPlayer(attackerId);
                    if (isEligibleMechanicTarget(boss, BossType.IRON_SAINT, attacker)) {
                        punishMechanicFailure(attacker, boss, BossType.IRON_SAINT);
                    }
                }
                boss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 120, 1, false, true, true));
                boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1, false, true, true));
                announceMechanicUpdate(
                    boss,
                    "<red><bold>STANCE FAILED</bold></red>",
                    "<dark_red>The Saint is empowered.</dark_red>",
                    "<red><bold>COUNTERSTANCE FAILED - THE SAINT IS EMPOWERED</bold></red>",
                    Sound.BLOCK_ANVIL_LAND,
                    0.42f
                );
                return true;
            }
            mechanic.stage = 1;
            mechanic.nextStepAt = now + 4_000L;
            boss.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 90, 10, false, true, true));
            announceMechanicUpdate(
                boss,
                "<green><bold>STANCE BROKEN</bold></green>",
                "<green>Deal 25% more damage now.</green>",
                "<green><bold>STANCE BROKEN - 25% MORE DAMAGE</bold></green>",
                Sound.ITEM_SHIELD_BREAK,
                0.75f
            );
            return false;
        }

        boss.getWorld().spawnParticle(Particle.CRIT, boss.getLocation().clone().add(0.0, 1.1, 0.0), 6, 0.45, 0.55, 0.45, 0.08);
        return now >= mechanic.nextStepAt;
    }

    private boolean tickRiftSectors(LivingEntity boss, ActiveBossMechanic mechanic, long now) {
        double halfWidth = Math.toRadians(mechanic.phase >= 2 ? 38.0 : 48.0);
        drawSafeSector(boss.getWorld(), mechanic.origin, mechanic.angle, mechanic.radius, halfWidth);
        if (now < mechanic.warningEndsAt) {
            return false;
        }

        for (Player player : eligibleMechanicPlayers(boss, BossType.AURELION_THE_RIFT_SERAPH, mechanic)) {
            double dx = player.getLocation().getX() - mechanic.origin.getX();
            double dz = player.getLocation().getZ() - mechanic.origin.getZ();
            double angle = Math.atan2(dz, dx);
            boolean safe = dx * dx + dz * dz <= 4.0 || BossMechanics.isAngleInSector(angle, mechanic.angle, halfWidth);
            if (safe) {
                grantMechanicSuccessReward(player, BossType.AURELION_THE_RIFT_SERAPH, mechanic.kind);
                continue;
            }
            punishMechanicFailure(player, boss, BossType.AURELION_THE_RIFT_SERAPH);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1, false, true, true));
            Vector pull = mechanic.origin.toVector().subtract(player.getLocation().toVector());
            if (pull.lengthSquared() > 1.0E-6) {
                player.setVelocity(player.getVelocity().add(pull.normalize().multiply(0.55).setY(0.16)));
            }
        }
        boss.getWorld().spawnParticle(Particle.REVERSE_PORTAL, mechanic.origin.clone().add(0.0, 0.7, 0.0), 90, 1.2, 0.6, 1.2, 0.22);
        boss.getWorld().playSound(mechanic.origin, Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 1.25f);
        return true;
    }

    private boolean tickRunebloomSigils(LivingEntity boss, ActiveBossMechanic mechanic, long now) {
        List<Player> players = eligibleMechanicPlayers(boss, BossType.MORVESSA_THE_RUNEBLOOM_WITCH, mechanic);
        capSharedObjectivesToSurvivors(mechanic, players.size());
        for (Location sigil : mechanic.points) {
            drawMechanicCircle(boss.getWorld(), sigil, 2.2, Color.fromRGB(85, 235, 105));
            boss.getWorld().spawnParticle(Particle.WITCH, sigil.clone().add(0.0, 0.35, 0.0), 3, 0.40, 0.25, 0.40, 0.01);
        }
        if (now < mechanic.warningEndsAt) {
            return false;
        }

        Set<UUID> protectedPlayers = new HashSet<>();
        int missed = 0;
        for (Location sigil : mechanic.points) {
            List<Player> occupants = players.stream()
                .filter(player -> horizontalDistanceSquared(player.getLocation(), sigil) <= 2.2 * 2.2)
                .toList();
            if (occupants.isEmpty()) {
                missed++;
            } else {
                occupants.forEach(player -> protectedPlayers.add(player.getUniqueId()));
            }
        }

        if (missed == 0) {
            announceMechanicUpdate(
                boss,
                "<green><bold>SIGILS SEALED</bold></green>",
                "<green>Morvessa's bloom is contained.</green>",
                "<green><bold>THE RUNEBLOOM SIGILS ARE SEALED</bold></green>",
                Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                1.45f
            );
            for (Player player : players) {
                if (protectedPlayers.contains(player.getUniqueId())) {
                    grantMechanicSuccessReward(player, BossType.MORVESSA_THE_RUNEBLOOM_WITCH, mechanic.kind);
                }
            }
        } else {
            for (Player player : players) {
                punishMechanicFailure(player, boss, BossType.MORVESSA_THE_RUNEBLOOM_WITCH);
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, mechanic.phase >= 2 ? 120 : 80, mechanic.phase >= 2 ? 1 : 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, false, true, true));
            }
            double maxHealth = boss.getAttribute(Attribute.MAX_HEALTH) == null ? boss.getHealth() : boss.getAttribute(Attribute.MAX_HEALTH).getValue();
            boss.setHealth(Math.min(maxHealth, boss.getHealth() + maxHealth * 0.03 * missed));
            announceMechanicUpdate(
                boss,
                "<red><bold>SIGILS FAILED</bold></red>",
                "<dark_red>The empty sigils healed Morvessa.</dark_red>",
                "<red><bold>THE EMPTY SIGILS BLOOM AGAINST YOU</bold></red>",
                Sound.ENTITY_WITCH_CELEBRATE,
                0.52f
            );
        }
        return true;
    }

    private boolean tickPetalstorm(LivingEntity boss, ActiveBossMechanic mechanic, long now) {
        double rotation = now < mechanic.warningEndsAt
            ? mechanic.angle
            : mechanic.angle + ((now - mechanic.warningEndsAt) / 1_000.0) * Math.toRadians(42.0);
        Color color = now < mechanic.warningEndsAt ? Color.fromRGB(235, 190, 65) : Color.fromRGB(195, 65, 225);
        drawMechanicLine(boss.getWorld(), mechanic.origin, rotation, mechanic.radius, color);
        boss.getWorld().spawnParticle(Particle.WITCH, mechanic.origin.clone().add(0.0, 0.7, 0.0), 5, 0.35, 0.35, 0.35, 0.02);
        if (now < mechanic.warningEndsAt) {
            return false;
        }

        for (Player player : eligibleMechanicPlayers(boss, BossType.MORVESSA_THE_RUNEBLOOM_WITCH, mechanic)) {
            double radial = Math.sqrt(Math.pow(player.getLocation().getX() - mechanic.origin.getX(), 2.0)
                + Math.pow(player.getLocation().getZ() - mechanic.origin.getZ(), 2.0));
            long nextHit = mechanic.hitCooldowns.getOrDefault(player.getUniqueId(), 0L);
            if (radial > mechanic.radius || horizontalDistanceToLine(player.getLocation(), mechanic.origin, rotation) > 1.15 || now < nextHit) {
                continue;
            }
            punishMechanicHazard(player, boss, BossType.MORVESSA_THE_RUNEBLOOM_WITCH);
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 65, 0, false, true, true));
            Vector side = new Vector(-Math.sin(rotation), 0.18, Math.cos(rotation)).multiply(0.35);
            player.setVelocity(player.getVelocity().add(side));
            mechanic.hitCooldowns.put(player.getUniqueId(), now + 1_500L);
        }
        return now >= mechanic.expiresAt;
    }

    private boolean tickResonanceLock(LivingEntity boss, ActiveBossMechanic mechanic, long now) {
        for (Player player : eligibleMechanicPlayers(boss, BossType.VORALITH_THE_CRIMSON_WARDEN, mechanic)) {
            boolean looking = isLookingAtBoss(player, boss);
            Color color = looking ? Color.fromRGB(230, 45, 70) : Color.fromRGB(75, 205, 125);
            player.getWorld().spawnParticle(Particle.DUST, player.getEyeLocation(), 3, 0.18, 0.16, 0.18, 0.0, new Particle.DustOptions(color, 1.0f));
        }
        boss.getWorld().spawnParticle(Particle.SCULK_SOUL, boss.getEyeLocation(), 7, 0.35, 0.35, 0.35, 0.03);
        if (now < mechanic.warningEndsAt) {
            return false;
        }

        for (Player player : eligibleMechanicPlayers(boss, BossType.VORALITH_THE_CRIMSON_WARDEN, mechanic)) {
            if (!isLookingAtBoss(player, boss)) {
                continue;
            }
            punishMechanicFailure(player, boss, BossType.VORALITH_THE_CRIMSON_WARDEN);
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 130, 0, false, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1, false, true, true));
            player.getWorld().spawnParticle(Particle.SONIC_BOOM, player.getLocation().clone().add(0.0, 1.0, 0.0), 1);
        }
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.6f, 0.62f);
        return true;
    }

    private boolean isLookingAtBoss(Player player, LivingEntity boss) {
        Vector toBoss = boss.getEyeLocation().toVector().subtract(player.getEyeLocation().toVector());
        if (toBoss.lengthSquared() <= 1.0E-6) {
            return true;
        }
        return BossMechanics.isLookingToward(player.getEyeLocation().getDirection().normalize().dot(toBoss.normalize()));
    }

    private boolean tickOathRings(LivingEntity boss, ActiveBossMechanic mechanic, long now) {
        Color boundary = mechanic.insideSafe ? Color.fromRGB(70, 220, 115) : Color.fromRGB(235, 70, 45);
        drawMechanicCircle(boss.getWorld(), mechanic.origin, mechanic.radius, boundary);
        if (mechanic.phase >= 3) {
            Component warning = mechanic.insideSafe
                ? MM.deserialize("<green><bold>GET IN</bold></green> <red><bold>+ SPREAD</bold></red> <dark_red>| NO HEALING</dark_red>")
                : MM.deserialize("<red><bold>GET OUT + SPREAD</bold></red> <dark_red>| NO HEALING</dark_red>");
            for (Player player : eligibleMechanicPlayers(boss, BossType.CORRUPTED_OATHKEEPER, mechanic)) {
                player.sendActionBar(warning);
            }
            for (UUID targetId : mechanic.targets) {
                Player target = Bukkit.getPlayer(targetId);
                if (!isEligibleMechanicTarget(boss, BossType.CORRUPTED_OATHKEEPER, target)) {
                    continue;
                }
                drawMechanicCircle(boss.getWorld(), target.getLocation(), 3.1, Color.fromRGB(235, 45, 75));
                target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().clone().add(0.0, 1.4, 0.0), 3, 0.15, 0.28, 0.15, 0.01);
            }
        }
        if (now < mechanic.warningEndsAt) {
            return false;
        }

        List<Player> players = eligibleMechanicPlayers(boss, BossType.CORRUPTED_OATHKEEPER, mechanic);
        Set<UUID> successfulPlayers = new HashSet<>();
        for (Player player : players) {
            double distanceSquared = horizontalDistanceSquared(player.getLocation(), mechanic.origin);
            boolean safe = mechanic.insideSafe
                ? distanceSquared <= mechanic.radius * mechanic.radius
                : distanceSquared >= mechanic.radius * mechanic.radius;
            if (safe) {
                successfulPlayers.add(player.getUniqueId());
                continue;
            }
            punishMechanicFailure(player, boss, BossType.CORRUPTED_OATHKEEPER);
            player.setFireTicks(Math.max(player.getFireTicks(), mechanic.phase >= 3 ? 120 : 80));
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0, false, true, true));
        }
        if (mechanic.phase >= 3) {
            Set<UUID> brandVictims = new HashSet<>();
            for (UUID targetId : mechanic.targets) {
                Player target = Bukkit.getPlayer(targetId);
                if (!isEligibleMechanicTarget(boss, BossType.CORRUPTED_OATHKEEPER, target)) {
                    continue;
                }
                Location center = target.getLocation();
                for (Player player : players) {
                    if (!player.getUniqueId().equals(targetId)
                        && horizontalDistanceSquared(player.getLocation(), center) <= 3.1 * 3.1) {
                        brandVictims.add(player.getUniqueId());
                    }
                }
            }
            for (UUID victimId : brandVictims) {
                successfulPlayers.remove(victimId);
                Player victim = Bukkit.getPlayer(victimId);
                if (victim == null || victim.isDead() || victim.getWorld() != boss.getWorld()) {
                    continue;
                }
                punishMechanicFailure(victim, boss, BossType.CORRUPTED_OATHKEEPER);
                victim.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 90, 0, false, true, true));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 1, false, true, true));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1, false, true, true));
                victim.getWorld().spawnParticle(Particle.WITCH, victim.getLocation().clone().add(0.0, 1.0, 0.0), 28, 0.4, 0.55, 0.4, 0.08);
            }
        }
        for (Player player : players) {
            if (successfulPlayers.contains(player.getUniqueId())) {
                grantMechanicSuccessReward(player, BossType.CORRUPTED_OATHKEEPER, mechanic.kind);
            }
        }
        boss.getWorld().spawnParticle(Particle.FLAME, mechanic.origin.clone().add(0.0, 0.5, 0.0), 75, mechanic.radius * 0.3, 0.3, mechanic.radius * 0.3, 0.06);
        boss.getWorld().playSound(mechanic.origin, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.25f, mechanic.insideSafe ? 1.1f : 0.65f);

        if (mechanic.phase >= 3 && mechanic.stage == 0) {
            mechanic.stage = 1;
            mechanic.insideSafe = !mechanic.insideSafe;
            mechanic.warningEndsAt = now + 2_500L;
            mechanic.targets.clear();
            List<Player> survivors = eligibleMechanicPlayers(boss, BossType.CORRUPTED_OATHKEEPER, mechanic);
            int targetCount = BossMechanics.scaledObjectiveCount(survivors.size(), 2);
            mechanic.targets.addAll(randomMechanicTargets(survivors, targetCount));
            String secondRingWarning = mechanic.insideSafe
                ? "<green><bold>GET IN</bold></green> <red><bold>+ SPREAD</bold></red> <dark_red>| NO HEALING</dark_red>"
                : "<red><bold>GET OUT + SPREAD</bold></red> <dark_red>| NO HEALING</dark_red>";
            announceMechanicUpdate(
                boss,
                "<red><bold>OATH RING REVERSED</bold></red>",
                mechanic.insideSafe ? "<green>Get in and spread.</green>" : "<red>Get out and spread.</red>",
                secondRingWarning,
                Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,
                mechanic.insideSafe ? 1.25f : 0.62f
            );
            return false;
        }
        return true;
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
        enforceArenaBoundary(entity, type, arena);
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

        // Render the same wall around each player's height. Collision is horizontal-only,
        // so the wall remains solid all the way from the world's floor to build height.
        for (Player player : world.getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            double playerDistance = Math.sqrt(horizontalDistanceSquared(player.getLocation(), center));
            if (playerDistance < arena.radius() - 8.0 || playerDistance > arena.radius() + 4.0) {
                continue;
            }
            double playerY = Math.max(world.getMinHeight() + 1.0, Math.min(world.getMaxHeight() - 2.0, player.getY()));
            for (int i = 0; i < points; i += 4) {
                double angle = (Math.PI * 2.0 * i) / points;
                double x = Math.cos(angle) * arena.radius();
                double z = Math.sin(angle) * arena.radius();
                for (double y = -1.0; y <= 2.0; y += 1.0) {
                    player.spawnParticle(Particle.DUST, center.getX() + x, playerY + y, center.getZ() + z, 1, 0.0, 0.0, 0.0, 0.0, dust);
                }
            }
        }
    }

    private void enforceArenaBoundary(LivingEntity boss, BossType type, BossArena arena) {
        Location center = arena.center();
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        double horizontalDistance = Math.sqrt(horizontalDistanceSquared(boss.getLocation(), center));
        double edgePressure = BossMechanics.arenaEdgePressure(horizontalDistance, arena.radius());
        updateBossEdgeResistance(boss, type, edgePressure);
        if (edgePressure > 0.0) {
            steerBossFromArenaEdge(boss, center, edgePressure);
        }
        if (BossMechanics.needsHardArenaRecovery(horizontalDistance, arena.radius())) {
            recoverBossInsideArena(boss, type, arena);
        }

        for (Player player : world.getPlayers()) {
            if (!isArenaRestrictedPlayer(player, boss.getUniqueId(), arena)) {
                continue;
            }
            if (player.isGliding()) {
                player.setGliding(false);
            }
            Location confined = confinedLocation(player.getLocation(), arena, PLAYER_ARENA_ESCAPE_BUFFER);
            if (confined == null) {
                continue;
            }
            player.teleport(confined);
            Vector inward = center.toVector().subtract(confined.toVector()).setY(0.0);
            if (inward.lengthSquared() > 1.0E-6) {
                player.setVelocity(inward.normalize().multiply(0.35).setY(Math.min(0.0, player.getVelocity().getY())));
            }
            player.sendActionBar(MM.deserialize("<red>The arena refuses to let you leave.</red>"));
            world.spawnParticle(Particle.DUST, player.getLocation().clone().add(0.0, 1.0, 0.0), 8, 0.25, 0.35, 0.25, 0.0, new Particle.DustOptions(arena.color(), 0.9f));
        }
    }

    private void updateBossEdgeResistance(LivingEntity boss, BossType type, double edgePressure) {
        AttributeInstance resistance = boss.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (resistance == null) {
            return;
        }
        double desired = BossMechanics.edgeKnockbackResistance(type.knockbackResistance(), edgePressure);
        if (Math.abs(resistance.getBaseValue() - desired) > 0.001) {
            resistance.setBaseValue(desired);
        }
    }

    private void steerBossFromArenaEdge(LivingEntity boss, Location center, double edgePressure) {
        Vector outward = boss.getLocation().toVector().subtract(center.toVector()).setY(0.0);
        if (outward.lengthSquared() <= 1.0E-6) {
            return;
        }
        outward.normalize();
        Vector inward = outward.clone().multiply(-1.0);
        Vector velocity = boss.getVelocity().clone();
        double outwardSpeed = velocity.dot(outward);
        if (outwardSpeed > 0.0) {
            velocity.subtract(outward.clone().multiply(outwardSpeed * edgePressure * 0.90));
        }

        velocity.add(inward.clone().multiply(edgePressure * 0.36));
        double strafeDirection = (boss.getUniqueId().getLeastSignificantBits() & 1L) == 0L ? 1.0 : -1.0;
        Vector tangent = new Vector(-inward.getZ(), 0.0, inward.getX());
        velocity.add(tangent.multiply(strafeDirection * edgePressure * 0.055));

        double horizontalSpeed = Math.sqrt(velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ());
        double maximumHorizontalSpeed = 1.10 - edgePressure * 0.38;
        if (horizontalSpeed > maximumHorizontalSpeed) {
            double scale = maximumHorizontalSpeed / horizontalSpeed;
            velocity.setX(velocity.getX() * scale);
            velocity.setZ(velocity.getZ() * scale);
        }
        boss.setVelocity(velocity);
    }

    private void recoverBossInsideArena(LivingEntity boss, BossType type, BossArena arena) {
        Location center = arena.center();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Location from = boss.getLocation().clone();
        Vector outward = from.toVector().subtract(center.toVector()).setY(0.0);
        if (outward.lengthSquared() <= 1.0E-6) {
            return;
        }
        outward.normalize();

        double recoveryRadius = BossMechanics.arenaRecoveryRadius(arena.radius());
        Location projected = center.clone().add(outward.clone().multiply(recoveryRadius));
        projected.setY(from.getY());
        Location safe = findSafeBossSpawnLocation(projected, type);
        double maximumRecoveryDistance = arena.radius() * 0.80;
        if (safe == null || horizontalDistanceSquared(safe, center) > maximumRecoveryDistance * maximumRecoveryDistance) {
            safe = findSafeBossSpawnLocation(center.clone(), type);
        }
        if (safe == null || !teleportBoss(boss, safe)) {
            return;
        }

        Vector inward = center.toVector().subtract(safe.toVector()).setY(0.0);
        if (inward.lengthSquared() > 1.0E-6) {
            boss.setVelocity(inward.normalize().multiply(0.24));
        }
        world.spawnParticle(Particle.REVERSE_PORTAL, from.clone().add(0.0, 1.0, 0.0), 18, 0.35, 0.45, 0.35, 0.12);
        world.spawnParticle(Particle.DUST, safe.clone().add(0.0, 1.0, 0.0), 18, 0.35, 0.45, 0.35, 0.0, new Particle.DustOptions(arena.color(), 1.0f));
        world.playSound(safe, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.35f);
    }

    public Location confinedArenaLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        for (BossArena arena : bossArenas.values()) {
            if (arena.center().getWorld() == location.getWorld()) {
                Location confined = confinedLocation(location, arena, PLAYER_ARENA_ESCAPE_BUFFER);
                if (confined != null) {
                    return confined;
                }
            }
        }
        return null;
    }

    private Location confinedLocation(Location location, BossArena arena, double escapeBuffer) {
        Location center = arena.center();
        World world = center.getWorld();
        if (world == null || location.getWorld() != world) {
            return null;
        }
        double dx = location.getX() - center.getX();
        double dz = location.getZ() - center.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (!BossMechanics.shouldRecoverArenaPlayer(length, arena.radius(), escapeBuffer)) {
            return null;
        }
        double safeRadius = BossMechanics.playerArenaRecoveryRadius(arena.radius());
        Location confined = location.clone();
        confined.setX(center.getX() + (dx / length) * safeRadius);
        confined.setZ(center.getZ() + (dz / length) * safeRadius);
        confined.setY(Math.max(world.getMinHeight() + 1.0, Math.min(world.getMaxHeight() - 2.0, location.getY())));
        return confined;
    }

    public boolean isPlayerRestrictedByArena(Player player) {
        if (!isFightEligiblePlayer(player)) {
            return false;
        }
        for (Map.Entry<UUID, BossArena> entry : bossArenas.entrySet()) {
            UUID bossId = entry.getKey();
            BossArena arena = entry.getValue();
            Entity boss = Bukkit.getEntity(bossId);
            if (!(boss instanceof LivingEntity living) || boss.isDead() || !boss.isValid()
                || living.getWorld() != player.getWorld() || arena.center().getWorld() != player.getWorld()) {
                continue;
            }
            if (isArenaRestrictedPlayer(player, bossId, arena)) {
                return true;
            }
        }
        return false;
    }

    private boolean isArenaRestrictedPlayer(Player player, UUID bossId, BossArena arena) {
        if (!isFightEligiblePlayer(player) || arena.center().getWorld() != player.getWorld()) {
            return false;
        }
        Set<UUID> arenaPlayers = bossArenaPlayers.computeIfAbsent(bossId, ignored -> ConcurrentHashMap.newKeySet());
        boolean tracked = arenaPlayers.contains(player.getUniqueId());
        double distance = Math.sqrt(horizontalDistanceSquared(player.getLocation(), arena.center()));
        if (!BossMechanics.isArenaRestrictedPlayer(tracked, distance, arena.radius())) {
            return false;
        }
        arenaPlayers.add(player.getUniqueId());
        return true;
    }

    static double horizontalDistanceSquared(Location first, Location second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
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

            player.damage(scaledBossAbilityDamage(boss, 2.0), boss);
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
        if (plugin.getBossDungeonManager() != null && plugin.getBossDungeonManager().isDungeonWorld(boss.getWorld())) {
            plugin.getBossDungeonManager().onBossFightFinished(false, null);
        }
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

    private void ensureBossAiState(LivingEntity boss, BossType type) {
        if (!(boss instanceof Mob mob)) {
            return;
        }
        if (!mob.isAware()) {
            mob.setAware(true);
        }
        mob.setPersistent(true);
        mob.setRemoveWhenFarAway(false);

        BossArena arena = bossArenas.get(boss.getUniqueId());
        double arenaRange = arena == null ? type.ritual().arenaRadius() : arena.radius();
        double minimumFollowRange = Math.max(type.followRange(), arenaRange * 2.0D + 8.0D);
        AttributeInstance followRange = boss.getAttribute(Attribute.FOLLOW_RANGE);
        if (followRange != null && followRange.getBaseValue() + 0.001D < minimumFollowRange) {
            followRange.setBaseValue(minimumFollowRange);
        }
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
        return horizontalDistanceSquared(player.getLocation(), center) <= radius * radius;
    }

    private void retargetBossByAggro(LivingEntity boss, BossType type) {
        if (!(boss instanceof Mob mob) || boss.isDead() || !boss.isValid()) {
            return;
        }
        long now = System.currentTimeMillis();
        PersistentDataContainer pdc = boss.getPersistentDataContainer();
        BossFightState state = bossFightStates.get(boss.getUniqueId());
        LivingEntity existingTarget = mob.getTarget();
        Player current = existingTarget instanceof Player player
            && isFightEligiblePlayer(player)
            && isPlayerInFightArea(player, boss, type)
            ? player
            : null;
        if (existingTarget != null && current == null) {
            mob.setTarget(null);
        }
        Long nextAt = pdc.get(keyBossAggroRetargetAt, PersistentDataType.LONG);
        if (current != null && nextAt != null && nextAt > now) {
            return;
        }
        pdc.set(keyBossAggroRetargetAt, PersistentDataType.LONG, now + 2_000L);

        Player best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Player player : boss.getWorld().getPlayers()) {
            if (!isFightEligiblePlayer(player) || !isPlayerInFightArea(player, boss, type)) {
                continue;
            }
            double score = bossAggroScore(boss, state, player, current);
            if (score > bestScore) {
                bestScore = score;
                best = player;
            }
        }
        if (best == null) {
            if (mob.getTarget() != null) {
                mob.setTarget(null);
            }
            return;
        }
        if (current == null) {
            mob.setTarget(best);
            return;
        }
        if (!current.equals(best)) {
            double currentScore = bossAggroScore(boss, state, current, current);
            if (BossMechanics.shouldSwitchAggroTarget(currentScore, bestScore, 1.25D)) {
                mob.setTarget(best);
            }
        }
    }

    private double bossAggroScore(LivingEntity boss, BossFightState state, Player player, Player currentTarget) {
        double score = 0.0D;
        if (state != null) {
            score += state.damageFor(player.getUniqueId()) / 18.0D;
        }
        if (plugin.getVeilOrbManager() != null) {
            score += plugin.getVeilOrbManager().aggroBonus(player);
        }
        double distance = Math.max(1.0D, boss.getLocation().distance(player.getLocation()));
        score += Math.max(0.0D, 4.0D - (distance / 8.0D));
        if (player.equals(currentTarget)) {
            score += 1.75D;
        }
        return score;
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
        bossArenaPlayers.computeIfAbsent(bossId, ignored -> ConcurrentHashMap.newKeySet()).add(player.getUniqueId());
        BossRecord record = trackedBosses.get(bossId);
        BossFightState state = record == null
            ? bossFightStates.computeIfAbsent(bossId, ignored -> new BossFightState(System.currentTimeMillis()))
            : fightState(record);
        state.touch(player);
        if (plugin.getLegendaryAltarManager() != null) {
            plugin.getLegendaryAltarManager().refreshBossBarFor(player);
        }
    }

    private void removePlayerFromBossArenaRosters(UUID playerId) {
        if (playerId == null) {
            return;
        }
        for (Set<UUID> arenaPlayers : bossArenaPlayers.values()) {
            arenaPlayers.remove(playerId);
        }
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
        double totalDamage = participants.stream().mapToDouble(BossFightParticipant::damageDone).sum();
        double totalHealing = participants.stream().mapToDouble(BossFightParticipant::healingReceived).sum();
        double damageTaken = participants.stream().mapToDouble(participant -> participant.damageTaken).sum();
        double blockedHealing = participants.stream().mapToDouble(participant -> participant.blockedHealing).sum();
        int mechanicFailures = participants.stream().mapToInt(participant -> participant.mechanicFailures).sum();
        plugin.getLogger().info(
            "Boss fight report: boss=" + type.id()
                + " outcome=" + (victory ? "victory" : "failure")
                + " duration_ms=" + Math.max(0L, endedAt - state.startedAt())
                + " participants=" + participants.size()
                + " peak_scaled_players=" + state.peakScaledPlayers
                + " damage=" + trimNumber(totalDamage)
                + " damage_taken=" + trimNumber(damageTaken)
                + " healing=" + trimNumber(totalHealing)
                + " blocked_healing=" + trimNumber(blockedHealing)
                + " mechanic_failures=" + mechanicFailures
        );
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

    private void announceSoulImprintDrop(Player killer, BossFightState state, Location location) {
        String teamName = null;
        if (killer != null && plugin.getTeamManager() != null) {
            teamName = plugin.getTeamManager().teamDisplayName(killer.getUniqueId());
        }
        if (teamName == null || teamName.isBlank()) {
            teamName = killer == null ? "Solo" : "Solo - " + killer.getName();
        }

        BossFightParticipant top = null;
        if (state != null) {
            top = state.sortedParticipants().stream()
                .filter(participant -> participant.damageDone() > 0.0)
                .findFirst()
                .orElse(null);
        }
        String topDamage = top == null
            ? "No damage recorded"
            : top.playerName() + " - " + trimNumber(top.damageDone()) + " dmg";

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.sendMessage(MessageUtil.prefixedRaw(
                "<gradient:#a78bfa:#facc15><bold>A " + soulImprintName(viewer) + " has fallen.</bold></gradient>"
            ));
            viewer.sendMessage(MessageUtil.prefixedRaw(
                "<gray>Team:</gray> <white>" + escapeMiniMessage(teamName) + "</white>"
                    + " <dark_gray>|</dark_gray> <gray>Top damage:</gray> <white>" + escapeMiniMessage(topDamage) + "</white>"
            ));
            viewer.playSound(viewer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 0.95f);
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.25f);
        }

        World world = location == null ? null : location.getWorld();
        if (world != null) {
            Location center = location.clone().add(0.0, 1.25, 0.0);
            world.playSound(center, Sound.BLOCK_END_PORTAL_SPAWN, 1.1f, 1.35f);
            world.spawnParticle(Particle.END_ROD, center, 100, 1.4, 1.0, 1.4, 0.08);
            world.spawnParticle(Particle.PORTAL, center, 140, 1.8, 1.2, 1.8, 0.12);
        }
    }

    private String escapeMiniMessage(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("<", "\\<");
    }

    private boolean startMarshalStack(LivingEntity boss, int phase) {
        List<Player> players = eligibleBossPlayers(boss, BossType.YULE_THE_MINION);
        if (players.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        ActiveBossMechanic mechanic = new ActiveBossMechanic(
            BossMechanicKind.MARSHAL_STACK,
            phase,
            now,
            now + 3_000L,
            now + 3_000L,
            boss.getLocation().clone()
        );
        mechanic.targets.add(players.get(ThreadLocalRandom.current().nextInt(players.size())).getUniqueId());
        return beginBossMechanic(
            boss,
            mechanic,
            keyBossSecondaryCooldown,
            phase >= 2 ? 10_500L : 13_000L,
            "<aqua><bold>HOLD THE LINE - STACK ON BLUE</bold></aqua>",
            Sound.BLOCK_BELL_RESONATE,
            0.8f
        );
    }

    private boolean startAshenCrossfire(LivingEntity boss, int phase) {
        List<Player> players = eligibleBossPlayers(boss, BossType.KAEL_THE_ASHEN);
        if (players.isEmpty()) {
            return false;
        }
        int targetCount = Math.min(players.size(), BossMechanics.scaledObjectiveCount(players.size(), phase >= 2 ? 3 : 2));
        long now = System.currentTimeMillis();
        ActiveBossMechanic mechanic = new ActiveBossMechanic(
            BossMechanicKind.ASHEN_CROSSFIRE,
            phase,
            now,
            now + 3_000L,
            now + 3_000L,
            boss.getLocation().clone()
        );
        mechanic.targets.addAll(randomMechanicTargets(players, targetCount));
        return beginBossMechanic(
            boss,
            mechanic,
            keyBossTertiaryCooldown,
            phase >= 2 ? 10_500L : 13_500L,
            "<red><bold>ASHEN CROSSFIRE - SPREAD THE MARKERS</bold></red>",
            Sound.ENTITY_SKELETON_SHOOT,
            0.65f
        );
    }

    private boolean startAshenDeadeye(LivingEntity boss) {
        List<Player> players = eligibleBossPlayers(boss, BossType.KAEL_THE_ASHEN);
        if (players.isEmpty()) {
            return false;
        }
        Player marked = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        long now = System.currentTimeMillis();
        ActiveBossMechanic mechanic = new ActiveBossMechanic(
            BossMechanicKind.ASHEN_DEADEYE,
            2,
            now,
            now + 4_000L,
            now + 4_000L,
            boss.getLocation().clone()
        );
        mechanic.targets.add(marked.getUniqueId());
        mechanic.nextStepAt = now + 2_250L;
        return beginBossMechanic(
            boss,
            mechanic,
            keyBossPrimaryCooldown,
            15_000L,
            "<yellow><bold>DEADEYE - THE AIM LINE WILL LOCK</bold></yellow>",
            Sound.ENTITY_SKELETON_SHOOT,
            0.45f
        );
    }

    private boolean startWidowsTrail(LivingEntity boss, int phase) {
        List<Player> players = eligibleBossPlayers(boss, BossType.VESPER_THE_WIDOW_QUEEN);
        Player hunted = players.stream()
            .max(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(boss.getLocation())))
            .orElse(null);
        if (hunted == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        ActiveBossMechanic mechanic = new ActiveBossMechanic(
            BossMechanicKind.WIDOWS_TRAIL,
            phase,
            now,
            now + 1_000L,
            now + (phase >= 2 ? 7_000L : 6_000L),
            hunted.getLocation().clone()
        );
        mechanic.targets.add(hunted.getUniqueId());
        mechanic.nextStepAt = mechanic.warningEndsAt;
        return beginBossMechanic(
            boss,
            mechanic,
            keyBossSecondaryCooldown,
            phase >= 2 ? 12_000L : 15_000L,
            "<red><bold>WIDOW'S CLAIM - KEEP MOVING</bold></red>",
            Sound.ENTITY_SPIDER_AMBIENT,
            0.55f
        );
    }

    private boolean startWidowsWebbreak(LivingEntity boss) {
        List<Player> players = eligibleBossPlayers(boss, BossType.VESPER_THE_WIDOW_QUEEN);
        if (players.isEmpty()) {
            return false;
        }
        int count = BossMechanics.scaledObjectiveCount(players.size(), 2);
        long now = System.currentTimeMillis();
        ActiveBossMechanic mechanic = new ActiveBossMechanic(
            BossMechanicKind.WIDOWS_WEBBREAK,
            2,
            now,
            now + 4_000L,
            now + 4_000L,
            boss.getLocation().clone()
        );
        mechanic.radius = 6.0;
        mechanic.targets.addAll(randomMechanicTargets(players, count));
        for (UUID targetId : mechanic.targets) {
            Player target = Bukkit.getPlayer(targetId);
            mechanic.points.add(target == null ? boss.getLocation().clone() : target.getLocation().clone());
        }
        return beginBossMechanic(
            boss,
            mechanic,
            keyBossTertiaryCooldown,
            14_000L,
            "<red><bold>WEBBREAK - MARKED PLAYERS RUN SIX BLOCKS</bold></red>",
            Sound.BLOCK_COBWEB_PLACE,
            0.55f
        );
    }

    private boolean startRootWards(LivingEntity boss, int phase) {
        List<Player> players = eligibleBossPlayers(boss, BossType.MIREWOOD_THE_ROOT_TYRANT);
        if (players.isEmpty()) {
            return false;
        }
        int count = BossMechanics.scaledObjectiveCount(players.size(), 3);
        long now = System.currentTimeMillis();
        Location center = bossMechanicCenter(boss);
        ActiveBossMechanic mechanic = new ActiveBossMechanic(
            BossMechanicKind.ROOT_WARDS,
            phase,
            now,
            now + 4_000L,
            now + 4_000L,
            center
        );
        mechanic.points.addAll(mechanicPoints(center, count, count == 1 ? 5.0 : 6.5));
        return beginBossMechanic(
            boss,
            mechanic,
            keyBossSecondaryCooldown,
            phase >= 2 ? 11_000L : 14_000L,
            "<green><bold>ROOT WARDS - FILL EVERY GREEN CIRCLE</bold></green>",
            Sound.BLOCK_ROOTED_DIRT_PLACE,
            0.72f
        );
    }

    private boolean startBriarLattice(LivingEntity boss) {
        long now = System.currentTimeMillis();
        Location center = bossMechanicCenter(boss);
        ActiveBossMechanic mechanic = new ActiveBossMechanic(
            BossMechanicKind.BRIAR_LATTICE,
            2,
            now,
            now + 3_750L,
            now + 3_750L,
            center
        );
        mechanic.angle = ThreadLocalRandom.current().nextDouble(Math.PI / 2.0);
        mechanic.radius = bossMechanicArenaRadius(boss, 13.0);
        return beginBossMechanic(
            boss,
            mechanic,
            keyBossPrimaryCooldown,
            13_500L,
            "<red><bold>BRIAR LATTICE - LEAVE THE RED ROOT LINES</bold></red>",
            Sound.BLOCK_ROOTED_DIRT_PLACE,
            0.5f
        );
    }

    private boolean startUndertow(LivingEntity boss, int phase) {
        long now = System.currentTimeMillis();
        ActiveBossMechanic mechanic = new ActiveBossMechanic(
            BossMechanicKind.UNDERTOW,
            phase,
            now,
            now + 1_500L,
            now + 4_000L,
            boss.getLocation().clone()
        );
        mechanic.radius = scaledBossAbilityRadius(boss, phase >= 2 ? 9.0 : 7.0);
        return beginBossMechanic(
            boss,
            mechanic,
            keyBossSecondaryCooldown,
            phase >= 2 ? 9_000L : 11_500L,
            "<aqua><bold>UNDERTOW - BRACE FOR THE PULL</bold></aqua>",
            Sound.ENTITY_ELDER_GUARDIAN_CURSE,
            0.8f
        );
    }

    private boolean startTidalDivide(LivingEntity boss) {
        long now = System.currentTimeMillis();
        Location center = bossMechanicCenter(boss);
        ActiveBossMechanic mechanic = new ActiveBossMechanic(
            BossMechanicKind.TIDAL_DIVIDE,
            2,
            now,
            now + 3_750L,
            now + 3_750L,
            center
        );
        mechanic.angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);
        mechanic.radius = bossMechanicArenaRadius(boss, 13.0);
        return beginBossMechanic(
            boss,
            mechanic,
            keyBossPrimaryCooldown,
            13_500L,
            "<green><bold>TIDAL DIVIDE - CROSS GREEN</bold></green> <dark_red><bold>| HEALING SEALED</bold></dark_red>",
            Sound.BLOCK_CONDUIT_ACTIVATE,
            0.65f
        );
    }

    private boolean startSaintsStagger(LivingEntity boss, int phase) {
        long now = System.currentTimeMillis();
        ActiveBossMechanic mechanic = new ActiveBossMechanic(
            BossMechanicKind.SAINTS_STAGGER,
            phase,
            now,
            now + 5_000L,
            now + 10_000L,
            boss.getLocation().clone()
        );
        double maxHealth = boss.getAttribute(Attribute.MAX_HEALTH) == null ? boss.getHealth() : boss.getAttribute(Attribute.MAX_HEALTH).getValue();
        mechanic.threshold = BossMechanics.staggerThreshold(maxHealth, scaledBossPlayerCount(boss));
        return beginBossMechanic(
            boss,
            mechanic,
            keyBossSecondaryCooldown,
            phase >= 2 ? 16_000L : 19_000L,
            "<yellow><bold>SAINT'S STAGGER - BREAK HIS GUARD</bold></yellow>",
            Sound.ITEM_SHIELD_BLOCK,
            0.55f
        );
    }

    private boolean startIronCounterstance(LivingEntity boss) {
        long now = System.currentTimeMillis();
        ActiveBossMechanic mechanic = new ActiveBossMechanic(
            BossMechanicKind.IRON_COUNTERSTANCE,
            2,
            now,
            now + 3_000L,
            now + 7_000L,
            boss.getLocation().clone()
        );
        return beginBossMechanic(
            boss,
            mechanic,
            keyBossPrimaryCooldown,
            16_000L,
            "<red><bold>COUNTERSTANCE - STOP ATTACKING | HEALING SEALED</bold></red>",
            Sound.ITEM_SHIELD_BLOCK,
            0.42f
        );
    }

    private boolean startRiftSectors(LivingEntity boss, int phase) {
        long now = System.currentTimeMillis();
        Location center = bossMechanicCenter(boss);
        ActiveBossMechanic mechanic = new ActiveBossMechanic(
            BossMechanicKind.RIFT_SECTORS,
            phase,
            now,
            now + 3_500L,
            now + 3_500L,
            center
        );
        mechanic.angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);
        mechanic.radius = bossMechanicArenaRadius(boss, 13.0);
        return beginBossMechanic(
            boss,
            mechanic,
            keyBossSecondaryCooldown,
            phase >= 2 ? 10_000L : 13_000L,
            phase >= 2
                ? "<green><bold>RIFT SECTORS - FIND GREEN</bold></green> <dark_red><bold>| HEALING SEALED</bold></dark_red>"
                : "<green><bold>RIFT SECTORS - FIND THE GREEN WEDGE</bold></green>",
            Sound.ENTITY_ENDERMAN_STARE,
            1.25f
        );
    }

    private boolean startRunebloomSigils(LivingEntity boss, int phase) {
        List<Player> players = eligibleBossPlayers(boss, BossType.MORVESSA_THE_RUNEBLOOM_WITCH);
        if (players.isEmpty()) {
            return false;
        }
        int count = BossMechanics.scaledObjectiveCount(players.size(), 3);
        long now = System.currentTimeMillis();
        Location center = bossMechanicCenter(boss);
        ActiveBossMechanic mechanic = new ActiveBossMechanic(
            BossMechanicKind.RUNEBLOOM_SIGILS,
            phase,
            now,
            now + 4_500L,
            now + 4_500L,
            center
        );
        mechanic.points.addAll(mechanicPoints(center, count, count == 1 ? 6.0 : 7.5));
        return beginBossMechanic(
            boss,
            mechanic,
            keyBossSecondaryCooldown,
            phase >= 2 ? 11_500L : 14_000L,
            "<green><bold>RUNEBLOOM SIGILS - SOAK EVERY CIRCLE</bold></green>",
            Sound.BLOCK_BREWING_STAND_BREW,
            0.72f
        );
    }

    private boolean startPetalstorm(LivingEntity boss) {
        long now = System.currentTimeMillis();
        Location center = bossMechanicCenter(boss);
        ActiveBossMechanic mechanic = new ActiveBossMechanic(
            BossMechanicKind.PETALSTORM,
            2,
            now,
            now + 2_500L,
            now + 8_500L,
            center
        );
        mechanic.angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);
        mechanic.radius = bossMechanicArenaRadius(boss, 14.0);
        return beginBossMechanic(
            boss,
            mechanic,
            keyBossTertiaryCooldown,
            20_000L,
            "<light_purple><bold>PETALSTORM - DODGE THE BEAM</bold></light_purple> <dark_red><bold>| HEALING SEALED</bold></dark_red>",
            Sound.ENTITY_WITCH_THROW,
            0.6f
        );
    }

    private boolean startResonanceLock(LivingEntity boss, int phase) {
        long now = System.currentTimeMillis();
        ActiveBossMechanic mechanic = new ActiveBossMechanic(
            BossMechanicKind.RESONANCE_LOCK,
            phase,
            now,
            now + 3_500L,
            now + 3_500L,
            boss.getLocation().clone()
        );
        return beginBossMechanic(
            boss,
            mechanic,
            keyBossSecondaryCooldown,
            phase >= 2 ? 10_500L : 13_500L,
            "<light_purple><bold>RESONANCE - LOOK AWAY</bold></light_purple> <dark_red><bold>| HEALING SEALED</bold></dark_red>",
            Sound.BLOCK_SCULK_SHRIEKER_SHRIEK,
            0.65f
        );
    }

    private boolean startOathRings(LivingEntity boss, int phase) {
        long now = System.currentTimeMillis();
        Location center = bossMechanicCenter(boss);
        ActiveBossMechanic mechanic = new ActiveBossMechanic(
            BossMechanicKind.OATH_RINGS,
            phase,
            now,
            now + 3_000L,
            now + (phase >= 3 ? 5_500L : 3_000L),
            center
        );
        mechanic.radius = scaledBossAbilityRadius(boss, phase >= 3 ? 8.0 : phase >= 2 ? 7.0 : 6.0);
        mechanic.insideSafe = ThreadLocalRandom.current().nextBoolean();
        if (phase >= 3) {
            List<Player> players = eligibleBossPlayers(boss, BossType.CORRUPTED_OATHKEEPER);
            mechanic.targets.addAll(randomMechanicTargets(players, BossMechanics.scaledObjectiveCount(players.size(), 2)));
        }
        return beginBossMechanic(
            boss,
            mechanic,
            keyBossPrimaryCooldown,
            phase >= 3 ? 9_000L : phase >= 2 ? 11_500L : 14_000L,
            phase >= 3
                ? (mechanic.insideSafe
                    ? "<green><bold>GET IN</bold></green> <red><bold>+ SPREAD</bold></red> <dark_red>| NO HEALING</dark_red>"
                    : "<red><bold>GET OUT + SPREAD</bold></red> <dark_red>| NO HEALING</dark_red>")
                : (mechanic.insideSafe
                    ? "<green><bold>OATH RING - GET IN</bold></green> <dark_red>| NO HEALING</dark_red>"
                    : "<red><bold>OATH RING - GET OUT</bold></red> <dark_red>| NO HEALING</dark_red>"),
            Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,
            mechanic.insideSafe ? 1.25f : 0.62f
        );
    }

    private List<Location> mechanicPoints(Location center, int count, double radius) {
        List<Location> points = new ArrayList<>();
        double offset = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);
        for (int i = 0; i < Math.max(1, count); i++) {
            double angle = offset + Math.PI * 2.0 * i / Math.max(1, count);
            points.add(center.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius));
        }
        return points;
    }

    private void tickYuleTheMinion(LivingEntity entity) {
        int phase = bossPhase(entity);
        double maxHealth = Math.max(1.0, entity.getAttribute(Attribute.MAX_HEALTH) == null ? 1.0 : entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        if (phase < 2 && entity.getHealth() <= maxHealth * 0.50) {
            setBossPhase(entity, 2);
            phase = 2;
            entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0, false, true, true));
            spawnYulePhaseTwoMinions(entity);
            entity.getWorld().spawnParticle(Particle.DUST, entity.getLocation().clone().add(0.0, 1.0, 0.0), 28, 0.35, 0.55, 0.35, 0.0, new Particle.DustOptions(Color.fromRGB(178, 34, 34), 1.2f));
            entity.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, entity.getLocation().clone().add(0.0, 1.1, 0.0), 12, 0.25, 0.40, 0.25, 0.0);
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.9f, 0.65f);
        }
        if (currentBossTarget(entity) != null && bossCooldownReady(entity, keyBossSecondaryCooldown)) {
            startMarshalStack(entity, phase);
        }
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
                zombie.customName(MM.deserialize("<red>Veilbound Thrall</red>"));
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
        if (target == null) {
            return;
        }
        int phase = bossPhase(entity);
        if (phase >= 2 && bossCooldownReady(entity, keyBossPrimaryCooldown) && startAshenDeadeye(entity)) {
            return;
        }
        if (bossCooldownReady(entity, keyBossTertiaryCooldown) && startAshenCrossfire(entity, phase)) {
            return;
        }
        if (!bossCooldownReady(entity, keyBossSecondaryCooldown)) {
            return;
        }
        double shotRange = scaledBossAbilityRadius(entity, 18.0);
        if (entity.getLocation().distanceSquared(target.getLocation()) > shotRange * shotRange || !entity.hasLineOfSight(target)) {
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
        if (target == null) {
            return;
        }
        int phase = bossPhase(entity);
        if (bossCooldownReady(entity, keyBossSecondaryCooldown) && startWidowsTrail(entity, phase)) {
            return;
        }
        if (phase >= 2 && bossCooldownReady(entity, keyBossTertiaryCooldown) && startWidowsWebbreak(entity)) {
            return;
        }
        if (!entity.isOnGround() || !bossCooldownReady(entity, keyBossPrimaryCooldown)) {
            return;
        }

        double distanceSquared = entity.getLocation().distanceSquared(target.getLocation());
        double leapRange = scaledBossAbilityRadius(entity, 12.0);
        if (distanceSquared < 3.5 * 3.5 || distanceSquared > leapRange * leapRange) {
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
        double pulseRange = scaledBossAbilityRadius(entity, 8.0);
        if (bossCooldownReady(entity, keyBossPrimaryCooldown) && distanceSquared <= pulseRange * pulseRange) {
            int phase = bossPhase(entity);
            if (telegraphAreaAbility(
                entity,
                "Dominion pulse - move away!",
                scaledBossAbilityRadius(entity, phase >= 2 ? 7.0 : 6.0),
                24L,
                Color.fromRGB(205, 30, 55),
                () -> unleashDominionPulse(entity, phase)
            )) {
                return;
            }
        }
        double resonanceRange = scaledBossAbilityRadius(entity, 18.0);
        if (bossCooldownReady(entity, keyBossSecondaryCooldown)
            && distanceSquared <= resonanceRange * resonanceRange) {
            startResonanceLock(entity, bossPhase(entity));
        }
    }

    private void spawnYuleAttackParticles(LivingEntity attacker, LivingEntity target, int phase) {
        World world = target.getWorld();
        Location center = target.getLocation().clone().add(0.0, 1.0, 0.0);
        if (phase >= 2) {
            world.spawnParticle(Particle.DUST, center, 14, 0.28, 0.38, 0.28, 0.0, new Particle.DustOptions(Color.fromRGB(185, 35, 35), 1.05f));
            world.spawnParticle(Particle.CRIT, center, 10, 0.25, 0.35, 0.25, 0.02);
        } else {
            world.spawnParticle(Particle.CRIT, center, 8, 0.22, 0.32, 0.22, 0.02);
            world.spawnParticle(Particle.SMOKE, center, 6, 0.18, 0.22, 0.18, 0.01);
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
            arrow.setDamage(arrow.getDamage() + scaledBossAbilityDamage(shooter, phase >= 2 ? 3.0 : 1.5));
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
            event.setDamage(event.getDamage() + scaledBossAbilityDamage(attacker, 2.0));
            Vector push = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
            if (push.lengthSquared() > 1.0E-6) {
                push.normalize().multiply(0.45).setY(Math.max(0.18, target.getVelocity().getY()));
                target.setVelocity(target.getVelocity().add(push));
            }
        } else {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0, false, true, true));
            event.setDamage(event.getDamage() + scaledBossAbilityDamage(attacker, 1.0));
        }
    }

    private void handleVesperMeleeHit(LivingEntity attacker, LivingEntity target, int phase) {
        World world = target.getWorld();
        Location center = target.getLocation().clone().add(0.0, 0.9, 0.0);
        world.spawnParticle(Particle.SQUID_INK, center, 10, 0.18, 0.20, 0.18, 0.01);
        world.spawnParticle(Particle.DUST, center, 12, 0.22, 0.25, 0.22, 0.0, new Particle.DustOptions(Color.fromRGB(88, 160, 64), phase >= 2 ? 1.15f : 0.9f));

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
        double radius = scaledBossAbilityRadius(entity, phase >= 2 ? 7.0 : 6.0);

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
            target.damage(scaledBossAbilityDamage(entity, phase >= 2 ? 8.0 : 5.0), entity);
        }

        setBossCooldown(entity, keyBossPrimaryCooldown, phase >= 2 ? 5200L : 7000L);
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

        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, phase >= 2 ? 100 : 60, 0, false, true, true));
        if (phase >= 2) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 0, false, true, true));
            event.setDamage(event.getDamage() + scaledBossAbilityDamage(attacker, 2.0));
            Vector slam = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
            if (slam.lengthSquared() > 1.0E-6) {
                slam.normalize().multiply(0.85);
                slam.setY(Math.max(0.28, target.getVelocity().getY()));
                target.setVelocity(target.getVelocity().add(slam));
            }
        } else {
            event.setDamage(event.getDamage() + scaledBossAbilityDamage(attacker, 1.0));
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
        if (target == null) {
            return;
        }
        int phase = bossPhase(entity);
        if (bossCooldownReady(entity, keyBossPrimaryCooldown)) {
            riftStepAroundTarget(entity, target, phase);
        }
        if (bossCooldownReady(entity, keyBossSecondaryCooldown)) {
            startRiftSectors(entity, phase);
        }
    }

    private void riftStepAroundTarget(LivingEntity entity, LivingEntity target, int phase) {
        if (entity.getLocation().distanceSquared(target.getLocation()) < 4.0 * 4.0) {
            setBossCooldown(entity, keyBossPrimaryCooldown, 1800L);
            return;
        }
        Location from = entity.getLocation().clone();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);
            double distance = ThreadLocalRandom.current().nextDouble(3.5, 6.0);
            Location seed = target.getLocation().clone().add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
            Location safe = findSafeBossSpawnLocation(seed, BossType.AURELION_THE_RIFT_SERAPH);
            if (safe == null || !teleportBoss(entity, safe)) {
                continue;
            }
            entity.getWorld().spawnParticle(Particle.REVERSE_PORTAL, from.clone().add(0.0, 1.0, 0.0), 40, 0.45, 0.6, 0.45, 0.18);
            entity.getWorld().spawnParticle(Particle.PORTAL, safe.clone().add(0.0, 1.0, 0.0), 55, 0.55, 0.7, 0.55, 0.25);
            entity.getWorld().playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 0.9f, 0.8f);
            entity.getWorld().playSound(safe, Sound.ENTITY_ENDERMAN_TELEPORT, 1.1f, 1.15f);
            setBossCooldown(entity, keyBossPrimaryCooldown, phase >= 2 ? 4200L : 6200L);
            return;
        }
        setBossCooldown(entity, keyBossPrimaryCooldown, 2200L);
    }

    private void tickMorvessaTheRunebloomWitch(LivingEntity entity) {
        double maxHealth = Math.max(1.0, entity.getAttribute(Attribute.MAX_HEALTH) == null
            ? 1.0
            : entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        int phase = bossPhase(entity);
        if (phase < 2 && entity.getHealth() <= maxHealth * 0.60) {
            setBossPhase(entity, 2);
            phase = 2;
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, true, true));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, true, true));
            entity.getWorld().spawnParticle(Particle.WITCH, entity.getLocation().clone().add(0.0, 1.0, 0.0), 90, 1.1, 0.8, 1.1, 0.10);
            entity.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, entity.getLocation().clone().add(0.0, 0.8, 0.0), 70, 1.2, 0.5, 1.2, 0.06);
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 1.2f, 0.62f);
            summonRunebloomFamiliars(entity, phase);
        }

        LivingEntity target = currentBossTarget(entity);
        if (target == null) {
            return;
        }

        if (bossCooldownReady(entity, keyBossSecondaryCooldown) && startRunebloomSigils(entity, phase)) {
            return;
        }
        if (phase >= 2 && bossCooldownReady(entity, keyBossTertiaryCooldown) && startPetalstorm(entity)) {
            return;
        }

        if (phase >= 2 && bossCooldownReady(entity, keyBossPrimaryCooldown)
            && countNearbyBossMinions(entity, 28.0) < 2) {
            summonRunebloomFamiliars(entity, phase);
        }
        if (bossCooldownReady(entity, keyBossPressureCooldown)) {
            castRunebloomHex(entity, target, phase);
        }
    }

    private void castRunebloomHex(LivingEntity boss, LivingEntity target, int phase) {
        if (!isEligibleBossVictim(boss, target)) {
            setBossCooldown(boss, keyBossPressureCooldown, 1_500L);
            return;
        }
        double damage = scaledBossAbilityDamage(
            boss,
            BossType.MORVESSA_THE_RUNEBLOOM_WITCH.attackDamage() * (phase >= 2 ? 0.45 : 0.35)
        );
        target.damage(damage, boss);
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, phase >= 2 ? 70 : 45, 0, false, true, true));
        target.getWorld().spawnParticle(Particle.WITCH, target.getLocation().clone().add(0.0, 1.0, 0.0), 24, 0.35, 0.45, 0.35, 0.08);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITCH_THROW, 0.8f, phase >= 2 ? 0.52f : 0.72f);
        setBossCooldown(boss, keyBossPressureCooldown, phase >= 2 ? 4_000L : 5_000L);
    }

    private void summonRunebloomFamiliars(LivingEntity boss, int phase) {
        BossRecord record = bossRecord(boss);
        if (record == null || boss.getWorld() == null) {
            return;
        }

        int count = scaledBossMinionCount(boss, 2);
        LivingEntity currentTarget = currentBossTarget(boss);
        World world = boss.getWorld();
        Location origin = boss.getLocation();
        for (int i = 0; i < count; i++) {
            double angle = ((Math.PI * 2.0) / count) * i;
            Location spawn = findGroundedSpawn(world, origin.clone().add(Math.cos(angle) * 3.5, 0.0, Math.sin(angle) * 3.5));
            Spider familiar = world.spawn(spawn, Spider.class, spider -> {
                spider.setPersistent(true);
                spider.setRemoveWhenFarAway(false);
                spider.setCanPickupItems(false);
                spider.customName(MM.deserialize("<green>Runebloom Familiar</green>"));
                spider.setCustomNameVisible(false);
            });
            double health = scaledBossSummonHealth(boss, phase >= 2 ? 65.0 : 48.0);
            setAttributeBase(familiar, Attribute.MAX_HEALTH, health);
            familiar.setHealth(health);
            setAttributeBase(familiar, Attribute.ATTACK_DAMAGE, scaledBossAbilityDamage(boss, phase >= 2 ? 8.0 : 6.0));
            setAttributeBase(familiar, Attribute.MOVEMENT_SPEED, 0.38);
            setAttributeBase(familiar, Attribute.FOLLOW_RANGE, 32.0);
            setAttributeBase(familiar, Attribute.KNOCKBACK_RESISTANCE, 0.30);
            markBossMinion(familiar, record.entityUuid());
            if (currentTarget != null && currentTarget.isValid() && !currentTarget.isDead()) {
                familiar.setTarget(currentTarget);
            }
            world.spawnParticle(Particle.WITCH, familiar.getLocation().clone().add(0.0, 0.8, 0.0), 20, 0.40, 0.35, 0.40, 0.06);
        }
        world.playSound(origin, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 0.95f, 1.15f);
        setBossCooldown(boss, keyBossPrimaryCooldown, 26_000L);
    }

    private int countNearbyBossMinions(LivingEntity boss, double radius) {
        BossRecord record = bossRecord(boss);
        if (record == null) {
            return 0;
        }
        int count = 0;
        for (Entity nearby : boss.getNearbyEntities(radius, radius, radius)) {
            if (record.entityUuid().equals(bossMinionOwner(nearby))) {
                count++;
            }
        }
        return count;
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
        if (target == null) {
            return;
        }
        int phase = bossPhase(entity);
        if (bossCooldownReady(entity, keyBossSecondaryCooldown) && startUndertow(entity, phase)) {
            return;
        }
        if (phase >= 2 && bossCooldownReady(entity, keyBossPrimaryCooldown)) {
            startTidalDivide(entity);
        }
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
        if (target == null) {
            return;
        }
        int phase = bossPhase(entity);
        if (bossCooldownReady(entity, keyBossSecondaryCooldown) && startSaintsStagger(entity, phase)) {
            return;
        }
        if (phase >= 2 && bossCooldownReady(entity, keyBossPrimaryCooldown)) {
            startIronCounterstance(entity);
        }
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
        if (target == null) {
            return;
        }
        int phase = bossPhase(entity);
        if (bossCooldownReady(entity, keyBossSecondaryCooldown) && startRootWards(entity, phase)) {
            return;
        }
        if (phase >= 2 && bossCooldownReady(entity, keyBossPrimaryCooldown)) {
            startBriarLattice(entity);
        }
    }

    private void tickCorruptedOathkeeper(LivingEntity entity) {
        ensureCorruptedOathkeeperSize(entity);

        double maxHealth = Math.max(1.0, entity.getAttribute(Attribute.MAX_HEALTH) == null ? 1.0 : entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        int phase = bossPhase(entity);
        if (phase < 3 && entity.getHealth() <= maxHealth * 0.33) {
            activateCorruptedOathkeeperPhase(entity, 3);
            phase = 3;
        } else if (phase < 2 && entity.getHealth() <= maxHealth * 0.66) {
            activateCorruptedOathkeeperPhase(entity, 2);
            phase = 2;
        }

        LivingEntity target = currentBossTarget(entity);
        if (target == null) {
            return;
        }

        double distanceSquared = entity.getLocation().distanceSquared(target.getLocation());
        if (bossCooldownReady(entity, keyBossPrimaryCooldown) && startOathRings(entity, phase)) {
            return;
        }

        double brandRange = scaledBossAbilityRadius(entity, phase >= 3 ? 24.0 : phase >= 2 ? 20.0 : 16.0);
        if (bossCooldownReady(entity, keyBossSecondaryCooldown) && distanceSquared <= brandRange * brandRange) {
            int brandPhase = phase;
            if (telegraphAreaAbility(
                entity,
                "Corrupted brand - spread from the center!",
                scaledBossAbilityRadius(entity, phase >= 3 ? 20.0 : phase >= 2 ? 17.0 : 14.0),
                28L,
                Color.fromRGB(185, 45, 220),
                () -> unleashCorruptedBrand(entity, brandPhase)
            )) {
                return;
            }
        }

        if (phase >= 2 && bossCooldownReady(entity, keyBossTertiaryCooldown)) {
            summonCorruptedOathkeeperMinions(entity, phase);
        }
    }

    private void ensureCorruptedOathkeeperSize(LivingEntity entity) {
        if (entity instanceof MagmaCube magmaCube && magmaCube.getSize() != 4) {
            magmaCube.setSize(4);
            entity.getPersistentDataContainer().set(keyBossScaledPlayerCount, PersistentDataType.INTEGER, -1);
        }
        setAttributeBase(entity, Attribute.SCALE, 4.0);
    }

    private void activateCorruptedOathkeeperPhase(LivingEntity entity, int phase) {
        setBossPhase(entity, phase);
        ensureCorruptedOathkeeperSize(entity);

        Location center = entity.getLocation().clone().add(0.0, Math.min(4.0, entity.getHeight() * 0.55), 0.0);
        World world = entity.getWorld();
        Color color = phase >= 3 ? Color.fromRGB(255, 54, 28) : Color.fromRGB(165, 35, 210);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticle(Particle.LAVA, center, phase >= 3 ? 70 : 46, 2.1, 1.2, 2.1, 0.12);
        world.spawnParticle(Particle.DUST, center, phase >= 3 ? 90 : 58, 2.4, 1.4, 2.4, 0.0, new Particle.DustOptions(color, 1.6f));
        world.playSound(entity.getLocation(), phase >= 3 ? Sound.ENTITY_WITHER_SPAWN : Sound.ENTITY_MAGMA_CUBE_SQUISH, 1.7f, phase >= 3 ? 0.62f : 0.55f);

        if (phase >= 3) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 2, false, true, true));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1, false, true, true));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, true, true));
        } else {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1, false, true, true));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, true, true));
        }
    }

    private void maybeBossDialogue(LivingEntity entity, BossType type) {
        if (entity == null || type == null || currentBossTarget(entity) == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long nextLine = entity.getPersistentDataContainer().getOrDefault(keyBossDialogueCooldown, PersistentDataType.LONG, 0L);
        if (nextLine > now) {
            return;
        }

        List<String> lines = BossDialogue.profile(type.id()).combatLines();
        if (!lines.isEmpty()) {
            sendBossLine(entity, type, lines.get(ThreadLocalRandom.current().nextInt(lines.size())));
        }
        scheduleNextBossDialogue(entity, 24_000L, 42_000L);
    }

    private void scheduleNextBossDialogue(LivingEntity entity, long minimumDelayMs, long maximumDelayMs) {
        if (entity == null) {
            return;
        }
        long minimum = Math.max(1_000L, minimumDelayMs);
        long maximum = Math.max(minimum, maximumDelayMs);
        long delay = minimum == maximum
            ? minimum
            : ThreadLocalRandom.current().nextLong(minimum, maximum + 1L);
        long now = System.currentTimeMillis();
        entity.getPersistentDataContainer().set(keyBossDialogueCooldown, PersistentDataType.LONG, now + delay);
    }

    private void sendBossLine(LivingEntity entity, BossType type, String line) {
        if (entity == null || type == null || entity.getWorld() == null || line == null || line.isBlank()) {
            return;
        }
        String message = type.displayName() + " <dark_gray>»</dark_gray> <gray>" + escapeMiniMessage(line) + "</gray>";
        Location center = entity.getLocation();
        double chatRadius = Math.max(48.0, Math.min(72.0, type.ritual().arenaRadius() + 36.0));
        for (Player player : entity.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(center) <= chatRadius * chatRadius) {
                player.sendMessage(MessageUtil.prefixedRaw(message));
            }
        }
    }

    private void unleashCorruptedBrand(LivingEntity entity, int phase) {
        double radius = scaledBossAbilityRadius(entity, phase >= 3 ? 20.0 : phase >= 2 ? 17.0 : 14.0);
        double damage = scaledBossAbilityDamage(entity, phase >= 3 ? 14.0 : phase >= 2 ? 10.0 : 7.0);
        Location eye = entity.getEyeLocation();
        World world = entity.getWorld();
        int hit = 0;

        for (Player player : world.getPlayers()) {
            if (!isEligibleBossVictim(entity, player) || player.getLocation().distanceSquared(entity.getLocation()) > radius * radius) {
                continue;
            }
            drawCorruptedBrandBeam(world, eye, player.getLocation().clone().add(0.0, 1.0, 0.0), phase);
            player.damage(damage, entity);
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, phase >= 3 ? 130 : 90, 0, false, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, phase >= 3 ? 110 : 70, 0, false, true, true));
            Vector pull = entity.getLocation().toVector().subtract(player.getLocation().toVector());
            if (pull.lengthSquared() > 1.0E-6) {
                player.setVelocity(player.getVelocity().add(pull.normalize().multiply(phase >= 3 ? 0.72 : 0.48).setY(0.22)));
            }
            hit++;
        }

        if (hit > 0) {
            world.playSound(entity.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.25f, phase >= 3 ? 0.42f : 0.55f);
            world.playSound(entity.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.68f);
            world.spawnParticle(Particle.REVERSE_PORTAL, entity.getLocation().clone().add(0.0, 2.4, 0.0), 80, 1.1, 0.75, 1.1, 0.15);
        }

        setBossCooldown(entity, keyBossSecondaryCooldown, phase >= 3 ? 8200L : phase >= 2 ? 9800L : 12_000L);
    }

    private void drawCorruptedBrandBeam(World world, Location from, Location to, int phase) {
        Vector direction = to.toVector().subtract(from.toVector());
        double length = direction.length();
        if (length <= 0.2) {
            return;
        }
        direction.normalize();
        Particle.DustOptions dust = new Particle.DustOptions(
            phase >= 3 ? Color.fromRGB(255, 60, 25) : Color.fromRGB(185, 45, 220),
            phase >= 3 ? 1.25f : 1.05f
        );
        for (double step = 0.0; step <= length; step += 0.75) {
            Location point = from.clone().add(direction.clone().multiply(step));
            world.spawnParticle(Particle.DUST, point, 1, 0.02, 0.02, 0.02, 0.0, dust);
            if (phase >= 3 && step % 1.5 < 0.75) {
                world.spawnParticle(Particle.FLAME, point, 1, 0.02, 0.02, 0.02, 0.01);
            }
        }
    }

    private void summonCorruptedOathkeeperMinions(LivingEntity bossEntity, int phase) {
        BossRecord record = bossRecord(bossEntity);
        if (record == null || bossEntity.getWorld() == null) {
            return;
        }

        World world = bossEntity.getWorld();
        Location origin = bossEntity.getLocation();
        LivingEntity currentTarget = currentBossTarget(bossEntity);
        int count = scaledBossMinionCount(bossEntity, phase >= 3 ? 3 : 2);
        double minionHealth = scaledBossSummonHealth(bossEntity, phase >= 3 ? 90.0 : 60.0);
        double minionDamage = scaledBossAbilityDamage(bossEntity, phase >= 3 ? 11.0 : 8.0);
        for (int i = 0; i < count; i++) {
            double angle = ((Math.PI * 2.0) / count) * i + ThreadLocalRandom.current().nextDouble(0.35);
            Location spawn = origin.clone().add(Math.cos(angle) * 4.0, 0.0, Math.sin(angle) * 4.0);
            spawn = findGroundedSpawn(world, spawn);
            MagmaCube minion = world.spawn(spawn, MagmaCube.class, magma -> {
                magma.setSize(phase >= 3 ? 3 : 2);
                magma.setPersistent(true);
                magma.setRemoveWhenFarAway(false);
                magma.setCanPickupItems(false);
                magma.customName(MM.deserialize("<dark_red>Corrupted Ember</dark_red>"));
                magma.setCustomNameVisible(false);
            });
            setAttributeBase(minion, Attribute.MAX_HEALTH, minionHealth);
            minion.setHealth(minionHealth);
            setAttributeBase(minion, Attribute.ATTACK_DAMAGE, minionDamage);
            setAttributeBase(minion, Attribute.MOVEMENT_SPEED, 0.34);
            setAttributeBase(minion, Attribute.FOLLOW_RANGE, 32.0);
            setAttributeBase(minion, Attribute.KNOCKBACK_RESISTANCE, 0.45);
            markBossMinion(minion, record.entityUuid());
            if (currentTarget != null && currentTarget.isValid() && !currentTarget.isDead()) {
                minion.setTarget(currentTarget);
            }
            world.spawnParticle(Particle.LAVA, minion.getLocation().clone().add(0.0, 0.8, 0.0), 18, 0.45, 0.35, 0.45, 0.08);
            world.spawnParticle(Particle.DUST, minion.getLocation().clone().add(0.0, 0.8, 0.0), 14, 0.32, 0.32, 0.32, 0.0, new Particle.DustOptions(Color.fromRGB(210, 40, 30), 1.0f));
        }
        world.playSound(origin, Sound.ENTITY_MAGMA_CUBE_SQUISH, 1.1f, 0.45f);
        setBossCooldown(bossEntity, keyBossTertiaryCooldown, phase >= 3 ? 22_000L : 30_000L);
    }

    private boolean isEligibleBossVictim(LivingEntity source, LivingEntity target) {
        if (source == null || target == null || source.equals(target) || target.isDead() || !target.isValid()) {
            return false;
        }
        if (target instanceof Player player
            && (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)) {
            return false;
        }
        return bossRecord(target) == null && !isBossMinion(target);
    }

    private void handleAurelionMeleeHit(LivingEntity attacker, LivingEntity target, int phase, EntityDamageByEntityEvent event) {
        event.setDamage(event.getDamage() + scaledBossAbilityDamage(attacker, phase >= 2 ? 2.0 : 1.0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, phase >= 2 ? 70 : 45, 0, false, true, true));
        target.getWorld().spawnParticle(Particle.PORTAL, target.getLocation().clone().add(0.0, 1.0, 0.0), 24, 0.35, 0.35, 0.35, 0.25);
    }

    private void handleMorvessaProjectileHit(LivingEntity attacker, LivingEntity target, int phase, EntityDamageByEntityEvent event) {
        double baseDamage = BossType.MORVESSA_THE_RUNEBLOOM_WITCH.attackDamage() * (phase >= 2 ? 0.72 : 0.58);
        event.setDamage(Math.max(event.getDamage(), scaledBossAbilityDamage(attacker, baseDamage)));
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, phase >= 2 ? 110 : 70, phase >= 2 ? 1 : 0, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, phase >= 2 ? 100 : 65, 0, false, true, true));
        target.getWorld().spawnParticle(Particle.WITCH, target.getLocation().clone().add(0.0, 1.0, 0.0), 28, 0.38, 0.45, 0.38, 0.08);
    }

    private void handleNereidaAttackHit(LivingEntity attacker, LivingEntity target, int phase, EntityDamageByEntityEvent event) {
        event.setDamage(event.getDamage() + scaledBossAbilityDamage(attacker, phase >= 2 ? 2.0 : 1.0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, phase >= 2 ? 80 : 50, 0, false, true, true));
        target.getWorld().spawnParticle(Particle.SPLASH, target.getLocation().clone().add(0.0, 1.0, 0.0), 18, 0.30, 0.35, 0.30, 0.05);
    }

    private void handleIronSaintMeleeHit(LivingEntity attacker, LivingEntity target, int phase, EntityDamageByEntityEvent event) {
        event.setDamage(event.getDamage() + scaledBossAbilityDamage(attacker, phase >= 2 ? 3.0 : 1.5));
        Vector away = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
        if (away.lengthSquared() > 1.0E-6) {
            target.setVelocity(target.getVelocity().add(away.normalize().multiply(phase >= 2 ? 0.75 : 0.45).setY(0.25)));
        }
        target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().clone().add(0.0, 1.0, 0.0), 18, 0.28, 0.34, 0.28, 0.06);
    }

    private void handleMirewoodMeleeHit(LivingEntity attacker, LivingEntity target, int phase, EntityDamageByEntityEvent event) {
        event.setDamage(event.getDamage() + scaledBossAbilityDamage(attacker, phase >= 2 ? 2.0 : 1.0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, phase >= 2 ? 80 : 45, 0, false, true, true));
        if (phase >= 2) {
            double maxHealth = attacker.getAttribute(Attribute.MAX_HEALTH) == null
                ? attacker.getHealth()
                : attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
            attacker.setHealth(Math.min(maxHealth, attacker.getHealth() + 2.0));
        }
        target.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, target.getLocation().clone().add(0.0, 1.0, 0.0), 18, 0.28, 0.34, 0.28, 0.04);
    }

    private void handleCorruptedOathkeeperMeleeHit(LivingEntity attacker, LivingEntity target, int phase, EntityDamageByEntityEvent event) {
        event.setDamage(event.getDamage() + scaledBossAbilityDamage(attacker, phase >= 3 ? 6.0 : phase >= 2 ? 4.0 : 2.5));
        target.setFireTicks(Math.max(target.getFireTicks(), phase >= 3 ? 140 : 90));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, phase >= 3 ? 100 : 60, 0, false, true, true));
        if (phase >= 3) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1, false, true, true));
        }
        target.getWorld().spawnParticle(Particle.LAVA, target.getLocation().clone().add(0.0, 1.0, 0.0), 12, 0.28, 0.30, 0.28, 0.05);
        target.getWorld().spawnParticle(Particle.DUST, target.getLocation().clone().add(0.0, 1.0, 0.0), 16, 0.25, 0.32, 0.25, 0.0, new Particle.DustOptions(Color.fromRGB(220, 45, 30), 1.05f));
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
                        if (isSafeBossSpawnLocation(candidate, type)) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isSafeBossSpawnLocation(Location location, BossType type) {
        int horizontalClearance = type == BossType.CORRUPTED_OATHKEEPER ? 4 : 0;
        return isSafeBossSpawnLocation(location, type.requiredAirBlocks(), horizontalClearance);
    }

    private boolean isSafeBossSpawnLocation(Location location, int requiredAirBlocks) {
        return isSafeBossSpawnLocation(location, requiredAirBlocks, 0);
    }

    private boolean isSafeBossSpawnLocation(Location location, int requiredAirBlocks, int horizontalClearance) {
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
        for (int dx = -horizontalClearance; dx <= horizontalClearance; dx++) {
            for (int dz = -horizontalClearance; dz <= horizontalClearance; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Block side = world.getBlockAt(location.getBlockX() + dx, blockY, location.getBlockZ() + dz);
                for (int i = 0; i < requiredAirBlocks; i++) {
                    if (!side.isPassable() || side.isLiquid()) {
                        return false;
                    }
                    side = side.getRelative(BlockFace.UP);
                }
            }
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
        bossArenaPlayers.remove(entityUuid);
        activeBossMechanics.remove(entityUuid);
        telegraphingBosses.remove(entityUuid);
        bossEntranceAnimations.remove(entityUuid);
        bossAttackVisualCooldowns.remove(entityUuid);
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

    private String formatPercent(double chance) {
        double percent = chance * 100.0;
        if (Math.rint(percent) == percent) {
            return Integer.toString((int) percent) + "%";
        }
        return String.format(Locale.US, "%.1f%%", percent);
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

    public record BossMusicContext(UUID bossEntityId, String bossId) {
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

    private record WispBossLootBonus(int activePets, double chance, boolean success) {
        private static WispBossLootBonus none() {
            return new WispBossLootBonus(0, 0.0D, false);
        }
    }

    private record DropPreview(Material icon, String name, String note, String relicId, int amount, double chance) {
    }

    private static final class BossFightState {
        private final long startedAt;
        private final Map<UUID, BossFightParticipant> participants = new LinkedHashMap<>();
        private long lossCheckAt;
        private boolean finished;
        private int peakScaledPlayers = 1;

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

        private double damageFor(UUID playerId) {
            BossFightParticipant participant = participants.get(playerId);
            return participant == null ? 0.0D : participant.damageDone();
        }

        private void addHealing(Player player, double amount) {
            participant(player).addHealing(amount);
            lossCheckAt = 0L;
        }

        private void addBlockedHealing(Player player, double amount) {
            participant(player).addBlockedHealing(amount);
        }

        private void addDamageTaken(Player player, double amount) {
            participant(player).addDamageTaken(amount);
        }

        private void addMechanicFailure(Player player) {
            participant(player).addMechanicFailure();
        }

        private void observeScaledPlayers(int playerCount) {
            peakScaledPlayers = Math.max(peakScaledPlayers, Math.max(1, playerCount));
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

        private Set<UUID> participantIds() {
            return new LinkedHashSet<>(participants.keySet());
        }
    }

    private static final class BossFightParticipant {
        private final UUID playerUuid;
        private String playerName;
        private double damageDone;
        private double healingReceived;
        private double blockedHealing;
        private double damageTaken;
        private int mechanicFailures;

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

        private void addBlockedHealing(double amount) {
            blockedHealing += Math.max(0.0, amount);
        }

        private void addDamageTaken(double amount) {
            damageTaken += Math.max(0.0, amount);
        }

        private void addMechanicFailure() {
            mechanicFailures++;
        }
    }

    private record RitualMatch(BossType type, Block focus) {
    }

    private record HealingSuppression(BossFightState state, ActiveBossMechanic mechanic) {
    }

    @FunctionalInterface
    public interface BossConfigurer {
        void apply(BossManager manager, LivingEntity entity);
    }

    // Add future entity/mechanic definitions here and their numeric progression profile in BossBalance.
    public enum BossType {
        YULE_THE_MINION(
            "yule_the_minion",
            EntityType.ZOMBIE,
            Material.ZOMBIE_HEAD,
            "<gradient:#d97706:#ef4444><bold>The Veilbound Marshal</bold></gradient>",
            0.36,
            40.0,
            0.35,
            2,
            false,
            List.of(
                "<gray>The opening Veil fight teaches the shared stack marker.</gray>",
                "<gray>Phase One:</gray> <white>stack on blue to split Hold the Line</white>",
                "<gray>Phase Two:</gray> <white>adds, Strength, and heavier knockback</white>",
                "<gray>Leaving the marked player alone makes the hit much harsher.</gray>"
            ),
            new BossRitual(
                "Veilbound Muster",
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
                14.0,
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
            "<gradient:#94a3b8:#e2e8f0><bold>Cindervale Arbalest</bold></gradient>",
            0.32,
            48.0,
            0.20,
            2,
            false,
            List.of(
                "<gray>A midrange Veil marksman that punishes space and line of sight.</gray>",
                "<gray>Phase One:</gray> <white>piercing arrows and baited Crossfire marks</white>",
                "<gray>Phase Two:</gray> <white>Deadeye tracks, locks, then executes anyone left in its red lane</white>",
                "<gray>Spread Crossfire marks, then dodge after Deadeye's aim line turns red.</gray>"
            ),
            new BossRitual(
                "Cindervale Wake",
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
                18.0,
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
            "<gradient:#22c55e:#84cc16><bold>The Gloam Matriarch</bold></gradient>",
            0.42,
            36.0,
            0.45,
            2,
            false,
            List.of(
                "<gray>A Gloam predator that dives through gaps and mauls stragglers.</gray>",
                "<gray>Phase One:</gray> <white>venom bites, leaps, and a moving poison trail</white>",
                "<gray>Phase Two:</gray> <white>Webbreak tethers marked prey to where they were caught</white>",
                "<gray>Keep Widow's Claim moving, then run six blocks to snap each tether.</gray>"
            ),
            new BossRitual(
                "Gloam Bloom",
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
                16.0,
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
            "<gradient:#991b1b:#ef4444><bold>Noctyr the Veil Warden</bold></gradient>",
            0.34,
            48.0,
            0.90,
            4,
            false,
            List.of(
                "<gray>The Season of the Veil apex fight, built around Warden pressure and violent shockwaves.</gray>",
                "<gray>Phase One:</gray> <white>dominion pulses and look-away Resonance Locks</white>",
                "<gray>Phase Two:</gray> <white>shorter lock windows, harder slams, and heavier punishment</white>",
                "<gray>Drops a <white>Veil Core</white> used to repair <white>Veil Dominion</white>.</gray>"
            ),
            new BossRitual(
                "Nocturne Gate",
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
                20.0,
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
        // Keep the original id for existing saves. Asterion is the only public identity.
        AURELION_THE_RIFT_SERAPH(
            "aurelion_the_rift_seraph",
            EntityType.ENDERMAN,
            Material.ENDER_EYE,
            "<gradient:#8b5cf6:#f0abfc><bold>Asterion the Rift Oracle</bold></gradient>",
            0.39,
            52.0,
            0.35,
            3,
            true,
            List.of(
                "<gray>A rift oracle that bends distance into a weapon.</gray>",
                "<gray>Phase One:</gray> <white>teleports and folds the arena into one safe wedge</white>",
                "<gray>Phase Two:</gray> <white>narrower Rift Sectors and heavier displacement</white>",
                "<gray>Drops Riftglass Lenses, rare Moonless Halos, Awakening Shards, and a very rare Awakening Table.</gray>"
            ),
            new BossRitual(
                "Rift Oracle Coronation",
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
                20.0,
                Color.fromRGB(170, 90, 255),
                Particle.PORTAL,
                Sound.BLOCK_END_PORTAL_FRAME_FILL,
                Sound.ENTITY_ENDERMAN_TELEPORT,
                Sound.BLOCK_END_PORTAL_SPAWN
            ),
            (manager, entity) -> { }
        ),
        MORVESSA_THE_RUNEBLOOM_WITCH(
            "morvessa_the_runebloom_witch",
            EntityType.WITCH,
            Material.BREWING_STAND,
            "<gradient:#65a30d:#a855f7><bold>Morvessa the Runebloom Witch</bold></gradient>",
            0.34,
            48.0,
            0.55,
            3,
            true,
            List.of(
                "<gray>A late-path hexweaver who makes the whole group tend her deadly garden.</gray>",
                "<gray>Phase One:</gray> <white>scaling Runebloom Sigils that heal her when missed</white>",
                "<gray>Phase Two:</gray> <white>rotating Petalstorms, stronger blooms, and familiars</white>",
                "<gray>Drops Riftglass Lenses and has a <white>5% chance</white> to drop a Runebloom Orb.</gray>"
            ),
            new BossRitual(
                "Runebloom Convergence",
                Material.BREWING_STAND,
                Material.DRAGON_BREATH,
                Material.BREWING_STAND,
                List.of(
                    "<gold><bold>Build Guide</bold></gold>",
                    "<gray>1. Put an <white>Amethyst Block</white> on the ground.</gray>",
                    "<gray>2. Place a <white>Brewing Stand</white> directly on top of it.</gray>",
                    "<gray>3. Put <white>Flowering Azalea Leaves</white> around the Amethyst Block on all four sides.</gray>",
                    "<gray>4. Hold <white>Dragon's Breath</white> and right-click the Brewing Stand or any shrine block.</gray>",
                    "<dark_gray>The Witch comes after the Rift Oracle and before the Veil Warden.</dark_gray>"
                ),
                80L,
                20.0,
                Color.fromRGB(120, 190, 45),
                Particle.WITCH,
                Sound.ENTITY_WITCH_CELEBRATE,
                Sound.BLOCK_BREWING_STAND_BREW,
                Sound.ENTITY_EVOKER_PREPARE_SUMMON
            ),
            (manager, entity) -> {
                if (entity instanceof Witch witch) {
                    witch.setCanPickupItems(false);
                }
                manager.equipBossHands(entity, new ItemStack(Material.BLAZE_ROD), null);
            }
        ),
        NEREIDA_THE_ABYSS_MOTHER(
            "nereida_the_abyss_mother",
            EntityType.DROWNED,
            Material.HEART_OF_THE_SEA,
            "<gradient:#38bdf8:#0f766e><bold>Thalassa the Drowned Veil</bold></gradient>",
            0.35,
            44.0,
            0.45,
            2,
            false,
            List.of(
                "<gray>A drowned Veil matron that turns rain and water into a battlefield.</gray>",
                "<gray>Phase One:</gray> <white>Undertow pulls followed by an escape check</white>",
                "<gray>Phase Two:</gray> <white>Tidal Divide floods one half while the green half stays safe</white>",
                "<gray>Drops Depthveil Pearls and rare Tideveil Hearts.</gray>"
            ),
            new BossRitual(
                "Drowned Veil Baptism",
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
                18.0,
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
            "<gradient:#d1d5db:#facc15><bold>The Argent Confessor</bold></gradient>",
            0.28,
            42.0,
            0.80,
            4,
            false,
            List.of(
                "<gray>A slow cathedral of argent iron, built to punish greedy spacing.</gray>",
                "<gray>Phase One:</gray> <white>break his guard or take the counter-slam</white>",
                "<gray>Phase Two:</gray> <white>Counterstance reflects greedy hits; stop attacking to expose him</white>",
                "<gray>Drops Argent Gears and rare Confessor Alloy.</gray>"
            ),
            new BossRitual(
                "Argent Litany",
                Material.ANVIL,
                Material.IRON_BLOCK,
                Material.ANVIL,
                List.of(
                    "<gold><bold>Build Guide</bold></gold>",
                    "<gray>1. Put a <white>Smithing Table</white> on the ground.</gray>",
                    "<gray>2. Place an <white>Anvil</white> directly on top of the Smithing Table.</gray>",
                    "<gray>3. Put <white>Iron Blocks</white> touching the Smithing Table north, south, east, and west.</gray>",
                    "<gray>4. Hold an <white>Iron Block</white> and right-click the Anvil or any shrine block.</gray>",
                    "<dark_gray>Bring real armor. The Confessor does not negotiate.</dark_gray>"
                ),
                76L,
                18.0,
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
            "<gradient:#16a34a:#854d0e><bold>The Briarveil Regent</bold></gradient>",
            0.33,
            40.0,
            0.55,
            2,
            false,
            List.of(
                "<gray>A briar-crowned tyrant wearing a corpse as a throne.</gray>",
                "<gray>Phase One:</gray> <white>fill every Root Ward before the roots close</white>",
                "<gray>Phase Two:</gray> <white>Briar Lattice roots the red crossing lanes and leaves the corners safe</white>",
                "<gray>Drops Briarwake Bark and rare Briarhearts.</gray>"
            ),
            new BossRitual(
                "Briarveil Wake",
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
                16.0,
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
        ),
        CORRUPTED_OATHKEEPER(
            "corrupted_oathkeeper",
            EntityType.MAGMA_CUBE,
            Material.MAGMA_CREAM,
            "<gradient:#7f1d1d:#f97316><bold>Corrupted Oathkeeper</bold></gradient>",
            0.36,
            56.0,
            1.0,
            10,
            false,
            List.of(
                "<gray>The strongest Veil boss yet: a huge corrupted magma oath made flesh.</gray>",
                "<gray>Phase One:</gray> <white>heavy melee and readable IN or OUT Oath Rings</white>",
                "<gray>Phase Two:</gray> <white>brand pulls, tighter rings, and corrupted embers</white>",
                "<gray>Phase Three:</gray> <white>back-to-back reversed rings overlap with moving spread brands</white>",
                "<gray>Drops <white>Corrupted Essence</white> for future item corruption.</gray>"
            ),
            new BossRitual(
                "Oathkeeper Corruption",
                Material.RESPAWN_ANCHOR,
                Material.NETHER_STAR,
                Material.RESPAWN_ANCHOR,
                List.of(
                    "<gold><bold>Build Guide</bold></gold>",
                    "<gray>1. Put <white>Crying Obsidian</white> on the ground.</gray>",
                    "<gray>2. Place a <white>Respawn Anchor</white> directly on top of it.</gray>",
                    "<gray>3. Put <white>Magma Blocks</white> touching the Crying Obsidian north, south, east, and west.</gray>",
                    "<gray>4. Put <white>Sculk Catalysts</white> on all four diagonal corners from the Crying Obsidian.</gray>",
                    "<gray>5. Hold a <white>Nether Star</white> and right-click the Respawn Anchor or any shrine block.</gray>",
                    "<dark_gray>Base layer: Catalyst / Magma / Catalyst</dark_gray>",
                    "<dark_gray>            Magma / Crying Obsidian / Magma</dark_gray>",
                    "<dark_gray>            Catalyst / Magma / Catalyst</dark_gray>"
                ),
                96L,
                22.0,
                Color.fromRGB(225, 48, 26),
                Particle.LAVA,
                Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE,
                Sound.ENTITY_MAGMA_CUBE_SQUISH,
                Sound.ENTITY_WITHER_SPAWN
            ),
            (manager, entity) -> {
                BossBalance.Profile balance = BossBalance.profile("corrupted_oathkeeper");
                if (entity instanceof MagmaCube magmaCube) {
                    magmaCube.setSize(4);
                }
                manager.setAttributeBase(entity, Attribute.SCALE, 4.0);
                manager.setAttributeBase(entity, Attribute.MAX_HEALTH, balance.maxHealth());
                manager.setAttributeBase(entity, Attribute.ATTACK_DAMAGE, balance.attackDamage());
                manager.setAttributeBase(entity, Attribute.MOVEMENT_SPEED, 0.36);
                manager.setAttributeBase(entity, Attribute.FOLLOW_RANGE, 56.0);
                manager.setAttributeBase(entity, Attribute.KNOCKBACK_RESISTANCE, 1.0);
                AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
                entity.setHealth(Math.max(1.0, health == null ? balance.maxHealth() : health.getValue()));
                entity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false, true));
            }
        );

        private static final Map<String, BossType> BY_ID = new HashMap<>();
        private static final Map<String, BossType> BY_INPUT = new HashMap<>();
        private static final List<BossType> PROGRESSION_ORDER;
        static {
            for (BossType type : values()) {
                BY_ID.put(type.id, type);
                BY_INPUT.put(type.id, type);
                BY_INPUT.put(type.commandToken(), type);
            }
            registerInputAliases(AURELION_THE_RIFT_SERAPH,
                "asterion", "asterion_the_rift_oracle", "rift_oracle", "aurelion");
            List<BossType> ordered = new ArrayList<>(List.of(values()));
            ordered.sort(Comparator.comparingInt(BossType::progressionTier));
            PROGRESSION_ORDER = List.copyOf(ordered);
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
            BossBalance.Profile balance = BossBalance.profile(id);
            this.maxHealth = balance.maxHealth();
            this.attackDamage = balance.attackDamage();
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
            if (input == null || input.isBlank()) return null;
            String normalized = BossIdentity.normalizeInput(input);
            BossType direct = BY_INPUT.get(normalized);
            return direct == null ? BY_ID.get(BossIdentity.canonicalId(normalized)) : direct;
        }

        private static void registerInputAliases(BossType type, String... aliases) {
            for (String alias : aliases) BY_INPUT.put(alias, type);
        }

        public static List<BossType> progressionOrder() {
            return PROGRESSION_ORDER;
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

        public int maxPhases() {
            return this == CORRUPTED_OATHKEEPER ? 3 : 2;
        }

        public int progressionTier() {
            return BossBalance.profile(id).tier();
        }

        public String recommendedGear() {
            return BossBalance.profile(id).recommendedGear();
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

    private enum BossMechanicKind {
        MARSHAL_STACK,
        ASHEN_CROSSFIRE,
        ASHEN_DEADEYE,
        WIDOWS_TRAIL,
        WIDOWS_WEBBREAK,
        ROOT_WARDS,
        BRIAR_LATTICE,
        UNDERTOW,
        TIDAL_DIVIDE,
        SAINTS_STAGGER,
        IRON_COUNTERSTANCE,
        RIFT_SECTORS,
        RUNEBLOOM_SIGILS,
        PETALSTORM,
        RESONANCE_LOCK,
        OATH_RINGS
    }

    private record MechanicNotice(String title, String instruction, String subtitle) {
    }

    private record BossAttackVisual(Particle accent, Sound sound, float pitch) {
    }

    private static final class ActiveBossMechanic {
        private final BossMechanicKind kind;
        private final int phase;
        private final long startedAt;
        private long warningEndsAt;
        private final long expiresAt;
        private final Location origin;
        private final List<Location> points = new ArrayList<>();
        private final List<UUID> targets = new ArrayList<>();
        private final Set<UUID> participants = new HashSet<>();
        private final Set<UUID> failedTargets = new HashSet<>();
        private final Map<UUID, Long> hitCooldowns = new HashMap<>();
        private final Map<UUID, Double> healingCaps = new HashMap<>();
        private final Map<UUID, Long> healingWarningCooldowns = new HashMap<>();
        private long nextStepAt;
        private int stage;
        private double angle;
        private double radius;
        private double progress;
        private double threshold;
        private boolean insideSafe;

        private ActiveBossMechanic(
            BossMechanicKind kind,
            int phase,
            long startedAt,
            long warningEndsAt,
            long expiresAt,
            Location origin
        ) {
            this.kind = kind;
            this.phase = phase;
            this.startedAt = startedAt;
            this.warningEndsAt = warningEndsAt;
            this.expiresAt = expiresAt;
            this.origin = origin.clone();
        }
    }

    private record BossMenuHolder(boolean despawnMode) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record BossRitualMenuHolder() implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record BossDropPreviewMenuHolder(BossType type) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
