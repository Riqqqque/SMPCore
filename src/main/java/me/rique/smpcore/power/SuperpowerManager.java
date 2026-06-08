package me.rique.smpcore.power;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.awakening.AwakeningTableListener;
import me.rique.smpcore.boss.BossManager;
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
import org.bukkit.enchantments.Enchantment;
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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
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
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.AnvilInventory;
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
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
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
    public static final String THE_WORLD_CLOCK_ITEM_ID = "the_world_clock";
    public static final String DRUID_GRIMOIRE_ITEM_ID = "druid_grimoire";

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Component POWERS_MENU_TITLE =
        MM.deserialize("<gradient:#ff9a3d:#d61c4e><bold>Superpower Info</bold></gradient>");
    private static final Component DRUID_MENU_TITLE =
        MM.deserialize("<gradient:#63c74d:#2f8f47><bold>Druid's Grimoire</bold></gradient>");

    private static final int MENU_SIZE = 54;
    private static final int DRUID_MENU_SIZE = 27;
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
    private static final int DRUID_BUFF_RADIUS = 5;
    private static final int DRUID_BUFF_DURATION_SECONDS = 300;
    private static final int DRUID_BUFF_COOLDOWN_SECONDS = 90;
    private static final int IMMORTALITY_REGEN_SECONDS = 10;
    private static final int IMMORTALITY_RESCUE_SECONDS = 4;
    private static final double IMMORTALITY_SURVIVAL_HEALTH = 1.0;
    private static final double BERSERK_LOW_HEALTH_THRESHOLD = 6.0;
    private static final int ENCHANTER_VIRTUAL_LAPIS_AMOUNT = 64;
    private static final double ENCHANTER_ESSENCE_EXTRACTION_CHANCE = 0.10;
    private static final double ENCHANTER_ARCANE_PRESERVATION_CHANCE = 0.25;
    private static final double ENCHANTER_LUCK_BONUS = 2.0;
    private static final double TANK_CROUCH_KNOCKBACK_RESISTANCE = 1.0;
    private static final double MINER_EXTRA_ORE_CHANCE = 0.15;
    private static final double MINER_HEALTH_BONUS = 2.0;
    private static final double GIANT_SCALE_MULTIPLIER = 1.2;
    private static final double GIANT_HEALTH_BONUS = 12.0;
    private static final double GIANT_KNOCKBACK_RESISTANCE = 0.40;
    private static final double GIANT_ATTACK_DAMAGE_BONUS = 2.0;
    private static final double SUPERMAN_HEALTH_BONUS = 20.0;
    private static final int SUPERMAN_FLIGHT_SECONDS = 30;
    private static final int SUPERMAN_FLIGHT_COOLDOWN_SECONDS = 5 * 60;
    private static final long SUPERMAN_BOOST_COOLDOWN_MS = 800L;
    private static final int XRAY_DURATION_SECONDS = 30;
    private static final int XRAY_COOLDOWN_SECONDS = 6 * 60;
    private static final int XRAY_ENTITY_RADIUS = 24;
    private static final int XRAY_ORE_RADIUS = 12;
    private static final int XRAY_ORE_VERTICAL_RADIUS = 8;
    private static final int TIME_STOP_RADIUS = 10;
    private static final int TIME_STOP_DURATION_SECONDS = 5;
    private static final int TIME_STOP_COOLDOWN_SECONDS = 5 * 60;
    private static final int PHOENIX_COOLDOWN_SECONDS = 10 * 60;
    private static final double PHOENIX_RECOVERY_HEALTH = 8.0;
    private static final double PHOENIX_BURST_RADIUS = 4.5;
    private static final double PHOENIX_BURST_DAMAGE = 4.0;
    private static final double PHOENIX_SEARING_STRIKE_DAMAGE = 1.0;
    private static final double PHOENIX_LOW_HEALTH_RATIO = 0.35;
    private static final int VOIDSTEP_COOLDOWN_SECONDS = 45;
    private static final double VOIDSTEP_RANGE = 25.0;
    private static final int VOIDSTEP_VEIL_SECONDS = 4;
    private static final int VOIDSTEP_SLOW_FALLING_SECONDS = 7;
    private static final int VOIDSTEP_INVISIBILITY_SECONDS = 12;
    private static final double VOIDSTEP_AMBUSH_DAMAGE = 3.0;
    private static final double SENTINEL_AURA_RADIUS = 6.0;
    private static final double SENTINEL_HEALTH_BONUS = 4.0;
    private static final double SENTINEL_BRACE_DAMAGE_REDUCTION = 0.20;
    private static final double WATERMAN_DAMAGE_MULTIPLIER = 1.25;
    private static final double WATERMAN_DAMAGE_REDUCTION = 0.25;
    private static final double WATERMAN_SUBMERGED_MINING_BONUS = 0.8;
    private static final int FROSTBORN_CHILL_SECONDS = 4;
    private static final double FROSTBORN_FROZEN_TARGET_DAMAGE_MULTIPLIER = 1.15;
    private static final double DEADEYE_PROJECTILE_DAMAGE_MULTIPLIER = 1.20;
    private static final double DEADEYE_MARKED_SHOT_DAMAGE_MULTIPLIER = 1.35;
    private static final double DEADEYE_MARKED_SHOT_VELOCITY_MULTIPLIER = 1.18;
    private static final int DEADEYE_GLOW_SECONDS = 5;
    private static final int DEADEYE_MARKED_SHOT_SLOW_SECONDS = 3;
    private static final double RIFTWARDEN_BOSS_RADIUS = 18.0;
    private static final double RIFTWARDEN_BOSS_DAMAGE_MULTIPLIER = 1.18;
    private static final double RIFTWARDEN_MOB_DAMAGE_MULTIPLIER = 1.08;
    private static final double RIFTWARDEN_BOSS_DAMAGE_REDUCTION = 0.25;
    private static final double RIFTWARDEN_MOB_DAMAGE_REDUCTION = 0.15;
    private static final double OATHBOUND_AURA_RADIUS = 8.0;
    private static final double RUNESMITH_PRESERVATION_CHANCE = 0.15;
    private static final double RUNESMITH_SPECIAL_PRESERVATION_CHANCE = 0.25;
    private static final double RUNESMITH_BOSS_REPAIR_RATIO = 0.10;
    private static final double GRAVEBORN_KILL_HEAL = 2.0;
    private static final double GRAVEBORN_PLAYER_KILL_HEAL = 6.0;
    private static final double GRAVEBORN_UNDEAD_DAMAGE_MULTIPLIER = 1.12;
    private static final double GRAVEBORN_UNDEAD_DAMAGE_REDUCTION = 0.20;
    private static final double STORMCALLER_PROC_CHANCE = 0.14;
    private static final double STORMCALLER_STORM_PROC_CHANCE = 0.24;
    private static final double STORMCALLER_DAMAGE_BONUS = 2.0;
    private static final double BLOODMENDER_PLAYER_LEECH_RATIO = 0.12;
    private static final double BLOODMENDER_MOB_LEECH_RATIO = 0.18;
    private static final double BLOODMENDER_MAX_LEECH = 2.0;
    private static final int PASSIVE_NIGHT_VISION_TICKS = 600;
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

    private static final Set<Material> MINER_ORE_BLOCKS = EnumSet.of(
        Material.COAL_ORE,
        Material.DEEPSLATE_COAL_ORE,
        Material.IRON_ORE,
        Material.DEEPSLATE_IRON_ORE,
        Material.COPPER_ORE,
        Material.DEEPSLATE_COPPER_ORE,
        Material.GOLD_ORE,
        Material.DEEPSLATE_GOLD_ORE,
        Material.REDSTONE_ORE,
        Material.DEEPSLATE_REDSTONE_ORE,
        Material.EMERALD_ORE,
        Material.DEEPSLATE_EMERALD_ORE,
        Material.LAPIS_ORE,
        Material.DEEPSLATE_LAPIS_ORE,
        Material.DIAMOND_ORE,
        Material.DEEPSLATE_DIAMOND_ORE,
        Material.NETHER_QUARTZ_ORE,
        Material.NETHER_GOLD_ORE,
        Material.ANCIENT_DEBRIS
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
    private final NamespacedKey keyTheWorldClock;
    private final NamespacedKey keyDruidGrimoire;
    private final NamespacedKey keyEnchanterLapis;
    private final NamespacedKey keyTankImmovableModifier;
    private final NamespacedKey keyEnchanterLuckModifier;
    private final NamespacedKey keyEnchanterMoveModifier;
    private final NamespacedKey keyEnchanterAttackModifier;
    private final NamespacedKey keyMinerHealthModifier;
    private final NamespacedKey keyGiantHealthModifier;
    private final NamespacedKey keyGiantScaleModifier;
    private final NamespacedKey keyGiantKnockbackModifier;
    private final NamespacedKey keyGiantAttackDamageModifier;
    private final NamespacedKey keySupermanHealthModifier;
    private final NamespacedKey keySentinelHealthModifier;
    private final NamespacedKey keyWatermanSubmergedMiningModifier;
    private final NamespacedKey keySupermanFlightActiveUntil;
    private final NamespacedKey keySupermanFlightCooldownUntil;
    private final NamespacedKey keyXrayActiveUntil;
    private final NamespacedKey keyXrayCooldownUntil;
    private final NamespacedKey keyTimeStopCooldownUntil;
    private final NamespacedKey keyPhoenixCooldownUntil;
    private final NamespacedKey keyVoidstepCooldownUntil;
    private final NamespacedKey keyVoidstepVeilUntil;
    private final NamespacedKey keyVoidwalkerNightVisionEnabled;
    private final NamespacedKey keyDeadeyeMarkedShot;
    private final NamespacedKey keyShadowCooldownUntil;
    private final NamespacedKey keyShadowActiveUntil;
    private final NamespacedKey keyDruidBuffCooldownUntil;
    private final NamespacedKey keyStormcallerLightningEnabled;
    private final NamespacedKey keyMonarchSummonOwner;
    private final NamespacedKey keyMonarchSummonTag;
    private final Map<UUID, Integer> pendingFloristStickReturns = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pendingTheWorldClockReturns = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pendingDruidGrimoireReturns = new ConcurrentHashMap<>();
    private final Map<UUID, PortalPair> activeTravelerPortals = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentPortalTravel = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> monarchSummonsByOwner = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> monarchOwnerByMob = new ConcurrentHashMap<>();
    private final Map<UUID, Long> floristCrouchGrowthCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> floristLeftClickCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> floristRightClickCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> supermanBoostCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, TimeStopState> activeTimeStops = new ConcurrentHashMap<>();
    private final Map<UUID, FrozenMobState> frozenMobs = new ConcurrentHashMap<>();
    private final Map<UUID, FrozenProjectileState> frozenProjectiles = new ConcurrentHashMap<>();
    private final Set<UUID> timeStoppedPlayers = ConcurrentHashMap.newKeySet();
    private BukkitTask passiveTask;
    private BukkitTask portalTask;
    private BukkitTask timeStopTask;

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
        this.keyTheWorldClock = new NamespacedKey(plugin, THE_WORLD_CLOCK_ITEM_ID);
        this.keyDruidGrimoire = new NamespacedKey(plugin, DRUID_GRIMOIRE_ITEM_ID);
        this.keyEnchanterLapis = new NamespacedKey(plugin, "superpower_enchanter_lapis");
        this.keyTankImmovableModifier = new NamespacedKey(plugin, "superpower_tank_immovable");
        this.keyEnchanterLuckModifier = new NamespacedKey(plugin, "superpower_enchanter_luck");
        this.keyEnchanterMoveModifier = new NamespacedKey(plugin, "superpower_enchanter_move");
        this.keyEnchanterAttackModifier = new NamespacedKey(plugin, "superpower_enchanter_attack");
        this.keyMinerHealthModifier = new NamespacedKey(plugin, "superpower_miner_health");
        this.keyGiantHealthModifier = new NamespacedKey(plugin, "superpower_giant_health");
        this.keyGiantScaleModifier = new NamespacedKey(plugin, "superpower_giant_scale");
        this.keyGiantKnockbackModifier = new NamespacedKey(plugin, "superpower_giant_knockback");
        this.keyGiantAttackDamageModifier = new NamespacedKey(plugin, "superpower_giant_attack_damage");
        this.keySupermanHealthModifier = new NamespacedKey(plugin, "superpower_superman_health");
        this.keySentinelHealthModifier = new NamespacedKey(plugin, "superpower_sentinel_health");
        this.keyWatermanSubmergedMiningModifier = new NamespacedKey(plugin, "superpower_waterman_submerged_mining");
        this.keySupermanFlightActiveUntil = new NamespacedKey(plugin, "superpower_superman_flight_until");
        this.keySupermanFlightCooldownUntil = new NamespacedKey(plugin, "superpower_superman_flight_cooldown_until");
        this.keyXrayActiveUntil = new NamespacedKey(plugin, "superpower_xray_active_until");
        this.keyXrayCooldownUntil = new NamespacedKey(plugin, "superpower_xray_cooldown_until");
        this.keyTimeStopCooldownUntil = new NamespacedKey(plugin, "superpower_time_stop_cooldown_until");
        this.keyPhoenixCooldownUntil = new NamespacedKey(plugin, "superpower_phoenix_cooldown_until");
        this.keyVoidstepCooldownUntil = new NamespacedKey(plugin, "superpower_voidstep_cooldown_until");
        this.keyVoidstepVeilUntil = new NamespacedKey(plugin, "superpower_voidstep_veil_until");
        this.keyVoidwalkerNightVisionEnabled = new NamespacedKey(plugin, "superpower_voidwalker_night_vision_enabled");
        this.keyDeadeyeMarkedShot = new NamespacedKey(plugin, "superpower_deadeye_marked_shot");
        this.keyShadowCooldownUntil = new NamespacedKey(plugin, "superpower_shadow_cooldown_until");
        this.keyShadowActiveUntil = new NamespacedKey(plugin, "superpower_shadow_active_until");
        this.keyDruidBuffCooldownUntil = new NamespacedKey(plugin, "superpower_druid_buff_cooldown_until");
        this.keyStormcallerLightningEnabled = new NamespacedKey(plugin, "superpower_stormcaller_lightning_enabled");
        this.keyMonarchSummonOwner = new NamespacedKey(plugin, "superpower_monarch_owner");
        this.keyMonarchSummonTag = new NamespacedKey(plugin, "superpower_monarch_summon");
    }

    public void start() {
        passiveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPlayers, 20L, 20L);
        portalTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPortals, 5L, 5L);
        timeStopTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTimeStops, 1L, 1L);
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::initializePlayerState));
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isShadowActive(player)) {
                restoreShadowAppearance(player);
            }
            syncTankImmovableState(player, false);
            clearPowerAttributeModifiers(player);
            syncSupermanFlightState(player, false);
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
        if (timeStopTask != null) {
            timeStopTask.cancel();
            timeStopTask = null;
        }
        for (UUID ownerId : new HashSet<>(activeTravelerPortals.keySet())) {
            closeTravelerPortal(ownerId, false);
        }
        for (UUID ownerId : new HashSet<>(monarchSummonsByOwner.keySet())) {
            despawnMonarchSummons(ownerId);
        }
        clearAllTimeStops();
        pendingFloristStickReturns.clear();
        pendingTheWorldClockReturns.clear();
        pendingDruidGrimoireReturns.clear();
        recentPortalTravel.clear();
        floristCrouchGrowthCooldowns.clear();
        floristLeftClickCooldowns.clear();
        floristRightClickCooldowns.clear();
        supermanBoostCooldowns.clear();
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

    public ItemStack createTheWorldClockItem() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        applyTheWorldClockState(meta);
        applyTheWorldClockPresentation(meta);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createDruidGrimoireItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        applyDruidGrimoireState(meta);
        applyDruidGrimoirePresentation(meta);
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

    public boolean isTheWorldClock(ItemStack item) {
        return hasTaggedItem(item, keyTheWorldClock, THE_WORLD_CLOCK_ITEM_ID);
    }

    public boolean isDruidGrimoire(ItemStack item) {
        return hasTaggedItem(item, keyDruidGrimoire, DRUID_GRIMOIRE_ITEM_ID);
    }

    public void openAdminInfoMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(
            new PowerInfoHolder(),
            MENU_SIZE,
            BedrockCompat.menuTitle(player, POWERS_MENU_TITLE, "Superpower Info")
        );
        fillInfoMenu(player, inventory);
        player.openInventory(inventory);
    }

    public SuperpowerType powerOf(Player player) {
        return player == null ? null : powerOf(player.getPersistentDataContainer());
    }

    public boolean hasPower(Player player, SuperpowerType type) {
        return type != null && type == powerOf(player);
    }

    public boolean shouldRetainFlightAccess(Player player) {
        if (player == null || !hasPower(player, SuperpowerType.SUPERMAN)) {
            return false;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        long now = System.currentTimeMillis();
        return supermanFlightActiveUntil(player) > now || supermanFlightCooldownUntil(player) <= now;
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

    public boolean handleXrayCommand(Player player) {
        if (!hasPower(player, SuperpowerType.XRAY_VISION)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }

        long now = System.currentTimeMillis();
        if (xrayActiveUntil(player) > now) {
            player.sendMessage(MessageUtil.warn(
                "Oracle Eye is already active for <white>" + secondsLeft(xrayActiveUntil(player), now) + "s</white>."
            ));
            return false;
        }

        long cooldownUntil = xrayCooldownUntil(player);
        if (cooldownUntil > now) {
            player.sendMessage(MessageUtil.warn(
                "Oracle Eye cooldown: <white>" + secondsLeft(cooldownUntil, now) + "s</white>."
            ));
            return false;
        }

        setXrayActiveUntil(player, now + (XRAY_DURATION_SECONDS * 1000L));
        setXrayCooldownUntil(player, now + (XRAY_COOLDOWN_SECONDS * 1000L));
        player.sendMessage(MessageUtil.success("Oracle Eye tears through the walls around you."));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.8f);
        renderXrayHighlights(player);
        return true;
    }

    public boolean handleVoidstepCommand(Player player) {
        if (!hasPower(player, SuperpowerType.VOIDWALKER)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            player.sendMessage(MessageUtil.warn("You cannot step through the void right now."));
            return false;
        }

        long now = System.currentTimeMillis();
        long cooldownUntil = voidstepCooldownUntil(player);
        if (cooldownUntil > now) {
            player.sendMessage(MessageUtil.warn("Voidstep cooldown: <white>" + secondsLeft(cooldownUntil, now) + "s</white>."));
            return false;
        }

        Location destination = findVoidstepDestination(player);
        if (destination == null) {
            player.sendMessage(MessageUtil.warn("The void cannot find a safe path there."));
            return false;
        }

        Location start = player.getLocation();
        setVoidstepCooldownUntil(player, now + (VOIDSTEP_COOLDOWN_SECONDS * 1000L));
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        player.getWorld().spawnParticle(Particle.PORTAL, start.clone().add(0.0, 1.0, 0.0), 45, 0.45, 0.65, 0.45, 0.08);
        player.getWorld().playSound(start, Sound.ENTITY_ENDERMAN_TELEPORT, 0.85f, 0.75f);
        player.teleportAsync(destination).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!ok) {
                setVoidstepCooldownUntil(player, 0L);
                player.sendMessage(MessageUtil.error("Voidstep failed."));
                return;
            }
            setVoidstepVeilUntil(player, System.currentTimeMillis() + (VOIDSTEP_VEIL_SECONDS * 1000L));
            applyPotion(player, PotionEffectType.INVISIBILITY, VOIDSTEP_INVISIBILITY_SECONDS * 20, 0);
            applyPotion(player, PotionEffectType.SLOW_FALLING, VOIDSTEP_SLOW_FALLING_SECONDS * 20, 0);
            player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().clone().add(0.0, 1.0, 0.0), 55, 0.45, 0.65, 0.45, 0.08);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.9f, 1.35f);
        }));
        return true;
    }

    public boolean handleVoidwalkerNightVisionCommand(Player player) {
        if (!hasPower(player, SuperpowerType.VOIDWALKER)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }

        boolean enabled = !isVoidwalkerNightVisionEnabled(player);
        setVoidwalkerNightVisionEnabled(player, enabled);
        if (enabled) {
            applyPassiveNightVision(player);
            player.sendMessage(MessageUtil.success("Voidwalker night vision <white>enabled</white>."));
        } else {
            removePassiveNightVision(player);
            player.sendMessage(MessageUtil.info("Voidwalker night vision <white>disabled</white>."));
        }
        return true;
    }

    public boolean handleStormcallerLightningCommand(Player player, Boolean requestedEnabled) {
        if (!hasPower(player, SuperpowerType.STORMCALLER)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }

        boolean enabled = requestedEnabled == null ? !isStormcallerLightningEnabled(player) : requestedEnabled;
        setStormcallerLightningEnabled(player, enabled);
        if (enabled) {
            player.sendMessage(MessageUtil.success("Stormcaller lightning <white>enabled</white>."));
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.35f);
            player.getWorld().spawnParticle(
                Particle.ELECTRIC_SPARK,
                player.getLocation().clone().add(0.0, 1.0, 0.0),
                18,
                0.35,
                0.45,
                0.35,
                0.05
            );
        } else {
            player.sendMessage(MessageUtil.info("Stormcaller lightning <white>disabled</white>. Your storm buffs still work."));
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_COPPER_BULB_TURN_OFF, 0.7f, 0.85f);
        }
        return true;
    }

    public boolean handleStormcallerLightningStatusCommand(Player player) {
        if (!hasPower(player, SuperpowerType.STORMCALLER)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }

        player.sendMessage(MessageUtil.info(
            "Stormcaller lightning is <white>" + (isStormcallerLightningEnabled(player) ? "enabled" : "disabled") + "</white>."
        ));
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
                pendingFloristStickReturns.put(player.getUniqueId(), 1);
            }
        }
        if (hasPower(player, SuperpowerType.THE_WORLD)) {
            int kept = 0;
            List<ItemStack> drops = event.getDrops();
            for (int i = drops.size() - 1; i >= 0; i--) {
                ItemStack drop = drops.get(i);
                if (!isTheWorldClock(drop)) {
                    continue;
                }
                kept += Math.max(1, drop.getAmount());
                drops.remove(i);
            }
            if (kept > 0) {
                pendingTheWorldClockReturns.merge(player.getUniqueId(), kept, Integer::sum);
            }
        }
        if (hasPower(player, SuperpowerType.DRUID)) {
            int kept = 0;
            List<ItemStack> drops = event.getDrops();
            for (int i = drops.size() - 1; i >= 0; i--) {
                ItemStack drop = drops.get(i);
                if (!isDruidGrimoire(drop)) {
                    continue;
                }
                kept += Math.max(1, drop.getAmount());
                drops.remove(i);
            }
            if (kept > 0) {
                pendingDruidGrimoireReturns.merge(player.getUniqueId(), kept, Integer::sum);
            }
        }
        if (isShadowActive(player)) {
            deactivateShadow(player, false, false);
        }
        stopSupermanFlight(player, false);
        syncTankImmovableState(player, false);
        clearPowerAttributeModifiers(player);
        clearVirtualEnchanterLapis(player);
        closeTravelerPortal(player.getUniqueId(), false);
        despawnMonarchSummons(player.getUniqueId());
        clearTimeStopForOwner(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Integer pendingValue = pendingFloristStickReturns.remove(player.getUniqueId());
        int pending = pendingValue == null ? 0 : pendingValue;
        Integer pendingClockValue = pendingTheWorldClockReturns.remove(player.getUniqueId());
        int pendingClocks = pendingClockValue == null ? 0 : pendingClockValue;
        Integer pendingGrimoireValue = pendingDruidGrimoireReturns.remove(player.getUniqueId());
        int pendingGrimoires = pendingGrimoireValue == null ? 0 : pendingGrimoireValue;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (pending > 0) {
                giveMotherNatureStick(player, true);
            }
            for (int i = 0; i < pendingClocks; i++) {
                giveTheWorldClock(player, true);
            }
            for (int i = 0; i < pendingGrimoires; i++) {
                giveDruidGrimoire(player, true);
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        syncTankImmovableState(player, false);
        clearPowerAttributeModifiers(player);
        stopSupermanFlight(player, false);
        clearVirtualEnchanterLapis(player);
        closeTravelerPortal(playerId, false);
        despawnMonarchSummons(playerId);
        clearTimeStopForOwner(playerId);
        timeStoppedPlayers.remove(playerId);
        recentPortalTravel.remove(playerId);
        floristCrouchGrowthCooldowns.remove(playerId);
        floristLeftClickCooldowns.remove(playerId);
        floristRightClickCooldowns.remove(playerId);
        supermanBoostCooldowns.remove(playerId);
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchanterItemDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        if (hasPower(player, SuperpowerType.ENCHANTER)
            && ThreadLocalRandom.current().nextDouble() < ENCHANTER_ARCANE_PRESERVATION_CHANCE) {
            event.setCancelled(true);
            return;
        }

        if (!hasPower(player, SuperpowerType.RUNESMITH)) {
            return;
        }
        double chance = isSpecialCraftedItem(event.getItem())
            ? RUNESMITH_SPECIAL_PRESERVATION_CHANCE
            : RUNESMITH_PRESERVATION_CHANCE;
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSupermanFlightToggle(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!hasPower(player, SuperpowerType.SUPERMAN)
            || player.getGameMode() == GameMode.CREATIVE
            || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        long now = System.currentTimeMillis();
        if (supermanFlightActiveUntil(player) > now) {
            return;
        }

        event.setCancelled(true);
        long cooldownUntil = supermanFlightCooldownUntil(player);
        if (cooldownUntil > now) {
            player.sendMessage(MessageUtil.warn(
                "Skybound flight cooldown: <white>" + secondsLeft(cooldownUntil, now) + "s</white>."
            ));
            syncSupermanFlightState(player, true);
            return;
        }

        startSupermanFlight(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFrozenPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!timeStoppedPlayers.contains(player.getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }

        Location locked = from.clone();
        locked.setYaw(to.getYaw());
        locked.setPitch(to.getPitch());
        event.setTo(locked);
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

        if (hasPower(player, SuperpowerType.PHOENIX)) {
            double finalDamage = event.getFinalDamage();
            if (finalDamage > 0.0 && player.getHealth() - finalDamage <= 0.0 && tryPhoenixRebirth(player, event.getCause())) {
                event.setCancelled(true);
                return;
            }
        }

        if (hasPower(player, SuperpowerType.VOIDWALKER)
            && event.getCause() == EntityDamageEvent.DamageCause.FALL
            && voidstepVeilUntil(player) > System.currentTimeMillis()) {
            event.setCancelled(true);
            return;
        }

        if (hasPower(player, SuperpowerType.FROSTBORN)
            && event.getCause() == EntityDamageEvent.DamageCause.FREEZE) {
            event.setCancelled(true);
            return;
        }

        if (hasPower(player, SuperpowerType.WATERMAN)
            && event.getCause() == EntityDamageEvent.DamageCause.DROWNING) {
            event.setCancelled(true);
            player.setRemainingAir(player.getMaximumAir());
            return;
        }

        if (hasPower(player, SuperpowerType.GRAVEBORN)
            && (event.getCause() == EntityDamageEvent.DamageCause.WITHER
            || event.getCause() == EntityDamageEvent.DamageCause.POISON)) {
            event.setCancelled(true);
            return;
        }

        if (isShadowActive(player) && event.getFinalDamage() > 0.0) {
            deactivateShadow(player, true, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPowerCombat(EntityDamageByEntityEvent event) {
        LivingEntity source = actualLivingDamager(event.getDamager());
        if (source == null || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Player attacker = source instanceof Player player ? player : null;
        if (attacker != null && isFriendlyTo(attacker, victim)) {
            return;
        }

        if (attacker != null && event.getDamage() > 0.0) {
            if (hasPower(attacker, SuperpowerType.WATERMAN) && isWatermanEmpowered(attacker)) {
                event.setDamage(event.getDamage() * WATERMAN_DAMAGE_MULTIPLIER);
                victim.getWorld().spawnParticle(Particle.SPLASH, victim.getLocation().clone().add(0.0, 1.0, 0.0), 18, 0.45, 0.35, 0.45, 0.05);
                victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_TRIDENT_HIT, 0.55f, 1.25f);
            }

            if (hasPower(attacker, SuperpowerType.PHOENIX)) {
                if (victim.getFireTicks() > 0) {
                    event.setDamage(event.getDamage() + PHOENIX_SEARING_STRIKE_DAMAGE);
                }
                victim.setFireTicks(Math.max(victim.getFireTicks(), 60));
                victim.getWorld().spawnParticle(Particle.FLAME, victim.getLocation().clone().add(0.0, 1.0, 0.0), 12, 0.35, 0.35, 0.35, 0.02);
            }

            if (hasPower(attacker, SuperpowerType.VOIDWALKER) && voidstepVeilUntil(attacker) > System.currentTimeMillis()) {
                event.setDamage(event.getDamage() + VOIDSTEP_AMBUSH_DAMAGE);
                applyPotion(victim, PotionEffectType.BLINDNESS, 60, 0);
                applyPotion(victim, PotionEffectType.WEAKNESS, 60, 0);
                setVoidstepVeilUntil(attacker, 0L);
                removeLikelyPowerPotion(attacker, PotionEffectType.INVISIBILITY, 0, VOIDSTEP_INVISIBILITY_SECONDS * 20 + 20);
                removeLikelyPowerPotion(attacker, PotionEffectType.SLOW_FALLING, 0, VOIDSTEP_SLOW_FALLING_SECONDS * 20 + 20);
                victim.getWorld().spawnParticle(Particle.PORTAL, victim.getLocation().clone().add(0.0, 1.0, 0.0), 35, 0.45, 0.45, 0.45, 0.06);
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ENDERMAN_HURT, 0.8f, 0.8f);
            }

            if (hasPower(attacker, SuperpowerType.FROSTBORN)) {
                if (victim.getPotionEffect(PotionEffectType.SLOWNESS) != null) {
                    event.setDamage(event.getDamage() * FROSTBORN_FROZEN_TARGET_DAMAGE_MULTIPLIER);
                }
                applyPotion(victim, PotionEffectType.SLOWNESS, FROSTBORN_CHILL_SECONDS * 20, 0);
                applyPotion(victim, PotionEffectType.WEAKNESS, FROSTBORN_CHILL_SECONDS * 20, 0);
                victim.getWorld().spawnParticle(Particle.CLOUD, victim.getLocation().clone().add(0.0, 1.0, 0.0), 12, 0.35, 0.35, 0.35, 0.02);
            }

            if (event.getDamager() instanceof Projectile projectile
                && hasPower(attacker, SuperpowerType.DEADEYE)) {
                boolean markedShot = projectile.getPersistentDataContainer().has(keyDeadeyeMarkedShot, PersistentDataType.BYTE);
                event.setDamage(event.getDamage() * (markedShot ? DEADEYE_MARKED_SHOT_DAMAGE_MULTIPLIER : DEADEYE_PROJECTILE_DAMAGE_MULTIPLIER));
                applyPotion(victim, PotionEffectType.GLOWING, DEADEYE_GLOW_SECONDS * 20, 0);
                if (markedShot) {
                    applyPotion(victim, PotionEffectType.SLOWNESS, DEADEYE_MARKED_SHOT_SLOW_SECONDS * 20, 1);
                }
                victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().clone().add(0.0, 1.0, 0.0), markedShot ? 28 : 18, 0.35, 0.35, 0.35, 0.05);
            }

            if (hasPower(attacker, SuperpowerType.RIFTWARDEN)) {
                if (isCustomBoss(victim)) {
                    event.setDamage(event.getDamage() * RIFTWARDEN_BOSS_DAMAGE_MULTIPLIER);
                    victim.getWorld().spawnParticle(Particle.REVERSE_PORTAL, victim.getLocation().clone().add(0.0, 1.0, 0.0), 22, 0.5, 0.45, 0.5, 0.08);
                } else if (isHostileMob(victim)) {
                    event.setDamage(event.getDamage() * RIFTWARDEN_MOB_DAMAGE_MULTIPLIER);
                }
            }

            if (hasPower(attacker, SuperpowerType.GRAVEBORN) && isUndeadPassiveType(victim.getType())) {
                event.setDamage(event.getDamage() * GRAVEBORN_UNDEAD_DAMAGE_MULTIPLIER);
                victim.getWorld().spawnParticle(Particle.SOUL, victim.getLocation().clone().add(0.0, 1.0, 0.0), 10, 0.35, 0.35, 0.35, 0.02);
            }

            if (hasPower(attacker, SuperpowerType.STORMCALLER) && tryStormcallerStrike(attacker, victim)) {
                event.setDamage(event.getDamage() + STORMCALLER_DAMAGE_BONUS);
            }

            if (hasPower(attacker, SuperpowerType.BLOODMENDER)) {
                applyBloodmenderLeech(attacker, victim, event.getDamage());
            }
        }

        if (!(victim instanceof Player defender) || source.getUniqueId().equals(defender.getUniqueId()) || isFriendlyTo(defender, source)) {
            return;
        }

        if (hasPower(defender, SuperpowerType.PHOENIX) && event.getDamage() > 0.0) {
            source.setFireTicks(Math.max(source.getFireTicks(), 60));
            applyPotion(defender, PotionEffectType.ABSORPTION, 80, 0);
            source.getWorld().spawnParticle(Particle.FLAME, source.getLocation().clone().add(0.0, 1.0, 0.0), 10, 0.35, 0.35, 0.35, 0.02);
        }

        if (hasPower(defender, SuperpowerType.SENTINEL) && defender.isSneaking() && event.getDamage() > 0.0) {
            event.setDamage(event.getDamage() * (1.0 - SENTINEL_BRACE_DAMAGE_REDUCTION));
            applyPotion(source, PotionEffectType.WEAKNESS, 80, 0);
            Vector push = source.getLocation().toVector().subtract(defender.getLocation().toVector());
            push.setY(0.0);
            if (push.lengthSquared() > 0.001) {
                source.setVelocity(source.getVelocity().add(push.normalize().multiply(0.45).setY(0.18)));
            }
            defender.getWorld().spawnParticle(Particle.CRIT, defender.getLocation().clone().add(0.0, 1.0, 0.0), 16, 0.55, 0.45, 0.55, 0.02);
            defender.getWorld().playSound(defender.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.75f, 0.9f);
        }

        if (hasPower(defender, SuperpowerType.WATERMAN) && isWatermanEmpowered(defender) && event.getDamage() > 0.0) {
            event.setDamage(event.getDamage() * (1.0 - WATERMAN_DAMAGE_REDUCTION));
            applyPotion(source, PotionEffectType.SLOWNESS, 60, 0);
            defender.getWorld().spawnParticle(Particle.BUBBLE_POP, defender.getLocation().clone().add(0.0, 1.0, 0.0), 18, 0.65, 0.4, 0.65, 0.03);
            defender.getWorld().playSound(defender.getLocation(), Sound.BLOCK_CONDUIT_ATTACK_TARGET, 0.45f, 1.4f);
        }

        if (hasPower(defender, SuperpowerType.FROSTBORN) && event.getDamage() > 0.0) {
            applyPotion(source, PotionEffectType.SLOWNESS, FROSTBORN_CHILL_SECONDS * 20, 0);
            source.getWorld().spawnParticle(Particle.CLOUD, source.getLocation().clone().add(0.0, 1.0, 0.0), 10, 0.35, 0.35, 0.35, 0.02);
        }

        if (hasPower(defender, SuperpowerType.RIFTWARDEN) && event.getDamage() > 0.0 && !(source instanceof Player)) {
            double reduction = isCustomBoss(source) ? RIFTWARDEN_BOSS_DAMAGE_REDUCTION : RIFTWARDEN_MOB_DAMAGE_REDUCTION;
            event.setDamage(event.getDamage() * (1.0 - reduction));
            defender.getWorld().spawnParticle(Particle.PORTAL, defender.getLocation().clone().add(0.0, 1.0, 0.0), 14, 0.45, 0.35, 0.45, 0.05);
        }

        if (hasPower(defender, SuperpowerType.GRAVEBORN) && event.getDamage() > 0.0 && isUndeadPassiveType(source.getType())) {
            event.setDamage(event.getDamage() * (1.0 - GRAVEBORN_UNDEAD_DAMAGE_REDUCTION));
            applyPotion(source, PotionEffectType.WEAKNESS, 60, 0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player attackingPlayer = resolvePlayerDamager(event.getDamager());
        if (attackingPlayer != null && timeStoppedPlayers.contains(attackingPlayer.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

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
            ItemStack heartDrop = createWardenHeartItem();
            if (plugin.getItemAuditManager() != null) {
                plugin.getItemAuditManager().recordKnownAcquisition(
                    killer,
                    heartDrop,
                    "warden_drop",
                    "Dropped from a Warden kill."
                );
            }
            event.getDrops().add(heartDrop);
        }

        if (killer != null
            && hasPower(killer, SuperpowerType.ENCHANTER)
            && entity.getType() != EntityType.PLAYER
            && ThreadLocalRandom.current().nextDouble() < ENCHANTER_ESSENCE_EXTRACTION_CHANCE) {
            event.getDrops().add(createEnchanterEssenceDrop());
        }

        if (killer != null && hasPower(killer, SuperpowerType.MONARCH) && isMonarchStorable(entity)) {
            storeMonarchMob(killer, entity.getType());
        }

        if (killer != null && hasPower(killer, SuperpowerType.GRAVEBORN)) {
            double healAmount = entity.getType() == EntityType.PLAYER ? GRAVEBORN_PLAYER_KILL_HEAL : GRAVEBORN_KILL_HEAL;
            healPlayer(killer, healAmount);
            applyPotion(killer, PotionEffectType.REGENERATION, 80, 0);
            killer.getWorld().spawnParticle(Particle.SOUL, killer.getLocation().clone().add(0.0, 1.0, 0.0), 18, 0.45, 0.45, 0.45, 0.03);
        }

        if (killer != null && hasPower(killer, SuperpowerType.RIFTWARDEN) && isCustomBoss(entity)) {
            applyPotion(killer, PotionEffectType.RESISTANCE, 20 * 20, 1);
            applyPotion(killer, PotionEffectType.ABSORPTION, 20 * 20, 1);
            killer.sendMessage(MessageUtil.success("The rift buckles. You gain a brief ward from the fallen boss."));
        }

        if (killer != null && hasPower(killer, SuperpowerType.RUNESMITH) && isCustomBoss(entity)) {
            if (repairRunesmithGear(killer)) {
                killer.sendMessage(MessageUtil.success("Runes in your gear mend themselves from the boss's remains."));
            }
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
        if (event.getTarget() instanceof Player target
            && hasPower(target, SuperpowerType.VOIDWALKER)
            && mob.getType() == EntityType.ENDERMAN) {
            event.setCancelled(true);
            mob.setTarget(null);
            return;
        }
        UUID ownerId = monarchOwnerByMob.get(mob.getUniqueId());
        if (ownerId == null) {
            if (event.getTarget() instanceof Player target
                && hasPower(target, SuperpowerType.MONARCH)
                && isUndeadPassiveType(mob.getType())) {
                event.setCancelled(true);
                mob.setTarget(null);
                return;
            }
            if (event.getTarget() instanceof Player target
                && hasPower(target, SuperpowerType.GRAVEBORN)
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
        if (isInsideActiveTimeStop(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
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
        if (hasPower(player, SuperpowerType.SUPERMAN) && event.isSneaking()) {
            trySupermanBoost(player);
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
    public void onMinerDrops(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.getGameMode() == GameMode.CREATIVE || !hasPower(player, SuperpowerType.MINER)) {
            return;
        }
        if (!MINER_ORE_BLOCKS.contains(event.getBlockState().getType())) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= MINER_EXTRA_ORE_CHANCE) {
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
    public void onTimeStoppedBreak(BlockBreakEvent event) {
        if (timeStoppedPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTimeStoppedPlace(BlockPlaceEvent event) {
        if (timeStoppedPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTimeStoppedProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        if (timeStoppedPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeadeyeProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)
            || !hasPower(player, SuperpowerType.DEADEYE)
            || !(event.getEntity() instanceof org.bukkit.entity.AbstractArrow projectile)) {
            return;
        }
        if (!player.isSneaking()) {
            return;
        }

        projectile.getPersistentDataContainer().set(keyDeadeyeMarkedShot, PersistentDataType.BYTE, (byte) 1);
        projectile.setVelocity(projectile.getVelocity().multiply(DEADEYE_MARKED_SHOT_VELOCITY_MULTIPLIER));
        player.getWorld().spawnParticle(Particle.CRIT, player.getEyeLocation().add(player.getEyeLocation().getDirection()), 10, 0.18, 0.18, 0.18, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.7f, 1.55f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFrozenPlayerInteract(PlayerInteractEvent event) {
        if (!timeStoppedPlayers.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFrozenPlayerDrop(PlayerDropItemEvent event) {
        if (timeStoppedPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDropTheWorldClock(PlayerDropItemEvent event) {
        if (!isTheWorldClock(event.getItemDrop().getItemStack())) {
            return;
        }
        if (!hasPower(event.getPlayer(), SuperpowerType.THE_WORLD)) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(MessageUtil.warn("The World Clock refuses to leave its master."));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDropDruidGrimoire(PlayerDropItemEvent event) {
        if (!isDruidGrimoire(event.getItemDrop().getItemStack())) {
            return;
        }
        if (!hasPower(event.getPlayer(), SuperpowerType.DRUID)) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(MessageUtil.warn("The Druid's Grimoire refuses to leave its keeper."));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTimeStopBlockPhysics(BlockPhysicsEvent event) {
        if (isInsideActiveTimeStop(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTimeStopFluid(BlockFromToEvent event) {
        if (isInsideActiveTimeStop(event.getBlock().getLocation()) || isInsideActiveTimeStop(event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTimeStopBlockFade(BlockFadeEvent event) {
        if (isInsideActiveTimeStop(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTimeStopBlockSpread(BlockSpreadEvent event) {
        if (isInsideActiveTimeStop(event.getBlock().getLocation()) || isInsideActiveTimeStop(event.getSource().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTimeStopRedstone(BlockRedstoneEvent event) {
        if (isInsideActiveTimeStop(event.getBlock().getLocation())) {
            event.setNewCurrent(event.getOldCurrent());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTimeStopPistonExtend(BlockPistonExtendEvent event) {
        if (isInsideActiveTimeStop(event.getBlock().getLocation()) || event.getBlocks().stream().anyMatch(block -> isInsideActiveTimeStop(block.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTimeStopPistonRetract(BlockPistonRetractEvent event) {
        if (isInsideActiveTimeStop(event.getBlock().getLocation()) || event.getBlocks().stream().anyMatch(block -> isInsideActiveTimeStop(block.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
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
        if (isTheWorldClock(item) && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            useTheWorldClock(player);
            return;
        }
        if (isDruidGrimoire(item) && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            openDruidGrimoire(player);
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
        if (!canReceiveCraftResult(player, event)) {
            return;
        }
        if (!consumeAncientScrollIngredients(inventory)) {
            player.sendMessage(MessageUtil.error("The Ancient Scroll recipe ingredients were invalid."));
            return;
        }
        ItemStack result = createAncientScrollItem();
        if (plugin.getItemAuditManager() != null) {
            plugin.getItemAuditManager().recordKnownAcquisition(
                player,
                result,
                "ancient_scroll_craft",
                "Crafted an Ancient Scroll."
            );
        }
        giveCraftResult(player, event, result);

        inventory.setResult(null);
        player.updateInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getFirstItem();
        ItemStack right = event.getInventory().getSecondItem();
        if (!isMotherNatureStick(left) && !isMotherNatureStick(right)
            && !isTheWorldClock(left) && !isTheWorldClock(right)
            && !isDruidGrimoire(left) && !isDruidGrimoire(right)) {
            return;
        }

        ItemStack source = isMotherNatureStick(left) || isTheWorldClock(left) || isDruidGrimoire(left) ? left : right;
        ItemStack result = event.getResult();
        if (source == null || result == null || result.getType() == Material.AIR) {
            return;
        }
        event.setResult(preserveBoundPowerItem(result, source));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (!(event.getInventory() instanceof GrindstoneInventory grindstone)) {
            return;
        }
        ItemStack top = grindstone.getUpperItem();
        ItemStack bottom = grindstone.getLowerItem();
        if (!isMotherNatureStick(top) && !isMotherNatureStick(bottom)
            && !isTheWorldClock(top) && !isTheWorldClock(bottom)
            && !isDruidGrimoire(top) && !isDruidGrimoire(bottom)) {
            return;
        }

        ItemStack source = isMotherNatureStick(top) || isTheWorldClock(top) || isDruidGrimoire(top) ? top : bottom;
        ItemStack result = event.getResult();
        if (source == null || result == null || result.getType() == Material.AIR) {
            return;
        }
        event.setResult(preserveBoundPowerItem(result, source));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPowerMenuClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PowerInfoHolder) {
            event.setCancelled(true);
            if (event.getRawSlot() == 49 && event.getWhoClicked() instanceof Player player) {
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> player.performCommand("menu"));
            }
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof DruidGrimoireHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (!player.getUniqueId().equals(holder.ownerId())) {
                return;
            }
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            DruidBlessing blessing = DruidBlessing.fromSlot(event.getSlot());
            if (blessing == null) {
                return;
            }
            useDruidBlessing(player, blessing);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPowerMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PowerInfoHolder
            || event.getView().getTopInventory().getHolder() instanceof DruidGrimoireHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPowerMenuClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PowerInfoHolder
            || event.getView().getTopInventory().getHolder() instanceof DruidGrimoireHolder) {
            event.getView().getTopInventory().clear();
        }
    }

    private void initializePlayerState(Player player) {
        markVisitedDimension(player, player.getWorld());
        SuperpowerType power = ensurePowerAssigned(player);
        if (power == SuperpowerType.FLORIST) {
            trimExtraMotherNatureSticks(player);
            if (!hasMotherNatureStick(player)) {
                giveMotherNatureStick(player, false);
            }
        }
        if (power == SuperpowerType.THE_WORLD && !hasTheWorldClock(player)) {
            giveTheWorldClock(player, false);
        }
        if (power == SuperpowerType.DRUID && !hasDruidGrimoire(player)) {
            giveDruidGrimoire(player, false);
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
            if (xrayActiveUntil(player) <= now) {
                setXrayActiveUntil(player, 0L);
            }
            if (supermanFlightActiveUntil(player) > 0L && supermanFlightActiveUntil(player) <= now) {
                stopSupermanFlight(player, true);
            }
            applyPassiveEffects(player);
        }
        cleanupRecentPortalTravel(now);
    }

    private void applyPassiveEffects(Player player) {
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            syncTankImmovableState(player, false);
            clearPowerAttributeModifiers(player);
            return;
        }

        SuperpowerType power = powerOf(player);
        if (power == null) {
            clearPowerAttributeModifiers(player);
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
                syncEnchanterAttunement(player);
                syncPlayerAttributeModifier(player, Attribute.LUCK, keyEnchanterLuckModifier, ENCHANTER_LUCK_BONUS, AttributeModifier.Operation.ADD_NUMBER, true);
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
                trimExtraMotherNatureSticks(player);
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
            case THE_WORLD -> {
                if (!hasTheWorldClock(player)) {
                    giveTheWorldClock(player, false);
                }
            }
            case DRUID -> {
                if (!hasDruidGrimoire(player)) {
                    giveDruidGrimoire(player, false);
                }
            }
            case XRAY_VISION -> {
                if (xrayActiveUntil(player) > System.currentTimeMillis()) {
                    renderXrayHighlights(player);
                }
            }
            case MINER -> {
                applyPotion(player, PotionEffectType.HASTE, 80, 2);
                syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keyMinerHealthModifier, MINER_HEALTH_BONUS, AttributeModifier.Operation.ADD_NUMBER, true);
            }
            case GIANT -> {
                syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keyGiantHealthModifier, GIANT_HEALTH_BONUS, AttributeModifier.Operation.ADD_NUMBER, true);
                syncPlayerAttributeModifier(player, Attribute.SCALE, keyGiantScaleModifier, GIANT_SCALE_MULTIPLIER - 1.0, AttributeModifier.Operation.ADD_SCALAR, true);
                syncPlayerAttributeModifier(player, Attribute.KNOCKBACK_RESISTANCE, keyGiantKnockbackModifier, GIANT_KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_NUMBER, true);
                syncPlayerAttributeModifier(player, Attribute.ATTACK_DAMAGE, keyGiantAttackDamageModifier, GIANT_ATTACK_DAMAGE_BONUS, AttributeModifier.Operation.ADD_NUMBER, true);
            }
            case SUPERMAN -> {
                applyPotion(player, PotionEffectType.STRENGTH, 80, 1);
                applyPotion(player, PotionEffectType.SPEED, 80, 0);
                syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keySupermanHealthModifier, SUPERMAN_HEALTH_BONUS, AttributeModifier.Operation.ADD_NUMBER, true);
                syncSupermanFlightState(player, true);
            }
            case WATERMAN -> {
                syncPlayerAttributeModifier(player, Attribute.SUBMERGED_MINING_SPEED, keyWatermanSubmergedMiningModifier, WATERMAN_SUBMERGED_MINING_BONUS, AttributeModifier.Operation.ADD_NUMBER, true);
                player.setRemainingAir(player.getMaximumAir());
                applyPotion(player, PotionEffectType.WATER_BREATHING, 80, 0);
                if (isWatermanEmpowered(player)) {
                    applyPassiveNightVision(player);
                    applyPotion(player, PotionEffectType.CONDUIT_POWER, 80, 0);
                    applyPotion(player, PotionEffectType.DOLPHINS_GRACE, 80, 0);
                    applyPotion(player, PotionEffectType.HASTE, 80, 1);
                    applyPotion(player, PotionEffectType.SPEED, 80, 1);
                    applyPotion(player, PotionEffectType.STRENGTH, 80, 0);
                    applyPotion(player, PotionEffectType.RESISTANCE, 80, 0);
                    applyPotion(player, PotionEffectType.REGENERATION, 80, 0);
                } else {
                    removePassiveNightVision(player);
                    removeWatermanWaterBuffs(player);
                }
            }
            case PHOENIX -> {
                applyPotion(player, PotionEffectType.FIRE_RESISTANCE, 80, 0);
                var phoenixHealth = player.getAttribute(Attribute.MAX_HEALTH);
                double healthCap = phoenixHealth == null ? 20.0 : phoenixHealth.getValue();
                if (healthCap > 0.0 && player.getHealth() <= healthCap * PHOENIX_LOW_HEALTH_RATIO) {
                    applyPotion(player, PotionEffectType.SPEED, 60, 0);
                    applyPotion(player, PotionEffectType.REGENERATION, 60, 0);
                }
            }
            case VOIDWALKER -> {
                if (isVoidwalkerNightVisionEnabled(player)) {
                    applyPassiveNightVision(player);
                } else {
                    removePassiveNightVision(player);
                }
                if (voidstepVeilUntil(player) > System.currentTimeMillis()) {
                    applyPotion(player, PotionEffectType.SLOW_FALLING, 60, 0);
                }
            }
            case SENTINEL -> {
                syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keySentinelHealthModifier, SENTINEL_HEALTH_BONUS, AttributeModifier.Operation.ADD_NUMBER, true);
                applySentinelAura(player);
            }
            case FROSTBORN -> {
                applyPotion(player, PotionEffectType.WATER_BREATHING, 80, 0);
                if (isStandingOnFrostBlock(player)) {
                    applyPotion(player, PotionEffectType.SPEED, 60, 0);
                }
            }
            case DEADEYE -> {
                applyPassiveNightVision(player);
                applyPotion(player, PotionEffectType.SPEED, 80, 0);
            }
            case RIFTWARDEN -> {
                boolean nearBoss = hasNearbyCustomBoss(player, RIFTWARDEN_BOSS_RADIUS);
                boolean wardedArea = nearBoss || player.getWorld().getEnvironment() == World.Environment.THE_END;
                if (nearBoss) {
                    applyPotion(player, PotionEffectType.SLOW_FALLING, 80, 0);
                } else {
                    removeLikelyPowerPotion(player, PotionEffectType.SLOW_FALLING, 0);
                }
                if (wardedArea) {
                    applyPotion(player, PotionEffectType.RESISTANCE, 80, 0);
                } else {
                    removeLikelyPowerPotion(player, PotionEffectType.RESISTANCE, 0);
                }
            }
            case OATHBOUND -> applyOathboundAura(player);
            case RUNESMITH -> applyPotion(player, PotionEffectType.HASTE, 80, 0);
            case GRAVEBORN -> {
                pacifyNearbyUndead(player);
                player.removePotionEffect(PotionEffectType.WITHER);
                player.removePotionEffect(PotionEffectType.POISON);
                applyPassiveNightVision(player);
            }
            case STORMCALLER -> {
                applyPotion(player, PotionEffectType.SPEED, 80, 0);
                if (isStorming(player.getWorld())) {
                    applyPotion(player, PotionEffectType.HASTE, 80, 1);
                    applyPotion(player, PotionEffectType.STRENGTH, 80, 0);
                }
            }
            case BLOODMENDER -> {
                var bloodmenderHealth = player.getAttribute(Attribute.MAX_HEALTH);
                double healthCap = bloodmenderHealth == null ? 20.0 : bloodmenderHealth.getValue();
                if (healthCap > 0.0 && player.getHealth() <= healthCap * 0.35) {
                    applyPotion(player, PotionEffectType.REGENERATION, 60, 0);
                }
            }
            default -> {
            }
        }

        clearInactivePowerAttributeModifiers(player, power);

        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null && player.getHealth() > maxHealth.getValue()) {
            player.setHealth(maxHealth.getValue());
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

    private void applyPotion(LivingEntity entity, PotionEffectType type, int durationTicks, int amplifier) {
        PotionEffect current = entity.getPotionEffect(type);
        if (current != null && current.getAmplifier() > amplifier && current.getDuration() > 20) {
            return;
        }
        if (current != null && current.getAmplifier() == amplifier && current.getDuration() > (durationTicks / 2)) {
            return;
        }
        entity.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, true, false, false));
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
        if (power == SuperpowerType.FLORIST) {
            trimExtraMotherNatureSticks(player);
            if (!hasMotherNatureStick(player)) {
                giveMotherNatureStick(player, false);
            }
        }
        if (power == SuperpowerType.THE_WORLD && !hasTheWorldClock(player)) {
            giveTheWorldClock(player, false);
        }
        if (power == SuperpowerType.DRUID && !hasDruidGrimoire(player)) {
            giveDruidGrimoire(player, false);
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
        pendingTheWorldClockReturns.remove(playerId);
        pendingDruidGrimoireReturns.remove(playerId);
        closeTravelerPortal(playerId, false);
        despawnMonarchSummons(playerId);
        clearTimeStopForOwner(playerId);
        stopSupermanFlight(player, false);

        if (isShadowActive(player)) {
            removeLikelyPowerPotion(player, PotionEffectType.INVISIBILITY, 0);
            removeLikelyPowerPotion(player, PotionEffectType.SPEED, 2);
            restoreShadowAppearance(player);
        }

        clearLikelyPassivePowerEffects(player);
        clearPowerCooldownState(player);
        removePassiveNightVision(player);

        if (nextPower != SuperpowerType.FLORIST) {
            removeMotherNatureSticks(player);
        }
        if (nextPower != SuperpowerType.THE_WORLD) {
            removeTheWorldClocks(player);
        }
        if (nextPower != SuperpowerType.DRUID) {
            removeDruidGrimoires(player);
        }
        syncTankImmovableState(player, false);
        clearPowerAttributeModifiers(player);
        clearVirtualEnchanterLapis(player);
    }

    private void clearPowerCooldownState(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.remove(keyXrayActiveUntil);
        pdc.remove(keyXrayCooldownUntil);
        pdc.remove(keyTimeStopCooldownUntil);
        pdc.remove(keyPhoenixCooldownUntil);
        pdc.remove(keyVoidstepCooldownUntil);
        pdc.remove(keyVoidstepVeilUntil);
        pdc.remove(keyShadowCooldownUntil);
        pdc.remove(keyShadowActiveUntil);
        pdc.remove(keyDruidBuffCooldownUntil);
        pdc.remove(keySupermanFlightActiveUntil);
        pdc.remove(keySupermanFlightCooldownUntil);
        pdc.remove(keyStormcallerLightningEnabled);
    }

    private void clearLikelyPassivePowerEffects(Player player) {
        removeLikelyPowerPotion(player, PotionEffectType.SPEED, 0);
        removeLikelyPowerPotion(player, PotionEffectType.SPEED, 1);
        removeLikelyPowerPotion(player, PotionEffectType.SPEED, 2);
        removeLikelyPowerPotion(player, PotionEffectType.HASTE, 2);
        removeLikelyPowerPotion(player, PotionEffectType.HASTE, 1);
        removeLikelyPowerPotion(player, PotionEffectType.HASTE, 0);
        removeLikelyPowerPotion(player, PotionEffectType.STRENGTH, 1);
        removeLikelyPowerPotion(player, PotionEffectType.STRENGTH, 0);
        removeLikelyPowerPotion(player, PotionEffectType.HEALTH_BOOST, 4);
        removeLikelyPowerPotion(player, PotionEffectType.REGENERATION, 0);
        removeLikelyPowerPotion(player, PotionEffectType.FIRE_RESISTANCE, 0);
        removeLikelyPowerPotion(player, PotionEffectType.RESISTANCE, 0);
        removeLikelyPowerPotion(player, PotionEffectType.SLOWNESS, 0);
        removeLikelyPowerPotion(player, PotionEffectType.WATER_BREATHING, 0);
        removeLikelyPowerPotion(player, PotionEffectType.CONDUIT_POWER, 0);
        removeLikelyPowerPotion(player, PotionEffectType.DOLPHINS_GRACE, 0);
        removeLikelyPowerPotion(player, PotionEffectType.NIGHT_VISION, 0);
        removeLikelyPowerPotion(player, PotionEffectType.SLOW_FALLING, 0);
        removeLikelyPowerPotion(player, PotionEffectType.INVISIBILITY, 0);
        removeLikelyPowerPotion(player, PotionEffectType.ABSORPTION, 0);

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
        } else if (parsed != null && !parsed.name().equals(raw)) {
            pdc.set(keyPowerType, PersistentDataType.STRING, parsed.name());
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

    private boolean tryPhoenixRebirth(Player player, EntityDamageEvent.DamageCause cause) {
        long now = System.currentTimeMillis();
        long cooldownUntil = phoenixCooldownUntil(player);
        if (cooldownUntil > now) {
            return false;
        }

        setPhoenixCooldownUntil(player, now + (PHOENIX_COOLDOWN_SECONDS * 1000L));
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double healthCap = maxHealth == null ? 20.0 : maxHealth.getValue();
        player.setHealth(Math.min(healthCap, PHOENIX_RECOVERY_HEALTH));
        player.setFireTicks(0);
        applyPotion(player, PotionEffectType.REGENERATION, 8 * 20, 2);
        applyPotion(player, PotionEffectType.FIRE_RESISTANCE, 30 * 20, 0);
        applyPotion(player, PotionEffectType.ABSORPTION, 8 * 20, 1);

        if (cause == EntityDamageEvent.DamageCause.VOID) {
            Location rescue = safeRespawnLikeLocation(player);
            if (rescue != null) {
                player.teleportAsync(rescue);
            }
        }

        World world = player.getWorld();
        Location center = player.getLocation().clone().add(0.0, 1.0, 0.0);
        world.spawnParticle(Particle.FLAME, center, 80, 1.2, 0.8, 1.2, 0.05);
        world.spawnParticle(Particle.LAVA, center, 18, 0.75, 0.55, 0.75, 0.02);
        world.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.65f);
        world.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.65f, 1.35f);

        for (Entity nearby : world.getNearbyEntities(player.getLocation(), PHOENIX_BURST_RADIUS, PHOENIX_BURST_RADIUS, PHOENIX_BURST_RADIUS)) {
            if (!(nearby instanceof LivingEntity living) || isFriendlyTo(player, nearby)) {
                continue;
            }
            living.setFireTicks(Math.max(living.getFireTicks(), 80));
            living.damage(PHOENIX_BURST_DAMAGE, player);
        }
        player.sendMessage(MessageUtil.success("Your Ashen Soul pulls you back from death."));
        return true;
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

    private long druidBuffCooldownUntil(Player player) {
        return player.getPersistentDataContainer().getOrDefault(keyDruidBuffCooldownUntil, PersistentDataType.LONG, 0L);
    }

    private long phoenixCooldownUntil(Player player) {
        return player.getPersistentDataContainer().getOrDefault(keyPhoenixCooldownUntil, PersistentDataType.LONG, 0L);
    }

    private long voidstepCooldownUntil(Player player) {
        return player.getPersistentDataContainer().getOrDefault(keyVoidstepCooldownUntil, PersistentDataType.LONG, 0L);
    }

    private long voidstepVeilUntil(Player player) {
        return player.getPersistentDataContainer().getOrDefault(keyVoidstepVeilUntil, PersistentDataType.LONG, 0L);
    }

    private boolean isVoidwalkerNightVisionEnabled(Player player) {
        Byte raw = player.getPersistentDataContainer().get(keyVoidwalkerNightVisionEnabled, PersistentDataType.BYTE);
        return raw == null || raw != 0;
    }

    private boolean isStormcallerLightningEnabled(Player player) {
        Byte raw = player.getPersistentDataContainer().get(keyStormcallerLightningEnabled, PersistentDataType.BYTE);
        return raw == null || raw != 0;
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

    private void setPhoenixCooldownUntil(Player player, long value) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (value <= 0L) {
            pdc.remove(keyPhoenixCooldownUntil);
            return;
        }
        pdc.set(keyPhoenixCooldownUntil, PersistentDataType.LONG, value);
    }

    private void setVoidstepCooldownUntil(Player player, long value) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (value <= 0L) {
            pdc.remove(keyVoidstepCooldownUntil);
            return;
        }
        pdc.set(keyVoidstepCooldownUntil, PersistentDataType.LONG, value);
    }

    private void setVoidstepVeilUntil(Player player, long value) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (value <= 0L) {
            pdc.remove(keyVoidstepVeilUntil);
            return;
        }
        pdc.set(keyVoidstepVeilUntil, PersistentDataType.LONG, value);
    }

    private void setVoidwalkerNightVisionEnabled(Player player, boolean enabled) {
        player.getPersistentDataContainer().set(keyVoidwalkerNightVisionEnabled, PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
    }

    private void setStormcallerLightningEnabled(Player player, boolean enabled) {
        player.getPersistentDataContainer().set(keyStormcallerLightningEnabled, PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
    }

    private void setDruidBuffCooldownUntil(Player player, long value) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (value <= 0L) {
            pdc.remove(keyDruidBuffCooldownUntil);
            return;
        }
        pdc.set(keyDruidBuffCooldownUntil, PersistentDataType.LONG, value);
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
        removeLikelyPowerPotion(player, type, amplifier, 80);
    }

    private void removeLikelyPowerPotion(Player player, PotionEffectType type, int amplifier, int maxDurationTicks) {
        PotionEffect current = player.getPotionEffect(type);
        if (current != null && current.getAmplifier() == amplifier && current.getDuration() <= maxDurationTicks) {
            player.removePotionEffect(type);
        }
    }

    private void applyPassiveNightVision(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PASSIVE_NIGHT_VISION_TICKS, 0, true, false, false));
    }

    private void removePassiveNightVision(Player player) {
        PotionEffect current = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        if (current != null && current.getAmplifier() == 0 && current.getDuration() <= PASSIVE_NIGHT_VISION_TICKS + 40) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    private void removeWatermanWaterBuffs(Player player) {
        removeLikelyPowerPotion(player, PotionEffectType.CONDUIT_POWER, 0);
        removeLikelyPowerPotion(player, PotionEffectType.DOLPHINS_GRACE, 0);
        removeLikelyPowerPotion(player, PotionEffectType.HASTE, 1);
        removeLikelyPowerPotion(player, PotionEffectType.SPEED, 1);
        removeLikelyPowerPotion(player, PotionEffectType.STRENGTH, 0);
        removeLikelyPowerPotion(player, PotionEffectType.RESISTANCE, 0);
        removeLikelyPowerPotion(player, PotionEffectType.REGENERATION, 0);
    }

    private int secondsLeft(long future, long now) {
        return Math.max(1, (int) Math.ceil((future - now) / 1000.0));
    }

    private void useAncientScroll(Player player) {
        SuperpowerType currentPower = powerOf(player);
        SuperpowerType rerolled = currentPower == null
            ? randomPower(false)
            : randomDifferentPower(currentPower, false);

        if (currentPower != null && rerolled == currentPower) {
            player.sendMessage(MessageUtil.error("The Ancient Scroll resisted the reroll. Try again."));
            return;
        }

        consumeHeldItem(player);
        assignPower(player, rerolled, true, true);
        player.sendMessage(MessageUtil.success("The Ancient Scroll rewrites your fate."));
    }

    private void openDruidGrimoire(Player player) {
        if (!hasPower(player, SuperpowerType.DRUID)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return;
        }

        Inventory inventory = Bukkit.createInventory(
            new DruidGrimoireHolder(player.getUniqueId()),
            DRUID_MENU_SIZE,
            BedrockCompat.menuTitle(player, DRUID_MENU_TITLE, "Druid's Grimoire")
        );
        fillDruidMenu(inventory);
        player.openInventory(inventory);
    }

    private void fillDruidMenu(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, fillerPane());
        }
        inventory.setItem(4, createDruidHeaderIcon());
        for (DruidBlessing blessing : DruidBlessing.values()) {
            inventory.setItem(blessing.slot(), createDruidBlessingIcon(blessing));
        }
    }

    private ItemStack createDruidHeaderIcon() {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(MM.deserialize("<gradient:#63c74d:#c6ff6b><bold>Druid's Grimoire</bold></gradient>"));
        meta.lore(List.of(
            MM.deserialize("<gray>Choose one blessing for yourself and nearby teammates.</gray>"),
            MM.deserialize("<gray>Only one blessing is cast per use.</gray>"),
            Component.empty(),
            MM.deserialize("<gray>Radius: <white>" + DRUID_BUFF_RADIUS + " blocks</white></gray>"),
            MM.deserialize("<gray>Duration: <white>" + DRUID_BUFF_DURATION_SECONDS + "s</white></gray>")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDruidBlessingIcon(DruidBlessing blessing) {
        ItemStack item = new ItemStack(blessing.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(MM.deserialize(blessing.display()));
        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<gray>Bless yourself and nearby teammates within <white>" + DRUID_BUFF_RADIUS + " blocks</white>.</gray>"));
        lore.add(MM.deserialize(blessing.lore()));
        lore.add(Component.empty());
        lore.add(MM.deserialize("<gray>Duration: <white>" + blessing.durationText() + "</white></gray>"));
        lore.add(MM.deserialize("<gray>Cooldown: <white>" + DRUID_BUFF_COOLDOWN_SECONDS + "s</white></gray>"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void useDruidBlessing(Player player, DruidBlessing blessing) {
        if (!hasPower(player, SuperpowerType.DRUID)) {
            player.closeInventory();
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownUntil = druidBuffCooldownUntil(player);
        if (cooldownUntil > now) {
            player.sendMessage(MessageUtil.warn(
                "Druid blessing cooldown: <white>" + secondsLeft(cooldownUntil, now) + "s</white>."
            ));
            player.closeInventory();
            return;
        }

        List<Player> targets = nearbyDruidTargets(player);
        if (targets.isEmpty()) {
            player.sendMessage(MessageUtil.warn("No one is close enough to receive that blessing."));
            player.closeInventory();
            return;
        }

        for (Player target : targets) {
            blessing.apply(this, target);
            Location effectCenter = target.getLocation().clone().add(0.0, 1.0, 0.0);
            target.getWorld().strikeLightningEffect(target.getLocation());
            target.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, effectCenter, 10, 0.3, 0.4, 0.3, 0.02);
            target.getWorld().spawnParticle(blessing.particle(), effectCenter, 12, 0.25, 0.35, 0.25, 0.01);
            target.playSound(target.getLocation(), blessing.sound(), 0.8f, 1.1f);
            if (!target.equals(player)) {
                target.sendMessage(MessageUtil.success("The druid blessed you with <white>" + blessing.plainName() + "</white>."));
            }
        }

        setDruidBuffCooldownUntil(player, now + (DRUID_BUFF_COOLDOWN_SECONDS * 1000L));
        player.closeInventory();
        player.sendMessage(MessageUtil.success(
            "You channeled <white>" + blessing.plainName() + "</white> into <white>" + targets.size() + "</white> ally(s), including yourself."
        ));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.95f, 0.85f);
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0.0, 1.2, 0.0), 32, 0.6, 0.8, 0.6, 0.02);
    }

    private List<Player> nearbyDruidTargets(Player player) {
        double radiusSquared = DRUID_BUFF_RADIUS * DRUID_BUFF_RADIUS;
        List<Player> targets = new ArrayList<>();
        targets.add(player);
        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.equals(player)) {
                continue;
            }
            if (nearby.isDead() || nearby.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (player.getLocation().distanceSquared(nearby.getLocation()) > radiusSquared) {
                continue;
            }
            if (!sameTeamOrSelf(player.getUniqueId(), nearby.getUniqueId())) {
                continue;
            }
            targets.add(nearby);
        }
        return targets;
    }

    private void applySentinelAura(Player player) {
        if (!player.isSneaking()) {
            return;
        }

        double radiusSquared = SENTINEL_AURA_RADIUS * SENTINEL_AURA_RADIUS;
        applyPotion(player, PotionEffectType.SLOWNESS, 45, 0);
        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.isDead() || nearby.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (nearby.getLocation().distanceSquared(player.getLocation()) > radiusSquared) {
                continue;
            }
            if (!sameTeamOrSelf(player.getUniqueId(), nearby.getUniqueId())) {
                continue;
            }
            applyPotion(nearby, PotionEffectType.RESISTANCE, 60, 0);
            applyPotion(nearby, PotionEffectType.ABSORPTION, 60, 0);
        }
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().clone().add(0.0, 1.0, 0.0), 8, 0.8, 0.35, 0.8, 0.01);
    }

    private void applyOathboundAura(Player player) {
        double radiusSquared = OATHBOUND_AURA_RADIUS * OATHBOUND_AURA_RADIUS;
        List<Player> allies = new ArrayList<>();
        allies.add(player);
        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.equals(player) || nearby.isDead() || nearby.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (nearby.getLocation().distanceSquared(player.getLocation()) > radiusSquared) {
                continue;
            }
            if (!sameTeamOrSelf(player.getUniqueId(), nearby.getUniqueId())) {
                continue;
            }
            allies.add(nearby);
        }

        if (allies.size() <= 1) {
            return;
        }
        for (Player ally : allies) {
            applyPotion(ally, PotionEffectType.SPEED, 60, 0);
        }
        applyPotion(player, PotionEffectType.RESISTANCE, 60, 0);
        applyPotion(player, PotionEffectType.ABSORPTION, 60, 0);
        player.getWorld().spawnParticle(Particle.WAX_ON, player.getLocation().clone().add(0.0, 1.0, 0.0), 10, 0.7, 0.4, 0.7, 0.01);
    }

    private boolean hasNearbyCustomBoss(Player player, double radius) {
        double radiusSquared = radius * radius;
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius)) {
            if (entity.getLocation().distanceSquared(player.getLocation()) <= radiusSquared && isCustomBoss(entity)) {
                return true;
            }
        }
        return false;
    }

    private boolean isCustomBoss(Entity entity) {
        BossManager bossManager = plugin.getBossManager();
        return bossManager != null && bossManager.isCustomBoss(entity);
    }

    private boolean isHostileMob(LivingEntity entity) {
        return entity instanceof org.bukkit.entity.Monster;
    }

    private boolean isStorming(World world) {
        return world != null && (world.hasStorm() || world.isThundering());
    }

    private boolean tryStormcallerStrike(Player attacker, LivingEntity victim) {
        if (!isStormcallerLightningEnabled(attacker)) {
            return false;
        }
        double chance = isStorming(attacker.getWorld()) ? STORMCALLER_STORM_PROC_CHANCE : STORMCALLER_PROC_CHANCE;
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return false;
        }
        Location center = victim.getLocation().clone().add(0.0, 1.0, 0.0);
        victim.getWorld().strikeLightningEffect(victim.getLocation());
        victim.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center, 28, 0.45, 0.55, 0.45, 0.08);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.45f, 1.7f);
        return true;
    }

    private void applyBloodmenderLeech(Player attacker, LivingEntity victim, double damage) {
        if (damage <= 0.0 || attacker.getHealth() <= 0.0) {
            return;
        }
        double ratio = victim instanceof Player ? BLOODMENDER_PLAYER_LEECH_RATIO : BLOODMENDER_MOB_LEECH_RATIO;
        double healAmount = Math.min(BLOODMENDER_MAX_LEECH, damage * ratio);
        if (healAmount <= 0.0) {
            return;
        }
        healPlayer(attacker, healAmount);
        attacker.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, attacker.getLocation().clone().add(0.0, 1.0, 0.0), 6, 0.25, 0.25, 0.25, 0.02);
    }

    private void healPlayer(Player player, double amount) {
        if (player == null || amount <= 0.0 || player.isDead()) {
            return;
        }
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double healthCap = maxHealth == null ? 20.0 : maxHealth.getValue();
        player.setHealth(Math.min(healthCap, player.getHealth() + amount));
    }

    private boolean repairRunesmithGear(Player player) {
        PlayerInventory inventory = player.getInventory();
        boolean repaired = repairDurableItem(inventory.getItemInMainHand());
        repaired |= repairDurableItem(inventory.getItemInOffHand());
        for (ItemStack armor : inventory.getArmorContents()) {
            repaired |= repairDurableItem(armor);
        }
        return repaired;
    }

    private boolean repairDurableItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getType().getMaxDurability() <= 0) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable) || damageable.getDamage() <= 0) {
            return false;
        }
        int repairAmount = Math.max(1, (int) Math.ceil(item.getType().getMaxDurability() * RUNESMITH_BOSS_REPAIR_RATIO));
        damageable.setDamage(Math.max(0, damageable.getDamage() - repairAmount));
        item.setItemMeta(meta);
        return true;
    }

    private boolean isSpecialCraftedItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        if (plugin.getLegendaryListener() != null && plugin.getLegendaryListener().isLegendaryItem(item)) {
            return true;
        }
        if (plugin.getCustomToolListener() != null && plugin.getCustomToolListener().isCustomTool(item)) {
            return true;
        }
        if (plugin.getSeasonRelicManager() != null && plugin.getSeasonRelicManager().isSeasonRelic(item)) {
            return true;
        }
        return isAncientScroll(item)
            || isWardenHeart(item)
            || isMotherNatureStick(item)
            || isTheWorldClock(item)
            || isDruidGrimoire(item)
            || isTalisman(item);
    }

    private boolean isStandingOnFrostBlock(Player player) {
        Material below = player.getLocation().clone().subtract(0.0, 0.1, 0.0).getBlock().getType();
        return switch (below) {
            case ICE, PACKED_ICE, BLUE_ICE, FROSTED_ICE, SNOW, SNOW_BLOCK, POWDER_SNOW -> true;
            default -> false;
        };
    }

    private boolean isWatermanEmpowered(Player player) {
        return player.isInWater() || player.isUnderWater();
    }

    private Location findVoidstepDestination(Player player) {
        Location origin = player.getLocation();
        Vector direction = player.getEyeLocation().getDirection();
        if (direction.lengthSquared() <= 0.0) {
            return null;
        }
        direction.normalize();

        for (double distance = VOIDSTEP_RANGE; distance >= 2.0; distance -= 0.5) {
            Location target = origin.clone().add(direction.clone().multiply(distance));
            target.setYaw(origin.getYaw());
            target.setPitch(origin.getPitch());
            Location safe = findSafeTravelLocation(target);
            if (safe == null || safe.getWorld() == null || !safe.getWorld().equals(origin.getWorld())) {
                continue;
            }
            if (safe.distanceSquared(origin) > (VOIDSTEP_RANGE + 3.0) * (VOIDSTEP_RANGE + 3.0)) {
                continue;
            }
            if (isVoidstepPathClear(origin, safe)) {
                return safe;
            }
        }
        return null;
    }

    private boolean isVoidstepPathClear(Location origin, Location destination) {
        if (origin == null || destination == null || origin.getWorld() == null || !origin.getWorld().equals(destination.getWorld())) {
            return false;
        }

        Vector start = origin.clone().add(0.0, 1.0, 0.0).toVector();
        Vector end = destination.clone().add(0.0, 1.0, 0.0).toVector();
        Vector delta = end.clone().subtract(start);
        double length = delta.length();
        if (length <= 0.1) {
            return true;
        }

        Vector step = delta.normalize().multiply(0.5);
        Vector cursor = start.clone();
        for (double walked = 0.0; walked <= length; walked += 0.5) {
            Location sample = cursor.toLocation(origin.getWorld());
            if (!sample.getBlock().isPassable() || !sample.clone().add(0.0, 1.0, 0.0).getBlock().isPassable()) {
                return false;
            }
            cursor.add(step);
        }
        return true;
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

    private boolean isFriendlyTo(Player owner, Entity target) {
        if (owner == null || target == null) {
            return false;
        }
        if (owner.getUniqueId().equals(target.getUniqueId())) {
            return true;
        }
        if (target instanceof Player player) {
            return sameTeamOrSelf(owner.getUniqueId(), player.getUniqueId());
        }
        if (target instanceof Tameable tameable && tameable.getOwner() != null) {
            return sameTeamOrSelf(owner.getUniqueId(), tameable.getOwner().getUniqueId());
        }
        return false;
    }

    private Player resolvePlayerDamager(Entity damager) {
        LivingEntity attacker = actualLivingDamager(damager);
        return attacker instanceof Player player ? player : null;
    }

    private void syncEnchanterAttunement(Player player) {
        int enchantLevels = totalEnchantLevels(player.getInventory().getItemInMainHand());
        double scalar = enchantLevels * 0.01;
        syncPlayerAttributeModifier(
            player,
            Attribute.MOVEMENT_SPEED,
            keyEnchanterMoveModifier,
            scalar,
            AttributeModifier.Operation.ADD_SCALAR,
            scalar > 0.0
        );
        syncPlayerAttributeModifier(
            player,
            Attribute.ATTACK_SPEED,
            keyEnchanterAttackModifier,
            scalar,
            AttributeModifier.Operation.ADD_SCALAR,
            scalar > 0.0
        );
    }

    private int totalEnchantLevels(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return 0;
        }
        int total = 0;
        for (int level : item.getEnchantments().values()) {
            total += Math.max(0, level);
        }
        if (item.getItemMeta() instanceof EnchantmentStorageMeta storageMeta) {
            for (int level : storageMeta.getStoredEnchants().values()) {
                total += Math.max(0, level);
            }
        }
        return total;
    }

    private void syncPlayerAttributeModifier(
        Player player,
        Attribute attribute,
        NamespacedKey key,
        double amount,
        AttributeModifier.Operation operation,
        boolean enabled
    ) {
        if (player == null || attribute == null || key == null) {
            return;
        }

        var instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }

        AttributeModifier existing = instance.getModifier(key);
        if (enabled && Math.abs(amount) > 1.0E-9 && existing != null
            && Math.abs(existing.getAmount() - amount) <= 1.0E-9
            && existing.getOperation() == operation) {
            return;
        }

        instance.removeModifier(key);
        if (!enabled || Math.abs(amount) <= 1.0E-9) {
            return;
        }
        instance.addTransientModifier(new AttributeModifier(key, amount, operation));
    }

    private void clearInactivePowerAttributeModifiers(Player player, SuperpowerType power) {
        if (power != SuperpowerType.ENCHANTER) {
            syncPlayerAttributeModifier(player, Attribute.LUCK, keyEnchanterLuckModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
            syncPlayerAttributeModifier(player, Attribute.MOVEMENT_SPEED, keyEnchanterMoveModifier, 0.0, AttributeModifier.Operation.ADD_SCALAR, false);
            syncPlayerAttributeModifier(player, Attribute.ATTACK_SPEED, keyEnchanterAttackModifier, 0.0, AttributeModifier.Operation.ADD_SCALAR, false);
        }
        if (power != SuperpowerType.MINER) {
            syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keyMinerHealthModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
        }
        if (power != SuperpowerType.GIANT) {
            syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keyGiantHealthModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
            syncPlayerAttributeModifier(player, Attribute.SCALE, keyGiantScaleModifier, 0.0, AttributeModifier.Operation.ADD_SCALAR, false);
            syncPlayerAttributeModifier(player, Attribute.KNOCKBACK_RESISTANCE, keyGiantKnockbackModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
            syncPlayerAttributeModifier(player, Attribute.ATTACK_DAMAGE, keyGiantAttackDamageModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
        }
        if (power != SuperpowerType.SUPERMAN) {
            syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keySupermanHealthModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
        }
        if (power != SuperpowerType.SENTINEL) {
            syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keySentinelHealthModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
        }
        if (power != SuperpowerType.WATERMAN) {
            syncPlayerAttributeModifier(player, Attribute.SUBMERGED_MINING_SPEED, keyWatermanSubmergedMiningModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
        }
    }

    private void clearPowerAttributeModifiers(Player player) {
        syncPlayerAttributeModifier(player, Attribute.LUCK, keyEnchanterLuckModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
        syncPlayerAttributeModifier(player, Attribute.MOVEMENT_SPEED, keyEnchanterMoveModifier, 0.0, AttributeModifier.Operation.ADD_SCALAR, false);
        syncPlayerAttributeModifier(player, Attribute.ATTACK_SPEED, keyEnchanterAttackModifier, 0.0, AttributeModifier.Operation.ADD_SCALAR, false);
        syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keyMinerHealthModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
        syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keyGiantHealthModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
        syncPlayerAttributeModifier(player, Attribute.SCALE, keyGiantScaleModifier, 0.0, AttributeModifier.Operation.ADD_SCALAR, false);
        syncPlayerAttributeModifier(player, Attribute.KNOCKBACK_RESISTANCE, keyGiantKnockbackModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
        syncPlayerAttributeModifier(player, Attribute.ATTACK_DAMAGE, keyGiantAttackDamageModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
        syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keySupermanHealthModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
        syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keySentinelHealthModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
        syncPlayerAttributeModifier(player, Attribute.SUBMERGED_MINING_SPEED, keyWatermanSubmergedMiningModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
    }

    private long xrayActiveUntil(Player player) {
        return player.getPersistentDataContainer().getOrDefault(keyXrayActiveUntil, PersistentDataType.LONG, 0L);
    }

    private long xrayCooldownUntil(Player player) {
        return player.getPersistentDataContainer().getOrDefault(keyXrayCooldownUntil, PersistentDataType.LONG, 0L);
    }

    private void setXrayActiveUntil(Player player, long value) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (value <= 0L) {
            pdc.remove(keyXrayActiveUntil);
            return;
        }
        pdc.set(keyXrayActiveUntil, PersistentDataType.LONG, value);
    }

    private void setXrayCooldownUntil(Player player, long value) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (value <= 0L) {
            pdc.remove(keyXrayCooldownUntil);
            return;
        }
        pdc.set(keyXrayCooldownUntil, PersistentDataType.LONG, value);
    }

    private long supermanFlightActiveUntil(Player player) {
        return player.getPersistentDataContainer().getOrDefault(keySupermanFlightActiveUntil, PersistentDataType.LONG, 0L);
    }

    private long supermanFlightCooldownUntil(Player player) {
        return player.getPersistentDataContainer().getOrDefault(keySupermanFlightCooldownUntil, PersistentDataType.LONG, 0L);
    }

    private void setSupermanFlightActiveUntil(Player player, long value) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (value <= 0L) {
            pdc.remove(keySupermanFlightActiveUntil);
            return;
        }
        pdc.set(keySupermanFlightActiveUntil, PersistentDataType.LONG, value);
    }

    private void setSupermanFlightCooldownUntil(Player player, long value) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (value <= 0L) {
            pdc.remove(keySupermanFlightCooldownUntil);
            return;
        }
        pdc.set(keySupermanFlightCooldownUntil, PersistentDataType.LONG, value);
    }

    private long timeStopCooldownUntil(Player player) {
        return player.getPersistentDataContainer().getOrDefault(keyTimeStopCooldownUntil, PersistentDataType.LONG, 0L);
    }

    private void setTimeStopCooldownUntil(Player player, long value) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (value <= 0L) {
            pdc.remove(keyTimeStopCooldownUntil);
            return;
        }
        pdc.set(keyTimeStopCooldownUntil, PersistentDataType.LONG, value);
    }

    private void syncSupermanFlightState(Player player, boolean supermanAvailable) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean active = supermanFlightActiveUntil(player) > now;
        boolean offCooldown = supermanFlightCooldownUntil(player) <= now;
        boolean shouldAllow = plugin.getPlayerManager().hasFlightEnabled(player.getUniqueId())
            || (supermanAvailable && (active || offCooldown))
            || (plugin.getCustomEnchantListener() != null && plugin.getCustomEnchantListener().shouldRetainFlightAccess(player));

        if (player.getAllowFlight() != shouldAllow) {
            player.setAllowFlight(shouldAllow);
        }
        if (!shouldAllow && player.isFlying()) {
            player.setFlying(false);
        }
    }

    private void startSupermanFlight(Player player) {
        long now = System.currentTimeMillis();
        setSupermanFlightActiveUntil(player, now + (SUPERMAN_FLIGHT_SECONDS * 1000L));
        syncSupermanFlightState(player, true);
        player.setFlying(true);
        player.sendMessage(MessageUtil.success("Skybound flight surges through you for <white>30 seconds</white>."));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.8f, 1.45f);
    }

    private void stopSupermanFlight(Player player, boolean expired) {
        long activeUntil = supermanFlightActiveUntil(player);
        if (activeUntil <= 0L && !expired) {
            syncSupermanFlightState(player, hasPower(player, SuperpowerType.SUPERMAN));
            return;
        }

        setSupermanFlightActiveUntil(player, 0L);
        if (expired) {
            setSupermanFlightCooldownUntil(player, System.currentTimeMillis() + (SUPERMAN_FLIGHT_COOLDOWN_SECONDS * 1000L));
        }
        if (player.isFlying() && !plugin.getPlayerManager().hasFlightEnabled(player.getUniqueId())) {
            player.setFlying(false);
        }
        syncSupermanFlightState(player, hasPower(player, SuperpowerType.SUPERMAN));
        if (expired && player.isOnline()) {
            player.sendMessage(MessageUtil.warn("Skybound flight faded. Cooldown: <white>5 minutes</white>."));
        }
    }

    private void trySupermanBoost(Player player) {
        long now = System.currentTimeMillis();
        if (supermanFlightActiveUntil(player) <= now || !player.isFlying()) {
            return;
        }

        long readyAt = supermanBoostCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) {
            return;
        }

        Vector boost = player.getLocation().getDirection().normalize().multiply(1.25).add(new Vector(0.0, 0.22, 0.0));
        player.setVelocity(boost);
        supermanBoostCooldowns.put(player.getUniqueId(), now + SUPERMAN_BOOST_COOLDOWN_MS);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.45f, 1.7f);
    }

    private void renderXrayHighlights(Player player) {
        if (!player.isOnline() || player.isDead() || xrayActiveUntil(player) <= System.currentTimeMillis()) {
            return;
        }

        Location origin = player.getLocation();
        World world = player.getWorld();
        for (Entity entity : world.getNearbyEntities(origin, XRAY_ENTITY_RADIUS, XRAY_ENTITY_RADIUS, XRAY_ENTITY_RADIUS)) {
            if (!(entity instanceof LivingEntity living) || living.equals(player) || living.isDead() || !living.isValid()) {
                continue;
            }
            org.bukkit.Color color = living instanceof Player ? org.bukkit.Color.fromRGB(255, 80, 80) : org.bukkit.Color.fromRGB(84, 214, 255);
            spawnXrayEntityOutline(player, living, color);
        }

        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY();
        int baseZ = origin.getBlockZ();
        int minY = Math.max(world.getMinHeight(), baseY - XRAY_ORE_VERTICAL_RADIUS);
        int maxY = Math.min(world.getMaxHeight() - 1, baseY + XRAY_ORE_VERTICAL_RADIUS);
        for (int x = baseX - XRAY_ORE_RADIUS; x <= baseX + XRAY_ORE_RADIUS; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = baseZ - XRAY_ORE_RADIUS; z <= baseZ + XRAY_ORE_RADIUS; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!MINER_ORE_BLOCKS.contains(block.getType())) {
                        continue;
                    }
                    spawnXrayOreMarker(player, block);
                }
            }
        }
    }

    private void spawnXrayEntityOutline(Player viewer, LivingEntity target, org.bukkit.Color color) {
        double height = Math.max(0.8, target.getHeight());
        double step = Math.max(0.4, height / 3.0);
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.1f);
        for (double y = 0.15; y <= height; y += step) {
            Location point = target.getLocation().clone().add(0.0, y, 0.0);
            viewer.spawnParticle(Particle.DUST, point, 8, target.getWidth() * 0.45, 0.08, target.getWidth() * 0.45, 0.0, dust);
        }
    }

    private void spawnXrayOreMarker(Player viewer, Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        Particle.DustOptions dust = new Particle.DustOptions(oreHighlightColor(block.getType()), 0.9f);
        viewer.spawnParticle(Particle.DUST, center, 6, 0.18, 0.18, 0.18, 0.0, dust);
    }

    private org.bukkit.Color oreHighlightColor(Material material) {
        return switch (material) {
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> org.bukkit.Color.AQUA;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> org.bukkit.Color.LIME;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> org.bukkit.Color.RED;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> org.bukkit.Color.YELLOW;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> org.bukkit.Color.BLUE;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> org.bukkit.Color.fromRGB(216, 170, 120);
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> org.bukkit.Color.fromRGB(214, 122, 74);
            case ANCIENT_DEBRIS -> org.bukkit.Color.fromRGB(91, 62, 54);
            case NETHER_QUARTZ_ORE -> org.bukkit.Color.WHITE;
            default -> org.bukkit.Color.fromRGB(90, 90, 90);
        };
    }

    private void useTheWorldClock(Player player) {
        if (!hasPower(player, SuperpowerType.THE_WORLD)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return;
        }

        UUID playerId = player.getUniqueId();
        if (activeTimeStops.containsKey(playerId)) {
            player.sendMessage(MessageUtil.warn("Time is already frozen around you."));
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownUntil = timeStopCooldownUntil(player);
        if (cooldownUntil > now) {
            player.sendMessage(MessageUtil.warn(
                "Time Stop cooldown: <white>" + secondsLeft(cooldownUntil, now) + "s</white>."
            ));
            return;
        }

        TimeStopState state = new TimeStopState(
            playerId,
            player.getLocation().clone(),
            now + (TIME_STOP_DURATION_SECONDS * 1000L)
        );
        activeTimeStops.put(playerId, state);
        setTimeStopCooldownUntil(player, now + (TIME_STOP_COOLDOWN_SECONDS * 1000L));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.1f, 0.55f);
        player.sendMessage(MessageUtil.success("The World stands still for <white>5 seconds</white>."));
    }

    private void tickTimeStops() {
        reconcileTimeStopState(true);
    }

    private void reconcileTimeStopState(boolean render) {
        long now = System.currentTimeMillis();
        Set<UUID> currentFrozenPlayers = new HashSet<>();
        Set<UUID> currentFrozenMobs = new HashSet<>();
        Set<UUID> currentFrozenProjectiles = new HashSet<>();

        for (Map.Entry<UUID, TimeStopState> entry : new HashMap<>(activeTimeStops).entrySet()) {
            TimeStopState state = entry.getValue();
            Location center = state.center();
            World world = center.getWorld();
            if (world == null) {
                activeTimeStops.remove(entry.getKey());
                continue;
            }
            if (state.expiresAt() <= now) {
                activeTimeStops.remove(entry.getKey());
                Player owner = Bukkit.getPlayer(entry.getKey());
                if (owner != null && owner.isOnline()) {
                    owner.sendMessage(MessageUtil.info("Time begins to move again."));
                    owner.getWorld().playSound(owner.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 1.35f);
                }
                continue;
            }

            if (render) {
                renderTimeStopState(state);
            }

            for (Entity entity : world.getNearbyEntities(center, TIME_STOP_RADIUS, TIME_STOP_RADIUS, TIME_STOP_RADIUS)) {
                if (entity.getUniqueId().equals(state.ownerId())) {
                    continue;
                }
                if (!isInsideTimeStop(state, entity.getLocation())) {
                    continue;
                }

                if (entity instanceof Player frozenPlayer) {
                    currentFrozenPlayers.add(frozenPlayer.getUniqueId());
                    frozenPlayer.setVelocity(new Vector());
                    applyPotion(frozenPlayer, PotionEffectType.SLOWNESS, 5, 10);
                    applyPotion(frozenPlayer, PotionEffectType.MINING_FATIGUE, 5, 4);
                    continue;
                }

                if (entity instanceof Mob mob) {
                    currentFrozenMobs.add(mob.getUniqueId());
                    frozenMobs.computeIfAbsent(mob.getUniqueId(), ignored -> new FrozenMobState(mob.hasAI(), mob.hasGravity()));
                    mob.setAI(false);
                    mob.setGravity(false);
                    mob.setVelocity(new Vector());
                    mob.setTarget(null);
                    continue;
                }

                if (entity instanceof Projectile projectile && shouldFreezeProjectile(projectile, state.ownerId())) {
                    currentFrozenProjectiles.add(projectile.getUniqueId());
                    frozenProjectiles.computeIfAbsent(
                        projectile.getUniqueId(),
                        ignored -> new FrozenProjectileState(projectile.getLocation().clone(), projectile.getVelocity().clone(), projectile.hasGravity())
                    );
                    FrozenProjectileState projectileState = frozenProjectiles.get(projectile.getUniqueId());
                    projectile.setGravity(false);
                    projectile.teleport(projectileState.location());
                    projectile.setVelocity(new Vector());
                }
            }
        }

        timeStoppedPlayers.clear();
        timeStoppedPlayers.addAll(currentFrozenPlayers);
        restoreExpiredFrozenMobs(currentFrozenMobs);
        restoreExpiredFrozenProjectiles(currentFrozenProjectiles);
    }

    private void renderTimeStopState(TimeStopState state) {
        Location center = state.center().clone().add(0.0, 1.0, 0.0);
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        world.spawnParticle(Particle.PORTAL, center, 20, 0.4, 0.7, 0.4, 0.0);
        for (int i = 0; i < 18; i++) {
            double angle = (Math.PI * 2.0 * i) / 18.0;
            double x = Math.cos(angle) * TIME_STOP_RADIUS;
            double z = Math.sin(angle) * TIME_STOP_RADIUS;
            Location ring = center.clone().add(x, 0.0, z);
            world.spawnParticle(Particle.DUST, ring, 1, 0.02, 0.12, 0.02, 0.0, new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 212, 74), 1.1f));
        }
    }

    private boolean isInsideActiveTimeStop(Location location) {
        for (TimeStopState state : activeTimeStops.values()) {
            if (isInsideTimeStop(state, location)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInsideTimeStop(TimeStopState state, Location location) {
        if (state == null || location == null || state.center().getWorld() == null || location.getWorld() == null) {
            return false;
        }
        if (!state.center().getWorld().equals(location.getWorld())) {
            return false;
        }
        return state.center().distanceSquared(location) <= (TIME_STOP_RADIUS * TIME_STOP_RADIUS);
    }

    private boolean shouldFreezeProjectile(Projectile projectile, UUID ownerId) {
        if (projectile.getShooter() instanceof Entity shooter && shooter.getUniqueId().equals(ownerId)) {
            return false;
        }
        return projectile.isValid() && !projectile.isDead();
    }

    private void restoreExpiredFrozenMobs(Set<UUID> currentFrozenMobs) {
        for (Map.Entry<UUID, FrozenMobState> entry : new HashMap<>(frozenMobs).entrySet()) {
            if (currentFrozenMobs.contains(entry.getKey())) {
                continue;
            }
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity instanceof Mob mob && mob.isValid()) {
                mob.setAI(entry.getValue().hadAi());
                mob.setGravity(entry.getValue().hadGravity());
            }
            frozenMobs.remove(entry.getKey());
        }
    }

    private void restoreExpiredFrozenProjectiles(Set<UUID> currentFrozenProjectiles) {
        for (Map.Entry<UUID, FrozenProjectileState> entry : new HashMap<>(frozenProjectiles).entrySet()) {
            if (currentFrozenProjectiles.contains(entry.getKey())) {
                continue;
            }
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity instanceof Projectile projectile && projectile.isValid()) {
                projectile.setGravity(entry.getValue().hadGravity());
                projectile.setVelocity(entry.getValue().velocity());
            }
            frozenProjectiles.remove(entry.getKey());
        }
    }

    private void clearTimeStopForOwner(UUID ownerId) {
        if (activeTimeStops.remove(ownerId) != null) {
            reconcileTimeStopState(false);
        }
    }

    private void clearAllTimeStops() {
        activeTimeStops.clear();
        timeStoppedPlayers.clear();
        restoreExpiredFrozenMobs(Set.of());
        restoreExpiredFrozenProjectiles(Set.of());
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

    private void trimExtraMotherNatureSticks(Player player) {
        boolean kept = false;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (!isMotherNatureStick(item)) {
                continue;
            }
            if (!kept) {
                if (item.getAmount() != 1) {
                    item.setAmount(1);
                    player.getInventory().setItem(slot, item);
                }
                kept = true;
                continue;
            }
            player.getInventory().setItem(slot, null);
        }

        ItemStack cursor = player.getItemOnCursor();
        if (!isMotherNatureStick(cursor)) {
            return;
        }
        if (!kept) {
            if (cursor.getAmount() != 1) {
                cursor.setAmount(1);
                player.setItemOnCursor(cursor);
            }
            return;
        }
        player.setItemOnCursor(null);
    }

    private boolean hasTheWorldClock(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isTheWorldClock(item)) {
                return true;
            }
        }
        return isTheWorldClock(player.getItemOnCursor());
    }

    private boolean hasDruidGrimoire(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isDruidGrimoire(item)) {
                return true;
            }
        }
        return isDruidGrimoire(player.getItemOnCursor());
    }

    private void giveMotherNatureStick(Player player, boolean restored) {
        trimExtraMotherNatureSticks(player);
        if (hasMotherNatureStick(player)) {
            return;
        }
        if (player.getInventory().firstEmpty() == -1) {
            pendingFloristStickReturns.put(player.getUniqueId(), 1);
            if (restored) {
                player.sendMessage(MessageUtil.warn("Clear an inventory slot so the Stick from Mother Nature can return."));
            }
            return;
        }

        ItemStack stick = createMotherNatureStickItem();
        if (plugin.getItemAuditManager() != null) {
            plugin.getItemAuditManager().recordKnownAcquisition(
                player,
                stick,
                restored ? "power_item_restore" : "power_item_grant",
                restored ? "Restored Stick from Mother Nature." : "Granted Stick from Mother Nature."
            );
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stick);
        if (!leftovers.isEmpty()) {
            pendingFloristStickReturns.put(player.getUniqueId(), 1);
        }
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

    private void giveTheWorldClock(Player player, boolean restored) {
        ItemStack clock = createTheWorldClockItem();
        if (plugin.getItemAuditManager() != null) {
            plugin.getItemAuditManager().recordKnownAcquisition(
                player,
                clock,
                restored ? "power_item_restore" : "power_item_grant",
                restored ? "Restored The World Clock." : "Granted The World Clock."
            );
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(clock);
        leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        if (restored) {
            player.sendMessage(MessageUtil.info("The World Clock returned to you."));
        }
    }

    private void removeTheWorldClocks(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (isTheWorldClock(item)) {
                player.getInventory().setItem(slot, null);
            }
        }

        if (isTheWorldClock(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    private void giveDruidGrimoire(Player player, boolean restored) {
        ItemStack grimoire = createDruidGrimoireItem();
        if (plugin.getItemAuditManager() != null) {
            plugin.getItemAuditManager().recordKnownAcquisition(
                player,
                grimoire,
                restored ? "power_item_restore" : "power_item_grant",
                restored ? "Restored Druid's Grimoire." : "Granted Druid's Grimoire."
            );
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(grimoire);
        leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        if (restored) {
            player.sendMessage(MessageUtil.info("The Druid's Grimoire returned to you."));
        }
    }

    private void removeDruidGrimoires(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (isDruidGrimoire(item)) {
                player.getInventory().setItem(slot, null);
            }
        }

        if (isDruidGrimoire(player.getItemOnCursor())) {
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
                    if (awakening != null) {
                        awakening.clearAwakeningState(meta);
                    }
                    applyAncientScrollState(meta);
                    applyAncientScrollPresentation(meta);
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
                continue;
            }
            if (isTheWorldClock(item)) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    applyTheWorldClockState(meta);
                    applyTheWorldClockPresentation(meta);
                    item.setItemMeta(meta);
                    player.getInventory().setItem(slot, item);
                }
                continue;
            }
            if (isDruidGrimoire(item)) {
                ItemStack refreshed = createDruidGrimoireItem();
                refreshed.setAmount(item.getAmount());
                player.getInventory().setItem(slot, refreshed);
            }
        }

        if (isDruidGrimoire(player.getItemOnCursor())) {
            ItemStack refreshed = createDruidGrimoireItem();
            refreshed.setAmount(player.getItemOnCursor().getAmount());
            player.setItemOnCursor(refreshed);
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

    private boolean canReceiveCraftResult(Player player, InventoryClickEvent event) {
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

    private ItemStack preserveBoundPowerItem(ItemStack result, ItemStack source) {
        ItemStack updated = result.clone();
        ItemMeta meta = updated.getItemMeta();
        if (meta == null) {
            return updated;
        }
        if (isMotherNatureStick(source)) {
            applyMotherNatureStickState(meta);
            applyMotherNatureStickPresentation(meta);
        } else if (isTheWorldClock(source)) {
            applyTheWorldClockState(meta);
            applyTheWorldClockPresentation(meta);
        } else if (isDruidGrimoire(source)) {
            applyDruidGrimoireState(meta);
            applyDruidGrimoirePresentation(meta);
        }
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

    private void fillInfoMenu(Player viewer, Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, fillerPane());
        }
        List<SuperpowerType> types = new ArrayList<>(List.of(SuperpowerType.values()));
        types.sort(Comparator
            .comparingDouble((SuperpowerType type) -> displayChance(type))
            .reversed()
            .thenComparing(SuperpowerType::displayName));
        int[] slots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };
        for (int i = 0; i < types.size() && i < slots.length; i++) {
            inventory.setItem(slots[i], createPowerInfoIcon(types.get(i)));
        }
        inventory.setItem(4, createCurrentPowerStatusIcon(viewer));
        inventory.setItem(49, simpleMenuItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to /menu.</gray>")));
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

    private ItemStack simpleMenuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(MM.deserialize(name));
        meta.lore(lore.stream().map(MM::deserialize).toList());
        item.setItemMeta(meta);
        return item;
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

    private ItemStack createCurrentPowerStatusIcon(Player viewer) {
        SuperpowerType currentPower = powerOf(viewer);
        Material icon = currentPower == null ? Material.BARRIER : currentPower.icon();
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String powerName = currentPower == null ? "Unknown" : currentPower.displayName();
        meta.displayName(MM.deserialize("<aqua><bold>Your Power</bold></aqua>"));
        meta.lore(List.of(
            MM.deserialize("<gray>Current Fate: <white>" + powerName + "</white></gray>"),
            Component.empty(),
            MM.deserialize("<gray>The menu below shows every possible power and its effects.</gray>")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> powerInfoLore(SuperpowerType type) {
        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<gray>Chance: <white>" + formatPercent(displayChance(type)) + "</white></gray>"));
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
                lore.add(MM.deserialize("<gray>Mob kills can drop <white>essence loot</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Enchanted gear has a <white>25%</white> chance to ignore durability loss.</gray>"));
                lore.add(MM.deserialize("<gray>Held enchant levels grant speed and attack speed.</gray>"));
                lore.add(MM.deserialize("<gray>Also grants <white>+2 Luck</white>.</gray>"));
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
                lore.add(MM.deserialize("<gray>An <white>Ancient Scroll</white> can reroll any current fate.</gray>"));
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
            case DRUID -> {
                lore.add(MM.deserialize("<gray>Receives a bound <white>Druid's Grimoire</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Choose one blessing for yourself and nearby teammates within <white>" + DRUID_BUFF_RADIUS + " blocks</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Can grant strength, speed, regeneration, resistance, fire resistance, absorption, or instant health.</gray>"));
                lore.add(MM.deserialize("<gray>Non-instant blessings last <white>" + (DRUID_BUFF_DURATION_SECONDS / 60) + " minutes</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Only one blessing can be chosen each use.</gray>"));
                lore.add(MM.deserialize("<gray>Cooldown: <white>" + DRUID_BUFF_COOLDOWN_SECONDS + "s</white>.</gray>"));
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
            case THE_WORLD -> {
                lore.add(MM.deserialize("<gray>Receives a bound <white>World Clock</white>.</gray>"));
                lore.add(MM.deserialize("<gray><white>Right-click</white> to stop time in a <white>10 block</white> radius.</gray>"));
                lore.add(MM.deserialize("<gray>Freezes mobs, players, projectiles, redstone, and block updates for <white>5s</white>.</gray>"));
                lore.add(MM.deserialize("<gray>The user can still move, fight, and interact during the stop.</gray>"));
                lore.add(MM.deserialize("<gray>Cooldown: <white>5 minutes</white>.</gray>"));
            }
            case XRAY_VISION -> {
                lore.add(MM.deserialize("<gray>Highlights ores, players, and mobs through walls for <white>30s</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Cooldown: <white>6 minutes</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Command: <white>/xray</white></gray>"));
            }
            case MINER -> {
                lore.add(MM.deserialize("<gray>Permanent <white>Haste III</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Grants <white>+2 health</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Mining ores has a <white>15%</white> chance to duplicate the drop.</gray>"));
            }
            case GIANT -> {
                lore.add(MM.deserialize("<gray>Scaled to <white>1.2x</white> normal size.</gray>"));
                lore.add(MM.deserialize("<gray>Grants <white>+6 hearts</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Heavy blows gain <white>+2 attack damage</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Reduces all incoming knockback by <white>40%</white>.</gray>"));
            }
            case SUPERMAN -> {
                lore.add(MM.deserialize("<gray>Permanent <white>Strength II</white> and <white>Speed I</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Grants <white>an extra row of hearts</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Double-tap jump to fly for <white>30s</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Sneak while flying to burst forward.</gray>"));
                lore.add(MM.deserialize("<gray>Cooldown: <white>5 minutes</white>.</gray>"));
            }
            case WATERMAN -> {
                lore.add(MM.deserialize("<gray>Can live underwater with <white>no drowning</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Air is constantly restored and Water Breathing is always active.</gray>"));
                lore.add(MM.deserialize("<gray>Breaks blocks at normal speed while submerged.</gray>"));
                lore.add(MM.deserialize("<gray>While in water, gains Conduit Power, Dolphin's Grace, Haste II, Speed II, Strength I, Resistance I, and Regeneration I.</gray>"));
                lore.add(MM.deserialize("<gray>Water-empowered hits deal <white>25%</white> more damage.</gray>"));
                lore.add(MM.deserialize("<gray>While water-empowered, incoming damage is reduced by <white>25%</white> and attackers are slowed.</gray>"));
            }
            case PHOENIX -> {
                lore.add(MM.deserialize("<gray>Permanent <white>Fire Resistance</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Low health grants <white>Speed I</white> and <white>Regeneration I</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Hits ignite enemies and burning targets take bonus damage.</gray>"));
                lore.add(MM.deserialize("<gray>Enemies that damage you are scorched while you gain brief Absorption.</gray>"));
                lore.add(MM.deserialize("<gray>Lethal damage can trigger <white>Rebirth</white>, restoring health and burning nearby enemies.</gray>"));
                lore.add(MM.deserialize("<gray>Rebirth cooldown: <white>" + (PHOENIX_COOLDOWN_SECONDS / 60) + " minutes</white>.</gray>"));
            }
            case VOIDWALKER -> {
                lore.add(MM.deserialize("<gray>Toggleable <white>Night Vision</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Command: <white>/voidstep</white></gray>"));
                lore.add(MM.deserialize("<gray>Night vision can be toggled with <white>/voidvision</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Blink up to <white>" + (int) VOIDSTEP_RANGE + " blocks</white> through a clear safe path.</gray>"));
                lore.add(MM.deserialize("<gray>Voidstep grants <white>" + VOIDSTEP_INVISIBILITY_SECONDS + "s</white> of Invisibility.</gray>"));
                lore.add(MM.deserialize("<gray>Voidstep grants <white>" + VOIDSTEP_SLOW_FALLING_SECONDS + "s</white> of Slow Falling.</gray>"));
                lore.add(MM.deserialize("<gray>Your first veil attack blinds and weakens the target with bonus damage.</gray>"));
                lore.add(MM.deserialize("<gray>The veil attack window lasts <white>" + VOIDSTEP_VEIL_SECONDS + "s</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Endermen refuse to target Voidwalkers.</gray>"));
                lore.add(MM.deserialize("<gray>Cooldown: <white>" + VOIDSTEP_COOLDOWN_SECONDS + "s</white>.</gray>"));
            }
            case SENTINEL -> {
                lore.add(MM.deserialize("<gray>Grants <white>+2 hearts</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Sneak to project a <white>" + (int) SENTINEL_AURA_RADIUS + " block</white> guard aura.</gray>"));
                lore.add(MM.deserialize("<gray>You and nearby teammates gain <white>Resistance I</white> and Absorption.</gray>"));
                lore.add(MM.deserialize("<gray>While braced, incoming damage is reduced and attackers are weakened.</gray>"));
                lore.add(MM.deserialize("<gray>The caster is slowed while bracing the aura.</gray>"));
            }
            case FROSTBORN -> {
                lore.add(MM.deserialize("<gray>Permanent <white>Water Breathing</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Immune to freezing damage.</gray>"));
                lore.add(MM.deserialize("<gray>Gain <white>Speed I</white> while standing on snow or ice.</gray>"));
                lore.add(MM.deserialize("<gray>Damaging enemies chills them with <white>Slowness I</white> and <white>Weakness I</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Chilled targets take <white>15%</white> more Frostborn damage.</gray>"));
                lore.add(MM.deserialize("<gray>Enemies that damage you are chilled too.</gray>"));
                lore.add(MM.deserialize("<gray>Chill duration: <white>" + FROSTBORN_CHILL_SECONDS + "s</white>.</gray>"));
            }
            case DEADEYE -> {
                lore.add(MM.deserialize("<gray>Permanent <white>Speed I</white> and <white>Night Vision</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Projectile hits deal <white>20%</white> more damage.</gray>"));
                lore.add(MM.deserialize("<gray>Sneak while shooting arrows/tridents to fire a faster marked shot.</gray>"));
                lore.add(MM.deserialize("<gray>Marked shots deal <white>35%</white> more damage and briefly slow targets.</gray>"));
                lore.add(MM.deserialize("<gray>Targets hit by projectiles glow for <white>" + DEADEYE_GLOW_SECONDS + "s</white>.</gray>"));
            }
            case RIFTWARDEN -> {
                lore.add(MM.deserialize("<gray>Built for boss fights and dangerous mobs.</gray>"));
                lore.add(MM.deserialize("<gray>Deals <white>18%</white> more damage to custom bosses.</gray>"));
                lore.add(MM.deserialize("<gray>Deals <white>8%</white> more damage to hostile mobs.</gray>"));
                lore.add(MM.deserialize("<gray>Reduces non-player damage, stronger against bosses.</gray>"));
                lore.add(MM.deserialize("<gray>Gains Slow Falling only near custom bosses.</gray>"));
                lore.add(MM.deserialize("<gray>Gains Resistance near custom bosses or while in the End.</gray>"));
            }
            case OATHBOUND -> {
                lore.add(MM.deserialize("<gray>Wakes up around nearby teammates.</gray>"));
                lore.add(MM.deserialize("<gray>You and teammates within <white>" + (int) OATHBOUND_AURA_RADIUS + " blocks</white> gain Speed I.</gray>"));
                lore.add(MM.deserialize("<gray>The Oathbound caster also gains Resistance I and Absorption.</gray>"));
                lore.add(MM.deserialize("<gray>Only works with real team members, so multiple Oathbound players stack naturally without duping inventory effects.</gray>"));
            }
            case RUNESMITH -> {
                lore.add(MM.deserialize("<gray>Permanent <white>Haste I</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Gear has a <white>15%</white> chance to ignore durability loss.</gray>"));
                lore.add(MM.deserialize("<gray>Custom, legendary, and season gear preserve durability at <white>25%</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Killing a custom boss repairs held gear and armor by <white>10%</white> durability.</gray>"));
            }
            case GRAVEBORN -> {
                lore.add(MM.deserialize("<gray>Immune to poison and wither damage.</gray>"));
                lore.add(MM.deserialize("<gray>Undead mobs refuse to target the Graveborn.</gray>"));
                lore.add(MM.deserialize("<gray>Deals extra damage to undead and takes less from them.</gray>"));
                lore.add(MM.deserialize("<gray>Kills restore health, with player kills restoring more.</gray>"));
                lore.add(MM.deserialize("<gray>Permanent Night Vision keeps caves readable without a command.</gray>"));
            }
            case STORMCALLER -> {
                lore.add(MM.deserialize("<gray>Permanent <white>Speed I</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Rain or thunder grants <white>Haste II</white> and <white>Strength I</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Hits can call a visual lightning strike for bonus damage.</gray>"));
                lore.add(MM.deserialize("<gray>Lightning chance is higher during storms.</gray>"));
                lore.add(MM.deserialize("<gray>Command: <white>/stormcaller on|off</white></gray>"));
            }
            case BLOODMENDER -> {
                lore.add(MM.deserialize("<gray>Damaging enemies heals a portion of the damage dealt.</gray>"));
                lore.add(MM.deserialize("<gray>Healing is stronger against mobs and capped per hit.</gray>"));
                lore.add(MM.deserialize("<gray>Kills pair well with boss waves and PvP cleanup without making the player immortal.</gray>"));
                lore.add(MM.deserialize("<gray>Low health grants brief Regeneration I.</gray>"));
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
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.EPIC, "Ancient Scroll"));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.PAPER,
            CustomLoreUtil.Rarity.EPIC.label(),
            "SCROLL",
            List.of("<gray>Right-click to reroll your current superpower.</gray>"),
            List.of(CustomLoreUtil.section(
                "Use",
                "Fate Rewrite",
                "<gray>Works even if you already have a power.</gray>",
                "<gray>Consumes the scroll and rerolls you into a random new fate.</gray>"
            ))
        ));
    }

    private void applyWardenHeartState(ItemMeta meta) {
        meta.getPersistentDataContainer().set(keyWardenHeart, PersistentDataType.STRING, WARDEN_HEART_ITEM_ID);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        CustomLoreUtil.applyStyledItemFlags(meta);
    }

    private void applyWardenHeartPresentation(ItemMeta meta) {
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.EPIC, "Warden Heart"));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.HEART_OF_THE_SEA,
            CustomLoreUtil.Rarity.EPIC.label(),
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
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.EPIC, "Stick from Mother Nature"));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.STICK,
            CustomLoreUtil.Rarity.EPIC.label(),
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

    private void applyTheWorldClockState(ItemMeta meta) {
        meta.getPersistentDataContainer().set(keyTheWorldClock, PersistentDataType.STRING, THE_WORLD_CLOCK_ITEM_ID);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        CustomLoreUtil.applyStyledItemFlags(meta);
    }

    private void applyTheWorldClockPresentation(ItemMeta meta) {
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.MYTHIC, "World Clock"));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.CLOCK,
            CustomLoreUtil.Rarity.MYTHIC.label(),
            "CLOCK",
            List.of("<gray>Bound to the one who halts the world.</gray>"),
            List.of(CustomLoreUtil.section(
                "Right Click",
                "Time Stop",
                "<gray>Freezes mobs, players, projectiles, redstone, and block updates</gray>",
                "<gray>inside a <white>" + TIME_STOP_RADIUS + " block</white> radius for <white>" + TIME_STOP_DURATION_SECONDS + "s</white>.</gray>",
                "<gray>Cooldown: <white>" + (TIME_STOP_COOLDOWN_SECONDS / 60) + " minutes</white>.</gray>"
            ))
        ));
    }

    private void applyDruidGrimoireState(ItemMeta meta) {
        meta.getPersistentDataContainer().set(keyDruidGrimoire, PersistentDataType.STRING, DRUID_GRIMOIRE_ITEM_ID);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        CustomLoreUtil.applyStyledItemFlags(meta);
    }

    private void applyDruidGrimoirePresentation(ItemMeta meta) {
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.EPIC, "Druid's Grimoire"));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.BOOK,
            CustomLoreUtil.Rarity.EPIC.label(),
            "GRIMOIRE",
            List.of("<gray>Bound to the keeper of the grove.</gray>"),
            List.of(CustomLoreUtil.section(
                "Right Click",
                "Wild Communion",
                "<gray>Open the grimoire and choose <white>one</white> blessing.</gray>",
                "<gray>Blesses you and nearby teammates within <white>" + DRUID_BUFF_RADIUS + " blocks</white>.</gray>",
                "<gray>Cooldown: <white>" + DRUID_BUFF_COOLDOWN_SECONDS + "s</white>.</gray>"
            ))
        ));
    }

    private ItemStack createEnchanterEssenceDrop() {
        if (ThreadLocalRandom.current().nextBoolean()) {
            return new ItemStack(Material.EXPERIENCE_BOTTLE);
        }

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        if (!(book.getItemMeta() instanceof EnchantmentStorageMeta meta)) {
            return new ItemStack(Material.EXPERIENCE_BOTTLE);
        }

        List<Enchantment> enchants = new ArrayList<>();
        for (Enchantment enchantment : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)) {
            if (enchantment != null) {
                enchants.add(enchantment);
            }
        }
        if (enchants.isEmpty()) {
            return new ItemStack(Material.EXPERIENCE_BOTTLE);
        }

        Enchantment chosen = enchants.get(ThreadLocalRandom.current().nextInt(enchants.size()));
        int level = Math.max(1, ThreadLocalRandom.current().nextInt(chosen.getMaxLevel()) + 1);
        meta.addStoredEnchant(chosen, level, true);
        book.setItemMeta(meta);
        return book;
    }

    private String formatPercent(double chance) {
        double percent = chance * 100.0;
        if (Math.abs(percent - Math.rint(percent)) < 0.0001) {
            return Long.toString(Math.round(percent)) + "%";
        }
        return String.format(Locale.US, "%.2f%%", percent);
    }

    private double displayChance(SuperpowerType type) {
        if (type == null) {
            return 0.0;
        }
        double total = 0.0;
        for (SuperpowerType candidate : SuperpowerType.values()) {
            total += candidate.chance();
        }
        return total <= 0.0 ? 0.0 : type.chance() / total;
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

    private record TimeStopState(UUID ownerId, Location center, long expiresAt) {}
    private record FrozenMobState(boolean hadAi, boolean hadGravity) {}
    private record FrozenProjectileState(Location location, Vector velocity, boolean hadGravity) {}
    private record PowerInfoHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record DruidGrimoireHolder(UUID ownerId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private enum DruidBlessing {
        STRENGTH(10, Material.IRON_SWORD, "<red><bold>Strength</bold></red>", "<gray>Grants <white>Strength I</white>.</gray>", Particle.CRIT, Sound.ENTITY_PLAYER_ATTACK_STRONG),
        SPEED(11, Material.SUGAR, "<aqua><bold>Speed</bold></aqua>", "<gray>Grants <white>Speed I</white>.</gray>", Particle.CLOUD, Sound.ENTITY_BREEZE_SLIDE),
        REGENERATION(12, Material.GLISTERING_MELON_SLICE, "<light_purple><bold>Regeneration</bold></light_purple>", "<gray>Grants <white>Regeneration I</white>.</gray>", Particle.HEART, Sound.BLOCK_AMETHYST_BLOCK_CHIME),
        RESISTANCE(13, Material.SHIELD, "<gray><bold>Resistance</bold></gray>", "<gray>Grants <white>Resistance I</white>.</gray>", Particle.ANGRY_VILLAGER, Sound.ITEM_ARMOR_EQUIP_NETHERITE),
        FIRE_RESISTANCE(14, Material.MAGMA_CREAM, "<gold><bold>Fire Resistance</bold></gold>", "<gray>Grants <white>Fire Resistance I</white>.</gray>", Particle.FLAME, Sound.ITEM_FIRECHARGE_USE),
        ABSORPTION(15, Material.GOLDEN_APPLE, "<yellow><bold>Absorption</bold></yellow>", "<gray>Grants <white>Absorption I</white>.</gray>", Particle.TOTEM_OF_UNDYING, Sound.BLOCK_BEACON_POWER_SELECT),
        INSTANT_HEALTH(16, Material.SPLASH_POTION, "<green><bold>Instant Health</bold></green>", "<gray>Restores a quick burst of health.</gray>", Particle.HAPPY_VILLAGER, Sound.ENTITY_PLAYER_LEVELUP);

        private final int slot;
        private final Material icon;
        private final String display;
        private final String lore;
        private final Particle particle;
        private final Sound sound;

        DruidBlessing(int slot, Material icon, String display, String lore, Particle particle, Sound sound) {
            this.slot = slot;
            this.icon = icon;
            this.display = display;
            this.lore = lore;
            this.particle = particle;
            this.sound = sound;
        }

        int slot() {
            return slot;
        }

        Material icon() {
            return icon;
        }

        String display() {
            return display;
        }

        String lore() {
            return lore;
        }

        Particle particle() {
            return particle;
        }

        Sound sound() {
            return sound;
        }

        String plainName() {
            return switch (this) {
                case STRENGTH -> "Strength";
                case SPEED -> "Speed";
                case REGENERATION -> "Regeneration";
                case RESISTANCE -> "Resistance";
                case FIRE_RESISTANCE -> "Fire Resistance";
                case ABSORPTION -> "Absorption";
                case INSTANT_HEALTH -> "Instant Health";
            };
        }

        String durationText() {
            return this == INSTANT_HEALTH ? "Instant" : (DRUID_BUFF_DURATION_SECONDS + "s");
        }

        void apply(SuperpowerManager manager, Player target) {
            switch (this) {
                case STRENGTH -> manager.applyPotion(target, PotionEffectType.STRENGTH, DRUID_BUFF_DURATION_SECONDS * 20, 0);
                case SPEED -> manager.applyPotion(target, PotionEffectType.SPEED, DRUID_BUFF_DURATION_SECONDS * 20, 0);
                case REGENERATION -> manager.applyPotion(target, PotionEffectType.REGENERATION, DRUID_BUFF_DURATION_SECONDS * 20, 0);
                case RESISTANCE -> manager.applyPotion(target, PotionEffectType.RESISTANCE, DRUID_BUFF_DURATION_SECONDS * 20, 0);
                case FIRE_RESISTANCE -> manager.applyPotion(target, PotionEffectType.FIRE_RESISTANCE, DRUID_BUFF_DURATION_SECONDS * 20, 0);
                case ABSORPTION -> manager.applyPotion(target, PotionEffectType.ABSORPTION, DRUID_BUFF_DURATION_SECONDS * 20, 0);
                case INSTANT_HEALTH -> target.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 0, true, true, true));
            }
        }

        static DruidBlessing fromSlot(int slot) {
            for (DruidBlessing blessing : values()) {
                if (blessing.slot == slot) {
                    return blessing;
                }
            }
            return null;
        }
    }

    private record PortalPair(UUID ownerId, Location source, Location target, long expiresAt) {}
}
