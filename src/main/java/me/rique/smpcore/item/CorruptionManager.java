package me.rique.smpcore.item;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.util.VisualRangeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class CorruptionManager implements Listener {

    public static final String STATION_ITEM_ID = "corruption_anchor";

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Material STATION_BLOCK_TYPE = Material.RESPAWN_ANCHOR;
    private static final int MENU_SIZE = 36;
    private static final int INFO_SLOT = 4;
    private static final int ITEM_LABEL_SLOT = 11;
    private static final int CATALYST_LABEL_SLOT = 15;
    private static final int FLOW_SLOT = 22;
    private static final int ITEM_SLOT = 20;
    private static final int ESSENCE_SLOT = 24;
    private static final int START_SLOT = 31;
    private static final long ANIMATION_TICKS = 100L;
    private static final double PLAYER_BASE_ATTACK_DAMAGE = 1.0;
    private static final double PLAYER_BASE_ATTACK_SPEED = 4.0;
    private static final int CORRUPTION_SCHEMA_VERSION = 2;
    private static final Set<String> CORRUPTION_LINE_PREFIXES = Set.of(
        "Corruption:",
        "Corrupted:",
        "Stats:",
        "Final Stats:",
        "Final:",
        "Attack Damage:",
        "Attack Speed:",
        "Attack Knockback:",
        "Armor:",
        "Armor Toughness:",
        "Projectile Damage:",
        "Durability:",
        "Seal:",
        "Sealed:"
    );
    private static final String CORRUPTION_STONE_ID = "corruption_stone";
    private static final String CORRUPTION_STONE_LIMIT_MESSAGE = "Corruption Stones cannot be used on legendary or mythic items.";
    private static final Float DRAGON_BREATH_PARTICLE_DATA = Float.valueOf(1.0f);

    private final SMPCore plugin;
    private final NamespacedKey keyStationItem;
    private final NamespacedKey keyCorruptionLocked;
    private final NamespacedKey keyCorruptionResult;
    private final NamespacedKey keyCorruptionFactor;
    private final NamespacedKey keyCorruptionBaseName;
    private final NamespacedKey keyCorruptionSchema;
    private final NamespacedKey keyCorruptionDeliveryId;
    private final NamespacedKey keyProjectileFactor;
    private final NamespacedKey keyHologram;
    private final NamespacedKey keyHologramBlock;
    private final Set<BlockKey> stations = ConcurrentHashMap.newKeySet();
    private final Map<String, UUID> hologramsByBlock = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveCorruption> activeCorruptions = new ConcurrentHashMap<>();
    private final File stationFile;
    private final File pendingFile;
    private BukkitTask stationTask;

    public CorruptionManager(SMPCore plugin) {
        this.plugin = plugin;
        this.keyStationItem = new NamespacedKey(plugin, STATION_ITEM_ID);
        this.keyCorruptionLocked = new NamespacedKey(plugin, "corruption_locked");
        this.keyCorruptionResult = new NamespacedKey(plugin, "corruption_result");
        this.keyCorruptionFactor = new NamespacedKey(plugin, "corruption_factor");
        this.keyCorruptionBaseName = new NamespacedKey(plugin, "corruption_base_name");
        this.keyCorruptionSchema = new NamespacedKey(plugin, "corruption_schema");
        this.keyCorruptionDeliveryId = new NamespacedKey(plugin, "corruption_delivery_id");
        this.keyProjectileFactor = new NamespacedKey(plugin, "corruption_projectile_factor");
        this.keyHologram = new NamespacedKey(plugin, "corruption_hologram");
        this.keyHologramBlock = new NamespacedKey(plugin, "corruption_hologram_block");
        this.stationFile = new File(plugin.getDataFolder(), "corruption-stations.yml");
        this.pendingFile = new File(plugin.getDataFolder(), "corruption-pending.yml");
    }

    public void start() {
        loadStations();
        Bukkit.getScheduler().runTask(plugin, () -> {
            syncLoadedStations();
            for (Player player : Bukkit.getOnlinePlayers()) {
                restoreInterruptedOrCompleted(player);
            }
        });
        stationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::syncLoadedStations, 100L, 400L);
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder(false) instanceof CorruptionMenuHolder) {
                if (activeCorruptions.containsKey(player.getUniqueId())) {
                    returnSlot(player, top, ESSENCE_SLOT);
                } else {
                    returnMenuInputs(player, top);
                }
                player.closeInventory();
            }
        }
        for (ActiveCorruption active : new ArrayList<>(activeCorruptions.values())) {
            if (active.task != null) {
                active.task.cancel();
            }
        }
        activeCorruptions.clear();
        if (stationTask != null) {
            stationTask.cancel();
            stationTask = null;
        }
        for (UUID displayId : new ArrayList<>(hologramsByBlock.values())) {
            Entity entity = Bukkit.getEntity(displayId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        hologramsByBlock.clear();
        saveStations();
    }

    public ItemStack createStationItem() {
        ItemStack item = new ItemStack(STATION_BLOCK_TYPE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.MYTHIC, "Corruption Anchor"));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            STATION_BLOCK_TYPE,
            CustomLoreUtil.Rarity.MYTHIC.label(),
            "CORRUPTION STATION",
            List.of(
                "<gray>Place this in spawn to corrupt items.</gray>",
                "<gray>Consumes <white>1 corruption catalyst</white> per attempt.</gray>"
            ),
            List.of(CustomLoreUtil.section(
                "Risk",
                "Two Catalysts",
                "<gray>Essence can empower, weaken, or destroy.</gray>",
                "<gray>Stone can x2 or seal unchanged.</gray>",
                "<dark_gray>Admin placed only.</dark_gray>"
            ))
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(keyStationItem, PersistentDataType.STRING, STATION_ITEM_ID);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isStationItem(ItemStack item) {
        if (item == null || item.getType() != STATION_BLOCK_TYPE) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && STATION_ITEM_ID.equals(meta.getPersistentDataContainer().get(keyStationItem, PersistentDataType.STRING));
    }

    public boolean isCorruptionLocked(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(keyCorruptionLocked, PersistentDataType.BYTE);
    }

    public String corruptionDisplayName(ItemStack item) {
        if (!isCorruptionLocked(item)) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        String result = meta.getPersistentDataContainer().get(keyCorruptionResult, PersistentDataType.STRING);
        CorruptionOutcome outcome = CorruptionOutcome.fromId(result);
        return outcome == null ? "Corrupted Item" : outcome.publicLabel;
    }

    public List<BlockKey> stationLocations() {
        return List.copyOf(stations);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStationPlace(BlockPlaceEvent event) {
        if (!isStationItem(event.getItemInHand())) {
            return;
        }
        if (!event.getPlayer().hasPermission("smpcore.corruption.admin")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.warn("Only admins can place Corruption Anchors."));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> registerStation(event.getBlockPlaced()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStationBreak(BlockBreakEvent event) {
        if (!isStationBlock(event.getBlock())) {
            return;
        }
        if (!event.getPlayer().hasPermission("smpcore.corruption.admin")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.warn("Only admins can break Corruption Anchors."));
            return;
        }
        event.setDropItems(false);
        Block block = event.getBlock();
        BlockKey key = BlockKey.from(block);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isStationBlock(block) || !stations.contains(key)) {
                return;
            }
            unregisterStation(block);
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.6, 0.5), createStationItem());
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isStationBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isStationBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStationInteract(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
            || event.getAction() != Action.RIGHT_CLICK_BLOCK
            || event.getClickedBlock() == null
            || !isStationBlock(event.getClickedBlock())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("smpcore.corruption.use")) {
            player.sendMessage(MessageUtil.warn("You cannot use this."));
            return;
        }
        if (activeCorruptions.containsKey(player.getUniqueId()) || hasPendingEntry(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("Your corruption attempt is still being handled."));
            return;
        }
        openMenu(player, BlockKey.from(event.getClickedBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof CorruptionMenuHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        if (!holder.playerId().equals(player.getUniqueId())) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (activeCorruptions.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            player.updateInventory();
            return;
        }

        int rawSlot = event.getRawSlot();
        boolean clickedTop = rawSlot >= 0 && rawSlot < top.getSize();
        if (clickedTop) {
            handleTopClick(event, player, top, rawSlot, holder.station());
            return;
        }
        if (event.isShiftClick()) {
            event.setCancelled(true);
            shiftMoveIntoMenu(player, top, event);
            return;
        }
        if (event.getClick() == ClickType.DOUBLE_CLICK || event.getClick().isCreativeAction()) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> sanitizeAndRefresh(player, top));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> sanitizeAndRefresh(player, top));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof CorruptionMenuHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    Bukkit.getScheduler().runTask(plugin, () -> sanitizeAndRefresh(player, event.getView().getTopInventory()));
                }
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof CorruptionMenuHolder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (activeCorruptions.containsKey(player.getUniqueId())) {
            returnSlot(player, event.getInventory(), ESSENCE_SLOT);
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
            return;
        }
        returnMenuInputs(player, event.getInventory());
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Inventory top = event.getEntity().getOpenInventory().getTopInventory();
        if (!(top.getHolder(false) instanceof CorruptionMenuHolder)) {
            return;
        }
        UUID playerId = event.getEntity().getUniqueId();
        if (activeCorruptions.containsKey(playerId)) {
            evacuateDeathInput(top, event.getDrops(), ESSENCE_SLOT);
        } else {
            evacuateDeathInput(top, event.getDrops(), ITEM_SLOT);
            evacuateDeathInput(top, event.getDrops(), ESSENCE_SLOT);
        }
    }

    private static void evacuateDeathInput(Inventory inventory, List<ItemStack> drops, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (item != null && !item.getType().isAir()) {
            drops.add(item.clone());
            inventory.setItem(slot, null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        restoreInterruptedOrCompleted(player);
        if (!hasPendingEntry(player.getUniqueId()) && clearDeliveryMarkers(player)) {
            persistPlayerData(player, "cleaning completed corruption markers");
        }
        Bukkit.getScheduler().runTask(plugin, () -> refreshCorruptedInventory(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        returnOpenCorruptionInputs(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        returnOpenCorruptionInputs(event.getPlayer());
    }

    private void returnOpenCorruptionInputs(Player player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (!(top.getHolder(false) instanceof CorruptionMenuHolder)) {
            return;
        }
        if (activeCorruptions.containsKey(player.getUniqueId())) {
            returnSlot(player, top, ESSENCE_SLOT);
        } else {
            returnMenuInputs(player, top);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        syncChunkStations(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        removeChunkHolograms(event.getChunk());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (containsLockedItem(event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (isCorruptionLocked(event.getInventory().getFirstItem()) || isCorruptionLocked(event.getInventory().getSecondItem())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (isCorruptionLocked(event.getInventory().getUpperItem()) || isCorruptionLocked(event.getInventory().getLowerItem())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (event.getInventory() == null) {
            return;
        }
        if (isCorruptionLocked(event.getInventory().getInputTemplate())
            || isCorruptionLocked(event.getInventory().getInputEquipment())
            || isCorruptionLocked(event.getInventory().getInputMineral())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        if (isCorruptionLocked(event.getItem())) {
            event.setCancelled(true);
            event.getEnchanter().sendMessage(MessageUtil.warn("Corrupted items cannot be changed."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedItemResultClick(InventoryClickEvent event) {
        InventoryType type = event.getView().getTopInventory().getType();
        if (type != InventoryType.ANVIL
            && type != InventoryType.GRINDSTONE
            && type != InventoryType.SMITHING
            && type != InventoryType.ENCHANTING
            && type != InventoryType.WORKBENCH) {
            return;
        }
        if (!isResultSlot(event)) {
            return;
        }
        if (!containsLockedItem(event.getView().getTopInventory().getContents())) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            player.sendMessage(MessageUtil.warn("Corrupted items cannot be changed."));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        double factor = projectileFactor(player);
        if (Math.abs(factor - 1.0) <= 0.001) {
            return;
        }
        event.getEntity().getPersistentDataContainer().set(keyProjectileFactor, PersistentDataType.DOUBLE, factor);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCorruptedProjectileDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile)) {
            return;
        }
        Double factor = projectile.getPersistentDataContainer().get(keyProjectileFactor, PersistentDataType.DOUBLE);
        if (factor != null && Math.abs(factor - 1.0) > 0.001) {
            event.setDamage(event.getDamage() * factor);
        }
    }

    private void openMenu(Player player, BlockKey station) {
        Inventory inventory = Bukkit.createInventory(new CorruptionMenuHolder(player.getUniqueId(), station), MENU_SIZE, Component.text("Item Corruption"));
        fillMenu(inventory);
        refreshMenu(inventory);
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_AMBIENT, 0.8f, 0.7f);
    }

    private void fillMenu(Inventory inventory) {
        decorateMenu(inventory);
        inventory.setItem(ITEM_SLOT, null);
        inventory.setItem(ESSENCE_SLOT, null);
        inventory.setItem(START_SLOT, startItem(false, null, false, null, null));
    }

    private void decorateMenu(Inventory inventory) {
        ItemStack filler = menuItem(
            Material.BLACK_STAINED_GLASS_PANE,
            MenuItemUtil.visibleName(Component.empty()),
            MenuItemUtil.visibleLore(Component.empty(), List.of())
        );
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot != ITEM_SLOT && slot != ESSENCE_SLOT && slot != START_SLOT) {
                inventory.setItem(slot, filler);
            }
        }
        inventory.setItem(INFO_SLOT, infoItem());
        inventory.setItem(ITEM_LABEL_SLOT, menuItem(
            Material.NETHERITE_SWORD,
            Component.text("Item Slot", NamedTextColor.RED).decorate(TextDecoration.BOLD),
            List.of(Component.text("Place one unlocked item below.", NamedTextColor.GRAY))
        ));
        inventory.setItem(FLOW_SLOT, menuItem(
            Material.RESPAWN_ANCHOR,
            Component.text("Corruption Path", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD),
            List.of(Component.text("Item + catalyst, then confirm below.", NamedTextColor.GRAY))
        ));
        inventory.setItem(CATALYST_LABEL_SLOT, menuItem(
            Material.RED_DYE,
            Component.text("Catalyst Slot", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD),
            List.of(Component.text("Use Essence or a Corruption Stone.", NamedTextColor.GRAY))
        ));
    }

    private ItemStack infoItem() {
        return menuItem(
            Material.KNOWLEDGE_BOOK,
            Component.text("Corruption Rules", NamedTextColor.RED).decorate(TextDecoration.BOLD),
            List.of(
                Component.text("Essence: 25% x3/x4, 25% -25%, 25% -50%, 25% destroyed.", NamedTextColor.GRAY),
                Component.text("Stone: 50% x2, 50% sealed unchanged.", NamedTextColor.GRAY),
                Component.text("Awakened items reject all corruption catalysts.", NamedTextColor.RED),
                Component.text("Stone cannot touch legendary or mythic items.", NamedTextColor.RED),
                Component.text("Every result locks the item forever.", NamedTextColor.DARK_GRAY)
            )
        );
    }

    private void refreshMenu(Inventory inventory) {
        decorateMenu(inventory);
        ItemStack target = inventory.getItem(ITEM_SLOT);
        ItemStack catalyst = inventory.getItem(ESSENCE_SLOT);
        boolean validTarget = isValidTarget(target);
        CorruptionCatalyst catalystType = corruptionCatalyst(catalyst);
        boolean ready = validTarget && catalystType != null && isValidTargetForCatalyst(target, catalystType);
        Player viewer = inventory.getHolder(false) instanceof CorruptionMenuHolder holder
            ? Bukkit.getPlayer(holder.playerId())
            : null;
        inventory.setItem(START_SLOT, startItem(ready, target, validTarget, catalystType, viewer));
    }

    private ItemStack startItem(boolean ready, ItemStack target, boolean validTarget, CorruptionCatalyst catalyst, Player viewer) {
        List<Component> lore = new ArrayList<>();
        if (ready) {
            lore.add(Component.text("Starts a 5 second corruption ritual.", NamedTextColor.GRAY));
            lore.add(Component.text("One click spends the catalyst.", NamedTextColor.DARK_GRAY));
            lore.add(Component.text("The item may be destroyed.", NamedTextColor.RED));
        } else {
            lore.add(Component.text("Place one item and a catalyst.", NamedTextColor.GRAY));
            if (target != null && !target.getType().isAir() && !validTarget) {
                lore.add(MM.deserialize("<red>" + invalidTargetReason(target, viewer) + "</red>"));
            } else if (target != null && !target.getType().isAir() && catalyst == CorruptionCatalyst.STONE && isHighTierBlockedForStone(target)) {
                lore.add(Component.text(CORRUPTION_STONE_LIMIT_MESSAGE, NamedTextColor.RED));
            }
        }
        return menuItem(
            ready ? Material.REDSTONE_BLOCK : Material.GRAY_CONCRETE,
            Component.text(ready ? "Corrupt Item" : "Waiting", ready ? NamedTextColor.RED : NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
            lore
        );
    }

    private void handleTopClick(InventoryClickEvent event, Player player, Inventory top, int rawSlot, BlockKey station) {
        if (rawSlot == START_SLOT) {
            event.setCancelled(true);
            if ((event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)
                || !MenuItemUtil.isVisibleItem(event.getCurrentItem())) {
                return;
            }
            beginCorruption(player, top, station);
            return;
        }
        if (rawSlot != ITEM_SLOT && rawSlot != ESSENCE_SLOT) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
            return;
        }
        if (event.isShiftClick() || isBlockedTopClick(event.getClick())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> sanitizeAndRefresh(player, top));
            return;
        }
        ItemStack cursor = event.getCursor();
        if (!isEmpty(cursor) && !isAllowedForSlot(rawSlot, cursor)) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.warn(rawSlot == ITEM_SLOT ? "Put one unlocked item there." : "Put Corrupted Essence or a Corruption Stone there."));
            Bukkit.getScheduler().runTask(plugin, () -> sanitizeAndRefresh(player, top));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> sanitizeAndRefresh(player, top));
    }

    private boolean isBlockedTopClick(ClickType click) {
        return click == ClickType.DOUBLE_CLICK
            || click == ClickType.DROP
            || click == ClickType.CONTROL_DROP
            || click == ClickType.MIDDLE
            || click == ClickType.SWAP_OFFHAND
            || click == ClickType.WINDOW_BORDER_LEFT
            || click == ClickType.WINDOW_BORDER_RIGHT
            || click.isKeyboardClick()
            || click.isCreativeAction();
    }

    private void shiftMoveIntoMenu(Player player, Inventory top, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (isEmpty(clicked)) {
            return;
        }
        if (isCorruptionCatalyst(clicked)) {
            moveCatalyst(top, clicked);
            if (clicked.getAmount() <= 0) {
                event.setCurrentItem(null);
            }
        } else if (isValidTarget(clicked)) {
            if (isEmpty(top.getItem(ITEM_SLOT))) {
                top.setItem(ITEM_SLOT, clicked.clone());
                event.setCurrentItem(null);
            } else {
                player.sendMessage(MessageUtil.warn("The item slot is already full."));
            }
        } else {
            player.sendMessage(MessageUtil.warn("Put one unlocked item there."));
        }
        sanitizeAndRefresh(player, top);
        player.updateInventory();
    }

    private void moveCatalyst(Inventory top, ItemStack clicked) {
        ItemStack existing = top.getItem(ESSENCE_SLOT);
        CatalystSlotChange change = moveOneCatalystIntoEmptySlot(isEmpty(existing) ? 0 : existing.getAmount(), clicked.getAmount());
        if (change.slotAmount() != (isEmpty(existing) ? 0 : existing.getAmount())) {
            top.setItem(ESSENCE_SLOT, clicked.asQuantity(change.slotAmount()));
            clicked.setAmount(change.sourceAmount());
        }
    }

    private void sanitizeAndRefresh(Player player, Inventory top) {
        ItemStack target = top.getItem(ITEM_SLOT);
        if (!isEmpty(target) && !isValidTarget(target)) {
            top.setItem(ITEM_SLOT, null);
            returnOrDrop(player, target);
        }
        ItemStack essence = top.getItem(ESSENCE_SLOT);
        if (!isEmpty(essence) && !isCorruptionCatalyst(essence)) {
            top.setItem(ESSENCE_SLOT, null);
            returnOrDrop(player, essence);
        } else if (!isEmpty(essence) && essence.getAmount() > 1) {
            CatalystSlotChange change = trimCatalystSlot(essence.getAmount());
            ItemStack extra = essence.clone();
            extra.setAmount(change.returnedAmount());
            essence.setAmount(change.slotAmount());
            top.setItem(ESSENCE_SLOT, essence);
            returnOrDrop(player, extra);
        }
        refreshMenu(top);
    }

    static CatalystSlotChange moveOneCatalystIntoEmptySlot(int slotAmount, int sourceAmount) {
        if (slotAmount > 0 || sourceAmount <= 0) {
            return new CatalystSlotChange(Math.max(0, slotAmount), Math.max(0, sourceAmount), 0);
        }
        return new CatalystSlotChange(1, sourceAmount - 1, 0);
    }

    static CatalystSlotChange trimCatalystSlot(int slotAmount) {
        if (slotAmount <= 1) {
            return new CatalystSlotChange(Math.max(0, slotAmount), 0, 0);
        }
        return new CatalystSlotChange(1, 0, slotAmount - 1);
    }

    record CatalystSlotChange(int slotAmount, int sourceAmount, int returnedAmount) {
    }

    private record RecoveryDelivery(ItemStack item, String deliveryId) {
    }

    private void beginCorruption(Player player, Inventory top, BlockKey station) {
        if (!isLiveStation(station)) {
            player.sendMessage(MessageUtil.warn("That Corruption Anchor is no longer available."));
            returnMenuInputs(player, top);
            player.closeInventory();
            return;
        }
        sanitizeAndRefresh(player, top);
        ItemStack target = top.getItem(ITEM_SLOT);
        ItemStack essence = top.getItem(ESSENCE_SLOT);
        CorruptionCatalyst catalyst = corruptionCatalyst(essence);
        if (!isValidTarget(target)) {
            player.sendMessage(MessageUtil.warn(invalidTargetReason(target, player)));
            refreshMenu(top);
            return;
        }
        if (catalyst == null) {
            player.sendMessage(MessageUtil.warn("Add one corruption catalyst."));
            refreshMenu(top);
            return;
        }
        if (!isValidTargetForCatalyst(target, catalyst)) {
            player.sendMessage(MessageUtil.warn(CORRUPTION_STONE_LIMIT_MESSAGE));
            refreshMenu(top);
            return;
        }
        UUID playerId = player.getUniqueId();
        if (activeCorruptions.containsKey(playerId) || hasPendingEntry(playerId)) {
            player.sendMessage(MessageUtil.warn("Your corruption attempt is still being handled."));
            return;
        }

        ItemStack targetCopy = target.clone();
        ItemStack essenceCopy = essence.asQuantity(1);
        String itemRecoveryId = UUID.randomUUID().toString();
        String catalystRecoveryId = UUID.randomUUID().toString();
        tagDeliveryId(targetCopy, itemRecoveryId);
        tagDeliveryId(essenceCopy, catalystRecoveryId);
        if (!prepareActivePending(
            player,
            top,
            targetCopy,
            essenceCopy,
            itemRecoveryId,
            catalystRecoveryId
        )) {
            return;
        }

        ActiveCorruption active = new ActiveCorruption(playerId, player.getName(), station, targetCopy, essenceCopy, catalyst);
        activeCorruptions.put(playerId, active);
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.45f);
        player.sendMessage(MessageUtil.warn("Corruption started. Do not log out."));
        active.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickCorruption(active), 0L, 5L);
    }

    private boolean prepareActivePending(
        Player player,
        Inventory top,
        ItemStack target,
        ItemStack catalyst,
        String itemRecoveryId,
        String catalystRecoveryId
    ) {
        List<String> recoveryIds = List.of(itemRecoveryId, catalystRecoveryId);
        int emptySlots = 0;
        for (ItemStack storageItem : player.getInventory().getStorageContents()) {
            if (isEmpty(storageItem)) {
                emptySlots++;
            }
        }
        if (emptySlots < 2) {
            player.sendMessage(MessageUtil.warn("Clear two inventory slots before starting corruption."));
            return false;
        }

        if (!player.getInventory().addItem(target.clone(), catalyst.clone()).isEmpty()) {
            removeDeliveryItems(player, recoveryIds);
            player.sendMessage(MessageUtil.error("Could not prepare this corruption attempt. Try again in a moment."));
            return false;
        }
        if (!persistPlayerData(player, "saving prepared corruption inputs")) {
            removeDeliveryItems(player, recoveryIds);
            persistPlayerData(player, "rolling back prepared corruption inputs");
            player.sendMessage(MessageUtil.error("Could not save this corruption attempt. Try again in a moment."));
            return false;
        }
        if (!saveActivePending(player, target, catalyst, itemRecoveryId, catalystRecoveryId)) {
            removeDeliveryItems(player, recoveryIds);
            persistPlayerData(player, "rolling back an unjournaled corruption attempt");
            player.sendMessage(MessageUtil.error("Could not save this corruption attempt. Try again in a moment."));
            return false;
        }

        top.setItem(ITEM_SLOT, null);
        consumeOne(top, ESSENCE_SLOT);
        removeDeliveryItems(player, recoveryIds);
        refreshMenu(top);
        if (!persistPlayerData(player, "saving consumed corruption inputs")) {
            restoreInterruptedOrCompleted(player);
            player.sendMessage(MessageUtil.error("Corruption did not start because its inputs could not be saved safely."));
            return false;
        }
        return true;
    }

    private boolean isLiveStation(BlockKey station) {
        if (station == null || !stations.contains(station)) {
            return false;
        }
        World world = Bukkit.getWorld(station.worldId());
        if (world == null || !world.isChunkLoaded(station.x() >> 4, station.z() >> 4)) {
            return false;
        }
        return isStationBlock(world.getBlockAt(station.x(), station.y(), station.z()));
    }

    private void tickCorruption(ActiveCorruption active) {
        active.elapsedTicks += 5L;
        Player player = Bukkit.getPlayer(active.playerId);
        Location effectLocation = effectLocation(active, player);
        if (effectLocation != null) {
            try {
                renderAnimation(effectLocation, active.elapsedTicks);
            } catch (RuntimeException ex) {
                if (!active.animationFailureLogged) {
                    active.animationFailureLogged = true;
                    plugin.getLogger().warning("Corruption animation failed for " + active.playerName + ": " + ex.getMessage());
                }
            }
        }
        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_AMBIENT, 0.45f, 0.55f + (active.elapsedTicks / 160.0f));
        }
        if (active.elapsedTicks < ANIMATION_TICKS) {
            return;
        }
        if (active.task != null) {
            active.task.cancel();
            active.task = null;
        }
        activeCorruptions.remove(active.playerId);
        finishCorruption(active);
    }

    private void finishCorruption(ActiveCorruption active) {
        CorruptionOutcome outcome = rollOutcome(active.catalyst);
        ItemStack result = null;
        String deliveryId = UUID.randomUUID().toString();
        if (outcome != CorruptionOutcome.DELETE) {
            result = active.item.clone();
            applyCorruption(result, outcome);
            tagDeliveryId(result, deliveryId);
        }
        ItemStack announcedItem = outcome == CorruptionOutcome.DELETE ? active.item : result;
        if (!saveCompletedPending(active, outcome, result, deliveryId)) {
            Player player = Bukkit.getPlayer(active.playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(MessageUtil.error("Could not finish corruption safely. Relog later to recover it."));
            }
            plugin.getLogger().severe("Failed to save completed corruption for " + active.playerName + "; keeping active recovery entry.");
            return;
        }

        Player player = Bukkit.getPlayer(active.playerId);
        if (player != null && player.isOnline()) {
            deliverCompleted(player, active.playerName, outcome, result, announcedItem, deliveryId);
        } else {
            broadcastResult(active.playerName, outcome, announcedItem);
        }
    }

    private CorruptionOutcome rollOutcome(CorruptionCatalyst catalyst) {
        if (catalyst == CorruptionCatalyst.STONE) {
            return ThreadLocalRandom.current().nextBoolean() ? CorruptionOutcome.STONE_SUCCESS_2X : CorruptionOutcome.STONE_FAIL_LOCKED;
        }
        int roll = ThreadLocalRandom.current().nextInt(4);
        if (roll == 0) {
            return ThreadLocalRandom.current().nextBoolean() ? CorruptionOutcome.SUCCESS_3X : CorruptionOutcome.SUCCESS_4X;
        }
        return switch (roll) {
            case 1 -> CorruptionOutcome.NEGATE_25;
            case 2 -> CorruptionOutcome.NEGATE_50;
            default -> CorruptionOutcome.DELETE;
        };
    }

    private void applyCorruption(ItemStack item, CorruptionOutcome outcome) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String baseName = pdc.get(keyCorruptionBaseName, PersistentDataType.STRING);
        if (baseName == null || baseName.isBlank()) {
            baseName = baseDisplayName(item, meta);
            pdc.set(keyCorruptionBaseName, PersistentDataType.STRING, baseName);
        }
        pdc.set(keyCorruptionLocked, PersistentDataType.BYTE, (byte) 1);
        pdc.set(keyCorruptionResult, PersistentDataType.STRING, outcome.id);
        pdc.set(keyCorruptionFactor, PersistentDataType.DOUBLE, outcome.factor);
        pdc.set(keyCorruptionSchema, PersistentDataType.INTEGER, CORRUPTION_SCHEMA_VERSION);
        meta.displayName(Component.text(outcome.namePrefix + " " + baseName, outcome.color)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        rewriteAttributes(item.getType(), meta, outcome.factor);
        if (outcome.makeUnbreakable && meta instanceof Damageable) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }
        meta.lore(rewriteCorruptionLore(meta.lore(), item.getType(), meta, outcome));
        item.setItemMeta(meta);
    }

    private void refreshCorruptedInventory(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (refreshCorruptedItem(item)) {
                inventory.setItem(slot, item);
                changed = true;
            }
        }

        ItemStack[] armor = inventory.getArmorContents();
        boolean armorChanged = false;
        for (int i = 0; i < armor.length; i++) {
            if (refreshCorruptedItem(armor[i])) {
                armorChanged = true;
            }
        }
        if (armorChanged) {
            inventory.setArmorContents(armor);
            changed = true;
        }

        ItemStack offhand = inventory.getItemInOffHand();
        if (refreshCorruptedItem(offhand)) {
            inventory.setItemInOffHand(offhand);
            changed = true;
        }

        if (changed) {
            player.updateInventory();
        }
    }

    private boolean refreshCorruptedItem(ItemStack item) {
        if (!isCorruptionLocked(item)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer schema = pdc.get(keyCorruptionSchema, PersistentDataType.INTEGER);
        if (schema != null && schema >= CORRUPTION_SCHEMA_VERSION) {
            return false;
        }
        CorruptionOutcome outcome = CorruptionOutcome.fromId(pdc.get(keyCorruptionResult, PersistentDataType.STRING));
        if (outcome == null) {
            return false;
        }
        Double storedFactor = pdc.get(keyCorruptionFactor, PersistentDataType.DOUBLE);
        double factor = storedFactor == null ? outcome.factor : storedFactor;
        ensureBaseAttackSpeedModifier(item.getType(), meta, factor);
        pdc.set(keyCorruptionSchema, PersistentDataType.INTEGER, CORRUPTION_SCHEMA_VERSION);
        meta.lore(rewriteCorruptionLore(meta.lore(), item.getType(), meta, outcome));
        item.setItemMeta(meta);
        return true;
    }

    private void ensureBaseAttackSpeedModifier(Material material, ItemMeta meta, double factor) {
        double baseSpeed = baseAttackSpeed(material);
        if (Math.abs(baseSpeed) <= 0.001 || Math.abs(factor - 1.0) <= 0.001) {
            return;
        }
        NamespacedKey migratedKey = new NamespacedKey(plugin, "corruption_base_speed_migrated");
        if (hasModifierKey(meta, Attribute.ATTACK_SPEED, migratedKey)) {
            return;
        }
        meta.addAttributeModifier(
            Attribute.ATTACK_SPEED,
            new AttributeModifier(
                migratedKey,
                baseSpeed * (factor - 1.0),
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND
            )
        );
    }

    private boolean hasModifierKey(ItemMeta meta, Attribute attribute, NamespacedKey key) {
        Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(attribute);
        if (modifiers == null || modifiers.isEmpty()) {
            return false;
        }
        for (AttributeModifier modifier : modifiers) {
            if (key.equals(modifier.getKey())) {
                return true;
            }
        }
        return false;
    }

    private void rewriteAttributes(Material material, ItemMeta meta, double factor) {
        Multimap<Attribute, AttributeModifier> rewritten = ArrayListMultimap.create();
        Multimap<Attribute, AttributeModifier> existing = meta.getAttributeModifiers();
        int index = 0;
        if (existing != null && !existing.isEmpty()) {
            for (Map.Entry<Attribute, AttributeModifier> entry : existing.entries()) {
                AttributeModifier modifier = entry.getValue();
                rewritten.put(entry.getKey(), new AttributeModifier(
                    new NamespacedKey(plugin, "corruption_scaled_" + index++),
                    modifier.getAmount() * factor,
                    modifier.getOperation(),
                    modifier.getSlotGroup() == null ? EquipmentSlotGroup.ANY : modifier.getSlotGroup()
                ));
            }
        }
        addBaseStatModifiers(material, factor, rewritten, index);
        meta.setAttributeModifiers(rewritten.isEmpty() ? null : rewritten);
    }

    private void addBaseStatModifiers(Material material, double factor, Multimap<Attribute, AttributeModifier> target, int startIndex) {
        double damage = baseAttackDamage(material);
        double attackSpeed = baseAttackSpeed(material);
        double armor = baseArmor(material);
        double toughness = baseArmorToughness(material);
        double knockback = material == Material.MACE ? 0.5 : 0.0;
        double deltaFactor = factor - 1.0;
        int index = startIndex;
        if (Math.abs(damage) > 0.001) {
            target.put(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                new NamespacedKey(plugin, "corruption_base_damage_" + index++),
                damage * deltaFactor,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND
            ));
        }
        if (Math.abs(attackSpeed) > 0.001) {
            target.put(Attribute.ATTACK_SPEED, new AttributeModifier(
                new NamespacedKey(plugin, "corruption_base_speed_" + index++),
                attackSpeed * deltaFactor,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND
            ));
        }
        if (Math.abs(knockback) > 0.001) {
            target.put(Attribute.ATTACK_KNOCKBACK, new AttributeModifier(
                new NamespacedKey(plugin, "corruption_base_knockback_" + index++),
                knockback * deltaFactor,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND
            ));
        }
        EquipmentSlotGroup armorSlot = armorSlot(material);
        if (armorSlot != null && Math.abs(armor) > 0.001) {
            target.put(Attribute.ARMOR, new AttributeModifier(
                new NamespacedKey(plugin, "corruption_base_armor_" + index++),
                armor * deltaFactor,
                AttributeModifier.Operation.ADD_NUMBER,
                armorSlot
            ));
        }
        if (armorSlot != null && Math.abs(toughness) > 0.001) {
            target.put(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(
                new NamespacedKey(plugin, "corruption_base_toughness_" + index),
                toughness * deltaFactor,
                AttributeModifier.Operation.ADD_NUMBER,
                armorSlot
            ));
        }
    }

    private List<Component> rewriteCorruptionLore(List<Component> currentLore, Material material, ItemMeta meta, CorruptionOutcome outcome) {
        List<Component> lore = new ArrayList<>();
        List<Component> customEnchantLore = CustomLoreUtil.customEnchantLore(meta);
        Set<String> customEnchantPlain = new HashSet<>();
        for (Component line : customEnchantLore) {
            customEnchantPlain.add(PLAIN.serialize(line).trim());
        }
        lore.addAll(customEnchantLore);
        List<Component> cleanedLore = CustomLoreUtil.removeManagedLines(currentLore, CORRUPTION_LINE_PREFIXES);
        if (!cleanedLore.isEmpty()) {
            for (Component line : cleanedLore) {
                String plain = PLAIN.serialize(line).trim();
                if (!customEnchantPlain.contains(plain)) {
                    lore.add(line);
                }
            }
        }
        lore.add(Component.text("Corrupted: ", NamedTextColor.DARK_RED)
            .append(Component.text(outcome.publicLabel, outcome.color))
            .append(Component.text(" • " + statMultiplierText(outcome.factor) + " stats", statColor(outcome.factor)))
            .decoration(TextDecoration.ITALIC, false));
        List<Component> statLines = corruptionStatLines(material, meta, outcome);
        List<String> compactStats = statLines.stream()
            .map(line -> compactCorruptionStat(PLAIN.serialize(line).trim()))
            .toList();
        for (int start = 0; start < compactStats.size(); start += 2) {
            int end = Math.min(compactStats.size(), start + 2);
            lore.add(Component.text("Final: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(String.join(" • ", compactStats.subList(start, end)), NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("Sealed: no further item modifiers.", NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false));
        return CustomLoreUtil.normalizeLore(lore);
    }

    private String compactCorruptionStat(String plain) {
        return plain
            .replace("Attack Damage:", "Dmg")
            .replace("Attack Speed:", "Speed")
            .replace("Attack Knockback:", "KB")
            .replace("Armor Toughness:", "Tough")
            .replace("Knockback Resistance:", "KB Resist")
            .replace("Projectile Damage:", "Projectile")
            .replace("Durability:", "Durability")
            .replace("Armor:", "Armor")
            .replaceAll(" total \\(x[^)]+\\)", "")
            .trim();
    }

    private List<Component> corruptionStatLines(Material material, ItemMeta meta, CorruptionOutcome outcome) {
        List<Component> lines = new ArrayList<>();
        double factor = outcome.factor;
        double baseDamage = baseAttackDamage(material);
        if (baseDamage > 0.001 || hasAttribute(meta, Attribute.ATTACK_DAMAGE, EquipmentSlotGroup.MAINHAND)) {
            lines.add(numberedStatLine(
                "Attack Damage",
                totalAttribute(meta, Attribute.ATTACK_DAMAGE, EquipmentSlotGroup.MAINHAND, baseDamage),
                factor
            ));
        }
        double baseSpeed = baseAttackSpeed(material);
        if (baseSpeed > 0.001 || hasAttribute(meta, Attribute.ATTACK_SPEED, EquipmentSlotGroup.MAINHAND)) {
            lines.add(numberedStatLine(
                "Attack Speed",
                totalAttribute(meta, Attribute.ATTACK_SPEED, EquipmentSlotGroup.MAINHAND, baseSpeed),
                factor
            ));
        }
        if (material == Material.MACE || hasAttribute(meta, Attribute.ATTACK_KNOCKBACK, EquipmentSlotGroup.MAINHAND)) {
            lines.add(numberedStatLine(
                "Attack Knockback",
                totalAttribute(meta, Attribute.ATTACK_KNOCKBACK, EquipmentSlotGroup.MAINHAND, material == Material.MACE ? 0.5 : 0.0),
                factor
            ));
        }
        EquipmentSlotGroup armorSlot = armorSlot(material);
        double baseArmor = baseArmor(material);
        if (armorSlot != null && (baseArmor > 0.001 || hasAttribute(meta, Attribute.ARMOR, armorSlot))) {
            lines.add(numberedStatLine(
                "Armor",
                totalAttribute(meta, Attribute.ARMOR, armorSlot, baseArmor),
                factor
            ));
        }
        double baseToughness = baseArmorToughness(material);
        if (armorSlot != null && (baseToughness > 0.001 || hasAttribute(meta, Attribute.ARMOR_TOUGHNESS, armorSlot))) {
            lines.add(numberedStatLine(
                "Armor Toughness",
                totalAttribute(meta, Attribute.ARMOR_TOUGHNESS, armorSlot, baseToughness),
                factor
            ));
        }
        if (material == Material.BOW || material == Material.CROSSBOW || material == Material.TRIDENT) {
            lines.add(statLine("Projectile Damage", factor == 1.0 ? "unchanged" : "x" + trimStatNumber(factor), statColor(factor)));
        }
        if (outcome.makeUnbreakable) {
            lines.add(statLine("Durability", "Unbreakable", NamedTextColor.GOLD));
        }
        return lines;
    }

    private Component numberedStatLine(String label, double total, double factor) {
        return statLine(label, trimStatNumber(total) + " total (" + statMultiplierText(factor) + ")", statColor(factor));
    }

    private double totalAttribute(ItemMeta meta, Attribute attribute, EquipmentSlotGroup slotGroup, double baseValue) {
        double total = baseValue;
        Multimap<Attribute, AttributeModifier> modifiers = meta.getAttributeModifiers();
        if (modifiers == null || modifiers.isEmpty()) {
            return total;
        }
        double scalar = 0.0;
        double multiplier = 1.0;
        for (Map.Entry<Attribute, AttributeModifier> entry : modifiers.entries()) {
            if (entry.getKey() != attribute || !appliesToSlot(entry.getValue(), slotGroup)) {
                continue;
            }
            AttributeModifier modifier = entry.getValue();
            if (modifier.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
                total += modifier.getAmount();
            } else if (modifier.getOperation() == AttributeModifier.Operation.ADD_SCALAR) {
                scalar += modifier.getAmount();
            } else if (modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1) {
                multiplier *= 1.0 + modifier.getAmount();
            }
        }
        total += baseValue * scalar;
        total *= multiplier;
        return Math.max(0.0, total);
    }

    private boolean hasAttribute(ItemMeta meta, Attribute attribute, EquipmentSlotGroup slotGroup) {
        Multimap<Attribute, AttributeModifier> modifiers = meta.getAttributeModifiers();
        if (modifiers == null || modifiers.isEmpty()) {
            return false;
        }
        for (Map.Entry<Attribute, AttributeModifier> entry : modifiers.entries()) {
            if (entry.getKey() == attribute && appliesToSlot(entry.getValue(), slotGroup)) {
                return true;
            }
        }
        return false;
    }

    private boolean appliesToSlot(AttributeModifier modifier, EquipmentSlotGroup slotGroup) {
        EquipmentSlotGroup modifierSlot = modifier.getSlotGroup();
        EquipmentSlot requestedSlot = representativeSlot(slotGroup);
        return modifierSlot == null
            || modifierSlot == EquipmentSlotGroup.ANY
            || slotGroup == null
            || modifierSlot == slotGroup
            || (requestedSlot != null && modifierSlot.test(requestedSlot));
    }

    private EquipmentSlot representativeSlot(EquipmentSlotGroup slotGroup) {
        if (slotGroup == EquipmentSlotGroup.MAINHAND || slotGroup == EquipmentSlotGroup.HAND) return EquipmentSlot.HAND;
        if (slotGroup == EquipmentSlotGroup.OFFHAND) return EquipmentSlot.OFF_HAND;
        if (slotGroup == EquipmentSlotGroup.HEAD) return EquipmentSlot.HEAD;
        if (slotGroup == EquipmentSlotGroup.CHEST) return EquipmentSlot.CHEST;
        if (slotGroup == EquipmentSlotGroup.LEGS) return EquipmentSlot.LEGS;
        if (slotGroup == EquipmentSlotGroup.FEET) return EquipmentSlot.FEET;
        if (slotGroup == EquipmentSlotGroup.BODY) return EquipmentSlot.BODY;
        if (slotGroup == EquipmentSlotGroup.SADDLE) return EquipmentSlot.SADDLE;
        return null;
    }

    private Component statLine(String label, String value, NamedTextColor color) {
        return Component.text(label + ": ", NamedTextColor.DARK_GRAY)
            .append(Component.text(value, color))
            .decoration(TextDecoration.ITALIC, false);
    }

    private NamedTextColor statColor(double factor) {
        if (factor > 1.001) {
            return NamedTextColor.GREEN;
        }
        if (factor < 0.999) {
            return NamedTextColor.RED;
        }
        return NamedTextColor.GRAY;
    }

    private String statMultiplierText(double factor) {
        return Math.abs(factor - 1.0) <= 0.001 ? "unchanged" : "x" + trimStatNumber(factor);
    }

    private String formatSignedPercent(double value) {
        long rounded = Math.round(value * 100.0);
        return (rounded > 0 ? "+" : "") + rounded + "%";
    }

    private String trimStatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private String baseDisplayName(ItemStack item, ItemMeta meta) {
        Component displayName = meta.displayName();
        if (displayName != null) {
            return stripKnownPrefix(PLAIN.serialize(displayName).trim());
        }
        return prettyMaterialName(item.getType());
    }

    private String stripKnownPrefix(String name) {
        if (name == null || name.isBlank()) {
            return "Unknown Item";
        }
        for (CorruptionOutcome outcome : CorruptionOutcome.values()) {
            String prefix = outcome.namePrefix + " ";
            if (name.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return name.substring(prefix.length()).trim();
            }
        }
        return name;
    }

    private boolean isValidTarget(ItemStack item) {
        return item != null
            && !item.getType().isAir()
            && item.getAmount() == 1
            && !isCorruptionLocked(item)
            && !isAwakened(item)
            && (plugin.getSeasonRelicManager() == null
                || (!plugin.getSeasonRelicManager().isSoulImprint(item)
                    && !plugin.getSeasonRelicManager().isSoulImprinted(item)));
    }

    private String invalidTargetReason(ItemStack item, Player viewer) {
        if (item == null || item.getType().isAir()) {
            return "Place one item first.";
        }
        if (item.getAmount() != 1) {
            return "Split the item to one first.";
        }
        if (isCorruptionLocked(item)) {
            return "That item is already locked.";
        }
        if (isAwakened(item)) {
            return "Awakened items cannot be corrupted.";
        }
        if (plugin.getSeasonRelicManager() != null && plugin.getSeasonRelicManager().isSoulImprint(item)) {
            return soulImprintName(viewer) + " cannot be corrupted.";
        }
        if (plugin.getSeasonRelicManager() != null && plugin.getSeasonRelicManager().isSoulImprinted(item)) {
            return "Copies made by " + soulImprintName(viewer) + " cannot be corrupted.";
        }
        return "That item cannot be corrupted.";
    }

    private String soulImprintName(Player player) {
        return plugin.getSeasonRelicManager() == null
            ? "<obfuscated>Soul Imprint</obfuscated>"
            : plugin.getSeasonRelicManager().soulImprintDisplayName(player);
    }

    private boolean isAwakened(ItemStack item) {
        return plugin.getAwakeningTableListener() != null && plugin.getAwakeningTableListener().isAwakened(item);
    }

    private boolean isValidTargetForCatalyst(ItemStack item, CorruptionCatalyst catalyst) {
        return isValidTarget(item) && (catalyst != CorruptionCatalyst.STONE || !isHighTierBlockedForStone(item));
    }

    private boolean isAllowedForSlot(int slot, ItemStack item) {
        return slot == ITEM_SLOT ? isValidTarget(item) : isCorruptionCatalyst(item);
    }

    private boolean isCorruptionCatalyst(ItemStack item) {
        return corruptionCatalyst(item) != null;
    }

    private CorruptionCatalyst corruptionCatalyst(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        if (plugin.getSeasonRelicManager() == null) {
            return null;
        }
        String relicId = plugin.getSeasonRelicManager().relicId(item);
        if ("corrupted_essence".equals(relicId)) {
            return CorruptionCatalyst.ESSENCE;
        }
        if (CORRUPTION_STONE_ID.equals(relicId)) {
            return CorruptionCatalyst.STONE;
        }
        return null;
    }

    private boolean isHighTierBlockedForStone(ItemStack item) {
        if (plugin.getLegendaryListener() != null && plugin.getLegendaryListener().isLegendaryItem(item)) {
            return true;
        }
        if (plugin.getMythicForgeListener() != null
            && (plugin.getMythicForgeListener().isMythicForgeItemStack(item)
                || plugin.getMythicForgeListener().isAscendantCoreItem(item))) {
            return true;
        }
        if (plugin.getSeasonRelicManager() == null) {
            return false;
        }
        CustomLoreUtil.Rarity rarity = plugin.getSeasonRelicManager().relicRarity(item);
        return rarity == CustomLoreUtil.Rarity.LEGENDARY || rarity == CustomLoreUtil.Rarity.MYTHIC;
    }

    private void consumeOne(Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (isEmpty(item)) {
            return;
        }
        if (item.getAmount() <= 1) {
            inventory.setItem(slot, null);
            return;
        }
        item.setAmount(item.getAmount() - 1);
    }

    private void returnMenuInputs(Player player, Inventory inventory) {
        returnSlot(player, inventory, ITEM_SLOT);
        returnSlot(player, inventory, ESSENCE_SLOT);
    }

    private void returnSlot(Player player, Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (isEmpty(item)) {
            return;
        }
        inventory.setItem(slot, null);
        returnOrDrop(player, item);
    }

    private void returnOrDrop(Player player, ItemStack item) {
        if (isEmpty(item)) {
            return;
        }
        player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private void deliverCompleted(
        Player player,
        String playerName,
        CorruptionOutcome outcome,
        ItemStack result,
        ItemStack announcedItem,
        String deliveryId
    ) {
        List<RecoveryDelivery> deliveries = outcome == CorruptionOutcome.DELETE
            ? List.of()
            : List.of(new RecoveryDelivery(result, deliveryId));
        if (!persistRecoveryDelivery(player, deliveries, "delivered", "corruption result")) {
            return;
        }
        if (!clearPending(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("Your result was delivered, but its recovery marker could not be cleared yet."));
            return;
        }
        if (plugin.getStoryService() != null && (outcome.success || outcome == CorruptionOutcome.DELETE)) {
            plugin.getStoryService().onItemCorruption(
                player,
                outcome.success,
                outcome == CorruptionOutcome.DELETE,
                deliveryId
            );
        }
        broadcastResult(playerName, outcome, announcedItem);
        if (outcome.success) {
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.35f);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.0f);
            player.spawnParticle(Particle.FLASH, player.getLocation().add(0, 1.0, 0), 1, 0, 0, 0, 0, Color.RED);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(player)) {
                    continue;
                }
                other.playSound(other.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.45f, 0.85f);
            }
        } else {
            player.playSound(player.getLocation(), outcome == CorruptionOutcome.DELETE ? Sound.ENTITY_ITEM_BREAK : Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.75f);
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_HURT, 0.55f, 0.55f);
        }
    }

    private void broadcastResult(String playerName, CorruptionOutcome outcome, ItemStack result) {
        String itemName = result == null ? "their item" : itemDisplayName(result);
        Bukkit.broadcast(MessageUtil.prefixedRaw(
            "<dark_red><bold>Corruption:</bold></dark_red> <white>" + miniEscape(playerName) + "</white> "
                + outcome.broadcastText.replace("{item}", "<white>" + miniEscape(itemName) + "</white>")
        ));
    }

    private String itemDisplayName(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "Unknown Item";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.displayName() != null) {
            return PLAIN.serialize(meta.displayName());
        }
        return prettyMaterialName(item.getType());
    }

    private void renderAnimation(Location center, long elapsedTicks) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double progress = Math.min(1.0, elapsedTicks / (double) ANIMATION_TICKS);
        double innerRadius = 1.15 - progress * 0.55;
        for (int i = 0; i < 12; i++) {
            double angle = (elapsedTicks * 0.18) + (Math.PI * 2.0 * i / 12.0);
            Location ring = center.clone().add(Math.cos(angle) * innerRadius, 0.35 + progress * 0.8, Math.sin(angle) * innerRadius);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, ring, 1, 0.02, 0.02, 0.02, 0.0);
            if (i % 2 == 0) {
                world.spawnParticle(Particle.REVERSE_PORTAL, ring, 2, 0.04, 0.04, 0.04, 0.01);
            }
        }

        double roomRadius = 3.0 + (progress * 4.5);
        int roomPoints = 28;
        for (int i = 0; i < roomPoints; i++) {
            double angle = (elapsedTicks * -0.06) + (Math.PI * 2.0 * i / roomPoints);
            double height = 0.35 + (i % 4) * 0.85 + progress * 0.65;
            Location outer = center.clone().add(Math.cos(angle) * roomRadius, height, Math.sin(angle) * roomRadius);
            world.spawnParticle(Particle.SCULK_SOUL, outer, 1, 0.08, 0.12, 0.08, 0.0);
            if (i % 3 == 0) {
                world.spawnParticle(Particle.REVERSE_PORTAL, outer, 2, 0.16, 0.18, 0.16, 0.025);
            }
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 22; i++) {
            double x = random.nextDouble(-6.5, 6.5);
            double y = random.nextDouble(0.35, 4.25);
            double z = random.nextDouble(-6.5, 6.5);
            Location spark = center.clone().add(x, y, z);
            if (i % 4 == 0) {
                world.spawnParticle(Particle.WITCH, spark, 1, 0.08, 0.08, 0.08, 0.01);
            } else {
                spawnDragonBreath(world, spark, 1, 0.08, 0.08, 0.08, 0.01);
            }
        }

        spawnDragonBreath(world, center.clone().add(0.0, 0.9, 0.0), 10, 0.55, 0.55, 0.55, 0.01);
        world.spawnParticle(Particle.SCULK_SOUL, center.clone().add(0.0, 0.8, 0.0), 4, 0.25, 0.35, 0.25, 0.0);
        if (elapsedTicks % 20L == 0L) {
            world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.75f, 0.45f + (float) progress);
        }
        if (elapsedTicks % 40L == 0L) {
            world.playSound(center, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.45f, 0.65f + (float) (progress * 0.4));
        }
        if (elapsedTicks >= ANIMATION_TICKS) {
            world.spawnParticle(Particle.FLASH, center.clone().add(0.0, 1.2, 0.0), 1, 0.0, 0.0, 0.0, 0.0, Color.RED);
            world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.65f, 0.85f);
        }
    }

    private void spawnDragonBreath(World world, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        world.spawnParticle(Particle.DRAGON_BREATH, location, count, offsetX, offsetY, offsetZ, extra, DRAGON_BREATH_PARTICLE_DATA);
    }

    private Location effectLocation(ActiveCorruption active, Player player) {
        Location stationLocation = active.station.location();
        if (stationLocation != null && stationLocation.getWorld() != null && stationLocation.getChunk().isLoaded()) {
            return stationLocation.add(0.5, 1.0, 0.5);
        }
        return player == null ? null : player.getLocation().add(0.0, 1.0, 0.0);
    }

    private double projectileFactor(Player player) {
        PlayerInventory inventory = player.getInventory();
        Double main = corruptionFactor(inventory.getItemInMainHand());
        if (main != null) {
            return main;
        }
        Double offhand = corruptionFactor(inventory.getItemInOffHand());
        return offhand == null ? 1.0 : offhand;
    }

    private Double corruptionFactor(ItemStack item) {
        if (!isCorruptionLocked(item)) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(keyCorruptionFactor, PersistentDataType.DOUBLE);
    }

    private boolean containsLockedItem(ItemStack[] contents) {
        if (contents == null) {
            return false;
        }
        for (ItemStack item : contents) {
            if (isCorruptionLocked(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean isResultSlot(InventoryClickEvent event) {
        return event.getSlotType() == InventoryType.SlotType.RESULT;
    }

    private ItemStack menuItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        Component visibleName = MenuItemUtil.visibleName(name);
        List<Component> visibleLore = MenuItemUtil.visibleLore(name, lore);
        meta.displayName(visibleName.decoration(TextDecoration.ITALIC, false));
        if (!visibleLore.isEmpty()) {
            meta.lore(visibleLore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void registerStation(Block block) {
        if (block == null || block.getType() != STATION_BLOCK_TYPE) {
            return;
        }
        stations.add(BlockKey.from(block));
        saveStations();
        ensureHologram(block);
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.65f);
        spawnDragonBreath(block.getWorld(), block.getLocation().add(0.5, 1.1, 0.5), 32, 0.45, 0.35, 0.45, 0.02);
    }

    private void unregisterStation(Block block) {
        if (block == null || block.getWorld() == null) {
            return;
        }
        stations.remove(BlockKey.from(block));
        removeHologram(block);
        saveStations();
    }

    public boolean isStationBlock(Block block) {
        return block != null && block.getType() == STATION_BLOCK_TYPE && stations.contains(BlockKey.from(block));
    }

    private void syncLoadedStations() {
        for (BlockKey key : new ArrayList<>(stations)) {
            Location location = key.location();
            if (location == null || !location.getChunk().isLoaded()) {
                continue;
            }
            Block block = location.getBlock();
            if (block.getType() != STATION_BLOCK_TYPE) {
                stations.remove(key);
                removeHologramByKey(key.asString());
                saveStations();
                continue;
            }
            ensureHologram(block);
        }
    }

    private void syncChunkStations(Chunk chunk) {
        for (BlockKey key : stations) {
            if (!key.worldId.equals(chunk.getWorld().getUID()) || (key.x >> 4) != chunk.getX() || (key.z >> 4) != chunk.getZ()) {
                continue;
            }
            Location location = key.location();
            if (location != null && location.getBlock().getType() == STATION_BLOCK_TYPE) {
                ensureHologram(location.getBlock());
            }
        }
        removeStaleChunkHolograms(chunk);
    }

    private void ensureHologram(Block block) {
        if (!isStationBlock(block)) {
            return;
        }
        String blockKey = BlockKey.from(block).asString();
        UUID existingId = hologramsByBlock.get(blockKey);
        Entity existing = existingId == null ? null : Bukkit.getEntity(existingId);
        if (existing instanceof TextDisplay display && display.isValid()) {
            display.teleport(hologramLocation(block));
            display.text(hologramText());
            VisualRangeUtil.applyHologramRange(display);
            return;
        }

        removeHologram(block);
        block.getWorld().spawn(hologramLocation(block), TextDisplay.class, display -> {
            display.text(hologramText());
            display.setGravity(false);
            display.setPersistent(false);
            display.setInvulnerable(true);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(false);
            display.setShadowed(true);
            display.setLineWidth(180);
            display.setTextOpacity((byte) 255);
            display.setBackgroundColor(Color.fromARGB(92, 42, 0, 0));
            display.setGlowing(true);
            display.setGlowColorOverride(Color.fromRGB(220, 38, 38));
            VisualRangeUtil.applyHologramRange(display);
            display.getPersistentDataContainer().set(keyHologram, PersistentDataType.BYTE, (byte) 1);
            display.getPersistentDataContainer().set(keyHologramBlock, PersistentDataType.STRING, blockKey);
            hologramsByBlock.put(blockKey, display.getUniqueId());
        });
    }

    private Component hologramText() {
        return Component.empty()
            .append(MM.deserialize("<gradient:#7f1d1d:#ef4444><bold>Corruption Anchor</bold></gradient>"))
            .append(Component.newline())
            .append(MM.deserialize("<gray>Right-click to risk an item</gray>"));
    }

    private Location hologramLocation(Block block) {
        return block.getLocation().add(0.5, 1.55, 0.5);
    }

    private void removeHologram(Block block) {
        if (block == null) {
            return;
        }
        removeHologramByKey(BlockKey.from(block).asString());
        for (Entity entity : block.getChunk().getEntities()) {
            if (isHologramFor(entity, BlockKey.from(block).asString())) {
                entity.remove();
            }
        }
    }

    private void removeHologramByKey(String blockKey) {
        UUID displayId = hologramsByBlock.remove(blockKey);
        if (displayId != null) {
            Entity entity = Bukkit.getEntity(displayId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
    }

    private void removeChunkHolograms(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (isHologram(entity)) {
                entity.remove();
            }
        }
    }

    private void removeStaleChunkHolograms(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (!isHologram(entity)) {
                continue;
            }
            String blockKey = entity.getPersistentDataContainer().get(keyHologramBlock, PersistentDataType.STRING);
            BlockKey parsed = BlockKey.parse(blockKey);
            Location location = parsed == null ? null : parsed.location();
            if (location == null || !isStationBlock(location.getBlock())) {
                entity.remove();
                if (blockKey != null) {
                    hologramsByBlock.remove(blockKey, entity.getUniqueId());
                }
            }
        }
    }

    private boolean isHologram(Entity entity) {
        return entity instanceof TextDisplay && entity.getPersistentDataContainer().has(keyHologram, PersistentDataType.BYTE);
    }

    private boolean isHologramFor(Entity entity, String blockKey) {
        if (!isHologram(entity)) {
            return false;
        }
        return blockKey.equals(entity.getPersistentDataContainer().get(keyHologramBlock, PersistentDataType.STRING));
    }

    private void loadStations() {
        stations.clear();
        if (!stationFile.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(stationFile);
        for (String raw : config.getStringList("stations")) {
            BlockKey key = BlockKey.parse(raw);
            if (key != null) {
                stations.add(key);
            }
        }
    }

    private void saveStations() {
        YamlConfiguration config = new YamlConfiguration();
        List<String> values = stations.stream().map(BlockKey::asString).sorted().toList();
        config.set("stations", values);
        saveYaml(config, stationFile);
    }

    private boolean hasPendingEntry(UUID playerId) {
        if (playerId == null || !pendingFile.exists()) {
            return false;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(pendingFile);
        return config.isConfigurationSection("entries." + playerId);
    }

    private boolean saveActivePending(
        Player player,
        ItemStack item,
        ItemStack essence,
        String itemDeliveryId,
        String catalystDeliveryId
    ) {
        YamlConfiguration config = pendingConfig();
        String path = "entries." + player.getUniqueId();
        config.set(path + ".state", "active");
        config.set(path + ".player-name", player.getName());
        config.set(path + ".item", item);
        config.set(path + ".essence", essence);
        config.set(path + ".item-delivery-id", itemDeliveryId);
        config.set(path + ".catalyst-delivery-id", catalystDeliveryId);
        config.set(path + ".started-at", System.currentTimeMillis());
        return saveYaml(config, pendingFile);
    }

    private boolean saveCompletedPending(ActiveCorruption active, CorruptionOutcome outcome, ItemStack result, String deliveryId) {
        YamlConfiguration config = pendingConfig();
        String path = "entries." + active.playerId;
        config.set(path + ".state", "completed");
        config.set(path + ".player-name", active.playerName);
        config.set(path + ".outcome", outcome.id);
        config.set(path + ".delivery-id", deliveryId);
        config.set(path + ".item", result);
        config.set(path + ".essence", null);
        config.set(path + ".completed-at", System.currentTimeMillis());
        return saveYaml(config, pendingFile);
    }

    private boolean clearPending(UUID playerId) {
        YamlConfiguration config = pendingConfig();
        config.set("entries." + playerId, null);
        return saveYaml(config, pendingFile);
    }

    private boolean markPendingState(UUID playerId, String state) {
        YamlConfiguration config = pendingConfig();
        String path = "entries." + playerId;
        if (!config.isConfigurationSection(path)) {
            return false;
        }
        config.set(path + ".state", state);
        config.set(path + ".acknowledged-at", System.currentTimeMillis());
        return saveYaml(config, pendingFile);
    }

    private void restoreInterruptedOrCompleted(Player player) {
        if (player == null || !pendingFile.exists() || activeCorruptions.containsKey(player.getUniqueId())) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(pendingFile);
        String path = "entries." + player.getUniqueId();
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return;
        }
        String state = section.getString("state", "active");
        if ("delivered".equalsIgnoreCase(state)) {
            if (!clearPending(player.getUniqueId())) {
                player.sendMessage(MessageUtil.warn("Your corruption result is safe, but its recovery marker could not be cleared yet."));
                return;
            }
            player.sendMessage(MessageUtil.info("Your finished corruption result was recovered."));
            return;
        }
        if ("returned".equalsIgnoreCase(state)) {
            if (!clearPending(player.getUniqueId())) {
                player.sendMessage(MessageUtil.warn("Your returned corruption inputs are safe, but their recovery marker could not be cleared yet."));
                return;
            }
            player.sendMessage(MessageUtil.warn("Your interrupted corruption attempt was returned."));
            return;
        }
        if ("completed".equalsIgnoreCase(state)) {
            CorruptionOutcome outcome = CorruptionOutcome.fromId(section.getString("outcome"));
            if (outcome == null) {
                player.sendMessage(MessageUtil.error("Your saved corruption result is invalid. Ask an admin to check the recovery file."));
                return;
            }
            ItemStack item = section.getItemStack("item");
            String deliveryId = section.getString("delivery-id");
            if (outcome != CorruptionOutcome.DELETE) {
                if (isEmpty(item)) {
                    player.sendMessage(MessageUtil.error("Your saved corruption result is missing. Ask an admin to check the recovery file."));
                    return;
                }
                boolean journalChanged = deliveryId == null || deliveryId.isBlank() || !itemHasDeliveryId(item, deliveryId);
                if (deliveryId == null || deliveryId.isBlank()) {
                    deliveryId = UUID.randomUUID().toString();
                }
                if (journalChanged) {
                    tagDeliveryId(item, deliveryId);
                    config.set(path + ".delivery-id", deliveryId);
                    config.set(path + ".item", item);
                    if (!saveYaml(config, pendingFile)) {
                        player.sendMessage(MessageUtil.warn("Could not prepare your corruption result for recovery yet. Try again later."));
                        return;
                    }
                }
            }
            List<RecoveryDelivery> deliveries = outcome == CorruptionOutcome.DELETE
                ? List.of()
                : List.of(new RecoveryDelivery(item, deliveryId));
            if (!persistRecoveryDelivery(player, deliveries, "delivered", "corruption result")) {
                return;
            }
            if (!clearPending(player.getUniqueId())) {
                player.sendMessage(MessageUtil.warn("Your corruption result was delivered, but its recovery marker is still pending."));
                return;
            }
            player.sendMessage(MessageUtil.info("Your finished corruption result was recovered."));
            return;
        }

        if (!"active".equalsIgnoreCase(state)) {
            player.sendMessage(MessageUtil.error("Your saved corruption recovery state is invalid. Ask an admin to check the recovery file."));
            return;
        }

        ItemStack item = section.getItemStack("item");
        ItemStack essence = section.getItemStack("essence");
        String itemDeliveryId = section.getString("item-delivery-id");
        String catalystDeliveryId = section.getString("catalyst-delivery-id");
        boolean journalChanged = false;
        if (!isEmpty(item)) {
            if (itemDeliveryId == null || itemDeliveryId.isBlank()) {
                itemDeliveryId = UUID.randomUUID().toString();
                journalChanged = true;
            }
            if (!itemHasDeliveryId(item, itemDeliveryId)) {
                tagDeliveryId(item, itemDeliveryId);
                journalChanged = true;
            }
            config.set(path + ".item-delivery-id", itemDeliveryId);
            config.set(path + ".item", item);
        }
        if (!isEmpty(essence)) {
            if (catalystDeliveryId == null || catalystDeliveryId.isBlank() || catalystDeliveryId.equals(itemDeliveryId)) {
                catalystDeliveryId = UUID.randomUUID().toString();
                journalChanged = true;
            }
            if (!itemHasDeliveryId(essence, catalystDeliveryId)) {
                tagDeliveryId(essence, catalystDeliveryId);
                journalChanged = true;
            }
            config.set(path + ".catalyst-delivery-id", catalystDeliveryId);
            config.set(path + ".essence", essence);
        }
        if (journalChanged && !saveYaml(config, pendingFile)) {
            player.sendMessage(MessageUtil.warn("Could not prepare your interrupted corruption attempt for recovery yet."));
            return;
        }

        List<RecoveryDelivery> deliveries = new ArrayList<>(2);
        if (!isEmpty(item)) {
            deliveries.add(new RecoveryDelivery(item, itemDeliveryId));
        }
        if (!isEmpty(essence)) {
            deliveries.add(new RecoveryDelivery(essence, catalystDeliveryId));
        }
        if (!persistRecoveryDelivery(player, deliveries, "returned", "interrupted corruption inputs")) {
            return;
        }
        if (!clearPending(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("Your interrupted inputs were returned, but their recovery marker is still pending."));
            return;
        }
        player.sendMessage(MessageUtil.warn("Your interrupted corruption attempt was returned."));
    }

    private boolean persistRecoveryDelivery(
        Player player,
        List<RecoveryDelivery> deliveries,
        String acknowledgedState,
        String description
    ) {
        if (player == null || deliveries == null) {
            return false;
        }
        List<RecoveryDelivery> missing = new ArrayList<>();
        for (RecoveryDelivery delivery : deliveries) {
            if (delivery == null || isEmpty(delivery.item())
                || delivery.deliveryId() == null || delivery.deliveryId().isBlank()) {
                player.sendMessage(MessageUtil.error("Your " + description + " recovery data is invalid. Ask an admin to check it."));
                return false;
            }
            if (!hasDeliveredItem(player, delivery.deliveryId())) {
                missing.add(delivery);
            }
        }

        long emptySlots = 0L;
        for (ItemStack storageItem : player.getInventory().getStorageContents()) {
            if (isEmpty(storageItem)) {
                emptySlots++;
            }
        }
        if (emptySlots < missing.size()) {
            player.sendMessage(MessageUtil.warn("Make " + missing.size() + " empty inventory slot"
                + (missing.size() == 1 ? "" : "s") + " to recover your " + description + "."));
            return false;
        }

        List<String> deliveryIds = deliveries.stream().map(RecoveryDelivery::deliveryId).toList();
        for (RecoveryDelivery delivery : missing) {
            if (!player.getInventory().addItem(delivery.item().clone()).isEmpty()) {
                removeDeliveryItems(player, deliveryIds);
                persistPlayerData(player, "rolling back an incomplete " + description + " delivery");
                player.sendMessage(MessageUtil.warn("Could not recover your " + description + " yet. Try again later."));
                return false;
            }
        }

        if (!deliveries.isEmpty() && !persistPlayerData(player, "saving a " + description + " delivery")) {
            removeDeliveryItems(player, deliveryIds);
            persistPlayerData(player, "rolling back an unsaved " + description + " delivery");
            player.sendMessage(MessageUtil.warn("Your " + description + " is still safe, but could not be saved to your inventory yet."));
            return false;
        }
        if (!markPendingState(player.getUniqueId(), acknowledgedState)) {
            if (!deliveries.isEmpty()) {
                removeDeliveryItems(player, deliveryIds);
                persistPlayerData(player, "rolling back an unacknowledged " + description + " delivery");
            }
            player.sendMessage(MessageUtil.warn("Your " + description + " is still safe, but its recovery state could not be saved yet."));
            return false;
        }
        player.updateInventory();
        return true;
    }

    private boolean persistPlayerData(Player player, String action) {
        try {
            player.saveData();
            return true;
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Failed while " + action + " for " + player.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    private void removeDeliveryItems(Player player, Collection<String> deliveryIds) {
        if (player == null || deliveryIds == null || deliveryIds.isEmpty()) {
            return;
        }
        removeDeliveryItems(player.getInventory(), deliveryIds);
        removeDeliveryItems(player.getEnderChest(), deliveryIds);
        player.updateInventory();
    }

    private void removeDeliveryItems(Inventory inventory, Collection<String> deliveryIds) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isEmpty(item)) {
                continue;
            }
            for (String deliveryId : deliveryIds) {
                if (itemHasDeliveryId(item, deliveryId)) {
                    inventory.setItem(slot, null);
                    break;
                }
            }
        }
    }

    private YamlConfiguration pendingConfig() {
        return pendingFile.exists() ? YamlConfiguration.loadConfiguration(pendingFile) : new YamlConfiguration();
    }

    private void tagDeliveryId(ItemStack item, String deliveryId) {
        if (item == null || item.getType().isAir() || deliveryId == null || deliveryId.isBlank()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(keyCorruptionDeliveryId, PersistentDataType.STRING, deliveryId);
        item.setItemMeta(meta);
    }

    private boolean hasDeliveredItem(Player player, String deliveryId) {
        if (player == null || deliveryId == null || deliveryId.isBlank()) {
            return false;
        }
        if (containsDeliveryId(player.getInventory().getContents(), deliveryId)
            || containsDeliveryId(player.getInventory().getArmorContents(), deliveryId)
            || containsDeliveryId(player.getEnderChest().getContents(), deliveryId)
            || itemHasDeliveryId(player.getInventory().getItemInOffHand(), deliveryId)) {
            return true;
        }
        return false;
    }

    private boolean containsDeliveryId(ItemStack[] contents, String deliveryId) {
        if (contents == null) {
            return false;
        }
        for (ItemStack item : contents) {
            if (itemHasDeliveryId(item, deliveryId)) {
                return true;
            }
        }
        return false;
    }

    private boolean itemHasDeliveryId(ItemStack item, String deliveryId) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && deliveryId.equals(meta.getPersistentDataContainer().get(keyCorruptionDeliveryId, PersistentDataType.STRING));
    }

    private boolean clearDeliveryMarkers(Player player) {
        boolean changed = clearDeliveryMarkers(player.getInventory().getContents(), player.getInventory());
        changed |= clearDeliveryMarkers(player.getEnderChest().getContents(), player.getEnderChest());
        ItemStack cursor = player.getItemOnCursor();
        if (itemHasAnyDeliveryId(cursor)) {
            clearDeliveryMarker(cursor);
            player.setItemOnCursor(cursor);
            changed = true;
        }
        return changed;
    }

    private boolean clearDeliveryMarkers(ItemStack[] contents, Inventory inventory) {
        boolean changed = false;
        if (contents == null) {
            return false;
        }
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!itemHasAnyDeliveryId(item)) {
                continue;
            }
            clearDeliveryMarker(item);
            inventory.setItem(slot, item);
            changed = true;
        }
        return changed;
    }

    private boolean itemHasAnyDeliveryId(ItemStack item) {
        if (isEmpty(item)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(keyCorruptionDeliveryId, PersistentDataType.STRING);
    }

    private void clearDeliveryMarker(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().remove(keyCorruptionDeliveryId);
        item.setItemMeta(meta);
    }

    private boolean saveYaml(YamlConfiguration config, File file) {
        Path temporary = null;
        try {
            File parent = file.getParentFile();
            Path parentPath = parent == null ? file.toPath().toAbsolutePath().getParent() : parent.toPath();
            if (parentPath == null) {
                throw new IOException("No parent directory is available");
            }
            Files.createDirectories(parentPath);
            temporary = Files.createTempFile(parentPath, file.getName() + ".", ".tmp");
            config.save(temporary.toFile());
            try {
                Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save " + file.getName() + ": " + e.getMessage());
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private double defaultFinalAttribute(Material material, EquipmentSlot slot, Attribute attribute, double playerBase) {
        double modifier = defaultAttributeModifier(material, slot, attribute);
        return Double.isNaN(modifier) ? Double.NaN : playerBase + modifier;
    }

    private double defaultAttributeModifier(Material material, EquipmentSlot slot, Attribute attribute) {
        Collection<AttributeModifier> modifiers = material.getDefaultAttributeModifiers(slot).get(attribute);
        if (modifiers == null || modifiers.isEmpty()) {
            return Double.NaN;
        }
        double total = 0.0;
        boolean found = false;
        for (AttributeModifier modifier : modifiers) {
            if (modifier.getOperation() != AttributeModifier.Operation.ADD_NUMBER) {
                continue;
            }
            total += modifier.getAmount();
            found = true;
        }
        return found ? total : Double.NaN;
    }

    private double baseAttackDamage(Material material) {
        double paperDefault = defaultFinalAttribute(material, EquipmentSlot.HAND, Attribute.ATTACK_DAMAGE, PLAYER_BASE_ATTACK_DAMAGE);
        if (!Double.isNaN(paperDefault)) {
            return paperDefault;
        }
        String name = material.name();
        if (material == Material.WOODEN_SWORD || material == Material.GOLDEN_SWORD) return 4.0;
        if (material == Material.STONE_SWORD) return 5.0;
        if (material == Material.IRON_SWORD) return 6.0;
        if (material == Material.DIAMOND_SWORD) return 7.0;
        if (material == Material.NETHERITE_SWORD) return 8.0;
        if (material == Material.TRIDENT) return 9.0;
        if (material == Material.MACE) return 6.0;
        if (name.endsWith("_AXE")) {
            if (material == Material.WOODEN_AXE || material == Material.GOLDEN_AXE) return 7.0;
            if (material == Material.STONE_AXE || material == Material.IRON_AXE || material == Material.DIAMOND_AXE) return 9.0;
            if (material == Material.NETHERITE_AXE) return 10.0;
        }
        if (name.endsWith("_PICKAXE")) {
            if (material == Material.WOODEN_PICKAXE || material == Material.GOLDEN_PICKAXE) return 2.0;
            if (material == Material.STONE_PICKAXE) return 3.0;
            if (material == Material.IRON_PICKAXE) return 4.0;
            if (material == Material.DIAMOND_PICKAXE) return 5.0;
            if (material == Material.NETHERITE_PICKAXE) return 6.0;
        }
        if (name.endsWith("_SHOVEL")) {
            if (material == Material.WOODEN_SHOVEL || material == Material.GOLDEN_SHOVEL) return 2.5;
            if (material == Material.STONE_SHOVEL) return 3.5;
            if (material == Material.IRON_SHOVEL) return 4.5;
            if (material == Material.DIAMOND_SHOVEL) return 5.5;
            if (material == Material.NETHERITE_SHOVEL) return 6.5;
        }
        if (name.endsWith("_HOE")) {
            if (material == Material.WOODEN_HOE || material == Material.GOLDEN_HOE) return 1.0;
            if (material == Material.STONE_HOE) return 1.0;
            if (material == Material.IRON_HOE) return 1.0;
            if (material == Material.DIAMOND_HOE) return 1.0;
            if (material == Material.NETHERITE_HOE) return 1.0;
        }
        return 0.0;
    }

    private double baseAttackSpeed(Material material) {
        double paperDefault = defaultFinalAttribute(material, EquipmentSlot.HAND, Attribute.ATTACK_SPEED, PLAYER_BASE_ATTACK_SPEED);
        if (!Double.isNaN(paperDefault)) {
            return Math.max(0.0, paperDefault);
        }
        String name = material.name();
        if (name.endsWith("_SWORD")) return 1.6;
        if (material == Material.TRIDENT) return 1.1;
        if (material == Material.MACE) return 0.6;
        if (material == Material.WOODEN_AXE || material == Material.STONE_AXE) return 0.8;
        if (material == Material.IRON_AXE) return 0.9;
        if (material == Material.DIAMOND_AXE || material == Material.NETHERITE_AXE || material == Material.GOLDEN_AXE) return 1.0;
        if (name.endsWith("_PICKAXE")) return 1.2;
        if (name.endsWith("_SHOVEL")) return 1.0;
        if (material == Material.WOODEN_HOE || material == Material.GOLDEN_HOE) return 1.0;
        if (material == Material.STONE_HOE) return 2.0;
        if (material == Material.IRON_HOE) return 3.0;
        if (material == Material.DIAMOND_HOE || material == Material.NETHERITE_HOE) return 4.0;
        return 0.0;
    }

    private double baseArmor(Material material) {
        EquipmentSlot slot = material.getEquipmentSlot();
        if (slot != null) {
            double paperDefault = defaultAttributeModifier(material, slot, Attribute.ARMOR);
            if (!Double.isNaN(paperDefault)) {
                return paperDefault;
            }
        }
        return switch (material) {
            case LEATHER_HELMET, GOLDEN_HELMET, CHAINMAIL_HELMET -> 2.0;
            case IRON_HELMET -> 2.0;
            case DIAMOND_HELMET, NETHERITE_HELMET, TURTLE_HELMET -> 3.0;
            case LEATHER_CHESTPLATE -> 3.0;
            case GOLDEN_CHESTPLATE, CHAINMAIL_CHESTPLATE -> 5.0;
            case IRON_CHESTPLATE -> 6.0;
            case DIAMOND_CHESTPLATE, NETHERITE_CHESTPLATE -> 8.0;
            case LEATHER_LEGGINGS -> 2.0;
            case GOLDEN_LEGGINGS -> 3.0;
            case CHAINMAIL_LEGGINGS -> 4.0;
            case IRON_LEGGINGS -> 5.0;
            case DIAMOND_LEGGINGS, NETHERITE_LEGGINGS -> 6.0;
            case LEATHER_BOOTS, GOLDEN_BOOTS, CHAINMAIL_BOOTS -> 1.0;
            case IRON_BOOTS -> 2.0;
            case DIAMOND_BOOTS, NETHERITE_BOOTS -> 3.0;
            default -> 0.0;
        };
    }

    private double baseArmorToughness(Material material) {
        EquipmentSlot slot = material.getEquipmentSlot();
        if (slot != null) {
            double paperDefault = defaultAttributeModifier(material, slot, Attribute.ARMOR_TOUGHNESS);
            if (!Double.isNaN(paperDefault)) {
                return paperDefault;
            }
        }
        return switch (material) {
            case DIAMOND_HELMET, DIAMOND_CHESTPLATE, DIAMOND_LEGGINGS, DIAMOND_BOOTS -> 2.0;
            case NETHERITE_HELMET, NETHERITE_CHESTPLATE, NETHERITE_LEGGINGS, NETHERITE_BOOTS -> 3.0;
            default -> 0.0;
        };
    }

    private EquipmentSlotGroup armorSlot(Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET") || material == Material.TURTLE_HELMET) return EquipmentSlotGroup.HEAD;
        if (name.endsWith("_CHESTPLATE") || material == Material.ELYTRA) return EquipmentSlotGroup.CHEST;
        if (name.endsWith("_LEGGINGS")) return EquipmentSlotGroup.LEGS;
        if (name.endsWith("_BOOTS")) return EquipmentSlotGroup.FEET;
        return null;
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private String prettyMaterialName(Material material) {
        StringBuilder out = new StringBuilder();
        for (String part : material.name().toLowerCase(Locale.ROOT).split("_")) {
            if (part.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return out.toString();
    }

    private String miniEscape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\")
            .replace("<", "\\<")
            .replace(">", "\\>");
    }

    private enum CorruptionOutcome {
        SUCCESS_3X("success_3x", true, true, 3.0, "Corrupted", "x3 Empowerment", "x3 all item stats", NamedTextColor.RED,
            "succeeded and gave {item} <red><bold>x3 stats</bold></red>."),
        SUCCESS_4X("success_4x", true, true, 4.0, "Abyssal", "x4 Empowerment", "x4 all item stats", NamedTextColor.DARK_RED,
            "succeeded and gave {item} <dark_red><bold>x4 stats</bold></dark_red>."),
        NEGATE_25("negate_25", false, false, 0.75, "Fractured", "25% Stat Loss", "-25% all item stats", NamedTextColor.YELLOW,
            "failed and {item} lost <yellow>25%</yellow> of its stats."),
        NEGATE_50("negate_50", false, false, 0.50, "Ruined", "50% Stat Loss", "-50% all item stats", NamedTextColor.GRAY,
            "failed and {item} lost <red>50%</red> of its stats."),
        DELETE("delete", false, false, 0.0, "Destroyed", "Destroyed", "item destroyed", NamedTextColor.DARK_GRAY,
            "failed and destroyed {item}."),
        STONE_SUCCESS_2X("stone_success_2x", true, false, 2.0, "Empowered", "x2 Stone Empowerment", "x2 all item stats", NamedTextColor.LIGHT_PURPLE,
            "used a Corruption Stone and gave {item} <light_purple><bold>x2 stats</bold></light_purple>."),
        STONE_FAIL_LOCKED("stone_fail_locked", false, false, 1.0, "Sealed", "Stone Failed", "stats unchanged", NamedTextColor.GRAY,
            "used a Corruption Stone on {item}; it kept its stats but is now sealed.");

        private final String id;
        private final boolean success;
        private final boolean makeUnbreakable;
        private final double factor;
        private final String namePrefix;
        private final String publicLabel;
        private final String loreText;
        private final NamedTextColor color;
        private final String broadcastText;

        CorruptionOutcome(
            String id,
            boolean success,
            boolean makeUnbreakable,
            double factor,
            String namePrefix,
            String publicLabel,
            String loreText,
            NamedTextColor color,
            String broadcastText
        ) {
            this.id = id;
            this.success = success;
            this.makeUnbreakable = makeUnbreakable;
            this.factor = factor;
            this.namePrefix = namePrefix;
            this.publicLabel = publicLabel;
            this.loreText = loreText;
            this.color = color;
            this.broadcastText = broadcastText;
        }

        private static CorruptionOutcome fromId(String id) {
            if (id == null) {
                return null;
            }
            for (CorruptionOutcome outcome : values()) {
                if (outcome.id.equals(id)) {
                    return outcome;
                }
            }
            return null;
        }
    }

    private enum CorruptionCatalyst {
        ESSENCE,
        STONE
    }

    private static final class ActiveCorruption {
        private final UUID playerId;
        private final String playerName;
        private final BlockKey station;
        private final ItemStack item;
        private final ItemStack essence;
        private final CorruptionCatalyst catalyst;
        private BukkitTask task;
        private long elapsedTicks;
        private boolean animationFailureLogged;

        private ActiveCorruption(UUID playerId, String playerName, BlockKey station, ItemStack item, ItemStack essence, CorruptionCatalyst catalyst) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.station = station;
            this.item = item;
            this.essence = essence;
            this.catalyst = catalyst;
        }
    }

    public record BlockKey(UUID worldId, int x, int y, int z) {
        private static BlockKey from(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        private static BlockKey parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String[] parts = raw.split(":");
            if (parts.length != 4) {
                return null;
            }
            try {
                return new BlockKey(UUID.fromString(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        public String asString() {
            return worldId + ":" + x + ":" + y + ":" + z;
        }

        public Location location() {
            World world = Bukkit.getWorld(worldId);
            return world == null ? null : new Location(world, x, y, z);
        }
    }

    private record CorruptionMenuHolder(UUID playerId, BlockKey station) implements InventoryHolder, MenuDupeGuardListener.MutableMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
