package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.compat.BedrockSkullManager;
import me.rique.smpcore.compat.BedrockSkullManager.RegistrationResult;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class BedrockSkullCommand {

    private BedrockSkullCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("bedrockskulls")
                .requires(source -> source.getSender().hasPermission("smpcore.bedrockskulls.admin"))
                .executes(ctx -> status(plugin, ctx.getSource().getSender()))
                .then(Commands.literal("register")
                    .executes(ctx -> register(plugin, ctx.getSource().getSender())))
                .then(Commands.literal("scan")
                    .executes(ctx -> scan(plugin, ctx.getSource().getSender(), 32))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                        .executes(ctx -> scan(plugin, ctx.getSource().getSender(), IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(Commands.literal("status")
                    .executes(ctx -> status(plugin, ctx.getSource().getSender())))
                .build(),
            "Register custom heads for Geyser Bedrock clients",
            List.of("bedrockheads")
        );
    }

    private static int register(SMPCore plugin, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Must be a player holding a custom head."));
            return 0;
        }
        BedrockSkullManager manager = plugin.getBedrockSkullManager();
        RegistrationResult result = manager == null ? RegistrationResult.UNAVAILABLE : manager.registerHeldSkull(player);
        sender.sendMessage(switch (result) {
            case ADDED -> MessageUtil.success("Skull registered. Restart the server once to rebuild Geyser's Bedrock pack.");
            case ALREADY_REGISTERED -> MessageUtil.info("That skull is already registered for Bedrock.");
            case NOT_A_SKULL -> MessageUtil.warn("Hold the custom player head in your main hand.");
            case NO_PROFILE -> MessageUtil.warn("That head has no usable skin profile.");
            case LIMIT_REACHED -> MessageUtil.error("The safe custom skull limit has been reached.");
            case UNAVAILABLE -> MessageUtil.error("Geyser's custom skull registry is unavailable.");
        });
        return result == RegistrationResult.ADDED || result == RegistrationResult.ALREADY_REGISTERED ? Command.SINGLE_SUCCESS : 0;
    }

    private static int scan(SMPCore plugin, CommandSender sender, int radius) {
        if (!(sender instanceof Player player) || plugin.getBedrockSkullManager() == null) return 0;
        int added = plugin.getBedrockSkullManager().scanNearby(player.getLocation(), radius);
        player.sendMessage(MessageUtil.success("Registered <white>" + added + "</white> new nearby skull texture(s)."));
        if (added > 0) player.sendMessage(MessageUtil.info("Restart the server once so Bedrock players receive the rebuilt pack."));
        return Command.SINGLE_SUCCESS;
    }

    private static int status(SMPCore plugin, CommandSender sender) {
        BedrockSkullManager manager = plugin.getBedrockSkullManager();
        if (manager == null || !manager.isAvailable()) {
            sender.sendMessage(MessageUtil.error("Geyser's custom skull registry is unavailable."));
            return 0;
        }
        sender.sendMessage(MessageUtil.info("Bedrock skulls registered: <white>" + manager.registeredCount() + "</white>."));
        sender.sendMessage(MessageUtil.info("Restart required: <white>" + (manager.isRestartRequired() ? "yes" : "no") + "</white>."));
        return Command.SINGLE_SUCCESS;
    }
}
