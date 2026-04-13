package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.boss.BossManager;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
public final class BossCommands {

    private BossCommands() {}

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("bosses")
                .requires(src -> src.getSender().hasPermission("smpcore.boss.admin"))
                .executes(ctx -> openBossMenu(plugin, ctx.getSource().getSender()))
                .then(Commands.literal("menu")
                    .executes(ctx -> openBossMenu(plugin, ctx.getSource().getSender())))
                .then(Commands.literal("status")
                    .executes(ctx -> {
                        BossManager bossManager = plugin.getBossManager();
                        if (bossManager == null) {
                            ctx.getSource().getSender().sendMessage(MessageUtil.error("Boss system is not ready yet."));
                            return 0;
                        }
                        for (String line : bossManager.statusLines()) {
                            ctx.getSource().getSender().sendMessage(MessageUtil.prefixedRaw(line));
                        }
                        return Command.SINGLE_SUCCESS;
                    }))
                .then(Commands.literal("clearall")
                    .executes(ctx -> clearAll(plugin, ctx.getSource().getSender())))
                .then(Commands.literal("spawn")
                    .then(Commands.argument("boss", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestBossIds(plugin, builder))
                        .executes(ctx -> spawnBoss(
                            plugin,
                            ctx.getSource().getSender(),
                            StringArgumentType.getString(ctx, "boss")
                        ))))
                .then(Commands.literal("despawn")
                    .then(Commands.argument("boss", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestBossIds(plugin, builder))
                        .executes(ctx -> despawnBoss(
                            plugin,
                            ctx.getSource().getSender(),
                            StringArgumentType.getString(ctx, "boss")
                        ))))
                .build(),
            "Custom boss control",
            List.of("boss")
        );
    }

    private static int openBossMenu(SMPCore plugin, CommandSender sender) {
        BossManager bossManager = plugin.getBossManager();
        if (bossManager == null) {
            sender.sendMessage(MessageUtil.error("Boss system is not ready yet."));
            return 0;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Console must use /bosses status, /bosses spawn <boss>, /bosses despawn <boss>, or /bosses clearall."));
            return 0;
        }
        bossManager.openBossMenu(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int clearAll(SMPCore plugin, CommandSender sender) {
        BossManager bossManager = plugin.getBossManager();
        if (bossManager == null) {
            sender.sendMessage(MessageUtil.error("Boss system is not ready yet."));
            return 0;
        }
        BossManager.BossActionResult result = bossManager.despawnAllBosses();
        sender.sendMessage(result.success() ? MessageUtil.success(result.message()) : MessageUtil.error(result.message()));
        return Command.SINGLE_SUCCESS;
    }

    private static int spawnBoss(SMPCore plugin, CommandSender sender, String requestedBossId) {
        BossManager bossManager = plugin.getBossManager();
        if (bossManager == null) {
            sender.sendMessage(MessageUtil.error("Boss system is not ready yet."));
            return 0;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Spawn boss from in-game so the boss can be placed at your location."));
            return 0;
        }
        BossManager.BossActionResult result = bossManager.spawnBoss(player, requestedBossId);
        sender.sendMessage(result.success() ? MessageUtil.success(result.message()) : MessageUtil.error(result.message()));
        return result.success() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int despawnBoss(SMPCore plugin, CommandSender sender, String requestedBossId) {
        BossManager bossManager = plugin.getBossManager();
        if (bossManager == null) {
            sender.sendMessage(MessageUtil.error("Boss system is not ready yet."));
            return 0;
        }
        BossManager.BossActionResult result = bossManager.despawnBoss(requestedBossId);
        sender.sendMessage(result.success() ? MessageUtil.success(result.message()) : MessageUtil.error(result.message()));
        return result.success() ? Command.SINGLE_SUCCESS : 0;
    }

    private static CompletableFuture<Suggestions> suggestBossIds(SMPCore plugin, SuggestionsBuilder builder) {
        BossManager bossManager = plugin.getBossManager();
        if (bossManager != null) {
            for (String option : bossManager.bossCommandOptions()) {
                builder.suggest(option);
            }
        }
        return builder.buildFuture();
    }
}
