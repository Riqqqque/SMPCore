package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.item.ReforgeManager;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class ReforgeCommand {

    private ReforgeCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("reforger")
                .requires(src -> src.getSender().hasPermission("smpcore.reforge.admin"))
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
                .then(Commands.literal("stone")
                    .executes(ctx -> giveStone(plugin, ctx.getSource().getSender(), senderPlayer(ctx.getSource().getSender())))
                    .then(Commands.argument("target", ArgumentTypes.player())
                        .executes(ctx -> {
                            List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                                .resolve(ctx.getSource());
                            if (targets.isEmpty()) {
                                ctx.getSource().getSender().sendMessage(MessageUtil.error("Player not found."));
                                return 0;
                            }
                            return giveStone(plugin, ctx.getSource().getSender(), targets.get(0));
                        })))
                .build(),
            "Manage Brannik and the Reforge Stone",
            List.of("dwarfnpc", "dwarfreforger")
        );

        commands.register(
            Commands.literal("spawnreforger")
                .requires(src -> src.getSender().hasPermission("smpcore.reforge.admin"))
                .executes(ctx -> spawn(plugin, ctx.getSource().getSender()))
                .build(),
            "Spawn Brannik"
        );
    }

    private static int spawn(SMPCore plugin, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Must be a player."));
            return 0;
        }
        ReforgeManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        Location location = manager.spawnDwarf(player.getLocation()).getLocation();
        player.sendMessage(MessageUtil.success(ReforgeManager.NPC_DISPLAY_NAME + " spawned at " + locationSummary(location) + "."));
        return Command.SINGLE_SUCCESS;
    }

    private static int remove(SMPCore plugin, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Must be a player."));
            return 0;
        }
        ReforgeManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        int removed = manager.removeNearestDwarf(player.getLocation(), 6.0);
        if (removed == 0) {
            player.sendMessage(MessageUtil.warn("No reforger was found nearby."));
            return 0;
        }
        player.sendMessage(MessageUtil.success("Nearest " + ReforgeManager.NPC_DISPLAY_NAME + " removed."));
        return Command.SINGLE_SUCCESS;
    }

    private static int list(SMPCore plugin, CommandSender sender) {
        ReforgeManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        List<Location> locations = manager.dwarfLocations();
        sender.sendMessage(MessageUtil.info(ReforgeManager.NPC_DISPLAY_NAME + " NPCs: <white>" + locations.size() + "</white>."));
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
        ReforgeManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        int refreshed = manager.refreshDwarfs();
        sender.sendMessage(MessageUtil.success("Refreshed <white>" + refreshed + "</white> " + ReforgeManager.NPC_DISPLAY_NAME + " NPCs."));
        return Command.SINGLE_SUCCESS;
    }

    private static int giveStone(SMPCore plugin, CommandSender sender, Player target) {
        if (target == null) {
            sender.sendMessage(MessageUtil.error("Use /reforger stone <player> from console."));
            return 0;
        }
        ReforgeManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        ItemStack stone = manager.createReforgeStone(ReforgeManager.STONE_ID);
        String displayName = manager.displayNameForStone(ReforgeManager.STONE_ID);
        if (stone == null || displayName == null) {
            sender.sendMessage(MessageUtil.error("Reforge stone is not ready yet."));
            return 0;
        }
        if (plugin.getItemAuditManager() != null) {
            plugin.getItemAuditManager().recordKnownAcquisition(target, stone.clone(), sender, "admin_give", "Given via reforger command.");
        }
        target.getInventory().addItem(stone).values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        target.sendMessage(MessageUtil.success("You received <white>" + displayName + "</white>."));
        if (!sender.equals(target)) {
            sender.sendMessage(MessageUtil.success("Gave <white>" + displayName + "</white> to <white>" + target.getName() + "</white>."));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static ReforgeManager manager(SMPCore plugin, CommandSender sender) {
        ReforgeManager manager = plugin.getReforgeManager();
        if (manager == null) {
            sender.sendMessage(MessageUtil.error("Reforge system is not ready yet."));
        }
        return manager;
    }

    private static Player senderPlayer(CommandSender sender) {
        return sender instanceof Player player ? player : null;
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
        sender.sendMessage(MessageUtil.info("<white>/reforger spawn</white> - spawn the dwarf here."));
        sender.sendMessage(MessageUtil.info("<white>/reforger remove</white> - remove the nearest dwarf."));
        sender.sendMessage(MessageUtil.info("<white>/reforger refresh</white> - refresh dwarf skins."));
        sender.sendMessage(MessageUtil.info("<white>/reforger stone [player]</white> - give a reforge stone."));
    }
}
