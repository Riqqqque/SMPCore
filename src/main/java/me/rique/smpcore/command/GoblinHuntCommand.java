package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.quest.GoblinHuntManager;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class GoblinHuntCommand {

    private GoblinHuntCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("goblins")
                .executes(ctx -> open(plugin, ctx.getSource().getSender()))
                .then(Commands.literal("menu").executes(ctx -> open(plugin, ctx.getSource().getSender())))
                .then(Commands.literal("count").executes(ctx -> count(plugin, ctx.getSource().getSender())))
                .then(Commands.literal("give")
                    .requires(source -> source.getSender().hasPermission("smpcore.goblins.admin"))
                    .executes(ctx -> give(plugin, ctx.getSource().getSender(), 1))
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1, 256))
                        .executes(ctx -> give(plugin, ctx.getSource().getSender(), IntegerArgumentType.getInteger(ctx, "amount")))))
                .build(),
            "View the goblin hunt or give placeable goblin heads",
            List.of("goblinhunt")
        );
    }

    private static int open(SMPCore plugin, CommandSender sender) {
        if (!(sender instanceof Player player) || plugin.getGoblinHuntManager() == null) return 0;
        plugin.getGoblinHuntManager().openMenu(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int count(SMPCore plugin, CommandSender sender) {
        GoblinHuntManager manager = plugin.getGoblinHuntManager();
        if (manager == null) return 0;
        sender.sendMessage(MessageUtil.info("Active hidden goblins: <white>" + manager.activeGoblinCount() + "</white>."));
        return Command.SINGLE_SUCCESS;
    }

    private static int give(SMPCore plugin, CommandSender sender, int amount) {
        if (!(sender instanceof Player player) || plugin.getGoblinHuntManager() == null) return 0;
        int given = plugin.getGoblinHuntManager().giveHeads(player, amount);
        player.sendMessage(MessageUtil.success("Received <white>" + amount + "</white> placeable goblin head(s)."));
        if (given < amount) player.sendMessage(MessageUtil.info("Inventory overflow was dropped safely at your feet."));
        return Command.SINGLE_SUCCESS;
    }
}
