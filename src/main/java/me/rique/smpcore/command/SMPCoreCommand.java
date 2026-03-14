package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;

import java.util.List;

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
                        plugin.restartDragonEggListener();
                        plugin.getSpawnerManager().refreshAllFromConfig();
                        if (plugin.getVeinMinerListener() != null) {
                            plugin.getVeinMinerListener().reloadConfig();
                        }
                        ctx.getSource().getSender().sendMessage(
                            MessageUtil.success("Configuration reloaded."));
                        return Command.SINGLE_SUCCESS;
                    }))
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
}
