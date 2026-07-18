package me.rique.smpcore.launch;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.connection.PlayerLoginConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class LaunchAccessManager implements Listener {

    private static final String CONFIG_ROOT = "launch-access";
    private static final String DEFAULT_DENY_MESSAGE =
        "<red>The server is still preparing for launch.</red> <gray>Please check back soon.</gray>";

    private final SMPCore plugin;
    private volatile boolean enabled;
    private volatile boolean locked;
    private volatile boolean manageVanillaWhitelist;
    private volatile Set<UUID> allowedPlayerUuids = Set.of();
    private volatile Component denyMessage = MessageUtil.parse(DEFAULT_DENY_MESSAGE);

    public LaunchAccessManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        reloadConfig();
        if (enabled && locked) {
            plugin.getLogger().info("Launch access locked to " + allowedPlayerUuids.size() + " approved player(s).");
        } else if (enabled) {
            plugin.getLogger().info("Launch access open to all players.");
        }
    }

    public void reloadConfig() {
        enabled = plugin.getConfig().getBoolean(CONFIG_ROOT + ".enabled", false);
        locked = plugin.getConfig().getBoolean(CONFIG_ROOT + ".locked", false);
        manageVanillaWhitelist = plugin.getConfig().getBoolean(CONFIG_ROOT + ".manage-vanilla-whitelist", true);
        allowedPlayerUuids = parseAllowedUuids(
            plugin.getConfig().getStringList(CONFIG_ROOT + ".allowed-player-uuids")
        );
        String configuredMessage = plugin.getConfig().getString(CONFIG_ROOT + ".denied-message", DEFAULT_DENY_MESSAGE);
        try {
            denyMessage = MessageUtil.parse(configuredMessage == null ? DEFAULT_DENY_MESSAGE : configuredMessage);
        } catch (RuntimeException ignored) {
            denyMessage = MessageUtil.parse(DEFAULT_DENY_MESSAGE);
        }
        applyWhitelistState();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerConnectionValidateLoginEvent event) {
        UUID playerUuid = connectionUuid(event.getConnection());
        if (!shouldDeny(enabled, locked, allowedPlayerUuids, playerUuid)) {
            return;
        }
        event.kickMessage(denyMessage);
    }

    public ActionResult openPublicAccess() {
        enabled = true;
        locked = false;
        saveState();
        applyWhitelistState();
        return new ActionResult(true, "Launch access is open. Everyone can join; the pre-SMP barrier stays active.");
    }

    public ActionResult lockToAllowedPlayers() {
        if (allowedPlayerUuids.isEmpty()) {
            return new ActionResult(false, "No launch owner is configured. Use /launchaccess allowme first.");
        }
        enabled = true;
        locked = true;
        saveState();
        applyWhitelistState();
        for (Player player : List.copyOf(plugin.getServer().getOnlinePlayers())) {
            if (!allowedPlayerUuids.contains(player.getUniqueId())) {
                player.kick(denyMessage);
            }
        }
        return new ActionResult(true, "Launch access is locked to " + allowedPlayerUuids.size() + " approved player(s).");
    }

    public ActionResult allow(UUID playerUuid) {
        if (playerUuid == null) {
            return new ActionResult(false, "That player UUID is invalid.");
        }
        LinkedHashSet<UUID> updated = new LinkedHashSet<>(allowedPlayerUuids);
        if (!updated.add(playerUuid)) {
            return new ActionResult(false, "That player is already approved for launch access.");
        }
        allowedPlayerUuids = Set.copyOf(updated);
        enabled = true;
        persistAllowedPlayers();
        saveState();
        if (manageVanillaWhitelist) {
            plugin.getServer().getOfflinePlayer(playerUuid).setWhitelisted(true);
        }
        String name = plugin.getServer().getOfflinePlayer(playerUuid).getName();
        String label = name == null || name.isBlank() ? playerUuid.toString() : name;
        return new ActionResult(true, label + " can now join while launch access is locked.");
    }

    public ActionResult allowSelf(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return new ActionResult(false, "Only a player can use /launchaccess allowme.");
        }
        return allow(player.getUniqueId());
    }

    public Component statusMessage() {
        if (!enabled) {
            return MessageUtil.info("Launch access is <white>disabled</white>; Paper's normal whitelist controls joins.");
        }
        if (locked) {
            return MessageUtil.warn(
                "Launch access is <white>locked</white> to <white>" + allowedPlayerUuids.size() + "</white> approved player(s)."
            );
        }
        return MessageUtil.success("Launch access is <white>open</white>; everyone may join.");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isLocked() {
        return enabled && locked;
    }

    public Set<UUID> allowedPlayerUuids() {
        return allowedPlayerUuids;
    }

    private void applyWhitelistState() {
        if (!enabled || !manageVanillaWhitelist) {
            return;
        }
        plugin.getServer().setWhitelist(locked);
        plugin.getServer().setWhitelistEnforced(locked);
        if (!locked) {
            return;
        }
        for (OfflinePlayer whitelistedPlayer : Set.copyOf(plugin.getServer().getWhitelistedPlayers())) {
            if (!allowedPlayerUuids.contains(whitelistedPlayer.getUniqueId())) {
                whitelistedPlayer.setWhitelisted(false);
            }
        }
        for (UUID uuid : allowedPlayerUuids) {
            OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(uuid);
            offlinePlayer.setWhitelisted(true);
        }
        plugin.getServer().reloadWhitelist();
    }

    private void saveState() {
        plugin.getConfig().set(CONFIG_ROOT + ".enabled", enabled);
        plugin.getConfig().set(CONFIG_ROOT + ".locked", locked);
        plugin.saveConfig();
    }

    private void persistAllowedPlayers() {
        List<String> values = allowedPlayerUuids.stream().map(UUID::toString).sorted().toList();
        plugin.getConfig().set(CONFIG_ROOT + ".allowed-player-uuids", values);
    }

    static Set<UUID> parseAllowedUuids(List<String> rawValues) {
        LinkedHashSet<UUID> parsed = new LinkedHashSet<>();
        if (rawValues == null) {
            return Set.of();
        }
        for (String raw : rawValues) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                parsed.add(UUID.fromString(raw.trim()));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed entries so one typo cannot disable the whole launch gate.
            }
        }
        return Set.copyOf(parsed);
    }

    static boolean shouldDeny(boolean enabled, boolean locked, Set<UUID> allowed, UUID playerUuid) {
        return enabled && locked && (playerUuid == null || allowed == null || !allowed.contains(playerUuid));
    }

    private static UUID connectionUuid(PlayerConnection connection) {
        if (connection instanceof PlayerLoginConnection loginConnection) {
            return loginConnection.getAuthenticatedProfile().getId();
        }
        if (connection instanceof PlayerConfigurationConnection configurationConnection) {
            return configurationConnection.getProfile().getId();
        }
        return null;
    }

    public record ActionResult(boolean success, String message) {}
}
