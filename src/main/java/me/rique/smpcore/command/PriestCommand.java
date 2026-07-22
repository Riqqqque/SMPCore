package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.essence.PriestManager;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class PriestCommand {

    private PriestCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("priest")
                .requires(src -> src.getSender().hasPermission("smpcore.priest.admin"))
                .executes(ctx -> {
                    sendUsage(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("spawn")
                    .executes(ctx -> spawn(plugin, ctx.getSource().getSender())))
                .then(Commands.literal("remove")
                    .executes(ctx -> remove(plugin, ctx.getSource().getSender())))
                .then(Commands.literal("list")
                    .executes(ctx -> list(plugin, ctx.getSource().getSender())))
                .then(Commands.literal("refresh")
                    .executes(ctx -> refresh(plugin, ctx.getSource().getSender())))
                .build(),
            "Manage Father Aldren NPCs",
            List.of("priestnpc", "veilpriest")
        );

        commands.register(
            Commands.literal("spawnpriest")
                .requires(src -> src.getSender().hasPermission("smpcore.priest.admin"))
                .executes(ctx -> spawn(plugin, ctx.getSource().getSender()))
                .build(),
            "Spawn Father Aldren"
        );
    }

    private static int spawn(SMPCore plugin, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Must be a player."));
            return 0;
        }
        PriestManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        Location location = manager.spawnPriest(player.getLocation()).getLocation();
        player.sendMessage(MessageUtil.success(PriestManager.NPC_DISPLAY_NAME + " spawned at " + locationSummary(location) + "."));
        return Command.SINGLE_SUCCESS;
    }

    private static int remove(SMPCore plugin, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Must be a player."));
            return 0;
        }
        PriestManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        int removed = manager.removeNearestPriest(player.getLocation(), 6.0);
        if (removed == 0) {
            player.sendMessage(MessageUtil.warn("No priest was found nearby."));
            return 0;
        }
        player.sendMessage(MessageUtil.success("Nearest " + PriestManager.NPC_DISPLAY_NAME + " removed."));
        return Command.SINGLE_SUCCESS;
    }

    private static int list(SMPCore plugin, CommandSender sender) {
        PriestManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        List<Location> locations = manager.priestLocations();
        sender.sendMessage(MessageUtil.info(PriestManager.NPC_DISPLAY_NAME + " NPCs: <white>" + locations.size() + "</white>."));
        for (int i = 0; i < Math.min(8, locations.size()); i++) {
            Location location = locations.get(i);
            sender.sendMessage(MessageUtil.info(
                "<white>" + (i + 1) + ".</white> "
                    + location.getWorld().getName() + " "
                    + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ()
            ));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int refresh(SMPCore plugin, CommandSender sender) {
        PriestManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        int refreshed = manager.refreshPriests();
        sender.sendMessage(MessageUtil.success("Refreshed <white>" + refreshed + "</white> " + PriestManager.NPC_DISPLAY_NAME + " NPCs."));
        return Command.SINGLE_SUCCESS;
    }

    private static PriestManager manager(SMPCore plugin, CommandSender sender) {
        PriestManager manager = plugin.getPriestManager();
        if (manager == null) {
            sender.sendMessage(MessageUtil.error("Priest system is not ready yet."));
        }
        return manager;
    }

    private static String locationSummary(Location location) {
        return location.getWorld().getName()
            + " "
            + location.getBlockX()
            + ", "
            + location.getBlockY()
            + ", "
            + location.getBlockZ();
    }

    private static void sendUsage(CommandSender sender) {
        sender.sendMessage(MessageUtil.info("<white>/priest spawn</white> - spawn the priest here."));
        sender.sendMessage(MessageUtil.info("<white>/priest remove</white> - remove the nearest priest."));
        sender.sendMessage(MessageUtil.info("<white>/priest list</white> - list placed priests."));
        sender.sendMessage(MessageUtil.info("<white>/priest refresh</white> - refresh priest skins."));
    }
}
