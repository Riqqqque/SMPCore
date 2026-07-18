package me.rique.smpcore.player;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

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
        boolean firstJoin = !player.hasPlayedBefore();
        event.joinMessage(null);

        plugin.getHomeManager().preload(player.getUniqueId());
        plugin.getPlayerManager().applyVanishToNewPlayer(player);
        boolean suppressFirstJoinAnnouncement = firstJoin
            && plugin.getSmpStartManager() != null
            && plugin.getSmpStartManager().shouldDeferSeasonIntroduction();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.isEnabled() || !player.isOnline() || plugin.getSmpStartManager() == null) {
                return;
            }
            plugin.getSmpStartManager().presentSeasonIntroductionIfPending(player);
        }, 20L);

        plugin.getDatabase().getNickname(player.getUniqueId()).thenAccept(opt ->
            opt.ifPresent(nick -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!plugin.isEnabled() || !player.isOnline()) return;
                player.displayName(MM.deserialize(nick));
                if (plugin.getTabListManager() != null) plugin.getTabListManager().requestRefresh();
            }))
        );

        plugin.getDatabase().upsertPlayer(player.getUniqueId(), player.getName()).thenAccept(joinCount ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!plugin.isEnabled() || !player.isOnline()) return;
                if (suppressFirstJoinAnnouncement) {
                    return;
                }
                String template = firstJoin ? plugin.getConfigManager().joinFirst : plugin.getConfigManager().joinReturn;
                String resolved = applyJoinPlaceholders(template, player.getName(), joinCount);
                Component msg = MessageUtil.prefixedRaw(resolved);
                Bukkit.broadcast(msg);
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

}
