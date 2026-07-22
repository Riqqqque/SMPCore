package me.rique.smpcore.backpack;

import me.rique.smpcore.SMPCore;
import org.bukkit.inventory.ItemStack;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

final class BackpackRecoveryJournal {

    private static final int MAGIC = 0x534D5042;
    private static final int VERSION = 1;
    private static final int MAX_ITEM_BYTES = 32 * 1024 * 1024;
    private static final int MAX_SNAPSHOTS_PER_BACKPACK = 5;
    private static final int MAX_SNAPSHOTS_PER_PLAYER = 100;
    private static final long MAX_SNAPSHOT_AGE_MILLIS = Duration.ofDays(14).toMillis();

    private final SMPCore plugin;
    private final Path directory;
    private final Path historyDirectory;
    private final Map<UUID, byte[]> lastJournalDigests = new HashMap<>();

    BackpackRecoveryJournal(SMPCore plugin) {
        this.plugin = plugin;
        this.directory = plugin.getDataFolder().toPath().resolve("backpack-recovery");
        this.historyDirectory = plugin.getDataFolder().toPath().resolve("backpack-history");
    }

    synchronized boolean write(
        UUID playerId,
        String backpackId,
        String sessionToken,
        int sourceSlot,
        ItemStack recoveredBackpack
    ) {
        if (playerId == null || backpackId == null || backpackId.isBlank()
            || sessionToken == null || sessionToken.isBlank() || recoveredBackpack == null) {
            return false;
        }

        byte[] itemBytes = serialize(recoveredBackpack, playerId);
        if (itemBytes == null) {
            return false;
        }
        byte[] digest = sha256(itemBytes);
        Path target = path(playerId);
        if (Files.isRegularFile(target) && !contentChanged(lastJournalDigests.get(playerId), digest)) {
            return true;
        }

        try {
            writeFile(
                target,
                playerId,
                backpackId,
                sessionToken,
                sourceSlot,
                System.currentTimeMillis(),
                itemBytes
            );
            lastJournalDigests.put(playerId, digest);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save backpack recovery data for " + playerId + ": " + ex.getMessage());
            return false;
        }
    }

    synchronized Recovery read(UUID playerId) {
        Path file = path(playerId);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        return readFile(file, playerId, true);
    }

    synchronized boolean exists(UUID playerId) {
        return Files.isRegularFile(path(playerId));
    }

    synchronized boolean delete(UUID playerId) {
        Path source = path(playerId);
        try {
            if (!Files.exists(source)) {
                lastJournalDigests.remove(playerId);
                return true;
            }
            Recovery recovery = readFile(source, playerId, false);
            if (recovery == null) {
                throw new IOException("active recovery journal failed validation");
            }
            migrateLegacyHistory(playerId);
            Path targetDirectory = snapshotDirectory(playerId, recovery.backpackId());
            Files.createDirectories(targetDirectory);
            moveAtomically(source, targetDirectory.resolve(snapshotFileName(recovery)));
            lastJournalDigests.remove(playerId);
            prunePlayerHistory(playerId, System.currentTimeMillis());
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not archive backpack recovery data for " + playerId + ": " + ex.getMessage());
            return false;
        }
    }

    synchronized List<Snapshot> listSnapshots(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        try {
            migrateLegacyHistory(playerId);
            prunePlayerHistory(playerId, System.currentTimeMillis());
            Path playerDirectory = historyDirectory.resolve(playerId.toString());
            if (!Files.isDirectory(playerDirectory)) {
                return List.of();
            }
            List<Snapshot> snapshots = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(playerDirectory, 3)) {
                for (Path file : paths.filter(Files::isRegularFile).filter(BackpackRecoveryJournal::isSnapshotFile).toList()) {
                    Recovery recovery = readFile(file, playerId, false);
                    if (recovery != null) {
                        snapshots.add(new Snapshot(snapshotId(recovery), file, recovery));
                    }
                }
            }
            snapshots.sort(Comparator.comparingLong((Snapshot snapshot) -> snapshot.recovery().savedAt()).reversed());
            return List.copyOf(snapshots);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not list backpack recovery snapshots for " + playerId + ": " + ex.getMessage());
            return List.of();
        }
    }

    synchronized SnapshotLookup findSnapshot(UUID playerId, String selector) {
        List<Snapshot> snapshots = listSnapshots(playerId);
        if (snapshots.isEmpty()) {
            return new SnapshotLookup(null, "No backpack recovery snapshots exist for this player.");
        }
        String normalized = selector == null ? "latest" : selector.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || "latest".equals(normalized)) {
            return new SnapshotLookup(snapshots.getFirst(), null);
        }
        List<Snapshot> matches = snapshots.stream()
            .filter(snapshot -> selectorMatches(snapshot.id(), normalized))
            .toList();
        if (matches.isEmpty()) {
            return new SnapshotLookup(null, "No backpack snapshot matches that ID. Use /backpackadmin list first.");
        }
        if (matches.size() > 1) {
            return new SnapshotLookup(null, "That snapshot ID is ambiguous. Enter more of the ID.");
        }
        return new SnapshotLookup(matches.getFirst(), null);
    }

    synchronized boolean archiveStandalone(UUID playerId, String backpackId, ItemStack backpack) {
        if (playerId == null || backpackId == null || backpackId.isBlank() || backpack == null) {
            return false;
        }
        byte[] itemBytes = serialize(backpack, playerId);
        if (itemBytes == null) {
            return false;
        }
        long savedAt = System.currentTimeMillis();
        String token = "pre-restore-" + UUID.randomUUID();
        try {
            Path targetDirectory = snapshotDirectory(playerId, backpackId);
            Files.createDirectories(targetDirectory);
            Recovery recovery = new Recovery(playerId, backpackId, token, -1, savedAt, backpack.clone());
            writeFile(
                targetDirectory.resolve(snapshotFileName(recovery)),
                playerId,
                backpackId,
                token,
                -1,
                savedAt,
                itemBytes
            );
            prunePlayerHistory(playerId, savedAt);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save the pre-restore backpack snapshot for " + playerId + ": " + ex.getMessage());
            return false;
        }
    }

    synchronized boolean consumeSnapshot(Snapshot snapshot) {
        if (snapshot == null || snapshot.file() == null) {
            return false;
        }
        try {
            return Files.deleteIfExists(snapshot.file());
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not consume restored backpack snapshot " + snapshot.id() + ": " + ex.getMessage());
            return false;
        }
    }

    private void migrateLegacyHistory(UUID playerId) throws IOException {
        Path legacy = historyDirectory.resolve(playerId + ".dat");
        if (!Files.isRegularFile(legacy)) {
            return;
        }
        Recovery recovery = readFile(legacy, playerId, false);
        if (recovery == null) {
            return;
        }
        Path targetDirectory = snapshotDirectory(playerId, recovery.backpackId());
        Files.createDirectories(targetDirectory);
        moveAtomically(legacy, targetDirectory.resolve(snapshotFileName(recovery)));
    }

    private void prunePlayerHistory(UUID playerId, long now) throws IOException {
        Path playerDirectory = historyDirectory.resolve(playerId.toString());
        if (!Files.isDirectory(playerDirectory)) {
            return;
        }
        List<Path> all;
        try (Stream<Path> paths = Files.walk(playerDirectory, 3)) {
            all = new ArrayList<>(paths.filter(Files::isRegularFile).filter(BackpackRecoveryJournal::isSnapshotFile).toList());
        }
        Map<Path, List<Path>> byBackpack = new HashMap<>();
        for (Path file : all) {
            byBackpack.computeIfAbsent(file.getParent(), ignored -> new ArrayList<>()).add(file);
        }
        for (List<Path> files : byBackpack.values()) {
            files.sort(newestFirst());
            for (int index = 0; index < files.size(); index++) {
                Path file = files.get(index);
                long modified = Files.getLastModifiedTime(file).toMillis();
                if (!shouldRetainSnapshot(index, 0, modified, now)) {
                    Files.deleteIfExists(file);
                }
            }
        }

        List<Path> remaining;
        try (Stream<Path> paths = Files.walk(playerDirectory, 3)) {
            remaining = new ArrayList<>(paths.filter(Files::isRegularFile).filter(BackpackRecoveryJournal::isSnapshotFile).toList());
        }
        remaining.sort(newestFirst());
        for (int index = MAX_SNAPSHOTS_PER_PLAYER; index < remaining.size(); index++) {
            Files.deleteIfExists(remaining.get(index));
        }
    }

    private Recovery readFile(Path file, UUID expectedPlayer, boolean logFailure) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) {
                throw new IOException("unsupported journal header");
            }
            UUID storedPlayer = new UUID(in.readLong(), in.readLong());
            if (!storedPlayer.equals(expectedPlayer)) {
                throw new IOException("journal owner mismatch");
            }
            String backpackId = in.readUTF();
            String sessionToken = in.readUTF();
            int sourceSlot = in.readInt();
            long savedAt = in.readLong();
            int length = in.readInt();
            if (length <= 0 || length > MAX_ITEM_BYTES) {
                throw new IOException("journal item length outside the safe range");
            }
            byte[] itemBytes = in.readNBytes(length);
            if (itemBytes.length != length || in.read() != -1) {
                throw new IOException("truncated or trailing journal data");
            }
            ItemStack backpack = ItemStack.deserializeBytes(itemBytes);
            return new Recovery(storedPlayer, backpackId, sessionToken, sourceSlot, savedAt, backpack);
        } catch (Exception ex) {
            if (logFailure) {
                plugin.getLogger().severe("Could not read backpack recovery data for " + expectedPlayer + ": " + ex.getMessage());
            } else {
                plugin.getLogger().warning("Skipped invalid backpack snapshot " + file.getFileName() + ": " + ex.getMessage());
            }
            return null;
        }
    }

    private void writeFile(
        Path target,
        UUID playerId,
        String backpackId,
        String sessionToken,
        int sourceSlot,
        long savedAt,
        byte[] itemBytes
    ) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString() + ".", ".tmp");
        try {
            try (FileOutputStream file = new FileOutputStream(temporary.toFile());
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(file))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeLong(playerId.getMostSignificantBits());
                out.writeLong(playerId.getLeastSignificantBits());
                out.writeUTF(backpackId);
                out.writeUTF(sessionToken);
                out.writeInt(sourceSlot);
                out.writeLong(savedAt);
                out.writeInt(itemBytes.length);
                out.write(itemBytes);
                out.flush();
                file.getFD().sync();
            }
            moveAtomically(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private byte[] serialize(ItemStack backpack, UUID playerId) {
        byte[] itemBytes;
        try {
            itemBytes = backpack.serializeAsBytes();
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Could not serialize backpack recovery data for " + playerId + ": " + ex.getMessage());
            return null;
        }
        if (itemBytes.length <= 0 || itemBytes.length > MAX_ITEM_BYTES) {
            plugin.getLogger().severe("Backpack recovery data for " + playerId + " was outside the safe size range.");
            return null;
        }
        return itemBytes;
    }

    private Path path(UUID playerId) {
        return directory.resolve(playerId + ".dat");
    }

    private Path snapshotDirectory(UUID playerId, String backpackId) {
        return historyDirectory.resolve(playerId.toString()).resolve(storageKey(backpackId));
    }

    private static String snapshotFileName(Recovery recovery) {
        return recovery.savedAt() + "-" + safeToken(recovery.sessionToken()) + ".dat";
    }

    private static String snapshotId(Recovery recovery) {
        String token = safeToken(recovery.sessionToken());
        String shortToken = token.substring(0, Math.min(8, token.length()));
        return Long.toString(recovery.savedAt(), 36) + "-" + shortToken;
    }

    private static String storageKey(String backpackId) {
        try {
            return UUID.fromString(backpackId).toString();
        } catch (IllegalArgumentException ignored) {
            return java.util.HexFormat.of().formatHex(sha256(backpackId.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
    }

    private static String safeToken(String token) {
        return token == null ? "unknown" : token.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static Comparator<Path> newestFirst() {
        return Comparator.comparingLong(BackpackRecoveryJournal::lastModified).reversed();
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private static boolean isSnapshotFile(Path path) {
        return path.getFileName().toString().endsWith(".dat");
    }

    static boolean shouldRetainSnapshot(int backpackIndex, int playerIndex, long modifiedAt, long now) {
        return backpackIndex < MAX_SNAPSHOTS_PER_BACKPACK
            && playerIndex < MAX_SNAPSHOTS_PER_PLAYER
            && modifiedAt >= now - MAX_SNAPSHOT_AGE_MILLIS;
    }

    static boolean contentChanged(byte[] previousDigest, byte[] currentDigest) {
        return previousDigest == null || currentDigest == null || !MessageDigest.isEqual(previousDigest, currentDigest);
    }

    static boolean selectorMatches(String snapshotId, String selector) {
        return snapshotId != null && selector != null
            && snapshotId.toLowerCase(Locale.ROOT).startsWith(selector.trim().toLowerCase(Locale.ROOT));
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    record Recovery(
        UUID playerId,
        String backpackId,
        String sessionToken,
        int sourceSlot,
        long savedAt,
        ItemStack backpack
    ) { }

    record Snapshot(String id, Path file, Recovery recovery) { }

    record SnapshotLookup(Snapshot snapshot, String error) { }
}
