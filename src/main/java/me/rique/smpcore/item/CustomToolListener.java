package me.rique.smpcore.item;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
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

    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String ADVANCED_PICKAXE_ID = "advanced_pickaxe";
    public static final String AMETHYST_PICKAXE_ID = "amethyst_pickaxe";

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
    private final NamespacedKey amethystPickaxeRecipeKey;
    private final Set<UUID> amethystAoePlayers = ConcurrentHashMap.newKeySet();
    private final Map<BlockKey, AdvancedPickaxeContext> advancedPickaxeContexts = new ConcurrentHashMap<>();

    public CustomToolListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyCustomToolId = new NamespacedKey(plugin, "custom_tool_id");
        this.advancedPickaxeRecipeKey = new NamespacedKey(plugin, "advanced_pickaxe_recipe");
        this.amethystPickaxeRecipeKey = new NamespacedKey(plugin, "amethyst_pickaxe_recipe");
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

    public boolean isCustomToolId(String id) {
        return ADVANCED_PICKAXE_ID.equals(id) || AMETHYST_PICKAXE_ID.equals(id);
    }

    public String displayNameFor(String toolId) {
        return switch (toolId) {
            case ADVANCED_PICKAXE_ID -> "Advanced Pickaxe";
            case AMETHYST_PICKAXE_ID -> "Amethyst Shard Pickaxe";
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
            case AMETHYST_PICKAXE_ID -> Map.of(
                Material.NETHERITE_PICKAXE, 1,
                Material.AMETHYST_SHARD, 8
            );
            default -> Map.of();
        };
    }

    public ItemStack createRecipePreview(String toolId) {
        return switch (toolId) {
            case ADVANCED_PICKAXE_ID -> createAdvancedPickaxe();
            case AMETHYST_PICKAXE_ID -> createAmethystPickaxe(new ItemStack(Material.NETHERITE_PICKAXE));
            default -> new ItemStack(Material.BARRIER);
        };
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
            case AMETHYST_PICKAXE_ID -> {
                matrix[0] = new ItemStack(Material.AMETHYST_SHARD);
                matrix[1] = new ItemStack(Material.AMETHYST_SHARD);
                matrix[2] = new ItemStack(Material.AMETHYST_SHARD);
                matrix[3] = new ItemStack(Material.AMETHYST_SHARD);
                matrix[4] = new ItemStack(Material.NETHERITE_PICKAXE);
                matrix[5] = new ItemStack(Material.AMETHYST_SHARD);
                matrix[6] = new ItemStack(Material.AMETHYST_SHARD);
                matrix[7] = new ItemStack(Material.AMETHYST_SHARD);
                matrix[8] = new ItemStack(Material.AMETHYST_SHARD);
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
            event.getInventory().setResult(plugin.getConfigManager().advancedPickaxeEnabled ? createAdvancedPickaxe() : null);
            return;
        }

        if (!amethystPickaxeRecipeKey.equals(keyed.getKey())) return;
        if (!plugin.getConfigManager().amethystPickaxeEnabled) {
            event.getInventory().setResult(null);
            return;
        }

        ItemStack base = event.getInventory().getMatrix()[4];
        if (!isPlainVanillaItem(base, Material.NETHERITE_PICKAXE)) {
            event.getInventory().setResult(null);
            return;
        }
        event.getInventory().setResult(createAmethystPickaxe(base));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getInventory() instanceof CraftingInventory crafting)) return;
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof org.bukkit.Keyed keyed)) return;

        if (advancedPickaxeRecipeKey.equals(keyed.getKey()) && !plugin.getConfigManager().advancedPickaxeEnabled) {
            event.setCancelled(true);
            return;
        }

        if (!amethystPickaxeRecipeKey.equals(keyed.getKey())) return;
        if (!plugin.getConfigManager().amethystPickaxeEnabled) {
            event.setCancelled(true);
            return;
        }

        ItemStack base = crafting.getMatrix()[4];
        if (!isPlainVanillaItem(base, Material.NETHERITE_PICKAXE)) {
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

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAmethystPickaxeBreak(BlockBreakEvent event) {
        if (!plugin.getConfigManager().amethystPickaxeEnabled) return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!AMETHYST_PICKAXE_ID.equals(customToolId(tool))) return;
        if (!amethystAoePlayers.add(player.getUniqueId())) return;

        try {
            for (Block target : amethystTargets(player, event.getBlock())) {
                if (target == null || target.equals(event.getBlock())) continue;
                if (target.getType().isAir()) continue;
                if (plugin.getVeinMinerListener() != null) {
                    plugin.getVeinMinerListener().suppressNextBreak(target.getLocation());
                }
                player.breakBlock(target);
            }
        } finally {
            amethystAoePlayers.remove(player.getUniqueId());
        }
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

        Material bonusType = bonusDropMaterial(event.getBlock().getType());
        if (bonusType == null) return;
        if (plugin.getConfigManager().advancedPickaxeDisableBonusWithSilkTouch
            && tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0) {
            return;
        }
        if (!rollBonus(event.getBlock().getType())) return;

        rememberAdvancedPickaxeContext(
            event.getBlock().getLocation(),
            new AdvancedPickaxeContext(
                player.getUniqueId(),
                hasTelekinesis(tool),
                new ItemStack(bonusType, 1),
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

        ItemStack bonus = context.bonusDrop().clone();
        if (context.telekinesis() && plugin.getCustomEnchantListener() != null) {
            plugin.getCustomEnchantListener().deliverTelekinesisDrops(player, List.of(bonus), event.getBlock().getLocation());
            return;
        }

        World world = event.getBlock().getWorld();
        Item dropped = world.dropItemNaturally(event.getBlock().getLocation().add(0.5, 0.5, 0.5), bonus);
        dropped.setPickupDelay(0);
    }

    private void registerRecipes() {
        Bukkit.removeRecipe(advancedPickaxeRecipeKey);
        Bukkit.removeRecipe(amethystPickaxeRecipeKey);

        ShapedRecipe advanced = new ShapedRecipe(advancedPickaxeRecipeKey, createAdvancedPickaxe());
        advanced.shape("III", "DSD", " S ");
        advanced.setIngredient('I', Material.IRON_INGOT);
        advanced.setIngredient('D', Material.DIAMOND);
        advanced.setIngredient('S', Material.STICK);
        advanced.setGroup("smpcore_custom_tools");
        Bukkit.addRecipe(advanced);

        ShapedRecipe amethyst = new ShapedRecipe(amethystPickaxeRecipeKey, createAmethystPickaxe(new ItemStack(Material.NETHERITE_PICKAXE)));
        amethyst.shape("AAA", "APA", "AAA");
        amethyst.setIngredient('A', Material.AMETHYST_SHARD);
        amethyst.setIngredient('P', Material.NETHERITE_PICKAXE);
        amethyst.setGroup("smpcore_custom_tools");
        Bukkit.addRecipe(amethyst);
    }

    private void discoverRecipes(Player player) {
        player.discoverRecipe(advancedPickaxeRecipeKey);
        player.discoverRecipe(amethystPickaxeRecipeKey);
    }

    private boolean hasTelekinesis(ItemStack tool) {
        return plugin.getCustomEnchantListener() != null
            && plugin.getCustomEnchantListener().hasTelekinesisEnchant(tool);
    }

    private ItemStack createAdvancedPickaxe() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        applyCustomToolState(meta, ADVANCED_PICKAXE_ID);
        applyCustomToolPresentation(meta, ADVANCED_PICKAXE_ID, item.getType());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createAmethystPickaxe(ItemStack base) {
        ItemStack item = base == null || base.getType() == Material.AIR
            ? new ItemStack(Material.NETHERITE_PICKAXE)
            : base.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        applyCustomToolState(meta, AMETHYST_PICKAXE_ID);
        applyCustomToolPresentation(meta, AMETHYST_PICKAXE_ID, item.getType());
        item.setItemMeta(meta);
        return item;
    }

    private void applyCustomToolState(ItemMeta meta, String toolId) {
        meta.getPersistentDataContainer().set(keyCustomToolId, PersistentDataType.STRING, toolId);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        CustomLoreUtil.applyStyledItemFlags(meta);
    }

    private String customToolId(ItemStack item) {
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
        applyCustomToolPresentation(resultMeta, toolId, updated.getType());
        updated.setItemMeta(resultMeta);
        return updated;
    }

    private void refreshCustomToolPresentation(ItemStack item) {
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
            if (customToolId(item) == null) {
                continue;
            }
            refreshCustomToolPresentation(item);
            player.getInventory().setItem(slot, item);
        }
    }

    private void applyCustomToolPresentation(ItemMeta meta, String toolId, Material material) {
        switch (toolId) {
            case ADVANCED_PICKAXE_ID -> {
                meta.displayName(MM.deserialize("<aqua><bold>Advanced Pickaxe</bold></aqua>"));
                meta.lore(CustomLoreUtil.buildStyledLore(
                    meta,
                    material,
                    "CUSTOM",
                    "PICKAXE",
                    List.of("<gray>Bonus ore drops while mining ores.</gray>"),
                    List.of(CustomLoreUtil.section(
                        "Item Ability",
                        "Ore Excavation",
                        "<gray>Coal and copper bonus drops are guaranteed.</gray>",
                        "<gray>Diamond and emerald bonus drops are <white>10%</white>.</gray>",
                        "<gray>Iron is <white>66.7%</white>, redstone and lapis <white>50%</white>, gold <white>33.3%</white>.</gray>",
                        "<gray>Fortune still affects the normal drop.</gray>"
                    ))
                ));
            }
            case AMETHYST_PICKAXE_ID -> {
                meta.displayName(MM.deserialize("<light_purple><bold>Amethyst Shard Pickaxe</bold></light_purple>"));
                meta.lore(CustomLoreUtil.buildStyledLore(
                    meta,
                    material,
                    "CUSTOM",
                    "PICKAXE",
                    List.of("<gray>Mines a <white>" + plugin.getConfigManager().amethystPickaxeStripWidth + "x1</white> strip.</gray>"),
                    List.of(CustomLoreUtil.section(
                        "Item Ability",
                        "Amethyst Line Break",
                        "<gray>Breaks a forward-facing strip instead of a single block.</gray>",
                        "<gray>Keeps normal pickaxe enchants and durability.</gray>"
                    ))
                ));
            }
            default -> {
            }
        }
    }

    private List<Block> amethystTargets(Player player, Block center) {
        List<Block> blocks = new ArrayList<>();
        blocks.add(center);

        int span = Math.max(1, plugin.getConfigManager().amethystPickaxeStripWidth);
        int radius = span / 2;
        boolean eastWestFacing = switch (player.getFacing()) {
            case EAST, WEST -> true;
            default -> false;
        };

        for (int offset = -radius; offset <= radius; offset++) {
            if (offset == 0) continue;
            blocks.add(eastWestFacing
                ? center.getRelative(0, 0, offset)
                : center.getRelative(offset, 0, 0));
        }
        return blocks;
    }

    private Material bonusDropMaterial(Material broken) {
        if (COAL_ORES.contains(broken)) return Material.COAL;
        if (IRON_ORES.contains(broken)) return Material.RAW_IRON;
        if (REDSTONE_ORES.contains(broken)) return Material.REDSTONE;
        if (LAPIS_ORES.contains(broken)) return Material.LAPIS_LAZULI;
        if (COPPER_ORES.contains(broken)) return Material.RAW_COPPER;
        if (DIAMOND_ORES.contains(broken)) return Material.DIAMOND;
        if (EMERALD_ORES.contains(broken)) return Material.EMERALD;
        if (GOLD_ORES.contains(broken)) {
            return broken == Material.NETHER_GOLD_ORE ? Material.GOLD_NUGGET : Material.RAW_GOLD;
        }
        return null;
    }

    private boolean rollBonus(Material broken) {
        double chance = switch (broken) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> plugin.getConfigManager().advancedPickaxeCoalChance;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> plugin.getConfigManager().advancedPickaxeIronChance;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> plugin.getConfigManager().advancedPickaxeRedstoneChance;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> plugin.getConfigManager().advancedPickaxeLapisChance;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> plugin.getConfigManager().advancedPickaxeCopperChance;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> plugin.getConfigManager().advancedPickaxeDiamondChance;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> plugin.getConfigManager().advancedPickaxeEmeraldChance;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> plugin.getConfigManager().advancedPickaxeGoldChance;
            default -> 0.0;
        };
        return ThreadLocalRandom.current().nextDouble() < chance;
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

    private record AdvancedPickaxeContext(UUID playerId, boolean telekinesis, ItemStack bonusDrop, long expiresAt) {}
}
