package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.duel.DuelManager;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class DuelCommand {

    private DuelCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("duel")
                .requires(source -> source.getSender().hasPermission("smpcore.duel.use"))
                .executes(context -> open(plugin, context.getSource().getSender()))
                .then(Commands.literal("queue")
                    .then(Commands.argument("rounds", IntegerArgumentType.integer(1, 3))
                        .then(Commands.argument("mode", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                builder.suggest("open");
                                builder.suggest("noheal");
                                builder.suggest("melee");
                                return builder.buildFuture();
                            })
                            .executes(context -> queue(
                                plugin,
                                context.getSource().getSender(),
                                IntegerArgumentType.getInteger(context, "rounds"),
                                StringArgumentType.getString(context, "mode"),
                                null
                            ))
                            .then(Commands.argument("team-size", IntegerArgumentType.integer(1, 3))
                                .executes(context -> queue(
                                    plugin,
                                    context.getSource().getSender(),
                                    IntegerArgumentType.getInteger(context, "rounds"),
                                    StringArgumentType.getString(context, "mode"),
                                    IntegerArgumentType.getInteger(context, "team-size")
                                ))))))
                .then(Commands.literal("find")
                    .executes(context -> find(plugin, context.getSource().getSender())))
                .then(Commands.literal("challenge")
                    .then(Commands.argument("target", ArgumentTypes.player())
                        .executes(context -> challengeSelected(
                            plugin,
                            context.getSource().getSender(),
                            firstTarget(context.getArgument("target", PlayerSelectorArgumentResolver.class), context.getSource())
                        ))
                        .then(Commands.argument("rounds", IntegerArgumentType.integer(1, 3))
                            .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    builder.suggest("open");
                                    builder.suggest("noheal");
                                    builder.suggest("melee");
                                    return builder.buildFuture();
                                })
                                .executes(context -> challenge(
                                    plugin,
                                    context.getSource().getSender(),
                                    firstTarget(context.getArgument("target", PlayerSelectorArgumentResolver.class), context.getSource()),
                                    IntegerArgumentType.getInteger(context, "rounds"),
                                    StringArgumentType.getString(context, "mode")
                                ))))))
                .then(Commands.literal("party")
                    .executes(context -> openParty(plugin, context.getSource().getSender()))
                    .then(Commands.literal("invite")
                        .then(Commands.argument("player", ArgumentTypes.player())
                            .executes(context -> partyInvite(
                                plugin,
                                context.getSource().getSender(),
                                firstTarget(context.getArgument("player", PlayerSelectorArgumentResolver.class), context.getSource())
                            ))))
                    .then(Commands.literal("accept")
                        .then(Commands.argument("captain", ArgumentTypes.player())
                            .executes(context -> partyAccept(
                                plugin,
                                context.getSource().getSender(),
                                firstTarget(context.getArgument("captain", PlayerSelectorArgumentResolver.class), context.getSource())
                            ))))
                    .then(Commands.literal("deny").executes(context -> partyDeny(plugin, context.getSource().getSender())))
                    .then(Commands.literal("leave").executes(context -> partyLeave(plugin, context.getSource().getSender())))
                    .then(Commands.literal("disband").executes(context -> partyDisband(plugin, context.getSource().getSender())))
                    .then(Commands.literal("kick")
                        .then(Commands.argument("player", ArgumentTypes.player())
                            .executes(context -> partyKick(
                                plugin,
                                context.getSource().getSender(),
                                firstTarget(context.getArgument("player", PlayerSelectorArgumentResolver.class), context.getSource())
                            ))))
                    .then(Commands.literal("status").executes(context -> partyStatus(plugin, context.getSource().getSender()))))
                .then(Commands.literal("accept")
                    .executes(context -> acceptPending(plugin, context.getSource().getSender()))
                    .then(Commands.argument("challenger", ArgumentTypes.player())
                        .executes(context -> accept(
                            plugin,
                            context.getSource().getSender(),
                            firstTarget(context.getArgument("challenger", PlayerSelectorArgumentResolver.class), context.getSource())
                        ))))
                .then(Commands.literal("deny").executes(context -> deny(plugin, context.getSource().getSender())))
                .then(Commands.literal("leave").executes(context -> leave(plugin, context.getSource().getSender())))
                .then(Commands.literal("spectate").executes(context -> spectate(plugin, context.getSource().getSender())))
                .then(Commands.literal("bet")
                    .executes(context -> bet(plugin, context.getSource().getSender()))
                    .then(Commands.literal("essence")
                        .then(Commands.argument("amount", LongArgumentType.longArg(1L, 9_999_999_999L))
                            .executes(context -> betEssence(
                                plugin,
                                context.getSource().getSender(),
                                LongArgumentType.getLong(context, "amount")
                            ))))
                    .then(Commands.literal("item")
                        .executes(context -> betItem(plugin, context.getSource().getSender(), null))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(context -> betItem(
                                plugin,
                                context.getSource().getSender(),
                                IntegerArgumentType.getInteger(context, "amount")
                            )))))
                .then(Commands.literal("leaderboard")
                    .then(Commands.literal("wins").executes(context -> leaderboard(plugin, context.getSource().getSender(), false)))
                    .then(Commands.literal("bets").executes(context -> leaderboard(plugin, context.getSource().getSender(), true))))
                .then(Commands.literal("admin")
                    .requires(source -> source.getSender().hasPermission("smpcore.duel.admin"))
                    .then(Commands.literal("set")
                        .then(Commands.argument("point", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                for (String point : List.of("lobby", "fighter1", "fighter1b", "fighter1c", "fighter2", "fighter2b", "fighter2c", "spectator", "corner1", "corner2")) builder.suggest(point);
                                return builder.buildFuture();
                            })
                            .executes(context -> setPoint(plugin, context.getSource().getSender(), StringArgumentType.getString(context, "point")))))
                    .then(Commands.literal("status").executes(context -> status(plugin, context.getSource().getSender())))
                    .then(Commands.literal("forcestop").executes(context -> forceStop(plugin, context.getSource().getSender())))
                    .then(Commands.literal("leaderboard")
                        .then(Commands.literal("wins").executes(context -> spawnBoard(plugin, context.getSource().getSender(), DuelManager.BoardType.WINS)))
                        .then(Commands.literal("bets").executes(context -> spawnBoard(plugin, context.getSource().getSender(), DuelManager.BoardType.BETS))))
                    .then(Commands.literal("removeleaderboard").executes(context -> removeBoard(plugin, context.getSource().getSender()))))
                .build(),
            "Open matchmaking, betting, and duel spectating",
            List.of("duels", "arena")
        );
    }

    private static int open(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.openMainMenu(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int queue(SMPCore plugin, CommandSender sender, int rounds, String modeInput, Integer teamSize) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        DuelManager.DuelMode mode = DuelManager.modeByInput(modeInput);
        if (mode == null) {
            sender.sendMessage(MessageUtil.error("Mode must be open, noheal, or melee."));
            return 0;
        }
        if (teamSize == null) manager.joinQueue(player, rounds, mode);
        else manager.joinQueue(player, rounds, mode, teamSize);
        return Command.SINGLE_SUCCESS;
    }

    private static int find(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.joinSelectedQueue(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int openParty(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.openPartyMenu(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int partyInvite(SMPCore plugin, CommandSender sender, Player target) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || target == null || manager == null) {
            if (target == null) sender.sendMessage(MessageUtil.error("Player not found."));
            return 0;
        }
        manager.inviteToParty(player, target);
        return Command.SINGLE_SUCCESS;
    }

    private static int partyAccept(SMPCore plugin, CommandSender sender, Player captain) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || captain == null || manager == null) {
            if (captain == null) sender.sendMessage(MessageUtil.error("Captain not found."));
            return 0;
        }
        manager.acceptPartyInvite(player, captain);
        return Command.SINGLE_SUCCESS;
    }

    private static int partyDeny(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.declinePartyInvite(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int partyLeave(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.leaveParty(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int partyDisband(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.disbandParty(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int partyKick(SMPCore plugin, CommandSender sender, Player target) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || target == null || manager == null) {
            if (target == null) sender.sendMessage(MessageUtil.error("Player not found."));
            return 0;
        }
        manager.kickFromParty(player, target);
        return Command.SINGLE_SUCCESS;
    }

    private static int partyStatus(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.sendPartyStatus(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int challenge(SMPCore plugin, CommandSender sender, Player target, int rounds, String modeInput) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        DuelManager.DuelMode mode = DuelManager.modeByInput(modeInput);
        if (player == null || target == null || manager == null || mode == null) {
            if (target == null) sender.sendMessage(MessageUtil.error("Player not found."));
            else if (mode == null) sender.sendMessage(MessageUtil.error("Mode must be open, noheal, or melee."));
            return 0;
        }
        manager.challenge(player, target, rounds, mode);
        return Command.SINGLE_SUCCESS;
    }

    private static int challengeSelected(SMPCore plugin, CommandSender sender, Player target) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || target == null || manager == null) {
            if (target == null) sender.sendMessage(MessageUtil.error("Player not found."));
            return 0;
        }
        manager.challengeSelected(player, target);
        return Command.SINGLE_SUCCESS;
    }

    private static int accept(SMPCore plugin, CommandSender sender, Player challenger) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || challenger == null || manager == null) {
            if (challenger == null) sender.sendMessage(MessageUtil.error("Challenger not found."));
            return 0;
        }
        manager.acceptChallenge(player, challenger);
        return Command.SINGLE_SUCCESS;
    }

    private static int acceptPending(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.acceptPendingChallenge(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int deny(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.denyChallenge(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int leave(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.leave(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int spectate(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.spectate(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int bet(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.openBetMenu(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int betEssence(SMPCore plugin, CommandSender sender, long amount) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.placeSelectedEssenceBet(player, amount);
        return Command.SINGLE_SUCCESS;
    }

    private static int betItem(SMPCore plugin, CommandSender sender, Integer amount) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        int selectedAmount = amount == null ? player.getInventory().getItemInMainHand().getAmount() : amount;
        manager.placeSelectedItemBet(player, selectedAmount);
        return Command.SINGLE_SUCCESS;
    }

    private static int leaderboard(SMPCore plugin, CommandSender sender, boolean bets) {
        Player player = player(sender);
        if (player == null || plugin.getLeaderboardManager() == null) return 0;
        plugin.getLeaderboardManager().openLeaderboardMenu(player, bets
            ? me.rique.smpcore.leaderboard.LeaderboardManager.LeaderboardType.DUEL_BET_WINS
            : me.rique.smpcore.leaderboard.LeaderboardManager.LeaderboardType.DUEL_WINS);
        return Command.SINGLE_SUCCESS;
    }

    private static int setPoint(SMPCore plugin, CommandSender sender, String point) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.setArenaPoint(player, point);
        return Command.SINGLE_SUCCESS;
    }

    private static int status(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.sendAdminStatus(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int forceStop(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.forceStop(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int spawnBoard(SMPCore plugin, CommandSender sender, DuelManager.BoardType type) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.spawnLeaderboard(player, type);
        return Command.SINGLE_SUCCESS;
    }

    private static int removeBoard(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        DuelManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.removeNearestLeaderboard(player);
        return Command.SINGLE_SUCCESS;
    }

    private static DuelManager manager(SMPCore plugin, CommandSender sender) {
        DuelManager manager = plugin.getDuelManager();
        if (manager == null) sender.sendMessage(MessageUtil.error("Duel system is not ready yet."));
        return manager;
    }

    private static Player player(CommandSender sender) {
        if (sender instanceof Player player) return player;
        sender.sendMessage(MessageUtil.error("Must be a player."));
        return null;
    }

    private static Player firstTarget(PlayerSelectorArgumentResolver resolver, io.papermc.paper.command.brigadier.CommandSourceStack source) throws CommandSyntaxException {
        List<Player> players = resolver.resolve(source);
        return players.isEmpty() ? null : players.getFirst();
    }
}
