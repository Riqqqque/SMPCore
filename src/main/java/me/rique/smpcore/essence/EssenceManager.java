package me.rique.smpcore.essence;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.command.MainMenuCommand;
import me.rique.smpcore.database.DatabaseManager;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.text.NumberFormat;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class EssenceManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance(Locale.US);
    private static final long MAX_BALANCE = 9_999_999_999L;
    private static final long PLACED_BLOCK_TTL_MILLIS = 20L * 60L * 1000L;
    private static final int MAX_PLACED_BLOCK_CACHE = 120_000;
    private static final long PLACED_BLOCK_SAVE_DELAY_TICKS = 20L;

    private final SMPCore plugin;
    private final Map<UUID, Account> accounts = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> accountLoads = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Boolean>> accountSaveChains = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Void>> offlineAdminChains = new ConcurrentHashMap<>();
    private final Map<BlockKey, Long> placedBlocks = new ConcurrentHashMap<>();
    private final Set<UUID> pendingBalanceMenuRefreshes = ConcurrentHashMap.newKeySet();
    private final File placedBlocksFile;
    private final Object placedBlocksSaveLock = new Object();
    private CompletableFuture<Void> placedBlocksSaveChain = CompletableFuture.completedFuture(null);
    private BukkitTask flushTask;
    private BukkitTask cleanupTask;
    private BukkitTask placedBlocksSaveTask;
    private volatile boolean shuttingDown;

    public EssenceManager(SMPCore plugin) {
        this.plugin = plugin;
        this.placedBlocksFile = new File(plugin.getDataFolder(), "essence-placed-blocks.yml");
    }

    public void start() {
        loadPlacedBlocks();
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadAccount(player);
        }
        int flushSeconds = Math.max(10, plugin.getConfigManager().normalEssenceFlushIntervalSeconds);
        flushTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> flushDirtyAccounts(), 20L * flushSeconds, 20L * flushSeconds);
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupPlacedBlocks, 20L * 60L, 20L * 60L);
    }

    public void shutdown() {
        shuttingDown = true;
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        if (placedBlocksSaveTask != null) {
            placedBlocksSaveTask.cancel();
            placedBlocksSaveTask = null;
        }
        awaitAll(List.copyOf(accountLoads.values()), "Essence account loads");
        awaitAll(List.copyOf(offlineAdminChains.values()), "offline Essence admin changes");

        for (int attempt = 0; attempt < 2; attempt++) {
            awaitAll(flushDirtyAccounts(), "Essence account saves");
            if (accounts.values().stream().noneMatch(account -> account.dirty)) {
                break;
            }
        }

        accountSaveChains.entrySet().removeIf(entry -> entry.getValue().isDone());
        long unsaved = accounts.entrySet().stream()
            .filter(entry -> entry.getValue().dirty
                || entry.getValue().loading
                || accountSaveChains.containsKey(entry.getKey()))
            .count();
        if (unsaved == 0L && accountSaveChains.isEmpty()) {
            accounts.clear();
        } else {
            plugin.getLogger().severe("Keeping " + unsaved + " unsaved Essence account(s) in memory after shutdown flush failure.");
        }
        awaitAll(List.of(queuePlacedBlocksSave()), "Essence block provenance save");
        placedBlocks.clear();
        pendingBalanceMenuRefreshes.clear();
    }

    public long balance(Player player) {
        if (player == null) {
            return 0L;
        }
        Account account = account(player);
        synchronized (account) {
            return account.balance;
        }
    }

    public boolean isLoaded(Player player) {
        if (player == null) {
            return false;
        }
        Account account = accounts.get(player.getUniqueId());
        return account != null && account.loaded;
    }

    public long lifetimeEarned(Player player) {
        if (player == null) {
            return 0L;
        }
        Account account = account(player);
        synchronized (account) {
            return account.lifetimeEarned;
        }
    }

    public int miningProgress(Player player) {
        if (player == null) {
            return 0;
        }
        Account account = account(player);
        synchronized (account) {
            return account.miningProgress;
        }
    }

    public int mobProgress(Player player) {
        if (player == null) {
            return 0;
        }
        Account account = account(player);
        synchronized (account) {
            return account.mobProgress;
        }
    }

    public int xpProgress(Player player) {
        if (player == null) {
            return 0;
        }
        Account account = account(player);
        synchronized (account) {
            return account.xpProgress;
        }
    }

    public boolean isRecentlyPlayerPlaced(Block block) {
        BlockKey key = BlockKey.of(block);
        Long expiresAt = key == null ? null : placedBlocks.get(key);
        return expiresAt != null && expiresAt > System.currentTimeMillis();
    }

    public String formattedBalance(Player player) {
        return NUMBERS.format(balance(player));
    }

    public String formatted(long amount) {
        return NUMBERS.format(Math.max(0L, amount));
    }

    public CompletableFuture<List<String>> suggestPlayerNames(String prefix) {
        String remaining = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        Set<String> names = new LinkedHashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(remaining)) {
                names.add(player.getName());
            }
        }
        return plugin.getDatabase().suggestEssenceAccountNames(remaining, 30)
            .thenApply(storedNames -> {
                for (String storedName : storedNames) {
                    if (storedName != null && storedName.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                        names.add(storedName);
                    }
                }
                return List.copyOf(names);
            })
            .exceptionally(ex -> {
                plugin.getLogger().warning("Could not suggest Essence accounts: " + ex.getMessage());
                return List.copyOf(names);
            });
    }

    public CompletableFuture<EssenceSnapshot> snapshot(String targetName) {
        Player online = onlinePlayer(targetName);
        if (online != null) {
            return CompletableFuture.completedFuture(snapshot(online));
        }
        return plugin.getDatabase().loadEssenceAccountByName(targetName)
            .thenApply(record -> record.map(value -> snapshot(value, false)).orElse(null));
    }

    public EssenceSnapshot snapshot(Player player) {
        Account account = account(player);
        synchronized (account) {
            return new EssenceSnapshot(
                player.getUniqueId(),
                player.getName(),
                account.balance,
                account.lifetimeEarned,
                account.miningProgress,
                account.mobProgress,
                account.xpProgress,
                true
            );
        }
    }

    public void sendBalance(Player player) {
        if (player == null) {
            return;
        }
        Account account = account(player);
        long balance;
        long lifetimeEarned;
        synchronized (account) {
            balance = account.balance;
            lifetimeEarned = account.lifetimeEarned;
        }
        player.sendMessage(MessageUtil.info(
            "Essence: <white>" + NUMBERS.format(balance) + "</white>"
                + " <dark_gray>| Lifetime: " + NUMBERS.format(lifetimeEarned) + "</dark_gray>"
        ));
    }

    public void addByAdmin(Player target, long amount, CommandSender actor) {
        if (target == null || amount <= 0L) {
            return;
        }
        AdminChangeResult result = applyOnlineAdminChange(target, amount, AdminEssenceOperation.ADD);
        flushAccount(target.getUniqueId());
        target.sendMessage(MessageUtil.success("You received <white>" + NUMBERS.format(Math.max(0L, result.applied())) + "</white> Essence."));
        if (actor != null && !actor.equals(target)) {
            actor.sendMessage(MessageUtil.success("Gave <white>" + NUMBERS.format(Math.max(0L, result.applied())) + "</white> Essence to <white>" + target.getName() + "</white>."));
        }
    }

    public void removeByAdmin(Player target, long amount, CommandSender actor) {
        if (target == null || amount <= 0L) {
            return;
        }
        AdminChangeResult result = applyOnlineAdminChange(target, amount, AdminEssenceOperation.REMOVE);
        flushAccount(target.getUniqueId());
        long removed = Math.abs(result.applied());
        target.sendMessage(MessageUtil.warn("<white>" + NUMBERS.format(removed) + "</white> Essence was removed."));
        if (actor != null && !actor.equals(target)) {
            actor.sendMessage(MessageUtil.success("Removed <white>" + NUMBERS.format(removed) + "</white> Essence from <white>" + target.getName() + "</white>."));
        }
    }

    public void setByAdmin(Player target, long amount, CommandSender actor) {
        if (target == null) {
            return;
        }
        AdminChangeResult result = applyOnlineAdminChange(target, amount, AdminEssenceOperation.SET);
        flushAccount(target.getUniqueId());
        target.sendMessage(MessageUtil.info("Your Essence is now <white>" + NUMBERS.format(result.after()) + "</white>."));
        if (actor != null && !actor.equals(target)) {
            actor.sendMessage(MessageUtil.success("Set <white>" + target.getName() + "</white>'s Essence to <white>" + NUMBERS.format(result.after()) + "</white>."));
        }
    }

    public CompletableFuture<AdminChangeResult> addByAdmin(String targetName, long amount) {
        return applyAdminChange(targetName, amount, AdminEssenceOperation.ADD);
    }

    public CompletableFuture<AdminChangeResult> removeByAdmin(String targetName, long amount) {
        return applyAdminChange(targetName, amount, AdminEssenceOperation.REMOVE);
    }

    public CompletableFuture<AdminChangeResult> setByAdmin(String targetName, long amount) {
        return applyAdminChange(targetName, amount, AdminEssenceOperation.SET);
    }

    public CompletableFuture<AdminChangeResult> resetByAdmin(String targetName) {
        return applyAdminChange(targetName, 0L, AdminEssenceOperation.RESET);
    }

    public boolean spend(Player player, long amount, String reason) {
        if (player == null || amount <= 0L) {
            return false;
        }
        Account account = account(player);
        long after;
        String storedPlayerName;
        synchronized (account) {
            if (account.balance < amount) {
                return false;
            }
            account.playerName = player.getName();
            account.balance -= amount;
            after = account.balance;
            if (!account.loaded && account.pendingAbsoluteBalance != null) {
                account.pendingAbsoluteBalance = after;
            }
            markDirty(account);
            storedPlayerName = account.playerName;
        }
        plugin.getDatabase().logEssenceTransaction(
            player.getUniqueId(),
            storedPlayerName,
            -amount,
            reason == null || reason.isBlank() ? "spend" : reason,
            after
        );
        flushAccount(player.getUniqueId());
        queueBalanceMenuRefresh(player.getUniqueId());
        return true;
    }

    public boolean credit(Player player, long amount, String reason) {
        if (player == null || amount <= 0L) {
            return false;
        }
        long added = changeBalance(player, amount, reason == null || reason.isBlank() ? "reward" : reason, true);
        flushAccount(player.getUniqueId());
        return added == amount;
    }

    public boolean canCreditFully(Player player, long amount) {
        if (player == null || amount <= 0L) {
            return false;
        }
        Account account = account(player);
        synchronized (account) {
            return account.loaded && account.balance <= MAX_BALANCE - amount;
        }
    }

    public long refund(Player player, long amount, String reason) {
        if (player == null || amount <= 0L) {
            return 0L;
        }
        Account account = account(player);
        long refunded = changeAccountBalance(
            account,
            player.getUniqueId(),
            player.getName(),
            amount,
            reason == null || reason.isBlank() ? "refund" : reason,
            false
        );
        flushAccount(player.getUniqueId());
        if (refunded != 0L) {
            queueBalanceMenuRefresh(player.getUniqueId());
        }
        return Math.max(0L, refunded);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        loadAccount(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        pendingBalanceMenuRefreshes.remove(playerId);
        Account account = accounts.get(playerId);
        if (account == null) {
            return;
        }
        synchronized (account) {
            account.removeWhenSaved = true;
        }
        CompletableFuture<Boolean> save = flushAccount(playerId);
        if (save == null) {
            removeAccountIfSaved(playerId, account);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (miningPoints(block.getType()) <= 0) {
            return;
        }
        BlockKey key = BlockKey.of(block);
        if (key != null) {
            placedBlocks.put(key, System.currentTimeMillis() + PLACED_BLOCK_TTL_MILLIS);
            savePlacedBlocks();
            if (placedBlocks.size() > MAX_PLACED_BLOCK_CACHE) {
                cleanupPlacedBlocks();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        BlockKey key = BlockKey.of(block);
        boolean wasPlaced = key != null && placedBlocks.remove(key) != null;
        if (wasPlaced) {
            savePlacedBlocks();
        }
        if (!eligible(player) || wasPlaced) {
            return;
        }
        int points = miningPoints(block.getType());
        if (points > 0) {
            addProgress(player, Source.MINING, points);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        movePlacedBlocks(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        movePlacedBlocks(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        removePlacedBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        removePlacedBlocks(event.blockList());
    }

    private void removePlacedBlocks(List<Block> blocks) {
        boolean changed = false;
        for (Block block : blocks) {
            BlockKey key = BlockKey.of(block);
            changed |= key != null && placedBlocks.remove(key) != null;
        }
        if (changed) {
            savePlacedBlocks();
        }
    }

    private void movePlacedBlocks(List<Block> blocks, org.bukkit.block.BlockFace direction) {
        if (blocks == null || direction == null || blocks.isEmpty()) {
            return;
        }
        Map<BlockKey, Long> moved = new LinkedHashMap<>();
        for (Block block : blocks) {
            BlockKey source = BlockKey.of(block);
            Long expiresAt = source == null ? null : placedBlocks.remove(source);
            if (expiresAt != null) {
                moved.put(BlockKey.of(block.getRelative(direction)), expiresAt);
            }
        }
        if (!moved.isEmpty()) {
            placedBlocks.putAll(moved);
            savePlacedBlocks();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onXpGain(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        if (!eligible(player) || event.getAmount() <= 0) {
            return;
        }
        addProgress(player, Source.XP, Math.min(event.getAmount(), 5000));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent event) {
        if (event.getEntityType() == EntityType.PLAYER) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (!eligible(killer)) {
            return;
        }
        int points = mobKillPoints(event.getEntityType());
        if (points > 0) {
            addProgress(killer, Source.MOBS, points);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        if (!eligible(killer) || killer.equals(victim)) {
            return;
        }
        if (plugin.getTeamManager() != null && plugin.getTeamManager().sameTeam(killer.getUniqueId(), victim.getUniqueId())) {
            return;
        }
        int payout = adjustedDirectEssencePayout(killer, plugin.getConfigManager().normalEssencePlayerKillPayout);
        if (payout <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long cooldownMillis = Math.max(0L, plugin.getConfigManager().normalEssencePlayerKillVictimCooldownSeconds) * 1000L;
        plugin.getDatabase().claimEssencePvpReward(killer.getUniqueId(), victim.getUniqueId(), now, cooldownMillis)
            .thenAccept(allowed -> {
                if (!allowed) {
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (killer.isOnline()) {
                        changeBalance(killer, payout, "player_kill", true);
                    }
                });
            })
            .exceptionally(ex -> {
                plugin.getLogger().severe("Could not claim Essence PvP reward: " + ex.getMessage());
                return null;
            });
    }

    private void addProgress(Player player, Source source, int points) {
        if (points <= 0) {
            return;
        }
        Account account = account(player);
        int threshold = adjustedEssenceThreshold(player, source.threshold(plugin));
        int payout = source.payout(plugin);
        if (threshold <= 0 || payout <= 0) {
            return;
        }

        int grants;
        synchronized (account) {
            int progress = source.progress(account) + points;
            grants = progress / threshold;
            progress %= threshold;
            source.progress(account, progress);
            markDirty(account);
        }

        if (grants > 0) {
            changeBalance(player, (long) grants * payout, source.reason, true);
        }
    }

    private int adjustedEssenceThreshold(Player player, int threshold) {
        if (threshold <= 1 || !hasActiveVeilWisp(player)) {
            return threshold;
        }
        double coreMultiplier = plugin.getBeastwardenManager() == null
            ? 1.0D : plugin.getBeastwardenManager().familiarCoreMultiplier(player, "veil_wisp");
        return Math.max(1, (int) Math.ceil(threshold / (1.0D + 0.5D * coreMultiplier)));
    }

    private int adjustedDirectEssencePayout(Player player, int payout) {
        if (payout <= 0 || !hasActiveVeilWisp(player)) {
            return payout;
        }
        double coreMultiplier = plugin.getBeastwardenManager() == null
            ? 1.0D : plugin.getBeastwardenManager().familiarCoreMultiplier(player, "veil_wisp");
        return Math.max(1, (int) Math.ceil(payout * (1.0D + 0.5D * coreMultiplier)));
    }

    private boolean hasActiveVeilWisp(Player player) {
        return plugin.getMayorQuestManager() != null && plugin.getMayorQuestManager().hasActiveVeilWisp(player);
    }

    private long changeBalance(Player player, long delta, String reason, boolean notify) {
        Account account = account(player);
        long applied = changeAccountBalance(account, player.getUniqueId(), player.getName(), delta, reason, true);
        if (notify && plugin.getConfigManager().normalEssenceNotify && applied > 0L) {
            notifyEarned(player, applied);
        }
        if (applied != 0L) queueBalanceMenuRefresh(player.getUniqueId());
        return Math.abs(applied);
    }

    private long changeAccountBalance(Account account, UUID playerId, String playerName, long delta, String reason, boolean countEarned) {
        long after;
        long applied;
        String storedPlayerName;
        synchronized (account) {
            account.playerName = playerName == null || playerName.isBlank() ? account.playerName : playerName;
            long before = account.balance;
            after = clampBalance(before + delta);
            applied = after - before;
            if (applied == 0L) {
                return 0L;
            }
            account.balance = after;
            if (!account.loaded && account.pendingAbsoluteBalance != null) {
                account.pendingAbsoluteBalance = after;
            }
            if (countEarned && applied > 0L) {
                account.lifetimeEarned = clampBalance(account.lifetimeEarned + applied);
            }
            markDirty(account);
            storedPlayerName = account.playerName;
        }
        plugin.getDatabase().logEssenceTransaction(playerId, storedPlayerName, applied, reason, after);
        return applied;
    }

    private AdminChangeResult applyOnlineAdminChange(Player target, long amount, AdminEssenceOperation operation) {
        Account account = account(target);
        long before;
        synchronized (account) {
            before = account.balance;
        }
        long applied = switch (operation) {
            case ADD -> changeAccountBalance(account, target.getUniqueId(), target.getName(), amount, "admin_add", true);
            case REMOVE -> changeAccountBalance(account, target.getUniqueId(), target.getName(), -amount, "admin_remove", false);
            case SET -> setOnlineBalance(target, amount, false);
            case RESET -> setOnlineBalance(target, 0L, true);
        };
        if (operation == AdminEssenceOperation.ADD && applied > 0L && plugin.getConfigManager().normalEssenceNotify) {
            notifyEarned(target, applied);
        }
        if (applied != 0L) queueBalanceMenuRefresh(target.getUniqueId());
        synchronized (account) {
            return new AdminChangeResult(
                true,
                true,
                target.getName(),
                before,
                account.balance,
                account.balance - before,
                operation == AdminEssenceOperation.RESET
            );
        }
    }

    private long setOnlineBalance(Player target, long amount, boolean resetProgress) {
        Account account = account(target);
        long after;
        long applied;
        synchronized (account) {
            long before = account.balance;
            after = clampBalance(amount);
            applied = after - before;
            account.playerName = target.getName();
            account.balance = after;
            if (!account.loaded) {
                account.pendingAbsoluteBalance = after;
            }
            if (resetProgress) {
                account.miningProgress = 0;
                account.mobProgress = 0;
                account.xpProgress = 0;
                if (!account.loaded) {
                    account.pendingProgressReset = true;
                }
            }
            markDirty(account);
        }
        if (applied != 0L) {
            plugin.getDatabase().logEssenceTransaction(target.getUniqueId(), target.getName(), applied, resetProgress ? "admin_reset" : "admin_set", after);
        }
        return applied;
    }

    private CompletableFuture<AdminChangeResult> applyAdminChange(String targetName, long amount, AdminEssenceOperation operation) {
        Player online = onlinePlayer(targetName);
        if (online != null) {
            AdminChangeResult result = applyOnlineAdminChange(online, amount, operation);
            flushAccount(online.getUniqueId());
            return CompletableFuture.completedFuture(result);
        }
        return queueOfflineAdminChange(targetName, amount, operation);
    }

    private CompletableFuture<AdminChangeResult> queueOfflineAdminChange(
        String targetName,
        long amount,
        AdminEssenceOperation operation
    ) {
        String chainKey = targetName == null ? "" : targetName.trim().toLowerCase(Locale.ROOT);
        CompletableFuture<AdminChangeResult> result = new CompletableFuture<>();
        CompletableFuture<Void> chain = offlineAdminChains.compute(chainKey, (ignored, previous) -> {
            CompletableFuture<Void> base = previous == null
                ? CompletableFuture.completedFuture(null)
                : previous.handle((unused, previousFailure) -> (Void) null);
            return base
                .thenCompose(unused -> plugin.getDatabase().loadEssenceAccountByName(targetName))
                .thenCompose(record -> record
                    .map(value -> applyOfflineAdminChange(value, amount, operation))
                    .orElseGet(() -> CompletableFuture.completedFuture(
                        AdminChangeResult.notFound(targetName, operation == AdminEssenceOperation.RESET))))
                .handle((change, failure) -> {
                    if (failure == null) {
                        result.complete(change);
                    } else {
                        result.completeExceptionally(failure);
                    }
                    return null;
                });
        });
        chain.whenComplete((unused, failure) -> offlineAdminChains.remove(chainKey, chain));
        return result;
    }

    private CompletableFuture<AdminChangeResult> applyOfflineAdminChange(DatabaseManager.EssenceAccountRecord record, long amount, AdminEssenceOperation operation) {
        if (accounts.containsKey(record.playerUuid())) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Essence account became active while the offline admin update was pending."));
        }
        long before = Math.max(0L, record.balance());
        long after = switch (operation) {
            case ADD -> clampBalance(before + amount);
            case REMOVE -> clampBalance(before - amount);
            case SET -> clampBalance(amount);
            case RESET -> 0L;
        };
        long applied = after - before;
        long lifetime = operation == AdminEssenceOperation.ADD && applied > 0L
            ? clampBalance(record.lifetimeEarned() + applied)
            : Math.max(0L, record.lifetimeEarned());
        boolean resetProgress = operation == AdminEssenceOperation.RESET;
        DatabaseManager.EssenceAccountRecord updated = new DatabaseManager.EssenceAccountRecord(
            record.playerUuid(),
            record.playerName(),
            after,
            lifetime,
            resetProgress ? 0 : record.miningProgress(),
            resetProgress ? 0 : record.mobProgress(),
            resetProgress ? 0 : record.xpProgress(),
            nextRevision(record.updatedAt())
        );
        return plugin.getDatabase().saveEssenceAccount(updated)
            .thenCompose(saved -> {
                if (!saved) {
                    return CompletableFuture.failedFuture(
                        new IllegalStateException("Essence account changed while the admin update was pending."));
                }
                if (applied == 0L) {
                    return CompletableFuture.completedFuture(null);
                }
                return plugin.getDatabase().logEssenceTransaction(
                    record.playerUuid(),
                    record.playerName(),
                    applied,
                    switch (operation) {
                        case ADD -> "admin_add";
                        case REMOVE -> "admin_remove";
                        case SET -> "admin_set";
                        case RESET -> "admin_reset";
                    },
                    after
                );
            })
            .thenApply(ignored -> new AdminChangeResult(
                true,
                false,
                record.playerName(),
                before,
                after,
                applied,
                resetProgress
            ));
    }

    private EssenceSnapshot snapshot(DatabaseManager.EssenceAccountRecord record, boolean online) {
        return new EssenceSnapshot(
            record.playerUuid(),
            record.playerName(),
            Math.max(0L, record.balance()),
            Math.max(0L, record.lifetimeEarned()),
            Math.max(0, record.miningProgress()),
            Math.max(0, record.mobProgress()),
            Math.max(0, record.xpProgress()),
            online
        );
    }

    private Player onlinePlayer(String targetName) {
        if (targetName == null || targetName.isBlank()) {
            return null;
        }
        Player exact = Bukkit.getPlayerExact(targetName);
        if (exact != null) {
            return exact;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(targetName)) {
                return player;
            }
        }
        return null;
    }

    private void notifyEarned(Player player, long amount) {
        player.sendActionBar(MM.deserialize("<aqua>+" + NUMBERS.format(amount) + " Essence</aqua>"));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35f, 1.35f);
    }

    private void loadAccount(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        Account account = accounts.computeIfAbsent(playerId, id -> Account.empty(id, playerName));
        boolean shouldLoad;
        synchronized (account) {
            account.removeWhenSaved = false;
            if (!playerName.equals(account.playerName)) {
                account.playerName = playerName;
                if (account.loaded) {
                    markDirty(account);
                }
            }
            shouldLoad = !account.loaded && !account.loading;
            if (shouldLoad) {
                account.loading = true;
            }
        }
        if (!shouldLoad) {
            if (account.dirty) {
                flushAccount(playerId);
            }
            return;
        }

        CompletableFuture<Void> load = plugin.getDatabase().loadEssenceAccount(playerId, playerName)
            .thenAccept(record -> mergeLoadedAccount(playerId, playerName, record))
            .exceptionally(ex -> {
                synchronized (account) {
                    account.loading = false;
                }
                plugin.getLogger().severe("Could not load Essence account for " + playerName + ": " + ex.getMessage());
                removeAccountIfSaved(playerId, account);
                return null;
            });
        accountLoads.put(playerId, load);
        load.whenComplete((ignored, failure) -> accountLoads.remove(playerId, load));
    }

    private void mergeLoadedAccount(UUID playerId, String playerName, DatabaseManager.EssenceAccountRecord record) {
        if (playerId == null || record == null) {
            return;
        }
        Account current = accounts.get(playerId);
        if (current == null) {
            return;
        }
        boolean shouldFlush;
        synchronized (current) {
            if (current.loaded) {
                current.loading = false;
                return;
            }

            boolean hadPendingChanges = current.dirty;
            long pendingBalance = current.balance;
            long pendingLifetime = current.lifetimeEarned;
            int pendingMining = current.miningProgress;
            int pendingMobs = current.mobProgress;
            int pendingXp = current.xpProgress;
            long pendingRevision = current.updatedAt;
            Long pendingAbsoluteBalance = current.pendingAbsoluteBalance;
            boolean pendingProgressReset = current.pendingProgressReset;

            current.playerName = playerName == null || playerName.isBlank() ? record.playerName() : playerName;
            current.balance = Math.max(0L, record.balance());
            current.lifetimeEarned = Math.max(0L, record.lifetimeEarned());
            current.miningProgress = Math.max(0, record.miningProgress());
            current.mobProgress = Math.max(0, record.mobProgress());
            current.xpProgress = Math.max(0, record.xpProgress());
            current.updatedAt = Math.max(0L, record.updatedAt());
            current.loaded = true;
            current.loading = false;
            current.pendingAbsoluteBalance = null;
            current.pendingProgressReset = false;

            if (hadPendingChanges) {
                current.balance = pendingAbsoluteBalance == null
                    ? clampBalance(current.balance + pendingBalance)
                    : clampBalance(pendingAbsoluteBalance);
                current.lifetimeEarned = clampBalance(current.lifetimeEarned + pendingLifetime);
                current.miningProgress = pendingProgressReset ? pendingMining : current.miningProgress + pendingMining;
                current.mobProgress = pendingProgressReset ? pendingMobs : current.mobProgress + pendingMobs;
                current.xpProgress = pendingProgressReset ? pendingXp : current.xpProgress + pendingXp;
                normalizeProgress(current);
                current.updatedAt = nextRevision(Math.max(current.updatedAt, pendingRevision));
                current.dirty = true;
            } else {
                current.dirty = false;
            }
            shouldFlush = current.dirty;
        }
        if (shouldFlush) {
            flushAccount(playerId);
        } else {
            removeAccountIfSaved(playerId, current);
        }
        queueBalanceMenuRefresh(playerId);
    }

    private Account account(Player player) {
        return accounts.computeIfAbsent(player.getUniqueId(), id -> Account.empty(id, player.getName()));
    }

    private void queueBalanceMenuRefresh(UUID playerId) {
        if (playerId == null || shuttingDown || !pendingBalanceMenuRefreshes.add(playerId)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingBalanceMenuRefreshes.remove(playerId);
            if (shuttingDown) return;
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) return;
            PriestManager priestManager = plugin.getPriestManager();
            if (priestManager != null) priestManager.refreshOpenMenu(player);
            MainMenuCommand.refreshEssenceNumbers(plugin, player);
        });
    }

    private boolean enabled() {
        return plugin.getConfigManager() != null && plugin.getConfigManager().normalEssenceEnabled;
    }

    private boolean eligible(Player player) {
        if (!enabled() || player == null || !player.isOnline()) {
            return false;
        }
        GameMode mode = player.getGameMode();
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }

    private List<CompletableFuture<Boolean>> flushDirtyAccounts() {
        List<CompletableFuture<Boolean>> saves = new ArrayList<>();
        for (UUID playerId : accounts.keySet()) {
            CompletableFuture<Boolean> save = flushAccount(playerId);
            if (save != null) {
                saves.add(save);
            }
        }
        return saves;
    }

    private CompletableFuture<Boolean> flushAccount(UUID playerId) {
        Account account = accounts.get(playerId);
        if (account == null) {
            return accountSaveChains.get(playerId);
        }

        DatabaseManager.EssenceAccountRecord record;
        CompletableFuture<Boolean> save;
        synchronized (account) {
            if (!account.loaded || !account.dirty) {
                return accountSaveChains.get(playerId);
            }
            record = account.toRecord();
            account.dirty = false;
            save = accountSaveChains.compute(playerId, (ignored, previous) -> {
                CompletableFuture<Void> base = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous.handle((saved, failure) -> (Void) null);
                return base
                    .thenCompose(unused -> plugin.getDatabase().saveEssenceAccount(record))
                    .handle((saved, failure) -> {
                        boolean succeeded = failure == null && Boolean.TRUE.equals(saved);
                        synchronized (account) {
                            if (!succeeded && account.updatedAt <= record.updatedAt()) {
                                account.dirty = true;
                            }
                        }
                        if (failure != null) {
                            plugin.getLogger().severe("Could not save Essence account for " + record.playerName() + ": " + failure.getMessage());
                        } else if (!succeeded) {
                            plugin.getLogger().warning("Rejected stale Essence snapshot for " + record.playerName()
                                + " at revision " + record.updatedAt() + ".");
                        }
                        return succeeded;
                    });
            });
        }
        save.whenComplete((saved, failure) -> {
            accountSaveChains.remove(playerId, save);
            removeAccountIfSaved(playerId, account);
        });
        return save;
    }

    private void removeAccountIfSaved(UUID playerId, Account account) {
        synchronized (account) {
            if (!account.removeWhenSaved || account.loading || account.dirty || accountSaveChains.containsKey(playerId)) {
                return;
            }
        }
        accounts.remove(playerId, account);
    }

    private void awaitAll(List<? extends CompletableFuture<?>> futures, String description) {
        if (futures.isEmpty()) {
            return;
        }
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            plugin.getLogger().severe(description + " were interrupted during shutdown.");
        } catch (ExecutionException | TimeoutException ex) {
            plugin.getLogger().severe(description + " did not finish cleanly: " + ex.getMessage());
        }
    }

    private void cleanupPlacedBlocks() {
        long now = System.currentTimeMillis();
        boolean changed = placedBlocks.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (placedBlocks.size() <= MAX_PLACED_BLOCK_CACHE) {
            if (changed) {
                savePlacedBlocks();
            }
            return;
        }
        int toRemove = placedBlocks.size() - MAX_PLACED_BLOCK_CACHE;
        int removed = 0;
        for (BlockKey key : placedBlocks.keySet()) {
            placedBlocks.remove(key);
            if (++removed >= toRemove) {
                break;
            }
        }
        savePlacedBlocks();
    }

    private void loadPlacedBlocks() {
        placedBlocks.clear();
        if (!placedBlocksFile.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(placedBlocksFile);
        long now = System.currentTimeMillis();
        for (String raw : config.getStringList("blocks")) {
            String[] parts = raw.split("\\|", 2);
            if (parts.length != 2) {
                continue;
            }
            try {
                String[] coordinates = parts[0].split(":", 4);
                if (coordinates.length != 4) {
                    continue;
                }
                long expiresAt = Long.parseLong(parts[1]);
                if (expiresAt <= now) {
                    continue;
                }
                placedBlocks.put(
                    new BlockKey(UUID.fromString(coordinates[0]),
                        Integer.parseInt(coordinates[1]),
                        Integer.parseInt(coordinates[2]),
                        Integer.parseInt(coordinates[3])),
                    expiresAt
                );
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed provenance rows and keep the valid rows.
            }
        }
    }

    private void savePlacedBlocks() {
        if (shuttingDown || placedBlocksSaveTask != null) {
            return;
        }
        placedBlocksSaveTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            placedBlocksSaveTask = null;
            queuePlacedBlocksSave();
        }, PLACED_BLOCK_SAVE_DELAY_TICKS);
    }

    private CompletableFuture<Void> queuePlacedBlocksSave() {
        synchronized (placedBlocksSaveLock) {
            placedBlocksSaveChain = placedBlocksSaveChain
                .handle((ignored, failure) -> null)
                .thenRunAsync(this::writePlacedBlocksSnapshot);
            return placedBlocksSaveChain;
        }
    }

    private void writePlacedBlocksSnapshot() {
        YamlConfiguration config = new YamlConfiguration();
        List<String> rows = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<BlockKey, Long> entry : placedBlocks.entrySet()) {
            if (entry.getValue() <= now) {
                continue;
            }
            BlockKey key = entry.getKey();
            rows.add(key.worldId() + ":" + key.x() + ":" + key.y() + ":" + key.z() + "|" + entry.getValue());
        }
        rows.sort(String::compareTo);
        config.set("blocks", rows);
        Path temporary = null;
        try {
            File parent = placedBlocksFile.getParentFile();
            Path parentPath = parent == null ? placedBlocksFile.toPath().toAbsolutePath().getParent() : parent.toPath();
            if (parentPath == null) {
                throw new IOException("No parent directory is available");
            }
            Files.createDirectories(parentPath);
            temporary = Files.createTempFile(parentPath, placedBlocksFile.getName() + ".", ".tmp");
            config.save(temporary.toFile());
            try {
                Files.move(temporary, placedBlocksFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, placedBlocksFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save Essence block provenance: " + ex.getMessage());
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Best effort cleanup of the temporary provenance file.
                }
            }
        }
    }

    private void normalizeProgress(Account account) {
        int miningThreshold = Math.max(1, plugin.getConfigManager().normalEssenceMiningThreshold);
        int mobThreshold = Math.max(1, plugin.getConfigManager().normalEssenceMobKillThreshold);
        int xpThreshold = Math.max(1, plugin.getConfigManager().normalEssenceXpThreshold);
        account.miningProgress = Math.floorMod(account.miningProgress, miningThreshold);
        account.mobProgress = Math.floorMod(account.mobProgress, mobThreshold);
        account.xpProgress = Math.floorMod(account.xpProgress, xpThreshold);
    }

    private int miningPoints(Material material) {
        return switch (material) {
            case ANCIENT_DEBRIS -> 50;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE, EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> 20;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> 12;
            case IRON_ORE, DEEPSLATE_IRON_ORE, REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> 10;
            case COAL_ORE, DEEPSLATE_COAL_ORE, COPPER_ORE, DEEPSLATE_COPPER_ORE,
                 NETHER_QUARTZ_ORE, NETHER_GOLD_ORE -> 8;
            case OBSIDIAN, CRYING_OBSIDIAN -> 4;
            case STONE, DEEPSLATE, TUFF, CALCITE, GRANITE, DIORITE, ANDESITE,
                 NETHERRACK, BASALT, SMOOTH_BASALT, BLACKSTONE, END_STONE,
                 SANDSTONE, RED_SANDSTONE -> 1;
            default -> 0;
        };
    }

    private int mobKillPoints(EntityType type) {
        return switch (type) {
            case ENDER_DRAGON, WITHER, WARDEN -> 25;
            case ELDER_GUARDIAN, RAVAGER, EVOKER, PIGLIN_BRUTE -> 5;
            case BLAZE, BREEZE, CREEPER, ENDERMAN, GHAST, GUARDIAN, HOGLIN, PHANTOM,
                 SHULKER, SLIME, WITCH, WITHER_SKELETON, ZOGLIN -> 2;
            case BOGGED, CAVE_SPIDER, DROWNED, HUSK, MAGMA_CUBE, PIGLIN, PILLAGER,
                 SKELETON, SPIDER, STRAY, VEX, VINDICATOR, ZOMBIE, ZOMBIE_VILLAGER,
                 ZOMBIFIED_PIGLIN -> 1;
            default -> 0;
        };
    }

    private long clampBalance(long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        return Math.min(amount, MAX_BALANCE);
    }

    private static void markDirty(Account account) {
        account.updatedAt = nextRevision(account.updatedAt);
        account.dirty = true;
    }

    private static long nextRevision(long current) {
        long now = Math.max(1L, System.currentTimeMillis());
        if (current >= Long.MAX_VALUE - 1L) {
            return Long.MAX_VALUE;
        }
        return Math.max(now, current + 1L);
    }

    private enum Source {
        MINING("mining") {
            @Override
            int threshold(SMPCore plugin) {
                return plugin.getConfigManager().normalEssenceMiningThreshold;
            }

            @Override
            int payout(SMPCore plugin) {
                return plugin.getConfigManager().normalEssenceMiningPayout;
            }

            @Override
            int progress(Account account) {
                return account.miningProgress;
            }

            @Override
            void progress(Account account, int progress) {
                account.miningProgress = progress;
            }
        },
        MOBS("mob_kills") {
            @Override
            int threshold(SMPCore plugin) {
                return plugin.getConfigManager().normalEssenceMobKillThreshold;
            }

            @Override
            int payout(SMPCore plugin) {
                return plugin.getConfigManager().normalEssenceMobKillPayout;
            }

            @Override
            int progress(Account account) {
                return account.mobProgress;
            }

            @Override
            void progress(Account account, int progress) {
                account.mobProgress = progress;
            }
        },
        XP("xp_gain") {
            @Override
            int threshold(SMPCore plugin) {
                return plugin.getConfigManager().normalEssenceXpThreshold;
            }

            @Override
            int payout(SMPCore plugin) {
                return plugin.getConfigManager().normalEssenceXpPayout;
            }

            @Override
            int progress(Account account) {
                return account.xpProgress;
            }

            @Override
            void progress(Account account, int progress) {
                account.xpProgress = progress;
            }
        };

        private final String reason;

        Source(String reason) {
            this.reason = reason;
        }

        abstract int threshold(SMPCore plugin);
        abstract int payout(SMPCore plugin);
        abstract int progress(Account account);
        abstract void progress(Account account, int progress);
    }

    private static final class Account {
        private final UUID playerId;
        private String playerName;
        private long balance;
        private long lifetimeEarned;
        private int miningProgress;
        private int mobProgress;
        private int xpProgress;
        private volatile long updatedAt;
        private volatile boolean loaded;
        private volatile boolean loading;
        private volatile boolean dirty;
        private volatile boolean removeWhenSaved;
        private Long pendingAbsoluteBalance;
        private boolean pendingProgressReset;

        private Account(UUID playerId, String playerName) {
            this.playerId = playerId;
            this.playerName = playerName == null || playerName.isBlank() ? "Unknown" : playerName;
            this.updatedAt = Math.max(1L, System.currentTimeMillis());
        }

        private static Account empty(UUID playerId, String playerName) {
            return new Account(playerId, playerName);
        }

        private DatabaseManager.EssenceAccountRecord toRecord() {
            return new DatabaseManager.EssenceAccountRecord(
                playerId,
                playerName,
                balance,
                lifetimeEarned,
                miningProgress,
                mobProgress,
                xpProgress,
                updatedAt
            );
        }
    }

    private enum AdminEssenceOperation {
        ADD,
        REMOVE,
        SET,
        RESET
    }

    public record EssenceSnapshot(
        UUID playerId,
        String playerName,
        long balance,
        long lifetimeEarned,
        int miningProgress,
        int mobProgress,
        int xpProgress,
        boolean online
    ) {}

    public record AdminChangeResult(
        boolean found,
        boolean online,
        String playerName,
        long before,
        long after,
        long applied,
        boolean progressReset
    ) {
        private static AdminChangeResult notFound(String playerName, boolean progressReset) {
            return new AdminChangeResult(false, false, playerName, 0L, 0L, 0L, progressReset);
        }
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {
        private static BlockKey of(Block block) {
            if (block == null) {
                return null;
            }
            World world = block.getWorld();
            return new BlockKey(world.getUID(), block.getX(), block.getY(), block.getZ());
        }
    }
}
