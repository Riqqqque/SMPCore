package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.wild.WildTeleportManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class WildCommand {

    private WildCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("wild")
                .requires(source -> source.getSender().hasPermission("smpcore.wild"))
                .executes(context -> execute(plugin, context.getSource().getSender()))
                .build(),
            "Travel to a safe random location inside the current world border",
            List.of("rtp", "randomtp")
        );
    }

    private static int execute(SMPCore plugin, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Only players can use <white>/wild</white>."));
            return 0;
        }
        WildTeleportManager manager = plugin.getWildTeleportManager();
        if (manager == null) {
            player.sendMessage(MessageUtil.error("Wild travel is unavailable right now."));
            return 0;
        }
        return manager.requestTeleport(player) ? Command.SINGLE_SUCCESS : 0;
    }
}
