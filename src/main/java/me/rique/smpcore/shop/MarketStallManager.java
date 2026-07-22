package me.rique.smpcore.shop;

import io.papermc.paper.event.player.PlayerOpenSignEvent;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.AtomicYamlFile;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Openable;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MarketStallManager implements Listener {

    private static final long CONFIRM_MILLIS = 15_000L;
    private static final long TRANSFER_MILLIS = 60_000L;
    private static final long MAX_PRICE = 100_000_000L;
    private static final long MAX_VOLUME = 100_000L;
    private static final int MAX_MANAGERS_PER_STALL = 5;
    private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance(Locale.US);

    private final SMPCore plugin;
    private final File file;
    private final MarketStallTemplateStore templateStore;
    private final NamespacedKey wandKey;
    private final NamespacedKey purchaseSignKey;
    private final Map<String, Stall> stalls = new LinkedHashMap<>();
    private final Map<String, String> signIndex = new HashMap<>();
    private final Map<UUID, Map<Long, List<Stall>>> chunkIndex = new HashMap<>();
    private final Map<UUID, Stall> ownershipIndex = new HashMap<>();
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, Confirmation> confirmations = new HashMap<>();
    private final Map<UUID, TransferRequest> transfers = new HashMap<>();

    public MarketStallManager(SMPCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "market-stalls.yml");
        this.templateStore = new MarketStallTemplateStore(plugin);
        this.wandKey = new NamespacedKey(plugin, "market_stall_wand");
        this.purchaseSignKey = new NamespacedKey(plugin, "market_stall_purchase_sign");
    }

    public void start() {
        load();
        for (Stall stall : stalls.values()) updateSign(stall);
        templateStore.load();
        for (Stall stall : stalls.values()) {
            if (templateStore.hasTemplate(stall.id) || stall.ownerId != null || !stall.placedBlocks.isEmpty()) continue;
            MarketStallTemplateStore.CaptureResult captured = templateStore.capture(stall.id, stall.region(), false, false);
            if (captured.success()) {
                plugin.getLogger().info("Captured launch template for market stall '" + stall.id + "' ("
                    + captured.blockCount() + " blocks, SHA-256 " + captured.sha256() + ").");
            } else {
                plugin.getLogger().severe("Could not capture launch template for market stall '" + stall.id + "': " + captured.reason());
            }
        }
    }

    public void shutdown() {
        save();
        selections.clear();
        confirmations.clear();
        transfers.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        selections.remove(playerId);
        confirmations.remove(playerId);
        transfers.entrySet().removeIf(entry ->
            entry.getKey().equals(playerId)
                || entry.getValue().ownerId().equals(playerId)
                || entry.getValue().targetId().equals(playerId)
        );
    }

    public ItemStack createSelectionWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.displayName(MessageUtil.parse("<gold><bold>Market Stall Wand</bold></gold>"));
        meta.lore(CustomLoreUtil.wrapLoreLines(List.of(
            MessageUtil.parse("<gray>Left-click: first corner</gray>"),
            MessageUtil.parse("<gray>Right-click: second corner</gray>"),
            MessageUtil.parse("<dark_gray>Then look at the sale sign and use /stall admin create.</dark_gray>")
        )));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        return wand;
    }

    public boolean isSelectionWand(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    public boolean isPurchaseSign(Block block) {
        if (block == null) return false;
        return signIndex.containsKey(blockKey(block));
    }

    boolean tryRecoverLegacyShopSign(Block block) {
        PlayerShopListener shops = plugin.getPlayerShopListener();
        if (block == null || shops == null || isPurchaseSign(block)) return false;
        Stall stall = stallAt(block.getLocation());
        return stall != null
            && stall.ownerId != null
            && stall.ownerName != null
            && stall.placedBlocks.contains(blockKey(block))
            && isWallSignAttachedToStorageInStall(stall, block)
            && shops.recoverLegacyShopSign(block, stall.ownerId, stall.ownerName);
    }

    public boolean handlesSpawnBlockChange(Block block) {
        return stallAt(block == null ? null : block.getLocation()) != null;
    }

    public boolean allowsSpawnInteraction(PlayerInteractEvent event) {
        if (event == null || event.getClickedBlock() == null) return false;
        if (isSelectionWand(event.getItem()) && isAdmin(event.getPlayer())) return true;
        Block block = event.getClickedBlock();
        if (isPurchaseSign(block)) return true;
        Stall stall = stallAt(block.getLocation());
        if (stall == null || event.getAction() != Action.RIGHT_CLICK_BLOCK) return false;
        if (block.getBlockData() instanceof Openable) return true;
        if (!stall.canManage(event.getPlayer())) return false;
        Material held = event.getItem() == null ? null : event.getItem().getType();
        return stall.placedBlocks.contains(blockKey(block))
            || isOwnerEditableMaterial(block.getType())
            || isPlaceableStorageMaterial(held)
            || isShopSignItem(held);
    }

    public boolean canCreateShopAt(Player player, Block chest, Block sign) {
        if (isPurchaseSign(sign)) return false;
        Stall chestStall = stallAt(chest == null ? null : chest.getLocation());
        Stall signStall = stallAt(sign == null ? null : sign.getLocation());
        if (chestStall == null && signStall == null) return true;
        return chestStall != null
            && chestStall == signStall
            && chestStall.canManage(player)
            && isPlaceableStorageMaterial(chest.getType())
            && chestStall.placedBlocks.contains(blockKey(sign))
            && isWallSignAttachedToStorage(sign, chest);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onStallSignOpen(PlayerOpenSignEvent event) {
        Block block = event.getSign().getBlock();
        if (plugin.getPlayerShopListener() != null && plugin.getPlayerShopListener().isShopPurchaseSign(block)) return;
        Stall stall = stallAt(block.getLocation());
        if (stall == null || canEditStallSign(isAdmin(event.getPlayer()), isPurchaseSign(block), stall.canManage(event.getPlayer()))) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(MessageUtil.warn(isPurchaseSign(block)
            ? "That market sign is managed by the server."
            : "Only this stall's owner or a trusted manager can edit its signs."));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onStallSignChange(SignChangeEvent event) {
        if (plugin.getPlayerShopListener() != null && plugin.getPlayerShopListener().isShopPurchaseSign(event.getBlock())) return;
        Stall stall = stallAt(event.getBlock().getLocation());
        if (stall == null || canEditStallSign(isAdmin(event.getPlayer()), isPurchaseSign(event.getBlock()), stall.canManage(event.getPlayer()))) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(MessageUtil.warn(isPurchaseSign(event.getBlock())
            ? "That market sign is managed by the server."
            : "Only this stall's owner or a trusted manager can edit its signs."));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onStallSignInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || !(event.getClickedBlock().getState() instanceof Sign)) {
            return;
        }
        Block block = event.getClickedBlock();
        if (isPurchaseSign(block) || (plugin.getPlayerShopListener() != null && plugin.getPlayerShopListener().isShopPurchaseSign(block))) {
            return;
        }
        Stall stall = stallAt(block.getLocation());
        if (stall == null || isAdmin(event.getPlayer()) || stall.canManage(event.getPlayer())) return;
        event.setCancelled(true);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        if (event.getHand() == EquipmentSlot.HAND) {
            event.getPlayer().sendMessage(MessageUtil.warn("Only this stall's owner or a trusted manager can edit its signs."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onWandUse(PlayerInteractEvent event) {
        if (!isSelectionWand(event.getItem()) || event.getClickedBlock() == null) return;
        event.setCancelled(true);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        Player player = event.getPlayer();
        if (!isAdmin(player)) {
            player.sendMessage(MessageUtil.warn("Only market admins can use that wand."));
            return;
        }
        Selection selection = selections.computeIfAbsent(player.getUniqueId(), ignored -> new Selection());
        Location point = event.getClickedBlock().getLocation();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selection.first = point;
            player.sendMessage(MessageUtil.success("Stall corner 1 set at <white>" + coordinates(point) + "</white>."));
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            selection.second = point;
            player.sendMessage(MessageUtil.success("Stall corner 2 set at <white>" + coordinates(point) + "</white>."));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.35f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPurchaseSign(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
            || event.getClickedBlock() == null
            || isSelectionWand(event.getItem())
            || !isPurchaseSign(event.getClickedBlock())) return;
        event.setCancelled(true);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        if (isSignModificationItem(event.getItem() == null ? null : event.getItem().getType())) {
            event.getPlayer().sendMessage(MessageUtil.warn("That market sign is managed by the server."));
            return;
        }
        String id = signIndex.get(blockKey(event.getClickedBlock()));
        Stall stall = stalls.get(id);
        if (stall == null) return;
        if (stall.ownerId != null) {
            event.getPlayer().sendMessage(MessageUtil.info("This stall belongs to <white>" + stall.ownerName + "</white>."));
            return;
        }
        confirmPurchase(event.getPlayer(), stall);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlace(BlockPlaceEvent event) {
        Stall stall = stallAt(event.getBlockPlaced().getLocation());
        if (stall == null || isAdmin(event.getPlayer())) return;
        if (!stall.canManage(event.getPlayer())) {
            deny(event.getPlayer(), event, "Only this stall's owner or a trusted manager can place shop fixtures here.");
            return;
        }
        if (!isAllowedPlacement(stall, event.getBlockPlaced())) {
            deny(event.getPlayer(), event, isWallShopSign(event.getBlockPlaced().getType())
                ? "Shop signs must be wall signs attached directly to a chest or trapped chest."
                : "Only chests, trapped chests, and wall signs attached to those chests can be placed here.");
            return;
        }
        if (!event.getBlockReplacedState().getType().isAir()) {
            deny(event.getPlayer(), event, "That part of the premade stall cannot be replaced.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaceComplete(BlockPlaceEvent event) {
        if (isAdmin(event.getPlayer())) return;
        Stall stall = stallAt(event.getBlockPlaced().getLocation());
        if (stall == null || !stall.canManage(event.getPlayer()) || !isAllowedPlacement(stall, event.getBlockPlaced())) return;
        if (stall.placedBlocks.add(blockKey(event.getBlockPlaced())) && !save()) {
            stall.placedBlocks.remove(blockKey(event.getBlockPlaced()));
            event.getBlockPlaced().breakNaturally(event.getItemInHand());
            event.getPlayer().sendMessage(MessageUtil.error("The stall change could not be saved, so the block was returned."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        Stall stall = stallAt(event.getBlock().getLocation());
        if (stall == null || isAdmin(event.getPlayer())) return;
        boolean placedByOwner = stall.placedBlocks.contains(blockKey(event.getBlock()));
        boolean editableFixture = isOwnerEditableMaterial(event.getBlock().getType());
        boolean chestShopSign = isWallSignAttachedToStorageInStall(stall, event.getBlock());
        if (!stall.canManage(event.getPlayer()) || !canOwnerBreakStallBlock(
            isPurchaseSign(event.getBlock()),
            placedByOwner,
            editableFixture,
            chestShopSign
        )) {
            deny(event.getPlayer(), event, "That part of the premade stall is protected. Use chests and the approved shop fixtures instead.");
            return;
        }
        if (!placedByOwner && hasInventoryItems(event.getBlock())) {
            deny(event.getPlayer(), event, "Empty that premade container before removing it.");
            return;
        }
        if (!placedByOwner) {
            event.setDropItems(false);
            event.setExpToDrop(0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakComplete(BlockBreakEvent event) {
        if (isAdmin(event.getPlayer())) return;
        Stall stall = stallAt(event.getBlock().getLocation());
        if (stall != null && stall.placedBlocks.remove(blockKey(event.getBlock())) && !save()) {
            plugin.getLogger().warning("Retrying a market-stall fixture save after a block break.");
            Bukkit.getScheduler().runTaskLater(plugin, this::save, 20L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        Location location = event.getInventory().getLocation();
        Stall stall = stallAt(location);
        if (stall == null || !(event.getPlayer() instanceof Player player) || isAdmin(player) || stall.canManage(player)) return;
        event.setCancelled(true);
        player.sendMessage(MessageUtil.warn("Buy from a chest shop sign. Only the stall owner or a trusted manager can open its storage."));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (stallAt(event.getSource().getLocation()) != null || stallAt(event.getDestination().getLocation()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> stallAt(block.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> stallAt(block.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (stallAt(event.getBlock().getLocation()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (stallAt(event.getBlock().getLocation()) != null || stallAt(event.getToBlock().getLocation()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (stallAt(event.getBlock().getLocation()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (pistonTouchesStall(event.getBlock(), event.getBlocks(), event.getDirection())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (pistonTouchesStall(event.getBlock(), event.getBlocks(), event.getDirection())) event.setCancelled(true);
    }

    public void giveWand(Player player) {
        if (player == null) return;
        player.getInventory().addItem(createSelectionWand()).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage(MessageUtil.success("Market stall wand added. Select two corners, look at the sale sign, then use <white>/stall admin create &lt;id&gt; &lt;price&gt;</white>."));
    }

    public void createStall(Player admin, String requestedId, long price) {
        if (admin == null || !isAdmin(admin)) return;
        String id = normalizeId(requestedId);
        if (id == null) {
            admin.sendMessage(MessageUtil.warn("Use a short id containing only letters, numbers, dashes, or underscores."));
            return;
        }
        if (price <= 0L || price > MAX_PRICE) {
            admin.sendMessage(MessageUtil.warn("The stall price must be between 1 and " + NUMBERS.format(MAX_PRICE) + " Essence."));
            return;
        }
        if (stalls.containsKey(id)) {
            admin.sendMessage(MessageUtil.warn("A stall named '" + id + "' already exists."));
            return;
        }
        Selection selection = selections.get(admin.getUniqueId());
        if (selection == null || selection.first == null || selection.second == null || !sameWorld(selection.first, selection.second)) {
            admin.sendMessage(MessageUtil.warn("Select both stall corners in the same world with <white>/stall admin wand</white>."));
            return;
        }
        Block signBlock = admin.getTargetBlockExact(8);
        if (signBlock == null || !(signBlock.getState() instanceof Sign)) {
            admin.sendMessage(MessageUtil.warn("Look directly at the sign that should sell this stall, then run the command again."));
            return;
        }
        Stall stall = Stall.fromSelection(id, selection.first, selection.second, signBlock, price);
        if (!stall.contains(signBlock.getLocation())) {
            admin.sendMessage(MessageUtil.warn("The sale sign must be inside the selected stall cuboid."));
            return;
        }
        if (stall.volume() > MAX_VOLUME) {
            admin.sendMessage(MessageUtil.warn("That selection is too large. Select only the stall itself."));
            return;
        }
        for (Stall existing : stalls.values()) {
            if (stall.overlaps(existing)) {
                admin.sendMessage(MessageUtil.warn("That selection overlaps stall '" + existing.id + "'."));
                return;
            }
        }
        stalls.put(id, stall);
        rebuildIndexes();
        if (!save()) {
            stalls.remove(id);
            rebuildIndexes();
            admin.sendMessage(MessageUtil.error("The stall could not be saved."));
            return;
        }
        if (!updateSign(stall)) {
            stalls.remove(id);
            rebuildIndexes();
            if (!save()) {
                plugin.getLogger().severe("Could not roll back market stall '" + id + "' after its purchase sign failed to update.");
            }
            admin.sendMessage(MessageUtil.error("The stall was not created because its sale sign could not be updated. Replace the sign and try again."));
            return;
        }
        selections.remove(admin.getUniqueId());
        MarketStallTemplateStore.CaptureResult captured = templateStore.capture(stall.id, stall.region(), false, false);
        if (!captured.success()) {
            admin.sendMessage(MessageUtil.warn("The stall was created, but its restore template was not saved: " + captured.reason()));
        }
        admin.sendMessage(MessageUtil.success("Created stall <white>" + id + "</white> for <white>" + NUMBERS.format(price) + " Essence</white>."));
    }

    public void setPrice(Player admin, String requestedId, long price) {
        Stall stall = stalls.get(normalizeId(requestedId));
        if (admin == null || !isAdmin(admin)) return;
        if (stall == null) {
            admin.sendMessage(MessageUtil.warn("That stall does not exist."));
            return;
        }
        if (price <= 0L || price > MAX_PRICE) {
            admin.sendMessage(MessageUtil.warn("The price must be between 1 and " + NUMBERS.format(MAX_PRICE) + " Essence."));
            return;
        }
        long previous = stall.price;
        stall.price = price;
        if (!save()) {
            stall.price = previous;
            admin.sendMessage(MessageUtil.error("The new price could not be saved."));
            return;
        }
        updateSign(stall);
        admin.sendMessage(MessageUtil.success("Stall <white>" + stall.id + "</white> now costs <white>" + NUMBERS.format(price) + " Essence</white>."));
    }

    public void removeStall(Player admin, String requestedId) {
        if (admin == null || !isAdmin(admin)) return;
        Stall stall = stalls.get(normalizeId(requestedId));
        if (stall == null) {
            admin.sendMessage(MessageUtil.warn("That stall does not exist."));
            return;
        }
        if (stall.ownerId != null || !stall.placedBlocks.isEmpty()) {
            admin.sendMessage(MessageUtil.warn("That stall is owned or still has player fixtures. Empty it before removing the region."));
            return;
        }
        stalls.remove(stall.id);
        rebuildIndexes();
        if (!save()) {
            stalls.put(stall.id, stall);
            rebuildIndexes();
            admin.sendMessage(MessageUtil.error("The stall removal could not be saved."));
            return;
        }
        clearSign(stall);
        admin.sendMessage(MessageUtil.success("Removed stall <white>" + stall.id + "</white>."));
    }

    public void restoreStall(Player admin, String requestedId) {
        if (admin == null || !isAdmin(admin)) return;
        Stall stall = stalls.get(normalizeId(requestedId));
        if (stall == null) {
            admin.sendMessage(MessageUtil.warn("That stall does not exist."));
            return;
        }
        Set<String> forcedFixtures = Set.copyOf(stall.placedBlocks);
        MarketStallTemplateStore.Inspection inspection = templateStore.inspect(stall.id, stall.region(), forcedFixtures);
        if (!inspection.success()) {
            admin.sendMessage(MessageUtil.error(inspection.reason()));
            return;
        }
        if (inspection.blockingContainers() > 0) {
            admin.sendMessage(MessageUtil.warn("Restore blocked: empty " + inspection.blockingContainers() + " changed container(s) holding "
                + inspection.blockingItemStacks() + " item stack(s). Nothing was changed."));
            return;
        }
        if (inspection.changedBlocks() == 0) {
            admin.sendMessage(MessageUtil.info("Stall <white>" + stall.id + "</white> already matches its launch template."));
            return;
        }

        long now = System.currentTimeMillis();
        Confirmation pending = confirmations.get(admin.getUniqueId());
        if (pending == null || pending.type != ConfirmationType.RESTORE || !pending.stallId.equals(stall.id) || pending.expiresAt < now) {
            confirmations.put(admin.getUniqueId(), new Confirmation(ConfirmationType.RESTORE, stall.id, now + CONFIRM_MILLIS));
            admin.sendMessage(MessageUtil.warn("Restore <white>" + stall.id + "</white> to its saved launch state? "
                + inspection.changedBlocks() + " block(s) will change. Run <white>/stall admin restore " + stall.id
                + "</white> again within 15 seconds."));
            admin.sendMessage(MessageUtil.info("Container contents are never saved in templates. Any changed container with items blocks the restore."));
            return;
        }
        confirmations.remove(admin.getUniqueId());

        int[] clearedShops = {0};
        PlayerShopListener shops = plugin.getPlayerShopListener();
        MarketStallTemplateStore.RestoreResult restored = templateStore.restore(stall.id, stall.region(), forcedFixtures, () -> {
            if (shops != null) {
                clearedShops[0] = shops.clearShopsInArea(
                    stall.worldId, stall.minX, stall.maxX, stall.minY, stall.maxY, stall.minZ, stall.maxZ
                );
            }
        });
        if (!restored.success()) {
            admin.sendMessage(MessageUtil.error(restored.reason()));
            return;
        }
        stall.placedBlocks.clear();
        if (!save()) {
            plugin.getLogger().severe("Restored market stall '" + stall.id + "' but could not clear its fixture index. Retrying next tick.");
            Bukkit.getScheduler().runTaskLater(plugin, this::save, 1L);
        }
        updateSign(stall);
        admin.sendMessage(MessageUtil.success("Restored stall <white>" + stall.id + "</white>: " + restored.restoredBlocks()
            + " block(s) reset and " + clearedShops[0] + " chest shop(s) closed."));
        plugin.getLogger().info(admin.getName() + " restored market stall '" + stall.id + "' from template " + restored.sha256() + ".");
    }

    public void snapshotStall(Player admin, String requestedId, boolean confirmed) {
        if (admin == null || !isAdmin(admin)) return;
        Stall stall = stalls.get(normalizeId(requestedId));
        if (stall == null) {
            admin.sendMessage(MessageUtil.warn("That stall does not exist."));
            return;
        }
        if (!confirmed) {
            admin.sendMessage(MessageUtil.warn("Use <white>/stall admin snapshot " + stall.id + " confirm</white> to intentionally replace its launch template."));
            return;
        }
        if (stall.ownerId != null || !stall.placedBlocks.isEmpty()) {
            admin.sendMessage(MessageUtil.warn("Only an unowned stall with no tracked player fixtures can become a launch template."));
            return;
        }
        PlayerShopListener shops = plugin.getPlayerShopListener();
        if (shops != null && shops.countShopsInArea(stall.worldId, stall.minX, stall.maxX, stall.minY, stall.maxY, stall.minZ, stall.maxZ) > 0) {
            admin.sendMessage(MessageUtil.warn("Remove every active chest shop before replacing this template."));
            return;
        }
        MarketStallTemplateStore.CaptureResult captured = templateStore.capture(stall.id, stall.region(), true, true);
        if (!captured.success()) {
            admin.sendMessage(MessageUtil.error(captured.reason()));
            return;
        }
        admin.sendMessage(MessageUtil.success("Saved launch template for <white>" + stall.id + "</white>: " + captured.blockCount()
            + " blocks, SHA-256 <white>" + captured.sha256() + "</white>."));
    }

    public void snapshotAll(Player admin, boolean confirmed) {
        if (admin == null || !isAdmin(admin)) return;
        if (!confirmed) {
            admin.sendMessage(MessageUtil.warn("Use <white>/stall admin snapshotall confirm</white> to replace every eligible launch template."));
            return;
        }
        int saved = 0;
        int skipped = 0;
        for (Stall stall : stalls.values()) {
            if (stall.ownerId != null || !stall.placedBlocks.isEmpty()) {
                skipped++;
                continue;
            }
            PlayerShopListener shops = plugin.getPlayerShopListener();
            if (shops != null && shops.countShopsInArea(stall.worldId, stall.minX, stall.maxX, stall.minY, stall.maxY, stall.minZ, stall.maxZ) > 0) {
                skipped++;
                continue;
            }
            MarketStallTemplateStore.CaptureResult captured = templateStore.capture(stall.id, stall.region(), true, true);
            if (captured.success()) saved++;
            else {
                skipped++;
                plugin.getLogger().warning("Skipped market stall template '" + stall.id + "': " + captured.reason());
            }
        }
        admin.sendMessage(MessageUtil.success("Saved " + saved + " launch stall template(s). " + skipped + " stall(s) were skipped for safety."));
    }

    public void sendAdminList(Player admin) {
        if (admin == null || !isAdmin(admin)) return;
        if (stalls.isEmpty()) {
            admin.sendMessage(MessageUtil.info("No market stalls are configured."));
            return;
        }
        admin.sendMessage(MessageUtil.prefixedRaw("<gold><bold>Market Stalls</bold></gold>"));
        for (Stall stall : stalls.values()) {
            admin.sendMessage(MessageUtil.info("<white>" + stall.id + "</white> - " + NUMBERS.format(stall.price) + " Essence - "
                + (stall.ownerId == null ? "available" : "owned by " + stall.ownerName) + " - " + stall.placedBlocks.size() + " fixtures - template "
                + (templateStore.hasTemplate(stall.id) ? templateStore.templateHash(stall.id).substring(0, 12) : "missing")));
        }
    }

    public void sendStatus(Player player) {
        Stall stall = ownedStall(player == null ? null : player.getUniqueId());
        if (player == null) return;
        if (stall == null) {
            List<Stall> managed = managedStalls(player.getUniqueId());
            if (!managed.isEmpty()) {
                player.sendMessage(MessageUtil.prefixedRaw("<gold><bold>Managed Market Stalls</bold></gold>"));
                for (Stall trusted : managed) {
                    player.sendMessage(MessageUtil.info("Stall <white>" + trusted.id + "</white> for <white>" + trusted.ownerName + "</white>."));
                }
                player.sendMessage(MessageUtil.info("You may stock, open, create, and remove those chest shops. Purchases still charge you normally."));
                return;
            }
            player.sendMessage(MessageUtil.info("You do not own a market stall. Right-click an available stall's sale sign to buy one."));
            player.sendMessage(MessageUtil.info("Shop payments: <white>/shops balance</white> and <white>/shops collect</white>."));
            return;
        }
        player.sendMessage(MessageUtil.prefixedRaw("<gold><bold>Your Market Stall</bold></gold>"));
        player.sendMessage(MessageUtil.info("Stall: <white>" + stall.id + "</white> <dark_gray>|</dark_gray> Price: <white>" + NUMBERS.format(stall.price) + " Essence</white>"));
        player.sendMessage(MessageUtil.info("Place chests, then attach wall signs directly to them. Use <white>/shops</white> for the four sign lines."));
        player.sendMessage(MessageUtil.info("You may remove shop storage, lanterns, furnaces, smokers, crafting tables, bookshelves, and pots. Structural wood and shelves stay protected."));
        player.sendMessage(MessageUtil.info("Trusted managers: <white>" + managerSummary(stall) + "</white>. Toggle access with <white>/stall manager &lt;player&gt;</white>."));
        player.sendMessage(MessageUtil.info("Use <white>/stall transfer &lt;player&gt;</white> or <white>/stall sell</white>. Remove all fixtures before selling it back."));
    }

    public void sendManagers(Player owner) {
        if (owner == null) return;
        Stall stall = ownedStall(owner.getUniqueId());
        if (stall == null) {
            owner.sendMessage(MessageUtil.warn("You do not own a stall."));
            return;
        }
        owner.sendMessage(MessageUtil.info("Trusted stall managers: <white>" + managerSummary(stall) + "</white>."));
        owner.sendMessage(MessageUtil.info("Use <white>/stall manager &lt;player&gt;</white> to grant or revoke access."));
    }

    public void toggleManager(Player owner, String targetName) {
        if (owner == null || targetName == null || targetName.isBlank()) return;
        Stall stall = ownedStall(owner.getUniqueId());
        if (stall == null) {
            owner.sendMessage(MessageUtil.warn("You do not own a stall."));
            return;
        }
        Map.Entry<UUID, String> trustedMatch = stall.managers.entrySet().stream()
            .filter(entry -> entry.getValue().equalsIgnoreCase(targetName.trim()))
            .findFirst()
            .orElse(null);
        OfflinePlayer target = trustedMatch == null
            ? cachedPlayer(targetName)
            : Bukkit.getOfflinePlayer(trustedMatch.getKey());
        if (target == null || target.getUniqueId() == null || (trustedMatch == null && target.getName() == null)) {
            owner.sendMessage(MessageUtil.warn("That player must be online or have joined the server before."));
            return;
        }
        UUID targetId = target.getUniqueId();
        String name = trustedMatch == null ? target.getName() : trustedMatch.getValue();
        if (targetId.equals(owner.getUniqueId())) {
            owner.sendMessage(MessageUtil.warn("You already have full access as the stall owner."));
            return;
        }

        String previous = stall.managers.remove(targetId);
        boolean granting = previous == null;
        if (granting) {
            if (stall.managers.size() >= MAX_MANAGERS_PER_STALL) {
                owner.sendMessage(MessageUtil.warn("A stall can have at most " + MAX_MANAGERS_PER_STALL + " trusted managers."));
                return;
            }
            stall.managers.put(targetId, name);
        }
        if (!save()) {
            if (granting) {
                stall.managers.remove(targetId);
            } else {
                stall.managers.put(targetId, previous);
            }
            owner.sendMessage(MessageUtil.error("That stall permission could not be saved."));
            return;
        }

        owner.sendMessage(granting
            ? MessageUtil.success("<white>" + name + "</white> can now manage stall <white>" + stall.id + "</white>.")
            : MessageUtil.success("Removed <white>" + previous + "</white>'s access to stall <white>" + stall.id + "</white>."));
        Player online = target.getPlayer();
        if (online != null && online.isOnline()) {
            online.sendMessage(granting
                ? MessageUtil.info("<white>" + owner.getName() + "</white> trusted you to manage stall <white>" + stall.id + "</white>. You can still buy from its shops normally.")
                : MessageUtil.info("Your manager access to stall <white>" + stall.id + "</white> was removed."));
        }
    }

    public List<String> managerSuggestions(Player owner) {
        Set<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Bukkit.getOnlinePlayers().forEach(player -> {
            if (owner == null || !player.getUniqueId().equals(owner.getUniqueId())) names.add(player.getName());
        });
        Stall stall = ownedStall(owner == null ? null : owner.getUniqueId());
        if (stall != null) names.addAll(stall.managers.values());
        return List.copyOf(names);
    }

    public void requestTransfer(Player owner, Player target) {
        if (owner == null || target == null || owner.equals(target)) {
            if (owner != null) owner.sendMessage(MessageUtil.warn("Choose another online player."));
            return;
        }
        Stall stall = ownedStall(owner.getUniqueId());
        if (stall == null) {
            owner.sendMessage(MessageUtil.warn("You do not own a stall."));
            return;
        }
        if (hasStall(target.getUniqueId())) {
            owner.sendMessage(MessageUtil.warn(target.getName() + " already owns a stall."));
            return;
        }
        transfers.put(target.getUniqueId(), new TransferRequest(stall.id, owner.getUniqueId(), target.getUniqueId(), System.currentTimeMillis() + TRANSFER_MILLIS));
        owner.sendMessage(MessageUtil.success("Transfer offered to <white>" + target.getName() + "</white>."));
        target.sendMessage(MessageUtil.info("<white>" + owner.getName() + "</white> wants to transfer stall <white>" + stall.id + "</white> to you."));
        target.sendMessage(MessageUtil.info("Use <white>/stall accept</white> or <white>/stall deny</white> within 60 seconds."));
    }

    public void acceptTransfer(Player target) {
        if (target == null) return;
        TransferRequest request = transfers.remove(target.getUniqueId());
        if (request == null || request.expiresAt < System.currentTimeMillis()) {
            target.sendMessage(MessageUtil.warn("You do not have an active stall transfer."));
            return;
        }
        Stall stall = stalls.get(request.stallId);
        if (stall == null || !request.ownerId.equals(stall.ownerId) || !request.targetId.equals(target.getUniqueId())) {
            target.sendMessage(MessageUtil.warn("That stall transfer is no longer valid."));
            return;
        }
        if (hasStall(target.getUniqueId())) {
            target.sendMessage(MessageUtil.warn("You already own a stall."));
            return;
        }
        UUID previousOwner = stall.ownerId;
        String previousName = stall.ownerName;
        Map<UUID, String> previousManagers = new LinkedHashMap<>(stall.managers);
        int transferredShops = transferChestShops(stall, previousOwner, target.getUniqueId(), target.getName());
        stall.managers.clear();
        setOwner(stall, target.getUniqueId(), target.getName());
        if (!save()) {
            setOwner(stall, previousOwner, previousName);
            stall.managers.putAll(previousManagers);
            transferChestShops(stall, target.getUniqueId(), previousOwner, previousName);
            target.sendMessage(MessageUtil.error("The stall transfer could not be saved."));
            return;
        }
        updateSign(stall);
        target.sendMessage(MessageUtil.success("You now own stall <white>" + stall.id + "</white>. " + transferredShops + " chest shop(s) transferred with it."));
        Player oldOwner = Bukkit.getPlayer(previousOwner);
        if (oldOwner != null) oldOwner.sendMessage(MessageUtil.success("Stall <white>" + stall.id + "</white> was transferred to <white>" + target.getName() + "</white>."));
    }

    public void denyTransfer(Player target) {
        if (target == null) return;
        TransferRequest request = transfers.remove(target.getUniqueId());
        if (request == null || request.expiresAt < System.currentTimeMillis()) {
            target.sendMessage(MessageUtil.warn("You do not have an active stall transfer."));
            return;
        }
        target.sendMessage(MessageUtil.info("Stall transfer declined."));
        Player owner = Bukkit.getPlayer(request.ownerId);
        if (owner != null) owner.sendMessage(MessageUtil.info(target.getName() + " declined the stall transfer."));
    }

    public void sellBack(Player owner) {
        if (owner == null) return;
        Stall stall = ownedStall(owner.getUniqueId());
        if (stall == null) {
            owner.sendMessage(MessageUtil.warn("You do not own a stall."));
            return;
        }
        if (!stall.placedBlocks.isEmpty()) {
            owner.sendMessage(MessageUtil.warn("Remove every chest and shop sign you placed before selling the stall back."));
            return;
        }
        MarketStallTemplateStore.Inspection inspection = templateStore.inspect(stall.id, stall.region());
        if (!inspection.success()) {
            owner.sendMessage(MessageUtil.error("This stall cannot be sold until staff repairs its launch template."));
            return;
        }
        if (inspection.blockingContainers() > 0) {
            owner.sendMessage(MessageUtil.warn("Empty the changed containers in your stall before selling it back."));
            return;
        }
        if (plugin.getEssenceManager() == null || !plugin.getEssenceManager().isLoaded(owner)) {
            owner.sendMessage(MessageUtil.warn("Your Essence is still loading. Try again in a moment."));
            return;
        }
        long refund = resaleRefund(stall.price);
        if (!plugin.getEssenceManager().canCreditFully(owner, refund)) {
            owner.sendMessage(MessageUtil.warn("Make room below the Essence cap before selling this stall."));
            return;
        }
        Confirmation pending = confirmations.get(owner.getUniqueId());
        long now = System.currentTimeMillis();
        if (pending == null || pending.type != ConfirmationType.SELL || !pending.stallId.equals(stall.id) || pending.expiresAt < now) {
            confirmations.put(owner.getUniqueId(), new Confirmation(ConfirmationType.SELL, stall.id, now + CONFIRM_MILLIS));
            owner.sendMessage(MessageUtil.warn("Selling returns <white>" + NUMBERS.format(refund) + " Essence</white> (75%) and resets approved decor. Use <white>/stall sell</white> again within 15 seconds to confirm."));
            return;
        }
        confirmations.remove(owner.getUniqueId());
        MarketStallTemplateStore.RestoreResult restored = templateStore.restore(stall.id, stall.region(), () -> {
            PlayerShopListener shops = plugin.getPlayerShopListener();
            if (shops != null) {
                shops.clearShopsInArea(stall.worldId, stall.minX, stall.maxX, stall.minY, stall.maxY, stall.minZ, stall.maxZ);
            }
        });
        if (!restored.success()) {
            owner.sendMessage(MessageUtil.error("The stall could not be returned to its launch state, so it was not sold."));
            return;
        }
        UUID previousOwner = stall.ownerId;
        String previousName = stall.ownerName;
        Map<UUID, String> previousManagers = new LinkedHashMap<>(stall.managers);
        stall.managers.clear();
        setOwner(stall, null, null);
        if (!save()) {
            setOwner(stall, previousOwner, previousName);
            stall.managers.putAll(previousManagers);
            updateSign(stall);
            owner.sendMessage(MessageUtil.error("The stall sale could not be saved."));
            return;
        }
        long paid = plugin.getEssenceManager().refund(owner, refund, "market stall resale");
        if (paid != refund) {
            setOwner(stall, previousOwner, previousName);
            stall.managers.putAll(previousManagers);
            save();
            updateSign(stall);
            owner.sendMessage(MessageUtil.error("The refund could not be completed, so you still own the stall."));
            return;
        }
        updateSign(stall);
        owner.sendMessage(MessageUtil.success("Stall <white>" + stall.id + "</white> sold back for <white>" + NUMBERS.format(refund) + " Essence</white>."));
    }

    private void confirmPurchase(Player buyer, Stall stall) {
        if (!buyer.hasPermission("smpcore.stall.use") && !buyer.hasPermission("smpcore.stall.admin")) {
            buyer.sendMessage(MessageUtil.warn("You do not have permission to buy market stalls."));
            return;
        }
        if (!canAcquireStall(stall.ownerId == null, hasStall(buyer.getUniqueId()))) {
            buyer.sendMessage(MessageUtil.warn("You already own a market stall."));
            return;
        }
        if (plugin.getEssenceManager() == null || !plugin.getEssenceManager().isLoaded(buyer)) {
            buyer.sendMessage(MessageUtil.warn("Your Essence is still loading. Try again in a moment."));
            return;
        }
        if (plugin.getEssenceManager().balance(buyer) < stall.price) {
            buyer.sendMessage(MessageUtil.warn("You need <white>" + NUMBERS.format(stall.price) + " Essence</white> to buy this stall."));
            return;
        }
        long now = System.currentTimeMillis();
        Confirmation pending = confirmations.get(buyer.getUniqueId());
        if (pending == null || pending.type != ConfirmationType.PURCHASE || !pending.stallId.equals(stall.id) || pending.expiresAt < now) {
            confirmations.put(buyer.getUniqueId(), new Confirmation(ConfirmationType.PURCHASE, stall.id, now + CONFIRM_MILLIS));
            buyer.sendMessage(MessageUtil.info("Stall <white>" + stall.id + "</white> costs <white>" + NUMBERS.format(stall.price) + " Essence</white>. Right-click the sign again within 15 seconds to buy it."));
            buyer.playSound(buyer.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.1f);
            return;
        }
        confirmations.remove(buyer.getUniqueId());
        if (!canAcquireStall(stall.ownerId == null, hasStall(buyer.getUniqueId()))) {
            buyer.sendMessage(MessageUtil.warn("That stall is no longer available."));
            updateSign(stall);
            return;
        }
        if (!plugin.getEssenceManager().spend(buyer, stall.price, "market stall purchase")) {
            buyer.sendMessage(MessageUtil.warn("You no longer have enough Essence."));
            return;
        }
        setOwner(stall, buyer.getUniqueId(), buyer.getName());
        if (!save()) {
            setOwner(stall, null, null);
            plugin.getEssenceManager().refund(buyer, stall.price, "failed market stall purchase");
            buyer.sendMessage(MessageUtil.error("The stall purchase could not be saved. Your Essence was refunded."));
            return;
        }
        updateSign(stall);
        buyer.sendMessage(MessageUtil.success("You purchased stall <white>" + stall.id + "</white>. Place a chest, then attach its shop sign directly to it."));
        buyer.sendMessage(MessageUtil.info("Use <white>/shops</white> for the short chest-shop setup guide."));
        buyer.playSound(buyer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.15f);
    }

    private int transferChestShops(Stall stall, UUID oldOwner, UUID newOwner, String newName) {
        PlayerShopListener shops = plugin.getPlayerShopListener();
        if (shops == null) return 0;
        return shops.transferOwnershipInArea(
            stall.worldId, stall.minX, stall.maxX, stall.minY, stall.maxY, stall.minZ, stall.maxZ,
            oldOwner, newOwner, newName
        );
    }

    public ShopIdentity shopIdentityFor(Player actor, Block chest, Block sign) {
        if (actor == null) return null;
        Stall chestStall = stallAt(chest == null ? null : chest.getLocation());
        Stall signStall = stallAt(sign == null ? null : sign.getLocation());
        if (chestStall == null && signStall == null) {
            return new ShopIdentity(actor.getUniqueId(), actor.getName());
        }
        if (chestStall == null || chestStall != signStall || !chestStall.canManage(actor)
            || chestStall.ownerId == null || chestStall.ownerName == null) {
            return null;
        }
        return new ShopIdentity(chestStall.ownerId, chestStall.ownerName);
    }

    public boolean canManageShopForOwner(Player actor, Block shopBlock, UUID shopOwnerId) {
        Stall stall = stallAt(shopBlock == null ? null : shopBlock.getLocation());
        return stall != null && shopOwnerId != null && shopOwnerId.equals(stall.ownerId) && stall.canManage(actor);
    }

    private Stall ownedStall(UUID ownerId) {
        return ownerId == null ? null : ownershipIndex.get(ownerId);
    }

    private List<Stall> managedStalls(UUID playerId) {
        if (playerId == null) return List.of();
        List<Stall> managed = new ArrayList<>();
        for (Stall stall : stalls.values()) {
            if (stall.managers.containsKey(playerId)) managed.add(stall);
        }
        return managed;
    }

    private boolean hasStall(UUID ownerId) {
        return ownedStall(ownerId) != null;
    }

    private OfflinePlayer cachedPlayer(String input) {
        String clean = input == null ? "" : input.trim();
        if (clean.isBlank()) return null;
        Player online = Bukkit.getPlayerExact(clean);
        if (online != null) return online;
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(clean);
        if (cached != null) return cached;
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() != null && player.getName().equalsIgnoreCase(clean)) return player;
        }
        return null;
    }

    private String managerSummary(Stall stall) {
        if (stall == null || stall.managers.isEmpty()) return "none";
        return String.join(", ", stall.managers.values().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList());
    }

    private void setOwner(Stall stall, UUID ownerId, String ownerName) {
        Stall existing = ownerId == null ? null : ownershipIndex.get(ownerId);
        if (existing != null && existing != stall) {
            throw new IllegalStateException("A player cannot own more than one market stall.");
        }
        if (stall.ownerId != null) ownershipIndex.remove(stall.ownerId, stall);
        stall.ownerId = ownerId;
        stall.ownerName = ownerId == null ? null : ownerName;
        if (ownerId != null) ownershipIndex.put(ownerId, stall);
    }

    private Stall stallAt(Location location) {
        if (location == null || location.getWorld() == null) return null;
        Map<Long, List<Stall>> worldIndex = chunkIndex.get(location.getWorld().getUID());
        if (worldIndex == null) return null;
        List<Stall> candidates = worldIndex.get(chunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4));
        if (candidates == null) return null;
        for (Stall stall : candidates) if (stall.contains(location)) return stall;
        return null;
    }

    private boolean pistonTouchesStall(Block piston, List<Block> blocks, org.bukkit.block.BlockFace direction) {
        if (stallAt(piston.getLocation()) != null) return true;
        for (Block block : blocks) {
            if (stallAt(block.getLocation()) != null
                || stallAt(block.getRelative(direction).getLocation()) != null
                || stallAt(block.getRelative(direction.getOppositeFace()).getLocation()) != null) return true;
        }
        return false;
    }

    private boolean updateSign(Stall stall) {
        Block block = blockFromKey(stall.signBlock);
        if (block == null || !(block.getState() instanceof Sign sign)) return false;
        if (plugin.getPlayerShopListener() != null) {
            plugin.getPlayerShopListener().releaseMarketPurchaseSign(block);
        }
        sign = (Sign) block.getState();
        sign.getPersistentDataContainer().set(purchaseSignKey, PersistentDataType.STRING, stall.id);
        for (Side side : List.of(Side.FRONT, Side.BACK)) {
            if (stall.ownerId == null) {
                sign.getSide(side).line(0, Component.text("Stall for", NamedTextColor.DARK_GREEN));
                sign.getSide(side).line(1, Component.text(signPrice(stall.price), NamedTextColor.DARK_BLUE));
                sign.getSide(side).line(2, Component.text("Right-click", NamedTextColor.DARK_RED));
                sign.getSide(side).line(3, Component.text("to purchase", NamedTextColor.DARK_GRAY));
            } else {
                sign.getSide(side).line(0, Component.text("Market Stall", NamedTextColor.DARK_GREEN));
                sign.getSide(side).line(1, Component.text("Owned by", NamedTextColor.DARK_GRAY));
                sign.getSide(side).line(2, Component.text(trimSign(stall.ownerName), NamedTextColor.DARK_BLUE));
                sign.getSide(side).line(3, Component.empty());
            }
        }
        sign.setWaxed(true);
        return sign.update(true, false);
    }

    private void clearSign(Stall stall) {
        Block block = blockFromKey(stall.signBlock);
        if (block == null || !(block.getState() instanceof Sign sign)) return;
        sign.getPersistentDataContainer().remove(purchaseSignKey);
        for (Side side : List.of(Side.FRONT, Side.BACK)) {
            for (int line = 0; line < 4; line++) sign.getSide(side).line(line, Component.empty());
        }
        sign.setWaxed(false);
        sign.update(true, false);
    }

    private void rebuildIndexes() {
        signIndex.clear();
        chunkIndex.clear();
        ownershipIndex.clear();
        for (Stall stall : stalls.values()) {
            signIndex.put(stall.signBlock, stall.id);
            if (stall.ownerId != null) {
                Stall existing = ownershipIndex.putIfAbsent(stall.ownerId, stall);
                if (existing != null && existing != stall) {
                    plugin.getLogger().severe("Duplicate market-stall ownership found between '" + existing.id + "' and '" + stall.id
                        + "'. New purchases and transfers remain blocked for that owner until staff repairs the data.");
                }
            }
            Map<Long, List<Stall>> world = chunkIndex.computeIfAbsent(stall.worldId, ignored -> new HashMap<>());
            for (int chunkX = stall.minX >> 4; chunkX <= stall.maxX >> 4; chunkX++) {
                for (int chunkZ = stall.minZ >> 4; chunkZ <= stall.maxZ >> 4; chunkZ++) {
                    world.computeIfAbsent(chunkKey(chunkX, chunkZ), ignored -> new ArrayList<>()).add(stall);
                }
            }
        }
    }

    private void load() {
        stalls.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("stalls");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) continue;
                try {
                    Stall stall = Stall.load(id, section);
                    if (stall != null) stalls.put(stall.id, stall);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Ignored invalid market stall '" + id + "': " + ex.getMessage());
                }
            }
        }
        rebuildIndexes();
    }

    private boolean save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", 2);
        for (Stall stall : stalls.values()) stall.save(yaml, "stalls." + stall.id);
        try {
            AtomicYamlFile.save(yaml, file);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save market stalls: " + ex.getMessage());
            return false;
        }
    }

    public static boolean isFixtureMaterial(Material material) {
        if (material == null) return false;
        String name = material.name();
        return material == Material.CHEST
            || material == Material.TRAPPED_CHEST
            || material == Material.BARREL
            || name.endsWith("_SIGN")
            || name.endsWith("_HANGING_SIGN");
    }

    public static boolean isPlaceableStorageMaterial(Material material) {
        return material == Material.CHEST || material == Material.TRAPPED_CHEST;
    }

    public static boolean isWallShopSign(Material material) {
        if (material == null) return false;
        String name = material.name();
        return name.endsWith("_WALL_SIGN") && !name.contains("HANGING");
    }

    public static boolean isShopSignItem(Material material) {
        if (material == null) return false;
        String name = material.name();
        return name.endsWith("_SIGN") && !name.contains("WALL") && !name.contains("HANGING");
    }

    static boolean isSignModificationItem(Material material) {
        if (material == null) return false;
        return material == Material.HONEYCOMB
            || material == Material.GLOW_INK_SAC
            || material == Material.INK_SAC
            || material.name().endsWith("_DYE");
    }

    public static boolean isOwnerEditableMaterial(Material material) {
        if (material == null) return false;
        String name = material.name();
        if (name.contains("CHERRY") || name.contains("PALE") || name.contains("SPRUCE") || name.endsWith("_TRAPDOOR")) return false;
        if (name.contains("SHELF") && material != Material.BOOKSHELF && material != Material.CHISELED_BOOKSHELF) return false;
        return material == Material.CHEST
            || material == Material.TRAPPED_CHEST
            || material == Material.BARREL
            || material == Material.LANTERN
            || material == Material.SOUL_LANTERN
            || material == Material.FURNACE
            || material == Material.SMOKER
            || material == Material.BLAST_FURNACE
            || material == Material.BOOKSHELF
            || material == Material.CHISELED_BOOKSHELF
            || material == Material.CRAFTING_TABLE
            || material == Material.FLOWER_POT
            || material == Material.DECORATED_POT
            || name.startsWith("POTTED_");
    }

    private boolean isAllowedPlacement(Stall stall, Block block) {
        if (stall == null || block == null) return false;
        if (isPlaceableStorageMaterial(block.getType())) return true;
        if (!isWallShopSign(block.getType()) || !(block.getBlockData() instanceof Directional directional)) return false;
        Block support = block.getRelative(directional.getFacing().getOppositeFace());
        return stall.contains(support.getLocation()) && isPlaceableStorageMaterial(support.getType());
    }

    private boolean isWallSignAttachedToStorage(Block sign, Block storage) {
        if (sign == null || storage == null || !isWallShopSign(sign.getType()) || !(sign.getBlockData() instanceof Directional directional)) return false;
        return sign.getRelative(directional.getFacing().getOppositeFace()).equals(storage) && isPlaceableStorageMaterial(storage.getType());
    }

    private boolean isWallSignAttachedToStorageInStall(Stall stall, Block sign) {
        if (stall == null || sign == null || !isWallShopSign(sign.getType()) || !(sign.getBlockData() instanceof Directional directional)) {
            return false;
        }
        Block storage = sign.getRelative(directional.getFacing().getOppositeFace());
        return stall.contains(storage.getLocation()) && isPlaceableStorageMaterial(storage.getType());
    }

    static boolean canOwnerBreakStallBlock(
        boolean purchaseSign,
        boolean placedByOwner,
        boolean editableFixture,
        boolean chestShopSign
    ) {
        return !purchaseSign && (placedByOwner || editableFixture || chestShopSign);
    }

    static boolean canEditStallSign(boolean admin, boolean purchaseSign, boolean owner) {
        return admin || (!purchaseSign && owner);
    }

    static boolean canManageStall(UUID ownerId, Set<UUID> managers, UUID actorId) {
        return ownerId != null && actorId != null
            && (ownerId.equals(actorId) || managers != null && managers.contains(actorId));
    }

    static boolean canAcquireStall(boolean stallAvailable, boolean alreadyOwnsStall) {
        return stallAvailable && !alreadyOwnsStall;
    }

    private boolean hasInventoryItems(Block block) {
        if (block == null || !(block.getState() instanceof InventoryHolder holder)) return false;
        for (ItemStack item : holder.getInventory().getStorageContents()) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) return true;
        }
        return false;
    }

    public static long resaleRefund(long purchasePrice) {
        long price = Math.max(0L, purchasePrice);
        long refund = (price / 4L * 3L) + ((price % 4L) * 3L / 4L);
        return price == 0L ? 0L : Math.max(1L, refund);
    }

    static boolean boxesOverlap(
        UUID firstWorld, int firstMinX, int firstMaxX, int firstMinY, int firstMaxY, int firstMinZ, int firstMaxZ,
        UUID secondWorld, int secondMinX, int secondMaxX, int secondMinY, int secondMaxY, int secondMinZ, int secondMaxZ
    ) {
        return firstWorld != null && firstWorld.equals(secondWorld)
            && firstMinX <= secondMaxX && firstMaxX >= secondMinX
            && firstMinY <= secondMaxY && firstMaxY >= secondMinY
            && firstMinZ <= secondMaxZ && firstMaxZ >= secondMinZ;
    }

    private boolean isAdmin(Player player) {
        return player != null && (player.isOp() || player.hasPermission("smpcore.stall.admin"));
    }

    private void deny(Player player, org.bukkit.event.Cancellable event, String message) {
        event.setCancelled(true);
        player.sendMessage(MessageUtil.warn(message));
    }

    private static String normalizeId(String input) {
        if (input == null) return null;
        String clean = input.trim().toLowerCase(Locale.ROOT);
        return clean.matches("[a-z0-9_-]{1,32}") ? clean : null;
    }

    private static String trimSign(String input) {
        if (input == null) return "";
        return input.length() <= 15 ? input : input.substring(0, 15);
    }

    static String signPrice(long price) {
        String exact = NUMBERS.format(Math.max(0L, price)) + " Essence";
        if (exact.length() <= 15) return exact;
        if (price >= 1_000_000L) {
            long tenths = price / 100_000L;
            String amount = tenths % 10L == 0L ? Long.toString(tenths / 10L) : (tenths / 10L) + "." + (tenths % 10L);
            return amount + "m Essence";
        }
        return (price / 1_000L) + "k Essence";
    }

    private static boolean sameWorld(Location first, Location second) {
        return first != null && second != null && first.getWorld() != null && second.getWorld() != null
            && first.getWorld().getUID().equals(second.getWorld().getUID());
    }

    private static String coordinates(Location location) {
        return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }

    private static String blockKey(Block block) {
        return block == null ? null : blockKey(block.getLocation());
    }

    private static String blockKey(Location location) {
        return location == null || location.getWorld() == null ? null
            : location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private static Block blockFromKey(String key) {
        if (key == null) return null;
        String[] parts = key.split(":");
        if (parts.length != 4) return null;
        try {
            World world = Bukkit.getWorld(UUID.fromString(parts[0]));
            return world == null ? null : world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static final class Selection {
        private Location first;
        private Location second;
    }

    private enum ConfirmationType { PURCHASE, SELL, RESTORE }

    private record Confirmation(ConfirmationType type, String stallId, long expiresAt) {}

    private record TransferRequest(String stallId, UUID ownerId, UUID targetId, long expiresAt) {}

    public record ShopIdentity(UUID ownerId, String ownerName) {}

    private static final class Stall {
        private final String id;
        private final UUID worldId;
        private final String worldName;
        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;
        private final int minZ;
        private final int maxZ;
        private final String signBlock;
        private long price;
        private UUID ownerId;
        private String ownerName;
        private final Map<UUID, String> managers = new LinkedHashMap<>();
        private final Set<String> placedBlocks = new HashSet<>();

        private Stall(
            String id, UUID worldId, String worldName,
            int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
            String signBlock, long price, UUID ownerId, String ownerName
        ) {
            this.id = id;
            this.worldId = worldId;
            this.worldName = worldName;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.signBlock = signBlock;
            this.price = price;
            this.ownerId = ownerId;
            this.ownerName = ownerName;
        }

        private static Stall fromSelection(String id, Location first, Location second, Block sign, long price) {
            World world = first.getWorld();
            return new Stall(
                id, world.getUID(), world.getName(),
                Math.min(first.getBlockX(), second.getBlockX()), Math.max(first.getBlockX(), second.getBlockX()),
                Math.min(first.getBlockY(), second.getBlockY()), Math.max(first.getBlockY(), second.getBlockY()),
                Math.min(first.getBlockZ(), second.getBlockZ()), Math.max(first.getBlockZ(), second.getBlockZ()),
                blockKey(sign), price, null, null
            );
        }

        private static Stall load(String rawId, ConfigurationSection section) {
            String id = normalizeId(rawId);
            String worldRaw = section.getString("world-id");
            String sign = section.getString("purchase-sign");
            long price = section.getLong("price", 0L);
            if (id == null || worldRaw == null || sign == null || price <= 0L || price > MAX_PRICE) return null;
            UUID worldId = UUID.fromString(worldRaw);
            UUID owner = null;
            String ownerRaw = section.getString("owner.uuid");
            if (ownerRaw != null && !ownerRaw.isBlank()) owner = UUID.fromString(ownerRaw);
            Stall stall = new Stall(
                id, worldId, section.getString("world-name", "world"),
                section.getInt("bounds.min-x"), section.getInt("bounds.max-x"),
                section.getInt("bounds.min-y"), section.getInt("bounds.max-y"),
                section.getInt("bounds.min-z"), section.getInt("bounds.max-z"),
                sign, price, owner, owner == null ? null : section.getString("owner.name", "Player")
            );
            ConfigurationSection managers = section.getConfigurationSection("managers");
            if (owner != null && managers != null) {
                for (String rawManagerId : managers.getKeys(false).stream().sorted().limit(MAX_MANAGERS_PER_STALL).toList()) {
                    try {
                        UUID managerId = UUID.fromString(rawManagerId);
                        String managerName = managers.getString(rawManagerId);
                        if (!managerId.equals(owner) && managerName != null && !managerName.isBlank()) {
                            stall.managers.put(managerId, managerName.trim());
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Keep the stall usable if one manually edited manager entry is malformed.
                    }
                }
            }
            if (stall.minX > stall.maxX || stall.minY > stall.maxY || stall.minZ > stall.maxZ || stall.volume() > MAX_VOLUME) return null;
            for (String placed : section.getStringList("placed-blocks")) {
                if (stall.shouldRetainPlacedBlock(placed)) stall.placedBlocks.add(placed);
            }
            return stall;
        }

        private void save(YamlConfiguration yaml, String path) {
            yaml.set(path + ".world-id", worldId.toString());
            yaml.set(path + ".world-name", worldName);
            yaml.set(path + ".bounds.min-x", minX);
            yaml.set(path + ".bounds.max-x", maxX);
            yaml.set(path + ".bounds.min-y", minY);
            yaml.set(path + ".bounds.max-y", maxY);
            yaml.set(path + ".bounds.min-z", minZ);
            yaml.set(path + ".bounds.max-z", maxZ);
            yaml.set(path + ".purchase-sign", signBlock);
            yaml.set(path + ".price", price);
            yaml.set(path + ".owner.uuid", ownerId == null ? null : ownerId.toString());
            yaml.set(path + ".owner.name", ownerId == null ? null : ownerName);
            for (Map.Entry<UUID, String> manager : managers.entrySet()) {
                yaml.set(path + ".managers." + manager.getKey(), manager.getValue());
            }
            yaml.set(path + ".placed-blocks", placedBlocks.stream().sorted().toList());
        }

        private boolean contains(Location location) {
            return location != null && location.getWorld() != null && worldId.equals(location.getWorld().getUID())
                && location.getBlockX() >= minX && location.getBlockX() <= maxX
                && location.getBlockY() >= minY && location.getBlockY() <= maxY
                && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
        }

        private boolean containsBlockKey(String key) {
            if (key == null) return false;
            String[] parts = key.split(":");
            if (parts.length != 4) return false;
            try {
                return worldId.equals(UUID.fromString(parts[0]))
                    && Integer.parseInt(parts[1]) >= minX && Integer.parseInt(parts[1]) <= maxX
                    && Integer.parseInt(parts[2]) >= minY && Integer.parseInt(parts[2]) <= maxY
                    && Integer.parseInt(parts[3]) >= minZ && Integer.parseInt(parts[3]) <= maxZ;
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }

        private boolean shouldRetainPlacedBlock(String key) {
            if (!containsBlockKey(key)) return false;
            String[] parts = key.split(":");
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            World world = Bukkit.getWorld(worldId);
            if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)) return true;
            Material material = world.getBlockAt(x, y, z).getType();
            return !material.isAir() && isFixtureMaterial(material);
        }

        private boolean canManage(Player player) {
            return player != null && canManageStall(ownerId, managers.keySet(), player.getUniqueId());
        }

        private long volume() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }

        private MarketStallTemplateStore.Region region() {
            return new MarketStallTemplateStore.Region(worldId, worldName, minX, maxX, minY, maxY, minZ, maxZ);
        }

        private boolean overlaps(Stall other) {
            return boxesOverlap(
                worldId, minX, maxX, minY, maxY, minZ, maxZ,
                other.worldId, other.minX, other.maxX, other.minY, other.maxY, other.minZ, other.maxZ
            );
        }
    }
}
