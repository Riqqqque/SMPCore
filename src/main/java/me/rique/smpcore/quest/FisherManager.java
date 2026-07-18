package me.rique.smpcore.quest;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.InventoryRecipeUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FisherManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int CLOSE_SLOT = 26;
    private static final List<FishingStage> STAGES = List.of(
        new FishingStage("Shallow Water", Material.COD, 5, "Catch cod beyond the shallows."),
        new FishingStage("Red Fins", Material.SALMON, 3, "Catch salmon for the smokehouse."),
        new FishingStage("Careful Hands", Material.PUFFERFISH, 2, "Catch pufferfish without getting stung.")
    );

    private final SMPCore plugin;
    private final NamespacedKey boatClaimedKey;
    private final NamespacedKey launchBoatKey;
    private final NamespacedKey questStageKey;
    private final NamespacedKey questProgressKey;
    private final NamespacedKey rewardRodKey;

    public FisherManager(SMPCore plugin) {
        this.plugin = plugin;
        this.boatClaimedKey = new NamespacedKey(plugin, "fisher_boat_claimed");
        this.launchBoatKey = new NamespacedKey(plugin, "fisher_launch_boat");
        this.questStageKey = new NamespacedKey(plugin, "fisher_quest_stage");
        this.questProgressKey = new NamespacedKey(plugin, "fisher_quest_progress");
        this.rewardRodKey = new NamespacedKey(plugin, "fisher_reward_rod");
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof FisherMenuHolder) {
                player.closeInventory();
            }
        }
    }

    public void openFromFisher(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        grantFirstBoat(player);
        openQuestMenu(player);
    }

    private void grantFirstBoat(Player player) {
        if (hasClaimedBoat(player)) {
            return;
        }
        player.getPersistentDataContainer().set(boatClaimedKey, PersistentDataType.BYTE, (byte) 1);
        InventoryRecipeUtil.giveOrDrop(player, createLaunchBoat());
        player.sendMessage(MessageUtil.info("Corin: Heading out? Take this boat. It should get you beyond the shallows."));
        player.sendMessage(MessageUtil.info("Corin: Bring back a few proper catches and I'll set you up with a better rod."));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8F, 1.0F);
    }

    private void openQuestMenu(Player player) {
        int stage = questStage(player);
        int progress = questProgress(player, stage);
        Inventory menu = Bukkit.createInventory(
            new FisherMenuHolder(player.getUniqueId()),
            27,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#38bdf8:#facc15><bold>Corin the Fisher</bold></gradient>"), "Corin the Fisher")
        );
        fill(menu);
        menu.setItem(4, item(
            Material.FISHING_ROD,
            "<aqua><bold>A Few Proper Catches</bold></aqua>",
            List.of(
                "<gray>Catch each fish while fishing.</gray>",
                "<gray>Your catches stay in your inventory.</gray>",
                "<gray>Finish all three jobs for Corin's rod.</gray>"
            )
        ));
        menu.setItem(10, item(
            Material.OAK_BOAT,
            "<gold><bold>Free Boat</bold></gold>",
            List.of(
                "<gray>Corin gives every player one boat.</gray>",
                "<green>Your boat has been claimed.</green>"
            )
        ));

        int[] slots = {12, 14, 16};
        for (int index = 0; index < STAGES.size(); index++) {
            FishingStage definition = STAGES.get(index);
            boolean complete = index < stage;
            boolean current = index == stage;
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + definition.description() + "</gray>");
            lore.add("<gray>Need: <white>" + definition.required() + " " + pretty(definition.material()) + "</white>.</gray>");
            if (complete) {
                lore.add("<green>Complete.</green>");
            } else if (current) {
                lore.add("<yellow>Progress: " + progress + "/" + definition.required() + ".</yellow>");
            } else {
                lore.add("<dark_gray>Finish the earlier catch first.</dark_gray>");
            }
            menu.setItem(slots[index], item(
                complete ? Material.LIME_STAINED_GLASS_PANE : current ? definition.material() : Material.GRAY_STAINED_GLASS_PANE,
                complete
                    ? "<green><bold>" + definition.title() + "</bold></green>"
                    : current ? "<aqua><bold>" + definition.title() + "</bold></aqua>" : "<dark_gray><bold>Locked</bold></dark_gray>",
                lore
            ));
        }

        boolean finished = stage >= STAGES.size();
        menu.setItem(22, item(
            Material.FISHING_ROD,
            finished ? "<green><bold>Shoreline Companion</bold></green>" : "<yellow><bold>Final Reward</bold></yellow>",
            List.of(
                "<gray>Luck of the Sea I</gray>",
                "<gray>Lure I</gray>",
                "<gray>Unbreaking II</gray>",
                finished ? "<green>Earned.</green>" : "<yellow>Complete all three catches.</yellow>"
            )
        ));
        menu.setItem(CLOSE_SLOT, item(Material.BARRIER, "<red><bold>Close</bold></red>", List.of("<gray>Close this menu.</gray>")));
        player.openInventory(menu);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFishCaught(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item caught)) {
            return;
        }
        Player player = event.getPlayer();
        if (!hasClaimedBoat(player)) {
            return;
        }
        int stage = questStage(player);
        Material material = caught.getItemStack().getType();
        if (!countsForStage(stage, material)) {
            return;
        }

        FishingStage definition = STAGES.get(stage);
        int progress = Math.min(definition.required(), questProgress(player, stage) + 1);
        if (progress < definition.required()) {
            player.getPersistentDataContainer().set(questProgressKey, PersistentDataType.INTEGER, progress);
            player.sendActionBar(MM.deserialize(
                "<aqua>" + definition.title() + ": <white>" + progress + "/" + definition.required() + "</white></aqua>"
            ));
            return;
        }

        int nextStage = stage + 1;
        player.getPersistentDataContainer().set(questStageKey, PersistentDataType.INTEGER, nextStage);
        player.getPersistentDataContainer().remove(questProgressKey);
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.9F, 1.2F);
        player.sendMessage(MessageUtil.success("Fishing job complete: <white>" + definition.title() + "</white>."));
        if (plugin.getStoryService() != null) {
            plugin.getStoryService().onQuestStage(player, "fisher", definition.title(), nextStage);
        }

        if (nextStage >= STAGES.size()) {
            grantRewardRod(player);
        } else {
            FishingStage next = STAGES.get(nextStage);
            player.sendMessage(MessageUtil.info("Corin: Good catch. Next, see if you can land " + next.required() + " " + pretty(next.material()).toLowerCase() + "."));
        }
    }

    private void grantRewardRod(Player player) {
        if (hasRewardRod(player)) {
            return;
        }
        player.getPersistentDataContainer().set(rewardRodKey, PersistentDataType.BYTE, (byte) 1);
        InventoryRecipeUtil.giveOrDrop(player, createRewardRod());
        player.sendMessage(MessageUtil.info("Corin: Nice work. That rod is yours. Don't lose it overboard."));
        player.sendMessage(MessageUtil.success("You received <white>Shoreline Companion</white>."));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9F, 1.15F);
    }

    private ItemStack createRewardRod() {
        ItemStack rod = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = rod.getItemMeta();
        meta.displayName(MM.deserialize("<gradient:#38bdf8:#fde68a><bold>Shoreline Companion</bold></gradient>")
            .decoration(TextDecoration.ITALIC, false));
        meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
        meta.addEnchant(Enchantment.LURE, 1, true);
        meta.addEnchant(Enchantment.UNBREAKING, 2, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.FISHING_ROD,
            "UNCOMMON",
            "FISHING ROD",
            List.of("<gray>A dependable rod from Corin.</gray>"),
            List.of(CustomLoreUtil.section(
                "Enchantments",
                "Coastal Kit",
                "<gray>Luck of the Sea I, Lure I</gray>",
                "<gray>and Unbreaking II.</gray>"
            ))
        ));
        meta.getPersistentDataContainer().set(rewardRodKey, PersistentDataType.BYTE, (byte) 1);
        rod.setItemMeta(meta);
        return rod;
    }

    private ItemStack createLaunchBoat() {
        ItemStack boat = new ItemStack(Material.OAK_BOAT);
        ItemMeta meta = boat.getItemMeta();
        meta.displayName(MM.deserialize("<gold><bold>Corin's Boat</bold></gold>").decoration(TextDecoration.ITALIC, false));
        meta.lore(CustomLoreUtil.wrapMiniMessageLines(List.of(
            "<gray>A one-time boat for leaving Veilward.</gray>",
            "<dark_gray>Can launch by Corin's beach.</dark_gray>"
        )).stream().map(line -> MM.deserialize(line).decoration(TextDecoration.ITALIC, false)).toList());
        meta.getPersistentDataContainer().set(launchBoatKey, PersistentDataType.BYTE, (byte) 1);
        boat.setItemMeta(meta);
        return boat;
    }

    public boolean canLaunchAtFisherBeach(Player player, ItemStack item, org.bukkit.Location location) {
        if (player == null || location == null || location.getWorld() == null || !isLaunchBoat(item)
            || plugin.getGuideNpcManager() == null) {
            return false;
        }
        return plugin.getGuideNpcManager().nearestLoadedNpc(
            me.rique.smpcore.npc.GuideNpcManager.GuideNpcType.FISHER,
            location,
            18.0D
        ) != null;
    }

    private boolean isLaunchBoat(ItemStack item) {
        if (item == null || item.getType() != Material.OAK_BOAT || !item.hasItemMeta()) {
            return false;
        }
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(launchBoatKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof FisherMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.playerId().equals(player.getUniqueId())) {
            return;
        }
        if ((event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT)
            && event.getRawSlot() == CLOSE_SLOT
            && MenuItemUtil.isVisibleItem(event.getCurrentItem())) {
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof FisherMenuHolder) {
            event.setCancelled(true);
        }
    }

    private boolean hasClaimedBoat(Player player) {
        Byte value = player.getPersistentDataContainer().get(boatClaimedKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private boolean hasRewardRod(Player player) {
        Byte value = player.getPersistentDataContainer().get(rewardRodKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private int questStage(Player player) {
        Integer value = player.getPersistentDataContainer().get(questStageKey, PersistentDataType.INTEGER);
        return Math.clamp(value == null ? 0 : value, 0, STAGES.size());
    }

    private int questProgress(Player player, int stage) {
        if (stage < 0 || stage >= STAGES.size()) {
            return 0;
        }
        Integer value = player.getPersistentDataContainer().get(questProgressKey, PersistentDataType.INTEGER);
        return Math.clamp(value == null ? 0 : value, 0, STAGES.get(stage).required());
    }

    static boolean countsForStage(int stage, Material material) {
        return stage >= 0 && stage < STAGES.size() && material == STAGES.get(stage).material();
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(MenuItemUtil.visibleMiniName(name)).decoration(TextDecoration.ITALIC, false));
        List<Component> wrappedLore = MenuItemUtil.visibleMiniLore(name, CustomLoreUtil.wrapMiniMessageLines(lore)).stream()
            .map(line -> line == null || line.isBlank() ? Component.empty() : MM.deserialize(line).decoration(TextDecoration.ITALIC, false))
            .toList();
        meta.lore(wrappedLore);
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, MenuItemUtil.INACTIVE_SLOT_LORE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private String pretty(Material material) {
        String value = material.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private record FishingStage(String title, Material material, int required, String description) {
    }

    private record FisherMenuHolder(UUID playerId) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
