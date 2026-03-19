package me.rique.smpcore.legendary;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.item.CustomToolListener;
import me.rique.smpcore.util.CustomLoreUtil;
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
import org.bukkit.entity.Endermite;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
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
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.ClickType;
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
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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

public final class LegendaryListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final int LEGENDARY_ITEM_DATA_VERSION = 9;
    private static final int STARTUP_LEGENDARY_MIGRATION_CHUNKS_PER_TICK = 24;
    private static final int LEGENDARY_ITEM_SCAN_MAX_DEPTH = 2;
    private static final int ENDERBOW_TP_COOLDOWN = 30;
    private static final int CHRONO_READY_SECONDS = 7;
    private static final int CHRONO_COOLDOWN = 45;
    private static final int CHRONO_COOLDOWN_DEATH = 90;
    private static final int HARPOON_COOLDOWN = 22;
    private static final int HYPNOSIS_COOLDOWN = 5;
    private static final int HYPNOSIS_MAX = 10;
    private static final int EMERALD_BLADE_MAX_LEVEL = 20;
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
    private static final String GUI_TITLE_RECIPES = "<gradient:#FEE440:#00BBF9><bold>Legendary Recipes</bold></gradient>";
    private static final String GUI_TITLE_PREFIX_RECIPE = "<gradient:#A0E7E5:#B4F8C8><bold>Recipe:</bold></gradient> ";

    private final SMPCore plugin;

    private final NamespacedKey keyLegendary;
    private final NamespacedKey keyLegendaryVersion;
    private final NamespacedKey keyLegendaryInstance;
    private final NamespacedKey keyMenuLegendary;
    private final NamespacedKey keyEnderBone;
    private final NamespacedKey keyEnderbowForm;
    private final NamespacedKey keyEmeraldLevel;
    private final NamespacedKey keyEmeraldBladeItemModel;
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
    private final Enchantment enchantPower;
    private final Enchantment enchantSharpness;
    private final Enchantment enchantEfficiency;
    private final Enchantment enchantUnbreaking;

    private final List<LegendaryRecipe> recipes;
    private final Set<NamespacedKey> recipeBookKeys = new HashSet<>();

    private final Map<UUID, Long> enderbowCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> chronoCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> harpoonCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> hypnosisCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> executionerStrengthCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> executionerShockwaveCd = new ConcurrentHashMap<>();

    private final Map<UUID, ChronoState> chronoStates = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> controlledByOwner = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> ownerByMob = new ConcurrentHashMap<>();
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

    public LegendaryListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyLegendary = new NamespacedKey(plugin, "legendary_id");
        this.keyLegendaryVersion = new NamespacedKey(plugin, "legendary_data_version");
        this.keyLegendaryInstance = new NamespacedKey(plugin, "legendary_instance");
        this.keyMenuLegendary = new NamespacedKey(plugin, "legendary_menu_id");
        this.keyEnderBone = new NamespacedKey(plugin, "ender_bone");
        this.keyEnderbowForm = new NamespacedKey(plugin, "enderbow_form");
        this.keyEmeraldLevel = new NamespacedKey(plugin, "emerald_blade_level");
        this.keyEmeraldBladeItemModel = new NamespacedKey(plugin, "emerald_blade");
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
        this.enchantPower = requireEnchantment("power");
        this.enchantSharpness = requireEnchantment("sharpness");
        this.enchantEfficiency = requireEnchantment("efficiency");
        this.enchantUnbreaking = requireEnchantment("unbreaking");
        this.recipes = buildRecipes();
        registerRecipeBookRecipes();
        Bukkit.getScheduler().runTask(plugin, () -> {
            cleanupTaggedEnderSwordDragons();
            cleanupTaggedEnderSwordSeats();
            for (Player online : Bukkit.getOnlinePlayers()) {
                migratePlayerLegendaryItems(online);
                refreshMagnetTracking(online);
                refreshWitherBladeLore(online);
                refreshLifeStealerLore(online);
            }
            scheduleLoadedChunkLegendaryMigration();
        });
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickMagnets, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickLifeStealerLore, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickEnderSwordDragons, 1L, 1L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        migratePlayerLegendaryItems(event.getPlayer());
        refreshMagnetTracking(event.getPlayer());
        refreshWitherBladeLore(event.getPlayer());
        refreshLifeStealerLore(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        enderbowCd.remove(id);
        chronoCd.remove(id);
        harpoonCd.remove(id);
        hypnosisCd.remove(id);
        executionerStrengthCd.remove(id);
        executionerShockwaveCd.remove(id);
        chronoStates.remove(id);
        activeMagnetPlayers.remove(id);
        pendingMagnetRefresh.remove(id);
        pendingWitherBladeLoreRefresh.remove(id);
        clearEnderDragonChunkTickets(id);
        despawnEnderSwordDragon(id, false);

        Set<UUID> controlled = controlledByOwner.remove(id);
        if (controlled != null) {
            for (UUID mobId : controlled) ownerByMob.remove(mobId);
        }
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
        LegendaryRecipe recipe = findRecipe(inv.getMatrix());
        if (recipe != null) {
            inv.setResult(null);
            return;
        }
        if (isLegendaryRecipe(event.getRecipe())) {
            inv.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getFirstItem();
        ItemStack right = event.getInventory().getSecondItem();
        LegendaryType leftType = typeOf(left);
        LegendaryType rightType = typeOf(right);
        if (leftType == null && rightType == null) return;
        if (!canUseVanillaLegendaryUtilities(leftType, rightType)) {
            event.setResult(null);
            return;
        }

        ItemStack source = leftType == LegendaryType.EXECUTIONER_BLADE ? left : right;
        ItemStack result = event.getResult();
        if (source == null || result == null || result.getType() == Material.AIR) return;
        event.setResult(preserveLegendaryUtilityResult(source, result, LegendaryType.EXECUTIONER_BLADE));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        ItemStack top = event.getInventory().getUpperItem();
        ItemStack bottom = event.getInventory().getLowerItem();
        LegendaryType topType = typeOf(top);
        LegendaryType bottomType = typeOf(bottom);
        if (topType == null && bottomType == null) return;
        if (!canUseVanillaLegendaryUtilities(topType, bottomType)) {
            event.setResult(null);
            return;
        }

        ItemStack source = topType == LegendaryType.EXECUTIONER_BLADE ? top : bottom;
        ItemStack result = event.getResult();
        if (source == null || result == null || result.getType() == Material.AIR) return;
        event.setResult(preserveLegendaryUtilityResult(source, result, LegendaryType.EXECUTIONER_BLADE));
    }

    private boolean canUseVanillaLegendaryUtilities(LegendaryType first, LegendaryType second) {
        return isVanillaUtilityAllowed(first) && isVanillaUtilityAllowed(second);
    }

    private boolean isVanillaUtilityAllowed(LegendaryType type) {
        return type == null || type == LegendaryType.EXECUTIONER_BLADE;
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
        resultMeta.setUnbreakable(true);
        resultMeta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        applyLegendaryTypeState(resultMeta, type);
        updated.setItemMeta(resultMeta);
        return updated;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareItemEnchant(PrepareItemEnchantEvent event) {
        if (typeOf(event.getItem()) != LegendaryType.EMERALD_BLADE) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        LegendaryType type = typeOf(event.getItem());
        if (type == null) {
            return;
        }
        if (type == LegendaryType.EMERALD_BLADE) {
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

        if (top.getHolder() instanceof RecipeMenuHolder holder) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            if (holder.type() == null) {
                String recipeId = readMenuLegendaryId(clicked);
                if (recipeId == null) return;
                if ("backpack".equals(recipeId)) {
                    openBackpackRecipeDetails(player);
                    return;
                }
                if (plugin.getCustomToolListener() != null && plugin.getCustomToolListener().isCustomToolId(recipeId)) {
                    openCustomToolRecipeDetails(player, recipeId);
                    return;
                }

                LegendaryType type = LegendaryType.fromId(recipeId);
                if (type == null) return;
                openRecipeDetails(player, type);
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
                openRecipeMenu(player);
            }
            return;
        }

        if (top.getHolder() instanceof CustomToolRecipeHolder holder) {
            event.setCancelled(true);
            if (event.getSlot() == RECIPE_TRADE_SLOT) {
                String displayName = plugin.getCustomToolListener() == null
                    ? "this tool"
                    : plugin.getCustomToolListener().displayNameFor(holder.toolId());
                player.sendMessage(MessageUtil.info(
                    "Craft <white>" + displayName + "</white> in a normal crafting table."
                ));
                return;
            }
            if (event.getSlot() == 18) {
                openRecipeMenu(player);
            }
            return;
        }

        if (top.getHolder() instanceof BackpackRecipeHolder) {
            event.setCancelled(true);
            if (event.getSlot() == RECIPE_TRADE_SLOT) {
                if (plugin.getBackpackListener() != null) {
                    plugin.getBackpackListener().tradeBackpack(player);
                }
                Bukkit.getScheduler().runTask(plugin, () -> openBackpackRecipeDetails(player));
                return;
            }
            if (event.getSlot() == 18) {
                openRecipeMenu(player);
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
        if (top.getHolder() instanceof RecipeMenuHolder
            || top.getHolder() instanceof BackpackRecipeHolder
            || top.getHolder() instanceof CustomToolRecipeHolder) {
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
        Inventory inv = Bukkit.createInventory(new RecipeMenuHolder(null), 45, MM.deserialize(GUI_TITLE_RECIPES));

        ItemStack filler = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24};
        LegendaryType[] types = LegendaryType.values();
        for (int i = 0; i < types.length && i < slots.length; i++) {
            LegendaryType type = types[i];
            ItemStack icon = createPreviewItem(type);
            tagMenuLegendaryId(icon, type.id);
            inv.setItem(slots[i], icon);
        }

        int backpackSlot = 31;
        ItemStack backpackIcon = createBackpackRecipeDisplayItem();
        tagMenuLegendaryId(backpackIcon, "backpack");
        ItemMeta backpackMeta = backpackIcon.getItemMeta();
        if (backpackMeta != null) {
            List<Component> lore = backpackMeta.lore() == null ? new ArrayList<>() : new ArrayList<>(backpackMeta.lore());
            lore.add(MM.deserialize("<dark_gray>Click to view recipe</dark_gray>"));
            backpackMeta.lore(lore);
            backpackIcon.setItemMeta(backpackMeta);
        }
        inv.setItem(backpackSlot, backpackIcon);

        if (plugin.getCustomToolListener() != null) {
            ItemStack advanced = createCustomToolPreview(CustomToolListener.ADVANCED_PICKAXE_ID);
            tagMenuLegendaryId(advanced, CustomToolListener.ADVANCED_PICKAXE_ID);
            inv.setItem(38, advanced);

            ItemStack amethyst = createCustomToolPreview(CustomToolListener.AMETHYST_PICKAXE_ID);
            tagMenuLegendaryId(amethyst, CustomToolListener.AMETHYST_PICKAXE_ID);
            inv.setItem(40, amethyst);
        }

        player.openInventory(inv);
    }

    public List<String> legendaryIds() {
        List<String> ids = new ArrayList<>();
        for (LegendaryType type : LegendaryType.values()) {
            ids.add(type.id);
        }
        return ids;
    }

    public boolean isLegendaryItem(ItemStack item) {
        return typeOf(item) != null;
    }

    public String displayNameForLegendary(String id) {
        LegendaryType type = LegendaryType.fromId(normalizeLegendaryId(id));
        if (type == null) return null;
        return PLAIN.serialize(MM.deserialize(type.display)).trim();
    }

    public String normalizeLegendaryId(String input) {
        if (input == null) return null;
        String normalized = input.trim().toLowerCase();
        if (normalized.isEmpty()) return null;
        return switch (normalized) {
            case "ender_bow" -> "enderbow";
            case "ender", "enderblade", "ender_sword", "dragonblade", "dragon_sword" -> "ender_sword";
            case "chrono", "chronosword", "chrono_blade" -> "chrono_sword";
            case "harpoon", "launcher" -> "harpoon_launcher";
            case "hypnosis", "staff" -> "hypnosis_staff";
            case "emerald", "blade" -> "emerald_blade";
            case "warpick", "pick" -> "war_pick";
            case "magnet", "faraday", "faradays_magnet", "faradays" -> "faradays_magnet";
            case "wind", "windcannon", "cannon", "wind_charge_cannon" -> "wind_charge_cannon";
            case "executioner", "executionerblade", "executioner_sword", "exec" -> "executioner_blade";
            case "hermes", "hermesboots", "hermes_boots", "boots" -> "hermes_boots";
            case "wither", "witherblade", "wither_sword" -> "wither_blade";
            case "lifestealer", "life_stealer", "life", "stealer", "bloodsword" -> "life_stealer";
            case "thor", "hammer", "thors", "mjolnir", "thors_hammer" -> "thors_hammer";
            default -> normalized;
        };
    }

    public ItemStack createLegendaryById(String id) {
        LegendaryType type = LegendaryType.fromId(normalizeLegendaryId(id));
        if (type == null) return null;
        return createItem(type);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        ItemStack hand = event.getItem();
        LegendaryType type = typeOf(hand);
        if (type == null) return;

        Player player = event.getPlayer();
        Action action = event.getAction();
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;

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
                player.sendMessage(MessageUtil.info("Enderbow form: <white>" + (tp ? "Teleport" : "Arrow") + "</white>."));
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
            case FARADAYS_MAGNET -> {
                if (!right) return;
                event.setCancelled(true);
                toggleMagnet(player, hand);
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
            case WAR_PICK -> {
                // passive/other event driven
            }
            case THORS_HAMMER -> {
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
            case FARADAYS_MAGNET -> {
                event.setCancelled(true);
                toggleMagnet(player, hand);
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
        if (state == null) return;
        if (player.getHealth() - event.getFinalDamage() > 0.0) return;

        event.setCancelled(true);
        activateChrono(player, true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
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

            event.getDrops().add(createEnderBone().asQuantity(plugin.getConfigManager().enderBoneDropCount));
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
        if (killer == null) return;
        LegendaryType killerWeapon = typeOf(killer.getInventory().getItemInMainHand());
        if (killerWeapon == LegendaryType.LIFE_STEALER && event.getEntity() instanceof Player playerVictim) {
            grantLifeStealerKillBuff(killer, killer.getInventory().getItemInMainHand(), playerVictim.getUniqueId());
        }
        if (killerWeapon != LegendaryType.EMERALD_BLADE) return;
        int amount = event.getEntity() instanceof Player ? 5 : ThreadLocalRandom.current().nextInt(1, 4);
        event.getDrops().add(new ItemStack(Material.EMERALD, amount));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMobTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        UUID owner = ownerByMob.get(mob.getUniqueId());
        if (owner == null) return;
        if (event.getTarget() instanceof Player target && sameTeamOrSelf(owner, target.getUniqueId())) {
            event.setCancelled(true);
            mob.setTarget(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (isOwnedEnderDragon(event.getDamager())) {
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

        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        directControlledMobs(attacker, victim);

        LegendaryType held = typeOf(attacker.getInventory().getItemInMainHand());
        if (held == LegendaryType.WITHER_BLADE) {
            refreshWitherBladeLore(attacker);
            double bonus = witherBladeBonusDamage(attacker);
            if (bonus > 0.0) {
                event.setDamage(event.getDamage() + bonus);
            }
        } else if (held == LegendaryType.EXECUTIONER_BLADE) {
            event.setDamage(event.getDamage() + executionerBladeBonusDamage(attacker));
        } else if (held == LegendaryType.LIFE_STEALER) {
            refreshLifeStealerLore(attacker);
        }

        if (held == LegendaryType.THORS_HAMMER) {
            victim.getWorld().strikeLightningEffect(victim.getLocation());
            event.setDamage(event.getDamage() + plugin.getConfigManager().thorsHammerBonusDamage);
        }

        if (!(victim instanceof Player targetPlayer)) return;
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
        if (typeOf(player.getInventory().getItemInMainHand()) != LegendaryType.WAR_PICK) return;
        UUID playerId = player.getUniqueId();
        // Prevent recursive AoE chains when breaking adjacent blocks below.
        if (!warPickAoePlayers.add(playerId)) return;

        try {
            Block center = event.getBlock();
            World world = center.getWorld();
            world.createExplosion(center.getLocation().add(0.5, 0.5, 0.5), 2.0f, false, false, player);

            int bx = center.getX();
            int by = center.getY();
            int bz = center.getZ();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block target = world.getBlockAt(bx + dx, by + dy, bz + dz);
                        if (target.getType().isAir()) continue;
                        if (isProtected(target.getType())) continue;
                        // Use the normal player-break path so protection plugins can cancel it.
                        player.breakBlock(target);
                    }
                }
            }
        } finally {
            warPickAoePlayers.remove(playerId);
        }
    }

    private void openRecipeDetails(Player player, LegendaryType type) {
        Inventory inv = Bukkit.createInventory(
            new RecipeMenuHolder(type),
            27,
            MM.deserialize(GUI_TITLE_PREFIX_RECIPE + type.display)
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
        inv.setItem(18, createGuiItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to item list</gray>")));
        player.openInventory(inv);
    }

    private void openBackpackRecipeDetails(Player player) {
        Inventory inv = Bukkit.createInventory(
            new BackpackRecipeHolder(),
            27,
            MM.deserialize(GUI_TITLE_PREFIX_RECIPE + "<gold><bold>Backpack</bold></gold>")
        );

        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        int[] matrixSlots = {3, 4, 5, 12, 13, 14, 21, 22, 23};
        Map<Material, Integer> backpackIngredients = plugin.getBackpackListener() == null
            ? Map.of(Material.LEATHER, 4, Material.STRING, 4, Material.CHEST, 1)
            : plugin.getBackpackListener().tradeIngredients();
        List<Map.Entry<Material, Integer>> ingredients = new ArrayList<>(backpackIngredients.entrySet());

        int index = 0;
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

        inv.setItem(16, createBackpackRecipeDisplayItem());
        boolean canTrade = plugin.getBackpackListener() != null && plugin.getBackpackListener().canTradeBackpack(player);
        inv.setItem(RECIPE_TRADE_SLOT, createTradeButton(player, "<gold><bold>Backpack</bold></gold>", backpackIngredients, canTrade));
        inv.setItem(18, createGuiItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to item list</gray>")));
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
            MM.deserialize(GUI_TITLE_PREFIX_RECIPE + "<aqua><bold>" + displayName + "</bold></aqua>")
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
        inv.setItem(RECIPE_TRADE_SLOT, createGuiItem(
            Material.CRAFTING_TABLE,
            "<gold><bold>Craft In Table</bold></gold>",
            List.of("<gray>This is a normal crafting recipe.</gray>", "<gray>Use the shown ingredients in a crafting table.</gray>")
        ));
        inv.setItem(18, createGuiItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to item list</gray>")));
        player.openInventory(inv);
    }

    private ItemStack createPreviewItem(LegendaryType type) {
        ItemStack item = createDisplayLegendaryItem(type, true);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(MM.deserialize("<dark_gray>Click to view recipe</dark_gray>"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCustomToolPreview(String toolId) {
        if (plugin.getCustomToolListener() == null) {
            return createGuiItem(Material.BARRIER, "<red>Unavailable</red>", List.of());
        }

        ItemStack item = plugin.getCustomToolListener().createRecipePreview(toolId);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(MM.deserialize("<dark_gray>Click to view recipe</dark_gray>"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
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

    private ItemStack createBackpackRecipeDisplayItem() {
        ItemStack item = new ItemStack(Material.CHEST_MINECART);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(MM.deserialize("<gold><bold>Backpack</bold></gold>"));
        meta.lore(List.of(
            MM.deserialize("<dark_gray>Portable Storage</dark_gray>"),
            MM.deserialize("<gray>Right-click to open.</gray>")
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTradeButton(Player player, LegendaryType type) {
        Map<Material, Integer> ingredients = ingredientsFor(type);
        boolean canCraft = canCraftLegendary(player, type.id);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Legendaries can only be crafted at the active altar.</gray>");
        lore.add(canCraft
            ? "<green>You have the required materials.</green>"
            : "<red>You are missing some required materials.</red>");
        lore.add("<gray>Click for altar status and coordinates.</gray>");
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
            ? "<green>Click to receive " + displayName + "<green>.</green>"
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

        summonEnderDragon(player);
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

    private UUID enderDragonOwner(Entity entity) {
        if (entity instanceof EnderDragon dragon) {
            return enderDragonOwnerByDragon.get(dragon.getUniqueId());
        }
        if (entity instanceof EnderDragonPart part) {
            return enderDragonOwnerByDragon.get(part.getParent().getUniqueId());
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
        return plugin.getTeamManager() != null && plugin.getTeamManager().sameTeam(ownerId, targetId);
    }

    private void feedEmeraldBlade(Player player, ItemStack blade) {
        int current = emeraldLevel(blade);
        if (current >= EMERALD_BLADE_MAX_LEVEL) {
            player.sendMessage(MessageUtil.warn("Emerald Blade is already at max Sharpness <white>" + EMERALD_BLADE_MAX_LEVEL + "</white>."));
            return;
        }

        if (!takeEmeraldBlock(player)) {
            player.sendMessage(MessageUtil.error("You need an emerald block."));
            return;
        }

        int lvl = current + 1;
        setEmeraldLevel(blade, lvl);
        player.getInventory().setItemInMainHand(blade);
        player.sendMessage(MessageUtil.success("Emerald Blade Sharpness: <white>" + lvl + "</white>."));
    }

    private void toggleMagnet(Player player, ItemStack magnet) {
        boolean active = !isMagnetActive(magnet);
        setMagnetActive(magnet, active);
        refreshMagnetTracking(player);
        player.sendMessage(MessageUtil.info("Faraday's Magnet: <white>" + (active ? "ON" : "OFF") + "</white>."));
    }

    private void useWindChargeCannon(Player player, ItemStack cannon, boolean superShot) {
        if (!refreshWindChargeCannonState(cannon)) {
            return;
        }

        int charges = windChargeCannonCharges(cannon);
        if (charges <= 0) {
            player.sendMessage(MessageUtil.warn(
                "Wind Charge Cannon recharging: <white>" + windChargeCannonSecondsLeft(cannon) + "s</white>."));
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
        if (state.seenVictims().contains(victimId)) {
            LifeStealerState refreshed = state.refreshDuration();
            applyLifeStealerEffects(killer, refreshed);
            applyLifeStealerState(weapon, refreshed);
            refreshLifeStealerLore(killer);
            return;
        }

        LifeStealerState next = state.addVictim(victimId);
        applyLifeStealerEffects(killer, next);
        applyLifeStealerState(weapon, next);
        refreshLifeStealerLore(killer);
    }

    private void applyLifeStealerEffects(Player player, LifeStealerState state) {
        if (state.stacks() <= 0) {
            player.removePotionEffect(PotionEffectType.STRENGTH);
            player.removePotionEffect(PotionEffectType.SPEED);
            player.removePotionEffect(PotionEffectType.ABSORPTION);
            return;
        }

        int amplifier = Math.max(0, state.stacks() - 1);
        int durationTicks = Math.max(1, remainingLifeStealerTicks(state));
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, durationTicks, amplifier, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, amplifier, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, durationTicks, amplifier, false, false, true));
    }

    private void ensureLifeStealerEffects(Player player, LifeStealerState state) {
        if (state.stacks() <= 0) {
            return;
        }

        int amplifier = Math.max(0, state.stacks() - 1);
        int durationTicks = Math.max(1, remainingLifeStealerTicks(state));
        ensurePotionEffect(player, PotionEffectType.STRENGTH, durationTicks, amplifier);
        ensurePotionEffect(player, PotionEffectType.SPEED, durationTicks, amplifier);
        ensurePotionEffect(player, PotionEffectType.ABSORPTION, durationTicks, amplifier);
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

    private int remainingLifeStealerTicks(LifeStealerState state) {
        if (state.stacks() <= 0) {
            return 0;
        }
        long remainingMillis = state.expiresAt() - System.currentTimeMillis();
        return (int) Math.max(1L, (remainingMillis + 49L) / 50L);
    }

    private LifeStealerState strongerLifeStealerState(LifeStealerState current, LifeStealerState candidate) {
        if (candidate.stacks() > current.stacks()) {
            return candidate;
        }
        if (candidate.stacks() < current.stacks()) {
            return current;
        }
        return candidate.expiresAt() > current.expiresAt() ? candidate : current;
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
        int stacks = Math.max(0, pdc.getOrDefault(keyLifeStealerStacks, PersistentDataType.INTEGER, 0));
        long expiresAt = pdc.getOrDefault(keyLifeStealerExpiresAt, PersistentDataType.LONG, 0L);
        Set<UUID> seenVictims = parseLifeStealerVictims(
            pdc.getOrDefault(keyLifeStealerSeenPlayers, PersistentDataType.STRING, "")
        );

        if (stacks <= 0 || expiresAt <= System.currentTimeMillis()) {
            return LifeStealerState.empty();
        }

        return new LifeStealerState(stacks, expiresAt, seenVictims);
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
            pdc.set(keyLifeStealerExpiresAt, PersistentDataType.LONG, state.expiresAt());
            pdc.set(keyLifeStealerSeenPlayers, PersistentDataType.STRING, joinLifeStealerVictims(state.seenVictims()));
        }
        meta.lore(buildLifeStealerLore(meta, state));
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

    private boolean onCooldown(Map<UUID, Long> map, UUID uuid) {
        return map.getOrDefault(uuid, 0L) > System.currentTimeMillis();
    }

    private long secondsLeft(Map<UUID, Long> map, UUID uuid) {
        long diff = map.getOrDefault(uuid, 0L) - System.currentTimeMillis();
        return diff <= 0 ? 0 : (diff + 999L) / 1000L;
    }

    private void setCooldown(Map<UUID, Long> map, UUID uuid, int seconds) {
        map.put(uuid, System.currentTimeMillis() + (seconds * 1000L));
    }

    public boolean canCraftLegendary(Player player, String id) {
        LegendaryType type = LegendaryType.fromId(normalizeLegendaryId(id));
        if (type == null) return false;
        Map<Material, Integer> ingredients = ingredientsFor(type);
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

        List<String> lines = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : ingredientsFor(type).entrySet()) {
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

        List<String> lines = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : ingredientsFor(type).entrySet()) {
            lines.add(entry.getValue() + "x " + prettyRecipeMaterial(type, entry.getKey()));
        }
        return lines;
    }

    public boolean craftLegendaryAtAltar(Player player, String id) {
        LegendaryType type = LegendaryType.fromId(normalizeLegendaryId(id));
        if (type == null) return false;
        return giveCraftedLegendary(player, type, "altar");
    }

    private boolean tradeLegendary(Player player, LegendaryType type) {
        return giveCraftedLegendary(player, type, "trade");
    }

    private boolean giveCraftedLegendary(Player player, LegendaryType type, String source) {
        Map<Material, Integer> ingredients = ingredientsFor(type);
        if (!removeRecipeMaterials(player, type, ingredients)) {
            player.sendMessage(MessageUtil.error(
                "You do not have all the materials for " + type.display + "<red>.</red>"
            ));
            return false;
        }

        ItemStack reward = createItem(type);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        announceLegendaryCraft(player, type);
        String sourceText = "trade".equals(source) ? "Traded materials for " : "Crafted ";
        player.sendMessage(MessageUtil.success(sourceText + type.display + "<green>.</green>"));
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

    private boolean isValidRecipeMaterial(ItemStack item, LegendaryType type, Material material) {
        if (type == LegendaryType.ENDER_SWORD && material == Material.BONE) {
            return isEnderBone(item);
        }
        return item != null
            && item.getType() == material
            && typeOf(item) == null;
    }

    private String prettyRecipeMaterial(LegendaryType type, Material material) {
        if (type == LegendaryType.ENDER_SWORD && material == Material.BONE) {
            return "Ender Bone";
        }
        return prettyMaterial(material);
    }

    private ItemStack displayRecipeIngredient(LegendaryType type, Material material, int amount) {
        ItemStack ingredientItem = type == LegendaryType.ENDER_SWORD && material == Material.BONE
            ? createEnderBone()
            : new ItemStack(material);
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
        meta.displayName(MM.deserialize("<dark_purple><bold>Ender Bone</bold></dark_purple>"));
        meta.lore(CustomLoreUtil.buildStyledLore(
            Material.BONE,
            "LEGENDARY",
            "TROPHY",
            List.of("<gray>Dropped by the Ender Dragon.</gray>"),
            List.of()
        ));
        meta.getPersistentDataContainer().set(keyEnderBone, PersistentDataType.BYTE, (byte) 1);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
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
        return CustomLoreUtil.buildStyledLore(meta, material, "LEGENDARY", itemKind, topLines, List.of(sections));
    }

    private List<Component> buildMythicLore(
        ItemMeta meta,
        Material material,
        String itemKind,
        List<String> topLines,
        CustomLoreUtil.LoreSection... sections
    ) {
        return CustomLoreUtil.buildStyledLore(meta, material, "MYTHIC", itemKind, topLines, List.of(sections));
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
            List.of("<gray>Unbreakable</gray>"),
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

    private List<Component> buildMagnetLore(ItemMeta meta, boolean active) {
        return buildLegendaryLore(
            meta,
            Material.RECOVERY_COMPASS,
            "UTILITY",
            List.of("<gray>Current State: " + (active ? "<green>ON</green>" : "<red>OFF</red>") + "</gray>"),
            CustomLoreUtil.section(
                "Item Ability",
                "Faraday Pull",
                "<gray><white>Right-click</white> while holding to toggle the magnet.</gray>",
                "<gray>When enabled, pulls dropped items within <white>" + MAGNET_RADIUS + "</white> blocks.</gray>",
                "<gray>Works from anywhere in your inventory.</gray>"
            )
        );
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
        int absorptionHearts = stacks * 2;
        List<String> topLines = new ArrayList<>();
        topLines.add("<gray>Current Bonus:</gray> <red>" + stacks + " Strength</red><gray>,</gray> <aqua>" + stacks + " Speed</aqua><gray>,</gray> <gold>" + absorptionHearts + " Absorption Hearts</gold>");
        if (state.stacks() > 0) {
            topLines.add("<gray>Streak Timer: <white>" + Math.max(1L, (state.expiresAt() - System.currentTimeMillis() + 999L) / 1000L) + "s</white></gray>");
        }
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
                "<gray>For each <white>unique player</white> killed, gain <red>1 level of Strength</red> and <aqua>Speed</aqua></gray>",
                "<gray>plus <gold>2 Absorption Hearts</gold> for <white>1 minute</white>.</gray>",
                "<gray>Stacks on new player kills and the timer resets every kill.</gray>"
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
                "<gold><bold>Wind Charge Cannon</bold></gold><gray> charges: <white>" + remainingCharges + "/" + WIND_CHARGE_CANNON_MAX_CHARGES + "</white>.</gray>"
            ));
            return;
        }
        player.sendActionBar(MM.deserialize(
            "<gold><bold>Wind Charge Cannon</bold></gold><gray> depleted. Recharge: <white>" + WIND_CHARGE_CANNON_RECHARGE + "s</white>.</gray>"
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

    private boolean migrateLegendaryItem(ItemStack item) {
        LegendaryType type = typeOf(item);
        if (type == null) return false;

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

    private void refreshLegendaryPresentation(ItemStack item) {
        LegendaryType type = typeOf(item);
        if (type == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

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
        if (type == LegendaryType.EMERALD_BLADE) {
            meta.setUnbreakable(false);
            meta.removeItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            return;
        }
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
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
            case HYPNOSIS_STAFF -> meta.lore(buildLegendaryLore(
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
            case EMERALD_BLADE -> {
                int level = Math.max(1, Math.min(
                    EMERALD_BLADE_MAX_LEVEL,
                    pdc.getOrDefault(keyEmeraldLevel, PersistentDataType.INTEGER, 1)
                ));
                pdc.set(keyEmeraldLevel, PersistentDataType.INTEGER, level);
                meta.setItemModel(keyEmeraldBladeItemModel);
                setEnchantLevel(meta, enchantSharpness, level);
                setEnchantLevel(meta, enchantUnbreaking, 3);
                meta.lore(buildEmeraldBladeLore(meta, level));
            }
            case WAR_PICK -> {
                setEnchantLevel(meta, enchantSharpness, 10);
                setEnchantLevel(meta, enchantEfficiency, 1);
                meta.lore(buildLegendaryLore(
                    meta,
                    Material.DIAMOND_PICKAXE,
                    "PICKAXE",
                    List.of(),
                    CustomLoreUtil.section(
                        "Item Ability",
                        "War Mining",
                        "<gray>Break a block to blast a <white>3x3x3</white> area.</gray>",
                        "<gray>Critical hits can knock players and damage armor.</gray>",
                        "<gray>No mining cooldown.</gray>"
                    )
                ));
            }
            case FARADAYS_MAGNET -> {
                boolean active = pdc.getOrDefault(keyMagnetActive, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
                pdc.set(keyMagnetActive, PersistentDataType.BYTE, active ? (byte) 1 : (byte) 0);
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
            case EXECUTIONER_BLADE -> meta.lore(buildLegendaryLore(
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
            case HERMES_BOOTS -> {
                if (meta instanceof LeatherArmorMeta leatherArmorMeta) {
                    leatherArmorMeta.setColor(HERMES_BOOTS_COLOR);
                }
                meta.removeAttributeModifier(Attribute.MOVEMENT_SPEED);
                meta.addAttributeModifier(
                    Attribute.MOVEMENT_SPEED,
                    new AttributeModifier(
                        keyHermesBootsSpeedModifier,
                        HERMES_BOOTS_SPEED_SCALAR,
                        AttributeModifier.Operation.ADD_SCALAR,
                        EquipmentSlotGroup.FEET
                    )
                );
                meta.lore(buildLegendaryLore(
                    meta,
                    Material.LEATHER_BOOTS,
                    "BOOTS",
                    List.of("<gray>Only works while worn</gray>"),
                    CustomLoreUtil.section(
                        "Passive",
                        "Hermes Step",
                        "<gray>Negates all fall damage.</gray>",
                        "<gray>Greatly increases movement speed.</gray>"
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
                setEnchantLevel(meta, enchantSharpness, 3);
                applyLifeStealerState(meta, refreshLifeStealerState(meta));
            }
            case THORS_HAMMER -> meta.lore(buildLegendaryLore(
                meta,
                Material.MACE,
                "MACE",
                List.of("<gray>Bonus Hit Damage: <white>+" + formatDamageNumber(plugin.getConfigManager().thorsHammerBonusDamage) + "</white></gray>"),
                CustomLoreUtil.section(
                    "Item Ability",
                    "Thunder Strike",
                    "<gray>Every hit calls lightning on the target.</gray>",
                    "<gray>Deals normal bonus hit damage only.</gray>",
                    "<gray>Does not bypass armor or Totems.</gray>"
                )
            ));
        }
    }

    private void setEnchantLevel(ItemMeta meta, Enchantment enchantment, int level) {
        if (meta.getEnchantLevel(enchantment) == level) return;
        meta.removeEnchant(enchantment);
        meta.addEnchant(enchantment, level, true);
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
                e(Material.BONE, 8), e(Material.DIAMOND_SWORD, 1))),
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
            new LegendaryRecipe(LegendaryType.WAR_PICK, ingredients(
                e(Material.REDSTONE_BLOCK, 32), e(Material.TNT, 32), e(Material.DIAMOND_PICKAXE, 1), e(Material.CRYING_OBSIDIAN, 16))),
            new LegendaryRecipe(LegendaryType.FARADAYS_MAGNET, ingredients(
                e(Material.IRON_BLOCK, 12), e(Material.REDSTONE_BLOCK, 12), e(Material.COPPER_BLOCK, 6), e(Material.NETHERITE_INGOT, 1))),
            new LegendaryRecipe(LegendaryType.WIND_CHARGE_CANNON, ingredients(
                e(Material.PRISMARINE_SHARD, 24), e(Material.WIND_CHARGE, 24), e(Material.COPPER_BLOCK, 6), e(Material.DISPENSER, 1))),
            new LegendaryRecipe(LegendaryType.EXECUTIONER_BLADE, ingredients(
                e(Material.NETHERITE_SWORD, 1), e(Material.BLAZE_ROD, 12), e(Material.ANVIL, 1), e(Material.WIND_CHARGE, 6), e(Material.REDSTONE_BLOCK, 12))),
            new LegendaryRecipe(LegendaryType.HERMES_BOOTS, ingredients(
                e(Material.LEATHER_BOOTS, 1), e(Material.LEATHER, 12), e(Material.FEATHER, 24), e(Material.GOLD_BLOCK, 6))),
            new LegendaryRecipe(LegendaryType.WITHER_BLADE, ingredients(
                e(Material.NETHERITE_SWORD, 1), e(Material.WITHER_SKELETON_SKULL, 2), e(Material.NETHER_STAR, 1), e(Material.SOUL_SAND, 24))),
            new LegendaryRecipe(LegendaryType.LIFE_STEALER, ingredients(
                e(Material.NETHERITE_SWORD, 1), e(Material.GHAST_TEAR, 12), e(Material.FERMENTED_SPIDER_EYE, 8), e(Material.REDSTONE_BLOCK, 8), e(Material.GOLDEN_APPLE, 4))),
            new LegendaryRecipe(LegendaryType.THORS_HAMMER, ingredients(
                e(Material.MACE, 1), e(Material.LIGHTNING_ROD, 12), e(Material.NETHERITE_INGOT, 2), e(Material.WIND_CHARGE, 12)))
        );
    }

    private void registerRecipeBookRecipes() {
        recipeBookKeys.clear();
        for (LegendaryRecipe recipe : recipes) {
            NamespacedKey key = new NamespacedKey(plugin, "legendary_" + recipe.type().id);
            Bukkit.removeRecipe(key);
        }
    }

    private void discoverLegendaryRecipes(Player player) {
        // custom items are traded through /lrecipe instead of the vanilla recipe book
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
        if (event.getClickedInventory() == null) return false;
        if (!(event.getView().getTopInventory() instanceof CraftingInventory inv)) return false;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return false;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return false;

        LegendaryType out = typeOf(event.getCurrentItem());
        LegendaryRecipe recipe = findRecipe(inv.getMatrix());
        if (out == null && recipe == null) return false;

        event.setCancelled(true);
        clearCustomCraftState(inv);
        player.updateInventory();
        player.sendMessage(MessageUtil.info("Use <white>/lrecipe</white> to trade materials for custom items."));
        return true;
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

    private record RecipeMenuHolder(LegendaryType type) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record BackpackRecipeHolder() implements InventoryHolder {
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

    private enum LegendaryType {
        ENDER_SWORD("ender_sword", Material.NETHERITE_SWORD, "<dark_purple><bold>Ender Sword</bold></dark_purple>"),
        ENDERBOW("enderbow", Material.BOW, "<gold><bold>Enderbow</bold></gold>"),
        CHRONO_SWORD("chrono_sword", Material.DIAMOND_SWORD, "<gold><bold>Chrono Sword</bold></gold>"),
        HARPOON_LAUNCHER("harpoon_launcher", Material.CROSSBOW, "<gold><bold>Harpoon Launcher</bold></gold>"),
        HYPNOSIS_STAFF("hypnosis_staff", Material.BLAZE_ROD, "<gold><bold>Hypnosis Staff</bold></gold>"),
        EMERALD_BLADE("emerald_blade", Material.DIAMOND_SWORD, "<gold><bold>Emerald Blade</bold></gold>"),
        WAR_PICK("war_pick", Material.DIAMOND_PICKAXE, "<gold><bold>War Pick</bold></gold>"),
        FARADAYS_MAGNET("faradays_magnet", Material.RECOVERY_COMPASS, "<gold><bold>Faraday's Magnet</bold></gold>"),
        WIND_CHARGE_CANNON("wind_charge_cannon", Material.PRISMARINE_SHARD, "<gold><bold>Wind Charge Cannon</bold></gold>"),
        EXECUTIONER_BLADE("executioner_blade", Material.NETHERITE_SWORD, "<red><bold>Executioner Blade</bold></red>"),
        HERMES_BOOTS("hermes_boots", Material.LEATHER_BOOTS, "<gold><bold>Hermes Boots</bold></gold>"),
        WITHER_BLADE("wither_blade", Material.NETHERITE_SWORD, "<dark_gray><bold>Wither Blade</bold></dark_gray>"),
        LIFE_STEALER("life_stealer", Material.NETHERITE_SWORD, "<light_purple><bold>Life Stealer</bold></light_purple>"),
        THORS_HAMMER("thors_hammer", Material.MACE, "<gold><bold>Thor's Hammer</bold></gold>");

        private static final Map<String, LegendaryType> BY_ID = new HashMap<>();
        static {
            for (LegendaryType t : values()) BY_ID.put(t.id, t);
        }

        private final String id;
        private final Material material;
        private final String display;

        LegendaryType(String id, Material material, String display) {
            this.id = id;
            this.material = material;
            this.display = display;
        }

        public static LegendaryType fromId(String id) { return BY_ID.get(id); }
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
            return new LifeStealerState(stacks + 1, System.currentTimeMillis() + (LIFE_STEALER_BUFF_SECONDS * 1000L), nextVictims);
        }

        private LifeStealerState refreshDuration() {
            if (stacks <= 0) {
                return empty();
            }
            return new LifeStealerState(stacks, System.currentTimeMillis() + (LIFE_STEALER_BUFF_SECONDS * 1000L), new HashSet<>(seenVictims));
        }
    }
    private record WitherBladeState(int skullCharges, long skullRechargeStartedAt, int dashCharges, long dashRechargeStartedAt) {
        private long skullMillisUntilNext() {
            return millisUntilNextCharge(skullCharges, skullRechargeStartedAt, WITHER_BLADE_SKULL_MAX_CHARGES, WITHER_BLADE_SKULL_RECHARGE_MS);
        }

        private long dashMillisUntilNext() {
            return millisUntilNextCharge(dashCharges, dashRechargeStartedAt, WITHER_BLADE_DASH_MAX_CHARGES, WITHER_BLADE_DASH_RECHARGE_MS);
        }
    }
}
