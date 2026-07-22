package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

@SuppressWarnings("UnstableApiUsage")
public final class FamiliarAdminCommand {
    private FamiliarAdminCommand() { }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("familiaradmin")
                .requires(source -> source.getSender().hasPermission("smpcore.familiar.admin"))
                .executes(context -> usage(plugin, context.getSource().getSender()))
                .then(Commands.literal("list")
                    .executes(context -> list(plugin, context.getSource().getSender())))
                .then(Commands.literal("give")
                    .then(Commands.argument("target", ArgumentTypes.player())
                        .then(Commands.argument("familiar", StringArgumentType.word())
                            .suggests((context, builder) -> suggest(plugin, builder))
                            .executes(context -> modify(
                                plugin,
                                context.getSource().getSender(),
                                firstTarget(context.getArgument("target", PlayerSelectorArgumentResolver.class), context.getSource()),
                                StringArgumentType.getString(context, "familiar"),
                                true
                            )))))
                .then(Commands.literal("take")
                    .then(Commands.argument("target", ArgumentTypes.player())
                        .then(Commands.argument("familiar", StringArgumentType.word())
                            .suggests((context, builder) -> suggest(plugin, builder))
                            .executes(context -> modify(
                                plugin,
                                context.getSource().getSender(),
                                firstTarget(context.getArgument("target", PlayerSelectorArgumentResolver.class), context.getSource()),
                                StringArgumentType.getString(context, "familiar"),
                                false
                            )))))
                .build(),
            "Give or remove any registered familiar",
            List.of("famadmin")
        );
    }

    private static Map<String, FamiliarEntry> registry(SMPCore plugin) {
        Map<String, FamiliarEntry> entries = new LinkedHashMap<>();
        if (plugin.getMayorQuestManager() != null) {
            entries.put("veil_wisp", new FamiliarEntry(
                "Veil Wisp",
                plugin.getMayorQuestManager()::hasPetUnlocked,
                plugin.getMayorQuestManager()::grantPet,
                plugin.getMayorQuestManager()::revokePet
            ));
        }
        if (plugin.getMinerManager() != null) {
            entries.put("miner", new FamiliarEntry(
                "Miner Familiar",
                plugin.getMinerManager()::hasMinerPet,
                plugin.getMinerManager()::grantMinerPet,
                plugin.getMinerManager()::revokeMinerPet
            ));
        }
        if (plugin.getFarmerManager() != null) {
            entries.put("tiller", new FamiliarEntry(
                "Tiller, the Sprout Mole",
                plugin.getFarmerManager()::hasTiller,
                plugin.getFarmerManager()::grantTiller,
                plugin.getFarmerManager()::revokeTiller
            ));
        }
        if (plugin.getWitchManager() != null) {
            entries.put("morrow", new FamiliarEntry(
                "Morrow, the Rosy Moonmoth",
                plugin.getWitchManager()::hasMorrow,
                plugin.getWitchManager()::grantMorrow,
                plugin.getWitchManager()::revokeMorrow
            ));
        }
        return entries;
    }

    private static int modify(SMPCore plugin, CommandSender sender, Player target, String rawId, boolean give) {
        if (target == null) {
            sender.sendMessage(MessageUtil.error("Player not found."));
            return 0;
        }
        Map<String, FamiliarEntry> entries = registry(plugin);
        String id = normalize(rawId);
        if (id.equals("all")) {
            entries.values().forEach(entry -> {
                if (give) entry.grant.accept(target, sender); else entry.revoke.accept(target, sender);
            });
            sender.sendMessage(MessageUtil.success((give ? "Granted" : "Removed") + " all registered familiars for <white>" + target.getName() + "</white>."));
            return Command.SINGLE_SUCCESS;
        }
        FamiliarEntry entry = entries.get(id);
        if (entry == null) {
            sender.sendMessage(MessageUtil.error("Unknown familiar. Use <white>/familiaradmin list</white>."));
            return 0;
        }
        if (give) entry.grant.accept(target, sender); else entry.revoke.accept(target, sender);
        return Command.SINGLE_SUCCESS;
    }

    private static int list(SMPCore plugin, CommandSender sender) {
        sender.sendMessage(MessageUtil.info("Registered familiars:"));
        registry(plugin).forEach((id, entry) -> sender.sendMessage(MessageUtil.info("<white>" + id + "</white> - " + entry.displayName)));
        sender.sendMessage(MessageUtil.info("<white>all</white> - every registered familiar."));
        return Command.SINGLE_SUCCESS;
    }

    private static int usage(SMPCore plugin, CommandSender sender) {
        sender.sendMessage(MessageUtil.info("<white>/familiaradmin give <player> <familiar|all></white>"));
        sender.sendMessage(MessageUtil.info("<white>/familiaradmin take <player> <familiar|all></white>"));
        return list(plugin, sender);
    }

    private static CompletableFuture<Suggestions> suggest(SMPCore plugin, SuggestionsBuilder builder) {
        registry(plugin).keySet().forEach(builder::suggest);
        builder.suggest("all");
        return builder.buildFuture();
    }

    private static String normalize(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (value) {
            case "wisp", "veilwisp", "veil_wisp_familiar" -> "veil_wisp";
            case "miner_familiar", "miner_pet" -> "miner";
            case "farmer", "farmer_pet", "sprout_mole" -> "tiller";
            case "witch", "witch_pet", "moonmoth" -> "morrow";
            default -> value;
        };
    }

    private static Player firstTarget(PlayerSelectorArgumentResolver resolver, io.papermc.paper.command.brigadier.CommandSourceStack source) throws CommandSyntaxException {
        List<Player> players = resolver.resolve(source);
        return players.isEmpty() ? null : players.get(0);
    }

    private record FamiliarEntry(
        String displayName,
        Predicate<Player> unlocked,
        BiConsumer<Player, CommandSender> grant,
        BiConsumer<Player, CommandSender> revoke
    ) { }
}
