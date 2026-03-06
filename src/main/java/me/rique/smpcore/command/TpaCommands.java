package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.tpa.TPARequest;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class TpaCommands {

    private TpaCommands() {}

    public static void register(Commands commands, SMPCore plugin) {

        // /tpa <player>
        commands.register(
            Commands.literal("tpa")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.tpa"))
                .then(Commands.argument("target", ArgumentTypes.player())
                    .executes(ctx -> {
                        Player sender = (Player) ctx.getSource().getSender();
                        List<Player> resolved = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                            .resolve(ctx.getSource());
                        if (resolved.isEmpty()) { sender.sendMessage(MessageUtil.error("Player not found.")); return 0; }
                        Player target = resolved.get(0);
                        if (target.equals(sender)) { sender.sendMessage(MessageUtil.error("You cannot /tpa yourself.")); return 0; }
                        plugin.getTpaManager().sendRequest(sender, target, TPARequest.Type.TO);
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "Request to teleport to a player",
            List.of("tprequest")
        );

        // /tpahere <player>
        commands.register(
            Commands.literal("tpahere")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.tpa"))
                .then(Commands.argument("target", ArgumentTypes.player())
                    .executes(ctx -> {
                        Player sender = (Player) ctx.getSource().getSender();
                        List<Player> resolved = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                            .resolve(ctx.getSource());
                        if (resolved.isEmpty()) { sender.sendMessage(MessageUtil.error("Player not found.")); return 0; }
                        Player target = resolved.get(0);
                        if (target.equals(sender)) { sender.sendMessage(MessageUtil.error("You cannot /tpahere yourself.")); return 0; }
                        plugin.getTpaManager().sendRequest(sender, target, TPARequest.Type.HERE);
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "Request a player to teleport to you"
        );

        // /tpaccept [player]
        commands.register(
            Commands.literal("tpaccept")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.tpa.accept"))
                .executes(ctx -> {
                    plugin.getTpaManager().accept((Player) ctx.getSource().getSender(), null);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("requester", StringArgumentType.word())
                    .executes(ctx -> {
                        plugin.getTpaManager().accept(
                            (Player) ctx.getSource().getSender(),
                            StringArgumentType.getString(ctx, "requester")
                        );
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "Accept a pending TPA request",
            List.of("tpyes")
        );

        // /tpdeny [player]
        commands.register(
            Commands.literal("tpdeny")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.tpa.accept"))
                .executes(ctx -> {
                    plugin.getTpaManager().deny((Player) ctx.getSource().getSender(), null);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("requester", StringArgumentType.word())
                    .executes(ctx -> {
                        plugin.getTpaManager().deny(
                            (Player) ctx.getSource().getSender(),
                            StringArgumentType.getString(ctx, "requester")
                        );
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "Deny a pending TPA request",
            List.of("tpno")
        );

        // /tpacancel
        commands.register(
            Commands.literal("tpacancel")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.tpa.accept"))
                .executes(ctx -> {
                    plugin.getTpaManager().cancel((Player) ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Cancel your outgoing TPA request"
        );
    }
}
