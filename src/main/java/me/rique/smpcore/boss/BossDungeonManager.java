package me.rique.smpcore.boss;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.npc.GuideNpcManager.GuideNpcType;
import me.rique.smpcore.util.AtomicYamlFile;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.InventoryRecipeUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class BossDungeonManager implements Listener {

    public static final String WORLD_NAME = "boss_dungeon";
    private static final String WORLD_RESOURCE = "dungeon/gothic-boss-room-world.zip";
    private static final String WORLD_ARCHIVE_ROOT = "Gothic Boss Room World/";
    private static final String MANAGED_WORLD_MARKER = ".smpcore-managed-dungeon";
    private static final int[] BOSS_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};
    private static final int EXIT_SLOT = 49;
    private static final int CONFIRM_SLOT = 15;
    private static final int CANCEL_SLOT = 11;
    static final int BOSS_SPAWN_COUNTDOWN_SECONDS = 10;
    private static final long LOOT_WINDOW_TICKS = 120L * 20L;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final SMPCore plugin;
    private final NamespacedKey droppedItemOwnerKey;
    private final Map<UUID, Long> nextMenuOpenAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextMenuActionAt = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> dungeonDeaths = ConcurrentHashMap.newKeySet();
    private final Map<UUID, GameMode> restoredGameModes = new ConcurrentHashMap<>();
    private final Set<UUID> managedTeleports = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> pendingEssenceRefunds = new ConcurrentHashMap<>();
    private final ArrayDeque<QueuedSummon> summonQueue = new ArrayDeque<>();
    private final File locationFile;
    private final YamlConfiguration locationConfig;
    private World world;
    private boolean summonPending;
    private DungeonEncounter encounter;
    private BukkitTask queueTask;
    private BukkitTask countdownTask;
    private BukkitTask lootTask;
    private DungeonLocations locations;

    public BossDungeonManager(SMPCore plugin) {
        this.plugin = plugin;
        this.droppedItemOwnerKey = new NamespacedKey(plugin, "dungeon_drop_owner");
        this.locationFile = new File(plugin.getDataFolder(), "boss-dungeon.yml");
        this.locationConfig = YamlConfiguration.loadConfiguration(locationFile);
        loadPendingEssenceRefunds();
    }

    public void start() {
        world = provisionAndLoadWorld();
        if (world == null) {
            plugin.getLogger().severe("Boss dungeon is unavailable. Boss summoning remains disabled.");
            return;
        }
        locations = loadLocations();
        configureWorld(world);
        keepArenaChunksLoaded();
        // Citizens restores its saved registry during delayed server init. Waiting avoids
        // creating a second keeper before the saved NPC becomes visible to SMPCore.
        Bukkit.getScheduler().runTaskLater(plugin, this::ensureArenaKeeper, 40L);
        Bukkit.getScheduler().runTaskLater(plugin, this::resetOrphanedArena, 60L);
        queueTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processQueue, 100L, 100L);
    }

    public void shutdown() {
        summonPending = false;
        if (queueTask != null) queueTask.cancel();
        cancelCountdownTask();
        if (lootTask != null) lootTask.cancel();
        if (encounter != null && !encounter.looting && encounter.paid) {
            refundEncounterEntry(encounter, "server_shutdown");
        }
        if (world != null && plugin.getBossManager() != null) plugin.getBossManager().despawnBossesInWorld(world);
        finishEncounter(false, false);
        cleanupCombatEntities(true);
        if (world != null) world.removePluginChunkTickets(plugin);
        nextMenuOpenAt.clear();
        nextMenuActionAt.clear();
        dungeonDeaths.clear();
        restoredGameModes.clear();
        summonQueue.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof DungeonMenuHolder) {
                player.closeInventory();
            }
        }
    }

    public boolean isDungeonWorld(World candidate) {
        return candidate != null && WORLD_NAME.equals(candidate.getName());
    }

    public void openFromKeeper(Player player) {
        if (player == null || !player.hasPermission("smpcore.dungeon.use")) {
            if (player != null) {
                player.sendMessage(MessageUtil.warn("You cannot use the boss dungeon."));
            }
            return;
        }
        long now = System.currentTimeMillis();
        if (nextMenuOpenAt.getOrDefault(player.getUniqueId(), 0L) > now) {
            return;
        }
        nextMenuOpenAt.put(player.getUniqueId(), now + 400L);
        if (isDungeonWorld(player.getWorld())) {
            openBossCatalog(player);
        } else {
            openEntranceMenu(player);
        }
    }

    public void openBossCatalog(Player player) {
        if (player == null) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(
            new DungeonMenuHolder(player.getUniqueId(), DungeonView.CATALOG, null),
            54,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#7f1d1d:#a78bfa><bold>Boss Dungeon</bold></gradient>"), "Boss Dungeon")
        );
        fill(inventory);
        inventory.setItem(4, item(Material.RESPAWN_ANCHOR, "<gradient:#7f1d1d:#a78bfa><bold>Dungeon Summoning</bold></gradient>", List.of(
            encounter == null ? "<green>The arena is ready.</green>" : "<yellow>A fight is active. New summons enter the queue.</yellow>",
            "<gray>Queue: <white>" + summonQueue.size() + "</white> | Teammates and allies may join.</gray>"
        )));
        List<BossManager.BossType> bosses = BossManager.BossType.progressionOrder();
        for (int i = 0; i < bosses.size() && i < BOSS_SLOTS.length; i++) {
            inventory.setItem(BOSS_SLOTS[i], bossItem(player, bosses.get(i)));
        }
        boolean inside = isDungeonWorld(player.getWorld());
        inventory.setItem(EXIT_SLOT, inside
            ? item(Material.IRON_DOOR, "<yellow><bold>Leave Dungeon</bold></yellow>", List.of("<gray>Return to server spawn.</gray>"))
            : item(Material.ARROW, "<yellow><bold>Back</bold></yellow>", List.of("<gray>Return to the dungeon entrance.</gray>")));
        player.openInventory(inventory);
    }

    private void openEntranceMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(
            new DungeonMenuHolder(player.getUniqueId(), DungeonView.ENTRANCE, null),
            27,
            BedrockCompat.menuTitle(player, MM.deserialize("<dark_red><bold>The Boss Dungeon</bold></dark_red>"), "Boss Dungeon")
        );
        fill(inventory);
        inventory.setItem(11, item(Material.ENDER_PEARL, encounter == null ? "<green><bold>Enter Dungeon</bold></green>" : "<aqua><bold>Spectate Fight</bold></aqua>", List.of(
            encounter == null ? "<gray>Travel to the protected boss arena.</gray>" : "<gray>Watch the current team without affecting combat.</gray>",
            "<dark_gray>Bosses cannot be summoned elsewhere.</dark_gray>"
        )));
        inventory.setItem(15, item(Material.WRITABLE_BOOK, "<gold><bold>Summon Costs</bold></gold>", List.of("<gray>Preview every boss, Essence fee, and material cost.</gray>")));
        player.openInventory(inventory);
    }

    private void openConfirmation(Player player, BossManager.BossType type) {
        Inventory inventory = Bukkit.createInventory(
            new DungeonMenuHolder(player.getUniqueId(), DungeonView.CONFIRM, type),
            27,
            BedrockCompat.menuTitle(player, MM.deserialize("<red><bold>Confirm Summon</bold></red>"), "Confirm Summon")
        );
        fill(inventory);
        inventory.setItem(CANCEL_SLOT, item(Material.RED_STAINED_GLASS_PANE, "<red><bold>Cancel</bold></red>", List.of("<gray>Keep your Essence and materials.</gray>")));
        inventory.setItem(13, bossItem(player, type));
        inventory.setItem(CONFIRM_SLOT, item(Material.LIME_STAINED_GLASS_PANE, "<green><bold>Summon Boss</bold></green>", List.of(
            encounter == null ? "<gray>Pay Malakar and begin the fight.</gray>" : "<gray>Join the queue. Payment waits for your turn.</gray>",
            "<gray>The boss forms after a <white>10-second</white> preparation countdown.</gray>",
            "<gray>The summoner pays; teammates join free.</gray>",
            "<dark_gray>Only ordinary materials count.</dark_gray>"
        )));
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof DungeonMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.playerId().equals(player.getUniqueId())) {
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize() || !MenuItemUtil.isVisibleItem(event.getCurrentItem())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (nextMenuActionAt.getOrDefault(player.getUniqueId(), 0L) > now) {
            return;
        }
        nextMenuActionAt.put(player.getUniqueId(), now + 500L);
        Bukkit.getScheduler().runTask(plugin, () -> handleClick(player, holder, slot));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof DungeonMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void handleClick(Player player, DungeonMenuHolder holder, int slot) {
        if (!player.isOnline()) {
            return;
        }
        switch (holder.view()) {
            case ENTRANCE -> {
                if (slot == 11) {
                    enterDungeon(player);
                } else if (slot == 15) {
                    openBossCatalog(player);
                }
            }
            case CATALOG -> {
                if (slot == EXIT_SLOT) {
                    if (isDungeonWorld(player.getWorld())) {
                        leaveDungeon(player);
                    } else {
                        openEntranceMenu(player);
                    }
                    return;
                }
                BossManager.BossType type = bossForSlot(slot);
                if (type == null) {
                    return;
                }
                if (!isDungeonWorld(player.getWorld())) {
                    player.sendMessage(MessageUtil.info("Enter the dungeon before summoning a boss."));
                    return;
                }
                openConfirmation(player, type);
            }
            case CONFIRM -> {
                if (slot == CANCEL_SLOT) {
                    openBossCatalog(player);
                } else if (slot == CONFIRM_SLOT && holder.type() != null) {
                    summon(player, holder.type());
                }
            }
        }
    }

    private void summon(Player player, BossManager.BossType type) {
        if (!isDungeonWorld(player.getWorld())) {
            player.closeInventory();
            player.sendMessage(MessageUtil.error("Bosses can only be summoned inside the dungeon."));
            return;
        }
        if (isQueued(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("You already have a boss summon in the queue."));
            return;
        }
        Map<Material, Integer> costs = summonCosts(type);
        if (!InventoryRecipeUtil.hasPlainMaterials(plugin, player, costs)) {
            player.sendMessage(MessageUtil.warn("You do not have every required summon material."));
            openConfirmation(player, type);
            return;
        }
        long essenceCost = summonEssenceCost(type);
        if (!hasEssence(player, essenceCost)) {
            player.sendMessage(MessageUtil.warn("You need <white>" + essenceCost + " Essence</white> to summon this boss."));
            openConfirmation(player, type);
            return;
        }
        player.closeInventory();
        if (encounter != null || summonPending || plugin.getBossManager() == null || plugin.getBossManager().hasActiveBossInWorld(world)) {
            summonQueue.addLast(new QueuedSummon(player.getUniqueId(), type, System.currentTimeMillis()));
            player.sendMessage(MessageUtil.success("Queued <white>" + type.plainDisplayName() + "</white> at position <white>" + summonQueue.size() + "</white>. Essence and materials stay with you until your turn."));
            return;
        }
        startEncounter(player, type);
    }

    private boolean startEncounter(Player owner, BossManager.BossType type) {
        return startEncounter(owner, type, false);
    }

    private boolean startEncounter(Player owner, BossManager.BossType type, boolean testMode) {
        if (owner == null || !owner.isOnline() || encounter != null || summonPending) {
            return false;
        }
        Map<Material, Integer> costs = testMode ? Map.of() : summonCosts(type);
        long essenceCost = testMode ? 0L : summonEssenceCost(type);
        if (!testMode && !InventoryRecipeUtil.hasPlainMaterials(plugin, owner, costs)) {
            owner.sendMessage(MessageUtil.warn("Your queue turn arrived, but you no longer have the summon materials."));
            return false;
        }
        if (!testMode && !hasEssence(owner, essenceCost)) {
            owner.sendMessage(MessageUtil.warn("Your queue turn arrived, but you no longer have <white>" + essenceCost + " Essence</white>."));
            return false;
        }
        boolean essenceConsumed = testMode || plugin.getEssenceManager().spend(owner, essenceCost, "boss_entry_" + type.id());
        if (!essenceConsumed) {
            owner.sendMessage(MessageUtil.error("Your Essence balance changed before Malakar could take payment."));
            return false;
        }
        boolean materialsConsumed = testMode || InventoryRecipeUtil.removePlainMaterials(plugin, owner, costs);
        if (!materialsConsumed) {
            if (essenceConsumed && !testMode) {
                refundEssence(owner, essenceCost, "materials_changed");
            }
            owner.sendMessage(MessageUtil.error("Your inventory changed before Malakar could take the materials."));
            return false;
        }
        summonPending = true;
        encounter = new DungeonEncounter(owner.getUniqueId(), type, costs, essenceCost, !testMode && materialsConsumed && essenceConsumed, testMode);
        encounter.participants.add(owner.getUniqueId());
        List<Player> autoJoined = presentTeamMembers(owner);
        for (Player teammate : autoJoined) {
            encounter.participants.add(teammate.getUniqueId());
        }
        for (Player visitor : new ArrayList<>(world.getPlayers())) {
            if (!encounter.participants.contains(visitor.getUniqueId())) makeSpectator(visitor);
        }
        prepareFighter(owner);
        for (Player teammate : autoJoined) {
            prepareFighter(teammate);
            teammate.sendMessage(MessageUtil.success("Your teammate is summoning <white>" + encounter.type.plainDisplayName() + "</white>. You joined the preparation automatically."));
            teammate.playSound(teammate.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.15f);
        }
        inviteTeamAndAllies(owner);
        owner.sendMessage(MessageUtil.success(
            "Payment accepted. <white>" + type.plainDisplayName() + "</white> forms in <white>10 seconds</white>. Prepare now."
        ));
        startBossCountdown(encounter);
        return true;
    }

    private void startBossCountdown(DungeonEncounter pending) {
        cancelCountdownTask();
        pending.countdownEndsAtMillis = System.currentTimeMillis() + BOSS_SPAWN_COUNTDOWN_SECONDS * 1000L;
        pending.lastCountdownSecond = -1;
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickBossCountdown(pending), 0L, 20L);
    }

    private void tickBossCountdown(DungeonEncounter pending) {
        if (encounter != pending || pending.bossSpawned || pending.looting) {
            cancelCountdownTask();
            return;
        }
        int remaining = countdownSecondsRemaining(pending.countdownEndsAtMillis, System.currentTimeMillis());
        if (remaining <= 0) {
            spawnCountdownBoss(pending);
            return;
        }
        if (pending.lastCountdownSecond == remaining) {
            return;
        }
        pending.lastCountdownSecond = remaining;
        announceBossCountdown(pending, remaining);
    }

    private void announceBossCountdown(DungeonEncounter pending, int remaining) {
        float pitch = Math.min(1.9f, 0.75f + (BOSS_SPAWN_COUNTDOWN_SECONDS - remaining) * 0.1f);
        Title title = Title.title(
            MM.deserialize("<gradient:#ef4444:#f59e0b><bold>" + remaining + "</bold></gradient>"),
            MM.deserialize("<gray>" + pending.type.plainDisplayName() + " is forming</gray>"),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(850L), Duration.ofMillis(100L))
        );
        for (UUID participantId : List.copyOf(pending.participants)) {
            Player participant = Bukkit.getPlayer(participantId);
            if (participant == null || !participant.isOnline() || pending.eliminated.contains(participantId)) {
                continue;
            }
            participant.showTitle(title);
            participant.sendActionBar(MM.deserialize(
                "<red><bold>BOSS IN " + remaining + "</bold></red> <dark_gray>•</dark_gray> <white>Prepare now</white>"
            ));
            participant.playSound(participant.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, pitch);
        }
        Location center = bossSpawn().clone().add(0.0D, 1.0D, 0.0D);
        world.spawnParticle(pending.type.ritual().primaryParticle(), center, 18, 0.8D, 0.5D, 0.8D, 0.02D);
    }

    private void spawnCountdownBoss(DungeonEncounter pending) {
        cancelCountdownTask();
        if (encounter != pending || pending.bossSpawned) {
            return;
        }
        pending.invitesOpen = false;
        Player summoner = activeParticipant(pending);
        if (summoner == null) {
            refundEncounterEntry(pending, "countdown_abandoned");
            notifyParticipants(pending, MessageUtil.warn("The boss summon was cancelled because no fighters remained."));
            finishEncounter(false, true);
            return;
        }

        Location center = bossSpawn();
        world.playSound(center, pending.type.ritual().startSound(), 1.25f, 0.75f);
        world.spawnParticle(pending.type.ritual().primaryParticle(), center.clone().add(0, 1, 0), 60, 1.2, 0.8, 1.2, 0.05);
        BossManager.BossActionResult result;
        try {
            result = plugin.getBossManager().spawnDungeonBoss(pending.type, center, summoner);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Dungeon boss summon failed after countdown: " + ex.getMessage());
            result = new BossManager.BossActionResult(false, "The boss failed to form.");
        } finally {
            summonPending = false;
        }
        if (!result.success()) {
            refundEncounterEntry(pending, "summon_failed");
            notifyParticipants(pending, MessageUtil.error(result.message()));
            finishEncounter(false, true);
            return;
        }
        pending.bossSpawned = true;
        notifyParticipants(pending, MessageUtil.success(result.message()));
    }

    private Player activeParticipant(DungeonEncounter pending) {
        for (UUID participantId : pending.participants) {
            Player participant = Bukkit.getPlayer(participantId);
            if (participant != null && participant.isOnline() && !participant.isDead()
                && !pending.eliminated.contains(participantId) && participant.getGameMode() != GameMode.SPECTATOR) {
                return participant;
            }
        }
        return null;
    }

    private void notifyParticipants(DungeonEncounter target, Component message) {
        for (UUID participantId : target.participants) {
            Player participant = Bukkit.getPlayer(participantId);
            if (participant != null && participant.isOnline()) {
                participant.sendMessage(message);
            }
        }
    }

    private void cancelCountdownTask() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    static int countdownSecondsRemaining(long endsAtMillis, long nowMillis) {
        if (endsAtMillis <= nowMillis) {
            return 0;
        }
        long remainingMillis = endsAtMillis - nowMillis;
        return (int) Math.min(Integer.MAX_VALUE, 1L + (remainingMillis - 1L) / 1000L);
    }

    public boolean adminStartTest(Player admin, BossManager.BossType type) {
        if (admin == null || type == null || world == null || plugin.getBossManager() == null) return false;
        if (encounter != null || summonPending || plugin.getBossManager().hasActiveBossInWorld(world)) {
            admin.sendMessage(MessageUtil.warn("The arena is occupied. Use <white>/bossdungeon reset</white> first."));
            return false;
        }
        summonQueue.removeIf(entry -> entry.ownerId().equals(admin.getUniqueId()));
        boolean started = startEncounter(admin, type, true);
        if (started) {
            plugin.getLogger().info(admin.getName() + " started a no-loot admin test of " + type.id() + ".");
            admin.sendMessage(MessageUtil.info("Test mode uses no materials and creates no collectible boss loot."));
        }
        return started;
    }

    public boolean adminJoinFight(Player admin) {
        if (admin == null || encounter == null || encounter.looting) return false;
        encounter.eliminated.remove(admin.getUniqueId());
        encounter.participants.add(admin.getUniqueId());
        prepareFighter(admin);
        admin.sendMessage(MessageUtil.success("Joined the active fight as a participant."));
        return true;
    }

    public boolean adminSpectate(Player admin) {
        if (admin == null || world == null) return false;
        if (encounter != null) encounter.participants.remove(admin.getUniqueId());
        makeSpectator(admin);
        admin.sendMessage(MessageUtil.info("Moved to the arena spectator point."));
        return true;
    }

    public boolean adminTeleport(Player admin, String point) {
        if (admin == null || world == null) return false;
        Location destination = switch (point == null ? "" : point.toLowerCase(java.util.Locale.ROOT)) {
            case "entry" -> entry();
            case "fight" -> fightSpawn();
            case "spectator" -> spectatorSpawn();
            case "boss" -> bossSpawn();
            case "keeper" -> keeperSpawn();
            default -> null;
        };
        if (destination == null) return false;
        managedTeleport(admin, destination);
        return true;
    }

    public int adminReset(Player admin) {
        summonQueue.clear();
        int removed = plugin.getBossManager() == null || world == null ? 0 : plugin.getBossManager().despawnBossesInWorld(world);
        if (encounter != null) {
            refundEncounterEntry(encounter, "admin_reset");
            finishEncounter(false, false);
        }
        else cleanupCombatEntities(true);
        clearStaleLootChests();
        plugin.getLogger().info((admin == null ? "Console" : admin.getName()) + " reset the Boss Dungeon test arena; removed " + removed + " boss(es).");
        return removed;
    }

    private void refund(Player player, Map<Material, Integer> costs) {
        for (Map.Entry<Material, Integer> entry : costs.entrySet()) {
            InventoryRecipeUtil.giveOrDrop(player, new ItemStack(entry.getKey(), entry.getValue()));
        }
    }

    private void refund(UUID ownerId, Map<Material, Integer> costs) {
        for (Map.Entry<Material, Integer> entry : costs.entrySet()) {
            deliverOrStore(ownerId, new ItemStack(entry.getKey(), entry.getValue()));
        }
    }

    private boolean hasEssence(Player player, long amount) {
        return amount <= 0L || plugin.getEssenceManager() != null && plugin.getEssenceManager().balance(player) >= amount;
    }

    private void refundEssence(Player player, long amount, String reason) {
        if (player == null || amount <= 0L) {
            return;
        }
        long refunded = plugin.getEssenceManager() == null
            ? 0L
            : plugin.getEssenceManager().refund(player, amount, "boss_entry_refund_" + reason);
        long remaining = Math.max(0L, amount - refunded);
        if (remaining > 0L) {
            queuePendingEssenceRefund(player.getUniqueId(), remaining);
        }
        if (refunded > 0L) {
            player.sendMessage(MessageUtil.info("Malakar returned <white>" + refunded + " Essence</white> because the boss entry was cancelled."));
        }
    }

    private void refundEssence(UUID ownerId, long amount, String reason) {
        if (ownerId == null || amount <= 0L) {
            return;
        }
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && owner.isOnline()) {
            refundEssence(owner, amount, reason);
            return;
        }
        queuePendingEssenceRefund(ownerId, amount);
    }

    private void refundEncounterEntry(DungeonEncounter target, String reason) {
        if (target == null || !target.paid || target.entryRefunded) {
            return;
        }
        target.entryRefunded = true;
        refund(target.ownerId, target.costs);
        refundEssence(target.ownerId, target.essenceCost, reason);
    }

    private void loadPendingEssenceRefunds() {
        org.bukkit.configuration.ConfigurationSection section = locationConfig.getConfigurationSection("pending-essence-refunds");
        if (section == null) {
            return;
        }
        for (String rawId : section.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(rawId);
                long amount = Math.max(0L, section.getLong(rawId));
                if (amount > 0L) {
                    pendingEssenceRefunds.put(playerId, amount);
                }
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignored invalid pending dungeon Essence refund id: " + rawId);
            }
        }
    }

    private void queuePendingEssenceRefund(UUID playerId, long amount) {
        if (playerId == null || amount <= 0L) {
            return;
        }
        pendingEssenceRefunds.merge(playerId, amount, Long::sum);
        savePendingEssenceRefunds();
    }

    private void deliverPendingEssenceRefund(Player player, int attemptsRemaining) {
        if (player == null || !player.isOnline() || plugin.getEssenceManager() == null) {
            return;
        }
        long pending = pendingEssenceRefunds.getOrDefault(player.getUniqueId(), 0L);
        if (pending <= 0L) {
            return;
        }
        if (!plugin.getEssenceManager().isLoaded(player)) {
            if (attemptsRemaining > 0) {
                Bukkit.getScheduler().runTaskLater(plugin,
                    () -> deliverPendingEssenceRefund(player, attemptsRemaining - 1), 20L);
            }
            return;
        }
        long refunded = plugin.getEssenceManager().refund(player, pending, "boss_entry_refund_delayed");
        if (refunded <= 0L) {
            return;
        }
        long remaining = Math.max(0L, pending - refunded);
        if (remaining == 0L) {
            pendingEssenceRefunds.remove(player.getUniqueId());
        } else {
            pendingEssenceRefunds.put(player.getUniqueId(), remaining);
        }
        savePendingEssenceRefunds();
        player.sendMessage(MessageUtil.info("Malakar returned <white>" + refunded + " Essence</white> from an interrupted boss entry."));
    }

    private void savePendingEssenceRefunds() {
        locationConfig.set("pending-essence-refunds", null);
        pendingEssenceRefunds.forEach((playerId, amount) ->
            locationConfig.set("pending-essence-refunds." + playerId, amount));
        try {
            AtomicYamlFile.save(locationConfig, locationFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save pending dungeon Essence refunds: " + ex.getMessage());
        }
    }

    private void inviteTeamAndAllies(Player owner) {
        if (encounter == null) return;
        for (Player candidate : Bukkit.getOnlinePlayers()) {
            if (candidate.equals(owner) || plugin.getTeamManager() == null
                || !plugin.getTeamManager().sameTeam(owner.getUniqueId(), candidate.getUniqueId())) {
                continue;
            }
            if (isDungeonWorld(candidate.getWorld())) {
                continue;
            }
            encounter.invited.add(candidate.getUniqueId());
            Component prompt = MessageUtil.prefixedRaw("<gold><bold>" + owner.getName() + "</bold></gold> <gray>is summoning <red>" + encounter.type.plainDisplayName() + "</red>. Join before it appears?</gray>")
                .append(Component.newline())
                .append(MM.deserialize("<green><bold>[JOIN]</bold></green>")
                    .clickEvent(ClickEvent.runCommand("/bossjoin accept"))
                    .hoverEvent(HoverEvent.showText(Component.text("Teleport into the boss fight"))))
                .append(Component.space())
                .append(MM.deserialize("<red><bold>[DECLINE]</bold></red>")
                    .clickEvent(ClickEvent.runCommand("/bossjoin deny")))
                .append(Component.space())
                .append(MM.deserialize("<dark_gray>10s | Bedrock: /bossjoin accept</dark_gray>"));
            candidate.sendMessage(prompt);
            candidate.playSound(candidate.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9f, 1.25f);
        }
    }

    private List<Player> presentTeamMembers(Player owner) {
        if (world == null || plugin.getTeamManager() == null) {
            return List.of();
        }
        return world.getPlayers().stream()
            .filter(candidate -> !candidate.equals(owner))
            .filter(candidate -> plugin.getTeamManager().sameTeam(owner.getUniqueId(), candidate.getUniqueId()))
            .toList();
    }

    public void respondToInvite(Player player, boolean accept) {
        if (player == null || encounter == null || !encounter.invitesOpen || !encounter.invited.remove(player.getUniqueId())) {
            if (player != null) player.sendMessage(MessageUtil.warn("You do not have an active boss invitation."));
            return;
        }
        if (!accept) {
            player.sendMessage(MessageUtil.info("Boss invitation declined."));
            return;
        }
        encounter.participants.add(player.getUniqueId());
        prepareFighter(player);
        int remaining = countdownSecondsRemaining(encounter.countdownEndsAtMillis, System.currentTimeMillis());
        player.sendMessage(MessageUtil.success(
            "Joined <white>" + encounter.type.plainDisplayName() + "</white>. It appears in <white>" + remaining + " second"
                + (remaining == 1 ? "" : "s") + "</white>."
        ));
    }

    public void leaveQueue(Player player) {
        if (player == null) return;
        boolean removed = summonQueue.removeIf(entry -> entry.ownerId().equals(player.getUniqueId()));
        player.sendMessage(removed ? MessageUtil.success("Removed your boss summon from the queue.") : MessageUtil.warn("You are not in the boss queue."));
    }

    public String queueStatus() {
        String phase = encounter == null ? "idle" : encounter.looting ? "looting" : encounter.bossSpawned ? "fighting" : "preparing";
        return "Arena: " + phase + "; queue: " + summonQueue.size();
    }

    public UUID currentEncounterOwnerId() {
        return encounter == null ? null : encounter.ownerId;
    }

    public boolean isAdminTestEncounter() {
        return encounter != null && encounter.testMode;
    }

    public BossManager.BossType activeEncounterType() {
        return encounter == null || encounter.looting ? null : encounter.type;
    }

    private boolean isQueued(UUID playerId) {
        return summonQueue.stream().anyMatch(entry -> entry.ownerId().equals(playerId));
    }

    private void processQueue() {
        if (world == null || encounter != null || summonPending) return;
        if (plugin.getBossManager().hasActiveBossInWorld(world)) {
            plugin.getLogger().warning("Removed an orphaned Boss Dungeon fight with no encounter owner.");
            plugin.getBossManager().despawnBossesInWorld(world);
            cleanupCombatEntities(true);
            return;
        }
        while (!summonQueue.isEmpty()) {
            QueuedSummon next = summonQueue.removeFirst();
            Player owner = Bukkit.getPlayer(next.ownerId());
            if (owner == null || !owner.isOnline()) continue;
            if (startEncounter(owner, next.type())) return;
        }
    }

    private void prepareFighter(Player player) {
        player.closeInventory();
        restoredGameModes.putIfAbsent(player.getUniqueId(), player.getGameMode() == GameMode.SPECTATOR ? GameMode.SURVIVAL : player.getGameMode());
        player.setGameMode(GameMode.SURVIVAL);
        managedTeleport(player, fightSpawn());
    }

    private void makeSpectator(Player player) {
        player.closeInventory();
        restoredGameModes.putIfAbsent(player.getUniqueId(), player.getGameMode() == GameMode.SPECTATOR ? GameMode.SURVIVAL : player.getGameMode());
        player.setGameMode(GameMode.SPECTATOR);
        managedTeleport(player, spectatorSpawn());
    }

    public Map<Material, Integer> summonCosts(BossManager.BossType type) {
        return summonCosts(type.id(), type.ritual().focusBlock(), type.ritual().catalyst());
    }

    public long summonEssenceCost(BossManager.BossType type) {
        if (type == null) {
            return 0L;
        }
        long configured = plugin.getConfig().getLong(
            "boss-dungeon.essence-costs." + type.id(),
            defaultEssenceCost(type.progressionTier())
        );
        return Math.max(1L, Math.min(1_000_000L, configured));
    }

    static long defaultEssenceCost(int tier) {
        return switch (Math.max(1, Math.min(10, tier))) {
            case 1 -> 25L;
            case 2 -> 40L;
            case 3 -> 60L;
            case 4 -> 80L;
            case 5 -> 105L;
            case 6 -> 130L;
            case 7 -> 160L;
            case 8 -> 195L;
            case 9 -> 235L;
            default -> 300L;
        };
    }

    static Map<Material, Integer> summonCosts(String bossId, Material focus, Material catalyst) {
        LinkedHashMap<Material, Integer> costs = new LinkedHashMap<>();
        add(costs, focus, 1);
        add(costs, catalyst, 1);
        switch (bossId) {
            case "yule_the_minion" -> { add(costs, Material.SOUL_SAND, 1); add(costs, Material.GOLD_BLOCK, 2); }
            case "kael_the_ashen" -> add(costs, Material.BONE_BLOCK, 3);
            case "vesper_the_widow_queen" -> { add(costs, Material.MOSS_BLOCK, 1); add(costs, Material.BLACK_CANDLE, 2); }
            case "voralith_the_crimson_warden" -> {
                add(costs, Material.SCULK_CATALYST, 1);
                add(costs, Material.REDSTONE_BLOCK, 1);
                add(costs, Material.SOUL_LANTERN, 2);
            }
            case "aurelion_the_rift_seraph" -> { add(costs, Material.PURPUR_BLOCK, 1); add(costs, Material.END_STONE_BRICKS, 2); }
            case "morvessa_the_runebloom_witch" -> { add(costs, Material.AMETHYST_BLOCK, 1); add(costs, Material.FLOWERING_AZALEA_LEAVES, 2); }
            case "nereida_the_abyss_mother" -> { add(costs, Material.PRISMARINE, 1); add(costs, Material.SEA_LANTERN, 2); }
            case "iron_saint" -> { add(costs, Material.SMITHING_TABLE, 1); add(costs, Material.IRON_BLOCK, 1); }
            case "mirewood_the_root_tyrant" -> { add(costs, Material.MOSS_BLOCK, 1); add(costs, Material.OAK_SAPLING, 2); }
            case "corrupted_oathkeeper" -> {
                add(costs, Material.CRYING_OBSIDIAN, 1);
                add(costs, Material.MAGMA_BLOCK, 2);
                add(costs, Material.SCULK_CATALYST, 2);
            }
            default -> throw new IllegalArgumentException("Unknown boss id: " + bossId);
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(costs));
    }

    private static void add(Map<Material, Integer> costs, Material material, int amount) {
        costs.merge(material, amount, Integer::sum);
    }

    private ItemStack bossItem(Player player, BossManager.BossType type) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Tier <white>" + type.progressionTier() + "</white></gray>");
        long essenceCost = summonEssenceCost(type);
        long essenceBalance = plugin.getEssenceManager() == null ? 0L : plugin.getEssenceManager().balance(player);
        boolean hasEssence = essenceBalance >= essenceCost;
        lore.add((hasEssence ? "<green>" : "<red>") + essenceCost + " Essence</" + (hasEssence ? "green>" : "red>")
            + " <dark_gray>• Balance: " + essenceBalance + "</dark_gray>");
        for (Map.Entry<Material, Integer> entry : summonCosts(type).entrySet()) {
            boolean owned = InventoryRecipeUtil.hasPlainMaterials(plugin, player, Map.of(entry.getKey(), entry.getValue()));
            lore.add((owned ? "<green>" : "<red>") + entry.getValue() + "x " + pretty(entry.getKey()) + (owned ? "</green>" : "</red>"));
        }
        lore.add("<dark_gray>Summoner pays once; teammates join free.</dark_gray>");
        lore.add("<yellow>Click to review and confirm.</yellow>");
        return item(type.menuIcon(), type.displayName(), lore);
    }

    private BossManager.BossType bossForSlot(int slot) {
        List<BossManager.BossType> bosses = BossManager.BossType.progressionOrder();
        for (int i = 0; i < bosses.size() && i < BOSS_SLOTS.length; i++) {
            if (BOSS_SLOTS[i] == slot) {
                return bosses.get(i);
            }
        }
        return null;
    }

    private void enterDungeon(Player player) {
        if (world == null) {
            player.sendMessage(MessageUtil.error("The boss dungeon is unavailable."));
            return;
        }
        player.closeInventory();
        Location destination = encounter == null ? entry() : spectatorSpawn();
        if (encounter != null) {
            makeSpectator(player);
            return;
        }
        UUID playerId = player.getUniqueId();
        managedTeleports.add(playerId);
        player.teleportAsync(destination).whenComplete((success, error) -> {
            managedTeleports.remove(playerId);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (error == null && Boolean.TRUE.equals(success)) {
                    player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.7f, 1.2f);
                } else {
                    player.sendMessage(MessageUtil.error("The dungeon teleport failed. Try again."));
                }
            });
        });
    }

    private void leaveDungeon(Player player) {
        Location spawn = plugin.getConfigManager().exactSpawnLocation();
        if (spawn == null) {
            player.sendMessage(MessageUtil.error("Server spawn is unavailable."));
            return;
        }
        player.closeInventory();
        clearDungeonCombatState(player);
        restoreGameMode(player);
        managedTeleport(player, spawn);
    }

    private void clearDungeonCombatState(Player player) {
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setVelocity(new Vector());
        for (PotionEffectType type : List.of(
            PotionEffectType.POISON,
            PotionEffectType.SLOWNESS,
            PotionEffectType.WEAKNESS,
            PotionEffectType.DARKNESS,
            PotionEffectType.MINING_FATIGUE
        )) {
            player.removePotionEffect(type);
        }
    }

    private Location entry() {
        return locations == null ? new Location(world, 0.5, 104.0, -29.5, 0.0f, 0.0f) : locations.entry().clone();
    }

    private Location bossSpawn() {
        return locations == null ? new Location(world, 0.5, 101.0, 0.5, 0.0f, 0.0f) : locations.boss().clone();
    }

    private Location fightSpawn() {
        return locations == null ? new Location(world, 0.5, 101.0, -15.5, 0.0f, 0.0f) : locations.fight().clone();
    }

    private Location spectatorSpawn() {
        return locations == null ? new Location(world, 0.5, 112.0, -29.5, 0.0f, 0.0f) : locations.spectator().clone();
    }

    private Location keeperSpawn() {
        return locations == null ? new Location(world, 0.5, 104.0, -34.5, 0.0f, 0.0f) : locations.keeper().clone();
    }

    private void ensureArenaKeeper() {
        if (world == null || plugin.getGuideNpcManager() == null) {
            return;
        }
        Location keeperLocation = keeperSpawn();
        world.getChunkAt(keeperLocation);
        List<Location> arenaKeepers = arenaKeeperLocations();
        int removed = 0;
        while (arenaKeepers.size() > 1) {
            int result = plugin.getGuideNpcManager().removeNearest(GuideNpcType.DUNGEON_KEEPER, keeperLocation, 512.0D);
            if (result <= 0) {
                break;
            }
            removed += result;
            arenaKeepers = arenaKeeperLocations();
        }
        if (removed > 0) {
            plugin.getLogger().warning("Removed " + removed + " duplicate boss-dungeon keeper NPC(s).");
        }
        if (arenaKeepers.isEmpty()) {
            plugin.getGuideNpcManager().spawnNpc(GuideNpcType.DUNGEON_KEEPER, keeperLocation);
        }
    }

    private List<Location> arenaKeeperLocations() {
        return plugin.getGuideNpcManager().locations(GuideNpcType.DUNGEON_KEEPER).stream()
            .filter(location -> isDungeonWorld(location.getWorld()))
            .toList();
    }

    public boolean setLocation(String point, Location location) {
        if (world == null || location == null || !isDungeonWorld(location.getWorld())) return false;
        String key = point == null ? "" : point.toLowerCase(java.util.Locale.ROOT);
        if (!Set.of("entry", "fight", "spectator", "boss", "keeper").contains(key)) return false;
        locationConfig.set("locations." + key, location);
        try {
            AtomicYamlFile.save(locationConfig, locationFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save boss dungeon locations: " + ex.getMessage());
            return false;
        }
        locations = loadLocations();
        if ("entry".equals(key)) world.setSpawnLocation(locations.entry());
        keepArenaChunksLoaded();
        return true;
    }

    public Map<String, Location> configuredLocations() {
        return Map.of("entry", entry(), "fight", fightSpawn(), "spectator", spectatorSpawn(), "boss", bossSpawn(), "keeper", keeperSpawn());
    }

    private DungeonLocations loadLocations() {
        return new DungeonLocations(
            configured("entry", new Location(world, 0.5, 104.0, -29.5, 0.0f, 0.0f)),
            configured("fight", new Location(world, 0.5, 101.0, -15.5, 0.0f, 0.0f)),
            configured("spectator", new Location(world, 0.5, 112.0, -29.5, 0.0f, 0.0f)),
            configured("boss", new Location(world, 0.5, 101.0, 0.5, 0.0f, 0.0f)),
            configured("keeper", new Location(world, 0.5, 104.0, -34.5, 0.0f, 0.0f))
        );
    }

    private Location configured(String key, Location fallback) {
        Location location = locationConfig.getLocation("locations." + key);
        return location != null && isDungeonWorld(location.getWorld()) ? location : fallback;
    }

    private void keepArenaChunksLoaded() {
        if (world == null || locations == null) return;
        world.removePluginChunkTickets(plugin);
        for (Location location : configuredLocations().values()) {
            world.addPluginChunkTicket(location.getBlockX() >> 4, location.getBlockZ() >> 4, plugin);
        }
        int arenaRadius = (int) Math.ceil(BossManager.BossType.progressionOrder().stream()
            .mapToDouble(type -> type.ritual().arenaRadius())
            .max()
            .orElse(24.0D)) + 2;
        Location center = bossSpawn();
        int minChunkX = (center.getBlockX() - arenaRadius) >> 4;
        int maxChunkX = (center.getBlockX() + arenaRadius) >> 4;
        int minChunkZ = (center.getBlockZ() - arenaRadius) >> 4;
        int maxChunkZ = (center.getBlockZ() + arenaRadius) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                world.addPluginChunkTicket(chunkX, chunkZ, plugin);
            }
        }
    }

    public void onBossFightFinished(boolean victory, Block lootChest) {
        if (encounter == null) return;
        encounter.invitesOpen = false;
        Bukkit.getScheduler().runTask(plugin, () -> cleanupCombatEntities(false));
        if (encounter.testMode) {
            if (lootChest != null && plugin.getBossManager() != null) plugin.getBossManager().clearDungeonLootChest(lootChest);
            for (UUID participantId : encounter.participants) {
                Player participant = Bukkit.getPlayer(participantId);
                if (participant != null) participant.sendMessage(MessageUtil.info("Admin test complete. Test loot was removed."));
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> finishEncounter(victory, false), 40L);
            return;
        }
        if (!victory) {
            finishEncounter(false, true);
            return;
        }
        encounter.looting = true;
        encounter.lootChest = lootChest;
        for (UUID participantId : encounter.participants) {
            Player participant = Bukkit.getPlayer(participantId);
            if (participant != null && participant.isOnline() && !encounter.eliminated.contains(participantId)) {
                participant.sendMessage(MessageUtil.success("Boss defeated. Loot the chest; everyone returns to spawn in <white>2 minutes</white>."));
            }
        }
        lootTask = Bukkit.getScheduler().runTaskLater(plugin, () -> finishEncounter(true, true), LOOT_WINDOW_TICKS);
        checkLootChestEmpty();
    }

    private void checkLootChestEmpty() {
        if (encounter == null || !encounter.looting || encounter.lootChest == null) return;
        if (!(encounter.lootChest.getState() instanceof Chest chest) || !chest.getBlockInventory().isEmpty()) return;
        plugin.getBossManager().clearDungeonLootChest(encounter.lootChest);
        encounter.lootChest = null;
        if (lootTask != null) lootTask.cancel();
        lootTask = Bukkit.getScheduler().runTaskLater(plugin, () -> finishEncounter(true, true), 60L);
    }

    private void finishEncounter(boolean victory, boolean advanceQueue) {
        DungeonEncounter finished = encounter;
        encounter = null;
        summonPending = false;
        cancelCountdownTask();
        if (lootTask != null) {
            lootTask.cancel();
            lootTask = null;
        }
        if (finished != null) {
            recoverArenaItems(finished);
            cleanupCombatEntities(true);
            Set<UUID> occupants = new LinkedHashSet<>(finished.participants);
            for (Player player : new ArrayList<>(world == null ? List.<Player>of() : world.getPlayers())) {
                occupants.add(player.getUniqueId());
            }
            for (UUID playerId : occupants) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.sendMessage(victory ? MessageUtil.info("The dungeon closes. Returning to spawn.") : MessageUtil.warn("The boss fight ended. Returning to spawn."));
                    leaveDungeon(player);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> recoverStoredDrops(player), 20L);
                }
            }
        }
        if (advanceQueue && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskLater(plugin, this::processQueue, 100L);
        }
    }

    private void resetOrphanedArena() {
        if (world == null || encounter != null || plugin.getBossManager() == null) {
            return;
        }
        int bosses = plugin.getBossManager().despawnBossesInWorld(world);
        int debris = cleanupCombatEntities(true);
        int chests = clearStaleLootChests();
        if (bosses + debris + chests > 0) {
            plugin.getLogger().warning("Reset stale Boss Dungeon state: " + bosses + " boss(es), " + debris + " combat entity/entities, " + chests + " loot chest(s).");
        }
    }

    private int cleanupCombatEntities(boolean includeExperience) {
        if (world == null) {
            return 0;
        }
        int removed = 0;
        for (Entity entity : new ArrayList<>(world.getEntities())) {
            boolean transientEntity = entity instanceof Projectile
                || entity instanceof AreaEffectCloud
                || entity instanceof EvokerFangs
                || entity instanceof FallingBlock
                || entity instanceof Firework
                || entity instanceof TNTPrimed
                || includeExperience && entity instanceof ExperienceOrb;
            if (!transientEntity && (plugin.getBossManager() == null || !plugin.getBossManager().isBossEncounterEntity(entity))) {
                continue;
            }
            entity.remove();
            removed++;
        }
        return removed;
    }

    private int clearStaleLootChests() {
        if (world == null || plugin.getBossManager() == null) {
            return 0;
        }
        int removed = 0;
        for (Chunk chunk : world.getLoadedChunks()) {
            for (BlockState state : chunk.getTileEntities()) {
                if (!plugin.getBossManager().isDungeonLootChest(state.getBlock())) {
                    continue;
                }
                UUID ownerId = plugin.getBossManager().dungeonLootOwner(state.getBlock());
                if (ownerId != null && state instanceof Chest chest) {
                    for (ItemStack item : chest.getBlockInventory().getContents()) {
                        if (item != null && !item.getType().isAir()) {
                            deliverOrStore(ownerId, item.clone());
                        }
                    }
                    chest.getBlockInventory().clear();
                }
                plugin.getBossManager().clearDungeonLootChest(state.getBlock());
                removed++;
            }
        }
        return removed;
    }

    private void recoverArenaItems(DungeonEncounter finished) {
        UUID fallbackOwner = finished.ownerId;
        if (finished.lootChest != null && finished.lootChest.getState() instanceof Chest chest) {
            for (ItemStack item : chest.getBlockInventory().getContents()) {
                if (item != null && !item.getType().isAir()) deliverOrStore(fallbackOwner, item.clone());
            }
            chest.getBlockInventory().clear();
            plugin.getBossManager().clearDungeonLootChest(finished.lootChest);
        }
        if (world == null) return;
        for (Entity entity : new ArrayList<>(world.getEntities())) {
            if (!(entity instanceof Item item)) continue;
            String owner = item.getPersistentDataContainer().get(droppedItemOwnerKey, PersistentDataType.STRING);
            UUID ownerId = fallbackOwner;
            if (owner != null) {
                try { ownerId = UUID.fromString(owner); } catch (IllegalArgumentException ignored) { }
            }
            ItemStack stack = item.getItemStack().clone();
            item.remove();
            deliverOrStore(ownerId, stack);
        }
    }

    private void deliverOrStore(UUID ownerId, ItemStack item) {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && owner.isOnline()) {
            Map<Integer, ItemStack> leftovers = owner.getInventory().addItem(item);
            for (ItemStack leftover : leftovers.values()) storeOwnedDrop(ownerId, leftover);
            return;
        }
        storeOwnedDrop(ownerId, item);
    }

    private void storeOwnedDrop(UUID ownerId, ItemStack item) {
        if (world == null || item == null || item.getType().isAir()) return;
        world.dropItem(keeperSpawn(), item, dropped -> {
            dropped.getPersistentDataContainer().set(droppedItemOwnerKey, PersistentDataType.STRING, ownerId.toString());
            dropped.setInvulnerable(true);
            dropped.setUnlimitedLifetime(true);
        });
    }

    private void recoverStoredDrops(Player player) {
        if (world == null) return;
        String owner = player.getUniqueId().toString();
        for (Entity entity : new ArrayList<>(world.getEntities())) {
            if (!(entity instanceof Item item)) continue;
            if (!owner.equals(item.getPersistentDataContainer().get(droppedItemOwnerKey, PersistentDataType.STRING))) continue;
            ItemStack stack = item.getItemStack().clone();
            item.remove();
            InventoryRecipeUtil.giveOrDrop(player, stack);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLootOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || encounter == null || !encounter.looting
            || !(event.getInventory().getHolder(false) instanceof Chest chest)
            || !plugin.getBossManager().isDungeonLootChest(chest.getBlock())) return;
        if (!encounter.participants.contains(player.getUniqueId()) || encounter.eliminated.contains(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.warn("Only surviving fight participants can open this chest."));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLootClick(InventoryClickEvent event) {
        if (encounter != null && encounter.looting) Bukkit.getScheduler().runTask(plugin, this::checkLootChestEmpty);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLootClose(InventoryCloseEvent event) {
        if (encounter != null && encounter.looting) Bukkit.getScheduler().runTask(plugin, this::checkLootChestEmpty);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAutomatedInventoryMove(InventoryMoveItemEvent event) {
        if (inventoryInDungeon(event.getSource()) || inventoryInDungeon(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAutomatedItemPickup(InventoryPickupItemEvent event) {
        if (inventoryInDungeon(event.getInventory())) event.setCancelled(true);
    }

    private boolean inventoryInDungeon(Inventory inventory) {
        Location location = inventory == null ? null : inventory.getLocation();
        return location != null && isDungeonWorld(location.getWorld());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!isDungeonWorld(event.getPlayer().getWorld())) return;
        if (encounter == null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.warn("Items cannot be dropped while the arena is idle."));
            return;
        }
        event.getItemDrop().getPersistentDataContainer().set(droppedItemOwnerKey, PersistentDataType.STRING, event.getPlayer().getUniqueId().toString());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!isDungeonWorld(event.getItem().getWorld())) return;
        String owner = event.getItem().getPersistentDataContainer().get(droppedItemOwnerKey, PersistentDataType.STRING);
        if (owner != null && (!(event.getEntity() instanceof Player player) || !owner.equals(player.getUniqueId().toString()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isDungeonWorld(event.getBlock().getWorld()) && !event.getPlayer().hasPermission("smpcore.dungeon.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isDungeonWorld(event.getBlock().getWorld()) && !event.getPlayer().hasPermission("smpcore.dungeon.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || !isDungeonWorld(event.getClickedBlock().getWorld()) || event.getPlayer().hasPermission("smpcore.dungeon.admin")) return;
        if (plugin.getBossManager().isDungeonLootChest(event.getClickedBlock())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (isDungeonWorld(event.getRightClicked().getWorld()) && !event.getPlayer().hasPermission("smpcore.dungeon.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDungeonEntityDamage(EntityDamageEvent event) {
        if (!isDungeonWorld(event.getEntity().getWorld()) || event.getEntity() instanceof Player) {
            return;
        }
        if (plugin.getBossManager() == null || !plugin.getBossManager().isBossEncounterEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (isDungeonWorld(event.getBlock().getWorld()) && !event.getPlayer().hasPermission("smpcore.dungeon.admin")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (isDungeonWorld(event.getBlock().getWorld()) && !event.getPlayer().hasPermission("smpcore.dungeon.admin")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityBlockChange(EntityChangeBlockEvent event) {
        if (isDungeonWorld(event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (isDungeonWorld(event.getEntity().getWorld()) && (event.getPlayer() == null || !event.getPlayer().hasPermission("smpcore.dungeon.admin"))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (isDungeonWorld(event.getEntity().getWorld()) && (event.getPlayer() == null || !event.getPlayer().hasPermission("smpcore.dungeon.admin"))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (isDungeonWorld(event.getEntity().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleCreate(VehicleCreateEvent event) {
        if (isDungeonWorld(event.getVehicle().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) { if (isDungeonWorld(event.getBlock().getWorld())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) { if (isDungeonWorld(event.getBlock().getWorld())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) { if (isDungeonWorld(event.getBlock().getWorld())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) { if (isDungeonWorld(event.getBlock().getWorld())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) { if (isDungeonWorld(event.getBlock().getWorld())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (isDungeonWorld(event.getLocation().getWorld())) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (isDungeonWorld(event.getBlock().getWorld())) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isDungeonWorld(player.getWorld())) {
            return;
        }
        if (encounter != null && encounter.participants.contains(player.getUniqueId())
            && !encounter.eliminated.contains(player.getUniqueId()) && plugin.getBossManager() != null) {
            Location confined = plugin.getBossManager().confinedArenaLocation(event.getTo());
            if (confined != null) {
                event.setTo(confined);
                player.sendActionBar(MM.deserialize("<red>The arena refuses to let you leave.</red>"));
                return;
            }
        }
        if (player.getY() < 80.0) {
            managedTeleport(event.getPlayer(), encounter == null ? entry() : spectatorSpawn());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!isDungeonWorld(player.getWorld())) return;
        dungeonDeaths.add(player.getUniqueId());
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        if (encounter != null && encounter.participants.contains(player.getUniqueId())) encounter.eliminated.add(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, this::checkEncounterDefeat, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!dungeonDeaths.remove(event.getPlayer().getUniqueId())) {
            return;
        }
        if (encounter != null) {
            event.setRespawnLocation(spectatorSpawn());
            Bukkit.getScheduler().runTask(plugin, () -> makeSpectator(event.getPlayer()));
        } else {
            Location spawn = plugin.getConfigManager().exactSpawnLocation();
            if (spawn != null) event.setRespawnLocation(spawn);
            Bukkit.getScheduler().runTask(plugin, () -> {
                clearDungeonCombatState(event.getPlayer());
                restoreGameMode(event.getPlayer());
            });
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> recoverStoredDrops(event.getPlayer()));
        Bukkit.getScheduler().runTaskLater(plugin, () -> deliverPendingEssenceRefund(event.getPlayer(), 10), 20L);
        if (isDungeonWorld(event.getPlayer().getWorld())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (encounter != null) makeSpectator(event.getPlayer()); else leaveDungeon(event.getPlayer());
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (managedTeleports.remove(player.getUniqueId())) return;
        boolean fromDungeon = event.getFrom().getWorld() != null && isDungeonWorld(event.getFrom().getWorld());
        boolean toDungeon = event.getTo().getWorld() != null && isDungeonWorld(event.getTo().getWorld());
        if (fromDungeon && !toDungeon && encounter != null && encounter.participants.contains(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.warn("You cannot leave an active boss fight."));
            return;
        }
        if (toDungeon && encounter != null) Bukkit.getScheduler().runTask(plugin, () -> {
            if (!encounter.participants.contains(player.getUniqueId()) || encounter.eliminated.contains(player.getUniqueId())) makeSpectator(player);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        nextMenuOpenAt.remove(playerId);
        nextMenuActionAt.remove(playerId);
        managedTeleports.remove(playerId);
        if (encounter != null && encounter.participants.contains(event.getPlayer().getUniqueId())) {
            encounter.eliminated.add(playerId);
            Bukkit.getScheduler().runTaskLater(plugin, this::checkEncounterDefeat, 20L);
        }
    }

    private void checkEncounterDefeat() {
        if (encounter == null || encounter.looting) return;
        for (UUID participantId : encounter.participants) {
            Player participant = Bukkit.getPlayer(participantId);
            if (!encounter.eliminated.contains(participantId) && participant != null && participant.isOnline()
                && !participant.isDead() && participant.getGameMode() != GameMode.SPECTATOR) return;
        }
        if (!encounter.bossSpawned) {
            refundEncounterEntry(encounter, "countdown_abandoned");
        }
        plugin.getBossManager().despawnBossesInWorld(world);
        finishEncounter(false, true);
    }

    private void managedTeleport(Player player, Location destination) {
        if (player == null || destination == null) return;
        UUID playerId = player.getUniqueId();
        managedTeleports.add(playerId);
        player.teleportAsync(destination).whenComplete((success, error) -> managedTeleports.remove(playerId));
    }

    private void restoreGameMode(Player player) {
        GameMode mode = restoredGameModes.remove(player.getUniqueId());
        if (mode != null) player.setGameMode(mode);
        else if (player.getGameMode() == GameMode.SPECTATOR) player.setGameMode(GameMode.SURVIVAL);
    }

    private World provisionAndLoadWorld() {
        World loaded = Bukkit.getWorld(WORLD_NAME);
        if (loaded != null) {
            return loaded;
        }
        Path container = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        Path target = container.resolve(WORLD_NAME).normalize();
        if (!target.startsWith(container)) {
            return null;
        }
        Path primaryWorld = Bukkit.getWorlds().isEmpty()
            ? container.resolve("world").normalize()
            : container.resolve(Bukkit.getWorlds().getFirst().getName()).normalize();
        Path modernDimension = primaryWorld.resolve("dimensions/minecraft").resolve(WORLD_NAME).normalize();
        boolean migratedWorldExists = modernDimension.startsWith(primaryWorld)
            && Files.isDirectory(modernDimension.resolve("region"));
        if (migratedWorldExists && Files.exists(target)) {
            if (!Files.isRegularFile(target.resolve(MANAGED_WORLD_MARKER))) {
                plugin.getLogger().severe("A migrated boss dungeon and an unmarked legacy boss_dungeon folder both exist; refusing to touch either one.");
                return null;
            }
            try {
                deleteStaging(target);
            } catch (IOException ex) {
                plugin.getLogger().severe("Could not remove SMPCore's migrated dungeon import copy: " + ex.getMessage());
                return null;
            }
        }
        if (!Files.exists(target) && !migratedWorldExists) {
            try {
                installBundledWorld(container, target);
            } catch (IOException ex) {
                plugin.getLogger().severe("Could not install boss dungeon world: " + ex.getMessage());
                return null;
            }
        }
        if (Files.exists(target) && !Files.isRegularFile(target.resolve("level.dat"))) {
            plugin.getLogger().severe("Boss dungeon folder exists without level.dat; refusing to overwrite it.");
            return null;
        }
        WorldCreator creator = new WorldCreator(WORLD_NAME);
        creator.environment(World.Environment.NORMAL);
        creator.generator(new VoidGenerator());
        return creator.createWorld();
    }

    private void installBundledWorld(Path container, Path target) throws IOException {
        Path staging = container.resolve(WORLD_NAME + ".installing").normalize();
        if (!staging.startsWith(container)) {
            throw new IOException("Unsafe staging path.");
        }
        deleteStaging(staging);
        Files.createDirectories(staging);
        try (InputStream raw = plugin.getResource(WORLD_RESOURCE)) {
            if (raw == null) {
                throw new IOException("Bundled arena archive is missing.");
            }
            try (ZipInputStream zip = new ZipInputStream(raw)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName().replace('\\', '/');
                    if (!name.startsWith(WORLD_ARCHIVE_ROOT)) {
                        continue;
                    }
                    String relative = name.substring(WORLD_ARCHIVE_ROOT.length());
                    if (relative.isBlank()) {
                        continue;
                    }
                    Path output = staging.resolve(relative).normalize();
                    if (!output.startsWith(staging)) {
                        throw new IOException("Unsafe arena archive entry.");
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(output);
                    } else {
                        Files.createDirectories(output.getParent());
                        Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException ex) {
            deleteStaging(staging);
            throw ex;
        }
        if (!Files.isRegularFile(staging.resolve("level.dat"))) {
            deleteStaging(staging);
            throw new IOException("Arena archive did not contain level.dat.");
        }
        Files.writeString(staging.resolve(MANAGED_WORLD_MARKER), "SMPCore managed boss dungeon import\n");
        try {
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailure) {
            Files.move(staging, target);
        }
    }

    private void deleteStaging(Path staging) throws IOException {
        if (!Files.exists(staging)) {
            return;
        }
        try (var paths = Files.walk(staging)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private void configureWorld(World arena) {
        arena.setAutoSave(true);
        arena.setGameRule(GameRules.PVP, false);
        arena.setSpawnLocation(entry());
        arena.setGameRule(GameRules.SPAWN_MOBS, false);
        arena.setGameRule(GameRules.ADVANCE_WEATHER, false);
        arena.setGameRule(GameRules.ADVANCE_TIME, false);
        arena.setGameRule(GameRules.MOB_GRIEFING, false);
        arena.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        arena.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        arena.setStorm(false);
        arena.setThundering(false);
        arena.setTime(18000L);
        arena.getWorldBorder().setCenter(0.5, 0.5);
        arena.getWorldBorder().setSize(160.0);
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, MenuItemUtil.INACTIVE_SLOT_LORE);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private ItemStack item(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(MenuItemUtil.visibleMiniName(name)));
        meta.lore(MenuItemUtil.visibleMiniLore(name, loreLines).stream().map(MM::deserialize).toList());
        item.setItemMeta(meta);
        return item;
    }

    private String pretty(Material material) {
        StringBuilder out = new StringBuilder();
        for (String part : material.name().toLowerCase(java.util.Locale.ROOT).split("_")) {
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private enum DungeonView { ENTRANCE, CATALOG, CONFIRM }

    private record QueuedSummon(UUID ownerId, BossManager.BossType type, long queuedAt) { }

    private record DungeonLocations(Location entry, Location fight, Location spectator, Location boss, Location keeper) { }

    private static final class DungeonEncounter {
        private final UUID ownerId;
        private final BossManager.BossType type;
        private final Map<Material, Integer> costs;
        private final long essenceCost;
        private final boolean paid;
        private final boolean testMode;
        private final Set<UUID> participants = new LinkedHashSet<>();
        private final Set<UUID> invited = new LinkedHashSet<>();
        private final Set<UUID> eliminated = new LinkedHashSet<>();
        private boolean invitesOpen = true;
        private boolean bossSpawned;
        private boolean looting;
        private boolean entryRefunded;
        private long countdownEndsAtMillis;
        private int lastCountdownSecond = -1;
        private Block lootChest;

        private DungeonEncounter(UUID ownerId, BossManager.BossType type, Map<Material, Integer> costs, long essenceCost, boolean paid, boolean testMode) {
            this.ownerId = ownerId;
            this.type = type;
            this.costs = costs;
            this.essenceCost = essenceCost;
            this.paid = paid;
            this.testMode = testMode;
        }
    }

    private record DungeonMenuHolder(UUID playerId, DungeonView view, BossManager.BossType type)
        implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private static final class VoidGenerator extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
    }
}
