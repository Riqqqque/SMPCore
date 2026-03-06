package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class GamemodeCommands {

    private GamemodeCommands() {}

    public static void register(Commands commands, SMPCore plugin) {
        register(commands, plugin, "gmc", GameMode.CREATIVE,  "smpcore.gmc",  List.of("gamemodecreative"));
        register(commands, plugin, "gms", GameMode.SURVIVAL,  "smpcore.gms",  List.of("gamemodesurvival"));
        register(commands, plugin, "gma", GameMode.ADVENTURE, "smpcore.gma",  List.of("gamemodeadventure"));
        register(commands, plugin, "gmsp",GameMode.SPECTATOR, "smpcore.gmsp", List.of("gamemodespectator"));
    }

    private static void register(Commands commands, SMPCore plugin,
                                  String label, GameMode mode,
                                  String permission, List<String> aliases) {
        commands.register(
            Commands.literal(label)
                .requires(src -> src.getSender().hasPermission(permission))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player self)) {
                        ctx.getSource().getSender().sendMessage(MessageUtil.error("Console must specify a player."));
                        return 0;
                    }
                    self.setGameMode(mode);
                    self.sendMessage(MessageUtil.success("Gamemode set to <white>" + mode.name() + "</white>."));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("target", ArgumentTypes.player())
                    .executes(ctx -> {
                        List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                            .resolve(ctx.getSource());
                        if (targets.isEmpty()) {
                            ctx.getSource().getSender().sendMessage(MessageUtil.error("Player not found."));
                            return 0;
                        }
                        Player target = targets.get(0);
                        target.setGameMode(mode);
                        target.sendMessage(MessageUtil.info("Your gamemode was set to <white>" + mode.name() + "</white>."));
                        ctx.getSource().getSender().sendMessage(
                            MessageUtil.success("Set <white>" + target.getName() + "</white>'s gamemode to <white>" + mode.name() + "</white>."));
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "Set gamemode to " + mode.name().toLowerCase(),
            aliases
        );
    }
}
