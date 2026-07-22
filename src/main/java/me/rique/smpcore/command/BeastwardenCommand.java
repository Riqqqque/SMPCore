package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.quest.BeastwardenManager;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class BeastwardenCommand {

    private BeastwardenCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("steed")
                .requires(source -> source.getSender().hasPermission("smpcore.beastwarden.use"))
                .executes(context -> toggle(plugin, context.getSource().getSender()))
                .then(Commands.literal("summon").executes(context -> summon(plugin, context.getSource().getSender())))
                .then(Commands.literal("recall").executes(context -> recall(plugin, context.getSource().getSender())))
                .then(Commands.literal("status").executes(context -> status(plugin, context.getSource().getSender())))
                .then(Commands.literal("quest").executes(context -> questProgress(plugin, context.getSource().getSender())))
                .build(),
            "Summon or recall your Wildbound steed",
            List.of("mountcall", "wildsteed")
        );

        commands.register(
            Commands.literal("beastwardenadmin")
                .requires(source -> source.getSender().hasPermission("smpcore.beastwarden.admin"))
                .executes(context -> usage(context.getSource().getSender()))
                .then(Commands.literal("complete")
                    .then(Commands.argument("target", ArgumentTypes.player())
                        .executes(context -> modify(
                            plugin,
                            context.getSource().getSender(),
                            firstTarget(context.getArgument("target", PlayerSelectorArgumentResolver.class), context.getSource()),
                            AdminAction.COMPLETE
                        ))))
                .then(Commands.literal("reset")
                    .then(Commands.argument("target", ArgumentTypes.player())
                        .executes(context -> modify(
                            plugin,
                            context.getSource().getSender(),
                            firstTarget(context.getArgument("target", PlayerSelectorArgumentResolver.class), context.getSource()),
                            AdminAction.RESET
                        ))))
                .then(Commands.literal("armor")
                    .then(Commands.argument("target", ArgumentTypes.player())
                        .executes(context -> modify(
                            plugin,
                            context.getSource().getSender(),
                            firstTarget(context.getArgument("target", PlayerSelectorArgumentResolver.class), context.getSource()),
                            AdminAction.ARMOR
                        ))))
                .then(Commands.literal("progress")
                    .then(Commands.argument("target", ArgumentTypes.player())
                        .executes(context -> progress(
                            plugin,
                            context.getSource().getSender(),
                            firstTarget(context.getArgument("target", PlayerSelectorArgumentResolver.class), context.getSource())
                        ))))
                .then(Commands.literal("preview")
                    .executes(context -> preview(plugin, context.getSource().getSender(), BeastwardenManager.AdminPreviewView.MAIN, null))
                    .then(Commands.literal("main")
                        .executes(context -> preview(plugin, context.getSource().getSender(), BeastwardenManager.AdminPreviewView.MAIN, null)))
                    .then(Commands.literal("familiars")
                        .executes(context -> preview(plugin, context.getSource().getSender(), BeastwardenManager.AdminPreviewView.TREE, null)))
                    .then(Commands.literal("tree")
                        .executes(context -> preview(plugin, context.getSource().getSender(), BeastwardenManager.AdminPreviewView.TREE, null))
                        .then(Commands.literal("veil_wisp").executes(context -> preview(plugin, context.getSource().getSender(), BeastwardenManager.AdminPreviewView.TREE, "veil_wisp")))
                        .then(Commands.literal("miner").executes(context -> preview(plugin, context.getSource().getSender(), BeastwardenManager.AdminPreviewView.TREE, "miner")))
                        .then(Commands.literal("tiller").executes(context -> preview(plugin, context.getSource().getSender(), BeastwardenManager.AdminPreviewView.TREE, "tiller")))
                        .then(Commands.literal("morrow").executes(context -> preview(plugin, context.getSource().getSender(), BeastwardenManager.AdminPreviewView.TREE, "morrow"))))
                    .then(Commands.literal("evolution")
                        .executes(context -> preview(plugin, context.getSource().getSender(), BeastwardenManager.AdminPreviewView.EVOLUTION, null))
                        .then(Commands.literal("veil_wisp").executes(context -> preview(plugin, context.getSource().getSender(), BeastwardenManager.AdminPreviewView.EVOLUTION, "veil_wisp")))
                        .then(Commands.literal("miner").executes(context -> preview(plugin, context.getSource().getSender(), BeastwardenManager.AdminPreviewView.EVOLUTION, "miner")))
                        .then(Commands.literal("tiller").executes(context -> preview(plugin, context.getSource().getSender(), BeastwardenManager.AdminPreviewView.EVOLUTION, "tiller")))
                        .then(Commands.literal("morrow").executes(context -> preview(plugin, context.getSource().getSender(), BeastwardenManager.AdminPreviewView.EVOLUTION, "morrow")))))
                .build(),
            "Test and manage Beastwarden progression",
            List.of("beastadmin")
        );
    }

    private static int toggle(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        BeastwardenManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.toggleSteed(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int summon(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        BeastwardenManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        return manager.summonSteed(player) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int recall(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        BeastwardenManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.recallSteed(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int status(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        BeastwardenManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        sender.sendMessage(MessageUtil.info("Steed: <white>" + manager.steedStatus(player) + "</white>."));
        return Command.SINGLE_SUCCESS;
    }

    private static int questProgress(SMPCore plugin, CommandSender sender) {
        Player player = player(sender);
        BeastwardenManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        manager.sendQuestProgress(player, sender);
        return Command.SINGLE_SUCCESS;
    }

    private static int progress(SMPCore plugin, CommandSender sender, Player target) {
        BeastwardenManager manager = manager(plugin, sender);
        if (manager == null || target == null) {
            if (target == null) sender.sendMessage(MessageUtil.error("Player not found."));
            return 0;
        }
        manager.sendQuestProgress(target, sender);
        return Command.SINGLE_SUCCESS;
    }

    private static int modify(SMPCore plugin, CommandSender sender, Player target, AdminAction action) {
        BeastwardenManager manager = manager(plugin, sender);
        if (manager == null || target == null) {
            if (target == null) sender.sendMessage(MessageUtil.error("Player not found."));
            return 0;
        }
        switch (action) {
            case COMPLETE -> manager.completeForAdmin(target, sender);
            case RESET -> manager.resetForAdmin(target, sender);
            case ARMOR -> manager.giveArmorForAdmin(target, sender);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int preview(
        SMPCore plugin,
        CommandSender sender,
        BeastwardenManager.AdminPreviewView view,
        String familiarId
    ) {
        Player player = player(sender);
        BeastwardenManager manager = manager(plugin, sender);
        if (player == null || manager == null) return 0;
        if (!manager.openAdminPreview(player, view, familiarId)) {
            sender.sendMessage(MessageUtil.error("That Beastwarden preview is not available."));
            return 0;
        }
        sender.sendMessage(MessageUtil.info("Admin preview: purchases, rewards, and progress are disabled."));
        return Command.SINGLE_SUCCESS;
    }

    private static int usage(CommandSender sender) {
        sender.sendMessage(MessageUtil.info("<white>/beastwardenadmin complete <player></white>"));
        sender.sendMessage(MessageUtil.info("<white>/beastwardenadmin reset <player></white>"));
        sender.sendMessage(MessageUtil.info("<white>/beastwardenadmin armor <player></white>"));
        sender.sendMessage(MessageUtil.info("<white>/beastwardenadmin progress <player></white>"));
        sender.sendMessage(MessageUtil.info("<white>/beastwardenadmin preview [main|familiars|tree|evolution]</white>"));
        return Command.SINGLE_SUCCESS;
    }

    private static BeastwardenManager manager(SMPCore plugin, CommandSender sender) {
        BeastwardenManager manager = plugin.getBeastwardenManager();
        if (manager == null) sender.sendMessage(MessageUtil.error("Beastwarden system is not ready yet."));
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

    private enum AdminAction { COMPLETE, RESET, ARMOR }
}
