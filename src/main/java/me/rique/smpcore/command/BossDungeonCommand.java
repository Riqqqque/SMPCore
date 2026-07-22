package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.boss.BossDungeonManager;
import me.rique.smpcore.boss.BossManager;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
public final class BossDungeonCommand {

    private BossDungeonCommand() { }

    public static void register(Commands commands, SMPCore plugin) {
        var root = Commands.literal("bossdungeon")
            .requires(source -> source.getSender().hasPermission("smpcore.dungeon.admin"))
            .executes(context -> status(plugin, context.getSource().getSender()));
        for (String point : new String[]{"entry", "fight", "spectator", "boss", "keeper"}) {
            root.then(Commands.literal("set" + point)
                .executes(context -> set(plugin, context.getSource().getSender(), point)));
        }
        root.then(Commands.literal("enter").executes(context -> teleport(plugin, context.getSource().getSender(), "entry")));
        root.then(Commands.literal("join").executes(context -> joinFight(plugin, context.getSource().getSender())));
        root.then(Commands.literal("spectate").executes(context -> spectate(plugin, context.getSource().getSender())));
        root.then(Commands.literal("reset").executes(context -> reset(plugin, context.getSource().getSender())));
        root.then(Commands.literal("loadouts")
            .executes(context -> BossTestLoadoutCommand.open(plugin, context.getSource().getSender())));
        root.then(Commands.literal("gear")
            .executes(context -> BossTestLoadoutCommand.open(plugin, context.getSource().getSender())));
        root.then(Commands.literal("tp")
            .then(Commands.argument("point", StringArgumentType.word())
                .suggests(BossDungeonCommand::suggestPoints)
                .executes(context -> teleport(plugin, context.getSource().getSender(), StringArgumentType.getString(context, "point")))));
        root.then(Commands.literal("test")
            .then(Commands.argument("boss", StringArgumentType.word())
                .suggests((context, builder) -> suggestBosses(plugin, builder))
                .executes(context -> testBoss(plugin, context.getSource().getSender(), StringArgumentType.getString(context, "boss")))));
        commands.register(root.build(), "Configure the Boss Dungeon", java.util.List.of("bdungeon"));

        commands.register(
            Commands.literal("bossjoin")
                .requires(source -> source.getSender().hasPermission("smpcore.dungeon.use"))
                .then(Commands.literal("accept").executes(context -> invite(plugin, context.getSource().getSender(), true)))
                .then(Commands.literal("deny").executes(context -> invite(plugin, context.getSource().getSender(), false)))
                .build(),
            "Accept or deny a boss fight invitation"
        );
        commands.register(
            Commands.literal("bossqueue")
                .requires(source -> source.getSender().hasPermission("smpcore.dungeon.use"))
                .then(Commands.literal("leave").executes(context -> leaveQueue(plugin, context.getSource().getSender())))
                .build(),
            "Manage your Boss Dungeon queue entry"
        );
    }

    private static int set(SMPCore plugin, CommandSender sender, String point) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Must be a player."));
            return 0;
        }
        BossDungeonManager manager = plugin.getBossDungeonManager();
        if (manager == null || !manager.setLocation(point, player.getLocation())) {
            sender.sendMessage(MessageUtil.error("Stand inside the Boss Dungeon before setting that point."));
            return 0;
        }
        sender.sendMessage(MessageUtil.success("Boss Dungeon <white>" + point + "</white> point updated."));
        return Command.SINGLE_SUCCESS;
    }

    private static int status(SMPCore plugin, CommandSender sender) {
        BossDungeonManager manager = plugin.getBossDungeonManager();
        if (manager == null) return 0;
        sender.sendMessage(MessageUtil.info(manager.queueStatus()));
        for (Map.Entry<String, Location> entry : manager.configuredLocations().entrySet()) {
            Location location = entry.getValue();
            sender.sendMessage(MessageUtil.info("<white>" + entry.getKey() + ":</white> " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ()));
        }
        sender.sendMessage(MessageUtil.info("<white>/bdungeon test <boss></white> - start a no-cost, no-loot test fight."));
        sender.sendMessage(MessageUtil.info("<white>/bdungeon loadouts</white> - equip realistic pre-boss test gear."));
        sender.sendMessage(MessageUtil.info("<white>/bdungeon join</white> | <white>spectate</white> | <white>reset</white>"));
        sender.sendMessage(MessageUtil.info("<white>/bdungeon tp <entry|fight|spectator|boss|keeper></white>"));
        return Command.SINGLE_SUCCESS;
    }

    private static int invite(SMPCore plugin, CommandSender sender, boolean accept) {
        if (!(sender instanceof Player player) || plugin.getBossDungeonManager() == null) return 0;
        plugin.getBossDungeonManager().respondToInvite(player, accept);
        return Command.SINGLE_SUCCESS;
    }

    private static int leaveQueue(SMPCore plugin, CommandSender sender) {
        if (!(sender instanceof Player player) || plugin.getBossDungeonManager() == null) return 0;
        plugin.getBossDungeonManager().leaveQueue(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int testBoss(SMPCore plugin, CommandSender sender, String bossInput) {
        if (!(sender instanceof Player player) || plugin.getBossDungeonManager() == null) {
            sender.sendMessage(MessageUtil.error("Run this command in-game."));
            return 0;
        }
        BossManager.BossType type = BossManager.BossType.fromInput(bossInput);
        if (type == null) {
            sender.sendMessage(MessageUtil.error("Unknown boss. Tab-complete a boss ID."));
            return 0;
        }
        return plugin.getBossDungeonManager().adminStartTest(player, type) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int joinFight(SMPCore plugin, CommandSender sender) {
        if (!(sender instanceof Player player) || plugin.getBossDungeonManager() == null) return 0;
        if (!plugin.getBossDungeonManager().adminJoinFight(player)) {
            sender.sendMessage(MessageUtil.warn("There is no active fight to join."));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int spectate(SMPCore plugin, CommandSender sender) {
        if (!(sender instanceof Player player) || plugin.getBossDungeonManager() == null) return 0;
        return plugin.getBossDungeonManager().adminSpectate(player) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int teleport(SMPCore plugin, CommandSender sender, String point) {
        if (!(sender instanceof Player player) || plugin.getBossDungeonManager() == null) return 0;
        if (!plugin.getBossDungeonManager().adminTeleport(player, point)) {
            sender.sendMessage(MessageUtil.error("Unknown or unavailable dungeon point."));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int reset(SMPCore plugin, CommandSender sender) {
        if (plugin.getBossDungeonManager() == null) return 0;
        int removed = plugin.getBossDungeonManager().adminReset(sender instanceof Player player ? player : null);
        sender.sendMessage(MessageUtil.success("Boss Dungeon reset. Removed <white>" + removed + "</white> active boss(es) and cleared the queue."));
        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> suggestBosses(SMPCore plugin, SuggestionsBuilder builder) {
        if (plugin.getBossManager() != null) plugin.getBossManager().bossCommandOptions().forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPoints(com.mojang.brigadier.context.CommandContext<?> context, SuggestionsBuilder builder) {
        for (String point : new String[]{"entry", "fight", "spectator", "boss", "keeper"}) builder.suggest(point);
        return builder.buildFuture();
    }
}
