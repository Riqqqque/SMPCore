package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class BossPotionCommands {

    private BossPotionCommands() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("bossbrews")
                .requires(src -> src.getSender() instanceof Player player && player.hasPermission("smpcore.bossbrews"))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    if (plugin.getBossPotionListener() == null) {
                        player.sendMessage(MessageUtil.error("Boss brews are not ready yet."));
                        return 0;
                    }
                    plugin.getBossPotionListener().openPotionMenu(player);
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Open the boss brew guide",
            List.of("bosspotions", "brews")
        );
    }
}
