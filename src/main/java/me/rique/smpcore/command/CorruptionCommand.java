package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.item.CorruptionManager;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class CorruptionCommand {

    private CorruptionCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("corruptionstation")
                .requires(src -> src.getSender().hasPermission("smpcore.corruption.admin"))
                .executes(ctx -> {
                    sendUsage(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("give")
                    .executes(ctx -> give(plugin, ctx.getSource().getSender(), senderPlayer(ctx.getSource().getSender())))
                    .then(Commands.argument("target", ArgumentTypes.player())
                        .executes(ctx -> {
                            List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                                .resolve(ctx.getSource());
                            if (targets.isEmpty()) {
                                ctx.getSource().getSender().sendMessage(MessageUtil.error("Player not found."));
                                return 0;
                            }
                            return give(plugin, ctx.getSource().getSender(), targets.get(0));
                        })))
                .then(Commands.literal("list")
                    .executes(ctx -> list(plugin, ctx.getSource().getSender())))
                .build(),
            "Manage Corruption Anchors",
            List.of("corruptor", "corruptionanchor")
        );
    }

    private static int give(SMPCore plugin, CommandSender sender, Player target) {
        if (target == null) {
            sender.sendMessage(MessageUtil.error("Use /corruptionstation give <player> from console."));
            return 0;
        }
        CorruptionManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        ItemStack item = manager.createStationItem();
        if (plugin.getItemAuditManager() != null) {
            plugin.getItemAuditManager().recordKnownAcquisition(target, item.clone(), sender, "admin_give", "Given via corruption station command.");
        }
        target.getInventory().addItem(item).values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        target.sendMessage(MessageUtil.success("You received a <white>Corruption Anchor</white>."));
        if (!sender.equals(target)) {
            sender.sendMessage(MessageUtil.success("Gave a <white>Corruption Anchor</white> to <white>" + target.getName() + "</white>."));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int list(SMPCore plugin, CommandSender sender) {
        CorruptionManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        List<CorruptionManager.BlockKey> stations = manager.stationLocations();
        sender.sendMessage(MessageUtil.info("Corruption Anchors: <white>" + stations.size() + "</white>."));
        for (int i = 0; i < Math.min(8, stations.size()); i++) {
            Location location = stations.get(i).location();
            if (location == null || location.getWorld() == null) {
                sender.sendMessage(MessageUtil.info("<white>" + (i + 1) + ".</white> world not loaded"));
                continue;
            }
            sender.sendMessage(MessageUtil.info(
                "<white>" + (i + 1) + ".</white> "
                    + location.getWorld().getName() + " "
                    + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ()
            ));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static CorruptionManager manager(SMPCore plugin, CommandSender sender) {
        CorruptionManager manager = plugin.getCorruptionManager();
        if (manager == null) {
            sender.sendMessage(MessageUtil.error("Corruption system is not ready yet."));
        }
        return manager;
    }

    private static Player senderPlayer(CommandSender sender) {
        return sender instanceof Player player ? player : null;
    }

    private static void sendUsage(CommandSender sender) {
        sender.sendMessage(MessageUtil.info("<white>/corruptionstation give [player]</white> - give the station item."));
        sender.sendMessage(MessageUtil.info("<white>/corruptionstation list</white> - list placed anchors."));
    }
}
