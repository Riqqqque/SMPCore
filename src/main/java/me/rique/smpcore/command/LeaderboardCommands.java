package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.leaderboard.LeaderboardManager;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
public final class LeaderboardCommands {

    private LeaderboardCommands() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("leaderboards")
                .requires(src -> src.getSender().hasPermission("smpcore.leaderboard"))
                .executes(ctx -> openOverview(plugin, ctx.getSource().getSender()))
                .then(Commands.argument("type", StringArgumentType.word())
                    .suggests(LeaderboardCommands::suggestTypes)
                    .executes(ctx -> openType(
                        plugin,
                        ctx.getSource().getSender(),
                        StringArgumentType.getString(ctx, "type")
                    )))
                .build(),
            "Open server leaderboards",
            List.of("leaderboard", "lb", "topstats")
        );
    }

    private static int openOverview(SMPCore plugin, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Console cannot open the leaderboard menu."));
            return 0;
        }
        if (plugin.getLeaderboardManager() == null) {
            sender.sendMessage(MessageUtil.error("Leaderboards are not ready yet."));
            return 0;
        }
        plugin.getLeaderboardManager().openOverviewMenu(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int openType(SMPCore plugin, CommandSender sender, String input) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Console cannot open the leaderboard menu."));
            return 0;
        }
        if (plugin.getLeaderboardManager() == null) {
            sender.sendMessage(MessageUtil.error("Leaderboards are not ready yet."));
            return 0;
        }
        LeaderboardManager.LeaderboardType type = LeaderboardManager.LeaderboardType.fromInput(input);
        if (type == null) {
            sender.sendMessage(MessageUtil.error("Unknown leaderboard type."));
            return 0;
        }
        plugin.getLeaderboardManager().openLeaderboardMenu(player, type);
        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> suggestTypes(com.mojang.brigadier.context.CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack> ctx, SuggestionsBuilder builder) {
        builder.suggest("player_kills");
        builder.suggest("deaths");
        builder.suggest("boss_kills");
        builder.suggest("boss_damage");
        builder.suggest("boss_fights");
        builder.suggest("mob_kills");
        return builder.buildFuture();
    }
}
