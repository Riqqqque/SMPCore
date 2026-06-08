package me.rique.smpcore.spawner;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.VisualRangeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for custom spawner data, persistence, and holograms.
 */
public final class SpawnerManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int BASE_MIN_DELAY = 200;
    private static final int BASE_MAX_DELAY = 800;
    private static final int VANILLA_SPAWN_COUNT = 4;

    public enum MergeResult {
        SUCCESS,
        NOT_TRACKED,
        TYPE_MISMATCH,
        WOULD_EXCEED_MAX
    }

    private final SMPCore plugin;
    private final Map<String, SpawnerData> cache = new ConcurrentHashMap<>();
    private final Map<String, UUID> holograms = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> chunkIndex = new ConcurrentHashMap<>();

    public SpawnerManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public static String key(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    public static String key(Location loc) {
        return key(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public void loadAll() {
        plugin.getDatabase().loadAllSpawners()
            .thenAccept(list -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!plugin.isEnabled()) return;
                for (SpawnerData data : list) {
                    String blockKey = key(data.world(), data.x(), data.y(), data.z());
                    cache.put(blockKey, data);
                    index(blockKey, data.world(), data.x(), data.z());

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
            }))
            .exceptionally(ex -> {
                plugin.getLogger().severe("Failed to load spawners: " + ex.getMessage());
                return null;
            });
    }

    public void shutdown() {
        destroyAllHolograms();
        List<CompletableFuture<Void>> saves = new ArrayList<>();
        cache.values().stream()
            .filter(SpawnerData::isDirty)
            .forEach(d -> saves.add(plugin.getDatabase().saveSpawner(d)));
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
        String normalizedType = entityType == null ? "PIG" : entityType.toUpperCase(Locale.ROOT);
        SpawnerData data = new SpawnerData(
            loc.getWorld().getName(),
            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
            normalizedType, clampedStack, clampedSugar, redstoneControlled, aiNerfed
        );
        String blockKey = key(loc);
        cache.put(blockKey, data);
        index(blockKey, data.world(), data.x(), data.z());
        plugin.getDatabase().saveSpawner(data);
        applySpeedToBlock(loc, data);
        Bukkit.getScheduler().runTask(plugin, () -> spawnHologram(data));
    }

    public void unregister(Location loc) {
        String blockKey = key(loc);
        SpawnerData removed = cache.remove(blockKey);
        if (removed != null) {
            unindex(blockKey, removed.world(), removed.x(), removed.z());
        }
        destroyHologram(blockKey);
        plugin.getDatabase().deleteSpawner(
            loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
        );
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
        String normalizedType = entityType == null ? "PIG" : entityType.toUpperCase(Locale.ROOT);
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
        if (!data.entityType().equalsIgnoreCase(entityType)) return MergeResult.TYPE_MISMATCH;
        int maxStack = plugin.getConfigManager().spawnerMaxStack;
        if (data.stackCount() + addCount > maxStack) return MergeResult.WOULD_EXCEED_MAX;
        data.setStackCount(data.stackCount() + addCount);
        applySpeedToBlock(loc, data);
        persistAndUpdateHologram(loc, data);
        return MergeResult.SUCCESS;
    }

    /** Push speed modifiers into the actual CreatureSpawner block state. */
    public void applySpeedToBlock(Location loc, SpawnerData data) {
        if (!(loc.getBlock().getState() instanceof CreatureSpawner cs)) return;
        int maxSugar = plugin.getConfigManager().spawnerMaxSugar;
        double maxMult = plugin.getConfigManager().spawnerMaxMultiplier;
        int minDelay = Math.max(
            plugin.getConfigManager().spawnerMinDelayFloor,
            data.adjustedDelay(BASE_MIN_DELAY, maxSugar, maxMult)
        );
        int maxDelay = Math.max(minDelay, data.adjustedDelay(BASE_MAX_DELAY, maxSugar, maxMult));
        int scaledSpawnCount = Math.max(
            VANILLA_SPAWN_COUNT,
            (int) Math.round(VANILLA_SPAWN_COUNT * Math.sqrt(Math.max(1, data.stackCount())))
        );
        scaledSpawnCount = Math.min(plugin.getConfigManager().spawnerStackSpawnCountCap, scaledSpawnCount);
        int maxNearbyEntities = Math.max(
            16,
            Math.min(plugin.getConfigManager().spawnerMaxNearbyEntitiesCap, scaledSpawnCount * 4)
        );
        cs.setMinSpawnDelay(minDelay);
        cs.setMaxSpawnDelay(maxDelay);
        cs.setSpawnCount(scaledSpawnCount);
        cs.setMaxNearbyEntities(maxNearbyEntities);
        cs.setDelay(minDelay);
        writeDataToSpawnerState(cs, data);
        cs.update(true, false);
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
        destroyHologram(blockKey);

        Location pos = new Location(world, data.x() + 0.5, data.y() + 1.5, data.z() + 0.5);
        TextDisplay display = world.spawn(pos, TextDisplay.class, entity -> {
            entity.text(buildHologramText(world, data));
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setViewRange(VisualRangeUtil.clampHologramViewRange(plugin.getConfigManager().spawnerHologramViewRange));
            entity.setPersistent(false);
            entity.setSeeThrough(false);
            entity.setBackgroundColor(Color.fromARGB(160, 0, 0, 0));
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
        if (entity instanceof TextDisplay td) {
            World world = loc.getWorld();
            if (world != null) {
                td.text(buildHologramText(world, data));
            }
        } else {
            spawnHologram(data);
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
        double maxMult = plugin.getConfigManager().spawnerMaxMultiplier;
        double mult = data.speedMultiplier(maxSugar, maxMult);

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
        plugin.getDatabase().saveSpawner(data);
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled()) return;
            updateHologram(loc);
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
        plugin.getDatabase().deleteSpawner(data.world(), data.x(), data.y(), data.z())
            .exceptionally(ex -> {
                plugin.getLogger().severe("Failed to delete stale spawner " + blockKey + ": " + ex.getMessage());
                return null;
            });
    }
}
