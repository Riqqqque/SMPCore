package me.rique.smpcore.player;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerSettingsManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final int MENU_SIZE = 36;
    private static final int DROP_SAFETY_SLOT = 11;
    private static final int SPAWN_MUSIC_SLOT = 13;
    private static final int BOSS_MUSIC_SLOT = 15;
    private static final int BACK_SLOT = 31;
    private static final long DROP_CONFIRM_WINDOW_MILLIS = 5_000L;
    private static final int PROTECTED_GROUND_PICKUP_DELAY_TICKS = 60;
    private static final long PROTECTED_GROUND_PICKUP_DELAY_MILLIS = PROTECTED_GROUND_PICKUP_DELAY_TICKS * 50L;
    private static final long PROTECTED_GROUND_LIFETIME_MILLIS = 15L * 60L * 1000L;

    private final SMPCore plugin;
    private final NamespacedKey keyDropSafety;
    private final NamespacedKey keySpawnMusic;
    private final NamespacedKey keyBossMusic;
    private final NamespacedKey keyDropSafetyGroundItem;
    private final NamespacedKey keyDropSafetyGroundExpiresAt;
    private final NamespacedKey keyDropSafetyGroundPickupUnlockAt;
    private final Map<UUID, DropConfirm> pendingDropConfirms = new ConcurrentHashMap<>();

    public PlayerSettingsManager(SMPCore plugin) {
        this.plugin = plugin;
        this.keyDropSafety = new NamespacedKey(plugin, "setting_drop_safety");
        this.keySpawnMusic = new NamespacedKey(plugin, "setting_spawn_music");
        this.keyBossMusic = new NamespacedKey(plugin, "setting_boss_music");
        this.keyDropSafetyGroundItem = new NamespacedKey(plugin, "drop_safety_ground_item");
        this.keyDropSafetyGroundExpiresAt = new NamespacedKey(plugin, "drop_safety_ground_expires_at");
        this.keyDropSafetyGroundPickupUnlockAt = new NamespacedKey(plugin, "drop_safety_ground_pickup_unlock_at");
        Bukkit.getScheduler().runTask(plugin, this::normalizeLoadedProtectedDrops);
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
                "<gray>Personal options saved to your player.</gray>",
                "<dark_gray>Changes apply immediately.</dark_gray>"
            )
        ));
        inventory.setItem(DROP_SAFETY_SLOT, dropSafetyItem(player));
        inventory.setItem(SPAWN_MUSIC_SLOT, spawnMusicItem(player));
        inventory.setItem(BOSS_MUSIC_SLOT, bossMusicItem(player));
        inventory.setItem(22, button(
            Material.REPEATER,
            "<aqua><bold>Music Controls</bold></aqua>",
            List.of(
                "<gray>Music toggles do not mute combat sounds,</gray>",
                "<gray>menu sounds, or the spawn soundscape.</gray>"
            )
        ));
        inventory.setItem(BACK_SLOT, button(
            fromMainMenu ? Material.ARROW : Material.BARRIER,
            fromMainMenu ? "<yellow><bold>Back</bold></yellow>" : "<red><bold>Close</bold></red>",
            List.of(fromMainMenu ? "<gray>Return to /menu.</gray>" : "<gray>Close this menu.</gray>")
        ));

        player.openInventory(inventory);
    }

    public boolean isDropSafetyEnabled(Player player) {
        return isSettingEnabled(player, keyDropSafety);
    }

    public void setDropSafetyEnabled(Player player, boolean enabled) {
        if (player == null) {
            return;
        }
        player.getPersistentDataContainer().set(keyDropSafety, PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
        pendingDropConfirms.remove(player.getUniqueId());
    }

    public boolean isSpawnMusicEnabled(Player player) {
        return isSettingEnabled(player, keySpawnMusic);
    }

    public void setSpawnMusicEnabled(Player player, boolean enabled) {
        setSettingEnabled(player, keySpawnMusic, enabled);
    }

    public boolean isBossMusicEnabled(Player player) {
        return isSettingEnabled(player, keyBossMusic);
    }

    public void setBossMusicEnabled(Player player, boolean enabled) {
        setSettingEnabled(player, keyBossMusic, enabled);
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

        int expectedAmountAfterRestore = protectedItemAmount(player, dropped, category) + dropped.getAmount();
        if (confirmDropOrPrompt(player, dropped, category)) {
            protectGroundDrop(player, event.getItemDrop());
            return;
        }

        event.setCancelled(true);
        restoreCancelledProtectedDrop(player, dropped.clone(), category, expectedAmountAfterRestore);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingDropConfirms.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickupWhileDropConfirmPending(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        DropConfirm pending = pendingDropConfirms.get(player.getUniqueId());
        if (pending == null) {
            return;
        }
        ItemStack pickedUp = event.getItem().getItemStack();
        if (sameProtectedIdentity(pickedUp, pending.item(), pending.category())) {
            pendingDropConfirms.remove(player.getUniqueId());
            player.sendActionBar(MM.deserialize("<yellow>Drop safety reset because that item came back.</yellow>"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDropSafetyPlayerPickup(EntityPickupItemEvent event) {
        Item item = event.getItem();
        if (!isActiveDropSafetyGroundItem(item)) {
            return;
        }
        Long unlockAt = item.getPersistentDataContainer().get(keyDropSafetyGroundPickupUnlockAt, PersistentDataType.LONG);
        if (protectedGroundPickupLocked(unlockAt, System.currentTimeMillis())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDropSafetyItemMerge(ItemMergeEvent event) {
        if (isActiveDropSafetyGroundItem(event.getEntity()) || isActiveDropSafetyGroundItem(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDropSafetyHopperPickup(InventoryPickupItemEvent event) {
        if (isActiveDropSafetyGroundItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDropSafetyItemDespawn(ItemDespawnEvent event) {
        Item item = event.getEntity();
        if (!isActiveDropSafetyGroundItem(item)) {
            return;
        }

        event.setCancelled(true);
        item.setTicksLived(1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (org.bukkit.entity.Entity entity : event.getEntities()) {
            if (entity instanceof Item item) {
                normalizeProtectedDrop(item);
            }
        }
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
        ItemStack confirmStack = intent.source().clone();
        confirmStack.setAmount(intent.amount());
        if (!confirmDropOrPrompt(player, confirmStack, category)) {
            player.updateInventory();
            return;
        }

        completeProtectedInventoryDrop(player, intent, category);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSettingsClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof SettingsMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return;
        }
        if (!MenuItemUtil.isVisibleItem(event.getCurrentItem())) {
            return;
        }

        if (rawSlot == DROP_SAFETY_SLOT) {
            Bukkit.getScheduler().runTask(plugin, () -> toggleDropSafetyFromMenu(player, holder.fromMainMenu()));
            return;
        }
        if (rawSlot == SPAWN_MUSIC_SLOT) {
            Bukkit.getScheduler().runTask(plugin, () -> toggleSpawnMusicFromMenu(player, holder.fromMainMenu()));
            return;
        }
        if (rawSlot == BOSS_MUSIC_SLOT) {
            Bukkit.getScheduler().runTask(plugin, () -> toggleBossMusicFromMenu(player, holder.fromMainMenu()));
            return;
        }

        if (rawSlot == BACK_SLOT) {
            Bukkit.getScheduler().runTask(plugin, () -> closeSettingsMenu(player, holder.fromMainMenu()));
        }
    }

    private void toggleDropSafetyFromMenu(Player player, boolean fromMainMenu) {
        if (!player.isOnline()) {
            return;
        }
        boolean enabled = !isDropSafetyEnabled(player);
        setDropSafetyEnabled(player, enabled);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.45f, enabled ? 1.45f : 0.85f);
        player.sendActionBar(enabled
            ? MM.deserialize("<green>Drop safety enabled.</green>")
            : MM.deserialize("<yellow>Drop safety disabled.</yellow>")
        );
        openSettingsMenu(player, fromMainMenu);
    }

    private void toggleSpawnMusicFromMenu(Player player, boolean fromMainMenu) {
        if (!player.isOnline()) {
            return;
        }
        boolean enabled = !isSpawnMusicEnabled(player);
        setSpawnMusicEnabled(player, enabled);
        if (plugin.getSpawnAmbienceManager() != null) {
            plugin.getSpawnAmbienceManager().onMusicPreferenceChanged(player, enabled);
        }
        confirmMusicToggle(player, "Spawn music", enabled);
        openSettingsMenu(player, fromMainMenu);
    }

    private void toggleBossMusicFromMenu(Player player, boolean fromMainMenu) {
        if (!player.isOnline()) {
            return;
        }
        boolean enabled = !isBossMusicEnabled(player);
        setBossMusicEnabled(player, enabled);
        if (plugin.getBossMusicManager() != null) {
            plugin.getBossMusicManager().onMusicPreferenceChanged(player, enabled);
        }
        confirmMusicToggle(player, "Boss music", enabled);
        openSettingsMenu(player, fromMainMenu);
    }

    private void confirmMusicToggle(Player player, String label, boolean enabled) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.45f, enabled ? 1.45f : 0.85f);
        player.sendActionBar(MM.deserialize(enabled
            ? "<green>" + label + " enabled.</green>"
            : "<yellow>" + label + " muted.</yellow>"
        ));
    }

    private void closeSettingsMenu(Player player, boolean fromMainMenu) {
        if (!player.isOnline()) {
            return;
        }
        if (fromMainMenu) {
            me.rique.smpcore.command.MainMenuCommand.openMenu(plugin, player);
        } else {
            player.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSettingsDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof SettingsMenuHolder) {
            event.setCancelled(true);
        }
    }

    private boolean confirmDropOrPrompt(Player player, ItemStack item, String category) {
        long now = System.currentTimeMillis();
        DropConfirm previous = pendingDropConfirms.get(player.getUniqueId());
        if (previous != null
            && previous.expiresAt() >= now
            && previous.amount() == item.getAmount()
            && previous.category().equals(category)
            && sameProtectedIdentity(item, previous.item(), category)) {
            pendingDropConfirms.remove(player.getUniqueId());
            player.sendActionBar(MessageUtil.parse(
                "<green>Drop confirmed.</green> <gray><item> was dropped.</gray>",
                MessageUtil.placeholder("item", itemName(item))
            ));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, 1.6f);
            return true;
        }

        pendingDropConfirms.put(player.getUniqueId(), new DropConfirm(category, item.clone(), item.getAmount(), now + DROP_CONFIRM_WINDOW_MILLIS));
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
        dropProtectedItem(player, drop);
        player.updateInventory();
    }

    private void restoreCancelledProtectedDrop(Player player, ItemStack dropped, String category, int expectedAmountAfterRestore) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            int missing = expectedAmountAfterRestore - protectedItemAmount(player, dropped, category);
            if (missing <= 0) {
                return;
            }

            ItemStack restore = dropped.clone();
            restore.setAmount(Math.min(missing, dropped.getAmount()));
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(restore);
            leftovers.values().forEach(leftover -> dropProtectedItem(player, leftover));
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
        return current.isSimilar(expected);
    }

    private void dropProtectedItem(Player player, ItemStack drop) {
        Item item = player.getWorld().dropItemNaturally(player.getLocation().add(0.0, 0.35, 0.0), drop);
        protectGroundDrop(player, item);
    }

    private void protectGroundDrop(Player player, Item item) {
        if (item == null || !item.isValid() || item.isDead() || protectedItemCategory(item.getItemStack()) == null) {
            return;
        }
        item.getPersistentDataContainer().set(keyDropSafetyGroundItem, PersistentDataType.BYTE, (byte) 1);
        if (!item.getPersistentDataContainer().has(keyDropSafetyGroundExpiresAt, PersistentDataType.LONG)) {
            item.getPersistentDataContainer().set(
                keyDropSafetyGroundExpiresAt,
                PersistentDataType.LONG,
                System.currentTimeMillis() + PROTECTED_GROUND_LIFETIME_MILLIS
            );
        }
        item.setUnlimitedLifetime(false);
        item.setWillAge(true);
        item.setCanMobPickup(false);
        applyProtectedGroundPickupLock(item, System.currentTimeMillis());
        if (player != null) {
            item.setThrower(player.getUniqueId());
        }
    }

    private boolean isDropSafetyGroundItem(Item item) {
        Byte marker = item.getPersistentDataContainer().get(keyDropSafetyGroundItem, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private boolean isActiveDropSafetyGroundItem(Item item) {
        if (!isDropSafetyGroundItem(item)) {
            return false;
        }
        Long expiresAt = item.getPersistentDataContainer().get(keyDropSafetyGroundExpiresAt, PersistentDataType.LONG);
        if (expiresAt != null && expiresAt > System.currentTimeMillis()) {
            return true;
        }
        if (expiresAt == null) {
            normalizeProtectedDrop(item);
            return true;
        }
        clearGroundProtection(item);
        return false;
    }

    private void normalizeLoadedProtectedDrops() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                normalizeProtectedDrop(item);
            }
        }
    }

    private void normalizeProtectedDrop(Item item) {
        if (item == null || !item.isValid() || item.isDead() || !isDropSafetyGroundItem(item)) {
            return;
        }
        Long expiresAt = item.getPersistentDataContainer().get(keyDropSafetyGroundExpiresAt, PersistentDataType.LONG);
        if (expiresAt != null && expiresAt <= System.currentTimeMillis()) {
            clearGroundProtection(item);
            item.remove();
            return;
        }
        if (expiresAt == null) {
            item.getPersistentDataContainer().set(
                keyDropSafetyGroundExpiresAt,
                PersistentDataType.LONG,
                System.currentTimeMillis() + PROTECTED_GROUND_LIFETIME_MILLIS
            );
            item.setTicksLived(1);
        }
        item.setUnlimitedLifetime(false);
        item.setWillAge(true);
        item.setCanMobPickup(false);
        applyProtectedGroundPickupLock(item, System.currentTimeMillis());
    }

    private void clearGroundProtection(Item item) {
        item.getPersistentDataContainer().remove(keyDropSafetyGroundItem);
        item.getPersistentDataContainer().remove(keyDropSafetyGroundExpiresAt);
        item.getPersistentDataContainer().remove(keyDropSafetyGroundPickupUnlockAt);
        item.setUnlimitedLifetime(false);
        item.setWillAge(true);
        item.setCanMobPickup(true);
    }

    private void applyProtectedGroundPickupLock(Item item, long now) {
        Long unlockAt = item.getPersistentDataContainer().get(keyDropSafetyGroundPickupUnlockAt, PersistentDataType.LONG);
        if (unlockAt == null) {
            unlockAt = now + PROTECTED_GROUND_PICKUP_DELAY_MILLIS;
            item.getPersistentDataContainer().set(keyDropSafetyGroundPickupUnlockAt, PersistentDataType.LONG, unlockAt);
        }
        int requiredDelay = protectedGroundPickupDelayTicks(item.getPickupDelay(), unlockAt, now);
        if (requiredDelay > item.getPickupDelay()) {
            item.setPickupDelay(requiredDelay);
        }
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

    private ItemStack spawnMusicItem(Player player) {
        boolean enabled = isSpawnMusicEnabled(player);
        return button(
            enabled ? Material.MUSIC_DISC_OTHERSIDE : Material.MUSIC_DISC_11,
            enabled ? "<green><bold>Spawn Music: ON</bold></green>" : "<red><bold>Spawn Music: OFF</bold></red>",
            List.of(
                "<gray>Controls the Veilward spawn theme.</gray>",
                "<gray>Other spawn ambience stays active.</gray>",
                "",
                enabled ? "<green>The theme can play for you.</green>" : "<yellow>The theme is muted for you.</yellow>",
                "<yellow>Click to toggle.</yellow>"
            )
        );
    }

    private ItemStack bossMusicItem(Player player) {
        boolean enabled = isBossMusicEnabled(player);
        return button(
            enabled ? Material.MUSIC_DISC_PIGSTEP : Material.MUSIC_DISC_11,
            enabled ? "<green><bold>Boss Music: ON</bold></green>" : "<red><bold>Boss Music: OFF</bold></red>",
            List.of(
                "<gray>Controls music during boss fights.</gray>",
                "<gray>Warnings and mechanic sounds stay active.</gray>",
                "",
                enabled ? "<green>Boss themes can play for you.</green>" : "<yellow>Boss themes are muted for you.</yellow>",
                "<yellow>Click to toggle.</yellow>"
            )
        );
    }

    private boolean isSettingEnabled(Player player, NamespacedKey key) {
        if (player == null) {
            return true;
        }
        Byte value = player.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return storedToggleEnabled(value);
    }

    private void setSettingEnabled(Player player, NamespacedKey key, boolean enabled) {
        if (player != null) {
            player.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
        }
    }

    static boolean storedToggleEnabled(Byte value) {
        return value == null || value != 0;
    }

    static boolean protectedGroundPickupLocked(Long unlockAt, long now) {
        return unlockAt != null && unlockAt > now;
    }

    static int protectedGroundPickupDelayTicks(int currentDelay, Long unlockAt, long now) {
        if (!protectedGroundPickupLocked(unlockAt, now)) {
            return Math.max(0, currentDelay);
        }
        long remainingMillis = unlockAt - now;
        long remainingTicks = (remainingMillis + 49L) / 50L;
        return (int) Math.max(Math.max(0, currentDelay), Math.min(Integer.MAX_VALUE, remainingTicks));
    }

    private String protectedItemCategory(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }

        // Biscuit's marked stick is intentionally dropped to start fetch. The fetch
        // manager owns its one-use token and cleanup, so the general drop guard must
        // not turn the throw into a two-step confirmation.
        if (plugin.getSpawnLifeManager() != null && plugin.getSpawnLifeManager().isFetchStick(item)) {
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
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isFrameSlot(slot, inventory.getSize())) {
                inventory.setItem(slot, filler);
            }
        }
    }

    private static boolean isFrameSlot(int slot, int size) {
        return slot < 9 || slot >= size - 9 || slot % 9 == 0 || slot % 9 == 8;
    }

    private static ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(MM.deserialize(MenuItemUtil.visibleMiniName(name)));
        meta.lore(MenuItemUtil.visibleMiniLore(name, lore).stream()
            .map(line -> line == null || line.isBlank() ? Component.empty() : MM.deserialize(line))
            .toList()
        );
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pane(Material material) {
        return button(material, MenuItemUtil.INACTIVE_SLOT_NAME, MenuItemUtil.INACTIVE_SLOT_LORE);
    }

    private record DropConfirm(String category, ItemStack item, int amount, long expiresAt) {
    }

    private record InventoryDropIntent(ItemStack source, int amount, boolean cursor, Inventory inventory, int slot) {
    }

    private record SettingsMenuHolder(boolean fromMainMenu) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
