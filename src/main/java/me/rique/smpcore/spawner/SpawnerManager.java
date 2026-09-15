package me.rique.smpcore.spawner;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.VisualRangeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Central manager for custom spawner data, persistence, and holograms.
 */
public final class SpawnerManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int BASE_MIN_DELAY = 200;
    private static final int BASE_MAX_DELAY = 800;
    private static final int VANILLA_SPAWN_COUNT = 4;
    private static final Color HOLOGRAM_BACKGROUND = Color.fromARGB(160, 0, 0, 0);

    public enum MergeResult {
        SUCCESS,
        NOT_TRACKED,
        TYPE_MISMATCH,
        INVALID_AMOUNT,
        WOULD_EXCEED_MAX
    }

    private final SMPCore plugin;
    private final NamespacedKey keySpawnerHologram;
    private final Map<String, SpawnerData> cache = new ConcurrentHashMap<>();
    private final Map<String, UUID> holograms = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> chunkIndex = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Void>> pendingPersistence = new ConcurrentHashMap<>();
    private final Set<String> pendingHologramUpdates = ConcurrentHashMap.newKeySet();

    public SpawnerManager(SMPCore plugin) {
        this.plugin = plugin;
        this.keySpawnerHologram = new NamespacedKey(plugin, "spawner_hologram");
    }

    public static String key(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    public static String key(Location loc) {
        return key(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Load the authoritative spawner cache before listeners are registered. Startup must not
     * accept player edits while an older database snapshot can still overwrite them.
     */
    public void loadAllBlocking() {
        List<SpawnerData> loaded;
        try {
            loaded = plugin.getDatabase().loadAllSpawners().join();
        } catch (CompletionException ex) {
            throw new IllegalStateException("Failed to load spawners", ex.getCause());
        }

        for (SpawnerData raw : loaded) {
            SpawnerData data = normalizeLoadedData(raw);
            String blockKey = key(data.world(), data.x(), data.y(), data.z());
            cache.put(blockKey, data);
            index(blockKey, data.world(), data.x(), data.z());
            if (!sameStoredState(raw, data)) {
                queueSave(blockKey, data);
            }

            World world = Bukkit.getWorld(data.world());
            if (world == null || !world.isChunkLoaded(data.x() >> 4, data.z() >> 4)) continue;

            Location loc = new Location(world, data.x(), data.y(), data.z());
            if (loc.getBlock().getType() != org.bukkit.Material.SPAWNER) {
                removeStaleEntry(blockKey, data);
                continue;
            }
            applySpeedToBlock(loc, data);
            spawnHologram(data);
        }
    }

    public void shutdown() {
        pendingHologramUpdates.clear();
        destroyAllHolograms();
        List<CompletableFuture<Void>> saves = new ArrayList<>();
        cache.values().stream()
            .filter(SpawnerData::isDirty)
            .forEach(data -> saves.add(queueSave(key(data.world(), data.x(), data.y(), data.z()), data)));
        saves.addAll(pendingPersistence.values());
        if (!saves.isEmpty()) {
            try {
                CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new)).join();
            } catch (Exception ex) {
                plugin.getLogger().severe("Failed to flush spawner data on shutdown: " + ex.getMessage());
            }
        }
    }

    /**
     * Re-apply live spawner/hologram state after config changes.
     * Only touches currently loaded chunks to avoid forcing chunk loads.
     */
    public void refreshAllFromConfig() {
        Runnable task = () -> {
            for (SpawnerData data : cache.values()) {
                World world = Bukkit.getWorld(data.world());
                if (world == null || !world.isChunkLoaded(data.x() >> 4, data.z() >> 4)) continue;
                Location loc = new Location(world, data.x(), data.y(), data.z());
                applySpeedToBlock(loc, data);
                updateHologram(loc);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public SpawnerData getData(Location loc) {
        return cache.get(key(loc));
    }

    public SpawnerData getData(World world, int x, int y, int z) {
        if (world == null) return null;
        return cache.get(key(world.getName(), x, y, z));
    }

    public boolean isTracked(Location loc) {
        return cache.containsKey(key(loc));
    }

    public void register(Location loc, String entityType, int stackCount) {
        register(loc, entityType, stackCount, 0, false, false);
    }

    public void register(Location loc, String entityType, int stackCount,
                         int sugarCount, boolean redstoneControlled, boolean aiNerfed) {
        int clampedStack = Math.max(1, Math.min(stackCount, plugin.getConfigManager().spawnerMaxStack));
        int clampedSugar = Math.max(0, Math.min(sugarCount, plugin.getConfigManager().spawnerMaxSugar));
        String normalizedType = normalizeEntityType(entityType);
        SpawnerData data = new SpawnerData(
            loc.getWorld().getName(),
            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
            normalizedType, clampedStack, clampedSugar, redstoneControlled, aiNerfed
        );
        String blockKey = key(loc);
        cache.put(blockKey, data);
        index(blockKey, data.world(), data.x(), data.z());
        queueSave(blockKey, data);
        applySpeedToBlock(loc, data);
        scheduleHologramUpdate(blockKey);
    }

    public void unregister(Location loc) {
        String blockKey = key(loc);
        SpawnerData removed = cache.remove(blockKey);
        if (removed != null) {
            unindex(blockKey, removed.world(), removed.x(), removed.z());
        }
        destroyHologram(blockKey);
        pendingHologramUpdates.remove(blockKey);
        queueDelete(blockKey, loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public int addSugar(Location loc, int amount) {
        SpawnerData data = getData(loc);
        if (data == null) return 0;
        int added = data.addSugar(amount, plugin.getConfigManager().spawnerMaxSugar);
        if (added > 0) {
            applySpeedToBlock(loc, data);
            persistAndUpdateHologram(loc, data);
        }
        return added;
    }

    public void toggleRedstone(Location loc) {
        SpawnerData data = getData(loc);
        if (data == null) return;
        data.toggleRedstone();
        persistAndUpdateHologram(loc, data);
    }

    public void toggleAiNerf(Location loc) {
        SpawnerData data = getData(loc);
        if (data == null) return;
        data.toggleAiNerf();
        persistAndUpdateHologram(loc, data);
    }

    public void resetModifiers(Location loc) {
        SpawnerData data = getData(loc);
        if (data == null) return;
        data.resetModifiers();
        applySpeedToBlock(loc, data);
        persistAndUpdateHologram(loc, data);
    }

    public void setEntityType(Location loc, String entityType) {
        String normalizedType = normalizeEntityType(entityType);
        SpawnerData data = getData(loc);
        if (data == null) {
            if (loc.getBlock().getState() instanceof CreatureSpawner cs) {
                try {
                    cs.setSpawnedType(org.bukkit.entity.EntityType.valueOf(normalizedType));
                    cs.update(true, false);
                } catch (IllegalArgumentException ignored) {
                    // Keep current type if invalid.
                }
            }
            register(loc, normalizedType, 1);
            return;
        }
        data.setEntityType(normalizedType);
        if (loc.getBlock().getState() instanceof CreatureSpawner cs) {
            try {
                cs.setSpawnedType(org.bukkit.entity.EntityType.valueOf(normalizedType));
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid/unknown entity type and keep current block state.
            }
            cs.update(true, false);
        }
        applySpeedToBlock(loc, data);
        persistAndUpdateHologram(loc, data);
    }

    public MergeResult mergeStack(Location loc, String entityType, int addCount) {
        SpawnerData data = getData(loc);
        if (data == null) return MergeResult.NOT_TRACKED;
        if (addCount <= 0) return MergeResult.INVALID_AMOUNT;
        String normalizedType = normalizeEntityType(entityType);
        if (!data.entityType().equals(normalizedType)) return MergeResult.TYPE_MISMATCH;
        int maxStack = plugin.getConfigManager().spawnerMaxStack;
        long mergedCount = (long) data.stackCount() + addCount;
        if (mergedCount > maxStack) return MergeResult.WOULD_EXCEED_MAX;
        data.setStackCount((int) mergedCount);
        applySpeedToBlock(loc, data);
        persistAndUpdateHologram(loc, data);
        return MergeResult.SUCCESS;
    }

    /** Push speed modifiers into the actual CreatureSpawner block state. */
    public void applySpeedToBlock(Location loc, SpawnerData data) {
        if (!(loc.getBlock().getState() instanceof CreatureSpawner cs)) return;
        SpawnerBlockSettings settings = calculateBlockSettings(
            data,
            plugin.getConfigManager().spawnerMaxSugar,
            plugin.getConfigManager().spawnerMaxMultiplier,
            plugin.getConfigManager().spawnerMinDelayFloor,
            plugin.getConfigManager().spawnerStackSpawnCountCap,
            plugin.getConfigManager().spawnerMaxNearbyEntitiesCap
        );

        int currentMinDelay = cs.getMinSpawnDelay();
        int currentMaxDelay = cs.getMaxSpawnDelay();
        int currentDelay = cs.getDelay();
        int clampedDelay = clampActiveDelay(currentDelay, settings.minDelay(), settings.maxDelay());
        boolean delayRangeChanged = currentMinDelay != settings.minDelay()
            || currentMaxDelay != settings.maxDelay();
        boolean blockSettingsChanged = delayRangeChanged
            || currentDelay != clampedDelay
            || cs.getSpawnCount() != settings.spawnCount()
            || cs.getMaxNearbyEntities() != settings.maxNearbyEntities();
        boolean storedDataChanged = !spawnerStateMatches(cs, data);
        if (!blockSettingsChanged && !storedDataChanged) return;

        if (delayRangeChanged) {
            // Respect Bukkit's min/max relationship while moving either range boundary.
            if (settings.minDelay() > currentMaxDelay) {
                cs.setMaxSpawnDelay(settings.maxDelay());
                cs.setMinSpawnDelay(settings.minDelay());
            } else {
                cs.setMinSpawnDelay(settings.minDelay());
                cs.setMaxSpawnDelay(settings.maxDelay());
            }
        }
        if (cs.getSpawnCount() != settings.spawnCount()) {
            cs.setSpawnCount(settings.spawnCount());
        }
        if (cs.getMaxNearbyEntities() != settings.maxNearbyEntities()) {
            cs.setMaxNearbyEntities(settings.maxNearbyEntities());
        }
        if (currentDelay != clampedDelay) {
            cs.setDelay(clampedDelay);
        }
        if (storedDataChanged) {
            writeDataToSpawnerState(cs, data);
        }
        cs.update(true, false);
    }

    static SpawnerBlockSettings calculateBlockSettings(
        SpawnerData data,
        int maxSugar,
        double maxMultiplier,
        int minDelayFloor,
        int spawnCountCap,
        int maxNearbyEntitiesCap
    ) {
        int minDelay = Math.max(
            Math.max(1, minDelayFloor),
            data.adjustedDelay(BASE_MIN_DELAY, maxSugar, maxMultiplier)
        );
        int maxDelay = Math.max(minDelay, data.adjustedDelay(BASE_MAX_DELAY, maxSugar, maxMultiplier));
        int scaledSpawnCount = Math.max(
            VANILLA_SPAWN_COUNT,
            (int) Math.round(VANILLA_SPAWN_COUNT * Math.sqrt(Math.max(1, data.stackCount())))
        );
        scaledSpawnCount = Math.min(Math.max(VANILLA_SPAWN_COUNT, spawnCountCap), scaledSpawnCount);
        int maxNearbyEntities = Math.max(
            16,
            Math.min(Math.max(16, maxNearbyEntitiesCap), scaledSpawnCount * 4)
        );
        return new SpawnerBlockSettings(minDelay, maxDelay, scaledSpawnCount, maxNearbyEntities);
    }

    static int clampActiveDelay(int currentDelay, int minDelay, int maxDelay) {
        return Math.max(minDelay, Math.min(maxDelay, currentDelay));
    }

    public double effectiveSpeedMultiplier(SpawnerData data) {
        return data.effectiveSpeedMultiplier(
            BASE_MIN_DELAY,
            BASE_MAX_DELAY,
            plugin.getConfigManager().spawnerMaxSugar,
            plugin.getConfigManager().spawnerMaxMultiplier,
            plugin.getConfigManager().spawnerMinDelayFloor
        );
    }

    public void spawnHologram(SpawnerData data) {
        World world = Bukkit.getWorld(data.world());
        if (world == null) return;
        if (world.getBlockAt(data.x(), data.y(), data.z()).getType() != org.bukkit.Material.SPAWNER) {
            String blockKey = key(data.world(), data.x(), data.y(), data.z());
            removeStaleEntry(blockKey, data);
            return;
        }

        String blockKey = key(data.world(), data.x(), data.y(), data.z());
        UUID existingId = holograms.get(blockKey);
        if (existingId != null) {
            Entity existing = Bukkit.getEntity(existingId);
            if (existing instanceof TextDisplay display && display.isValid()) {
                refreshHologram(display, world, data);
                return;
            }
            holograms.remove(blockKey, existingId);
            if (existing != null) existing.remove();
        }

        Location pos = new Location(world, data.x() + 0.5, data.y() + 1.5, data.z() + 0.5);
        TextDisplay display = world.spawn(pos, TextDisplay.class, entity -> {
            entity.text(buildHologramText(world, data));
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setBillboard(Display.Billboard.CENTER);
            VisualRangeUtil.applyHologramRange(entity, plugin.getConfigManager().spawnerHologramViewRange);
            entity.setPersistent(false);
            entity.setSeeThrough(false);
            entity.setBackgroundColor(HOLOGRAM_BACKGROUND);
            entity.getPersistentDataContainer().set(keySpawnerHologram, PersistentDataType.BYTE, (byte) 1);
        });

        holograms.put(blockKey, display.getUniqueId());
    }

    public void updateHologram(Location loc) {
        SpawnerData data = getData(loc);
        if (data == null) return;
        if (loc.getBlock().getType() != org.bukkit.Material.SPAWNER) {
            unregister(loc);
            return;
        }

        String blockKey = key(loc);
        UUID uid = holograms.get(blockKey);
        if (uid == null) {
            spawnHologram(data);
            return;
        }

        Entity entity = Bukkit.getEntity(uid);
        if (entity instanceof TextDisplay td && td.isValid()) {
            World world = loc.getWorld();
            if (world != null) {
                refreshHologram(td, world, data);
            }
        } else {
            spawnHologram(data);
        }
    }

    public void queueHologramUpdate(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        scheduleHologramUpdate(key(loc));
    }

    private void refreshHologram(TextDisplay display, World world, SpawnerData data) {
        Component nextText = buildHologramText(world, data);
        if (!nextText.equals(display.text())) {
            display.text(nextText);
        }
        float nextRange = VisualRangeUtil.clampHologramViewRange(
            plugin.getConfigManager().spawnerHologramViewRange
        );
        if (Float.compare(display.getViewRange(), nextRange) != 0) {
            display.setViewRange(nextRange);
        }
        if (display.isSeeThrough()) {
            display.setSeeThrough(false);
        }
    }

    public void destroyHologram(String blockKey) {
        UUID uid = holograms.remove(blockKey);
        if (uid == null) return;
        Entity entity = Bukkit.getEntity(uid);
        if (entity != null) entity.remove();
    }

    public void destroyAllHolograms() {
        holograms.forEach((key, uid) -> {
            Entity e = Bukkit.getEntity(uid);
            if (e != null) e.remove();
        });
        holograms.clear();
    }

    public void onChunkLoad(Chunk chunk) {
        String idxKey = chunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        Set<String> blockKeys = chunkIndex.get(idxKey);
        if (blockKeys == null || blockKeys.isEmpty()) return;

        for (String blockKey : blockKeys) {
            SpawnerData data = cache.get(blockKey);
            if (data == null) continue;
            Location loc = new Location(chunk.getWorld(), data.x(), data.y(), data.z());
            if (loc.getBlock().getType() != org.bukkit.Material.SPAWNER) {
                removeStaleEntry(blockKey, data);
                continue;
            }
            applySpeedToBlock(loc, data);
            spawnHologram(data);
        }
    }

    public void onChunkUnload(Chunk chunk) {
        String idxKey = chunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        Set<String> blockKeys = chunkIndex.get(idxKey);
        if (blockKeys == null || blockKeys.isEmpty()) return;
        for (String blockKey : blockKeys) {
            destroyHologram(blockKey);
        }
    }

    private Component buildHologramText(World world, SpawnerData data) {
        int maxSugar = plugin.getConfigManager().spawnerMaxSugar;
        double mult = effectiveSpeedMultiplier(data);

        String mobName = formatMobName(data.entityType());
        String stackSuffix = data.stackCount() > 1 ? " <white>x" + data.stackCount() + "</white>" : "";
        boolean redstonePowered = false;
        boolean spawnerRunning = true;
        if (data.redstoneControlled()) {
            Block spawnerBlock = world.getBlockAt(data.x(), data.y(), data.z());
            redstonePowered = isPowered(spawnerBlock);
            boolean disableWhenPowered = plugin.getConfigManager().spawnerRedstoneDisables;
            spawnerRunning = redstonePowered != disableWhenPowered;
        }

        String line1 = "<gold><bold>" + mobName + "</bold></gold>" + stackSuffix;
        String line2 = "<aqua>Speed:</aqua> <white>" + String.format(Locale.US, "%.1f", mult) + "x</white>"
            + " <dark_gray>(" + data.sugarCount() + "/" + maxSugar + " sugar)</dark_gray>";
        String line3 = (data.redstoneControlled()
            ? (spawnerRunning ? "<green>Redstone: ON</green>" : "<red>Redstone: OFF</red>")
                + " <dark_gray>(" + (redstonePowered ? "powered" : "unpowered") + ")</dark_gray>"
            : "<dark_gray>Redstone: OFF</dark_gray>")
            + "  "
            + (data.aiNerfed()
            ? "<light_purple>AI Nerf: ON</light_purple>"
            : "<dark_gray>AI Nerf: OFF</dark_gray>");

        return MM.deserialize(line1 + "\n" + line2 + "\n" + line3);
    }

    private static boolean isPowered(Block block) {
        return block.isBlockPowered() || block.isBlockIndirectlyPowered() || block.getBlockPower() > 0;
    }

    private static void writeDataToSpawnerState(CreatureSpawner cs, SpawnerData data) {
        PersistentDataContainer pdc = cs.getPersistentDataContainer();
        pdc.set(SpawnerListener.STACK_COUNT_KEY, PersistentDataType.INTEGER, data.stackCount());
        pdc.set(SpawnerListener.SUGAR_COUNT_KEY, PersistentDataType.INTEGER, data.sugarCount());
        pdc.set(
            SpawnerListener.REDSTONE_CONTROLLED_KEY,
            PersistentDataType.BYTE,
            data.redstoneControlled() ? (byte) 1 : (byte) 0
        );
        pdc.set(
            SpawnerListener.AI_NERFED_KEY,
            PersistentDataType.BYTE,
            data.aiNerfed() ? (byte) 1 : (byte) 0
        );
    }

    private static boolean spawnerStateMatches(CreatureSpawner cs, SpawnerData data) {
        PersistentDataContainer pdc = cs.getPersistentDataContainer();
        Integer stackCount = pdc.get(SpawnerListener.STACK_COUNT_KEY, PersistentDataType.INTEGER);
        Integer sugarCount = pdc.get(SpawnerListener.SUGAR_COUNT_KEY, PersistentDataType.INTEGER);
        Byte redstoneControlled = pdc.get(SpawnerListener.REDSTONE_CONTROLLED_KEY, PersistentDataType.BYTE);
        Byte aiNerfed = pdc.get(SpawnerListener.AI_NERFED_KEY, PersistentDataType.BYTE);
        return stackCount != null && stackCount == data.stackCount()
            && sugarCount != null && sugarCount == data.sugarCount()
            && redstoneControlled != null && (redstoneControlled == (byte) 1) == data.redstoneControlled()
            && aiNerfed != null && (aiNerfed == (byte) 1) == data.aiNerfed();
    }

    private String formatMobName(String entityType) {
        String[] parts = entityType.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0)));
            sb.append(p.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private void persistAndUpdateHologram(Location loc, SpawnerData data) {
        String blockKey = key(loc);
        queueSave(blockKey, data);
        scheduleHologramUpdate(blockKey);
    }

    private void scheduleHologramUpdate(String blockKey) {
        if (!plugin.isEnabled() || !pendingHologramUpdates.add(blockKey)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingHologramUpdates.remove(blockKey);
            if (!plugin.isEnabled()) return;
            SpawnerData current = cache.get(blockKey);
            if (current == null) return;
            World world = Bukkit.getWorld(current.world());
            if (world == null || !world.isChunkLoaded(current.x() >> 4, current.z() >> 4)) return;
            updateHologram(new Location(world, current.x(), current.y(), current.z()));
        });
    }

    private static String chunkKey(String world, int chunkX, int chunkZ) {
        return world + ":" + chunkX + ":" + chunkZ;
    }

    private void index(String blockKey, String world, int blockX, int blockZ) {
        String idxKey = chunkKey(world, blockX >> 4, blockZ >> 4);
        chunkIndex.computeIfAbsent(idxKey, ignored -> ConcurrentHashMap.newKeySet()).add(blockKey);
    }

    private void unindex(String blockKey, String world, int blockX, int blockZ) {
        String idxKey = chunkKey(world, blockX >> 4, blockZ >> 4);
        Set<String> keys = chunkIndex.get(idxKey);
        if (keys == null) return;
        keys.remove(blockKey);
        if (keys.isEmpty()) chunkIndex.remove(idxKey);
    }

    private void removeStaleEntry(String blockKey, SpawnerData data) {
        cache.remove(blockKey);
        unindex(blockKey, data.world(), data.x(), data.z());
        destroyHologram(blockKey);
        queueDelete(blockKey, data.world(), data.x(), data.y(), data.z());
    }

    public static String normalizeEntityType(String entityType) {
        if (entityType == null || entityType.isBlank()) return EntityType.PIG.name();
        try {
            EntityType type = EntityType.valueOf(entityType.trim().toUpperCase(Locale.ROOT));
            return type.isSpawnable() && type.isAlive() ? type.name() : EntityType.PIG.name();
        } catch (IllegalArgumentException ignored) {
            return EntityType.PIG.name();
        }
    }

    public static int clampStackCount(int stackCount, int maxStack) {
        return Math.max(1, Math.min(stackCount, Math.max(1, maxStack)));
    }

    public static int clampSugarCount(int sugarCount, int maxSugar) {
        return Math.max(0, Math.min(sugarCount, Math.max(0, maxSugar)));
    }

    public static int breakExperience(int stackCount, int maxStack) {
        long normalized = clampStackCount(stackCount, maxStack);
        return (int) Math.min(Integer.MAX_VALUE, 15L + (normalized - 1L) * 5L);
    }

    private SpawnerData normalizeLoadedData(SpawnerData raw) {
        return new SpawnerData(
            raw.world(), raw.x(), raw.y(), raw.z(),
            normalizeEntityType(raw.entityType()),
            clampStackCount(raw.stackCount(), plugin.getConfigManager().spawnerMaxStack),
            clampSugarCount(raw.sugarCount(), plugin.getConfigManager().spawnerMaxSugar),
            raw.redstoneControlled(), raw.aiNerfed()
        );
    }

    private static boolean sameStoredState(SpawnerData left, SpawnerData right) {
        return left.entityType().equals(right.entityType())
            && left.stackCount() == right.stackCount()
            && left.sugarCount() == right.sugarCount()
            && left.redstoneControlled() == right.redstoneControlled()
            && left.aiNerfed() == right.aiNerfed();
    }

    private CompletableFuture<Void> queueSave(String blockKey, SpawnerData data) {
        SpawnerData snapshot = data.snapshot();
        return queuePersistence(blockKey, () -> plugin.getDatabase().saveSpawner(snapshot));
    }

    private CompletableFuture<Void> queueDelete(String blockKey, String world, int x, int y, int z) {
        return queuePersistence(blockKey, () -> plugin.getDatabase().deleteSpawner(world, x, y, z));
    }

    private CompletableFuture<Void> queuePersistence(
        String blockKey,
        Supplier<CompletableFuture<Void>> operation
    ) {
        CompletableFuture<Void> queued = pendingPersistence.compute(blockKey, (ignored, previous) -> {
            CompletableFuture<Void> ordered = previous == null
                ? CompletableFuture.completedFuture(null)
                : previous.handle((result, failure) -> null);
            return ordered.thenCompose(result -> operation.get());
        });
        queued.whenComplete((result, failure) -> {
            pendingPersistence.remove(blockKey, queued);
            if (failure != null) {
                plugin.getLogger().severe(
                    "Failed to persist spawner " + blockKey + ": " + failure.getMessage()
                );
            }
        });
        return queued;
    }

    record SpawnerBlockSettings(int minDelay, int maxDelay, int spawnCount, int maxNearbyEntities) {
    }
}
