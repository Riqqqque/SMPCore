package me.rique.smpcore.tavern;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.database.DatabaseManager;
import me.rique.smpcore.npc.GuideNpcManager.GuideNpcType;
import me.rique.smpcore.util.AtomicYamlFile;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.InventoryRecipeUtil;
import me.rique.smpcore.util.ItemEscrowService;
import me.rique.smpcore.util.ItemEscrowService.EscrowedItem;
import me.rique.smpcore.util.ItemEscrowService.EscrowPayout;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.util.VisualRangeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class TavernManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int MENU_SIZE = 27;
    private static final long CANTEEN_COOLDOWN_MS = 30L * 60L * 1000L;
    private static final long COIN_COOLDOWN_MS = 24L * 60L * 60L * 1000L;
    private static final int BOUNTY_KILLS = 12;
    private static final long BOUNTY_REWARD = 75L;
    private static final long GAMBLING_LUCK_DRINK_MS = 30_000L;
    private static final long GAMBLING_LUCK_FOOD_MS = 30L * 60L * 1000L;
    private static final double GAMBLING_LUCK_DRINK_CHANCE = 0.03D;
    private static final double MAX_GAMBLING_LUCK = 0.10D;
    private static final double BREWMASTER_DRINK_RANGE_SQUARED = 100.0D * 100.0D;
    private static final int[] SLOT_REELS = {11, 13, 15};
    private static final int[] DART_LANE = {9, 10, 11, 12, 13, 14, 15, 16, 17};
    private static final int[] DART_START_POSITIONS = {0, 1, 2, 6, 7, 8};
    private static final int DART_RAPID_CLICK_LIMIT = 3;
    static final long DARTS_PERFECT_REWARD = 10L;
    private static final int[] CARD_SLOTS = {20, 22, 24};
    private static final List<Material> SLOT_SYMBOLS = List.of(
        Material.SWEET_BERRIES, Material.GOLD_NUGGET, Material.EMERALD,
        Material.DIAMOND, Material.NETHER_STAR, Material.HONEY_BOTTLE
    );

    private final SMPCore plugin;
    private final File stationsFile;
    private final File leaderboardsFile;
    private final Map<StationType, Set<BlockKey>> stations = new EnumMap<>(StationType.class);
    private final Map<BlockKey, TableLobby> tableLobbies = new ConcurrentHashMap<>();
    private final Map<UUID, BlockKey> playerTableLobbies = new ConcurrentHashMap<>();
    private final Map<BlockKey, UUID> activeTableMatches = new ConcurrentHashMap<>();
    private final Map<UUID, CardMatch> cardMatches = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerCardMatches = new ConcurrentHashMap<>();
    private final Map<UUID, DartSession> dartSessions = new ConcurrentHashMap<>();
    private final Map<UUID, SlotSession> slotSessions = new ConcurrentHashMap<>();
    private final Map<UUID, SlotWager> lastSlotWagers = new ConcurrentHashMap<>();
    private final Map<UUID, SlotWager> selectedSlotWagers = new ConcurrentHashMap<>();
    private final Map<UUID, BlockKey> recentCardTables = new ConcurrentHashMap<>();
    private final Map<UUID, SlotWager> selectedCardWagers = new ConcurrentHashMap<>();
    private final Map<UUID, BlockKey> pendingCardTables = new ConcurrentHashMap<>();
    private final Map<UUID, Inventory> playerLobbyInventories = new ConcurrentHashMap<>();
    private final Map<UUID, BlockKey> playerGameStations = new ConcurrentHashMap<>();
    private final Map<BlockKey, UUID> stationHolograms = new ConcurrentHashMap<>();
    private final Map<UUID, TavernLeaderboard> tavernLeaderboards = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> tavernLeaderboardDisplays = new ConcurrentHashMap<>();
    private final Set<String> unresolvedLeaderboardEntries = ConcurrentHashMap.newKeySet();
    private final Map<UUID, GameTimer> gameTimers = new ConcurrentHashMap<>();
    private final Set<UUID> sleepingIntoxicated = ConcurrentHashMap.newKeySet();
    private final Set<UUID> clearingIntoxication = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingMenuActions = ConcurrentHashMap.newKeySet();
    private final NamespacedKey actionKey;
    private final NamespacedKey drinkKey;
    private final NamespacedKey intoxicationKey;
    private final NamespacedKey morningDrinkKey;
    private final NamespacedKey gamblingDrinkKey;
    private final NamespacedKey gamblingFoodKey;
    private final NamespacedKey gamblingFoodChanceKey;
    private final NamespacedKey gamblingFoodExpiryKey;
    private final NamespacedKey gamblingDrinkExpiryKey;
    private final NamespacedKey gamblingDrinkSipsKey;
    private final NamespacedKey questKey;
    private final NamespacedKey questProgressKey;
    private final NamespacedKey rewardKey;
    private final NamespacedKey bountyDayKey;
    private final NamespacedKey bountyProgressKey;
    private final NamespacedKey bountyClaimedKey;
    private final NamespacedKey hologramKey;
    private final NamespacedKey leaderboardHologramKey;
    private BukkitTask intoxicationTask;
    private BukkitTask leaderboardTask;
    private BukkitTask leaderboardSaveTask;
    private final TavernBountyManager bountyManager;
    private final ItemEscrowService payoutEscrow;

    public TavernManager(SMPCore plugin) {
        this.plugin = plugin;
        this.bountyManager = new TavernBountyManager(plugin);
        this.payoutEscrow = new ItemEscrowService(plugin, "tavern_payout", "tavern-payouts.yml");
        this.stationsFile = new File(plugin.getDataFolder(), "tavern-stations.yml");
        this.leaderboardsFile = new File(plugin.getDataFolder(), "tavern-leaderboards.yml");
        this.actionKey = new NamespacedKey(plugin, "tavern_action");
        this.drinkKey = new NamespacedKey(plugin, "tavern_drink");
        this.intoxicationKey = new NamespacedKey(plugin, "tavern_intoxication");
        this.morningDrinkKey = new NamespacedKey(plugin, "tavern_morning_drink");
        this.gamblingDrinkKey = new NamespacedKey(plugin, "tavern_gambling_drink");
        this.gamblingFoodKey = new NamespacedKey(plugin, "tavern_gambling_food");
        this.gamblingFoodChanceKey = new NamespacedKey(plugin, "tavern_gambling_food_chance");
        this.gamblingFoodExpiryKey = new NamespacedKey(plugin, "tavern_gambling_food_expiry");
        this.gamblingDrinkExpiryKey = new NamespacedKey(plugin, "tavern_gambling_drink_expiry");
        this.gamblingDrinkSipsKey = new NamespacedKey(plugin, "tavern_gambling_drink_sips");
        this.questKey = new NamespacedKey(plugin, "tavern_quest");
        this.questProgressKey = new NamespacedKey(plugin, "tavern_quest_progress");
        this.rewardKey = new NamespacedKey(plugin, "tavern_reward");
        this.bountyDayKey = new NamespacedKey(plugin, "tavern_bounty_day");
        this.bountyProgressKey = new NamespacedKey(plugin, "tavern_bounty_progress");
        this.bountyClaimedKey = new NamespacedKey(plugin, "tavern_bounty_claimed");
        this.hologramKey = new NamespacedKey(plugin, "tavern_station_hologram");
        this.leaderboardHologramKey = new NamespacedKey(plugin, "tavern_leaderboard_hologram");
        for (StationType type : StationType.values()) stations.put(type, ConcurrentHashMap.newKeySet());
    }

    public void start() {
        payoutEscrow.start(Bukkit.getOnlinePlayers());
        Bukkit.getPluginManager().registerEvents(bountyManager, plugin);
        bountyManager.start();
        loadStations();
        loadLeaderboards();
        Bukkit.getScheduler().runTask(plugin, this::refreshAllStationHolograms);
        Bukkit.getScheduler().runTask(plugin, this::refreshAllTavernLeaderboards);
        intoxicationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<Location> brewmasters = plugin.getGuideNpcManager() == null
                ? List.of()
                : plugin.getGuideNpcManager().locations(GuideNpcType.BREWMASTER);
            for (Player player : Bukkit.getOnlinePlayers()) {
                reapplyIntoxication(player);
                maintainGamblingLuck(player, brewmasters);
                restoreTavernPayouts(player, false);
            }
        }, 20L, 40L);
        leaderboardTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAllTavernLeaderboards, 20L * 60L, 20L * 60L);
    }

    public void shutdown() {
        bountyManager.shutdown();
        if (intoxicationTask != null) intoxicationTask.cancel();
        if (leaderboardTask != null) leaderboardTask.cancel();
        if (leaderboardSaveTask != null) leaderboardSaveTask.cancel();
        leaderboardSaveTask = null;
        for (SlotSession session : new ArrayList<>(slotSessions.values())) refundSlot(session);
        for (DartSession session : dartSessions.values()) if (session.task != null) session.task.cancel();
        for (CardMatch match : new ArrayList<>(cardMatches.values())) cancelCardMatch(match);
        tableLobbies.clear();
        playerTableLobbies.clear();
        playerLobbyInventories.clear();
        slotSessions.clear();
        lastSlotWagers.clear();
        recentCardTables.clear();
        dartSessions.clear();
        cardMatches.clear();
        playerCardMatches.clear();
        for (UUID playerId : new ArrayList<>(gameTimers.keySet())) stopGameTimer(playerId, false);
        removeAllStationHolograms();
        removeAllTavernLeaderboardDisplays();
        saveStations();
        saveLeaderboardsNow();
        payoutEscrow.shutdown();
    }

    public boolean setStation(StationType type, Location location) {
        if (type == null || location == null || location.getWorld() == null) return false;
        boolean added = stations.get(type).add(BlockKey.of(location));
        if (added) {
            saveStations();
            refreshStationHologram(BlockKey.of(location));
        }
        return added;
    }

    public boolean removeStation(StationType type, Location location) {
        if (type == null || location == null || location.getWorld() == null) return false;
        boolean removed = stations.get(type).remove(BlockKey.of(location));
        if (removed) {
            BlockKey key = BlockKey.of(location);
            if (type == StationType.GAME_TABLE) {
                TableLobby lobby = tableLobbies.remove(key);
                if (lobby != null) {
                    for (UUID id : lobby.players) playerTableLobbies.remove(id, key);
                }
                UUID matchId = activeTableMatches.get(key);
                CardMatch match = matchId == null ? null : cardMatches.get(matchId);
                if (match != null) cancelCardMatch(match);
            }
            saveStations();
            removeStationHologram(key);
        }
        return removed;
    }

    public int stationCount(StationType type) {
        return type == null ? 0 : stations.get(type).size();
    }

    public UUID createLeaderboard(Location location, TavernGame game) {
        if (location == null || location.getWorld() == null || game == null) return null;
        for (TavernLeaderboard existing : new ArrayList<>(tavernLeaderboards.values())) {
            if (existing.game != game) continue;
            tavernLeaderboards.remove(existing.id);
            removeTavernLeaderboardDisplay(existing.id);
        }
        UUID id = UUID.randomUUID();
        tavernLeaderboards.put(id, new TavernLeaderboard(id, location.clone(), game));
        requestLeaderboardSave();
        refreshTavernLeaderboard(id);
        return id;
    }

    public boolean removeNearestLeaderboard(Location location, double maxDistance) {
        if (location == null || location.getWorld() == null) return false;
        TavernLeaderboard nearest = tavernLeaderboards.values().stream()
            .filter(board -> board.location.getWorld() != null && board.location.getWorld().equals(location.getWorld()))
            .filter(board -> board.location.distanceSquared(location) <= maxDistance * maxDistance)
            .min(java.util.Comparator.comparingDouble(board -> board.location.distanceSquared(location)))
            .orElse(null);
        if (nearest == null || tavernLeaderboards.remove(nearest.id) == null) return false;
        removeTavernLeaderboardDisplay(nearest.id);
        requestLeaderboardSave();
        return true;
    }

    public int leaderboardCount() {
        return tavernLeaderboards.size();
    }

    public void openActiveBounties(Player player) {
        if (player != null && player.isOnline()) {
            bountyManager.openActiveBounties(player);
        }
    }

    public boolean isStation(Location location) {
        return location != null && location.getWorld() != null && stationAt(BlockKey.of(location)) != null;
    }

    public void openNpc(Player player, GuideNpcType type) {
        if (type == GuideNpcType.BREWMASTER) {
            openBrewmasterMenu(player);
        } else if (type == GuideNpcType.CARDSHARP) {
            openAdventurerMenu(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStationInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        BlockKey key = BlockKey.of(event.getClickedBlock().getLocation());
        StationType type = stationAt(key);
        if (type == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        playerGameStations.put(player.getUniqueId(), key);
        switch (type) {
            case SLOT_MACHINE -> openSlotMenu(player);
            case GAME_TABLE -> openCardBetMenu(player, key);
            case DARTBOARD -> openDartsGame(player);
            case RUMOR_BOARD -> bountyManager.open(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof TavernMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.playerId.equals(player.getUniqueId())) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        String action = action(event.getCurrentItem());
        if (action == null) return;
        if (action.equals("dart:throw")) {
            attemptDartThrow(player);
            return;
        }
        if (!pendingMenuActions.add(player.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try { handleAction(player, action); }
            finally { pendingMenuActions.remove(player.getUniqueId()); }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof TavernMenuHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        DartSession darts = dartSessions.get(player.getUniqueId());
        if (darts != null && darts.inventory == event.getInventory()) cancelDarts(player.getUniqueId());
        Inventory lobbyInventory = playerLobbyInventories.get(player.getUniqueId());
        if (lobbyInventory == event.getInventory()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (playerLobbyInventories.remove(player.getUniqueId(), event.getInventory())) {
                    leaveTableLobby(player, false);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (StationType type : StationType.values()) {
                for (BlockKey key : stations.get(type)) {
                    if (key.worldId.equals(event.getWorld().getUID()) && (key.x >> 4) == event.getChunk().getX() && (key.z >> 4) == event.getChunk().getZ()) {
                        refreshStationHologram(key);
                    }
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrinkOrRewardUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) return;
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (meta.getPersistentDataContainer().has(gamblingDrinkKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
            sipGamblingDrink(event.getPlayer());
            return;
        }
        String drink = meta.getPersistentDataContainer().get(drinkKey, PersistentDataType.STRING);
        if (drink != null) {
            event.setCancelled(true);
            consumeDrink(event.getPlayer(), item, drink);
            return;
        }
        String reward = meta.getPersistentDataContainer().get(rewardKey, PersistentDataType.STRING);
        if (reward != null) {
            event.setCancelled(true);
            useQuestReward(event.getPlayer(), item, reward);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGamblingFoodConsume(PlayerItemConsumeEvent event) {
        ItemMeta meta = event.getItem().getItemMeta();
        if (meta == null) return;
        String foodId = meta.getPersistentDataContainer().get(gamblingFoodKey, PersistentDataType.STRING);
        GamblerFood food = GamblerFood.byId(foodId);
        if (food == null) return;
        PersistentDataContainer pdc = event.getPlayer().getPersistentDataContainer();
        pdc.set(gamblingFoodChanceKey, PersistentDataType.DOUBLE, food.chance);
        pdc.set(gamblingFoodExpiryKey, PersistentDataType.LONG, System.currentTimeMillis() + GAMBLING_LUCK_FOOD_MS);
        event.getPlayer().sendMessage(MessageUtil.success(food.displayName + " grants <white>" + percent(food.chance) + " Tavern Luck</white> for 30 minutes."));
        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.ENTITY_PLAYER_BURP, 0.6f, 1.35f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGamblingDrinkInventoryMove(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ItemStack hotbar = event.getHotbarButton() >= 0 && event.getWhoClicked() instanceof Player player
            ? player.getInventory().getItem(event.getHotbarButton())
            : null;
        if (!isGamblingDrink(current) && !isGamblingDrink(cursor) && !isGamblingDrink(hotbar)) return;
        boolean storageClick = event.getClickedInventory() != null && !(event.getClickedInventory() instanceof PlayerInventory);
        boolean shiftToStorage = event.isShiftClick() && event.getClickedInventory() instanceof PlayerInventory
            && !(event.getView().getTopInventory().getHolder(false) instanceof TavernMenuHolder);
        boolean bundleInsert = isBundle(current) && isGamblingDrink(cursor)
            || isGamblingDrink(current) && isBundle(cursor);
        if (storageClick || shiftToStorage || bundleInsert
            || event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) player.sendMessage(MessageUtil.warn("Bram's bottomless drink cannot be stored or shared."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGamblingDrinkEntityInteract(PlayerInteractEntityEvent event) {
        ItemStack held = event.getPlayer().getInventory().getItem(event.getHand());
        if (!isGamblingDrink(held)) return;
        event.setCancelled(true);
        sipGamblingDrink(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGamblingDrinkDrop(PlayerDropItemEvent event) {
        if (!isGamblingDrink(event.getItemDrop().getItemStack())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(MessageUtil.warn("Bram's bottomless drink cannot be dropped."));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGamblingDrinkDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isGamblingDrink);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (!event.enterAction().canSleep().success()) return;
        if (intoxicationLevel(event.getPlayer()) > 0 || temporaryGamblingNausea(event.getPlayer())) {
            sleepingIntoxicated.add(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNightSkip(TimeSkipEvent event) {
        if (event.getSkipReason() != TimeSkipEvent.SkipReason.NIGHT_SKIP) return;
        for (UUID playerId : new ArrayList<>(sleepingIntoxicated)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline() && player.getWorld().equals(event.getWorld())
                && sleepingIntoxicated.remove(playerId)) {
                clearIntoxication(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedLeave(PlayerBedLeaveEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (!sleepingIntoxicated.contains(playerId)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline() && player.isSleeping()) return;
            sleepingIntoxicated.remove(playerId);
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNauseaRemoved(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getOldEffect() == null || event.getOldEffect().getType() != PotionEffectType.NAUSEA || event.getNewEffect() != null) return;
        if (intoxicationLevel(player) <= 0 || clearingIntoxication.contains(player.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> reapplyIntoxication(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            reapplyIntoxication(event.getPlayer());
            restoreTavernPayouts(event.getPlayer(), true);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        sleepingIntoxicated.remove(id);
        pendingMenuActions.remove(id);
        playerLobbyInventories.remove(id);
        playerGameStations.remove(id);
        lastSlotWagers.remove(id);
        selectedSlotWagers.remove(id);
        recentCardTables.remove(id);
        selectedCardWagers.remove(id);
        pendingCardTables.remove(id);
        cancelDarts(id);
        SlotSession slot = slotSessions.get(id);
        if (slot != null) refundSlot(slot);
        removePlayerFromCards(id);
        BlockKey lobbyKey = playerTableLobbies.remove(id);
        TableLobby lobby = lobbyKey == null ? null : tableLobbies.get(lobbyKey);
        if (lobby != null) {
            synchronized (lobby) {
                lobby.players.remove(id);
                if (lobby.players.isEmpty()) tableLobbies.remove(lobbyKey, lobby);
                else {
                    if (id.equals(lobby.hostId)) lobby.hostId = lobby.players.iterator().next();
                    renderTableLobby(lobby);
                }
            }
            updateStationHologram(lobbyKey);
        }
        stopGameTimer(id, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBountyKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !isHostile(event.getEntityType())) return;
        bountyManager.recordHostileKill(killer);
    }

    private void openBrewmasterMenu(Player player) {
        Inventory inv = menu(player, "Bram the Brewmaster", "Brewmaster");
        inv.setItem(10, button(Material.BREWING_STAND, "<gold><bold>Buy Drinks</bold></gold>", List.of("<gray>Browse 13 drinks from mild to premium.</gray>", "<yellow>Click to open the bar.</yellow>"), "npc:drinks"));
        inv.setItem(13, button(Material.WRITABLE_BOOK, "<yellow><bold>Bram's Quest</bold></yellow>", brewQuestLore(player), "npc:brewquest"));
        inv.setItem(16, button(Material.GOLDEN_CARROT, "<green><bold>Gambler's Fare</bold></green>", List.of("<gray>A bottomless drink and four luck foods.</gray>", "<yellow>Click for small, timed game advantages.</yellow>"), "npc:luckshop"));
        player.openInventory(inv);
    }

    private void openGamblingLuckMenu(Player player) {
        Inventory inv = menu(player, "Bram's Gambler Fare", "Gambler Fare");
        inv.setItem(10, button(Material.POTION, "<aqua><bold>Bottomless Lucky Draught</bold></aqua>", List.of(
            "<gray>Cost: <white>15 Essence</white></gray>",
            "<green>3% bonus-draw chance for 30 seconds.</green>",
            "<gray>Right-click to sip and refresh it forever.</gray>",
            "<red>Each sip increases 30-second nausea.</red>",
            "<dark_gray>Vanishes beyond 100 blocks from Bram.</dark_gray>"
        ), "luck:drink"));
        int[] slots = {12, 13, 14, 15};
        GamblerFood[] foods = GamblerFood.values();
        for (int i = 0; i < foods.length; i++) {
            GamblerFood food = foods[i];
            inv.setItem(slots[i], button(food.material, "<" + food.color + "><bold>" + food.displayName + "</bold></" + food.color + ">", List.of(
                "<gray>Cost: <white>" + food.cost + " Essence</white></gray>",
                "<green>" + percent(food.chance) + " Tavern Luck for 30 minutes.</green>",
                "<dark_gray>Strongest food replaces the previous food.</dark_gray>"
            ), "luck:food:" + food.id));
        }
        inv.setItem(22, button(Material.ENCHANTED_BOOK, "<yellow><bold>HOW LUCK WORKS</bold></yellow>", List.of(
            "<gray>Luck can grant one safer bonus roll in slots</gray>",
            "<gray>or one extra card draw, keeping the better card.</gray>",
            "<gray>Food and drink combine, capped at <white>10%</white>.</gray>"
        ), null));
        inv.setItem(26, button(Material.ARROW, "<yellow>Back</yellow>", List.of(), "npc:brewmaster"));
        player.openInventory(inv);
    }

    private void openDrinkMenu(Player player) {
        Inventory inv = menu(player, "Bram's Bar", "Bram's Bar");
        int[] slots = {9,10,11,12,13,14,15,16,17,19,20,21,22};
        Drink[] drinks = Drink.values();
        for (int i = 0; i < drinks.length; i++) {
            Drink drink = drinks[i];
            inv.setItem(slots[i], button(drink.material, "<" + drink.color + ">" + drink.displayName + "</" + drink.color + ">", List.of(
                "<gray>Tier " + (i + 1) + "/" + drinks.length + " · " + drink.cost + " Essence</gray>",
                "<gray>Intoxication " + (drink.amplifier + 1) + "</gray>",
                "<green>After sleep: " + drink.buffDescription + "</green>"
            ), "drink:" + drink.id));
        }
        inv.setItem(26, button(Material.ARROW, "<yellow>Back</yellow>", List.of(), "npc:brewmaster"));
        player.openInventory(inv);
    }

    private void openAdventurerMenu(Player player) {
        Inventory inv = menu(player, "Rook's Tavern Trial", "Adventurer");
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        int mask = pdc.getOrDefault(questProgressKey, PersistentDataType.INTEGER, 0);
        boolean started = pdc.has(questKey("cards_started"), PersistentDataType.BYTE);
        boolean done = pdc.has(questKey("cards_done"), PersistentDataType.BYTE);
        inv.setItem(10, progressItem(Material.NETHER_STAR, "Slot Victory", mask, 0));
        inv.setItem(13, progressItem(Material.PAPER, "Card Table Victory", mask, 1));
        inv.setItem(16, progressItem(Material.TARGET, "Dart Bullseye", mask, 2));
        String action = done ? "close" : "npc:adventurequest";
        String status = done ? "<green>Quest complete.</green>" : !started ? "<yellow>Click to begin Rook's trial.</yellow>" : mask == 7 ? "<green>Click to claim the Quiet House Coin.</green>" : "<gray>Complete all three tavern challenges.</gray>";
        inv.setItem(22, button(done ? Material.SUNFLOWER : Material.WRITABLE_BOOK, "<gold><bold>Rook's Challenge</bold></gold>", List.of(status, "<gray>Reward: Quiet House Coin</gray>", "<gray>Luck II for 15 minutes, once per day.</gray>"), action));
        player.openInventory(inv);
    }

    private ItemStack progressItem(Material material, String name, int mask, int bit) {
        boolean complete = (mask & (1 << bit)) != 0;
        return button(material, (complete ? "<green>" : "<gray>") + name + (complete ? "</green>" : "</gray>"), List.of(complete ? "<green>Complete</green>" : "<yellow>Not complete</yellow>"), "close");
    }

    private List<String> brewQuestLore(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc.has(questKey("brew_done"), PersistentDataType.BYTE)) return List.of("<green>Completed.</green>", "<gray>Reward: Cellarmaster's Canteen</gray>");
        boolean started = pdc.has(questKey("brew_started"), PersistentDataType.BYTE);
        return List.of(
            started ? "<gray>Wheat: <white>" + plainCount(player, Material.WHEAT) + "/16</white></gray>" : "<gray>Help Bram perfect a restorative brew.</gray>",
            started ? "<gray>Sweet Berries: <white>" + plainCount(player, Material.SWEET_BERRIES) + "/8</white></gray>" : "<yellow>Click to begin.</yellow>",
            started ? "<gray>Honey Bottles: <white>" + plainCount(player, Material.HONEY_BOTTLE) + "/4</white></gray>" : "<gray>Reward: Cellarmaster's Canteen</gray>",
            started ? "<yellow>Click to turn in when ready.</yellow>" : "<gray>Speed I and Haste I for 3 minutes.</gray>"
        );
    }

    private void openSlotMenu(Player player) {
        if (isTavernGameBusy(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("Finish your current tavern game first."));
            return;
        }
        SlotWager wager = selectedSlotWagers.computeIfAbsent(player.getUniqueId(), ignored -> new SlotWager("essence", "1"));
        int amount = Integer.parseInt(wager.amount);
        Inventory inv = Bukkit.createInventory(new TavernMenuHolder(player.getUniqueId()), 45,
            BedrockCompat.menuTitle(player, MM.deserialize("<gold><bold>Tavern Slots</bold></gold>"), "Tavern Slots"));
        ItemStack filler = button(Material.BLACK_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, List.of(), null);
        for (int slot = 0; slot < inv.getSize(); slot++) inv.setItem(slot, filler);
        inv.setItem(4, button(Material.NETHER_STAR, "<gold><bold>CURRENT WAGER</bold></gold>", List.of("<white>" + amount + " " + prettyMaterialName(wager.currency) + "</white>", "<gray>Choose currency, adjust amount, then spin.</gray>"), null));
        inv.setItem(10, slotCurrencyButton(Material.AMETHYST_SHARD, "Essence", "essence", wager.currency));
        inv.setItem(12, slotCurrencyButton(Material.IRON_INGOT, "Iron Ingots", "iron", wager.currency));
        inv.setItem(14, slotCurrencyButton(Material.GOLD_INGOT, "Gold Ingots", "gold", wager.currency));
        inv.setItem(16, slotCurrencyButton(Material.DIAMOND, "Diamonds", "diamond", wager.currency));
        inv.setItem(28, button(Material.RED_DYE, "<red><bold>-8</bold></red>", List.of("<gray>Minimum wager: 1</gray>"), "slot:adjust:-8"));
        inv.setItem(29, button(Material.RED_DYE, "<red><bold>-1</bold></red>", List.of("<gray>Lower the wager.</gray>"), "slot:adjust:-1"));
        inv.setItem(31, button(Material.LIME_DYE, "<green><bold>+1</bold></green>", List.of("<gray>Raise the wager.</gray>"), "slot:adjust:1"));
        inv.setItem(32, button(Material.LIME_DYE, "<green><bold>+8</bold></green>", List.of("<gray>Raise quickly.</gray>"), "slot:adjust:8"));
        inv.setItem(33, button(Material.EMERALD_BLOCK, "<green><bold>MAX 64</bold></green>", List.of("<gray>Set the wager to one full stack.</gray>"), "slot:adjust:64"));
        inv.setItem(22, button(Material.NETHER_STAR, "<gold><bold>SPIN " + amount + " " + prettyMaterialName(wager.currency) + "</bold></gold>", List.of("<yellow>Click to start.</yellow>", "<dark_gray>The wager is charged exactly once.</dark_gray>"), "slot:start"));
        inv.setItem(38, button(Material.ENCHANTED_BOOK, "<gold><bold>RARE PAYOUTS</bold></gold>", List.of(
            "<gold>0.1% · Crown · 25x</gold>", "<green>0.4% · Emerald · 15x</green>",
            "<aqua>1.5% · Diamond · 8x</aqua>", "<yellow>3% · Gold · 4x</yellow>"), null));
        inv.setItem(40, button(Material.BOOK, "<yellow><bold>COMMON OUTCOMES</bold></yellow>", List.of(
            "<light_purple>7% · Amethyst · 3x</light_purple>", "<gray>88% · No payout</gray>",
            "<red>Pushes and 1x results are losses.</red>", "<dark_gray>Base: 12% hit · 53.5% RTP</dark_gray>"), null));
        inv.setItem(36, button(Material.RABBIT_FOOT, "<green><bold>TAVERN LUCK: " + percent(gamblingLuck(player)) + "</bold></green>", List.of(
            "<gray>Effective slot hit: <white>" + percent(slotHitRate(gamblingLuck(player))) + "</white></gray>",
            "<gray>Chance to receive one safer bonus roll.</gray>", "<dark_gray>Buy short buffs from Bram.</dark_gray>"), null));
        inv.setItem(44, button(Material.BARRIER, "<red>Close</red>", List.of(), "close"));
        player.openInventory(inv);
    }

    private ItemStack slotCurrencyButton(Material material, String label, String id, String selected) {
        boolean active = id.equals(selected);
        return button(material, active ? "<green><bold>" + label + " ✓</bold></green>" : "<white>" + label + "</white>",
            List.of(active ? "<green>Currently selected</green>" : "<yellow>Click to select</yellow>"), "slot:currency:" + id);
    }

    private List<String> slotLore() {
        return List.of("<gray>Click to spin.</gray>", "<dark_gray>Wagers are taken before the roll.</dark_gray>");
    }

    private Inventory menu(Player player, String title, String bedrockTitle) {
        return Bukkit.createInventory(new TavernMenuHolder(player.getUniqueId()), MENU_SIZE,
            BedrockCompat.menuTitle(player, MM.deserialize("<gold><bold>" + title + "</bold></gold>"), bedrockTitle));
    }

    private void handleAction(Player player, String action) {
        String[] parts = action.split(":");
        if (action.equals("close")) player.closeInventory();
        else if (action.equals("npc:brewmaster")) openBrewmasterMenu(player);
        else if (action.equals("npc:drinks")) openDrinkMenu(player);
        else if (action.equals("npc:luckshop")) openGamblingLuckMenu(player);
        else if (action.equals("npc:brewquest")) { handleBrewmasterQuest(player); openBrewmasterMenu(player); }
        else if (action.equals("npc:adventurequest")) { handleCardsharpQuest(player); openAdventurerMenu(player); }
        else if (action.equals("rumor:accept")) { acceptRumor(player); openRumorMenu(player); }
        else if (action.equals("dart:repeat")) openDartsGame(player);
        else if (action.equals("table:leave")) leaveTableLobby(player);
        else if (action.equals("table:start")) startTableFromLobby(player);
        else if (action.equals("card:again")) rejoinCardTable(player);
        else if (action.equals("card:bet:join")) joinSelectedCardTable(player);
        else if (action.equals("slot:repeat")) repeatSlot(player);
        else if (action.equals("slot:start")) startSelectedSlot(player);
        else if (action.equals("luck:drink")) buyGamblingDrink(player);
        else if (parts[0].equals("luck") && parts.length == 3 && parts[1].equals("food")) buyGamblingFood(player, parts[2]);
        else if (parts[0].equals("drink") && parts.length == 2) { player.closeInventory(); buyDrink(player, parts[1]); }
        else if (parts[0].equals("slot") && parts.length == 3 && parts[1].equals("currency")) selectSlotCurrency(player, parts[2]);
        else if (parts[0].equals("slot") && parts.length == 3 && parts[1].equals("adjust")) adjustSlotWager(player, parts[2]);
        else if (parts[0].equals("card") && parts.length == 4 && parts[1].equals("select")) selectCard(player, parts[2], parts[3]);
        else if (parts[0].equals("card") && parts.length == 3 && parts[1].equals("leave")) leaveCardMatch(player, parts[2]);
        else if (parts[0].equals("card") && parts.length == 3 && parts[1].equals("currency")) selectCardCurrency(player, parts[2]);
        else if (parts[0].equals("card") && parts.length == 3 && parts[1].equals("adjust")) adjustCardWager(player, parts[2]);
    }

    private void buyDrink(Player player, String id) {
        Drink drink = Drink.byId(id);
        if (drink == null) return;
        ItemStack item = drinkItem(drink);
        if (!canFitOne(player, item)) {
            player.sendMessage(MessageUtil.warn("Make room for this drink before buying it."));
            return;
        }
        if (!spend(player, drink.cost, "tavern_drink_" + id)) {
            player.sendMessage(MessageUtil.warn("You need <white>" + drink.cost + " Essence</white>."));
            return;
        }
        InventoryRecipeUtil.giveOrDrop(player, item);
        recordAcquisition(player, item, "tavern_drink", "Bought " + drink.displayName + ".");
        player.sendMessage(MessageUtil.success("Bought <white>" + drink.displayName + "</white>."));
    }

    private ItemStack drinkItem(Drink drink) {
        ItemStack item = new ItemStack(drink.material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<" + drink.color + ">" + drink.displayName + "</" + drink.color + ">"));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            drink.material,
            "COMMON",
            "DRINK",
            List.of("<gray>A cheap tavern drink with a strong kick.</gray>"),
            List.of(
                CustomLoreUtil.section("Use", "Intoxicate",
                    "<gray><white>Right-click</white> to drink.</gray>",
                    "<red>Only a full night's sleep sobers you.</red>"),
                CustomLoreUtil.section("Morning", "Wake-Up Bonus",
                    "<green>" + drink.buffDescription + "</green>",
                    "<dark_gray>Your latest drink sets this bonus.</dark_gray>")
            )
        ));
        meta.getPersistentDataContainer().set(drinkKey, PersistentDataType.STRING, drink.id);
        meta.setMaxStackSize(16);
        item.setItemMeta(meta);
        return item;
    }

    private void consumeDrink(Player player, ItemStack item, String id) {
        Drink drink = Drink.byId(id);
        if (drink == null) return;
        if (item.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
        else item.setAmount(item.getAmount() - 1);
        int amplifier = Math.max(intoxicationLevel(player), drink.amplifier);
        player.getPersistentDataContainer().set(intoxicationKey, PersistentDataType.INTEGER, amplifier);
        player.getPersistentDataContainer().set(morningDrinkKey, PersistentDataType.STRING, drink.id);
        applyIntoxication(player, amplifier);
        player.sendMessage(MessageUtil.warn("You are intoxicated. Complete a night's sleep to recover and gain <white>" + drink.buffDescription + "</white>."));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1.0f, 0.7f);
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 1, 0), 22, 0.4, 0.5, 0.4, 0.02);
    }

    private void buyGamblingDrink(Player player) {
        if (hasGamblingDrink(player)) {
            player.sendMessage(MessageUtil.warn("You already have Bram's Bottomless Lucky Draught."));
            return;
        }
        ItemStack item = gamblingDrinkItem();
        if (!canFitOne(player, item)) {
            player.sendMessage(MessageUtil.warn("Make room for the draught first."));
            return;
        }
        if (!spend(player, 15, "tavern_luck_drink")) {
            player.sendMessage(MessageUtil.warn("You need <white>15 Essence</white>."));
            return;
        }
        InventoryRecipeUtil.giveOrDrop(player, item);
        recordAcquisition(player, item, "tavern_luck_drink", "Bought Bottomless Lucky Draught.");
        player.sendMessage(MessageUtil.success("Bought Bram's <white>Bottomless Lucky Draught</white>. Right-click to sip."));
        openGamblingLuckMenu(player);
    }

    private void buyGamblingFood(Player player, String id) {
        GamblerFood food = GamblerFood.byId(id);
        if (food == null) return;
        ItemStack item = gamblingFoodItem(food);
        if (!canFitOne(player, item)) {
            player.sendMessage(MessageUtil.warn("Make room for the food first."));
            return;
        }
        if (!spend(player, food.cost, "tavern_luck_food_" + food.id)) {
            player.sendMessage(MessageUtil.warn("You need <white>" + food.cost + " Essence</white>."));
            return;
        }
        InventoryRecipeUtil.giveOrDrop(player, item);
        recordAcquisition(player, item, "tavern_luck_food", "Bought " + food.displayName + ".");
        player.sendMessage(MessageUtil.success("Bought <white>" + food.displayName + "</white>."));
        openGamblingLuckMenu(player);
    }

    private ItemStack gamblingDrinkItem() {
        ItemStack item = new ItemStack(Material.POTION);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<aqua><bold>Bottomless Lucky Draught</bold></aqua>"));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.POTION,
            "RARE",
            "TAVERN DRINK",
            List.of("<gray>A bottomless sip of risky fortune.</gray>"),
            List.of(
                CustomLoreUtil.section("Use", "Liquid Luck",
                    "<gray><white>Right-click</white> for <green>+3% Tavern Luck</green> for 30s.</gray>",
                    "<gray>Sipping refreshes it but raises Nausea.</gray>"),
                CustomLoreUtil.section("Limit", "Bram's Pour",
                    "<dark_gray>Vanishes more than 100 blocks from Bram.</dark_gray>")
            )
        ));
        meta.getPersistentDataContainer().set(gamblingDrinkKey, PersistentDataType.BYTE, (byte) 1);
        meta.setMaxStackSize(1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack gamblingFoodItem(GamblerFood food) {
        ItemStack item = new ItemStack(food.material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<" + food.color + "><bold>" + food.displayName + "</bold></" + food.color + ">"));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            food.material,
            "UNCOMMON",
            "TAVERN FOOD",
            List.of("<gray>Food prepared for a long gambling session.</gray>"),
            List.of(CustomLoreUtil.section("Eat", "Fortunate Meal",
                "<gray>Grants <green>+" + percent(food.chance) + " Tavern Luck</green> for 30m.</gray>",
                "<dark_gray>Total Tavern Luck cannot exceed 10%.</dark_gray>"))
        ));
        meta.getPersistentDataContainer().set(gamblingFoodKey, PersistentDataType.STRING, food.id);
        meta.setMaxStackSize(16);
        item.setItemMeta(meta);
        return item;
    }

    private void sipGamblingDrink(Player player) {
        if (!isNearBrewmaster(player, plugin.getGuideNpcManager() == null ? List.of() : plugin.getGuideNpcManager().locations(GuideNpcType.BREWMASTER))) {
            removeGamblingDrinks(player);
            player.sendMessage(MessageUtil.warn("The draught dries up this far from Bram."));
            return;
        }
        long now = System.currentTimeMillis();
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        long oldExpiry = pdc.getOrDefault(gamblingDrinkExpiryKey, PersistentDataType.LONG, 0L);
        int oldSips = oldExpiry > now ? pdc.getOrDefault(gamblingDrinkSipsKey, PersistentDataType.INTEGER, 0) : 0;
        int sips = Math.min(5, oldSips + 1);
        pdc.set(gamblingDrinkExpiryKey, PersistentDataType.LONG, now + GAMBLING_LUCK_DRINK_MS);
        pdc.set(gamblingDrinkSipsKey, PersistentDataType.INTEGER, sips);
        int nausea = Math.min(4, sips - 1);
        PotionEffect current = player.getPotionEffect(PotionEffectType.NAUSEA);
        if (current == null || current.getAmplifier() <= nausea || current.getDuration() < 600) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20 * 30, nausea, false, false, true));
        }
        player.sendActionBar(MM.deserialize("<aqua>Lucky Draught refreshed:</aqua> <white>3% for 30s</white> <dark_gray>· Nausea " + (nausea + 1) + "</dark_gray>"));
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 0.65f, 1.15f);
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 1, 0), 10, 0.25, 0.3, 0.25, 0.01);
    }

    private void applyIntoxication(Player player, int amplifier) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, PotionEffect.INFINITE_DURATION, amplifier, false, false, true));
    }

    private void reapplyIntoxication(Player player) {
        int level = intoxicationLevel(player);
        if (level <= 0 || !player.isOnline()) return;
        PotionEffect current = player.getPotionEffect(PotionEffectType.NAUSEA);
        if (current == null || current.getAmplifier() != level || current.getDuration() < 1200) applyIntoxication(player, level);
    }

    private int intoxicationLevel(Player player) {
        return Math.max(0, player.getPersistentDataContainer().getOrDefault(intoxicationKey, PersistentDataType.INTEGER, 0));
    }

    private void clearIntoxication(Player player) {
        clearingIntoxication.add(player.getUniqueId());
        String morningDrink = player.getPersistentDataContainer().get(morningDrinkKey, PersistentDataType.STRING);
        player.getPersistentDataContainer().remove(intoxicationKey);
        player.getPersistentDataContainer().remove(morningDrinkKey);
        player.getPersistentDataContainer().remove(gamblingDrinkSipsKey);
        player.getPersistentDataContainer().remove(gamblingDrinkExpiryKey);
        player.removePotionEffect(PotionEffectType.NAUSEA);
        Drink drink = Drink.byId(morningDrink);
        if (drink != null) {
            player.addPotionEffect(new PotionEffect(drink.buffType, drink.buffTicks, drink.buffAmplifier, false, true, true));
            player.sendMessage(MessageUtil.success("You wake refreshed with <white>" + drink.buffDescription + "</white>."));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.75f, 1.35f);
        }
        Bukkit.getScheduler().runTask(plugin, () -> clearingIntoxication.remove(player.getUniqueId()));
        player.sendMessage(MessageUtil.success("A full night's rest clears your intoxication."));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
    }

    public boolean clearIntoxicationByAdmin(Player player) {
        if (player == null) return false;
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        boolean changed = intoxicationLevel(player) > 0
            || temporaryGamblingNausea(player)
            || player.hasPotionEffect(PotionEffectType.NAUSEA);
        UUID playerId = player.getUniqueId();
        clearingIntoxication.add(playerId);
        sleepingIntoxicated.remove(playerId);
        pdc.remove(intoxicationKey);
        pdc.remove(morningDrinkKey);
        pdc.remove(gamblingDrinkSipsKey);
        pdc.remove(gamblingDrinkExpiryKey);
        player.removePotionEffect(PotionEffectType.NAUSEA);
        Bukkit.getScheduler().runTask(plugin, () -> clearingIntoxication.remove(playerId));
        return changed;
    }

    private void maintainGamblingLuck(Player player, List<Location> brewmasters) {
        long now = System.currentTimeMillis();
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc.getOrDefault(gamblingFoodExpiryKey, PersistentDataType.LONG, 0L) <= now) {
            pdc.remove(gamblingFoodExpiryKey);
            pdc.remove(gamblingFoodChanceKey);
        }
        if (pdc.getOrDefault(gamblingDrinkExpiryKey, PersistentDataType.LONG, 0L) <= now) {
            pdc.remove(gamblingDrinkExpiryKey);
            pdc.remove(gamblingDrinkSipsKey);
        }
        if (hasGamblingDrink(player) && !isNearBrewmaster(player, brewmasters)) {
            removeGamblingDrinks(player);
            pdc.remove(gamblingDrinkExpiryKey);
            pdc.remove(gamblingDrinkSipsKey);
            player.sendMessage(MessageUtil.info("Bram's Bottomless Lucky Draught vanished when you left the tavern area."));
        }
    }

    private boolean temporaryGamblingNausea(Player player) {
        return player.getPersistentDataContainer().getOrDefault(gamblingDrinkSipsKey, PersistentDataType.INTEGER, 0) > 0;
    }

    public double gamblingLuck(Player player) {
        long now = System.currentTimeMillis();
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        double food = pdc.getOrDefault(gamblingFoodExpiryKey, PersistentDataType.LONG, 0L) > now
            ? Math.max(0.0D, pdc.getOrDefault(gamblingFoodChanceKey, PersistentDataType.DOUBLE, 0.0D))
            : 0.0D;
        double drink = pdc.getOrDefault(gamblingDrinkExpiryKey, PersistentDataType.LONG, 0L) > now
            ? GAMBLING_LUCK_DRINK_CHANCE
            : 0.0D;
        return Math.min(MAX_GAMBLING_LUCK, food + drink);
    }

    private boolean hasGamblingDrink(Player player) {
        for (ItemStack item : player.getInventory().getContents()) if (isGamblingDrink(item)) return true;
        return false;
    }

    private boolean isGamblingDrink(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(gamblingDrinkKey, PersistentDataType.BYTE);
    }

    private boolean isBundle(ItemStack item) {
        return item != null && item.getItemMeta() instanceof BundleMeta;
    }

    private void removeGamblingDrinks(Player player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isGamblingDrink(inventory.getItem(slot))) inventory.setItem(slot, null);
        }
    }

    private boolean isNearBrewmaster(Player player, List<Location> brewmasters) {
        for (Location location : brewmasters) {
            if (location.getWorld() != null && location.getWorld().equals(player.getWorld())
                && location.distanceSquared(player.getLocation()) <= BREWMASTER_DRINK_RANGE_SQUARED) return true;
        }
        return false;
    }

    private String percent(double chance) {
        double value = chance * 100.0D;
        return value % 1.0D == 0.0D
            ? String.format(Locale.US, "%.0f%%", value)
            : String.format(Locale.US, "%.1f%%", value);
    }

    private void startSlotAnimation(Player player, String currency, String amountRaw) {
        if (isTavernGameBusy(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("Finish your current tavern game first."));
            return;
        }
        Material material = null;
        List<EscrowedItem> materialEscrows = List.of();
        int wager;
        if (currency.equals("essence")) {
            wager = Integer.parseInt(amountRaw);
            long maximumProfit = 24L * wager;
            if (plugin.getEssenceManager() == null || !plugin.getEssenceManager().isLoaded(player)
                || !plugin.getEssenceManager().canCreditFully(player, maximumProfit)) {
                player.sendMessage(MessageUtil.warn("Make room below the Essence cap before spinning."));
                return;
            }
            if (!spend(player, wager, "tavern_slots")) {
                player.sendMessage(MessageUtil.warn("You need <white>" + wager + " Essence</white>."));
                return;
            }
        } else {
            material = switch (currency) { case "iron" -> Material.IRON_INGOT; case "gold" -> Material.GOLD_INGOT; case "diamond" -> Material.DIAMOND; default -> null; };
            wager = Integer.parseInt(amountRaw);
            materialEscrows = material == null ? null : capturePlainWager(player, material, wager, UUID.randomUUID());
            if (materialEscrows == null) {
                player.sendMessage(MessageUtil.warn("You need <white>" + wager + " plain " + prettyMaterialName(currency) + "</white>."));
                return;
            }
        }
        int primaryRoll = ThreadLocalRandom.current().nextInt(10_000);
        int bonusRoll = ThreadLocalRandom.current().nextInt(10_000);
        double luck = gamblingLuck(player);
        double luckTrigger = ThreadLocalRandom.current().nextDouble();
        boolean luckTriggered = luckTrigger < luck;
        int multiplier = slotMultiplier(luckySlotRoll(primaryRoll, bonusRoll, luckTrigger, luck));
        Inventory inv = Bukkit.createInventory(new TavernMenuHolder(player.getUniqueId()), 27,
            BedrockCompat.menuTitle(player, MM.deserialize("<gold><bold>Spinning...</bold></gold>"), "Spinning Slots"));
        SlotSession session = new SlotSession(player.getUniqueId(), inv, material, wager, multiplier, materialEscrows);
        slotSessions.put(player.getUniqueId(), session);
        lastSlotWagers.put(player.getUniqueId(), new SlotWager(currency, amountRaw));
        startGameTimer(player, TavernGame.SLOTS);
        decorateSlotMachine(inv);
        player.openInventory(inv);
        session.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickSlot(session), 0L, 2L);
        if (luckTriggered && bonusRoll < primaryRoll) {
            player.sendActionBar(MM.deserialize("<green>Tavern Luck granted a safer bonus roll.</green>"));
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, 1.8f);
        }
    }

    private void startSelectedSlot(Player player) {
        SlotWager wager = selectedSlotWagers.computeIfAbsent(player.getUniqueId(), ignored -> new SlotWager("essence", "1"));
        startSlotAnimation(player, wager.currency, wager.amount);
    }

    private void selectSlotCurrency(Player player, String currency) {
        if (!List.of("essence", "iron", "gold", "diamond").contains(currency)) return;
        SlotWager current = selectedSlotWagers.getOrDefault(player.getUniqueId(), new SlotWager("essence", "1"));
        selectedSlotWagers.put(player.getUniqueId(), new SlotWager(currency, current.amount));
        openSlotMenu(player);
    }

    private void adjustSlotWager(Player player, String deltaRaw) {
        int delta;
        try { delta = Integer.parseInt(deltaRaw); } catch (NumberFormatException ex) { return; }
        SlotWager current = selectedSlotWagers.getOrDefault(player.getUniqueId(), new SlotWager("essence", "1"));
        int amount = Math.max(1, Math.min(64, Integer.parseInt(current.amount) + delta));
        selectedSlotWagers.put(player.getUniqueId(), new SlotWager(current.currency, Integer.toString(amount)));
        openSlotMenu(player);
    }

    private String prettyMaterialName(String currency) {
        return switch (currency) { case "essence" -> "Essence"; case "iron" -> "Iron Ingots"; case "gold" -> "Gold Ingots"; case "diamond" -> "Diamonds"; default -> currency; };
    }

    private void announceGameResult(BlockKey station, String message, Sound sound, float pitch, Set<UUID> soundExclusions) {
        Location location = station == null ? null : station.location();
        if (location == null || location.getWorld() == null) return;
        Component component = MM.deserialize(message);
        for (Player nearby : location.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(location) > 256.0D) continue;
            BedrockCompat.sendGameMessage(nearby, component);
            if (!soundExclusions.contains(nearby.getUniqueId())) nearby.playSound(location, sound, 0.7f, pitch);
        }
    }

    private void playResultSound(Player player, Sound sound, float volume, float pitch) {
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player online = Bukkit.getPlayer(playerId);
            if (online != null && online.isOnline()) online.playSound(online.getLocation(), sound, volume, pitch);
        }, 4L);
    }

    private void repeatSlot(Player player) {
        if (slotSessions.containsKey(player.getUniqueId())) return;
        SlotWager wager = lastSlotWagers.get(player.getUniqueId());
        if (wager == null) {
            openSlotMenu(player);
            return;
        }
        startSlotAnimation(player, wager.currency, wager.amount);
    }

    private void decorateSlotMachine(Inventory inventory) {
        ItemStack frame = button(Material.YELLOW_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, List.of(), null);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot < 9 || slot >= 18 || slot == 9 || slot == 17) inventory.setItem(slot, frame);
        }
        inventory.setItem(4, button(Material.NETHER_STAR, "<gold><bold>CROWN REELS</bold></gold>", List.of("<gray>Three reels stop one at a time.</gray>"), null));
    }

    private void tickSlot(SlotSession session) {
        Player player = Bukkit.getPlayer(session.playerId);
        if (player == null || !player.isOnline()) { refundSlot(session); return; }
        session.step++;
        for (int reel = 0; reel < SLOT_REELS.length; reel++) {
            Material symbol = slotReelStopped(session.step, reel)
                ? finalSlotSymbol(session, reel)
                : SLOT_SYMBOLS.get(ThreadLocalRandom.current().nextInt(SLOT_SYMBOLS.size()));
            session.inventory.setItem(SLOT_REELS[reel], slotSymbol(symbol));
        }
        int stopped = session.step >= 30 ? 3 : session.step >= 24 ? 2 : session.step >= 18 ? 1 : 0;
        session.inventory.setItem(22, button(stopped == 0 ? Material.CLOCK : Material.GOLD_INGOT,
            "<yellow><bold>" + (stopped == 0 ? "SPINNING" : stopped + "/3 REELS LOCKED") + "</bold></yellow>",
            List.of("<gray>Watch each reel snap into place.</gray>"), null));
        player.playSound(player.getLocation(), stopped > session.lastStopped ? Sound.BLOCK_NOTE_BLOCK_CHIME : Sound.BLOCK_NOTE_BLOCK_HAT,
            stopped > session.lastStopped ? 0.9f : 0.35f, stopped > session.lastStopped ? 1.1f + stopped * 0.2f : 0.8f + session.step * 0.018f);
        if (stopped > session.lastStopped) {
            player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 8, 0.25, 0.3, 0.25, 0.01);
            session.lastStopped = stopped;
        }
        if (session.step < 30) return;
        finishSlot(session);
    }

    static boolean slotReelStopped(int step, int reel) {
        if (reel < 0 || reel > 2) throw new IllegalArgumentException("reel must be 0, 1, or 2");
        return step >= 18 + reel * 6;
    }

    private Material finalSlotSymbol(SlotSession session, int reel) {
        Material winSymbol = switch (session.multiplier) {
            case 25 -> Material.NETHER_STAR;
            case 15 -> Material.EMERALD;
            case 8 -> Material.DIAMOND;
            case 4 -> Material.GOLD_INGOT;
            case 3 -> Material.AMETHYST_SHARD;
            default -> null;
        };
        if (winSymbol != null) return winSymbol;
        return SLOT_SYMBOLS.get((session.lossOffset + reel * 2) % SLOT_SYMBOLS.size());
    }

    private ItemStack slotSymbol(Material material) {
        return button(material, "<white><bold>" + prettyMaterial(material) + "</bold></white>", List.of(), null);
    }

    private void finishSlot(SlotSession session) {
        if (!slotSessions.remove(session.playerId, session)) return;
        if (session.task != null) session.task.cancel();
        Player player = Bukkit.getPlayer(session.playerId);
        if (player == null) { stopGameTimer(session.playerId, false); return; }
        if (session.multiplier > 1) {
            boolean paid = session.material == null
                ? credit(player, (long) session.wager * session.multiplier, "tavern_slots_win")
                : settlePlainEscrows(player, session.materialEscrows, session.material,
                    session.wager * session.multiplier, "SLOTS_WIN");
            if (!paid) {
                refundSlotWager(session, player);
                session.inventory.setItem(22, button(Material.YELLOW_STAINED_GLASS_PANE,
                    "<yellow><bold>WAGER RETURNED</bold></yellow>",
                    List.of("<gray>The payout could not be journaled safely.</gray>"), null));
                BedrockCompat.sendGameMessage(player, MessageUtil.warn("That spin could not settle safely, so your wager was returned."));
                stopGameTimer(player.getUniqueId(), false);
                session.inventory.setItem(26, button(Material.EMERALD, "<green><bold>SPIN AGAIN</bold></green>", List.of("<gray>Uses the same wager and charges it once.</gray>", "<yellow>Click to spin without leaving.</yellow>"), "slot:repeat"));
                BedrockCompat.syncGameInventory(player);
                return;
            }
            session.inventory.setItem(22, button(Material.LIME_STAINED_GLASS_PANE, "<green><bold>" + session.multiplier + "x PAYOUT</bold></green>", List.of("<gray>Your winnings were delivered.</gray>"), null));
            BedrockCompat.sendGameMessage(player, MessageUtil.success(session.multiplier == 25 ? "Crown jackpot! You won <white>25x</white>." : "The reels pay <white>" + session.multiplier + "x</white>."));
            playResultSound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, session.multiplier == 25 ? 1.7f : 1.25f);
            announceGameResult(playerGameStations.get(player.getUniqueId()), "<gold><bold>" + escapeMini(player.getName()) + " won " + session.multiplier + "x at Tavern Slots!</bold></gold>", Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, session.multiplier == 25 ? 1.6f : 1.25f, Set.of(player.getUniqueId()));
            incrementCardsharpProgress(player, 0);
            stopGameTimer(player.getUniqueId(), true);
        } else {
            if (session.material != null && !payoutEscrow.consumeAll(session.materialEscrows)) {
                returnPlainEscrows(player, session.materialEscrows);
                session.inventory.setItem(22, button(Material.YELLOW_STAINED_GLASS_PANE,
                    "<yellow><bold>WAGER RETURNED</bold></yellow>",
                    List.of("<gray>The loss could not be recorded safely.</gray>"), null));
                BedrockCompat.sendGameMessage(player, MessageUtil.warn("That spin could not settle safely, so your wager was returned."));
                stopGameTimer(player.getUniqueId(), false);
                session.inventory.setItem(26, button(Material.EMERALD, "<green><bold>SPIN AGAIN</bold></green>", List.of("<gray>Uses the same wager and charges it once.</gray>", "<yellow>Click to spin without leaving.</yellow>"), "slot:repeat"));
                BedrockCompat.syncGameInventory(player);
                return;
            }
            session.inventory.setItem(22, button(Material.RED_STAINED_GLASS_PANE, "<red><bold>NO PAYOUT</bold></red>", List.of("<gray>The house takes this spin.</gray>"), null));
            BedrockCompat.sendGameMessage(player, MessageUtil.warn("The reels come up empty."));
            playResultSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.65f);
            stopGameTimer(player.getUniqueId(), false);
        }
        session.inventory.setItem(26, button(Material.EMERALD, "<green><bold>SPIN AGAIN</bold></green>", List.of("<gray>Uses the same wager and charges it once.</gray>", "<yellow>Click to spin without leaving.</yellow>"), "slot:repeat"));
        BedrockCompat.syncGameInventory(player);
    }

    private void refundSlot(SlotSession session) {
        if (!slotSessions.remove(session.playerId, session)) return;
        if (session.task != null) session.task.cancel();
        Player player = Bukkit.getPlayer(session.playerId);
        refundSlotWager(session, player);
        stopGameTimer(session.playerId, false);
        if (player != null) player.sendMessage(MessageUtil.warn("Your unfinished slot wager was refunded."));
    }

    private void refundSlotWager(SlotSession session, Player player) {
        if (session.material != null) {
            returnPlainEscrows(player, session.materialEscrows);
            return;
        }
        if (player == null || plugin.getEssenceManager() == null
            || plugin.getEssenceManager().refund(player, session.wager, "tavern_slots_refund") != session.wager) {
            plugin.getLogger().severe("Could not refund an interrupted Essence slot wager for " + session.playerId + ".");
        }
    }

    private void openCardBetMenu(Player player, BlockKey key) {
        pendingCardTables.put(player.getUniqueId(), key);
        TableLobby lobby = tableLobbies.get(key);
        SlotWager wager = lobby == null
            ? selectedCardWagers.computeIfAbsent(player.getUniqueId(), ignored -> new SlotWager("essence", "1"))
            : lobby.wager;
        if (lobby != null) selectedCardWagers.put(player.getUniqueId(), wager);
        Inventory inv = menu(player, "Crown & Casks Wager", "Card Table Wager");
        inv.setItem(9, button(Material.AMETHYST_SHARD, "<light_purple>Essence</light_purple>", List.of(), "card:currency:essence"));
        inv.setItem(10, button(Material.IRON_INGOT, "<white>Iron</white>", List.of(), "card:currency:iron"));
        inv.setItem(11, button(Material.GOLD_INGOT, "<gold>Gold</gold>", List.of(), "card:currency:gold"));
        inv.setItem(12, button(Material.DIAMOND, "<aqua>Diamonds</aqua>", List.of(), "card:currency:diamond"));
        inv.setItem(14, button(Material.RED_DYE, "<red>-1</red>", List.of(), "card:adjust:-1"));
        inv.setItem(15, button(Material.LIME_DYE, "<green>+1</green>", List.of(), "card:adjust:1"));
        inv.setItem(16, button(Material.LIME_DYE, "<green>+8</green>", List.of(), "card:adjust:8"));
        inv.setItem(20, button(Material.EMERALD, "<green><bold>JOIN FOR " + wager.amount + " " + prettyMaterialName(wager.currency) + "</bold></green>",
            List.of("<gray>Charged when at least two players begin.</gray>"), "card:bet:join"));
        inv.setItem(22, button(Material.BOOK, "<yellow>How Crown & Casks Works</yellow>", List.of(
            "<gray>2-4 players choose one of three cards.</gray>", "<gray>Highest card wins the entire pot.</gray>",
            "<gray>Ties refund every wager.</gray>", "<gray>All players match the first wager.</gray>", "<gray>Maximum wager: 64</gray>"), "close"));
        inv.setItem(24, button(Material.RABBIT_FOOT, "<green><bold>TAVERN LUCK: " + percent(gamblingLuck(player)) + "</bold></green>", List.of(
            "<gray>Chance to draw one extra card</gray>", "<gray>and keep it only when it improves your hand.</gray>"), null));
        player.openInventory(inv);
    }

    private void selectCardCurrency(Player player, String currency) {
        if (!List.of("essence", "iron", "gold", "diamond").contains(currency)) return;
        BlockKey key = pendingCardTables.get(player.getUniqueId());
        if (key == null) return;
        TableLobby lobby = tableLobbies.get(key);
        if (lobby != null) { player.sendMessage(MessageUtil.warn("The first player already locked this table's wager.")); openCardBetMenu(player, key); return; }
        SlotWager current = selectedCardWagers.getOrDefault(player.getUniqueId(), new SlotWager("essence", "1"));
        selectedCardWagers.put(player.getUniqueId(), new SlotWager(currency, current.amount));
        openCardBetMenu(player, key);
    }

    private void adjustCardWager(Player player, String deltaRaw) {
        BlockKey key = pendingCardTables.get(player.getUniqueId());
        if (key == null) return;
        if (tableLobbies.containsKey(key)) { player.sendMessage(MessageUtil.warn("The first player already locked this table's wager.")); openCardBetMenu(player, key); return; }
        int delta; try { delta = Integer.parseInt(deltaRaw); } catch (NumberFormatException ex) { return; }
        SlotWager current = selectedCardWagers.getOrDefault(player.getUniqueId(), new SlotWager("essence", "1"));
        int amount = Math.max(1, Math.min(64, Integer.parseInt(current.amount) + delta));
        selectedCardWagers.put(player.getUniqueId(), new SlotWager(current.currency, Integer.toString(amount)));
        openCardBetMenu(player, key);
    }

    private void joinSelectedCardTable(Player player) {
        BlockKey key = pendingCardTables.get(player.getUniqueId());
        if (key == null || stationAt(key) != StationType.GAME_TABLE) return;
        joinTable(player, key);
    }

    private void joinTable(Player player, BlockKey key) {
        if (slotSessions.containsKey(player.getUniqueId()) || dartSessions.containsKey(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("Finish your current tavern game first."));
            return;
        }
        if (playerCardMatches.containsKey(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("Finish your current card round first."));
            return;
        }
        BlockKey existingLobby = playerTableLobbies.get(player.getUniqueId());
        if (existingLobby != null && !existingLobby.equals(key)) {
            player.sendMessage(MessageUtil.warn("You are already seated at another table."));
            return;
        }
        if (activeTableMatches.containsKey(key)) {
            player.sendMessage(MessageUtil.warn("This table already has a card round in progress."));
            return;
        }
        SlotWager selected = selectedCardWagers.getOrDefault(player.getUniqueId(), new SlotWager("essence", "1"));
        TableLobby lobby = tableLobbies.computeIfAbsent(key, ignored -> new TableLobby(selected, player.getUniqueId()));
        if (!lobby.wager.equals(selected)) {
            selectedCardWagers.put(player.getUniqueId(), lobby.wager);
            player.sendMessage(MessageUtil.warn("This table requires <white>" + lobby.wager.amount + " " + prettyMaterialName(lobby.wager.currency) + "</white>."));
            openCardBetMenu(player, key);
            return;
        }
        synchronized (lobby) {
            if (lobby.players.contains(player.getUniqueId())) {
                openTableLobbyMenu(player, lobby);
                return;
            }
            if (lobby.players.size() >= 4) {
                player.sendMessage(MessageUtil.warn("All four seats are taken."));
                return;
            }
            lobby.players.add(player.getUniqueId());
            playerTableLobbies.put(player.getUniqueId(), key);
            recentCardTables.put(player.getUniqueId(), key);
            startGameTimer(player, TavernGame.CARDS);
            updateStationHologram(key);
            renderTableLobby(lobby);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.55f, 1.25f);
        }
    }

    static int slotMultiplier(int roll) {
        if (roll < 0 || roll >= 10_000) throw new IllegalArgumentException("roll must be between 0 and 9999");
        return roll < 10 ? 25 : roll < 50 ? 15 : roll < 200 ? 8 : roll < 500 ? 4 : roll < 1200 ? 3 : 0;
    }

    static double slotHitRate(double luckChance) {
        if (luckChance < 0.0D || luckChance > MAX_GAMBLING_LUCK) throw new IllegalArgumentException("invalid luck chance");
        double baseHit = 0.12D;
        double bonusHit = 1.0D - (1.0D - baseHit) * (1.0D - baseHit);
        return baseHit * (1.0D - luckChance) + bonusHit * luckChance;
    }

    static int luckySlotRoll(int primaryRoll, int bonusRoll, double triggerRoll, double luckChance) {
        if (primaryRoll < 0 || primaryRoll >= 10_000 || bonusRoll < 0 || bonusRoll >= 10_000) {
            throw new IllegalArgumentException("slot rolls must be between 0 and 9999");
        }
        if (triggerRoll < 0.0D || triggerRoll >= 1.0D || luckChance < 0.0D || luckChance > MAX_GAMBLING_LUCK) {
            throw new IllegalArgumentException("invalid luck chance or trigger");
        }
        return triggerRoll < luckChance ? Math.min(primaryRoll, bonusRoll) : primaryRoll;
    }

    private void resolveTable(BlockKey key, TableLobby lobby) {
        List<Player> players = lobby.players.stream().map(Bukkit::getPlayer).filter(p -> p != null && p.isOnline()).toList();
        if (players.size() < 2) {
            players.forEach(p -> p.sendMessage(MessageUtil.warn("Crown & Casks needs at least two players.")));
            return;
        }
        Player uncovered = players.stream().filter(player -> !canCoverCardWager(player, lobby.wager)).findFirst().orElse(null);
        if (uncovered != null) {
            players.forEach(p -> p.sendMessage(MessageUtil.warn("<white>" + uncovered.getName() + "</white> cannot cover the table wager yet.")));
            renderTableLobby(lobby);
            return;
        }
        if (lobby.wager.currency.equals("essence")) {
            long maximumProfit = (long) Integer.parseInt(lobby.wager.amount) * (players.size() - 1L);
            Player capped = players.stream()
                .filter(player -> !plugin.getEssenceManager().canCreditFully(player, maximumProfit))
                .findFirst().orElse(null);
            if (capped != null) {
                players.forEach(p -> p.sendMessage(MessageUtil.warn("<white>" + capped.getName()
                    + "</white> needs room below the Essence cap before this round.")));
                return;
            }
        }
        if (!tableLobbies.remove(key, lobby)) return;
        for (UUID id : lobby.players) {
            playerTableLobbies.remove(id, key);
            playerLobbyInventories.remove(id);
        }
        List<Player> charged = new ArrayList<>();
        Map<UUID, List<EscrowedItem>> materialEscrows = new HashMap<>();
        UUID matchId = UUID.randomUUID();
        for (Player player : players) {
            List<EscrowedItem> captured = List.of();
            boolean chargedSuccessfully;
            if (lobby.wager.currency.equals("essence")) {
                chargedSuccessfully = takeCardWager(player, lobby.wager);
            } else {
                Material material = cardWagerMaterial(lobby.wager);
                captured = material == null ? null : capturePlainWager(
                    player, material, Integer.parseInt(lobby.wager.amount), matchId);
                chargedSuccessfully = captured != null;
            }
            if (!chargedSuccessfully) {
                for (Player paid : charged) {
                    if (lobby.wager.currency.equals("essence")) refundCardWager(paid, lobby.wager, 1);
                    else returnPlainEscrows(paid, materialEscrows.get(paid.getUniqueId()));
                }
                players.forEach(p -> p.sendMessage(MessageUtil.warn("The card round was cancelled because a player could not cover the wager.")));
                players.forEach(p -> stopGameTimer(p.getUniqueId(), false));
                updateStationHologram(key);
                return;
            }
            if (captured != null && !captured.isEmpty()) materialEscrows.put(player.getUniqueId(), captured);
            charged.add(player);
        }
        startCardMatch(key, players, lobby.wager, matchId, materialEscrows);
    }

    private void openTableLobbyMenu(Player player, TableLobby lobby) {
        Inventory inv = Bukkit.createInventory(new TavernMenuHolder(player.getUniqueId()), 45,
            BedrockCompat.menuTitle(player, MM.deserialize("<gold><bold>Crown & Casks Lobby</bold></gold>"), "Card Table Lobby"));
        ItemStack filler = button(Material.BLACK_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, List.of(), null);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        int slot = 10;
        for (UUID id : lobby.players) {
            Player seated = Bukkit.getPlayer(id);
            boolean host = id.equals(lobby.hostId);
            inv.setItem(slot, button(Material.PLAYER_HEAD, (host ? "<gold>" : "<white>") + (seated == null ? "Waiting player" : seated.getName()) + (host ? " ★</gold>" : "</white>"), List.of(host ? "<gold>Host</gold>" : "<green>Ready</green>"), "close"));
            slot += 2;
        }
        inv.setItem(22, button(Material.GOLD_INGOT, "<gold><bold>TABLE POT</bold></gold>", List.of("<gray>Players: <white>" + lobby.players.size() + "/4</white></gray>", "<gray>Each: <white>" + lobby.wager.amount + " " + prettyMaterialName(lobby.wager.currency) + "</white></gray>", "<gray>Winner takes all. Ties refund.</gray>"), "close"));
        boolean isHost = player.getUniqueId().equals(lobby.hostId);
        inv.setItem(31, button(isHost && lobby.players.size() >= 2 ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
            isHost ? (lobby.players.size() >= 2 ? "<green><bold>DRAW CARDS</bold></green>" : "<gray>WAITING FOR PLAYERS</gray>") : "<yellow>HOST STARTS THE ROUND</yellow>",
            List.of(isHost ? (lobby.players.size() >= 2 ? "<yellow>Click when everyone is ready.</yellow>" : "<gray>At least two players are required.</gray>") : "<gray>" + hostName(lobby) + " controls the draw.</gray>"), isHost && lobby.players.size() >= 2 ? "table:start" : "close"));
        inv.setItem(35, button(Material.BARRIER, "<red><bold>LEAVE TABLE</bold></red>", List.of("<gray>Leave before wagers are charged.</gray>"), "table:leave"));
        inv.setItem(40, button(Material.BOOK, "<yellow>How to Play</yellow>", List.of("<gray>Host draws with 2-4 players.</gray>", "<gray>Choose one of three cards.</gray>", "<gray>Highest card wins the full pot.</gray>"), "close"));
        playerLobbyInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    private String hostName(TableLobby lobby) {
        Player host = Bukkit.getPlayer(lobby.hostId);
        return host == null ? "The host" : host.getName();
    }

    private void startTableFromLobby(Player player) {
        BlockKey key = playerTableLobbies.get(player.getUniqueId());
        TableLobby lobby = key == null ? null : tableLobbies.get(key);
        if (lobby == null || !player.getUniqueId().equals(lobby.hostId)) return;
        resolveTable(key, lobby);
    }

    private void leaveTableLobby(Player player) {
        leaveTableLobby(player, true);
    }

    private void leaveTableLobby(Player player, boolean closeInventory) {
        UUID playerId = player.getUniqueId();
        playerLobbyInventories.remove(playerId);
        BlockKey key = playerTableLobbies.remove(playerId);
        TableLobby lobby = key == null ? null : tableLobbies.get(key);
        if (lobby == null) {
            if (closeInventory) player.closeInventory();
            return;
        }
        synchronized (lobby) {
            lobby.players.remove(playerId);
            if (lobby.players.isEmpty()) tableLobbies.remove(key, lobby);
            else {
                if (playerId.equals(lobby.hostId)) lobby.hostId = lobby.players.iterator().next();
                renderTableLobby(lobby);
            }
        }
        updateStationHologram(key);
        stopGameTimer(playerId, false);
        if (closeInventory) player.closeInventory();
        player.sendMessage(MessageUtil.info("You leave the card table."));
    }

    private void renderTableLobby(TableLobby lobby) {
        for (UUID id : lobby.players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) openTableLobbyMenu(player, lobby);
        }
    }

    private void startCardMatch(
        BlockKey key,
        List<Player> players,
        SlotWager wager,
        UUID matchId,
        Map<UUID, List<EscrowedItem>> materialEscrows
    ) {
        List<Integer> deck = new ArrayList<>();
        for (int suit = 0; suit < 4; suit++) for (int rank = 2; rank <= 14; rank++) deck.add(rank);
        Collections.shuffle(deck);
        CardMatch match = new CardMatch(matchId, key, wager);
        match.materialEscrows.putAll(materialEscrows);
        int cursor = 0;
        for (Player player : players) {
            List<Integer> hand = new ArrayList<>(List.of(deck.get(cursor++), deck.get(cursor++), deck.get(cursor++)));
            if (ThreadLocalRandom.current().nextDouble() < gamblingLuck(player)) {
                int bonus = deck.get(cursor++);
                int lowestIndex = 0;
                for (int i = 1; i < hand.size(); i++) if (hand.get(i) < hand.get(lowestIndex)) lowestIndex = i;
                if (bonus > hand.get(lowestIndex)) {
                    hand.set(lowestIndex, bonus);
                    player.sendActionBar(MM.deserialize("<green>Tavern Luck improved your opening hand.</green>"));
                }
            }
            match.hands.put(player.getUniqueId(), List.copyOf(hand));
            match.contributors.put(player.getUniqueId(), player.getName());
            playerCardMatches.put(player.getUniqueId(), matchId);
        }
        cardMatches.put(matchId, match);
        activeTableMatches.put(key, matchId);
        updateStationHologram(key);
        players.forEach(player -> {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.85f, 1.15f);
            openCardHand(player, match);
        });
        match.task = Bukkit.getScheduler().runTaskLater(plugin, () -> autoSelectAndResolve(match), 20L * 15L);
    }

    private void openCardHand(Player player, CardMatch match) {
        List<Integer> hand = match.hands.get(player.getUniqueId());
        if (hand == null) return;
        Inventory inv = Bukkit.createInventory(new TavernMenuHolder(player.getUniqueId()), 45,
            BedrockCompat.menuTitle(player, MM.deserialize("<gold><bold>Choose Your Card</bold></gold>"), "Choose a Card"));
        ItemStack filler = button(Material.BLACK_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, List.of(), null);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        inv.setItem(4, button(Material.PAPER, "<yellow><bold>YOUR HAND</bold></yellow>", List.of("<gray>Choose one card. Highest card wins.</gray>"), "close"));
        for (int i = 0; i < hand.size(); i++) {
            int rank = hand.get(i);
            inv.setItem(CARD_SLOTS[i], button(cardMaterial(rank), "<gold><bold>" + cardName(rank) + "</bold></gold>", List.of("<gray>Highest chosen card wins.</gray>", "<yellow>Click to play this card.</yellow>"), "card:select:" + match.id + ":" + i));
        }
        inv.setItem(40, button(Material.CLOCK, "<yellow>Choose within 15 seconds</yellow>", List.of("<gray>No choice causes a safe automatic draw.</gray>"), "close"));
        inv.setItem(44, button(Material.BARRIER, "<red><bold>FORFEIT ROUND</bold></red>", List.of("<gray>Your wager stays in the pot.</gray>"), "card:leave:" + match.id));
        player.openInventory(inv);
    }

    private void leaveCardMatch(Player player, String matchRaw) {
        UUID requestedMatch;
        try { requestedMatch = UUID.fromString(matchRaw); }
        catch (IllegalArgumentException ex) { return; }
        if (!requestedMatch.equals(playerCardMatches.get(player.getUniqueId()))) return;
        removePlayerFromCards(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(MessageUtil.info("You leave the card round."));
    }

    private void rejoinCardTable(Player player) {
        BlockKey table = recentCardTables.get(player.getUniqueId());
        if (table == null || stationAt(table) != StationType.GAME_TABLE) {
            player.closeInventory();
            player.sendMessage(MessageUtil.warn("That card table is no longer available."));
            return;
        }
        joinTable(player, table);
    }

    private void selectCard(Player player, String matchRaw, String indexRaw) {
        UUID matchId;
        int index;
        try { matchId = UUID.fromString(matchRaw); index = Integer.parseInt(indexRaw); }
        catch (RuntimeException ex) { return; }
        CardMatch match = cardMatches.get(matchId);
        List<Integer> hand = match == null ? null : match.hands.get(player.getUniqueId());
        if (hand == null || index < 0 || index >= hand.size() || match.selections.containsKey(player.getUniqueId())) return;
        match.selections.put(player.getUniqueId(), hand.get(index));
        player.closeInventory();
        player.sendMessage(MessageUtil.success("You play the <white>" + cardName(hand.get(index)) + "</white>."));
        if (match.selections.size() >= match.hands.size()) resolveCardMatch(match);
    }

    private void autoSelectAndResolve(CardMatch match) {
        for (Map.Entry<UUID, List<Integer>> entry : match.hands.entrySet()) {
            match.selections.computeIfAbsent(entry.getKey(), ignored -> entry.getValue().get(ThreadLocalRandom.current().nextInt(entry.getValue().size())));
        }
        resolveCardMatch(match);
    }

    private void resolveCardMatch(CardMatch match) {
        if (!cardMatches.remove(match.id, match)) return;
        if (match.task != null) match.task.cancel();
        activeTableMatches.remove(match.table, match.id);
        int best = match.selections.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<UUID> winners = match.selections.entrySet().stream().filter(e -> e.getValue() == best).map(Map.Entry::getKey).toList();
        Player winner = winners.size() == 1 ? Bukkit.getPlayer(winners.getFirst()) : null;
        boolean winnerPaid = winner != null && payCardPot(winner, match);
        boolean settledAsWin = winners.size() == 1 && winnerPaid;
        if (settledAsWin) match.potSettled = true;
        else refundCardContributors(match);
        for (UUID id : match.hands.keySet()) {
            playerCardMatches.remove(id, match.id);
            Player player = Bukkit.getPlayer(id);
            stopGameTimer(id, settledAsWin && winners.contains(id));
            if (player == null) continue;
            recentCardTables.put(id, match.table);
            if (settledAsWin) {
                BedrockCompat.sendGameMessage(player, MessageUtil.success("<white>" + winner.getName() + "</white> wins with " + cardName(best) + "."));
                player.playSound(player.getLocation(), winners.contains(id) ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, winners.contains(id) ? 1.25f : 0.65f);
            } else if (winners.size() == 1) {
                BedrockCompat.sendGameMessage(player, MessageUtil.warn("The pot could not be settled safely, so every wager was returned."));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.65f, 0.8f);
            } else {
                BedrockCompat.sendGameMessage(player, MessageUtil.info("The top cards tie at <white>" + cardName(best) + "</white>."));
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.65f, 1.0f);
            }
            openCardResult(player, settledAsWin && winners.contains(id), best, !settledAsWin);
        }
        if (settledAsWin) {
            incrementCardsharpProgress(winner, 1);
            BedrockCompat.sendGameMessage(winner, MessageUtil.success("Winner takes the pot: <white>" + (Integer.parseInt(match.wager.amount) * match.contributors.size()) + " " + prettyMaterialName(match.wager.currency) + "</white>."));
            announceGameResult(match.table, "<gold><bold>" + escapeMini(winner.getName()) + " won the Crown & Casks pot!</bold></gold>", Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.2f, Set.copyOf(match.hands.keySet()));
        }
        updateStationHologram(match.table);
    }

    private void openCardResult(Player player, boolean won, int best, boolean tied) {
        Inventory inv = menu(player, "Crown & Casks Result", "Card Result");
        inv.setItem(13, button(won ? Material.NETHER_STAR : tied ? Material.PAPER : Material.RED_STAINED_GLASS_PANE,
            won ? "<gold><bold>YOU WIN</bold></gold>" : tied ? "<yellow><bold>ROUND TIED</bold></yellow>" : "<red><bold>ROUND LOST</bold></red>",
            List.of(best > 0 ? "<gray>Winning card: <white>" + cardName(best) + "</white></gray>" : "<gray>Won by forfeit.</gray>"), "close"));
        inv.setItem(21, button(Material.EMERALD, "<green><bold>PLAY AGAIN</bold></green>", List.of("<gray>Rejoin this same table.</gray>"), "card:again"));
        inv.setItem(23, button(Material.BARRIER, "<red>Leave Table</red>", List.of("<gray>Close this result.</gray>"), "close"));
        player.openInventory(inv);
    }

    private void removePlayerFromCards(UUID playerId) {
        UUID matchId = playerCardMatches.remove(playerId);
        CardMatch match = matchId == null ? null : cardMatches.get(matchId);
        if (match == null) return;
        match.hands.remove(playerId);
        match.selections.remove(playerId);
        stopGameTimer(playerId, false);
        if (match.hands.size() == 1) resolveCardForfeit(match, match.hands.keySet().iterator().next());
        else if (match.hands.isEmpty()) cancelCardMatch(match);
        else if (match.selections.size() >= match.hands.size()) resolveCardMatch(match);
    }

    private void resolveCardForfeit(CardMatch match, UUID winnerId) {
        if (!cardMatches.remove(match.id, match)) return;
        if (match.task != null) match.task.cancel();
        activeTableMatches.remove(match.table, match.id);
        playerCardMatches.remove(winnerId, match.id);
        Player winner = Bukkit.getPlayer(winnerId);
        if (winner != null && payCardPot(winner, match)) {
            stopGameTimer(winnerId, true);
            match.potSettled = true;
            recentCardTables.put(winnerId, match.table);
            BedrockCompat.sendGameMessage(winner, MessageUtil.success("The other players left. You take the full pot."));
            winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
            announceGameResult(match.table, "<gold><bold>" + escapeMini(winner.getName()) + " won Crown & Casks by forfeit!</bold></gold>", Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.1f, Set.of(winnerId));
            openCardResult(winner, true, 0, false);
        } else {
            refundCardContributors(match);
            if (winner != null) {
                stopGameTimer(winnerId, false);
                BedrockCompat.sendGameMessage(winner, MessageUtil.warn("The pot could not be settled safely, so every wager was returned."));
                openCardCancelled(winner);
            }
        }
        updateStationHologram(match.table);
    }

    private void cancelCardMatch(CardMatch match) {
        if (!cardMatches.remove(match.id, match)) return;
        if (match.task != null) match.task.cancel();
        activeTableMatches.remove(match.table, match.id);
        refundCardContributors(match);
        for (UUID id : match.hands.keySet()) {
            playerCardMatches.remove(id, match.id);
            stopGameTimer(id, false);
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                recentCardTables.put(id, match.table);
                openCardCancelled(player);
            }
        }
        updateStationHologram(match.table);
    }

    private boolean takeCardWager(Player player, SlotWager wager) {
        int amount = Integer.parseInt(wager.amount);
        return wager.currency.equals("essence") && spend(player, amount, "tavern_cards_wager");
    }

    private boolean canCoverCardWager(Player player, SlotWager wager) {
        int amount = Integer.parseInt(wager.amount);
        if (wager.currency.equals("essence")) return plugin.getEssenceManager() != null
            && plugin.getEssenceManager().isLoaded(player) && plugin.getEssenceManager().balance(player) >= amount;
        Material material = cardWagerMaterial(wager);
        return material != null && hasPlain(player, material, amount);
    }

    private void refundCardWager(Player player, SlotWager wager, int shares) {
        if (player == null || shares <= 0) return;
        int amount = Integer.parseInt(wager.amount) * shares;
        if (wager.currency.equals("essence") && (plugin.getEssenceManager() == null
            || plugin.getEssenceManager().refund(player, amount, "tavern_cards_refund") != amount)) {
            plugin.getLogger().severe("Could not refund an Essence card wager for " + player.getName() + ".");
        }
    }

    private boolean payCardPot(Player winner, CardMatch match) {
        int amount = Integer.parseInt(match.wager.amount) * match.contributors.size();
        if (match.wager.currency.equals("essence")) {
            return credit(winner, amount, "tavern_cards_payout");
        }
        Material material = cardWagerMaterial(match.wager);
        List<EscrowedItem> consumed = match.materialEscrows.values().stream().flatMap(Collection::stream).toList();
        return settlePlainEscrows(winner, consumed, material, amount, "CARDS_PAYOUT");
    }

    private Material cardWagerMaterial(SlotWager wager) {
        return switch (wager.currency) {
            case "iron" -> Material.IRON_INGOT;
            case "gold" -> Material.GOLD_INGOT;
            case "diamond" -> Material.DIAMOND;
            default -> null;
        };
    }

    private void refundCardContributors(CardMatch match) {
        if (match.potSettled) return;
        match.potSettled = true;
        for (UUID id : match.contributors.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (match.wager.currency.equals("essence")) {
                if (player != null) refundCardWager(player, match.wager, 1);
                else plugin.getLogger().warning("Could not immediately return an offline Essence card wager to " + match.contributors.get(id) + ".");
            } else {
                returnPlainEscrows(player, match.materialEscrows.get(id));
            }
        }
    }

    private void openCardCancelled(Player player) {
        Inventory inv = menu(player, "Crown & Casks", "Card Round Ended");
        inv.setItem(13, button(Material.PAPER, "<yellow><bold>ROUND ENDED</bold></yellow>", List.of("<gray>Not enough players remained.</gray>"), "close"));
        inv.setItem(21, button(Material.EMERALD, "<green><bold>PLAY AGAIN</bold></green>", List.of("<gray>Rejoin this same table.</gray>"), "card:again"));
        inv.setItem(23, button(Material.BARRIER, "<red>Leave Table</red>", List.of("<gray>Close this result.</gray>"), "close"));
        player.openInventory(inv);
    }

    private Material cardMaterial(int rank) {
        return rank >= 14 ? Material.NETHER_STAR : rank >= 11 ? Material.GOLD_INGOT : rank >= 8 ? Material.IRON_INGOT : Material.PAPER;
    }

    private String cardName(int rank) {
        return switch (rank) { case 11 -> "Jack"; case 12 -> "Queen"; case 13 -> "King"; case 14 -> "Ace"; default -> Integer.toString(rank); };
    }

    private void openDartsGame(Player player) {
        if (isTavernGameBusy(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("Finish your current tavern game first."));
            return;
        }
        Inventory inv = menu(player, "Tavern Darts", "Tavern Darts");
        DartSession session = new DartSession(player.getUniqueId(), inv);
        dartSessions.put(player.getUniqueId(), session);
        startGameTimer(player, TavernGame.DARTS);
        prepareDartThrow(session);
        renderDarts(session);
        player.openInventory(inv);
        session.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickDarts(session), 1L, 1L);
    }

    private void tickDarts(DartSession session) {
        Player player = Bukkit.getPlayer(session.playerId);
        if (player == null || !player.isOnline()) { cancelDarts(session.playerId); return; }
        if (!session.armed) {
            if (--session.armTicksRemaining <= 0) {
                session.armed = true;
                session.prematureClicks = 0;
                session.ticksUntilMove = nextDartMoveDelay(session);
                renderDartThrowButton(session);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, 1.45f);
            }
            return;
        }
        if (--session.ticksUntilMove > 0) return;

        maybeTurnDartCursor(session);
        int previousCursor = session.cursor;
        DartStep step = advanceDartCursor(session.cursor, session.direction);
        session.cursor = step.cursor;
        session.direction = step.direction;
        session.stepsSinceTurn++;
        renderDartLaneSlot(session, previousCursor);
        renderDartLaneSlot(session, session.cursor);
        session.ticksUntilMove = nextDartMoveDelay(session);
    }

    static DartStep advanceDartCursor(int cursor, int direction) {
        if (cursor < 0 || cursor >= DART_LANE.length) throw new IllegalArgumentException("cursor outside dart lane");
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("direction must be -1 or 1");
        int next = cursor + direction;
        if (next < 0) return new DartStep(1, 1);
        if (next >= DART_LANE.length) return new DartStep(DART_LANE.length - 2, -1);
        if (next == 0) return new DartStep(0, 1);
        if (next == DART_LANE.length - 1) return new DartStep(next, -1);
        return new DartStep(next, direction);
    }

    private void renderDarts(DartSession session) {
        for (int i = 0; i < DART_LANE.length; i++) {
            renderDartLaneSlot(session, i);
        }
        renderDartThrowButton(session);
        session.inventory.setItem(26, button(Material.BOOK, "<yellow>How Darts Work</yellow>", List.of(
            "<gray>Three timing-based throws.</gray>",
            "<gray>The pace and direction can shift.</gray>",
            "<gray>Rapid clicks delay your next throw.</gray>",
            "<gray>Center hits score 50.</gray>",
            "<green>Perfect 150: " + DARTS_PERFECT_REWARD + " Essence</green>"
        ), "close"));
    }

    private void renderDartLaneSlot(DartSession session, int index) {
        int distance = Math.abs(index - 4);
        Material material = index == session.cursor ? Material.SPECTRAL_ARROW
            : distance == 0 ? Material.LIME_STAINED_GLASS_PANE
            : distance == 1 ? Material.YELLOW_STAINED_GLASS_PANE
            : distance == 2 ? Material.ORANGE_STAINED_GLASS_PANE
            : Material.RED_STAINED_GLASS_PANE;
        session.inventory.setItem(DART_LANE[index], button(material,
            index == session.cursor ? "<white><bold>AIM</bold></white>" : "<gray>Target</gray>", List.of(), "close"));
    }

    private void renderDartThrowButton(DartSession session) {
        Material material = session.armed ? Material.TARGET : Material.CLOCK;
        String name = session.armed ? "<gold><bold>THROW</bold></gold>" : "<yellow><bold>STEADY...</bold></yellow>";
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Throw " + (session.throwsMade + 1) + "/3</gray>");
        lore.add("<gray>Total: " + session.total + "</gray>");
        lore.add(session.armed
            ? "<yellow>Click when AIM reaches the center.</yellow>"
            : "<dark_gray>Wait for THROW before clicking.</dark_gray>");
        if (!session.armed && session.prematureClicks >= DART_RAPID_CLICK_LIMIT) {
            lore.add("<red>Rapid clicking is delaying your aim.</red>");
        }
        session.inventory.setItem(22, button(material, name, lore, "dart:throw"));
    }

    private void attemptDartThrow(Player player) {
        DartSession session = dartSessions.get(player.getUniqueId());
        if (session == null || player.getOpenInventory().getTopInventory() != session.inventory) return;
        if (!session.armed) {
            session.prematureClicks++;
            if (session.prematureClicks >= DART_RAPID_CLICK_LIMIT) {
                session.armTicksRemaining = Math.max(session.armTicksRemaining, ThreadLocalRandom.current().nextInt(8, 15));
                renderDartThrowButton(session);
                long now = System.currentTimeMillis();
                if (now - session.lastRapidClickWarningAt >= 1_000L) {
                    session.lastRapidClickWarningAt = now;
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.35f, 0.65f);
                }
            }
            return;
        }

        session.armed = false;
        int score = dartScoreForCursor(session.cursor);
        session.throwsMade++;
        session.total += score;
        if (session.throwsMade < 3) player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.75f, score == 50 ? 1.7f : 1.1f);
        player.sendMessage(MessageUtil.info("Dart <white>" + session.throwsMade + "/3</white>: <gold>" + score + " points</gold>."));
        if (score == 50) {
            incrementCardsharpProgress(player, 2);
        }
        if (session.throwsMade >= 3) {
            if (session.total == 150) {
                credit(player, DARTS_PERFECT_REWARD, "tavern_darts_perfect");
                recordTavernStat(player, TavernGame.DARTS, 1, 0L);
                BedrockCompat.sendGameMessage(player, MessageUtil.success("Perfect round! You earned <white>"
                    + DARTS_PERFECT_REWARD + " Essence</white>."));
                announceGameResult(playerGameStations.get(player.getUniqueId()), "<gold><bold>" + escapeMini(player.getName()) + " threw a perfect 150 at Tavern Darts!</bold></gold>", Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.25f, Set.of(player.getUniqueId()));
            }
            finishDarts(session, player);
            player.sendMessage(MessageUtil.success("Darts total: <white>" + session.total + "</white>."));
            return;
        }
        prepareDartThrow(session);
        renderDarts(session);
    }

    private void prepareDartThrow(DartSession session) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        session.cursor = DART_START_POSITIONS[random.nextInt(DART_START_POSITIONS.length)];
        session.direction = session.cursor <= 1 ? 1 : session.cursor >= 7 ? -1 : random.nextBoolean() ? 1 : -1;
        session.armed = false;
        session.armTicksRemaining = random.nextInt(10, 21);
        session.ticksUntilMove = 0;
        session.moveDelayTicks = 0;
        session.speedStepsRemaining = 0;
        session.stepsSinceTurn = 0;
        session.turnCooldown = random.nextInt(3, 7);
        session.prematureClicks = 0;
    }

    private int nextDartMoveDelay(DartSession session) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (session.speedStepsRemaining <= 0) {
            int previousDelay = session.moveDelayTicks;
            int nextDelay;
            do {
                nextDelay = random.nextInt(1, 5);
            } while (nextDelay == previousDelay);
            session.moveDelayTicks = nextDelay;
            session.speedStepsRemaining = random.nextInt(2, 6);
        }
        session.speedStepsRemaining--;
        return dartDelayForCursor(session.cursor, session.moveDelayTicks);
    }

    private void maybeTurnDartCursor(DartSession session) {
        if (session.cursor <= 1 || session.cursor >= DART_LANE.length - 2
            || session.stepsSinceTurn < session.turnCooldown
            || ThreadLocalRandom.current().nextDouble() >= 0.12D) {
            return;
        }
        session.direction *= -1;
        session.stepsSinceTurn = 0;
        session.turnCooldown = ThreadLocalRandom.current().nextInt(3, 7);
    }

    static int dartDelayForCursor(int cursor, int delayTicks) {
        if (cursor < 0 || cursor >= DART_LANE.length) throw new IllegalArgumentException("cursor outside dart lane");
        if (delayTicks < 1 || delayTicks > 4) throw new IllegalArgumentException("dart delay must be between 1 and 4 ticks");
        return cursor == 4 ? Math.max(2, delayTicks) : delayTicks;
    }

    private void cancelDarts(UUID playerId) {
        DartSession session = dartSessions.remove(playerId);
        if (session != null && session.task != null) session.task.cancel();
        if (session != null) stopGameTimer(playerId, false);
    }

    private void finishDarts(DartSession session, Player player) {
        if (!dartSessions.remove(session.playerId, session)) return;
        if (session.task != null) session.task.cancel();
        stopGameTimer(session.playerId, false);
        for (int slot : DART_LANE) session.inventory.setItem(slot, button(Material.LIME_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, List.of(), "close"));
        session.inventory.setItem(13, button(Material.TARGET, "<gold><bold>" + session.total + " POINTS</bold></gold>", List.of("<gray>Three throws complete.</gray>"), "close"));
        session.inventory.setItem(21, button(Material.EMERALD, "<green><bold>PLAY AGAIN</bold></green>", List.of("<gray>Start three more throws.</gray>"), "dart:repeat"));
        session.inventory.setItem(23, button(Material.BARRIER, "<red>Leave Darts</red>", List.of("<gray>Close this game.</gray>"), "close"));
        boolean perfect = session.total == 150;
        player.playSound(player.getLocation(), perfect ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_NOTE_BLOCK_BASS, 0.75f, perfect ? 1.35f : 0.75f);
        player.getWorld().spawnParticle(perfect ? Particle.FIREWORK : Particle.SMOKE, player.getLocation().add(0, 1, 0), perfect ? 18 : 8, 0.35, 0.45, 0.35, 0.02);
    }

    static int dartScoreForCursor(int cursor) {
        if (cursor < 0 || cursor >= DART_LANE.length) throw new IllegalArgumentException("cursor outside dart lane");
        return switch (Math.abs(cursor - 4)) { case 0 -> 50; case 1 -> 25; case 2 -> 15; case 3 -> 8; default -> 2; };
    }

    private void openRumorMenu(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String today = today();
        boolean accepted = today.equals(pdc.get(bountyDayKey, PersistentDataType.STRING));
        int progress = accepted ? pdc.getOrDefault(bountyProgressKey, PersistentDataType.INTEGER, 0) : 0;
        boolean claimed = accepted && pdc.has(bountyClaimedKey, PersistentDataType.BYTE);
        Inventory inv = menu(player, "Tavern Rumor Board", "Rumor Board");
        inv.setItem(13, button(claimed ? Material.EMERALD : Material.WRITABLE_BOOK, "<gold><bold>Today's Bounty</bold></gold>", List.of(
            "<gray>Whispers tell of hostile creatures gathering.</gray>",
            "<gray>Defeat: <white>" + progress + "/" + BOUNTY_KILLS + " hostile mobs</white></gray>",
            "<gray>Reward: <white>" + BOUNTY_REWARD + " Essence</white></gray>",
            claimed ? "<green>Completed today.</green>" : accepted ? "<yellow>Bounty active.</yellow>" : "<yellow>Click to accept.</yellow>"
        ), accepted ? "close" : "rumor:accept"));
        player.openInventory(inv);
    }

    private void acceptRumor(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String today = today();
        if (today.equals(pdc.get(bountyDayKey, PersistentDataType.STRING))) return;
        pdc.set(bountyDayKey, PersistentDataType.STRING, today);
        pdc.set(bountyProgressKey, PersistentDataType.INTEGER, 0);
        pdc.remove(bountyClaimedKey);
        player.sendMessage(MessageUtil.success("Daily rumor accepted: defeat <white>" + BOUNTY_KILLS + " hostile mobs</white>."));
    }

    private void handleBrewmasterQuest(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc.has(questKey("brew_done"), PersistentDataType.BYTE)) {
            player.sendMessage(MessageUtil.info("Bram: That canteen should serve you well."));
            return;
        }
        if (!pdc.has(questKey("brew_started"), PersistentDataType.BYTE)) {
            pdc.set(questKey("brew_started"), PersistentDataType.BYTE, (byte) 1);
            player.sendMessage(MessageUtil.info("Bram: Bring me <white>16 wheat, 8 sweet berries, and 4 honey bottles</white>."));
            player.sendMessage(MessageUtil.info("Bram: I'll make you a canteen that gives Speed and Haste."));
            return;
        }
        if (!hasPlain(player, Material.WHEAT, 16) || !hasPlain(player, Material.SWEET_BERRIES, 8) || !hasPlain(player, Material.HONEY_BOTTLE, 4)) {
            player.sendMessage(MessageUtil.warn("Bram still needs 16 wheat, 8 sweet berries, and 4 honey bottles."));
            return;
        }
        Map<Material, Integer> ingredients = Map.of(
            Material.WHEAT, 16,
            Material.SWEET_BERRIES, 8,
            Material.HONEY_BOTTLE, 4
        );
        if (!InventoryRecipeUtil.removePlainMaterials(plugin, player, ingredients)) {
            player.sendMessage(MessageUtil.warn("The ingredients changed before Bram could take them."));
            return;
        }
        giveQuestReward(player, rewardItem("canteen"));
        pdc.set(questKey("brew_done"), PersistentDataType.BYTE, (byte) 1);
        player.sendMessage(MessageUtil.success("Bram gives you the <white>Cellarmaster's Canteen</white>."));
        if (plugin.getStoryService() != null) plugin.getStoryService().onTavernMilestone(player, "bram_cellarmaster");
    }

    private void handleCardsharpQuest(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc.has(questKey("cards_done"), PersistentDataType.BYTE)) {
            player.sendMessage(MessageUtil.info("Rook: Nice work. The coin gives Luck II once per day."));
            return;
        }
        if (!pdc.has(questKey("cards_started"), PersistentDataType.BYTE)) {
            pdc.set(questKey("cards_started"), PersistentDataType.BYTE, (byte) 1);
            pdc.set(questProgressKey, PersistentDataType.INTEGER, 0);
            player.sendMessage(MessageUtil.info("Rook: Win at the slots, win Crown & Casks, and strike a dart bullseye."));
            return;
        }
        int mask = pdc.getOrDefault(questProgressKey, PersistentDataType.INTEGER, 0);
        if (mask != 7) {
            player.sendMessage(MessageUtil.info("Rook's trial: Slots " + mark(mask, 0) + " | Table " + mark(mask, 1) + " | Bullseye " + mark(mask, 2)));
            return;
        }
        giveQuestReward(player, rewardItem("house_coin"));
        pdc.set(questKey("cards_done"), PersistentDataType.BYTE, (byte) 1);
        player.sendMessage(MessageUtil.success("Rook gives you the <white>Quiet House Coin</white>."));
        if (plugin.getStoryService() != null) plugin.getStoryService().onTavernMilestone(player, "rook_trial");
    }

    private void incrementCardsharpProgress(Player player, int bit) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (!pdc.has(questKey("cards_started"), PersistentDataType.BYTE) || pdc.has(questKey("cards_done"), PersistentDataType.BYTE)) return;
        int old = pdc.getOrDefault(questProgressKey, PersistentDataType.INTEGER, 0);
        int updated = old | (1 << bit);
        pdc.set(questProgressKey, PersistentDataType.INTEGER, updated);
        if (updated != old) player.sendMessage(MessageUtil.success("Rook's trial advanced. <white>" + Integer.bitCount(updated) + "/3</white>."));
    }

    private ItemStack rewardItem(String id) {
        Material material = id.equals("canteen") ? Material.POTION : Material.SUNFLOWER;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        String name = id.equals("canteen") ? "Cellarmaster's Canteen" : "Quiet House Coin";
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.RARE, name));
        meta.lore(CustomLoreUtil.buildStyledLore(meta, material, "RARE", "TAVERN RELIC", List.of("<gray>Earned in the spawn tavern.</gray>"), List.of(CustomLoreUtil.section("Use", id.equals("canteen") ? "Second Wind" : "Quiet Fortune", id.equals("canteen") ? "<gray>Speed I and Haste I for 3 minutes.</gray>" : "<gray>Luck II for 15 minutes.</gray>", id.equals("canteen") ? "<dark_gray>30 minute cooldown.</dark_gray>" : "<dark_gray>24 hour cooldown.</dark_gray>"))));
        meta.getPersistentDataContainer().set(rewardKey, PersistentDataType.STRING, id);
        meta.setMaxStackSize(1);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void useQuestReward(Player player, ItemStack item, String id) {
        long now = System.currentTimeMillis();
        NamespacedKey readyKey = new NamespacedKey(plugin, "tavern_" + id + "_ready");
        long ready = player.getPersistentDataContainer().getOrDefault(readyKey, PersistentDataType.LONG, 0L);
        if (ready > now) {
            player.sendMessage(MessageUtil.warn("Ready in <white>" + Math.max(1, (ready - now + 999) / 1000) + "s</white>."));
            return;
        }
        if (id.equals("canteen")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 180, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 180, 0));
            player.getPersistentDataContainer().set(readyKey, PersistentDataType.LONG, now + CANTEEN_COOLDOWN_MS);
        } else if (id.equals("house_coin")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 20 * 900, 1));
            player.getPersistentDataContainer().set(readyKey, PersistentDataType.LONG, now + COIN_COOLDOWN_MS);
        } else return;
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.35f);
        player.sendMessage(MessageUtil.success("The " + (id.equals("canteen") ? "canteen" : "coin") + " stirs to life."));
    }

    private void giveQuestReward(Player player, ItemStack reward) {
        InventoryRecipeUtil.giveOrDrop(player, reward);
        recordAcquisition(player, reward, "tavern_quest", "Completed a tavern NPC quest.");
    }

    private void recordAcquisition(Player player, ItemStack item, String source, String detail) {
        if (plugin.getItemAuditManager() != null) plugin.getItemAuditManager().recordKnownAcquisition(player, item, source, detail);
    }

    private boolean spend(Player player, long amount, String reason) {
        return plugin.getEssenceManager() != null && plugin.getEssenceManager().spend(player, amount, reason);
    }

    private boolean credit(Player player, long amount, String reason) {
        return plugin.getEssenceManager() != null && plugin.getEssenceManager().credit(player, amount, reason);
    }

    private boolean hasPlain(Player player, Material material, int amount) {
        return plainCount(player, material) >= amount;
    }

    private int plainCount(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) if (InventoryRecipeUtil.isPlainMaterial(plugin, item, material)) count += item.getAmount();
        return count;
    }

    private boolean removePlain(Player player, Material material, int amount) {
        if (!hasPlain(player, material, amount)) return false;
        int left = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack item = contents[i];
            if (!InventoryRecipeUtil.isPlainMaterial(plugin, item, material)) continue;
            int take = Math.min(left, item.getAmount());
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) contents[i] = null;
            left -= take;
        }
        player.getInventory().setStorageContents(contents);
        return true;
    }

    private List<EscrowedItem> capturePlainWager(Player player, Material material, int amount, UUID transactionId) {
        if (player == null || material == null || amount <= 0 || !hasPlain(player, material, amount)) {
            return null;
        }
        List<EscrowedItem> captured = new ArrayList<>();
        int remaining = amount;
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length && remaining > 0; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (!InventoryRecipeUtil.isPlainMaterial(plugin, item, material)) continue;
            int take = Math.min(remaining, item.getAmount());
            EscrowedItem escrowed = payoutEscrow.capturePartial(transactionId, player, slot, item.clone(), take);
            if (escrowed == null) {
                returnPlainEscrows(player, captured);
                return null;
            }
            payoutEscrow.retain(escrowed);
            captured.add(escrowed);
            remaining -= take;
        }
        if (remaining > 0) {
            returnPlainEscrows(player, captured);
            return null;
        }
        return List.copyOf(captured);
    }

    private boolean settlePlainEscrows(
        Player player,
        Collection<EscrowedItem> consumed,
        Material material,
        int amount,
        String state
    ) {
        if (player == null || consumed == null || consumed.isEmpty() || material == null || amount <= 0) {
            return false;
        }
        List<EscrowPayout> payouts = splitPlainPayoutAmounts(amount, material.getMaxStackSize()).stream()
            .map(stack -> new EscrowPayout(player.getUniqueId(), player.getName(), new ItemStack(material, stack)))
            .toList();
        if (!payoutEscrow.replaceEscrowsWithRecoveries(consumed, payouts, state)) {
            return false;
        }
        restoreTavernPayouts(player, false);
        return true;
    }

    private void returnPlainEscrows(Player player, Collection<EscrowedItem> escrows) {
        if (escrows == null || escrows.isEmpty()) return;
        for (EscrowedItem escrowed : escrows) {
            if (escrowed == null) continue;
            payoutEscrow.retarget(escrowed, escrowed.ownerId(), escrowed.ownerName(), "RETURNING");
            if (player != null && player.isOnline() && player.getUniqueId().equals(escrowed.ownerId())
                && payoutEscrow.give(player, escrowed)) continue;
            payoutEscrow.queueRecovery(escrowed.ownerId(), escrowed.ownerName(), escrowed);
        }
    }

    private void deliverPlainPayout(Player player, UUID ownerId, String ownerName, Material material, int amount, String state) {
        int queued = 0;
        for (int stackAmount : splitPlainPayoutAmounts(amount, material.getMaxStackSize())) {
            if (!payoutEscrow.deliverOrQueueGenerated(player, ownerId, ownerName, new ItemStack(material, stackAmount), state)) queued += stackAmount;
        }
        if (player != null && queued > 0) {
            player.sendMessage(MessageUtil.info("Your inventory is full. <white>" + queued + " " + prettyMaterial(material) + "</white> is safely held and will return when you make space."));
        }
    }

    static List<Integer> splitPlainPayoutAmounts(int amount, int maxStackSize) {
        if (amount <= 0 || maxStackSize <= 0) return List.of();
        List<Integer> stacks = new ArrayList<>();
        int left = amount;
        while (left > 0) {
            int stack = Math.min(maxStackSize, left);
            stacks.add(stack);
            left -= stack;
        }
        return List.copyOf(stacks);
    }

    private void restoreTavernPayouts(Player player, boolean notifyPending) {
        boolean restored = payoutEscrow.restorePendingRecovery(player);
        if (restored) player.sendMessage(MessageUtil.success("Your held tavern winnings were returned."));
        if (notifyPending && payoutEscrow.hasPendingRecovery(player)) {
            player.sendMessage(MessageUtil.info("Some tavern winnings are safely held. Clear an inventory slot to receive them automatically."));
        }
    }

    private boolean canFitOne(Player player, ItemStack item) {
        if (player.getInventory().firstEmpty() >= 0) return true;
        for (ItemStack stored : player.getInventory().getStorageContents()) {
            if (stored != null && stored.isSimilar(item) && stored.getAmount() < stored.getMaxStackSize()) return true;
        }
        return false;
    }

    private NamespacedKey questKey(String id) { return new NamespacedKey(plugin, "tavern_" + id); }
    private String mark(int mask, int bit) { return (mask & (1 << bit)) != 0 ? "<green>done</green>" : "<gray>missing</gray>"; }
    private String today() { return LocalDate.now(ZoneOffset.UTC).toString(); }

    private boolean isHostile(EntityType type) {
        return type.getEntityClass() != null && org.bukkit.entity.Monster.class.isAssignableFrom(type.getEntityClass());
    }

    private boolean isTavernGameBusy(UUID playerId) {
        return slotSessions.containsKey(playerId)
            || dartSessions.containsKey(playerId)
            || playerCardMatches.containsKey(playerId)
            || playerTableLobbies.containsKey(playerId);
    }

    private String prettyMaterial(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private void refreshAllStationHolograms() {
        removeAllStationHolograms();
        for (StationType type : StationType.values()) for (BlockKey key : stations.get(type)) refreshStationHologram(key);
        int total = stations.values().stream().mapToInt(Set::size).sum();
        plugin.getLogger().info("Loaded " + total + " tavern station(s) with visible interaction labels.");
    }

    private void refreshStationHologram(BlockKey key) {
        StationType type = stationAt(key);
        Location location = key.location();
        if (type == null || location == null || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return;
        removeStationHologram(key);
        TextDisplay display = location.getWorld().spawn(location.clone().add(0.5, 1.65, 0.5), TextDisplay.class, entity -> {
            entity.text(stationText(type, key));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setSeeThrough(false);
            entity.setShadowed(true);
            entity.setDefaultBackground(false);
            entity.setTextOpacity((byte) 255);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setPersistent(false);
            entity.getPersistentDataContainer().set(hologramKey, PersistentDataType.STRING, key.serialize());
            VisualRangeUtil.applyHologramRange(entity, 16.0D);
        });
        stationHolograms.put(key, display.getUniqueId());
    }

    private Component stationText(StationType type, BlockKey key) {
        return switch (type) {
            case SLOT_MACHINE -> MM.deserialize("<gold><bold>TAVERN SLOTS</bold></gold><newline><yellow>Right-click to spin</yellow>");
            case DARTBOARD -> MM.deserialize("<red><bold>TAVERN DARTS</bold></red><newline><yellow>Right-click for 3 throws</yellow>");
            case RUMOR_BOARD -> MM.deserialize("<aqua><bold>RUMOR BOARD</bold></aqua><newline><yellow>Dailies & player bounties</yellow>");
            case GAME_TABLE -> {
                UUID active = activeTableMatches.get(key);
                TableLobby lobby = tableLobbies.get(key);
                String status = active != null ? "<red>Round in progress</red>" : lobby == null
                    ? "<yellow>0/4 - Right-click to host</yellow>"
                    : "<yellow>" + lobby.players.size() + "/4 - Host draws when ready</yellow>";
                yield MM.deserialize("<green><bold>CROWN & CASKS</bold></green><newline>" + status);
            }
        };
    }

    private void updateStationHologram(BlockKey key) {
        UUID id = stationHolograms.get(key);
        Entity entity = id == null ? null : Bukkit.getEntity(id);
        StationType type = stationAt(key);
        if (entity instanceof TextDisplay display && display.isValid() && type != null) display.text(stationText(type, key));
        else refreshStationHologram(key);
    }

    private void removeStationHologram(BlockKey key) {
        UUID id = stationHolograms.remove(key);
        Entity entity = id == null ? null : Bukkit.getEntity(id);
        if (entity != null) entity.remove();
    }

    private void removeAllStationHolograms() {
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getPersistentDataContainer().has(hologramKey, PersistentDataType.STRING)) display.remove();
            }
        }
        stationHolograms.clear();
    }

    private StationType stationAt(BlockKey key) {
        for (StationType type : StationType.values()) if (stations.get(type).contains(key)) return type;
        return null;
    }

    private ItemStack button(Material material, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(MenuItemUtil.visibleMiniName(name)));
        meta.lore(MenuItemUtil.visibleMiniLore(name, lore).stream().map(MM::deserialize).toList());
        if (action != null && !action.isBlank()) meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private String action(ItemStack item) {
        if (!MenuItemUtil.isVisibleItem(item)) return null;
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    private void startGameTimer(Player player, TavernGame game) {
        gameTimers.putIfAbsent(player.getUniqueId(), new GameTimer(game, player.getName(), System.currentTimeMillis()));
    }

    private void stopGameTimer(UUID playerId, boolean win) {
        GameTimer timer = gameTimers.remove(playerId);
        if (timer == null) return;
        long seconds = Math.max(1L, (System.currentTimeMillis() - timer.startedAt) / 1000L);
        plugin.getDatabase().incrementTavernStat(playerId, timer.playerName, timer.game.id, win ? 1 : 0, seconds)
            .exceptionally(error -> { plugin.getLogger().warning("Could not save tavern game stats: " + error.getMessage()); return null; });
    }

    private void recordTavernStat(Player player, TavernGame game, int wins, long seconds) {
        plugin.getDatabase().incrementTavernStat(player.getUniqueId(), player.getName(), game.id, wins, seconds)
            .exceptionally(error -> { plugin.getLogger().warning("Could not save tavern game stats: " + error.getMessage()); return null; });
    }

    public void recordExternalGameStat(UUID playerId, String playerName, TavernGame game, int wins, long seconds) {
        if (playerId == null || playerName == null || playerName.isBlank() || game == null) return;
        plugin.getDatabase().incrementTavernStat(playerId, playerName, game.id, Math.max(0, wins), Math.max(0L, seconds))
            .exceptionally(error -> { plugin.getLogger().warning("Could not save tavern game stats: " + error.getMessage()); return null; });
    }

    private void refreshAllTavernLeaderboards() {
        resolvePendingLeaderboards();
        for (UUID id : new ArrayList<>(tavernLeaderboards.keySet())) refreshTavernLeaderboard(id);
    }

    private void refreshTavernLeaderboard(UUID id) {
        TavernLeaderboard board = tavernLeaderboards.get(id);
        if (board == null || board.location.getWorld() == null) return;
        plugin.getDatabase().loadTavernGameLeaderboard(board.game.id, 10).whenComplete((entries, error) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!tavernLeaderboards.containsKey(id)) return;
                List<DatabaseManager.TavernLeaderboardEntry> safeEntries = error == null && entries != null ? entries : List.of();
                spawnTavernLeaderboardDisplay(board, safeEntries);
            })
        );
    }

    private void spawnTavernLeaderboardDisplay(TavernLeaderboard board, List<DatabaseManager.TavernLeaderboardEntry> entries) {
        removeTavernLeaderboardDisplay(board.id);
        if (board.location.getWorld() == null) return;
        board.location.getChunk().load();
        removePersistedLeaderboardDisplays(board);
        StringBuilder text = new StringBuilder("<gold><bold>").append(board.game.display.toUpperCase(Locale.ROOT)).append(" CHAMPIONS</bold></gold><newline><gray>Wins · Playtime</gray>");
        if (entries.isEmpty()) text.append("<newline><gray>No scores yet</gray>");
        else for (int i = 0; i < entries.size(); i++) {
            DatabaseManager.TavernLeaderboardEntry entry = entries.get(i);
            text.append("<newline><yellow>").append(i + 1).append(".</yellow> <white>")
                .append(escapeMini(entry.playerName())).append("</white> <gray>-</gray> <aqua>")
                .append(entry.wins()).append(" wins</aqua> <dark_gray>·</dark_gray> <green>")
                .append(formatGameTime(entry.playtimeSeconds())).append("</green>");
        }
        TextDisplay display = board.location.getWorld().spawn(board.location, TextDisplay.class, entity -> {
            entity.text(MM.deserialize(text.toString()));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setSeeThrough(false);
            entity.setShadowed(true);
            entity.setDefaultBackground(false);
            // Persist across chunk unloads; the matching old display is removed before refresh.
            entity.setPersistent(true);
            entity.setInvulnerable(true);
            entity.getPersistentDataContainer().set(leaderboardHologramKey, PersistentDataType.STRING, board.id.toString());
            VisualRangeUtil.applyHologramRange(entity, 24.0D);
        });
        tavernLeaderboardDisplays.put(board.id, display.getUniqueId());
    }

    private String formatGameTime(long seconds) {
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        return hours > 0 ? hours + "h " + minutes + "m" : minutes > 0 ? minutes + "m" : seconds + "s";
    }

    private String escapeMini(String value) {
        return value == null ? "Unknown" : value.replace("<", "").replace(">", "");
    }

    private void removeTavernLeaderboardDisplay(UUID id) {
        UUID entityId = tavernLeaderboardDisplays.remove(id);
        Entity entity = entityId == null ? null : Bukkit.getEntity(entityId);
        if (entity != null) entity.remove();
    }

    private void removePersistedLeaderboardDisplays(TavernLeaderboard board) {
        String boardId = board.id.toString();
        for (Entity entity : board.location.getWorld().getNearbyEntities(board.location, 4.0D, 16.0D, 4.0D)) {
            String storedId = entity.getPersistentDataContainer().get(leaderboardHologramKey, PersistentDataType.STRING);
            if (boardId.equals(storedId)) entity.remove();
        }
    }

    private void removeAllTavernLeaderboardDisplays() {
        for (World world : Bukkit.getWorlds()) for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
            if (display.getPersistentDataContainer().has(leaderboardHologramKey, PersistentDataType.STRING)) display.remove();
        }
        tavernLeaderboardDisplays.clear();
    }

    private void loadLeaderboards() {
        if (!leaderboardsFile.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(leaderboardsFile);
        unresolvedLeaderboardEntries.addAll(yaml.getStringList("leaderboards"));
        resolvePendingLeaderboards();
    }

    private void resolvePendingLeaderboards() {
        for (String raw : new ArrayList<>(unresolvedLeaderboardEntries)) {
            String[] p = raw.split("\\|");
            try {
                if (p.length != 8) throw new IllegalArgumentException("wrong field count");
                UUID id = UUID.fromString(p[0]);
                World world = Bukkit.getWorld(UUID.fromString(p[1]));
                TavernGame game = TavernGame.byId(p[7]);
                if (world == null) continue;
                if (game == null) throw new IllegalArgumentException("unknown game");
                tavernLeaderboards.put(id, new TavernLeaderboard(id,
                    new Location(world, Double.parseDouble(p[2]), Double.parseDouble(p[3]), Double.parseDouble(p[4]), Float.parseFloat(p[5]), Float.parseFloat(p[6])), game));
                unresolvedLeaderboardEntries.remove(raw);
            } catch (RuntimeException ignored) {
                unresolvedLeaderboardEntries.remove(raw);
                plugin.getLogger().warning("Skipped a malformed tavern leaderboard entry.");
            }
        }
    }

    private void requestLeaderboardSave() {
        if (leaderboardSaveTask != null || !plugin.isEnabled()) return;
        leaderboardSaveTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            leaderboardSaveTask = null;
            saveLeaderboardsNow();
        }, 20L);
    }

    private void saveLeaderboardsNow() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> serialized = new ArrayList<>(unresolvedLeaderboardEntries);
        serialized.addAll(tavernLeaderboards.values().stream().map(board -> String.join("|",
            board.id.toString(), board.location.getWorld().getUID().toString(), Double.toString(board.location.getX()), Double.toString(board.location.getY()),
            Double.toString(board.location.getZ()), Float.toString(board.location.getYaw()), Float.toString(board.location.getPitch()), board.game.id)).toList());
        yaml.set("leaderboards", serialized.stream().distinct().sorted().toList());
        try {
            AtomicYamlFile.save(yaml, leaderboardsFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save tavern leaderboards: " + ex.getMessage());
        }
    }

    private void loadStations() {
        if (!stationsFile.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stationsFile);
        for (StationType type : StationType.values()) {
            for (String raw : yaml.getStringList(type.id)) {
                BlockKey key = BlockKey.parse(raw);
                if (key != null) stations.get(type).add(key);
            }
        }
    }

    private void saveStations() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (StationType type : StationType.values()) yaml.set(type.id, stations.get(type).stream().map(BlockKey::serialize).sorted().toList());
        try { AtomicYamlFile.save(yaml, stationsFile); }
        catch (IOException ex) { plugin.getLogger().severe("Could not save tavern stations: " + ex.getMessage()); }
    }

    public enum StationType {
        SLOT_MACHINE("slots"), GAME_TABLE("table"), DARTBOARD("darts"), RUMOR_BOARD("rumors");
        private final String id;
        StationType(String id) { this.id = id; }
        public String id() { return id; }
        public static StationType byId(String id) {
            if (id == null) return null;
            for (StationType type : values()) if (type.id.equalsIgnoreCase(id)) return type;
            return null;
        }
    }

    public enum TavernGame {
        SLOTS("slots", "Slots"), CARDS("cards", "Crown & Casks"), DARTS("darts", "Darts"), ROULETTE("roulette", "Roulette");
        private final String id, display;
        TavernGame(String id, String display) { this.id = id; this.display = display; }
        public String id() { return id; }
        public static TavernGame byId(String id) {
            if (id == null) return null;
            for (TavernGame game : values()) if (game.id.equalsIgnoreCase(id)) return game;
            return null;
        }
    }

    private enum Drink {
        MEAD("mead", "Mead", "gold", Material.HONEY_BOTTLE, 5, 2, PotionEffectType.REGENERATION, 0, 300, "Regeneration I for 5m"),
        BERRY_CIDER("berry_cider", "Berry Cider", "light_purple", Material.SWEET_BERRIES, 5, 2, PotionEffectType.SPEED, 0, 720, "Speed I for 12m"),
        MOONSHINE("moonshine", "Moonshine", "white", Material.GLASS_BOTTLE, 6, 3, PotionEffectType.JUMP_BOOST, 1, 720, "Jump Boost II for 12m"),
        PHANTOM_RUM("phantom_rum", "Phantom Rum", "blue", Material.PHANTOM_MEMBRANE, 7, 3, PotionEffectType.SLOW_FALLING, 0, 900, "Slow Falling for 15m"),
        TIDEWATER_GROG("tidewater_grog", "Tidewater Grog", "dark_aqua", Material.KELP, 7, 3, PotionEffectType.WATER_BREATHING, 0, 1200, "Water Breathing for 20m"),
        FROSTWINE("frostwine", "Frostwine", "aqua", Material.SNOWBALL, 8, 4, PotionEffectType.FIRE_RESISTANCE, 0, 1200, "Fire Resistance for 20m"),
        DWARVEN_STOUT("dwarven_stout", "Dwarven Stout", "gray", Material.MILK_BUCKET, 8, 4, PotionEffectType.HASTE, 1, 600, "Haste II for 10m"),
        GOLDEN_ALE("golden_ale", "Golden Ale", "yellow", Material.GOLDEN_CARROT, 9, 4, PotionEffectType.ABSORPTION, 2, 900, "Absorption III for 15m"),
        SILK_GIN("silk_gin", "Spider-Silk Gin", "dark_gray", Material.FERMENTED_SPIDER_EYE, 10, 5, PotionEffectType.LUCK, 1, 1200, "Luck II for 20m"),
        IRONROOT_PORTER("ironroot_porter", "Ironroot Porter", "dark_green", Material.IRON_NUGGET, 11, 5, PotionEffectType.RESISTANCE, 0, 480, "Resistance I for 8m"),
        FIREWHISKY("firewhisky", "Firewhisky", "red", Material.POTION, 12, 6, PotionEffectType.STRENGTH, 0, 720, "Strength I for 12m"),
        HONEYHEART_BRANDY("honeyheart_brandy", "Honeyheart Brandy", "gold", Material.HONEYCOMB, 13, 7, PotionEffectType.HEALTH_BOOST, 2, 900, "Health Boost III for 15m"),
        VOID_ABSINTHE("void_absinthe", "Void Absinthe", "dark_purple", Material.DRAGON_BREATH, 15, 9, PotionEffectType.NIGHT_VISION, 0, 1800, "Night Vision for 30m");
        private final String id, displayName, color, buffDescription;
        private final Material material;
        private final int cost, amplifier;
        private final PotionEffectType buffType;
        private final int buffAmplifier, buffTicks;
        Drink(String id, String displayName, String color, Material material, int cost, int amplifier,
              PotionEffectType buffType, int buffAmplifier, int buffSeconds, String buffDescription) {
            this.id=id; this.displayName=displayName; this.color=color; this.material=material; this.cost=cost; this.amplifier=amplifier;
            this.buffType=buffType; this.buffAmplifier=buffAmplifier; this.buffTicks=buffSeconds * 20; this.buffDescription=buffDescription;
        }
        private static Drink byId(String id) { for (Drink drink : values()) if (drink.id.equals(id)) return drink; return null; }
    }

    private enum GamblerFood {
        SALTED_PRETZEL("salted_pretzel", "Salted Pretzel", "gold", Material.BREAD, 12, 0.025D),
        FORTUNE_PIE("fortune_pie", "Fortune Pie", "yellow", Material.PUMPKIN_PIE, 25, 0.05D),
        GILDED_PLATTER("gilded_platter", "Gilded Platter", "green", Material.COOKED_BEEF, 45, 0.075D),
        DRAGONS_FEAST("dragons_feast", "Dragon's Feast", "light_purple", Material.GOLDEN_CARROT, 80, 0.10D);

        private final String id, displayName, color;
        private final Material material;
        private final int cost;
        private final double chance;

        GamblerFood(String id, String displayName, String color, Material material, int cost, double chance) {
            this.id = id;
            this.displayName = displayName;
            this.color = color;
            this.material = material;
            this.cost = cost;
            this.chance = chance;
        }

        private static GamblerFood byId(String id) {
            for (GamblerFood food : values()) if (food.id.equals(id)) return food;
            return null;
        }
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {
        private static BlockKey of(Location location) { return new BlockKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ()); }
        private String serialize() { return worldId + ":" + x + ":" + y + ":" + z; }
        private Location location() {
            World world = Bukkit.getWorld(worldId);
            return world == null ? null : new Location(world, x, y, z);
        }
        private static BlockKey parse(String raw) {
            try { String[] p=raw.split(":"); return p.length==4 ? new BlockKey(UUID.fromString(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])) : null; }
            catch (RuntimeException ex) { return null; }
        }
    }

    private static final class TableLobby {
        private final Set<UUID> players = new LinkedHashSet<>(); private final SlotWager wager; private UUID hostId;
        private TableLobby(SlotWager wager, UUID hostId) { this.wager = wager; this.hostId = hostId; }
    }
    private static final class SlotSession {
        private final UUID playerId; private final Inventory inventory; private final Material material; private final int wager; private final int multiplier; private final List<EscrowedItem> materialEscrows;
        private final int lossOffset; private int step; private int lastStopped; private BukkitTask task;
        private SlotSession(UUID playerId, Inventory inventory, Material material, int wager, int multiplier, List<EscrowedItem> materialEscrows) { this.playerId=playerId; this.inventory=inventory; this.material=material; this.wager=wager; this.multiplier=multiplier; this.materialEscrows=materialEscrows == null ? List.of() : List.copyOf(materialEscrows); this.lossOffset=ThreadLocalRandom.current().nextInt(SLOT_SYMBOLS.size()); }
    }
    private static final class DartSession {
        private final UUID playerId; private final Inventory inventory; private int cursor; private int direction=1; private int throwsMade; private int total; private BukkitTask task;
        private boolean armed; private int armTicksRemaining; private int ticksUntilMove; private int moveDelayTicks; private int speedStepsRemaining;
        private int stepsSinceTurn; private int turnCooldown; private int prematureClicks; private long lastRapidClickWarningAt;
        private DartSession(UUID playerId, Inventory inventory) { this.playerId=playerId; this.inventory=inventory; }
    }
    private static final class CardMatch {
        private final UUID id; private final BlockKey table; private final SlotWager wager; private final Map<UUID,List<Integer>> hands = new HashMap<>(); private final Map<UUID,Integer> selections = new ConcurrentHashMap<>(); private final Map<UUID,String> contributors = new HashMap<>(); private final Map<UUID,List<EscrowedItem>> materialEscrows = new HashMap<>(); private boolean potSettled; private BukkitTask task;
        private CardMatch(UUID id, BlockKey table, SlotWager wager) { this.id=id; this.table=table; this.wager=wager; }
    }
    private record GameTimer(TavernGame game, String playerName, long startedAt) {}
    private record SlotWager(String currency, String amount) {}
    private record TavernLeaderboard(UUID id, Location location, TavernGame game) {}
    record DartStep(int cursor, int direction) {}
    private record TavernMenuHolder(UUID playerId) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder { @Override public Inventory getInventory() { return null; } }
}
