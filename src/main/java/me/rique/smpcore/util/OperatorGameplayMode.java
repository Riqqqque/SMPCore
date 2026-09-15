package me.rique.smpcore.util;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OperatorGameplayMode {
    private static final Set<UUID> STAFF_MODE_PLAYERS = ConcurrentHashMap.newKeySet();

    private OperatorGameplayMode() {
    }

    public static boolean isStaffModeEnabled(Player player) {
        return player != null && STAFF_MODE_PLAYERS.contains(player.getUniqueId());
    }

    public static void setStaffMode(Player player, boolean enabled) {
        if (player == null) {
            return;
        }
        if (enabled) {
            STAFF_MODE_PLAYERS.add(player.getUniqueId());
        } else {
            STAFF_MODE_PLAYERS.remove(player.getUniqueId());
        }
    }

    public static boolean allowsPassiveBypass(Player player, String permission) {
        return player != null
            && permission != null
            && passiveBypassAllowed(player.isOp(), player.hasPermission(permission), isStaffModeEnabled(player));
    }

    static boolean passiveBypassAllowed(boolean operator, boolean hasPermission, boolean staffModeEnabled) {
        return hasPermission && (!operator || staffModeEnabled);
    }
}
