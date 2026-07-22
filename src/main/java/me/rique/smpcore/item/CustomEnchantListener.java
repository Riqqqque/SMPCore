package me.rique.smpcore.item;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.combat.AbilityDamageContext;
import me.rique.smpcore.compat.CrossplayManager.AnvilRecipe;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.InventoryRecipeUtil;
import me.rique.smpcore.util.ItemModelUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class CustomEnchantListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Component ENCHANTS_MENU_TITLE = MM.deserialize("<dark_aqua><bold>Custom Enchants</bold></dark_aqua>");
    private static final String DELICATE_LORE_LINE = "Delicate I";
    private static final String TELEKINESIS_LORE_LINE = "Telekinesis I";
    private static final String SMELTING_TOUCH_LORE_LINE = "Smelting Touch I";
    private static final String WISE_LORE_PREFIX = "Wise ";
    private static final String DOUBLE_JUMP_LORE_LINE = "Double Jump I";
    private static final String DASH_LORE_LINE = "Dash I";
    private static final String FROSTBITE_LORE_PREFIX = "Frostbite ";
    private static final String HARVESTING_LORE_PREFIX = "Harvesting ";
    private static final String BULWARK_LORE_PREFIX = "Bulwark ";
    private static final String REINFORCED_LORE_PREFIX = "Reinforced ";
    private static final String KINGSLAYER_LORE_LINE = "Kingslayer I";
    private static final String SOUL_SIPHON_LORE_LINE = "Soul Siphon I";
    private static final String ECHOING_LORE_LINE = "Echoing I";
    private static final String ESSENCE_CAPTURE_LORE_PREFIX = "Essence Capture ";
    private static final double ESSENCE_CAPTURE_LEVEL_ONE_CHANCE = 0.005D;
    private static final double ESSENCE_CAPTURE_LEVEL_TWO_CHANCE = 0.015D;
    private static final double ESSENCE_CAPTURE_LEVEL_THREE_CHANCE = 0.025D;
    private static final long TELEKINESIS_MINING_CONTEXT_TTL_MS = 1000L;
    private static final int CUSTOM_ENCHANT_ANVIL_BASE_COST = 6;
    private static final int CUSTOM_ENCHANT_ANVIL_LEVEL_COST = 3;
    private static final Map<String, Integer> CUSTOM_ENCHANT_MAX_LEVELS = Map.ofEntries(
        Map.entry("delicate", 1),
        Map.entry("telekinesis", 1),
        Map.entry("smelting_touch", 1),
        Map.entry("wise", 3),
        Map.entry("double_jump", 1),
        Map.entry("dash", 1),
        Map.entry("frostbite", 2),
        Map.entry("harvesting", 3),
        Map.entry("bulwark", 3),
        Map.entry("reinforced", 3),
        Map.entry("kingslayer", 1),
        Map.entry("soul_siphon", 1),
        Map.entry("echoing", 1),
        Map.entry("essence_capture", 3)
    );

    private final SMPCore plugin;
    private final NamespacedKey keyCustomEnchantBook;
    private final NamespacedKey keyReplenishBook;
    private final NamespacedKey keyEnchantMenuId;
    private final NamespacedKey keyDelicate;
    private final NamespacedKey keyTelekinesis;
    private final NamespacedKey keySmeltingTouch;
    private final NamespacedKey keyWise;
    private final NamespacedKey keyDoubleJump;
    private final NamespacedKey keyDash;
    private final NamespacedKey keyFrostbite;
    private final NamespacedKey keyHarvesting;
    private final NamespacedKey keyBulwark;
    private final NamespacedKey keyReinforced;
    private final NamespacedKey keyKingslayer;
    private final NamespacedKey keySoulSiphon;
    private final NamespacedKey keyEchoing;
    private final NamespacedKey keyEssenceCapture;
    private final NamespacedKey keyDashCooldownUntil;
    private final NamespacedKey keyTelekinesisProjectileOwner;
    private final NamespacedKey keyEssenceCaptureProjectileOwner;
    private final NamespacedKey keyEssenceCaptureProjectileLevel;
    private final NamespacedKey keyKingslayerRecipe;
    private final NamespacedKey keySoulSiphonRecipe;
    private final NamespacedKey keyEchoingRecipe;
    private final Map<UUID, UUID> telekinesisLootOwners = new ConcurrentHashMap<>();
    private final Map<UUID, EssenceCaptureHit> essenceCaptureLootOwners = new ConcurrentHashMap<>();
    private final Map<BlockKey, TelekinesisMiningContext> telekinesisMiningContexts = new ConcurrentHashMap<>();
    private final Map<UUID, Double> wiseXpRemainders = new ConcurrentHashMap<>();
    private final Map<Material, ItemStack> smeltingResults = new ConcurrentHashMap<>();
    private final java.util.Set<Material> nonSmeltableDrops = ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> doubleJumpArmedPlayers = ConcurrentHashMap.newKeySet();

    public CustomEnchantListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyCustomEnchantBook = new NamespacedKey(plugin, "custom_enchant_book");
        this.keyReplenishBook = new NamespacedKey(plugin, "replenish_book");
        this.keyEnchantMenuId = new NamespacedKey(plugin, "custom_enchant_menu_id");
        this.keyDelicate = new NamespacedKey(plugin, "delicate_enchant");
        this.keyTelekinesis = new NamespacedKey(plugin, "telekinesis_enchant");
        this.keySmeltingTouch = new NamespacedKey(plugin, "smelting_touch_enchant");
        this.keyWise = new NamespacedKey(plugin, "wise_enchant");
        this.keyDoubleJump = new NamespacedKey(plugin, "double_jump_enchant");
        this.keyDash = new NamespacedKey(plugin, "dash_enchant");
        this.keyFrostbite = new NamespacedKey(plugin, "frostbite_enchant");
        this.keyHarvesting = new NamespacedKey(plugin, "harvesting_enchant");
        this.keyBulwark = new NamespacedKey(plugin, "bulwark_enchant");
        this.keyReinforced = new NamespacedKey(plugin, "reinforced_enchant");
        this.keyKingslayer = new NamespacedKey(plugin, "kingslayer_enchant");
        this.keySoulSiphon = new NamespacedKey(plugin, "soul_siphon_enchant");
        this.keyEchoing = new NamespacedKey(plugin, "echoing_enchant");
        this.keyEssenceCapture = new NamespacedKey(plugin, "essence_capture_enchant");
        this.keyDashCooldownUntil = new NamespacedKey(plugin, "dash_cooldown_until");
        this.keyTelekinesisProjectileOwner = new NamespacedKey(plugin, "telekinesis_projectile_owner");
        this.keyEssenceCaptureProjectileOwner = new NamespacedKey(plugin, "essence_capture_projectile_owner");
        this.keyEssenceCaptureProjectileLevel = new NamespacedKey(plugin, "essence_capture_projectile_level");
        this.keyKingslayerRecipe = new NamespacedKey(plugin, "kingslayer_book");
        this.keySoulSiphonRecipe = new NamespacedKey(plugin, "soul_siphon_book");
        this.keyEchoingRecipe = new NamespacedKey(plugin, "echoing_book");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickDoubleJumpFlightPlayers, 1L, 1L);
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearDoubleJumpFlight(player);
        }
        doubleJumpArmedPlayers.clear();
        wiseXpRemainders.clear();
        telekinesisLootOwners.clear();
        essenceCaptureLootOwners.clear();
        telekinesisMiningContexts.clear();
    }

    public ItemStack createDelicateBook() {
        return createBook(CustomEnchantEntry.DELICATE, 1);
    }

    public ItemStack createTelekinesisBook() {
        return createBook(CustomEnchantEntry.TELEKINESIS, 1);
    }

    public ItemStack createSmeltingTouchBook() {
        return createBook(CustomEnchantEntry.SMELTING_TOUCH, 1);
    }

    public ItemStack createWiseBook(int level) {
        return createBook(CustomEnchantEntry.WISE, clampWiseLevel(level));
    }

    public ItemStack createDoubleJumpBook() {
        return createBook(CustomEnchantEntry.DOUBLE_JUMP, 1);
    }

    public ItemStack createDashBook() {
        return createBook(CustomEnchantEntry.DASH, 1);
    }

    public ItemStack createKingslayerBook() {
        return createBook(CustomEnchantEntry.KINGSLAYER, 1);
    }

    public ItemStack createSoulSiphonBook() {
        return createBook(CustomEnchantEntry.SOUL_SIPHON, 1);
    }

    public ItemStack createEchoingBook() {
        return createBook(CustomEnchantEntry.ECHOING, 1);
    }

    public void registerCraftOnlyRecipes() {
        registerCraftOnlyRecipe(
            keyKingslayerRecipe,
            createKingslayerBook(),
            List.of(
                "CBC",
                "BEB",
                "SWS"
            ),
            Map.of(
                'C', exactRelicChoice("crimson_rib"),
                'B', new RecipeChoice.MaterialChoice(Material.BLAZE_ROD),
                'E', new RecipeChoice.MaterialChoice(Material.BOOK),
                'S', exactRelicChoice("sculk_heart"),
                'W', new RecipeChoice.MaterialChoice(Material.NETHER_STAR)
            )
        );
        registerCraftOnlyRecipe(
            keySoulSiphonRecipe,
            createSoulSiphonBook(),
            List.of(
                "VGV",
                "CEC",
                "SAS"
            ),
            Map.of(
                'V', exactRelicChoice("verdant_heart"),
                'G', new RecipeChoice.MaterialChoice(Material.GHAST_TEAR),
                'C', exactRelicChoice("crimson_rib"),
                'E', new RecipeChoice.MaterialChoice(Material.BOOK),
                'S', new RecipeChoice.MaterialChoice(Material.SOUL_SAND),
                'A', new RecipeChoice.MaterialChoice(Material.GOLDEN_APPLE)
            )
        );
        registerCraftOnlyRecipe(
            keyEchoingRecipe,
            createEchoingBook(),
            List.of(
                "TET",
                "AEA",
                "SLS"
            ),
            Map.of(
                'T', exactRelicChoice("titan_gear"),
                'E', new RecipeChoice.MaterialChoice(Material.ECHO_SHARD),
                'A', new RecipeChoice.MaterialChoice(Material.AMETHYST_SHARD),
                'S', exactRelicChoice("sculk_heart"),
                'L', new RecipeChoice.MaterialChoice(Material.BOOK)
            )
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraftOnlyEnchant(PrepareItemCraftEvent event) {
        CustomEnchantEntry enchant = craftOnlyRecipeEntry(event.getRecipe());
        if (enchant == null) {
            return;
        }
        if (!usesOnlyValidCraftOnlyIngredients(enchant, event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftOnlyEnchant(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        CustomEnchantEntry enchant = craftOnlyRecipeEntry(event.getRecipe());
        if (enchant == null || usesOnlyValidCraftOnlyIngredients(enchant, event.getInventory().getMatrix())) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(MessageUtil.warn("That enchant recipe needs the exact boss materials and clean vanilla ingredients."));
    }

    public boolean hasTelekinesisEnchant(ItemStack item) {
        return hasTelekinesis(item);
    }

    public boolean hasSmeltingTouchEnchant(ItemStack item) {
        return hasSmeltingTouch(item);
    }

    public String customEnchantBookId(ItemStack item) {
        BookEnchantData enchant = bookEnchant(item);
        return enchant == null ? null : enchant.enchant().id;
    }

    public String customEnchantBookDisplayName(ItemStack item) {
        BookEnchantData enchant = bookEnchant(item);
        return enchant == null ? null : enchant.enchant().plainDisplay(enchant.level()) + " Book";
    }

    public boolean isCustomEnchantBook(ItemStack item) {
        return bookEnchant(item) != null;
    }

    /**
     * Returns true for every enchanted book carrying any SMPCore custom-enchant
     * payload, including malformed legacy books. Callers that handle vanilla
     * stored enchantments must use this to avoid treating custom books as
     * ordinary enchanted books.
     */
    public boolean hasCustomEnchantBookData(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.getKeys().contains(keyCustomEnchantBook)) {
            return true;
        }
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            if (pdc.getKeys().contains(keyFor(enchant))) {
                return true;
            }
        }
        return false;
    }

    public boolean isUnsafeCustomEnchantBook(ItemStack item) {
        return hasCustomEnchantBookData(item) && customBookPayloadState(item) != CustomBookPayloadState.VALID;
    }

    public List<String> managedEnchantIds() {
        return CustomEnchantEntry.MANAGED.stream()
            .map(enchant -> enchant.id)
            .toList();
    }

    public void deliverTelekinesisDrops(Player player, Collection<ItemStack> drops, Location origin) {
        giveDrops(player, drops, origin);
    }

    public void applyManagedEnchantLore(ItemMeta meta) {
        if (meta == null) {
            return;
        }

        List<Component> baseLore = CustomLoreUtil.stripCustomEnchantLore(meta.lore());

        List<Component> managedLore = new ArrayList<>(CustomLoreUtil.customEnchantLore(meta));

        if (managedLore.isEmpty() && baseLore.isEmpty()) {
            meta.lore(null);
            return;
        }

        managedLore.addAll(baseLore);
        meta.lore(CustomLoreUtil.normalizeLore(managedLore));
        CustomLoreUtil.applyStyledItemFlags(meta);
    }

    public List<ItemStack> smeltMiningDrops(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
            return List.of();
        }

        ItemStack result = findSmeltingResult(stack.getType());
        if (result == null || result.getType() == Material.AIR || result.getAmount() <= 0) {
            return List.of(stack.clone());
        }

        long totalAmount = (long) result.getAmount() * stack.getAmount();
        if (totalAmount <= 0L) {
            return List.of(stack.clone());
        }

        List<ItemStack> smelted = new ArrayList<>();
        int maxStack = Math.max(1, result.getMaxStackSize());
        long remaining = totalAmount;
        while (remaining > 0L) {
            ItemStack split = result.clone();
            split.setAmount((int) Math.min(remaining, maxStack));
            smelted.add(split);
            remaining -= split.getAmount();
        }
        return smelted;
    }

    public void openEnchantMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(
            new EnchantMenuHolder(),
            45,
            BedrockCompat.menuTitle(player, ENCHANTS_MENU_TITLE, "Custom Enchants")
        );
        ItemStack filler = createMenuItem(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isFrameSlot(slot, inventory.getSize())) {
                inventory.setItem(slot, filler);
            }
        }
        inventory.setItem(4, createMenuItem(
            Material.ENCHANTED_BOOK,
            "<gradient:#00d4ff:#73ff9d><bold>Custom Enchants</bold></gradient>",
            List.of(
                "<gray>Special enchants can appear through enchanting, loot,</gray>",
                "<gray>or be applied with custom enchanted books.</gray>"
            )
        ));
        int[] slots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
        };
        List<CustomEnchantEntry> entries = CustomEnchantEntry.MENU_ENTRIES;
        for (int i = 0; i < slots.length && i < entries.size(); i++) {
            inventory.setItem(slots[i], createMenuIcon(entries.get(i)));
        }
        inventory.setItem(36, createMenuItem(Material.ANVIL, "<light_purple><bold>Use A Placed Anvil</bold></light_purple>", List.of(
            "<gray>Bedrock players can tap a placed anvil for custom recipes.</gray>",
            "<gray>Crouch and tap it for normal vanilla anvil use.</gray>"
        )));
        inventory.setItem(40, createMenuItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to /menu.</gray>")));
        player.openInventory(inventory);
    }

    public AnvilRecipe crossplayAnvilRecipe(ItemStack left, ItemStack right) {
        if (isUnsafeCustomEnchantBook(left) || isUnsafeCustomEnchantBook(right)) {
            return null;
        }
        BookEnchantData leftBook = bookEnchant(left);
        BookEnchantData rightBook = bookEnchant(right);
        if (leftBook == null && rightBook == null) {
            // Same-item merges need Minecraft's native durability, prior-work, and
            // vanilla-enchant calculation. CrossplayManager transfers these inputs
            // to a real server-created anvil rather than approximating that result.
            return null;
        }
        CustomEnchantAnvilResult result = customEnchantAnvilResult(left, right);
        if (result == null) {
            return null;
        }
        boolean createsBook = bookEnchant(result.result()) != null;
        return new AnvilRecipe(
            result.result(),
            result.cost(),
            result.action() + " <white>" + result.description() + "</white>.",
            result.action() + " " + result.description() + " through the crossplay custom anvil.",
            createsBook
        );
    }

    public boolean activateHeldCrossplayAbility(Player player) {
        if (player == null || !hasDash(player.getInventory().getItemInMainHand())) {
            return false;
        }
        useDash(player);
        return true;
    }

    public boolean hasDashEnchant(ItemStack item) {
        return hasDash(item);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        ItemStack item = event.getItem();
        if (event.getEnchantsToAdd().isEmpty()) {
            return;
        }
        List<CustomEnchantEntry> candidates = enchantTableCandidates(item, event.getExpLevelCost());
        if (event.getEnchantsToAdd().containsKey(Enchantment.SILK_TOUCH)) {
            candidates.remove(CustomEnchantEntry.SMELTING_TOUCH);
        }
        if (candidates.isEmpty()) return;

        CustomEnchantEntry selected = pickEnchantTableEntry(candidates);
        if (selected == null) return;

        int level = enchantTableLevel(selected, event.getExpLevelCost());
        Player enchanter = event.getEnchanter();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.isCancelled()) {
                return;
            }
            if (!canApply(item, selected, level)) {
                return;
            }
            applyEnchant(item, selected, level);
            if (enchanter.isOnline()) {
                enchanter.sendMessage(MessageUtil.success(
                    "Your item gained <white>" + selected.plainDisplay(level) + "</white>."
                ));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchantItemConflict(EnchantItemEvent event) {
        if (!hasSmeltingTouch(event.getItem())
            || !event.getEnchantsToAdd().containsKey(Enchantment.SILK_TOUCH)) {
            return;
        }
        event.getEnchantsToAdd().remove(Enchantment.SILK_TOUCH);
        if (event.getEnchantsToAdd().isEmpty()) {
            event.setCancelled(true);
        }
        event.getEnchanter().sendMessage(MessageUtil.warn(
            "Smelting Touch cannot be combined with Silk Touch. Your Smelting Touch was kept."
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getFirstItem();
        ItemStack right = event.getInventory().getSecondItem();
        if (isUnsafeCustomEnchantBook(left) || isUnsafeCustomEnchantBook(right)) {
            event.setResult(null);
            return;
        }
        BookEnchantData leftBook = bookEnchant(left);
        BookEnchantData rightBook = bookEnchant(right);
        if (leftBook == null && rightBook == null) {
            CustomEnchantAnvilResult mergeResult = customEnchantItemMergeResult(left, right, event.getResult());
            if (mergeResult == null) {
                ItemStack vanillaResult = event.getResult();
                if (vanillaResult != null && !vanillaResult.getType().isAir()) {
                    ItemStack refreshed = vanillaResult.clone();
                    normalizeManagedEnchantData(refreshed);
                    if (CustomLoreUtil.hasSmeltingSilkConflict(refreshed)) {
                        event.setResult(null);
                        return;
                    }
                    if (CustomLoreUtil.normalizeItemLore(refreshed)) {
                        event.setResult(refreshed);
                    }
                }
                return;
            }
            event.setResult(mergeResult.result().clone());
            configureCustomEnchantAnvil(event, mergeResult);
            return;
        }

        CustomEnchantAnvilResult customResult = customEnchantAnvilResult(left, right);
        if (customResult == null) {
            event.setResult(null);
            return;
        }

        event.setResult(customResult.result().clone());
        configureCustomEnchantAnvil(event, customResult);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnvilClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getType() != InventoryType.ANVIL) return;
        if (!isAnvilResultSlot(event)) return;
        if (!(event.getView().getTopInventory() instanceof AnvilInventory anvil)) return;

        ItemStack left = anvil.getFirstItem();
        ItemStack right = anvil.getSecondItem();
        if (isUnsafeCustomEnchantBook(left) || isUnsafeCustomEnchantBook(right)) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.warn(
                "That custom enchant book has invalid or mixed enchant data. Ask staff to replace it."
            ));
            return;
        }
        BookEnchantData leftBook = bookEnchant(left);
        BookEnchantData rightBook = bookEnchant(right);
        CustomEnchantAnvilResult customResult = leftBook != null || rightBook != null
            ? customEnchantAnvilResult(left, right)
            : customEnchantItemMergeResult(left, right, event.getCurrentItem());
        if (customResult == null) return;

        ItemStack result = customResult.result().clone();
        if (result == null || result.getType() == Material.AIR) return;

        event.setCancelled(true);
        if (!canReceiveAnvilResult(player, event)) {
            return;
        }
        int xpCost = customResult.cost();
        if (!canPayAnvilCost(player, xpCost)) {
            return;
        }

        ItemStack consumedLeftBook = bookEnchant(left) == null ? null : left.clone();
        ItemStack consumedRightBook = bookEnchant(right) == null ? null : right.clone();
        anvil.setItem(0, null);
        anvil.setItem(1, consumeOne(right));
        anvil.setItem(2, null);
        chargeAnvilCost(player, xpCost);
        if (plugin.getItemAuditManager() != null) {
            String auditDetails = customResult.action() + " " + customResult.description() + " in an anvil.";
            if (consumedLeftBook != null) {
                plugin.getItemAuditManager().recordConsumption(player, consumedLeftBook, "custom_enchant_anvil", auditDetails);
            }
            if (consumedRightBook != null) {
                plugin.getItemAuditManager().recordConsumption(player, consumedRightBook, "custom_enchant_anvil", auditDetails);
            }
            if (bookEnchant(result) != null) {
                plugin.getItemAuditManager().recordKnownAcquisition(
                    player,
                    result,
                    "custom_enchant_anvil",
                    auditDetails
                );
            }
        }
        giveAnvilResult(player, event, result);
        String action = customResult.action();
        player.sendMessage(MessageUtil.success(
            action + " <white>" + customResult.description() + "</white>."
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (!(event.getInventory() instanceof GrindstoneInventory grindstone)) return;

        ItemStack top = grindstone.getUpperItem();
        ItemStack bottom = grindstone.getLowerItem();
        if (isUnsafeCustomEnchantBook(top) || isUnsafeCustomEnchantBook(bottom)) {
            event.setResult(null);
            return;
        }
        boolean topBook = isCustomEnchantBook(top);
        boolean bottomBook = isCustomEnchantBook(bottom);
        if (topBook || bottomBook) {
            if (!isEmptyItem(top) && !isEmptyItem(bottom)) {
                event.setResult(null);
                return;
            }
            event.setResult(new ItemStack(Material.BOOK));
            return;
        }
        boolean topHasManagedData = hasManagedEnchant(top) || hasAnyManagedEnchantData(top);
        boolean bottomHasManagedData = hasManagedEnchant(bottom) || hasAnyManagedEnchantData(bottom);
        if (!topHasManagedData && !bottomHasManagedData) return;

        ItemStack result = event.getResult();
        if (result != null && result.getType() != Material.AIR) {
            event.setResult(stripManagedEnchants(result.clone()));
            return;
        }

        ItemStack source = topHasManagedData ? top : bottom;
        if (!isEmptyItem(top) && !isEmptyItem(bottom)) {
            event.setResult(null);
            return;
        }
        if (source == null || source.getType() == Material.AIR) return;
        event.setResult(stripManagedEnchants(source.clone()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGrindstoneCustomBookStackClick(InventoryClickEvent event) {
        if (!isGrindstoneResultSlot(event)) {
            return;
        }
        GrindstoneInventory grindstone = (GrindstoneInventory) event.getView().getTopInventory();
        if (!isUnsafeCustomEnchantBook(grindstone.getUpperItem())
            && !isUnsafeCustomEnchantBook(grindstone.getLowerItem())) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            player.sendMessage(MessageUtil.warn(
                "That custom enchant book has invalid or mixed enchant data. Ask staff to replace it."
            ));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrindstoneCustomBookConsumed(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
            || !isGrindstoneResultSlot(event)
            || event.getAction() == InventoryAction.NOTHING
            || event.getCurrentItem() == null
            || event.getCurrentItem().getType().isAir()) {
            return;
        }
        GrindstoneInventory grindstone = (GrindstoneInventory) event.getView().getTopInventory();
        recordGroundBookConsumption(player, grindstone.getUpperItem());
        recordGroundBookConsumption(player, grindstone.getLowerItem());
    }

    private void recordGroundBookConsumption(Player player, ItemStack item) {
        if (plugin.getItemAuditManager() == null || !isCustomEnchantBook(item)) {
            return;
        }
        plugin.getItemAuditManager().recordConsumption(
            player,
            item.clone(),
            "custom_enchant_grindstone",
            "Removed a custom enchant book in a grindstone."
        );
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmeltingTouchMine(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.getGameMode() == GameMode.CREATIVE) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!hasSmeltingTouch(tool)) return;

        Location origin = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        List<ItemStack> overflow = new ArrayList<>();
        for (Item item : event.getItems()) {
            List<ItemStack> smelted = smeltMiningDrops(item.getItemStack());
            if (smelted.isEmpty()) continue;
            item.setItemStack(smelted.get(0));
            for (int i = 1; i < smelted.size(); i++) {
                overflow.add(smelted.get(i));
            }
        }

        if (!overflow.isEmpty()) {
            if (hasTelekinesis(tool)) {
                giveDrops(player, overflow, origin);
            } else {
                dropDropsNaturally(origin, overflow);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDelicateBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!hasDelicate(tool)) return;

        Block block = event.getBlock();
        if (harvestStemPreservingPlant(player, tool, block)) {
            event.setCancelled(true);
            event.setDropItems(false);
            event.setExpToDrop(0);
            return;
        }

        if (isImmatureDelicateCrop(block)) {
            event.setCancelled(true);
            return;
        }

        if (isAlwaysProtectedPlant(block.getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTelekinesisBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (!hasTelekinesis(player.getInventory().getItemInMainHand())) return;
        if (event.getBlock().getState() instanceof org.bukkit.inventory.InventoryHolder) return;

        rememberTelekinesisMiningContext(player, event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTelekinesisMine(BlockDropItemEvent event) {
        Location blockLocation = event.getBlock().getLocation();
        Player owner = telekinesisMiningOwner(blockLocation);
        if (owner == null || owner.getGameMode() == GameMode.CREATIVE) {
            forgetTelekinesisMiningContext(blockLocation);
            return;
        }

        List<ItemStack> drops = new ArrayList<>();
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) continue;
            drops.add(stack.clone());
        }
        if (drops.isEmpty()) {
            forgetTelekinesisMiningContext(blockLocation);
            return;
        }

        event.setCancelled(true);
        giveDrops(owner, drops, blockLocation);
        forgetTelekinesisMiningContext(blockLocation);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getProjectile() instanceof Projectile projectile)) return;

        ItemStack weapon = event.getBow();
        if (hasTelekinesis(weapon)) {
            projectile.getPersistentDataContainer().set(
                keyTelekinesisProjectileOwner,
                PersistentDataType.STRING,
                player.getUniqueId().toString()
            );
        }
        int essenceCaptureLevel = storedEnchantLevel(weapon, CustomEnchantEntry.ESSENCE_CAPTURE);
        if (essenceCaptureLevel > 0) {
            tagEssenceCaptureProjectile(projectile, player.getUniqueId(), essenceCaptureLevel);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player)) return;

        ItemStack sourceItem = projectileSourceItem(projectile);
        int essenceCaptureLevel = storedEnchantLevel(sourceItem, CustomEnchantEntry.ESSENCE_CAPTURE);
        if (essenceCaptureLevel <= 0) return;

        tagEssenceCaptureProjectile(projectile, player.getUniqueId(), essenceCaptureLevel);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || victim instanceof Player) return;

        if (event.getDamager() instanceof Player attacker && AbilityDamageContext.isActive(attacker)) {
            telekinesisLootOwners.remove(victim.getUniqueId());
            essenceCaptureLootOwners.remove(victim.getUniqueId());
            return;
        }

        UUID telekinesisOwnerId = telekinesisOwner(event.getDamager());
        if (telekinesisOwnerId != null) {
            telekinesisLootOwners.put(victim.getUniqueId(), telekinesisOwnerId);
        } else {
            telekinesisLootOwners.remove(victim.getUniqueId());
        }

        EssenceCaptureHit essenceCaptureHit = essenceCaptureHit(event.getDamager());
        if (essenceCaptureHit != null) {
            essenceCaptureLootOwners.put(victim.getUniqueId(), essenceCaptureHit);
        } else {
            essenceCaptureLootOwners.remove(victim.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) return;

        UUID ownerId = telekinesisLootOwners.remove(event.getEntity().getUniqueId());
        if (ownerId == null) return;

        Player killer = event.getEntity().getKiller();
        if (killer != null && !killer.getUniqueId().equals(ownerId)) {
            return;
        }

        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) return;
        if (event.getDrops().isEmpty()) return;

        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        event.getDrops().clear();
        giveDrops(owner, drops, event.getEntity().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEssenceCaptureDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return;

        EssenceCaptureHit essenceCaptureHit = essenceCaptureLootOwners.remove(entity.getUniqueId());
        if (isCustomBoss(entity) || isBlockedEssenceCaptureType(entity.getType())) return;
        if (essenceCaptureHit == null) return;

        UUID ownerId = essenceCaptureHit.ownerId();
        Player killer = entity.getKiller();
        if (killer == null || !killer.getUniqueId().equals(ownerId)) {
            return;
        }

        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) return;
        if (owner.getGameMode() == GameMode.CREATIVE || owner.getGameMode() == GameMode.SPECTATOR) return;
        double dropChance = essenceCaptureDropChance(essenceCaptureHit.level());
        if (dropChance <= 0.0D || ThreadLocalRandom.current().nextDouble() >= dropChance) return;

        Material eggMaterial = spawnEggMaterial(entity.getType());
        if (eggMaterial == null) return;

        event.getDrops().add(new ItemStack(eggMaterial));
        Location center = entity.getLocation().add(0.0, 1.0, 0.0);
        entity.getWorld().spawnParticle(Particle.SOUL, center, 12, 0.35, 0.35, 0.35, 0.02);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.55f, 1.55f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombatEnchantDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        if (!(event.getDamager() instanceof Player attacker)) return;
        if (AbilityDamageContext.isActive(attacker)) return;
        if (attacker.equals(victim)) return;
        if (victim instanceof Player target && sameTeam(attacker, target)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        boolean bossEncounterVictim = isBossEncounterEntity(victim);
        int frostbiteLevel = storedEnchantLevel(weapon, CustomEnchantEntry.FROSTBITE);
        if (!bossEncounterVictim
            && frostbiteLevel > 0
            && ThreadLocalRandom.current().nextDouble() < 0.12D + (frostbiteLevel * 0.08D)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, Math.min(1, frostbiteLevel - 1), false, true, true));
            victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0.0, 1.0, 0.0), 14, 0.35, 0.45, 0.35, 0.02);
        }

        if (storedEnchantLevel(weapon, CustomEnchantEntry.KINGSLAYER) > 0 && isCustomBoss(victim)) {
            event.setDamage(event.getDamage() * 1.18D);
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0.0, 1.0, 0.0), 18, 0.35, 0.35, 0.35, 0.1);
        }

        if (!bossEncounterVictim
            && storedEnchantLevel(weapon, CustomEnchantEntry.ECHOING) > 0
            && ThreadLocalRandom.current().nextDouble() < 0.25D) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 80, 0, false, true, true));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 50, 0, false, true, true));
            Vector push = victim.getLocation().toVector().subtract(attacker.getLocation().toVector());
            if (push.lengthSquared() > 0.0001D) {
                push.normalize().multiply(0.35D).setY(0.18D);
                victim.setVelocity(victim.getVelocity().add(push));
            }
            victim.getWorld().spawnParticle(Particle.SONIC_BOOM, victim.getLocation().add(0.0, 1.0, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.65f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSoulSiphonHeal(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        if (!(event.getDamager() instanceof Player attacker)) return;
        if (AbilityDamageContext.isActive(attacker)) return;
        if (attacker.equals(victim) || attacker.isDead()) return;
        if (victim instanceof Player target && sameTeam(attacker, target)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (storedEnchantLevel(weapon, CustomEnchantEntry.SOUL_SIPHON) <= 0) return;

        double heal = Math.min(victim instanceof Player ? 1.2D : 2.0D, event.getFinalDamage() * 0.10D);
        if (heal <= 0.0D) return;
        if (plugin.getBossManager() != null && plugin.getBossManager().blockHealingIfSuppressed(attacker, heal)) return;

        attacker.setHealth(Math.min(maxHealth(attacker), attacker.getHealth() + heal));
        attacker.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, attacker.getLocation().add(0.0, 1.0, 0.0), 6, 0.25, 0.35, 0.25, 0.02);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBulwarkDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID || event.getCause() == EntityDamageEvent.DamageCause.SUICIDE) {
            return;
        }

        int level = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            level += storedEnchantLevel(armor, CustomEnchantEntry.BULWARK);
        }
        if (level <= 0) return;

        double reduction = Math.min(0.30D, level * 0.025D);
        event.setDamage(event.getDamage() * (1.0D - reduction));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onReinforcedItemDamage(PlayerItemDamageEvent event) {
        int level = storedEnchantLevel(event.getItem(), CustomEnchantEntry.REINFORCED);
        if (level <= 0) return;
        if (ThreadLocalRandom.current().nextDouble() >= level * 0.08D) return;

        event.setCancelled(true);
        event.getPlayer().getWorld().spawnParticle(Particle.WAX_ON, event.getPlayer().getLocation().add(0.0, 1.0, 0.0), 4, 0.25, 0.35, 0.25, 0.01);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHarvestingDrops(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.getGameMode() == GameMode.CREATIVE) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        int level = storedEnchantLevel(tool, CustomEnchantEntry.HARVESTING);
        if (level <= 0 || !isWiseCrop(event.getBlock())) return;

        double chance = Math.min(0.60D, 0.16D * level);
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;

        boolean granted = false;
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            if (stack == null || stack.getType().isAir()) continue;
            if (!isCropDrop(stack.getType())) continue;
            if (stack.getAmount() < stack.getMaxStackSize()) {
                ItemStack next = stack.clone();
                next.setAmount(stack.getAmount() + 1);
                item.setItemStack(next);
            } else {
                ItemStack bonus = stack.clone();
                bonus.setAmount(1);
                Location origin = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
                if (hasTelekinesis(tool)) {
                    giveDrops(player, List.of(bonus), origin);
                } else {
                    event.getBlock().getWorld().dropItemNaturally(origin, bonus);
                }
            }
            granted = true;
            break;
        }
        if (granted) {
            event.getBlock().getWorld().spawnParticle(Particle.HAPPY_VILLAGER, event.getBlock().getLocation().add(0.5, 0.8, 0.5), 5, 0.25, 0.25, 0.25, 0.01);
        }
    }

    public boolean applyHarvestingBonus(Player player, ItemStack tool, List<ItemStack> drops) {
        if (player == null || player.getGameMode() == GameMode.CREATIVE || drops == null || drops.isEmpty()) return false;
        int level = storedEnchantLevel(tool, CustomEnchantEntry.HARVESTING);
        if (level <= 0 || ThreadLocalRandom.current().nextDouble() >= Math.min(0.60D, 0.16D * level)) return false;
        return addSingleCropDropBonus(drops);
    }

    static boolean addSingleCropDropBonus(List<ItemStack> drops) {
        if (drops == null || drops.isEmpty()) {
            return false;
        }
        int targetIndex = firstCropDropIndex(drops.stream()
            .map(stack -> stack == null || stack.getType().isAir() ? null : stack.getType().name())
            .toList());
        if (targetIndex < 0) {
            return false;
        }
        ItemStack stack = drops.get(targetIndex);
        if (stack.getAmount() < stack.getMaxStackSize()) {
            stack.setAmount(stack.getAmount() + 1);
        } else {
            ItemStack bonus = stack.clone();
            bonus.setAmount(1);
            drops.add(bonus);
        }
        return true;
    }

    static int firstCropDropIndex(List<String> materialNames) {
        if (materialNames == null) {
            return -1;
        }
        for (int index = 0; index < materialNames.size(); index++) {
            if (isCropDropName(materialNames.get(index))) {
                return index;
            }
        }
        return -1;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWiseCropBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        int wiseLevel = wiseLevelForHarvest(player, EquipmentSlot.HAND);
        if (wiseLevel <= 0) return;
        if (!isWiseCrop(event.getBlock())) return;

        spawnMissingWiseCropXp(event.getBlock().getLocation(), event.getExpToDrop());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWiseBlockHarvest(PlayerHarvestBlockEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        int wiseLevel = wiseLevelForHarvest(player, event.getHand());
        if (wiseLevel <= 0) return;
        if (!isWiseCrop(event.getHarvestedBlock()) && !containsCropDrop(event.getItemsHarvested())) return;

        spawnWiseCropXp(event.getHarvestedBlock().getLocation(), plugin.getConfigManager().wiseCropXp);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWiseXpGain(PlayerExpChangeEvent event) {
        int amount = event.getAmount();
        if (amount <= 0) return;
        if (plugin.getXpLecternListener() != null && plugin.getXpLecternListener().isLecternBottleXpPickup(event.getPlayer())) return;

        int wiseLevel = wiseLevel(event.getPlayer().getInventory().getItemInMainHand());
        if (wiseLevel <= 0) return;

        double bonusRate = wiseBonusRate(wiseLevel);
        if (bonusRate <= 0.0) return;

        UUID playerId = event.getPlayer().getUniqueId();
        double totalBonus = (amount * bonusRate) + wiseXpRemainders.getOrDefault(playerId, 0.0);
        int extra = (int) Math.floor(totalBonus);
        double remainder = totalBonus - extra;
        if (extra <= 0 && remainder <= 0.0) return;

        event.setAmount(amount + extra);
        if (remainder > 0.0001) {
            wiseXpRemainders.put(playerId, remainder);
        } else {
            wiseXpRemainders.remove(playerId);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDashUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!rightClick) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!hasDash(item)) {
            return;
        }

        if (rightClick && action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
            && isDashProtectedInteraction(event.getClickedBlock().getType())
            && !player.isSneaking()) {
            return;
        }

        event.setCancelled(true);
        useDash(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDoubleJumpFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!event.isFlying()) return;
        if (!doubleJumpArmedPlayers.contains(player.getUniqueId())) return;

        if (!canUseDoubleJump(player)) {
            clearDoubleJumpFlight(player);
            return;
        }

        event.setCancelled(true);
        player.setFlying(false);
        clearDoubleJumpFlight(player);
        if (!consumeDoubleJumpCost(player)) {
            player.sendActionBar(MM.deserialize("<red>Too hungry to double jump.</red>"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.55f, 0.75f);
            return;
        }

        launchDoubleJump(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        if (!isAncientCityLoot(event.getLootTable())) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= plugin.getConfigManager().doubleJumpAncientCityChestChance) {
            return;
        }

        ItemStack book = createDoubleJumpBook();
        if (plugin.getItemAuditManager() != null) {
            Location origin = event.getLootContext().getLocation();
            plugin.getItemAuditManager().recordGeneratedItem(
                book,
                "ancient_city_loot",
                "Generated in " + origin.getWorld().getName() + " at "
                    + origin.getBlockX() + "," + origin.getBlockY() + "," + origin.getBlockZ() + "."
            );
        }
        List<ItemStack> loot = new ArrayList<>(event.getLoot());
        loot.add(book);
        event.setLoot(loot);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipe(keyKingslayerRecipe);
        event.getPlayer().discoverRecipe(keySoulSiphonRecipe);
        event.getPlayer().discoverRecipe(keyEchoingRecipe);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        wiseXpRemainders.remove(playerId);
        telekinesisLootOwners.entrySet().removeIf(entry -> playerId.equals(entry.getValue()));
        essenceCaptureLootOwners.entrySet().removeIf(entry -> playerId.equals(entry.getValue().ownerId()));
        clearDoubleJumpFlight(event.getPlayer());
    }

    @EventHandler
    public void onEntityRemoved(EntityRemoveFromWorldEvent event) {
        UUID entityId = event.getEntity().getUniqueId();
        telekinesisLootOwners.remove(entityId);
        essenceCaptureLootOwners.remove(entityId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder rawHolder = top.getHolder(false);
        if (rawHolder instanceof EnchantRecipeMenuHolder recipeHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
                return;
            }
            int rawSlot = event.getRawSlot();
            if (rawSlot < 0 || rawSlot >= top.getSize()) {
                return;
            }
            if (rawSlot == 40) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        openEnchantMenu(player);
                    }
                });
                return;
            }
            if (rawSlot == 23 || rawSlot == 25) {
                CustomEnchantEntry enchant = recipeHolder.enchant();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    craftEnchantBookFromInventory(player, enchant);
                    openEnchantRecipeMenu(player, enchant);
                });
            }
            return;
        }
        if (!(rawHolder instanceof EnchantMenuHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return;
        }
        if (rawSlot == 40) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                player.closeInventory();
                player.performCommand("menu");
            });
            return;
        }
        if (rawSlot == 36) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && plugin.getCrossplayManager() != null) {
                    plugin.getCrossplayManager().sendAnvilAccessHint(player);
                }
            });
            return;
        }

        CustomEnchantEntry enchant = menuEnchant(event.getCurrentItem());
        if (enchant != null && hasCraftingRecipe(enchant)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    openEnchantRecipeMenu(player, enchant);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMenuDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof EnchantMenuHolder || holder instanceof EnchantRecipeMenuHolder) {
            event.setCancelled(true);
        }
    }

    private ItemStack createBook(CustomEnchantEntry enchant, int level) {
        int appliedLevel = enchant.clampLevel(level);
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta == null) return book;

        meta.displayName(CustomLoreUtil.displayName(
            CustomLoreUtil.Rarity.RARE,
            enchant.plainDisplay(appliedLevel) + " Book"
        ));
        ItemModelUtil.apply(meta, enchant.id + "_book");
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(keyCustomEnchantBook, PersistentDataType.STRING, enchant.id);
        meta.getPersistentDataContainer().set(keyFor(enchant), PersistentDataType.INTEGER, appliedLevel);
        meta.lore(buildBookLore(meta, enchant, appliedLevel));
        book.setItemMeta(meta);
        return book;
    }

    private ItemStack createMenuIcon(CustomEnchantEntry enchant) {
        ItemStack icon = new ItemStack(enchant.icon);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;

        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.RARE, enchant.plainName()));
        meta.lore(buildMenuLore(enchant));
        meta.getPersistentDataContainer().set(keyEnchantMenuId, PersistentDataType.STRING, enchant.id);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack createMenuItem(Material material, String displayName, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(MM.deserialize(displayName));
        if (!loreLines.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(MM.deserialize(line));
            }
            meta.lore(CustomLoreUtil.wrapLoreLines(lore));
        }
        item.setItemMeta(meta);
        return item;
    }

    private void registerCraftOnlyRecipe(
        NamespacedKey key,
        ItemStack result,
        List<String> shape,
        Map<Character, RecipeChoice> choices
    ) {
        if (result == null || result.getType().isAir()) {
            return;
        }

        Bukkit.removeRecipe(key);
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(shape.toArray(String[]::new));
        for (Map.Entry<Character, RecipeChoice> entry : choices.entrySet()) {
            if (entry.getValue() != null) {
                recipe.setIngredient(entry.getKey(), entry.getValue());
            }
        }
        Bukkit.addRecipe(recipe);
    }

    private CustomEnchantEntry craftOnlyRecipeEntry(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) {
            return null;
        }
        NamespacedKey key = keyed.getKey();
        if (keyKingslayerRecipe.equals(key)) {
            return CustomEnchantEntry.KINGSLAYER;
        }
        if (keySoulSiphonRecipe.equals(key)) {
            return CustomEnchantEntry.SOUL_SIPHON;
        }
        if (keyEchoingRecipe.equals(key)) {
            return CustomEnchantEntry.ECHOING;
        }
        return null;
    }

    private boolean usesOnlyValidCraftOnlyIngredients(CustomEnchantEntry enchant, ItemStack[] matrix) {
        if (enchant == null || matrix == null) {
            return false;
        }
        List<InventoryRecipeUtil.Ingredient> ingredients = craftOnlyRecipeIngredients(enchant);
        if (ingredients.isEmpty()) {
            return false;
        }

        for (ItemStack item : matrix) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            boolean valid = false;
            for (InventoryRecipeUtil.Ingredient ingredient : ingredients) {
                if (ingredient.matcher() != null && ingredient.matcher().test(item)) {
                    valid = true;
                    break;
                }
            }
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    private RecipeChoice exactRelicChoice(String relicId) {
        if (plugin.getSeasonRelicManager() == null) {
            return new RecipeChoice.MaterialChoice(Material.BARRIER);
        }
        ItemStack relic = plugin.getSeasonRelicManager().createRelicItem(relicId);
        if (relic == null || relic.getType().isAir()) {
            return new RecipeChoice.MaterialChoice(Material.BARRIER);
        }
        return new RecipeChoice.ExactChoice(relic);
    }

    private List<Component> buildBookLore(ItemMeta meta, CustomEnchantEntry enchant, int level) {
        List<String> topLines = new ArrayList<>();
        topLines.add("<gray>Levels: <white>" + enchant.levelDisplay(level) + "</white></gray>");
        return CustomLoreUtil.buildStyledLore(
            meta,
            Material.ENCHANTED_BOOK,
            CustomLoreUtil.Rarity.RARE.label(),
            "BOOK",
            topLines,
            List.of(CustomLoreUtil.section(
                "Enchant Effect",
                enchant.plainName(),
                enchantDescriptionLines(enchant, level, true)
            ))
        );
    }

    private List<Component> buildMenuLore(CustomEnchantEntry enchant) {
        List<String> topLines = new ArrayList<>();
        topLines.add("<gray>Levels: <white>" + enchant.levels + "</white></gray>");
        topLines.add("<gray>Enchant Table: <white>" + (enchant.enchantTableEligible ? "Yes" : "No") + "</white></gray>");
        if (hasCraftingRecipe(enchant)) {
            topLines.add("<gray>Craftable Book: <white>Yes</white></gray>");
            topLines.add("<yellow>Click to view recipe.</yellow>");
        }
        return CustomLoreUtil.buildStyledLore(
            enchant.icon,
            CustomLoreUtil.Rarity.RARE.label(),
            "ICON",
            topLines,
            List.of(CustomLoreUtil.section(
                "Enchant Effect",
                enchant.plainName(),
                enchantDescriptionLines(enchant, enchant.maxLevel(), false)
            ))
        );
    }

    private String[] enchantDescriptionLines(CustomEnchantEntry enchant, int level, boolean specificLevelBook) {
        List<String> lines = new ArrayList<>();
        for (String line : enchant.description(plugin.getConfigManager(), level, specificLevelBook)) {
            lines.add("<gray>" + line + "</gray>");
        }
        lines.add("<gray>Apply in an <white>anvil</white> to a valid item.</gray>");
        if (enchant.enchantTableEligible) {
            lines.add("<gray>Also obtainable from an <white>enchant table</white>.</gray>");
        }
        return lines.toArray(String[]::new);
    }

    private void openEnchantRecipeMenu(Player player, CustomEnchantEntry enchant) {
        Inventory inventory = Bukkit.createInventory(
            new EnchantRecipeMenuHolder(enchant),
            45,
            BedrockCompat.menuTitle(
                player,
                MM.deserialize("<gradient:#00d4ff:#73ff9d><bold>" + enchant.plainName() + " Recipe</bold></gradient>"),
                enchant.plainName() + " Recipe"
            )
        );
        ItemStack filler = createMenuItem(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isFrameSlot(slot, inventory.getSize())) {
                inventory.setItem(slot, filler);
            }
        }

        inventory.setItem(4, createMenuItem(
            Material.ENCHANTED_BOOK,
            "<gradient:#00d4ff:#73ff9d><bold>" + enchant.plainName() + " Book</bold></gradient>",
            List.of(
                "<gray>Boss-forged custom enchant recipe.</gray>",
                "<gray>Click the crafting table or output book to craft from inventory.</gray>"
            )
        ));

        int[] matrixSlots = {10, 11, 12, 19, 20, 21, 28, 29, 30};
        ItemStack[] matrix = craftOnlyRecipeMatrix(enchant);
        for (int i = 0; i < matrixSlots.length && i < matrix.length; i++) {
            if (matrix[i] != null && !matrix[i].getType().isAir()) {
                inventory.setItem(matrixSlots[i], matrix[i]);
            }
        }
        inventory.setItem(23, createMenuItem(Material.CRAFTING_TABLE, "<gold><bold>Craft From Inventory</bold></gold>", List.of(
            "<gray>Click to craft server-side using your inventory.</gray>",
            "<gray>Java players can still use the exact layout shown.</gray>",
            "<gray>The book ingredient is a normal <white>Book</white>.</gray>",
            "<gray>Boss materials must be real Veil drops.</gray>"
        )));
        inventory.setItem(25, createBook(enchant, 1));
        inventory.setItem(40, createMenuItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to custom enchants.</gray>")));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }

    private static boolean isFrameSlot(int slot, int size) {
        return slot < 9 || slot >= size - 9 || slot % 9 == 0 || slot % 9 == 8;
    }

    private ItemStack[] craftOnlyRecipeMatrix(CustomEnchantEntry enchant) {
        ItemStack[] matrix = new ItemStack[9];
        switch (enchant) {
            case KINGSLAYER -> {
                matrix[0] = relicIngredient("crimson_rib");
                matrix[1] = ingredient(Material.BLAZE_ROD);
                matrix[2] = relicIngredient("crimson_rib");
                matrix[3] = ingredient(Material.BLAZE_ROD);
                matrix[4] = ingredient(Material.BOOK);
                matrix[5] = ingredient(Material.BLAZE_ROD);
                matrix[6] = relicIngredient("sculk_heart");
                matrix[7] = ingredient(Material.NETHER_STAR);
                matrix[8] = relicIngredient("sculk_heart");
            }
            case SOUL_SIPHON -> {
                matrix[0] = relicIngredient("verdant_heart");
                matrix[1] = ingredient(Material.GHAST_TEAR);
                matrix[2] = relicIngredient("verdant_heart");
                matrix[3] = relicIngredient("crimson_rib");
                matrix[4] = ingredient(Material.BOOK);
                matrix[5] = relicIngredient("crimson_rib");
                matrix[6] = ingredient(Material.SOUL_SAND);
                matrix[7] = ingredient(Material.GOLDEN_APPLE);
                matrix[8] = ingredient(Material.SOUL_SAND);
            }
            case ECHOING -> {
                matrix[0] = relicIngredient("titan_gear");
                matrix[1] = ingredient(Material.ECHO_SHARD);
                matrix[2] = relicIngredient("titan_gear");
                matrix[3] = ingredient(Material.AMETHYST_SHARD);
                matrix[4] = ingredient(Material.ECHO_SHARD);
                matrix[5] = ingredient(Material.AMETHYST_SHARD);
                matrix[6] = relicIngredient("sculk_heart");
                matrix[7] = ingredient(Material.BOOK);
                matrix[8] = relicIngredient("sculk_heart");
            }
            default -> {
            }
        }
        return matrix;
    }

    private ItemStack relicIngredient(String relicId) {
        if (plugin.getSeasonRelicManager() != null) {
            ItemStack relic = plugin.getSeasonRelicManager().createRelicItem(relicId);
            if (relic != null && !relic.getType().isAir()) {
                return relic;
            }
        }
        ItemStack missing = ingredient(Material.BARRIER);
        ItemMeta meta = missing.getItemMeta();
        if (meta != null) {
            meta.displayName(MM.deserialize("<red>Missing Boss Material</red>"));
            meta.lore(List.of(MM.deserialize("<gray>Relic id: <white>" + relicId + "</white></gray>")));
            missing.setItemMeta(meta);
        }
        return missing;
    }

    private ItemStack ingredient(Material material) {
        return new ItemStack(material);
    }

    private boolean hasCraftingRecipe(CustomEnchantEntry enchant) {
        return enchant == CustomEnchantEntry.KINGSLAYER
            || enchant == CustomEnchantEntry.SOUL_SIPHON
            || enchant == CustomEnchantEntry.ECHOING;
    }

    private boolean craftEnchantBookFromInventory(Player player, CustomEnchantEntry enchant) {
        if (!hasCraftingRecipe(enchant)) {
            player.sendMessage(MessageUtil.error("That enchant does not have a craftable book recipe."));
            return false;
        }
        if (plugin.getSeasonRelicManager() == null) {
            player.sendMessage(MessageUtil.error("Boss material recipes are not ready yet."));
            return false;
        }

        List<InventoryRecipeUtil.Ingredient> ingredients = craftOnlyRecipeIngredients(enchant);
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
        ItemStack reward = createBook(enchant, 1);
        if (!InventoryRecipeUtil.canFitRewardAfterRemovingIngredients(player, ingredients, reward)) {
            player.sendMessage(MessageUtil.warn("Clear enough inventory space before crafting <white>" + enchant.plainName() + " Book</white>."));
            return false;
        }
        if (!InventoryRecipeUtil.removeIngredients(player, ingredients)) {
            player.sendMessage(MessageUtil.error("Those ingredients changed before the craft finished. Try again."));
            return false;
        }

        if (plugin.getItemAuditManager() != null) {
            plugin.getItemAuditManager().recordKnownAcquisition(
                player,
                reward,
                "custom_enchant_reliquary_craft",
                "Crafted " + enchant.plainName() + " Book from the custom enchant menu."
            );
        }
        InventoryRecipeUtil.giveOrDrop(player, reward);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.25f);
        player.sendMessage(MessageUtil.success("Crafted <white>" + enchant.plainName() + " Book</white>."));
        return true;
    }

    private List<InventoryRecipeUtil.Ingredient> craftOnlyRecipeIngredients(CustomEnchantEntry enchant) {
        return switch (enchant) {
            case KINGSLAYER -> List.of(
                relicIngredientRequirement("crimson_rib", 2),
                InventoryRecipeUtil.plainMaterial(plugin, Material.BLAZE_ROD, 3),
                InventoryRecipeUtil.plainMaterial(plugin, Material.BOOK, 1),
                relicIngredientRequirement("sculk_heart", 2),
                InventoryRecipeUtil.plainMaterial(plugin, Material.NETHER_STAR, 1)
            );
            case SOUL_SIPHON -> List.of(
                relicIngredientRequirement("verdant_heart", 2),
                InventoryRecipeUtil.plainMaterial(plugin, Material.GHAST_TEAR, 1),
                relicIngredientRequirement("crimson_rib", 2),
                InventoryRecipeUtil.plainMaterial(plugin, Material.BOOK, 1),
                InventoryRecipeUtil.plainMaterial(plugin, Material.SOUL_SAND, 2),
                InventoryRecipeUtil.plainMaterial(plugin, Material.GOLDEN_APPLE, 1)
            );
            case ECHOING -> List.of(
                relicIngredientRequirement("titan_gear", 2),
                InventoryRecipeUtil.plainMaterial(plugin, Material.ECHO_SHARD, 2),
                InventoryRecipeUtil.plainMaterial(plugin, Material.AMETHYST_SHARD, 2),
                relicIngredientRequirement("sculk_heart", 2),
                InventoryRecipeUtil.plainMaterial(plugin, Material.BOOK, 1)
            );
            default -> List.of();
        };
    }

    private InventoryRecipeUtil.Ingredient relicIngredientRequirement(String relicId, int amount) {
        String name = plugin.getSeasonRelicManager() == null ? prettyRelicId(relicId) : plugin.getSeasonRelicManager().displayNameFor(relicId);
        return new InventoryRecipeUtil.Ingredient(
            name == null ? prettyRelicId(relicId) : name,
            amount,
            item -> plugin.getSeasonRelicManager() != null && relicId.equals(plugin.getSeasonRelicManager().relicId(item))
        );
    }

    private String prettyRelicId(String relicId) {
        StringBuilder out = new StringBuilder();
        for (String part : relicId.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private CustomEnchantEntry menuEnchant(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String id = meta.getPersistentDataContainer().get(keyEnchantMenuId, PersistentDataType.STRING);
        if (id == null || id.isBlank()) {
            return null;
        }
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MENU_ENTRIES) {
            if (enchant.id.equalsIgnoreCase(id)) {
                return enchant;
            }
        }
        return null;
    }

    private BookEnchantData bookEnchant(ItemStack item) {
        if (customBookPayloadState(item) != CustomBookPayloadState.VALID) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        String id = meta.getPersistentDataContainer().get(keyCustomEnchantBook, PersistentDataType.STRING);
        if (id == null) return null;

        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            if (enchant.id.equalsIgnoreCase(id)) {
                int level = storedEnchantLevel(meta, enchant);
                return new BookEnchantData(enchant, level);
            }
        }
        return null;
    }

    private CustomBookPayloadState customBookPayloadState(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) {
            return CustomBookPayloadState.NONE;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return CustomBookPayloadState.NONE;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        boolean markerPresent = pdc.getKeys().contains(keyCustomEnchantBook);
        String marker = pdc.get(keyCustomEnchantBook, PersistentDataType.STRING);
        Map<String, Integer> levels = new LinkedHashMap<>();
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            NamespacedKey key = keyFor(enchant);
            if (pdc.getKeys().contains(key)) {
                levels.put(enchant.id, pdc.get(key, PersistentDataType.INTEGER));
            }
        }
        if (!markerPresent && levels.isEmpty()) {
            return CustomBookPayloadState.NONE;
        }

        boolean hasVanillaEnchantData = !meta.getEnchants().isEmpty()
            || meta instanceof EnchantmentStorageMeta storageMeta && storageMeta.hasStoredEnchants();
        boolean hasCompetingBookMarker = pdc.getKeys().contains(keyReplenishBook);
        return classifyCustomBookPayload(
            markerPresent ? (marker == null ? "\u0000" : marker) : null,
            levels,
            item.getAmount(),
            hasVanillaEnchantData,
            hasCompetingBookMarker
        );
    }

    static boolean isValidCustomBookAmount(int amount) {
        return amount == 1;
    }

    static CustomBookPayloadState classifyCustomBookPayload(
        String marker,
        Map<String, Integer> levels,
        int amount,
        boolean hasVanillaEnchantData,
        boolean hasCompetingBookMarker
    ) {
        Map<String, Integer> safeLevels = levels == null ? Map.of() : levels;
        boolean hasMarker = marker != null && !marker.isBlank();
        if (!hasMarker && safeLevels.isEmpty()) {
            return CustomBookPayloadState.NONE;
        }
        if (!isValidCustomBookAmount(amount)
            || !hasMarker
            || hasVanillaEnchantData
            || hasCompetingBookMarker
            || safeLevels.size() != 1) {
            return CustomBookPayloadState.INVALID;
        }

        String selectedId = marker.toLowerCase(Locale.ROOT);
        Integer maximumLevel = CUSTOM_ENCHANT_MAX_LEVELS.get(selectedId);
        if (maximumLevel == null || !safeLevels.containsKey(selectedId)) {
            return CustomBookPayloadState.INVALID;
        }
        Integer level = safeLevels.get(selectedId);
        if (level == null || level <= 0 || level > maximumLevel) {
            return CustomBookPayloadState.INVALID;
        }
        return CustomBookPayloadState.VALID;
    }

    private static CustomEnchantEntry customEnchantById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            if (enchant.id.equalsIgnoreCase(id)) {
                return enchant;
            }
        }
        return null;
    }

    private boolean hasCustomEnchantBookMarker(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null
            && meta.getPersistentDataContainer().getKeys().contains(keyCustomEnchantBook);
    }

    private boolean hasAnyManagedEnchantData(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            if (pdc.getKeys().contains(keyFor(enchant))) {
                return true;
            }
        }
        return pdc.getKeys().contains(keyCustomEnchantBook);
    }

    private boolean canApply(ItemStack item, CustomEnchantEntry enchant, int level) {
        return item != null
            && item.getType() != Material.AIR
            && enchant.applicable.test(item.getType())
            && (enchant != CustomEnchantEntry.SMELTING_TOUCH
                || item.getEnchantmentLevel(Enchantment.SILK_TOUCH) <= 0)
            && storedEnchantLevel(item, enchant) < enchant.clampLevel(level);
    }

    private List<CustomEnchantEntry> enchantTableCandidates(ItemStack item, int expLevelCost) {
        if (item == null || item.getType() == Material.AIR) return List.of();

        List<CustomEnchantEntry> candidates = new ArrayList<>();
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            if (!enchant.enchantTableEligible) continue;
            if (expLevelCost < enchant.enchantTableMinCost) continue;
            if (!canApply(item, enchant, enchantTableLevel(enchant, expLevelCost))) continue;
            candidates.add(enchant);
        }
        return candidates;
    }

    private CustomEnchantEntry pickEnchantTableEntry(List<CustomEnchantEntry> candidates) {
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);

        List<CustomEnchantEntry> pool = new ArrayList<>();
        for (CustomEnchantEntry enchant : candidates) {
            for (int i = 0; i < enchant.enchantTableWeight; i++) {
                pool.add(enchant);
            }
        }
        if (pool.isEmpty()) {
            return candidates.get(0);
        }

        Collections.shuffle(pool, ThreadLocalRandom.current());
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private ItemStack applyEnchant(ItemStack item, CustomEnchantEntry enchant, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !canApply(item, enchant, level)) return item;

        removeInvalidManagedEnchantData(meta, item.getType());
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyFor(enchant), PersistentDataType.INTEGER, enchant.clampLevel(level));
        applyManagedEnchantLore(meta);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack applyManagedEnchant(ItemStack item, String enchantId, int level) {
        if (item == null || item.getType().isAir() || enchantId == null || enchantId.isBlank()) {
            return item;
        }
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            if (enchant.id.equalsIgnoreCase(enchantId) && enchant.applicable.test(item.getType())) {
                return applyEnchant(item, enchant, level);
            }
        }
        return item;
    }

    public ItemStack preserveManagedEnchants(ItemStack source, ItemStack result) {
        if (source == null || result == null || result.getType().isAir()) {
            return result;
        }
        ItemMeta sourceMeta = source.getItemMeta();
        ItemMeta resultMeta = result.getItemMeta();
        if (sourceMeta == null || resultMeta == null) {
            return result;
        }

        PersistentDataContainer resultPdc = resultMeta.getPersistentDataContainer();
        resultPdc.remove(keyCustomEnchantBook);
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            resultPdc.remove(keyFor(enchant));
        }
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            if (!enchant.applicable.test(result.getType())) {
                continue;
            }
            int level = storedEnchantLevel(sourceMeta, enchant);
            if (level > 0) {
                resultPdc.set(keyFor(enchant), PersistentDataType.INTEGER, level);
            }
        }
        applyManagedEnchantLore(resultMeta);
        result.setItemMeta(resultMeta);
        return result;
    }

    private ItemStack stripManagedEnchants(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        boolean wasCustomEnchantBook = pdc.has(keyCustomEnchantBook, PersistentDataType.STRING);
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            pdc.remove(keyFor(enchant));
        }
        pdc.remove(keyCustomEnchantBook);
        applyManagedEnchantLore(meta);
        item.setItemMeta(meta);
        if (wasCustomEnchantBook && plugin.getItemAuditManager() != null) {
            plugin.getItemAuditManager().clearTrackedIdentity(item, "custom_enchant:");
        }
        return item;
    }

    private boolean giveAnvilResult(Player player, InventoryClickEvent event, ItemStack result) {
        if (event.isShiftClick()) {
            if (player.getInventory().firstEmpty() == -1) {
                player.sendMessage(MessageUtil.warn("You need at least one empty inventory slot."));
                return false;
            }
            InventoryRecipeUtil.giveOrDrop(player, result);
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

    private boolean canReceiveAnvilResult(Player player, InventoryClickEvent event) {
        if (!isAllowedResultClick(event.getClick())) {
            player.sendMessage(MessageUtil.warn("Use a normal click or shift-click to take this result."));
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

    private boolean isAnvilResultSlot(InventoryClickEvent event) {
        return event.getView().getTopInventory().getType() == InventoryType.ANVIL
            && (event.getClickedInventory() == event.getView().getTopInventory() || event.getRawSlot() == 2)
            && (event.getSlotType() == InventoryType.SlotType.RESULT || event.getRawSlot() == 2);
    }

    private boolean isGrindstoneResultSlot(InventoryClickEvent event) {
        return event.getView().getTopInventory().getType() == InventoryType.GRINDSTONE
            && event.getRawSlot() == 2;
    }

    private CustomEnchantAnvilResult customEnchantAnvilResult(ItemStack left, ItemStack right) {
        BookEnchantData rightEnchant = bookEnchant(right);
        if (rightEnchant == null) {
            return null;
        }

        BookEnchantData leftEnchant = bookEnchant(left);
        if (leftEnchant != null) {
            if (leftEnchant.enchant() != rightEnchant.enchant()) {
                return null;
            }
            int level = combinedBookLevel(leftEnchant, rightEnchant);
            if (level <= leftEnchant.level()) {
                return null;
            }
            ItemStack result = createBook(leftEnchant.enchant(), level);
            return new CustomEnchantAnvilResult(
                result,
                leftEnchant.enchant(),
                level,
                customEnchantAnvilCost(leftEnchant.enchant(), level),
                "Combined",
                leftEnchant.enchant().plainDisplay(level)
            );
        }

        if (left == null || left.getType() == Material.AIR || !rightEnchant.enchant().applicable.test(left.getType())) {
            return null;
        }

        int currentLevel = storedEnchantLevel(left, rightEnchant.enchant());
        int level = targetItemEnchantLevel(currentLevel, rightEnchant);
        if (level <= currentLevel || !canApply(left, rightEnchant.enchant(), level)) {
            return null;
        }

        ItemStack result = applyEnchant(left.clone(), rightEnchant.enchant(), level);
        return new CustomEnchantAnvilResult(
            result,
            rightEnchant.enchant(),
            level,
            customEnchantAnvilCost(rightEnchant.enchant(), level),
            "Applied",
            rightEnchant.enchant().plainDisplay(level)
        );
    }

    private CustomEnchantAnvilResult customEnchantItemMergeResult(ItemStack left, ItemStack right, ItemStack vanillaResult) {
        if (left == null || right == null || left.getType().isAir() || right.getType().isAir()) {
            return null;
        }
        if (left.getType() != right.getType() || hasCustomEnchantBookMarker(left) || hasCustomEnchantBookMarker(right)) {
            return null;
        }

        ItemStack result = vanillaResult == null || vanillaResult.getType().isAir()
            ? left.clone()
            : vanillaResult.clone();
        result = stripManagedEnchants(result);
        if (result.getType().isAir()) {
            return null;
        }

        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return null;
        }

        boolean changedByRightItem = false;
        int costLevels = 0;
        PersistentDataContainer resultPdc = meta.getPersistentDataContainer();
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            if (!enchant.applicable.test(result.getType())) {
                continue;
            }
            int leftLevel = storedEnchantLevel(left, enchant);
            int rightLevel = storedEnchantLevel(right, enchant);
            int mergedLevel = combinedItemEnchantLevel(enchant, leftLevel, rightLevel);
            if (mergedLevel <= 0) {
                continue;
            }
            if (mergedLevel > leftLevel || (leftLevel == 0 && rightLevel > 0)) {
                changedByRightItem = true;
                costLevels += mergedLevel;
            }
            resultPdc.set(keyFor(enchant), PersistentDataType.INTEGER, mergedLevel);
        }
        applyManagedEnchantLore(meta);
        result.setItemMeta(meta);

        ReplenishListener replenish = plugin.getReplenishListener();
        boolean leftReplenish = replenish != null && replenish.hasReplenish(left);
        boolean rightReplenish = replenish != null && replenish.hasReplenish(right);
        if (rightReplenish && !leftReplenish) {
            changedByRightItem = true;
            costLevels += 1;
        }
        if (replenish != null && (leftReplenish || rightReplenish)) {
            result = replenish.preserveReplenish(leftReplenish ? left : right, result);
        }

        if (CustomLoreUtil.hasSmeltingSilkConflict(result)) {
            return null;
        }

        if (!changedByRightItem) {
            return null;
        }

        int cost = CUSTOM_ENCHANT_ANVIL_BASE_COST + (Math.max(1, costLevels) * CUSTOM_ENCHANT_ANVIL_LEVEL_COST);
        return new CustomEnchantAnvilResult(
            result,
            null,
            0,
            cost,
            "Merged",
            "custom enchants"
        );
    }

    private int combinedItemEnchantLevel(CustomEnchantEntry enchant, int leftLevel, int rightLevel) {
        return combinedEnchantLevel(leftLevel, rightLevel, enchant.maxLevel());
    }

    private int combinedBookLevel(BookEnchantData left, BookEnchantData right) {
        return combinedEnchantLevel(left.level(), right.level(), left.enchant().maxLevel());
    }

    private int targetItemEnchantLevel(int currentLevel, BookEnchantData book) {
        return combinedEnchantLevel(currentLevel, book.level(), book.enchant().maxLevel());
    }

    static int combinedEnchantLevel(int leftLevel, int rightLevel, int maximumLevel) {
        int max = Math.max(1, maximumLevel);
        int left = Math.max(0, Math.min(max, leftLevel));
        int right = Math.max(0, Math.min(max, rightLevel));
        if (left == 0) {
            return right;
        }
        if (right == 0) {
            return left;
        }
        if (left == right && left < max) {
            return left + 1;
        }
        return Math.max(left, right);
    }

    private void configureCustomEnchantAnvil(PrepareAnvilEvent event, CustomEnchantAnvilResult result) {
        if (!(event.getView() instanceof org.bukkit.inventory.view.AnvilView anvilView)) {
            return;
        }

        int cost = result.cost();
        anvilView.setRepairCost(cost);
        anvilView.setRepairItemCountCost(1);
        anvilView.setMaximumRepairCost(Math.max(40, cost));
    }

    private int customEnchantAnvilCost(CustomEnchantEntry enchant, int enchantLevel) {
        int level = Math.max(1, enchant.clampLevel(enchantLevel));
        return CUSTOM_ENCHANT_ANVIL_BASE_COST + (level * CUSTOM_ENCHANT_ANVIL_LEVEL_COST);
    }

    private boolean canPayAnvilCost(Player player, int xpCost) {
        if (xpCost <= 0 || player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        if (player.getLevel() >= xpCost) {
            return true;
        }
        player.sendMessage(MessageUtil.warn("You need <white>" + xpCost + "</white> XP levels to apply that enchant."));
        return false;
    }

    private void chargeAnvilCost(Player player, int xpCost) {
        if (xpCost <= 0 || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        player.setLevel(Math.max(0, player.getLevel() - xpCost));
    }

    private NamespacedKey keyFor(CustomEnchantEntry enchant) {
        return switch (enchant) {
            case DELICATE -> keyDelicate;
            case TELEKINESIS -> keyTelekinesis;
            case SMELTING_TOUCH -> keySmeltingTouch;
            case WISE -> keyWise;
            case DOUBLE_JUMP -> keyDoubleJump;
            case DASH -> keyDash;
            case FROSTBITE -> keyFrostbite;
            case HARVESTING -> keyHarvesting;
            case BULWARK -> keyBulwark;
            case REINFORCED -> keyReinforced;
            case KINGSLAYER -> keyKingslayer;
            case SOUL_SIPHON -> keySoulSiphon;
            case ECHOING -> keyEchoing;
            case ESSENCE_CAPTURE -> keyEssenceCapture;
            default -> throw new IllegalArgumentException("Unsupported managed enchant: " + enchant.id);
        };
    }

    public boolean hasManagedEnchant(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            if (hasEnchant(item, enchant)) {
                return true;
            }
        }
        return false;
    }

    public boolean normalizeManagedEnchantData(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (item.getType() == Material.ENCHANTED_BOOK) {
            return normalizeLegacyCustomEnchantBook(item);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        boolean changed = false;
        if (pdc.getKeys().contains(keyCustomEnchantBook)) {
            pdc.remove(keyCustomEnchantBook);
            changed = true;
        }
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            NamespacedKey key = keyFor(enchant);
            if (!pdc.getKeys().contains(key)) {
                continue;
            }
            Integer raw = pdc.get(key, PersistentDataType.INTEGER);
            if (raw == null || raw <= 0 || !enchant.applicable.test(item.getType())) {
                pdc.remove(key);
                changed = true;
                continue;
            }
            int clamped = enchant.clampLevel(raw);
            if (raw != clamped) {
                pdc.set(key, PersistentDataType.INTEGER, clamped);
                changed = true;
            }
        }
        if (changed) {
            item.setItemMeta(meta);
        }
        return changed;
    }

    private boolean normalizeLegacyCustomEnchantBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK || item.getAmount() != 1) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        boolean markerPresent = pdc.getKeys().contains(keyCustomEnchantBook);
        String marker = pdc.get(keyCustomEnchantBook, PersistentDataType.STRING);
        Map<String, Integer> levels = new LinkedHashMap<>();
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            NamespacedKey key = keyFor(enchant);
            if (pdc.getKeys().contains(key)) {
                levels.put(enchant.id, pdc.get(key, PersistentDataType.INTEGER));
            }
        }
        String canonicalId = canonicalCustomBookId(
            markerPresent ? marker : null,
            levels,
            pdc.getKeys().contains(keyReplenishBook)
        );
        CustomEnchantEntry selected = customEnchantById(canonicalId);
        if (selected == null) {
            return false;
        }

        Integer rawLevel = levels.get(selected.id);
        if (rawLevel == null || rawLevel <= 0) {
            return false;
        }
        int level = selected.clampLevel(rawLevel);
        boolean changed = !selected.id.equals(marker) || rawLevel != level || levels.size() != 1;
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            NamespacedKey key = keyFor(enchant);
            if (enchant != selected && pdc.getKeys().contains(key)) {
                pdc.remove(key);
                changed = true;
            }
        }
        pdc.set(keyCustomEnchantBook, PersistentDataType.STRING, selected.id);
        pdc.set(keyFor(selected), PersistentDataType.INTEGER, level);

        for (Enchantment enchantment : new ArrayList<>(meta.getEnchants().keySet())) {
            changed |= meta.removeEnchant(enchantment);
        }
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            for (Enchantment enchantment : new ArrayList<>(storageMeta.getStoredEnchants().keySet())) {
                changed |= storageMeta.removeStoredEnchant(enchantment);
            }
        }
        if (!meta.hasMaxStackSize() || meta.getMaxStackSize() != 1) {
            meta.setMaxStackSize(1);
            changed = true;
        }
        if (!changed) {
            return false;
        }
        meta.displayName(CustomLoreUtil.displayName(
            CustomLoreUtil.Rarity.RARE,
            selected.plainDisplay(level) + " Book"
        ));
        ItemModelUtil.apply(meta, selected.id + "_book");
        applyManagedEnchantLore(meta);
        item.setItemMeta(meta);
        return true;
    }

    static String canonicalCustomBookId(String marker, Map<String, Integer> levels, boolean hasCompetingBookMarker) {
        if (hasCompetingBookMarker || levels == null || levels.isEmpty()) {
            return null;
        }
        if (marker != null && !marker.isBlank()) {
            String normalized = marker.toLowerCase(Locale.ROOT);
            Integer maximum = CUSTOM_ENCHANT_MAX_LEVELS.get(normalized);
            Integer level = levels.get(normalized);
            return maximum != null && level != null && level > 0 ? normalized : null;
        }
        if (levels.size() != 1) {
            return null;
        }
        Map.Entry<String, Integer> only = levels.entrySet().iterator().next();
        Integer maximum = CUSTOM_ENCHANT_MAX_LEVELS.get(only.getKey());
        Integer level = only.getValue();
        return maximum != null && level != null && level > 0 ? only.getKey() : null;
    }

    public boolean hasOnlyManagedEnchantData(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String namespace = plugin.getName().toLowerCase(Locale.ROOT);
        boolean hasManagedKey = false;
        for (NamespacedKey key : pdc.getKeys()) {
            if (!namespace.equals(key.getNamespace())) {
                continue;
            }
            if (!isManagedEnchantDataKey(key)) {
                return false;
            }
            hasManagedKey = true;
        }
        return hasManagedKey;
    }

    public boolean isManagedEnchantDataKey(NamespacedKey key) {
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            if (keyFor(enchant).equals(key)) {
                return true;
            }
        }
        return false;
    }

    public List<ManagedEnchantOption> managedEnchantOptions(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return List.of();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return List.of();
        }
        List<ManagedEnchantOption> options = new ArrayList<>();
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            int level = storedEnchantLevel(meta, enchant);
            if (level > 0 && enchant.applicable.test(item.getType())) {
                options.add(new ManagedEnchantOption(enchant.id, enchant.plainName(), level, enchant.maxLevel()));
            }
        }
        return options;
    }

    public int upgradeManagedEnchant(ItemStack item, String enchantId, int increase) {
        if (item == null || item.getType() == Material.AIR || enchantId == null || enchantId.isBlank()) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            if (!enchant.id.equalsIgnoreCase(enchantId) || !enchant.applicable.test(item.getType())) {
                continue;
            }
            int current = storedEnchantLevel(meta, enchant);
            if (current <= 0) {
                return 0;
            }
            int next = Math.min(enchant.maxLevel(), current + Math.max(1, increase));
            if (next <= current) {
                return 0;
            }
            meta.getPersistentDataContainer().set(keyFor(enchant), PersistentDataType.INTEGER, next);
            applyManagedEnchantLore(meta);
            item.setItemMeta(meta);
            return next - current;
        }
        return 0;
    }

    private boolean hasDelicate(ItemStack item) {
        return hasEnchant(item, CustomEnchantEntry.DELICATE);
    }

    private boolean hasTelekinesis(ItemStack item) {
        return hasEnchant(item, CustomEnchantEntry.TELEKINESIS);
    }

    private boolean hasSmeltingTouch(ItemStack item) {
        return hasEnchant(item, CustomEnchantEntry.SMELTING_TOUCH);
    }

    private int wiseLevel(ItemStack item) {
        return storedEnchantLevel(item, CustomEnchantEntry.WISE);
    }

    private int wiseLevelForHarvest(Player player, EquipmentSlot hand) {
        if (player == null) {
            return 0;
        }
        EquipmentSlot safeHand = hand == null ? EquipmentSlot.HAND : hand;
        ItemStack item = safeHand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
        return wiseLevel(item);
    }

    private boolean hasDoubleJump(ItemStack item) {
        return hasEnchant(item, CustomEnchantEntry.DOUBLE_JUMP);
    }

    private boolean hasDash(ItemStack item) {
        return hasEnchant(item, CustomEnchantEntry.DASH);
    }

    private boolean hasEssenceCapture(ItemStack item) {
        return hasEnchant(item, CustomEnchantEntry.ESSENCE_CAPTURE);
    }

    private boolean hasEnchant(ItemStack item, CustomEnchantEntry enchant) {
        return storedEnchantLevel(item, enchant) > 0;
    }

    private int storedEnchantLevel(ItemStack item, CustomEnchantEntry enchant) {
        if (item == null || item.getType() == Material.AIR) return 0;
        ItemMeta meta = item.getItemMeta();
        return storedEnchantLevel(meta, enchant);
    }

    private int storedEnchantLevel(ItemMeta meta, CustomEnchantEntry enchant) {
        int stored = rawStoredEnchantLevel(meta, enchant);
        return stored <= 0 ? 0 : enchant.clampLevel(stored);
    }

    private int rawStoredEnchantLevel(ItemMeta meta, CustomEnchantEntry enchant) {
        if (meta == null) return 0;
        Integer stored = meta.getPersistentDataContainer().get(keyFor(enchant), PersistentDataType.INTEGER);
        return stored == null ? 0 : stored;
    }

    private void removeInvalidManagedEnchantData(ItemMeta meta, Material material) {
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (material != Material.ENCHANTED_BOOK) {
            pdc.remove(keyCustomEnchantBook);
        }
        for (CustomEnchantEntry enchant : CustomEnchantEntry.MANAGED) {
            NamespacedKey key = keyFor(enchant);
            if (!pdc.getKeys().contains(key)) {
                continue;
            }
            int level = rawStoredEnchantLevel(meta, enchant);
            if (level <= 0 || !enchant.applicable.test(material)) {
                pdc.remove(key);
                continue;
            }
            int clamped = enchant.clampLevel(level);
            if (level != clamped) {
                pdc.set(key, PersistentDataType.INTEGER, clamped);
            }
        }
    }

    private int enchantTableLevel(CustomEnchantEntry enchant, int expLevelCost) {
        if (enchant.maxLevel() <= 1) {
            return 1;
        }
        if (expLevelCost >= 30) {
            return enchant.maxLevel();
        }
        if (expLevelCost >= 20) {
            return Math.min(2, enchant.maxLevel());
        }
        return 1;
    }

    private int clampWiseLevel(int level) {
        return CustomEnchantEntry.WISE.clampLevel(level);
    }

    private double wiseBonusRate(int level) {
        return switch (clampWiseLevel(level)) {
            case 1 -> plugin.getConfigManager().wiseLevelOneBonus;
            case 2 -> plugin.getConfigManager().wiseLevelTwoBonus;
            case 3 -> plugin.getConfigManager().wiseLevelThreeBonus;
            default -> 0.0;
        };
    }

    private void tickDoubleJumpFlightPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateDoubleJumpFlight(player);
        }
    }

    private void updateDoubleJumpFlight(Player player) {
        UUID playerId = player.getUniqueId();
        boolean armed = doubleJumpArmedPlayers.contains(playerId);
        DoubleJumpFlightAction action = doubleJumpFlightAction(
            canUseDoubleJump(player),
            armed,
            player.getAllowFlight(),
            isOnGround(player)
        );
        switch (action) {
            case ARM -> {
                player.setAllowFlight(true);
                doubleJumpArmedPlayers.add(playerId);
            }
            case KEEP_ARMED -> {
                if (!player.getAllowFlight()) {
                    player.setAllowFlight(true);
                }
            }
            case DISARM -> clearDoubleJumpFlight(player);
            case NONE -> {
            }
        }
    }

    static DoubleJumpFlightAction doubleJumpFlightAction(boolean usable, boolean armed, boolean allowFlight, boolean onGround) {
        if (!usable) return armed ? DoubleJumpFlightAction.DISARM : DoubleJumpFlightAction.NONE;
        if (armed) return DoubleJumpFlightAction.KEEP_ARMED;
        if (onGround && !allowFlight) return DoubleJumpFlightAction.ARM;
        return DoubleJumpFlightAction.NONE;
    }

    private boolean canUseDoubleJump(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        if (shouldKeepExternalFlight(player)) {
            return false;
        }
        if (player.isFlying() || player.isGliding() || player.isInWater() || player.isInsideVehicle()) {
            return false;
        }
        return hasDoubleJump(player.getInventory().getBoots());
    }

    public boolean shouldRetainFlightAccess(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }
        if (doubleJumpArmedPlayers.contains(player.getUniqueId())) {
            return true;
        }
        return isOnGround(player) && canUseDoubleJump(player);
    }

    private boolean shouldKeepExternalFlight(Player player) {
        return plugin.getPlayerManager().hasFlightEnabled(player.getUniqueId())
            || (plugin.getSuperpowerManager() != null && plugin.getSuperpowerManager().shouldRetainFlightAccess(player));
    }

    private boolean isOnGround(Player player) {
        return ((Entity) player).isOnGround();
    }

    private boolean consumeDoubleJumpCost(Player player) {
        int hungerCost = plugin.getConfigManager().doubleJumpHungerCost;
        if (hungerCost <= 0) {
            return true;
        }
        if (player.getFoodLevel() < hungerCost) {
            return false;
        }

        player.setFoodLevel(player.getFoodLevel() - hungerCost);
        return true;
    }

    private void launchDoubleJump(Player player) {
        Vector velocity = doubleJumpVelocity(
            player.getVelocity(),
            player.getLocation().getDirection(),
            plugin.getConfigManager().doubleJumpForwardBoost,
            plugin.getConfigManager().doubleJumpVerticalBoost
        );
        player.setVelocity(velocity);
        player.setFallDistance(0.0f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 0.9f, 1.25f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0.0, 0.2, 0.0), 14, 0.28, 0.08, 0.28, 0.035);
    }

    static Vector doubleJumpVelocity(Vector currentVelocity, Vector facingDirection, double forwardBoost, double verticalBoost) {
        Vector current = currentVelocity == null ? new Vector() : currentVelocity.clone();
        Vector horizontal = current.clone().setY(0.0);
        Vector facing = facingDirection == null ? new Vector() : facingDirection.clone().setY(0.0);
        double boost = Math.max(0.0, forwardBoost);
        if (boost > 0.0 && facing.lengthSquared() > 0.0001) {
            double speedLimit = Math.max(boost, horizontal.length());
            horizontal.multiply(0.45).add(facing.normalize().multiply(boost));
            if (horizontal.lengthSquared() > speedLimit * speedLimit) {
                horizontal.normalize().multiply(speedLimit);
            }
        }
        horizontal.setY(Math.max(Math.max(0.1, verticalBoost), current.getY()));
        return horizontal;
    }

    private void useDash(Player player) {
        long now = System.currentTimeMillis();
        long cooldownUntil = dashCooldownUntil(player);
        if (cooldownUntil > now) {
            player.sendActionBar(MM.deserialize("<red>Dash ready in <white>" + secondsLeft(cooldownUntil - now) + "s</white>.</red>"));
            return;
        }
        if (cooldownUntil > 0L) {
            clearDashCooldown(player);
        }

        Vector direction = player.getEyeLocation().getDirection();
        if (direction.lengthSquared() < 0.0001) {
            direction = player.getLocation().getDirection();
        }
        if (direction.lengthSquared() < 0.0001) {
            return;
        }

        direction.normalize();
        Vector velocity = direction.multiply(plugin.getConfigManager().dashEnchantHorizontalBoost);
        velocity.setY(Math.max(plugin.getConfigManager().dashEnchantVerticalBoost, velocity.getY() * 0.45 + plugin.getConfigManager().dashEnchantVerticalBoost));

        player.setVelocity(velocity);
        player.setFallDistance(0.0f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1.0f, 1.15f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0.0, 0.35, 0.0), 18, 0.35, 0.15, 0.35, 0.02);

        int cooldownSeconds = plugin.getConfigManager().dashEnchantCooldownSeconds;
        if (cooldownSeconds > 0) {
            setDashCooldownUntil(player, now + (cooldownSeconds * 1000L));
            player.sendActionBar(MM.deserialize("<aqua>Dash used. Ready in <white>" + cooldownSeconds + "s</white>.</aqua>"));
        } else {
            player.sendActionBar(MM.deserialize("<aqua>Dash used.</aqua>"));
        }
    }

    private long dashCooldownUntil(Player player) {
        return player.getPersistentDataContainer().getOrDefault(keyDashCooldownUntil, PersistentDataType.LONG, 0L);
    }

    private void setDashCooldownUntil(Player player, long cooldownUntil) {
        if (cooldownUntil <= System.currentTimeMillis()) {
            clearDashCooldown(player);
            return;
        }
        player.getPersistentDataContainer().set(keyDashCooldownUntil, PersistentDataType.LONG, cooldownUntil);
    }

    private void clearDashCooldown(Player player) {
        player.getPersistentDataContainer().remove(keyDashCooldownUntil);
    }

    private long secondsLeft(long millis) {
        return Math.max(1L, (millis + 999L) / 1000L);
    }

    private void clearDoubleJumpFlight(Player player) {
        if (player == null) {
            return;
        }
        if (!doubleJumpArmedPlayers.remove(player.getUniqueId())) {
            return;
        }
        if (!shouldKeepExternalFlight(player)) {
            if (player.isFlying()) {
                player.setFlying(false);
            }
            player.setAllowFlight(false);
        }
    }

    enum DoubleJumpFlightAction {
        NONE,
        ARM,
        KEEP_ARMED,
        DISARM
    }

    private boolean isAncientCityLoot(LootTable lootTable) {
        if (lootTable == null || lootTable.getKey() == null) {
            return false;
        }
        return LootTables.ANCIENT_CITY.getKey().equals(lootTable.getKey())
            || LootTables.ANCIENT_CITY_ICE_BOX.getKey().equals(lootTable.getKey());
    }

    private ItemStack findSmeltingResult(Material input) {
        if (input == null || input == Material.AIR) {
            return null;
        }
        if (input == Material.NETHERRACK) {
            nonSmeltableDrops.add(input);
            return null;
        }
        ItemStack cached = smeltingResults.get(input);
        if (cached != null) {
            return cached.clone();
        }
        if (nonSmeltableDrops.contains(input)) {
            return null;
        }

        ItemStack testStack = new ItemStack(input);
        var iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            if (!(iterator.next() instanceof CookingRecipe<?> recipe)) {
                continue;
            }
            if (recipe.getInputChoice() == null || !recipe.getInputChoice().test(testStack)) {
                continue;
            }
            ItemStack result = recipe.getResult();
            if (result == null || result.getType() == Material.AIR || result.getAmount() <= 0) {
                continue;
            }
            ItemStack normalized = result.clone();
            smeltingResults.put(input, normalized.clone());
            return normalized;
        }

        nonSmeltableDrops.add(input);
        return null;
    }

    private boolean isWiseCrop(Block block) {
        if (block == null) {
            return false;
        }
        Material material = block.getType();
        if (material == Material.AIR) {
            return false;
        }
        if (isMatureWiseCrop(block)) {
            return true;
        }
        if (delicateGrowthDirection(material) != null) {
            return true;
        }
        return material == Material.MELON || material == Material.PUMPKIN;
    }

    private boolean containsCropDrop(List<ItemStack> drops) {
        if (drops == null || drops.isEmpty()) {
            return false;
        }
        for (ItemStack drop : drops) {
            if (drop != null && isCropDrop(drop.getType())) {
                return true;
            }
        }
        return false;
    }

    private void spawnMissingWiseCropXp(Location location, int alreadyDropping) {
        int configuredXp = plugin.getConfigManager().wiseCropXp;
        if (configuredXp <= 0) {
            return;
        }
        spawnWiseCropXp(location, Math.max(0, configuredXp - Math.max(0, alreadyDropping)));
    }

    private void spawnWiseCropXp(Location location, int amount) {
        if (amount <= 0 || location == null || location.getWorld() == null) {
            return;
        }
        Location spawnLocation = location.clone().add(0.5, 0.45, 0.5);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (spawnLocation.getWorld() == null) {
                return;
            }
            spawnLocation.getWorld().spawn(spawnLocation, ExperienceOrb.class, orb -> orb.setExperience(amount));
            spawnLocation.getWorld().spawnParticle(Particle.ENCHANT, spawnLocation, 6, 0.22, 0.18, 0.22, 0.02);
        });
    }

    private boolean isMatureWiseCrop(Block block) {
        Material material = block.getType();
        if (!isDelicateCrop(material)) {
            return false;
        }
        if (!(block.getBlockData() instanceof Ageable ageable)) {
            return false;
        }
        return ageable.getAge() >= ageable.getMaximumAge();
    }

    private static boolean isPickaxeSwordOrHoe(Material material) {
        return Tag.ITEMS_PICKAXES.isTagged(material)
            || Tag.ITEMS_HOES.isTagged(material)
            || material.name().endsWith("_SWORD");
    }

    private static boolean isSwordOrAxe(Material material) {
        return material.name().endsWith("_SWORD") || Tag.ITEMS_AXES.isTagged(material);
    }

    private static boolean isMeleeWeapon(Material material) {
        return material.name().endsWith("_SWORD")
            || Tag.ITEMS_AXES.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_MACE.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_TRIDENT.isTagged(material);
    }

    private static boolean isArmor(Material material) {
        return Tag.ITEMS_ENCHANTABLE_HEAD_ARMOR.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_CHEST_ARMOR.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_LEG_ARMOR.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_FOOT_ARMOR.isTagged(material);
    }

    private static boolean isToolWeaponOrArmor(Material material) {
        return isToolOrWeapon(material) || isArmor(material);
    }

    private boolean isDashProtectedInteraction(Material material) {
        if (material == null || material == Material.AIR) {
            return false;
        }
        if (Tag.BUTTONS.isTagged(material)
            || Tag.DOORS.isTagged(material)
            || Tag.TRAPDOORS.isTagged(material)
            || Tag.FENCE_GATES.isTagged(material)
            || Tag.ANVIL.isTagged(material)) {
            return true;
        }
        return switch (material) {
            case CHEST, TRAPPED_CHEST, BARREL, SHULKER_BOX,
                 WHITE_SHULKER_BOX, ORANGE_SHULKER_BOX, MAGENTA_SHULKER_BOX, LIGHT_BLUE_SHULKER_BOX,
                 YELLOW_SHULKER_BOX, LIME_SHULKER_BOX, PINK_SHULKER_BOX, GRAY_SHULKER_BOX,
                 LIGHT_GRAY_SHULKER_BOX, CYAN_SHULKER_BOX, PURPLE_SHULKER_BOX, BLUE_SHULKER_BOX,
                 BROWN_SHULKER_BOX, GREEN_SHULKER_BOX, RED_SHULKER_BOX, BLACK_SHULKER_BOX,
                 CRAFTING_TABLE, FURNACE, BLAST_FURNACE, SMOKER, ENCHANTING_TABLE, ENDER_CHEST,
                 BREWING_STAND, BEACON, HOPPER, DISPENSER, DROPPER, LECTERN, LOOM,
                 CARTOGRAPHY_TABLE, FLETCHING_TABLE, GRINDSTONE, SMITHING_TABLE, STONECUTTER,
                 NOTE_BLOCK, JUKEBOX, LEVER, REPEATER, COMPARATOR, DAYLIGHT_DETECTOR,
                 RESPAWN_ANCHOR, CAKE, COMPOSTER, BELL, DECORATED_POT -> true;
            default -> false;
        };
    }

    private boolean sameTeam(Player first, Player second) {
        return first != null
            && second != null
            && (plugin.getDuelManager() == null || !plugin.getDuelManager().areOpponents(first.getUniqueId(), second.getUniqueId()))
            && plugin.getTeamManager() != null
            && plugin.getTeamManager().sameTeam(first.getUniqueId(), second.getUniqueId());
    }

    private boolean isCustomBoss(Entity entity) {
        return entity != null && plugin.getBossManager() != null && plugin.getBossManager().isCustomBoss(entity);
    }

    private boolean isBossEncounterEntity(Entity entity) {
        return entity != null && plugin.getBossManager() != null && plugin.getBossManager().isBossEncounterEntity(entity);
    }

    private static boolean isBlockedEssenceCaptureType(EntityType type) {
        return type == null
            || type == EntityType.ENDER_DRAGON
            || type == EntityType.WITHER
            || type == EntityType.WARDEN
            || type == EntityType.ELDER_GUARDIAN;
    }

    private static Material spawnEggMaterial(EntityType type) {
        if (isBlockedEssenceCaptureType(type)) {
            return null;
        }

        Material direct = Material.matchMaterial(type.name() + "_SPAWN_EGG");
        if (direct != null && !direct.isAir()) {
            return direct;
        }

        return switch (type.name()) {
            case "MUSHROOM_COW" -> Material.matchMaterial("MOOSHROOM_SPAWN_EGG");
            case "PIG_ZOMBIE" -> Material.matchMaterial("ZOMBIFIED_PIGLIN_SPAWN_EGG");
            default -> null;
        };
    }

    private double maxHealth(Player player) {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0D : Math.max(1.0D, attribute.getValue());
    }

    private static boolean isCropDrop(Material material) {
        if (material == null || material.isAir()) {
            return false;
        }
        return isCropDropName(material.name());
    }

    private static boolean isCropDropName(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return false;
        }
        return switch (materialName) {
            case "WHEAT", "WHEAT_SEEDS", "BEETROOT", "BEETROOT_SEEDS", "CARROT", "POTATO", "POISONOUS_POTATO",
                 "MELON_SLICE", "MELON_SEEDS", "PUMPKIN", "PUMPKIN_SEEDS", "COCOA_BEANS", "SWEET_BERRIES",
                 "GLOW_BERRIES", "SUGAR_CANE", "CACTUS", "BAMBOO", "KELP", "NETHER_WART" -> true;
            default -> false;
        };
    }

    private String formatPercent(double value) {
        double percent = value * 100.0;
        if (Math.abs(percent - Math.rint(percent)) < 0.0001) {
            return Math.round(percent) + "%";
        }
        return String.format(java.util.Locale.US, "%.1f%%", percent);
    }

    private String romanNumeral(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(level);
        };
    }

    private UUID telekinesisOwner(Entity damager) {
        if (damager instanceof Player player && hasTelekinesis(player.getInventory().getItemInMainHand())) {
            return player.getUniqueId();
        }

        if (!(damager instanceof Projectile projectile)) return null;
        String owner = projectile.getPersistentDataContainer().get(keyTelekinesisProjectileOwner, PersistentDataType.STRING);
        if (owner == null || owner.isBlank()) return null;

        try {
            return UUID.fromString(owner);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private EssenceCaptureHit essenceCaptureHit(Entity damager) {
        if (damager instanceof Player player) {
            int level = storedEnchantLevel(player.getInventory().getItemInMainHand(), CustomEnchantEntry.ESSENCE_CAPTURE);
            if (level > 0) {
                return new EssenceCaptureHit(player.getUniqueId(), level);
            }
            return null;
        }

        if (!(damager instanceof Projectile projectile)) return null;
        String owner = projectile.getPersistentDataContainer().get(keyEssenceCaptureProjectileOwner, PersistentDataType.STRING);
        if (owner == null || owner.isBlank()) return null;

        try {
            Integer storedLevel = projectile.getPersistentDataContainer().get(keyEssenceCaptureProjectileLevel, PersistentDataType.INTEGER);
            int level = storedLevel == null ? 1 : CustomEnchantEntry.ESSENCE_CAPTURE.clampLevel(storedLevel);
            return new EssenceCaptureHit(UUID.fromString(owner), level);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void tagEssenceCaptureProjectile(Projectile projectile, UUID ownerId, int level) {
        projectile.getPersistentDataContainer().set(
            keyEssenceCaptureProjectileOwner,
            PersistentDataType.STRING,
            ownerId.toString()
        );
        projectile.getPersistentDataContainer().set(
            keyEssenceCaptureProjectileLevel,
            PersistentDataType.INTEGER,
            CustomEnchantEntry.ESSENCE_CAPTURE.clampLevel(level)
        );
    }

    private double essenceCaptureDropChance(int level) {
        return switch (CustomEnchantEntry.ESSENCE_CAPTURE.clampLevel(level)) {
            case 1 -> ESSENCE_CAPTURE_LEVEL_ONE_CHANCE;
            case 2 -> ESSENCE_CAPTURE_LEVEL_TWO_CHANCE;
            case 3 -> ESSENCE_CAPTURE_LEVEL_THREE_CHANCE;
            default -> 0.0D;
        };
    }

    private ItemStack projectileSourceItem(Projectile projectile) {
        if (projectile instanceof AbstractArrow arrow) {
            ItemStack weapon = arrow.getWeapon();
            if (weapon != null && !weapon.getType().isAir()) {
                return weapon;
            }

            ItemStack itemStack = arrow.getItemStack();
            if (itemStack != null && !itemStack.getType().isAir()) {
                return itemStack;
            }
        }

        if (projectile instanceof ThrowableProjectile throwable) {
            ItemStack item = throwable.getItem();
            if (item != null && !item.getType().isAir()) {
                return item;
            }
        }

        return null;
    }

    private void rememberTelekinesisMiningContext(Player player, Location location) {
        cleanupExpiredTelekinesisMiningContexts();
        telekinesisMiningContexts.put(
            blockKey(location),
            new TelekinesisMiningContext(player.getUniqueId(), System.currentTimeMillis() + TELEKINESIS_MINING_CONTEXT_TTL_MS)
        );
    }

    private Player telekinesisMiningOwner(Location location) {
        cleanupExpiredTelekinesisMiningContexts();
        TelekinesisMiningContext context = telekinesisMiningContexts.get(blockKey(location));
        if (context == null) {
            return null;
        }

        Player owner = Bukkit.getPlayer(context.playerId());
        if (owner == null || !owner.isOnline()) {
            telekinesisMiningContexts.remove(blockKey(location));
            return null;
        }
        return owner;
    }

    private void forgetTelekinesisMiningContext(Location location) {
        telekinesisMiningContexts.remove(blockKey(location));
    }

    private void cleanupExpiredTelekinesisMiningContexts() {
        long now = System.currentTimeMillis();
        telekinesisMiningContexts.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    private BlockKey blockKey(Location location) {
        return new BlockKey(
            location.getWorld().getUID(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ()
        );
    }

    private boolean harvestStemPreservingPlant(Player player, ItemStack tool, Block source) {
        BlockFace growthDirection = delicateGrowthDirection(source.getType());
        if (growthDirection == null) return false;

        Block root = findDelicateRoot(source, growthDirection);
        List<Block> harvested = new ArrayList<>();
        Block cursor = root.getRelative(growthDirection);
        while (isSameDelicateFamily(root.getType(), cursor.getType())) {
            harvested.add(cursor);
            cursor = cursor.getRelative(growthDirection);
        }

        if (harvested.isEmpty()) {
            return true;
        }

        List<ItemStack> drops = new ArrayList<>();
        for (Block block : harvested) {
            drops.addAll(block.getDrops(tool, player));
        }

        for (Block block : harvested) {
            block.setType(Material.AIR, false);
        }

        if (hasTelekinesis(tool)) {
            giveDrops(player, drops, source.getLocation());
        } else {
            dropDropsNaturally(source.getLocation(), drops);
        }
        return true;
    }

    private Block findDelicateRoot(Block source, BlockFace growthDirection) {
        Block cursor = source;
        BlockFace towardRoot = growthDirection.getOppositeFace();
        while (isSameDelicateFamily(source.getType(), cursor.getRelative(towardRoot).getType())) {
            cursor = cursor.getRelative(towardRoot);
        }
        return cursor;
    }

    private BlockFace delicateGrowthDirection(Material material) {
        return switch (material) {
            case SUGAR_CANE, CACTUS, BAMBOO, BAMBOO_SAPLING, KELP, KELP_PLANT, TWISTING_VINES, TWISTING_VINES_PLANT -> BlockFace.UP;
            case WEEPING_VINES, WEEPING_VINES_PLANT, CAVE_VINES, CAVE_VINES_PLANT -> BlockFace.DOWN;
            default -> null;
        };
    }

    private boolean isSameDelicateFamily(Material first, Material second) {
        String firstFamily = delicateFamily(first);
        return firstFamily != null && firstFamily.equals(delicateFamily(second));
    }

    private String delicateFamily(Material material) {
        return switch (material) {
            case SUGAR_CANE -> "sugar_cane";
            case CACTUS -> "cactus";
            case BAMBOO, BAMBOO_SAPLING -> "bamboo";
            case KELP, KELP_PLANT -> "kelp";
            case TWISTING_VINES, TWISTING_VINES_PLANT -> "twisting_vines";
            case WEEPING_VINES, WEEPING_VINES_PLANT -> "weeping_vines";
            case CAVE_VINES, CAVE_VINES_PLANT -> "cave_vines";
            default -> null;
        };
    }

    private boolean isImmatureDelicateCrop(Block block) {
        Material material = block.getType();
        if (!isDelicateCrop(material)) {
            return false;
        }

        if (!(block.getBlockData() instanceof Ageable ageable)) {
            return false;
        }

        return ageable.getAge() < ageable.getMaximumAge();
    }

    private boolean isDelicateCrop(Material material) {
        return Tag.CROPS.isTagged(material) || material == Material.COCOA || material == Material.SWEET_BERRY_BUSH;
    }

    private boolean isAlwaysProtectedPlant(Material material) {
        if (Tag.SAPLINGS.isTagged(material)
            || Tag.FLOWERS.isTagged(material)
            || Tag.SMALL_FLOWERS.isTagged(material)) {
            return true;
        }

        return switch (material) {
            case MELON_STEM, ATTACHED_MELON_STEM,
                 PUMPKIN_STEM, ATTACHED_PUMPKIN_STEM,
                 VINE,
                 CHORUS_FLOWER, CHORUS_PLANT,
                 BIG_DRIPLEAF, BIG_DRIPLEAF_STEM, SMALL_DRIPLEAF,
                 MANGROVE_PROPAGULE, LILY_PAD,
                 SHORT_GRASS, TALL_GRASS, FERN, LARGE_FERN,
                 DEAD_BUSH, BROWN_MUSHROOM, RED_MUSHROOM,
                 CRIMSON_FUNGUS, WARPED_FUNGUS,
                 CRIMSON_ROOTS, WARPED_ROOTS, NETHER_SPROUTS,
                 SEA_PICKLE, PINK_PETALS -> true;
            default -> false;
        };
    }

    private void giveDrops(Player player, Collection<ItemStack> drops, Location origin) {
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType() == Material.AIR || drop.getAmount() <= 0) continue;
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(drop);
            leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(origin, left));
        }
    }

    private void dropDropsNaturally(Location origin, Collection<ItemStack> drops) {
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType() == Material.AIR || drop.getAmount() <= 0) continue;
            origin.getWorld().dropItemNaturally(origin, drop);
        }
    }

    private ItemStack consumeOne(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return null;
        if (stack.getAmount() <= 1) return null;

        ItemStack next = stack.clone();
        next.setAmount(stack.getAmount() - 1);
        return next;
    }

    private static boolean isEmptyItem(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private enum CustomEnchantEntry {
        REPLENISH(
            "replenish",
            "<green><bold>Replenish</bold></green>",
            "<green><bold>Replenish Book</bold></green>",
            "Replenish",
            Material.WHEAT,
            "I",
            List.of(
                "Hoe enchant.",
                "Breaking supported crops replants them automatically.",
                "Any successful enchant-table use on a hoe grants it."
            ),
            material -> false,
            true,
            1,
            1,
            1
        ),
        DELICATE(
            "delicate",
            "<gold><bold>Delicate</bold></gold>",
            "<gold><bold>Delicate Book</bold></gold>",
            "Delicate",
            Material.TORCHFLOWER,
            "I",
            List.of(
                "Tool and weapon enchant.",
                "Immature crops stay planted.",
                "Stacked plants keep their root stem while you harvest the growth."
            ),
            CustomEnchantListener::isToolOrWeapon,
            true,
            1,
            1,
            1
        ),
        TELEKINESIS(
            "telekinesis",
            "<aqua><bold>Telekinesis</bold></aqua>",
            "<aqua><bold>Telekinesis Book</bold></aqua>",
            "Telekinesis",
            Material.ENDER_PEARL,
            "I",
            List.of(
                "Tool and weapon enchant.",
                "Mining and mob drops go straight into your inventory when space is available."
            ),
            CustomEnchantListener::isToolOrWeapon,
            true,
            1,
            1,
            1
        ),
        SMELTING_TOUCH(
            "smelting_touch",
            "<gold><bold>Smelting Touch</bold></gold>",
            "<gold><bold>Smelting Touch Book</bold></gold>",
            "Smelting Touch",
            Material.BLAST_FURNACE,
            "I",
            List.of(
                "Pickaxe enchant.",
                "Smelts mined drops automatically when a valid cooking recipe exists."
            ),
            Tag.ITEMS_PICKAXES::isTagged,
            true,
            12,
            1,
            1
        ),
        WISE(
            "wise",
            "<light_purple><bold>Wise</bold></light_purple>",
            "<light_purple><bold>Wise Book</bold></light_purple>",
            "Wise",
            Material.EXPERIENCE_BOTTLE,
            "I, II, III",
            List.of(),
            CustomEnchantListener::isPickaxeSwordOrHoe,
            true,
            10,
            1,
            3
        ),
        DOUBLE_JUMP(
            "double_jump",
            "<aqua><bold>Double Jump</bold></aqua>",
            "<aqua><bold>Double Jump Book</bold></aqua>",
            "Double Jump",
            Material.FEATHER,
            "I",
            List.of(),
            Tag.ITEMS_ENCHANTABLE_FOOT_ARMOR::isTagged,
            false,
            1,
            1,
            1
        ),
        DASH(
            "dash",
            "<yellow><bold>Dash</bold></yellow>",
            "<yellow><bold>Dash Book</bold></yellow>",
            "Dash",
            Material.WIND_CHARGE,
            "I",
            List.of(
                "Sword and axe enchant.",
                "Right-click to burst in the direction you are facing.",
                "Sneak + right-click interactable blocks to dash instead of opening them.",
                "Cooldown feedback appears above your hotbar."
            ),
            CustomEnchantListener::isSwordOrAxe,
            true,
            18,
            1,
            1
        ),
        FROSTBITE(
            "frostbite",
            "<aqua><bold>Frostbite</bold></aqua>",
            "<aqua><bold>Frostbite Book</bold></aqua>",
            "Frostbite",
            Material.BLUE_ICE,
            "I, II",
            List.of(
                "Weapon enchant.",
                "Hits can briefly slow enemies.",
                "Higher levels improve the chance and chill strength.",
                "Does not slow boss-encounter enemies."
            ),
            CustomEnchantListener::isMeleeWeapon,
            true,
            14,
            2,
            2
        ),
        HARVESTING(
            "harvesting",
            "<green><bold>Harvesting</bold></green>",
            "<green><bold>Harvesting Book</bold></green>",
            "Harvesting",
            Material.GOLDEN_HOE,
            "I, II, III",
            List.of(
                "Hoe enchant.",
                "Mature crops have a chance to produce one extra crop drop."
            ),
            Tag.ITEMS_HOES::isTagged,
            true,
            10,
            2,
            3
        ),
        BULWARK(
            "bulwark",
            "<gray><bold>Bulwark</bold></gray>",
            "<gray><bold>Bulwark Book</bold></gray>",
            "Bulwark",
            Material.SHIELD,
            "I, II, III",
            List.of(
                "Armor enchant.",
                "Each worn piece slightly reduces incoming damage.",
                "Caps at 30% total damage reduction."
            ),
            CustomEnchantListener::isArmor,
            true,
            16,
            2,
            3
        ),
        REINFORCED(
            "reinforced",
            "<yellow><bold>Reinforced</bold></yellow>",
            "<yellow><bold>Reinforced Book</bold></yellow>",
            "Reinforced",
            Material.ANVIL,
            "I, II, III",
            List.of(
                "Gear enchant.",
                "Items have a chance to ignore durability damage."
            ),
            CustomEnchantListener::isToolWeaponOrArmor,
            true,
            18,
            2,
            3
        ),
        KINGSLAYER(
            "kingslayer",
            "<red><bold>Kingslayer</bold></red>",
            "<red><bold>Kingslayer Book</bold></red>",
            "Kingslayer",
            Material.NETHERITE_SWORD,
            "I",
            List.of(
                "Boss-forged weapon enchant.",
                "Deals 18% more damage to custom bosses.",
                "Craft-only book made with Veil trophy materials."
            ),
            CustomEnchantListener::isMeleeWeapon,
            false,
            1,
            1,
            1
        ),
        SOUL_SIPHON(
            "soul_siphon",
            "<dark_red><bold>Soul Siphon</bold></dark_red>",
            "<dark_red><bold>Soul Siphon Book</bold></dark_red>",
            "Soul Siphon",
            Material.GHAST_TEAR,
            "I",
            List.of(
                "Boss-forged weapon enchant.",
                "Heals a small amount from damage dealt.",
                "Healing is capped so it stays PvP-safe."
            ),
            CustomEnchantListener::isMeleeWeapon,
            false,
            1,
            1,
            1
        ),
        ECHOING(
            "echoing",
            "<dark_aqua><bold>Echoing</bold></dark_aqua>",
            "<dark_aqua><bold>Echoing Book</bold></dark_aqua>",
            "Echoing",
            Material.ECHO_SHARD,
            "I",
            List.of(
                "Boss-forged melee enchant.",
                "Hits can mark, weaken, and lightly knock enemies back.",
                "Does not control boss-encounter enemies."
            ),
            CustomEnchantListener::isMeleeWeapon,
            false,
            1,
            1,
            1
        ),
        ESSENCE_CAPTURE(
            "essence_capture",
            "<dark_green><bold>Essence Capture</bold></dark_green>",
            "<dark_green><bold>Essence Capture Book</bold></dark_green>",
            "Essence Capture",
            Material.ZOMBIE_SPAWN_EGG,
            "I, II, III",
            List.of(
                "Tool and weapon enchant.",
                "Spawn egg chance: I 0.5%, II 1.5%, III 2.5%.",
                "Does not work on players, custom bosses, or vanilla boss mobs."
            ),
            CustomEnchantListener::isToolOrWeapon,
            true,
            24,
            1,
            3
        );

        private static final List<CustomEnchantEntry> MANAGED = List.of(
            DELICATE,
            TELEKINESIS,
            SMELTING_TOUCH,
            WISE,
            DOUBLE_JUMP,
            DASH,
            FROSTBITE,
            HARVESTING,
            BULWARK,
            REINFORCED,
            KINGSLAYER,
            SOUL_SIPHON,
            ECHOING,
            ESSENCE_CAPTURE
        );
        private static final List<CustomEnchantEntry> MENU_ENTRIES = List.of(
            REPLENISH,
            DELICATE,
            TELEKINESIS,
            SMELTING_TOUCH,
            WISE,
            DOUBLE_JUMP,
            DASH,
            FROSTBITE,
            HARVESTING,
            BULWARK,
            REINFORCED,
            KINGSLAYER,
            SOUL_SIPHON,
            ECHOING,
            ESSENCE_CAPTURE
        );

        private final String id;
        private final String menuDisplay;
        private final String bookDisplay;
        private final String plainName;
        private final Material icon;
        private final String levels;
        private final List<String> description;
        private final java.util.function.Predicate<Material> applicable;
        private final boolean enchantTableEligible;
        private final int enchantTableMinCost;
        private final int enchantTableWeight;
        private final int maxLevel;

        CustomEnchantEntry(
            String id,
            String menuDisplay,
            String bookDisplay,
            String plainName,
            Material icon,
            String levels,
            List<String> description,
            java.util.function.Predicate<Material> applicable,
            boolean enchantTableEligible,
            int enchantTableMinCost,
            int enchantTableWeight,
            int maxLevel
        ) {
            this.id = id;
            this.menuDisplay = menuDisplay;
            this.bookDisplay = bookDisplay;
            this.plainName = plainName;
            this.icon = icon;
            this.levels = levels;
            this.description = description;
            this.applicable = applicable;
            this.enchantTableEligible = enchantTableEligible;
            this.enchantTableMinCost = enchantTableMinCost;
            this.enchantTableWeight = Math.max(1, enchantTableWeight);
            this.maxLevel = Math.max(1, maxLevel);
        }

        private int clampLevel(int level) {
            return Math.max(1, Math.min(maxLevel, level));
        }

        private int maxLevel() {
            return maxLevel;
        }

        private String plainName() {
            return plainName;
        }

        private String levelDisplay(int level) {
            int safeLevel = Math.max(1, Math.min(255, level));
            if (maxLevel <= 1 && safeLevel <= 1) {
                return "I";
            }
            return switch (safeLevel) {
                case 1 -> "I";
                case 2 -> "II";
                case 3 -> "III";
                case 4 -> "IV";
                case 5 -> "V";
                default -> Integer.toString(level);
            };
        }

        private String loreLine(int level) {
            int safeLevel = Math.max(1, Math.min(255, level));
            return plainName + " " + switch (safeLevel) {
                case 1 -> "I";
                case 2 -> "II";
                case 3 -> "III";
                case 4 -> "IV";
                case 5 -> "V";
                default -> Integer.toString(level);
            };
        }

        private boolean matchesLoreLine(String plain) {
            if (plain == null || plain.isBlank()) {
                return false;
            }
            if (this == WISE) {
                return plain.startsWith(WISE_LORE_PREFIX);
            }
            if (this == DOUBLE_JUMP) {
                return DOUBLE_JUMP_LORE_LINE.equalsIgnoreCase(plain);
            }
            if (this == DASH) {
                return DASH_LORE_LINE.equalsIgnoreCase(plain);
            }
            if (this == FROSTBITE) {
                return plain.startsWith(FROSTBITE_LORE_PREFIX);
            }
            if (this == HARVESTING) {
                return plain.startsWith(HARVESTING_LORE_PREFIX);
            }
            if (this == BULWARK) {
                return plain.startsWith(BULWARK_LORE_PREFIX);
            }
            if (this == REINFORCED) {
                return plain.startsWith(REINFORCED_LORE_PREFIX);
            }
            if (this == KINGSLAYER) {
                return KINGSLAYER_LORE_LINE.equalsIgnoreCase(plain);
            }
            if (this == SOUL_SIPHON) {
                return SOUL_SIPHON_LORE_LINE.equalsIgnoreCase(plain);
            }
            if (this == ECHOING) {
                return ECHOING_LORE_LINE.equalsIgnoreCase(plain);
            }
            if (this == ESSENCE_CAPTURE) {
                return plain.startsWith(ESSENCE_CAPTURE_LORE_PREFIX);
            }
            return loreLine(1).equalsIgnoreCase(plain);
        }

        private String bookDisplay(int level) {
            if (maxLevel <= 1) {
                return bookDisplay;
            }
            String color = switch (this) {
                case WISE -> "light_purple";
                case FROSTBITE -> "aqua";
                case HARVESTING -> "green";
                case BULWARK -> "gray";
                case REINFORCED -> "yellow";
                default -> "gold";
            };
            return "<" + color + "><bold>" + plainName + " " + levelDisplay(level) + " Book</bold></" + color + ">";
        }

        private String plainDisplay(int level) {
            if (maxLevel <= 1 && level <= 1) {
                return plainName + " I";
            }
            return loreLine(level);
        }

        private List<String> description(me.rique.smpcore.config.ConfigManager config, int level, boolean specificLevelBook) {
            if (this == DOUBLE_JUMP) {
                return List.of(
                    "Boots enchant.",
                    "Double-tap jump in midair to launch forward.",
                    "Each jump costs " + formatFoodCost(config.doubleJumpHungerCost) + ".",
                    "Found in Ancient City chests at " + formatConfigPercent(config.doubleJumpAncientCityChestChance) + "."
                );
            }

            if (this == DASH) {
                return List.of(
                    "Sword and axe enchant.",
                    "Right-click to dash forward.",
                    "Cooldown: " + config.dashEnchantCooldownSeconds + " seconds.",
                    "Sneak while right-clicking interactable blocks to dash instead of opening them.",
                    "Cooldown feedback appears above your hotbar."
                );
            }

            if (this == ESSENCE_CAPTURE) {
                if (specificLevelBook) {
                    return List.of(
                        "Tool and weapon enchant.",
                        "Eligible mob kills have a " + essenceCaptureChanceLabel(clampLevel(level)) + " chance to drop that mob's spawn egg.",
                        "Does not work on players, custom bosses, or vanilla boss mobs."
                    );
                }
                return List.of(
                    "Tool and weapon enchant.",
                    "Level I: 0.5% spawn egg chance.",
                    "Level II: 1.5% spawn egg chance.",
                    "Level III: 2.5% spawn egg chance.",
                    "Does not work on players, custom bosses, or vanilla boss mobs."
                );
            }

            if (this != WISE) {
                return description;
            }

            if (specificLevelBook) {
                double bonus = switch (clampLevel(level)) {
                    case 1 -> config.wiseLevelOneBonus;
                    case 2 -> config.wiseLevelTwoBonus;
                    case 3 -> config.wiseLevelThreeBonus;
                    default -> 0.0;
                };
                String percent = formatConfigPercent(bonus);
                return List.of(
                    "Pickaxe, sword, and hoe enchant.",
                    "Grants +" + percent + " XP from all sources while held.",
                    "Breaking or right-click harvesting crops with it drops at least " + config.wiseCropXp + " XP."
                );
            }

            return List.of(
                "Pickaxe, sword, and hoe enchant.",
                "Level I: +" + formatConfigPercent(config.wiseLevelOneBonus) + " XP from all sources while held.",
                "Level II: +" + formatConfigPercent(config.wiseLevelTwoBonus) + " XP from all sources while held.",
                "Level III: +" + formatConfigPercent(config.wiseLevelThreeBonus) + " XP from all sources while held.",
                "Breaking or right-click harvesting crops with it drops at least " + config.wiseCropXp + " XP."
            );
        }

        private static String essenceCaptureChanceLabel(int level) {
            return switch (level) {
                case 1 -> "0.5%";
                case 2 -> "1.5%";
                case 3 -> "2.5%";
                default -> "0%";
            };
        }

        private static String formatConfigPercent(double bonus) {
            double percent = bonus * 100.0;
            if (Math.abs(percent - Math.rint(percent)) < 0.0001) {
                return Math.round(percent) + "%";
            }
            return String.format(java.util.Locale.US, "%.1f%%", percent);
        }

        private static String formatNumber(double value) {
            if (Math.abs(value - Math.rint(value)) < 0.0001) {
                return Integer.toString((int) Math.round(value));
            }
            return String.format(java.util.Locale.US, "%.1f", value);
        }

        private static String formatFoodCost(int hungerCost) {
            if (hungerCost <= 0) {
                return "no hunger";
            }
            if ((hungerCost & 1) == 0) {
                int bars = hungerCost / 2;
                return bars + (bars == 1 ? " hunger bar" : " hunger bars");
            }
            return hungerCost + " hunger points";
        }
    }

    private static boolean isToolOrWeapon(Material material) {
        return Tag.ITEMS_PICKAXES.isTagged(material)
            || Tag.ITEMS_AXES.isTagged(material)
            || Tag.ITEMS_SHOVELS.isTagged(material)
            || Tag.ITEMS_HOES.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_WEAPON.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_BOW.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_CROSSBOW.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_TRIDENT.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_MACE.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_FISHING.isTagged(material)
            || material == Material.SHEARS;
    }

    private record EnchantMenuHolder() implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record EnchantRecipeMenuHolder(CustomEnchantEntry enchant) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    enum CustomBookPayloadState {
        NONE,
        VALID,
        INVALID
    }

    private record BookEnchantData(CustomEnchantEntry enchant, int level) {}

    public record ManagedEnchantOption(String id, String displayName, int level, int maxLevel) {}

    private record EssenceCaptureHit(UUID ownerId, int level) {}

    private record CustomEnchantAnvilResult(
        ItemStack result,
        CustomEnchantEntry enchant,
        int level,
        int cost,
        String action,
        String description
    ) {}

    private record BlockKey(UUID worldId, int x, int y, int z) {}

    private record TelekinesisMiningContext(UUID playerId, long expiresAt) {}
}
