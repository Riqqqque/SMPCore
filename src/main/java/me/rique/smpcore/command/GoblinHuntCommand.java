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
                .then(Commands.literal("audit")
                    .requires(source -> source.getSender().hasPermission("smpcore.goblins.admin"))
                    .executes(ctx -> audit(plugin, ctx.getSource().getSender(), false))
                    .then(Commands.literal("prune")
                        .executes(ctx -> audit(plugin, ctx.getSource().getSender(), true))))
                .then(Commands.literal("see")
                    .requires(source -> source.getSender().hasPermission("smpcore.goblins.admin"))
                    .executes(ctx -> vision(plugin, ctx.getSource().getSender(), null))
                    .then(Commands.literal("on")
                        .executes(ctx -> vision(plugin, ctx.getSource().getSender(), true)))
                    .then(Commands.literal("off")
                        .executes(ctx -> vision(plugin, ctx.getSource().getSender(), false)))
                    .then(Commands.literal("refresh")
                        .executes(ctx -> refreshVision(plugin, ctx.getSource().getSender()))))
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

    private static int audit(SMPCore plugin, CommandSender sender, boolean pruneInvalid) {
        GoblinHuntManager manager = plugin.getGoblinHuntManager();
        if (manager == null) return 0;
        return manager.auditPlacements(sender, pruneInvalid) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int vision(SMPCore plugin, CommandSender sender, Boolean requestedState) {
        GoblinHuntManager manager = plugin.getGoblinHuntManager();
        if (!(sender instanceof Player player) || manager == null) {
            sender.sendMessage(MessageUtil.error("Goblin sight can only be used by a player."));
            return 0;
        }
        boolean enabled = requestedState == null ? !manager.isAdminVisionEnabled(player) : requestedState;
        int visible = manager.setAdminVision(player, enabled);
        if (!enabled) {
            player.sendMessage(MessageUtil.info("Goblin sight disabled."));
            return Command.SINGLE_SUCCESS;
        }
        player.sendMessage(MessageUtil.success("Goblin sight enabled. <white>" + visible
            + "</white> loaded head(s) are marked through walls; markers update as you move."));
        player.sendMessage(MessageUtil.info("Run <white>/goblins see</white> again to hide them."));
        return Command.SINGLE_SUCCESS;
    }

    private static int refreshVision(SMPCore plugin, CommandSender sender) {
        GoblinHuntManager manager = plugin.getGoblinHuntManager();
        if (!(sender instanceof Player player) || manager == null) {
            sender.sendMessage(MessageUtil.error("Goblin sight can only be used by a player."));
            return 0;
        }
        int visible = manager.refreshAdminVision(player);
        player.sendMessage(MessageUtil.info("Refreshed goblin sight: <white>" + visible + "</white> loaded marker(s)."));
        return Command.SINGLE_SUCCESS;
    }
}
