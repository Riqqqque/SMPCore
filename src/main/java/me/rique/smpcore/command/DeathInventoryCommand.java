package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.death.DeathInventoryManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class DeathInventoryCommand {

    private DeathInventoryCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("deathinventory")
                .requires(source -> source.getSender().hasPermission("smpcore.admin.deathinventory"))
                .executes(context -> help(plugin, context.getSource().getSender()))
                .then(Commands.literal("list")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> suggestPlayers(builder))
                        .executes(context -> list(
                            plugin,
                            context.getSource().getSender(),
                            StringArgumentType.getString(context, "player"),
                            1
                        ))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                            .executes(context -> list(
                                plugin,
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "player"),
                                IntegerArgumentType.getInteger(context, "page")
                            )))))
                .then(Commands.literal("view")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> suggestPlayers(builder))
                        .executes(context -> view(
                            plugin,
                            context.getSource().getSender(),
                            StringArgumentType.getString(context, "player"),
                            "latest"
                        ))
                        .then(Commands.argument("snapshot", StringArgumentType.word())
                            .suggests((context, builder) -> suggestSnapshots(
                                plugin,
                                StringArgumentType.getString(context, "player"),
                                builder
                            ))
                            .executes(context -> view(
                                plugin,
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "player"),
                                StringArgumentType.getString(context, "snapshot")
                            )))))
                .then(Commands.literal("restore")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> suggestPlayers(builder))
                        .executes(context -> restore(
                            plugin,
                            context.getSource().getSender(),
                            StringArgumentType.getString(context, "player"),
                            "latest"
                        ))
                        .then(Commands.argument("snapshot", StringArgumentType.word())
                            .suggests((context, builder) -> suggestSnapshots(
                                plugin,
                                StringArgumentType.getString(context, "player"),
                                builder
                            ))
                            .executes(context -> restore(
                                plugin,
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "player"),
                                StringArgumentType.getString(context, "snapshot")
                            )))))
                .then(Commands.literal("confirm")
                    .executes(context -> confirm(plugin, context.getSource().getSender())))
                .then(Commands.literal("cancel")
                    .executes(context -> cancel(plugin, context.getSource().getSender())))
                .build(),
            "Inspect and safely restore exact player death inventories",
            List.of("deathinv", "invrestore")
        );
    }

    private static int help(SMPCore plugin, org.bukkit.command.CommandSender sender) {
        DeathInventoryManager manager = plugin.getDeathInventoryManager();
        if (manager == null) {
            return 0;
        }
        manager.sendHelp(sender);
        return Command.SINGLE_SUCCESS;
    }

    private static int list(SMPCore plugin, org.bukkit.command.CommandSender sender, String player, int page) {
        DeathInventoryManager manager = plugin.getDeathInventoryManager();
        if (manager == null) {
            return 0;
        }
        manager.listSnapshots(sender, player, page);
        return Command.SINGLE_SUCCESS;
    }

    private static int view(SMPCore plugin, org.bukkit.command.CommandSender sender, String player, String snapshot) {
        DeathInventoryManager manager = plugin.getDeathInventoryManager();
        if (manager == null) {
            return 0;
        }
        manager.viewSnapshot(sender, player, snapshot);
        return Command.SINGLE_SUCCESS;
    }

    private static int restore(SMPCore plugin, org.bukkit.command.CommandSender sender, String player, String snapshot) {
        DeathInventoryManager manager = plugin.getDeathInventoryManager();
        if (manager == null) {
            return 0;
        }
        manager.requestRestore(sender, player, snapshot);
        return Command.SINGLE_SUCCESS;
    }

    private static int confirm(SMPCore plugin, org.bukkit.command.CommandSender sender) {
        DeathInventoryManager manager = plugin.getDeathInventoryManager();
        if (manager == null) {
            return 0;
        }
        manager.confirmRestore(sender);
        return Command.SINGLE_SUCCESS;
    }

    private static int cancel(SMPCore plugin, org.bukkit.command.CommandSender sender) {
        DeathInventoryManager manager = plugin.getDeathInventoryManager();
        if (manager == null) {
            return 0;
        }
        manager.cancelRestore(sender);
        return Command.SINGLE_SUCCESS;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPlayers(
        SuggestionsBuilder builder
    ) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            builder.suggest(player.getName());
        }
        return builder.buildFuture();
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestSnapshots(
        SMPCore plugin,
        String player,
        SuggestionsBuilder builder
    ) {
        DeathInventoryManager manager = plugin.getDeathInventoryManager();
        if (manager != null) {
            for (String suggestion : manager.suggestSnapshotIds(player)) {
                builder.suggest(suggestion);
            }
        }
        return builder.buildFuture();
    }
}
