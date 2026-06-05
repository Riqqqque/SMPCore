package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class ServerUtilityCommands {

    private ServerUtilityCommands() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("day")
                .requires(src -> src.getSender().hasPermission("smpcore.time"))
                .executes(ctx -> {
                    setTime(ctx.getSource().getSender(), 1000L, "day");
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Set the current world to day"
        );

        commands.register(
            Commands.literal("night")
                .requires(src -> src.getSender().hasPermission("smpcore.time"))
                .executes(ctx -> {
                    setTime(ctx.getSource().getSender(), 13000L, "night");
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Set the current world to night"
        );

        commands.register(
            Commands.literal("sun")
                .requires(src -> src.getSender().hasPermission("smpcore.weather"))
                .executes(ctx -> {
                    setWeather(ctx.getSource().getSender(), false, false, "clear weather");
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Clear weather in the current world"
        );

        commands.register(
            Commands.literal("storm")
                .requires(src -> src.getSender().hasPermission("smpcore.weather"))
                .executes(ctx -> {
                    setWeather(ctx.getSource().getSender(), true, true, "storm");
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Start a thunderstorm in the current world"
        );
    }

    private static void setTime(CommandSender sender, long time, String label) {
        List<World> worlds = targetWorlds(sender);
        for (World world : worlds) {
            world.setTime(time);
        }
        sender.sendMessage(MessageUtil.success("Set <white>" + worldLabel(sender, worlds) + "</white> to <white>" + label + "</white>."));
    }

    private static void setWeather(CommandSender sender, boolean storm, boolean thunder, String label) {
        List<World> worlds = targetWorlds(sender);
        for (World world : worlds) {
            world.setStorm(storm);
            world.setThundering(thunder);
            world.setWeatherDuration(storm ? 20 * 60 * 10 : 20 * 60 * 20);
            world.setThunderDuration(thunder ? 20 * 60 * 10 : 20 * 60 * 20);
        }
        sender.sendMessage(MessageUtil.success("Set <white>" + worldLabel(sender, worlds) + "</white> to <white>" + label + "</white>."));
    }

    private static List<World> targetWorlds(CommandSender sender) {
        if (sender instanceof Player player) {
            return List.of(player.getWorld());
        }
        return Bukkit.getWorlds();
    }

    private static String worldLabel(CommandSender sender, List<World> worlds) {
        if (sender instanceof Player player) {
            return player.getWorld().getName();
        }
        return worlds.size() + " worlds";
    }
}
