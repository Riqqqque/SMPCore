package me.rique.smpcore.quest;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.boss.BossManager;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.util.VisualRangeUtil;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BlackMarketManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int[] TROPHY_SLOTS = {10, 11, 12, 13, 14, 15, 16, 20, 22, 24};
    private static final long PURCHASE_DEBOUNCE_MS = 350L;

    private final SMPCore plugin;
    private final NamespacedKey introducedKey;
    private final NamespacedKey claimsKey;
    private final NamespacedKey menuActionKey;
    private final NamespacedKey trophyItemKey;
    private final NamespacedKey trophyBlockKey;
    private final NamespacedKey trophyOwnerKey;
    private final NamespacedKey trophyOwnerNameKey;
    private final NamespacedKey trophyDisplayKey;
    private final NamespacedKey displayXKey;
    private final NamespacedKey displayYKey;
    private final NamespacedKey displayZKey;
    private final Map<UUID, Long> nextPurchaseAt = new ConcurrentHashMap<>();

    public BlackMarketManager(SMPCore plugin) {
        this.plugin = plugin;
        this.introducedKey = new NamespacedKey(plugin, "black_market_intro");
        this.claimsKey = new NamespacedKey(plugin, "black_market_free_claims");
        this.menuActionKey = new NamespacedKey(plugin, "black_market_action");
        this.trophyItemKey = new NamespacedKey(plugin, "boss_souvenir_item");
        this.trophyBlockKey = new NamespacedKey(plugin, "boss_souvenir_block");
        this.trophyOwnerKey = new NamespacedKey(plugin, "boss_souvenir_owner");
        this.trophyOwnerNameKey = new NamespacedKey(plugin, "boss_souvenir_owner_name");
        this.trophyDisplayKey = new NamespacedKey(plugin, "boss_souvenir_display");
        this.displayXKey = new NamespacedKey(plugin, "boss_souvenir_x");
        this.displayYKey = new NamespacedKey(plugin, "boss_souvenir_y");
        this.displayZKey = new NamespacedKey(plugin, "boss_souvenir_z");
    }

    public void start() {
        Bukkit.getScheduler().runTask(plugin, this::syncLoadedTrophies);
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder(false);
            if (holder instanceof BlackMarketMenuHolder || holder instanceof PurchaseConfirmHolder) {
                player.closeInventory();
            }
        }
        removeAllDisplays();
        nextPurchaseAt.clear();
    }

    public void openFromNpc(Player player) {
        if (player == null || !player.isOnline()) return;
        if (player.getPersistentDataContainer().has(introducedKey, PersistentDataType.BYTE)) {
            openMenu(player);
            return;
        }
        player.getPersistentDataContainer().set(introducedKey, PersistentDataType.BYTE, (byte) 1);
        player.sendMessage(MM.deserialize("<dark_purple>Sable:</dark_purple> <white>Beat a boss and I can turn the proof into something worth displaying.</white>"));
        player.sendMessage(MM.deserialize("<dark_purple>Sable:</dark_purple> <white>Your first trophy is free. Replacements cost Essence.</white>"));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_AMBIENT, 0.55F, 0.75F);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) openMenu(player);
        }, 16L);
    }

    public void recordBossDefeat(BossManager.BossType boss, Collection<UUID> participants) {
        if (boss == null || participants == null || plugin.getBossMasteryManager() == null) return;
        TrophyDefinition trophy = trophy(boss);
        for (UUID playerId : new LinkedHashSet<>(participants)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || claimedBosses(player).contains(boss.id())) continue;
            if (plugin.getBossMasteryManager().kills(playerId, boss.id()) != 1) continue;
            player.sendMessage(MessageUtil.success("Sable can now make your first <white>" + trophy.name() + "</white> for free."));
            player.playSound(player.getLocation(), Sound.BLOCK_DECORATED_POT_INSERT, 0.75F, 1.2F);
        }
    }

    public ItemStack createTrophyItem(BossManager.BossType boss) {
        TrophyDefinition trophy = trophy(boss);
        ItemStack item = new ItemStack(Material.DECORATED_POT);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.EPIC, trophy.name())
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(CustomLoreUtil.buildStyledLore(
            Material.DECORATED_POT,
            "EPIC",
            "BOSS SOUVENIR",
            List.of(
                "<gray>" + trophy.flavor() + "</gray>",
                "<gray>Defeated: <white>" + boss.plainDisplayName() + "</white></gray>"
            ),
            List.of(CustomLoreUtil.section(
                "Display",
                "Placeable Trophy",
                "<gray>Place it in your base, then right-click it to inspect.</gray>",
                "<gray>Breaking it safely returns the trophy.</gray>"
            ))
        ));
        meta.getPersistentDataContainer().set(trophyItemKey, PersistentDataType.STRING, boss.id());
        item.setItemMeta(meta);
        return item;
    }

    private void openMenu(Player player) {
        Inventory menu = Bukkit.createInventory(
            new BlackMarketMenuHolder(player.getUniqueId()),
            45,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#581c87:#f59e0b><bold>Sable's Black Market</bold></gradient>"), "Boss Souvenirs")
        );
        fill(menu);
        Set<String> claimed = claimedBosses(player);
        List<BossManager.BossType> bosses = BossManager.BossType.progressionOrder();
        for (int index = 0; index < bosses.size() && index < TROPHY_SLOTS.length; index++) {
            BossManager.BossType boss = bosses.get(index);
            TrophyDefinition trophy = trophy(boss);
            int kills = bossKills(player, boss);
            if (kills <= 0) {
                menu.setItem(TROPHY_SLOTS[index], menuItem(
                    Material.GRAY_STAINED_GLASS_PANE,
                    "<dark_gray><bold>LOCKED SOUVENIR</bold></dark_gray>",
                    List.of(
                        "<gray>Defeat <white>" + boss.plainDisplayName() + "</white> first.</gray>",
                        "<dark_gray>Only eligible boss-fight victories count.</dark_gray>"
                    ),
                    null
                ));
                continue;
            }
            boolean firstClaimed = claimed.contains(boss.id());
            menu.setItem(TROPHY_SLOTS[index], menuItem(
                Material.DECORATED_POT,
                "<gradient:#a855f7:#f59e0b><bold>" + trophy.name() + "</bold></gradient>",
                List.of(
                    "<gray>Victories: <white>" + kills + "</white></gray>",
                    "<gray>" + trophy.flavor() + "</gray>",
                    firstClaimed
                        ? "<gold>Replacement: <white>" + trophy.price() + " Essence</white></gold>"
                        : "<green>Your first copy is free.</green>",
                    "<yellow>" + BedrockCompat.menuActionWord(player) + " to " + (firstClaimed ? "buy" : "claim") + ".</yellow>"
                ),
                "select:" + boss.id()
            ));
        }
        long balance = plugin.getEssenceManager() == null ? 0L : plugin.getEssenceManager().balance(player);
        menu.setItem(4, menuItem(Material.ENDER_CHEST, "<dark_purple><bold>BOSS SOUVENIRS</bold></dark_purple>", List.of(
            "<gray>Each boss unlocks one permanent trophy design.</gray>",
            "<gray>First copies are free; replacements cost Essence.</gray>",
            "<gray>Your Essence: <white>" + balance + "</white></gray>"
        ), null));
        menu.setItem(40, menuItem(Material.BARRIER, "<red><bold>CLOSE</bold></red>", List.of(), "close"));
        player.openInventory(menu);
    }

    private void openPurchaseConfirm(Player player, BossManager.BossType boss) {
        TrophyDefinition trophy = trophy(boss);
        Inventory menu = Bukkit.createInventory(
            new PurchaseConfirmHolder(player.getUniqueId(), boss.id()),
            27,
            BedrockCompat.menuTitle(player, MM.deserialize("<gold><bold>Confirm Souvenir</bold></gold>"), "Confirm Trophy")
        );
        fill(menu);
        menu.setItem(13, createTrophyItem(boss));
        menu.setItem(11, menuItem(Material.LIME_CONCRETE, "<green><bold>BUY TROPHY</bold></green>", List.of(
            "<gray>Cost: <white>" + trophy.price() + " Essence</white></gray>",
            "<gray>This gives one placeable trophy.</gray>"
        ), "confirm"));
        menu.setItem(15, menuItem(Material.RED_CONCRETE, "<red><bold>CANCEL</bold></red>", List.of(
            "<gray>Return without spending anything.</gray>"
        ), "back"));
        player.openInventory(menu);
    }

    private void selectTrophy(Player player, String bossId) {
        BossManager.BossType boss = BossManager.BossType.fromId(bossId);
        if (boss == null || bossKills(player, boss) <= 0) {
            player.sendMessage(MessageUtil.warn("Defeat that boss before asking Sable for its trophy."));
            openMenu(player);
            return;
        }
        if (!claimedBosses(player).contains(boss.id())) {
            completePurchase(player, boss);
            return;
        }
        openPurchaseConfirm(player, boss);
    }

    private void completePurchase(Player player, BossManager.BossType boss) {
        if (!markPurchase(player)) return;
        if (bossKills(player, boss) <= 0) {
            player.sendMessage(MessageUtil.warn("That souvenir is still locked."));
            openMenu(player);
            return;
        }
        Set<String> claimed = claimedBosses(player);
        boolean free = !claimed.contains(boss.id());
        TrophyDefinition trophy = trophy(boss);
        ItemStack item = createTrophyItem(boss);
        if (!canFit(player.getInventory(), item)) {
            player.sendMessage(MessageUtil.warn("Clear one inventory slot before taking a trophy."));
            return;
        }

        boolean spent = false;
        if (!free) {
            if (plugin.getEssenceManager() == null
                || !plugin.getEssenceManager().spend(player, trophy.price(), "black_market_trophy_" + boss.id())) {
                player.sendMessage(MessageUtil.warn("You need <white>" + trophy.price() + " Essence</white> for that replacement."));
                openMenu(player);
                return;
            }
            spent = true;
        }

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            if (spent && plugin.getEssenceManager() != null) {
                plugin.getEssenceManager().refund(player, trophy.price(), "black_market_trophy_refund_" + boss.id());
            }
            player.sendMessage(MessageUtil.error("The trophy could not be delivered. No Essence was kept."));
            return;
        }
        if (free) {
            claimed.add(boss.id());
            storeClaimedBosses(player, claimed);
        }
        player.sendMessage(MessageUtil.success("Received <white>" + trophy.name() + "</white>."));
        player.playSound(player.getLocation(), Sound.BLOCK_DECORATED_POT_INSERT, 0.9F, free ? 1.35F : 1.05F);
        player.spawnParticle(Particle.END_ROD, player.getLocation().add(0.0, 1.0, 0.0), 8, 0.3, 0.45, 0.3, 0.01);
        openMenu(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder(false);
        if (!(holder instanceof BlackMarketMenuHolder) && !(holder instanceof PurchaseConfirmHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) return;
        if (holder instanceof BlackMarketMenuHolder market && !market.playerId().equals(player.getUniqueId())) return;
        if (holder instanceof PurchaseConfirmHolder confirm && !confirm.playerId().equals(player.getUniqueId())) return;
        String action = menuAction(event.getCurrentItem());
        if (action == null) return;
        if ("close".equals(action)) {
            player.closeInventory();
        } else if ("back".equals(action)) {
            openMenu(player);
        } else if ("confirm".equals(action) && holder instanceof PurchaseConfirmHolder confirm) {
            BossManager.BossType boss = BossManager.BossType.fromId(confirm.bossId());
            if (boss != null) completePurchase(player, boss);
        } else if (action.startsWith("select:") && holder instanceof BlackMarketMenuHolder) {
            selectTrophy(player, action.substring("select:".length()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMenuDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof BlackMarketMenuHolder || holder instanceof PurchaseConfirmHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        nextPurchaseAt.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        BossManager.BossType boss = trophyBoss(event.getItemInHand());
        if (boss == null) return;
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.DECORATED_POT || !(block.getState() instanceof TileState state)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.error("That trophy could not be placed here."));
            return;
        }
        PersistentDataContainer pdc = state.getPersistentDataContainer();
        pdc.set(trophyBlockKey, PersistentDataType.STRING, boss.id());
        pdc.set(trophyOwnerKey, PersistentDataType.STRING, event.getPlayer().getUniqueId().toString());
        pdc.set(trophyOwnerNameKey, PersistentDataType.STRING, event.getPlayer().getName());
        if (!state.update(true, false)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.error("That trophy could not be saved safely."));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (trophyBoss(block) == boss) {
                ensureDisplay(block);
                playTrophyEffect(block.getLocation(), trophy(boss));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTrophyInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
            || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
            || event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();
        BossManager.BossType boss = trophyBoss(block);
        if (boss == null) return;
        event.setCancelled(true);
        ensureDisplay(block);
        TrophyDefinition trophy = trophy(boss);
        String owner = trophyOwnerName(block);
        event.getPlayer().sendMessage(MM.deserialize(
            "<gradient:#a855f7:#f59e0b><bold>" + trophy.name() + "</bold></gradient> "
                + "<dark_gray>-</dark_gray> <gray>" + boss.plainDisplayName() + ""
                + (owner == null ? "" : " · placed by <white>" + owner + "</white>") + "</gray>"
        ));
        playTrophyEffect(block.getLocation(), trophy);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        BossManager.BossType boss = trophyBoss(block);
        if (boss == null) return;
        event.setDropItems(false);
        event.setExpToDrop(0);
        removeDisplays(block);
        boolean returnItem = event.getPlayer().getGameMode() != GameMode.CREATIVE;
        ItemStack trophyItem = createTrophyItem(boss);
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (trophyBoss(block) != null) {
                ensureDisplay(block);
                return;
            }
            if (returnItem) giveOrDrop(player, block.getLocation(), trophyItem);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> trophyBoss(block) != null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> trophyBoss(block) != null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> trophyBoss(block) != null
            || trophyBoss(block.getRelative(event.getDirection())) != null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> trophyBoss(block) != null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (isTrophyInventory(event.getSource()) || isTrophyInventory(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> syncChunk(event.getChunk()));
    }

    private void syncLoadedTrophies() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) syncChunk(chunk);
        }
    }

    private void syncChunk(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof ItemDisplay display) || !isTrophyDisplay(display)) continue;
            Block anchor = displayAnchor(display);
            if (anchor == null || trophyBoss(anchor) == null) display.remove();
        }
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof TileState && trophyBoss(state.getBlock()) != null) {
                ensureDisplay(state.getBlock());
            }
        }
    }

    private void ensureDisplay(Block block) {
        BossManager.BossType boss = trophyBoss(block);
        if (boss == null || block.getWorld() == null) return;
        List<ItemDisplay> matches = new ArrayList<>();
        Location center = block.getLocation().add(0.5, 1.12, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(center, 1.25, 1.75, 1.25)) {
            if (entity instanceof ItemDisplay display && displayMatches(display, block)) matches.add(display);
        }
        ItemDisplay display = matches.isEmpty()
            ? block.getWorld().spawn(center, ItemDisplay.class)
            : matches.getFirst();
        for (int index = 1; index < matches.size(); index++) matches.get(index).remove();
        configureDisplay(display, block, boss);
    }

    private void configureDisplay(ItemDisplay display, Block block, BossManager.BossType boss) {
        TrophyDefinition trophy = trophy(boss);
        Location target = block.getLocation().add(0.5, 1.12, 0.5);
        if (!display.getWorld().equals(block.getWorld()) || display.getLocation().distanceSquared(target) > 0.000001D) {
            display.teleport(target);
        }
        display.setItemStack(trophyDisplayItem(boss));
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setBillboard(Display.Billboard.FIXED);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setViewRange(VisualRangeUtil.blocksToDisplayViewRange(24));
        display.setGlowing(true);
        display.setGlowColorOverride(trophy.color());
        display.setShadowRadius(0.22F);
        display.setShadowStrength(0.75F);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setPersistent(false);
        boolean brokenStandard = boss == BossManager.BossType.YULE_THE_MINION;
        display.setTransformation(new Transformation(
            new Vector3f(0.0F, brokenStandard ? 0.08F : 0.0F, 0.0F),
            new AxisAngle4f((float) Math.toRadians(brokenStandard ? -6.0 : -18.0), 1.0F, 0.0F, 0.0F),
            new Vector3f(brokenStandard ? 0.78F : 0.72F, brokenStandard ? 0.78F : 0.72F, brokenStandard ? 0.78F : 0.72F),
            new AxisAngle4f((float) Math.toRadians(brokenStandard ? 10.0 : 35.0), 0.0F, 1.0F, 0.0F)
        ));
        PersistentDataContainer pdc = display.getPersistentDataContainer();
        pdc.set(trophyDisplayKey, PersistentDataType.STRING, boss.id());
        pdc.set(displayXKey, PersistentDataType.INTEGER, block.getX());
        pdc.set(displayYKey, PersistentDataType.INTEGER, block.getY());
        pdc.set(displayZKey, PersistentDataType.INTEGER, block.getZ());
    }

    private ItemStack trophyDisplayItem(BossManager.BossType boss) {
        TrophyDefinition trophy = trophy(boss);
        ItemStack item = new ItemStack(trophy.icon());
        if (boss != BossManager.BossType.YULE_THE_MINION || !(item.getItemMeta() instanceof BannerMeta meta)) {
            return item;
        }
        meta.setPatterns(List.of(
            new Pattern(DyeColor.BLACK, PatternType.BORDER),
            new Pattern(DyeColor.RED, PatternType.STRIPE_DOWNLEFT),
            new Pattern(DyeColor.YELLOW, PatternType.RHOMBUS),
            new Pattern(DyeColor.BLACK, PatternType.TRIANGLES_BOTTOM)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private void removeDisplays(Block block) {
        Location center = block.getLocation().add(0.5, 1.12, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(center, 1.25, 1.75, 1.25)) {
            if (entity instanceof ItemDisplay display && displayMatches(display, block)) display.remove();
        }
    }

    private void removeAllDisplays() {
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (isTrophyDisplay(display)) display.remove();
            }
        }
    }

    private boolean displayMatches(ItemDisplay display, Block block) {
        PersistentDataContainer pdc = display.getPersistentDataContainer();
        Integer x = pdc.get(displayXKey, PersistentDataType.INTEGER);
        Integer y = pdc.get(displayYKey, PersistentDataType.INTEGER);
        Integer z = pdc.get(displayZKey, PersistentDataType.INTEGER);
        return pdc.has(trophyDisplayKey, PersistentDataType.STRING)
            && x != null && y != null && z != null
            && display.getWorld().equals(block.getWorld())
            && x == block.getX() && y == block.getY() && z == block.getZ();
    }

    private boolean isTrophyDisplay(ItemDisplay display) {
        return display.getPersistentDataContainer().has(trophyDisplayKey, PersistentDataType.STRING);
    }

    private Block displayAnchor(ItemDisplay display) {
        PersistentDataContainer pdc = display.getPersistentDataContainer();
        Integer x = pdc.get(displayXKey, PersistentDataType.INTEGER);
        Integer y = pdc.get(displayYKey, PersistentDataType.INTEGER);
        Integer z = pdc.get(displayZKey, PersistentDataType.INTEGER);
        if (x == null || y == null || z == null || y < display.getWorld().getMinHeight() || y >= display.getWorld().getMaxHeight()) return null;
        return display.getWorld().getBlockAt(x, y, z);
    }

    private BossManager.BossType trophyBoss(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        String id = meta == null ? null : meta.getPersistentDataContainer().get(trophyItemKey, PersistentDataType.STRING);
        return BossManager.BossType.fromId(id);
    }

    private BossManager.BossType trophyBoss(Block block) {
        if (block == null || block.getType() != Material.DECORATED_POT || !(block.getState() instanceof TileState state)) return null;
        return BossManager.BossType.fromId(state.getPersistentDataContainer().get(trophyBlockKey, PersistentDataType.STRING));
    }

    private String trophyOwnerName(Block block) {
        if (!(block.getState() instanceof TileState state)) return null;
        return state.getPersistentDataContainer().get(trophyOwnerNameKey, PersistentDataType.STRING);
    }

    private boolean isTrophyInventory(Inventory inventory) {
        InventoryHolder holder = inventory == null ? null : inventory.getHolder(false);
        return holder instanceof BlockState state && trophyBoss(state.getBlock()) != null;
    }

    private int bossKills(Player player, BossManager.BossType boss) {
        return plugin.getBossMasteryManager() == null ? 0 : plugin.getBossMasteryManager().kills(player.getUniqueId(), boss.id());
    }

    private Set<String> claimedBosses(Player player) {
        String stored = player.getPersistentDataContainer().get(claimsKey, PersistentDataType.STRING);
        Set<String> claimed = new LinkedHashSet<>();
        if (stored == null || stored.isBlank()) return claimed;
        for (String id : stored.split(",")) {
            BossManager.BossType boss = BossManager.BossType.fromId(id);
            if (boss != null) claimed.add(boss.id());
        }
        return claimed;
    }

    private void storeClaimedBosses(Player player, Set<String> claimed) {
        List<String> ordered = claimed.stream()
            .filter(id -> BossManager.BossType.fromId(id) != null)
            .sorted(Comparator.comparingInt(id -> BossManager.BossType.fromId(id).progressionTier()))
            .toList();
        player.getPersistentDataContainer().set(claimsKey, PersistentDataType.STRING, String.join(",", ordered));
    }

    private boolean markPurchase(Player player) {
        long now = System.currentTimeMillis();
        Long next = nextPurchaseAt.get(player.getUniqueId());
        if (next != null && next > now) return false;
        nextPurchaseAt.put(player.getUniqueId(), now + PURCHASE_DEBOUNCE_MS);
        return true;
    }

    static long replacementPrice(int progressionTier) {
        return 20L + Math.max(1, progressionTier) * 10L;
    }

    private static boolean canFit(Inventory inventory, ItemStack item) {
        if (inventory == null || item == null || item.getType().isAir()) return false;
        for (ItemStack existing : inventory.getStorageContents()) {
            if (existing == null || existing.getType().isAir()) return true;
            if (existing.isSimilar(item) && existing.getAmount() < existing.getMaxStackSize()) return true;
        }
        return false;
    }

    private void giveOrDrop(Player player, Location location, ItemStack item) {
        World world = location == null ? null : location.getWorld();
        if (world == null) return;
        Location dropLocation = location.clone().add(0.5, 0.5, 0.5);
        if (player != null && player.isOnline()) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            leftovers.values().forEach(left -> world.dropItemNaturally(dropLocation, left));
        } else {
            world.dropItemNaturally(dropLocation, item);
        }
    }

    private void playTrophyEffect(Location blockLocation, TrophyDefinition trophy) {
        Location center = blockLocation.clone().add(0.5, 1.05, 0.5);
        blockLocation.getWorld().spawnParticle(
            Particle.DUST,
            center,
            10,
            0.22,
            0.28,
            0.22,
            0.0,
            new Particle.DustOptions(trophy.color(), 1.0F)
        );
        blockLocation.getWorld().playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.65F, 1.2F);
    }

    private ItemStack menuItem(Material material, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        String visibleName = MenuItemUtil.visibleMiniName(name);
        meta.displayName(MM.deserialize(visibleName).decoration(TextDecoration.ITALIC, false));
        meta.lore(MenuItemUtil.visibleMiniLore(visibleName, lore).stream()
            .map(line -> MM.deserialize(line).decoration(TextDecoration.ITALIC, false))
            .toList());
        if (action != null) meta.getPersistentDataContainer().set(menuActionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private String menuAction(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(menuActionKey, PersistentDataType.STRING);
    }

    private void fill(Inventory inventory) {
        ItemStack filler = menuItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), null);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    private static TrophyDefinition trophy(BossManager.BossType boss) {
        long price = replacementPrice(boss.progressionTier());
        return switch (boss) {
            case YULE_THE_MINION -> new TrophyDefinition("Marshal's Broken Standard", Material.ORANGE_BANNER, Color.fromRGB(225, 92, 28), price, "A command standard snapped at the first breach.");
            case KAEL_THE_ASHEN -> new TrophyDefinition("Cindervale Sightline", Material.CROSSBOW, Color.fromRGB(190, 205, 220), price, "The last clean shot fired over Cindervale.");
            case VESPER_THE_WIDOW_QUEEN -> new TrophyDefinition("Matriarch's Silk Crown", Material.COBWEB, Color.fromRGB(80, 190, 88), price, "Gloam silk woven into a crown that still twitches.");
            case MIREWOOD_THE_ROOT_TYRANT -> new TrophyDefinition("Regent's Thornheart", Material.SPORE_BLOSSOM, Color.fromRGB(82, 160, 70), price, "A living knot cut from the Briarveil throne.");
            case NEREIDA_THE_ABYSS_MOTHER -> new TrophyDefinition("Drowned Veil Reliquary", Material.HEART_OF_THE_SEA, Color.fromRGB(35, 180, 220), price, "A sealed tide that remembers every lost ship.");
            case IRON_SAINT -> new TrophyDefinition("Confessor's Last Gear", Material.HEAVY_CORE, Color.fromRGB(210, 200, 150), price, "The final gear to turn inside the machine chapel.");
            case AURELION_THE_RIFT_SERAPH -> new TrophyDefinition("Rift Oracle's Lens", Material.ENDER_EYE, Color.fromRGB(170, 90, 255), price, "A lens fixed on a door that should not exist.");
            case MORVESSA_THE_RUNEBLOOM_WITCH -> new TrophyDefinition("Runebloom Witchglass", Material.AMETHYST_CLUSTER, Color.fromRGB(145, 80, 205), price, "A bloom preserved at the instant its hex broke.");
            case VORALITH_THE_CRIMSON_WARDEN -> new TrophyDefinition("Noctyr's Silent Bell", Material.ECHO_SHARD, Color.fromRGB(205, 30, 55), price, "A bell fragment that rings only in memory.");
            case CORRUPTED_OATHKEEPER -> new TrophyDefinition("Aurel Voss's Fractured Oath", Material.NETHER_STAR, Color.fromRGB(245, 90, 30), price, "A broken oath that refused to become fuel.");
        };
    }

    private record TrophyDefinition(String name, Material icon, Color color, long price, String flavor) {
    }

    private record BlackMarketMenuHolder(UUID playerId) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record PurchaseConfirmHolder(UUID playerId, String bossId) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
