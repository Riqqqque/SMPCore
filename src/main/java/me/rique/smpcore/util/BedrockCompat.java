package me.rique.smpcore.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Optional Bedrock/Floodgate compatibility helpers.
 * Uses reflection so SMPCore does not require Floodgate at compile time.
 */
public final class BedrockCompat {

    private static volatile Method floodgateGetInstance;
    private static volatile Method floodgateIsPlayer;

    private BedrockCompat() {
    }

    public static boolean isBedrockPlayer(Player player) {
        if (player == null) {
            return false;
        }
        ensureLookup();
        Method getInstance = floodgateGetInstance;
        Method isPlayer = floodgateIsPlayer;
        if (getInstance == null || isPlayer == null) {
            return false;
        }
        try {
            Object api = getInstance.invoke(null);
            if (api == null) {
                return false;
            }
            Object result = isPlayer.invoke(api, player.getUniqueId());
            return result instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            floodgateGetInstance = null;
            floodgateIsPlayer = null;
            return false;
        }
    }

    public static Component menuTitle(Player player, Component javaTitle, String bedrockTitle) {
        return isBedrockPlayer(player) ? Component.text(bedrockTitle) : javaTitle;
    }

    public static String menuActionWord(Player player) {
        return isBedrockPlayer(player) ? "Tap" : "Click";
    }

    public static boolean isFloodgateAvailable() {
        return isFloodgatePresent();
    }

    private static void ensureLookup() {
        if (floodgateGetInstance != null && floodgateIsPlayer != null) {
            return;
        }
        synchronized (BedrockCompat.class) {
            if (floodgateGetInstance != null && floodgateIsPlayer != null) {
                return;
            }
            if (!isFloodgatePresent()) {
                return;
            }
            try {
                Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                floodgateGetInstance = apiClass.getMethod("getInstance");
                floodgateIsPlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                floodgateGetInstance = null;
                floodgateIsPlayer = null;
            }
        }
    }

    private static boolean isFloodgatePresent() {
        return Bukkit.getPluginManager().isPluginEnabled("floodgate");
    }
}
