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
import org.bukkit.event.server.ServerCommandEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class PluginActivityLogger implements Listener {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());
    private final SMPCore plugin;
    private final Path logFile;
    private final Object ioLock = new Object();

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
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> append(line));
    }

    private void append(String line) {
        synchronized (ioLock) {
            try {
                Files.createDirectories(logFile.getParent());
                Files.writeString(
                    logFile,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                );
            } catch (IOException ex) {
                plugin.getLogger().warning("Failed to write plugin activity log: " + ex.getMessage());
            }
        }
    }

    private String sanitize(String input) {
        if (input == null) {
            return "";
        }
        return input.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
