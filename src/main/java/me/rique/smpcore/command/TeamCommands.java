package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.team.TeamManager;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.List;

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
            List.of("teams")
        );
    }

    private static void sendHelp(Player player, TeamManager teamManager) {
        for (String line : teamManager.teamHelpLines()) {
            player.sendMessage(MessageUtil.prefixedRaw(line));
        }
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
