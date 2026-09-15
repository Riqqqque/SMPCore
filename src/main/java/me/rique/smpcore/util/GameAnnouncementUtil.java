package me.rique.smpcore.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public final class GameAnnouncementUtil {

    public static final double DEFAULT_RADIUS = 16.0D;

    private GameAnnouncementUtil() {
    }

    public static int announceNearby(
        Location preferredOrigin,
        Player fallbackPlayer,
        Component message,
        Sound sound,
        float volume,
        float pitch,
        Set<UUID> soundExclusions
    ) {
        Location origin = usableOrigin(preferredOrigin);
        Location fallbackOrigin = fallbackPlayer != null && fallbackPlayer.isOnline()
            ? usableOrigin(fallbackPlayer.getLocation())
            : null;
        if (origin == null) {
            origin = fallbackOrigin;
        }
        if (origin == null || message == null) {
            return 0;
        }

        Set<UUID> excludedSounds = soundExclusions == null ? Set.of() : soundExclusions;
        int recipients = deliverNearby(origin, message, sound, volume, pitch, excludedSounds);
        if (recipients == 0 && fallbackOrigin != null && !sameOrigin(origin, fallbackOrigin)) {
            recipients = deliverNearby(fallbackOrigin, message, sound, volume, pitch, excludedSounds);
        }
        return recipients;
    }

    private static int deliverNearby(
        Location origin,
        Component message,
        Sound sound,
        float volume,
        float pitch,
        Set<UUID> soundExclusions
    ) {
        World world = origin.getWorld();
        int recipients = 0;
        for (Player nearby : world.getNearbyPlayers(origin, DEFAULT_RADIUS)) {
            if (!nearby.isOnline()) {
                continue;
            }
            BedrockCompat.sendGameMessage(nearby, message);
            recipients++;
            if (sound != null && !soundExclusions.contains(nearby.getUniqueId())) {
                nearby.playSound(origin, sound, volume, pitch);
            }
        }
        return recipients;
    }

    public static int broadcast(Component message) {
        if (message == null) {
            return 0;
        }
        int recipients = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            BedrockCompat.sendGameMessage(player, message);
            recipients++;
        }
        return recipients;
    }

    private static Location usableOrigin(Location location) {
        if (location == null || location.getWorld() == null
            || !Double.isFinite(location.getX())
            || !Double.isFinite(location.getY())
            || !Double.isFinite(location.getZ())) {
            return null;
        }
        return location.clone();
    }

    private static boolean sameOrigin(Location first, Location second) {
        return first.getWorld() == second.getWorld() && first.distanceSquared(second) < 0.01D;
    }
}
