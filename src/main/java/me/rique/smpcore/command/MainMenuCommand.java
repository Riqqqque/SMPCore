package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class MainMenuCommand implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int SIZE = 54;

    private final SMPCore plugin;

    public MainMenuCommand(SMPCore plugin) {
        this.plugin = plugin;
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
    }

    public static void openMenu(SMPCore plugin, Player player) {
        Inventory inventory = Bukkit.createInventory(
            new MainMenuHolder(),
            SIZE,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#f97316:#22d3ee><bold>Menu</bold></gradient>"), "Menu")
        );

        decorate(inventory);
        inventory.setItem(4, button(
            Material.NETHER_STAR,
            "<gradient:#facc15:#22d3ee><bold>Menu</bold></gradient>",
            List.of(
                "<gray>Your hub for progression, bosses, teams,</gray>",
                "<gray>custom items, stats, and server guides.</gray>",
                "<dark_gray>Use /menu anytime.</dark_gray>"
            )
        ));

        inventory.setItem(19, button(Material.ENCHANTED_BOOK, "<gold><bold>Reliquary</bold></gold>", List.of(
            "<gray>Custom item recipes, tools, armor,</gray>",
            "<gray>relics, and normal craftables.</gray>",
            "<yellow>Click to browse.</yellow>"
        )));
        inventory.setItem(20, button(Material.NETHERITE_CHESTPLATE, "<gradient:#ef4444:#facc15><bold>Covenant Armory</bold></gradient>", List.of(
            "<gray>Boss-forged weapons, armor sets,</gray>",
            "<gray>standalone pieces, and trophies.</gray>",
            "<yellow>Click to open.</yellow>"
        )));
        inventory.setItem(21, button(Material.END_CRYSTAL, "<gradient:#f472b6:#c084fc><bold>Mythic Nexus</bold></gradient>", List.of(
            "<gray>Fusion pairings and mythic rewards.</gray>",
            "<dark_gray>High investment. Big payoff.</dark_gray>",
            "<yellow>Click to inspect.</yellow>"
        )));
        inventory.setItem(23, button(Material.WITHER_SKELETON_SKULL, "<gradient:#7f1d1d:#fb923c><bold>Boss Rituals</bold></gradient>", List.of(
            "<gray>Summoning structures, boss rules,</gray>",
            "<gray>drops, and arena warnings.</gray>",
            "<yellow>Click for rituals.</yellow>"
        )));
        inventory.setItem(24, button(Material.EXPERIENCE_BOTTLE, "<gradient:#38bdf8:#73ff9d><bold>Custom Enchants</bold></gradient>", List.of(
            "<gray>Enchant effects, valid gear,</gray>",
            "<gray>and craft-only book recipes.</gray>",
            "<yellow>Click to view.</yellow>"
        )));
        inventory.setItem(25, button(Material.POTION, "<gradient:#38bdf8:#fb7185><bold>Boss Brews</bold></gradient>", List.of(
            "<gray>Stronger potions brewed with</gray>",
            "<gray>custom boss materials.</gray>",
            "<yellow>Click for recipes.</yellow>"
        )));

        inventory.setItem(30, button(Material.AMETHYST_SHARD, "<gradient:#c084fc:#fb7185><bold>Superpowers</bold></gradient>", List.of(
            "<gray>Power classes, chances, passives,</gray>",
            "<gray>and command abilities.</gray>",
            "<yellow>Click to open.</yellow>"
        )));
        inventory.setItem(31, button(Material.BARREL, "<green><bold>Team Vault</bold></green>", List.of(
            "<gray>Open your team's shared double chest.</gray>",
            "<dark_gray>Requires a team.</dark_gray>",
            "<yellow>Click to open.</yellow>"
        )));
        inventory.setItem(32, button(Material.GOLD_BLOCK, "<gradient:#facc15:#22d3ee><bold>Leaderboards</bold></gradient>", List.of(
            "<gray>Kills, deaths, boss damage,</gray>",
            "<gray>boss fights, and personal reports.</gray>",
            "<yellow>Click to compete.</yellow>"
        )));
        inventory.setItem(53, button(Material.WRITABLE_BOOK, "<yellow><bold>Wiki</bold></yellow>", List.of(
            "<gray>Open the server guide link.</gray>",
            "<dark_gray>Recipes, bosses, powers, commands.</dark_gray>",
            "<yellow>Click to send the link.</yellow>"
        )));

        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MainMenuHolder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory().getType() == InventoryType.PLAYER) {
            return;
        }

        switch (event.getRawSlot()) {
            case 19 -> openReliquary(player);
            case 20 -> openArmory(player);
            case 21 -> openMythics(player);
            case 23 -> openBossRituals(player);
            case 24 -> openEnchants(player);
            case 25 -> openBossBrews(player);
            case 30 -> openPowers(player);
            case 31 -> openTeamVault(player);
            case 32 -> openLeaderboards(player);
            case 53 -> {
                player.closeInventory();
                player.performCommand("wiki");
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MainMenuHolder) {
            event.setCancelled(true);
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
            player.sendMessage(MessageUtil.error("You do not have permission to view the Covenant Armory."));
            return;
        }
        if (plugin.getSeasonRelicManager() == null) {
            player.sendMessage(MessageUtil.error("The Covenant Armory is not ready yet."));
            return;
        }
        plugin.getSeasonRelicManager().openArmoryMenuFromMainMenu(player);
    }

    private void openBossRituals(Player player) {
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
        if (plugin.getSuperpowerManager() == null) {
            player.sendMessage(MessageUtil.error("Power info is not ready yet."));
            return;
        }
        plugin.getSuperpowerManager().openAdminInfoMenu(player);
    }

    private void openTeamVault(Player player) {
        if (!player.hasPermission("smpcore.team")) {
            player.sendMessage(MessageUtil.error("You do not have permission to use team vaults."));
            return;
        }
        plugin.getTeamManager().openTeamVault(player);
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

    private static void decorate(Inventory inventory) {
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack accent = pane(Material.CYAN_STAINED_GLASS_PANE);
        ItemStack warmAccent = pane(Material.ORANGE_STAINED_GLASS_PANE);
        int lastRow = (inventory.getSize() / 9) - 1;
        for (int i = 0; i < inventory.getSize(); i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == lastRow || col == 0 || col == 8) {
                inventory.setItem(i, filler);
            }
        }
        for (int slot : List.of(1, 7, 9, 17, 36, 44, 46, 52)) {
            inventory.setItem(slot, accent);
        }
        for (int slot : List.of(2, 6, 18, 26, 27, 35, 47, 51)) {
            inventory.setItem(slot, warmAccent);
        }
    }

    private static ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(name));
        meta.lore(lore.stream().map(MM::deserialize).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack label(Material material, String name, List<String> lore) {
        ItemStack item = button(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack pane(Material material) {
        return button(material, "<dark_gray> </dark_gray>", List.of());
    }

    private record MainMenuHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, SIZE, Component.empty());
        }
    }
}
