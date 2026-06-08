package me.rique.smpcore.awakening;

import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.item.CustomToolListener;
import me.rique.smpcore.legendary.LegendaryListener;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.util.VisualRangeUtil;
import net.kyori.adventure.text.Component;
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
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.EnchantingTable;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class AwakeningTableListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final Component AWAKENING_MENU_TITLE =
        MM.deserialize("<gradient:#ff6b6b:#c1121f><bold>Awakening Table</bold></gradient>");
    private static final Component AWAKENED_PREFIX =
        MM.deserialize("<gradient:#ff6b6b:#c1121f><bold>Awakened </bold></gradient>");
    private static final Component AWAKENING_BONUS_HEADER =
        MM.deserialize("<gradient:#ff8a5b:#ff3d3d><bold>Awakening Bonus</bold></gradient>");
    private static final String AWAKENING_BONUS_HEADER_PLAIN = "Awakening Bonus";

    private static final int MENU_SIZE = 27;
    private static final int STATUS_SLOT = 4;
    private static final int ITEM_SLOT = 11;
    private static final int ACTION_SLOT = 13;
    private static final int STAR_SLOT = 15;
    private static final long TRIDENT_MARK_WINDOW_MS = 1_500L;
    private static final double PLAYER_BASE_ATTACK_SPEED = 4.0;

    private final SMPCore plugin;
    private final NamespacedKey keyAwakeningTableItem;
    private final NamespacedKey keyAwakeningTableBlock;
    private final NamespacedKey keyAwakened;
    private final NamespacedKey keyRestoreUnbreakable;
    private final NamespacedKey keyBaseName;
    private final NamespacedKey keyProjectileAwakened;
    private final NamespacedKey keyAttackSpeedModifier;
    private final NamespacedKey keyArmorModifier;
    private final NamespacedKey keyArmorToughnessModifier;
    private final NamespacedKey keyKnockbackResistanceModifier;
    private final NamespacedKey keyEquippedArmorBonusModifier;
    private final NamespacedKey keyEquippedArmorToughnessBonusModifier;
    private final NamespacedKey keyEquippedKnockbackResistanceBonusModifier;
    private final NamespacedKey keyAwakeningTableHologram;
    private final NamespacedKey keyAwakeningTableHologramBlock;
    private final Map<UUID, Long> pendingTridentLaunches = new ConcurrentHashMap<>();

    public AwakeningTableListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyAwakeningTableItem = new NamespacedKey(plugin, "awakening_table_item");
        this.keyAwakeningTableBlock = new NamespacedKey(plugin, "awakening_table_block");
        this.keyAwakened = new NamespacedKey(plugin, "awakened_item");
        this.keyRestoreUnbreakable = new NamespacedKey(plugin, "awakening_restore_unbreakable");
        this.keyBaseName = new NamespacedKey(plugin, "awakening_base_name");
        this.keyProjectileAwakened = new NamespacedKey(plugin, "awakening_projectile");
        this.keyAttackSpeedModifier = new NamespacedKey(plugin, "awakening_attack_speed");
        this.keyArmorModifier = new NamespacedKey(plugin, "awakening_armor");
        this.keyArmorToughnessModifier = new NamespacedKey(plugin, "awakening_armor_toughness");
        this.keyKnockbackResistanceModifier = new NamespacedKey(plugin, "awakening_knockback_resistance");
        this.keyEquippedArmorBonusModifier = new NamespacedKey(plugin, "awakening_equipped_armor_bonus");
        this.keyEquippedArmorToughnessBonusModifier = new NamespacedKey(plugin, "awakening_equipped_armor_toughness_bonus");
        this.keyEquippedKnockbackResistanceBonusModifier = new NamespacedKey(plugin, "awakening_equipped_knockback_resistance_bonus");
        this.keyAwakeningTableHologram = new NamespacedKey(plugin, "awakening_table_hologram");
        this.keyAwakeningTableHologramBlock = new NamespacedKey(plugin, "awakening_table_hologram_block");
    }

    public void start() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            syncLoadedTableHolograms();
            for (Player player : Bukkit.getOnlinePlayers()) {
                normalizePlayerAwakeningItems(player);
                syncEquippedAwakeningArmor(player);
            }
        });
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearEquippedAwakeningArmorBonuses(player);
        }
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                removeStaleChunkHolograms(chunk, true);
            }
        }
    }

    public ItemStack createAwakeningTableItem() {
        ItemStack item = new ItemStack(Material.ENCHANTING_TABLE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.MYTHIC, "Awakening Table"));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.ENCHANTING_TABLE,
            CustomLoreUtil.Rarity.MYTHIC.label(),
            "TABLE",
            List.of(
                "<gray>Dropped by <white>Aurelion the Rift Seraph</white>.</gray>",
                "<gray>Success chance: <white>" + formatPercent(plugin.getConfigManager().awakeningTableSuccessChance) + "</white></gray>"
            ),
            List.of(CustomLoreUtil.section(
                "Use",
                "Awaken Gear",
                "<gray>Insert a weapon, tool, or armor piece.</gray>",
                "<gray>Spend a <white>Nether Star</white> to attempt an awakening.</gray>"
            ))
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(keyAwakeningTableItem, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isAwakeningTableCustomItem(ItemStack item) {
        return isAwakeningTableItem(item);
    }

    public void copyAwakeningState(ItemMeta sourceMeta, ItemMeta targetMeta) {
        if (sourceMeta == null || targetMeta == null) {
            return;
        }
        copyString(sourceMeta, targetMeta, keyBaseName);
        copyByte(sourceMeta, targetMeta, keyAwakened);
        copyByte(sourceMeta, targetMeta, keyRestoreUnbreakable);
    }

    public void clearAwakeningState(ItemMeta meta) {
        if (meta == null) {
            return;
        }
        removeAwakeningAttributeModifiers(meta);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(keyBaseName);
        pdc.remove(keyAwakened);
        pdc.remove(keyRestoreUnbreakable);
    }

    public void applyManagedItemState(ItemMeta meta, Material material, Component baseDisplayName, boolean defaultUnbreakable) {
        if (meta == null || material == null || material == Material.AIR) {
            return;
        }

        removeAwakeningAttributeModifiers(meta);
        AwakeningBaseStats baseStats = collectBaseStats(meta, material);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        boolean awakened = isMarked(pdc, keyAwakened);
        boolean restoreUnbreakable = isMarked(pdc, keyRestoreUnbreakable);
        Component safeBaseDisplay = baseDisplayName == null ? defaultDisplayName(material) : baseDisplayName;

        meta.displayName(awakened ? AWAKENED_PREFIX.append(safeBaseDisplay) : safeBaseDisplay);
        applyUnbreakableState(meta, defaultUnbreakable, awakened, restoreUnbreakable);
        if (awakened) {
            applyAwakeningAttributeBonuses(meta, material, baseStats);
        }
        applyAwakeningLore(meta, material, awakened, baseStats);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.ENCHANTING_TABLE) {
            return;
        }
        if (!isAwakeningTableItem(event.getItemInHand())) {
            return;
        }

        if (!(event.getBlockPlaced().getState() instanceof EnchantingTable enchantingTable)) {
            return;
        }
        enchantingTable.getPersistentDataContainer().set(keyAwakeningTableBlock, PersistentDataType.BYTE, (byte) 1);
        enchantingTable.update(true, false);
        ensureAwakeningTableHologram(event.getBlockPlaced().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (!isAwakeningTableBlock(block)) {
            return;
        }

        event.setCancelled(true);
        ensureAwakeningTableHologram(block.getLocation());
        Player player = event.getPlayer();
        if (!plugin.getConfigManager().awakeningTableEnabled) {
            player.sendMessage(MessageUtil.warn("The Awakening Table is currently disabled."));
            return;
        }
        if (!player.hasPermission("smpcore.awakening.use")) {
            player.sendMessage(MessageUtil.error("You do not have permission to use the Awakening Table."));
            return;
        }

        openMenu(player, block.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isAwakeningTableBlock(block)) {
            return;
        }

        removeAwakeningTableHolograms(block.getLocation());
        event.setDropItems(false);
        event.setExpToDrop(0);
        if (event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), createAwakeningTableItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        dropExplodedAwakeningTables(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        dropExplodedAwakeningTables(event.blockList());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!plugin.getConfigManager().awakeningTableEnabled) {
            removeStaleChunkHolograms(event.getChunk(), true);
            return;
        }
        syncChunkTableHolograms(event.getChunk());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getFirstItem();
        ItemStack right = event.getInventory().getSecondItem();
        if (!hasAwakeningState(left) && !hasAwakeningState(right)) {
            return;
        }
        if (isManagedByOtherSystem(left) || isManagedByOtherSystem(right)) {
            return;
        }

        ItemStack source = hasAwakeningState(left) ? left : right;
        ItemStack result = event.getResult();
        if (source == null || result == null || result.getType() == Material.AIR) {
            return;
        }

        event.setResult(preserveVanillaAwakeningResult(source, result));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (!(event.getInventory() instanceof GrindstoneInventory grindstone)) {
            return;
        }

        ItemStack top = grindstone.getUpperItem();
        ItemStack bottom = grindstone.getLowerItem();
        if (!hasAwakeningState(top) && !hasAwakeningState(bottom)) {
            return;
        }
        if (isManagedByOtherSystem(top) || isManagedByOtherSystem(bottom)) {
            return;
        }

        ItemStack source = hasAwakeningState(top) ? top : bottom;
        if (source == null) {
            return;
        }

        ItemStack result = event.getResult();
        if (result == null || result.getType() == Material.AIR) {
            result = source.clone();
        }
        event.setResult(preserveVanillaAwakeningResult(source, result));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (!(event.getInventory() instanceof SmithingInventory smithing)) {
            return;
        }

        ItemStack source = smithing.getInputEquipment();
        if (!hasAwakeningState(source) || isManagedByOtherSystem(source)) {
            return;
        }

        ItemStack result = event.getResult();
        if (source == null || result == null || result.getType() == Material.AIR) {
            return;
        }

        event.setResult(preserveVanillaAwakeningResult(source, result));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof AwakeningMenuHolder holder)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();
        if (rawSlot < top.getSize()) {
            event.setCancelled(true);
            if (rawSlot == ACTION_SLOT) {
                attemptAwakening(player, top, holder);
                return;
            }
            if (rawSlot == ITEM_SLOT || rawSlot == STAR_SLOT) {
                handleMenuSlotInteraction(player, top, rawSlot);
                refreshMenu(top);
            }
            return;
        }

        if (event.isShiftClick()) {
            event.setCancelled(true);
            handleShiftTransfer(event, top);
            refreshMenu(top);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof AwakeningMenuHolder)) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof AwakeningMenuHolder)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        returnMenuItem(event.getPlayer(), top, ITEM_SLOT);
        returnMenuItem(event.getPlayer(), top, STAR_SLOT);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!(event.getProjectile() instanceof Projectile projectile)) {
            return;
        }

        ItemStack weapon = event.getBow();
        if (!isAwakenedToolOrWeapon(weapon)) {
            return;
        }
        projectile.getPersistentDataContainer().set(keyProjectileAwakened, PersistentDataType.BYTE, (byte) 1);
        pendingTridentLaunches.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAwakenedTridentUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.TRIDENT || !isAwakenedToolOrWeapon(item)) {
            return;
        }

        pendingTridentLaunches.put(event.getPlayer().getUniqueId(), System.currentTimeMillis() + TRIDENT_MARK_WINDOW_MS);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) {
            return;
        }
        if (!(trident.getShooter() instanceof Player player)) {
            return;
        }

        Long expiresAt = pendingTridentLaunches.get(player.getUniqueId());
        if (expiresAt == null) {
            return;
        }
        if (expiresAt < System.currentTimeMillis()) {
            pendingTridentLaunches.remove(player.getUniqueId());
            return;
        }

        trident.getPersistentDataContainer().set(keyProjectileAwakened, PersistentDataType.BYTE, (byte) 1);
        pendingTridentLaunches.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEquipmentChanged(EntityEquipmentChangedEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        boolean armorChanged = false;
        for (EquipmentSlot slot : event.getEquipmentChanges().keySet()) {
            if (isArmorEquipmentSlot(slot)) {
                armorChanged = true;
                break;
            }
        }
        if (!armorChanged) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> syncEquippedAwakeningArmor(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventorySlotChange(PlayerInventorySlotChangeEvent event) {
        Player player = event.getPlayer();
        int slot = event.getSlot();
        if (slot < 0 || slot >= player.getInventory().getSize()) {
            return;
        }

        ItemStack updated = event.getNewItemStack();
        if (!needsAwakeningNormalization(updated)) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> normalizePlayerInventorySlot(player, slot));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (isAwakenedMeleeWeapon(mainHand)) {
                double multiplier = plugin.getConfigManager().awakeningTableWeaponDamageMultiplier;
                if (multiplier > 0.0) {
                    event.setDamage(event.getDamage() * multiplier);
                }
            }
            return;
        }

        if (event.getDamager() instanceof Projectile projectile
            && isMarked(projectile.getPersistentDataContainer(), keyProjectileAwakened)) {
            double multiplier = plugin.getConfigManager().awakeningTableWeaponDamageMultiplier;
            if (multiplier > 0.0) {
                event.setDamage(event.getDamage() * multiplier);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            normalizePlayerAwakeningItems(player);
            syncEquippedAwakeningArmor(player);
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> syncEquippedAwakeningArmor(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof AwakeningMenuHolder) {
            returnMenuItem(player, top, ITEM_SLOT);
            returnMenuItem(player, top, STAR_SLOT);
        }
        pendingTridentLaunches.remove(player.getUniqueId());
        clearEquippedAwakeningArmorBonuses(player);
    }

    private void openMenu(Player player, Location tableLocation) {
        Inventory inventory = Bukkit.createInventory(
            new AwakeningMenuHolder(tableLocation.clone()),
            MENU_SIZE,
            BedrockCompat.menuTitle(player, AWAKENING_MENU_TITLE, "Awakening Table")
        );
        refreshMenu(inventory);
        player.openInventory(inventory);
    }

    private void refreshMenu(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot == ITEM_SLOT || slot == ACTION_SLOT || slot == STAR_SLOT || slot == STATUS_SLOT) {
                continue;
            }
            inventory.setItem(slot, fillerPane());
        }

        ItemStack input = inventory.getItem(ITEM_SLOT);
        ItemStack star = inventory.getItem(STAR_SLOT);
        inventory.setItem(STATUS_SLOT, statusIcon(input, star));
        inventory.setItem(ACTION_SLOT, actionIcon(input, star));
    }

    private ItemStack fillerPane() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private ItemStack statusIcon(ItemStack input, ItemStack star) {
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta meta = info.getItemMeta();
        if (meta == null) {
            return info;
        }

        meta.displayName(MM.deserialize("<gold><bold>Awakening Info</bold></gold>"));
        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<gray>Success chance: <white>" + formatPercent(plugin.getConfigManager().awakeningTableSuccessChance) + "</white></gray>"));
        lore.add(MM.deserialize("<gray>Material cost: <white>1 Nether Star</white></gray>"));
        lore.add(Component.empty());
        if (input == null || input.getType() == Material.AIR) {
            lore.add(MM.deserialize("<yellow>Place a weapon, tool, or armor piece in the left slot.</yellow>"));
        } else if (!isAwakenable(input)) {
            lore.add(MM.deserialize("<red>That item cannot be awakened.</red>"));
        } else if (isAwakened(input)) {
            lore.add(MM.deserialize("<red>That item is already awakened.</red>"));
        } else if (star == null || star.getType() != Material.NETHER_STAR || star.getAmount() <= 0) {
            lore.add(MM.deserialize("<yellow>Place a Nether Star in the right slot.</yellow>"));
        } else {
            lore.add(MM.deserialize("<green>Ready to attempt an awakening.</green>"));
            lore.add(MM.deserialize("<gray>Failure removes <white>" + formatPercent(plugin.getConfigManager().awakeningTableFailureDurabilityLossFraction) + "</white> of remaining durability.</gray>"));
            lore.add(MM.deserialize("<gray>Items under <white>" + formatPercent(plugin.getConfigManager().awakeningTableDestroyThreshold) + "</white> remaining durability are destroyed on failure.</gray>"));
        }
        meta.lore(lore);
        info.setItemMeta(meta);
        return info;
    }

    private ItemStack actionIcon(ItemStack input, ItemStack star) {
        boolean ready = canAttemptAwakening(input, star);
        ItemStack icon = new ItemStack(ready ? Material.NETHER_STAR : Material.BARRIER);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) {
            return icon;
        }

        if (ready) {
            meta.displayName(MM.deserialize("<gradient:#ff6b6b:#c1121f><bold>Awaken Item</bold></gradient>"));
            meta.lore(List.of(
                MM.deserialize("<gray>Click to consume <white>1 Nether Star</white></gray>"),
                MM.deserialize("<gray>and roll for an awakening.</gray>")
            ));
        } else {
            meta.displayName(MM.deserialize("<red><bold>Cannot Awaken Yet</bold></red>"));
            meta.lore(List.of(MM.deserialize("<gray>Insert a valid item and a Nether Star first.</gray>")));
        }
        icon.setItemMeta(meta);
        return icon;
    }

    private void handleMenuSlotInteraction(Player player, Inventory top, int slot) {
        ItemStack current = top.getItem(slot);
        ItemStack cursor = player.getItemOnCursor();

        if (cursor == null || cursor.getType() == Material.AIR) {
            if (current != null && current.getType() != Material.AIR) {
                player.setItemOnCursor(current);
                top.setItem(slot, null);
            }
            return;
        }

        if (!acceptsItem(slot, cursor)) {
            return;
        }

        if (slot == STAR_SLOT) {
            ItemStack updated = cursor.clone();
            if (current != null && current.getType() == Material.NETHER_STAR) {
                int max = updated.getMaxStackSize();
                int transfer = Math.min(cursor.getAmount(), max - current.getAmount());
                if (transfer <= 0) {
                    return;
                }
                current.setAmount(current.getAmount() + transfer);
                cursor.setAmount(cursor.getAmount() - transfer);
                top.setItem(slot, current);
                player.setItemOnCursor(cursor.getAmount() <= 0 ? null : cursor);
                return;
            }

            top.setItem(slot, updated);
            player.setItemOnCursor(current == null || current.getType() == Material.AIR ? null : current);
            return;
        }

        ItemStack placed = cursor.clone();
        placed.setAmount(1);
        top.setItem(slot, placed);
        if (cursor.getAmount() <= 1) {
            player.setItemOnCursor(current == null || current.getType() == Material.AIR ? null : current);
            return;
        }

        cursor.setAmount(cursor.getAmount() - 1);
        player.setItemOnCursor(cursor);
        if (current != null && current.getType() != Material.AIR) {
            returnItem(player, current);
        }
    }

    private void handleShiftTransfer(InventoryClickEvent event, Inventory top) {
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) {
            return;
        }
        if (current.getType() == Material.NETHER_STAR && isEmpty(top.getItem(STAR_SLOT))) {
            top.setItem(STAR_SLOT, current.clone());
            event.setCurrentItem(null);
            return;
        }
        if (isAwakenable(current) && isEmpty(top.getItem(ITEM_SLOT))) {
            ItemStack moved = current.clone();
            moved.setAmount(1);
            top.setItem(ITEM_SLOT, moved);
            if (current.getAmount() <= 1) {
                event.setCurrentItem(null);
            } else {
                current.setAmount(current.getAmount() - 1);
                event.setCurrentItem(current);
            }
        }
    }

    private void attemptAwakening(Player player, Inventory top, AwakeningMenuHolder holder) {
        ItemStack input = top.getItem(ITEM_SLOT);
        ItemStack star = top.getItem(STAR_SLOT);
        if (!canAttemptAwakening(input, star)) {
            player.sendMessage(MessageUtil.warn("Place a valid item and a Nether Star into the Awakening Table first."));
            refreshMenu(top);
            return;
        }
        if (!isAwakeningTableBlock(holder.tableLocation().getBlock())) {
            player.sendMessage(MessageUtil.error("That Awakening Table no longer exists."));
            player.closeInventory();
            return;
        }

        consumeOne(top, STAR_SLOT);
        ItemStack working = input.clone();
        if (ThreadLocalRandom.current().nextDouble() < plugin.getConfigManager().awakeningTableSuccessChance) {
            ItemStack awakened = awakenItem(working);
            top.setItem(ITEM_SLOT, awakened);
            player.sendMessage(MessageUtil.success("Awakening succeeded. Your item is now awakened."));
            playSuccessAnimation(player, holder.tableLocation());
            announceAwakening(player, awakened);
        } else {
            ItemStack failed = failAwakening(working);
            if (failed == null || failed.getType() == Material.AIR) {
                top.setItem(ITEM_SLOT, null);
                player.sendMessage(MessageUtil.error("The awakening failed and the item shattered."));
                playFailureAnimation(holder.tableLocation());
            } else {
                top.setItem(ITEM_SLOT, failed);
                player.sendMessage(MessageUtil.warn("The awakening failed and your item was heavily damaged."));
                playFailureAnimation(holder.tableLocation());
            }
        }
        refreshMenu(top);
    }

    private ItemStack preserveVanillaAwakeningResult(ItemStack source, ItemStack result) {
        ItemStack updated = result.clone();
        ItemMeta sourceMeta = source.getItemMeta();
        ItemMeta resultMeta = updated.getItemMeta();
        if (sourceMeta == null || resultMeta == null) {
            return updated;
        }

        copyAwakeningState(sourceMeta, resultMeta);
        Component baseName = preservedResultBaseName(sourceMeta, resultMeta, source.getType(), updated.getType());
        storeBaseName(resultMeta, baseName);
        applyVanillaPresentation(resultMeta, updated.getType(), baseName);
        updated.setItemMeta(resultMeta);
        return updated;
    }

    private ItemStack awakenItem(ItemStack item) {
        ItemStack updated = item.clone();
        ItemMeta meta = updated.getItemMeta();
        if (meta == null) {
            return updated;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyAwakened, PersistentDataType.BYTE, (byte) 1);
        pdc.remove(keyRestoreUnbreakable);
        boolean managedByOtherSystem = isManagedByOtherSystem(updated);
        if (!managedByOtherSystem) {
            storeBaseName(meta, visibleBaseName(meta, updated.getType()));
            if (plugin.getConfigManager().awakeningTableRepairVanillaOnSuccess && meta instanceof Damageable damageable) {
                damageable.setDamage(0);
            }
        }
        updated.setItemMeta(meta);
        reapplyPresentation(updated);
        return updated;
    }

    private ItemStack failAwakening(ItemStack item) {
        if (plugin.getSuperpowerManager() != null && plugin.getSuperpowerManager().isAncientScroll(item)) {
            return null;
        }
        if (!(item.getItemMeta() instanceof Damageable damageable)) {
            return item;
        }

        short maxDurability = item.getType().getMaxDurability();
        if (maxDurability <= 0) {
            return item;
        }

        ItemStack updated = item.clone();
        ItemMeta meta = updated.getItemMeta();
        if (!(meta instanceof Damageable updatedDamageable)) {
            return updated;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (meta.isUnbreakable()) {
            pdc.set(keyRestoreUnbreakable, PersistentDataType.BYTE, (byte) 1);
        } else {
            pdc.remove(keyRestoreUnbreakable);
        }
        pdc.remove(keyAwakened);
        pdc.remove(keyBaseName);

        int currentDamage = Math.max(0, damageable.getDamage());
        int remainingDurability = Math.max(0, maxDurability - currentDamage);
        int durabilityLoss = Math.max(
            1,
            (int) Math.ceil(remainingDurability * plugin.getConfigManager().awakeningTableFailureDurabilityLossFraction)
        );
        int newDamage = Math.min(maxDurability, currentDamage + durabilityLoss);
        int remainingAfter = Math.max(0, maxDurability - newDamage);

        double thresholdFraction = plugin.getConfigManager().awakeningTableDestroyThreshold;
        if (remainingAfter <= 0 || (remainingAfter / (double) maxDurability) < thresholdFraction) {
            return null;
        }

        updatedDamageable.setDamage(newDamage);
        updatedDamageable.setUnbreakable(false);
        updatedDamageable.removeItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        updated.setItemMeta(meta);
        reapplyPresentation(updated);
        return updated;
    }

    private void reapplyPresentation(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        LegendaryListener legendary = plugin.getLegendaryListener();
        if (legendary != null && legendary.refreshLegendaryItem(item)) {
            return;
        }

        CustomToolListener customTools = plugin.getCustomToolListener();
        if (customTools != null && customTools.refreshCustomToolItem(item)) {
            return;
        }

        refreshVanillaPresentation(item);
    }

    private void refreshVanillaPresentation(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !hasAwakeningState(meta)) {
            return;
        }

        Component baseName = storedBaseName(meta);
        if (baseName == null) {
            baseName = visibleBaseName(meta, item.getType());
            storeBaseName(meta, baseName);
        }
        applyVanillaPresentation(meta, item.getType(), baseName);
        item.setItemMeta(meta);
    }

    private void applyVanillaPresentation(ItemMeta meta, Material material, Component baseName) {
        applyManagedItemState(meta, material, baseName, false);
    }

    private void applyUnbreakableState(ItemMeta meta, boolean defaultUnbreakable, boolean awakened, boolean restoreUnbreakable) {
        if (defaultUnbreakable) {
            boolean unbreakable = awakened || !restoreUnbreakable;
            meta.setUnbreakable(unbreakable);
            if (unbreakable) {
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            } else {
                meta.removeItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            }
            return;
        }

        meta.setUnbreakable(false);
        meta.removeItemFlags(ItemFlag.HIDE_UNBREAKABLE);
    }

    private void applyAwakeningAttributeBonuses(ItemMeta meta, Material material, AwakeningBaseStats baseStats) {
        if (isToolOrWeapon(material)) {
            applyAttackSpeedBonus(meta, baseStats.attackSpeedBase());
        }
    }

    private void applyAwakeningLore(ItemMeta meta, Material material, boolean awakened, AwakeningBaseStats baseStats) {
        stripManagedAwakeningLore(meta);
        if (!awakened) {
            return;
        }

        List<Component> bonusLore = buildAwakeningBonusLore(material, baseStats);
        if (bonusLore.isEmpty()) {
            return;
        }

        List<Component> lore = meta.hasLore() && meta.lore() != null
            ? new ArrayList<>(meta.lore())
            : new ArrayList<>();
        if (!lore.isEmpty()) {
            lore.add(Component.empty());
        }
        lore.add(AWAKENING_BONUS_HEADER);
        lore.addAll(bonusLore);
        meta.lore(lore);
    }

    private void stripManagedAwakeningLore(ItemMeta meta) {
        List<Component> lore = meta.hasLore() && meta.lore() != null
            ? new ArrayList<>(meta.lore())
            : null;
        if (lore == null || lore.isEmpty()) {
            return;
        }

        List<Component> cleaned = new ArrayList<>();
        int index = 0;
        while (index < lore.size()) {
            String plain = plainLoreLine(lore.get(index));
            if (!AWAKENING_BONUS_HEADER_PLAIN.equalsIgnoreCase(plain)) {
                cleaned.add(lore.get(index));
                index++;
                continue;
            }

            if (!cleaned.isEmpty() && plainLoreLine(cleaned.get(cleaned.size() - 1)).isBlank()) {
                cleaned.remove(cleaned.size() - 1);
            }
            index++;
            while (index < lore.size() && isManagedAwakeningBonusLine(plainLoreLine(lore.get(index)))) {
                index++;
            }
        }

        meta.lore(cleaned);
    }

    private List<Component> buildAwakeningBonusLore(Material material, AwakeningBaseStats baseStats) {
        List<Component> lore = new ArrayList<>();
        if (isToolOrWeapon(material)) {
            double damageMultiplier = plugin.getConfigManager().awakeningTableWeaponDamageMultiplier;
            if (damageMultiplier > 1.0) {
                lore.add(MM.deserialize("<gray>Damage Multiplier: <red>x" + formatAmount(damageMultiplier) + "</red></gray>"));
            }
            double attackSpeedMultiplier = plugin.getConfigManager().awakeningTableAttackSpeedMultiplier;
            if (attackSpeedMultiplier > 1.0 && !Double.isNaN(baseStats.attackSpeedBase())) {
                lore.add(MM.deserialize("<gray>Attack Speed Multiplier: <red>x" + formatAmount(attackSpeedMultiplier) + "</red></gray>"));
            }
        }
        if (isArmor(material)) {
            appendArmorTotalLore(
                lore,
                baseStats.armorBase(),
                plugin.getConfigManager().awakeningTableArmorMultiplier,
                "Armor"
            );
            appendArmorTotalLore(
                lore,
                baseStats.armorToughnessBase(),
                plugin.getConfigManager().awakeningTableArmorToughnessMultiplier,
                "Armor Toughness"
            );
            appendArmorTotalLore(
                lore,
                baseStats.knockbackResistanceBase(),
                plugin.getConfigManager().awakeningTableKnockbackResistanceMultiplier,
                "Knockback Resistance"
            );
        }
        return lore;
    }

    private void appendArmorTotalLore(List<Component> lore, double baseAmount, double multiplier, String label) {
        if (multiplier <= 1.0) {
            return;
        }

        double bonus = awakeningArmorBonus(baseAmount, multiplier);
        if (bonus <= 0.0) {
            return;
        }
        double total = baseAmount + bonus;
        lore.add(MM.deserialize(
            "<gray>" + label + " Total: <red>" + formatAmount(total) + "</red> <dark_gray>(base "
                + formatAmount(baseAmount) + " + " + formatAmount(bonus) + ")</dark_gray></gray>"
        ));
    }

    private double awakeningArmorBonus(double baseAmount, double multiplier) {
        if (Double.isNaN(baseAmount) || baseAmount == 0.0) {
            return 0.0;
        }
        return baseAmount * (multiplier - 1.0);
    }

    private String plainLoreLine(Component line) {
        return line == null ? "" : PLAIN.serialize(line).trim();
    }

    private boolean isManagedAwakeningBonusLine(String plain) {
        return plain.isBlank()
            || plain.startsWith("Damage: ")
            || plain.startsWith("Damage Multiplier: ")
            || plain.startsWith("Attack Speed: ")
            || plain.startsWith("Attack Speed Multiplier: ")
            || plain.startsWith("Armor: ")
            || plain.startsWith("Armor Total: ")
            || plain.startsWith("Armor Toughness: ")
            || plain.startsWith("Armor Toughness Total: ")
            || plain.startsWith("Knockback Resistance: ")
            || plain.startsWith("Knockback Resistance Total: ");
    }

    private void applyAttackSpeedBonus(ItemMeta meta, double baseModifier) {
        double multiplier = plugin.getConfigManager().awakeningTableAttackSpeedMultiplier;
        if (multiplier <= 1.0) {
            return;
        }

        if (Double.isNaN(baseModifier)) {
            return;
        }

        double currentTotal = PLAYER_BASE_ATTACK_SPEED + baseModifier;
        double desiredTotal = currentTotal * multiplier;
        double extraAmount = desiredTotal - currentTotal;
        if (extraAmount <= 0.0) {
            return;
        }

        meta.addAttributeModifier(
            Attribute.ATTACK_SPEED,
            new AttributeModifier(
                keyAttackSpeedModifier,
                extraAmount,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND
            )
        );
    }

    private void removeAwakeningAttributeModifiers(ItemMeta meta) {
        removeAttributeModifier(meta, Attribute.ATTACK_SPEED, keyAttackSpeedModifier);
        removeAttributeModifier(meta, Attribute.ARMOR, keyArmorModifier);
        removeAttributeModifier(meta, Attribute.ARMOR_TOUGHNESS, keyArmorToughnessModifier);
        removeAttributeModifier(meta, Attribute.KNOCKBACK_RESISTANCE, keyKnockbackResistanceModifier);
    }

    private void removeAttributeModifier(ItemMeta meta, Attribute attribute, NamespacedKey key) {
        Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(attribute);
        if (modifiers == null || modifiers.isEmpty()) {
            return;
        }

        List<AttributeModifier> toRemove = new ArrayList<>();
        for (AttributeModifier modifier : modifiers) {
            if (key.equals(modifier.getKey())) {
                toRemove.add(modifier);
            }
        }
        for (AttributeModifier modifier : toRemove) {
            meta.removeAttributeModifier(attribute, modifier);
        }
    }

    private boolean hasAttributeModifier(ItemMeta meta, Attribute attribute, NamespacedKey key) {
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

    private void normalizePlayerAwakeningItems(Player player) {
        if (player == null) {
            return;
        }

        normalizeInventory(player.getInventory());
        normalizeInventory(player.getEnderChest());
    }

    private void normalizePlayerInventorySlot(Player player, int slot) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (slot < 0 || slot >= player.getInventory().getSize()) {
            return;
        }

        ItemStack item = player.getInventory().getItem(slot);
        if (!needsAwakeningNormalization(item)) {
            return;
        }

        ItemStack refreshed = normalizeAwakeningItem(item);
        if (refreshed != item) {
            player.getInventory().setItem(slot, refreshed);
        }
    }

    private void normalizeInventory(Inventory inventory) {
        if (inventory == null) {
            return;
        }

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!needsAwakeningNormalization(item)) {
                continue;
            }
            inventory.setItem(slot, normalizeAwakeningItem(item));
        }
    }

    private boolean needsAwakeningNormalization(ItemStack item) {
        return hasAwakeningState(item)
            && (hasAwakeningArmorItemModifiers(item) || hasLegacyAwakeningLore(item));
    }

    private ItemStack normalizeAwakeningItem(ItemStack item) {
        if (!hasAwakeningState(item)) {
            return item;
        }

        ItemStack refreshed = item.clone();
        reapplyPresentation(refreshed);
        return refreshed;
    }

    private boolean hasLegacyAwakeningLore(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null || !meta.hasLore() || meta.lore() == null) {
            return false;
        }

        for (Component line : meta.lore()) {
            String plain = plainLoreLine(line);
            if (plain.startsWith("Damage: ")
                || plain.startsWith("Attack Speed: ")
                || plain.startsWith("Armor: ")
                || plain.startsWith("Armor Toughness: ")
                || plain.startsWith("Knockback Resistance: ")) {
                return true;
            }
        }
        return false;
    }

    private void syncEquippedAwakeningArmor(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        sanitizeEquippedAwakenedArmor(player);
        if (player.isDead() || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            clearEquippedAwakeningArmorBonuses(player);
            return;
        }

        ArmorBonuses bonuses = collectEquippedAwakeningArmorBonuses(player);
        syncPlayerAttributeModifier(player, Attribute.ARMOR, keyEquippedArmorBonusModifier, bonuses.armor());
        syncPlayerAttributeModifier(player, Attribute.ARMOR_TOUGHNESS, keyEquippedArmorToughnessBonusModifier, bonuses.armorToughness());
        syncPlayerAttributeModifier(player, Attribute.KNOCKBACK_RESISTANCE, keyEquippedKnockbackResistanceBonusModifier, bonuses.knockbackResistance());
    }

    private void sanitizeEquippedAwakenedArmor(Player player) {
        sanitizeEquippedAwakenedArmor(player, EquipmentSlot.HEAD);
        sanitizeEquippedAwakenedArmor(player, EquipmentSlot.CHEST);
        sanitizeEquippedAwakenedArmor(player, EquipmentSlot.LEGS);
        sanitizeEquippedAwakenedArmor(player, EquipmentSlot.FEET);
    }

    private void sanitizeEquippedAwakenedArmor(Player player, EquipmentSlot slot) {
        ItemStack equipped = equippedArmorItem(player, slot);
        if (equipped == null || equipped.getType() == Material.AIR || !hasAwakeningArmorItemModifiers(equipped)) {
            return;
        }

        ItemStack refreshed = equipped.clone();
        reapplyPresentation(refreshed);
        setEquippedArmorItem(player, slot, refreshed);
    }

    private boolean hasAwakeningArmorItemModifiers(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !isArmor(item.getType())) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta != null && (
            hasAttributeModifier(meta, Attribute.ARMOR, keyArmorModifier)
                || hasAttributeModifier(meta, Attribute.ARMOR_TOUGHNESS, keyArmorToughnessModifier)
                || hasAttributeModifier(meta, Attribute.KNOCKBACK_RESISTANCE, keyKnockbackResistanceModifier)
        );
    }

    private ArmorBonuses collectEquippedAwakeningArmorBonuses(Player player) {
        double armorBonus = 0.0;
        double armorToughnessBonus = 0.0;
        double knockbackResistanceBonus = 0.0;

        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack equipped = equippedArmorItem(player, slot);
            if (!isAwakenedArmor(equipped)) {
                continue;
            }

            AwakeningBaseStats baseStats = collectItemBaseStatsWithoutAwakening(equipped);
            armorBonus += awakeningArmorBonus(baseStats.armorBase(), plugin.getConfigManager().awakeningTableArmorMultiplier);
            armorToughnessBonus += awakeningArmorBonus(baseStats.armorToughnessBase(), plugin.getConfigManager().awakeningTableArmorToughnessMultiplier);
            knockbackResistanceBonus += awakeningArmorBonus(baseStats.knockbackResistanceBase(), plugin.getConfigManager().awakeningTableKnockbackResistanceMultiplier);
        }

        return new ArmorBonuses(armorBonus, armorToughnessBonus, knockbackResistanceBonus);
    }

    private AwakeningBaseStats collectItemBaseStatsWithoutAwakening(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return new AwakeningBaseStats(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }

        ItemStack cloned = item.clone();
        ItemMeta meta = cloned.getItemMeta();
        if (meta == null) {
            return new AwakeningBaseStats(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }

        removeAwakeningAttributeModifiers(meta);
        return collectBaseStats(meta, cloned.getType());
    }

    private void clearEquippedAwakeningArmorBonuses(Player player) {
        syncPlayerAttributeModifier(player, Attribute.ARMOR, keyEquippedArmorBonusModifier, 0.0);
        syncPlayerAttributeModifier(player, Attribute.ARMOR_TOUGHNESS, keyEquippedArmorToughnessBonusModifier, 0.0);
        syncPlayerAttributeModifier(player, Attribute.KNOCKBACK_RESISTANCE, keyEquippedKnockbackResistanceBonusModifier, 0.0);
    }

    private void syncPlayerAttributeModifier(Player player, Attribute attribute, NamespacedKey key, double amount) {
        var instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }

        AttributeModifier existing = null;
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (key.equals(modifier.getKey())) {
                existing = modifier;
                break;
            }
        }

        if (amount <= 0.0) {
            if (existing != null) {
                instance.removeModifier(existing);
            }
            return;
        }

        if (existing != null) {
            if (Math.abs(existing.getAmount() - amount) < 0.0001D) {
                return;
            }
            instance.removeModifier(existing);
        }

        instance.addModifier(new AttributeModifier(
            key,
            amount,
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlotGroup.ANY
        ));
    }

    private ItemStack equippedArmorItem(Player player, EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> player.getInventory().getHelmet();
            case CHEST -> player.getInventory().getChestplate();
            case LEGS -> player.getInventory().getLeggings();
            case FEET -> player.getInventory().getBoots();
            default -> null;
        };
    }

    private void setEquippedArmorItem(Player player, EquipmentSlot slot, ItemStack item) {
        switch (slot) {
            case HEAD -> player.getInventory().setHelmet(item);
            case CHEST -> player.getInventory().setChestplate(item);
            case LEGS -> player.getInventory().setLeggings(item);
            case FEET -> player.getInventory().setBoots(item);
            default -> {
            }
        }
    }

    private boolean isArmorEquipmentSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD
            || slot == EquipmentSlot.CHEST
            || slot == EquipmentSlot.LEGS
            || slot == EquipmentSlot.FEET;
    }

    private boolean isAwakenedArmor(ItemStack item) {
        return item != null
            && item.getType() != Material.AIR
            && isArmor(item.getType())
            && isAwakened(item);
    }

    private double defaultAttributeAmount(Material material, EquipmentSlot slot, Attribute attribute) {
        Collection<AttributeModifier> modifiers = material.getDefaultAttributeModifiers(slot).get(attribute);
        if (modifiers == null || modifiers.isEmpty()) {
            return Double.NaN;
        }

        double total = 0.0;
        for (AttributeModifier modifier : modifiers) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
                total += modifier.getAmount();
            }
        }
        return total;
    }

    private double itemAttributeAmount(ItemMeta meta, Material material, EquipmentSlot slot, Attribute attribute) {
        boolean found = false;
        double total = 0.0;

        double defaults = defaultAttributeAmount(material, slot, attribute);
        if (!Double.isNaN(defaults)) {
            total += defaults;
            found = true;
        }

        Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(attribute);
        if (modifiers != null) {
            for (AttributeModifier modifier : modifiers) {
                if (modifier.getOperation() != AttributeModifier.Operation.ADD_NUMBER) {
                    continue;
                }
                total += modifier.getAmount();
                found = true;
            }
        }

        return found ? total : Double.NaN;
    }

    private AwakeningBaseStats collectBaseStats(ItemMeta meta, Material material) {
        EquipmentSlot slot = material.getEquipmentSlot();
        double attackSpeedBase = isToolOrWeapon(material)
            ? itemAttributeAmount(meta, material, EquipmentSlot.HAND, Attribute.ATTACK_SPEED)
            : Double.NaN;
        double armorBase = isArmor(material)
            ? itemAttributeAmount(meta, material, slot, Attribute.ARMOR)
            : Double.NaN;
        double armorToughnessBase = isArmor(material)
            ? itemAttributeAmount(meta, material, slot, Attribute.ARMOR_TOUGHNESS)
            : Double.NaN;
        double knockbackResistanceBase = isArmor(material)
            ? itemAttributeAmount(meta, material, slot, Attribute.KNOCKBACK_RESISTANCE)
            : Double.NaN;
        return new AwakeningBaseStats(attackSpeedBase, armorBase, armorToughnessBase, knockbackResistanceBase);
    }

    private boolean canAttemptAwakening(ItemStack input, ItemStack star) {
        return isAwakenable(input)
            && !isAwakened(input)
            && star != null
            && star.getType() == Material.NETHER_STAR
            && star.getAmount() > 0;
    }

    private boolean acceptsItem(int slot, ItemStack item) {
        if (slot == ITEM_SLOT) {
            return isAwakenable(item) && item.getAmount() > 0;
        }
        if (slot == STAR_SLOT) {
            return item != null && item.getType() == Material.NETHER_STAR;
        }
        return false;
    }

    private boolean isAwakenable(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            return false;
        }
        return item.getType().getMaxDurability() > 0
            && (isArmor(item.getType()) || isToolOrWeapon(item.getType()));
    }

    private boolean isToolOrWeapon(Material material) {
        return Tag.ITEMS_SWORDS.isTagged(material)
            || Tag.ITEMS_AXES.isTagged(material)
            || Tag.ITEMS_PICKAXES.isTagged(material)
            || Tag.ITEMS_SHOVELS.isTagged(material)
            || Tag.ITEMS_HOES.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_WEAPON.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_TRIDENT.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_BOW.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_CROSSBOW.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_MACE.isTagged(material);
    }

    private boolean isMeleeToolOrWeapon(Material material) {
        return Tag.ITEMS_SWORDS.isTagged(material)
            || Tag.ITEMS_AXES.isTagged(material)
            || Tag.ITEMS_PICKAXES.isTagged(material)
            || Tag.ITEMS_SHOVELS.isTagged(material)
            || Tag.ITEMS_HOES.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_TRIDENT.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_MACE.isTagged(material);
    }

    private boolean isArmor(Material material) {
        return Tag.ITEMS_HEAD_ARMOR.isTagged(material)
            || Tag.ITEMS_CHEST_ARMOR.isTagged(material)
            || Tag.ITEMS_LEG_ARMOR.isTagged(material)
            || Tag.ITEMS_FOOT_ARMOR.isTagged(material)
            || material == Material.ELYTRA;
    }

    private boolean isAwakenedToolOrWeapon(ItemStack item) {
        return isAwakened(item) && item != null && isToolOrWeapon(item.getType());
    }

    private boolean isAwakenedMeleeWeapon(ItemStack item) {
        return isAwakened(item) && item != null && isMeleeToolOrWeapon(item.getType());
    }

    private boolean hasAwakeningState(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return hasAwakeningState(meta);
    }

    private boolean hasAwakeningState(ItemMeta meta) {
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return isMarked(pdc, keyAwakened) || isMarked(pdc, keyRestoreUnbreakable) || pdc.has(keyBaseName, PersistentDataType.STRING);
    }

    public boolean isAwakened(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        return isAwakened(item.getItemMeta());
    }

    public boolean isAwakened(ItemMeta meta) {
        return meta != null && isMarked(meta.getPersistentDataContainer(), keyAwakened);
    }

    private boolean isManagedByOtherSystem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        LegendaryListener legendary = plugin.getLegendaryListener();
        if (legendary != null && legendary.isLegendaryItem(item)) {
            return true;
        }
        CustomToolListener customTools = plugin.getCustomToolListener();
        return customTools != null && customTools.isCustomTool(item);
    }

    private boolean isAwakeningTableItem(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTING_TABLE) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && isMarked(meta.getPersistentDataContainer(), keyAwakeningTableItem);
    }

    private boolean isAwakeningTableBlock(Block block) {
        if (block == null || block.getType() != Material.ENCHANTING_TABLE) {
            return false;
        }
        if (!(block.getState() instanceof EnchantingTable enchantingTable)) {
            return false;
        }
        return isMarked(enchantingTable.getPersistentDataContainer(), keyAwakeningTableBlock);
    }

    private boolean isMarked(PersistentDataContainer pdc, NamespacedKey key) {
        Byte marker = pdc.get(key, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private void copyByte(ItemMeta sourceMeta, ItemMeta targetMeta, NamespacedKey key) {
        PersistentDataContainer source = sourceMeta.getPersistentDataContainer();
        PersistentDataContainer target = targetMeta.getPersistentDataContainer();
        Byte value = source.get(key, PersistentDataType.BYTE);
        if (value == null) {
            target.remove(key);
        } else {
            target.set(key, PersistentDataType.BYTE, value);
        }
    }

    private void copyString(ItemMeta sourceMeta, ItemMeta targetMeta, NamespacedKey key) {
        PersistentDataContainer source = sourceMeta.getPersistentDataContainer();
        PersistentDataContainer target = targetMeta.getPersistentDataContainer();
        String value = source.get(key, PersistentDataType.STRING);
        if (value == null || value.isBlank()) {
            target.remove(key);
        } else {
            target.set(key, PersistentDataType.STRING, value);
        }
    }

    private Component visibleBaseName(ItemMeta meta, Material material) {
        Component stored = storedBaseName(meta);
        if (stored != null) {
            return stored;
        }
        if (meta != null && meta.hasDisplayName()) {
            return Component.text(PLAIN.serialize(meta.displayName()));
        }
        return defaultDisplayName(material);
    }

    private Component preservedResultBaseName(ItemMeta sourceMeta, ItemMeta resultMeta, Material sourceMaterial, Material resultMaterial) {
        Component stored = storedBaseName(sourceMeta);
        Component sourceVisible = sourceMeta != null && sourceMeta.hasDisplayName()
            ? Component.text(PLAIN.serialize(sourceMeta.displayName()))
            : defaultDisplayName(sourceMaterial);
        Component resultVisible = resultMeta != null && resultMeta.hasDisplayName()
            ? Component.text(PLAIN.serialize(resultMeta.displayName()))
            : defaultDisplayName(resultMaterial);

        if (stored != null && PLAIN.serialize(sourceVisible).equals(PLAIN.serialize(resultVisible))) {
            return stored;
        }
        return resultVisible;
    }

    private Component storedBaseName(ItemMeta meta) {
        if (meta == null) {
            return null;
        }
        String stored = meta.getPersistentDataContainer().get(keyBaseName, PersistentDataType.STRING);
        if (stored == null || stored.isBlank()) {
            return null;
        }
        return Component.text(stored);
    }

    private void storeBaseName(ItemMeta meta, Component baseName) {
        if (meta == null || baseName == null) {
            return;
        }
        String plain = PLAIN.serialize(baseName).trim();
        if (plain.isBlank()) {
            meta.getPersistentDataContainer().remove(keyBaseName);
            return;
        }
        meta.getPersistentDataContainer().set(keyBaseName, PersistentDataType.STRING, plain);
    }

    private Component defaultDisplayName(Material material) {
        return Component.text(prettyMaterialName(material));
    }

    private String prettyMaterialName(Material material) {
        String raw = material.name().toLowerCase(Locale.ROOT);
        String[] parts = raw.split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            out.append(part.substring(1));
        }
        return out.toString();
    }

    private void consumeOne(Inventory inventory, int slot) {
        ItemStack stack = inventory.getItem(slot);
        if (stack == null || stack.getType() == Material.AIR) {
            return;
        }
        if (stack.getAmount() <= 1) {
            inventory.setItem(slot, null);
            return;
        }
        stack.setAmount(stack.getAmount() - 1);
        inventory.setItem(slot, stack);
    }

    private void returnMenuItem(org.bukkit.entity.HumanEntity viewer, Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        inventory.setItem(slot, null);
        returnItem(viewer, item);
    }

    private void returnItem(org.bukkit.entity.HumanEntity viewer, ItemStack item) {
        if (!(viewer instanceof Player player)) {
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    private void dropExplodedAwakeningTables(List<Block> blocks) {
        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (!isAwakeningTableBlock(block)) {
                continue;
            }
            iterator.remove();
            removeAwakeningTableHolograms(block.getLocation());
            block.setType(Material.AIR, false);
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), createAwakeningTableItem());
        }
    }

    private void syncLoadedTableHolograms() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                syncChunkTableHolograms(chunk);
            }
        }
    }

    private void syncChunkTableHolograms(Chunk chunk) {
        removeStaleChunkHolograms(chunk, false);
        if (!plugin.getConfigManager().awakeningTableHologramEnabled) {
            return;
        }
        for (BlockState state : chunk.getTileEntities()) {
            if (!(state instanceof EnchantingTable enchantingTable)) {
                continue;
            }
            if (!isMarked(enchantingTable.getPersistentDataContainer(), keyAwakeningTableBlock)) {
                continue;
            }
            ensureAwakeningTableHologram(enchantingTable.getLocation());
        }
    }

    private void removeStaleChunkHolograms(Chunk chunk, boolean removeAll) {
        for (Entity entity : chunk.getEntities()) {
            if (!isAwakeningTableHologram(entity)) {
                continue;
            }
            if (removeAll) {
                entity.remove();
                continue;
            }

            Location tableLocation = hologramTableLocation(entity);
            if (tableLocation == null || !isAwakeningTableBlock(tableLocation.getBlock())) {
                entity.remove();
            }
        }
    }

    private void ensureAwakeningTableHologram(Location tableLocation) {
        if (tableLocation == null) {
            return;
        }
        removeAwakeningTableHolograms(tableLocation);
        if (!plugin.getConfigManager().awakeningTableEnabled || !plugin.getConfigManager().awakeningTableHologramEnabled) {
            return;
        }
        if (!isAwakeningTableBlock(tableLocation.getBlock())) {
            return;
        }

        World world = tableLocation.getWorld();
        if (world == null) {
            return;
        }

        Location hologramLocation = tableLocation.clone().add(0.5, plugin.getConfigManager().awakeningTableHologramHeight, 0.5);
        world.spawn(hologramLocation, TextDisplay.class, display -> {
            tagAwakeningTableHologram(display, tableLocation);
            display.text(buildAwakeningTableHologramText());
            display.setGravity(false);
            display.setPersistent(false);
            display.setInvulnerable(true);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(false);
            display.setShadowed(false);
            VisualRangeUtil.applyHologramRange(display, plugin.getConfigManager().awakeningTableHologramViewRange);
            display.setLineWidth(220);
            display.setBackgroundColor(Color.fromARGB(96, 12, 0, 0));
        });
    }

    private Component buildAwakeningTableHologramText() {
        return MM.deserialize(
            "<gradient:#ff8a5b:#ff3d3d><bold>Awakening Table</bold></gradient>\n"
                + "<white>Nether Star</white><gray> Required</gray>\n"
                + "<gray>Success: <white>"
                + formatPercent(plugin.getConfigManager().awakeningTableSuccessChance)
                + "</white></gray>"
        );
    }

    private void tagAwakeningTableHologram(TextDisplay display, Location tableLocation) {
        PersistentDataContainer pdc = display.getPersistentDataContainer();
        pdc.set(keyAwakeningTableHologram, PersistentDataType.BYTE, (byte) 1);
        pdc.set(keyAwakeningTableHologramBlock, PersistentDataType.STRING, awakeningTableBlockKey(tableLocation));
    }

    private void removeAwakeningTableHolograms(Location tableLocation) {
        World world = tableLocation == null ? null : tableLocation.getWorld();
        if (world == null) {
            return;
        }

        String tableKey = awakeningTableBlockKey(tableLocation);
        Chunk chunk = tableLocation.getChunk();
        for (Entity entity : chunk.getEntities()) {
            if (!isAwakeningTableHologram(entity)) {
                continue;
            }
            if (tableKey.equals(hologramBlockKey(entity))) {
                entity.remove();
            }
        }
    }

    private boolean isAwakeningTableHologram(Entity entity) {
        return entity instanceof TextDisplay display
            && isMarked(display.getPersistentDataContainer(), keyAwakeningTableHologram);
    }

    private String hologramBlockKey(Entity entity) {
        return entity.getPersistentDataContainer().get(keyAwakeningTableHologramBlock, PersistentDataType.STRING);
    }

    private Location hologramTableLocation(Entity entity) {
        String blockKey = hologramBlockKey(entity);
        if (blockKey == null || blockKey.isBlank()) {
            return null;
        }

        String[] parts = blockKey.split(":", 4);
        if (parts.length != 4) {
            return null;
        }

        World world;
        try {
            world = Bukkit.getWorld(UUID.fromString(parts[0]));
        } catch (IllegalArgumentException ex) {
            return null;
        }
        if (world == null) {
            return null;
        }

        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String awakeningTableBlockKey(Location location) {
        World world = location == null ? null : location.getWorld();
        if (world == null) {
            return "";
        }
        return world.getUID()
            + ":"
            + location.getBlockX()
            + ":"
            + location.getBlockY()
            + ":"
            + location.getBlockZ();
    }

    private void announceAwakening(Player player, ItemStack item) {
        if (!plugin.getConfigManager().awakeningTableAnnounceSuccess) {
            return;
        }

        Component displayName = item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()
            ? item.getItemMeta().displayName()
            : defaultDisplayName(item == null ? Material.STONE : item.getType());
        Component message = MM.deserialize(
            "<gradient:#ff6b6b:#c1121f><bold>Awakening</bold></gradient><gray> </gray><white>"
                + player.getName()
                + "</white><gray> awakened </gray>"
        ).append(displayName).append(MM.deserialize("<gray>!</gray>"));

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(message);
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    private void playSuccessAnimation(Player player, Location tableLocation) {
        Location center = tableLocation.clone().add(0.5, 1.0, 0.5);
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 74, 74), 1.5f);
        for (int i = 0; i < 3; i++) {
            long delay = i * 3L;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                world.spawnParticle(Particle.DUST, center, 28, 0.45, 0.45, 0.45, 0.0, dust);
                world.spawnParticle(Particle.ENCHANT, center, 40, 0.65, 0.6, 0.65, 0.05);
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0.0, 1.0, 0.0), 16, 0.4, 0.5, 0.4, 0.02);
            }, delay);
        }
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.1f);
        world.playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.15f);
        world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 1.1f);
    }

    private void playFailureAnimation(Location tableLocation) {
        Location center = tableLocation.clone().add(0.5, 1.0, 0.5);
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        world.spawnParticle(Particle.SMOKE, center, 24, 0.45, 0.35, 0.45, 0.02);
        world.spawnParticle(Particle.CRIT, center, 12, 0.35, 0.35, 0.35, 0.02);
        world.playSound(center, Sound.BLOCK_ANVIL_LAND, 0.85f, 0.8f);
        world.playSound(center, Sound.ENTITY_ITEM_BREAK, 0.9f, 0.75f);
    }

    private String formatPercent(double chance) {
        double percent = chance * 100.0;
        if (Math.abs(percent - Math.rint(percent)) < 0.0001) {
            return Long.toString(Math.round(percent)) + "%";
        }
        return String.format(Locale.US, "%.1f%%", percent);
    }

    private String formatPercentAmount(double fraction) {
        return formatAmount(fraction * 100.0) + "%";
    }

    private String formatAmount(double amount) {
        if (Math.abs(amount - Math.rint(amount)) < 0.0001) {
            return Long.toString(Math.round(amount));
        }
        if (Math.abs(amount * 10.0 - Math.rint(amount * 10.0)) < 0.0001) {
            return String.format(Locale.US, "%.1f", amount);
        }
        return String.format(Locale.US, "%.2f", amount);
    }

    private record AwakeningMenuHolder(Location tableLocation) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record ArmorBonuses(double armor, double armorToughness, double knockbackResistance) {
    }

    private record AwakeningBaseStats(
        double attackSpeedBase,
        double armorBase,
        double armorToughnessBase,
        double knockbackResistanceBase
    ) {}
}
