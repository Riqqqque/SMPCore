package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class ChangelogCommand {

    private ChangelogCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("changelog")
                .requires(src -> src.getSender().hasPermission("smpcore.changelog"))
                .executes(ctx -> {
                    plugin.getChangelogManager().send(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("reload")
                    .requires(src -> src.getSender().hasPermission("smpcore.changelog.reload"))
                    .executes(ctx -> {
                        if (!plugin.getChangelogManager().reload()) {
                            ctx.getSource().getSender().sendMessage(MessageUtil.error(
                                "Could not reload <white>" + plugin.getChangelogManager().getFile().getName()
                                    + "</white>. Check the console and fix the file."
                            ));
                            return Command.SINGLE_SUCCESS;
                        }
                        ctx.getSource().getSender().sendMessage(MessageUtil.success(
                            "Reloaded changelog from <white>" + plugin.getChangelogManager().getFile().getName() + "</white>."
                        ));
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "Show recent player-facing server changes",
            List.of("changes", "updates")
        );
    }
}
