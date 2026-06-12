package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.command.CommandSender;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class ShopCommands {

    private ShopCommands() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("shops")
                .requires(source -> source.getSender().hasPermission("smpcore.shop"))
                .executes(ctx -> {
                    sendHelp(ctx.getSource().getSender(), plugin);
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Show player shop setup help",
            List.of("shop")
        );
    }

    private static void sendHelp(CommandSender sender, SMPCore plugin) {
        if (!plugin.getConfigManager().playerShopsEnabled) {
            sender.sendMessage(MessageUtil.warn("Player shops are currently disabled."));
            return;
        }
        if (plugin.getPlayerShopListener() == null) {
            sender.sendMessage(MessageUtil.error("Player shops are not ready yet."));
            return;
        }
        sender.sendMessage(MessageUtil.prefixedRaw("<gradient:#22c55e:#facc15><bold>Player Shops</bold></gradient>"));
        plugin.getPlayerShopListener().helpLines().forEach(sender::sendMessage);
    }
}
