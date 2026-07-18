package me.rique.smpcore.item;

import com.destroystokyo.paper.event.entity.ExperienceOrbMergeEvent;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.InventoryRecipeUtil;
import me.rique.smpcore.util.ItemModelUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
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
import org.bukkit.block.TileState;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class XpLecternListener implements Listener {

    public static final String ITEM_ID = "xp_lectern";

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final long MAX_STORED_XP = Integer.MAX_VALUE;
    private static final int MENU_SIZE = 27;
    static final int XP_PER_BOTTLE = 10;

    private final SMPCore plugin;
    private final NamespacedKey keyLecternItem;
    private final NamespacedKey keyLecternBlock;
    private final NamespacedKey keyStoredXp;
    private final NamespacedKey keyMenuAction;
    private final NamespacedKey keyMenuAmount;
    private final NamespacedKey keyHologram;
    private final NamespacedKey keyHologramBlock;
    private final NamespacedKey keyFilledBottle;
    private final NamespacedKey keyFilledBottleProjectile;
    private final NamespacedKey keyFilledBottleOrb;
    private final NamespacedKey recipeKey;
    private final Map<String, UUID> hologramsByBlock = new ConcurrentHashMap<>();
    private final Set<UUID> lecternBottleXpPickups = ConcurrentHashMap.newKeySet();
    private BukkitTask maintenanceTask;

    public XpLecternListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyLecternItem = new NamespacedKey(plugin, ITEM_ID);
        this.keyLecternBlock = new NamespacedKey(plugin, "xp_lectern_block");
        this.keyStoredXp = new NamespacedKey(plugin, "xp_lectern_stored_xp");
        this.keyMenuAction = new NamespacedKey(plugin, "xp_lectern_menu_action");
        this.keyMenuAmount = new NamespacedKey(plugin, "xp_lectern_menu_amount");
        this.keyHologram = new NamespacedKey(plugin, "xp_lectern_hologram");
        this.keyHologramBlock = new NamespacedKey(plugin, "xp_lectern_hologram_block");
        this.keyFilledBottle = new NamespacedKey("smpcore_runtime", "lectern_filled_bottle");
        this.keyFilledBottleProjectile = new NamespacedKey(plugin, "lectern_filled_bottle_projectile");
        this.keyFilledBottleOrb = new NamespacedKey(plugin, "lectern_filled_bottle_orb");
        this.recipeKey = new NamespacedKey(plugin, ITEM_ID);
    }

    public void start() {
        registerRecipe();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.discoverRecipe(recipeKey);
            }
            syncLoadedLecterns();
        });
        maintenanceTask = Bukkit.getScheduler().runTaskTimer(plugin, this::syncLoadedLecterns, 100L, 200L);
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
        lecternBottleXpPickups.clear();
    }

    public ItemStack createLecternItem() {
        return createLecternItem(0L);
    }

    public ItemStack createLecternItem(long storedXp) {
        long safeStoredXp = clampStoredXp(storedXp);
        ItemStack item = new ItemStack(Material.LECTERN);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        List<String> topLines = new ArrayList<>();
        topLines.add("<gray>Stores XP safely as exact experience points.</gray>");
        topLines.add("<gray>Players deposit and withdraw with simple level buttons.</gray>");
        if (safeStoredXp > 0L) {
            topLines.add("<gray>Carried Storage: <white>" + levelSummary(safeStoredXp) + "</white></gray>");
        }

        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.RARE, "XP Lectern"));
        ItemModelUtil.apply(meta, ITEM_ID);
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.LECTERN,
            CustomLoreUtil.Rarity.RARE.label(),
            "UTILITY STATION",
            topLines,
            List.of(
                CustomLoreUtil.section(
                    "Use",
                    "Level Banking",
                    "<gray>Place it, then right-click to open the XP menu.</gray>",
                    "<gray>Deposit <white>1</white>, <white>5</white>, <white>10</white>, or all levels.</gray>",
                    "<gray>Withdraw the same way whenever you need the XP back.</gray>",
                    "<gray>Stored XP can also fill plain glass bottles.</gray>"
                ),
                CustomLoreUtil.section(
                    "Safety",
                    "Exact Storage",
                    "<gray>The lectern stores raw XP internally to avoid level-curve dupes.</gray>",
                    "<gray>Break it to move it; stored XP stays inside the dropped item.</gray>"
                )
            )
        ));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyLecternItem, PersistentDataType.STRING, ITEM_ID);
        if (safeStoredXp > 0L) {
            pdc.set(keyStoredXp, PersistentDataType.LONG, safeStoredXp);
        } else {
            pdc.remove(keyStoredXp);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isLecternItem(ItemStack item) {
        if (item == null || item.getType() != Material.LECTERN) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && ITEM_ID.equals(meta.getPersistentDataContainer().get(keyLecternItem, PersistentDataType.STRING));
    }

    public Map<Material, Integer> recipeIngredients() {
        return Map.of(
            Material.LECTERN, 1,
            Material.BOOK, 2,
            Material.EXPERIENCE_BOTTLE, 1,
            Material.REDSTONE, 1
        );
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
            player.sendMessage(MessageUtil.warn("Use plain vanilla ingredients for XP Lectern recipes."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack placedItem = event.getItemInHand();
        if (!isLecternItem(placedItem)) {
            return;
        }
        long storedXp = storedXp(placedItem);
        if (storedXp > 0L && placedItem.getAmount() > 1) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.warn("Split stored XP Lecterns before placing them."));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> setupPlacedLectern(event.getBlockPlaced(), storedXp));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isLecternBlock(block)) {
            return;
        }
        long storedXp = storedXp(block);
        event.setDropItems(false);
        removeHologram(block);
        Bukkit.getScheduler().runTask(plugin, () -> finishBreak(block, storedXp));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isLecternBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isLecternBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFilledBottleDispense(BlockDispenseEvent event) {
        if (isLecternFilledBottle(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFilledBottleLaunch(PlayerLaunchProjectileEvent event) {
        if (event.getProjectile() instanceof ThrownExpBottle && isLecternFilledBottle(event.getItemStack())) {
            event.getProjectile().getPersistentDataContainer().set(keyFilledBottleProjectile, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFilledBottleBreak(ExpBottleEvent event) {
        if (!event.getEntity().getPersistentDataContainer().has(keyFilledBottleProjectile, PersistentDataType.BYTE)) {
            return;
        }
        int experience = Math.max(0, event.getExperience());
        event.setExperience(0);
        if (experience <= 0) {
            return;
        }
        event.getEntity().getWorld().spawn(event.getEntity().getLocation(), ExperienceOrb.class, orb -> {
            orb.setExperience(experience);
            orb.getPersistentDataContainer().set(keyFilledBottleOrb, PersistentDataType.BYTE, (byte) 1);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFilledBottleOrbMerge(ExperienceOrbMergeEvent event) {
        if (isFilledBottleOrb(event.getMergeSource()) || isFilledBottleOrb(event.getMergeTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFilledBottleXpPickup(PlayerPickupExperienceEvent event) {
        if (!isFilledBottleOrb(event.getExperienceOrb())) {
            return;
        }
        UUID playerId = event.getPlayer().getUniqueId();
        lecternBottleXpPickups.add(playerId);
        Bukkit.getScheduler().runTask(plugin, () -> lecternBottleXpPickups.remove(playerId));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFilledBottleXpApplied(PlayerExpChangeEvent event) {
        if (event.getAmount() > 0) {
            lecternBottleXpPickups.remove(event.getPlayer().getUniqueId());
        }
    }

    public boolean isLecternBottleXpPickup(Player player) {
        return player != null && lecternBottleXpPickups.contains(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!isLecternBlock(block)) {
            return;
        }
        event.setCancelled(true);
        openMenu(event.getPlayer(), block);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof XpLecternMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory().getType() == InventoryType.PLAYER) {
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }

        Block block = blockFromKey(holder.blockKey());
        if (block == null || !isLecternBlock(block)) {
            player.closeInventory();
            player.sendMessage(MessageUtil.error("That XP Lectern is no longer available."));
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (!MenuItemUtil.isVisibleItem(clicked)) {
            return;
        }
        String action = menuAction(clicked);
        if (action == null) {
            return;
        }

        switch (action) {
            case "deposit" -> depositLevels(player, block, menuAmount(clicked), false);
            case "deposit_all" -> depositLevels(player, block, 0, true);
            case "withdraw" -> withdrawLevels(player, block, menuAmount(clicked), false);
            case "withdraw_all" -> withdrawLevels(player, block, 0, true);
            case "bottle" -> bottleExperience(player, block, menuAmount(clicked));
            case "close" -> {
                player.closeInventory();
                return;
            }
            default -> {
                return;
            }
        }
        renderMenu(event.getView().getTopInventory(), block, player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof XpLecternMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        syncChunkLecterns(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        removeChunkHolograms(event.getChunk());
    }

    private void registerRecipe() {
        Bukkit.removeRecipe(recipeKey);
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createLecternItem());
        recipe.shape(" E ", "BLB", " R ");
        recipe.setIngredient('E', Material.EXPERIENCE_BOTTLE);
        recipe.setIngredient('B', Material.BOOK);
        recipe.setIngredient('L', Material.LECTERN);
        recipe.setIngredient('R', Material.REDSTONE);
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

    private void setupPlacedLectern(Block block, long storedXp) {
        if (block == null || block.getType() != Material.LECTERN) {
            return;
        }
        BlockState state = block.getState();
        if (state instanceof TileState tile) {
            PersistentDataContainer pdc = tile.getPersistentDataContainer();
            pdc.set(keyLecternBlock, PersistentDataType.STRING, ITEM_ID);
            setStoredXp(tile, storedXp);
            tile.update(true, false);
        }
        ensureHologram(block);
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.85f, 1.35f);
        block.getWorld().spawnParticle(Particle.ENCHANT, block.getLocation().add(0.5, 1.15, 0.5), 28, 0.35, 0.28, 0.35, 0.02);
    }

    private void finishBreak(Block block, long storedXp) {
        if (isLecternBlock(block)) {
            ensureHologram(block);
            return;
        }

        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.75, 0.5), createLecternItem(storedXp));
    }

    private void openMenu(Player player, Block block) {
        Inventory inventory = Bukkit.createInventory(
            new XpLecternMenuHolder(blockKey(block)),
            MENU_SIZE,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#38bdf8:#a78bfa><bold>XP Lectern</bold></gradient>"), "XP Lectern")
        );
        renderMenu(inventory, block, player);
        player.openInventory(inventory);
    }

    private void renderMenu(Inventory inventory, Block block, Player player) {
        ItemStack filler = item(Material.BLUE_STAINED_GLASS_PANE, "<dark_gray> ", List.of(), null, 0);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        long storedXp = storedXp(block);
        int playerXp = totalExperience(player);
        inventory.setItem(4, item(
            Material.EXPERIENCE_BOTTLE,
            "<gradient:#38bdf8:#a78bfa><bold>Stored Experience</bold></gradient>",
            List.of(
                "<gray>Stored:</gray> <white>" + levelSummary(storedXp) + "</white>",
                "<gray>Your XP:</gray> <white>" + levelSummary(playerXp) + "</white>",
                "<dark_gray>Buttons use levels, storage uses exact XP points.</dark_gray>"
            ),
            null,
            0
        ));

        inventory.setItem(10, item(Material.LIME_DYE, "<green><bold>Deposit 1 Level</bold></green>", depositLore(player, 1), "deposit", 1));
        inventory.setItem(11, item(Material.LIME_DYE, "<green><bold>Deposit 5 Levels</bold></green>", depositLore(player, 5), "deposit", 5));
        inventory.setItem(12, item(Material.LIME_DYE, "<green><bold>Deposit 10 Levels</bold></green>", depositLore(player, 10), "deposit", 10));
        inventory.setItem(19, item(Material.EMERALD, "<green><bold>Deposit All XP</bold></green>", List.of("<gray>Stores all XP you currently have.</gray>"), "deposit_all", 0));

        inventory.setItem(14, item(Material.LIGHT_BLUE_DYE, "<aqua><bold>Withdraw 1 Level</bold></aqua>", withdrawLore(player, storedXp, 1), "withdraw", 1));
        inventory.setItem(15, item(Material.LIGHT_BLUE_DYE, "<aqua><bold>Withdraw 5 Levels</bold></aqua>", withdrawLore(player, storedXp, 5), "withdraw", 5));
        inventory.setItem(16, item(Material.LIGHT_BLUE_DYE, "<aqua><bold>Withdraw 10 Levels</bold></aqua>", withdrawLore(player, storedXp, 10), "withdraw", 10));
        inventory.setItem(25, item(Material.DIAMOND, "<aqua><bold>Withdraw All XP</bold></aqua>", List.of("<gray>Takes every stored XP point from this lectern.</gray>"), "withdraw_all", 0));

        inventory.setItem(21, item(Material.GLASS_BOTTLE, "<light_purple><bold>Fill 1 XP Bottle</bold></light_purple>", bottleLore(player, storedXp, 1), "bottle", 1));
        inventory.setItem(23, item(Material.EXPERIENCE_BOTTLE, "<light_purple><bold>Fill 8 XP Bottles</bold></light_purple>", bottleLore(player, storedXp, 8), "bottle", 8));
        inventory.setItem(22, item(Material.BARRIER, "<red><bold>Close</bold></red>", List.of("<gray>Leave the lectern menu.</gray>"), "close", 0));
    }

    private List<String> bottleLore(Player player, long storedXp, int bottles) {
        long xpCost = bottlingXpCost(bottles);
        InventoryRecipeUtil.Ingredient glass = InventoryRecipeUtil.plainMaterial(plugin, Material.GLASS_BOTTLE, bottles);
        int availableGlass = InventoryRecipeUtil.countIngredient(player, glass);
        List<String> lore = new ArrayList<>(List.of(
            "<gray>Uses <white>" + xpCost + " stored XP</white> and <white>" + bottles + " plain glass bottle" + (bottles == 1 ? "" : "s") + "</white>.</gray>",
            "<dark_gray>Bottling is intentionally lossy when bottles are thrown.</dark_gray>"
        ));
        if (storedXp < xpCost) {
            lore.add("<red>Not enough XP is stored.</red>");
        } else if (availableGlass < bottles) {
            lore.add("<red>You need " + bottles + " plain glass bottle" + (bottles == 1 ? "" : "s") + ".</red>");
        } else {
            lore.add("<yellow>Click to fill.</yellow>");
        }
        return lore;
    }

    private List<String> depositLore(Player player, int levels) {
        int available = depositAmountForLevels(player, levels);
        return List.of(
            "<gray>Stores roughly <white>" + levels + "</white> level" + (levels == 1 ? "" : "s") + " from your current XP.</gray>",
            available <= 0
                ? "<red>You do not have enough levels.</red>"
                : "<gray>Would store: <white>" + levelSummary(available) + "</white></gray>"
        );
    }

    private List<String> withdrawLore(Player player, long storedXp, int levels) {
        int wanted = withdrawAmountForLevels(player, levels);
        long given = Math.min(storedXp, wanted);
        return List.of(
            "<gray>Pulls enough XP for about <white>" + levels + "</white> level" + (levels == 1 ? "" : "s") + ".</gray>",
            given <= 0
                ? "<red>The lectern has no XP stored.</red>"
                : "<gray>Would withdraw: <white>" + levelSummary(given) + "</white></gray>"
        );
    }

    private void depositLevels(Player player, Block block, int levels, boolean all) {
        int currentXp = totalExperience(player);
        int amount = all ? currentXp : depositAmountForLevels(player, levels);
        if (amount <= 0) {
            player.sendMessage(MessageUtil.warn("You do not have enough XP to deposit that."));
            return;
        }

        long stored = storedXp(block);
        long accepted = Math.min(amount, MAX_STORED_XP - stored);
        if (accepted <= 0L) {
            player.sendMessage(MessageUtil.warn("This XP Lectern is full."));
            return;
        }

        setTotalExperience(player, currentXp - (int) accepted);
        setStoredXp(block, stored + accepted);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.65f, 0.75f);
        player.spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1.0, 0), 18, 0.35, 0.35, 0.35, 0.02);
        player.sendActionBar(MM.deserialize("<aqua>Stored <white>" + levelSummary(accepted) + "</white> in the XP Lectern.</aqua>"));
        ensureHologram(block);
    }

    private void withdrawLevels(Player player, Block block, int levels, boolean all) {
        long stored = storedXp(block);
        if (stored <= 0L) {
            player.sendMessage(MessageUtil.warn("This XP Lectern has no stored XP."));
            return;
        }

        int currentXp = totalExperience(player);
        long capacity = Math.max(0L, Integer.MAX_VALUE - (long) currentXp);
        if (capacity <= 0L) {
            player.sendMessage(MessageUtil.warn("You cannot hold any more XP right now."));
            return;
        }

        long amount = all ? stored : Math.min(stored, withdrawAmountForLevels(player, levels));
        amount = Math.min(amount, capacity);
        if (amount <= 0L) {
            player.sendMessage(MessageUtil.warn("This XP Lectern does not have enough XP for that withdrawal."));
            return;
        }

        setStoredXp(block, stored - amount);
        setTotalExperience(player, clampPlayerXp(currentXp + amount));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.75f, 1.55f);
        player.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.0, 0), 12, 0.35, 0.35, 0.35, 0.02);
        player.sendActionBar(MM.deserialize("<aqua>Withdrew <white>" + levelSummary(amount) + "</white> from the XP Lectern.</aqua>"));
        ensureHologram(block);
    }

    private void bottleExperience(Player player, Block block, int requestedBottles) {
        int bottles = normalizedBottleCount(requestedBottles);
        long xpCost = bottlingXpCost(bottles);
        long stored = storedXp(block);
        if (bottles <= 0 || xpCost <= 0L) {
            player.sendMessage(MessageUtil.error("That XP bottle amount is invalid."));
            return;
        }
        if (stored < xpCost) {
            player.sendMessage(MessageUtil.warn("This XP Lectern needs <white>" + xpCost + " stored XP</white> for that."));
            return;
        }

        List<InventoryRecipeUtil.Ingredient> ingredients = List.of(
            InventoryRecipeUtil.plainMaterial(plugin, Material.GLASS_BOTTLE, bottles)
        );
        ItemStack reward = createFilledBottle(bottles);
        if (!InventoryRecipeUtil.hasIngredients(player, ingredients)) {
            player.sendMessage(MessageUtil.warn("You need <white>" + bottles + " plain glass bottle" + (bottles == 1 ? "" : "s") + "</white>."));
            return;
        }
        if (!InventoryRecipeUtil.canFitRewardAfterRemovingIngredients(player, ingredients, reward)) {
            player.sendMessage(MessageUtil.warn("Clear inventory space for the filled XP bottles."));
            return;
        }

        ItemStack[] storageBefore = cloneContents(player.getInventory().getStorageContents());
        ItemStack offhandBefore = cloneItem(player.getInventory().getItemInOffHand());
        if (!InventoryRecipeUtil.removeIngredients(player, ingredients)) {
            player.sendMessage(MessageUtil.error("The glass bottles changed before they could be filled."));
            return;
        }

        setStoredXp(block, stored - xpCost);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
        if (!leftovers.isEmpty()) {
            player.getInventory().setStorageContents(storageBefore);
            player.getInventory().setItemInOffHand(offhandBefore);
            setStoredXp(block, stored);
            player.updateInventory();
            player.sendMessage(MessageUtil.error("The XP bottles could not be stored, so nothing was consumed."));
            return;
        }

        player.playSound(player.getLocation(), Sound.ITEM_BOTTLE_FILL, 0.8f, 1.35f);
        player.spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1.0, 0), 14, 0.3, 0.3, 0.3, 0.02);
        player.sendActionBar(MM.deserialize("<light_purple>Filled <white>" + bottles + " XP bottle" + (bottles == 1 ? "" : "s") + "</white> for <white>" + xpCost + " XP</white>.</light_purple>"));
        player.updateInventory();
        ensureHologram(block);
    }

    static int normalizedBottleCount(int requested) {
        return requested == 1 || requested == 8 ? requested : 0;
    }

    static long bottlingXpCost(int bottles) {
        return bottles <= 0 ? 0L : Math.multiplyExact((long) bottles, XP_PER_BOTTLE);
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] clone = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            clone[i] = cloneItem(contents[i]);
        }
        return clone;
    }

    private ItemStack cloneItem(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }

    private ItemStack createFilledBottle(int amount) {
        ItemStack bottle = new ItemStack(Material.EXPERIENCE_BOTTLE, Math.max(1, amount));
        ItemMeta meta = bottle.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(keyFilledBottle, PersistentDataType.BYTE, (byte) 1);
            bottle.setItemMeta(meta);
        }
        return bottle;
    }

    private boolean isLecternFilledBottle(ItemStack item) {
        if (item == null || item.getType() != Material.EXPERIENCE_BOTTLE) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(keyFilledBottle, PersistentDataType.BYTE);
    }

    private boolean isFilledBottleOrb(ExperienceOrb orb) {
        return orb != null && orb.getPersistentDataContainer().has(keyFilledBottleOrb, PersistentDataType.BYTE);
    }

    private void syncLoadedLecterns() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                syncChunkLecterns(chunk);
            }
        }
    }

    private void syncChunkLecterns(Chunk chunk) {
        removeStaleChunkHolograms(chunk);
        for (BlockState tile : chunk.getTileEntities()) {
            Block block = tile.getBlock();
            if (isLecternBlock(block)) {
                ensureHologram(block);
            }
        }
    }

    private void ensureHologram(Block block) {
        if (!isLecternBlock(block)) {
            return;
        }
        String blockKey = blockKey(block);
        UUID existingId = hologramsByBlock.get(blockKey);
        Entity existing = existingId == null ? null : Bukkit.getEntity(existingId);
        if (existing instanceof TextDisplay display && display.isValid()) {
            display.teleport(hologramLocation(block));
            display.text(hologramText(block));
            VisualRangeUtil.applyHologramRange(display);
            return;
        }

        removeHologram(block);
        block.getWorld().spawn(hologramLocation(block), TextDisplay.class, display -> {
            display.text(hologramText(block));
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
            display.setBackgroundColor(Color.fromARGB(92, 9, 13, 24));
            PersistentDataContainer pdc = display.getPersistentDataContainer();
            pdc.set(keyHologram, PersistentDataType.BYTE, (byte) 1);
            pdc.set(keyHologramBlock, PersistentDataType.STRING, blockKey);
            hologramsByBlock.put(blockKey, display.getUniqueId());
        });
    }

    private Component hologramText(Block block) {
        return Component.empty()
            .append(MM.deserialize("<gradient:#38bdf8:#a78bfa><bold>XP Lectern</bold></gradient>"))
            .append(Component.newline())
            .append(MM.deserialize("<gray>Stored: <white>" + levelSummary(storedXp(block)) + "</white></gray>"));
    }

    private Location hologramLocation(Block block) {
        return block.getLocation().add(0.5, 1.55, 0.5);
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
            if (isHologramFor(entity, blockKey)) {
                entity.remove();
            }
        }
    }

    private void removeChunkHolograms(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (!isHologram(entity)) {
                continue;
            }
            String blockKey = entity.getPersistentDataContainer().get(keyHologramBlock, PersistentDataType.STRING);
            if (blockKey != null) {
                hologramsByBlock.remove(blockKey);
            }
            entity.remove();
        }
    }

    private void removeStaleChunkHolograms(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (!isHologram(entity)) {
                continue;
            }
            String blockKey = entity.getPersistentDataContainer().get(keyHologramBlock, PersistentDataType.STRING);
            Block block = blockFromKey(blockKey);
            if (block == null || !isLecternBlock(block)) {
                if (blockKey != null) {
                    hologramsByBlock.remove(blockKey);
                }
                entity.remove();
            }
        }
    }

    private boolean isHologram(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(keyHologram, PersistentDataType.BYTE);
    }

    private boolean isHologramFor(Entity entity, String blockKey) {
        return isHologram(entity)
            && blockKey.equals(entity.getPersistentDataContainer().get(keyHologramBlock, PersistentDataType.STRING));
    }

    public boolean isLecternBlock(Block block) {
        if (block == null || block.getType() != Material.LECTERN) {
            return false;
        }
        BlockState state = block.getState();
        return state instanceof TileState tile
            && ITEM_ID.equals(tile.getPersistentDataContainer().get(keyLecternBlock, PersistentDataType.STRING));
    }

    private long storedXp(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null) {
            return 0L;
        }
        return clampStoredXp(meta.getPersistentDataContainer().getOrDefault(keyStoredXp, PersistentDataType.LONG, 0L));
    }

    private long storedXp(Block block) {
        if (!isLecternBlock(block)) {
            return 0L;
        }
        BlockState state = block.getState();
        if (!(state instanceof TileState tile)) {
            return 0L;
        }
        return clampStoredXp(tile.getPersistentDataContainer().getOrDefault(keyStoredXp, PersistentDataType.LONG, 0L));
    }

    private void setStoredXp(Block block, long storedXp) {
        BlockState state = block.getState();
        if (state instanceof TileState tile) {
            setStoredXp(tile, storedXp);
            tile.update(true, false);
        }
    }

    private void setStoredXp(TileState tile, long storedXp) {
        long safe = clampStoredXp(storedXp);
        if (safe <= 0L) {
            tile.getPersistentDataContainer().remove(keyStoredXp);
        } else {
            tile.getPersistentDataContainer().set(keyStoredXp, PersistentDataType.LONG, safe);
        }
    }

    private long clampStoredXp(long value) {
        return Math.max(0L, Math.min(MAX_STORED_XP, value));
    }

    private static int clampPlayerXp(long value) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, value));
    }

    private int depositAmountForLevels(Player player, int levels) {
        if (player == null || levels <= 0 || player.getLevel() <= 0) {
            return 0;
        }
        int currentTotal = totalExperience(player);
        int targetLevel = Math.max(0, player.getLevel() - levels);
        return Math.max(0, currentTotal - totalXpAtLevel(targetLevel));
    }

    private int withdrawAmountForLevels(Player player, int levels) {
        if (player == null || levels <= 0) {
            return 0;
        }
        long target = totalXpAtLevel(player.getLevel() + levels);
        long currentBase = totalXpAtLevel(player.getLevel());
        return clampPlayerXp(target - currentBase);
    }

    private int totalExperience(Player player) {
        if (player == null) {
            return 0;
        }
        int level = Math.max(0, player.getLevel());
        int base = totalXpAtLevel(level);
        int progress = Math.round(player.getExp() * xpToNextLevel(level));
        return Math.max(0, base + progress);
    }

    private void setTotalExperience(Player player, int amount) {
        int safe = Math.max(0, amount);
        int level = levelFromTotalXp(safe);
        int base = totalXpAtLevel(level);
        int next = Math.max(1, xpToNextLevel(level));
        float progress = Math.max(0.0f, Math.min(1.0f, (safe - base) / (float) next));
        player.setExp(0.0f);
        player.setLevel(0);
        player.setTotalExperience(0);
        player.setLevel(level);
        player.setExp(progress);
        player.setTotalExperience(safe);
    }

    private static int xpToNextLevel(int level) {
        if (level >= 31) {
            return 9 * level - 158;
        }
        if (level >= 16) {
            return 5 * level - 38;
        }
        return 2 * level + 7;
    }

    private static int totalXpAtLevel(int level) {
        int safeLevel = Math.max(0, level);
        if (safeLevel <= 16) {
            return safeLevel * safeLevel + 6 * safeLevel;
        }
        if (safeLevel <= 31) {
            return (int) (2.5D * safeLevel * safeLevel - 40.5D * safeLevel + 360.0D);
        }
        return (int) (4.5D * safeLevel * safeLevel - 162.5D * safeLevel + 2220.0D);
    }

    private int approximateLevel(long xp) {
        return levelFromTotalXp(clampPlayerXp(xp));
    }

    private static int levelFromTotalXp(int xp) {
        int target = clampPlayerXp(xp);
        int low = 0;
        int high = 24791;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (totalXpAtLevel(mid) <= target) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    private String levelSummary(long xp) {
        long safe = clampStoredXp(xp);
        return approximateLevel(safe) + " levels (" + safe + " XP)";
    }

    private String menuAction(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(keyMenuAction, PersistentDataType.STRING);
    }

    private int menuAmount(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        return meta.getPersistentDataContainer().getOrDefault(keyMenuAmount, PersistentDataType.INTEGER, 0);
    }

    private ItemStack item(Material material, String name, List<String> lore, String action, int amount) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MM.deserialize(MenuItemUtil.visibleMiniName(name)));
            meta.lore(MenuItemUtil.visibleMiniLore(name, lore).stream().map(MM::deserialize).toList());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            if (action != null) {
                meta.getPersistentDataContainer().set(keyMenuAction, PersistentDataType.STRING, action);
                meta.getPersistentDataContainer().set(keyMenuAmount, PersistentDataType.INTEGER, Math.max(0, amount));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private String blockKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private Block blockFromKey(String key) {
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
            return world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private record XpLecternMenuHolder(String blockKey) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
