package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.smp.SmpStartManager;
import me.rique.smpcore.util.CommandSuggestionUtil;
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
                .then(Commands.argument("graceMinutes", IntegerArgumentType.integer(0, 10_080))
                    .suggests((ctx, builder) -> CommandSuggestionUtil.suggestNumbers(builder, 0, 5, 10, 15, 30, 60, 120, 1440))
                    .executes(ctx -> {
                        int graceMinutes = IntegerArgumentType.getInteger(ctx, "graceMinutes");
                        SmpStartManager.StartResult result = plugin.getSmpStartManager().start(ctx.getSource().getSender(), graceMinutes);
                        ctx.getSource().getSender().sendMessage(
                            result.success() ? MessageUtil.success(result.message()) : MessageUtil.error(result.message())
                        );
                        return result.success() ? Command.SINGLE_SUCCESS : 0;
                    }))
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
                .then(Commands.literal("lock")
                    .requires(src -> src.getSender().hasPermission("smpcore.startsmp.lock"))
                    .executes(ctx -> {
                        SmpStartManager.StartResult result = plugin.getSmpStartManager().lock(ctx.getSource().getSender());
                        ctx.getSource().getSender().sendMessage(
                            result.success() ? MessageUtil.success(result.message()) : MessageUtil.error(result.message())
                        );
                        return result.success() ? Command.SINGLE_SUCCESS : 0;
                    }))
                .then(Commands.literal("barrier")
                    .requires(src -> src.getSender().hasPermission("smpcore.startsmp.lock"))
                    .executes(ctx -> {
                        SmpStartManager.StartResult result = plugin.getSmpStartManager().lock(ctx.getSource().getSender());
                        ctx.getSource().getSender().sendMessage(
                            result.success() ? MessageUtil.success(result.message()) : MessageUtil.error(result.message())
                        );
                        return result.success() ? Command.SINGLE_SUCCESS : 0;
                    }))
                .then(Commands.literal("preview")
                    .executes(ctx -> {
                        SmpStartManager.StartResult result = plugin.getSmpStartManager().toggleBarrierPreview(
                            ctx.getSource().getSender()
                        );
                        ctx.getSource().getSender().sendMessage(
                            result.success() ? MessageUtil.success(result.message()) : MessageUtil.error(result.message())
                        );
                        return result.success() ? Command.SINGLE_SUCCESS : 0;
                    }))
                .build(),
            "Open or lock the SMP staging barrier and grace-period flow",
            List.of("smpstart")
        );
    }
}
