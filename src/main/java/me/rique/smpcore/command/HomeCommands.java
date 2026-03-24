package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.entity.Player;

@SuppressWarnings("UnstableApiUsage")
public final class HomeCommands {

    private HomeCommands() {}

    public static void register(Commands commands, SMPCore plugin) {

        commands.register(
            Commands.literal("home")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.home"))
                .executes(ctx -> {
                    plugin.getHomeManager().teleportHome((Player) ctx.getSource().getSender(), "home");
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> {
                        plugin.getHomeManager().teleportHome(
                            (Player) ctx.getSource().getSender(),
                            StringArgumentType.getString(ctx, "name")
                        );
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "Teleport to a saved home"
        );

        commands.register(
            Commands.literal("sethome")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.home"))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    setHome(player, "home", plugin);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        setHome(player, StringArgumentType.getString(ctx, "name"), plugin);
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "Set a home at your current location"
        );

        registerDeleteHomeCommand(commands, plugin, "delhome");
        registerDeleteHomeCommand(commands, plugin, "deletehome");
        registerDeleteHomeCommand(commands, plugin, "removehome");

        commands.register(
            Commands.literal("homes")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.home"))
                .executes(ctx -> {
                    plugin.getHomeManager().listHomes((Player) ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "List your saved homes"
        );
    }

    private static void setHome(Player player, String name, SMPCore plugin) {
        plugin.getHomeManager().setHome(
            player,
            name,
            player.getLocation(),
            () -> player.sendMessage(MessageUtil.success(setSuccessMessage(name))),
            () -> player.sendMessage(MessageUtil.error(
                "Home limit reached (<white>" + plugin.getHomeManager().maxHomes(player) + "</white>).")),
            () -> player.sendMessage(MessageUtil.error("Could not save home right now."))
        );
    }

    private static void registerDeleteHomeCommand(Commands commands, SMPCore plugin, String literal) {
        commands.register(
            Commands.literal(literal)
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.home"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        String name = StringArgumentType.getString(ctx, "name");
                        plugin.getHomeManager().deleteHome(
                            player,
                            name,
                            () -> player.sendMessage(MessageUtil.success(deleteSuccessMessage(name))),
                            () -> player.sendMessage(MessageUtil.error(notFoundMessage(name))),
                            () -> player.sendMessage(MessageUtil.error("Could not delete home right now."))
                        );
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "Delete a saved home"
        );
    }

    private static String setSuccessMessage(String name) {
        return isDefaultHome(name)
            ? "Default home set."
            : "Home <white>" + name + "</white> set.";
    }

    private static String deleteSuccessMessage(String name) {
        return isDefaultHome(name)
            ? "Default home deleted."
            : "Home <white>" + name + "</white> deleted.";
    }

    private static String notFoundMessage(String name) {
        return isDefaultHome(name)
            ? "No default home is set."
            : "No home named <white>" + name + "</white>.";
    }

    private static boolean isDefaultHome(String name) {
        return "home".equalsIgnoreCase(name);
    }
}
