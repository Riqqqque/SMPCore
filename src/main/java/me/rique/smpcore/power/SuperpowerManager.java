package me.rique.smpcore.power;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.awakening.AwakeningTableListener;
import me.rique.smpcore.item.CustomEnchantListener;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.GrindstoneInventory;
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
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public final class SuperpowerManager implements Listener {

    public static final String ANCIENT_SCROLL_ITEM_ID = "ancient_scroll";
    public static final String WARDEN_HEART_ITEM_ID = "warden_heart";
    public static final String MOTHER_NATURE_STICK_ITEM_ID = "mother_nature_stick";

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Component POWERS_MENU_TITLE =
        MM.deserialize("<gradient:#ff9a3d:#d61c4e><bold>Superpower Info</bold></gradient>");

    private static final int MENU_SIZE = 27;
    private static final int SHADOW_DURATION_SECONDS = 15 * 60;
    private static final int SHADOW_COOLDOWN_SECONDS = 5 * 60;
    private static final int SHADOW_HIT_COOLDOWN_SECONDS = 7 * 60;
    private static final int TRAVEL_PORTAL_DURATION_SECONDS = 60;
    private static final int MONARCH_STORAGE_LIMIT = 15;
    private static final int FLORIST_HEAL_DURATION_SECONDS = 10;
    private static final int FLORIST_VINE_DAMAGE = 2;
    private static final int FLORIST_VINE_RANGE = 18;
    private static final double FLORIST_GROWTH_BONUS_CHANCE = 0.20;
    private static final int FLORIST_CROUCH_GROWTH_RADIUS = 2;
    private static final int FLORIST_CROUCH_GROWTH_STAGES = 2;
    private static final int IMMORTALITY_REGEN_SECONDS = 10;
    private static final int IMMORTALITY_RESCUE_SECONDS = 4;
    private static final double IMMORTALITY_SURVIVAL_HEALTH = 1.0;
    private static final double BERSERK_LOW_HEALTH_THRESHOLD = 6.0;
    private static final int ENCHANTER_VIRTUAL_LAPIS_AMOUNT = 64;
    private static final double TANK_CROUCH_KNOCKBACK_RESISTANCE = 1.0;
    private static final long PORTAL_RECENT_TRAVEL_MS = 2500L;
    private static final long FLORIST_CROUCH_GROWTH_COOLDOWN_MS = 150L;
    private static final long FLORIST_STICK_RIGHT_CLICK_COOLDOWN_MS = 1250L;
    private static final long FLORIST_STICK_LEFT_CLICK_COOLDOWN_MS = 650L;

    private static final Set<Material> FLORIST_DOUBLE_CROP_BLOCKS = EnumSet.of(
        Material.WHEAT,
        Material.CARROTS,
        Material.POTATOES,
        Material.BEETROOTS,
        Material.NETHER_WART,
        Material.COCOA,
        Material.SWEET_BERRY_BUSH,
        Material.MELON,
        Material.PUMPKIN
    );

    private static final Set<EntityType> MONARCH_BLOCKED_TYPES = EnumSet.of(
        EntityType.ENDER_DRAGON,
        EntityType.WITHER,
        EntityType.WARDEN,
        EntityType.PLAYER,
        EntityType.VILLAGER,
        EntityType.WANDERING_TRADER,
        EntityType.IRON_GOLEM,
        EntityType.SNOW_GOLEM
    );

    private static final Set<EntityType> UNDEAD_PASSIVE_TYPES = EnumSet.of(
        EntityType.ZOMBIE,
        EntityType.ZOMBIE_VILLAGER,
        EntityType.HUSK,
        EntityType.DROWNED,
        EntityType.ZOMBIFIED_PIGLIN,
        EntityType.SKELETON,
        EntityType.STRAY,
        EntityType.BOGGED,
        EntityType.WITHER_SKELETON,
        EntityType.WITHER,
        EntityType.PHANTOM,
        EntityType.ZOMBIE_HORSE,
        EntityType.SKELETON_HORSE
    );

    private final SMPCore plugin;
    private final NamespacedKey keyPowerType;
    private final NamespacedKey keyVisitedOverworld;
    private final NamespacedKey keyVisitedNether;
    private final NamespacedKey keyVisitedEnd;
    private final NamespacedKey keyMonarchStorage;
    private final NamespacedKey keyPowerRerolls;
    private final NamespacedKey keyAncientScroll;
    private final NamespacedKey keyWardenHeart;
    private final NamespacedKey keyMotherNatureStick;
    private final NamespacedKey keyEnchanterLapis;
    private final NamespacedKey keyTankImmovableModifier;
    private final NamespacedKey keyShadowCooldownUntil;
    private final NamespacedKey keyShadowActiveUntil;
    private final NamespacedKey keyMonarchSummonOwner;
    private final NamespacedKey keyMonarchSummonTag;
    private final Map<UUID, Integer> pendingFloristStickReturns = new ConcurrentHashMap<>();
    private final Map<UUID, PortalPair> activeTravelerPortals = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentPortalTravel = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> monarchSummonsByOwner = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> monarchOwnerByMob = new ConcurrentHashMap<>();
    private final Map<UUID, Long> floristCrouchGrowthCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> floristLeftClickCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> floristRightClickCooldowns = new ConcurrentHashMap<>();
    private BukkitTask passiveTask;
    private BukkitTask portalTask;

    public SuperpowerManager(SMPCore plugin) {
        this.plugin = plugin;
        this.keyPowerType = new NamespacedKey(plugin, "superpower_type");
        this.keyVisitedOverworld = new NamespacedKey(plugin, "superpower_visited_overworld");
        this.keyVisitedNether = new NamespacedKey(plugin, "superpower_visited_nether");
        this.keyVisitedEnd = new NamespacedKey(plugin, "superpower_visited_end");
        this.keyMonarchStorage = new NamespacedKey(plugin, "superpower_monarch_storage");
        this.keyPowerRerolls = new NamespacedKey(plugin, "superpower_rerolls");
        this.keyAncientScroll = new NamespacedKey(plugin, ANCIENT_SCROLL_ITEM_ID);
        this.keyWardenHeart = new NamespacedKey(plugin, WARDEN_HEART_ITEM_ID);
        this.keyMotherNatureStick = new NamespacedKey(plugin, MOTHER_NATURE_STICK_ITEM_ID);
        this.keyEnchanterLapis = new NamespacedKey(plugin, "superpower_enchanter_lapis");
        this.keyTankImmovableModifier = new NamespacedKey(plugin, "superpower_tank_immovable");
        this.keyShadowCooldownUntil = new NamespacedKey(plugin, "superpower_shadow_cooldown_until");
        this.keyShadowActiveUntil = new NamespacedKey(plugin, "superpower_shadow_active_until");
        this.keyMonarchSummonOwner = new NamespacedKey(plugin, "superpower_monarch_owner");
        this.keyMonarchSummonTag = new NamespacedKey(plugin, "superpower_monarch_summon");
    }

    public void start() {
        passiveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPlayers, 20L, 20L);
        portalTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPortals, 5L, 5L);
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::initializePlayerState));
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isShadowActive(player)) {
                restoreShadowAppearance(player);
            }
            syncTankImmovableState(player, false);
            clearVirtualEnchanterLapis(player);
        }
        if (passiveTask != null) {
            passiveTask.cancel();
            passiveTask = null;
        }
        if (portalTask != null) {
            portalTask.cancel();
            portalTask = null;
        }
        for (UUID ownerId : new HashSet<>(activeTravelerPortals.keySet())) {
            closeTravelerPortal(ownerId, false);
        }
        for (UUID ownerId : new HashSet<>(monarchSummonsByOwner.keySet())) {
            despawnMonarchSummons(ownerId);
        }
        pendingFloristStickReturns.clear();
        recentPortalTravel.clear();
        floristCrouchGrowthCooldowns.clear();
        floristLeftClickCooldowns.clear();
        floristRightClickCooldowns.clear();
    }

    public ItemStack createAncientScrollItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        applyAncientScrollState(meta);
        applyAncientScrollPresentation(meta);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createWardenHeartItem() {
        ItemStack item = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        applyWardenHeartState(meta);
        applyWardenHeartPresentation(meta);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createMotherNatureStickItem() {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        applyMotherNatureStickState(meta);
        applyMotherNatureStickPresentation(meta);
        item.setItemMeta(meta);
        return item;
    }

    public List<ItemStack> ancientScrollRecipeDisplayItems() {
        return List.of(
            new ItemStack(Material.TOTEM_OF_UNDYING),
            new ItemStack(Material.NETHER_STAR, 2),
            createWardenHeartItem()
        );
    }

    public boolean isAncientScroll(ItemStack item) {
        return hasTaggedItem(item, keyAncientScroll, ANCIENT_SCROLL_ITEM_ID);
    }

    public boolean isWardenHeart(ItemStack item) {
        return hasTaggedItem(item, keyWardenHeart, WARDEN_HEART_ITEM_ID);
    }

    public boolean isMotherNatureStick(ItemStack item) {
        return hasTaggedItem(item, keyMotherNatureStick, MOTHER_NATURE_STICK_ITEM_ID);
    }

    public void openAdminInfoMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(
            new PowerInfoHolder(),
            MENU_SIZE,
            BedrockCompat.menuTitle(player, POWERS_MENU_TITLE, "Superpower Info")
        );
        fillInfoMenu(inventory);
        player.openInventory(inventory);
    }

    public SuperpowerType powerOf(Player player) {
        return player == null ? null : powerOf(player.getPersistentDataContainer());
    }

    public boolean hasPower(Player player, SuperpowerType type) {
        return type != null && type == powerOf(player);
    }

    public boolean assignPower(Player player, SuperpowerType power, boolean notifyPlayer) {
        if (player == null || power == null) {
            return false;
        }

        assignPower(player, power, false, false);
        if (notifyPlayer) {
            player.sendMessage(MessageUtil.info("Your fate has shifted."));
        }
        return true;
    }

    public boolean handleShadowCommand(Player player) {
        if (!hasPower(player, SuperpowerType.SHADOW)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }

        long now = System.currentTimeMillis();
        if (isShadowActive(player)) {
            deactivateShadow(player, false, true);
            player.sendMessage(MessageUtil.info("The shadows release you."));
            return true;
        }

        long cooldownUntil = shadowCooldownUntil(player);
        if (cooldownUntil > now) {
            player.sendMessage(MessageUtil.warn(
                "Shadow cooldown: <white>" + secondsLeft(cooldownUntil, now) + "s</white>."
            ));
            return false;
        }

        setShadowActiveUntil(player, now + (SHADOW_DURATION_SECONDS * 1000L));
        applyShadowEffects(player);
        player.sendMessage(MessageUtil.success("You fade into the shadows."));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_AMBIENT, 0.8f, 1.2f);
        return true;
    }

    public boolean handleMonarchSummonCommand(Player player, int amount) {
        if (!hasPower(player, SuperpowerType.MONARCH)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }

        List<EntityType> stored = monarchStorage(player);
        if (stored.isEmpty()) {
            player.sendMessage(MessageUtil.warn("You have no stored mobs to summon."));
            return false;
        }

        int summonCount = Math.max(1, Math.min(amount, stored.size()));
        List<EntityType> available = new ArrayList<>(stored);
        List<EntityType> summonedTypes = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int summoned = 0;
        for (int i = 0; i < summonCount && !available.isEmpty(); i++) {
            EntityType type = available.remove(random.nextInt(available.size()));
            Location spawnLocation = findSummonLocation(player.getLocation());
            if (spawnLocation == null) {
                break;
            }
            Entity entity = player.getWorld().spawnEntity(spawnLocation, type);
            if (!(entity instanceof Mob mob)) {
                entity.remove();
                continue;
            }
            markMonarchSummon(mob, player.getUniqueId());
            mob.setTarget(null);
            summonedTypes.add(type);
            summoned++;
        }

        if (summoned <= 0) {
            player.sendMessage(MessageUtil.error("No stored mobs could be summoned here."));
            return false;
        }

        for (EntityType type : summonedTypes) {
            stored.remove(type);
        }
        saveMonarchStorage(player, stored);
        player.sendMessage(MessageUtil.success("Summoned <white>" + summoned + "</white> stored mob(s)."));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 0.85f, 0.9f);
        return true;
    }

    public boolean handleTravelCommand(Player player, int x, int y, int z, String dimensionRaw) {
        if (!hasPower(player, SuperpowerType.TRAVELER)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }

        World.Environment environment = parseEnvironment(dimensionRaw);
        if (environment == null) {
            player.sendMessage(MessageUtil.error("Use <white>overworld</white>, <white>nether</white>, or <white>end</white>."));
            return false;
        }
        if (!hasVisitedEnvironment(player, environment)) {
            player.sendMessage(MessageUtil.warn("That dimension has not opened itself to you yet."));
            return false;
        }

        World targetWorld = primaryWorld(environment);
        if (targetWorld == null) {
            player.sendMessage(MessageUtil.error("That dimension is not available right now."));
            return false;
        }

        Location source = centeredPortalLocation(player.getLocation());
        Location requestedTarget = new Location(targetWorld, x + 0.5, y, z + 0.5, player.getLocation().getYaw(), player.getLocation().getPitch());
        Location target = findSafeTravelLocation(requestedTarget);
        if (target == null) {
            player.sendMessage(MessageUtil.error("No safe portal anchor could be created at those coordinates."));
            return false;
        }

        closeTravelerPortal(player.getUniqueId(), false);
        PortalPair pair = new PortalPair(
            player.getUniqueId(),
            source,
            centeredPortalLocation(target),
            System.currentTimeMillis() + (TRAVEL_PORTAL_DURATION_SECONDS * 1000L)
        );
        activeTravelerPortals.put(player.getUniqueId(), pair);

        player.sendMessage(MessageUtil.success(
            "A travel portal now links <white>" + shortWorldName(source.getWorld()) + "</white> and <white>"
                + shortWorldName(target.getWorld()) + "</white>."
        ));
        player.sendMessage(MessageUtil.info(
            "The portal will fade after <white>" + TRAVEL_PORTAL_DURATION_SECONDS + "s</white>."
        ));
        if (source.getWorld() != null) {
            source.getWorld().playSound(source, Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 1.05f);
        }
        if (target.getWorld() != null) {
            target.getWorld().playSound(target, Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 1.15f);
        }
        return true;
    }

    public boolean handleTravelCloseCommand(Player player) {
        if (!hasPower(player, SuperpowerType.TRAVELER)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }

        UUID playerId = player.getUniqueId();
        if (!activeTravelerPortals.containsKey(playerId)) {
            player.sendMessage(MessageUtil.warn("You do not have an active travel portal."));
            return false;
        }

        closeTravelerPortal(playerId, false);
        player.sendMessage(MessageUtil.info("You collapse your travel portal."));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 0.8f, 0.7f);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        initializePlayerState(player);
        refreshVisibleShadowsFor(player);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        markVisitedDimension(event.getPlayer(), event.getPlayer().getWorld());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (hasPower(player, SuperpowerType.FLORIST)) {
            int kept = 0;
            List<ItemStack> drops = event.getDrops();
            for (int i = drops.size() - 1; i >= 0; i--) {
                ItemStack drop = drops.get(i);
                if (!isMotherNatureStick(drop)) {
                    continue;
                }
                kept += Math.max(1, drop.getAmount());
                drops.remove(i);
            }
            if (kept > 0) {
                pendingFloristStickReturns.merge(player.getUniqueId(), kept, Integer::sum);
            }
        }
        if (isShadowActive(player)) {
            deactivateShadow(player, false, false);
        }
        syncTankImmovableState(player, false);
        clearVirtualEnchanterLapis(player);
        closeTravelerPortal(player.getUniqueId(), false);
        despawnMonarchSummons(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Integer pendingValue = pendingFloristStickReturns.remove(player.getUniqueId());
        int pending = pendingValue == null ? 0 : pendingValue;
        if (pending <= 0) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            for (int i = 0; i < pending; i++) {
                giveMotherNatureStick(player, true);
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        syncTankImmovableState(player, false);
        clearVirtualEnchanterLapis(player);
        closeTravelerPortal(playerId, false);
        despawnMonarchSummons(playerId);
        recentPortalTravel.remove(playerId);
        floristCrouchGrowthCooldowns.remove(playerId);
        floristLeftClickCooldowns.remove(playerId);
        floristRightClickCooldowns.remove(playerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExpGain(PlayerExpChangeEvent event) {
        if (!hasPower(event.getPlayer(), SuperpowerType.ENCHANTER)) {
            return;
        }
        int amount = event.getAmount();
        if (amount > 0) {
            event.setAmount(amount * 5);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareItemEnchant(PrepareItemEnchantEvent event) {
        if (!hasPower(event.getEnchanter(), SuperpowerType.ENCHANTER)) {
            return;
        }
        if (event.getView().getTopInventory() instanceof EnchantingInventory enchanting) {
            refreshEnchanterLapis(event.getEnchanter(), enchanting);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        if (!hasPower(event.getEnchanter(), SuperpowerType.ENCHANTER)) {
            return;
        }
        if (!(event.getView().getTopInventory() instanceof EnchantingInventory enchanting)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> refreshEnchanterLapis(event.getEnchanter(), enchanting));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnchanterInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory() instanceof EnchantingInventory enchanting)) {
            return;
        }

        if (!hasPower(player, SuperpowerType.ENCHANTER)) {
            clearVirtualEnchanterLapis(enchanting);
            return;
        }

        if (event.getClickedInventory() == event.getView().getTopInventory()
            && event.getSlot() == 1
            && isVirtualEnchanterLapis(enchanting.getSecondary())) {
            event.setCancelled(true);
        }

        Bukkit.getScheduler().runTask(plugin, () -> refreshEnchanterLapis(player, enchanting));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnchanterInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory() instanceof EnchantingInventory enchanting)) {
            return;
        }

        if (!hasPower(player, SuperpowerType.ENCHANTER)) {
            clearVirtualEnchanterLapis(enchanting);
            return;
        }

        if (event.getRawSlots().contains(1) && isVirtualEnchanterLapis(enchanting.getSecondary())) {
            event.setCancelled(true);
        }

        Bukkit.getScheduler().runTask(plugin, () -> refreshEnchanterLapis(player, enchanting));
    }

    @EventHandler
    public void onEnchanterInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory() instanceof EnchantingInventory enchanting) {
            clearVirtualEnchanterLapis(enchanting);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (hasPower(player, SuperpowerType.IMMORTALITY)) {
            double finalDamage = event.getFinalDamage();
            if (finalDamage > 0.0 && player.getHealth() - finalDamage <= 0.0) {
                event.setCancelled(true);
                rescueImmortalPlayer(player, event.getCause());
                return;
            }
        }

        if (isShadowActive(player) && event.getFinalDamage() > 0.0) {
            deactivateShadow(player, true, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker) {
            if (hasPower(attacker, SuperpowerType.MONARCH) && event.getEntity() instanceof LivingEntity victim) {
                directMonarchSummons(attacker, victim);
            }
            return;
        }

        Player summonOwner = monarchOwnerOf(event.getDamager());
        if (summonOwner != null && event.getEntity() instanceof LivingEntity victim) {
            directMonarchSummons(summonOwner, victim);
        }

        if (event.getEntity() instanceof Player victim && hasPower(victim, SuperpowerType.MONARCH)) {
            LivingEntity attacker = actualLivingDamager(event.getDamager());
            if (attacker != null) {
                directMonarchSummons(victim, attacker);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();

        if (entity.getType() == EntityType.WARDEN && killer != null) {
            event.getDrops().add(createWardenHeartItem());
        }

        if (killer != null && hasPower(killer, SuperpowerType.MONARCH) && isMonarchStorable(entity)) {
            storeMonarchMob(killer, entity.getType());
        }

        UUID mobId = entity.getUniqueId();
        UUID ownerId = monarchOwnerByMob.remove(mobId);
        if (ownerId == null) {
            return;
        }
        Set<UUID> summons = monarchSummonsByOwner.get(ownerId);
        if (summons != null) {
            summons.remove(mobId);
            if (summons.isEmpty()) {
                monarchSummonsByOwner.remove(ownerId);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        UUID ownerId = monarchOwnerByMob.get(mob.getUniqueId());
        if (ownerId == null) {
            if (event.getTarget() instanceof Player target
                && hasPower(target, SuperpowerType.MONARCH)
                && isUndeadPassiveType(mob.getType())) {
                event.setCancelled(true);
                mob.setTarget(null);
            }
            return;
        }
        if (event.getTarget() instanceof Player teammate && sameTeamOrSelf(ownerId, teammate.getUniqueId())) {
            event.setCancelled(true);
            mob.setTarget(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        if (!(event.getNewState().getBlockData() instanceof Ageable ageable)) {
            return;
        }
        if (!isNaturalCrop(event.getBlock().getType()) || !hasNearbyFlorist(event.getBlock().getLocation())) {
            return;
        }
        if (ageable.getAge() >= ageable.getMaximumAge()) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= FLORIST_GROWTH_BONUS_CHANCE) {
            return;
        }
        ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + 1));
        event.getNewState().setBlockData(ageable);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (hasPower(player, SuperpowerType.TANK)) {
            syncTankImmovableState(player, event.isSneaking());
        }
        if (!hasPower(player, SuperpowerType.FLORIST)) {
            return;
        }
        pulseFloristGrowth(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFloristDrops(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.getGameMode() == GameMode.CREATIVE || !hasPower(player, SuperpowerType.FLORIST)) {
            return;
        }
        Material blockType = event.getBlockState().getType();
        if (!isFloristDoubleDropBlock(blockType)) {
            return;
        }

        List<ItemStack> extraDrops = new ArrayList<>();
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
                continue;
            }
            extraDrops.add(stack.clone());
        }
        if (extraDrops.isEmpty()) {
            return;
        }

        CustomEnchantListener enchants = plugin.getCustomEnchantListener();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (enchants != null && enchants.hasTelekinesisEnchant(tool)) {
            enchants.deliverTelekinesisDrops(player, extraDrops, event.getBlock().getLocation());
            return;
        }

        Location dropLocation = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack stack : extraDrops) {
            Item dropped = event.getBlock().getWorld().dropItemNaturally(dropLocation, stack);
            dropped.setPickupDelay(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPowerInteract(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
            && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (isAncientScroll(item) && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            useAncientScroll(player);
            return;
        }
        if (!isMotherNatureStick(item) || !hasPower(player, SuperpowerType.FLORIST)) {
            return;
        }

        event.setCancelled(true);
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            useMotherNatureStickAttack(player);
            return;
        }
        useMotherNatureStickHeal(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFloristStickSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        if (!hasPower(player, SuperpowerType.FLORIST)) {
            return;
        }
        if (!isMotherNatureStick(player.getInventory().getItemInMainHand())) {
            return;
        }
        useMotherNatureStickAttack(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onShadowInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isShadowActive(player)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && isShadowActive(player)) {
                hideShadowEquipment(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onShadowInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isShadowActive(player)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && isShadowActive(player)) {
                hideShadowEquipment(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onShadowHeldItemChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!isShadowActive(player)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && isShadowActive(player)) {
                hideShadowEquipment(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDropMotherNatureStick(PlayerDropItemEvent event) {
        if (!isMotherNatureStick(event.getItemDrop().getItemStack())) {
            return;
        }
        if (!hasPower(event.getPlayer(), SuperpowerType.FLORIST)) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(MessageUtil.warn("The Stick from Mother Nature refuses to leave you."));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        if (matchesAncientScrollRecipe(inventory.getMatrix())) {
            inventory.setResult(createAncientScrollItem());
            return;
        }

        ItemStack result = inventory.getResult();
        if (isAncientScroll(result)) {
            inventory.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory() instanceof CraftingInventory inventory)) {
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (event.getSlotType() != InventoryType.SlotType.RESULT) {
            return;
        }

        ItemStack current = event.getCurrentItem();
        if (!isAncientScroll(current) && !matchesAncientScrollRecipe(inventory.getMatrix())) {
            return;
        }

        event.setCancelled(true);
        if (!giveCraftResult(player, event, createAncientScrollItem())) {
            return;
        }
        if (!consumeAncientScrollIngredients(inventory)) {
            player.sendMessage(MessageUtil.error("The Ancient Scroll recipe ingredients were invalid."));
            return;
        }

        inventory.setResult(null);
        player.updateInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getFirstItem();
        ItemStack right = event.getInventory().getSecondItem();
        if (!isMotherNatureStick(left) && !isMotherNatureStick(right)) {
            return;
        }

        ItemStack source = isMotherNatureStick(left) ? left : right;
        ItemStack result = event.getResult();
        if (source == null || result == null || result.getType() == Material.AIR) {
            return;
        }
        event.setResult(preserveMotherNatureStick(result));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (!(event.getInventory() instanceof GrindstoneInventory grindstone)) {
            return;
        }
        ItemStack top = grindstone.getUpperItem();
        ItemStack bottom = grindstone.getLowerItem();
        if (!isMotherNatureStick(top) && !isMotherNatureStick(bottom)) {
            return;
        }

        ItemStack source = isMotherNatureStick(top) ? top : bottom;
        ItemStack result = event.getResult();
        if (source == null || result == null || result.getType() == Material.AIR) {
            return;
        }
        event.setResult(preserveMotherNatureStick(result));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPowerMenuClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PowerInfoHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPowerMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PowerInfoHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPowerMenuClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PowerInfoHolder) {
            event.getView().getTopInventory().clear();
        }
    }

    private void initializePlayerState(Player player) {
        markVisitedDimension(player, player.getWorld());
        SuperpowerType power = ensurePowerAssigned(player);
        if (power == SuperpowerType.FLORIST && !hasMotherNatureStick(player)) {
            giveMotherNatureStick(player, false);
        }
        if (power == SuperpowerType.SHADOW && isShadowActive(player)) {
            applyShadowEffects(player);
        }
        syncTankImmovableState(player, power == SuperpowerType.TANK && player.isSneaking());
        refreshPowerItems(player);
        applyPassiveEffects(player);
    }

    private void tickPlayers() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline()) {
                continue;
            }
            ensurePowerAssigned(player);
            if (isShadowActive(player) && shadowActiveUntil(player) <= now) {
                deactivateShadow(player, false, false);
            }
            applyPassiveEffects(player);
        }
        cleanupRecentPortalTravel(now);
    }

    private void applyPassiveEffects(Player player) {
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            syncTankImmovableState(player, false);
            return;
        }

        SuperpowerType power = powerOf(player);
        if (power == null) {
            return;
        }

        switch (power) {
            case FLASH -> {
                applyPotion(player, PotionEffectType.SPEED, 80, 2);
                applyPotion(player, PotionEffectType.HASTE, 80, 2);
            }
            case ENCHANTER -> {
                if (player.getOpenInventory().getTopInventory() instanceof EnchantingInventory enchanting) {
                    refreshEnchanterLapis(player, enchanting);
                }
            }
            case BERSERK -> {
                applyPotion(player, PotionEffectType.STRENGTH, 80, 1);
                if (player.getHealth() <= BERSERK_LOW_HEALTH_THRESHOLD) {
                    applyPotion(player, PotionEffectType.SPEED, 60, 1);
                    applyPotion(player, PotionEffectType.REGENERATION, 60, 0);
                }
            }
            case TANK -> {
                applyPotion(player, PotionEffectType.HEALTH_BOOST, 80, 4);
                syncTankImmovableState(player, player.isSneaking());
                var maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (maxHealth != null && player.getHealth() > maxHealth.getValue()) {
                    player.setHealth(maxHealth.getValue());
                }
            }
            case TRAVELER -> applyPotion(player, PotionEffectType.SPEED, 80, 1);
            case FLORIST -> {
                if (player.getLocation().getBlock().getLightFromSky() > 0) {
                    applyPotion(player, PotionEffectType.REGENERATION, 60, 0);
                }
                if (!hasMotherNatureStick(player)) {
                    giveMotherNatureStick(player, false);
                }
            }
            case MONARCH -> pacifyNearbyUndead(player);
            case SHADOW -> {
                if (isShadowActive(player)) {
                    applyShadowEffects(player);
                }
            }
            default -> {
            }
        }
    }

    private void applyPotion(Player player, PotionEffectType type, int durationTicks, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);
        if (current != null && current.getAmplifier() > amplifier && current.getDuration() > 20) {
            return;
        }
        if (current != null && current.getAmplifier() == amplifier && current.getDuration() > (durationTicks / 2)) {
            return;
        }
        player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, true, false, false));
    }

    private SuperpowerType ensurePowerAssigned(Player player) {
        SuperpowerType existing = powerOf(player);
        if (existing != null) {
            return existing;
        }
        SuperpowerType assigned = randomPower(false);
        assignPower(player, assigned, false, false);
        return assigned;
    }

    private void assignPower(Player player, SuperpowerType power, boolean rerolled, boolean notifyScrollResult) {
        prepareForPowerAssignment(player, power);

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(keyPowerType, PersistentDataType.STRING, power.name());
        if (rerolled) {
            int rerolls = pdc.getOrDefault(keyPowerRerolls, PersistentDataType.INTEGER, 0);
            pdc.set(keyPowerRerolls, PersistentDataType.INTEGER, rerolls + 1);
        }
        if (power == SuperpowerType.FLORIST && !hasMotherNatureStick(player)) {
            giveMotherNatureStick(player, false);
        }
        applyPassiveEffects(player);
        player.updateInventory();
        if (notifyScrollResult || power.hasCommandHint()) {
            sendPowerHint(player, power);
        }
    }

    private void prepareForPowerAssignment(Player player, SuperpowerType nextPower) {
        UUID playerId = player.getUniqueId();
        pendingFloristStickReturns.remove(playerId);
        closeTravelerPortal(playerId, false);
        despawnMonarchSummons(playerId);

        if (isShadowActive(player)) {
            removeLikelyPowerPotion(player, PotionEffectType.INVISIBILITY, 0);
            removeLikelyPowerPotion(player, PotionEffectType.SPEED, 2);
            restoreShadowAppearance(player);
        }

        clearLikelyPassivePowerEffects(player);
        clearPowerCooldownState(player);

        if (nextPower != SuperpowerType.FLORIST) {
            removeMotherNatureSticks(player);
        }
        syncTankImmovableState(player, false);
        clearVirtualEnchanterLapis(player);
    }

    private void clearPowerCooldownState(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.remove(keyShadowCooldownUntil);
        pdc.remove(keyShadowActiveUntil);
    }

    private void clearLikelyPassivePowerEffects(Player player) {
        removeLikelyPowerPotion(player, PotionEffectType.SPEED, 1);
        removeLikelyPowerPotion(player, PotionEffectType.SPEED, 2);
        removeLikelyPowerPotion(player, PotionEffectType.HASTE, 2);
        removeLikelyPowerPotion(player, PotionEffectType.STRENGTH, 1);
        removeLikelyPowerPotion(player, PotionEffectType.HEALTH_BOOST, 4);
        removeLikelyPowerPotion(player, PotionEffectType.REGENERATION, 0);

        var maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (maxHealth != null && player.getHealth() > maxHealth.getValue()) {
            player.setHealth(maxHealth.getValue());
        }
    }

    private void sendPowerHint(Player player, SuperpowerType power) {
        if (power.hasCommandHint()) {
            player.sendMessage(MessageUtil.info("<gray>" + power.commandHint() + "</gray>"));
        }
    }

    private SuperpowerType powerOf(PersistentDataContainer pdc) {
        String raw = pdc.get(keyPowerType, PersistentDataType.STRING);
        SuperpowerType parsed = SuperpowerType.fromId(raw);
        if (parsed == null && raw != null && !raw.isBlank()) {
            pdc.remove(keyPowerType);
        }
        return parsed;
    }

    private SuperpowerType randomPower(boolean excludeHuman) {
        double total = 0.0;
        for (SuperpowerType type : SuperpowerType.values()) {
            if (excludeHuman && type == SuperpowerType.HUMAN) {
                continue;
            }
            total += type.chance();
        }

        double roll = ThreadLocalRandom.current().nextDouble() * total;
        double cursor = 0.0;
        for (SuperpowerType type : SuperpowerType.values()) {
            if (excludeHuman && type == SuperpowerType.HUMAN) {
                continue;
            }
            cursor += type.chance();
            if (roll <= cursor) {
                return type;
            }
        }
        return excludeHuman ? SuperpowerType.FLASH : SuperpowerType.HUMAN;
    }

    private void markVisitedDimension(Player player, World world) {
        if (player == null || world == null) {
            return;
        }
        NamespacedKey key = switch (world.getEnvironment()) {
            case NETHER -> keyVisitedNether;
            case THE_END -> keyVisitedEnd;
            default -> keyVisitedOverworld;
        };
        player.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    }

    private boolean hasVisitedEnvironment(Player player, World.Environment environment) {
        if (player == null || environment == null) {
            return false;
        }
        NamespacedKey key = switch (environment) {
            case NETHER -> keyVisitedNether;
            case THE_END -> keyVisitedEnd;
            default -> keyVisitedOverworld;
        };
        Byte value = player.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private World primaryWorld(World.Environment environment) {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == environment) {
                return world;
            }
        }
        return null;
    }

    private World.Environment parseEnvironment(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "overworld", "world" -> World.Environment.NORMAL;
            case "nether" -> World.Environment.NETHER;
            case "end", "the_end" -> World.Environment.THE_END;
            default -> null;
        };
    }

    private String shortWorldName(World world) {
        if (world == null) {
            return "Unknown";
        }
        return switch (world.getEnvironment()) {
            case NETHER -> "Nether";
            case THE_END -> "End";
            default -> "Overworld";
        };
    }

    private void rescueImmortalPlayer(Player player, EntityDamageEvent.DamageCause cause) {
        var maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double healthCap = maxHealth == null ? 20.0 : maxHealth.getValue();
        player.setHealth(Math.min(healthCap, IMMORTALITY_SURVIVAL_HEALTH));
        player.setFireTicks(0);
        applyPotion(player, PotionEffectType.REGENERATION, IMMORTALITY_REGEN_SECONDS * 20, 5);
        applyPotion(player, PotionEffectType.RESISTANCE, IMMORTALITY_RESCUE_SECONDS * 20, 3);
        applyPotion(player, PotionEffectType.FIRE_RESISTANCE, IMMORTALITY_REGEN_SECONDS * 20, 0);

        if (cause == EntityDamageEvent.DamageCause.VOID) {
            Location rescue = safeRespawnLikeLocation(player);
            if (rescue != null) {
                player.teleportAsync(rescue);
            }
        }

        World world = player.getWorld();
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0.0, 1.0, 0.0), 28, 0.45, 0.6, 0.45, 0.03);
        world.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 0.85f);
    }

    private Location safeRespawnLikeLocation(Player player) {
        Location bed = player.getRespawnLocation();
        if (bed != null) {
            Location safe = findSafeTravelLocation(bed);
            if (safe != null) {
                return safe;
            }
        }
        return findSafeTravelLocation(player.getWorld().getSpawnLocation());
    }

    private long shadowCooldownUntil(Player player) {
        return player.getPersistentDataContainer().getOrDefault(keyShadowCooldownUntil, PersistentDataType.LONG, 0L);
    }

    private long shadowActiveUntil(Player player) {
        return player.getPersistentDataContainer().getOrDefault(keyShadowActiveUntil, PersistentDataType.LONG, 0L);
    }

    private void setShadowCooldownUntil(Player player, long value) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (value <= 0L) {
            pdc.remove(keyShadowCooldownUntil);
            return;
        }
        pdc.set(keyShadowCooldownUntil, PersistentDataType.LONG, value);
    }

    private void setShadowActiveUntil(Player player, long value) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (value <= 0L) {
            pdc.remove(keyShadowActiveUntil);
            return;
        }
        pdc.set(keyShadowActiveUntil, PersistentDataType.LONG, value);
    }

    private boolean isShadowActive(Player player) {
        return shadowActiveUntil(player) > System.currentTimeMillis();
    }

    private void applyShadowEffects(Player player) {
        applyPotion(player, PotionEffectType.INVISIBILITY, 60, 0);
        applyPotion(player, PotionEffectType.SPEED, 60, 2);
        hideShadowEquipment(player);
    }

    private void deactivateShadow(Player player, boolean hit, boolean manual) {
        setShadowActiveUntil(player, 0L);
        long cooldownSeconds = hit ? SHADOW_HIT_COOLDOWN_SECONDS : SHADOW_COOLDOWN_SECONDS;
        setShadowCooldownUntil(player, System.currentTimeMillis() + (cooldownSeconds * 1000L));
        removeLikelyPowerPotion(player, PotionEffectType.INVISIBILITY, 0);
        removeLikelyPowerPotion(player, PotionEffectType.SPEED, 2);
        restoreShadowAppearance(player);
        if (hit) {
            player.sendMessage(MessageUtil.warn("You were struck and ripped out of the shadows."));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_HURT, 1.0f, 0.8f);
        } else if (manual) {
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.9f, 1.1f);
        }
    }

    private void hideShadowEquipment(Player player) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            sendShadowEquipmentState(viewer, player, true);
        }
    }

    private void restoreShadowAppearance(Player player) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            sendShadowEquipmentState(viewer, player, false);
        }
    }

    private void refreshVisibleShadowsFor(Player viewer) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(viewer) || !isShadowActive(player)) {
                continue;
            }
            sendShadowEquipmentState(viewer, player, true);
        }
    }

    private void sendShadowEquipmentState(Player viewer, Player shadowPlayer, boolean hidden) {
        if (viewer == null || shadowPlayer == null || viewer.equals(shadowPlayer) || !viewer.isOnline() || !shadowPlayer.isOnline()) {
            return;
        }
        if (!viewer.canSee(shadowPlayer)) {
            return;
        }

        PlayerInventory inventory = shadowPlayer.getInventory();
        viewer.sendEquipmentChange(shadowPlayer, EquipmentSlot.HAND, hidden ? new ItemStack(Material.AIR) : visibleEquipment(inventory.getItemInMainHand()));
        viewer.sendEquipmentChange(shadowPlayer, EquipmentSlot.OFF_HAND, hidden ? new ItemStack(Material.AIR) : visibleEquipment(inventory.getItemInOffHand()));
        viewer.sendEquipmentChange(shadowPlayer, EquipmentSlot.FEET, hidden ? new ItemStack(Material.AIR) : visibleEquipment(inventory.getBoots()));
        viewer.sendEquipmentChange(shadowPlayer, EquipmentSlot.LEGS, hidden ? new ItemStack(Material.AIR) : visibleEquipment(inventory.getLeggings()));
        viewer.sendEquipmentChange(shadowPlayer, EquipmentSlot.CHEST, hidden ? new ItemStack(Material.AIR) : visibleEquipment(inventory.getChestplate()));
        viewer.sendEquipmentChange(shadowPlayer, EquipmentSlot.HEAD, hidden ? new ItemStack(Material.AIR) : visibleEquipment(inventory.getHelmet()));
    }

    private ItemStack visibleEquipment(ItemStack item) {
        return item == null || item.getType() == Material.AIR ? new ItemStack(Material.AIR) : item.clone();
    }

    private void removeLikelyPowerPotion(Player player, PotionEffectType type, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);
        if (current != null && current.getAmplifier() == amplifier && current.getDuration() <= 80) {
            player.removePotionEffect(type);
        }
    }

    private int secondsLeft(long future, long now) {
        return Math.max(1, (int) Math.ceil((future - now) / 1000.0));
    }

    private void useAncientScroll(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean awakened = isAncientScroll(held) && isAwakenedAncientScroll(held);
        SuperpowerType currentPower = powerOf(player);

        if (!awakened && currentPower != SuperpowerType.HUMAN) {
            player.sendMessage(MessageUtil.warn("The Ancient Scroll can only awaken a dormant soul."));
            return;
        }

        consumeHeldItem(player);
        SuperpowerType rerolled = awakened
            ? randomDifferentPower(currentPower, true)
            : randomPower(true);
        assignPower(player, rerolled, true, true);
        if (awakened) {
            player.sendMessage(MessageUtil.success("The awakened Ancient Scroll twists your power into a new fate."));
        } else {
            player.sendMessage(MessageUtil.success("The Ancient Scroll rewrites your fate."));
        }
    }

    private void useMotherNatureStickAttack(Player player) {
        long now = System.currentTimeMillis();
        long readyAt = floristLeftClickCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) {
            return;
        }
        floristLeftClickCooldowns.put(player.getUniqueId(), now + FLORIST_STICK_LEFT_CLICK_COOLDOWN_MS);

        float baseYaw = player.getLocation().getYaw();
        for (float offset : new float[]{-8.0f, 0.0f, 8.0f}) {
            fireVineBurst(player, baseYaw + offset, player.getLocation().getPitch());
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AZALEA_BREAK, 0.8f, 0.75f);
    }

    private void useMotherNatureStickHeal(Player player) {
        long now = System.currentTimeMillis();
        long readyAt = floristRightClickCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) {
            return;
        }
        floristRightClickCooldowns.put(player.getUniqueId(), now + FLORIST_STICK_RIGHT_CLICK_COOLDOWN_MS);

        LivingEntity target = targetedLivingEntity(player, 10.0, living -> true);
        if (target == null) {
            target = player;
        }

        target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, FLORIST_HEAL_DURATION_SECONDS * 20, 2, true, true, true));
        target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0.0, 1.1, 0.0), 8, 0.25, 0.35, 0.25, 0.01);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_MOSS_PLACE, 0.85f, 1.15f);
        if (!target.equals(player) && target instanceof Player other) {
            other.sendMessage(MessageUtil.success("Nature restores you."));
            player.sendMessage(MessageUtil.success("You healed <white>" + other.getName() + "</white>."));
        } else {
            player.sendMessage(MessageUtil.success("Nature restores you."));
        }
    }

    private void fireVineBurst(Player player, float yaw, float pitch) {
        Location eye = player.getEyeLocation();
        Vector direction = directionFromYawPitch(yaw, pitch);
        RayTraceResult hit = player.getWorld().rayTraceEntities(
            eye,
            direction,
            FLORIST_VINE_RANGE,
            0.45,
            entity -> entity instanceof LivingEntity
                && !entity.equals(player)
                && !sameTeamOrSelf(player.getUniqueId(), entity.getUniqueId())
        );

        double distance = hit == null || hit.getHitPosition() == null
            ? FLORIST_VINE_RANGE
            : hit.getHitPosition().distance(eye.toVector());
        spawnVineTrail(player.getWorld(), eye, direction, distance);

        if (hit != null && hit.getHitEntity() instanceof LivingEntity victim) {
            victim.damage(FLORIST_VINE_DAMAGE, player);
            victim.getWorld().spawnParticle(Particle.ITEM_SLIME, victim.getLocation().add(0.0, 1.0, 0.0), 16, 0.25, 0.35, 0.25, 0.02);
        }
    }

    private LivingEntity targetedLivingEntity(Player player, double range, Predicate<LivingEntity> filter) {
        RayTraceResult hit = player.getWorld().rayTraceEntities(
            player.getEyeLocation(),
            player.getEyeLocation().getDirection(),
            range,
            0.45,
            entity -> entity instanceof LivingEntity living
                && !living.equals(player)
                && filter.test(living)
        );
        return hit != null && hit.getHitEntity() instanceof LivingEntity living ? living : null;
    }

    private void spawnVineTrail(World world, Location start, Vector direction, double distance) {
        double travelled = 0.0;
        while (travelled <= distance) {
            Location point = start.clone().add(direction.clone().multiply(travelled));
            world.spawnParticle(Particle.COMPOSTER, point, 2, 0.04, 0.04, 0.04, 0.0);
            travelled += 0.45;
        }
    }

    private void tickPortals() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, PortalPair> entry : new ArrayList<>(activeTravelerPortals.entrySet())) {
            PortalPair pair = entry.getValue();
            if (pair.expiresAt() <= now) {
                closeTravelerPortal(entry.getKey(), true);
                continue;
            }
            showPortal(pair.source());
            showPortal(pair.target());
            handlePortalTravel(pair.source(), pair.target());
            handlePortalTravel(pair.target(), pair.source());
        }
    }

    private void handlePortalTravel(Location from, Location to) {
        World world = from.getWorld();
        if (world == null || !from.getChunk().isLoaded()) {
            return;
        }
        World targetWorld = to.getWorld();
        Location safe = targetWorld == null ? null : findSafeTravelLocation(to);
        Collection<Entity> nearby = world.getNearbyEntities(from, 1.35, 2.25, 1.35);
        for (Entity entity : nearby) {
            long recentUntil = recentPortalTravel.getOrDefault(entity.getUniqueId(), 0L);
            if (recentUntil > System.currentTimeMillis()) {
                continue;
            }

            if (entity instanceof Player player) {
                if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                if (targetWorld == null || !hasVisitedEnvironment(player, targetWorld.getEnvironment())) {
                    player.sendMessage(MessageUtil.warn("This portal rejects you."));
                    recentPortalTravel.put(player.getUniqueId(), System.currentTimeMillis() + 1000L);
                    continue;
                }
                if (safe == null) {
                    player.sendMessage(MessageUtil.error("The other side of the portal is unstable."));
                    recentPortalTravel.put(player.getUniqueId(), System.currentTimeMillis() + 1000L);
                    continue;
                }

                plugin.getPlayerManager().saveBackLocation(player);
                recentPortalTravel.put(player.getUniqueId(), System.currentTimeMillis() + PORTAL_RECENT_TRAVEL_MS);
                player.teleportAsync(safe.clone());
                player.setFallDistance(0.0f);
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.8f, 1.2f);
                continue;
            }

            if (!(entity instanceof LivingEntity living) || living.isDead() || !living.isValid()) {
                continue;
            }
            if (safe == null) {
                recentPortalTravel.put(living.getUniqueId(), System.currentTimeMillis() + 1000L);
                continue;
            }
            recentPortalTravel.put(living.getUniqueId(), System.currentTimeMillis() + PORTAL_RECENT_TRAVEL_MS);
            living.setFallDistance(0.0f);
            if (living.teleport(safe.clone())) {
                living.setFallDistance(0.0f);
            }
        }
    }

    private void showPortal(Location center) {
        World world = center.getWorld();
        if (world == null || !center.getChunk().isLoaded()) {
            return;
        }
        for (int i = 0; i < 10; i++) {
            double angle = (Math.PI * 2.0 * i) / 10.0;
            double x = Math.cos(angle) * 0.9;
            double z = Math.sin(angle) * 0.9;
            world.spawnParticle(Particle.PORTAL, center.clone().add(x, 0.15, z), 2, 0.0, 0.55, 0.0, 0.02);
            world.spawnParticle(Particle.REVERSE_PORTAL, center.clone().add(x * 0.6, 1.15, z * 0.6), 1, 0.0, 0.2, 0.0, 0.0);
        }
    }

    private void closeTravelerPortal(UUID ownerId, boolean expired) {
        PortalPair removed = activeTravelerPortals.remove(ownerId);
        if (removed == null || !expired) {
            return;
        }
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(MessageUtil.info("Your travel portal faded away."));
        }
    }

    private void cleanupRecentPortalTravel(long now) {
        recentPortalTravel.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private Location centeredPortalLocation(Location location) {
        return location.clone().add(0.5, 0.0, 0.5);
    }

    private Location findSafeTravelLocation(Location target) {
        if (target == null || target.getWorld() == null) {
            return null;
        }
        World world = target.getWorld();
        Chunk chunk = world.getChunkAt(target);
        if (!chunk.isLoaded()) {
            chunk.load();
        }

        int originX = target.getBlockX();
        int originY = target.getBlockY();
        int originZ = target.getBlockZ();
        for (int radius = 0; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = -4; dy <= 4; dy++) {
                        Location candidate = new Location(world, originX + dx + 0.5, originY + dy, originZ + dz + 0.5, target.getYaw(), target.getPitch());
                        if (isSafeStandingLocation(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }

        int highestY = world.getHighestBlockYAt(originX, originZ);
        Location fallback = new Location(world, originX + 0.5, highestY + 1.0, originZ + 0.5, target.getYaw(), target.getPitch());
        return isSafeStandingLocation(fallback) ? fallback : null;
    }

    private boolean isSafeStandingLocation(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block floor = feet.getRelative(BlockFace.DOWN);
        return feet.isPassable()
            && head.isPassable()
            && !floor.isPassable()
            && floor.getType() != Material.LAVA
            && floor.getType() != Material.MAGMA_BLOCK;
    }

    private boolean isNaturalCrop(Material material) {
        return switch (material) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS, NETHER_WART, COCOA, SWEET_BERRY_BUSH -> true;
            default -> false;
        };
    }

    private void pulseFloristGrowth(Player player) {
        if (player == null || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        long now = System.currentTimeMillis();
        long readyAt = floristCrouchGrowthCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) {
            return;
        }

        World world = player.getWorld();
        Location origin = player.getLocation();
        int grown = 0;
        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY();
        int baseZ = origin.getBlockZ();

        for (int dx = -FLORIST_CROUCH_GROWTH_RADIUS; dx <= FLORIST_CROUCH_GROWTH_RADIUS; dx++) {
            for (int dz = -FLORIST_CROUCH_GROWTH_RADIUS; dz <= FLORIST_CROUCH_GROWTH_RADIUS; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Block block = world.getBlockAt(baseX + dx, baseY + dy, baseZ + dz);
                    if (!isNaturalCrop(block.getType()) || !(block.getBlockData() instanceof Ageable ageable)) {
                        continue;
                    }
                    if (ageable.getAge() >= ageable.getMaximumAge()) {
                        continue;
                    }

                    ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + FLORIST_CROUCH_GROWTH_STAGES));
                    block.setBlockData(ageable, false);
                    world.spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.75, 0.5), 5, 0.18, 0.22, 0.18, 0.0);
                    grown++;
                }
            }
        }

        if (grown <= 0) {
            return;
        }

        floristCrouchGrowthCooldowns.put(player.getUniqueId(), now + FLORIST_CROUCH_GROWTH_COOLDOWN_MS);
        world.playSound(origin, Sound.ITEM_BONE_MEAL_USE, 0.65f, 1.15f);
    }

    private boolean hasNearbyFlorist(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        for (Player player : world.getPlayers()) {
            if (!hasPower(player, SuperpowerType.FLORIST) || player.isDead()) {
                continue;
            }
            Location playerLocation = player.getLocation();
            if (Math.abs(playerLocation.getBlockX() - location.getBlockX()) > 2) continue;
            if (Math.abs(playerLocation.getBlockZ() - location.getBlockZ()) > 2) continue;
            if (Math.abs(playerLocation.getBlockY() - location.getBlockY()) > 3) continue;
            return true;
        }
        return false;
    }

    private boolean isFloristDoubleDropBlock(Material material) {
        return FLORIST_DOUBLE_CROP_BLOCKS.contains(material) || isWoodBlock(material);
    }

    private boolean isWoodBlock(Material material) {
        String name = material.name();
        return name.endsWith("_LOG")
            || name.endsWith("_WOOD")
            || name.endsWith("_STEM")
            || name.endsWith("_HYPHAE");
    }

    private void storeMonarchMob(Player player, EntityType type) {
        List<EntityType> stored = monarchStorage(player);
        if (stored.size() >= MONARCH_STORAGE_LIMIT) {
            return;
        }
        stored.add(type);
        saveMonarchStorage(player, stored);
        player.sendMessage(MessageUtil.info(
            "Monarch stored <white>" + prettyEntityType(type) + "</white> (<white>" + stored.size() + "/" + MONARCH_STORAGE_LIMIT + "</white>)."
        ));
    }

    private List<EntityType> monarchStorage(Player player) {
        String raw = player.getPersistentDataContainer().get(keyMonarchStorage, PersistentDataType.STRING);
        List<EntityType> stored = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return stored;
        }
        for (String part : raw.split(",")) {
            if (part == null || part.isBlank()) {
                continue;
            }
            try {
                stored.add(EntityType.valueOf(part));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return stored;
    }

    private void saveMonarchStorage(Player player, List<EntityType> stored) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (stored == null || stored.isEmpty()) {
            pdc.remove(keyMonarchStorage);
            return;
        }
        String joined = stored.stream().map(Enum::name).reduce((left, right) -> left + "," + right).orElse("");
        pdc.set(keyMonarchStorage, PersistentDataType.STRING, joined);
    }

    private boolean isMonarchStorable(LivingEntity entity) {
        if (!(entity instanceof Mob mob)) {
            return false;
        }
        EntityType type = mob.getType();
        if (MONARCH_BLOCKED_TYPES.contains(type) || !type.isSpawnable()) {
            return false;
        }
        Class<?> entityClass = type.getEntityClass();
        return entityClass != null && Mob.class.isAssignableFrom(entityClass);
    }

    private void markMonarchSummon(Mob mob, UUID ownerId) {
        PersistentDataContainer pdc = mob.getPersistentDataContainer();
        pdc.set(keyMonarchSummonTag, PersistentDataType.BYTE, (byte) 1);
        pdc.set(keyMonarchSummonOwner, PersistentDataType.STRING, ownerId.toString());
        mob.setPersistent(false);
        monarchOwnerByMob.put(mob.getUniqueId(), ownerId);
        monarchSummonsByOwner.computeIfAbsent(ownerId, ignored -> ConcurrentHashMap.newKeySet()).add(mob.getUniqueId());
    }

    private Player monarchOwnerOf(Entity entity) {
        UUID ownerId = entity == null ? null : monarchOwnerByMob.get(entity.getUniqueId());
        return ownerId == null ? null : Bukkit.getPlayer(ownerId);
    }

    private void directMonarchSummons(Player owner, LivingEntity target) {
        if (owner == null || target == null) {
            return;
        }
        if (target instanceof Player teammate && sameTeamOrSelf(owner.getUniqueId(), teammate.getUniqueId())) {
            return;
        }

        Set<UUID> summons = monarchSummonsByOwner.get(owner.getUniqueId());
        if (summons == null || summons.isEmpty()) {
            return;
        }

        for (UUID mobId : new HashSet<>(summons)) {
            Entity entity = Bukkit.getEntity(mobId);
            if (!(entity instanceof Mob mob) || !mob.isValid() || mob.isDead()) {
                summons.remove(mobId);
                monarchOwnerByMob.remove(mobId);
                continue;
            }
            if (!mob.getWorld().equals(target.getWorld())) {
                continue;
            }
            if (mob.getLocation().distanceSquared(owner.getLocation()) > 64 * 64) {
                continue;
            }
            mob.setTarget(target);
        }

        if (summons.isEmpty()) {
            monarchSummonsByOwner.remove(owner.getUniqueId());
        }
    }

    private void despawnMonarchSummons(UUID ownerId) {
        Set<UUID> summons = monarchSummonsByOwner.remove(ownerId);
        if (summons == null) {
            return;
        }
        for (UUID mobId : summons) {
            monarchOwnerByMob.remove(mobId);
            Entity entity = Bukkit.getEntity(mobId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
    }

    private boolean isUndeadPassiveType(EntityType type) {
        return type != null && UNDEAD_PASSIVE_TYPES.contains(type);
    }

    private void pacifyNearbyUndead(Player player) {
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 18.0, 12.0, 18.0)) {
            if (!(entity instanceof Mob mob) || !isUndeadPassiveType(mob.getType())) {
                continue;
            }
            if (mob.getTarget() != null && mob.getTarget().getUniqueId().equals(player.getUniqueId())) {
                mob.setTarget(null);
            }
        }
    }

    private LivingEntity actualLivingDamager(Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        if (damager instanceof Tameable tameable && tameable.getOwner() instanceof LivingEntity owner) {
            return owner;
        }
        return null;
    }

    private boolean sameTeamOrSelf(UUID ownerId, UUID targetId) {
        if (ownerId == null || targetId == null) {
            return false;
        }
        if (ownerId.equals(targetId)) {
            return true;
        }
        return plugin.getTeamManager() != null && plugin.getTeamManager().sameTeam(ownerId, targetId);
    }

    private String prettyEntityType(EntityType type) {
        String[] parts = type.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            out.append(part.substring(1));
        }
        return out.toString();
    }

    private Location findSummonLocation(Location base) {
        World world = base.getWorld();
        if (world == null) {
            return null;
        }
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);
            double radius = 2.0 + ThreadLocalRandom.current().nextDouble() * 2.0;
            Location candidate = base.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            Location safe = findSafeTravelLocation(candidate);
            if (safe != null) {
                return safe;
            }
        }
        return null;
    }

    private Vector directionFromYawPitch(float yaw, float pitch) {
        double yawRad = Math.toRadians(-yaw - 90.0f);
        double pitchRad = Math.toRadians(-pitch);
        double x = Math.cos(yawRad) * Math.cos(pitchRad);
        double y = Math.sin(pitchRad);
        double z = Math.sin(yawRad) * Math.cos(pitchRad);
        return new Vector(x, y, z).normalize();
    }

    private void consumeHeldItem(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand == null || mainHand.getType() == Material.AIR) {
            return;
        }
        if (mainHand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
            return;
        }
        mainHand.setAmount(mainHand.getAmount() - 1);
        player.getInventory().setItemInMainHand(mainHand);
    }

    private boolean hasMotherNatureStick(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isMotherNatureStick(item)) {
                return true;
            }
        }
        return isMotherNatureStick(player.getItemOnCursor());
    }

    private void giveMotherNatureStick(Player player, boolean restored) {
        ItemStack stick = createMotherNatureStickItem();
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stick);
        leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        if (restored) {
            player.sendMessage(MessageUtil.info("The Stick from Mother Nature returned to you."));
        }
    }

    private void removeMotherNatureSticks(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (isMotherNatureStick(item)) {
                player.getInventory().setItem(slot, null);
            }
        }

        if (isMotherNatureStick(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    private void refreshPowerItems(Player player) {
        AwakeningTableListener awakening = plugin.getAwakeningTableListener();
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (isAncientScroll(item)) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    applyAncientScrollState(meta);
                    applyAncientScrollPresentation(meta);
                    if (awakening != null) {
                        awakening.applyManagedItemState(meta, item.getType(), Component.text("Ancient Scroll"), false);
                    }
                    item.setItemMeta(meta);
                    player.getInventory().setItem(slot, item);
                }
                continue;
            }
            if (isWardenHeart(item)) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    applyWardenHeartState(meta);
                    applyWardenHeartPresentation(meta);
                    item.setItemMeta(meta);
                    player.getInventory().setItem(slot, item);
                }
                continue;
            }
            if (isMotherNatureStick(item)) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    applyMotherNatureStickState(meta);
                    applyMotherNatureStickPresentation(meta);
                    item.setItemMeta(meta);
                    player.getInventory().setItem(slot, item);
                }
            }
        }
    }

    private boolean matchesAncientScrollRecipe(ItemStack[] matrix) {
        int totems = 0;
        int stars = 0;
        int hearts = 0;
        for (ItemStack item : matrix) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (item.getType() == Material.TOTEM_OF_UNDYING && !isAncientScroll(item) && !isTalisman(item)) {
                totems += item.getAmount();
                continue;
            }
            if (item.getType() == Material.NETHER_STAR) {
                stars += item.getAmount();
                continue;
            }
            if (isWardenHeart(item)) {
                hearts += item.getAmount();
                continue;
            }
            return false;
        }
        return totems == 1 && stars == 2 && hearts == 1;
    }

    private boolean consumeAncientScrollIngredients(CraftingInventory inventory) {
        ItemStack[] matrix = inventory.getMatrix();
        if (!matchesAncientScrollRecipe(matrix)) {
            return false;
        }

        ItemStack[] next = new ItemStack[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            next[i] = matrix[i] == null ? null : matrix[i].clone();
        }

        int stars = 2;
        int totems = 1;
        int hearts = 1;
        for (int i = 0; i < next.length; i++) {
            ItemStack item = next[i];
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (item.getType() == Material.NETHER_STAR && stars > 0) {
                int take = Math.min(stars, item.getAmount());
                stars -= take;
                next[i] = reduceItem(item, take);
                continue;
            }
            if (item.getType() == Material.TOTEM_OF_UNDYING && totems > 0 && !isTalisman(item)) {
                int take = Math.min(totems, item.getAmount());
                totems -= take;
                next[i] = reduceItem(item, take);
                continue;
            }
            if (isWardenHeart(item) && hearts > 0) {
                int take = Math.min(hearts, item.getAmount());
                hearts -= take;
                next[i] = reduceItem(item, take);
            }
        }

        if (stars > 0 || totems > 0 || hearts > 0) {
            return false;
        }
        inventory.setMatrix(next);
        return true;
    }

    private ItemStack reduceItem(ItemStack item, int amount) {
        int left = item.getAmount() - amount;
        return left <= 0 ? null : item.asQuantity(left);
    }

    private boolean giveCraftResult(Player player, InventoryClickEvent event, ItemStack result) {
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

    private ItemStack preserveMotherNatureStick(ItemStack result) {
        ItemStack updated = result.clone();
        ItemMeta meta = updated.getItemMeta();
        if (meta == null) {
            return updated;
        }
        applyMotherNatureStickState(meta);
        applyMotherNatureStickPresentation(meta);
        updated.setItemMeta(meta);
        return updated;
    }

    private boolean hasTaggedItem(ItemStack item, NamespacedKey key, String expectedValue) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return expectedValue.equals(meta.getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    private boolean isTalisman(ItemStack item) {
        return plugin.getSustenanceTalismanListener() != null && plugin.getSustenanceTalismanListener().isTalisman(item);
    }

    private void fillInfoMenu(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, fillerPane());
        }
        SuperpowerType[] types = SuperpowerType.values();
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 21, 23};
        for (int i = 0; i < types.length && i < slots.length; i++) {
            inventory.setItem(slots[i], createPowerInfoIcon(types[i]));
        }
    }

    private ItemStack fillerPane() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private ItemStack createPowerInfoIcon(SuperpowerType type) {
        ItemStack item = new ItemStack(type.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(MM.deserialize("<gold><bold>" + type.displayName() + "</bold></gold>"));
        meta.lore(powerInfoLore(type));
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> powerInfoLore(SuperpowerType type) {
        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<gray>Chance: <white>" + formatPercent(type.chance()) + "</white></gray>"));
        lore.add(Component.empty());
        switch (type) {
            case IMMORTALITY -> {
                lore.add(MM.deserialize("<gray>Lethal damage leaves the player at <white>half a heart</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Triggers extremely strong regeneration.</gray>"));
            }
            case FLASH -> {
                lore.add(MM.deserialize("<gray>Permanent <white>Speed III</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Permanent <white>Haste III</white>.</gray>"));
            }
            case ENCHANTER -> {
                lore.add(MM.deserialize("<gray>All XP gains are multiplied by <white>5x</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Enchanting no longer needs <white>lapis</white>.</gray>"));
            }
            case BERSERK -> {
                lore.add(MM.deserialize("<gray>Permanent <white>Strength II</white>.</gray>"));
                lore.add(MM.deserialize("<gray>At <white>3 hearts or less</white>, gain <white>Speed II</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Also gains <white>Regeneration I</white> while low.</gray>"));
            }
            case TANK -> {
                lore.add(MM.deserialize("<gray>Permanent <white>two rows of health</white>.</gray>"));
                lore.add(MM.deserialize("<gray>While crouching, incoming knockback is nearly ignored.</gray>"));
            }
            case HUMAN -> {
                lore.add(MM.deserialize("<gray>No passive power.</gray>"));
                lore.add(MM.deserialize("<gray>An <white>Ancient Scroll</white> can reroll this fate.</gray>"));
                lore.add(MM.deserialize("<gray>An <white>awakened</white> scroll can rewrite any current power.</gray>"));
            }
            case TRAVELER -> {
                lore.add(MM.deserialize("<gray>Permanent <white>Speed II</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Creates paired portals to chosen coordinates.</gray>"));
                lore.add(MM.deserialize("<gray>Players and mobs can pass through them.</gray>"));
                lore.add(MM.deserialize("<gray>Works across the Overworld, Nether, and End.</gray>"));
                lore.add(MM.deserialize("<gray>Portals fade after <white>" + TRAVEL_PORTAL_DURATION_SECONDS + "s</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Command: <white>/travel [x] [y] [z] [dimension]</white></gray>"));
                lore.add(MM.deserialize("<gray>Close it early with <white>/travel close</white>.</gray>"));
            }
            case FLORIST -> {
                lore.add(MM.deserialize("<gray>Boosts nearby crop growth and doubles crops and wood.</gray>"));
                lore.add(MM.deserialize("<gray>Regenerates health outdoors.</gray>"));
                lore.add(MM.deserialize("<gray>Spam-crouching near crops surges their growth.</gray>"));
                lore.add(MM.deserialize("<gray>Grants the <white>Stick from Mother Nature</white>.</gray>"));
            }
            case MONARCH -> {
                lore.add(MM.deserialize("<gray>Stores slain mobs for later battle.</gray>"));
                lore.add(MM.deserialize("<gray>Undead mobs refuse to target the Monarch.</gray>"));
                lore.add(MM.deserialize("<gray>Storage limit: <white>" + MONARCH_STORAGE_LIMIT + "</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Command: <white>/msummon [amount]</white></gray>"));
            }
            case SHADOW -> {
                lore.add(MM.deserialize("<gray>Toggle invisibility for <white>15 minutes</white>.</gray>"));
                lore.add(MM.deserialize("<gray>While hidden, gain <white>Speed III</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Hit cooldown: <white>7 minutes</white>. Normal cooldown: <white>5 minutes</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Command: <white>/shadow toggle</white></gray>"));
            }
        }
        return lore;
    }

    private void applyAncientScrollState(ItemMeta meta) {
        meta.getPersistentDataContainer().set(keyAncientScroll, PersistentDataType.STRING, ANCIENT_SCROLL_ITEM_ID);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        CustomLoreUtil.applyStyledItemFlags(meta);
    }

    private void applyAncientScrollPresentation(ItemMeta meta) {
        boolean awakened = isAwakenedAncientScroll(meta);
        meta.displayName(MM.deserialize("<gold><bold>Ancient Scroll</bold></gold>"));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.PAPER,
            "CUSTOM",
            "SCROLL",
            List.of(awakened
                ? "<gray>Right-click to twist your current power into a random new one.</gray>"
                : "<gray>Right-click to reroll a dormant fate.</gray>"),
            List.of(CustomLoreUtil.section(
                "Use",
                awakened ? "Power Rewrite" : "Fate Rewrite",
                awakened
                    ? "<gray>Works even if you already have a power.</gray>"
                    : "<gray>Only works for a <white>Human</white> with no power.</gray>",
                awakened
                    ? "<gray>Consumes the scroll and rerolls you into a random new power.</gray>"
                    : "<gray>Consumes the scroll and rerolls into a real power.</gray>"
            ))
        ));
    }

    private void applyWardenHeartState(ItemMeta meta) {
        meta.getPersistentDataContainer().set(keyWardenHeart, PersistentDataType.STRING, WARDEN_HEART_ITEM_ID);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        CustomLoreUtil.applyStyledItemFlags(meta);
    }

    private void applyWardenHeartPresentation(ItemMeta meta) {
        meta.displayName(MM.deserialize("<dark_aqua><bold>Warden Heart</bold></dark_aqua>"));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.HEART_OF_THE_SEA,
            "CUSTOM",
            "RELIC",
            List.of("<gray>A rare relic pulled from a fallen Warden.</gray>"),
            List.of(CustomLoreUtil.section(
                "Use",
                "Ancient Scroll",
                "<gray>Used to craft an <white>Ancient Scroll</white>.</gray>"
            ))
        ));
    }

    private void applyMotherNatureStickState(ItemMeta meta) {
        meta.getPersistentDataContainer().set(keyMotherNatureStick, PersistentDataType.STRING, MOTHER_NATURE_STICK_ITEM_ID);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        CustomLoreUtil.applyStyledItemFlags(meta);
    }

    private void applyMotherNatureStickPresentation(ItemMeta meta) {
        meta.displayName(MM.deserialize("<green><bold>Stick from Mother Nature</bold></green>"));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.STICK,
            "CUSTOM",
            "NATURE",
            List.of("<gray>Bound to the Florist.</gray>"),
            List.of(
                CustomLoreUtil.section(
                    "Left Click",
                    "Piercing Vines",
                    "<gray>Fires a burst of <white>3</white> piercing vine shots.</gray>",
                    "<gray>Each vine deals <white>1 heart</white>.</gray>"
                ),
                CustomLoreUtil.section(
                    "Right Click",
                    "Nature's Heal",
                    "<gray>Heals yourself or the player you are pointing at.</gray>",
                    "<gray>Grants <white>Regeneration III</white> for <white>" + FLORIST_HEAL_DURATION_SECONDS + "s</white>.</gray>"
                )
            )
        ));
    }

    private String formatPercent(double chance) {
        double percent = chance * 100.0;
        if (Math.abs(percent - Math.rint(percent)) < 0.0001) {
            return Long.toString(Math.round(percent)) + "%";
        }
        return String.format(Locale.US, "%.2f%%", percent);
    }

    private SuperpowerType randomDifferentPower(SuperpowerType currentPower, boolean excludeHuman) {
        List<SuperpowerType> choices = new ArrayList<>();
        double total = 0.0;
        for (SuperpowerType type : SuperpowerType.values()) {
            if (type == currentPower) {
                continue;
            }
            if (excludeHuman && type == SuperpowerType.HUMAN) {
                continue;
            }
            choices.add(type);
            total += type.chance();
        }
        if (choices.isEmpty()) {
            return randomPower(excludeHuman);
        }

        double roll = ThreadLocalRandom.current().nextDouble() * total;
        double cursor = 0.0;
        for (SuperpowerType type : choices) {
            cursor += type.chance();
            if (roll <= cursor) {
                return type;
            }
        }
        return choices.get(choices.size() - 1);
    }

    private void syncTankImmovableState(Player player, boolean shouldBeImmovable) {
        var attribute = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (attribute == null) {
            return;
        }

        AttributeModifier existing = null;
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (keyTankImmovableModifier.equals(modifier.getKey())) {
                existing = modifier;
                break;
            }
        }

        boolean active = shouldBeImmovable
            && hasPower(player, SuperpowerType.TANK)
            && !player.isDead()
            && player.getGameMode() != GameMode.SPECTATOR;

        if (active) {
            if (existing == null) {
                attribute.addModifier(new AttributeModifier(
                    keyTankImmovableModifier,
                    TANK_CROUCH_KNOCKBACK_RESISTANCE,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.ANY
                ));
            }
            return;
        }

        if (existing != null) {
            attribute.removeModifier(existing);
        }
    }

    private ItemStack createVirtualEnchanterLapis() {
        ItemStack lapis = new ItemStack(Material.LAPIS_LAZULI, ENCHANTER_VIRTUAL_LAPIS_AMOUNT);
        ItemMeta meta = lapis.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(keyEnchanterLapis, PersistentDataType.BYTE, (byte) 1);
            lapis.setItemMeta(meta);
        }
        return lapis;
    }

    private boolean isVirtualEnchanterLapis(ItemStack item) {
        if (item == null || item.getType() != Material.LAPIS_LAZULI) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte marker = meta.getPersistentDataContainer().get(keyEnchanterLapis, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private void refreshEnchanterLapis(Player player, EnchantingInventory enchanting) {
        if (player == null || enchanting == null) {
            return;
        }
        if (!player.isOnline() || !hasPower(player, SuperpowerType.ENCHANTER)) {
            clearVirtualEnchanterLapis(enchanting);
            return;
        }

        ItemStack secondary = enchanting.getSecondary();
        if (secondary != null && secondary.getType() != Material.AIR && !isVirtualEnchanterLapis(secondary)) {
            return;
        }

        ItemStack item = enchanting.getItem();
        if (item == null || item.getType() == Material.AIR) {
            clearVirtualEnchanterLapis(enchanting);
            return;
        }

        enchanting.setSecondary(createVirtualEnchanterLapis());
    }

    private void clearVirtualEnchanterLapis(Player player) {
        if (player == null) {
            return;
        }
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top instanceof EnchantingInventory enchanting) {
            clearVirtualEnchanterLapis(enchanting);
        }
    }

    private void clearVirtualEnchanterLapis(EnchantingInventory enchanting) {
        if (enchanting != null && isVirtualEnchanterLapis(enchanting.getSecondary())) {
            enchanting.setSecondary(null);
        }
    }

    private boolean isAwakenedAncientScroll(ItemStack item) {
        AwakeningTableListener awakening = plugin.getAwakeningTableListener();
        return awakening != null && awakening.isAwakened(item);
    }

    private boolean isAwakenedAncientScroll(ItemMeta meta) {
        AwakeningTableListener awakening = plugin.getAwakeningTableListener();
        return awakening != null && awakening.isAwakened(meta);
    }

    private record PowerInfoHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record PortalPair(UUID ownerId, Location source, Location target, long expiresAt) {}
}
