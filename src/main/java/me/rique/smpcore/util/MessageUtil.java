package me.rique.smpcore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

/**
 * Central message utility for plugin-facing chat output.
 */
public final class MessageUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String DEFAULT_PREFIX = "<dark_gray>[<gradient:#FF6B6B:#FFE66D>Server</gradient>]</dark_gray> ";
    private static volatile String prefix = DEFAULT_PREFIX;
    public static final String OK_COLOR = "<green>";
    public static final String ERR_COLOR = "<red>";
    public static final String WARN_COLOR = "<yellow>";
    public static final String INFO_COLOR = "<gray>";

    private MessageUtil() {}

    public static Component parse(String miniMessage, TagResolver... resolvers) {
        return MM.deserialize(miniMessage, resolvers);
    }

    public static Component prefixedRaw(String miniMessage, TagResolver... resolvers) {
        return MM.deserialize(prefix + miniMessage, resolvers);
    }

    public static Component success(String text) {
        return MM.deserialize(prefix + OK_COLOR + text);
    }

    public static Component error(String text) {
        return MM.deserialize(prefix + ERR_COLOR + text);
    }

    public static Component warn(String text) {
        return MM.deserialize(prefix + WARN_COLOR + text);
    }

    public static Component info(String text) {
        return MM.deserialize(prefix + INFO_COLOR + text);
    }

    public static void send(CommandSender sender, Component component) {
        sender.sendMessage(component);
    }

    public static void sendSuccess(CommandSender sender, String text) {
        sender.sendMessage(success(text));
    }

    public static void sendError(CommandSender sender, String text) {
        sender.sendMessage(error(text));
    }

    public static void sendInfo(CommandSender sender, String text) {
        sender.sendMessage(info(text));
    }

    public static TagResolver placeholder(String key, String value) {
        return Placeholder.unparsed(key, value);
    }

    public static TagResolver componentPlaceholder(String key, Component value) {
        return Placeholder.component(key, value);
    }

    public static void setPrefix(String configuredPrefix) {
        if (configuredPrefix == null || configuredPrefix.isBlank()) {
            prefix = DEFAULT_PREFIX;
            return;
        }
        prefix = configuredPrefix.endsWith(" ") ? configuredPrefix : configuredPrefix + " ";
    }

    public static String defaultPrefix() {
        return DEFAULT_PREFIX;
    }
}
