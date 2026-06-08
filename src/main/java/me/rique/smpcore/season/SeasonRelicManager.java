package me.rique.smpcore.season;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.boss.BossManager;
import me.rique.smpcore.command.MainMenuCommand;
import me.rique.smpcore.legendary.MythicForgeListener;
import me.rique.smpcore.power.SuperpowerManager;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Covenant relic layer. This keeps the large Armory expansion out of the older
 * legendary listener while still feeding the same Reliquary/admin/audit surfaces.
 */
public final class SeasonRelicManager implements Listener {

    public static final String ARMORY_MENU_ID = "covenant_armory";

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
    private static final int CRAFT_SLOT = 49;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 53;
    private static final long PASSIVE_TICKS = 40L;
    private static final int PASSIVE_NIGHT_VISION_TICKS = 600;
    private static final double TRUE_SIGHT_RADIUS = 48.0;

    private final SMPCore plugin;
    private final NamespacedKey keyRelicId;
    private final NamespacedKey keyMenuAction;
    private final NamespacedKey keyMenuValue;
    private final NamespacedKey keyProjectileRelic;
    private final NamespacedKey keyRiftAnchor;

    private final Map<String, RelicDefinition> relics;
    private final Map<RelicCategory, List<RelicDefinition>> relicsByCategory;
    private final Map<String, List<RelicDefinition>> armorSets;
    private final Map<UUID, ArmoryBackTarget> menuBackTargets = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<UUID> trueSightGlowingTargets = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private BukkitTask passiveTask;

    public SeasonRelicManager(SMPCore plugin) {
        this.plugin = plugin;
        this.keyRelicId = new NamespacedKey(plugin, "season_relic_id");
        this.keyMenuAction = new NamespacedKey(plugin, "season_menu_action");
        this.keyMenuValue = new NamespacedKey(plugin, "season_menu_value");
        this.keyProjectileRelic = new NamespacedKey(plugin, "season_projectile_relic");
        this.keyRiftAnchor = new NamespacedKey(plugin, "season_rift_anchor");
        this.relics = buildRelics();
        this.relicsByCategory = groupByCategory(relics.values());
        this.armorSets = groupArmorSets(relics.values());
    }

    public void start() {
        if (passiveTask != null) {
            passiveTask.cancel();
        }
        passiveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPassives, PASSIVE_TICKS, PASSIVE_TICKS);
    }

    public void shutdown() {
        if (passiveTask != null) {
            passiveTask.cancel();
            passiveTask = null;
        }
        clearTrueSightGlow(Set.of());
    }

    public List<String> relicIds() {
        return List.copyOf(relics.keySet());
    }

    public List<String> commandOptions() {
        LinkedHashSet<String> options = new LinkedHashSet<>();
        for (RelicDefinition relic : relics.values()) {
            options.add(relic.id());
            options.add(commandToken(relic.name()));
            options.addAll(relic.aliases());
        }
        return List.copyOf(options);
    }

    public boolean isRelicId(String input) {
        return definition(input) != null;
    }

    public String normalizeRelicId(String input) {
        RelicDefinition definition = definition(input);
        return definition == null ? null : definition.id();
    }

    public String relicId(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String id = meta.getPersistentDataContainer().get(keyRelicId, PersistentDataType.STRING);
        return relics.containsKey(id) ? id : null;
    }

    public boolean isSeasonRelic(ItemStack item) {
        return relicId(item) != null;
    }

    public String displayNameFor(String input) {
        RelicDefinition definition = definition(input);
        return definition == null ? null : definition.name();
    }

    public String rarityLabelFor(String input) {
        RelicDefinition definition = definition(input);
        return definition == null ? null : definition.rarity().label();
    }

    public boolean isMaterialRelicId(String input) {
        RelicDefinition definition = definition(input);
        return definition != null && definition.kind() == RelicKind.MATERIAL;
    }

    public ItemStack createRelicItem(String input) {
        RelicDefinition definition = definition(input);
        if (definition == null) {
            return null;
        }
        return createItem(definition, false);
    }

    public ItemStack createArmoryPreview(Player player) {
        ItemStack item = createGuiItem(
            Material.ECHO_SHARD,
            "<gradient:#ff4d6d:#facc15><bold>Covenant Armory</bold></gradient>",
            List.of(
                "<gray>Boss-forged weapons, armor, materials,</gray>",
                "<gray>and utility relics live here.</gray>",
                "<dark_gray>" + BedrockCompat.menuActionWord(player) + " to open</dark_gray>"
            )
        );
        tagMenu(item, "open_armory", "");
        return item;
    }

    public List<String> weaponAndToolRelicIds() {
        List<String> ids = new ArrayList<>();
        for (RelicDefinition definition : relics.values()) {
            if (definition.category() == RelicCategory.WEAPONS && !definition.recipe().isEmpty()) {
                ids.add(definition.id());
            }
        }
        return ids;
    }

    public ItemStack createRelicPreview(Player player, String relicId) {
        RelicDefinition definition = definition(relicId);
        if (definition == null) {
            return new ItemStack(Material.BARRIER);
        }
        return createItem(definition, true);
    }

    public boolean handlesReliquaryEntry(String recipeId) {
        return ARMORY_MENU_ID.equals(recipeId) || isRelicId(recipeId);
    }

    public void openReliquaryEntry(Player player, String recipeId) {
        openReliquaryEntry(player, recipeId, false);
    }

    public void openReliquaryEntry(Player player, String recipeId, boolean reliquaryReturnsToMainMenu) {
        setBackTarget(player, reliquaryReturnsToMainMenu ? ArmoryBackTarget.RELIQUARY_THEN_MAIN_MENU : ArmoryBackTarget.RELIQUARY);
        if (ARMORY_MENU_ID.equals(recipeId)) {
            openArmoryMenuInternal(player);
            return;
        }
        if (isRelicId(recipeId)) {
            openRelicDetails(player, normalizeRelicId(recipeId));
        }
    }

    public void openArmoryMenu(Player player) {
        setBackTarget(player, ArmoryBackTarget.RELIQUARY);
        openArmoryMenuInternal(player);
    }

    public void openArmoryMenuFromReliquary(Player player, boolean reliquaryReturnsToMainMenu) {
        setBackTarget(player, reliquaryReturnsToMainMenu ? ArmoryBackTarget.RELIQUARY_THEN_MAIN_MENU : ArmoryBackTarget.RELIQUARY);
        openArmoryMenuInternal(player);
    }

    public void openArmoryMenuFromMainMenu(Player player) {
        setBackTarget(player, ArmoryBackTarget.MAIN_MENU);
        openArmoryMenuInternal(player);
    }

    private void openArmoryMenuInternal(Player player) {
        ArmoryBackTarget backTarget = backTarget(player);
        Inventory inventory = Bukkit.createInventory(
            new SeasonMenuHolder(MenuView.HUB, null, null),
            54,
            BedrockCompat.menuTitle(
                player,
                MM.deserialize("<gradient:#ff4d6d:#facc15><bold>Covenant Armory</bold></gradient>"),
                "Covenant Armory"
            )
        );
        decorate(inventory);
        inventory.setItem(4, createGuiItem(
            Material.NETHER_STAR,
            "<gradient:#ff4d6d:#facc15><bold>Covenant Armory</bold></gradient>",
            List.of(
                "<gray>The bosses are old locks. These relics are the keys.</gray>",
                "<gray>Most recipes need trophies from custom bosses, so</gray>",
                "<gray>endgame power stays tied to public server moments.</gray>"
            )
        ));

        inventory.setItem(19, categoryItem(player, RelicCategory.WEAPONS));
        inventory.setItem(21, categoryItem(player, RelicCategory.ARMOR_SETS));
        inventory.setItem(23, categoryItem(player, RelicCategory.ARMOR_PIECES));
        inventory.setItem(25, categoryItem(player, RelicCategory.UTILITIES));
        inventory.setItem(31, categoryItem(player, RelicCategory.MATERIALS));
        inventory.setItem(13, createGuiItem(
            Material.COMPASS,
            "<gradient:#facc15:#fb7185><bold>Path of Ascension</bold></gradient>",
            List.of(
                "<gray>Suggested climb:</gray>",
                "<white>Yule</white> <dark_gray>-></dark_gray> <white>Vesper/Kael</white> <dark_gray>-></dark_gray> <white>Nereida/Mirewood</white>",
                "<dark_gray>-></dark_gray> <white>Iron Saint/Aurelion</white> <dark_gray>-></dark_gray> <white>Voralith</white>",
                "<dark_gray>Boss trophies unlock gear for the next step.</dark_gray>"
            )
        ));
        inventory.setItem(BACK_SLOT, backItem(backTarget == ArmoryBackTarget.MAIN_MENU ? "Return to /menu" : "Return to Reliquary"));
        inventory.setItem(CLOSE_SLOT, createGuiItem(Material.BARRIER, "<red>Close</red>", List.of("<gray>Close the Armory.</gray>")));
        player.openInventory(inventory);
    }

    private void setBackTarget(Player player, ArmoryBackTarget target) {
        if (player == null) {
            return;
        }
        menuBackTargets.put(player.getUniqueId(), target == null ? ArmoryBackTarget.RELIQUARY : target);
    }

    private ArmoryBackTarget backTarget(Player player) {
        if (player == null) {
            return ArmoryBackTarget.RELIQUARY;
        }
        return menuBackTargets.getOrDefault(player.getUniqueId(), ArmoryBackTarget.RELIQUARY);
    }

    private void openArmoryParent(Player player) {
        ArmoryBackTarget target = backTarget(player);
        if (target == ArmoryBackTarget.MAIN_MENU) {
            menuBackTargets.remove(player.getUniqueId());
            MainMenuCommand.openMenu(plugin, player);
            return;
        }
        if (plugin.getLegendaryListener() == null) {
            player.closeInventory();
            return;
        }
        if (target == ArmoryBackTarget.RELIQUARY_THEN_MAIN_MENU) {
            plugin.getLegendaryListener().openRecipeMenuFromMainMenu(player);
        } else {
            plugin.getLegendaryListener().openRecipeMenu(player);
        }
    }

    public void openCategoryMenu(Player player, RelicCategory category) {
        Inventory inventory = Bukkit.createInventory(
            new SeasonMenuHolder(MenuView.CATEGORY, category, null),
            54,
            BedrockCompat.menuTitle(player, MM.deserialize(category.title()), category.plainTitle())
        );
        decorate(inventory);
        List<String> lore = new ArrayList<>(category.lore());
        lore.add("<dark_gray> ");
        int entryCount = category == RelicCategory.ARMOR_SETS
            ? armorSets.size()
            : relicsByCategory.getOrDefault(category, List.of()).size();
        lore.add("<gray>Entries: <white>" + entryCount + "</white></gray>");
        inventory.setItem(4, createGuiItem(category.icon(), category.title(), lore));

        if (category == RelicCategory.ARMOR_SETS) {
            List<String> setIds = new ArrayList<>(armorSets.keySet());
            setIds.sort(Comparator.comparing(SeasonRelicManager::setTitle));
            for (int i = 0; i < setIds.size() && i < CONTENT_SLOTS.length; i++) {
                String setId = setIds.get(i);
                ItemStack icon = armorSetPreview(player, setId);
                tagMenu(icon, "open_armor_set", setId);
                inventory.setItem(CONTENT_SLOTS[i], icon);
            }
        } else {
            List<RelicDefinition> entries = relicsByCategory.getOrDefault(category, List.of());
            for (int i = 0; i < entries.size() && i < CONTENT_SLOTS.length; i++) {
                RelicDefinition definition = entries.get(i);
                ItemStack icon = createItem(definition, true);
                tagMenu(icon, "open_relic", definition.id());
                inventory.setItem(CONTENT_SLOTS[i], icon);
            }
        }
        inventory.setItem(BACK_SLOT, backItem("Return to Covenant Armory"));
        player.openInventory(inventory);
    }

    private void openArmorSetMenu(Player player, String setId) {
        List<RelicDefinition> pieces = armorSets.getOrDefault(setId, List.of());
        if (pieces.isEmpty()) {
            player.sendMessage(MessageUtil.error("That armor set is not registered."));
            return;
        }

        Inventory inventory = Bukkit.createInventory(
            new SeasonMenuHolder(MenuView.ARMOR_SET, RelicCategory.ARMOR_SETS, setId),
            54,
            BedrockCompat.menuTitle(
                player,
                MM.deserialize("<gradient:#facc15:#ef4444><bold>" + setTitle(setId) + "</bold></gradient>"),
                setTitle(setId)
            )
        );
        decorate(inventory);
        inventory.setItem(4, armorSetPreview(player, setId));

        int[] setSlots = {20, 21, 23, 24};
        List<RelicDefinition> sorted = new ArrayList<>(pieces);
        sorted.sort(Comparator.comparingInt(piece -> armorOrder(piece.equipmentSlot())));
        for (int i = 0; i < sorted.size() && i < setSlots.length; i++) {
            RelicDefinition definition = sorted.get(i);
            ItemStack icon = createItem(definition, true);
            tagMenu(icon, "open_relic", definition.id());
            inventory.setItem(setSlots[i], icon);
        }

        inventory.setItem(40, createGuiItem(
            Material.NETHER_STAR,
            "<gradient:#ff4d6d:#facc15><bold>Full Set Bonus</bold></gradient>",
            List.of(
                "<gray>" + setBonusPlain(setId) + "</gray>",
                "<dark_gray>Wear all four pieces together to wake it.</dark_gray>"
            )
        ));
        inventory.setItem(BACK_SLOT, backItem("Back to Covenant Sets"));
        player.openInventory(inventory);
    }

    public void openRelicDetails(Player player, String relicId) {
        RelicDefinition definition = relics.get(relicId);
        if (definition == null) {
            player.sendMessage(MessageUtil.error("That relic is not registered."));
            return;
        }
        Inventory inventory = Bukkit.createInventory(
            new SeasonMenuHolder(MenuView.RELIC, definition.category(), definition.id()),
            54,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#ff4d6d:#facc15><bold>" + definition.name() + "</bold></gradient>"), definition.name())
        );
        decorate(inventory);
        inventory.setItem(4, createItem(definition, true));
        List<RecipeIngredient> recipe = definition.recipe();
        if (recipe.isEmpty()) {
            inventory.setItem(22, createGuiItem(
                Material.BARRIER,
                "<red><bold>Not Craftable</bold></red>",
                List.of(
                    "<gray>This is a boss trophy or progression material.</gray>",
                    "<gray>Use <white>/bossrituals</white> to learn the source.</gray>"
                )
            ));
        } else {
            for (int i = 0; i < recipe.size() && i < CONTENT_SLOTS.length; i++) {
                inventory.setItem(CONTENT_SLOTS[i], recipe.get(i).displayItem(this));
            }
            inventory.setItem(CRAFT_SLOT, createGuiItem(
                Material.LIME_STAINED_GLASS_PANE,
                "<green><bold>Craft " + definition.name() + "</bold></green>",
                List.of(
                    "<gray>Consumes the listed materials from your inventory.</gray>",
                    "<dark_gray>Armor/offhand slots are never consumed.</dark_gray>"
                )
            ));
        }
        inventory.setItem(BACK_SLOT, backItem("Back to " + definition.category().plainTitle()));
        player.openInventory(inventory);
    }

    public List<ItemStack> createBossDrops(String bossId) {
        String normalized = bossId == null ? "" : bossId.toLowerCase(Locale.ROOT);
        List<ItemStack> drops = new ArrayList<>();
        switch (normalized) {
            case "yule_the_minion" -> {
                drops.add(stacked("gilded_skull", 1));
                if (Math.random() < 0.35) drops.add(stacked("oathbound_plate", 1));
            }
            case "kael_the_ashen" -> {
                drops.add(stacked("solar_ember", 2));
                if (Math.random() < 0.25) drops.add(stacked("titan_gear", 1));
            }
            case "vesper_the_widow_queen" -> {
                drops.add(stacked("widow_silk", 2));
                if (Math.random() < 0.25) drops.add(stacked("verdant_heart", 1));
            }
            case "voralith_the_crimson_warden" -> {
                drops.add(stacked("crimson_rib", 2));
                drops.add(stacked("sculk_heart", 1));
            }
            case "aurelion_the_rift_seraph" -> {
                drops.add(stacked("rift_lens", 2));
                if (Math.random() < 0.30) drops.add(stacked("void_halo", 1));
            }
            case "nereida_the_abyss_mother" -> {
                drops.add(stacked("abyssal_pearl", 2));
                if (Math.random() < 0.30) drops.add(stacked("tideheart", 1));
            }
            case "iron_saint" -> {
                drops.add(stacked("titan_gear", 2));
                if (Math.random() < 0.30) drops.add(stacked("saint_alloy", 1));
            }
            case "mirewood_the_root_tyrant" -> {
                drops.add(stacked("living_bark", 2));
                if (Math.random() < 0.30) drops.add(stacked("verdant_heart", 1));
            }
            default -> {
            }
        }
        return drops;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SeasonMenuHolder holder)) {
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
        if (event.getRawSlot() == CLOSE_SLOT && holder.view() == MenuView.HUB) {
            player.closeInventory();
            return;
        }
        if (event.getRawSlot() == BACK_SLOT) {
            if (holder.view() == MenuView.HUB) {
                openArmoryParent(player);
            } else if (holder.view() == MenuView.RELIC && holder.relicId() != null) {
                RelicDefinition definition = relics.get(holder.relicId());
                if (definition != null && definition.armorSetId() != null) {
                    openArmorSetMenu(player, definition.armorSetId());
                } else if (holder.category() != null) {
                    openCategoryMenu(player, holder.category());
                } else {
                    openArmoryMenuInternal(player);
                }
            } else if (holder.view() == MenuView.ARMOR_SET) {
                openCategoryMenu(player, RelicCategory.ARMOR_SETS);
            } else if (holder.view() == MenuView.CATEGORY) {
                openArmoryMenuInternal(player);
            } else if (holder.category() != null) {
                openCategoryMenu(player, holder.category());
            } else {
                openArmoryMenuInternal(player);
            }
            return;
        }
        if (holder.view() == MenuView.RELIC && event.getRawSlot() == CRAFT_SLOT) {
            craftRelic(player, holder.relicId());
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        String action = readMenu(clicked, keyMenuAction);
        String value = readMenu(clicked, keyMenuValue);
        if ("open_category".equals(action)) {
            RelicCategory category = RelicCategory.fromId(value);
            if (category != null) {
                openCategoryMenu(player, category);
            }
            return;
        }
        if ("open_armor_set".equals(action)) {
            openArmorSetMenu(player, value);
            return;
        }
        if ("open_relic".equals(action)) {
            openRelicDetails(player, value);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMenuDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SeasonMenuHolder)) {
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRelicInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        String id = relicId(event.getItem());
        if (id == null) {
            return;
        }
        RelicDefinition definition = relics.get(id);
        if (definition == null) {
            return;
        }
        if (definition.kind() == RelicKind.MATERIAL) {
            if (action == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                player.sendActionBar(MM.deserialize("<gray>Boss trophies are crafting materials, not placeable blocks.</gray>"));
            }
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (definition.activeAbility() == ActiveAbility.NONE) {
            return;
        }
        event.setCancelled(true);
        activateUtility(player, definition, player.isSneaking());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRelicShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getProjectile() instanceof Projectile projectile)) {
            return;
        }
        String id = relicId(event.getBow());
        if (id == null) {
            return;
        }
        RelicDefinition definition = relics.get(id);
        if (definition == null || definition.weaponEffect() == WeaponEffect.NONE) {
            return;
        }
        projectile.getPersistentDataContainer().set(keyProjectileRelic, PersistentDataType.STRING, id);
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setCritical(true);
            if (id.equals("sunless_repeater")) {
                arrow.setPierceLevel(Math.max(arrow.getPierceLevel(), 2));
                arrow.setDamage(arrow.getDamage() + 3.5);
            }
            if (id.equals("thornwhisper")) {
                arrow.setDamage(arrow.getDamage() + 2.5);
            }
        }
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getEyeLocation(), 12, 0.15, 0.12, 0.15, 0.1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRelicDamage(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        String id = projectileRelicId(event.getDamager());
        if (id == null) {
            id = relicId(attacker.getInventory().getItemInMainHand());
        }
        if (id == null) {
            return;
        }
        RelicDefinition definition = relics.get(id);
        if (definition == null || definition.weaponEffect() == WeaponEffect.NONE) {
            return;
        }
        applyWeaponEffect(attacker, target, definition, event);
    }

    private void tickPassives() {
        Set<UUID> activeTrueSightTargets = new LinkedHashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            applySetBonus(player, fullArmorSet(player));
            applyStandaloneArmorPassives(player);
            revealInvisiblePlayers(player, activeTrueSightTargets);
        }
        clearTrueSightGlow(activeTrueSightTargets);
    }

    private void applySetBonus(Player player, String setId) {
        if (setId == null) {
            return;
        }
        switch (setId) {
            case "crimson_guard" -> {
                applyPotion(player, PotionEffectType.RESISTANCE, 80, 0);
                if (player.getHealth() <= Math.max(6.0, maxHealth(player) * 0.35)) {
                    applyPotion(player, PotionEffectType.STRENGTH, 80, 1);
                    applyPotion(player, PotionEffectType.REGENERATION, 80, 0);
                }
            }
            case "widow_court" -> {
                applyPotion(player, PotionEffectType.SPEED, 80, 1);
                applyPotion(player, PotionEffectType.JUMP_BOOST, 80, 0);
                player.removePotionEffect(PotionEffectType.POISON);
            }
            case "ashen_saint" -> {
                applyPotion(player, PotionEffectType.FIRE_RESISTANCE, 80, 0);
                applyPotion(player, PotionEffectType.HASTE, 80, 1);
                applyPotion(player, PotionEffectType.STRENGTH, 80, 0);
            }
            case "tidebound" -> {
                applyPotion(player, PotionEffectType.WATER_BREATHING, 100, 0);
                if (isWet(player)) {
                    applyPotion(player, PotionEffectType.DOLPHINS_GRACE, 80, 0);
                    applyPotion(player, PotionEffectType.SPEED, 80, 1);
                    applyPotion(player, PotionEffectType.REGENERATION, 80, 0);
                }
            }
            case "riftwalker" -> {
                applyPotion(player, PotionEffectType.SLOW_FALLING, 80, 0);
                applyPotion(player, PotionEffectType.SPEED, 80, 1);
                applyPotion(player, PotionEffectType.RESISTANCE, 80, 0);
            }
            default -> {
            }
        }
    }

    private void applyStandaloneArmorPassives(Player player) {
        PlayerInventory inventory = player.getInventory();
        applyArmorPassive(player, inventory.getHelmet());
        applyArmorPassive(player, inventory.getChestplate());
        applyArmorPassive(player, inventory.getLeggings());
        applyArmorPassive(player, inventory.getBoots());
    }

    private void applyArmorPassive(Player player, ItemStack item) {
        String id = relicId(item);
        if (id == null) {
            return;
        }
        switch (id) {
            case "crown_of_cinders" -> applyPotion(player, PotionEffectType.FIRE_RESISTANCE, 80, 0);
            case "graveveil_hood" -> applyPotion(player, PotionEffectType.NIGHT_VISION, PASSIVE_NIGHT_VISION_TICKS, 0);
            case "bastion_pauldrons" -> applyPotion(player, PotionEffectType.ABSORPTION, 80, 0);
            case "sculkplate_harness" -> {
                player.removePotionEffect(PotionEffectType.DARKNESS);
                applyPotion(player, PotionEffectType.RESISTANCE, 80, 0);
            }
            case "stormcall_greaves" -> {
                applyPotion(player, PotionEffectType.SPEED, 80, 0);
                if (player.getWorld().hasStorm()) {
                    applyPotion(player, PotionEffectType.HASTE, 80, 0);
                }
            }
            case "rootwarden_greaves" -> {
                if (player.getWorld().getEnvironment() == World.Environment.NORMAL) {
                    applyPotion(player, PotionEffectType.REGENERATION, 80, 0);
                }
            }
            case "siren_treads" -> {
                applyPotion(player, PotionEffectType.WATER_BREATHING, 100, 0);
                if (isWet(player)) {
                    applyPotion(player, PotionEffectType.DOLPHINS_GRACE, 80, 0);
                }
            }
            case "voidstep_boots" -> applyPotion(player, PotionEffectType.SLOW_FALLING, 80, 0);
            case "glasswalker_boots" -> {
                Material below = player.getLocation().getBlock().getRelative(BlockFace.DOWN).getType();
                if (below == Material.ICE || below == Material.PACKED_ICE || below == Material.BLUE_ICE || below == Material.FROSTED_ICE) {
                    applyPotion(player, PotionEffectType.SPEED, 80, 1);
                }
            }
            case "oathkeeper_helm" -> {
                if (player.getHealth() <= Math.max(6.0, maxHealth(player) * 0.45)) {
                    applyPotion(player, PotionEffectType.ABSORPTION, 80, 1);
                    applyPotion(player, PotionEffectType.RESISTANCE, 80, 0);
                }
            }
            case "revelator_helm" -> {
                player.getWorld().spawnParticle(Particle.ENCHANT, player.getEyeLocation(), 2, 0.12, 0.08, 0.12, 0.02);
            }
            default -> {
            }
        }
    }

    private void revealInvisiblePlayers(Player viewer, Set<UUID> activeTargets) {
        if (!"revelator_helm".equals(relicId(viewer.getInventory().getHelmet()))) {
            return;
        }
        if (viewer.isDead() || viewer.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        double radiusSquared = TRUE_SIGHT_RADIUS * TRUE_SIGHT_RADIUS;
        for (Player target : viewer.getWorld().getPlayers()) {
            if (target.equals(viewer) || target.isDead() || target.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (!isTrueSightTarget(target)) {
                continue;
            }
            if (target.getLocation().distanceSquared(viewer.getLocation()) > radiusSquared) {
                continue;
            }

            activeTargets.add(target.getUniqueId());
            if (!target.isGlowing()) {
                target.setGlowing(true);
                trueSightGlowingTargets.add(target.getUniqueId());
            }
        }
    }

    private boolean isTrueSightTarget(Player target) {
        return target.isInvisible() || target.hasPotionEffect(PotionEffectType.INVISIBILITY);
    }

    private void clearTrueSightGlow(Set<UUID> activeTargets) {
        trueSightGlowingTargets.removeIf(uuid -> {
            if (activeTargets.contains(uuid)) {
                return false;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isGlowing()) {
                player.setGlowing(false);
            }
            return true;
        });
    }

    private void activateUtility(Player player, RelicDefinition definition, boolean sneaking) {
        if (definition.activeAbility() == ActiveAbility.RIFT_ANCHOR && sneaking) {
            activateRiftAnchor(player, true);
            return;
        }
        if (!cooldownReady(player, definition.id(), definition.activeCooldownSeconds())) {
            return;
        }
        boolean success = switch (definition.activeAbility()) {
            case TEAM_BANNER -> activateBloodboundBanner(player);
            case RIFT_ANCHOR -> activateRiftAnchor(player, sneaking);
            case EMBER_VIAL -> {
                applyPotion(player, PotionEffectType.FIRE_RESISTANCE, 90 * 20, 0);
                player.setFireTicks(0);
                player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0.0, 1.0, 0.0), 24, 0.5, 0.35, 0.5, 0.02);
                player.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.8f, 1.25f);
                yield true;
            }
            case WIDOW_ANTIDOTE -> {
                player.removePotionEffect(PotionEffectType.POISON);
                player.removePotionEffect(PotionEffectType.WITHER);
                player.removePotionEffect(PotionEffectType.SLOWNESS);
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0.0, 1.0, 0.0), 18, 0.35, 0.35, 0.35, 0.02);
                yield true;
            }
            case SAINT_WHETSTONE -> activateWhetstone(player);
            case ABYSSAL_CONCH -> {
                applyPotion(player, PotionEffectType.WATER_BREATHING, 120 * 20, 0);
                applyPotion(player, PotionEffectType.DOLPHINS_GRACE, 90 * 20, 0);
                player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 0.8f, 0.9f);
                yield true;
            }
            case ROOT_SIGIL -> activateRootSigil(player);
            case TITAN_CHARM -> {
                applyPotion(player, PotionEffectType.RESISTANCE, 35 * 20, 1);
                applyPotion(player, PotionEffectType.ABSORPTION, 35 * 20, 1);
                player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0.0, 1.0, 0.0), 28, 0.55, 0.45, 0.55, 0.0, new Particle.DustOptions(Color.fromRGB(150, 150, 170), 1.25f));
                yield true;
            }
            case OATHGLASS_COMPASS -> activateOathglassCompass(player);
            case WARPED_KEY -> activateWarpedKey(player);
            case NULL_BELL -> activateNullBell(player);
            case RIFTWARD_LENS -> activateRiftwardLens(player);
            case GRAVETIDE_PHIAL -> activateGravetidePhial(player);
            case OATHKEEPER_CORD -> activateOathkeeperCord(player);
            case SAINT_LEDGER -> activateSaintLedger(player);
            case NONE -> false;
        };
        if (success) {
            setCooldown(player, definition.id(), definition.activeCooldownSeconds());
        }
    }

    private boolean activateBloodboundBanner(Player player) {
        int affected = 0;
        for (Player target : player.getWorld().getPlayers()) {
            if (target.getLocation().distanceSquared(player.getLocation()) > 10.0 * 10.0) {
                continue;
            }
            if (!target.equals(player) && (plugin.getTeamManager() == null || !plugin.getTeamManager().sameTeam(player.getUniqueId(), target.getUniqueId()))) {
                continue;
            }
            applyPotion(target, PotionEffectType.STRENGTH, 40 * 20, 0);
            applyPotion(target, PotionEffectType.RESISTANCE, 40 * 20, 0);
            applyPotion(target, PotionEffectType.ABSORPTION, 40 * 20, 0);
            target.getWorld().strikeLightningEffect(target.getLocation());
            affected++;
        }
        player.sendActionBar(MM.deserialize("<gold>Bloodbound oath empowered <white>" + affected + "</white> ally" + (affected == 1 ? "" : "ies") + ".</gold>"));
        return true;
    }

    private boolean activateRiftAnchor(Player player, boolean sneaking) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (sneaking) {
            pdc.set(keyRiftAnchor, PersistentDataType.STRING, serializeLocation(player.getLocation()));
            player.sendMessage(MessageUtil.success("Rift Anchor bound to your current location."));
            player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0.0, 1.0, 0.0), 50, 0.5, 0.8, 0.5, 0.5);
            player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 1.2f);
            return true;
        }
        Location target = deserializeLocation(pdc.get(keyRiftAnchor, PersistentDataType.STRING));
        if (target == null || target.getWorld() == null) {
            player.sendMessage(MessageUtil.warn("Sneak-right-click first to bind this anchor."));
            return false;
        }
        if (!isSafeTeleport(target)) {
            player.sendMessage(MessageUtil.error("The bound rift is no longer safe."));
            return false;
        }
        Location from = player.getLocation();
        player.teleport(target);
        from.getWorld().spawnParticle(Particle.PORTAL, from.add(0.0, 1.0, 0.0), 60, 0.6, 0.9, 0.6, 0.55);
        target.getWorld().spawnParticle(Particle.REVERSE_PORTAL, target.clone().add(0.0, 1.0, 0.0), 60, 0.6, 0.9, 0.6, 0.18);
        player.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.85f);
        return true;
    }

    private boolean activateWhetstone(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir() || !(held.getItemMeta() instanceof Damageable damageable) || damageable.getDamage() <= 0) {
            player.sendMessage(MessageUtil.warn("Hold a damaged tool, weapon, or armor piece in your main hand."));
            return false;
        }
        int maxDamage = damageable.hasMaxDamage() ? damageable.getMaxDamage() : held.getType().getMaxDurability();
        if (maxDamage <= 0) {
            player.sendMessage(MessageUtil.warn("That item cannot be repaired by the whetstone."));
            return false;
        }
        int repair = Math.max(1, maxDamage / 4);
        damageable.setDamage(Math.max(0, damageable.getDamage() - repair));
        held.setItemMeta(damageable);
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0.0, 1.0, 0.0), 18, 0.35, 0.30, 0.35, 0.02);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.35f);
        return true;
    }

    private boolean activateRootSigil(Player player) {
        for (Player target : player.getWorld().getPlayers()) {
            if (target.getLocation().distanceSquared(player.getLocation()) > 8.0 * 8.0) {
                continue;
            }
            if (!target.equals(player) && plugin.getTeamManager() != null && !plugin.getTeamManager().sameTeam(player.getUniqueId(), target.getUniqueId())) {
                continue;
            }
            applyPotion(target, PotionEffectType.REGENERATION, 35 * 20, 1);
            applyPotion(target, PotionEffectType.ABSORPTION, 35 * 20, 0);
            target.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, target.getLocation().add(0.0, 1.0, 0.0), 14, 0.35, 0.35, 0.35, 0.02);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.8f);
        return true;
    }

    private boolean activateOathglassCompass(Player player) {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Player candidate : player.getWorld().getPlayers()) {
            if (candidate.equals(player) || candidate.getGameMode() == GameMode.SPECTATOR || candidate.getGameMode() == GameMode.CREATIVE) {
                continue;
            }
            double distance = candidate.getLocation().distance(player.getLocation());
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            player.sendMessage(MessageUtil.warn("The Oathglass found no nearby living signatures in this world."));
            return false;
        }
        Vector delta = nearest.getLocation().toVector().subtract(player.getLocation().toVector());
        String direction = cardinalDirection(delta);
        int rounded = Math.max(25, (int) Math.round(nearestDistance / 25.0) * 25);
        player.sendMessage(MessageUtil.info("The Oathglass points <white>" + direction + "</white>, roughly <white>" + rounded + "</white> blocks away."));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.1f);
        return true;
    }

    private boolean activateWarpedKey(Player player) {
        RayTraceResult trace = player.rayTraceBlocks(9.0, FluidCollisionMode.NEVER);
        Location target = trace == null || trace.getHitBlock() == null
            ? player.getLocation().add(player.getLocation().getDirection().normalize().multiply(8.0))
            : trace.getHitBlock().getLocation().add(0.5, 1.0, 0.5);
        Location safe = nearestSafeTeleport(target);
        if (safe == null) {
            player.sendMessage(MessageUtil.warn("No safe blink point found."));
            return false;
        }
        Location from = player.getLocation();
        player.teleport(safe.setDirection(player.getLocation().getDirection()));
        from.getWorld().spawnParticle(Particle.PORTAL, from.add(0.0, 1.0, 0.0), 35, 0.35, 0.55, 0.35, 0.45);
        safe.getWorld().spawnParticle(Particle.REVERSE_PORTAL, safe.clone().add(0.0, 1.0, 0.0), 35, 0.35, 0.55, 0.35, 0.16);
        player.playSound(safe, Sound.ENTITY_ENDERMAN_TELEPORT, 0.9f, 1.25f);
        return true;
    }

    private boolean activateNullBell(Player player) {
        int affected = 0;
        for (Player target : player.getWorld().getPlayers()) {
            if (!isAllyOrSelf(player, target) || target.getLocation().distanceSquared(player.getLocation()) > 8.0 * 8.0) {
                continue;
            }
            cleanseHarmfulEffects(target);
            applyPotion(target, PotionEffectType.RESISTANCE, 15 * 20, 0);
            target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0.0, 1.0, 0.0), 18, 0.35, 0.55, 0.35, 0.02);
            affected++;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, 1.0f, 0.7f);
        player.sendActionBar(MM.deserialize("<aqua>Nullbell cleansed <white>" + affected + "</white> oathbound soul" + (affected == 1 ? "" : "s") + ".</aqua>"));
        return affected > 0;
    }

    private boolean activateRiftwardLens(Player player) {
        int revealed = 0;
        for (LivingEntity target : player.getWorld().getNearbyLivingEntities(player.getLocation(), 32.0)) {
            if (target.equals(player) || (target instanceof Player other && isAllyOrSelf(player, other))) {
                continue;
            }
            target.setGlowing(true);
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 10 * 20, 0, false, true, true));
            target.getWorld().spawnParticle(Particle.ENCHANT, target.getEyeLocation(), 16, 0.25, 0.25, 0.25, 0.15);
            revealed++;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.45f);
        player.sendActionBar(MM.deserialize("<light_purple>Riftward Lens revealed <white>" + revealed + "</white> nearby threat" + (revealed == 1 ? "" : "s") + ".</light_purple>"));
        return true;
    }

    private boolean activateGravetidePhial(Player player) {
        int affected = 0;
        Location center = player.getLocation().add(0.0, 0.75, 0.0);
        player.getWorld().spawnParticle(Particle.SOUL, center, 80, 2.4, 0.5, 2.4, 0.05);
        player.getWorld().spawnParticle(Particle.SCULK_SOUL, center, 32, 1.6, 0.35, 1.6, 0.05);
        for (LivingEntity target : player.getWorld().getNearbyLivingEntities(player.getLocation(), 7.0)) {
            if (target.equals(player) || (target instanceof Player other && isAllyOrSelf(player, other))) {
                continue;
            }
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 8 * 20, 1, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 8 * 20, 0, false, true, true));
            Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0.0);
            if (push.lengthSquared() > 1.0E-6) {
                target.setVelocity(target.getVelocity().add(push.normalize().multiply(0.35).setY(0.16)));
            }
            affected++;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 1.6f);
        return affected > 0;
    }

    private boolean activateOathkeeperCord(Player player) {
        int affected = 0;
        for (Player target : player.getWorld().getPlayers()) {
            if (!isAllyOrSelf(player, target) || target.getLocation().distanceSquared(player.getLocation()) > 10.0 * 10.0) {
                continue;
            }
            applyPotion(target, PotionEffectType.SPEED, 25 * 20, 1);
            applyPotion(target, PotionEffectType.SLOW_FALLING, 25 * 20, 0);
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0.0, 1.0, 0.0), 14, 0.35, 0.45, 0.35, 0.02);
            affected++;
        }
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.9f, 1.2f);
        return affected > 0;
    }

    private boolean activateSaintLedger(Player player) {
        int repaired = 0;
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (repairItem(item, 0.12)) {
                repaired++;
            }
        }
        if (repairItem(player.getInventory().getItemInMainHand(), 0.12)) {
            repaired++;
        }
        player.giveExp(20);
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0.0, 1.0, 0.0), 45, 0.55, 0.55, 0.55, 0.25);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.9f, 1.0f);
        player.sendActionBar(MM.deserialize("<gold>Saint's Ledger repaired <white>" + repaired + "</white> item" + (repaired == 1 ? "" : "s") + " and returned a little XP.</gold>"));
        return true;
    }

    private void applyWeaponEffect(Player attacker, LivingEntity target, RelicDefinition definition, EntityDamageByEntityEvent event) {
        if (target.equals(attacker)) {
            return;
        }
        switch (definition.weaponEffect()) {
            case ASHEN_VERDICT -> {
                event.setDamage(event.getDamage() + 3.5);
                target.setFireTicks(Math.max(target.getFireTicks(), 120));
                target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0.0, 1.0, 0.0), 14, 0.25, 0.35, 0.25, 0.02);
            }
            case WIDOWFANG -> {
                if (combatCooldownReady(attacker, definition.id(), 8)) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1, false, true, true));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 70, 1, false, true, true));
                    setCooldown(attacker, definition.id(), 8);
                }
            }
            case RIFT_PIKE -> {
                if (combatCooldownReady(attacker, definition.id(), 10)) {
                    Vector pull = attacker.getLocation().toVector().subtract(target.getLocation().toVector());
                    if (pull.lengthSquared() > 1.0E-6) {
                        target.setVelocity(target.getVelocity().add(pull.normalize().multiply(0.45).setY(0.18)));
                    }
                    target.getWorld().spawnParticle(Particle.REVERSE_PORTAL, target.getLocation().add(0.0, 1.0, 0.0), 22, 0.35, 0.45, 0.35, 0.1);
                    setCooldown(attacker, definition.id(), 10);
                }
            }
            case SAINTSPLITTER -> {
                event.setDamage(event.getDamage() + 2.5);
                if (combatCooldownReady(attacker, definition.id(), 9)) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 120, 1, false, true, true));
                    setCooldown(attacker, definition.id(), 9);
                }
            }
            case TIDEBREAKER -> {
                if (isWet(attacker)) {
                    event.setDamage(event.getDamage() + 4.0);
                    target.getWorld().spawnParticle(Particle.SPLASH, target.getLocation().add(0.0, 1.0, 0.0), 20, 0.35, 0.35, 0.35, 0.05);
                } else {
                    event.setDamage(event.getDamage() + 1.0);
                }
            }
            case GRAVEMOURN -> {
                event.setDamage(event.getDamage() + 2.0);
                if (combatCooldownReady(attacker, definition.id(), 4)) {
                    heal(attacker, 3.0);
                    attacker.getWorld().spawnParticle(Particle.SOUL, attacker.getLocation().add(0.0, 1.0, 0.0), 12, 0.25, 0.30, 0.25, 0.02);
                    setCooldown(attacker, definition.id(), 4);
                }
            }
            case NULLGLASS -> {
                if (combatCooldownReady(attacker, definition.id(), 14)) {
                    removeOnePositiveEffect(target);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 70, 0, false, true, true));
                    target.getWorld().spawnParticle(Particle.ENCHANT, target.getLocation().add(0.0, 1.0, 0.0), 28, 0.35, 0.35, 0.35, 0.35);
                    setCooldown(attacker, definition.id(), 14);
                }
            }
            case SUNLESS_REPEATER -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 0, false, true, true));
                target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, true, true));
            }
            case THORNWHISPER -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1, false, true, true));
                target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 0, false, true, true));
                target.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, target.getLocation().add(0.0, 0.8, 0.0), 18, 0.35, 0.25, 0.35, 0.04);
            }
            case CINDERSHARD -> {
                if (isBehind(attacker, target)) {
                    event.setDamage(event.getDamage() + 4.5);
                    attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 70, 1, false, true, true));
                }
            }
            case OATHBREAKER -> {
                if (isCustomBoss(target)) {
                    event.setDamage(event.getDamage() + 4.0);
                    target.getWorld().spawnParticle(Particle.ENCHANT, target.getLocation().add(0.0, 1.0, 0.0), 18, 0.25, 0.45, 0.25, 0.1);
                }
                if (combatCooldownReady(attacker, definition.id(), 10)) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, false, true, true));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 0, false, true, true));
                    setCooldown(attacker, definition.id(), 10);
                }
            }
            case DUSKBELL -> {
                if (combatCooldownReady(attacker, definition.id(), 14)) {
                    setCooldown(attacker, definition.id(), 14);
                    target.getWorld().spawnParticle(Particle.SONIC_BOOM, target.getLocation().add(0.0, 1.0, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
                    target.getWorld().playSound(target.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.9f, 0.55f);
                    for (LivingEntity nearby : target.getWorld().getNearbyLivingEntities(target.getLocation(), 4.0)) {
                        if (nearby.equals(attacker) || nearby.equals(target) || (nearby instanceof Player player && isAllyOrSelf(attacker, player))) {
                            continue;
                        }
                        nearby.damage(3.0, attacker);
                        nearby.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, false, true, true));
                    }
                }
            }
            case BRIARHOOK -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 70, 0, false, true, true));
                double max = Math.max(1.0, target.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0 : target.getAttribute(Attribute.MAX_HEALTH).getValue());
                if (target.getHealth() / max <= 0.35) {
                    event.setDamage(event.getDamage() + 2.5);
                    attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 50, 0, false, true, true));
                }
            }
            case NONE -> {
            }
        }
    }

    private int craftRelic(Player player, String relicId) {
        RelicDefinition definition = relics.get(relicId);
        if (definition == null || definition.recipe().isEmpty()) {
            return 0;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        for (RecipeIngredient ingredient : definition.recipe()) {
            if (countIngredient(storage, ingredient) < ingredient.amount()) {
                player.sendMessage(MessageUtil.error("Missing <white>" + ingredient.amount() + "x " + ingredient.name(this) + "</white>."));
                return 0;
            }
        }
        for (RecipeIngredient ingredient : definition.recipe()) {
            removeIngredient(storage, ingredient);
        }
        inventory.setStorageContents(storage);
        ItemStack reward = createRelicItem(definition.id());
        if (plugin.getItemAuditManager() != null) {
            plugin.getItemAuditManager().recordKnownAcquisition(player, reward, "season_craft", "Crafted from the Covenant Armory.");
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
        leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.updateInventory();
        player.sendMessage(MessageUtil.success("Crafted <white>" + definition.name() + "</white>."));
        if (definition.rarity() == CustomLoreUtil.Rarity.MYTHIC) {
            Bukkit.broadcast(MessageUtil.prefixedRaw(
                "<gradient:#ff4df0:#ffb000><white>" + player.getName() + "</white> forged the mythic relic <white>"
                    + definition.name() + "</white>.</gradient>"
            ));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.9f, 0.9f);
        openRelicDetails(player, definition.id());
        return 1;
    }

    private int countIngredient(ItemStack[] storage, RecipeIngredient ingredient) {
        int count = 0;
        for (ItemStack item : storage) {
            if (ingredient.matches(this, item)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeIngredient(ItemStack[] storage, RecipeIngredient ingredient) {
        int remaining = ingredient.amount();
        for (int i = 0; i < storage.length && remaining > 0; i++) {
            ItemStack item = storage[i];
            if (!ingredient.matches(this, item)) {
                continue;
            }
            int remove = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - remove);
            remaining -= remove;
            if (item.getAmount() <= 0) {
                storage[i] = null;
            }
        }
    }

    private ItemStack createItem(RelicDefinition definition, boolean preview) {
        ItemStack item = new ItemStack(definition.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(CustomLoreUtil.displayName(definition.rarity(), definition.name()));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            definition.material(),
            definition.rarity().label(),
            definition.kind().label(),
            definition.topLore(),
            definition.sections()
        ));
        meta.getPersistentDataContainer().set(keyRelicId, PersistentDataType.STRING, definition.id());
        if (definition.maxDamage() > 0 && meta instanceof Damageable damageable) {
            damageable.setMaxDamage(definition.maxDamage());
            damageable.setDamage(0);
            meta = damageable;
        }
        for (AttributeBonus bonus : definition.attributes()) {
            NamespacedKey key = new NamespacedKey(plugin, "season_" + definition.id() + "_" + bonus.attribute().getKey().getKey());
            meta.addAttributeModifier(
                bonus.attribute(),
                new AttributeModifier(key, bonus.amount(), bonus.operation(), bonus.slot())
            );
        }
        if (definition.rarity().ordinal() >= CustomLoreUtil.Rarity.EPIC.ordinal()) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        if (preview) {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            CustomLoreUtil.addSpacer(lore);
            lore.add(MM.deserialize("<dark_gray>Click to view recipe and source.</dark_gray>"));
            meta.lore(lore);
        }
        CustomLoreUtil.applyStyledItemFlags(meta);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack stacked(String relicId, int amount) {
        ItemStack item = createRelicItem(relicId);
        if (item != null) {
            item.setAmount(Math.max(1, Math.min(amount, item.getMaxStackSize())));
        }
        return item == null ? new ItemStack(Material.AIR) : item;
    }

    private void decorate(Inventory inventory) {
        ItemStack filler = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> </dark_gray>", List.of());
        ItemStack accent = createGuiItem(Material.RED_STAINED_GLASS_PANE, "<dark_gray> </dark_gray>", List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        for (int slot : List.of(0, 1, 7, 8, 9, 17, 36, 44, 46, 52, 53)) {
            inventory.setItem(slot, accent);
        }
    }

    private ItemStack categoryItem(Player player, RelicCategory category) {
        List<String> lore = new ArrayList<>(category.lore());
        lore.add("<dark_gray> ");
        int entryCount = category == RelicCategory.ARMOR_SETS
            ? armorSets.size()
            : relicsByCategory.getOrDefault(category, List.of()).size();
        lore.add("<gray>Entries: <white>" + entryCount + "</white></gray>");
        lore.add("<dark_gray>" + BedrockCompat.menuActionWord(player) + " to open</dark_gray>");
        ItemStack item = createGuiItem(category.icon(), category.title(), lore);
        tagMenu(item, "open_category", category.id());
        return item;
    }

    private ItemStack armorSetPreview(Player player, String setId) {
        List<RelicDefinition> pieces = armorSets.getOrDefault(setId, List.of());
        RelicDefinition first = pieces.stream()
            .min(Comparator.comparingInt(piece -> armorOrder(piece.equipmentSlot())))
            .orElse(null);
        Material icon = first == null ? Material.NETHERITE_CHESTPLATE : first.material();
        CustomLoreUtil.Rarity rarity = first == null ? CustomLoreUtil.Rarity.EPIC : first.rarity();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + setBonusPlain(setId) + "</gray>");
        lore.add("<dark_gray> ");
        lore.add("<gray>Pieces: <white>" + pieces.size() + "/4</white></gray>");
        lore.add("<dark_gray>" + BedrockCompat.menuActionWord(player) + " to inspect the set</dark_gray>");
        return createGuiItem(icon, CustomLoreUtil.displayNameTag(rarity, setTitle(setId)), lore);
    }

    private static int armorOrder(EquipmentSlot slot) {
        if (slot == EquipmentSlot.HEAD) return 0;
        if (slot == EquipmentSlot.CHEST) return 1;
        if (slot == EquipmentSlot.LEGS) return 2;
        if (slot == EquipmentSlot.FEET) return 3;
        return 4;
    }

    private ItemStack backItem(String label) {
        return createGuiItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>" + label + "</gray>"));
    }

    private ItemStack createGuiItem(Material material, String name, List<String> loreLines) {
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

    private void tagMenu(ItemStack item, String action, String value) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(keyMenuAction, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(keyMenuValue, PersistentDataType.STRING, value == null ? "" : value);
        item.setItemMeta(meta);
    }

    private String readMenu(ItemStack item, NamespacedKey key) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private RelicDefinition definition(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.isBlank()) {
            return null;
        }
        RelicDefinition direct = relics.get(normalized);
        if (direct != null) {
            return direct;
        }
        for (RelicDefinition definition : relics.values()) {
            if (definition.aliases().contains(normalized) || commandToken(definition.name()).equals(normalized)) {
                return definition;
            }
        }
        return null;
    }

    private boolean matchesExternalRelic(String id, ItemStack item) {
        if (id == null || item == null || item.getType().isAir()) {
            return false;
        }
        if (id.equals(relicId(item))) {
            return true;
        }
        return switch (id) {
            case BossManager.DOMINION_CORE_ITEM_ID -> plugin.getBossManager() != null && plugin.getBossManager().isDominionCore(item);
            case SuperpowerManager.WARDEN_HEART_ITEM_ID -> plugin.getSuperpowerManager() != null && plugin.getSuperpowerManager().isWardenHeart(item);
            case MythicForgeListener.ASCENDANT_CORE_ITEM_ID -> plugin.getMythicForgeListener() != null && plugin.getMythicForgeListener().isAscendantCoreItem(item);
            default -> false;
        };
    }

    private boolean isProtectedCustomIngredient(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (relicId(item) != null) {
            return true;
        }
        if (plugin.getLegendaryListener() != null && plugin.getLegendaryListener().legendaryId(item) != null) {
            return true;
        }
        if (plugin.getCustomToolListener() != null && plugin.getCustomToolListener().customToolId(item) != null) {
            return true;
        }
        if (plugin.getBackpackListener() != null && plugin.getBackpackListener().isBackpack(item)) {
            return true;
        }
        if (plugin.getAwakeningTableListener() != null && plugin.getAwakeningTableListener().isAwakeningTableCustomItem(item)) {
            return true;
        }
        if (plugin.getMythicForgeListener() != null
            && (plugin.getMythicForgeListener().isMythicForgeItemStack(item) || plugin.getMythicForgeListener().isAscendantCoreItem(item))) {
            return true;
        }
        if (plugin.getSuperpowerManager() != null
            && (plugin.getSuperpowerManager().isAncientScroll(item)
                || plugin.getSuperpowerManager().isWardenHeart(item)
                || plugin.getSuperpowerManager().isMotherNatureStick(item)
                || plugin.getSuperpowerManager().isTheWorldClock(item)
                || plugin.getSuperpowerManager().isDruidGrimoire(item))) {
            return true;
        }
        return plugin.getBossManager() != null && plugin.getBossManager().isDominionCore(item);
    }

    private ItemStack displayExternalRelic(String id, int amount) {
        ItemStack item = switch (id) {
            case BossManager.DOMINION_CORE_ITEM_ID -> plugin.getBossManager() == null ? null : plugin.getBossManager().createDominionCoreItem();
            case SuperpowerManager.WARDEN_HEART_ITEM_ID -> plugin.getSuperpowerManager() == null ? null : plugin.getSuperpowerManager().createWardenHeartItem();
            case MythicForgeListener.ASCENDANT_CORE_ITEM_ID -> plugin.getMythicForgeListener() == null ? null : plugin.getMythicForgeListener().createAscendantCoreItem();
            default -> createRelicItem(id);
        };
        if (item == null) {
            item = createGuiItem(Material.PAPER, "<white>" + prettyName(id) + "</white>", List.of());
        }
        item.setAmount(Math.max(1, Math.min(amount, item.getMaxStackSize())));
        return item;
    }

    private String fullArmorSet(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] armor = {
            inv.getHelmet(),
            inv.getChestplate(),
            inv.getLeggings(),
            inv.getBoots()
        };
        String setId = null;
        for (ItemStack item : armor) {
            String id = relicId(item);
            RelicDefinition definition = id == null ? null : relics.get(id);
            if (definition == null || definition.armorSetId() == null) {
                return null;
            }
            if (setId == null) {
                setId = definition.armorSetId();
            } else if (!setId.equals(definition.armorSetId())) {
                return null;
            }
        }
        List<RelicDefinition> setPieces = armorSets.getOrDefault(setId, List.of());
        return setPieces.size() >= 4 ? setId : null;
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private String projectileRelicId(Entity damager) {
        if (!(damager instanceof Projectile projectile)) {
            return null;
        }
        String id = projectile.getPersistentDataContainer().get(keyProjectileRelic, PersistentDataType.STRING);
        return relics.containsKey(id) ? id : null;
    }

    private boolean cooldownReady(Player player, String id, int seconds) {
        if (seconds <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        long until = player.getPersistentDataContainer().getOrDefault(cooldownKey(id), PersistentDataType.LONG, 0L);
        if (now >= until) {
            return true;
        }
        long remaining = Math.max(1L, (until - now + 999L) / 1000L);
        player.sendActionBar(MM.deserialize("<red>" + prettyName(id) + " ready in <white>" + remaining + "s</white>.</red>"));
        return false;
    }

    private boolean combatCooldownReady(Player player, String id, int seconds) {
        long now = System.currentTimeMillis();
        long until = player.getPersistentDataContainer().getOrDefault(cooldownKey(id), PersistentDataType.LONG, 0L);
        return now >= until || seconds <= 0;
    }

    private void setCooldown(Player player, String id, int seconds) {
        if (seconds <= 0) {
            return;
        }
        player.getPersistentDataContainer().set(cooldownKey(id), PersistentDataType.LONG, System.currentTimeMillis() + seconds * 1000L);
    }

    private NamespacedKey cooldownKey(String id) {
        return new NamespacedKey(plugin, "season_cd_" + sanitizeKey(id));
    }

    private void applyPotion(Player player, PotionEffectType type, int durationTicks, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);
        if (current != null && current.getAmplifier() >= amplifier && current.getDuration() > durationTicks / 2) {
            return;
        }
        player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, true, true, true));
    }

    private boolean isAllyOrSelf(Player owner, Player target) {
        if (owner == null || target == null) {
            return false;
        }
        if (owner.equals(target)) {
            return true;
        }
        return plugin.getTeamManager() != null && plugin.getTeamManager().sameTeam(owner.getUniqueId(), target.getUniqueId());
    }

    private void cleanseHarmfulEffects(Player target) {
        for (PotionEffect effect : new ArrayList<>(target.getActivePotionEffects())) {
            if (effect.getType().getCategory() == org.bukkit.potion.PotionEffectTypeCategory.HARMFUL
                || effect.getType() == PotionEffectType.DARKNESS
                || effect.getType() == PotionEffectType.SLOWNESS
                || effect.getType() == PotionEffectType.MINING_FATIGUE) {
                target.removePotionEffect(effect.getType());
            }
        }
    }

    private boolean repairItem(ItemStack item, double percentOfMax) {
        if (item == null || item.getType().isAir() || !(item.getItemMeta() instanceof Damageable damageable)) {
            return false;
        }
        if (damageable.getDamage() <= 0) {
            return false;
        }
        int maxDamage = damageable.hasMaxDamage() ? damageable.getMaxDamage() : item.getType().getMaxDurability();
        if (maxDamage <= 0) {
            return false;
        }
        int repair = Math.max(1, (int) Math.round(maxDamage * Math.max(0.01, percentOfMax)));
        damageable.setDamage(Math.max(0, damageable.getDamage() - repair));
        item.setItemMeta(damageable);
        return true;
    }

    private void heal(Player player, double amount) {
        player.setHealth(Math.min(maxHealth(player), player.getHealth() + amount));
    }

    private double maxHealth(Player player) {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0 : attribute.getValue();
    }

    private boolean isWet(Entity entity) {
        return entity.isInWater() || entity.isInRain();
    }

    private void removeOnePositiveEffect(LivingEntity target) {
        Collection<PotionEffect> effects = target.getActivePotionEffects();
        for (PotionEffect effect : effects) {
            if (effect.getType().getCategory() == org.bukkit.potion.PotionEffectTypeCategory.BENEFICIAL) {
                target.removePotionEffect(effect.getType());
                return;
            }
        }
    }

    private boolean isBehind(Player attacker, LivingEntity target) {
        Vector targetFacing = target.getLocation().getDirection().setY(0.0);
        Vector attackerDirection = attacker.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0.0);
        if (targetFacing.lengthSquared() <= 1.0E-6 || attackerDirection.lengthSquared() <= 1.0E-6) {
            return false;
        }
        return targetFacing.normalize().dot(attackerDirection.normalize()) < -0.55;
    }

    private boolean isCustomBoss(Entity entity) {
        return plugin.getBossManager() != null && plugin.getBossManager().isCustomBoss(entity);
    }

    private String serializeLocation(Location location) {
        return location.getWorld().getName() + ";" + location.getX() + ";" + location.getY() + ";" + location.getZ() + ";" + location.getYaw() + ";" + location.getPitch();
    }

    private Location deserializeLocation(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(";");
        if (parts.length < 6) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(
                world,
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]),
                Float.parseFloat(parts[4]),
                Float.parseFloat(parts[5])
            );
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Location nearestSafeTeleport(Location target) {
        World world = target.getWorld();
        if (world == null) {
            return null;
        }
        for (int dy = 0; dy <= 3; dy++) {
            for (int sign : List.of(1, -1)) {
                Location candidate = target.clone().add(0.0, dy * sign, 0.0);
                if (isSafeTeleport(candidate)) {
                    return candidate.getBlock().getLocation().add(0.5, 0.0, 0.5);
                }
            }
        }
        return null;
    }

    private boolean isSafeTeleport(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        Block feet = location.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block floor = feet.getRelative(BlockFace.DOWN);
        return feet.isPassable()
            && head.isPassable()
            && !floor.isPassable()
            && !floor.isLiquid()
            && floor.getType() != Material.LAVA;
    }

    private String cardinalDirection(Vector delta) {
        double angle = Math.atan2(-delta.getX(), delta.getZ());
        double degrees = Math.toDegrees(angle);
        if (degrees < 0) degrees += 360.0;
        String[] directions = {"South", "Southwest", "West", "Northwest", "North", "Northeast", "East", "Southeast"};
        return directions[(int) Math.round(degrees / 45.0) % directions.length];
    }

    private static String sanitizeKey(String id) {
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    }

    private static String commandToken(String text) {
        return text.toLowerCase(Locale.ROOT)
            .replace("'", "")
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
    }

    private static String prettyName(String id) {
        if (id == null || id.isBlank()) {
            return "Unknown";
        }
        String[] parts = id.split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private static Map<RelicCategory, List<RelicDefinition>> groupByCategory(Collection<RelicDefinition> definitions) {
        Map<RelicCategory, List<RelicDefinition>> out = new EnumMap<>(RelicCategory.class);
        for (RelicDefinition definition : definitions) {
            out.computeIfAbsent(definition.category(), ignored -> new ArrayList<>()).add(definition);
        }
        for (List<RelicDefinition> list : out.values()) {
            list.sort(Comparator.comparing(RelicDefinition::name));
        }
        return out;
    }

    private static Map<String, List<RelicDefinition>> groupArmorSets(Collection<RelicDefinition> definitions) {
        Map<String, List<RelicDefinition>> out = new HashMap<>();
        for (RelicDefinition definition : definitions) {
            if (definition.armorSetId() != null) {
                out.computeIfAbsent(definition.armorSetId(), ignored -> new ArrayList<>()).add(definition);
            }
        }
        return out;
    }

    private Map<String, RelicDefinition> buildRelics() {
        LinkedHashMap<String, RelicDefinition> out = new LinkedHashMap<>();

        add(out, material("gilded_skull", Material.SKELETON_SKULL, "Gilded Skull", CustomLoreUtil.Rarity.RARE,
            "A trophy from Yule's broken command chain.", "Yule the Minion"));
        add(out, material("oathbound_plate", Material.GOLD_INGOT, "Oathbound Plate", CustomLoreUtil.Rarity.RARE,
            "Soft gold hammered around a name no one says twice.", "Yule the Minion"));
        add(out, material("solar_ember", Material.BLAZE_POWDER, "Solar Ember", CustomLoreUtil.Rarity.RARE,
            "A coal-bright fragment of Kael's last sunrise.", "Kael the Ashen"));
        add(out, material("widow_silk", Material.STRING, "Widow Silk", CustomLoreUtil.Rarity.RARE,
            "Thread that tightens when it hears fear.", "Vesper the Widow Queen"));
        add(out, material("crimson_rib", Material.BONE, "Crimson Rib", CustomLoreUtil.Rarity.EPIC,
            "A red rib pulled from the Dominion's buried hymn.", "Voralith the Crimson Warden"));
        add(out, material("sculk_heart", Material.ECHO_SHARD, "Sculk Heart", CustomLoreUtil.Rarity.EPIC,
            "It beats only when the holder lies.", "Voralith the Crimson Warden"));
        add(out, material("rift_lens", Material.ENDER_EYE, "Rift Lens", CustomLoreUtil.Rarity.EPIC,
            "A pupil that remembers impossible doors.", "Aurelion the Rift Seraph"));
        add(out, material("void_halo", Material.NETHER_STAR, "Void Halo", CustomLoreUtil.Rarity.MYTHIC,
            "A crown for things that were never born.", "Aurelion the Rift Seraph"));
        add(out, material("abyssal_pearl", Material.PRISMARINE_CRYSTALS, "Abyssal Pearl", CustomLoreUtil.Rarity.EPIC,
            "An ocean-dark pearl with a pulse like thunder.", "Nereida the Abyss Mother"));
        add(out, material("tideheart", Material.HEART_OF_THE_SEA, "Tideheart", CustomLoreUtil.Rarity.MYTHIC,
            "The sea kept this heart until it learned your name.", "Nereida the Abyss Mother"));
        add(out, material("titan_gear", Material.IRON_NUGGET, "Titan Gear", CustomLoreUtil.Rarity.EPIC,
            "A gear from a saint that forgot mercy.", "The Iron Saint"));
        add(out, material("saint_alloy", Material.NETHERITE_SCRAP, "Saint Alloy", CustomLoreUtil.Rarity.MYTHIC,
            "Metal that refuses to bend for kings.", "The Iron Saint"));
        add(out, material("living_bark", Material.MANGROVE_ROOTS, "Living Bark", CustomLoreUtil.Rarity.RARE,
            "Wood that still dreams of strangling the sun.", "Mirewood the Root Tyrant"));
        add(out, material("verdant_heart", Material.SPORE_BLOSSOM, "Verdant Heart", CustomLoreUtil.Rarity.EPIC,
            "A green heart, patient as a graveyard.", "Mirewood the Root Tyrant"));

        add(out, weapon("ashen_verdict", Material.NETHERITE_SWORD, "Ashen Verdict", CustomLoreUtil.Rarity.LEGENDARY, 4.0, 0.20,
            WeaponEffect.ASHEN_VERDICT,
            "A judge's blade from the ash court.",
            List.of(mat(Material.NETHERITE_SWORD, 1), relic("solar_ember", 6), relic("gilded_skull", 1), mat(Material.BLAZE_ROD, 8), mat(Material.DIAMOND, 24))));
        add(out, weapon("widowfang", Material.DIAMOND_SWORD, "Widowfang", CustomLoreUtil.Rarity.LEGENDARY, 3.0, 0.30,
            WeaponEffect.WIDOWFANG,
            "A quiet blade that waits for panic.",
            List.of(mat(Material.DIAMOND_SWORD, 1), relic("widow_silk", 8), mat(Material.FERMENTED_SPIDER_EYE, 12), mat(Material.OBSIDIAN, 16))));
        add(out, weapon("rift_pike", Material.TRIDENT, "Rift Pike", CustomLoreUtil.Rarity.MYTHIC, 4.5, 0.35,
            WeaponEffect.RIFT_PIKE,
            "A spear that pins distance to the wall.",
            List.of(mat(Material.TRIDENT, 1), relic("rift_lens", 6), relic("void_halo", 1), mat(Material.ENDER_PEARL, 16))));
        add(out, weapon("saintsplitter", Material.NETHERITE_AXE, "Saintsplitter", CustomLoreUtil.Rarity.LEGENDARY, 3.0, 0.15,
            WeaponEffect.SAINTSPLITTER,
            "A heavy axe from the machine chapel.",
            List.of(mat(Material.NETHERITE_AXE, 1), relic("titan_gear", 6), relic("saint_alloy", 1), mat(Material.IRON_BLOCK, 8))));
        add(out, weapon("tidebreaker", Material.DIAMOND_AXE, "Tidebreaker", CustomLoreUtil.Rarity.LEGENDARY, 3.5, 0.20,
            WeaponEffect.TIDEBREAKER,
            "An axe that becomes cruel in rain.",
            List.of(mat(Material.DIAMOND_AXE, 1), relic("abyssal_pearl", 6), relic("tideheart", 1), mat(Material.PRISMARINE, 32))));
        add(out, weapon("gravemourn", Material.NETHERITE_HOE, "Gravemourn", CustomLoreUtil.Rarity.MYTHIC, 5.0, 0.35,
            WeaponEffect.GRAVEMOURN,
            "A reaping hook for debts the dead still owe.",
            List.of(mat(Material.NETHERITE_HOE, 1), relic("crimson_rib", 4), relic("verdant_heart", 2), mat(Material.SOUL_SAND, 32))));
        add(out, weapon("nullglass_rapier", Material.IRON_SWORD, "Nullglass Rapier", CustomLoreUtil.Rarity.EPIC, 2.5, 0.55,
            WeaponEffect.NULLGLASS,
            "A thin blade that cuts blessings first.",
            List.of(mat(Material.IRON_SWORD, 1), relic("rift_lens", 3), mat(Material.GLASS, 32), mat(Material.AMETHYST_SHARD, 16))));
        add(out, weapon("sunless_repeater", Material.CROSSBOW, "Sunless Repeater", CustomLoreUtil.Rarity.LEGENDARY, 0.0, 0.0,
            WeaponEffect.SUNLESS_REPEATER,
            "A crossbow loaded with nights that never ended.",
            List.of(mat(Material.CROSSBOW, 1), relic("sculk_heart", 2), relic("crimson_rib", 2), mat(Material.PHANTOM_MEMBRANE, 8))));
        add(out, weapon("thornwhisper", Material.BOW, "Thornwhisper", CustomLoreUtil.Rarity.EPIC, 0.0, 0.0,
            WeaponEffect.THORNWHISPER,
            "A bow strung with living roots.",
            List.of(mat(Material.BOW, 1), relic("living_bark", 8), relic("verdant_heart", 1), mat(Material.VINE, 16))));
        add(out, weapon("cindershard_dagger", Material.IRON_SWORD, "Cindershard Dagger", CustomLoreUtil.Rarity.EPIC, 2.0, 0.65,
            WeaponEffect.CINDERSHARD,
            "A short knife for very close betrayals.",
            List.of(mat(Material.IRON_SWORD, 1), relic("solar_ember", 3), mat(Material.FLINT, 16), mat(Material.GOLD_INGOT, 8))));
        add(out, weapon("oathbreaker_mattock", Material.NETHERITE_PICKAXE, "Oathbreaker Mattock", CustomLoreUtil.Rarity.LEGENDARY, 3.5, 0.25,
            WeaponEffect.OATHBREAKER,
            "A war-pick for cracking shrines and stubborn monsters.",
            List.of(mat(Material.NETHERITE_PICKAXE, 1), relic("oathbound_plate", 5), relic("sculk_heart", 1), mat(Material.NETHERITE_INGOT, 2))));
        add(out, weapon("duskbell_mallet", Material.MACE, "Duskbell Mallet", CustomLoreUtil.Rarity.MYTHIC, 4.0, 0.20,
            WeaponEffect.DUSKBELL,
            "A bell-heavy mace that makes crowds remember silence.",
            List.of(mat(Material.MACE, 1), relic("void_halo", 1), relic("titan_gear", 8), mat(Material.BELL, 2), mat(Material.HEAVY_CORE, 1))));
        add(out, weapon("briarhook_saw", Material.NETHERITE_AXE, "Briarhook Saw", CustomLoreUtil.Rarity.LEGENDARY, 3.0, 0.25,
            WeaponEffect.BRIARHOOK,
            "An axe whose teeth keep chasing after the wound.",
            List.of(mat(Material.NETHERITE_AXE, 1), relic("living_bark", 10), relic("verdant_heart", 2), mat(Material.VINE, 24))));

        addArmorSet(out, "crimson_guard", "Crimson Guard", CustomLoreUtil.Rarity.LEGENDARY, Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
            "Armor for those who stand where the shrine screamed.", List.of(relic("crimson_rib", 6), relic("sculk_heart", 2), mat(Material.NETHERITE_INGOT, 6)));
        addArmorSet(out, "widow_court", "Widow Court", CustomLoreUtil.Rarity.EPIC, Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
            "Courtwear from a queen who ate her crown.", List.of(relic("widow_silk", 12), mat(Material.DIAMOND, 24), mat(Material.COBWEB, 12)));
        addArmorSet(out, "ashen_saint", "Ashen Saint", CustomLoreUtil.Rarity.LEGENDARY, Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
            "Sanctified armor from a chapel made of smoke.", List.of(relic("solar_ember", 10), relic("saint_alloy", 2), mat(Material.NETHERITE_INGOT, 4)));
        addArmorSet(out, "tidebound", "Tidebound", CustomLoreUtil.Rarity.EPIC, Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
            "A drowned covenant for walking under stormwater.", List.of(relic("abyssal_pearl", 10), relic("tideheart", 1), mat(Material.PRISMARINE_SHARD, 32)));
        addArmorSet(out, "riftwalker", "Riftwalker", CustomLoreUtil.Rarity.MYTHIC, Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
            "A suit stitched along the seam between worlds.", List.of(relic("rift_lens", 10), relic("void_halo", 2), mat(Material.ENDER_PEARL, 32)));

        add(out, standaloneArmor("crown_of_cinders", Material.GOLDEN_HELMET, "Crown of Cinders", CustomLoreUtil.Rarity.EPIC, EquipmentSlotGroup.HEAD,
            "A small crown for survivors of a burning throne.", ActiveAbility.NONE,
            List.of(relic("solar_ember", 4), mat(Material.GOLD_BLOCK, 3), mat(Material.FIRE_CHARGE, 8))));
        add(out, standaloneArmor("graveveil_hood", Material.LEATHER_HELMET, "Graveveil Hood", CustomLoreUtil.Rarity.RARE, EquipmentSlotGroup.HEAD,
            "A hood that keeps one eye in the dark.", ActiveAbility.NONE,
            List.of(relic("widow_silk", 4), mat(Material.PHANTOM_MEMBRANE, 4), mat(Material.LEATHER, 12))));
        add(out, standaloneArmor("bastion_pauldrons", Material.IRON_CHESTPLATE, "Bastion Pauldrons", CustomLoreUtil.Rarity.EPIC, EquipmentSlotGroup.CHEST,
            "Iron shoulders that remember every shield wall.", ActiveAbility.NONE,
            List.of(relic("titan_gear", 4), mat(Material.IRON_BLOCK, 5), mat(Material.SHIELD, 1))));
        add(out, standaloneArmor("sculkplate_harness", Material.DIAMOND_CHESTPLATE, "Sculkplate Harness", CustomLoreUtil.Rarity.LEGENDARY, EquipmentSlotGroup.CHEST,
            "A chestpiece that refuses the Warden's dark.", ActiveAbility.NONE,
            List.of(relic("sculk_heart", 2), mat(Material.DIAMOND_CHESTPLATE, 1), mat(Material.SCULK, 32))));
        add(out, standaloneArmor("stormcall_greaves", Material.CHAINMAIL_LEGGINGS, "Stormcall Greaves", CustomLoreUtil.Rarity.RARE, EquipmentSlotGroup.LEGS,
            "Chain greaves with thunder caught between links.", ActiveAbility.NONE,
            List.of(relic("solar_ember", 2), mat(Material.COPPER_BLOCK, 6), mat(Material.IRON_INGOT, 16))));
        add(out, standaloneArmor("rootwarden_greaves", Material.DIAMOND_LEGGINGS, "Rootwarden Greaves", CustomLoreUtil.Rarity.EPIC, EquipmentSlotGroup.LEGS,
            "Leg armor grown around a buried pulse.", ActiveAbility.NONE,
            List.of(relic("living_bark", 6), relic("verdant_heart", 1), mat(Material.DIAMOND_LEGGINGS, 1))));
        add(out, standaloneArmor("siren_treads", Material.DIAMOND_BOOTS, "Siren Treads", CustomLoreUtil.Rarity.EPIC, EquipmentSlotGroup.FEET,
            "Boots that learned to step on water's throat.", ActiveAbility.NONE,
            List.of(relic("abyssal_pearl", 4), mat(Material.DIAMOND_BOOTS, 1), mat(Material.KELP, 32))));
        add(out, standaloneArmor("voidstep_boots", Material.NETHERITE_BOOTS, "Voidstep Boots", CustomLoreUtil.Rarity.LEGENDARY, EquipmentSlotGroup.FEET,
            "Boots for falling through where the world forgot floor.", ActiveAbility.NONE,
            List.of(relic("rift_lens", 4), mat(Material.NETHERITE_BOOTS, 1), mat(Material.FEATHER, 16))));
        add(out, standaloneArmor("glasswalker_boots", Material.LEATHER_BOOTS, "Glasswalker Boots", CustomLoreUtil.Rarity.RARE, EquipmentSlotGroup.FEET,
            "Soft boots for crossing things that should break.", ActiveAbility.NONE,
            List.of(mat(Material.LEATHER_BOOTS, 1), mat(Material.BLUE_ICE, 16), mat(Material.AMETHYST_SHARD, 8))));
        add(out, standaloneArmor("oathkeeper_helm", Material.IRON_HELMET, "Oathkeeper Helm", CustomLoreUtil.Rarity.EPIC, EquipmentSlotGroup.HEAD,
            "A helm that gets louder when blood gets low.", ActiveAbility.NONE,
            List.of(relic("oathbound_plate", 4), mat(Material.IRON_HELMET, 1), mat(Material.GOLDEN_APPLE, 8))));
        add(out, standaloneArmor("revelator_helm", Material.IRON_HELMET, "Revelator Helm", CustomLoreUtil.Rarity.EPIC, EquipmentSlotGroup.HEAD,
            "An iron visor that drags hidden names back into the world.",
            ActiveAbility.NONE,
            List.of(
                mat(Material.IRON_HELMET, 1),
                relic("oathbound_plate", 2),
                mat(Material.RECOVERY_COMPASS, 1),
                mat(Material.ECHO_SHARD, 4),
                mat(Material.AMETHYST_SHARD, 16),
                mat(Material.GLOWSTONE_DUST, 16)
            )));

        add(out, utility("bloodbound_banner", Material.RED_BANNER, "Bloodbound Banner", CustomLoreUtil.Rarity.LEGENDARY, ActiveAbility.TEAM_BANNER, 120,
            "Raises a short team battle oath around the caster.",
            List.of(relic("crimson_rib", 3), relic("oathbound_plate", 2), mat(Material.RED_BANNER, 1))));
        add(out, utility("rift_anchor", Material.RESPAWN_ANCHOR, "Rift Anchor", CustomLoreUtil.Rarity.MYTHIC, ActiveAbility.RIFT_ANCHOR, 90,
            "Sneak-right-click to bind. Right-click to return.",
            List.of(relic("rift_lens", 6), mat(Material.RESPAWN_ANCHOR, 1), mat(Material.ENDER_PEARL, 16))));
        add(out, utility("ember_vial", Material.HONEY_BOTTLE, "Ember Vial", CustomLoreUtil.Rarity.RARE, ActiveAbility.EMBER_VIAL, 90,
            "Swallow the last heat of Kael's burned sky.",
            List.of(relic("solar_ember", 2), mat(Material.GLASS_BOTTLE, 1), mat(Material.BLAZE_POWDER, 8))));
        add(out, utility("widow_antidote", Material.POTION, "Widow Antidote", CustomLoreUtil.Rarity.RARE, ActiveAbility.WIDOW_ANTIDOTE, 75,
            "A bitter cure made from the poison that taught it.",
            List.of(relic("widow_silk", 2), mat(Material.MILK_BUCKET, 1), mat(Material.SPIDER_EYE, 8))));
        add(out, utility("saints_whetstone", Material.POLISHED_BLACKSTONE, "Saint's Whetstone", CustomLoreUtil.Rarity.EPIC, ActiveAbility.SAINT_WHETSTONE, 300,
            "Repairs a held item by roughly a quarter of max durability.",
            List.of(relic("saint_alloy", 1), relic("titan_gear", 4), mat(Material.GRINDSTONE, 1))));
        add(out, utility("abyssal_conch", Material.NAUTILUS_SHELL, "Abyssal Conch", CustomLoreUtil.Rarity.EPIC, ActiveAbility.ABYSSAL_CONCH, 100,
            "A shell that lets the drowned road open for you.",
            List.of(relic("abyssal_pearl", 4), mat(Material.NAUTILUS_SHELL, 1), mat(Material.PRISMARINE_CRYSTALS, 16))));
        add(out, utility("root_sigil", Material.FLOWER_BANNER_PATTERN, "Root Sigil", CustomLoreUtil.Rarity.EPIC, ActiveAbility.ROOT_SIGIL, 120,
            "Calls nearby allies back from the edge of the grave.",
            List.of(relic("verdant_heart", 1), relic("living_bark", 6), mat(Material.SPORE_BLOSSOM, 1))));
        add(out, utility("titan_charm", Material.HEAVY_CORE, "Titan Charm", CustomLoreUtil.Rarity.LEGENDARY, ActiveAbility.TITAN_CHARM, 120,
            "A compact order to stop moving.",
            List.of(relic("titan_gear", 6), mat(Material.HEAVY_CORE, 1), mat(Material.IRON_BLOCK, 8))));
        add(out, utility("oathglass_compass", Material.RECOVERY_COMPASS, "Oathglass Compass", CustomLoreUtil.Rarity.EPIC, ActiveAbility.OATHGLASS_COMPASS, 180,
            "Points toward the nearest living player in broad strokes.",
            List.of(relic("oathbound_plate", 3), mat(Material.RECOVERY_COMPASS, 1), mat(Material.AMETHYST_SHARD, 16))));
        add(out, utility("warped_key", Material.TRIAL_KEY, "Warped Key", CustomLoreUtil.Rarity.EPIC, ActiveAbility.WARPED_KEY, 30,
            "A short-range blink key with just enough mercy.",
            List.of(relic("rift_lens", 3), mat(Material.TRIAL_KEY, 1), mat(Material.ENDER_PEARL, 12))));
        add(out, utility("nullbell", Material.BELL, "Nullbell", CustomLoreUtil.Rarity.EPIC, ActiveAbility.NULL_BELL, 150,
            "A bell that rings the venom out of an oath.",
            List.of(relic("oathbound_plate", 4), relic("widow_silk", 3), mat(Material.BELL, 1), mat(Material.MILK_BUCKET, 1))));
        add(out, utility("riftward_lens", Material.SPYGLASS, "Riftward Lens", CustomLoreUtil.Rarity.EPIC, ActiveAbility.RIFTWARD_LENS, 120,
            "A glass eye that catches cowards hiding between breaths.",
            List.of(relic("rift_lens", 4), mat(Material.SPYGLASS, 1), mat(Material.GLOWSTONE_DUST, 24), mat(Material.AMETHYST_SHARD, 12))));
        add(out, utility("gravetide_phial", Material.DRAGON_BREATH, "Gravetide Phial", CustomLoreUtil.Rarity.LEGENDARY, ActiveAbility.GRAVETIDE_PHIAL, 180,
            "A bottled undertow that remembers every body it carried.",
            List.of(relic("abyssal_pearl", 5), relic("crimson_rib", 2), mat(Material.DRAGON_BREATH, 1), mat(Material.SOUL_SAND, 16))));
        add(out, utility("oathkeeper_cord", Material.LEAD, "Oathkeeper Cord", CustomLoreUtil.Rarity.RARE, ActiveAbility.OATHKEEPER_CORD, 90,
            "A bright cord for pulling the living out of bad decisions.",
            List.of(relic("oathbound_plate", 2), mat(Material.LEAD, 1), mat(Material.FEATHER, 12), mat(Material.HONEYCOMB, 8))));
        add(out, utility("saints_ledger", Material.BOOK, "Saint's Ledger", CustomLoreUtil.Rarity.LEGENDARY, ActiveAbility.SAINT_LEDGER, 300,
            "A machine-prayer that audits every dent in your armor.",
            List.of(relic("saint_alloy", 1), relic("titan_gear", 5), mat(Material.BOOK, 1), mat(Material.EXPERIENCE_BOTTLE, 16))));

        return out;
    }

    private static void add(Map<String, RelicDefinition> out, RelicDefinition definition) {
        out.put(definition.id(), definition);
    }

    private static RelicDefinition material(String id, Material material, String name, CustomLoreUtil.Rarity rarity, String line, String source) {
        return new RelicDefinition(
            id, material, name, rarity, RelicKind.MATERIAL, RelicCategory.MATERIALS,
            null, null, 0, WeaponEffect.NONE, ActiveAbility.NONE, 0,
            List.of("<gray>" + line + "</gray>"),
            List.of(CustomLoreUtil.section("Source", source, "<gray>Dropped by this custom boss.</gray>")),
            List.of(), List.of(), Set.of(commandToken(name))
        );
    }

    private static RelicDefinition weapon(
        String id,
        Material material,
        String name,
        CustomLoreUtil.Rarity rarity,
        double damageBonus,
        double speedBonus,
        WeaponEffect effect,
        String line,
        List<RecipeIngredient> recipe
    ) {
        List<AttributeBonus> attributes = new ArrayList<>();
        double tunedDamage = damageBonus == 0.0 ? 0.0 : damageBonus + switch (rarity) {
            case MYTHIC -> 2.5;
            case LEGENDARY -> 2.0;
            case EPIC -> 1.25;
            default -> 0.75;
        };
        double tunedSpeed = speedBonus == 0.0 ? 0.0 : speedBonus + switch (rarity) {
            case MYTHIC -> 0.18;
            case LEGENDARY -> 0.14;
            case EPIC -> 0.10;
            default -> 0.06;
        };
        if (tunedDamage != 0.0) attributes.add(new AttributeBonus(Attribute.ATTACK_DAMAGE, tunedDamage, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        if (tunedSpeed != 0.0) attributes.add(new AttributeBonus(Attribute.ATTACK_SPEED, tunedSpeed, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        return new RelicDefinition(
            id, material, name, rarity, RelicKind.WEAPON, RelicCategory.WEAPONS,
            null, null, material.getMaxDurability() > 0 ? Math.max(material.getMaxDurability() * 2, material.getMaxDurability() + 512) : 0,
            effect, ActiveAbility.NONE, 0,
            List.of("<gray>" + line + "</gray>"),
            List.of(
                CustomLoreUtil.section("Role", "PvP Friendly Power", "<gray>Strong effects are either short, conditional, or cooldown-gated.</gray>"),
                CustomLoreUtil.section("Echo", effect.label(), effect.description())
            ),
            attributes, recipe, Set.of(commandToken(name))
        );
    }

    private static RelicDefinition utility(
        String id,
        Material material,
        String name,
        CustomLoreUtil.Rarity rarity,
        ActiveAbility ability,
        int cooldown,
        String line,
        List<RecipeIngredient> recipe
    ) {
        return new RelicDefinition(
            id, material, name, rarity, RelicKind.UTILITY, RelicCategory.UTILITIES,
            null, null, 0, WeaponEffect.NONE, ability, cooldown,
            List.of("<gray>" + line + "</gray>"),
            List.of(
                CustomLoreUtil.section("Ability", ability.label(), ability.description()),
                CustomLoreUtil.section("Cooldown", cooldown + " seconds", "<gray>Cooldowns are saved on the player, so relogging does not reset them.</gray>")
            ),
            List.of(), recipe, Set.of(commandToken(name))
        );
    }

    private static RelicDefinition standaloneArmor(
        String id,
        Material material,
        String name,
        CustomLoreUtil.Rarity rarity,
        EquipmentSlotGroup slot,
        String line,
        ActiveAbility ability,
        List<RecipeIngredient> recipe
    ) {
        List<AttributeBonus> attributes = armorAttributes(slot, rarity, false);
        return new RelicDefinition(
            id, material, name, rarity, RelicKind.ARMOR, RelicCategory.ARMOR_PIECES,
            null, equipmentSlotFromGroup(slot), material.getMaxDurability() > 0 ? material.getMaxDurability() * 2 : 0,
            WeaponEffect.NONE, ability, 0,
            List.of("<gray>" + line + "</gray>"),
            List.of(CustomLoreUtil.section("Passive", passiveName(id), passiveText(id))),
            attributes, recipe, Set.of(commandToken(name))
        );
    }

    private static void addArmorSet(
        Map<String, RelicDefinition> out,
        String setId,
        String setName,
        CustomLoreUtil.Rarity rarity,
        Material helmet,
        Material chestplate,
        Material leggings,
        Material boots,
        String line,
        List<RecipeIngredient> sharedMaterials
    ) {
        add(out, armorSetPiece(setId, "helm", helmet, setName + " Helm", rarity, EquipmentSlot.HEAD, EquipmentSlotGroup.HEAD, line, sharedMaterials));
        add(out, armorSetPiece(setId, "chestplate", chestplate, setName + " Chestplate", rarity, EquipmentSlot.CHEST, EquipmentSlotGroup.CHEST, line, sharedMaterials));
        add(out, armorSetPiece(setId, "leggings", leggings, setName + " Leggings", rarity, EquipmentSlot.LEGS, EquipmentSlotGroup.LEGS, line, sharedMaterials));
        add(out, armorSetPiece(setId, "boots", boots, setName + " Boots", rarity, EquipmentSlot.FEET, EquipmentSlotGroup.FEET, line, sharedMaterials));
    }

    private static RelicDefinition armorSetPiece(
        String setId,
        String piece,
        Material material,
        String name,
        CustomLoreUtil.Rarity rarity,
        EquipmentSlot equipmentSlot,
        EquipmentSlotGroup slot,
        String line,
        List<RecipeIngredient> sharedMaterials
    ) {
        List<RecipeIngredient> recipe = new ArrayList<>(sharedMaterials);
        recipe.add(mat(material, 1));
        return new RelicDefinition(
            setId + "_" + piece,
            material,
            name,
            rarity,
            RelicKind.ARMOR,
            RelicCategory.ARMOR_SETS,
            setId,
            equipmentSlot,
            material.getMaxDurability() > 0 ? material.getMaxDurability() * 2 : 0,
            WeaponEffect.NONE,
            ActiveAbility.NONE,
            0,
            List.of("<gray>" + line + "</gray>"),
            List.of(
                CustomLoreUtil.section("Set Bonus", setTitle(setId), setBonusText(setId)),
                CustomLoreUtil.section("Condition", "Full Set Required", "<gray>All four pieces must be worn together.</gray>")
            ),
            armorAttributes(slot, rarity, true),
            recipe,
            Set.of(commandToken(name), setId)
        );
    }

    private static List<AttributeBonus> armorAttributes(EquipmentSlotGroup slot, CustomLoreUtil.Rarity rarity, boolean setPiece) {
        boolean highTier = rarity.ordinal() >= CustomLoreUtil.Rarity.LEGENDARY.ordinal();
        double armor = setPiece
            ? (rarity == CustomLoreUtil.Rarity.MYTHIC ? 6.0 : highTier ? 4.75 : 3.25)
            : (rarity == CustomLoreUtil.Rarity.MYTHIC ? 3.75 : highTier ? 3.0 : 2.0);
        double toughness = rarity == CustomLoreUtil.Rarity.MYTHIC ? 3.0 : highTier ? 2.25 : 1.25;
        List<AttributeBonus> attributes = new ArrayList<>();
        attributes.add(new AttributeBonus(Attribute.ARMOR, armor, AttributeModifier.Operation.ADD_NUMBER, slot));
        attributes.add(new AttributeBonus(Attribute.ARMOR_TOUGHNESS, toughness, AttributeModifier.Operation.ADD_NUMBER, slot));
        if (setPiece && highTier) {
            attributes.add(new AttributeBonus(
                Attribute.KNOCKBACK_RESISTANCE,
                rarity == CustomLoreUtil.Rarity.MYTHIC ? 0.07 : 0.05,
                AttributeModifier.Operation.ADD_NUMBER,
                slot
            ));
        }
        return List.copyOf(attributes);
    }

    private static EquipmentSlot equipmentSlotFromGroup(EquipmentSlotGroup slot) {
        if (slot == EquipmentSlotGroup.HEAD) return EquipmentSlot.HEAD;
        if (slot == EquipmentSlotGroup.CHEST) return EquipmentSlot.CHEST;
        if (slot == EquipmentSlotGroup.LEGS) return EquipmentSlot.LEGS;
        if (slot == EquipmentSlotGroup.FEET) return EquipmentSlot.FEET;
        return null;
    }

    private static RecipeIngredient mat(Material material, int amount) {
        return new RecipeIngredient(material, null, amount);
    }

    private static RecipeIngredient relic(String relicId, int amount) {
        return new RecipeIngredient(null, relicId, amount);
    }

    private static String setTitle(String setId) {
        return switch (setId) {
            case "crimson_guard" -> "Blood-Wake Guard";
            case "widow_court" -> "Court of Quiet Teeth";
            case "ashen_saint" -> "Ashen Benediction";
            case "tidebound" -> "Drowned Oath";
            case "riftwalker" -> "Between-World Step";
            default -> "Covenant Set";
        };
    }

    private static String setBonusText(String setId) {
        return "<gray>" + setBonusPlain(setId) + "</gray>";
    }

    private static String setBonusPlain(String setId) {
        return switch (setId) {
            case "crimson_guard" -> "Resistance I. At low health, gain Strength II and Regeneration I.";
            case "widow_court" -> "Speed II, Jump Boost I, and poison is cleansed while worn.";
            case "ashen_saint" -> "Fire Resistance, Haste II, and Strength I.";
            case "tidebound" -> "Water Breathing. In water or rain, gain Dolphin's Grace, Speed II, and Regeneration I.";
            case "riftwalker" -> "Slow Falling, Speed II, and Resistance I.";
            default -> "Wear every piece to wake the set.";
        };
    }

    private static String passiveName(String id) {
        return switch (id) {
            case "crown_of_cinders" -> "Cinder Crown";
            case "graveveil_hood" -> "Grave Sight";
            case "bastion_pauldrons" -> "Bastion Skin";
            case "sculkplate_harness" -> "Darkness Denied";
            case "stormcall_greaves" -> "Storm March";
            case "rootwarden_greaves" -> "Overgrowth";
            case "siren_treads" -> "Siren Step";
            case "voidstep_boots" -> "Soft Void";
            case "glasswalker_boots" -> "Icewalker's Rush";
            case "oathkeeper_helm" -> "Last Oath";
            case "revelator_helm" -> "True Sight";
            default -> "Relic Passive";
        };
    }

    private static String passiveText(String id) {
        return switch (id) {
            case "crown_of_cinders" -> "<gray>Grants Fire Resistance while worn.</gray>";
            case "graveveil_hood" -> "<gray>Refreshes Night Vision before it flickers.</gray>";
            case "bastion_pauldrons" -> "<gray>Refreshes a small Absorption shield.</gray>";
            case "sculkplate_harness" -> "<gray>Resists damage and clears Darkness.</gray>";
            case "stormcall_greaves" -> "<gray>Grants Speed I while worn, plus Haste I during storms.</gray>";
            case "rootwarden_greaves" -> "<gray>Regenerates slowly in the Overworld.</gray>";
            case "siren_treads" -> "<gray>Water Breathing, plus Dolphin's Grace in water or rain.</gray>";
            case "voidstep_boots" -> "<gray>Grants Slow Falling while worn.</gray>";
            case "glasswalker_boots" -> "<gray>Gain Speed II on ice.</gray>";
            case "oathkeeper_helm" -> "<gray>At low health, refreshes Absorption II and Resistance I.</gray>";
            case "revelator_helm" -> "<gray>Invisible players within 48 blocks glow while this helmet is worn.</gray>";
            default -> "<gray>A quiet relic effect.</gray>";
        };
    }

    public enum RelicCategory {
        WEAPONS(
            "weapons",
            "Covenant Weapons",
            Material.NETHERITE_SWORD,
            "<gradient:#ff4d6d:#f97316><bold>Covenant Weapons</bold></gradient>",
            List.of("<gray>Thirteen PvP-aware weapons and tools with cooldown-gated or conditional pressure.</gray>")
        ),
        ARMOR_SETS(
            "armor_sets",
            "Covenant Sets",
            Material.NETHERITE_CHESTPLATE,
            "<gradient:#facc15:#ef4444><bold>Full Armor Sets</bold></gradient>",
            List.of("<gray>Five four-piece sets. The combo effect only wakes when the full set is worn.</gray>")
        ),
        ARMOR_PIECES(
            "armor_pieces",
            "Solitary Armor",
            Material.DIAMOND_HELMET,
            "<gradient:#67e8f9:#a78bfa><bold>Solitary Armor</bold></gradient>",
            List.of("<gray>Eleven individual pieces with stand-alone passives.</gray>")
        ),
        UTILITIES(
            "utilities",
            "Covenant Utility",
            Material.RECOVERY_COMPASS,
            "<gradient:#c084fc:#fb7185><bold>Utility Relics</bold></gradient>",
            List.of("<gray>Fifteen tactical tools for teams, travel, cleansing, repairs, and scouting.</gray>")
        ),
        MATERIALS(
            "materials",
            "Boss Materials",
            Material.ECHO_SHARD,
            "<gradient:#94a3b8:#f8fafc><bold>Boss Materials</bold></gradient>",
            List.of("<gray>Trophies dropped by custom bosses and used in Covenant recipes.</gray>")
        );

        private final String id;
        private final String plainTitle;
        private final Material icon;
        private final String title;
        private final List<String> lore;

        RelicCategory(String id, String plainTitle, Material icon, String title, List<String> lore) {
            this.id = id;
            this.plainTitle = plainTitle;
            this.icon = icon;
            this.title = title;
            this.lore = lore;
        }

        private String id() {
            return id;
        }

        private String plainTitle() {
            return plainTitle;
        }

        private Material icon() {
            return icon;
        }

        private String title() {
            return title;
        }

        private List<String> lore() {
            return lore;
        }

        public static RelicCategory fromId(String id) {
            for (RelicCategory category : values()) {
                if (category.id.equals(id)) {
                    return category;
                }
            }
            return null;
        }
    }

    private enum MenuView {
        HUB,
        CATEGORY,
        ARMOR_SET,
        RELIC
    }

    private enum ArmoryBackTarget {
        RELIQUARY,
        RELIQUARY_THEN_MAIN_MENU,
        MAIN_MENU
    }

    private enum RelicKind {
        WEAPON("WEAPON"),
        ARMOR("ARMOR"),
        UTILITY("RELIC"),
        MATERIAL("TROPHY");

        private final String label;

        RelicKind(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }
    }

    private enum WeaponEffect {
        NONE("None", "<gray>No active weapon effect.</gray>"),
        ASHEN_VERDICT("Ashen Verdict", "<gray>Heavy bonus damage and longer fire pressure.</gray>"),
        WIDOWFANG("Venom Hook", "<gray>Cooldown Poison II and Slowness II on hit.</gray>"),
        RIFT_PIKE("Rift Pull", "<gray>Periodically pulls targets toward the wielder.</gray>"),
        SAINTSPLITTER("Iron Sentence", "<gray>Bonus damage and cooldown Weakness II.</gray>"),
        TIDEBREAKER("Storm Edge", "<gray>Deals bonus damage always, and much more in water or rain.</gray>"),
        GRAVEMOURN("Soul Tithe", "<gray>Bonus damage and lifesteal on a short cooldown.</gray>"),
        NULLGLASS("Blessing Cut", "<gray>Removes one beneficial effect and briefly weakens on cooldown.</gray>"),
        SUNLESS_REPEATER("Sunless Bolt", "<gray>Bolts inflict Wither and Darkness.</gray>"),
        THORNWHISPER("Rooted Shot", "<gray>Arrows apply strong Slowness and brief Mining Fatigue.</gray>"),
        CINDERSHARD("Back Cinder", "<gray>Backstabs deal extra damage and grant speed.</gray>"),
        OATHBREAKER("Shrinebreaker", "<gray>Deals bonus damage to custom bosses and weakens targets on cooldown.</gray>"),
        DUSKBELL("Duskbell Shock", "<gray>Cooldown sonic burst damages and slows enemies near the target.</gray>"),
        BRIARHOOK("Briar Chase", "<gray>Slows targets and executes wounded enemies with bonus pressure.</gray>");

        private final String label;
        private final String description;

        WeaponEffect(String label, String description) {
            this.label = label;
            this.description = description;
        }

        private String label() {
            return label;
        }

        private String description() {
            return description;
        }
    }

    private enum ActiveAbility {
        NONE("None", "<gray>No active ability.</gray>"),
        TEAM_BANNER("Bloodbound Oath", "<gray>Buffs the caster and nearby teammates with Strength, Resistance, and Absorption.</gray>"),
        RIFT_ANCHOR("Bound Rift", "<gray>Sneak-right-click to bind. Right-click to teleport back.</gray>"),
        EMBER_VIAL("Last Ember", "<gray>Fire Resistance and extinguishes the caster.</gray>"),
        WIDOW_ANTIDOTE("Black Antidote", "<gray>Cleanses poison, wither, and slowness.</gray>"),
        SAINT_WHETSTONE("Saint's Repair", "<gray>Repairs the held damaged item by about 25%.</gray>"),
        ABYSSAL_CONCH("Drowned Breath", "<gray>Water Breathing and Dolphin's Grace.</gray>"),
        ROOT_SIGIL("Root Mercy", "<gray>Regeneration and Absorption aura for the caster and nearby teammates.</gray>"),
        TITAN_CHARM("Titan Brace", "<gray>Resistance II and Absorption II for a short window.</gray>"),
        OATHGLASS_COMPASS("Oathglass Reading", "<gray>Shows a broad direction and distance to the nearest player.</gray>"),
        WARPED_KEY("Warp Step", "<gray>Short-range blink with safe-location checks.</gray>"),
        NULL_BELL("Nullbell Peal", "<gray>Cleanses harmful effects from the caster and nearby teammates.</gray>"),
        RIFTWARD_LENS("Riftward Sight", "<gray>Briefly reveals nearby threats through invisibility.</gray>"),
        GRAVETIDE_PHIAL("Gravetide Break", "<gray>Slows, weakens, and pushes nearby enemies away.</gray>"),
        OATHKEEPER_CORD("Oathkeeper Rush", "<gray>Gives the caster and nearby teammates Speed II and Slow Falling.</gray>"),
        SAINT_LEDGER("Saint's Ledger", "<gray>Repairs worn gear and the held item slightly, then returns a little XP.</gray>");

        private final String label;
        private final String description;

        ActiveAbility(String label, String description) {
            this.label = label;
            this.description = description;
        }

        private String label() {
            return label;
        }

        private String description() {
            return description;
        }
    }

    private record SeasonMenuHolder(MenuView view, RelicCategory category, String relicId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record RecipeIngredient(Material material, String relicId, int amount) {
        private RecipeIngredient {
            amount = Math.max(1, amount);
        }

        private boolean matches(SeasonRelicManager manager, ItemStack item) {
            if (item == null || item.getType().isAir()) {
                return false;
            }
            if (material != null) {
                return item.getType() == material && !manager.isProtectedCustomIngredient(item);
            }
            return manager.matchesExternalRelic(relicId, item);
        }

        private ItemStack displayItem(SeasonRelicManager manager) {
            ItemStack item = material == null
                ? manager.displayExternalRelic(relicId, amount)
                : new ItemStack(material, Math.min(amount, material.getMaxStackSize()));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                CustomLoreUtil.addSpacer(lore);
                lore.add(MM.deserialize("<gray>Required: <white>" + amount + "x</white></gray>"));
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            return item;
        }

        private String name(SeasonRelicManager manager) {
            if (material != null) {
                return prettyName(material.name());
            }
            RelicDefinition definition = manager.definition(relicId);
            if (definition != null) {
                return definition.name();
            }
            return prettyName(relicId);
        }
    }

    private record AttributeBonus(Attribute attribute, double amount, AttributeModifier.Operation operation, EquipmentSlotGroup slot) {
    }

    private record RelicDefinition(
        String id,
        Material material,
        String name,
        CustomLoreUtil.Rarity rarity,
        RelicKind kind,
        RelicCategory category,
        String armorSetId,
        EquipmentSlot equipmentSlot,
        int maxDamage,
        WeaponEffect weaponEffect,
        ActiveAbility activeAbility,
        int activeCooldownSeconds,
        List<String> topLore,
        List<CustomLoreUtil.LoreSection> sections,
        List<AttributeBonus> attributes,
        List<RecipeIngredient> recipe,
        Set<String> aliases
    ) {
        private RelicDefinition {
            Objects.requireNonNull(id);
            topLore = List.copyOf(topLore);
            sections = List.copyOf(sections);
            attributes = List.copyOf(attributes);
            recipe = List.copyOf(recipe);
            aliases = Set.copyOf(aliases);
        }
    }
}
