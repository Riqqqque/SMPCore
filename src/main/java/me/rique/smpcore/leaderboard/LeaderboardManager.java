package me.rique.smpcore.leaderboard;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.database.DatabaseManager;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class LeaderboardManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int MENU_SIZE = 54;
    private static final int BACK_SLOT = 49;
    private static final long PLAYTIME_FLUSH_INTERVAL_TICKS = 20L * 60L;
    private static final DateTimeFormatter REPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, h:mm a")
        .withZone(ZoneId.of("America/Denver"));

    private final SMPCore plugin;
    private final Map<UUID, Long> playtimeAnchors = new ConcurrentHashMap<>();
    private BukkitTask playtimeFlushTask;

    public LeaderboardManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            playtimeAnchors.put(player.getUniqueId(), now);
        }
        playtimeFlushTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushOnlinePlaytime, PLAYTIME_FLUSH_INTERVAL_TICKS, PLAYTIME_FLUSH_INTERVAL_TICKS);
    }

    public void shutdown() {
        if (playtimeFlushTask != null) {
            playtimeFlushTask.cancel();
            playtimeFlushTask = null;
        }

        long now = System.currentTimeMillis();
        List<CompletableFuture<Void>> saves = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            CompletableFuture<Void> save = flushPlayerPlaytime(player, now, false);
            if (save != null) {
                saves.add(save);
            }
        }
        playtimeAnchors.clear();

        if (!saves.isEmpty()) {
            try {
                CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new)).get(3, TimeUnit.SECONDS);
            } catch (Exception ex) {
                plugin.getLogger().warning("Timed out while saving playtime stats: " + ex.getMessage());
            }
        }
    }

    public void recordBossKill(Player killer, String bossId) {
        if (killer == null) {
            return;
        }
        increment(killer, LeaderboardType.BOSS_KILLS);
    }

    public void recordDuelWin(Player winner) {
        if (winner != null) increment(winner, LeaderboardType.DUEL_WINS);
    }

    public void recordDuelBetWin(Player winner) {
        recordDuelBetWins(winner, 1);
    }

    public void recordDuelBetWins(Player winner, int wins) {
        if (winner != null) recordDuelBetWins(winner.getUniqueId(), winner.getName(), wins);
    }

    public void recordDuelBetWins(UUID winnerId, String winnerName, int wins) {
        if (winnerId != null && wins > 0) {
            plugin.getDatabase().incrementLeaderboardStat(
                winnerId,
                winnerName == null || winnerName.isBlank() ? "Unknown" : winnerName,
                LeaderboardType.DUEL_BET_WINS.column,
                wins
            );
        }
    }

    public void recordBossFightParticipant(UUID playerUuid, String playerName, double damageDone) {
        if (playerUuid == null) {
            return;
        }
        String safeName = playerName == null || playerName.isBlank() ? "Unknown" : playerName;
        plugin.getDatabase().incrementLeaderboardStat(playerUuid, safeName, LeaderboardType.BOSS_FIGHTS.column, 1);
        int roundedDamage = (int) Math.max(0, Math.round(damageDone));
        if (roundedDamage > 0) {
            plugin.getDatabase().incrementLeaderboardStat(playerUuid, safeName, LeaderboardType.BOSS_DAMAGE.column, roundedDamage);
        }
    }

    public void openOverviewMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(
            new LeaderboardMenuHolder(null),
            MENU_SIZE,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#facc15:#22d3ee><bold>Leaderboards</bold></gradient>"), "Leaderboards")
        );
        decorate(inventory);
        inventory.setItem(4, item(Material.GOLD_BLOCK, "<gradient:#facc15:#22d3ee><bold>Leaderboards</bold></gradient>", List.of(
            "<gray>Track the players shaping the SMP.</gray>",
            "<gray>PvP, mobs, bosses, and fight reports</gray>",
            "<dark_gray>Saved to the plugin database.</dark_gray>"
        )));

        inventory.setItem(10, label(Material.DIAMOND_SWORD, "<gradient:#fb7185:#f97316><bold>Combat</bold></gradient>", List.of("<dark_gray>PvP and death stats</dark_gray>")));
        inventory.setItem(13, label(Material.CLOCK, "<gradient:#67e8f9:#facc15><bold>Activity</bold></gradient>", List.of("<dark_gray>Time spent online</dark_gray>")));
        inventory.setItem(16, label(Material.WITHER_SKELETON_SKULL, "<gradient:#a855f7:#22d3ee><bold>Bosses</bold></gradient>", List.of("<dark_gray>Boss contribution stats</dark_gray>")));
        inventory.setItem(29, label(Material.NETHERITE_SWORD, "<gradient:#ef4444:#f59e0b><bold>Duels</bold></gradient>", List.of("<dark_gray>Arena wins and successful bets</dark_gray>")));

        inventory.setItem(19, categoryItem(LeaderboardType.PLAYER_KILLS));
        inventory.setItem(20, categoryItem(LeaderboardType.DEATHS));
        inventory.setItem(21, categoryItem(LeaderboardType.MOB_KILLS));
        inventory.setItem(22, categoryItem(LeaderboardType.PLAYTIME));
        inventory.setItem(23, categoryItem(LeaderboardType.BOSS_KILLS));
        inventory.setItem(24, categoryItem(LeaderboardType.BOSS_DAMAGE));
        inventory.setItem(25, categoryItem(LeaderboardType.BOSS_FIGHTS));
        inventory.setItem(32, categoryItem(LeaderboardType.DUEL_WINS));
        inventory.setItem(33, categoryItem(LeaderboardType.DUEL_BET_WINS));
        inventory.setItem(40, item(Material.WRITABLE_BOOK, "<gradient:#fb7185:#facc15><bold>My Boss Reports</bold></gradient>", List.of(
            "<gray>Review your recent boss fights, damage,</gray>",
            "<gray>healing received, outcome, and rank.</gray>",
            "<yellow>Click to view.</yellow>"
        )));
        inventory.setItem(BACK_SLOT, item(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to /menu.</gray>")));
        player.openInventory(inventory);
    }

    public void openMyBossReportsMenu(Player player) {
        Inventory loading = Bukkit.createInventory(
            new BossReportMenuHolder(),
            MENU_SIZE,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#fb7185:#facc15><bold>My Boss Reports</bold></gradient>"), "My Boss Reports")
        );
        decorate(loading);
        loading.setItem(22, item(Material.CLOCK, "<yellow>Loading...</yellow>", List.of("<gray>Reading your recent boss fights.</gray>")));
        loading.setItem(BACK_SLOT, item(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to leaderboards.</gray>")));
        player.openInventory(loading);

        plugin.getDatabase().loadPlayerBossFightReports(player.getUniqueId(), 21).whenComplete((entries, throwable) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (!(player.getOpenInventory().getTopInventory().getHolder(false) instanceof BossReportMenuHolder)) {
                    return;
                }
                if (throwable != null) {
                    player.sendMessage(MessageUtil.error("Could not load your boss reports right now."));
                    openOverviewMenu(player);
                    return;
                }

                Inventory inventory = Bukkit.createInventory(
                    new BossReportMenuHolder(),
                    MENU_SIZE,
                    BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#fb7185:#facc15><bold>My Boss Reports</bold></gradient>"), "My Boss Reports")
                );
                decorate(inventory);
                inventory.setItem(4, item(Material.WRITABLE_BOOK, "<gradient:#fb7185:#facc15><bold>My Boss Reports</bold></gradient>", List.of(
                    "<gray>Your recent custom boss after-action reports.</gray>",
                    "<dark_gray>Damage and healing are saved when fights end.</dark_gray>"
                )));

                int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
                for (int i = 0; i < slots.length; i++) {
                    if (i >= entries.size()) {
                        inventory.setItem(slots[i], item(Material.PAPER, "<dark_gray>No Report</dark_gray>", List.of("<gray>Finish a boss fight to fill this slot.</gray>")));
                        continue;
                    }
                    inventory.setItem(slots[i], bossReportItem(entries.get(i)));
                }
                inventory.setItem(BACK_SLOT, item(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to leaderboards.</gray>")));
                player.openInventory(inventory);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            });
        });
    }

    public void openLeaderboardMenu(Player player, LeaderboardType type) {
        if (type == null) {
            openOverviewMenu(player);
            return;
        }

        Inventory loading = Bukkit.createInventory(
            new LeaderboardMenuHolder(type),
            MENU_SIZE,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#facc15:#22d3ee><bold>" + type.title + "</bold></gradient>"), type.title)
        );
        decorate(loading);
        loading.setItem(22, item(Material.CLOCK, "<yellow>Loading...</yellow>", List.of("<gray>Reading saved stats.</gray>")));
        loading.setItem(BACK_SLOT, item(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to leaderboards.</gray>")));
        player.openInventory(loading);

        CompletableFuture<List<DatabaseManager.LeaderboardEntry>> entriesFuture = (type == LeaderboardType.PLAYTIME
            ? flushOnlinePlaytimeAsync()
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Could not flush live playtime before loading leaderboard: " + throwable.getMessage());
                    return null;
                })
            : CompletableFuture.completedFuture(null))
            .thenCompose(ignored -> plugin.getDatabase().loadLeaderboard(type.column, 10));

        entriesFuture.whenComplete((entries, throwable) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (!(player.getOpenInventory().getTopInventory().getHolder(false) instanceof LeaderboardMenuHolder current)
                    || current.type() != type) {
                    return;
                }
                if (throwable != null) {
                    player.sendMessage(MessageUtil.error("Could not load leaderboards right now."));
                    openOverviewMenu(player);
                    return;
                }
                Inventory inventory = Bukkit.createInventory(
                    new LeaderboardMenuHolder(type),
                    MENU_SIZE,
                    BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#facc15:#22d3ee><bold>" + type.title + "</bold></gradient>"), type.title)
                );
                decorate(inventory);
                inventory.setItem(4, item(type.icon, "<gradient:#facc15:#22d3ee><bold>" + type.title + "</bold></gradient>", List.of(
                    type.description,
                    "<dark_gray>Top 10 saved players.</dark_gray>"
                )));
                inventory.setItem(12, label(Material.GOLD_BLOCK, "<gold><bold>Top Three</bold></gold>", List.of("<gray>The current podium.</gray>")));
                inventory.setItem(37, label(Material.PAPER, "<aqua><bold>Chasers</bold></aqua>", List.of("<gray>The rest of the board.</gray>")));
                int[] slots = {13, 21, 23, 28, 29, 30, 31, 32, 33, 34};
                for (int i = 0; i < slots.length; i++) {
                    if (i >= entries.size()) {
                        inventory.setItem(slots[i], item(Material.PAPER, "<dark_gray>#" + (i + 1) + " Empty</dark_gray>", List.of("<gray>No stat recorded yet.</gray>")));
                        continue;
                    }
                    DatabaseManager.LeaderboardEntry entry = entries.get(i);
                    inventory.setItem(slots[i], leaderboardEntryItem(i + 1, entry, type));
                }
                inventory.setItem(BACK_SLOT, item(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to leaderboards.</gray>")));
                player.openInventory(inventory);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            });
        });
    }

    public void sendPlaytime(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        CompletableFuture<Void> flush = flushPlayerPlaytime(player, System.currentTimeMillis(), true);
        if (flush == null) {
            flush = CompletableFuture.completedFuture(null);
        }
        flush
            .exceptionally(throwable -> {
                plugin.getLogger().warning("Could not flush live playtime before /playtime: " + throwable.getMessage());
                return null;
            })
            .thenCompose(ignored -> plugin.getDatabase().loadLeaderboardStat(playerId, LeaderboardType.PLAYTIME.column))
            .whenComplete((storedSeconds, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (throwable != null) {
                    player.sendMessage(MessageUtil.error("Could not load your playtime right now."));
                    return;
                }
                long totalSeconds = Math.max(0L, storedSeconds == null ? 0L : storedSeconds);
                player.sendMessage(MessageUtil.info("Your playtime: <white>" + formatPlaytimeSeconds(totalSeconds) + "</white>."));
            }));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        playtimeAnchors.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        flushPlayerPlaytime(event.getPlayer(), System.currentTimeMillis(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (plugin.getDuelManager() != null && plugin.getDuelManager().isDuelParticipant(player)) {
            return;
        }
        increment(player, LeaderboardType.DEATHS);

        Player killer = player.getKiller();
        if (killer != null && !killer.equals(player)) {
            increment(killer, LeaderboardType.PLAYER_KILLS);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }
        if (plugin.getBossManager() != null && plugin.getBossManager().isCustomBoss(entity)) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            increment(killer, LeaderboardType.MOB_KILLS);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder rawHolder = top.getHolder(false);
        if (rawHolder instanceof BossReportMenuHolder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                if (event.getClick() != org.bukkit.event.inventory.ClickType.LEFT
                    && event.getClick() != org.bukkit.event.inventory.ClickType.RIGHT) {
                    return;
                }
                int rawSlot = event.getRawSlot();
                if (rawSlot >= 0 && rawSlot < top.getSize() && rawSlot == BACK_SLOT
                    && MenuItemUtil.isVisibleItem(event.getCurrentItem())) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            openOverviewMenu(player);
                        }
                    });
                }
            }
            return;
        }
        if (!(rawHolder instanceof LeaderboardMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClick() != org.bukkit.event.inventory.ClickType.LEFT
            && event.getClick() != org.bukkit.event.inventory.ClickType.RIGHT) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return;
        }
        if (!MenuItemUtil.isVisibleItem(event.getCurrentItem())) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> handleLeaderboardMenuClick(player, holder, rawSlot));
    }

    private void handleLeaderboardMenuClick(Player player, LeaderboardMenuHolder holder, int rawSlot) {
        if (!player.isOnline()) {
            return;
        }
        if (rawSlot == BACK_SLOT) {
            if (holder.type() == null) {
                player.closeInventory();
                player.performCommand("menu");
            } else {
                openOverviewMenu(player);
            }
            return;
        }

        if (holder.type() != null) {
            return;
        }

        LeaderboardType type = switch (rawSlot) {
            case 19 -> LeaderboardType.PLAYER_KILLS;
            case 20 -> LeaderboardType.DEATHS;
            case 21 -> LeaderboardType.MOB_KILLS;
            case 22 -> LeaderboardType.PLAYTIME;
            case 23 -> LeaderboardType.BOSS_KILLS;
            case 24 -> LeaderboardType.BOSS_DAMAGE;
            case 25 -> LeaderboardType.BOSS_FIGHTS;
            case 32 -> LeaderboardType.DUEL_WINS;
            case 33 -> LeaderboardType.DUEL_BET_WINS;
            default -> null;
        };
        if (type != null) {
            openLeaderboardMenu(player, type);
        } else if (rawSlot == 40) {
            openMyBossReportsMenu(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof LeaderboardMenuHolder || holder instanceof BossReportMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void increment(Player player, LeaderboardType type) {
        if (player == null || type == null) {
            return;
        }
        plugin.getDatabase().incrementLeaderboardStat(player.getUniqueId(), player.getName(), type.column, 1);
    }

    private void flushOnlinePlaytime() {
        flushOnlinePlaytimeAsync();
    }

    private CompletableFuture<Void> flushOnlinePlaytimeAsync() {
        long now = System.currentTimeMillis();
        List<CompletableFuture<Void>> saves = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            CompletableFuture<Void> save = flushPlayerPlaytime(player, now, true);
            if (save != null) {
                saves.add(save);
            }
        }
        return saves.isEmpty() ? CompletableFuture.completedFuture(null) : CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Void> flushPlayerPlaytime(Player player, long now, boolean keepTracking) {
        if (player == null) {
            return null;
        }
        UUID playerId = player.getUniqueId();
        Long previous = keepTracking ? playtimeAnchors.put(playerId, now) : playtimeAnchors.remove(playerId);
        if (previous == null) {
            return null;
        }
        long seconds = Math.max(0L, (now - previous) / 1000L);
        if (seconds <= 0L) {
            return null;
        }
        int safeSeconds = (int) Math.min(Integer.MAX_VALUE, seconds);
        return plugin.getDatabase().incrementLeaderboardStat(playerId, player.getName(), LeaderboardType.PLAYTIME.column, safeSeconds);
    }

    public long liveSessionSeconds(UUID playerId, long now) {
        if (playerId == null) {
            return 0L;
        }
        Long anchor = playtimeAnchors.get(playerId);
        if (anchor == null) {
            return 0L;
        }
        return Math.max(0L, (now - anchor) / 1000L);
    }

    private ItemStack categoryItem(LeaderboardType type) {
        return item(type.icon, type.gradientTitle(), List.of(
            type.description,
            "<dark_gray>" + type.valueLabel + " leaderboard.</dark_gray>",
            "<yellow>Click to view top players.</yellow>"
        ));
    }

    private ItemStack leaderboardEntryItem(int rank, DatabaseManager.LeaderboardEntry entry, LeaderboardType type) {
        Material material = switch (rank) {
            case 1 -> Material.NETHER_STAR;
            case 2 -> Material.GOLD_INGOT;
            case 3 -> Material.IRON_INGOT;
            default -> Material.PAPER;
        };
        String name = entry.playerName() == null || entry.playerName().isBlank()
            ? playerName(entry.playerUuid())
            : entry.playerName();
        return item(material, rankTitle(rank, name), List.of(
            rankSubtitle(rank),
            "<gray>" + type.valueLabel + ": <white>" + formatLeaderboardValue(entry.value(), type) + "</white></gray>",
            rank == 1 ? "<gold>Current leader.</gold>" : "<dark_gray>Keep climbing.</dark_gray>"
        ));
    }

    private ItemStack bossReportItem(DatabaseManager.BossFightMenuEntry entry) {
        Material material = "victory".equalsIgnoreCase(entry.outcome()) ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        String bossName = plugin.getBossManager() == null
            ? prettyBossName(entry.bossId())
            : plugin.getBossManager().displayNameForBoss(entry.bossId());
        String outcome = "victory".equalsIgnoreCase(entry.outcome()) ? "<green>Victory</green>" : "<red>Failure</red>";
        return item(material, outcome + " <white>" + escape(bossName) + "</white>", List.of(
            "<gray>Ended:</gray> <white>" + REPORT_TIME_FORMAT.format(Instant.ofEpochMilli(entry.endedAt())) + "</white>",
            "<gray>Duration:</gray> <white>" + formatDuration(entry.durationMs()) + "</white>",
            "<gray>Your Rank:</gray> <white>#" + Math.max(1, entry.rank()) + "</white>",
            "<gray>Your Damage:</gray> <white>" + trim(entry.damageDone()) + "</white>",
            "<gray>Your Healing Received:</gray> <white>" + trim(entry.healingReceived()) + "</white>",
            "<gray>Total Fight Damage:</gray> <white>" + trim(entry.totalDamage()) + "</white>",
            entry.doubleDrops() ? "<gold>Veil Wisp doubled the loot.</gold>" : "<dark_gray>Normal boss rewards.</dark_gray>"
        ));
    }

    private String playerName(UUID playerUuid) {
        if (playerUuid == null) {
            return "Unknown";
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);
        return offlinePlayer.getName() == null ? "Unknown" : offlinePlayer.getName();
    }

    private void decorate(Inventory inventory) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, MenuItemUtil.INACTIVE_SLOT_LORE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isFrameSlot(slot, inventory.getSize())) {
                inventory.setItem(slot, filler);
            }
        }
    }

    private boolean isFrameSlot(int slot, int size) {
        return slot < 9 || slot >= size - 9 || slot % 9 == 0 || slot % 9 == 8;
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(MM.deserialize(MenuItemUtil.visibleMiniName(name)));
        meta.lore(MenuItemUtil.visibleMiniLore(name, lore).stream().map(MM::deserialize).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack label(Material material, String name, List<String> lore) {
        return item(material, name, lore);
    }

    private String rankTitle(int rank, String name) {
        return switch (rank) {
            case 1 -> "<gradient:#facc15:#f97316><bold>#1 " + escape(name) + "</bold></gradient>";
            case 2 -> "<gradient:#e5e7eb:#94a3b8><bold>#2 " + escape(name) + "</bold></gradient>";
            case 3 -> "<gradient:#f59e0b:#92400e><bold>#3 " + escape(name) + "</bold></gradient>";
            default -> "<aqua><bold>#" + rank + "</bold></aqua> <white>" + escape(name) + "</white>";
        };
    }

    private String rankSubtitle(int rank) {
        return switch (rank) {
            case 1 -> "<gold>Champion slot.</gold>";
            case 2 -> "<gray>Runner-up.</gray>";
            case 3 -> "<#cd7f32>Third place.</#cd7f32>";
            default -> "<gray>Ranked contender.</gray>";
        };
    }

    private String escape(String raw) {
        return raw.replace("\\", "\\\\").replace("<", "\\<");
    }

    private String prettyBossName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Unknown";
        }
        String[] parts = raw.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
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

    private String formatDuration(long durationMs) {
        long seconds = Math.max(0L, durationMs / 1000L);
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        return minutes + "m " + remainingSeconds + "s";
    }

    private String formatLeaderboardValue(long value, LeaderboardType type) {
        return type == LeaderboardType.PLAYTIME ? formatPlaytimeSeconds(value) : Long.toString(value);
    }

    private String formatPlaytimeSeconds(long totalSeconds) {
        long seconds = Math.max(0L, totalSeconds);
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        seconds %= 60L;

        if (days > 0L) {
            return days + "d " + hours + "h " + minutes + "m";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    private String trim(double value) {
        if (Math.rint(value) == value) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }

    public enum LeaderboardType {
        PLAYER_KILLS("player_kills", "Player Kills", Material.DIAMOND_SWORD, "<gray>Most player kills recorded by SMPCore.</gray>", "Kills"),
        DEATHS("deaths", "Deaths", Material.SKELETON_SKULL, "<gray>Most deaths recorded by SMPCore.</gray>", "Deaths"),
        BOSS_KILLS("boss_kills", "Boss Kills", Material.WITHER_SKELETON_SKULL, "<gray>Most custom boss kills.</gray>", "Boss Kills"),
        BOSS_DAMAGE("boss_damage", "Boss Damage", Material.NETHERITE_AXE, "<gray>Most total damage dealt to custom bosses.</gray>", "Damage"),
        BOSS_FIGHTS("boss_fights", "Boss Fights", Material.SHIELD, "<gray>Most custom boss fights participated in.</gray>", "Fights"),
        MOB_KILLS("mob_kills", "Mob Kills", Material.ZOMBIE_HEAD, "<gray>Most non-player mobs killed.</gray>", "Mob Kills"),
        PLAYTIME("playtime_seconds", "Playtime", Material.CLOCK, "<gray>Most time spent online.</gray>", "Time"),
        DUEL_WINS("duel_wins", "Duel Wins", Material.NETHERITE_SWORD, "<gray>Most Veilward arena match wins.</gray>", "Wins"),
        DUEL_BET_WINS("duel_bet_wins", "Duel Bets Won", Material.GOLD_INGOT, "<gray>Most profitable duel bets won.</gray>", "Bets");

        private final String column;
        private final String title;
        private final Material icon;
        private final String description;
        private final String valueLabel;

        LeaderboardType(String column, String title, Material icon, String description, String valueLabel) {
            this.column = column;
            this.title = title;
            this.icon = icon;
            this.description = description;
            this.valueLabel = valueLabel;
        }

        public static LeaderboardType fromInput(String input) {
            if (input == null || input.isBlank()) {
                return null;
            }
            String normalized = input.toLowerCase(Locale.ROOT).replace("-", "_");
            for (LeaderboardType type : values()) {
                if (type.column.equals(normalized) || type.title.toLowerCase(Locale.ROOT).replace(" ", "_").equals(normalized)) {
                    return type;
                }
            }
            return switch (normalized) {
                case "kills", "pkills", "playerkills" -> PLAYER_KILLS;
                case "bosses", "bosskills" -> BOSS_KILLS;
                case "bossdamage", "damage" -> BOSS_DAMAGE;
                case "bossfights", "fights" -> BOSS_FIGHTS;
                case "mobs", "mobkills" -> MOB_KILLS;
                case "playtime", "time", "timeplayed", "time_played" -> PLAYTIME;
                case "duelwins", "duels", "arena" -> DUEL_WINS;
                case "duelbetwins", "betwins", "bets" -> DUEL_BET_WINS;
                default -> null;
            };
        }

        public String column() {
            return column;
        }

        private String gradientTitle() {
            return switch (this) {
                case PLAYER_KILLS -> "<gradient:#fb7185:#f97316><bold>Player Kills</bold></gradient>";
                case DEATHS -> "<gradient:#94a3b8:#f8fafc><bold>Deaths</bold></gradient>";
                case BOSS_KILLS -> "<gradient:#a855f7:#ef4444><bold>Boss Kills</bold></gradient>";
                case BOSS_DAMAGE -> "<gradient:#ef4444:#facc15><bold>Boss Damage</bold></gradient>";
                case BOSS_FIGHTS -> "<gradient:#38bdf8:#a78bfa><bold>Boss Fights</bold></gradient>";
                case MOB_KILLS -> "<gradient:#4ade80:#22c55e><bold>Mob Kills</bold></gradient>";
                case PLAYTIME -> "<gradient:#67e8f9:#facc15><bold>Playtime</bold></gradient>";
                case DUEL_WINS -> "<gradient:#ef4444:#f59e0b><bold>Duel Wins</bold></gradient>";
                case DUEL_BET_WINS -> "<gradient:#facc15:#22c55e><bold>Duel Bets Won</bold></gradient>";
            };
        }
    }

    private record LeaderboardMenuHolder(LeaderboardType type) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record BossReportMenuHolder() implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
