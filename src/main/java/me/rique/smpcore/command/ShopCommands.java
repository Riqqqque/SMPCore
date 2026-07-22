package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class ShopCommands {

    private ShopCommands() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("shops")
                .requires(source -> source.getSender().hasPermission("smpcore.shop")
                    || source.getSender().hasPermission("smpcore.shop.admin"))
                .executes(ctx -> {
                    sendHelp(ctx.getSource().getSender(), plugin);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("collect")
                    .executes(ctx -> collect(ctx.getSource().getSender(), plugin)))
                .then(Commands.literal("balance")
                    .executes(ctx -> balance(ctx.getSource().getSender(), plugin)))
                .then(Commands.literal("help")
                    .executes(ctx -> {
                        sendHelp(ctx.getSource().getSender(), plugin);
                        return Command.SINGLE_SUCCESS;
                    }))
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
        plugin.getPlayerShopListener().helpLines(sender.hasPermission("smpcore.shop.admin")).forEach(sender::sendMessage);
    }

    private static int collect(CommandSender sender, SMPCore plugin) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Players must run this command."));
            return 0;
        }
        if (plugin.getPlayerShopListener() == null) {
            sender.sendMessage(MessageUtil.error("Player shops are not ready yet."));
            return 0;
        }
        plugin.getPlayerShopListener().collectPayments(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int balance(CommandSender sender, SMPCore plugin) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Players must run this command."));
            return 0;
        }
        if (plugin.getPlayerShopListener() == null) {
            sender.sendMessage(MessageUtil.error("Player shops are not ready yet."));
            return 0;
        }
        List<String> payments = plugin.getPlayerShopListener().paymentSummary(player);
        if (payments.isEmpty()) {
            sender.sendMessage(MessageUtil.info("You have no shop payments waiting."));
        } else {
            sender.sendMessage(MessageUtil.info("Waiting shop payments: <white>" + String.join(", ", payments) + "</white>."));
        }
        return Command.SINGLE_SUCCESS;
    }
}
