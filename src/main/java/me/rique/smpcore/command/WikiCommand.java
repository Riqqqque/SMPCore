package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class WikiCommand {

    private static final String WIKI_URL = "https://github.com/Riqqqque/SMPCore/wiki";

    private WikiCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("wiki")
                .requires(source -> source.getSender().hasPermission("smpcore.wiki"))
                .executes(ctx -> {
                    if (ctx.getSource().getSender() instanceof Player player && BedrockCompat.isBedrockPlayer(player)) {
                        player.sendMessage(MessageUtil.prefixedRaw(
                            "<gradient:#ff4d6d:#facc15><bold>SMPCore Wiki</bold></gradient> <gray>- open this address in your browser:</gray>"
                        ));
                        player.sendMessage(MessageUtil.prefixedRaw("<aqua>" + WIKI_URL + "</aqua>"));
                        return Command.SINGLE_SUCCESS;
                    }
                    ctx.getSource().getSender().sendMessage(MessageUtil.prefixedRaw(
                        "<gradient:#ff4d6d:#facc15><bold>SMPCore Wiki</bold></gradient> <gray>- click below for guides, bosses, classes, Reliquary recipes, and admin notes.</gray>"
                    ));
                    ctx.getSource().getSender().sendMessage(MessageUtil.prefixedRaw(
                        "<click:open_url:'" + WIKI_URL + "'><hover:show_text:'<gray>Open <white>" + WIKI_URL + "</white></gray>'><aqua><underlined>Open the SMPCore Wiki</underlined></aqua></hover></click>"
                    ));
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Show the SMPCore wiki link",
            List.of("guide", "smpwiki")
        );
    }
}
