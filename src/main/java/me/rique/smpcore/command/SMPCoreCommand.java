package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.legendary.LegendaryListener;
import me.rique.smpcore.util.MessageUtil;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
public final class SMPCoreCommand {

    private SMPCoreCommand() {}

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("smpcore")
                .requires(src -> src.getSender().hasPermission("smpcore.reload"))
                .then(Commands.literal("reload")
                    .executes(ctx -> {
                        plugin.getConfigManager().reload();
                        if (plugin.getCraftingRulesListener() != null) {
                            plugin.getCraftingRulesListener().reloadConfig();
                        }
                        plugin.restartDragonEggListener();
                        plugin.getSpawnerManager().refreshAllFromConfig();
                        if (plugin.getWorldRulesListener() != null) {
                            plugin.getWorldRulesListener().applyConfiguredWorldRules();
                        }
                        if (plugin.getVeinMinerListener() != null) {
                            plugin.getVeinMinerListener().reloadConfig();
                        }
                        if (plugin.getCustomToolListener() != null) {
                            plugin.getCustomToolListener().reloadConfig();
                        }
                        if (plugin.getMythicForgeListener() != null) {
                            plugin.getMythicForgeListener().reloadConfig();
                        }
                        if (plugin.getLegendaryAltarManager() != null) {
                            plugin.getLegendaryAltarManager().reloadConfig();
                        }
                        ctx.getSource().getSender().sendMessage(
                            MessageUtil.success("Configuration reloaded."));
                        return Command.SINGLE_SUCCESS;
                    }))
                .then(Commands.literal("altar")
                    .executes(ctx -> {
                        if (plugin.getLegendaryAltarManager() == null) {
                            ctx.getSource().getSender().sendMessage(MessageUtil.error("Legendary altar system is not ready yet."));
                            return 0;
                        }
                        sendAltarResult(ctx.getSource().getSender(), plugin.getLegendaryAltarManager().altarStatusSummary());
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.literal("status")
                        .executes(ctx -> {
                            if (plugin.getLegendaryAltarManager() == null) {
                                ctx.getSource().getSender().sendMessage(MessageUtil.error("Legendary altar system is not ready yet."));
                                return 0;
                            }
                            sendAltarResult(ctx.getSource().getSender(), plugin.getLegendaryAltarManager().altarStatusSummary());
                            return Command.SINGLE_SUCCESS;
                        }))
                    .then(Commands.literal("clear")
                        .executes(ctx -> {
                            if (plugin.getLegendaryAltarManager() == null) {
                                ctx.getSource().getSender().sendMessage(MessageUtil.error("Legendary altar system is not ready yet."));
                                return 0;
                            }
                            sendAltarResult(ctx.getSource().getSender(), plugin.getLegendaryAltarManager().clearForAdmin());
                            return Command.SINGLE_SUCCESS;
                        }))
                    .then(Commands.literal("spawn")
                        .executes(ctx -> executeAltarSpawn(plugin, ctx.getSource().getSender(), null, false))
                        .then(Commands.argument("legendary", StringArgumentType.word())
                            .suggests((ctx, builder) -> suggestLegendaryIds(plugin, builder))
                            .executes(ctx -> executeAltarSpawn(
                                plugin,
                                ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "legendary"),
                                false
                            ))))
                    .then(Commands.literal("spawnready")
                        .executes(ctx -> executeAltarSpawn(plugin, ctx.getSource().getSender(), null, true))
                        .then(Commands.argument("legendary", StringArgumentType.word())
                            .suggests((ctx, builder) -> suggestLegendaryIds(plugin, builder))
                            .executes(ctx -> executeAltarSpawn(
                                plugin,
                                ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "legendary"),
                                true
                            )))))
                .then(Commands.literal("netheritearmor")
                    .executes(ctx -> {
                        boolean blocked = plugin.getConfigManager().blockNetheriteArmorUpgrade;
                        ctx.getSource().getSender().sendMessage(MessageUtil.info(
                            "Netherite armor upgrades are currently <white>" + (blocked ? "blocked" : "allowed") + "</white>."));
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.literal("status")
                        .executes(ctx -> {
                            boolean blocked = plugin.getConfigManager().blockNetheriteArmorUpgrade;
                            ctx.getSource().getSender().sendMessage(MessageUtil.info(
                                "Netherite armor upgrades are currently <white>" + (blocked ? "blocked" : "allowed") + "</white>."));
                            return Command.SINGLE_SUCCESS;
                        }))
                    .then(Commands.literal("toggle")
                        .executes(ctx -> {
                            boolean blocked = plugin.getConfigManager().toggleBlockNetheriteArmorUpgrade();
                            ctx.getSource().getSender().sendMessage(MessageUtil.success(
                                "Netherite armor upgrades are now <white>" + (blocked ? "blocked" : "allowed") + "</white>."));
                            return Command.SINGLE_SUCCESS;
                        }))
                    .then(Commands.literal("on")
                        .executes(ctx -> {
                            plugin.getConfigManager().setBlockNetheriteArmorUpgrade(true);
                            ctx.getSource().getSender().sendMessage(MessageUtil.success(
                                "Netherite armor upgrades are now <white>blocked</white>."));
                            return Command.SINGLE_SUCCESS;
                        }))
                    .then(Commands.literal("off")
                        .executes(ctx -> {
                            plugin.getConfigManager().setBlockNetheriteArmorUpgrade(false);
                            ctx.getSource().getSender().sendMessage(MessageUtil.success(
                                "Netherite armor upgrades are now <white>allowed</white>."));
                            return Command.SINGLE_SUCCESS;
                        })))
                .build(),
            "SMPCore admin commands",
            List.of("core")
        );
    }

    private static int executeAltarSpawn(
        SMPCore plugin,
        org.bukkit.command.CommandSender sender,
        String requestedLegendary,
        boolean activeImmediately
    ) {
        if (plugin.getLegendaryAltarManager() == null) {
            sender.sendMessage(MessageUtil.error("Legendary altar system is not ready yet."));
            return 0;
        }
        sendAltarResult(sender, plugin.getLegendaryAltarManager().forceSpawnForTesting(requestedLegendary, activeImmediately));
        return Command.SINGLE_SUCCESS;
    }

    private static void sendAltarResult(
        org.bukkit.command.CommandSender sender,
        me.rique.smpcore.legendary.LegendaryAltarManager.AdminActionResult result
    ) {
        sender.sendMessage(result.success() ? MessageUtil.success(result.message()) : MessageUtil.error(result.message()));
    }

    private static CompletableFuture<Suggestions> suggestLegendaryIds(SMPCore plugin, SuggestionsBuilder builder) {
        LegendaryListener legendary = plugin.getLegendaryListener();
        if (legendary != null) {
            for (String option : legendaryCommandOptions(legendary)) {
                builder.suggest(option);
            }
        }
        return builder.buildFuture();
    }

    private static Set<String> legendaryCommandOptions(LegendaryListener legendary) {
        LinkedHashSet<String> options = new LinkedHashSet<>();
        for (String id : legendary.legendaryIds()) {
            String displayName = legendary.displayNameForLegendary(id);
            String commandToken = toCommandToken(displayName);
            if (commandToken != null && !commandToken.isBlank()) {
                options.add(commandToken);
            }
            options.add(id);
        }
        return options;
    }

    private static String toCommandToken(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.toLowerCase(Locale.ROOT)
            .replace("'", "")
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? null : normalized;
    }
}
