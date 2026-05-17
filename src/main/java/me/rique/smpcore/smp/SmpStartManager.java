package me.rique.smpcore.smp;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.config.ConfigManager;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SmpStartManager implements Listener {

    private static final long MESSAGE_THROTTLE_MS = 2500L;

    private final SMPCore plugin;
    private final Map<UUID, Long> nextGraceMessageAt = new ConcurrentHashMap<>();

    public SmpStartManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void applyConfiguredState() {
        if (!plugin.getConfigManager().smpStartEnabled) {
            return;
        }

        World world = configuredWorld();
        if (world == null) {
            plugin.getLogger().warning("SMP start world '" + plugin.getConfigManager().smpStartWorld + "' is not loaded.");
            return;
        }

        applyBorder(world, false);
    }

    public StartResult start(CommandSender sender) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.smpStartEnabled) {
            return new StartResult(false, "SMP start flow is disabled in config.");
        }
        if (config.smpStarted) {
            return new StartResult(false, "The SMP has already been started.");
        }

        World world = configuredWorld();
        if (world == null) {
            return new StartResult(false, "SMP start world '" + config.smpStartWorld + "' is not loaded.");
        }

        long now = System.currentTimeMillis();
        config.smpStarted = true;
        config.smpStartedAt = now;
        plugin.getConfig().set("smp-start.started", true);
        plugin.getConfig().set("smp-start.started-at", now);
        plugin.saveConfig();

        applyBorder(world, true);
        Bukkit.broadcast(startBroadcast());
        world.playSound(world.getSpawnLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        return new StartResult(true, "SMP started by " + sender.getName() + ".");
    }

    public StartResult reset(CommandSender sender) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.smpStartEnabled) {
            return new StartResult(false, "SMP start flow is disabled in config.");
        }

        config.smpStarted = false;
        config.smpStartedAt = 0L;
        plugin.getConfig().set("smp-start.started", false);
        plugin.getConfig().set("smp-start.started-at", 0L);
        plugin.saveConfig();

        applyConfiguredState();
        Bukkit.broadcast(MessageUtil.warn("The SMP start state was reset by <white>" + sender.getName() + "</white>."));
        return new StartResult(true, "SMP start state reset.");
    }

    public Component statusMessage() {
        ConfigManager config = plugin.getConfigManager();
        if (!config.smpStartEnabled) {
            return MessageUtil.info("SMP start flow is disabled.");
        }

        String border = formatNumber(config.smpStarted ? config.smpStartedBorderDiameter : config.smpPreStartBorderDiameter);
        if (!config.smpStarted) {
            return MessageUtil.info("SMP has not started. Border: <white>" + border + "</white>. PvP is blocked.");
        }

        if (isGraceActive()) {
            return MessageUtil.info(
                "SMP is started. Border: <white>" + border + "</white>. PvP unlocks in <white>"
                    + formatDuration(graceMillisRemaining()) + "</white>."
            );
        }

        return MessageUtil.info("SMP is started. Border: <white>" + border + "</white>. PvP is unlocked.");
    }

    public boolean isGraceActive() {
        ConfigManager config = plugin.getConfigManager();
        if (!config.smpStartEnabled) return false;
        if (!config.smpStarted) return true;
        return graceMillisRemaining() > 0L;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (!event.getWorld().getName().equalsIgnoreCase(plugin.getConfigManager().smpStartWorld)) {
            return;
        }
        applyConfiguredState();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (!isGraceActive()) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = attackingPlayer(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;

        event.setCancelled(true);
        maybeSendGraceMessage(attacker);
    }

    private void applyBorder(World world, boolean fromStartCommand) {
        ConfigManager config = plugin.getConfigManager();
        WorldBorder border = world.getWorldBorder();
        Location center = world.getSpawnLocation();
        border.setCenter(center.getX(), center.getZ());

        double targetSize = config.smpStarted
            ? config.smpStartedBorderDiameter
            : config.smpPreStartBorderDiameter;
        if (fromStartCommand && config.smpBorderExpandSeconds > 0) {
            border.changeSize(targetSize, config.smpBorderExpandSeconds);
        } else {
            border.setSize(targetSize);
        }
    }

    private Component startBroadcast() {
        ConfigManager config = plugin.getConfigManager();
        String message = config.smpStartBroadcast == null || config.smpStartBroadcast.isBlank()
            ? "<gold><bold>The SMP has started!</bold></gold>"
            : config.smpStartBroadcast;
        message = message
            .replace("{border}", formatNumber(config.smpStartedBorderDiameter))
            .replace("{grace}", formatDuration(graceMillisRemaining()));
        return MessageUtil.prefixedRaw(message);
    }

    private void maybeSendGraceMessage(Player player) {
        long now = System.currentTimeMillis();
        Long next = nextGraceMessageAt.get(player.getUniqueId());
        if (next != null && next > now) {
            return;
        }

        nextGraceMessageAt.put(player.getUniqueId(), now + MESSAGE_THROTTLE_MS);
        String message = plugin.getConfigManager().smpGraceDenyMessage;
        if (message == null || message.isBlank()) {
            message = "<yellow>PvP is protected for <white>{time}</white>.</yellow>";
        }
        player.sendMessage(MessageUtil.prefixedRaw(message.replace("{time}", formatDuration(graceMillisRemaining()))));
    }

    private long graceMillisRemaining() {
        ConfigManager config = plugin.getConfigManager();
        if (!config.smpStarted) {
            return Long.MAX_VALUE;
        }
        long graceMs = Duration.ofMinutes(config.smpPostStartGraceMinutes).toMillis();
        long endsAt = config.smpStartedAt + graceMs;
        return Math.max(0L, endsAt - System.currentTimeMillis());
    }

    private World configuredWorld() {
        return Bukkit.getWorld(plugin.getConfigManager().smpStartWorld);
    }

    private Player attackingPlayer(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (!(damager instanceof Projectile projectile)) {
            return null;
        }
        ProjectileSource source = projectile.getShooter();
        return source instanceof Player player ? player : null;
    }

    private String formatDuration(long millis) {
        if (millis == Long.MAX_VALUE) {
            return "until /startsmp";
        }
        long seconds = Math.max(0L, (millis + 999L) / 1000L);
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        if (minutes <= 0L) {
            return remainingSeconds + "s";
        }
        if (remainingSeconds == 0L) {
            return minutes + "m";
        }
        return minutes + "m " + remainingSeconds + "s";
    }

    private String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return Long.toString(Math.round(value));
        }
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    public record StartResult(boolean success, String message) {}
}
