package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.game.RouletteManager;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class RouletteCommand {

    private RouletteCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("roulette")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission("smpcore.roulette"))
                .executes(context -> {
                    sendCroupierMessage((Player) context.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("help")
                    .executes(context -> {
                        sendCroupierMessage((Player) context.getSource().getSender());
                        return Command.SINGLE_SUCCESS;
                    }))
                .then(Commands.literal("claim")
                    .executes(context -> claim(plugin, (Player) context.getSource().getSender())))
                .build(),
            "Recover roulette payouts; new spins start through Renn",
            List.of("roul")
        );
    }

    private static int claim(SMPCore plugin, Player player) {
        RouletteManager manager = plugin.getRouletteManager();
        if (manager == null) {
            player.sendMessage(MessageUtil.error("Roulette is not ready yet."));
            return 0;
        }
        return manager.claim(player, true) ? Command.SINGLE_SUCCESS : 0;
    }

    private static void sendCroupierMessage(Player player) {
        player.sendMessage(MessageUtil.info("Talk to <white>Renn the Croupier</white> in the tavern to play roulette."));
        player.sendMessage(MessageUtil.info("Use <white>/roulette claim</white> only to recover an interrupted material payout."));
    }
}
