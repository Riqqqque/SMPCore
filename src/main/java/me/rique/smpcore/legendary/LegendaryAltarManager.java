package me.rique.smpcore.legendary;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.database.DatabaseManager;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.util.VisualRangeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class LegendaryAltarManager implements Listener {

    private static final String DISPLAY_TYPE_CRAFT_ITEM = "craft_item";
    private static final String DISPLAY_TYPE_RECIPE_TEXT = "recipe_text";

    private final SMPCore plugin;
    private final BossBar bossBar;
    private final NamespacedKey keyAltarDisplayType;
    private final Map<String, ClaimedLegendaryInstance> claimedLegendaryInstances = new ConcurrentHashMap<>();
    private final Set<String> createdLegendaryIds = ConcurrentHashMap.newKeySet();

    private volatile DatabaseManager.LegendaryAltarRecord altarRecord = DatabaseManager.LegendaryAltarRecord.empty();
    private volatile boolean loaded;
    private volatile boolean activationAnnounced;
    private volatile boolean claimedLegendaryIdsReady;

    public LegendaryAltarManager(SMPCore plugin) {
        this.plugin = plugin;
        this.bossBar = Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SOLID);
        this.keyAltarDisplayType = new NamespacedKey(plugin, "legendary_altar_display_type");
        this.bossBar.setVisible(false);
        loadState();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void reloadConfig() {
        refreshBossBar();
        ensureStructureState();
    }

    public void shutdown() {
        bossBar.removeAll();
        bossBar.setVisible(false);
    }

    public boolean isLegendaryClaimed(String legendaryId) {
        String normalized = normalizeLegendaryId(legendaryId);
        return normalized != null
            && (isLegendaryCreated(normalized) || activeLegendaryCount(normalized) >= maxServerCopies(normalized));
    }

    public boolean isLegendaryCreated(String legendaryId) {
        String normalized = normalizeLegendaryId(legendaryId);
        return normalized != null && createdLegendaryIds.contains(normalized);
    }

    public void retireLegendaryFromCycle(String legendaryId, java.util.UUID ownerId) {
        String normalized = normalizeLegendaryId(legendaryId);
        if (normalized == null) {
            return;
        }
        recordLegendaryCreated(normalized, ownerId);
        if (hasActiveAltar() && normalized.equals(normalizeLegendaryId(altarRecord.legendaryId()))) {
            clearActiveAltar(false);
        }
    }

    public int activeLegendaryCount(String legendaryId) {
        String normalized = normalizeLegendaryId(legendaryId);
        if (normalized == null) {
            return 0;
        }

        int count = 0;
        for (ClaimedLegendaryInstance claimedInstance : claimedLegendaryInstances.values()) {
            if (normalized.equals(claimedInstance.legendaryId())) {
                count++;
            }
        }
        if (count == 0 && createdLegendaryIds.contains(normalized)) {
            count++;
        }
        return count;
    }

    public void registerLegendaryInstance(String legendaryId, String instanceId, java.util.UUID ownerId, boolean craftedCurrentAltar) {
        String normalized = normalizeLegendaryId(legendaryId);
        if (normalized == null || instanceId == null || instanceId.isBlank()) {
            return;
        }

        registerLegendaryInstance(normalized, instanceId, ownerId, null, craftedCurrentAltar);
    }

    private void registerLegendaryInstance(
        String legendaryId,
        String instanceId,
        java.util.UUID ownerId,
        String sourceKey,
        boolean craftedCurrentAltar
    ) {
        String normalized = normalizeLegendaryId(legendaryId);
        String normalizedSource = normalizeSourceKey(sourceKey);
        if (normalized == null || instanceId == null || instanceId.isBlank()) {
            return;
        }

        ClaimedLegendaryInstance next = new ClaimedLegendaryInstance(normalized, ownerId, normalizedSource);
        ClaimedLegendaryInstance previous = claimedLegendaryInstances.put(instanceId, next);
        if (!next.equals(previous)) {
            plugin.getDatabase().saveClaimedLegendaryInstance(instanceId, normalized, ownerId, normalizedSource).exceptionally(throwable -> {
                plugin.getLogger().severe("Failed to persist active legendary instance " + instanceId + ": " + throwable.getMessage());
                return null;
            });
        }
        recordLegendaryCreated(normalized, ownerId);

        if (hasActiveAltar() && normalized.equals(normalizeLegendaryId(altarRecord.legendaryId()))) {
            clearActiveAltar(craftedCurrentAltar);
        }
    }

    public void syncLegendaryOwnership(java.util.UUID ownerId, Map<String, String> activeLegendaryInstancesById) {
        if (ownerId == null) {
            return;
        }

        Map<String, String> normalizedActiveInstances = new LinkedHashMap<>();
        if (activeLegendaryInstancesById != null) {
            for (Map.Entry<String, String> entry : activeLegendaryInstancesById.entrySet()) {
                String instanceId = entry.getKey();
                if (instanceId == null || instanceId.isBlank()) {
                    continue;
                }
                String legendaryId = entry.getValue();
                String normalized = normalizeLegendaryId(legendaryId);
                if (normalized != null) {
                    normalizedActiveInstances.put(instanceId.trim(), normalized);
                }
            }
        }

        for (Map.Entry<String, ClaimedLegendaryInstance> entry : List.copyOf(claimedLegendaryInstances.entrySet())) {
            ClaimedLegendaryInstance claimedInstance = entry.getValue();
            if (claimedInstance.sourceKey() != null
                || !ownerId.equals(claimedInstance.ownerId())
                || normalizedActiveInstances.containsKey(entry.getKey())) {
                continue;
            }
            if (claimedLegendaryInstances.remove(entry.getKey(), claimedInstance)) {
                plugin.getDatabase().deleteClaimedLegendaryInstance(entry.getKey()).exceptionally(throwable -> {
                    plugin.getLogger().severe("Failed to clear active legendary instance " + entry.getKey() + ": " + throwable.getMessage());
                    return null;
                });
            }
        }

        for (Map.Entry<String, String> entry : normalizedActiveInstances.entrySet()) {
            registerLegendaryInstance(entry.getValue(), entry.getKey(), ownerId, false);
        }
    }

    public void syncStoredLegendaryOwnership(String sourceKey, Map<String, String> activeLegendaryInstancesById) {
        String normalizedSource = normalizeSourceKey(sourceKey);
        if (normalizedSource == null) {
            return;
        }

        Map<String, String> normalizedActiveInstances = new LinkedHashMap<>();
        if (activeLegendaryInstancesById != null) {
            for (Map.Entry<String, String> entry : activeLegendaryInstancesById.entrySet()) {
                String instanceId = entry.getKey();
                if (instanceId == null || instanceId.isBlank()) {
                    continue;
                }
                String normalizedLegendary = normalizeLegendaryId(entry.getValue());
                if (normalizedLegendary != null) {
                    normalizedActiveInstances.put(instanceId.trim(), normalizedLegendary);
                }
            }
        }

        for (Map.Entry<String, ClaimedLegendaryInstance> entry : List.copyOf(claimedLegendaryInstances.entrySet())) {
            ClaimedLegendaryInstance claimedInstance = entry.getValue();
            if (!Objects.equals(normalizedSource, claimedInstance.sourceKey())
                || normalizedActiveInstances.containsKey(entry.getKey())) {
                continue;
            }
            if (claimedLegendaryInstances.remove(entry.getKey(), claimedInstance)) {
                plugin.getDatabase().deleteClaimedLegendaryInstance(entry.getKey()).exceptionally(throwable -> {
                    plugin.getLogger().severe("Failed to clear stored legendary instance " + entry.getKey() + ": " + throwable.getMessage());
                    return null;
                });
            }
        }

        for (Map.Entry<String, String> entry : normalizedActiveInstances.entrySet()) {
            registerLegendaryInstance(entry.getValue(), entry.getKey(), null, normalizedSource, false);
        }
    }

    public void sendRecipeHint(Player player, String requestedLegendaryId) {
        if (!loaded) {
            player.sendMessage(MessageUtil.info("Legendary altar data is still loading."));
            return;
        }
        if (!claimedLegendaryIdsReady) {
            player.sendMessage(MessageUtil.info("Legendary ownership data is still loading."));
            return;
        }

        String normalizedRequestedId = normalizeLegendaryId(requestedLegendaryId);
        if (normalizedRequestedId != null && isLegendaryClaimed(normalizedRequestedId)) {
            String requestedName = plugin.getLegendaryListener().displayNameForLegendary(normalizedRequestedId);
            player.sendMessage(MessageUtil.info(limitReachedMessage(requestedName, normalizedRequestedId)));
            if (hasActiveAltar() && normalizedRequestedId.equals(normalizeLegendaryId(altarRecord.legendaryId()))) {
                clearActiveAltar(false);
            }
            return;
        }

        String activeLegendaryId = altarRecord.legendaryId();
        if (activeLegendaryId == null) {
            player.sendMessage(MessageUtil.info(
                "There is no active legendary altar right now. Altars have a <white>1/20</white> chance to appear each night."
            ));
            return;
        }

        String requestedName = plugin.getLegendaryListener().displayNameForLegendary(requestedLegendaryId);
        String activeName = plugin.getLegendaryListener().displayNameForLegendary(activeLegendaryId);
        String coords = altarCoordsString();
        if (!activeLegendaryId.equals(requestedLegendaryId)) {
            player.sendMessage(MessageUtil.info(
                "The current altar is attuned to <white>" + activeName + "</white> at <white>" + coords + "</white>."
            ));
            return;
        }

        if (!isActivated()) {
            player.sendMessage(MessageUtil.info(
                "<white>" + requestedName + "</white> will unlock at <white>" + coords + "</white> in <white>"
                    + formatRemaining(altarRecord.activatesAt() - System.currentTimeMillis()) + "</white>."
            ));
            return;
        }

        player.sendMessage(MessageUtil.info(
            "<white>" + requestedName + "</white> can be crafted right now at <white>" + coords + "</white>."
        ));
    }

    public AdminActionResult altarStatusSummary() {
        if (!loaded) {
            return AdminActionResult.failure("Legendary altar data is still loading.");
        }
        if (!hasActiveAltar()) {
            return AdminActionResult.success("There is no active legendary altar right now.");
        }

        String displayName = plugin.getLegendaryListener() == null
            ? altarRecord.legendaryId()
            : plugin.getLegendaryListener().displayNameForLegendary(altarRecord.legendaryId());
        String phase = isActivated()
            ? "active, expires in " + formatRemaining(altarRecord.expiresAt() - System.currentTimeMillis())
            : "dormant, unlocks in " + formatRemaining(altarRecord.activatesAt() - System.currentTimeMillis());
        return AdminActionResult.success(displayName + " altar at " + altarCoordsString() + " is " + phase + ".");
    }

    public AdminActionResult clearForAdmin() {
        if (!loaded) {
            return AdminActionResult.failure("Legendary altar data is still loading.");
        }
        if (!hasActiveAltar()) {
            refreshBossBar();
            return AdminActionResult.failure("There is no active legendary altar to clear.");
        }

        String displayName = plugin.getLegendaryListener() == null
            ? altarRecord.legendaryId()
            : plugin.getLegendaryListener().displayNameForLegendary(altarRecord.legendaryId());
        long lastRollDay = altarRecord.lastRollDay();
        removeStructure();
        altarRecord = new DatabaseManager.LegendaryAltarRecord(null, null, 0, 0, 0, 0L, 0L, 0L, lastRollDay);
        activationAnnounced = false;
        refreshBossBar();
        persistRecord();
        return AdminActionResult.success("Cleared the altar for " + displayName + ".");
    }

    public AdminActionResult forceSpawnForTesting(String requestedLegendaryId, boolean activeImmediately) {
        if (!loaded) {
            return AdminActionResult.failure("Legendary altar data is still loading.");
        }
        if (!claimedLegendaryIdsReady) {
            return AdminActionResult.failure("Legendary ownership data is still loading.");
        }
        if (!plugin.getConfigManager().legendaryAltarEnabled) {
            return AdminActionResult.failure("Legendary altars are disabled in config.");
        }

        LegendaryListener legendary = plugin.getLegendaryListener();
        if (legendary == null) {
            return AdminActionResult.failure("Legendary items are not ready yet.");
        }

        String legendaryId = requestedLegendaryId == null || requestedLegendaryId.isBlank()
            ? pickLegendaryId()
            : legendary.normalizeLegendaryId(requestedLegendaryId);
        if (legendaryId == null || legendary.createLegendaryById(legendaryId) == null) {
            return AdminActionResult.failure(
                "Unknown legendary. Options: " + String.join(", ", legendary.legendaryIds()) + "."
            );
        }
        if (isLegendaryClaimed(legendaryId)) {
            String displayName = legendary.displayNameForLegendary(legendaryId);
            return AdminActionResult.failure(limitReachedMessage(displayName, legendaryId));
        }

        World world = configuredWorld();
        if (world == null) {
            return AdminActionResult.failure("Configured altar world is not loaded.");
        }

        Location location = findSpawnLocation(world);
        if (location == null) {
            return AdminActionResult.failure("Could not find a safe altar location in " + world.getName() + ".");
        }

        removeStructure();
        long now = System.currentTimeMillis();
        long currentDay = world.getFullTime() / 24000L;
        long lastRollDay = Math.max(currentDay, altarRecord.lastRollDay());
        long activatesAt = activeImmediately
            ? now
            : now + (plugin.getConfigManager().legendaryAltarActivationSeconds * 1000L);

        altarRecord = new DatabaseManager.LegendaryAltarRecord(
            legendaryId,
            world.getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ(),
            now,
            activatesAt,
            now + (plugin.getConfigManager().legendaryAltarExpirationHours * 3_600_000L),
            lastRollDay
        );
        activationAnnounced = activeImmediately;
        ensureStructureState();
        refreshBossBar();
        persistRecord();

        String displayName = legendary.displayNameForLegendary(legendaryId);
        String state = activeImmediately
            ? "Spawned a ready altar for "
            : "Spawned a dormant altar for ";
        return AdminActionResult.success(state + displayName + " at " + altarCoordsString() + ".");
    }

    public AdminActionResult summonFromMysticOrb(Player player) {
        if (!loaded) {
            return AdminActionResult.failure("Legendary altar data is still loading.");
        }
        if (!claimedLegendaryIdsReady) {
            return AdminActionResult.failure("Legendary ownership data is still loading.");
        }
        if (!plugin.getConfigManager().legendaryAltarEnabled) {
            return AdminActionResult.failure("Legendary altars are disabled right now.");
        }
        if (hasActiveAltar()) {
            return AdminActionResult.failure("There is already an active altar: " + altarStatusSummary().message());
        }

        LegendaryListener legendary = plugin.getLegendaryListener();
        if (legendary == null) {
            return AdminActionResult.failure("Legendary items are not ready yet.");
        }

        World world = configuredWorld();
        if (world == null) {
            return AdminActionResult.failure("Configured altar world is not loaded.");
        }

        String legendaryId = pickLegendaryId();
        if (legendaryId == null) {
            return AdminActionResult.failure("No craftable legendary altar rewards are available.");
        }

        Location location = findSpawnLocation(world);
        if (location == null) {
            return AdminActionResult.failure("Could not find a safe altar location in " + world.getName() + ".");
        }

        long now = System.currentTimeMillis();
        long currentDay = world.getFullTime() / 24000L;
        long activatesAt = now + (plugin.getConfigManager().legendaryAltarActivationSeconds * 1000L);
        altarRecord = new DatabaseManager.LegendaryAltarRecord(
            legendaryId,
            world.getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ(),
            now,
            activatesAt,
            now + (plugin.getConfigManager().legendaryAltarExpirationHours * 3_600_000L),
            Math.max(currentDay, altarRecord.lastRollDay())
        );
        activationAnnounced = false;
        ensureStructureState();
        refreshBossBar();
        persistRecord();

        String displayName = legendary.displayNameForLegendary(legendaryId);
        Bukkit.broadcast(MessageUtil.prefixedRaw(
            "<light_purple><white>" + player.getName() + "</white> used an Orb of the Mystics.</light_purple> "
                + "<gold>A legendary altar is forming.</gold> <white>" + displayName + "</white> will awaken in <white>"
                + plugin.getConfigManager().legendaryAltarActivationSeconds + "s</white>."
        ));
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0f, 0.8f);
        }

        return AdminActionResult.success("Summoned a dormant altar for " + displayName + " at " + altarCoordsString() + ".");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (hasActiveAltar() && plugin.getConfigManager().legendaryAltarBossBarEnabled) {
            bossBar.addPlayer(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;
        if (!isAltarBlock(event.getClickedBlock().getLocation())) return;

        event.setCancelled(true);
        handleCraftInteract(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!isAltarDisplay(event.getRightClicked(), DISPLAY_TYPE_CRAFT_ITEM)) return;
        Location center = altarLocation();
        if (center == null
            || !center.getWorld().equals(event.getRightClicked().getWorld())
            || event.getRightClicked().getLocation().distanceSquared(center.clone().add(0.5, 3.5, 0.5)) > 16.0) {
            return;
        }
        event.setCancelled(true);
        handleCraftInteract(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDisplayDamage(EntityDamageEvent event) {
        if (!isAltarDisplay(event.getEntity(), DISPLAY_TYPE_CRAFT_ITEM)
            && !isAltarDisplay(event.getEntity(), DISPLAY_TYPE_RECIPE_TEXT)) {
            return;
        }
        event.setCancelled(true);
    }

    private void handleCraftInteract(Player player) {
        if (!loaded) {
            player.sendMessage(MessageUtil.info("Legendary altar data is still loading."));
            return;
        }
        if (!claimedLegendaryIdsReady) {
            player.sendMessage(MessageUtil.info("Legendary ownership data is still loading."));
            return;
        }
        if (!hasActiveAltar()) {
            player.sendMessage(MessageUtil.info("This altar has faded."));
            return;
        }

        String legendaryId = altarRecord.legendaryId();
        String displayName = plugin.getLegendaryListener().displayNameForLegendary(legendaryId);
        if (!isActivated()) {
            player.sendMessage(MessageUtil.warn(
                "<white>" + displayName + "</white> unlocks in <white>"
                    + formatRemaining(altarRecord.activatesAt() - System.currentTimeMillis()) + "</white>."
            ));
            return;
        }

        if (isLegendaryClaimed(legendaryId)) {
            player.sendMessage(MessageUtil.warn(limitReachedMessage(displayName, legendaryId)));
            if (legendaryId.equals(normalizeLegendaryId(altarRecord.legendaryId()))) {
                clearActiveAltar(false);
            }
            return;
        }

        if (!plugin.getLegendaryListener().canCraftLegendary(player, legendaryId)) {
            player.sendMessage(MessageUtil.error("You are missing materials for <white>" + displayName + "</white>."));
            for (String line : plugin.getLegendaryListener().recipeProgressLines(player, legendaryId)) {
                player.sendMessage(MessageUtil.prefixedRaw(line));
            }
            return;
        }

        if (!plugin.getLegendaryListener().craftLegendaryAtAltar(player, legendaryId)) {
            player.sendMessage(MessageUtil.error("Legendary crafting failed. Try again."));
            return;
        }

        clearActiveAltar(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!isAltarBlock(event.getBlock().getLocation())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(MessageUtil.warn("The legendary altar cannot be broken."));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isAltarBlock(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> isAltarBlock(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTimeSkip(TimeSkipEvent event) {
        if (!loaded || hasActiveAltar()) return;

        World world = configuredWorld();
        if (world == null || !world.equals(event.getWorld())) return;

        long currentDay = world.getFullTime() / 24000L;
        if (altarRecord.lastRollDay() >= currentDay) return;

        long before = world.getTime();
        long skipAmount = event.getSkipAmount();
        if (!isNightSkip(event)) return;

        if (crossesAltarRollTime(before, skipAmount)) {
            tryNightlySpawn(true);
        }
    }

    private boolean isNightSkip(TimeSkipEvent event) {
        try {
            Object reason = event.getClass().getMethod("getSkipReason").invoke(event);
            return reason instanceof Enum<?> enumReason
                ? "NIGHT_SKIP".equals(enumReason.name())
                : "NIGHT_SKIP".equals(String.valueOf(reason));
        } catch (NoSuchMethodException ex) {
            // Some 26.1.x server jars do not expose a skip reason. Fall back to the time-crossing check.
            return true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().warning("Could not read time skip reason: " + ex.getMessage());
            return true;
        }
    }

    private void loadState() {
        CompletableFuture<DatabaseManager.LegendaryAltarRecord> altarFuture = plugin.getDatabase().loadLegendaryAltar();
        CompletableFuture<Set<String>> createdLegendaryIdsFuture = plugin.getDatabase().loadClaimedLegendaryIds();
        CompletableFuture<Map<String, DatabaseManager.LegendaryClaimedInstanceRecord>> claimedInstancesFuture =
            plugin.getDatabase().loadClaimedLegendaryInstances();
        CompletableFuture.allOf(altarFuture, createdLegendaryIdsFuture, claimedInstancesFuture).whenComplete((ignored, throwable) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                DatabaseManager.LegendaryAltarRecord record = DatabaseManager.LegendaryAltarRecord.empty();
                try {
                    record = altarFuture.join();
                } catch (CompletionException ex) {
                    plugin.getLogger().severe("Failed to load legendary altar data: " + ex.getCause().getMessage());
                }

                claimedLegendaryInstances.clear();
                createdLegendaryIds.clear();
                claimedLegendaryIdsReady = false;
                boolean claimDataLoaded = false;
                try {
                    Set<String> loadedCreatedIds = createdLegendaryIdsFuture.join();
                    for (String legendaryId : loadedCreatedIds) {
                        String normalized = normalizeLegendaryId(legendaryId);
                        if (normalized != null) {
                            createdLegendaryIds.add(normalized);
                        }
                    }

                    Map<String, DatabaseManager.LegendaryClaimedInstanceRecord> claimedInstances = claimedInstancesFuture.join();
                    for (DatabaseManager.LegendaryClaimedInstanceRecord entry : claimedInstances.values()) {
                        String normalized = normalizeLegendaryId(entry.legendaryId());
                        if (normalized != null) {
                            claimedLegendaryInstances.put(
                                entry.instanceId(),
                                new ClaimedLegendaryInstance(normalized, entry.ownerUuid(), normalizeSourceKey(entry.sourceKey()))
                            );
                            recordLegendaryCreated(normalized, entry.ownerUuid());
                        }
                    }
                    claimDataLoaded = true;
                } catch (CompletionException ex) {
                    plugin.getLogger().severe("Failed to load active legendary data: " + ex.getCause().getMessage());
                }

                DatabaseManager.LegendaryAltarRecord loadedRecord = record == null ? DatabaseManager.LegendaryAltarRecord.empty() : record;
                if (!claimDataLoaded) {
                    finishLoadedState(loadedRecord, false, null);
                    return;
                }

                CompletableFuture<Void> teamVaultSync = plugin.getTeamManager() == null
                    ? CompletableFuture.completedFuture(null)
                    : plugin.getTeamManager().resyncAllTeamVaultLegendaryClaims();
                teamVaultSync.whenComplete((ignoredSync, syncThrowable) -> {
                    if (!plugin.isEnabled()) {
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () ->
                        finishLoadedState(loadedRecord, syncThrowable == null, syncThrowable)
                    );
                });
            });
        });
    }

    private void finishLoadedState(DatabaseManager.LegendaryAltarRecord record, boolean claimsReady, Throwable throwable) {
        if (throwable != null) {
            Throwable root = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
            plugin.getLogger().severe("Failed to warm legendary team-vault claim data: " + root.getMessage());
            plugin.getLogger().severe("Legendary altar rolls are paused until claim data can load safely.");
        }

        claimedLegendaryIdsReady = claimsReady;
        altarRecord = record == null ? DatabaseManager.LegendaryAltarRecord.empty() : record;
        loaded = true;
        activationAnnounced = hasActiveAltar() && System.currentTimeMillis() >= altarRecord.activatesAt();

        if (!hasActiveAltar()) {
            refreshBossBar();
            return;
        }

        if (System.currentTimeMillis() >= altarRecord.expiresAt()) {
            clearActiveAltar(false);
            return;
        }

        if (claimsReady && isLegendaryClaimed(altarRecord.legendaryId())) {
            clearActiveAltar(false);
            return;
        }

        ensureStructureState();
        refreshBossBar();
    }

    private void tick() {
        if (!loaded) return;

        tryNightlySpawn(false);
        if (!hasActiveAltar()) {
            refreshBossBar();
            return;
        }

        long now = System.currentTimeMillis();
        if (now >= altarRecord.expiresAt()) {
            clearActiveAltar(false);
            return;
        }

        boolean justActivated = !activationAnnounced && now >= altarRecord.activatesAt();

        ensureStructureState();
        spawnBeaconParticles();
        refreshBossBar();

        if (justActivated) {
            activateAltar();
        }
    }

    private void tryNightlySpawn(boolean allowSkippedNight) {
        if (!plugin.getConfigManager().legendaryAltarEnabled) return;
        if (hasActiveAltar()) return;
        if (!claimedLegendaryIdsReady) return;

        World world = configuredWorld();
        if (world == null) return;

        long day = world.getFullTime() / 24000L;
        if (altarRecord.lastRollDay() >= day) return;

        long time = world.getTime();
        if (!allowSkippedNight && time < plugin.getConfigManager().legendaryAltarRollTimeTicks) return;
        if (!allowSkippedNight && time > 23950L) return;
        if (plugin.getConfigManager().legendaryAltarRequirePlayerOnline && world.getPlayers().isEmpty()) return;

        DatabaseManager.LegendaryAltarRecord nextRollMarker = new DatabaseManager.LegendaryAltarRecord(
            null, null, 0, 0, 0, 0L, 0L, 0L, day
        );

        if (ThreadLocalRandom.current().nextDouble() >= plugin.getConfigManager().legendaryAltarNightlyChance) {
            altarRecord = nextRollMarker;
            persistRecord();
            return;
        }

        String legendaryId = pickLegendaryId();
        Location location = findSpawnLocation(world);
        if (legendaryId == null || location == null) {
            altarRecord = nextRollMarker;
            persistRecord();
            return;
        }

        long now = System.currentTimeMillis();
        altarRecord = new DatabaseManager.LegendaryAltarRecord(
            legendaryId,
            world.getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ(),
            now,
            now + (plugin.getConfigManager().legendaryAltarActivationSeconds * 1000L),
            now + (plugin.getConfigManager().legendaryAltarExpirationHours * 3_600_000L),
            day
        );
        activationAnnounced = false;
        ensureStructureState();
        refreshBossBar();
        persistRecord();

        String displayName = plugin.getLegendaryListener().displayNameForLegendary(legendaryId);
        Bukkit.broadcast(MessageUtil.prefixedRaw(
            "<gold>A legendary altar is forming.</gold> <white>" + displayName + "</white> will awaken in <white>"
                + plugin.getConfigManager().legendaryAltarActivationSeconds + "s</white>."
        ));
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0f, 0.8f);
        }
    }

    private void activateAltar() {
        activationAnnounced = true;
        ensureStructureState();
        String displayName = plugin.getLegendaryListener().displayNameForLegendary(altarRecord.legendaryId());
        Bukkit.broadcast(MessageUtil.prefixedRaw(
            "<green>The legendary altar for <white>" + displayName + "</white> is now active at <white>"
                + altarCoordsString() + "</white>.</green>"
        ));
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.1f);
        }
    }

    private void clearActiveAltar(boolean crafted) {
        if (!hasActiveAltar()) {
            activationAnnounced = false;
            refreshBossBar();
            return;
        }

        String displayName = plugin.getLegendaryListener().displayNameForLegendary(altarRecord.legendaryId());
        removeStructure();

        long lastRollDay = altarRecord.lastRollDay();
        altarRecord = new DatabaseManager.LegendaryAltarRecord(null, null, 0, 0, 0, 0L, 0L, 0L, lastRollDay);
        activationAnnounced = false;
        refreshBossBar();
        persistRecord();

        Bukkit.broadcast(MessageUtil.prefixedRaw(
            crafted
                ? "<gold>The altar for <white>" + displayName + "</white> has been consumed.</gold>"
                : "<gray>The altar for <white>" + displayName + "</white> faded away.</gray>"
        ));
    }

    private void ensureStructureState() {
        if (!hasActiveAltar()) return;
        Location center = altarLocation();
        if (center == null) return;
        World world = center.getWorld();
        if (world == null) return;

        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Block block = world.getBlockAt(centerX + x, centerY, centerZ + z);
                boolean outerRing = Math.abs(x) == 2 || Math.abs(z) == 2;
                boolean cardinalOuter = (x == 0 && Math.abs(z) == 2) || (z == 0 && Math.abs(x) == 2);
                if (cardinalOuter) {
                    setAltarStair(block, facingFor(x, z));
                } else {
                    setAltarBlock(block, outerRing ? Material.DEEPSLATE_BRICKS : Material.SMOOTH_QUARTZ);
                }
            }
        }

        clearPedestalAir(world, centerX, centerY + 1, centerZ);
        setAltarBlock(world.getBlockAt(centerX, centerY + 1, centerZ), Material.DEEPSLATE_BRICKS);
        setAltarStair(world.getBlockAt(centerX, centerY + 1, centerZ - 1), BlockFace.NORTH);
        setAltarStair(world.getBlockAt(centerX + 1, centerY + 1, centerZ), BlockFace.EAST);
        setAltarStair(world.getBlockAt(centerX, centerY + 1, centerZ + 1), BlockFace.SOUTH);
        setAltarStair(world.getBlockAt(centerX - 1, centerY + 1, centerZ), BlockFace.WEST);
        clearPedestalAir(world, centerX, centerY + 2, centerZ);
        setAltarBlock(world.getBlockAt(centerX, centerY + 2, centerZ), Material.DEEPSLATE_TILE_WALL);
        ensureDisplayEntities(center);
    }

    private void removeStructure() {
        Location center = altarLocation();
        if (center == null) return;
        World world = center.getWorld();
        if (world == null) return;
        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                clearAltarBlock(world.getBlockAt(centerX + x, centerY, centerZ + z));
            }
        }
        clearAltarBlock(world.getBlockAt(centerX, centerY + 1, centerZ));
        clearAltarBlock(world.getBlockAt(centerX, centerY + 1, centerZ - 1));
        clearAltarBlock(world.getBlockAt(centerX + 1, centerY + 1, centerZ));
        clearAltarBlock(world.getBlockAt(centerX, centerY + 1, centerZ + 1));
        clearAltarBlock(world.getBlockAt(centerX - 1, centerY + 1, centerZ));
        clearAltarBlock(world.getBlockAt(centerX, centerY + 2, centerZ));
        removeDisplayEntities(center);
    }

    private void clearPedestalAir(World world, int centerX, int y, int centerZ) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block block = world.getBlockAt(centerX + x, y, centerZ + z);
                if (isAltarMaterial(block.getType())) {
                    block.setType(Material.AIR, false);
                }
            }
        }
    }

    private void setAltarBlock(Block block, Material material) {
        if (block.getType() != material) {
            block.setType(material, false);
        }
    }

    private void setAltarStair(Block block, BlockFace facing) {
        if (block.getType() != Material.DEEPSLATE_TILE_STAIRS) {
            block.setType(Material.DEEPSLATE_TILE_STAIRS, false);
        }
        if (block.getBlockData() instanceof Stairs stairs) {
            stairs.setFacing(facing);
            block.setBlockData(stairs, false);
        }
    }

    private void clearAltarBlock(Block block) {
        if (isAltarMaterial(block.getType())) {
            block.setType(Material.AIR, false);
        }
    }

    private BlockFace facingFor(int x, int z) {
        if (z < 0) return BlockFace.NORTH;
        if (z > 0) return BlockFace.SOUTH;
        if (x > 0) return BlockFace.EAST;
        return BlockFace.WEST;
    }

    private void ensureDisplayEntities(Location center) {
        World world = center.getWorld();
        LegendaryListener legendary = plugin.getLegendaryListener();
        if (world == null || legendary == null) {
            return;
        }

        ItemDisplay itemDisplay = null;
        TextDisplay textDisplay = null;
        Location searchCenter = center.clone().add(0.5, 3.5, 0.5);
        for (Entity entity : world.getNearbyEntities(searchCenter, 2.0, 3.0, 2.0)) {
            if (itemDisplay == null && isAltarDisplay(entity, DISPLAY_TYPE_CRAFT_ITEM) && entity instanceof ItemDisplay display) {
                itemDisplay = display;
            } else if (textDisplay == null && isAltarDisplay(entity, DISPLAY_TYPE_RECIPE_TEXT) && entity instanceof TextDisplay display) {
                textDisplay = display;
            }
        }

        ItemStack displayItem = legendary.createLegendaryById(altarRecord.legendaryId());
        if (displayItem != null) {
            displayItem.setAmount(1);
        }
        Location itemLocation = center.clone().add(0.5, 3.35, 0.5);
        if (itemDisplay == null || !itemDisplay.isValid()) {
            itemDisplay = world.spawn(itemLocation, ItemDisplay.class, display -> {
                tagDisplay(display, DISPLAY_TYPE_CRAFT_ITEM);
                display.setGravity(false);
                display.setPersistent(false);
                display.setInvulnerable(true);
                display.setBillboard(Display.Billboard.CENTER);
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                VisualRangeUtil.applyHologramRange(display);
            });
        }
        itemDisplay.teleport(itemLocation);
        VisualRangeUtil.applyHologramRange(itemDisplay);
        itemDisplay.setItemStack(displayItem);

        String statusLine = isActivated()
            ? "Right-click the item to craft"
            : "Unlocks in " + formatRemaining(altarRecord.activatesAt() - System.currentTimeMillis());
        StringBuilder text = new StringBuilder();
        text.append(stripMiniMessage(legendary.displayNameForLegendary(altarRecord.legendaryId())));
        for (String line : legendary.altarRequirementLines(altarRecord.legendaryId())) {
            text.append('\n').append(line);
        }
        text.append('\n').append(statusLine);

        Location textLocation = center.clone().add(0.5, 4.55, 0.5);
        if (textDisplay == null || !textDisplay.isValid()) {
            textDisplay = world.spawn(textLocation, TextDisplay.class, display -> {
                tagDisplay(display, DISPLAY_TYPE_RECIPE_TEXT);
                display.setGravity(false);
                display.setPersistent(false);
                display.setInvulnerable(true);
                display.setBillboard(Display.Billboard.CENTER);
                display.setSeeThrough(false);
                display.setShadowed(false);
                display.setLineWidth(220);
                VisualRangeUtil.applyHologramRange(display);
            });
        }
        textDisplay.teleport(textLocation);
        VisualRangeUtil.applyHologramRange(textDisplay);
        textDisplay.text(Component.text(text.toString()));
    }

    private void removeDisplayEntities(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Location searchCenter = center.clone().add(0.5, 3.5, 0.5);
        for (Entity entity : world.getNearbyEntities(searchCenter, 2.0, 3.0, 2.0)) {
            if (isAltarDisplay(entity, DISPLAY_TYPE_CRAFT_ITEM) || isAltarDisplay(entity, DISPLAY_TYPE_RECIPE_TEXT)) {
                entity.remove();
            }
        }
    }

    private void tagDisplay(Entity entity, String type) {
        entity.getPersistentDataContainer().set(keyAltarDisplayType, PersistentDataType.STRING, type);
    }

    private boolean isAltarDisplay(Entity entity, String type) {
        String stored = entity.getPersistentDataContainer().get(keyAltarDisplayType, PersistentDataType.STRING);
        return type.equals(stored);
    }

    private String stripMiniMessage(String input) {
        return input.replaceAll("<[^>]+>", "");
    }

    private void refreshBossBar() {
        if (!plugin.getConfigManager().legendaryAltarBossBarEnabled || !hasActiveAltar()) {
            bossBar.removeAll();
            bossBar.setVisible(false);
            return;
        }

        Location location = altarLocation();
        if (location == null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
            return;
        }

        String displayName = plugin.getLegendaryListener().displayNameForLegendary(altarRecord.legendaryId());
        String coords = coordsString(location);
        long now = System.currentTimeMillis();

        double progress;
        if (now < altarRecord.activatesAt()) {
            long remaining = altarRecord.activatesAt() - now;
            long total = Math.max(1L, altarRecord.activatesAt() - altarRecord.spawnedAt());
            progress = Math.max(0.0, Math.min(1.0, 1.0 - (remaining / (double) total)));
            bossBar.setColor(BarColor.PURPLE);
        } else {
            long remaining = altarRecord.expiresAt() - now;
            long total = Math.max(1L, altarRecord.expiresAt() - altarRecord.activatesAt());
            progress = Math.max(0.0, Math.min(1.0, remaining / (double) total));
            bossBar.setColor(BarColor.GREEN);
        }

        bossBar.setTitle(displayName + " altar | " + coords + (now < altarRecord.activatesAt()
            ? " | unlocks in " + formatRemaining(altarRecord.activatesAt() - now)
            : " | expires in " + formatRemaining(altarRecord.expiresAt() - now)));
        bossBar.setProgress(progress);
        bossBar.setVisible(true);
        for (Player online : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(online);
        }
    }

    private void spawnBeaconParticles() {
        Location center = altarLocation();
        if (center == null || center.getWorld() == null) return;

        World world = center.getWorld();
        double radius = plugin.getConfigManager().legendaryAltarBeaconViewRange;
        boolean active = isActivated();

        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(center) > radius * radius) continue;

            for (double y = 0.5; y <= 12.5; y += 1.5) {
                Location point = center.clone().add(0.5, y, 0.5);
                world.spawnParticle(active ? Particle.END_ROD : Particle.ENCHANT, point, 2, 0.05, 0.25, 0.05, 0.0);
            }

            if (active) {
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(0.5, 1.2, 0.5), 12, 0.45, 0.15, 0.45, 0.01);
                world.spawnParticle(Particle.PORTAL, center.clone().add(0.5, 1.0, 0.5), 10, 0.40, 0.20, 0.40, 0.02);
            } else {
                world.spawnParticle(Particle.WITCH, center.clone().add(0.5, 1.0, 0.5), 8, 0.40, 0.20, 0.40, 0.01);
            }
        }
    }

    private String pickLegendaryId() {
        LegendaryListener legendary = plugin.getLegendaryListener();
        if (legendary == null) return null;
        if (!claimedLegendaryIdsReady) return null;

        List<String> ids = legendary.craftableLegendaryIds();
        ids.removeIf(this::isLegendaryClaimed);
        if (ids.isEmpty()) return null;
        return ids.get(ThreadLocalRandom.current().nextInt(ids.size()));
    }

    private int maxServerCopies(String legendaryId) {
        LegendaryListener legendary = plugin.getLegendaryListener();
        return legendary == null ? 1 : Math.max(1, legendary.maxServerCopiesForLegendary(legendaryId));
    }

    private String limitReachedMessage(String displayName, String legendaryId) {
        String shownName = displayName == null || displayName.isBlank() ? legendaryId : displayName;
        if (isLegendaryCreated(legendaryId)) {
            return "<white>" + shownName + "</white> has already been created on this server.";
        }
        int limit = maxServerCopies(legendaryId);
        if (limit <= 1) {
            return "<white>" + shownName + "</white> already exists on the server right now.";
        }
        return "<white>" + shownName + "</white> is already at its server limit right now.";
    }

    private void recordLegendaryCreated(String legendaryId, java.util.UUID ownerId) {
        String normalized = normalizeLegendaryId(legendaryId);
        if (normalized == null) {
            return;
        }

        if (createdLegendaryIds.add(normalized)) {
            plugin.getDatabase().saveClaimedLegendaryOwner(normalized, ownerId).exceptionally(throwable -> {
                plugin.getLogger().severe("Failed to persist created legendary claim " + normalized + ": " + throwable.getMessage());
                return null;
            });
        }
    }

    private String normalizeLegendaryId(String legendaryId) {
        if (legendaryId == null) {
            return null;
        }
        LegendaryListener legendary = plugin.getLegendaryListener();
        if (legendary != null) {
            String normalized = legendary.normalizeLegendaryId(legendaryId);
            if (normalized != null && !normalized.isBlank()) {
                return normalized;
            }
        }
        String normalized = legendaryId.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeSourceKey(String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank()) {
            return null;
        }
        String normalized = sourceKey.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private Location findSpawnLocation(World world) {
        Location spawn = world.getSpawnLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int minDistance = plugin.getConfigManager().legendaryAltarMinDistanceFromSpawn;
        int maxDistance = plugin.getConfigManager().legendaryAltarMaxDistanceFromSpawn;

        for (int attempt = 0; attempt < plugin.getConfigManager().legendaryAltarSearchAttempts; attempt++) {
            double angle = random.nextDouble(Math.PI * 2.0);
            double distance = random.nextDouble(minDistance, maxDistance + 1.0);
            int x = spawn.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = spawn.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);

            Block ground = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (!isValidGround(ground)) continue;

            Location center = ground.getLocation().add(0.0, 1.0, 0.0);
            if (!canPlacePlatform(center)) continue;
            return center;
        }

        plugin.getLogger().warning("Failed to find a valid legendary altar location in world " + world.getName() + ".");
        return null;
    }

    private boolean isValidGround(Block ground) {
        Material material = ground.getType();
        return material.isSolid()
            && !Tag.LEAVES.isTagged(material)
            && material != Material.BEDROCK
            && material != Material.LAVA
            && material != Material.WATER
            && material != Material.CACTUS
            && material != Material.POWDER_SNOW;
    }

    private boolean canPlacePlatform(Location center) {
        World world = center.getWorld();
        if (world == null) return false;

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Block block = world.getBlockAt(center.getBlockX() + x, center.getBlockY(), center.getBlockZ() + z);
                Block above = block.getRelative(0, 1, 0);
                Block aboveTwo = block.getRelative(0, 2, 0);
                if (!block.isPassable() || block.isLiquid()) return false;
                if (!above.isPassable() || above.isLiquid()) return false;
                if (!aboveTwo.isPassable() || aboveTwo.isLiquid()) return false;
            }
        }
        return true;
    }

    private boolean hasActiveAltar() {
        return altarRecord.hasActiveAltar();
    }

    private boolean isActivated() {
        return hasActiveAltar() && System.currentTimeMillis() >= altarRecord.activatesAt();
    }

    private Location altarLocation() {
        if (!hasActiveAltar()) return null;
        World world = Bukkit.getWorld(altarRecord.world());
        if (world == null) return null;
        return new Location(world, altarRecord.x(), altarRecord.y(), altarRecord.z());
    }

    private World configuredWorld() {
        String worldName = plugin.getConfigManager().legendaryAltarWorld;
        if (worldName == null || worldName.isBlank()) return null;
        return Bukkit.getWorld(worldName);
    }

    private boolean isAltarBlock(Location location) {
        if (!hasActiveAltar() || location == null || location.getWorld() == null) return false;
        Location center = altarLocation();
        if (center == null || !center.getWorld().equals(location.getWorld())) return false;

        int dx = location.getBlockX() - center.getBlockX();
        int dy = location.getBlockY() - center.getBlockY();
        int dz = location.getBlockZ() - center.getBlockZ();
        if (dy == 0) {
            return Math.abs(dx) <= 2 && Math.abs(dz) <= 2;
        }
        if (dy == 1) {
            return (dx == 0 && dz == 0) || (Math.abs(dx) + Math.abs(dz) == 1);
        }
        return dy == 2 && dx == 0 && dz == 0;
    }

    private boolean isAltarMaterial(Material material) {
        return material == Material.DEEPSLATE_BRICKS
            || material == Material.DEEPSLATE_TILE_STAIRS
            || material == Material.DEEPSLATE_TILE_WALL
            || material == Material.SMOOTH_QUARTZ;
    }

    private String coordsString(Location location) {
        return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }

    private String altarCoordsString() {
        Location location = altarLocation();
        if (location != null) {
            return coordsString(location);
        }
        return altarRecord.x() + ", " + altarRecord.y() + ", " + altarRecord.z();
    }

    private boolean crossesAltarRollTime(long before, long skipAmount) {
        if (skipAmount <= 0L) {
            return false;
        }

        long rollTime = plugin.getConfigManager().legendaryAltarRollTimeTicks;
        long normalizedBefore = Math.floorMod(before, 24000L);
        long normalizedAfter = normalizedBefore + skipAmount;
        return normalizedBefore < rollTime && normalizedAfter >= rollTime;
    }

    private String formatRemaining(long millis) {
        long totalSeconds = Math.max(0L, (millis + 999L) / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    private void persistRecord() {
        plugin.getDatabase().saveLegendaryAltar(altarRecord).exceptionally(throwable -> {
            plugin.getLogger().severe("Failed to save legendary altar data: " + throwable.getMessage());
            return null;
        });
    }

    public record AdminActionResult(boolean success, String message) {
        public static AdminActionResult success(String message) {
            return new AdminActionResult(true, message);
        }

        public static AdminActionResult failure(String message) {
            return new AdminActionResult(false, message);
        }
    }

    private record ClaimedLegendaryInstance(String legendaryId, java.util.UUID ownerId, String sourceKey) {
    }
}
