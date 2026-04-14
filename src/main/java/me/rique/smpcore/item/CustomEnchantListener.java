package me.rique.smpcore.item;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class CustomEnchantListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Component ENCHANTS_MENU_TITLE = MM.deserialize("<dark_aqua><bold>Custom Enchants</bold></dark_aqua>");
    private static final String DELICATE_LORE_LINE = "Delicate I";
    private static final String TELEKINESIS_LORE_LINE = "Telekinesis I";
    private static final String SMELTING_TOUCH_LORE_LINE = "Smelting Touch I";
    private static final String WISE_LORE_PREFIX = "Wise ";
    private static final String DOUBLE_JUMP_LORE_LINE = "Double Jump I";
    private static final long TELEKINESIS_MINING_CONTEXT_TTL_MS = 1000L;

    private final SMPCore plugin;
    private final NamespacedKey keyCustomEnchantBook;
    private final NamespacedKey keyDelicate;
    private final NamespacedKey keyTelekinesis;
    private final NamespacedKey keySmeltingTouch;
    private final NamespacedKey keyWise;
    private final NamespacedKey keyDoubleJump;
    private final NamespacedKey keyTelekinesisProjectileOwner;
    private final Map<UUID, UUID> telekinesisLootOwners = new ConcurrentHashMap<>();
    private final Map<BlockKey, TelekinesisMiningContext> telekinesisMiningContexts = new ConcurrentHashMap<>();
    private final Map<UUID, Double> wiseXpRemainders = new ConcurrentHashMap<>();
    private final Map<Material, ItemStack> smeltingResults = new ConcurrentHashMap<>();
    private final java.util.Set<Material> nonSmeltableDrops = ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> doubleJumpFlightPlayers = ConcurrentHashMap.newKeySet();

    public CustomEnchantListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyCustomEnchantBook = new NamespacedKey(plugin, "custom_enchant_book");
        this.keyDelicate = new NamespacedKey(plugin, "delicate_enchant");
        this.keyTelekinesis = new NamespacedKey(plugin, "telekinesis_enchant");
        this.keySmeltingTouch = new NamespacedKey(plugin, "smelting_touch_enchant");
        this.keyWise = new NamespacedKey(plugin, "wise_enchant");
        this.keyDoubleJump = new NamespacedKey(plugin, "double_jump_enchant");
        this.keyTelekinesisProjectileOwner = new NamespacedKey(plugin, "telekinesis_projectile_owner");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickDoubleJumpFlightPlayers, 1L, 2L);
    }

    public ItemStack createDelicateBook() {
        return createBook(CustomEnchantEntry.DELICATE, 1);
    }

    public ItemStack createTelekinesisBook() {
        return createBook(CustomEnchantEntry.TELEKINESIS, 1);
    }

    public ItemStack createSmeltingTouchBook() {
        return createBook(CustomEnchantEntry.SMELTING_TOUCH, 1);
    }

    public ItemStack createWiseBook(int level) {
        return createBook(CustomEnchantEntry.WISE, clampWiseLevel(level));
    }

    public ItemStack createDoubleJumpBook() {
        return createBook(CustomEnchantEntry.DOUBLE_JUMP, 1);
    }

    public boolean hasTelekinesisEnchant(ItemStack item) {
        return hasTelekinesis(item);
    }

    public boolean hasSmeltingTouchEnchant(ItemStack item) {
        return hasSmeltingTouch(item);
    }

    public void deliverTelekinesisDrops(Player player, Collection<ItemStack> drops, Location origin) {
        giveDrops(player, drops, origin);
    }

    public void applyManagedEnchantLore(ItemMeta meta) {
        if (meta == null) {
            return;
        }

        List<Component> baseLore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        baseLore.removeIf(line -> isManagedEnchantLoreLine(PLAIN.serialize(line).trim()));

        List<Component> managedLore = new ArrayList<>();
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            int level = storedEnchantLevel(meta, enchant);
            if (level <= 0) {
                continue;
            }
            managedLore.add(MM.deserialize("<gray>" + enchant.loreLine(level) + "</gray>"));
        }

        if (managedLore.isEmpty() && baseLore.isEmpty()) {
            meta.lore(null);
            return;
        }

        managedLore.addAll(baseLore);
        meta.lore(managedLore);
    }

    public List<ItemStack> smeltMiningDrops(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
            return List.of();
        }

        ItemStack result = findSmeltingResult(stack.getType());
        if (result == null || result.getType() == Material.AIR || result.getAmount() <= 0) {
            return List.of(stack.clone());
        }

        long totalAmount = (long) result.getAmount() * stack.getAmount();
        if (totalAmount <= 0L) {
            return List.of(stack.clone());
        }

        List<ItemStack> smelted = new ArrayList<>();
        int maxStack = Math.max(1, result.getMaxStackSize());
        long remaining = totalAmount;
        while (remaining > 0L) {
            ItemStack split = result.clone();
            split.setAmount((int) Math.min(remaining, maxStack));
            smelted.add(split);
            remaining -= split.getAmount();
        }
        return smelted;
    }

    public void openEnchantMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(
            new EnchantMenuHolder(),
            9,
            BedrockCompat.menuTitle(player, ENCHANTS_MENU_TITLE, "Custom Enchants")
        );
        inventory.setItem(0, createMenuIcon(CustomEnchantEntry.REPLENISH));
        inventory.setItem(2, createMenuIcon(CustomEnchantEntry.DELICATE));
        inventory.setItem(4, createMenuIcon(CustomEnchantEntry.TELEKINESIS));
        inventory.setItem(6, createMenuIcon(CustomEnchantEntry.SMELTING_TOUCH));
        inventory.setItem(7, createMenuIcon(CustomEnchantEntry.DOUBLE_JUMP));
        inventory.setItem(8, createMenuIcon(CustomEnchantEntry.WISE));
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        ItemStack item = event.getItem();
        List<CustomEnchantEntry> candidates = enchantTableCandidates(item, event.getExpLevelCost());
        if (candidates.isEmpty()) return;

        CustomEnchantEntry selected = pickEnchantTableEntry(candidates);
        if (selected == null) return;

        int level = enchantTableLevel(selected, event.getExpLevelCost());
        applyEnchant(item, selected, level);
        event.getEnchanter().sendMessage(MessageUtil.success(
            "Your item gained <white>" + selected.plainDisplay(level) + "</white>."
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getFirstItem();
        ItemStack right = event.getInventory().getSecondItem();
        BookEnchantData enchant = bookEnchant(right);
        if (enchant == null) return;
        if (!canApply(left, enchant.enchant(), enchant.level())) {
            event.setResult(null);
            return;
        }

        event.setResult(applyEnchant(left.clone(), enchant.enchant(), enchant.level()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnvilClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getType() != InventoryType.ANVIL) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;
        if (!(event.getView().getTopInventory() instanceof AnvilInventory anvil)) return;

        ItemStack left = anvil.getFirstItem();
        ItemStack right = anvil.getSecondItem();
        BookEnchantData enchant = bookEnchant(right);
        if (enchant == null || !canApply(left, enchant.enchant(), enchant.level())) return;

        ItemStack result = applyEnchant(left.clone(), enchant.enchant(), enchant.level());
        if (result == null || result.getType() == Material.AIR) return;

        event.setCancelled(true);
        if (!canReceiveAnvilResult(player, event)) {
            return;
        }

        anvil.setItem(0, null);
        anvil.setItem(1, consumeOne(right));
        anvil.setItem(2, null);
        giveAnvilResult(player, event, result);
        player.sendMessage(MessageUtil.success(
            "Applied <white>" + enchant.enchant().plainDisplay(enchant.level()) + "</white> to your item."
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (!(event.getInventory() instanceof GrindstoneInventory grindstone)) return;

        ItemStack top = grindstone.getUpperItem();
        ItemStack bottom = grindstone.getLowerItem();
        if (!hasManagedEnchant(top) && !hasManagedEnchant(bottom)) return;

        ItemStack result = event.getResult();
        if (result != null && result.getType() != Material.AIR) {
            event.setResult(stripManagedEnchants(result.clone()));
            return;
        }

        ItemStack source = hasManagedEnchant(top) ? top : bottom;
        if (source == null || source.getType() == Material.AIR) return;
        event.setResult(stripManagedEnchants(source.clone()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmeltingTouchMine(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.getGameMode() == GameMode.CREATIVE) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!hasSmeltingTouch(tool)) return;

        Location origin = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        List<ItemStack> overflow = new ArrayList<>();
        for (Item item : event.getItems()) {
            List<ItemStack> smelted = smeltMiningDrops(item.getItemStack());
            if (smelted.isEmpty()) continue;
            item.setItemStack(smelted.get(0));
            for (int i = 1; i < smelted.size(); i++) {
                overflow.add(smelted.get(i));
            }
        }

        if (!overflow.isEmpty()) {
            if (hasTelekinesis(tool)) {
                giveDrops(player, overflow, origin);
            } else {
                dropDropsNaturally(origin, overflow);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDelicateBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!hasDelicate(tool)) return;

        Block block = event.getBlock();
        if (harvestStemPreservingPlant(player, tool, block)) {
            event.setCancelled(true);
            event.setDropItems(false);
            event.setExpToDrop(0);
            return;
        }

        if (isImmatureDelicateCrop(block)) {
            event.setCancelled(true);
            return;
        }

        if (isAlwaysProtectedPlant(block.getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTelekinesisBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (!hasTelekinesis(player.getInventory().getItemInMainHand())) return;
        if (event.getBlock().getState() instanceof org.bukkit.inventory.InventoryHolder) return;

        rememberTelekinesisMiningContext(player, event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTelekinesisMine(BlockDropItemEvent event) {
        Location blockLocation = event.getBlock().getLocation();
        Player owner = telekinesisMiningOwner(blockLocation);
        if (owner == null || owner.getGameMode() == GameMode.CREATIVE) {
            forgetTelekinesisMiningContext(blockLocation);
            return;
        }

        List<ItemStack> drops = new ArrayList<>();
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) continue;
            drops.add(stack.clone());
        }
        if (drops.isEmpty()) {
            forgetTelekinesisMiningContext(blockLocation);
            return;
        }

        event.setCancelled(true);
        giveDrops(owner, drops, blockLocation);
        forgetTelekinesisMiningContext(blockLocation);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getProjectile() instanceof Projectile projectile)) return;

        ItemStack weapon = event.getBow();
        if (!hasTelekinesis(weapon)) return;

        projectile.getPersistentDataContainer().set(
            keyTelekinesisProjectileOwner,
            PersistentDataType.STRING,
            player.getUniqueId().toString()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || victim instanceof Player) return;

        UUID ownerId = telekinesisOwner(event.getDamager());
        if (ownerId == null) return;
        telekinesisLootOwners.put(victim.getUniqueId(), ownerId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) return;

        UUID ownerId = telekinesisLootOwners.remove(event.getEntity().getUniqueId());
        if (ownerId == null) return;

        Player killer = event.getEntity().getKiller();
        if (killer != null && !killer.getUniqueId().equals(ownerId)) {
            return;
        }

        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) return;
        if (event.getDrops().isEmpty()) return;

        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        event.getDrops().clear();
        giveDrops(owner, drops, event.getEntity().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWiseCropBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (plugin.getConfigManager().wiseCropXp <= 0) return;

        int wiseLevel = wiseLevel(player.getInventory().getItemInMainHand());
        if (wiseLevel <= 0) return;
        if (!isWiseCrop(event.getBlock())) return;

        event.setExpToDrop(Math.max(event.getExpToDrop(), plugin.getConfigManager().wiseCropXp));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWiseXpGain(PlayerExpChangeEvent event) {
        int amount = event.getAmount();
        if (amount <= 0) return;

        int wiseLevel = wiseLevel(event.getPlayer().getInventory().getItemInMainHand());
        if (wiseLevel <= 0) return;

        double bonusRate = wiseBonusRate(wiseLevel);
        if (bonusRate <= 0.0) return;

        UUID playerId = event.getPlayer().getUniqueId();
        double totalBonus = (amount * bonusRate) + wiseXpRemainders.getOrDefault(playerId, 0.0);
        int extra = (int) Math.floor(totalBonus);
        double remainder = totalBonus - extra;
        if (extra <= 0 && remainder <= 0.0) return;

        event.setAmount(amount + extra);
        if (remainder > 0.0001) {
            wiseXpRemainders.put(playerId, remainder);
        } else {
            wiseXpRemainders.remove(playerId);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDoubleJumpFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!event.isFlying()) return;
        if (!doubleJumpFlightPlayers.contains(player.getUniqueId())) return;

        event.setCancelled(true);
        player.setFlying(false);
        if (!shouldKeepExternalFlight(player)) {
            player.setAllowFlight(false);
        }
        doubleJumpFlightPlayers.remove(player.getUniqueId());

        if (!canUseDoubleJump(player)) {
            return;
        }
        if (!consumeDoubleJumpCost(player)) {
            return;
        }

        launchDoubleJump(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        if (!isAncientCityLoot(event.getLootTable())) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= plugin.getConfigManager().doubleJumpAncientCityChestChance) {
            return;
        }

        List<ItemStack> loot = new ArrayList<>(event.getLoot());
        loot.add(createDoubleJumpBook());
        event.setLoot(loot);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        wiseXpRemainders.remove(event.getPlayer().getUniqueId());
        clearDoubleJumpFlight(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof EnchantMenuHolder)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMenuDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof EnchantMenuHolder)) return;
        event.setCancelled(true);
    }

    private ItemStack createBook(CustomEnchantEntry enchant, int level) {
        int appliedLevel = enchant.clampLevel(level);
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta == null) return book;

        meta.displayName(MM.deserialize(enchant.bookDisplay(appliedLevel)));
        meta.lore(buildBookLore(enchant, appliedLevel));
        meta.getPersistentDataContainer().set(keyCustomEnchantBook, PersistentDataType.STRING, enchant.id);
        meta.getPersistentDataContainer().set(keyFor(enchant), PersistentDataType.INTEGER, appliedLevel);
        book.setItemMeta(meta);
        return book;
    }

    private ItemStack createMenuIcon(CustomEnchantEntry enchant) {
        ItemStack icon = new ItemStack(enchant.icon);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;

        meta.displayName(MM.deserialize(enchant.menuDisplay));
        meta.lore(buildMenuLore(enchant));
        icon.setItemMeta(meta);
        return icon;
    }

    private List<Component> buildBookLore(CustomEnchantEntry enchant, int level) {
        List<String> topLines = new ArrayList<>();
        topLines.add("<gray>Levels: <white>" + enchant.levelDisplay(level) + "</white></gray>");
        return CustomLoreUtil.buildStyledLore(
            Material.ENCHANTED_BOOK,
            "ENCHANTED",
            "BOOK",
            topLines,
            List.of(CustomLoreUtil.section(
                "Enchant Effect",
                enchant.plainName(),
                enchantDescriptionLines(enchant, level, true)
            ))
        );
    }

    private List<Component> buildMenuLore(CustomEnchantEntry enchant) {
        List<String> topLines = new ArrayList<>();
        topLines.add("<gray>Levels: <white>" + enchant.levels + "</white></gray>");
        topLines.add("<gray>Enchant Table: <white>" + (enchant.enchantTableEligible ? "Yes" : "No") + "</white></gray>");
        return CustomLoreUtil.buildStyledLore(
            enchant.icon,
            "ENCHANTED",
            "ICON",
            topLines,
            List.of(CustomLoreUtil.section(
                "Enchant Effect",
                enchant.plainName(),
                enchantDescriptionLines(enchant, enchant.maxLevel(), false)
            ))
        );
    }

    private String[] enchantDescriptionLines(CustomEnchantEntry enchant, int level, boolean specificLevelBook) {
        List<String> lines = new ArrayList<>();
        for (String line : enchant.description(plugin.getConfigManager(), level, specificLevelBook)) {
            lines.add("<gray>" + line + "</gray>");
        }
        lines.add("<gray>Apply in an <white>anvil</white> to a valid item.</gray>");
        if (enchant.enchantTableEligible) {
            lines.add("<gray>Also obtainable from an <white>enchant table</white>.</gray>");
        }
        return lines.toArray(String[]::new);
    }

    private BookEnchantData bookEnchant(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        String id = meta.getPersistentDataContainer().get(keyCustomEnchantBook, PersistentDataType.STRING);
        if (id == null) return null;

        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            if (enchant.id.equalsIgnoreCase(id)) {
                int level = storedEnchantLevel(meta, enchant);
                return new BookEnchantData(enchant, level <= 0 ? 1 : enchant.clampLevel(level));
            }
        }
        return null;
    }

    private boolean canApply(ItemStack item, CustomEnchantEntry enchant, int level) {
        return item != null
            && item.getType() != Material.AIR
            && enchant.applicable.test(item.getType())
            && storedEnchantLevel(item, enchant) < enchant.clampLevel(level);
    }

    private List<CustomEnchantEntry> enchantTableCandidates(ItemStack item, int expLevelCost) {
        if (item == null || item.getType() == Material.AIR) return List.of();

        List<CustomEnchantEntry> candidates = new ArrayList<>();
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            if (!enchant.enchantTableEligible) continue;
            if (expLevelCost < enchant.enchantTableMinCost) continue;
            if (!canApply(item, enchant, enchantTableLevel(enchant, expLevelCost))) continue;
            candidates.add(enchant);
        }
        return candidates;
    }

    private CustomEnchantEntry pickEnchantTableEntry(List<CustomEnchantEntry> candidates) {
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);

        List<CustomEnchantEntry> pool = new ArrayList<>();
        for (CustomEnchantEntry enchant : candidates) {
            for (int i = 0; i < enchant.enchantTableWeight; i++) {
                pool.add(enchant);
            }
        }
        if (pool.isEmpty()) {
            return candidates.get(0);
        }

        Collections.shuffle(pool, ThreadLocalRandom.current());
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private ItemStack applyEnchant(ItemStack item, CustomEnchantEntry enchant, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyFor(enchant), PersistentDataType.INTEGER, enchant.clampLevel(level));
        applyManagedEnchantLore(meta);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack stripManagedEnchants(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            pdc.remove(keyFor(enchant));
        }
        applyManagedEnchantLore(meta);
        item.setItemMeta(meta);
        return item;
    }

    private boolean giveAnvilResult(Player player, InventoryClickEvent event, ItemStack result) {
        if (event.isShiftClick()) {
            if (player.getInventory().firstEmpty() == -1) {
                player.sendMessage(MessageUtil.warn("You need at least one empty inventory slot."));
                return false;
            }
            player.getInventory().addItem(result);
            return true;
        }

        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            player.sendMessage(MessageUtil.warn("Your cursor must be empty."));
            return false;
        }

        player.setItemOnCursor(result);
        return true;
    }

    private boolean canReceiveAnvilResult(Player player, InventoryClickEvent event) {
        if (event.isShiftClick()) {
            if (player.getInventory().firstEmpty() != -1) {
                return true;
            }
            player.sendMessage(MessageUtil.warn("You need at least one empty inventory slot."));
            return false;
        }

        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType() == Material.AIR) {
            return true;
        }
        player.sendMessage(MessageUtil.warn("Your cursor must be empty."));
        return false;
    }

    private NamespacedKey keyFor(CustomEnchantEntry enchant) {
        return switch (enchant) {
            case DELICATE -> keyDelicate;
            case TELEKINESIS -> keyTelekinesis;
            case SMELTING_TOUCH -> keySmeltingTouch;
            case WISE -> keyWise;
            case DOUBLE_JUMP -> keyDoubleJump;
            default -> throw new IllegalArgumentException("Unsupported managed enchant: " + enchant.id);
        };
    }

    private boolean hasManagedEnchant(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            if (hasEnchant(item, enchant)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDelicate(ItemStack item) {
        return hasEnchant(item, CustomEnchantEntry.DELICATE);
    }

    private boolean hasTelekinesis(ItemStack item) {
        return hasEnchant(item, CustomEnchantEntry.TELEKINESIS);
    }

    private boolean hasSmeltingTouch(ItemStack item) {
        return hasEnchant(item, CustomEnchantEntry.SMELTING_TOUCH);
    }

    private int wiseLevel(ItemStack item) {
        return storedEnchantLevel(item, CustomEnchantEntry.WISE);
    }

    private boolean hasDoubleJump(ItemStack item) {
        return hasEnchant(item, CustomEnchantEntry.DOUBLE_JUMP);
    }

    private boolean hasEnchant(ItemStack item, CustomEnchantEntry enchant) {
        return storedEnchantLevel(item, enchant) > 0;
    }

    private int storedEnchantLevel(ItemStack item, CustomEnchantEntry enchant) {
        if (item == null || item.getType() == Material.AIR) return 0;
        ItemMeta meta = item.getItemMeta();
        return storedEnchantLevel(meta, enchant);
    }

    private int storedEnchantLevel(ItemMeta meta, CustomEnchantEntry enchant) {
        if (meta == null) return 0;
        Integer stored = meta.getPersistentDataContainer().get(keyFor(enchant), PersistentDataType.INTEGER);
        if (stored == null) return 0;
        return enchant.clampLevel(stored);
    }

    private int enchantTableLevel(CustomEnchantEntry enchant, int expLevelCost) {
        if (enchant != CustomEnchantEntry.WISE) {
            return 1;
        }
        if (expLevelCost >= 30) {
            return 3;
        }
        if (expLevelCost >= 20) {
            return 2;
        }
        return 1;
    }

    private int clampWiseLevel(int level) {
        return CustomEnchantEntry.WISE.clampLevel(level);
    }

    private double wiseBonusRate(int level) {
        return switch (clampWiseLevel(level)) {
            case 1 -> plugin.getConfigManager().wiseLevelOneBonus;
            case 2 -> plugin.getConfigManager().wiseLevelTwoBonus;
            case 3 -> plugin.getConfigManager().wiseLevelThreeBonus;
            default -> 0.0;
        };
    }

    private boolean isManagedEnchantLoreLine(String plain) {
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            if (enchant.matchesLoreLine(plain)) {
                return true;
            }
        }
        return false;
    }

    private void tickDoubleJumpFlightPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateDoubleJumpFlight(player);
        }
    }

    private void updateDoubleJumpFlight(Player player) {
        UUID playerId = player.getUniqueId();
        boolean grantedFlight = doubleJumpFlightPlayers.contains(playerId);
        if (!canUseDoubleJump(player)) {
            if (grantedFlight) {
                clearDoubleJumpFlight(player);
            }
            return;
        }

        if (isOnGround(player)) {
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
                doubleJumpFlightPlayers.add(playerId);
            }
            return;
        }

        if (grantedFlight && !player.isFlying()) {
            if (!shouldKeepExternalFlight(player)) {
                player.setAllowFlight(false);
            }
            doubleJumpFlightPlayers.remove(playerId);
        }
    }

    private boolean canUseDoubleJump(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        if (plugin.getPlayerManager().hasFlightEnabled(player.getUniqueId())) {
            return false;
        }
        if (player.isFlying() || player.isGliding() || player.isInWater() || player.isInsideVehicle()) {
            return false;
        }
        return hasDoubleJump(player.getInventory().getBoots());
    }

    public boolean shouldRetainFlightAccess(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }
        if (doubleJumpFlightPlayers.contains(player.getUniqueId())) {
            return true;
        }
        return isOnGround(player) && canUseDoubleJump(player);
    }

    private boolean shouldKeepExternalFlight(Player player) {
        return plugin.getPlayerManager().hasFlightEnabled(player.getUniqueId())
            || (plugin.getSuperpowerManager() != null && plugin.getSuperpowerManager().shouldRetainFlightAccess(player));
    }

    private boolean isOnGround(Player player) {
        return ((Entity) player).isOnGround();
    }

    private boolean consumeDoubleJumpCost(Player player) {
        int hungerCost = plugin.getConfigManager().doubleJumpHungerCost;
        if (hungerCost <= 0) {
            return true;
        }
        if (player.getFoodLevel() < hungerCost) {
            return false;
        }

        player.setFoodLevel(player.getFoodLevel() - hungerCost);
        return true;
    }

    private void launchDoubleJump(Player player) {
        Vector horizontal = player.getLocation().getDirection().setY(0.0);
        if (horizontal.lengthSquared() > 0.0001) {
            horizontal.normalize().multiply(plugin.getConfigManager().doubleJumpForwardBoost);
        } else {
            horizontal.zero();
        }

        Vector velocity = player.getVelocity().clone();
        velocity.setX(horizontal.getX());
        velocity.setZ(horizontal.getZ());
        velocity.setY(plugin.getConfigManager().doubleJumpVerticalBoost);
        player.setVelocity(velocity);
        player.setFallDistance(0.0f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 0.9f, 1.25f);
    }

    private void clearDoubleJumpFlight(Player player) {
        if (player == null) {
            return;
        }
        if (!doubleJumpFlightPlayers.remove(player.getUniqueId())) {
            return;
        }
        if (!player.isFlying() && !shouldKeepExternalFlight(player)) {
            player.setAllowFlight(false);
        }
    }

    private boolean isAncientCityLoot(LootTable lootTable) {
        if (lootTable == null || lootTable.getKey() == null) {
            return false;
        }
        return LootTables.ANCIENT_CITY.getKey().equals(lootTable.getKey())
            || LootTables.ANCIENT_CITY_ICE_BOX.getKey().equals(lootTable.getKey());
    }

    private ItemStack findSmeltingResult(Material input) {
        if (input == null || input == Material.AIR) {
            return null;
        }
        if (input == Material.NETHERRACK) {
            nonSmeltableDrops.add(input);
            return null;
        }
        ItemStack cached = smeltingResults.get(input);
        if (cached != null) {
            return cached.clone();
        }
        if (nonSmeltableDrops.contains(input)) {
            return null;
        }

        ItemStack testStack = new ItemStack(input);
        var iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            if (!(iterator.next() instanceof CookingRecipe<?> recipe)) {
                continue;
            }
            if (recipe.getInputChoice() == null || !recipe.getInputChoice().test(testStack)) {
                continue;
            }
            ItemStack result = recipe.getResult();
            if (result == null || result.getType() == Material.AIR || result.getAmount() <= 0) {
                continue;
            }
            ItemStack normalized = result.clone();
            smeltingResults.put(input, normalized.clone());
            return normalized;
        }

        nonSmeltableDrops.add(input);
        return null;
    }

    private boolean isWiseCrop(Block block) {
        Material material = block.getType();
        if (material == Material.AIR) {
            return false;
        }
        if (isMatureWiseCrop(block)) {
            return true;
        }
        if (delicateGrowthDirection(material) != null) {
            return true;
        }
        return material == Material.MELON || material == Material.PUMPKIN;
    }

    private boolean isMatureWiseCrop(Block block) {
        Material material = block.getType();
        if (!isDelicateCrop(material)) {
            return false;
        }
        if (!(block.getBlockData() instanceof Ageable ageable)) {
            return false;
        }
        return ageable.getAge() >= ageable.getMaximumAge();
    }

    private static boolean isPickaxeSwordOrHoe(Material material) {
        return Tag.ITEMS_PICKAXES.isTagged(material)
            || Tag.ITEMS_HOES.isTagged(material)
            || material.name().endsWith("_SWORD");
    }

    private String formatPercent(double value) {
        double percent = value * 100.0;
        if (Math.abs(percent - Math.rint(percent)) < 0.0001) {
            return Math.round(percent) + "%";
        }
        return String.format(java.util.Locale.US, "%.1f%%", percent);
    }

    private String romanNumeral(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(level);
        };
    }

    private UUID telekinesisOwner(Entity damager) {
        if (damager instanceof Player player && hasTelekinesis(player.getInventory().getItemInMainHand())) {
            return player.getUniqueId();
        }

        if (!(damager instanceof Projectile projectile)) return null;
        String owner = projectile.getPersistentDataContainer().get(keyTelekinesisProjectileOwner, PersistentDataType.STRING);
        if (owner == null || owner.isBlank()) return null;

        try {
            return UUID.fromString(owner);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void rememberTelekinesisMiningContext(Player player, Location location) {
        cleanupExpiredTelekinesisMiningContexts();
        telekinesisMiningContexts.put(
            blockKey(location),
            new TelekinesisMiningContext(player.getUniqueId(), System.currentTimeMillis() + TELEKINESIS_MINING_CONTEXT_TTL_MS)
        );
    }

    private Player telekinesisMiningOwner(Location location) {
        cleanupExpiredTelekinesisMiningContexts();
        TelekinesisMiningContext context = telekinesisMiningContexts.get(blockKey(location));
        if (context == null) {
            return null;
        }

        Player owner = Bukkit.getPlayer(context.playerId());
        if (owner == null || !owner.isOnline()) {
            telekinesisMiningContexts.remove(blockKey(location));
            return null;
        }
        return owner;
    }

    private void forgetTelekinesisMiningContext(Location location) {
        telekinesisMiningContexts.remove(blockKey(location));
    }

    private void cleanupExpiredTelekinesisMiningContexts() {
        long now = System.currentTimeMillis();
        telekinesisMiningContexts.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    private BlockKey blockKey(Location location) {
        return new BlockKey(
            location.getWorld().getUID(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ()
        );
    }

    private boolean harvestStemPreservingPlant(Player player, ItemStack tool, Block source) {
        BlockFace growthDirection = delicateGrowthDirection(source.getType());
        if (growthDirection == null) return false;

        Block root = findDelicateRoot(source, growthDirection);
        List<Block> harvested = new ArrayList<>();
        Block cursor = root.getRelative(growthDirection);
        while (isSameDelicateFamily(root.getType(), cursor.getType())) {
            harvested.add(cursor);
            cursor = cursor.getRelative(growthDirection);
        }

        if (harvested.isEmpty()) {
            return true;
        }

        List<ItemStack> drops = new ArrayList<>();
        for (Block block : harvested) {
            drops.addAll(block.getDrops(tool, player));
        }

        for (Block block : harvested) {
            block.setType(Material.AIR, false);
        }

        if (hasTelekinesis(tool)) {
            giveDrops(player, drops, source.getLocation());
        } else {
            dropDropsNaturally(source.getLocation(), drops);
        }
        return true;
    }

    private Block findDelicateRoot(Block source, BlockFace growthDirection) {
        Block cursor = source;
        BlockFace towardRoot = growthDirection.getOppositeFace();
        while (isSameDelicateFamily(source.getType(), cursor.getRelative(towardRoot).getType())) {
            cursor = cursor.getRelative(towardRoot);
        }
        return cursor;
    }

    private BlockFace delicateGrowthDirection(Material material) {
        return switch (material) {
            case SUGAR_CANE, CACTUS, BAMBOO, BAMBOO_SAPLING, KELP, KELP_PLANT, TWISTING_VINES, TWISTING_VINES_PLANT -> BlockFace.UP;
            case WEEPING_VINES, WEEPING_VINES_PLANT, CAVE_VINES, CAVE_VINES_PLANT -> BlockFace.DOWN;
            default -> null;
        };
    }

    private boolean isSameDelicateFamily(Material first, Material second) {
        String firstFamily = delicateFamily(first);
        return firstFamily != null && firstFamily.equals(delicateFamily(second));
    }

    private String delicateFamily(Material material) {
        return switch (material) {
            case SUGAR_CANE -> "sugar_cane";
            case CACTUS -> "cactus";
            case BAMBOO, BAMBOO_SAPLING -> "bamboo";
            case KELP, KELP_PLANT -> "kelp";
            case TWISTING_VINES, TWISTING_VINES_PLANT -> "twisting_vines";
            case WEEPING_VINES, WEEPING_VINES_PLANT -> "weeping_vines";
            case CAVE_VINES, CAVE_VINES_PLANT -> "cave_vines";
            default -> null;
        };
    }

    private boolean isImmatureDelicateCrop(Block block) {
        Material material = block.getType();
        if (!isDelicateCrop(material)) {
            return false;
        }

        if (!(block.getBlockData() instanceof Ageable ageable)) {
            return false;
        }

        return ageable.getAge() < ageable.getMaximumAge();
    }

    private boolean isDelicateCrop(Material material) {
        return Tag.CROPS.isTagged(material) || material == Material.COCOA || material == Material.SWEET_BERRY_BUSH;
    }

    private boolean isAlwaysProtectedPlant(Material material) {
        if (Tag.SAPLINGS.isTagged(material)
            || Tag.FLOWERS.isTagged(material)
            || Tag.SMALL_FLOWERS.isTagged(material)) {
            return true;
        }

        return switch (material) {
            case MELON_STEM, ATTACHED_MELON_STEM,
                 PUMPKIN_STEM, ATTACHED_PUMPKIN_STEM,
                 VINE,
                 CHORUS_FLOWER, CHORUS_PLANT,
                 BIG_DRIPLEAF, BIG_DRIPLEAF_STEM, SMALL_DRIPLEAF,
                 MANGROVE_PROPAGULE, LILY_PAD,
                 SHORT_GRASS, TALL_GRASS, FERN, LARGE_FERN,
                 DEAD_BUSH, BROWN_MUSHROOM, RED_MUSHROOM,
                 CRIMSON_FUNGUS, WARPED_FUNGUS,
                 CRIMSON_ROOTS, WARPED_ROOTS, NETHER_SPROUTS,
                 SEA_PICKLE, PINK_PETALS -> true;
            default -> false;
        };
    }

    private void giveDrops(Player player, Collection<ItemStack> drops, Location origin) {
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType() == Material.AIR || drop.getAmount() <= 0) continue;
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(drop);
            leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(origin, left));
        }
    }

    private void dropDropsNaturally(Location origin, Collection<ItemStack> drops) {
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType() == Material.AIR || drop.getAmount() <= 0) continue;
            origin.getWorld().dropItemNaturally(origin, drop);
        }
    }

    private ItemStack consumeOne(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return null;
        if (stack.getAmount() <= 1) return null;

        ItemStack next = stack.clone();
        next.setAmount(stack.getAmount() - 1);
        return next;
    }

    private enum CustomEnchantEntry {
        REPLENISH(
            "replenish",
            "<green><bold>Replenish</bold></green>",
            "<green><bold>Replenish Book</bold></green>",
            "Replenish",
            Material.WHEAT,
            "I",
            List.of(
                "Hoe enchant.",
                "Breaking supported crops replants them automatically."
            ),
            material -> false,
            true,
            1,
            1,
            1
        ),
        DELICATE(
            "delicate",
            "<gold><bold>Delicate</bold></gold>",
            "<gold><bold>Delicate Book</bold></gold>",
            "Delicate",
            Material.TORCHFLOWER,
            "I",
            List.of(
                "Tool and weapon enchant.",
                "Immature crops stay planted.",
                "Stacked plants keep their root stem while you harvest the growth."
            ),
            CustomEnchantListener::isToolOrWeapon,
            true,
            1,
            1,
            1
        ),
        TELEKINESIS(
            "telekinesis",
            "<aqua><bold>Telekinesis</bold></aqua>",
            "<aqua><bold>Telekinesis Book</bold></aqua>",
            "Telekinesis",
            Material.ENDER_PEARL,
            "I",
            List.of(
                "Tool and weapon enchant.",
                "Mining and mob drops go straight into your inventory when space is available."
            ),
            CustomEnchantListener::isToolOrWeapon,
            true,
            1,
            1,
            1
        ),
        SMELTING_TOUCH(
            "smelting_touch",
            "<gold><bold>Smelting Touch</bold></gold>",
            "<gold><bold>Smelting Touch Book</bold></gold>",
            "Smelting Touch",
            Material.BLAST_FURNACE,
            "I",
            List.of(
                "Pickaxe enchant.",
                "Smelts mined drops automatically when a valid cooking recipe exists."
            ),
            Tag.ITEMS_PICKAXES::isTagged,
            true,
            12,
            1,
            1
        ),
        WISE(
            "wise",
            "<light_purple><bold>Wise</bold></light_purple>",
            "<light_purple><bold>Wise Book</bold></light_purple>",
            "Wise",
            Material.EXPERIENCE_BOTTLE,
            "I, II, III",
            List.of(),
            CustomEnchantListener::isPickaxeSwordOrHoe,
            true,
            10,
            1,
            3
        ),
        DOUBLE_JUMP(
            "double_jump",
            "<aqua><bold>Double Jump</bold></aqua>",
            "<aqua><bold>Double Jump Book</bold></aqua>",
            "Double Jump",
            Material.FEATHER,
            "I",
            List.of(),
            Tag.ITEMS_ENCHANTABLE_FOOT_ARMOR::isTagged,
            false,
            1,
            1,
            1
        );

        private static final List<CustomEnchantEntry> MANAGED = List.of(DELICATE, TELEKINESIS, SMELTING_TOUCH, WISE, DOUBLE_JUMP);

        private final String id;
        private final String menuDisplay;
        private final String bookDisplay;
        private final String plainName;
        private final Material icon;
        private final String levels;
        private final List<String> description;
        private final java.util.function.Predicate<Material> applicable;
        private final boolean enchantTableEligible;
        private final int enchantTableMinCost;
        private final int enchantTableWeight;
        private final int maxLevel;

        CustomEnchantEntry(
            String id,
            String menuDisplay,
            String bookDisplay,
            String plainName,
            Material icon,
            String levels,
            List<String> description,
            java.util.function.Predicate<Material> applicable,
            boolean enchantTableEligible,
            int enchantTableMinCost,
            int enchantTableWeight,
            int maxLevel
        ) {
            this.id = id;
            this.menuDisplay = menuDisplay;
            this.bookDisplay = bookDisplay;
            this.plainName = plainName;
            this.icon = icon;
            this.levels = levels;
            this.description = description;
            this.applicable = applicable;
            this.enchantTableEligible = enchantTableEligible;
            this.enchantTableMinCost = enchantTableMinCost;
            this.enchantTableWeight = Math.max(1, enchantTableWeight);
            this.maxLevel = Math.max(1, maxLevel);
        }

        private int clampLevel(int level) {
            return Math.max(1, Math.min(maxLevel, level));
        }

        private int maxLevel() {
            return maxLevel;
        }

        private String plainName() {
            return plainName;
        }

        private String levelDisplay(int level) {
            if (maxLevel <= 1) {
                return "I";
            }
            return switch (this) {
                case WISE -> switch (clampLevel(level)) {
                    case 1 -> "I";
                    case 2 -> "II";
                    case 3 -> "III";
                    default -> Integer.toString(level);
                };
                default -> levels;
            };
        }

        private String loreLine(int level) {
            return plainName + " " + switch (clampLevel(level)) {
                case 1 -> "I";
                case 2 -> "II";
                case 3 -> "III";
                case 4 -> "IV";
                case 5 -> "V";
                default -> Integer.toString(level);
            };
        }

        private boolean matchesLoreLine(String plain) {
            if (plain == null || plain.isBlank()) {
                return false;
            }
            if (this == WISE) {
                return plain.startsWith(WISE_LORE_PREFIX);
            }
            if (this == DOUBLE_JUMP) {
                return DOUBLE_JUMP_LORE_LINE.equalsIgnoreCase(plain);
            }
            return loreLine(1).equalsIgnoreCase(plain);
        }

        private String bookDisplay(int level) {
            if (maxLevel <= 1) {
                return bookDisplay;
            }
            return switch (this) {
                case WISE -> "<light_purple><bold>Wise " + switch (clampLevel(level)) {
                    case 1 -> "I";
                    case 2 -> "II";
                    case 3 -> "III";
                    default -> Integer.toString(level);
                } + " Book</bold></light_purple>";
                default -> bookDisplay;
            };
        }

        private String plainDisplay(int level) {
            if (maxLevel <= 1) {
                return plainName + " I";
            }
            return loreLine(level);
        }

        private List<String> description(me.rique.smpcore.config.ConfigManager config, int level, boolean specificLevelBook) {
            if (this == DOUBLE_JUMP) {
                return List.of(
                    "Boots enchant.",
                    "Jump again in midair to launch yourself forward.",
                    "Each jump costs " + formatFoodCost(config.doubleJumpHungerCost) + ".",
                    "Found in Ancient City chests at " + formatConfigPercent(config.doubleJumpAncientCityChestChance) + "."
                );
            }

            if (this != WISE) {
                return description;
            }

            if (specificLevelBook) {
                double bonus = switch (clampLevel(level)) {
                    case 1 -> config.wiseLevelOneBonus;
                    case 2 -> config.wiseLevelTwoBonus;
                    case 3 -> config.wiseLevelThreeBonus;
                    default -> 0.0;
                };
                String percent = formatConfigPercent(bonus);
                return List.of(
                    "Pickaxe, sword, and hoe enchant.",
                    "Grants +" + percent + " XP from all sources while held.",
                    "Harvesting crops with it drops at least " + config.wiseCropXp + " XP."
                );
            }

            return List.of(
                "Pickaxe, sword, and hoe enchant.",
                "Level I: +" + formatConfigPercent(config.wiseLevelOneBonus) + " XP from all sources while held.",
                "Level II: +" + formatConfigPercent(config.wiseLevelTwoBonus) + " XP from all sources while held.",
                "Level III: +" + formatConfigPercent(config.wiseLevelThreeBonus) + " XP from all sources while held.",
                "Harvesting crops with it drops at least " + config.wiseCropXp + " XP."
            );
        }

        private static String formatConfigPercent(double bonus) {
            double percent = bonus * 100.0;
            if (Math.abs(percent - Math.rint(percent)) < 0.0001) {
                return Math.round(percent) + "%";
            }
            return String.format(java.util.Locale.US, "%.1f%%", percent);
        }

        private static String formatNumber(double value) {
            if (Math.abs(value - Math.rint(value)) < 0.0001) {
                return Integer.toString((int) Math.round(value));
            }
            return String.format(java.util.Locale.US, "%.1f", value);
        }

        private static String formatFoodCost(int hungerCost) {
            if (hungerCost <= 0) {
                return "no hunger";
            }
            if ((hungerCost & 1) == 0) {
                return (hungerCost / 2) + " hunger bars";
            }
            return hungerCost + " hunger points";
        }
    }

    private static boolean isToolOrWeapon(Material material) {
        return Tag.ITEMS_PICKAXES.isTagged(material)
            || Tag.ITEMS_AXES.isTagged(material)
            || Tag.ITEMS_SHOVELS.isTagged(material)
            || Tag.ITEMS_HOES.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_WEAPON.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_BOW.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_CROSSBOW.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_TRIDENT.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_MACE.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_FISHING.isTagged(material)
            || material == Material.SHEARS;
    }

    private record EnchantMenuHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record BookEnchantData(CustomEnchantEntry enchant, int level) {}

    private record BlockKey(UUID worldId, int x, int y, int z) {}

    private record TelekinesisMiningContext(UUID playerId, long expiresAt) {}
}
