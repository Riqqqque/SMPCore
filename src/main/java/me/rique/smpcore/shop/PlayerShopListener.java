package me.rique.smpcore.shop;

import io.papermc.paper.event.player.PlayerOpenSignEvent;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.InventoryRecipeUtil;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.OperatorGameplayMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerShopListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String SHOP_HEADER = "[shop]";
    private static final String SHOP_HEADER_ALIAS = "[shops]";
    private static final String ADMIN_SHOP_HEADER = "[adminshop]";
    private static final String ADMIN_SHOP_SHORT_HEADER = "[ashop]";
    private static final Set<Material> CHEST_TYPES = Set.of(Material.CHEST, Material.TRAPPED_CHEST);
    private static final int[] ITEM_CHOICE_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};
    private static final int EDITOR_SIZE = 54;
    private static final int BUYER_SIZE = 27;
    private static final int HUB_SIZE = 27;

    private final SMPCore plugin;
    private final NamespacedKey keyShopSign;
    private final NamespacedKey keyShopChest;
    private final NamespacedKey keyOwnerUuid;
    private final NamespacedKey keyOwnerName;
    private final NamespacedKey keyChestBlock;
    private final NamespacedKey keySignBlock;
    private final NamespacedKey keyItem;
    private final NamespacedKey keyAmount;
    private final NamespacedKey keyPrice;
    private final NamespacedKey keyCurrency;
    private final NamespacedKey keyAdminShop;
    private final ShopPaymentLedger paymentLedger;
    private final Set<String> activeTransactions = new HashSet<>();
    private final Map<UUID, ShopDraft> shopDrafts = new HashMap<>();

    public PlayerShopListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyShopSign = new NamespacedKey(plugin, "player_shop_sign");
        this.keyShopChest = new NamespacedKey(plugin, "player_shop_chest");
        this.keyOwnerUuid = new NamespacedKey(plugin, "player_shop_owner_uuid");
        this.keyOwnerName = new NamespacedKey(plugin, "player_shop_owner_name");
        this.keyChestBlock = new NamespacedKey(plugin, "player_shop_chest_block");
        this.keySignBlock = new NamespacedKey(plugin, "player_shop_sign_block");
        this.keyItem = new NamespacedKey(plugin, "player_shop_item");
        this.keyAmount = new NamespacedKey(plugin, "player_shop_amount");
        this.keyPrice = new NamespacedKey(plugin, "player_shop_price");
        this.keyCurrency = new NamespacedKey(plugin, "player_shop_currency");
        this.keyAdminShop = new NamespacedKey(plugin, "player_shop_admin");
        this.paymentLedger = new ShopPaymentLedger(plugin);
    }

    public void start() {
        paymentLedger.start();
    }

    public void shutdown() {
        shopDrafts.clear();
        paymentLedger.shutdown();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onLegacyShopInteract(PlayerInteractEvent event) {
        if (!plugin.getConfigManager().playerShopsEnabled
            || event.getAction() != Action.RIGHT_CLICK_BLOCK
            || event.getHand() != EquipmentSlot.HAND
            || event.getClickedBlock() == null
            || isShopSign(event.getClickedBlock())
            || isMarketPurchaseSign(event.getClickedBlock())) {
            return;
        }
        MarketStallManager stalls = plugin.getMarketStallManager();
        if (stalls != null && stalls.tryRecoverLegacyShopSign(event.getClickedBlock())) {
            return;
        }
        Block signBlock = event.getClickedBlock();
        if (!(liveBlockState(signBlock) instanceof Sign) || attachedChestBlock(signBlock) == null) {
            return;
        }
        event.setCancelled(true);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        Bukkit.getScheduler().runTask(plugin, () -> openShopEditor(event.getPlayer(), signBlock, false));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (isShopSign(event.getBlock()) || isMarketPurchaseSign(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.warn(isMarketPurchaseSign(event.getBlock())
                ? "That market sign is managed by the server."
                : "Use the shop sign's management menu to make changes."));
            return;
        }
        if (!plugin.getConfigManager().playerShopsEnabled) {
            return;
        }
        String header = cleanLine(eventLine(event, 0));
        boolean adminShop = isAdminShopHeader(header);
        if (!isPlayerShopHeader(header) && !adminShop) {
            return;
        }
        Player player = event.getPlayer();
        if (adminShop && !hasPassiveAdminAccess(player)) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.error("You do not have permission to create admin shops."));
            return;
        }
        if (!player.hasPermission("smpcore.shop") && !hasPassiveAdminAccess(player)) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.error("You do not have permission to create shops."));
            return;
        }
        if (event.getSide() != Side.FRONT) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.warn("Create shops on the front side of the sign."));
            return;
        }

        Block signBlock = event.getBlock();
        if (isMarketPurchaseSign(signBlock)) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.warn("That sign is reserved for purchasing the market stall."));
            return;
        }
        Block chestBlock = attachedChestBlock(signBlock);
        if (chestBlock == null) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.warn("Attach the shop sign to the front of a chest or double chest."));
            return;
        }
        if (plugin.getMarketStallManager() != null
            && !plugin.getMarketStallManager().canCreateShopAt(player, chestBlock, signBlock)) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.warn("Inside a stall you manage, attach a wall sign directly to a chest or trapped chest."));
            return;
        }
        if (isShopChest(chestBlock) || isShopSign(signBlock)) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.warn("That chest already has a shop."));
            return;
        }
        event.line(0, Component.text(adminShop ? "[Admin Shop]" : "[Shop]", NamedTextColor.DARK_GREEN));
        event.line(1, Component.text("Right-click", NamedTextColor.GOLD));
        event.line(2, Component.text("to set up", NamedTextColor.GRAY));
        event.line(3, Component.empty());
        Bukkit.getScheduler().runTask(plugin, () -> {
            openShopEditor(player, signBlock, adminShop);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onShopSignOpen(PlayerOpenSignEvent event) {
        Block block = event.getSign().getBlock();
        if (!isShopSign(block)) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(MessageUtil.warn(canManageShop(event.getPlayer(), block)
            ? "Right-click the sign to manage this shop."
            : "Only the shop owner or an admin can edit that sign."));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.getConfigManager().playerShopsEnabled
            || event.getAction() != Action.RIGHT_CLICK_BLOCK
            || event.getHand() != EquipmentSlot.HAND
            || event.getClickedBlock() == null) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (shouldHandleShopPurchase(isShopSign(clicked), isMarketPurchaseSign(clicked))) {
            event.setCancelled(true);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            if (MarketStallManager.isSignModificationItem(event.getItem() == null ? null : event.getItem().getType())) {
                event.getPlayer().sendMessage(MessageUtil.warn("Shop signs cannot be dyed, waxed, or rewritten."));
                return;
            }
            if (canManageShop(event.getPlayer(), clicked)) {
                openShopEditor(event.getPlayer(), clicked, false);
            } else {
                openBuyerMenu(event.getPlayer(), clicked);
            }
            return;
        }
        Block shopChest = protectedShopChestBlock(clicked);
        if (shopChest == null) {
            return;
        }
        if (canManageShop(event.getPlayer(), shopChest)) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(MessageUtil.warn("Right-click the shop sign to open its menu."));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!plugin.getConfigManager().playerShopsEnabled || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        Block chestBlock = shopChestFromInventory(event.getInventory());
        if (chestBlock == null || canManageShop(player, chestBlock)) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(MessageUtil.warn("Right-click the shop sign to open its menu."));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!CHEST_TYPES.contains(event.getBlockPlaced().getType())) {
            return;
        }
        for (BlockFace face : horizontalFaces()) {
            Block adjacent = event.getBlockPlaced().getRelative(face);
            Block shopChest = protectedShopChestBlock(adjacent);
            if (shopChest != null) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(MessageUtil.warn("Break and recreate the shop if you need to change it between single and double chest."));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (isMarketPurchaseSign(block)) return;
        boolean shopSign = isShopSign(block);
        Block shopChest = protectedShopChestBlock(block);
        if (!shopSign && shopChest == null) {
            return;
        }
        Block protectedBlock = shopSign ? block : shopChest;
        if (!canManageShop(event.getPlayer(), protectedBlock)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.warn("Only the shop owner or an admin can break that shop."));
            return;
        }
        clearShop(protectedBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (shopChestFromInventory(event.getSource()) != null || shopChestFromInventory(event.getDestination()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> isShopSign(block) || isShopChest(block));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isShopSign(block) || isShopChest(block));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (isProtectedShopBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (containsProtectedShopBlock(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (containsProtectedShopBlock(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    public void openShopHub(Player player) {
        if (player == null || !plugin.getConfigManager().playerShopsEnabled) {
            return;
        }
        Inventory menu = Bukkit.createInventory(
            new ShopMenuHolder(player.getUniqueId(), ShopMenuView.HUB, "", false),
            HUB_SIZE,
            BedrockCompat.menuTitle(player, Component.text("Player Shops", NamedTextColor.DARK_GREEN), "Player Shops")
        );
        fillMenu(menu);
        menu.setItem(4, menuItem(
            Material.CHEST,
            Component.text("Chest Shops", NamedTextColor.GREEN),
            List.of(
                Component.text("1. Put stock in a chest.", NamedTextColor.GRAY),
                Component.text("2. Attach a wall sign.", NamedTextColor.GRAY),
                Component.text("3. Right-click the sign.", NamedTextColor.GRAY),
                Component.text("All setup is done in the menu.", NamedTextColor.YELLOW)
            )
        ));
        List<String> waiting = paymentSummary(player);
        menu.setItem(11, menuItem(
            Material.BELL,
            Component.text("Waiting Payments", NamedTextColor.GOLD),
            waiting.isEmpty()
                ? List.of(Component.text("No payments are waiting.", NamedTextColor.GRAY))
                : List.of(
                    Component.text(String.join(", ", waiting), NamedTextColor.WHITE),
                    Component.text("Tap Collect Payments to claim them.", NamedTextColor.YELLOW)
                )
        ));
        menu.setItem(15, menuItem(
            Material.EMERALD,
            Component.text("Collect Payments", NamedTextColor.GREEN),
            List.of(
                Component.text("Payments stay safe outside stock chests.", NamedTextColor.GRAY),
                Component.text("Tap to collect everything that fits.", NamedTextColor.YELLOW)
            )
        ));
        menu.setItem(22, menuItem(
            Material.WRITABLE_BOOK,
            Component.text("How Buying Works", NamedTextColor.AQUA),
            List.of(
                Component.text("Right-click a shop sign to see its offer.", NamedTextColor.GRAY),
                Component.text("Confirm each purchase in the menu.", NamedTextColor.GRAY),
                Component.text("Currencies include materials and Essence.", NamedTextColor.GRAY)
            )
        ));
        menu.setItem(26, closeButton());
        player.openInventory(menu);
    }

    private void openShopEditor(Player player, Block signBlock, boolean requestedAdminShop) {
        if (player == null || signBlock == null || !plugin.getConfigManager().playerShopsEnabled) {
            return;
        }
        ShopData existing = readShopData(signBlock);
        Block chestBlock = existing == null ? attachedChestBlock(signBlock) : blockFromKey(existing.chestBlock());
        String problem = validateShopEditorAccess(player, signBlock, chestBlock, existing, requestedAdminShop);
        if (problem != null) {
            player.sendMessage(MessageUtil.warn(problem));
            return;
        }

        ShopDraft draft = shopDrafts.get(player.getUniqueId());
        if (draft == null || !draft.signBlock().equals(blockKey(signBlock))) {
            ItemStack prototype = existing == null
                ? firstChestItem(shopInventory(chestBlock))
                : decodeItem(existing.encodedItem());
            if (prototype == null || prototype.getType().isAir()) {
                player.sendMessage(MessageUtil.warn("Put the item you want to sell in the chest first."));
                return;
            }
            draft = new ShopDraft(
                blockKey(signBlock),
                prototype.asOne(),
                existing == null ? 1 : existing.amount(),
                existing == null ? 1 : existing.price(),
                existing == null ? ShopCurrency.ESSENCE : existing.currency(),
                existing == null ? requestedAdminShop : existing.adminShop(),
                existing != null
            );
            List<ItemStack> choices = distinctChestItems(shopInventory(chestBlock));
            for (int index = 0; index < choices.size(); index++) {
                if (choices.get(index).isSimilar(draft.prototype())) {
                    draft.itemPage(index / ITEM_CHOICE_SLOTS.length);
                    break;
                }
            }
            shopDrafts.put(player.getUniqueId(), draft);
        }
        openShopEditor(player, draft, false);
    }

    private void openShopEditor(Player player, ShopDraft draft, boolean confirmClose) {
        Block signBlock = blockFromKey(draft.signBlock());
        ShopData existing = signBlock == null ? null : readShopData(signBlock);
        Block chestBlock = existing == null ? attachedChestBlock(signBlock) : blockFromKey(existing.chestBlock());
        String problem = validateShopEditorAccess(player, signBlock, chestBlock, existing, draft.adminShop());
        if (problem != null) {
            shopDrafts.remove(player.getUniqueId());
            player.closeInventory();
            player.sendMessage(MessageUtil.warn(problem));
            return;
        }

        Inventory menu = Bukkit.createInventory(
            new ShopMenuHolder(player.getUniqueId(), ShopMenuView.EDITOR, draft.signBlock(), confirmClose),
            EDITOR_SIZE,
            BedrockCompat.menuTitle(
                player,
                Component.text(existing == null ? "Create Chest Shop" : "Manage Chest Shop", NamedTextColor.DARK_GREEN),
                existing == null ? "Create Shop" : "Manage Shop"
            )
        );
        fillMenu(menu);
        menu.setItem(4, menuItem(
            Material.OAK_SIGN,
            Component.text(existing == null ? "Create Chest Shop" : "Manage Chest Shop", NamedTextColor.GREEN),
            List.of(
                Component.text("Owner: " + shopOwnerName(player, chestBlock, signBlock, existing), NamedTextColor.GRAY),
                Component.text("Choose an item already in the stock chest.", NamedTextColor.GRAY),
                Component.text("Every button works with a normal tap.", NamedTextColor.YELLOW)
            )
        ));

        List<ItemStack> choices = distinctChestItems(shopInventory(chestBlock));
        int itemPages = Math.max(1, (choices.size() + ITEM_CHOICE_SLOTS.length - 1) / ITEM_CHOICE_SLOTS.length);
        draft.itemPage(Math.max(0, Math.min(draft.itemPage(), itemPages - 1)));
        int itemOffset = draft.itemPage() * ITEM_CHOICE_SLOTS.length;
        for (int index = 0; index < ITEM_CHOICE_SLOTS.length; index++) {
            int slot = ITEM_CHOICE_SLOTS[index];
            int choiceIndex = itemOffset + index;
            if (choiceIndex >= choices.size()) {
                menu.setItem(slot, menuItem(
                    Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    Component.text("Empty Stock Slot", NamedTextColor.DARK_GRAY),
                    List.of(Component.text("Add another item type to the chest.", NamedTextColor.GRAY))
                ));
                continue;
            }
            ItemStack choice = choices.get(choiceIndex).asOne();
            boolean selected = choice.isSimilar(draft.prototype());
            menu.setItem(slot, choiceItem(choice, selected));
        }
        if (draft.itemPage() > 0) {
            menu.setItem(18, menuItem(
                Material.ARROW,
                Component.text("Previous Items", NamedTextColor.YELLOW),
                List.of(Component.text("Page " + draft.itemPage() + " of " + itemPages, NamedTextColor.GRAY))
            ));
        }
        if (draft.itemPage() + 1 < itemPages) {
            menu.setItem(26, menuItem(
                Material.ARROW,
                Component.text("More Items", NamedTextColor.YELLOW),
                List.of(Component.text("Page " + (draft.itemPage() + 2) + " of " + itemPages, NamedTextColor.GRAY))
            ));
        }

        menu.setItem(20, adjustButton(Material.RED_STAINED_GLASS_PANE, "-10 Items"));
        menu.setItem(21, adjustButton(Material.RED_STAINED_GLASS_PANE, "-1 Item"));
        menu.setItem(22, menuItem(
            draft.prototype().getType(),
            Component.text("Amount: " + draft.amount(), NamedTextColor.GOLD),
            List.of(
                Component.text("Items delivered per purchase.", NamedTextColor.GRAY),
                Component.text("Maximum: " + plugin.getConfigManager().playerShopsMaxAmountPerPurchase, NamedTextColor.DARK_GRAY)
            )
        ));
        menu.setItem(23, adjustButton(Material.LIME_STAINED_GLASS_PANE, "+1 Item"));
        menu.setItem(24, adjustButton(Material.LIME_STAINED_GLASS_PANE, "+10 Items"));

        menu.setItem(28, adjustButton(Material.RED_STAINED_GLASS_PANE, "-100 Price"));
        menu.setItem(29, adjustButton(Material.RED_STAINED_GLASS_PANE, "-10 Price"));
        menu.setItem(30, adjustButton(Material.RED_STAINED_GLASS_PANE, "-1 Price"));
        menu.setItem(31, menuItem(
            currencyIcon(draft.currency()),
            Component.text("Price: " + draft.price() + " " + draft.currency().display(draft.price()), NamedTextColor.GOLD),
            List.of(
                Component.text("Cost of one purchase.", NamedTextColor.GRAY),
                Component.text("Maximum: " + plugin.getConfigManager().playerShopsMaxPrice, NamedTextColor.DARK_GRAY)
            )
        ));
        menu.setItem(32, adjustButton(Material.LIME_STAINED_GLASS_PANE, "+1 Price"));
        menu.setItem(33, adjustButton(Material.LIME_STAINED_GLASS_PANE, "+10 Price"));
        menu.setItem(34, adjustButton(Material.LIME_STAINED_GLASS_PANE, "+100 Price"));

        menu.setItem(38, menuItem(
            Material.ARROW,
            Component.text("Previous Currency", NamedTextColor.YELLOW),
            List.of(Component.text(previousCurrency(draft.currency()).display(2), NamedTextColor.GRAY))
        ));
        menu.setItem(40, menuItem(
            currencyIcon(draft.currency()),
            Component.text(draft.currency().display(2), NamedTextColor.AQUA),
            List.of(Component.text("Current payment currency.", NamedTextColor.GRAY))
        ));
        menu.setItem(42, menuItem(
            Material.ARROW,
            Component.text("Next Currency", NamedTextColor.YELLOW),
            List.of(Component.text(nextCurrency(draft.currency()).display(2), NamedTextColor.GRAY))
        ));

        menu.setItem(45, closeButton());
        if (existing != null && !blocksOwnerPurchase(
            existing.adminShop(),
            plugin.getConfigManager().playerShopsAllowOwnerPurchases,
            player.getUniqueId(),
            existing.ownerUuid()
        )) {
            menu.setItem(46, menuItem(
                Material.MINECART,
                Component.text("Shop as Customer", NamedTextColor.AQUA),
                List.of(
                    Component.text("Open the same purchase menu buyers see.", NamedTextColor.GRAY),
                    Component.text("Trusted managers still pay normally.", NamedTextColor.YELLOW)
                )
            ));
        }
        if (hasPassiveAdminAccess(player)) {
            menu.setItem(47, menuItem(
                draft.adminShop() ? Material.COMMAND_BLOCK : Material.CHEST,
                Component.text(draft.adminShop() ? "Admin Shop: ON" : "Admin Shop: OFF",
                    draft.adminShop() ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GRAY),
                List.of(
                    Component.text(draft.adminShop()
                        ? "Infinite stock; payment is removed."
                        : "Uses chest stock; payment goes to the owner.", NamedTextColor.GRAY),
                    Component.text("Tap to switch.", NamedTextColor.YELLOW)
                )
            ));
        }
        menu.setItem(49, menuItem(
            Material.LIME_CONCRETE,
            Component.text(existing == null ? "Create Shop" : "Save Changes", NamedTextColor.GREEN),
            List.of(
                Component.text(draft.amount() + "x " + displayName(draft.prototype()), NamedTextColor.WHITE),
                Component.text("for " + draft.price() + " " + draft.currency().display(draft.price()), NamedTextColor.WHITE),
                Component.text("Tap to validate and save.", NamedTextColor.YELLOW)
            )
        ));
        if (existing != null && existing.ownerUuid().equals(player.getUniqueId())) {
            menu.setItem(51, menuItem(
                Material.EMERALD,
                Component.text("Collect Payments", NamedTextColor.GREEN),
                List.of(Component.text("Claim waiting shop sales.", NamedTextColor.GRAY))
            ));
            menu.setItem(53, menuItem(
                confirmClose ? Material.TNT : Material.BARRIER,
                Component.text(confirmClose ? "Tap Again: Close Shop" : "Close Shop", NamedTextColor.RED),
                List.of(Component.text(confirmClose
                    ? "This removes the shop but leaves its stock."
                    : "Requires a second confirmation tap.", NamedTextColor.GRAY))
            ));
        }
        player.openInventory(menu);
    }

    private void openBuyerMenu(Player player, Block signBlock) {
        ShopData data = readShopData(signBlock);
        if (data == null) {
            player.sendMessage(MessageUtil.error("That shop is missing data. Ask the owner to recreate it."));
            return;
        }
        Block chestBlock = blockFromKey(data.chestBlock());
        Inventory stock = shopInventory(chestBlock);
        ItemStack prototype = decodeItem(data.encodedItem());
        if (stock == null || prototype == null || isLegendaryPrototype(prototype)) {
            player.sendMessage(MessageUtil.error("That shop cannot be opened safely."));
            return;
        }
        Inventory menu = Bukkit.createInventory(
            new ShopMenuHolder(player.getUniqueId(), ShopMenuView.BUYER, blockKey(signBlock), false),
            BUYER_SIZE,
            BedrockCompat.menuTitle(player, Component.text("Chest Shop", NamedTextColor.DARK_GREEN), "Chest Shop")
        );
        fillMenu(menu);
        int available = data.adminShop() ? -1 : countSimilar(stock.getStorageContents(), prototype);
        menu.setItem(4, menuItem(
            Material.OAK_SIGN,
            Component.text(data.adminShop() ? "Server Shop" : data.ownerName() + "'s Shop", NamedTextColor.GREEN),
            List.of(
                Component.text("Offer: " + data.amount() + "x " + data.itemName(), NamedTextColor.WHITE),
                Component.text("Price: " + data.price() + " " + data.currency().display(data.price()), NamedTextColor.GOLD)
            )
        ));
        ItemStack preview = prototype.asQuantity(Math.min(data.amount(), prototype.getMaxStackSize()));
        menu.setItem(13, preview);
        menu.setItem(10, menuItem(
            available < 0 ? Material.ENDER_CHEST : available >= data.amount() ? Material.CHEST : Material.BARRIER,
            Component.text(available < 0 ? "Infinite Stock" : "Stock: " + available,
                available < 0 || available >= data.amount() ? NamedTextColor.GREEN : NamedTextColor.RED),
            List.of(Component.text(available < 0
                ? "This server shop never runs out."
                : available / Math.max(1, data.amount()) + " purchase(s) available.", NamedTextColor.GRAY))
        ));
        long funds = data.currency().isEssence()
            ? plugin.getEssenceManager() == null ? 0L : plugin.getEssenceManager().balance(player)
            : countMaterial(player.getInventory().getStorageContents(), data.currency().material());
        menu.setItem(16, menuItem(
            currencyIcon(data.currency()),
            Component.text("You Have: " + funds, funds >= data.price() ? NamedTextColor.GREEN : NamedTextColor.RED),
            List.of(Component.text(data.currency().display(funds), NamedTextColor.GRAY))
        ));
        menu.setItem(22, menuItem(
            funds >= data.price() && (available < 0 || available >= data.amount()) ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
            Component.text("Buy Once", NamedTextColor.YELLOW),
            List.of(
                Component.text(data.amount() + "x " + data.itemName(), NamedTextColor.WHITE),
                Component.text("Tap to confirm this purchase.", NamedTextColor.YELLOW),
                Component.text("The menu stays open for repeat buys.", NamedTextColor.GRAY)
            )
        ));
        menu.setItem(26, closeButton());
        player.openInventory(menu);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onShopMenuClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof ShopMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
            || !holder.playerId().equals(player.getUniqueId())
            || event.getRawSlot() < 0
            || event.getRawSlot() >= top.getSize()) {
            return;
        }
        int slot = event.getRawSlot();
        if (holder.view() == ShopMenuView.HUB) {
            if (slot == 15) {
                collectPayments(player);
                Bukkit.getScheduler().runTask(plugin, () -> openShopHub(player));
            } else if (slot == 26) {
                player.closeInventory();
            }
            return;
        }
        if (holder.view() == ShopMenuView.BUYER) {
            if (slot == 22) {
                Block signBlock = blockFromKey(holder.signBlock());
                if (signBlock != null) {
                    buyFromShop(player, signBlock);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) openBuyerMenu(player, signBlock);
                    });
                }
            } else if (slot == 26) {
                player.closeInventory();
            }
            return;
        }

        ShopDraft draft = shopDrafts.get(player.getUniqueId());
        if (draft == null || !draft.signBlock().equals(holder.signBlock())) {
            player.closeInventory();
            player.sendMessage(MessageUtil.warn("That shop editor expired. Right-click the sign again."));
            return;
        }
        if (slot >= 9 && slot <= 17) {
            Block signBlock = blockFromKey(draft.signBlock());
            ShopData existing = signBlock == null ? null : readShopData(signBlock);
            Block chestBlock = existing == null ? attachedChestBlock(signBlock) : blockFromKey(existing.chestBlock());
            List<ItemStack> choices = distinctChestItems(shopInventory(chestBlock));
            int index = draft.itemPage() * ITEM_CHOICE_SLOTS.length + slot - 9;
            if (index < choices.size()) draft.prototype(choices.get(index).asOne());
        } else if (slot == 18 && draft.itemPage() > 0) {
            draft.itemPage(draft.itemPage() - 1);
        } else if (slot == 26) {
            draft.itemPage(draft.itemPage() + 1);
        } else if (slot == 20) {
            draft.amount(adjustBounded(draft.amount(), -10, plugin.getConfigManager().playerShopsMaxAmountPerPurchase));
        } else if (slot == 21) {
            draft.amount(adjustBounded(draft.amount(), -1, plugin.getConfigManager().playerShopsMaxAmountPerPurchase));
        } else if (slot == 23) {
            draft.amount(adjustBounded(draft.amount(), 1, plugin.getConfigManager().playerShopsMaxAmountPerPurchase));
        } else if (slot == 24) {
            draft.amount(adjustBounded(draft.amount(), 10, plugin.getConfigManager().playerShopsMaxAmountPerPurchase));
        } else if (slot == 28) {
            draft.price(adjustBounded(draft.price(), -100, plugin.getConfigManager().playerShopsMaxPrice));
        } else if (slot == 29) {
            draft.price(adjustBounded(draft.price(), -10, plugin.getConfigManager().playerShopsMaxPrice));
        } else if (slot == 30) {
            draft.price(adjustBounded(draft.price(), -1, plugin.getConfigManager().playerShopsMaxPrice));
        } else if (slot == 32) {
            draft.price(adjustBounded(draft.price(), 1, plugin.getConfigManager().playerShopsMaxPrice));
        } else if (slot == 33) {
            draft.price(adjustBounded(draft.price(), 10, plugin.getConfigManager().playerShopsMaxPrice));
        } else if (slot == 34) {
            draft.price(adjustBounded(draft.price(), 100, plugin.getConfigManager().playerShopsMaxPrice));
        } else if (slot == 38) {
            draft.currency(previousCurrency(draft.currency()));
        } else if (slot == 42) {
            draft.currency(nextCurrency(draft.currency()));
        } else if (slot == 45) {
            player.closeInventory();
            return;
        } else if (slot == 46 && draft.existing()) {
            Block signBlock = blockFromKey(draft.signBlock());
            if (signBlock != null) openBuyerMenu(player, signBlock);
            return;
        } else if (slot == 47 && hasPassiveAdminAccess(player)) {
            draft.adminShop(!draft.adminShop());
        } else if (slot == 49) {
            saveShopDraft(player, draft);
            return;
        } else if (slot == 51 && draft.existing()) {
            Block signBlock = blockFromKey(draft.signBlock());
            ShopData existing = signBlock == null ? null : readShopData(signBlock);
            if (existing == null || !existing.ownerUuid().equals(player.getUniqueId())) {
                player.sendMessage(MessageUtil.warn("Only the shop owner can collect its payments."));
                return;
            }
            collectPayments(player);
        } else if (slot == 53 && draft.existing()) {
            if (!holder.confirmClose()) {
                openShopEditor(player, draft, true);
                return;
            }
            Block signBlock = blockFromKey(draft.signBlock());
            if (signBlock != null && canManageShop(player, signBlock)) {
                clearShop(signBlock);
                shopDrafts.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(MessageUtil.success("Shop closed. Its stock was left in the chest."));
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8f, 0.8f);
            }
            return;
        }
        openShopEditor(player, draft, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onShopMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof ShopMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onShopMenuClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof ShopMenuHolder holder)
            || holder.view() != ShopMenuView.EDITOR
            || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!(player.getOpenInventory().getTopInventory().getHolder(false) instanceof ShopMenuHolder next)
                || next.view() != ShopMenuView.EDITOR) {
                shopDrafts.remove(player.getUniqueId());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onShopEditorQuit(PlayerQuitEvent event) {
        shopDrafts.remove(event.getPlayer().getUniqueId());
    }

    private void saveShopDraft(Player player, ShopDraft draft) {
        Block signBlock = blockFromKey(draft.signBlock());
        ShopData existing = signBlock == null ? null : readShopData(signBlock);
        Block chestBlock = existing == null ? attachedChestBlock(signBlock) : blockFromKey(existing.chestBlock());
        String problem = validateShopEditorAccess(player, signBlock, chestBlock, existing, draft.adminShop());
        if (problem != null) {
            player.sendMessage(MessageUtil.warn(problem));
            return;
        }
        if (draft.prototype() == null || draft.prototype().getType().isAir()) {
            player.sendMessage(MessageUtil.warn("Choose a valid item from the chest."));
            return;
        }
        ItemStack storedPrototype = existing == null ? null : decodeItem(existing.encodedItem());
        boolean keepsStoredItem = storedPrototype != null && storedPrototype.isSimilar(draft.prototype());
        if (!keepsStoredItem && !containsSimilar(shopInventory(chestBlock), draft.prototype())) {
            player.sendMessage(MessageUtil.warn("The selected item is no longer in the chest."));
            openShopEditor(player, draft, false);
            return;
        }
        if (isLegendaryPrototype(draft.prototype())) {
            player.sendMessage(MessageUtil.warn("Legendary items cannot be sold through shops."));
            return;
        }
        String encoded = encodeItem(draft.prototype().asOne());
        if (encoded == null) {
            player.sendMessage(MessageUtil.error("That item could not be stored safely."));
            return;
        }
        MarketStallManager.ShopIdentity identity = existing == null ? shopIdentity(player, chestBlock, signBlock) : null;
        UUID ownerId = existing == null
            ? draft.adminShop() ? player.getUniqueId() : identity == null ? null : identity.ownerId()
            : existing.ownerUuid();
        String ownerName = existing == null
            ? draft.adminShop() ? player.getName() : identity == null ? null : identity.ownerName()
            : existing.ownerName();
        if (ownerId == null || ownerName == null) {
            player.sendMessage(MessageUtil.warn("You no longer have permission to create a shop there."));
            return;
        }
        ShopData data = new ShopData(
            ownerId,
            ownerName,
            blockKey(chestBlock),
            blockKey(signBlock),
            encoded,
            displayName(draft.prototype()),
            draft.amount(),
            draft.price(),
            draft.currency(),
            draft.adminShop()
        );
        if (!writeShopData(signBlock, chestBlock, data)) {
            player.sendMessage(MessageUtil.error("The shop could not be saved. Its previous state was left alone."));
            return;
        }
        shopDrafts.remove(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(MessageUtil.success(existing == null ? "Shop created." : "Shop changes saved."));
        player.sendMessage(MessageUtil.info("Players can now right-click the sign to view and confirm purchases."));
        signBlock.getWorld().playSound(signBlock.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.4f);
    }

    private String validateShopEditorAccess(
        Player player,
        Block signBlock,
        Block chestBlock,
        ShopData existing,
        boolean adminShop
    ) {
        if (signBlock == null || !(liveBlockState(signBlock) instanceof Sign)) {
            return "That shop sign no longer exists.";
        }
        if (isMarketPurchaseSign(signBlock)) {
            return "That sign is reserved for the market stall.";
        }
        if (chestBlock == null || shopInventory(chestBlock) == null) {
            return "Attach the wall sign directly to a chest or double chest.";
        }
        if (adminShop && !hasPassiveAdminAccess(player)) {
            return "You do not have permission to create admin shops.";
        }
        if (!player.hasPermission("smpcore.shop") && !hasPassiveAdminAccess(player)) {
            return "You do not have permission to create shops.";
        }
        if (existing != null) {
            return canManageShop(player, signBlock) ? null : "Only the shop owner or an admin can manage that shop.";
        }
        if (isShopSign(signBlock) || isShopChest(chestBlock)) {
            return "That chest already belongs to a shop.";
        }
        if (plugin.getMarketStallManager() != null
            && !plugin.getMarketStallManager().canCreateShopAt(player, chestBlock, signBlock)) {
            return "You can only create shops on chests inside a stall you manage.";
        }
        return shopIdentity(player, chestBlock, signBlock) == null
            ? "You no longer have permission to manage this stall."
            : null;
    }

    private MarketStallManager.ShopIdentity shopIdentity(Player player, Block chestBlock, Block signBlock) {
        return plugin.getMarketStallManager() == null
            ? new MarketStallManager.ShopIdentity(player.getUniqueId(), player.getName())
            : plugin.getMarketStallManager().shopIdentityFor(player, chestBlock, signBlock);
    }

    private String shopOwnerName(Player player, Block chestBlock, Block signBlock, ShopData existing) {
        if (existing != null) {
            return existing.ownerName();
        }
        MarketStallManager.ShopIdentity identity = shopIdentity(player, chestBlock, signBlock);
        return identity == null ? player.getName() : identity.ownerName();
    }

    private List<ItemStack> distinctChestItems(Inventory inventory) {
        List<ItemStack> distinct = new ArrayList<>();
        if (inventory == null) return distinct;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0 || isLegendaryPrototype(item)) continue;
            boolean alreadyPresent = distinct.stream().anyMatch(existing -> existing.isSimilar(item));
            if (!alreadyPresent) distinct.add(item.asOne());
        }
        return distinct;
    }

    private boolean containsSimilar(Inventory inventory, ItemStack prototype) {
        return inventory != null && countSimilar(inventory.getStorageContents(), prototype) > 0;
    }

    private ItemStack choiceItem(ItemStack prototype, boolean selected) {
        ItemStack choice = prototype.asOne();
        ItemMeta meta = choice.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(Component.text(selected ? "Selected" : "Tap to sell this item",
            selected ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        meta.lore(lore);
        choice.setItemMeta(meta);
        return choice;
    }

    private ItemStack adjustButton(Material material, String name) {
        return menuItem(material, Component.text(name, NamedTextColor.YELLOW), List.of(Component.text("Tap to adjust.", NamedTextColor.GRAY)));
    }

    private ItemStack closeButton() {
        return menuItem(Material.BARRIER, Component.text("Close", NamedTextColor.RED), List.of());
    }

    private ItemStack menuItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void fillMenu(Inventory inventory) {
        ItemStack filler = menuItem(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private Material currencyIcon(ShopCurrency currency) {
        return currency.isEssence() ? Material.AMETHYST_SHARD : currency.material();
    }

    static int adjustBounded(int current, int delta, int maximum) {
        long adjusted = (long) current + delta;
        return (int) Math.max(1L, Math.min(Math.max(1, maximum), adjusted));
    }

    static ShopCurrency nextCurrency(ShopCurrency currency) {
        ShopCurrency[] values = ShopCurrency.values();
        return values[(currency.ordinal() + 1) % values.length];
    }

    static ShopCurrency previousCurrency(ShopCurrency currency) {
        ShopCurrency[] values = ShopCurrency.values();
        return values[(currency.ordinal() + values.length - 1) % values.length];
    }

    private void buyFromShop(Player buyer, Block signBlock) {
        String transactionKey = blockKey(signBlock);
        if (!activeTransactions.add(transactionKey)) {
            buyer.sendMessage(MessageUtil.warn("That shop is finishing another purchase. Try again."));
            return;
        }
        try {
            executePurchase(buyer, signBlock);
        } finally {
            activeTransactions.remove(transactionKey);
        }
    }

    private void executePurchase(Player buyer, Block signBlock) {
        if (!shouldHandleShopPurchase(isShopSign(signBlock), isMarketPurchaseSign(signBlock))) {
            return;
        }
        ShopData data = readShopData(signBlock);
        if (data == null) {
            buyer.sendMessage(MessageUtil.error("That shop is missing data. Ask the owner to recreate it."));
            return;
        }
        refreshShopSignLines(signBlock, data);
        if (blocksOwnerPurchase(
            data.adminShop(),
            plugin.getConfigManager().playerShopsAllowOwnerPurchases,
            buyer.getUniqueId(),
            data.ownerUuid()
        )) {
            buyer.sendMessage(MessageUtil.warn("You cannot buy from your own shop."));
            return;
        }

        Block chestBlock = blockFromKey(data.chestBlock());
        Inventory shopInventory = chestBlock == null ? null : shopInventory(chestBlock);
        ItemStack prototype = decodeItem(data.encodedItem());
        if (shopInventory == null || prototype == null) {
            buyer.sendMessage(MessageUtil.error("That shop is broken. Ask the owner to recreate it."));
            clearShop(signBlock);
            return;
        }
        if (isLegendaryPrototype(prototype)) {
            buyer.sendMessage(MessageUtil.warn("Legendary items cannot be sold through shops."));
            return;
        }

        List<ItemStack> boughtItems = stacksOf(prototype, data.amount());
        if (!data.adminShop() && countSimilar(shopInventory.getStorageContents(), prototype) < data.amount()) {
            buyer.sendMessage(MessageUtil.warn("That shop is out of stock."));
            return;
        }
        if (data.currency().isEssence()) {
            if (plugin.getEssenceManager() == null || !plugin.getEssenceManager().isLoaded(buyer)) {
                buyer.sendMessage(MessageUtil.warn("Your Essence is still loading. Try again in a moment."));
                return;
            }
            if (plugin.getEssenceManager().balance(buyer) < data.price()) {
                buyer.sendMessage(MessageUtil.warn("You need " + data.price() + " Essence."));
                return;
            }
        } else if (countMaterial(buyer.getInventory().getStorageContents(), data.currency().material()) < data.price()) {
            buyer.sendMessage(MessageUtil.warn("You need " + data.price() + " " + data.currency().display(data.price()) + "."));
            return;
        }
        if (!canBuyerFitAfterPayment(buyer.getInventory(), data.currency(), data.price(), boughtItems)) {
            buyer.sendMessage(MessageUtil.warn("Clear inventory space before buying this."));
            return;
        }

        boolean charged;
        if (data.currency().isEssence()) {
            charged = plugin.getEssenceManager() != null
                && plugin.getEssenceManager().spend(buyer, data.price(), "player shop purchase");
        } else {
            removeMaterial(buyer.getInventory(), data.currency().material(), data.price());
            charged = true;
        }
        if (!charged) {
            buyer.sendMessage(MessageUtil.warn("The payment changed before this purchase finished. Nothing was bought."));
            return;
        }
        if (!data.adminShop() && !paymentLedger.credit(data.ownerUuid(), data.ownerName(), data.currency(), data.price())) {
            refundPayment(buyer, data.currency(), data.price());
            buyer.sendMessage(MessageUtil.error("That shop cannot accept the payment right now. Your payment was returned."));
            return;
        }
        if (!data.adminShop()) {
            removeSimilar(shopInventory, prototype, data.amount());
        }
        boughtItems.forEach(item -> buyer.getInventory().addItem(item).values().forEach(leftover ->
            buyer.getWorld().dropItemNaturally(buyer.getLocation(), leftover)
        ));
        buyer.updateInventory();
        buyer.sendMessage(MessageUtil.success("Bought <white>" + data.amount() + "x " + safe(data.itemName()) + "</white> for <white>" + data.price() + " " + data.currency().display(data.price()) + "</white>."));
        buyer.playSound(buyer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.65f, 1.35f);
        if (!data.adminShop()) {
            Player owner = Bukkit.getPlayer(data.ownerUuid());
            if (owner != null && owner.isOnline() && !owner.getUniqueId().equals(buyer.getUniqueId())) {
                owner.sendMessage(MessageUtil.info("Your shop sold <white>" + data.amount() + "x " + safe(data.itemName())
                    + "</white>. Use <white>/shops collect</white> for the payment."));
            }
        }
    }

    private void refundPayment(Player buyer, ShopCurrency currency, int amount) {
        if (currency.isEssence()) {
            if (plugin.getEssenceManager() == null
                || !plugin.getEssenceManager().credit(buyer, amount, "failed player shop purchase refund")) {
                plugin.getLogger().severe("Could not return " + amount + " Essence after a failed shop purchase for " + buyer.getName() + ".");
            }
            return;
        }

        for (ItemStack refund : stacksOf(new ItemStack(currency.material()), amount)) {
            buyer.getInventory().addItem(refund).values().forEach(leftover ->
                buyer.getWorld().dropItemNaturally(buyer.getLocation(), leftover)
            );
        }
    }

    private boolean writeShopData(Block signBlock, Block chestBlock, ShopData data) {
        BlockState signState = signBlock.getState();
        if (!(signState instanceof Sign sign)) {
            return false;
        }
        writeSignPdc(sign, data);
        writeStoredSignLines(sign, data);
        sign.setWaxed(true);
        sign.update(true, false);

        for (Block shopChest : shopChestBlocks(chestBlock)) {
            BlockState state = shopChest.getState();
            if (state instanceof TileState tile) {
                PersistentDataContainer pdc = tile.getPersistentDataContainer();
                pdc.set(keyShopChest, PersistentDataType.BYTE, (byte) 1);
                pdc.set(keyOwnerUuid, PersistentDataType.STRING, data.ownerUuid().toString());
                pdc.set(keyOwnerName, PersistentDataType.STRING, data.ownerName());
                pdc.set(keySignBlock, PersistentDataType.STRING, data.signBlock());
                pdc.set(keyAdminShop, PersistentDataType.BYTE, data.adminShop() ? (byte) 1 : (byte) 0);
                tile.update(true, false);
            }
        }
        return true;
    }

    private void writeSignPdc(Sign sign, ShopData data) {
        PersistentDataContainer pdc = sign.getPersistentDataContainer();
        pdc.set(keyShopSign, PersistentDataType.BYTE, (byte) 1);
        pdc.set(keyOwnerUuid, PersistentDataType.STRING, data.ownerUuid().toString());
        pdc.set(keyOwnerName, PersistentDataType.STRING, data.ownerName());
        pdc.set(keyChestBlock, PersistentDataType.STRING, data.chestBlock());
        pdc.set(keySignBlock, PersistentDataType.STRING, data.signBlock());
        pdc.set(keyItem, PersistentDataType.STRING, data.encodedItem());
        pdc.set(keyAmount, PersistentDataType.INTEGER, data.amount());
        pdc.set(keyPrice, PersistentDataType.INTEGER, data.price());
        pdc.set(keyCurrency, PersistentDataType.STRING, data.currency().name());
        pdc.set(keyAdminShop, PersistentDataType.BYTE, data.adminShop() ? (byte) 1 : (byte) 0);
    }

    private ShopData readShopData(Block signBlock) {
        BlockState state = signBlock == null ? null : liveBlockState(signBlock);
        if (!(state instanceof Sign sign)) {
            return null;
        }
        PersistentDataContainer pdc = sign.getPersistentDataContainer();
        if (!pdc.has(keyShopSign, PersistentDataType.BYTE)) {
            return null;
        }
        try {
            String ownerRaw = pdc.get(keyOwnerUuid, PersistentDataType.STRING);
            String item = pdc.get(keyItem, PersistentDataType.STRING);
            String currencyRaw = pdc.get(keyCurrency, PersistentDataType.STRING);
            Integer amount = pdc.get(keyAmount, PersistentDataType.INTEGER);
            Integer price = pdc.get(keyPrice, PersistentDataType.INTEGER);
            if (ownerRaw == null || item == null || currencyRaw == null || amount == null || price == null) {
                return null;
            }
            ItemStack prototype = decodeItem(item);
            boolean adminShop = pdc.getOrDefault(keyAdminShop, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
            return new ShopData(
                UUID.fromString(ownerRaw),
                pdc.getOrDefault(keyOwnerName, PersistentDataType.STRING, "Unknown"),
                pdc.get(keyChestBlock, PersistentDataType.STRING),
                blockKey(signBlock),
                item,
                prototype == null ? "Unknown Item" : displayName(prototype),
                amount,
                price,
                ShopCurrency.valueOf(currencyRaw),
                adminShop
            );
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    boolean recoverLegacyShopSign(Block signBlock, UUID ownerUuid, String ownerName) {
        if (!plugin.getConfigManager().playerShopsEnabled || signBlock == null || ownerUuid == null || ownerName == null
            || ownerName.isBlank() || isMarketPurchaseSign(signBlock)) {
            return false;
        }
        if (isShopSign(signBlock)) {
            return readShopData(signBlock) != null;
        }
        BlockState state = liveBlockState(signBlock);
        if (!(state instanceof Sign sign) || !isPlayerShopHeader(signText(sign, 0))) {
            return false;
        }

        Block chestBlock = attachedChestBlock(signBlock);
        if (chestBlock == null || !chestAvailableForRecovery(chestBlock, signBlock, ownerUuid)) {
            return false;
        }
        Integer amount = parseLeadingInt(signText(sign, 2));
        Integer price = parseLeadingInt(signText(sign, 3));
        ShopCurrency currency = ShopCurrency.parse(signText(sign, 3));
        if (amount == null || amount <= 0 || amount > plugin.getConfigManager().playerShopsMaxAmountPerPurchase
            || price == null || price <= 0 || price > plugin.getConfigManager().playerShopsMaxPrice
            || currency == null) {
            return false;
        }

        Inventory inventory = shopInventory(chestBlock);
        ItemStack prototype = firstChestItem(inventory);
        if (prototype == null || prototype.getType().isAir()) {
            return false;
        }
        prototype = prototype.asOne();
        String itemName = displayName(prototype);
        if (!storedItemNameMatches(signText(sign, 1), itemName) || isLegendaryPrototype(prototype)) {
            return false;
        }
        String encoded = encodeItem(prototype);
        if (encoded == null) {
            return false;
        }

        ShopData data = new ShopData(
            ownerUuid,
            ownerName,
            blockKey(chestBlock),
            blockKey(signBlock),
            encoded,
            itemName,
            amount,
            price,
            currency,
            false
        );
        if (!writeShopData(signBlock, chestBlock, data)) {
            return false;
        }
        plugin.getLogger().info("Recovered legacy chest shop at " + blockKey(signBlock) + ".");
        return true;
    }

    private boolean chestAvailableForRecovery(Block chestBlock, Block signBlock, UUID ownerUuid) {
        String expectedSign = blockKey(signBlock);
        String expectedOwner = ownerUuid.toString();
        for (Block shopChest : shopChestBlocks(chestBlock)) {
            BlockState state = liveBlockState(shopChest);
            if (!(state instanceof TileState tile)) {
                return false;
            }
            PersistentDataContainer pdc = tile.getPersistentDataContainer();
            boolean hasShopData = pdc.has(keyShopChest, PersistentDataType.BYTE)
                || pdc.has(keySignBlock, PersistentDataType.STRING)
                || pdc.has(keyOwnerUuid, PersistentDataType.STRING)
                || pdc.has(keyAdminShop, PersistentDataType.BYTE);
            if (!hasShopData) {
                continue;
            }
            if (!pdc.has(keyShopChest, PersistentDataType.BYTE)
                || !expectedSign.equals(pdc.get(keySignBlock, PersistentDataType.STRING))
                || !expectedOwner.equals(pdc.get(keyOwnerUuid, PersistentDataType.STRING))
                || pdc.getOrDefault(keyAdminShop, PersistentDataType.BYTE, (byte) 0) != (byte) 0) {
                return false;
            }
        }
        return true;
    }

    private void clearShop(Block block) {
        ShopData data = isShopSign(block) ? readShopData(block) : null;
        Block signBlock = isShopSign(block) ? block : shopSignBlockFromChest(block);
        Block chestBlock = data == null
            ? (isShopChest(block) ? block : shopChestBlockFromSign(signBlock))
            : blockFromKey(data.chestBlock());
        if (signBlock != null) {
            BlockState state = signBlock.getState();
            if (state instanceof Sign sign) {
                clearShopPdc(sign.getPersistentDataContainer());
                sign.getSide(Side.FRONT).line(0, Component.text("[Shop]", NamedTextColor.DARK_RED));
                sign.getSide(Side.FRONT).line(1, Component.text("Closed", NamedTextColor.GRAY));
                sign.getSide(Side.FRONT).line(2, Component.empty());
                sign.getSide(Side.FRONT).line(3, Component.empty());
                sign.setWaxed(false);
                sign.update(true, false);
            }
        }
        if (chestBlock != null) {
            for (Block shopChest : shopChestBlocks(chestBlock)) {
                BlockState state = shopChest.getState();
                if (state instanceof TileState tile) {
                    clearShopPdc(tile.getPersistentDataContainer());
                    tile.update(true, false);
                }
            }
        }
    }

    private void clearShopPdc(PersistentDataContainer pdc) {
        pdc.remove(keyShopSign);
        pdc.remove(keyShopChest);
        pdc.remove(keyOwnerUuid);
        pdc.remove(keyOwnerName);
        pdc.remove(keyChestBlock);
        pdc.remove(keySignBlock);
        pdc.remove(keyItem);
        pdc.remove(keyAmount);
        pdc.remove(keyPrice);
        pdc.remove(keyCurrency);
        pdc.remove(keyAdminShop);
    }

    private boolean canManageShop(Player player, Block block) {
        if (player == null) {
            return false;
        }
        if (hasPassiveAdminAccess(player)) {
            return true;
        }
        String owner = ownerUuid(block);
        if (owner == null) return false;
        if (owner.equals(player.getUniqueId().toString())) return true;
        try {
            return plugin.getMarketStallManager() != null
                && plugin.getMarketStallManager().canManageShopForOwner(player, block, UUID.fromString(owner));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean hasPassiveAdminAccess(Player player) {
        return OperatorGameplayMode.allowsPassiveBypass(player, "smpcore.shop.admin");
    }

    static boolean blocksOwnerPurchase(boolean adminShop, boolean allowOwnerPurchases, UUID buyerId, UUID ownerId) {
        return !adminShop && !allowOwnerPurchases && buyerId != null && buyerId.equals(ownerId);
    }

    private String ownerUuid(Block block) {
        BlockState state = block == null ? null : liveBlockState(block);
        if (state instanceof TileState tile) {
            return tile.getPersistentDataContainer().get(keyOwnerUuid, PersistentDataType.STRING);
        }
        return null;
    }

    private Block attachedChestBlock(Block signBlock) {
        if (signBlock == null || !(signBlock.getBlockData() instanceof WallSign directional)) {
            return null;
        }
        Block attached = signBlock.getRelative(directional.getFacing().getOppositeFace());
        return CHEST_TYPES.contains(attached.getType()) ? attached : null;
    }

    private Inventory shopInventory(Block chestBlock) {
        if (chestBlock == null || !CHEST_TYPES.contains(chestBlock.getType())) {
            return null;
        }
        BlockState state = liveBlockState(chestBlock);
        return state instanceof Chest chest ? chest.getInventory() : null;
    }

    private Block shopChestFromInventory(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        InventoryHolder holder = inventory.getHolder(false);
        if (holder instanceof Chest chest && isShopChest(chest.getBlock())) {
            return chest.getBlock();
        }
        if (holder instanceof DoubleChest doubleChest) {
            InventoryHolder left = doubleChest.getLeftSide(false);
            if (left instanceof Chest leftChest && isShopChest(leftChest.getBlock())) {
                return leftChest.getBlock();
            }
            InventoryHolder right = doubleChest.getRightSide(false);
            if (right instanceof Chest rightChest && isShopChest(rightChest.getBlock())) {
                return rightChest.getBlock();
            }
        }
        return null;
    }

    private Block protectedShopChestBlock(Block block) {
        if (block == null || !CHEST_TYPES.contains(block.getType())) {
            return null;
        }
        if (isShopChest(block)) {
            return block;
        }

        Inventory inventory = shopInventory(block);
        return shopChestFromInventory(inventory);
    }

    private List<Block> shopChestBlocks(Block chestBlock) {
        List<Block> blocks = new ArrayList<>();
        if (chestBlock == null || !CHEST_TYPES.contains(chestBlock.getType())) {
            return blocks;
        }
        Inventory inventory = shopInventory(chestBlock);
        InventoryHolder holder = inventory == null ? null : inventory.getHolder(false);
        if (holder instanceof DoubleChest doubleChest) {
            InventoryHolder left = doubleChest.getLeftSide(false);
            if (left instanceof Chest leftChest) {
                blocks.add(leftChest.getBlock());
            }
            InventoryHolder right = doubleChest.getRightSide(false);
            if (right instanceof Chest rightChest && !blocks.contains(rightChest.getBlock())) {
                blocks.add(rightChest.getBlock());
            }
            return blocks;
        }
        blocks.add(chestBlock);
        return blocks;
    }

    private Block shopSignBlockFromChest(Block chestBlock) {
        BlockState state = chestBlock == null ? null : liveBlockState(chestBlock);
        if (!(state instanceof TileState tile)) {
            return null;
        }
        String signKey = tile.getPersistentDataContainer().get(keySignBlock, PersistentDataType.STRING);
        return blockFromKey(signKey);
    }

    private Block shopChestBlockFromSign(Block signBlock) {
        BlockState state = signBlock == null ? null : liveBlockState(signBlock);
        if (!(state instanceof TileState tile)) return null;
        return blockFromKey(tile.getPersistentDataContainer().get(keyChestBlock, PersistentDataType.STRING));
    }

    private boolean isShopSign(Block block) {
        BlockState state = block == null ? null : liveBlockState(block);
        return state instanceof Sign sign
            && sign.getPersistentDataContainer().has(keyShopSign, PersistentDataType.BYTE);
    }

    private boolean isShopChest(Block block) {
        BlockState state = block == null ? null : liveBlockState(block);
        return state instanceof TileState tile
            && tile.getPersistentDataContainer().has(keyShopChest, PersistentDataType.BYTE);
    }

    @SuppressWarnings("deprecation")
    private BlockState liveBlockState(Block block) {
        return block.getState(false);
    }

    private boolean containsProtectedShopBlock(List<Block> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return false;
        }
        for (Block block : blocks) {
            if (isProtectedShopBlock(block)) {
                return true;
            }
        }
        return false;
    }

    private boolean isProtectedShopBlock(Block block) {
        return isShopSign(block) || protectedShopChestBlock(block) != null;
    }

    private ItemStack firstChestItem(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        for (ItemStack item : inventory.getStorageContents()) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                return item.clone();
            }
        }
        return null;
    }

    private boolean canBuyerFitAfterPayment(PlayerInventory inventory, ShopCurrency currency, int price, List<ItemStack> boughtItems) {
        ItemStack[] storage = cloneContents(inventory.getStorageContents());
        if (!currency.isEssence() && !removeMaterial(storage, currency.material(), price)) {
            return false;
        }
        for (ItemStack item : boughtItems) {
            if (!canFit(storage, item)) {
                return false;
            }
            addToCopy(storage, item);
        }
        return true;
    }

    private int countSimilar(ItemStack[] contents, ItemStack prototype) {
        int count = 0;
        for (ItemStack item : contents) {
            if (item != null && item.isSimilar(prototype)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private int countMaterial(ItemStack[] contents, Material material) {
        int count = 0;
        for (ItemStack item : contents) {
            if (isPlainCurrencyItem(item, material)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeSimilar(Inventory inventory, ItemStack prototype, int amount) {
        int remaining = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || !item.isSimilar(prototype)) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            int left = item.getAmount() - take;
            contents[i] = left <= 0 ? null : item.asQuantity(left);
            remaining -= take;
        }
        inventory.setStorageContents(contents);
    }

    private void removeMaterial(PlayerInventory inventory, Material material, int amount) {
        ItemStack[] contents = inventory.getStorageContents();
        removeMaterial(contents, material, amount);
        inventory.setStorageContents(contents);
    }

    private boolean removeSimilar(ItemStack[] contents, ItemStack prototype, int amount) {
        int remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || !item.isSimilar(prototype)) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            int left = item.getAmount() - take;
            contents[i] = left <= 0 ? null : item.asQuantity(left);
            remaining -= take;
        }
        return remaining <= 0;
    }

    private boolean removeMaterial(ItemStack[] contents, Material material, int amount) {
        int remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (!isPlainCurrencyItem(item, material)) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            int left = item.getAmount() - take;
            contents[i] = left <= 0 ? null : item.asQuantity(left);
            remaining -= take;
        }
        return remaining <= 0;
    }

    private boolean isPlainCurrencyItem(ItemStack item, Material material) {
        return item != null
            && item.getType() == material
            && InventoryRecipeUtil.isPlainMaterial(plugin, item, material);
    }

    private boolean canFit(ItemStack[] contents, ItemStack item) {
        int remaining = item.getAmount();
        int maxStack = Math.max(1, item.getMaxStackSize());
        for (ItemStack existing : contents) {
            if (remaining <= 0) {
                return true;
            }
            if (existing != null && existing.isSimilar(item)) {
                remaining -= Math.max(0, maxStack - existing.getAmount());
            }
        }
        for (ItemStack existing : contents) {
            if (remaining <= 0) {
                return true;
            }
            if (existing == null || existing.getType().isAir()) {
                remaining -= maxStack;
            }
        }
        return remaining <= 0;
    }

    private void addToCopy(ItemStack[] contents, ItemStack item) {
        int remaining = item.getAmount();
        int maxStack = Math.max(1, item.getMaxStackSize());
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack existing = contents[i];
            if (existing == null || !existing.isSimilar(item)) {
                continue;
            }
            int add = Math.min(remaining, maxStack - existing.getAmount());
            if (add <= 0) {
                continue;
            }
            contents[i] = existing.asQuantity(existing.getAmount() + add);
            remaining -= add;
        }
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack existing = contents[i];
            if (existing != null && !existing.getType().isAir()) {
                continue;
            }
            int add = Math.min(remaining, maxStack);
            contents[i] = item.asQuantity(add);
            remaining -= add;
        }
    }

    private List<ItemStack> stacksOf(ItemStack prototype, int amount) {
        List<ItemStack> stacks = new ArrayList<>();
        int remaining = amount;
        int maxStack = Math.max(1, prototype.getMaxStackSize());
        while (remaining > 0) {
            int stackSize = Math.min(remaining, maxStack);
            stacks.add(prototype.asQuantity(stackSize));
            remaining -= stackSize;
        }
        return stacks;
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] clone = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            clone[i] = contents[i] == null ? null : contents[i].clone();
        }
        return clone;
    }

    private void writeStoredSignLines(Sign sign, ShopData data) {
        sign.getSide(Side.FRONT).line(0, Component.text(data.adminShop() ? "[AdminShop]" : "[Shop]", data.adminShop() ? NamedTextColor.DARK_PURPLE : NamedTextColor.DARK_GREEN));
        sign.getSide(Side.FRONT).line(1, Component.text(trimSign(data.itemName()), NamedTextColor.BLACK));
        sign.getSide(Side.FRONT).line(2, Component.text(data.adminShop() ? data.amount() + "x Inf" : data.amount() + "x", NamedTextColor.DARK_BLUE));
        sign.getSide(Side.FRONT).line(3, Component.text(data.price() + " " + data.currency().shortName(), NamedTextColor.DARK_RED));
    }

    private void refreshShopSignLines(Block signBlock, ShopData data) {
        BlockState state = signBlock.getState();
        if (!(state instanceof Sign sign)
            || trimSign(data.itemName()).equals(signText(sign, 1))) {
            return;
        }
        writeStoredSignLines(sign, data);
        sign.setWaxed(true);
        sign.update(true, false);
    }

    private String encodeItem(ItemStack item) {
        try {
            return Base64.getEncoder().encodeToString(item.asOne().serializeAsBytes());
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Could not encode shop item: " + ex.getMessage());
            return null;
        }
    }

    private ItemStack decodeItem(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
        } catch (RuntimeException ex) {
            return null;
        }
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
            var world = Bukkit.getWorld(UUID.fromString(parts[0]));
            if (world == null) {
                return null;
            }
            return world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String displayName(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "Unknown Item";
        }
        if (item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return PLAIN.serialize(item.getItemMeta().displayName());
        }
        if (item.getItemMeta() instanceof EnchantmentStorageMeta bookMeta
            && !bookMeta.getStoredEnchants().isEmpty()) {
            List<String> enchantments = bookMeta.getStoredEnchants().entrySet().stream()
                .map(entry -> compactVanillaEnchantName(entry.getKey().getKey().getKey(), entry.getValue()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
            return enchantments.size() == 1
                ? enchantments.getFirst()
                : enchantments.getFirst() + " +" + (enchantments.size() - 1);
        }
        return prettyMaterial(item.getType());
    }

    static String compactVanillaEnchantName(String key, int level) {
        String normalized = key == null ? "enchant" : key.trim().toLowerCase(Locale.ROOT);
        String name = switch (normalized) {
            case "bane_of_arthropods" -> "Bane Arth.";
            case "binding_curse" -> "Binding Curse";
            case "blast_protection" -> "Blast Prot";
            case "depth_strider" -> "Depth Str.";
            case "feather_falling" -> "Feather Fall";
            case "fire_protection" -> "Fire Prot";
            case "luck_of_the_sea" -> "Luck Sea";
            case "projectile_protection" -> "Proj. Prot";
            case "sweeping_edge" -> "Sweeping";
            case "vanishing_curse" -> "Vanishing Curse";
            default -> prettyWords(normalized);
        };
        if ("vanishing_curse".equals(normalized)) {
            return name;
        }
        return name + " " + romanNumeral(Math.max(1, level));
    }

    private String prettyMaterial(Material material) {
        return prettyWords(material.name().toLowerCase(Locale.ROOT));
    }

    private static String prettyWords(String raw) {
        String[] words = raw.split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    private static String romanNumeral(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(value);
        };
    }

    static String trimSign(String text) {
        String safe = safe(text);
        if (safe.length() <= 15) {
            return safe;
        }
        int suffixStart = safe.lastIndexOf(" +");
        if (suffixStart > 0) {
            String suffix = safe.substring(suffixStart);
            if (suffix.length() < 15) {
                return safe.substring(0, 15 - suffix.length()).stripTrailing() + suffix;
            }
        }
        return safe.substring(0, 15).stripTrailing();
    }

    private static String safe(String text) {
        return text == null ? "" : text.replace("<", "").replace(">", "");
    }

    private String cleanLine(String line) {
        return line == null ? "" : line.trim();
    }

    private boolean isAdminShopHeader(String header) {
        String clean = cleanLine(header).toLowerCase(Locale.ROOT);
        return ADMIN_SHOP_HEADER.equals(clean) || ADMIN_SHOP_SHORT_HEADER.equals(clean);
    }

    static boolean isPlayerShopHeader(String header) {
        String clean = header == null ? "" : header.trim();
        return SHOP_HEADER.equalsIgnoreCase(clean) || SHOP_HEADER_ALIAS.equalsIgnoreCase(clean);
    }

    static boolean storedItemNameMatches(String signName, String itemName) {
        String visible = signName == null ? "" : signName.trim();
        return !visible.isBlank() && visible.equalsIgnoreCase(trimSign(itemName));
    }

    private String signText(Sign sign, int line) {
        Component component = sign.getSide(Side.FRONT).line(line);
        return component == null ? "" : PLAIN.serialize(component);
    }

    private boolean isLegendaryPrototype(ItemStack item) {
        return item != null
            && plugin.getLegendaryListener() != null
            && plugin.getLegendaryListener().isLegendaryItem(item);
    }

    private String eventLine(SignChangeEvent event, int line) {
        Component component = event.line(line);
        return component == null ? "" : PLAIN.serialize(component);
    }

    private Integer parseLeadingInt(String line) {
        String clean = cleanLine(line);
        if (clean.isBlank()) {
            return null;
        }
        String first = clean.split("\\s+")[0].replace("x", "");
        try {
            return Integer.parseInt(first);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<BlockFace> horizontalFaces() {
        return List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);
    }

    public List<Component> helpLines() {
        return helpLines(false);
    }

    public List<Component> helpLines(boolean includeAdminHelp) {
        List<Component> lines = new ArrayList<>(List.of(
            MessageUtil.info("Player shops use a wall sign attached to a chest or double chest."),
            MessageUtil.info("Put stock in the chest, attach a blank wall sign, then right-click the sign."),
            MessageUtil.info("Choose the exact item, amount, price, and currency in the setup menu."),
            MessageUtil.info("Right-click an active shop sign to buy, restock, edit, collect payments, or close it."),
            MessageUtil.info("Payments are stored safely outside the stock chest. Use <white>/shops collect</white> to claim them."),
            MessageUtil.info("Trusted stall managers can maintain shops, but sales always pay the stall owner and managers purchase normally."),
            MessageUtil.info("Currencies: coal, copper, iron, gold, redstone, lapis, emerald, diamond, netherite, and Essence.")
        ));
        if (includeAdminHelp) {
            lines.add(MessageUtil.info("Admins can toggle infinite-stock server-shop mode from the setup menu."));
            lines.add(MessageUtil.info("Server shops remove buyer payment and do not create owner payouts. Legendary items stay blocked."));
        }
        return lines;
    }

    public void collectPayments(Player player) {
        paymentLedger.collect(player);
    }

    public List<String> paymentSummary(Player player) {
        return player == null ? List.of() : paymentLedger.summary(player.getUniqueId());
    }

    public boolean isShopPurchaseSign(Block block) {
        return shouldHandleShopPurchase(isShopSign(block), isMarketPurchaseSign(block));
    }

    public void releaseMarketPurchaseSign(Block block) {
        if (block != null && isShopSign(block)) clearShop(block);
    }

    private boolean isMarketPurchaseSign(Block block) {
        return plugin.getMarketStallManager() != null && plugin.getMarketStallManager().isPurchaseSign(block);
    }

    static boolean shouldHandleShopPurchase(boolean playerShopSign, boolean marketPurchaseSign) {
        return playerShopSign && !marketPurchaseSign;
    }

    public int transferOwnershipInArea(
        UUID worldId,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
        UUID oldOwner,
        UUID newOwner,
        String newOwnerName
    ) {
        if (worldId == null || oldOwner == null || newOwner == null) return 0;
        var world = Bukkit.getWorld(worldId);
        if (world == null) return 0;
        int transferred = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block signBlock = world.getBlockAt(x, y, z);
                    if (!isShopSign(signBlock)) continue;
                    ShopData data = readShopData(signBlock);
                    if (data == null || data.adminShop() || !data.ownerUuid().equals(oldOwner)) continue;
                    Block chestBlock = blockFromKey(data.chestBlock());
                    if (chestBlock == null) continue;
                    ShopData updated = new ShopData(
                        newOwner,
                        newOwnerName,
                        data.chestBlock(),
                        data.signBlock(),
                        data.encodedItem(),
                        data.itemName(),
                        data.amount(),
                        data.price(),
                        data.currency(),
                        false
                    );
                    if (writeShopData(signBlock, chestBlock, updated)) transferred++;
                }
            }
        }
        return transferred;
    }

    public int countShopsInArea(
        UUID worldId,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ
    ) {
        return visitShopsInArea(worldId, minX, maxX, minY, maxY, minZ, maxZ, false);
    }

    public int clearShopsInArea(
        UUID worldId,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ
    ) {
        return visitShopsInArea(worldId, minX, maxX, minY, maxY, minZ, maxZ, true);
    }

    private int visitShopsInArea(
        UUID worldId,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
        boolean clear
    ) {
        if (worldId == null || minX > maxX || minY > maxY || minZ > maxZ) return 0;
        var world = Bukkit.getWorld(worldId);
        if (world == null) return 0;
        int count = 0;
        Set<String> visited = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!isShopSign(block) && !isShopChest(block)) continue;
                    Block sign = isShopSign(block) ? block : shopSignBlockFromChest(block);
                    String identity = sign == null ? blockKey(block) : blockKey(sign);
                    if (!visited.add(identity)) continue;
                    count++;
                    if (clear) clearShop(block);
                }
            }
        }
        return count;
    }

    private enum ShopMenuView {
        HUB,
        EDITOR,
        BUYER
    }

    private record ShopMenuHolder(
        UUID playerId,
        ShopMenuView view,
        String signBlock,
        boolean confirmClose
    ) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final class ShopDraft {
        private final String signBlock;
        private ItemStack prototype;
        private int amount;
        private int price;
        private ShopCurrency currency;
        private boolean adminShop;
        private final boolean existing;
        private int itemPage;

        private ShopDraft(
            String signBlock,
            ItemStack prototype,
            int amount,
            int price,
            ShopCurrency currency,
            boolean adminShop,
            boolean existing
        ) {
            this.signBlock = signBlock;
            this.prototype = prototype;
            this.amount = amount;
            this.price = price;
            this.currency = currency;
            this.adminShop = adminShop;
            this.existing = existing;
        }

        String signBlock() {
            return signBlock;
        }

        ItemStack prototype() {
            return prototype;
        }

        void prototype(ItemStack prototype) {
            this.prototype = prototype;
        }

        int amount() {
            return amount;
        }

        void amount(int amount) {
            this.amount = amount;
        }

        int price() {
            return price;
        }

        void price(int price) {
            this.price = price;
        }

        ShopCurrency currency() {
            return currency;
        }

        void currency(ShopCurrency currency) {
            this.currency = currency;
        }

        boolean adminShop() {
            return adminShop;
        }

        void adminShop(boolean adminShop) {
            this.adminShop = adminShop;
        }

        boolean existing() {
            return existing;
        }

        int itemPage() {
            return itemPage;
        }

        void itemPage(int itemPage) {
            this.itemPage = Math.max(0, itemPage);
        }
    }

    private record ShopData(
        UUID ownerUuid,
        String ownerName,
        String chestBlock,
        String signBlock,
        String encodedItem,
        String itemName,
        int amount,
        int price,
        ShopCurrency currency,
        boolean adminShop
    ) {}

}
