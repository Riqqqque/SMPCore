package me.rique.smpcore.player;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.meta.FireworkMeta;

import java.time.Duration;

/**
 * Handles join/quit messages, home preload, vanish sync, and player upsert.
 */
public final class JoinListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final SMPCore plugin;

    public JoinListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.joinMessage(null);

        plugin.getHomeManager().preload(player.getUniqueId());
        plugin.getPlayerManager().applyVanishToNewPlayer(player);

        plugin.getDatabase().getNickname(player.getUniqueId()).thenAccept(opt ->
            opt.ifPresent(nick -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!plugin.isEnabled() || !player.isOnline()) return;
                player.displayName(MM.deserialize(nick));
                player.playerListName(MM.deserialize(nick));
            }))
        );

        plugin.getDatabase().upsertPlayer(player.getUniqueId(), player.getName()).thenAccept(joinCount ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!plugin.isEnabled() || !player.isOnline()) return;
                String template = joinCount == 1
                    ? plugin.getConfigManager().joinFirst
                    : plugin.getConfigManager().joinReturn;
                String resolved = applyJoinPlaceholders(template, player.getName(), joinCount);
                Component msg = MessageUtil.prefixedRaw(resolved);
                Bukkit.broadcast(msg);

                if (joinCount == 1) {
                    playCovenantWelcome(player);
                    player.sendMessage(MessageUtil.info(
                        "Use <white>/help</white> to see player commands, including <white>/reliquary</white>."
                    ));
                }
            })
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String resolved = applyJoinPlaceholders(plugin.getConfigManager().quit, player.getName(), 0);
        event.quitMessage(MessageUtil.prefixedRaw(resolved));

        plugin.getPlayerManager().onDisconnect(player);
        plugin.getHomeManager().unload(player.getUniqueId());
        plugin.getWaystoneManager().unloadPlayer(player.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.getPlayerManager().saveDeathLocation(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
            || "CHORUS_FRUIT".equals(cause.name())) {
            plugin.getPlayerManager().saveBackLocation(event.getPlayer());
        }
    }

    private static String applyJoinPlaceholders(String template, String playerName, int joinCount) {
        return template
            .replace("{player}", playerName)
            .replace("{count}", Integer.toString(joinCount))
            .replace("<player>", playerName)
            .replace("<count>", Integer.toString(joinCount));
    }

    private void playCovenantWelcome(Player player) {
        player.showTitle(Title.title(
            MM.deserialize("<gradient:#ff4d6d:#facc15><bold>Season of the Covenant</bold></gradient>"),
            MM.deserialize("<gray>Your oath begins at the border.</gray>"),
            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(900))
        ));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
        Bukkit.broadcast(MessageUtil.prefixedRaw(
            "<gradient:#ff4d6d:#facc15><bold>" + player.getName() + "</bold></gradient> <gray>has entered the Season of the Covenant.</gray>"
        ));

        for (int i = 0; i < 3; i++) {
            int delay = i * 14;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!plugin.isEnabled() || !player.isOnline()) {
                    return;
                }
                Firework firework = player.getWorld().spawn(player.getLocation().add(0.0, 1.0, 0.0), Firework.class);
                FireworkMeta meta = firework.getFireworkMeta();
                meta.setPower(1);
                meta.addEffect(FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.fromRGB(255, 77, 109), Color.fromRGB(250, 204, 21))
                    .withFade(Color.fromRGB(34, 211, 238))
                    .trail(true)
                    .flicker(true)
                    .build());
                firework.setFireworkMeta(meta);
            }, delay);
        }
    }
}
