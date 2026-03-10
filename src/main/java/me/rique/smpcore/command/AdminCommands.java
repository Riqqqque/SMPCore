package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.item.ReplenishListener;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Staff/admin commands: /fly, /vanish, /heal, /feed, /speed, /god, /nick, /invsee, /setspawn.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AdminCommands {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private AdminCommands() {}

    public static void register(Commands commands, SMPCore plugin) {
        registerFly(commands, plugin);
        registerVanish(commands, plugin);
        registerHeal(commands, plugin);
        registerFeed(commands, plugin);
        registerSpeed(commands, plugin);
        registerGod(commands, plugin);
        registerNick(commands, plugin);
        registerInvSee(commands, plugin);
        registerSetSpawn(commands, plugin);
        registerReplenishBook(commands, plugin);
    }

    // ── /fly [player] ────────────────────────────────────────────────────────

    private static void registerFly(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("fly")
                .requires(src -> src.getSender().hasPermission("smpcore.fly"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player self)) {
                        ctx.getSource().getSender().sendMessage(MessageUtil.error("Must be a player."));
                        return 0;
                    }
                    toggleFly(self, self);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("target", ArgumentTypes.player())
                    .requires(src -> src.getSender().hasPermission("smpcore.fly.others"))
                    .executes(ctx -> {
                        List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                            .resolve(ctx.getSource());
                        if (targets.isEmpty()) { ctx.getSource().getSender().sendMessage(MessageUtil.error("Player not found.")); return 0; }
                        toggleFly(targets.get(0), ctx.getSource().getSender() instanceof Player p ? p : null);
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "Toggle flight mode"
        );
    }

    private static void toggleFly(Player target, Player sender) {
        boolean flying = !target.getAllowFlight();
        target.setAllowFlight(flying);
        if (!flying) target.setFlying(false);
        target.sendMessage(MessageUtil.success("Flight <white>" + (flying ? "enabled" : "disabled") + "</white>."));
        if (sender != null && !sender.equals(target)) {
            sender.sendMessage(MessageUtil.success(
                "Flight <white>" + (flying ? "enabled" : "disabled") + "</white> for <white>" + target.getName() + "</white>."));
        }
    }

    // ── /vanish [player] ─────────────────────────────────────────────────────

    private static void registerVanish(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("vanish")
                .requires(src -> src.getSender().hasPermission("smpcore.vanish"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player self)) {
                        ctx.getSource().getSender().sendMessage(MessageUtil.error("Must be a player.")); return 0;
                    }
                    boolean now = plugin.getPlayerManager().toggleVanish(self);
                    self.sendMessage(MessageUtil.success("Vanish <white>" + (now ? "ON" : "OFF") + "</white>."));
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Toggle vanish mode",
            List.of("v")
        );
    }

    // ── /heal [player] ───────────────────────────────────────────────────────

    private static void registerHeal(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("heal")
                .requires(src -> src.getSender().hasPermission("smpcore.heal"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player self)) {
                        ctx.getSource().getSender().sendMessage(MessageUtil.error("Must be a player.")); return 0;
                    }
                    healPlayer(self);
                    self.sendMessage(MessageUtil.success("You have been healed."));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("target", ArgumentTypes.player())
                    .requires(src -> src.getSender().hasPermission("smpcore.heal.others"))
                    .executes(ctx -> {
                        List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                            .resolve(ctx.getSource());
                        if (targets.isEmpty()) { ctx.getSource().getSender().sendMessage(MessageUtil.error("Player not found.")); return 0; }
                        Player target = targets.get(0);
                        healPlayer(target);
                        target.sendMessage(MessageUtil.success("You have been healed."));
                        ctx.getSource().getSender().sendMessage(MessageUtil.success("Healed <white>" + target.getName() + "</white>."));
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "Fully heal a player"
        );
    }

    private static void healPlayer(Player p) {
        var maxHealthAttr = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) p.setHealth(maxHealthAttr.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
    }

    // ── /feed [player] ───────────────────────────────────────────────────────

    private static void registerFeed(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("feed")
                .requires(src -> src.getSender().hasPermission("smpcore.feed"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player self)) {
                        ctx.getSource().getSender().sendMessage(MessageUtil.error("Must be a player.")); return 0;
                    }
                    feedPlayer(self);
                    self.sendMessage(MessageUtil.success("You have been fed."));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("target", ArgumentTypes.player())
                    .requires(src -> src.getSender().hasPermission("smpcore.feed.others"))
                    .executes(ctx -> {
                        List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                            .resolve(ctx.getSource());
                        if (targets.isEmpty()) { ctx.getSource().getSender().sendMessage(MessageUtil.error("Player not found.")); return 0; }
                        Player target = targets.get(0);
                        feedPlayer(target);
                        target.sendMessage(MessageUtil.success("You have been fed."));
                        ctx.getSource().getSender().sendMessage(MessageUtil.success("Fed <white>" + target.getName() + "</white>."));
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "Fill a player's hunger"
        );
    }

    private static void feedPlayer(Player p) {
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setExhaustion(0f);
    }

    // ── /speed <walk|fly> <0.1‑10> [player] ──────────────────────────────────

    private static void registerSpeed(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("speed")
                .requires(src -> src.getSender().hasPermission("smpcore.speed"))
                .then(Commands.literal("walk")
                    .then(Commands.argument("value", FloatArgumentType.floatArg(0.1f, 10f))
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player self)) { return 0; }
                            float v = FloatArgumentType.getFloat(ctx, "value");
                            self.setWalkSpeed(normaliseSpeed(v, 0.2f));
                            self.sendMessage(MessageUtil.success("Walk speed set to <white>" + v + "</white>."));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("target", ArgumentTypes.player())
                            .requires(src -> src.getSender().hasPermission("smpcore.speed.others"))
                            .executes(ctx -> {
                                float v = FloatArgumentType.getFloat(ctx, "value");
                                List<Player> t = ctx.getArgument("target", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
                                if (t.isEmpty()) return 0;
                                t.get(0).setWalkSpeed(normaliseSpeed(v, 0.2f));
                                ctx.getSource().getSender().sendMessage(MessageUtil.success("Walk speed of <white>" + t.get(0).getName() + "</white> set to <white>" + v + "</white>."));
                                return Command.SINGLE_SUCCESS;
                            }))))
                .then(Commands.literal("fly")
                    .then(Commands.argument("value", FloatArgumentType.floatArg(0.1f, 10f))
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player self)) { return 0; }
                            float v = FloatArgumentType.getFloat(ctx, "value");
                            self.setFlySpeed(normaliseSpeed(v, 0.1f));
                            self.sendMessage(MessageUtil.success("Fly speed set to <white>" + v + "</white>."));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("target", ArgumentTypes.player())
                            .requires(src -> src.getSender().hasPermission("smpcore.speed.others"))
                            .executes(ctx -> {
                                float v = FloatArgumentType.getFloat(ctx, "value");
                                List<Player> t = ctx.getArgument("target", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
                                if (t.isEmpty()) return 0;
                                t.get(0).setFlySpeed(normaliseSpeed(v, 0.1f));
                                ctx.getSource().getSender().sendMessage(MessageUtil.success("Fly speed of <white>" + t.get(0).getName() + "</white> set to <white>" + v + "</white>."));
                                return Command.SINGLE_SUCCESS;
                            }))))
                .build(),
            "Set walk or fly speed",
            List.of("wspeed", "flyspeed")
        );
    }

    /** Map user-friendly 1-10 scale to Bukkit's internal speed range. */
    private static float normaliseSpeed(float value, float defaultInternal) {
        // Bukkit walk: default 0.2, max 1.0. Fly: default 0.1, max 1.0
        // We scale linearly: value 1 = default, value 10 = 1.0
        return Math.min(1.0f, defaultInternal * value);
    }

    // ── /god [player] ────────────────────────────────────────────────────────

    private static void registerGod(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("god")
                .requires(src -> src.getSender().hasPermission("smpcore.god"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player self)) {
                        ctx.getSource().getSender().sendMessage(MessageUtil.error("Must be a player.")); return 0;
                    }
                    boolean now = plugin.getPlayerManager().toggleGod(self);
                    self.sendMessage(MessageUtil.success("God mode <white>" + (now ? "ON" : "OFF") + "</white>."));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("target", ArgumentTypes.player())
                    .requires(src -> src.getSender().hasPermission("smpcore.god.others"))
                    .executes(ctx -> {
                        List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                            .resolve(ctx.getSource());
                        if (targets.isEmpty()) { ctx.getSource().getSender().sendMessage(MessageUtil.error("Player not found.")); return 0; }
                        Player target = targets.get(0);
                        boolean now = plugin.getPlayerManager().toggleGod(target);
                        target.sendMessage(MessageUtil.success("God mode <white>" + (now ? "ON" : "OFF") + "</white>."));
                        ctx.getSource().getSender().sendMessage(MessageUtil.success(
                            "God mode for <white>" + target.getName() + "</white>: <white>" + (now ? "ON" : "OFF") + "</white>."));
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "Toggle invulnerability"
        );
    }

    // ── /hat ─────────────────────────────────────────────────────────────────

    // ── /nick <name|off> ─────────────────────────────────────────────────────

    private static void registerNick(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("nick")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.nickname"))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        String input  = StringArgumentType.getString(ctx, "name");

                        if (input.equalsIgnoreCase("off") || input.equalsIgnoreCase("reset")) {
                            player.displayName(net.kyori.adventure.text.Component.text(player.getName()));
                            player.playerListName(net.kyori.adventure.text.Component.text(player.getName()));
                            plugin.getDatabase().setNickname(player.getUniqueId(), player.getName(), null);
                            player.sendMessage(MessageUtil.success("Nickname removed."));
                        } else {
                            // Allow MiniMessage formatting in nicknames
                            var component = MM.deserialize(input);
                            player.displayName(component);
                            player.playerListName(component);
                            plugin.getDatabase().setNickname(player.getUniqueId(), player.getName(), input);
                            player.sendMessage(MessageUtil.success("Nickname set to " + input + "."));
                        }
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "Set your display name",
            List.of("nickname")
        );
    }

    // ── /invsee <player> ─────────────────────────────────────────────────────

    private static void registerInvSee(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("invsee")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.invsee"))
                .then(Commands.argument("target", ArgumentTypes.player())
                    .executes(ctx -> {
                        Player viewer = (Player) ctx.getSource().getSender();
                        List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                            .resolve(ctx.getSource());
                        if (targets.isEmpty()) { viewer.sendMessage(MessageUtil.error("Player not found.")); return 0; }
                        Player target = targets.get(0);
                        viewer.openInventory(target.getInventory());
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "View another player's inventory"
        );
    }

    // ── /setspawn ────────────────────────────────────────────────────────────

    private static void registerSetSpawn(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("setspawn")
                .requires(src -> src.getSender() instanceof Player p && p.hasPermission("smpcore.setspawn"))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    World world = player.getWorld();
                    world.setSpawnLocation(
                        player.getLocation().getBlockX(),
                        player.getLocation().getBlockY(),
                        player.getLocation().getBlockZ()
                    );
                    plugin.getConfig().set("spawn.world", world.getName());
                    plugin.saveConfig();
                    plugin.getConfigManager().reload();
                    player.sendMessage(MessageUtil.success("Spawn point set to your current location."));
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Set the world spawn point to your location"
        );
    }

    private static void registerReplenishBook(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("replenishbook")
                .requires(src -> src.getSender().hasPermission("smpcore.replenish.admin"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player self)) {
                        ctx.getSource().getSender().sendMessage(MessageUtil.error("Console must specify a target."));
                        return 0;
                    }
                    return giveReplenishBook(plugin, ctx.getSource().getSender(), self);
                })
                .then(Commands.argument("target", ArgumentTypes.player())
                    .executes(ctx -> {
                        List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                            .resolve(ctx.getSource());
                        if (targets.isEmpty()) {
                            ctx.getSource().getSender().sendMessage(MessageUtil.error("Player not found."));
                            return 0;
                        }
                        return giveReplenishBook(plugin, ctx.getSource().getSender(), targets.get(0));
                    }))
                .build(),
            "Give a Replenish enchant book"
        );
    }

    private static int giveReplenishBook(SMPCore plugin, org.bukkit.command.CommandSender sender, Player target) {
        ReplenishListener replenish = plugin.getReplenishListener();
        if (replenish == null) {
            sender.sendMessage(MessageUtil.error("Replenish system is not ready yet."));
            return 0;
        }

        ItemStack book = replenish.createReplenishBook();
        var leftovers = target.getInventory().addItem(book);
        leftovers.values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));

        target.sendMessage(MessageUtil.success("You received a <white>Replenish Book</white>."));
        if (!sender.equals(target)) {
            sender.sendMessage(MessageUtil.success(
                "Gave a <white>Replenish Book</white> to <white>" + target.getName() + "</white>."
            ));
        }
        return Command.SINGLE_SUCCESS;
    }
}
