package me.rique.smpcore.power;

import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.combat.AbilityDamageContext;
import me.rique.smpcore.awakening.AwakeningTableListener;
import me.rique.smpcore.boss.BossManager;
import me.rique.smpcore.item.CustomEnchantListener;
import me.rique.smpcore.player.SpawnProtectionListener;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.InventoryRecipeUtil;
import me.rique.smpcore.util.ItemModelUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Light;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
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
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.world.ChunkLoadEvent;
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
    private static final String POWER_COMMAND_BYPASS_PERMISSION = "smpcore.superpower.command.all";

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Component POWERS_MENU_TITLE =
        MM.deserialize("<gradient:#ff9a3d:#d61c4e><bold>Class Info</bold></gradient>");
    private static final Component POWER_CHOICE_MENU_TITLE =
        MM.deserialize("<gradient:#ff4d6d:#7c3aed><bold>Choose Your Fate</bold></gradient>");
    private static final Component DRUID_MENU_TITLE =
        MM.deserialize("<gradient:#63c74d:#2f8f47><bold>Druid's Grimoire</bold></gradient>");

    private static final int MENU_SIZE = 54;
    private static final int DRUID_MENU_SIZE = 54;
    private static final int[] POWER_CHOICE_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
    private static final int POWER_CHOICE_PREVIOUS_SLOT = 45;
    private static final int POWER_CHOICE_CANCEL_SLOT = 49;
    private static final int POWER_CHOICE_NEXT_SLOT = 53;
    private static final int SHADOW_DURATION_SECONDS = 15 * 60;
    private static final int SHADOW_COOLDOWN_SECONDS = 5 * 60;
    private static final int SHADOW_HIT_COOLDOWN_SECONDS = 7 * 60;
    private static final int TRAVEL_PORTAL_DURATION_SECONDS = 60;
    private static final int MONARCH_STORAGE_LIMIT = 15;
    private static final double MONARCH_TARGET_RANGE = 34.0;
    private static final double MONARCH_TARGET_VERTICAL_RANGE = 18.0;
    private static final double MONARCH_SUMMON_HEALTH = 40.0;
    private static final double MONARCH_MIN_DAMAGE = 10.0;
    private static final double MONARCH_DAMAGE_MULTIPLIER = 1.85;
    private static final double MONARCH_MIN_SPEED = 0.31;
    private static final double MONARCH_MIN_ARMOR = 10.0;
    private static final double MONARCH_MIN_ARMOR_TOUGHNESS = 4.0;
    private static final double MONARCH_KNOCKBACK_RESISTANCE = 0.35;
    private static final double MONARCH_BOSS_DAMAGE_MULTIPLIER = 0.30;
    private static final int FLORIST_HEAL_DURATION_SECONDS = 10;
    private static final int FLORIST_VINE_DAMAGE = 2;
    private static final int FLORIST_VINE_RANGE = 18;
    private static final double FLORIST_GROWTH_BONUS_CHANCE = 0.20;
    private static final int FLORIST_CROUCH_GROWTH_RADIUS = 2;
    private static final int FLORIST_CROUCH_GROWTH_STAGES = 2;
    private static final int DRUID_BUFF_RADIUS = 5;
    private static final int DRUID_BUFF_DURATION_SECONDS = 300;
    private static final int DRUID_BUFF_COOLDOWN_SECONDS = 90;
    private static final int HONORED_DOMAIN_HALF_RANGE = 8;
    private static final int HONORED_DOMAIN_DURATION_SECONDS = 15;
    private static final int HONORED_DOMAIN_COOLDOWN_SECONDS = 10 * 60;
    private static final int HONORED_DOMAIN_LEGACY_COOLDOWN_SECONDS = 2 * 60 * 60;
    private static final int HONORED_DOMAIN_PLATFORM_RADIUS = 8;
    private static final int HONORED_DOMAIN_WALL_HEIGHT = 7;
    private static final int HONORED_DOMAIN_LIGHT_HEIGHT = 4;
    private static final int HONORED_DOMAIN_LIGHT_OFFSET = 5;
    private static final int HONORED_DOMAIN_NAUSEA_DURATION_TICKS = 220;
    private static final int HONORED_DOMAIN_NAUSEA_AMPLIFIER = 24;
    private static final long HONORED_DOMAIN_SWING_COOLDOWN_MS = 450L;
    private static final double HONORED_DOMAIN_TEAMMATE_DAMAGE_MULTIPLIER = 1.25;
    private static final double HONORED_PROJECTILE_AURA_RADIUS = 5.0;
    private static final long HONORED_PROJECTILE_FREEZE_MS = 2500L;
    private static final int IMMORTALITY_REGEN_SECONDS = 10;
    private static final int IMMORTALITY_RESCUE_SECONDS = 4;
    private static final double IMMORTALITY_SURVIVAL_HEALTH = 1.0;
    private static final double BERSERK_LOW_HEALTH_THRESHOLD = 6.0;
    private static final int ENCHANTER_VIRTUAL_LAPIS_AMOUNT = 64;
    private static final double ENCHANTER_ARCANE_PRESERVATION_CHANCE = 0.25;
    private static final double ENCHANTER_LUCK_BONUS = 2.0;
    private static final double TANK_CROUCH_KNOCKBACK_RESISTANCE = 1.0;
    private static final int JUGGERNAUT_UNSTOPPABLE_DURATION_SECONDS = 20;
    private static final int JUGGERNAUT_UNSTOPPABLE_COOLDOWN_SECONDS = 5 * 60;
    private static final long JUGGERNAUT_IMPACT_INTERVAL_MS = 220L;
    private static final double JUGGERNAUT_UNSTOPPABLE_BREAK_HARDNESS_LIMIT = 12.0;
    private static final double JUGGERNAUT_UNSTOPPABLE_IMPACT_DAMAGE = 2.0;
    private static final double JUGGERNAUT_GROUND_SLAM_RADIUS = 5.0;
    private static final double JUGGERNAUT_GROUND_SLAM_MIN_FALL_DISTANCE = 4.0;
    private static final double JUGGERNAUT_GROUND_SLAM_MAX_PLAYER_DAMAGE = 16.0;
    private static final double JUGGERNAUT_FALL_DAMAGE_MULTIPLIER = 0.20;
    private static final double MINER_EXTRA_ORE_CHANCE = 0.25;
    private static final double MINER_HEALTH_BONUS = 2.0;
    private static final int MINER_NIGHT_VISION_Y_LEVEL = 32;
    private static final double GIANT_SCALE_MULTIPLIER = 1.2;
    private static final double GIANT_HEALTH_BONUS = 12.0;
    private static final double GIANT_KNOCKBACK_RESISTANCE = 0.40;
    private static final double GIANT_ATTACK_DAMAGE_BONUS = 2.0;
    private static final double SUPERMAN_HEALTH_BONUS = 20.0;
    private static final int SUPERMAN_FLIGHT_SECONDS = 30;
    private static final int SUPERMAN_FLIGHT_COOLDOWN_SECONDS = 5 * 60;
    private static final long SUPERMAN_BOOST_COOLDOWN_MS = 800L;
    private static final int XRAY_DURATION_SECONDS = 150;
    private static final int XRAY_COOLDOWN_SECONDS = 6 * 60;
    private static final int XRAY_ENTITY_RADIUS = 24;
    private static final int XRAY_ORE_RADIUS = 12;
    private static final int XRAY_ORE_VERTICAL_RADIUS = 8;
    private static final int XRAY_ORE_SCAN_BUDGET = 2048;
    private static final int XRAY_VALUABLE_ALERT_RADIUS = 3;
    private static final long XRAY_VALUABLE_ALERT_INTERVAL_MS = 8000L;
    private static final int TIME_STOP_RADIUS = 10;
    private static final int TIME_STOP_DURATION_SECONDS = 5;
    private static final int TIME_STOP_COOLDOWN_SECONDS = 5 * 60;
    private static final int PHOENIX_COOLDOWN_SECONDS = 10 * 60;
    private static final double PHOENIX_RECOVERY_HEALTH = 8.0;
    private static final double PHOENIX_BURST_RADIUS = 4.5;
    private static final double PHOENIX_BURST_DAMAGE = 4.0;
    private static final double PHOENIX_SEARING_STRIKE_DAMAGE = 1.0;
    private static final double PHOENIX_LOW_HEALTH_RATIO = 0.35;
    private static final int VOIDSTEP_COOLDOWN_SECONDS = 35;
    private static final double VOIDSTEP_RANGE = 30.0;
    private static final int VOIDSTEP_VEIL_SECONDS = 4;
    private static final int VOIDSTEP_SLOW_FALLING_SECONDS = 7;
    private static final int VOIDSTEP_INVISIBILITY_SECONDS = 12;
    private static final double VOIDSTEP_AMBUSH_DAMAGE = 3.0;
    private static final double SENTINEL_AURA_RADIUS = 7.0;
    private static final double SENTINEL_HEALTH_BONUS = 6.0;
    private static final double SENTINEL_BRACE_DAMAGE_REDUCTION = 0.35;
    private static final double WATERMAN_DAMAGE_MULTIPLIER = 1.25;
    private static final double WATERMAN_DAMAGE_REDUCTION = 0.25;
    private static final double WATERMAN_SUBMERGED_MINING_BONUS = 0.8;
    private static final int FROSTBORN_CHILL_SECONDS = 5;
    private static final double FROSTBORN_FROZEN_TARGET_DAMAGE_MULTIPLIER = 1.25;
    private static final double FROSTBORN_BOSS_DAMAGE_MULTIPLIER = 1.10;
    private static final double DEADEYE_PROJECTILE_DAMAGE_MULTIPLIER = 1.25;
    private static final double DEADEYE_MARKED_SHOT_DAMAGE_MULTIPLIER = 1.45;
    private static final double DEADEYE_BOSS_PROJECTILE_DAMAGE_MULTIPLIER = 1.15;
    private static final double DEADEYE_BOSS_MARKED_SHOT_DAMAGE_MULTIPLIER = 1.25;
    private static final double DEADEYE_MARKED_SHOT_VELOCITY_MULTIPLIER = 1.25;
    private static final int DEADEYE_GLOW_SECONDS = 5;
    private static final int DEADEYE_MARKED_SHOT_SLOW_SECONDS = 3;
    private static final double RIFTWARDEN_BOSS_RADIUS = 18.0;
    private static final double RIFTWARDEN_BOSS_DAMAGE_MULTIPLIER = 1.15;
    private static final double RIFTWARDEN_MOB_DAMAGE_MULTIPLIER = 1.12;
    private static final double RIFTWARDEN_BOSS_DAMAGE_REDUCTION = 0.15;
    private static final double RIFTWARDEN_MOB_DAMAGE_REDUCTION = 0.18;
    private static final double OATHBOUND_AURA_RADIUS = 9.0;
    private static final double OATHBOUND_SUMMON_BUFF_RADIUS = 8.0;
    private static final int OATHBOUND_SUMMON_BUFF_SECONDS = 30;
    private static final double RUNESMITH_PRESERVATION_CHANCE = 0.20;
    private static final double RUNESMITH_SPECIAL_PRESERVATION_CHANCE = 0.35;
    private static final double RUNESMITH_BOSS_REPAIR_RATIO = 0.15;
    private static final int ARCANIST_BOOK_UPGRADE_COOLDOWN_SECONDS = 5 * 60 * 60;
    private static final double GRAVEBORN_KILL_HEAL = 3.0;
    private static final double GRAVEBORN_PLAYER_KILL_HEAL = 8.0;
    private static final double GRAVEBORN_UNDEAD_DAMAGE_MULTIPLIER = 1.18;
    private static final double GRAVEBORN_UNDEAD_DAMAGE_REDUCTION = 0.25;
    private static final double GRAVEBORN_REVIVE_RADIUS = 8.0;
    private static final double GRAVEBORN_PLAYER_DEATH_BUFF_RADIUS = 12.0;
    private static final double STORMCALLER_PROC_CHANCE = 0.18;
    private static final double STORMCALLER_STORM_PROC_CHANCE = 0.32;
    private static final double STORMCALLER_DAMAGE_BONUS = 3.0;
    private static final int STORMCALLER_HEAVY_WEAPON_BUFF_SECONDS = 8;
    private static final double BLOODMENDER_PLAYER_LEECH_RATIO = 0.16;
    private static final double BLOODMENDER_MOB_LEECH_RATIO = 0.22;
    private static final double BLOODMENDER_MAX_LEECH = 3.0;
    private static final double BLOODMENDER_SACRIFICE_RADIUS = 12.0;
    private static final int BLOODMENDER_SACRIFICE_WEAKNESS_SECONDS = 5 * 60;
    private static final double BLOODMENDER_CURSE_RADIUS = 6.0;
    private static final int VEIL_ASSASSIN_CROUCH_SECONDS = 5;
    private static final double VEIL_ASSASSIN_MAX_HEALTH = 16.0;
    private static final double VEIL_ASSASSIN_SMOKE_RADIUS = 5.0;
    private static final int VEIL_ASSASSIN_SMOKE_DARKNESS_SECONDS = 15;
    private static final int VEIL_ASSASSIN_SMOKE_BUFF_SECONDS = 5;
    private static final int VEIL_ASSASSIN_SMOKE_COOLDOWN_SECONDS = 60;
    private static final double VEIL_ASSASSIN_BACKSTAB_RATIO = 0.90;
    private static final double VEIL_ASSASSIN_BOSS_BACKSTAB_MULTIPLIER = 1.20;
    private static final double VEIL_ASSASSIN_SNEAKING_SPEED_BONUS = 0.70;
    private static final int PASSIVE_NIGHT_VISION_TICKS = 600;
    private static final long PORTAL_RECENT_TRAVEL_MS = 2500L;
    private static final long FLORIST_CROUCH_GROWTH_COOLDOWN_MS = 150L;
    private static final long FLORIST_STICK_RIGHT_CLICK_COOLDOWN_MS = 1250L;
    private static final long FLORIST_STICK_LEFT_CLICK_COOLDOWN_MS = 650L;
    private static final long BEDROCK_POWER_ITEM_ACTIVATION_DEBOUNCE_MS = 650L;

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
    private final NamespacedKey keyVeilAssassinHealthModifier;
    private final NamespacedKey keyVeilAssassinSneakingSpeedModifier;
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
    private final NamespacedKey keyNightshadeNightVisionEnabled;
    private final NamespacedKey keyJuggernautUnstoppableActiveUntil;
    private final NamespacedKey keyJuggernautUnstoppableCooldownUntil;
    private final NamespacedKey keyArcanistBookUpgradeCooldownUntil;
    private final NamespacedKey keyDeadeyeArrowInfinityEnabled;
    private final NamespacedKey keyDeadeyeMarkedShot;
    private final NamespacedKey keyShadowCooldownUntil;
    private final NamespacedKey keyShadowActiveUntil;
    private final NamespacedKey keyDruidBuffCooldownUntil;
    private final NamespacedKey keyHonoredDomainCooldownUntil;
    private final NamespacedKey keyHonoredInfinityEnabled;
    private final NamespacedKey keyVeilAssassinSmokeBombCooldownUntil;
    private final NamespacedKey keyStormcallerLightningEnabled;
    private final NamespacedKey keyMonarchSummonOwner;
    private final NamespacedKey keyMonarchSummonTag;
    private final NamespacedKey keyMonarchBaseHealth;
    private final NamespacedKey keyMonarchBaseDamage;
    private final NamespacedKey keyMonarchBaseSpeed;
    private final Map<UUID, Integer> pendingFloristStickReturns = new ConcurrentHashMap<>();
    private final Map<UUID, PortalPair> activeTravelerPortals = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentPortalTravel = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> monarchSummonsByOwner = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> monarchOwnerByMob = new ConcurrentHashMap<>();
    private final Set<UUID> pendingMonarchUnsummonOwners = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> floristCrouchGrowthCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> floristLeftClickCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> floristRightClickCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> supermanBoostCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> juggernautImpactCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Float> juggernautPeakFallDistances = new ConcurrentHashMap<>();
    private final Map<UUID, Long> juggernautGroundSlamCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> oracleValuableAlertCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> oracleXrayScanCursors = new ConcurrentHashMap<>();
    private final Map<UUID, Long> veilAssassinCrouchStartedAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> veilAssassinSmokeInvisibilityUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> veilAssassinArmorWarnCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> bedrockPowerItemActivationDebounces = new ConcurrentHashMap<>();
    private final Map<UUID, TimeStopState> activeTimeStops = new ConcurrentHashMap<>();
    private final Map<UUID, FrozenMobState> frozenMobs = new ConcurrentHashMap<>();
    private final Map<UUID, FrozenProjectileState> frozenProjectiles = new ConcurrentHashMap<>();
    private final Map<UUID, HonoredDomainState> activeHonoredDomains = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pendingHonoredDomainReturns = new ConcurrentHashMap<>();
    private final Map<UUID, Location> honoredDomainDeathChestOrigins = new ConcurrentHashMap<>();
    private final Map<UUID, HonoredFrozenProjectileState> honoredFrozenProjectiles = new ConcurrentHashMap<>();
    private final Map<UUID, Long> honoredDomainSwingCooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> timeStoppedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> honoredDomainParalyzedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> honoredDomainDamageGuards = ConcurrentHashMap.newKeySet();
    private final Set<UUID> veilAssassinsInVeil = ConcurrentHashMap.newKeySet();
    private BukkitTask passiveTask;
    private BukkitTask portalTask;
    private BukkitTask timeStopTask;
    private BukkitTask honoredTask;
    private long honoredAuraPulse;

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
        this.keyVeilAssassinHealthModifier = new NamespacedKey(plugin, "superpower_veil_assassin_health");
        this.keyVeilAssassinSneakingSpeedModifier = new NamespacedKey(plugin, "superpower_veil_assassin_sneaking_speed");
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
        this.keyNightshadeNightVisionEnabled = new NamespacedKey(plugin, "superpower_nightshade_night_vision_enabled");
        this.keyJuggernautUnstoppableActiveUntil = new NamespacedKey(plugin, "superpower_juggernaut_unstoppable_until");
        this.keyJuggernautUnstoppableCooldownUntil = new NamespacedKey(plugin, "superpower_juggernaut_unstoppable_cooldown_until");
        this.keyArcanistBookUpgradeCooldownUntil = new NamespacedKey(plugin, "superpower_arcanist_book_upgrade_cooldown_until");
        this.keyDeadeyeArrowInfinityEnabled = new NamespacedKey(plugin, "superpower_deadeye_arrow_infinity_enabled");
        this.keyDeadeyeMarkedShot = new NamespacedKey(plugin, "superpower_deadeye_marked_shot");
        this.keyShadowCooldownUntil = new NamespacedKey(plugin, "superpower_shadow_cooldown_until");
        this.keyShadowActiveUntil = new NamespacedKey(plugin, "superpower_shadow_active_until");
        this.keyDruidBuffCooldownUntil = new NamespacedKey(plugin, "superpower_druid_buff_cooldown_until");
        this.keyHonoredDomainCooldownUntil = new NamespacedKey(plugin, "superpower_honored_domain_cooldown_until");
        this.keyHonoredInfinityEnabled = new NamespacedKey(plugin, "superpower_honored_infinity_enabled");
        this.keyVeilAssassinSmokeBombCooldownUntil = new NamespacedKey(plugin, "superpower_veil_assassin_smoke_cooldown_until");
        this.keyStormcallerLightningEnabled = new NamespacedKey(plugin, "superpower_stormcaller_lightning_enabled");
        this.keyMonarchSummonOwner = new NamespacedKey(plugin, "superpower_monarch_owner");
        this.keyMonarchSummonTag = new NamespacedKey(plugin, "superpower_monarch_summon");
        this.keyMonarchBaseHealth = new NamespacedKey(plugin, "superpower_monarch_base_health");
        this.keyMonarchBaseDamage = new NamespacedKey(plugin, "superpower_monarch_base_damage");
        this.keyMonarchBaseSpeed = new NamespacedKey(plugin, "superpower_monarch_base_speed");
    }

    public void start() {
        passiveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPlayers, 20L, 20L);
        portalTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPortals, 5L, 5L);
        timeStopTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTimeStops, 1L, 1L);
        honoredTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickHonoredPowers, 1L, 1L);
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::initializePlayerState));
    }

    public void shutdown() {
        endAllHonoredDomains(false);
        restoreHonoredFrozenProjectiles(false);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isShadowActive(player)) {
                restoreShadowAppearance(player);
            }
            clearVeilAssassinState(player, true);
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
        if (honoredTask != null) {
            honoredTask.cancel();
            honoredTask = null;
        }
        for (UUID ownerId : new HashSet<>(activeTravelerPortals.keySet())) {
            closeTravelerPortal(ownerId, false);
        }
        monarchSummonsByOwner.clear();
        monarchOwnerByMob.clear();
        pendingMonarchUnsummonOwners.clear();
        clearAllTimeStops();
        pendingHonoredDomainReturns.clear();
        honoredDomainDeathChestOrigins.clear();
        honoredDomainParalyzedPlayers.clear();
        honoredDomainDamageGuards.clear();
        honoredDomainSwingCooldowns.clear();
        pendingFloristStickReturns.clear();
        recentPortalTravel.clear();
        floristCrouchGrowthCooldowns.clear();
        floristLeftClickCooldowns.clear();
        floristRightClickCooldowns.clear();
        supermanBoostCooldowns.clear();
        juggernautImpactCooldowns.clear();
        juggernautPeakFallDistances.clear();
        juggernautGroundSlamCooldowns.clear();
        oracleValuableAlertCooldowns.clear();
        oracleXrayScanCursors.clear();
        veilAssassinCrouchStartedAt.clear();
        veilAssassinSmokeInvisibilityUntil.clear();
        veilAssassinArmorWarnCooldowns.clear();
        veilAssassinsInVeil.clear();
        bedrockPowerItemActivationDebounces.clear();
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

    public boolean refreshPowerItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        if (isAncientScroll(item)) {
            applyAncientScrollState(meta);
            applyAncientScrollPresentation(meta);
        } else if (isWardenHeart(item)) {
            applyWardenHeartState(meta);
            applyWardenHeartPresentation(meta);
        } else if (isMotherNatureStick(item)) {
            applyMotherNatureStickState(meta);
            applyMotherNatureStickPresentation(meta);
        } else if (isTheWorldClock(item)) {
            applyTheWorldClockState(meta);
            applyTheWorldClockPresentation(meta);
        } else if (isDruidGrimoire(item)) {
            applyDruidGrimoireState(meta);
            applyDruidGrimoirePresentation(meta);
        } else {
            return false;
        }

        item.setItemMeta(meta);
        return true;
    }

    public void openAdminInfoMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(
            new PowerInfoHolder(),
            MENU_SIZE,
            BedrockCompat.menuTitle(player, POWERS_MENU_TITLE, "Class Info")
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

    private boolean hasCommandPower(Player player, SuperpowerType type) {
        return player != null
            && (hasPower(player, type) || player.hasPermission(POWER_COMMAND_BYPASS_PERMISSION));
    }

    public boolean hasOathSummonTarget(Player requester) {
        if (requester == null || !requester.isOnline()) {
            return false;
        }
        UUID requesterId = requester.getUniqueId();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.isDead() || target.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (hasPower(target, SuperpowerType.OATHBOUND)
                && sameTeamOrSelf(requesterId, target.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    public boolean shouldRetainFlightAccess(Player player) {
        if (player == null || !hasPower(player, SuperpowerType.SKYBOUND)) {
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
        if (!hasCommandPower(player, SuperpowerType.NIGHTSHADE)) {
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

    public boolean handleNightshadeVisionCommand(Player player, Boolean requestedEnabled) {
        if (!hasCommandPower(player, SuperpowerType.NIGHTSHADE)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }

        boolean enabled = requestedEnabled == null ? !isNightshadeNightVisionEnabled(player) : requestedEnabled;
        setNightshadeNightVisionEnabled(player, enabled);
        if (enabled) {
            applyPassiveNightVision(player);
            player.sendMessage(MessageUtil.success("Night vision <white>enabled</white>."));
        } else {
            removePassiveNightVision(player);
            player.sendMessage(MessageUtil.info("Night vision <white>disabled</white>."));
        }
        return true;
    }

    public boolean handleNightshadeVisionStatusCommand(Player player) {
        if (!hasCommandPower(player, SuperpowerType.NIGHTSHADE)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }

        player.sendMessage(MessageUtil.info(
            "Nightshade night vision is <white>" + (isNightshadeNightVisionEnabled(player) ? "enabled" : "disabled") + "</white>."
        ));
        return true;
    }

    public boolean handleSmokeBombCommand(Player player) {
        if (!hasCommandPower(player, SuperpowerType.VEIL_ASSASSIN)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            player.sendMessage(MessageUtil.warn("You cannot vanish right now."));
            return false;
        }
        if (denyUnsafeSpawnAbilityArea(player, player.getLocation(), VEIL_ASSASSIN_SMOKE_RADIUS)) {
            return false;
        }

        long now = System.currentTimeMillis();
        long cooldownUntil = veilAssassinSmokeBombCooldownUntil(player);
        if (cooldownUntil > now) {
            player.sendMessage(MessageUtil.warn(
                "Smoke Bomb cooldown: <white>" + formatShortDuration(cooldownUntil - now) + "</white>."
            ));
            return false;
        }

        setVeilAssassinSmokeBombCooldownUntil(player, now + (VEIL_ASSASSIN_SMOKE_COOLDOWN_SECONDS * 1000L));
        veilAssassinSmokeInvisibilityUntil.put(player.getUniqueId(), now + (VEIL_ASSASSIN_SMOKE_BUFF_SECONDS * 1000L));
        applyPotion(player, PotionEffectType.SPEED, VEIL_ASSASSIN_SMOKE_BUFF_SECONDS * 20, 2);
        applyPotion(player, PotionEffectType.DOLPHINS_GRACE, VEIL_ASSASSIN_SMOKE_BUFF_SECONDS * 20, 0);
        applyPotion(player, PotionEffectType.INVISIBILITY, VEIL_ASSASSIN_SMOKE_BUFF_SECONDS * 20, 0);
        hideShadowEquipment(player);
        refreshVeilAssassinConcealment(player);

        int blinded = 0;
        for (Entity entity : player.getNearbyEntities(VEIL_ASSASSIN_SMOKE_RADIUS, VEIL_ASSASSIN_SMOKE_RADIUS, VEIL_ASSASSIN_SMOKE_RADIUS)) {
            if (!(entity instanceof Player target) || target.equals(player) || target.isDead() || target.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            applyPotion(target, PotionEffectType.DARKNESS, VEIL_ASSASSIN_SMOKE_DARKNESS_SECONDS * 20, 0);
            blinded++;
        }

        renderSmokeBomb(player.getLocation());
        player.sendMessage(MessageUtil.success("Smoke Bomb dropped."));
        if (blinded > 0) {
            player.sendMessage(MessageUtil.info("Blinded <white>" + blinded + "</white> nearby player(s)."));
        }
        return true;
    }

    public boolean handleXrayCommand(Player player) {
        if (!hasCommandPower(player, SuperpowerType.ORACLE_EYE)) {
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
        oracleXrayScanCursors.put(player.getUniqueId(), 0);
        setXrayCooldownUntil(player, now + (XRAY_COOLDOWN_SECONDS * 1000L));
        player.sendMessage(MessageUtil.success("Oracle Eye tears through the walls around you."));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.8f);
        renderXrayHighlights(player);
        return true;
    }

    public boolean handleUnstoppableForceCommand(Player player) {
        if (!hasCommandPower(player, SuperpowerType.JUGGERNAUT)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            player.sendMessage(MessageUtil.warn("You cannot charge right now."));
            return false;
        }
        if (denyUnsafeSpawnAbilityArea(player, player.getLocation(), 3.0, 3.0, 3.0)) {
            return false;
        }

        long now = System.currentTimeMillis();
        long activeUntil = juggernautUnstoppableActiveUntil(player);
        if (activeUntil > now) {
            player.sendMessage(MessageUtil.warn(
                "Unstoppable Force is already active for <white>" + secondsLeft(activeUntil, now) + "s</white>."
            ));
            return false;
        }

        long cooldownUntil = juggernautUnstoppableCooldownUntil(player);
        if (cooldownUntil > now) {
            player.sendMessage(MessageUtil.warn(
                "Unstoppable Force cooldown: <white>" + formatShortDuration(cooldownUntil - now) + "</white>."
            ));
            return false;
        }

        setJuggernautUnstoppableActiveUntil(player, now + (JUGGERNAUT_UNSTOPPABLE_DURATION_SECONDS * 1000L));
        setJuggernautUnstoppableCooldownUntil(player, now + (JUGGERNAUT_UNSTOPPABLE_COOLDOWN_SECONDS * 1000L));
        applyPotion(player, PotionEffectType.RESISTANCE, JUGGERNAUT_UNSTOPPABLE_DURATION_SECONDS * 20, 1);
        applyPotion(player, PotionEffectType.SPEED, JUGGERNAUT_UNSTOPPABLE_DURATION_SECONDS * 20, 0);
        player.sendMessage(MessageUtil.success("Unstoppable Force active for <white>20s</white>. Sprint into walls."));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.9f, 0.75f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().clone().add(0.0, 1.0, 0.0), 40, 0.7, 0.45, 0.7, 0.08);
        return true;
    }

    public boolean handleDeadeyeArrowInfinityCommand(Player player, Boolean requestedEnabled) {
        if (!hasCommandPower(player, SuperpowerType.DEADEYE)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }

        boolean enabled = requestedEnabled == null ? !isDeadeyeArrowInfinityEnabled(player) : requestedEnabled;
        setDeadeyeArrowInfinityEnabled(player, enabled);
        player.sendMessage((enabled ? MessageUtil.success("Deadeye arrows <white>preserved</white>.") : MessageUtil.info("Deadeye arrows <white>consume normally</white>.")));
        return true;
    }

    public boolean handleDeadeyeArrowInfinityStatusCommand(Player player) {
        if (!hasCommandPower(player, SuperpowerType.DEADEYE)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }

        player.sendMessage(MessageUtil.info(
            "Deadeye arrow preservation is <white>" + (isDeadeyeArrowInfinityEnabled(player) ? "enabled" : "disabled") + "</white>."
        ));
        return true;
    }

    public boolean handleArcanistBookUpgradeCommand(Player player) {
        if (!hasCommandPower(player, SuperpowerType.ARCANIST)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }

        long now = System.currentTimeMillis();
        long cooldownUntil = arcanistBookUpgradeCooldownUntil(player);
        if (cooldownUntil > now) {
            player.sendMessage(MessageUtil.warn(
                "Arcane upgrade cooldown: <white>" + formatShortDuration(cooldownUntil - now) + "</white>."
            ));
            return false;
        }

        ItemStack book = player.getInventory().getItemInMainHand();
        if (book == null || book.getType() != Material.ENCHANTED_BOOK || !(book.getItemMeta() instanceof EnchantmentStorageMeta meta)) {
            player.sendMessage(MessageUtil.warn("Hold one enchanted book first."));
            return false;
        }
        if (book.getAmount() != 1) {
            player.sendMessage(MessageUtil.warn("Hold only one enchanted book at a time."));
            return false;
        }
        if (!meta.hasStoredEnchants()) {
            player.sendMessage(MessageUtil.warn("That book has no stored enchants."));
            return false;
        }

        Map<Enchantment, Integer> upgrades = new HashMap<>();
        for (Map.Entry<Enchantment, Integer> entry : meta.getStoredEnchants().entrySet()) {
            Enchantment enchantment = entry.getKey();
            if (enchantment == null) {
                continue;
            }
            int currentLevel = Math.max(0, entry.getValue());
            int maxLevel = Math.max(1, enchantment.getMaxLevel());
            if (currentLevel > 0 && currentLevel < maxLevel) {
                upgrades.put(enchantment, maxLevel);
            }
        }
        if (upgrades.isEmpty()) {
            player.sendMessage(MessageUtil.info("That book is already maxed."));
            return false;
        }

        for (Map.Entry<Enchantment, Integer> upgrade : upgrades.entrySet()) {
            meta.removeStoredEnchant(upgrade.getKey());
            meta.addStoredEnchant(upgrade.getKey(), upgrade.getValue(), true);
        }
        book.setItemMeta(meta);
        setArcanistBookUpgradeCooldownUntil(player, now + (ARCANIST_BOOK_UPGRADE_COOLDOWN_SECONDS * 1000L));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.9f, 1.35f);
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().clone().add(0.0, 1.1, 0.0), 36, 0.45, 0.6, 0.45, 0.04);
        player.sendMessage(MessageUtil.success("The book's enchantment level was maxed."));
        return true;
    }

    public boolean handleOathSummonCommand(Player requester, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            requester.sendMessage(MessageUtil.warn("Use <white>/oathsummon <player></white>."));
            return false;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            requester.sendMessage(MessageUtil.warn("That player is not online."));
            return false;
        }
        if (!hasPower(target, SuperpowerType.OATHBOUND)) {
            requester.sendMessage(MessageUtil.warn("That player is not Oathbound."));
            return false;
        }
        if (!sameTeamOrSelf(requester.getUniqueId(), target.getUniqueId())) {
            requester.sendMessage(MessageUtil.warn("They are not on your team."));
            return false;
        }
        if (target.isDead() || target.getGameMode() == GameMode.SPECTATOR) {
            requester.sendMessage(MessageUtil.warn("They cannot be summoned right now."));
            return false;
        }
        if (isPowerFrozenPlayer(target.getUniqueId())) {
            requester.sendMessage(MessageUtil.warn("They are locked by another class right now."));
            return false;
        }
        if (isActiveBossFight(requester) || isActiveBossFight(target)) {
            requester.sendMessage(MessageUtil.warn("Oath Summon cannot move players into or out of an active boss fight."));
            return false;
        }
        if (denyUnsafeSpawnAbility(requester, requester.getLocation())
            || denyUnsafeSpawnAbility(requester, target.getLocation())) {
            return false;
        }

        Location destination = requester.getLocation().clone();
        if (target.isInsideVehicle()) {
            target.leaveVehicle();
        }
        target.teleportAsync(destination).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!requester.isOnline() || !target.isOnline()) {
                return;
            }
            if (!ok) {
                requester.sendMessage(MessageUtil.error("Oath summon failed."));
                return;
            }
            target.setFallDistance(0.0f);
            applyOathSummonBuffs(target);
            requester.sendMessage(MessageUtil.success("Summoned <white>" + target.getName() + "</white>."));
            if (!requester.equals(target)) {
                target.sendMessage(MessageUtil.info("<white>" + requester.getName() + "</white> called your oath."));
            }
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 0.75f);
            target.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, target.getLocation().clone().add(0.0, 1.0, 0.0), 34, 0.65, 0.55, 0.65, 0.02);
        }));
        return true;
    }

    public boolean handleBloodSacrificeCommand(Player player) {
        if (!hasCommandPower(player, SuperpowerType.BLOODMENDER)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            player.sendMessage(MessageUtil.warn("You cannot sacrifice right now."));
            return false;
        }
        if (player.getHealth() <= 2.0) {
            player.sendMessage(MessageUtil.warn("You are too weak to sacrifice more blood."));
            return false;
        }
        if (denyUnsafeSpawnAbilityArea(player, player.getLocation(), BLOODMENDER_SACRIFICE_RADIUS, 4.0, BLOODMENDER_SACRIFICE_RADIUS)) {
            return false;
        }

        List<Player> targets = nearbyBloodmenderTeammates(player);
        if (targets.isEmpty()) {
            player.sendMessage(MessageUtil.warn("No nearby teammates need your blood."));
            return false;
        }

        BossManager bossManager = plugin.getBossManager();
        if (bossManager != null) {
            targets = targets.stream()
                .filter(target -> !bossManager.blockHealingIfSuppressed(target, Math.max(0.0, maxHealth(target) - target.getHealth())))
                .toList();
        }
        if (targets.isEmpty()) {
            player.sendMessage(MessageUtil.warn("Healing is sealed. No blood was consumed."));
            return false;
        }

        double newHealth = Math.max(1.0, player.getHealth() * 0.5);
        player.setHealth(newHealth);
        applyPotion(player, PotionEffectType.WEAKNESS, BLOODMENDER_SACRIFICE_WEAKNESS_SECONDS * 20, 1);

        for (Player target : targets) {
            healPlayer(target, maxHealth(target));
            target.setFireTicks(0);
            target.getWorld().spawnParticle(Particle.HEART, target.getLocation().clone().add(0.0, 1.0, 0.0), 12, 0.45, 0.45, 0.45, 0.02);
            target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.45f, 1.45f);
            target.sendMessage(MessageUtil.success("<white>" + player.getName() + "</white> fully healed you."));
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_HURT, 0.7f, 1.3f);
        player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, player.getLocation().clone().add(0.0, 1.0, 0.0), 24, 0.5, 0.55, 0.5, 0.05);
        player.sendMessage(MessageUtil.success("You healed <white>" + targets.size() + "</white> teammate(s). Weakness II for <white>5m</white>."));
        return true;
    }

    public boolean handleBloodCurseCommand(Player player) {
        if (!hasCommandPower(player, SuperpowerType.BLOODMENDER)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }
        if (denyUnsafeSpawnAbilityArea(player, player.getLocation(), BLOODMENDER_CURSE_RADIUS, 4.0, BLOODMENDER_CURSE_RADIUS)) {
            return false;
        }

        List<ArmorCurseTarget> validTargets = nearbyCurseArmorTargets(player);
        if (validTargets.isEmpty()) {
            player.sendMessage(MessageUtil.warn("No enemy armor can be cursed within <white>6 blocks</white>."));
            return false;
        }

        ArmorCurseTarget chosen = validTargets.get(ThreadLocalRandom.current().nextInt(validTargets.size()));
        ItemStack armor = chosen.item();
        ItemMeta meta = armor.getItemMeta();
        if (meta == null) {
            player.sendMessage(MessageUtil.error("That armor could not be cursed."));
            return false;
        }
        meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        armor.setItemMeta(meta);
        switch (chosen.slot()) {
            case HEAD -> chosen.target().getInventory().setHelmet(armor);
            case CHEST -> chosen.target().getInventory().setChestplate(armor);
            case LEGS -> chosen.target().getInventory().setLeggings(armor);
            case FEET -> chosen.target().getInventory().setBoots(armor);
            default -> {
                player.sendMessage(MessageUtil.error("That armor could not be cursed."));
                return false;
            }
        }

        player.sendMessage(MessageUtil.success("Cursed <white>" + chosen.target().getName() + "</white>'s armor."));
        chosen.target().sendMessage(MessageUtil.warn("A Bloodmender cursed one piece of your armor."));
        chosen.target().getWorld().playSound(chosen.target().getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.75f, 1.45f);
        chosen.target().getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, chosen.target().getLocation().clone().add(0.0, 1.0, 0.0), 18, 0.45, 0.5, 0.45, 0.04);
        return true;
    }

    public boolean handleVoidstepCommand(Player player) {
        if (!hasCommandPower(player, SuperpowerType.VOIDWALKER)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            player.sendMessage(MessageUtil.warn("You cannot step through the void right now."));
            return false;
        }
        if (isActiveBossFight(player)) {
            player.sendMessage(MessageUtil.warn("Voidstep is sealed during active boss fights."));
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
        if (denyUnsafeSpawnAbility(player, player.getLocation()) || denyUnsafeSpawnAbility(player, destination)) {
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
        if (!hasCommandPower(player, SuperpowerType.VOIDWALKER)) {
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
        if (!hasCommandPower(player, SuperpowerType.STORMCALLER)) {
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
        if (!hasCommandPower(player, SuperpowerType.STORMCALLER)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }

        player.sendMessage(MessageUtil.info(
            "Stormcaller lightning is <white>" + (isStormcallerLightningEnabled(player) ? "enabled" : "disabled") + "</white>."
        ));
        return true;
    }

    public boolean handleDomainExpansionCommand(Player player) {
        if (!hasCommandPower(player, SuperpowerType.HONORED_ONE)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            player.sendMessage(MessageUtil.warn("You cannot open a domain right now."));
            return false;
        }
        if (isActiveBossFight(player)) {
            player.sendMessage(MessageUtil.warn("Domain Expansion cannot remove fighters from an active boss encounter."));
            return false;
        }
        if (denyUnsafeSpawnAbilityArea(player, player.getLocation(), HONORED_DOMAIN_HALF_RANGE, HONORED_DOMAIN_HALF_RANGE, HONORED_DOMAIN_HALF_RANGE)) {
            return false;
        }
        if (activeHonoredDomains.containsKey(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("Your domain is already open."));
            return false;
        }

        long now = System.currentTimeMillis();
        long cooldownUntil = honoredDomainCooldownUntil(player);
        if (cooldownUntil > now) {
            player.sendMessage(MessageUtil.warn(
                "Domain cooldown: <white>" + formatShortDuration(cooldownUntil - now) + "</white>."
            ));
            return false;
        }

        List<Player> participants = honoredDomainParticipants(player);
        if (participants.size() <= 1) {
            player.sendMessage(MessageUtil.warn("No one is close enough for the domain."));
            return false;
        }

        World endWorld = primaryWorld(World.Environment.THE_END);
        if (endWorld == null) {
            player.sendMessage(MessageUtil.error("The End is not available right now."));
            return false;
        }

        Location source = player.getLocation().clone();
        Location domainCenter = honoredDomainCenter(player, endWorld);
        List<BlockState> restoreBlocks = prepareHonoredDomainPlatform(domainCenter);
        Map<UUID, Location> returnLocations = new HashMap<>();
        Set<UUID> participantIds = new HashSet<>();
        HonoredDomainState state = new HonoredDomainState(
            player.getUniqueId(),
            domainCenter,
            now + (HONORED_DOMAIN_DURATION_SECONDS * 1000L),
            returnLocations,
            participantIds,
            restoreBlocks
        );
        activeHonoredDomains.put(player.getUniqueId(), state);
        setHonoredDomainCooldownUntil(player, now + (HONORED_DOMAIN_COOLDOWN_SECONDS * 1000L));

        if (source.getWorld() != null) {
            source.getWorld().playSound(source, Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 0.8f);
            source.getWorld().spawnParticle(Particle.REVERSE_PORTAL, source.clone().add(0.0, 1.0, 0.0), 90, 1.1, 0.9, 1.1, 0.18);
        }

        int teleported = 0;
        for (Player target : participants) {
            if (!target.isOnline() || target.isDead() || target.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (target.isInsideVehicle()) {
                target.leaveVehicle();
            }
            UUID targetId = target.getUniqueId();
            returnLocations.put(targetId, target.getLocation().clone());
            participantIds.add(targetId);
            Location destination = honoredDomainSpawn(domainCenter, teleported, participants.size(), target);
            clearHonoredDomainTeleportVisualsNowAndLater(target);
            boolean moved = target.teleport(destination);
            if (!moved) {
                participantIds.remove(targetId);
                returnLocations.remove(targetId);
                clearHonoredDomainTeleportVisualsNowAndLater(target);
                continue;
            }
            clearHonoredDomainTeleportVisualsNowAndLater(target);
            target.setFallDistance(0.0f);
            if (!targetId.equals(player.getUniqueId())) {
                honoredDomainParalyzedPlayers.add(targetId);
                applyHonoredDomainLockEffects(target);
                target.sendMessage(MessageUtil.warn("Unlimited Void locks around you."));
            } else {
                target.sendMessage(MessageUtil.success("Domain Expansion."));
            }
            target.playSound(target.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, targetId.equals(player.getUniqueId()) ? 1.45f : 0.7f);
            target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().clone().add(0.0, 1.0, 0.0), 30, 0.45, 0.6, 0.45, 0.04);
            teleported++;
        }

        if (teleported <= 1) {
            endHonoredDomain(player.getUniqueId(), false);
            setHonoredDomainCooldownUntil(player, 0L);
            player.sendMessage(MessageUtil.error("The domain failed to pull anyone in."));
            return false;
        }

        renderHonoredDomainOpening(state);
        return true;
    }

    public Location consumeHonoredDomainDeathChestOrigin(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        Location cached = honoredDomainDeathChestOrigins.remove(playerId);
        if (cached != null && cached.getWorld() != null) {
            return cached.clone();
        }
        Location activeReturn = honoredDomainReturnLocation(playerId);
        return activeReturn == null || activeReturn.getWorld() == null ? null : activeReturn.clone();
    }

    public boolean handleInfinityCommand(Player player, Boolean requestedEnabled) {
        if (!hasCommandPower(player, SuperpowerType.HONORED_ONE)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }

        boolean enabled = requestedEnabled == null ? !isHonoredInfinityEnabled(player) : requestedEnabled;
        setHonoredInfinityEnabled(player, enabled);
        if (enabled) {
            player.sendMessage(MessageUtil.success("Infinity is <white>on</white>."));
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.65f, 1.55f);
        } else {
            releaseHonoredFrozenProjectiles(player.getUniqueId());
            player.sendMessage(MessageUtil.info("Infinity is <white>off</white>."));
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_COPPER_BULB_TURN_OFF, 0.65f, 0.8f);
        }
        return true;
    }

    public boolean handleInfinityStatusCommand(Player player) {
        if (!hasCommandPower(player, SuperpowerType.HONORED_ONE)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }
        player.sendMessage(MessageUtil.info(
            "Infinity is <white>" + (isHonoredInfinityEnabled(player) ? "on" : "off") + "</white>."
        ));
        return true;
    }

    public boolean handleMonarchSummonCommand(Player player, int amount) {
        if (!hasCommandPower(player, SuperpowerType.MONARCH)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }
        if (denyUnsafeSpawnAbilityArea(player, player.getLocation(), 5.0, 4.0, 5.0)) {
            return false;
        }

        List<EntityType> stored = cleanMonarchStorage(player, monarchStorage(player));
        if (stored.isEmpty()) {
            player.sendMessage(MessageUtil.warn("You have no stored hostile mobs to summon."));
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

    public boolean handleMonarchDespawnCommand(Player player) {
        if (!hasCommandPower(player, SuperpowerType.MONARCH)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }

        int removed = despawnMonarchSummons(player.getUniqueId(), true);
        player.sendMessage(removed > 0
            ? MessageUtil.success("Unsummoned <white>" + removed + "</white> Shadow Monarch summon(s).")
            : MessageUtil.info("No loaded Shadow Monarch summons were active. Any unloaded summons will be removed when their chunks load."));
        return true;
    }

    public boolean handleTravelCommand(Player player, int x, int y, int z, String dimensionRaw) {
        if (!hasCommandPower(player, SuperpowerType.WAYFARER)) {
            player.sendMessage(MessageUtil.warn("Nothing happens."));
            return false;
        }
        if (isActiveBossFight(player)) {
            player.sendMessage(MessageUtil.warn("Travel portals cannot open during an active boss fight."));
            return false;
        }

        World.Environment environment = parseEnvironment(dimensionRaw);
        if (environment == null) {
            player.sendMessage(MessageUtil.error("Use <white>overworld</white>, <white>nether</white>, or <white>end</white>."));
            return false;
        }
        if (!isWayfarerWorld(player.getWorld())) {
            player.sendMessage(MessageUtil.warn("Wayfarer portals cannot anchor in this world."));
            return false;
        }
        if (!canEnterWayfarerDimension(player, environment)) {
            player.sendMessage(MessageUtil.warn("That dimension is still locked."));
            return false;
        }
        if (!hasVisitedEnvironment(player, environment)) {
            player.sendMessage(MessageUtil.warn("That dimension has not opened itself to you yet."));
            return false;
        }

        World targetWorld = wayfarerWorld(environment);
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
        if (denyUnsafeSpawnAbilityArea(player, source, 2.5, 3.0, 2.5)
            || denyUnsafeSpawnAbilityArea(player, target, 2.5, 3.0, 2.5)) {
            return false;
        }

        closeTravelerPortal(player.getUniqueId(), false);
        Location safeSource = findSafeTravelLocation(source, false);
        Location targetAnchor = centeredPortalLocation(target);
        PortalPair pair = new PortalPair(
            player.getUniqueId(),
            source,
            targetAnchor,
            safeSource,
            targetAnchor,
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
        if (!hasCommandPower(player, SuperpowerType.WAYFARER)) {
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
        returnPendingHonoredDomainPlayer(player);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        markVisitedDimension(event.getPlayer(), event.getPlayer().getWorld());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getPlayer();
        rememberHonoredDomainDeathChestOrigin(player.getUniqueId());
        handleHonoredDomainPlayerExit(player.getUniqueId(), false);
        if (hasPower(player, SuperpowerType.VERDANT)) {
            ItemStack kept = null;
            List<ItemStack> drops = event.getDrops();
            for (int i = drops.size() - 1; i >= 0; i--) {
                ItemStack drop = drops.get(i);
                if (!isMotherNatureStick(drop)) {
                    continue;
                }
                if (kept == null) {
                    kept = drop.clone();
                    kept.setAmount(1);
                }
                drops.remove(i);
            }
            if (kept != null) {
                event.getItemsToKeep().add(kept);
            }
        }
        if (hasPower(player, SuperpowerType.THE_WORLD)) {
            List<ItemStack> drops = event.getDrops();
            for (int i = drops.size() - 1; i >= 0; i--) {
                ItemStack drop = drops.get(i);
                if (!isTheWorldClock(drop)) {
                    continue;
                }
                event.getItemsToKeep().add(drop.clone());
                drops.remove(i);
            }
        }
        if (hasPower(player, SuperpowerType.DRUID)) {
            List<ItemStack> drops = event.getDrops();
            for (int i = drops.size() - 1; i >= 0; i--) {
                ItemStack drop = drops.get(i);
                if (!isDruidGrimoire(drop)) {
                    continue;
                }
                event.getItemsToKeep().add(drop.clone());
                drops.remove(i);
            }
        }
        if (isShadowActive(player)) {
            deactivateShadow(player, false, false);
        }
        clearVeilAssassinState(player, true);
        stopSupermanFlight(player, false);
        syncTankImmovableState(player, false);
        clearPowerAttributeModifiers(player);
        clearVirtualEnchanterLapis(player);
        closeTravelerPortal(player.getUniqueId(), false);
        clearTimeStopForOwner(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Integer pendingValue = pendingFloristStickReturns.remove(player.getUniqueId());
        int pending = pendingValue == null ? 0 : pendingValue;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (pending > 0) {
                giveMotherNatureStick(player, true);
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
        clearTimeStopForOwner(playerId);
        handleHonoredDomainPlayerExit(playerId, true);
        timeStoppedPlayers.remove(playerId);
        honoredDomainParalyzedPlayers.remove(playerId);
        honoredDomainSwingCooldowns.remove(playerId);
        recentPortalTravel.remove(playerId);
        floristCrouchGrowthCooldowns.remove(playerId);
        floristLeftClickCooldowns.remove(playerId);
        floristRightClickCooldowns.remove(playerId);
        supermanBoostCooldowns.remove(playerId);
        juggernautImpactCooldowns.remove(playerId);
        juggernautPeakFallDistances.remove(playerId);
        juggernautGroundSlamCooldowns.remove(playerId);
        oracleValuableAlertCooldowns.remove(playerId);
        oracleXrayScanCursors.remove(playerId);
        veilAssassinCrouchStartedAt.remove(playerId);
        veilAssassinSmokeInvisibilityUntil.remove(playerId);
        veilAssassinArmorWarnCooldowns.remove(playerId);
        veilAssassinsInVeil.remove(playerId);
        bedrockPowerItemActivationDebounces.remove(playerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExpGain(PlayerExpChangeEvent event) {
        if (!hasPower(event.getPlayer(), SuperpowerType.ARCANIST)) {
            return;
        }
        if (plugin.getXpLecternListener() != null && plugin.getXpLecternListener().isLecternBottleXpPickup(event.getPlayer())) {
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
        if (hasPower(player, SuperpowerType.ARCANIST)
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
        if (!hasPower(player, SuperpowerType.SKYBOUND)
            || player.getGameMode() == GameMode.CREATIVE
            || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (isActiveBossFight(player)) {
            event.setCancelled(true);
            stopSupermanFlight(player, false);
            player.sendMessage(MessageUtil.warn("Skybound flight is grounded during active boss fights."));
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
        if (!isPowerFrozenPlayer(player.getUniqueId())) {
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJuggernautUnstoppableMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!hasCommandPower(player, SuperpowerType.JUGGERNAUT) || !player.isSprinting()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (juggernautUnstoppableActiveUntil(player) <= now) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getWorld() != to.getWorld()) {
            return;
        }
        if (from.distanceSquared(to) < 0.0025) {
            return;
        }
        long nextImpactAt = juggernautImpactCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (nextImpactAt > now) {
            return;
        }

        if (tryJuggernautWallImpact(player)) {
            juggernautImpactCooldowns.put(player.getUniqueId(), now + JUGGERNAUT_IMPACT_INTERVAL_MS);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJuggernautFallTrack(PlayerMoveEvent event) {
        trackJuggernautFall(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHonoredDomainAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        if (isPowerFrozenPlayer(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (triggerHonoredDomainPulse(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHonoredDomainInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (isPowerFrozenPlayer(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (triggerHonoredDomainPulse(player)) {
            event.setCancelled(true);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareItemEnchant(PrepareItemEnchantEvent event) {
        if (!hasPower(event.getEnchanter(), SuperpowerType.ARCANIST)) {
            return;
        }
        if (event.getView().getTopInventory() instanceof EnchantingInventory enchanting) {
            refreshEnchanterLapis(event.getEnchanter(), enchanting);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        if (!hasPower(event.getEnchanter(), SuperpowerType.ARCANIST)) {
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
        if (hasPower(player, SuperpowerType.VEIL_ASSASSIN)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && hasPower(player, SuperpowerType.VEIL_ASSASSIN)) {
                    enforceVeilAssassinArmor(player);
                }
            });
        }
        if (!(event.getView().getTopInventory() instanceof EnchantingInventory enchanting)) {
            return;
        }

        if (!hasPower(player, SuperpowerType.ARCANIST)) {
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVeilAssassinEquipmentChanged(EntityEquipmentChangedEvent event) {
        if (!(event.getEntity() instanceof Player player) || !hasPower(player, SuperpowerType.VEIL_ASSASSIN)) {
            return;
        }
        boolean armorChanged = false;
        for (EquipmentSlot slot : event.getEquipmentChanges().keySet()) {
            if (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET) {
                armorChanged = true;
                break;
            }
        }
        if (!armorChanged) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && hasPower(player, SuperpowerType.VEIL_ASSASSIN)) {
                enforceVeilAssassinArmor(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnchanterInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory() instanceof EnchantingInventory enchanting)) {
            return;
        }

        if (!hasPower(player, SuperpowerType.ARCANIST)) {
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
        boolean lethalBossMechanic = isLethalBossMechanicDamage(player);

        if (hasPower(player, SuperpowerType.JUGGERNAUT)
            && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            double fallDamage = event.getDamage();
            Float trackedFall = juggernautPeakFallDistances.remove(player.getUniqueId());
            float fallDistance = Math.max(player.getFallDistance(), trackedFall == null ? 0.0f : trackedFall);
            tryTriggerJuggernautGroundSlam(player, fallDistance, fallDamage);
            event.setDamage(fallDamage * JUGGERNAUT_FALL_DAMAGE_MULTIPLIER);
        }

        if (!lethalBossMechanic && hasPower(player, SuperpowerType.ASHEN_SOUL)) {
            double finalDamage = event.getFinalDamage();
            if (finalDamage > 0.0 && player.getHealth() - finalDamage <= 0.0 && tryPhoenixRebirth(player, event.getCause())) {
                event.setCancelled(true);
                return;
            }
        }

        if (!lethalBossMechanic && hasPower(player, SuperpowerType.GRAVEBORN)) {
            double finalDamage = event.getFinalDamage();
            if (finalDamage > 0.0 && player.getHealth() - finalDamage <= 0.0 && tryGravebornSecondChance(player)) {
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

        if (hasPower(player, SuperpowerType.TIDEBORN)
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
        boolean honoredDomainSplash = event.getEntity() instanceof Player splashTarget
            && honoredDomainDamageGuards.remove(splashTarget.getUniqueId());
        if (event.getEntity() instanceof Player projectileTarget
            && hasCommandPower(projectileTarget, SuperpowerType.HONORED_ONE)
            && event.getDamager() instanceof Projectile projectile
            && shouldBlockHonoredIncomingProjectile(projectile, projectileTarget)) {
            event.setCancelled(true);
            freezeHonoredProjectile(projectile, projectileTarget);
            return;
        }

        LivingEntity source = actualLivingDamager(event.getDamager());
        if (source == null || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Player attacker = source instanceof Player player ? player : null;
        boolean customBossVictim = isCustomBoss(victim);
        if (customBossVictim && monarchOwnerOf(source) != null) {
            event.setDamage(event.getDamage() * MONARCH_BOSS_DAMAGE_MULTIPLIER);
        }
        if (blocksUnsafeSpawnCombat(attacker, victim)) {
            event.setCancelled(true);
            return;
        }
        boolean honoredDomainPulse = attacker != null && event.getDamage() > 0.0 && triggerHonoredDomainPulse(attacker);
        boolean friendlyTarget = attacker != null && isFriendlyTo(attacker, victim);
        if (!honoredDomainSplash
            && attacker != null
            && victim instanceof Player hitPlayer
            && shouldCancelRecentHonoredDomainDirectHit(attacker, hitPlayer)) {
            event.setCancelled(true);
            return;
        }

        if (attacker != null && event.getDamage() > 0.0 && !friendlyTarget) {
            if (hasPower(attacker, SuperpowerType.TIDEBORN) && isWatermanEmpowered(attacker)) {
                event.setDamage(event.getDamage() * WATERMAN_DAMAGE_MULTIPLIER);
                victim.getWorld().spawnParticle(Particle.SPLASH, victim.getLocation().clone().add(0.0, 1.0, 0.0), 18, 0.45, 0.35, 0.45, 0.05);
                victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_TRIDENT_HIT, 0.55f, 1.25f);
            }

            if (hasPower(attacker, SuperpowerType.ASHEN_SOUL)) {
                if (victim.getFireTicks() > 0) {
                    event.setDamage(event.getDamage() + PHOENIX_SEARING_STRIKE_DAMAGE);
                }
                victim.setFireTicks(Math.max(victim.getFireTicks(), 60));
                victim.getWorld().spawnParticle(Particle.FLAME, victim.getLocation().clone().add(0.0, 1.0, 0.0), 12, 0.35, 0.35, 0.35, 0.02);
            }

            if (hasPower(attacker, SuperpowerType.VOIDWALKER) && voidstepVeilUntil(attacker) > System.currentTimeMillis()) {
                event.setDamage(event.getDamage() + VOIDSTEP_AMBUSH_DAMAGE);
                if (!customBossVictim) {
                    applyPotion(victim, PotionEffectType.BLINDNESS, 60, 0);
                    applyPotion(victim, PotionEffectType.WEAKNESS, 60, 0);
                }
                setVoidstepVeilUntil(attacker, 0L);
                removeLikelyPowerPotion(attacker, PotionEffectType.INVISIBILITY, 0, VOIDSTEP_INVISIBILITY_SECONDS * 20 + 20);
                removeLikelyPowerPotion(attacker, PotionEffectType.SLOW_FALLING, 0, VOIDSTEP_SLOW_FALLING_SECONDS * 20 + 20);
                victim.getWorld().spawnParticle(Particle.PORTAL, victim.getLocation().clone().add(0.0, 1.0, 0.0), 35, 0.45, 0.45, 0.45, 0.06);
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ENDERMAN_HURT, 0.8f, 0.8f);
            }

            if (hasPower(attacker, SuperpowerType.FROSTBORN)) {
                if (customBossVictim) {
                    event.setDamage(event.getDamage() * FROSTBORN_BOSS_DAMAGE_MULTIPLIER);
                } else if (victim.getPotionEffect(PotionEffectType.SLOWNESS) != null) {
                    event.setDamage(event.getDamage() * FROSTBORN_FROZEN_TARGET_DAMAGE_MULTIPLIER);
                }
                if (!customBossVictim) {
                    applyPotion(victim, PotionEffectType.SLOWNESS, FROSTBORN_CHILL_SECONDS * 20, 0);
                    applyPotion(victim, PotionEffectType.WEAKNESS, FROSTBORN_CHILL_SECONDS * 20, 0);
                }
                victim.getWorld().spawnParticle(Particle.CLOUD, victim.getLocation().clone().add(0.0, 1.0, 0.0), 12, 0.35, 0.35, 0.35, 0.02);
            }

            if (hasPower(attacker, SuperpowerType.NIGHTSHADE)) {
                applyPotion(victim, PotionEffectType.POISON, 5 * 20, 0);
                victim.getWorld().spawnParticle(Particle.WITCH, victim.getLocation().clone().add(0.0, 1.0, 0.0), 10, 0.35, 0.35, 0.35, 0.01);
            }

            if (hasPower(attacker, SuperpowerType.VEIL_ASSASSIN)
                && isInVeilAssassinVeil(attacker)
                && isBehindTarget(attacker, victim)) {
                applyVeilAssassinBackstab(event, attacker, victim);
            }

            if (event.getDamager() instanceof Projectile projectile
                && hasPower(attacker, SuperpowerType.DEADEYE)) {
                boolean markedShot = projectile.getPersistentDataContainer().has(keyDeadeyeMarkedShot, PersistentDataType.BYTE);
                double multiplier = customBossVictim
                    ? (markedShot ? DEADEYE_BOSS_MARKED_SHOT_DAMAGE_MULTIPLIER : DEADEYE_BOSS_PROJECTILE_DAMAGE_MULTIPLIER)
                    : (markedShot ? DEADEYE_MARKED_SHOT_DAMAGE_MULTIPLIER : DEADEYE_PROJECTILE_DAMAGE_MULTIPLIER);
                event.setDamage(event.getDamage() * multiplier);
                applyPotion(victim, PotionEffectType.GLOWING, DEADEYE_GLOW_SECONDS * 20, 0);
                if (markedShot && !customBossVictim) {
                    applyPotion(victim, PotionEffectType.SLOWNESS, DEADEYE_MARKED_SHOT_SLOW_SECONDS * 20, 1);
                }
                victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().clone().add(0.0, 1.0, 0.0), markedShot ? 28 : 18, 0.35, 0.35, 0.35, 0.05);
            }

            if (hasPower(attacker, SuperpowerType.RIFTWARDEN)) {
                if (customBossVictim) {
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

            if (hasCommandPower(attacker, SuperpowerType.STORMCALLER) && tryStormcallerStrike(attacker, victim)) {
                event.setDamage(event.getDamage() + STORMCALLER_DAMAGE_BONUS);
            }
            if (victim instanceof Player && hasPower(attacker, SuperpowerType.STORMCALLER) && isStormcallerHeavyWeapon(attacker.getInventory().getItemInMainHand())) {
                applyPotion(attacker, PotionEffectType.RESISTANCE, STORMCALLER_HEAVY_WEAPON_BUFF_SECONDS * 20, 0);
                applyPotion(attacker, PotionEffectType.STRENGTH, STORMCALLER_HEAVY_WEAPON_BUFF_SECONDS * 20, 0);
                attacker.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, attacker.getLocation().clone().add(0.0, 1.0, 0.0), 10, 0.4, 0.45, 0.4, 0.04);
            }

            if (hasPower(attacker, SuperpowerType.BLOODMENDER)) {
                applyBloodmenderLeech(attacker, victim, event.getDamage());
            }
        }

        if (!honoredDomainSplash && !honoredDomainPulse && attacker != null && event.getDamage() > 0.0) {
            applyHonoredDomainDamage(event, attacker, victim);
        }

        if (!(victim instanceof Player defender) || source.getUniqueId().equals(defender.getUniqueId()) || isFriendlyTo(defender, source)) {
            return;
        }

        if (hasPower(defender, SuperpowerType.ASHEN_SOUL) && event.getDamage() > 0.0) {
            source.setFireTicks(Math.max(source.getFireTicks(), 60));
            applyPotion(defender, PotionEffectType.ABSORPTION, 80, 0);
            source.getWorld().spawnParticle(Particle.FLAME, source.getLocation().clone().add(0.0, 1.0, 0.0), 10, 0.35, 0.35, 0.35, 0.02);
        }

        if (hasPower(defender, SuperpowerType.SENTINEL) && defender.isSneaking() && event.getDamage() > 0.0) {
            event.setDamage(event.getDamage() * (1.0 - SENTINEL_BRACE_DAMAGE_REDUCTION));
            if (!isCustomBoss(source)) {
                applyPotion(source, PotionEffectType.WEAKNESS, 80, 0);
            }
            Vector push = source.getLocation().toVector().subtract(defender.getLocation().toVector());
            push.setY(0.0);
            if (!isCustomBoss(source) && push.lengthSquared() > 0.001) {
                source.setVelocity(source.getVelocity().add(push.normalize().multiply(0.45).setY(0.18)));
            }
            defender.getWorld().spawnParticle(Particle.CRIT, defender.getLocation().clone().add(0.0, 1.0, 0.0), 16, 0.55, 0.45, 0.55, 0.02);
            defender.getWorld().playSound(defender.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.75f, 0.9f);
        }

        if (hasPower(defender, SuperpowerType.TIDEBORN) && isWatermanEmpowered(defender) && event.getDamage() > 0.0) {
            event.setDamage(event.getDamage() * (1.0 - WATERMAN_DAMAGE_REDUCTION));
            if (!isCustomBoss(source)) {
                applyPotion(source, PotionEffectType.SLOWNESS, 60, 0);
            }
            defender.getWorld().spawnParticle(Particle.BUBBLE_POP, defender.getLocation().clone().add(0.0, 1.0, 0.0), 18, 0.65, 0.4, 0.65, 0.03);
            defender.getWorld().playSound(defender.getLocation(), Sound.BLOCK_CONDUIT_ATTACK_TARGET, 0.45f, 1.4f);
        }

        if (hasPower(defender, SuperpowerType.FROSTBORN) && event.getDamage() > 0.0) {
            if (!isCustomBoss(source)) {
                applyPotion(source, PotionEffectType.SLOWNESS, FROSTBORN_CHILL_SECONDS * 20, 0);
            }
            source.getWorld().spawnParticle(Particle.CLOUD, source.getLocation().clone().add(0.0, 1.0, 0.0), 10, 0.35, 0.35, 0.35, 0.02);
        }

        if (hasPower(defender, SuperpowerType.RIFTWARDEN) && event.getDamage() > 0.0 && !(source instanceof Player)) {
            double reduction = isCustomBoss(source) ? RIFTWARDEN_BOSS_DAMAGE_REDUCTION : RIFTWARDEN_MOB_DAMAGE_REDUCTION;
            event.setDamage(event.getDamage() * (1.0 - reduction));
            defender.getWorld().spawnParticle(Particle.PORTAL, defender.getLocation().clone().add(0.0, 1.0, 0.0), 14, 0.45, 0.35, 0.45, 0.05);
        }

        if (hasPower(defender, SuperpowerType.GRAVEBORN) && event.getDamage() > 0.0 && isUndeadPassiveType(source.getType())) {
            event.setDamage(event.getDamage() * (1.0 - GRAVEBORN_UNDEAD_DAMAGE_REDUCTION));
            if (!isCustomBoss(source)) {
                applyPotion(source, PotionEffectType.WEAKNESS, 60, 0);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player attackingPlayer = resolvePlayerDamager(event.getDamager());
        if (attackingPlayer != null && isPowerFrozenPlayer(attackingPlayer.getUniqueId())) {
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

        if (killer != null && hasPower(killer, SuperpowerType.MONARCH) && isMonarchStorable(entity)) {
            storeMonarchMob(killer, entity.getType());
        }

        if (entity instanceof Player deadPlayer) {
            applyNearbyGravebornDeathBuff(deadPlayer);
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
            return;
        }
        if (event.getTarget() instanceof Tameable tameable
            && tameable.getOwner() != null
            && sameTeamOrSelf(ownerId, tameable.getOwner().getUniqueId())) {
            event.setCancelled(true);
            mob.setTarget(null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (!(entity instanceof Mob mob) || !isTaggedMonarchSummon(mob)) {
                continue;
            }
            UUID ownerId = monarchOwnerIdFromPdc(mob);
            if (ownerId == null) {
                mob.remove();
                continue;
            }
            if (pendingMonarchUnsummonOwners.contains(ownerId)) {
                mob.remove();
                continue;
            }
            monarchOwnerByMob.put(mob.getUniqueId(), ownerId);
            monarchSummonsByOwner.computeIfAbsent(ownerId, ignored -> ConcurrentHashMap.newKeySet()).add(mob.getUniqueId());
            hardenMonarchSummon(mob, false);
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
        if (hasPower(player, SuperpowerType.JUGGERNAUT)) {
            syncTankImmovableState(player, event.isSneaking());
        }
        if (hasPower(player, SuperpowerType.SKYBOUND) && event.isSneaking()) {
            trySupermanBoost(player);
        }
        if (hasPower(player, SuperpowerType.VEIL_ASSASSIN)) {
            if (event.isSneaking()) {
                startVeilAssassinCrouch(player);
            } else {
                deactivateVeilAssassin(player, true);
            }
        }
        if (!hasPower(player, SuperpowerType.VERDANT)) {
            return;
        }
        pulseFloristGrowth(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFloristDrops(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.getGameMode() == GameMode.CREATIVE || !hasPower(player, SuperpowerType.VERDANT)) {
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
        if (player == null || player.getGameMode() == GameMode.CREATIVE || !hasPower(player, SuperpowerType.PROSPECTOR)) {
            return;
        }
        if (!MINER_ORE_BLOCKS.contains(event.getBlockState().getType())) {
            return;
        }
        if (plugin.getGoblinHuntManager() != null && !plugin.getGoblinHuntManager().isEligibleOreBreak(event.getBlock(), player)) {
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
        if (isPowerFrozenPlayer(event.getPlayer().getUniqueId())
            || isInsideAnyHonoredDomain(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTimeStoppedPlace(BlockPlaceEvent event) {
        if (isPowerFrozenPlayer(event.getPlayer().getUniqueId())
            || isInsideAnyHonoredDomain(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTimeStoppedProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        if (isPowerFrozenPlayer(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeadeyeCrossbowLoad(EntityLoadCrossbowEvent event) {
        if (!(event.getEntity() instanceof Player player)
            || !hasCommandPower(player, SuperpowerType.DEADEYE)
            || !isDeadeyeArrowInfinityEnabled(player)
            || !event.shouldConsumeItem()) {
            return;
        }
        ItemStack selected = likelyCrossbowConsumable(player);
        if (selected == null || !isArrowMaterial(selected.getType())) {
            return;
        }
        event.setConsumeItem(false);
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeadeyeBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)
            || !hasCommandPower(player, SuperpowerType.DEADEYE)
            || !isDeadeyeArrowInfinityEnabled(player)
            || !(event.getProjectile() instanceof AbstractArrow)
            || !event.shouldConsumeItem()) {
            return;
        }
        ItemStack consumable = event.getConsumable();
        if (consumable == null || !isArrowMaterial(consumable.getType())) {
            return;
        }
        ItemStack refund = consumable.clone();
        refund.setAmount(1);
        Bukkit.getScheduler().runTask(plugin, () -> refundDeadeyeArrow(player, refund));
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
    public void onFrostbornSnowballHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)
            || !(snowball.getShooter() instanceof Player player)
            || !hasPower(player, SuperpowerType.FROSTBORN)
            || !(event.getHitEntity() instanceof LivingEntity target)) {
            return;
        }
        if (!isCustomBoss(target)) {
            applyPotion(target, PotionEffectType.WEAKNESS, FROSTBORN_CHILL_SECONDS * 20, 0);
        }
        target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().clone().add(0.0, 1.0, 0.0), 16, 0.35, 0.35, 0.35, 0.03);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_SNOW_BREAK, 0.75f, 1.15f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFrozenPlayerInteract(PlayerInteractEvent event) {
        if (!isPowerFrozenPlayer(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFrozenPlayerDrop(PlayerDropItemEvent event) {
        if (isPowerFrozenPlayer(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDropMotherNatureStick(PlayerDropItemEvent event) {
        if (!isMotherNatureStick(event.getItemDrop().getItemStack())) {
            return;
        }
        if (!hasPower(event.getPlayer(), SuperpowerType.VERDANT)) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(MessageUtil.warn("The Wand of Mother Nature refuses to leave you."));
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
        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
            && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            item = itemInHand(player, hand);
        }
        if (isAncientScroll(item) && isPowerItemActivationClick(player, action)) {
            if (!markPowerItemActivation(player)) {
                return;
            }
            event.setCancelled(true);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            useAncientScroll(player, hand);
            return;
        }
        if (isTheWorldClock(item) && isPowerItemActivationClick(player, action)) {
            if (!markPowerItemActivation(player)) {
                return;
            }
            event.setCancelled(true);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            useTheWorldClock(player);
            return;
        }
        if (isDruidGrimoire(item) && isPowerItemActivationClick(player, action)) {
            if (!markPowerItemActivation(player)) {
                return;
            }
            event.setCancelled(true);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            openDruidGrimoire(player);
            return;
        }
        if (hand != EquipmentSlot.HAND) {
            return;
        }
        if (!isMotherNatureStick(item) || !hasPower(player, SuperpowerType.VERDANT)) {
            return;
        }

        event.setCancelled(true);
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            useMotherNatureStickAttack(player);
            return;
        }
        useMotherNatureStickHeal(player);
    }

    public boolean activateHeldCrossplayAbility(Player player, boolean alternate) {
        if (player == null) {
            return false;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (isAncientScroll(item)) {
            if (markPowerItemActivation(player)) {
                useAncientScroll(player, EquipmentSlot.HAND);
            }
            return true;
        }
        if (isTheWorldClock(item)) {
            if (markPowerItemActivation(player)) {
                useTheWorldClock(player);
            }
            return true;
        }
        if (isDruidGrimoire(item)) {
            if (markPowerItemActivation(player)) {
                openDruidGrimoire(player);
            }
            return true;
        }
        if (!isMotherNatureStick(item) || !hasPower(player, SuperpowerType.VERDANT)) {
            return false;
        }
        if (alternate) {
            useMotherNatureStickAttack(player);
        } else {
            useMotherNatureStickHeal(player);
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBedrockPowerItemSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        if (!BedrockCompat.isBedrockPlayer(player)) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (isPowerFrozenPlayer(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (isAncientScroll(item)) {
            if (markPowerItemActivation(player)) {
                useAncientScroll(player, EquipmentSlot.HAND);
            }
            return;
        }
        if (isTheWorldClock(item)) {
            if (markPowerItemActivation(player)) {
                useTheWorldClock(player);
            }
            return;
        }
        if (isDruidGrimoire(item) && markPowerItemActivation(player)) {
            openDruidGrimoire(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFloristStickSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        if (isPowerFrozenPlayer(player.getUniqueId())) {
            return;
        }
        if (!hasPower(player, SuperpowerType.VERDANT)) {
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
        if (!isCraftResultSlot(event)) {
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
        event.setCurrentItem(null);
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
        Inventory top = event.getView().getTopInventory();
        InventoryHolder rawHolder = top.getHolder(false);
        if ((rawHolder instanceof PowerChoiceHolder
            || rawHolder instanceof PowerInfoHolder
            || rawHolder instanceof DruidGrimoireHolder)
            && !isIntentionalMenuAction(event)) {
            event.setCancelled(true);
            return;
        }
        if (rawHolder instanceof PowerChoiceHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (!player.getUniqueId().equals(holder.ownerId())) {
                return;
            }
            int rawSlot = event.getRawSlot();
            if (rawSlot < 0 || rawSlot >= top.getSize()) {
                return;
            }
            if (!MenuItemUtil.isVisibleItem(event.getCurrentItem())) {
                return;
            }
            if (rawSlot == POWER_CHOICE_CANCEL_SLOT) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.closeInventory();
                    }
                });
                return;
            }
            if (rawSlot == POWER_CHOICE_PREVIOUS_SLOT && holder.page() > 0) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        openAncientScrollPowerChoice(player, holder.hand(), holder.page() - 1);
                    }
                });
                return;
            }
            if (rawSlot == POWER_CHOICE_NEXT_SLOT && holder.page() < maxPowerChoicePage(selectablePowerChoices().size())) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        openAncientScrollPowerChoice(player, holder.hand(), holder.page() + 1);
                    }
                });
                return;
            }
            SuperpowerType choice = powerChoiceBySlot(rawSlot, holder.page());
            if (choice != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        chooseAncientScrollPower(player, holder, choice);
                    }
                });
            }
            return;
        }
        if (rawHolder instanceof PowerInfoHolder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                int rawSlot = event.getRawSlot();
                if (rawSlot >= 0 && rawSlot < top.getSize() && rawSlot == 49
                    && MenuItemUtil.isVisibleItem(event.getCurrentItem())) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        player.closeInventory();
                        player.performCommand("menu");
                    });
                }
            }
            return;
        }
        if (rawHolder instanceof DruidGrimoireHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (!player.getUniqueId().equals(holder.ownerId())) {
                return;
            }
            int rawSlot = event.getRawSlot();
            if (rawSlot < 0 || rawSlot >= top.getSize()) {
                return;
            }
            if (!MenuItemUtil.isVisibleItem(event.getCurrentItem())) {
                return;
            }
            DruidBlessing blessing = DruidBlessing.fromSlot(rawSlot);
            if (blessing == null) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    useDruidBlessing(player, blessing);
                }
            });
        }
    }

    private boolean isIntentionalMenuAction(InventoryClickEvent event) {
        return event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPowerMenuDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof PowerInfoHolder
            || holder instanceof PowerChoiceHolder
            || holder instanceof DruidGrimoireHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPowerMenuClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof PowerInfoHolder
            || holder instanceof PowerChoiceHolder
            || holder instanceof DruidGrimoireHolder) {
            event.getView().getTopInventory().clear();
        }
    }

    private void initializePlayerState(Player player) {
        markVisitedDimension(player, player.getWorld());
        SuperpowerType power = ensurePowerAssigned(player);
        if (power == SuperpowerType.VERDANT) {
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
        if (power == SuperpowerType.NIGHTSHADE && isShadowActive(player)) {
            applyShadowEffects(player);
        }
        syncTankImmovableState(player, power == SuperpowerType.JUGGERNAUT && player.isSneaking());
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
            if (juggernautUnstoppableActiveUntil(player) <= now) {
                setJuggernautUnstoppableActiveUntil(player, 0L);
            }
            if (supermanFlightActiveUntil(player) > 0L && supermanFlightActiveUntil(player) <= now) {
                stopSupermanFlight(player, true);
            }
            applyPassiveEffects(player);
            applyBypassedCommandEffects(player, now);
        }
        cleanupRecentPortalTravel(now);
        tickMonarchSummons();
    }

    private void applyBypassedCommandEffects(Player player, long now) {
        if (!player.hasPermission(POWER_COMMAND_BYPASS_PERMISSION)
            || player.isDead()
            || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        SuperpowerType power = powerOf(player);
        if (power != SuperpowerType.NIGHTSHADE && isShadowActive(player)) {
            applyShadowEffects(player);
        }
        if (power != SuperpowerType.ORACLE_EYE && xrayActiveUntil(player) > now) {
            renderXrayHighlights(player);
        }
        if (power != SuperpowerType.VEIL_ASSASSIN && hasActiveSmokeInvisibility(player)) {
            tickVeilAssassinSmokeInvisibility(player);
        }
        if ((power != SuperpowerType.NIGHTSHADE && isNightshadeNightVisionEnabled(player))
            || (power != SuperpowerType.VOIDWALKER && isVoidwalkerNightVisionEnabled(player))) {
            applyPassiveNightVision(player);
        }
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
            case VEIL_ASSASSIN -> applyVeilAssassinPassives(player);
            case ARCANIST -> {
                if (player.getOpenInventory().getTopInventory() instanceof EnchantingInventory enchanting) {
                    refreshEnchanterLapis(player, enchanting);
                }
                syncEnchanterAttunement(player);
                syncPlayerAttributeModifier(player, Attribute.LUCK, keyEnchanterLuckModifier, ENCHANTER_LUCK_BONUS, AttributeModifier.Operation.ADD_NUMBER, true);
            }
            case BERSERKER -> {
                applyPotion(player, PotionEffectType.STRENGTH, 80, 1);
                if (player.getHealth() <= BERSERK_LOW_HEALTH_THRESHOLD) {
                    applyPotion(player, PotionEffectType.SPEED, 60, 1);
                    applyPotion(player, PotionEffectType.REGENERATION, 60, 0);
                }
            }
            case JUGGERNAUT -> {
                applyPotion(player, PotionEffectType.HEALTH_BOOST, 80, 4);
                syncTankImmovableState(player, player.isSneaking());
                var maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (maxHealth != null && player.getHealth() > maxHealth.getValue()) {
                    player.setHealth(maxHealth.getValue());
                }
            }
            case WAYFARER -> applyPotion(player, PotionEffectType.SPEED, 80, 1);
            case VERDANT -> {
                if (player.getLocation().getBlock().getLightFromSky() > 0) {
                    applyPotion(player, PotionEffectType.REGENERATION, 60, 0);
                }
                trimExtraMotherNatureSticks(player);
                if (!hasMotherNatureStick(player)) {
                    giveMotherNatureStick(player, false);
                }
            }
            case MONARCH -> pacifyNearbyUndead(player);
            case NIGHTSHADE -> {
                if (isNightshadeNightVisionEnabled(player)) {
                    applyPassiveNightVision(player);
                } else {
                    removePassiveNightVision(player);
                }
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
            case ORACLE_EYE -> {
                if (xrayActiveUntil(player) > System.currentTimeMillis()) {
                    renderXrayHighlights(player);
                }
            }
            case PROSPECTOR -> {
                applyPotion(player, PotionEffectType.HASTE, 80, 2);
                if (player.getLocation().getBlockY() <= MINER_NIGHT_VISION_Y_LEVEL) {
                    applyPassiveNightVision(player);
                } else {
                    removePassiveNightVision(player);
                }
                syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keyMinerHealthModifier, MINER_HEALTH_BONUS, AttributeModifier.Operation.ADD_NUMBER, true);
            }
            case TITAN -> {
                syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keyGiantHealthModifier, GIANT_HEALTH_BONUS, AttributeModifier.Operation.ADD_NUMBER, true);
                syncPlayerAttributeModifier(player, Attribute.SCALE, keyGiantScaleModifier, GIANT_SCALE_MULTIPLIER - 1.0, AttributeModifier.Operation.ADD_SCALAR, true);
                syncPlayerAttributeModifier(player, Attribute.KNOCKBACK_RESISTANCE, keyGiantKnockbackModifier, GIANT_KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_NUMBER, true);
                syncPlayerAttributeModifier(player, Attribute.ATTACK_DAMAGE, keyGiantAttackDamageModifier, GIANT_ATTACK_DAMAGE_BONUS, AttributeModifier.Operation.ADD_NUMBER, true);
            }
            case SKYBOUND -> {
                applyPotion(player, PotionEffectType.STRENGTH, 80, 1);
                applyPotion(player, PotionEffectType.SPEED, 80, 0);
                syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keySupermanHealthModifier, SUPERMAN_HEALTH_BONUS, AttributeModifier.Operation.ADD_NUMBER, true);
                if (isActiveBossFight(player) && supermanFlightActiveUntil(player) > 0L) {
                    stopSupermanFlight(player, false);
                    player.sendActionBar(MM.deserialize("<red>Skybound flight grounded for this boss fight.</red>"));
                } else {
                    syncSupermanFlightState(player, true);
                }
            }
            case TIDEBORN -> {
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
            case ASHEN_SOUL -> {
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
                if (isStandingOnFrostBlock(player) || isColdBiome(player)) {
                    applyPotion(player, PotionEffectType.SPEED, 60, isColdBiome(player) ? 1 : 0);
                }
                if (isColdBiome(player)) {
                    applyPotion(player, PotionEffectType.STRENGTH, 60, 0);
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
            case RUNESMITH -> applyPotion(player, PotionEffectType.HASTE, 80, 1);
            case GRAVEBORN -> {
                pacifyNearbyUndead(player);
                player.removePotionEffect(PotionEffectType.WITHER);
                player.removePotionEffect(PotionEffectType.POISON);
                applyPassiveNightVision(player);
            }
            case STORMCALLER -> {
                applyPotion(player, PotionEffectType.SPEED, 80, 1);
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

    private boolean denyUnsafeSpawnAbility(Player player, Location location) {
        SpawnProtectionListener spawnProtection = plugin.getSpawnProtectionListener();
        if (spawnProtection == null || !spawnProtection.blocksUnsafeAbility(player, location)) {
            return false;
        }
        spawnProtection.sendUnsafeAbilityDeny(player);
        return true;
    }

    private boolean denyUnsafeSpawnAbilityArea(Player player, Location center, double radius) {
        return denyUnsafeSpawnAbilityArea(player, center, radius, radius, radius);
    }

    private boolean denyUnsafeSpawnAbilityArea(Player player, Location center, double radiusX, double radiusY, double radiusZ) {
        SpawnProtectionListener spawnProtection = plugin.getSpawnProtectionListener();
        if (spawnProtection == null || !spawnProtection.blocksUnsafeAbilityArea(player, center, radiusX, radiusY, radiusZ)) {
            return false;
        }
        spawnProtection.sendUnsafeAbilityDeny(player);
        return true;
    }

    private boolean blocksUnsafeSpawnAbilityArea(Player player, Location center, double radiusX, double radiusY, double radiusZ) {
        SpawnProtectionListener spawnProtection = plugin.getSpawnProtectionListener();
        return spawnProtection != null && spawnProtection.blocksUnsafeAbilityArea(player, center, radiusX, radiusY, radiusZ);
    }

    private boolean blocksUnsafeSpawnCombat(Player attacker, LivingEntity victim) {
        if (attacker == null || victim == null) {
            return false;
        }
        SpawnProtectionListener spawnProtection = plugin.getSpawnProtectionListener();
        if (spawnProtection == null || spawnProtection.canBypassSpawnSafety(attacker)) {
            return false;
        }
        return spawnProtection.blocksUnsafeAbility(attacker, attacker.getLocation())
            || spawnProtection.blocksUnsafeAbility(attacker, victim.getLocation());
    }

    private void applyVeilAssassinPassives(Player player) {
        syncVeilAssassinHealthCap(player);
        enforceVeilAssassinArmor(player);
        tickVeilAssassinSmokeInvisibility(player);
        tickVeilAssassinCrouch(player);
    }

    private void syncVeilAssassinHealthCap(Player player) {
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        AttributeModifier existing = maxHealth.getModifier(keyVeilAssassinHealthModifier);
        double currentWithoutCap = maxHealth.getValue();
        if (existing != null && existing.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
            currentWithoutCap -= existing.getAmount();
        }
        double amount = VEIL_ASSASSIN_MAX_HEALTH - currentWithoutCap;
        syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keyVeilAssassinHealthModifier, amount, AttributeModifier.Operation.ADD_NUMBER, true);
    }

    private void startVeilAssassinCrouch(Player player) {
        UUID playerId = player.getUniqueId();
        veilAssassinCrouchStartedAt.put(playerId, System.currentTimeMillis());
        veilAssassinsInVeil.remove(playerId);
    }

    private void tickVeilAssassinCrouch(Player player) {
        UUID playerId = player.getUniqueId();
        if (!player.isSneaking() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            deactivateVeilAssassin(player, true);
            return;
        }

        long now = System.currentTimeMillis();
        long startedAt = veilAssassinCrouchStartedAt.computeIfAbsent(playerId, id -> now);
        if (now - startedAt < VEIL_ASSASSIN_CROUCH_SECONDS * 1000L) {
            return;
        }

        boolean justEntered = veilAssassinsInVeil.add(playerId);
        applyPotion(player, PotionEffectType.INVISIBILITY, 60, 0);
        applyPotion(player, PotionEffectType.SPEED, 60, 3);
        syncPlayerAttributeModifier(
            player,
            Attribute.SNEAKING_SPEED,
            keyVeilAssassinSneakingSpeedModifier,
            VEIL_ASSASSIN_SNEAKING_SPEED_BONUS,
            AttributeModifier.Operation.ADD_NUMBER,
            true
        );
        hideShadowEquipment(player);
        if (justEntered) {
            player.sendMessage(MessageUtil.success("You enter the veil."));
            refreshVeilAssassinConcealment(player);
            player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.6f, 0.65f);
            player.spawnParticle(Particle.SMOKE, player.getLocation().clone().add(0.0, 1.0, 0.0), 24, 0.35, 0.55, 0.35, 0.03);
        }
    }

    private void deactivateVeilAssassin(Player player, boolean restoreAppearance) {
        UUID playerId = player.getUniqueId();
        veilAssassinCrouchStartedAt.remove(playerId);
        boolean wasInVeil = veilAssassinsInVeil.remove(playerId);
        syncPlayerAttributeModifier(
            player,
            Attribute.SNEAKING_SPEED,
            keyVeilAssassinSneakingSpeedModifier,
            0.0,
            AttributeModifier.Operation.ADD_NUMBER,
            false
        );
        removeLikelyPowerPotion(player, PotionEffectType.SPEED, 3);
        if (!hasActiveSmokeInvisibility(player)) {
            removeLikelyPowerPotion(player, PotionEffectType.INVISIBILITY, 0);
            if (wasInVeil) {
                refreshVeilAssassinConcealment(player);
            }
            if (restoreAppearance && wasInVeil) {
                restoreShadowAppearance(player);
            }
        }
    }

    private void clearVeilAssassinState(Player player, boolean restoreAppearance) {
        UUID playerId = player.getUniqueId();
        boolean hadCrouch = veilAssassinCrouchStartedAt.containsKey(playerId);
        boolean hadSmoke = veilAssassinSmokeInvisibilityUntil.remove(playerId) != null;
        boolean hadVeil = veilAssassinsInVeil.contains(playerId);
        veilAssassinArmorWarnCooldowns.remove(playerId);
        if (!hadCrouch && !hadSmoke && !hadVeil) {
            return;
        }
        deactivateVeilAssassin(player, restoreAppearance);
        removeLikelyPowerPotion(player, PotionEffectType.SPEED, 2, VEIL_ASSASSIN_SMOKE_BUFF_SECONDS * 20 + 40);
        removeLikelyPowerPotion(player, PotionEffectType.DOLPHINS_GRACE, 0, VEIL_ASSASSIN_SMOKE_BUFF_SECONDS * 20 + 40);
        removeLikelyPowerPotion(player, PotionEffectType.INVISIBILITY, 0, VEIL_ASSASSIN_SMOKE_BUFF_SECONDS * 20 + 40);
        if (hadSmoke || hadVeil) {
            refreshVeilAssassinConcealment(player);
        }
        if (restoreAppearance && (hadSmoke || hadVeil)) {
            restoreShadowAppearance(player);
        }
    }

    private void tickVeilAssassinSmokeInvisibility(Player player) {
        UUID playerId = player.getUniqueId();
        Long activeUntil = veilAssassinSmokeInvisibilityUntil.get(playerId);
        if (activeUntil == null) {
            return;
        }
        if (activeUntil > System.currentTimeMillis()) {
            hideShadowEquipment(player);
            return;
        }
        veilAssassinSmokeInvisibilityUntil.remove(playerId);
        removeLikelyPowerPotion(player, PotionEffectType.SPEED, 2, VEIL_ASSASSIN_SMOKE_BUFF_SECONDS * 20 + 40);
        removeLikelyPowerPotion(player, PotionEffectType.DOLPHINS_GRACE, 0, VEIL_ASSASSIN_SMOKE_BUFF_SECONDS * 20 + 40);
        if (!isInVeilAssassinVeil(player)) {
            removeLikelyPowerPotion(player, PotionEffectType.INVISIBILITY, 0, VEIL_ASSASSIN_SMOKE_BUFF_SECONDS * 20 + 40);
            refreshVeilAssassinConcealment(player);
            restoreShadowAppearance(player);
        }
    }

    private boolean hasActiveSmokeInvisibility(Player player) {
        return veilAssassinSmokeInvisibilityUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    public boolean isVeilAssassinFullyConcealed(Player player) {
        return player != null
            && player.isOnline()
            && !player.isDead()
            && player.getGameMode() != GameMode.SPECTATOR
            && hasPower(player, SuperpowerType.VEIL_ASSASSIN)
            && (veilAssassinsInVeil.contains(player.getUniqueId()) || hasActiveSmokeInvisibility(player));
    }

    private boolean isInVeilAssassinVeil(Player player) {
        return player != null
            && player.isSneaking()
            && hasPower(player, SuperpowerType.VEIL_ASSASSIN)
            && veilAssassinsInVeil.contains(player.getUniqueId());
    }

    private void refreshVeilAssassinConcealment(Player player) {
        if (player == null || plugin.getPlayerVisualListener() == null) {
            return;
        }
        plugin.getPlayerVisualListener().refreshPlayerConcealment(player);
    }

    private void enforceVeilAssassinArmor(Player player) {
        PlayerInventory inventory = player.getInventory();
        boolean removed = false;
        removed |= removeForbiddenVeilAssassinArmor(player, EquipmentSlot.HEAD, inventory.getHelmet());
        removed |= removeForbiddenVeilAssassinArmor(player, EquipmentSlot.CHEST, inventory.getChestplate());
        removed |= removeForbiddenVeilAssassinArmor(player, EquipmentSlot.LEGS, inventory.getLeggings());
        removed |= removeForbiddenVeilAssassinArmor(player, EquipmentSlot.FEET, inventory.getBoots());
        if (!removed) {
            return;
        }

        long now = System.currentTimeMillis();
        long nextWarnAt = veilAssassinArmorWarnCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (nextWarnAt <= now) {
            veilAssassinArmorWarnCooldowns.put(player.getUniqueId(), now + 3000L);
            player.sendMessage(MessageUtil.warn("Veil Assassins cannot wear netherite armor."));
        }
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.65f, 0.7f);
        player.updateInventory();
    }

    private boolean removeForbiddenVeilAssassinArmor(Player player, EquipmentSlot slot, ItemStack item) {
        if (!isNetheriteArmor(item)) {
            return false;
        }
        ItemStack removed = item.clone();
        switch (slot) {
            case HEAD -> player.getInventory().setHelmet(null);
            case CHEST -> player.getInventory().setChestplate(null);
            case LEGS -> player.getInventory().setLeggings(null);
            case FEET -> player.getInventory().setBoots(null);
            default -> {
                return false;
            }
        }
        giveOrDrop(player, removed);
        return true;
    }

    private boolean isNetheriteArmor(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return switch (item.getType()) {
            case NETHERITE_HELMET, NETHERITE_CHESTPLATE, NETHERITE_LEGGINGS, NETHERITE_BOOTS -> true;
            default -> false;
        };
    }

    private void giveOrDrop(Player player, ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
            return;
        }
        player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private void applyVeilAssassinBackstab(EntityDamageByEntityEvent event, Player attacker, LivingEntity victim) {
        if (isCustomBoss(victim)) {
            event.setDamage(event.getDamage() * VEIL_ASSASSIN_BOSS_BACKSTAB_MULTIPLIER);
        } else {
            double targetFinalDamage = Math.max(1.0, victim.getHealth() * VEIL_ASSASSIN_BACKSTAB_RATIO);
            setFinalDamageAtLeast(event, targetFinalDamage);
        }
        victim.getWorld().spawnParticle(Particle.SWEEP_ATTACK, victim.getLocation().clone().add(0.0, 1.0, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
        victim.getWorld().spawnParticle(Particle.SMOKE, victim.getLocation().clone().add(0.0, 1.0, 0.0), 18, 0.35, 0.45, 0.35, 0.04);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.9f, 0.75f);
        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.35f, 1.6f);
    }

    private void setFinalDamageAtLeast(EntityDamageByEntityEvent event, double targetFinalDamage) {
        if (event.getFinalDamage() >= targetFinalDamage) {
            return;
        }
        double rawDamage = Math.max(event.getDamage(), targetFinalDamage);
        for (int i = 0; i < 6; i++) {
            event.setDamage(rawDamage);
            double finalDamage = event.getFinalDamage();
            if (finalDamage >= targetFinalDamage || finalDamage <= 0.0) {
                return;
            }
            rawDamage *= targetFinalDamage / finalDamage;
        }
    }

    private boolean isBehindTarget(Player attacker, LivingEntity victim) {
        Vector targetFacing = victim.getLocation().getDirection();
        Vector targetToAttacker = attacker.getLocation().toVector().subtract(victim.getLocation().toVector());
        targetFacing.setY(0.0);
        targetToAttacker.setY(0.0);
        if (targetFacing.lengthSquared() < 0.001 || targetToAttacker.lengthSquared() < 0.001) {
            return false;
        }
        return targetFacing.normalize().dot(targetToAttacker.normalize()) < -0.45;
    }

    private void renderSmokeBomb(Location center) {
        World world = center.getWorld();
        world.playSound(center, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.55f);
        world.playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 0.85f, 0.7f);
        for (int i = 0; i < 8; i++) {
            int delay = i * 5;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                world.spawnParticle(Particle.SMOKE, center.clone().add(0.0, 1.0, 0.0), 95, 2.8, 1.2, 2.8, 0.08);
                world.spawnParticle(Particle.CLOUD, center.clone().add(0.0, 0.75, 0.0), 55, 2.4, 0.7, 2.4, 0.05);
                world.spawnParticle(Particle.ASH, center.clone().add(0.0, 1.2, 0.0), 60, 2.7, 1.0, 2.7, 0.03);
            }, delay);
        }
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
        if (power == SuperpowerType.VERDANT) {
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
        refreshPowerCommandVisibility();
        if (notifyScrollResult || power.hasCommandHint()) {
            sendPowerHint(player, power);
        }
    }

    private void refreshPowerCommandVisibility() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.updateCommands();
        }
    }

    private void prepareForPowerAssignment(Player player, SuperpowerType nextPower) {
        UUID playerId = player.getUniqueId();
        pendingFloristStickReturns.remove(playerId);
        closeTravelerPortal(playerId, false);
        despawnMonarchSummons(playerId);
        clearTimeStopForOwner(playerId);
        handleHonoredDomainPlayerExit(playerId, false);
        honoredDomainSwingCooldowns.remove(playerId);
        clearVeilAssassinState(player, true);
        stopSupermanFlight(player, false);

        if (isShadowActive(player)) {
            removeLikelyPowerPotion(player, PotionEffectType.INVISIBILITY, 0);
            removeLikelyPowerPotion(player, PotionEffectType.SPEED, 2);
            restoreShadowAppearance(player);
        }

        clearLikelyPassivePowerEffects(player);
        clearPowerCooldownState(player);
        removePassiveNightVision(player);

        if (nextPower != SuperpowerType.VERDANT) {
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
        pdc.remove(keyNightshadeNightVisionEnabled);
        pdc.remove(keyJuggernautUnstoppableActiveUntil);
        pdc.remove(keyJuggernautUnstoppableCooldownUntil);
        pdc.remove(keyArcanistBookUpgradeCooldownUntil);
        pdc.remove(keyDeadeyeArrowInfinityEnabled);
        pdc.remove(keyShadowCooldownUntil);
        pdc.remove(keyShadowActiveUntil);
        pdc.remove(keyDruidBuffCooldownUntil);
        pdc.remove(keyHonoredDomainCooldownUntil);
        pdc.remove(keyHonoredInfinityEnabled);
        pdc.remove(keyVeilAssassinSmokeBombCooldownUntil);
        pdc.remove(keySupermanFlightActiveUntil);
        pdc.remove(keySupermanFlightCooldownUntil);
        pdc.remove(keyStormcallerLightningEnabled);
    }

    private void clearLikelyPassivePowerEffects(Player player) {
        removeLikelyPowerPotion(player, PotionEffectType.SPEED, 0);
        removeLikelyPowerPotion(player, PotionEffectType.SPEED, 1);
        removeLikelyPowerPotion(player, PotionEffectType.SPEED, 2);
        removeLikelyPowerPotion(player, PotionEffectType.SPEED, 3);
        removeLikelyPowerPotion(player, PotionEffectType.HASTE, 2);
        removeLikelyPowerPotion(player, PotionEffectType.HASTE, 1);
        removeLikelyPowerPotion(player, PotionEffectType.HASTE, 0);
        removeLikelyPowerPotion(player, PotionEffectType.STRENGTH, 1);
        removeLikelyPowerPotion(player, PotionEffectType.STRENGTH, 0);
        removeLikelyPowerPotion(player, PotionEffectType.HEALTH_BOOST, 4);
        removeLikelyPowerPotion(player, PotionEffectType.HEALTH_BOOST, 1);
        removeLikelyPowerPotion(player, PotionEffectType.HEALTH_BOOST, 0);
        removeLikelyPowerPotion(player, PotionEffectType.REGENERATION, 0);
        removeLikelyPowerPotion(player, PotionEffectType.REGENERATION, 1);
        removeLikelyPowerPotion(player, PotionEffectType.FIRE_RESISTANCE, 0);
        removeLikelyPowerPotion(player, PotionEffectType.JUMP_BOOST, 0);
        removeLikelyPowerPotion(player, PotionEffectType.RESISTANCE, 0);
        removeLikelyPowerPotion(player, PotionEffectType.RESISTANCE, 1);
        removeLikelyPowerPotion(player, PotionEffectType.SLOWNESS, 0);
        removeLikelyPowerPotion(player, PotionEffectType.WATER_BREATHING, 0);
        removeLikelyPowerPotion(player, PotionEffectType.CONDUIT_POWER, 0);
        removeLikelyPowerPotion(player, PotionEffectType.DOLPHINS_GRACE, 0);
        removeLikelyPowerPotion(player, PotionEffectType.NIGHT_VISION, 0);
        removeLikelyPowerPotion(player, PotionEffectType.SLOW_FALLING, 0);
        removeLikelyPowerPotion(player, PotionEffectType.INVISIBILITY, 0);
        removeLikelyPowerPotion(player, PotionEffectType.ABSORPTION, 0);
        removeLikelyPowerPotion(player, PotionEffectType.ABSORPTION, 1);
        removeLikelyPowerPotion(player, PotionEffectType.LUCK, 0);
        removeLikelyPowerPotion(player, PotionEffectType.SATURATION, 0);
        removeLikelyPowerPotion(player, PotionEffectType.WEAKNESS, 10);
        removeLikelyPowerPotion(player, PotionEffectType.WEAKNESS, 1, BLOODMENDER_SACRIFICE_WEAKNESS_SECONDS * 20 + 40);
        removeLikelyPowerPotion(player, PotionEffectType.MINING_FATIGUE, 4);
        removeLikelyPowerPotion(player, PotionEffectType.BLINDNESS, 0);
        removeLikelyPowerPotion(player, PotionEffectType.NAUSEA, HONORED_DOMAIN_NAUSEA_AMPLIFIER, HONORED_DOMAIN_NAUSEA_DURATION_TICKS + 40);
        removeLikelyPowerPotion(player, PotionEffectType.DARKNESS, 0);

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
            if (excludeHuman && type == SuperpowerType.MORTAL) {
                continue;
            }
            total += type.chance();
        }

        double roll = ThreadLocalRandom.current().nextDouble() * total;
        double cursor = 0.0;
        for (SuperpowerType type : SuperpowerType.values()) {
            if (excludeHuman && type == SuperpowerType.MORTAL) {
                continue;
            }
            cursor += type.chance();
            if (roll <= cursor) {
                return type;
            }
        }
        return excludeHuman ? SuperpowerType.VEIL_ASSASSIN : SuperpowerType.MORTAL;
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

    private World wayfarerWorld(World.Environment environment) {
        if (environment == null) {
            return null;
        }
        World world = environment == World.Environment.NORMAL
            ? Bukkit.getWorld(plugin.getConfigManager().smpStartWorld)
            : primaryWorld(environment);
        return world != null && world.getEnvironment() == environment ? world : null;
    }

    private boolean isWayfarerWorld(World world) {
        return world != null && world.equals(wayfarerWorld(world.getEnvironment()));
    }

    private boolean canEnterWayfarerDimension(Player player, World.Environment environment) {
        if (environment != World.Environment.NETHER && environment != World.Environment.THE_END) {
            return true;
        }
        if (player.hasPermission("smpcore.startsmp.bypass-dimension-lock")) {
            return true;
        }
        return plugin.getSmpStartManager() == null || plugin.getSmpStartManager().isDimensionUnlocked(environment);
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
            AbilityDamageContext.damage(player, living, PHOENIX_BURST_DAMAGE);
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

    private long honoredDomainCooldownUntil(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        long cooldownUntil = pdc.getOrDefault(keyHonoredDomainCooldownUntil, PersistentDataType.LONG, 0L);
        if (cooldownUntil <= 0L) {
            return 0L;
        }
        long now = System.currentTimeMillis();
        if (cooldownUntil <= now) {
            pdc.remove(keyHonoredDomainCooldownUntil);
            return 0L;
        }

        long currentCooldownMs = HONORED_DOMAIN_COOLDOWN_SECONDS * 1000L;
        long remainingMs = cooldownUntil - now;
        if (remainingMs <= currentCooldownMs) {
            return cooldownUntil;
        }

        // Older builds stored Domain Expansion cooldowns as 2-hour absolute expiry times.
        long migratedUntil = cooldownUntil - ((HONORED_DOMAIN_LEGACY_COOLDOWN_SECONDS - HONORED_DOMAIN_COOLDOWN_SECONDS) * 1000L);
        if (migratedUntil <= now) {
            pdc.remove(keyHonoredDomainCooldownUntil);
            return 0L;
        }
        pdc.set(keyHonoredDomainCooldownUntil, PersistentDataType.LONG, migratedUntil);
        return migratedUntil;
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
        return raw == null ? hasPower(player, SuperpowerType.VOIDWALKER) : raw != 0;
    }

    private boolean isNightshadeNightVisionEnabled(Player player) {
        Byte raw = player.getPersistentDataContainer().get(keyNightshadeNightVisionEnabled, PersistentDataType.BYTE);
        return raw == null ? hasPower(player, SuperpowerType.NIGHTSHADE) : raw != 0;
    }

    private boolean isStormcallerLightningEnabled(Player player) {
        Byte raw = player.getPersistentDataContainer().get(keyStormcallerLightningEnabled, PersistentDataType.BYTE);
        return raw == null ? hasPower(player, SuperpowerType.STORMCALLER) : raw != 0;
    }

    private boolean isDeadeyeArrowInfinityEnabled(Player player) {
        Byte raw = player.getPersistentDataContainer().get(keyDeadeyeArrowInfinityEnabled, PersistentDataType.BYTE);
        return raw == null ? hasPower(player, SuperpowerType.DEADEYE) : raw != 0;
    }

    private boolean isHonoredInfinityEnabled(Player player) {
        Byte raw = player.getPersistentDataContainer().get(keyHonoredInfinityEnabled, PersistentDataType.BYTE);
        return raw == null ? hasPower(player, SuperpowerType.HONORED_ONE) : raw != 0;
    }

    private long juggernautUnstoppableActiveUntil(Player player) {
        return player.getPersistentDataContainer().getOrDefault(keyJuggernautUnstoppableActiveUntil, PersistentDataType.LONG, 0L);
    }

    private long juggernautUnstoppableCooldownUntil(Player player) {
        return player.getPersistentDataContainer().getOrDefault(keyJuggernautUnstoppableCooldownUntil, PersistentDataType.LONG, 0L);
    }

    private long arcanistBookUpgradeCooldownUntil(Player player) {
        return player.getPersistentDataContainer().getOrDefault(keyArcanistBookUpgradeCooldownUntil, PersistentDataType.LONG, 0L);
    }

    private long veilAssassinSmokeBombCooldownUntil(Player player) {
        return player.getPersistentDataContainer().getOrDefault(keyVeilAssassinSmokeBombCooldownUntil, PersistentDataType.LONG, 0L);
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

    private void setNightshadeNightVisionEnabled(Player player, boolean enabled) {
        player.getPersistentDataContainer().set(keyNightshadeNightVisionEnabled, PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
    }

    private void setStormcallerLightningEnabled(Player player, boolean enabled) {
        player.getPersistentDataContainer().set(keyStormcallerLightningEnabled, PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
    }

    private void setDeadeyeArrowInfinityEnabled(Player player, boolean enabled) {
        player.getPersistentDataContainer().set(keyDeadeyeArrowInfinityEnabled, PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
    }

    private void setHonoredInfinityEnabled(Player player, boolean enabled) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (enabled) {
            pdc.remove(keyHonoredInfinityEnabled);
            return;
        }
        pdc.set(keyHonoredInfinityEnabled, PersistentDataType.BYTE, (byte) 0);
    }

    private void setJuggernautUnstoppableActiveUntil(Player player, long value) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (value <= 0L) {
            pdc.remove(keyJuggernautUnstoppableActiveUntil);
            return;
        }
        pdc.set(keyJuggernautUnstoppableActiveUntil, PersistentDataType.LONG, value);
    }

    private void setJuggernautUnstoppableCooldownUntil(Player player, long value) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (value <= 0L) {
            pdc.remove(keyJuggernautUnstoppableCooldownUntil);
            return;
        }
        pdc.set(keyJuggernautUnstoppableCooldownUntil, PersistentDataType.LONG, value);
    }

    private void setArcanistBookUpgradeCooldownUntil(Player player, long value) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (value <= 0L) {
            pdc.remove(keyArcanistBookUpgradeCooldownUntil);
            return;
        }
        pdc.set(keyArcanistBookUpgradeCooldownUntil, PersistentDataType.LONG, value);
    }

    private void setVeilAssassinSmokeBombCooldownUntil(Player player, long value) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (value <= 0L) {
            pdc.remove(keyVeilAssassinSmokeBombCooldownUntil);
            return;
        }
        pdc.set(keyVeilAssassinSmokeBombCooldownUntil, PersistentDataType.LONG, value);
    }

    private void setDruidBuffCooldownUntil(Player player, long value) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (value <= 0L) {
            pdc.remove(keyDruidBuffCooldownUntil);
            return;
        }
        pdc.set(keyDruidBuffCooldownUntil, PersistentDataType.LONG, value);
    }

    private void setHonoredDomainCooldownUntil(Player player, long value) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (value <= 0L) {
            pdc.remove(keyHonoredDomainCooldownUntil);
            return;
        }
        pdc.set(keyHonoredDomainCooldownUntil, PersistentDataType.LONG, value);
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
            if (player.equals(viewer)) {
                continue;
            }
            if (isVeilAssassinFullyConcealed(player)) {
                viewer.hideEntity(plugin, player);
                if (!viewer.isListed(player)) {
                    try {
                        viewer.listPlayer(player);
                    } catch (IllegalStateException ignored) {
                        // PlayerVisualListener retries once the join visibility state settles.
                    }
                }
                continue;
            }
            if (!isShadowActive(player)) {
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

    private String formatShortDuration(long millis) {
        long seconds = Math.max(1L, (long) Math.ceil(millis / 1000.0));
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainder = seconds % 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + remainder + "s";
        }
        return remainder + "s";
    }

    private boolean isPowerItemActivationClick(Player player, Action action) {
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            return true;
        }
        return BedrockCompat.isBedrockPlayer(player)
            && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK);
    }

    private boolean markPowerItemActivation(Player player) {
        if (!BedrockCompat.isBedrockPlayer(player)) {
            return true;
        }
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        long blockedUntil = bedrockPowerItemActivationDebounces.getOrDefault(playerId, 0L);
        if (blockedUntil > now) {
            return false;
        }
        bedrockPowerItemActivationDebounces.put(playerId, now + BEDROCK_POWER_ITEM_ACTIVATION_DEBOUNCE_MS);
        return true;
    }

    private void useAncientScroll(Player player, EquipmentSlot hand) {
        ItemStack held = itemInHand(player, hand);
        if (isAwakenedAncientScroll(held)) {
            openAncientScrollPowerChoice(player, hand);
            return;
        }

        SuperpowerType currentPower = powerOf(player);
        SuperpowerType rerolled = currentPower == null
            ? randomPower(false)
            : randomDifferentPower(currentPower, false);

        if (currentPower != null && rerolled == currentPower) {
            player.sendMessage(MessageUtil.error("The Ancient Scroll resisted the reroll. Try again."));
            return;
        }

        if (!consumeHeldItem(player, hand, this::isAncientScroll)) {
            player.sendMessage(MessageUtil.error("Hold the Ancient Scroll you want to use."));
            return;
        }
        assignPower(player, rerolled, true, true);
        player.sendMessage(MessageUtil.success("The Ancient Scroll rewrites your fate."));
    }

    private void openAncientScrollPowerChoice(Player player, EquipmentSlot hand) {
        openAncientScrollPowerChoice(player, hand, 0);
    }

    private void openAncientScrollPowerChoice(Player player, EquipmentSlot hand, int requestedPage) {
        if (!isAwakenedAncientScroll(itemInHand(player, hand))) {
            player.sendMessage(MessageUtil.error("Hold the awakened Ancient Scroll you want to use."));
            return;
        }

        int page = clampPowerChoicePage(requestedPage);
        Inventory inventory = Bukkit.createInventory(
            new PowerChoiceHolder(player.getUniqueId(), hand, page),
            MENU_SIZE,
            BedrockCompat.menuTitle(player, POWER_CHOICE_MENU_TITLE, "Choose Your Fate")
        );
        fillPowerChoiceMenu(player, inventory, page);
        player.openInventory(inventory);
    }

    private void chooseAncientScrollPower(Player player, PowerChoiceHolder holder, SuperpowerType choice) {
        if (choice == null || choice == SuperpowerType.MORTAL) {
            return;
        }
        if (choice == powerOf(player)) {
            player.sendMessage(MessageUtil.warn("You already have <white>" + choice.displayName() + "</white>. Pick a different fate."));
            return;
        }
        if (choice == SuperpowerType.HONORED_ONE
            && plugin.getSpawnProtectionListener() != null
            && plugin.getSpawnProtectionListener().blocksProtectedSpawnDeath(player)) {
            player.sendMessage(MessageUtil.warn("Choose that fate outside protected spawn."));
            return;
        }
        if (!isAwakenedAncientScroll(itemInHand(player, holder.hand()))) {
            player.sendMessage(MessageUtil.error("Hold the awakened Ancient Scroll you want to use."));
            player.closeInventory();
            return;
        }
        if (!consumeHeldItem(player, holder.hand(), item -> isAncientScroll(item) && isAwakenedAncientScroll(item))) {
            player.sendMessage(MessageUtil.error("The awakened Ancient Scroll slipped from your hand."));
            player.closeInventory();
            return;
        }

        assignPower(player, choice, true, true);
        player.closeInventory();
        player.sendMessage(MessageUtil.success("The awakened scroll binds you to <white>" + choice.displayName() + "</white>."));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.9f, 1.45f);
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0.0, 1.1, 0.0), 40, 0.45, 0.65, 0.45, 0.04);
        if (choice == SuperpowerType.HONORED_ONE) {
            player.sendMessage(MessageUtil.warn("The scroll takes your life as payment."));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 1.4f);
            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().clone().add(0.0, 1.0, 0.0), 70, 0.6, 0.8, 0.6, 0.12);
            player.setHealth(0.0);
        }
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
            if (isFrameSlot(slot, inventory.getSize())) {
                inventory.setItem(slot, fillerPane());
            }
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
        meta.lore(CustomLoreUtil.wrapLoreLines(List.of(
            MM.deserialize("<gray>Choose one positive blessing for yourself and nearby teammates.</gray>"),
            MM.deserialize("<gray>Only one blessing is cast per use.</gray>"),
            Component.empty(),
            MM.deserialize("<gray>Radius: <white>" + DRUID_BUFF_RADIUS + " blocks</white></gray>"),
            MM.deserialize("<gray>Duration: <white>" + DRUID_BUFF_DURATION_SECONDS + "s</white></gray>")
        )));
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
            applyPotion(player, PotionEffectType.SPEED, 60, 1);
            return;
        }
        for (Player ally : allies) {
            applyPotion(ally, PotionEffectType.SPEED, 60, 1);
            applyPotion(ally, PotionEffectType.RESISTANCE, 60, 0);
        }
        applyPotion(player, PotionEffectType.STRENGTH, 60, 0);
        applyPotion(player, PotionEffectType.ABSORPTION, 60, 1);
        player.getWorld().spawnParticle(Particle.WAX_ON, player.getLocation().clone().add(0.0, 1.0, 0.0), 10, 0.7, 0.4, 0.7, 0.01);
    }

    private void applyOathSummonBuffs(Player oathbound) {
        double radiusSquared = OATHBOUND_SUMMON_BUFF_RADIUS * OATHBOUND_SUMMON_BUFF_RADIUS;
        for (Player nearby : oathbound.getWorld().getPlayers()) {
            if (nearby.isDead() || nearby.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (nearby.getLocation().distanceSquared(oathbound.getLocation()) > radiusSquared) {
                continue;
            }
            if (!sameTeamOrSelf(oathbound.getUniqueId(), nearby.getUniqueId())) {
                continue;
            }
            applyPotion(nearby, PotionEffectType.STRENGTH, OATHBOUND_SUMMON_BUFF_SECONDS * 20, 1);
            applyPotion(nearby, PotionEffectType.ABSORPTION, OATHBOUND_SUMMON_BUFF_SECONDS * 20, 1);
            nearby.getWorld().spawnParticle(Particle.WAX_ON, nearby.getLocation().clone().add(0.0, 1.0, 0.0), 12, 0.55, 0.4, 0.55, 0.02);
        }
    }

    private boolean tryJuggernautWallImpact(Player player) {
        Vector direction = player.getLocation().getDirection();
        direction.setY(0.0);
        if (direction.lengthSquared() < 0.01) {
            direction = player.getVelocity().clone();
            direction.setY(0.0);
        }
        if (direction.lengthSquared() < 0.01) {
            return false;
        }
        direction.normalize();

        Location base = player.getLocation();
        if (blocksUnsafeSpawnAbilityArea(player, base, 3.0, 3.0, 3.0)) {
            return false;
        }
        BlockFace forward = dominantFace(direction);
        BlockFace side = perpendicularFace(forward);
        int broken = 0;
        boolean hitWall = false;
        for (int depth = 1; depth <= 2; depth++) {
            Block center = base.clone().add(direction.clone().multiply(depth)).getBlock();
            for (int y = 0; y <= 2; y++) {
                for (int offset = -1; offset <= 1; offset++) {
                    Block block = center.getRelative(BlockFace.UP, y).getRelative(side, offset);
                    if (!isUnstoppableBreakable(block)) {
                        if (block.getType().isSolid()) {
                            hitWall = true;
                        }
                        continue;
                    }
                    if (breakUnstoppableBlock(player, block)) {
                        broken++;
                    }
                }
            }
        }

        if (broken <= 0) {
            return hitWall;
        }
        Location impact = base.clone().add(direction.multiply(1.2)).add(0.0, 1.0, 0.0);
        World world = player.getWorld();
        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.75f, 0.75f);
        world.playSound(impact, Sound.BLOCK_STONE_BREAK, 1.0f, 0.55f);
        world.spawnParticle(Particle.CLOUD, impact, 36, 0.75, 0.55, 0.75, 0.12);
        world.spawnParticle(Particle.CRIT, impact, 22, 0.65, 0.45, 0.65, 0.05);
        damageJuggernautImpact(player, impact);
        return true;
    }

    private boolean breakUnstoppableBlock(Player player, Block block) {
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) {
            return false;
        }
        if (!breakEvent.isDropItems()) {
            block.setType(Material.AIR, true);
            return true;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        return block.breakNaturally(tool, true, true);
    }

    private boolean isUnstoppableBreakable(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        if (type.isAir() || !type.isBlock() || !type.isSolid()) {
            return false;
        }
        if (block.getState() instanceof Container) {
            return false;
        }
        float hardness = type.getHardness();
        if (hardness < 0.0f || hardness > JUGGERNAUT_UNSTOPPABLE_BREAK_HARDNESS_LIMIT) {
            return false;
        }
        return switch (type) {
            case BEDROCK, BARRIER, COMMAND_BLOCK, CHAIN_COMMAND_BLOCK, REPEATING_COMMAND_BLOCK,
                STRUCTURE_BLOCK, STRUCTURE_VOID, JIGSAW, END_PORTAL, END_PORTAL_FRAME,
                NETHER_PORTAL, TRIAL_SPAWNER, SPAWNER, VAULT, REINFORCED_DEEPSLATE,
                CHEST, TRAPPED_CHEST, BARREL, HOPPER, DISPENSER, DROPPER, FURNACE,
                BLAST_FURNACE, SMOKER, BREWING_STAND, CHISELED_BOOKSHELF, JUKEBOX,
                LECTERN, DECORATED_POT -> false;
            default -> true;
        };
    }

    private BlockFace dominantFace(Vector direction) {
        if (Math.abs(direction.getX()) > Math.abs(direction.getZ())) {
            return direction.getX() >= 0.0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return direction.getZ() >= 0.0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private BlockFace perpendicularFace(BlockFace face) {
        return switch (face) {
            case EAST, WEST -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }

    private void damageJuggernautImpact(Player player, Location impact) {
        for (Entity nearby : player.getWorld().getNearbyEntities(impact, 2.4, 1.8, 2.4)) {
            if (!(nearby instanceof LivingEntity living) || living.equals(player) || living.isDead() || isFriendlyTo(player, living)) {
                continue;
            }
            AbilityDamageContext.damage(player, living, JUGGERNAUT_UNSTOPPABLE_IMPACT_DAMAGE);
            Vector push = living.getLocation().toVector().subtract(player.getLocation().toVector());
            push.setY(0.0);
            if (!isCustomBoss(living) && push.lengthSquared() > 0.001) {
                living.setVelocity(living.getVelocity().add(push.normalize().multiply(0.35).setY(0.12)));
            }
        }
    }

    private void trackJuggernautFall(Player player) {
        UUID playerId = player.getUniqueId();
        if (!hasPower(player, SuperpowerType.JUGGERNAUT) || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            juggernautPeakFallDistances.remove(playerId);
            return;
        }
        if (player.isFlying() || player.isGliding() || player.isInsideVehicle()) {
            juggernautPeakFallDistances.remove(playerId);
            return;
        }

        float currentFall = player.getFallDistance();
        if (!((Entity) player).isOnGround()) {
            if (currentFall > 0.5f) {
                juggernautPeakFallDistances.merge(playerId, currentFall, (previous, current) -> Math.max(previous, current));
            }
            return;
        }

        Float peakFall = juggernautPeakFallDistances.remove(playerId);
        if (peakFall == null || peakFall < JUGGERNAUT_GROUND_SLAM_MIN_FALL_DISTANCE) {
            return;
        }
        tryTriggerJuggernautGroundSlam(player, peakFall, Math.max(0.0, peakFall - 3.0));
    }

    private boolean tryTriggerJuggernautGroundSlam(Player player, float fallDistance, double fallDamage) {
        if (blocksUnsafeSpawnAbilityArea(player, player.getLocation(), JUGGERNAUT_GROUND_SLAM_RADIUS, 3.0, JUGGERNAUT_GROUND_SLAM_RADIUS)) {
            return false;
        }
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        Long nextSlamAt = juggernautGroundSlamCooldowns.get(playerId);
        if (nextSlamAt != null && nextSlamAt > now) {
            return false;
        }
        boolean triggered = triggerJuggernautGroundSlam(player, fallDistance, fallDamage);
        if (triggered) {
            juggernautGroundSlamCooldowns.put(playerId, now + 700L);
        }
        return triggered;
    }

    private boolean triggerJuggernautGroundSlam(Player player, float fallDistance, double fallDamage) {
        double effectiveFall = Math.max(fallDistance, fallDamage + 3.0);
        if (effectiveFall < JUGGERNAUT_GROUND_SLAM_MIN_FALL_DISTANCE) {
            return false;
        }
        double radius = Math.min(JUGGERNAUT_GROUND_SLAM_RADIUS, 2.5 + (effectiveFall * 0.15));
        double playerDamage = Math.min(JUGGERNAUT_GROUND_SLAM_MAX_PLAYER_DAMAGE, Math.max(2.0, (effectiveFall - 3.0) * 0.75));
        Location center = player.getLocation();
        player.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.65f);
        player.getWorld().spawnParticle(Particle.CLOUD, center.clone().add(0.0, 0.15, 0.0), 54, radius * 0.35, 0.1, radius * 0.35, 0.1);
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(0.0, 0.55, 0.0), 18, radius * 0.28, 0.15, radius * 0.28, 0.0);
        for (Entity nearby : player.getWorld().getNearbyEntities(center, radius, 2.5, radius)) {
            if (!(nearby instanceof LivingEntity living) || living.equals(player) || living.isDead() || isFriendlyTo(player, living)) {
                continue;
            }
            double damage = living instanceof Player ? playerDamage : playerDamage * 2.0;
            AbilityDamageContext.damage(player, living, damage);
            Vector launch = living.getLocation().toVector().subtract(center.toVector());
            launch.setY(0.0);
            if (!isCustomBoss(living) && launch.lengthSquared() > 0.001) {
                living.setVelocity(living.getVelocity().add(launch.normalize().multiply(0.45).setY(0.28)));
            }
        }
        return true;
    }

    private boolean tryGravebornSecondChance(Player player) {
        Mob sacrifice = nearestReviveUndead(player);
        if (sacrifice == null) {
            return false;
        }
        sacrifice.getWorld().spawnParticle(Particle.SOUL, sacrifice.getLocation().clone().add(0.0, 1.0, 0.0), 30, 0.45, 0.5, 0.45, 0.04);
        sacrifice.remove();

        double healthCap = maxHealth(player);
        player.setHealth(Math.min(healthCap, 8.0));
        player.setFireTicks(0);
        player.removePotionEffect(PotionEffectType.POISON);
        player.removePotionEffect(PotionEffectType.WITHER);
        applyPotion(player, PotionEffectType.REGENERATION, 8 * 20, 1);
        applyPotion(player, PotionEffectType.RESISTANCE, 5 * 20, 1);
        applyPotion(player, PotionEffectType.ABSORPTION, 10 * 20, 1);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().clone().add(0.0, 1.0, 0.0), 42, 0.55, 0.65, 0.55, 0.03);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.95f, 0.8f);
        player.sendMessage(MessageUtil.success("An undead soul pulled you back."));
        return true;
    }

    private Mob nearestReviveUndead(Player player) {
        Mob best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity nearby : player.getWorld().getNearbyEntities(player.getLocation(), GRAVEBORN_REVIVE_RADIUS, GRAVEBORN_REVIVE_RADIUS, GRAVEBORN_REVIVE_RADIUS)) {
            if (!(nearby instanceof Mob mob) || mob.isDead() || !mob.isValid() || !isUndeadPassiveType(mob.getType())) {
                continue;
            }
            if (isTaggedMonarchSummon(mob) || isCustomBoss(mob)) {
                continue;
            }
            double distance = mob.getLocation().distanceSquared(player.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = mob;
            }
        }
        return best;
    }

    private void applyNearbyGravebornDeathBuff(Player deadPlayer) {
        for (Player nearby : deadPlayer.getWorld().getPlayers()) {
            if (nearby.equals(deadPlayer) || nearby.isDead() || !hasPower(nearby, SuperpowerType.GRAVEBORN)) {
                continue;
            }
            if (nearby.getLocation().distanceSquared(deadPlayer.getLocation()) > GRAVEBORN_PLAYER_DEATH_BUFF_RADIUS * GRAVEBORN_PLAYER_DEATH_BUFF_RADIUS) {
                continue;
            }
            applyPotion(nearby, PotionEffectType.STRENGTH, 30 * 20, 0);
            applyPotion(nearby, PotionEffectType.SPEED, 30 * 20, 0);
            applyPotion(nearby, PotionEffectType.ABSORPTION, 45 * 20, 2);
            applyPotion(nearby, PotionEffectType.REGENERATION, 8 * 20, 1);
            nearby.getWorld().spawnParticle(Particle.SOUL, nearby.getLocation().clone().add(0.0, 1.0, 0.0), 22, 0.55, 0.5, 0.55, 0.03);
        }
    }

    private List<Player> nearbyBloodmenderTeammates(Player player) {
        double radiusSquared = BLOODMENDER_SACRIFICE_RADIUS * BLOODMENDER_SACRIFICE_RADIUS;
        List<Player> targets = new ArrayList<>();
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
            if (nearby.getHealth() >= maxHealth(nearby) - 0.05 && nearby.getFireTicks() <= 0) {
                continue;
            }
            targets.add(nearby);
        }
        return targets;
    }

    private List<ArmorCurseTarget> nearbyCurseArmorTargets(Player player) {
        double radiusSquared = BLOODMENDER_CURSE_RADIUS * BLOODMENDER_CURSE_RADIUS;
        List<ArmorCurseTarget> targets = new ArrayList<>();
        for (Player enemy : player.getWorld().getPlayers()) {
            if (enemy.equals(player) || enemy.isDead() || enemy.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (enemy.getLocation().distanceSquared(player.getLocation()) > radiusSquared) {
                continue;
            }
            if (sameTeamOrSelf(player.getUniqueId(), enemy.getUniqueId())) {
                continue;
            }
            collectCurseTarget(targets, enemy, EquipmentSlot.HEAD, enemy.getInventory().getHelmet());
            collectCurseTarget(targets, enemy, EquipmentSlot.CHEST, enemy.getInventory().getChestplate());
            collectCurseTarget(targets, enemy, EquipmentSlot.LEGS, enemy.getInventory().getLeggings());
            collectCurseTarget(targets, enemy, EquipmentSlot.FEET, enemy.getInventory().getBoots());
        }
        return targets;
    }

    private void collectCurseTarget(List<ArmorCurseTarget> targets, Player target, EquipmentSlot slot, ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !isArmorMaterial(item.getType())) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.hasEnchant(Enchantment.VANISHING_CURSE)) {
            return;
        }
        targets.add(new ArmorCurseTarget(target, slot, item.clone()));
    }

    private boolean isArmorMaterial(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return name.endsWith("_HELMET")
            || name.endsWith("_CHESTPLATE")
            || name.endsWith("_LEGGINGS")
            || name.endsWith("_BOOTS")
            || name.equals("ELYTRA")
            || name.equals("TURTLE_HELMET");
    }

    private boolean isArrowMaterial(Material material) {
        return material == Material.ARROW || material == Material.TIPPED_ARROW || material == Material.SPECTRAL_ARROW;
    }

    private ItemStack likelyCrossbowConsumable(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (isCrossbowConsumable(offhand)) {
            return offhand;
        }
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (isCrossbowConsumable(mainHand)) {
            return mainHand;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (isCrossbowConsumable(item)) {
                return item;
            }
        }
        return null;
    }

    private boolean isCrossbowConsumable(ItemStack item) {
        return item != null
            && item.getAmount() > 0
            && (isArrowMaterial(item.getType()) || item.getType() == Material.FIREWORK_ROCKET);
    }

    private void refundDeadeyeArrow(Player player, ItemStack refund) {
        if (!player.isOnline() || refund == null || refund.getAmount() <= 0 || !isArrowMaterial(refund.getType())) {
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(refund);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        player.updateInventory();
    }

    private boolean isStormcallerHeavyWeapon(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        Material type = item.getType();
        return type == Material.MACE || type.name().endsWith("_AXE");
    }

    private boolean isColdBiome(Player player) {
        Block block = player.getLocation().getBlock();
        String biome = block.getBiome().getKey().getKey().toUpperCase(Locale.ROOT);
        return block.getTemperature() <= 0.20
            || biome.contains("SNOW")
            || biome.contains("FROZEN")
            || biome.contains("ICE")
            || biome.contains("COLD")
            || biome.contains("GROVE")
            || biome.contains("PEAK");
    }

    private double maxHealth(Player player) {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0 : Math.max(1.0, attribute.getValue());
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

    private boolean isBossEncounterEntity(Entity entity) {
        BossManager bossManager = plugin.getBossManager();
        return bossManager != null && bossManager.isBossEncounterEntity(entity);
    }

    private boolean isBossOwnedProjectile(Projectile projectile) {
        BossManager bossManager = plugin.getBossManager();
        return bossManager != null && bossManager.isBossOwnedProjectile(projectile);
    }

    private boolean isActiveBossFight(Player player) {
        BossManager bossManager = plugin.getBossManager();
        return bossManager != null && bossManager.isActiveBossFight(player);
    }

    private boolean isLethalBossMechanicDamage(Player player) {
        BossManager bossManager = plugin.getBossManager();
        return bossManager != null && bossManager.isLethalBossMechanicDamage(player);
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
        if (plugin.getBossManager() != null && plugin.getBossManager().blockHealingIfSuppressed(player, amount)) {
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
        if (blocksUnsafeSpawnAbilityArea(player, player.getLocation(), FLORIST_VINE_RANGE, 4.0, FLORIST_VINE_RANGE)) {
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
            AbilityDamageContext.damage(player, victim, FLORIST_VINE_DAMAGE);
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
            handlePortalTravel(pair, true);
            handlePortalTravel(pair, false);
        }
    }

    private void handlePortalTravel(PortalPair pair, boolean travelToTarget) {
        Location from = travelToTarget ? pair.source() : pair.target();
        Location to = travelToTarget ? pair.target() : pair.source();
        World world = from.getWorld();
        if (world == null || !isChunkLoaded(from)) {
            return;
        }

        long now = System.currentTimeMillis();
        List<Entity> travelers = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(from, 1.35, 2.25, 1.35)) {
            if (recentPortalTravel.getOrDefault(entity.getUniqueId(), 0L) > now) {
                continue;
            }
            if (entity instanceof Player player) {
                boolean activeBossFight = isActiveBossFight(player);
                if (!player.isDead() && player.getGameMode() != GameMode.SPECTATOR && !activeBossFight) {
                    travelers.add(player);
                } else if (activeBossFight) {
                    recentPortalTravel.put(player.getUniqueId(), now + 1_000L);
                    player.sendActionBar(MM.deserialize("<red>Boss fights seal Wayfarer portals.</red>"));
                }
            } else if (entity instanceof LivingEntity living
                && !living.isDead()
                && living.isValid()
                && !isBossEncounterEntity(living)) {
                travelers.add(living);
            }
        }
        if (travelers.isEmpty()) {
            return;
        }

        World targetWorld = to.getWorld();
        Location safe = targetWorld == null ? null : resolvePortalDestination(pair, travelToTarget);
        for (Entity entity : travelers) {

            if (entity instanceof Player player) {
                if (targetWorld == null
                    || !isWayfarerWorld(targetWorld)
                    || !canEnterWayfarerDimension(player, targetWorld.getEnvironment())
                    || !hasVisitedEnvironment(player, targetWorld.getEnvironment())) {
                    player.sendMessage(MessageUtil.warn("This portal rejects you."));
                    recentPortalTravel.put(player.getUniqueId(), now + 1000L);
                    continue;
                }
                if (safe == null) {
                    player.sendMessage(MessageUtil.error("The other side of the portal is unstable."));
                    recentPortalTravel.put(player.getUniqueId(), now + 1000L);
                    continue;
                }

                plugin.getPlayerManager().saveBackLocation(player);
                recentPortalTravel.put(player.getUniqueId(), now + PORTAL_RECENT_TRAVEL_MS);
                player.teleportAsync(safe.clone());
                player.setFallDistance(0.0f);
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.8f, 1.2f);
                continue;
            }

            LivingEntity living = (LivingEntity) entity;
            if (safe == null) {
                recentPortalTravel.put(living.getUniqueId(), now + 1000L);
                continue;
            }
            if (!isChunkLoaded(safe)) {
                continue;
            }
            recentPortalTravel.put(living.getUniqueId(), now + PORTAL_RECENT_TRAVEL_MS);
            living.setFallDistance(0.0f);
            if (living.teleport(safe.clone())) {
                living.setFallDistance(0.0f);
            }
        }
    }

    private void showPortal(Location center) {
        World world = center.getWorld();
        if (world == null || !isChunkLoaded(center)) {
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
        return new Location(
            location.getWorld(),
            location.getBlockX() + 0.5,
            location.getY(),
            location.getBlockZ() + 0.5,
            location.getYaw(),
            location.getPitch()
        );
    }

    private Location findSafeTravelLocation(Location target) {
        return findSafeTravelLocation(target, true);
    }

    private Location findSafeTravelLocation(Location target, boolean loadTargetChunk) {
        if (target == null || target.getWorld() == null) {
            return null;
        }
        World world = target.getWorld();
        int targetChunkX = target.getBlockX() >> 4;
        int targetChunkZ = target.getBlockZ() >> 4;
        if (!world.isChunkLoaded(targetChunkX, targetChunkZ)) {
            if (!loadTargetChunk) {
                return null;
            }
            world.getChunkAt(targetChunkX, targetChunkZ);
        }

        int originX = target.getBlockX();
        int originY = target.getBlockY();
        int originZ = target.getBlockZ();
        for (int radius = 0; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = -4; dy <= 4; dy++) {
                        Location candidate = new Location(world, originX + dx + 0.5, originY + dy, originZ + dz + 0.5, target.getYaw(), target.getPitch());
                        if (!loadTargetChunk && !isChunkLoaded(candidate)) {
                            continue;
                        }
                        if (isSafeStandingLocation(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }

        int highestY = world.getHighestBlockYAt(originX, originZ);
        Location fallback = new Location(world, originX + 0.5, highestY + 1.0, originZ + 0.5, target.getYaw(), target.getPitch());
        return (loadTargetChunk || isChunkLoaded(fallback)) && isSafeStandingLocation(fallback) ? fallback : null;
    }

    private Location resolvePortalDestination(PortalPair pair, boolean travelToTarget) {
        Location anchor = travelToTarget ? pair.target() : pair.source();
        Location cached = pair.cachedDestination(travelToTarget);
        if (cached != null && (!isChunkLoaded(cached) || isSafeStandingLocation(cached))) {
            return cached.clone();
        }
        if (!isChunkLoaded(anchor)) {
            return null;
        }
        Location resolved = findSafeTravelLocation(anchor, false);
        pair.cacheDestination(travelToTarget, resolved);
        return resolved == null ? null : resolved.clone();
    }

    private boolean isChunkLoaded(Location location) {
        World world = location == null ? null : location.getWorld();
        return world != null && world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
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
                    if (blocksUnsafeSpawnAbilityArea(player, block.getLocation().add(0.5, 0.5, 0.5), 0.5, 0.5, 0.5)) {
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
            if (!hasPower(player, SuperpowerType.VERDANT) || player.isDead()) {
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
        if (!isMonarchStorableType(type)) {
            return;
        }
        List<EntityType> stored = monarchStorage(player);
        if (stored.size() >= MONARCH_STORAGE_LIMIT) {
            return;
        }
        stored.add(type);
        saveMonarchStorage(player, stored);
        player.sendMessage(MessageUtil.info(
            "Shadow Monarch stored hostile <white>" + prettyEntityType(type) + "</white> (<white>" + stored.size() + "/" + MONARCH_STORAGE_LIMIT + "</white>)."
        ));
    }

    private List<EntityType> cleanMonarchStorage(Player player, List<EntityType> stored) {
        if (stored == null || stored.isEmpty()) {
            return new ArrayList<>();
        }
        List<EntityType> hostileOnly = new ArrayList<>();
        for (EntityType type : stored) {
            if (isMonarchStorableType(type)) {
                hostileOnly.add(type);
            }
        }
        if (hostileOnly.size() != stored.size()) {
            saveMonarchStorage(player, hostileOnly);
            int removed = stored.size() - hostileOnly.size();
            player.sendMessage(MessageUtil.info("Cleared <white>" + removed + "</white> old non-hostile Shadow Monarch stored mob(s)."));
        }
        return hostileOnly;
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
        if (isCustomBoss(entity)) {
            return false;
        }
        return isMonarchStorableType(type);
    }

    private boolean isMonarchStorableType(EntityType type) {
        if (type == null || MONARCH_BLOCKED_TYPES.contains(type) || !type.isSpawnable()) {
            return false;
        }
        Class<?> entityClass = type.getEntityClass();
        return entityClass != null && org.bukkit.entity.Monster.class.isAssignableFrom(entityClass);
    }

    private void markMonarchSummon(Mob mob, UUID ownerId) {
        PersistentDataContainer pdc = mob.getPersistentDataContainer();
        pdc.set(keyMonarchSummonTag, PersistentDataType.BYTE, (byte) 1);
        pdc.set(keyMonarchSummonOwner, PersistentDataType.STRING, ownerId.toString());
        pendingMonarchUnsummonOwners.remove(ownerId);
        hardenMonarchSummon(mob, true);
        monarchOwnerByMob.put(mob.getUniqueId(), ownerId);
        monarchSummonsByOwner.computeIfAbsent(ownerId, ignored -> ConcurrentHashMap.newKeySet()).add(mob.getUniqueId());
    }

    private void hardenMonarchSummon(Mob mob, boolean healToFull) {
        mob.setPersistent(true);
        mob.setRemoveWhenFarAway(false);
        mob.setCanPickupItems(false);
        mob.setSilent(false);
        double baseDamage = monarchBaseAttribute(mob, keyMonarchBaseDamage, Attribute.ATTACK_DAMAGE, 4.0);
        double baseSpeed = monarchBaseAttribute(mob, keyMonarchBaseSpeed, Attribute.MOVEMENT_SPEED, 0.23);
        setMobAttributeBase(
            mob,
            Attribute.MAX_HEALTH,
            MONARCH_SUMMON_HEALTH
        );
        setMobAttributeBase(
            mob,
            Attribute.ATTACK_DAMAGE,
            Math.max(MONARCH_MIN_DAMAGE, baseDamage * MONARCH_DAMAGE_MULTIPLIER)
        );
        setMobAttributeBase(
            mob,
            Attribute.MOVEMENT_SPEED,
            Math.max(MONARCH_MIN_SPEED, baseSpeed * 1.20)
        );
        setMobAttributeBase(mob, Attribute.FOLLOW_RANGE, Math.max(42.0, currentMobAttribute(mob, Attribute.FOLLOW_RANGE, 24.0)));
        setMobAttributeBase(mob, Attribute.ARMOR, Math.max(MONARCH_MIN_ARMOR, currentMobAttribute(mob, Attribute.ARMOR, 0.0)));
        setMobAttributeBase(mob, Attribute.ARMOR_TOUGHNESS, Math.max(MONARCH_MIN_ARMOR_TOUGHNESS, currentMobAttribute(mob, Attribute.ARMOR_TOUGHNESS, 0.0)));
        setMobAttributeBase(mob, Attribute.KNOCKBACK_RESISTANCE, Math.max(MONARCH_KNOCKBACK_RESISTANCE, currentMobAttribute(mob, Attribute.KNOCKBACK_RESISTANCE, 0.0)));
        double maxHealth = Math.max(1.0, currentMobAttribute(mob, Attribute.MAX_HEALTH, MONARCH_SUMMON_HEALTH));
        if (healToFull) {
            mob.setHealth(maxHealth);
        } else if (mob.getHealth() > maxHealth) {
            mob.setHealth(maxHealth);
        }
        mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false, false));
        mob.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, false, false));
        equipMonarchSummon(mob);
    }

    private double monarchBaseAttribute(Mob mob, NamespacedKey key, Attribute attribute, double fallback) {
        PersistentDataContainer pdc = mob.getPersistentDataContainer();
        Double stored = pdc.get(key, PersistentDataType.DOUBLE);
        if (stored != null && stored > 0.0 && Double.isFinite(stored)) {
            return stored;
        }
        double observed = currentMobAttribute(mob, attribute, fallback);
        pdc.set(key, PersistentDataType.DOUBLE, observed);
        return observed;
    }

    private void equipMonarchSummon(Mob mob) {
        var equipment = mob.getEquipment();
        if (equipment == null) {
            return;
        }
        if (equipment.getItemInMainHand().getType().isAir()) {
            Material weapon = mob.getType() == EntityType.SKELETON
                || mob.getType() == EntityType.STRAY
                || mob.getType() == EntityType.BOGGED
                ? Material.BOW
                : Material.IRON_AXE;
            equipment.setItemInMainHand(new ItemStack(weapon));
        }
        if (equipment.getHelmet() == null || equipment.getHelmet().getType().isAir()) {
            equipment.setHelmet(new ItemStack(Material.IRON_HELMET));
        }
        if (equipment.getChestplate() == null || equipment.getChestplate().getType().isAir()) {
            equipment.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        }
        if (equipment.getLeggings() == null || equipment.getLeggings().getType().isAir()) {
            equipment.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        }
        if (equipment.getBoots() == null || equipment.getBoots().getType().isAir()) {
            equipment.setBoots(new ItemStack(Material.IRON_BOOTS));
        }
        equipment.setItemInMainHandDropChance(0.0f);
        equipment.setHelmetDropChance(0.0f);
        equipment.setChestplateDropChance(0.0f);
        equipment.setLeggingsDropChance(0.0f);
        equipment.setBootsDropChance(0.0f);
    }

    private double currentMobAttribute(Mob mob, Attribute attribute, double fallback) {
        var instance = mob.getAttribute(attribute);
        return instance == null ? fallback : instance.getBaseValue();
    }

    private void setMobAttributeBase(Mob mob, Attribute attribute, double value) {
        var instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private Player monarchOwnerOf(Entity entity) {
        UUID ownerId = entity == null ? null : monarchOwnerByMob.get(entity.getUniqueId());
        return ownerId == null ? null : Bukkit.getPlayer(ownerId);
    }

    private void directMonarchSummons(Player owner, LivingEntity target) {
        if (owner == null || target == null) {
            return;
        }
        if (!isValidMonarchTarget(owner.getUniqueId(), target, true)) {
            return;
        }

        Set<UUID> summons = monarchSummonsByOwner.get(owner.getUniqueId());
        if (summons == null || summons.isEmpty()) {
            return;
        }

        for (UUID mobId : new HashSet<>(summons)) {
            Entity entity = Bukkit.getEntity(mobId);
            if (entity == null) {
                continue;
            }
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
        despawnMonarchSummons(ownerId, true);
    }

    private int despawnMonarchSummons(UUID ownerId, boolean rememberUnloaded) {
        if (rememberUnloaded && ownerId != null) {
            pendingMonarchUnsummonOwners.add(ownerId);
        }
        Set<UUID> summons = monarchSummonsByOwner.remove(ownerId);
        if (summons == null) {
            return 0;
        }
        int removed = 0;
        for (UUID mobId : summons) {
            monarchOwnerByMob.remove(mobId);
            Entity entity = Bukkit.getEntity(mobId);
            if (entity != null && entity.isValid()) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    private void tickMonarchSummons() {
        for (Map.Entry<UUID, Set<UUID>> entry : new HashMap<>(monarchSummonsByOwner).entrySet()) {
            UUID ownerId = entry.getKey();
            Set<UUID> summons = entry.getValue();
            if (summons == null || summons.isEmpty()) {
                monarchSummonsByOwner.remove(ownerId);
                continue;
            }

            for (UUID mobId : new HashSet<>(summons)) {
                Entity entity = Bukkit.getEntity(mobId);
                if (entity == null) {
                    continue;
                }
                if (!(entity instanceof Mob mob) || !mob.isValid() || mob.isDead()) {
                    summons.remove(mobId);
                    monarchOwnerByMob.remove(mobId);
                    continue;
                }

                hardenMonarchSummon(mob, false);
                LivingEntity currentTarget = mob.getTarget();
                if (currentTarget != null
                    && currentTarget.isValid()
                    && !currentTarget.isDead()
                    && isValidMonarchTarget(ownerId, currentTarget, true)) {
                    continue;
                }

                LivingEntity nextTarget = findBestMonarchTarget(ownerId, mob);
                mob.setTarget(nextTarget);
            }

            if (summons.isEmpty()) {
                monarchSummonsByOwner.remove(ownerId);
            }
        }
    }

    private LivingEntity findBestMonarchTarget(UUID ownerId, Mob mob) {
        LivingEntity bestHostile = null;
        double bestPlayerDistance = Double.MAX_VALUE;
        double bestHostileDistance = Double.MAX_VALUE;

        for (Entity entity : mob.getWorld().getNearbyEntities(
            mob.getLocation(),
            MONARCH_TARGET_RANGE,
            MONARCH_TARGET_VERTICAL_RANGE,
            MONARCH_TARGET_RANGE
        )) {
            if (!(entity instanceof LivingEntity living) || living.equals(mob)) {
                continue;
            }
            if (!isValidMonarchTarget(ownerId, living, false)) {
                continue;
            }
            double distance = living.getLocation().distanceSquared(mob.getLocation());
            if (living instanceof Player && distance < bestPlayerDistance) {
                bestPlayerDistance = distance;
                bestHostile = living;
                continue;
            }
            if (bestPlayerDistance == Double.MAX_VALUE && isHostileMob(living) && distance < bestHostileDistance) {
                bestHostileDistance = distance;
                bestHostile = living;
            }
        }
        return bestHostile;
    }

    private boolean isValidMonarchTarget(UUID ownerId, LivingEntity target, boolean allowPassive) {
        if (target == null || target.isDead() || !target.isValid()) {
            return false;
        }
        if (target instanceof Player player) {
            return player.getGameMode() != GameMode.SPECTATOR
                && !sameTeamOrSelf(ownerId, player.getUniqueId());
        }
        if (monarchOwnerByMob.containsKey(target.getUniqueId())) {
            return false;
        }
        if (target instanceof Tameable tameable && tameable.getOwner() != null) {
            return !sameTeamOrSelf(ownerId, tameable.getOwner().getUniqueId());
        }
        return allowPassive || isHostileMob(target);
    }

    private boolean isTaggedMonarchSummon(Mob mob) {
        Byte tag = mob.getPersistentDataContainer().get(keyMonarchSummonTag, PersistentDataType.BYTE);
        return tag != null && tag == (byte) 1;
    }

    private UUID monarchOwnerIdFromPdc(Mob mob) {
        String raw = mob.getPersistentDataContainer().get(keyMonarchSummonOwner, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
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
        if (plugin.getDuelManager() != null && plugin.getDuelManager().areOpponents(ownerId, targetId)) {
            return false;
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
        if (power != SuperpowerType.ARCANIST) {
            syncPlayerAttributeModifier(player, Attribute.LUCK, keyEnchanterLuckModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
            syncPlayerAttributeModifier(player, Attribute.MOVEMENT_SPEED, keyEnchanterMoveModifier, 0.0, AttributeModifier.Operation.ADD_SCALAR, false);
            syncPlayerAttributeModifier(player, Attribute.ATTACK_SPEED, keyEnchanterAttackModifier, 0.0, AttributeModifier.Operation.ADD_SCALAR, false);
        }
        if (power != SuperpowerType.PROSPECTOR) {
            syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keyMinerHealthModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
        }
        if (power != SuperpowerType.TITAN) {
            syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keyGiantHealthModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
            syncPlayerAttributeModifier(player, Attribute.SCALE, keyGiantScaleModifier, 0.0, AttributeModifier.Operation.ADD_SCALAR, false);
            syncPlayerAttributeModifier(player, Attribute.KNOCKBACK_RESISTANCE, keyGiantKnockbackModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
            syncPlayerAttributeModifier(player, Attribute.ATTACK_DAMAGE, keyGiantAttackDamageModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
        }
        if (power != SuperpowerType.SKYBOUND) {
            syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keySupermanHealthModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
        }
        if (power != SuperpowerType.SENTINEL) {
            syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keySentinelHealthModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
        }
        if (power != SuperpowerType.VEIL_ASSASSIN) {
            syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keyVeilAssassinHealthModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
            syncPlayerAttributeModifier(player, Attribute.SNEAKING_SPEED, keyVeilAssassinSneakingSpeedModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
            clearVeilAssassinState(player, true);
        }
        if (power != SuperpowerType.TIDEBORN) {
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
        syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keyVeilAssassinHealthModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
        syncPlayerAttributeModifier(player, Attribute.SNEAKING_SPEED, keyVeilAssassinSneakingSpeedModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER, false);
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
            oracleXrayScanCursors.remove(player.getUniqueId());
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
        boolean bossRestricted = isActiveBossFight(player);
        boolean shouldAllow = plugin.getPlayerManager().hasFlightEnabled(player.getUniqueId())
            || (!bossRestricted && supermanAvailable && (active || offCooldown))
            || (!bossRestricted && plugin.getCustomEnchantListener() != null && plugin.getCustomEnchantListener().shouldRetainFlightAccess(player));

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
            syncSupermanFlightState(player, hasPower(player, SuperpowerType.SKYBOUND));
            return;
        }

        setSupermanFlightActiveUntil(player, 0L);
        if (expired) {
            setSupermanFlightCooldownUntil(player, System.currentTimeMillis() + (SUPERMAN_FLIGHT_COOLDOWN_SECONDS * 1000L));
        }
        if (player.isFlying() && !plugin.getPlayerManager().hasFlightEnabled(player.getUniqueId())) {
            player.setFlying(false);
        }
        syncSupermanFlightState(player, hasPower(player, SuperpowerType.SKYBOUND));
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
        int diameter = (XRAY_ORE_RADIUS * 2) + 1;
        int horizontalArea = diameter * diameter;
        int height = (maxY - minY) + 1;
        int totalBlocks = horizontalArea * height;
        int start = Math.floorMod(oracleXrayScanCursors.getOrDefault(player.getUniqueId(), 0), totalBlocks);
        int scanCount = Math.min(XRAY_ORE_SCAN_BUDGET, totalBlocks);
        for (int offset = 0; offset < scanCount; offset++) {
            int index = (start + offset) % totalBlocks;
            int y = minY + (index / horizontalArea);
            int horizontalIndex = index % horizontalArea;
            int x = baseX - XRAY_ORE_RADIUS + (horizontalIndex / diameter);
            int z = baseZ - XRAY_ORE_RADIUS + (horizontalIndex % diameter);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            Block block = world.getBlockAt(x, y, z);
            if (MINER_ORE_BLOCKS.contains(block.getType())) {
                spawnXrayOreMarker(player, block);
            }
        }
        oracleXrayScanCursors.put(player.getUniqueId(), (start + scanCount) % totalBlocks);
        alertOracleValuableNearby(player, origin);
    }

    private void alertOracleValuableNearby(Player player, Location origin) {
        long now = System.currentTimeMillis();
        long nextAt = oracleValuableAlertCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (nextAt > now || !hasValuableOreNearby(origin, XRAY_VALUABLE_ALERT_RADIUS)) {
            return;
        }
        oracleValuableAlertCooldowns.put(player.getUniqueId(), now + XRAY_VALUABLE_ALERT_INTERVAL_MS);
        player.sendMessage(MessageUtil.info("You sense something valuable nearby."));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45f, 1.65f);
    }

    private boolean hasValuableOreNearby(Location origin, int radius) {
        World world = origin.getWorld();
        if (world == null) {
            return false;
        }
        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY();
        int baseZ = origin.getBlockZ();
        int minY = Math.max(world.getMinHeight(), baseY - radius);
        int maxY = Math.min(world.getMaxHeight() - 1, baseY + radius);
        for (int x = baseX - radius; x <= baseX + radius; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = baseZ - radius; z <= baseZ + radius; z++) {
                    double dx = x - baseX;
                    double dy = y - baseY;
                    double dz = z - baseZ;
                    if ((dx * dx) + (dy * dy) + (dz * dz) > radius * radius) {
                        continue;
                    }
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                        continue;
                    }
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (type == Material.DIAMOND_ORE || type == Material.DEEPSLATE_DIAMOND_ORE || type == Material.ANCIENT_DEBRIS) {
                        return true;
                    }
                }
            }
        }
        return false;
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
        if (isActiveBossFight(player)) {
            player.sendMessage(MessageUtil.warn("Boss encounters resist Time Stop."));
            return;
        }
        if (denyUnsafeSpawnAbilityArea(player, player.getLocation(), TIME_STOP_RADIUS)) {
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
                if (isBossEncounterEntity(entity)
                    || (entity instanceof Projectile projectile && isBossOwnedProjectile(projectile))) {
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

    private boolean isPowerFrozenPlayer(UUID playerId) {
        return playerId != null && (timeStoppedPlayers.contains(playerId) || honoredDomainParalyzedPlayers.contains(playerId));
    }

    private void tickHonoredPowers() {
        long now = System.currentTimeMillis();
        tickHonoredDomains(now);
        tickHonoredProjectileAura(now);
        tickHonoredFrozenProjectiles(now);
    }

    private void tickHonoredDomains(long now) {
        Set<UUID> currentParalyzed = new HashSet<>();
        for (Map.Entry<UUID, HonoredDomainState> entry : new HashMap<>(activeHonoredDomains).entrySet()) {
            HonoredDomainState state = entry.getValue();
            Player owner = Bukkit.getPlayer(state.ownerId());
            if (owner == null || !owner.isOnline() || owner.isDead() || owner.getGameMode() == GameMode.SPECTATOR) {
                endHonoredDomain(state.ownerId(), false);
                continue;
            }
            if (state.expiresAt() <= now) {
                endHonoredDomain(state.ownerId(), true);
                continue;
            }
            if (!isInsideHonoredDomain(owner.getLocation(), state)) {
                clearHonoredDomainTeleportVisualsNowAndLater(owner);
                owner.teleport(honoredDomainSpawn(state.domainCenter(), 0, state.participantIds().size(), owner));
                clearHonoredDomainTeleportVisualsNowAndLater(owner);
            }

            renderHonoredDomain(state);
            int index = 1;
            for (UUID participantId : new HashSet<>(state.participantIds())) {
                if (participantId.equals(state.ownerId())) {
                    continue;
                }
                Player participant = Bukkit.getPlayer(participantId);
                if (participant == null || !participant.isOnline()) {
                    continue;
                }
                if (participant.isDead() || participant.getGameMode() == GameMode.SPECTATOR) {
                    handleHonoredDomainPlayerExit(participantId, false);
                    continue;
                }
                if (!isInsideHonoredDomain(participant.getLocation(), state)) {
                    clearHonoredDomainTeleportVisualsNowAndLater(participant);
                    participant.teleport(honoredDomainSpawn(state.domainCenter(), index, state.participantIds().size(), participant));
                    clearHonoredDomainTeleportVisualsNowAndLater(participant);
                }
                participant.setVelocity(new Vector());
                participant.setFallDistance(0.0f);
                applyHonoredDomainLockEffects(participant);
                currentParalyzed.add(participantId);
                index++;
            }
        }
        honoredDomainParalyzedPlayers.clear();
        honoredDomainParalyzedPlayers.addAll(currentParalyzed);
    }

    private void renderHonoredDomain(HonoredDomainState state) {
        World world = state.domainCenter().getWorld();
        if (world == null) {
            return;
        }
        Location center = state.domainCenter().clone().add(0.0, 1.0, 0.0);
        world.spawnParticle(Particle.REVERSE_PORTAL, center, 14, 0.55, 0.7, 0.55, 0.08);
        for (int i = 0; i < 28; i++) {
            double angle = (Math.PI * 2.0 * i) / 28.0;
            double x = Math.cos(angle) * HONORED_DOMAIN_PLATFORM_RADIUS;
            double z = Math.sin(angle) * HONORED_DOMAIN_PLATFORM_RADIUS;
            Location ring = center.clone().add(x, 0.05, z);
            world.spawnParticle(
                Particle.DUST,
                ring,
                1,
                0.02,
                0.02,
                0.02,
                0.0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(185, 225, 255), 1.15f)
            );
        }
    }

    private void renderHonoredDomainOpening(HonoredDomainState state) {
        World world = state.domainCenter().getWorld();
        if (world == null) {
            return;
        }
        Location center = state.domainCenter().clone().add(0.0, 1.0, 0.0);
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.85f, 1.8f);
        world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.55f);
        world.spawnParticle(Particle.FLASH, center, 1, 0.0, 0.0, 0.0, 0.0, org.bukkit.Color.WHITE);
        world.spawnParticle(Particle.END_ROD, center, 130, 3.5, 1.0, 3.5, 0.08);
    }

    private List<Player> honoredDomainParticipants(Player owner) {
        Location center = owner.getLocation();
        List<Player> participants = new ArrayList<>();
        participants.add(owner);
        for (Player candidate : owner.getWorld().getPlayers()) {
            if (candidate.equals(owner) || candidate.isDead() || candidate.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Location location = candidate.getLocation();
            if (Math.abs(location.getX() - center.getX()) > HONORED_DOMAIN_HALF_RANGE
                || Math.abs(location.getY() - center.getY()) > HONORED_DOMAIN_HALF_RANGE
                || Math.abs(location.getZ() - center.getZ()) > HONORED_DOMAIN_HALF_RANGE) {
                continue;
            }
            participants.add(candidate);
        }
        participants.sort(Comparator.comparingDouble(player -> player.equals(owner) ? -1.0 : player.getLocation().distanceSquared(center)));
        return participants;
    }

    private Location honoredDomainCenter(Player player, World endWorld) {
        int y = Math.min(endWorld.getMaxHeight() - HONORED_DOMAIN_WALL_HEIGHT - 2, endWorld.getMinHeight() + 16);
        Location base = new Location(
            endWorld,
            player.getLocation().getBlockX() + 0.5,
            y,
            player.getLocation().getBlockZ() + 0.5,
            player.getLocation().getYaw(),
            player.getLocation().getPitch()
        );
        if (!honoredDomainCenterOverlaps(base)) {
            return base;
        }

        double spacing = (HONORED_DOMAIN_PLATFORM_RADIUS * 2.0) + 10.0;
        for (int ring = 1; ring <= 8; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.abs(dx) != ring && Math.abs(dz) != ring) {
                        continue;
                    }
                    Location candidate = base.clone().add(dx * spacing, 0.0, dz * spacing);
                    if (!honoredDomainCenterOverlaps(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return base.clone().add(spacing * (activeHonoredDomains.size() + 1), 0.0, 0.0);
    }

    private boolean honoredDomainCenterOverlaps(Location candidate) {
        if (candidate == null || candidate.getWorld() == null) {
            return true;
        }
        double minimumGap = (HONORED_DOMAIN_PLATFORM_RADIUS * 2.0) + 5.0;
        for (HonoredDomainState state : activeHonoredDomains.values()) {
            Location active = state.domainCenter();
            if (active.getWorld() == null || !active.getWorld().equals(candidate.getWorld())) {
                continue;
            }
            if (Math.abs(active.getX() - candidate.getX()) <= minimumGap
                && Math.abs(active.getZ() - candidate.getZ()) <= minimumGap) {
                return true;
            }
        }
        return false;
    }

    private Location honoredDomainSpawn(Location center, int index, int total, Player player) {
        Location destination = center.clone();
        if (index > 0 && total > 1) {
            double angle = (Math.PI * 2.0 * (index - 1)) / Math.max(1, total - 1);
            double radius = Math.min(5.5, 2.0 + (Math.max(0, index - 1) / 6) * 1.5);
            destination.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
        }
        destination.setYaw(player.getLocation().getYaw());
        destination.setPitch(player.getLocation().getPitch());
        return destination;
    }

    private List<BlockState> prepareHonoredDomainPlatform(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return List.of();
        }
        loadHonoredDomainChunks(center);
        List<BlockState> restoreBlocks = new ArrayList<>();
        int baseX = center.getBlockX();
        int baseY = center.getBlockY();
        int baseZ = center.getBlockZ();
        for (int x = -HONORED_DOMAIN_PLATFORM_RADIUS; x <= HONORED_DOMAIN_PLATFORM_RADIUS; x++) {
            for (int z = -HONORED_DOMAIN_PLATFORM_RADIUS; z <= HONORED_DOMAIN_PLATFORM_RADIUS; z++) {
                for (int y = -1; y <= HONORED_DOMAIN_WALL_HEIGHT; y++) {
                    Block block = world.getBlockAt(baseX + x, baseY + y, baseZ + z);
                    restoreBlocks.add(block.getState());
                    boolean shell = y == -1
                        || y == HONORED_DOMAIN_WALL_HEIGHT
                        || Math.abs(x) == HONORED_DOMAIN_PLATFORM_RADIUS
                        || Math.abs(z) == HONORED_DOMAIN_PLATFORM_RADIUS;
                    if (shell) {
                        block.setType(Material.SCULK, false);
                    } else if (isHonoredDomainLightBlock(x, y, z)) {
                        block.setType(Material.LIGHT, false);
                        if (block.getBlockData() instanceof Light light) {
                            light.setLevel(light.getMaximumLevel());
                            block.setBlockData(light, false);
                        }
                    } else {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
        return restoreBlocks;
    }

    private void loadHonoredDomainChunks(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        int minChunkX = (center.getBlockX() - HONORED_DOMAIN_PLATFORM_RADIUS) >> 4;
        int maxChunkX = (center.getBlockX() + HONORED_DOMAIN_PLATFORM_RADIUS) >> 4;
        int minChunkZ = (center.getBlockZ() - HONORED_DOMAIN_PLATFORM_RADIUS) >> 4;
        int maxChunkZ = (center.getBlockZ() + HONORED_DOMAIN_PLATFORM_RADIUS) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                world.getChunkAt(chunkX, chunkZ);
            }
        }
    }

    private boolean isHonoredDomainLightBlock(int x, int y, int z) {
        if (y != HONORED_DOMAIN_LIGHT_HEIGHT) {
            return false;
        }
        boolean litX = x == 0 || Math.abs(x) == HONORED_DOMAIN_LIGHT_OFFSET;
        boolean litZ = z == 0 || Math.abs(z) == HONORED_DOMAIN_LIGHT_OFFSET;
        return litX && litZ;
    }

    private boolean isInsideHonoredDomain(Location location, HonoredDomainState state) {
        if (location == null || state == null || location.getWorld() == null || state.domainCenter().getWorld() == null) {
            return false;
        }
        if (!location.getWorld().equals(state.domainCenter().getWorld())) {
            return false;
        }
        return Math.abs(location.getX() - state.domainCenter().getX()) <= HONORED_DOMAIN_PLATFORM_RADIUS + 1.0
            && Math.abs(location.getZ() - state.domainCenter().getZ()) <= HONORED_DOMAIN_PLATFORM_RADIUS + 1.0
            && location.getY() >= state.domainCenter().getY() - 1.5
            && location.getY() <= state.domainCenter().getY() + HONORED_DOMAIN_WALL_HEIGHT + 0.5;
    }

    private boolean isInsideAnyHonoredDomain(Location location) {
        for (HonoredDomainState state : activeHonoredDomains.values()) {
            if (isInsideHonoredDomain(location, state)) {
                return true;
            }
        }
        return false;
    }

    private void applyHonoredDomainLockEffects(Player participant) {
        applyHonoredDomainEffect(participant, PotionEffectType.SLOWNESS, 10, 10, false, false);
        applyHonoredDomainEffect(participant, PotionEffectType.MINING_FATIGUE, 10, 4, false, false);
        applyHonoredDomainEffect(participant, PotionEffectType.WEAKNESS, 10, 10, false, false);
        applyHonoredDomainEffect(participant, PotionEffectType.NAUSEA, HONORED_DOMAIN_NAUSEA_DURATION_TICKS, HONORED_DOMAIN_NAUSEA_AMPLIFIER, true, true);
        applyHonoredDomainEffect(participant, PotionEffectType.DARKNESS, 45, 0, false, false);
    }

    private void applyHonoredDomainEffect(
        Player participant,
        PotionEffectType type,
        int durationTicks,
        int amplifier,
        boolean particles,
        boolean icon
    ) {
        PotionEffect current = participant.getPotionEffect(type);
        if (current != null && current.getAmplifier() >= amplifier && current.getDuration() >= Math.max(1, durationTicks - 3)) {
            return;
        }
        if (current != null) {
            participant.removePotionEffect(type);
        }
        participant.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, true, particles, icon));
    }

    private void clearHonoredDomainLockEffects(Player participant) {
        removeLikelyPowerPotion(participant, PotionEffectType.SLOWNESS, 10);
        removeLikelyPowerPotion(participant, PotionEffectType.MINING_FATIGUE, 4);
        removeLikelyPowerPotion(participant, PotionEffectType.WEAKNESS, 10);
        removeLikelyPowerPotion(participant, PotionEffectType.NAUSEA, HONORED_DOMAIN_NAUSEA_AMPLIFIER, HONORED_DOMAIN_NAUSEA_DURATION_TICKS + 40);
        removeLikelyPowerPotion(participant, PotionEffectType.DARKNESS, 0);
    }

    private boolean triggerHonoredDomainPulse(Player attacker) {
        if (!hasPower(attacker, SuperpowerType.HONORED_ONE)) {
            return false;
        }
        HonoredDomainState state = activeHonoredDomains.get(attacker.getUniqueId());
        if (state == null || !isInsideHonoredDomain(attacker.getLocation(), state)) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long cooldownUntil = honoredDomainSwingCooldowns.get(attacker.getUniqueId());
        if (cooldownUntil != null && cooldownUntil > now) {
            return true;
        }
        honoredDomainSwingCooldowns.put(attacker.getUniqueId(), now + HONORED_DOMAIN_SWING_COOLDOWN_MS);

        double damage = honoredDomainPulseDamage(attacker);
        int hits = 0;
        for (UUID participantId : new HashSet<>(state.participantIds())) {
            if (participantId.equals(attacker.getUniqueId())) {
                continue;
            }
            Player target = Bukkit.getPlayer(participantId);
            if (target == null || !target.isOnline() || target.isDead() || target.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (!isInsideHonoredDomain(target.getLocation(), state)) {
                continue;
            }
            double multiplier = sameTeamOrSelf(attacker.getUniqueId(), target.getUniqueId())
                ? HONORED_DOMAIN_TEAMMATE_DAMAGE_MULTIPLIER
                : 1.0;
            damageHonoredDomainTarget(attacker, target, damage * multiplier);
            hits++;
        }
        if (hits > 0) {
            Location effect = attacker.getLocation().clone().add(0.0, 1.0, 0.0);
            attacker.getWorld().spawnParticle(Particle.SONIC_BOOM, effect, 1, 0.0, 0.0, 0.0, 0.0);
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.75f, 1.75f);
        }
        return true;
    }

    private double honoredDomainPulseDamage(Player attacker) {
        var attribute = attacker.getAttribute(Attribute.ATTACK_DAMAGE);
        double damage = attribute == null ? 1.0 : attribute.getValue();
        return Math.max(1.0, damage * Math.max(0.35, attacker.getAttackCooldown()));
    }

    private boolean shouldCancelRecentHonoredDomainDirectHit(Player attacker, Player hitPlayer) {
        if (!isRecentHonoredDomainSwing(attacker)) {
            return false;
        }
        HonoredDomainState state = activeHonoredDomains.get(attacker.getUniqueId());
        return state != null
            && state.participantIds().contains(hitPlayer.getUniqueId())
            && isInsideHonoredDomain(attacker.getLocation(), state)
            && isInsideHonoredDomain(hitPlayer.getLocation(), state);
    }

    private boolean isRecentHonoredDomainSwing(Player attacker) {
        Long cooldownUntil = honoredDomainSwingCooldowns.get(attacker.getUniqueId());
        if (cooldownUntil == null) {
            return false;
        }
        if (cooldownUntil <= System.currentTimeMillis()) {
            honoredDomainSwingCooldowns.remove(attacker.getUniqueId());
            return false;
        }
        return true;
    }

    private void damageHonoredDomainTarget(Player attacker, Player target, double damage) {
        target.setNoDamageTicks(0);
        honoredDomainDamageGuards.add(target.getUniqueId());
        try {
            AbilityDamageContext.damage(attacker, target, Math.max(0.1, damage));
        } finally {
            honoredDomainDamageGuards.remove(target.getUniqueId());
        }
        Location effect = target.getLocation().clone().add(0.0, 1.0, 0.0);
        target.getWorld().spawnParticle(Particle.END_ROD, effect, 18, 0.35, 0.45, 0.35, 0.04);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.65f, 1.65f);
    }

    private void clearHonoredDomainTeleportVisualsNowAndLater(Player player) {
        clearHonoredDomainTeleportVisuals(player);
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        for (long delay : new long[] {1L, 5L, 20L}) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player online = Bukkit.getPlayer(playerId);
                if (online != null && online.isOnline()) {
                    clearHonoredDomainTeleportVisuals(online);
                }
            }, delay);
        }
    }

    private void clearHonoredDomainTeleportVisuals(Player player) {
        if (plugin.getPlayerVisualListener() != null) {
            plugin.getPlayerVisualListener().clearTeleportVisuals(player);
        }
    }

    private void applyHonoredDomainDamage(EntityDamageByEntityEvent event, Player attacker, LivingEntity hitVictim) {
        if (!hasPower(attacker, SuperpowerType.HONORED_ONE)) {
            return;
        }
        HonoredDomainState state = activeHonoredDomains.get(attacker.getUniqueId());
        if (state == null || !isInsideHonoredDomain(attacker.getLocation(), state)) {
            return;
        }
        double damage = event.getDamage();
        if (damage <= 0.0) {
            return;
        }
        if (hitVictim instanceof Player hitPlayer
            && state.participantIds().contains(hitPlayer.getUniqueId())
            && sameTeamOrSelf(attacker.getUniqueId(), hitPlayer.getUniqueId())) {
            event.setDamage(damage * HONORED_DOMAIN_TEAMMATE_DAMAGE_MULTIPLIER);
        }
        for (UUID participantId : new HashSet<>(state.participantIds())) {
            if (participantId.equals(attacker.getUniqueId())
                || (hitVictim != null && participantId.equals(hitVictim.getUniqueId()))) {
                continue;
            }
            Player target = Bukkit.getPlayer(participantId);
            if (target == null || !target.isOnline() || target.isDead() || target.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (!isInsideHonoredDomain(target.getLocation(), state)) {
                continue;
            }
            double multiplier = sameTeamOrSelf(attacker.getUniqueId(), target.getUniqueId())
                ? HONORED_DOMAIN_TEAMMATE_DAMAGE_MULTIPLIER
                : 1.0;
            damageHonoredDomainTarget(attacker, target, damage * multiplier);
        }
    }

    private void endHonoredDomain(UUID ownerId, boolean announce) {
        HonoredDomainState state = activeHonoredDomains.remove(ownerId);
        if (state == null) {
            return;
        }
        honoredDomainSwingCooldowns.remove(ownerId);
        for (UUID participantId : new HashSet<>(state.participantIds())) {
            honoredDomainParalyzedPlayers.remove(participantId);
            Player participant = Bukkit.getPlayer(participantId);
            Location returnLocation = state.returnLocations().get(participantId);
            if (returnLocation == null || returnLocation.getWorld() == null) {
                continue;
            }
            if (participant == null || !participant.isOnline()) {
                pendingHonoredDomainReturns.put(participantId, returnLocation);
                continue;
            }
            participant.setFallDistance(0.0f);
            clearHonoredDomainTeleportVisualsNowAndLater(participant);
            participant.teleport(returnLocation);
            clearHonoredDomainTeleportVisualsNowAndLater(participant);
            clearHonoredDomainLockEffects(participant);
            if (announce) {
                participant.sendMessage(MessageUtil.info("The domain fades."));
                participant.playSound(participant.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.25f);
                participant.getWorld().spawnParticle(Particle.PORTAL, participant.getLocation().clone().add(0.0, 1.0, 0.0), 35, 0.45, 0.55, 0.45, 0.08);
            }
        }
        restoreHonoredDomainBlocks(state.restoreBlocks());
    }

    private void endAllHonoredDomains(boolean announce) {
        for (UUID ownerId : new HashSet<>(activeHonoredDomains.keySet())) {
            endHonoredDomain(ownerId, announce);
        }
        honoredDomainParalyzedPlayers.clear();
        honoredDomainSwingCooldowns.clear();
    }

    private void rememberHonoredDomainDeathChestOrigin(UUID playerId) {
        Location returnLocation = honoredDomainReturnLocation(playerId);
        if (returnLocation == null || returnLocation.getWorld() == null) {
            return;
        }
        Location cached = returnLocation.clone();
        honoredDomainDeathChestOrigins.put(playerId, cached);
        Bukkit.getScheduler().runTask(plugin, () -> honoredDomainDeathChestOrigins.remove(playerId, cached));
    }

    private Location honoredDomainReturnLocation(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        HonoredDomainState ownedDomain = activeHonoredDomains.get(playerId);
        if (ownedDomain != null) {
            return ownedDomain.returnLocations().get(playerId);
        }
        for (HonoredDomainState state : activeHonoredDomains.values()) {
            if (!state.participantIds().contains(playerId)) {
                continue;
            }
            Location returnLocation = state.returnLocations().get(playerId);
            if (returnLocation != null) {
                return returnLocation;
            }
        }
        return null;
    }

    private void handleHonoredDomainPlayerExit(UUID playerId, boolean keepReturnForRejoin) {
        if (playerId == null) {
            return;
        }
        honoredDomainSwingCooldowns.remove(playerId);
        if (activeHonoredDomains.containsKey(playerId)) {
            endHonoredDomain(playerId, false);
            return;
        }
        for (HonoredDomainState state : new ArrayList<>(activeHonoredDomains.values())) {
            if (!state.participantIds().remove(playerId)) {
                continue;
            }
            honoredDomainParalyzedPlayers.remove(playerId);
            Location returnLocation = state.returnLocations().remove(playerId);
            if (keepReturnForRejoin && returnLocation != null && returnLocation.getWorld() != null) {
                pendingHonoredDomainReturns.put(playerId, returnLocation);
            }
            if (state.participantIds().size() <= 1) {
                endHonoredDomain(state.ownerId(), false);
            }
        }
    }

    private void returnPendingHonoredDomainPlayer(Player player) {
        Location returnLocation = pendingHonoredDomainReturns.remove(player.getUniqueId());
        if (returnLocation == null || returnLocation.getWorld() == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.isDead()) {
                pendingHonoredDomainReturns.put(player.getUniqueId(), returnLocation);
                return;
            }
            clearHonoredDomainTeleportVisualsNowAndLater(player);
            player.teleport(returnLocation);
            player.setFallDistance(0.0f);
            clearHonoredDomainTeleportVisualsNowAndLater(player);
            clearHonoredDomainLockEffects(player);
            player.sendMessage(MessageUtil.info("The domain released you."));
        });
    }

    private void restoreHonoredDomainBlocks(List<BlockState> restoreBlocks) {
        for (int i = restoreBlocks.size() - 1; i >= 0; i--) {
            restoreBlocks.get(i).update(true, false);
        }
    }

    private void tickHonoredProjectileAura(long now) {
        honoredAuraPulse++;
        boolean renderAura = honoredAuraPulse % 10L == 0L;
        boolean scanAura = honoredAuraPulse % 2L == 0L;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!hasCommandPower(player, SuperpowerType.HONORED_ONE)
                || !isHonoredInfinityEnabled(player)
                || player.isDead()
                || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Location center = player.getLocation().clone().add(0.0, 1.0, 0.0);
            if (renderAura) {
                renderHonoredProjectileAura(player, center);
            }
            if (!scanAura) {
                continue;
            }
            for (Entity entity : player.getWorld().getNearbyEntities(center, HONORED_PROJECTILE_AURA_RADIUS, HONORED_PROJECTILE_AURA_RADIUS, HONORED_PROJECTILE_AURA_RADIUS)) {
                if (!(entity instanceof Projectile projectile) || !shouldFreezeHonoredProjectile(projectile, player, center)) {
                    continue;
                }
                freezeHonoredProjectile(projectile, player);
            }
        }
    }

    private void renderHonoredProjectileAura(Player player, Location center) {
        World world = player.getWorld();
        for (int i = 0; i < 14; i++) {
            double angle = (Math.PI * 2.0 * i) / 14.0;
            Location point = center.clone().add(
                Math.cos(angle) * HONORED_PROJECTILE_AURA_RADIUS,
                Math.sin(angle * 2.0) * 0.35,
                Math.sin(angle) * HONORED_PROJECTILE_AURA_RADIUS
            );
            world.spawnParticle(
                Particle.DUST,
                point,
                1,
                0.02,
                0.02,
                0.02,
                0.0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(210, 245, 255), 0.85f)
            );
        }
    }

    private boolean shouldFreezeHonoredProjectile(Projectile projectile, Player player, Location auraCenter) {
        if (!isHonoredInfinityEnabled(player)
            || !projectile.isValid()
            || projectile.isDead()
            || honoredFrozenProjectiles.containsKey(projectile.getUniqueId())
            || isBossOwnedProjectile(projectile)) {
            return false;
        }
        if (projectile.getShooter() instanceof Entity shooter && shooter.getUniqueId().equals(player.getUniqueId())) {
            return false;
        }
        if (isPlayerUtilityProjectile(projectile) && projectile.getShooter() instanceof Player) {
            return false;
        }
        return projectile.getLocation().distanceSquared(auraCenter) <= HONORED_PROJECTILE_AURA_RADIUS * HONORED_PROJECTILE_AURA_RADIUS;
    }

    private void freezeHonoredProjectile(Projectile projectile, Player player) {
        if (!isHonoredInfinityEnabled(player) || !projectile.isValid() || projectile.isDead() || isBossOwnedProjectile(projectile)) {
            return;
        }
        if (projectile.getShooter() instanceof Entity shooter && shooter.getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        if (isPlayerUtilityProjectile(projectile) && projectile.getShooter() instanceof Player) {
            return;
        }
        UUID projectileId = projectile.getUniqueId();
        honoredFrozenProjectiles.computeIfAbsent(projectileId, ignored -> new HonoredFrozenProjectileState(
            player.getUniqueId(),
            projectile.getLocation().clone(),
            projectile.getVelocity().clone(),
            projectile.hasGravity(),
            System.currentTimeMillis() + HONORED_PROJECTILE_FREEZE_MS
        ));
        HonoredFrozenProjectileState state = honoredFrozenProjectiles.get(projectileId);
        projectile.setGravity(false);
        projectile.teleport(state.location());
        projectile.setVelocity(new Vector());
        projectile.getWorld().spawnParticle(Particle.END_ROD, state.location(), 12, 0.18, 0.18, 0.18, 0.02);
        projectile.getWorld().playSound(state.location(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.55f, 1.85f);
    }

    private boolean shouldBlockHonoredIncomingProjectile(Projectile projectile, Player target) {
        if (!isHonoredInfinityEnabled(target) || isBossOwnedProjectile(projectile)) {
            return false;
        }
        if (projectile.getShooter() instanceof Entity shooter && shooter.getUniqueId().equals(target.getUniqueId())) {
            return false;
        }
        return !(isPlayerUtilityProjectile(projectile) && projectile.getShooter() instanceof Player);
    }

    private boolean isPlayerUtilityProjectile(Projectile projectile) {
        EntityType type = projectile.getType();
        return type == EntityType.WIND_CHARGE
            || type == EntityType.ENDER_PEARL
            || type == EntityType.FISHING_BOBBER;
    }

    private void releaseHonoredFrozenProjectiles(UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        for (Map.Entry<UUID, HonoredFrozenProjectileState> entry : new HashMap<>(honoredFrozenProjectiles).entrySet()) {
            HonoredFrozenProjectileState state = entry.getValue();
            if (!ownerId.equals(state.ownerId())) {
                continue;
            }
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity instanceof Projectile projectile && projectile.isValid()) {
                projectile.setGravity(state.hadGravity());
                projectile.setVelocity(state.velocity());
            }
            honoredFrozenProjectiles.remove(entry.getKey());
        }
    }

    private void tickHonoredFrozenProjectiles(long now) {
        for (Map.Entry<UUID, HonoredFrozenProjectileState> entry : new HashMap<>(honoredFrozenProjectiles).entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof Projectile projectile) || !projectile.isValid() || projectile.isDead()) {
                honoredFrozenProjectiles.remove(entry.getKey());
                continue;
            }
            HonoredFrozenProjectileState state = entry.getValue();
            if (state.expiresAt() <= now) {
                projectile.getWorld().spawnParticle(Particle.REVERSE_PORTAL, state.location(), 18, 0.18, 0.18, 0.18, 0.06);
                projectile.remove();
                honoredFrozenProjectiles.remove(entry.getKey());
                continue;
            }
            projectile.setGravity(false);
            projectile.teleport(state.location());
            projectile.setVelocity(new Vector());
        }
    }

    private void restoreHonoredFrozenProjectiles(boolean remove) {
        for (Map.Entry<UUID, HonoredFrozenProjectileState> entry : new HashMap<>(honoredFrozenProjectiles).entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity instanceof Projectile projectile && projectile.isValid()) {
                if (remove) {
                    projectile.remove();
                } else {
                    projectile.setGravity(entry.getValue().hadGravity());
                    projectile.setVelocity(entry.getValue().velocity());
                }
            }
        }
        honoredFrozenProjectiles.clear();
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

    private boolean consumeHeldItem(Player player, EquipmentSlot hand, Predicate<ItemStack> matcher) {
        ItemStack held = itemInHand(player, hand);
        if (held == null || held.getType().isAir() || (matcher != null && !matcher.test(held))) {
            return false;
        }
        if (held.getAmount() <= 1) {
            setItemInHand(player, hand, null);
            return true;
        }
        held.setAmount(held.getAmount() - 1);
        setItemInHand(player, hand, held);
        return true;
    }

    private ItemStack itemInHand(Player player, EquipmentSlot hand) {
        if (hand == EquipmentSlot.OFF_HAND) {
            return player.getInventory().getItemInOffHand();
        }
        return player.getInventory().getItemInMainHand();
    }

    private void setItemInHand(Player player, EquipmentSlot hand, ItemStack item) {
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(item);
        } else {
            player.getInventory().setItemInMainHand(item);
        }
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
                player.sendMessage(MessageUtil.warn("Clear an inventory slot so the Wand of Mother Nature can return."));
            }
            return;
        }

        ItemStack stick = createMotherNatureStickItem();
        if (plugin.getItemAuditManager() != null) {
            plugin.getItemAuditManager().recordKnownAcquisition(
                player,
                stick,
                restored ? "power_item_restore" : "power_item_grant",
                restored ? "Restored Wand of Mother Nature." : "Granted Wand of Mother Nature."
            );
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stick);
        if (!leftovers.isEmpty()) {
            pendingFloristStickReturns.put(player.getUniqueId(), 1);
        }
        if (restored) {
            player.sendMessage(MessageUtil.info("The Wand of Mother Nature returned to you."));
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
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (refreshPowerItem(item)) {
                player.getInventory().setItem(slot, item);
            }
        }

        ItemStack cursor = player.getItemOnCursor();
        if (refreshPowerItem(cursor)) {
            player.setItemOnCursor(cursor);
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
            if (InventoryRecipeUtil.isPlainMaterial(plugin, item, Material.TOTEM_OF_UNDYING)) {
                totems += item.getAmount();
                continue;
            }
            if (InventoryRecipeUtil.isPlainMaterial(plugin, item, Material.NETHER_STAR)) {
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

    public boolean isAncientScrollCraft(ItemStack[] matrix) {
        return matchesAncientScrollRecipe(matrix);
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
            if (InventoryRecipeUtil.isPlainMaterial(plugin, item, Material.NETHER_STAR) && stars > 0) {
                int take = Math.min(stars, item.getAmount());
                stars -= take;
                next[i] = reduceItem(item, take);
                continue;
            }
            if (InventoryRecipeUtil.isPlainMaterial(plugin, item, Material.TOTEM_OF_UNDYING) && totems > 0) {
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
        if (!isAllowedResultClick(event.getClick())) {
            player.sendMessage(MessageUtil.warn("Use a normal click or shift-click to craft this item."));
            return false;
        }
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

    private boolean isAllowedResultClick(ClickType click) {
        return click == ClickType.LEFT
            || click == ClickType.RIGHT
            || click == ClickType.SHIFT_LEFT
            || click == ClickType.SHIFT_RIGHT;
    }

    private boolean isCraftResultSlot(InventoryClickEvent event) {
        return event.getView().getTopInventory() instanceof CraftingInventory
            && (event.getClickedInventory() == event.getView().getTopInventory() || event.getRawSlot() == 0)
            && (event.getSlotType() == InventoryType.SlotType.RESULT || event.getRawSlot() == 0);
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
            if (isFrameSlot(slot, inventory.getSize())) {
                inventory.setItem(slot, fillerPane());
            }
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

    private void fillPowerChoiceMenu(Player viewer, Inventory inventory, int page) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isFrameSlot(slot, inventory.getSize())) {
                inventory.setItem(slot, fillerPane());
            }
        }

        List<SuperpowerType> choices = selectablePowerChoices();
        int safePage = clampPowerChoicePage(page, choices.size());
        int totalPages = maxPowerChoicePage(choices.size()) + 1;
        int start = safePage * POWER_CHOICE_SLOTS.length;
        inventory.setItem(4, createPowerChoiceHeaderIcon(viewer, safePage, totalPages));

        for (int i = 0; i < POWER_CHOICE_SLOTS.length; i++) {
            int choiceIndex = start + i;
            if (choiceIndex >= choices.size()) {
                break;
            }
            inventory.setItem(POWER_CHOICE_SLOTS[i], createPowerChoiceIcon(choices.get(choiceIndex)));
        }
        if (safePage > 0) {
            inventory.setItem(POWER_CHOICE_PREVIOUS_SLOT, simpleMenuItem(
                Material.ARROW,
                "<yellow>Previous Page</yellow>",
                List.of("<gray>View earlier classes.</gray>")
            ));
        }
        if (safePage + 1 < totalPages) {
            inventory.setItem(POWER_CHOICE_NEXT_SLOT, simpleMenuItem(
                Material.ARROW,
                "<yellow>Next Page</yellow>",
                List.of("<gray>View more classes.</gray>")
            ));
        }
        inventory.setItem(POWER_CHOICE_CANCEL_SLOT, simpleMenuItem(Material.BARRIER, "<red>Cancel</red>", List.of("<gray>Keep the awakened scroll for later.</gray>")));
    }

    private List<SuperpowerType> selectablePowerChoices() {
        List<SuperpowerType> choices = new ArrayList<>();
        for (SuperpowerType type : SuperpowerType.values()) {
            if (type != SuperpowerType.MORTAL) {
                choices.add(type);
            }
        }
        choices.sort(Comparator
            .comparingDouble((SuperpowerType type) -> displayChance(type))
            .reversed()
            .thenComparing(SuperpowerType::displayName));
        return choices;
    }

    private int clampPowerChoicePage(int requestedPage) {
        return clampPowerChoicePage(requestedPage, selectablePowerChoices().size());
    }

    private int clampPowerChoicePage(int requestedPage, int choiceCount) {
        int maxPage = maxPowerChoicePage(choiceCount);
        return Math.max(0, Math.min(maxPage, requestedPage));
    }

    private int maxPowerChoicePage(int choiceCount) {
        return Math.max(0, (Math.max(0, choiceCount) - 1) / POWER_CHOICE_SLOTS.length);
    }

    private SuperpowerType powerChoiceBySlot(int slot, int page) {
        List<SuperpowerType> choices = selectablePowerChoices();
        int offset = clampPowerChoicePage(page, choices.size()) * POWER_CHOICE_SLOTS.length;
        for (int i = 0; i < POWER_CHOICE_SLOTS.length; i++) {
            if (POWER_CHOICE_SLOTS[i] != slot) {
                continue;
            }
            int index = offset + i;
            return index < choices.size() ? choices.get(index) : null;
        }
        return null;
    }

    private ItemStack fillerPane() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(MenuItemUtil.visibleName(Component.empty()));
            meta.lore(MenuItemUtil.visibleLore(Component.empty(), List.of()));
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private boolean isFrameSlot(int slot, int size) {
        return slot < 9 || slot >= size - 9 || slot % 9 == 0 || slot % 9 == 8;
    }

    private ItemStack simpleMenuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(MM.deserialize(MenuItemUtil.visibleMiniName(name)));
        meta.lore(MenuItemUtil.visibleMiniLore(name, lore).stream().map(MM::deserialize).toList());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPowerChoiceHeaderIcon(Player viewer, int page, int totalPages) {
        SuperpowerType currentPower = powerOf(viewer);
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(MM.deserialize("<gradient:#ff4d6d:#7c3aed><bold>Awakened Ancient Scroll</bold></gradient>"));
        meta.lore(CustomLoreUtil.wrapLoreLines(List.of(
            MM.deserialize("<gray>Choose the class you want to receive.</gray>"),
            MM.deserialize("<gray>The scroll is consumed only after you click a class.</gray>"),
            Component.empty(),
            MM.deserialize("<gray>Current Class: <white>" + (currentPower == null ? "Unknown" : currentPower.displayName()) + "</white></gray>"),
            MM.deserialize("<gray>Page: <white>" + (page + 1) + "/" + Math.max(1, totalPages) + "</white></gray>")
        )));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPowerInfoIcon(SuperpowerType type) {
        ItemStack item = new ItemStack(type.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(MM.deserialize(powerTitleTag(type)));
        meta.lore(powerInfoLore(type));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPowerChoiceIcon(SuperpowerType type) {
        ItemStack item = createPowerInfoIcon(type);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        List<Component> lore = meta.hasLore() && meta.lore() != null
            ? new ArrayList<>(meta.lore())
            : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(MM.deserialize("<green>Click to choose this class.</green>"));
        meta.lore(lore);
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
        meta.displayName(MM.deserialize("<aqua><bold>Your Class</bold></aqua>"));
        meta.lore(List.of(
            MM.deserialize("<gray>Current Class: <white>" + powerName + "</white></gray>"),
            Component.empty(),
            MM.deserialize("<gray>The menu below shows every possible class and its effects.</gray>")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> powerInfoLore(SuperpowerType type) {
        List<Component> lore = new ArrayList<>();
        String rarityColor = powerRarityColorName(type);
        lore.add(MM.deserialize("<gray>Tier: <" + rarityColor + "><bold>" + powerRarityName(type) + "</bold></" + rarityColor + "></gray>"));
        lore.add(MM.deserialize("<gray>Chance: <" + rarityColor + "><bold><italic>" + formatPercent(displayChance(type)) + "</italic></bold></" + rarityColor + "></gray>"));
        lore.add(Component.empty());
        switch (type) {
            case HONORED_ONE -> {
                lore.add(MM.deserialize("<gray><white>/infinity</white> stops non-boss hostile projectiles.</gray>"));
                lore.add(MM.deserialize("<gray><white>/domainexpansion</white> pulls nearby players into a <white>15s</white> sculk End domain.</gray>"));
                lore.add(MM.deserialize("<gray>Trapped players get heavy Nausea and cannot move.</gray>"));
                lore.add(MM.deserialize("<gray>Your domain swings hit every trapped player.</gray>"));
                lore.add(MM.deserialize("<gray>Teammates take <white>25%</white> more domain damage.</gray>"));
                lore.add(MM.deserialize("<gray>Cooldown: <white>10m</white>. Sealed during boss fights.</gray>"));
                lore.add(MM.deserialize("<gray>Choosing it from an awakened scroll kills you once.</gray>"));
            }
            case VEIL_ASSASSIN -> {
                lore.add(MM.deserialize("<gray>Has only <white>8 hearts</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Cannot wear <white>netherite armor</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Crouch for <white>5s</white> to enter the veil.</gray>"));
                lore.add(MM.deserialize("<gray>In the veil, crouching moves at full speed with Speed IV.</gray>"));
                lore.add(MM.deserialize("<gray>Your body, gear, name, and ally marker are fully hidden.</gray>"));
                lore.add(MM.deserialize("<gray>Backstabs: <white>90% current HP</white>; bosses: <white>+20%</white>.</gray>"));
                lore.add(MM.deserialize("<gray><white>/smokebomb</white> blinds nearby players and fully hides you for <white>5s</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Smoke Bomb cooldown: <white>60s</white>.</gray>"));
            }
            case ARCANIST -> {
                lore.add(MM.deserialize("<gray>All XP gains are multiplied by <white>5x</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Enchanting no longer needs <white>lapis</white>.</gray>"));
                lore.add(MM.deserialize("<gray><white>/arcanebook</white> upgrades one held enchanted book to max level.</gray>"));
                lore.add(MM.deserialize("<gray>Book upgrade cooldown: <white>5 hours</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Enchanted gear has a <white>25%</white> chance to ignore durability loss.</gray>"));
                lore.add(MM.deserialize("<gray>Held enchant levels grant speed and attack speed.</gray>"));
                lore.add(MM.deserialize("<gray>Also grants <white>+2 Luck</white>.</gray>"));
            }
            case BERSERKER -> {
                lore.add(MM.deserialize("<gray>Permanent <white>Strength II</white>.</gray>"));
                lore.add(MM.deserialize("<gray>At <white>3 hearts or less</white>, gain <white>Speed II</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Also gains <white>Regeneration I</white> while low.</gray>"));
            }
            case JUGGERNAUT -> {
                lore.add(MM.deserialize("<gray>Permanent <white>two rows of health</white>.</gray>"));
                lore.add(MM.deserialize("<gray>While crouching, incoming knockback is nearly ignored.</gray>"));
                lore.add(MM.deserialize("<gray><white>/unstoppableforce</white> lets you sprint through breakable walls for <white>20s</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Fall impacts trigger an area slam and deal <white>80%</white> less fall damage to you.</gray>"));
                lore.add(MM.deserialize("<gray>Unstoppable Force cooldown: <white>5 minutes</white>.</gray>"));
            }
            case MORTAL -> {
                lore.add(MM.deserialize("<gray>No passive ability.</gray>"));
                lore.add(MM.deserialize("<gray>An <white>Ancient Scroll</white> can reroll any current fate.</gray>"));
            }
            case WAYFARER -> {
                lore.add(MM.deserialize("<gray>Permanent <white>Speed II</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Creates paired portals to chosen coordinates.</gray>"));
                lore.add(MM.deserialize("<gray>Players and mobs can pass through them.</gray>"));
                lore.add(MM.deserialize("<gray>Works across main dimensions, but not during boss fights.</gray>"));
                lore.add(MM.deserialize("<gray>Portals fade after <white>" + TRAVEL_PORTAL_DURATION_SECONDS + "s</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Command: <white>/travel [x] [y] [z] [dimension]</white></gray>"));
                lore.add(MM.deserialize("<gray>Close it early with <white>/travel close</white>.</gray>"));
            }
            case VERDANT -> {
                lore.add(MM.deserialize("<gray>Boosts nearby crop growth and doubles crops and wood.</gray>"));
                lore.add(MM.deserialize("<gray>Regenerates health outdoors.</gray>"));
                lore.add(MM.deserialize("<gray>Spam-crouching near crops surges their growth.</gray>"));
                lore.add(MM.deserialize("<gray>Grants the <white>Wand of Mother Nature</white>.</gray>"));
            }
            case DRUID -> {
                lore.add(MM.deserialize("<gray>Receives a bound <white>Druid's Grimoire</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Choose one blessing for yourself and nearby teammates within <white>" + DRUID_BUFF_RADIUS + " blocks</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Can grant the positive potion blessings in the grimoire.</gray>"));
                lore.add(MM.deserialize("<gray>Non-instant blessings last <white>" + (DRUID_BUFF_DURATION_SECONDS / 60) + " minutes</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Only one blessing can be chosen each use.</gray>"));
                lore.add(MM.deserialize("<gray>Cooldown: <white>" + DRUID_BUFF_COOLDOWN_SECONDS + "s</white>.</gray>"));
            }
            case MONARCH -> {
                lore.add(MM.deserialize("<gray>Stores slain hostile mobs for later battle.</gray>"));
                lore.add(MM.deserialize("<gray>Summons have <white>40 HP</white>, armor, resistance, and strong damage.</gray>"));
                lore.add(MM.deserialize("<gray>They persist during normal gameplay and guard against enemies of your team.</gray>"));
                lore.add(MM.deserialize("<gray>They auto-prioritize enemy players, then hostile mobs.</gray>"));
                lore.add(MM.deserialize("<gray>Bosses resist <white>70%</white> of their damage.</gray>"));
                lore.add(MM.deserialize("<gray>Undead mobs refuse to target the Shadow Monarch.</gray>"));
                lore.add(MM.deserialize("<gray>Storage limit: <white>" + MONARCH_STORAGE_LIMIT + "</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Command: <white>/msummon [amount]</white> or <white>/msummon despawn</white></gray>"));
            }
            case NIGHTSHADE -> {
                lore.add(MM.deserialize("<gray>Toggle invisibility for <white>15 minutes</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Night vision can be toggled with <white>/nightshadevision</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Hits poison enemies briefly.</gray>"));
                lore.add(MM.deserialize("<gray>While hidden, gain <white>Speed III</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Hit cooldown: <white>7 minutes</white>. Normal cooldown: <white>5 minutes</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Command: <white>/shadow toggle</white></gray>"));
            }
            case THE_WORLD -> {
                lore.add(MM.deserialize("<gray>Receives a bound <white>World Clock</white>.</gray>"));
                lore.add(MM.deserialize("<gray><white>Right-click</white> to stop time in a <white>10 block</white> radius.</gray>"));
                lore.add(MM.deserialize("<gray>Freezes mobs, players, projectiles, redstone, and block updates for <white>5s</white>.</gray>"));
                lore.add(MM.deserialize("<gray>You stay active. Bosses resist it. Cooldown: <white>5m</white>.</gray>"));
            }
            case ORACLE_EYE -> {
                lore.add(MM.deserialize("<gray>Highlights ores, players, and mobs through walls for <white>2.5 minutes</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Quietly warns you when diamonds or ancient debris are within <white>3 blocks</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Cooldown: <white>6 minutes</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Command: <white>/xray</white></gray>"));
            }
            case PROSPECTOR -> {
                lore.add(MM.deserialize("<gray>Permanent <white>Haste III</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Grants <white>+2 health</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Gains Night Vision under Y <white>" + MINER_NIGHT_VISION_Y_LEVEL + "</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Mining ores has a <white>25%</white> chance to duplicate the drop.</gray>"));
            }
            case TITAN -> {
                lore.add(MM.deserialize("<gray>Scaled to <white>1.2x</white> normal size.</gray>"));
                lore.add(MM.deserialize("<gray>Grants <white>+6 hearts</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Heavy blows gain <white>+2 attack damage</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Reduces all incoming knockback by <white>40%</white>.</gray>"));
            }
            case SKYBOUND -> {
                lore.add(MM.deserialize("<gray>Permanent <white>Strength II</white> and <white>Speed I</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Grants <white>an extra row of hearts</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Double-tap jump to fly for <white>30s</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Sneak to boost. Bosses ground flight. Cooldown: <white>5m</white>.</gray>"));
            }
            case TIDEBORN -> {
                lore.add(MM.deserialize("<gray>Can live underwater with <white>no drowning</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Air is constantly restored and Water Breathing is always active.</gray>"));
                lore.add(MM.deserialize("<gray>Breaks blocks at normal speed while submerged.</gray>"));
                lore.add(MM.deserialize("<gray>While in water, gains Conduit Power, Dolphin's Grace, Haste II, Speed II, Strength I, Resistance I, and Regeneration I.</gray>"));
                lore.add(MM.deserialize("<gray>Water-empowered hits deal <white>25%</white> more damage.</gray>"));
                lore.add(MM.deserialize("<gray>While water-empowered, incoming damage is reduced by <white>25%</white> and attackers are slowed.</gray>"));
            }
            case ASHEN_SOUL -> {
                lore.add(MM.deserialize("<gray>Permanent <white>Fire Resistance</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Low health grants <white>Speed I</white> and <white>Regeneration I</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Hits ignite enemies and burning targets take bonus damage.</gray>"));
                lore.add(MM.deserialize("<gray>Enemies that damage you are scorched while you gain brief Absorption.</gray>"));
                lore.add(MM.deserialize("<gray><white>Rebirth</white> survives lethal hits, except failed boss mechanics.</gray>"));
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
                lore.add(MM.deserialize("<gray>Endermen ignore you. Boss fights seal Voidstep.</gray>"));
                lore.add(MM.deserialize("<gray>Cooldown: <white>" + VOIDSTEP_COOLDOWN_SECONDS + "s</white>.</gray>"));
            }
            case SENTINEL -> {
                lore.add(MM.deserialize("<gray>Grants <white>+3 hearts</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Sneak to project a <white>" + (int) SENTINEL_AURA_RADIUS + " block</white> guard aura.</gray>"));
                lore.add(MM.deserialize("<gray>You and nearby teammates gain <white>Resistance I</white> and Absorption.</gray>"));
                lore.add(MM.deserialize("<gray>While braced, incoming damage is reduced and attackers are weakened.</gray>"));
                lore.add(MM.deserialize("<gray>The caster is slowed while bracing the aura.</gray>"));
            }
            case FROSTBORN -> {
                lore.add(MM.deserialize("<gray>Permanent <white>Water Breathing</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Immune to freezing damage.</gray>"));
                lore.add(MM.deserialize("<gray>Gain speed on snow or ice, stronger in cold biomes.</gray>"));
                lore.add(MM.deserialize("<gray>Cold and snowy biomes also grant <white>Strength I</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Damaging enemies chills them with <white>Slowness I</white> and <white>Weakness I</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Snowballs inflict <white>Weakness I</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Chilled targets take <white>+25%</white>; bosses resist it and take <white>+10%</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Enemies that damage you are chilled too.</gray>"));
                lore.add(MM.deserialize("<gray>Chill duration: <white>" + FROSTBORN_CHILL_SECONDS + "s</white>.</gray>"));
            }
            case DEADEYE -> {
                lore.add(MM.deserialize("<gray>Permanent <white>Speed I</white> and <white>Night Vision</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Can preserve any arrow type if at least one is in your inventory.</gray>"));
                lore.add(MM.deserialize("<gray>Toggle arrow preservation with <white>/deadeyearrows</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Projectile hits deal <white>25%</white> more damage.</gray>"));
                lore.add(MM.deserialize("<gray>Sneak while shooting arrows/tridents to fire a faster marked shot.</gray>"));
                lore.add(MM.deserialize("<gray>Marked shots: <white>+45%</white> and slow. Boss shots: <white>+15/25%</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Targets hit by projectiles glow for <white>" + DEADEYE_GLOW_SECONDS + "s</white>.</gray>"));
            }
            case RIFTWARDEN -> {
                lore.add(MM.deserialize("<gray>Built for boss fights and dangerous mobs.</gray>"));
                lore.add(MM.deserialize("<gray>Deals <white>15%</white> more damage to custom bosses.</gray>"));
                lore.add(MM.deserialize("<gray>Deals <white>12%</white> more damage to hostile mobs.</gray>"));
                lore.add(MM.deserialize("<gray>Reduces boss damage by <white>15%</white> and mob damage by <white>18%</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Gains Slow Falling only near custom bosses.</gray>"));
                lore.add(MM.deserialize("<gray>Gains Resistance near custom bosses or while in the End.</gray>"));
            }
            case OATHBOUND -> {
                lore.add(MM.deserialize("<gray>Wakes up around nearby teammates.</gray>"));
                lore.add(MM.deserialize("<gray>Even alone, keeps <white>Speed II</white>.</gray>"));
                lore.add(MM.deserialize("<gray>You and teammates within <white>" + (int) OATHBOUND_AURA_RADIUS + " blocks</white> gain Speed II and Resistance I.</gray>"));
                lore.add(MM.deserialize("<gray>With nearby teammates, the caster also gains Strength I and Absorption II.</gray>"));
                lore.add(MM.deserialize("<gray><white>/oathsummon [player]</white> calls an ally outside boss fights.</gray>"));
                lore.add(MM.deserialize("<gray>The team aura only counts real team members.</gray>"));
            }
            case RUNESMITH -> {
                lore.add(MM.deserialize("<gray>Permanent <white>Haste II</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Gear has a <white>20%</white> chance to ignore durability loss.</gray>"));
                lore.add(MM.deserialize("<gray>Custom, legendary, and season gear preserve durability at <white>35%</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Killing a custom boss repairs held gear and armor by <white>15%</white> durability.</gray>"));
            }
            case GRAVEBORN -> {
                lore.add(MM.deserialize("<gray>Immune to poison and wither damage.</gray>"));
                lore.add(MM.deserialize("<gray>Undead mobs refuse to target the Graveborn.</gray>"));
                lore.add(MM.deserialize("<gray>Deals extra damage to undead and takes less from them.</gray>"));
                lore.add(MM.deserialize("<gray>Nearby undead can save a lethal hit, except failed boss mechanics.</gray>"));
                lore.add(MM.deserialize("<gray>Nearby player deaths grant temporary combat buffs and absorption.</gray>"));
                lore.add(MM.deserialize("<gray>Kills restore health, with player kills restoring more.</gray>"));
                lore.add(MM.deserialize("<gray>Permanent Night Vision keeps caves readable without a command.</gray>"));
            }
            case STORMCALLER -> {
                lore.add(MM.deserialize("<gray>Permanent <white>Speed II</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Rain or thunder grants <white>Haste II</white> and <white>Strength I</white>.</gray>"));
                lore.add(MM.deserialize("<gray>Hits can call a visual lightning strike for bonus damage.</gray>"));
                lore.add(MM.deserialize("<gray>Axes and maces in PvP grant brief Strength and Resistance.</gray>"));
                lore.add(MM.deserialize("<gray>Lightning chance is higher during storms.</gray>"));
                lore.add(MM.deserialize("<gray>Command: <white>/stormcaller on|off</white></gray>"));
            }
            case BLOODMENDER -> {
                lore.add(MM.deserialize("<gray>Damaging enemies heals a portion of the damage dealt.</gray>"));
                lore.add(MM.deserialize("<gray>Healing is stronger against mobs and capped per hit.</gray>"));
                lore.add(MM.deserialize("<gray><white>/bloodsacrifice</white> spends half your health to fully heal allies.</gray>"));
                lore.add(MM.deserialize("<gray>Healing seals cancel it without taking your health.</gray>"));
                lore.add(MM.deserialize("<gray><white>/curse</white> curses one nearby enemy armor piece with Vanishing.</gray>"));
                lore.add(MM.deserialize("<gray>Kills pair well with boss waves and PvP cleanup without making the player immortal.</gray>"));
                lore.add(MM.deserialize("<gray>Low health grants brief Regeneration I.</gray>"));
            }
        }
        lore.add(Component.empty());
        lore.add(MM.deserialize("<dark_gray><italic>Numbers shown here are live season values.</italic></dark_gray>"));
        return lore;
    }

    private String powerTitleTag(SuperpowerType type) {
        String color = powerRarityColorName(type);
        return "<" + color + "><bold>" + type.displayName() + "</bold></" + color + ">";
    }

    private String powerRarityName(SuperpowerType type) {
        double chance = displayChance(type);
        if (chance <= 0.001) {
            return "Secret";
        }
        if (chance <= 0.025) {
            return "Mythic";
        }
        if (chance <= 0.04) {
            return "Legendary";
        }
        if (chance <= 0.055) {
            return "Epic";
        }
        if (chance <= 0.075) {
            return "Rare";
        }
        return "Common";
    }

    private String powerRarityColorName(SuperpowerType type) {
        return switch (powerRarityName(type)) {
            case "Secret" -> "dark_purple";
            case "Mythic" -> "light_purple";
            case "Legendary" -> "gold";
            case "Epic" -> "aqua";
            case "Rare" -> "green";
            default -> "gray";
        };
    }

    private void applyAncientScrollState(ItemMeta meta) {
        meta.getPersistentDataContainer().set(keyAncientScroll, PersistentDataType.STRING, ANCIENT_SCROLL_ITEM_ID);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        CustomLoreUtil.applyStyledItemFlags(meta);
    }

    private void applyAncientScrollPresentation(ItemMeta meta) {
        boolean awakened = isAwakenedAncientScroll(meta);
        CustomLoreUtil.Rarity rarity = awakened ? CustomLoreUtil.Rarity.MYTHIC : CustomLoreUtil.Rarity.EPIC;
        String name = awakened ? "Awakened Ancient Scroll" : "Ancient Scroll";
        meta.displayName(CustomLoreUtil.displayName(rarity, name));
        ItemModelUtil.apply(meta, awakened ? "awakened_ancient_scroll" : ANCIENT_SCROLL_ITEM_ID);
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.PAPER,
            rarity.label(),
            "SCROLL",
            List.of(awakened
                ? "<gray>Use or tap to choose your next class.</gray>"
                : "<gray>Use or tap to reroll your current class.</gray>"),
            List.of(CustomLoreUtil.section(
                "Use",
                awakened ? "Class Choice" : "Class Rewrite",
                awakened
                    ? "<gray>Opens a menu where you choose any non-Mortal class.</gray>"
                    : "<gray>Works even if you already have a class.</gray>",
                awakened
                    ? "<gray>Consumes the scroll only after a new class is selected.</gray>"
                    : "<gray>Consumes the scroll and rerolls you into a random new class.</gray>"
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
        ItemModelUtil.apply(meta, WARDEN_HEART_ITEM_ID);
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
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.EPIC, "Wand of Mother Nature"));
        ItemModelUtil.apply(meta, MOTHER_NATURE_STICK_ITEM_ID);
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.STICK,
            CustomLoreUtil.Rarity.EPIC.label(),
            "NATURE",
            List.of("<gray>Bound to the Verdant.</gray>"),
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
        ItemModelUtil.apply(meta, THE_WORLD_CLOCK_ITEM_ID);
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
        ItemModelUtil.apply(meta, DRUID_GRIMOIRE_ITEM_ID);
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
            if (excludeHuman && type == SuperpowerType.MORTAL) {
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
            && hasPower(player, SuperpowerType.JUGGERNAUT)
            && !player.isDead()
            && player.getGameMode() != GameMode.SPECTATOR;

        if (active) {
            if (existing == null) {
                attribute.addTransientModifier(new AttributeModifier(
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
        if (!player.isOnline() || !hasPower(player, SuperpowerType.ARCANIST)) {
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
        return awakening != null && isAncientScroll(item) && awakening.isAwakened(item);
    }

    private boolean isAwakenedAncientScroll(ItemMeta meta) {
        AwakeningTableListener awakening = plugin.getAwakeningTableListener();
        return awakening != null && awakening.isAwakened(meta);
    }

    private record TimeStopState(UUID ownerId, Location center, long expiresAt) {}
    private record FrozenMobState(boolean hadAi, boolean hadGravity) {}
    private record FrozenProjectileState(Location location, Vector velocity, boolean hadGravity) {}
    private record HonoredDomainState(
        UUID ownerId,
        Location domainCenter,
        long expiresAt,
        Map<UUID, Location> returnLocations,
        Set<UUID> participantIds,
        List<BlockState> restoreBlocks
    ) {}
    private record HonoredFrozenProjectileState(UUID ownerId, Location location, Vector velocity, boolean hadGravity, long expiresAt) {}
    private record ArmorCurseTarget(Player target, EquipmentSlot slot, ItemStack item) {}
    private record PowerInfoHolder() implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record PowerChoiceHolder(UUID ownerId, EquipmentSlot hand, int page) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record DruidGrimoireHolder(UUID ownerId) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private enum DruidBlessing {
        STRENGTH(10, Material.IRON_SWORD, "<red><bold>Strength</bold></red>", "<gray>Grants <white>Strength I</white>.</gray>", PotionEffectType.STRENGTH, 0, false, Particle.CRIT, Sound.ENTITY_PLAYER_ATTACK_STRONG),
        SPEED(11, Material.SUGAR, "<aqua><bold>Speed</bold></aqua>", "<gray>Grants <white>Speed I</white>.</gray>", PotionEffectType.SPEED, 0, false, Particle.CLOUD, Sound.ENTITY_BREEZE_SLIDE),
        HASTE(12, Material.GOLDEN_PICKAXE, "<yellow><bold>Haste</bold></yellow>", "<gray>Grants <white>Haste I</white>.</gray>", PotionEffectType.HASTE, 0, false, Particle.ELECTRIC_SPARK, Sound.BLOCK_BEACON_POWER_SELECT),
        JUMP_BOOST(13, Material.RABBIT_FOOT, "<green><bold>Jump Boost</bold></green>", "<gray>Grants <white>Jump Boost I</white>.</gray>", PotionEffectType.JUMP_BOOST, 0, false, Particle.HAPPY_VILLAGER, Sound.ENTITY_RABBIT_JUMP),
        REGENERATION(14, Material.GLISTERING_MELON_SLICE, "<light_purple><bold>Regeneration</bold></light_purple>", "<gray>Grants <white>Regeneration I</white>.</gray>", PotionEffectType.REGENERATION, 0, false, Particle.HEART, Sound.BLOCK_AMETHYST_BLOCK_CHIME),
        RESISTANCE(15, Material.SHIELD, "<gray><bold>Resistance</bold></gray>", "<gray>Grants <white>Resistance I</white>.</gray>", PotionEffectType.RESISTANCE, 0, false, Particle.ANGRY_VILLAGER, Sound.ITEM_ARMOR_EQUIP_NETHERITE),
        FIRE_RESISTANCE(16, Material.MAGMA_CREAM, "<gold><bold>Fire Resistance</bold></gold>", "<gray>Grants <white>Fire Resistance I</white>.</gray>", PotionEffectType.FIRE_RESISTANCE, 0, false, Particle.FLAME, Sound.ITEM_FIRECHARGE_USE),
        WATER_BREATHING(19, Material.PUFFERFISH, "<blue><bold>Water Breathing</bold></blue>", "<gray>Grants <white>Water Breathing I</white>.</gray>", PotionEffectType.WATER_BREATHING, 0, false, Particle.BUBBLE_POP, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE),
        NIGHT_VISION(20, Material.GOLDEN_CARROT, "<yellow><bold>Night Vision</bold></yellow>", "<gray>Grants <white>Night Vision I</white>.</gray>", PotionEffectType.NIGHT_VISION, 0, false, Particle.GLOW, Sound.BLOCK_AMETHYST_BLOCK_CHIME),
        ABSORPTION(21, Material.GOLDEN_APPLE, "<yellow><bold>Absorption</bold></yellow>", "<gray>Grants <white>Absorption I</white>.</gray>", PotionEffectType.ABSORPTION, 0, false, Particle.TOTEM_OF_UNDYING, Sound.BLOCK_BEACON_POWER_SELECT),
        HEALTH_BOOST(22, Material.APPLE, "<red><bold>Health Boost</bold></red>", "<gray>Grants <white>Health Boost I</white>.</gray>", PotionEffectType.HEALTH_BOOST, 0, false, Particle.HEART, Sound.ENTITY_PLAYER_LEVELUP),
        LUCK(23, Material.EMERALD, "<green><bold>Luck</bold></green>", "<gray>Grants <white>Luck I</white>.</gray>", PotionEffectType.LUCK, 0, false, Particle.HAPPY_VILLAGER, Sound.BLOCK_AMETHYST_BLOCK_CHIME),
        SLOW_FALLING(24, Material.PHANTOM_MEMBRANE, "<white><bold>Slow Falling</bold></white>", "<gray>Grants <white>Slow Falling I</white>.</gray>", PotionEffectType.SLOW_FALLING, 0, false, Particle.CLOUD, Sound.ENTITY_PHANTOM_FLAP),
        DOLPHINS_GRACE(25, Material.TROPICAL_FISH, "<aqua><bold>Dolphin's Grace</bold></aqua>", "<gray>Grants <white>Dolphin's Grace I</white>.</gray>", PotionEffectType.DOLPHINS_GRACE, 0, false, Particle.SPLASH, Sound.ENTITY_DOLPHIN_PLAY),
        CONDUIT_POWER(28, Material.HEART_OF_THE_SEA, "<aqua><bold>Conduit Power</bold></aqua>", "<gray>Grants <white>Conduit Power I</white>.</gray>", PotionEffectType.CONDUIT_POWER, 0, false, Particle.NAUTILUS, Sound.BLOCK_CONDUIT_ACTIVATE),
        SATURATION(29, Material.COOKED_BEEF, "<gold><bold>Saturation</bold></gold>", "<gray>Restores hunger quickly.</gray>", PotionEffectType.SATURATION, 0, true, Particle.HAPPY_VILLAGER, Sound.ENTITY_GENERIC_EAT),
        HERO_OF_THE_VILLAGE(30, Material.BELL, "<gold><bold>Hero of the Village</bold></gold>", "<gray>Grants <white>Hero of the Village I</white>.</gray>", PotionEffectType.HERO_OF_THE_VILLAGE, 0, false, Particle.TOTEM_OF_UNDYING, Sound.UI_TOAST_CHALLENGE_COMPLETE),
        INVISIBILITY(31, Material.GLASS_BOTTLE, "<gray><bold>Invisibility</bold></gray>", "<gray>Grants <white>Invisibility I</white>.</gray>", PotionEffectType.INVISIBILITY, 0, false, Particle.PORTAL, Sound.BLOCK_AMETHYST_BLOCK_RESONATE),
        INSTANT_HEALTH(32, Material.SPLASH_POTION, "<green><bold>Instant Health</bold></green>", "<gray>Restores a quick burst of health.</gray>", PotionEffectType.INSTANT_HEALTH, 0, true, Particle.HAPPY_VILLAGER, Sound.ENTITY_PLAYER_LEVELUP);

        private final int slot;
        private final Material icon;
        private final String display;
        private final String lore;
        private final PotionEffectType effectType;
        private final int amplifier;
        private final boolean instant;
        private final Particle particle;
        private final Sound sound;

        DruidBlessing(
            int slot,
            Material icon,
            String display,
            String lore,
            PotionEffectType effectType,
            int amplifier,
            boolean instant,
            Particle particle,
            Sound sound
        ) {
            this.slot = slot;
            this.icon = icon;
            this.display = display;
            this.lore = lore;
            this.effectType = effectType;
            this.amplifier = amplifier;
            this.instant = instant;
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
                case HASTE -> "Haste";
                case JUMP_BOOST -> "Jump Boost";
                case REGENERATION -> "Regeneration";
                case RESISTANCE -> "Resistance";
                case FIRE_RESISTANCE -> "Fire Resistance";
                case WATER_BREATHING -> "Water Breathing";
                case NIGHT_VISION -> "Night Vision";
                case ABSORPTION -> "Absorption";
                case HEALTH_BOOST -> "Health Boost";
                case LUCK -> "Luck";
                case SLOW_FALLING -> "Slow Falling";
                case DOLPHINS_GRACE -> "Dolphin's Grace";
                case CONDUIT_POWER -> "Conduit Power";
                case SATURATION -> "Saturation";
                case HERO_OF_THE_VILLAGE -> "Hero of the Village";
                case INVISIBILITY -> "Invisibility";
                case INSTANT_HEALTH -> "Instant Health";
            };
        }

        String durationText() {
            return instant ? "Instant" : (DRUID_BUFF_DURATION_SECONDS + "s");
        }

        void apply(SuperpowerManager manager, Player target) {
            if (instant) {
                target.addPotionEffect(new PotionEffect(effectType, 1, amplifier, true, true, true));
                return;
            }
            manager.applyPotion(target, effectType, DRUID_BUFF_DURATION_SECONDS * 20, amplifier);
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

    private static final class PortalPair {
        private final UUID ownerId;
        private final Location source;
        private final Location target;
        private final long expiresAt;
        private Location safeSource;
        private Location safeTarget;

        private PortalPair(
            UUID ownerId,
            Location source,
            Location target,
            Location safeSource,
            Location safeTarget,
            long expiresAt
        ) {
            this.ownerId = ownerId;
            this.source = source.clone();
            this.target = target.clone();
            this.safeSource = safeSource == null ? null : safeSource.clone();
            this.safeTarget = safeTarget == null ? null : safeTarget.clone();
            this.expiresAt = expiresAt;
        }

        private UUID ownerId() {
            return ownerId;
        }

        private Location source() {
            return source;
        }

        private Location target() {
            return target;
        }

        private long expiresAt() {
            return expiresAt;
        }

        private Location cachedDestination(boolean targetDestination) {
            return targetDestination ? safeTarget : safeSource;
        }

        private void cacheDestination(boolean targetDestination, Location destination) {
            if (targetDestination) {
                safeTarget = destination == null ? null : destination.clone();
            } else {
                safeSource = destination == null ? null : destination.clone();
            }
        }
    }
}
