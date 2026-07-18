package me.rique.smpcore.quest;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.compat.BedrockSkullManager;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class GoblinHuntManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String HEAD_DATABASE_ID = "89260";
    private static final int ESSENCE_PER_GOBLIN = 5;
    private static final int FINDINGS_PER_TURN_IN = 5;
    private static final double MAX_MINING_LUCK = 0.20D;
    private static final double HUNT_DAMAGE_BONUS = 0.02D;
    private static final Set<Material> ORES = Set.of(
        Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
        Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
        Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
        Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE,
        Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
        Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
        Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
        Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
        Material.NETHER_QUARTZ_ORE
    );
    private static final Set<Material> RAW_ORE_DROPS = Set.of(
        Material.COAL, Material.RAW_IRON, Material.RAW_COPPER, Material.RAW_GOLD,
        Material.GOLD_NUGGET, Material.REDSTONE, Material.LAPIS_LAZULI,
        Material.DIAMOND, Material.EMERALD, Material.QUARTZ
    );

    private final SMPCore plugin;
    private final File dataFile;
    private final Map<BlockKey, Integer> placements = new HashMap<>();
    private final Set<Integer> activeIds = new HashSet<>();
    private final Set<BlockKey> placedOres = new HashSet<>();
    private final Set<BlockKey> fortuneEligiblePlacedOres = new HashSet<>();
    private final Map<BlockKey, NaturalOreBreak> naturalOreBreaks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> conversations = new ConcurrentHashMap<>();
    private final NamespacedKey headMarkerKey;
    private final NamespacedKey headIdKey;
    private final NamespacedKey foundKey;
    private final NamespacedKey turnedInKey;
    private final NamespacedKey introducedKey;
    private final NamespacedKey menuActionKey;
    private final NamespacedKey naturalSilkOreKey;
    private int nextId = 1;
    private BukkitTask cleanupTask;
    private BukkitTask headRegistrationTask;
    private BukkitTask saveTask;

    public GoblinHuntManager(SMPCore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "goblin-hunt.yml");
        this.headMarkerKey = new NamespacedKey(plugin, "goblin_collectible");
        this.headIdKey = new NamespacedKey(plugin, "goblin_collectible_id");
        this.foundKey = new NamespacedKey(plugin, "goblin_findings");
        this.turnedInKey = new NamespacedKey(plugin, "goblin_findings_turned_in");
        this.introducedKey = new NamespacedKey(plugin, "goblin_hunter_intro");
        this.menuActionKey = new NamespacedKey(plugin, "goblin_menu_action");
        this.naturalSilkOreKey = new NamespacedKey(plugin, "natural_silk_ore");
    }

    public void start() {
        load();
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupOreBreaks, 100L, 100L);
        headRegistrationTask = Bukkit.getScheduler().runTaskLater(plugin, () -> createGoblinHead(0), 100L);
    }

    public void shutdown() {
        if (cleanupTask != null) cleanupTask.cancel();
        if (headRegistrationTask != null) headRegistrationTask.cancel();
        if (saveTask != null) saveTask.cancel();
        saveTask = null;
        for (BukkitTask task : conversations.values()) task.cancel();
        conversations.clear();
        naturalOreBreaks.clear();
        saveNow();
    }

    public int activeGoblinCount() {
        return placements.size();
    }

    public void openFromHunter(Player player) {
        if (player.getPersistentDataContainer().has(introducedKey, PersistentDataType.BYTE)) {
            openMenu(player);
            return;
        }
        if (conversations.containsKey(player.getUniqueId())) return;
        String[] lines = {
            "<gold>Grikk:</gold> <white>Spawn has a goblin problem. Small ones. Sneaky ones.</white>",
            "<gold>Grikk:</gold> <white>Find their hidden heads and report what you discover.</white>",
            "<gold>Grikk:</gold> <white>Each find holds 5 Essence. Every five findings improves your mining luck.</white>",
            "<gold>Grikk:</gold> <white>Bring me enough evidence and your ore hauls may double.</white>"
        };
        final int[] index = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                BukkitTask current = conversations.remove(player.getUniqueId());
                if (current != null) current.cancel();
                return;
            }
            if (index[0] < lines.length) {
                player.sendMessage(MM.deserialize(lines[index[0]++]));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 0.45f, 0.85f + index[0] * 0.05f);
                return;
            }
            player.getPersistentDataContainer().set(introducedKey, PersistentDataType.BYTE, (byte) 1);
            BukkitTask current = conversations.remove(player.getUniqueId());
            if (current != null) current.cancel();
            openMenu(player);
        }, 1L, 32L);
        conversations.put(player.getUniqueId(), task);
    }

    public void openMenu(Player player) {
        if (!player.getPersistentDataContainer().has(introducedKey, PersistentDataType.BYTE)
            && !player.hasPermission("smpcore.goblins.admin")) {
            player.sendMessage(MessageUtil.warn("Speak with Grikk the Goblin Hunter first."));
            return;
        }
        int total = activeGoblinCount();
        BitSet found = findings(player);
        int foundTotal = found.cardinality();
        int foundActive = activeFoundCount(found);
        int turnedIn = turnedIn(player);
        int available = Math.max(0, foundTotal - turnedIn);
        double luck = miningLuck(player);
        boolean huntComplete = completedActiveHunt(found, activeIds);
        Inventory inventory = Bukkit.createInventory(new GoblinMenuHolder(player.getUniqueId()), 27,
            BedrockCompat.menuTitle(player, MM.deserialize("<dark_green><bold>Goblin Hunt</bold></dark_green>"), "Goblin Hunt"));
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, List.of(), null);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        inventory.setItem(10, item(goblinMenuHead(), "<green><bold>GOBLINS FOUND</bold></green>", List.of(
            "<gray>Active map progress: <white>" + foundActive + "/" + total + "</white></gray>",
            "<gray>Lifetime findings: <white>" + foundTotal + "</white></gray>",
            "<gray>Each new goblin gives <light_purple>5 Essence</light_purple>.</gray>"
        ), null));
        inventory.setItem(13, item(Material.RAW_IRON, "<gold><bold>MINING LUCK</bold></gold>", List.of(
            "<gray>Current double chance: <green>" + percent(luck) + "</green></gray>",
            "<gray>Turned in: <white>" + turnedIn + " findings</white></gray>",
            "<gray>Scales against all <white>" + total + " active goblins</white>.</gray>",
            "<dark_gray>Maximum: 20% extra raw ore drops.</dark_gray>"
        ), null));
        boolean ready = available >= FINDINGS_PER_TURN_IN && total > 0;
        inventory.setItem(16, item(ready ? Material.LIME_DYE : Material.GRAY_DYE,
            ready ? "<green><bold>TURN IN 5 FINDINGS</bold></green>" : "<gray>NEED 5 FINDINGS</gray>", List.of(
                "<gray>Unspent findings: <white>" + available + "</white></gray>",
                ready ? "<yellow>Click to improve Mining Luck.</yellow>" : "<gray>Keep searching around spawn.</gray>"
            ), ready ? "turn_in" : null));
        inventory.setItem(19, item(huntComplete ? Material.DIAMOND_SWORD : Material.STONE_SWORD,
            huntComplete ? "<green><bold>GOBLIN SLAYER ACTIVE</bold></green>" : "<gray>GOBLIN SLAYER LOCKED</gray>", List.of(
                "<gray>Find every active goblin.</gray>",
                "<gray>Reward: <red>+2% player and mob damage</red>.</gray>",
                huntComplete ? "<green>All " + total + " active goblins found.</green>" : "<gray>Progress: <white>" + foundActive + "/" + total + "</white></gray>"
            ), null));
        inventory.setItem(22, item(Material.BARRIER, "<red><bold>CLOSE</bold></red>", List.of(), "close"));
        player.openInventory(inventory);
    }

    public int giveHeads(Player player, int amount) {
        int remaining = Math.max(1, Math.min(256, amount));
        int given = 0;
        while (remaining > 0) {
            int stackSize = Math.min(64, remaining);
            ItemStack stack = createGoblinHead(0);
            stack.setAmount(stackSize);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            int overflowed = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
            overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            given += stackSize - overflowed;
            remaining -= stackSize;
        }
        return given;
    }

    public double miningLuck(Player player) {
        return scaledMiningLuck(turnedIn(player), activeGoblinCount());
    }

    public boolean hasCompletedActiveHunt(Player player) {
        return player != null && completedActiveHunt(findings(player), activeIds);
    }

    public boolean isEligibleOreBreak(Block block, Player player) {
        if (block == null || player == null) return false;
        NaturalOreBreak tracked = naturalOreBreaks.get(BlockKey.of(block));
        return tracked != null && tracked.playerId.equals(player.getUniqueId()) && tracked.expiresAt > System.currentTimeMillis();
    }

    static double scaledMiningLuck(int turnedIn, int total) {
        if (total <= 0 || turnedIn <= 0) return 0.0D;
        return Math.min(MAX_MINING_LUCK, MAX_MINING_LUCK * Math.min(turnedIn, total) / total);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGoblinPlace(BlockPlaceEvent event) {
        if (!isGoblinHead(event.getItemInHand())) return;
        Block block = event.getBlockPlaced();
        if (!isHeadBlock(block.getType())) return;
        int requested = headId(event.getItemInHand());
        int id = requested > 0 && !activeIds.contains(requested) ? requested : nextId++;
        nextId = Math.max(nextId, id + 1);
        BlockKey key = BlockKey.of(block);
        placements.put(key, id);
        activeIds.add(id);
        saveNow();
        event.getPlayer().sendMessage(MessageUtil.success("Goblin <white>#" + id + "</white> placed. Active goblins: <white>" + placements.size() + "</white>."));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOrePlace(BlockPlaceEvent event) {
        if (!ORES.contains(event.getBlockPlaced().getType())) return;
        BlockKey key = BlockKey.of(event.getBlockPlaced());
        boolean changed = placedOres.add(key);
        if (isNaturalSilkOre(event.getItemInHand())) changed |= fortuneEligiblePlacedOres.add(key);
        else changed |= fortuneEligiblePlacedOres.remove(key);
        if (changed) requestSave();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGoblinBreak(BlockBreakEvent event) {
        BlockKey key = BlockKey.of(event.getBlock());
        Integer id = placements.get(key);
        if (id == null) return;
        if (!event.getPlayer().hasPermission("smpcore.goblins.admin")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.warn("This hidden goblin is part of the hunt."));
            return;
        }
        placements.remove(key);
        activeIds.remove(id);
        event.setDropItems(false);
        event.setExpToDrop(0);
        ItemStack head = createGoblinHead(id);
        Map<Integer, ItemStack> overflow = event.getPlayer().getInventory().addItem(head);
        overflow.values().forEach(item -> event.getPlayer().getWorld().dropItemNaturally(event.getPlayer().getLocation(), item));
        saveNow();
        event.getPlayer().sendMessage(MessageUtil.info("Goblin <white>#" + id + "</white> packed for moving. Active goblins: <white>" + placements.size() + "</white>."));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onGoblinFind(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick() || event.getClickedBlock() == null) return;
        Integer id = placements.get(BlockKey.of(event.getClickedBlock()));
        if (id == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        BitSet found = findings(player);
        if (found.get(id)) {
            player.sendActionBar(MM.deserialize("<gray>You already found Goblin #" + id + ".</gray>"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, 0.8f);
            return;
        }
        found.set(id);
        saveFindings(player, found);
        if (plugin.getEssenceManager() != null) plugin.getEssenceManager().credit(player, ESSENCE_PER_GOBLIN, "goblin_collectible");
        player.showTitle(net.kyori.adventure.title.Title.title(
            MM.deserialize("<green><bold>GOBLIN FOUND!</bold></green>"),
            MM.deserialize("<light_purple>+5 Essence</light_purple> <dark_gray>·</dark_gray> <white>" + found.cardinality() + " discoveries</white>"),
            net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(250), java.time.Duration.ofMillis(1800), java.time.Duration.ofMillis(400))
        ));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.75f, 1.35f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, event.getClickedBlock().getLocation().add(0.5, 0.7, 0.5), 18, 0.35, 0.35, 0.35, 0.02);
        if (plugin.getStoryService() != null) {
            plugin.getStoryService().onQuestStage(player, "goblin", "discovery", found.cardinality());
        }
        if (completedActiveHunt(found, activeIds)) {
            player.sendMessage(MessageUtil.success("You found every active goblin. <white>Goblin Slayer</white> now grants <red>+2% player and mob damage</red>."));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.65f, 1.35f);
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0.0, 1.0, 0.0), 28, 0.45, 0.55, 0.45, 0.04);
            if (plugin.getStoryService() != null) {
                plugin.getStoryService().onQuestStage(player, "goblin", "all_found", found.cardinality());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCompletedHuntDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity) || event.getDamage() <= 0.0D) return;
        Player attacker = event.getDamageSource().getCausingEntity() instanceof Player player
            ? player
            : event.getDamager() instanceof Player player ? player : null;
        if (attacker == null || attacker.equals(event.getEntity()) || !completedActiveHunt(findings(attacker), activeIds)) return;
        event.setDamage(event.getDamage() * (1.0D + HUNT_DAMAGE_BONUS));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOreBreak(BlockBreakEvent event) {
        if (!ORES.contains(event.getBlock().getType())) return;
        BlockKey key = BlockKey.of(event.getBlock());
        if (placedOres.remove(key)) {
            boolean eligible = fortuneEligiblePlacedOres.remove(key);
            requestSave();
            if (eligible) naturalOreBreaks.put(key, new NaturalOreBreak(event.getPlayer().getUniqueId(), System.currentTimeMillis() + 5_000L));
            return;
        }
        if (plugin.getEssenceManager() != null && plugin.getEssenceManager().isRecentlyPlayerPlaced(event.getBlock())) return;
        naturalOreBreaks.put(key, new NaturalOreBreak(event.getPlayer().getUniqueId(), System.currentTimeMillis() + 5_000L));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOreDrops(BlockDropItemEvent event) {
        NaturalOreBreak tracked = naturalOreBreaks.remove(BlockKey.of(event.getBlockState().getLocation()));
        if (tracked == null || !tracked.playerId.equals(event.getPlayer().getUniqueId())) return;
        markNaturalSilkOreDrops(event);
        double luck = miningLuck(event.getPlayer());
        boolean luckProc = luck > 0.0D && ThreadLocalRandom.current().nextDouble() < luck;
        MinerManager.OreBonusResult minerBonus = plugin.getMinerManager() == null
            ? new MinerManager.OreBonusResult(0, false, false)
            : plugin.getMinerManager().rollOreBonuses(event.getPlayer());
        int bonusCopies = (luckProc ? 1 : 0) + minerBonus.bonusCopies();
        if (bonusCopies <= 0) return;
        int rewarded = 0;
        List<Item> originalItems = new ArrayList<>(event.getItems());
        for (Item item : originalItems) {
            ItemStack stack = item.getItemStack();
            if (!RAW_ORE_DROPS.contains(stack.getType())) continue;
            int original = stack.getAmount();
            int remaining = original * (1 + bonusCopies);
            stack.setAmount(Math.min(stack.getMaxStackSize(), remaining));
            item.setItemStack(stack);
            remaining -= stack.getAmount();
            while (remaining > 0) {
                ItemStack overflow = stack.clone();
                overflow.setAmount(Math.min(overflow.getMaxStackSize(), remaining));
                Item extra = event.getBlock().getWorld().dropItem(event.getBlock().getLocation().add(0.5, 0.5, 0.5), overflow);
                extra.setPickupDelay(0);
                event.getItems().add(extra);
                remaining -= overflow.getAmount();
            }
            rewarded += original * bonusCopies;
        }
        if (rewarded > 0) {
            List<String> procs = new ArrayList<>();
            if (luckProc) procs.add("Mining Luck");
            if (minerBonus.petProc()) procs.add("Miner Familiar");
            if (minerBonus.feverProc()) procs.add("Mining Fever");
            event.getPlayer().sendActionBar(MM.deserialize("<gold>" + String.join(" + ", procs) + "!</gold> <yellow>+" + rewarded + " ore</yellow>"));
            event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45f, 1.7f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof GoblinMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.playerId.equals(player.getUniqueId())) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        String action = action(event.getCurrentItem());
        if (action == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (action.equals("close")) player.closeInventory();
            else if (action.equals("turn_in")) turnIn(player);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof GoblinMenuHolder) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> placements.containsKey(BlockKey.of(block)))) {
            event.setCancelled(true);
            return;
        }
        movePlacedOres(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> placements.containsKey(BlockKey.of(block)))) {
            event.setCancelled(true);
            return;
        }
        movePlacedOres(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> placements.containsKey(BlockKey.of(block)));
        Set<BlockKey> exploded = event.blockList().stream().map(BlockKey::of).collect(java.util.stream.Collectors.toSet());
        boolean changed = placedOres.removeIf(exploded::contains);
        changed |= fortuneEligiblePlacedOres.removeIf(exploded::contains);
        if (changed) requestSave();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> placements.containsKey(BlockKey.of(block)));
        Set<BlockKey> exploded = event.blockList().stream().map(BlockKey::of).collect(java.util.stream.Collectors.toSet());
        boolean changed = placedOres.removeIf(exploded::contains);
        changed |= fortuneEligiblePlacedOres.removeIf(exploded::contains);
        if (changed) requestSave();
    }

    private void turnIn(Player player) {
        BitSet found = findings(player);
        int turnedIn = turnedIn(player);
        if (activeGoblinCount() <= 0 || found.cardinality() - turnedIn < FINDINGS_PER_TURN_IN) {
            player.sendMessage(MessageUtil.warn("You need five unspent findings."));
            openMenu(player);
            return;
        }
        player.getPersistentDataContainer().set(turnedInKey, PersistentDataType.INTEGER, turnedIn + FINDINGS_PER_TURN_IN);
        player.sendMessage(MessageUtil.success("Grikk records five findings. Mining Luck is now <white>" + percent(miningLuck(player)) + "</white>."));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
        openMenu(player);
    }

    private ItemStack createGoblinHead(int id) {
        ItemStack item = headDatabaseItem();
        if (item == null || item.getType() != Material.PLAYER_HEAD) item = new ItemStack(Material.PLAYER_HEAD);
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<green><bold>Hidden Goblin</bold></green>"));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<gray>Place this around the map for players to find.</gray>"));
        lore.add(MM.deserialize("<gray>Each player may discover it once for <light_purple>5 Essence</light_purple>.</gray>"));
        lore.add(MM.deserialize(id > 0 ? "<dark_gray>Collectible #" + id + " · HeadDB 89260</dark_gray>" : "<dark_gray>A unique ID is assigned when placed.</dark_gray>"));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(headMarkerKey, PersistentDataType.BYTE, (byte) 1);
        if (id > 0) meta.getPersistentDataContainer().set(headIdKey, PersistentDataType.INTEGER, id);
        item.setItemMeta(meta);
        BedrockSkullManager bedrock = plugin.getBedrockSkullManager();
        if (bedrock != null) bedrock.registerItemForBedrock(item);
        return item;
    }

    private ItemStack headDatabaseItem() {
        org.bukkit.plugin.Plugin hdb = Bukkit.getPluginManager().getPlugin("HeadDatabase");
        if (hdb == null || !hdb.isEnabled()) return null;
        try {
            Class<?> apiClass = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI", true, hdb.getClass().getClassLoader());
            Object api = apiClass.getConstructor().newInstance();
            Method method = apiClass.getMethod("getItemHead", String.class);
            return (ItemStack) method.invoke(api, HEAD_DATABASE_ID);
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().warning("Could not load HeadDatabase head 89260: " + ex.getMessage());
            return null;
        }
    }

    private ItemStack goblinMenuHead() {
        ItemStack head = headDatabaseItem();
        if (head == null || head.getType() != Material.PLAYER_HEAD) return new ItemStack(Material.PLAYER_HEAD);
        head.setAmount(1);
        return head;
    }

    private boolean isGoblinHead(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(headMarkerKey, PersistentDataType.BYTE);
    }

    private void markNaturalSilkOreDrops(BlockDropItemEvent event) {
        for (Item dropped : event.getItems()) {
            ItemStack stack = dropped.getItemStack();
            if (stack == null || !ORES.contains(stack.getType())) continue;
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) continue;
            meta.getPersistentDataContainer().set(naturalSilkOreKey, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
            dropped.setItemStack(stack);
        }
    }

    private boolean isNaturalSilkOre(ItemStack item) {
        if (item == null || !ORES.contains(item.getType()) || !item.hasItemMeta()) return false;
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(naturalSilkOreKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private int headId(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null) return 0;
        return meta.getPersistentDataContainer().getOrDefault(headIdKey, PersistentDataType.INTEGER, 0);
    }

    private boolean isHeadBlock(Material material) {
        return material == Material.PLAYER_HEAD || material == Material.PLAYER_WALL_HEAD;
    }

    private BitSet findings(Player player) {
        byte[] bytes = player.getPersistentDataContainer().get(foundKey, PersistentDataType.BYTE_ARRAY);
        return bytes == null ? new BitSet() : BitSet.valueOf(bytes);
    }

    private void saveFindings(Player player, BitSet findings) {
        player.getPersistentDataContainer().set(foundKey, PersistentDataType.BYTE_ARRAY, findings.toByteArray());
    }

    private int turnedIn(Player player) {
        return Math.max(0, player.getPersistentDataContainer().getOrDefault(turnedInKey, PersistentDataType.INTEGER, 0));
    }

    private int activeFoundCount(BitSet found) {
        int count = 0;
        for (int id : activeIds) if (found.get(id)) count++;
        return count;
    }

    static boolean completedActiveHunt(BitSet found, Set<Integer> activeIds) {
        if (found == null || activeIds == null || activeIds.isEmpty()) return false;
        for (int id : activeIds) if (!found.get(id)) return false;
        return true;
    }

    private String percent(double value) {
        return String.format(Locale.US, value * 100.0D % 1.0D == 0.0D ? "%.0f%%" : "%.1f%%", value * 100.0D);
    }

    private ItemStack item(Material material, String name, List<String> lore, String action) {
        return item(new ItemStack(material), name, lore, action);
    }

    private ItemStack item(ItemStack item, String name, List<String> lore, String action) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(MenuItemUtil.visibleMiniName(name)));
        meta.lore(MenuItemUtil.visibleMiniLore(name, lore).stream().map(MM::deserialize).toList());
        if (action != null) meta.getPersistentDataContainer().set(menuActionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private String action(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(menuActionKey, PersistentDataType.STRING);
    }

    private void cleanupOreBreaks() {
        long now = System.currentTimeMillis();
        naturalOreBreaks.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        boolean changed = false;
        for (Map.Entry<BlockKey, Integer> entry : new ArrayList<>(placements.entrySet())) {
            BlockKey key = entry.getKey();
            World world = Bukkit.getWorld(key.worldId);
            if (world == null || !world.isChunkLoaded(key.x >> 4, key.z >> 4)) continue;
            if (isHeadBlock(world.getBlockAt(key.x, key.y, key.z).getType())) continue;
            placements.remove(key);
            activeIds.remove(entry.getValue());
            changed = true;
        }
        if (changed) saveNow();
    }

    private void movePlacedOres(List<Block> blocks, org.bukkit.block.BlockFace direction) {
        Set<BlockKey> moved = new HashSet<>();
        Set<BlockKey> movedEligible = new HashSet<>();
        boolean changed = false;
        for (Block block : blocks) {
            BlockKey source = BlockKey.of(block);
            if (!placedOres.remove(source)) continue;
            BlockKey destination = BlockKey.of(block.getRelative(direction));
            moved.add(destination);
            if (fortuneEligiblePlacedOres.remove(source)) movedEligible.add(destination);
            changed = true;
        }
        if (changed) {
            placedOres.addAll(moved);
            fortuneEligiblePlacedOres.addAll(movedEligible);
            requestSave();
        }
    }

    private void load() {
        placements.clear();
        activeIds.clear();
        placedOres.clear();
        fortuneEligiblePlacedOres.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        nextId = Math.max(1, yaml.getInt("next-id", 1));
        for (String raw : yaml.getStringList("placements")) {
            String[] parts = raw.split(":");
            if (parts.length != 5) continue;
            try {
                BlockKey key = new BlockKey(UUID.fromString(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                int id = Integer.parseInt(parts[4]);
                if (id > 0 && activeIds.add(id)) {
                    placements.put(key, id);
                    nextId = Math.max(nextId, id + 1);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (String raw : yaml.getStringList("placed-ores")) {
            BlockKey key = BlockKey.parse(raw);
            if (key != null) placedOres.add(key);
        }
        for (String raw : yaml.getStringList("fortune-eligible-placed-ores")) {
            BlockKey key = BlockKey.parse(raw);
            if (key != null && placedOres.contains(key)) fortuneEligiblePlacedOres.add(key);
        }
    }

    private void requestSave() {
        if (saveTask != null || !plugin.isEnabled()) return;
        saveTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            saveTask = null;
            saveNow();
        }, 100L);
    }

    private void saveNow() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("next-id", nextId);
        yaml.set("placements", placements.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .map(entry -> entry.getKey().serialize() + ":" + entry.getValue())
            .toList());
        yaml.set("placed-ores", placedOres.stream().map(BlockKey::serialize).sorted().toList());
        yaml.set("fortune-eligible-placed-ores", fortuneEligiblePlacedOres.stream().map(BlockKey::serialize).sorted().toList());
        File temporary = new File(dataFile.getParentFile(), dataFile.getName() + ".next");
        try {
            yaml.save(temporary);
            try {
                Files.move(
                    temporary.toPath(),
                    dataFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            try {
                Files.deleteIfExists(temporary.toPath());
            } catch (IOException ignored) {
            }
            plugin.getLogger().severe("Could not save goblin hunt locations: " + ex.getMessage());
        }
    }

    private record NaturalOreBreak(UUID playerId, long expiresAt) {}

    private record BlockKey(UUID worldId, int x, int y, int z) {
        private static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        private static BlockKey of(Location location) {
            return new BlockKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        private String serialize() {
            return worldId + ":" + x + ":" + y + ":" + z;
        }

        private static BlockKey parse(String raw) {
            String[] parts = raw == null ? new String[0] : raw.split(":");
            if (parts.length != 4) return null;
            try {
                return new BlockKey(UUID.fromString(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }

    private record GoblinMenuHolder(UUID playerId) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
