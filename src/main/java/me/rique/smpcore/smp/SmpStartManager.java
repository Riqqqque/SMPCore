package me.rique.smpcore.smp;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.config.ConfigManager;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.util.PluginCommandRoots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SmpStartManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final long MESSAGE_THROTTLE_MS = 2500L;
    private static final long HAZARD_ATTRIBUTION_MS = 30_000L;
    private static final double HAZARD_DAMAGE_RADIUS_SQUARED = 9.0;
    private static final double HAZARD_EXPLOSION_RADIUS_SQUARED = 144.0;
    private final SMPCore plugin;
    private final NamespacedKey seasonIntroductionKey;
    private final NamespacedKey seasonLaunchFireworkKey;
    private final Map<UUID, Long> nextGraceMessageAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextLockdownMessageAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextDimensionMessageAt = new ConcurrentHashMap<>();
    private final Map<BlockKey, HazardAttribution> recentGraceHazards = new ConcurrentHashMap<>();
    private final Set<UUID> preStartBorderViewers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> preStartBarrierPreviewers = ConcurrentHashMap.newKeySet();
    private BossBar graceBossBar;
    private BukkitTask graceBossBarTask;
    private WorldBorder preStartPlayerBorder;

    public SmpStartManager(SMPCore plugin) {
        this.plugin = plugin;
        this.seasonIntroductionKey = new NamespacedKey(plugin, "season_launch_introduction_at");
        this.seasonLaunchFireworkKey = new NamespacedKey(plugin, "season_launch_firework");
    }

    public void applyConfiguredState() {
        if (!plugin.getConfigManager().smpStartEnabled) {
            clearPreStartPlayerBorders();
            stopGraceBossBar();
            return;
        }

        World world = configuredWorld();
        if (world == null) {
            clearPreStartPlayerBorders();
            plugin.getLogger().warning("SMP start world '" + plugin.getConfigManager().smpStartWorld + "' is not loaded.");
            return;
        }

        if (!plugin.getConfigManager().smpStarted) {
            applyBorder(world, false);
            configurePreStartPlayerBorder(world);
        } else {
            clearPreStartPlayerBorders();
        }
        updateGraceBossBarState();
    }

    public void shutdown() {
        clearPreStartPlayerBorders();
        preStartBarrierPreviewers.clear();
        stopGraceBossBar();
        nextGraceMessageAt.clear();
        nextLockdownMessageAt.clear();
        nextDimensionMessageAt.clear();
        recentGraceHazards.clear();
    }

    public StartResult start(CommandSender sender) {
        return start(sender, null);
    }

    public StartResult start(CommandSender sender, Integer graceMinutesOverride) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.smpStartEnabled) {
            return new StartResult(false, "SMP start flow is disabled in config.");
        }
        if (config.smpStarted) {
            return new StartResult(false, "The SMP has already been started.");
        }

        World world = configuredWorld();
        if (world == null) {
            return new StartResult(false, "SMP start world '" + config.smpStartWorld + "' is not loaded.");
        }

        long now = System.currentTimeMillis();
        config.smpStarted = true;
        config.smpStartedAt = now;
        if (graceMinutesOverride != null) {
            int graceMinutes = Math.max(0, Math.min(7 * 24 * 60, graceMinutesOverride));
            config.smpPostStartGraceMinutes = graceMinutes;
            plugin.getConfig().set("smp-start.post-start-grace-minutes", graceMinutes);
        }
        plugin.getConfig().set("smp-start.started", true);
        plugin.getConfig().set("smp-start.started-at", now);
        plugin.saveConfig();

        applyBorder(world, true);
        clearPreStartPlayerBorders();
        Bukkit.broadcast(startBroadcast());
        Bukkit.broadcast(MessageUtil.info(
            "The Nether opens on real-life Day <white>" + config.smpNetherUnlockDay
                + "</white>. The End opens on Day <white>" + config.smpEndUnlockDay + "</white>."
        ));
        presentSeasonOpeningToOnlinePlayers(world.getSpawnLocation());
        startGraceBossBar();
        return new StartResult(true, "SMP started by " + sender.getName() + ".");
    }

    public StartResult reset(CommandSender sender) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.smpStartEnabled) {
            return new StartResult(false, "SMP start flow is disabled in config.");
        }

        setPreStartLockedState();
        Bukkit.broadcast(MessageUtil.warn("The SMP start state was reset by <white>" + sender.getName() + "</white>."));
        return new StartResult(true, "SMP start state reset.");
    }

    public StartResult lock(CommandSender sender) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.smpStartEnabled) {
            return new StartResult(false, "SMP start flow is disabled in config.");
        }

        World world = configuredWorld();
        if (world == null) {
            return new StartResult(false, "SMP start world '" + config.smpStartWorld + "' is not loaded.");
        }

        setPreStartLockedState();
        Bukkit.broadcast(MessageUtil.warn("The SMP start area was locked by <white>" + sender.getName() + "</white>."));
        return new StartResult(true, "SMP start area locked around world spawn.");
    }

    public Component statusMessage() {
        ConfigManager config = plugin.getConfigManager();
        if (!config.smpStartEnabled) {
            return MessageUtil.info("SMP start flow is disabled.");
        }

        World world = configuredWorld();
        String border = config.smpStarted && world != null
            ? formatNumber(world.getWorldBorder().getSize())
            : formatNumber(config.smpPreStartBorderDiameter);
        if (!config.smpStarted) {
            return MessageUtil.info(
                "SMP has not started. Spawn barrier: <white>" + formatNumber(config.smpPreStartBorderDiameter / 2.0)
                    + " blocks</white>. PvP and dimensions are locked."
            );
        }

        String dimensions = "Nether: <white>" + dimensionStatus(World.Environment.NETHER)
            + "</white>. End: <white>" + dimensionStatus(World.Environment.THE_END) + "</white>.";
        if (isGraceActive()) {
            return MessageUtil.info(
                "SMP is started. Border: <white>" + border + "</white>. PvP unlocks in <white>"
                    + formatDuration(graceMillisRemaining()) + "</white>. " + dimensions
            );
        }

        return MessageUtil.info("SMP is started. Border: <white>" + border + "</white>. PvP is unlocked. " + dimensions);
    }

    public StartResult toggleBarrierPreview(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return new StartResult(false, "Only a player can preview the spawn barrier.");
        }
        if (!isPreStartLocked()) {
            return new StartResult(false, "The pre-SMP spawn barrier is not active.");
        }

        UUID playerId = player.getUniqueId();
        if (preStartBarrierPreviewers.remove(playerId)) {
            clearPreStartPlayerBorder(player);
            return new StartResult(true, "Spawn barrier preview disabled.");
        }

        preStartBarrierPreviewers.add(playerId);
        syncPreStartPlayer(player);
        return new StartResult(true, "Spawn barrier preview enabled for you. Run /startsmp preview again to leave it.");
    }

    public boolean isGraceActive() {
        ConfigManager config = plugin.getConfigManager();
        if (!config.smpStartEnabled) return false;
        if (!config.smpStarted) return true;
        return graceMillisRemaining() > 0L;
    }

    public boolean isPreStartLocked() {
        ConfigManager config = plugin.getConfigManager();
        return config.smpStartEnabled && !config.smpStarted;
    }

    public boolean shouldDeferSeasonIntroduction() {
        return isPreStartLocked();
    }

    public void presentSeasonIntroductionIfPending(Player player) {
        if (player == null || !player.isOnline() || !isSeasonIntroductionPending(player)) {
            return;
        }
        markSeasonIntroductionSeen(player);
        playSeasonIntroduction(List.of(player), player.getLocation(), 2);
    }

    static boolean shouldDeliverSeasonIntroduction(
        boolean smpStartEnabled,
        boolean smpStarted,
        long smpStartedAt,
        Long introductionSeenAt
    ) {
        if (smpStartEnabled && !smpStarted) {
            return false;
        }
        long launchToken = smpStartEnabled ? Math.max(1L, smpStartedAt) : 1L;
        return introductionSeenAt == null || introductionSeenAt.longValue() != launchToken;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (!event.getWorld().getName().equalsIgnoreCase(plugin.getConfigManager().smpStartWorld)) {
            return;
        }
        applyConfiguredState();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> syncPreStartPlayer(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> syncPreStartPlayer(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        nextGraceMessageAt.remove(playerId);
        nextLockdownMessageAt.remove(playerId);
        nextDimensionMessageAt.remove(playerId);
        preStartBorderViewers.remove(playerId);
        preStartBarrierPreviewers.remove(playerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreStartBarrierTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (!isPreStartBarrierSubject(player) || !isPreStartLocked() || to == null || to.getWorld() == null) {
            return;
        }
        if (!isPreStartProtectedWorld(to.getWorld()) || isInsidePreStartBarrier(to, 0.0D)) {
            return;
        }

        event.setCancelled(true);
        maybeSendLockdownMessage(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreStartBarrierMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (!isPreStartLocked() || !isPreStartBarrierSubject(player) || to == null) {
            return;
        }
        if (!isPreStartProtectedWorld(to.getWorld()) || isInsidePreStartBarrier(to, 0.0D)) {
            return;
        }

        Location from = event.getFrom();
        if (isInsidePreStartBarrier(from, 0.0D)) {
            event.setCancelled(true);
        } else {
            World world = configuredWorld();
            if (world != null) {
                Location spawn = world.getSpawnLocation().clone();
                spawn.setYaw(from.getYaw());
                spawn.setPitch(from.getPitch());
                event.setTo(spawn);
            } else {
                event.setCancelled(true);
            }
        }
        maybeSendLockdownMessage(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedDimensionTravel(PlayerTeleportEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || to.getWorld() == null || from.getWorld() == null) {
            return;
        }
        World.Environment destination = to.getWorld().getEnvironment();
        if (destination == from.getWorld().getEnvironment()
            || (destination != World.Environment.NETHER && destination != World.Environment.THE_END)) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission("smpcore.startsmp.bypass-dimension-lock") || isDimensionUnlocked(destination)) {
            return;
        }

        event.setCancelled(true);
        maybeSendDimensionMessage(player, destination);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreStartPluginCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!shouldBlockPreStartCommand(player)) {
            return;
        }

        String root = commandRoot(event.getMessage());
        if (!PluginCommandRoots.contains(root)) {
            return;
        }

        event.setCancelled(true);
        maybeSendLockdownMessage(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreStartFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!isProtectedPreStartMember(player)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreStartFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!isProtectedPreStartMember(player)) {
            return;
        }
        if (event.getFoodLevel() >= player.getFoodLevel()) {
            return;
        }

        event.setCancelled(true);
        player.setSaturation(Math.max(player.getSaturation(), 5.0f));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreStartHostileSpawn(CreatureSpawnEvent event) {
        if (!isPreStartLocked()) {
            return;
        }
        if (!isInsidePreStartBarrier(event.getLocation())) {
            return;
        }
        if (!isHostileSpawn(event)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreStartBlockBreak(BlockBreakEvent event) {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().allowsArenaBlockBreak(event.getPlayer(), event.getBlock())) return;
        if (!shouldBlockPreStartBlockEdit(event.getPlayer(), event.getBlock().getWorld())) {
            return;
        }

        event.setCancelled(true);
        maybeSendLockdownMessage(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreStartBlockPlace(BlockPlaceEvent event) {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().allowsArenaBlockPlacement(event.getPlayer(), event.getBlockPlaced())) return;
        if (!shouldBlockPreStartBlockEdit(event.getPlayer(), event.getBlockPlaced().getWorld())) {
            return;
        }

        event.setCancelled(true);
        maybeSendLockdownMessage(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreStartBucketEmpty(PlayerBucketEmptyEvent event) {
        Block target = event.getBlock().getRelative(event.getBlockFace());
        if (plugin.getDuelManager() != null && plugin.getDuelManager().allowsArenaBucket(event.getPlayer(), target)) return;
        if (!shouldBlockPreStartBlockEdit(event.getPlayer(), event.getBlock().getWorld())) {
            return;
        }

        event.setCancelled(true);
        maybeSendLockdownMessage(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreStartBucketFill(PlayerBucketFillEvent event) {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().allowsArenaBucket(event.getPlayer(), event.getBlock())) return;
        if (!shouldBlockPreStartBlockEdit(event.getPlayer(), event.getBlock().getWorld())) {
            return;
        }

        event.setCancelled(true);
        maybeSendLockdownMessage(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreStartBlockIgnite(BlockIgniteEvent event) {
        Player player = event.getPlayer();
        if (player != null && plugin.getDuelManager() != null
            && plugin.getDuelManager().isActiveArena(event.getBlock().getLocation())
            && plugin.getDuelManager().isDuelParticipant(player)) return;
        if (player == null || !shouldBlockPreStartBlockEdit(player, event.getBlock().getWorld())) {
            return;
        }

        event.setCancelled(true);
        maybeSendLockdownMessage(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGraceHazardPlace(BlockPlaceEvent event) {
        if (!isGraceActive()) return;
        if (!isHazardBlock(event.getBlockPlaced().getType())) return;
        rememberGraceHazard(event.getBlockPlaced().getLocation(), event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGraceBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!isGraceActive()) return;
        if (event.getBucket() != Material.LAVA_BUCKET) return;
        rememberGraceHazard(event.getBlock().getLocation(), event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGraceBlockIgnite(BlockIgniteEvent event) {
        Player player = event.getPlayer();
        if (!isGraceActive()) return;

        if (player == null) {
            player = attackingPlayer(event.getIgnitingEntity());
        }
        if (player == null) return;

        rememberGraceHazard(event.getBlock().getLocation(), player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGracePlayerDamage(EntityDamageEvent event) {
        if (!isGraceActive()) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = graceAttacker(event, victim);
        if (attacker == null || attacker.equals(victim)) return;

        event.setCancelled(true);
        maybeSendGraceMessage(attacker);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSeasonLaunchFireworkDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Firework firework)) {
            return;
        }
        if (firework.getPersistentDataContainer().has(seasonLaunchFireworkKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    private void applyBorder(World world, boolean fromStartCommand) {
        ConfigManager config = plugin.getConfigManager();
        WorldBorder border = world.getWorldBorder();
        Location center = world.getSpawnLocation();
        border.setCenter(center.getX(), center.getZ());

        double targetSize = config.smpStartedBorderDiameter;
        if (fromStartCommand && config.smpBorderExpandSeconds > 0) {
            border.changeSize(targetSize, config.smpBorderExpandSeconds);
        } else {
            border.setSize(targetSize);
        }
    }

    private void configurePreStartPlayerBorder(World world) {
        if (preStartPlayerBorder == null) {
            preStartPlayerBorder = Bukkit.createWorldBorder();
        }
        Location center = world.getSpawnLocation();
        preStartPlayerBorder.setCenter(center.getX(), center.getZ());
        preStartPlayerBorder.setSize(plugin.getConfigManager().smpPreStartBorderDiameter);
        preStartPlayerBorder.setDamageBuffer(0.0D);
        preStartPlayerBorder.setDamageAmount(0.0D);
        preStartPlayerBorder.setWarningDistance(0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            syncPreStartPlayer(player);
        }
    }

    private void syncPreStartPlayer(Player player) {
        if (player == null || !player.isOnline() || !isPreStartLocked() || !isPreStartBarrierSubject(player)) {
            clearPreStartPlayerBorder(player);
            return;
        }

        World world = configuredWorld();
        if (world == null || preStartPlayerBorder == null) {
            clearPreStartPlayerBorder(player);
            return;
        }

        if (!world.getUID().equals(player.getWorld().getUID())) {
            clearPreStartPlayerBorder(player);
            player.teleportAsync(world.getSpawnLocation());
            return;
        }

        player.setWorldBorder(preStartPlayerBorder);
        preStartBorderViewers.add(player.getUniqueId());
        if (!isInsidePreStartBarrier(player.getLocation(), 0.0D)) {
            player.teleportAsync(world.getSpawnLocation());
        }
    }

    private boolean isPreStartBarrierSubject(Player player) {
        return player != null
            && (preStartBarrierPreviewers.contains(player.getUniqueId())
                || (!player.isOp() && !player.hasPermission("smpcore.startsmp.bypass-dimension-lock")));
    }

    private void clearPreStartPlayerBorder(Player player) {
        if (player == null || !preStartBorderViewers.remove(player.getUniqueId())) {
            return;
        }
        if (player.getWorldBorder() == preStartPlayerBorder) {
            player.setWorldBorder(null);
        }
    }

    private void clearPreStartPlayerBorders() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearPreStartPlayerBorder(player);
        }
        preStartBorderViewers.clear();
    }

    private void setPreStartLockedState() {
        ConfigManager config = plugin.getConfigManager();
        config.smpStarted = false;
        config.smpStartedAt = 0L;
        plugin.getConfig().set("smp-start.started", false);
        plugin.getConfig().set("smp-start.started-at", 0L);
        plugin.saveConfig();
        stopGraceBossBar();
        applyConfiguredState();
    }

    private void updateGraceBossBarState() {
        if (plugin.getConfigManager().smpStarted && graceMillisRemaining() > 0L) {
            startGraceBossBar();
        } else {
            stopGraceBossBar();
        }
    }

    private void startGraceBossBar() {
        ConfigManager config = plugin.getConfigManager();
        if (!config.smpStartEnabled || !config.smpStarted || graceMillisRemaining() <= 0L) {
            stopGraceBossBar();
            return;
        }
        if (graceBossBar == null) {
            graceBossBar = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SEGMENTED_20);
        }
        if (graceBossBarTask == null) {
            graceBossBarTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickGraceBossBar, 0L, 20L);
        }
    }

    private void tickGraceBossBar() {
        ConfigManager config = plugin.getConfigManager();
        long remaining = graceMillisRemaining();
        if (!config.smpStartEnabled || !config.smpStarted || remaining <= 0L) {
            stopGraceBossBar();
            return;
        }

        if (graceBossBar == null) {
            return;
        }
        long total = Math.max(1L, Duration.ofMinutes(config.smpPostStartGraceMinutes).toMillis());
        graceBossBar.setTitle("PvP Grace: " + formatDuration(remaining));
        graceBossBar.setProgress(Math.max(0.0, Math.min(1.0, remaining / (double) total)));
        graceBossBar.setVisible(true);
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean inBossFight = plugin.getBossManager() != null && plugin.getBossManager().isActiveBossFight(player);
            if (inBossFight) {
                graceBossBar.removePlayer(player);
            } else if (!graceBossBar.getPlayers().contains(player)) {
                graceBossBar.addPlayer(player);
            }
        }
        for (Player viewer : List.copyOf(graceBossBar.getPlayers())) {
            if (!viewer.isOnline() || (plugin.getBossManager() != null && plugin.getBossManager().isActiveBossFight(viewer))) {
                graceBossBar.removePlayer(viewer);
            }
        }
    }

    private void stopGraceBossBar() {
        if (graceBossBarTask != null) {
            graceBossBarTask.cancel();
            graceBossBarTask = null;
        }
        if (graceBossBar != null) {
            graceBossBar.removeAll();
            graceBossBar = null;
        }
    }

    private Component startBroadcast() {
        ConfigManager config = plugin.getConfigManager();
        String message = config.smpStartBroadcast == null || config.smpStartBroadcast.isBlank()
            ? "<gold><bold>The SMP has started!</bold></gold>"
            : config.smpStartBroadcast;
        message = message
            .replace("{border}", formatNumber(config.smpStartedBorderDiameter))
            .replace("{grace}", formatDuration(graceMillisRemaining()));
        return MessageUtil.prefixedRaw(message);
    }

    private void presentSeasonOpeningToOnlinePlayers(Location origin) {
        List<Player> recipients = new java.util.ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOnline() && isSeasonIntroductionPending(player)) {
                recipients.add(player);
            }
        }
        if (recipients.isEmpty()) {
            return;
        }
        recipients.forEach(this::markSeasonIntroductionSeen);
        playSeasonIntroduction(recipients, origin, 4);
    }

    private boolean isSeasonIntroductionPending(Player player) {
        Long seenAt = player.getPersistentDataContainer().get(seasonIntroductionKey, PersistentDataType.LONG);
        ConfigManager config = plugin.getConfigManager();
        return shouldDeliverSeasonIntroduction(
            config.smpStartEnabled,
            config.smpStarted,
            config.smpStartedAt,
            seenAt
        );
    }

    private void markSeasonIntroductionSeen(Player player) {
        player.getPersistentDataContainer().set(
            seasonIntroductionKey,
            PersistentDataType.LONG,
            seasonIntroductionToken()
        );
    }

    private long seasonIntroductionToken() {
        ConfigManager config = plugin.getConfigManager();
        return config.smpStartEnabled ? Math.max(1L, config.smpStartedAt) : 1L;
    }

    private void playSeasonIntroduction(List<Player> recipients, Location fireworkOrigin, int fireworkCount) {
        Title title = Title.title(
            MM.deserialize("<gradient:#8b5cf6:#22d3ee><bold>SEASON V</bold></gradient>"),
            MM.deserialize("<white>Season of the Veil</white>"),
            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(900))
        );
        for (Player player : recipients) {
            if (!player.isOnline()) {
                continue;
            }
            player.showTitle(title);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
            player.sendMessage(MM.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
            player.sendMessage(MM.deserialize("<gradient:#8b5cf6:#22d3ee><bold>SEASON V • SEASON OF THE VEIL</bold></gradient>"));
            player.sendMessage(MM.deserialize("<gray>The barrier has fallen. Your story begins now.</gray>"));
            player.sendMessage(MM.deserialize("<gray>Meet <white>Mira the Guide</white> at spawn, then open <aqua>/menu</aqua>.</gray>"));
            player.sendMessage(MM.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
            if (plugin.getSpawnAmbienceManager() != null) {
                plugin.getSpawnAmbienceManager().playWelcomeSong(player);
            }
        }
        spawnSeasonFireworks(fireworkOrigin, fireworkCount);
    }

    private void spawnSeasonFireworks(Location origin, int count) {
        if (origin == null || origin.getWorld() == null || count <= 0) {
            return;
        }
        Location base = origin.clone().add(0.0D, 1.0D, 0.0D);
        for (int i = 0; i < count; i++) {
            int index = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!plugin.isEnabled() || base.getWorld() == null) {
                    return;
                }
                double angle = (Math.PI * 2.0D * index) / Math.max(1, count);
                Location launchAt = base.clone().add(Math.cos(angle) * 2.5D, 0.0D, Math.sin(angle) * 2.5D);
                Firework firework = base.getWorld().spawn(launchAt, Firework.class);
                firework.getPersistentDataContainer().set(
                    seasonLaunchFireworkKey,
                    PersistentDataType.BYTE,
                    (byte) 1
                );
                FireworkMeta meta = firework.getFireworkMeta();
                meta.setPower(1);
                meta.addEffect(FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.fromRGB(139, 92, 246), Color.fromRGB(34, 211, 238))
                    .withFade(Color.fromRGB(240, 171, 252))
                    .trail(true)
                    .flicker(true)
                    .build());
                firework.setFireworkMeta(meta);
            }, i * 12L);
        }
    }

    private void maybeSendGraceMessage(Player player) {
        long now = System.currentTimeMillis();
        Long next = nextGraceMessageAt.get(player.getUniqueId());
        if (next != null && next > now) {
            return;
        }

        nextGraceMessageAt.put(player.getUniqueId(), now + MESSAGE_THROTTLE_MS);
        String message = plugin.getConfigManager().smpGraceDenyMessage;
        if (message == null || message.isBlank()) {
            message = "<yellow>PvP is protected for <white>{time}</white>.</yellow>";
        }
        player.sendMessage(MessageUtil.prefixedRaw(message.replace("{time}", formatDuration(graceMillisRemaining()))));
    }

    private void maybeSendLockdownMessage(Player player) {
        long now = System.currentTimeMillis();
        Long next = nextLockdownMessageAt.get(player.getUniqueId());
        if (next != null && next > now) {
            return;
        }

        nextLockdownMessageAt.put(player.getUniqueId(), now + MESSAGE_THROTTLE_MS);
        String message = plugin.getConfigManager().smpLockdownDenyMessage;
        if (message == null || message.isBlank()) {
            message = "<yellow>The SMP has not started yet. Please wait for <white>/startsmp</white>.</yellow>";
        }
        player.sendMessage(MessageUtil.prefixedRaw(message));
    }

    private void maybeSendDimensionMessage(Player player, World.Environment environment) {
        long now = System.currentTimeMillis();
        Long next = nextDimensionMessageAt.get(player.getUniqueId());
        if (next != null && next > now) {
            return;
        }
        nextDimensionMessageAt.put(player.getUniqueId(), now + MESSAGE_THROTTLE_MS);

        int day = dimensionUnlockDay(environment);
        long remaining = dimensionUnlockMillisRemaining(environment, now);
        String dimension = environment == World.Environment.NETHER ? "Nether" : "End";
        String time = remaining == Long.MAX_VALUE ? "after /startsmp" : formatDuration(remaining);
        player.sendMessage(MessageUtil.warn(
            "The <white>" + dimension + "</white> opens on real-life Day <white>" + day
                + "</white> <gray>(" + time + ")</gray>."
        ));
    }

    private boolean shouldBlockPreStartCommand(Player player) {
        return player != null
            && !player.isOp()
            && plugin.getConfigManager().smpLockPluginCommandsBeforeStart
            && isPreStartLocked();
    }

    private boolean shouldBlockPreStartBlockEdit(Player player, World world) {
        return player != null
            && !player.isOp()
            && plugin.getConfigManager().smpLockBlockEditsBeforeStart
            && isPreStartLocked()
            && isPreStartProtectedWorld(world);
    }

    private boolean isProtectedPreStartMember(Player player) {
        return player != null
            && !player.isOp()
            && isPreStartLocked()
            && isInsidePreStartBarrier(player.getLocation());
    }

    private boolean isInsidePreStartBarrier(Location location) {
        return isInsidePreStartBarrier(location, 1.0D);
    }

    private boolean isInsidePreStartBarrier(Location location, double padding) {
        if (location == null || location.getWorld() == null || !isPreStartProtectedWorld(location.getWorld())) {
            return false;
        }

        Location center = location.getWorld().getSpawnLocation();
        double radius = plugin.getConfigManager().smpPreStartBorderDiameter / 2.0;
        return isInsideSquareBarrier(
            location.getX(),
            location.getZ(),
            center.getX(),
            center.getZ(),
            radius,
            padding
        );
    }

    static boolean isInsideSquareBarrier(
        double x,
        double z,
        double centerX,
        double centerZ,
        double radius,
        double padding
    ) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(centerX) || !Double.isFinite(centerZ)) {
            return false;
        }
        double limit = Math.max(0.0D, radius) + Math.max(0.0D, padding);
        return Math.abs(x - centerX) <= limit && Math.abs(z - centerZ) <= limit;
    }

    private boolean isPreStartProtectedWorld(World world) {
        if (world == null) {
            return false;
        }
        World configured = configuredWorld();
        if (configured != null) {
            return configured.getUID().equals(world.getUID());
        }
        return world.getName().equalsIgnoreCase(plugin.getConfigManager().smpStartWorld);
    }

    private String commandRoot(String message) {
        if (message == null) {
            return "";
        }
        String trimmed = message.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.isBlank()) {
            return "";
        }
        int space = trimmed.indexOf(' ');
        String root = space < 0 ? trimmed : trimmed.substring(0, space);
        int namespace = root.indexOf(':');
        if (namespace >= 0 && namespace + 1 < root.length()) {
            root = root.substring(namespace + 1);
        }
        return root.toLowerCase(Locale.ROOT);
    }

    private long graceMillisRemaining() {
        ConfigManager config = plugin.getConfigManager();
        if (!config.smpStarted) {
            return Long.MAX_VALUE;
        }
        long graceMs = Duration.ofMinutes(config.smpPostStartGraceMinutes).toMillis();
        long endsAt = config.smpStartedAt + graceMs;
        return Math.max(0L, endsAt - System.currentTimeMillis());
    }

    public boolean isDimensionUnlocked(World.Environment environment) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.smpStartEnabled) {
            return true;
        }
        return dimensionUnlockMillisRemaining(environment, System.currentTimeMillis()) <= 0L;
    }

    private String dimensionStatus(World.Environment environment) {
        long remaining = dimensionUnlockMillisRemaining(environment, System.currentTimeMillis());
        if (remaining <= 0L) {
            return "unlocked";
        }
        if (remaining == Long.MAX_VALUE) {
            return "locked until /startsmp";
        }
        return "unlocks in " + formatDuration(remaining);
    }

    private int dimensionUnlockDay(World.Environment environment) {
        ConfigManager config = plugin.getConfigManager();
        return environment == World.Environment.NETHER ? config.smpNetherUnlockDay : config.smpEndUnlockDay;
    }

    private long dimensionUnlockMillisRemaining(World.Environment environment, long now) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.smpStarted || config.smpStartedAt <= 0L) {
            return Long.MAX_VALUE;
        }
        return millisUntilUnlock(now, config.smpStartedAt, dimensionUnlockDay(environment));
    }

    static long millisUntilUnlock(long now, long startedAt, int unlockDay) {
        if (startedAt <= 0L) {
            return Long.MAX_VALUE;
        }
        long unlockAt = unlockAtEpochMillis(startedAt, unlockDay);
        return Math.max(0L, unlockAt - Math.max(0L, now));
    }

    static long unlockAtEpochMillis(long startedAt, int unlockDay) {
        int daysAfterStart = Math.max(0, unlockDay - 1);
        long delay = Duration.ofDays(daysAfterStart).toMillis();
        if (startedAt > Long.MAX_VALUE - delay) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, startedAt) + delay;
    }

    private World configuredWorld() {
        return Bukkit.getWorld(plugin.getConfigManager().smpStartWorld);
    }

    private Player graceAttacker(EntityDamageEvent event, Player victim) {
        Player attacker = attackingPlayer(event.getDamageSource());
        if (attacker == null && event instanceof EntityDamageByEntityEvent byEntityEvent) {
            attacker = attackingPlayer(byEntityEvent.getDamager());
        }
        if (attacker == null && isEnvironmentalPvpCause(event.getCause())) {
            attacker = recentHazardOwner(event, victim);
        }
        return attacker == null || attacker.equals(victim) ? null : attacker;
    }

    private Player attackingPlayer(DamageSource damageSource) {
        if (damageSource == null) {
            return null;
        }

        Player player = attackingPlayer(damageSource.getCausingEntity());
        if (player != null) {
            return player;
        }
        return attackingPlayer(damageSource.getDirectEntity());
    }

    private Player attackingPlayer(Entity damager) {
        return attackingPlayer(damager, 0);
    }

    private Player attackingPlayer(Entity damager, int depth) {
        if (damager == null) {
            return null;
        }
        if (depth > 4) {
            return null;
        }
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
            if (source instanceof Entity sourceEntity) {
                return attackingPlayer(sourceEntity, depth + 1);
            }
        }
        if (damager instanceof Tameable tameable && tameable.getOwner() instanceof Player player) {
            return player;
        }
        if (damager instanceof TNTPrimed tnt) {
            return attackingPlayer(tnt.getSource(), depth + 1);
        }
        if (damager instanceof AreaEffectCloud cloud) {
            ProjectileSource source = cloud.getSource();
            if (source instanceof Player player) {
                return player;
            }
            if (source instanceof Entity sourceEntity) {
                return attackingPlayer(sourceEntity, depth + 1);
            }
        }
        return null;
    }

    private void rememberGraceHazard(Location location, Player owner) {
        if (location == null || location.getWorld() == null || owner == null) {
            return;
        }

        long now = System.currentTimeMillis();
        cleanupExpiredHazards(now);
        recentGraceHazards.put(
            BlockKey.of(location),
            new HazardAttribution(
                owner.getUniqueId(),
                location.getWorld().getUID(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                now + HAZARD_ATTRIBUTION_MS
            )
        );
    }

    private Player recentHazardOwner(EntityDamageEvent event, Player victim) {
        long now = System.currentTimeMillis();
        cleanupExpiredHazards(now);

        Location victimLocation = victim.getLocation();
        Location sourceLocation = event.getDamageSource() == null ? null : event.getDamageSource().getSourceLocation();
        double maxDistanceSquared = isExplosionCause(event.getCause())
            ? HAZARD_EXPLOSION_RADIUS_SQUARED
            : HAZARD_DAMAGE_RADIUS_SQUARED;

        for (HazardAttribution hazard : recentGraceHazards.values()) {
            if (hazard.expiresAt() <= now) {
                continue;
            }
            double distanceSquared = Math.min(
                hazard.distanceSquared(victimLocation),
                hazard.distanceSquared(sourceLocation)
            );
            if (distanceSquared > maxDistanceSquared) {
                continue;
            }

            Player owner = Bukkit.getPlayer(hazard.ownerId());
            if (owner != null && owner.isOnline()) {
                return owner;
            }
        }
        return null;
    }

    private void cleanupExpiredHazards(long now) {
        recentGraceHazards.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    private boolean isEnvironmentalPvpCause(EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case BLOCK_EXPLOSION, CONTACT, ENTITY_EXPLOSION, FIRE, FIRE_TICK, HOT_FLOOR, LAVA, MAGIC, POISON, WITHER, FREEZE -> true;
            default -> false;
        };
    }

    private boolean isExplosionCause(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
            || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;
    }

    private boolean isHostileSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Monster) {
            return true;
        }

        String type = event.getEntityType().name();
        return type.contains("SLIME")
            || type.contains("MAGMA_CUBE")
            || type.contains("PHANTOM")
            || type.contains("SHULKER")
            || type.contains("BREEZE");
    }

    private boolean isHazardBlock(Material material) {
        return switch (material) {
            case CACTUS,
                CAMPFIRE,
                FIRE,
                LAVA,
                MAGMA_BLOCK,
                POINTED_DRIPSTONE,
                POWDER_SNOW,
                SOUL_CAMPFIRE,
                SOUL_FIRE,
                SWEET_BERRY_BUSH,
                TNT,
                WITHER_ROSE -> true;
            default -> false;
        };
    }

    private String formatDuration(long millis) {
        if (millis == Long.MAX_VALUE) {
            return "until /startsmp";
        }
        long seconds = Math.max(0L, (millis + 999L) / 1000L);
        long days = seconds / 86_400L;
        long hours = (seconds % 86_400L) / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        long remainingSeconds = seconds % 60L;
        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes <= 0L) {
            return remainingSeconds + "s";
        }
        if (remainingSeconds == 0L) {
            return minutes + "m";
        }
        return minutes + "m " + remainingSeconds + "s";
    }

    private String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return Long.toString(Math.round(value));
        }
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {
        private static BlockKey of(Location location) {
            return new BlockKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }

    private record HazardAttribution(UUID ownerId, UUID worldId, int x, int y, int z, long expiresAt) {
        private double distanceSquared(Location location) {
            if (location == null || location.getWorld() == null || !location.getWorld().getUID().equals(worldId)) {
                return Double.MAX_VALUE;
            }
            double dx = (x + 0.5) - location.getX();
            double dy = (y + 0.5) - location.getY();
            double dz = (z + 0.5) - location.getZ();
            return (dx * dx) + (dy * dy) + (dz * dz);
        }
    }

    public record StartResult(boolean success, String message) {}
}
