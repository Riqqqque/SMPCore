package me.rique.smpcore.legendary;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.awakening.AwakeningTableListener;
import me.rique.smpcore.boss.BossManager;
import me.rique.smpcore.combat.DamageNumberListener;
import me.rique.smpcore.combat.CombatLogListener;
import me.rique.smpcore.command.MainMenuCommand;
import me.rique.smpcore.item.CustomToolListener;
import me.rique.smpcore.item.SalvagingDepotListener;
import me.rique.smpcore.item.AgriculturalPylonListener;
import me.rique.smpcore.item.SustenanceTalismanListener;
import me.rique.smpcore.item.XpLecternListener;
import me.rique.smpcore.power.SuperpowerManager;
import me.rique.smpcore.season.SeasonRelicManager;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.InventoryRecipeUtil;
import me.rique.smpcore.util.MessageUtil;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Input;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WindCharge;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public final class LegendaryListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String BACKPACK_RECIPE_ID = "backpack";
    private static final String EXPANDED_BACKPACK_RECIPE_ID = "expanded_backpack";
    private static final String MYTHIC_NEXUS_MENU_ID = "mythic_nexus";
    private static final String AWAKENING_TABLE_INFO_ID = "awakening_table_info";
    private static final String RELIQUARY_SECTION_PREFIX = "reliquary_section:";
    private static final int[] RELIQUARY_CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
    private static final int[] MYTHIC_FUSION_MENU_SLOTS = RELIQUARY_CONTENT_SLOTS;

    private static final int LEGENDARY_ITEM_DATA_VERSION = 21;
    private static final double ORB_OF_THE_MYSTICS_DROP_CHANCE = 0.10;
    private static final long ORB_OF_THE_MYSTICS_COOLDOWN_MS = 60L * 60L * 1000L;
    private static final int STARTUP_LEGENDARY_MIGRATION_CHUNKS_PER_TICK = 24;
    private static final int LEGENDARY_ITEM_SCAN_MAX_DEPTH = 2;
    private static final long LEGENDARY_DUPLICATE_AUDIT_INTERVAL_TICKS = 20L * 15L;
    private static final int ENDERBOW_TP_COOLDOWN = 30;
    private static final double ENDER_SWORD_MELEE_DAMAGE = 10.0;
    private static final int CHRONO_READY_SECONDS = 7;
    private static final int CHRONO_COOLDOWN = 45;
    private static final int CHRONO_COOLDOWN_DEATH = 90;
    private static final int HARPOON_COOLDOWN = 22;
    private static final int HYPNOSIS_COOLDOWN = 5;
    private static final int HYPNOSIS_MAX = 10;
    private static final int EMERALD_BLADE_MAX_LEVEL = 20;
    private static final double WARDEN_BLADE_MELEE_DAMAGE = 8.0;
    private static final int WARDEN_BLADE_PROTECTION_SECONDS = 3 * 60;
    private static final int WARDEN_BLADE_PROTECTION_COOLDOWN = 420;
    private static final int WARDEN_BLADE_SOUND_WAVE_COOLDOWN = 40;
    private static final double WARDEN_BLADE_SOUND_WAVE_DAMAGE = 9.0;
    private static final double WARDEN_BLADE_SOUND_WAVE_RANGE = 18.0;
    private static final double WARDEN_BLADE_SOUND_WAVE_HITBOX = 0.65;
    private static final double FROST_SCYTHE_MELEE_DAMAGE = 5.0;
    private static final int FROST_SCYTHE_NAUSEA_TICKS = 2 * 20;
    private static final int FROST_SCYTHE_FREEZE_TICKS = 2 * 20;
    private static final double FROST_SCYTHE_FREEZE_RADIUS = 6.0;
    private static final double FROST_SCYTHE_SWEEP_RANGE = 5.5;
    private static final double FROST_SCYTHE_SWEEP_DOT = 0.15;
    private static final double RHITTA_MELEE_DAMAGE = 7.0;
    private static final int RHITTA_SUNS_BLESSING_SECONDS = 300;
    private static final int RHITTA_SUNS_BLESSING_COOLDOWN = 420;
    private static final int RHITTA_CRUEL_SUN_COOLDOWN = 320;
    private static final int RHITTA_CRUEL_SUN_FIRE_SECONDS = 5;
    private static final double RHITTA_CRUEL_SUN_RADIUS = 6.0;
    private static final double RHITTA_CRUEL_SUN_TICK_DAMAGE = 1.0;
    private static final double MIDAS_SWORD_MELEE_DAMAGE = 12.0;
    private static final int MIDAS_SWORD_BASE_SHARPNESS = 5;
    private static final int MIDAS_SWORD_MAX_SHARPNESS = 20;
    private static final double MIDAS_SWORD_HEALTH_BONUS = 16.0;
    private static final int MIDAS_SWORD_GOLD_NUGGETS = 18;
    private static final double REAPER_SCYTHE_MELEE_DAMAGE = 14.0;
    private static final int REAPER_SCYTHE_DRAIN_COOLDOWN = 30;
    private static final double REAPER_SCYTHE_DRAIN_FRACTION = 0.35;
    private static final int REAPER_SCYTHE_WITHER_SECONDS = 6;
    private static final double REAPER_SCYTHE_ABSORPTION_HEALTH = 8.0;
    private static final double SHADOW_BLADE_DAMAGE = 13.0;
    private static final double SHADOW_BLADE_MONARCH_DAMAGE = 16.0;
    private static final int SHADOW_BLADE_DURATION_SECONDS = 20;
    private static final int SHADOW_BLADE_SHADOW_DURATION_SECONDS = 45;
    private static final int SHADOW_BLADE_COOLDOWN = 20;
    private static final double SHADOW_BLADE_BASE_ATTACK_SPEED_BONUS = 0.2;
    private static final double SHADOW_BLADE_SHADOW_ATTACK_SPEED_BONUS = 1.4;
    private static final int HEADHUNTER_RAGE_MAX = 5;
    private static final int HEADHUNTER_BUFF_SECONDS = 15;
    private static final int HEADHUNTER_COOLDOWN = 120;
    private static final int GOD_CHESTPLATE_COOLDOWN = 240;
    private static final double GOD_CHESTPLATE_HEALTH_BONUS = 10.0;
    private static final int STRENGTH_SWORD_STAGE_TWO_KILLS = 2;
    private static final int STRENGTH_SWORD_STAGE_THREE_KILLS = 4;
    private static final int STRENGTH_SWORD_BEAM_COOLDOWN = 25;
    private static final double STRENGTH_SWORD_BEAM_TRUE_DAMAGE = 16.0;
    private static final int STRENGTH_SWORD_DOMAIN_COOLDOWN = 90;
    private static final int STRENGTH_SWORD_DOMAIN_SECONDS = 18;
    private static final double STRENGTH_SWORD_DOMAIN_RADIUS = 12.0;
    private static final double STRENGTH_SWORD_DOMAIN_HEALTH_BONUS = 16.0;
    private static final double STRENGTH_SWORD_DOMAIN_ATTACK_SPEED_SCALAR = 0.35;
    private static final int STRENGTH_SWORD_MAX_DURABILITY = 8192;
    private static final int STRENGTH_SWORD_REPAIR_XP_COST = 12;
    private static final int DASH_MACE_COOLDOWN = 15;
    private static final double DASH_MACE_HORIZONTAL = 2.10;
    private static final double DASH_MACE_VERTICAL = 0.52;
    private static final double STRENGTH_MACE_DAMAGE_BONUS = 9.0;
    private static final int LIFE_STEALER_MAX_STACKS = 3;
    private static final int LIFE_STEALER_MAX_SHARPNESS = 6;
    private static final int PERCY_TRIDENT_WATER_BREATHING_TICKS = 60;
    private static final int BLINK_DAGGER_RANGE_BLOCKS = 12;
    private static final int BLINK_DAGGER_COOLDOWN = 12;
    private static final int BLINK_DAGGER_BACKSTAB_COOLDOWN = 20;
    private static final int BLINK_DAGGER_STUN_SECONDS = 3;
    private static final double BLINK_DAGGER_BACKSTAB_DOT = -0.45;
    private static final byte PERCY_TRIDENT_MODE_THROW = 0;
    private static final byte PERCY_TRIDENT_MODE_RIPTIDE = 1;
    private static final double PERCY_TRIDENT_DAMAGE_BONUS = 3.0;
    private static final int MAGNET_RADIUS = 20;
    private static final int WIND_CHARGE_CANNON_MAX_CHARGES = 5;
    private static final int WIND_CHARGE_CANNON_RECHARGE = 15;
    private static final int EXECUTIONER_BLADE_STRENGTH_SECONDS = 6 * 60;
    private static final int EXECUTIONER_BLADE_STRENGTH_COOLDOWN = 8 * 60;
    private static final int EXECUTIONER_BLADE_STRENGTH_AMPLIFIER = 1;
    private static final int EXECUTIONER_BLADE_STUN_SECONDS = 3;
    private static final int EXECUTIONER_BLADE_SHOCKWAVE_COOLDOWN = 20;
    private static final double EXECUTIONER_BLADE_SHOCKWAVE_RADIUS = 6.0;
    private static final double EXECUTIONER_BLADE_SHOCKWAVE_HORIZONTAL = 0.65;
    private static final double EXECUTIONER_BLADE_SHOCKWAVE_VERTICAL = 0.80;
    private static final float ENDER_SWORD_DRAGON_YAW_OFFSET = 180.0f;
    private static final double ENDER_SWORD_DRAGON_RIDER_FORWARD_OFFSET = -0.10;
    private static final double ENDER_SWORD_DRAGON_RIDER_VERTICAL_OFFSET = -2.10;
    private static final double ENDER_SWORD_SEAT_START_Y_OFFSET = 0.35;
    private static final double ENDER_SWORD_DRAGON_BASE_MOVE_SCALAR = 0.60;
    private static final double ENDER_SWORD_DRAGON_SPRINT_MULTIPLIER = 1.15;
    private static final int ENDER_SWORD_DRAGON_COLLISION_STABILIZE_TICKS = 4;
    private static final double ENDER_SWORD_DRAGON_RIDER_COLLISION_MAX_HORIZONTAL = 0.18;
    private static final double ENDER_SWORD_DRAGON_RIDER_COLLISION_MAX_VERTICAL = 0.10;
    private static final double ENDER_SWORD_DRAGON_TARGET_COLLISION_MAX_HORIZONTAL = 0.85;
    private static final double ENDER_SWORD_DRAGON_TARGET_COLLISION_MAX_VERTICAL = 0.35;
    private static final double ENDER_SWORD_SEAT_COLLISION_RADIUS = 0.45;
    private static final double ENDER_SWORD_SEAT_COLLISION_HEAD_HEIGHT = 1.55;
    private static final double ENDER_SWORD_SEAT_COLLISION_MID_HEIGHT = 0.90;
    private static final double ENDER_SWORD_SEAT_COLLISION_FOOT_HEIGHT = 0.10;
    private static final List<Vector> ENDER_SWORD_SEAT_COLLISION_OFFSETS = List.of(
        new Vector(0.0, 0.0, 0.0),
        new Vector(ENDER_SWORD_SEAT_COLLISION_RADIUS, 0.0, 0.0),
        new Vector(-ENDER_SWORD_SEAT_COLLISION_RADIUS, 0.0, 0.0),
        new Vector(0.0, 0.0, ENDER_SWORD_SEAT_COLLISION_RADIUS),
        new Vector(0.0, 0.0, -ENDER_SWORD_SEAT_COLLISION_RADIUS)
    );
    private static final List<Double> ENDER_SWORD_SEAT_COLLISION_HEIGHTS = List.of(
        ENDER_SWORD_SEAT_COLLISION_FOOT_HEIGHT,
        ENDER_SWORD_SEAT_COLLISION_MID_HEIGHT,
        ENDER_SWORD_SEAT_COLLISION_HEAD_HEIGHT
    );
    private static final double HERMES_BOOTS_SPEED_SCALAR = 0.60;
    private static final Color HERMES_BOOTS_COLOR = Color.fromRGB(245, 201, 66);
    private static final int RECIPE_TRADE_SLOT = 26;
    private static final double WIND_CHARGE_CANNON_SUPER_SHOT_STRENGTH = 0.8;
    private static final double WIND_CHARGE_CANNON_NORMAL_SHOT_STRENGTH = 1.2;
    private static final int WITHER_BLADE_SKULL_MAX_CHARGES = 10;
    private static final int WITHER_BLADE_DASH_MAX_CHARGES = 4;
    private static final long WITHER_BLADE_SKULL_RECHARGE_MS = 4_500L;
    private static final long WITHER_BLADE_DASH_RECHARGE_MS = 3_000L;
    private static final float WITHER_BLADE_SKULL_EXPLOSION_POWER = 1.8f;
    private static final double WITHER_BLADE_SKULL_DAMAGE_CAP = 3.0;
    private static final double WITHER_BLADE_DIRECT_HIT_DAMAGE = 6.0;
    private static final double WITHER_BLADE_SPLASH_DAMAGE = 6.0;
    private static final double WITHER_BLADE_SPLASH_RADIUS = 3.5;
    private static final double WITHER_BLADE_SKULL_SPEED = 1.35;
    private static final double WITHER_BLADE_DASH_HORIZONTAL = 1.75;
    private static final double WITHER_BLADE_DASH_VERTICAL = 0.72;
    private static final int WITHER_BLADE_WITHER_SECONDS = 10;
    private static final double EXECUTIONER_BLADE_SKULL_DAMAGE_CAP = 6.0;
    private static final double LIFE_STEALER_HIT_HEAL = 1.0;
    private static final int LIFE_STEALER_BUFF_SECONDS = 60;
    private static final Color WITHER_BLADE_PARTICLE_COLOR = Color.fromRGB(18, 18, 18);
    private static final String GUI_TITLE_RECIPES = "<gradient:#FEE440:#00BBF9><bold>Reliquary</bold></gradient>";
    private static final String GUI_TITLE_PREFIX_RECIPE = "<gradient:#A0E7E5:#B4F8C8><bold>Reliquary:</bold></gradient> ";

    private final SMPCore plugin;

    private final NamespacedKey keyLegendary;
    private final NamespacedKey keyLegendaryVersion;
    private final NamespacedKey keyLegendaryInstance;
    private final NamespacedKey keyMenuLegendary;
    private final NamespacedKey keyEnderBone;
    private final NamespacedKey keyOrbOfTheMystics;
    private final NamespacedKey keyOrbOfTheMysticsCooldownUntil;
    private final NamespacedKey keyLegacyOrbOfTheMysticsInstance;
    private final NamespacedKey keyMidasSharpness;
    private final NamespacedKey keyPercyTridentMode;
    private final NamespacedKey keyWarPickMode;
    private final NamespacedKey keyEnderbowForm;
    private final NamespacedKey keyEmeraldLevel;
    private final NamespacedKey faradaysMagnetRecipeKey;
    private final NamespacedKey keyEnderbowTag;
    private final NamespacedKey keyEnderSwordDragonOwner;
    private final NamespacedKey keyEnderSwordSeatOwner;
    private final NamespacedKey keyHarpoonTag;
    private final NamespacedKey keyMagnetActive;
    private final NamespacedKey keyWindCannonCharges;
    private final NamespacedKey keyWindCannonCooldownUntil;
    private final NamespacedKey keyHermesBootsSpeedModifier;
    private final NamespacedKey keyWitherBladeSkullTag;
    private final NamespacedKey keyWitherBladeSkullCharges;
    private final NamespacedKey keyWitherBladeSkullRechargeStarted;
    private final NamespacedKey keyWitherBladeDashCharges;
    private final NamespacedKey keyWitherBladeDashRechargeStarted;
    private final NamespacedKey keyLifeStealerStacks;
    private final NamespacedKey keyLifeStealerExpiresAt;
    private final NamespacedKey keyLifeStealerSeenPlayers;
    private final NamespacedKey keyLifeStealerSharpness;
    private final NamespacedKey keyStrengthSwordVictims;
    private final NamespacedKey keyMidasHealthModifier;
    private final NamespacedKey keyShadowBladeAttackSpeedModifier;
    private final NamespacedKey keyGodChestplateHealthModifier;
    private final NamespacedKey keyGodChestplateKnockbackModifier;
    private final NamespacedKey keyStrengthDomainHealthModifier;
    private final NamespacedKey keyStrengthDomainAttackSpeedModifier;
    private final Enchantment enchantPower;
    private final Enchantment enchantSharpness;
    private final Enchantment enchantProtection;
    private final Enchantment enchantEfficiency;
    private final Enchantment enchantFortune;
    private final Enchantment enchantUnbreaking;
    private final Enchantment enchantFireAspect;
    private final Enchantment enchantLooting;
    private final Enchantment enchantLoyalty;
    private final Enchantment enchantChanneling;
    private final Enchantment enchantImpaling;
    private final Enchantment enchantRiptide;
    private final Enchantment enchantDensity;
    private final Enchantment enchantBreach;
    private final Enchantment enchantWindBurst;
    private final Enchantment enchantMending;

    private final List<LegendaryRecipe> recipes;
    private final Set<NamespacedKey> recipeBookKeys = new HashSet<>();
    private final Set<UUID> reliquaryReturnToMainMenuPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> mythicFusionReturnToMainMenuPlayers = ConcurrentHashMap.newKeySet();

    private final Map<UUID, Long> enderbowCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> enderSwordSummonCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> chronoCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> harpoonCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> hypnosisCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> wardenBladeProtectionCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> wardenBladeSoundWaveCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> frostScytheCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> blinkDaggerCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> blinkDaggerBackstabCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> executionerStrengthCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> executionerShockwaveCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> rhittaBlessingCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> rhittaCruelSunCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> reaperScytheCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> shadowBladeCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> headhunterBuffCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> godChestplateCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> strengthSwordBeamCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> strengthSwordDomainCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dashMaceCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> thorsHammerCd = new ConcurrentHashMap<>();

    private final Map<UUID, ChronoState> chronoStates = new ConcurrentHashMap<>();
    private final Map<UUID, FrostScytheFreezeState> frostScytheFrozen = new ConcurrentHashMap<>();
    private final Map<UUID, RhittaBurnState> rhittaBurns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> blinkDaggerStunnedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> shadowBladeActiveUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> headhunterRage = new ConcurrentHashMap<>();
    private final Map<UUID, StrengthDomainState> activeStrengthDomains = new ConcurrentHashMap<>();
    private final Map<UUID, Double> lifeStealerGrantedAbsorption = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lifeStealerGrantedAmplifier = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> controlledByOwner = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> ownerByMob = new ConcurrentHashMap<>();
    private final Set<UUID> frostScytheSweepPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> warPickAoePlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeMagnetPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingMagnetRefresh = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingWitherBladeLoreRefresh = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> enderDragonByOwner = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> enderDragonSeatByOwner = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> enderDragonOwnerByDragon = new ConcurrentHashMap<>();
    private final Set<UUID> activeEnderDragonRiders = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<Long>> enderDragonChunkTickets = new ConcurrentHashMap<>();
    private final Map<UUID, String> enderDragonChunkTicketWorlds = new ConcurrentHashMap<>();
    private boolean legendaryDuplicateAuditQueued;

    public LegendaryListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyLegendary = new NamespacedKey(plugin, "legendary_id");
        this.keyLegendaryVersion = new NamespacedKey(plugin, "legendary_data_version");
        this.keyLegendaryInstance = new NamespacedKey(plugin, "legendary_instance");
        this.keyMenuLegendary = new NamespacedKey(plugin, "legendary_menu_id");
        this.keyEnderBone = new NamespacedKey(plugin, "ender_bone");
        this.keyOrbOfTheMystics = new NamespacedKey(plugin, "orb_of_the_mystics");
        this.keyOrbOfTheMysticsCooldownUntil = new NamespacedKey(plugin, "orb_of_the_mystics_cooldown_until");
        this.keyLegacyOrbOfTheMysticsInstance = new NamespacedKey(plugin, "orb_of_the_mystics_instance");
        this.keyMidasSharpness = new NamespacedKey(plugin, "midas_sharpness");
        this.keyPercyTridentMode = new NamespacedKey(plugin, "trident_of_percy_mode");
        this.keyWarPickMode = new NamespacedKey(plugin, "war_pick_mode");
        this.keyEnderbowForm = new NamespacedKey(plugin, "enderbow_form");
        this.keyEmeraldLevel = new NamespacedKey(plugin, "emerald_blade_level");
        this.faradaysMagnetRecipeKey = new NamespacedKey(plugin, "faradays_magnet_recipe");
        this.keyEnderbowTag = new NamespacedKey(plugin, "enderbow_endermite");
        this.keyEnderSwordDragonOwner = new NamespacedKey(plugin, "ender_sword_dragon_owner");
        this.keyEnderSwordSeatOwner = new NamespacedKey(plugin, "ender_sword_seat_owner");
        this.keyHarpoonTag = new NamespacedKey(plugin, "harpoon");
        this.keyMagnetActive = new NamespacedKey(plugin, "magnet_active");
        this.keyWindCannonCharges = new NamespacedKey(plugin, "wind_cannon_charges");
        this.keyWindCannonCooldownUntil = new NamespacedKey(plugin, "wind_cannon_cooldown_until");
        this.keyHermesBootsSpeedModifier = new NamespacedKey(plugin, "hermes_boots_speed");
        this.keyWitherBladeSkullTag = new NamespacedKey(plugin, "wither_blade_skull");
        this.keyWitherBladeSkullCharges = new NamespacedKey(plugin, "wither_blade_skull_charges");
        this.keyWitherBladeSkullRechargeStarted = new NamespacedKey(plugin, "wither_blade_skull_recharge_started");
        this.keyWitherBladeDashCharges = new NamespacedKey(plugin, "wither_blade_dash_charges");
        this.keyWitherBladeDashRechargeStarted = new NamespacedKey(plugin, "wither_blade_dash_recharge_started");
        this.keyLifeStealerStacks = new NamespacedKey(plugin, "life_stealer_stacks");
        this.keyLifeStealerExpiresAt = new NamespacedKey(plugin, "life_stealer_expires_at");
        this.keyLifeStealerSeenPlayers = new NamespacedKey(plugin, "life_stealer_seen_players");
        this.keyLifeStealerSharpness = new NamespacedKey(plugin, "life_stealer_sharpness");
        this.keyStrengthSwordVictims = new NamespacedKey(plugin, "strength_sword_victims");
        this.keyMidasHealthModifier = new NamespacedKey(plugin, "midas_sword_health");
        this.keyShadowBladeAttackSpeedModifier = new NamespacedKey(plugin, "shadow_blade_attack_speed");
        this.keyGodChestplateHealthModifier = new NamespacedKey(plugin, "god_chestplate_health");
        this.keyGodChestplateKnockbackModifier = new NamespacedKey(plugin, "god_chestplate_knockback");
        this.keyStrengthDomainHealthModifier = new NamespacedKey(plugin, "strength_domain_health");
        this.keyStrengthDomainAttackSpeedModifier = new NamespacedKey(plugin, "strength_domain_attack_speed");
        this.enchantPower = requireEnchantment("power");
        this.enchantSharpness = requireEnchantment("sharpness");
        this.enchantProtection = requireEnchantment("protection");
        this.enchantEfficiency = requireEnchantment("efficiency");
        this.enchantFortune = requireEnchantment("fortune");
        this.enchantUnbreaking = requireEnchantment("unbreaking");
        this.enchantFireAspect = requireEnchantment("fire_aspect");
        this.enchantLooting = requireEnchantment("looting");
        this.enchantLoyalty = requireEnchantment("loyalty");
        this.enchantChanneling = requireEnchantment("channeling");
        this.enchantImpaling = requireEnchantment("impaling");
        this.enchantRiptide = requireEnchantment("riptide");
        this.enchantDensity = requireEnchantment("density");
        this.enchantBreach = requireEnchantment("breach");
        this.enchantWindBurst = requireEnchantment("wind_burst");
        this.enchantMending = requireEnchantment("mending");
        this.recipes = buildRecipes();
        registerRecipeBookRecipes();
        registerNormalCraftingRecipes();
        Bukkit.getScheduler().runTask(plugin, () -> {
            cleanupTaggedEnderSwordDragons();
            cleanupTaggedEnderSwordSeats();
            for (Player online : Bukkit.getOnlinePlayers()) {
                migratePlayerLegendaryItems(online);
                discoverNormalCraftingRecipes(online);
                refreshMagnetTracking(online);
                refreshWitherBladeLore(online);
                refreshLifeStealerLore(online);
            }
            scheduleLoadedChunkLegendaryMigration();
            auditLegendaryClaimsAndDuplicates();
        });
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickMagnets, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickPercyTridentEffects, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickLifeStealerLore, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickLegendaryStates, 10L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickEnderSwordDragons, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpiredLegendaryCooldowns, 20L * 60L, 20L * 60L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::auditLegendaryClaimsAndDuplicates, LEGENDARY_DUPLICATE_AUDIT_INTERVAL_TICKS, LEGENDARY_DUPLICATE_AUDIT_INTERVAL_TICKS);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cleanupExpiredLegendaryCooldowns();
        migratePlayerLegendaryItems(event.getPlayer());
        discoverNormalCraftingRecipes(event.getPlayer());
        refreshMagnetTracking(event.getPlayer());
        refreshWitherBladeLore(event.getPlayer());
        refreshLifeStealerLore(event.getPlayer());
        syncLegendaryOwnership(event.getPlayer());
        scheduleLegendaryDuplicateAudit();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        blinkDaggerStunnedUntil.remove(id);
        chronoStates.remove(id);
        activeMagnetPlayers.remove(id);
        pendingMagnetRefresh.remove(id);
        pendingWitherBladeLoreRefresh.remove(id);
        shadowBladeActiveUntil.remove(id);
        headhunterRage.remove(id);
        activeStrengthDomains.remove(id);
        lifeStealerGrantedAbsorption.remove(id);
        clearPlayerLegendaryAttributeModifiers(event.getPlayer());
        clearEnderDragonChunkTickets(id);
        despawnEnderSwordDragon(id, false);
        cleanupExpiredLegendaryCooldowns();
        syncLegendaryOwnership(event.getPlayer());

        Set<UUID> controlled = controlledByOwner.remove(id);
        if (controlled != null) {
            for (UUID mobId : controlled) ownerByMob.remove(mobId);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlinkDaggerStunnedGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!event.isGliding()) return;
        if (!isBlinkDaggerStunned(player.getUniqueId())) return;

        event.setCancelled(true);
        player.setGliding(false);
    }

    public void shutdown() {
        for (UUID ownerId : new HashSet<>(enderDragonByOwner.keySet())) {
            despawnEnderSwordDragon(ownerId, false);
        }
        cleanupTaggedEnderSwordDragons();
        cleanupTaggedEnderSwordSeats();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        Recipe eventRecipe = event.getRecipe();
        if (eventRecipe instanceof Keyed keyed && faradaysMagnetRecipeKey.equals(keyed.getKey())) {
            inv.setResult(usesOnlyPlainNormalRecipeIngredients(inv.getMatrix()) ? createFaradaysMagnetItem() : null);
            return;
        }
        LegendaryRecipe recipe = findRecipe(inv.getMatrix());
        if (recipe != null) {
            inv.setResult(null);
            return;
        }
        if (isLegendaryRecipe(eventRecipe)) {
            inv.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getRecipe() instanceof Keyed keyed) || !faradaysMagnetRecipeKey.equals(keyed.getKey())) {
            return;
        }
        if (usesOnlyPlainNormalRecipeIngredients(event.getInventory().getMatrix())) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            player.sendMessage(MessageUtil.warn("Use plain vanilla ingredients for Faraday's Magnet."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getFirstItem();
        ItemStack right = event.getInventory().getSecondItem();
        LegendaryType leftType = typeOf(left);
        LegendaryType rightType = typeOf(right);

        if ((leftType == LegendaryType.STRENGTH_SWORD && isCrimsonDominionRepairItem(right))
            || (rightType == LegendaryType.STRENGTH_SWORD && isCrimsonDominionRepairItem(left))) {
            ItemStack source = leftType == LegendaryType.STRENGTH_SWORD ? left : right;
            ItemStack repaired = createStrengthSwordRepairResult(source);
            if (repaired == null) {
                event.setResult(null);
                return;
            }
            if (event.getView() instanceof org.bukkit.inventory.view.AnvilView anvilView) {
                anvilView.setRepairCost(STRENGTH_SWORD_REPAIR_XP_COST);
                anvilView.setRepairItemCountCost(1);
                anvilView.setMaximumRepairCost(40);
            }
            event.setResult(repaired);
            return;
        }

        if (leftType == null && rightType == null) return;
        if (!canUseVanillaLegendaryUtilities(leftType, rightType)) {
            event.setResult(null);
            return;
        }

        LegendaryType sourceType = utilitySourceType(leftType, rightType);
        ItemStack source = sourceType == leftType ? left : right;
        ItemStack other = sourceType == leftType ? right : left;
        ItemStack result = event.getResult();
        if (source == null || result == null || result.getType() == Material.AIR) return;
        if (sourceType == LegendaryType.STRENGTH_SWORD && !canUseStrengthSwordUtility(other, leftType, rightType)) {
            event.setResult(null);
            return;
        }
        if (sourceType == LegendaryType.GOD_CHESTPLATE && !result.getEnchantments().isEmpty()) {
            event.setResult(null);
            return;
        }
        if (sourceType == LegendaryType.STRENGTH_SWORD && result.getEnchantmentLevel(enchantMending) > 0) {
            event.setResult(null);
            return;
        }
        event.setResult(preserveLegendaryUtilityResult(source, result, sourceType));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        ItemStack top = event.getInventory().getUpperItem();
        ItemStack bottom = event.getInventory().getLowerItem();
        LegendaryType topType = typeOf(top);
        LegendaryType bottomType = typeOf(bottom);
        if (topType == null && bottomType == null) return;
        event.setResult(null);
    }

    private boolean canUseVanillaLegendaryUtilities(LegendaryType first, LegendaryType second) {
        if (first == null || second == null) {
            return first != null || second != null;
        }
        return first == second;
    }

    private boolean canUseStrengthSwordUtility(ItemStack other, LegendaryType first, LegendaryType second) {
        if (other == null || other.getType() == Material.AIR) {
            return true;
        }
        if (first == LegendaryType.STRENGTH_SWORD && second == LegendaryType.STRENGTH_SWORD) {
            return true;
        }
        if (isCrimsonDominionRepairItem(other)) {
            return true;
        }
        return other.getType() == Material.ENCHANTED_BOOK;
    }

    private boolean isCrimsonDominionRepairItem(ItemStack item) {
        BossManager bossManager = plugin.getBossManager();
        return bossManager != null && bossManager.isDominionCore(item);
    }

    private ItemStack createStrengthSwordRepairResult(ItemStack source) {
        if (typeOf(source) != LegendaryType.STRENGTH_SWORD) {
            return null;
        }

        ItemStack repaired = source.clone();
        ItemMeta meta = repaired.getItemMeta();
        if (!(meta instanceof Damageable damageable) || !damageable.hasDamage() || damageable.getDamage() <= 0) {
            return null;
        }

        applyLegendaryDurabilitySettings(meta, LegendaryType.STRENGTH_SWORD);
        damageable.setDamage(0);
        meta.removeEnchant(enchantMending);
        meta.lore(buildStrengthSwordLore(meta, strengthSwordVictimCount(meta)));
        repaired.setItemMeta(meta);
        return repaired;
    }

    private boolean damageStrengthSwordAbilityUse(Player player, int amount) {
        if (player == null || amount <= 0) {
            return false;
        }

        ItemStack sword = player.getInventory().getItemInMainHand();
        if (typeOf(sword) != LegendaryType.STRENGTH_SWORD) {
            return false;
        }

        ItemMeta meta = sword.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return false;
        }

        applyLegendaryDurabilitySettings(meta, LegendaryType.STRENGTH_SWORD);
        int maxDamage = damageable.hasMaxDamage() ? damageable.getMaxDamage() : STRENGTH_SWORD_MAX_DURABILITY;
        int nextDamage = damageable.getDamage() + amount;
        if (nextDamage >= maxDamage) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
            return true;
        }

        damageable.setDamage(nextDamage);
        sword.setItemMeta(meta);
        player.getInventory().setItemInMainHand(sword);
        return true;
    }

    private LegendaryType utilitySourceType(LegendaryType first, LegendaryType second) {
        return first != null ? first : second;
    }

    private ItemStack preserveLegendaryUtilityResult(ItemStack source, ItemStack result, LegendaryType type) {
        ItemStack updated = result.clone();
        ItemMeta sourceMeta = source.getItemMeta();
        ItemMeta resultMeta = updated.getItemMeta();
        if (sourceMeta == null || resultMeta == null) {
            return updated;
        }

        PersistentDataContainer sourcePdc = sourceMeta.getPersistentDataContainer();
        PersistentDataContainer resultPdc = resultMeta.getPersistentDataContainer();
        String instanceId = sourcePdc.get(keyLegendaryInstance, PersistentDataType.STRING);
        if (instanceId == null || instanceId.isBlank()) {
            instanceId = UUID.randomUUID().toString();
        }

        resultPdc.set(keyLegendary, PersistentDataType.STRING, type.id);
        resultPdc.set(keyLegendaryVersion, PersistentDataType.INTEGER, LEGENDARY_ITEM_DATA_VERSION);
        resultPdc.set(keyLegendaryInstance, PersistentDataType.STRING, instanceId);
        if (!resultMeta.hasDisplayName()) {
            resultMeta.displayName(MM.deserialize(type.display));
        }
        resultMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        CustomLoreUtil.applyStyledItemFlags(resultMeta);
        applyLegendaryDurabilitySettings(resultMeta, type);
        AwakeningTableListener awakening = plugin.getAwakeningTableListener();
        if (awakening != null) {
            awakening.copyAwakeningState(sourceMeta, resultMeta);
        }
        applyLegendaryTypeState(resultMeta, type);
        updated.setItemMeta(resultMeta);
        return updated;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareItemEnchant(PrepareItemEnchantEvent event) {
        LegendaryType type = typeOf(event.getItem());
        if (type != LegendaryType.EMERALD_BLADE && type != LegendaryType.GOD_CHESTPLATE) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        LegendaryType type = typeOf(event.getItem());
        if (type == null) {
            return;
        }
        if (type == LegendaryType.EMERALD_BLADE || type == LegendaryType.GOD_CHESTPLATE) {
            event.setCancelled(true);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> refreshLegendaryPresentation(event.getItem()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        migrateLegendaryItemsInInventory(event.getInventory());
        if (event.getPlayer() instanceof Player player) {
            migrateLegendaryItemsInInventory(player.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (migrateLegendaryItemTree(event.getCurrentItem())) {
            event.setCurrentItem(event.getCurrentItem());
        }
        if (migrateLegendaryItemTree(event.getCursor())) {
            event.getWhoClicked().setItemOnCursor(event.getCursor());
        }
        queueMagnetTrackingRefresh(player);
        queueWitherBladeLoreRefresh(player);
        if (handleLegendaryCraftClick(event, player)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() != top) return;

        if (top.getHolder() instanceof ReliquaryMenuHolder holder) {
            event.setCancelled(true);

            if (holder.section() == null && event.getSlot() == 49 && reliquaryReturnsToMainMenu(player)) {
                reliquaryReturnToMainMenuPlayers.remove(player.getUniqueId());
                MainMenuCommand.openMenu(plugin, player);
                return;
            }

            if (holder.section() != null && event.getSlot() == 49) {
                openCurrentReliquaryMenu(player);
                return;
            }

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            String recipeId = readMenuLegendaryId(clicked);
            if (recipeId == null) return;

            if (recipeId.startsWith(RELIQUARY_SECTION_PREFIX)) {
                ReliquarySection section = ReliquarySection.fromId(recipeId.substring(RELIQUARY_SECTION_PREFIX.length()));
                if (section != null) {
                    if (section == ReliquarySection.SEASON && plugin.getSeasonRelicManager() != null) {
                        plugin.getSeasonRelicManager().openArmoryMenuFromReliquary(player, reliquaryReturnsToMainMenu(player));
                        return;
                    }
                    openReliquarySectionMenu(player, section);
                }
                return;
            }

            openReliquaryEntry(player, recipeId);
            return;
        }

        if (top.getHolder() instanceof RecipeMenuHolder holder) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            if (holder.type() == null) {
                String recipeId = readMenuLegendaryId(clicked);
                if (recipeId == null) return;
                openReliquaryEntry(player, recipeId);
                return;
            }

            if (event.getSlot() == RECIPE_TRADE_SLOT) {
                if (plugin.getLegendaryAltarManager() != null) {
                    plugin.getLegendaryAltarManager().sendRecipeHint(player, holder.type().id);
                } else {
                    player.sendMessage(MessageUtil.info("Legendary altar data is not ready yet."));
                }
                return;
            }

            if (event.getSlot() == 18) {
                openCurrentReliquaryMenu(player);
            }
            return;
        }

        if (top.getHolder() instanceof AncientScrollRecipeHolder) {
            event.setCancelled(true);
            if (event.getSlot() == RECIPE_TRADE_SLOT) {
                craftAncientScrollFromInventory(player);
                Bukkit.getScheduler().runTask(plugin, () -> openAncientScrollRecipeDetails(player));
                return;
            }
            if (event.getSlot() == 18) {
                openCurrentReliquaryMenu(player);
            }
            return;
        }

        if (top.getHolder() instanceof AscendantCoreRecipeHolder) {
            event.setCancelled(true);
            if (event.getSlot() == RECIPE_TRADE_SLOT) {
                craftMythicForgeItemFromInventory(player, MythicForgeListener.ASCENDANT_CORE_ITEM_ID);
                Bukkit.getScheduler().runTask(plugin, () -> openAscendantCoreRecipeDetails(player));
                return;
            }
            if (event.getSlot() == 18) {
                openCurrentReliquaryMenu(player);
            }
            return;
        }

        if (top.getHolder() instanceof CustomToolRecipeHolder holder) {
            event.setCancelled(true);
            if (event.getSlot() == RECIPE_TRADE_SLOT) {
                craftCustomToolFromInventory(player, holder.toolId());
                Bukkit.getScheduler().runTask(plugin, () -> openCustomToolRecipeDetails(player, holder.toolId()));
                return;
            }
            if (event.getSlot() == 18) {
                openCurrentReliquaryMenu(player);
            }
            return;
        }

        if (top.getHolder() instanceof MythicForgeRecipeHolder) {
            event.setCancelled(true);
            if (event.getSlot() == RECIPE_TRADE_SLOT) {
                craftMythicForgeItemFromInventory(player, MythicForgeListener.MYTHIC_FORGE_ITEM_ID);
                Bukkit.getScheduler().runTask(plugin, () -> openMythicForgeRecipeDetails(player));
                return;
            }
            if (event.getSlot() == 40) {
                openMythicFusionMenuFromReliquary(player);
                return;
            }
            if (event.getSlot() == 45) {
                openCurrentReliquaryMenu(player);
            }
            return;
        }

        if (top.getHolder() instanceof MythicFusionMenuHolder) {
            event.setCancelled(true);
            if (event.getSlot() == 49) {
                if (mythicFusionReturnToMainMenuPlayers.remove(player.getUniqueId())) {
                    MainMenuCommand.openMenu(plugin, player);
                } else {
                    openCurrentReliquaryMenu(player);
                }
                return;
            }
            MythicForgeListener.FusionRecipeView recipe = fusionRecipeForMenuSlot(event.getSlot());
            if (recipe != null) {
                openMythicFusionRecipeDetails(player, recipe);
            }
            return;
        }

        if (top.getHolder() instanceof MythicFusionRecipeHolder) {
            event.setCancelled(true);
            if (event.getSlot() == 49) {
                openMythicFusionMenuInternal(player, mythicFusionReturnToMainMenuPlayers.contains(player.getUniqueId()));
            }
            return;
        }

        if (top.getHolder() instanceof FaradaysMagnetRecipeHolder) {
            event.setCancelled(true);
            if (event.getSlot() == RECIPE_TRADE_SLOT) {
                craftFaradaysMagnetFromInventory(player);
                Bukkit.getScheduler().runTask(plugin, () -> openFaradaysMagnetRecipeDetails(player));
                return;
            }
            if (event.getSlot() == 18) {
                openCurrentReliquaryMenu(player);
            }
            return;
        }

        if (top.getHolder() instanceof BackpackRecipeHolder holder) {
            event.setCancelled(true);
            if (event.getSlot() == RECIPE_TRADE_SLOT) {
                if (plugin.getBackpackListener() != null) {
                    if (EXPANDED_BACKPACK_RECIPE_ID.equals(holder.recipeId())) {
                        plugin.getBackpackListener().tradeUpgradedBackpack(player);
                    } else {
                        plugin.getBackpackListener().tradeBackpack(player);
                    }
                }
                Bukkit.getScheduler().runTask(plugin, () -> openBackpackRecipeDetails(player, holder.recipeId()));
                return;
            }
            if (event.getSlot() == 18) {
                openCurrentReliquaryMenu(player);
            }
            return;
        }

        if (top.getHolder() instanceof TalismanRecipeHolder) {
            event.setCancelled(true);
            if (event.getSlot() == RECIPE_TRADE_SLOT) {
                craftTalismanFromInventory(player);
                Bukkit.getScheduler().runTask(plugin, () -> openSustenanceTalismanRecipeDetails(player));
                return;
            }
            if (event.getSlot() == 18) {
                openCurrentReliquaryMenu(player);
            }
            return;
        }

        if (top.getHolder() instanceof SalvagingDepotRecipeHolder) {
            event.setCancelled(true);
            if (event.getSlot() == RECIPE_TRADE_SLOT) {
                craftSalvagingDepotFromInventory(player);
                Bukkit.getScheduler().runTask(plugin, () -> openSalvagingDepotRecipeDetails(player));
                return;
            }
            if (event.getSlot() == 18) {
                openCurrentReliquaryMenu(player);
            }
        }

        if (top.getHolder() instanceof AgriculturalPylonRecipeHolder) {
            event.setCancelled(true);
            if (event.getSlot() == RECIPE_TRADE_SLOT) {
                craftAgriculturalPylonFromInventory(player);
                Bukkit.getScheduler().runTask(plugin, () -> openAgriculturalPylonRecipeDetails(player));
                return;
            }
            if (event.getSlot() == 18) {
                openCurrentReliquaryMenu(player);
            }
        }

        if (top.getHolder() instanceof XpLecternRecipeHolder) {
            event.setCancelled(true);
            if (event.getSlot() == RECIPE_TRADE_SLOT) {
                craftXpLecternFromInventory(player);
                Bukkit.getScheduler().runTask(plugin, () -> openXpLecternRecipeDetails(player));
                return;
            }
            if (event.getSlot() == 18) {
                openCurrentReliquaryMenu(player);
            }
        }

        if (top.getHolder() instanceof AwakeningTableInfoHolder) {
            event.setCancelled(true);
            if (event.getSlot() == 18) {
                openCurrentReliquaryMenu(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            queueMagnetTrackingRefresh(player);
            queueWitherBladeLoreRefresh(player);
        }
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof ReliquaryMenuHolder
            || top.getHolder() instanceof RecipeMenuHolder
            || top.getHolder() instanceof AncientScrollRecipeHolder
            || top.getHolder() instanceof AscendantCoreRecipeHolder
            || top.getHolder() instanceof BackpackRecipeHolder
            || top.getHolder() instanceof TalismanRecipeHolder
            || top.getHolder() instanceof SalvagingDepotRecipeHolder
            || top.getHolder() instanceof AgriculturalPylonRecipeHolder
            || top.getHolder() instanceof XpLecternRecipeHolder
            || top.getHolder() instanceof AwakeningTableInfoHolder
            || top.getHolder() instanceof MythicForgeRecipeHolder
            || top.getHolder() instanceof MythicFusionMenuHolder
            || top.getHolder() instanceof MythicFusionRecipeHolder
            || top.getHolder() instanceof CustomToolRecipeHolder
            || top.getHolder() instanceof FaradaysMagnetRecipeHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickupItem(EntityPickupItemEvent event) {
        if (migrateLegendaryItemTree(event.getItem().getItemStack())) {
            event.getItem().setItemStack(event.getItem().getItemStack());
        }
        if (event.getEntity() instanceof Player player) {
            queueMagnetTrackingRefresh(player);
            queueWitherBladeLoreRefresh(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent event) {
        queueWitherBladeLoreRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        queueWitherBladeLoreRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        queueWitherBladeLoreRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        migrateLegendaryItemsInChunk(event.getChunk());
    }

    public void openRecipeMenu(Player player) {
        openRecipeMenu(player, false);
    }

    public void openRecipeMenuFromMainMenu(Player player) {
        openRecipeMenu(player, true);
    }

    private void openCurrentReliquaryMenu(Player player) {
        openRecipeMenu(player, reliquaryReturnsToMainMenu(player));
    }

    private boolean reliquaryReturnsToMainMenu(Player player) {
        return player != null && reliquaryReturnToMainMenuPlayers.contains(player.getUniqueId());
    }

    private void openRecipeMenu(Player player, boolean returnToMainMenu) {
        if (returnToMainMenu) {
            reliquaryReturnToMainMenuPlayers.add(player.getUniqueId());
        } else {
            reliquaryReturnToMainMenuPlayers.remove(player.getUniqueId());
        }
        mythicFusionReturnToMainMenuPlayers.remove(player.getUniqueId());

        Inventory inv = Bukkit.createInventory(
            new ReliquaryMenuHolder(null),
            54,
            BedrockCompat.menuTitle(player, MM.deserialize(GUI_TITLE_RECIPES), "Reliquary")
        );

        decorateReliquaryInventory(inv);
        inv.setItem(4, createGuiItem(
            Material.ENCHANTED_BOOK,
            "<gradient:#ffe066:#72f7ff><bold>Reliquary</bold></gradient>",
            List.of(
                "<gray>Recipes and relic knowledge are sorted by category.</gray>",
                "<gray>Pick a wing below, then choose the item you want to inspect.</gray>"
            )
        ));
        inv.setItem(20, createReliquarySectionItem(player, ReliquarySection.LEGENDARIES));
        inv.setItem(22, createReliquarySectionItem(player, ReliquarySection.MYTHICS));
        inv.setItem(24, createReliquarySectionItem(player, ReliquarySection.TOOLS));
        inv.setItem(31, createReliquarySectionItem(player, ReliquarySection.UTILITY));
        if (plugin.getSeasonRelicManager() != null) {
            inv.setItem(13, createReliquarySectionItem(player, ReliquarySection.SEASON));
        }
        if (returnToMainMenu) {
            inv.setItem(49, createGuiItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to /menu.</gray>")));
        }

        player.openInventory(inv);
    }

    private void openReliquarySectionMenu(Player player, ReliquarySection section) {
        Inventory inv = Bukkit.createInventory(
            new ReliquaryMenuHolder(section),
            54,
            BedrockCompat.menuTitle(player, MM.deserialize(section.title), section.plainTitle)
        );

        decorateReliquaryInventory(inv);
        List<String> headerLore = new ArrayList<>(section.lore);
        headerLore.add("<dark_gray> ");
        headerLore.add("<gray>Entries: <white>" + reliquaryEntries(player, section).size() + "</white></gray>");
        inv.setItem(4, createGuiItem(section.icon, section.title, headerLore));

        List<CustomRecipeEntry> entries = reliquaryEntries(player, section);
        for (int i = 0; i < entries.size() && i < RELIQUARY_CONTENT_SLOTS.length; i++) {
            CustomRecipeEntry entry = entries.get(i);
            ItemStack icon = entry.icon().clone();
            tagMenuLegendaryId(icon, entry.id());
            inv.setItem(RELIQUARY_CONTENT_SLOTS[i], icon);
        }

        if (entries.isEmpty()) {
            inv.setItem(22, createGuiItem(
                Material.BARRIER,
                "<red><bold>No Entries Ready</bold></red>",
                List.of("<gray>This category has no available recipes right now.</gray>")
            ));
        }

        inv.setItem(49, createGuiItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to the Reliquary hub</gray>")));
        player.openInventory(inv);
    }

    private MythicForgeListener.FusionRecipeView fusionRecipeForMenuSlot(int slot) {
        MythicForgeListener forge = plugin.getMythicForgeListener();
        if (forge == null) {
            return null;
        }
        List<MythicForgeListener.FusionRecipeView> recipes = forge.fusionRecipes();
        for (int i = 0; i < MYTHIC_FUSION_MENU_SLOTS.length && i < recipes.size(); i++) {
            if (MYTHIC_FUSION_MENU_SLOTS[i] == slot) {
                return recipes.get(i);
            }
        }
        return null;
    }

    public void openMythicFusionMenu(Player player) {
        reliquaryReturnToMainMenuPlayers.remove(player.getUniqueId());
        openMythicFusionMenuInternal(player, false);
    }

    public void openMythicFusionMenuFromMainMenu(Player player) {
        reliquaryReturnToMainMenuPlayers.remove(player.getUniqueId());
        openMythicFusionMenuInternal(player, true);
    }

    private void openMythicFusionMenuFromReliquary(Player player) {
        openMythicFusionMenuInternal(player, false);
    }

    private void openMythicFusionMenuInternal(Player player, boolean returnToMainMenu) {
        if (returnToMainMenu) {
            mythicFusionReturnToMainMenuPlayers.add(player.getUniqueId());
        } else {
            mythicFusionReturnToMainMenuPlayers.remove(player.getUniqueId());
        }

        MythicForgeListener forge = plugin.getMythicForgeListener();
        if (forge == null) {
            player.sendMessage(MessageUtil.error("Mythic fusion recipes are not ready yet."));
            return;
        }

        Inventory inv = Bukkit.createInventory(
            new MythicFusionMenuHolder(),
            54,
            BedrockCompat.menuTitle(
                player,
                MM.deserialize("<gradient:#ff4df0:#ffb000><bold>Mythic Nexus</bold></gradient>"),
                "Mythic Nexus"
            )
        );

        ItemStack filler = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        inv.setItem(4, createGuiItem(
            Material.NETHER_STAR,
            "<gradient:#ff4df0:#ffb000><bold>Mythic Nexus</bold></gradient>",
            List.of(
                "<gray>These are the top-tier fusions made at a placed</gray>",
                "<gray><white>Mythic Forge</white> using <white>2 legendaries</white> and an <white>Ascendant Core</white>.</gray>",
                "<dark_gray>The forge station only crafts. This menu shows the rewards.</dark_gray>"
            )
        ));

        int index = 0;
        for (MythicForgeListener.FusionRecipeView recipe : forge.fusionRecipes()) {
            if (index >= MYTHIC_FUSION_MENU_SLOTS.length) {
                break;
            }
            inv.setItem(MYTHIC_FUSION_MENU_SLOTS[index++], createFusionRecipePreview(player, recipe));
        }

        inv.setItem(49, createGuiItem(
            Material.ARROW,
            "<yellow>Back</yellow>",
            List.of(returnToMainMenu ? "<gray>Return to /menu.</gray>" : "<gray>Return to the Reliquary</gray>")
        ));
        player.openInventory(inv);
    }

    private void openReliquaryEntry(Player player, String recipeId) {
        if (BACKPACK_RECIPE_ID.equals(recipeId)) {
            openBackpackRecipeDetails(player, BACKPACK_RECIPE_ID);
            return;
        }
        if (EXPANDED_BACKPACK_RECIPE_ID.equals(recipeId)) {
            openBackpackRecipeDetails(player, EXPANDED_BACKPACK_RECIPE_ID);
            return;
        }
        if (MYTHIC_NEXUS_MENU_ID.equals(recipeId)) {
            openMythicFusionMenuFromReliquary(player);
            return;
        }
        if (SuperpowerManager.ANCIENT_SCROLL_ITEM_ID.equals(recipeId)) {
            openAncientScrollRecipeDetails(player);
            return;
        }
        if (MythicForgeListener.MYTHIC_FORGE_ITEM_ID.equals(recipeId)) {
            openMythicForgeRecipeDetails(player);
            return;
        }
        if (MythicForgeListener.ASCENDANT_CORE_ITEM_ID.equals(recipeId)) {
            openAscendantCoreRecipeDetails(player);
            return;
        }
        if (SustenanceTalismanListener.ITEM_ID.equals(recipeId)) {
            openSustenanceTalismanRecipeDetails(player);
            return;
        }
        if (SalvagingDepotListener.ITEM_ID.equals(recipeId)) {
            openSalvagingDepotRecipeDetails(player);
            return;
        }
        if (AgriculturalPylonListener.ITEM_ID.equals(recipeId)) {
            openAgriculturalPylonRecipeDetails(player);
            return;
        }
        if (XpLecternListener.ITEM_ID.equals(recipeId)) {
            openXpLecternRecipeDetails(player);
            return;
        }
        if (AWAKENING_TABLE_INFO_ID.equals(recipeId)) {
            openAwakeningTableInfo(player);
            return;
        }
        if (plugin.getSeasonRelicManager() != null
            && plugin.getSeasonRelicManager().handlesReliquaryEntry(recipeId)) {
            plugin.getSeasonRelicManager().openReliquaryEntry(player, recipeId, reliquaryReturnsToMainMenu(player));
            return;
        }
        if (plugin.getCustomToolListener() != null && plugin.getCustomToolListener().isCustomToolId(recipeId)) {
            openCustomToolRecipeDetails(player, recipeId);
            return;
        }
        if (LegendaryType.FARADAYS_MAGNET.id.equals(recipeId)) {
            openFaradaysMagnetRecipeDetails(player);
            return;
        }

        LegendaryType type = LegendaryType.fromId(recipeId);
        if (type != null) {
            openRecipeDetails(player, type);
        }
    }

    private void decorateReliquaryInventory(Inventory inv) {
        ItemStack filler = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        ItemStack accent = createGuiItem(Material.PURPLE_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
        for (int slot : List.of(0, 1, 7, 8, 9, 17, 36, 44, 45, 46, 52, 53)) {
            inv.setItem(slot, accent);
        }
    }

    private ItemStack createReliquarySectionItem(Player player, ReliquarySection section) {
        List<String> lore = new ArrayList<>(section.lore);
        lore.add("<dark_gray> ");
        lore.add("<gray>Entries: <white>" + reliquaryEntries(player, section).size() + "</white></gray>");
        lore.add("<dark_gray>" + BedrockCompat.menuActionWord(player) + " to open</dark_gray>");
        ItemStack item = createGuiItem(section.icon, section.title, lore);
        tagMenuLegendaryId(item, RELIQUARY_SECTION_PREFIX + section.id);
        return item;
    }

    private List<CustomRecipeEntry> reliquaryEntries(Player player, ReliquarySection section) {
        List<CustomRecipeEntry> entries = new ArrayList<>();
        switch (section) {
            case LEGENDARIES -> {
                for (LegendaryType type : craftableLegendaryTypes()) {
                    entries.add(new CustomRecipeEntry(type.id, createPreviewItem(player, type)));
                }
            }
            case MYTHICS -> {
                if (plugin.getMythicForgeListener() != null) {
                    entries.add(new CustomRecipeEntry(MYTHIC_NEXUS_MENU_ID, createMythicNexusPreview(player)));
                    entries.add(new CustomRecipeEntry(MythicForgeListener.MYTHIC_FORGE_ITEM_ID, createMythicForgePreview(player)));
                    entries.add(new CustomRecipeEntry(MythicForgeListener.ASCENDANT_CORE_ITEM_ID, createAscendantCorePreview(player)));
                }
            }
            case TOOLS -> {
                entries.add(new CustomRecipeEntry(BACKPACK_RECIPE_ID, appendRecipeMenuHint(player, createBackpackRecipeDisplayItem(false))));
                entries.add(new CustomRecipeEntry(EXPANDED_BACKPACK_RECIPE_ID, appendRecipeMenuHint(player, createBackpackRecipeDisplayItem(true))));
                if (plugin.getSalvagingDepotListener() != null) {
                    entries.add(new CustomRecipeEntry(SalvagingDepotListener.ITEM_ID, createSalvagingDepotPreview(player)));
                }
                if (plugin.getAgriculturalPylonListener() != null) {
                    entries.add(new CustomRecipeEntry(AgriculturalPylonListener.ITEM_ID, createAgriculturalPylonPreview(player)));
                }
                if (plugin.getXpLecternListener() != null) {
                    entries.add(new CustomRecipeEntry(XpLecternListener.ITEM_ID, createXpLecternPreview(player)));
                }
                if (plugin.getCustomToolListener() != null) {
                    for (String toolId : plugin.getCustomToolListener().craftableToolIds()) {
                        entries.add(new CustomRecipeEntry(toolId, createCustomToolPreview(player, toolId)));
                    }
                }
                entries.add(new CustomRecipeEntry(LegendaryType.FARADAYS_MAGNET.id, createFaradaysMagnetPreview(player)));
            }
            case UTILITY -> {
                if (plugin.getAwakeningTableListener() != null) {
                    entries.add(new CustomRecipeEntry(AWAKENING_TABLE_INFO_ID, createAwakeningTablePreview(player)));
                }
                if (plugin.getSuperpowerManager() != null) {
                    entries.add(new CustomRecipeEntry(SuperpowerManager.ANCIENT_SCROLL_ITEM_ID, createAncientScrollPreview(player)));
                }
                if (plugin.getSustenanceTalismanListener() != null) {
                    entries.add(new CustomRecipeEntry(SustenanceTalismanListener.ITEM_ID, createSustenanceTalismanPreview(player)));
                }
            }
            case SEASON -> {
                if (plugin.getSeasonRelicManager() != null) {
                    entries.add(new CustomRecipeEntry(SeasonRelicManager.ARMORY_MENU_ID, plugin.getSeasonRelicManager().createArmoryPreview(player)));
                }
            }
        }
        return entries;
    }

    public List<String> legendaryIds() {
        List<String> ids = new ArrayList<>();
        for (LegendaryType type : LegendaryType.values()) {
            ids.add(type.id);
        }
        return ids;
    }

    public List<String> craftableLegendaryIds() {
        List<String> ids = new ArrayList<>();
        for (LegendaryRecipe recipe : recipes) {
            ids.add(recipe.type().id);
        }
        return ids;
    }

    private List<LegendaryType> craftableLegendaryTypes() {
        List<LegendaryType> types = new ArrayList<>();
        for (LegendaryRecipe recipe : recipes) {
            types.add(recipe.type());
        }
        return types;
    }

    public boolean isLegendaryItem(ItemStack item) {
        return typeOf(item) != null;
    }

    public boolean containsStorageRestrictedLegendary(ItemStack item) {
        return containsStorageRestrictedLegendary(item, 0);
    }

    public boolean isEnderBoneItem(ItemStack item) {
        return isEnderBone(item);
    }

    public boolean isOrbOfTheMysticsItem(ItemStack item) {
        return isOrbOfTheMystics(item);
    }

    public boolean refreshLegendaryItem(ItemStack item) {
        if (typeOf(item) == null) {
            return false;
        }
        refreshLegendaryPresentation(item);
        return true;
    }

    public String displayNameForLegendary(String id) {
        LegendaryType type = LegendaryType.fromId(normalizeLegendaryId(id));
        if (type == null) return null;
        return PLAIN.serialize(MM.deserialize(type.display)).trim();
    }

    public String rarityLabelForLegendary(String id) {
        LegendaryType type = LegendaryType.fromId(normalizeLegendaryId(id));
        return type == null ? null : type.rarity.label();
    }

    public String legendaryId(ItemStack item) {
        LegendaryType type = typeOf(item);
        return type == null ? null : type.id;
    }

    public int maxServerCopiesForLegendary(String id) {
        LegendaryType type = LegendaryType.fromId(normalizeLegendaryId(id));
        return maxServerCopiesForLegendary(type);
    }

    public boolean isMythicForgeSourceLegendary(String id) {
        LegendaryType type = LegendaryType.fromId(normalizeLegendaryId(id));
        return isMythicForgeSourceLegendary(type);
    }

    public boolean isMythicForgeOutputLegendary(String id) {
        LegendaryType type = LegendaryType.fromId(normalizeLegendaryId(id));
        return isMythicForgeOutputLegendary(type);
    }

    public String legendaryInstanceId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(keyLegendaryInstance, PersistentDataType.STRING);
    }

    public void registerLegendaryInstance(Player owner, ItemStack item, boolean craftedCurrentAltar) {
        if (owner == null || item == null) {
            return;
        }
        LegendaryType type = typeOf(item);
        if (type == null || !isExclusiveLegendaryType(type)) {
            return;
        }
        String instanceId = legendaryInstanceId(item);
        if (instanceId == null || instanceId.isBlank()) {
            return;
        }
        LegendaryAltarManager altarManager = plugin.getLegendaryAltarManager();
        if (altarManager != null) {
            altarManager.registerLegendaryInstance(type.id, instanceId, owner.getUniqueId(), craftedCurrentAltar);
        }
    }

    public void resyncLegendaryOwnership(Player player) {
        syncLegendaryOwnership(player);
    }

    public void syncStoredLegendaryOwnership(String sourceKey, Inventory inventory) {
        LegendaryAltarManager altarManager = plugin.getLegendaryAltarManager();
        if (altarManager == null || sourceKey == null || sourceKey.isBlank()) {
            return;
        }

        Map<String, String> heldLegendaryInstances = new LinkedHashMap<>();
        migrateLegendaryItemsInInventory(inventory);
        collectLegendaryOwnership(inventory, heldLegendaryInstances);
        altarManager.syncStoredLegendaryOwnership(sourceKey, heldLegendaryInstances);
    }

    public void clearStoredLegendaryOwnership(String sourceKey) {
        LegendaryAltarManager altarManager = plugin.getLegendaryAltarManager();
        if (altarManager == null || sourceKey == null || sourceKey.isBlank()) {
            return;
        }
        altarManager.syncStoredLegendaryOwnership(sourceKey, Map.of());
    }

    public String normalizeLegendaryId(String input) {
        if (input == null) return null;
        String normalized = input.trim().toLowerCase();
        if (normalized.isEmpty()) return null;
        return switch (normalized) {
            case "ender_bow", "riftbow" -> "enderbow";
            case "ender", "enderblade", "ender_sword", "dragonblade", "dragon_sword", "riftreaver" -> "ender_sword";
            case "chrono", "chronosword", "chrono_blade", "hourglassblade", "hourglass_blade" -> "chrono_sword";
            case "harpoon", "launcher", "leviathan", "leviathan_launcher", "leviathanlauncher" -> "harpoon_launcher";
            case "hypnosis", "staff", "siren", "sirenscane", "sirens_cane" -> "hypnosis_staff";
            case "emerald", "blade", "verdantfang", "verdant_fang" -> "emerald_blade";
            case "blink", "blinkdagger", "blink_dagger", "blink-dagger", "dagger" -> "blink_dagger";
            case "warden", "wardenblade", "warden_blade", "warden-blade", "sculk_blade" -> "warden_blade";
            case "scythe", "frost", "frostscythe", "frost_scythe" -> "frost_scythe";
            case "rhitta", "divineaxe", "divine_axe", "divine_axe_rhitta", "divine-axe-rhitta", "rhitta_axe" -> "divine_axe_rhitta";
            case "percy", "tridentofpercy", "trident_of_percy", "trident-of-percy", "percys_trident", "percy_trident" -> "trident_of_percy";
            case "warpick", "pick", "siegebreaker", "siegebreaker_pick" -> "war_pick";
            case "magnet", "faraday", "faradays_magnet", "faradays" -> "faradays_magnet";
            case "wind", "windcannon", "cannon", "wind_charge_cannon", "tempestcannon", "tempest_cannon" -> "wind_charge_cannon";
            case "executioner", "executionerblade", "executioner_sword", "exec" -> "executioner_blade";
            case "hermes", "hermesboots", "hermes_boots", "boots" -> "hermes_boots";
            case "wither", "witherblade", "wither_sword" -> "wither_blade";
            case "lifestealer", "life_stealer", "life", "stealer", "bloodsword" -> "life_stealer";
            case "midas", "midassword", "midas_sword", "goldsword", "gildedsovereign", "gilded_sovereign" -> "midas_sword";
            case "reaper", "reaperscythe", "reaper_scythe", "reapers_scythe", "soulrender" -> "reapers_scythe";
            case "shadowblade", "shadow_blade", "shadow-blade", "nightfall" -> "shadow_blade";
            case "headhunter", "headhunter_chestpiece", "headhunters_chestpiece", "rage_chestplate", "headhuntersharness", "headhunters_harness" -> "headhunters_chestpiece";
            case "god", "godchestplate", "god_chestplate", "divine_chestplate", "aegis", "aegis_of_the_undying", "aegisoftheundying" -> "god_chestplate";
            case "strength", "strengthsword", "strength_sword", "domain_blade", "crimsondominion", "crimson_dominion" -> "strength_sword";
            case "thor", "hammer", "thors", "mjolnir", "thors_hammer" -> "thors_hammer";
            case "hardhitter", "hard_hitter", "hard hitter", "titanbreaker" -> "hard_hitter";
            default -> normalized;
        };
    }

    public ItemStack createLegendaryById(String id) {
        LegendaryType type = LegendaryType.fromId(normalizeLegendaryId(id));
        if (type == null) return null;
        return createItem(type);
    }

    public ItemStack createEnderBoneItem() {
        return createEnderBone();
    }

    public ItemStack createOrbOfTheMysticsItem() {
        return createOrbOfTheMystics();
    }

    public ItemStack createFaradaysMagnetItem() {
        return createItem(LegendaryType.FARADAYS_MAGNET);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        ItemStack hand = event.getItem();
        Player player = event.getPlayer();
        Action action = event.getAction();
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;

        if (right && isOrbOfTheMystics(hand)) {
            event.setCancelled(true);
            useOrbOfTheMystics(player);
            return;
        }

        LegendaryType type = typeOf(hand);
        if (type == null) return;

        switch (type) {
            case ENDER_SWORD -> {
                if (!right) return;
                event.setCancelled(true);
                useEnderSword(player);
            }
            case ENDERBOW -> {
                if (!left) return;
                boolean tp = !isEnderbowTpForm(hand);
                setEnderbowForm(hand, tp);
                player.getInventory().setItemInMainHand(hand);
                player.sendMessage(MessageUtil.info("Riftbow form: <white>" + (tp ? "Teleport" : "Arrow") + "</white>."));
            }
            case CHRONO_SWORD -> {
                if (!right) return;
                event.setCancelled(true);
                useChronoSword(player);
            }
            case HARPOON_LAUNCHER -> {
                if (!right) return;
                event.setCancelled(true);
                useHarpoon(player);
            }
            case HYPNOSIS_STAFF -> {
                if (!right) return;
                event.setCancelled(true);
                useHypnosis(player);
            }
            case EMERALD_BLADE -> {
                if (!right || !player.isSneaking()) return;
                event.setCancelled(true);
                feedEmeraldBlade(player, hand);
            }
            case BLINK_DAGGER -> {
                if (!right) return;
                event.setCancelled(true);
                useBlinkDagger(player);
            }
            case WARDEN_BLADE -> {
                if (!right) return;
                event.setCancelled(true);
                useWardenBlade(player);
            }
            case FARADAYS_MAGNET -> {
                if (!right || !player.isSneaking()) return;
                event.setCancelled(true);
                toggleMagnet(player, hand);
                player.getInventory().setItemInMainHand(hand);
            }
            case FROST_SCYTHE -> {
                if (!right) return;
                event.setCancelled(true);
                useFrostScythe(player);
            }
            case DIVINE_AXE_RHITTA -> {
                if (!right) return;
                event.setCancelled(true);
                useDivineAxeRhitta(player);
            }
            case SHADOW_BLADE -> {
                if (!right) return;
                event.setCancelled(true);
                useShadowBlade(player);
            }
            case STRENGTH_SWORD -> {
                if (left && player.isSneaking()) {
                    event.setCancelled(true);
                    useStrengthSwordBeam(player);
                    return;
                }
                if (!right) return;
                event.setCancelled(true);
                useStrengthSwordDomain(player);
            }
            case TRIDENT_OF_PERCY -> {
                if (!right || !player.isSneaking()) return;
                event.setCancelled(true);
                togglePercyTridentMode(player, hand);
                player.getInventory().setItemInMainHand(hand);
            }
            case WIND_CHARGE_CANNON -> {
                if (!left && !right) return;
                event.setCancelled(true);
                useWindChargeCannon(player, hand, left);
                player.getInventory().setItemInMainHand(hand);
            }
            case EXECUTIONER_BLADE -> {
                if (!right) return;
                event.setCancelled(true);
                useExecutionerBlade(player);
            }
            case WITHER_BLADE -> {
                if (!right) return;
                event.setCancelled(true);
                useWitherBlade(player, hand);
                player.getInventory().setItemInMainHand(hand);
            }
            case DASH_MACE -> {
                if (!right) return;
                event.setCancelled(true);
                useDashMace(player);
            }
            case WAR_PICK -> {
                if (!right || !player.isSneaking()) return;
                event.setCancelled(true);
                toggleWarPickMode(player, hand);
                player.getInventory().setItemInMainHand(hand);
            }
            case THORS_HAMMER -> {
                // passive/other event driven
            }
            case STRENGTH_MACE, HARD_HITTER -> {
                // passive/other event driven
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        LegendaryType type = typeOf(hand);
        if (type == null) return;

        switch (type) {
            case ENDER_SWORD -> {
                event.setCancelled(true);
                useEnderSword(player);
            }
            case CHRONO_SWORD -> {
                event.setCancelled(true);
                useChronoSword(player);
            }
            case HARPOON_LAUNCHER -> {
                event.setCancelled(true);
                useHarpoon(player);
            }
            case HYPNOSIS_STAFF -> {
                event.setCancelled(true);
                useHypnosis(player);
            }
            case EMERALD_BLADE -> {
                if (!player.isSneaking()) return;
                event.setCancelled(true);
                feedEmeraldBlade(player, hand);
            }
            case BLINK_DAGGER -> {
                event.setCancelled(true);
                useBlinkDagger(player);
            }
            case WARDEN_BLADE -> {
                event.setCancelled(true);
                useWardenBlade(player);
            }
            case FARADAYS_MAGNET -> {
                if (!player.isSneaking()) return;
                event.setCancelled(true);
                toggleMagnet(player, hand);
                player.getInventory().setItemInMainHand(hand);
            }
            case FROST_SCYTHE -> {
                event.setCancelled(true);
                useFrostScythe(player);
            }
            case DIVINE_AXE_RHITTA -> {
                event.setCancelled(true);
                useDivineAxeRhitta(player);
            }
            case SHADOW_BLADE -> {
                event.setCancelled(true);
                useShadowBlade(player);
            }
            case STRENGTH_SWORD -> {
                event.setCancelled(true);
                useStrengthSwordDomain(player);
            }
            case TRIDENT_OF_PERCY -> {
                if (!player.isSneaking()) return;
                event.setCancelled(true);
                togglePercyTridentMode(player, hand);
                player.getInventory().setItemInMainHand(hand);
            }
            case EXECUTIONER_BLADE -> {
                event.setCancelled(true);
                useExecutionerBlade(player);
            }
            case WITHER_BLADE -> {
                event.setCancelled(true);
                useWitherBlade(player, hand);
                player.getInventory().setItemInMainHand(hand);
            }
            case DASH_MACE -> {
                event.setCancelled(true);
                useDashMace(player);
            }
            case WAR_PICK -> {
                if (!player.isSneaking()) return;
                event.setCancelled(true);
                toggleWarPickMode(player, hand);
                player.getInventory().setItemInMainHand(hand);
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack bow = event.getBow();
        if (typeOf(bow) != LegendaryType.ENDERBOW) return;

        if (isEnderbowTpForm(bow)) {
            if (onCooldown(enderbowCd, player.getUniqueId())) {
                player.sendMessage(MessageUtil.warn("Teleport Form cooldown: <white>" + secondsLeft(enderbowCd, player.getUniqueId()) + "s</white>."));
                event.setCancelled(true);
                if (event.getProjectile() != null) event.getProjectile().remove();
                return;
            }
            event.setCancelled(true);
            if (event.getProjectile() != null) event.getProjectile().remove();
            EnderPearl pearl = player.launchProjectile(EnderPearl.class);
            pearl.setVelocity(player.getLocation().getDirection().normalize().multiply(Math.max(1.6, 2.6 * event.getForce())));
            setCooldown(enderbowCd, player.getUniqueId(), ENDERBOW_TP_COOLDOWN);
            return;
        }

        if (event.getForce() >= 0.99 && event.getProjectile() instanceof Arrow arrow) {
            arrow.getPersistentDataContainer().set(keyEnderbowTag, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof WitherSkull skull) {
            PersistentDataContainer skullPdc = skull.getPersistentDataContainer();
            if (skullPdc.has(keyWitherBladeSkullTag, PersistentDataType.BYTE)) {
                handleWitherBladeSkullHit(event, skull);
                return;
            }
        }

        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        PersistentDataContainer pdc = arrow.getPersistentDataContainer();

        if (pdc.has(keyHarpoonTag, PersistentDataType.BYTE)) {
            handleHarpoonHit(event, arrow);
            return;
        }

        if (!pdc.has(keyEnderbowTag, PersistentDataType.BYTE)) return;
        if (!(event.getHitEntity() instanceof Player victim)) return;
        Endermite mite = victim.getWorld().spawn(victim.getLocation(), Endermite.class);
        mite.setTarget(victim);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (typeOf(player.getInventory().getBoots()) != LegendaryType.HERMES_BOOTS) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLethalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ChronoState state = chronoStates.get(player.getUniqueId());
        if (state != null && player.getHealth() - event.getFinalDamage() <= 0.0) {
            event.setCancelled(true);
            activateChrono(player, true);
            return;
        }
        tryGodChestplateRescue(player, event);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        rhittaBurns.remove(event.getEntity().getUniqueId());
        if (event.getEntity() instanceof Player player) {
            blinkDaggerStunnedUntil.remove(player.getUniqueId());
            shadowBladeActiveUntil.remove(player.getUniqueId());
            headhunterRage.remove(player.getUniqueId());
            activeStrengthDomains.remove(player.getUniqueId());
            clearPlayerLegendaryAttributeModifiers(player);
            clearLifeStealerEffects(player);
        }
        if (event.getEntity() instanceof EnderDragon dragon) {
            UUID ownerId = enderDragonOwnerByDragon.remove(dragon.getUniqueId());
            if (ownerId != null) {
                enderDragonByOwner.remove(ownerId, dragon.getUniqueId());
                removeEnderDragonSeat(ownerId);
                activeEnderDragonRiders.remove(ownerId);
                clearEnderDragonChunkTickets(ownerId);
                event.getDrops().clear();
                event.setDroppedExp(0);
                return;
            }

            ItemStack enderBoneDrop = createEnderBone().asQuantity(plugin.getConfigManager().enderBoneDropCount);
            Player killer = dragon.getKiller();
            if (killer != null && plugin.getItemAuditManager() != null) {
                plugin.getItemAuditManager().recordKnownAcquisition(
                    killer,
                    enderBoneDrop,
                    "ender_dragon_drop",
                    "Dropped from the Ender Dragon."
                );
            }
            event.getDrops().add(enderBoneDrop);
        }

        UUID mobId = event.getEntity().getUniqueId();
        UUID owner = ownerByMob.remove(mobId);
        if (owner != null) {
            Set<UUID> set = controlledByOwner.get(owner);
            if (set != null) {
                set.remove(mobId);
                if (set.isEmpty()) controlledByOwner.remove(owner);
            }
        }

        Player killer = event.getEntity().getKiller();
        if (event.getEntity() instanceof Enderman && killer != null
            && ThreadLocalRandom.current().nextDouble() < ORB_OF_THE_MYSTICS_DROP_CHANCE) {
            ItemStack orbDrop = createOrbOfTheMystics();
            if (plugin.getItemAuditManager() != null) {
                plugin.getItemAuditManager().recordKnownAcquisition(
                    killer,
                    orbDrop,
                    "enderman_drop",
                    "Dropped from an Enderman kill."
                );
            }
            event.getDrops().add(orbDrop);
        }
        if (killer == null) return;
        if ((event.getEntity().getType() == org.bukkit.entity.EntityType.PIGLIN
            || event.getEntity().getType() == org.bukkit.entity.EntityType.PIGLIN_BRUTE)
            && ThreadLocalRandom.current().nextDouble() <= 0.10) {
            event.getDrops().add(new ItemStack(Material.PIGLIN_HEAD));
        }
        LegendaryType killerWeapon = typeOf(killer.getInventory().getItemInMainHand());
        if (killerWeapon == LegendaryType.MIDAS_SWORD) {
            event.getDrops().add(new ItemStack(Material.GOLD_NUGGET, MIDAS_SWORD_GOLD_NUGGETS));
            if (event.getEntity() instanceof Player) {
                levelMidasSword(killer.getInventory().getItemInMainHand(), true);
            }
        }
        if (killerWeapon == LegendaryType.LIFE_STEALER && event.getEntity() instanceof Player playerVictim) {
            grantLifeStealerKillBuff(killer, killer.getInventory().getItemInMainHand(), playerVictim.getUniqueId());
        }
        if (killerWeapon == LegendaryType.STRENGTH_SWORD && event.getEntity() instanceof Player playerVictim) {
            grantStrengthSwordKillProgress(killer.getInventory().getItemInMainHand(), playerVictim.getUniqueId());
        }
        if (killerWeapon != LegendaryType.EMERALD_BLADE) return;
        int amount = event.getEntity() instanceof Player ? 5 : ThreadLocalRandom.current().nextInt(1, 4);
        event.getDrops().add(new ItemStack(Material.EMERALD, amount));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMobTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        UUID owner = ownerByMob.get(mob.getUniqueId());
        if (owner == null) {
            if (event.getTarget() instanceof Player target
                && (mob.getType() == org.bukkit.entity.EntityType.PIGLIN || mob.getType() == org.bukkit.entity.EntityType.PIGLIN_BRUTE)
                && hasLegendaryInHands(target, LegendaryType.MIDAS_SWORD)) {
                event.setCancelled(true);
                mob.setTarget(null);
            }
            return;
        }
        if (event.getTarget() instanceof Player target && sameTeamOrSelf(owner, target.getUniqueId())) {
            event.setCancelled(true);
            mob.setTarget(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (handleOwnedEnderDragonAttack(event.getDamager(), event.getEntity())) {
            event.setCancelled(true);
            return;
        }
        if (event.getDamager() instanceof WitherSkull skull) {
            PersistentDataContainer skullPdc = skull.getPersistentDataContainer();
            if (skullPdc.has(keyWitherBladeSkullTag, PersistentDataType.BYTE)) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getDamager() instanceof Trident trident && isPercyTridentProjectile(trident)) {
            event.setDamage(event.getDamage() + PERCY_TRIDENT_DAMAGE_BONUS);
        }

        Player attacker = resolvePlayerAttacker(event.getDamager());
        if (attacker == null) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        if (event.getFinalDamage() > 0.0 && victim instanceof Player targetPlayer && !attacker.equals(targetPlayer)) {
            dismountEnderDragonForCombat(attacker);
            dismountEnderDragonForCombat(targetPlayer);
        }

        directControlledMobs(attacker, victim);

        LegendaryType held = typeOf(attacker.getInventory().getItemInMainHand());
        if (held == LegendaryType.ENDER_SWORD) {
            event.setDamage(ENDER_SWORD_MELEE_DAMAGE);
        } else if (held == LegendaryType.WITHER_BLADE) {
            refreshWitherBladeLore(attacker);
            double bonus = witherBladeBonusDamage(attacker);
            if (bonus > 0.0) {
                event.setDamage(event.getDamage() + bonus);
            }
        } else if (held == LegendaryType.MIDAS_SWORD) {
            event.setDamage(MIDAS_SWORD_MELEE_DAMAGE);
        } else if (held == LegendaryType.WARDEN_BLADE) {
            event.setDamage(WARDEN_BLADE_MELEE_DAMAGE);
        } else if (held == LegendaryType.FROST_SCYTHE) {
            event.setDamage(FROST_SCYTHE_MELEE_DAMAGE);
            applyFrostScytheNausea(victim);
            if (frostScytheSweepPlayers.add(attacker.getUniqueId())) {
                try {
                    sweepFrostScytheTargets(attacker, victim);
                } finally {
                    frostScytheSweepPlayers.remove(attacker.getUniqueId());
                }
            }
        } else if (held == LegendaryType.BLINK_DAGGER) {
            tryBlinkDaggerBackstab(attacker, victim);
        } else if (held == LegendaryType.TRIDENT_OF_PERCY) {
            event.setDamage(event.getDamage() + PERCY_TRIDENT_DAMAGE_BONUS);
        } else if (held == LegendaryType.DIVINE_AXE_RHITTA) {
            event.setDamage(RHITTA_MELEE_DAMAGE);
        } else if (held == LegendaryType.REAPERS_SCYTHE) {
            event.setDamage(REAPER_SCYTHE_MELEE_DAMAGE);
            tryReaperScytheDrain(attacker, victim);
        } else if (held == LegendaryType.SHADOW_BLADE) {
            event.setDamage(shadowBladeDamage(attacker));
            if (victim instanceof Player) {
                cancelShadowBlade(attacker, true);
            }
        } else if (held == LegendaryType.EXECUTIONER_BLADE) {
            event.setDamage(event.getDamage() + executionerBladeBonusDamage(attacker));
        } else if (held == LegendaryType.LIFE_STEALER) {
            refreshLifeStealerLore(attacker);
        } else if (held == LegendaryType.STRENGTH_SWORD) {
            applyStrengthSwordStageHit(attacker, victim);
        } else if (held == LegendaryType.STRENGTH_MACE) {
            event.setDamage(event.getDamage() + STRENGTH_MACE_DAMAGE_BONUS);
        } else if (held == LegendaryType.PARADOX_REAVER) {
            event.setDamage(14.0);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 70, 1, false, true, true));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 70, 0, false, true, true));
            victim.getWorld().spawnParticle(Particle.REVERSE_PORTAL, victim.getLocation().add(0.0, 1.0, 0.0), 22, 0.35, 0.45, 0.35, 0.08);
        } else if (held == LegendaryType.TEMPEST_TRIDENT) {
            double bonus = (attacker.isInWater() || attacker.isInRain()) ? 9.0 : 7.0;
            event.setDamage(event.getDamage() + bonus);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, false, true, true));
            if (attacker.isInWater() || attacker.isInRain() || attacker.getWorld().hasStorm()) {
                victim.getWorld().strikeLightningEffect(victim.getLocation());
            } else {
                victim.getWorld().spawnParticle(Particle.SPLASH, victim.getLocation().add(0.0, 1.0, 0.0), 18, 0.35, 0.35, 0.35, 0.05);
            }
        } else if (held == LegendaryType.STORMFALL_MAUL) {
            event.setDamage(event.getDamage() + 6.0);
            Vector push = victim.getLocation().toVector().subtract(attacker.getLocation().toVector()).setY(0.0);
            if (push.lengthSquared() > 1.0E-6) {
                victim.setVelocity(victim.getVelocity().add(push.normalize().multiply(0.75).setY(0.35)));
            }
            victim.getWorld().spawnParticle(Particle.SONIC_BOOM, victim.getLocation().add(0.0, 1.0, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
            for (LivingEntity nearby : victim.getWorld().getNearbyLivingEntities(victim.getLocation(), 4.5)) {
                if (nearby.equals(victim) || nearby.equals(attacker)) continue;
                if (nearby instanceof Player player && sameTeamOrSelf(attacker.getUniqueId(), player.getUniqueId())) continue;
                Vector wave = nearby.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0.0);
                if (wave.lengthSquared() > 1.0E-6) {
                    nearby.setVelocity(nearby.getVelocity().add(wave.normalize().multiply(0.45).setY(0.22)));
                }
                nearby.damage(2.0, attacker);
            }
        }

        if (held == LegendaryType.THORS_HAMMER) {
            victim.getWorld().strikeLightningEffect(victim.getLocation());
            event.setDamage(event.getDamage() + plugin.getConfigManager().thorsHammerBonusDamage);
            tryThorsHammerThunderStrike(attacker, victim);
        }

        if (!(victim instanceof Player targetPlayer)) return;
        addHeadhunterRage(attacker, true);
        addHeadhunterRage(targetPlayer, false);
        if (held != LegendaryType.WAR_PICK) return;
        if (!isCritical(attacker)) return;
        if (ThreadLocalRandom.current().nextDouble() > 0.20) return;

        Vector kb = targetPlayer.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize().multiply(1.2).setY(0.42);
        targetPlayer.setVelocity(kb);
        damageArmorPiece(targetPlayer);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLifeStealerConfirmedHit(EntityDamageByEntityEvent event) {
        if (event.getFinalDamage() <= 0.0) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (typeOf(attacker.getInventory().getItemInMainHand()) != LegendaryType.LIFE_STEALER) {
            return;
        }

        healLivingEntity(attacker, LIFE_STEALER_HIT_HEAL);
        refreshLifeStealerLore(attacker);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOwnedEnderDragonDamage(EntityDamageEvent event) {
        if (!isOwnedEnderDragon(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOwnedEnderDragonChangeBlock(EntityChangeBlockEvent event) {
        if (!isOwnedEnderDragon(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOwnedEnderDragonExplode(EntityExplodeEvent event) {
        if (!isOwnedEnderDragon(event.getEntity())) {
            return;
        }
        event.blockList().clear();
        event.setYield(0.0f);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (typeOf(heldItem) != LegendaryType.WAR_PICK) return;
        WarPickMode mode = warPickMode(heldItem);
        UUID playerId = player.getUniqueId();
        // Prevent recursive AoE chains when breaking adjacent blocks below.
        if (!warPickAoePlayers.add(playerId)) return;

        try {
            Block center = event.getBlock();
            World world = center.getWorld();
            world.createExplosion(center.getLocation().add(0.5, 0.5, 0.5), 2.0f, false, false, player);

            for (Block target : warPickTargets(player, center, mode)) {
                if (target.equals(center)) continue;
                if (target.getType().isAir()) continue;
                if (isProtected(target.getType())) continue;
                if (plugin.getVeinMinerListener() != null) {
                    plugin.getVeinMinerListener().suppressNextBreak(target.getLocation());
                }
                // Use the normal player-break path so protection plugins can cancel it.
                player.breakBlock(target);
            }
        } finally {
            warPickAoePlayers.remove(playerId);
        }
    }

    private void openRecipeDetails(Player player, LegendaryType type) {
        Inventory inv = Bukkit.createInventory(
            new RecipeMenuHolder(type),
            27,
            BedrockCompat.menuTitle(
                player,
                MM.deserialize(GUI_TITLE_PREFIX_RECIPE + type.display),
                "Recipe: " + PLAIN.serialize(MM.deserialize(type.display)).trim()
            )
        );

        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        int[] matrixSlots = {3, 4, 5, 12, 13, 14, 21, 22, 23};
        int index = 0;
        Map<Material, Integer> ingredients = ingredientsFor(type);
        for (Map.Entry<Material, Integer> entry : ingredients.entrySet()) {
            if (index >= matrixSlots.length) break;
            ItemStack ingredientItem = displayRecipeIngredient(type, entry.getKey(), entry.getValue());
            inv.setItem(matrixSlots[index++], ingredientItem);
        }

        inv.setItem(16, createDisplayLegendaryItem(type, true));
        inv.setItem(RECIPE_TRADE_SLOT, createTradeButton(player, type));
        inv.setItem(18, createGuiItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to the Reliquary</gray>")));
        player.openInventory(inv);
    }

    private void openBackpackRecipeDetails(Player player, String recipeId) {
        boolean upgraded = EXPANDED_BACKPACK_RECIPE_ID.equals(recipeId);
        String displayName = upgraded ? "Expanded Backpack" : "Backpack";
        CustomLoreUtil.Rarity rarity = upgraded ? CustomLoreUtil.Rarity.RARE : CustomLoreUtil.Rarity.UNCOMMON;
        Inventory inv = Bukkit.createInventory(
            new BackpackRecipeHolder(recipeId),
            27,
            BedrockCompat.menuTitle(
                player,
                MM.deserialize(GUI_TITLE_PREFIX_RECIPE + CustomLoreUtil.displayNameTag(rarity, displayName)),
                "Recipe: " + displayName
            )
        );

        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        int[] matrixSlots = {3, 4, 5, 12, 13, 14, 21, 22, 23};
        Map<Material, Integer> backpackIngredients = plugin.getBackpackListener() == null
            ? (upgraded ? Map.of(Material.LEATHER, 16, Material.DIAMOND, 8) : Map.of(Material.LEATHER, 4, Material.STRING, 4, Material.CHEST, 1))
            : (upgraded ? plugin.getBackpackListener().upgradedTradeIngredients() : plugin.getBackpackListener().tradeIngredients());
        List<Map.Entry<Material, Integer>> ingredients = new ArrayList<>(backpackIngredients.entrySet());

        int index = 0;
        if (upgraded) {
            inv.setItem(matrixSlots[index++], createBackpackRecipeDisplayItem(false));
        }
        for (Map.Entry<Material, Integer> entry : ingredients) {
            if (index >= matrixSlots.length) break;
            ItemStack ingredientItem = new ItemStack(entry.getKey(), Math.min(64, entry.getValue()));
            ItemMeta meta = ingredientItem.getItemMeta();
            if (meta != null) {
                meta.lore(List.of(MM.deserialize("<gray>Required: <white>x" + entry.getValue() + "</white>")));
                ingredientItem.setItemMeta(meta);
            }
            inv.setItem(matrixSlots[index++], ingredientItem);
        }

        inv.setItem(16, createBackpackRecipeDisplayItem(upgraded));
        boolean canTrade = plugin.getBackpackListener() != null
            && (upgraded ? plugin.getBackpackListener().canTradeUpgradedBackpack(player) : plugin.getBackpackListener().canTradeBackpack(player));
        inv.setItem(RECIPE_TRADE_SLOT, createTradeButton(
            player,
            CustomLoreUtil.displayNameTag(rarity, displayName),
            backpackIngredients,
            canTrade
        ));
        inv.setItem(18, createGuiItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to the Reliquary</gray>")));
        player.openInventory(inv);
    }

    private void openAncientScrollRecipeDetails(Player player) {
        if (plugin.getSuperpowerManager() == null) {
            player.sendMessage(MessageUtil.error("Ancient Scroll recipes are not ready yet."));
            return;
        }

        Inventory inv = Bukkit.createInventory(
            new AncientScrollRecipeHolder(),
            27,
            BedrockCompat.menuTitle(
                player,
                MM.deserialize(GUI_TITLE_PREFIX_RECIPE + CustomLoreUtil.displayNameTag(CustomLoreUtil.Rarity.EPIC, "Ancient Scroll")),
                "Recipe: Ancient Scroll"
            )
        );

        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        int[] matrixSlots = {4, 12, 14};
        int[] requiredTotals = {1, 2, 1};
        List<ItemStack> ingredients = plugin.getSuperpowerManager().ancientScrollRecipeDisplayItems();
        for (int i = 0; i < matrixSlots.length && i < ingredients.size(); i++) {
            inv.setItem(matrixSlots[i], addRequiredTotalLore(ingredients.get(i), requiredTotals[i]));
        }

        inv.setItem(16, plugin.getSuperpowerManager().createAncientScrollItem());
        inv.setItem(RECIPE_TRADE_SLOT, createInventoryCraftButton("Uses the exact total item counts shown here."));
        inv.setItem(18, createGuiItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to the Reliquary</gray>")));
        player.openInventory(inv);
    }

    private void openAscendantCoreRecipeDetails(Player player) {
        MythicForgeListener forge = plugin.getMythicForgeListener();
        if (forge == null) {
            player.sendMessage(MessageUtil.error("Mythic forge recipes are not ready yet."));
            return;
        }

        Inventory inv = Bukkit.createInventory(
            new AscendantCoreRecipeHolder(),
            27,
            BedrockCompat.menuTitle(
                player,
                MM.deserialize(GUI_TITLE_PREFIX_RECIPE + CustomLoreUtil.displayNameTag(CustomLoreUtil.Rarity.LEGENDARY, "Ascendant Core")),
                "Recipe: Ascendant Core"
            )
        );

        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        int[] matrixSlots = {3, 4, 5, 12, 13, 14, 21, 22, 23};
        ItemStack[] recipeMatrix = forge.recipeMatrix(MythicForgeListener.ASCENDANT_CORE_ITEM_ID);
        for (int i = 0; i < matrixSlots.length && i < recipeMatrix.length; i++) {
            ItemStack ingredient = recipeMatrix[i];
            if (ingredient == null || ingredient.getType() == Material.AIR) {
                continue;
            }
            inv.setItem(matrixSlots[i], ingredient);
        }

        inv.setItem(16, forge.createAscendantCoreItem());
        inv.setItem(RECIPE_TRADE_SLOT, createInventoryCraftButton("Use it as the catalyst in a <white>Mythic Forge</white>."));
        inv.setItem(18, createGuiItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to the Reliquary</gray>")));
        player.openInventory(inv);
    }

    private void openMythicForgeRecipeDetails(Player player) {
        MythicForgeListener forge = plugin.getMythicForgeListener();
        if (forge == null) {
            player.sendMessage(MessageUtil.error("Mythic forge recipes are not ready yet."));
            return;
        }

        Inventory inv = Bukkit.createInventory(
            new MythicForgeRecipeHolder(),
            54,
            BedrockCompat.menuTitle(
                player,
                MM.deserialize(GUI_TITLE_PREFIX_RECIPE + CustomLoreUtil.displayNameTag(CustomLoreUtil.Rarity.MYTHIC, "Mythic Forge")),
                "Recipe: Mythic Forge"
            )
        );

        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        int[] matrixSlots = {3, 4, 5, 12, 13, 14, 21, 22, 23};
        ItemStack[] recipeMatrix = forge.recipeMatrix(MythicForgeListener.MYTHIC_FORGE_ITEM_ID);
        for (int i = 0; i < matrixSlots.length && i < recipeMatrix.length; i++) {
            ItemStack ingredient = recipeMatrix[i];
            if (ingredient == null || ingredient.getType() == Material.AIR) {
                continue;
            }
            inv.setItem(matrixSlots[i], ingredient);
        }

        inv.setItem(16, forge.createMythicForgeItem());
        inv.setItem(RECIPE_TRADE_SLOT, createInventoryCraftButton("Place it, then combine <white>2 compatible legendaries</white> with an <white>Ascendant Core</white>."));
        inv.setItem(30, forge.createAscendantCoreItem());
        inv.setItem(31, createGuiItem(
            Material.AMETHYST_CLUSTER,
            "<light_purple><bold>Fusion Catalyst</bold></light_purple>",
            List.of(
                "<gray>Every mythic fusion consumes</gray>",
                "<gray><white>1 Ascendant Core</white>.</gray>"
            )
        ));
        inv.setItem(40, createGuiItem(
            Material.NETHER_STAR,
            "<gradient:#ff4df0:#ffb000><bold>Mythic Nexus</bold></gradient>",
            List.of(
                "<gray>Fusion rewards live in their own menu now.</gray>",
                "<gray>Use <white>/mythics</white> or click here to view every pairing.</gray>"
            )
        ));

        inv.setItem(45, createGuiItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to the Reliquary</gray>")));
        player.openInventory(inv);
    }

    private ItemStack createFusionRecipePreview(Player player, MythicForgeListener.FusionRecipeView recipe) {
        LegendaryType outputType = LegendaryType.fromId(normalizeLegendaryId(recipe.outputId()));
        if (outputType == null) {
            return createGuiItem(Material.BARRIER, "<red>Unavailable</red>", List.of());
        }

        ItemStack preview = createDisplayLegendaryItem(outputType, true);
        ItemMeta meta = preview.getItemMeta();
        if (meta == null) {
            return preview;
        }

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(MM.deserialize("<gold><bold>Mythic Fusion</bold></gold>"));
        lore.add(MM.deserialize("<gray>Left:</gray> <white>" + displayNameForLegendary(recipe.leftId()) + "</white>"));
        lore.add(MM.deserialize("<gray>Right:</gray> <white>" + displayNameForLegendary(recipe.rightId()) + "</white>"));
        lore.add(MM.deserialize("<gray>Catalyst:</gray> <white>Ascendant Core</white>"));
        lore.add(MM.deserialize("<dark_gray>" + BedrockCompat.menuActionWord(player) + " to view exact fusion steps</dark_gray>"));
        meta.lore(lore);
        preview.setItemMeta(meta);
        return preview;
    }

    private void openMythicFusionRecipeDetails(Player player, MythicForgeListener.FusionRecipeView recipe) {
        MythicForgeListener forge = plugin.getMythicForgeListener();
        if (forge == null || recipe == null) {
            player.sendMessage(MessageUtil.error("Mythic fusion recipes are not ready yet."));
            return;
        }

        LegendaryType leftType = LegendaryType.fromId(normalizeLegendaryId(recipe.leftId()));
        LegendaryType rightType = LegendaryType.fromId(normalizeLegendaryId(recipe.rightId()));
        LegendaryType outputType = LegendaryType.fromId(normalizeLegendaryId(recipe.outputId()));
        if (leftType == null || rightType == null || outputType == null) {
            player.sendMessage(MessageUtil.error("That mythic fusion recipe is not available right now."));
            return;
        }

        Inventory inv = Bukkit.createInventory(
            new MythicFusionRecipeHolder(recipe.outputId()),
            54,
            BedrockCompat.menuTitle(
                player,
                MM.deserialize(GUI_TITLE_PREFIX_RECIPE + CustomLoreUtil.displayNameTag(CustomLoreUtil.Rarity.MYTHIC, displayNameForLegendary(recipe.outputId()))),
                "Mythic Fusion"
            )
        );

        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        inv.setItem(4, createGuiItem(
            Material.NETHER_STAR,
            "<gradient:#ff4df0:#ffb000><bold>Mythic Fusion</bold></gradient>",
            List.of(
                "<gray>Use a placed <white>Mythic Forge</white>.</gray>",
                "<gray>Put the two source relics on the sides.</gray>",
                "<gray>Put an <white>Ascendant Core</white> in the center.</gray>"
            )
        ));
        inv.setItem(10, createDisplayLegendaryItem(leftType, true));
        inv.setItem(13, forge.createAscendantCoreItem());
        inv.setItem(16, createDisplayLegendaryItem(rightType, true));
        inv.setItem(22, createDisplayLegendaryItem(outputType, true));
        inv.setItem(28, forge.createMythicForgeItem());
        inv.setItem(31, createGuiItem(
            Material.CRAFTING_TABLE,
            "<gold><bold>How To Forge</bold></gold>",
            List.of(
                "<gray>1. Craft and place a <white>Mythic Forge</white>.</gray>",
                "<gray>2. Open it and insert both source relics.</gray>",
                "<gray>3. Add an <white>Ascendant Core</white>.</gray>",
                "<gray>4. Click the result slot to claim the mythic.</gray>"
            )
        ));
        inv.setItem(34, createGuiItem(
            Material.LODESTONE,
            "<red><bold>One-Of-One Warning</bold></red>",
            List.of(
                "<gray>The mythic output is unique to the server.</gray>",
                "<gray>The two source relics are retired from</gray>",
                "<gray>future altar rolls once the fusion succeeds.</gray>"
            )
        ));
        inv.setItem(49, createGuiItem(
            Material.ARROW,
            "<yellow>Back</yellow>",
            List.of("<gray>Return to Mythic Nexus</gray>")
        ));
        player.openInventory(inv);
    }

    private void openCustomToolRecipeDetails(Player player, String toolId) {
        if (plugin.getCustomToolListener() == null) {
            player.sendMessage(MessageUtil.error("Custom tool recipes are not ready yet."));
            return;
        }

        String displayName = plugin.getCustomToolListener().displayNameFor(toolId);
        if (displayName == null) {
            player.sendMessage(MessageUtil.error("Unknown custom tool recipe."));
            return;
        }

        Inventory inv = Bukkit.createInventory(
            new CustomToolRecipeHolder(toolId),
            27,
            BedrockCompat.menuTitle(
                player,
                MM.deserialize(GUI_TITLE_PREFIX_RECIPE + CustomLoreUtil.displayNameTag(customToolRarity(toolId), displayName)),
                "Recipe: " + displayName
            )
        );

        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        int[] matrixSlots = {3, 4, 5, 12, 13, 14, 21, 22, 23};
        ItemStack[] recipeMatrix = plugin.getCustomToolListener().recipeMatrix(toolId);
        for (int i = 0; i < matrixSlots.length && i < recipeMatrix.length; i++) {
            ItemStack ingredient = recipeMatrix[i];
            if (ingredient == null || ingredient.getType() == Material.AIR) {
                continue;
            }
            inv.setItem(matrixSlots[i], ingredient);
        }

        inv.setItem(16, plugin.getCustomToolListener().createRecipePreview(toolId));
        inv.setItem(RECIPE_TRADE_SLOT, createInventoryCraftButton("Uses the shown vanilla ingredients."));
        inv.setItem(18, createGuiItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to the Reliquary</gray>")));
        player.openInventory(inv);
    }

    private void openFaradaysMagnetRecipeDetails(Player player) {
        Inventory inv = Bukkit.createInventory(
            new FaradaysMagnetRecipeHolder(),
            27,
            BedrockCompat.menuTitle(
                player,
                MM.deserialize(GUI_TITLE_PREFIX_RECIPE + LegendaryType.FARADAYS_MAGNET.display),
                "Recipe: Faraday's Magnet"
            )
        );

        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        int[] matrixSlots = {3, 4, 5, 12, 13, 14, 21, 22, 23};
        ItemStack[] recipeMatrix = faradaysMagnetRecipeMatrix();
        for (int i = 0; i < matrixSlots.length && i < recipeMatrix.length; i++) {
            ItemStack ingredient = recipeMatrix[i];
            if (ingredient == null || ingredient.getType() == Material.AIR) {
                continue;
            }
            inv.setItem(matrixSlots[i], ingredient);
        }

        inv.setItem(16, createFaradaysMagnetItem());
        inv.setItem(RECIPE_TRADE_SLOT, createInventoryCraftButton("Uses the shown vanilla ingredients."));
        inv.setItem(18, createGuiItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to the Reliquary</gray>")));
        player.openInventory(inv);
    }

    private void openSustenanceTalismanRecipeDetails(Player player) {
        if (plugin.getSustenanceTalismanListener() == null) {
            player.sendMessage(MessageUtil.error("Talisman recipes are not ready yet."));
            return;
        }

        Inventory inv = Bukkit.createInventory(
            new TalismanRecipeHolder(),
            27,
            BedrockCompat.menuTitle(
                player,
                MM.deserialize(GUI_TITLE_PREFIX_RECIPE + CustomLoreUtil.displayNameTag(CustomLoreUtil.Rarity.EPIC, "Talisman of Sustenance")),
                "Recipe: Talisman of Sustenance"
            )
        );

        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        int[] matrixSlots = {3, 4, 5, 12, 13, 14, 21, 22, 23};
        List<Map.Entry<Material, Integer>> ingredients = new ArrayList<>(plugin.getSustenanceTalismanListener().recipeIngredients().entrySet());
        int index = 0;
        for (Map.Entry<Material, Integer> entry : ingredients) {
            if (index >= matrixSlots.length) break;
            ItemStack ingredientItem = new ItemStack(entry.getKey(), Math.min(64, entry.getValue()));
            ItemMeta meta = ingredientItem.getItemMeta();
            if (meta != null) {
                meta.lore(List.of(MM.deserialize("<gray>Required: <white>x" + entry.getValue() + "</white> total</gray>")));
                ingredientItem.setItemMeta(meta);
            }
            inv.setItem(matrixSlots[index++], ingredientItem);
        }

        inv.setItem(16, plugin.getSustenanceTalismanListener().createTalismanItem());
        inv.setItem(RECIPE_TRADE_SLOT, createInventoryCraftButton("Uses the exact total item counts shown here."));
        inv.setItem(18, createGuiItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to the Reliquary</gray>")));
        player.openInventory(inv);
    }

    private void openSalvagingDepotRecipeDetails(Player player) {
        if (plugin.getSalvagingDepotListener() == null) {
            player.sendMessage(MessageUtil.error("Salvaging Depot recipes are not ready yet."));
            return;
        }

        Inventory inv = Bukkit.createInventory(
            new SalvagingDepotRecipeHolder(),
            27,
            BedrockCompat.menuTitle(
                player,
                MM.deserialize(GUI_TITLE_PREFIX_RECIPE + CustomLoreUtil.displayNameTag(CustomLoreUtil.Rarity.UNCOMMON, "Salvaging Depot")),
                "Recipe: Salvaging Depot"
            )
        );

        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        int[] matrixSlots = {3, 4, 5, 12, 13, 14, 21, 22, 23};
        ItemStack[] recipeMatrix = {
            null,
            new ItemStack(Material.IRON_INGOT),
            null,
            new ItemStack(Material.REDSTONE),
            new ItemStack(Material.CHEST),
            new ItemStack(Material.REDSTONE),
            null,
            new ItemStack(Material.HOPPER),
            null
        };
        for (int i = 0; i < matrixSlots.length && i < recipeMatrix.length; i++) {
            ItemStack ingredient = recipeMatrix[i];
            if (ingredient == null || ingredient.getType() == Material.AIR) {
                continue;
            }
            inv.setItem(matrixSlots[i], ingredient);
        }

        inv.setItem(16, plugin.getSalvagingDepotListener().createDepotItem());
        inv.setItem(RECIPE_TRADE_SLOT, createInventoryCraftButton("Place it as a station, then insert vanilla gear for recycling."));
        inv.setItem(18, createGuiItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to the Reliquary</gray>")));
        player.openInventory(inv);
    }

    private void openAgriculturalPylonRecipeDetails(Player player) {
        if (plugin.getAgriculturalPylonListener() == null) {
            player.sendMessage(MessageUtil.error("Agricultural Pylon recipes are not ready yet."));
            return;
        }

        Inventory inv = Bukkit.createInventory(
            new AgriculturalPylonRecipeHolder(),
            27,
            BedrockCompat.menuTitle(
                player,
                MM.deserialize(GUI_TITLE_PREFIX_RECIPE + CustomLoreUtil.displayNameTag(CustomLoreUtil.Rarity.UNCOMMON, "Agricultural Pylon")),
                "Recipe: Agricultural Pylon"
            )
        );

        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        int[] matrixSlots = {3, 4, 5, 12, 13, 14, 21, 22, 23};
        ItemStack[] recipeMatrix = {
            new ItemStack(Material.BONE_MEAL),
            new ItemStack(Material.WHEAT),
            new ItemStack(Material.BONE_MEAL),
            new ItemStack(Material.COPPER_INGOT),
            new ItemStack(Material.LANTERN),
            new ItemStack(Material.COPPER_INGOT),
            new ItemStack(Material.BONE_MEAL),
            new ItemStack(Material.WHEAT),
            new ItemStack(Material.BONE_MEAL)
        };
        for (int i = 0; i < matrixSlots.length && i < recipeMatrix.length; i++) {
            inv.setItem(matrixSlots[i], recipeMatrix[i]);
        }

        inv.setItem(16, plugin.getAgriculturalPylonListener().createPylonItem());
        inv.setItem(RECIPE_TRADE_SLOT, createInventoryCraftButton("Place it near farms to prevent nearby farmland from being trampled."));
        inv.setItem(18, createGuiItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to the Reliquary</gray>")));
        player.openInventory(inv);
    }

    private void openXpLecternRecipeDetails(Player player) {
        if (plugin.getXpLecternListener() == null) {
            player.sendMessage(MessageUtil.error("XP Lectern recipes are not ready yet."));
            return;
        }

        Inventory inv = Bukkit.createInventory(
            new XpLecternRecipeHolder(),
            27,
            BedrockCompat.menuTitle(
                player,
                MM.deserialize(GUI_TITLE_PREFIX_RECIPE + CustomLoreUtil.displayNameTag(CustomLoreUtil.Rarity.RARE, "XP Lectern")),
                "Recipe: XP Lectern"
            )
        );

        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        int[] matrixSlots = {3, 4, 5, 12, 13, 14, 21, 22, 23};
        ItemStack[] recipeMatrix = {
            null,
            new ItemStack(Material.EXPERIENCE_BOTTLE),
            null,
            new ItemStack(Material.BOOK),
            new ItemStack(Material.LECTERN),
            new ItemStack(Material.BOOK),
            null,
            new ItemStack(Material.REDSTONE),
            null
        };
        for (int i = 0; i < matrixSlots.length && i < recipeMatrix.length; i++) {
            ItemStack ingredient = recipeMatrix[i];
            if (ingredient == null || ingredient.getType() == Material.AIR) {
                continue;
            }
            inv.setItem(matrixSlots[i], ingredient);
        }

        inv.setItem(16, plugin.getXpLecternListener().createLecternItem());
        inv.setItem(RECIPE_TRADE_SLOT, createInventoryCraftButton("Place it as a station, then right-click it to store XP."));
        inv.setItem(18, createGuiItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to the Reliquary</gray>")));
        player.openInventory(inv);
    }

    private void openAwakeningTableInfo(Player player) {
        Inventory inv = Bukkit.createInventory(
            new AwakeningTableInfoHolder(),
            27,
            BedrockCompat.menuTitle(
                player,
                MM.deserialize(GUI_TITLE_PREFIX_RECIPE + CustomLoreUtil.displayNameTag(CustomLoreUtil.Rarity.MYTHIC, "Awakening Table")),
                "Awakening Table"
            )
        );

        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        inv.setItem(11, createAwakeningTablePreview(player));
        inv.setItem(13, createGuiItem(
            Material.END_ROD,
            "<gradient:#8b5cf6:#f0abfc><bold>Aurelion the Rift Seraph</bold></gradient>",
            List.of(
                "<gray>Current player source for Awakening Tables.</gray>",
                "<gray>Ritual works only in <white>The End</white>.</gray>",
                "<gray>Default guide: <white>/bossrituals</white> -> Aurelion.</gray>",
                "<gray>Configured drop chance: <white>" + menuPercent(plugin.getConfigManager().awakeningTableRiftSeraphDropChance) + "</white></gray>"
            )
        ));
        inv.setItem(15, createGuiItem(
            Material.NETHER_STAR,
            "<gradient:#ff8a5b:#ff3d3d><bold>What It Does</bold></gradient>",
            List.of(
                "<gray>Place the table, then right-click it.</gray>",
                "<gray>Use <white>1 Nether Star</white> per awakening attempt.</gray>",
                "<gray>Success chance: <white>" + menuPercent(plugin.getConfigManager().awakeningTableSuccessChance) + "</white></gray>",
                "<gray>Failures damage the item and can shatter weak gear.</gray>"
            )
        ));
        inv.setItem(18, createGuiItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to the Reliquary</gray>")));
        player.openInventory(inv);
    }

    private ItemStack createPreviewItem(Player player, LegendaryType type) {
        return appendRecipeMenuHint(player, createDisplayLegendaryItem(type, true));
    }

    private ItemStack createCustomToolPreview(Player player, String toolId) {
        if (plugin.getCustomToolListener() == null) {
            return createGuiItem(Material.BARRIER, "<red>Unavailable</red>", List.of());
        }

        return appendRecipeMenuHint(player, plugin.getCustomToolListener().createRecipePreview(toolId));
    }

    private ItemStack createMythicNexusPreview(Player player) {
        return createGuiItem(
            Material.NETHER_STAR,
            "<gradient:#ff4df0:#ffb000><bold>Mythic Nexus</bold></gradient>",
            List.of(
                "<gray>View every Mythic Forge fusion pairing.</gray>",
                "<gray>Use <white>/mythics</white> to open it directly.</gray>",
                "<dark_gray>" + BedrockCompat.menuActionWord(player) + " to view fusions</dark_gray>"
            )
        );
    }

    private CustomLoreUtil.Rarity customToolRarity(String toolId) {
        return switch (toolId) {
            case CustomToolListener.ADVANCED_PICKAXE_ID,
                 CustomToolListener.GRAPPLE_HOOK_ID,
                 CustomToolListener.SURVEYORS_LENS_ID -> CustomLoreUtil.Rarity.RARE;
            case CustomToolListener.SPELUNKER_LANTERN_ID,
                 CustomToolListener.MENDERS_KIT_ID -> CustomLoreUtil.Rarity.UNCOMMON;
            default -> CustomLoreUtil.Rarity.COMMON;
        };
    }

    private ItemStack createFaradaysMagnetPreview(Player player) {
        return appendRecipeMenuHint(player, createDisplayLegendaryItem(LegendaryType.FARADAYS_MAGNET, true));
    }

    private ItemStack createSustenanceTalismanPreview(Player player) {
        if (plugin.getSustenanceTalismanListener() == null) {
            return createGuiItem(Material.BARRIER, "<red>Unavailable</red>", List.of());
        }

        return appendRecipeMenuHint(player, plugin.getSustenanceTalismanListener().createTalismanItem());
    }

    private ItemStack createSalvagingDepotPreview(Player player) {
        if (plugin.getSalvagingDepotListener() == null) {
            return createGuiItem(Material.BARRIER, "<red>Unavailable</red>", List.of());
        }

        return appendRecipeMenuHint(player, plugin.getSalvagingDepotListener().createDepotItem());
    }

    private ItemStack createAgriculturalPylonPreview(Player player) {
        if (plugin.getAgriculturalPylonListener() == null) {
            return createGuiItem(Material.BARRIER, "<red>Unavailable</red>", List.of());
        }

        return appendRecipeMenuHint(player, plugin.getAgriculturalPylonListener().createPylonItem());
    }

    private ItemStack createXpLecternPreview(Player player) {
        if (plugin.getXpLecternListener() == null) {
            return createGuiItem(Material.BARRIER, "<red>Unavailable</red>", List.of());
        }

        return appendRecipeMenuHint(player, plugin.getXpLecternListener().createLecternItem());
    }

    private ItemStack createAwakeningTablePreview(Player player) {
        if (plugin.getAwakeningTableListener() == null) {
            return createGuiItem(Material.BARRIER, "<red>Unavailable</red>", List.of());
        }

        return createGuiItem(
            Material.ENCHANTING_TABLE,
            CustomLoreUtil.displayNameTag(CustomLoreUtil.Rarity.MYTHIC, "Awakening Table"),
            List.of(
                "<gray>Dropped by <white>Aurelion the Rift Seraph</white>.</gray>",
                "<gray>Drop chance: <white>" + menuPercent(plugin.getConfigManager().awakeningTableRiftSeraphDropChance) + "</white></gray>",
                "<gray>Use it to awaken armor, tools, weapons,</gray>",
                "<gray>and special supported relics.</gray>",
                "<dark_gray>" + BedrockCompat.menuActionWord(player) + " to view source and usage</dark_gray>"
            )
        );
    }

    private ItemStack createAncientScrollPreview(Player player) {
        if (plugin.getSuperpowerManager() == null) {
            return createGuiItem(Material.BARRIER, "<red>Unavailable</red>", List.of());
        }

        return appendRecipeMenuHint(player, plugin.getSuperpowerManager().createAncientScrollItem());
    }

    private ItemStack createMythicForgePreview(Player player) {
        if (plugin.getMythicForgeListener() == null) {
            return createGuiItem(Material.BARRIER, "<red>Unavailable</red>", List.of());
        }

        return appendRecipeMenuHint(player, plugin.getMythicForgeListener().createMythicForgeItem());
    }

    private ItemStack createAscendantCorePreview(Player player) {
        if (plugin.getMythicForgeListener() == null) {
            return createGuiItem(Material.BARRIER, "<red>Unavailable</red>", List.of());
        }

        return appendRecipeMenuHint(player, plugin.getMythicForgeListener().createAscendantCoreItem());
    }

    private ItemStack createDisplayLegendaryItem(LegendaryType type, boolean stripLegendaryTag) {
        ItemStack item = createItem(type);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (stripLegendaryTag) {
            meta.getPersistentDataContainer().remove(keyLegendary);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack[] faradaysMagnetRecipeMatrix() {
        ItemStack[] matrix = new ItemStack[9];
        matrix[0] = new ItemStack(Material.IRON_BLOCK);
        matrix[1] = new ItemStack(Material.REDSTONE_BLOCK);
        matrix[2] = new ItemStack(Material.IRON_BLOCK);
        matrix[3] = new ItemStack(Material.COPPER_BLOCK);
        matrix[4] = new ItemStack(Material.RECOVERY_COMPASS);
        matrix[5] = new ItemStack(Material.COPPER_BLOCK);
        matrix[6] = new ItemStack(Material.IRON_BLOCK);
        matrix[7] = new ItemStack(Material.NETHERITE_INGOT);
        matrix[8] = new ItemStack(Material.IRON_BLOCK);
        return matrix;
    }

    private ItemStack appendRecipeMenuHint(Player player, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(MM.deserialize("<dark_gray>" + BedrockCompat.menuActionWord(player) + " to view recipe</dark_gray>"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String menuPercent(double chance) {
        double percent = chance * 100.0;
        if (Math.abs(percent - Math.rint(percent)) < 0.001) {
            return String.valueOf((int) Math.round(percent)) + "%";
        }
        return String.format(java.util.Locale.US, "%.1f%%", percent);
    }

    private ItemStack addRequiredTotalLore(ItemStack item, int totalRequired) {
        ItemStack display = item.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) {
            return display;
        }

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(MM.deserialize("<gray>Required: <white>x" + totalRequired + "</white> total</gray>"));
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack createGuiItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(MM.deserialize(name));
        if (!loreLines.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(MM.deserialize(line));
            }
            meta.lore(lore);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInventoryCraftButton(String description) {
        return createGuiItem(
            Material.CRAFTING_TABLE,
            "<gold><bold>Craft From Inventory</bold></gold>",
            List.of(
                "<gray>Click to craft server-side using your inventory.</gray>",
                "<gray>" + description + "</gray>",
                "<dark_gray>Java players can still use the normal recipe.</dark_gray>"
            )
        );
    }

    private boolean craftAncientScrollFromInventory(Player player) {
        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers == null) {
            player.sendMessage(MessageUtil.error("Ancient Scroll recipes are not ready yet."));
            return false;
        }

        List<InventoryRecipeUtil.Ingredient> ingredients = List.of(
            InventoryRecipeUtil.plainMaterial(plugin, Material.TOTEM_OF_UNDYING, 1),
            InventoryRecipeUtil.plainMaterial(plugin, Material.NETHER_STAR, 2),
            new InventoryRecipeUtil.Ingredient("Warden Heart", 1, powers::isWardenHeart)
        );
        return craftInventoryRecipe(
            player,
            "Ancient Scroll",
            ingredients,
            powers.createAncientScrollItem(),
            "ancient_scroll_reliquary_craft",
            "Crafted an Ancient Scroll from the Reliquary."
        );
    }

    private boolean craftCustomToolFromInventory(Player player, String toolId) {
        CustomToolListener tools = plugin.getCustomToolListener();
        if (tools == null) {
            player.sendMessage(MessageUtil.error("Custom tool recipes are not ready yet."));
            return false;
        }

        String displayName = tools.displayNameFor(toolId);
        if (displayName == null) {
            player.sendMessage(MessageUtil.error("Unknown custom tool recipe."));
            return false;
        }

        Map<Material, Integer> ingredients = tools.recipeIngredients(toolId);
        if (ingredients.isEmpty()) {
            player.sendMessage(MessageUtil.error(displayName + " cannot be crafted from this menu yet."));
            return false;
        }

        return craftInventoryRecipe(
            player,
            displayName,
            InventoryRecipeUtil.plainMaterials(plugin, ingredients),
            tools.createCustomToolForPlayer(toolId, player),
            "custom_tool_reliquary_craft",
            "Crafted " + displayName + " from the Reliquary."
        );
    }

    private boolean craftMythicForgeItemFromInventory(Player player, String itemId) {
        MythicForgeListener forge = plugin.getMythicForgeListener();
        if (forge == null) {
            player.sendMessage(MessageUtil.error("Mythic forge recipes are not ready yet."));
            return false;
        }

        String displayName = forge.displayNameFor(itemId);
        if (displayName == null) {
            player.sendMessage(MessageUtil.error("Unknown mythic forge recipe."));
            return false;
        }

        Map<Material, Integer> ingredients = forge.recipeIngredients(itemId);
        if (ingredients.isEmpty()) {
            player.sendMessage(MessageUtil.error(displayName + " cannot be crafted from this menu yet."));
            return false;
        }

        return craftInventoryRecipe(
            player,
            displayName,
            InventoryRecipeUtil.plainMaterials(plugin, ingredients),
            forge.createCustomItem(itemId),
            "mythic_forge_reliquary_craft",
            "Crafted " + displayName + " from the Reliquary."
        );
    }

    private boolean craftFaradaysMagnetFromInventory(Player player) {
        return craftInventoryRecipe(
            player,
            "Faraday's Magnet",
            InventoryRecipeUtil.plainMaterials(plugin, faradaysMagnetIngredients()),
            createFaradaysMagnetItem(),
            "faradays_magnet_reliquary_craft",
            "Crafted Faraday's Magnet from the Reliquary."
        );
    }

    private boolean craftTalismanFromInventory(Player player) {
        SustenanceTalismanListener talisman = plugin.getSustenanceTalismanListener();
        if (talisman == null) {
            player.sendMessage(MessageUtil.error("Talisman recipes are not ready yet."));
            return false;
        }

        return craftInventoryRecipe(
            player,
            "Talisman of Sustenance",
            InventoryRecipeUtil.plainMaterials(plugin, talisman.recipeIngredients()),
            talisman.createTalismanItem(),
            "talisman_reliquary_craft",
            "Crafted a Talisman of Sustenance from the Reliquary."
        );
    }

    private boolean craftSalvagingDepotFromInventory(Player player) {
        SalvagingDepotListener depot = plugin.getSalvagingDepotListener();
        if (depot == null) {
            player.sendMessage(MessageUtil.error("Salvaging Depot recipes are not ready yet."));
            return false;
        }

        return craftInventoryRecipe(
            player,
            "Salvaging Depot",
            InventoryRecipeUtil.plainMaterials(plugin, depot.recipeIngredients()),
            depot.createDepotItem(),
            "salvaging_depot_reliquary_craft",
            "Crafted a Salvaging Depot from the Reliquary."
        );
    }

    private boolean craftAgriculturalPylonFromInventory(Player player) {
        AgriculturalPylonListener pylon = plugin.getAgriculturalPylonListener();
        if (pylon == null) {
            player.sendMessage(MessageUtil.error("Agricultural Pylon recipes are not ready yet."));
            return false;
        }

        return craftInventoryRecipe(
            player,
            "Agricultural Pylon",
            InventoryRecipeUtil.plainMaterials(plugin, pylon.recipeIngredients()),
            pylon.createPylonItem(),
            "agricultural_pylon_reliquary_craft",
            "Crafted an Agricultural Pylon from the Reliquary."
        );
    }

    private boolean craftXpLecternFromInventory(Player player) {
        XpLecternListener lectern = plugin.getXpLecternListener();
        if (lectern == null) {
            player.sendMessage(MessageUtil.error("XP Lectern recipes are not ready yet."));
            return false;
        }

        return craftInventoryRecipe(
            player,
            "XP Lectern",
            InventoryRecipeUtil.plainMaterials(plugin, lectern.recipeIngredients()),
            lectern.createLecternItem(),
            "xp_lectern_reliquary_craft",
            "Crafted an XP Lectern from the Reliquary."
        );
    }

    private boolean craftInventoryRecipe(
        Player player,
        String displayName,
        List<InventoryRecipeUtil.Ingredient> ingredients,
        ItemStack reward,
        String auditSource,
        String auditDetails
    ) {
        if (reward == null || reward.getType().isAir()) {
            player.sendMessage(MessageUtil.error("That recipe is not ready yet."));
            return false;
        }
        for (InventoryRecipeUtil.Ingredient ingredient : ingredients) {
            int available = InventoryRecipeUtil.countIngredient(player, ingredient);
            if (available < ingredient.amount()) {
                player.sendMessage(MessageUtil.error(
                    "Missing <white>" + (ingredient.amount() - available) + "x " + ingredient.name()
                        + "</white> <gray>(need <white>" + ingredient.amount()
                        + "</white>, have <white>" + available + "</white>).</gray>"
                ));
                return false;
            }
        }
        if (!InventoryRecipeUtil.canFitRewardAfterRemovingIngredients(player, ingredients, reward)) {
            player.sendMessage(MessageUtil.warn("Clear enough inventory space before crafting <white>" + displayName + "</white>."));
            return false;
        }
        if (!InventoryRecipeUtil.removeIngredients(player, ingredients)) {
            player.sendMessage(MessageUtil.error("Those ingredients changed before the craft finished. Try again."));
            return false;
        }

        if (plugin.getItemAuditManager() != null) {
            plugin.getItemAuditManager().recordKnownAcquisition(player, reward, auditSource, auditDetails);
        }
        InventoryRecipeUtil.giveOrDrop(player, reward);
        player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.7f, 1.25f);
        player.sendMessage(MessageUtil.success("Crafted <white>" + displayName + "</white>."));
        return true;
    }

    private Map<Material, Integer> faradaysMagnetIngredients() {
        Map<Material, Integer> ingredients = new LinkedHashMap<>();
        ingredients.put(Material.IRON_BLOCK, 4);
        ingredients.put(Material.REDSTONE_BLOCK, 1);
        ingredients.put(Material.COPPER_BLOCK, 2);
        ingredients.put(Material.RECOVERY_COMPASS, 1);
        ingredients.put(Material.NETHERITE_INGOT, 1);
        return ingredients;
    }

    private ItemStack createBackpackRecipeDisplayItem(boolean upgraded) {
        ItemStack item = new ItemStack(Material.FLOWER_POT);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String displayName = upgraded ? "Expanded Backpack" : "Backpack";
        CustomLoreUtil.Rarity rarity = upgraded ? CustomLoreUtil.Rarity.RARE : CustomLoreUtil.Rarity.UNCOMMON;
        int slots = upgraded ? 54 : 27;
        meta.setItemModel(null);
        meta.displayName(CustomLoreUtil.displayName(rarity, displayName));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.FLOWER_POT,
            rarity.label(),
            "STORAGE",
            List.of(
                "<gray>Portable storage.</gray>",
                "<dark_gray>Recipe preview only.</dark_gray>"
            ),
            List.of(CustomLoreUtil.section(
                "Use",
                upgraded ? "Deep Pocket Vault" : "Pocket Vault",
                "<gray>Right-click to open.</gray>",
                "<gray>Holds <white>" + slots + "</white> items safely in its own saved storage.</gray>"
            ))
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTradeButton(Player player, LegendaryType type) {
        Map<Material, Integer> ingredients = ingredientsFor(type);
        boolean claimed = plugin.getLegendaryAltarManager() != null
            && plugin.getLegendaryAltarManager().isLegendaryClaimed(type.id);
        boolean canCraft = canCraftLegendary(player, type.id);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Legendaries can only be crafted at the active altar.</gray>");
        if (claimed) {
            lore.add("<red>This legendary has already been created on this server.</red>");
            lore.add("<gray>It is locked out of future altar rolls.</gray>");
            lore.add("<dark_gray> ");
            lore.addAll(recipeProgressLines(player, type.id));
            return createGuiItem(
                Material.BARRIER,
                "<red><bold>Already Exists</bold></red>",
                lore
            );
        }
        lore.add(canCraft
            ? "<green>You have the required materials.</green>"
            : "<red>You are missing some required materials.</red>");
        lore.add("<gray>" + BedrockCompat.menuActionWord(player) + " for altar status and coordinates.</gray>");
        lore.add("<dark_gray> ");
        lore.addAll(recipeProgressLines(player, type.id));
        return createGuiItem(
            canCraft ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
            canCraft ? "<green><bold>Craft At Altar</bold></green>" : "<red><bold>Missing Materials</bold></red>",
            lore
        );
    }

    private ItemStack createTradeButton(Player player, String displayName, Map<Material, Integer> ingredients, boolean canTrade) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Trade the required materials from your inventory.</gray>");
        lore.add(canTrade
            ? "<green>" + BedrockCompat.menuActionWord(player) + " to receive " + displayName + "<green>.</green>"
            : "<red>You do not have all required materials.</red>");
        lore.add("<dark_gray> ");
        for (Map.Entry<Material, Integer> entry : ingredients.entrySet()) {
            int have = countRecipeMaterial(player, null, entry.getKey());
            String color = have >= entry.getValue() ? "<green>" : "<red>";
            lore.add("<gray>" + prettyMaterial(entry.getKey()) + ": " + color + have + "</" + (have >= entry.getValue() ? "green" : "red") + "><gray>/<white>" + entry.getValue() + "</white></gray>");
        }
        return createGuiItem(
            canTrade ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
            canTrade ? "<green><bold>Trade Materials</bold></green>" : "<red><bold>Missing Materials</bold></red>",
            lore
        );
    }

    private void announceLegendaryCraft(Player crafter, LegendaryType type) {
        Component message = MessageUtil.prefixedRaw(
            "<gold><white>" + crafter.getName() + "</white> has crafted " + type.display + "<gold>!</gold>"
        );
        Bukkit.broadcast(message);
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.8f, 1.15f);
        }
    }

    private void useEnderSword(Player player) {
        if (plugin.getCombatLogListener() != null && plugin.getCombatLogListener().isInPlayerCombat(player)) {
            player.sendMessage(MessageUtil.error("You can't ride your Ender Dragon while in combat."));
            return;
        }

        EnderDragon dragon = ownedEnderDragon(player.getUniqueId());
        if (dragon != null) {
            if (plugin.getConfigManager().enderSwordRequireOpenSky && !hasOpenSky(player.getLocation())) {
                player.sendMessage(MessageUtil.error("You need open sky above you to call your Ender Dragon here."));
                return;
            }
            recallAndMountEnderDragon(player, dragon);
            return;
        }

        if (plugin.getConfigManager().enderSwordRequireOpenSky && !hasOpenSky(player.getLocation())) {
            player.sendMessage(MessageUtil.error("You need open sky above you to summon your Ender Dragon."));
            return;
        }

        if (onCooldown(enderSwordSummonCd, player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn(
                "Ender Dragon cooldown: <white>" + secondsLeft(enderSwordSummonCd, player.getUniqueId()) + "s</white>."
            ));
            return;
        }

        summonEnderDragon(player);
    }

    private void dismountEnderDragonForCombat(Player player) {
        if (player == null || !activeEnderDragonRiders.contains(player.getUniqueId())) {
            return;
        }

        player.setFallDistance(0.0f);
        despawnEnderSwordDragon(player.getUniqueId(), false);
        if (player.isOnline()) {
            player.sendMessage(MessageUtil.warn("Dragon Mode cancelled because you're in combat."));
        }
    }

    private void summonEnderDragon(Player player) {
        World world = player.getWorld();
        Location spawn = dragonVisualLocation(
            dragonSeatLocation(player.getLocation(), player.getLocation().getYaw()),
            player.getLocation().getYaw()
        );

        EnderDragon dragon = world.spawn(spawn, EnderDragon.class, entity -> {
            entity.setAI(true);
            entity.setAware(true);
            entity.setGravity(false);
            entity.setNoPhysics(true);
            entity.setCollidable(false);
            entity.setSilent(true);
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setPhase(EnderDragon.Phase.CIRCLING);
            entity.getPersistentDataContainer().set(
                keyEnderSwordDragonOwner,
                PersistentDataType.STRING,
                player.getUniqueId().toString()
            );

            var scale = entity.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(plugin.getConfigManager().enderSwordDragonScale);
            }

            var health = entity.getAttribute(Attribute.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(plugin.getConfigManager().enderSwordDragonHealth);
                entity.setHealth(plugin.getConfigManager().enderSwordDragonHealth);
            }

            if (entity.getBossBar() != null) {
                entity.getBossBar().setVisible(false);
            }
        });

        UUID ownerId = player.getUniqueId();
        enderDragonByOwner.put(ownerId, dragon.getUniqueId());
        enderDragonOwnerByDragon.put(dragon.getUniqueId(), ownerId);
        setCooldown(enderSwordSummonCd, ownerId, plugin.getConfigManager().enderSwordSummonCooldownSeconds);

        recallAndMountEnderDragon(player, dragon);
        world.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_AMBIENT, 1.0f, 1.15f);
        player.sendMessage(MessageUtil.success("Entered <white>Dragon Mode</white>."));
    }

    private void recallAndMountEnderDragon(Player player, EnderDragon dragon) {
        if (!dragon.isValid() || dragon.isDead()) {
            cleanupEnderDragon(player.getUniqueId(), dragon.getUniqueId());
            return;
        }
        ArmorStand seat = ownedEnderDragonSeat(player.getUniqueId());
        if (seat == null) {
            seat = spawnEnderDragonSeat(player);
        }
        if (seat == null || !seat.isValid()) {
            player.sendMessage(MessageUtil.error("Failed to create the Ender Dragon seat."));
            despawnEnderSwordDragon(player.getUniqueId(), false);
            return;
        }

        Location seatLocation = dragonSeatLocation(player.getLocation(), player.getLocation().getYaw());
        seat.teleport(seatLocation);
        seat.setRotation(player.getLocation().getYaw(), 0.0f);
        if (player.isInsideVehicle() && player.getVehicle() != seat) {
            player.leaveVehicle();
        }
        if (player.getVehicle() != seat && !seat.addPassenger(player)) {
            ArmorStand retrySeat = seat;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (retrySeat.isValid() && player.isOnline() && player.getVehicle() != retrySeat) {
                    retrySeat.addPassenger(player);
                }
            });
        }

        dragon.setPhase(EnderDragon.Phase.CIRCLING);
        dragon.setVelocity(new Vector());
        activeEnderDragonRiders.add(player.getUniqueId());
        updateEnderDragonChunkTickets(player.getUniqueId(), seatLocation);
        syncEnderDragonVisual(seatLocation, player.getLocation().getYaw(), dragon, true);
        player.sendActionBar(MM.deserialize(
            "<dark_purple><bold>Ender Dragon</bold></dark_purple><gray> Use <white>WASD</white> to steer, <white>look up or down</white> to climb or dive, and <white>sneak</white> to dismount.</gray>"
        ));
    }

    private void tickEnderSwordDragons() {
        if (enderDragonByOwner.isEmpty()) return;

        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, UUID> entry : new HashMap<>(enderDragonByOwner).entrySet()) {
            UUID ownerId = entry.getKey();
            EnderDragon dragon = ownedEnderDragon(ownerId);
            if (dragon == null) {
                cleanupEnderDragon(ownerId, entry.getValue());
                continue;
            }

            Player owner = Bukkit.getPlayer(ownerId);
            if (owner == null || !owner.isOnline() || owner.isDead()) {
                despawnEnderSwordDragon(ownerId, false);
                continue;
            }
            ArmorStand seat = ownedEnderDragonSeat(ownerId);
            if (seat == null) {
                cleanupEnderDragon(ownerId, entry.getValue());
                continue;
            }

            if (activeEnderDragonRiders.contains(ownerId)) {
                controlEnderDragon(owner, dragon, seat, now);
                continue;
            }
            despawnEnderSwordDragon(ownerId, false);
        }
    }

    private void controlEnderDragon(Player owner, EnderDragon dragon, ArmorStand seat, long now) {
        if (owner.getVehicle() != seat || !seat.getPassengers().contains(owner)) {
            despawnEnderSwordDragon(owner.getUniqueId(), false);
            return;
        }

        float yaw = owner.getLocation().getYaw();
        double yawRadians = Math.toRadians(yaw);
        Vector flatForward = new Vector(-Math.sin(yawRadians), 0.0, Math.cos(yawRadians)).normalize();
        Vector lookForward = owner.getLocation().getDirection().normalize();
        Vector right = new Vector(flatForward.getZ(), 0.0, -flatForward.getX()).normalize();
        Input input = owner.getCurrentInput();

        Vector movement = new Vector();
        if (input.isForward()) {
            movement.add(lookForward);
        }
        if (input.isBackward()) {
            movement.subtract(lookForward);
        }
        if (input.isLeft()) {
            movement.add(right);
        }
        if (input.isRight()) {
            movement.subtract(right);
        }

        double horizontalSpeed = plugin.getConfigManager().enderSwordDragonSpeed;
        if (input.isSprint()) {
            horizontalSpeed *= ENDER_SWORD_DRAGON_SPRINT_MULTIPLIER;
        }

        if (movement.lengthSquared() > 0.0001) {
            movement.normalize().multiply(horizontalSpeed * ENDER_SWORD_DRAGON_BASE_MOVE_SCALAR);
        }

        if (input.isJump()) {
            movement.setY(movement.getY() + plugin.getConfigManager().enderSwordDragonVerticalSpeed * 0.22);
        }

        Location nextSeat = resolveEnderDragonSeatMovement(seat.getLocation(), movement);
        nextSeat.setYaw(yaw);
        nextSeat.setPitch(0.0f);
        seat.teleport(nextSeat);
        seat.setRotation(yaw, 0.0f);
        updateEnderDragonChunkTickets(owner.getUniqueId(), nextSeat);
        syncEnderDragonVisual(nextSeat, yaw, dragon, false);
        seat.setVelocity(new Vector());
        owner.setVelocity(new Vector());
        owner.setFallDistance(0.0f);
    }

    private EnderDragon ownedEnderDragon(UUID ownerId) {
        UUID dragonId = enderDragonByOwner.get(ownerId);
        if (dragonId == null) return null;
        Entity entity = Bukkit.getEntity(dragonId);
        if (entity instanceof EnderDragon dragon && dragon.isValid() && !dragon.isDead()) {
            return dragon;
        }
        return null;
    }

    private ArmorStand ownedEnderDragonSeat(UUID ownerId) {
        UUID seatId = enderDragonSeatByOwner.get(ownerId);
        if (seatId == null) return null;
        Entity entity = Bukkit.getEntity(seatId);
        if (entity instanceof ArmorStand stand && stand.isValid() && !stand.isDead()) {
            return stand;
        }
        return null;
    }

    private ArmorStand spawnEnderDragonSeat(Player player) {
        Location seatLocation = dragonSeatLocation(player.getLocation(), player.getLocation().getYaw());
        ArmorStand seat = player.getWorld().spawn(seatLocation, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setCollidable(false);
            stand.setSilent(true);
            stand.setPersistent(false);
            stand.setInvulnerable(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setCanMove(true);
            stand.setCanTick(true);
            stand.getPersistentDataContainer().set(
                keyEnderSwordSeatOwner,
                PersistentDataType.STRING,
                player.getUniqueId().toString()
            );
        });
        enderDragonSeatByOwner.put(player.getUniqueId(), seat.getUniqueId());
        return seat;
    }

    private void despawnEnderSwordDragon(UUID ownerId, boolean applyKilledCooldown) {
        UUID dragonId = enderDragonByOwner.remove(ownerId);
        removeEnderDragonSeat(ownerId);
        activeEnderDragonRiders.remove(ownerId);
        clearEnderDragonChunkTickets(ownerId);
        if (dragonId == null) return;

        enderDragonOwnerByDragon.remove(dragonId);
        Entity entity = Bukkit.getEntity(dragonId);
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    private void cleanupEnderDragon(UUID ownerId, UUID dragonId) {
        enderDragonByOwner.remove(ownerId, dragonId);
        removeEnderDragonSeat(ownerId);
        activeEnderDragonRiders.remove(ownerId);
        enderDragonOwnerByDragon.remove(dragonId, ownerId);
        clearEnderDragonChunkTickets(ownerId);
    }

    private void syncEnderDragonVisual(Location seatLocation, float yaw, EnderDragon dragon, boolean snap) {
        if (!dragon.isValid() || dragon.isDead()) {
            return;
        }

        Location dragonLocation = dragonVisualLocation(seatLocation, yaw);
        dragon.setPhase(EnderDragon.Phase.CIRCLING);
        dragon.setRotation(dragonLocation.getYaw(), dragonLocation.getPitch());
        moveEnderDragonVisual(dragon, dragonLocation, snap);
    }

    private void moveEnderDragonVisual(EnderDragon dragon, Location dragonLocation, boolean snap) {
        boolean changedWorld = !dragon.getWorld().equals(dragonLocation.getWorld());
        boolean movedEnough = dragon.getLocation().distanceSquared(dragonLocation) > 0.0001;
        if (!changedWorld && !snap && !movedEnough) {
            return;
        }

        dragon.teleport(dragonLocation);
        dragon.setVelocity(new Vector());
    }

    private boolean isEnderDragonLandingState(Player player) {
        if (player.isInWater() || player.isInLava()) {
            return true;
        }
        Block blockBelow = player.getLocation().clone().subtract(0.0, 0.15, 0.0).getBlock();
        return !blockBelow.isPassable();
    }

    private Location dragonSeatLocation(Location base, float yaw) {
        Location seat = base.clone().add(0.0, ENDER_SWORD_SEAT_START_Y_OFFSET, 0.0);
        seat.setYaw(yaw);
        seat.setPitch(0.0f);
        return seat;
    }

    private Location dragonVisualLocation(Location seatLocation, float yaw) {
        double radians = Math.toRadians(yaw);
        Vector forward = new Vector(-Math.sin(radians), 0.0, Math.cos(radians)).normalize();
        Location dragonLocation = seatLocation.clone()
            .add(forward.multiply(ENDER_SWORD_DRAGON_RIDER_FORWARD_OFFSET))
            .add(0.0, ENDER_SWORD_DRAGON_RIDER_VERTICAL_OFFSET, 0.0);
        dragonLocation.setYaw(enderDragonVisualYaw(yaw));
        dragonLocation.setPitch(0.0f);
        return dragonLocation;
    }

    private float enderDragonVisualYaw(float riderYaw) {
        float yaw = riderYaw + ENDER_SWORD_DRAGON_YAW_OFFSET;
        while (yaw <= -180.0f) {
            yaw += 360.0f;
        }
        while (yaw > 180.0f) {
            yaw -= 360.0f;
        }
        return yaw;
    }

    private void removeEnderDragonSeat(UUID ownerId) {
        UUID seatId = enderDragonSeatByOwner.remove(ownerId);
        if (seatId == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(seatId);
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    private Location resolveEnderDragonSeatMovement(Location currentSeat, Vector movement) {
        if (movement.lengthSquared() <= 0.0001) {
            return currentSeat.clone();
        }

        Location fullMove = currentSeat.clone().add(movement);
        if (canMoveEnderDragonSeat(currentSeat, fullMove)) {
            return fullMove;
        }

        Location resolved = currentSeat.clone();
        if (Math.abs(movement.getY()) > 0.0001) {
            Location verticalMove = resolved.clone().add(0.0, movement.getY(), 0.0);
            if (canMoveEnderDragonSeat(resolved, verticalMove)) {
                resolved = verticalMove;
            }
        }

        Vector primaryHorizontal = Math.abs(movement.getX()) >= Math.abs(movement.getZ())
            ? new Vector(movement.getX(), 0.0, 0.0)
            : new Vector(0.0, 0.0, movement.getZ());
        Vector secondaryHorizontal = Math.abs(movement.getX()) >= Math.abs(movement.getZ())
            ? new Vector(0.0, 0.0, movement.getZ())
            : new Vector(movement.getX(), 0.0, 0.0);

        if (primaryHorizontal.lengthSquared() > 0.0001) {
            Location attempt = resolved.clone().add(primaryHorizontal);
            if (canMoveEnderDragonSeat(resolved, attempt)) {
                resolved = attempt;
            }
        }
        if (secondaryHorizontal.lengthSquared() > 0.0001) {
            Location attempt = resolved.clone().add(secondaryHorizontal);
            if (canMoveEnderDragonSeat(resolved, attempt)) {
                resolved = attempt;
            }
        }

        return resolved;
    }

    private boolean canMoveEnderDragonSeat(Location from, Location to) {
        World world = from.getWorld();
        if (world == null || to.getWorld() == null || !world.equals(to.getWorld())) {
            return false;
        }
        if (!world.isChunkLoaded(to.getBlockX() >> 4, to.getBlockZ() >> 4)) {
            return false;
        }
        return isEnderDragonSeatPathClear(world, from, to) && isEnderDragonSeatSpaceClear(world, to);
    }

    private boolean isEnderDragonSeatPathClear(World world, Location from, Location to) {
        Vector path = to.toVector().subtract(from.toVector());
        double distance = path.length();
        if (distance <= 0.0001) {
            return true;
        }
        Vector direction = path.clone().normalize();
        for (Vector offset : ENDER_SWORD_SEAT_COLLISION_OFFSETS) {
            for (double height : ENDER_SWORD_SEAT_COLLISION_HEIGHTS) {
                Location sampleStart = from.clone().add(offset).add(0.0, height, 0.0);
                if (world.rayTraceBlocks(sampleStart, direction, distance, FluidCollisionMode.NEVER, true) != null) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isEnderDragonSeatSpaceClear(World world, Location location) {
        for (Vector offset : ENDER_SWORD_SEAT_COLLISION_OFFSETS) {
            for (double height : ENDER_SWORD_SEAT_COLLISION_HEIGHTS) {
                Block block = world.getBlockAt(location.clone().add(offset).add(0.0, height, 0.0));
                if (!block.isPassable()) {
                    return false;
                }
            }
        }
        return true;
    }

    private void updateEnderDragonChunkTickets(UUID ownerId, Location location) {
        World world = location.getWorld();
        if (world == null) {
            clearEnderDragonChunkTickets(ownerId);
            return;
        }

        String previousWorldName = enderDragonChunkTicketWorlds.get(ownerId);
        if (previousWorldName != null && !previousWorldName.equals(world.getName())) {
            clearEnderDragonChunkTickets(ownerId);
        }
        enderDragonChunkTicketWorlds.put(ownerId, world.getName());

        int centerChunkX = location.getBlockX() >> 4;
        int centerChunkZ = location.getBlockZ() >> 4;
        Set<Long> desired = new HashSet<>();
        for (int x = centerChunkX - 3; x <= centerChunkX + 3; x++) {
            for (int z = centerChunkZ - 3; z <= centerChunkZ + 3; z++) {
                desired.add(chunkKey(x, z));
            }
        }

        Set<Long> current = enderDragonChunkTickets.computeIfAbsent(ownerId, ignored -> ConcurrentHashMap.newKeySet());
        for (long key : new HashSet<>(current)) {
            if (desired.contains(key)) {
                continue;
            }
            world.getChunkAt(chunkX(key), chunkZ(key)).removePluginChunkTicket(plugin);
            current.remove(key);
        }

        for (long key : desired) {
            if (!current.add(key)) {
                continue;
            }
            world.getChunkAt(chunkX(key), chunkZ(key)).addPluginChunkTicket(plugin);
        }
    }

    private void clearEnderDragonChunkTickets(UUID ownerId) {
        Set<Long> chunkKeys = enderDragonChunkTickets.remove(ownerId);
        String worldName = enderDragonChunkTicketWorlds.remove(ownerId);
        if (chunkKeys == null || chunkKeys.isEmpty()) {
            return;
        }
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        for (long key : chunkKeys) {
            world.getChunkAt(chunkX(key), chunkZ(key)).removePluginChunkTicket(plugin);
        }
    }

    private void cleanupTaggedEnderSwordDragons() {
        for (World world : Bukkit.getWorlds()) {
            for (EnderDragon dragon : world.getEntitiesByClass(EnderDragon.class)) {
                if (!dragon.getPersistentDataContainer().has(keyEnderSwordDragonOwner, PersistentDataType.STRING)) {
                    continue;
                }
                dragon.remove();
            }
        }
    }

    private void cleanupTaggedEnderSwordSeats() {
        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                if (!stand.getPersistentDataContainer().has(keyEnderSwordSeatOwner, PersistentDataType.STRING)) {
                    continue;
                }
                stand.remove();
            }
        }
    }

    private long chunkKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private int chunkX(long key) {
        return (int) (key >> 32);
    }

    private int chunkZ(long key) {
        return (int) key;
    }

    private boolean isOwnedEnderDragon(Entity entity) {
        return enderDragonOwner(entity) != null;
    }

    private boolean handleOwnedEnderDragonAttack(Entity damager, Entity target) {
        UUID ownerId = enderDragonOwner(damager);
        if (ownerId == null) {
            return false;
        }

        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) {
            return true;
        }

        if (target instanceof Player victim) {
            if (owner.equals(victim)) {
                stabilizeEnderDragonCollision(owner, null);
                return true;
            }

            stabilizeEnderDragonCollision(owner, victim);
            if (!owner.equals(victim)) {
                CombatLogListener combatLogListener = plugin.getCombatLogListener();
                if (combatLogListener != null) {
                    combatLogListener.tagPlayers(owner, victim);
                }
                dismountEnderDragonForCombat(owner);
                dismountEnderDragonForCombat(victim);
                stabilizeEnderDragonCollision(owner, victim);
            }
        }
        return true;
    }

    private void stabilizeEnderDragonCollision(Player owner, Player victim) {
        stabilizeEnderDragonCollisionVelocity(owner, ENDER_SWORD_DRAGON_RIDER_COLLISION_MAX_HORIZONTAL, ENDER_SWORD_DRAGON_RIDER_COLLISION_MAX_VERTICAL);
        if (victim != null && victim.isOnline()) {
            stabilizeEnderDragonCollisionVelocity(victim, ENDER_SWORD_DRAGON_TARGET_COLLISION_MAX_HORIZONTAL, ENDER_SWORD_DRAGON_TARGET_COLLISION_MAX_VERTICAL);
        }

        for (int delay = 1; delay <= ENDER_SWORD_DRAGON_COLLISION_STABILIZE_TICKS; delay++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (owner.isOnline()) {
                    stabilizeEnderDragonCollisionVelocity(owner, ENDER_SWORD_DRAGON_RIDER_COLLISION_MAX_HORIZONTAL, ENDER_SWORD_DRAGON_RIDER_COLLISION_MAX_VERTICAL);
                }
                if (victim != null && victim.isOnline()) {
                    stabilizeEnderDragonCollisionVelocity(victim, ENDER_SWORD_DRAGON_TARGET_COLLISION_MAX_HORIZONTAL, ENDER_SWORD_DRAGON_TARGET_COLLISION_MAX_VERTICAL);
                }
            }, delay);
        }
    }

    private void stabilizeEnderDragonCollisionVelocity(Player player, double maxHorizontal, double maxVertical) {
        if (player == null || !player.isOnline()) {
            return;
        }

        Vector velocity = player.getVelocity().clone();
        double horizontalSquared = (velocity.getX() * velocity.getX()) + (velocity.getZ() * velocity.getZ());
        double maxHorizontalSquared = maxHorizontal * maxHorizontal;
        if (horizontalSquared > maxHorizontalSquared) {
            double scale = maxHorizontal / Math.sqrt(horizontalSquared);
            velocity.setX(velocity.getX() * scale);
            velocity.setZ(velocity.getZ() * scale);
        }
        if (Math.abs(velocity.getY()) > maxVertical) {
            velocity.setY(Math.copySign(maxVertical, velocity.getY()));
        }
        player.setVelocity(velocity);
        player.setFallDistance(0.0f);
    }

    private UUID enderDragonOwner(Entity entity) {
        if (entity instanceof EnderDragon dragon) {
            return enderDragonOwnerByDragon.get(dragon.getUniqueId());
        }
        if (entity instanceof EnderDragonPart part) {
            Entity parent = part.getParent();
            return parent instanceof EnderDragon dragon ? enderDragonOwnerByDragon.get(dragon.getUniqueId()) : null;
        }
        return null;
    }

    private boolean hasOpenSky(Location location) {
        World world = location.getWorld();
        if (world == null) return false;
        int highest = world.getHighestBlockAt(location, HeightMap.MOTION_BLOCKING_NO_LEAVES).getY();
        return highest <= location.getBlockY() + 1;
    }

    private void tagMenuLegendaryId(ItemStack item, String id) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(keyMenuLegendary, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
    }

    private String readMenuLegendaryId(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(keyMenuLegendary, PersistentDataType.STRING);
    }

    private void useChronoSword(Player player) {
        UUID id = player.getUniqueId();
        ChronoState state = chronoStates.get(id);
        long now = System.currentTimeMillis();

        if (state == null) {
            if (onCooldown(chronoCd, id)) {
                player.sendMessage(MessageUtil.warn("Chrono cooldown: <white>" + secondsLeft(chronoCd, id) + "s</white>."));
                return;
            }
            chronoStates.put(id, new ChronoState(player.getLocation().clone(), now + CHRONO_READY_SECONDS * 1000L));
            player.sendMessage(MessageUtil.success("Time marked. Rewind available in <white>" + CHRONO_READY_SECONDS + "s</white>."));
            return;
        }

        if (now < state.readyAt()) {
            long wait = (state.readyAt() - now + 999L) / 1000L;
            player.sendMessage(MessageUtil.warn("Mark stabilizing: <white>" + wait + "s</white>."));
            return;
        }

        activateChrono(player, false);
    }

    private void activateChrono(Player player, boolean lethal) {
        ChronoState state = chronoStates.remove(player.getUniqueId());
        if (state == null) return;

        int cooldown = lethal ? CHRONO_COOLDOWN_DEATH : CHRONO_COOLDOWN;
        setCooldown(chronoCd, player.getUniqueId(), cooldown);

        player.teleportAsync(state.loc()).thenAccept(success ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (!success) {
                    player.sendMessage(MessageUtil.error("Chrono rewind failed."));
                    return;
                }
                var max = player.getAttribute(Attribute.MAX_HEALTH);
                double maxHealth = max == null ? 20.0 : max.getValue();
                player.setHealth(Math.max(1.0, maxHealth));
                player.setFireTicks(0);
                player.sendMessage(MessageUtil.success("Chrono rewind used. Cooldown: <white>" + cooldown + "s</white>."));
            })
        );
    }

    private void useHarpoon(Player player) {
        UUID id = player.getUniqueId();
        if (onCooldown(harpoonCd, id)) {
            player.sendMessage(MessageUtil.warn("Harpoon cooldown: <white>" + secondsLeft(harpoonCd, id) + "s</white>."));
            return;
        }
        setCooldown(harpoonCd, id, HARPOON_COOLDOWN);

        Arrow arrow = player.launchProjectile(Arrow.class);
        arrow.setGravity(false);
        arrow.setCritical(true);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setVelocity(player.getLocation().getDirection().normalize().multiply(2.5));
        arrow.getPersistentDataContainer().set(keyHarpoonTag, PersistentDataType.BYTE, (byte) 1);
    }

    private void handleHarpoonHit(ProjectileHitEvent event, AbstractArrow arrow) {
        if (!(arrow.getShooter() instanceof Player shooter)) {
            arrow.remove();
            return;
        }

        Entity hit = event.getHitEntity();
        if (hit instanceof LivingEntity living && !living.equals(shooter)) {
            living.damage(6.0, shooter);
            Location pullTo = shooter.getLocation().clone().add(
                shooter.getLocation().getDirection().normalize().multiply(1.1)
            );
            pullTo.setPitch(living.getLocation().getPitch());
            pullTo.setYaw(living.getLocation().getYaw());
            living.teleport(pullTo);
        } else if (event.getHitBlock() != null) {
            Location anchor = event.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
            Location to = anchor.clone();
            to.setDirection(shooter.getLocation().getDirection());
            shooter.teleport(to);
        }
        arrow.remove();
    }

    private void useBlinkDagger(Player player) {
        UUID playerId = player.getUniqueId();
        if (onCooldown(blinkDaggerCd, playerId)) {
            player.sendMessage(MessageUtil.warn("Blink cooldown: <white>" + secondsLeft(blinkDaggerCd, playerId) + "s</white>."));
            return;
        }

        Location from = player.getLocation().clone();
        Location destination = findBlinkDaggerDestination(player);
        if (destination == null) {
            player.sendMessage(MessageUtil.error("No safe blink destination in that direction."));
            return;
        }
        if (!player.teleport(destination)) {
            player.sendMessage(MessageUtil.error("Blink failed."));
            return;
        }

        setCooldown(blinkDaggerCd, playerId, BLINK_DAGGER_COOLDOWN);
        player.setFallDistance(0.0f);
        World world = player.getWorld();
        world.playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 0.9f, 1.25f);
        world.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 0.9f, 1.15f);
        world.spawnParticle(Particle.PORTAL, from.clone().add(0.0, 1.0, 0.0), 30, 0.25, 0.45, 0.25, 0.18);
        world.spawnParticle(Particle.PORTAL, destination.clone().add(0.0, 1.0, 0.0), 42, 0.25, 0.45, 0.25, 0.18);
    }

    private Location findBlinkDaggerDestination(Player player) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().clone();
        direction.setY(Math.max(-0.35, Math.min(0.35, direction.getY())));
        if (direction.lengthSquared() <= 1.0E-6) {
            return null;
        }
        direction.normalize();

        RayTraceResult hit = world.rayTraceBlocks(
            eye,
            direction,
            BLINK_DAGGER_RANGE_BLOCKS,
            FluidCollisionMode.NEVER,
            true
        );
        double maxDistance = BLINK_DAGGER_RANGE_BLOCKS;
        if (hit != null) {
            maxDistance = Math.min(
                maxDistance,
                Math.max(0.0, hit.getHitPosition().distance(eye.toVector()) - 0.6)
            );
        }

        Location origin = player.getLocation();
        for (double distance = maxDistance; distance >= 2.0; distance -= 0.5) {
            Location probe = origin.clone().add(direction.clone().multiply(distance));
            Location resolved = resolveBlinkDaggerDestination(world, probe, origin.getYaw(), origin.getPitch());
            if (resolved != null && resolved.distanceSquared(origin) >= 4.0) {
                return resolved;
            }
        }
        return null;
    }

    private Location resolveBlinkDaggerDestination(World world, Location probe, float yaw, float pitch) {
        double[] yOffsets = {1.0, 0.0, -1.0, -2.0, 2.0};
        for (double yOffset : yOffsets) {
            Location centered = centerBlinkDaggerLocation(probe.clone().add(0.0, yOffset, 0.0), yaw, pitch);
            if (isBlinkDaggerSafeDestination(world, centered)) {
                return centered;
            }
            for (int drop = 1; drop <= 3; drop++) {
                Location lowered = centerBlinkDaggerLocation(centered.clone().subtract(0.0, drop, 0.0), yaw, pitch);
                if (isBlinkDaggerSafeDestination(world, lowered)) {
                    return lowered;
                }
            }
        }
        return null;
    }

    private Location centerBlinkDaggerLocation(Location location, float yaw, float pitch) {
        location.setX(Math.floor(location.getX()) + 0.5);
        location.setZ(Math.floor(location.getZ()) + 0.5);
        location.setYaw(yaw);
        location.setPitch(pitch);
        return location;
    }

    private boolean isBlinkDaggerSafeDestination(World world, Location location) {
        double y = location.getY();
        if (y < world.getMinHeight() || y + 1.0 >= world.getMaxHeight()) {
            return false;
        }

        Block feet = location.getBlock();
        Block head = location.clone().add(0.0, 1.0, 0.0).getBlock();
        Block below = location.clone().subtract(0.0, 0.1, 0.0).getBlock();
        return feet.isPassable() && head.isPassable() && (!below.isPassable() || below.isLiquid());
    }

    private void tryBlinkDaggerBackstab(Player attacker, LivingEntity victim) {
        UUID attackerId = attacker.getUniqueId();
        if (victim.equals(attacker)) {
            return;
        }
        if (victim instanceof Player teammate && sameTeamOrSelf(attackerId, teammate.getUniqueId())) {
            return;
        }
        if (onCooldown(blinkDaggerBackstabCd, attackerId) || !isBlinkDaggerBackstab(attacker, victim)) {
            return;
        }

        setCooldown(blinkDaggerBackstabCd, attackerId, BLINK_DAGGER_BACKSTAB_COOLDOWN);
        applyBlinkDaggerStun(victim);
        Location center = victim.getLocation().clone().add(0.0, 1.0, 0.0);
        World world = victim.getWorld();
        world.spawnParticle(Particle.CRIT, center, 18, 0.35, 0.45, 0.35, 0.02);
        world.spawnParticle(Particle.SWEEP_ATTACK, center, 1, 0.0, 0.0, 0.0, 0.0);
        world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
    }

    private boolean isBlinkDaggerBackstab(Player attacker, LivingEntity victim) {
        Vector facing = victim.getLocation().getDirection().clone().setY(0.0);
        Vector toAttacker = attacker.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0.0);
        if (facing.lengthSquared() <= 1.0E-6 || toAttacker.lengthSquared() <= 1.0E-6) {
            return false;
        }
        facing.normalize();
        toAttacker.normalize();
        return facing.dot(toAttacker) <= BLINK_DAGGER_BACKSTAB_DOT;
    }

    private boolean isBlinkDaggerStunned(UUID playerId) {
        Long expiresAt = blinkDaggerStunnedUntil.get(playerId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt <= System.currentTimeMillis()) {
            blinkDaggerStunnedUntil.remove(playerId, expiresAt);
            return false;
        }
        return true;
    }

    private void clearBlinkDaggerStun(UUID playerId, long expiresAt) {
        blinkDaggerStunnedUntil.remove(playerId, expiresAt);
    }

    private void applyBlinkDaggerStun(LivingEntity target) {
        long expiresAt = System.currentTimeMillis() + (BLINK_DAGGER_STUN_SECONDS * 1000L);
        if (target instanceof Player playerTarget) {
            blinkDaggerStunnedUntil.put(playerTarget.getUniqueId(), expiresAt);
            if (playerTarget.isGliding()) {
                playerTarget.setGliding(false);
            }
        }
        target.setVelocity(new Vector());
        target.removePotionEffect(PotionEffectType.SLOWNESS);
        target.addPotionEffect(new PotionEffect(
            PotionEffectType.SLOWNESS,
            BLINK_DAGGER_STUN_SECONDS * 20,
            255,
            false,
            true,
            true
        ));
        target.removePotionEffect(PotionEffectType.JUMP_BOOST);
        target.addPotionEffect(new PotionEffect(
            PotionEffectType.JUMP_BOOST,
            BLINK_DAGGER_STUN_SECONDS * 20,
            128,
            false,
            false,
            true
        ));
        if (target instanceof Player playerTarget) {
            Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> clearBlinkDaggerStun(playerTarget.getUniqueId(), expiresAt),
                BLINK_DAGGER_STUN_SECONDS * 20L
            );
        }
    }

    private void useHypnosis(Player player) {
        UUID ownerId = player.getUniqueId();
        if (onCooldown(hypnosisCd, ownerId)) {
            player.sendMessage(MessageUtil.warn("Hypnosis cooldown: <white>" + secondsLeft(hypnosisCd, ownerId) + "s</white>."));
            return;
        }
        setCooldown(hypnosisCd, ownerId, HYPNOSIS_COOLDOWN);

        RayTraceResult hit = player.getWorld().rayTraceEntities(
            player.getEyeLocation(),
            player.getEyeLocation().getDirection(),
            24.0,
            0.4,
            entity -> entity instanceof Mob mob && mob.isValid() && !mob.isDead()
        );

        if (hit == null || !(hit.getHitEntity() instanceof Mob mob)) {
            player.sendMessage(MessageUtil.error("No mob hit."));
            return;
        }

        UUID mobId = mob.getUniqueId();
        UUID currentOwner = ownerByMob.get(mobId);
        if (ownerId.equals(currentOwner)) {
            healMob(mob, 20.0);
            player.sendMessage(MessageUtil.success("Controlled mob healed."));
            return;
        }

        Set<UUID> set = controlledByOwner.computeIfAbsent(ownerId, ignored -> ConcurrentHashMap.newKeySet());
        if (currentOwner == null && set.size() >= HYPNOSIS_MAX) {
            player.sendMessage(MessageUtil.error("Control cap reached (<white>" + HYPNOSIS_MAX + "</white>)."));
            return;
        }

        if (currentOwner != null) {
            Set<UUID> oldSet = controlledByOwner.get(currentOwner);
            if (oldSet != null) {
                oldSet.remove(mobId);
                if (oldSet.isEmpty()) controlledByOwner.remove(currentOwner);
            }
        }

        ownerByMob.put(mobId, ownerId);
        set.add(mobId);
        mob.setPersistent(true);
        mob.setTarget(null);
        player.sendMessage(MessageUtil.success("Mob is now loyal to you."));
    }

    private void directControlledMobs(Player owner, LivingEntity victim) {
        Set<UUID> set = controlledByOwner.get(owner.getUniqueId());
        if (set == null || set.isEmpty()) return;
        if (victim instanceof Player teammate && sameTeamOrSelf(owner.getUniqueId(), teammate.getUniqueId())) {
            return;
        }
        World world = owner.getWorld();

        for (UUID mobId : new HashSet<>(set)) {
            Entity entity = Bukkit.getEntity(mobId);
            if (!(entity instanceof Mob mob) || !mob.isValid() || mob.isDead()) {
                set.remove(mobId);
                ownerByMob.remove(mobId);
                continue;
            }
            if (!mob.getWorld().equals(world)) continue;
            if (mob.getLocation().distanceSquared(owner.getLocation()) > 64 * 64) continue;
            if (mob.equals(victim)) continue;
            mob.setTarget(victim);
        }
    }

    private boolean sameTeamOrSelf(UUID ownerId, UUID targetId) {
        if (ownerId == null || targetId == null) return false;
        if (ownerId.equals(targetId)) return true;
        return plugin.getTeamManager() != null && plugin.getTeamManager().sameTeam(ownerId, targetId);
    }

    private void feedEmeraldBlade(Player player, ItemStack blade) {
        int current = emeraldLevel(blade);
        if (current >= EMERALD_BLADE_MAX_LEVEL) {
            player.sendMessage(MessageUtil.warn("Verdant Fang is already at max Sharpness <white>" + EMERALD_BLADE_MAX_LEVEL + "</white>."));
            return;
        }

        if (!takeEmeraldBlock(player)) {
            player.sendMessage(MessageUtil.error("You need an emerald block."));
            return;
        }

        int lvl = current + 1;
        setEmeraldLevel(blade, lvl);
        player.getInventory().setItemInMainHand(blade);
        player.sendMessage(MessageUtil.success("Verdant Fang Sharpness: <white>" + lvl + "</white>."));
    }

    private void toggleMagnet(Player player, ItemStack magnet) {
        boolean active = !isMagnetActive(magnet);
        setMagnetActive(magnet, active);
        refreshMagnetTracking(player);
        player.sendMessage(MessageUtil.info("Faraday's Magnet: <white>" + (active ? "ON" : "OFF") + "</white>."));
    }

    private void useWardenBlade(Player player) {
        if (player.isSneaking()) {
            useWardenBladeProtection(player);
            return;
        }
        useWardenBladeSoundWave(player);
    }

    private void useWardenBladeProtection(Player player) {
        UUID playerId = player.getUniqueId();
        if (onCooldown(wardenBladeProtectionCd, playerId)) {
            player.sendMessage(MessageUtil.warn(
                "Sculk Protection cooldown: <white>" + secondsLeft(wardenBladeProtectionCd, playerId) + "s</white>."
            ));
            return;
        }

        applyPotionIfStrongerOrLonger(player, PotionEffectType.RESISTANCE, WARDEN_BLADE_PROTECTION_SECONDS * 20, 1);
        setCooldown(wardenBladeProtectionCd, playerId, WARDEN_BLADE_PROTECTION_COOLDOWN);

        World world = player.getWorld();
        Location center = player.getLocation().clone().add(0.0, 1.0, 0.0);
        world.spawnParticle(Particle.SCULK_SOUL, center, 28, 0.45, 0.65, 0.45, 0.02);
        world.spawnParticle(Particle.SONIC_BOOM, center.clone().add(0.0, 0.55, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
        world.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 0.75f, 1.35f);
        player.sendMessage(MessageUtil.success(
            "Sculk Protection active: <white>Resistance II</white> for <white>3 minutes</white>."
        ));
    }

    private void useWardenBladeSoundWave(Player player) {
        UUID playerId = player.getUniqueId();
        if (onCooldown(wardenBladeSoundWaveCd, playerId)) {
            player.sendMessage(MessageUtil.warn(
                "Sound Wave cooldown: <white>" + secondsLeft(wardenBladeSoundWaveCd, playerId) + "s</white>."
            ));
            return;
        }

        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().clone();
        if (direction.lengthSquared() <= 1.0E-6) {
            return;
        }
        direction.normalize();

        World world = player.getWorld();
        RayTraceResult blockHit = world.rayTraceBlocks(
            eye,
            direction,
            WARDEN_BLADE_SOUND_WAVE_RANGE,
            FluidCollisionMode.NEVER,
            true
        );
        double maxDistance = WARDEN_BLADE_SOUND_WAVE_RANGE;
        if (blockHit != null) {
            maxDistance = Math.max(0.0, blockHit.getHitPosition().distance(eye.toVector()));
        }

        double impactDistance = maxDistance;
        Location impact = eye.clone().add(direction.clone().multiply(maxDistance));
        LivingEntity target = null;
        WardenBladeSoundWaveHit hit = findWardenBladeSoundWaveTarget(player, eye, direction, maxDistance);
        if (hit != null) {
            target = hit.target();
            impactDistance = hit.distance();
            impact = hit.impact();
        }

        spawnWardenBladeSoundWaveTrail(world, eye, direction, impactDistance);
        world.spawnParticle(Particle.SONIC_BOOM, impact, 1, 0.0, 0.0, 0.0, 0.0);
        world.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f);
        setCooldown(wardenBladeSoundWaveCd, playerId, WARDEN_BLADE_SOUND_WAVE_COOLDOWN);

        if (target != null) {
            if (applyWardenBladeTrueDamage(player, target, WARDEN_BLADE_SOUND_WAVE_DAMAGE)) {
                world.spawnParticle(Particle.SCULK_SOUL, target.getLocation().clone().add(0.0, 1.0, 0.0), 16, 0.30, 0.45, 0.30, 0.02);
                world.playSound(impact, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 0.95f, 0.9f);
            }
        }
    }

    private WardenBladeSoundWaveHit findWardenBladeSoundWaveTarget(Player attacker, Location eye, Vector direction, double maxDistance) {
        if (maxDistance <= 0.0) {
            return null;
        }

        Vector start = eye.toVector();
        LivingEntity nearestTarget = null;
        Vector nearestHitPosition = null;
        double nearestDistance = maxDistance + 0.0001D;

        for (Entity entity : attacker.getNearbyEntities(maxDistance, maxDistance, maxDistance)) {
            if (!(entity instanceof LivingEntity living) || !canWardenBladeSoundWaveHit(attacker, living)) {
                continue;
            }

            BoundingBox hitbox = living.getBoundingBox().clone().expand(WARDEN_BLADE_SOUND_WAVE_HITBOX);
            RayTraceResult hit = hitbox.rayTrace(start, direction, maxDistance);
            if (hit == null || hit.getHitPosition() == null) {
                continue;
            }

            double hitDistance = hit.getHitPosition().distance(start);
            if (hitDistance >= nearestDistance) {
                continue;
            }

            nearestTarget = living;
            nearestHitPosition = hit.getHitPosition();
            nearestDistance = hitDistance;
        }

        if (nearestTarget == null || nearestHitPosition == null) {
            return null;
        }

        return new WardenBladeSoundWaveHit(
            nearestTarget,
            nearestDistance,
            nearestHitPosition.toLocation(attacker.getWorld())
        );
    }

    private void spawnWardenBladeSoundWaveTrail(World world, Location start, Vector direction, double distance) {
        for (double step = 0.75; step < distance; step += 0.65) {
            Location point = start.clone().add(direction.clone().multiply(step));
            world.spawnParticle(Particle.SCULK_SOUL, point, 2, 0.08, 0.08, 0.08, 0.0);
        }
    }

    private boolean canWardenBladeSoundWaveHit(Player attacker, LivingEntity target) {
        if (target.equals(attacker)) {
            return false;
        }
        if (target.isInvulnerable()) {
            return false;
        }
        if (target instanceof Player player
            && (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)) {
            return false;
        }
        return !(target instanceof Player teammate)
            || !sameTeamOrSelf(attacker.getUniqueId(), teammate.getUniqueId());
    }

    private boolean applyWardenBladeTrueDamage(Player attacker, LivingEntity target, double damage) {
        if (damage <= 0.0 || target.isDead() || !target.isValid()) {
            return false;
        }
        if (!canWardenBladeSoundWaveHit(attacker, target)) {
            return false;
        }

        if (target instanceof Player victim && plugin.getCombatLogListener() != null) {
            plugin.getCombatLogListener().tagPlayers(attacker, victim);
        }

        DamageNumberListener damageNumbers = plugin.getDamageNumberListener();
        if (damageNumbers != null) {
            damageNumbers.showTrueDamage(target, damage);
        }

        double nextHealth = Math.max(0.0, target.getHealth() - damage);
        if (nextHealth <= 0.0) {
            target.setHealth(0.0);
        } else {
            target.setHealth(nextHealth);
        }
        return true;
    }

    private void tryThorsHammerThunderStrike(Player attacker, LivingEntity target) {
        UUID attackerId = attacker.getUniqueId();
        if (onCooldown(thorsHammerCd, attackerId)) {
            return;
        }

        double trueDamage = plugin.getConfigManager().thorsHammerTrueDamage;
        if (!applyWardenBladeTrueDamage(attacker, target, trueDamage)) {
            return;
        }

        setCooldown(thorsHammerCd, attackerId, plugin.getConfigManager().thorsHammerTrueDamageCooldownSeconds);
        World world = target.getWorld();
        Location impact = target.getLocation().add(0.0, 1.0, 0.0);
        world.spawnParticle(Particle.ELECTRIC_SPARK, impact, 26, 0.35, 0.55, 0.35, 0.08);
        world.spawnParticle(Particle.FLASH, impact, 1, 0.0, 0.0, 0.0, 0.0, Color.YELLOW);
        world.playSound(impact, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.65f, 1.35f);
        attacker.sendActionBar(MM.deserialize(
            "<gold><bold>Thunder Strike</bold></gold> <gray>"
                + formatDamageNumber(trueDamage / 2.0)
                + " hearts true damage</gray>"
        ));
    }

    private void useFrostScythe(Player player) {
        UUID playerId = player.getUniqueId();
        if (onCooldown(frostScytheCd, playerId)) {
            player.sendMessage(MessageUtil.warn(
                "Frost Scythe cooldown: <white>" + secondsLeft(frostScytheCd, playerId) + "s</white>."
            ));
            return;
        }

        setCooldown(frostScytheCd, playerId, plugin.getConfigManager().frostScytheAbilityCooldownSeconds);
        World world = player.getWorld();
        Location center = player.getLocation().clone().add(0.0, 1.0, 0.0);

        world.spawnParticle(Particle.SNOWFLAKE, center, 34, 1.8, 0.45, 1.8, 0.02);
        world.spawnParticle(Particle.SWEEP_ATTACK, center, 12, 1.9, 0.30, 1.9, 0.0);
        world.playSound(player.getLocation(), Sound.BLOCK_POWDER_SNOW_BREAK, 1.0f, 0.8f);
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 0.7f);

        for (LivingEntity target : world.getNearbyLivingEntities(center, FROST_SCYTHE_FREEZE_RADIUS)) {
            if (!canFrostScytheAffect(player, target)) continue;
            applyFrostScytheNausea(target);
            applyFrostScytheFreeze(target);
            world.spawnParticle(Particle.SNOWFLAKE, target.getLocation().clone().add(0.0, 1.0, 0.0), 8, 0.30, 0.45, 0.30, 0.01);
        }
    }

    private void useDivineAxeRhitta(Player player) {
        if (player.isSneaking()) {
            useRhittaCruelSun(player);
            return;
        }
        useRhittaSunsBlessing(player);
    }

    private void useRhittaSunsBlessing(Player player) {
        UUID playerId = player.getUniqueId();
        if (onCooldown(rhittaBlessingCd, playerId)) {
            player.sendMessage(MessageUtil.warn(
                "Sun's Blessing cooldown: <white>" + secondsLeft(rhittaBlessingCd, playerId) + "s</white>."
            ));
            return;
        }

        applyPotionIfStrongerOrLonger(player, PotionEffectType.FIRE_RESISTANCE, RHITTA_SUNS_BLESSING_SECONDS * 20, 0);
        applyPotionIfStrongerOrLonger(player, PotionEffectType.SPEED, RHITTA_SUNS_BLESSING_SECONDS * 20, 1);
        setCooldown(rhittaBlessingCd, playerId, RHITTA_SUNS_BLESSING_COOLDOWN);

        World world = player.getWorld();
        Location center = player.getLocation().clone().add(0.0, 1.0, 0.0);
        world.spawnParticle(Particle.FLAME, center, 38, 0.45, 0.65, 0.45, 0.02);
        world.spawnParticle(Particle.SMALL_FLAME, center, 24, 0.30, 0.45, 0.30, 0.02);
        world.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.1f);
        player.sendMessage(MessageUtil.success(
            "Sun's Blessing active: <white>Fire Resistance</white> and <white>Speed II</white> for <white>5 minutes</white>."
        ));
    }

    private void useRhittaCruelSun(Player player) {
        UUID playerId = player.getUniqueId();
        if (onCooldown(rhittaCruelSunCd, playerId)) {
            player.sendMessage(MessageUtil.warn(
                "Cruel Sun cooldown: <white>" + secondsLeft(rhittaCruelSunCd, playerId) + "s</white>."
            ));
            return;
        }

        World world = player.getWorld();
        Location center = player.getLocation().clone().add(0.0, 1.0, 0.0);
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity target : world.getNearbyLivingEntities(center, RHITTA_CRUEL_SUN_RADIUS)) {
            if (canRhittaBurnTarget(player, target)) {
                targets.add(target);
            }
        }
        if (targets.isEmpty()) {
            player.sendMessage(MessageUtil.warn("No enemies were close enough for Cruel Sun."));
            return;
        }

        setCooldown(rhittaCruelSunCd, playerId, RHITTA_CRUEL_SUN_COOLDOWN);
        world.spawnParticle(Particle.FLAME, center, 52, 1.8, 0.35, 1.8, 0.03);
        world.spawnParticle(Particle.SMALL_FLAME, center, 34, 1.4, 0.25, 1.4, 0.02);
        world.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.8f);

        for (LivingEntity target : targets) {
            applyRhittaCruelSun(player, target);
        }
        player.sendMessage(MessageUtil.success(
            "Cruel Sun scorched <white>" + targets.size() + "</white> nearby " + (targets.size() == 1 ? "enemy" : "enemies") + "."
        ));
    }

    private void useShadowBlade(Player player) {
        UUID playerId = player.getUniqueId();
        if (onCooldown(shadowBladeCd, playerId)) {
            player.sendMessage(MessageUtil.warn(
                "Nightfall cooldown: <white>" + secondsLeft(shadowBladeCd, playerId) + "s</white>."
            ));
            return;
        }

        int durationSeconds = shadowBladeDurationSeconds(player);
        shadowBladeActiveUntil.put(playerId, System.currentTimeMillis() + (durationSeconds * 1000L));
        setCooldown(shadowBladeCd, playerId, SHADOW_BLADE_COOLDOWN);
        applyPotionIfStrongerOrLonger(player, PotionEffectType.INVISIBILITY, durationSeconds * 20, 0);
        applyPotionIfStrongerOrLonger(player, PotionEffectType.SPEED, durationSeconds * 20, 2);

        World world = player.getWorld();
        Location center = player.getLocation().clone().add(0.0, 1.0, 0.0);
        world.spawnParticle(Particle.SMOKE, center, 24, 0.35, 0.55, 0.35, 0.01);
        world.spawnParticle(Particle.PORTAL, center, 18, 0.25, 0.45, 0.25, 0.01);
        world.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.75f);
        player.sendMessage(MessageUtil.success(
            "Nightfall cloaked you for <white>" + durationSeconds + "s</white>."
        ));
    }

    private void cancelShadowBlade(Player player, boolean interrupted) {
        if (player == null) {
            return;
        }
        Long removed = shadowBladeActiveUntil.remove(player.getUniqueId());
        if (removed == null) {
            return;
        }

        removeTemporaryLegendaryPotion(player, PotionEffectType.INVISIBILITY, 0, SHADOW_BLADE_SHADOW_DURATION_SECONDS * 20);
        removeTemporaryLegendaryPotion(player, PotionEffectType.SPEED, 2, SHADOW_BLADE_SHADOW_DURATION_SECONDS * 20);
        if (interrupted) {
            player.sendMessage(MessageUtil.warn("Nightfall faded after you struck a player."));
        }
    }

    private void useStrengthSwordBeam(Player player) {
        ItemStack sword = player.getInventory().getItemInMainHand();
        int stage = strengthSwordStage(sword);
        if (stage < 2) {
            player.sendMessage(MessageUtil.warn(
                "Crimson Dominion beam unlocks after <white>" + STRENGTH_SWORD_STAGE_TWO_KILLS + "</white> unique player kills."
            ));
            return;
        }

        UUID playerId = player.getUniqueId();
        if (onCooldown(strengthSwordBeamCd, playerId)) {
            player.sendMessage(MessageUtil.warn(
                "Crimson Dominion beam cooldown: <white>" + secondsLeft(strengthSwordBeamCd, playerId) + "s</white>."
            ));
            return;
        }

        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().clone();
        if (direction.lengthSquared() <= 1.0E-6) {
            return;
        }
        direction.normalize();

        World world = player.getWorld();
        RayTraceResult blockHit = world.rayTraceBlocks(
            eye,
            direction,
            WARDEN_BLADE_SOUND_WAVE_RANGE,
            FluidCollisionMode.NEVER,
            true
        );
        double maxDistance = WARDEN_BLADE_SOUND_WAVE_RANGE;
        if (blockHit != null) {
            maxDistance = Math.max(0.0, blockHit.getHitPosition().distance(eye.toVector()));
        }

        double impactDistance = maxDistance;
        Location impact = eye.clone().add(direction.clone().multiply(maxDistance));
        WardenBladeSoundWaveHit hit = findWardenBladeSoundWaveTarget(player, eye, direction, maxDistance);
        LivingEntity target = null;
        if (hit != null) {
            target = hit.target();
            impactDistance = hit.distance();
            impact = hit.impact();
        }

        spawnStrengthSwordBeamTrail(world, eye, direction, impactDistance);
        world.spawnParticle(Particle.SONIC_BOOM, impact, 1, 0.0, 0.0, 0.0, 0.0);
        world.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.8f);
        setCooldown(strengthSwordBeamCd, playerId, STRENGTH_SWORD_BEAM_COOLDOWN);
        damageStrengthSwordAbilityUse(player, 1);

        if (target != null && applyWardenBladeTrueDamage(player, target, STRENGTH_SWORD_BEAM_TRUE_DAMAGE)) {
            world.spawnParticle(Particle.DUST, target.getLocation().clone().add(0.0, 1.0, 0.0), 18, 0.25, 0.45, 0.25, 0.0, new Particle.DustOptions(Color.fromRGB(255, 60, 80), 1.2f));
        }
    }

    private void spawnStrengthSwordBeamTrail(World world, Location start, Vector direction, double distance) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 60, 80), 1.25f);
        for (double step = 0.60; step < distance; step += 0.55) {
            Location point = start.clone().add(direction.clone().multiply(step));
            world.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, dust);
            world.spawnParticle(Particle.SMALL_FLAME, point, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private void useStrengthSwordDomain(Player player) {
        ItemStack sword = player.getInventory().getItemInMainHand();
        int stage = strengthSwordStage(sword);
        if (stage < 3) {
            player.sendMessage(MessageUtil.warn(
                "Crimson Dominion domain unlocks after <white>" + STRENGTH_SWORD_STAGE_THREE_KILLS + "</white> unique player kills."
            ));
            return;
        }

        UUID playerId = player.getUniqueId();
        if (onCooldown(strengthSwordDomainCd, playerId)) {
            player.sendMessage(MessageUtil.warn(
                "Crimson Dominion domain cooldown: <white>" + secondsLeft(strengthSwordDomainCd, playerId) + "s</white>."
            ));
            return;
        }

        long expiresAt = System.currentTimeMillis() + (STRENGTH_SWORD_DOMAIN_SECONDS * 1000L);
        activeStrengthDomains.put(playerId, new StrengthDomainState(player.getLocation().clone(), expiresAt));
        setCooldown(strengthSwordDomainCd, playerId, STRENGTH_SWORD_DOMAIN_COOLDOWN);

        World world = player.getWorld();
        Location center = player.getLocation().clone().add(0.0, 0.15, 0.0);
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 0.7f);
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 0.6f);
        world.spawnParticle(Particle.ENCHANT, center.clone().add(0.0, 1.0, 0.0), 42, 1.2, 0.6, 1.2, 0.03);
        world.spawnParticle(Particle.DUST, center.clone().add(0.0, 0.3, 0.0), 28, STRENGTH_SWORD_DOMAIN_RADIUS, 0.1, STRENGTH_SWORD_DOMAIN_RADIUS, 0.0, new Particle.DustOptions(Color.fromRGB(255, 70, 100), 1.3f));
        damageStrengthSwordAbilityUse(player, 1);
        player.sendMessage(MessageUtil.success(
            "Crimson Dominion domain opened for <white>" + STRENGTH_SWORD_DOMAIN_SECONDS + "s</white>."
        ));
    }

    private void applyRhittaCruelSun(Player attacker, LivingEntity target) {
        long expiresAt = System.currentTimeMillis() + (RHITTA_CRUEL_SUN_FIRE_SECONDS * 1000L);
        rhittaBurns.put(target.getUniqueId(), new RhittaBurnState(attacker.getUniqueId(), expiresAt));
        target.setFireTicks(Math.max(target.getFireTicks(), RHITTA_CRUEL_SUN_FIRE_SECONDS * 20));
        target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().clone().add(0.0, 1.0, 0.0), 12, 0.25, 0.45, 0.25, 0.01);
        scheduleRhittaCruelSunTick(target.getUniqueId(), attacker.getUniqueId(), expiresAt, RHITTA_CRUEL_SUN_FIRE_SECONDS);
    }

    private void scheduleRhittaCruelSunTick(UUID targetId, UUID attackerId, long expiresAt, int ticksRemaining) {
        if (ticksRemaining <= 0) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            RhittaBurnState state = rhittaBurns.get(targetId);
            if (state == null || !state.attackerId().equals(attackerId) || state.expiresAt() != expiresAt) {
                return;
            }
            Entity entity = Bukkit.getEntity(targetId);
            Player attacker = Bukkit.getPlayer(attackerId);
            if (!(entity instanceof LivingEntity target) || !target.isValid() || target.isDead() || attacker == null || !attacker.isOnline()) {
                rhittaBurns.remove(targetId, state);
                return;
            }
            if (System.currentTimeMillis() > expiresAt) {
                rhittaBurns.remove(targetId, state);
                return;
            }

            target.damage(RHITTA_CRUEL_SUN_TICK_DAMAGE, attacker);
            target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().clone().add(0.0, 1.0, 0.0), 8, 0.20, 0.40, 0.20, 0.01);
            if (ticksRemaining <= 1) {
                rhittaBurns.remove(targetId, state);
                return;
            }
            scheduleRhittaCruelSunTick(targetId, attackerId, expiresAt, ticksRemaining - 1);
        }, 20L);
    }

    private void useWindChargeCannon(Player player, ItemStack cannon, boolean superShot) {
        if (!refreshWindChargeCannonState(cannon)) {
            return;
        }

        int charges = windChargeCannonCharges(cannon);
        if (charges <= 0) {
            player.sendMessage(MessageUtil.warn(
                "Tempest Cannon recharging: <white>" + windChargeCannonSecondsLeft(cannon) + "s</white>."));
            return;
        }

        int nextCharges = charges - 1;
        long cooldownUntil = nextCharges <= 0
            ? System.currentTimeMillis() + (WIND_CHARGE_CANNON_RECHARGE * 1000L)
            : 0L;
        setWindChargeCannonState(cannon, nextCharges, cooldownUntil);

        if (superShot) {
            Vector direction = player.getLocation().getDirection().normalize();
            Vector current = player.getVelocity();
            Vector boost = direction.multiply(0.9 * WIND_CHARGE_CANNON_SUPER_SHOT_STRENGTH);
            boost.setY(Math.max(
                1.8 * WIND_CHARGE_CANNON_SUPER_SHOT_STRENGTH,
                (direction.getY() * (0.55 * WIND_CHARGE_CANNON_SUPER_SHOT_STRENGTH))
                    + (1.8 * WIND_CHARGE_CANNON_SUPER_SHOT_STRENGTH)
            ));

            Vector next = current.add(boost);
            next.setX(Math.max(-4.0, Math.min(4.0, next.getX())));
            next.setZ(Math.max(-4.0, Math.min(4.0, next.getZ())));
            player.setVelocity(next);
            player.setFallDistance(0.0f);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 1.15f);
            sendWindChargeCannonActionBar(player, nextCharges);
            return;
        }

        WindCharge charge = player.launchProjectile(WindCharge.class);
        charge.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(1.5 * WIND_CHARGE_CANNON_NORMAL_SHOT_STRENGTH));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1.0f, 1.0f);
        sendWindChargeCannonActionBar(player, nextCharges);
    }

    private void useExecutionerBlade(Player player) {
        if (player.isSneaking()) {
            useExecutionerShockwave(player);
            return;
        }

        UUID playerId = player.getUniqueId();
        if (onCooldown(executionerStrengthCd, playerId)) {
            player.sendMessage(MessageUtil.warn(
                "Executioner fury cooldown: <white>" + secondsLeft(executionerStrengthCd, playerId) + "s</white>."
            ));
            return;
        }

        int durationTicks = EXECUTIONER_BLADE_STRENGTH_SECONDS * 20;
        PotionEffect current = player.getPotionEffect(PotionEffectType.STRENGTH);
        if (current == null
            || current.getAmplifier() < EXECUTIONER_BLADE_STRENGTH_AMPLIFIER
            || (current.getAmplifier() == EXECUTIONER_BLADE_STRENGTH_AMPLIFIER && current.getDuration() < durationTicks)) {
            player.removePotionEffect(PotionEffectType.STRENGTH);
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.STRENGTH,
                durationTicks,
                EXECUTIONER_BLADE_STRENGTH_AMPLIFIER,
                false,
                true,
                true
            ));
        }
        setCooldown(executionerStrengthCd, playerId, EXECUTIONER_BLADE_STRENGTH_COOLDOWN);

        Location center = player.getLocation().clone().add(0.0, 1.0, 0.0);
        World world = player.getWorld();
        world.spawnParticle(Particle.CRIT, center, 24, 0.35, 0.5, 0.35, 0.02);
        world.spawnParticle(Particle.ASH, center, 10, 0.20, 0.35, 0.20, 0.01);
        world.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.85f, 0.7f);
        player.sendMessage(MessageUtil.success(
            "Executioner fury active: <white>Strength II</white> for <white>6 minutes</white>."
        ));
    }

    private void useExecutionerShockwave(Player player) {
        UUID playerId = player.getUniqueId();
        if (onCooldown(executionerShockwaveCd, playerId)) {
            player.sendMessage(MessageUtil.warn(
                "Executioner shockwave cooldown: <white>" + secondsLeft(executionerShockwaveCd, playerId) + "s</white>."
            ));
            return;
        }
        setCooldown(executionerShockwaveCd, playerId, EXECUTIONER_BLADE_SHOCKWAVE_COOLDOWN);

        World world = player.getWorld();
        Location center = player.getLocation();
        world.spawnParticle(Particle.CLOUD, center.clone().add(0.0, 0.15, 0.0), 42, 1.8, 0.10, 1.8, 0.08);
        world.spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(0.0, 1.0, 0.0), 16, 2.2, 0.25, 2.2, 0.0);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
        world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.65f);

        for (Player target : world.getNearbyPlayers(center, EXECUTIONER_BLADE_SHOCKWAVE_RADIUS)) {
            if (sameTeamOrSelf(player.getUniqueId(), target.getUniqueId())) continue;

            Vector launch = target.getLocation().toVector().subtract(center.toVector());
            launch.setY(0.0);
            if (launch.lengthSquared() > 0.0001) {
                launch.normalize().multiply(EXECUTIONER_BLADE_SHOCKWAVE_HORIZONTAL);
            } else {
                launch.zero();
            }
            launch.setY(Math.max(EXECUTIONER_BLADE_SHOCKWAVE_VERTICAL, target.getVelocity().getY()));

            target.setVelocity(launch);
            target.removePotionEffect(PotionEffectType.SLOWNESS);
            target.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS,
                EXECUTIONER_BLADE_STUN_SECONDS * 20,
                255,
                false,
                true,
                true
            ));
            target.removePotionEffect(PotionEffectType.JUMP_BOOST);
            target.addPotionEffect(new PotionEffect(
                PotionEffectType.JUMP_BOOST,
                EXECUTIONER_BLADE_STUN_SECONDS * 20,
                128,
                false,
                false,
                true
            ));
            world.spawnParticle(Particle.ASH, target.getLocation().clone().add(0.0, 1.0, 0.0), 12, 0.25, 0.45, 0.25, 0.01);
        }
    }

    private void sweepFrostScytheTargets(Player attacker, LivingEntity primaryTarget) {
        World world = attacker.getWorld();
        Location eye = attacker.getEyeLocation();
        Vector facing = eye.getDirection().normalize();

        world.spawnParticle(Particle.SWEEP_ATTACK, eye.clone().add(facing.clone().multiply(1.4)), 1, 0.0, 0.0, 0.0, 0.0);
        world.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.2f);

        for (LivingEntity target : world.getNearbyLivingEntities(attacker.getLocation(), FROST_SCYTHE_SWEEP_RANGE)) {
            if (target.equals(primaryTarget) || !canFrostScytheAffect(attacker, target)) continue;
            if (!attacker.hasLineOfSight(target)) continue;

            Vector toTarget = target.getEyeLocation().toVector().subtract(eye.toVector());
            double distance = toTarget.length();
            if (distance <= 0.0001 || distance > FROST_SCYTHE_SWEEP_RANGE) continue;
            if (facing.dot(toTarget.normalize()) < FROST_SCYTHE_SWEEP_DOT) continue;

            target.damage(FROST_SCYTHE_MELEE_DAMAGE, attacker);
        }
    }

    private void useWitherBlade(Player player, ItemStack blade) {
        if (!refreshWitherBladeState(blade, player)) {
            return;
        }

        WitherBladeState state = witherBladeState(blade);
        if (player.isSneaking()) {
            if (state.dashCharges() <= 0) {
                player.sendMessage(MessageUtil.warn(
                    "Wither dash recharging: <white>" + formatSeconds(state.dashMillisUntilNext()) + "s</white>."
                ));
                sendWitherBladeActionBar(player, state);
                return;
            }

            state = spendWitherBladeDash(state);
            applyWitherBladeState(blade, state);

            Vector direction = player.getLocation().getDirection().normalize();
            Vector current = player.getVelocity();
            Vector boost = direction.multiply(WITHER_BLADE_DASH_HORIZONTAL);
            boost.setY(Math.max(WITHER_BLADE_DASH_VERTICAL, (direction.getY() * 0.35) + WITHER_BLADE_DASH_VERTICAL));

            Vector next = current.add(boost);
            next.setX(Math.max(-3.0, Math.min(3.0, next.getX())));
            next.setZ(Math.max(-3.0, Math.min(3.0, next.getZ())));
            next.setY(Math.max(next.getY(), WITHER_BLADE_DASH_VERTICAL));
            player.setVelocity(next);
            player.setFallDistance(0.0f);
            scheduleWitherBladeDashTrail(player);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.85f);
            sendWitherBladeActionBar(player, state);
            return;
        }

        if (state.skullCharges() <= 0) {
            player.sendMessage(MessageUtil.warn(
                "Wither skulls recharging: <white>" + formatSeconds(state.skullMillisUntilNext()) + "s</white>."
            ));
            sendWitherBladeActionBar(player, state);
            return;
        }

        state = spendWitherBladeSkull(state);
        applyWitherBladeState(blade, state);

        WitherSkull skull = player.launchProjectile(WitherSkull.class);
        skull.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(WITHER_BLADE_SKULL_SPEED));
        skull.setCharged(false);
        skull.setYield(0.0f);
        skull.setIsIncendiary(false);
        skull.getPersistentDataContainer().set(keyWitherBladeSkullTag, PersistentDataType.BYTE, (byte) 1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.0f, 1.0f);
        sendWitherBladeActionBar(player, state);
    }

    private void useDashMace(Player player) {
        UUID playerId = player.getUniqueId();
        if (onCooldown(dashMaceCd, playerId)) {
            player.sendMessage(MessageUtil.warn("Dash Mace cooldown: <white>" + secondsLeft(dashMaceCd, playerId) + "s</white>."));
            return;
        }

        Vector direction = player.getEyeLocation().getDirection();
        if (direction.lengthSquared() < 0.0001) {
            return;
        }
        direction.normalize();

        Vector velocity = direction.multiply(DASH_MACE_HORIZONTAL);
        velocity.setY(Math.max(DASH_MACE_VERTICAL, velocity.getY() * 0.45 + DASH_MACE_VERTICAL));
        player.setVelocity(velocity);
        player.setFallDistance(0.0f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0.0, 0.35, 0.0), 26, 0.45, 0.2, 0.45, 0.03);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1.1f, 0.9f);
        setCooldown(dashMaceCd, playerId, DASH_MACE_COOLDOWN);
    }

    private double witherBladeBonusDamage(Player player) {
        return witherBladeBonusDamage(witherBladeSkullCount(player));
    }

    private double executionerBladeBonusDamage(Player player) {
        return Math.min(
            EXECUTIONER_BLADE_SKULL_DAMAGE_CAP,
            countPlayerInventorySkulls(player)
        );
    }

    private void handleWitherBladeSkullHit(ProjectileHitEvent event, WitherSkull skull) {
        if (!(skull.getShooter() instanceof Player shooter)) {
            skull.remove();
            return;
        }

        if (event.getHitEntity() instanceof LivingEntity living && canWitherBladeDamage(shooter, living)) {
            living.damage(WITHER_BLADE_DIRECT_HIT_DAMAGE, shooter);
            living.addPotionEffect(new PotionEffect(
                PotionEffectType.WITHER,
                WITHER_BLADE_WITHER_SECONDS * 20,
                0,
                false,
                true,
                true
            ));
        }

        Location hit = event.getHitBlock() != null
            ? event.getHitBlock().getLocation().add(0.5, 0.5, 0.5)
            : skull.getLocation();
        spawnWitherBladeImpactParticles(hit);
        applyWitherBladeSplash(shooter, hit);
        skull.remove();
    }

    private boolean canWitherBladeDamage(Player shooter, LivingEntity target) {
        if (target.equals(shooter)) return false;
        return !(target instanceof Player teammate)
            || !sameTeamOrSelf(shooter.getUniqueId(), teammate.getUniqueId());
    }

    private boolean canFrostScytheAffect(Player attacker, LivingEntity target) {
        if (target.equals(attacker)) return false;
        return !(target instanceof Player teammate)
            || !sameTeamOrSelf(attacker.getUniqueId(), teammate.getUniqueId());
    }

    private boolean canRhittaBurnTarget(Player attacker, LivingEntity target) {
        if (target.equals(attacker)) {
            return false;
        }
        return !(target instanceof Player teammate)
            || !sameTeamOrSelf(attacker.getUniqueId(), teammate.getUniqueId());
    }

    private void applyPotionIfStrongerOrLonger(LivingEntity entity, PotionEffectType type, int durationTicks, int amplifier) {
        PotionEffect current = entity.getPotionEffect(type);
        if (current != null
            && (current.getAmplifier() > amplifier
            || (current.getAmplifier() == amplifier && current.getDuration() >= durationTicks))) {
            return;
        }

        entity.removePotionEffect(type);
        entity.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, false, true, true));
    }

    private void applyFrostScytheNausea(LivingEntity target) {
        target.addPotionEffect(new PotionEffect(
            PotionEffectType.NAUSEA,
            FROST_SCYTHE_NAUSEA_TICKS,
            0,
            false,
            true,
            true
        ));
    }

    private void applyFrostScytheFreeze(LivingEntity target) {
        UUID targetId = target.getUniqueId();
        FrostScytheFreezeState existing = frostScytheFrozen.get(targetId);
        int previousFreezeTicks = existing != null ? existing.previousFreezeTicks() : target.getFreezeTicks();
        boolean previousFreezeLocked = existing != null ? existing.previousFreezeLocked() : target.isFreezeTickingLocked();
        long expiresAt = System.currentTimeMillis() + (FROST_SCYTHE_FREEZE_TICKS * 50L);

        frostScytheFrozen.put(targetId, new FrostScytheFreezeState(expiresAt, previousFreezeTicks, previousFreezeLocked));
        target.setFreezeTicks(Math.max(target.getMaxFreezeTicks(), FROST_SCYTHE_FREEZE_TICKS));
        target.lockFreezeTicks(true);
        target.removePotionEffect(PotionEffectType.SLOWNESS);
        target.addPotionEffect(new PotionEffect(
            PotionEffectType.SLOWNESS,
            FROST_SCYTHE_FREEZE_TICKS,
            255,
            false,
            true,
            true
        ));
        target.removePotionEffect(PotionEffectType.JUMP_BOOST);
        target.addPotionEffect(new PotionEffect(
            PotionEffectType.JUMP_BOOST,
            FROST_SCYTHE_FREEZE_TICKS,
            128,
            false,
            false,
            true
        ));

        Bukkit.getScheduler().runTaskLater(plugin, () -> releaseFrostScytheFreeze(targetId, expiresAt), FROST_SCYTHE_FREEZE_TICKS);
    }

    private void releaseFrostScytheFreeze(UUID targetId, long expiresAt) {
        FrostScytheFreezeState state = frostScytheFrozen.get(targetId);
        if (state == null || state.expiresAt() != expiresAt) return;

        frostScytheFrozen.remove(targetId);
        Entity entity = Bukkit.getEntity(targetId);
        if (!(entity instanceof LivingEntity target) || !entity.isValid() || entity.isDead()) return;

        target.lockFreezeTicks(state.previousFreezeLocked());
        target.setFreezeTicks(state.previousFreezeTicks());
    }

    private void applyWitherBladeSplash(Player shooter, Location center) {
        World world = center.getWorld();
        if (world == null) return;

        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.95f);

        for (LivingEntity living : world.getNearbyLivingEntities(center, WITHER_BLADE_SPLASH_RADIUS)) {
            if (!canWitherBladeDamage(shooter, living)) continue;

            double distance = living.getLocation().distance(center);
            if (distance > WITHER_BLADE_SPLASH_RADIUS) continue;

            double scale = 1.0 - (distance / WITHER_BLADE_SPLASH_RADIUS);
            if (scale <= 0.0) continue;

            living.damage(Math.max(1.0, WITHER_BLADE_SPLASH_DAMAGE * scale), shooter);

            Vector knockback = living.getLocation().toVector().subtract(center.toVector());
            if (knockback.lengthSquared() > 0.0001) {
                knockback.normalize().multiply(0.25 + (0.45 * scale)).setY(Math.max(0.18, 0.28 * scale));
                living.setVelocity(living.getVelocity().add(knockback));
            }
        }
    }

    private void scheduleWitherBladeDashTrail(Player player) {
        UUID playerId = player.getUniqueId();
        for (int tick = 0; tick < 7; tick++) {
            long delay = tick;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player online = Bukkit.getPlayer(playerId);
                if (online == null || !online.isOnline()) return;
                Location point = online.getLocation().clone().add(0.0, 1.0, 0.0);
                spawnBlackDragonBreath(online.getWorld(), point, 4, 0.12, 0.03);
            }, delay);
        }
    }

    private void spawnWitherBladeImpactParticles(Location location) {
        World world = location.getWorld();
        if (world == null) return;
        spawnBlackDragonBreath(world, location.clone().add(0.0, 0.1, 0.0), 30, 0.30, 0.02);
        world.spawnParticle(Particle.SMOKE, location, 16, 0.25, 0.25, 0.25, 0.01);
    }

    private void spawnBlackDragonBreath(World world, Location location, int dragonBreathCount, double spread, double speed) {
        world.spawnParticle(
            Particle.DRAGON_BREATH,
            location,
            dragonBreathCount,
            spread, spread, spread,
            speed,
            Float.valueOf(1.0f)
        );
        world.spawnParticle(
            Particle.ENTITY_EFFECT,
            location,
            Math.max(6, dragonBreathCount / 2),
            spread, spread, spread,
            0.0,
            WITHER_BLADE_PARTICLE_COLOR
        );
        world.spawnParticle(Particle.SMOKE, location, Math.max(2, dragonBreathCount / 3), spread, spread * 0.6, spread, 0.01);
    }

    private void tickMagnets() {
        for (UUID playerId : new HashSet<>(activeMagnetPlayers)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline() || player.isDead()) {
                activeMagnetPlayers.remove(playerId);
                continue;
            }

            ItemStack magnet = findActiveMagnet(player);
            if (magnet == null) {
                activeMagnetPlayers.remove(playerId);
                continue;
            }
            if (!canAcceptAnyItem(player)) continue;
            pullNearbyItems(player);
        }
    }

    private void tickPercyTridentEffects() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ensurePercyTridentEffects(player);
        }
    }

    private void ensurePercyTridentEffects(Player player) {
        if (!isPercyTridentHeld(player)) {
            return;
        }

        PotionEffect current = player.getPotionEffect(PotionEffectType.WATER_BREATHING);
        if (current != null && current.getDuration() > PERCY_TRIDENT_WATER_BREATHING_TICKS / 2) {
            return;
        }

        player.addPotionEffect(new PotionEffect(
            PotionEffectType.WATER_BREATHING,
            PERCY_TRIDENT_WATER_BREATHING_TICKS,
            0,
            false,
            false,
            true
        ));
    }

    private boolean isPercyTridentHeld(Player player) {
        return typeOf(player.getInventory().getItemInMainHand()) == LegendaryType.TRIDENT_OF_PERCY
            || typeOf(player.getInventory().getItemInOffHand()) == LegendaryType.TRIDENT_OF_PERCY;
    }

    private void queueMagnetTrackingRefresh(Player player) {
        UUID playerId = player.getUniqueId();
        if (!pendingMagnetRefresh.add(playerId)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingMagnetRefresh.remove(playerId);
            if (!player.isOnline()) {
                activeMagnetPlayers.remove(playerId);
                return;
            }
            refreshMagnetTracking(player);
        });
    }

    private void queueWitherBladeLoreRefresh(Player player) {
        UUID playerId = player.getUniqueId();
        if (!pendingWitherBladeLoreRefresh.add(playerId)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingWitherBladeLoreRefresh.remove(playerId);
            if (!player.isOnline()) return;
            refreshWitherBladeLore(player);
        });
    }

    private void refreshWitherBladeLore(Player player) {
        int skullCount = witherBladeSkullCount(player);
        double bonusDamage = witherBladeBonusDamage(skullCount);

        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (typeOf(item) != LegendaryType.WITHER_BLADE) continue;

            WitherBladeState state = witherBladeState(item);
            applyWitherBladeState(item, state, skullCount, bonusDamage);
            player.getInventory().setItem(slot, item);
        }

        ItemStack cursor = player.getItemOnCursor();
        if (typeOf(cursor) == LegendaryType.WITHER_BLADE) {
            WitherBladeState state = witherBladeState(cursor);
            applyWitherBladeState(cursor, state, skullCount, bonusDamage);
            player.setItemOnCursor(cursor);
        }
    }

    private void tickLifeStealerLore() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshLifeStealerLore(player);
        }
    }

    private void refreshLifeStealerLore(Player player) {
        LifeStealerState strongestState = LifeStealerState.empty();
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (typeOf(item) != LegendaryType.LIFE_STEALER) continue;

            LifeStealerState state = lifeStealerState(item);
            strongestState = strongerLifeStealerState(strongestState, state);
            applyLifeStealerState(item, state);
            player.getInventory().setItem(slot, item);
        }

        ItemStack cursor = player.getItemOnCursor();
        if (typeOf(cursor) == LegendaryType.LIFE_STEALER) {
            LifeStealerState state = lifeStealerState(cursor);
            strongestState = strongerLifeStealerState(strongestState, state);
            applyLifeStealerState(cursor, state);
            player.setItemOnCursor(cursor);
        }

        ensureLifeStealerEffects(player, strongestState);
    }

    private void refreshMagnetTracking(Player player) {
        if (findActiveMagnet(player) == null) {
            activeMagnetPlayers.remove(player.getUniqueId());
            return;
        }
        activeMagnetPlayers.add(player.getUniqueId());
    }

    private ItemStack findActiveMagnet(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (typeOf(item) != LegendaryType.FARADAYS_MAGNET) continue;
            if (isMagnetActive(item)) return item;
        }
        return null;
    }

    private void pullNearbyItems(Player player) {
        for (Entity entity : player.getWorld().getNearbyEntities(
            player.getLocation(),
            MAGNET_RADIUS, MAGNET_RADIUS, MAGNET_RADIUS,
            candidate -> candidate instanceof Item item && item.getPickupDelay() <= 0
        )) {
            Item dropped = (Item) entity;
            if (!dropped.isValid() || dropped.isDead()) continue;
            ItemStack stack = dropped.getItemStack();
            if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) continue;

            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack.clone());
            if (leftovers.isEmpty()) {
                dropped.remove();
                continue;
            }

            int remaining = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            if (remaining <= 0) {
                dropped.remove();
                continue;
            }
            if (remaining < stack.getAmount()) {
                ItemStack updated = stack.clone();
                int maxStack = Math.max(1, updated.getMaxStackSize());
                updated.setAmount(Math.min(maxStack, remaining));
                dropped.setItemStack(updated);
                int overflow = remaining - updated.getAmount();
                while (overflow > 0) {
                    ItemStack overflowStack = stack.clone();
                    overflowStack.setAmount(Math.min(maxStack, overflow));
                    player.getWorld().dropItemNaturally(dropped.getLocation(), overflowStack);
                    overflow -= overflowStack.getAmount();
                }
            }
        }
    }

    private boolean canAcceptAnyItem(Player player) {
        for (ItemStack content : player.getInventory().getContents()) {
            if (content == null || content.getType() == Material.AIR) return true;
            if (content.getAmount() < content.getMaxStackSize()) return true;
        }
        return false;
    }

    private void damageArmorPiece(Player victim) {
        ItemStack[] armor = victim.getInventory().getArmorContents();
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < armor.length; i++) {
            ItemStack it = armor[i];
            if (it == null || it.getType() == Material.AIR) continue;
            if (it.getType().getMaxDurability() <= 0) continue;
            candidates.add(i);
        }
        if (candidates.isEmpty()) return;

        int idx = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        ItemStack piece = armor[idx];
        ItemMeta meta = piece.getItemMeta();
        if (!(meta instanceof Damageable dmg)) return;

        int add = ThreadLocalRandom.current().nextInt(8, 21);
        int max = piece.getType().getMaxDurability();
        int next = dmg.getDamage() + add;
        if (next >= max) {
            armor[idx] = new ItemStack(Material.AIR);
        } else {
            dmg.setDamage(next);
            piece.setItemMeta((ItemMeta) dmg);
            armor[idx] = piece;
        }
        victim.getInventory().setArmorContents(armor);
    }

    private boolean isCritical(Player player) {
        return player.getFallDistance() > 0.0f
            && player.getVelocity().getY() < 0.0
            && !player.isInsideVehicle()
            && !player.isSprinting()
            && player.getAttackCooldown() > 0.9f;
    }

    private void healMob(Mob mob, double amount) {
        var attr = mob.getAttribute(Attribute.MAX_HEALTH);
        double max = attr == null ? mob.getHealth() : attr.getValue();
        mob.setHealth(Math.min(max, mob.getHealth() + amount));
    }

    private void healLivingEntity(LivingEntity entity, double amount) {
        if (amount <= 0.0 || entity.isDead() || !entity.isValid()) {
            return;
        }
        var attr = entity.getAttribute(Attribute.MAX_HEALTH);
        double max = attr == null ? entity.getHealth() : attr.getValue();
        entity.setHealth(Math.min(max, entity.getHealth() + amount));
    }

    private void grantLifeStealerKillBuff(Player killer, ItemStack weapon, UUID victimId) {
        if (typeOf(weapon) != LegendaryType.LIFE_STEALER) {
            return;
        }

        LifeStealerState state = lifeStealerState(weapon);
        if (!state.seenVictims().contains(victimId) && state.stacks() < LIFE_STEALER_MAX_STACKS) {
            state = state.addVictim(victimId);
        }

        ItemMeta meta = weapon.getItemMeta();
        if (meta != null) {
            setLifeStealerSharpness(meta, lifeStealerSharpness(meta) + 1);
            applyLifeStealerState(meta, state);
            weapon.setItemMeta(meta);
        }
        applyLifeStealerEffects(killer, state);
        refreshLifeStealerLore(killer);
    }

    private void levelMidasSword(ItemStack sword, boolean playerKill) {
        if (typeOf(sword) != LegendaryType.MIDAS_SWORD || !playerKill) {
            return;
        }

        ItemMeta meta = sword.getItemMeta();
        if (meta == null) {
            return;
        }
        int nextLevel = Math.min(MIDAS_SWORD_MAX_SHARPNESS, midasSharpness(meta) + 1);
        if (nextLevel == midasSharpness(meta)) {
            return;
        }

        meta.getPersistentDataContainer().set(keyMidasSharpness, PersistentDataType.INTEGER, nextLevel);
        setEnchantLevel(meta, enchantSharpness, nextLevel);
        meta.lore(buildMidasSwordLore(meta, nextLevel));
        sword.setItemMeta(meta);
    }

    private void grantStrengthSwordKillProgress(ItemStack sword, UUID victimId) {
        if (typeOf(sword) != LegendaryType.STRENGTH_SWORD || victimId == null) {
            return;
        }

        ItemMeta meta = sword.getItemMeta();
        if (meta == null) {
            return;
        }

        Set<UUID> victims = parseLifeStealerVictims(
            meta.getPersistentDataContainer().getOrDefault(keyStrengthSwordVictims, PersistentDataType.STRING, "")
        );
        if (!victims.add(victimId)) {
            return;
        }

        meta.getPersistentDataContainer().set(keyStrengthSwordVictims, PersistentDataType.STRING, joinLifeStealerVictims(victims));
        meta.lore(buildStrengthSwordLore(meta, victims.size()));
        sword.setItemMeta(meta);
    }

    private void tryReaperScytheDrain(Player attacker, LivingEntity victim) {
        UUID attackerId = attacker.getUniqueId();
        if (onCooldown(reaperScytheCd, attackerId) || victim.isDead() || !victim.isValid()) {
            return;
        }
        if (victim instanceof Player teammate && sameTeamOrSelf(attackerId, teammate.getUniqueId())) {
            return;
        }

        double drainDamage = Math.max(1.0, victim.getHealth() * REAPER_SCYTHE_DRAIN_FRACTION);
        setCooldown(reaperScytheCd, attackerId, REAPER_SCYTHE_DRAIN_COOLDOWN);
        if (applyWardenBladeTrueDamage(attacker, victim, drainDamage)) {
            copyBeneficialPotionEffects(victim, attacker);
            healLivingEntity(attacker, drainDamage);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, REAPER_SCYTHE_WITHER_SECONDS * 20, 1, false, true, true));
            applyPotionIfStrongerOrLonger(attacker, PotionEffectType.REGENERATION, 8 * 20, 1);
            if (attacker.getAbsorptionAmount() < REAPER_SCYTHE_ABSORPTION_HEALTH) {
                attacker.setAbsorptionAmount(REAPER_SCYTHE_ABSORPTION_HEALTH);
            }
            World world = attacker.getWorld();
            Location hit = victim.getLocation().clone().add(0.0, 1.0, 0.0);
            world.spawnParticle(Particle.SOUL, hit, 20, 0.25, 0.45, 0.25, 0.02);
            world.spawnParticle(Particle.SCULK_SOUL, attacker.getLocation().clone().add(0.0, 1.0, 0.0), 16, 0.25, 0.45, 0.25, 0.02);
            world.playSound(attacker.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 0.85f);
        }
    }

    private void copyBeneficialPotionEffects(LivingEntity source, LivingEntity target) {
        for (PotionEffect effect : source.getActivePotionEffects()) {
            PotionEffectType type = effect.getType();
            if (type == null || type.isInstant() || type.getCategory() != PotionEffectTypeCategory.BENEFICIAL) {
                continue;
            }
            applyPotionIfStrongerOrLonger(target, type, effect.getDuration(), effect.getAmplifier());
        }
    }

    private double shadowBladeDamage(Player attacker) {
        if (plugin.getSuperpowerManager() != null && plugin.getSuperpowerManager().hasPower(attacker, me.rique.smpcore.power.SuperpowerType.MONARCH)) {
            return SHADOW_BLADE_MONARCH_DAMAGE;
        }
        return SHADOW_BLADE_DAMAGE;
    }

    private void applyStrengthSwordStageHit(Player attacker, LivingEntity victim) {
        int stage = strengthSwordStage(attacker.getInventory().getItemInMainHand());
        if (stage < 1 || !(victim instanceof Player targetPlayer)) {
            return;
        }

        targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 5 * 20, 1, false, true, true));
        targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 5 * 20, 0, false, true, true));
    }

    private void addHeadhunterRage(Player player, boolean attacking) {
        if (player == null || !player.isOnline() || typeOf(player.getInventory().getChestplate()) != LegendaryType.HEADHUNTERS_CHESTPIECE) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (onCooldown(headhunterCooldownMap(), playerId)) {
            return;
        }

        int next = Math.min(HEADHUNTER_RAGE_MAX, headhunterRage.getOrDefault(playerId, 0) + 1);
        if (next >= HEADHUNTER_RAGE_MAX) {
            headhunterRage.remove(playerId);
            setCooldown(headhunterCooldownMap(), playerId, HEADHUNTER_COOLDOWN);
            applyPotionIfStrongerOrLonger(player, PotionEffectType.STRENGTH, HEADHUNTER_BUFF_SECONDS * 20, 2);
            applyPotionIfStrongerOrLonger(player, PotionEffectType.SPEED, HEADHUNTER_BUFF_SECONDS * 20, 1);
            player.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, player.getLocation().clone().add(0.0, 1.0, 0.0), 20, 0.35, 0.45, 0.35, 0.0);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.7f, 1.15f);
            player.sendMessage(MessageUtil.success(
                "Headhunter's Rage erupted: <white>Strength III</white> and <white>Speed II</white> for <white>"
                    + HEADHUNTER_BUFF_SECONDS + "s</white>."
            ));
            return;
        }

        headhunterRage.put(playerId, next);
        player.sendActionBar(MM.deserialize(
            "<red><bold>Headhunter Rage</bold></red><gray> "
                + next + "/" + HEADHUNTER_RAGE_MAX
                + (attacking ? " built from battle." : " built from pain.")
                + "</gray>"
        ));
    }

    private Map<UUID, Long> headhunterCooldownMap() {
        return headhunterBuffCd;
    }

    private void applyLifeStealerEffects(Player player, LifeStealerState state) {
        ensureLifeStealerEffects(player, state);
    }

    private void ensureLifeStealerEffects(Player player, LifeStealerState state) {
        if (state.stacks() <= 0) {
            clearLifeStealerEffects(player);
            return;
        }

        int amplifier = Math.max(0, state.stacks() - 1);
        ensurePotionEffect(player, PotionEffectType.STRENGTH, 60, amplifier);
        ensurePotionEffect(player, PotionEffectType.SPEED, 60, amplifier);
        lifeStealerGrantedAmplifier.put(player.getUniqueId(), amplifier);

        double targetAbsorption = lifeStealerAbsorptionAmount(state.stacks());
        double currentAbsorption = player.getAbsorptionAmount();
        if (currentAbsorption < targetAbsorption) {
            player.setAbsorptionAmount(targetAbsorption);
        }
        lifeStealerGrantedAbsorption.put(player.getUniqueId(), targetAbsorption);
    }

    private void clearLifeStealerEffects(Player player) {
        UUID playerId = player.getUniqueId();
        Integer amplifier = lifeStealerGrantedAmplifier.remove(playerId);
        if (amplifier != null) {
            removeTemporaryLegendaryPotion(player, PotionEffectType.STRENGTH, amplifier, 60);
            removeTemporaryLegendaryPotion(player, PotionEffectType.SPEED, amplifier, 60);
        }
        Double granted = lifeStealerGrantedAbsorption.remove(playerId);
        if (granted != null && player.getAbsorptionAmount() <= granted + 0.01) {
            player.setAbsorptionAmount(0.0);
        }
    }

    private double lifeStealerAbsorptionAmount(int stacks) {
        return switch (Math.max(0, Math.min(LIFE_STEALER_MAX_STACKS, stacks))) {
            case 1 -> 2.0;
            case 2 -> 6.0;
            case 3 -> 10.0;
            default -> 0.0;
        };
    }

    private void ensurePotionEffect(Player player, PotionEffectType type, int durationTicks, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);
        if (current != null
            && current.getAmplifier() >= amplifier
            && current.getDuration() >= Math.max(1, durationTicks - 20)) {
            return;
        }
        player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, false, false, true));
    }

    private LifeStealerState strongerLifeStealerState(LifeStealerState current, LifeStealerState candidate) {
        if (candidate.stacks() > current.stacks()) {
            return candidate;
        }
        if (candidate.stacks() < current.stacks()) {
            return current;
        }
        return candidate.seenVictims().size() > current.seenVictims().size() ? candidate : current;
    }

    private LifeStealerState lifeStealerState(ItemStack sword) {
        ItemMeta meta = sword.getItemMeta();
        if (meta == null) {
            return LifeStealerState.empty();
        }
        return refreshLifeStealerState(meta);
    }

    private LifeStealerState refreshLifeStealerState(ItemMeta meta) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int stacks = Math.max(0, Math.min(LIFE_STEALER_MAX_STACKS, pdc.getOrDefault(keyLifeStealerStacks, PersistentDataType.INTEGER, 0)));
        Set<UUID> seenVictims = parseLifeStealerVictims(
            pdc.getOrDefault(keyLifeStealerSeenPlayers, PersistentDataType.STRING, "")
        );
        return stacks <= 0 ? LifeStealerState.empty() : new LifeStealerState(stacks, Long.MAX_VALUE, seenVictims);
    }

    private void applyLifeStealerState(ItemStack sword, LifeStealerState state) {
        ItemMeta meta = sword.getItemMeta();
        if (meta == null) {
            return;
        }
        applyLifeStealerState(meta, state);
        sword.setItemMeta(meta);
    }

    private void applyLifeStealerState(ItemMeta meta, LifeStealerState state) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (state.stacks() <= 0) {
            pdc.remove(keyLifeStealerStacks);
            pdc.remove(keyLifeStealerExpiresAt);
            pdc.remove(keyLifeStealerSeenPlayers);
        } else {
            pdc.set(keyLifeStealerStacks, PersistentDataType.INTEGER, state.stacks());
            pdc.remove(keyLifeStealerExpiresAt);
            pdc.set(keyLifeStealerSeenPlayers, PersistentDataType.STRING, joinLifeStealerVictims(state.seenVictims()));
        }
        setLifeStealerSharpness(meta, lifeStealerSharpness(meta));
        meta.setItemModel(null);
        meta.lore(buildLifeStealerLore(meta, state));
    }

    private int lifeStealerSharpness(ItemStack sword) {
        ItemMeta meta = sword == null ? null : sword.getItemMeta();
        return meta == null ? 3 : lifeStealerSharpness(meta);
    }

    private int lifeStealerSharpness(ItemMeta meta) {
        return Math.max(3, Math.min(LIFE_STEALER_MAX_SHARPNESS, meta.getPersistentDataContainer().getOrDefault(
            keyLifeStealerSharpness,
            PersistentDataType.INTEGER,
            3
        )));
    }

    private void setLifeStealerSharpness(ItemMeta meta, int sharpness) {
        int clamped = Math.max(3, Math.min(LIFE_STEALER_MAX_SHARPNESS, sharpness));
        meta.getPersistentDataContainer().set(keyLifeStealerSharpness, PersistentDataType.INTEGER, clamped);
        setEnchantLevel(meta, enchantSharpness, clamped);
    }

    private int midasSharpness(ItemStack sword) {
        ItemMeta meta = sword == null ? null : sword.getItemMeta();
        return meta == null ? MIDAS_SWORD_BASE_SHARPNESS : midasSharpness(meta);
    }

    private int midasSharpness(ItemMeta meta) {
        return Math.max(MIDAS_SWORD_BASE_SHARPNESS, Math.min(MIDAS_SWORD_MAX_SHARPNESS, meta.getPersistentDataContainer().getOrDefault(
            keyMidasSharpness,
            PersistentDataType.INTEGER,
            MIDAS_SWORD_BASE_SHARPNESS
        )));
    }

    private Set<UUID> parseLifeStealerVictims(String raw) {
        if (raw == null || raw.isBlank()) {
            return new HashSet<>();
        }
        Set<UUID> seen = new HashSet<>();
        for (String token : raw.split(",")) {
            if (token.isBlank()) continue;
            try {
                seen.add(UUID.fromString(token.trim()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return seen;
    }

    private String joinLifeStealerVictims(Set<UUID> victims) {
        if (victims.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (UUID victim : victims) {
            if (!out.isEmpty()) {
                out.append(',');
            }
            out.append(victim);
        }
        return out.toString();
    }

    private void tickLegendaryStates() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            syncMidasBlessing(player);
            syncGodChestplateState(player);
            syncShadowBladeAttackSpeed(player);
            syncHeadhunterChestpieceState(player);
            syncShadowBladeState(player, now);
            syncStrengthDomainState(player, now);
        }
    }

    private void syncMidasBlessing(Player player) {
        boolean active = typeOf(player.getInventory().getItemInMainHand()) == LegendaryType.MIDAS_SWORD;
        syncPlayerAttributeModifier(
            player,
            Attribute.MAX_HEALTH,
            keyMidasHealthModifier,
            active ? MIDAS_SWORD_HEALTH_BONUS : 0.0,
            AttributeModifier.Operation.ADD_NUMBER
        );
    }

    private void syncGodChestplateState(Player player) {
        boolean active = typeOf(player.getInventory().getChestplate()) == LegendaryType.GOD_CHESTPLATE;
        syncPlayerAttributeModifier(
            player,
            Attribute.MAX_HEALTH,
            keyGodChestplateHealthModifier,
            active ? GOD_CHESTPLATE_HEALTH_BONUS : 0.0,
            AttributeModifier.Operation.ADD_NUMBER
        );
        syncPlayerAttributeModifier(
            player,
            Attribute.KNOCKBACK_RESISTANCE,
            keyGodChestplateKnockbackModifier,
            active ? 1.0 : 0.0,
            AttributeModifier.Operation.ADD_NUMBER
        );
    }

    private void syncShadowBladeAttackSpeed(Player player) {
        boolean holdingShadowBlade = typeOf(player.getInventory().getItemInMainHand()) == LegendaryType.SHADOW_BLADE;
        boolean shadowPower = holdingShadowBlade
            && plugin.getSuperpowerManager() != null
            && plugin.getSuperpowerManager().hasPower(player, me.rique.smpcore.power.SuperpowerType.SHADOW);
        syncPlayerAttributeModifier(
            player,
            Attribute.ATTACK_SPEED,
            keyShadowBladeAttackSpeedModifier,
            holdingShadowBlade
                ? (shadowPower ? SHADOW_BLADE_SHADOW_ATTACK_SPEED_BONUS : SHADOW_BLADE_BASE_ATTACK_SPEED_BONUS)
                : 0.0,
            AttributeModifier.Operation.ADD_NUMBER
        );
    }

    private void syncHeadhunterChestpieceState(Player player) {
        if (typeOf(player.getInventory().getChestplate()) != LegendaryType.HEADHUNTERS_CHESTPIECE) {
            headhunterRage.remove(player.getUniqueId());
            return;
        }
        applyPotionIfStrongerOrLonger(player, PotionEffectType.STRENGTH, 40, 0);
    }

    private void syncShadowBladeState(Player player, long now) {
        Long expiresAt = shadowBladeActiveUntil.get(player.getUniqueId());
        if (expiresAt == null) {
            return;
        }
        if (expiresAt <= now) {
            cancelShadowBlade(player, false);
            return;
        }

        int durationTicks = Math.max(20, (int) ((expiresAt - now + 49L) / 50L));
        applyPotionIfStrongerOrLonger(player, PotionEffectType.INVISIBILITY, durationTicks, 0);
        applyPotionIfStrongerOrLonger(player, PotionEffectType.SPEED, durationTicks, 2);
    }

    private void syncStrengthDomainState(Player player, long now) {
        StrengthDomainState state = activeStrengthDomains.get(player.getUniqueId());
        if (state == null) {
            syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keyStrengthDomainHealthModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER);
            syncPlayerAttributeModifier(player, Attribute.ATTACK_SPEED, keyStrengthDomainAttackSpeedModifier, 0.0, AttributeModifier.Operation.ADD_SCALAR);
            return;
        }
        if (state.expiresAt() <= now) {
            activeStrengthDomains.remove(player.getUniqueId(), state);
            syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keyStrengthDomainHealthModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER);
            syncPlayerAttributeModifier(player, Attribute.ATTACK_SPEED, keyStrengthDomainAttackSpeedModifier, 0.0, AttributeModifier.Operation.ADD_SCALAR);
            return;
        }

        renderStrengthDomain(state);
        boolean inside = state.center().getWorld() != null
            && state.center().getWorld().equals(player.getWorld())
            && player.getLocation().distanceSquared(state.center()) <= (STRENGTH_SWORD_DOMAIN_RADIUS * STRENGTH_SWORD_DOMAIN_RADIUS);
        syncPlayerAttributeModifier(
            player,
            Attribute.MAX_HEALTH,
            keyStrengthDomainHealthModifier,
            inside ? STRENGTH_SWORD_DOMAIN_HEALTH_BONUS : 0.0,
            AttributeModifier.Operation.ADD_NUMBER
        );
        syncPlayerAttributeModifier(
            player,
            Attribute.ATTACK_SPEED,
            keyStrengthDomainAttackSpeedModifier,
            inside ? STRENGTH_SWORD_DOMAIN_ATTACK_SPEED_SCALAR : 0.0,
            AttributeModifier.Operation.ADD_SCALAR
        );
        if (inside) {
            applyPotionIfStrongerOrLonger(player, PotionEffectType.STRENGTH, 30, 2);
        }
    }

    private void renderStrengthDomain(StrengthDomainState state) {
        World world = state.center().getWorld();
        if (world == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 70, 100), 1.2f);
        Location center = state.center().clone().add(0.0, 0.1, 0.0);
        for (int i = 0; i < 24; i++) {
            double angle = (Math.PI * 2.0 * i) / 24.0;
            double x = Math.cos(angle) * STRENGTH_SWORD_DOMAIN_RADIUS;
            double z = Math.sin(angle) * STRENGTH_SWORD_DOMAIN_RADIUS;
            Location ring = center.clone().add(x, 0.0, z);
            world.spawnParticle(Particle.DUST, ring, 1, 0.0, 0.0, 0.0, 0.0, dust);
            world.spawnParticle(Particle.ENCHANT, ring.clone().add(0.0, 1.0, 0.0), 1, 0.02, 0.20, 0.02, 0.0);
        }
    }

    private void syncPlayerAttributeModifier(
        Player player,
        Attribute attributeType,
        NamespacedKey key,
        double amount,
        AttributeModifier.Operation operation
    ) {
        var attribute = player.getAttribute(attributeType);
        if (attribute == null) {
            return;
        }

        AttributeModifier existing = currentPlayerAttributeModifier(player, attributeType, key);
        if (Math.abs(amount) <= 1.0E-6 || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            if (existing != null) {
                attribute.removeModifier(existing);
            }
            return;
        }

        if (existing != null
            && Math.abs(existing.getAmount() - amount) <= 1.0E-6
            && existing.getOperation() == operation) {
            return;
        }
        if (existing != null) {
            attribute.removeModifier(existing);
        }
        attribute.addModifier(new AttributeModifier(key, amount, operation, EquipmentSlotGroup.ANY));

        if (attributeType == Attribute.MAX_HEALTH && player.getHealth() > attribute.getValue()) {
            player.setHealth(attribute.getValue());
        }
    }

    private AttributeModifier currentPlayerAttributeModifier(Player player, Attribute attributeType, NamespacedKey key) {
        var attribute = player.getAttribute(attributeType);
        if (attribute == null) {
            return null;
        }
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (key.equals(modifier.getKey())) {
                return modifier;
            }
        }
        return null;
    }

    private void clearPlayerLegendaryAttributeModifiers(Player player) {
        syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keyMidasHealthModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER);
        syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keyGodChestplateHealthModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER);
        syncPlayerAttributeModifier(player, Attribute.KNOCKBACK_RESISTANCE, keyGodChestplateKnockbackModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER);
        syncPlayerAttributeModifier(player, Attribute.ATTACK_SPEED, keyShadowBladeAttackSpeedModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER);
        syncPlayerAttributeModifier(player, Attribute.MAX_HEALTH, keyStrengthDomainHealthModifier, 0.0, AttributeModifier.Operation.ADD_NUMBER);
        syncPlayerAttributeModifier(player, Attribute.ATTACK_SPEED, keyStrengthDomainAttackSpeedModifier, 0.0, AttributeModifier.Operation.ADD_SCALAR);
    }

    private void tryGodChestplateRescue(Player player, EntityDamageEvent event) {
        if (player.getHealth() - event.getFinalDamage() > 0.0) {
            return;
        }
        if (typeOf(player.getInventory().getChestplate()) != LegendaryType.GOD_CHESTPLATE) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (onCooldown(godChestplateCd, playerId)) {
            return;
        }

        event.setCancelled(true);
        setCooldown(godChestplateCd, playerId, GOD_CHESTPLATE_COOLDOWN);
        player.setHealth(1.0);
        player.setFireTicks(0);
        applyPotionIfStrongerOrLonger(player, PotionEffectType.REGENERATION, 10 * 20, 1);
        applyPotionIfStrongerOrLonger(player, PotionEffectType.ABSORPTION, 10 * 20, 1);
        applyPotionIfStrongerOrLonger(player, PotionEffectType.FIRE_RESISTANCE, 10 * 20, 0);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().clone().add(0.0, 1.0, 0.0), 1, 0.35, 0.45, 0.35, 0.0);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
        player.sendMessage(MessageUtil.success(
            "Aegis of the Undying denied death. Cooldown: <white>" + GOD_CHESTPLATE_COOLDOWN + "s</white>."
        ));
    }

    private boolean hasLegendaryInHands(Player player, LegendaryType type) {
        return typeOf(player.getInventory().getItemInMainHand()) == type
            || typeOf(player.getInventory().getItemInOffHand()) == type;
    }

    private int strengthSwordVictimCount(ItemStack sword) {
        ItemMeta meta = sword == null ? null : sword.getItemMeta();
        if (meta == null) {
            return 0;
        }
        return strengthSwordVictimCount(meta);
    }

    private int strengthSwordVictimCount(ItemMeta meta) {
        if (meta == null) {
            return 0;
        }
        return parseLifeStealerVictims(
            meta.getPersistentDataContainer().getOrDefault(keyStrengthSwordVictims, PersistentDataType.STRING, "")
        ).size();
    }

    private int strengthSwordStage(ItemStack sword) {
        return strengthSwordStage(strengthSwordVictimCount(sword));
    }

    private int strengthSwordStage(int uniqueKills) {
        if (uniqueKills >= STRENGTH_SWORD_STAGE_THREE_KILLS) {
            return 3;
        }
        if (uniqueKills >= STRENGTH_SWORD_STAGE_TWO_KILLS) {
            return 2;
        }
        if (uniqueKills >= 1) {
            return 1;
        }
        return 0;
    }

    private int shadowBladeDurationSeconds(Player player) {
        return plugin.getSuperpowerManager() != null
            && plugin.getSuperpowerManager().hasPower(player, me.rique.smpcore.power.SuperpowerType.SHADOW)
            ? SHADOW_BLADE_SHADOW_DURATION_SECONDS
            : SHADOW_BLADE_DURATION_SECONDS;
    }

    private void removeTemporaryLegendaryPotion(Player player, PotionEffectType type, int amplifier, int maxDurationTicks) {
        PotionEffect current = player.getPotionEffect(type);
        if (current == null) {
            return;
        }
        if (current.getAmplifier() == amplifier && current.getDuration() <= maxDurationTicks + 40) {
            player.removePotionEffect(type);
        }
    }

    private boolean onCooldown(Map<UUID, Long> map, UUID uuid) {
        long now = System.currentTimeMillis();
        long expiresAt = map.getOrDefault(uuid, 0L);
        if (expiresAt <= now) {
            if (expiresAt > 0L) {
                map.remove(uuid, expiresAt);
            }
            return false;
        }
        return true;
    }

    private long secondsLeft(Map<UUID, Long> map, UUID uuid) {
        long now = System.currentTimeMillis();
        long expiresAt = map.getOrDefault(uuid, 0L);
        if (expiresAt <= now) {
            if (expiresAt > 0L) {
                map.remove(uuid, expiresAt);
            }
            return 0L;
        }
        long diff = expiresAt - now;
        return diff <= 0 ? 0 : (diff + 999L) / 1000L;
    }

    private void setCooldown(Map<UUID, Long> map, UUID uuid, int seconds) {
        if (seconds <= 0) {
            map.remove(uuid);
            return;
        }
        map.put(uuid, System.currentTimeMillis() + (seconds * 1000L));
    }

    private void cleanupExpiredLegendaryCooldowns() {
        long now = System.currentTimeMillis();
        cleanupExpiredCooldownMap(enderbowCd, now);
        cleanupExpiredCooldownMap(enderSwordSummonCd, now);
        cleanupExpiredCooldownMap(chronoCd, now);
        cleanupExpiredCooldownMap(harpoonCd, now);
        cleanupExpiredCooldownMap(hypnosisCd, now);
        cleanupExpiredCooldownMap(wardenBladeProtectionCd, now);
        cleanupExpiredCooldownMap(wardenBladeSoundWaveCd, now);
        cleanupExpiredCooldownMap(frostScytheCd, now);
        cleanupExpiredCooldownMap(blinkDaggerCd, now);
        cleanupExpiredCooldownMap(blinkDaggerBackstabCd, now);
        cleanupExpiredCooldownMap(executionerStrengthCd, now);
        cleanupExpiredCooldownMap(executionerShockwaveCd, now);
        cleanupExpiredCooldownMap(rhittaBlessingCd, now);
        cleanupExpiredCooldownMap(rhittaCruelSunCd, now);
        cleanupExpiredCooldownMap(reaperScytheCd, now);
        cleanupExpiredCooldownMap(shadowBladeCd, now);
        cleanupExpiredCooldownMap(headhunterBuffCd, now);
        cleanupExpiredCooldownMap(godChestplateCd, now);
        cleanupExpiredCooldownMap(strengthSwordBeamCd, now);
        cleanupExpiredCooldownMap(strengthSwordDomainCd, now);
        cleanupExpiredCooldownMap(dashMaceCd, now);
        cleanupExpiredCooldownMap(thorsHammerCd, now);
        cleanupExpiredCooldownMap(blinkDaggerStunnedUntil, now);
        cleanupExpiredCooldownMap(shadowBladeActiveUntil, now);
        rhittaBurns.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().expiresAt() <= now);
        activeStrengthDomains.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().expiresAt() <= now);
    }

    private void cleanupExpiredCooldownMap(Map<UUID, Long> map, long now) {
        map.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= now);
    }

    public boolean canCraftLegendary(Player player, String id) {
        LegendaryType type = LegendaryType.fromId(normalizeLegendaryId(id));
        if (type == null) return false;
        Map<Material, Integer> ingredients = ingredientsFor(type);
        if (ingredients.isEmpty()) return false;
        for (Map.Entry<Material, Integer> entry : ingredients.entrySet()) {
            if (countRecipeMaterial(player, type, entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public List<String> recipeProgressLines(Player player, String id) {
        LegendaryType type = LegendaryType.fromId(normalizeLegendaryId(id));
        if (type == null) return List.of("<red>Unknown legendary.</red>");
        Map<Material, Integer> ingredients = ingredientsFor(type);
        if (ingredients.isEmpty()) return List.of("<red>No recipe available for this legendary.</red>");

        List<String> lines = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : ingredients.entrySet()) {
            int have = countRecipeMaterial(player, type, entry.getKey());
            boolean enough = have >= entry.getValue();
            lines.add(
                "<gray>" + prettyRecipeMaterial(type, entry.getKey()) + ": "
                    + (enough ? "<green>" : "<red>") + have
                    + "</" + (enough ? "green" : "red") + "><gray>/<white>"
                    + entry.getValue() + "</white></gray>"
            );
        }
        return lines;
    }

    public List<String> altarRequirementLines(String id) {
        LegendaryType type = LegendaryType.fromId(normalizeLegendaryId(id));
        if (type == null) {
            return List.of("Unknown legendary");
        }
        Map<Material, Integer> ingredients = ingredientsFor(type);
        if (ingredients.isEmpty()) {
            return List.of("No recipe available");
        }

        List<String> lines = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : ingredients.entrySet()) {
            lines.add(entry.getValue() + "x " + prettyRecipeMaterial(type, entry.getKey()));
        }
        return lines;
    }

    public boolean craftLegendaryAtAltar(Player player, String id) {
        LegendaryType type = LegendaryType.fromId(normalizeLegendaryId(id));
        if (type == null) return false;
        if (ingredientsFor(type).isEmpty()) return false;
        return giveCraftedLegendary(player, type, "altar");
    }

    private boolean tradeLegendary(Player player, LegendaryType type) {
        if (ingredientsFor(type).isEmpty()) return false;
        return giveCraftedLegendary(player, type, "trade");
    }

    private boolean giveCraftedLegendary(Player player, LegendaryType type, String source) {
        Map<Material, Integer> ingredients = ingredientsFor(type);
        ItemStack reward = createItem(type);
        if (!canFitLegendaryRewardAfterRemovingMaterials(player, type, ingredients, reward)) {
            player.sendMessage(MessageUtil.warn("Clear enough inventory space before crafting " + type.display + "<yellow>.</yellow>"));
            return false;
        }

        if (!removeRecipeMaterials(player, type, ingredients)) {
            player.sendMessage(MessageUtil.error(
                "You do not have all the materials for " + type.display + "<red>.</red>"
            ));
            return false;
        }

        if (plugin.getItemAuditManager() != null) {
            plugin.getItemAuditManager().recordKnownAcquisition(
                player,
                reward,
                source,
                "Created " + type.display + " from " + source + "."
            );
        }
        player.getInventory().addItem(reward);
        registerLegendaryInstance(player, reward, "altar".equals(source));
        announceLegendaryCraft(player, type);
        String sourceText = "trade".equals(source) ? "Traded materials for " : "Crafted ";
        player.sendMessage(MessageUtil.success(sourceText + type.display + "<green>.</green>"));
        scheduleLegendaryDuplicateAudit();
        return true;
    }

    private boolean canTrade(Player player, Map<Material, Integer> ingredients) {
        for (Map.Entry<Material, Integer> entry : ingredients.entrySet()) {
            if (countRecipeMaterial(player, null, entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private int countRecipeMaterial(Player player, LegendaryType type, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (!isValidRecipeMaterial(item, type, material)) continue;
            count += item.getAmount();
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (isValidRecipeMaterial(offhand, type, material)) {
            count += offhand.getAmount();
        }
        return count;
    }

    private boolean removeRecipeMaterials(Player player, LegendaryType type, Map<Material, Integer> required) {
        ItemStack[] storage = player.getInventory().getStorageContents().clone();
        ItemStack offhand = player.getInventory().getItemInOffHand();
        ItemStack nextOffhand = offhand == null ? null : offhand.clone();

        for (Map.Entry<Material, Integer> entry : required.entrySet()) {
            int remaining = entry.getValue();

            for (int i = 0; i < storage.length && remaining > 0; i++) {
                ItemStack item = storage[i];
                if (!isValidRecipeMaterial(item, type, entry.getKey())) continue;

                int take = Math.min(remaining, item.getAmount());
                int left = item.getAmount() - take;
                storage[i] = left <= 0 ? null : item.asQuantity(left);
                remaining -= take;
            }

            if (remaining > 0 && isValidRecipeMaterial(nextOffhand, type, entry.getKey())) {
                int take = Math.min(remaining, nextOffhand.getAmount());
                int left = nextOffhand.getAmount() - take;
                nextOffhand = left <= 0 ? null : nextOffhand.asQuantity(left);
                remaining -= take;
            }

            if (remaining > 0) {
                return false;
            }
        }

        player.getInventory().setStorageContents(storage);
        player.getInventory().setItemInOffHand(nextOffhand);
        return true;
    }

    private boolean canFitLegendaryRewardAfterRemovingMaterials(
        Player player,
        LegendaryType type,
        Map<Material, Integer> required,
        ItemStack reward
    ) {
        if (reward == null || reward.getType().isAir()) {
            return false;
        }
        ItemStack[] storage = cloneStorageContents(player.getInventory().getStorageContents());
        RecipeOffhandHolder offhand = new RecipeOffhandHolder(cloneOrNull(player.getInventory().getItemInOffHand()));
        if (!removeRecipeMaterialsFromCopies(storage, offhand, type, required)) {
            return false;
        }
        return canFitItem(storage, reward);
    }

    private boolean removeRecipeMaterialsFromCopies(
        ItemStack[] storage,
        RecipeOffhandHolder offhand,
        LegendaryType type,
        Map<Material, Integer> required
    ) {
        for (Map.Entry<Material, Integer> entry : required.entrySet()) {
            int remaining = entry.getValue();

            for (int i = 0; i < storage.length && remaining > 0; i++) {
                ItemStack item = storage[i];
                if (!isValidRecipeMaterial(item, type, entry.getKey())) continue;

                int take = Math.min(remaining, item.getAmount());
                int left = item.getAmount() - take;
                storage[i] = left <= 0 ? null : item.asQuantity(left);
                remaining -= take;
            }

            if (remaining > 0 && isValidRecipeMaterial(offhand.item(), type, entry.getKey())) {
                int take = Math.min(remaining, offhand.item().getAmount());
                int left = offhand.item().getAmount() - take;
                offhand.item(left <= 0 ? null : offhand.item().asQuantity(left));
                remaining -= take;
            }

            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private ItemStack[] cloneStorageContents(ItemStack[] contents) {
        ItemStack[] clone = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            clone[i] = cloneOrNull(contents[i]);
        }
        return clone;
    }

    private ItemStack cloneOrNull(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }

    private boolean canFitItem(ItemStack[] storage, ItemStack item) {
        int remaining = item.getAmount();
        int maxStack = Math.max(1, item.getType().getMaxStackSize());
        for (ItemStack existing : storage) {
            if (remaining <= 0) {
                return true;
            }
            if (existing == null || existing.getType().isAir() || !existing.isSimilar(item)) {
                continue;
            }
            remaining -= Math.max(0, maxStack - existing.getAmount());
        }
        for (ItemStack existing : storage) {
            if (remaining <= 0) {
                return true;
            }
            if (existing == null || existing.getType().isAir()) {
                remaining -= maxStack;
            }
        }
        return remaining <= 0;
    }

    private boolean isValidRecipeMaterial(ItemStack item, LegendaryType type, Material material) {
        if (type == LegendaryType.ENDER_SWORD && material == Material.BONE) {
            return isEnderBone(item);
        }
        if (type == LegendaryType.WARDEN_BLADE && material == Material.HEART_OF_THE_SEA) {
            return plugin.getSuperpowerManager() != null && plugin.getSuperpowerManager().isWardenHeart(item);
        }
        if (type == LegendaryType.STRENGTH_SWORD && material == Material.HEART_OF_THE_SEA) {
            return plugin.getSuperpowerManager() != null && plugin.getSuperpowerManager().isWardenHeart(item);
        }
        return isPlainLegendaryRecipeMaterial(item, material);
    }

    private boolean isPlainLegendaryRecipeMaterial(ItemStack item, Material material) {
        if (item == null || item.getType() != material || item.getType().isAir() || item.getAmount() <= 0) {
            return false;
        }
        if (typeOf(item) != null || isPluginManagedRecipeItem(item)) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return true;
        }
        if (hasPluginPersistentData(meta)) {
            return false;
        }
        if (meta.hasDisplayName() || meta.hasLore() || !meta.getEnchants().isEmpty()
            || meta.hasCustomModelDataComponent() || meta.hasItemModel()) {
            return false;
        }
        return !(meta instanceof Damageable damageable) || damageable.getDamage() <= 0;
    }

    private boolean isPluginManagedRecipeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (plugin.getBackpackListener() != null && plugin.getBackpackListener().isBackpack(item)) {
            return true;
        }
        if (plugin.getCustomToolListener() != null && plugin.getCustomToolListener().isCustomTool(item)) {
            return true;
        }
        if (plugin.getSeasonRelicManager() != null && plugin.getSeasonRelicManager().isSeasonRelic(item)) {
            return true;
        }
        if (plugin.getSustenanceTalismanListener() != null && plugin.getSustenanceTalismanListener().isTalisman(item)) {
            return true;
        }
        if (plugin.getXpLecternListener() != null && plugin.getXpLecternListener().isLecternItem(item)) {
            return true;
        }
        if (plugin.getSalvagingDepotListener() != null && plugin.getSalvagingDepotListener().isDepotItem(item)) {
            return true;
        }
        if (plugin.getAgriculturalPylonListener() != null && plugin.getAgriculturalPylonListener().isPylonItem(item)) {
            return true;
        }
        if (plugin.getBossPotionListener() != null && plugin.getBossPotionListener().isBossPotion(item)) {
            return true;
        }
        if (plugin.getAwakeningTableListener() != null && plugin.getAwakeningTableListener().isAwakeningTableCustomItem(item)) {
            return true;
        }
        if (plugin.getMythicForgeListener() != null
            && (plugin.getMythicForgeListener().isMythicForgeItemStack(item)
                || plugin.getMythicForgeListener().isAscendantCoreItem(item))) {
            return true;
        }
        if (plugin.getBossManager() != null && plugin.getBossManager().isDominionCore(item)) {
            return true;
        }
        return plugin.getSuperpowerManager() != null
            && (plugin.getSuperpowerManager().isAncientScroll(item)
                || plugin.getSuperpowerManager().isWardenHeart(item)
                || plugin.getSuperpowerManager().isMotherNatureStick(item)
                || plugin.getSuperpowerManager().isTheWorldClock(item)
                || plugin.getSuperpowerManager().isDruidGrimoire(item));
    }

    private boolean hasPluginPersistentData(ItemMeta meta) {
        if (meta == null) {
            return false;
        }
        String namespace = plugin.getName().toLowerCase(java.util.Locale.ROOT);
        return meta.getPersistentDataContainer().getKeys().stream()
            .anyMatch(key -> namespace.equals(key.getNamespace()));
    }

    private String prettyRecipeMaterial(LegendaryType type, Material material) {
        if (type == LegendaryType.ENDER_SWORD && material == Material.BONE) {
            return "Ender Bone";
        }
        if (type == LegendaryType.WARDEN_BLADE && material == Material.HEART_OF_THE_SEA) {
            return "Warden Heart";
        }
        if (type == LegendaryType.STRENGTH_SWORD && material == Material.HEART_OF_THE_SEA) {
            return "Warden Heart";
        }
        return prettyMaterial(material);
    }

    private ItemStack displayRecipeIngredient(LegendaryType type, Material material, int amount) {
        ItemStack ingredientItem = recipeIngredientBaseItem(type, material);
        ingredientItem.setAmount(Math.min(64, Math.max(1, amount)));
        ItemMeta meta = ingredientItem.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>();
            lore.add(MM.deserialize("<gray>Required: <white>x" + amount + "</white>"));
            meta.lore(lore);
            ingredientItem.setItemMeta(meta);
        }
        return ingredientItem;
    }

    private ItemStack recipeIngredientBaseItem(LegendaryType type, Material material) {
        if (type == LegendaryType.ENDER_SWORD && material == Material.BONE) {
            return createEnderBone();
        }
        if (type == LegendaryType.WARDEN_BLADE && material == Material.HEART_OF_THE_SEA && plugin.getSuperpowerManager() != null) {
            return plugin.getSuperpowerManager().createWardenHeartItem();
        }
        if (type == LegendaryType.STRENGTH_SWORD && material == Material.HEART_OF_THE_SEA && plugin.getSuperpowerManager() != null) {
            return plugin.getSuperpowerManager().createWardenHeartItem();
        }
        return new ItemStack(material);
    }

    private String prettyMaterial(Material material) {
        StringBuilder out = new StringBuilder();
        String[] parts = material.name().toLowerCase().split("_");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private LegendaryType typeOf(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String id = meta.getPersistentDataContainer().get(keyLegendary, PersistentDataType.STRING);
        if (id == null) return null;
        return LegendaryType.fromId(id);
    }

    private ItemStack createItem(LegendaryType type) {
        ItemStack out = new ItemStack(type.material);
        ItemMeta meta = out.getItemMeta();
        if (meta == null) return out;

        applyLegendaryIdentity(meta, type, UUID.randomUUID().toString());
        applyLegendaryTypeState(meta, type);
        out.setItemMeta(meta);
        return out;
    }

    private ItemStack createEnderBone() {
        ItemStack item = new ItemStack(Material.BONE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        applyEnderBonePresentation(meta);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createOrbOfTheMystics() {
        ItemStack item = new ItemStack(Material.ENDER_EYE, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        applyOrbOfTheMysticsPresentation(meta);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isEnderBone(ItemStack item) {
        if (item == null || item.getType() != Material.BONE) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Byte tagged = meta.getPersistentDataContainer().get(keyEnderBone, PersistentDataType.BYTE);
        return tagged != null && tagged == (byte) 1;
    }

    private boolean isOrbOfTheMystics(ItemStack item) {
        if (item == null || item.getType() != Material.ENDER_EYE) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Byte tagged = meta.getPersistentDataContainer().get(keyOrbOfTheMystics, PersistentDataType.BYTE);
        return tagged != null && tagged == (byte) 1;
    }

    private void applyEnderBonePresentation(ItemMeta meta) {
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.EPIC, "Ender Bone"));
        meta.setItemModel(null);
        meta.lore(CustomLoreUtil.buildStyledLore(
            Material.BONE,
            CustomLoreUtil.Rarity.EPIC.label(),
            "TROPHY",
            List.of("<gray>Dropped by the Ender Dragon.</gray>"),
            List.of()
        ));
        meta.getPersistentDataContainer().set(keyEnderBone, PersistentDataType.BYTE, (byte) 1);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }

    private boolean refreshEnderBonePresentation(ItemStack item) {
        if (!isEnderBone(item)) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        String displayName = meta.hasDisplayName() ? PLAIN.serialize(meta.displayName()).trim() : "";
        if ("Ender Bone".equals(displayName) && hasLoreText(meta, "EPIC TROPHY")) return false;

        applyEnderBonePresentation(meta);
        item.setItemMeta(meta);
        return true;
    }

    private void applyOrbOfTheMysticsPresentation(ItemMeta meta) {
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.LEGENDARY, "Orb of the Mystics"));
        meta.setItemModel(null);
        meta.lore(CustomLoreUtil.buildStyledLore(
            Material.ENDER_EYE,
            CustomLoreUtil.Rarity.LEGENDARY.label(),
            "ORB",
            List.of(
                "<gray>Right-click to summon a legendary altar.</gray>",
                "<gray>Single-use. Consumed when the altar is called.</gray>",
                "<gray>Cooldown: <white>1 hour</white> per player.</gray>"
            ),
            List.of(CustomLoreUtil.section(
                "Item Ability",
                "Mystic Summon",
                "<gray>Calls forth a dormant legendary altar at a random location.</gray>",
                "<gray>Dropped by Endermen at <white>10%</white>.</gray>",
                "<gray>The caller cannot use another orb for <white>1 hour</white>.</gray>"
            ))
        ));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyOrbOfTheMystics, PersistentDataType.BYTE, (byte) 1);
        pdc.remove(keyLegacyOrbOfTheMysticsInstance);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }

    private boolean refreshOrbOfTheMysticsPresentation(ItemStack item) {
        if (!isOrbOfTheMystics(item)) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        String displayName = meta.hasDisplayName() ? PLAIN.serialize(meta.displayName()).trim() : "";
        boolean hasLegacyInstance = meta.getPersistentDataContainer().has(keyLegacyOrbOfTheMysticsInstance, PersistentDataType.STRING);
        if ("Orb of the Mystics".equals(displayName)
            && hasLoreText(meta, "LEGENDARY ORB")
            && hasLoreText(meta, "Cooldown: 1 hour")
            && !hasLegacyInstance) {
            return false;
        }

        applyOrbOfTheMysticsPresentation(meta);
        item.setItemMeta(meta);
        return true;
    }

    private boolean hasLoreText(ItemMeta meta, String text) {
        if (meta == null || text == null || text.isBlank() || !meta.hasLore() || meta.lore() == null) {
            return false;
        }
        for (Component line : meta.lore()) {
            if (PLAIN.serialize(line).contains(text)) {
                return true;
            }
        }
        return false;
    }

    private void useOrbOfTheMystics(Player player) {
        if (plugin.getLegendaryAltarManager() == null) {
            player.sendMessage(MessageUtil.error("Legendary altar system is not ready yet."));
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownUntil = orbOfTheMysticsCooldownUntil(player);
        if (cooldownUntil > now) {
            player.sendMessage(MessageUtil.warn(
                "Orb of the Mystics cooldown: <white>" + formatShortDuration(cooldownUntil - now) + "</white>."
            ));
            return;
        }

        LegendaryAltarManager.AdminActionResult result = plugin.getLegendaryAltarManager().summonFromMysticOrb(player);
        if (!result.success()) {
            player.sendMessage(MessageUtil.error(result.message()));
            return;
        }

        consumeMainHandItem(player);
        setOrbOfTheMysticsCooldownUntil(player, System.currentTimeMillis() + ORB_OF_THE_MYSTICS_COOLDOWN_MS);
        player.sendMessage(MessageUtil.success(result.message()));
    }

    private long orbOfTheMysticsCooldownUntil(Player player) {
        return player.getPersistentDataContainer().getOrDefault(
            keyOrbOfTheMysticsCooldownUntil,
            PersistentDataType.LONG,
            0L
        );
    }

    private void setOrbOfTheMysticsCooldownUntil(Player player, long value) {
        if (value <= System.currentTimeMillis()) {
            player.getPersistentDataContainer().remove(keyOrbOfTheMysticsCooldownUntil);
            return;
        }
        player.getPersistentDataContainer().set(keyOrbOfTheMysticsCooldownUntil, PersistentDataType.LONG, value);
    }

    private String formatShortDuration(long millis) {
        long totalSeconds = Math.max(1L, (millis + 999L) / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    private void consumeMainHandItem(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isOrbOfTheMystics(hand)) {
            return;
        }
        if (hand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        player.getInventory().setItemInMainHand(hand);
    }

    private boolean isEnderbowTpForm(ItemStack bow) {
        ItemMeta meta = bow.getItemMeta();
        if (meta == null) return false;
        Byte form = meta.getPersistentDataContainer().get(keyEnderbowForm, PersistentDataType.BYTE);
        return form != null && form == (byte) 1;
    }

    private void setEnderbowForm(ItemStack bow, boolean teleport) {
        ItemMeta meta = bow.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(keyEnderbowForm, PersistentDataType.BYTE, teleport ? (byte) 1 : (byte) 0);
        meta.lore(buildEnderbowLore(meta, teleport));
        bow.setItemMeta(meta);
    }

    private List<Component> buildLegendaryLore(
        ItemMeta meta,
        Material material,
        String itemKind,
        List<String> topLines,
        CustomLoreUtil.LoreSection... sections
    ) {
        return CustomLoreUtil.buildStyledLore(meta, material, rarityLabelForMeta(meta, "LEGENDARY"), itemKind, topLines, List.of(sections));
    }

    private List<Component> buildMythicLore(
        ItemMeta meta,
        Material material,
        String itemKind,
        List<String> topLines,
        CustomLoreUtil.LoreSection... sections
    ) {
        return CustomLoreUtil.buildStyledLore(meta, material, rarityLabelForMeta(meta, "MYTHIC"), itemKind, topLines, List.of(sections));
    }

    private String rarityLabelForMeta(ItemMeta meta, String fallback) {
        if (meta == null) {
            return fallback;
        }
        String id = meta.getPersistentDataContainer().get(keyLegendary, PersistentDataType.STRING);
        LegendaryType type = LegendaryType.fromId(id);
        return type == null ? fallback : type.rarity.label();
    }

    private List<Component> buildEnderbowLore(ItemMeta meta, boolean teleportForm) {
        return buildLegendaryLore(
            meta,
            Material.BOW,
            "BOW",
            List.of("<gray>Current Form: <yellow>" + (teleportForm ? "Teleport" : "Arrow") + "</yellow></gray>"),
            CustomLoreUtil.section(
                "Item Ability",
                "Form Shift",
                "<gray><white>Left-click</white> to switch between Arrow and Teleport form.</gray>",
                "<gray>Arrow Form spawns an Endermite on player hit at full draw.</gray>",
                "<gray>Teleport Form fires an Ender Pearl with a <white>30s</white> cooldown.</gray>"
            )
        );
    }

    private List<Component> buildEnderSwordLore(ItemMeta meta) {
        return buildLegendaryLore(
            meta,
            Material.NETHERITE_SWORD,
            "SWORD",
            List.of(
                "<gray>Unbreakable</gray>",
                "<gray>Hit Damage: <white>" + formatDamageNumber(ENDER_SWORD_MELEE_DAMAGE) + "</white></gray>",
                "<gray>Summon Cooldown: <white>" + plugin.getConfigManager().enderSwordSummonCooldownSeconds + "s</white></gray>"
            ),
            CustomLoreUtil.section(
                "Item Ability",
                "Ender Dragon",
                "<gray><white>Right-click</white> to enter <white>Dragon Mode</white>.</gray>",
                "<gray>Landing ends the mode and despawns the dragon.</gray>",
                "<gray>The dragon is visual only and does not damage terrain.</gray>"
            )
        );
    }

    private List<Component> buildEmeraldBladeLore(ItemMeta meta, int level) {
        return buildLegendaryLore(
            meta,
            Material.DIAMOND_SWORD,
            "SWORD",
            List.of(
                "<gray>Current Bonus: <green>" + level + "</green>/<white>" + EMERALD_BLADE_MAX_LEVEL + "</white> Sharpness</gray>"
            ),
            CustomLoreUtil.section(
                "Item Ability",
                "Emerald Growth",
                "<gray><white>Sneak + Right-click</white> with an <white>Emerald Block</white> to upgrade the blade.</gray>",
                "<gray>This blade is breakable, unrepairable, and cannot be enchanted.</gray>",
                "<gray>Kills with this blade drop emeralds.</gray>"
            )
        );
    }

    private List<Component> buildBlinkDaggerLore(ItemMeta meta) {
        return buildLegendaryLore(
            meta,
            Material.IRON_SWORD,
            "DAGGER",
            List.of(
                "<gray>Blink Range: <white>" + BLINK_DAGGER_RANGE_BLOCKS + " blocks</white></gray>",
                "<gray>Blink Cooldown: <white>" + BLINK_DAGGER_COOLDOWN + "s</white></gray>",
                "<gray>Backstab Cooldown: <white>" + BLINK_DAGGER_BACKSTAB_COOLDOWN + "s</white></gray>"
            ),
            CustomLoreUtil.section(
                "Item Ability",
                "Blink",
                "<gray><white>Right-click</white> to blink a medium distance forward.</gray>",
                "<gray>Stops at the last safe spot before a wall.</gray>"
            ),
            CustomLoreUtil.section(
                "Passive",
                "Backstab",
                "<gray>Hit a player or mob from behind to stun them for <white>" + BLINK_DAGGER_STUN_SECONDS + "s</white>.</gray>",
                "<gray>Teammates are ignored and the stun can only trigger once every <white>" + BLINK_DAGGER_BACKSTAB_COOLDOWN + "s</white>.</gray>"
            )
        );
    }

    private List<Component> buildWardenBladeLore(ItemMeta meta) {
        return CustomLoreUtil.buildStyledLore(
            meta,
            Material.NETHERITE_SWORD,
            rarityLabelForMeta(meta, CustomLoreUtil.Rarity.MYTHIC.label()),
            "BLADE",
            List.of(
                "<gray>Hit Damage: <white>" + formatDamageNumber(WARDEN_BLADE_MELEE_DAMAGE) + "</white></gray>",
                "<gray>Sculk Protection Cooldown: <white>" + WARDEN_BLADE_PROTECTION_COOLDOWN + "s</white></gray>",
                "<gray>Sound Wave Cooldown: <white>" + WARDEN_BLADE_SOUND_WAVE_COOLDOWN + "s</white></gray>"
            ),
            List.of(
                CustomLoreUtil.section(
                    "Item Ability",
                    "Sculk Protection",
                    "<gray><white>Shift + Right-click</white> to gain <white>Resistance II</white> for <white>3 minutes</white>.</gray>"
                ),
                CustomLoreUtil.section(
                    "Item Ability",
                    "Sound Wave",
                    "<gray><white>Right-click</white> to fire a sonic shriek where you are aiming.</gray>",
                    "<gray>The shriek deals <white>4.5 hearts</white> of <white>true damage</white> to enemy players and mobs.</gray>",
                    "<gray>Teammates are ignored by the shriek.</gray>"
                )
            )
        );
    }

    private List<Component> buildMagnetLore(ItemMeta meta, boolean active) {
        return buildLegendaryLore(
            meta,
            Material.RECOVERY_COMPASS,
            "UTILITY",
            List.of(
                "<gray>Current State: " + (active ? "<green>ON</green>" : "<red>OFF</red>") + "</gray>"
            ),
            CustomLoreUtil.section(
                "Passive",
                "Faraday Pull",
                "<gray><white>Shift + Right-click</white> while holding to toggle the magnet.</gray>",
                "<gray>When enabled, pulls dropped items within <white>" + MAGNET_RADIUS + "</white> blocks.</gray>",
                "<gray>Works from anywhere in your inventory.</gray>"
            )
        );
    }

    private List<Component> buildFrostScytheLore(ItemMeta meta) {
        return buildLegendaryLore(
            meta,
            Material.DIAMOND_HOE,
            "SCYTHE",
            List.of(
                "<gray>Hit Damage: <white>" + formatDamageNumber(FROST_SCYTHE_MELEE_DAMAGE) + "</white></gray>",
                "<gray>Nausea + Freeze Duration: <white>2s</white></gray>",
                "<gray>Frozen Reap Cooldown: <white>" + plugin.getConfigManager().frostScytheAbilityCooldownSeconds + "s</white></gray>"
            ),
            CustomLoreUtil.section(
                "Passive",
                "Wide Sweep",
                "<gray>Melee hits cut through nearby mobs and players in front of you.</gray>",
                "<gray>Teammates are ignored by the sweep.</gray>"
            ),
            CustomLoreUtil.section(
                "Item Ability",
                "Frozen Reap",
                "<gray><white>Right-click</white> to freeze nearby mobs and players for <white>2s</white>.</gray>",
                "<gray>Applies <white>Nausea</white> at the same time.</gray>",
                "<gray>Teammates are ignored by the frost burst.</gray>"
            )
        );
    }

    private List<Component> buildRhittaLore(ItemMeta meta) {
        return buildLegendaryLore(
            meta,
            Material.NETHERITE_AXE,
            "AXE",
            List.of(
                "<gray>Hit Damage: <white>" + formatDamageNumber(RHITTA_MELEE_DAMAGE) + "</white></gray>",
                "<gray>Sun's Blessing Cooldown: <white>" + RHITTA_SUNS_BLESSING_COOLDOWN + "s</white></gray>",
                "<gray>Cruel Sun Cooldown: <white>" + RHITTA_CRUEL_SUN_COOLDOWN + "s</white></gray>"
            ),
            CustomLoreUtil.section(
                "Item Ability",
                "Sun's Blessing",
                "<gray><white>Right-click</white> to gain <white>Fire Resistance</white> and <white>Speed II</white> for <white>300s</white>.</gray>"
            ),
            CustomLoreUtil.section(
                "Item Ability",
                "Cruel Sun",
                "<gray><white>Shift + Right-click</white> to ignite nearby enemies for <white>5s</white>.</gray>",
                "<gray>Targets burn for <white>0.5 hearts</white> each second while the fire lasts.</gray>",
                "<gray>Teammates are ignored by the flames.</gray>"
            )
        );
    }

    private List<Component> buildMidasSwordLore(ItemMeta meta, int sharpnessLevel) {
        return buildMythicLore(
            meta,
            Material.GOLDEN_SWORD,
            "SWORD",
            List.of(
                "<gray>Hit Damage: <white>" + formatDamageNumber(MIDAS_SWORD_MELEE_DAMAGE) + "</white></gray>",
                "<gray>Current Sharpness: <white>" + sharpnessLevel + "</white>/<white>" + MIDAS_SWORD_MAX_SHARPNESS + "</white></gray>",
                "<gray>Main-hand Blessing: <white>+" + formatDamageNumber(MIDAS_SWORD_HEALTH_BONUS / 2.0) + " hearts</white></gray>"
            ),
            CustomLoreUtil.section(
                "Passive",
                "Golden Tribute",
                "<gray>Kills spill <white>" + MIDAS_SWORD_GOLD_NUGGETS + " gold nuggets</white>.</gray>",
                "<gray>Piglins remain neutral while you hold the blade.</gray>"
            ),
            CustomLoreUtil.section(
                "Passive",
                "Midas Hunger",
                "<gray>Every player kill raises the sword by <white>+1 Sharpness</white>.</gray>",
                "<gray>Caps at <white>Sharpness " + MIDAS_SWORD_MAX_SHARPNESS + "</white>.</gray>"
            )
        );
    }

    private List<Component> buildReaperScytheLore(ItemMeta meta) {
        return buildMythicLore(
            meta,
            Material.NETHERITE_HOE,
            "SCYTHE",
            List.of(
                "<gray>Hit Damage: <white>" + formatDamageNumber(REAPER_SCYTHE_MELEE_DAMAGE) + "</white></gray>",
                "<gray>Attack Speed: <white>1</white> | Rend: <white>" + Math.round(REAPER_SCYTHE_DRAIN_FRACTION * 100.0) + "% current health</white></gray>",
                "<gray>Soul Rend Cooldown: <white>" + REAPER_SCYTHE_DRAIN_COOLDOWN + "s</white></gray>"
            ),
            CustomLoreUtil.section(
                "Passive",
                "Soul Rend",
                "<gray>Your next ready hit drains the target, heals you, and steals positive potion effects.</gray>",
                "<gray>The target is afflicted with <white>Wither II</white> while you gain <white>Regeneration II</white> and absorption.</gray>"
            )
        );
    }

    private List<Component> buildShadowBladeLore(ItemMeta meta, Player holder) {
        boolean monarch = holder != null
            && plugin.getSuperpowerManager() != null
            && plugin.getSuperpowerManager().hasPower(holder, me.rique.smpcore.power.SuperpowerType.MONARCH);
        boolean shadow = holder != null
            && plugin.getSuperpowerManager() != null
            && plugin.getSuperpowerManager().hasPower(holder, me.rique.smpcore.power.SuperpowerType.SHADOW);
        double damage = monarch ? SHADOW_BLADE_MONARCH_DAMAGE : SHADOW_BLADE_DAMAGE;
        double attackSpeed = shadow ? 3.0 : 1.8;
        return buildMythicLore(
            meta,
            Material.DIAMOND_SWORD,
            "BLADE",
            List.of(
                "<gray>Hit Damage: <white>" + formatDamageNumber(damage) + "</white></gray>",
                "<gray>Attack Speed: <white>" + formatDamageNumber(attackSpeed) + "</white></gray>",
                "<gray>Cooldown: <white>" + SHADOW_BLADE_COOLDOWN + "s</white></gray>"
            ),
            CustomLoreUtil.section(
                "Item Ability",
                "Veil Step",
                "<gray><white>Right-click</white> to gain <white>Invisibility</white> and <white>Speed III</white>.</gray>",
                "<gray>Base duration: <white>" + SHADOW_BLADE_DURATION_SECONDS + "s</white>.</gray>",
                "<gray>If the wielder has <white>Nightshade</white>, duration becomes <white>" + SHADOW_BLADE_SHADOW_DURATION_SECONDS + "s</white>.</gray>"
            ),
            CustomLoreUtil.section(
                "Passive",
                "Shadow Hunger",
                "<gray>The blade grows stronger in the hands of a <white>Sovereign</white> or <white>Nightshade</white>.</gray>",
                "<gray>Hitting another player instantly breaks the cloak.</gray>"
            )
        );
    }

    private List<Component> buildHeadhunterChestpieceLore(ItemMeta meta) {
        return buildLegendaryLore(
            meta,
            Material.DIAMOND_CHESTPLATE,
            "CHESTPLATE",
            List.of(
                "<gray>Protection: <white>III</white></gray>",
                "<gray>Max Rage: <white>" + HEADHUNTER_RAGE_MAX + "</white></gray>",
                "<gray>Fury Cooldown: <white>" + HEADHUNTER_COOLDOWN + "s</white></gray>"
            ),
            CustomLoreUtil.section(
                "Passive",
                "Battle Lust",
                "<gray>Wearing it grants <white>Strength I</white>.</gray>",
                "<gray>It can still be enchanted normally.</gray>"
            ),
            CustomLoreUtil.section(
                "Passive",
                "Headhunter's Rage",
                "<gray>Deal or take player damage to build Rage.</gray>",
                "<gray>At max Rage, gain <white>Strength III</white> and <white>Speed II</white> for <white>" + HEADHUNTER_BUFF_SECONDS + "s</white>.</gray>"
            )
        );
    }

    private List<Component> buildGodChestplateLore(ItemMeta meta) {
        return buildMythicLore(
            meta,
            Material.DIAMOND_CHESTPLATE,
            "CHESTPLATE",
            List.of(
                "<gray>Worn Blessing: <white>+5 hearts</white></gray>",
                "<gray>Knockback: <white>Negated</white></gray>",
                "<gray>Death Save Cooldown: <white>" + GOD_CHESTPLATE_COOLDOWN + "s</white></gray>"
            ),
            CustomLoreUtil.section(
                "Passive",
                "Divine Denial",
                "<gray>While worn, lethal damage is denied once per cooldown.</gray>",
                "<gray>Triggers a built-in totem-like rescue with no totem required.</gray>"
            ),
            CustomLoreUtil.section(
                "Limit",
                "Sacred Forge",
                "<gray>This chestplate cannot be enchanted.</gray>",
                "<gray>It remains permanently unbreakable.</gray>"
            )
        );
    }

    private List<Component> buildStrengthSwordLore(ItemMeta meta, int uniqueVictimCount) {
        int stage = strengthSwordStage(uniqueVictimCount);
        return buildMythicLore(
            meta,
            Material.NETHERITE_SWORD,
            "SWORD",
            List.of(
                "<gray>Unique Player Kills: <white>" + uniqueVictimCount + "</white></gray>",
                "<gray>Current Stage: <white>" + stage + "</white>/3</gray>",
                "<gray>Durability: <white>" + STRENGTH_SWORD_MAX_DURABILITY + "</white></gray>",
                "<gray>Beam Cooldown: <white>" + STRENGTH_SWORD_BEAM_COOLDOWN + "s</white> | Domain Cooldown: <white>" + STRENGTH_SWORD_DOMAIN_COOLDOWN + "s</white></gray>"
            ),
            CustomLoreUtil.section(
                "Stage I",
                "Curse Mark",
                "<gray>Unlocks after <white>1 unique player kill</white>.</gray>",
                "<gray>Hits on players apply <white>Weakness II</white> and <white>Slowness I</white> for <white>5s</white>.</gray>"
            ),
            CustomLoreUtil.section(
                "Stage II",
                "Crimson Beam",
                "<gray>Unlocks after <white>" + STRENGTH_SWORD_STAGE_TWO_KILLS + " unique player kills</white>.</gray>",
                "<gray><white>Shift + Left-click</white> to fire a crimson warden beam for <white>"
                    + formatDamageNumber(STRENGTH_SWORD_BEAM_TRUE_DAMAGE / 2.0) + " hearts true damage</white>.</gray>"
            ),
            CustomLoreUtil.section(
                "Stage III",
                "Dominion",
                "<gray>Unlocks after <white>" + STRENGTH_SWORD_STAGE_THREE_KILLS + " unique player kills</white>.</gray>",
                "<gray><white>Right-click</white> to open a <white>" + STRENGTH_SWORD_DOMAIN_RADIUS + " block</white> domain for <white>" + STRENGTH_SWORD_DOMAIN_SECONDS + "s</white>.</gray>",
                "<gray>Inside it, the user gains <white>+"
                    + formatDamageNumber(STRENGTH_SWORD_DOMAIN_HEALTH_BONUS / 2.0)
                    + " hearts</white>, <white>Strength III</white>, and <white>35% faster attack speed</white>.</gray>"
            ),
            CustomLoreUtil.section(
                "Limit",
                "Bound Edge",
                "<gray>This sword can never receive <white>Mending</white>.</gray>",
                "<gray>Repair it in an <white>Anvil</white> using a <white>Dominion Core</white>.</gray>"
            )
        );
    }

    private List<Component> buildPercyTridentLore(ItemMeta meta, boolean riptideMode) {
        String currentMode = riptideMode ? "Riptide" : "Throw";
        return buildLegendaryLore(
            meta,
            Material.TRIDENT,
            "TRIDENT",
            List.of(
                "<gray>Current Mode: <white>" + currentMode + "</white></gray>",
                "<gray>Bonus Damage: <white>+" + formatDamageNumber(PERCY_TRIDENT_DAMAGE_BONUS) + "</white> on hit and throw</gray>",
                "<gray>Grants <white>Water Breathing</white> while held</gray>"
            ),
            CustomLoreUtil.section(
                "Item Ability",
                "Tidal Shift",
                "<gray><white>Shift + Right-click</white> to swap between Throw and Riptide mode.</gray>",
                "<gray>Mode changes update the trident's enchantments instantly.</gray>"
            ),
            CustomLoreUtil.section(
                "Passive",
                "Sea Blessing",
                "<gray>Holding the trident grants <white>Water Breathing</white>.</gray>"
            ),
            CustomLoreUtil.section(
                "Throw Mode",
                "Storm Spear",
                "<gray>Applies <white>Loyalty III</white>, <white>Channeling I</white>, and <white>Impaling V</white>.</gray>"
            ),
            CustomLoreUtil.section(
                "Riptide Mode",
                "Sea Rush",
                "<gray>Applies <white>Riptide III</white> and <white>Impaling V</white>.</gray>"
            )
        );
    }

    private boolean isPercyTridentRiptideMode(ItemMeta meta) {
        return meta.getPersistentDataContainer().getOrDefault(
            keyPercyTridentMode,
            PersistentDataType.BYTE,
            PERCY_TRIDENT_MODE_THROW
        ) == PERCY_TRIDENT_MODE_RIPTIDE;
    }

    private void applyPercyTridentState(ItemMeta meta) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        boolean riptideMode = isPercyTridentRiptideMode(meta);
        pdc.set(
            keyPercyTridentMode,
            PersistentDataType.BYTE,
            riptideMode ? PERCY_TRIDENT_MODE_RIPTIDE : PERCY_TRIDENT_MODE_THROW
        );
        meta.setItemModel(null);
        setEnchantLevel(meta, enchantLoyalty, riptideMode ? 0 : 3);
        setEnchantLevel(meta, enchantChanneling, riptideMode ? 0 : 1);
        setEnchantLevel(meta, enchantImpaling, 5);
        setEnchantLevel(meta, enchantRiptide, riptideMode ? 3 : 0);
        meta.lore(buildPercyTridentLore(meta, riptideMode));
    }

    private void togglePercyTridentMode(Player player, ItemStack trident) {
        ItemMeta meta = trident.getItemMeta();
        if (meta == null) return;

        boolean nextRiptideMode = !isPercyTridentRiptideMode(meta);
        meta.getPersistentDataContainer().set(
            keyPercyTridentMode,
            PersistentDataType.BYTE,
            nextRiptideMode ? PERCY_TRIDENT_MODE_RIPTIDE : PERCY_TRIDENT_MODE_THROW
        );
        applyPercyTridentState(meta);
        trident.setItemMeta(meta);
        player.sendMessage(MessageUtil.info(
            "Trident of Percy mode: <white>" + (nextRiptideMode ? "Riptide" : "Throw") + "</white>."
        ));
    }

    private List<Component> buildWarPickLore(ItemMeta meta, WarPickMode mode) {
        return buildLegendaryLore(
            meta,
            Material.DIAMOND_PICKAXE,
            "PICKAXE",
            List.of(
                "<gray>Current Mode: <white>" + mode.label() + "</white></gray>",
                "<gray>Fortune: <white>III</white></gray>"
            ),
            CustomLoreUtil.section(
                "Item Ability",
                "War Mining",
                "<gray><white>Shift + Right-click</white> to cycle mining modes.</gray>",
                "<gray>Break a block to mine a <white>" + mode.label() + "</white> area.</gray>",
                "<gray>2D modes face the surface you are mining.</gray>"
            ),
            CustomLoreUtil.section(
                "Passive",
                "Battle Crush",
                "<gray>Critical hits can knock players back and damage armor.</gray>",
                "<gray>No mining cooldown.</gray>"
            )
        );
    }

    private WarPickMode warPickMode(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        return meta == null ? WarPickMode.defaultMode() : warPickMode(meta);
    }

    private WarPickMode warPickMode(ItemMeta meta) {
        return WarPickMode.fromOrdinal(meta.getPersistentDataContainer().getOrDefault(keyWarPickMode, PersistentDataType.INTEGER, 0));
    }

    private void applyWarPickState(ItemMeta meta) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        WarPickMode mode = warPickMode(meta);
        pdc.set(keyWarPickMode, PersistentDataType.INTEGER, mode.ordinal());
        meta.setItemModel(null);
        setEnchantLevel(meta, enchantSharpness, 10);
        setEnchantLevel(meta, enchantEfficiency, 1);
        setEnchantLevel(meta, enchantFortune, 3);
        meta.lore(buildWarPickLore(meta, mode));
    }

    private void toggleWarPickMode(Player player, ItemStack warPick) {
        ItemMeta meta = warPick.getItemMeta();
        if (meta == null) return;

        WarPickMode nextMode = warPickMode(meta).next();
        meta.getPersistentDataContainer().set(keyWarPickMode, PersistentDataType.INTEGER, nextMode.ordinal());
        applyWarPickState(meta);
        warPick.setItemMeta(meta);
        player.sendMessage(MessageUtil.info("Siegebreaker Pick mode: <white>" + nextMode.label() + "</white>."));
    }

    private List<Block> warPickTargets(Player player, Block center, WarPickMode mode) {
        List<Block> blocks = new ArrayList<>(mode.blockCount());
        int centerX = center.getX();
        int centerY = center.getY();
        int centerZ = center.getZ();

        if (!mode.planar()) {
            int radius = mode.size() / 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        blocks.add(center.getWorld().getBlockAt(centerX + dx, centerY + dy, centerZ + dz));
                    }
                }
            }
            return blocks;
        }

        WarPickAxis normalAxis = warPickNormalAxis(player);
        int[] firstOffsets = planarOffsets(mode.size(), axisBias(player, center, firstPlanarAxis(normalAxis)));
        int[] secondOffsets = planarOffsets(mode.size(), axisBias(player, center, secondPlanarAxis(normalAxis)));
        for (int firstOffset : firstOffsets) {
            for (int secondOffset : secondOffsets) {
                blocks.add(warPickRelativeBlock(center, normalAxis, firstOffset, secondOffset));
            }
        }
        return blocks;
    }

    private WarPickAxis warPickNormalAxis(Player player) {
        Vector direction = player.getEyeLocation().getDirection();
        if (Math.abs(direction.getY()) >= 0.65) {
            return WarPickAxis.Y;
        }
        return Math.abs(direction.getX()) >= Math.abs(direction.getZ()) ? WarPickAxis.X : WarPickAxis.Z;
    }

    private WarPickAxis firstPlanarAxis(WarPickAxis normalAxis) {
        return switch (normalAxis) {
            case X -> WarPickAxis.Y;
            case Y -> WarPickAxis.X;
            case Z -> WarPickAxis.X;
        };
    }

    private WarPickAxis secondPlanarAxis(WarPickAxis normalAxis) {
        return switch (normalAxis) {
            case X -> WarPickAxis.Z;
            case Y -> WarPickAxis.Z;
            case Z -> WarPickAxis.Y;
        };
    }

    private int axisBias(Player player, Block center, WarPickAxis axis) {
        Location playerLocation = player.getLocation();
        double centerCoordinate = switch (axis) {
            case X -> center.getX() + 0.5;
            case Y -> center.getY() + 0.5;
            case Z -> center.getZ() + 0.5;
        };
        double playerCoordinate = switch (axis) {
            case X -> playerLocation.getX();
            case Y -> playerLocation.getY();
            case Z -> playerLocation.getZ();
        };
        return playerCoordinate >= centerCoordinate ? 1 : -1;
    }

    private int[] planarOffsets(int size, int bias) {
        if (size <= 1) {
            return new int[] {0};
        }
        if ((size & 1) == 1) {
            int radius = size / 2;
            int[] offsets = new int[size];
            for (int i = 0; i < size; i++) {
                offsets[i] = i - radius;
            }
            return offsets;
        }
        return bias >= 0 ? new int[] {0, 1} : new int[] {-1, 0};
    }

    private Block warPickRelativeBlock(Block center, WarPickAxis normalAxis, int firstOffset, int secondOffset) {
        return switch (normalAxis) {
            case X -> center.getRelative(0, firstOffset, secondOffset);
            case Y -> center.getRelative(firstOffset, 0, secondOffset);
            case Z -> center.getRelative(firstOffset, secondOffset, 0);
        };
    }

    private List<Component> buildWindChargeCannonLore(ItemMeta meta, int charges, long cooldownUntil) {
        long secondsLeft = cooldownSecondsLeft(cooldownUntil);
        List<String> topLines = new ArrayList<>();
        topLines.add("<gray>Current Charges: <white>" + charges + "</white>/<white>" + WIND_CHARGE_CANNON_MAX_CHARGES + "</white></gray>");
        if (secondsLeft > 0) {
            topLines.add("<gray>Recharge Remaining: <white>" + secondsLeft + "s</white></gray>");
        }
        return buildLegendaryLore(
            meta,
            Material.PRISMARINE_SHARD,
            "UTILITY",
            topLines,
            CustomLoreUtil.section(
                "Item Ability",
                "Wind Cannon",
                "<gray><white>Right-click</white> to fire a normal wind charge.</gray>",
                "<gray><white>Left-click</white> to use the super launch blast.</gray>",
                "<gray>Spend all charges to trigger a <white>" + WIND_CHARGE_CANNON_RECHARGE + "s</white> recharge.</gray>"
            )
        );
    }

    private List<Component> buildLifeStealerLore(ItemMeta meta, LifeStealerState state) {
        int stacks = state.stacks();
        double absorptionHearts = lifeStealerAbsorptionAmount(stacks) / 2.0;
        int sharpness = lifeStealerSharpness(meta);
        List<String> topLines = new ArrayList<>();
        topLines.add("<gray>Current Bonus:</gray> <red>" + stacks + " Strength</red><gray>,</gray> <aqua>" + stacks + " Speed</aqua><gray>,</gray> <gold>" + formatDamageNumber(absorptionHearts) + " Absorption Hearts</gold>");
        topLines.add("<gray>Current Sharpness: <white>" + sharpness + "</white>/<white>" + LIFE_STEALER_MAX_SHARPNESS + "</white></gray>");
        return buildMythicLore(
            meta,
            Material.NETHERITE_SWORD,
            "SWORD",
            topLines,
            CustomLoreUtil.section(
                "Item Ability",
                "Life Drain",
                "<gray>Regain <red>0.5 hearts</red> when hitting a mob or player.</gray>"
            ),
            CustomLoreUtil.section(
                "Item Ability",
                "Blood Alchemy",
                "<gray>Unique player kills permanently build up to <white>Strength III</white>, <white>Speed III</white>, and</gray>",
                "<gray><white>5 Absorption Hearts</white> while you carry the blade.</gray>",
                "<gray>Every player kill also raises the sword by <white>+1 Sharpness</white> up to <white>" + LIFE_STEALER_MAX_SHARPNESS + "</white>.</gray>"
            )
        );
    }

    private boolean isMagnetActive(ItemStack magnet) {
        ItemMeta meta = magnet.getItemMeta();
        if (meta == null) return false;
        Byte raw = meta.getPersistentDataContainer().get(keyMagnetActive, PersistentDataType.BYTE);
        return raw != null && raw == (byte) 1;
    }

    private void setMagnetActive(ItemStack magnet, boolean active) {
        ItemMeta meta = magnet.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(keyMagnetActive, PersistentDataType.BYTE, active ? (byte) 1 : (byte) 0);
        meta.getPersistentDataContainer().remove(new NamespacedKey(plugin, "faraday_uses"));
        meta.lore(buildMagnetLore(meta, active));
        magnet.setItemMeta(meta);
    }

    private boolean refreshWindChargeCannonState(ItemStack cannon) {
        ItemMeta meta = cannon.getItemMeta();
        if (meta == null) return false;

        int charges = clampWindChargeCannonCharges(
            meta.getPersistentDataContainer().getOrDefault(
                keyWindCannonCharges,
                PersistentDataType.INTEGER,
                WIND_CHARGE_CANNON_MAX_CHARGES
            )
        );
        long cooldownUntil = meta.getPersistentDataContainer().getOrDefault(
            keyWindCannonCooldownUntil,
            PersistentDataType.LONG,
            0L
        );

        if (cooldownUntil > 0L && cooldownUntil <= System.currentTimeMillis()) {
            charges = WIND_CHARGE_CANNON_MAX_CHARGES;
            cooldownUntil = 0L;
        } else if (cooldownUntil > System.currentTimeMillis()) {
            charges = 0;
        }

        applyWindChargeCannonState(cannon, meta, charges, cooldownUntil);
        return true;
    }

    private int windChargeCannonCharges(ItemStack cannon) {
        ItemMeta meta = cannon.getItemMeta();
        if (meta == null) return 0;
        long cooldownUntil = meta.getPersistentDataContainer().getOrDefault(
            keyWindCannonCooldownUntil,
            PersistentDataType.LONG,
            0L
        );
        if (cooldownUntil > System.currentTimeMillis()) {
            return 0;
        }
        return clampWindChargeCannonCharges(
            meta.getPersistentDataContainer().getOrDefault(
                keyWindCannonCharges,
                PersistentDataType.INTEGER,
                WIND_CHARGE_CANNON_MAX_CHARGES
            )
        );
    }

    private long windChargeCannonSecondsLeft(ItemStack cannon) {
        ItemMeta meta = cannon.getItemMeta();
        if (meta == null) return 0L;
        long cooldownUntil = meta.getPersistentDataContainer().getOrDefault(
            keyWindCannonCooldownUntil,
            PersistentDataType.LONG,
            0L
        );
        return cooldownSecondsLeft(cooldownUntil);
    }

    private void setWindChargeCannonState(ItemStack cannon, int charges, long cooldownUntil) {
        ItemMeta meta = cannon.getItemMeta();
        if (meta == null) return;
        applyWindChargeCannonState(cannon, meta, charges, cooldownUntil);
    }

    private void applyWindChargeCannonState(ItemStack cannon, ItemMeta meta, int charges, long cooldownUntil) {
        writeWindChargeCannonState(meta, charges, cooldownUntil);
        cannon.setItemMeta(meta);
    }

    private void writeWindChargeCannonState(ItemMeta meta, int charges, long cooldownUntil) {
        int normalizedCharges = cooldownUntil > System.currentTimeMillis()
            ? 0
            : clampWindChargeCannonCharges(charges);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyWindCannonCharges, PersistentDataType.INTEGER, normalizedCharges);
        if (cooldownUntil > System.currentTimeMillis()) {
            pdc.set(keyWindCannonCooldownUntil, PersistentDataType.LONG, cooldownUntil);
        } else {
            pdc.remove(keyWindCannonCooldownUntil);
        }
        meta.lore(buildWindChargeCannonLore(meta, normalizedCharges, cooldownUntil));
    }

    private int clampWindChargeCannonCharges(int charges) {
        return Math.max(0, Math.min(WIND_CHARGE_CANNON_MAX_CHARGES, charges));
    }

    private long cooldownSecondsLeft(long cooldownUntil) {
        long diff = cooldownUntil - System.currentTimeMillis();
        return diff <= 0 ? 0L : (diff + 999L) / 1000L;
    }

    private void sendWindChargeCannonActionBar(Player player, int remainingCharges) {
        if (remainingCharges > 0) {
            player.sendActionBar(MM.deserialize(
                "<gold><bold>Tempest Cannon</bold></gold><gray> charges: <white>" + remainingCharges + "/" + WIND_CHARGE_CANNON_MAX_CHARGES + "</white>.</gray>"
            ));
            return;
        }
        player.sendActionBar(MM.deserialize(
            "<gold><bold>Tempest Cannon</bold></gold><gray> depleted. Recharge: <white>" + WIND_CHARGE_CANNON_RECHARGE + "s</white>.</gray>"
        ));
    }

    private boolean refreshWitherBladeState(ItemStack blade) {
        ItemMeta meta = blade.getItemMeta();
        if (meta == null) return false;
        applyWitherBladeState(blade, refreshWitherBladeState(meta));
        return true;
    }

    private boolean refreshWitherBladeState(ItemStack blade, Player owner) {
        ItemMeta meta = blade.getItemMeta();
        if (meta == null) return false;
        WitherBladeState state = refreshWitherBladeState(meta);
        applyWitherBladeState(blade, state, witherBladeSkullCount(owner), witherBladeBonusDamage(owner));
        return true;
    }

    private WitherBladeState witherBladeState(ItemStack blade) {
        ItemMeta meta = blade.getItemMeta();
        if (meta == null) {
            return new WitherBladeState(
                WITHER_BLADE_SKULL_MAX_CHARGES, 0L,
                WITHER_BLADE_DASH_MAX_CHARGES, 0L
            );
        }
        return refreshWitherBladeState(meta);
    }

    private WitherBladeState refreshWitherBladeState(ItemMeta meta) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        RechargeState skull = refreshRechargeState(
            new RechargeState(
                pdc.getOrDefault(keyWitherBladeSkullCharges, PersistentDataType.INTEGER, WITHER_BLADE_SKULL_MAX_CHARGES),
                pdc.getOrDefault(keyWitherBladeSkullRechargeStarted, PersistentDataType.LONG, 0L)
            ),
            WITHER_BLADE_SKULL_MAX_CHARGES,
            WITHER_BLADE_SKULL_RECHARGE_MS
        );
        RechargeState dash = refreshRechargeState(
            new RechargeState(
                pdc.getOrDefault(keyWitherBladeDashCharges, PersistentDataType.INTEGER, WITHER_BLADE_DASH_MAX_CHARGES),
                pdc.getOrDefault(keyWitherBladeDashRechargeStarted, PersistentDataType.LONG, 0L)
            ),
            WITHER_BLADE_DASH_MAX_CHARGES,
            WITHER_BLADE_DASH_RECHARGE_MS
        );
        return new WitherBladeState(skull.charges(), skull.rechargeStartedAt(), dash.charges(), dash.rechargeStartedAt());
    }

    private RechargeState refreshRechargeState(RechargeState state, int maxCharges, long rechargeMs) {
        int charges = Math.max(0, Math.min(maxCharges, state.charges()));
        long rechargeStartedAt = state.rechargeStartedAt();
        if (charges >= maxCharges) {
            return new RechargeState(maxCharges, 0L);
        }

        long now = System.currentTimeMillis();
        if (rechargeStartedAt <= 0L || rechargeStartedAt > now) {
            rechargeStartedAt = now;
        }

        long recovered = (now - rechargeStartedAt) / rechargeMs;
        if (recovered <= 0L) {
            return new RechargeState(charges, rechargeStartedAt);
        }

        charges = Math.min(maxCharges, charges + (int) recovered);
        if (charges >= maxCharges) {
            return new RechargeState(maxCharges, 0L);
        }
        return new RechargeState(charges, rechargeStartedAt + (recovered * rechargeMs));
    }

    private RechargeState spendRechargeState(RechargeState state, int maxCharges) {
        if (state.charges() <= 0) return state;
        long now = System.currentTimeMillis();
        long rechargeStartedAt = state.rechargeStartedAt();
        if (state.charges() >= maxCharges || rechargeStartedAt <= 0L) {
            rechargeStartedAt = now;
        }
        return new RechargeState(state.charges() - 1, rechargeStartedAt);
    }

    private WitherBladeState spendWitherBladeSkull(WitherBladeState state) {
        RechargeState spent = spendRechargeState(
            new RechargeState(state.skullCharges(), state.skullRechargeStartedAt()),
            WITHER_BLADE_SKULL_MAX_CHARGES
        );
        return new WitherBladeState(
            spent.charges(),
            spent.rechargeStartedAt(),
            state.dashCharges(),
            state.dashRechargeStartedAt()
        );
    }

    private WitherBladeState spendWitherBladeDash(WitherBladeState state) {
        RechargeState spent = spendRechargeState(
            new RechargeState(state.dashCharges(), state.dashRechargeStartedAt()),
            WITHER_BLADE_DASH_MAX_CHARGES
        );
        return new WitherBladeState(
            state.skullCharges(),
            state.skullRechargeStartedAt(),
            spent.charges(),
            spent.rechargeStartedAt()
        );
    }

    private void applyWitherBladeState(ItemStack blade, WitherBladeState state) {
        ItemMeta meta = blade.getItemMeta();
        if (meta == null) return;
        applyWitherBladeState(meta, state.skullCharges(), state.skullRechargeStartedAt(), state.dashCharges(), state.dashRechargeStartedAt());
        blade.setItemMeta(meta);
    }

    private void applyWitherBladeState(ItemStack blade, WitherBladeState state, int carriedSkulls, double bonusDamage) {
        ItemMeta meta = blade.getItemMeta();
        if (meta == null) return;
        applyWitherBladeState(
            meta,
            state.skullCharges(),
            state.skullRechargeStartedAt(),
            state.dashCharges(),
            state.dashRechargeStartedAt(),
            carriedSkulls,
            bonusDamage
        );
        blade.setItemMeta(meta);
    }

    private void applyWitherBladeState(ItemMeta meta, int skullCharges, long skullRechargeStartedAt, int dashCharges, long dashRechargeStartedAt) {
        applyWitherBladeState(meta, skullCharges, skullRechargeStartedAt, dashCharges, dashRechargeStartedAt, 0, 0.0);
    }

    private void applyWitherBladeState(
        ItemMeta meta,
        int skullCharges,
        long skullRechargeStartedAt,
        int dashCharges,
        long dashRechargeStartedAt,
        int carriedSkulls,
        double bonusDamage
    ) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        meta.setItemModel(null);
        pdc.set(keyWitherBladeSkullCharges, PersistentDataType.INTEGER, skullCharges);
        pdc.set(keyWitherBladeDashCharges, PersistentDataType.INTEGER, dashCharges);
        if (skullCharges >= WITHER_BLADE_SKULL_MAX_CHARGES || skullRechargeStartedAt <= 0L) {
            pdc.remove(keyWitherBladeSkullRechargeStarted);
        } else {
            pdc.set(keyWitherBladeSkullRechargeStarted, PersistentDataType.LONG, skullRechargeStartedAt);
        }
        if (dashCharges >= WITHER_BLADE_DASH_MAX_CHARGES || dashRechargeStartedAt <= 0L) {
            pdc.remove(keyWitherBladeDashRechargeStarted);
        } else {
            pdc.set(keyWitherBladeDashRechargeStarted, PersistentDataType.LONG, dashRechargeStartedAt);
        }
        meta.lore(buildWitherBladeLore(
            meta,
            skullCharges,
            skullRechargeStartedAt,
            dashCharges,
            dashRechargeStartedAt,
            carriedSkulls,
            bonusDamage
        ));
    }

    private List<Component> buildWitherBladeLore(
        ItemMeta meta,
        int skullCharges,
        long skullRechargeStartedAt,
        int dashCharges,
        long dashRechargeStartedAt,
        int carriedSkulls,
        double bonusDamage
    ) {
        long skullNext = millisUntilNextCharge(skullCharges, skullRechargeStartedAt, WITHER_BLADE_SKULL_MAX_CHARGES, WITHER_BLADE_SKULL_RECHARGE_MS);
        long dashNext = millisUntilNextCharge(dashCharges, dashRechargeStartedAt, WITHER_BLADE_DASH_MAX_CHARGES, WITHER_BLADE_DASH_RECHARGE_MS);
        List<String> topLines = new ArrayList<>();
        topLines.add("<gray>Wither Skulls Carried: <white>" + carriedSkulls + "</white></gray>");
        topLines.add("<gray>Current Bonus Damage: <white>+" + formatDamageNumber(bonusDamage) + "</white></gray>");
        topLines.add(
            "<gray>Skull Charges: <white>" + skullCharges + "</white>/<white>" + WITHER_BLADE_SKULL_MAX_CHARGES
                + "</white> <dark_gray>(+1 every " + formatSeconds(WITHER_BLADE_SKULL_RECHARGE_MS) + "s)</dark_gray></gray>"
        );
        topLines.add(
            "<gray>Dash Charges: <white>" + dashCharges + "</white>/<white>" + WITHER_BLADE_DASH_MAX_CHARGES
                + "</white> <dark_gray>(+1 every " + formatSeconds(WITHER_BLADE_DASH_RECHARGE_MS) + "s)</dark_gray></gray>"
        );
        if (skullNext > 0L) {
            topLines.add("<gray>Next Skull Charge: <white>" + formatSeconds(skullNext) + "s</white></gray>");
        }
        if (dashNext > 0L) {
            topLines.add("<gray>Next Dash Charge: <white>" + formatSeconds(dashNext) + "s</white></gray>");
        }
        return buildLegendaryLore(
            meta,
            Material.NETHERITE_SWORD,
            "SWORD",
            topLines,
            CustomLoreUtil.section(
                "Passive",
                "Skull Hunger",
                "<gray>Gain <white>+1 damage</white> per Wither Skull carried.</gray>",
                "<gray>Caps at <white>+3 damage</white>.</gray>"
            ),
            CustomLoreUtil.section(
                "Item Ability",
                "Wither Skull",
                "<gray><white>Right-click</white> to fire an explosive wither skull.</gray>"
            ),
            CustomLoreUtil.section(
                "Item Ability",
                "Wither Dash",
                "<gray><white>Shift + Right-click</white> to dash forward in a cloud of dragon breath.</gray>"
            )
        );
    }

    private static long millisUntilNextCharge(int charges, long rechargeStartedAt, int maxCharges, long rechargeMs) {
        if (charges >= maxCharges) return 0L;
        if (rechargeStartedAt <= 0L) return rechargeMs;
        long elapsed = Math.max(0L, System.currentTimeMillis() - rechargeStartedAt);
        long remaining = rechargeMs - elapsed;
        return Math.max(1L, remaining);
    }

    private String formatSeconds(long millis) {
        double seconds = millis / 1000.0;
        if (Math.abs(seconds - Math.rint(seconds)) < 0.0001) {
            return Long.toString(Math.round(seconds));
        }
        return String.format(java.util.Locale.US, "%.1f", seconds);
    }

    private String formatDamageNumber(double damage) {
        if (Math.abs(damage - Math.rint(damage)) < 0.0001) {
            return Long.toString(Math.round(damage));
        }
        return String.format(java.util.Locale.US, "%.1f", damage);
    }

    private void scheduleLegendaryDuplicateAudit() {
        if (legendaryDuplicateAuditQueued) {
            return;
        }
        legendaryDuplicateAuditQueued = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            legendaryDuplicateAuditQueued = false;
            auditLegendaryClaimsAndDuplicates();
        });
    }

    private void auditLegendaryClaimsAndDuplicates() {
        LegendaryAltarManager altarManager = plugin.getLegendaryAltarManager();
        if (altarManager == null) {
            return;
        }

        Map<String, List<LegendaryCopy>> copiesByLegendaryId = new LinkedHashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            collectLegendaryCopies(player, player.getInventory(), copiesByLegendaryId);
            collectLegendaryCopies(player, player.getEnderChest(), copiesByLegendaryId);
            collectLegendaryCursorCopy(player, copiesByLegendaryId);
        }

        for (Map.Entry<String, List<LegendaryCopy>> entry : copiesByLegendaryId.entrySet()) {
            String legendaryId = entry.getKey();
            List<LegendaryCopy> copies = entry.getValue();
            if (copies.isEmpty()) {
                continue;
            }

            int totalCopies = 0;
            for (LegendaryCopy copy : copies) {
                totalCopies += Math.max(1, copy.amount());
            }
            int maxCopies = maxServerCopiesForLegendary(legendaryId);
            if (totalCopies <= maxCopies) {
                continue;
            }

            refundAndRemoveDuplicateLegendaries(legendaryId, copies, maxCopies);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            syncLegendaryOwnership(player);
        }
    }

    private void syncLegendaryOwnership(Player player) {
        LegendaryAltarManager altarManager = plugin.getLegendaryAltarManager();
        if (altarManager == null || player == null) {
            return;
        }

        Map<String, String> heldLegendaryInstances = new LinkedHashMap<>();
        collectLegendaryOwnership(player.getInventory(), heldLegendaryInstances);
        collectLegendaryOwnership(player.getEnderChest(), heldLegendaryInstances);
        collectLegendaryOwnership(player.getOpenInventory().getCursor(), heldLegendaryInstances);
        altarManager.syncLegendaryOwnership(player.getUniqueId(), heldLegendaryInstances);
    }

    private void collectLegendaryOwnership(Inventory inventory, Map<String, String> heldLegendaryInstances) {
        if (inventory == null || heldLegendaryInstances == null) {
            return;
        }

        for (ItemStack item : inventory.getContents()) {
            collectLegendaryOwnership(item, heldLegendaryInstances);
        }
    }

    private void collectLegendaryOwnership(ItemStack item, Map<String, String> heldLegendaryInstances) {
        collectLegendaryOwnership(item, heldLegendaryInstances, 0);
    }

    private void collectLegendaryOwnership(ItemStack item, Map<String, String> heldLegendaryInstances, int depth) {
        if (heldLegendaryInstances == null) {
            return;
        }
        if (item == null || item.getType() == Material.AIR || depth > LEGENDARY_ITEM_SCAN_MAX_DEPTH) {
            return;
        }

        LegendaryType type = typeOf(item);
        if (type != null && isExclusiveLegendaryType(type)) {
            String instanceId = legendaryInstanceId(item);
            if (instanceId != null && !instanceId.isBlank()) {
                heldLegendaryInstances.put(instanceId, type.id);
            }
        }

        if (depth >= LEGENDARY_ITEM_SCAN_MAX_DEPTH) {
            return;
        }

        if (plugin.getBackpackListener() != null && plugin.getBackpackListener().isBackpack(item)) {
            for (ItemStack nested : plugin.getBackpackListener().auditContents(null, item)) {
                collectLegendaryOwnership(nested, heldLegendaryInstances, depth + 1);
            }
        }

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BundleMeta bundleMeta) {
            for (ItemStack nested : bundleMeta.getItems()) {
                collectLegendaryOwnership(nested, heldLegendaryInstances, depth + 1);
            }
        }

        if (!(meta instanceof BlockStateMeta blockStateMeta)) {
            return;
        }

        BlockState state = blockStateMeta.getBlockState();
        if (!(state instanceof InventoryHolder holder)) {
            return;
        }

        Inventory nestedInventory = holder.getInventory();
        if (nestedInventory == null) {
            return;
        }
        for (ItemStack nested : nestedInventory.getContents()) {
            collectLegendaryOwnership(nested, heldLegendaryInstances, depth + 1);
        }
    }

    private boolean containsStorageRestrictedLegendary(ItemStack item, int depth) {
        if (item == null || item.getType() == Material.AIR || depth > LEGENDARY_ITEM_SCAN_MAX_DEPTH) {
            return false;
        }

        LegendaryType type = typeOf(item);
        if (isStorageRestrictedLegendaryType(type)) {
            return true;
        }

        if (depth >= LEGENDARY_ITEM_SCAN_MAX_DEPTH) {
            return false;
        }

        if (plugin.getBackpackListener() != null && plugin.getBackpackListener().isBackpack(item)) {
            for (ItemStack nested : plugin.getBackpackListener().auditContents(null, item)) {
                if (containsStorageRestrictedLegendary(nested, depth + 1)) {
                    return true;
                }
            }
        }

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BundleMeta bundleMeta) {
            for (ItemStack nested : bundleMeta.getItems()) {
                if (containsStorageRestrictedLegendary(nested, depth + 1)) {
                    return true;
                }
            }
        }

        if (!(meta instanceof BlockStateMeta blockStateMeta)) {
            return false;
        }

        BlockState state = blockStateMeta.getBlockState();
        if (!(state instanceof InventoryHolder holder)) {
            return false;
        }

        Inventory nestedInventory = holder.getInventory();
        if (nestedInventory == null) {
            return false;
        }
        for (ItemStack nested : nestedInventory.getContents()) {
            if (containsStorageRestrictedLegendary(nested, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private void collectLegendaryCopies(Player player, Inventory inventory, Map<String, List<LegendaryCopy>> copiesByLegendaryId) {
        if (player == null || inventory == null) {
            return;
        }

        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            final int targetSlot = slot;
            collectLegendaryCopies(
                player,
                item,
                copiesByLegendaryId,
                0,
                replacement -> inventory.setItem(targetSlot, replacement)
            );
        }
    }

    private void collectLegendaryCursorCopy(Player player, Map<String, List<LegendaryCopy>> copiesByLegendaryId) {
        if (player == null || copiesByLegendaryId == null) {
            return;
        }

        ItemStack cursor = player.getOpenInventory().getCursor();
        collectLegendaryCopies(player, cursor, copiesByLegendaryId, 0, player::setItemOnCursor);
    }

    private void collectLegendaryCopies(
        Player player,
        ItemStack item,
        Map<String, List<LegendaryCopy>> copiesByLegendaryId,
        int depth,
        Consumer<ItemStack> replacementSink
    ) {
        if (player == null || copiesByLegendaryId == null || item == null || item.getType() == Material.AIR || depth > LEGENDARY_ITEM_SCAN_MAX_DEPTH) {
            return;
        }

        LegendaryType type = typeOf(item);
        if (type != null && isExclusiveLegendaryType(type)) {
            copiesByLegendaryId.computeIfAbsent(type.id, ignored -> new ArrayList<>())
                .add(new LegendaryCopy(
                    player,
                    type,
                    Math.max(1, item.getAmount()),
                    () -> replacementSink.accept(null)
                ));
            return;
        }

        if (depth >= LEGENDARY_ITEM_SCAN_MAX_DEPTH) {
            return;
        }

        collectBackpackLegendaryCopies(player, item, copiesByLegendaryId, depth, replacementSink);
        collectBundleLegendaryCopies(player, item, copiesByLegendaryId, depth, replacementSink);
        collectBlockStateLegendaryCopies(player, item, copiesByLegendaryId, depth, replacementSink);
    }

    private void collectBackpackLegendaryCopies(
        Player player,
        ItemStack item,
        Map<String, List<LegendaryCopy>> copiesByLegendaryId,
        int depth,
        Consumer<ItemStack> replacementSink
    ) {
        if (plugin.getBackpackListener() == null || !plugin.getBackpackListener().isBackpack(item)) {
            return;
        }
        List<ItemStack> nested = plugin.getBackpackListener().auditContents(null, item);
        if (nested.isEmpty()) {
            return;
        }
        ItemStack[] contents = nested.toArray(new ItemStack[0]);
        for (int slot = 0; slot < contents.length; slot++) {
            final int nestedSlot = slot;
            collectLegendaryCopies(player, contents[slot], copiesByLegendaryId, depth + 1, replacement -> {
                contents[nestedSlot] = replacement == null ? null : replacement.clone();
                if (plugin.getBackpackListener().rewriteAuditContents(item, contents)) {
                    replacementSink.accept(item);
                }
            });
        }
    }

    private void collectBundleLegendaryCopies(
        Player player,
        ItemStack item,
        Map<String, List<LegendaryCopy>> copiesByLegendaryId,
        int depth,
        Consumer<ItemStack> replacementSink
    ) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BundleMeta bundleMeta)) {
            return;
        }
        List<ItemStack> contents = new ArrayList<>(bundleMeta.getItems());
        for (int slot = 0; slot < contents.size(); slot++) {
            final int nestedSlot = slot;
            collectLegendaryCopies(player, contents.get(slot), copiesByLegendaryId, depth + 1, replacement -> {
                contents.set(nestedSlot, replacement == null ? null : replacement.clone());
                ItemMeta currentMeta = item.getItemMeta();
                if (!(currentMeta instanceof BundleMeta currentBundleMeta)) {
                    return;
                }
                currentBundleMeta.setItems(nonEmptyCopies(contents));
                item.setItemMeta(currentBundleMeta);
                replacementSink.accept(item);
            });
        }
    }

    private void collectBlockStateLegendaryCopies(
        Player player,
        ItemStack item,
        Map<String, List<LegendaryCopy>> copiesByLegendaryId,
        int depth,
        Consumer<ItemStack> replacementSink
    ) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta blockStateMeta)) {
            return;
        }
        BlockState state = blockStateMeta.getBlockState();
        if (!(state instanceof InventoryHolder holder)) {
            return;
        }

        Inventory nestedInventory = holder.getInventory();
        if (nestedInventory == null) {
            return;
        }
        ItemStack[] contents = nestedInventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            final int nestedSlot = slot;
            collectLegendaryCopies(player, contents[slot], copiesByLegendaryId, depth + 1, replacement -> {
                contents[nestedSlot] = replacement == null ? null : replacement.clone();
                nestedInventory.setContents(contents);
                blockStateMeta.setBlockState(state);
                item.setItemMeta(blockStateMeta);
                replacementSink.accept(item);
            });
        }
    }

    private List<ItemStack> nonEmptyCopies(List<ItemStack> contents) {
        List<ItemStack> cleaned = new ArrayList<>();
        if (contents == null) {
            return cleaned;
        }
        for (ItemStack item : contents) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            cleaned.add(item.clone());
        }
        return cleaned;
    }

    private boolean isExclusiveLegendaryType(LegendaryType type) {
        return type != null && type != LegendaryType.FARADAYS_MAGNET;
    }

    private boolean isStorageRestrictedLegendaryType(LegendaryType type) {
        return type != null && maxServerCopiesForLegendary(type) != Integer.MAX_VALUE;
    }

    private int maxServerCopiesForLegendary(LegendaryType type) {
        if (type == null || type == LegendaryType.FARADAYS_MAGNET) {
            return Integer.MAX_VALUE;
        }
        return 1;
    }

    private boolean isMythicForgeSourceLegendary(LegendaryType type) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case EMERALD_BLADE,
                 DIVINE_AXE_RHITTA,
                 WITHER_BLADE,
                 EXECUTIONER_BLADE,
                 BLINK_DAGGER,
                 HYPNOSIS_STAFF,
                 HARD_HITTER,
                 WARDEN_BLADE,
                 ENDER_SWORD,
                 CHRONO_SWORD,
                 FROST_SCYTHE,
                 TRIDENT_OF_PERCY,
                 THORS_HAMMER,
                 DASH_MACE -> true;
            default -> false;
        };
    }

    private boolean isMythicForgeOutputLegendary(LegendaryType type) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case MIDAS_SWORD,
                 REAPERS_SCYTHE,
                 SHADOW_BLADE,
                 STRENGTH_SWORD,
                 PARADOX_REAVER,
                 TEMPEST_TRIDENT,
                 STORMFALL_MAUL -> true;
            default -> false;
        };
    }

    private void refundAndRemoveDuplicateLegendaries(String legendaryId, List<LegendaryCopy> copies, int allowedCopies) {
        Map<UUID, Map<LegendaryType, Integer>> refundsByPlayer = new LinkedHashMap<>();
        Map<UUID, Player> playersById = new HashMap<>();
        List<LegendaryCopy> copiesToRemove = new ArrayList<>(copies);

        for (LegendaryCopy copy : copiesToRemove) {
            removeLegendaryCopy(copy);
            refundsByPlayer
                .computeIfAbsent(copy.player().getUniqueId(), ignored -> new LinkedHashMap<>())
                .merge(copy.type(), Math.max(1, copy.amount()), Integer::sum);
            playersById.put(copy.player().getUniqueId(), copy.player());
        }

        for (Map.Entry<UUID, Map<LegendaryType, Integer>> entry : refundsByPlayer.entrySet()) {
            Player player = playersById.get(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }

            for (Map.Entry<LegendaryType, Integer> refund : entry.getValue().entrySet()) {
                refundLegendaryMaterials(player, refund.getKey(), refund.getValue());
            }
            player.updateInventory();
        }

        plugin.getLogger().warning(
            "Removed all legendary copies for " + legendaryId + " after it exceeded the server cap of " + allowedCopies
                + " and refunded recipe materials."
        );
    }

    private void removeLegendaryCopy(LegendaryCopy copy) {
        if (copy == null || copy.player() == null || copy.remover() == null) {
            return;
        }
        copy.remover().run();
    }

    private void refundLegendaryMaterials(Player player, LegendaryType type, int copiesRemoved) {
        if (copiesRemoved <= 0) {
            return;
        }

        if (refundMythicForgeInputs(player, type, copiesRemoved)) {
            return;
        }

        Map<Material, Integer> ingredients = ingredientsFor(type);
        if (ingredients.isEmpty()) {
            player.sendMessage(MessageUtil.error(
                "Duplicate " + type.display + "<red> was removed, but no refund recipe exists.</red>"
            ));
            return;
        }

        List<ItemStack> refundStacks = new ArrayList<>();
        for (Map.Entry<Material, Integer> ingredient : ingredients.entrySet()) {
            int remaining = ingredient.getValue() * copiesRemoved;
            ItemStack baseItem = recipeIngredientBaseItem(type, ingredient.getKey());
            int maxStack = Math.max(1, baseItem.getType().getMaxStackSize());
            while (remaining > 0) {
                int amount = Math.min(remaining, maxStack);
                refundStacks.add(recipeIngredientBaseItem(type, ingredient.getKey()).asQuantity(amount));
                remaining -= amount;
            }
        }

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(refundStacks.toArray(new ItemStack[0]));
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage(MessageUtil.warn(
            "Duplicate " + type.display + "<yellow> was removed. Refunded materials for <white>"
                + copiesRemoved + "</white> copy/copies.</yellow>"
        ));
    }

    private boolean refundMythicForgeInputs(Player player, LegendaryType type, int copiesRemoved) {
        MythicForgeListener forge = plugin.getMythicForgeListener();
        if (forge == null) {
            return false;
        }

        MythicForgeListener.FusionRecipeView recipe = forge.fusionRecipeForOutput(type.id);
        if (recipe == null) {
            return false;
        }

        LegendaryType leftType = LegendaryType.fromId(recipe.leftId());
        LegendaryType rightType = LegendaryType.fromId(recipe.rightId());
        if (leftType == null || rightType == null) {
            return false;
        }

        List<ItemStack> refundStacks = new ArrayList<>();
        if (!appendRecipeMaterialRefund(refundStacks, leftType, copiesRemoved)
            || !appendRecipeMaterialRefund(refundStacks, rightType, copiesRemoved)) {
            return false;
        }
        for (int i = 0; i < copiesRemoved; i++) {
            refundStacks.add(forge.createAscendantCoreItem());
        }

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(refundStacks.toArray(new ItemStack[0]));
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage(MessageUtil.warn(
            "Duplicate " + type.display + "<yellow> was removed. Refunded the source relic materials and Ascendant Core for <white>"
                + copiesRemoved + "</white> copy/copies.</yellow>"
        ));
        return true;
    }

    private boolean appendRecipeMaterialRefund(List<ItemStack> refundStacks, LegendaryType type, int copiesRemoved) {
        Map<Material, Integer> ingredients = ingredientsFor(type);
        if (ingredients.isEmpty()) {
            return false;
        }

        for (Map.Entry<Material, Integer> ingredient : ingredients.entrySet()) {
            int remaining = ingredient.getValue() * copiesRemoved;
            ItemStack baseItem = recipeIngredientBaseItem(type, ingredient.getKey());
            int maxStack = Math.max(1, baseItem.getType().getMaxStackSize());
            while (remaining > 0) {
                int amount = Math.min(remaining, maxStack);
                refundStacks.add(recipeIngredientBaseItem(type, ingredient.getKey()).asQuantity(amount));
                remaining -= amount;
            }
        }
        return true;
    }

    private void sendWitherBladeActionBar(Player player, WitherBladeState state) {
        player.sendActionBar(MM.deserialize(
            "<dark_gray><bold>Wither Blade</bold></dark_gray><gray> skulls: <white>"
                + state.skullCharges() + "/" + WITHER_BLADE_SKULL_MAX_CHARGES
                + "</white> | dash: <white>"
                + state.dashCharges() + "/" + WITHER_BLADE_DASH_MAX_CHARGES
                + "</white></gray>"
        ));
    }

    private void scheduleLoadedChunkLegendaryMigration() {
        ArrayDeque<Chunk> queue = new ArrayDeque<>();
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                queue.addLast(chunk);
            }
        }
        if (queue.isEmpty()) return;

        final int[] taskId = new int[1];
        taskId[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            int processed = 0;
            while (processed < STARTUP_LEGENDARY_MIGRATION_CHUNKS_PER_TICK && !queue.isEmpty()) {
                migrateLegendaryItemsInChunk(queue.removeFirst());
                processed++;
            }
            if (queue.isEmpty()) {
                Bukkit.getScheduler().cancelTask(taskId[0]);
            }
        }, 1L, 1L);
    }

    private int migratePlayerLegendaryItems(Player player) {
        int migrated = 0;
        migrated += migrateLegendaryItemsInInventory(player.getInventory());
        migrated += migrateLegendaryItemsInInventory(player.getEnderChest());
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top != null) {
            migrated += migrateLegendaryItemsInInventory(top);
        }
        return migrated;
    }

    private int migrateLegendaryItemsInChunk(Chunk chunk) {
        int migrated = 0;
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof InventoryHolder holder) {
                migrated += migrateLegendaryItemsInInventory(holder.getInventory());
            }
        }
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Item dropped) {
                ItemStack stack = dropped.getItemStack();
                if (migrateLegendaryItemTree(stack)) {
                    dropped.setItemStack(stack);
                    migrated++;
                }
                continue;
            }
            if (entity instanceof ItemFrame frame) {
                ItemStack stack = frame.getItem();
                if (migrateLegendaryItemTree(stack)) {
                    frame.setItem(stack);
                    migrated++;
                }
            }
        }
        return migrated;
    }

    private int migrateLegendaryItemsInInventory(Inventory inventory) {
        return migrateLegendaryItemsInInventory(inventory, 0);
    }

    private int migrateLegendaryItemsInInventory(Inventory inventory, int depth) {
        if (inventory == null || depth > LEGENDARY_ITEM_SCAN_MAX_DEPTH) {
            return 0;
        }

        int migrated = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!migrateLegendaryItemTree(item, depth)) continue;
            inventory.setItem(slot, item);
            migrated++;
        }
        return migrated;
    }

    private boolean migrateLegendaryItemTree(ItemStack item) {
        return migrateLegendaryItemTree(item, 0);
    }

    private boolean migrateLegendaryItemTree(ItemStack item, int depth) {
        if (item == null || item.getType() == Material.AIR || depth > LEGENDARY_ITEM_SCAN_MAX_DEPTH) {
            return false;
        }

        boolean changed = migrateLegendaryItem(item);
        changed |= migrateBackpackLegendaryItems(item, depth);
        changed |= migrateBundleLegendaryItems(item, depth);

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta blockStateMeta)) {
            return changed;
        }

        BlockState state = blockStateMeta.getBlockState();
        if (!(state instanceof InventoryHolder holder)) {
            return changed;
        }

        if (migrateLegendaryItemsInInventory(holder.getInventory(), depth + 1) <= 0) {
            return changed;
        }

        blockStateMeta.setBlockState(state);
        item.setItemMeta(blockStateMeta);
        return true;
    }

    private boolean migrateBackpackLegendaryItems(ItemStack item, int depth) {
        if (depth >= LEGENDARY_ITEM_SCAN_MAX_DEPTH || plugin.getBackpackListener() == null || !plugin.getBackpackListener().isBackpack(item)) {
            return false;
        }

        List<ItemStack> nested = plugin.getBackpackListener().auditContents(null, item);
        if (nested.isEmpty()) {
            return false;
        }

        boolean changed = false;
        ItemStack[] contents = nested.toArray(new ItemStack[0]);
        for (int slot = 0; slot < contents.length; slot++) {
            if (migrateLegendaryItemTree(contents[slot], depth + 1)) {
                changed = true;
            }
        }
        return changed && plugin.getBackpackListener().rewriteAuditContents(item, contents);
    }

    private boolean migrateBundleLegendaryItems(ItemStack item, int depth) {
        if (depth >= LEGENDARY_ITEM_SCAN_MAX_DEPTH) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BundleMeta bundleMeta)) {
            return false;
        }

        List<ItemStack> contents = new ArrayList<>(bundleMeta.getItems());
        boolean changed = false;
        for (int slot = 0; slot < contents.size(); slot++) {
            ItemStack nested = contents.get(slot);
            if (migrateLegendaryItemTree(nested, depth + 1)) {
                contents.set(slot, nested);
                changed = true;
            }
        }
        if (!changed) {
            return false;
        }

        bundleMeta.setItems(nonEmptyCopies(contents));
        item.setItemMeta(bundleMeta);
        return true;
    }

    private boolean migrateLegendaryItem(ItemStack item) {
        if (refreshEnderBonePresentation(item) || refreshOrbOfTheMysticsPresentation(item)) {
            return true;
        }

        LegendaryType type = typeOf(item);
        if (type == null) return false;
        lockDiscoveredExclusiveLegendary(type);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int version = pdc.getOrDefault(keyLegendaryVersion, PersistentDataType.INTEGER, 0);
        String instanceId = pdc.get(keyLegendaryInstance, PersistentDataType.STRING);
        if (instanceId == null || instanceId.isBlank()) {
            instanceId = UUID.randomUUID().toString();
        }

        if (version >= LEGENDARY_ITEM_DATA_VERSION
            && pdc.has(keyLegendaryInstance, PersistentDataType.STRING)) {
            return false;
        }

        applyLegendaryIdentity(meta, type, instanceId);
        applyLegendaryTypeState(meta, type);
        item.setItemMeta(meta);
        return true;
    }

    private void lockDiscoveredExclusiveLegendary(LegendaryType type) {
        if (!isExclusiveLegendaryType(type)) {
            return;
        }
        LegendaryAltarManager altarManager = plugin.getLegendaryAltarManager();
        if (altarManager != null) {
            altarManager.retireLegendaryFromCycle(type.id, null);
        }
    }

    private void refreshLegendaryPresentation(ItemStack item) {
        LegendaryType type = typeOf(item);
        if (type == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        applyLegendaryDurabilitySettings(meta, type);
        applyLegendaryTypeState(meta, type);
        item.setItemMeta(meta);
    }

    private void applyLegendaryIdentity(ItemMeta meta, LegendaryType type, String instanceId) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyLegendary, PersistentDataType.STRING, type.id);
        pdc.set(keyLegendaryVersion, PersistentDataType.INTEGER, LEGENDARY_ITEM_DATA_VERSION);
        pdc.set(keyLegendaryInstance, PersistentDataType.STRING, instanceId);
        meta.displayName(MM.deserialize(type.display));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        CustomLoreUtil.applyStyledItemFlags(meta);
        applyLegendaryDurabilitySettings(meta, type);
    }

    private void applyLegendaryDurabilitySettings(ItemMeta meta, LegendaryType type) {
        if (type == LegendaryType.EMERALD_BLADE || type == LegendaryType.STRENGTH_SWORD) {
            meta.setUnbreakable(false);
            meta.removeItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        } else {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }

        if (meta instanceof Damageable damageable) {
            if (type == LegendaryType.STRENGTH_SWORD) {
                damageable.setMaxDamage(STRENGTH_SWORD_MAX_DURABILITY);
            } else if (damageable.hasMaxDamage()) {
                damageable.setMaxDamage(null);
            }
        }
    }

    private void applyLegendaryTypeState(ItemMeta meta, LegendaryType type) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        meta.setItemModel(null);
        switch (type) {
            case ENDER_SWORD -> {
                setEnchantLevel(meta, enchantSharpness, 5);
                meta.lore(buildEnderSwordLore(meta));
            }
            case ENDERBOW -> {
                setEnchantLevel(meta, enchantPower, 7);
                boolean teleport = pdc.getOrDefault(keyEnderbowForm, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
                pdc.set(keyEnderbowForm, PersistentDataType.BYTE, teleport ? (byte) 1 : (byte) 0);
                meta.lore(buildEnderbowLore(meta, teleport));
            }
            case CHRONO_SWORD -> meta.lore(buildLegendaryLore(
                meta,
                Material.DIAMOND_SWORD,
                "SWORD",
                List.of("<gray>Cooldown: <white>45s</white> normal, <white>90s</white> on death rewind</gray>"),
                CustomLoreUtil.section(
                    "Item Ability",
                    "Time Rewind",
                    "<gray><white>Right-click</white> to mark your spot, then use it again after <yellow>7s</yellow> to rewind.</gray>",
                    "<gray>Lethal damage auto-rewinds and fully heals you.</gray>"
                )
            ));
            case HARPOON_LAUNCHER -> meta.lore(buildLegendaryLore(
                meta,
                Material.CROSSBOW,
                "LAUNCHER",
                List.of("<gray>Cooldown: <white>22s</white></gray>"),
                CustomLoreUtil.section(
                    "Item Ability",
                    "Harpoon Shot",
                    "<gray><white>Right-click</white> to fire a harpoon arrow.</gray>",
                    "<gray>Targets are pulled to you on hit.</gray>",
                    "<gray>Blocks pull you to the impact point.</gray>"
                )
            ));
            case HYPNOSIS_STAFF -> {
                meta.lore(buildLegendaryLore(
                    meta,
                    Material.BLAZE_ROD,
                    "STAFF",
                    List.of("<gray>Control Limit: <white>10</white> mobs</gray>", "<gray>Cooldown: <white>5s</white></gray>"),
                    CustomLoreUtil.section(
                        "Item Ability",
                        "Mind Control",
                        "<gray><white>Right-click</white> the first mob you hit to control it.</gray>",
                        "<gray>Use it again on a controlled mob to heal <white>10 hearts</white>.</gray>"
                    )
                ));
            }
            case EMERALD_BLADE -> {
                int level = Math.max(1, Math.min(
                    EMERALD_BLADE_MAX_LEVEL,
                    pdc.getOrDefault(keyEmeraldLevel, PersistentDataType.INTEGER, 1)
                ));
                pdc.set(keyEmeraldLevel, PersistentDataType.INTEGER, level);
                setEnchantLevel(meta, enchantSharpness, level);
                setEnchantLevel(meta, enchantUnbreaking, 3);
                meta.lore(buildEmeraldBladeLore(meta, level));
            }
            case BLINK_DAGGER -> {
                meta.lore(buildBlinkDaggerLore(meta));
            }
            case WARDEN_BLADE -> meta.lore(buildWardenBladeLore(meta));
            case FROST_SCYTHE -> {
                meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
                meta.lore(buildFrostScytheLore(meta));
            }
            case DIVINE_AXE_RHITTA -> meta.lore(buildRhittaLore(meta));
            case TRIDENT_OF_PERCY -> applyPercyTridentState(meta);
            case WAR_PICK -> applyWarPickState(meta);
            case FARADAYS_MAGNET -> {
                boolean active = pdc.getOrDefault(keyMagnetActive, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
                pdc.set(keyMagnetActive, PersistentDataType.BYTE, active ? (byte) 1 : (byte) 0);
                pdc.remove(new NamespacedKey(plugin, "faraday_uses"));
                meta.lore(buildMagnetLore(meta, active));
            }
            case WIND_CHARGE_CANNON -> {
                int charges = clampWindChargeCannonCharges(
                    pdc.getOrDefault(keyWindCannonCharges, PersistentDataType.INTEGER, WIND_CHARGE_CANNON_MAX_CHARGES)
                );
                long cooldownUntil = pdc.getOrDefault(keyWindCannonCooldownUntil, PersistentDataType.LONG, 0L);
                if (cooldownUntil > 0L && cooldownUntil <= System.currentTimeMillis()) {
                    charges = WIND_CHARGE_CANNON_MAX_CHARGES;
                    cooldownUntil = 0L;
                } else if (cooldownUntil > System.currentTimeMillis()) {
                    charges = 0;
                }
                writeWindChargeCannonState(meta, charges, cooldownUntil);
            }
            case MIDAS_SWORD -> {
                int sharpness = midasSharpness(meta);
                pdc.set(keyMidasSharpness, PersistentDataType.INTEGER, sharpness);
                setEnchantLevel(meta, enchantSharpness, sharpness);
                meta.lore(buildMidasSwordLore(meta, sharpness));
            }
            case REAPERS_SCYTHE -> {
                meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
                meta.lore(buildReaperScytheLore(meta));
            }
            case SHADOW_BLADE -> {
                meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
                meta.lore(buildShadowBladeLore(meta, null));
            }
            case PARADOX_REAVER -> {
                setEnchantLevel(meta, enchantSharpness, 8);
                setEnchantLevel(meta, enchantUnbreaking, 4);
                meta.lore(buildMythicLore(
                    meta,
                    Material.NETHERITE_SWORD,
                    "SWORD",
                    List.of(
                        "<gray>Hit Damage: <white>14</white></gray>",
                        "<gray>Temporal Break: <white>Slowness II</white> and <white>Weakness I</white></gray>"
                    ),
                    CustomLoreUtil.section(
                        "Mythic Fusion",
                        "Paradox Cut",
                        "<gray>Forged from <white>Riftreaver</white> and <white>Hourglass Blade</white>.</gray>",
                        "<gray>Hits tear a brief delay into the target, reducing escape and counterpressure.</gray>"
                    )
                ));
            }
            case TEMPEST_TRIDENT -> {
                setEnchantLevel(meta, enchantLoyalty, 4);
                setEnchantLevel(meta, enchantChanneling, 1);
                setEnchantLevel(meta, enchantImpaling, 6);
                meta.lore(buildMythicLore(
                    meta,
                    Material.TRIDENT,
                    "TRIDENT",
                    List.of(
                        "<gray>Hit Bonus: <white>+7 damage</white></gray>",
                        "<gray>Storm Surge: stronger in water or rain</gray>"
                    ),
                    CustomLoreUtil.section(
                        "Mythic Fusion",
                        "Stormtide",
                        "<gray>Forged from <white>Frost Scythe</white> and <white>Trident of Percy</white>.</gray>",
                        "<gray>Rewards water, rain, and boss arenas without deleting PvP counterplay.</gray>"
                    )
                ));
            }
            case STORMFALL_MAUL -> {
                setEnchantLevel(meta, enchantDensity, 6);
                setEnchantLevel(meta, enchantBreach, 4);
                setEnchantLevel(meta, enchantWindBurst, 2);
                setEnchantLevel(meta, enchantUnbreaking, 4);
                meta.lore(buildMythicLore(
                    meta,
                    Material.MACE,
                    "MACE",
                    List.of(
                        "<gray>Hit Bonus: <white>+6 damage</white></gray>",
                        "<gray>Shockwave: knocks enemies back on hit</gray>"
                    ),
                    CustomLoreUtil.section(
                        "Mythic Fusion",
                        "Stormfall",
                        "<gray>Forged from <white>Mjolnir</white> and <white>Meteorbreaker</white>.</gray>",
                        "<gray>A high-risk brawler weapon built for breaking clustered fights.</gray>"
                    )
                ));
            }
            case HEADHUNTERS_CHESTPIECE -> {
                setEnchantLevel(meta, enchantProtection, 3);
                meta.lore(buildHeadhunterChestpieceLore(meta));
            }
            case GOD_CHESTPLATE -> {
                for (Enchantment enchantment : new HashSet<>(meta.getEnchants().keySet())) {
                    meta.removeEnchant(enchantment);
                }
                meta.lore(buildGodChestplateLore(meta));
            }
            case STRENGTH_SWORD -> {
                setEnchantLevel(meta, enchantUnbreaking, 3);
                setEnchantLevel(meta, enchantSharpness, 7);
                setEnchantLevel(meta, enchantFireAspect, 2);
                setEnchantLevel(meta, enchantLooting, 4);
                meta.removeEnchant(enchantMending);
                meta.lore(buildStrengthSwordLore(meta, strengthSwordVictimCount(meta)));
            }
            case EXECUTIONER_BLADE -> {
                meta.lore(buildLegendaryLore(
                    meta,
                    Material.NETHERITE_SWORD,
                    "SWORD",
                    List.of("<gray>Current Bonus: <red>+1 damage</red> per skull carried <dark_gray>(cap +6)</dark_gray></gray>"),
                    CustomLoreUtil.section(
                        "Item Ability",
                        "Fury",
                        "<gray><white>Right-click</white> to gain <white>Strength II</white> for <white>6 minutes</white>.</gray>",
                        "<gray>Cooldown: <white>" + EXECUTIONER_BLADE_STRENGTH_COOLDOWN + "s</white></gray>"
                    ),
                    CustomLoreUtil.section(
                        "Item Ability",
                        "Executioner Shockwave",
                        "<gray><white>Shift + Right-click</white> to launch nearby enemy players upward and stun them for <white>3s</white>.</gray>",
                        "<gray>Cooldown: <white>" + EXECUTIONER_BLADE_SHOCKWAVE_COOLDOWN + "s</white></gray>",
                        "<gray>Teammates are ignored by the shockwave.</gray>"
                    )
                ));
            }
            case HERMES_BOOTS -> {
                boolean awakened = plugin.getAwakeningTableListener() != null
                    && plugin.getAwakeningTableListener().isAwakened(meta);
                double speedScalar = awakened ? (HERMES_BOOTS_SPEED_SCALAR * 2.0) : HERMES_BOOTS_SPEED_SCALAR;
                meta.removeAttributeModifier(Attribute.MOVEMENT_SPEED);
                meta.addAttributeModifier(
                    Attribute.MOVEMENT_SPEED,
                    new AttributeModifier(
                        keyHermesBootsSpeedModifier,
                        speedScalar,
                        AttributeModifier.Operation.ADD_SCALAR,
                        EquipmentSlotGroup.FEET
                    )
                );
                meta.lore(buildLegendaryLore(
                    meta,
                    Material.DIAMOND_BOOTS,
                    "BOOTS",
                    List.of(
                        "<gray>Only works while worn</gray>",
                        "<gray>Speed Bonus: <white>" + Math.round(speedScalar * 100.0) + "%</white></gray>"
                    ),
                    CustomLoreUtil.section(
                        "Passive",
                        "Hermes Step",
                        "<gray>Negates all fall damage.</gray>",
                        awakened
                            ? "<gray>Awakened Hermes flow doubles the movement boost.</gray>"
                            : "<gray>Greatly increases movement speed.</gray>"
                    )
                ));
            }
            case WITHER_BLADE -> {
                WitherBladeState state = refreshWitherBladeState(meta);
                applyWitherBladeState(
                    meta,
                    state.skullCharges(),
                    state.skullRechargeStartedAt(),
                    state.dashCharges(),
                    state.dashRechargeStartedAt()
                );
            }
            case LIFE_STEALER -> {
                applyLifeStealerState(meta, refreshLifeStealerState(meta));
            }
            case THORS_HAMMER -> meta.lore(buildLegendaryLore(
                meta,
                Material.MACE,
                "MACE",
                List.of(
                    "<gray>Bonus Hit Damage: <white>+" + formatDamageNumber(plugin.getConfigManager().thorsHammerBonusDamage) + "</white></gray>",
                    "<gray>Thunder Strike: <white>"
                        + formatDamageNumber(plugin.getConfigManager().thorsHammerTrueDamage / 2.0)
                        + " hearts true damage</white></gray>",
                    "<gray>Thunder Strike Cooldown: <white>"
                        + plugin.getConfigManager().thorsHammerTrueDamageCooldownSeconds
                        + "s</white></gray>"
                ),
                CustomLoreUtil.section(
                    "Item Ability",
                    "Thunder Strike",
                    "<gray>Every hit calls lightning on the target.</gray>",
                    "<gray>When off cooldown, the lightning also deals armor-piercing true damage.</gray>",
                    "<gray>Creative, spectator, invulnerable, and teammate targets are ignored.</gray>"
                )
            ));
            case DASH_MACE -> meta.lore(buildLegendaryLore(
                meta,
                Material.MACE,
                "MACE",
                List.of(
                    "<gray>Dash Cooldown: <white>" + DASH_MACE_COOLDOWN + "s</white></gray>",
                    "<gray>Unbreakable</gray>"
                ),
                CustomLoreUtil.section(
                    "Item Ability",
                    "Meteor Dash",
                    "<gray><white>Right-click</white> to rocket in the direction you are facing.</gray>",
                    "<gray>Launches harder than the Dash enchant but has its own cooldown.</gray>"
                )
            ));
            case STRENGTH_MACE -> meta.lore(buildLegendaryLore(
                meta,
                Material.MACE,
                "MACE",
                List.of(
                    "<gray>Smash Bonus: <red>+" + formatDamageNumber(STRENGTH_MACE_DAMAGE_BONUS) + "</red> damage</gray>",
                    "<gray>Unbreakable</gray>"
                ),
                CustomLoreUtil.section(
                    "Passive",
                    "Titan Force",
                    "<gray>Every hit lands with the bonus damage of <white>Strength III</white>.</gray>",
                    "<gray>This stacks with normal mace mechanics.</gray>"
                )
            ));
            case HARD_HITTER -> {
                setEnchantLevel(meta, enchantDensity, 5);
                setEnchantLevel(meta, enchantBreach, 4);
                setEnchantLevel(meta, enchantWindBurst, 1);
                meta.lore(buildLegendaryLore(
                    meta,
                    Material.MACE,
                    "MACE",
                    List.of("<gray>Unbreakable</gray>"),
                    CustomLoreUtil.section(
                        "Item Ability",
                        "Heavy Impact",
                        "<gray>Comes preloaded with <white>Density V</white>, <white>Breach IV</white>, and <white>Wind Burst I</white>.</gray>",
                        "<gray>No extra plugin effect is added on top of the mace enchantments.</gray>"
                    )
                ));
            }
        }
        if (plugin.getCustomEnchantListener() != null) {
            plugin.getCustomEnchantListener().applyManagedEnchantLore(meta);
        }
        AwakeningTableListener awakening = plugin.getAwakeningTableListener();
        if (awakening != null) {
            awakening.applyManagedItemState(meta, type.material, MM.deserialize(type.display), type != LegendaryType.EMERALD_BLADE);
        }
    }

    private void setEnchantLevel(ItemMeta meta, Enchantment enchantment, int level) {
        int currentLevel = meta.getEnchantLevel(enchantment);
        if ((level <= 0 && currentLevel <= 0) || currentLevel == level) return;
        meta.removeEnchant(enchantment);
        if (level > 0) {
            meta.addEnchant(enchantment, level, true);
        }
    }

    private int countPlayerInventoryMaterial(Player player, Material material) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != material || item.getAmount() <= 0) continue;
            total += item.getAmount();
        }
        return total;
    }

    private int countPlayerInventorySkulls(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !isSkullMaterial(item.getType()) || item.getAmount() <= 0) continue;
            total += item.getAmount();
        }
        return total;
    }

    private int witherBladeSkullCount(Player player) {
        return countPlayerInventoryMaterial(player, Material.WITHER_SKELETON_SKULL);
    }

    private double witherBladeBonusDamage(int skullCount) {
        return Math.min(WITHER_BLADE_SKULL_DAMAGE_CAP, skullCount);
    }

    private boolean isSkullMaterial(Material material) {
        return switch (material) {
            case SKELETON_SKULL, WITHER_SKELETON_SKULL, PLAYER_HEAD, ZOMBIE_HEAD,
                CREEPER_HEAD, DRAGON_HEAD, PIGLIN_HEAD -> true;
            default -> false;
        };
    }

    private int emeraldLevel(ItemStack blade) {
        ItemMeta meta = blade.getItemMeta();
        if (meta == null) return 1;
        Integer lvl = meta.getPersistentDataContainer().get(keyEmeraldLevel, PersistentDataType.INTEGER);
        return lvl == null || lvl < 1 ? 1 : lvl;
    }

    private void setEmeraldLevel(ItemStack blade, int level) {
        int normalized = Math.max(1, Math.min(EMERALD_BLADE_MAX_LEVEL, level));
        ItemMeta meta = blade.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(keyEmeraldLevel, PersistentDataType.INTEGER, normalized);
        meta.removeEnchant(enchantSharpness);
        meta.addEnchant(enchantSharpness, normalized, true);
        meta.lore(buildEmeraldBladeLore(meta, normalized));
        blade.setItemMeta(meta);
    }

    private boolean takeEmeraldBlock(Player player) {
        Map<Integer, ? extends ItemStack> leftovers = player.getInventory().removeItem(new ItemStack(Material.EMERALD_BLOCK, 1));
        return leftovers.isEmpty();
    }

    private List<LegendaryRecipe> buildRecipes() {
        return List.of(
            new LegendaryRecipe(LegendaryType.ENDER_SWORD, ingredients(
                e(Material.BONE, 8), e(Material.DRAGON_EGG, 1), e(Material.DIAMOND_SWORD, 1))),
            new LegendaryRecipe(LegendaryType.ENDERBOW, ingredients(
                e(Material.BOW, 1), e(Material.ENDER_EYE, 24), e(Material.ENDER_PEARL, 12), e(Material.DIAMOND, 1))),
            new LegendaryRecipe(LegendaryType.CHRONO_SWORD, ingredients(
                e(Material.ENDER_PEARL, 12), e(Material.DIAMOND, 20), e(materialByName("CHAIN", Material.IRON_BARS), 12),
                e(Material.SOUL_LANTERN, 2), e(Material.CLOCK, 3), e(Material.DIAMOND_SWORD, 1))),
            new LegendaryRecipe(LegendaryType.HARPOON_LAUNCHER, ingredients(
                e(Material.LEAD, 6), e(Material.REDSTONE_BLOCK, 8), e(Material.FISHING_ROD, 1), e(Material.TRIDENT, 1))),
            new LegendaryRecipe(LegendaryType.HYPNOSIS_STAFF, ingredients(
                e(Material.TOTEM_OF_UNDYING, 1), e(Material.BLAZE_ROD, 24), e(Material.REDSTONE_BLOCK, 12))),
            new LegendaryRecipe(LegendaryType.EMERALD_BLADE, ingredients(
                e(Material.GOLDEN_CARROT, 32), e(Material.EMERALD_BLOCK, 32), e(Material.BELL, 1), e(Material.DIAMOND_SWORD, 1))),
            new LegendaryRecipe(LegendaryType.BLINK_DAGGER, ingredients(
                e(Material.IRON_SWORD, 1), e(Material.ENDER_PEARL, 16), e(Material.CHORUS_FRUIT, 8),
                e(Material.ECHO_SHARD, 4), e(Material.BREEZE_ROD, 2), e(Material.PHANTOM_MEMBRANE, 4), e(Material.DIAMOND, 12))),
            new LegendaryRecipe(LegendaryType.WARDEN_BLADE, ingredients(
                e(Material.HEART_OF_THE_SEA, 1), e(Material.ECHO_SHARD, 12), e(Material.NETHERITE_SWORD, 1),
                e(Material.NETHERITE_INGOT, 4), e(Material.SCULK_SHRIEKER, 2))),
            new LegendaryRecipe(LegendaryType.FROST_SCYTHE, ingredients(
                e(Material.DIAMOND_HOE, 1), e(Material.BLUE_ICE, 24), e(Material.PACKED_ICE, 24),
                e(Material.POWDER_SNOW_BUCKET, 4), e(Material.AMETHYST_SHARD, 16), e(Material.BREEZE_ROD, 2))),
            new LegendaryRecipe(LegendaryType.DIVINE_AXE_RHITTA, ingredients(
                e(Material.NETHERITE_AXE, 1), e(Material.NETHERITE_INGOT, 4), e(Material.BLAZE_ROD, 8),
                e(Material.MAGMA_CREAM, 16), e(Material.FIRE_CHARGE, 8), e(Material.NETHER_STAR, 1))),
            new LegendaryRecipe(LegendaryType.TRIDENT_OF_PERCY, ingredients(
                e(Material.TRIDENT, 1), e(Material.HEART_OF_THE_SEA, 1), e(Material.CONDUIT, 1),
                e(Material.NAUTILUS_SHELL, 8), e(Material.PRISMARINE_CRYSTALS, 32), e(Material.PRISMARINE_SHARD, 32),
                e(Material.NETHERITE_INGOT, 4), e(Material.NETHER_STAR, 1))),
            new LegendaryRecipe(LegendaryType.HEADHUNTERS_CHESTPIECE, ingredients(
                e(Material.PIGLIN_HEAD, 1), e(Material.WEEPING_VINES, 4), e(Material.IRON_BLOCK, 2), e(Material.NETHERITE_SCRAP, 1), e(Material.DIAMOND_CHESTPLATE, 1))),
            new LegendaryRecipe(LegendaryType.GOD_CHESTPLATE, ingredients(
                e(Material.DIAMOND_CHESTPLATE, 1), e(Material.TOTEM_OF_UNDYING, 1), e(Material.ENCHANTED_GOLDEN_APPLE, 1), e(Material.DIAMOND_BLOCK, 2))),
            new LegendaryRecipe(LegendaryType.WAR_PICK, ingredients(
                e(Material.REDSTONE_BLOCK, 32), e(Material.TNT, 32), e(Material.DIAMOND_PICKAXE, 1), e(Material.CRYING_OBSIDIAN, 16))),
            new LegendaryRecipe(LegendaryType.WIND_CHARGE_CANNON, ingredients(
                e(Material.PRISMARINE_SHARD, 24), e(Material.WIND_CHARGE, 24), e(Material.COPPER_BLOCK, 6), e(Material.DISPENSER, 1))),
            new LegendaryRecipe(LegendaryType.EXECUTIONER_BLADE, ingredients(
                e(Material.NETHERITE_SWORD, 1), e(Material.BLAZE_ROD, 12), e(Material.ANVIL, 1), e(Material.WIND_CHARGE, 6), e(Material.REDSTONE_BLOCK, 12))),
            new LegendaryRecipe(LegendaryType.HERMES_BOOTS, ingredients(
                e(Material.DIAMOND_BOOTS, 1), e(Material.FEATHER, 24), e(Material.GOLD_BLOCK, 6), e(Material.RABBIT_FOOT, 4))),
            new LegendaryRecipe(LegendaryType.WITHER_BLADE, ingredients(
                e(Material.NETHERITE_SWORD, 1), e(Material.WITHER_SKELETON_SKULL, 2), e(Material.NETHER_STAR, 1), e(Material.SOUL_SAND, 24))),
            new LegendaryRecipe(LegendaryType.LIFE_STEALER, ingredients(
                e(Material.NETHERITE_SWORD, 1), e(Material.GHAST_TEAR, 12), e(Material.FERMENTED_SPIDER_EYE, 8), e(Material.REDSTONE_BLOCK, 8), e(Material.GOLDEN_APPLE, 4))),
            new LegendaryRecipe(LegendaryType.THORS_HAMMER, ingredients(
                e(Material.MACE, 1), e(Material.LIGHTNING_ROD, 12), e(Material.NETHERITE_INGOT, 2), e(Material.WIND_CHARGE, 12))),
            new LegendaryRecipe(LegendaryType.DASH_MACE, ingredients(
                e(Material.MACE, 1), e(Material.BREEZE_ROD, 4), e(Material.WIND_CHARGE, 16), e(Material.FEATHER, 16), e(Material.DIAMOND, 16))),
            new LegendaryRecipe(LegendaryType.STRENGTH_MACE, ingredients(
                e(Material.MACE, 1), e(Material.BLAZE_POWDER, 16), e(Material.NETHERITE_INGOT, 2), e(Material.DIAMOND_BLOCK, 2), e(Material.GOLDEN_APPLE, 8))),
            new LegendaryRecipe(LegendaryType.HARD_HITTER, ingredients(
                e(Material.BREEZE_ROD, 1), e(Material.HEAVY_CORE, 1), e(Material.IRON_INGOT, 3), e(Material.DIAMOND, 32)))
        );
    }

    private void registerRecipeBookRecipes() {
        recipeBookKeys.clear();
        for (LegendaryRecipe recipe : recipes) {
            NamespacedKey key = new NamespacedKey(plugin, "legendary_" + recipe.type().id);
            Bukkit.removeRecipe(key);
        }
    }

    private void registerNormalCraftingRecipes() {
        Bukkit.removeRecipe(faradaysMagnetRecipeKey);

        ShapedRecipe recipe = new ShapedRecipe(faradaysMagnetRecipeKey, createFaradaysMagnetItem());
        recipe.shape("IRI", "CMC", "INI");
        recipe.setIngredient('I', Material.IRON_BLOCK);
        recipe.setIngredient('R', Material.REDSTONE_BLOCK);
        recipe.setIngredient('C', Material.COPPER_BLOCK);
        recipe.setIngredient('M', Material.RECOVERY_COMPASS);
        recipe.setIngredient('N', Material.NETHERITE_INGOT);
        Bukkit.addRecipe(recipe);
    }

    private void discoverNormalCraftingRecipes(Player player) {
        if (player == null) {
            return;
        }
        player.discoverRecipe(faradaysMagnetRecipeKey);
    }

    private void discoverLegendaryRecipes(Player player) {
        // custom items are traded through the Reliquary instead of the vanilla recipe book
    }

    private boolean isLegendaryRecipe(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) return false;
        return recipeBookKeys.contains(keyed.getKey());
    }

    private Map<Material, Integer> count(ItemStack[] matrix) {
        Map<Material, Integer> out = new EnumMap<>(Material.class);
        for (ItemStack item : matrix) {
            if (item == null || item.getType() == Material.AIR) continue;
            out.merge(item.getType(), item.getAmount(), Integer::sum);
        }
        return out;
    }

    private LegendaryRecipe findRecipe(ItemStack[] matrix) {
        if (containsLegendaryIngredient(matrix)) return null;
        return findRecipe(count(matrix));
    }

    private LegendaryRecipe findRecipe(Map<Material, Integer> provided) {
        for (LegendaryRecipe recipe : recipes) {
            if (match(provided, recipe.ingredients)) return recipe;
        }
        return null;
    }

    private boolean containsLegendaryIngredient(ItemStack[] matrix) {
        for (ItemStack item : matrix) {
            if (item == null || item.getType() == Material.AIR) continue;
            if (isLegendaryIngredient(item)) return true;
        }
        return false;
    }

    private boolean usesOnlyPlainNormalRecipeIngredients(ItemStack[] matrix) {
        if (matrix == null) {
            return false;
        }
        for (ItemStack item : matrix) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (!isPlainLegendaryRecipeMaterial(item, item.getType())) {
                return false;
            }
        }
        return true;
    }

    private boolean isLegendaryIngredient(ItemStack item) {
        if (typeOf(item) != null) return true;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;

        String actualName = PLAIN.serialize(meta.displayName()).trim();
        if (actualName.isEmpty()) return false;

        for (LegendaryType type : LegendaryType.values()) {
            if (item.getType() != type.material) continue;
            String expectedName = PLAIN.serialize(MM.deserialize(type.display)).trim();
            if (actualName.equals(expectedName)) {
                return true;
            }
        }
        return false;
    }

    private Map<Material, Integer> ingredientsFor(LegendaryType type) {
        for (LegendaryRecipe recipe : recipes) {
            if (recipe.type == type) {
                return recipe.ingredients;
            }
        }
        return Map.of();
    }

    private boolean match(Map<Material, Integer> provided, Map<Material, Integer> required) {
        if (provided.size() != required.size()) return false;
        for (Map.Entry<Material, Integer> need : required.entrySet()) {
            if (!need.getValue().equals(provided.get(need.getKey()))) return false;
        }
        return true;
    }

    private void clearCustomCraftState(CraftingInventory inv) {
        inv.setResult(null);
    }

    private boolean handleLegendaryCraftClick(InventoryClickEvent event, Player player) {
        if (!(event.getView().getTopInventory() instanceof CraftingInventory inv)) return false;
        if (!isCraftResultSlot(event)) return false;

        LegendaryType out = typeOf(event.getCurrentItem());
        LegendaryRecipe recipe = findRecipe(inv.getMatrix());
        if (out != null && !isExclusiveLegendaryType(out) && recipe == null) {
            return false;
        }
        if (out == null && recipe == null) return false;

        event.setCancelled(true);
        clearCustomCraftState(inv);
        player.updateInventory();
        player.sendMessage(MessageUtil.info(
                "Use <white>/reliquary</white> to view custom recipes. Legendary items are crafted at the altar."
            ));
        return true;
    }

    private boolean isCraftResultSlot(InventoryClickEvent event) {
        return event.getView().getTopInventory() instanceof CraftingInventory
            && (event.getClickedInventory() == event.getView().getTopInventory() || event.getRawSlot() == 0)
            && (event.getSlotType() == InventoryType.SlotType.RESULT || event.getRawSlot() == 0);
    }

    @SafeVarargs
    private static Map<Material, Integer> ingredients(Map.Entry<Material, Integer>... entries) {
        Map<Material, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<Material, Integer> entry : entries) out.put(entry.getKey(), entry.getValue());
        return out;
    }

    private static Map.Entry<Material, Integer> e(Material material, int amount) {
        return Map.entry(material, amount);
    }

    private static boolean isProtected(Material material) {
        return switch (material) {
            case BEDROCK, BARRIER, COMMAND_BLOCK, CHAIN_COMMAND_BLOCK, REPEATING_COMMAND_BLOCK,
                 STRUCTURE_BLOCK, JIGSAW, END_PORTAL_FRAME, END_PORTAL, END_GATEWAY, LIGHT -> true;
            default -> false;
        };
    }

    private static Material materialByName(String name, Material fallback) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private Enchantment requireEnchantment(String key) {
        Enchantment enchantment = RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.ENCHANTMENT)
            .get(NamespacedKey.minecraft(key));
        if (enchantment == null) {
            throw new IllegalStateException("Missing enchantment: " + key);
        }
        return enchantment;
    }

    private Player resolvePlayerAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        if (damager instanceof Tameable tameable && tameable.getOwner() instanceof Player player) {
            return player;
        }
        return null;
    }

    private boolean isPercyTridentProjectile(Trident trident) {
        return typeOf(trident.getItem()) == LegendaryType.TRIDENT_OF_PERCY
            || typeOf(trident.getItemStack()) == LegendaryType.TRIDENT_OF_PERCY
            || typeOf(trident.getWeapon()) == LegendaryType.TRIDENT_OF_PERCY;
    }

    private enum WarPickAxis {
        X,
        Y,
        Z
    }

    private record WardenBladeSoundWaveHit(LivingEntity target, double distance, Location impact) {
    }

    private enum WarPickMode {
        TWO_BY_TWO_BY_ONE("2x2x1", 2, true),
        THREE_BY_THREE_BY_ONE("3x3x1", 3, true),
        THREE_BY_THREE_BY_THREE("3x3x3", 3, false),
        FIVE_BY_FIVE_BY_FIVE("5x5x5", 5, false),
        NINE_BY_NINE_BY_NINE("9x9x9", 9, false);

        private final String label;
        private final int size;
        private final boolean planar;

        WarPickMode(String label, int size, boolean planar) {
            this.label = label;
            this.size = size;
            this.planar = planar;
        }

        private static WarPickMode defaultMode() {
            return THREE_BY_THREE_BY_THREE;
        }

        private static WarPickMode fromOrdinal(int ordinal) {
            WarPickMode[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : defaultMode();
        }

        private String label() {
            return label;
        }

        private int size() {
            return size;
        }

        private boolean planar() {
            return planar;
        }

        private int blockCount() {
            return planar ? size * size : size * size * size;
        }

        private WarPickMode next() {
            WarPickMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private enum ReliquarySection {
        LEGENDARIES(
            "legendaries",
            "Reliquary: Legendaries",
            Material.NETHER_STAR,
            "<gradient:#ffd56a:#ff6f3c><bold>Legendary Relics</bold></gradient>",
            List.of(
                "<gray>Altar-crafted weapons, armor, and rare combat relics.</gray>",
                "<gray>These obey the one-per-server legendary rules.</gray>"
            )
        ),
        MYTHICS(
            "mythics",
            "Reliquary: Mythics",
            Material.END_CRYSTAL,
            "<gradient:#ff4df0:#ffb000><bold>Mythic Works</bold></gradient>",
            List.of(
                "<gray>Forge stations, catalysts, and Mythic Nexus fusions.</gray>",
                "<gray>This is where the endgame upgrades live.</gray>"
            )
        ),
        TOOLS(
            "tools",
            "Reliquary: Tools",
            Material.NETHERITE_PICKAXE,
            "<gradient:#72f7ff:#4dff88><bold>Tools & Gear</bold></gradient>",
            List.of(
                "<gray>Craftable utility gear, backpacks, magnets, and custom tools.</gray>",
                "<gray>Mostly normal crafting table recipes.</gray>"
            )
        ),
        UTILITY(
            "utility",
            "Reliquary: Utility",
            Material.AMETHYST_SHARD,
            "<gradient:#c77dff:#ff7ad9><bold>Utility Relics</bold></gradient>",
            List.of(
                "<gray>Support items that change powers, survival, or progression.</gray>",
                "<gray>Good place to check non-weapon custom recipes.</gray>"
            )
        ),
        SEASON(
            "season",
            "Reliquary: Covenant",
            Material.ECHO_SHARD,
            "<gradient:#ff4d6d:#facc15><bold>Covenant Armory</bold></gradient>",
            List.of(
                "<gray>Boss trophies, weapons, armor sets,</gray>",
                "<gray>standalone armor, and tactical utility relics.</gray>"
            )
        );

        private final String id;
        private final String plainTitle;
        private final Material icon;
        private final String title;
        private final List<String> lore;

        ReliquarySection(String id, String plainTitle, Material icon, String title, List<String> lore) {
            this.id = id;
            this.plainTitle = plainTitle;
            this.icon = icon;
            this.title = title;
            this.lore = lore;
        }

        private static ReliquarySection fromId(String id) {
            for (ReliquarySection section : values()) {
                if (section.id.equals(id)) {
                    return section;
                }
            }
            return null;
        }
    }

    private record ReliquaryMenuHolder(ReliquarySection section) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record RecipeMenuHolder(LegendaryType type) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record AncientScrollRecipeHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record AscendantCoreRecipeHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record BackpackRecipeHolder(String recipeId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record TalismanRecipeHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record SalvagingDepotRecipeHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record AgriculturalPylonRecipeHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record XpLecternRecipeHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record AwakeningTableInfoHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record FaradaysMagnetRecipeHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record MythicForgeRecipeHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record MythicFusionMenuHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record MythicFusionRecipeHolder(String outputId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record CustomToolRecipeHolder(String toolId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record CustomRecipeEntry(String id, ItemStack icon) {
    }

    private enum LegendaryType {
        ENDER_SWORD("ender_sword", Material.NETHERITE_SWORD, "Riftreaver", CustomLoreUtil.Rarity.MYTHIC),
        ENDERBOW("enderbow", Material.BOW, "Riftbow", CustomLoreUtil.Rarity.EPIC),
        CHRONO_SWORD("chrono_sword", Material.DIAMOND_SWORD, "Hourglass Blade", CustomLoreUtil.Rarity.MYTHIC),
        HARPOON_LAUNCHER("harpoon_launcher", Material.CROSSBOW, "Leviathan Launcher", CustomLoreUtil.Rarity.EPIC),
        HYPNOSIS_STAFF("hypnosis_staff", Material.BLAZE_ROD, "Siren's Cane", CustomLoreUtil.Rarity.LEGENDARY),
        EMERALD_BLADE("emerald_blade", Material.DIAMOND_SWORD, "Verdant Fang", CustomLoreUtil.Rarity.LEGENDARY),
        BLINK_DAGGER("blink_dagger", Material.IRON_SWORD, "Blink Dagger", CustomLoreUtil.Rarity.EPIC),
        WARDEN_BLADE("warden_blade", Material.NETHERITE_SWORD, "Warden Blade", CustomLoreUtil.Rarity.MYTHIC),
        TRIDENT_OF_PERCY("trident_of_percy", Material.TRIDENT, "Trident of Percy", CustomLoreUtil.Rarity.LEGENDARY),
        WAR_PICK("war_pick", Material.DIAMOND_PICKAXE, "Siegebreaker Pick", CustomLoreUtil.Rarity.LEGENDARY),
        FARADAYS_MAGNET("faradays_magnet", Material.RECOVERY_COMPASS, "Faraday's Magnet", CustomLoreUtil.Rarity.RARE),
        WIND_CHARGE_CANNON("wind_charge_cannon", Material.PRISMARINE_SHARD, "Tempest Cannon", CustomLoreUtil.Rarity.EPIC),
        FROST_SCYTHE("frost_scythe", Material.DIAMOND_HOE, "Frost Scythe", CustomLoreUtil.Rarity.LEGENDARY),
        DIVINE_AXE_RHITTA("divine_axe_rhitta", Material.NETHERITE_AXE, "Divine Axe Rhitta", CustomLoreUtil.Rarity.LEGENDARY),
        MIDAS_SWORD("midas_sword", Material.GOLDEN_SWORD, "Gilded Sovereign", CustomLoreUtil.Rarity.MYTHIC),
        REAPERS_SCYTHE("reapers_scythe", Material.NETHERITE_HOE, "Soulrender", CustomLoreUtil.Rarity.MYTHIC),
        SHADOW_BLADE("shadow_blade", Material.DIAMOND_SWORD, "Nightfall", CustomLoreUtil.Rarity.MYTHIC),
        PARADOX_REAVER("paradox_reaver", Material.NETHERITE_SWORD, "Paradox Reaver", CustomLoreUtil.Rarity.MYTHIC),
        TEMPEST_TRIDENT("tempest_trident", Material.TRIDENT, "Tempest Trident", CustomLoreUtil.Rarity.MYTHIC),
        STORMFALL_MAUL("stormfall_maul", Material.MACE, "Stormfall Maul", CustomLoreUtil.Rarity.MYTHIC),
        HEADHUNTERS_CHESTPIECE("headhunters_chestpiece", Material.DIAMOND_CHESTPLATE, "Headhunter's Harness", CustomLoreUtil.Rarity.LEGENDARY),
        GOD_CHESTPLATE("god_chestplate", Material.DIAMOND_CHESTPLATE, "Aegis of the Undying", CustomLoreUtil.Rarity.MYTHIC),
        STRENGTH_SWORD("strength_sword", Material.NETHERITE_SWORD, "Crimson Dominion", CustomLoreUtil.Rarity.MYTHIC),
        EXECUTIONER_BLADE("executioner_blade", Material.NETHERITE_SWORD, "Executioner Blade", CustomLoreUtil.Rarity.LEGENDARY),
        HERMES_BOOTS("hermes_boots", Material.DIAMOND_BOOTS, "Hermes Boots", CustomLoreUtil.Rarity.LEGENDARY),
        WITHER_BLADE("wither_blade", Material.NETHERITE_SWORD, "Wither Blade", CustomLoreUtil.Rarity.LEGENDARY),
        LIFE_STEALER("life_stealer", Material.NETHERITE_SWORD, "Life Stealer", CustomLoreUtil.Rarity.MYTHIC),
        THORS_HAMMER("thors_hammer", Material.MACE, "Mjolnir", CustomLoreUtil.Rarity.LEGENDARY),
        DASH_MACE("dash_mace", Material.MACE, "Meteorbreaker", CustomLoreUtil.Rarity.LEGENDARY),
        STRENGTH_MACE("strength_mace", Material.MACE, "Colossus Maul", CustomLoreUtil.Rarity.LEGENDARY),
        HARD_HITTER("hard_hitter", Material.MACE, "Titanbreaker", CustomLoreUtil.Rarity.EPIC);

        private static final Map<String, LegendaryType> BY_ID = new HashMap<>();
        static {
            for (LegendaryType t : values()) BY_ID.put(t.id, t);
        }

        private final String id;
        private final Material material;
        private final String display;
        private final CustomLoreUtil.Rarity rarity;

        LegendaryType(String id, Material material, String plainName, CustomLoreUtil.Rarity rarity) {
            this.id = id;
            this.material = material;
            this.rarity = rarity;
            this.display = CustomLoreUtil.displayNameTag(rarity, plainName);
        }

        public static LegendaryType fromId(String id) { return BY_ID.get(id); }
    }

    private record FrostScytheFreezeState(long expiresAt, int previousFreezeTicks, boolean previousFreezeLocked) {}
    private record RhittaBurnState(UUID attackerId, long expiresAt) {}

    private record LegendaryCopy(Player player, LegendaryType type, int amount, Runnable remover) {}

    private static final class RecipeOffhandHolder {
        private ItemStack item;

        private RecipeOffhandHolder(ItemStack item) {
            this.item = item;
        }

        private ItemStack item() {
            return item;
        }

        private void item(ItemStack item) {
            this.item = item;
        }
    }

    private record LegendaryRecipe(LegendaryType type, Map<Material, Integer> ingredients) {}
    private record ChronoState(Location loc, long readyAt) {}
    private record RechargeState(int charges, long rechargeStartedAt) {}
    private record LifeStealerState(int stacks, long expiresAt, Set<UUID> seenVictims) {
        private static LifeStealerState empty() {
            return new LifeStealerState(0, 0L, new HashSet<>());
        }

        private LifeStealerState addVictim(UUID victimId) {
            Set<UUID> nextVictims = new HashSet<>(seenVictims);
            nextVictims.add(victimId);
            return new LifeStealerState(Math.min(LIFE_STEALER_MAX_STACKS, stacks + 1), Long.MAX_VALUE, nextVictims);
        }

        private LifeStealerState refreshDuration() {
            if (stacks <= 0) {
                return empty();
            }
            return new LifeStealerState(stacks, Long.MAX_VALUE, new HashSet<>(seenVictims));
        }
    }
    private record StrengthDomainState(Location center, long expiresAt) {}
    private record WitherBladeState(int skullCharges, long skullRechargeStartedAt, int dashCharges, long dashRechargeStartedAt) {
        private long skullMillisUntilNext() {
            return millisUntilNextCharge(skullCharges, skullRechargeStartedAt, WITHER_BLADE_SKULL_MAX_CHARGES, WITHER_BLADE_SKULL_RECHARGE_MS);
        }

        private long dashMillisUntilNext() {
            return millisUntilNextCharge(dashCharges, dashRechargeStartedAt, WITHER_BLADE_DASH_MAX_CHARGES, WITHER_BLADE_DASH_RECHARGE_MS);
        }
    }
}
