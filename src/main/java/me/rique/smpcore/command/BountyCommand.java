package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.tavern.TavernManager;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class BountyCommand {

    private BountyCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("bounties")
                .requires(source -> source.getSender().hasPermission("smpcore.bounties"))
                .executes(context -> open(plugin, context.getSource().getSender()))
                .build(),
            "View every active player bounty",
            List.of("bountylist", "wanted")
        );
    }

    private static int open(SMPCore plugin, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Only players can open the bounty list."));
            return 0;
        }
        TavernManager manager = plugin.getTavernManager();
        if (manager == null) {
            player.sendMessage(MessageUtil.error("The bounty board is unavailable right now."));
            return 0;
        }
        manager.openActiveBounties(player);
        return Command.SINGLE_SUCCESS;
    }
}
