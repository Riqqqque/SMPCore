package me.rique.smpcore.motd;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

import java.util.List;
import java.util.Map;

public final class MotdListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
        Map.entry('0', "black"),
        Map.entry('1', "dark_blue"),
        Map.entry('2', "dark_green"),
        Map.entry('3', "dark_aqua"),
        Map.entry('4', "dark_red"),
        Map.entry('5', "dark_purple"),
        Map.entry('6', "gold"),
        Map.entry('7', "gray"),
        Map.entry('8', "dark_gray"),
        Map.entry('9', "blue"),
        Map.entry('a', "green"),
        Map.entry('b', "aqua"),
        Map.entry('c', "red"),
        Map.entry('d', "light_purple"),
        Map.entry('e', "yellow"),
        Map.entry('f', "white"),
        Map.entry('k', "obfuscated"),
        Map.entry('l', "bold"),
        Map.entry('m', "strikethrough"),
        Map.entry('n', "underlined"),
        Map.entry('o', "italic"),
        Map.entry('r', "reset")
    );

    private final SMPCore plugin;

    public MotdListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerListPing(ServerListPingEvent event) {
        ConfigManager config = plugin.getConfigManager();
        if (config == null || !config.motdEnabled) {
            return;
        }

        String raw = renderMotd(config.motdLines, event);
        if (raw.isBlank()) {
            return;
        }

        event.motd(parseMotd(raw, config.motdLegacyColorCodes));
    }

    private String renderMotd(List<String> lines, ServerListPingEvent event) {
        String raw = String.join("\n", lines);
        return raw
            .replace("{online}", String.valueOf(event.getNumPlayers()))
            .replace("{max}", String.valueOf(event.getMaxPlayers()))
            .replace("{host}", safe(event.getHostname()))
            .replace("{newline}", "\n");
    }

    private Component parseMotd(String raw, boolean legacyColorCodes) {
        if (!legacyColorCodes) {
            try {
                return MM.deserialize(raw);
            } catch (RuntimeException ignored) {
                return Component.text(raw);
            }
        }

        try {
            return MM.deserialize(legacyToMiniMessage(raw));
        } catch (RuntimeException ignored) {
            return LEGACY.deserialize(raw.replace('\u00A7', '&'));
        }
    }

    private String legacyToMiniMessage(String raw) {
        StringBuilder output = new StringBuilder(raw.length() + 32);
        for (int i = 0; i < raw.length(); i++) {
            char current = raw.charAt(i);
            if ((current != '&' && current != '\u00A7') || i + 1 >= raw.length()) {
                output.append(current);
                continue;
            }

            char code = Character.toLowerCase(raw.charAt(i + 1));
            if (code == '#'
                && i + 7 < raw.length()
                && isHexColor(raw.substring(i + 2, i + 8))) {
                output.append("<#").append(raw, i + 2, i + 8).append(">");
                i += 7;
                continue;
            }

            String tag = LEGACY_TAGS.get(code);
            if (tag == null) {
                output.append(current);
                continue;
            }

            output.append('<').append(tag).append('>');
            i++;
        }
        return output.toString();
    }

    private boolean isHexColor(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f') && (c < 'A' || c > 'F')) {
                return false;
            }
        }
        return value.length() == 6;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
