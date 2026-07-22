package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.game.BlackjackManager;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class BlackjackCommand {

    private BlackjackCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("blackjack")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.blackjack"))
                .executes(ctx -> {
                    sendDealerMessage((Player) ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("help")
                    .executes(ctx -> {
                        sendDealerMessage((Player) ctx.getSource().getSender());
                        return Command.SINGLE_SUCCESS;
                    }))
                .then(Commands.literal("claim")
                    .executes(ctx -> runClaim(plugin, (Player) ctx.getSource().getSender())))
                .build(),
            "Recover blackjack items; new games start through Silas",
            List.of("bj")
        );
    }

    private static int runClaim(SMPCore plugin, Player player) {
        BlackjackManager manager = plugin.getBlackjackManager();
        if (manager == null) {
            player.sendMessage(MessageUtil.error("Blackjack is not ready yet."));
            return 0;
        }
        return manager.claim(player, true) ? Command.SINGLE_SUCCESS : 0;
    }

    private static void sendDealerMessage(Player player) {
        player.sendMessage(MessageUtil.info("Talk to <white>Silas the Dealer</white> in the tavern to play blackjack."));
        player.sendMessage(MessageUtil.info("Use <white>/blackjack claim</white> only to recover an interrupted payout."));
    }
}
