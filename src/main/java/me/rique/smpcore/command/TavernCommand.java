package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.tavern.TavernManager;
import me.rique.smpcore.tavern.TavernManager.StationType;
import me.rique.smpcore.tavern.TavernManager.TavernGame;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.util.CommandSuggestionUtil;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class TavernCommand {

    private TavernCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("tavernadmin")
                .requires(source -> source.getSender().hasPermission("smpcore.tavern.admin"))
                .executes(ctx -> usage(ctx.getSource().getSender()))
                .then(Commands.literal("set")
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestTypes(builder))
                        .executes(ctx -> set(plugin, ctx.getSource().getSender(), StringArgumentType.getString(ctx, "type"), false))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestTypes(builder))
                        .executes(ctx -> set(plugin, ctx.getSource().getSender(), StringArgumentType.getString(ctx, "type"), true))))
                .then(Commands.literal("list")
                    .executes(ctx -> list(plugin, ctx.getSource().getSender())))
                .then(Commands.literal("leaderboard")
                    .then(Commands.literal("spawn")
                        .then(Commands.argument("game", StringArgumentType.word())
                            .suggests((ctx, builder) -> CommandSuggestionUtil.suggestMatching(builder, "slots", "cards", "darts", "roulette"))
                            .executes(ctx -> spawnLeaderboard(plugin, ctx.getSource().getSender(), StringArgumentType.getString(ctx, "game")))))
                    .then(Commands.literal("remove")
                        .executes(ctx -> removeLeaderboard(plugin, ctx.getSource().getSender()))))
                .then(Commands.literal("help")
                    .executes(ctx -> usage(ctx.getSource().getSender())))
                .build(),
            "Place and manage tavern stations",
            List.of("tavernsetup", "tavernstations")
        );
    }

    private static int set(SMPCore plugin, CommandSender sender, String rawType, boolean remove) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Must be a player."));
            return 0;
        }
        TavernManager manager = plugin.getTavernManager();
        StationType type = StationType.byId(rawType);
        if (manager == null || type == null) {
            player.sendMessage(MessageUtil.error("Use slots, table, darts, or rumors."));
            return 0;
        }
        Block target = player.getTargetBlockExact(8);
        if (target == null) {
            player.sendMessage(MessageUtil.warn("Look directly at the block you want to configure."));
            return 0;
        }
        boolean changed = remove
            ? manager.removeStation(type, target.getLocation())
            : manager.setStation(type, target.getLocation());
        if (!changed) {
            player.sendMessage(MessageUtil.warn(remove ? "That block is not registered as this station." : "That station is already registered."));
            return 0;
        }
        player.sendMessage(MessageUtil.success((remove ? "Removed " : "Placed ") + "<white>" + type.id() + "</white> at "
            + target.getX() + ", " + target.getY() + ", " + target.getZ() + "."));
        return Command.SINGLE_SUCCESS;
    }

    private static int list(SMPCore plugin, CommandSender sender) {
        TavernManager manager = plugin.getTavernManager();
        if (manager == null) return 0;
        for (StationType type : StationType.values()) {
            sender.sendMessage(MessageUtil.info(type.id() + ": <white>" + manager.stationCount(type) + "</white>."));
        }
        sender.sendMessage(MessageUtil.info("leaderboards: <white>" + manager.leaderboardCount() + "</white>."));
        return Command.SINGLE_SUCCESS;
    }

    private static int spawnLeaderboard(SMPCore plugin, CommandSender sender, String rawGame) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Must be a player."));
            return 0;
        }
        TavernGame game = TavernGame.byId(rawGame);
        if (game == null) {
            player.sendMessage(MessageUtil.error("Use slots, cards, darts, or roulette."));
            return 0;
        }
        java.util.UUID id = plugin.getTavernManager().createLeaderboard(player.getLocation().add(0.0, 2.25, 0.0), game);
        if (id == null) return 0;
        player.sendMessage(MessageUtil.success("Spawned the <white>" + game.id() + " champions</white> board above you. Any older board for that game was replaced."));
        return Command.SINGLE_SUCCESS;
    }

    private static int removeLeaderboard(SMPCore plugin, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Must be a player."));
            return 0;
        }
        if (!plugin.getTavernManager().removeNearestLeaderboard(player.getEyeLocation(), 6.0)) {
            player.sendMessage(MessageUtil.warn("No tavern leaderboard is within 6 blocks."));
            return 0;
        }
        player.sendMessage(MessageUtil.success("Removed the nearest tavern leaderboard."));
        return Command.SINGLE_SUCCESS;
    }

    private static int usage(CommandSender sender) {
        sender.sendMessage(MessageUtil.info("<white>/tavernadmin set \\<slots|table|darts|rumors></white> while looking at a block."));
        sender.sendMessage(MessageUtil.info("<white>/tavernadmin leaderboard spawn \\<slots|cards|darts|roulette></white> - wins and playtime for that game."));
        sender.sendMessage(MessageUtil.info("<white>/tavernadmin leaderboard remove</white> while standing nearby."));
        sender.sendMessage(MessageUtil.info("Use <white>/brewmaster spawn</white> and <white>/adventurer spawn</white> for the quest NPCs."));
        return Command.SINGLE_SUCCESS;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestTypes(
        com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        return CommandSuggestionUtil.suggestMatching(builder, "slots", "table", "darts", "rumors");
    }

}
