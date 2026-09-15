package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.home.AllianceHomeManager;
import me.rique.smpcore.util.CommandSuggestionUtil;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
public final class HomeCommands {

    private HomeCommands() {}

    public static void register(Commands commands, SMPCore plugin) {

        commands.register(
            Commands.literal("home")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.home"))
                .executes(ctx -> {
                    plugin.getHomeManager().teleportHome((Player) ctx.getSource().getSender(), "home");
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestHomes(plugin, (Player) ctx.getSource().getSender(), builder))
                    .executes(ctx -> {
                        plugin.getHomeManager().teleportHome(
                            (Player) ctx.getSource().getSender(),
                            StringArgumentType.getString(ctx, "name")
                        );
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "Teleport to a saved home"
        );

        commands.register(
            Commands.literal("sethome")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.home"))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    setHome(player, "home", plugin);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestHomes(plugin, (Player) ctx.getSource().getSender(), builder))
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        setHome(player, StringArgumentType.getString(ctx, "name"), plugin);
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "Set a home at your current location"
        );

        registerDeleteHomeCommand(commands, plugin, "delhome");
        registerDeleteHomeCommand(commands, plugin, "deletehome");
        registerDeleteHomeCommand(commands, plugin, "removehome");

        commands.register(
            Commands.literal("homes")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.home"))
                .executes(ctx -> {
                    plugin.getHomeManager().listHomes((Player) ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "List your saved homes"
        );

        registerAllianceHomeCommands(commands, plugin);
    }

    private static void registerAllianceHomeCommands(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("allyhome")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.allyhome"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests((ctx, builder) ->
                        suggestAllianceHomes(plugin, (Player) ctx.getSource().getSender(), builder))
                    .executes(ctx -> {
                        plugin.getAllianceHomeManager().teleportHome(
                            (Player) ctx.getSource().getSender(),
                            StringArgumentType.getString(ctx, "name"),
                            null
                        );
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.argument("ally", StringArgumentType.greedyString())
                        .suggests((ctx, builder) ->
                            suggestAllies(plugin, (Player) ctx.getSource().getSender(), builder))
                        .executes(ctx -> {
                            plugin.getAllianceHomeManager().teleportHome(
                                (Player) ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "name"),
                                StringArgumentType.getString(ctx, "ally")
                            );
                            return Command.SINGLE_SUCCESS;
                        })))
                .build(),
            "Teleport to a shared alliance home"
        );

        commands.register(
            Commands.literal("allysethome")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.allyhome"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests((ctx, builder) ->
                        suggestAllianceHomes(plugin, (Player) ctx.getSource().getSender(), builder))
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        sendAllianceResult(
                            plugin,
                            player,
                            plugin.getAllianceHomeManager().setHome(
                                player,
                                StringArgumentType.getString(ctx, "name"),
                                null
                            )
                        );
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.argument("ally", StringArgumentType.greedyString())
                        .suggests((ctx, builder) ->
                            suggestAllies(plugin, (Player) ctx.getSource().getSender(), builder))
                        .executes(ctx -> {
                            Player player = (Player) ctx.getSource().getSender();
                            sendAllianceResult(
                                plugin,
                                player,
                                plugin.getAllianceHomeManager().setHome(
                                    player,
                                    StringArgumentType.getString(ctx, "name"),
                                    StringArgumentType.getString(ctx, "ally")
                                )
                            );
                            return Command.SINGLE_SUCCESS;
                        })))
                .build(),
            "Set a shared alliance home"
        );

        commands.register(
            Commands.literal("allydelhome")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.allyhome"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests((ctx, builder) ->
                        suggestAllianceHomes(plugin, (Player) ctx.getSource().getSender(), builder))
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        sendAllianceResult(
                            plugin,
                            player,
                            plugin.getAllianceHomeManager().deleteHome(
                                player,
                                StringArgumentType.getString(ctx, "name"),
                                null
                            )
                        );
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.argument("ally", StringArgumentType.greedyString())
                        .suggests((ctx, builder) ->
                            suggestAllies(plugin, (Player) ctx.getSource().getSender(), builder))
                        .executes(ctx -> {
                            Player player = (Player) ctx.getSource().getSender();
                            sendAllianceResult(
                                plugin,
                                player,
                                plugin.getAllianceHomeManager().deleteHome(
                                    player,
                                    StringArgumentType.getString(ctx, "name"),
                                    StringArgumentType.getString(ctx, "ally")
                                )
                            );
                            return Command.SINGLE_SUCCESS;
                        })))
                .build(),
            "Delete a shared alliance home"
        );

        commands.register(
            Commands.literal("allyhomes")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.allyhome"))
                .executes(ctx -> {
                    plugin.getAllianceHomeManager().listHomes(
                        (Player) ctx.getSource().getSender(),
                        null
                    );
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("ally", StringArgumentType.greedyString())
                    .suggests((ctx, builder) ->
                        suggestAllies(plugin, (Player) ctx.getSource().getSender(), builder))
                    .executes(ctx -> {
                        plugin.getAllianceHomeManager().listHomes(
                            (Player) ctx.getSource().getSender(),
                            StringArgumentType.getString(ctx, "ally")
                        );
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "List shared alliance homes"
        );
    }

    private static void setHome(Player player, String name, SMPCore plugin) {
        plugin.getHomeManager().setHome(
            player,
            name,
            player.getLocation(),
            () -> player.sendMessage(MessageUtil.success(setSuccessMessage(name))),
            () -> player.sendMessage(MessageUtil.error(
                "Home limit reached (<white>" + plugin.getHomeManager().maxHomes(player) + "</white>).")),
            () -> player.sendMessage(MessageUtil.error("Could not save home right now."))
        );
    }

    private static void registerDeleteHomeCommand(Commands commands, SMPCore plugin, String literal) {
        commands.register(
            Commands.literal(literal)
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.home"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestHomes(plugin, (Player) ctx.getSource().getSender(), builder))
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        String name = StringArgumentType.getString(ctx, "name");
                        plugin.getHomeManager().deleteHome(
                            player,
                            name,
                            () -> player.sendMessage(MessageUtil.success(deleteSuccessMessage(name))),
                            () -> player.sendMessage(MessageUtil.error(notFoundMessage(name))),
                            () -> player.sendMessage(MessageUtil.error("Could not delete home right now."))
                        );
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "Delete a saved home"
        );
    }

    private static String setSuccessMessage(String name) {
        return isDefaultHome(name)
            ? "Default home set."
            : "Home <white>" + name + "</white> set.";
    }

    private static String deleteSuccessMessage(String name) {
        return isDefaultHome(name)
            ? "Default home deleted."
            : "Home <white>" + name + "</white> deleted.";
    }

    private static String notFoundMessage(String name) {
        return isDefaultHome(name)
            ? "No default home is set."
            : "No home named <white>" + name + "</white>.";
    }

    private static boolean isDefaultHome(String name) {
        return "home".equalsIgnoreCase(name);
    }

    private static CompletableFuture<Suggestions> suggestHomes(SMPCore plugin, Player player, SuggestionsBuilder builder) {
        List<String> names = new ArrayList<>(plugin.getHomeManager().cachedHomeNames(player.getUniqueId()));
        if (!names.contains("home")) {
            names.add("home");
        }
        return CommandSuggestionUtil.suggestMatching(builder, names);
    }

    private static CompletableFuture<Suggestions> suggestAllianceHomes(
        SMPCore plugin,
        Player player,
        SuggestionsBuilder builder
    ) {
        return CommandSuggestionUtil.suggestMatching(
            builder,
            plugin.getAllianceHomeManager().cachedHomeNames(player.getUniqueId())
        );
    }

    private static CompletableFuture<Suggestions> suggestAllies(
        SMPCore plugin,
        Player player,
        SuggestionsBuilder builder
    ) {
        return CommandSuggestionUtil.suggestMatching(
            builder,
            plugin.getTeamManager().alliedTeamNames(player.getUniqueId())
        );
    }

    private static void sendAllianceResult(
        SMPCore plugin,
        Player player,
        CompletableFuture<AllianceHomeManager.ActionResult> future
    ) {
        future.whenComplete((result, error) -> {
            Runnable send = () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (error != null || result == null) {
                    player.sendMessage(MessageUtil.error("Alliance homes could not be updated right now."));
                    return;
                }
                player.sendMessage(result.success()
                    ? MessageUtil.success(result.message())
                    : MessageUtil.error(result.message()));
            };
            if (Bukkit.isPrimaryThread()) {
                send.run();
            } else if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, send);
            }
        });
    }
}
