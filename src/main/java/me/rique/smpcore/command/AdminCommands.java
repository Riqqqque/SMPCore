package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.ban.BanListType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.audit.ItemAuditManager;
import me.rique.smpcore.awakening.AwakeningTableListener;
import me.rique.smpcore.backpack.BackpackListener;
import me.rique.smpcore.boss.BossManager;
import me.rique.smpcore.item.CustomEnchantListener;
import me.rique.smpcore.item.CustomToolListener;
import me.rique.smpcore.item.ReplenishListener;
import me.rique.smpcore.item.RewardLanternListener;
import me.rique.smpcore.item.SustenanceTalismanListener;
import me.rique.smpcore.legendary.LegendaryListener;
import me.rique.smpcore.legendary.MythicForgeListener;
import me.rique.smpcore.power.SuperpowerManager;
import me.rique.smpcore.power.SuperpowerType;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.destroystokyo.paper.profile.PlayerProfile;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Staff/admin commands: /fly, /vanish, /heal, /feed, /speed, /god, /nick, /invsee, /setspawn.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AdminCommands {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String BACKPACK_ITEM_ID = "backpack";
    private static final String AWAKENING_TABLE_ITEM_ID = "awakening_table";
    private static final String ENDER_BONE_ITEM_ID = "ender_bone";
    private static final String ORB_OF_THE_MYSTICS_ITEM_ID = "orb_of_the_mystics";
    private static final String FARADAYS_MAGNET_ITEM_ID = "faradays_magnet";
    private static final String MYTHIC_FORGE_ITEM_ID = MythicForgeListener.MYTHIC_FORGE_ITEM_ID;
    private static final String ASCENDANT_CORE_ITEM_ID = MythicForgeListener.ASCENDANT_CORE_ITEM_ID;
    private static final String TALISMAN_OF_SUSTENANCE_ITEM_ID = SustenanceTalismanListener.ITEM_ID;
    private static final String ANCIENT_SCROLL_ITEM_ID = SuperpowerManager.ANCIENT_SCROLL_ITEM_ID;
    private static final String WARDEN_HEART_ITEM_ID = SuperpowerManager.WARDEN_HEART_ITEM_ID;
    private static final String MOTHER_NATURE_STICK_ITEM_ID = SuperpowerManager.MOTHER_NATURE_STICK_ITEM_ID;
    private static final String THE_WORLD_CLOCK_ITEM_ID = SuperpowerManager.THE_WORLD_CLOCK_ITEM_ID;
    private static final String DRUID_GRIMOIRE_ITEM_ID = SuperpowerManager.DRUID_GRIMOIRE_ITEM_ID;
    private static final String DOMINION_CORE_ITEM_ID = BossManager.DOMINION_CORE_ITEM_ID;
    private static final Set<String> ABSOLUTE_OWNER_ACCOUNTS = Set.of("riqqqque");
    private static final List<String> CUSTOM_ITEM_IDS = List.of(
        BACKPACK_ITEM_ID,
        AWAKENING_TABLE_ITEM_ID,
        ENDER_BONE_ITEM_ID,
        ORB_OF_THE_MYSTICS_ITEM_ID,
        FARADAYS_MAGNET_ITEM_ID,
        MYTHIC_FORGE_ITEM_ID,
        ASCENDANT_CORE_ITEM_ID,
        TALISMAN_OF_SUSTENANCE_ITEM_ID,
        ANCIENT_SCROLL_ITEM_ID,
        WARDEN_HEART_ITEM_ID,
        MOTHER_NATURE_STICK_ITEM_ID,
        THE_WORLD_CLOCK_ITEM_ID,
        DRUID_GRIMOIRE_ITEM_ID,
        DOMINION_CORE_ITEM_ID,
        CustomToolListener.ADVANCED_PICKAXE_ID,
        CustomToolListener.GRAPPLE_HOOK_ID
    );

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
        registerAnnounce(commands);
        registerUnban(commands);
        registerReplenishBook(commands, plugin);
        registerDelicateBook(commands, plugin);
        registerTelekinesisBook(commands, plugin);
        registerSmeltingTouchBook(commands, plugin);
        registerWiseBook(commands, plugin);
        registerDoubleJumpBook(commands, plugin);
        registerDashBook(commands, plugin);
        registerSetPower(commands, plugin);
        registerPowerInfo(commands, plugin);
        registerAdminRewardCommand(commands, plugin);
        registerAbsoluteOwnerTeamVaultView(commands, plugin);
        registerAbsoluteOwnerCommands(commands, plugin);
        registerCustomItemCommand(commands, plugin);
        registerItemAudit(commands, plugin);
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
                    toggleFly(plugin, self, self);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("target", ArgumentTypes.player())
                    .requires(src -> src.getSender().hasPermission("smpcore.fly.others"))
                    .executes(ctx -> {
                        List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                            .resolve(ctx.getSource());
                        if (targets.isEmpty()) { ctx.getSource().getSender().sendMessage(MessageUtil.error("Player not found.")); return 0; }
                        toggleFly(plugin, targets.get(0), ctx.getSource().getSender() instanceof Player p ? p : null);
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "Toggle flight mode"
        );
    }

    private static void toggleFly(SMPCore plugin, Player target, Player sender) {
        boolean flying = plugin.getPlayerManager().toggleFlight(target);
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

    private static void registerAnnounce(Commands commands) {
        commands.register(
            Commands.literal("announce")
                .requires(src -> src.getSender().hasPermission("smpcore.announce"))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        String message = StringArgumentType.getString(ctx, "message").trim();
                        if (message.isEmpty()) {
                            ctx.getSource().getSender().sendMessage(MessageUtil.error("Usage: /announce <message>"));
                            return 0;
                        }

                        Bukkit.broadcast(MessageUtil.prefixedRaw(
                            "<white><message></white>",
                            MessageUtil.placeholder("message", message)
                        ));
                        ctx.getSource().getSender().sendMessage(MessageUtil.success("Announcement sent."));
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "Broadcast an announcement to the whole server",
            List.of("broadcast")
        );
    }

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

    private static void registerUnban(Commands commands) {
        commands.register(
            Commands.literal("unban")
                .requires(src -> src.getSender().hasPermission("smpcore.unban"))
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestBannedProfiles(builder))
                    .executes(ctx -> executeUnban(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"))))
                .build(),
            "Unban a player profile",
            List.of("pardon")
        );
    }

    private static void registerAbsoluteOwnerTeamVaultView(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("viewteamvault")
                .requires(src -> src.getSender() instanceof Player player && hasAbsoluteOwnerRights(player))
                .then(Commands.argument("team", StringArgumentType.greedyString())
                    .suggests((ctx, builder) -> suggestTeamNames(plugin, builder))
                    .executes(ctx -> {
                        Player viewer = (Player) ctx.getSource().getSender();
                        plugin.getTeamManager().openTeamVaultInspector(
                            viewer,
                            StringArgumentType.getString(ctx, "team")
                        );
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "View a team vault without editing it",
            List.of("teamvaultsee", "tvaultsee")
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

    private static void registerDelicateBook(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("delicatebook")
                .requires(src -> src.getSender().hasPermission("smpcore.customenchant.admin"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player self)) {
                        ctx.getSource().getSender().sendMessage(MessageUtil.error("Console must specify a target."));
                        return 0;
                    }
                    return giveManagedEnchantBook(plugin, ctx.getSource().getSender(), self, "Delicate", ManagedEnchantBook.DELICATE, 1);
                })
                .then(Commands.argument("target", ArgumentTypes.player())
                    .executes(ctx -> {
                        List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                            .resolve(ctx.getSource());
                        if (targets.isEmpty()) {
                            ctx.getSource().getSender().sendMessage(MessageUtil.error("Player not found."));
                            return 0;
                        }
                        return giveManagedEnchantBook(
                            plugin,
                            ctx.getSource().getSender(),
                            targets.get(0),
                            "Delicate",
                            ManagedEnchantBook.DELICATE,
                            1
                        );
                    }))
                .build(),
            "Give a Delicate enchant book"
        );
    }

    private static void registerTelekinesisBook(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("telekinesisbook")
                .requires(src -> src.getSender().hasPermission("smpcore.customenchant.admin"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player self)) {
                        ctx.getSource().getSender().sendMessage(MessageUtil.error("Console must specify a target."));
                        return 0;
                    }
                    return giveManagedEnchantBook(
                        plugin,
                        ctx.getSource().getSender(),
                        self,
                        "Telekinesis",
                        ManagedEnchantBook.TELEKINESIS,
                        1
                    );
                })
                .then(Commands.argument("target", ArgumentTypes.player())
                    .executes(ctx -> {
                        List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                            .resolve(ctx.getSource());
                        if (targets.isEmpty()) {
                            ctx.getSource().getSender().sendMessage(MessageUtil.error("Player not found."));
                            return 0;
                        }
                        return giveManagedEnchantBook(
                            plugin,
                            ctx.getSource().getSender(),
                            targets.get(0),
                            "Telekinesis",
                            ManagedEnchantBook.TELEKINESIS,
                            1
                        );
                    }))
                .build(),
            "Give a Telekinesis enchant book",
            List.of("telekenesisbook")
        );
    }

    private static void registerSmeltingTouchBook(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("smeltingtouchbook")
                .requires(src -> src.getSender().hasPermission("smpcore.customenchant.admin"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player self)) {
                        ctx.getSource().getSender().sendMessage(MessageUtil.error("Console must specify a target."));
                        return 0;
                    }
                    return giveManagedEnchantBook(plugin, ctx.getSource().getSender(), self, "Smelting Touch", ManagedEnchantBook.SMELTING_TOUCH, 1);
                })
                .then(Commands.argument("target", ArgumentTypes.player())
                    .executes(ctx -> {
                        List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                            .resolve(ctx.getSource());
                        if (targets.isEmpty()) {
                            ctx.getSource().getSender().sendMessage(MessageUtil.error("Player not found."));
                            return 0;
                        }
                        return giveManagedEnchantBook(
                            plugin,
                            ctx.getSource().getSender(),
                            targets.get(0),
                            "Smelting Touch",
                            ManagedEnchantBook.SMELTING_TOUCH,
                            1
                        );
                    }))
                .build(),
            "Give a Smelting Touch enchant book",
            List.of("smeltingbook")
        );
    }

    private static void registerWiseBook(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("wisebook")
                .requires(src -> src.getSender().hasPermission("smpcore.customenchant.admin"))
                .then(Commands.argument("level", IntegerArgumentType.integer(1, 3))
                    .executes(ctx -> {
                        if (!(ctx.getSource().getSender() instanceof Player self)) {
                            ctx.getSource().getSender().sendMessage(MessageUtil.error("Console must specify a target."));
                            return 0;
                        }
                        int level = IntegerArgumentType.getInteger(ctx, "level");
                        return giveManagedEnchantBook(
                            plugin,
                            ctx.getSource().getSender(),
                            self,
                            "Wise " + romanNumeral(level),
                            ManagedEnchantBook.WISE,
                            level
                        );
                    })
                    .then(Commands.argument("target", ArgumentTypes.player())
                        .executes(ctx -> {
                            List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                                .resolve(ctx.getSource());
                            if (targets.isEmpty()) {
                                ctx.getSource().getSender().sendMessage(MessageUtil.error("Player not found."));
                                return 0;
                            }
                            int level = IntegerArgumentType.getInteger(ctx, "level");
                            return giveManagedEnchantBook(
                                plugin,
                                ctx.getSource().getSender(),
                                targets.get(0),
                                "Wise " + romanNumeral(level),
                                ManagedEnchantBook.WISE,
                                level
                            );
                        })))
                .build(),
            "Give a Wise enchant book"
        );
    }

    private static void registerDoubleJumpBook(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("doublejumpbook")
                .requires(src -> src.getSender().hasPermission("smpcore.customenchant.admin"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player self)) {
                        ctx.getSource().getSender().sendMessage(MessageUtil.error("Console must specify a target."));
                        return 0;
                    }
                    return giveManagedEnchantBook(
                        plugin,
                        ctx.getSource().getSender(),
                        self,
                        "Double Jump",
                        ManagedEnchantBook.DOUBLE_JUMP,
                        1
                    );
                })
                .then(Commands.argument("target", ArgumentTypes.player())
                    .executes(ctx -> {
                        List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                            .resolve(ctx.getSource());
                        if (targets.isEmpty()) {
                            ctx.getSource().getSender().sendMessage(MessageUtil.error("Player not found."));
                            return 0;
                        }
                        return giveManagedEnchantBook(
                            plugin,
                            ctx.getSource().getSender(),
                            targets.get(0),
                            "Double Jump",
                            ManagedEnchantBook.DOUBLE_JUMP,
                            1
                        );
                    }))
                .build(),
            "Give a Double Jump enchant book",
            List.of("djbook")
        );
    }

    private static void registerDashBook(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("dashbook")
                .requires(src -> src.getSender().hasPermission("smpcore.customenchant.admin"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player self)) {
                        ctx.getSource().getSender().sendMessage(MessageUtil.error("Console must specify a target."));
                        return 0;
                    }
                    return giveManagedEnchantBook(
                        plugin,
                        ctx.getSource().getSender(),
                        self,
                        "Dash",
                        ManagedEnchantBook.DASH,
                        1
                    );
                })
                .then(Commands.argument("target", ArgumentTypes.player())
                    .executes(ctx -> {
                        List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                            .resolve(ctx.getSource());
                        if (targets.isEmpty()) {
                            ctx.getSource().getSender().sendMessage(MessageUtil.error("Player not found."));
                            return 0;
                        }
                        return giveManagedEnchantBook(
                            plugin,
                            ctx.getSource().getSender(),
                            targets.get(0),
                            "Dash",
                            ManagedEnchantBook.DASH,
                            1
                        );
                    }))
                .build(),
            "Give a Dash enchant book"
        );
    }

    private static void registerPowerInfo(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("powerinfo")
                .requires(src -> src.getSender() instanceof Player)
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    SuperpowerManager powers = plugin.getSuperpowerManager();
                    if (powers == null) {
                        player.sendMessage(MessageUtil.error("Power system is not ready yet."));
                        return 0;
                    }
                    powers.openAdminInfoMenu(player);
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Open the superpower info menu",
            List.of("classinfo", "powermenu")
        );
    }

    private static void registerAdminRewardCommand(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("admin")
                .requires(src -> src.getSender().hasPermission("smpcore.reward.admin"))
                .then(Commands.literal("reward")
                    .then(Commands.argument("target", ArgumentTypes.player())
                        .executes(ctx -> {
                            List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                                .resolve(ctx.getSource());
                            if (targets.isEmpty()) {
                                ctx.getSource().getSender().sendMessage(MessageUtil.error("Player not found."));
                                return 0;
                            }
                            return giveRewardLantern(plugin, ctx.getSource().getSender(), targets.get(0));
                        }))
                    .then(Commands.literal("revoke")
                        .then(Commands.argument("target", ArgumentTypes.player())
                            .executes(ctx -> {
                                List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                                    .resolve(ctx.getSource());
                                if (targets.isEmpty()) {
                                    ctx.getSource().getSender().sendMessage(MessageUtil.error("Player not found."));
                                    return 0;
                                }
                                return revokeRewardLantern(plugin, ctx.getSource().getSender(), targets.get(0));
                            })))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("target", ArgumentTypes.player())
                            .executes(ctx -> {
                                List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                                    .resolve(ctx.getSource());
                                if (targets.isEmpty()) {
                                    ctx.getSource().getSender().sendMessage(MessageUtil.error("Player not found."));
                                    return 0;
                                }
                                return revokeRewardLantern(plugin, ctx.getSource().getSender(), targets.get(0));
                            }))))
                .build(),
            "Grant or revoke the reward lantern"
        );
    }

    private static void registerAbsoluteOwnerCommands(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("forceunvanish")
                .requires(src -> hasAbsoluteOwnerRights(src.getSender()))
                .then(Commands.argument("target", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestOnlinePlayers(builder))
                    .executes(ctx -> forceUnvanish(
                        plugin,
                        ctx.getSource().getSender(),
                        StringArgumentType.getString(ctx, "target")
                    )))
                .build(),
            "Force another player out of vanish",
            List.of("adminunvanish", "revealplayer")
        );
    }

    private static void registerSetPower(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("setpower")
                .requires(src -> src.getSender().hasPermission("smpcore.superpower.assign"))
                .then(Commands.argument("target", ArgumentTypes.player())
                    .then(Commands.argument("power", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestSuperpowerTypes(builder))
                        .executes(ctx -> {
                            List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                                .resolve(ctx.getSource());
                            if (targets.isEmpty()) {
                                ctx.getSource().getSender().sendMessage(MessageUtil.error("Player not found."));
                                return 0;
                            }

                            String rawPower = StringArgumentType.getString(ctx, "power");
                            return setSuperpower(plugin, ctx.getSource().getSender(), targets.get(0), rawPower);
                        })))
                .build(),
            "Assign a superpower to a player",
            List.of("powerset")
        );
    }

    private static int giveManagedEnchantBook(
        SMPCore plugin,
        org.bukkit.command.CommandSender sender,
        Player target,
        String displayName,
        ManagedEnchantBook bookType,
        int level
    ) {
        CustomEnchantListener listener = plugin.getCustomEnchantListener();
        if (listener == null) {
            sender.sendMessage(MessageUtil.error("Custom enchant system is not ready yet."));
            return 0;
        }

        ItemStack book = switch (bookType) {
            case DELICATE -> listener.createDelicateBook();
            case TELEKINESIS -> listener.createTelekinesisBook();
            case SMELTING_TOUCH -> listener.createSmeltingTouchBook();
            case WISE -> listener.createWiseBook(level);
            case DOUBLE_JUMP -> listener.createDoubleJumpBook();
            case DASH -> listener.createDashBook();
        };
        var leftovers = target.getInventory().addItem(book);
        leftovers.values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));

        target.sendMessage(MessageUtil.success("You received a <white>" + displayName + " Book</white>."));
        if (!sender.equals(target)) {
            sender.sendMessage(MessageUtil.success(
                "Gave a <white>" + displayName + " Book</white> to <white>" + target.getName() + "</white>."
            ));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int giveRewardLantern(SMPCore plugin, CommandSender sender, Player target) {
        RewardLanternListener rewardLanterns = plugin.getRewardLanternListener();
        if (rewardLanterns == null) {
            sender.sendMessage(MessageUtil.error("Reward lantern system is not ready yet."));
            return 0;
        }

        rewardLanterns.grantRewardAccess(target);
        ItemStack lantern = rewardLanterns.createRewardLantern(target);
        return giveItem(plugin, sender, target, lantern, "Reward Soul Lantern");
    }

    private static int revokeRewardLantern(SMPCore plugin, CommandSender sender, Player target) {
        RewardLanternListener rewardLanterns = plugin.getRewardLanternListener();
        if (rewardLanterns == null) {
            sender.sendMessage(MessageUtil.error("Reward lantern system is not ready yet."));
            return 0;
        }

        rewardLanterns.revokeRewardAccess(target);
        target.sendMessage(MessageUtil.warn("Your Reward Soul Lantern access has been removed."));
        if (!sender.equals(target)) {
            sender.sendMessage(MessageUtil.success(
                "Removed Reward Soul Lantern access from <white>" + target.getName() + "</white>."
            ));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int forceUnvanish(SMPCore plugin, CommandSender sender, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            target = Bukkit.getPlayer(targetName);
        }
        if (target == null) {
            sender.sendMessage(MessageUtil.error("Player not found."));
            return 0;
        }

        if (!plugin.getPlayerManager().isVanished(target.getUniqueId())) {
            sender.sendMessage(MessageUtil.warn("<white>" + target.getName() + "</white> is already visible."));
            return 0;
        }

        plugin.getPlayerManager().setVanished(target, false);
        target.sendMessage(MessageUtil.warn("Your vanish was forcibly disabled."));
        sender.sendMessage(MessageUtil.success(
            "Forced <white>" + target.getName() + "</white> out of vanish."
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static void registerCustomItemCommand(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("customitem")
                .requires(src -> src.getSender().hasPermission("smpcore.customitem.admin"))
                .then(Commands.literal("give")
                    .then(Commands.argument("item", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestCustomItemIds(builder))
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player self)) {
                                ctx.getSource().getSender().sendMessage(MessageUtil.error("Console must specify a target."));
                                return 0;
                            }
                            String itemId = StringArgumentType.getString(ctx, "item");
                            return giveCustomItem(plugin, ctx.getSource().getSender(), self, itemId);
                        })
                        .then(Commands.argument("target", ArgumentTypes.player())
                            .executes(ctx -> {
                                List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                                    .resolve(ctx.getSource());
                                if (targets.isEmpty()) {
                                    ctx.getSource().getSender().sendMessage(MessageUtil.error("Player not found."));
                                    return 0;
                                }
                                String itemId = StringArgumentType.getString(ctx, "item");
                                return giveCustomItem(plugin, ctx.getSource().getSender(), targets.get(0), itemId);
                            }))))
                .build(),
            "Give non-legendary custom items",
            List.of("citem")
        );
    }

    private static int giveCustomItem(SMPCore plugin, CommandSender sender, Player target, String requestedId) {
        String itemId = normalizeCustomItemId(requestedId);
        AdminGiveItem item = switch (itemId) {
            case BACKPACK_ITEM_ID -> createBackpackAdminItem(plugin, sender);
            case AWAKENING_TABLE_ITEM_ID -> createAwakeningTableAdminItem(plugin, sender);
            case ENDER_BONE_ITEM_ID -> createEnderBoneAdminItem(plugin, sender);
            case ORB_OF_THE_MYSTICS_ITEM_ID -> createOrbOfTheMysticsAdminItem(plugin, sender);
            case FARADAYS_MAGNET_ITEM_ID -> createFaradaysMagnetAdminItem(plugin, sender);
            case MYTHIC_FORGE_ITEM_ID -> createMythicForgeAdminItem(plugin, sender);
            case ASCENDANT_CORE_ITEM_ID -> createAscendantCoreAdminItem(plugin, sender);
            case TALISMAN_OF_SUSTENANCE_ITEM_ID -> createTalismanOfSustenanceAdminItem(plugin, sender);
            case ANCIENT_SCROLL_ITEM_ID -> createAncientScrollAdminItem(plugin, sender);
            case WARDEN_HEART_ITEM_ID -> createWardenHeartAdminItem(plugin, sender);
            case MOTHER_NATURE_STICK_ITEM_ID -> createMotherNatureStickAdminItem(plugin, sender);
            case THE_WORLD_CLOCK_ITEM_ID -> createTheWorldClockAdminItem(plugin, sender);
            case DRUID_GRIMOIRE_ITEM_ID -> createDruidGrimoireAdminItem(plugin, sender);
            case DOMINION_CORE_ITEM_ID -> createDominionCoreAdminItem(plugin, sender);
            case CustomToolListener.ADVANCED_PICKAXE_ID,
                 CustomToolListener.GRAPPLE_HOOK_ID ->
                createCustomToolAdminItem(plugin, sender, itemId);
            default -> null;
        };
        if (item == null) {
            if (itemId == null || !CUSTOM_ITEM_IDS.contains(itemId)) {
                sender.sendMessage(MessageUtil.error(
                    "Unknown custom item. Options: <white>" + String.join(", ", customItemCommandOptions()) + "</white>."
                ));
            }
            return 0;
        }
        return giveItem(plugin, sender, target, item.item(), item.displayName());
    }

    private static int setSuperpower(SMPCore plugin, CommandSender sender, Player target, String rawPower) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            sender.sendMessage(MessageUtil.error("Power system is not ready yet."));
            return 0;
        }

        SuperpowerType power = normalizeSuperpowerType(rawPower);
        if (power == null) {
            sender.sendMessage(MessageUtil.error(
                "Unknown power. Options: <white>" + String.join(", ", powerSuggestionValues()) + "</white>."
            ));
            return 0;
        }

        powers.assignPower(target, power, true);
        sender.sendMessage(MessageUtil.success(
            "Assigned <white>" + power.displayName() + "</white> to <white>" + target.getName() + "</white>."
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static AdminGiveItem createBackpackAdminItem(SMPCore plugin, CommandSender sender) {
        BackpackListener backpacks = plugin.getBackpackListener();
        if (backpacks == null) {
            sender.sendMessage(MessageUtil.error("Backpack system is not ready yet."));
            return null;
        }
        return new AdminGiveItem(backpacks.createNewBackpack(), "Backpack");
    }

    private static AdminGiveItem createAwakeningTableAdminItem(SMPCore plugin, CommandSender sender) {
        AwakeningTableListener awakening = plugin.getAwakeningTableListener();
        if (awakening == null) {
            sender.sendMessage(MessageUtil.error("Awakening system is not ready yet."));
            return null;
        }
        return new AdminGiveItem(awakening.createAwakeningTableItem(), "Awakening Table");
    }

    private static AdminGiveItem createEnderBoneAdminItem(SMPCore plugin, CommandSender sender) {
        LegendaryListener legendary = plugin.getLegendaryListener();
        if (legendary == null) {
            sender.sendMessage(MessageUtil.error("Legendary system is not ready yet."));
            return null;
        }
        return new AdminGiveItem(legendary.createEnderBoneItem(), "Ender Bone");
    }

    private static AdminGiveItem createOrbOfTheMysticsAdminItem(SMPCore plugin, CommandSender sender) {
        LegendaryListener legendary = plugin.getLegendaryListener();
        if (legendary == null) {
            sender.sendMessage(MessageUtil.error("Legendary system is not ready yet."));
            return null;
        }
        return new AdminGiveItem(legendary.createOrbOfTheMysticsItem(), "Orb of the Mystics");
    }

    private static AdminGiveItem createFaradaysMagnetAdminItem(SMPCore plugin, CommandSender sender) {
        LegendaryListener legendary = plugin.getLegendaryListener();
        if (legendary == null) {
            sender.sendMessage(MessageUtil.error("Legendary system is not ready yet."));
            return null;
        }
        return new AdminGiveItem(legendary.createFaradaysMagnetItem(), "Faraday's Magnet");
    }

    private static AdminGiveItem createMythicForgeAdminItem(SMPCore plugin, CommandSender sender) {
        MythicForgeListener forge = plugin.getMythicForgeListener();
        if (forge == null) {
            sender.sendMessage(MessageUtil.error("Mythic Forge system is not ready yet."));
            return null;
        }
        return new AdminGiveItem(forge.createMythicForgeItem(), "Mythic Forge");
    }

    private static AdminGiveItem createAscendantCoreAdminItem(SMPCore plugin, CommandSender sender) {
        MythicForgeListener forge = plugin.getMythicForgeListener();
        if (forge == null) {
            sender.sendMessage(MessageUtil.error("Mythic Forge system is not ready yet."));
            return null;
        }
        return new AdminGiveItem(forge.createAscendantCoreItem(), "Ascendant Core");
    }

    private static AdminGiveItem createTalismanOfSustenanceAdminItem(SMPCore plugin, CommandSender sender) {
        SustenanceTalismanListener talisman = plugin.getSustenanceTalismanListener();
        if (talisman == null) {
            sender.sendMessage(MessageUtil.error("Talisman system is not ready yet."));
            return null;
        }
        return new AdminGiveItem(talisman.createTalismanItem(), "Talisman of Sustenance");
    }

    private static AdminGiveItem createAncientScrollAdminItem(SMPCore plugin, CommandSender sender) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            sender.sendMessage(MessageUtil.error("Power system is not ready yet."));
            return null;
        }
        return new AdminGiveItem(powers.createAncientScrollItem(), "Ancient Scroll");
    }

    private static AdminGiveItem createWardenHeartAdminItem(SMPCore plugin, CommandSender sender) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            sender.sendMessage(MessageUtil.error("Power system is not ready yet."));
            return null;
        }
        return new AdminGiveItem(powers.createWardenHeartItem(), "Warden Heart");
    }

    private static AdminGiveItem createMotherNatureStickAdminItem(SMPCore plugin, CommandSender sender) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            sender.sendMessage(MessageUtil.error("Power system is not ready yet."));
            return null;
        }
        return new AdminGiveItem(powers.createMotherNatureStickItem(), "Stick from Mother Nature");
    }

    private static AdminGiveItem createTheWorldClockAdminItem(SMPCore plugin, CommandSender sender) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            sender.sendMessage(MessageUtil.error("Power system is not ready yet."));
            return null;
        }
        return new AdminGiveItem(powers.createTheWorldClockItem(), "World Clock");
    }

    private static AdminGiveItem createDruidGrimoireAdminItem(SMPCore plugin, CommandSender sender) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            sender.sendMessage(MessageUtil.error("Power system is not ready yet."));
            return null;
        }
        return new AdminGiveItem(powers.createDruidGrimoireItem(), "Druid's Grimoire");
    }

    private static AdminGiveItem createDominionCoreAdminItem(SMPCore plugin, CommandSender sender) {
        BossManager bossManager = plugin.getBossManager();
        if (bossManager == null) {
            sender.sendMessage(MessageUtil.error("Boss system is not ready yet."));
            return null;
        }
        return new AdminGiveItem(bossManager.createDominionCoreItem(), "Dominion Core");
    }

    private static AdminGiveItem createCustomToolAdminItem(SMPCore plugin, CommandSender sender, String itemId) {
        CustomToolListener tools = plugin.getCustomToolListener();
        if (tools == null) {
            sender.sendMessage(MessageUtil.error("Custom tool system is not ready yet."));
            return null;
        }
        String displayName = tools.displayNameFor(itemId);
        return new AdminGiveItem(
            tools.createCustomTool(itemId),
            displayName == null ? prettyCustomItemName(itemId) : displayName
        );
    }

    private static int giveItem(SMPCore plugin, CommandSender sender, Player target, ItemStack item, String displayName) {
        ItemAuditManager audit = plugin.getItemAuditManager();
        if (audit != null) {
            audit.recordKnownAcquisition(target, item, sender, "admin_give", "Given via admin command.");
        }
        var leftovers = target.getInventory().addItem(item);
        leftovers.values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));

        target.sendMessage(MessageUtil.success("You received <white>" + displayName + "</white>."));
        if (!sender.equals(target)) {
            sender.sendMessage(MessageUtil.success(
                "Gave <white>" + displayName + "</white> to <white>" + target.getName() + "</white>."
            ));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void registerItemAudit(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("itemaudit")
                .requires(src -> src.getSender().hasPermission("smpcore.itemaudit.admin"))
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestOnlinePlayers(builder))
                    .executes(ctx -> {
                        String rawName = StringArgumentType.getString(ctx, "player");
                        return executeItemAudit(plugin, ctx.getSource().getSender(), rawName, null);
                    })
                    .then(Commands.argument("item", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestAuditItems(plugin, builder))
                        .executes(ctx -> {
                            String rawName = StringArgumentType.getString(ctx, "player");
                            String itemKey = StringArgumentType.getString(ctx, "item");
                            return executeItemAudit(plugin, ctx.getSource().getSender(), rawName, itemKey);
                        })))
                .build(),
            "View tracked custom item audit logs",
            List.of("itemtrace", "audititem")
        );
    }

    private static int executeItemAudit(SMPCore plugin, CommandSender sender, String rawName, String itemKey) {
        ItemAuditManager audit = plugin.getItemAuditManager();
        if (audit == null) {
            sender.sendMessage(MessageUtil.error("Item audit system is not ready yet."));
            return 0;
        }
        OfflinePlayer target = resolveOfflinePlayer(rawName);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(MessageUtil.error("Player not found."));
            return 0;
        }
        audit.sendAuditLog(sender, target, itemKey);
        return Command.SINGLE_SUCCESS;
    }

    private static OfflinePlayer resolveOfflinePlayer(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(rawName);
        if (online != null) {
            return online;
        }
        online = Bukkit.getPlayer(rawName);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayer(rawName);
    }

    private static int executeUnban(CommandSender sender, String rawName) {
        String targetName = rawName == null ? "" : rawName.trim();
        if (targetName.isEmpty()) {
            sender.sendMessage(MessageUtil.error("Enter a player name to unban."));
            return 0;
        }

        ProfileBanList banList = Bukkit.getServer().getBanList(BanListType.PROFILE);
        PlayerProfile bannedProfile = findBannedProfile(banList, targetName);
        if (bannedProfile == null) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
            if (offline.isBanned()) {
                bannedProfile = offline.getPlayerProfile();
            }
        }

        if (bannedProfile == null) {
            sender.sendMessage(MessageUtil.error("No active profile ban was found for <white>" + targetName + "</white>."));
            return 0;
        }

        String displayName = bannedProfile.getName() == null || bannedProfile.getName().isBlank()
            ? targetName
            : bannedProfile.getName();
        banList.pardon(bannedProfile);
        sender.sendMessage(MessageUtil.success("Unbanned <white>" + displayName + "</white>."));
        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> suggestCustomItemIds(SuggestionsBuilder builder) {
        for (String option : customItemCommandOptions()) {
            builder.suggest(option);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestOnlinePlayers(SuggestionsBuilder builder) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            builder.suggest(player.getName());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestAuditItems(SMPCore plugin, SuggestionsBuilder builder) {
        ItemAuditManager audit = plugin.getItemAuditManager();
        if (audit != null) {
            for (String option : audit.itemAuditOptions()) {
                builder.suggest(option);
            }
        }
        return builder.buildFuture();
    }

    private static Set<String> customItemCommandOptions() {
        LinkedHashSet<String> options = new LinkedHashSet<>();
        for (String id : CUSTOM_ITEM_IDS) {
            String displayToken = switch (id) {
                case CustomToolListener.ADVANCED_PICKAXE_ID -> "prospectors_pick";
                case CustomToolListener.GRAPPLE_HOOK_ID -> "skyhook";
                default -> id;
            };
            options.add(displayToken);
            options.add(id);
        }
        return options;
    }

    private static CompletableFuture<Suggestions> suggestBannedProfiles(SuggestionsBuilder builder) {
        ProfileBanList banList = Bukkit.getServer().getBanList(BanListType.PROFILE);
        for (var entry : banList.getEntries()) {
            Object target = entry.getBanTarget();
            if (target instanceof PlayerProfile profile) {
                String name = profile.getName();
                if (name != null && !name.isBlank()) {
                    builder.suggest(name);
                }
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestTeamNames(SMPCore plugin, SuggestionsBuilder builder) {
        if (plugin.getTeamManager() != null) {
            for (String name : plugin.getTeamManager().teamNames()) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestSuperpowerTypes(SuggestionsBuilder builder) {
        for (String power : powerSuggestionValues()) {
            builder.suggest(power);
        }
        return builder.buildFuture();
    }

    private static PlayerProfile findBannedProfile(ProfileBanList banList, String input) {
        for (var entry : banList.getEntries()) {
            Object target = entry.getBanTarget();
            if (target instanceof PlayerProfile profile) {
                String name = profile.getName();
                if (name != null && name.equalsIgnoreCase(input)) {
                    return profile;
                }
            }
        }
        return null;
    }

    private static String normalizeCustomItemId(String input) {
        if (input == null) return null;
        String normalized = input.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.isBlank()) return null;
        return switch (normalized) {
            case "advancedpickaxe", "advanced_pick", "prospector", "prospectorspick", "prospectors_pick", "prospectorspickaxe", "prospectors_pickaxe" -> CustomToolListener.ADVANCED_PICKAXE_ID;
            case "ancientscroll", "ancient_scroll", "scroll" -> ANCIENT_SCROLL_ITEM_ID;
            case "awakening", "awakeningtable", "awakening_table", "awaken_table", "table" -> AWAKENING_TABLE_ITEM_ID;
            case "ascendant", "ascendantcore", "ascendant_core", "core" -> ASCENDANT_CORE_ITEM_ID;
            case "druid", "druidbook", "druid_book", "druidgrimoire", "druid_grimoire", "grimoire" -> DRUID_GRIMOIRE_ITEM_ID;
            case "dominioncore", "dominion_core", "coreofdominion", "repaircore", "repair_core" -> DOMINION_CORE_ITEM_ID;
            case "enderbone", "bone" -> ENDER_BONE_ITEM_ID;
            case "magnet", "faraday", "faradays", "faradaysmagnet", "faradays_magnet" -> FARADAYS_MAGNET_ITEM_ID;
            case "grapple", "grapplehook", "grapple_hook", "hook", "skyhook" -> CustomToolListener.GRAPPLE_HOOK_ID;
            case "mythicforge", "mythic_forge", "forge" -> MYTHIC_FORGE_ITEM_ID;
            case "mothernature", "mother_nature", "mothernaturestick", "mother_nature_stick", "naturestick" -> MOTHER_NATURE_STICK_ITEM_ID;
            case "orb", "mystic_orb", "orb_of_mystics", "orb_of_the_mystic", "orbofthemystics" -> ORB_OF_THE_MYSTICS_ITEM_ID;
            case "talisman", "sustenance_talisman", "talisman_of_sustenance" -> TALISMAN_OF_SUSTENANCE_ITEM_ID;
            case "theworld", "the_world", "worldclock", "world_clock", "clock" -> THE_WORLD_CLOCK_ITEM_ID;
            case "wardenheart", "warden_heart" -> WARDEN_HEART_ITEM_ID;
            default -> normalized;
        };
    }

    private static SuperpowerType normalizeSuperpowerType(String input) {
        if (input == null) {
            return null;
        }

        String normalized = input.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.isBlank()) {
            return null;
        }

        return switch (normalized) {
            case "no_power", "no_powers", "none", "normal" -> SuperpowerType.HUMAN;
            default -> SuperpowerType.fromId(normalized);
        };
    }

    private static List<String> powerSuggestionValues() {
        List<String> values = new java.util.ArrayList<>();
        for (SuperpowerType type : SuperpowerType.values()) {
            values.add(type.name().toLowerCase(Locale.ROOT));
        }
        values.add("no_powers");
        return values;
    }

    private static String prettyCustomItemName(String itemId) {
        if (itemId == null || itemId.isBlank()) return "Unknown";
        String[] parts = itemId.split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            out.append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private static boolean hasAbsoluteOwnerRights(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        return ABSOLUTE_OWNER_ACCOUNTS.contains(player.getName().toLowerCase(Locale.ROOT));
    }

    private static String romanNumeral(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(value);
        };
    }

    private record AdminGiveItem(ItemStack item, String displayName) {}

    private enum ManagedEnchantBook {
        DELICATE,
        TELEKINESIS,
        SMELTING_TOUCH,
        WISE,
        DOUBLE_JUMP,
        DASH
    }
}
