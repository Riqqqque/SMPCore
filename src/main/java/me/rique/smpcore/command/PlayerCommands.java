package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.item.CustomEnchantListener;
import me.rique.smpcore.item.VeinMinerListener;
import me.rique.smpcore.power.SuperpowerManager;
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
        registerTeamVault(commands, plugin);
        registerTop(commands, plugin);
        registerSuicide(commands, plugin);
        registerShadow(commands, plugin);
        registerXray(commands, plugin);
        registerVoidstep(commands, plugin);
        registerVoidVision(commands, plugin);
        registerTravel(commands, plugin);
        registerMonarchSummon(commands, plugin);
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

    private static void registerTeamVault(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("tvault")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.team"))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    plugin.getTeamManager().openTeamVault(player);
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Open your team vault",
            List.of("teamvault")
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
                    SuperpowerManager powers = plugin.getSuperpowerManager();
                    if (powers != null && powers.hasPower(player, me.rique.smpcore.power.SuperpowerType.IMMORTALITY)) {
                        player.sendMessage(MessageUtil.warn("Nothing happens."));
                        return 0;
                    }
                    player.setHealth(0.0);
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Kill yourself"
        );
    }

    private static void registerShadow(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("shadow")
                .requires(src -> src.getSender() instanceof Player)
                .executes(ctx -> toggleShadow(plugin, (Player) ctx.getSource().getSender()))
                .then(Commands.literal("toggle")
                    .executes(ctx -> toggleShadow(plugin, (Player) ctx.getSource().getSender())))
                .build(),
            "Toggle the Shadow power"
        );
    }

    private static int toggleShadow(SMPCore plugin, Player player) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            player.sendMessage(MessageUtil.error("Power system is not ready yet."));
            return 0;
        }
        return powers.handleShadowCommand(player) ? Command.SINGLE_SUCCESS : 0;
    }

    private static void registerXray(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("xray")
                .requires(src -> src.getSender() instanceof Player)
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    SuperpowerManager powers = plugin.getSuperpowerManager();
                    if (powers == null) {
                        player.sendMessage(MessageUtil.error("Power system is not ready yet."));
                        return 0;
                    }
                    return powers.handleXrayCommand(player) ? Command.SINGLE_SUCCESS : 0;
                })
                .build(),
            "Trigger Oracle Eye"
        );
    }

    private static void registerVoidstep(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("voidstep")
                .requires(src -> src.getSender() instanceof Player)
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    SuperpowerManager powers = plugin.getSuperpowerManager();
                    if (powers == null) {
                        player.sendMessage(MessageUtil.error("Power system is not ready yet."));
                        return 0;
                    }
                    return powers.handleVoidstepCommand(player) ? Command.SINGLE_SUCCESS : 0;
                })
                .build(),
            "Blink forward with Voidwalker",
            List.of("vstep")
        );
    }

    private static void registerVoidVision(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("voidvision")
                .requires(src -> src.getSender() instanceof Player)
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    SuperpowerManager powers = plugin.getSuperpowerManager();
                    if (powers == null) {
                        player.sendMessage(MessageUtil.error("Power system is not ready yet."));
                        return 0;
                    }
                    return powers.handleVoidwalkerNightVisionCommand(player) ? Command.SINGLE_SUCCESS : 0;
                })
                .then(Commands.literal("toggle")
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        SuperpowerManager powers = plugin.getSuperpowerManager();
                        if (powers == null) {
                            player.sendMessage(MessageUtil.error("Power system is not ready yet."));
                            return 0;
                        }
                        return powers.handleVoidwalkerNightVisionCommand(player) ? Command.SINGLE_SUCCESS : 0;
                    }))
                .build(),
            "Toggle Voidwalker night vision",
            List.of("vvision", "voidnv")
        );
    }

    private static void registerTravel(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("travel")
                .requires(src -> src.getSender() instanceof Player)
                .then(Commands.literal("close")
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        SuperpowerManager powers = plugin.getSuperpowerManager();
                        if (powers == null) {
                            player.sendMessage(MessageUtil.error("Power system is not ready yet."));
                            return 0;
                        }
                        return powers.handleTravelCloseCommand(player) ? Command.SINGLE_SUCCESS : 0;
                    }))
                .then(Commands.argument("x", IntegerArgumentType.integer())
                    .then(Commands.argument("y", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                            .then(Commands.argument("dimension", StringArgumentType.word())
                                .executes(ctx -> {
                                    Player player = (Player) ctx.getSource().getSender();
                                    SuperpowerManager powers = plugin.getSuperpowerManager();
                                    if (powers == null) {
                                        player.sendMessage(MessageUtil.error("Power system is not ready yet."));
                                        return 0;
                                    }
                                    int x = IntegerArgumentType.getInteger(ctx, "x");
                                    int y = IntegerArgumentType.getInteger(ctx, "y");
                                    int z = IntegerArgumentType.getInteger(ctx, "z");
                                    String dimension = StringArgumentType.getString(ctx, "dimension");
                                    return powers.handleTravelCommand(player, x, y, z, dimension) ? Command.SINGLE_SUCCESS : 0;
                                })))))
                .build(),
            "Open a Traveler portal"
        );
    }

    private static void registerMonarchSummon(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("msummon")
                .requires(src -> src.getSender() instanceof Player)
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 15))
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        SuperpowerManager powers = plugin.getSuperpowerManager();
                        if (powers == null) {
                            player.sendMessage(MessageUtil.error("Power system is not ready yet."));
                            return 0;
                        }
                        int amount = IntegerArgumentType.getInteger(ctx, "amount");
                        return powers.handleMonarchSummonCommand(player, amount) ? Command.SINGLE_SUCCESS : 0;
                    }))
                .build(),
            "Summon stored Monarch mobs"
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
            "Teleport to the server spawn"
        );
    }

    private static void sendPlayerHelp(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add("<gold><bold>Player Commands</bold></gold>");
        if (player.hasPermission("smpcore.legendary.recipe")) {
            lines.add("<gray>Use <white>/lrecipe</white>, <white>/lrecipes</white>, or <white>/reliquary</white> to open the Reliquary.</gray>");
        }

        if (player.hasPermission("smpcore.help")) {
            lines.add("<gray><white>/help</white> - Show this help menu</gray>");
        }
        if (player.hasPermission("smpcore.legendary.recipe")) {
            lines.add("<gray><white>/lrecipe</white> (<white>/reliquary</white>) - Open the Reliquary</gray>");
            lines.add("<gray><white>/mythics</white> - View Mythic Nexus fusion pairings</gray>");
        }
        lines.add("<gray><white>/bossrituals</white> - View custom boss summon rituals</gray>");
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
        if (player.hasPermission("smpcore.startsmp")) {
            lines.add("<gray><white>/startsmp</white> - Expand the world border and start the grace timer</gray>");
        }
        if (player.hasPermission("smpcore.home")) {
            lines.add("<gray><white>/home [name]</white>, <white>/sethome [name]</white>, <white>/delhome [name]</white>, <white>/homes</white></gray>");
        }
        if (player.hasPermission("smpcore.team")) {
            lines.add("<gray><white>/team</white> - Team management commands</gray>");
            lines.add("<gray><white>/tvault</white> - Open your team vault</gray>");
        }
        if (player.hasPermission("smpcore.waystone.use")) {
            lines.add("<gray>Right-click a known waystone sign to open your teleport menu</gray>");
        }
        lines.add("<gray>Power commands unlock naturally if your hidden power uses them: <white>/shadow</white>, <white>/xray</white>, <white>/voidstep</white>, <white>/voidvision</white>, <white>/travel</white>, <white>/msummon</white>.</gray>");
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
