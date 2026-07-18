package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.game.SpinBetManager;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
public final class SpinBetCommand {

    private SpinBetCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("spinbet")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.spinbet"))
                .executes(ctx -> {
                    sendUsage((Player) ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("help")
                    .executes(ctx -> {
                        sendUsage((Player) ctx.getSource().getSender());
                        return Command.SINGLE_SUCCESS;
                    }))
                .then(Commands.literal("cancel")
                    .executes(ctx -> runCancel(plugin, (Player) ctx.getSource().getSender())))
                .then(Commands.literal("claim")
                    .executes(ctx -> runClaim(plugin, (Player) ctx.getSource().getSender())))
                .then(Commands.literal("accept")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestPendingInvites(plugin, (Player) ctx.getSource().getSender(), builder))
                        .executes(ctx -> runAccept(
                            plugin,
                            (Player) ctx.getSource().getSender(),
                            StringArgumentType.getString(ctx, "player")
                        ))))
                .then(Commands.literal("deny")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestPendingInvites(plugin, (Player) ctx.getSource().getSender(), builder))
                        .executes(ctx -> runDeny(
                            plugin,
                            (Player) ctx.getSource().getSender(),
                            StringArgumentType.getString(ctx, "player")
                        ))))
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests(SpinBetCommand::suggestOnlinePlayers)
                    .executes(ctx -> runChallenge(
                        plugin,
                        (Player) ctx.getSource().getSender(),
                        StringArgumentType.getString(ctx, "player")
                    )))
                .build(),
            "Bet held items on a red/green spin",
            List.of("itembet")
        );
    }

    private static int runChallenge(SMPCore plugin, Player player, String targetName) {
        SpinBetManager manager = plugin.getSpinBetManager();
        if (manager == null) {
            player.sendMessage(MessageUtil.error("Spin bet is not ready yet."));
            return 0;
        }
        return manager.createInvite(player, targetName) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int runAccept(SMPCore plugin, Player player, String challengerName) {
        SpinBetManager manager = plugin.getSpinBetManager();
        if (manager == null) {
            player.sendMessage(MessageUtil.error("Spin bet is not ready yet."));
            return 0;
        }
        return manager.acceptInvite(player, challengerName) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int runDeny(SMPCore plugin, Player player, String challengerName) {
        SpinBetManager manager = plugin.getSpinBetManager();
        if (manager == null) {
            player.sendMessage(MessageUtil.error("Spin bet is not ready yet."));
            return 0;
        }
        return manager.denyInvite(player, challengerName) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int runCancel(SMPCore plugin, Player player) {
        SpinBetManager manager = plugin.getSpinBetManager();
        if (manager == null) {
            player.sendMessage(MessageUtil.error("Spin bet is not ready yet."));
            return 0;
        }
        return manager.cancelInvite(player) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int runClaim(SMPCore plugin, Player player) {
        SpinBetManager manager = plugin.getSpinBetManager();
        if (manager == null) {
            player.sendMessage(MessageUtil.error("Spin bet is not ready yet."));
            return 0;
        }
        return manager.claim(player, true) ? Command.SINGLE_SUCCESS : 0;
    }

    private static void sendUsage(Player player) {
        player.sendMessage(MessageUtil.info("<white>/spinbet <player></white> - invite someone."));
        player.sendMessage(MessageUtil.info("<white>/spinbet accept <player></white> - lock and review both wagers."));
        player.sendMessage(MessageUtil.info("Both main-hand items are locked until you confirm or deny."));
    }

    private static CompletableFuture<Suggestions> suggestOnlinePlayers(
        com.mojang.brigadier.context.CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack> ctx,
        SuggestionsBuilder builder
    ) {
        String remaining = builder.getRemainingLowerCase();
        Player sender = ctx.getSource().getSender() instanceof Player player ? player : null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (sender != null && player.getUniqueId().equals(sender.getUniqueId())) {
                continue;
            }
            String name = player.getName();
            if (remaining.isBlank() || name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPendingInvites(SMPCore plugin, Player player, SuggestionsBuilder builder) {
        SpinBetManager manager = plugin.getSpinBetManager();
        if (manager == null) {
            return builder.buildFuture();
        }
        String remaining = builder.getRemainingLowerCase();
        for (String name : manager.pendingChallengerNames(player)) {
            if (remaining.isBlank() || name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }
}
