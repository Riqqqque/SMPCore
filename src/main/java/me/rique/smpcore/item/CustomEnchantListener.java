package me.rique.smpcore.item;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

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

    private final SMPCore plugin;
    private final NamespacedKey keyCustomEnchantBook;
    private final NamespacedKey keyDelicate;
    private final NamespacedKey keyTelekinesis;
    private final NamespacedKey keyTelekinesisProjectileOwner;
    private final Map<UUID, UUID> telekinesisLootOwners = new ConcurrentHashMap<>();

    public CustomEnchantListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyCustomEnchantBook = new NamespacedKey(plugin, "custom_enchant_book");
        this.keyDelicate = new NamespacedKey(plugin, "delicate_enchant");
        this.keyTelekinesis = new NamespacedKey(plugin, "telekinesis_enchant");
        this.keyTelekinesisProjectileOwner = new NamespacedKey(plugin, "telekinesis_projectile_owner");
    }

    public ItemStack createDelicateBook() {
        return createBook(CustomEnchantEntry.DELICATE);
    }

    public ItemStack createTelekinesisBook() {
        return createBook(CustomEnchantEntry.TELEKINESIS);
    }

    public void openEnchantMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(new EnchantMenuHolder(), 9, ENCHANTS_MENU_TITLE);
        inventory.setItem(2, createMenuIcon(CustomEnchantEntry.REPLENISH));
        inventory.setItem(4, createMenuIcon(CustomEnchantEntry.DELICATE));
        inventory.setItem(6, createMenuIcon(CustomEnchantEntry.TELEKINESIS));
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        ItemStack item = event.getItem();
        List<CustomEnchantEntry> candidates = enchantTableCandidates(item, event.getExpLevelCost());
        if (candidates.isEmpty()) return;

        CustomEnchantEntry selected = pickEnchantTableEntry(candidates);
        if (selected == null) return;

        applyEnchant(item, selected);
        event.getEnchanter().sendMessage(MessageUtil.success(
            "Your item gained <white>" + PLAIN.serialize(MM.deserialize(selected.menuDisplay)) + " " + selected.levels + "</white>."
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getFirstItem();
        ItemStack right = event.getInventory().getSecondItem();
        CustomEnchantEntry enchant = bookEnchant(right);
        if (enchant == null) return;
        if (!canApply(left, enchant)) {
            event.setResult(null);
            return;
        }

        event.setResult(applyEnchant(left.clone(), enchant));
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
        CustomEnchantEntry enchant = bookEnchant(right);
        if (enchant == null || !canApply(left, enchant)) return;

        ItemStack result = applyEnchant(left.clone(), enchant);
        if (result == null || result.getType() == Material.AIR) return;

        event.setCancelled(true);
        if (!giveAnvilResult(player, event, result)) {
            return;
        }

        anvil.setItem(0, null);
        anvil.setItem(1, consumeOne(right));
        anvil.setItem(2, null);
        player.sendMessage(MessageUtil.success(
            "Applied <white>" + PLAIN.serialize(MM.deserialize(enchant.menuDisplay)) + " " + enchant.levels + "</white> to your item."
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
    public void onTelekinesisMine(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (!event.isDropItems()) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!hasTelekinesis(tool)) return;
        if (event.getBlock().getState() instanceof org.bukkit.inventory.InventoryHolder) return;

        List<ItemStack> drops = new ArrayList<>(event.getBlock().getDrops(tool, player));
        if (drops.isEmpty()) return;

        event.setDropItems(false);
        giveDrops(player, drops, event.getBlock().getLocation());
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

    private ItemStack createBook(CustomEnchantEntry enchant) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta == null) return book;

        meta.displayName(MM.deserialize(enchant.bookDisplay));
        meta.lore(buildBookLore(enchant));
        meta.getPersistentDataContainer().set(keyCustomEnchantBook, PersistentDataType.STRING, enchant.id);
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

    private List<Component> buildBookLore(CustomEnchantEntry enchant) {
        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<dark_gray>Custom Enchant Book</dark_gray>"));
        lore.add(MM.deserialize("<gray>Levels: <white>" + enchant.levels + "</white></gray>"));
        for (String line : enchant.description) {
            lore.add(MM.deserialize("<gray>" + line + "</gray>"));
        }
        lore.add(MM.deserialize("<gray>Apply in an <white>anvil</white> to a valid item.</gray>"));
        if (enchant.enchantTableEligible) {
            lore.add(MM.deserialize("<gray>Also obtainable from an <white>enchant table</white>.</gray>"));
        }
        return lore;
    }

    private List<Component> buildMenuLore(CustomEnchantEntry enchant) {
        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<gray>Levels: <white>" + enchant.levels + "</white></gray>"));
        if (enchant.enchantTableEligible) {
            lore.add(MM.deserialize("<gray>Enchant Table: <white>Yes</white></gray>"));
        }
        for (String line : enchant.description) {
            lore.add(MM.deserialize("<gray>" + line + "</gray>"));
        }
        return lore;
    }

    private CustomEnchantEntry bookEnchant(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        String id = meta.getPersistentDataContainer().get(keyCustomEnchantBook, PersistentDataType.STRING);
        if (id == null) return null;

        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            if (enchant.id.equalsIgnoreCase(id)) {
                return enchant;
            }
        }
        return null;
    }

    private boolean canApply(ItemStack item, CustomEnchantEntry enchant) {
        return item != null
            && item.getType() != Material.AIR
            && enchant.applicable.test(item.getType())
            && !hasEnchant(item, enchant);
    }

    private List<CustomEnchantEntry> enchantTableCandidates(ItemStack item, int expLevelCost) {
        if (item == null || item.getType() == Material.AIR) return List.of();

        List<CustomEnchantEntry> candidates = new ArrayList<>();
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            if (!enchant.enchantTableEligible) continue;
            if (expLevelCost < enchant.enchantTableMinCost) continue;
            if (!canApply(item, enchant)) continue;
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

    private ItemStack applyEnchant(ItemStack item, CustomEnchantEntry enchant) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyFor(enchant), PersistentDataType.INTEGER, 1);

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.removeIf(line -> enchant.loreLine.equalsIgnoreCase(PLAIN.serialize(line).trim()));
        lore.add(MM.deserialize(enchant.loreFormat));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack stripManagedEnchants(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(keyDelicate);
        pdc.remove(keyTelekinesis);

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.removeIf(line -> {
            String plain = PLAIN.serialize(line).trim();
            return DELICATE_LORE_LINE.equalsIgnoreCase(plain) || TELEKINESIS_LORE_LINE.equalsIgnoreCase(plain);
        });
        meta.lore(lore.isEmpty() ? null : lore);
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

    private NamespacedKey keyFor(CustomEnchantEntry enchant) {
        return switch (enchant) {
            case DELICATE -> keyDelicate;
            case TELEKINESIS -> keyTelekinesis;
            default -> throw new IllegalArgumentException("Unsupported managed enchant: " + enchant.id);
        };
    }

    private boolean hasManagedEnchant(ItemStack item) {
        return hasDelicate(item) || hasTelekinesis(item);
    }

    private boolean hasDelicate(ItemStack item) {
        return hasEnchant(item, CustomEnchantEntry.DELICATE);
    }

    private boolean hasTelekinesis(ItemStack item) {
        return hasEnchant(item, CustomEnchantEntry.TELEKINESIS);
    }

    private boolean hasEnchant(ItemStack item, CustomEnchantEntry enchant) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(keyFor(enchant), PersistentDataType.INTEGER);
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
            "Replenish I",
            "<green>Replenish I</green>",
            Material.WHEAT,
            "I",
            List.of(
                "Hoe enchant.",
                "Breaking supported crops replants them automatically."
            ),
            material -> false,
            true,
            1,
            1
        ),
        DELICATE(
            "delicate",
            "<gold><bold>Delicate</bold></gold>",
            "<gold><bold>Delicate Book</bold></gold>",
            DELICATE_LORE_LINE,
            "<gold>Delicate I</gold>",
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
            1
        ),
        TELEKINESIS(
            "telekinesis",
            "<aqua><bold>Telekinesis</bold></aqua>",
            "<aqua><bold>Telekinesis Book</bold></aqua>",
            TELEKINESIS_LORE_LINE,
            "<aqua>Telekinesis I</aqua>",
            Material.ENDER_PEARL,
            "I",
            List.of(
                "Tool and weapon enchant.",
                "Mining and mob drops go straight into your inventory when space is available."
            ),
            CustomEnchantListener::isToolOrWeapon,
            true,
            1,
            1
        );

        private static final List<CustomEnchantEntry> MANAGED = List.of(DELICATE, TELEKINESIS);

        private final String id;
        private final String menuDisplay;
        private final String bookDisplay;
        private final String loreLine;
        private final String loreFormat;
        private final Material icon;
        private final String levels;
        private final List<String> description;
        private final java.util.function.Predicate<Material> applicable;
        private final boolean enchantTableEligible;
        private final int enchantTableMinCost;
        private final int enchantTableWeight;

        CustomEnchantEntry(
            String id,
            String menuDisplay,
            String bookDisplay,
            String loreLine,
            String loreFormat,
            Material icon,
            String levels,
            List<String> description,
            java.util.function.Predicate<Material> applicable,
            boolean enchantTableEligible,
            int enchantTableMinCost,
            int enchantTableWeight
        ) {
            this.id = id;
            this.menuDisplay = menuDisplay;
            this.bookDisplay = bookDisplay;
            this.loreLine = loreLine;
            this.loreFormat = loreFormat;
            this.icon = icon;
            this.levels = levels;
            this.description = description;
            this.applicable = applicable;
            this.enchantTableEligible = enchantTableEligible;
            this.enchantTableMinCost = enchantTableMinCost;
            this.enchantTableWeight = Math.max(1, enchantTableWeight);
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
}
