package me.rique.smpcore.death;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DeathChestListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final BlockFace[] HORIZONTAL_FACES = {
        BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };
    private static final long CLEANUP_INTERVAL_TICKS = 20L * 60L;
    private static final long NOTE_RETRY_INTERVAL_TICKS = 20L * 10L;

    private final SMPCore plugin;
    private final NamespacedKey keyDeathChestId;
    private final NamespacedKey keyDeathChestExpiresAt;
    private final Set<UUID> pendingChunkScan = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, List<ItemStack>> pendingRespawnNotes = new ConcurrentHashMap<>();

    public DeathChestListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyDeathChestId = new NamespacedKey(plugin, "death_chest_id");
        this.keyDeathChestExpiresAt = new NamespacedKey(plugin, "death_chest_expires_at");
        Bukkit.getScheduler().runTask(plugin, this::cleanupLoadedDeathChests);
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupLoadedDeathChests, CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);
        Bukkit.getScheduler().runTaskTimer(plugin, this::retryPendingDeathChestNotes, NOTE_RETRY_INTERVAL_TICKS, NOTE_RETRY_INTERVAL_TICKS);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getConfigManager().deathChestEnabled) {
            return;
        }
        if (event.getKeepInventory()) {
            return;
        }

        Player player = event.getPlayer();
        if (plugin.getConfigManager().deathChestDisableInPlayerCombat
            && plugin.getCombatLogListener() != null
            && plugin.getCombatLogListener().isInPlayerCombat(player)) {
            return;
        }

        List<ItemStack> drops = cloneDrops(event.getDrops());
        if (drops.isEmpty()) {
            return;
        }

        DeathChestPlacement placement = findPlacement(player.getLocation(), drops.size());
        if (placement == null) {
            maybeNotifyNoSpace(player);
            return;
        }

        long expiresAt = System.currentTimeMillis() + (plugin.getConfigManager().deathChestLifetimeMinutes * 60_000L);
        String chestId = UUID.randomUUID().toString();
        Component chestName = MM.deserialize(applyPlaceholders(
            plugin.getConfigManager().deathChestChestName,
            player.getName(),
            placement.primary().getLocation(),
            plugin.getConfigManager().deathChestLifetimeMinutes
        ));

        DeathChestStorage storage = createDeathChestStorage(placement, chestId, expiresAt, chestName);
        if (storage == null) {
            clearDeathChestBlocks(placement.blocks());
            maybeNotifyNoSpace(player);
            return;
        }

        List<ItemStack> overflow = storeItems(storage, drops);
        auditDeathChest(player, storage);
        event.getDrops().clear();
        if (!overflow.isEmpty() && !plugin.getConfigManager().deathChestDropOverflowItems) {
            event.getDrops().addAll(cloneDrops(overflow));
        } else if (!overflow.isEmpty()) {
            dropItems(placement.primary().getLocation().add(0.5, 0.5, 0.5), overflow);
        }

        maybeSendChestMessage(player, placement.primary().getLocation(), plugin.getConfigManager().deathChestLifetimeMinutes);
        queueDeathChestNote(player, placement.primary().getLocation(), plugin.getConfigManager().deathChestLifetimeMinutes);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> attemptPendingNoteDelivery(event.getPlayer(), true));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> attemptPendingNoteDelivery(event.getPlayer(), true));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!plugin.getConfigManager().deathChestEnabled || !plugin.getConfigManager().deathChestRemoveWhenEmpty) {
            return;
        }
        if (event.getInventory().getViewers().size() > 1) {
            return;
        }
        if (!isEmpty(event.getInventory())) {
            return;
        }

        String deathChestId = deathChestId(event.getInventory().getHolder());
        if (deathChestId == null) {
            return;
        }

        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.getConfigManager().deathChestEnabled || !plugin.getConfigManager().deathChestRemoveWhenEmpty) {
                return;
            }
            if (inventory.getViewers().size() > 0 || !isEmpty(inventory)) {
                return;
            }
            if (!deathChestId.equals(deathChestId(holder))) {
                return;
            }
            removeDeathChest(holder, deathChestId, false);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!plugin.getConfigManager().deathChestEnabled) {
            return;
        }

        UUID chunkId = chunkId(event.getChunk());
        if (!pendingChunkScan.add(chunkId)) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                cleanupDeathChestsInChunk(event.getChunk(), System.currentTimeMillis());
            } finally {
                pendingChunkScan.remove(chunkId);
            }
        });
    }

    private void deliverDeathChestNotes(Player player, List<ItemStack> notes, boolean notifyStillWaiting) {
        if (player == null || !player.isOnline()) {
            return;
        }

        List<ItemStack> undelivered = new ArrayList<>();
        for (ItemStack note : notes) {
            var leftovers = player.getInventory().addItem(note);
            if (leftovers.isEmpty()) {
                continue;
            }
            if (plugin.getConfigManager().deathChestNoteDropIfInventoryFull) {
                dropItems(player.getLocation(), new ArrayList<>(leftovers.values()));
                player.sendMessage(MessageUtil.warn("Your death chest note was dropped because your inventory was full."));
            } else {
                undelivered.addAll(leftovers.values());
            }
        }

        if (!undelivered.isEmpty()) {
            pendingRespawnNotes.merge(
                player.getUniqueId(),
                undelivered,
                (existing, added) -> {
                    List<ItemStack> merged = new ArrayList<>(existing);
                    merged.addAll(added);
                    return merged;
                }
            );
            if (notifyStillWaiting) {
                player.sendMessage(MessageUtil.warn("Your inventory was full, so your death chest note is still waiting."));
            }
        }
    }

    private void attemptPendingNoteDelivery(Player player, boolean notifyStillWaiting) {
        if (player == null || !player.isOnline()) {
            return;
        }

        List<ItemStack> notes = pendingRespawnNotes.remove(player.getUniqueId());
        if (notes == null || notes.isEmpty()) {
            return;
        }

        deliverDeathChestNotes(player, notes, notifyStillWaiting);
    }

    private void retryPendingDeathChestNotes() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!pendingRespawnNotes.containsKey(player.getUniqueId())) {
                continue;
            }
            attemptPendingNoteDelivery(player, false);
        }
    }

    private void maybeSendChestMessage(Player player, Location location, int lifetimeMinutes) {
        if (!plugin.getConfigManager().deathChestNotifyChat) {
            return;
        }
        String message = plugin.getConfigManager().deathChestChatMessage;
        if (message == null || message.isBlank()) {
            return;
        }
        player.sendMessage(MessageUtil.prefixedRaw(applyPlaceholders(message, player.getName(), location, lifetimeMinutes)));
    }

    private void maybeNotifyNoSpace(Player player) {
        if (!plugin.getConfigManager().deathChestNotifyWhenNoSpace) {
            return;
        }
        String message = plugin.getConfigManager().deathChestNoSpaceMessage;
        if (message == null || message.isBlank()) {
            return;
        }
        player.sendMessage(MessageUtil.prefixedRaw(message));
    }

    private void queueDeathChestNote(Player player, Location location, int lifetimeMinutes) {
        if (!plugin.getConfigManager().deathChestNoteEnabled) {
            return;
        }

        pendingRespawnNotes.compute(player.getUniqueId(), (ignored, existing) -> {
            List<ItemStack> notes = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            notes.add(createDeathChestNote(player.getName(), location, lifetimeMinutes));
            return notes;
        });
    }

    private ItemStack createDeathChestNote(String playerName, Location location, int lifetimeMinutes) {
        ItemStack note = new ItemStack(Material.PAPER);
        ItemMeta meta = note.getItemMeta();
        if (meta == null) {
            return note;
        }

        meta.displayName(MM.deserialize(applyPlaceholders(
            plugin.getConfigManager().deathChestNoteTitle,
            playerName,
            location,
            lifetimeMinutes
        )));

        List<Component> lore = new ArrayList<>();
        for (String line : plugin.getConfigManager().deathChestNoteLore) {
            lore.add(MM.deserialize(applyPlaceholders(line, playerName, location, lifetimeMinutes)));
        }
        meta.lore(lore);
        note.setItemMeta(meta);
        return note;
    }

    private DeathChestStorage createDeathChestStorage(DeathChestPlacement placement, String chestId, long expiresAt, Component chestName) {
        if (!placeChestBlock(placement.primary(), placement.facing(), placement.primaryType(), chestId, expiresAt, chestName)) {
            return null;
        }

        if (placement.isDoubleChest()
            && !placeChestBlock(placement.secondary(), placement.facing(), placement.secondaryType(), chestId, expiresAt, chestName)) {
            clearDeathChestBlocks(placement.blocks());
            return null;
        }

        if (!(placement.primary().getState() instanceof Chest primaryChest)) {
            return null;
        }
        Inventory primaryInventory = primaryChest.getBlockInventory();
        Inventory secondaryInventory = null;
        if (placement.isDoubleChest()) {
            if (!(placement.secondary().getState() instanceof Chest secondaryChest)) {
                return null;
            }
            secondaryInventory = secondaryChest.getBlockInventory();
        }
        return new DeathChestStorage(primaryInventory, secondaryInventory);
    }

    private boolean placeChestBlock(
        Block block,
        BlockFace facing,
        org.bukkit.block.data.type.Chest.Type type,
        String chestId,
        long expiresAt,
        Component chestName
    ) {
        block.setType(Material.CHEST, false);
        org.bukkit.block.data.type.Chest chestData = (org.bukkit.block.data.type.Chest) Bukkit.createBlockData(Material.CHEST);
        chestData.setFacing(facing);
        chestData.setType(type);
        block.setBlockData(chestData, false);

        if (!(block.getState() instanceof Chest chest)) {
            return false;
        }

        chest.customName(chestName);
        PersistentDataContainer pdc = chest.getPersistentDataContainer();
        pdc.set(keyDeathChestId, PersistentDataType.STRING, chestId);
        pdc.set(keyDeathChestExpiresAt, PersistentDataType.LONG, expiresAt);
        chest.update(true, false);
        return true;
    }

    private DeathChestPlacement findPlacement(Location deathLocation, int itemCount) {
        World world = deathLocation.getWorld();
        if (world == null) {
            return null;
        }

        boolean needsLargeChest = plugin.getConfigManager().deathChestLargeChestEnabled && itemCount > 27;
        int baseX = deathLocation.getBlockX();
        int baseY = deathLocation.getBlockY();
        int baseZ = deathLocation.getBlockZ();

        for (int yOffset : orderedOffsets(plugin.getConfigManager().deathChestVerticalSearchRadius)) {
            int y = baseY + yOffset;
            if (y < world.getMinHeight() || y >= world.getMaxHeight() - 1) {
                continue;
            }

            for (int[] horizontalOffset : orderedHorizontalOffsets(plugin.getConfigManager().deathChestSearchRadius)) {
                Block block = world.getBlockAt(baseX + horizontalOffset[0], y, baseZ + horizontalOffset[1]);
                if (needsLargeChest) {
                    DeathChestPlacement largePlacement = findLargeChestPlacement(block);
                    if (largePlacement != null) {
                        return largePlacement;
                    }
                }

                DeathChestPlacement singlePlacement = findSingleChestPlacement(block);
                if (singlePlacement != null) {
                    return singlePlacement;
                }
            }
        }
        return null;
    }

    private DeathChestPlacement findSingleChestPlacement(Block block) {
        if (!canPlaceChest(block) || hasChestNeighborConflict(block, null)) {
            return null;
        }
        return new DeathChestPlacement(
            block,
            null,
            BlockFace.NORTH,
            org.bukkit.block.data.type.Chest.Type.SINGLE,
            org.bukkit.block.data.type.Chest.Type.SINGLE
        );
    }

    private DeathChestPlacement findLargeChestPlacement(Block block) {
        if (!canPlaceChest(block)) {
            return null;
        }

        for (BlockFace face : HORIZONTAL_FACES) {
            Block other = block.getRelative(face);
            if (!canPlaceChest(other)) {
                continue;
            }
            if (hasChestNeighborConflict(block, other) || hasChestNeighborConflict(other, block)) {
                continue;
            }

            BlockFace facing = (face == BlockFace.EAST || face == BlockFace.WEST) ? BlockFace.NORTH : BlockFace.EAST;
            boolean secondaryIsRightSide = face == rightOf(facing);
            return new DeathChestPlacement(
                block,
                other,
                facing,
                secondaryIsRightSide ? org.bukkit.block.data.type.Chest.Type.LEFT : org.bukkit.block.data.type.Chest.Type.RIGHT,
                secondaryIsRightSide ? org.bukkit.block.data.type.Chest.Type.RIGHT : org.bukkit.block.data.type.Chest.Type.LEFT
            );
        }
        return null;
    }

    private boolean canPlaceChest(Block block) {
        if (block == null) {
            return false;
        }
        if (!block.isReplaceable()) {
            return false;
        }
        if (!plugin.getConfigManager().deathChestAllowWaterPlacement
            && (block.getType() == Material.WATER || block.getType() == Material.LAVA)) {
            return false;
        }
        if (plugin.getConfigManager().deathChestRequireSupportingBlock && !hasSupportingBlock(block)) {
            return false;
        }
        if (plugin.getConfigManager().deathChestRequireClearAbove && block.getRelative(BlockFace.UP).isSolid()) {
            return false;
        }
        return true;
    }

    private boolean hasSupportingBlock(Block block) {
        Block below = block.getRelative(BlockFace.DOWN);
        return !below.isReplaceable() && !below.isPassable();
    }

    private boolean hasChestNeighborConflict(Block block, Block allowedNeighbor) {
        for (BlockFace face : HORIZONTAL_FACES) {
            Block neighbor = block.getRelative(face);
            if (allowedNeighbor != null && neighbor.equals(allowedNeighbor)) {
                continue;
            }
            Material material = neighbor.getType();
            if (material == Material.CHEST || material == Material.TRAPPED_CHEST) {
                return true;
            }
        }
        return false;
    }

    private List<ItemStack> cloneDrops(List<ItemStack> drops) {
        List<ItemStack> cloned = new ArrayList<>();
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType() == Material.AIR || drop.getAmount() <= 0) {
                continue;
            }
            cloned.add(drop.clone());
        }
        return cloned;
    }

    private List<ItemStack> storeItems(DeathChestStorage storage, List<ItemStack> drops) {
        List<ItemStack> overflow = new ArrayList<>();
        List<Inventory> inventories = storage.inventories();
        for (ItemStack drop : drops) {
            List<ItemStack> remaining = List.of(drop.clone());
            for (Inventory inventory : inventories) {
                if (remaining.isEmpty()) {
                    break;
                }
                remaining = addToInventory(inventory, remaining);
            }
            overflow.addAll(cloneDrops(remaining));
        }
        return overflow;
    }

    private void auditDeathChest(Player player, DeathChestStorage storage) {
        if (player == null || storage == null || plugin.getItemAuditManager() == null) {
            return;
        }

        int chestIndex = 1;
        for (Inventory inventory : storage.inventories()) {
            plugin.getItemAuditManager().auditSharedInventory(
                player,
                inventory,
                "death_chest:" + player.getName() + ":" + chestIndex
            );
            chestIndex++;
        }
    }

    private List<ItemStack> addToInventory(Inventory inventory, List<ItemStack> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        var leftovers = inventory.addItem(items.toArray(ItemStack[]::new));
        return leftovers.isEmpty() ? List.of() : new ArrayList<>(leftovers.values());
    }

    private void dropItems(Location location, List<ItemStack> items) {
        if (location.getWorld() == null) {
            return;
        }
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
                continue;
            }
            location.getWorld().dropItemNaturally(location, item.clone());
        }
    }

    private void cleanupLoadedDeathChests() {
        if (!plugin.getConfigManager().deathChestEnabled) {
            return;
        }
        long now = System.currentTimeMillis();
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                cleanupDeathChestsInChunk(chunk, now);
            }
        }
    }

    private void cleanupDeathChestsInChunk(Chunk chunk, long now) {
        Set<String> removedChestIds = new HashSet<>();
        for (org.bukkit.block.BlockState state : chunk.getTileEntities()) {
            if (!(state instanceof Chest chest)) {
                continue;
            }
            String chestId = deathChestIdFromState(chest);
            if (chestId == null || removedChestIds.contains(chestId)) {
                continue;
            }
            if (!isExpired(chest, now)) {
                continue;
            }
            if (!chest.getInventory().getViewers().isEmpty()) {
                continue;
            }
            removeDeathChest(chest, chestId, true);
            removedChestIds.add(chestId);
        }
    }

    private boolean isExpired(Chest chest, long now) {
        long expiresAt = chest.getPersistentDataContainer().getOrDefault(keyDeathChestExpiresAt, PersistentDataType.LONG, 0L);
        return expiresAt > 0L && expiresAt <= now;
    }

    private void removeDeathChest(InventoryHolder holder, String chestId, boolean clearInventory) {
        for (Block block : deathChestBlocks(holder, chestId)) {
            if (block.getState() instanceof Chest chest && clearInventory) {
                chest.getBlockInventory().clear();
                chest.update(true, false);
            }
            block.setType(Material.AIR, false);
        }
    }

    private List<Block> deathChestBlocks(InventoryHolder holder, String chestId) {
        List<Block> blocks = new ArrayList<>();
        if (holder instanceof Chest chest) {
            addDeathChestBlocks(blocks, chest.getBlock(), chestId);
            return blocks;
        }
        if (holder instanceof DoubleChest doubleChest) {
            InventoryHolder left = doubleChest.getLeftSide();
            InventoryHolder right = doubleChest.getRightSide();
            if (left instanceof Chest leftChest) {
                addDeathChestBlocks(blocks, leftChest.getBlock(), chestId);
            }
            if (right instanceof Chest rightChest) {
                addDeathChestBlocks(blocks, rightChest.getBlock(), chestId);
            }
        }
        return blocks;
    }

    private void addDeathChestBlocks(List<Block> blocks, Block origin, String chestId) {
        if (origin == null || blocks.contains(origin)) {
            return;
        }
        if (!hasDeathChestId(origin, chestId)) {
            return;
        }
        blocks.add(origin);
        for (BlockFace face : HORIZONTAL_FACES) {
            Block neighbor = origin.getRelative(face);
            if (!blocks.contains(neighbor) && hasDeathChestId(neighbor, chestId)) {
                blocks.add(neighbor);
            }
        }
    }

    private String deathChestId(InventoryHolder holder) {
        if (holder instanceof Chest chest) {
            return deathChestIdFromState(chest);
        }
        if (holder instanceof DoubleChest doubleChest) {
            InventoryHolder left = doubleChest.getLeftSide();
            if (left instanceof Chest leftChest) {
                String id = deathChestIdFromState(leftChest);
                if (id != null) {
                    return id;
                }
            }
            InventoryHolder right = doubleChest.getRightSide();
            if (right instanceof Chest rightChest) {
                return deathChestIdFromState(rightChest);
            }
        }
        return null;
    }

    private String deathChestIdFromState(TileState tileState) {
        return tileState.getPersistentDataContainer().get(keyDeathChestId, PersistentDataType.STRING);
    }

    private boolean hasDeathChestId(Block block, String expectedId) {
        if (!(block.getState() instanceof TileState tileState)) {
            return false;
        }
        String id = deathChestIdFromState(tileState);
        return id != null && id.equals(expectedId);
    }

    private boolean isEmpty(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                return false;
            }
        }
        return true;
    }

    private void clearDeathChestBlocks(List<Block> blocks) {
        for (Block block : blocks) {
            if (block == null || block.getType() != Material.CHEST) {
                continue;
            }
            block.setType(Material.AIR, false);
        }
    }

    private int[] orderedOffsets(int radius) {
        int[] offsets = new int[(radius * 2) + 1];
        offsets[0] = 0;
        int index = 1;
        for (int step = 1; step <= radius; step++) {
            offsets[index++] = step;
            offsets[index++] = -step;
        }
        return offsets;
    }

    private List<int[]> orderedHorizontalOffsets(int radius) {
        List<int[]> offsets = new ArrayList<>();
        for (int distance = 0; distance <= radius; distance++) {
            for (int x = -distance; x <= distance; x++) {
                for (int z = -distance; z <= distance; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != distance) {
                        continue;
                    }
                    offsets.add(new int[] {x, z});
                }
            }
        }
        return offsets;
    }

    private BlockFace rightOf(BlockFace facing) {
        return switch (facing) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }

    private UUID chunkId(Chunk chunk) {
        long packed = (((long) chunk.getX()) << 32) ^ (chunk.getZ() & 0xffffffffL);
        return new UUID(chunk.getWorld().getUID().getMostSignificantBits() ^ packed, chunk.getWorld().getUID().getLeastSignificantBits());
    }

    private String applyPlaceholders(String template, String playerName, Location location, int lifetimeMinutes) {
        if (template == null) {
            return "";
        }
        String worldName = location.getWorld() == null ? "world" : location.getWorld().getName();
        return template
            .replace("{player}", playerName)
            .replace("{world}", worldName)
            .replace("{x}", Integer.toString(location.getBlockX()))
            .replace("{y}", Integer.toString(location.getBlockY()))
            .replace("{z}", Integer.toString(location.getBlockZ()))
            .replace("{minutes}", Integer.toString(lifetimeMinutes));
    }

    private record DeathChestPlacement(
        Block primary,
        Block secondary,
        BlockFace facing,
        org.bukkit.block.data.type.Chest.Type primaryType,
        org.bukkit.block.data.type.Chest.Type secondaryType
    ) {
        private boolean isDoubleChest() {
            return secondary != null;
        }

        private List<Block> blocks() {
            List<Block> blocks = new ArrayList<>();
            blocks.add(primary);
            if (secondary != null) {
                blocks.add(secondary);
            }
            return blocks;
        }
    }

    private record DeathChestStorage(Inventory primary, Inventory secondary) {
        private List<Inventory> inventories() {
            List<Inventory> inventories = new ArrayList<>();
            inventories.add(primary);
            if (secondary != null) {
                inventories.add(secondary);
            }
            return inventories;
        }
    }
}
