package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("UnstableApiUsage")
public final class MainMenuCommand implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final int MAIN_MENU_SIZE = 36;
    private static final int SECTION_MENU_SIZE = 27;
    private static final int STATS_MENU_SIZE = 54;
    private static final String ACTION_PROFILE_MENU = "profile_menu";
    private static final String ACTION_GEAR_MENU = "gear_menu";
    private static final String ACTION_CHALLENGES_MENU = "challenges_menu";
    private static final String ACTION_COMMUNITY_MENU = "community_menu";
    private static final String ACTION_CROSSPLAY = "crossplay";
    private static final String ACTION_STATS = "stats";
    private static final String ACTION_CUSTOM_STATS = "custom_stats";
    private static final String ACTION_CUSTOM_STATS_REFRESH = "custom_stats_refresh";
    private static final String ACTION_SETTINGS = "settings";
    private static final String ACTION_RELIQUARY = "reliquary";
    private static final String ACTION_ARMORY = "armory";
    private static final String ACTION_MYTHICS = "mythics";
    private static final String ACTION_BOSSES = "bosses";
    private static final String ACTION_ENCHANTS = "enchants";
    private static final String ACTION_BREWS = "brews";
    private static final String ACTION_POWERS = "powers";
    private static final String ACTION_TEAMS = "teams";
    private static final String ACTION_LEADERBOARDS = "leaderboards";
    private static final String ACTION_FAMILIARS = "familiars";
    private static final String ACTION_CHANGELOG = "changelog";
    private static final String ACTION_WIKI = "wiki";
    private static final String ACTION_BACK = "back";
    private static final String ACTION_REFRESH = "refresh";
    private static final String ACTION_CLOSE = "close";

    private final SMPCore plugin;
    private final NamespacedKey menuActionKey;

    public MainMenuCommand(SMPCore plugin) {
        this.plugin = plugin;
        this.menuActionKey = actionKey(plugin);
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("menu")
                .requires(src -> src.getSender() instanceof Player player && player.hasPermission("smpcore.menu"))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    openMenu(plugin, player);
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Open the main SMPCore menu",
            List.of("smpmenu")
        );
        commands.register(
            Commands.literal("settings")
                .requires(src -> src.getSender() instanceof Player player && player.hasPermission("smpcore.settings"))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    if (plugin.getPlayerSettingsManager() == null) {
                        player.sendMessage(MessageUtil.error("Player settings are not ready yet."));
                        return Command.SINGLE_SUCCESS;
                    }
                    plugin.getPlayerSettingsManager().openSettingsMenu(player, false);
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Open personal player settings",
            List.of("playersettings", "prefs")
        );
    }

    public static void openMenu(SMPCore plugin, Player player) {
        NamespacedKey actionKey = actionKey(plugin);
        Inventory inventory = Bukkit.createInventory(
            new MainMenuHolder(),
            MAIN_MENU_SIZE,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#7c3aed:#22d3ee><bold>Veil Menu</bold></gradient>"), "Menu")
        );

        decorateFrame(inventory);
        inventory.setItem(4, playerOverviewHead(plugin, player, actionKey, ACTION_STATS));
        inventory.setItem(10, sectionButton(Material.COMPASS, "<aqua><bold>Profile</bold></aqua>", List.of(
            "<gray>Stats, class, and settings.</gray>"
        ), actionKey, ACTION_PROFILE_MENU));
        inventory.setItem(12, sectionButton(Material.NETHERITE_CHESTPLATE, "<gold><bold>Gear & Recipes</bold></gold>", List.of(
            "<gray>Reliquary, Armory, and enchants.</gray>"
        ), actionKey, ACTION_GEAR_MENU));
        inventory.setItem(14, sectionButton(Material.WITHER_SKELETON_SKULL, "<red><bold>Bosses & Mythics</bold></red>", List.of(
            "<gray>Rituals, fusions, and boss brews.</gray>"
        ), actionKey, ACTION_CHALLENGES_MENU));
        inventory.setItem(16, sectionButton(Material.SHIELD, "<green><bold>Community</bold></green>", List.of(
            "<gray>Teams, rankings, and familiars.</gray>"
        ), actionKey, ACTION_COMMUNITY_MENU));

        inventory.setItem(22, essenceOverview(plugin, player));

        inventory.setItem(27, button(Material.KNOWLEDGE_BOOK, "<gold><bold>Changelog</bold></gold>", List.of(
            "<gray>Read the latest server changes.</gray>"
        ), actionKey, ACTION_CHANGELOG));
        if (BedrockCompat.isBedrockPlayer(player) && plugin.getCrossplayManager() != null) {
            inventory.setItem(29, button(Material.RECOVERY_COMPASS, "<aqua><bold>Bedrock Controls</bold></aqua>", List.of(
                "<gray>Ability shortcuts and crossplay help.</gray>",
                "<yellow>Tap to open.</yellow>"
            ), actionKey, ACTION_CROSSPLAY));
        }
        inventory.setItem(31, button(Material.BARRIER, "<red><bold>Close</bold></red>", List.of(
            "<gray>Close this menu.</gray>"
        ), actionKey, ACTION_CLOSE));
        inventory.setItem(35, button(Material.WRITABLE_BOOK, "<yellow><bold>Wiki</bold></yellow>", List.of(
            "<gray>Open the server guide.</gray>"
        ), actionKey, ACTION_WIKI));

        player.openInventory(inventory);
    }

    public static void refreshEssenceNumbers(SMPCore plugin, Player player) {
        if (plugin == null || player == null || !player.isOnline()) return;
        Inventory top = player.getOpenInventory().getTopInventory();
        InventoryHolder holder = top.getHolder(false);
        NamespacedKey actionKey = actionKey(plugin);
        boolean changed = false;

        if (holder instanceof MainMenuHolder) {
            top.setItem(4, playerOverviewHead(plugin, player, actionKey, ACTION_STATS));
            top.setItem(22, essenceOverview(plugin, player));
            changed = true;
        } else if (holder instanceof SectionMenuHolder
            && ACTION_STATS.equals(menuAction(top.getItem(11), actionKey))) {
            top.setItem(11, playerOverviewHead(plugin, player, actionKey, ACTION_STATS));
            changed = true;
        } else if (holder instanceof PlayerStatsMenuHolder) {
            String refreshAction = menuAction(top.getItem(4), actionKey);
            if (ACTION_REFRESH.equals(refreshAction) || ACTION_CUSTOM_STATS_REFRESH.equals(refreshAction)) {
                top.setItem(4, playerOverviewHead(plugin, player, actionKey, refreshAction));
                if (ACTION_CUSTOM_STATS_REFRESH.equals(refreshAction)) {
                    top.setItem(33, statItem(
                        Material.EXPERIENCE_BOTTLE,
                        "<yellow><bold>Progress & Currency</bold></yellow>",
                        progressionStatsLore(plugin, player)
                    ));
                }
                changed = true;
            }
        }

        if (changed) player.updateInventory();
    }

    private void openSectionMenu(Player player, MenuSection section) {
        Inventory inventory = Bukkit.createInventory(
            new SectionMenuHolder(),
            SECTION_MENU_SIZE,
            BedrockCompat.menuTitle(player, MM.deserialize(section.title()), section.fallbackTitle())
        );
        decorateFrame(inventory);
        inventory.setItem(4, button(section.icon(), section.title(), List.of(section.description())));

        switch (section) {
            case PROFILE -> {
                inventory.setItem(11, playerOverviewHead(plugin, player, menuActionKey, ACTION_STATS));
                inventory.setItem(13, classMenuButton(player));
                inventory.setItem(15, button(Material.COMPARATOR, "<yellow><bold>Settings</bold></yellow>", List.of(
                    "<gray>Personal toggles and item safety.</gray>",
                    "<yellow>Click to configure.</yellow>"
                ), menuActionKey, ACTION_SETTINGS));
            }
            case GEAR -> {
                inventory.setItem(11, button(Material.ENCHANTED_BOOK, "<gold><bold>Reliquary</bold></gold>", List.of(
                    "<gray>Recipes, tools, armor, and relics.</gray>",
                    "<yellow>Click to browse.</yellow>"
                ), menuActionKey, ACTION_RELIQUARY));
                inventory.setItem(13, button(Material.NETHERITE_CHESTPLATE, "<light_purple><bold>Armory of the Veil</bold></light_purple>", List.of(
                    "<gray>Boss-forged weapons and armor.</gray>",
                    "<yellow>Click to open.</yellow>"
                ), menuActionKey, ACTION_ARMORY));
                inventory.setItem(15, button(Material.EXPERIENCE_BOTTLE, "<aqua><bold>Custom Enchants</bold></aqua>", List.of(
                    "<gray>Effects, valid gear, and recipes.</gray>",
                    "<yellow>Click to view.</yellow>"
                ), menuActionKey, ACTION_ENCHANTS));
            }
            case CHALLENGES -> {
                boolean bossBrewsUnlocked = plugin.getWitchManager() != null && plugin.getWitchManager().hasBossBrewingUnlocked(player);
                inventory.setItem(11, button(Material.WITHER_SKELETON_SKULL, "<red><bold>Boss Rituals</bold></red>", List.of(
                    "<gray>Shrines, catalysts, and drops.</gray>",
                    "<yellow>Click to view.</yellow>"
                ), menuActionKey, ACTION_BOSSES));
                inventory.setItem(13, button(Material.END_CRYSTAL, "<light_purple><bold>Mythic Nexus</bold></light_purple>", List.of(
                    "<gray>Fusion pairings and mythic rewards.</gray>",
                    "<yellow>Click to inspect.</yellow>"
                ), menuActionKey, ACTION_MYTHICS));
                inventory.setItem(15, button(Material.POTION, "<blue><bold>Boss Brews</bold></blue>", List.of(
                    "<gray>Potions brewed with boss materials.</gray>",
                    bossBrewsUnlocked ? "<yellow>Click for recipes.</yellow>" : "<dark_gray>Complete Vespera's Moonlit Thesis.</dark_gray>"
                ), menuActionKey, ACTION_BREWS));
            }
            case COMMUNITY -> {
                inventory.setItem(11, button(Material.SHIELD, "<green><bold>Teams</bold></green>", List.of(
                    "<gray>Browse teams and open team tools.</gray>",
                    "<yellow>Click to open.</yellow>"
                ), menuActionKey, ACTION_TEAMS));
                inventory.setItem(13, button(Material.GOLD_BLOCK, "<gold><bold>Leaderboards</bold></gold>", List.of(
                    "<gray>Kills, playtime, bosses, and reports.</gray>",
                    "<yellow>Click to compete.</yellow>"
                ), menuActionKey, ACTION_LEADERBOARDS));
                inventory.setItem(15, familiarMenuButton(plugin, player, menuActionKey));
            }
        }

        inventory.setItem(18, button(Material.ARROW, "<yellow>Back</yellow>", List.of(
            "<gray>Return to the main menu.</gray>"
        ), menuActionKey, ACTION_BACK));
        inventory.setItem(22, button(Material.BARRIER, "<red>Close</red>", List.of(
            "<gray>Close this menu.</gray>"
        ), menuActionKey, ACTION_CLOSE));
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder(false);
        if (!(holder instanceof MainMenuHolder)
            && !(holder instanceof SectionMenuHolder)
            && !(holder instanceof PlayerStatsMenuHolder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClick() != org.bukkit.event.inventory.ClickType.LEFT
            && event.getClick() != org.bukkit.event.inventory.ClickType.RIGHT) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return;
        }

        String action = menuAction(event.getCurrentItem());
        if (action == null) {
            return;
        }

        if (holder instanceof PlayerStatsMenuHolder) {
            Bukkit.getScheduler().runTask(plugin, () -> handleStatsMenuAction(player, action));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> handleMainMenuAction(player, action));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof MainMenuHolder
            || holder instanceof SectionMenuHolder
            || holder instanceof PlayerStatsMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void handleMainMenuAction(Player player, String action) {
        if (!player.isOnline()) {
            return;
        }
        switch (action) {
            case ACTION_PROFILE_MENU -> openSectionMenu(player, MenuSection.PROFILE);
            case ACTION_GEAR_MENU -> openSectionMenu(player, MenuSection.GEAR);
            case ACTION_CHALLENGES_MENU -> openSectionMenu(player, MenuSection.CHALLENGES);
            case ACTION_COMMUNITY_MENU -> openSectionMenu(player, MenuSection.COMMUNITY);
            case ACTION_CROSSPLAY -> {
                if (plugin.getCrossplayManager() != null) {
                    plugin.getCrossplayManager().openControls(player);
                }
            }
            case ACTION_STATS -> openPlayerStatsMenu(player);
            case ACTION_CUSTOM_STATS -> openCustomStatsMenu(player);
            case ACTION_SETTINGS -> openSettings(player);
            case ACTION_RELIQUARY -> openReliquary(player);
            case ACTION_ARMORY -> openArmory(player);
            case ACTION_MYTHICS -> openMythics(player);
            case ACTION_BOSSES -> openBossRituals(player);
            case ACTION_ENCHANTS -> openEnchants(player);
            case ACTION_BREWS -> openBossBrews(player);
            case ACTION_POWERS -> openPowers(player);
            case ACTION_TEAMS -> openTeams(player);
            case ACTION_LEADERBOARDS -> openLeaderboards(player);
            case ACTION_FAMILIARS -> openFamiliars(player);
            case ACTION_CHANGELOG -> openChangelog(player);
            case ACTION_WIKI -> openWiki(player);
            case ACTION_BACK -> openMenu(plugin, player);
            case ACTION_CLOSE -> player.closeInventory();
            default -> {
            }
        }
    }

    private void handleStatsMenuAction(Player player, String action) {
        if (!player.isOnline()) {
            return;
        }
        switch (action) {
            case ACTION_BACK -> openMenu(plugin, player);
            case ACTION_REFRESH -> openPlayerStatsMenu(player);
            case ACTION_CUSTOM_STATS -> openCustomStatsMenu(player);
            case ACTION_CUSTOM_STATS_REFRESH -> openCustomStatsMenu(player);
            case ACTION_CLOSE -> player.closeInventory();
            default -> {
            }
        }
    }

    private void openReliquary(Player player) {
        if (!player.hasPermission("smpcore.legendary.recipe")) {
            player.sendMessage(MessageUtil.error("You do not have permission to use the Reliquary."));
            return;
        }
        plugin.getLegendaryListener().openRecipeMenuFromMainMenu(player);
    }

    private void openMythics(Player player) {
        if (!player.hasPermission("smpcore.legendary.recipe")) {
            player.sendMessage(MessageUtil.error("You do not have permission to view mythics."));
            return;
        }
        plugin.getLegendaryListener().openMythicFusionMenuFromMainMenu(player);
    }

    private void openArmory(Player player) {
        if (!player.hasPermission("smpcore.legendary.recipe")) {
            player.sendMessage(MessageUtil.error("You do not have permission to view the Armory of the Veil."));
            return;
        }
        if (plugin.getSeasonRelicManager() == null) {
            player.sendMessage(MessageUtil.error("The Armory of the Veil is not ready yet."));
            return;
        }
        plugin.getSeasonRelicManager().openArmoryMenuFromMainMenu(player);
    }

    private void openBossRituals(Player player) {
        if (!player.hasPermission("smpcore.bossrituals")) {
            player.sendMessage(MessageUtil.error("You do not have permission to view boss rituals."));
            return;
        }
        if (plugin.getBossManager() == null) {
            player.sendMessage(MessageUtil.error("Boss rituals are not ready yet."));
            return;
        }
        plugin.getBossManager().openRitualMenu(player);
    }

    private void openEnchants(Player player) {
        if (!player.hasPermission("smpcore.enchants")) {
            player.sendMessage(MessageUtil.error("You do not have permission to view custom enchants."));
            return;
        }
        plugin.getCustomEnchantListener().openEnchantMenu(player);
    }

    private void openPowers(Player player) {
        if (!player.hasPermission("smpcore.superpower.admin")) {
            player.sendMessage(MessageUtil.error("You do not have permission to view class info."));
            return;
        }
        if (plugin.getSuperpowerManager() == null) {
            player.sendMessage(MessageUtil.error("Class info is not ready yet."));
            return;
        }
        plugin.getSuperpowerManager().openAdminInfoMenu(player);
    }

    private void openTeams(Player player) {
        if (!player.hasPermission("smpcore.team")) {
            player.sendMessage(MessageUtil.error("You do not have permission to view teams."));
            return;
        }
        plugin.getTeamManager().openTeamsMenu(player, null, true);
    }

    private void openLeaderboards(Player player) {
        if (!player.hasPermission("smpcore.leaderboard")) {
            player.sendMessage(MessageUtil.error("You do not have permission to view leaderboards."));
            return;
        }
        if (plugin.getLeaderboardManager() == null) {
            player.sendMessage(MessageUtil.error("Leaderboards are not ready yet."));
            return;
        }
        plugin.getLeaderboardManager().openOverviewMenu(player);
    }

    private void openFamiliars(Player player) {
        if (!player.hasPermission("smpcore.familiar")) {
            player.sendMessage(MessageUtil.error("You do not have permission to manage familiars."));
            return;
        }
        if (plugin.getMayorQuestManager() == null) {
            player.sendMessage(MessageUtil.error("Familiars are not ready yet."));
            return;
        }
        plugin.getMayorQuestManager().openPetCollectionMenu(player);
    }

    private void openSettings(Player player) {
        if (!player.hasPermission("smpcore.settings")) {
            player.sendMessage(MessageUtil.error("You do not have permission to use player settings."));
            return;
        }
        if (plugin.getPlayerSettingsManager() == null) {
            player.sendMessage(MessageUtil.error("Player settings are not ready yet."));
            return;
        }
        plugin.getPlayerSettingsManager().openSettingsMenu(player, true);
    }

    private void openChangelog(Player player) {
        if (!player.hasPermission("smpcore.changelog")) {
            player.sendMessage(MessageUtil.error("You do not have permission to view the changelog."));
            return;
        }
        player.closeInventory();
        plugin.getChangelogManager().send(player);
    }

    private void openWiki(Player player) {
        if (!player.hasPermission("smpcore.wiki")) {
            player.sendMessage(MessageUtil.error("You do not have permission to view the wiki."));
            return;
        }
        player.closeInventory();
        player.performCommand("wiki");
    }

    private void openBossBrews(Player player) {
        if (!player.hasPermission("smpcore.bossbrews")) {
            player.sendMessage(MessageUtil.error("You do not have permission to view boss brews."));
            return;
        }
        if (plugin.getBossPotionListener() == null) {
            player.sendMessage(MessageUtil.error("Boss brews are not ready yet."));
            return;
        }
        plugin.getBossPotionListener().openPotionMenu(player);
    }

    private void openPlayerStatsMenu(Player player) {
        NamespacedKey actionKey = actionKey(plugin);
        Inventory inventory = Bukkit.createInventory(
            new PlayerStatsMenuHolder(),
            STATS_MENU_SIZE,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#7c3aed:#22d3ee><bold>Your Stats</bold></gradient>"), "Your Stats")
        );
        decorateFrame(inventory);
        inventory.setItem(4, playerOverviewHead(plugin, player, actionKey, ACTION_REFRESH));
        inventory.setItem(10, statItem(Material.NETHERITE_SWORD, "<red><bold>Combat</bold></red>", List.of(
            attributeLine(player, Attribute.ATTACK_DAMAGE, "Attack Damage"),
            attributeLine(player, Attribute.ATTACK_SPEED, "Attack Speed"),
            attributeLine(player, Attribute.LUCK, "Luck"),
            "<dark_gray>Updates from your current gear and held item.</dark_gray>"
        )));
        inventory.setItem(12, statItem(Material.SHIELD, "<aqua><bold>Defense</bold></aqua>", List.of(
            attributeLine(player, Attribute.MAX_HEALTH, "Max Health"),
            "<gray>Hearts: <white>" + formatStat(attributeValue(player, Attribute.MAX_HEALTH) / 2.0) + "</white></gray>",
            attributeLine(player, Attribute.ARMOR, "Armor"),
            attributeLine(player, Attribute.ARMOR_TOUGHNESS, "Toughness"),
            attributeLine(player, Attribute.KNOCKBACK_RESISTANCE, "Knockback Resist")
        )));
        inventory.setItem(14, statItem(Material.FEATHER, "<green><bold>Movement</bold></green>", List.of(
            attributeLine(player, Attribute.MOVEMENT_SPEED, "Move Speed"),
            attributeLine(player, Attribute.SCALE, "Scale"),
            attributeLine(player, Attribute.SUBMERGED_MINING_SPEED, "Underwater Mining"),
            "<dark_gray>Some classes and relics refresh these passively.</dark_gray>"
        )));
        inventory.setItem(16, statItem(Material.AMETHYST_SHARD, "<light_purple><bold>Class</bold></light_purple>", powerLore(player)));
        inventory.setItem(28, statItem(Material.ITEM_FRAME, "<gold><bold>Held Item</bold></gold>", heldItemLore(player)));
        inventory.setItem(30, statItem(Material.IRON_CHESTPLATE, "<white><bold>Armor Slots</bold></white>", armorLore(player)));
        inventory.setItem(32, statItem(Material.POTION, "<blue><bold>Active Effects</bold></blue>", effectLore(player)));
        inventory.setItem(34, statItem(Material.COMPASS, "<yellow><bold>World State</bold></yellow>", List.of(
            "<gray>World: <white>" + miniEscape(player.getWorld().getName()) + "</white></gray>",
            "<gray>Level: <white>" + player.getLevel() + "</white></gray>",
            "<gray>Health: <white>" + formatStat(player.getHealth()) + "/" + formatStat(attributeValue(player, Attribute.MAX_HEALTH)) + "</white></gray>",
            "<gray>Food: <white>" + player.getFoodLevel() + "/20</white></gray>"
        )));
        inventory.setItem(47, button(Material.NETHER_STAR, "<gradient:#facc15:#22d3ee><bold>Custom Stats</bold></gradient>", List.of(
            "<gray>Mining, familiar, combat, luck,</gray>",
            "<gray>equipment, and seasonal multipliers.</gray>",
            "<yellow>Click to view every custom total.</yellow>"
        ), actionKey, ACTION_CUSTOM_STATS));
        inventory.setItem(45, button(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to /menu.</gray>"), actionKey, ACTION_BACK));
        inventory.setItem(49, button(Material.CLOCK, "<green>Refresh</green>", List.of("<gray>Update stats after changing gear.</gray>"), actionKey, ACTION_REFRESH));
        inventory.setItem(53, button(Material.BARRIER, "<red>Close</red>", List.of("<gray>Close this menu.</gray>"), actionKey, ACTION_CLOSE));
        player.openInventory(inventory);
    }

    private void openCustomStatsMenu(Player player) {
        NamespacedKey actionKey = actionKey(plugin);
        Inventory inventory = Bukkit.createInventory(
            new PlayerStatsMenuHolder(),
            STATS_MENU_SIZE,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#facc15:#22d3ee><bold>Custom Stats</bold></gradient>"), "Custom Stats")
        );
        decorateFrame(inventory);
        inventory.setItem(4, playerOverviewHead(plugin, player, actionKey, ACTION_CUSTOM_STATS_REFRESH));
        inventory.setItem(10, statItem(Material.DIAMOND_PICKAXE, "<gold><bold>Mining & Gathering</bold></gold>", gatheringStatsLore(player)));
        inventory.setItem(12, statItem(Material.NETHERITE_SWORD, "<red><bold>Combat Multipliers</bold></red>", customCombatStatsLore(player)));
        inventory.setItem(14, statItem(Material.ALLAY_SPAWN_EGG, "<light_purple><bold>Familiars</bold></light_purple>", familiarStatsLore(player)));
        inventory.setItem(16, statItem(Material.RABBIT_FOOT, "<green><bold>Luck & Rewards</bold></green>", rewardStatsLore(player)));
        inventory.setItem(29, statItem(Material.IRON_CHESTPLATE, "<aqua><bold>Equipment Totals</bold></aqua>", equipmentCustomStatsLore(player)));
        inventory.setItem(31, statItem(Material.AMETHYST_SHARD, "<light_purple><bold>Class & Abilities</bold></light_purple>", powerLore(player)));
        inventory.setItem(33, statItem(Material.EXPERIENCE_BOTTLE, "<yellow><bold>Progress & Currency</bold></yellow>", progressionStatsLore(plugin, player)));
        inventory.setItem(45, button(Material.ARROW, "<yellow>Base Stats</yellow>", List.of("<gray>Return to vanilla attributes.</gray>"), actionKey, ACTION_STATS));
        inventory.setItem(49, button(Material.CLOCK, "<green>Refresh</green>", List.of("<gray>Recalculate every live custom value.</gray>"), actionKey, ACTION_CUSTOM_STATS_REFRESH));
        inventory.setItem(53, button(Material.BARRIER, "<red>Close</red>", List.of("<gray>Close this menu.</gray>"), actionKey, ACTION_CLOSE));
        player.openInventory(inventory);
    }

    private List<String> gatheringStatsLore(Player player) {
        double miningLuck = plugin.getGoblinHuntManager() == null ? 0.0D : plugin.getGoblinHuntManager().miningLuck(player);
        double petChance = plugin.getMinerManager() == null ? 0.0D : plugin.getMinerManager().minerPetTripleChance(player);
        double feverChance = plugin.getMinerManager() == null ? 0.0D : plugin.getMinerManager().miningFeverTripleChance(player);
        long feverTime = plugin.getMinerManager() == null ? 0L : plugin.getMinerManager().miningFeverRemainingMillis(player);
        long cooldown = plugin.getMinerManager() == null ? 0L : plugin.getMinerManager().miningFeverCooldownRemainingMillis(player);
        return List.of(
            customPercentLine("Mining Luck double", miningLuck),
            customPercentLine("Miner Familiar triple", petChance),
            customPercentLine("Mining Fever triple", feverChance),
            "<gray>Mining Fever: " + (feverTime > 0 ? "<green>active " + compactDuration(feverTime) + "</green>" : cooldown > 0 ? "<yellow>ready in " + compactDuration(cooldown) + "</yellow>" : "<green>ready</green>") + ".</gray>",
            "<gray>Vein Miner: <white>" + (plugin.getVeinMinerListener() != null && plugin.getVeinMinerListener().isEnabledFor(player) ? "enabled" : "disabled") + "</white>.</gray>",
            "<dark_gray>Ore bonuses add copies instead of multiplying each other.</dark_gray>"
        );
    }

    private List<String> customCombatStatsLore(Player player) {
        boolean goblinBonus = plugin.getGoblinHuntManager() != null && plugin.getGoblinHuntManager().hasCompletedActiveHunt(player);
        double ward = plugin.getPriestManager() == null ? 1.0D : plugin.getPriestManager().bossWardDamageMultiplier(player);
        double pvpSet = plugin.getSeasonRelicManager() == null ? 0.0D : plugin.getSeasonRelicManager().equippedFullSetPlayerDamageBonus(player);
        double taken = plugin.getReforgeManager() == null ? 1.0D : plugin.getReforgeManager().equippedDamageTakenMultiplier(player);
        int aggro = plugin.getVeilOrbManager() == null ? 0 : plugin.getVeilOrbManager().aggroBonus(player);
        return List.of(
            customPercentLine("Goblin hunt damage", goblinBonus ? 0.02D : 0.0D),
            customPercentLine("Boss Ward damage", ward - 1.0D),
            customPercentLine("Full-set player damage", pvpSet),
            "<gray>Reforge damage taken: <white>" + formatStat(taken * 100.0D) + "%</white>.</gray>",
            "<gray>Boss aggro bonus: <white>+" + aggro + "</white>.</gray>",
            "<dark_gray>Vanilla totals are under Base Stats.</dark_gray>"
        );
    }

    private List<String> familiarStatsLore(Player player) {
        boolean wispUnlocked = plugin.getMayorQuestManager() != null && plugin.getMayorQuestManager().hasPetUnlocked(player);
        boolean wispActive = plugin.getMayorQuestManager() != null && plugin.getMayorQuestManager().hasActiveVeilWisp(player);
        boolean minerUnlocked = plugin.getMinerManager() != null && plugin.getMinerManager().hasMinerPet(player);
        boolean minerActive = plugin.getMinerManager() != null && plugin.getMinerManager().hasActiveMinerPet(player);
        boolean tillerUnlocked = plugin.getFarmerManager() != null && plugin.getFarmerManager().hasTiller(player);
        boolean tillerActive = plugin.getFarmerManager() != null && plugin.getFarmerManager().hasActiveTiller(player);
        boolean morrowUnlocked = plugin.getWitchManager() != null && plugin.getWitchManager().hasMorrow(player);
        boolean morrowActive = plugin.getWitchManager() != null && plugin.getWitchManager().hasActiveMorrow(player);
        double wispCore = plugin.getBeastwardenManager() == null ? 1.0D : plugin.getBeastwardenManager().familiarCoreMultiplier(player, "veil_wisp");
        double tillerCore = plugin.getBeastwardenManager() == null ? 1.0D : plugin.getBeastwardenManager().familiarCoreMultiplier(player, "tiller");
        double morrowCore = plugin.getBeastwardenManager() == null ? 1.0D : plugin.getBeastwardenManager().familiarCoreMultiplier(player, "morrow");
        List<String> lore = new ArrayList<>(List.of(
            "<gray>Veil Wisp: " + state(wispUnlocked, wispActive) + ".</gray>",
            "<gray>Wisp Essence: <white>" + (wispActive ? "+" + formatStat(50.0D * wispCore) + "%" : "0%") + "</white>.</gray>",
            "<gray>Wisp boss roll: <white>" + (wispActive ? formatStat(51.0D * wispCore) + "% solo" : "disabled") + "</white>.</gray>",
            "<gray>Miner Familiar: " + state(minerUnlocked, minerActive) + ".</gray>",
            "<gray>Miner triple: <white>" + formatStat((plugin.getMinerManager() == null ? 0.0D : plugin.getMinerManager().minerPetTripleChance(player)) * 100.0D) + "%</white>.</gray>",
            "<gray>Tiller: " + state(tillerUnlocked, tillerActive) + ".</gray>",
            "<gray>Hearty food: <white>" + (tillerActive && plugin.getFarmerManager().hasHeartyFoodMastery(player) ? formatStat(5.0D * tillerCore) + "%" : "0%") + "</white>.</gray>",
            "<gray>Morrow: " + state(morrowUnlocked, morrowActive) + ".</gray>",
            "<gray>Potion duration: <white>" + (morrowActive ? "+" + formatStat(15.0D * morrowCore) + "-" + formatStat(Math.min(36.0D, 30.0D * morrowCore)) + "%" : "0%") + "</white>.</gray>"
        ));
        if (plugin.getBeastwardenManager() != null) {
            String active = plugin.getBeastwardenManager().activeFamiliarId(player);
            String activeName = switch (active == null ? "" : active) {
                case "veil_wisp" -> "Veil Wisp";
                case "miner" -> "Miner Familiar";
                case "tiller" -> "Tiller";
                case "morrow" -> "Morrow";
                default -> "None";
            };
            lore.add("<gray>Active training: <white>" + activeName + "</white>.</gray>");
            lore.add("<gray>Mob damage: <white>+" + formatStat(plugin.getBeastwardenManager().activeFamiliarMobDamageBonus(player) * 100.0D) + "%</white>.</gray>");
            lore.add("<gray>Mob defense: <white>-" + formatStat((1.0D - plugin.getBeastwardenManager().activeFamiliarDamageTakenMultiplier(player)) * 100.0D) + "%</white>.</gray>");
            lore.add("<gray>Speed / XP: <white>+" + formatStat(plugin.getBeastwardenManager().activeFamiliarSpeedBonus(player) * 100.0D) + "% / +" + formatStat(plugin.getBeastwardenManager().activeFamiliarExperienceBonus(player) * 100.0D) + "%</white>.</gray>");
        }
        return lore;
    }

    private List<String> rewardStatsLore(Player player) {
        double tavernLuck = plugin.getTavernManager() == null ? 0.0D : plugin.getTavernManager().gamblingLuck(player);
        return List.of(
            customPercentLine("Tavern Luck", tavernLuck),
            "<gray>Maximum Tavern Luck: <white>10%</white>.</gray>",
            "<gray>Mining Luck cap: <white>20%</white>.</gray>",
            "<gray>Rare-drop Luck attribute: <white>" + formatStat(attributeValue(player, Attribute.LUCK)) + "</white>.</gray>",
            "<dark_gray>Temporary food, drink, and familiar states update live.</dark_gray>"
        );
    }

    private List<String> equipmentCustomStatsLore(Player player) {
        return List.of(
            "<gray>Damage: <white>" + formatStat(attributeValue(player, Attribute.ATTACK_DAMAGE)) + "</white>.</gray>",
            "<gray>Armor: <white>" + formatStat(attributeValue(player, Attribute.ARMOR)) + "</white>.</gray>",
            "<gray>Toughness: <white>" + formatStat(attributeValue(player, Attribute.ARMOR_TOUGHNESS)) + "</white>.</gray>",
            "<gray>Max health: <white>" + formatStat(attributeValue(player, Attribute.MAX_HEALTH)) + "</white>.</gray>",
            "<gray>Movement: <white>" + formatStat(attributeValue(player, Attribute.MOVEMENT_SPEED)) + "</white>.</gray>",
            "<dark_gray>Includes reforges, corruption, awakening, orbs, sets, and item attributes.</dark_gray>"
        );
    }

    private static List<String> progressionStatsLore(SMPCore plugin, Player player) {
        if (plugin.getEssenceManager() == null) return List.of("<dark_gray>Essence data is loading.</dark_gray>");
        return List.of(
            "<gray>Essence: <white>" + plugin.getEssenceManager().formattedBalance(player) + "</white>.</gray>",
            "<gray>Lifetime earned: <white>" + plugin.getEssenceManager().lifetimeEarned(player) + "</white>.</gray>",
            "<gray>Veil Authority: <white>" + (plugin.getOverseerManager() == null ? 0 : plugin.getOverseerManager().authority(player)) + "</white>.</gray>",
            "<gray>Mining progress: <white>" + plugin.getEssenceManager().miningProgress(player) + "</white>.</gray>",
            "<gray>Mob progress: <white>" + plugin.getEssenceManager().mobProgress(player) + "</white>.</gray>",
            "<gray>XP progress: <white>" + plugin.getEssenceManager().xpProgress(player) + "</white>.</gray>"
        );
    }

    private static String customPercentLine(String label, double value) {
        String sign = value > 0.00001D ? "+" : "";
        return "<gray>" + label + ": <white>" + sign + formatStat(value * 100.0D) + "%</white>.</gray>";
    }

    private static String state(boolean unlocked, boolean active) {
        if (!unlocked) return "<dark_gray>locked</dark_gray>";
        return active ? "<green>active</green>" : "<yellow>dismissed</yellow>";
    }

    private static String compactDuration(long millis) {
        long seconds = Math.max(1L, (millis + 999L) / 1000L);
        return seconds >= 60L ? (seconds / 60L) + "m " + (seconds % 60L) + "s" : seconds + "s";
    }

    private static void decorateFrame(Inventory inventory) {
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isFrameSlot(slot, inventory.getSize())) {
                inventory.setItem(slot, filler);
            }
        }
    }

    private static boolean isFrameSlot(int slot, int size) {
        return slot < 9 || slot >= size - 9 || slot % 9 == 0 || slot % 9 == 8;
    }

    private static ItemStack playerOverviewHead(SMPCore plugin, Player player, NamespacedKey actionKey, String action) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(MM.deserialize("<gradient:#7c3aed:#22d3ee><bold>" + miniEscape(player.getName()) + "</bold></gradient>"));
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Class: <white>" + powerName(plugin, player) + "</white></gray>");
        lore.add("<gray>Essence: <white>" + essenceBalance(plugin, player) + "</white></gray>");
        lore.add("<gray>Health: <white>" + formatStat(player.getHealth()) + "/" + formatStat(attributeValue(player, Attribute.MAX_HEALTH)) + "</white></gray>");
        lore.add("<gray>Damage: <white>" + formatStat(attributeValue(player, Attribute.ATTACK_DAMAGE)) + "</white></gray>");
        lore.add("<gray>Armor: <white>" + formatStat(attributeValue(player, Attribute.ARMOR)) + "</white></gray>");
        lore.add("<dark_gray>Click for full live stats.</dark_gray>");
        meta.lore(MenuItemUtil.visibleMiniLore("profile", lore).stream().map(MM::deserialize).toList());
        applyAction(meta, actionKey, action);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack essenceOverview(SMPCore plugin, Player player) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Balance: <white>" + essenceBalance(plugin, player) + "</white></gray>");
        if (plugin.getEssenceManager() != null && plugin.getConfigManager() != null) {
            lore.add("<dark_gray> ");
            lore.add(progressLine("Mining", plugin.getEssenceManager().miningProgress(player), plugin.getConfigManager().normalEssenceMiningThreshold));
            lore.add(progressLine("Mobs", plugin.getEssenceManager().mobProgress(player), plugin.getConfigManager().normalEssenceMobKillThreshold));
            lore.add(progressLine("XP", plugin.getEssenceManager().xpProgress(player), plugin.getConfigManager().normalEssenceXpThreshold));
            lore.add("<dark_gray>Player kills pay directly.</dark_gray>");
        } else {
            lore.add("<dark_gray>Loading...</dark_gray>");
        }
        return button(
            Material.EXPERIENCE_BOTTLE,
            "<gradient:#22d3ee:#73ff9d><bold>Essence</bold></gradient>",
            lore
        );
    }

    private static ItemStack familiarMenuButton(SMPCore plugin, Player player, NamespacedKey actionKey) {
        List<String> lore = new ArrayList<>();
        if (plugin.getMayorQuestManager() == null) {
            lore.add("<gray>Familiar manager is loading.</gray>");
        } else if (plugin.getMayorQuestManager().hasPetUnlocked(player)
            || (plugin.getMinerManager() != null && plugin.getMinerManager().hasMinerPet(player))
            || (plugin.getFarmerManager() != null && plugin.getFarmerManager().hasTiller(player))
            || (plugin.getWitchManager() != null && plugin.getWitchManager().hasMorrow(player))) {
            int unlocked = (plugin.getMayorQuestManager().hasPetUnlocked(player) ? 1 : 0)
                + (plugin.getMinerManager() != null && plugin.getMinerManager().hasMinerPet(player) ? 1 : 0)
                + (plugin.getFarmerManager() != null && plugin.getFarmerManager().hasTiller(player) ? 1 : 0)
                + (plugin.getWitchManager() != null && plugin.getWitchManager().hasMorrow(player) ? 1 : 0);
            lore.add("<gray>Unlocked familiars: <white>" + unlocked + "</white>.</gray>");
            lore.add("<gray>Summon, dismiss, and manage familiars.</gray>");
            lore.add("<yellow>Click to open.</yellow>");
        } else {
            lore.add("<gray>No familiars unlocked yet.</gray>");
            lore.add("<dark_gray>Mayor, Miner, Farmer, and Witch quests unlock them.</dark_gray>");
            lore.add("<yellow>Click to view.</yellow>");
        }
        ItemStack icon = button(
            Material.PLAYER_HEAD,
            "<light_purple><bold>Familiars</bold></light_purple>",
            lore,
            actionKey,
            ACTION_FAMILIARS
        );
        return applyFamiliarHeadTexture(plugin, player, icon);
    }

    private static ItemStack applyFamiliarHeadTexture(SMPCore plugin, Player player, ItemStack icon) {
        if (plugin.getMayorQuestManager() != null && plugin.getMayorQuestManager().hasActiveVeilWisp(player)) {
            return plugin.getMayorQuestManager().applyVeilWispHeadTexture(icon);
        }
        if (plugin.getMinerManager() != null && plugin.getMinerManager().hasActiveMinerPet(player)) {
            return plugin.getMinerManager().applyMinerHeadTexture(icon);
        }
        if (plugin.getFarmerManager() != null && plugin.getFarmerManager().hasActiveTiller(player)) {
            return plugin.getFarmerManager().applyTillerHeadTexture(icon);
        }
        if (plugin.getWitchManager() != null && plugin.getWitchManager().hasActiveMorrow(player)) {
            return plugin.getWitchManager().applyMorrowHeadTexture(icon);
        }
        if (plugin.getMayorQuestManager() != null && plugin.getMayorQuestManager().hasPetUnlocked(player)) {
            return plugin.getMayorQuestManager().applyVeilWispHeadTexture(icon);
        }
        if (plugin.getMinerManager() != null && plugin.getMinerManager().hasMinerPet(player)) {
            return plugin.getMinerManager().applyMinerHeadTexture(icon);
        }
        if (plugin.getFarmerManager() != null && plugin.getFarmerManager().hasTiller(player)) {
            return plugin.getFarmerManager().applyTillerHeadTexture(icon);
        }
        if (plugin.getWitchManager() != null && plugin.getWitchManager().hasMorrow(player)) {
            return plugin.getWitchManager().applyMorrowHeadTexture(icon);
        }
        return plugin.getMayorQuestManager() == null ? icon : plugin.getMayorQuestManager().applyVeilWispHeadTexture(icon);
    }

    private ItemStack classMenuButton(Player player) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Current: <white>" + powerName(plugin, player) + "</white></gray>");
        if (player.hasPermission("smpcore.superpower.admin")) {
            lore.add("<gray>View classes, passives, and commands.</gray>");
            lore.add("<yellow>Click to open class controls.</yellow>");
            return button(
                Material.AMETHYST_SHARD,
                "<light_purple><bold>Classes</bold></light_purple>",
                lore,
                menuActionKey,
                ACTION_POWERS
            );
        }
        lore.add("<dark_gray>Class controls are staff-only.</dark_gray>");
        return button(Material.AMETHYST_SHARD, "<light_purple><bold>Your Class</bold></light_purple>", lore);
    }

    private static ItemStack sectionButton(
        Material material,
        String name,
        List<String> lore,
        NamespacedKey actionKey,
        String action
    ) {
        List<String> sectionLore = new ArrayList<>(lore);
        sectionLore.add("<yellow>Click to open.</yellow>");
        return button(material, name, sectionLore, actionKey, action);
    }

    private static ItemStack statItem(Material material, String name, List<String> lore) {
        return button(material, name, lore);
    }

    private List<String> powerLore(Player player) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Current: <white>" + powerName(plugin, player) + "</white></gray>");
        if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH)) {
            lore.add("<gray>Strength is active right now.</gray>");
        }
        lore.add("<dark_gray>Use /powerinfo for class details.</dark_gray>");
        return lore;
    }

    private static String powerName(SMPCore plugin, Player player) {
        if (player == null || !player.isOnline()) {
            return "Unknown";
        }
        if (plugin == null || plugin.getSuperpowerManager() == null) {
            return "Unknown";
        }
        var power = plugin.getSuperpowerManager().powerOf(player);
        return power == null ? "Unknown" : miniEscape(power.displayName());
    }

    private static String essenceBalance(SMPCore plugin, Player player) {
        if (plugin == null || player == null || plugin.getEssenceManager() == null) {
            return "0";
        }
        return plugin.getEssenceManager().formattedBalance(player);
    }

    private static String progressLine(String label, int current, int threshold) {
        int safeThreshold = Math.max(1, threshold);
        int safeCurrent = Math.max(0, Math.min(current, safeThreshold));
        return "<gray>" + label + ": <white>" + safeCurrent + "/" + safeThreshold + "</white></gray>";
    }

    private static List<String> heldItemLore(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            return List.of("<gray>Main hand: <white>Empty</white></gray>");
        }
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Main hand: <white>" + miniEscape(itemName(item)) + "</white></gray>");
        lore.add("<gray>Type: <white>" + prettyMaterial(item.getType()) + "</white></gray>");
        if (item.getType().getMaxDurability() > 0 && item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable) {
            int max = item.getType().getMaxDurability();
            lore.add("<gray>Durability: <white>" + Math.max(0, max - damageable.getDamage()) + "/" + max + "</white></gray>");
        }
        lore.add("<dark_gray>The stat cards already include current held-item modifiers.</dark_gray>");
        return lore;
    }

    private static List<String> armorLore(Player player) {
        List<String> lore = new ArrayList<>();
        addArmorLine(lore, "Helmet", player.getInventory().getHelmet());
        addArmorLine(lore, "Chest", player.getInventory().getChestplate());
        addArmorLine(lore, "Legs", player.getInventory().getLeggings());
        addArmorLine(lore, "Boots", player.getInventory().getBoots());
        return lore;
    }

    private static void addArmorLine(List<String> lore, String label, ItemStack item) {
        lore.add("<gray>" + label + ": <white>" + (item == null || item.getType().isAir() ? "Empty" : miniEscape(itemName(item))) + "</white></gray>");
    }

    private static List<String> effectLore(Player player) {
        List<PotionEffect> effects = new ArrayList<>(player.getActivePotionEffects());
        if (effects.isEmpty()) {
            return List.of("<gray>No active potion effects.</gray>");
        }
        effects.sort(Comparator.comparing(effect -> effectName(effect).toLowerCase(Locale.ROOT)));
        List<String> lore = new ArrayList<>();
        int shown = 0;
        for (PotionEffect effect : effects) {
            if (shown++ >= 7) {
                lore.add("<dark_gray>+" + (effects.size() - 7) + " more...</dark_gray>");
                break;
            }
            lore.add("<gray>" + effectName(effect) + " <white>" + roman(effect.getAmplifier() + 1) + "</white> <dark_gray>" + formatDuration(effect.getDuration()) + "</dark_gray></gray>");
        }
        return lore;
    }

    private static String attributeLine(Player player, Attribute attribute, String label) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return "<gray>" + label + ": <dark_gray>N/A</dark_gray></gray>";
        }
        return "<gray>" + label + ": <white>" + formatStat(instance.getValue()) + "</white> <dark_gray>base " + formatStat(instance.getBaseValue()) + "</dark_gray></gray>";
    }

    private static double attributeValue(Player player, Attribute attribute) {
        AttributeInstance instance = player.getAttribute(attribute);
        return instance == null ? 0.0 : instance.getValue();
    }

    private static String itemName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return PLAIN.serialize(meta.displayName());
        }
        return prettyMaterial(item.getType());
    }

    private static String prettyMaterial(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private static String effectName(PotionEffect effect) {
        String key = effect.getType().getKey().getKey();
        String[] parts = key.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private static String formatDuration(int ticks) {
        if (ticks < 0 || ticks >= 20 * 60 * 60) {
            return "long";
        }
        int seconds = Math.max(1, ticks / 20);
        int minutes = seconds / 60;
        int remainder = seconds % 60;
        return minutes > 0 ? minutes + "m " + remainder + "s" : seconds + "s";
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(value);
        };
    }

    private static String formatStat(double value) {
        return String.format(Locale.US, Math.abs(value) >= 10.0 ? "%.1f" : "%.2f", value);
    }

    private static String miniEscape(String raw) {
        return raw == null ? "" : raw.replace("\\", "\\\\").replace("<", "\\<");
    }

    private static ItemStack button(Material material, String name, List<String> lore) {
        return button(material, name, lore, null, null);
    }

    private static ItemStack button(Material material, String name, List<String> lore, NamespacedKey actionKey, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(MenuItemUtil.visibleMiniName(name)));
        meta.lore(MenuItemUtil.visibleMiniLore(name, lore).stream().map(MM::deserialize).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        applyAction(meta, actionKey, action);
        item.setItemMeta(meta);
        return item;
    }

    private String menuAction(ItemStack item) {
        return menuAction(item, menuActionKey);
    }

    private static String menuAction(ItemStack item, NamespacedKey actionKey) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    private static void applyAction(ItemMeta meta, NamespacedKey actionKey, String action) {
        if (meta == null || actionKey == null || action == null || action.isBlank()) {
            return;
        }
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
    }

    private static NamespacedKey actionKey(SMPCore plugin) {
        return new NamespacedKey(plugin, "main_menu_action");
    }

    private static ItemStack pane(Material material) {
        return button(material, MenuItemUtil.INACTIVE_SLOT_NAME, MenuItemUtil.INACTIVE_SLOT_LORE);
    }

    private record MainMenuHolder() implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record SectionMenuHolder() implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record PlayerStatsMenuHolder() implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private enum MenuSection {
        PROFILE(
            Material.COMPASS,
            "<aqua><bold>Profile</bold></aqua>",
            "Profile",
            "<gray>Your stats, class, and settings.</gray>"
        ),
        GEAR(
            Material.NETHERITE_CHESTPLATE,
            "<gold><bold>Gear & Recipes</bold></gold>",
            "Gear & Recipes",
            "<gray>Equipment, recipes, and upgrades.</gray>"
        ),
        CHALLENGES(
            Material.WITHER_SKELETON_SKULL,
            "<red><bold>Bosses & Mythics</bold></red>",
            "Bosses & Mythics",
            "<gray>Boss rituals, fusions, and brews.</gray>"
        ),
        COMMUNITY(
            Material.SHIELD,
            "<green><bold>Community</bold></green>",
            "Community",
            "<gray>Teams, rankings, and familiars.</gray>"
        );

        private final Material icon;
        private final String title;
        private final String fallbackTitle;
        private final String description;

        MenuSection(Material icon, String title, String fallbackTitle, String description) {
            this.icon = icon;
            this.title = title;
            this.fallbackTitle = fallbackTitle;
            this.description = description;
        }

        private Material icon() {
            return icon;
        }

        private String title() {
            return title;
        }

        private String fallbackTitle() {
            return fallbackTitle;
        }

        private String description() {
            return description;
        }
    }
}
