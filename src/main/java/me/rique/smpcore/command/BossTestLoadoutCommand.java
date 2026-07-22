package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.boss.BossManager;
import me.rique.smpcore.boss.BossTestLoadoutManager;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
public final class BossTestLoadoutCommand {

    private BossTestLoadoutCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("bossloadout")
                .requires(source -> source.getSender().hasPermission("smpcore.dungeon.admin"))
                .executes(context -> open(plugin, context.getSource().getSender()))
                .then(Commands.literal("clear")
                    .executes(context -> clear(plugin, context.getSource().getSender())))
                .then(Commands.argument("boss", StringArgumentType.word())
                    .suggests(BossTestLoadoutCommand::suggestBosses)
                    .executes(context -> equip(
                        plugin,
                        context.getSource().getSender(),
                        StringArgumentType.getString(context, "boss")
                    )))
                .build(),
            "Equip pre-boss admin test gear",
            List.of("bossgear", "testgear")
        );
    }

    public static int open(SMPCore plugin, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Run this command in-game."));
            return 0;
        }
        BossTestLoadoutManager manager = plugin.getBossTestLoadoutManager();
        if (manager == null) {
            sender.sendMessage(MessageUtil.error("Boss test loadouts are not ready yet."));
            return 0;
        }
        manager.openMenu(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int equip(SMPCore plugin, CommandSender sender, String bossInput) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Run this command in-game."));
            return 0;
        }
        BossManager.BossType boss = BossManager.BossType.fromInput(bossInput);
        if (boss == null) {
            sender.sendMessage(MessageUtil.error("Unknown boss. Tab-complete a boss ID."));
            return 0;
        }
        BossTestLoadoutManager manager = plugin.getBossTestLoadoutManager();
        if (manager == null) {
            sender.sendMessage(MessageUtil.error("Boss test loadouts are not ready yet."));
            return 0;
        }
        return manager.equipLoadout(player, boss) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int clear(SMPCore plugin, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Run this command in-game."));
            return 0;
        }
        BossTestLoadoutManager manager = plugin.getBossTestLoadoutManager();
        if (manager == null) {
            sender.sendMessage(MessageUtil.error("Boss test loadouts are not ready yet."));
            return 0;
        }
        manager.clearTestGear(player);
        return Command.SINGLE_SUCCESS;
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
