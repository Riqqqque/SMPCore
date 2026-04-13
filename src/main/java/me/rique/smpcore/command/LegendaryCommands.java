package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.legendary.LegendaryListener;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
public final class LegendaryCommands {

    private LegendaryCommands() {}

    public static void register(Commands commands, SMPCore plugin) {
        registerRecipeCommand(commands, plugin);
        registerAdminLegendaryCommand(commands, plugin);
    }

    private static void registerRecipeCommand(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("lrecipe")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.legendary.recipe"))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    LegendaryListener legendary = plugin.getLegendaryListener();
                    if (legendary == null) {
                        player.sendMessage(MessageUtil.error("Legendary system is not ready yet."));
                        return 0;
                    }
                    legendary.openRecipeMenu(player);
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Open the legendary recipes GUI",
            List.of("lrecipes")
        );
    }

    private static void registerAdminLegendaryCommand(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("legendary")
                .requires(src -> src.getSender().hasPermission("smpcore.legendary.admin"))
                .then(Commands.literal("give")
                    .then(Commands.argument("item", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestLegendaryIds(plugin, builder))
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player self)) {
                                ctx.getSource().getSender().sendMessage(
                                    MessageUtil.error("Console must specify a target."));
                                return 0;
                            }
                            String itemId = StringArgumentType.getString(ctx, "item");
                            return executeGive(plugin, ctx.getSource().getSender(), self, itemId);
                        })
                        .then(Commands.argument("target", ArgumentTypes.player())
                            .executes(ctx -> {
                                List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                                    .resolve(ctx.getSource());
                                if (targets.isEmpty()) {
                                    ctx.getSource().getSender().sendMessage(MessageUtil.error("Player not found."));
                                    return 0;
                                }
                                String itemId = StringArgumentType.getString(ctx, "item");
                                return executeGive(plugin, ctx.getSource().getSender(), targets.get(0), itemId);
                            }))))
                .build(),
            "Legendary item admin commands"
        );
    }

    private static int executeGive(SMPCore plugin, org.bukkit.command.CommandSender sender, Player target, String requestedId) {
        LegendaryListener legendary = plugin.getLegendaryListener();
        if (legendary == null) {
            sender.sendMessage(MessageUtil.error("Legendary system is not ready yet."));
            return 0;
        }

        String normalized = legendary.normalizeLegendaryId(requestedId);
        ItemStack item = legendary.createLegendaryById(normalized);
        if (item == null) {
            sender.sendMessage(MessageUtil.error(
                "Unknown legendary item. Options: <white>" + String.join(", ", legendaryCommandOptions(legendary)) + "</white>."));
            return 0;
        }

        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
        leftovers.values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        legendary.resyncLegendaryOwnership(target);

        String pretty = legendary.displayNameForLegendary(normalized);
        if (pretty == null || pretty.isBlank()) {
            pretty = prettyName(normalized);
        }
        target.sendMessage(MessageUtil.success("You received <white>" + pretty + "</white>."));
        if (!sender.equals(target)) {
            sender.sendMessage(MessageUtil.success(
                "Gave <white>" + pretty + "</white> to <white>" + target.getName() + "</white>."));
        }
        return Command.SINGLE_SUCCESS;
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

    private static String prettyName(String id) {
        if (id == null || id.isBlank()) return "Unknown";
        String[] parts = id.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            sb.append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }
}
