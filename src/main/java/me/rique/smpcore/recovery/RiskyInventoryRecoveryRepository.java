package me.rique.smpcore.recovery;

import me.rique.smpcore.SMPCore;

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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

final class RiskyInventoryRecoveryRepository {

    static final byte AVAILABLE = 0;
    static final byte RESTORING = 1;
    static final byte CONSUMED = 2;
    static final int MAX_SNAPSHOTS_PER_PLAYER = 50;
    static final long MAX_AGE_MILLIS = Duration.ofDays(14).toMillis();
    private static final long MAX_PLAYER_BYTES = 128L * 1024L * 1024L;
    private static final int MAX_ITEM_BYTES = 2 * 1024 * 1024;
    private static final int MAX_SNAPSHOT_BYTES = 10 * 1024 * 1024;
    private static final long DUPLICATE_TRANSACTION_WINDOW_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final int MAGIC = 0x534D5052;
    private static final int VERSION = 1;

    private final SMPCore plugin;
    private final Path directory;

    RiskyInventoryRecoveryRepository(SMPCore plugin) {
        this.plugin = plugin;
        this.directory = plugin.getDataFolder().toPath().resolve("inventory-recovery");
    }

    synchronized boolean save(SerializedSnapshot snapshot) {
        if (!valid(snapshot)) return false;
        Path playerDirectory = directory.resolve(snapshot.playerId().toString());
        Path target = playerDirectory.resolve(snapshot.createdAt() + "-" + snapshot.id() + ".dat");
        try {
            write(target, snapshot);
            prune(playerDirectory, System.currentTimeMillis());
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save risky inventory recovery snapshot for "
                + snapshot.playerName() + ": " + ex.getMessage());
            return false;
        }
    }

    synchronized List<SnapshotHandle> list(UUID playerId) {
        if (playerId == null) return List.of();
        Path playerDirectory = directory.resolve(playerId.toString());
        try {
            prune(playerDirectory, System.currentTimeMillis());
            if (!Files.isDirectory(playerDirectory)) return List.of();
            List<SnapshotHandle> snapshots = new ArrayList<>();
            try (Stream<Path> paths = Files.list(playerDirectory)) {
                for (Path file : paths.filter(Files::isRegularFile).filter(RiskyInventoryRecoveryRepository::isSnapshot).toList()) {
                    SerializedSnapshot snapshot = read(file);
                    if (snapshot != null && playerId.equals(snapshot.playerId())) {
                        snapshots.add(new SnapshotHandle(file, snapshot));
                    }
                }
            }
            snapshots.sort(Comparator.comparingLong((SnapshotHandle value) -> value.snapshot().createdAt()).reversed());
            return List.copyOf(snapshots);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not list risky inventory recovery snapshots for " + playerId + ": " + ex.getMessage());
            return List.of();
        }
    }

    synchronized SnapshotLookup find(UUID playerId, String selector) {
        List<SnapshotHandle> snapshots = list(playerId);
        if (snapshots.isEmpty()) return new SnapshotLookup(null, "No risky-inventory snapshots exist for this player.");
        String normalized = selector == null ? "latest" : selector.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || "latest".equals(normalized)) return new SnapshotLookup(snapshots.getFirst(), null);
        List<SnapshotHandle> matches = snapshots.stream()
            .filter(handle -> selectorMatches(shortId(handle.snapshot().id()), normalized)
                || selectorMatches(handle.snapshot().id().toString(), normalized))
            .toList();
        if (matches.isEmpty()) return new SnapshotLookup(null, "No snapshot matches that ID.");
        if (matches.size() > 1) return new SnapshotLookup(null, "That snapshot ID is ambiguous. Enter more characters.");
        return new SnapshotLookup(matches.getFirst(), null);
    }

    synchronized RestoreTransition beginRestore(UUID playerId, String selector, int itemIndex) {
        SnapshotLookup lookup = find(playerId, selector);
        if (lookup.handle() == null) return new RestoreTransition(null, lookup.error());
        SerializedSnapshot snapshot = lookup.handle().snapshot();
        int index = itemIndex - 1;
        if (index < 0 || index >= snapshot.items().size()) return new RestoreTransition(null, "That item number does not exist.");
        SerializedItem selected = snapshot.items().get(index);
        if (selected.state() == CONSUMED) return new RestoreTransition(null, "That recovery item was already restored.");
        if (selected.state() == RESTORING) return new RestoreTransition(null, "That recovery item is already being reconciled.");
        if (hasConsumedDuplicate(playerId, snapshot.createdAt(), selected.bytes())) {
            return new RestoreTransition(null, "An identical copy from the same transaction window was already restored.");
        }
        SerializedSnapshot updated = withState(snapshot, index, RESTORING);
        try {
            write(lookup.handle().file(), updated);
            return new RestoreTransition(new RestoreToken(lookup.handle().file(), updated, index), null);
        } catch (IOException ex) {
            return new RestoreTransition(null, "The recovery file could not be locked safely: " + ex.getMessage());
        }
    }

    synchronized boolean completeRestore(RestoreToken token) {
        if (!transition(token, RESTORING, CONSUMED)) return false;
        consumeTransactionDuplicates(token.snapshot().playerId(), token.snapshot().createdAt(),
            token.snapshot().items().get(token.itemIndex()).bytes());
        return true;
    }

    synchronized boolean rollbackRestore(RestoreToken token) {
        return transition(token, RESTORING, AVAILABLE);
    }

    synchronized List<RestoreToken> restoring(UUID playerId) {
        List<RestoreToken> restoring = new ArrayList<>();
        for (SnapshotHandle handle : list(playerId)) {
            List<SerializedItem> items = handle.snapshot().items();
            for (int index = 0; index < items.size(); index++) {
                if (items.get(index).state() == RESTORING) {
                    restoring.add(new RestoreToken(handle.file(), handle.snapshot(), index));
                }
            }
        }
        return List.copyOf(restoring);
    }

    synchronized boolean reconcileDeliveredMarker(UUID playerId, String marker) {
        MarkerParts parts = parseMarker(marker);
        if (playerId == null || parts == null) return false;
        SnapshotLookup lookup = find(playerId, parts.snapshotId().toString());
        if (lookup.handle() == null) return false;
        SerializedSnapshot snapshot = lookup.handle().snapshot();
        if (parts.itemIndex() < 0 || parts.itemIndex() >= snapshot.items().size()) return false;
        byte state = snapshot.items().get(parts.itemIndex()).state();
        if (state == CONSUMED) return true;
        if (state != AVAILABLE && state != RESTORING) return false;
        try {
            write(lookup.handle().file(), withState(snapshot, parts.itemIndex(), CONSUMED));
            consumeTransactionDuplicates(playerId, snapshot.createdAt(), snapshot.items().get(parts.itemIndex()).bytes());
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not reconcile delivered inventory recovery " + marker + ": " + ex.getMessage());
            return false;
        }
    }

    private boolean transition(RestoreToken token, byte expected, byte targetState) {
        if (token == null || token.file() == null) return false;
        SerializedSnapshot current = read(token.file());
        if (current == null || !current.id().equals(token.snapshot().id())
            || token.itemIndex() < 0 || token.itemIndex() >= current.items().size()
            || current.items().get(token.itemIndex()).state() != expected) return false;
        try {
            write(token.file(), withState(current, token.itemIndex(), targetState));
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not update inventory recovery state " + current.id() + ": " + ex.getMessage());
            return false;
        }
    }

    private boolean hasConsumedDuplicate(UUID playerId, long createdAt, byte[] payload) {
        for (SnapshotHandle handle : list(playerId)) {
            if (!sameTransactionWindow(handle.snapshot().createdAt(), createdAt)) continue;
            for (SerializedItem item : handle.snapshot().items()) {
                if (item.state() == CONSUMED && java.util.Arrays.equals(item.bytes(), payload)) return true;
            }
        }
        return false;
    }

    private void consumeTransactionDuplicates(UUID playerId, long createdAt, byte[] payload) {
        for (SnapshotHandle handle : list(playerId)) {
            SerializedSnapshot snapshot = handle.snapshot();
            if (!sameTransactionWindow(snapshot.createdAt(), createdAt)) continue;
            List<SerializedItem> items = new ArrayList<>(snapshot.items());
            boolean changed = false;
            for (int index = 0; index < items.size(); index++) {
                SerializedItem item = items.get(index);
                if (item.state() != CONSUMED && java.util.Arrays.equals(item.bytes(), payload)) {
                    items.set(index, new SerializedItem(item.source(), item.slot(), CONSUMED, item.bytes()));
                    changed = true;
                }
            }
            if (!changed) continue;
            try {
                write(handle.file(), new SerializedSnapshot(snapshot.id(), snapshot.playerId(), snapshot.playerName(),
                    snapshot.createdAt(), snapshot.surface(), snapshot.reason(), List.copyOf(items)));
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not retire a duplicate recovery record " + snapshot.id() + ": " + ex.getMessage());
            }
        }
    }

    private void write(Path target, SerializedSnapshot snapshot) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString() + ".", ".tmp");
        try {
            try (FileOutputStream file = new FileOutputStream(temporary.toFile());
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(file))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeLong(snapshot.id().getMostSignificantBits());
                out.writeLong(snapshot.id().getLeastSignificantBits());
                out.writeLong(snapshot.playerId().getMostSignificantBits());
                out.writeLong(snapshot.playerId().getLeastSignificantBits());
                out.writeUTF(snapshot.playerName());
                out.writeLong(snapshot.createdAt());
                out.writeUTF(snapshot.surface());
                out.writeUTF(snapshot.reason());
                out.writeInt(snapshot.items().size());
                for (SerializedItem item : snapshot.items()) {
                    out.writeUTF(item.source());
                    out.writeInt(item.slot());
                    out.writeByte(item.state());
                    out.writeInt(item.bytes().length);
                    out.write(item.bytes());
                }
                out.flush();
                file.getFD().sync();
            }
            moveAtomically(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private SerializedSnapshot read(Path file) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) throw new IOException("unsupported snapshot header");
            UUID id = new UUID(in.readLong(), in.readLong());
            UUID playerId = new UUID(in.readLong(), in.readLong());
            String playerName = in.readUTF();
            long createdAt = in.readLong();
            String surface = in.readUTF();
            String reason = in.readUTF();
            int count = in.readInt();
            if (count < 1 || count > 128) throw new IOException("invalid item count");
            List<SerializedItem> items = new ArrayList<>(count);
            int total = 0;
            for (int index = 0; index < count; index++) {
                String source = in.readUTF();
                int slot = in.readInt();
                byte state = in.readByte();
                int length = in.readInt();
                if (state < AVAILABLE || state > CONSUMED || length < 1 || length > MAX_ITEM_BYTES) {
                    throw new IOException("invalid recovery item");
                }
                total += length;
                if (total > MAX_SNAPSHOT_BYTES) throw new IOException("snapshot exceeds safe size");
                byte[] bytes = in.readNBytes(length);
                if (bytes.length != length) throw new IOException("truncated recovery item");
                items.add(new SerializedItem(source, slot, state, bytes));
            }
            if (in.read() != -1) throw new IOException("trailing recovery data");
            SerializedSnapshot snapshot = new SerializedSnapshot(id, playerId, playerName, createdAt, surface, reason, items);
            return valid(snapshot) ? snapshot : null;
        } catch (Exception ex) {
            plugin.getLogger().warning("Skipped invalid inventory recovery snapshot " + file.getFileName() + ": " + ex.getMessage());
            return null;
        }
    }

    private void prune(Path playerDirectory, long now) throws IOException {
        if (!Files.isDirectory(playerDirectory)) return;
        List<Path> files;
        try (Stream<Path> paths = Files.list(playerDirectory)) {
            files = new ArrayList<>(paths.filter(Files::isRegularFile).filter(RiskyInventoryRecoveryRepository::isSnapshot).toList());
        }
        files.sort(Comparator.comparingLong(RiskyInventoryRecoveryRepository::modified).reversed());
        long retainedBytes = 0L;
        for (int index = 0; index < files.size(); index++) {
            Path file = files.get(index);
            long modified = modified(file);
            long size = Files.size(file);
            boolean keep = shouldRetain(index, modified, now, retainedBytes, size);
            if (keep) retainedBytes += size;
            else Files.deleteIfExists(file);
        }
    }

    static boolean shouldRetain(int index, long modified, long now, long retainedBytes, long size) {
        return index < MAX_SNAPSHOTS_PER_PLAYER
            && modified >= now - MAX_AGE_MILLIS
            && size > 0L
            && retainedBytes + size <= MAX_PLAYER_BYTES;
    }

    static boolean selectorMatches(String id, String selector) {
        return id != null && selector != null
            && id.toLowerCase(Locale.ROOT).startsWith(selector.trim().toLowerCase(Locale.ROOT));
    }

    static boolean sameTransactionWindow(long first, long second) {
        long raw;
        try { raw = Math.subtractExact(first, second); }
        catch (ArithmeticException ex) { return false; }
        if (raw == Long.MIN_VALUE) return false;
        long difference = Math.abs(raw);
        return difference <= DUPLICATE_TRANSACTION_WINDOW_MILLIS;
    }

    static String shortId(UUID id) {
        return id == null ? "unknown" : id.toString().replace("-", "").substring(0, 10);
    }

    static MarkerParts parseMarker(String marker) {
        if (marker == null || marker.isBlank()) return null;
        int split = marker.lastIndexOf(':');
        if (split <= 0 || split >= marker.length() - 1) return null;
        try {
            UUID snapshotId = UUID.fromString(marker.substring(0, split));
            int itemIndex = Integer.parseInt(marker.substring(split + 1));
            return itemIndex < 0 ? null : new MarkerParts(snapshotId, itemIndex);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static SerializedSnapshot withState(SerializedSnapshot snapshot, int itemIndex, byte state) {
        List<SerializedItem> items = new ArrayList<>(snapshot.items());
        SerializedItem current = items.get(itemIndex);
        items.set(itemIndex, new SerializedItem(current.source(), current.slot(), state, current.bytes()));
        return new SerializedSnapshot(snapshot.id(), snapshot.playerId(), snapshot.playerName(), snapshot.createdAt(),
            snapshot.surface(), snapshot.reason(), List.copyOf(items));
    }

    private static boolean valid(SerializedSnapshot snapshot) {
        if (snapshot == null || snapshot.id() == null || snapshot.playerId() == null
            || snapshot.playerName() == null || snapshot.playerName().isBlank()
            || snapshot.surface() == null || snapshot.surface().isBlank()
            || snapshot.reason() == null || snapshot.reason().isBlank()
            || snapshot.items() == null || snapshot.items().isEmpty() || snapshot.items().size() > 128) return false;
        int total = 0;
        for (SerializedItem item : snapshot.items()) {
            if (item == null || item.source() == null || item.source().isBlank() || item.bytes() == null
                || item.bytes().length < 1 || item.bytes().length > MAX_ITEM_BYTES
                || item.state() < AVAILABLE || item.state() > CONSUMED) return false;
            total += item.bytes().length;
            if (total > MAX_SNAPSHOT_BYTES) return false;
        }
        return true;
    }

    private static boolean isSnapshot(Path path) {
        return path.getFileName().toString().endsWith(".dat");
    }

    private static long modified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException ignored) { return Long.MIN_VALUE; }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    record SerializedItem(String source, int slot, byte state, byte[] bytes) { }
    record SerializedSnapshot(UUID id, UUID playerId, String playerName, long createdAt, String surface, String reason,
                              List<SerializedItem> items) { }
    record SnapshotHandle(Path file, SerializedSnapshot snapshot) { }
    record SnapshotLookup(SnapshotHandle handle, String error) { }
    record RestoreToken(Path file, SerializedSnapshot snapshot, int itemIndex) { }
    record RestoreTransition(RestoreToken token, String error) { }
    record MarkerParts(UUID snapshotId, int itemIndex) { }
}
