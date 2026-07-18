package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.npc.GuideNpcManager;
import me.rique.smpcore.npc.GuideNpcManager.GuideNpcType;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class SpawnLifeCommand {

    private static final List<SpawnLifeType> TYPES = List.of(
        new SpawnLifeType("dog", GuideNpcType.FETCH_HOUND),
        new SpawnLifeType("cat", GuideNpcType.TOWN_CAT),
        new SpawnLifeType("fox", GuideNpcType.TOWN_FOX),
        new SpawnLifeType("parrot", GuideNpcType.TOWN_PARROT),
        new SpawnLifeType("illusioner", GuideNpcType.HIDDEN_ILLUSIONER),
        new SpawnLifeType("baker", GuideNpcType.TOWN_BAKER),
        new SpawnLifeType("mason", GuideNpcType.TOWN_MASON),
        new SpawnLifeType("courier", GuideNpcType.TOWN_COURIER),
        new SpawnLifeType("dockhand", GuideNpcType.TOWN_DOCKHAND),
        new SpawnLifeType("seamstress", GuideNpcType.TOWN_SEAMSTRESS),
        new SpawnLifeType("tavern_host", GuideNpcType.TAVERN_HOST),
        new SpawnLifeType("tavern_regular", GuideNpcType.TAVERN_REGULAR),
        new SpawnLifeType("tavern_tipsy", GuideNpcType.TAVERN_TIPSY)
    );

    private SpawnLifeCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        var spawn = Commands.literal("spawn");
        var remove = Commands.literal("remove");
        var list = Commands.literal("list")
            .executes(ctx -> listAll(plugin, ctx.getSource().getSender()));
        for (SpawnLifeType type : TYPES) {
            spawn.then(Commands.literal(type.commandName())
                .executes(ctx -> spawn(plugin, type.type(), ctx.getSource().getSender())));
            remove.then(Commands.literal(type.commandName())
                .executes(ctx -> remove(plugin, type.type(), ctx.getSource().getSender())));
            list.then(Commands.literal(type.commandName())
                .executes(ctx -> list(plugin, type.type(), ctx.getSource().getSender())));
        }

        commands.register(
            Commands.literal("spawnlife")
                .requires(source -> source.getSender().hasPermission("smpcore.spawnlife.admin"))
                .executes(ctx -> usage(ctx.getSource().getSender()))
                .then(spawn)
                .then(remove)
                .then(list)
                .then(Commands.literal("refresh")
                    .executes(ctx -> refresh(plugin, ctx.getSource().getSender())))
                .build(),
            "Place and manage ambient spawn NPCs",
            List.of("ambientnpc", "townnpc")
        );
    }

    private static int spawn(SMPCore plugin, GuideNpcType type, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Must be a player."));
            return 0;
        }
        GuideNpcManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        Entity entity = manager.spawnNpc(type, player.getLocation());
        player.sendMessage(MessageUtil.success(type.displayName() + " spawned at " + locationSummary(entity.getLocation()) + "."));
        return Command.SINGLE_SUCCESS;
    }

    private static int remove(SMPCore plugin, GuideNpcType type, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Must be a player."));
            return 0;
        }
        GuideNpcManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        int removed = manager.removeNearest(type, player.getLocation(), 6.0D);
        if (removed == 0) {
            player.sendMessage(MessageUtil.warn("No nearby " + type.displayName() + " was found."));
            return 0;
        }
        player.sendMessage(MessageUtil.success("Nearest " + type.displayName() + " removed."));
        return Command.SINGLE_SUCCESS;
    }

    private static int listAll(SMPCore plugin, CommandSender sender) {
        GuideNpcManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        int total = 0;
        for (SpawnLifeType type : TYPES) {
            int count = manager.locations(type.type()).size();
            total += count;
            sender.sendMessage(MessageUtil.info(
                "<white>" + type.commandName() + ":</white> " + count + " placed"
            ));
        }
        sender.sendMessage(MessageUtil.success("Ambient NPC total: <white>" + total + "</white>."));
        return Command.SINGLE_SUCCESS;
    }

    private static int list(SMPCore plugin, GuideNpcType type, CommandSender sender) {
        GuideNpcManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        List<Location> locations = manager.locations(type);
        sender.sendMessage(MessageUtil.info(type.displayName() + " placements: <white>" + locations.size() + "</white>."));
        for (int index = 0; index < Math.min(10, locations.size()); index++) {
            sender.sendMessage(MessageUtil.info(
                "<white>" + (index + 1) + ".</white> " + locationSummary(locations.get(index))
            ));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int refresh(SMPCore plugin, CommandSender sender) {
        GuideNpcManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        int refreshed = manager.refreshNpcs();
        sender.sendMessage(MessageUtil.success("Refreshed <white>" + refreshed + "</white> NPCs."));
        return Command.SINGLE_SUCCESS;
    }

    private static int usage(CommandSender sender) {
        sender.sendMessage(MessageUtil.info("<white>/spawnlife spawn &lt;type&gt;</white> - place one here."));
        sender.sendMessage(MessageUtil.info("<white>/spawnlife remove &lt;type&gt;</white> - remove the nearest one."));
        sender.sendMessage(MessageUtil.info("<white>/spawnlife list [type]</white> - show placements."));
        sender.sendMessage(MessageUtil.info("Animals: <white>dog, cat, fox, parrot</white>"));
        sender.sendMessage(MessageUtil.info("Town: <white>baker, mason, courier, dockhand, seamstress</white>"));
        sender.sendMessage(MessageUtil.info("Tavern: <white>tavern_host, tavern_regular, tavern_tipsy</white>"));
        sender.sendMessage(MessageUtil.info("Hidden: <white>illusioner</white>"));
        return Command.SINGLE_SUCCESS;
    }

    private static GuideNpcManager manager(SMPCore plugin, CommandSender sender) {
        GuideNpcManager manager = plugin.getGuideNpcManager();
        if (manager == null) {
            sender.sendMessage(MessageUtil.error("NPC system is not ready yet."));
        }
        return manager;
    }

    private static String locationSummary(Location location) {
        return location.getWorld().getName() + " "
            + location.getBlockX() + ", "
            + location.getBlockY() + ", "
            + location.getBlockZ();
    }

    private record SpawnLifeType(String commandName, GuideNpcType type) {
    }
}
