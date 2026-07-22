package me.rique.smpcore.item;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.compat.CrossplayManager.AnvilRecipe;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.InventoryRecipeUtil;
import me.rique.smpcore.util.ItemModelUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class ReplenishListener implements Listener {

    private static final int REPLENISH_ANVIL_COST = 8;

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final SMPCore plugin;
    private final NamespacedKey keyReplenishHoe;
    private final NamespacedKey keyReplenishBook;

    public ReplenishListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyReplenishHoe = new NamespacedKey(plugin, "replenish_hoe");
        this.keyReplenishBook = new NamespacedKey(plugin, "replenish_book");
    }

    public ItemStack createReplenishBook() {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta == null) return book;

        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.RARE, "Replenish Book"));
        ItemModelUtil.apply(meta, "replenish_book");
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(keyReplenishBook, PersistentDataType.BYTE, (byte) 1);
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.ENCHANTED_BOOK,
            CustomLoreUtil.Rarity.RARE.label(),
            "BOOK",
            List.of("<gray>Custom hoe enchant.</gray>"),
            List.of(CustomLoreUtil.section(
                "Enchant Effect",
                "Replenish",
                "<gray>Apply in an anvil to any hoe.</gray>",
                "<gray>Any successful enchant-table use on a hoe also grants it.</gray>",
                "<gray>Breaking supported crops replants them automatically.</gray>"
            ))
        ));
        book.setItemMeta(meta);
        return book;
    }

    public AnvilRecipe crossplayAnvilRecipe(ItemStack left, ItemStack right) {
        if (isUnsafeReplenishBook(left) || isUnsafeReplenishBook(right)) {
            return null;
        }
        if (!isReplenishBook(right) || !isHoe(left) || hasReplenish(left)) {
            return null;
        }
        return new AnvilRecipe(
            applyReplenish(left.clone()),
            REPLENISH_ANVIL_COST,
            "Applied <white>Replenish I</white>.",
            "Applied Replenish I through the crossplay custom anvil.",
            false
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getFirstItem();
        ItemStack right = event.getInventory().getSecondItem();

        if (isUnsafeReplenishBook(left) || isUnsafeReplenishBook(right)) {
            event.setResult(null);
            return;
        }
        boolean leftBook = isReplenishBook(left);
        boolean rightBook = isReplenishBook(right);
        if (!leftBook && !rightBook) return;
        if (!rightBook) {
            event.setResult(null);
            return;
        }
        if (!isHoe(left) || hasReplenish(left)) {
            event.setResult(null);
            return;
        }

        event.setResult(applyReplenish(left.clone()));
        configureReplenishAnvil(event);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        ItemStack item = event.getItem();
        if (!isHoe(item) || hasReplenish(item)) return;
        if (event.getEnchantsToAdd().isEmpty()) return;

        Player enchanter = event.getEnchanter();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.isCancelled()) return;
            if (!isHoe(item) || hasReplenish(item)) return;
            applyReplenish(item);
            if (enchanter.isOnline()) {
                enchanter.sendMessage(MessageUtil.success("Your hoe gained <white>Replenish I</white>."));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (!(event.getInventory() instanceof GrindstoneInventory grindstone)) return;

        ItemStack top = grindstone.getUpperItem();
        ItemStack bottom = grindstone.getLowerItem();
        if (isUnsafeReplenishBook(top) || isUnsafeReplenishBook(bottom)) {
            event.setResult(null);
            return;
        }
        if (isReplenishBook(top) || isReplenishBook(bottom)) {
            if (!isEmptyItem(top) && !isEmptyItem(bottom)) {
                event.setResult(null);
                return;
            }
            event.setResult(new ItemStack(Material.BOOK));
            return;
        }
        if (!hasReplenish(top) && !hasReplenish(bottom)) return;

        ItemStack result = event.getResult();
        if (result != null && result.getType() != Material.AIR) {
            event.setResult(stripReplenish(result.clone()));
            return;
        }

        ItemStack source = hasReplenish(top) ? top : bottom;
        if (!isEmptyItem(top) && !isEmptyItem(bottom)) {
            event.setResult(null);
            return;
        }
        if (source == null || source.getType() == Material.AIR) return;
        event.setResult(stripReplenish(source.clone()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnvilClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getType() != InventoryType.ANVIL) return;
        if (!isAnvilResultSlot(event)) return;
        if (!(event.getView().getTopInventory() instanceof AnvilInventory anvil)) return;

        ItemStack left = anvil.getFirstItem();
        ItemStack right = anvil.getSecondItem();
        if (isUnsafeReplenishBook(left) || isUnsafeReplenishBook(right)) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.warn(
                "That Replenish book has invalid or mixed enchant data. Ask staff to replace it."
            ));
            return;
        }
        if (!isReplenishBook(right) || !isHoe(left) || hasReplenish(left)) return;

        ItemStack result = applyReplenish(left.clone());
        if (result.getType() == Material.AIR) return;

        event.setCancelled(true);
        if (!canReceiveAnvilResult(player, event)) {
            return;
        }
        if (!canPayAnvilCost(player)) {
            return;
        }

        anvil.setItem(0, null);
        anvil.setItem(1, consumeOne(right));
        anvil.setItem(2, null);
        chargeAnvilCost(player);
        giveAnvilResult(player, event, result.clone());
        player.sendMessage(MessageUtil.success("Applied <white>Replenish I</white> to your hoe."));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCropBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!hasReplenish(tool)) return;

        Block block = event.getBlock();
        CropReplantState crop = cropReplantState(block);
        if (crop == null) return;

        BlockData replanted = crop.replanted();
        if (replanted == null) return;

        BlockData original = block.getBlockData().clone();
        List<ItemStack> drops = List.of();
        boolean needsInventorySeed = false;
        if (crop.mature()) {
            Material seedMaterial = seedMaterialFor(block.getType());
            if (seedMaterial == null) return;

            List<ItemStack> harvestDrops = new ArrayList<>(block.getDrops(tool, player));
            if (!consumeSeedDrop(harvestDrops, seedMaterial)) {
                if (!hasPlainInventoryItem(player, seedMaterial)) {
                    return;
                }
                needsInventorySeed = true;
            }
            if (plugin.getCustomEnchantListener() != null
                && plugin.getCustomEnchantListener().applyHarvestingBonus(player, tool, harvestDrops)) {
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.8, 0.5), 5, 0.25, 0.25, 0.25, 0.01);
            }
            drops = harvestDrops;
        }

        event.setDropItems(false);
        Location location = block.getLocation();
        List<ItemStack> finalDrops = cloneDrops(drops);
        boolean consumeInventorySeedAfterBreak = needsInventorySeed;
        boolean telekinesis = plugin.getCustomEnchantListener() != null
            && plugin.getCustomEnchantListener().hasTelekinesisEnchant(tool);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!block.getType().isAir()) return;
            if (consumeInventorySeedAfterBreak) {
                Material seedMaterial = seedMaterialFor(original.getMaterial());
                if (seedMaterial == null || !consumeOneFromInventory(player, seedMaterial)) {
                    block.setBlockData(original, false);
                    return;
                }
            }
            if (telekinesis) {
                giveDrops(player, finalDrops, location);
            } else {
                dropDropsNaturally(finalDrops, location);
            }
            block.setBlockData(replanted, false);
        });
    }

    public ItemStack applyReplenish(ItemStack hoe) {
        ItemMeta meta = hoe.getItemMeta();
        if (meta == null) return hoe;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyReplenishHoe, PersistentDataType.BYTE, (byte) 1);

        hoe.setItemMeta(meta);
        CustomLoreUtil.refreshEnchantLore(hoe);
        return hoe;
    }

    private ItemStack stripReplenish(ItemStack hoe) {
        if (!isHoe(hoe)) return hoe;
        ItemMeta meta = hoe.getItemMeta();
        if (meta == null) return hoe;

        meta.getPersistentDataContainer().remove(keyReplenishHoe);
        hoe.setItemMeta(meta);
        CustomLoreUtil.refreshEnchantLore(hoe);
        return hoe;
    }

    public ItemStack preserveReplenish(ItemStack source, ItemStack result) {
        if (!hasReplenish(source) || result == null || result.getType().isAir() || !isHoe(result)) {
            return result;
        }
        return applyReplenish(result);
    }

    public boolean hasReplenish(ItemStack item) {
        if (!isHoe(item)) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Byte marker = meta.getPersistentDataContainer().get(keyReplenishHoe, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public boolean isReplenishEnchantDataKey(NamespacedKey key) {
        return keyReplenishHoe.equals(key);
    }

    public boolean hasReplenishBookData(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().getKeys().contains(keyReplenishBook);
    }

    public boolean isReplenishBook(ItemStack item) {
        if (!hasReplenishBookData(item)) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Byte marker = pdc.get(keyReplenishBook, PersistentDataType.BYTE);
        CustomEnchantListener custom = plugin.getCustomEnchantListener();
        boolean hasVanillaEnchantData = !meta.getEnchants().isEmpty()
            || meta instanceof EnchantmentStorageMeta storageMeta && storageMeta.hasStoredEnchants();
        return isValidReplenishBookPayload(
            item.getAmount(),
            marker,
            pdc.getKeys().contains(keyReplenishHoe),
            hasVanillaEnchantData,
            custom != null && custom.hasCustomEnchantBookData(item)
        );
    }

    static boolean isValidReplenishBookPayload(
        int amount,
        Byte marker,
        boolean hasHoeMarker,
        boolean hasVanillaEnchantData,
        boolean hasCustomEnchantData
    ) {
        return amount == 1
            && marker != null
            && marker == (byte) 1
            && !hasHoeMarker
            && !hasVanillaEnchantData
            && !hasCustomEnchantData;
    }

    public boolean isUnsafeReplenishBook(ItemStack item) {
        return hasReplenishBookData(item) && !isReplenishBook(item);
    }

    public boolean normalizeReplenishData(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (item.getType() == Material.ENCHANTED_BOOK) {
            if (!pdc.getKeys().contains(keyReplenishBook)
                || item.getAmount() != 1
                || pdc.get(keyReplenishBook, PersistentDataType.BYTE) == null
                || pdc.get(keyReplenishBook, PersistentDataType.BYTE) != (byte) 1) {
                return false;
            }
            CustomEnchantListener custom = plugin.getCustomEnchantListener();
            if (custom != null && custom.hasCustomEnchantBookData(item)) {
                return false;
            }
            boolean changed = false;
            if (pdc.getKeys().contains(keyReplenishHoe)) {
                pdc.remove(keyReplenishHoe);
                changed = true;
            }
            for (org.bukkit.enchantments.Enchantment enchantment : new ArrayList<>(meta.getEnchants().keySet())) {
                changed |= meta.removeEnchant(enchantment);
            }
            if (meta instanceof EnchantmentStorageMeta storageMeta) {
                for (org.bukkit.enchantments.Enchantment enchantment : new ArrayList<>(storageMeta.getStoredEnchants().keySet())) {
                    changed |= storageMeta.removeStoredEnchant(enchantment);
                }
            }
            if (!meta.hasMaxStackSize() || meta.getMaxStackSize() != 1) {
                meta.setMaxStackSize(1);
                changed = true;
            }
            if (!changed) {
                return false;
            }
            meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.RARE, "Replenish Book"));
            ItemModelUtil.apply(meta, "replenish_book");
            item.setItemMeta(meta);
            CustomLoreUtil.refreshEnchantLore(item);
            return true;
        }

        if (!pdc.getKeys().contains(keyReplenishHoe)) {
            return false;
        }
        Byte marker = pdc.get(keyReplenishHoe, PersistentDataType.BYTE);
        if (isHoe(item) && marker != null && marker == (byte) 1) {
            return false;
        }
        pdc.remove(keyReplenishHoe);
        item.setItemMeta(meta);
        CustomLoreUtil.refreshEnchantLore(item);
        return true;
    }

    private boolean isHoe(ItemStack item) {
        return item != null && isHoe(item.getType());
    }

    private boolean isHoe(Material material) {
        return switch (material) {
            case WOODEN_HOE, STONE_HOE, IRON_HOE, GOLDEN_HOE, DIAMOND_HOE, NETHERITE_HOE -> true;
            default -> false;
        };
    }

    private CropReplantState cropReplantState(Block block) {
        BlockData source = block.getBlockData();
        if (!(source instanceof Ageable ageable)) return null;
        if (seedMaterialFor(block.getType()) == null) return null;

        BlockData replanted = source.clone();
        if (!(replanted instanceof Ageable replantedAgeable)) return null;
        replantedAgeable.setAge(0);
        return new CropReplantState(replanted, ageable.getAge() >= ageable.getMaximumAge());
    }

    private Material seedMaterialFor(Material cropType) {
        return switch (cropType) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            case TORCHFLOWER_CROP -> Material.TORCHFLOWER_SEEDS;
            default -> null;
        };
    }

    private boolean consumeSeedDrop(List<ItemStack> drops, Material seedMaterial) {
        for (int i = 0; i < drops.size(); i++) {
            ItemStack drop = drops.get(i);
            if (drop == null || drop.getType() != seedMaterial) continue;
            if (drop.getAmount() <= 1) {
                drops.remove(i);
            } else {
                drop.setAmount(drop.getAmount() - 1);
            }
            return true;
        }
        return false;
    }

    private boolean consumeOneFromInventory(Player player, Material material) {
        Map<Integer, ? extends ItemStack> leftovers = player.getInventory().removeItem(new ItemStack(material, 1));
        return leftovers.isEmpty();
    }

    private boolean hasPlainInventoryItem(Player player, Material material) {
        if (player == null || material == null || material.isAir()) {
            return false;
        }
        ItemStack plain = new ItemStack(material, 1);
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getAmount() > 0 && item.isSimilar(plain)) {
                return true;
            }
        }
        return false;
    }

    private ItemStack consumeOne(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return null;
        if (stack.getAmount() <= 1) return null;
        ItemStack next = stack.clone();
        next.setAmount(stack.getAmount() - 1);
        return next;
    }

    private static boolean isEmptyItem(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private void giveDrops(Player player, Collection<ItemStack> drops, Location origin) {
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType() == Material.AIR) continue;
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(drop);
            leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(origin, left));
        }
    }

    private void dropDropsNaturally(Collection<ItemStack> drops, Location origin) {
        if (origin == null || origin.getWorld() == null) {
            return;
        }
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir() || drop.getAmount() <= 0) {
                continue;
            }
            origin.getWorld().dropItemNaturally(origin, drop);
        }
    }

    private List<ItemStack> cloneDrops(Collection<ItemStack> drops) {
        if (drops == null || drops.isEmpty()) {
            return List.of();
        }
        List<ItemStack> cloned = new ArrayList<>();
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType() == Material.AIR || drop.getAmount() <= 0) {
                continue;
            }
            cloned.add(drop.clone());
        }
        return cloned;
    }

    private boolean canReceiveAnvilResult(Player player, InventoryClickEvent event) {
        if (!isAllowedResultClick(event.getClick())) {
            player.sendMessage(MessageUtil.warn("Use a normal click or shift-click to take this result."));
            return false;
        }
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

    private boolean isAllowedResultClick(ClickType click) {
        return click == ClickType.LEFT
            || click == ClickType.RIGHT
            || click == ClickType.SHIFT_LEFT
            || click == ClickType.SHIFT_RIGHT;
    }

    private boolean isAnvilResultSlot(InventoryClickEvent event) {
        return event.getView().getTopInventory().getType() == InventoryType.ANVIL
            && (event.getClickedInventory() == event.getView().getTopInventory() || event.getRawSlot() == 2)
            && (event.getSlotType() == InventoryType.SlotType.RESULT || event.getRawSlot() == 2);
    }

    private void giveAnvilResult(Player player, InventoryClickEvent event, ItemStack result) {
        if (event.isShiftClick()) {
            InventoryRecipeUtil.giveOrDrop(player, result);
            return;
        }
        player.setItemOnCursor(result);
    }

    private void configureReplenishAnvil(PrepareAnvilEvent event) {
        if (!(event.getView() instanceof org.bukkit.inventory.view.AnvilView anvilView)) {
            return;
        }
        anvilView.setRepairCost(REPLENISH_ANVIL_COST);
        anvilView.setRepairItemCountCost(1);
        anvilView.setMaximumRepairCost(40);
    }

    private boolean canPayAnvilCost(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getLevel() >= REPLENISH_ANVIL_COST) {
            return true;
        }
        player.sendMessage(MessageUtil.warn("You need <white>" + REPLENISH_ANVIL_COST + "</white> XP levels to apply Replenish."));
        return false;
    }

    private void chargeAnvilCost(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        player.setLevel(Math.max(0, player.getLevel() - REPLENISH_ANVIL_COST));
    }

    private record CropReplantState(BlockData replanted, boolean mature) {}
}
