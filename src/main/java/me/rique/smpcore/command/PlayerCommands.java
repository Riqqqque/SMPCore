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
import me.rique.smpcore.power.SuperpowerType;
import me.rique.smpcore.util.CommandSuggestionUtil;
import me.rique.smpcore.util.LocationUtil;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Survival-accessible player commands: /top, /suicide, /back, /spawn.
 */
@SuppressWarnings("UnstableApiUsage")
public final class PlayerCommands {

    private static final Map<UUID, Long> SPAWN_COOLDOWNS = new ConcurrentHashMap<>();
    private static final String POWER_COMMAND_PERMISSION = "smpcore.superpower.command";
    private static final String POWER_COMMAND_BYPASS_PERMISSION = "smpcore.superpower.command.all";

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
        registerNightshadeVision(commands, plugin);
        registerXray(commands, plugin);
        registerVoidstep(commands, plugin);
        registerVoidVision(commands, plugin);
        registerSmokeBomb(commands, plugin);
        registerStormcaller(commands, plugin);
        registerDomainExpansion(commands, plugin);
        registerInfinity(commands, plugin);
        registerUnstoppableForce(commands, plugin);
        registerDeadeyeArrows(commands, plugin);
        registerArcanistBook(commands, plugin);
        registerOathSummon(commands, plugin);
        registerBloodmenderCommands(commands, plugin);
        registerTravel(commands, plugin);
        registerMonarchSummon(commands, plugin);
        registerBackpack(commands, plugin);
        registerBack(commands, plugin);
        registerSpawn(commands, plugin);
    }

    private static void registerBackpack(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("backpack")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.backpack.use"))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    player.sendMessage(MessageUtil.info("Hold a backpack, then use <white>/backpack label \\<text></white> or <white>/backpack clear</white>."));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("label")
                    .then(Commands.argument("label", StringArgumentType.greedyString())
                        .executes(ctx -> plugin.getBackpackListener().setBackpackSuffix(
                            (Player) ctx.getSource().getSender(),
                            StringArgumentType.getString(ctx, "label")
                        ) ? Command.SINGLE_SUCCESS : 0)))
                .then(Commands.literal("clear")
                    .executes(ctx -> plugin.getBackpackListener().clearBackpackSuffix(
                        (Player) ctx.getSource().getSender()
                    ) ? Command.SINGLE_SUCCESS : 0))
                .build(),
            "Label the backpack in your main hand",
            List.of("backpacklabel")
        );
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
            "Privately outline teammates through walls",
            List.of("allyglow", "teammateglow")
        );
    }

    // /top - teleport above the highest block at current x,z
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

    // /suicide - kill the player
    private static void registerSuicide(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("suicide")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.suicide"))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    if (plugin.getSpawnProtectionListener() != null
                        && plugin.getSpawnProtectionListener().blocksProtectedSpawnDeath(player)) {
                        player.sendMessage(MessageUtil.warn("You cannot die in protected spawn."));
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
                .requires(src -> canUsePowerCommand(plugin, src.getSender(), SuperpowerType.NIGHTSHADE))
                .executes(ctx -> toggleShadow(plugin, (Player) ctx.getSource().getSender()))
                .then(Commands.literal("toggle")
                    .executes(ctx -> toggleShadow(plugin, (Player) ctx.getSource().getSender())))
                .build(),
            "Toggle the Shadow class ability"
        );
    }

    private static int toggleShadow(SMPCore plugin, Player player) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
            return 0;
        }
        return powers.handleShadowCommand(player) ? Command.SINGLE_SUCCESS : 0;
    }

    private static void registerNightshadeVision(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("nightshadevision")
                .requires(src -> canUsePowerCommand(plugin, src.getSender(), SuperpowerType.NIGHTSHADE))
                .executes(ctx -> setNightshadeVision(plugin, (Player) ctx.getSource().getSender(), null))
                .then(Commands.literal("toggle")
                    .executes(ctx -> setNightshadeVision(plugin, (Player) ctx.getSource().getSender(), null)))
                .then(Commands.literal("on")
                    .executes(ctx -> setNightshadeVision(plugin, (Player) ctx.getSource().getSender(), true)))
                .then(Commands.literal("off")
                    .executes(ctx -> setNightshadeVision(plugin, (Player) ctx.getSource().getSender(), false)))
                .then(Commands.literal("status")
                    .executes(ctx -> showNightshadeVisionStatus(plugin, (Player) ctx.getSource().getSender())))
                .build(),
            "Toggle Nightshade night vision",
            List.of("nvision", "nightshadenv")
        );
    }

    private static int setNightshadeVision(SMPCore plugin, Player player, Boolean enabled) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
            return 0;
        }
        return powers.handleNightshadeVisionCommand(player, enabled) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int showNightshadeVisionStatus(SMPCore plugin, Player player) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
            return 0;
        }
        return powers.handleNightshadeVisionStatusCommand(player) ? Command.SINGLE_SUCCESS : 0;
    }

    private static void registerXray(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("xray")
                .requires(src -> canUsePowerCommand(plugin, src.getSender(), SuperpowerType.ORACLE_EYE))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    SuperpowerManager powers = plugin.getSuperpowerManager();
                    if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
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
                .requires(src -> canUsePowerCommand(plugin, src.getSender(), SuperpowerType.VOIDWALKER))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    SuperpowerManager powers = plugin.getSuperpowerManager();
                    if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
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
                .requires(src -> canUsePowerCommand(plugin, src.getSender(), SuperpowerType.VOIDWALKER))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    SuperpowerManager powers = plugin.getSuperpowerManager();
                    if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
                        return 0;
                    }
                    return powers.handleVoidwalkerNightVisionCommand(player) ? Command.SINGLE_SUCCESS : 0;
                })
                .then(Commands.literal("toggle")
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        SuperpowerManager powers = plugin.getSuperpowerManager();
                        if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
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
                .requires(src -> canUsePowerCommand(plugin, src.getSender(), SuperpowerType.STORMCALLER))
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

    private static void registerSmokeBomb(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("smokebomb")
                .requires(src -> canUsePowerCommand(plugin, src.getSender(), SuperpowerType.VEIL_ASSASSIN))
                .executes(ctx -> useSmokeBomb(plugin, (Player) ctx.getSource().getSender()))
                .build(),
            "Drop a Veil Assassin smoke bomb",
            List.of("sb")
        );
    }

    private static void registerTravel(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("travel")
                .requires(src -> canUsePowerCommand(plugin, src.getSender(), SuperpowerType.WAYFARER))
                .then(Commands.literal("close")
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        SuperpowerManager powers = plugin.getSuperpowerManager();
                        if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
                            return 0;
                        }
                        return powers.handleTravelCloseCommand(player) ? Command.SINGLE_SUCCESS : 0;
                    }))
                .then(Commands.argument("x", IntegerArgumentType.integer())
                    .then(Commands.argument("y", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                            .then(Commands.argument("dimension", StringArgumentType.word())
                                .suggests((ctx, builder) -> CommandSuggestionUtil.suggestLoadedWorlds(builder))
                                .executes(ctx -> {
                                    Player player = (Player) ctx.getSource().getSender();
                                    SuperpowerManager powers = plugin.getSuperpowerManager();
                                    if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
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

    private static void registerDomainExpansion(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("domainexpansion")
                .requires(src -> canUsePowerCommand(plugin, src.getSender(), SuperpowerType.HONORED_ONE))
                .executes(ctx -> runDomainExpansion(plugin, (Player) ctx.getSource().getSender()))
                .build(),
            "Open The Honored One domain",
            List.of("domain", "domainexp")
        );
    }

    private static int runDomainExpansion(SMPCore plugin, Player player) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
            return 0;
        }
        return powers.handleDomainExpansionCommand(player) ? Command.SINGLE_SUCCESS : 0;
    }

    private static void registerInfinity(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("infinity")
                .requires(src -> canUsePowerCommand(plugin, src.getSender(), SuperpowerType.HONORED_ONE))
                .executes(ctx -> setInfinity(plugin, (Player) ctx.getSource().getSender(), null))
                .then(Commands.literal("toggle")
                    .executes(ctx -> setInfinity(plugin, (Player) ctx.getSource().getSender(), null)))
                .then(Commands.literal("on")
                    .executes(ctx -> setInfinity(plugin, (Player) ctx.getSource().getSender(), true)))
                .then(Commands.literal("off")
                    .executes(ctx -> setInfinity(plugin, (Player) ctx.getSource().getSender(), false)))
                .then(Commands.literal("status")
                    .executes(ctx -> showInfinityStatus(plugin, (Player) ctx.getSource().getSender())))
                .build(),
            "Toggle The Honored One Infinity",
            List.of("infinite", "honoredinfinity")
        );
    }

    private static int setInfinity(SMPCore plugin, Player player, Boolean enabled) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
            return 0;
        }
        return powers.handleInfinityCommand(player, enabled) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int showInfinityStatus(SMPCore plugin, Player player) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
            return 0;
        }
        return powers.handleInfinityStatusCommand(player) ? Command.SINGLE_SUCCESS : 0;
    }

    private static void registerUnstoppableForce(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("unstoppableforce")
                .requires(src -> canUsePowerCommand(plugin, src.getSender(), SuperpowerType.JUGGERNAUT))
                .executes(ctx -> runUnstoppableForce(plugin, (Player) ctx.getSource().getSender()))
                .build(),
            "Charge through breakable walls as Juggernaut",
            List.of("uf")
        );
    }

    private static int runUnstoppableForce(SMPCore plugin, Player player) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
            return 0;
        }
        return powers.handleUnstoppableForceCommand(player) ? Command.SINGLE_SUCCESS : 0;
    }

    private static void registerDeadeyeArrows(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("deadeyearrows")
                .requires(src -> canUsePowerCommand(plugin, src.getSender(), SuperpowerType.DEADEYE))
                .executes(ctx -> setDeadeyeArrows(plugin, (Player) ctx.getSource().getSender(), null))
                .then(Commands.literal("toggle")
                    .executes(ctx -> setDeadeyeArrows(plugin, (Player) ctx.getSource().getSender(), null)))
                .then(Commands.literal("on")
                    .executes(ctx -> setDeadeyeArrows(plugin, (Player) ctx.getSource().getSender(), true)))
                .then(Commands.literal("off")
                    .executes(ctx -> setDeadeyeArrows(plugin, (Player) ctx.getSource().getSender(), false)))
                .then(Commands.literal("status")
                    .executes(ctx -> showDeadeyeArrowsStatus(plugin, (Player) ctx.getSource().getSender())))
                .build(),
            "Toggle Deadeye arrow preservation",
            List.of("darrows", "deadeyeinfinity")
        );
    }

    private static int setDeadeyeArrows(SMPCore plugin, Player player, Boolean enabled) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
            return 0;
        }
        return powers.handleDeadeyeArrowInfinityCommand(player, enabled) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int showDeadeyeArrowsStatus(SMPCore plugin, Player player) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
            return 0;
        }
        return powers.handleDeadeyeArrowInfinityStatusCommand(player) ? Command.SINGLE_SUCCESS : 0;
    }

    private static void registerArcanistBook(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("arcanebook")
                .requires(src -> canUsePowerCommand(plugin, src.getSender(), SuperpowerType.ARCANIST))
                .executes(ctx -> runArcanistBook(plugin, (Player) ctx.getSource().getSender()))
                .build(),
            "Upgrade a held enchanted book to max level as Arcanist",
            List.of("maxbook", "bookmax")
        );
    }

    private static int runArcanistBook(SMPCore plugin, Player player) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
            return 0;
        }
        return powers.handleArcanistBookUpgradeCommand(player) ? Command.SINGLE_SUCCESS : 0;
    }

    private static void registerOathSummon(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("oathsummon")
                .requires(src -> canUseOathSummonCommand(plugin, src.getSender()))
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests(PlayerCommands::suggestOnlinePlayers)
                    .executes(ctx -> runOathSummon(
                        plugin,
                        (Player) ctx.getSource().getSender(),
                        StringArgumentType.getString(ctx, "player")
                    )))
                .build(),
            "Summon an Oathbound teammate to you"
        );
    }

    private static int runOathSummon(SMPCore plugin, Player player, String targetName) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
            return 0;
        }
        return powers.handleOathSummonCommand(player, targetName) ? Command.SINGLE_SUCCESS : 0;
    }

    private static void registerBloodmenderCommands(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("bloodsacrifice")
                .requires(src -> canUsePowerCommand(plugin, src.getSender(), SuperpowerType.BLOODMENDER))
                .executes(ctx -> runBloodSacrifice(plugin, (Player) ctx.getSource().getSender()))
                .build(),
            "Sacrifice health to heal nearby teammates as Bloodmender",
            List.of("bloodheal")
        );
        commands.register(
            Commands.literal("curse")
                .requires(src -> canUsePowerCommand(plugin, src.getSender(), SuperpowerType.BLOODMENDER))
                .executes(ctx -> runBloodCurse(plugin, (Player) ctx.getSource().getSender()))
                .build(),
            "Curse a nearby enemy armor piece as Bloodmender"
        );
    }

    private static int runBloodSacrifice(SMPCore plugin, Player player) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
            return 0;
        }
        return powers.handleBloodSacrificeCommand(player) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int runBloodCurse(SMPCore plugin, Player player) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
            return 0;
        }
        return powers.handleBloodCurseCommand(player) ? Command.SINGLE_SUCCESS : 0;
    }

    private static void registerMonarchSummon(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("msummon")
                .requires(src -> canUsePowerCommand(plugin, src.getSender(), SuperpowerType.MONARCH))
                .executes(ctx -> runMonarchSummon(plugin, (Player) ctx.getSource().getSender(), 1))
                .then(Commands.literal("despawn")
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        SuperpowerManager powers = plugin.getSuperpowerManager();
                        if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
                            return 0;
                        }
                        return powers.handleMonarchDespawnCommand(player) ? Command.SINGLE_SUCCESS : 0;
                    }))
                .then(Commands.literal("unsummon")
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        SuperpowerManager powers = plugin.getSuperpowerManager();
                        if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
                            return 0;
                        }
                        return powers.handleMonarchDespawnCommand(player) ? Command.SINGLE_SUCCESS : 0;
                    }))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 15))
                    .suggests((ctx, builder) -> CommandSuggestionUtil.suggestNumbers(builder, 1, 2, 3, 5, 10, 15))
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        int amount = IntegerArgumentType.getInteger(ctx, "amount");
                        return runMonarchSummon(plugin, player, amount);
                    }))
                .build(),
            "Summon stored Shadow Monarch mobs"
        );
    }

    private static int runMonarchSummon(SMPCore plugin, Player player, int amount) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
            return 0;
        }
        return powers.handleMonarchSummonCommand(player, amount) ? Command.SINGLE_SUCCESS : 0;
    }

    // /back - return to last death or pre-teleport location
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
                    Location safe = LocationUtil.findNearestSafeStandingLocation(back, 5, 8);
                    if (safe == null) {
                        player.sendMessage(MessageUtil.error("That saved location is not safe anymore."));
                        return 0;
                    }
                    plugin.getPlayerManager().saveBackLocation(player);
                    player.teleportAsync(safe).thenAccept(ok -> {
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

    // /spawn - teleport to exact server spawn
    private static void registerSpawn(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("spawn")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.spawn"))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    if (isInPlayerCombat(plugin, player)) {
                        return 0;
                    }
                    if (!canUseSpawn(plugin, player)) {
                        return 0;
                    }
                    World world = Bukkit.getWorld(plugin.getConfigManager().spawnWorld);
                    if (world == null) {
                        player.sendMessage(MessageUtil.error("Spawn world is not loaded."));
                        return 0;
                    }
                    Location spawn = plugin.getExactSpawnListener() == null
                        ? plugin.getConfigManager().exactSpawnLocation()
                        : plugin.getExactSpawnListener().exactSpawnLocation();
                    if (spawn == null) {
                        player.sendMessage(MessageUtil.error("Spawn is not ready right now."));
                        return 0;
                    }
                    plugin.getPlayerManager().saveBackLocation(player);
                    startSpawnCooldown(plugin, player);
                    player.teleportAsync(spawn).thenAccept(ok -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) return;
                            if (isInPlayerCombat(plugin, player)) {
                                clearSpawnCooldown(player);
                                return;
                            }
                            if (ok) player.sendMessage(MessageUtil.success("Teleported to spawn."));
                            else {
                                clearSpawnCooldown(player);
                                player.sendMessage(MessageUtil.error("Teleport failed."));
                            }
                        });
                    });
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Teleport to the server spawn"
        );
    }

    private static boolean isInPlayerCombat(SMPCore plugin, Player player) {
        if (plugin.getCombatLogListener() == null || !plugin.getCombatLogListener().isInPlayerCombat(player)) {
            return false;
        }
        player.sendMessage(MessageUtil.warn("You cannot teleport while in combat."));
        return true;
    }

    private static boolean canUseSpawn(SMPCore plugin, Player player) {
        if (player.hasPermission("smpcore.spawn.cooldown.bypass")) {
            return true;
        }
        int cooldownSeconds = plugin.getConfigManager().spawnCooldownSeconds;
        if (cooldownSeconds <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        long expiresAt = SPAWN_COOLDOWNS.getOrDefault(player.getUniqueId(), 0L);
        if (expiresAt <= now) {
            return true;
        }
        long secondsLeft = Math.max(1L, (expiresAt - now + 999L) / 1000L);
        player.sendMessage(MessageUtil.warn("Wait <white>" + secondsLeft + "s</white> before using /spawn again."));
        return false;
    }

    private static void startSpawnCooldown(SMPCore plugin, Player player) {
        int cooldownSeconds = plugin.getConfigManager().spawnCooldownSeconds;
        if (cooldownSeconds <= 0 || player.hasPermission("smpcore.spawn.cooldown.bypass")) {
            return;
        }
        UUID playerId = player.getUniqueId();
        long expiresAt = System.currentTimeMillis() + (cooldownSeconds * 1000L);
        SPAWN_COOLDOWNS.put(playerId, expiresAt);
        Bukkit.getScheduler().runTaskLater(
            plugin,
            () -> SPAWN_COOLDOWNS.remove(playerId, expiresAt),
            cooldownSeconds * 20L
        );
    }

    private static void clearSpawnCooldown(Player player) {
        SPAWN_COOLDOWNS.remove(player.getUniqueId());
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
        if (player.hasPermission("smpcore.changelog")) {
            lines.add("<gray><white>/changelog</white> - See the latest player-facing update notes</gray>");
        }
        if (player.hasPermission("smpcore.spinbet")) {
            lines.add("<gray><white>/spinbet</white> - Bet held items with another player</gray>");
        }
        if (player.hasPermission("smpcore.blackjack")) {
            lines.add("<gray><white>/blackjack claim</white> - Recover an interrupted Dealer payout</gray>");
        }
        if (player.hasPermission("smpcore.roulette")) {
            lines.add("<gray><white>/roulette claim</white> - Recover an interrupted roulette payout</gray>");
        }
        if (player.hasPermission("smpcore.leaderboard")) {
            lines.add("<gray><white>/leaderboards</white> (<white>/lb</white>) - View kills, deaths, boss damage, and fight reports</gray>");
        }
        if (player.hasPermission("smpcore.legendary.recipe")) {
            lines.add("<gray><white>/lrecipe</white> (<white>/reliquary</white>) - Open the Reliquary</gray>");
            lines.add("<gray><white>/mythics</white> - View Mythic Nexus fusion pairings</gray>");
        }
        if (player.hasPermission("smpcore.bossbrews")) {
            lines.add("<gray><white>/bossbrews</white> - View boss-material potion recipes</gray>");
        }
        if (player.hasPermission("smpcore.bossrituals")) {
            lines.add("<gray><white>/bossrituals</white> - View custom boss summon rituals</gray>");
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
            lines.add("<gray><white>/startsmp</white> - Open the spawn barrier and start the grace timer</gray>");
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
            lines.add("<gray><white>/teamglow</white> - Privately outline teammates through walls</gray>");
        }
        if (player.hasPermission("smpcore.waystone.use")) {
            lines.add("<gray>Right-click a known waystone sign to open your teleport menu</gray>");
        }
        if (player.hasPermission(POWER_COMMAND_PERMISSION) || player.hasPermission(POWER_COMMAND_BYPASS_PERMISSION)) {
            lines.add("<gray>Class commands only show when your class can use them.</gray>");
        }
        if (player.hasPermission("smpcore.backpack.use")) {
            lines.add("<gray>Right-click a <white>Backpack</white> to open portable storage</gray>");
            lines.add("<gray><white>/backpack label \\<text></white> - Add an organization label</gray>");
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
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
            return 0;
        }
        return powers.handleStormcallerLightningCommand(player, enabled) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int showStormcallerLightningStatus(SMPCore plugin, Player player) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
            return 0;
        }
        return powers.handleStormcallerLightningStatusCommand(player) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int useSmokeBomb(SMPCore plugin, Player player) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            player.sendMessage(MessageUtil.error("Class system is not ready yet."));
            return 0;
        }
        return powers.handleSmokeBombCommand(player) ? Command.SINGLE_SUCCESS : 0;
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
        player.sendMessage(MessageUtil.info("Teammate outlines are <white>" + (enabled ? "enabled" : "disabled") + "</white>."));
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

    private static CompletableFuture<Suggestions> suggestOnlinePlayers(
        com.mojang.brigadier.context.CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack> ctx,
        SuggestionsBuilder builder
    ) {
        String remaining = builder.getRemainingLowerCase();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String name = player.getName();
            if (remaining.isBlank() || name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    private static boolean canUsePowerCommand(SMPCore plugin, CommandSender sender, SuperpowerType... types) {
        if (!(sender instanceof Player player) || !hasPowerCommandPermission(player)) {
            return false;
        }
        if (player.hasPermission(POWER_COMMAND_BYPASS_PERMISSION)) {
            return true;
        }
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            return false;
        }
        for (SuperpowerType type : types) {
            if (powers.hasPower(player, type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canUseOathSummonCommand(SMPCore plugin, CommandSender sender) {
        if (!(sender instanceof Player player) || !hasPowerCommandPermission(player)) {
            return false;
        }
        if (player.hasPermission(POWER_COMMAND_BYPASS_PERMISSION)) {
            return true;
        }
        SuperpowerManager powers = plugin.getSuperpowerManager();
        return powers != null && powers.hasOathSummonTarget(player);
    }

    private static boolean hasPowerCommandPermission(Player player) {
        return player.hasPermission(POWER_COMMAND_PERMISSION)
            || player.hasPermission(POWER_COMMAND_BYPASS_PERMISSION);
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
