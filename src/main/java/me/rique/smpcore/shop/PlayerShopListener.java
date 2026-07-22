package me.rique.smpcore.shop;

import io.papermc.paper.event.player.PlayerOpenSignEvent;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.InventoryRecipeUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import org.bukkit.block.data.Directional;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class PlayerShopListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String SHOP_HEADER = "[shop]";
    private static final String SHOP_HEADER_ALIAS = "[shops]";
    private static final String ADMIN_SHOP_HEADER = "[adminshop]";
    private static final String ADMIN_SHOP_SHORT_HEADER = "[ashop]";
    private static final Set<Material> CHEST_TYPES = Set.of(Material.CHEST, Material.TRAPPED_CHEST);

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
        if (stalls != null) {
            stalls.tryRecoverLegacyShopSign(event.getClickedBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (isShopSign(event.getBlock()) || isMarketPurchaseSign(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.warn(isMarketPurchaseSign(event.getBlock())
                ? "That market sign is managed by the server."
                : "Break and recreate the shop to change its sign."));
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
        if (adminShop && !player.hasPermission("smpcore.shop.admin")) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.error("You do not have permission to create admin shops."));
            return;
        }
        if (!player.hasPermission("smpcore.shop") && !player.hasPermission("smpcore.shop.admin")) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.error("You do not have permission to create shops."));
            return;
        }
        if (event.getSide() != Side.FRONT) {
            markSignError(event, "Use front side");
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
            markSignError(event, "Attach to chest");
            player.sendMessage(MessageUtil.warn("Attach the shop sign to the front of a chest or double chest."));
            return;
        }
        if (plugin.getMarketStallManager() != null
            && !plugin.getMarketStallManager().canCreateShopAt(player, chestBlock, signBlock)) {
            markSignError(event, "Use your chest");
            player.sendMessage(MessageUtil.warn("Inside a stall you manage, attach a wall sign directly to a chest or trapped chest."));
            return;
        }
        MarketStallManager.ShopIdentity shopOwner = plugin.getMarketStallManager() == null
            ? new MarketStallManager.ShopIdentity(player.getUniqueId(), player.getName())
            : plugin.getMarketStallManager().shopIdentityFor(player, chestBlock, signBlock);
        if (shopOwner == null) {
            markSignError(event, "No stall access");
            player.sendMessage(MessageUtil.warn("You no longer have permission to manage this stall."));
            return;
        }
        if (isShopChest(chestBlock) || isShopSign(signBlock)) {
            markSignError(event, "Already a shop");
            player.sendMessage(MessageUtil.warn("That chest already has a shop."));
            return;
        }

        ShopCurrency currency = ShopCurrency.parse(eventLine(event, 3));
        Integer price = parseLeadingInt(eventLine(event, 3));
        Integer amount = parseLeadingInt(eventLine(event, 2));
        if (currency == null || price == null || price <= 0) {
            markSignError(event, "Bad price");
            player.sendMessage(MessageUtil.warn("Line 4 must be a price and supported currency, such as 5 diamond or 25 essence."));
            return;
        }
        if (amount == null || amount <= 0) {
            markSignError(event, "Bad amount");
            player.sendMessage(MessageUtil.warn("Line 3 must be the amount sold per purchase."));
            return;
        }
        int maxAmount = plugin.getConfigManager().playerShopsMaxAmountPerPurchase;
        int maxPrice = plugin.getConfigManager().playerShopsMaxPrice;
        if (amount > maxAmount || price > maxPrice) {
            markSignError(event, "Too large");
            player.sendMessage(MessageUtil.warn("Shop amount or price is too high for this server."));
            return;
        }

        Inventory inventory = shopInventory(chestBlock);
        String itemLine = eventLine(event, 1);
        boolean usesChestPrototype = usesChestPrototype(itemLine);
        ItemStack prototype = prototypeFromLine(itemLine, inventory);
        if (prototype == null || prototype.getType().isAir()) {
            markSignError(event, "No item found");
            player.sendMessage(MessageUtil.warn("Put the sold item in the chest, then use 'chest' on line 2."));
            return;
        }
        prototype = prototype.asOne();
        if (isLegendaryPrototype(prototype)) {
            markSignError(event, "No legends");
            player.sendMessage(MessageUtil.warn("Legendary items cannot be sold through shops."));
            return;
        }
        final ItemStack shopPrototype = prototype;
        String encoded = encodeItem(prototype);
        if (encoded == null) {
            markSignError(event, "Bad item");
            player.sendMessage(MessageUtil.error("That item could not be stored safely as a shop item."));
            return;
        }

        ShopData data = new ShopData(
            adminShop ? player.getUniqueId() : shopOwner.ownerId(),
            adminShop ? player.getName() : shopOwner.ownerName(),
            blockKey(chestBlock),
            blockKey(signBlock),
            encoded,
            displayName(prototype),
            amount,
            price,
            currency,
            adminShop
        );
        writeShopSignLines(event, data);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!writeShopData(signBlock, chestBlock, data)) {
                player.sendMessage(MessageUtil.error("Could not finish creating that shop."));
                return;
            }
            if (adminShop && usesChestPrototype) {
                returnMatchingSamples(player, inventory, shopPrototype);
            }
            player.sendMessage(MessageUtil.success(adminShop
                ? "Admin shop created. Buyers pay from their inventory and stock is infinite."
                : shopOwner.ownerId().equals(player.getUniqueId())
                    ? "Shop created. Players buy by right-clicking the sign."
                    : "Shop created for " + shopOwner.ownerName() + ". Players buy by right-clicking the sign."));
            signBlock.getWorld().playSound(signBlock.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.4f);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onShopSignOpen(PlayerOpenSignEvent event) {
        Block block = event.getSign().getBlock();
        if (!isShopSign(block)) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(MessageUtil.warn(canManageShop(event.getPlayer(), block)
            ? "Break and recreate the shop to change its sign."
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
            buyFromShop(event.getPlayer(), clicked);
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
        event.getPlayer().sendMessage(MessageUtil.warn("Buy from this shop by right-clicking its sign."));
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
        player.sendMessage(MessageUtil.warn("Buy from this shop by right-clicking its sign."));
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
        BlockState state = signBlock == null ? null : signBlock.getState();
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
        BlockState state = signBlock.getState();
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
            BlockState state = shopChest.getState();
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
        if (player.isOp() || player.hasPermission("smpcore.shop.admin")) {
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

    static boolean blocksOwnerPurchase(boolean adminShop, boolean allowOwnerPurchases, UUID buyerId, UUID ownerId) {
        return !adminShop && !allowOwnerPurchases && buyerId != null && buyerId.equals(ownerId);
    }

    private String ownerUuid(Block block) {
        BlockState state = block == null ? null : block.getState();
        if (state instanceof TileState tile) {
            return tile.getPersistentDataContainer().get(keyOwnerUuid, PersistentDataType.STRING);
        }
        return null;
    }

    private Block attachedChestBlock(Block signBlock) {
        if (signBlock == null || !(signBlock.getBlockData() instanceof Directional directional)) {
            return null;
        }
        Block attached = signBlock.getRelative(directional.getFacing().getOppositeFace());
        return CHEST_TYPES.contains(attached.getType()) ? attached : null;
    }

    private Inventory shopInventory(Block chestBlock) {
        if (chestBlock == null || !CHEST_TYPES.contains(chestBlock.getType())) {
            return null;
        }
        BlockState state = chestBlock.getState();
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
            InventoryHolder left = doubleChest.getLeftSide();
            if (left instanceof Chest leftChest && isShopChest(leftChest.getBlock())) {
                return leftChest.getBlock();
            }
            InventoryHolder right = doubleChest.getRightSide();
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
            InventoryHolder left = doubleChest.getLeftSide();
            if (left instanceof Chest leftChest) {
                blocks.add(leftChest.getBlock());
            }
            InventoryHolder right = doubleChest.getRightSide();
            if (right instanceof Chest rightChest && !blocks.contains(rightChest.getBlock())) {
                blocks.add(rightChest.getBlock());
            }
            return blocks;
        }
        blocks.add(chestBlock);
        return blocks;
    }

    private Block shopSignBlockFromChest(Block chestBlock) {
        BlockState state = chestBlock == null ? null : chestBlock.getState();
        if (!(state instanceof TileState tile)) {
            return null;
        }
        String signKey = tile.getPersistentDataContainer().get(keySignBlock, PersistentDataType.STRING);
        return blockFromKey(signKey);
    }

    private Block shopChestBlockFromSign(Block signBlock) {
        BlockState state = signBlock == null ? null : signBlock.getState();
        if (!(state instanceof TileState tile)) return null;
        return blockFromKey(tile.getPersistentDataContainer().get(keyChestBlock, PersistentDataType.STRING));
    }

    private boolean isShopSign(Block block) {
        BlockState state = block == null ? null : block.getState();
        return state instanceof Sign sign
            && sign.getPersistentDataContainer().has(keyShopSign, PersistentDataType.BYTE);
    }

    private boolean isShopChest(Block block) {
        BlockState state = block == null ? null : block.getState();
        return state instanceof TileState tile
            && tile.getPersistentDataContainer().has(keyShopChest, PersistentDataType.BYTE);
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

    private ItemStack prototypeFromLine(String itemLine, Inventory inventory) {
        String token = cleanLine(itemLine).toLowerCase(Locale.ROOT);
        if (usesChestPrototype(token)) {
            return firstChestItem(inventory);
        }
        Material material = Material.matchMaterial(token);
        if (material == null) {
            material = Material.matchMaterial(token.toUpperCase(Locale.ROOT));
        }
        return material == null || material.isAir() ? firstChestItem(inventory) : new ItemStack(material);
    }

    private boolean usesChestPrototype(String itemLine) {
        String token = cleanLine(itemLine).toLowerCase(Locale.ROOT);
        return token.isBlank() || token.equals("chest") || token.equals("item") || token.equals("this");
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

    private void returnMatchingSamples(Player player, Inventory inventory, ItemStack prototype) {
        if (player == null || inventory == null || prototype == null || prototype.getType().isAir()) {
            return;
        }
        ItemStack[] contents = inventory.getStorageContents();
        List<ItemStack> returned = new ArrayList<>();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || !item.isSimilar(prototype)) {
                continue;
            }
            returned.add(item.clone());
            contents[i] = null;
        }
        if (returned.isEmpty()) {
            return;
        }
        inventory.setStorageContents(contents);
        Location dropLocation = player.getLocation();
        for (ItemStack item : returned) {
            player.getInventory().addItem(item).values().forEach(leftover ->
                player.getWorld().dropItemNaturally(dropLocation, leftover)
            );
        }
        player.updateInventory();
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

    private void writeShopSignLines(SignChangeEvent event, ShopData data) {
        event.line(0, Component.text(data.adminShop() ? "[AdminShop]" : "[Shop]", data.adminShop() ? NamedTextColor.DARK_PURPLE : NamedTextColor.DARK_GREEN));
        event.line(1, Component.text(trimSign(data.itemName()), NamedTextColor.BLACK));
        event.line(2, Component.text(data.adminShop() ? data.amount() + "x Inf" : data.amount() + "x", NamedTextColor.DARK_BLUE));
        event.line(3, Component.text(data.price() + " " + data.currency().shortName(), NamedTextColor.DARK_RED));
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

    private void markSignError(SignChangeEvent event, String error) {
        event.line(0, Component.text("[Shop]", NamedTextColor.DARK_RED));
        event.line(1, Component.text("Invalid", NamedTextColor.RED));
        event.line(2, Component.text(trimSign(error), NamedTextColor.DARK_GRAY));
        event.line(3, Component.empty());
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
            MessageUtil.info("In a market stall, signs can only be placed directly onto your chest or trapped chest."),
            MessageUtil.info("Line 1: <white>[shop]</white> (the <white>[shops]</white> alias also works)."),
            MessageUtil.info("Line 2: <white>chest</white> for the first item in the chest, or a vanilla item id."),
            MessageUtil.info("Line 3: amount sold per purchase, like <white>4</white>."),
            MessageUtil.info("Line 4: price and currency, such as <white>5 diamond</white>, <white>12 iron</white>, or <white>25 essence</white>."),
            MessageUtil.info("Payments are stored safely outside the stock chest. Use <white>/shops collect</white> to claim them."),
            MessageUtil.info("Trusted stall managers can maintain shops, but sales always pay the stall owner and managers purchase normally."),
            MessageUtil.info("Currencies: coal, copper, iron, gold, redstone, lapis, emerald, diamond, netherite, and Essence.")
        ));
        if (includeAdminHelp) {
            lines.add(MessageUtil.info("Admin shops: use <white>[adminshop]</white> or <white>[ashop]</white> on line 1. They have infinite stock, remove buyer payment, and do not store stock or payment."));
            lines.add(MessageUtil.info("Admin shops support custom item samples from the chest, but legendary items are blocked."));
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
