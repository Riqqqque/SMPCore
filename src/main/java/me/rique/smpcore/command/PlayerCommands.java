package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.item.CustomEnchantListener;
import me.rique.smpcore.item.VeinMinerListener;
import me.rique.smpcore.util.LocationUtil;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Survival-accessible player commands: /top, /suicide, /back, /spawn.
 */
@SuppressWarnings("UnstableApiUsage")
public final class PlayerCommands {

    private PlayerCommands() {}

    public static void register(Commands commands, SMPCore plugin) {
        registerHelp(commands);
        registerEnchants(commands, plugin);
        registerVeinMiner(commands, plugin);
        registerTop(commands, plugin);
        registerSuicide(commands, plugin);
        registerBack(commands, plugin);
        registerSpawn(commands, plugin);
    }

    // /help - show player command help menu
    private static void registerHelp(Commands commands) {
        commands.register(
            Commands.literal("help")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.help"))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    sendPlayerHelp(player);
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Show available player commands",
            List.of("smphelp")
        );
    }

    private static void registerEnchants(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("enchants")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.enchants"))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    CustomEnchantListener listener = plugin.getCustomEnchantListener();
                    if (listener == null) {
                        player.sendMessage(MessageUtil.error("Custom enchant menu is not ready yet."));
                        return 0;
                    }

                    listener.openEnchantMenu(player);
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Open the custom enchant menu",
            List.of("enchantments")
        );
    }

    private static void registerVeinMiner(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("veinminer")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.veinminer.use"))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    VeinMinerListener veinMiner = plugin.getVeinMinerListener();
                    if (veinMiner == null) {
                        player.sendMessage(MessageUtil.error("Vein miner is not ready yet."));
                        return 0;
                    }

                    boolean enabled = veinMiner.toggle(player);
                    player.sendMessage(MessageUtil.success(
                        "Vein miner <white>" + (enabled ? "enabled" : "disabled") + "</white>."
                    ));
                    if (enabled && plugin.getConfigManager().veinMinerRequireSneak) {
                        player.sendMessage(MessageUtil.info("Sneak while mining to activate it."));
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("on")
                    .executes(ctx -> setVeinMiner(plugin, (Player) ctx.getSource().getSender(), true)))
                .then(Commands.literal("off")
                    .executes(ctx -> setVeinMiner(plugin, (Player) ctx.getSource().getSender(), false)))
                .then(Commands.literal("status")
                    .executes(ctx -> showVeinMinerStatus(plugin, (Player) ctx.getSource().getSender())))
                .build(),
            "Toggle vein miner",
            List.of("vm")
        );
    }

    // /top — teleport above the highest block at current x,z
    private static void registerTop(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("top")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.top"))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    plugin.getPlayerManager().saveBackLocation(player);
                    Location dest = LocationUtil.getTopLocation(player.getLocation());
                    player.teleportAsync(dest).thenAccept(ok -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) return;
                            if (ok) player.sendMessage(MessageUtil.success("Teleported to the top."));
                            else    player.sendMessage(MessageUtil.error("Teleport failed."));
                        });
                    });
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Teleport above the highest block at your position"
        );
    }

    // /suicide — kill the player
    private static void registerSuicide(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("suicide")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.suicide"))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    player.setHealth(0.0);
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Kill yourself"
        );
    }

    // /back — return to last death or pre-teleport location
    private static void registerBack(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("back")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.back"))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    Location back = plugin.getPlayerManager().getBackLocation(player.getUniqueId());
                    if (back == null) {
                        player.sendMessage(MessageUtil.error("No previous location saved."));
                        return 0;
                    }
                    plugin.getPlayerManager().saveBackLocation(player);
                    player.teleportAsync(back).thenAccept(ok -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) return;
                            if (ok) player.sendMessage(MessageUtil.success("Returned to previous location."));
                            else    player.sendMessage(MessageUtil.error("Teleport failed."));
                        });
                    });
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Return to your last death or teleport location"
        );
    }

    // /spawn — teleport to world spawn
    private static void registerSpawn(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("spawn")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.spawn"))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    World world = Bukkit.getWorld(plugin.getConfigManager().spawnWorld);
                    if (world == null) {
                        player.sendMessage(MessageUtil.error("Spawn world is not loaded."));
                        return 0;
                    }
                    plugin.getPlayerManager().saveBackLocation(player);
                    player.teleportAsync(world.getSpawnLocation()).thenAccept(ok -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) return;
                            if (ok) player.sendMessage(MessageUtil.success("Teleported to spawn."));
                            else    player.sendMessage(MessageUtil.error("Teleport failed."));
                        });
                    });
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Teleport to the server spawn",
            List.of("hub")
        );
    }

    private static void sendPlayerHelp(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add("<gold><bold>Player Commands</bold></gold>");
        if (player.hasPermission("smpcore.legendary.recipe")) {
            lines.add("<gray>Use <white>/lrecipe</white> or <white>/lrecipes</white> to view legendary recipes.</gray>");
        }

        if (player.hasPermission("smpcore.help")) {
            lines.add("<gray><white>/help</white> - Show this help menu</gray>");
        }
        if (player.hasPermission("smpcore.legendary.recipe")) {
            lines.add("<gray><white>/lrecipe</white> (<white>/lrecipes</white>) - Open legendary recipe menu</gray>");
        }
        if (player.hasPermission("smpcore.enchants")) {
            lines.add("<gray><white>/enchants</white> - View custom enchants</gray>");
        }
        if (player.hasPermission("smpcore.veinminer.use")) {
            lines.add("<gray><white>/veinminer</white> - Toggle vein miner</gray>");
        }
        if (player.hasPermission("smpcore.spawn")) {
            lines.add("<gray><white>/spawn</white> - Teleport to spawn</gray>");
        }
        if (player.hasPermission("smpcore.back")) {
            lines.add("<gray><white>/back</white> - Return to your last saved location</gray>");
        }
        if (player.hasPermission("smpcore.top")) {
            lines.add("<gray><white>/top</white> - Teleport above the highest block</gray>");
        }
        if (player.hasPermission("smpcore.home")) {
            lines.add("<gray><white>/home [name]</white>, <white>/sethome [name]</white>, <white>/delhome [name]</white>, <white>/homes</white></gray>");
        }
        if (player.hasPermission("smpcore.team")) {
            lines.add("<gray><white>/team</white> - Team management commands</gray>");
        }
        if (player.hasPermission("smpcore.tpa")) {
            lines.add("<gray><white>/tpa</white>, <white>/tpahere</white>, <white>/tpaccept</white>, <white>/tpdeny</white>, <white>/tpacancel</white></gray>");
        }
        if (player.hasPermission("smpcore.waystone.use")) {
            lines.add("<gray>Right-click a known waystone sign to open your teleport menu</gray>");
        }
        if (player.hasPermission("smpcore.backpack.use")) {
            lines.add("<gray>Right-click a <white>Backpack</white> to open portable storage</gray>");
        }
        if (player.hasPermission("smpcore.suicide")) {
            lines.add("<gray><white>/suicide</white> - Instantly respawn</gray>");
        }

        for (String line : lines) {
            player.sendMessage(MessageUtil.prefixedRaw(line));
        }
    }

    private static int setVeinMiner(SMPCore plugin, Player player, boolean enabled) {
        VeinMinerListener veinMiner = plugin.getVeinMinerListener();
        if (veinMiner == null) {
            player.sendMessage(MessageUtil.error("Vein miner is not ready yet."));
            return 0;
        }

        veinMiner.setEnabled(player, enabled);
        player.sendMessage(MessageUtil.success(
            "Vein miner <white>" + (enabled ? "enabled" : "disabled") + "</white>."
        ));
        if (enabled && plugin.getConfigManager().veinMinerRequireSneak) {
            player.sendMessage(MessageUtil.info("Sneak while mining to activate it."));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int showVeinMinerStatus(SMPCore plugin, Player player) {
        VeinMinerListener veinMiner = plugin.getVeinMinerListener();
        if (veinMiner == null) {
            player.sendMessage(MessageUtil.error("Vein miner is not ready yet."));
            return 0;
        }

        boolean enabled = veinMiner.isEnabledFor(player);
        player.sendMessage(MessageUtil.info(
            "Vein miner: <white>" + (enabled ? "enabled" : "disabled") + "</white>."
        ));
        if (enabled && plugin.getConfigManager().veinMinerRequireSneak) {
            player.sendMessage(MessageUtil.info("Current mode: <white>requires sneaking</white>."));
        }
        return Command.SINGLE_SUCCESS;
    }
}
