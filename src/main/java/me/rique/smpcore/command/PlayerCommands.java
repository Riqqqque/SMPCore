package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.item.CustomEnchantListener;
import me.rique.smpcore.item.VeinMinerListener;
import me.rique.smpcore.power.SuperpowerManager;
import me.rique.smpcore.util.LocationUtil;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

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
        registerTeamGlow(commands, plugin);
        registerTop(commands, plugin);
        registerSuicide(commands, plugin);
        registerShadow(commands, plugin);
        registerXray(commands, plugin);
        registerVoidstep(commands, plugin);
        registerVoidVision(commands, plugin);
        registerStormcaller(commands, plugin);
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
                .then(Commands.literal("blocks")
                    .executes(ctx -> showVeinMinerBlocks(plugin, (Player) ctx.getSource().getSender())))
                .then(Commands.literal("list")
                    .executes(ctx -> showVeinMinerBlocks(plugin, (Player) ctx.getSource().getSender())))
                .then(Commands.literal("addblock")
                    .executes(ctx -> addVeinMinerBlock(plugin, (Player) ctx.getSource().getSender(), null))
                    .then(Commands.argument("block", StringArgumentType.word())
                        .suggests(PlayerCommands::suggestBlockMaterials)
                        .executes(ctx -> addVeinMinerBlock(
                            plugin,
                            (Player) ctx.getSource().getSender(),
                            StringArgumentType.getString(ctx, "block")
                        ))))
                .then(Commands.literal("add")
                    .then(Commands.literal("block")
                        .executes(ctx -> addVeinMinerBlock(plugin, (Player) ctx.getSource().getSender(), null))
                        .then(Commands.argument("block", StringArgumentType.word())
                            .suggests(PlayerCommands::suggestBlockMaterials)
                            .executes(ctx -> addVeinMinerBlock(
                                plugin,
                                (Player) ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "block")
                            )))))
                .then(Commands.literal("removeblock")
                    .executes(ctx -> removeVeinMinerBlock(plugin, (Player) ctx.getSource().getSender(), null))
                    .then(Commands.argument("block", StringArgumentType.word())
                        .suggests(PlayerCommands::suggestBlockMaterials)
                        .executes(ctx -> removeVeinMinerBlock(
                            plugin,
                            (Player) ctx.getSource().getSender(),
                            StringArgumentType.getString(ctx, "block")
                        ))))
                .then(Commands.literal("remove")
                    .then(Commands.literal("block")
                        .executes(ctx -> removeVeinMinerBlock(plugin, (Player) ctx.getSource().getSender(), null))
                        .then(Commands.argument("block", StringArgumentType.word())
                            .suggests(PlayerCommands::suggestBlockMaterials)
                            .executes(ctx -> removeVeinMinerBlock(
                                plugin,
                                (Player) ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "block")
                            )))))
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

    private static void registerTeamGlow(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("teamglow")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.teamglow"))
                .executes(ctx -> toggleTeamGlow(plugin, (Player) ctx.getSource().getSender()))
                .then(Commands.literal("toggle")
                    .executes(ctx -> toggleTeamGlow(plugin, (Player) ctx.getSource().getSender())))
                .then(Commands.literal("on")
                    .executes(ctx -> setTeamGlow(plugin, (Player) ctx.getSource().getSender(), true)))
                .then(Commands.literal("off")
                    .executes(ctx -> setTeamGlow(plugin, (Player) ctx.getSource().getSender(), false)))
                .then(Commands.literal("status")
                    .executes(ctx -> showTeamGlowStatus(plugin, (Player) ctx.getSource().getSender())))
                .build(),
            "Privately highlight your teammates",
            List.of("allyglow", "teammateglow")
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

    private static void registerStormcaller(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("stormcaller")
                .requires(src -> src.getSender() instanceof Player)
                .executes(ctx -> setStormcallerLightning(plugin, (Player) ctx.getSource().getSender(), null))
                .then(Commands.literal("toggle")
                    .executes(ctx -> setStormcallerLightning(plugin, (Player) ctx.getSource().getSender(), null)))
                .then(Commands.literal("on")
                    .executes(ctx -> setStormcallerLightning(plugin, (Player) ctx.getSource().getSender(), true)))
                .then(Commands.literal("off")
                    .executes(ctx -> setStormcallerLightning(plugin, (Player) ctx.getSource().getSender(), false)))
                .then(Commands.literal("status")
                    .executes(ctx -> showStormcallerLightningStatus(plugin, (Player) ctx.getSource().getSender())))
                .build(),
            "Toggle Stormcaller lightning strikes",
            List.of("sclightning", "stormpower")
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
                .executes(ctx -> runMonarchSummon(plugin, (Player) ctx.getSource().getSender(), 1))
                .then(Commands.literal("despawn")
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        SuperpowerManager powers = plugin.getSuperpowerManager();
                        if (powers == null) {
                            player.sendMessage(MessageUtil.error("Power system is not ready yet."));
                            return 0;
                        }
                        return powers.handleMonarchDespawnCommand(player) ? Command.SINGLE_SUCCESS : 0;
                    }))
                .then(Commands.literal("unsummon")
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        SuperpowerManager powers = plugin.getSuperpowerManager();
                        if (powers == null) {
                            player.sendMessage(MessageUtil.error("Power system is not ready yet."));
                            return 0;
                        }
                        return powers.handleMonarchDespawnCommand(player) ? Command.SINGLE_SUCCESS : 0;
                    }))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 15))
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        int amount = IntegerArgumentType.getInteger(ctx, "amount");
                        return runMonarchSummon(plugin, player, amount);
                    }))
                .build(),
            "Summon stored Monarch mobs"
        );
    }

    private static int runMonarchSummon(SMPCore plugin, Player player, int amount) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            player.sendMessage(MessageUtil.error("Power system is not ready yet."));
            return 0;
        }
        return powers.handleMonarchSummonCommand(player, amount) ? Command.SINGLE_SUCCESS : 0;
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
        if (player.hasPermission("smpcore.menu")) {
            lines.add("<gray><white>/menu</white> - Open the main plugin menu</gray>");
        }
        if (player.hasPermission("smpcore.leaderboard")) {
            lines.add("<gray><white>/leaderboards</white> (<white>/lb</white>) - View kills, deaths, boss damage, and fight reports</gray>");
        }
        if (player.hasPermission("smpcore.legendary.recipe")) {
            lines.add("<gray><white>/lrecipe</white> (<white>/reliquary</white>) - Open the Reliquary</gray>");
            lines.add("<gray><white>/mythics</white> - View Mythic Nexus fusion pairings</gray>");
        }
        lines.add("<gray><white>/bossrituals</white> - View custom boss summon rituals</gray>");
        if (player.hasPermission("smpcore.bossbrews")) {
            lines.add("<gray><white>/bossbrews</white> - View boss-material potion recipes</gray>");
        }
        if (player.hasPermission("smpcore.wiki")) {
            lines.add("<gray><white>/wiki</white> - Open the SMPCore wiki link</gray>");
        }
        if (player.hasPermission("smpcore.enchants")) {
            lines.add("<gray><white>/enchants</white> - View custom enchants</gray>");
        }
        if (player.hasPermission("smpcore.veinminer.use")) {
            lines.add("<gray><white>/veinminer</white> - Toggle vein miner</gray>");
            lines.add("<gray><white>/veinminer addblock</white>, <white>/veinminer removeblock</white>, <white>/veinminer blocks</white> - Personal block list</gray>");
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
        if (player.hasPermission("smpcore.startsmp.lock")) {
            lines.add("<gray><white>/startsmp lock</white> - Re-enable the pre-start spawn barrier and lockdown</gray>");
        }
        if (player.hasPermission("smpcore.time")) {
            lines.add("<gray><white>/day</white>, <white>/night</white> - Change time in your current world</gray>");
        }
        if (player.hasPermission("smpcore.weather")) {
            lines.add("<gray><white>/sun</white>, <white>/storm</white> - Change weather in your current world</gray>");
        }
        if (player.hasPermission("smpcore.home")) {
            lines.add("<gray><white>/home [name]</white>, <white>/sethome [name]</white>, <white>/delhome [name]</white>, <white>/homes</white></gray>");
        }
        if (player.hasPermission("smpcore.team")) {
            lines.add("<gray><white>/team</white> - Team management commands</gray>");
            lines.add("<gray><white>/tvault</white> - Open your team vault</gray>");
        }
        if (player.hasPermission("smpcore.teamglow")) {
            lines.add("<gray><white>/teamglow</white> - Privately highlight teammates through walls</gray>");
        }
        if (player.hasPermission("smpcore.waystone.use")) {
            lines.add("<gray>Right-click a known waystone sign to open your teleport menu</gray>");
        }
        lines.add("<gray>Power commands unlock naturally if your hidden power uses them: <white>/shadow</white>, <white>/xray</white>, <white>/voidstep</white>, <white>/voidvision</white>, <white>/travel</white>, <white>/msummon</white>, <white>/msummon despawn</white>, <white>/stormcaller</white>.</gray>");
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

    private static int setStormcallerLightning(SMPCore plugin, Player player, Boolean enabled) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            player.sendMessage(MessageUtil.error("Power system is not ready yet."));
            return 0;
        }
        return powers.handleStormcallerLightningCommand(player, enabled) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int showStormcallerLightningStatus(SMPCore plugin, Player player) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            player.sendMessage(MessageUtil.error("Power system is not ready yet."));
            return 0;
        }
        return powers.handleStormcallerLightningStatusCommand(player) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int toggleTeamGlow(SMPCore plugin, Player player) {
        if (plugin.getPlayerVisualListener() == null) {
            player.sendMessage(MessageUtil.error("Player visuals are not ready yet."));
            return 0;
        }
        plugin.getPlayerVisualListener().toggleTeamGlow(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int setTeamGlow(SMPCore plugin, Player player, boolean enabled) {
        if (plugin.getPlayerVisualListener() == null) {
            player.sendMessage(MessageUtil.error("Player visuals are not ready yet."));
            return 0;
        }
        plugin.getPlayerVisualListener().setTeamGlow(player, enabled);
        return Command.SINGLE_SUCCESS;
    }

    private static int showTeamGlowStatus(SMPCore plugin, Player player) {
        if (plugin.getPlayerVisualListener() == null) {
            player.sendMessage(MessageUtil.error("Player visuals are not ready yet."));
            return 0;
        }
        boolean enabled = plugin.getPlayerVisualListener().isTeamGlowEnabled(player);
        player.sendMessage(MessageUtil.info("Teammate glow is <white>" + (enabled ? "enabled" : "disabled") + "</white>."));
        return Command.SINGLE_SUCCESS;
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

    private static int addVeinMinerBlock(SMPCore plugin, Player player, String rawBlock) {
        VeinMinerListener veinMiner = plugin.getVeinMinerListener();
        if (veinMiner == null) {
            player.sendMessage(MessageUtil.error("Vein miner is not ready yet."));
            return 0;
        }

        Material material = resolveVeinMinerBlock(player, rawBlock);
        if (!veinMiner.isValidCustomBlock(material)) {
            player.sendMessage(MessageUtil.error("Use a real block name, hold a block, or look at a block within 6 blocks."));
            return 0;
        }

        boolean changed = veinMiner.addCustomBlock(player, material);
        player.sendMessage(changed
            ? MessageUtil.success("Added <white>" + prettyMaterial(material) + "</white> to your veinminer blocks.")
            : MessageUtil.info("<white>" + prettyMaterial(material) + "</white> is already in your veinminer blocks."));
        player.sendMessage(MessageUtil.info("Use <white>/veinminer removeblock " + material.name().toLowerCase(Locale.ROOT) + "</white> when you do not want it veinmined."));
        return Command.SINGLE_SUCCESS;
    }

    private static int removeVeinMinerBlock(SMPCore plugin, Player player, String rawBlock) {
        VeinMinerListener veinMiner = plugin.getVeinMinerListener();
        if (veinMiner == null) {
            player.sendMessage(MessageUtil.error("Vein miner is not ready yet."));
            return 0;
        }

        Material material = resolveVeinMinerBlock(player, rawBlock);
        if (!veinMiner.isValidCustomBlock(material)) {
            player.sendMessage(MessageUtil.error("Use a real block name, hold a block, or look at a block within 6 blocks."));
            return 0;
        }

        boolean changed = veinMiner.removeCustomBlock(player, material);
        player.sendMessage(changed
            ? MessageUtil.success("Removed <white>" + prettyMaterial(material) + "</white> from your veinminer blocks.")
            : MessageUtil.info("<white>" + prettyMaterial(material) + "</white> was not in your veinminer blocks."));
        return Command.SINGLE_SUCCESS;
    }

    private static int showVeinMinerBlocks(SMPCore plugin, Player player) {
        VeinMinerListener veinMiner = plugin.getVeinMinerListener();
        if (veinMiner == null) {
            player.sendMessage(MessageUtil.error("Vein miner is not ready yet."));
            return 0;
        }

        List<Material> blocks = veinMiner.customBlocks(player);
        if (blocks.isEmpty()) {
            player.sendMessage(MessageUtil.info("You have no custom veinminer blocks. Add one with <white>/veinminer addblock</white>."));
            return Command.SINGLE_SUCCESS;
        }
        String joined = String.join(", ", blocks.stream().map(PlayerCommands::prettyMaterial).toList());
        player.sendMessage(MessageUtil.info("Custom veinminer blocks: <white>" + joined + "</white>."));
        return Command.SINGLE_SUCCESS;
    }

    private static Material resolveVeinMinerBlock(Player player, String rawBlock) {
        if (rawBlock == null || rawBlock.isBlank() || rawBlock.equalsIgnoreCase("target") || rawBlock.equalsIgnoreCase("looking")) {
            Block target = player.getTargetBlockExact(6);
            if (target != null && !target.getType().isAir()) {
                return target.getType();
            }
            return heldBlockMaterial(player);
        }
        if (rawBlock.equalsIgnoreCase("held") || rawBlock.equalsIgnoreCase("hand")) {
            return heldBlockMaterial(player);
        }
        String normalized = rawBlock.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        int namespaceIndex = normalized.indexOf(':');
        if (namespaceIndex >= 0 && namespaceIndex + 1 < normalized.length()) {
            normalized = normalized.substring(namespaceIndex + 1);
        }
        try {
            return Material.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Material heldBlockMaterial(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand != null && mainHand.getType().isBlock() && !mainHand.getType().isAir()) {
            return mainHand.getType();
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType().isBlock() && !offhand.getType().isAir()) {
            return offhand.getType();
        }
        return null;
    }

    private static CompletableFuture<Suggestions> suggestBlockMaterials(
        com.mojang.brigadier.context.CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack> ctx,
        SuggestionsBuilder builder
    ) {
        builder.suggest("target");
        builder.suggest("held");
        String remaining = builder.getRemainingLowerCase();
        int suggested = 0;
        for (Material material : Material.values()) {
            if (!material.isBlock() || material.isAir()) {
                continue;
            }
            String name = material.name().toLowerCase(Locale.ROOT);
            if (!remaining.isBlank() && !name.startsWith(remaining)) {
                continue;
            }
            builder.suggest(name);
            if (++suggested >= 80) {
                break;
            }
        }
        return builder.buildFuture();
    }

    private static String prettyMaterial(Material material) {
        if (material == null) {
            return "Unknown";
        }
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        List<String> words = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) continue;
            words.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1));
        }
        return String.join(" ", words);
    }
}
