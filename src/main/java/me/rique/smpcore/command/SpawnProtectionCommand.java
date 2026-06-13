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
                    .executes(ctx -> setEnabled(plugin, ctx.getSource().getSender(), false)))
                .build(),
            "Manage spawn grief protection",
            List.of("sprotect")
        );
    }

    private static int sendStatus(SMPCore plugin, CommandSender sender) {
        ConfigManager config = plugin.getConfigManager();
        World world = Bukkit.getWorld(config.spawnWorld);
        String center = "world not loaded";
        if (world != null) {
            Location spawn = world.getSpawnLocation();
            center = world.getName()
                + " "
                + spawn.getBlockX()
                + ", "
                + spawn.getBlockY()
                + ", "
                + spawn.getBlockZ();
        }

        sender.sendMessage(MessageUtil.info(
            "Spawn protection is <white>"
                + (config.spawnProtectionEnabled ? "enabled" : "disabled")
                + "</white>. Radius: <white>"
                + config.spawnProtectionRadius
                + "</white>. Center: <white>"
                + center
                + "</white>."
        ));
        sender.sendMessage(MessageUtil.info(
            "Allowed builders: <white>"
                + (config.spawnProtectionAllowedBuilders.isEmpty()
                    ? "none"
                    : String.join(", ", config.spawnProtectionAllowedBuilders))
                + "</white>."
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
            "Spawn protection radius set to <white>"
                + plugin.getConfigManager().spawnProtectionRadius
                + "</white> blocks."
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static int setEnabled(SMPCore plugin, CommandSender sender, boolean enabled) {
        plugin.getConfigManager().setSpawnProtectionEnabled(enabled);
        sender.sendMessage(MessageUtil.success(
            "Spawn protection is now <white>" + (enabled ? "enabled" : "disabled") + "</white>."
        ));
        return Command.SINGLE_SUCCESS;
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

    private static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String token = raw.trim().toLowerCase(Locale.ROOT);
        return token.isBlank() ? null : token;
    }
}
