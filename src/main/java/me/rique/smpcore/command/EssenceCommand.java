package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.essence.EssenceManager;
import me.rique.smpcore.util.CommandSuggestionUtil;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
public final class EssenceCommand {

    private static final long MAX_ADMIN_AMOUNT = 9_999_999_999L;

    private EssenceCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("essence")
                .requires(src -> src.getSender().hasPermission("smpcore.essence"))
                .executes(ctx -> showSelf(plugin, ctx.getSource().getSender()))
                .then(Commands.literal("balance")
                    .executes(ctx -> showSelf(plugin, ctx.getSource().getSender()))
                    .then(Commands.argument("target", StringArgumentType.word())
                        .requires(src -> src.getSender().hasPermission("smpcore.essence.admin"))
                        .suggests((ctx, builder) -> suggestEssenceTargets(plugin, builder))
                        .executes(ctx -> showTarget(plugin, ctx.getSource().getSender(), StringArgumentType.getString(ctx, "target")))))
                .then(Commands.literal("progress")
                    .requires(src -> src.getSender().hasPermission("smpcore.essence.admin"))
                    .then(Commands.argument("target", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestEssenceTargets(plugin, builder))
                        .executes(ctx -> showProgress(plugin, ctx.getSource().getSender(), StringArgumentType.getString(ctx, "target")))))
                .build(),
            "Show your Essence balance",
            List.of("ess")
        );

        commands.register(
            Commands.literal("essenceadmin")
                .requires(src -> src.getSender().hasPermission("smpcore.essence.admin"))
                .executes(ctx -> {
                    sendAdminUsage(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("help")
                    .executes(ctx -> {
                        sendAdminUsage(ctx.getSource().getSender());
                        return Command.SINGLE_SUCCESS;
                    }))
                .then(snapshotNode(plugin, "balance", false))
                .then(snapshotNode(plugin, "progress", true))
                .then(changeNode(plugin, "give", AdminAction.ADD))
                .then(changeNode(plugin, "add", AdminAction.ADD))
                .then(changeNode(plugin, "take", AdminAction.REMOVE))
                .then(changeNode(plugin, "remove", AdminAction.REMOVE))
                .then(changeNode(plugin, "set", AdminAction.SET))
                .then(resetNode(plugin, "reset"))
                .then(resetNode(plugin, "clear"))
                .build(),
            "Manage Essence balances",
            List.of("essadmin")
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> snapshotNode(SMPCore plugin, String literal, boolean progress) {
        return Commands.literal(literal)
            .then(Commands.argument("target", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestEssenceTargets(plugin, builder))
                .executes(ctx -> progress
                    ? showProgress(plugin, ctx.getSource().getSender(), StringArgumentType.getString(ctx, "target"))
                    : showTarget(plugin, ctx.getSource().getSender(), StringArgumentType.getString(ctx, "target"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> changeNode(SMPCore plugin, String literal, AdminAction action) {
        long min = action == AdminAction.SET ? 0L : 1L;
        return Commands.literal(literal)
            .then(Commands.argument("target", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestEssenceTargets(plugin, builder))
                .then(Commands.argument("amount", LongArgumentType.longArg(min, MAX_ADMIN_AMOUNT))
                    .suggests(EssenceCommand::suggestAmounts)
                    .executes(ctx -> change(
                        plugin,
                        ctx.getSource().getSender(),
                        StringArgumentType.getString(ctx, "target"),
                        LongArgumentType.getLong(ctx, "amount"),
                        action
                    ))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> resetNode(SMPCore plugin, String literal) {
        return Commands.literal(literal)
            .then(Commands.argument("target", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestEssenceTargets(plugin, builder))
                .executes(ctx -> {
                    String target = StringArgumentType.getString(ctx, "target");
                    ctx.getSource().getSender().sendMessage(MessageUtil.warn("Use <white>/essenceadmin " + literal + " " + target + " confirm</white>."));
                    return 0;
                })
                .then(Commands.literal("confirm")
                    .executes(ctx -> reset(plugin, ctx.getSource().getSender(), StringArgumentType.getString(ctx, "target")))));
    }

    private static int showSelf(SMPCore plugin, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Use /essence balance <player> from console."));
            return 0;
        }
        EssenceManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        manager.sendBalance(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int showTarget(SMPCore plugin, CommandSender sender, String targetName) {
        EssenceManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        manager.snapshot(targetName).whenComplete((snapshot, ex) -> runSync(plugin, () -> {
            if (ex != null) {
                plugin.getLogger().severe("Could not load Essence balance: " + ex.getMessage());
                sender.sendMessage(MessageUtil.error("Could not load that Essence account."));
                return;
            }
            if (snapshot == null) {
                sender.sendMessage(MessageUtil.error("No Essence account found for <white>" + targetName + "</white>."));
                return;
            }
            sender.sendMessage(MessageUtil.info(
                "<white>" + snapshot.playerName() + "</white> has <white>" + manager.formatted(snapshot.balance()) + "</white> Essence"
                    + " <dark_gray>| Lifetime: " + manager.formatted(snapshot.lifetimeEarned())
                    + (snapshot.online() ? " | online" : " | offline") + "</dark_gray>"
            ));
        }));
        return Command.SINGLE_SUCCESS;
    }

    private static int showProgress(SMPCore plugin, CommandSender sender, String targetName) {
        EssenceManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        manager.snapshot(targetName).whenComplete((snapshot, ex) -> runSync(plugin, () -> {
            if (ex != null) {
                plugin.getLogger().severe("Could not load Essence progress: " + ex.getMessage());
                sender.sendMessage(MessageUtil.error("Could not load that Essence account."));
                return;
            }
            if (snapshot == null) {
                sender.sendMessage(MessageUtil.error("No Essence account found for <white>" + targetName + "</white>."));
                return;
            }
            sender.sendMessage(MessageUtil.info(
                "<white>" + snapshot.playerName() + "</white> Essence progress:"
                    + " <gray>Mining</gray> <white>" + snapshot.miningProgress() + "/" + plugin.getConfigManager().normalEssenceMiningThreshold + "</white>,"
                    + " <gray>Mobs</gray> <white>" + snapshot.mobProgress() + "/" + plugin.getConfigManager().normalEssenceMobKillThreshold + "</white>,"
                    + " <gray>XP</gray> <white>" + snapshot.xpProgress() + "/" + plugin.getConfigManager().normalEssenceXpThreshold + "</white>."
            ));
        }));
        return Command.SINGLE_SUCCESS;
    }

    private static int change(SMPCore plugin, CommandSender sender, String targetName, long amount, AdminAction action) {
        EssenceManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        CompletableFuture<EssenceManager.AdminChangeResult> future = switch (action) {
            case ADD -> manager.addByAdmin(targetName, amount);
            case REMOVE -> manager.removeByAdmin(targetName, amount);
            case SET -> manager.setByAdmin(targetName, amount);
        };
        future.whenComplete((result, ex) -> runSync(plugin, () -> sendChangeResult(plugin, sender, manager, targetName, result, ex, action)));
        return Command.SINGLE_SUCCESS;
    }

    private static int reset(SMPCore plugin, CommandSender sender, String targetName) {
        EssenceManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        manager.resetByAdmin(targetName).whenComplete((result, ex) -> runSync(plugin, () -> {
            if (ex != null) {
                plugin.getLogger().severe("Could not reset Essence account: " + ex.getMessage());
                sender.sendMessage(MessageUtil.error("Could not reset that Essence account."));
                return;
            }
            if (result == null || !result.found()) {
                sender.sendMessage(MessageUtil.error("No Essence account found for <white>" + targetName + "</white>."));
                return;
            }
            sender.sendMessage(MessageUtil.success(
                "Reset <white>" + result.playerName() + "</white>'s Essence to <white>0</white> and cleared progress."
            ));
            Player online = Bukkit.getPlayerExact(result.playerName());
            if (online != null && !online.equals(sender)) {
                online.sendMessage(MessageUtil.warn("Your Essence was reset by staff."));
            }
        }));
        return Command.SINGLE_SUCCESS;
    }

    private static void sendChangeResult(
        SMPCore plugin,
        CommandSender sender,
        EssenceManager manager,
        String targetName,
        EssenceManager.AdminChangeResult result,
        Throwable ex,
        AdminAction action
    ) {
        if (ex != null) {
            plugin.getLogger().severe("Could not update Essence account: " + ex.getMessage());
            sender.sendMessage(MessageUtil.error("Could not update that Essence account."));
            return;
        }
        if (result == null || !result.found()) {
            sender.sendMessage(MessageUtil.error("No Essence account found for <white>" + targetName + "</white>."));
            return;
        }

        String verb = switch (action) {
            case ADD -> "Gave";
            case REMOVE -> "Removed";
            case SET -> "Set";
        };
        String amountText = switch (action) {
            case ADD, REMOVE -> manager.formatted(Math.abs(result.applied()));
            case SET -> manager.formatted(result.after());
        };
        sender.sendMessage(MessageUtil.success(
            verb + " <white>" + amountText + "</white> Essence "
                + (action == AdminAction.SET ? "for " : action == AdminAction.ADD ? "to " : "from ")
                + "<white>" + result.playerName() + "</white>."
                + " <dark_gray>Balance: " + manager.formatted(result.before()) + " -> " + manager.formatted(result.after()) + "</dark_gray>"
        ));

        Player online = Bukkit.getPlayerExact(result.playerName());
        if (online == null || online.equals(sender)) {
            return;
        }
        switch (action) {
            case ADD -> online.sendMessage(MessageUtil.success("You received <white>" + amountText + "</white> Essence."));
            case REMOVE -> online.sendMessage(MessageUtil.warn("<white>" + amountText + "</white> Essence was removed."));
            case SET -> online.sendMessage(MessageUtil.info("Your Essence is now <white>" + amountText + "</white>."));
        }
    }

    private static EssenceManager manager(SMPCore plugin, CommandSender sender) {
        EssenceManager manager = plugin.getEssenceManager();
        if (manager == null) {
            sender.sendMessage(MessageUtil.error("Essence is not ready yet."));
        }
        return manager;
    }

    private static CompletableFuture<Suggestions> suggestEssenceTargets(SMPCore plugin, SuggestionsBuilder builder) {
        EssenceManager manager = plugin.getEssenceManager();
        if (manager == null) {
            return CommandSuggestionUtil.suggestOnlinePlayers(builder);
        }
        return manager.suggestPlayerNames(builder.getRemaining()).thenApply(names -> {
            for (String name : names) {
                builder.suggest(name);
            }
            return builder.build();
        });
    }

    private static CompletableFuture<Suggestions> suggestAmounts(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return CommandSuggestionUtil.suggestNumbers(builder, 1, 5, 10, 25, 50, 100, 250, 500, 1000, 5000, 10000);
    }

    private static void sendAdminUsage(CommandSender sender) {
        sender.sendMessage(MessageUtil.info("<white>/essenceadmin balance <player></white>"));
        sender.sendMessage(MessageUtil.info("<white>/essenceadmin progress <player></white>"));
        sender.sendMessage(MessageUtil.info("<white>/essenceadmin give <player> <amount></white>"));
        sender.sendMessage(MessageUtil.info("<white>/essenceadmin take <player> <amount></white>"));
        sender.sendMessage(MessageUtil.info("<white>/essenceadmin set <player> <amount></white>"));
        sender.sendMessage(MessageUtil.info("<white>/essenceadmin reset <player> confirm</white>"));
    }

    private static void runSync(SMPCore plugin, Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    private enum AdminAction {
        ADD,
        REMOVE,
        SET
    }
}
