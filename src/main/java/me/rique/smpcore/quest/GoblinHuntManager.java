package me.rique.smpcore.quest;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.compat.BedrockSkullManager;
import me.rique.smpcore.item.CustomEnchantListener;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.util.VisualRangeUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.block.TileState;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
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
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
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
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
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
    private static final double ADMIN_MARKER_VIEW_RANGE_BLOCKS = 256.0D;
    private static final List<BlockFace> CLAIM_FACES = List.of(
        BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    );
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
    private final Set<UUID> adminVisionEnabled = new HashSet<>();
    private final Map<UUID, Map<BlockKey, AdminMarker>> adminVisionMarkers = new HashMap<>();
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
    private BukkitTask auditTask;
    private BukkitTask saveTask;
    private BukkitTask adminVisionTask;
    private boolean auditInProgress;
    private String expectedGoblinTextureValue;

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
        auditTask = Bukkit.getScheduler().runTaskLater(plugin,
            () -> auditPlacements(Bukkit.getConsoleSender(), true), 200L);
    }

    public void shutdown() {
        if (cleanupTask != null) cleanupTask.cancel();
        if (headRegistrationTask != null) headRegistrationTask.cancel();
        if (auditTask != null) auditTask.cancel();
        if (saveTask != null) saveTask.cancel();
        if (adminVisionTask != null) adminVisionTask.cancel();
        saveTask = null;
        adminVisionTask = null;
        for (BukkitTask task : conversations.values()) task.cancel();
        conversations.clear();
        naturalOreBreaks.clear();
        for (UUID playerId : new ArrayList<>(adminVisionMarkers.keySet())) removeAdminVisionMarkers(playerId);
        adminVisionEnabled.clear();
        saveNow();
    }

    public int activeGoblinCount() {
        return placements.size();
    }

    public boolean isAdminVisionEnabled(Player player) {
        return player != null && adminVisionEnabled.contains(player.getUniqueId());
    }

    public int setAdminVision(Player player, boolean enabled) {
        if (player == null) return 0;
        UUID playerId = player.getUniqueId();
        if (!enabled) {
            adminVisionEnabled.remove(playerId);
            removeAdminVisionMarkers(playerId);
            stopAdminVisionTaskIfUnused();
            return 0;
        }
        adminVisionEnabled.add(playerId);
        ensureAdminVisionTask();
        syncAdminVision(player);
        return adminVisionMarkers.getOrDefault(playerId, Map.of()).size();
    }

    public int refreshAdminVision(Player player) {
        if (player == null) return 0;
        adminVisionEnabled.add(player.getUniqueId());
        removeAdminVisionMarkers(player.getUniqueId());
        ensureAdminVisionTask();
        syncAdminVision(player);
        return adminVisionMarkers.getOrDefault(player.getUniqueId(), Map.of()).size();
    }

    private void ensureAdminVisionTask() {
        if (adminVisionTask != null) return;
        adminVisionTask = Bukkit.getScheduler().runTaskTimer(plugin, this::syncAdminVisionViewers, 10L, 10L);
    }

    private void stopAdminVisionTaskIfUnused() {
        if (!adminVisionEnabled.isEmpty() || adminVisionTask == null) return;
        adminVisionTask.cancel();
        adminVisionTask = null;
    }

    private void syncAdminVisionViewers() {
        for (UUID playerId : new ArrayList<>(adminVisionEnabled)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline() || !player.hasPermission("smpcore.goblins.admin")) {
                adminVisionEnabled.remove(playerId);
                removeAdminVisionMarkers(playerId);
                continue;
            }
            syncAdminVision(player);
        }
        stopAdminVisionTaskIfUnused();
    }

    private void syncAdminVision(Player player) {
        UUID playerId = player.getUniqueId();
        Map<BlockKey, AdminMarker> markers = adminVisionMarkers.computeIfAbsent(playerId, ignored -> new HashMap<>());
        for (Map.Entry<BlockKey, AdminMarker> entry : new ArrayList<>(markers.entrySet())) {
            BlockKey key = entry.getKey();
            AdminMarker marker = entry.getValue();
            Integer currentId = placements.get(key);
            World world = Bukkit.getWorld(key.worldId());
            Entity entity = Bukkit.getEntity(marker.entityId());
            boolean sameWorld = world != null && world.equals(player.getWorld());
            boolean chunkLoaded = sameWorld && world.isChunkLoaded(key.x() >> 4, key.z() >> 4);
            boolean matchingHead = chunkLoaded && currentId != null
                && currentId == marker.goblinId()
                && isMatchingGoblinHead(world.getBlockAt(key.x(), key.y(), key.z()), currentId);
            if (entity instanceof TextDisplay display && display.isValid()
                && canDisplayAdminMarker(sameWorld, chunkLoaded, matchingHead)) {
                player.showEntity(plugin, display);
                continue;
            }
            removeAdminMarker(marker);
            markers.remove(key);
        }

        for (Map.Entry<BlockKey, Integer> placement : placements.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .toList()) {
            BlockKey key = placement.getKey();
            if (markers.containsKey(key)) continue;
            World world = Bukkit.getWorld(key.worldId());
            boolean sameWorld = world != null && world.equals(player.getWorld());
            boolean chunkLoaded = sameWorld && world.isChunkLoaded(key.x() >> 4, key.z() >> 4);
            boolean matchingHead = chunkLoaded
                && isMatchingGoblinHead(world.getBlockAt(key.x(), key.y(), key.z()), placement.getValue());
            if (!canDisplayAdminMarker(sameWorld, chunkLoaded, matchingHead)) continue;
            markers.put(key, spawnAdminMarker(player, world, key, placement.getValue()));
        }
    }

    private AdminMarker spawnAdminMarker(Player viewer, World world, BlockKey key, int goblinId) {
        Location location = new Location(world, key.x() + 0.5D, key.y() + 1.35D, key.z() + 0.5D);
        TextDisplay display = world.spawn(location, TextDisplay.class, entity -> {
            entity.text(MM.deserialize("<green><bold>GOBLIN #" + goblinId + "</bold></green>"));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setViewRange(VisualRangeUtil.blocksToDisplayViewRange(ADMIN_MARKER_VIEW_RANGE_BLOCKS));
            entity.setLineWidth(160);
            entity.setShadowed(true);
            entity.setSeeThrough(true);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
            entity.setTextOpacity((byte) 0xFF);
            entity.setGlowColorOverride(Color.LIME);
            entity.setGlowing(true);
            entity.setGravity(false);
            entity.setSilent(true);
            entity.setInvulnerable(true);
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);
        });
        viewer.showEntity(plugin, display);
        return new AdminMarker(goblinId, display.getUniqueId());
    }

    private void removeAdminVisionMarkers(UUID playerId) {
        Map<BlockKey, AdminMarker> markers = adminVisionMarkers.remove(playerId);
        if (markers == null) return;
        for (AdminMarker marker : markers.values()) removeAdminMarker(marker);
    }

    private static void removeAdminMarker(AdminMarker marker) {
        if (marker == null) return;
        Entity entity = Bukkit.getEntity(marker.entityId());
        if (entity != null) entity.remove();
    }

    static boolean canDisplayAdminMarker(boolean sameWorld, boolean chunkLoaded, boolean matchingHead) {
        return sameWorld && chunkLoaded && matchingHead;
    }

    public boolean auditPlacements(CommandSender sender, boolean pruneInvalid) {
        if (auditInProgress) {
            if (sender != null) sender.sendMessage(MessageUtil.warn("A goblin audit is already running."));
            return false;
        }
        auditInProgress = true;
        Deque<PlacementSnapshot> pending = new ArrayDeque<>(placements.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .map(entry -> new PlacementSnapshot(entry.getKey(), entry.getValue()))
            .toList());
        AuditProgress progress = new AuditProgress(pending.size(), pruneInvalid);
        if (sender != null) {
            sender.sendMessage(MessageUtil.info("Checking <white>" + pending.size() + "</white> hidden goblins against the live world..."));
        }
        auditNext(sender, pending, progress);
        return true;
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
        int foundActive = activeMapProgress(foundTotal, total);
        int turnedIn = turnedIn(player);
        int available = Math.max(0, foundTotal - turnedIn);
        double luck = miningLuck(player);
        boolean huntComplete = completedActiveHunt(found, activeIds);
        Inventory inventory = Bukkit.createInventory(new GoblinMenuHolder(player.getUniqueId()), 27,
            BedrockCompat.menuTitle(player, MM.deserialize("<dark_green><bold>Goblin Hunt</bold></dark_green>"), "Goblin Hunt"));
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, List.of(), null);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        inventory.setItem(10, item(goblinMenuHead(), "<green><bold>GOBLINS FOUND</bold></green>", List.of(
            "<gray>Map progress: <white>" + foundActive + "/" + total + "</white></gray>",
            "<gray>Lifetime findings: <white>" + foundTotal + "</white></gray>",
            "<gray>Each new goblin gives <light_purple>5 Essence</light_purple>.</gray>",
            "<dark_gray>Map changes never remove earned progress.</dark_gray>"
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
        if (block.getState(false) instanceof TileState tileState) {
            tileState.getPersistentDataContainer().set(headMarkerKey, PersistentDataType.BYTE, (byte) 1);
            tileState.getPersistentDataContainer().set(headIdKey, PersistentDataType.INTEGER, id);
            tileState.update(true, false);
        }
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
        if (!isMatchingGoblinHead(event.getClickedBlock(), id)) {
            placements.remove(BlockKey.of(event.getClickedBlock()), id);
            activeIds.remove(id);
            saveNow();
            event.getPlayer().sendActionBar(MM.deserialize("<gray>That goblin is no longer active.</gray>"));
            return;
        }
        Player player = event.getPlayer();
        BitSet found = findings(player);
        if (found.get(id)) {
            player.sendActionBar(MM.deserialize("<gray>You already found Goblin #" + id + ".</gray>"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, 0.8f);
            return;
        }
        boolean alreadyCompletedMap = completedActiveHunt(found, activeIds);
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
        if (!alreadyCompletedMap && completedActiveHunt(found, activeIds)) {
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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOreDrops(BlockDropItemEvent event) {
        NaturalOreBreak tracked = naturalOreBreaks.get(BlockKey.of(event.getBlockState().getLocation()));
        if (tracked == null || !tracked.playerId.equals(event.getPlayer().getUniqueId())) return;
        markNaturalSilkOreDrops(event);
        double luck = miningLuck(event.getPlayer());
        boolean luckProc = luck > 0.0D && ThreadLocalRandom.current().nextDouble() < luck;
        MinerManager.OreBonusResult minerBonus = plugin.getMinerManager() == null
            ? new MinerManager.OreBonusResult(0, false, false)
            : plugin.getMinerManager().rollOreBonuses(event.getPlayer());
        int bonusCopies = (luckProc ? 1 : 0) + minerBonus.bonusCopies();
        if (bonusCopies <= 0) return;
        List<ItemStack> originalDrops = event.getItems().stream().map(Item::getItemStack).toList();
        List<ItemStack> bonusDrops = createAdditiveOreBonusStacks(originalDrops, bonusCopies);
        int rewarded = bonusDrops.stream().mapToInt(ItemStack::getAmount).sum();
        if (rewarded > 0) {
            Player player = event.getPlayer();
            ItemStack tool = player.getInventory().getItemInMainHand();
            CustomEnchantListener enchants = plugin.getCustomEnchantListener();
            if (enchants != null && enchants.hasSmeltingTouchEnchant(tool)) {
                bonusDrops = bonusDrops.stream().flatMap(stack -> enchants.smeltMiningDrops(stack).stream()).toList();
            }
            Location dropLocation = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
            if (enchants != null && enchants.hasTelekinesisEnchant(tool)) {
                enchants.deliverTelekinesisDrops(player, bonusDrops, dropLocation);
            } else {
                for (ItemStack bonusDrop : bonusDrops) {
                    Item dropped = event.getBlock().getWorld().dropItemNaturally(dropLocation, bonusDrop);
                    dropped.setPickupDelay(0);
                }
            }
            List<String> procs = new ArrayList<>();
            if (luckProc) procs.add("Mining Luck");
            if (minerBonus.petProc()) procs.add("Miner Familiar");
            if (minerBonus.feverProc()) procs.add("Mining Fever");
            player.sendActionBar(MM.deserialize("<gold>" + String.join(" + ", procs) + "!</gold> <yellow>+" + rewarded + " ore</yellow>"));
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45f, 1.7f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOreDropsComplete(BlockDropItemEvent event) {
        BlockKey key = BlockKey.of(event.getBlockState().getLocation());
        NaturalOreBreak tracked = naturalOreBreaks.get(key);
        if (tracked != null && tracked.playerId.equals(event.getPlayer().getUniqueId())) {
            naturalOreBreaks.remove(key, tracked);
        }
    }

    static List<ItemStack> createAdditiveOreBonusStacks(List<ItemStack> drops, int bonusCopies) {
        if (drops == null || drops.isEmpty() || bonusCopies <= 0) return List.of();
        List<ItemStack> bonuses = new ArrayList<>();
        for (ItemStack source : drops) {
            if (source == null || source.getAmount() <= 0 || !RAW_ORE_DROPS.contains(source.getType())) continue;
            int maxStack = Math.max(1, source.getMaxStackSize());
            for (int amount : splitBonusAmounts(source.getAmount(), bonusCopies, maxStack)) {
                ItemStack split = source.clone();
                split.setAmount(amount);
                bonuses.add(split);
            }
        }
        return bonuses;
    }

    static List<Integer> splitBonusAmounts(int sourceAmount, int bonusCopies, int maxStackSize) {
        if (sourceAmount <= 0 || bonusCopies <= 0 || maxStackSize <= 0) return List.of();
        List<Integer> amounts = new ArrayList<>();
        long remaining = (long) sourceAmount * bonusCopies;
        while (remaining > 0L) {
            int amount = (int) Math.min(remaining, maxStackSize);
            amounts.add(amount);
            remaining -= amount;
        }
        return amounts;
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

    @EventHandler
    public void onAdminVisionQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (!adminVisionEnabled.remove(playerId)) return;
        removeAdminVisionMarkers(playerId);
        stopAdminVisionTaskIfUnused();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdminVisionWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!adminVisionEnabled.contains(player.getUniqueId())) return;
        removeAdminVisionMarkers(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && adminVisionEnabled.contains(player.getUniqueId())) syncAdminVision(player);
        });
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
        meta.lore(CustomLoreUtil.wrapLoreLines(lore));
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

    static int activeMapProgress(int lifetimeFindings, int activeCount) {
        return Math.min(Math.max(0, lifetimeFindings), Math.max(0, activeCount));
    }

    static boolean completedActiveHunt(BitSet found, Set<Integer> activeIds) {
        if (found == null || activeIds == null || activeIds.isEmpty()) return false;
        return activeMapProgress(found.cardinality(), activeIds.size()) == activeIds.size();
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
            if (isMatchingGoblinHead(world.getBlockAt(key.x, key.y, key.z), entry.getValue())) continue;
            placements.remove(key);
            activeIds.remove(entry.getValue());
            changed = true;
        }
        if (changed) saveNow();
    }

    private void auditNext(CommandSender sender, Deque<PlacementSnapshot> pending, AuditProgress progress) {
        PlacementSnapshot placement = pending.pollFirst();
        if (placement == null) {
            finishAudit(sender, progress);
            return;
        }
        BlockKey key = placement.key();
        World world = Bukkit.getWorld(key.worldId());
        if (world == null) {
            progress.unavailable++;
            progress.remember("#" + placement.id() + " (world unavailable)");
            auditNext(sender, pending, progress);
            return;
        }
        world.getChunkAtAsync(key.x() >> 4, key.z() >> 4, false).whenComplete((chunk, error) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null || chunk == null) {
                    progress.unavailable++;
                    progress.remember("#" + placement.id() + " at " + coordinates(key) + " (chunk unavailable)");
                    auditNext(sender, pending, progress);
                    return;
                }
                Integer currentId = placements.get(key);
                if (!Integer.valueOf(placement.id()).equals(currentId)) {
                    progress.changedDuringAudit++;
                    auditNext(sender, pending, progress);
                    return;
                }
                Block block = world.getBlockAt(key.x(), key.y(), key.z());
                if (!isMatchingGoblinHead(block, placement.id())) {
                    progress.missing++;
                    progress.remember("#" + placement.id() + " at " + coordinates(key) + " was missing or replaced");
                    if (progress.pruneInvalid && removeAuditedPlacement(key, placement.id(), null)) progress.removed++;
                } else if (!hasClaimableFace(block)) {
                    progress.blocked++;
                    progress.remember("#" + placement.id() + " at " + coordinates(key) + " was fully enclosed");
                    if (progress.pruneInvalid && removeAuditedPlacement(key, placement.id(), block)) progress.removed++;
                } else {
                    progress.claimable++;
                }
                auditNext(sender, pending, progress);
            })
        );
    }

    private boolean removeAuditedPlacement(BlockKey key, int id, Block block) {
        if (!placements.remove(key, id)) return false;
        activeIds.remove(id);
        if (block != null && isHeadBlock(block.getType())) block.setType(Material.AIR, false);
        return true;
    }

    private void finishAudit(CommandSender sender, AuditProgress progress) {
        auditInProgress = false;
        int removed = progress.removed;
        if (removed > 0) saveNow();
        if (sender != null) {
            for (String detail : progress.details) sender.sendMessage(MessageUtil.warn(detail));
            if (progress.omittedDetails > 0) {
                sender.sendMessage(MessageUtil.info("And <white>" + progress.omittedDetails + "</white> more issue(s)."));
            }
            String action = progress.pruneInvalid
                ? " Removed <white>" + removed + "</white>; active count is now <white>" + placements.size() + "</white>."
                : progress.missing + progress.blocked > 0 ? " Run <white>/goblins audit prune</white> to remove them." : "";
            sender.sendMessage(MessageUtil.info(
                "Goblin audit checked <white>" + progress.total + "</white>: <green>" + progress.claimable + " claimable</green>, <red>" + progress.missing
                    + " overwritten</red>, <red>" + progress.blocked + " enclosed</red>, <yellow>" + progress.unavailable
                    + " unavailable</yellow>" + (progress.changedDuringAudit > 0 ? ", <yellow>" + progress.changedDuringAudit + " moved while scanning</yellow>" : "") + "." + action
            ));
        }
        plugin.getLogger().info("Goblin audit finished: " + progress.claimable + " claimable, " + progress.missing
            + " overwritten, " + progress.blocked + " enclosed, " + progress.unavailable + " unavailable, " + removed + " removed.");
    }

    private static boolean hasClaimableFace(Block block) {
        boolean[] passable = new boolean[CLAIM_FACES.size()];
        for (int index = 0; index < CLAIM_FACES.size(); index++) {
            passable[index] = block.getRelative(CLAIM_FACES.get(index)).isPassable();
        }
        return hasClaimableFace(passable);
    }

    static boolean hasClaimableFace(boolean... adjacentPassable) {
        if (adjacentPassable == null) return false;
        for (boolean passable : adjacentPassable) if (passable) return true;
        return false;
    }

    private boolean isMatchingGoblinHead(Block block, int id) {
        if (block == null || !isHeadBlock(block.getType())) return false;
        if (block.getState(false) instanceof TileState tileState) {
            PersistentDataContainer data = tileState.getPersistentDataContainer();
            if (data.has(headMarkerKey, PersistentDataType.BYTE)) {
                return data.getOrDefault(headIdKey, PersistentDataType.INTEGER, -1) == id;
            }
        }
        String expectedTexture = expectedGoblinTextureValue();
        if (expectedTexture == null) return true;
        if (!(block.getState(false) instanceof Skull skull)) return false;
        return sameTexture(expectedTexture, textureValue(skull.getProfile().properties()));
    }

    private String expectedGoblinTextureValue() {
        if (expectedGoblinTextureValue != null) return expectedGoblinTextureValue;
        ItemStack head = headDatabaseItem();
        ItemMeta meta = head == null ? null : head.getItemMeta();
        if (!(meta instanceof SkullMeta skullMeta)) return null;
        expectedGoblinTextureValue = textureValue(skullMeta.getPlayerProfile());
        return expectedGoblinTextureValue;
    }

    private static String textureValue(PlayerProfile profile) {
        return profile == null ? null : textureValue(profile.getProperties());
    }

    private static String textureValue(java.util.Collection<ProfileProperty> properties) {
        if (properties == null) return null;
        return properties.stream()
            .filter(property -> "textures".equalsIgnoreCase(property.getName()))
            .map(ProfileProperty::getValue)
            .findFirst()
            .orElse(null);
    }

    static boolean sameTexture(String expected, String actual) {
        return expected != null && actual != null && expected.equalsIgnoreCase(actual);
    }

    private static String coordinates(BlockKey key) {
        return key.x() + ", " + key.y() + ", " + key.z();
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

    private record PlacementSnapshot(BlockKey key, int id) {}

    private record AdminMarker(int goblinId, UUID entityId) {}

    private static final class AuditProgress {
        private static final int MAX_DETAILS = 12;
        private final int total;
        private final boolean pruneInvalid;
        private final List<String> details = new ArrayList<>();
        private int claimable;
        private int missing;
        private int blocked;
        private int unavailable;
        private int changedDuringAudit;
        private int removed;
        private int omittedDetails;

        private AuditProgress(int total, boolean pruneInvalid) {
            this.total = total;
            this.pruneInvalid = pruneInvalid;
        }

        private void remember(String detail) {
            if (details.size() < MAX_DETAILS) details.add(detail);
            else omittedDetails++;
        }
    }

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
