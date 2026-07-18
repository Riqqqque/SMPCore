package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.launch.LaunchAccessManager;
import me.rique.smpcore.util.MessageUtil;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class LaunchAccessCommand {

    private LaunchAccessCommand() {}

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("launchaccess")
                .requires(source -> source.getSender().hasPermission("smpcore.launchaccess.admin"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(plugin.getLaunchAccessManager().statusMessage());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("status")
                    .executes(context -> {
                        context.getSource().getSender().sendMessage(plugin.getLaunchAccessManager().statusMessage());
                        return Command.SINGLE_SUCCESS;
                    }))
                .then(Commands.literal("allowme")
                    .executes(context -> sendResult(
                        context.getSource().getSender(),
                        plugin.getLaunchAccessManager().allowSelf(context.getSource().getSender())
                    )))
                .then(Commands.literal("lock")
                    .executes(context -> sendResult(
                        context.getSource().getSender(),
                        plugin.getLaunchAccessManager().lockToAllowedPlayers()
                    )))
                .then(Commands.literal("open")
                    .executes(context -> sendResult(
                        context.getSource().getSender(),
                        plugin.getLaunchAccessManager().openPublicAccess()
                    )))
                .build(),
            "Control owner-only launch access",
            List.of("serveraccess", "launchgate")
        );
    }

    private static int sendResult(org.bukkit.command.CommandSender sender, LaunchAccessManager.ActionResult result) {
        sender.sendMessage(result.success() ? MessageUtil.success(result.message()) : MessageUtil.error(result.message()));
        return result.success() ? Command.SINGLE_SUCCESS : 0;
    }
}
