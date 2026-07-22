package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.quest.MayorQuestManager;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class MayorPetCommand {

    private MayorPetCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("familiar")
                .requires(src -> canUseFamiliars(src.getSender()))
                .executes(ctx -> openFamiliarMenu(plugin, ctx.getSource().getSender()))
                .build(),
            "Manage your familiars",
            List.of("familiars", "veilfamiliar")
        );

        commands.register(
            Commands.literal("mayorfamiliar")
                .requires(src -> canAdminFamiliars(src.getSender()))
                .executes(ctx -> {
                    sendAdminUsage(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("give")
                    .then(Commands.argument("target", ArgumentTypes.player())
                        .executes(ctx -> {
                            Player target = firstTarget(ctx.getArgument("target", PlayerSelectorArgumentResolver.class), ctx.getSource());
                            return give(plugin, ctx.getSource().getSender(), target);
                        })))
                .then(Commands.literal("take")
                    .then(Commands.argument("target", ArgumentTypes.player())
                        .executes(ctx -> {
                            Player target = firstTarget(ctx.getArgument("target", PlayerSelectorArgumentResolver.class), ctx.getSource());
                            return take(plugin, ctx.getSource().getSender(), target);
                        })))
                .build(),
            "Manage Mayor Bah familiar rewards",
            List.of("adminfamiliar")
        );
    }

    private static int openFamiliarMenu(SMPCore plugin, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Only players can open the familiar menu."));
            return 0;
        }
        MayorQuestManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        manager.openPetCollectionMenu(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int give(SMPCore plugin, CommandSender sender, Player target) {
        MayorQuestManager manager = manager(plugin, sender);
        if (manager == null || target == null) {
            sender.sendMessage(MessageUtil.error("Player not found."));
            return 0;
        }
        manager.grantPet(target, sender);
        return Command.SINGLE_SUCCESS;
    }

    private static int take(SMPCore plugin, CommandSender sender, Player target) {
        MayorQuestManager manager = manager(plugin, sender);
        if (manager == null || target == null) {
            sender.sendMessage(MessageUtil.error("Player not found."));
            return 0;
        }
        manager.revokePet(target, sender);
        return Command.SINGLE_SUCCESS;
    }

    private static MayorQuestManager manager(SMPCore plugin, CommandSender sender) {
        MayorQuestManager manager = plugin.getMayorQuestManager();
        if (manager == null) {
            sender.sendMessage(MessageUtil.error("Mayor quests are not ready yet."));
        }
        return manager;
    }

    private static boolean canUseFamiliars(CommandSender sender) {
        return sender.hasPermission("smpcore.familiar");
    }

    private static boolean canAdminFamiliars(CommandSender sender) {
        return sender.hasPermission("smpcore.familiar.admin") || sender.hasPermission("smpcore.mayor.admin");
    }

    private static Player firstTarget(PlayerSelectorArgumentResolver resolver, io.papermc.paper.command.brigadier.CommandSourceStack source) throws CommandSyntaxException {
        List<Player> players = resolver.resolve(source);
        return players.isEmpty() ? null : players.get(0);
    }

    private static void sendAdminUsage(CommandSender sender) {
        sender.sendMessage(MessageUtil.info("<white>/mayorfamiliar give <player></white> - unlock the Veil Wisp."));
        sender.sendMessage(MessageUtil.info("<white>/mayorfamiliar take <player></white> - remove the Veil Wisp."));
    }
}
