package me.rique.smpcore.item;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.AtomicYamlFile;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.util.VisualRangeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class VeilOrbManager implements Listener {

    public static final String AGGRO_ORB_ID = "warden_lure_orb";
    public static final String STAT_ORB_ID = "veilshift_orb";
    public static final String ENCHANT_ORB_ID = "runebloom_orb";
    public static final String SOLO_DEVICE_ID = "lone_star_engine";
    public static final String RUNIC_LOOM_ID = "runic_loom";
    public static final String FATE_CRUCIBLE_ID = "fate_crucible";

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final int RUNIC_ITEM_SLOT = 20;
    private static final int RUNIC_ORB_SLOT = 24;
    private static final int RUNIC_STATUS_SLOT = 22;
    private static final int FATE_ITEM_SLOT = 22;
    private static final int FATE_BUTTON_SLOT = 31;
    private static final int INFO_SLOT = 4;
    private static final int CLOSE_SLOT = 40;
    private static final int[] ENCHANT_OPTION_SLOTS = {10, 11, 12, 13, 14, 15, 16, 28, 29, 30, 32, 33, 34};
    private static final int RUNIC_REQUIRED_ENCHANTS = 4;
    private static final int RUNIC_EXTRA_MAX_LEVELS = 2;
    private static final int AGGRO_ORB_BONUS = 5;
    private static final double SOLO_CHECK_RADIUS = 34.0D;
    private static final double SOLO_TEAM_RADIUS = 32.0D;
    private static final long MOMENTUM_KILL_WINDOW_MS = 30_000L;
    private static final int MOMENTUM_MAX_STACKS = 3;
    private static final int MOMENTUM_EFFECT_TICKS = 70;
    private static final Set<String> RUNIC_SCALING_ENCHANTS = Set.of(
        "bane_of_arthropods",
        "blast_protection",
        "breach",
        "density",
        "depth_strider",
        "efficiency",
        "feather_falling",
        "fire_aspect",
        "fire_protection",
        "fortune",
        "frost_walker",
        "impaling",
        "knockback",
        "looting",
        "loyalty",
        "luck_of_the_sea",
        "lure",
        "piercing",
        "power",
        "projectile_protection",
        "protection",
        "punch",
        "quick_charge",
        "respiration",
        "riptide",
        "sharpness",
        "smite",
        "soul_speed",
        "sweeping_edge",
        "swift_sneak",
        "thorns",
        "unbreaking",
        "wind_burst"
    );

    private final SMPCore plugin;
    private final NamespacedKey keyAggroBonus;
    private final NamespacedKey keyStatOrbKind;
    private final NamespacedKey keyStatOrbAmount;
    private final NamespacedKey keyStatOrbModifier;
    private final NamespacedKey keyMenuAction;
    private final NamespacedKey keyMenuValue;
    private final NamespacedKey keyStationHologram;
    private final NamespacedKey keyStationHologramBlock;
    private final NamespacedKey keyMomentumDeviceSeen;
    private final File stationFile;
    private final Map<BlockKey, StationType> stations = new ConcurrentHashMap<>();
    private final Map<String, UUID> hologramsByBlock = new ConcurrentHashMap<>();
    private final Map<UUID, MomentumState> momentumStates = new ConcurrentHashMap<>();
    private BukkitTask momentumTask;

    public VeilOrbManager(SMPCore plugin) {
        this.plugin = plugin;
        this.keyAggroBonus = new NamespacedKey(plugin, "boss_aggro_bonus");
        this.keyStatOrbKind = new NamespacedKey(plugin, "veilshift_stat");
        this.keyStatOrbAmount = new NamespacedKey(plugin, "veilshift_amount");
        this.keyStatOrbModifier = new NamespacedKey(plugin, "veilshift_modifier");
        this.keyMenuAction = new NamespacedKey(plugin, "veil_orb_menu_action");
        this.keyMenuValue = new NamespacedKey(plugin, "veil_orb_menu_value");
        this.keyStationHologram = new NamespacedKey(plugin, "veil_orb_station_hologram");
        this.keyStationHologramBlock = new NamespacedKey(plugin, "veil_orb_station_hologram_block");
        this.keyMomentumDeviceSeen = new NamespacedKey(plugin, "lone_star_engine_seen");
        this.stationFile = new File(plugin.getDataFolder(), "veil-orb-stations.yml");
    }

    public void start() {
        loadStations();
        syncLoadedStations();
        if (momentumTask != null) {
            momentumTask.cancel();
        }
        momentumTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickMomentumDevices, 20L, 20L);
    }

    public void shutdown() {
        if (momentumTask != null) {
            momentumTask.cancel();
            momentumTask = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            InventoryHolder holder = top.getHolder(false);
            boolean evacuated = false;
            if (holder instanceof RunicLoomHolder runic && runic.playerId().equals(player.getUniqueId())) {
                returnSlot(player, top, RUNIC_ITEM_SLOT);
                returnSlot(player, top, RUNIC_ORB_SLOT);
                evacuated = true;
            } else if (holder instanceof FateCrucibleHolder fate && fate.playerId().equals(player.getUniqueId())) {
                returnSlot(player, top, FATE_ITEM_SLOT);
                evacuated = true;
            }
            if (evacuated) {
                player.closeInventory();
                player.updateInventory();
            }
        }
        for (UUID hologramId : new ArrayList<>(hologramsByBlock.values())) {
            Entity entity = Bukkit.getEntity(hologramId);
            if (entity != null) {
                entity.remove();
            }
        }
        hologramsByBlock.clear();
        momentumStates.clear();
    }

    public int aggroBonus(Player player) {
        if (player == null || !player.isOnline()) {
            return 0;
        }
        int total = 0;
        PlayerInventory inventory = player.getInventory();
        for (ItemStack armor : inventory.getArmorContents()) {
            total += itemAggroBonus(armor);
        }
        return total;
    }

    public boolean isFateCurrency(ItemStack item) {
        String id = relicId(item);
        if (id == null) {
            return false;
        }
        return AGGRO_ORB_ID.equals(id)
            || STAT_ORB_ID.equals(id)
            || ENCHANT_ORB_ID.equals(id)
            || (plugin.getSeasonRelicManager() != null && plugin.getSeasonRelicManager().isSoulImprint(item));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOrbApply(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String orbId = relicId(event.getCursor());
        if (!AGGRO_ORB_ID.equals(orbId) && !STAT_ORB_ID.equals(orbId)) {
            return;
        }
        event.setCancelled(true);
        if (event.getView().getTopInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING
            || event.getClickedInventory() != player.getInventory()) {
            player.sendMessage(MessageUtil.warn("Use that orb from your inventory."));
            player.updateInventory();
            return;
        }
        if (isBlockedClick(event.getClick()) || event.isShiftClick()) {
            player.sendMessage(MessageUtil.warn("Use a normal click on one item."));
            player.updateInventory();
            return;
        }

        ItemStack target = event.getCurrentItem();
        String validation = AGGRO_ORB_ID.equals(orbId)
            ? validateAggroTarget(target)
            : validateStatTarget(target);
        if (validation != null) {
            player.sendMessage(MessageUtil.warn(validation));
            player.updateInventory();
            return;
        }

        int targetSlot = event.getSlot();
        ItemStack targetSnapshot = target.clone();
        ItemStack orbSnapshot = event.getCursor().clone();
        Bukkit.getScheduler().runTask(plugin,
            () -> applyInventoryOrb(player, targetSlot, targetSnapshot, orbSnapshot, orbId));
    }

    private void applyInventoryOrb(Player player, int targetSlot, ItemStack targetSnapshot, ItemStack orbSnapshot, String orbId) {
        if (player == null || !player.isOnline()) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack liveTarget = inventory.getItem(targetSlot);
        ItemStack liveOrb = player.getItemOnCursor();
        if (!sameStack(liveTarget, targetSnapshot) || !sameStack(liveOrb, orbSnapshot)) {
            player.sendMessage(MessageUtil.warn("Your inventory changed. The orb was not consumed."));
            player.updateInventory();
            return;
        }

        String validation = AGGRO_ORB_ID.equals(orbId)
            ? validateAggroTarget(liveTarget)
            : validateStatTarget(liveTarget);
        if (validation != null) {
            player.sendMessage(MessageUtil.warn(validation));
            player.updateInventory();
            return;
        }

        ItemStack updated = liveTarget.clone();
        if (AGGRO_ORB_ID.equals(orbId)) {
            applyAggroOrb(updated);
            player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.55f, 1.35f);
            player.sendMessage(MessageUtil.success("Added <white>+5 boss aggro</white> to " + itemNameTag(updated) + "."));
        } else {
            StatRoll roll = applyStatOrb(updated);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.65f, 1.45f);
            player.spawnParticle(Particle.WITCH, player.getLocation().add(0.0, 1.0, 0.0), 18, 0.4, 0.45, 0.4, 0.02);
            player.sendMessage(MessageUtil.success("Veilshift rolled <white>" + roll.display() + "</white> on " + itemNameTag(updated) + "."));
        }
        inventory.setItem(targetSlot, updated);
        consumeCursor(player, liveOrb);
        player.updateInventory();
    }

    private boolean sameStack(ItemStack first, ItemStack second) {
        if (isEmpty(first) || isEmpty(second)) {
            return isEmpty(first) && isEmpty(second);
        }
        return first.getAmount() == second.getAmount() && first.isSimilar(second);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStationPlace(BlockPlaceEvent event) {
        String id = relicId(event.getItemInHand());
        StationType type = StationType.fromRelicId(id);
        if (type == null) {
            return;
        }
        Block block = event.getBlockPlaced();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (block.getType() != type.blockType) {
                return;
            }
            stations.put(BlockKey.from(block), type);
            saveStations();
            ensureHologram(block, type);
            if (player.isOnline()) {
                player.sendMessage(MessageUtil.success(type.displayName + " linked."));
            }
            block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.75f, 1.2f);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStationBreak(BlockBreakEvent event) {
        BlockKey key = BlockKey.from(event.getBlock());
        StationType type = stations.get(key);
        if (type == null) {
            return;
        }
        event.setDropItems(false);
        Block block = event.getBlock();
        boolean shouldDrop = event.getPlayer().getGameMode() != GameMode.CREATIVE;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (block.getType() == type.blockType || !stations.remove(key, type)) {
                return;
            }
            removeHologram(key);
            saveStations();
            if (shouldDrop && plugin.getSeasonRelicManager() != null) {
                ItemStack drop = plugin.getSeasonRelicManager().createRelicItem(type.relicId);
                if (drop != null && !drop.getType().isAir()) {
                    block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        protectStationBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        protectStationBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStationInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        StationType type = stations.get(BlockKey.from(event.getClickedBlock()));
        if (type == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (type == StationType.RUNIC_LOOM) {
            openRunicLoom(player, BlockKey.from(event.getClickedBlock()));
        } else {
            openFateCrucible(player, BlockKey.from(event.getClickedBlock()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder(false);
        if (!(holder instanceof RunicLoomHolder) && !(holder instanceof FateCrucibleHolder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        if (!holderBelongsToPlayer(holder, player)) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }
        if (isBlockedClick(event.getClick()) || event.isShiftClick()) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.warn("Use normal clicks in this menu."));
            player.updateInventory();
            return;
        }
        if (event.getRawSlot() < 0) {
            event.setCancelled(true);
            return;
        }
        if (event.getRawSlot() >= top.getSize()) {
            return;
        }
        event.setCancelled(true);
        if (holder instanceof RunicLoomHolder runicHolder) {
            handleRunicClick(player, runicHolder, event);
            return;
        }
        handleFateClick(player, (FateCrucibleHolder) holder, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (!(holder instanceof RunicLoomHolder) && !(holder instanceof FateCrucibleHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    player.sendMessage(MessageUtil.warn("Click items into the table one at a time."));
                    Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                }
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder(false);
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (holder instanceof RunicLoomHolder) {
            returnSlot(player, top, RUNIC_ITEM_SLOT);
            returnSlot(player, top, RUNIC_ORB_SLOT);
            player.updateInventory();
        } else if (holder instanceof FateCrucibleHolder) {
            returnSlot(player, top, FATE_ITEM_SLOT);
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGuiInputDeath(PlayerDeathEvent event) {
        Inventory top = event.getEntity().getOpenInventory().getTopInventory();
        if (top.getHolder(false) instanceof RunicLoomHolder) {
            evacuateDeathInput(top, event.getDrops(), RUNIC_ITEM_SLOT);
            evacuateDeathInput(top, event.getDrops(), RUNIC_ORB_SLOT);
        } else if (top.getHolder(false) instanceof FateCrucibleHolder) {
            evacuateDeathInput(top, event.getDrops(), FATE_ITEM_SLOT);
        }
    }

    private static void evacuateDeathInput(Inventory inventory, List<ItemStack> drops, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (item != null && !item.getType().isAir()) {
            drops.add(item.clone());
            inventory.setItem(slot, null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        returnOpenStationInputs(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        returnOpenStationInputs(event.getPlayer());
        momentumStates.remove(event.getPlayer().getUniqueId());
    }

    private void returnOpenStationInputs(Player player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        InventoryHolder holder = top.getHolder(false);
        if (holder instanceof RunicLoomHolder runic && runic.playerId().equals(player.getUniqueId())) {
            returnSlot(player, top, RUNIC_ITEM_SLOT);
            returnSlot(player, top, RUNIC_ORB_SLOT);
        } else if (holder instanceof FateCrucibleHolder fate && fate.playerId().equals(player.getUniqueId())) {
            returnSlot(player, top, FATE_ITEM_SLOT);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }
        if (plugin.getTeamManager() != null && plugin.getTeamManager().sameTeam(killer.getUniqueId(), victim.getUniqueId())) {
            return;
        }
        if (!hasSoloDevice(killer) || !soloCondition(killer, true)) {
            return;
        }
        MomentumState state = momentumStates.computeIfAbsent(killer.getUniqueId(), ignored -> new MomentumState());
        state.boostUntil = System.currentTimeMillis() + MOMENTUM_KILL_WINDOW_MS;
        state.stacks = Math.min(MOMENTUM_MAX_STACKS, state.stacks + 1);
        killer.sendMessage(MessageUtil.success("Lone Star Engine gained momentum."));
        killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.55f, 1.65f);
    }

    private void openRunicLoom(Player player, BlockKey station) {
        Inventory inventory = Bukkit.createInventory(
            new RunicLoomHolder(player.getUniqueId(), station),
            45,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#7dd3fc:#c084fc><bold>Runic Loom</bold></gradient>"), "Runic Loom")
        );
        fill(inventory);
        inventory.setItem(INFO_SLOT, item(Material.ENCHANTING_TABLE, "<gradient:#7dd3fc:#c084fc><bold>Runic Loom</bold></gradient>", List.of(
            "<gray>Adds <gold>+1</gold> or <gold>+2</gold> to one enchant.</gray>",
            "<gray>Needs gear with at least <white>4 enchants</white>.</gray>",
            "<gray>Vanilla enchants cap at normal max <gold>+2</gold>.</gray>",
            "<gray>Consumes one <white>Runebloom Orb</white>.</gray>",
            "<dark_gray>Corrupted items are locked.</dark_gray>"
        )));
        inventory.setItem(RUNIC_ITEM_SLOT, null);
        inventory.setItem(RUNIC_ORB_SLOT, null);
        refreshRunicLoom(inventory);
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.55f, 1.15f);
    }

    private void openFateCrucible(Player player, BlockKey station) {
        Inventory inventory = Bukkit.createInventory(
            new FateCrucibleHolder(player.getUniqueId(), station),
            45,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#f97316:#ef4444><bold>Fate Crucible</bold></gradient>"), "Fate Crucible")
        );
        fill(inventory);
        String imprintName = soulImprintName(player);
        inventory.setItem(INFO_SLOT, item(Material.LODESTONE, "<gradient:#f97316:#ef4444><bold>Fate Crucible</bold></gradient>", List.of(
            "<gray>Put in an orb or " + imprintName + ".</gray>",
            "<gray>The whole stack is either <green>doubled</green> or <red>destroyed</red>.</gray>",
            "<gray>Odds are always <white>50/50</white>.</gray>",
            "<dark_gray>No preview items can leave this menu.</dark_gray>"
        )));
        inventory.setItem(FATE_ITEM_SLOT, null);
        refreshFateCrucible(inventory);
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.45f, 0.8f);
    }

    private void handleRunicClick(Player player, RunicLoomHolder holder, InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();
        if (rawSlot >= top.getSize()) {
            if (event.isShiftClick()) {
                player.sendMessage(MessageUtil.warn("Place items into the Loom by hand."));
                player.updateInventory();
            }
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (rawSlot == RUNIC_ITEM_SLOT || rawSlot == RUNIC_ORB_SLOT) {
            boolean itemSlot = rawSlot == RUNIC_ITEM_SLOT;
            moveCursorIntoMutableSlot(player, top, rawSlot, event.getCursor(), itemSlot ? this::isRunicItem : this::isRunicOrb, itemSlot);
            Bukkit.getScheduler().runTask(plugin, () -> refreshRunicLoom(top));
            return;
        }
        String action = menuAction(event.getCurrentItem());
        if (!"upgrade_enchant".equals(action)) {
            return;
        }
        executeRunicUpgrade(player, holder, top, menuValue(event.getCurrentItem()));
    }

    private void handleFateClick(Player player, FateCrucibleHolder holder, InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();
        if (rawSlot >= top.getSize()) {
            if (event.isShiftClick()) {
                player.sendMessage(MessageUtil.warn("Place the stack into the Crucible by hand."));
                player.updateInventory();
            }
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (rawSlot == FATE_ITEM_SLOT) {
            moveCursorIntoMutableSlot(player, top, rawSlot, event.getCursor(), this::isFateCurrency, false);
            Bukkit.getScheduler().runTask(plugin, () -> refreshFateCrucible(top));
            return;
        }
        if (rawSlot == FATE_BUTTON_SLOT && "roll_fate".equals(menuAction(event.getCurrentItem()))) {
            executeFateRoll(player, holder, top);
        }
    }

    private void executeRunicUpgrade(Player player, RunicLoomHolder holder, Inventory top, String encodedOption) {
        if (!stationStillValid(holder.station(), StationType.RUNIC_LOOM)) {
            player.closeInventory();
            player.sendMessage(MessageUtil.warn("That Runic Loom is gone."));
            return;
        }
        ItemStack item = top.getItem(RUNIC_ITEM_SLOT);
        ItemStack orb = top.getItem(RUNIC_ORB_SLOT);
        if (!isRunicItem(item)) {
            player.sendMessage(MessageUtil.warn(runicItemReason(item)));
            refreshRunicLoom(top);
            return;
        }
        if (!isRunicOrb(orb)) {
            player.sendMessage(MessageUtil.warn("Add one Runebloom Orb first."));
            refreshRunicLoom(top);
            return;
        }
        EnchantOption selected = enchantOptions(item).stream()
            .filter(option -> option.encoded().equals(encodedOption))
            .findFirst()
            .orElse(null);
        if (selected == null) {
            player.sendMessage(MessageUtil.warn("That enchant is no longer on the item."));
            refreshRunicLoom(top);
            return;
        }
        int increase = ThreadLocalRandom.current().nextBoolean() ? 1 : 2;
        ItemStack result = item.clone();
        int appliedIncrease = applyEnchantIncrease(result, selected, increase);
        if (appliedIncrease <= 0) {
            player.sendMessage(MessageUtil.warn("That enchant could not be upgraded."));
            refreshRunicLoom(top);
            return;
        }
        top.setItem(RUNIC_ITEM_SLOT, result);
        consumeOne(top, RUNIC_ORB_SLOT);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.45f);
        player.spawnParticle(Particle.ENCHANT, player.getLocation().add(0.0, 1.1, 0.0), 45, 0.6, 0.45, 0.6, 0.08);
        String capNote = appliedIncrease < increase ? " <dark_gray>(cap reached)</dark_gray>" : "";
        player.sendMessage(MessageUtil.success("Raised <white>" + selected.display() + "</white> by <gold>+" + appliedIncrease + "</gold>." + capNote));
        refreshRunicLoom(top);
    }

    private void executeFateRoll(Player player, FateCrucibleHolder holder, Inventory top) {
        if (!stationStillValid(holder.station(), StationType.FATE_CRUCIBLE)) {
            player.closeInventory();
            player.sendMessage(MessageUtil.warn("That Fate Crucible is gone."));
            return;
        }
        ItemStack input = top.getItem(FATE_ITEM_SLOT);
        if (!isFateCurrency(input)) {
            player.sendMessage(MessageUtil.warn("Put in an orb or " + soulImprintName(player) + " first."));
            refreshFateCrucible(top);
            return;
        }
        top.setItem(FATE_ITEM_SLOT, null);
        boolean success = ThreadLocalRandom.current().nextBoolean();
        if (success) {
            List<ItemStack> doubled = splitStack(input, Math.max(1, input.getAmount()) * 2);
            for (ItemStack stack : doubled) {
                returnOrDrop(player, stack);
            }
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.85f, 1.2f);
            player.spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0.0, 1.0, 0.0), 35, 0.45, 0.45, 0.45, 0.03);
            player.sendMessage(MessageUtil.success("Fate doubled <white>" + itemDisplayName(input) + "</white>."));
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.9f, 0.75f);
            player.spawnParticle(Particle.SMOKE, player.getLocation().add(0.0, 1.0, 0.0), 25, 0.45, 0.4, 0.45, 0.02);
            player.sendMessage(MessageUtil.warn("Fate destroyed <white>" + itemDisplayName(input) + "</white>."));
        }
        refreshFateCrucible(top);
        player.updateInventory();
    }

    private void refreshRunicLoom(Inventory inventory) {
        for (int slot : ENCHANT_OPTION_SLOTS) {
            inventory.setItem(slot, null);
        }
        ItemStack item = inventory.getItem(RUNIC_ITEM_SLOT);
        ItemStack orb = inventory.getItem(RUNIC_ORB_SLOT);
        if (isEmpty(item)) {
            inventory.setItem(RUNIC_ITEM_SLOT, ghost(Material.NETHERITE_SWORD, "<gray>Gear Slot</gray>", "<dark_gray>Place armor, a tool, or a weapon.</dark_gray>"));
        }
        if (isEmpty(orb)) {
            inventory.setItem(RUNIC_ORB_SLOT, ghost(Material.EXPERIENCE_BOTTLE, "<gray>Orb Slot</gray>", "<dark_gray>Consumes one Runebloom Orb.</dark_gray>"));
        }
        String reason = runicItemReason(item);
        if (reason != null) {
            inventory.setItem(RUNIC_STATUS_SLOT, item(Material.BARRIER, "<red><bold>Not Ready</bold></red>", List.of("<gray>" + reason + "</gray>")));
            return;
        }
        if (!isRunicOrb(orb)) {
            inventory.setItem(RUNIC_STATUS_SLOT, item(Material.EXPERIENCE_BOTTLE, "<yellow><bold>Add Runebloom Orb</bold></yellow>", List.of("<gray>One orb is consumed per upgrade.</gray>")));
            return;
        }
        List<EnchantOption> options = enchantOptions(item);
        if (options.isEmpty()) {
            inventory.setItem(RUNIC_STATUS_SLOT, item(Material.BARRIER, "<red><bold>No Enchants Found</bold></red>", List.of("<gray>This item has no upgradeable enchants.</gray>")));
            return;
        }
        inventory.setItem(RUNIC_STATUS_SLOT, item(Material.LIME_DYE, "<green><bold>Choose An Enchant</bold></green>", List.of("<gray>Click one enchant below.</gray>", "<gray>Rolls <gold>+1</gold> or <gold>+2</gold>, capped safely.</gray>")));
        options.sort(Comparator.comparing(EnchantOption::display, String.CASE_INSENSITIVE_ORDER));
        for (int i = 0; i < options.size() && i < ENCHANT_OPTION_SLOTS.length; i++) {
            EnchantOption option = options.get(i);
            ItemStack icon = item(Material.ENCHANTED_BOOK, "<aqua><bold>" + miniEscape(option.display()) + "</bold></aqua>", List.of(
                "<gray>Current:</gray> <white>" + option.level() + "</white><dark_gray>/</dark_gray><white>" + option.maxLevel() + "</white>",
                "<gray>Result:</gray> <gold>" + runicResultPreview(option) + "</gold>",
                "<yellow>Click to upgrade.</yellow>"
            ));
            tagMenu(icon, "upgrade_enchant", option.encoded());
            inventory.setItem(ENCHANT_OPTION_SLOTS[i], icon);
        }
    }

    private void refreshFateCrucible(Inventory inventory) {
        String imprintName = soulImprintName(fateViewer(inventory));
        if (isEmpty(inventory.getItem(FATE_ITEM_SLOT))) {
            inventory.setItem(FATE_ITEM_SLOT, ghost(Material.END_CRYSTAL, "<gray>Risk Slot</gray>", "<dark_gray>Put in a stack of orbs or " + imprintName + ".</dark_gray>"));
        }
        if (isFateCurrency(inventory.getItem(FATE_ITEM_SLOT))) {
            inventory.setItem(FATE_BUTTON_SLOT, actionItem(Material.REDSTONE_TORCH, "<gradient:#f97316:#ef4444><bold>Tempt Fate</bold></gradient>", List.of(
                "<gray>50% double the entire stack.</gray>",
                "<gray>50% delete it all.</gray>",
                "<yellow>Click when you are sure.</yellow>"
            ), "roll_fate"));
        } else {
            inventory.setItem(FATE_BUTTON_SLOT, item(Material.GRAY_DYE, "<gray><bold>Waiting</bold></gray>", List.of("<gray>Add an orb or " + imprintName + " first.</gray>")));
        }
    }

    private Player fateViewer(Inventory inventory) {
        if (inventory != null && inventory.getHolder(false) instanceof FateCrucibleHolder holder) {
            return Bukkit.getPlayer(holder.playerId());
        }
        return null;
    }

    private String soulImprintName(Player player) {
        return plugin.getSeasonRelicManager() == null
            ? "<obfuscated>Soul Imprint</obfuscated>"
            : plugin.getSeasonRelicManager().soulImprintDisplayName(player);
    }

    private void moveCursorIntoMutableSlot(Player player, Inventory top, int slot, ItemStack cursor, java.util.function.Predicate<ItemStack> allowed, boolean requireSingle) {
        ItemStack current = top.getItem(slot);
        if (isGhost(current)) {
            current = null;
        }
        if (isEmpty(cursor)) {
            if (!isEmpty(current)) {
                top.setItem(slot, null);
                player.setItemOnCursor(current);
            }
            return;
        }
        if (!isEmpty(current)) {
            player.sendMessage(MessageUtil.warn("Take the current item out first."));
            return;
        }
        if (requireSingle && cursor.getAmount() != 1) {
            player.sendMessage(MessageUtil.warn("Split that item to one first."));
            return;
        }
        if (!allowed.test(cursor)) {
            player.sendMessage(MessageUtil.warn("That item does not fit here."));
            return;
        }
        int movedAmount = requireSingle ? cursor.getAmount() : Math.max(1, cursor.getAmount());
        ItemStack placed = cursor.clone();
        placed.setAmount(movedAmount);
        top.setItem(slot, placed);
        player.setItemOnCursor(null);
    }

    private void tickMomentumDevices() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!hasSoloDevice(player)) {
                momentumStates.remove(player.getUniqueId());
                continue;
            }
            MomentumState state = momentumStates.computeIfAbsent(player.getUniqueId(), ignored -> new MomentumState());
            boolean active = soloCondition(player, true);
            if (!active) {
                state.active = false;
                if (state.boostUntil <= now) {
                    state.stacks = 0;
                }
                continue;
            }
            state.active = true;
            if (state.boostUntil <= now) {
                state.stacks = 0;
            }
            applyMomentumEffects(player, state.stacks);
            if (!player.getPersistentDataContainer().has(keyMomentumDeviceSeen, PersistentDataType.BYTE)) {
                player.getPersistentDataContainer().set(keyMomentumDeviceSeen, PersistentDataType.BYTE, (byte) 1);
                player.sendMessage(MessageUtil.info("Lone Star Engine wakes up while you fight alone."));
            }
        }
    }

    private void applyMomentumEffects(Player player, int stacks) {
        int strength = stacks >= 2 ? 1 : 0;
        int speed = stacks >= 1 ? 1 : 0;
        int resistance = stacks >= 3 ? 1 : 0;
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, MOMENTUM_EFFECT_TICKS, strength, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, MOMENTUM_EFFECT_TICKS, speed, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, MOMENTUM_EFFECT_TICKS, resistance, true, false, true));
        if (stacks > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, MOMENTUM_EFFECT_TICKS, Math.min(2, stacks - 1), true, false, true));
        }
    }

    private boolean soloCondition(Player player, boolean requireEnemy) {
        if (player == null || !player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        int enemies = 0;
        double enemyRadiusSquared = SOLO_CHECK_RADIUS * SOLO_CHECK_RADIUS;
        double teamRadiusSquared = SOLO_TEAM_RADIUS * SOLO_TEAM_RADIUS;
        for (Player other : player.getWorld().getPlayers()) {
            if (other.equals(player) || other.isDead() || other.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            double distanceSquared = other.getLocation().distanceSquared(player.getLocation());
            boolean sameTeam = plugin.getTeamManager() != null && plugin.getTeamManager().sameTeam(player.getUniqueId(), other.getUniqueId());
            if (sameTeam && distanceSquared <= teamRadiusSquared) {
                return false;
            }
            if (!sameTeam && distanceSquared <= enemyRadiusSquared) {
                enemies++;
            }
        }
        return !requireEnemy || enemies > 0;
    }

    private boolean hasSoloDevice(Player player) {
        if (player == null) {
            return false;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (SOLO_DEVICE_ID.equals(relicId(item))) {
                return true;
            }
        }
        return false;
    }

    private int itemAggroBonus(ItemStack item) {
        if (isEmpty(item)) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        Integer value = meta.getPersistentDataContainer().get(keyAggroBonus, PersistentDataType.INTEGER);
        return value == null ? 0 : Math.max(0, value);
    }

    private String validateAggroTarget(ItemStack item) {
        if (isEmpty(item)) {
            return "Click one armor piece.";
        }
        if (item.getAmount() != 1) {
            return "Split the armor to one item first.";
        }
        if (!isArmor(item.getType())) {
            return "Boss aggro orbs only work on armor.";
        }
        if (isCorruptionLocked(item)) {
            return "That item is locked.";
        }
        if (itemAggroBonus(item) > 0) {
            return "That armor already has boss aggro.";
        }
        return null;
    }

    private String validateStatTarget(ItemStack item) {
        if (isEmpty(item)) {
            return "Click one armor, weapon, or tool.";
        }
        if (item.getAmount() != 1) {
            return "Split that item to one first.";
        }
        if (!isGear(item)) {
            return "Veilshift Orbs only work on gear.";
        }
        if (isCorruptionLocked(item)) {
            return "That item is locked.";
        }
        return null;
    }

    private void applyAggroOrb(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(keyAggroBonus, PersistentDataType.INTEGER, AGGRO_ORB_BONUS);
        List<Component> lore = CustomLoreUtil.removeManagedLines(meta.lore(), Set.of("Boss Aggro:"));
        CustomLoreUtil.addSpacer(lore);
        lore.add(MM.deserialize("<dark_aqua><bold>Boss Aggro:</bold></dark_aqua> <aqua>+5 boss focus</aqua>"));
        meta.lore(CustomLoreUtil.normalizeLore(lore));
        item.setItemMeta(meta);
    }

    private StatRoll applyStatOrb(ItemStack item) {
        List<StatRoll> rolls = statRolls(item.getType());
        StatRoll roll = rolls.get(ThreadLocalRandom.current().nextInt(rolls.size()));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return roll;
        }
        removeVeilshiftModifier(meta);
        meta.addAttributeModifier(roll.attribute(), new AttributeModifier(
            keyStatOrbModifier,
            roll.amount(),
            AttributeModifier.Operation.ADD_NUMBER,
            roll.slot()
        ));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyStatOrbKind, PersistentDataType.STRING, roll.id());
        pdc.set(keyStatOrbAmount, PersistentDataType.DOUBLE, roll.amount());
        List<Component> lore = CustomLoreUtil.removeManagedLines(meta.lore(), Set.of("Veilshift:"));
        CustomLoreUtil.addSpacer(lore);
        lore.add(MM.deserialize("<light_purple><bold>Veilshift:</bold></light_purple> <white>" + roll.display() + "</white>"));
        meta.lore(CustomLoreUtil.normalizeLore(lore));
        item.setItemMeta(meta);
        return roll;
    }

    private void removeVeilshiftModifier(ItemMeta meta) {
        Multimap<Attribute, AttributeModifier> existing = meta.getAttributeModifiers();
        if (existing == null || existing.isEmpty()) {
            return;
        }
        Multimap<Attribute, AttributeModifier> rewritten = ArrayListMultimap.create();
        for (Map.Entry<Attribute, AttributeModifier> entry : existing.entries()) {
            AttributeModifier modifier = entry.getValue();
            if (keyStatOrbModifier.equals(modifier.getKey())) {
                continue;
            }
            rewritten.put(entry.getKey(), modifier);
        }
        meta.setAttributeModifiers(rewritten.isEmpty() ? null : rewritten);
    }

    private List<StatRoll> statRolls(Material material) {
        List<StatRoll> rolls = new ArrayList<>();
        EquipmentSlotGroup armorSlot = armorSlot(material);
        if (armorSlot != null) {
            rolls.add(new StatRoll("armor", Attribute.ARMOR, 1.0D, armorSlot, "+1 Armor"));
            rolls.add(new StatRoll("toughness", Attribute.ARMOR_TOUGHNESS, 0.75D, armorSlot, "+0.75 Armor Toughness"));
            rolls.add(new StatRoll("speed", Attribute.MOVEMENT_SPEED, 0.012D, armorSlot, "+0.012 Movement Speed"));
            return rolls;
        }
        rolls.add(new StatRoll("damage", Attribute.ATTACK_DAMAGE, 1.5D, EquipmentSlotGroup.MAINHAND, "+1.5 Attack Damage"));
        rolls.add(new StatRoll("speed", Attribute.ATTACK_SPEED, 0.12D, EquipmentSlotGroup.MAINHAND, "+0.12 Attack Speed"));
        rolls.add(new StatRoll("knockback", Attribute.ATTACK_KNOCKBACK, 0.35D, EquipmentSlotGroup.MAINHAND, "+0.35 Attack Knockback"));
        return rolls;
    }

    private String runicItemReason(ItemStack item) {
        if (isGhost(item) || isEmpty(item)) {
            return "Place one armor, weapon, or tool.";
        }
        if (item.getAmount() != 1) {
            return "Split the item to one first.";
        }
        if (!isGear(item)) {
            return "Only armor, weapons, and tools can be upgraded.";
        }
        if (isCorruptionLocked(item)) {
            return "Corrupted items are locked.";
        }
        if (CustomLoreUtil.hasAnyEnchantConflict(item)) {
            return "Resolve conflicting enchants before using the Loom.";
        }
        if (runicEnchantCount(item) < RUNIC_REQUIRED_ENCHANTS) {
            return "This item needs at least 4 enchants.";
        }
        return null;
    }

    private boolean isRunicItem(ItemStack item) {
        return runicItemReason(item) == null;
    }

    private boolean isRunicOrb(ItemStack item) {
        return ENCHANT_ORB_ID.equals(relicId(item));
    }

    private List<EnchantOption> enchantOptions(ItemStack item) {
        if (isEmpty(item)) {
            return List.of();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return List.of();
        }
        List<EnchantOption> out = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
            Enchantment enchantment = entry.getKey();
            if (!isRunicVanillaEnchant(enchantment)) {
                continue;
            }
            int current = Math.max(1, entry.getValue());
            int maxLevel = runicMaxLevel(enchantment);
            if (current >= maxLevel) {
                continue;
            }
            NamespacedKey key = enchantment.getKey();
            out.add(new EnchantOption("vanilla", key.toString(), prettyEnchantName(key), current, maxLevel));
        }
        if (plugin.getCustomEnchantListener() != null) {
            for (CustomEnchantListener.ManagedEnchantOption option : plugin.getCustomEnchantListener().managedEnchantOptions(item)) {
                int current = Math.max(1, option.level());
                int maxLevel = Math.max(current, option.maxLevel());
                if (current < maxLevel) {
                    out.add(new EnchantOption("custom", option.id(), option.displayName(), current, maxLevel));
                }
            }
        }
        return out;
    }

    private int applyEnchantIncrease(ItemStack item, EnchantOption option, int increase) {
        if ("custom".equals(option.type())) {
            int applied = plugin.getCustomEnchantListener() == null
                ? 0
                : plugin.getCustomEnchantListener().upgradeManagedEnchant(item, option.key(), increase);
            if (applied > 0) {
                CustomLoreUtil.refreshEnchantLore(item);
            }
            return applied;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        Enchantment enchantment = null;
        for (Enchantment candidate : meta.getEnchants().keySet()) {
            if (candidate.getKey().toString().equals(option.key())) {
                enchantment = candidate;
                break;
            }
        }
        if (enchantment == null || !isRunicVanillaEnchant(enchantment)) {
            return 0;
        }
        int current = Math.max(1, meta.getEnchantLevel(enchantment));
        int next = Math.min(runicMaxLevel(enchantment), current + Math.max(1, increase));
        if (next <= current) {
            return 0;
        }
        meta.addEnchant(enchantment, next, true);
        item.setItemMeta(meta);
        CustomLoreUtil.refreshEnchantLore(item);
        return next - current;
    }

    private int runicEnchantCount(ItemStack item) {
        if (isEmpty(item)) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        int count = meta.getEnchants().size();
        if (plugin.getCustomEnchantListener() != null) {
            count += plugin.getCustomEnchantListener().managedEnchantOptions(item).size();
        }
        return count;
    }

    private boolean isRunicVanillaEnchant(Enchantment enchantment) {
        if (enchantment == null) {
            return false;
        }
        NamespacedKey key = enchantment.getKey();
        return key != null
            && "minecraft".equals(key.getNamespace())
            && enchantment.getMaxLevel() > 1
            && RUNIC_SCALING_ENCHANTS.contains(key.getKey());
    }

    private int runicMaxLevel(Enchantment enchantment) {
        return Math.max(1, enchantment.getMaxLevel()) + RUNIC_EXTRA_MAX_LEVELS;
    }

    private String runicResultPreview(EnchantOption option) {
        int low = Math.min(option.maxLevel(), option.level() + 1);
        int high = Math.min(option.maxLevel(), option.level() + 2);
        if (low == high) {
            return Integer.toString(low);
        }
        return low + "-" + high;
    }

    private boolean stationStillValid(BlockKey key, StationType type) {
        if (key == null || type == null || stations.get(key) != type) {
            return false;
        }
        Location location = key.location();
        return location != null && location.getBlock().getType() == type.blockType;
    }

    public boolean isStationBlock(Block block) {
        if (block == null) {
            return false;
        }
        StationType type = stations.get(BlockKey.from(block));
        return type != null && block.getType() == type.blockType;
    }

    private void protectStationBlocks(List<Block> blocks) {
        blocks.removeIf(block -> {
            StationType type = stations.get(BlockKey.from(block));
            return type != null && block.getType() == type.blockType;
        });
    }

    private void syncLoadedStations() {
        boolean changed = false;
        for (Map.Entry<BlockKey, StationType> entry : new ArrayList<>(stations.entrySet())) {
            Location location = entry.getKey().location();
            if (location == null) {
                continue;
            }
            if (location.getBlock().getType() != entry.getValue().blockType) {
                stations.remove(entry.getKey());
                removeHologram(entry.getKey());
                changed = true;
                continue;
            }
            ensureHologram(location.getBlock(), entry.getValue());
        }
        if (changed) {
            saveStations();
        }
    }

    private void ensureHologram(Block block, StationType type) {
        if (block == null || type == null) {
            return;
        }
        BlockKey key = BlockKey.from(block);
        String rawKey = key.asString();
        UUID existingId = hologramsByBlock.get(rawKey);
        Entity existing = existingId == null ? null : Bukkit.getEntity(existingId);
        Location location = block.getLocation().add(0.5, 1.45, 0.5);
        if (existing instanceof TextDisplay display && display.isValid()) {
            display.teleport(location);
            display.text(hologramText(type));
            return;
        }
        removeHologram(key);
        TextDisplay display = block.getWorld().spawn(location, TextDisplay.class, text -> {
            text.text(hologramText(type));
            text.setBillboard(Display.Billboard.CENTER);
            text.setGravity(false);
            text.setPersistent(false);
            text.setInvulnerable(true);
            text.setSeeThrough(false);
            text.setShadowed(true);
            text.setDefaultBackground(false);
            text.setViewRange(VisualRangeUtil.blocksToDisplayViewRange(24.0D));
            text.getPersistentDataContainer().set(keyStationHologram, PersistentDataType.BYTE, (byte) 1);
            text.getPersistentDataContainer().set(keyStationHologramBlock, PersistentDataType.STRING, rawKey);
        });
        hologramsByBlock.put(rawKey, display.getUniqueId());
    }

    private Component hologramText(StationType type) {
        return MM.deserialize(type.hologram);
    }

    private void removeHologram(BlockKey key) {
        if (key == null) {
            return;
        }
        UUID id = hologramsByBlock.remove(key.asString());
        Entity entity = id == null ? null : Bukkit.getEntity(id);
        if (entity != null) {
            entity.remove();
        }
    }

    private void loadStations() {
        stations.clear();
        if (!stationFile.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(stationFile);
        for (String raw : config.getStringList("stations")) {
            StationRecord record = StationRecord.parse(raw);
            if (record != null) {
                stations.put(record.key(), record.type());
            }
        }
    }

    private void saveStations() {
        YamlConfiguration config = new YamlConfiguration();
        List<String> values = stations.entrySet().stream()
            .map(entry -> new StationRecord(entry.getKey(), entry.getValue()).asString())
            .sorted()
            .toList();
        config.set("stations", values);
        try {
            AtomicYamlFile.save(config, stationFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save veil orb stations: " + e.getMessage());
        }
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, MenuItemUtil.INACTIVE_SLOT_LORE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "<red>Close</red>", List.of("<gray>Return any items in the table.</gray>")));
    }

    private ItemStack item(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        List<String> visibleLore = MenuItemUtil.visibleMiniLore(name, loreLines);
        meta.displayName(MM.deserialize(MenuItemUtil.visibleMiniName(name)).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : visibleLore) {
            lore.add(MM.deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack actionItem(Material material, String name, List<String> lore, String action) {
        ItemStack item = item(material, name, lore);
        tagMenu(item, action, "");
        return item;
    }

    private ItemStack ghost(Material material, String name, String lore) {
        ItemStack item = item(material, name, List.of(lore));
        tagMenu(item, "ghost", "");
        return item;
    }

    private void tagMenu(ItemStack item, String action, String value) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyMenuAction, PersistentDataType.STRING, action == null ? "" : action);
        pdc.set(keyMenuValue, PersistentDataType.STRING, value == null ? "" : value);
        item.setItemMeta(meta);
    }

    private String menuAction(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(keyMenuAction, PersistentDataType.STRING);
    }

    private String menuValue(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null) {
            return "";
        }
        String value = meta.getPersistentDataContainer().get(keyMenuValue, PersistentDataType.STRING);
        return value == null ? "" : value;
    }

    private boolean isGhost(ItemStack item) {
        return "ghost".equals(menuAction(item));
    }

    private boolean holderBelongsToPlayer(InventoryHolder holder, Player player) {
        if (holder instanceof RunicLoomHolder runic) {
            return runic.playerId().equals(player.getUniqueId());
        }
        if (holder instanceof FateCrucibleHolder fate) {
            return fate.playerId().equals(player.getUniqueId());
        }
        return false;
    }

    private boolean isBlockedClick(ClickType click) {
        return click != ClickType.LEFT && click != ClickType.RIGHT;
    }

    private void consumeCursor(Player player, ItemStack cursor) {
        if (isEmpty(cursor) || cursor.getAmount() <= 1) {
            player.setItemOnCursor(null);
            return;
        }
        ItemStack remaining = cursor.clone();
        remaining.setAmount(cursor.getAmount() - 1);
        player.setItemOnCursor(remaining);
    }

    private void consumeOne(Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (isEmpty(item) || isGhost(item)) {
            inventory.setItem(slot, null);
            return;
        }
        if (item.getAmount() <= 1) {
            inventory.setItem(slot, null);
            return;
        }
        item.setAmount(item.getAmount() - 1);
    }

    private void returnSlot(Player player, Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (isEmpty(item) || isGhost(item)) {
            inventory.setItem(slot, null);
            return;
        }
        inventory.setItem(slot, null);
        returnOrDrop(player, item);
    }

    private void returnOrDrop(Player player, ItemStack item) {
        if (isEmpty(item)) {
            return;
        }
        player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private List<ItemStack> splitStack(ItemStack source, int totalAmount) {
        if (isEmpty(source) || totalAmount <= 0) {
            return List.of();
        }
        List<ItemStack> out = new ArrayList<>();
        int max = Math.max(1, source.getMaxStackSize());
        int remaining = totalAmount;
        while (remaining > 0) {
            int amount = Math.min(max, remaining);
            ItemStack stack = source.clone();
            stack.setAmount(amount);
            out.add(stack);
            remaining -= amount;
        }
        return out;
    }

    private boolean isCorruptionLocked(ItemStack item) {
        return plugin.getCorruptionManager() != null && plugin.getCorruptionManager().isCorruptionLocked(item);
    }

    private String relicId(ItemStack item) {
        return plugin.getSeasonRelicManager() == null ? null : plugin.getSeasonRelicManager().relicId(item);
    }

    private boolean isGear(ItemStack item) {
        return !isEmpty(item) && isGear(item.getType());
    }

    private boolean isGear(Material material) {
        return isArmor(material) || isToolOrWeapon(material);
    }

    private boolean isToolOrWeapon(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return name.endsWith("_SWORD")
            || name.endsWith("_AXE")
            || name.endsWith("_PICKAXE")
            || name.endsWith("_SHOVEL")
            || name.endsWith("_HOE")
            || material == Material.BOW
            || material == Material.CROSSBOW
            || material == Material.TRIDENT
            || material == Material.MACE
            || material == Material.SHEARS
            || material == Material.FISHING_ROD
            || material == Material.FLINT_AND_STEEL
            || material == Material.BRUSH;
    }

    private boolean isArmor(Material material) {
        return armorSlot(material) != null;
    }

    private EquipmentSlotGroup armorSlot(Material material) {
        if (material == null) {
            return null;
        }
        String name = material.name();
        if (name.endsWith("_HELMET") || material == Material.TURTLE_HELMET) return EquipmentSlotGroup.HEAD;
        if (name.endsWith("_CHESTPLATE") || material == Material.ELYTRA) return EquipmentSlotGroup.CHEST;
        if (name.endsWith("_LEGGINGS")) return EquipmentSlotGroup.LEGS;
        if (name.endsWith("_BOOTS")) return EquipmentSlotGroup.FEET;
        return null;
    }

    private String itemNameTag(ItemStack item) {
        return "<white>" + miniEscape(itemDisplayName(item)) + "</white>";
    }

    private String itemDisplayName(ItemStack item) {
        if (isEmpty(item)) {
            return "Unknown Item";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.displayName() != null) {
            return PLAIN.serialize(meta.displayName());
        }
        return prettyMaterialName(item.getType());
    }

    private String prettyMaterialName(Material material) {
        if (material == null) {
            return "Unknown Item";
        }
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return out.toString();
    }

    private String prettyEnchantName(NamespacedKey key) {
        if (key == null) {
            return "Unknown Enchant";
        }
        return prettyRawName(key.getKey());
    }

    private String prettyRawName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Unknown";
        }
        String[] parts = raw.toLowerCase(Locale.ROOT).split("[_./-]");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return out.toString();
    }

    private String miniEscape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("<", "\\<");
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private enum StationType {
        RUNIC_LOOM(RUNIC_LOOM_ID, Material.ENCHANTING_TABLE, "Runic Loom", "<gradient:#7dd3fc:#c084fc><bold>Runic Loom</bold></gradient><newline><gray>Click to upgrade enchants</gray>"),
        FATE_CRUCIBLE(FATE_CRUCIBLE_ID, Material.LODESTONE, "Fate Crucible", "<gradient:#f97316:#ef4444><bold>Fate Crucible</bold></gradient><newline><gray>Click to tempt fate</gray>");

        private final String relicId;
        private final Material blockType;
        private final String displayName;
        private final String hologram;

        StationType(String relicId, Material blockType, String displayName, String hologram) {
            this.relicId = relicId;
            this.blockType = blockType;
            this.displayName = displayName;
            this.hologram = hologram;
        }

        private static StationType fromRelicId(String relicId) {
            for (StationType type : values()) {
                if (type.relicId.equals(relicId)) {
                    return type;
                }
            }
            return null;
        }

        private static StationType fromId(String id) {
            if (id == null) {
                return null;
            }
            for (StationType type : values()) {
                if (type.name().equalsIgnoreCase(id) || type.relicId.equalsIgnoreCase(id)) {
                    return type;
                }
            }
            return null;
        }
    }

    private record StatRoll(String id, Attribute attribute, double amount, EquipmentSlotGroup slot, String display) {
    }

    private record EnchantOption(String type, String key, String display, int level, int maxLevel) {
        private String encoded() {
            return type + ":" + key;
        }
    }

    private static final class MomentumState {
        private boolean active;
        private long boostUntil;
        private int stacks;
    }

    private record StationRecord(BlockKey key, StationType type) {
        private static StationRecord parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String[] parts = raw.split(":");
            if (parts.length != 5) {
                return null;
            }
            try {
                BlockKey key = new BlockKey(UUID.fromString(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                StationType type = StationType.fromId(parts[4]);
                return type == null ? null : new StationRecord(key, type);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        private String asString() {
            return key.asString() + ":" + type.name();
        }
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {
        private static BlockKey from(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        private String asString() {
            return worldId + ":" + x + ":" + y + ":" + z;
        }

        private Location location() {
            World world = Bukkit.getWorld(worldId);
            return world == null ? null : new Location(world, x, y, z);
        }
    }

    private record RunicLoomHolder(UUID playerId, BlockKey station) implements InventoryHolder, MenuDupeGuardListener.RecoveryTrackedMenuHolder {
        @Override public String recoverySurface() { return "Runic Loom"; }
        @Override public int[] recoverySlots() { return new int[] { RUNIC_ITEM_SLOT, RUNIC_ORB_SLOT }; }
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record FateCrucibleHolder(UUID playerId, BlockKey station) implements InventoryHolder, MenuDupeGuardListener.RecoveryTrackedMenuHolder {
        @Override public String recoverySurface() { return "Fate Crucible"; }
        @Override public int[] recoverySlots() { return new int[] { FATE_ITEM_SLOT }; }
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
