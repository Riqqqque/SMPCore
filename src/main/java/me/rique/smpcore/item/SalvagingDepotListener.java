package me.rique.smpcore.item;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.InventoryRecipeUtil;
import me.rique.smpcore.util.ItemModelUtil;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.util.VisualRangeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.TileState;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SalvagingDepotListener implements Listener {

    public static final String ITEM_ID = "salvaging_depot";

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final long LOADED_DEPOT_RECONCILE_TICKS = 20L * 60L * 5L;
    private static final long KNOWN_DEPOT_SCAN_TICKS = 20L * 5L;
    private static final BlockFace[] HORIZONTAL_FACES = {
        BlockFace.NORTH,
        BlockFace.EAST,
        BlockFace.SOUTH,
        BlockFace.WEST
    };

    private final SMPCore plugin;
    private final NamespacedKey keyDepotItem;
    private final NamespacedKey keyDepotBlock;
    private final NamespacedKey keyDepotHologram;
    private final NamespacedKey keyDepotHologramBlock;
    private final NamespacedKey keyProcessingId;
    private final NamespacedKey keyProcessingReadyAt;
    private final NamespacedKey keyQueueReadyAt;
    private final NamespacedKey recipeKey;
    private final Map<String, UUID> hologramsByBlock = new ConcurrentHashMap<>();
    private final Set<String> knownDepotBlocks = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingDepotBlocks = ConcurrentHashMap.newKeySet();
    private final Map<Inventory, String> depotBlocksByInventory = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<UUID, BukkitTask> processingTasks = new ConcurrentHashMap<>();
    private BukkitTask maintenanceTask;
    private BukkitTask quickScanTask;

    public SalvagingDepotListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyDepotItem = new NamespacedKey(plugin, ITEM_ID);
        this.keyDepotBlock = new NamespacedKey(plugin, "salvaging_depot_block");
        this.keyDepotHologram = new NamespacedKey(plugin, "salvaging_depot_hologram");
        this.keyDepotHologramBlock = new NamespacedKey(plugin, "salvaging_depot_hologram_block");
        this.keyProcessingId = new NamespacedKey(plugin, "salvage_processing_id");
        this.keyProcessingReadyAt = new NamespacedKey(plugin, "salvage_ready_at");
        this.keyQueueReadyAt = new NamespacedKey(plugin, "salvage_queue_ready_at");
        this.recipeKey = new NamespacedKey(plugin, ITEM_ID);
    }

    public void start() {
        registerRecipe();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.discoverRecipe(recipeKey);
            }
            syncLoadedDepots();
        });
        maintenanceTask = Bukkit.getScheduler().runTaskTimer(plugin, this::syncLoadedDepots, 100L, LOADED_DEPOT_RECONCILE_TICKS);
        quickScanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scanKnownDepots, 20L, KNOWN_DEPOT_SCAN_TICKS);
    }

    public void shutdown() {
        if (maintenanceTask != null) {
            maintenanceTask.cancel();
            maintenanceTask = null;
        }
        if (quickScanTask != null) {
            quickScanTask.cancel();
            quickScanTask = null;
        }
        for (BukkitTask task : processingTasks.values()) {
            task.cancel();
        }
        processingTasks.clear();
        for (UUID displayId : new ArrayList<>(hologramsByBlock.values())) {
            Entity entity = Bukkit.getEntity(displayId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        hologramsByBlock.clear();
        knownDepotBlocks.clear();
        pendingDepotBlocks.clear();
        depotBlocksByInventory.clear();
    }

    public ItemStack createDepotItem() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.UNCOMMON, "Salvaging Depot"));
        ItemModelUtil.apply(meta, ITEM_ID);
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.CHEST,
            CustomLoreUtil.Rarity.UNCOMMON.label(),
            "UTILITY STATION",
            List.of(
                "<gray>Break old gear down into raw materials.</gray>",
                "<gray>Works with manual inserts and hoppers.</gray>",
                "<gray>Place two together for <white>54 slots</white>.</gray>"
            ),
            List.of(
                CustomLoreUtil.section(
                    "Use",
                    "Cancelable Salvage",
                    "<gray>Put armor, weapons, or tools inside.</gray>",
                    "<gray>Items queue for <white>" + cancelWindowSeconds() + "s</white>; remove them during that window to cancel.</gray>",
                    "<gray>Once locked, salvaging takes <white>" + processingSeconds() + "s</white> and returns about <white>" + returnRatePercent() + "%</white> of its base materials.</gray>",
                    "<gray>Damaged items return less.</gray>"
                ),
                CustomLoreUtil.section(
                    "Safety",
                    "No Relic Recycling",
                    "<gray>Unique relics, legendaries, backpacks, stations, and class items are ignored.</gray>",
                    "<gray>Processing items cannot be pulled out by players or hoppers.</gray>"
                )
            )
        ));
        meta.getPersistentDataContainer().set(keyDepotItem, PersistentDataType.STRING, ITEM_ID);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isDepotItem(ItemStack item) {
        if (item == null || item.getType() != Material.CHEST) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && ITEM_ID.equals(meta.getPersistentDataContainer().get(keyDepotItem, PersistentDataType.STRING));
    }

    public Map<Material, Integer> recipeIngredients() {
        Map<Material, Integer> ingredients = new LinkedHashMap<>();
        ingredients.put(Material.IRON_INGOT, 1);
        ingredients.put(Material.REDSTONE, 2);
        ingredients.put(Material.CHEST, 1);
        ingredients.put(Material.HOPPER, 1);
        return ingredients;
    }

    private int cancelWindowSeconds() {
        return plugin.getConfigManager() == null
            ? 10
            : Math.max(1, plugin.getConfigManager().salvagingDepotCancelWindowSeconds);
    }

    private long cancelWindowMillis() {
        return cancelWindowSeconds() * 1000L;
    }

    private long cancelWindowTicks() {
        return cancelWindowSeconds() * 20L;
    }

    private int processingSeconds() {
        return plugin.getConfigManager() == null
            ? 6
            : Math.max(1, plugin.getConfigManager().salvagingDepotProcessingSeconds);
    }

    private long processingMillis() {
        return processingSeconds() * 1000L;
    }

    private long processingTicks() {
        return processingSeconds() * 20L;
    }

    private double returnRate() {
        if (plugin.getConfigManager() == null) {
            return 0.66D;
        }
        return Math.max(0.0D, Math.min(1.0D, plugin.getConfigManager().salvagingDepotReturnRate));
    }

    private String returnRatePercent() {
        double percent = returnRate() * 100.0D;
        if (Math.abs(percent - Math.rint(percent)) < 0.0001D) {
            return Long.toString(Math.round(percent));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", percent);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipe(recipeKey);
        purgeQueuedItemsOutsideDepot(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (isManagedRecipe(event.getRecipe()) && !usesOnlyPlainRecipeIngredients(event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!isManagedRecipe(event.getRecipe()) || usesOnlyPlainRecipeIngredients(event.getInventory().getMatrix())) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            player.sendMessage(MessageUtil.warn("Use plain vanilla ingredients for Salvaging Depot recipes."));
        }
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        purgeQueuedItemsOutsideDepot(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        purgeQueuedItemsOutsideDepot(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        cleanDroppedDepotState(event.getItemDrop());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        cleanDroppedDepotState(event.getItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        cleanDroppedDepotState(event.getItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() == Material.CHEST && !isDepotItem(event.getItemInHand()) && hasAdjacentDepot(block)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.warn("Place normal chests away from Salvaging Depots."));
            return;
        }

        if (!isDepotItem(event.getItemInHand())) {
            return;
        }

        List<Block> adjacentChests = adjacentChests(block);
        if (!canPlaceDepotBeside(block, adjacentChests)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.warn(
                adjacentChests.isEmpty()
                    ? "That Salvaging Depot could not be placed safely."
                    : "A Salvaging Depot can only join one other single Salvaging Depot."
            ));
            return;
        }

        String pendingKey = blockKey(block);
        pendingDepotBlocks.add(pendingKey);
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingDepotBlocks.remove(pendingKey);
            setupPlacedDepot(block);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!isDepotBlock(block)) {
            return;
        }

        scheduleBlockScan(block);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isDepotBlock(block)) {
            return;
        }

        if (hasProcessingItem(block)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.warn("Wait for the current salvage to finish before breaking this depot."));
            return;
        }

        event.setDropItems(false);
        Inventory inventory = depotLocalInventory(block);
        ItemStack[] contents = inventory == null ? new ItemStack[0] : cloneContents(inventory.getStorageContents());
        if (inventory != null) {
            inventory.clear();
        }
        forgetDepotInventories(block);
        removeDepotHolograms(block);
        Bukkit.getScheduler().runTask(plugin, () -> finishBreak(block, contents));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isDepotBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isDepotBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        Player player = event.getWhoClicked() instanceof Player clickedPlayer ? clickedPlayer : null;
        if (!resolveDepotInventory(top)) {
            if (player != null && cleanEscapedDepotState(event)) {
                Bukkit.getScheduler().runTask(plugin, () -> purgeQueuedItemsOutsideDepot(player, true));
            }
            return;
        }

        if (isUnsafeDepotClick(event)) {
            event.setCancelled(true);
            scheduleScan(top);
            if (player != null) {
                Bukkit.getScheduler().runTask(plugin, () -> purgeQueuedItemsOutsideDepot(player, true));
            }
            return;
        }

        if (event.getRawSlot() >= 0 && event.getRawSlot() < top.getSize()) {
            ItemStack current = event.getCurrentItem();
            if (isProcessing(current)) {
                event.setCancelled(true);
                if (player != null) {
                    player.sendMessage(MessageUtil.warn("Items placed in the Salvaging Depot are locked until salvage finishes."));
                }
                scheduleScan(top);
                return;
            }
            if (isQueued(current)) {
                event.setCurrentItem(cleanDepotState(current));
                scheduleScan(top);
                if (player != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> purgeQueuedItemsOutsideDepot(player, true));
                }
                return;
            }
        }

        scheduleScan(top);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (resolveDepotInventory(top)) {
            scheduleScan(top);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (resolveDepotInventory(event.getInventory())) {
            scanDepot(event.getInventory());
            if (event.getPlayer() instanceof Player closingPlayer) {
                purgeQueuedItemsOutsideDepot(closingPlayer);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (resolveDepotInventory(event.getInventory())) {
            scheduleScan(event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (isDepotInventory(event.getSource())) {
            if (isProcessing(event.getItem()) || isQueued(event.getItem()) || !salvageOutputs(event.getItem()).isEmpty()) {
                event.setCancelled(true);
            }
            scheduleScan(event.getSource());
            return;
        }

        if (isDepotInventory(event.getDestination())) {
            scheduleScan(event.getDestination());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        for (int i = 0; i < event.getDrops().size(); i++) {
            ItemStack item = event.getDrops().get(i);
            if (isQueued(item) || isProcessing(item)) {
                event.getDrops().set(i, cleanDepotState(item));
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> purgeQueuedItemsOutsideDepot(event.getPlayer()));
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        syncChunkDepots(event.getChunk());
    }

    @EventHandler(ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        removeChunkHolograms(event.getChunk());
    }

    private void registerRecipe() {
        Bukkit.removeRecipe(recipeKey);
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createDepotItem());
        recipe.shape(" I ", "RCR", " H ");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('R', Material.REDSTONE);
        recipe.setIngredient('C', Material.CHEST);
        recipe.setIngredient('H', Material.HOPPER);
        recipe.setGroup("smpcore_utility");
        Bukkit.addRecipe(recipe);
    }

    private boolean isManagedRecipe(Recipe recipe) {
        return recipe instanceof Keyed keyed && recipeKey.equals(keyed.getKey());
    }

    private boolean usesOnlyPlainRecipeIngredients(ItemStack[] matrix) {
        if (matrix == null) {
            return false;
        }
        for (ItemStack item : matrix) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (!InventoryRecipeUtil.isPlainMaterial(plugin, item, item.getType())) {
                return false;
            }
        }
        return true;
    }

    private boolean isUnsafeDepotClick(InventoryClickEvent event) {
        InventoryAction action = event.getAction();
        String actionName = action == null ? "" : action.name();
        org.bukkit.event.inventory.ClickType click = event.getClick();
        return action == InventoryAction.CLONE_STACK
            || action == InventoryAction.COLLECT_TO_CURSOR
            || action == InventoryAction.DROP_ALL_CURSOR
            || action == InventoryAction.DROP_ALL_SLOT
            || action == InventoryAction.DROP_ONE_CURSOR
            || action == InventoryAction.DROP_ONE_SLOT
            || action == InventoryAction.HOTBAR_SWAP
            || "HOTBAR_MOVE_AND_READD".equals(actionName)
            || action == InventoryAction.UNKNOWN
            || click == org.bukkit.event.inventory.ClickType.CREATIVE
            || click == org.bukkit.event.inventory.ClickType.CONTROL_DROP
            || click == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK
            || click == org.bukkit.event.inventory.ClickType.DROP
            || click == org.bukkit.event.inventory.ClickType.MIDDLE
            || click == org.bukkit.event.inventory.ClickType.NUMBER_KEY
            || click == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
            || click == org.bukkit.event.inventory.ClickType.UNKNOWN;
    }

    private void setupPlacedDepot(Block block) {
        if (block.getType() != Material.CHEST) {
            return;
        }
        BlockState state = block.getState();
        if (state instanceof Chest chest) {
            chest.getPersistentDataContainer().set(keyDepotBlock, PersistentDataType.STRING, ITEM_ID);
            chest.update(true, false);
        }
        knownDepotBlocks.add(blockKey(block));
        reconcileDepot(block);
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_ANVIL_PLACE, 0.75f, 1.35f);
        block.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, block.getLocation().add(0.5, 1.1, 0.5), 18, 0.28, 0.24, 0.28, 0.03);
    }

    private void finishBreak(Block block, ItemStack[] contents) {
        if (isDepotBlock(block)) {
            Inventory inventory = depotLocalInventory(block);
            if (inventory != null) {
                inventory.setStorageContents(fitToInventory(contents, inventory.getStorageContents().length));
            }
            reconcileDepot(block);
            return;
        }

        knownDepotBlocks.remove(blockKey(block));
        Location dropLocation = block.getLocation().add(0.5, 0.6, 0.5);
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            block.getWorld().dropItemNaturally(dropLocation, cleanDepotState(item));
        }
        block.getWorld().dropItemNaturally(dropLocation, createDepotItem());
        Block remaining = adjacentDepot(block);
        if (remaining != null) {
            reconcileDepot(remaining);
        }
    }

    private void syncLoadedDepots() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                syncChunkDepots(chunk);
            }
        }
    }

    private void scanKnownDepots() {
        List<String> blockKeys = new ArrayList<>(knownDepotBlocks);
        Set<String> scannedDepots = new java.util.HashSet<>();
        for (String blockKey : hologramsByBlock.keySet()) {
            if (!knownDepotBlocks.contains(blockKey)) {
                blockKeys.add(blockKey);
            }
        }

        for (String blockKey : blockKeys) {
            Location location = locationFromBlockKey(blockKey);
            if (location == null || location.getWorld() == null || !location.isChunkLoaded()) {
                continue;
            }

            Block block = location.getBlock();
            if (!isDepotBlock(block)) {
                knownDepotBlocks.remove(blockKey);
                UUID displayId = hologramsByBlock.remove(blockKey);
                Entity display = displayId == null ? null : Bukkit.getEntity(displayId);
                if (display != null && display.isValid()) {
                    display.remove();
                }
                continue;
            }

            knownDepotBlocks.add(blockKey);
            Block canonical = canonicalDepotBlock(block);
            if (scannedDepots.add(blockKey(canonical))) {
                scanDepot(canonical);
            }
        }
    }

    private void syncChunkDepots(Chunk chunk) {
        removeStaleChunkHolograms(chunk);
        Set<String> scannedDepots = new java.util.HashSet<>();
        for (BlockState tile : chunk.getTileEntities()) {
            if (!(tile instanceof Chest chest)) {
                continue;
            }
            Block block = chest.getBlock();
            if (!isDepotBlock(block)) {
                continue;
            }
            knownDepotBlocks.add(blockKey(block));
            Block canonical = canonicalDepotBlock(block);
            if (!scannedDepots.add(blockKey(canonical))) continue;
            ensureHologram(canonical);
            scanDepot(canonical);
        }
    }

    private void scanDepot(Block block) {
        Block canonical = canonicalDepotBlock(block);
        Inventory inventory = depotInventory(canonical);
        if (inventory != null) {
            rememberDepotInventory(inventory, canonical);
            scanDepot(canonical, inventory);
        }
    }

    private void scanDepot(Inventory inventory) {
        Block block = depotBlock(inventory);
        if (block == null) {
            return;
        }
        scanDepot(block, inventory);
    }

    private void scanDepot(Block block, Inventory inventory) {
        if (block == null || !isDepotBlock(block) || inventory == null) {
            return;
        }
        block = canonicalDepotBlock(block);
        rememberDepotInventory(inventory, block);
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            try {
                scanDepotSlot(inventory, block, slot, contents[slot]);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Skipped a Salvaging Depot slot at " + blockKey(block)
                    + " because " + describeItem(contents[slot]) + " could not be scanned: " + ex.getMessage());
            }
        }
    }

    private void scanDepotSlot(Inventory inventory, Block block, int slot, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        if (isProcessing(item)) {
            UUID processId = processingId(item);
            long readyAt = processingReadyAt(item);
            if (processId != null) {
                if (readyAt <= System.currentTimeMillis()) {
                    finishProcessing(blockKey(block), processId);
                    return;
                }
                scheduleCompletion(blockKey(block), processId, readyAt);
            }
            return;
        }
        if (isQueued(item)) {
            long readyAt = queueReadyAt(item);
            ItemStack cleanItem = cleanQueuedItem(item);
            List<ItemStack> outputs = salvageOutputs(cleanItem);
            if (outputs.isEmpty()) {
                inventory.setItem(slot, cleanItem);
                return;
            }
            if (readyAt > System.currentTimeMillis()) {
                scheduleQueueScan(blockKey(block), readyAt);
                return;
            }
            startProcessing(inventory, block, slot, cleanItem);
            return;
        }
        List<ItemStack> outputs = salvageOutputs(item);
        if (outputs.isEmpty()) {
            return;
        }

        long readyAt = System.currentTimeMillis() + cancelWindowMillis();
        inventory.setItem(slot, markQueued(item, readyAt));
        scheduleQueueScan(blockKey(block), readyAt);
        notifyDepotViewers(inventory, "<gold>Queued for salvage.</gold> <gray>Remove it within <white>" + cancelWindowSeconds() + "s</white> to cancel.</gray>");
        block.getWorld().playSound(block.getLocation().add(0.5, 0.6, 0.5), Sound.BLOCK_COMPARATOR_CLICK, 0.55f, 1.45f);
        block.getWorld().spawnParticle(Particle.WAX_ON, block.getLocation().add(0.5, 1.05, 0.5), 8, 0.20, 0.14, 0.20, 0.01);
    }

    private void startProcessing(Inventory inventory, Block block, int slot, ItemStack item) {
        UUID processId = UUID.randomUUID();
        long readyAt = System.currentTimeMillis() + processingMillis();
        inventory.setItem(slot, markProcessing(item, processId, readyAt));
        scheduleCompletion(blockKey(block), processId, readyAt);
        block.getWorld().playSound(block.getLocation().add(0.5, 0.6, 0.5), Sound.BLOCK_GRINDSTONE_USE, 0.7f, 1.15f);
        block.getWorld().spawnParticle(Particle.WAX_OFF, block.getLocation().add(0.5, 1.05, 0.5), 12, 0.24, 0.18, 0.24, 0.02);
    }

    private void scheduleScan(Inventory inventory) {
        Bukkit.getScheduler().runTask(plugin, () -> scanScheduledInventory(inventory));
        Bukkit.getScheduler().runTaskLater(plugin, () -> scanScheduledInventory(inventory), 2L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> scanScheduledInventory(inventory), 10L);
    }

    private void scanScheduledInventory(Inventory inventory) {
        Block block = depotBlock(inventory);
        if (block != null) {
            scanDepot(block, inventory);
        }
    }

    private void scheduleBlockScan(Block block) {
        if (block == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isDepotBlock(block)) {
                scanDepot(block);
            }
        });
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (block != null) {
                scanDepot(block);
            }
        }, 10L);
    }

    private void scheduleQueueScan(String blockKey, long readyAt) {
        long delay = readyAt <= 0L
            ? cancelWindowTicks()
            : Math.max(1L, (readyAt - System.currentTimeMillis() + 49L) / 50L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Location location = locationFromBlockKey(blockKey);
            if (location == null || location.getWorld() == null || !location.isChunkLoaded()) {
                return;
            }
            scanDepot(location.getBlock());
        }, delay);
    }

    private void scheduleCompletion(String blockKey, UUID processId, long readyAt) {
        if (processId == null || processingTasks.containsKey(processId)) {
            return;
        }
        long delay = readyAt <= 0L
            ? processingTicks()
            : Math.max(1L, (readyAt - System.currentTimeMillis() + 49L) / 50L);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            processingTasks.remove(processId);
            finishProcessing(blockKey, processId);
        }, delay);
        processingTasks.put(processId, task);
    }

    private ItemStack markQueued(ItemStack item, long readyAt) {
        ItemStack marked = item.clone();
        ItemMeta meta = marked.getItemMeta();
        if (meta == null) {
            return marked;
        }
        meta.getPersistentDataContainer().set(keyQueueReadyAt, PersistentDataType.LONG, readyAt);
        List<Component> lore = mutableLore(meta);
        lore.add(Component.empty());
        lore.add(MM.deserialize("<yellow>Queued for salvage...</yellow> <dark_gray>take it back within " + cancelWindowSeconds() + "s to cancel</dark_gray>"));
        meta.lore(lore);
        marked.setItemMeta(meta);
        return marked;
    }

    private void finishProcessing(String blockKey, UUID processId) {
        Location location = locationFromBlockKey(blockKey);
        if (location == null || location.getWorld() == null || !location.isChunkLoaded()) {
            return;
        }

        Block block = location.getBlock();
        Inventory inventory = depotInventory(block);
        if (inventory == null || !isDepotBlock(block)) {
            return;
        }

        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!processId.equals(processingId(item))) {
                continue;
            }

            ItemStack cleanItem = cleanProcessingItem(item);
            List<ItemStack> outputs = salvageOutputs(cleanItem);
            if (outputs.isEmpty()) {
                inventory.setItem(slot, cleanItem);
                plugin.getLogger().warning("Restored a Salvaging Depot item because no safe salvage output could be calculated at " + blockKey + ".");
                return;
            }

            inventory.setItem(slot, null);
            Map<Integer, ItemStack> leftovers = inventory.addItem(outputs.toArray(ItemStack[]::new));
            leftovers.values().forEach(leftover ->
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.8, 0.5), leftover)
            );
            block.getWorld().playSound(block.getLocation().add(0.5, 0.6, 0.5), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.65f, 1.55f);
            block.getWorld().spawnParticle(Particle.END_ROD, block.getLocation().add(0.5, 1.0, 0.5), 16, 0.22, 0.22, 0.22, 0.015);
            return;
        }
    }

    private ItemStack markProcessing(ItemStack item, UUID processId, long readyAt) {
        ItemStack marked = item.clone();
        ItemMeta meta = marked.getItemMeta();
        if (meta == null) {
            return marked;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(keyQueueReadyAt);
        pdc.set(keyProcessingId, PersistentDataType.STRING, processId.toString());
        pdc.set(keyProcessingReadyAt, PersistentDataType.LONG, readyAt);
        List<Component> lore = removeDepotStateLore(mutableLore(meta), "Queued for salvage...");
        lore.add(Component.empty());
        lore.add(MM.deserialize("<yellow>Salvaging...</yellow> <dark_gray>" + processingSeconds() + "s</dark_gray>"));
        meta.lore(lore);
        marked.setItemMeta(meta);
        return marked;
    }

    private void cleanDroppedDepotState(Item itemEntity) {
        if (itemEntity == null || !itemEntity.isValid()) {
            return;
        }
        ItemStack stack = itemEntity.getItemStack();
        if (!hasDepotState(stack)) {
            return;
        }
        itemEntity.setItemStack(cleanDepotState(stack));
    }

    private boolean cleanEscapedDepotState(InventoryClickEvent event) {
        boolean changed = false;
        ItemStack current = event.getCurrentItem();
        if (hasDepotState(current)) {
            event.setCurrentItem(cleanDepotState(current));
            changed = true;
        }
        ItemStack cursor = event.getCursor();
        if (hasDepotState(cursor)) {
            event.getView().setCursor(cleanDepotState(cursor));
            changed = true;
        }
        return changed;
    }

    private ItemStack cleanDepotState(ItemStack item) {
        return cleanProcessingItem(cleanQueuedItem(item));
    }

    private ItemStack cleanQueuedItem(ItemStack item) {
        ItemStack clean = item == null ? null : item.clone();
        if (clean == null || clean.getType().isAir()) {
            return clean;
        }

        ItemMeta meta = clean.getItemMeta();
        if (meta == null) {
            return clean;
        }

        meta.getPersistentDataContainer().remove(keyQueueReadyAt);
        List<Component> lore = meta.lore();
        if (lore != null && !lore.isEmpty()) {
            meta.lore(removeDepotStateLore(lore, "Queued for salvage..."));
        }
        clean.setItemMeta(meta);
        return clean;
    }

    private ItemStack cleanProcessingItem(ItemStack item) {
        ItemStack clean = item == null ? null : item.clone();
        if (clean == null || clean.getType().isAir()) {
            return clean;
        }

        ItemMeta meta = clean.getItemMeta();
        if (meta == null) {
            return clean;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(keyProcessingId);
        pdc.remove(keyProcessingReadyAt);

        List<Component> lore = meta.lore();
        if (lore != null && !lore.isEmpty()) {
            meta.lore(removeDepotStateLore(lore, "Salvaging..."));
        }

        clean.setItemMeta(meta);
        return clean;
    }

    private List<Component> mutableLore(ItemMeta meta) {
        List<Component> lore = meta == null ? null : meta.lore();
        return lore == null ? new ArrayList<>() : new ArrayList<>(lore);
    }

    private List<Component> removeDepotStateLore(List<Component> lore, String marker) {
        List<Component> cleanedLore = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
        int last = cleanedLore.size() - 1;
        if (last < 0) {
            return cleanedLore;
        }
        String lastPlain = PLAIN.serialize(cleanedLore.get(last));
        if (lastPlain.contains(marker)) {
            cleanedLore.remove(last);
            if (!cleanedLore.isEmpty()) {
                int spacer = cleanedLore.size() - 1;
                if (PLAIN.serialize(cleanedLore.get(spacer)).isBlank()) {
                    cleanedLore.remove(spacer);
                }
            }
        }
        return cleanedLore;
    }

    private boolean isProcessing(ItemStack item) {
        return processingId(item) != null;
    }

    private boolean isQueued(ItemStack item) {
        return queueReadyAt(item) > 0L;
    }

    private boolean hasDepotState(ItemStack item) {
        return isQueued(item) || isProcessing(item);
    }

    private void purgeQueuedItemsOutsideDepot(Player player) {
        purgeQueuedItemsOutsideDepot(player, false);
    }

    private void purgeQueuedItemsOutsideDepot(Player player, boolean notifyPlayer) {
        if (player == null || !player.isOnline()) {
            return;
        }

        boolean changed = false;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!hasDepotState(item)) {
                continue;
            }
            inventory.setItem(slot, cleanDepotState(item));
            changed = true;
        }

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (hasDepotState(offhand)) {
            player.getInventory().setItemInOffHand(cleanDepotState(offhand));
            changed = true;
        }

        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean armorChanged = false;
        for (int i = 0; i < armor.length; i++) {
            if (!hasDepotState(armor[i])) {
                continue;
            }
            armor[i] = cleanDepotState(armor[i]);
            armorChanged = true;
            changed = true;
        }
        if (armorChanged) {
            player.getInventory().setArmorContents(armor);
        }

        ItemStack cursor = player.getItemOnCursor();
        if (hasDepotState(cursor)) {
            player.setItemOnCursor(cleanDepotState(cursor));
            changed = true;
        }

        if (changed) {
            if (notifyPlayer) {
                player.sendActionBar(MM.deserialize("<green>Salvage canceled.</green> <gray>Your item was returned safely.</gray>"));
            }
            player.updateInventory();
        }
    }

    private void notifyDepotViewers(Inventory inventory, String message) {
        Component component = MM.deserialize(message);
        for (var viewer : inventory.getViewers()) {
            if (viewer instanceof Player player) {
                player.sendActionBar(component);
            }
        }
    }

    private long queueReadyAt(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0L;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0L;
        }
        Long readyAt = meta.getPersistentDataContainer().get(keyQueueReadyAt, PersistentDataType.LONG);
        return readyAt == null ? 0L : readyAt;
    }

    private UUID processingId(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String raw = meta.getPersistentDataContainer().get(keyProcessingId, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private long processingReadyAt(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0L;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0L;
        }
        Long readyAt = meta.getPersistentDataContainer().get(keyProcessingReadyAt, PersistentDataType.LONG);
        return readyAt == null ? System.currentTimeMillis() + processingMillis() : readyAt;
    }

    private List<ItemStack> salvageOutputs(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return List.of();
        }

        Material type = item.getType();
        if (!isSalvageableGear(item)) {
            return List.of();
        }
        if (isPluginManagedItem(item)) {
            return List.of();
        }

        Map<Material, Integer> base = baseMaterials(type);
        if (base.isEmpty()) {
            base.putAll(estimatedBaseMaterials(type));
        }
        if (base.isEmpty()) {
            Material fallback = preferredFallbackMaterial(type);
            if (fallback != null) {
                base.put(fallback, 1);
            }
        }
        if (base.isEmpty()) {
            return List.of();
        }

        double durabilityFactor = salvageDurabilityFactor(item);
        double rate = returnRate();
        int itemCount = Math.max(1, Math.min(64, item.getAmount()));
        List<ItemStack> outputs = new ArrayList<>();
        Material primaryMaterial = fallbackMaterial(base, type);
        Map<Material, Integer> returned = calculateReturnedMaterials(base, primaryMaterial, itemCount, durabilityFactor, rate);
        returned.forEach((material, amount) -> addOutput(outputs, material, amount));
        if (outputs.isEmpty()) {
            Material minimumFallback = preferredFallbackMaterial(type);
            if (minimumFallback != null && rate > 0.0D && durabilityFactor > 0.0D) {
                addOutput(outputs, minimumFallback, itemCount);
            }
        }
        return outputs;
    }

    static Map<Material, Integer> calculateReturnedMaterials(
        Map<Material, Integer> base,
        Material primaryMaterial,
        int itemCount,
        double durabilityFactor,
        double rate
    ) {
        if (base == null || base.isEmpty() || primaryMaterial == null || itemCount <= 0 || durabilityFactor <= 0.0D || rate <= 0.0D) {
            return Map.of();
        }
        Map<Material, Integer> returned = new LinkedHashMap<>();
        int safeItemCount = Math.max(1, Math.min(64, itemCount));
        for (Map.Entry<Material, Integer> entry : base.entrySet()) {
            int count = returnedCount(entry.getValue() * safeItemCount, durabilityFactor, rate);
            if (count <= 0 && durabilityFactor > 0.0D && entry.getKey() == primaryMaterial) {
                count = safeItemCount;
            }
            if (count > 0) {
                returned.put(entry.getKey(), count);
            }
        }
        if (returned.isEmpty()) {
            returned.put(primaryMaterial, safeItemCount);
        }
        return Collections.unmodifiableMap(returned);
    }

    private boolean isSalvageableGear(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        Material material = item.getType();
        if (isGearMaterial(material)) {
            return true;
        }
        if (!(item.getItemMeta() instanceof Damageable)) {
            return false;
        }
        return material.getMaxDurability() > 0 && material.isItem() && preferredFallbackMaterial(material) != null;
    }

    private boolean isGearMaterial(Material material) {
        if (material == null) {
            return false;
        }
        if (!material.isItem()) {
            return false;
        }
        if (isTaggedArmor(material) || isTaggedWeaponOrTool(material)) {
            return true;
        }
        EquipmentSlot slot = material.getEquipmentSlot();
        if (slot != null && material.getMaxDurability() > 0) {
            return true;
        }

        String name = material.name();
        return name.endsWith("_HELMET")
            || name.endsWith("_CHESTPLATE")
            || name.endsWith("_LEGGINGS")
            || name.endsWith("_BOOTS")
            || name.endsWith("_SWORD")
            || name.endsWith("_SPEAR")
            || name.endsWith("_PICKAXE")
            || name.endsWith("_AXE")
            || name.endsWith("_SHOVEL")
            || name.endsWith("_HOE")
            || name.endsWith("_HORSE_ARMOR")
            || name.endsWith("_NAUTILUS_ARMOR")
            || material == Material.BOW
            || material == Material.CROSSBOW
            || material == Material.FISHING_ROD
            || material == Material.SHIELD
            || material == Material.SHEARS
            || material == Material.FLINT_AND_STEEL
            || material == Material.BRUSH
            || material == Material.ELYTRA
            || material == Material.TRIDENT
            || material == Material.MACE
            || material == Material.WOLF_ARMOR
            || material == Material.CARROT_ON_A_STICK
            || material == Material.WARPED_FUNGUS_ON_A_STICK;
    }

    private boolean isTaggedArmor(Material material) {
        return Tag.ITEMS_ENCHANTABLE_ARMOR.isTagged(material)
            || Tag.ITEMS_HEAD_ARMOR.isTagged(material)
            || Tag.ITEMS_CHEST_ARMOR.isTagged(material)
            || Tag.ITEMS_LEG_ARMOR.isTagged(material)
            || Tag.ITEMS_FOOT_ARMOR.isTagged(material)
            || Tag.ITEMS_TRIMMABLE_ARMOR.isTagged(material);
    }

    private boolean isTaggedWeaponOrTool(Material material) {
        return Tag.ITEMS_SWORDS.isTagged(material)
            || Tag.ITEMS_SPEARS.isTagged(material)
            || Tag.ITEMS_AXES.isTagged(material)
            || Tag.ITEMS_PICKAXES.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_WEAPON.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_MELEE_WEAPON.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_SHARP_WEAPON.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_BOW.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_CROSSBOW.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_TRIDENT.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_MACE.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_MINING.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_FISHING.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_DURABILITY.isTagged(material);
    }

    private static int returnedCount(int baseCount, double durabilityFactor, double rate) {
        double exact = baseCount * rate * durabilityFactor;
        int count = (int) Math.floor(exact);
        if (count == 0 && exact >= 0.50D) {
            count = 1;
        }
        return Math.max(0, count);
    }

    private double salvageDurabilityFactor(ItemStack item) {
        if (!(item.getItemMeta() instanceof Damageable damageable)) {
            return 1.0D;
        }

        double max = damageable.hasMaxDamage()
            ? damageable.getMaxDamage()
            : item.getType().getMaxDurability();
        return salvageDurabilityFactor(damageable.getDamage(), max);
    }

    static double salvageDurabilityFactor(double damage, double max) {
        if (max <= 0.0D) {
            return 1.0D;
        }
        double remaining = Math.max(0.0D, max - Math.max(0.0D, damage));
        return Math.max(0.08D, Math.min(1.0D, remaining / max));
    }

    private Material fallbackMaterial(Map<Material, Integer> base, Material sourceType) {
        Material preferred = preferredFallbackMaterial(sourceType);
        if (preferred != null && base.containsKey(preferred)) {
            return preferred;
        }

        Material fallback = null;
        int bestCount = 0;
        for (Map.Entry<Material, Integer> entry : base.entrySet()) {
            if (entry.getValue() > bestCount) {
                fallback = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return fallback;
    }

    private Material preferredFallbackMaterial(Material sourceType) {
        if (sourceType == null) {
            return null;
        }
        return switch (sourceType) {
            case LEATHER_HELMET, LEATHER_CHESTPLATE, LEATHER_LEGGINGS, LEATHER_BOOTS, LEATHER_HORSE_ARMOR -> Material.LEATHER;
            case GOLDEN_HELMET, GOLDEN_CHESTPLATE, GOLDEN_LEGGINGS, GOLDEN_BOOTS,
                GOLDEN_SWORD, GOLDEN_PICKAXE, GOLDEN_AXE, GOLDEN_SHOVEL, GOLDEN_HOE,
                GOLDEN_HORSE_ARMOR, GOLDEN_NAUTILUS_ARMOR -> Material.GOLD_INGOT;
            case IRON_HELMET, IRON_CHESTPLATE, IRON_LEGGINGS, IRON_BOOTS,
                IRON_SWORD, IRON_PICKAXE, IRON_AXE, IRON_SHOVEL, IRON_HOE,
                IRON_HORSE_ARMOR, CHAINMAIL_HELMET, CHAINMAIL_CHESTPLATE, CHAINMAIL_LEGGINGS,
                CHAINMAIL_BOOTS, IRON_NAUTILUS_ARMOR, SHEARS, FLINT_AND_STEEL -> Material.IRON_INGOT;
            case COPPER_HELMET, COPPER_CHESTPLATE, COPPER_LEGGINGS, COPPER_BOOTS,
                COPPER_SWORD, COPPER_PICKAXE, COPPER_AXE, COPPER_SHOVEL, COPPER_HOE,
                COPPER_HORSE_ARMOR, COPPER_NAUTILUS_ARMOR, BRUSH -> Material.COPPER_INGOT;
            case DIAMOND_HELMET, DIAMOND_CHESTPLATE, DIAMOND_LEGGINGS, DIAMOND_BOOTS,
                DIAMOND_SWORD, DIAMOND_PICKAXE, DIAMOND_AXE, DIAMOND_SHOVEL, DIAMOND_HOE,
                DIAMOND_HORSE_ARMOR, DIAMOND_NAUTILUS_ARMOR, NETHERITE_HELMET, NETHERITE_CHESTPLATE, NETHERITE_LEGGINGS,
                NETHERITE_BOOTS, NETHERITE_SWORD, NETHERITE_PICKAXE, NETHERITE_AXE,
                NETHERITE_SHOVEL, NETHERITE_HOE, NETHERITE_HORSE_ARMOR, NETHERITE_NAUTILUS_ARMOR -> Material.DIAMOND;
            case TURTLE_HELMET -> Material.TURTLE_SCUTE;
            case ELYTRA -> Material.PHANTOM_MEMBRANE;
            case TRIDENT -> Material.PRISMARINE_SHARD;
            case MACE -> Material.BREEZE_ROD;
            case WOLF_ARMOR -> Material.ARMADILLO_SCUTE;
            case CARROT_ON_A_STICK, WARPED_FUNGUS_ON_A_STICK -> Material.STICK;
            default -> genericFallbackMaterial(sourceType);
        };
    }

    private Material genericFallbackMaterial(Material sourceType) {
        if (sourceType == null) {
            return null;
        }
        String name = sourceType.name();
        if (name.startsWith("LEATHER_")) return Material.LEATHER;
        if (name.startsWith("CHAINMAIL_") || name.startsWith("IRON_")) return Material.IRON_INGOT;
        if (name.startsWith("COPPER_")) return Material.COPPER_INGOT;
        if (name.startsWith("GOLDEN_")) return Material.GOLD_INGOT;
        if (name.startsWith("DIAMOND_") || name.startsWith("NETHERITE_")) return Material.DIAMOND;
        if (name.startsWith("WOODEN_")) return Material.OAK_PLANKS;
        if (name.startsWith("STONE_")) return Material.COBBLESTONE;
        return null;
    }

    private Map<Material, Integer> estimatedBaseMaterials(Material material) {
        Map<Material, Integer> result = new LinkedHashMap<>();
        Material fallback = preferredFallbackMaterial(material);
        if (fallback == null) {
            return result;
        }

        String name = material.name();
        if (name.endsWith("_HELMET")) {
            put(result, fallback, 5);
        } else if (name.endsWith("_CHESTPLATE")) {
            put(result, fallback, 8);
        } else if (name.endsWith("_LEGGINGS")) {
            put(result, fallback, 7);
        } else if (name.endsWith("_BOOTS")) {
            put(result, fallback, 4);
        } else if (name.endsWith("_SWORD")) {
            put(result, fallback, 2, Material.STICK, 1);
        } else if (name.endsWith("_PICKAXE") || name.endsWith("_AXE")) {
            put(result, fallback, 3, Material.STICK, 2);
        } else if (name.endsWith("_SHOVEL")) {
            put(result, fallback, 1, Material.STICK, 2);
        } else if (name.endsWith("_HOE")) {
            put(result, fallback, 2, Material.STICK, 2);
        } else if (name.endsWith("_HORSE_ARMOR") || name.endsWith("_NAUTILUS_ARMOR")) {
            put(result, fallback, 5);
        }

        return result;
    }

    private void addOutput(List<ItemStack> outputs, Material material, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(material.getMaxStackSize(), remaining);
            outputs.add(new ItemStack(material, stackSize));
            remaining -= stackSize;
        }
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        if (contents == null) {
            return new ItemStack[0];
        }
        ItemStack[] cloned = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            cloned[i] = contents[i] == null ? null : contents[i].clone();
        }
        return cloned;
    }

    private ItemStack[] fitToInventory(ItemStack[] contents, int size) {
        ItemStack[] fitted = new ItemStack[Math.max(0, size)];
        if (contents == null) {
            return fitted;
        }
        for (int i = 0; i < Math.min(contents.length, fitted.length); i++) {
            fitted[i] = contents[i] == null ? null : contents[i].clone();
        }
        return fitted;
    }

    private Map<Material, Integer> baseMaterials(Material material) {
        Map<Material, Integer> result = new LinkedHashMap<>();
        switch (material) {
            case LEATHER_HELMET -> put(result, Material.LEATHER, 5);
            case LEATHER_CHESTPLATE -> put(result, Material.LEATHER, 8);
            case LEATHER_LEGGINGS -> put(result, Material.LEATHER, 7);
            case LEATHER_BOOTS -> put(result, Material.LEATHER, 4);
            case CHAINMAIL_HELMET, IRON_HELMET -> put(result, Material.IRON_INGOT, 5);
            case CHAINMAIL_CHESTPLATE, IRON_CHESTPLATE -> put(result, Material.IRON_INGOT, 8);
            case CHAINMAIL_LEGGINGS, IRON_LEGGINGS -> put(result, Material.IRON_INGOT, 7);
            case CHAINMAIL_BOOTS, IRON_BOOTS -> put(result, Material.IRON_INGOT, 4);
            case COPPER_HELMET -> put(result, Material.COPPER_INGOT, 5);
            case COPPER_CHESTPLATE -> put(result, Material.COPPER_INGOT, 8);
            case COPPER_LEGGINGS -> put(result, Material.COPPER_INGOT, 7);
            case COPPER_BOOTS -> put(result, Material.COPPER_INGOT, 4);
            case GOLDEN_HELMET -> put(result, Material.GOLD_INGOT, 5);
            case GOLDEN_CHESTPLATE -> put(result, Material.GOLD_INGOT, 8);
            case GOLDEN_LEGGINGS -> put(result, Material.GOLD_INGOT, 7);
            case GOLDEN_BOOTS -> put(result, Material.GOLD_INGOT, 4);
            case DIAMOND_HELMET -> put(result, Material.DIAMOND, 5);
            case DIAMOND_CHESTPLATE -> put(result, Material.DIAMOND, 8);
            case DIAMOND_LEGGINGS -> put(result, Material.DIAMOND, 7);
            case DIAMOND_BOOTS -> put(result, Material.DIAMOND, 4);
            case NETHERITE_HELMET -> put(result, Material.DIAMOND, 5, Material.NETHERITE_SCRAP, 4, Material.GOLD_INGOT, 4);
            case NETHERITE_CHESTPLATE -> put(result, Material.DIAMOND, 8, Material.NETHERITE_SCRAP, 4, Material.GOLD_INGOT, 4);
            case NETHERITE_LEGGINGS -> put(result, Material.DIAMOND, 7, Material.NETHERITE_SCRAP, 4, Material.GOLD_INGOT, 4);
            case NETHERITE_BOOTS -> put(result, Material.DIAMOND, 4, Material.NETHERITE_SCRAP, 4, Material.GOLD_INGOT, 4);
            case TURTLE_HELMET -> put(result, Material.TURTLE_SCUTE, 5);
            case WOODEN_SWORD -> put(result, Material.OAK_PLANKS, 2, Material.STICK, 1);
            case WOODEN_PICKAXE, WOODEN_AXE -> put(result, Material.OAK_PLANKS, 3, Material.STICK, 2);
            case WOODEN_SHOVEL -> put(result, Material.OAK_PLANKS, 1, Material.STICK, 2);
            case WOODEN_HOE -> put(result, Material.OAK_PLANKS, 2, Material.STICK, 2);
            case STONE_SWORD -> put(result, Material.COBBLESTONE, 2, Material.STICK, 1);
            case STONE_PICKAXE, STONE_AXE -> put(result, Material.COBBLESTONE, 3, Material.STICK, 2);
            case STONE_SHOVEL -> put(result, Material.COBBLESTONE, 1, Material.STICK, 2);
            case STONE_HOE -> put(result, Material.COBBLESTONE, 2, Material.STICK, 2);
            case COPPER_SWORD -> put(result, Material.COPPER_INGOT, 2, Material.STICK, 1);
            case COPPER_PICKAXE, COPPER_AXE -> put(result, Material.COPPER_INGOT, 3, Material.STICK, 2);
            case COPPER_SHOVEL -> put(result, Material.COPPER_INGOT, 1, Material.STICK, 2);
            case COPPER_HOE -> put(result, Material.COPPER_INGOT, 2, Material.STICK, 2);
            case IRON_SWORD -> put(result, Material.IRON_INGOT, 2, Material.STICK, 1);
            case IRON_PICKAXE, IRON_AXE -> put(result, Material.IRON_INGOT, 3, Material.STICK, 2);
            case IRON_SHOVEL -> put(result, Material.IRON_INGOT, 1, Material.STICK, 2);
            case IRON_HOE -> put(result, Material.IRON_INGOT, 2, Material.STICK, 2);
            case GOLDEN_SWORD -> put(result, Material.GOLD_INGOT, 2, Material.STICK, 1);
            case GOLDEN_PICKAXE, GOLDEN_AXE -> put(result, Material.GOLD_INGOT, 3, Material.STICK, 2);
            case GOLDEN_SHOVEL -> put(result, Material.GOLD_INGOT, 1, Material.STICK, 2);
            case GOLDEN_HOE -> put(result, Material.GOLD_INGOT, 2, Material.STICK, 2);
            case DIAMOND_SWORD -> put(result, Material.DIAMOND, 2, Material.STICK, 1);
            case DIAMOND_PICKAXE, DIAMOND_AXE -> put(result, Material.DIAMOND, 3, Material.STICK, 2);
            case DIAMOND_SHOVEL -> put(result, Material.DIAMOND, 1, Material.STICK, 2);
            case DIAMOND_HOE -> put(result, Material.DIAMOND, 2, Material.STICK, 2);
            case NETHERITE_SWORD -> put(result, Material.DIAMOND, 2, Material.NETHERITE_SCRAP, 4, Material.GOLD_INGOT, 4, Material.STICK, 1);
            case NETHERITE_PICKAXE, NETHERITE_AXE -> put(result, Material.DIAMOND, 3, Material.NETHERITE_SCRAP, 4, Material.GOLD_INGOT, 4, Material.STICK, 2);
            case NETHERITE_SHOVEL -> put(result, Material.DIAMOND, 1, Material.NETHERITE_SCRAP, 4, Material.GOLD_INGOT, 4, Material.STICK, 2);
            case NETHERITE_HOE -> put(result, Material.DIAMOND, 2, Material.NETHERITE_SCRAP, 4, Material.GOLD_INGOT, 4, Material.STICK, 2);
            case BOW -> put(result, Material.STICK, 3, Material.STRING, 3);
            case CROSSBOW -> put(result, Material.STICK, 3, Material.STRING, 2, Material.IRON_INGOT, 1, Material.TRIPWIRE_HOOK, 1);
            case FISHING_ROD -> put(result, Material.STICK, 3, Material.STRING, 2);
            case SHIELD -> put(result, Material.OAK_PLANKS, 6, Material.IRON_INGOT, 1);
            case SHEARS -> put(result, Material.IRON_INGOT, 2);
            case FLINT_AND_STEEL -> put(result, Material.IRON_INGOT, 1, Material.FLINT, 1);
            case BRUSH -> put(result, Material.FEATHER, 1, Material.COPPER_INGOT, 1, Material.STICK, 1);
            case MACE -> put(result, Material.BREEZE_ROD, 1);
            case TRIDENT -> put(result, Material.PRISMARINE_SHARD, 6, Material.DIAMOND, 1);
            case ELYTRA -> put(result, Material.PHANTOM_MEMBRANE, 6);
            case WOLF_ARMOR -> put(result, Material.ARMADILLO_SCUTE, 6);
            case CARROT_ON_A_STICK -> put(result, Material.STICK, 3, Material.STRING, 2, Material.CARROT, 1);
            case WARPED_FUNGUS_ON_A_STICK -> put(result, Material.STICK, 3, Material.STRING, 2, Material.WARPED_FUNGUS, 1);
            case LEATHER_HORSE_ARMOR -> put(result, Material.LEATHER, 7);
            case IRON_HORSE_ARMOR -> put(result, Material.IRON_INGOT, 5);
            case GOLDEN_HORSE_ARMOR -> put(result, Material.GOLD_INGOT, 5);
            case DIAMOND_HORSE_ARMOR -> put(result, Material.DIAMOND, 5);
            case NETHERITE_HORSE_ARMOR -> put(result, Material.DIAMOND, 5, Material.NETHERITE_SCRAP, 4, Material.GOLD_INGOT, 4);
            case COPPER_HORSE_ARMOR -> put(result, Material.COPPER_INGOT, 5);
            case IRON_NAUTILUS_ARMOR -> put(result, Material.IRON_INGOT, 5);
            case GOLDEN_NAUTILUS_ARMOR -> put(result, Material.GOLD_INGOT, 5);
            case DIAMOND_NAUTILUS_ARMOR -> put(result, Material.DIAMOND, 5);
            case NETHERITE_NAUTILUS_ARMOR -> put(result, Material.DIAMOND, 5, Material.NETHERITE_SCRAP, 4, Material.GOLD_INGOT, 4);
            case COPPER_NAUTILUS_ARMOR -> put(result, Material.COPPER_INGOT, 5);
            default -> {
            }
        }
        if (!result.isEmpty() || !isRecipeSalvageCandidate(material)) {
            return result;
        }
        result.putAll(vanillaRecipeMaterials(material));
        if (result.isEmpty()) {
            Material fallback = preferredFallbackMaterial(material);
            if (fallback != null) {
                put(result, fallback, 1);
            }
        }
        return result;
    }

    private boolean isRecipeSalvageCandidate(Material material) {
        if (material == null) {
            return false;
        }
        return isGearMaterial(material)
            || (material.getMaxDurability() > 0 && material.isItem() && preferredFallbackMaterial(material) != null);
    }

    private Map<Material, Integer> vanillaRecipeMaterials(Material material) {
        Map<Material, Integer> result = new LinkedHashMap<>();
        Iterator<Recipe> recipes = Bukkit.recipeIterator();
        while (recipes.hasNext()) {
            Recipe recipe = recipes.next();
            if (!(recipe instanceof Keyed keyed) || !"minecraft".equals(keyed.getKey().getNamespace())) {
                continue;
            }
            ItemStack output = recipe.getResult();
            if (output == null || output.getType() != material) {
                continue;
            }
            Map<Material, Integer> ingredients = recipeIngredients(recipe);
            if (ingredients.isEmpty()) {
                continue;
            }
            int resultAmount = Math.max(1, output.getAmount());
            if (resultAmount <= 1) {
                return ingredients;
            }
            for (Map.Entry<Material, Integer> entry : ingredients.entrySet()) {
                int count = (int) Math.ceil(entry.getValue() / (double) resultAmount);
                if (count > 0) {
                    result.put(entry.getKey(), count);
                }
            }
            return result;
        }
        return result;
    }

    private Map<Material, Integer> recipeIngredients(Recipe recipe) {
        Map<Material, Integer> ingredients = new LinkedHashMap<>();
        if (recipe instanceof ShapedRecipe shaped) {
            Map<Character, RecipeChoice> choices = shaped.getChoiceMap();
            for (String row : shaped.getShape()) {
                for (int i = 0; i < row.length(); i++) {
                    char key = row.charAt(i);
                    if (key == ' ') {
                        continue;
                    }
                    addRecipeChoice(ingredients, choices.get(key));
                }
            }
            return ingredients;
        }

        if (recipe instanceof ShapelessRecipe shapeless) {
            for (RecipeChoice choice : shapeless.getChoiceList()) {
                addRecipeChoice(ingredients, choice);
            }
        }
        return ingredients;
    }

    private void addRecipeChoice(Map<Material, Integer> ingredients, RecipeChoice choice) {
        Material material = materialFromChoice(choice);
        if (material != null && !material.isAir()) {
            ingredients.merge(material, 1, Integer::sum);
        }
    }

    private Material materialFromChoice(RecipeChoice choice) {
        if (choice == null) {
            return null;
        }
        if (choice instanceof RecipeChoice.MaterialChoice materialChoice) {
            List<Material> choices = materialChoice.getChoices();
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            if (choices.contains(Material.OAK_PLANKS)) {
                return Material.OAK_PLANKS;
            }
            if (choices.contains(Material.COBBLESTONE)) {
                return Material.COBBLESTONE;
            }
            if (choices.contains(Material.IRON_INGOT)) {
                return Material.IRON_INGOT;
            }
            if (choices.contains(Material.GOLD_INGOT)) {
                return Material.GOLD_INGOT;
            }
            if (choices.contains(Material.COPPER_INGOT)) {
                return Material.COPPER_INGOT;
            }
            if (choices.contains(Material.DIAMOND)) {
                return Material.DIAMOND;
            }
            return choices.get(0);
        }

        if (choice instanceof RecipeChoice.ExactChoice exactChoice) {
            List<ItemStack> choices = exactChoice.getChoices();
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            ItemStack stack = choices.get(0);
            return stack == null ? null : stack.getType();
        }

        return null;
    }

    private void put(Map<Material, Integer> result, Object... pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            Material material = (Material) pairs[i];
            int amount = (Integer) pairs[i + 1];
            result.merge(material, amount, Integer::sum);
        }
    }

    private boolean isPluginManagedItem(ItemStack item) {
        if (isDepotItem(item)) {
            return true;
        }
        if (plugin.getAgriculturalPylonListener() != null && plugin.getAgriculturalPylonListener().isPylonItem(item)) {
            return true;
        }
        if (plugin.getLegendaryListener() != null
            && (plugin.getLegendaryListener().isLegendaryItem(item)
                || plugin.getLegendaryListener().isEnderBoneItem(item)
                || plugin.getLegendaryListener().isOrbOfTheMysticsItem(item))) {
            return true;
        }
        if (plugin.getBackpackListener() != null && plugin.getBackpackListener().isBackpack(item)) {
            return true;
        }
        if (plugin.getSeasonRelicManager() != null && plugin.getSeasonRelicManager().isSeasonRelic(item)) {
            return true;
        }
        if (plugin.getBossMasteryManager() != null && plugin.getBossMasteryManager().isMasteryItem(item)) {
            return true;
        }
        if (plugin.getSustenanceTalismanListener() != null && plugin.getSustenanceTalismanListener().isTalisman(item)) {
            return true;
        }
        if (plugin.getXpLecternListener() != null && plugin.getXpLecternListener().isLecternItem(item)) {
            return true;
        }
        if (plugin.getBossPotionListener() != null && plugin.getBossPotionListener().isBossPotion(item)) {
            return true;
        }
        if (plugin.getAwakeningTableListener() != null && plugin.getAwakeningTableListener().isAwakeningTableCustomItem(item)) {
            return true;
        }
        if (plugin.getMythicForgeListener() != null
            && (plugin.getMythicForgeListener().isMythicForgeItemStack(item)
                || plugin.getMythicForgeListener().isAscendantCoreItem(item))) {
            return true;
        }
        if (plugin.getBossManager() != null && plugin.getBossManager().isDominionCore(item)) {
            return true;
        }
        return plugin.getSuperpowerManager() != null
            && (plugin.getSuperpowerManager().isAncientScroll(item)
                || plugin.getSuperpowerManager().isWardenHeart(item)
                || plugin.getSuperpowerManager().isMotherNatureStick(item)
                || plugin.getSuperpowerManager().isTheWorldClock(item)
                || plugin.getSuperpowerManager().isDruidGrimoire(item));
    }

    private String describeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "air";
        }
        return item.getType().name() + "x" + Math.max(1, item.getAmount());
    }

    private List<Block> adjacentChests(Block block) {
        List<Block> chests = new ArrayList<>(2);
        for (BlockFace face : HORIZONTAL_FACES) {
            Block adjacent = block.getRelative(face);
            if (adjacent.getType() == Material.CHEST) chests.add(adjacent);
        }
        return chests;
    }

    private boolean canPlaceDepotBeside(Block block, List<Block> adjacentChests) {
        if (adjacentChests == null || adjacentChests.isEmpty()) return true;
        if (adjacentChests.size() != 1) return false;
        Block adjacent = adjacentChests.getFirst();
        boolean depot = isDepotBlock(adjacent) || pendingDepotBlocks.contains(blockKey(adjacent));
        return allowsDepotPlacement(1, depot, isConnectedChestPair(block, adjacent));
    }

    static boolean allowsDepotPlacement(int adjacentChestCount, boolean adjacentIsDepot, boolean connectedPair) {
        return adjacentChestCount == 0
            || adjacentChestCount == 1 && adjacentIsDepot && connectedPair;
    }

    private boolean isConnectedChestPair(Block first, Block second) {
        if (first == null || second == null || first.getType() != Material.CHEST || second.getType() != Material.CHEST) {
            return false;
        }
        BlockState state = first.getState();
        if (!(state instanceof Chest chest) || !(chest.getInventory().getHolder(false) instanceof DoubleChest doubleChest)) {
            return false;
        }
        Block left = containerBlock(doubleChest.getLeftSide());
        Block right = containerBlock(doubleChest.getRightSide());
        return sameBlock(first, left) && sameBlock(second, right)
            || sameBlock(first, right) && sameBlock(second, left);
    }

    private boolean hasAdjacentDepot(Block block) {
        for (BlockFace face : HORIZONTAL_FACES) {
            Block adjacent = block.getRelative(face);
            if (isDepotBlock(adjacent) || pendingDepotBlocks.contains(blockKey(adjacent))) {
                return true;
            }
        }
        return false;
    }

    private Block adjacentDepot(Block block) {
        if (block == null) return null;
        for (BlockFace face : HORIZONTAL_FACES) {
            Block adjacent = block.getRelative(face);
            if (isDepotBlock(adjacent)) return adjacent;
        }
        return null;
    }

    private boolean hasProcessingItem(Block block) {
        Inventory inventory = depotInventory(block);
        if (inventory == null) {
            return false;
        }
        for (ItemStack item : inventory.getStorageContents()) {
            if (isProcessing(item)) {
                return true;
            }
        }
        return false;
    }

    public boolean isDepotBlock(Block block) {
        if (block == null || block.getType() != Material.CHEST) {
            return false;
        }
        BlockState state = block.getState();
        if (state instanceof TileState tile
            && ITEM_ID.equals(tile.getPersistentDataContainer().get(keyDepotBlock, PersistentDataType.STRING))) {
            return true;
        }
        return false;
    }

    private boolean resolveDepotInventory(Inventory inventory) {
        return depotBlock(inventory) != null;
    }

    public boolean isRecoveryTrackedInventory(Inventory inventory) {
        return resolveDepotInventory(inventory);
    }

    private boolean isDepotInventory(Inventory inventory) {
        return resolveDepotInventory(inventory);
    }

    private Block depotBlock(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        Block block = depotBlock(inventory.getHolder(false));
        if (block != null) {
            rememberDepotInventory(inventory, block);
            return block;
        }
        block = depotBlock(inventory.getHolder());
        if (block != null) {
            rememberDepotInventory(inventory, block);
            return block;
        }

        // A detached double-chest Inventory can survive for a few scheduled ticks after one half breaks.
        // Never resolve that stale 54-slot view through its old location or cache entry.
        if (inventory.getSize() > 27) return null;

        Location location = inventory.getLocation();
        if (location != null && location.getWorld() != null) {
            block = location.getBlock();
            if (isDepotBlock(block)) {
                rememberDepotInventory(inventory, block);
                return block;
            }
        }

        String cachedKey = depotBlocksByInventory.get(inventory);
        Location cachedLocation = locationFromBlockKey(cachedKey);
        block = cachedLocation == null ? null : cachedLocation.getBlock();
        if (isDepotBlock(block)) {
            return block;
        }
        if (cachedKey != null) {
            depotBlocksByInventory.remove(inventory);
        }
        return null;
    }

    private void rememberDepotInventory(Inventory inventory, Block block) {
        if (inventory == null || block == null || !isDepotBlock(block)) {
            return;
        }
        String blockKey = blockKey(block);
        knownDepotBlocks.add(blockKey);
        depotBlocksByInventory.put(inventory, blockKey);
    }

    private void forgetDepotInventories(Block block) {
        if (block == null) return;
        Set<String> groupKeys = new java.util.HashSet<>();
        groupKeys.add(blockKey(block));
        Block pair = pairedDepotBlock(block);
        if (pair != null) groupKeys.add(blockKey(pair));
        Block canonical = canonicalDepotBlock(block);
        if (canonical != null) groupKeys.add(blockKey(canonical));
        synchronized (depotBlocksByInventory) {
            depotBlocksByInventory.entrySet().removeIf(entry -> groupKeys.contains(entry.getValue()));
        }
    }

    private Block depotBlock(InventoryHolder holder) {
        Block direct = singleDepotBlock(holder);
        if (direct != null) {
            return direct;
        }
        if (holder instanceof DoubleChest doubleChest) {
            Block left = containerBlock(doubleChest.getLeftSide());
            Block right = containerBlock(doubleChest.getRightSide());
            if (!isDepotBlock(left) || !isDepotBlock(right)) return null;
            return canonicalBlock(left, right);
        }
        return null;
    }

    private Block singleDepotBlock(InventoryHolder holder) {
        Block block = containerBlock(holder);
        return isDepotBlock(block) ? block : null;
    }

    private Block containerBlock(InventoryHolder holder) {
        if (holder instanceof Chest chest) return chest.getBlock();
        if (holder instanceof Container container) return container.getBlock();
        return null;
    }

    private Inventory depotInventory(Block block) {
        if (!isDepotBlock(block)) {
            return null;
        }
        BlockState state = block.getState();
        if (!(state instanceof Chest chest)) {
            return null;
        }
        Inventory combined = chest.getInventory();
        Inventory inventory = depotBlock(combined.getHolder(false)) == null
            ? chest.getBlockInventory()
            : combined;
        rememberDepotInventory(inventory, canonicalDepotBlock(block));
        return inventory;
    }

    private Inventory depotLocalInventory(Block block) {
        if (!isDepotBlock(block)) return null;
        BlockState state = block.getState();
        return state instanceof Chest chest ? chest.getBlockInventory() : null;
    }

    private Block pairedDepotBlock(Block block) {
        if (!isDepotBlock(block)) return null;
        BlockState state = block.getState();
        if (!(state instanceof Chest chest) || !(chest.getInventory().getHolder(false) instanceof DoubleChest doubleChest)) {
            return null;
        }
        Block left = containerBlock(doubleChest.getLeftSide());
        Block right = containerBlock(doubleChest.getRightSide());
        if (!isDepotBlock(left) || !isDepotBlock(right)) return null;
        if (sameBlock(block, left)) return right;
        if (sameBlock(block, right)) return left;
        return null;
    }

    private Block canonicalDepotBlock(Block block) {
        if (!isDepotBlock(block)) return block;
        Block pair = pairedDepotBlock(block);
        return pair == null ? block : canonicalBlock(block, pair);
    }

    private Block canonicalBlock(Block first, Block second) {
        if (first == null) return second;
        if (second == null) return first;
        if (first.getX() != second.getX()) return first.getX() < second.getX() ? first : second;
        if (first.getY() != second.getY()) return first.getY() < second.getY() ? first : second;
        return first.getZ() <= second.getZ() ? first : second;
    }

    private boolean sameBlock(Block first, Block second) {
        return first != null && second != null
            && first.getWorld().getUID().equals(second.getWorld().getUID())
            && first.getX() == second.getX()
            && first.getY() == second.getY()
            && first.getZ() == second.getZ();
    }

    private void reconcileDepot(Block block) {
        if (!isDepotBlock(block)) return;
        Block canonical = canonicalDepotBlock(block);
        Block pair = pairedDepotBlock(canonical);
        boolean large = pair != null;
        updateDepotName(canonical, large);
        if (pair != null) updateDepotName(pair, true);
        ensureHologram(canonical);
        scanDepot(canonical);
    }

    private void updateDepotName(Block block, boolean large) {
        if (!isDepotBlock(block)) return;
        BlockState state = block.getState();
        if (!(state instanceof Chest chest)) return;
        chest.customName(MM.deserialize(large
            ? "<gradient:#74ee15:#22c55e><bold>Large Salvaging Depot</bold></gradient>"
            : "<gradient:#74ee15:#22c55e><bold>Salvaging Depot</bold></gradient>"));
        chest.update(true, false);
    }

    private void ensureHologram(Block block) {
        if (!isDepotBlock(block)) {
            return;
        }
        Block canonical = canonicalDepotBlock(block);
        Block pair = pairedDepotBlock(canonical);
        if (pair != null && !sameBlock(pair, canonical)) removeHologramExact(pair);
        String blockKey = blockKey(canonical);
        knownDepotBlocks.add(blockKey);
        UUID existingId = hologramsByBlock.get(blockKey);
        Entity existing = existingId == null ? null : Bukkit.getEntity(existingId);
        if (existing instanceof TextDisplay display && display.isValid()) {
            display.teleport(hologramLocation(canonical));
            display.text(hologramText(canonical));
            VisualRangeUtil.applyHologramRange(display);
            return;
        }

        removeHologramExact(canonical);
        canonical.getWorld().spawn(hologramLocation(canonical), TextDisplay.class, display -> {
            display.text(hologramText(canonical));
            display.setGravity(false);
            display.setPersistent(false);
            display.setInvulnerable(true);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(false);
            display.setShadowed(false);
            VisualRangeUtil.applyHologramRange(display);
            display.setLineWidth(180);
            display.setTextOpacity((byte) 255);
            display.setBackgroundColor(Color.fromARGB(96, 8, 12, 10));
            PersistentDataContainer pdc = display.getPersistentDataContainer();
            pdc.set(keyDepotHologram, PersistentDataType.BYTE, (byte) 1);
            pdc.set(keyDepotHologramBlock, PersistentDataType.STRING, blockKey);
            hologramsByBlock.put(blockKey, display.getUniqueId());
        });
    }

    private Component hologramText(Block block) {
        boolean large = pairedDepotBlock(block) != null;
        return Component.empty()
            .append(MM.deserialize(large
                ? "<gradient:#74ee15:#22c55e><bold>Large Salvaging Depot</bold></gradient>"
                : "<gradient:#74ee15:#22c55e><bold>Salvaging Depot</bold></gradient>"))
            .append(Component.newline())
            .append(MM.deserialize("<gray>" + (large ? "54 slots | " : "") + cancelWindowSeconds()
                + "s cancel, then <white>" + processingSeconds() + "s</white> salvage</gray>"));
    }

    private Location hologramLocation(Block block) {
        Block pair = pairedDepotBlock(block);
        if (pair == null) return block.getLocation().add(0.5, 1.45, 0.5);
        return new Location(
            block.getWorld(),
            (block.getX() + pair.getX()) / 2.0D + 0.5D,
            block.getY() + 1.45D,
            (block.getZ() + pair.getZ()) / 2.0D + 0.5D
        );
    }

    private void removeDepotHolograms(Block block) {
        if (block == null) return;
        Block pair = pairedDepotBlock(block);
        removeHologramExact(block);
        if (pair != null) removeHologramExact(pair);
    }

    private void removeHologramExact(Block block) {
        if (block == null) {
            return;
        }
        String blockKey = blockKey(block);
        UUID displayId = hologramsByBlock.remove(blockKey);
        if (displayId != null) {
            Entity entity = Bukkit.getEntity(displayId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        for (Entity entity : block.getChunk().getEntities()) {
            if (isDepotHologramFor(entity, blockKey)) {
                entity.remove();
            }
        }
    }

    private void removeChunkHolograms(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (!isDepotHologram(entity)) {
                continue;
            }
            String blockKey = entity.getPersistentDataContainer().get(keyDepotHologramBlock, PersistentDataType.STRING);
            if (blockKey != null) {
                hologramsByBlock.remove(blockKey);
            }
            entity.remove();
        }
    }

    private void removeStaleChunkHolograms(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (!isDepotHologram(entity)) {
                continue;
            }
            String blockKey = entity.getPersistentDataContainer().get(keyDepotHologramBlock, PersistentDataType.STRING);
            Location location = locationFromBlockKey(blockKey);
            if (location == null || !isDepotBlock(location.getBlock())) {
                if (blockKey != null) {
                    hologramsByBlock.remove(blockKey);
                }
                entity.remove();
            }
        }
    }

    private boolean isDepotHologram(Entity entity) {
        return entity != null
            && entity.getPersistentDataContainer().has(keyDepotHologram, PersistentDataType.BYTE);
    }

    private boolean isDepotHologramFor(Entity entity, String blockKey) {
        return isDepotHologram(entity)
            && blockKey.equals(entity.getPersistentDataContainer().get(keyDepotHologramBlock, PersistentDataType.STRING));
    }

    private String blockKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private Location locationFromBlockKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String[] parts = key.split(":");
        if (parts.length != 4) {
            return null;
        }
        try {
            UUID worldId = UUID.fromString(parts[0]);
            World world = Bukkit.getWorlds().stream()
                .filter(candidate -> candidate.getUID().equals(worldId))
                .findFirst()
                .orElse(null);
            if (world == null) {
                return null;
            }
            return new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
