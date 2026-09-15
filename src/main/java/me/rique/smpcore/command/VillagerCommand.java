package me.rique.smpcore.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.player.VillagerManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

public final class VillagerCommand {

    private VillagerCommand() {
    }

    public static void register(Commands registrar, SMPCore plugin) {
        registrar.register(
            root(plugin).build(),
            "Manage SMPCore's portable villagers and infinite trade stock"
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root(SMPCore plugin) {
        return Commands.literal("clickvillagers")
            .requires(source -> source.getSender().hasPermission(VillagerManager.PERMISSION_CONFIG))
            .executes(context -> status(plugin, context.getSource().getSender()))
            .then(Commands.literal("status")
                .executes(context -> status(plugin, context.getSource().getSender())))
            .then(Commands.literal("repair")
                .executes(context -> repair(plugin, context.getSource().getSender())))
            .then(Commands.literal("selftest")
                .executes(context -> selfTest(plugin, context.getSource().getSender())))
            .then(Commands.literal("config")
                .then(Commands.literal("path")
                    .executes(context -> configPath(plugin, context.getSource().getSender())))
                .then(Commands.literal("reload")
                    .executes(context -> reload(plugin, context.getSource().getSender())))
                .then(Commands.literal("get")
                    .then(Commands.argument("option", StringArgumentType.word())
                        .executes(context -> getOption(
                            plugin,
                            context.getSource().getSender(),
                            StringArgumentType.getString(context, "option")
                        ))))
                .then(Commands.literal("set")
                    .then(Commands.argument("option", StringArgumentType.word())
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                            .executes(context -> setOption(
                                plugin,
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "option"),
                                StringArgumentType.getString(context, "value")
                            ))))));
    }

    private static int status(SMPCore plugin, CommandSender sender) {
        VillagerManager.RepairResult result = plugin.getVillagerManager().repairLoadedVillagers();
        sender.sendMessage(Component.text(
            "Native villagers active: checked " + result.checked()
                + ", repaired " + result.repaired()
                + ", infinite stock "
                + (plugin.getConfig().getBoolean("villagers.infinite-trades", true) ? "on" : "off")
                + ".",
            NamedTextColor.GREEN
        ));
        return 1;
    }

    private static int repair(SMPCore plugin, CommandSender sender) {
        VillagerManager.RepairResult result = plugin.getVillagerManager().repairLoadedVillagers();
        sender.sendMessage(Component.text(
            "Repaired " + result.repaired() + " of " + result.checked() + " loaded villagers.",
            NamedTextColor.GREEN
        ));
        return result.repaired();
    }

    private static int selfTest(SMPCore plugin, CommandSender sender) {
        VillagerManager.StockSelfTestResult result =
            plugin.getVillagerManager().runLiveStockSelfTest();
        sender.sendMessage(Component.text(
            "Native villager live test " + (result.passed() ? "passed: " : "failed: ")
                + result.detail(),
            result.passed() ? NamedTextColor.GREEN : NamedTextColor.RED
        ));
        return result.passed() ? 1 : 0;
    }

    private static int configPath(SMPCore plugin, CommandSender sender) {
        sender.sendMessage(Component.text(
            plugin.getDataFolder().toPath().resolve("config.yml").toString(),
            NamedTextColor.GRAY
        ));
        return 1;
    }

    private static int reload(SMPCore plugin, CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage(Component.text("Villager settings reloaded.", NamedTextColor.GREEN));
        return 1;
    }

    private static int getOption(SMPCore plugin, CommandSender sender, String rawOption) {
        String path = path(rawOption);
        if (!plugin.getConfig().contains(path)) {
            Object defaultValue = defaultValue(rawOption);
            if (defaultValue == null) {
                sender.sendMessage(Component.text("Unknown villager option.", NamedTextColor.RED));
                return 0;
            }
            sender.sendMessage(Component.text(
                rawOption + " = " + defaultValue + " (default)",
                NamedTextColor.YELLOW
            ));
            return 1;
        }
        sender.sendMessage(Component.text(
            rawOption + " = " + plugin.getConfig().get(path),
            NamedTextColor.YELLOW
        ));
        return 1;
    }

    private static int setOption(
        SMPCore plugin,
        CommandSender sender,
        String rawOption,
        String rawValue
    ) {
        Object defaultValue = defaultValue(rawOption);
        if (defaultValue == null) {
            sender.sendMessage(Component.text("Unknown villager option.", NamedTextColor.RED));
            return 0;
        }
        Object parsed;
        try {
            parsed = defaultValue instanceof Boolean
                ? parseBoolean(rawValue)
                : Integer.parseInt(rawValue.trim());
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage(Component.text("That value is not valid.", NamedTextColor.RED));
            return 0;
        }
        plugin.getConfig().set(path(rawOption), parsed);
        plugin.saveConfig();
        sender.sendMessage(Component.text(
            rawOption + " set to " + parsed + ".",
            NamedTextColor.GREEN
        ));
        return 1;
    }

    private static Boolean parseBoolean(String value) {
        return switch (value.trim().toLowerCase()) {
            case "true", "on", "yes", "1" -> true;
            case "false", "off", "no", "0" -> false;
            default -> throw new IllegalArgumentException("Not a boolean");
        };
    }

    private static String path(String option) {
        return "villagers." + switch (option.trim().toLowerCase().replace('-', '_')) {
            case "cooldown", "cooldown_seconds" -> "cooldown-seconds";
            case "partner_limit", "partner_limit_per_player" -> "partner-limit";
            default -> option.trim().toLowerCase().replace('_', '-');
        };
    }

    private static Object defaultValue(String rawOption) {
        return switch (rawOption.trim().toLowerCase().replace('-', '_')) {
            case "infinite_trades" -> true;
            case "claimed_damage" -> false;
            case "claimed_zombies_passive" -> true;
            case "claimed_immune_kill_command" -> true;
            case "allow_zombie_villagers" -> true;
            case "claimed_villagers_bypass_permissions" -> false;
            case "validate_partner_names" -> true;
            case "show_trades" -> true;
            case "enable_dispensers" -> false;
            case "cooldown", "cooldown_seconds" -> 2;
            case "partner_limit", "partner_limit_per_player" -> 10;
            default -> null;
        };
    }
}
