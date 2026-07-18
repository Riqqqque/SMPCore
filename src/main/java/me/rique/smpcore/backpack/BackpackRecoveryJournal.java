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
import java.util.UUID;

final class BackpackRecoveryJournal {

    private static final int MAGIC = 0x534D5042;
    private static final int VERSION = 1;
    private static final int MAX_ITEM_BYTES = 32 * 1024 * 1024;

    private final SMPCore plugin;
    private final Path directory;

    BackpackRecoveryJournal(SMPCore plugin) {
        this.plugin = plugin;
        this.directory = plugin.getDataFolder().toPath().resolve("backpack-recovery");
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

        byte[] itemBytes;
        try {
            itemBytes = recoveredBackpack.serializeAsBytes();
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Could not serialize backpack recovery data for " + playerId + ": " + ex.getMessage());
            return false;
        }
        if (itemBytes.length <= 0 || itemBytes.length > MAX_ITEM_BYTES) {
            plugin.getLogger().severe("Backpack recovery data for " + playerId + " was outside the safe size range.");
            return false;
        }

        Path target = path(playerId);
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, playerId + ".", ".tmp");
            try (FileOutputStream file = new FileOutputStream(temporary.toFile());
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(file))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeLong(playerId.getMostSignificantBits());
                out.writeLong(playerId.getLeastSignificantBits());
                out.writeUTF(backpackId);
                out.writeUTF(sessionToken);
                out.writeInt(sourceSlot);
                out.writeLong(System.currentTimeMillis());
                out.writeInt(itemBytes.length);
                out.write(itemBytes);
                out.flush();
                file.getFD().sync();
            }
            moveAtomically(temporary, target);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save backpack recovery data for " + playerId + ": " + ex.getMessage());
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The next successful write will not use this temporary file.
                }
            }
        }
    }

    synchronized Recovery read(UUID playerId) {
        Path file = path(playerId);
        if (!Files.isRegularFile(file)) {
            return null;
        }

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) {
                throw new IOException("unsupported journal header");
            }
            UUID storedPlayer = new UUID(in.readLong(), in.readLong());
            if (!storedPlayer.equals(playerId)) {
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
            return new Recovery(playerId, backpackId, sessionToken, sourceSlot, savedAt, backpack);
        } catch (Exception ex) {
            plugin.getLogger().severe("Could not read backpack recovery data for " + playerId + ": " + ex.getMessage());
            return null;
        }
    }

    synchronized boolean exists(UUID playerId) {
        return Files.isRegularFile(path(playerId));
    }

    synchronized boolean delete(UUID playerId) {
        try {
            return !Files.exists(path(playerId)) || Files.deleteIfExists(path(playerId));
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not clear backpack recovery data for " + playerId + ": " + ex.getMessage());
            return false;
        }
    }

    private Path path(UUID playerId) {
        return directory.resolve(playerId + ".dat");
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
}
