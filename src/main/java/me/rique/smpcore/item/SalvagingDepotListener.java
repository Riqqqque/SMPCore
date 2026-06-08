package me.rique.smpcore.item;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SalvagingDepotListener implements Listener {

    public static final String ITEM_ID = "salvaging_depot";

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final long PROCESS_TICKS = 20L * 6L;
    private static final long PROCESS_MILLIS = 6_000L;
    private static final double RETURN_RATE = 0.66D;
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
    private final NamespacedKey recipeKey;
    private final Map<String, UUID> hologramsByBlock = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> processingTasks = new ConcurrentHashMap<>();
    private BukkitTask maintenanceTask;

    public SalvagingDepotListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyDepotItem = new NamespacedKey(plugin, ITEM_ID);
        this.keyDepotBlock = new NamespacedKey(plugin, "salvaging_depot_block");
        this.keyDepotHologram = new NamespacedKey(plugin, "salvaging_depot_hologram");
        this.keyDepotHologramBlock = new NamespacedKey(plugin, "salvaging_depot_hologram_block");
        this.keyProcessingId = new NamespacedKey(plugin, "salvage_processing_id");
        this.keyProcessingReadyAt = new NamespacedKey(plugin, "salvage_ready_at");
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
        maintenanceTask = Bukkit.getScheduler().runTaskTimer(plugin, this::syncLoadedDepots, 100L, 200L);
    }

    public void shutdown() {
        if (maintenanceTask != null) {
            maintenanceTask.cancel();
            maintenanceTask = null;
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
    }

    public ItemStack createDepotItem() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.UNCOMMON, "Salvaging Depot"));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.CHEST,
            CustomLoreUtil.Rarity.UNCOMMON.label(),
            "UTILITY STATION",
            List.of(
                "<gray>Break old gear down into raw materials.</gray>",
                "<gray>Works with manual inserts and hoppers.</gray>"
            ),
            List.of(
                CustomLoreUtil.section(
                    "Use",
                    "Six-Second Salvage",
                    "<gray>Put vanilla armor, weapons, or tools inside.</gray>",
                    "<gray>After <white>6s</white>, the item is consumed and returns about <white>66%</white> of its base materials.</gray>",
                    "<gray>Damaged items return less.</gray>"
                ),
                CustomLoreUtil.section(
                    "Safety",
                    "No Relic Recycling",
                    "<gray>Custom items, legendaries, backpacks, boss relics, and power items are ignored.</gray>",
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

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipe(recipeKey);
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

        if (hasAdjacentChest(block)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.warn("Place the Salvaging Depot with one block of space from other chests."));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> setupPlacedDepot(block));
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
        removeHologram(block);
        Inventory inventory = depotInventory(block);
        if (inventory != null) {
            for (ItemStack item : inventory.getStorageContents()) {
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.6, 0.5), item.clone());
            }
            inventory.clear();
        }
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.6, 0.5), createDepotItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isDepotBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isDepotBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!isDepotInventory(top)) {
            return;
        }

        if (event.getRawSlot() >= 0 && event.getRawSlot() < top.getSize()) {
            ItemStack current = event.getCurrentItem();
            if (isProcessing(current)) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    player.sendMessage(MessageUtil.warn("Items placed in the Salvaging Depot are locked until salvage finishes."));
                }
                scheduleScan(top);
                return;
            }
            if (!salvageOutputs(current).isEmpty()) {
                event.setCancelled(true);
                scanDepot(top);
                if (event.getWhoClicked() instanceof Player player) {
                    player.sendMessage(MessageUtil.warn("That item is being salvaged now."));
                }
                return;
            }
        }

        scheduleScan(top);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (isDepotInventory(top)) {
            scheduleScan(top);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (isDepotInventory(event.getInventory())) {
            scanDepot(event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (isDepotInventory(event.getInventory())) {
            scheduleScan(event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (isDepotInventory(event.getSource())
            && (isProcessing(event.getItem()) || !salvageOutputs(event.getItem()).isEmpty())) {
            event.setCancelled(true);
            return;
        }

        if (isDepotInventory(event.getDestination())) {
            scheduleScan(event.getDestination());
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        syncChunkDepots(event.getChunk());
    }

    @EventHandler
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

    private void setupPlacedDepot(Block block) {
        if (block.getType() != Material.CHEST) {
            return;
        }
        BlockData data = block.getBlockData();
        if (data instanceof org.bukkit.block.data.type.Chest chestData) {
            chestData.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
            block.setBlockData(chestData, false);
        }
        BlockState state = block.getState();
        if (state instanceof Chest chest) {
            chest.customName(MM.deserialize("<gradient:#74ee15:#22c55e><bold>Salvaging Depot</bold></gradient>"));
            chest.getPersistentDataContainer().set(keyDepotBlock, PersistentDataType.STRING, ITEM_ID);
            chest.update(true, false);
        }
        ensureHologram(block);
        scanDepot(block);
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_ANVIL_PLACE, 0.75f, 1.35f);
        block.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, block.getLocation().add(0.5, 1.1, 0.5), 18, 0.28, 0.24, 0.28, 0.03);
    }

    private void syncLoadedDepots() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                syncChunkDepots(chunk);
            }
        }
    }

    private void syncChunkDepots(Chunk chunk) {
        removeStaleChunkHolograms(chunk);
        for (BlockState tile : chunk.getTileEntities()) {
            if (!(tile instanceof Chest chest)) {
                continue;
            }
            Block block = chest.getBlock();
            if (!isDepotBlock(block)) {
                continue;
            }
            ensureHologram(block);
            scanDepot(block);
        }
    }

    private void scanDepot(Block block) {
        Inventory inventory = depotInventory(block);
        if (inventory != null) {
            scanDepot(inventory);
        }
    }

    private void scanDepot(Inventory inventory) {
        Block block = depotBlock(inventory);
        if (block == null || !isDepotBlock(block)) {
            return;
        }

        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (isProcessing(item)) {
                UUID processId = processingId(item);
                long readyAt = processingReadyAt(item);
                if (processId != null) {
                    scheduleCompletion(blockKey(block), processId, readyAt);
                }
                continue;
            }
            List<ItemStack> outputs = salvageOutputs(item);
            if (outputs.isEmpty()) {
                continue;
            }

            UUID processId = UUID.randomUUID();
            long readyAt = System.currentTimeMillis() + PROCESS_MILLIS;
            inventory.setItem(slot, markProcessing(item, processId, readyAt));
            scheduleCompletion(blockKey(block), processId, readyAt);
            block.getWorld().playSound(block.getLocation().add(0.5, 0.6, 0.5), Sound.BLOCK_GRINDSTONE_USE, 0.7f, 1.15f);
            block.getWorld().spawnParticle(Particle.WAX_OFF, block.getLocation().add(0.5, 1.05, 0.5), 12, 0.24, 0.18, 0.24, 0.02);
        }
    }

    private void scheduleScan(Inventory inventory) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isDepotInventory(inventory)) {
                scanDepot(inventory);
            }
        });
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isDepotInventory(inventory)) {
                scanDepot(inventory);
            }
        }, 2L);
    }

    private void scheduleCompletion(String blockKey, UUID processId, long readyAt) {
        if (processId == null || processingTasks.containsKey(processId)) {
            return;
        }
        long delay = readyAt <= 0L
            ? PROCESS_TICKS
            : Math.max(1L, (readyAt - System.currentTimeMillis() + 49L) / 50L);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            processingTasks.remove(processId);
            finishProcessing(blockKey, processId);
        }, delay);
        processingTasks.put(processId, task);
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

            List<ItemStack> outputs = salvageOutputs(item);
            inventory.setItem(slot, null);
            if (!outputs.isEmpty()) {
                Map<Integer, ItemStack> leftovers = inventory.addItem(outputs.toArray(ItemStack[]::new));
                leftovers.values().forEach(leftover ->
                    block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.8, 0.5), leftover)
                );
            }
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
        pdc.set(keyProcessingId, PersistentDataType.STRING, processId.toString());
        pdc.set(keyProcessingReadyAt, PersistentDataType.LONG, readyAt);
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(MM.deserialize("<yellow>Salvaging...</yellow> <dark_gray>6s</dark_gray>"));
        meta.lore(lore);
        marked.setItemMeta(meta);
        return marked;
    }

    private boolean isProcessing(ItemStack item) {
        return processingId(item) != null;
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
        return readyAt == null ? System.currentTimeMillis() + PROCESS_MILLIS : readyAt;
    }

    private List<ItemStack> salvageOutputs(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() != 1 || isPluginManagedItem(item)) {
            return List.of();
        }

        Map<Material, Integer> base = baseMaterials(item.getType());
        if (base.isEmpty()) {
            return List.of();
        }

        double durabilityFactor = durabilityFactor(item);
        List<ItemStack> outputs = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : base.entrySet()) {
            int count = returnedCount(entry.getValue(), durabilityFactor);
            if (count <= 0) {
                continue;
            }
            addOutput(outputs, entry.getKey(), count);
        }
        return outputs;
    }

    private int returnedCount(int baseCount, double durabilityFactor) {
        double exact = baseCount * RETURN_RATE * durabilityFactor;
        int count = (int) Math.floor(exact);
        if (count == 0 && exact >= 0.50D) {
            count = 1;
        }
        return Math.max(0, count);
    }

    private double durabilityFactor(ItemStack item) {
        if (!(item.getItemMeta() instanceof Damageable damageable) || item.getType().getMaxDurability() <= 0) {
            return 1.0D;
        }
        double max = item.getType().getMaxDurability();
        double remaining = Math.max(0.0D, max - damageable.getDamage());
        return Math.max(0.0D, Math.min(1.0D, remaining / max));
    }

    private void addOutput(List<ItemStack> outputs, Material material, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(material.getMaxStackSize(), remaining);
            outputs.add(new ItemStack(material, stackSize));
            remaining -= stackSize;
        }
    }

    private Map<Material, Integer> baseMaterials(Material material) {
        Map<Material, Integer> result = new HashMap<>();
        switch (material) {
            case LEATHER_HELMET -> put(result, Material.LEATHER, 5);
            case LEATHER_CHESTPLATE -> put(result, Material.LEATHER, 8);
            case LEATHER_LEGGINGS -> put(result, Material.LEATHER, 7);
            case LEATHER_BOOTS -> put(result, Material.LEATHER, 4);
            case CHAINMAIL_HELMET, IRON_HELMET -> put(result, Material.IRON_INGOT, 5);
            case CHAINMAIL_CHESTPLATE, IRON_CHESTPLATE -> put(result, Material.IRON_INGOT, 8);
            case CHAINMAIL_LEGGINGS, IRON_LEGGINGS -> put(result, Material.IRON_INGOT, 7);
            case CHAINMAIL_BOOTS, IRON_BOOTS -> put(result, Material.IRON_INGOT, 4);
            case GOLDEN_HELMET -> put(result, Material.GOLD_INGOT, 5);
            case GOLDEN_CHESTPLATE -> put(result, Material.GOLD_INGOT, 8);
            case GOLDEN_LEGGINGS -> put(result, Material.GOLD_INGOT, 7);
            case GOLDEN_BOOTS -> put(result, Material.GOLD_INGOT, 4);
            case DIAMOND_HELMET -> put(result, Material.DIAMOND, 5);
            case DIAMOND_CHESTPLATE -> put(result, Material.DIAMOND, 8);
            case DIAMOND_LEGGINGS -> put(result, Material.DIAMOND, 7);
            case DIAMOND_BOOTS -> put(result, Material.DIAMOND, 4);
            case NETHERITE_HELMET -> put(result, Material.DIAMOND, 5, Material.NETHERITE_INGOT, 1);
            case NETHERITE_CHESTPLATE -> put(result, Material.DIAMOND, 8, Material.NETHERITE_INGOT, 1);
            case NETHERITE_LEGGINGS -> put(result, Material.DIAMOND, 7, Material.NETHERITE_INGOT, 1);
            case NETHERITE_BOOTS -> put(result, Material.DIAMOND, 4, Material.NETHERITE_INGOT, 1);
            case TURTLE_HELMET -> put(result, Material.TURTLE_SCUTE, 5);
            case WOODEN_SWORD -> put(result, Material.OAK_PLANKS, 2, Material.STICK, 1);
            case WOODEN_PICKAXE, WOODEN_AXE -> put(result, Material.OAK_PLANKS, 3, Material.STICK, 2);
            case WOODEN_SHOVEL -> put(result, Material.OAK_PLANKS, 1, Material.STICK, 2);
            case WOODEN_HOE -> put(result, Material.OAK_PLANKS, 2, Material.STICK, 2);
            case STONE_SWORD -> put(result, Material.COBBLESTONE, 2, Material.STICK, 1);
            case STONE_PICKAXE, STONE_AXE -> put(result, Material.COBBLESTONE, 3, Material.STICK, 2);
            case STONE_SHOVEL -> put(result, Material.COBBLESTONE, 1, Material.STICK, 2);
            case STONE_HOE -> put(result, Material.COBBLESTONE, 2, Material.STICK, 2);
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
            case NETHERITE_SWORD -> put(result, Material.DIAMOND, 2, Material.NETHERITE_INGOT, 1, Material.STICK, 1);
            case NETHERITE_PICKAXE, NETHERITE_AXE -> put(result, Material.DIAMOND, 3, Material.NETHERITE_INGOT, 1, Material.STICK, 2);
            case NETHERITE_SHOVEL -> put(result, Material.DIAMOND, 1, Material.NETHERITE_INGOT, 1, Material.STICK, 2);
            case NETHERITE_HOE -> put(result, Material.DIAMOND, 2, Material.NETHERITE_INGOT, 1, Material.STICK, 2);
            case BOW -> put(result, Material.STICK, 3, Material.STRING, 3);
            case CROSSBOW -> put(result, Material.STICK, 3, Material.STRING, 2, Material.IRON_INGOT, 1, Material.TRIPWIRE_HOOK, 1);
            case FISHING_ROD -> put(result, Material.STICK, 3, Material.STRING, 2);
            case SHIELD -> put(result, Material.OAK_PLANKS, 6, Material.IRON_INGOT, 1);
            case SHEARS -> put(result, Material.IRON_INGOT, 2);
            case FLINT_AND_STEEL -> put(result, Material.IRON_INGOT, 1, Material.FLINT, 1);
            case BRUSH -> put(result, Material.FEATHER, 1, Material.COPPER_INGOT, 1, Material.STICK, 1);
            case MACE -> put(result, Material.HEAVY_CORE, 1, Material.BREEZE_ROD, 1);
            case TRIDENT -> put(result, Material.PRISMARINE_SHARD, 6, Material.DIAMOND, 1);
            case ELYTRA -> put(result, Material.PHANTOM_MEMBRANE, 6);
            case LEATHER_HORSE_ARMOR -> put(result, Material.LEATHER, 7);
            case IRON_HORSE_ARMOR -> put(result, Material.IRON_INGOT, 5);
            case GOLDEN_HORSE_ARMOR -> put(result, Material.GOLD_INGOT, 5);
            case DIAMOND_HORSE_ARMOR -> put(result, Material.DIAMOND, 5);
            case NETHERITE_HORSE_ARMOR -> put(result, Material.DIAMOND, 5, Material.NETHERITE_INGOT, 1);
            case COPPER_HORSE_ARMOR -> put(result, Material.COPPER_INGOT, 5);
            default -> {
            }
        }
        return result;
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
        if (plugin.getLegendaryListener() != null
            && (plugin.getLegendaryListener().isLegendaryItem(item)
                || plugin.getLegendaryListener().isEnderBoneItem(item)
                || plugin.getLegendaryListener().isOrbOfTheMysticsItem(item))) {
            return true;
        }
        if (plugin.getCustomToolListener() != null && plugin.getCustomToolListener().isCustomTool(item)) {
            return true;
        }
        if (plugin.getBackpackListener() != null && plugin.getBackpackListener().isBackpack(item)) {
            return true;
        }
        if (plugin.getSeasonRelicManager() != null && plugin.getSeasonRelicManager().isSeasonRelic(item)) {
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

    private boolean hasAdjacentChest(Block block) {
        for (BlockFace face : HORIZONTAL_FACES) {
            if (block.getRelative(face).getType() == Material.CHEST) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAdjacentDepot(Block block) {
        for (BlockFace face : HORIZONTAL_FACES) {
            if (isDepotBlock(block.getRelative(face))) {
                return true;
            }
        }
        return false;
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

    private boolean isDepotBlock(Block block) {
        if (block == null || block.getType() != Material.CHEST) {
            return false;
        }
        BlockState state = block.getState();
        return state instanceof TileState tile
            && ITEM_ID.equals(tile.getPersistentDataContainer().get(keyDepotBlock, PersistentDataType.STRING));
    }

    private boolean isDepotInventory(Inventory inventory) {
        return depotBlock(inventory) != null;
    }

    private Block depotBlock(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        InventoryHolder holder = inventory.getHolder(false);
        if (holder instanceof Chest chest && isDepotBlock(chest.getBlock())) {
            return chest.getBlock();
        }
        return null;
    }

    private Inventory depotInventory(Block block) {
        if (!isDepotBlock(block)) {
            return null;
        }
        BlockState state = block.getState();
        return state instanceof Chest chest ? chest.getBlockInventory() : null;
    }

    private void ensureHologram(Block block) {
        if (!isDepotBlock(block)) {
            return;
        }
        String blockKey = blockKey(block);
        UUID existingId = hologramsByBlock.get(blockKey);
        Entity existing = existingId == null ? null : Bukkit.getEntity(existingId);
        if (existing instanceof TextDisplay display && display.isValid()) {
            display.teleport(hologramLocation(block));
            display.text(hologramText());
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
            display.setSeeThrough(true);
            display.setShadowed(false);
            display.setViewRange(28.0f);
            display.setLineWidth(180);
            display.setTextOpacity((byte) 255);
            display.setBackgroundColor(Color.fromARGB(96, 8, 12, 10));
            PersistentDataContainer pdc = display.getPersistentDataContainer();
            pdc.set(keyDepotHologram, PersistentDataType.BYTE, (byte) 1);
            pdc.set(keyDepotHologramBlock, PersistentDataType.STRING, blockKey);
            hologramsByBlock.put(blockKey, display.getUniqueId());
        });
    }

    private Component hologramText() {
        return Component.empty()
            .append(MM.deserialize("<gradient:#74ee15:#22c55e><bold>Salvaging Depot</bold></gradient>"))
            .append(Component.newline())
            .append(MM.deserialize("<gray>Recycles vanilla gear in <white>6s</white></gray>"));
    }

    private Location hologramLocation(Block block) {
        return block.getLocation().add(0.5, 1.45, 0.5);
    }

    private void removeHologram(Block block) {
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
