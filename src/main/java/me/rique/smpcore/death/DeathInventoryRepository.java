package me.rique.smpcore.death;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.AtomicYamlFile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class DeathInventoryRepository {

    private final SMPCore plugin;
    private final File root;

    DeathInventoryRepository(SMPCore plugin) {
        this.plugin = plugin;
        this.root = new File(plugin.getDataFolder(), "death-inventories");
        try {
            Files.createDirectories(root.toPath());
            plugin.getLogger().info("Death inventory records: plugins/SMPCore/death-inventories");
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not create the death inventory record folder: " + ex.getMessage());
        }
    }

    YamlConfiguration baseSnapshot(Player player, UUID id, String kind, String state, long createdAt) {
        YamlConfiguration data = new YamlConfiguration();
        data.set("schema-version", DeathInventoryCodec.SCHEMA_VERSION);
        data.set("snapshot.id", id.toString());
        data.set("snapshot.kind", kind);
        data.set("snapshot.state", state);
        data.set("snapshot.created-at-epoch-ms", createdAt);
        data.set("snapshot.created-at-utc", Instant.ofEpochMilli(createdAt).toString());
        data.set("player.uuid", player.getUniqueId().toString());
        data.set("player.name", player.getName());
        data.set("server.minecraft-version", Bukkit.getMinecraftVersion());
        data.set("server.plugin-version", plugin.getPluginMeta().getVersion());
        return data;
    }

    void save(YamlConfiguration data, File file) throws IOException {
        AtomicYamlFile.save(data, file);
    }

    SnapshotLookup findDeathSnapshot(UUID playerId, String rawSelector) {
        List<SnapshotHandle> snapshots = loadDeathSnapshots(playerId);
        if (snapshots.isEmpty()) {
            return new SnapshotLookup(null, "No death inventory records exist for that player.");
        }
        String selector = rawSelector == null || rawSelector.isBlank() ? "latest" : rawSelector.trim();
        if (selector.equalsIgnoreCase("latest")) {
            return new SnapshotLookup(snapshots.getFirst(), null);
        }

        List<SnapshotHandle> matches = snapshots.stream()
            .filter(snapshot -> DeathInventoryPolicy.selectorMatches(snapshot.id(), selector))
            .toList();
        if (matches.isEmpty()) {
            return new SnapshotLookup(null, "No death inventory matches that ID. Use /deathinventory list first.");
        }
        if (matches.size() > 1) {
            return new SnapshotLookup(null, "That short ID matches multiple records. Enter more of the ID.");
        }
        return new SnapshotLookup(matches.getFirst(), null);
    }

    List<SnapshotHandle> loadDeathSnapshots(UUID playerId) {
        File directory = playerDirectory(playerId);
        File[] files = directory.listFiles((ignored, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            return List.of();
        }

        List<SnapshotHandle> snapshots = new ArrayList<>();
        for (File file : files) {
            try {
                SnapshotHandle snapshot = load(file);
                if (DeathInventoryPolicy.KIND_DEATH.equalsIgnoreCase(snapshot.kind())
                    && playerId.toString().equals(snapshot.data().getString("player.uuid"))) {
                    snapshots.add(snapshot);
                }
            } catch (IOException ex) {
                plugin.getLogger().warning("Skipping unreadable death inventory file " + file.getName() + ": " + ex.getMessage());
            }
        }
        snapshots.sort(Comparator.comparingLong(SnapshotHandle::createdAt).reversed());
        return snapshots;
    }

    SnapshotHandle load(File file) throws IOException {
        if (file == null || !file.isFile() || file.length() <= 0) {
            throw new IOException("Snapshot file is missing or empty.");
        }
        YamlConfiguration data = new YamlConfiguration();
        try {
            data.load(file);
        } catch (InvalidConfigurationException ex) {
            throw new IOException("Snapshot YAML is malformed.", ex);
        }
        if (data.getInt("schema-version", -1) != DeathInventoryCodec.SCHEMA_VERSION) {
            throw new IOException("Unsupported snapshot schema.");
        }
        String id = data.getString("snapshot.id");
        String kind = data.getString("snapshot.kind");
        String state = data.getString("snapshot.state");
        long createdAt = data.getLong("snapshot.created-at-epoch-ms", -1L);
        if (id == null || kind == null || state == null || createdAt <= 0L) {
            throw new IOException("Snapshot header is incomplete.");
        }
        return new SnapshotHandle(
            file,
            data,
            id,
            kind,
            state,
            createdAt,
            data.getString("snapshot.created-at-utc", Instant.ofEpochMilli(createdAt).toString()),
            data.getString("death.bukkit-cause", "UNKNOWN"),
            data.getInt("inventory.stack-count"),
            data.getInt("inventory.item-count")
        );
    }

    ResolvedPlayer resolvePlayer(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();
        try {
            UUID uuid = UUID.fromString(trimmed);
            OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
            String name = offline.getName() == null ? uuid.toString() : offline.getName();
            return new ResolvedPlayer(uuid, name);
        } catch (IllegalArgumentException ignored) {
            // Player name path.
        }

        Player online = Bukkit.getPlayerExact(trimmed);
        if (online == null) {
            online = Bukkit.getPlayer(trimmed);
        }
        if (online != null) {
            return new ResolvedPlayer(online.getUniqueId(), online.getName());
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(trimmed);
        if (!offline.hasPlayedBefore() && !playerDirectory(offline.getUniqueId()).isDirectory()) {
            return null;
        }
        return new ResolvedPlayer(offline.getUniqueId(), offline.getName() == null ? trimmed : offline.getName());
    }

    File playerDirectory(UUID playerId) {
        return new File(root, playerId.toString());
    }

    File safeSibling(File snapshot, String fileName) throws IOException {
        if (snapshot == null || fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
            return null;
        }
        File parent = snapshot.getCanonicalFile().getParentFile();
        File sibling = new File(parent, fileName).getCanonicalFile();
        return sibling.getParentFile().equals(parent) ? sibling : null;
    }

    String relativePath(File file) {
        try {
            return plugin.getDataFolder().toPath().toAbsolutePath().normalize()
                .relativize(file.toPath().toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
        } catch (IllegalArgumentException ex) {
            return file.getName();
        }
    }

    static void writeLocation(YamlConfiguration data, String path, Location location) {
        if (location == null || location.getWorld() == null) {
            data.set(path + ".present", false);
            return;
        }
        data.set(path + ".present", true);
        data.set(path + ".world-uuid", location.getWorld().getUID().toString());
        data.set(path + ".world-name", location.getWorld().getName());
        data.set(path + ".x", location.getX());
        data.set(path + ".y", location.getY());
        data.set(path + ".z", location.getZ());
        data.set(path + ".block-x", location.getBlockX());
        data.set(path + ".block-y", location.getBlockY());
        data.set(path + ".block-z", location.getBlockZ());
        data.set(path + ".yaw", location.getYaw());
        data.set(path + ".pitch", location.getPitch());
    }

    static void appendAudit(YamlConfiguration data, String action, String actor, String detail) {
        List<Map<?, ?>> existing = data.getMapList("audit");
        List<Map<String, Object>> audit = new ArrayList<>();
        for (Map<?, ?> entry : existing) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> value : entry.entrySet()) {
                copy.put(String.valueOf(value.getKey()), value.getValue());
            }
            audit.add(copy);
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("at-utc", Instant.now().toString());
        entry.put("at-epoch-ms", System.currentTimeMillis());
        entry.put("action", action);
        entry.put("actor", actor);
        entry.put("detail", detail == null ? "" : detail);
        audit.add(entry);
        data.set("audit", audit);
    }

    record SnapshotHandle(
        File file,
        YamlConfiguration data,
        String id,
        String kind,
        String state,
        long createdAt,
        String createdAtUtc,
        String cause,
        int stackCount,
        int itemCount
    ) {
    }

    record SnapshotLookup(SnapshotHandle snapshot, String error) {
    }

    record ResolvedPlayer(UUID uuid, String name) {
    }
}
