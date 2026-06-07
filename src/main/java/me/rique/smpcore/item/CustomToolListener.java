package me.rique.smpcore.item;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.awakening.AwakeningTableListener;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class CustomToolListener implements Listener {

    public static final String ADVANCED_PICKAXE_ID = "advanced_pickaxe";
    public static final String GRAPPLE_HOOK_ID = "grapple_hook";
    public static final String SPELUNKER_LANTERN_ID = "spelunker_lantern";
    public static final String SURVEYORS_LENS_ID = "surveyors_lens";
    public static final String MENDERS_KIT_ID = "menders_kit";
    private static final String LEGACY_AMETHYST_PICKAXE_ID = "amethyst_pickaxe";

    private static final Set<Material> GOLD_ORES = EnumSet.of(Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE);
    private static final Set<Material> COAL_ORES = EnumSet.of(Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE);
    private static final Set<Material> IRON_ORES = EnumSet.of(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE);
    private static final Set<Material> REDSTONE_ORES = EnumSet.of(Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE);
    private static final Set<Material> LAPIS_ORES = EnumSet.of(Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE);
    private static final Set<Material> COPPER_ORES = EnumSet.of(Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE);
    private static final Set<Material> DIAMOND_ORES = EnumSet.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE);
    private static final Set<Material> EMERALD_ORES = EnumSet.of(Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE);
    private static final Set<Material> SURVEYOR_ORES = EnumSet.of(
        Material.COAL_ORE,
        Material.DEEPSLATE_COAL_ORE,
        Material.COPPER_ORE,
        Material.DEEPSLATE_COPPER_ORE,
        Material.IRON_ORE,
        Material.DEEPSLATE_IRON_ORE,
        Material.GOLD_ORE,
        Material.DEEPSLATE_GOLD_ORE,
        Material.NETHER_GOLD_ORE,
        Material.REDSTONE_ORE,
        Material.DEEPSLATE_REDSTONE_ORE,
        Material.LAPIS_ORE,
        Material.DEEPSLATE_LAPIS_ORE,
        Material.DIAMOND_ORE,
        Material.DEEPSLATE_DIAMOND_ORE,
        Material.EMERALD_ORE,
        Material.DEEPSLATE_EMERALD_ORE,
        Material.NETHER_QUARTZ_ORE,
        Material.ANCIENT_DEBRIS
    );
    private static final long ADVANCED_PICKAXE_CONTEXT_TTL_MS = 2_000L;
    private static final int SURVEYOR_SCAN_RADIUS = 24;
    private static final long SURVEYOR_SCAN_COOLDOWN_MS = 20_000L;
    private static final double MENDERS_KIT_REPAIR_PERCENT = 0.35;
    private static final int PASSIVE_NIGHT_VISION_TICKS = 600;

    private final SMPCore plugin;
    private final NamespacedKey keyCustomToolId;
    private final NamespacedKey keyGrappleHookUses;
    private final NamespacedKey keyGrappleHookCooldownUntil;
    private final NamespacedKey keySurveyorLensCooldownUntil;
    private final NamespacedKey advancedPickaxeRecipeKey;
    private final NamespacedKey grappleHookRecipeKey;
    private final NamespacedKey spelunkerLanternRecipeKey;
    private final NamespacedKey surveyorsLensRecipeKey;
    private final NamespacedKey mendersKitRecipeKey;
    private final Map<BlockKey, AdvancedPickaxeContext> advancedPickaxeContexts = new ConcurrentHashMap<>();

    public CustomToolListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyCustomToolId = new NamespacedKey(plugin, "custom_tool_id");
        this.keyGrappleHookUses = new NamespacedKey(plugin, "grapple_hook_uses");
        this.keyGrappleHookCooldownUntil = new NamespacedKey(plugin, "grapple_hook_cooldown_until");
        this.keySurveyorLensCooldownUntil = new NamespacedKey(plugin, "surveyor_lens_cooldown_until");
        this.advancedPickaxeRecipeKey = new NamespacedKey(plugin, "advanced_pickaxe_recipe");
        this.grappleHookRecipeKey = new NamespacedKey(plugin, "grapple_hook_recipe");
        this.spelunkerLanternRecipeKey = new NamespacedKey(plugin, "spelunker_lantern_recipe");
        this.surveyorsLensRecipeKey = new NamespacedKey(plugin, "surveyors_lens_recipe");
        this.mendersKitRecipeKey = new NamespacedKey(plugin, "menders_kit_recipe");
        registerRecipes();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickHeldGear, 40L, 20L);

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                discoverRecipes(player);
                refreshPlayerCustomTools(player);
            }
        });
    }

    public void reloadConfig() {
        registerRecipes();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                discoverRecipes(player);
                refreshPlayerCustomTools(player);
            }
        });
    }

    public boolean isCustomTool(ItemStack item) {
        return customToolId(item) != null;
    }

    public String customToolId(ItemStack item) {
        return customToolIdInternal(item);
    }

    public boolean refreshCustomToolItem(ItemStack item) {
        if (stripLegacyAmethystPickaxe(item)) {
            return true;
        }
        if (customToolIdInternal(item) == null) {
            return false;
        }
        refreshCustomToolPresentation(item);
        return true;
    }

    public boolean isCustomToolId(String id) {
        return ADVANCED_PICKAXE_ID.equals(id)
            || GRAPPLE_HOOK_ID.equals(id)
            || SPELUNKER_LANTERN_ID.equals(id)
            || SURVEYORS_LENS_ID.equals(id)
            || MENDERS_KIT_ID.equals(id);
    }

    public List<String> craftableToolIds() {
        return List.of(
            ADVANCED_PICKAXE_ID,
            GRAPPLE_HOOK_ID,
            SPELUNKER_LANTERN_ID,
            SURVEYORS_LENS_ID,
            MENDERS_KIT_ID
        );
    }

    public String displayNameFor(String toolId) {
        return switch (toolId) {
            case ADVANCED_PICKAXE_ID -> "Prospector's Pick";
            case GRAPPLE_HOOK_ID -> "Skyhook";
            case SPELUNKER_LANTERN_ID -> "Spelunker's Lantern";
            case SURVEYORS_LENS_ID -> "Surveyor's Lens";
            case MENDERS_KIT_ID -> "Mender's Kit";
            default -> null;
        };
    }

    public Map<Material, Integer> recipeIngredients(String toolId) {
        return switch (toolId) {
            case ADVANCED_PICKAXE_ID -> Map.of(
                Material.IRON_INGOT, 3,
                Material.DIAMOND, 2,
                Material.STICK, 2
            );
            case GRAPPLE_HOOK_ID -> Map.of(
                Material.FISHING_ROD, 1,
                Material.IRON_INGOT, 1,
                Material.SLIME_BALL, 1
            );
            case SPELUNKER_LANTERN_ID -> Map.of(
                Material.LANTERN, 1,
                Material.COPPER_INGOT, 2,
                Material.GLOWSTONE_DUST, 2
            );
            case SURVEYORS_LENS_ID -> Map.of(
                Material.SPYGLASS, 1,
                Material.GLASS, 2,
                Material.AMETHYST_SHARD, 1,
                Material.REDSTONE, 1
            );
            case MENDERS_KIT_ID -> Map.of(
                Material.IRON_INGOT, 4,
                Material.PHANTOM_MEMBRANE, 1,
                Material.HONEYCOMB, 1,
                Material.LAPIS_LAZULI, 1
            );
            default -> Map.of();
        };
    }

    public ItemStack createCustomTool(String toolId) {
        return switch (toolId) {
            case ADVANCED_PICKAXE_ID -> createAdvancedPickaxe();
            case GRAPPLE_HOOK_ID -> createGrappleHook();
            case SPELUNKER_LANTERN_ID -> createSpelunkerLantern();
            case SURVEYORS_LENS_ID -> createSurveyorsLens();
            case MENDERS_KIT_ID -> createMendersKit();
            default -> new ItemStack(Material.BARRIER);
        };
    }

    public ItemStack createRecipePreview(String toolId) {
        return createCustomTool(toolId);
    }

    public ItemStack[] recipeMatrix(String toolId) {
        ItemStack[] matrix = new ItemStack[9];
        switch (toolId) {
            case ADVANCED_PICKAXE_ID -> {
                matrix[0] = new ItemStack(Material.IRON_INGOT);
                matrix[1] = new ItemStack(Material.IRON_INGOT);
                matrix[2] = new ItemStack(Material.IRON_INGOT);
                matrix[3] = new ItemStack(Material.DIAMOND);
                matrix[4] = new ItemStack(Material.STICK);
                matrix[5] = new ItemStack(Material.DIAMOND);
                matrix[7] = new ItemStack(Material.STICK);
            }
            case GRAPPLE_HOOK_ID -> {
                matrix[1] = new ItemStack(Material.IRON_INGOT);
                matrix[4] = new ItemStack(Material.FISHING_ROD);
                matrix[7] = new ItemStack(Material.SLIME_BALL);
            }
            case SPELUNKER_LANTERN_ID -> {
                matrix[1] = new ItemStack(Material.GLOWSTONE_DUST);
                matrix[3] = new ItemStack(Material.COPPER_INGOT);
                matrix[4] = new ItemStack(Material.LANTERN);
                matrix[5] = new ItemStack(Material.COPPER_INGOT);
                matrix[7] = new ItemStack(Material.GLOWSTONE_DUST);
            }
            case SURVEYORS_LENS_ID -> {
                matrix[1] = new ItemStack(Material.AMETHYST_SHARD);
                matrix[3] = new ItemStack(Material.GLASS);
                matrix[4] = new ItemStack(Material.SPYGLASS);
                matrix[5] = new ItemStack(Material.GLASS);
                matrix[7] = new ItemStack(Material.REDSTONE);
            }
            case MENDERS_KIT_ID -> {
                matrix[0] = new ItemStack(Material.IRON_INGOT);
                matrix[1] = new ItemStack(Material.PHANTOM_MEMBRANE);
                matrix[2] = new ItemStack(Material.IRON_INGOT);
                matrix[3] = new ItemStack(Material.IRON_INGOT);
                matrix[4] = new ItemStack(Material.HONEYCOMB);
                matrix[5] = new ItemStack(Material.IRON_INGOT);
                matrix[7] = new ItemStack(Material.LAPIS_LAZULI);
            }
            default -> {
                return matrix;
            }
        }
        return matrix;
    }

    public boolean isPlainVanillaItem(ItemStack item, Material material) {
        return item != null
            && item.getType() == material
            && !isCustomTool(item)
            && (plugin.getLegendaryListener() == null || !plugin.getLegendaryListener().isLegendaryItem(item));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        discoverRecipes(event.getPlayer());
        refreshPlayerCustomTools(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        if (customToolId(event.getItem()) == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> refreshCustomToolPresentation(event.getItem()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof org.bukkit.Keyed keyed)) return;

        if (advancedPickaxeRecipeKey.equals(keyed.getKey())) {
            Player viewer = event.getViewers().stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .findFirst()
                .orElse(null);
            boolean enchanterCraft = viewer != null
                && plugin.getSuperpowerManager() != null
                && plugin.getSuperpowerManager().hasPower(viewer, me.rique.smpcore.power.SuperpowerType.ENCHANTER);
            event.getInventory().setResult(
                plugin.getConfigManager().advancedPickaxeEnabled ? createAdvancedPickaxe(enchanterCraft) : null
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getInventory() instanceof CraftingInventory crafting)) return;
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof org.bukkit.Keyed keyed)) return;

        if (advancedPickaxeRecipeKey.equals(keyed.getKey()) && !plugin.getConfigManager().advancedPickaxeEnabled) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getFirstItem();
        ItemStack right = event.getInventory().getSecondItem();
        String leftId = customToolId(left);
        String rightId = customToolId(right);
        if (leftId == null && rightId == null) return;

        if (leftId != null && rightId != null && !leftId.equals(rightId)) {
            event.setResult(null);
            return;
        }

        String toolId = leftId != null ? leftId : rightId;
        ItemStack source = leftId != null ? left : right;
        ItemStack result = event.getResult();
        if (source == null || result == null || result.getType() == Material.AIR) return;
        event.setResult(preserveCustomToolResult(source, result, toolId));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (!(event.getInventory() instanceof GrindstoneInventory grindstone)) return;
        ItemStack top = grindstone.getUpperItem();
        ItemStack bottom = grindstone.getLowerItem();
        String topId = customToolId(top);
        String bottomId = customToolId(bottom);
        if (topId == null && bottomId == null) return;

        if (topId != null && bottomId != null && !topId.equals(bottomId)) {
            event.setResult(null);
            return;
        }

        String toolId = topId != null ? topId : bottomId;
        ItemStack source = topId != null ? top : bottom;
        ItemStack result = event.getResult();
        if (source == null) return;
        if (result == null || result.getType() == Material.AIR) {
            result = source.clone();
        }
        event.setResult(preserveCustomToolResult(source, result, toolId));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAdvancedPickaxeBreak(BlockBreakEvent event) {
        if (!plugin.getConfigManager().advancedPickaxeEnabled) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!ADVANCED_PICKAXE_ID.equals(customToolId(tool))) return;
        if (!isAdvancedPickaxeLuckySource(event.getBlock().getType())) return;
        if (plugin.getConfigManager().advancedPickaxeDisableBonusWithSilkTouch
            && tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0) {
            return;
        }
        ItemStack bonusDrop = rollAdvancedPickaxeLuckyBonus(event.getBlock().getType(), player);
        if (bonusDrop == null) return;

        rememberAdvancedPickaxeContext(
            event.getBlock().getLocation(),
            new AdvancedPickaxeContext(
                player.getUniqueId(),
                hasTelekinesis(tool),
                hasSmeltingTouch(tool),
                bonusDrop,
                System.currentTimeMillis() + ADVANCED_PICKAXE_CONTEXT_TTL_MS
            )
        );
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAdvancedPickaxeDrops(BlockDropItemEvent event) {
        if (!plugin.getConfigManager().advancedPickaxeEnabled) return;

        AdvancedPickaxeContext context = takeAdvancedPickaxeContext(event.getBlock().getLocation());
        if (context == null) {
            return;
        }

        Player player = Bukkit.getPlayer(context.playerId());
        if (player == null || !player.isOnline() || player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }

        List<ItemStack> bonusDrops = List.of(context.bonusDrop().clone());
        if (context.smeltingTouch() && plugin.getCustomEnchantListener() != null) {
            bonusDrops = plugin.getCustomEnchantListener().smeltMiningDrops(context.bonusDrop());
        }

        if (context.telekinesis() && plugin.getCustomEnchantListener() != null) {
            plugin.getCustomEnchantListener().deliverTelekinesisDrops(player, bonusDrops, event.getBlock().getLocation());
            return;
        }

        World world = event.getBlock().getWorld();
        for (ItemStack bonus : bonusDrops) {
            Item dropped = world.dropItemNaturally(event.getBlock().getLocation().add(0.5, 0.5, 0.5), bonus);
            dropped.setPickupDelay(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGrappleHookUse(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }
        if (!GRAPPLE_HOOK_ID.equals(customToolId(player.getInventory().getItemInMainHand()))) {
            return;
        }

        switch (event.getState()) {
            case IN_GROUND, REEL_IN, CAUGHT_ENTITY -> {
            }
            default -> {
                return;
            }
        }

        long now = System.currentTimeMillis();
        long readyAt = player.getPersistentDataContainer().getOrDefault(
            keyGrappleHookCooldownUntil,
            PersistentDataType.LONG,
            0L
        );
        if (readyAt > now) {
            long remainingMillis = readyAt - now;
            long remainingSeconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
            player.sendMessage(MessageUtil.warn(
                "Skyhook cooldown: <white>" + remainingSeconds + "s</white>."
            ));
            return;
        }

        Location hookLocation = event.getHook().getLocation();
        Vector pull = hookLocation.toVector().subtract(player.getLocation().toVector());
        double distance = pull.length();
        if (distance < 1.25) {
            return;
        }

        if (!consumeSkyhookUse(player)) {
            return;
        }

        long cooldownMillis = Math.max(0L, plugin.getConfigManager().grappleHookCooldownSeconds * 1000L);
        if (cooldownMillis > 0L) {
            player.getPersistentDataContainer().set(
                keyGrappleHookCooldownUntil,
                PersistentDataType.LONG,
                now + cooldownMillis
            );
        } else {
            player.getPersistentDataContainer().remove(keyGrappleHookCooldownUntil);
        }

        Vector velocity = pull.normalize().multiply(Math.min(1.85, 0.60 + (distance * 0.08)));
        velocity.setY(Math.max(0.35, Math.min(1.05, velocity.getY() + 0.35)));
        player.setFallDistance(0.0f);
        player.setVelocity(velocity);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, 0.7f, 1.25f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCustomGearInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) {
            return;
        }

        ItemStack item = event.getItem();
        String toolId = customToolId(item);
        if (toolId == null) {
            return;
        }

        switch (toolId) {
            case SURVEYORS_LENS_ID -> {
                event.setCancelled(true);
                scanNearbyOres(event.getPlayer());
            }
            case MENDERS_KIT_ID -> {
                event.setCancelled(true);
                useMendersKit(event.getPlayer(), hand, item);
            }
            case SPELUNKER_LANTERN_ID -> {
                if (action == Action.RIGHT_CLICK_BLOCK) {
                    event.setCancelled(true);
                }
            }
            default -> {
            }
        }
    }

    private void registerRecipes() {
        Bukkit.removeRecipe(advancedPickaxeRecipeKey);
        Bukkit.removeRecipe(grappleHookRecipeKey);
        Bukkit.removeRecipe(spelunkerLanternRecipeKey);
        Bukkit.removeRecipe(surveyorsLensRecipeKey);
        Bukkit.removeRecipe(mendersKitRecipeKey);

        ShapedRecipe advanced = new ShapedRecipe(advancedPickaxeRecipeKey, createAdvancedPickaxe());
        advanced.shape("III", "DSD", " S ");
        advanced.setIngredient('I', Material.IRON_INGOT);
        advanced.setIngredient('D', Material.DIAMOND);
        advanced.setIngredient('S', Material.STICK);
        advanced.setGroup("smpcore_custom_tools");
        Bukkit.addRecipe(advanced);

        ShapedRecipe grappleHook = new ShapedRecipe(grappleHookRecipeKey, createGrappleHook());
        grappleHook.shape(" I ", " R ", " S ");
        grappleHook.setIngredient('I', Material.IRON_INGOT);
        grappleHook.setIngredient('R', Material.FISHING_ROD);
        grappleHook.setIngredient('S', Material.SLIME_BALL);
        grappleHook.setGroup("smpcore_custom_tools");
        Bukkit.addRecipe(grappleHook);

        ShapedRecipe lantern = new ShapedRecipe(spelunkerLanternRecipeKey, createSpelunkerLantern());
        lantern.shape(" G ", "CLC", " G ");
        lantern.setIngredient('G', Material.GLOWSTONE_DUST);
        lantern.setIngredient('C', Material.COPPER_INGOT);
        lantern.setIngredient('L', Material.LANTERN);
        lantern.setGroup("smpcore_custom_tools");
        Bukkit.addRecipe(lantern);

        ShapedRecipe lens = new ShapedRecipe(surveyorsLensRecipeKey, createSurveyorsLens());
        lens.shape(" A ", "GSG", " R ");
        lens.setIngredient('A', Material.AMETHYST_SHARD);
        lens.setIngredient('G', Material.GLASS);
        lens.setIngredient('S', Material.SPYGLASS);
        lens.setIngredient('R', Material.REDSTONE);
        lens.setGroup("smpcore_custom_tools");
        Bukkit.addRecipe(lens);

        ShapedRecipe kit = new ShapedRecipe(mendersKitRecipeKey, createMendersKit());
        kit.shape("IPI", "IHI", " L ");
        kit.setIngredient('I', Material.IRON_INGOT);
        kit.setIngredient('P', Material.PHANTOM_MEMBRANE);
        kit.setIngredient('H', Material.HONEYCOMB);
        kit.setIngredient('L', Material.LAPIS_LAZULI);
        kit.setGroup("smpcore_custom_tools");
        Bukkit.addRecipe(kit);
    }

    private void discoverRecipes(Player player) {
        player.discoverRecipe(advancedPickaxeRecipeKey);
        player.discoverRecipe(grappleHookRecipeKey);
        player.discoverRecipe(spelunkerLanternRecipeKey);
        player.discoverRecipe(surveyorsLensRecipeKey);
        player.discoverRecipe(mendersKitRecipeKey);
    }

    private boolean hasTelekinesis(ItemStack tool) {
        return plugin.getCustomEnchantListener() != null
            && plugin.getCustomEnchantListener().hasTelekinesisEnchant(tool);
    }

    private boolean hasSmeltingTouch(ItemStack tool) {
        return plugin.getCustomEnchantListener() != null
            && plugin.getCustomEnchantListener().hasSmeltingTouchEnchant(tool);
    }

    private ItemStack createAdvancedPickaxe() {
        return createAdvancedPickaxe(false);
    }

    private ItemStack createAdvancedPickaxe(boolean enchanterCrafted) {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        applyCustomToolState(meta, ADVANCED_PICKAXE_ID);
        if (enchanterCrafted) {
            meta.addEnchant(Enchantment.FORTUNE, 3, true);
        }
        applyCustomToolPresentation(meta, ADVANCED_PICKAXE_ID, item.getType());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGrappleHook() {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        applyCustomToolState(meta, GRAPPLE_HOOK_ID);
        meta.getPersistentDataContainer().set(
            keyGrappleHookUses,
            PersistentDataType.INTEGER,
            plugin.getConfigManager().grappleHookMaxUses
        );
        applyCustomToolPresentation(meta, GRAPPLE_HOOK_ID, item.getType());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSpelunkerLantern() {
        ItemStack item = new ItemStack(Material.LANTERN);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        applyCustomToolState(meta, SPELUNKER_LANTERN_ID);
        applyCustomToolPresentation(meta, SPELUNKER_LANTERN_ID, item.getType());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSurveyorsLens() {
        ItemStack item = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        applyCustomToolState(meta, SURVEYORS_LENS_ID);
        applyCustomToolPresentation(meta, SURVEYORS_LENS_ID, item.getType());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMendersKit() {
        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        applyCustomToolState(meta, MENDERS_KIT_ID);
        applyCustomToolPresentation(meta, MENDERS_KIT_ID, item.getType());
        item.setItemMeta(meta);
        return item;
    }

    private void applyCustomToolState(ItemMeta meta, String toolId) {
        meta.getPersistentDataContainer().set(keyCustomToolId, PersistentDataType.STRING, toolId);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        CustomLoreUtil.applyStyledItemFlags(meta);
    }

    private String customToolIdInternal(ItemStack item) {
        String rawId = rawCustomToolId(item);
        return isCustomToolId(rawId) ? rawId : null;
    }

    private String rawCustomToolId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(keyCustomToolId, PersistentDataType.STRING);
    }

    private ItemStack preserveCustomToolResult(ItemStack source, ItemStack result, String toolId) {
        ItemStack updated = result.clone();
        ItemMeta sourceMeta = source.getItemMeta();
        ItemMeta resultMeta = updated.getItemMeta();
        if (sourceMeta == null || resultMeta == null) return updated;

        applyCustomToolState(resultMeta, toolId);
        copyManagedToolState(sourceMeta, resultMeta, toolId);
        AwakeningTableListener awakening = plugin.getAwakeningTableListener();
        if (awakening != null) {
            awakening.copyAwakeningState(sourceMeta, resultMeta);
        }
        applyCustomToolPresentation(resultMeta, toolId, updated.getType());
        updated.setItemMeta(resultMeta);
        return updated;
    }

    private void refreshCustomToolPresentation(ItemStack item) {
        if (stripLegacyAmethystPickaxe(item)) {
            return;
        }

        String toolId = customToolId(item);
        if (toolId == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        applyCustomToolPresentation(meta, toolId, item.getType());
        item.setItemMeta(meta);
    }

    private void refreshPlayerCustomTools(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (stripLegacyAmethystPickaxe(item)) {
                player.getInventory().setItem(slot, item);
                continue;
            }
            if (customToolId(item) == null) {
                continue;
            }
            refreshCustomToolPresentation(item);
            player.getInventory().setItem(slot, item);
        }
    }

    private void applyCustomToolPresentation(ItemMeta meta, String toolId, Material material) {
        meta.setItemModel(null);
        Component baseDisplayName = null;
        switch (toolId) {
            case ADVANCED_PICKAXE_ID -> {
                baseDisplayName = CustomLoreUtil.displayName(CustomLoreUtil.Rarity.RARE, "Prospector's Pick");
                meta.displayName(baseDisplayName);
                meta.lore(CustomLoreUtil.buildStyledLore(
                    meta,
                    material,
                    CustomLoreUtil.Rarity.RARE.label(),
                    "PICKAXE",
                    List.of("<gray>A tuned pick that coaxes richer veins from stone.</gray>"),
                    List.of(CustomLoreUtil.section(
                        "Item Ability",
                        "Lucky Vein",
                        "<gray>Mining an ore has a <white>" + formatPercent(plugin.getConfigManager().advancedPickaxeLuckyDropChance) + "</white> chance to roll a random different ore.</gray>",
                        "<gray>Coal and copper are guaranteed-weight rolls. Iron, redstone, and lapis are common.</gray>",
                        "<gray>Gold is rarer. Diamond and emerald are the rarest lucky rolls.</gray>",
                        "<gray>Lucky ore drops come in stacks of <white>1 to 3</white>.</gray>",
                        "<gray>Arcanists craft this with <white>Fortune III</white>.</gray>"
                    ))
                ));
            }
            case GRAPPLE_HOOK_ID -> {
                baseDisplayName = CustomLoreUtil.displayName(CustomLoreUtil.Rarity.RARE, "Skyhook");
                int uses = normalizeGrappleHookUses(meta);
                meta.displayName(baseDisplayName);
                meta.lore(CustomLoreUtil.buildStyledLore(
                    meta,
                    material,
                    CustomLoreUtil.Rarity.RARE.label(),
                    "HOOK",
                    List.of("<gray>A reinforced line launcher built for sharp movement.</gray>"),
                    List.of(CustomLoreUtil.section(
                        "Right Click",
                        "Sky Pull",
                        "<gray>Cast and reel it in to yank yourself toward the hook.</gray>",
                        "<gray>Cooldown: <white>" + plugin.getConfigManager().grappleHookCooldownSeconds + "s</white>.</gray>",
                        "<gray>Uses Remaining: <white>" + uses + "</white></gray>",
                        "<gray>Built with simple parts for easy early crafting.</gray>"
                    ))
                ));
            }
            case SPELUNKER_LANTERN_ID -> {
                baseDisplayName = CustomLoreUtil.displayName(CustomLoreUtil.Rarity.UNCOMMON, "Spelunker's Lantern");
                meta.displayName(baseDisplayName);
                meta.lore(CustomLoreUtil.buildStyledLore(
                    meta,
                    material,
                    CustomLoreUtil.Rarity.UNCOMMON.label(),
                    "HELD GEAR",
                    List.of("<gray>A copper-caged lamp that burns steady underground.</gray>"),
                    List.of(CustomLoreUtil.section(
                        "Held Passive",
                        "Caveglow",
                        "<gray>Hold in either hand to keep <white>Night Vision</white> active.</gray>",
                        "<gray>Also grants a light <white>Haste I</white> pulse while held.</gray>",
                        "<gray>Cannot be placed as a normal lantern.</gray>"
                    ))
                ));
            }
            case SURVEYORS_LENS_ID -> {
                baseDisplayName = CustomLoreUtil.displayName(CustomLoreUtil.Rarity.RARE, "Surveyor's Lens");
                meta.displayName(baseDisplayName);
                meta.lore(CustomLoreUtil.buildStyledLore(
                    meta,
                    material,
                    CustomLoreUtil.Rarity.RARE.label(),
                    "SCANNER",
                    List.of("<gray>A tuned spyglass that listens for mineral seams.</gray>"),
                    List.of(CustomLoreUtil.section(
                        "Right Click",
                        "Ore Echo",
                        "<gray>Scans loaded blocks within <white>" + SURVEYOR_SCAN_RADIUS + "</white> blocks.</gray>",
                        "<gray>Reports ore totals and the nearest valuable echo.</gray>",
                        "<gray>Cooldown: <white>" + (SURVEYOR_SCAN_COOLDOWN_MS / 1000L) + "s</white>.</gray>"
                    ))
                ));
            }
            case MENDERS_KIT_ID -> {
                baseDisplayName = CustomLoreUtil.displayName(CustomLoreUtil.Rarity.UNCOMMON, "Mender's Kit");
                meta.displayName(baseDisplayName);
                meta.lore(CustomLoreUtil.buildStyledLore(
                    meta,
                    material,
                    CustomLoreUtil.Rarity.UNCOMMON.label(),
                    "CONSUMABLE GEAR",
                    List.of("<gray>A field repair bundle for gear that cannot wait.</gray>"),
                    List.of(CustomLoreUtil.section(
                        "Right Click",
                        "Patchwork",
                        "<gray>Repairs your most damaged carried or worn item by <white>35%</white>.</gray>",
                        "<gray>Only consumes itself after a repair succeeds.</gray>",
                        "<gray>Will never repair another Mender's Kit.</gray>"
                    ))
                ));
            }
            default -> {
            }
        }
        if (plugin.getCustomEnchantListener() != null) {
            plugin.getCustomEnchantListener().applyManagedEnchantLore(meta);
        }
        AwakeningTableListener awakening = plugin.getAwakeningTableListener();
        if (awakening != null && baseDisplayName != null) {
            awakening.applyManagedItemState(meta, material, baseDisplayName, false);
        }
    }

    private boolean stripLegacyAmethystPickaxe(ItemStack item) {
        if (!LEGACY_AMETHYST_PICKAXE_ID.equals(rawCustomToolId(item))) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(keyCustomToolId);
        meta.setItemModel(null);
        meta.displayName(null);
        meta.lore(null);
        meta.removeItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return true;
    }

    private boolean isAdvancedPickaxeLuckySource(Material broken) {
        return bonusFamilyOf(broken) != null
            || broken == Material.NETHER_QUARTZ_ORE
            || broken == Material.ANCIENT_DEBRIS;
    }

    private ItemStack rollAdvancedPickaxeLuckyBonus(Material broken, Player player) {
        if (ThreadLocalRandom.current().nextDouble() >= plugin.getConfigManager().advancedPickaxeLuckyDropChance) {
            return null;
        }

        AdvancedPickaxeBonusFamily sourceFamily = bonusFamilyOf(broken);
        double totalWeight = 0.0;
        for (AdvancedPickaxeBonusFamily family : AdvancedPickaxeBonusFamily.values()) {
            if (family == sourceFamily) continue;
            totalWeight += bonusWeight(family, player);
        }
        if (totalWeight <= 0.0) {
            return null;
        }

        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        AdvancedPickaxeBonusFamily fallback = null;
        for (AdvancedPickaxeBonusFamily family : AdvancedPickaxeBonusFamily.values()) {
            if (family == sourceFamily) continue;
            double weight = bonusWeight(family, player);
            if (weight <= 0.0) continue;
            fallback = family;
            roll -= weight;
            if (roll <= 0.0) {
                return new ItemStack(family.drop(), ThreadLocalRandom.current().nextInt(1, 4));
            }
        }

        return fallback == null ? null : new ItemStack(fallback.drop(), ThreadLocalRandom.current().nextInt(1, 4));
    }

    private void copyManagedToolState(ItemMeta sourceMeta, ItemMeta targetMeta, String toolId) {
        if (!GRAPPLE_HOOK_ID.equals(toolId)) {
            return;
        }
        int uses = normalizeGrappleHookUses(sourceMeta);
        targetMeta.getPersistentDataContainer().set(keyGrappleHookUses, PersistentDataType.INTEGER, uses);
    }

    private int normalizeGrappleHookUses(ItemMeta meta) {
        int maxUses = Math.max(1, plugin.getConfigManager().grappleHookMaxUses);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int uses = pdc.getOrDefault(keyGrappleHookUses, PersistentDataType.INTEGER, maxUses);
        uses = Math.max(0, Math.min(maxUses, uses));
        pdc.set(keyGrappleHookUses, PersistentDataType.INTEGER, uses);
        return uses;
    }

    private boolean consumeSkyhookUse(Player player) {
        ItemStack skyhook = player.getInventory().getItemInMainHand();
        if (!GRAPPLE_HOOK_ID.equals(customToolId(skyhook))) {
            return false;
        }

        ItemMeta meta = skyhook.getItemMeta();
        if (meta == null) {
            return false;
        }

        int uses = normalizeGrappleHookUses(meta);
        if (uses <= 0) {
            player.getInventory().setItemInMainHand(null);
            player.sendMessage(MessageUtil.warn("Your Skyhook has no uses left."));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 0.9f);
            return false;
        }

        uses--;
        if (uses <= 0) {
            player.getInventory().setItemInMainHand(null);
            player.sendMessage(MessageUtil.warn("Your Skyhook snapped apart."));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.9f, 0.8f);
            return true;
        }

        meta.getPersistentDataContainer().set(keyGrappleHookUses, PersistentDataType.INTEGER, uses);
        applyCustomToolPresentation(meta, GRAPPLE_HOOK_ID, skyhook.getType());
        skyhook.setItemMeta(meta);
        player.getInventory().setItemInMainHand(skyhook);
        return true;
    }

    private void tickHeldGear() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!hasHeldCustomTool(player, SPELUNKER_LANTERN_ID)) {
                continue;
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PASSIVE_NIGHT_VISION_TICKS, 0, true, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 120, 0, true, false, false));
        }
    }

    private boolean hasHeldCustomTool(Player player, String toolId) {
        return toolId.equals(customToolId(player.getInventory().getItemInMainHand()))
            || toolId.equals(customToolId(player.getInventory().getItemInOffHand()));
    }

    private void scanNearbyOres(Player player) {
        long now = System.currentTimeMillis();
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        long readyAt = pdc.getOrDefault(keySurveyorLensCooldownUntil, PersistentDataType.LONG, 0L);
        if (readyAt > now) {
            long remainingSeconds = Math.max(1L, ((readyAt - now) + 999L) / 1000L);
            player.sendMessage(MessageUtil.warn("Surveyor's Lens cooldown: <white>" + remainingSeconds + "s</white>."));
            return;
        }

        Location origin = player.getLocation();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }

        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        Block nearestValuable = null;
        double nearestValuableDistanceSq = Double.MAX_VALUE;
        int radiusSq = SURVEYOR_SCAN_RADIUS * SURVEYOR_SCAN_RADIUS;
        int centerX = origin.getBlockX();
        int centerY = origin.getBlockY();
        int centerZ = origin.getBlockZ();
        int minY = Math.max(world.getMinHeight(), centerY - SURVEYOR_SCAN_RADIUS);
        int maxY = Math.min(world.getMaxHeight() - 1, centerY + SURVEYOR_SCAN_RADIUS);

        for (int x = centerX - SURVEYOR_SCAN_RADIUS; x <= centerX + SURVEYOR_SCAN_RADIUS; x++) {
            for (int z = centerZ - SURVEYOR_SCAN_RADIUS; z <= centerZ + SURVEYOR_SCAN_RADIUS; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    int dx = x - centerX;
                    int dy = y - centerY;
                    int dz = z - centerZ;
                    if ((dx * dx) + (dy * dy) + (dz * dz) > radiusSq) {
                        continue;
                    }
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (!SURVEYOR_ORES.contains(type)) {
                        continue;
                    }
                    counts.merge(oreLabel(type), 1, Integer::sum);
                    if (isValuableSurveyorOre(type)) {
                        double distanceSq = block.getLocation().add(0.5, 0.5, 0.5).distanceSquared(origin);
                        if (distanceSq < nearestValuableDistanceSq) {
                            nearestValuableDistanceSq = distanceSq;
                            nearestValuable = block;
                        }
                    }
                }
            }
        }

        pdc.set(keySurveyorLensCooldownUntil, PersistentDataType.LONG, now + SURVEYOR_SCAN_COOLDOWN_MS);
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) {
            player.sendMessage(MessageUtil.warn("The lens found no ore echoes nearby."));
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 0.7f);
            return;
        }

        player.sendMessage(MessageUtil.success(
            "Surveyor's Lens found <white>" + total + "</white> ore blocks within <white>" + SURVEYOR_SCAN_RADIUS + "</white> blocks."
        ));
        player.sendMessage(MessageUtil.info(formatOreBreakdown(counts)));
        if (nearestValuable != null) {
            player.sendMessage(MessageUtil.info(describeNearestValuable(origin, nearestValuable)));
        } else {
            player.sendMessage(MessageUtil.info("<gray>No diamond, emerald, or ancient debris echoes were close enough.</gray>"));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.85f, 1.25f);
    }

    private void useMendersKit(Player player, EquipmentSlot hand, ItemStack kit) {
        RepairTarget target = findMostDamagedRepairTarget(player);
        if (target == null) {
            player.sendMessage(MessageUtil.warn("Nothing in your inventory needs the Mender's Kit."));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.45f, 1.35f);
            return;
        }

        if (!repairItem(target.item(), MENDERS_KIT_REPAIR_PERCENT)) {
            player.sendMessage(MessageUtil.warn("The kit could not repair that item."));
            return;
        }

        applyRepairTarget(player, target);
        consumeOne(player, hand, kit);
        player.sendMessage(MessageUtil.success("Patched <white>" + target.label() + "</white>."));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.75f, 1.2f);
    }

    private RepairTarget findMostDamagedRepairTarget(Player player) {
        RepairTarget best = null;
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            best = betterRepairTarget(best, repairTarget(storage[slot], slot, null, "inventory item"));
        }
        best = betterRepairTarget(best, repairTarget(player.getInventory().getItemInOffHand(), -1, EquipmentSlot.OFF_HAND, "offhand item"));
        best = betterRepairTarget(best, repairTarget(player.getInventory().getHelmet(), -1, EquipmentSlot.HEAD, "helmet"));
        best = betterRepairTarget(best, repairTarget(player.getInventory().getChestplate(), -1, EquipmentSlot.CHEST, "chestplate"));
        best = betterRepairTarget(best, repairTarget(player.getInventory().getLeggings(), -1, EquipmentSlot.LEGS, "leggings"));
        best = betterRepairTarget(best, repairTarget(player.getInventory().getBoots(), -1, EquipmentSlot.FEET, "boots"));
        return best;
    }

    private RepairTarget repairTarget(ItemStack item, int storageSlot, EquipmentSlot equipmentSlot, String label) {
        double ratio = damageRatio(item);
        return ratio <= 0.0 ? null : new RepairTarget(item, storageSlot, equipmentSlot, label, ratio);
    }

    private RepairTarget betterRepairTarget(RepairTarget current, RepairTarget candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.damageRatio() > current.damageRatio() ? candidate : current;
    }

    private double damageRatio(ItemStack item) {
        if (item == null || item.getType().isAir() || MENDERS_KIT_ID.equals(customToolId(item))) {
            return 0.0;
        }
        if (!(item.getItemMeta() instanceof Damageable damageable)) {
            return 0.0;
        }
        int maxDamage = maxDamage(item, damageable);
        if (maxDamage <= 0 || damageable.getDamage() <= 0) {
            return 0.0;
        }
        return Math.min(1.0, damageable.getDamage() / (double) maxDamage);
    }

    private boolean repairItem(ItemStack item, double percentOfMax) {
        if (item == null || item.getType().isAir() || !(item.getItemMeta() instanceof Damageable damageable)) {
            return false;
        }
        int maxDamage = maxDamage(item, damageable);
        if (maxDamage <= 0 || damageable.getDamage() <= 0) {
            return false;
        }
        int repair = Math.max(1, (int) Math.round(maxDamage * Math.max(0.01, percentOfMax)));
        damageable.setDamage(Math.max(0, damageable.getDamage() - repair));
        item.setItemMeta(damageable);
        return true;
    }

    private int maxDamage(ItemStack item, Damageable damageable) {
        int maxDamage = damageable.hasMaxDamage() ? damageable.getMaxDamage() : item.getType().getMaxDurability();
        return Math.max(0, maxDamage);
    }

    private void applyRepairTarget(Player player, RepairTarget target) {
        if (target.storageSlot() >= 0) {
            player.getInventory().setItem(target.storageSlot(), target.item());
            return;
        }
        switch (target.equipmentSlot()) {
            case OFF_HAND -> player.getInventory().setItemInOffHand(target.item());
            case HEAD -> player.getInventory().setHelmet(target.item());
            case CHEST -> player.getInventory().setChestplate(target.item());
            case LEGS -> player.getInventory().setLeggings(target.item());
            case FEET -> player.getInventory().setBoots(target.item());
            default -> {
            }
        }
    }

    private void consumeOne(Player player, EquipmentSlot hand, ItemStack item) {
        ItemStack next = item.clone();
        next.setAmount(next.getAmount() - 1);
        if (next.getAmount() <= 0) {
            next = null;
        }
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(next);
        } else {
            player.getInventory().setItemInMainHand(next);
        }
    }

    private String formatOreBreakdown(Map<String, Integer> counts) {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((left, right) -> {
            int byCount = Integer.compare(right.getValue(), left.getValue());
            return byCount != 0 ? byCount : left.getKey().compareToIgnoreCase(right.getKey());
        });
        List<String> parts = new ArrayList<>();
        int limit = Math.min(5, sorted.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Integer> entry = sorted.get(i);
            parts.add("<white>" + entry.getKey() + "</white> x" + entry.getValue());
        }
        return "<gray>Strongest echoes: " + String.join("<gray>, </gray>", parts) + "</gray>";
    }

    private String describeNearestValuable(Location origin, Block block) {
        Location blockCenter = block.getLocation().add(0.5, 0.5, 0.5);
        Vector delta = blockCenter.toVector().subtract(origin.toVector());
        String vertical = delta.getY() > 3.0 ? "above" : delta.getY() < -3.0 ? "below" : "level";
        int distance = Math.max(1, (int) Math.round(delta.length()));
        return "<gray>Nearest valuable echo: <white>" + oreLabel(block.getType()) + "</white>, "
            + cardinalDirection(delta) + " and " + vertical + ", about <white>" + distance + "</white> blocks away.</gray>";
    }

    private boolean isValuableSurveyorOre(Material type) {
        return DIAMOND_ORES.contains(type) || EMERALD_ORES.contains(type) || type == Material.ANCIENT_DEBRIS;
    }

    private String oreLabel(Material type) {
        if (COAL_ORES.contains(type)) return "Coal";
        if (COPPER_ORES.contains(type)) return "Copper";
        if (IRON_ORES.contains(type)) return "Iron";
        if (GOLD_ORES.contains(type)) return "Gold";
        if (REDSTONE_ORES.contains(type)) return "Redstone";
        if (LAPIS_ORES.contains(type)) return "Lapis";
        if (DIAMOND_ORES.contains(type)) return "Diamond";
        if (EMERALD_ORES.contains(type)) return "Emerald";
        if (type == Material.NETHER_QUARTZ_ORE) return "Quartz";
        if (type == Material.ANCIENT_DEBRIS) return "Ancient Debris";
        return "Ore";
    }

    private String cardinalDirection(Vector delta) {
        Vector flat = delta.clone().setY(0.0);
        if (flat.lengthSquared() <= 1.0E-6) {
            return "right here";
        }
        double angle = Math.atan2(-flat.getX(), flat.getZ());
        double degrees = Math.toDegrees(angle);
        if (degrees < 0) degrees += 360.0;
        String[] directions = {"south", "southwest", "west", "northwest", "north", "northeast", "east", "southeast"};
        return directions[(int) Math.round(degrees / 45.0) % directions.length];
    }

    private AdvancedPickaxeBonusFamily bonusFamilyOf(Material broken) {
        if (COAL_ORES.contains(broken)) return AdvancedPickaxeBonusFamily.COAL;
        if (IRON_ORES.contains(broken)) return AdvancedPickaxeBonusFamily.IRON;
        if (REDSTONE_ORES.contains(broken)) return AdvancedPickaxeBonusFamily.REDSTONE;
        if (LAPIS_ORES.contains(broken)) return AdvancedPickaxeBonusFamily.LAPIS;
        if (COPPER_ORES.contains(broken)) return AdvancedPickaxeBonusFamily.COPPER;
        if (DIAMOND_ORES.contains(broken)) return AdvancedPickaxeBonusFamily.DIAMOND;
        if (EMERALD_ORES.contains(broken)) return AdvancedPickaxeBonusFamily.EMERALD;
        if (GOLD_ORES.contains(broken)) return AdvancedPickaxeBonusFamily.GOLD;
        return null;
    }

    private double bonusWeight(AdvancedPickaxeBonusFamily family, Player player) {
        boolean enchanter = player != null
            && plugin.getSuperpowerManager() != null
            && plugin.getSuperpowerManager().hasPower(player, me.rique.smpcore.power.SuperpowerType.ENCHANTER);
        return switch (family) {
            case COAL -> plugin.getConfigManager().advancedPickaxeCoalChance;
            case IRON -> plugin.getConfigManager().advancedPickaxeIronChance;
            case REDSTONE -> plugin.getConfigManager().advancedPickaxeRedstoneChance;
            case LAPIS -> plugin.getConfigManager().advancedPickaxeLapisChance;
            case COPPER -> plugin.getConfigManager().advancedPickaxeCopperChance;
            case DIAMOND -> enchanter ? (1.0 / 3.0) : plugin.getConfigManager().advancedPickaxeDiamondChance;
            case EMERALD -> plugin.getConfigManager().advancedPickaxeEmeraldChance;
            case GOLD -> plugin.getConfigManager().advancedPickaxeGoldChance;
        };
    }

    private String formatPercent(double chance) {
        double percent = chance * 100.0;
        if (Math.abs(percent - Math.rint(percent)) < 0.0001) {
            return Math.round(percent) + "%";
        }
        return String.format(java.util.Locale.US, "%.1f%%", percent);
    }

    private void rememberAdvancedPickaxeContext(Location location, AdvancedPickaxeContext context) {
        cleanupExpiredAdvancedPickaxeContexts();
        advancedPickaxeContexts.put(blockKey(location), context);
    }

    private AdvancedPickaxeContext takeAdvancedPickaxeContext(Location location) {
        cleanupExpiredAdvancedPickaxeContexts();
        return advancedPickaxeContexts.remove(blockKey(location));
    }

    private void cleanupExpiredAdvancedPickaxeContexts() {
        long now = System.currentTimeMillis();
        advancedPickaxeContexts.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    private BlockKey blockKey(Location location) {
        return new BlockKey(
            location.getWorld().getUID(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ()
        );
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {}
    private record RepairTarget(ItemStack item, int storageSlot, EquipmentSlot equipmentSlot, String label, double damageRatio) {}
    private enum AdvancedPickaxeBonusFamily {
        COAL(Material.COAL),
        IRON(Material.RAW_IRON),
        REDSTONE(Material.REDSTONE),
        LAPIS(Material.LAPIS_LAZULI),
        COPPER(Material.RAW_COPPER),
        DIAMOND(Material.DIAMOND),
        EMERALD(Material.EMERALD),
        GOLD(Material.RAW_GOLD);

        private final Material drop;

        AdvancedPickaxeBonusFamily(Material drop) {
            this.drop = drop;
        }

        private Material drop() {
            return drop;
        }
    }

    private record AdvancedPickaxeContext(UUID playerId, boolean telekinesis, boolean smeltingTouch, ItemStack bonusDrop, long expiresAt) {}
}
