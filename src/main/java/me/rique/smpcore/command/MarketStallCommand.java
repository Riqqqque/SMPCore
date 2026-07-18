package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.shop.MarketStallManager;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class MarketStallCommand {

    private MarketStallCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("stall")
                .requires(source -> source.getSender().hasPermission("smpcore.stall.use")
                    || source.getSender().hasPermission("smpcore.stall.admin"))
                .executes(context -> status(plugin, context.getSource().getSender()))
                .then(Commands.literal("sell")
                    .executes(context -> sell(plugin, context.getSource().getSender())))
                .then(Commands.literal("transfer")
                    .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(context -> transfer(
                            plugin,
                            context.getSource().getSender(),
                            firstTarget(context.getArgument("player", PlayerSelectorArgumentResolver.class), context.getSource())
                        ))))
                .then(Commands.literal("accept")
                    .executes(context -> accept(plugin, context.getSource().getSender())))
                .then(Commands.literal("deny")
                    .executes(context -> deny(plugin, context.getSource().getSender())))
                .then(Commands.literal("admin")
                    .requires(source -> source.getSender().hasPermission("smpcore.stall.admin"))
                    .then(Commands.literal("wand")
                        .executes(context -> wand(plugin, context.getSource().getSender())))
                    .then(Commands.literal("create")
                        .then(Commands.argument("id", StringArgumentType.word())
                            .then(Commands.argument("price", LongArgumentType.longArg(1L, 100_000_000L))
                                .executes(context -> create(
                                    plugin,
                                    context.getSource().getSender(),
                                    StringArgumentType.getString(context, "id"),
                                    LongArgumentType.getLong(context, "price")
                                )))))
                    .then(Commands.literal("setprice")
                        .then(Commands.argument("id", StringArgumentType.word())
                            .then(Commands.argument("price", LongArgumentType.longArg(1L, 100_000_000L))
                                .executes(context -> setPrice(
                                    plugin,
                                    context.getSource().getSender(),
                                    StringArgumentType.getString(context, "id"),
                                    LongArgumentType.getLong(context, "price")
                                )))))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word())
                            .executes(context -> remove(plugin, context.getSource().getSender(), StringArgumentType.getString(context, "id")))))
                    .then(Commands.literal("restore")
                        .then(Commands.argument("id", StringArgumentType.word())
                            .executes(context -> restore(plugin, context.getSource().getSender(), StringArgumentType.getString(context, "id")))))
                    .then(Commands.literal("snapshot")
                        .then(Commands.argument("id", StringArgumentType.word())
                            .executes(context -> snapshot(plugin, context.getSource().getSender(), StringArgumentType.getString(context, "id"), false))
                            .then(Commands.literal("confirm")
                                .executes(context -> snapshot(plugin, context.getSource().getSender(), StringArgumentType.getString(context, "id"), true)))))
                    .then(Commands.literal("snapshotall")
                        .executes(context -> snapshotAll(plugin, context.getSource().getSender(), false))
                        .then(Commands.literal("confirm")
                            .executes(context -> snapshotAll(plugin, context.getSource().getSender(), true))))
                    .then(Commands.literal("list")
                        .executes(context -> list(plugin, context.getSource().getSender()))))
                .build(),
            "Buy and manage protected market stalls",
            List.of("stalls", "marketstall")
        );
    }

    private static int status(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        MarketStallManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.sendStatus(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int sell(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        MarketStallManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.sellBack(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int transfer(SMPCore plugin, CommandSender sender, Player target) {
        Player player = player(sender);
        MarketStallManager manager = manager(plugin, sender);
        if (player == null || manager == null || target == null) {
            if (target == null) sender.sendMessage(MessageUtil.warn("That player must be online."));
            return 0;
        }
        manager.requestTransfer(player, target);
        return Command.SINGLE_SUCCESS;
    }

    private static int accept(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        MarketStallManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.acceptTransfer(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int deny(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        MarketStallManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.denyTransfer(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int wand(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        MarketStallManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.giveWand(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int create(SMPCore plugin, CommandSender sender, String id, long price) {
        Player player = player(sender);
        MarketStallManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.createStall(player, id, price);
        return Command.SINGLE_SUCCESS;
    }

    private static int setPrice(SMPCore plugin, CommandSender sender, String id, long price) {
        Player player = player(sender);
        MarketStallManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.setPrice(player, id, price);
        return Command.SINGLE_SUCCESS;
    }

    private static int remove(SMPCore plugin, CommandSender sender, String id) {
        Player player = player(sender);
        MarketStallManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.removeStall(player, id);
        return Command.SINGLE_SUCCESS;
    }

    private static int restore(SMPCore plugin, CommandSender sender, String id) {
        Player player = player(sender);
        MarketStallManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.restoreStall(player, id);
        return Command.SINGLE_SUCCESS;
    }

    private static int snapshot(SMPCore plugin, CommandSender sender, String id, boolean confirmed) {
        Player player = player(sender);
        MarketStallManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.snapshotStall(player, id, confirmed);
        return Command.SINGLE_SUCCESS;
    }

    private static int snapshotAll(SMPCore plugin, CommandSender sender, boolean confirmed) {
        Player player = player(sender);
        MarketStallManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.snapshotAll(player, confirmed);
        return Command.SINGLE_SUCCESS;
    }

    private static int list(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        MarketStallManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.sendAdminList(player);
        return Command.SINGLE_SUCCESS;
    }

    private static Player player(CommandSender sender) {
        if (sender instanceof Player player) return player;
        sender.sendMessage(MessageUtil.error("Players must run this command."));
        return null;
    }

    private static MarketStallManager manager(SMPCore plugin, CommandSender sender) {
        MarketStallManager manager = plugin.getMarketStallManager();
        if (manager == null) sender.sendMessage(MessageUtil.error("Market stalls are not ready yet."));
        return manager;
    }

    private static Player firstTarget(PlayerSelectorArgumentResolver resolver, io.papermc.paper.command.brigadier.CommandSourceStack source) {
        try {
            List<Player> players = resolver.resolve(source);
            return players.isEmpty() ? null : players.getFirst();
        } catch (CommandSyntaxException ignored) {
            return null;
        }
    }
}
