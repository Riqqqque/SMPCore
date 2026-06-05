package me.rique.smpcore.smp;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.config.ConfigManager;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.util.PluginCommandRoots;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
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
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SmpStartManager implements Listener {

    private static final long MESSAGE_THROTTLE_MS = 2500L;
    private static final long HAZARD_ATTRIBUTION_MS = 30_000L;
    private static final double HAZARD_DAMAGE_RADIUS_SQUARED = 9.0;
    private static final double HAZARD_EXPLOSION_RADIUS_SQUARED = 144.0;
    private final SMPCore plugin;
    private final Map<UUID, Long> nextGraceMessageAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextLockdownMessageAt = new ConcurrentHashMap<>();
    private final Map<BlockKey, HazardAttribution> recentGraceHazards = new ConcurrentHashMap<>();
    private BossBar graceBossBar;
    private BukkitTask graceBossBarTask;

    public SmpStartManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void applyConfiguredState() {
        if (!plugin.getConfigManager().smpStartEnabled) {
            stopGraceBossBar();
            return;
        }

        World world = configuredWorld();
        if (world == null) {
            plugin.getLogger().warning("SMP start world '" + plugin.getConfigManager().smpStartWorld + "' is not loaded.");
            return;
        }

        applyBorder(world, false);
        updateGraceBossBarState();
    }

    public void shutdown() {
        stopGraceBossBar();
        nextGraceMessageAt.clear();
        nextLockdownMessageAt.clear();
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
        Bukkit.broadcast(startBroadcast());
        world.playSound(world.getSpawnLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
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

        String border = formatNumber(config.smpStarted ? config.smpStartedBorderDiameter : config.smpPreStartBorderDiameter);
        if (!config.smpStarted) {
            return MessageUtil.info("SMP has not started. Border: <white>" + border + "</white>. PvP is blocked.");
        }

        if (isGraceActive()) {
            return MessageUtil.info(
                "SMP is started. Border: <white>" + border + "</white>. PvP unlocks in <white>"
                    + formatDuration(graceMillisRemaining()) + "</white>."
            );
        }

        return MessageUtil.info("SMP is started. Border: <white>" + border + "</white>. PvP is unlocked.");
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

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (!event.getWorld().getName().equalsIgnoreCase(plugin.getConfigManager().smpStartWorld)) {
            return;
        }
        applyConfiguredState();
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
        if (!shouldBlockPreStartBlockEdit(event.getPlayer(), event.getBlock().getWorld())) {
            return;
        }

        event.setCancelled(true);
        maybeSendLockdownMessage(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreStartBlockPlace(BlockPlaceEvent event) {
        if (!shouldBlockPreStartBlockEdit(event.getPlayer(), event.getBlockPlaced().getWorld())) {
            return;
        }

        event.setCancelled(true);
        maybeSendLockdownMessage(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreStartBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!shouldBlockPreStartBlockEdit(event.getPlayer(), event.getBlock().getWorld())) {
            return;
        }

        event.setCancelled(true);
        maybeSendLockdownMessage(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreStartBucketFill(PlayerBucketFillEvent event) {
        if (!shouldBlockPreStartBlockEdit(event.getPlayer(), event.getBlock().getWorld())) {
            return;
        }

        event.setCancelled(true);
        maybeSendLockdownMessage(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreStartBlockIgnite(BlockIgniteEvent event) {
        Player player = event.getPlayer();
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

    private void applyBorder(World world, boolean fromStartCommand) {
        ConfigManager config = plugin.getConfigManager();
        WorldBorder border = world.getWorldBorder();
        Location center = world.getSpawnLocation();
        border.setCenter(center.getX(), center.getZ());

        double targetSize = config.smpStarted
            ? config.smpStartedBorderDiameter
            : config.smpPreStartBorderDiameter;
        if (fromStartCommand && config.smpBorderExpandSeconds > 0) {
            border.changeSize(targetSize, config.smpBorderExpandSeconds);
        } else {
            border.setSize(targetSize);
        }
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
            if (!graceBossBar.getPlayers().contains(player)) {
                graceBossBar.addPlayer(player);
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
        if (location == null || location.getWorld() == null || !isPreStartProtectedWorld(location.getWorld())) {
            return false;
        }

        Location center = location.getWorld().getSpawnLocation();
        double radius = plugin.getConfigManager().smpPreStartBorderDiameter / 2.0;
        double dx = location.getX() - center.getX();
        double dz = location.getZ() - center.getZ();
        double generousRadius = radius + 1.0;
        return (dx * dx) + (dz * dz) <= generousRadius * generousRadius;
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
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
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
