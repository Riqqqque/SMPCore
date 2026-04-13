package me.rique.smpcore.item;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.awakening.AwakeningTableListener;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

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

    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String ADVANCED_PICKAXE_ID = "advanced_pickaxe";
    public static final String GRAPPLE_HOOK_ID = "grapple_hook";
    private static final String LEGACY_AMETHYST_PICKAXE_ID = "amethyst_pickaxe";

    private static final Set<Material> GOLD_ORES = EnumSet.of(Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE);
    private static final Set<Material> COAL_ORES = EnumSet.of(Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE);
    private static final Set<Material> IRON_ORES = EnumSet.of(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE);
    private static final Set<Material> REDSTONE_ORES = EnumSet.of(Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE);
    private static final Set<Material> LAPIS_ORES = EnumSet.of(Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE);
    private static final Set<Material> COPPER_ORES = EnumSet.of(Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE);
    private static final Set<Material> DIAMOND_ORES = EnumSet.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE);
    private static final Set<Material> EMERALD_ORES = EnumSet.of(Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE);
    private static final long ADVANCED_PICKAXE_CONTEXT_TTL_MS = 2_000L;

    private final SMPCore plugin;
    private final NamespacedKey keyCustomToolId;
    private final NamespacedKey advancedPickaxeRecipeKey;
    private final NamespacedKey grappleHookRecipeKey;
    private final Map<BlockKey, AdvancedPickaxeContext> advancedPickaxeContexts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> grappleHookCooldowns = new ConcurrentHashMap<>();

    public CustomToolListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyCustomToolId = new NamespacedKey(plugin, "custom_tool_id");
        this.advancedPickaxeRecipeKey = new NamespacedKey(plugin, "advanced_pickaxe_recipe");
        this.grappleHookRecipeKey = new NamespacedKey(plugin, "grapple_hook_recipe");
        registerRecipes();

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

    public boolean refreshCustomToolItem(ItemStack item) {
        if (stripLegacyAmethystPickaxe(item)) {
            return true;
        }
        if (customToolId(item) == null) {
            return false;
        }
        refreshCustomToolPresentation(item);
        return true;
    }

    public boolean isCustomToolId(String id) {
        return ADVANCED_PICKAXE_ID.equals(id) || GRAPPLE_HOOK_ID.equals(id);
    }

    public List<String> craftableToolIds() {
        return List.of(ADVANCED_PICKAXE_ID, GRAPPLE_HOOK_ID);
    }

    public String displayNameFor(String toolId) {
        return switch (toolId) {
            case ADVANCED_PICKAXE_ID -> "Prospector's Pick";
            case GRAPPLE_HOOK_ID -> "Skyhook";
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
            default -> Map.of();
        };
    }

    public ItemStack createCustomTool(String toolId) {
        return switch (toolId) {
            case ADVANCED_PICKAXE_ID -> createAdvancedPickaxe();
            case GRAPPLE_HOOK_ID -> createGrappleHook();
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
        long readyAt = grappleHookCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) {
            long remainingMillis = readyAt - now;
            long remainingSeconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
            player.sendMessage(MessageUtil.warn(
                "Skyhook cooldown: <white>" + remainingSeconds + "s</white>."
            ));
            return;
        }

        long cooldownMillis = Math.max(0L, plugin.getConfigManager().grappleHookCooldownSeconds * 1000L);
        if (cooldownMillis > 0L) {
            grappleHookCooldowns.put(player.getUniqueId(), now + cooldownMillis);
        } else {
            grappleHookCooldowns.remove(player.getUniqueId());
        }

        Location hookLocation = event.getHook().getLocation();
        Vector pull = hookLocation.toVector().subtract(player.getLocation().toVector());
        double distance = pull.length();
        if (distance < 1.25) {
            return;
        }

        Vector velocity = pull.normalize().multiply(Math.min(1.85, 0.60 + (distance * 0.08)));
        velocity.setY(Math.max(0.35, Math.min(1.05, velocity.getY() + 0.35)));
        player.setFallDistance(0.0f);
        player.setVelocity(velocity);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, 0.7f, 1.25f);
    }

    private void registerRecipes() {
        Bukkit.removeRecipe(advancedPickaxeRecipeKey);
        Bukkit.removeRecipe(grappleHookRecipeKey);

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
    }

    private void discoverRecipes(Player player) {
        player.discoverRecipe(advancedPickaxeRecipeKey);
        player.discoverRecipe(grappleHookRecipeKey);
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
        applyCustomToolPresentation(meta, GRAPPLE_HOOK_ID, item.getType());
        item.setItemMeta(meta);
        return item;
    }

    private void applyCustomToolState(ItemMeta meta, String toolId) {
        meta.getPersistentDataContainer().set(keyCustomToolId, PersistentDataType.STRING, toolId);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        CustomLoreUtil.applyStyledItemFlags(meta);
    }

    private String customToolId(ItemStack item) {
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
                baseDisplayName = MM.deserialize("<aqua><bold>Prospector's Pick</bold></aqua>");
                meta.displayName(baseDisplayName);
                meta.lore(CustomLoreUtil.buildStyledLore(
                    meta,
                    material,
                    "CUSTOM",
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
                baseDisplayName = MM.deserialize("<gold><bold>Skyhook</bold></gold>");
                meta.displayName(baseDisplayName);
                meta.lore(CustomLoreUtil.buildStyledLore(
                    meta,
                    material,
                    "CUSTOM",
                    "HOOK",
                    List.of("<gray>A reinforced line launcher built for sharp movement.</gray>"),
                    List.of(CustomLoreUtil.section(
                        "Right Click",
                        "Sky Pull",
                        "<gray>Cast and reel it in to yank yourself toward the hook.</gray>",
                        "<gray>Cooldown: <white>" + plugin.getConfigManager().grappleHookCooldownSeconds + "s</white>.</gray>",
                        "<gray>Built with simple parts for easy early crafting.</gray>"
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
