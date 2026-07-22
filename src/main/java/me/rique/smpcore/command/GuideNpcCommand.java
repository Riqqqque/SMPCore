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
public final class GuideNpcCommand {

    private GuideNpcCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        List<Registration> registrations = List.of(
            new Registration(GuideNpcType.SPAWN_GUIDE, "spawnnpc", "Manage Mira the Guide", "spawnguide", "guide"),
            new Registration(GuideNpcType.CORRUPTION_WARDEN, "corruptionwarden", "Manage Veyr", "wardennpc", "warden"),
            new Registration(GuideNpcType.MAYOR, "mayor", "Manage Mayor Bah", "mayornpc"),
            new Registration(GuideNpcType.GEAR_EXPERT, "artificer", "Manage Orin the Artificer", "gearguide", "gearmentor"),
            new Registration(GuideNpcType.DUNGEON_KEEPER, "dungeonkeeper", "Manage Malakar the Gatekeeper", "dungeonnpc", "bosskeeper"),
            new Registration(GuideNpcType.BREWMASTER, "brewmaster", "Manage Bram the Brewmaster", "brewmasternpc"),
            new Registration(GuideNpcType.CARDSHARP, "adventurer", "Manage Rook the Retired Adventurer", "cardsharp", "cardsharpnpc", "adventurernpc"),
            new Registration(GuideNpcType.DEALER, "dealer", "Manage Silas the Dealer", "dealernpc", "blackjackdealer"),
            new Registration(GuideNpcType.ROULETTE_CROUPIER, "croupier", "Manage Renn the Croupier", "roulettecroupier", "roulettedealer"),
            new Registration(GuideNpcType.DUELMASTER, "duelmaster", "Manage Cassian the Fightmaster", "duelnpc", "fightmaster"),
            new Registration(GuideNpcType.GOBLIN_HUNTER, "goblinhunter", "Manage Grikk the Goblin Hunter", "goblinhunternpc"),
            new Registration(GuideNpcType.MINER, "miner", "Manage Torren the Miner", "minernpc"),
            new Registration(GuideNpcType.FARMER, "farmer", "Manage Rowan the Farmer", "farmernpc"),
            new Registration(GuideNpcType.WITCH, "witch", "Manage Vespera the Hedge-Witch", "witchnpc"),
            new Registration(GuideNpcType.OVERSEER, "overseer", "Manage the Veil Overseer", "overseernpc"),
            new Registration(GuideNpcType.BEASTWARDEN, "beastwarden", "Manage Kael the Beastwarden", "beastwardennpc", "beastnpc"),
            new Registration(GuideNpcType.BOSSBROKER, "bossbroker", "Manage Mogrik the Bossbroker", "bossbrokernpc", "mogrik"),
            new Registration(GuideNpcType.BLACK_MARKETEER, "blackmarket", "Manage Sable the Curio Broker", "blackmarketnpc", "curiobroker", "sable"),
            new Registration(GuideNpcType.FISHER, "fisher", "Manage Corin the Fisher", "fishernpc")
        );
        registrations.forEach(registration -> registerType(
            commands,
            plugin,
            registration.type(),
            registration.root(),
            registration.description(),
            registration.aliases()
        ));
    }

    private static void registerType(Commands commands, SMPCore plugin, GuideNpcType type, String root, String description, List<String> aliases) {
        commands.register(
            Commands.literal(root)
                .requires(src -> src.getSender().hasPermission("smpcore.guide.admin"))
                .executes(ctx -> {
                    sendUsage(ctx.getSource().getSender(), root);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("spawn")
                    .executes(ctx -> spawn(plugin, type, ctx.getSource().getSender())))
                .then(Commands.literal("remove")
                    .executes(ctx -> remove(plugin, type, ctx.getSource().getSender())))
                .then(Commands.literal("list")
                    .executes(ctx -> list(plugin, type, ctx.getSource().getSender())))
                .then(Commands.literal("refresh")
                    .executes(ctx -> refresh(plugin, ctx.getSource().getSender())))
                .build(),
            description,
            aliases
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
        Location location = entity.getLocation();
        player.sendMessage(MessageUtil.success(type.displayName() + " spawned at " + locationSummary(location) + "."));
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
        int removed = manager.removeNearest(type, player.getLocation(), 6.0);
        if (removed == 0) {
            player.sendMessage(MessageUtil.warn("No " + type.displayName() + " NPC was found nearby."));
            return 0;
        }
        player.sendMessage(MessageUtil.success("Nearest " + type.displayName() + " removed."));
        return Command.SINGLE_SUCCESS;
    }

    private static int list(SMPCore plugin, GuideNpcType type, CommandSender sender) {
        GuideNpcManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        List<Location> locations = manager.locations(type);
        sender.sendMessage(MessageUtil.info(type.displayName() + " NPCs: <white>" + locations.size() + "</white>."));
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
        GuideNpcManager manager = manager(plugin, sender);
        if (manager == null) {
            return 0;
        }
        int refreshed = manager.refreshNpcs();
        sender.sendMessage(MessageUtil.success("Refreshed <white>" + refreshed + "</white> guide NPCs."));
        return Command.SINGLE_SUCCESS;
    }

    private static GuideNpcManager manager(SMPCore plugin, CommandSender sender) {
        GuideNpcManager manager = plugin.getGuideNpcManager();
        if (manager == null) {
            sender.sendMessage(MessageUtil.error("Guide NPC system is not ready yet."));
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

    private static void sendUsage(CommandSender sender, String root) {
        sender.sendMessage(MessageUtil.info("<white>/" + root + " spawn</white> - spawn this NPC here."));
        sender.sendMessage(MessageUtil.info("<white>/" + root + " remove</white> - remove the nearest one."));
        sender.sendMessage(MessageUtil.info("<white>/" + root + " list</white> - list placed NPCs."));
        sender.sendMessage(MessageUtil.info("<white>/" + root + " refresh</white> - refresh Citizens data."));
    }

    private record Registration(
        GuideNpcType type,
        String root,
        String description,
        List<String> aliases
    ) {
        private Registration(GuideNpcType type, String root, String description, String... aliases) {
            this(type, root, description, List.of(aliases));
        }
    }
}
