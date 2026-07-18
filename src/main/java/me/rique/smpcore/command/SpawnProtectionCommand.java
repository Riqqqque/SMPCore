package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.config.ConfigManager;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
public final class SpawnProtectionCommand {

    private SpawnProtectionCommand() {}

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("spawnprotect")
                .requires(src -> src.getSender().hasPermission("smpcore.spawnprotect.admin"))
                .executes(ctx -> sendStatus(plugin, ctx.getSource().getSender()))
                .then(Commands.literal("status")
                    .executes(ctx -> sendStatus(plugin, ctx.getSource().getSender())))
                .then(Commands.literal("list")
                    .executes(ctx -> sendList(plugin, ctx.getSource().getSender())))
                .then(Commands.literal("flags")
                    .executes(ctx -> sendFlags(plugin, ctx.getSource().getSender())))
                .then(Commands.literal("stick")
                    .executes(ctx -> giveAdminSpawnStick(plugin, ctx.getSource().getSender())))
                .then(Commands.literal("public")
                    .executes(ctx -> sendPublicInteractions(plugin, ctx.getSource().getSender()))
                    .then(Commands.literal("list")
                        .executes(ctx -> sendPublicInteractions(plugin, ctx.getSource().getSender())))
                    .then(Commands.literal("clear")
                        .executes(ctx -> confirmClearPublicInteractions(ctx.getSource().getSender()))
                        .then(Commands.literal("confirm")
                            .executes(ctx -> clearPublicInteractions(plugin, ctx.getSource().getSender())))))
                .then(Commands.literal("check")
                    .executes(ctx -> checkLocation(plugin, ctx.getSource().getSender())))
                .then(Commands.literal("see")
                    .executes(ctx -> showOutline(plugin, ctx.getSource().getSender())))
                .then(Commands.literal("outline")
                    .executes(ctx -> showOutline(plugin, ctx.getSource().getSender())))
                .then(Commands.literal("clean")
                    .executes(ctx -> cleanSpawnMobs(plugin, ctx.getSource().getSender())))
                .then(Commands.literal("purge")
                    .executes(ctx -> cleanSpawnMobs(plugin, ctx.getSource().getSender())))
                .then(Commands.literal("debug")
                    .executes(ctx -> sendDebugStatus(plugin, ctx.getSource().getSender()))
                    .then(Commands.argument("state", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestToggleStates(builder))
                        .executes(ctx -> setDebugMobSpawns(
                            plugin,
                            ctx.getSource().getSender(),
                            StringArgumentType.getString(ctx, "state")
                        ))))
                .then(Commands.literal("flag")
                    .then(Commands.argument("flag", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestFlags(builder))
                        .then(Commands.argument("state", StringArgumentType.word())
                            .suggests((ctx, builder) -> suggestToggleStates(builder))
                            .executes(ctx -> setFlag(
                                plugin,
                                ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "flag"),
                                StringArgumentType.getString(ctx, "state")
                            )))))
                .then(Commands.literal("pos1")
                    .executes(ctx -> setCorner(plugin, ctx.getSource().getSender(), 1)))
                .then(Commands.literal("corner1")
                    .executes(ctx -> setCorner(plugin, ctx.getSource().getSender(), 1)))
                .then(Commands.literal("pos2")
                    .executes(ctx -> setCorner(plugin, ctx.getSource().getSender(), 2)))
                .then(Commands.literal("corner2")
                    .executes(ctx -> setCorner(plugin, ctx.getSource().getSender(), 2)))
                .then(Commands.literal("clearregion")
                    .executes(ctx -> clearRegion(plugin, ctx.getSource().getSender())))
                .then(Commands.literal("allow")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestOnlinePlayers(builder))
                        .executes(ctx -> addBuilder(
                            plugin,
                            ctx.getSource().getSender(),
                            StringArgumentType.getString(ctx, "player")
                        ))))
                .then(Commands.literal("add")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestOnlinePlayers(builder))
                        .executes(ctx -> addBuilder(
                            plugin,
                            ctx.getSource().getSender(),
                            StringArgumentType.getString(ctx, "player")
                        ))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestAllowedBuilders(plugin, builder))
                        .executes(ctx -> removeBuilder(
                            plugin,
                            ctx.getSource().getSender(),
                            StringArgumentType.getString(ctx, "player")
                        ))))
                .then(Commands.literal("deny")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestAllowedBuilders(plugin, builder))
                        .executes(ctx -> removeBuilder(
                            plugin,
                            ctx.getSource().getSender(),
                            StringArgumentType.getString(ctx, "player")
                        ))))
                .then(Commands.literal("radius")
                    .then(Commands.argument("blocks", IntegerArgumentType.integer(1, 10_000))
                        .executes(ctx -> setRadius(
                            plugin,
                            ctx.getSource().getSender(),
                            IntegerArgumentType.getInteger(ctx, "blocks")
                        ))))
                .then(Commands.literal("on")
                    .executes(ctx -> setEnabled(plugin, ctx.getSource().getSender(), true)))
                .then(Commands.literal("off")
                    .executes(ctx -> confirmDisable(plugin, ctx.getSource().getSender()))
                    .then(Commands.literal("confirm")
                        .executes(ctx -> setEnabled(plugin, ctx.getSource().getSender(), false))))
                .then(Commands.literal("disable")
                    .executes(ctx -> confirmDisable(plugin, ctx.getSource().getSender()))
                    .then(Commands.literal("confirm")
                        .executes(ctx -> setEnabled(plugin, ctx.getSource().getSender(), false))))
                .build(),
            "Manage spawn protection",
            List.of("sprotect")
        );

        commands.register(
            Commands.literal("adminspawnstick")
                .requires(src -> src.getSender().hasPermission("smpcore.spawnprotect.admin"))
                .executes(ctx -> giveAdminSpawnStick(plugin, ctx.getSource().getSender()))
                .build(),
            "Give the spawn public-use stick"
        );
    }

    private static int sendStatus(SMPCore plugin, CommandSender sender) {
        ConfigManager config = plugin.getConfigManager();
        sender.sendMessage(MessageUtil.info(
            "Spawn protection is <white>"
                + (config.spawnProtectionEnabled ? "enabled" : "disabled")
                + "</white>. "
                + regionStatus(config)
        ));
        sendFlags(plugin, sender);
        sender.sendMessage(MessageUtil.info(
            "Allowed builders: <white>"
                + (config.spawnProtectionAllowedBuilders.isEmpty()
                    ? "none"
                    : String.join(", ", config.spawnProtectionAllowedBuilders))
                + "</white>."
        ));
        sender.sendMessage(MessageUtil.info(
            "Public-use blocks: <white>" + config.spawnProtectionPublicInteractBlocks.size() + "</white>."
        ));
        sender.sendMessage(MessageUtil.info(
            "Mob debug logs: <white>" + (config.spawnProtectionDebugMobSpawns ? "on" : "off") + "</white>."
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static int sendList(SMPCore plugin, CommandSender sender) {
        List<String> builders = plugin.getConfigManager().spawnProtectionAllowedBuilders;
        sender.sendMessage(MessageUtil.info(
            "Spawn builders: <white>"
                + (builders.isEmpty() ? "none" : String.join(", ", builders))
                + "</white>."
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static int sendFlags(SMPCore plugin, CommandSender sender) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.spawnProtectionEnabled) {
            sender.sendMessage(MessageUtil.warn("Spawn protection is off. These flags are saved but not active."));
        }
        sender.sendMessage(MessageUtil.info("Spawn flags: <white>on blocks</white>, <white>off allows</white>."));
        sender.sendMessage(MessageUtil.info(
            "Blocking: <white>" + flagDescriptions(config, true) + "</white>."
        ));
        sender.sendMessage(MessageUtil.info(
            "Allowing: <white>" + flagDescriptions(config, false) + "</white>."
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static int giveAdminSpawnStick(SMPCore plugin, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Run this in game."));
            return 0;
        }
        if (plugin.getSpawnProtectionListener() == null) {
            sender.sendMessage(MessageUtil.error("Spawn protection is not ready yet."));
            return 0;
        }
        player.getInventory().addItem(plugin.getSpawnProtectionListener().createAdminSpawnStick())
            .values()
            .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.sendMessage(MessageUtil.success("Admin Spawn Stick added. Right-click the same block twice to toggle public use."));
        return Command.SINGLE_SUCCESS;
    }

    private static int sendPublicInteractions(SMPCore plugin, CommandSender sender) {
        List<String> blocks = plugin.getConfigManager().spawnProtectionPublicInteractBlocks;
        sender.sendMessage(MessageUtil.info("Public-use spawn blocks: <white>" + blocks.size() + "</white>."));
        for (int i = 0; i < Math.min(12, blocks.size()); i++) {
            sender.sendMessage(MessageUtil.info("<white>" + (i + 1) + ".</white> " + blocks.get(i)));
        }
        if (blocks.size() > 12) {
            sender.sendMessage(MessageUtil.info("Showing first <white>12</white>. Check config.yml for the full list."));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int confirmClearPublicInteractions(CommandSender sender) {
        sender.sendMessage(MessageUtil.warn("This removes every public-use spawn block."));
        sender.sendMessage(MessageUtil.info("Use <white>/spawnprotect public clear confirm</white> if you mean it."));
        return 0;
    }

    private static int clearPublicInteractions(SMPCore plugin, CommandSender sender) {
        int cleared = plugin.getConfigManager().clearSpawnProtectionPublicInteractions();
        sender.sendMessage(MessageUtil.success("Cleared <white>" + cleared + "</white> public-use spawn block" + (cleared == 1 ? "" : "s") + "."));
        return Command.SINGLE_SUCCESS;
    }

    private static int setCorner(SMPCore plugin, CommandSender sender, int corner) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Run this in game at the spawn corner."));
            return 0;
        }
        Location location = player.getLocation();
        plugin.getConfigManager().setSpawnProtectionCorner(corner, location);
        ConfigManager config = plugin.getConfigManager();
        sender.sendMessage(MessageUtil.success(
            "Spawn corner <white>" + corner + "</white> set to <white>"
                + location.getWorld().getName()
                + " "
                + location.getBlockX()
                + ", "
                + location.getBlockY()
                + ", "
                + location.getBlockZ()
                + "</white>."
        ));
        if (config.spawnProtectionRegionSet) {
            sender.sendMessage(MessageUtil.info(regionStatus(config)));
            cleanSpawnMobs(plugin);
        } else {
            sender.sendMessage(MessageUtil.info("Set the other corner to activate the marked spawn region."));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int clearRegion(SMPCore plugin, CommandSender sender) {
        plugin.getConfigManager().clearSpawnProtectionRegion();
        sender.sendMessage(MessageUtil.success("Cleared the marked spawn region. Radius fallback is active."));
        return Command.SINGLE_SUCCESS;
    }

    private static int checkLocation(SMPCore plugin, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Run this in game where you want to check spawn protection."));
            return 0;
        }
        Location location = player.getLocation();
        boolean protectedHere = plugin.getSpawnProtectionListener() != null
            && plugin.getSpawnProtectionListener().isProtected(location);
        boolean mobSpawnsBlockedHere = plugin.getSpawnProtectionListener() != null
            && plugin.getSpawnProtectionListener().blocksMobSpawns(location);
        boolean canEdit = plugin.getSpawnProtectionListener() != null
            && plugin.getSpawnProtectionListener().canEditSpawn(player);
        sender.sendMessage(MessageUtil.info(
            "Here: <white>"
                + location.getWorld().getName()
                + " "
                + location.getBlockX()
                + ", "
                + location.getBlockY()
                + ", "
                + location.getBlockZ()
                + "</white>."
        ));
        sender.sendMessage(MessageUtil.info(
            "Protected: <white>"
                + (protectedHere ? "yes" : "no")
                + "</white>. Mob spawns blocked here: <white>"
                + (mobSpawnsBlockedHere ? "yes" : "no")
                + "</white>. You bypass: <white>"
                + (canEdit ? "yes" : "no")
                + "</white>."
        ));
        sender.sendMessage(MessageUtil.info(regionStatus(plugin.getConfigManager())));
        return Command.SINGLE_SUCCESS;
    }

    private static int showOutline(SMPCore plugin, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Run this in game to see the spawn outline."));
            return 0;
        }
        if (plugin.getSpawnProtectionListener() == null
            || !plugin.getSpawnProtectionListener().showProtectedAreaOutline(player)) {
            sender.sendMessage(MessageUtil.error("No active spawn outline in this world."));
            return 0;
        }
        sender.sendMessage(MessageUtil.success("Spawn outline shown."));
        return Command.SINGLE_SUCCESS;
    }

    private static int cleanSpawnMobs(SMPCore plugin, CommandSender sender) {
        int removed = cleanSpawnMobs(plugin);
        sender.sendMessage(MessageUtil.success(
            "Removed <white>" + removed + "</white> stray mob" + (removed == 1 ? "" : "s") + " from protected spawn."
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static int sendDebugStatus(SMPCore plugin, CommandSender sender) {
        sender.sendMessage(MessageUtil.info(
            "Spawn mob debug logs are <white>"
                + (plugin.getConfigManager().spawnProtectionDebugMobSpawns ? "on" : "off")
                + "</white>."
        ));
        sender.sendMessage(MessageUtil.info("Use <white>/spawnprotect debug on</white> for temporary log details."));
        return Command.SINGLE_SUCCESS;
    }

    private static int setDebugMobSpawns(SMPCore plugin, CommandSender sender, String rawState) {
        Boolean enabled = parseToggle(rawState);
        if (enabled == null) {
            sender.sendMessage(MessageUtil.error("Use on or off."));
            return 0;
        }
        boolean changed = plugin.getConfigManager().setSpawnProtectionDebugMobSpawns(enabled);
        String message = "Spawn mob debug logs are <white>"
            + (enabled ? "on" : "off")
            + "</white>."
            + (enabled ? " Check console or latest.log." : "");
        sender.sendMessage(changed ? MessageUtil.success(message) : MessageUtil.info(message));
        return Command.SINGLE_SUCCESS;
    }

    private static int setFlag(SMPCore plugin, CommandSender sender, String rawFlag, String rawState) {
        Boolean enabled = parseToggle(rawState);
        if (enabled == null) {
            sender.sendMessage(MessageUtil.error("Use on or off."));
            return 0;
        }
        ConfigManager config = plugin.getConfigManager();
        if (!config.isValidSpawnProtectionFlag(rawFlag)) {
            sender.sendMessage(MessageUtil.error(
                "Unknown flag. Options: <white>" + String.join(", ", ConfigManager.SPAWN_PROTECTION_DEFAULT_FLAGS) + "</white>."
            ));
            return 0;
        }
        boolean changed = config.setSpawnProtectionFlag(rawFlag, enabled);
        String flag = config.spawnProtectionFlagName(rawFlag);
        String message = "Spawn flag <white>"
            + flag
            + "</white> is <white>"
            + (enabled ? "on" : "off")
            + "</white>: <white>"
            + (enabled ? "blocking " : "allowing ")
            + flagDescription(flag)
            + "</white>.";
        sender.sendMessage(changed ? MessageUtil.success(message) : MessageUtil.info(message));
        if (!config.spawnProtectionEnabled) {
            sender.sendMessage(MessageUtil.warn("Saved, but spawn protection is off. Run /spawnprotect on."));
        }
        if (enabled && (flag.equals("mob-spawns") || flag.equals("mob-entry"))) {
            cleanSpawnMobs(plugin);
            if (plugin.getSpawnProtectionListener() != null) {
                plugin.getSpawnProtectionListener().scheduleProtectedSpawnMobCleanup();
            }
        }
        if (enabled && flag.equals("weather-lock") && plugin.getSpawnProtectionListener() != null) {
            plugin.getSpawnProtectionListener().enforceWeatherLock();
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int addBuilder(SMPCore plugin, CommandSender sender, String playerName) {
        String token = normalise(playerName);
        if (token == null) {
            sender.sendMessage(MessageUtil.error("Usage: /spawnprotect allow <player>"));
            return 0;
        }
        boolean added = plugin.getConfigManager().addSpawnProtectionBuilder(token);
        sender.sendMessage(added
            ? MessageUtil.success("<white>" + token + "</white> can now build at spawn.")
            : MessageUtil.info("<white>" + token + "</white> already has spawn build access."));
        return Command.SINGLE_SUCCESS;
    }

    private static int removeBuilder(SMPCore plugin, CommandSender sender, String playerName) {
        String token = normalise(playerName);
        if (token == null) {
            sender.sendMessage(MessageUtil.error("Usage: /spawnprotect remove <player>"));
            return 0;
        }
        boolean removed = plugin.getConfigManager().removeSpawnProtectionBuilder(token);
        sender.sendMessage(removed
            ? MessageUtil.success("<white>" + token + "</white> can no longer build at spawn.")
            : MessageUtil.info("<white>" + token + "</white> was not on the spawn builder list."));
        return Command.SINGLE_SUCCESS;
    }

    private static int setRadius(SMPCore plugin, CommandSender sender, int radius) {
        plugin.getConfigManager().setSpawnProtectionRadius(radius);
        sender.sendMessage(MessageUtil.success(
            "Spawn protection fallback radius set to <white>"
                + plugin.getConfigManager().spawnProtectionRadius
                + "</white> blocks."
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static int confirmDisable(SMPCore plugin, CommandSender sender) {
        if (!plugin.getConfigManager().spawnProtectionEnabled) {
            sender.sendMessage(MessageUtil.info("Spawn protection is already off."));
            return 0;
        }
        sender.sendMessage(MessageUtil.warn("This turns off every spawn protection flag."));
        sender.sendMessage(MessageUtil.info("Use <white>/spawnprotect off confirm</white> if you mean it."));
        return 0;
    }

    private static int setEnabled(SMPCore plugin, CommandSender sender, boolean enabled) {
        boolean changed = plugin.getConfigManager().setSpawnProtectionEnabled(enabled, sender.getName());
        String message = "Spawn protection is <white>" + (enabled ? "on" : "off") + "</white>.";
        sender.sendMessage(changed ? MessageUtil.success(message) : MessageUtil.info(message));
        if (!enabled) {
            sender.sendMessage(MessageUtil.warn("All spawn flags are inactive until /spawnprotect on."));
        }
        if (enabled) {
            cleanSpawnMobs(plugin);
            if (plugin.getSpawnProtectionListener() != null) {
                plugin.getSpawnProtectionListener().scheduleProtectedSpawnMobCleanup();
                plugin.getSpawnProtectionListener().enforceWeatherLock();
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int cleanSpawnMobs(SMPCore plugin) {
        return plugin.getSpawnProtectionListener() == null
            ? 0
            : plugin.getSpawnProtectionListener().cleanupProtectedSpawnMobs();
    }

    private static String regionStatus(ConfigManager config) {
        if (config.spawnProtectionRegionSet) {
            return "Selected region: <white>"
                + config.spawnProtectionRegionWorld
                + " "
                + config.spawnProtectionMinX
                + ", "
                + config.spawnProtectionMinZ
                + "</white> to <white>"
                + config.spawnProtectionMaxX
                + ", "
                + config.spawnProtectionMaxZ
                + "</white>. Height: <white>"
                + yStatus(config)
                + "</white>.";
        }

        World world = Bukkit.getWorld(config.spawnWorld);
        String center = "world not loaded";
        if (world != null) {
            Location spawn = world.getSpawnLocation();
            center = world.getName()
                + " "
                + spawn.getBlockX()
                + ", "
                + spawn.getBlockZ();
        }
        return "No selected region. Radius fallback: <white>"
            + config.spawnProtectionRadius
            + "</white> blocks around <white>"
            + center
            + "</white>.";
    }

    private static String yStatus(ConfigManager config) {
        if (config.spawnProtectionRegionFullHeight) {
            return "all Y";
        }
        return config.spawnProtectionMinY + "-" + config.spawnProtectionMaxY;
    }

    private static String flagDescriptions(ConfigManager config, boolean enabled) {
        StringBuilder builder = new StringBuilder();
        for (String flag : ConfigManager.SPAWN_PROTECTION_DEFAULT_FLAGS) {
            if (config.isSpawnProtectionFlagEnabled(flag) != enabled) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(flag).append(" (").append(flagDescription(flag)).append(")");
        }
        return builder.isEmpty() ? "none" : builder.toString();
    }

    private static String flagDescription(String flag) {
        return switch (flag) {
            case "build" -> "block edits";
            case "interact" -> "block interaction";
            case "pvp" -> "PvP";
            case "hunger-drain" -> "hunger drain";
            case "mob-grief" -> "mob block changes";
            case "mob-spawns" -> "mob spawning";
            case "mob-entry" -> "mobs entering";
            case "explosions" -> "explosions";
            case "fire" -> "fire spread";
            case "liquids" -> "liquid flow";
            case "redstone" -> "redstone changes";
            case "environment" -> "environment changes";
            case "natural-decay" -> "leaf decay and natural growth";
            case "crop-trample" -> "crop trampling";
            case "bone-meal" -> "public bone meal use";
            case "weather-lock" -> "rain and thunder";
            case "entity-edit" -> "item frame and armor stand edits";
            default -> flag;
        };
    }

    private static CompletableFuture<Suggestions> suggestOnlinePlayers(SuggestionsBuilder builder) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            builder.suggest(player.getName());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestAllowedBuilders(SMPCore plugin, SuggestionsBuilder builder) {
        for (String builderName : plugin.getConfigManager().spawnProtectionAllowedBuilders) {
            builder.suggest(builderName);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestFlags(SuggestionsBuilder builder) {
        for (String flag : ConfigManager.SPAWN_PROTECTION_DEFAULT_FLAGS) {
            builder.suggest(flag);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestToggleStates(SuggestionsBuilder builder) {
        builder.suggest("on");
        builder.suggest("off");
        return builder.buildFuture();
    }

    private static Boolean parseToggle(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "enable", "enabled" -> true;
            case "off", "false", "no", "disable", "disabled" -> false;
            default -> null;
        };
    }

    private static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String token = raw.trim().toLowerCase(Locale.ROOT);
        return token.isBlank() ? null : token;
    }
}
