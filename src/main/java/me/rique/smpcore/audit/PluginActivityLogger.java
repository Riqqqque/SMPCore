package me.rique.smpcore.audit;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.PluginCommandRoots;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class PluginActivityLogger implements Listener {

    private static final long MAX_LOG_BYTES = 5L * 1024L * 1024L;
    private static final int MAX_ARCHIVES = 5;
    private static final int MAX_QUEUED_LINES = 2048;
    private static final int MAX_BATCH_LINES = 256;
    private static final int MAX_FIELD_LENGTH = 4096;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());
    private final SMPCore plugin;
    private final Path logFile;
    private final Object ioLock = new Object();
    private final ArrayBlockingQueue<String> pendingLines = new ArrayBlockingQueue<>(MAX_QUEUED_LINES);
    private final AtomicBoolean drainScheduled = new AtomicBoolean();
    private final AtomicLong droppedLines = new AtomicLong();

    public PluginActivityLogger(SMPCore plugin) {
        this.plugin = plugin;
        this.logFile = plugin.getDataFolder().toPath().resolve("logs").resolve("plugin-activity.log");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        String root = rootCommand(message);
        if (!isTrackedCommand(root)) {
            return;
        }

        Player player = event.getPlayer();
        String location = player.getWorld().getName()
            + " " + player.getLocation().getBlockX()
            + "," + player.getLocation().getBlockY()
            + "," + player.getLocation().getBlockZ();
        write("player_command", player, "cancelled=" + event.isCancelled()
            + " op=" + player.isOp()
            + " gamemode=" + player.getGameMode()
            + " location=" + location
            + " command=" + sanitize(message));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent event) {
        String root = rootCommand(event.getCommand());
        if (!isTrackedCommand(root)) {
            return;
        }
        write("server_command", event.getSender(), "command=" + sanitize(event.getCommand()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            drainPendingLines();
        }
    }

    private boolean isTrackedCommand(String root) {
        return PluginCommandRoots.contains(root);
    }

    private String rootCommand(String command) {
        if (command == null || command.isBlank()) {
            return null;
        }
        String normalized = command.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        int space = normalized.indexOf(' ');
        if (space >= 0) {
            normalized = normalized.substring(0, space);
        }
        int namespace = normalized.indexOf(':');
        if (namespace >= 0 && namespace + 1 < normalized.length()) {
            normalized = normalized.substring(namespace + 1);
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private void write(String type, CommandSender sender, String details) {
        String actor = sender == null ? "unknown" : sender.getName();
        String line = "[" + TIME_FORMAT.format(Instant.now()) + "] "
            + type
            + " actor=" + sanitize(actor)
            + " " + details
            + System.lineSeparator();
        if (!pendingLines.offer(line)) {
            droppedLines.incrementAndGet();
        }
        scheduleDrain();
    }

    private void scheduleDrain() {
        if (!drainScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, this::drainPendingLines);
        } catch (RuntimeException ex) {
            drainScheduled.set(false);
            drainPendingLines();
        }
    }

    private void drainPendingLines() {
        while (true) {
            StringBuilder batch = new StringBuilder();
            int drained = 0;
            String line;
            while (drained < MAX_BATCH_LINES && (line = pendingLines.poll()) != null) {
                batch.append(line);
                drained++;
            }
            long dropped = droppedLines.getAndSet(0L);
            if (dropped > 0L) {
                batch.append('[').append(TIME_FORMAT.format(Instant.now())).append("] logger dropped=")
                    .append(dropped).append(" reason=queue_full").append(System.lineSeparator());
            }
            if (!batch.isEmpty()) {
                append(batch.toString());
            }

            if (!pendingLines.isEmpty()) {
                continue;
            }
            drainScheduled.set(false);
            if (pendingLines.isEmpty() || !drainScheduled.compareAndSet(false, true)) {
                return;
            }
        }
    }

    private void append(String lines) {
        synchronized (ioLock) {
            try {
                Files.createDirectories(logFile.getParent());
                rotateIfNeeded(lines.getBytes(StandardCharsets.UTF_8).length);
                Files.writeString(
                    logFile,
                    lines,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                );
            } catch (IOException ex) {
                plugin.getLogger().warning("Failed to write plugin activity log: " + ex.getMessage());
            }
        }
    }

    private void rotateIfNeeded(int incomingBytes) throws IOException {
        if (!Files.exists(logFile) || Files.size(logFile) + Math.max(0, incomingBytes) <= MAX_LOG_BYTES) {
            return;
        }
        Files.deleteIfExists(archivePath(MAX_ARCHIVES));
        for (int archive = MAX_ARCHIVES - 1; archive >= 1; archive--) {
            Path source = archivePath(archive);
            if (Files.exists(source)) {
                Files.move(source, archivePath(archive + 1), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.move(logFile, archivePath(1), StandardCopyOption.REPLACE_EXISTING);
    }

    private Path archivePath(int archive) {
        return logFile.resolveSibling("plugin-activity." + archive + ".log");
    }

    private String sanitize(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder safe = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char character = input.charAt(i);
            safe.append(Character.isISOControl(character) ? ' ' : character);
        }
        String sanitized = safe.toString().trim();
        if (sanitized.length() <= MAX_FIELD_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_FIELD_LENGTH) + "...";
    }
}
