package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.smp.SmpStartManager;
import me.rique.smpcore.util.MessageUtil;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class SmpStartCommand {

    private SmpStartCommand() {}

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("startsmp")
                .requires(src -> src.getSender().hasPermission("smpcore.startsmp"))
                .executes(ctx -> {
                    SmpStartManager.StartResult result = plugin.getSmpStartManager().start(ctx.getSource().getSender());
                    ctx.getSource().getSender().sendMessage(
                        result.success() ? MessageUtil.success(result.message()) : MessageUtil.error(result.message())
                    );
                    return result.success() ? Command.SINGLE_SUCCESS : 0;
                })
                .then(Commands.literal("status")
                    .executes(ctx -> {
                        ctx.getSource().getSender().sendMessage(plugin.getSmpStartManager().statusMessage());
                        return Command.SINGLE_SUCCESS;
                    }))
                .then(Commands.literal("reset")
                    .requires(src -> src.getSender().hasPermission("smpcore.startsmp.reset"))
                    .executes(ctx -> {
                        SmpStartManager.StartResult result = plugin.getSmpStartManager().reset(ctx.getSource().getSender());
                        ctx.getSource().getSender().sendMessage(
                            result.success() ? MessageUtil.success(result.message()) : MessageUtil.error(result.message())
                        );
                        return result.success() ? Command.SINGLE_SUCCESS : 0;
                    }))
                .build(),
            "Start the SMP border and grace-period flow",
            List.of("smpstart")
        );
    }
}
