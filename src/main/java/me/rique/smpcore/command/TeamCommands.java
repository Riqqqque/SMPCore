package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.team.TeamManager;
import me.rique.smpcore.util.CommandSuggestionUtil;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
public final class TeamCommands {

    private TeamCommands() {}

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("team")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.team"))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    sendHelp(player, plugin.getTeamManager());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("list")
                    .executes(ctx -> openTeams(ctx.getSource().getSender() instanceof Player player ? player : null, plugin, null)))
                .then(Commands.literal("search")
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> suggestTeams(plugin, builder))
                        .executes(ctx -> openTeams(
                            ctx.getSource().getSender() instanceof Player player ? player : null,
                            plugin,
                            StringArgumentType.getString(ctx, "name")
                        ))))
                .then(Commands.literal("colors")
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        for (String line : plugin.getTeamManager().teamColorLines()) {
                            player.sendMessage(MessageUtil.prefixedRaw(line));
                        }
                        return Command.SINGLE_SUCCESS;
                    }))
                .then(Commands.literal("create")
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            Player player = (Player) ctx.getSource().getSender();
                            TeamCreateInput input = parseCreateInput(StringArgumentType.getString(ctx, "name"), plugin.getTeamManager());
                            plugin.getTeamManager().createTeam(player, input.name(), input.color())
                                .thenAccept(error ->
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        if (!player.isOnline()) return;
                                        if (error != null) {
                                            player.sendMessage(MessageUtil.error(error));
                                            return;
                                        }
                                        String colorText = input.color() == null ? "" : " with color <white>" + input.color() + "</white>";
                                        player.sendMessage(MessageUtil.success("Team <white>" + input.name() + "</white> created" + colorText + "."));
                                    })
                                )
                                .exceptionally(ex -> {
                                    plugin.getLogger().severe("team create failed: " + ex.getMessage());
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        if (player.isOnline()) {
                                            player.sendMessage(MessageUtil.error("Could not create team right now."));
                                        }
                                    });
                                    return null;
                                });
                            return Command.SINGLE_SUCCESS;
                        })))
                .then(Commands.literal("color")
                    .then(Commands.argument("color", StringArgumentType.word())
                        .suggests((ctx, builder) -> CommandSuggestionUtil.suggestMatching(builder, plugin.getTeamManager().teamColorIds()))
                        .executes(ctx -> {
                            Player player = (Player) ctx.getSource().getSender();
                            String color = StringArgumentType.getString(ctx, "color");
                            plugin.getTeamManager().changeTeamColor(player, color)
                                .thenAccept(error ->
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        if (!player.isOnline()) return;
                                        if (error != null) {
                                            player.sendMessage(MessageUtil.error(error));
                                            return;
                                        }
                                        player.sendMessage(MessageUtil.success("Team color changed to <white>" + color + "</white>."));
                                    })
                                )
                                .exceptionally(ex -> {
                                    plugin.getLogger().severe("team color failed: " + ex.getMessage());
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        if (player.isOnline()) {
                                            player.sendMessage(MessageUtil.error("Could not change team color right now."));
                                        }
                                    });
                                    return null;
                                });
                            return Command.SINGLE_SUCCESS;
                        })))
                .then(Commands.literal("rename")
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> renameTeam(ctx.getSource().getSender() instanceof Player player ? player : null, plugin, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("name")
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> renameTeam(ctx.getSource().getSender() instanceof Player player ? player : null, plugin, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("invite")
                    .then(Commands.argument("target", ArgumentTypes.player())
                        .executes(ctx -> {
                            Player sender = (Player) ctx.getSource().getSender();
                            List<Player> resolved = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                                .resolve(ctx.getSource());
                            if (resolved.isEmpty()) {
                                sender.sendMessage(MessageUtil.error("Player not found."));
                                return 0;
                            }

                            Player target = resolved.get(0);
                            String error = plugin.getTeamManager().invite(sender, target);
                            if (error != null) {
                                sender.sendMessage(MessageUtil.error(error));
                                return 0;
                            }
                            sender.sendMessage(MessageUtil.success("Team invite sent to <white>" + target.getName() + "</white>."));
                            return Command.SINGLE_SUCCESS;
                        })))
                .then(Commands.literal("accept")
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        plugin.getTeamManager().acceptInvite(player)
                            .thenAccept(error ->
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    if (!player.isOnline()) return;
                                    if (error != null) {
                                        player.sendMessage(MessageUtil.error(error));
                                        return;
                                    }
                                    player.sendMessage(MessageUtil.success("Team invite accepted."));
                                })
                            )
                            .exceptionally(ex -> {
                                plugin.getLogger().severe("team accept failed: " + ex.getMessage());
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    if (player.isOnline()) {
                                        player.sendMessage(MessageUtil.error("Could not accept that invite right now."));
                                    }
                                });
                                return null;
                            });
                        return Command.SINGLE_SUCCESS;
                    }))
                .then(Commands.literal("deny")
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        String error = plugin.getTeamManager().denyInvite(player);
                        if (error != null) {
                            player.sendMessage(MessageUtil.error(error));
                            return 0;
                        }
                        player.sendMessage(MessageUtil.info("Team invite denied."));
                        return Command.SINGLE_SUCCESS;
                    }))
                .then(Commands.literal("ally")
                    .then(Commands.literal("add")
                        .then(Commands.argument("team", StringArgumentType.greedyString())
                            .suggests((ctx, builder) -> suggestTeams(plugin, builder))
                            .executes(ctx -> {
                                Player player = (Player) ctx.getSource().getSender();
                                String team = StringArgumentType.getString(ctx, "team");
                                String error = plugin.getTeamManager().requestAlly(player, team);
                                if (error != null) {
                                    player.sendMessage(MessageUtil.error(error));
                                    return 0;
                                }
                                player.sendMessage(MessageUtil.success("Alliance request sent to <white>" + team + "</white>."));
                                return Command.SINGLE_SUCCESS;
                            })))
                    .then(Commands.literal("accept")
                        .then(Commands.argument("team", StringArgumentType.greedyString())
                            .suggests((ctx, builder) -> suggestTeams(plugin, builder))
                            .executes(ctx -> {
                                Player player = (Player) ctx.getSource().getSender();
                                String team = StringArgumentType.getString(ctx, "team");
                                plugin.getTeamManager().acceptAlly(player, team)
                                    .thenAccept(error ->
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            if (!player.isOnline()) return;
                                            if (error != null) {
                                                player.sendMessage(MessageUtil.error(error));
                                                return;
                                            }
                                            player.sendMessage(MessageUtil.success("Alliance accepted."));
                                        })
                                    )
                                    .exceptionally(ex -> {
                                        plugin.getLogger().severe("team ally accept failed: " + ex.getMessage());
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            if (player.isOnline()) {
                                                player.sendMessage(MessageUtil.error("Could not accept that alliance right now."));
                                            }
                                        });
                                        return null;
                                    });
                                return Command.SINGLE_SUCCESS;
                            })))
                    .then(Commands.literal("deny")
                        .then(Commands.argument("team", StringArgumentType.greedyString())
                            .suggests((ctx, builder) -> suggestTeams(plugin, builder))
                            .executes(ctx -> {
                                Player player = (Player) ctx.getSource().getSender();
                                String team = StringArgumentType.getString(ctx, "team");
                                String error = plugin.getTeamManager().denyAlly(player, team);
                                if (error != null) {
                                    player.sendMessage(MessageUtil.error(error));
                                    return 0;
                                }
                                player.sendMessage(MessageUtil.info("Alliance request denied."));
                                return Command.SINGLE_SUCCESS;
                            })))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("team", StringArgumentType.greedyString())
                            .suggests((ctx, builder) -> suggestTeams(plugin, builder))
                            .executes(ctx -> {
                                Player player = (Player) ctx.getSource().getSender();
                                String team = StringArgumentType.getString(ctx, "team");
                                plugin.getTeamManager().removeAlly(player, team)
                                    .thenAccept(error ->
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            if (!player.isOnline()) return;
                                            if (error != null) {
                                                player.sendMessage(MessageUtil.error(error));
                                                return;
                                            }
                                            player.sendMessage(MessageUtil.success("Alliance removed."));
                                        })
                                    )
                                    .exceptionally(ex -> {
                                        plugin.getLogger().severe("team ally remove failed: " + ex.getMessage());
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            if (player.isOnline()) {
                                                player.sendMessage(MessageUtil.error("Could not remove that alliance right now."));
                                            }
                                        });
                                        return null;
                                    });
                                return Command.SINGLE_SUCCESS;
                            }))))
                .then(Commands.literal("allies")
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        player.sendMessage(plugin.getTeamManager().alliesMessage(player.getUniqueId()));
                        return Command.SINGLE_SUCCESS;
                    }))
                .then(Commands.literal("leave")
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        plugin.getTeamManager().leaveTeam(player)
                            .thenAccept(error ->
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    if (!player.isOnline()) return;
                                    if (error != null) {
                                        player.sendMessage(MessageUtil.error(error));
                                        return;
                                    }
                                    player.sendMessage(MessageUtil.success("You left your team."));
                                })
                            )
                            .exceptionally(ex -> {
                                plugin.getLogger().severe("team leave failed: " + ex.getMessage());
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    if (player.isOnline()) {
                                        player.sendMessage(MessageUtil.error("Could not leave team right now."));
                                    }
                                });
                                return null;
                            });
                        return Command.SINGLE_SUCCESS;
                    }))
                .then(Commands.literal("disband")
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        plugin.getTeamManager().disbandTeam(player)
                            .thenAccept(error ->
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    if (!player.isOnline()) return;
                                    if (error != null) {
                                        player.sendMessage(MessageUtil.error(error));
                                        return;
                                    }
                                    player.sendMessage(MessageUtil.success("Team disbanded."));
                                })
                            )
                            .exceptionally(ex -> {
                                plugin.getLogger().severe("team disband failed: " + ex.getMessage());
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    if (player.isOnline()) {
                                        player.sendMessage(MessageUtil.error("Could not disband team right now."));
                                    }
                                });
                                return null;
                            });
                        return Command.SINGLE_SUCCESS;
                    }))
                .then(Commands.literal("info")
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        player.sendMessage(plugin.getTeamManager().infoMessage(player.getUniqueId()));
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "Manage player teams",
            List.of()
        );

        commands.register(
            Commands.literal("teams")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.team"))
                .executes(ctx -> openTeams(ctx.getSource().getSender() instanceof Player player ? player : null, plugin, null))
                .then(Commands.argument("search", StringArgumentType.greedyString())
                    .suggests((ctx, builder) -> suggestTeams(plugin, builder))
                    .executes(ctx -> openTeams(
                        ctx.getSource().getSender() instanceof Player player ? player : null,
                        plugin,
                        StringArgumentType.getString(ctx, "search")
                    )))
                .build(),
            "Browse server teams",
            List.of("teamlist")
        );
    }

    private static void sendHelp(Player player, TeamManager teamManager) {
        for (String line : teamManager.teamHelpLines()) {
            player.sendMessage(MessageUtil.prefixedRaw(line));
        }
    }

    private static int renameTeam(Player player, SMPCore plugin, String name) {
        if (player == null) {
            return 0;
        }
        plugin.getTeamManager().renameTeam(player, name)
            .thenAccept(error ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (error != null) {
                        player.sendMessage(MessageUtil.error(error));
                        return;
                    }
                    player.sendMessage(MessageUtil.success("Team renamed to <white>" + name + "</white>."));
                })
            )
            .exceptionally(ex -> {
                plugin.getLogger().severe("team rename failed: " + ex.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(MessageUtil.error("Could not rename team right now."));
                    }
                });
                return null;
            });
        return Command.SINGLE_SUCCESS;
    }

    private static int openTeams(Player player, SMPCore plugin, String search) {
        if (player == null) {
            return 0;
        }
        plugin.getTeamManager().openTeamsMenu(player, search, false);
        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> suggestTeams(SMPCore plugin, SuggestionsBuilder builder) {
        String input = builder.getRemainingLowerCase();
        for (String name : plugin.getTeamManager().teamNames()) {
            if (name.toLowerCase(Locale.ROOT).startsWith(input)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    private static TeamCreateInput parseCreateInput(String raw, TeamManager teamManager) {
        String input = raw == null ? "" : raw.trim();
        if (input.length() >= 2 && (input.startsWith("\"") || input.startsWith("'"))) {
            char quote = input.charAt(0);
            int end = input.indexOf(quote, 1);
            if (end > 0) {
                String name = input.substring(1, end).trim();
                String rest = input.substring(end + 1).trim();
                String color = rest.isBlank() ? null : rest;
                return new TeamCreateInput(name, color);
            }
        }

        String[] parts = input.split("\\s+");
        if (parts.length >= 2) {
            String last = parts[parts.length - 1].toLowerCase(Locale.ROOT);
            if (teamManager.isTeamColor(last)) {
                String name = input.substring(0, input.length() - parts[parts.length - 1].length()).trim();
                return new TeamCreateInput(name, last);
            }
        }
        return new TeamCreateInput(stripWrappingQuotes(input), null);
    }

    private static String stripWrappingQuotes(String input) {
        if (input.length() >= 2) {
            if ((input.startsWith("\"") && input.endsWith("\"")) || (input.startsWith("'") && input.endsWith("'"))) {
                return input.substring(1, input.length() - 1).trim();
            }
        }
        return input;
    }

    private record TeamCreateInput(String name, String color) {}
}
