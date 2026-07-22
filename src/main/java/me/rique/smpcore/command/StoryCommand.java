package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.story.StoryAlignment;
import me.rique.smpcore.story.StoryChapter;
import me.rique.smpcore.story.StoryService;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

@SuppressWarnings("UnstableApiUsage")
public final class StoryCommand {
    private StoryCommand() { }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("veil")
                .requires(source -> source.getSender().hasPermission("smpcore.story"))
                .executes(context -> player(plugin, context.getSource().getSender(), StoryService::openJournal))
                .then(Commands.literal("journal").executes(context -> player(plugin, context.getSource().getSender(), StoryService::openJournal)))
                .then(Commands.literal("objective").executes(context -> player(plugin, context.getSource().getSender(), StoryService::sendObjective)))
                .then(Commands.literal("memories").executes(context -> player(plugin, context.getSource().getSender(), StoryService::sendMemories)))
                .then(Commands.literal("text").executes(context -> player(plugin, context.getSource().getSender(), StoryService::sendTextJournal)))
                .then(Commands.literal("skip").executes(context -> player(plugin, context.getSource().getSender(), StoryService::skip)))
                .then(Commands.literal("choose")
                    .then(Commands.argument("alignment", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            List.of("mend", "bind", "sever").forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> choose(plugin, context.getSource().getSender(), StringArgumentType.getString(context, "alignment")))))
                .then(Commands.literal("admin")
                    .requires(source -> source.getSender().hasPermission("smpcore.story.admin"))
                    .executes(context -> usage(context.getSource().getSender()))
                    .then(Commands.literal("reload").executes(context -> reload(plugin, context.getSource().getSender())))
                    .then(Commands.literal("status")
                        .then(Commands.argument("target", ArgumentTypes.player()).executes(context -> status(plugin, context.getSource().getSender(), target(context, "target")))))
                    .then(Commands.literal("setchapter")
                        .then(Commands.argument("target", ArgumentTypes.player())
                            .then(Commands.argument("chapter", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (StoryChapter chapter : StoryChapter.values()) builder.suggest(chapter.name().toLowerCase(Locale.ROOT));
                                    return builder.buildFuture();
                                })
                                .executes(context -> setChapter(plugin, context.getSource().getSender(), target(context, "target"), StringArgumentType.getString(context, "chapter"))))))
                    .then(Commands.literal("setstage")
                        .then(Commands.argument("target", ArgumentTypes.player())
                            .then(Commands.argument("stage", StringArgumentType.word())
                                .executes(context -> setStage(plugin, context.getSource().getSender(), target(context, "target"), StringArgumentType.getString(context, "stage"))))))
                    .then(Commands.literal("flag")
                        .then(Commands.argument("target", ArgumentTypes.player())
                            .then(Commands.argument("key", StringArgumentType.word())
                                .then(Commands.argument("value", StringArgumentType.string())
                                    .executes(context -> flag(plugin, context.getSource().getSender(), target(context, "target"),
                                        StringArgumentType.getString(context, "key"), StringArgumentType.getString(context, "value")))))))
                    .then(Commands.literal("unlock")
                        .then(Commands.argument("target", ArgumentTypes.player())
                            .then(Commands.argument("entry", StringArgumentType.word())
                                .executes(context -> entry(plugin, context.getSource().getSender(), target(context, "target"), StringArgumentType.getString(context, "entry"), true)))))
                    .then(Commands.literal("lock")
                        .then(Commands.argument("target", ArgumentTypes.player())
                            .then(Commands.argument("entry", StringArgumentType.word())
                                .executes(context -> entry(plugin, context.getSource().getSender(), target(context, "target"), StringArgumentType.getString(context, "entry"), false)))))
                    .then(Commands.literal("replay")
                        .then(Commands.argument("target", ArgumentTypes.player())
                            .then(Commands.argument("node", StringArgumentType.word())
                                .executes(context -> replay(plugin, context.getSource().getSender(), target(context, "target"), StringArgumentType.getString(context, "node"))))))
                    .then(Commands.literal("reset")
                        .then(Commands.argument("target", ArgumentTypes.player())
                            .executes(context -> reset(plugin, context.getSource().getSender(), target(context, "target"), false))
                            .then(Commands.argument("keepBossHistory", BoolArgumentType.bool())
                                .executes(context -> reset(plugin, context.getSource().getSender(), target(context, "target"), BoolArgumentType.getBool(context, "keepBossHistory"))))))
                    .then(Commands.literal("debug")
                        .then(Commands.argument("target", ArgumentTypes.player())
                            .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> debug(plugin, context.getSource().getSender(), target(context, "target"), BoolArgumentType.getBool(context, "enabled"))))))
                    .then(Commands.literal("migrate")
                        .then(Commands.argument("target", ArgumentTypes.player())
                            .executes(context -> migrate(plugin, context.getSource().getSender(), target(context, "target"))))))
                .build(),
            "Open The Eleventh Oath lore journal",
            List.of("veiljournal")
        );
    }

    private static int player(SMPCore plugin, CommandSender sender, java.util.function.BiConsumer<StoryService, Player> action) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("This command is for players."));
            return 0;
        }
        StoryService story = plugin.getStoryService();
        if (story == null || !story.ready()) {
            sender.sendMessage(MessageUtil.warn("The story journal is still loading."));
            return 0;
        }
        action.accept(story, player);
        return Command.SINGLE_SUCCESS;
    }

    private static int choose(SMPCore plugin, CommandSender sender, String rawChoice) {
        return player(plugin, sender, (story, player) -> {
            StoryAlignment choice = StoryAlignment.parse(rawChoice);
            if (choice == StoryAlignment.UNDECIDED || !story.choose(player, choice)) {
                player.sendMessage(MessageUtil.warn("Choose <white>mend</white>, <white>bind</white>, or <white>sever</white> after recovering Aurel's memory."));
            }
        });
    }

    private static int reload(SMPCore plugin, CommandSender sender) {
        plugin.getStoryService().reload(true);
        sender.sendMessage(MessageUtil.info("Reloading The Eleventh Oath files asynchronously..."));
        return Command.SINGLE_SUCCESS;
    }

    private static int status(SMPCore plugin, CommandSender sender, Player target) {
        if (target == null) return missing(sender);
        sender.sendMessage(MessageUtil.info("<white>" + target.getName() + "</white>: " + plugin.getStoryService().status(target)));
        return Command.SINGLE_SUCCESS;
    }

    private static int setChapter(SMPCore plugin, CommandSender sender, Player target, String rawChapter) {
        if (target == null) return missing(sender);
        StoryChapter chapter = StoryChapter.parse(rawChapter);
        plugin.getStoryService().adminSetChapter(target, chapter);
        return changed(plugin, sender, target, "set chapter to " + chapter);
    }

    private static int setStage(SMPCore plugin, CommandSender sender, Player target, String stage) {
        if (target == null) return missing(sender);
        plugin.getStoryService().adminSetStage(target, stage);
        return changed(plugin, sender, target, "set stage to " + stage);
    }

    private static int flag(SMPCore plugin, CommandSender sender, Player target, String key, String value) {
        if (target == null) return missing(sender);
        plugin.getStoryService().adminSetFlag(target, key, value);
        return changed(plugin, sender, target, "set flag " + key + "=" + value);
    }

    private static int entry(SMPCore plugin, CommandSender sender, Player target, String entry, boolean unlock) {
        if (target == null) return missing(sender);
        if (unlock) plugin.getStoryService().adminUnlock(target, entry); else plugin.getStoryService().adminLock(target, entry);
        return changed(plugin, sender, target, (unlock ? "unlocked " : "locked ") + entry);
    }

    private static int replay(SMPCore plugin, CommandSender sender, Player target, String node) {
        if (target == null) return missing(sender);
        if (!plugin.getStoryService().replay(target, node)) {
            sender.sendMessage(MessageUtil.error("Unknown dialogue node or unloaded profile."));
            return 0;
        }
        return changed(plugin, sender, target, "replayed " + node);
    }

    private static int reset(SMPCore plugin, CommandSender sender, Player target, boolean keepHistory) {
        if (target == null) return missing(sender);
        plugin.getStoryService().adminReset(target, keepHistory);
        return changed(plugin, sender, target, "reset story" + (keepHistory ? " while preserving boss history" : ""));
    }

    private static int debug(SMPCore plugin, CommandSender sender, Player target, boolean enabled) {
        if (target == null) return missing(sender);
        plugin.getStoryService().adminDebug(target, enabled);
        return changed(plugin, sender, target, "set debug=" + enabled);
    }

    private static int migrate(SMPCore plugin, CommandSender sender, Player target) {
        if (target == null) return missing(sender);
        plugin.getStoryService().adminMigrate(target);
        plugin.getLogger().info(sender.getName() + " requested story migration for " + target.getName() + ".");
        sender.sendMessage(MessageUtil.info("Checking recorded boss fights for <white>" + target.getName() + "</white>..."));
        return Command.SINGLE_SUCCESS;
    }

    private static int changed(SMPCore plugin, CommandSender sender, Player target, String action) {
        plugin.getLogger().warning(sender.getName() + " " + action + " for story profile " + target.getName() + ".");
        sender.sendMessage(MessageUtil.success("Updated <white>" + target.getName() + "</white>: " + action + "."));
        return Command.SINGLE_SUCCESS;
    }

    private static int usage(CommandSender sender) {
        sender.sendMessage(MessageUtil.info("<white>/veil admin status|setchapter|setstage|flag|unlock|lock|replay|reset|debug|migrate|reload</white>"));
        return Command.SINGLE_SUCCESS;
    }

    private static int missing(CommandSender sender) {
        sender.sendMessage(MessageUtil.error("Player not found."));
        return 0;
    }

    private static Player target(com.mojang.brigadier.context.CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack> context, String name)
        throws CommandSyntaxException {
        PlayerSelectorArgumentResolver resolver = context.getArgument(name, PlayerSelectorArgumentResolver.class);
        List<Player> players = resolver.resolve(context.getSource());
        return players.isEmpty() ? null : players.get(0);
    }
}
