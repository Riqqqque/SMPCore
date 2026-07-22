package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.CommandSuggestionUtil;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.warp.WarpManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class WarpCommand {

    private WarpCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("warp")
                .requires(source -> source.getSender().hasPermission("smpcore.warp.use")
                    || source.getSender().hasPermission("smpcore.warp.admin"))
                .executes(context -> list(plugin, context.getSource().getSender()))
                .then(Commands.literal("list")
                    .executes(context -> list(plugin, context.getSource().getSender())))
                .then(Commands.literal("create")
                    .requires(source -> source.getSender().hasPermission("smpcore.warp.admin"))
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(context -> create(
                            plugin,
                            context.getSource().getSender(),
                            StringArgumentType.getString(context, "name")
                        ))))
                .then(Commands.literal("move")
                    .requires(source -> source.getSender().hasPermission("smpcore.warp.admin"))
                    .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> suggestWarps(plugin, builder))
                        .executes(context -> move(
                            plugin,
                            context.getSource().getSender(),
                            StringArgumentType.getString(context, "name")
                        ))))
                .then(Commands.literal("delete")
                    .requires(source -> source.getSender().hasPermission("smpcore.warp.admin"))
                    .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> suggestWarps(plugin, builder))
                        .executes(context -> delete(
                            plugin,
                            context.getSource().getSender(),
                            StringArgumentType.getString(context, "name")
                        ))))
                .then(Commands.literal("info")
                    .requires(source -> source.getSender().hasPermission("smpcore.warp.admin"))
                    .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> suggestWarps(plugin, builder))
                        .executes(context -> info(
                            plugin,
                            context.getSource().getSender(),
                            StringArgumentType.getString(context, "name")
                        ))))
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests((context, builder) -> suggestWarps(plugin, builder))
                    .executes(context -> teleport(
                        plugin,
                        context.getSource().getSender(),
                        StringArgumentType.getString(context, "name")
                    )))
                .build(),
            "Use and manage public warps",
            List.of("warps")
        );
    }

    private static int create(SMPCore plugin, CommandSender sender, String name) {
        Player player = requirePlayer(sender);
        WarpManager manager = requireManager(plugin, sender);
        if (player == null || manager == null) return 0;
        return manager.createWarp(player, name) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int move(SMPCore plugin, CommandSender sender, String name) {
        Player player = requirePlayer(sender);
        WarpManager manager = requireManager(plugin, sender);
        if (player == null || manager == null) return 0;
        return manager.moveWarp(player, name) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int delete(SMPCore plugin, CommandSender sender, String name) {
        WarpManager manager = requireManager(plugin, sender);
        if (manager == null) return 0;
        return manager.deleteWarp(sender, name) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int info(SMPCore plugin, CommandSender sender, String name) {
        WarpManager manager = requireManager(plugin, sender);
        if (manager == null) return 0;
        manager.sendInfo(sender, name);
        return Command.SINGLE_SUCCESS;
    }

    private static int list(SMPCore plugin, CommandSender sender) {
        WarpManager manager = requireManager(plugin, sender);
        if (manager == null) return 0;
        manager.sendList(sender);
        return Command.SINGLE_SUCCESS;
    }

    private static int teleport(SMPCore plugin, CommandSender sender, String name) {
        Player player = requirePlayer(sender);
        WarpManager manager = requireManager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.teleport(player, name);
        return Command.SINGLE_SUCCESS;
    }

    private static Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        sender.sendMessage(MessageUtil.error("Only players can use that warp action."));
        return null;
    }

    private static WarpManager requireManager(SMPCore plugin, CommandSender sender) {
        WarpManager manager = plugin.getWarpManager();
        if (manager != null) return manager;
        sender.sendMessage(MessageUtil.error("Public warps are unavailable right now."));
        return null;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestWarps(
        SMPCore plugin,
        com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        WarpManager manager = plugin.getWarpManager();
        return CommandSuggestionUtil.suggestMatching(builder, manager == null ? List.of() : manager.commandNames());
    }
}
