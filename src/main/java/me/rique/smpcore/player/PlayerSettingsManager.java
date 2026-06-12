package me.rique.smpcore.player;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerSettingsManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final int MENU_SIZE = 27;
    private static final int DROP_SAFETY_SLOT = 13;
    private static final int BACK_SLOT = 22;
    private static final long DROP_CONFIRM_WINDOW_MILLIS = 5_000L;

    private final SMPCore plugin;
    private final NamespacedKey keyDropSafety;
    private final Map<UUID, DropConfirm> pendingDropConfirms = new ConcurrentHashMap<>();

    public PlayerSettingsManager(SMPCore plugin) {
        this.plugin = plugin;
        this.keyDropSafety = new NamespacedKey(plugin, "setting_drop_safety");
    }

    public void shutdown() {
        pendingDropConfirms.clear();
    }

    public void openSettingsMenu(Player player, boolean fromMainMenu) {
        Inventory inventory = Bukkit.createInventory(
            new SettingsMenuHolder(fromMainMenu),
            MENU_SIZE,
            BedrockCompat.menuTitle(
                player,
                MM.deserialize("<gradient:#38bdf8:#facc15><bold>Player Settings</bold></gradient>"),
                "Player Settings"
            )
        );

        decorate(inventory);
        inventory.setItem(4, button(
            Material.COMPARATOR,
            "<gradient:#38bdf8:#facc15><bold>Player Settings</bold></gradient>",
            List.of(
                "<gray>Small quality-of-life toggles that only affect you.</gray>",
                "<dark_gray>More settings can live here later.</dark_gray>"
            )
        ));
        inventory.setItem(DROP_SAFETY_SLOT, dropSafetyItem(player));
        inventory.setItem(BACK_SLOT, button(
            fromMainMenu ? Material.ARROW : Material.BARRIER,
            fromMainMenu ? "<yellow><bold>Back</bold></yellow>" : "<red><bold>Close</bold></red>",
            List.of(fromMainMenu ? "<gray>Return to /menu.</gray>" : "<gray>Close this menu.</gray>")
        ));

        player.openInventory(inventory);
    }

    public boolean isDropSafetyEnabled(Player player) {
        if (player == null) {
            return true;
        }
        Byte value = player.getPersistentDataContainer().get(keyDropSafety, PersistentDataType.BYTE);
        return value == null || value != 0;
    }

    public void setDropSafetyEnabled(Player player, boolean enabled) {
        if (player == null) {
            return;
        }
        player.getPersistentDataContainer().set(keyDropSafety, PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
        pendingDropConfirms.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!isDropSafetyEnabled(player)) {
            return;
        }

        ItemStack dropped = event.getItemDrop().getItemStack();
        String category = protectedItemCategory(dropped);
        if (category == null) {
            return;
        }

        if (confirmDropOrPrompt(player, dropped, category)) {
            return;
        }

        event.setCancelled(true);
        restoreCancelledProtectedDrop(player, dropped.clone(), category);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingDropConfirms.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedInventoryDrop(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!isDropSafetyEnabled(player)) {
            return;
        }

        InventoryDropIntent intent = inventoryDropIntent(event);
        if (intent == null || intent.amount() <= 0 || intent.source() == null || intent.source().getType().isAir()) {
            return;
        }

        String category = protectedItemCategory(intent.source());
        if (category == null) {
            return;
        }

        event.setCancelled(true);
        if (!confirmDropOrPrompt(player, intent.source(), category)) {
            player.updateInventory();
            return;
        }

        completeProtectedInventoryDrop(player, intent, category);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSettingsClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SettingsMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory().getType() == InventoryType.PLAYER) {
            return;
        }

        if (event.getRawSlot() == DROP_SAFETY_SLOT) {
            boolean enabled = !isDropSafetyEnabled(player);
            setDropSafetyEnabled(player, enabled);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.45f, enabled ? 1.45f : 0.85f);
            player.sendActionBar(enabled
                ? MM.deserialize("<green>Drop safety enabled.</green>")
                : MM.deserialize("<yellow>Drop safety disabled.</yellow>")
            );
            openSettingsMenu(player, holder.fromMainMenu());
            return;
        }

        if (event.getRawSlot() == BACK_SLOT) {
            if (holder.fromMainMenu()) {
                me.rique.smpcore.command.MainMenuCommand.openMenu(plugin, player);
            } else {
                player.closeInventory();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSettingsDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SettingsMenuHolder) {
            event.setCancelled(true);
        }
    }

    private boolean confirmDropOrPrompt(Player player, ItemStack item, String category) {
        long now = System.currentTimeMillis();
        String fingerprint = fingerprint(item, category);
        DropConfirm previous = pendingDropConfirms.get(player.getUniqueId());
        if (previous != null && previous.expiresAt() >= now && previous.fingerprint().equals(fingerprint)) {
            pendingDropConfirms.remove(player.getUniqueId());
            player.sendActionBar(MessageUtil.parse(
                "<green>Drop confirmed.</green> <gray><item> was dropped.</gray>",
                MessageUtil.placeholder("item", itemName(item))
            ));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, 1.6f);
            return true;
        }

        pendingDropConfirms.put(player.getUniqueId(), new DropConfirm(fingerprint, now + DROP_CONFIRM_WINDOW_MILLIS));
        player.sendActionBar(MessageUtil.parse(
            "<yellow>Drop again within 5s to drop <item>.</yellow>",
            MessageUtil.placeholder("item", itemName(item))
        ));
        player.sendMessage(MessageUtil.prefixedRaw(
            "<yellow>Drop safety protected <item>. Drop it again within 5 seconds if you really want to drop it.</yellow>",
            MessageUtil.placeholder("item", itemName(item))
        ));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.45f, 1.25f);
        return false;
    }

    private InventoryDropIntent inventoryDropIntent(InventoryClickEvent event) {
        InventoryAction action = event.getAction();
        if (action == InventoryAction.DROP_ALL_CURSOR || action == InventoryAction.DROP_ONE_CURSOR) {
            ItemStack cursor = event.getCursor();
            if (cursor == null || cursor.getType().isAir()) {
                return null;
            }
            int amount = action == InventoryAction.DROP_ALL_CURSOR ? cursor.getAmount() : 1;
            return new InventoryDropIntent(cursor.clone(), amount, true, null, -1);
        }

        ClickType click = event.getClick();
        boolean slotDrop = action == InventoryAction.DROP_ALL_SLOT
            || action == InventoryAction.DROP_ONE_SLOT
            || click == ClickType.DROP
            || click == ClickType.CONTROL_DROP;
        if (!slotDrop || event.getClickedInventory() == null || clickedSmpCoreTopInventory(event)) {
            return null;
        }

        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) {
            return null;
        }
        int amount = action == InventoryAction.DROP_ALL_SLOT || click == ClickType.CONTROL_DROP ? current.getAmount() : 1;
        return new InventoryDropIntent(current.clone(), amount, false, event.getClickedInventory(), event.getSlot());
    }

    private boolean clickedSmpCoreTopInventory(InventoryClickEvent event) {
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || clicked != event.getView().getTopInventory()) {
            return false;
        }
        InventoryHolder holder = clicked.getHolder();
        return holder != null && holder.getClass().getName().startsWith("me.rique.smpcore.");
    }

    private void completeProtectedInventoryDrop(Player player, InventoryDropIntent intent, String category) {
        ItemStack current = intent.cursor()
            ? player.getItemOnCursor()
            : intent.inventory().getItem(intent.slot());
        if (!sameProtectedIdentity(current, intent.source(), category)) {
            player.sendActionBar(MM.deserialize("<red>Drop cancelled because that item changed.</red>"));
            player.updateInventory();
            return;
        }

        int amount = Math.min(intent.amount(), current.getAmount());
        if (amount <= 0) {
            player.updateInventory();
            return;
        }

        ItemStack drop = current.clone();
        drop.setAmount(amount);
        int remainingAmount = current.getAmount() - amount;
        ItemStack remaining = null;
        if (remainingAmount > 0) {
            remaining = current.clone();
            remaining.setAmount(remainingAmount);
        }

        if (intent.cursor()) {
            player.setItemOnCursor(remaining);
        } else {
            intent.inventory().setItem(intent.slot(), remaining);
        }
        player.getWorld().dropItemNaturally(player.getLocation().add(0.0, 0.35, 0.0), drop);
        player.updateInventory();
    }

    private void restoreCancelledProtectedDrop(Player player, ItemStack dropped, String category) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || protectedItemAmount(player, dropped, category) >= dropped.getAmount()) {
                return;
            }

            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(dropped.clone());
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            player.updateInventory();
            plugin.getLogger().warning("Restored protected drop item for " + player.getName() + " after cancellation did not return it: " + itemName(dropped));
        });
    }

    private int protectedItemAmount(Player player, ItemStack expected, String category) {
        int amount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (sameProtectedIdentity(item, expected, category)) {
                amount += item.getAmount();
            }
        }
        if (sameProtectedIdentity(player.getItemOnCursor(), expected, category)) {
            amount += player.getItemOnCursor().getAmount();
        }
        return amount;
    }

    private boolean sameProtectedIdentity(ItemStack current, ItemStack expected, String category) {
        if (current == null || expected == null || current.getType().isAir() || expected.getType().isAir()) {
            return false;
        }
        if (current.getType() != expected.getType()) {
            return false;
        }
        String currentCategory = protectedItemCategory(current);
        if (!category.equals(currentCategory)) {
            return false;
        }
        return identityFingerprint(current, category).equals(identityFingerprint(expected, category));
    }

    private ItemStack dropSafetyItem(Player player) {
        boolean enabled = isDropSafetyEnabled(player);
        return button(
            enabled ? Material.LIME_DYE : Material.GRAY_DYE,
            enabled ? "<green><bold>Drop Safety: ON</bold></green>" : "<red><bold>Drop Safety: OFF</bold></red>",
            List.of(
                "<gray>Protects custom, rare, and plugin-tracked items</gray>",
                "<gray>from accidental single-key drops.</gray>",
                "",
                enabled
                    ? "<green>Important items need two drop presses within 5s.</green>"
                    : "<yellow>Important items can be dropped normally.</yellow>",
                "<yellow>Click to toggle.</yellow>"
            )
        );
    }

    private String protectedItemCategory(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }

        if (plugin.getLegendaryListener() != null) {
            if (plugin.getLegendaryListener().isLegendaryItem(item)) return "legendary";
            if (plugin.getLegendaryListener().isEnderBoneItem(item)) return "ender_bone";
            if (plugin.getLegendaryListener().isOrbOfTheMysticsItem(item)) return "orb_of_the_mystics";
        }
        if (plugin.getBackpackListener() != null && plugin.getBackpackListener().isBackpack(item)) return "backpack";
        if (plugin.getCustomToolListener() != null && plugin.getCustomToolListener().isCustomTool(item)) return "custom_tool";
        if (plugin.getSeasonRelicManager() != null && plugin.getSeasonRelicManager().isSeasonRelic(item)) return "season_relic";
        if (plugin.getRewardLanternListener() != null && plugin.getRewardLanternListener().isRewardLantern(item)) return "reward_lantern";
        if (plugin.getSustenanceTalismanListener() != null && plugin.getSustenanceTalismanListener().isTalisman(item)) return "talisman";
        if (plugin.getSalvagingDepotListener() != null && plugin.getSalvagingDepotListener().isDepotItem(item)) return "salvaging_depot";
        if (plugin.getAgriculturalPylonListener() != null && plugin.getAgriculturalPylonListener().isPylonItem(item)) return "agricultural_pylon";
        if (plugin.getXpLecternListener() != null && plugin.getXpLecternListener().isLecternItem(item)) return "xp_lectern";
        if (plugin.getBossPotionListener() != null && plugin.getBossPotionListener().isBossPotion(item)) return "boss_potion";
        if (plugin.getAwakeningTableListener() != null) {
            if (plugin.getAwakeningTableListener().isAwakeningTableCustomItem(item)) return "awakening_table";
            if (plugin.getAwakeningTableListener().isAwakened(item)) return "awakened";
        }
        if (plugin.getMythicForgeListener() != null) {
            if (plugin.getMythicForgeListener().isMythicForgeItemStack(item)) return "mythic_forge";
            if (plugin.getMythicForgeListener().isAscendantCoreItem(item)) return "ascendant_core";
        }
        if (plugin.getBossManager() != null && plugin.getBossManager().isDominionCore(item)) return "dominion_core";
        if (plugin.getSuperpowerManager() != null) {
            if (plugin.getSuperpowerManager().isAncientScroll(item)) return "ancient_scroll";
            if (plugin.getSuperpowerManager().isWardenHeart(item)) return "warden_heart";
            if (plugin.getSuperpowerManager().isMotherNatureStick(item)) return "mother_nature_stick";
            if (plugin.getSuperpowerManager().isTheWorldClock(item)) return "the_world_clock";
            if (plugin.getSuperpowerManager().isDruidGrimoire(item)) return "druid_grimoire";
        }

        return hasSmpCoreData(item) ? "plugin_item" : null;
    }

    private boolean hasSmpCoreData(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().getKeys().stream()
            .anyMatch(key -> "smpcore".equalsIgnoreCase(key.getNamespace()));
    }

    private String fingerprint(ItemStack item, String category) {
        return identityFingerprint(item, category) + "|" + item.getAmount();
    }

    private String identityFingerprint(ItemStack item, String category) {
        ItemMeta meta = item.getItemMeta();
        List<String> keys = new ArrayList<>();
        if (meta != null) {
            meta.getPersistentDataContainer().getKeys().stream()
                .map(key -> key.getNamespace() + ":" + key.getKey())
                .sorted(Comparator.naturalOrder())
                .forEach(keys::add);
        }
        return category
            + "|" + item.getType().name()
            + "|" + itemName(item).toLowerCase(Locale.ROOT)
            + "|" + String.join(",", keys);
    }

    private String itemName(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "that item";
        }
        ItemMeta meta = item.getItemMeta();
        Component name = null;
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            name = meta.displayName();
        } else if (meta != null && meta.hasItemName() && meta.itemName() != null) {
            name = meta.itemName();
        }
        if (name != null) {
            String plain = PLAIN.serialize(name).trim();
            if (!plain.isBlank()) {
                return plain;
            }
        }
        String[] parts = item.getType().name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.isEmpty() ? "that item" : out.toString();
    }

    private static void decorate(Inventory inventory) {
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack accent = pane(Material.CYAN_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        for (int slot : List.of(1, 7, 9, 17, 19, 25)) {
            inventory.setItem(slot, accent);
        }
    }

    private static ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(MM.deserialize(name));
        meta.lore(lore.stream()
            .map(line -> line == null || line.isBlank() ? Component.empty() : MM.deserialize(line))
            .toList()
        );
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pane(Material material) {
        return button(material, "<dark_gray> </dark_gray>", List.of());
    }

    private record DropConfirm(String fingerprint, long expiresAt) {
    }

    private record InventoryDropIntent(ItemStack source, int amount, boolean cursor, Inventory inventory, int slot) {
    }

    private record SettingsMenuHolder(boolean fromMainMenu) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, MENU_SIZE, Component.empty());
        }
    }
}
