package me.rique.smpcore.player;

import io.papermc.paper.event.entity.EntityMoveEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.config.ConfigManager;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SpawnProtectionListener implements Listener {

    private static final String FLAG_BUILD = "build";
    private static final String FLAG_INTERACT = "interact";
    private static final String FLAG_PVP = "pvp";
    private static final String FLAG_HUNGER_DRAIN = "hunger-drain";
    private static final String FLAG_MOB_GRIEF = "mob-grief";
    private static final String FLAG_MOB_SPAWNS = "mob-spawns";
    private static final String FLAG_MOB_ENTRY = "mob-entry";
    private static final String FLAG_EXPLOSIONS = "explosions";
    private static final String FLAG_FIRE = "fire";
    private static final String FLAG_LIQUIDS = "liquids";
    private static final String FLAG_REDSTONE = "redstone";
    private static final String FLAG_ENVIRONMENT = "environment";
    private static final String FLAG_NATURAL_DECAY = "natural-decay";
    private static final String FLAG_CROP_TRAMPLE = "crop-trample";
    private static final String FLAG_BONE_MEAL = "bone-meal";
    private static final String FLAG_WEATHER_LOCK = "weather-lock";
    private static final String FLAG_ENTITY_EDIT = "entity-edit";
    private static final long MESSAGE_THROTTLE_MS = 2500L;
    private static final long MOB_CLEANUP_INTERVAL_TICKS = 100L;
    private static final long[] MOB_CLEANUP_RECHECK_DELAYS_TICKS = {1L, 20L, 100L};
    private static final long MOB_DEBUG_LOG_THROTTLE_MS = 250L;
    private static final int MOB_DEBUG_MARGIN_BLOCKS = 32;
    private static final long ADMIN_SPAWN_EGG_WINDOW_MS = 2000L;
    private static final double ADMIN_SPAWN_EGG_RADIUS_SQUARED = 36.0;
    private static final long PUBLIC_INTERACTION_CONFIRM_WINDOW_MS = 8000L;
    private static final long BONE_MEAL_GROWTH_ALLOWANCE_MS = 1500L;
    private static final long WEATHER_BYPASS_WINDOW_MS = 2000L;
    private static final int CLEAR_WEATHER_DURATION_TICKS = 20 * 60 * 60;
    private static final String ADMIN_PLACED_MOB_TAG = "smpcore_admin_placed_mob";

    private final SMPCore plugin;
    private final Map<UUID, Long> nextMessageAt = new ConcurrentHashMap<>();
    private final Map<UUID, AdminSpawnEggAllowance> adminSpawnEggAllowances = new ConcurrentHashMap<>();
    private final Map<UUID, PendingPublicInteractionToggle> pendingPublicInteractionToggles = new ConcurrentHashMap<>();
    private final Map<UUID, Long> weatherLockBypassWorlds = new ConcurrentHashMap<>();
    private final Map<String, Long> allowedBoneMealGrowthBlocks = new ConcurrentHashMap<>();
    private final List<BoneMealGrowthArea> allowedBoneMealGrowthAreas = new CopyOnWriteArrayList<>();
    private final NamespacedKey npcKey;
    private final NamespacedKey spawnNpcKey;
    private final NamespacedKey reforgeNpcKey;
    private final NamespacedKey adminSpawnStickKey;
    private final NamespacedKey adminPlacedMobKey;
    private final GsitSeatBridge gsitSeatBridge;
    private BukkitTask cleanupTask;
    private long nextMobDebugLogAt;
    private int skippedMobDebugLogs;

    public SpawnProtectionListener(SMPCore plugin) {
        this.plugin = plugin;
        this.npcKey = new NamespacedKey(plugin, "npc");
        this.spawnNpcKey = new NamespacedKey(plugin, "spawn_npc");
        this.reforgeNpcKey = new NamespacedKey(plugin, "reforge_dwarf_npc");
        this.adminSpawnStickKey = new NamespacedKey(plugin, "admin_spawn_stick");
        this.adminPlacedMobKey = new NamespacedKey(plugin, "admin_placed_mob");
        this.gsitSeatBridge = new GsitSeatBridge(plugin);
    }

    public void start() {
        disableVanillaSpawnProtectionIfConfigured();
        logActiveProtectionArea();
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        cleanupTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            () -> {
                cleanupProtectedSpawnMobs();
                cleanupTransientAllowances();
            },
            20L,
            MOB_CLEANUP_INTERVAL_TICKS
        );
        scheduleProtectedSpawnMobCleanup();
        Bukkit.getScheduler().runTask(plugin, () -> enforceWeatherLock());
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        nextMessageAt.clear();
        adminSpawnEggAllowances.clear();
        pendingPublicInteractionToggles.clear();
        weatherLockBypassWorlds.clear();
        allowedBoneMealGrowthBlocks.clear();
        allowedBoneMealGrowthAreas.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        nextMessageAt.remove(playerId);
        adminSpawnEggAllowances.remove(playerId);
        pendingPublicInteractionToggles.remove(playerId);
    }

    private void cleanupTransientAllowances() {
        long now = System.currentTimeMillis();
        adminSpawnEggAllowances.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis < now);
        weatherLockBypassWorlds.entrySet().removeIf(entry -> entry.getValue() < now);
        allowedBoneMealGrowthBlocks.entrySet().removeIf(entry -> entry.getValue() < now);
        allowedBoneMealGrowthAreas.removeIf(area -> area.expiresAtMillis() < now);
        pendingPublicInteractionToggles.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() < now);
    }

    private void disableVanillaSpawnProtectionIfConfigured() {
        ConfigManager config = plugin.getConfigManager();
        if (!config.spawnProtectionDisableVanillaSpawnProtection) {
            return;
        }

        Path propertiesPath = plugin.getServer().getWorldContainer().toPath().resolve("server.properties");
        if (!Files.isRegularFile(propertiesPath)) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(propertiesPath, StandardCharsets.UTF_8);
            List<String> updated = new ArrayList<>(lines.size() + 1);
            boolean found = false;
            boolean changed = false;
            int previous = 0;
            for (String line : lines) {
                if (!line.startsWith("spawn-protection=")) {
                    updated.add(line);
                    continue;
                }

                found = true;
                previous = parseVanillaSpawnProtection(line);
                if (previous == 0) {
                    updated.add(line);
                } else {
                    updated.add("spawn-protection=0");
                    changed = true;
                }
            }
            if (!found) {
                updated.add("spawn-protection=0");
                changed = true;
                previous = -1;
            }
            if (!changed) {
                return;
            }

            Files.write(propertiesPath, updated, StandardCharsets.UTF_8);
            plugin.getLogger().warning(
                "server.properties had spawn-protection="
                    + (previous < 0 ? "missing" : previous)
                    + ". Wrote spawn-protection=0 so the selected SMPCore region controls spawn."
                    + " Restart once if this message appears during startup."
            );
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not update server.properties spawn-protection: " + ex.getMessage());
        }
    }

    private int parseVanillaSpawnProtection(String line) {
        int equals = line.indexOf('=');
        if (equals < 0 || equals == line.length() - 1) {
            return -1;
        }
        try {
            return Math.max(0, Integer.parseInt(line.substring(equals + 1).trim()));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void logActiveProtectionArea() {
        ConfigManager config = plugin.getConfigManager();
        if (!config.spawnProtectionEnabled) {
            plugin.getLogger().warning("Spawn protection is disabled. Run /spawnprotect on to re-enable it.");
            return;
        }
        plugin.getLogger().info("Spawn protection active: " + debugRegionSummary(config)
            + "; flags=" + String.join(",", config.spawnProtectionFlags)
            + "; builders=" + config.spawnProtectionAllowedBuilders.size());
    }

    public ItemStack createAdminSpawnStick() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(MessageUtil.parse("<gold><bold>Admin Spawn Stick</bold></gold>"));
        meta.lore(CustomLoreUtil.wrapLoreLines(List.of(
            MessageUtil.parse("<gray>Left-click: inspect a spawn block.</gray>"),
            MessageUtil.parse("<gray>Right-click: arm public-use toggle.</gray>"),
            MessageUtil.parse("<gray>Right-click same block again to confirm.</gray>"),
            MessageUtil.parse("<dark_gray>Only works for spawn builders/admins.</dark_gray>")
        )));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(adminSpawnStickKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isAdminSpawnStick(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(adminSpawnStickKey, PersistentDataType.BYTE);
    }

    public boolean isProtected(Location location) {
        return isProtected(location, false);
    }

    public boolean blocksMobSpawns(Location location) {
        return flagEnabled(FLAG_MOB_SPAWNS) && isProtectedColumn(location);
    }

    public boolean isPublicInteractionBlock(Location location) {
        return plugin.getConfigManager().isSpawnProtectionPublicInteraction(location);
    }

    private boolean isProtectedColumn(Location location) {
        return isProtected(location, true);
    }

    private boolean isProtected(Location location, boolean forceFullHeight) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.spawnProtectionEnabled) {
            return false;
        }
        if (location == null || location.getWorld() == null) {
            return false;
        }

        if (config.spawnProtectionRegionSet) {
            if (!isConfiguredWorld(location.getWorld(), config.spawnProtectionRegionWorld)) {
                return false;
            }
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            return x >= config.spawnProtectionMinX
                && x <= config.spawnProtectionMaxX
                && (forceFullHeight
                    || config.spawnProtectionRegionFullHeight
                    || (y >= config.spawnProtectionMinY && y <= config.spawnProtectionMaxY))
                && z >= config.spawnProtectionMinZ
                && z <= config.spawnProtectionMaxZ;
        }

        if (!isConfiguredWorld(location.getWorld(), config.spawnWorld)) {
            return false;
        }
        Location spawn = location.getWorld().getSpawnLocation();
        double radius = config.spawnProtectionRadius;
        double dx = location.getX() - spawn.getX();
        double dz = location.getZ() - spawn.getZ();
        return (dx * dx) + (dz * dz) <= radius * radius;
    }

    public boolean canEditSpawn(Player player) {
        if (player == null) {
            return false;
        }
        return canBypassSpawnProtection(player)
            || player.hasPermission("smpcore.spawnprotect.admin")
            || player.hasPermission("smpcore.spawnprotect.build")
            || plugin.getConfigManager().isSpawnProtectionBuilder(
                player.getName(),
                player.getUniqueId().toString()
            );
    }

    public boolean canBypassSpawnSafety(Player player) {
        return canBypassSpawnProtection(player)
            || plugin.getDuelManager() != null && plugin.getDuelManager().isDuelParticipant(player);
    }

    public boolean blocksProtectedSpawnDeath(Player player) {
        return player != null && !canBypassSpawnSafety(player) && isProtectedColumn(player.getLocation());
    }

    public boolean canUseCombatAbility(Player attacker, LivingEntity target) {
        if (attacker == null || target == null) {
            return false;
        }
        return canBypassSpawnSafety(attacker)
            || (!isProtectedColumn(attacker.getLocation()) && !isProtectedColumn(target.getLocation()));
    }

    public boolean blocksUnsafeAbility(Player player, Location location) {
        return !canBypassSpawnSafety(player) && isProtectedColumn(location);
    }

    public boolean blocksUnsafeAbilityArea(Player player, Location center, double radius) {
        return blocksUnsafeAbilityArea(player, center, radius, radius, radius);
    }

    public boolean blocksUnsafeAbilityArea(Player player, Location center, double radiusX, double radiusY, double radiusZ) {
        return !canBypassSpawnSafety(player)
            && intersectsProtectedArea(center, Math.max(0.0, radiusX), Math.max(0.0, radiusY), Math.max(0.0, radiusZ));
    }

    public void sendUnsafeAbilityDeny(Player player) {
        if (player != null) {
            player.sendMessage(MessageUtil.warn("That ability is blocked in protected spawn."));
        }
    }

    public void markSpawnNpc(Entity entity) {
        if (entity == null) {
            return;
        }
        entity.getPersistentDataContainer().set(spawnNpcKey, PersistentDataType.BYTE, (byte) 1);
        entity.addScoreboardTag("smpcore_spawn_npc");
    }

    public void markAdminPlacedMob(Entity entity) {
        if (entity == null) {
            return;
        }
        entity.getPersistentDataContainer().set(adminPlacedMobKey, PersistentDataType.BYTE, (byte) 1);
        entity.addScoreboardTag(ADMIN_PLACED_MOB_TAG);
    }

    public void runWithWeatherLockBypass(World world, Runnable action) {
        if (world == null || action == null) {
            return;
        }
        weatherLockBypassWorlds.put(world.getUID(), System.currentTimeMillis() + WEATHER_BYPASS_WINDOW_MS);
        try {
            action.run();
        } finally {
            weatherLockBypassWorlds.remove(world.getUID());
        }
    }

    public boolean showProtectedAreaOutline(Player player) {
        if (!canDrawProtectedAreaOutline(player)) {
            return false;
        }
        drawProtectedAreaOutline(player);
        for (long delay = 10L; delay <= 80L; delay += 10L) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> drawProtectedAreaOutline(player), delay);
        }
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        scheduleProtectedSpawnMobCleanup(event.getPlayer().getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        scheduleProtectedSpawnMobCleanup(event.getPlayer().getWorld());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)
            || event.getFoodLevel() >= player.getFoodLevel()
            || !shouldBlockHungerDrain(player)) {
            return;
        }

        event.setCancelled(true);
        player.setExhaustion(0.0f);
        player.setSaturation(Math.max(player.getSaturation(), 5.0f));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExhaustion(EntityExhaustionEvent event) {
        if (!(event.getEntity() instanceof Player player) || !shouldBlockHungerDrain(player)) {
            return;
        }

        event.setCancelled(true);
        event.setExhaustion(0.0f);
        player.setExhaustion(0.0f);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBlockBreakEarly(BlockBreakEvent event) {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().allowsArenaBlockBreak(event.getPlayer(), event.getBlock())) return;
        if (plugin.getMarketStallManager() != null && plugin.getMarketStallManager().handlesSpawnBlockChange(event.getBlock())) return;
        enforceProtectedBlockEvent(event, event.getPlayer(), event.getBlock(), FLAG_BUILD, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().allowsArenaBlockBreak(event.getPlayer(), event.getBlock())) return;
        if (plugin.getMarketStallManager() != null && plugin.getMarketStallManager().handlesSpawnBlockChange(event.getBlock())) return;
        enforceProtectedBlockEvent(event, event.getPlayer(), event.getBlock(), FLAG_BUILD, true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBlockBreakFinal(BlockBreakEvent event) {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().allowsArenaBlockBreak(event.getPlayer(), event.getBlock())) return;
        if (plugin.getMarketStallManager() != null && plugin.getMarketStallManager().handlesSpawnBlockChange(event.getBlock())) return;
        enforceProtectedBlockEvent(event, event.getPlayer(), event.getBlock(), FLAG_BUILD, false);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBlockPlaceEarly(BlockPlaceEvent event) {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().allowsArenaBlockPlacement(event.getPlayer(), event.getBlockPlaced())) return;
        if (plugin.getMarketStallManager() != null && plugin.getMarketStallManager().handlesSpawnBlockChange(event.getBlockPlaced())) return;
        enforceProtectedBlockEvent(event, event.getPlayer(), event.getBlockPlaced(), FLAG_BUILD, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().allowsArenaBlockPlacement(event.getPlayer(), event.getBlockPlaced())) return;
        if (plugin.getMarketStallManager() != null && plugin.getMarketStallManager().handlesSpawnBlockChange(event.getBlockPlaced())) return;
        enforceProtectedBlockEvent(event, event.getPlayer(), event.getBlockPlaced(), FLAG_BUILD, true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBlockPlaceFinal(BlockPlaceEvent event) {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().allowsArenaBlockPlacement(event.getPlayer(), event.getBlockPlaced())) return;
        if (plugin.getMarketStallManager() != null && plugin.getMarketStallManager().handlesSpawnBlockChange(event.getBlockPlaced())) return;
        enforceProtectedBlockEvent(event, event.getPlayer(), event.getBlockPlaced(), FLAG_BUILD, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerHarvestBlock(PlayerHarvestBlockEvent event) {
        if (shouldBlock(event.getPlayer(), event.getHarvestedBlock(), FLAG_BUILD)) {
            event.setCancelled(true);
            sendDeny(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().allowsArenaBucket(event.getPlayer(), event.getBlock())) return;
        if (shouldBlock(event.getPlayer(), event.getBlock(), FLAG_BUILD)) {
            event.setCancelled(true);
            sendDeny(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().allowsArenaBucket(event.getPlayer(), event.getBlock())) return;
        if (shouldBlock(event.getPlayer(), event.getBlock(), FLAG_BUILD)) {
            event.setCancelled(true);
            sendDeny(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent event) {
        if (shouldBlock(event.getPlayer(), event.getEntity().getLocation(), FLAG_BUILD)) {
            event.setCancelled(true);
            sendDeny(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        if (plugin.getDuelManager() != null && plugin.getDuelManager().allowsArenaInteraction(event)) {
            return;
        }

        if (plugin.getMarketStallManager() != null && plugin.getMarketStallManager().allowsSpawnInteraction(event)) {
            return;
        }

        if (plugin.getPlayerShopListener() != null && plugin.getPlayerShopListener().isShopPurchaseSign(block)) {
            return;
        }

        if (handleAdminSpawnStick(event, block)) {
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
            && block.getType() == Material.WATER
            && plugin.getFisherManager() != null
            && plugin.getFisherManager().canLaunchAtFisherBeach(event.getPlayer(), event.getItem(), block.getLocation())) {
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && isBoneMealUse(event) && isProtected(block.getLocation())) {
            if (flagEnabled(FLAG_BONE_MEAL) && !canEditSpawn(event.getPlayer())) {
                event.setCancelled(true);
                event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
                event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
                sendDeny(event.getPlayer());
                return;
            }
            if (applyProtectedBoneMeal(event, block)) {
                return;
            }
            if (!canEditSpawn(event.getPlayer())) {
                if (isBoneMealTarget(block)) {
                    return;
                }
                if (isRestrictedSpawnUtilityBlock(block.getType())) {
                    event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
                    event.setUseItemInHand(org.bukkit.event.Event.Result.ALLOW);
                    return;
                }
            }
        }

        if (handleProtectedSpawnGsitSideClick(event, block)) {
            return;
        }

        if (interactionWasDenied(event)) {
            return;
        }

        if (event.getAction() == Action.PHYSICAL && shouldBlockCropTrample(event.getPlayer(), block)) {
            event.setCancelled(true);
            sendDeny(event.getPlayer());
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && shouldCancelSpawnEggUse(event.getPlayer(), block.getLocation())) {
            event.setCancelled(true);
            sendDeny(event.getPlayer());
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && shouldDenySpawnUtilityBlockUse(event.getPlayer(), block)) {
            event.setCancelled(true);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            sendDeny(event.getPlayer());
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && isAlwaysAllowedSpawnUtilityBlock(block)) {
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && isPublicInteractionBlock(block.getLocation())) {
            return;
        }

        if (isGsitSeatInteraction(event, block)) {
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
            && plugin.getTavernManager() != null
            && plugin.getTavernManager().isStation(block.getLocation())) {
            return;
        }

        if (!shouldBlock(event.getPlayer(), block, FLAG_INTERACT)) {
            return;
        }

        event.setCancelled(true);
        sendDeny(event.getPlayer());
    }

    private boolean isGsitSeatInteraction(PlayerInteractEvent event, Block block) {
        return event.getAction() == Action.RIGHT_CLICK_BLOCK
            && event.getHand() == EquipmentSlot.HAND
            && Bukkit.getPluginManager().isPluginEnabled("GSit")
            && isEmptyHandGsitSeat(block.getType(), event.getItem() == null ? null : event.getItem().getType());
    }

    private boolean handleProtectedSpawnGsitSideClick(PlayerInteractEvent event, Block block) {
        if (!isProtected(block.getLocation())
            || interactionWasDenied(event)
            || !isGsitSeatInteraction(event, block)
            || !GsitSeatBridge.shouldUseSideClickFallback(event.getBlockFace())
            || !gsitSeatBridge.trySitOnStair(block, event.getPlayer())) {
            return false;
        }

        event.setCancelled(true);
        return true;
    }

    static boolean isGsitSeatMaterial(Material material) {
        return material != null && material.name().endsWith("_STAIRS");
    }

    static boolean isEmptyHandGsitSeat(Material seatMaterial, Material heldMaterial) {
        return isGsitSeatMaterial(seatMaterial) && (heldMaterial == null || heldMaterial == Material.AIR);
    }

    private boolean handleAdminSpawnStick(PlayerInteractEvent event, Block block) {
        if (!isAdminSpawnStick(event.getItem())) {
            return false;
        }
        event.setCancelled(true);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);

        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return true;
        }

        Player player = event.getPlayer();
        if (!canEditSpawn(player)) {
            player.sendMessage(MessageUtil.warn("Only spawn builders can use this stick."));
            return true;
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            inspectPublicInteractionBlock(player, block);
            return true;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return true;
        }
        togglePublicInteractionBlock(player, block);
        return true;
    }

    private void inspectPublicInteractionBlock(Player player, Block block) {
        boolean protectedBlock = isProtected(block.getLocation());
        boolean publicUse = isPublicInteractionBlock(block.getLocation());
        player.sendMessage(MessageUtil.info(
            "<white>" + block.getType().name().toLowerCase(Locale.ROOT) + "</white> at <white>"
                + blockLocation(block)
                + "</white>: protected=<white>"
                + (protectedBlock ? "yes" : "no")
                + "</white>, public-use=<white>"
                + (publicUse ? "yes" : "no")
                + "</white>."
        ));
    }

    private void togglePublicInteractionBlock(Player player, Block block) {
        if (!isProtected(block.getLocation())) {
            player.sendMessage(MessageUtil.warn("That block is outside protected spawn."));
            return;
        }

        ConfigManager config = plugin.getConfigManager();
        String key = config.spawnProtectionBlockKey(block.getLocation());
        if (key == null) {
            player.sendMessage(MessageUtil.error("Could not read that block location."));
            return;
        }

        PublicInteractionAction action = config.isSpawnProtectionPublicInteraction(block.getLocation())
            ? PublicInteractionAction.REMOVE
            : PublicInteractionAction.ADD;
        long now = System.currentTimeMillis();
        PendingPublicInteractionToggle pending = pendingPublicInteractionToggles.get(player.getUniqueId());
        if (pending == null || pending.expiresAtMillis < now || !pending.blockKey.equals(key) || pending.action != action) {
            pendingPublicInteractionToggles.put(
                player.getUniqueId(),
                new PendingPublicInteractionToggle(key, action, now + PUBLIC_INTERACTION_CONFIRM_WINDOW_MS)
            );
            player.sendMessage(MessageUtil.info(
                (action == PublicInteractionAction.ADD ? "Arm public use for " : "Arm public-use removal for ")
                    + "<white>" + blockLocation(block) + "</white>. Right-click it again to confirm."
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.55f, 1.25f);
            player.spawnParticle(Particle.END_ROD, block.getLocation().add(0.5, 0.65, 0.5), 8, 0.25, 0.25, 0.25, 0.01);
            return;
        }

        pendingPublicInteractionToggles.remove(player.getUniqueId());
        boolean changed = action == PublicInteractionAction.ADD
            ? config.addSpawnProtectionPublicInteraction(block.getLocation())
            : config.removeSpawnProtectionPublicInteraction(block.getLocation());
        if (!changed) {
            player.sendMessage(MessageUtil.info("That public-use rule was already " + (action == PublicInteractionAction.ADD ? "set." : "removed.")));
            return;
        }

        player.sendMessage(MessageUtil.success(
            (action == PublicInteractionAction.ADD ? "Public players can now use " : "Public players can no longer use ")
                + "<white>" + blockLocation(block) + "</white>."
        ));
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.75f, action == PublicInteractionAction.ADD ? 1.35f : 0.75f);
        block.getWorld().spawnParticle(
            action == PublicInteractionAction.ADD ? Particle.HAPPY_VILLAGER : Particle.SMOKE,
            block.getLocation().add(0.5, 0.8, 0.5),
            16,
            0.35,
            0.35,
            0.35,
            0.02
        );
    }

    private String blockLocation(Block block) {
        return block.getWorld().getName()
            + " "
            + block.getX()
            + ", "
            + block.getY()
            + ", "
            + block.getZ();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Location location = event.getLocation();
        if (!flagEnabled(FLAG_MOB_SPAWNS)) {
            debugMobSpawn("creature-spawn", event.getEntity(), location, "allow", "mob-spawns flag is off; reason=" + event.getSpawnReason());
            return;
        }
        if (!isProtectedColumn(location)) {
            debugMobSpawn("creature-spawn", event.getEntity(), location, "allow", "outside protected spawn column; reason=" + event.getSpawnReason());
            return;
        }
        if (isSpawnNpc(event.getEntity())) {
            debugMobSpawn("creature-spawn", event.getEntity(), location, "allow", "npc marker; reason=" + event.getSpawnReason());
            return;
        }
        if (isAllowedProtectedCreatureSpawn(event)) {
            markAdminPlacedMob(event.getEntity());
            debugMobSpawn("creature-spawn", event.getEntity(), location, "allow", "admin allowed; reason=" + event.getSpawnReason());
            return;
        }
        event.setCancelled(true);
        debugMobSpawn("creature-spawn", event.getEntity(), location, "block", "protected spawn column; reason=" + event.getSpawnReason());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        Location location = event.getLocation();
        if (event instanceof CreatureSpawnEvent) {
            return;
        }
        if (plugin.getDuelManager() != null && plugin.getDuelManager().allowsArenaEntitySpawn(event.getEntity())) {
            return;
        }
        if (!flagEnabled(FLAG_MOB_SPAWNS)) {
            debugMobSpawn("entity-spawn", event.getEntity(), location, "allow", "mob-spawns flag is off");
            return;
        }
        if (!isProtectedColumn(location)) {
            debugMobSpawn("entity-spawn", event.getEntity(), location, "allow", "outside protected spawn column");
            return;
        }
        if (shouldKeepOutOfSpawn(event.getEntity())) {
            event.setCancelled(true);
            debugMobSpawn("entity-spawn", event.getEntity(), location, "block", "protected spawn column");
            return;
        }
        debugMobSpawn("entity-spawn", event.getEntity(), location, "allow", "kept entity type or marker");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCreatureSpawnMonitor(CreatureSpawnEvent event) {
        removeBlockedSpawnMobAfterEvent(event.getEntity(), event.getLocation(), event.isCancelled());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitySpawnMonitor(EntitySpawnEvent event) {
        if (event instanceof CreatureSpawnEvent) {
            return;
        }
        removeBlockedSpawnMobAfterEvent(event.getEntity(), event.getLocation(), event.isCancelled());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityMove(EntityMoveEvent event) {
        if (!flagEnabled(FLAG_MOB_ENTRY) || !event.hasChangedBlock()) {
            return;
        }
        if (!shouldKeepOutOfSpawn(event.getEntity())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (!isProtectedColumn(from) && isProtectedColumn(to)) {
            event.setCancelled(true);
            removeProtectedStray(event.getEntity(), to);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (!flagEnabled(FLAG_MOB_ENTRY) || !shouldKeepOutOfSpawn(event.getEntity())) {
            return;
        }
        Location to = event.getTo();
        if (!isProtectedColumn(event.getFrom()) && to != null && isProtectedColumn(to)) {
            event.setCancelled(true);
            removeProtectedStray(event.getEntity(), to);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        ConfigManager config = plugin.getConfigManager();
        if (!shouldCleanProtectedSpawnMobs(config)
            || !isActiveProtectionWorld(event.getWorld(), config)
            || !chunkIntersectsProtectedArea(event.getChunk(), config)) {
            return;
        }
        cleanupProtectedSpawnMobsInChunk(event.getChunk());
        Bukkit.getScheduler().runTask(plugin, () -> cleanupProtectedSpawnMobsInChunk(event.getChunk()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        ConfigManager config = plugin.getConfigManager();
        if (!shouldCleanProtectedSpawnMobs(config)
            || !isActiveProtectionWorld(event.getWorld(), config)
            || !chunkIntersectsProtectedArea(event.getChunk(), config)) {
            return;
        }
        cleanupProtectedSpawnMobs(event.getEntities());
        Bukkit.getScheduler().runTask(plugin, () -> cleanupProtectedSpawnMobsInChunk(event.getChunk()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        if (!event.toWeatherState() || !shouldCancelWeatherLock(event.getWorld(), event.getCause())) {
            return;
        }
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> enforceWeatherLock(event.getWorld()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onThunderChange(ThunderChangeEvent event) {
        if (!event.toThunderState() || !shouldCancelWeatherLock(event.getWorld(), event.getCause())) {
            return;
        }
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> enforceWeatherLock(event.getWorld()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().isActiveArena(event.getBlock().getLocation())
            && event.getPlayer() != null && plugin.getDuelManager().isDuelParticipant(event.getPlayer())) return;
        if (!isProtectedWithFlag(event.getBlock().getLocation(), FLAG_FIRE)) {
            return;
        }
        Player player = event.getPlayer();
        if (player != null && canEditSpawn(player)) {
            return;
        }

        event.setCancelled(true);
        sendDeny(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().isActiveArena(event.getBlock().getLocation())) return;
        if (isProtectedWithFlag(event.getBlock().getLocation(), FLAG_FIRE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().isActiveArena(event.getBlock().getLocation())) return;
        String flag = isFire(event.getSource().getType()) || isFire(event.getNewState().getType())
            ? FLAG_FIRE
            : FLAG_NATURAL_DECAY;
        if (flag.equals(FLAG_NATURAL_DECAY) && isAllowedBoneMealGrowth(event.getBlock().getLocation())) {
            return;
        }
        if (isProtectedWithFlag(event.getBlock().getLocation(), flag)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        if (isAllowedBoneMealGrowth(event.getBlock().getLocation())) {
            return;
        }
        if (isProtectedWithFlag(event.getBlock().getLocation(), FLAG_NATURAL_DECAY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        if (event instanceof BlockSpreadEvent) {
            return;
        }
        if (plugin.getDuelManager() != null && plugin.getDuelManager().isActiveArena(event.getBlock().getLocation())) return;
        if (isAllowedBoneMealGrowth(event.getBlock().getLocation())) {
            return;
        }
        if (isProtectedWithFlag(event.getBlock().getLocation(), FLAG_NATURAL_DECAY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        if (isProtectedWithFlag(event.getBlock().getLocation(), FLAG_NATURAL_DECAY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (isProtectedWithFlag(event.getBlock().getLocation(), FLAG_NATURAL_DECAY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCauldronChange(CauldronLevelChangeEvent event) {
        if (!isProtectedWithFlag(event.getBlock().getLocation(), FLAG_ENVIRONMENT)) {
            return;
        }
        if (event.getEntity() instanceof Player player && canEditSpawn(player)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFertilize(BlockFertilizeEvent event) {
        if (!affectsProtectedArea(event.getBlock(), event.getBlocks())) {
            return;
        }
        Player player = event.getPlayer();
        if (player != null) {
            if (flagEnabled(FLAG_BONE_MEAL) && !canEditSpawn(player)) {
                event.setCancelled(true);
                sendDeny(player);
                return;
            }
            recordAllowedBoneMealGrowth(event.getBlock().getLocation(), event.getBlocks());
            return;
        }
        if (isAllowedBoneMealGrowth(event.getBlock().getLocation()) || affectsAllowedBoneMealGrowth(event.getBlocks())) {
            return;
        }
        if (flagEnabled(FLAG_BONE_MEAL)) {
            event.setCancelled(true);
            return;
        }
        if (flagEnabled(FLAG_NATURAL_DECAY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        if (!affectsProtectedArea(event.getLocation(), event.getBlocks())) {
            return;
        }
        Player player = event.getPlayer();
        if (player != null) {
            if (flagEnabled(FLAG_BONE_MEAL) && !canEditSpawn(player)) {
                event.setCancelled(true);
                sendDeny(player);
                return;
            }
            recordAllowedBoneMealGrowth(event.getLocation(), event.getBlocks());
            return;
        }
        if (isAllowedBoneMealGrowth(event.getLocation()) || affectsAllowedBoneMealGrowth(event.getBlocks())) {
            return;
        }
        if (flagEnabled(FLAG_NATURAL_DECAY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().allowsArenaFluid(event.getBlock(), event.getToBlock())) return;
        if (!flagEnabled(FLAG_LIQUIDS)) {
            return;
        }
        if (isProtected(event.getBlock().getLocation()) || isProtected(event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpongeAbsorb(SpongeAbsorbEvent event) {
        if (!flagEnabled(FLAG_LIQUIDS)) {
            return;
        }
        if (affectsProtectedArea(event.getBlock(), event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent event) {
        if (!flagEnabled(FLAG_REDSTONE)) {
            return;
        }
        if (isProtected(event.getBlock().getLocation()) || isProtected(dispenseTarget(event.getBlock()).getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (shouldBlockCropTrample(event.getEntity(), event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        if (isProtectedWithFlag(event.getBlock().getLocation(), FLAG_MOB_GRIEF)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(EntityInteractEvent event) {
        if (shouldBlockCropTrample(event.getEntity(), event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        if (isProtectedWithFlag(event.getBlock().getLocation(), FLAG_MOB_GRIEF)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTntPrime(TNTPrimeEvent event) {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().isActiveArena(event.getBlock().getLocation())) return;
        if (!flagEnabled(FLAG_EXPLOSIONS)) {
            return;
        }
        if (isProtected(event.getBlock().getLocation())
            || (event.getPrimingBlock() != null && isProtected(event.getPrimingBlock().getLocation()))
            || (event.getPrimingEntity() != null && isProtected(event.getPrimingEntity().getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().isArenaExplosion(event.getEntity().getLocation())) return;
        if (isProtectedWithFlag(event.getEntity().getLocation(), FLAG_EXPLOSIONS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().isArenaExplosion(event.getBlock().getLocation())) return;
        if (!flagEnabled(FLAG_EXPLOSIONS)) {
            return;
        }
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        event.blockList().removeIf(block -> isProtected(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().isArenaExplosion(event.getLocation())) return;
        if (!flagEnabled(FLAG_EXPLOSIONS)) {
            return;
        }
        if (isProtected(event.getLocation())) {
            event.setCancelled(true);
            return;
        }
        event.blockList().removeIf(block -> isProtected(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (flagEnabled(FLAG_REDSTONE) && pistonTouchesProtectedArea(event.getBlock(), event.getDirection(), event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (flagEnabled(FLAG_REDSTONE) && pistonTouchesProtectedArea(event.getBlock(), event.getDirection(), event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null || !shouldBlockProtectedEntityPlacement(player, event.getEntity())) {
            return;
        }

        event.setCancelled(true);
        sendDeny(player);
        resyncDeniedEntityPlacement(player, event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        if (!isProtectedWithFlag(event.getEntity().getLocation(), FLAG_ENTITY_EDIT)) {
            return;
        }
        Player remover = asPlayer(event.getRemover());
        if (remover != null && canEditSpawn(remover)) {
            return;
        }

        event.setCancelled(true);
        sendDeny(remover);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (event instanceof HangingBreakByEntityEvent) {
            return;
        }
        if (isProtectedWithFlag(event.getEntity().getLocation(), FLAG_ENTITY_EDIT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null && event.getEntity() instanceof Boat
            && plugin.getFisherManager() != null
            && plugin.getFisherManager().canLaunchAtFisherBeach(player, heldItem(player, event.getHand()), event.getEntity().getLocation())) {
            return;
        }
        if (plugin.getDuelManager() != null && plugin.getDuelManager().allowsArenaEntityPlacement(player, event.getEntity())) return;
        if (player == null || !shouldBlockProtectedEntityPlacement(player, event.getEntity())) {
            return;
        }

        event.setCancelled(true);
        sendDeny(player);
        resyncDeniedEntityPlacement(player, event.getEntity());
    }

    private ItemStack heldItem(Player player, EquipmentSlot hand) {
        if (player == null || hand == null) {
            return null;
        }
        return hand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().shouldBypassSpawnDamage(event)) return;
        if (event.getEntity() instanceof Player protectedPlayer && shouldProtectSpawnPlayerDamage(protectedPlayer)) {
            event.setCancelled(true);
            clearSpawnDamageHazards(protectedPlayer, event.getCause());
            return;
        }

        if (flagEnabled(FLAG_EXPLOSIONS)
            && event.getEntity() instanceof Player player
            && isExplosionDamage(event)
            && isProtected(player.getLocation())) {
            event.setCancelled(true);
            return;
        }

        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return;
        }

        if (byEntity.getEntity() instanceof Player victim) {
            Player attacker = attackingPlayer(byEntity.getDamager());
            if (attacker != null && shouldBlockPvp(attacker, victim)) {
                byEntity.setCancelled(true);
                sendDeny(attacker);
            }
            return;
        }

        Entity entity = byEntity.getEntity();
        if (!(entity instanceof ArmorStand || entity instanceof Hanging)) {
            return;
        }
        if (!isProtectedWithFlag(entity.getLocation(), FLAG_ENTITY_EDIT)) {
            return;
        }
        Player attacker = attackingPlayer(byEntity.getDamager());
        if (attacker != null && canEditSpawn(attacker)) {
            return;
        }

        byEntity.setCancelled(true);
        sendDeny(attacker);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombustByEntity(EntityCombustByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attackingPlayer(event.getCombuster());
        if (attacker != null && plugin.getDuelManager() != null
            && plugin.getDuelManager().areOpponents(attacker.getUniqueId(), victim.getUniqueId())) return;
        if (attacker != null && shouldBlockPvp(attacker, victim)) {
            event.setCancelled(true);
            sendDeny(attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!flagEnabled(FLAG_PVP)) {
            return;
        }
        Player shooter = projectileShooter(event.getPotion());
        if (shooter == null || canBypassSpawnProtection(shooter)) {
            return;
        }
        boolean potionInSpawn = isProtected(event.getPotion().getLocation());
        for (var affected : event.getAffectedEntities()) {
            if (affected instanceof Player victim && (potionInSpawn || isProtected(victim.getLocation()))) {
                if (plugin.getDuelManager() != null
                    && plugin.getDuelManager().areOpponents(shooter.getUniqueId(), victim.getUniqueId())) continue;
                event.setIntensity(victim, 0.0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLingeringPotionSplash(LingeringPotionSplashEvent event) {
        if (!flagEnabled(FLAG_PVP)) {
            return;
        }
        Player shooter = projectileShooter(event.getEntity());
        if (shooter == null || canBypassSpawnProtection(shooter)
            || plugin.getDuelManager() != null && plugin.getDuelManager().isDuelParticipant(shooter)) {
            return;
        }
        if (isProtected(event.getEntity().getLocation()) || isProtected(event.getAreaEffectCloud().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (isSpawnNpc(event.getRightClicked())) {
            return;
        }
        if (shouldBlock(event.getPlayer(), event.getRightClicked().getLocation(), FLAG_ENTITY_EDIT)) {
            event.setCancelled(true);
            sendDeny(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemFrameChange(PlayerItemFrameChangeEvent event) {
        if (shouldBlock(event.getPlayer(), event.getItemFrame().getLocation(), FLAG_ENTITY_EDIT)) {
            event.setCancelled(true);
            sendDeny(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (shouldCancelSpawnEggUse(event.getPlayer(), event.getRightClicked().getLocation())) {
            event.setCancelled(true);
            sendDeny(event.getPlayer());
            return;
        }
        blockProtectedEntityUse(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (shouldCancelSpawnEggUse(event.getPlayer(), event.getRightClicked().getLocation())) {
            event.setCancelled(true);
            sendDeny(event.getPlayer());
            return;
        }
        blockProtectedEntityUse(event);
    }

    private void blockProtectedEntityUse(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof ArmorStand || entity instanceof Hanging)) {
            return;
        }
        if (isSpawnNpc(entity)) {
            return;
        }
        if (shouldBlock(event.getPlayer(), entity.getLocation(), FLAG_ENTITY_EDIT)) {
            event.setCancelled(true);
            sendDeny(event.getPlayer());
        }
    }

    private boolean shouldDenySpawnUtilityBlockUse(Player player, Block block) {
        if (block == null || !isProtected(block.getLocation()) || canEditSpawn(player)) {
            return false;
        }
        if (isAlwaysAllowedSpawnUtilityBlock(block)) {
            return false;
        }
        if (isPluginCustomUtilityBlock(block)) {
            return !isPublicInteractionBlock(block.getLocation());
        }
        return isRestrictedSpawnUtilityBlock(block.getType());
    }

    private boolean isAlwaysAllowedSpawnUtilityBlock(Block block) {
        return block != null && (block.getType() == Material.CRAFTING_TABLE || isPublicSpawnStation(block));
    }

    private boolean isPublicSpawnStation(Block block) {
        if (block == null) {
            return false;
        }
        if (plugin.getCorruptionManager() != null && plugin.getCorruptionManager().isStationBlock(block)) {
            return true;
        }
        if (plugin.getAwakeningTableListener() != null && plugin.getAwakeningTableListener().isAwakeningTableBlock(block)) {
            return true;
        }
        return plugin.getVeilOrbManager() != null && plugin.getVeilOrbManager().isStationBlock(block);
    }

    private boolean isPluginCustomUtilityBlock(Block block) {
        if (block == null) {
            return false;
        }
        if (plugin.getXpLecternListener() != null && plugin.getXpLecternListener().isLecternBlock(block)) {
            return true;
        }
        if (plugin.getCorruptionManager() != null && plugin.getCorruptionManager().isStationBlock(block)) {
            return true;
        }
        if (plugin.getAwakeningTableListener() != null && plugin.getAwakeningTableListener().isAwakeningTableBlock(block)) {
            return true;
        }
        if (plugin.getVeilOrbManager() != null && plugin.getVeilOrbManager().isStationBlock(block)) {
            return true;
        }
        if (plugin.getSalvagingDepotListener() != null && plugin.getSalvagingDepotListener().isDepotBlock(block)) {
            return true;
        }
        return plugin.getAgriculturalPylonListener() != null && plugin.getAgriculturalPylonListener().isPylonBlock(block);
    }

    private boolean isRestrictedSpawnUtilityBlock(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        if (name.endsWith("SHULKER_BOX")
            || name.endsWith("_BED")
            || name.endsWith("_CAULDRON")
            || name.endsWith("_BUTTON")
            || name.endsWith("_DOOR")
            || name.endsWith("_TRAPDOOR")
            || name.endsWith("_FENCE_GATE")
            || name.endsWith("_COPPER_BULB")) {
            return true;
        }
        return switch (name) {
            case "ENCHANTING_TABLE",
                "ANVIL",
                "CHIPPED_ANVIL",
                "DAMAGED_ANVIL",
                "GRINDSTONE",
                "SMITHING_TABLE",
                "CARTOGRAPHY_TABLE",
                "FLETCHING_TABLE",
                "LOOM",
                "STONECUTTER",
                "BREWING_STAND",
                "FURNACE",
                "BLAST_FURNACE",
                "SMOKER",
                "BEACON",
                "ENDER_CHEST",
                "CHEST",
                "TRAPPED_CHEST",
                "BARREL",
                "DISPENSER",
                "DROPPER",
                "HOPPER",
                "CHISELED_BOOKSHELF",
                "JUKEBOX",
                "RESPAWN_ANCHOR",
                "LECTERN",
                "COMPOSTER",
                "CRAFTER",
                "DECORATED_POT",
                "VAULT",
                "OMINOUS_VAULT",
                "CAMPFIRE",
                "SOUL_CAMPFIRE" -> true;
            default -> false;
        };
    }

    private boolean shouldBlock(Player player, Block block, String flag) {
        return block != null && shouldBlock(player, block.getLocation(), flag);
    }

    private boolean shouldBlock(Player player, Location location, String flag) {
        return isProtectedWithFlag(location, flag) && !canEditSpawn(player);
    }

    private void enforceProtectedBlockEvent(Cancellable event, Player player, Block block, String flag, boolean notify) {
        if (event == null || block == null || !shouldBlock(player, block, flag)) {
            return;
        }
        boolean wasCancelled = event.isCancelled();
        event.setCancelled(true);
        if (notify && !wasCancelled) {
            sendDeny(player);
        }
    }

    private boolean shouldBlockProtectedEntityPlacement(Player player, Entity entity) {
        if (player == null || entity == null || canEditSpawn(player)) {
            return false;
        }
        Location location = entity.getLocation();
        if (entity instanceof ArmorStand || entity instanceof Hanging) {
            return isProtectedWithFlag(location, FLAG_BUILD) || isProtectedWithFlag(location, FLAG_ENTITY_EDIT);
        }
        return isProtectedWithFlag(location, FLAG_BUILD);
    }

    private void resyncDeniedEntityPlacement(Player player, Entity entity) {
        if (player == null) {
            return;
        }
        UUID entityId = entity == null ? null : entity.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.updateInventory();
            if (entityId == null) {
                return;
            }
            Entity liveEntity = Bukkit.getEntity(entityId);
            if (liveEntity != null && (liveEntity instanceof ArmorStand || liveEntity instanceof Hanging)
                && isProtected(liveEntity.getLocation()) && !canEditSpawn(player)) {
                liveEntity.remove();
            }
        });
    }

    private boolean shouldCancelSpawnEggUse(Player player, Location location) {
        if (player == null || !flagEnabled(FLAG_MOB_SPAWNS) || !isHoldingSpawnEgg(player) || !isProtectedColumn(location)) {
            return false;
        }
        if (!canEditSpawn(player)) {
            return true;
        }

        recordAdminSpawnEggUse(player, location);
        return false;
    }

    private boolean shouldBlockCropTrample(Entity entity, Block block) {
        if (block == null || !isTrampleSensitiveMaterial(block.getType())) {
            return false;
        }
        if (block.getType() == Material.TURTLE_EGG) {
            return isProtected(block.getLocation());
        }
        if (!isProtectedWithFlag(block.getLocation(), FLAG_CROP_TRAMPLE)) return false;
        return !(entity instanceof Player player && canEditSpawn(player));
    }

    private boolean isBoneMealUse(PlayerInteractEvent event) {
        return event.getItem() != null && event.getItem().getType() == Material.BONE_MEAL;
    }

    private boolean interactionWasDenied(PlayerInteractEvent event) {
        return event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY
            || event.useItemInHand() == org.bukkit.event.Event.Result.DENY;
    }

    private boolean applyProtectedBoneMeal(PlayerInteractEvent event, Block block) {
        if (!isBoneMealTarget(block)) {
            return false;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.BONE_MEAL) {
            return false;
        }

        Player player = event.getPlayer();
        recordAllowedBoneMealGrowthArea(block.getLocation(), 6, 8);
        boolean applied = block.applyBoneMeal(event.getBlockFace());
        if (!applied) {
            return true;
        }

        event.setCancelled(true);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);

        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        if (player.getGameMode() != GameMode.CREATIVE) {
            consumeBoneMeal(player, hand, item);
        }
        player.swingHand(hand);
        player.getWorld().playSound(block.getLocation(), Sound.ITEM_BONE_MEAL_USE, 1.0f, 1.0f);
        player.getWorld().spawnParticle(
            Particle.HAPPY_VILLAGER,
            block.getLocation().add(0.5, 0.5, 0.5),
            12,
            0.25,
            0.35,
            0.25,
            0.0
        );
        return true;
    }

    private void consumeBoneMeal(Player player, EquipmentSlot hand, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            return;
        }
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(null);
            return;
        }
        player.getInventory().setItemInMainHand(null);
    }

    private boolean isBoneMealTarget(Block block) {
        if (block == null) {
            return false;
        }
        if (block.getBlockData() instanceof org.bukkit.block.data.Ageable) {
            return true;
        }
        String name = block.getType().name();
        return name.endsWith("_SAPLING")
            || name.endsWith("_FUNGUS")
            || name.endsWith("_MUSHROOM")
            || name.endsWith("_NYLIUM")
            || name.endsWith("_ROOTS")
            || name.endsWith("_VINES")
            || name.endsWith("_VINES_PLANT")
            || name.equals("BAMBOO")
            || name.equals("CACTUS")
            || name.equals("CAVE_VINES")
            || name.equals("CAVE_VINES_PLANT")
            || name.equals("GRASS_BLOCK")
            || name.equals("MOSS_BLOCK")
            || name.equals("MOSS_CARPET")
            || name.equals("PALE_MOSS_BLOCK")
            || name.equals("PALE_MOSS_CARPET")
            || name.equals("SHORT_GRASS")
            || name.equals("TALL_GRASS")
            || name.equals("FERN")
            || name.equals("LARGE_FERN")
            || name.equals("SUGAR_CANE")
            || name.equals("SEA_PICKLE")
            || name.equals("SEAGRASS")
            || name.equals("TALL_SEAGRASS")
            || name.equals("KELP")
            || name.equals("KELP_PLANT")
            || name.equals("HANGING_ROOTS")
            || name.equals("PINK_PETALS")
            || name.equals("PITCHER_PLANT")
            || name.equals("SPORE_BLOSSOM")
            || name.equals("BIG_DRIPLEAF")
            || name.equals("SMALL_DRIPLEAF");
    }

    private boolean shouldBlockPvp(Player attacker, Player victim) {
        return flagEnabled(FLAG_PVP)
            && !canBypassSpawnProtection(attacker)
            && (isProtected(attacker.getLocation()) || isProtected(victim.getLocation()));
    }

    private boolean shouldProtectSpawnPlayerDamage(Player player) {
        return blocksProtectedSpawnDeath(player);
    }

    private boolean shouldBlockHungerDrain(Player player) {
        return player != null
            && (plugin.getDuelManager() == null || !plugin.getDuelManager().isDuelParticipant(player))
            && !canBypassSpawnProtection(player)
            && isProtectedWithFlag(player.getLocation(), FLAG_HUNGER_DRAIN);
    }

    private void clearSpawnDamageHazards(Player player, EntityDamageEvent.DamageCause cause) {
        player.setFallDistance(0.0f);
        switch (cause) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR, CAMPFIRE -> player.setFireTicks(0);
            case POISON -> player.removePotionEffect(PotionEffectType.POISON);
            case WITHER -> player.removePotionEffect(PotionEffectType.WITHER);
            case DROWNING -> player.setRemainingAir(player.getMaximumAir());
            case FREEZE -> player.setFreezeTicks(0);
            case VOID -> rescueProtectedSpawnPlayer(player);
            default -> {
            }
        }
    }

    private void rescueProtectedSpawnPlayer(Player player) {
        Location safe = plugin.getExactSpawnListener() == null
            ? player.getWorld().getSpawnLocation()
            : plugin.getExactSpawnListener().exactSpawnLocation();
        if (safe == null || safe.getWorld() == null) {
            safe = player.getWorld().getSpawnLocation();
        }
        player.teleport(safe);
        player.setFallDistance(0.0f);
    }

    private boolean isProtectedWithFlag(Location location, String flag) {
        return flagEnabled(flag) && isProtected(location);
    }

    private boolean flagEnabled(String flag) {
        return plugin.getConfigManager().isSpawnProtectionFlagEnabled(flag);
    }

    private boolean canBypassSpawnProtection(Player player) {
        return player != null && (player.isOp() || player.hasPermission("smpcore.spawnprotect.bypass"));
    }

    private boolean shouldKeepOutOfSpawn(Entity entity) {
        return entity instanceof LivingEntity
            && !(entity instanceof Player)
            && !(entity instanceof ArmorStand)
            && !isSpawnNpc(entity)
            && !isAdminPlacedMob(entity);
    }

    public int cleanupProtectedSpawnMobs() {
        ConfigManager config = plugin.getConfigManager();
        if (!shouldCleanProtectedSpawnMobs(config)) {
            return 0;
        }
        World world = activeProtectionWorld(config);
        if (world == null) {
            return 0;
        }

        int removed = 0;
        for (Chunk chunk : world.getLoadedChunks()) {
            if (!chunkIntersectsProtectedArea(chunk, config)) {
                continue;
            }
            removed += cleanupProtectedSpawnMobsInChunk(chunk);
        }
        return removed;
    }

    public void scheduleProtectedSpawnMobCleanup() {
        scheduleProtectedSpawnMobCleanup(activeProtectionWorld(plugin.getConfigManager()));
    }

    private void scheduleProtectedSpawnMobCleanup(World world) {
        if (!shouldCleanProtectedSpawnMobs(plugin.getConfigManager())
            || !isActiveProtectionWorld(world, plugin.getConfigManager())) {
            return;
        }
        for (long delay : MOB_CLEANUP_RECHECK_DELAYS_TICKS) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> cleanupProtectedSpawnMobs(), delay);
        }
    }

    private int cleanupProtectedSpawnMobsInChunk(Chunk chunk) {
        int removed = cleanupProtectedSpawnMobs(chunk.getEntities());
        debugMobCleanup("chunk-cleanup", chunk, removed);
        return removed;
    }

    private int cleanupProtectedSpawnMobs(Entity[] entities) {
        int removed = 0;
        for (Entity entity : entities) {
            if (removeProtectedStray(entity, entity.getLocation())) {
                removed++;
            }
        }
        return removed;
    }

    private int cleanupProtectedSpawnMobs(Iterable<? extends Entity> entities) {
        int removed = 0;
        for (Entity entity : entities) {
            if (removeProtectedStray(entity, entity.getLocation())) {
                removed++;
            }
        }
        return removed;
    }

    private void removeBlockedSpawnMobAfterEvent(Entity entity, Location location, boolean cancelled) {
        if (cancelled || !flagEnabled(FLAG_MOB_SPAWNS) || !isProtectedColumn(location) || !shouldKeepOutOfSpawn(entity)) {
            return;
        }
        debugMobSpawn("spawn-monitor", entity, location, "remove-next-tick", "spawn event reached monitor uncancelled");
        Bukkit.getScheduler().runTask(plugin, () -> removeProtectedStray(entity, entity.getLocation()));
    }

    private boolean removeProtectedStray(Entity entity, Location protectedLocation) {
        if (shouldKeepOutOfSpawn(entity) && isProtectedColumn(protectedLocation)) {
            entity.remove();
            return true;
        }
        return false;
    }

    private boolean shouldCleanProtectedSpawnMobs(ConfigManager config) {
        return config.spawnProtectionEnabled && (flagEnabled(FLAG_MOB_SPAWNS) || flagEnabled(FLAG_MOB_ENTRY));
    }

    public void enforceWeatherLock() {
        ConfigManager config = plugin.getConfigManager();
        World world = activeProtectionWorld(config);
        if (world != null) {
            enforceWeatherLock(world);
        }
    }

    private void enforceWeatherLock(World world) {
        if (!shouldLockWeather(world)) {
            return;
        }
        runWithWeatherLockBypass(world, () -> {
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(CLEAR_WEATHER_DURATION_TICKS);
            world.setThunderDuration(CLEAR_WEATHER_DURATION_TICKS);
            world.setClearWeatherDuration(CLEAR_WEATHER_DURATION_TICKS);
        });
    }

    private boolean shouldCancelWeatherLock(World world, WeatherChangeEvent.Cause cause) {
        return cause != WeatherChangeEvent.Cause.COMMAND && shouldLockWeather(world) && !isWeatherLockBypassed(world);
    }

    private boolean shouldCancelWeatherLock(World world, ThunderChangeEvent.Cause cause) {
        return cause != ThunderChangeEvent.Cause.COMMAND && shouldLockWeather(world) && !isWeatherLockBypassed(world);
    }

    private boolean shouldLockWeather(World world) {
        return world != null
            && plugin.getConfigManager().spawnProtectionEnabled
            && flagEnabled(FLAG_WEATHER_LOCK)
            && isActiveProtectionWorld(world, plugin.getConfigManager());
    }

    private boolean isWeatherLockBypassed(World world) {
        if (world == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        weatherLockBypassWorlds.entrySet().removeIf(entry -> entry.getValue() < now);
        Long expiresAt = weatherLockBypassWorlds.get(world.getUID());
        return expiresAt != null && expiresAt >= now;
    }

    private boolean isSpawnNpc(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (entity.hasMetadata("NPC")
            || entity.hasMetadata("npc")
            || entity.hasMetadata("smpcore_npc")
            || entity.hasMetadata("smpcore_spawn_npc")) {
            return true;
        }
        if (entity.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE)
            || entity.getPersistentDataContainer().has(spawnNpcKey, PersistentDataType.BYTE)
            || entity.getPersistentDataContainer().has(reforgeNpcKey, PersistentDataType.BYTE)) {
            return true;
        }
        for (String rawTag : entity.getScoreboardTags()) {
            String tag = rawTag.toLowerCase(Locale.ROOT);
            if (tag.equals("npc")
                || tag.equals("smpcore_npc")
                || tag.equals("smpcore_spawn_npc")
                || tag.equals("smpcore_reforge_npc")
                || tag.equals("citizens_npc")
                || tag.equals("citizensnpc")) {
                return true;
            }
        }
        return false;
    }

    private boolean isAdminPlacedMob(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (entity.getPersistentDataContainer().has(adminPlacedMobKey, PersistentDataType.BYTE)) {
            return true;
        }
        for (String rawTag : entity.getScoreboardTags()) {
            if (rawTag.equalsIgnoreCase(ADMIN_PLACED_MOB_TAG)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllowedProtectedCreatureSpawn(CreatureSpawnEvent event) {
        return switch (event.getSpawnReason()) {
            case COMMAND -> true;
            case SPAWNER_EGG -> consumeAdminSpawnEggAllowance(event.getLocation());
            default -> false;
        };
    }

    private void debugMobCleanup(String source, Chunk chunk, int removed) {
        if (removed <= 0 || !plugin.getConfigManager().spawnProtectionDebugMobSpawns) {
            return;
        }
        plugin.getLogger().info("[spawnprotect] mob-debug event=" + source
            + " result=remove"
            + " removed=" + removed
            + " chunk=" + chunk.getWorld().getName() + ":" + chunk.getX() + "," + chunk.getZ());
    }

    private void debugMobSpawn(String source, Entity entity, Location location, String result, String reason) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.spawnProtectionDebugMobSpawns || !shouldDebugMobLocation(location, config)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextMobDebugLogAt) {
            skippedMobDebugLogs++;
            return;
        }
        int skipped = skippedMobDebugLogs;
        skippedMobDebugLogs = 0;
        nextMobDebugLogAt = now + MOB_DEBUG_LOG_THROTTLE_MS;

        plugin.getLogger().info("[spawnprotect] mob-debug"
            + " event=" + source
            + " result=" + result
            + " entity=" + (entity == null ? "unknown" : entity.getType().name())
            + " at=" + formatLocation(location)
            + " reason=\"" + reason + "\""
            + " enabled=" + config.spawnProtectionEnabled
            + " mob-spawns=" + flagEnabled(FLAG_MOB_SPAWNS)
            + " protected-column=" + isProtectedColumn(location)
            + " protected-y=" + isProtected(location)
            + " npc=" + isSpawnNpc(entity)
            + " admin-placed=" + isAdminPlacedMob(entity)
            + " region=" + debugRegionSummary(config)
            + (skipped > 0 ? " skipped=" + skipped : ""));
    }

    private boolean shouldDebugMobLocation(Location location, ConfigManager config) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (config.spawnProtectionRegionSet) {
            if (!isConfiguredWorld(location.getWorld(), config.spawnProtectionRegionWorld)) {
                return false;
            }
            int x = location.getBlockX();
            int z = location.getBlockZ();
            return x >= config.spawnProtectionMinX - MOB_DEBUG_MARGIN_BLOCKS
                && x <= config.spawnProtectionMaxX + MOB_DEBUG_MARGIN_BLOCKS
                && z >= config.spawnProtectionMinZ - MOB_DEBUG_MARGIN_BLOCKS
                && z <= config.spawnProtectionMaxZ + MOB_DEBUG_MARGIN_BLOCKS;
        }
        if (!isConfiguredWorld(location.getWorld(), config.spawnWorld)) {
            return false;
        }
        Location spawn = location.getWorld().getSpawnLocation();
        double radius = config.spawnProtectionRadius + MOB_DEBUG_MARGIN_BLOCKS;
        double dx = location.getX() - spawn.getX();
        double dz = location.getZ() - spawn.getZ();
        return (dx * dx) + (dz * dz) <= radius * radius;
    }

    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "unknown";
        }
        return location.getWorld().getName()
            + " "
            + location.getBlockX()
            + ","
            + location.getBlockY()
            + ","
            + location.getBlockZ();
    }

    private String debugRegionSummary(ConfigManager config) {
        if (config.spawnProtectionRegionSet) {
            return config.spawnProtectionRegionWorld
                + "[x=" + config.spawnProtectionMinX + ".." + config.spawnProtectionMaxX
                + ",z=" + config.spawnProtectionMinZ + ".." + config.spawnProtectionMaxZ
                + ",y=" + (config.spawnProtectionRegionFullHeight ? "all" : config.spawnProtectionMinY + ".." + config.spawnProtectionMaxY)
                + "]";
        }
        return config.spawnWorld + "[radius=" + config.spawnProtectionRadius + "]";
    }

    private void recordAdminSpawnEggUse(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) {
            return;
        }
        adminSpawnEggAllowances.put(
            player.getUniqueId(),
            new AdminSpawnEggAllowance(location, System.currentTimeMillis() + ADMIN_SPAWN_EGG_WINDOW_MS)
        );
    }

    private boolean consumeAdminSpawnEggAllowance(Location spawnLocation) {
        if (spawnLocation == null || spawnLocation.getWorld() == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        adminSpawnEggAllowances.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis < now);
        for (Map.Entry<UUID, AdminSpawnEggAllowance> entry : adminSpawnEggAllowances.entrySet()) {
            AdminSpawnEggAllowance allowance = entry.getValue();
            if (allowance.matches(spawnLocation)) {
                adminSpawnEggAllowances.remove(entry.getKey());
                return true;
            }
        }
        return false;
    }

    private boolean canDrawProtectedAreaOutline(Player player) {
        if (player == null || !player.isOnline() || !plugin.getConfigManager().spawnProtectionEnabled) {
            return false;
        }
        ConfigManager config = plugin.getConfigManager();
        if (config.spawnProtectionRegionSet) {
            return isConfiguredWorld(player.getWorld(), config.spawnProtectionRegionWorld);
        }
        return isConfiguredWorld(player.getWorld(), config.spawnWorld);
    }

    private void drawProtectedAreaOutline(Player player) {
        if (!canDrawProtectedAreaOutline(player)) {
            return;
        }
        ConfigManager config = plugin.getConfigManager();
        if (config.spawnProtectionRegionSet) {
            drawRegionOutline(player, config);
        } else {
            drawRadiusOutline(player, config);
        }
    }

    private void drawRegionOutline(Player player, ConfigManager config) {
        double y = player.getLocation().getY() + 0.15;
        int width = Math.max(1, config.spawnProtectionMaxX - config.spawnProtectionMinX + 1);
        int depth = Math.max(1, config.spawnProtectionMaxZ - config.spawnProtectionMinZ + 1);
        int perimeter = (width + depth) * 2;
        int step = Math.max(1, (int) Math.ceil(perimeter / 240.0));
        for (int x = config.spawnProtectionMinX; x <= config.spawnProtectionMaxX; x += step) {
            spawnOutlineParticle(player, x + 0.5, y, config.spawnProtectionMinZ + 0.5);
            spawnOutlineParticle(player, x + 0.5, y, config.spawnProtectionMaxZ + 0.5);
        }
        for (int z = config.spawnProtectionMinZ; z <= config.spawnProtectionMaxZ; z += step) {
            spawnOutlineParticle(player, config.spawnProtectionMinX + 0.5, y, z + 0.5);
            spawnOutlineParticle(player, config.spawnProtectionMaxX + 0.5, y, z + 0.5);
        }
        spawnOutlineParticle(player, config.spawnProtectionMaxX + 0.5, y, config.spawnProtectionMinZ + 0.5);
        spawnOutlineParticle(player, config.spawnProtectionMaxX + 0.5, y, config.spawnProtectionMaxZ + 0.5);
        spawnOutlineParticle(player, config.spawnProtectionMinX + 0.5, y, config.spawnProtectionMaxZ + 0.5);
        spawnOutlineParticle(player, config.spawnProtectionMinX + 0.5, y, config.spawnProtectionMinZ + 0.5);
    }

    private void drawRadiusOutline(Player player, ConfigManager config) {
        World world = player.getWorld();
        Location spawn = world.getSpawnLocation();
        double y = player.getLocation().getY() + 0.15;
        double radius = config.spawnProtectionRadius;
        int points = radius > 250.0 ? 240 : 160;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i) / points;
            double x = spawn.getX() + Math.cos(angle) * radius;
            double z = spawn.getZ() + Math.sin(angle) * radius;
            spawnOutlineParticle(player, x, y, z);
        }
    }

    private void spawnOutlineParticle(Player player, double x, double y, double z) {
        player.spawnParticle(Particle.HAPPY_VILLAGER, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private World activeProtectionWorld(ConfigManager config) {
        String worldName = config.spawnProtectionRegionSet
            ? config.spawnProtectionRegionWorld
            : config.spawnWorld;
        if (worldName == null || worldName.isBlank()) {
            return null;
        }

        World configured = Bukkit.getWorld(worldName);
        if (configured != null) {
            return configured;
        }
        for (World world : Bukkit.getWorlds()) {
            if (world.getName().equalsIgnoreCase(worldName)) {
                return world;
            }
        }
        return null;
    }

    private boolean isActiveProtectionWorld(World world, ConfigManager config) {
        if (world == null) {
            return false;
        }
        String worldName = config.spawnProtectionRegionSet
            ? config.spawnProtectionRegionWorld
            : config.spawnWorld;
        return worldName != null && !worldName.isBlank() && isConfiguredWorld(world, worldName);
    }

    private boolean chunkIntersectsProtectedArea(Chunk chunk, ConfigManager config) {
        int chunkMinX = chunk.getX() << 4;
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = chunk.getZ() << 4;
        int chunkMaxZ = chunkMinZ + 15;

        if (config.spawnProtectionRegionSet) {
            return rangesOverlap(chunkMinX, chunkMaxX, config.spawnProtectionMinX, config.spawnProtectionMaxX)
                && rangesOverlap(chunkMinZ, chunkMaxZ, config.spawnProtectionMinZ, config.spawnProtectionMaxZ);
        }

        Location spawn = chunk.getWorld().getSpawnLocation();
        double closestX = clamp(spawn.getX(), chunkMinX, chunkMaxX + 1.0);
        double closestZ = clamp(spawn.getZ(), chunkMinZ, chunkMaxZ + 1.0);
        double dx = spawn.getX() - closestX;
        double dz = spawn.getZ() - closestZ;
        double radius = config.spawnProtectionRadius;
        return (dx * dx) + (dz * dz) <= radius * radius;
    }

    private boolean rangesOverlap(int firstMin, int firstMax, int secondMin, int secondMax) {
        return firstMin <= secondMax && secondMin <= firstMax;
    }

    private boolean intersectsProtectedArea(Location center, double radiusX, double radiusY, double radiusZ) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.spawnProtectionEnabled || center == null || center.getWorld() == null) {
            return false;
        }

        double minX = center.getX() - radiusX;
        double maxX = center.getX() + radiusX;
        double minY = center.getY() - radiusY;
        double maxY = center.getY() + radiusY;
        double minZ = center.getZ() - radiusZ;
        double maxZ = center.getZ() + radiusZ;

        if (config.spawnProtectionRegionSet) {
            if (!isConfiguredWorld(center.getWorld(), config.spawnProtectionRegionWorld)) {
                return false;
            }
            boolean overlapsY = config.spawnProtectionRegionFullHeight
                || (minY <= config.spawnProtectionMaxY && maxY >= config.spawnProtectionMinY);
            return overlapsY
                && minX <= config.spawnProtectionMaxX
                && maxX >= config.spawnProtectionMinX
                && minZ <= config.spawnProtectionMaxZ
                && maxZ >= config.spawnProtectionMinZ;
        }

        if (!isConfiguredWorld(center.getWorld(), config.spawnWorld)) {
            return false;
        }

        Location spawn = center.getWorld().getSpawnLocation();
        double closestX = clamp(spawn.getX(), minX, maxX);
        double closestZ = clamp(spawn.getZ(), minZ, maxZ);
        double dx = spawn.getX() - closestX;
        double dz = spawn.getZ() - closestZ;
        double radius = config.spawnProtectionRadius;
        return (dx * dx) + (dz * dz) <= radius * radius;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean isConfiguredWorld(World world, String configuredWorldName) {
        World configured = Bukkit.getWorld(configuredWorldName);
        if (configured != null) {
            return configured.getUID().equals(world.getUID());
        }
        return world.getName().equalsIgnoreCase(configuredWorldName);
    }

    private boolean affectsProtectedArea(Block origin, Iterable<BlockState> changedBlocks) {
        return (origin != null && isProtected(origin.getLocation())) || affectsProtectedArea(changedBlocks);
    }

    private boolean affectsProtectedArea(Location origin, Iterable<BlockState> changedBlocks) {
        return isProtected(origin) || affectsProtectedArea(changedBlocks);
    }

    private boolean affectsProtectedArea(Iterable<BlockState> changedBlocks) {
        for (BlockState state : changedBlocks) {
            if (isProtected(state.getLocation())) {
                return true;
            }
        }
        return false;
    }

    private void recordAllowedBoneMealGrowth(Location origin, Iterable<BlockState> changedBlocks) {
        long expiresAt = System.currentTimeMillis() + BONE_MEAL_GROWTH_ALLOWANCE_MS;
        recordAllowedBoneMealGrowth(origin, expiresAt);
        for (BlockState state : changedBlocks) {
            recordAllowedBoneMealGrowth(state.getLocation(), expiresAt);
        }
    }

    private void recordAllowedBoneMealGrowthArea(Location center, int horizontalRadius, int verticalRadius) {
        if (center == null || center.getWorld() == null) {
            return;
        }

        World world = center.getWorld();
        int minY = Math.max(world.getMinHeight(), center.getBlockY() - verticalRadius);
        int maxY = Math.min(world.getMaxHeight() - 1, center.getBlockY() + verticalRadius);
        long expiresAt = System.currentTimeMillis() + BONE_MEAL_GROWTH_ALLOWANCE_MS;
        allowedBoneMealGrowthAreas.add(new BoneMealGrowthArea(
            world.getUID(),
            center.getBlockX() - horizontalRadius,
            center.getBlockX() + horizontalRadius,
            minY,
            maxY,
            center.getBlockZ() - horizontalRadius,
            center.getBlockZ() + horizontalRadius,
            expiresAt
        ));
    }

    private void recordAllowedBoneMealGrowth(Location location, long expiresAt) {
        String key = blockKey(location);
        if (key != null) {
            allowedBoneMealGrowthBlocks.put(key, expiresAt);
        }
    }

    private boolean isAllowedBoneMealGrowth(Location location) {
        String key = blockKey(location);
        if (key == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        allowedBoneMealGrowthBlocks.entrySet().removeIf(entry -> entry.getValue() < now);
        Long expiresAt = allowedBoneMealGrowthBlocks.get(key);
        if (expiresAt != null && expiresAt >= now) {
            return true;
        }
        allowedBoneMealGrowthAreas.removeIf(area -> area.expiresAtMillis() < now);
        for (BoneMealGrowthArea area : allowedBoneMealGrowthAreas) {
            if (area.contains(location, now)) {
                return true;
            }
        }
        return false;
    }

    private boolean affectsAllowedBoneMealGrowth(Iterable<BlockState> changedBlocks) {
        for (BlockState state : changedBlocks) {
            if (isAllowedBoneMealGrowth(state.getLocation())) {
                return true;
            }
        }
        return false;
    }

    private String blockKey(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return location.getWorld().getUID()
            + ":"
            + location.getBlockX()
            + ":"
            + location.getBlockY()
            + ":"
            + location.getBlockZ();
    }

    private boolean pistonTouchesProtectedArea(Block piston, BlockFace direction, Iterable<Block> movedBlocks) {
        if (isProtected(piston.getLocation())) {
            return true;
        }
        for (Block block : movedBlocks) {
            if (isProtected(block.getLocation())) {
                return true;
            }
            if (isProtected(block.getRelative(direction).getLocation())) {
                return true;
            }
            if (isProtected(block.getRelative(direction.getOppositeFace()).getLocation())) {
                return true;
            }
        }
        return false;
    }

    private Block dispenseTarget(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Directional directional) {
            return block.getRelative(directional.getFacing());
        }
        return block;
    }

    private boolean isExplosionDamage(EntityDamageEvent event) {
        return event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
            || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;
    }

    private boolean isFire(Material material) {
        return material == Material.FIRE || material == Material.SOUL_FIRE;
    }

    static boolean isTrampleSensitiveMaterial(Material material) {
        return material == Material.FARMLAND || material == Material.TURTLE_EGG;
    }

    private boolean isHoldingSpawnEgg(Player player) {
        return isSpawnEgg(player.getInventory().getItemInMainHand())
            || isSpawnEgg(player.getInventory().getItemInOffHand());
    }

    private boolean isSpawnEgg(ItemStack item) {
        return item != null && item.getType().name().endsWith("_SPAWN_EGG");
    }

    private Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            return projectileShooter(projectile);
        }
        return null;
    }

    private Player projectileShooter(Projectile projectile) {
        ProjectileSource shooter = projectile.getShooter();
        return shooter instanceof Player player ? player : null;
    }

    private Player asPlayer(Entity entity) {
        return entity instanceof Player player ? player : null;
    }

    private void sendDeny(Player player) {
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long next = nextMessageAt.get(player.getUniqueId());
        if (next != null && next > now) {
            return;
        }

        nextMessageAt.put(player.getUniqueId(), now + MESSAGE_THROTTLE_MS);
        String message = plugin.getConfigManager().spawnProtectionDenyMessage;
        if (message == null || message.isBlank()) {
            message = "<red>Spawn is protected. Ask staff if you need build access here.</red>";
        }
        player.sendMessage(MessageUtil.prefixedRaw(message));
    }

    private record AdminSpawnEggAllowance(Location location, long expiresAtMillis) {
        boolean matches(Location spawnLocation) {
            return location.getWorld() != null
                && spawnLocation.getWorld() != null
                && location.getWorld().getUID().equals(spawnLocation.getWorld().getUID())
                && location.distanceSquared(spawnLocation) <= ADMIN_SPAWN_EGG_RADIUS_SQUARED;
        }
    }

    private enum PublicInteractionAction {
        ADD,
        REMOVE
    }

    private record PendingPublicInteractionToggle(
        String blockKey,
        PublicInteractionAction action,
        long expiresAtMillis
    ) {
    }

    private record BoneMealGrowthArea(
        UUID worldId,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
        long expiresAtMillis
    ) {
        private boolean contains(Location location, long now) {
            return expiresAtMillis >= now
                && location != null
                && location.getWorld() != null
                && location.getWorld().getUID().equals(worldId)
                && location.getBlockX() >= minX
                && location.getBlockX() <= maxX
                && location.getBlockY() >= minY
                && location.getBlockY() <= maxY
                && location.getBlockZ() >= minZ
                && location.getBlockZ() <= maxZ;
        }
    }
}
