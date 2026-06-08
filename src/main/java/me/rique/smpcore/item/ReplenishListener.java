package me.rique.smpcore.item;

import me.rique.smpcore.SMPCore;
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
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
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
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String REPLENISH_LORE_LINE = "Replenish I";

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
                "<gray>Enchanting hoes at an enchant table can also grant it.</gray>",
                "<gray>Breaking supported crops replants them automatically.</gray>"
            ))
        ));
        meta.getPersistentDataContainer().set(keyReplenishBook, PersistentDataType.BYTE, (byte) 1);
        book.setItemMeta(meta);
        return book;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getFirstItem();
        ItemStack right = event.getInventory().getSecondItem();

        if (!isReplenishBook(right)) return;
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
        if (!hasReplenish(top) && !hasReplenish(bottom)) return;

        ItemStack result = event.getResult();
        if (result != null && result.getType() != Material.AIR) {
            event.setResult(stripReplenish(result.clone()));
            return;
        }

        ItemStack source = hasReplenish(top) ? top : bottom;
        if (source == null || source.getType() == Material.AIR) return;
        event.setResult(stripReplenish(source.clone()));
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
        if (!isReplenishBook(right) || !isHoe(left) || hasReplenish(left)) return;

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) return;

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

        List<ItemStack> drops = List.of();
        if (crop.mature()) {
            Material seedMaterial = seedMaterialFor(block.getType());
            if (seedMaterial == null) return;

            List<ItemStack> harvestDrops = new ArrayList<>(block.getDrops(tool, player));
            if (!consumeSeedDrop(harvestDrops, seedMaterial) && !consumeOneFromInventory(player, seedMaterial)) {
                return;
            }
            drops = harvestDrops;
        }

        event.setDropItems(false);
        Location location = block.getLocation();
        giveDrops(player, drops, location);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!block.getType().isAir()) return;
            block.setBlockData(replanted, false);
        });
    }

    private ItemStack applyReplenish(ItemStack hoe) {
        ItemMeta meta = hoe.getItemMeta();
        if (meta == null) return hoe;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyReplenishHoe, PersistentDataType.BYTE, (byte) 1);

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.removeIf(line -> REPLENISH_LORE_LINE.equalsIgnoreCase(PLAIN.serialize(line).trim()));
        lore.add(0, MM.deserialize("<gray>Replenish I</gray>"));
        meta.lore(lore);
        hoe.setItemMeta(meta);
        return hoe;
    }

    private ItemStack stripReplenish(ItemStack hoe) {
        if (!isHoe(hoe)) return hoe;
        ItemMeta meta = hoe.getItemMeta();
        if (meta == null) return hoe;

        meta.getPersistentDataContainer().remove(keyReplenishHoe);
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.removeIf(line -> REPLENISH_LORE_LINE.equalsIgnoreCase(PLAIN.serialize(line).trim()));
        meta.lore(lore.isEmpty() ? null : lore);
        hoe.setItemMeta(meta);
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
        return meta.getPersistentDataContainer().has(keyReplenishHoe, PersistentDataType.BYTE);
    }

    public boolean isReplenishEnchantDataKey(NamespacedKey key) {
        return keyReplenishHoe.equals(key);
    }

    private boolean isReplenishBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(keyReplenishBook, PersistentDataType.BYTE);
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

    private ItemStack consumeOne(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return null;
        if (stack.getAmount() <= 1) return null;
        ItemStack next = stack.clone();
        next.setAmount(stack.getAmount() - 1);
        return next;
    }

    private void giveDrops(Player player, Collection<ItemStack> drops, Location origin) {
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType() == Material.AIR) continue;
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(drop);
            leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(origin, left));
        }
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

    private void giveAnvilResult(Player player, InventoryClickEvent event, ItemStack result) {
        if (event.isShiftClick()) {
            player.getInventory().addItem(result);
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
