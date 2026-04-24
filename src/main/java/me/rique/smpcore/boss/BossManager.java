package me.rique.smpcore.boss;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry and control surface for custom bosses.
 * Future bosses only need to be added to BossType to appear in the GUI and commands.
 */
public final class BossManager implements Listener {

    public static final String DOMINION_CORE_ITEM_ID = "dominion_core";
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Component MENU_TITLE = MM.deserialize("<gradient:#ff5e5b:#ff914d><bold>Boss Control</bold></gradient>");
    private static final int[] BOSS_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };
    private static final String SCOREBOARD_TAG = "smpcore_custom_boss";
    private static final long ORPHAN_MINION_CLEANUP_INTERVAL_MS = 30_000L;

    private final SMPCore plugin;
    private final NamespacedKey keyBossId;
    private final NamespacedKey keyBossInstanceId;
    private final NamespacedKey keyBossMarker;
    private final NamespacedKey keyBossPhase;
    private final NamespacedKey keyBossPrimaryCooldown;
    private final NamespacedKey keyBossSecondaryCooldown;
    private final NamespacedKey keyBossMinionMarker;
    private final NamespacedKey keyBossMinionOwner;
    private final NamespacedKey keyDominionCoreItem;

    private final Map<UUID, BossRecord> trackedBosses = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> trackedByBossId = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> holograms = new ConcurrentHashMap<>();
    private BukkitTask heartbeatTask;
    private long nextOrphanMinionCleanupAt;

    public BossManager(SMPCore plugin) {
        this.plugin = plugin;
        this.keyBossId = new NamespacedKey(plugin, "boss_id");
        this.keyBossInstanceId = new NamespacedKey(plugin, "boss_instance_id");
        this.keyBossMarker = new NamespacedKey(plugin, "boss_marker");
        this.keyBossPhase = new NamespacedKey(plugin, "boss_phase");
        this.keyBossPrimaryCooldown = new NamespacedKey(plugin, "boss_primary_cd");
        this.keyBossSecondaryCooldown = new NamespacedKey(plugin, "boss_secondary_cd");
        this.keyBossMinionMarker = new NamespacedKey(plugin, "boss_minion_marker");
        this.keyBossMinionOwner = new NamespacedKey(plugin, "boss_minion_owner");
        this.keyDominionCoreItem = new NamespacedKey(plugin, DOMINION_CORE_ITEM_ID);
    }

    public void start() {
        try {
            List<BossRecord> loaded = plugin.getDatabase().loadAllBosses().join();
            trackedBosses.clear();
            trackedByBossId.clear();
            for (BossRecord record : loaded) {
                trackRecord(record);
            }
        } catch (Exception ex) {
            plugin.getLogger().severe("Failed to load custom boss records: " + ex.getMessage());
        }

        reconcileLoadedBosses();
        startHeartbeat();
    }

    public void shutdown() {
        stopHeartbeat();
        destroyAllBossVisuals();
        trackedBosses.clear();
        trackedByBossId.clear();
    }

    public List<String> bossIds() {
        List<String> ids = new ArrayList<>();
        for (BossType type : BossType.values()) {
            ids.add(type.id());
        }
        return ids;
    }

    public String normalizeBossId(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.isBlank()) {
            return null;
        }
        BossType type = BossType.fromInput(normalized);
        return type == null ? normalized : type.id();
    }

    public String displayNameForBoss(String input) {
        String normalized = normalizeBossId(input);
        BossType type = BossType.fromId(normalized);
        if (type != null) {
            return type.plainDisplayName();
        }
        return prettyBossName(normalized);
    }

    public Set<String> bossCommandOptions() {
        LinkedHashSet<String> options = new LinkedHashSet<>();
        for (BossType type : BossType.values()) {
            options.add(type.commandToken());
            options.add(type.id());
        }
        return options;
    }

    public ItemStack createDominionCoreItem() {
        ItemStack item = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(MM.deserialize("<gradient:#ef4444:#f97316><bold>Dominion Core</bold></gradient>"));
        meta.lore(List.of(
            MM.deserialize("<gray>A pulsing shard torn from a fallen dominion warden.</gray>"),
            MM.deserialize("<gray>Use it in an <white>Anvil</white> with <white>Crimson Dominion</white></gray>"),
            MM.deserialize("<gray>to fully restore the blade's durability.</gray>")
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(keyDominionCoreItem, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isDominionCore(ItemStack item) {
        if (item == null || item.getType() != Material.ECHO_SHARD) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte tagged = meta.getPersistentDataContainer().get(keyDominionCoreItem, PersistentDataType.BYTE);
        return tagged != null && tagged == (byte) 1;
    }

    public void openBossMenu(Player player) {
        reconcileLoadedBosses();

        Inventory inventory = Bukkit.createInventory(
            new BossMenuHolder(),
            54,
            BedrockCompat.menuTitle(player, MENU_TITLE, "Boss Control")
        );
        ItemStack filler = menuItem(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        inventory.setItem(4, createOverviewItem());
        inventory.setItem(49, createClearAllItem());
        inventory.setItem(53, createRefreshItem());

        BossType[] types = BossType.values();
        if (types.length == 0) {
            inventory.setItem(22, menuItem(
                Material.PAPER,
                "<yellow><bold>No Bosses Registered</bold></yellow>",
                List.of(
                    "<gray>The boss framework is live.</gray>",
                    "<gray>Add future boss entries in the boss registry,</gray>",
                    "<gray>and they will appear here automatically.</gray>"
                )
            ));
        } else {
            for (int i = 0; i < types.length && i < BOSS_SLOTS.length; i++) {
                inventory.setItem(BOSS_SLOTS[i], createBossEntryItem(types[i]));
            }
        }
        player.openInventory(inventory);
    }

    public BossActionResult spawnBoss(Player player, String requestedBossId) {
        BossType type = BossType.fromId(normalizeBossId(requestedBossId));
        if (type == null) {
            return new BossActionResult(false, "Unknown boss.");
        }
        Location spawnLocation = findBossSpawnLocation(player, type);
        if (spawnLocation == null) {
            return new BossActionResult(false, "No safe spot was found nearby to spawn " + type.plainDisplayName() + ".");
        }
        return spawnBoss(type, spawnLocation);
    }

    public BossActionResult spawnBoss(BossType type, Location location) {
        if (type == null) {
            return new BossActionResult(false, "Unknown boss.");
        }
        if (BossType.values().length == 0) {
            return new BossActionResult(false, "No custom bosses are registered yet.");
        }
        World world = location == null ? null : location.getWorld();
        if (world == null) {
            return new BossActionResult(false, "Spawn location is invalid.");
        }

        LivingEntity spawned;
        try {
            Entity entity = world.spawnEntity(location, type.entityType());
            if (!(entity instanceof LivingEntity living)) {
                entity.remove();
                return new BossActionResult(false, "That boss type does not spawn as a living entity.");
            }
            spawned = living;
        } catch (Exception ex) {
            return new BossActionResult(false, "Failed to spawn " + type.plainDisplayName() + ".");
        }

        applyBossState(spawned, type);

        BossRecord record = new BossRecord(
            spawned.getUniqueId(),
            type.id(),
            world.getName(),
            spawned.getLocation().getX(),
            spawned.getLocation().getY(),
            spawned.getLocation().getZ(),
            spawned.getChunk().getX(),
            spawned.getChunk().getZ(),
            System.currentTimeMillis()
        );
        trackRecord(record);
        plugin.getDatabase().saveBossRecord(record);

        return new BossActionResult(
            true,
            "Spawned <white>" + type.plainDisplayName() + "</white> at <white>"
                + spawned.getLocation().getBlockX() + ", "
                + spawned.getLocation().getBlockY() + ", "
                + spawned.getLocation().getBlockZ() + "</white>."
        );
    }

    public BossActionResult despawnBoss(String requestedBossId) {
        String normalized = normalizeBossId(requestedBossId);
        if (normalized == null || normalized.isBlank()) {
            return new BossActionResult(false, "Unknown boss.");
        }
        int removed = despawnBossRecords(record -> record.bossId().equalsIgnoreCase(normalized));
        if (removed <= 0) {
            return new BossActionResult(false, "No active <white>" + displayNameForBoss(normalized) + "</white> bosses were found.");
        }
        return new BossActionResult(true, "Removed <white>" + removed + "</white> active <white>" + displayNameForBoss(normalized) + "</white> boss" + (removed == 1 ? "" : "es") + ".");
    }

    public BossActionResult despawnAllBosses() {
        int removed = despawnBossRecords(record -> true);
        if (removed <= 0) {
            return new BossActionResult(true, "There were no active custom bosses to remove.");
        }
        return new BossActionResult(true, "Removed <white>" + removed + "</white> active custom boss" + (removed == 1 ? "" : "es") + ".");
    }

    public List<String> statusLines() {
        reconcileLoadedBosses();
        List<String> lines = new ArrayList<>();
        lines.add("<gold><bold>Boss Status</bold></gold>");
        lines.add("<gray>Total tracked bosses:</gray> <white>" + trackedBosses.size() + "</white>");

        BossType[] types = BossType.values();
        if (types.length == 0) {
            lines.add("<gray>No custom bosses are registered yet.</gray>");
            return lines;
        }

        for (BossType type : types) {
            lines.add("<gray>" + type.plainDisplayName() + ":</gray> <white>" + activeCount(type.id()) + "</white>");
        }
        return lines;
    }

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTrackedBosses, 1L, 10L);
    }

    private void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }
    }

    private void tickTrackedBosses() {
        List<BossRecord> snapshot = new ArrayList<>(trackedBosses.values());
        for (BossRecord record : snapshot) {
            World world = Bukkit.getWorld(record.world());
            if (world == null) {
                destroyBossVisuals(record.entityUuid());
                untrackRecord(record.entityUuid());
                plugin.getDatabase().deleteBossRecord(record.entityUuid());
                continue;
            }

            if (!world.isChunkLoaded(record.chunkX(), record.chunkZ())) {
                continue;
            }

            Entity entity = Bukkit.getEntity(record.entityUuid());
            if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
                destroyBossVisuals(record.entityUuid());
                untrackRecord(record.entityUuid());
                plugin.getDatabase().deleteBossRecord(record.entityUuid());
                continue;
            }

            BossType type = BossType.fromId(record.bossId());
            if (type == null) {
                destroyBossVisuals(record.entityUuid());
                living.remove();
                untrackRecord(record.entityUuid());
                plugin.getDatabase().deleteBossRecord(record.entityUuid());
                continue;
            }

            ensureBossVisuals(living, type);
            updateBossBar(living, type);
            updateBossHologram(living, type);
            tickBossBehavior(living, type);
        }

        maybeCleanupOrphanBossMinions();
    }

    private void maybeCleanupOrphanBossMinions() {
        long now = System.currentTimeMillis();
        if (now < nextOrphanMinionCleanupAt) {
            return;
        }

        nextOrphanMinionCleanupAt = now + ORPHAN_MINION_CLEANUP_INTERVAL_MS;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                cleanupOrphanBossMinions(chunk);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        BossRecord record = bossRecord(entity);
        if (record != null) {
            BossType type = BossType.fromId(record.bossId());
            if (type == BossType.VORALITH_THE_CRIMSON_WARDEN) {
                Player killer = event.getEntity().getKiller();
                ItemStack coreDrop = createDominionCoreItem();
                if (killer != null && plugin.getItemAuditManager() != null) {
                    plugin.getItemAuditManager().recordKnownAcquisition(
                        killer,
                        coreDrop,
                        "boss_drop",
                        "Dropped from Voralith the Crimson Warden."
                    );
                }
                event.getDrops().clear();
                event.getDrops().add(coreDrop);
                event.setDroppedExp(Math.max(event.getDroppedExp(), 80));
            }
            despawnBossMinions(record.entityUuid());
            destroyBossVisuals(record.entityUuid());
            untrackRecord(record.entityUuid());
            plugin.getDatabase().deleteBossRecord(record.entityUuid());
            return;
        }

        if (isBossMinion(entity)) {
            entity.getPersistentDataContainer().remove(keyBossMinionMarker);
            entity.getPersistentDataContainer().remove(keyBossMinionOwner);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossCombust(EntityCombustEvent event) {
        if (bossRecord(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof LivingEntity shooter)) {
            return;
        }
        BossRecord record = bossRecord(shooter);
        if (record == null || !(event.getProjectile() instanceof Projectile projectile)) {
            return;
        }
        BossType type = BossType.fromId(record.bossId());
        if (type == BossType.KAEL_THE_ASHEN) {
            handleKaelBowShot(shooter, projectile, bossPhase(shooter));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossDamage(EntityDamageByEntityEvent event) {
        LivingEntity bossEntity;
        boolean projectileHit = false;
        if (event.getDamager() instanceof LivingEntity attacker) {
            bossEntity = attacker;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            bossEntity = shooter;
            projectileHit = true;
        } else {
            return;
        }

        BossRecord record = bossRecord(bossEntity);
        if (record == null) {
            return;
        }
        BossType type = BossType.fromId(record.bossId());
        if (type == null || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        int phase = bossPhase(bossEntity);
        if (type == BossType.YULE_THE_MINION && !projectileHit) {
            spawnYuleAttackParticles(bossEntity, target, phase);
            if (phase >= 2) {
                applyYulePhaseTwoKnockback(bossEntity, target);
            }
            return;
        }

        if (type == BossType.KAEL_THE_ASHEN && projectileHit) {
            handleKaelProjectileHit(bossEntity, target, phase, event);
            return;
        }

        if (type == BossType.VESPER_THE_WIDOW_QUEEN && !projectileHit) {
            handleVesperMeleeHit(bossEntity, target, phase);
            return;
        }

        if (type == BossType.VORALITH_THE_CRIMSON_WARDEN && !projectileHit) {
            handleVoralithMeleeHit(bossEntity, target, phase, event);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        reconcileChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BossMenuHolder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() < 0 || event.getRawSlot() >= topSize) {
            return;
        }

        if (event.getRawSlot() == 49) {
            BossActionResult result = despawnAllBosses();
            player.sendMessage(result.success() ? MessageUtil.success(result.message()) : MessageUtil.error(result.message()));
            Bukkit.getScheduler().runTask(plugin, () -> openBossMenu(player));
            return;
        }

        if (event.getRawSlot() == 53) {
            reconcileLoadedBosses();
            Bukkit.getScheduler().runTask(plugin, () -> openBossMenu(player));
            return;
        }

        BossType type = bossTypeForSlot(event.getRawSlot());
        if (type == null) {
            return;
        }

        if (event.isRightClick()) {
            BossActionResult result = despawnBoss(type.id());
            player.sendMessage(result.success() ? MessageUtil.success(result.message()) : MessageUtil.error(result.message()));
        } else if (event.isLeftClick()) {
            BossActionResult result = spawnBoss(player, type.id());
            player.sendMessage(result.success() ? MessageUtil.success(result.message()) : MessageUtil.error(result.message()));
        }

        Bukkit.getScheduler().runTask(plugin, () -> openBossMenu(player));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossMenuDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BossMenuHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private BossType bossTypeForSlot(int rawSlot) {
        BossType[] types = BossType.values();
        for (int i = 0; i < types.length && i < BOSS_SLOTS.length; i++) {
            if (BOSS_SLOTS[i] == rawSlot) {
                return types[i];
            }
        }
        return null;
    }

    private ItemStack createOverviewItem() {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Total tracked bosses:</gray> <white>" + trackedBosses.size() + "</white>");
        lore.add("<gray>Left-click a boss to spawn it.</gray>");
        lore.add("<gray>Right-click a boss to despawn every copy of it.</gray>");
        lore.add("<gray>Future boss definitions will appear here automatically.</gray>");
        return menuItem(Material.NETHER_STAR, "<gold><bold>Boss Console</bold></gold>", lore);
    }

    private ItemStack createClearAllItem() {
        return menuItem(
            Material.BARRIER,
            "<red><bold>Delete All Bosses</bold></red>",
            List.of(
                "<gray>Removes every tracked custom boss.</gray>",
                "<gray>This also clears persisted boss records.</gray>"
            )
        );
    }

    private ItemStack createRefreshItem() {
        return menuItem(
            Material.ENDER_EYE,
            "<aqua><bold>Refresh</bold></aqua>",
            List.of(
                "<gray>Rebuild the live boss snapshot</gray>",
                "<gray>and refresh the current counts.</gray>"
            )
        );
    }

    private ItemStack createBossEntryItem(BossType type) {
        List<String> lore = new ArrayList<>(type.description());
        if (!lore.isEmpty()) {
            lore.add("<dark_gray> ");
        }
        lore.add("<gray>Active:</gray> <white>" + activeCount(type.id()) + "</white>");
        lore.add("<gray>Entity:</gray> <white>" + prettyBossName(type.entityType().name()) + "</white>");
        lore.add("<gray>Health:</gray> <white>" + trimNumber(type.maxHealth()) + "</white>");
        lore.add("<gray>Left-click:</gray> <white>Spawn at your position</white>");
        lore.add("<gray>Right-click:</gray> <white>Despawn all copies</white>");
        return menuItem(type.menuIcon(), type.displayName(), lore);
    }

    private ItemStack menuItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(MM.deserialize(name));
        if (!loreLines.isEmpty()) {
            List<Component> lore = new ArrayList<>(loreLines.size());
            for (String line : loreLines) {
                lore.add(MM.deserialize(line));
            }
            meta.lore(lore);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void applyBossState(LivingEntity entity, BossType type) {
        entity.setPersistent(true);
        entity.customName(MM.deserialize(type.displayName()));
        entity.setCustomNameVisible(false);
        entity.setGlowing(type.glowing());

        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(keyBossMarker, PersistentDataType.BYTE, (byte) 1);
        pdc.set(keyBossId, PersistentDataType.STRING, type.id());
        pdc.set(keyBossInstanceId, PersistentDataType.STRING, entity.getUniqueId().toString());
        pdc.set(keyBossPhase, PersistentDataType.INTEGER, 1);
        entity.addScoreboardTag(SCOREBOARD_TAG);
        entity.addScoreboardTag(SCOREBOARD_TAG + ":" + type.id());

        if (entity instanceof Mob mob) {
            mob.setRemoveWhenFarAway(false);
            mob.setCanPickupItems(false);
        }
        clearBossEquipment(entity);

        setAttributeBase(entity, Attribute.MAX_HEALTH, type.maxHealth());
        AttributeInstance healthAttribute = entity.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = healthAttribute == null ? type.maxHealth() : healthAttribute.getValue();
        entity.setHealth(Math.max(1.0, maxHealth));
        setAttributeBase(entity, Attribute.ATTACK_DAMAGE, type.attackDamage());
        setAttributeBase(entity, Attribute.MOVEMENT_SPEED, type.movementSpeed());
        setAttributeBase(entity, Attribute.FOLLOW_RANGE, type.followRange());
        setAttributeBase(entity, Attribute.KNOCKBACK_RESISTANCE, type.knockbackResistance());

        type.configurer().apply(this, entity);
        ensureBossVisuals(entity, type);
    }

    private void setAttributeBase(LivingEntity entity, Attribute attribute, double baseValue) {
        if (baseValue <= 0.0) {
            return;
        }
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(baseValue);
        }
    }

    private void clearBossEquipment(LivingEntity entity) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }

        equipment.setHelmet(null);
        equipment.setChestplate(null);
        equipment.setLeggings(null);
        equipment.setBoots(null);
        equipment.setItemInMainHand(null);
        equipment.setItemInOffHand(null);

        equipment.setHelmetDropChance(0.0f);
        equipment.setChestplateDropChance(0.0f);
        equipment.setLeggingsDropChance(0.0f);
        equipment.setBootsDropChance(0.0f);
        equipment.setItemInMainHandDropChance(0.0f);
        equipment.setItemInOffHandDropChance(0.0f);
    }

    private void equipBossArmor(LivingEntity entity, ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }

        equipment.setHelmet(helmet);
        equipment.setChestplate(chestplate);
        equipment.setLeggings(leggings);
        equipment.setBoots(boots);

        equipment.setHelmetDropChance(0.0f);
        equipment.setChestplateDropChance(0.0f);
        equipment.setLeggingsDropChance(0.0f);
        equipment.setBootsDropChance(0.0f);
    }

    private void equipBossHands(LivingEntity entity, ItemStack mainHand, ItemStack offHand) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }

        equipment.setItemInMainHand(mainHand);
        equipment.setItemInOffHand(offHand);
        equipment.setItemInMainHandDropChance(0.0f);
        equipment.setItemInOffHandDropChance(0.0f);
    }

    private void ensureBossVisuals(LivingEntity entity, BossType type) {
        ensureBossBar(entity, type);
        ensureBossHologram(entity, type);
    }

    private void ensureBossBar(LivingEntity entity, BossType type) {
        bossBars.computeIfAbsent(entity.getUniqueId(), uuid -> Bukkit.createBossBar("", BarColor.RED, BarStyle.SEGMENTED_10));
    }

    private void ensureBossHologram(LivingEntity entity, BossType type) {
        UUID hologramId = holograms.get(entity.getUniqueId());
        Entity existing = hologramId == null ? null : Bukkit.getEntity(hologramId);
        if (existing instanceof TextDisplay) {
            return;
        }

        TextDisplay display = entity.getWorld().spawn(entity.getLocation().clone().add(0.0, entity.getHeight() + 0.85, 0.0), TextDisplay.class, textDisplay -> {
            textDisplay.setPersistent(false);
            textDisplay.setGravity(false);
            textDisplay.setBillboard(Display.Billboard.CENTER);
            textDisplay.setSeeThrough(true);
            textDisplay.setShadowed(true);
            textDisplay.setDefaultBackground(false);
            textDisplay.setTextOpacity((byte) 255);
        });
        holograms.put(entity.getUniqueId(), display.getUniqueId());
    }

    private void updateBossBar(LivingEntity entity, BossType type) {
        BossBar bossBar = bossBars.get(entity.getUniqueId());
        if (bossBar == null) {
            return;
        }

        int phase = bossPhase(entity);
        bossBar.setTitle(type.plainDisplayName() + " | Phase " + phase);
        double maxHealth = Math.max(1.0, entity.getAttribute(Attribute.MAX_HEALTH) == null ? type.maxHealth() : entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, entity.getHealth() / maxHealth)));
        bossBar.setColor(phase >= 2 ? BarColor.PURPLE : BarColor.RED);
        bossBar.setVisible(true);

        Set<Player> nearby = new HashSet<>();
        for (Player player : entity.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(entity.getLocation()) <= 64 * 64) {
                nearby.add(player);
                if (!bossBar.getPlayers().contains(player)) {
                    bossBar.addPlayer(player);
                }
            }
        }
        for (Player viewer : new ArrayList<>(bossBar.getPlayers())) {
            if (!nearby.contains(viewer)) {
                bossBar.removePlayer(viewer);
            }
        }
    }

    private void updateBossHologram(LivingEntity entity, BossType type) {
        UUID hologramId = holograms.get(entity.getUniqueId());
        Entity existing = hologramId == null ? null : Bukkit.getEntity(hologramId);
        TextDisplay display;
        if (existing instanceof TextDisplay current && current.isValid()) {
            display = current;
        } else {
            holograms.remove(entity.getUniqueId());
            ensureBossHologram(entity, type);
            hologramId = holograms.get(entity.getUniqueId());
            existing = hologramId == null ? null : Bukkit.getEntity(hologramId);
            if (!(existing instanceof TextDisplay refreshed) || !refreshed.isValid()) {
                return;
            }
            display = refreshed;
        }

        display.teleport(entity.getLocation().clone().add(0.0, entity.getHeight() + 0.85, 0.0));
        int phase = bossPhase(entity);
        double maxHealth = Math.max(1.0, entity.getAttribute(Attribute.MAX_HEALTH) == null ? type.maxHealth() : entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        display.text(MM.deserialize(
            type.displayName()
                + "\n<gray>Phase <white>" + phase + "</white> <dark_gray>|</dark_gray> HP <white>"
                + trimNumber(entity.getHealth()) + "/" + trimNumber(maxHealth) + "</white></gray>"
        ));
    }

    private void destroyBossVisuals(UUID bossId) {
        BossBar bar = bossBars.remove(bossId);
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }
        UUID hologramId = holograms.remove(bossId);
        Entity hologram = hologramId == null ? null : Bukkit.getEntity(hologramId);
        if (hologram != null) {
            hologram.remove();
        }
    }

    private void destroyAllBossVisuals() {
        for (UUID bossId : new HashSet<>(bossBars.keySet())) {
            destroyBossVisuals(bossId);
        }
        for (UUID bossId : new HashSet<>(holograms.keySet())) {
            destroyBossVisuals(bossId);
        }
    }

    private int bossPhase(LivingEntity entity) {
        return entity.getPersistentDataContainer().getOrDefault(keyBossPhase, PersistentDataType.INTEGER, 1);
    }

    private void setBossPhase(LivingEntity entity, int phase) {
        entity.getPersistentDataContainer().set(keyBossPhase, PersistentDataType.INTEGER, Math.max(1, phase));
    }

    private long bossCooldownAt(LivingEntity entity, NamespacedKey key) {
        return entity.getPersistentDataContainer().getOrDefault(key, PersistentDataType.LONG, 0L);
    }

    private boolean bossCooldownReady(LivingEntity entity, NamespacedKey key) {
        return System.currentTimeMillis() >= bossCooldownAt(entity, key);
    }

    private void setBossCooldown(LivingEntity entity, NamespacedKey key, long cooldownMs) {
        long readyAt = Math.max(System.currentTimeMillis(), System.currentTimeMillis() + Math.max(0L, cooldownMs));
        entity.getPersistentDataContainer().set(key, PersistentDataType.LONG, readyAt);
    }

    private LivingEntity currentBossTarget(LivingEntity entity) {
        if (!(entity instanceof Mob mob)) {
            return null;
        }
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isValid() || target.isDead() || target.getWorld() != entity.getWorld()) {
            return null;
        }
        return target;
    }

    private void tickBossBehavior(LivingEntity entity, BossType type) {
        switch (type) {
            case YULE_THE_MINION -> tickYuleTheMinion(entity);
            case KAEL_THE_ASHEN -> tickKaelTheAshen(entity);
            case VESPER_THE_WIDOW_QUEEN -> tickVesperTheWidowQueen(entity);
            case VORALITH_THE_CRIMSON_WARDEN -> tickVoralithTheCrimsonWarden(entity);
        }
    }

    private void tickYuleTheMinion(LivingEntity entity) {
        if (bossPhase(entity) >= 2) {
            return;
        }
        double maxHealth = Math.max(1.0, entity.getAttribute(Attribute.MAX_HEALTH) == null ? 1.0 : entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        if (entity.getHealth() > maxHealth * 0.50) {
            return;
        }

        setBossPhase(entity, 2);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0, false, true, true));
        spawnYulePhaseTwoMinions(entity);
        entity.getWorld().spawnParticle(Particle.DUST, entity.getLocation().clone().add(0.0, 1.0, 0.0), 28, 0.35, 0.55, 0.35, 0.0, new Particle.DustOptions(Color.fromRGB(178, 34, 34), 1.2f));
        entity.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, entity.getLocation().clone().add(0.0, 1.1, 0.0), 12, 0.25, 0.40, 0.25, 0.0);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.9f, 0.65f);
    }

    private void spawnYulePhaseTwoMinions(LivingEntity bossEntity) {
        BossRecord record = bossRecord(bossEntity);
        if (record == null || bossEntity.getWorld() == null) {
            return;
        }

        LivingEntity currentTarget = currentBossTarget(bossEntity);
        World world = bossEntity.getWorld();
        Location origin = bossEntity.getLocation();
        double[][] offsets = {
            {2.0, 0.0},
            {-2.0, 0.0},
            {0.0, 2.0}
        };

        for (double[] offset : offsets) {
            Location spawn = origin.clone().add(offset[0], 0.0, offset[1]);
            spawn = findGroundedSpawn(world, spawn);
            Zombie minion = world.spawn(spawn, Zombie.class, zombie -> {
                zombie.setAdult();
                zombie.setPersistent(true);
                zombie.setRemoveWhenFarAway(false);
                zombie.setCanPickupItems(false);
                zombie.setConversionTime(-1);
                zombie.customName(MM.deserialize("<red>Yule's Thrall</red>"));
                zombie.setCustomNameVisible(false);
            });

            clearBossEquipment(minion);
            equipBossArmor(
                minion,
                new ItemStack(Material.GOLDEN_HELMET),
                new ItemStack(Material.GOLDEN_CHESTPLATE),
                new ItemStack(Material.GOLDEN_LEGGINGS),
                new ItemStack(Material.GOLDEN_BOOTS)
            );
            equipBossHands(minion, new ItemStack(Material.GOLDEN_SWORD), null);
            setAttributeBase(minion, Attribute.MAX_HEALTH, 40.0);
            minion.setHealth(40.0);
            setAttributeBase(minion, Attribute.ATTACK_DAMAGE, 8.0);
            setAttributeBase(minion, Attribute.MOVEMENT_SPEED, 0.33);
            setAttributeBase(minion, Attribute.FOLLOW_RANGE, 28.0);
            setAttributeBase(minion, Attribute.KNOCKBACK_RESISTANCE, 0.20);
            markBossMinion(minion, record.entityUuid());
            if (currentTarget != null && currentTarget.isValid() && !currentTarget.isDead()) {
                minion.setTarget(currentTarget);
            }

            Location center = minion.getLocation().clone().add(0.0, 1.0, 0.0);
            world.spawnParticle(Particle.SMOKE, center, 10, 0.25, 0.35, 0.25, 0.02);
            world.spawnParticle(
                Particle.DUST,
                center,
                10,
                0.18,
                0.25,
                0.18,
                0.0,
                new Particle.DustOptions(Color.fromRGB(196, 80, 40), 1.0f)
            );
        }
    }

    private Location findGroundedSpawn(World world, Location preferred) {
        Location candidate = preferred.clone();
        int baseY = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 2, candidate.getBlockY()));
        candidate.setY(baseY);

        for (int offset = 0; offset <= 4; offset++) {
            int[] checks = offset == 0 ? new int[]{0} : new int[]{offset, -offset};
            for (int dy : checks) {
                int y = baseY + dy;
                if (y < world.getMinHeight() || y >= world.getMaxHeight() - 1) {
                    continue;
                }
                Block feet = world.getBlockAt(candidate.getBlockX(), y, candidate.getBlockZ());
                Block head = feet.getRelative(BlockFace.UP);
                Block below = feet.getRelative(BlockFace.DOWN);
                if (!feet.isPassable() || !head.isPassable() || below.isPassable()) {
                    continue;
                }
                return feet.getLocation().add(0.5, 0.0, 0.5);
            }
        }
        return preferred.clone().add(0.0, 0.1, 0.0);
    }

    private void tickKaelTheAshen(LivingEntity entity) {
        double maxHealth = Math.max(1.0, entity.getAttribute(Attribute.MAX_HEALTH) == null ? 1.0 : entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        if (bossPhase(entity) < 2 && entity.getHealth() <= maxHealth * 0.45) {
            setBossPhase(entity, 2);
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, true, true));
            entity.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, entity.getLocation().clone().add(0.0, 1.0, 0.0), 36, 0.35, 0.55, 0.35, 0.02);
            entity.getWorld().spawnParticle(Particle.SMOKE, entity.getLocation().clone().add(0.0, 1.0, 0.0), 18, 0.30, 0.45, 0.30, 0.03);
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.75f, 1.35f);
        }

        LivingEntity target = currentBossTarget(entity);
        if (target == null || !bossCooldownReady(entity, keyBossSecondaryCooldown)) {
            return;
        }
        if (entity.getLocation().distanceSquared(target.getLocation()) > 18 * 18 || !entity.hasLineOfSight(target)) {
            return;
        }

        entity.getWorld().spawnParticle(Particle.SOUL, entity.getEyeLocation(), 10, 0.16, 0.12, 0.16, 0.01);
        entity.getWorld().spawnParticle(Particle.ENCHANT, entity.getEyeLocation(), 12, 0.22, 0.18, 0.22, 0.2);
        setBossCooldown(entity, keyBossSecondaryCooldown, bossPhase(entity) >= 2 ? 2200L : 3600L);
    }

    private void tickVesperTheWidowQueen(LivingEntity entity) {
        double maxHealth = Math.max(1.0, entity.getAttribute(Attribute.MAX_HEALTH) == null ? 1.0 : entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        if (bossPhase(entity) < 2 && entity.getHealth() <= maxHealth * 0.50) {
            setBossPhase(entity, 2);
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, true, true));
            entity.getWorld().spawnParticle(Particle.SQUID_INK, entity.getLocation().clone().add(0.0, 0.8, 0.0), 18, 0.35, 0.28, 0.35, 0.02);
            entity.getWorld().spawnParticle(Particle.DUST, entity.getLocation().clone().add(0.0, 1.0, 0.0), 26, 0.35, 0.45, 0.35, 0.0, new Particle.DustOptions(Color.fromRGB(88, 160, 64), 1.1f));
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 0.9f, 0.6f);
        }

        LivingEntity target = currentBossTarget(entity);
        if (target == null || !entity.isOnGround() || !bossCooldownReady(entity, keyBossPrimaryCooldown)) {
            return;
        }

        double distanceSquared = entity.getLocation().distanceSquared(target.getLocation());
        if (distanceSquared < 3.5 * 3.5 || distanceSquared > 12.0 * 12.0) {
            return;
        }

        Vector leap = target.getEyeLocation().toVector().subtract(entity.getLocation().toVector());
        if (leap.lengthSquared() <= 1.0E-6) {
            return;
        }
        leap.normalize().multiply(bossPhase(entity) >= 2 ? 1.08 : 0.92);
        leap.setY(bossPhase(entity) >= 2 ? 0.52 : 0.42);
        entity.setVelocity(leap);
        entity.getWorld().spawnParticle(Particle.SQUID_INK, entity.getLocation().clone().add(0.0, 0.5, 0.0), 10, 0.24, 0.12, 0.24, 0.01);
        entity.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, entity.getLocation().clone().add(0.0, 0.6, 0.0), 16, 0.26, 0.18, 0.26, 0.02);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_SPIDER_STEP, 1.0f, bossPhase(entity) >= 2 ? 0.6f : 0.8f);
        setBossCooldown(entity, keyBossPrimaryCooldown, bossPhase(entity) >= 2 ? 3000L : 4600L);
    }

    private void tickVoralithTheCrimsonWarden(LivingEntity entity) {
        double maxHealth = Math.max(1.0, entity.getAttribute(Attribute.MAX_HEALTH) == null ? 1.0 : entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        if (bossPhase(entity) < 2 && entity.getHealth() <= maxHealth * 0.45) {
            setBossPhase(entity, 2);
            entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0, false, true, true));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, true, true));
            entity.getWorld().spawnParticle(Particle.SONIC_BOOM, entity.getLocation().clone().add(0.0, 1.35, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
            entity.getWorld().spawnParticle(Particle.SCULK_SOUL, entity.getLocation().clone().add(0.0, 1.0, 0.0), 32, 0.45, 0.65, 0.45, 0.02);
            entity.getWorld().spawnParticle(
                Particle.DUST,
                entity.getLocation().clone().add(0.0, 1.0, 0.0),
                28,
                0.40,
                0.55,
                0.40,
                0.0,
                new Particle.DustOptions(Color.fromRGB(180, 30, 55), 1.2f)
            );
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.6f, 0.72f);
        }

        LivingEntity target = currentBossTarget(entity);
        if (target == null) {
            return;
        }

        double distanceSquared = entity.getLocation().distanceSquared(target.getLocation());
        if (bossCooldownReady(entity, keyBossPrimaryCooldown) && distanceSquared <= 8.0 * 8.0) {
            unleashDominionPulse(entity, bossPhase(entity));
        }
        if (bossPhase(entity) >= 2
            && bossCooldownReady(entity, keyBossSecondaryCooldown)
            && distanceSquared <= 18.0 * 18.0
            && entity.hasLineOfSight(target)) {
            unleashCrimsonResonance(entity, target);
        }
    }

    private void spawnYuleAttackParticles(LivingEntity attacker, LivingEntity target, int phase) {
        World world = target.getWorld();
        Location center = target.getLocation().clone().add(0.0, 1.0, 0.0);
        if (phase >= 2) {
            world.spawnParticle(Particle.DUST, center, 14, 0.28, 0.38, 0.28, 0.0, new Particle.DustOptions(Color.fromRGB(185, 35, 35), 1.05f));
            world.spawnParticle(Particle.CRIT, center, 10, 0.25, 0.35, 0.25, 0.02);
            world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.85f, 0.75f);
        } else {
            world.spawnParticle(Particle.CRIT, center, 8, 0.22, 0.32, 0.22, 0.02);
            world.spawnParticle(Particle.SMOKE, center, 6, 0.18, 0.22, 0.18, 0.01);
            world.playSound(center, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.65f, 1.1f);
        }
    }

    private void applyYulePhaseTwoKnockback(LivingEntity attacker, LivingEntity target) {
        Vector direction = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
        if (direction.lengthSquared() <= 1.0E-6) {
            return;
        }
        Vector knockback = direction.normalize().multiply(0.85);
        knockback.setY(Math.max(0.22, target.getVelocity().getY()));
        target.setVelocity(target.getVelocity().add(knockback));
    }

    private void handleKaelBowShot(LivingEntity shooter, Projectile projectile, int phase) {
        Location eye = shooter.getEyeLocation();
        World world = shooter.getWorld();
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, eye, phase >= 2 ? 14 : 8, 0.10, 0.10, 0.10, 0.01);
        world.spawnParticle(Particle.SMOKE, eye, phase >= 2 ? 8 : 4, 0.08, 0.08, 0.08, 0.01);
        world.playSound(shooter.getLocation(), Sound.ENTITY_SKELETON_SHOOT, 1.0f, phase >= 2 ? 0.7f : 0.95f);

        if (projectile instanceof org.bukkit.entity.AbstractArrow arrow) {
            arrow.setCritical(true);
            arrow.setDamage(arrow.getDamage() + (phase >= 2 ? 3.0 : 1.5));
            if (phase >= 2) {
                arrow.setFireTicks(100);
            } else {
                arrow.setPierceLevel(Math.max(arrow.getPierceLevel(), 1));
            }
        }
    }

    private void handleKaelProjectileHit(LivingEntity attacker, LivingEntity target, int phase, EntityDamageByEntityEvent event) {
        Location center = target.getLocation().clone().add(0.0, 1.0, 0.0);
        World world = target.getWorld();
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, phase >= 2 ? 16 : 10, 0.22, 0.30, 0.22, 0.01);
        world.spawnParticle(Particle.CRIT, center, 8, 0.20, 0.26, 0.20, 0.02);
        if (phase >= 2) {
            target.setFireTicks(Math.max(target.getFireTicks(), 100));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 70, 0, false, true, true));
            event.setDamage(event.getDamage() + 2.0);
            Vector push = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
            if (push.lengthSquared() > 1.0E-6) {
                push.normalize().multiply(0.45).setY(Math.max(0.18, target.getVelocity().getY()));
                target.setVelocity(target.getVelocity().add(push));
            }
        } else {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0, false, true, true));
            event.setDamage(event.getDamage() + 1.0);
        }
    }

    private void handleVesperMeleeHit(LivingEntity attacker, LivingEntity target, int phase) {
        World world = target.getWorld();
        Location center = target.getLocation().clone().add(0.0, 0.9, 0.0);
        world.spawnParticle(Particle.SQUID_INK, center, 10, 0.18, 0.20, 0.18, 0.01);
        world.spawnParticle(Particle.DUST, center, 12, 0.22, 0.25, 0.22, 0.0, new Particle.DustOptions(Color.fromRGB(88, 160, 64), phase >= 2 ? 1.15f : 0.9f));
        world.playSound(center, Sound.ENTITY_SPIDER_HURT, 0.8f, phase >= 2 ? 0.7f : 0.95f);

        if (phase >= 2) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 90, 1, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, false, true, true));
            Vector drag = attacker.getLocation().toVector().subtract(target.getLocation().toVector());
            if (drag.lengthSquared() > 1.0E-6) {
                target.setVelocity(target.getVelocity().add(drag.normalize().multiply(0.25).setY(0.12)));
            }
        } else {
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0, false, true, true));
        }
    }

    private void unleashDominionPulse(LivingEntity entity, int phase) {
        Location center = entity.getLocation().clone().add(0.0, 1.0, 0.0);
        World world = entity.getWorld();
        double radius = phase >= 2 ? 7.0 : 6.0;

        world.spawnParticle(Particle.SONIC_BOOM, center, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticle(Particle.SCULK_SOUL, center, phase >= 2 ? 42 : 26, 0.90, 0.40, 0.90, 0.03);
        world.spawnParticle(
            Particle.DUST,
            center,
            phase >= 2 ? 30 : 18,
            1.10,
            0.45,
            1.10,
            0.0,
            new Particle.DustOptions(Color.fromRGB(190, 25, 45), phase >= 2 ? 1.35f : 1.05f)
        );
        world.playSound(entity.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.7f, phase >= 2 ? 0.72f : 0.9f);

        for (Entity nearby : entity.getNearbyEntities(radius, 2.5, radius)) {
            if (!(nearby instanceof LivingEntity target) || target.equals(entity) || target.isDead() || !target.isValid()) {
                continue;
            }
            if (target instanceof Player player
                && (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR)) {
                continue;
            }

            Vector push = target.getLocation().toVector().subtract(entity.getLocation().toVector());
            if (push.lengthSquared() > 1.0E-6) {
                push.normalize().multiply(phase >= 2 ? 1.05 : 0.65);
                push.setY(phase >= 2 ? 0.30 : 0.20);
                target.setVelocity(target.getVelocity().add(push));
            }
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, phase >= 2 ? 100 : 60, 0, false, true, true));
            target.damage(phase >= 2 ? 8.0 : 5.0, entity);
        }

        setBossCooldown(entity, keyBossPrimaryCooldown, phase >= 2 ? 5200L : 7000L);
    }

    private void unleashCrimsonResonance(LivingEntity entity, LivingEntity target) {
        Location eye = entity.getEyeLocation();
        Vector direction = target.getEyeLocation().toVector().subtract(eye.toVector());
        double length = Math.min(18.0, direction.length());
        if (length <= 0.5) {
            return;
        }

        direction.normalize();
        World world = entity.getWorld();
        for (double step = 0.5; step <= length; step += 0.65) {
            Location point = eye.clone().add(direction.clone().multiply(step));
            world.spawnParticle(Particle.SCULK_SOUL, point, 2, 0.04, 0.04, 0.04, 0.0);
            world.spawnParticle(
                Particle.DUST,
                point,
                1,
                0.0,
                0.0,
                0.0,
                0.0,
                new Particle.DustOptions(Color.fromRGB(220, 40, 65), 1.15f)
            );
        }
        world.spawnParticle(Particle.SONIC_BOOM, target.getLocation().clone().add(0.0, 1.0, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
        world.playSound(entity.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.35f, 0.56f);
        world.playSound(target.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 0.82f);

        target.damage(10.0, entity);
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 120, 0, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, false, true, true));

        Vector shove = target.getLocation().toVector().subtract(entity.getLocation().toVector());
        if (shove.lengthSquared() > 1.0E-6) {
            shove.normalize().multiply(0.70);
            shove.setY(Math.max(0.24, target.getVelocity().getY()));
            target.setVelocity(target.getVelocity().add(shove));
        }

        setBossCooldown(entity, keyBossSecondaryCooldown, 9500L);
    }

    private void handleVoralithMeleeHit(LivingEntity attacker, LivingEntity target, int phase, EntityDamageByEntityEvent event) {
        World world = target.getWorld();
        Location center = target.getLocation().clone().add(0.0, 1.0, 0.0);
        world.spawnParticle(Particle.SCULK_SOUL, center, 16, 0.28, 0.35, 0.28, 0.02);
        world.spawnParticle(
            Particle.DUST,
            center,
            14,
            0.25,
            0.30,
            0.25,
            0.0,
            new Particle.DustOptions(Color.fromRGB(190, 32, 45), phase >= 2 ? 1.2f : 0.95f)
        );
        world.playSound(center, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.0f, phase >= 2 ? 0.7f : 0.88f);

        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, phase >= 2 ? 100 : 60, 0, false, true, true));
        if (phase >= 2) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 0, false, true, true));
            event.setDamage(event.getDamage() + 2.0);
            Vector slam = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
            if (slam.lengthSquared() > 1.0E-6) {
                slam.normalize().multiply(0.85);
                slam.setY(Math.max(0.28, target.getVelocity().getY()));
                target.setVelocity(target.getVelocity().add(slam));
            }
        } else {
            event.setDamage(event.getDamage() + 1.0);
        }
    }

    private Location findBossSpawnLocation(Player player, BossType type) {
        Block targetBlock = player.getTargetBlockExact(16);
        Location seed = targetBlock != null
            ? targetBlock.getLocation().clone().add(0.5, 1.0, 0.5)
            : player.getLocation().clone();
        Location safe = findSafeBossSpawnLocation(seed, type);
        if (safe != null) {
            return safe;
        }
        return findSafeBossSpawnLocation(player.getLocation().clone(), type);
    }

    private Location findSafeBossSpawnLocation(Location seed, BossType type) {
        World world = seed.getWorld();
        if (world == null) {
            return null;
        }

        int baseX = seed.getBlockX();
        int baseY = Math.max(world.getMinHeight() + 1, Math.min(seed.getBlockY(), world.getMaxHeight() - type.requiredAirBlocks() - 1));
        int baseZ = seed.getBlockZ();

        for (int radius = 0; radius <= 2; radius++) {
            for (int dy = -1; dy <= 3; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                            continue;
                        }
                        Location candidate = new Location(world, baseX + dx + 0.5, baseY + dy, baseZ + dz + 0.5, seed.getYaw(), seed.getPitch());
                        if (isSafeBossSpawnLocation(candidate, type.requiredAirBlocks())) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isSafeBossSpawnLocation(Location location, int requiredAirBlocks) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        int blockY = location.getBlockY();
        if (blockY <= world.getMinHeight() || blockY + requiredAirBlocks >= world.getMaxHeight()) {
            return false;
        }

        Block floor = location.getBlock().getRelative(BlockFace.DOWN);
        if (floor.isPassable() || floor.isLiquid() || floor.getType() == Material.LAVA || floor.getType() == Material.MAGMA_BLOCK) {
            return false;
        }

        Block current = location.getBlock();
        for (int i = 0; i < requiredAirBlocks; i++) {
            if (!current.isPassable() || current.isLiquid()) {
                return false;
            }
            current = current.getRelative(BlockFace.UP);
        }
        return true;
    }

    private int despawnBossRecords(java.util.function.Predicate<BossRecord> predicate) {
        reconcileLoadedBosses();

        Set<UUID> removedIds = new HashSet<>();
        int removed = 0;
        List<BossRecord> snapshot = new ArrayList<>(trackedBosses.values());
        snapshot.sort(Comparator.comparing(BossRecord::spawnedAt));
        for (BossRecord record : snapshot) {
            if (!predicate.test(record)) {
                continue;
            }
            removed += removeBossRecord(record, removedIds) ? 1 : 0;
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                BossRecord record = bossRecord(entity);
                if (record == null || !predicate.test(record) || !removedIds.add(record.entityUuid())) {
                    continue;
                }
                destroyBossVisuals(record.entityUuid());
                entity.remove();
                removed++;
                untrackRecord(record.entityUuid());
                plugin.getDatabase().deleteBossRecord(record.entityUuid());
            }
        }

        return removed;
    }

    private boolean removeBossRecord(BossRecord record, Set<UUID> removedIds) {
        if (record == null || !removedIds.add(record.entityUuid())) {
            return false;
        }

        World world = Bukkit.getWorld(record.world());
        if (world != null) {
            world.getChunkAt(record.chunkX(), record.chunkZ());
        }

        Entity entity = Bukkit.getEntity(record.entityUuid());
        if (entity != null && entity.isValid()) {
            entity.remove();
        }

        despawnBossMinions(record.entityUuid());
        destroyBossVisuals(record.entityUuid());
        untrackRecord(record.entityUuid());
        plugin.getDatabase().deleteBossRecord(record.entityUuid());
        return true;
    }

    private void reconcileLoadedBosses() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                reconcileChunk(chunk);
            }
        }
    }

    private void reconcileChunk(Chunk chunk) {
        cleanupOrphanBossMinions(chunk);
        Map<UUID, Entity> foundEntities = new HashMap<>();
        for (Entity entity : chunk.getEntities()) {
            BossRecord record = bossRecord(entity);
            if (record == null) {
                continue;
            }
            foundEntities.put(record.entityUuid(), entity);
            BossRecord existing = trackedBosses.get(record.entityUuid());
            if (existing == null || !sameRecord(existing, record)) {
                trackRecord(record);
                plugin.getDatabase().saveBossRecord(record);
            }
        }

        List<BossRecord> snapshot = new ArrayList<>(trackedBosses.values());
        for (BossRecord record : snapshot) {
            if (!record.world().equals(chunk.getWorld().getName())
                || record.chunkX() != chunk.getX()
                || record.chunkZ() != chunk.getZ()) {
                continue;
            }
            if (foundEntities.containsKey(record.entityUuid())) {
                continue;
            }
            destroyBossVisuals(record.entityUuid());
            untrackRecord(record.entityUuid());
            plugin.getDatabase().deleteBossRecord(record.entityUuid());
        }
    }

    private void markBossMinion(LivingEntity entity, UUID ownerBossId) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(keyBossMinionMarker, PersistentDataType.BYTE, (byte) 1);
        pdc.set(keyBossMinionOwner, PersistentDataType.STRING, ownerBossId.toString());
        entity.addScoreboardTag(SCOREBOARD_TAG + "_minion");
    }

    private boolean isBossMinion(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(keyBossMinionMarker, PersistentDataType.BYTE);
    }

    private UUID bossMinionOwner(Entity entity) {
        if (!isBossMinion(entity)) {
            return null;
        }
        String raw = entity.getPersistentDataContainer().get(keyBossMinionOwner, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void despawnBossMinions(UUID ownerBossId) {
        if (ownerBossId == null) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!isBossMinion(entity) || !ownerBossId.equals(bossMinionOwner(entity))) {
                    continue;
                }
                entity.remove();
            }
        }
    }

    private void cleanupOrphanBossMinions(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            UUID ownerBossId = bossMinionOwner(entity);
            if (ownerBossId == null) {
                continue;
            }
            Entity owner = Bukkit.getEntity(ownerBossId);
            if (owner instanceof LivingEntity living && living.isValid() && !living.isDead() && trackedBosses.containsKey(ownerBossId)) {
                continue;
            }
            entity.remove();
        }
    }

    private boolean sameRecord(BossRecord left, BossRecord right) {
        return left.entityUuid().equals(right.entityUuid())
            && left.bossId().equals(right.bossId())
            && left.world().equals(right.world())
            && left.chunkX() == right.chunkX()
            && left.chunkZ() == right.chunkZ();
    }

    private BossRecord bossRecord(Entity entity) {
        if (entity == null) {
            return null;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (!pdc.has(keyBossMarker, PersistentDataType.BYTE)) {
            return null;
        }
        String bossId = pdc.get(keyBossId, PersistentDataType.STRING);
        String instanceId = pdc.get(keyBossInstanceId, PersistentDataType.STRING);
        if (bossId == null || bossId.isBlank() || instanceId == null || instanceId.isBlank()) {
            return null;
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(instanceId);
        } catch (IllegalArgumentException ex) {
            return null;
        }

        Location location = entity.getLocation();
        return new BossRecord(
            uuid,
            bossId.trim().toLowerCase(Locale.ROOT),
            location.getWorld().getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            entity.getChunk().getX(),
            entity.getChunk().getZ(),
            System.currentTimeMillis()
        );
    }

    private void trackRecord(BossRecord record) {
        trackedBosses.put(record.entityUuid(), record);
        trackedByBossId.computeIfAbsent(record.bossId(), key -> ConcurrentHashMap.newKeySet()).add(record.entityUuid());
    }

    private void untrackRecord(UUID entityUuid) {
        BossRecord removed = trackedBosses.remove(entityUuid);
        if (removed == null) {
            return;
        }
        Set<UUID> bossSet = trackedByBossId.get(removed.bossId());
        if (bossSet != null) {
            bossSet.remove(entityUuid);
            if (bossSet.isEmpty()) {
                trackedByBossId.remove(removed.bossId());
            }
        }
    }

    private int activeCount(String bossId) {
        String normalized = normalizeBossId(bossId);
        if (normalized == null) {
            return 0;
        }
        Set<UUID> ids = trackedByBossId.getOrDefault(normalized, Collections.emptySet());
        return ids.size();
    }

    private String trimNumber(double value) {
        if (Math.rint(value) == value) {
            return Integer.toString((int) value);
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private String prettyBossName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Unknown";
        }
        String[] parts = raw.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            out.append(part.substring(1));
        }
        return out.toString();
    }

    public record BossActionResult(boolean success, String message) {
    }

    @FunctionalInterface
    public interface BossConfigurer {
        void apply(BossManager manager, LivingEntity entity);
    }

    // Add future bosses here. The boss GUI and boss commands populate from this enum automatically.
    public enum BossType {
        YULE_THE_MINION(
            "yule_the_minion",
            EntityType.ZOMBIE,
            Material.ZOMBIE_HEAD,
            "<gradient:#d97706:#ef4444><bold>Yule the Minion</bold></gradient>",
            300.0,
            14.0,
            0.33,
            40.0,
            0.35,
            2,
            false,
            List.of(
                "<gray>A disciplined undead bruiser that stays dangerous into late gear.</gray>",
                "<gray>Phase One:</gray> <white>fast melee pressure with steady damage</white>",
                "<gray>Phase Two:</gray> <white>awakens with Strength and much heavier knockback</white>",
                "<gray>Every custom boss gets a boss bar and live hologram automatically.</gray>"
            ),
            (manager, entity) -> {
                if (entity instanceof Zombie zombie) {
                    zombie.setConversionTime(-1);
                }
                manager.equipBossArmor(
                    entity,
                    new ItemStack(Material.GOLDEN_HELMET),
                    new ItemStack(Material.GOLDEN_CHESTPLATE),
                    new ItemStack(Material.GOLDEN_LEGGINGS),
                    new ItemStack(Material.GOLDEN_BOOTS)
                );
            }
        ),
        KAEL_THE_ASHEN(
            "kael_the_ashen",
            EntityType.SKELETON,
            Material.BOW,
            "<gradient:#94a3b8:#e2e8f0><bold>Kael the Ashen</bold></gradient>",
            240.0,
            8.0,
            0.29,
            48.0,
            0.20,
            2,
            false,
            List.of(
                "<gray>An ash-marked marksman that punishes space and line of sight.</gray>",
                "<gray>Phase One:</gray> <white>piercing arrows that sap your strength</white>",
                "<gray>Phase Two:</gray> <white>faster pace, burning arrows, and stronger control</white>",
                "<gray>Stay mobile or he will pin fights down from range.</gray>"
            ),
            (manager, entity) -> {
                if (entity instanceof Skeleton skeleton) {
                    skeleton.setConversionTime(-1);
                }
                manager.equipBossArmor(
                    entity,
                    new ItemStack(Material.CHAINMAIL_HELMET),
                    new ItemStack(Material.CHAINMAIL_CHESTPLATE),
                    new ItemStack(Material.CHAINMAIL_LEGGINGS),
                    new ItemStack(Material.CHAINMAIL_BOOTS)
                );
                manager.equipBossHands(entity, new ItemStack(Material.BOW), null);
            }
        ),
        VESPER_THE_WIDOW_QUEEN(
            "vesper_the_widow_queen",
            EntityType.SPIDER,
            Material.COBWEB,
            "<gradient:#22c55e:#84cc16><bold>Vesper the Widow Queen</bold></gradient>",
            280.0,
            11.0,
            0.38,
            36.0,
            0.45,
            2,
            false,
            List.of(
                "<gray>A relentless predator that dives through gaps and mauls stragglers.</gray>",
                "<gray>Phase One:</gray> <white>venom bites and measured leap pressure</white>",
                "<gray>Phase Two:</gray> <white>faster leaps, harsher poison, and a dragging strike</white>",
                "<gray>Do not let her control the gap or the fight snowballs fast.</gray>"
            ),
            (manager, entity) -> { }
        ),
        VORALITH_THE_CRIMSON_WARDEN(
            "voralith_the_crimson_warden",
            EntityType.WARDEN,
            Material.SCULK_SHRIEKER,
            "<gradient:#991b1b:#ef4444><bold>Voralith the Crimson Warden</bold></gradient>",
            650.0,
            16.0,
            0.31,
            48.0,
            0.90,
            4,
            false,
            List.of(
                "<gray>A deep-dark tyrant that mixes Warden pressure with crimson shockwaves.</gray>",
                "<gray>Phase One:</gray> <white>dominion pulses, darkness, and bruising melee hits</white>",
                "<gray>Phase Two:</gray> <white>resonance blasts, harder slams, and much heavier punishment</white>",
                "<gray>Drops a <white>Dominion Core</white> used to repair <white>Crimson Dominion</white>.</gray>"
            ),
            (manager, entity) -> {
                if (entity instanceof Warden warden) {
                    warden.setCanPickupItems(false);
                }
            }
        );

        private static final Map<String, BossType> BY_ID = new HashMap<>();
        static {
            for (BossType type : values()) {
                BY_ID.put(type.id, type);
            }
        }

        private final String id;
        private final EntityType entityType;
        private final Material menuIcon;
        private final String displayName;
        private final double maxHealth;
        private final double attackDamage;
        private final double movementSpeed;
        private final double followRange;
        private final double knockbackResistance;
        private final int requiredAirBlocks;
        private final boolean glowing;
        private final List<String> description;
        private final BossConfigurer configurer;

        BossType(
            String id,
            EntityType entityType,
            Material menuIcon,
            String displayName,
            double maxHealth,
            double attackDamage,
            double movementSpeed,
            double followRange,
            double knockbackResistance,
            int requiredAirBlocks,
            boolean glowing,
            List<String> description,
            BossConfigurer configurer
        ) {
            this.id = id;
            this.entityType = entityType;
            this.menuIcon = menuIcon;
            this.displayName = displayName;
            this.maxHealth = maxHealth;
            this.attackDamage = attackDamage;
            this.movementSpeed = movementSpeed;
            this.followRange = followRange;
            this.knockbackResistance = knockbackResistance;
            this.requiredAirBlocks = Math.max(2, requiredAirBlocks);
            this.glowing = glowing;
            this.description = List.copyOf(description);
            this.configurer = configurer == null ? (manager, entity) -> { } : configurer;
        }

        public static BossType fromId(String id) {
            return id == null ? null : BY_ID.get(id.trim().toLowerCase(Locale.ROOT));
        }

        public static BossType fromInput(String input) {
            if (input == null || input.isBlank()) {
                return null;
            }
            String normalized = input.trim().toLowerCase(Locale.ROOT).replace('-', '_');
            for (BossType type : values()) {
                if (type.id.equals(normalized) || type.commandToken().equals(normalized)) {
                    return type;
                }
            }
            return null;
        }

        public String id() {
            return id;
        }

        public EntityType entityType() {
            return entityType;
        }

        public Material menuIcon() {
            return menuIcon;
        }

        public String displayName() {
            return displayName;
        }

        public double maxHealth() {
            return maxHealth;
        }

        public double attackDamage() {
            return attackDamage;
        }

        public double movementSpeed() {
            return movementSpeed;
        }

        public double followRange() {
            return followRange;
        }

        public double knockbackResistance() {
            return knockbackResistance;
        }

        public int requiredAirBlocks() {
            return requiredAirBlocks;
        }

        public boolean glowing() {
            return glowing;
        }

        public List<String> description() {
            return description;
        }

        public BossConfigurer configurer() {
            return configurer;
        }

        public String plainDisplayName() {
            return MiniMessage.miniMessage().stripTags(displayName);
        }

        public String commandToken() {
            return plainDisplayName()
                .toLowerCase(Locale.ROOT)
                .replace("'", "")
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        }
    }

    private record BossMenuHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
