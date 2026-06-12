package me.rique.smpcore.item;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.InventoryRecipeUtil;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.util.VisualRangeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AgriculturalPylonListener implements Listener {

    public static final String ITEM_ID = "agricultural_pylon";

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Material PYLON_BLOCK_TYPE = Material.BARREL;

    private final SMPCore plugin;
    private final NamespacedKey keyPylonItem;
    private final NamespacedKey keyPylonBlock;
    private final NamespacedKey keyPylonHologram;
    private final NamespacedKey keyPylonHologramBlock;
    private final NamespacedKey recipeKey;
    private final Map<UUID, Set<BlockKey>> pylonsByWorld = new ConcurrentHashMap<>();
    private final Map<String, UUID> hologramsByBlock = new ConcurrentHashMap<>();
    private BukkitTask maintenanceTask;

    public AgriculturalPylonListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyPylonItem = new NamespacedKey(plugin, ITEM_ID);
        this.keyPylonBlock = new NamespacedKey(plugin, "agricultural_pylon_block");
        this.keyPylonHologram = new NamespacedKey(plugin, "agricultural_pylon_hologram");
        this.keyPylonHologramBlock = new NamespacedKey(plugin, "agricultural_pylon_hologram_block");
        this.recipeKey = new NamespacedKey(plugin, ITEM_ID);
    }

    public void start() {
        registerRecipe();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.discoverRecipe(recipeKey);
            }
            syncLoadedPylons();
        });
        maintenanceTask = Bukkit.getScheduler().runTaskTimer(plugin, this::syncLoadedPylons, 100L, 400L);
    }

    public void shutdown() {
        if (maintenanceTask != null) {
            maintenanceTask.cancel();
            maintenanceTask = null;
        }
        for (UUID displayId : new ArrayList<>(hologramsByBlock.values())) {
            Entity entity = Bukkit.getEntity(displayId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        hologramsByBlock.clear();
        pylonsByWorld.clear();
    }

    public ItemStack createPylonItem() {
        ItemStack item = new ItemStack(PYLON_BLOCK_TYPE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.UNCOMMON, "Agricultural Pylon"));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            PYLON_BLOCK_TYPE,
            CustomLoreUtil.Rarity.UNCOMMON.label(),
            "UTILITY STATION",
            java.util.List.of(
                "<gray>Stabilizes soil around a farm plot.</gray>",
                "<gray>Prevents farmland trampling nearby.</gray>"
            ),
            java.util.List.of(
                CustomLoreUtil.section(
                    "Aura",
                    "Rootguard Field",
                    "<gray>Place it near crops to protect farmland in a <white>" + protectedDiameterText() + "</white> area.</gray>",
                    "<gray>Works against players, mobs, and accidental jump trampling.</gray>"
                ),
                CustomLoreUtil.section(
                    "Safety",
                    "No Storage",
                    "<gray>This is a station, not a chest.</gray>",
                    "<gray>Players and hoppers cannot put items inside it.</gray>"
                )
            )
        ));
        meta.getPersistentDataContainer().set(keyPylonItem, PersistentDataType.STRING, ITEM_ID);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isPylonItem(ItemStack item) {
        if (item == null || item.getType() != PYLON_BLOCK_TYPE) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && ITEM_ID.equals(meta.getPersistentDataContainer().get(keyPylonItem, PersistentDataType.STRING));
    }

    public Map<Material, Integer> recipeIngredients() {
        Map<Material, Integer> ingredients = new LinkedHashMap<>();
        ingredients.put(Material.BONE_MEAL, 4);
        ingredients.put(Material.WHEAT, 2);
        ingredients.put(Material.COPPER_INGOT, 2);
        ingredients.put(Material.LANTERN, 1);
        return ingredients;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipe(recipeKey);
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
            player.sendMessage(MessageUtil.warn("Use plain vanilla ingredients for Agricultural Pylon recipes."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!isPylonItem(event.getItemInHand())) {
            return;
        }
        if (!enabled()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.warn("Agricultural Pylons are disabled."));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> setupPlacedPylon(event.getBlockPlaced()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isPylonBlock(block)) {
            return;
        }

        event.setDropItems(false);
        uncachePylon(block);
        removeHologram(block);
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), createPylonItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTrample(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL || event.getClickedBlock() == null) {
            return;
        }
        if (event.getClickedBlock().getType() == Material.FARMLAND && isProtected(event.getClickedBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTrample(EntityInteractEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.FARMLAND && isProtected(block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFarmlandChange(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.FARMLAND && event.getTo() == Material.DIRT && isProtected(block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (isPylonInventory(event.getInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (isPylonInventory(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isPylonInventory(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (isPylonInventory(event.getSource()) || isPylonInventory(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isPylonBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isPylonBlock);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        syncChunkPylons(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        removeChunkPylonCache(event.getChunk());
        removeChunkHolograms(event.getChunk());
    }

    private void registerRecipe() {
        Bukkit.removeRecipe(recipeKey);
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createPylonItem());
        recipe.shape("BWB", "CLC", "BWB");
        recipe.setIngredient('B', Material.BONE_MEAL);
        recipe.setIngredient('W', Material.WHEAT);
        recipe.setIngredient('C', Material.COPPER_INGOT);
        recipe.setIngredient('L', Material.LANTERN);
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

    private void setupPlacedPylon(Block block) {
        if (block.getType() != PYLON_BLOCK_TYPE) {
            return;
        }

        BlockState state = block.getState();
        if (state instanceof Container container) {
            container.customName(MM.deserialize("<gradient:#74ee15:#22c55e><bold>Agricultural Pylon</bold></gradient>"));
            container.getInventory().clear();
        }
        if (state instanceof TileState tile) {
            tile.getPersistentDataContainer().set(keyPylonBlock, PersistentDataType.STRING, ITEM_ID);
            tile.update(true, false);
        }

        cachePylon(block);
        ensureHologram(block);
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_ROOTED_DIRT_PLACE, 0.8f, 1.15f);
        block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 1.1, 0.5), 24, 0.45, 0.25, 0.45, 0.02);
    }

    private void syncLoadedPylons() {
        pylonsByWorld.clear();
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                syncChunkPylons(chunk);
            }
        }
    }

    private void syncChunkPylons(Chunk chunk) {
        removeChunkPylonCache(chunk);
        removeStaleChunkHolograms(chunk);
        for (BlockState state : chunk.getTileEntities()) {
            Block block = state.getBlock();
            if (!isPylonBlock(block)) {
                continue;
            }
            cachePylon(block);
            ensureHologram(block);
        }
    }

    private boolean isProtected(Block block) {
        if (!enabled() || block == null || block.getWorld() == null) {
            return false;
        }
        Set<BlockKey> pylons = pylonsByWorld.get(block.getWorld().getUID());
        if (pylons == null || pylons.isEmpty()) {
            return false;
        }
        int horizontalRadius = horizontalRadius();
        int verticalRadius = verticalRadius();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        for (BlockKey key : pylons) {
            if (Math.abs(key.x - x) <= horizontalRadius
                && Math.abs(key.y - y) <= verticalRadius
                && Math.abs(key.z - z) <= horizontalRadius) {
                return true;
            }
        }
        return false;
    }

    private void cachePylon(Block block) {
        if (block == null || block.getWorld() == null) {
            return;
        }
        pylonsByWorld
            .computeIfAbsent(block.getWorld().getUID(), ignored -> ConcurrentHashMap.newKeySet())
            .add(BlockKey.from(block));
    }

    private void uncachePylon(Block block) {
        if (block == null || block.getWorld() == null) {
            return;
        }
        Set<BlockKey> pylons = pylonsByWorld.get(block.getWorld().getUID());
        if (pylons != null) {
            pylons.remove(BlockKey.from(block));
        }
    }

    private void removeChunkPylonCache(Chunk chunk) {
        Set<BlockKey> pylons = pylonsByWorld.get(chunk.getWorld().getUID());
        if (pylons == null) {
            return;
        }
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        pylons.removeIf(key -> (key.x >> 4) == chunkX && (key.z >> 4) == chunkZ);
    }

    private boolean isPylonBlock(Block block) {
        if (block == null || block.getType() != PYLON_BLOCK_TYPE) {
            return false;
        }
        BlockState state = block.getState();
        return state instanceof TileState tile
            && ITEM_ID.equals(tile.getPersistentDataContainer().get(keyPylonBlock, PersistentDataType.STRING));
    }

    private boolean isPylonInventory(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        InventoryHolder holder = inventory.getHolder(false);
        return holder instanceof Container container && isPylonBlock(container.getBlock());
    }

    private void ensureHologram(Block block) {
        if (!isPylonBlock(block)) {
            return;
        }
        String blockKey = blockKey(block);
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
            display.setShadowed(false);
            display.setLineWidth(180);
            display.setTextOpacity((byte) 255);
            display.setBackgroundColor(Color.fromARGB(88, 8, 18, 8));
            VisualRangeUtil.applyHologramRange(display);
            PersistentDataContainer pdc = display.getPersistentDataContainer();
            pdc.set(keyPylonHologram, PersistentDataType.BYTE, (byte) 1);
            pdc.set(keyPylonHologramBlock, PersistentDataType.STRING, blockKey);
            hologramsByBlock.put(blockKey, display.getUniqueId());
        });
    }

    private Component hologramText() {
        return Component.empty()
            .append(MM.deserialize("<gradient:#74ee15:#22c55e><bold>Agricultural Pylon</bold></gradient>"))
            .append(Component.newline())
            .append(MM.deserialize("<gray>Protects farmland in <white>" + protectedDiameterText() + "</white></gray>"));
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
            if (isPylonHologramFor(entity, blockKey)) {
                entity.remove();
            }
        }
    }

    private void removeChunkHolograms(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (isPylonHologram(entity)) {
                entity.remove();
            }
        }
    }

    private void removeStaleChunkHolograms(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (!isPylonHologram(entity)) {
                continue;
            }
            String blockKey = entity.getPersistentDataContainer().get(keyPylonHologramBlock, PersistentDataType.STRING);
            Location location = blockKey == null ? null : locationFromBlockKey(blockKey);
            if (location == null || !isPylonBlock(location.getBlock())) {
                entity.remove();
                if (blockKey != null) {
                    hologramsByBlock.remove(blockKey, entity.getUniqueId());
                }
            }
        }
    }

    private boolean isPylonHologram(Entity entity) {
        return entity instanceof TextDisplay
            && entity.getPersistentDataContainer().has(keyPylonHologram, PersistentDataType.BYTE);
    }

    private boolean isPylonHologramFor(Entity entity, String blockKey) {
        if (!isPylonHologram(entity)) {
            return false;
        }
        String stored = entity.getPersistentDataContainer().get(keyPylonHologramBlock, PersistentDataType.STRING);
        return blockKey.equals(stored);
    }

    private String blockKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private Location locationFromBlockKey(String blockKey) {
        if (blockKey == null || blockKey.isBlank()) {
            return null;
        }
        String[] parts = blockKey.split(":");
        if (parts.length != 4) {
            return null;
        }
        try {
            World world = Bukkit.getWorld(UUID.fromString(parts[0]));
            if (world == null) {
                return null;
            }
            return new Location(
                world,
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3])
            );
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean enabled() {
        return plugin.getConfigManager() == null || plugin.getConfigManager().agriculturalPylonEnabled;
    }

    private int horizontalRadius() {
        return plugin.getConfigManager() == null
            ? 5
            : Math.max(1, plugin.getConfigManager().agriculturalPylonHorizontalRadius);
    }

    private int verticalRadius() {
        return plugin.getConfigManager() == null
            ? 5
            : Math.max(1, plugin.getConfigManager().agriculturalPylonVerticalRadius);
    }

    private String protectedDiameterText() {
        int horizontal = horizontalRadius() * 2;
        int vertical = verticalRadius() * 2;
        return horizontal + "x" + vertical + "x" + horizontal;
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {
        private static BlockKey from(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }
}
