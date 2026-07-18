package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.boss.BossManager;
import me.rique.smpcore.quest.BossMasteryManager;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
public final class BossMasteryCommand {

    private BossMasteryCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("bossmasteryadmin")
                .requires(source -> source.getSender().hasPermission("smpcore.bossmastery.admin"))
                .executes(context -> usage(context.getSource().getSender()))
                .then(Commands.literal("setkills")
                    .then(Commands.argument("target", ArgumentTypes.player())
                        .then(Commands.argument("boss", StringArgumentType.word())
                            .suggests(BossMasteryCommand::suggestBosses)
                            .then(Commands.argument("amount", IntegerArgumentType.integer(0, 10_000))
                                .executes(context -> setKills(
                                    plugin,
                                    context.getSource().getSender(),
                                    firstTarget(context.getArgument("target", PlayerSelectorArgumentResolver.class), context.getSource()),
                                    StringArgumentType.getString(context, "boss"),
                                    IntegerArgumentType.getInteger(context, "amount")
                                ))))))
                .then(Commands.literal("reset")
                    .then(Commands.argument("target", ArgumentTypes.player())
                        .executes(context -> reset(
                            plugin,
                            context.getSource().getSender(),
                            firstTarget(context.getArgument("target", PlayerSelectorArgumentResolver.class), context.getSource())
                        ))))
                .build(),
            "Test and repair Mogrik's Boss Mastery progress",
            List.of("bossledgeradmin")
        );
    }

    private static int setKills(SMPCore plugin, CommandSender sender, Player target, String bossInput, int amount) {
        BossMasteryManager manager = manager(plugin, sender);
        BossManager.BossType boss = BossManager.BossType.fromInput(bossInput);
        if (manager == null || target == null || boss == null) {
            if (target == null) sender.sendMessage(MessageUtil.error("Player not found."));
            else if (boss == null) sender.sendMessage(MessageUtil.error("Unknown boss. Use tab completion."));
            return 0;
        }
        manager.setKillsForAdmin(target, boss, amount);
        sender.sendMessage(MessageUtil.success("Set <white>" + target.getName() + "</white> to <white>" + amount
            + "</white> victories for <white>" + boss.plainDisplayName() + "</white>."));
        return Command.SINGLE_SUCCESS;
    }

    private static int reset(SMPCore plugin, CommandSender sender, Player target) {
        BossMasteryManager manager = manager(plugin, sender);
        if (manager == null || target == null) {
            if (target == null) sender.sendMessage(MessageUtil.error("Player not found."));
            return 0;
        }
        manager.resetForAdmin(target);
        sender.sendMessage(MessageUtil.success("Reset <white>" + target.getName() + "</white>'s Boss Mastery progress."));
        return Command.SINGLE_SUCCESS;
    }

    private static int usage(CommandSender sender) {
        sender.sendMessage(MessageUtil.info("<white>/bossmasteryadmin setkills <player> <boss> <amount></white>"));
        sender.sendMessage(MessageUtil.info("<white>/bossmasteryadmin reset <player></white>"));
        return Command.SINGLE_SUCCESS;
    }

    private static BossMasteryManager manager(SMPCore plugin, CommandSender sender) {
        BossMasteryManager manager = plugin.getBossMasteryManager();
        if (manager == null) sender.sendMessage(MessageUtil.error("Boss Mastery is not ready yet."));
        return manager;
    }

    private static Player firstTarget(PlayerSelectorArgumentResolver resolver, io.papermc.paper.command.brigadier.CommandSourceStack source)
        throws CommandSyntaxException {
        List<Player> players = resolver.resolve(source);
        return players.isEmpty() ? null : players.getFirst();
    }

    private static CompletableFuture<Suggestions> suggestBosses(
        com.mojang.brigadier.context.CommandContext<?> context,
        SuggestionsBuilder builder
    ) {
        for (BossManager.BossType boss : BossManager.BossType.progressionOrder()) {
            builder.suggest(boss.id());
            builder.suggest(boss.commandToken());
        }
        return builder.buildFuture();
    }
}
