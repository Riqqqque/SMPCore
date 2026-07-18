package me.rique.smpcore.season;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.boss.BossBalance;
import me.rique.smpcore.boss.BossManager;
import me.rique.smpcore.command.MainMenuCommand;
import me.rique.smpcore.combat.AbilityDamageContext;
import me.rique.smpcore.item.VeilOrbManager;
import me.rique.smpcore.legendary.MythicForgeListener;
import me.rique.smpcore.power.SuperpowerManager;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.ItemModelUtil;
import me.rique.smpcore.util.LocationUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.Tag;
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
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
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
import java.util.concurrent.ThreadLocalRandom;
import java.time.Duration;

/**
 * Season relic layer. This keeps the large Armory expansion out of the older
 * legendary listener while still feeding the same Reliquary/admin/audit surfaces.
 */
public final class SeasonRelicManager implements Listener {

    public static final String ARMORY_MENU_ID = "veil_armory";
    public static final String SOUL_IMPRINT_ID = "soul_imprint";
    private static final String LEGACY_ARMORY_MENU_ID = "covenant_armory";

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
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
    private static final int PASSIVE_EFFECT_TICKS = 12 * 20;
    private static final int PASSIVE_NIGHT_VISION_TICKS = 30 * 20;
    private static final double TRUE_SIGHT_RADIUS = 48.0;
    private static final long BEDROCK_RELIC_ACTIVATION_DEBOUNCE_MS = 650L;
    private static final int SOUL_IMPRINT_REQUIRED_CLICKS = 3;
    private static final int CONFESSOR_LEDGER_MAX_USES = 4;
    private static final long SOUL_IMPRINT_CONFIRM_MS = 15_000L;
    private static final double SEASON_WEAPON_PVP_MULTIPLIER = 0.78;
    private static final double FULL_SET_BOSS_DAMAGE_REDUCTION = 0.06;
    private static final double MAX_BOSS_DAMAGE_REDUCTION = 0.40;
    private static final String SOUL_IMPRINT_FIRST_OWNER_PATH = "milestones.soul-imprint-first.owner";
    private static final String REMOVED_OATHGLASS_COMPASS_ID = "oathglass_compass";

    private final SMPCore plugin;
    private final NamespacedKey keyRelicId;
    private final NamespacedKey keyMenuAction;
    private final NamespacedKey keyMenuValue;
    private final NamespacedKey keyProjectileRelic;
    private final NamespacedKey keySoulImprinted;
    private final NamespacedKey keySoulImprintHeldDiscovery;
    private final NamespacedKey keyConfessorLedgerUses;

    private final Map<String, RelicDefinition> relics;
    private final Map<RelicCategory, List<RelicDefinition>> relicsByCategory;
    private final Map<String, List<RelicDefinition>> armorSets;
    private final Map<UUID, ArmoryBackTarget> menuBackTargets = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> bedrockRelicActivationDebounces = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, SoulImprintConfirmation> soulImprintConfirmations = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<UUID> trueSightGlowingTargets = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private BukkitTask passiveTask;

    public SeasonRelicManager(SMPCore plugin) {
        this.plugin = plugin;
        this.keyRelicId = new NamespacedKey(plugin, "season_relic_id");
        this.keyMenuAction = new NamespacedKey(plugin, "season_menu_action");
        this.keyMenuValue = new NamespacedKey(plugin, "season_menu_value");
        this.keyProjectileRelic = new NamespacedKey(plugin, "season_projectile_relic");
        this.keySoulImprinted = new NamespacedKey(plugin, "soul_imprinted");
        this.keySoulImprintHeldDiscovery = new NamespacedKey(plugin, "held_soul_imprint");
        this.keyConfessorLedgerUses = new NamespacedKey(plugin, "confessor_ledger_uses");
        this.relics = buildRelics();
        this.relicsByCategory = groupByCategory(relics.values());
        this.armorSets = groupArmorSets(relics.values());
    }

    public void start() {
        if (passiveTask != null) {
            passiveTask.cancel();
        }
        passiveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPassives, PASSIVE_TICKS, PASSIVE_TICKS);
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::refundRemovedOathglassCompasses));
    }

    public void shutdown() {
        if (passiveTask != null) {
            passiveTask.cancel();
            passiveTask = null;
        }
        bedrockRelicActivationDebounces.clear();
        soulImprintConfirmations.clear();
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
        String id = rawRelicId(item);
        return relics.containsKey(id) ? id : null;
    }

    private String rawRelicId(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(keyRelicId, PersistentDataType.STRING);
    }

    public boolean isSeasonRelic(ItemStack item) {
        return relicId(item) != null;
    }

    public boolean isSoulImprint(ItemStack item) {
        return SOUL_IMPRINT_ID.equals(relicId(item));
    }

    public boolean isSoulImprinted(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(keySoulImprinted, PersistentDataType.BYTE);
    }

    public boolean hasHeldSoulImprint(Player player) {
        return player != null
            && player.getPersistentDataContainer().has(keySoulImprintHeldDiscovery, PersistentDataType.BYTE);
    }

    public String soulImprintDisplayName(Player player) {
        return soulImprintDisplayName(hasHeldSoulImprint(player));
    }

    static String soulImprintDisplayName(boolean discovered) {
        return discovered
            ? "Soul Imprint"
            : "<obfuscated>Soul Imprint</obfuscated>";
    }

    public CustomLoreUtil.Rarity relicRarity(ItemStack item) {
        RelicDefinition definition = relics.get(relicId(item));
        return definition == null ? null : definition.rarity();
    }

    public boolean isVeilArmor(ItemStack item) {
        RelicDefinition definition = relics.get(relicId(item));
        return definition != null && definition.kind() == RelicKind.ARMOR;
    }

    public String displayNameFor(String input) {
        RelicDefinition definition = definition(input);
        return definition == null ? null : definition.name();
    }

    public void markRelicDiscovered(Player player, String relicId) {
        if (player == null || relicId == null || relicId.isBlank() || !relics.containsKey(relicId)) {
            return;
        }
        player.getPersistentDataContainer().set(discoveryKey(relicId), PersistentDataType.BYTE, (byte) 1);
    }

    private void detectSoulImprintHeld(Player player) {
        if (player == null || player.isDead()) {
            return;
        }
        if (!isSoulImprint(player.getInventory().getItemInMainHand())
            && !isSoulImprint(player.getInventory().getItemInOffHand())) {
            return;
        }
        discoverSoulImprint(player);
    }

    private void discoverSoulImprint(Player player) {
        if (hasHeldSoulImprint(player)) {
            return;
        }
        player.getPersistentDataContainer().set(keySoulImprintHeldDiscovery, PersistentDataType.BYTE, (byte) 1);
        player.showTitle(Title.title(
            MM.deserialize("<gradient:#f0abfc:#facc15><bold>SOUL IMPRINT</bold></gradient>"),
            MM.deserialize("<white>You hold an echo capable of copying fate.</white>"),
            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(900))
        ));
        Location effect = player.getLocation().add(0.0, 1.1, 0.0);
        player.getWorld().spawnParticle(Particle.END_ROD, effect, 80, 0.8, 0.9, 0.8, 0.05);
        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, effect, 100, 0.9, 1.0, 0.9, 0.12);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
        player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.8f, 1.25f);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.65f);
        player.sendMessage(MessageUtil.success("You discovered your first <light_purple>Soul Imprint</light_purple>. Handle it carefully."));
        if (plugin.getStoryService() != null) {
            plugin.getStoryService().onSoulImprintDiscovered(player);
        }

        if (plugin.getConfig().getString(SOUL_IMPRINT_FIRST_OWNER_PATH) == null) {
            plugin.getConfig().set(SOUL_IMPRINT_FIRST_OWNER_PATH, player.getUniqueId().toString());
            plugin.getConfig().set("milestones.soul-imprint-first.name", player.getName());
            plugin.getConfig().set("milestones.soul-imprint-first.acquired-at", System.currentTimeMillis());
            plugin.saveConfig();
            Bukkit.broadcast(MessageUtil.prefixedRaw(
                "<gradient:#f0abfc:#facc15><bold>SERVER FIRST!</bold></gradient> <white>"
                    + escapeMiniMessage(player.getName())
                    + "</white> <gray>is the first player to acquire a</gray> <light_purple><bold>Soul Imprint</bold></light_purple><gray>!</gray>"
            ));
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.05f);
            }
        }
    }

    public boolean hasRelicDiscovered(Player player, String relicId) {
        if (player == null || relicId == null || relicId.isBlank()) {
            return false;
        }
        if (player.getPersistentDataContainer().has(discoveryKey(relicId), PersistentDataType.BYTE)) {
            return true;
        }
        return containsRelic(player.getInventory().getContents(), relicId)
            || containsRelic(player.getInventory().getArmorContents(), relicId)
            || containsRelic(new ItemStack[] {player.getInventory().getItemInOffHand()}, relicId)
            || containsRelic(player.getEnderChest().getContents(), relicId);
    }

    private boolean containsRelic(ItemStack[] contents, String relicId) {
        if (contents == null) {
            return false;
        }
        for (ItemStack item : contents) {
            if (relicId.equals(relicId(item))) {
                return true;
            }
        }
        return false;
    }

    private NamespacedKey discoveryKey(String relicId) {
        String safe = relicId == null ? "unknown" : relicId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        return new NamespacedKey(plugin, "discovered_" + safe);
    }

    public String rarityLabelFor(String input) {
        RelicDefinition definition = definition(input);
        return definition == null ? null : definition.rarity().label();
    }

    public boolean isMaterialRelicId(String input) {
        RelicDefinition definition = definition(input);
        return definition != null && (definition.kind() == RelicKind.MATERIAL || definition.kind() == RelicKind.CATALYST);
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
            "<gradient:#7c3aed:#22d3ee><bold>Armory of the Veil</bold></gradient>",
            List.of(
                "<gray>Season 5 weapons, armor, materials,</gray>",
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
        return createItemForPlayer(definition, player);
    }

    public boolean handlesReliquaryEntry(String recipeId) {
        return isArmoryMenuId(recipeId) || isRelicId(recipeId);
    }

    public void openReliquaryEntry(Player player, String recipeId) {
        openReliquaryEntry(player, recipeId, false);
    }

    public void openReliquaryEntry(Player player, String recipeId, boolean reliquaryReturnsToMainMenu) {
        setBackTarget(player, reliquaryReturnsToMainMenu ? ArmoryBackTarget.RELIQUARY_THEN_MAIN_MENU : ArmoryBackTarget.RELIQUARY);
        if (isArmoryMenuId(recipeId)) {
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
                MM.deserialize("<gradient:#7c3aed:#22d3ee><bold>Armory of the Veil</bold></gradient>"),
                "Armory of the Veil"
            )
        );
        decorate(inventory);
        inventory.setItem(4, createGuiItem(
            Material.NETHER_STAR,
            "<gradient:#7c3aed:#22d3ee><bold>Armory of the Veil</bold></gradient>",
            List.of(
                "<gray>Season 5: Season of the Veil.</gray>",
                "<gray>Boss trophies forge the weapons, armor,</gray>",
                "<gray>and utility relics for the next climb.</gray>"
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
                "<white>1. The Veilbound Marshal</white>",
                "<white>2. Cindervale Arbalest</white>",
                "<white>3. The Gloam Matriarch</white>",
                "<white>4. The Briarveil Regent</white>",
                "<white>5. Thalassa the Drowned Veil</white>",
                "<white>6. The Argent Confessor</white>",
                "<white>7. Asterion the Rift Oracle</white>",
                "<white>8. Morvessa the Runebloom Witch</white>",
                "<white>9. Noctyr the Veil Warden</white>",
                "<white>10. Corrupted Oathkeeper</white>",
                "<dark_gray>Sets rise: Gloam Court -> Depthveil Pact -> Cinder Confessor</dark_gray>",
                "<dark_gray>-> Riftveil Step -> Nocturne Guard -> Eclipse Mantle.</dark_gray>"
            )
        ));
        inventory.setItem(BACK_SLOT, backItem(backTarget == ArmoryBackTarget.MAIN_MENU ? "Return to /menu" : "Return to Reliquary"));
        inventory.setItem(CLOSE_SLOT, createGuiItem(Material.BARRIER, "<red>Close</red>", List.of("<gray>Close the Armory.</gray>")));
        player.openInventory(inventory);
    }

    private static boolean isArmoryMenuId(String recipeId) {
        return ARMORY_MENU_ID.equals(recipeId) || LEGACY_ARMORY_MENU_ID.equals(recipeId);
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
            setIds.sort(Comparator.comparingInt(SeasonRelicManager::armorSetTier));
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
                ItemStack icon = createItemForPlayer(definition, player);
                tagMenu(icon, "open_relic", definition.id());
                inventory.setItem(CONTENT_SLOTS[i], icon);
            }
        }
        inventory.setItem(BACK_SLOT, backItem("Return to Armory of the Veil"));
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
            ItemStack icon = createItemForPlayer(definition, player);
            tagMenu(icon, "open_relic", definition.id());
            inventory.setItem(setSlots[i], icon);
        }

        inventory.setItem(40, createGuiItem(
            Material.NETHER_STAR,
            "<gradient:#ff4d6d:#facc15><bold>Full Set Bonus</bold></gradient>",
            List.of(
                "<gray>" + setBonusPlain(setId) + "</gray>",
                "<green>Full set: +" + balancePercent(fullSetPlayerDamageBonus(setId)) + " damage to players.</green>",
                "<dark_gray>Wear all four pieces together to wake it.</dark_gray>"
            )
        ));
        inventory.setItem(BACK_SLOT, backItem("Back to Veil Sets"));
        player.openInventory(inventory);
    }

    public void openRelicDetails(Player player, String relicId) {
        RelicDefinition definition = relics.get(relicId);
        if (definition == null) {
            player.sendMessage(MessageUtil.error("That relic is not registered."));
            return;
        }
        RelicCategory backCategory = isListedInArmoryCategory(definition) ? definition.category() : null;
        Inventory inventory = Bukkit.createInventory(
            new SeasonMenuHolder(MenuView.RELIC, backCategory, definition.id()),
            54,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#ff4d6d:#facc15><bold>" + definition.name() + "</bold></gradient>"), definition.name())
        );
        decorate(inventory);
        inventory.setItem(4, createItemForPlayer(definition, player));
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
                Material.CRAFTING_TABLE,
                "<green><bold>Craft " + definition.name() + "</bold></green>",
                List.of(
                    "<gray>Consumes the listed materials from your inventory.</gray>",
                    "<dark_gray>Armor/offhand slots are never consumed.</dark_gray>"
                )
            ));
        }
        inventory.setItem(BACK_SLOT, backItem(backCategory == null ? "Back to Armory of the Veil" : "Back to " + backCategory.plainTitle()));
        player.openInventory(inventory);
    }

    public List<ItemStack> createBossDrops(String bossId) {
        String normalized = bossId == null ? "" : bossId.toLowerCase(Locale.ROOT);
        List<ItemStack> drops = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        switch (normalized) {
            case "yule_the_minion" -> {
                drops.add(stacked("gilded_skull", 2));
                drops.add(stacked("oathbound_plate", 1));
            }
            case "kael_the_ashen" -> {
                drops.add(stacked("solar_ember", 4));
                if (random.nextDouble() < 0.30) drops.add(stacked("titan_gear", 1));
            }
            case "vesper_the_widow_queen" -> {
                drops.add(stacked("widow_silk", 4));
                if (random.nextDouble() < 0.35) drops.add(stacked("verdant_heart", 1));
            }
            case "voralith_the_crimson_warden" -> {
                drops.add(stacked("crimson_rib", 4));
                drops.add(stacked("sculk_heart", 1));
            }
            case "aurelion_the_rift_seraph" -> {
                drops.add(stacked("rift_lens", 4));
                if (random.nextDouble() < 0.35) drops.add(stacked("void_halo", 1));
                if (plugin.getConfigManager().awakeningTableEnabled
                    && random.nextDouble() < plugin.getConfigManager().awakeningTableRiftSeraphShardDropChance) {
                    drops.add(stacked("awakening_shard", 1));
                }
            }
            case "morvessa_the_runebloom_witch" -> {
                drops.add(stacked("rift_lens", 2));
                if (random.nextDouble() < BossBalance.RUNEBLOOM_WITCH_ORB_DROP_CHANCE) {
                    drops.add(stacked(VeilOrbManager.ENCHANT_ORB_ID, 1));
                }
            }
            case "nereida_the_abyss_mother" -> {
                drops.add(stacked("abyssal_pearl", 4));
                if (random.nextDouble() < 0.40) drops.add(stacked("tideheart", 1));
            }
            case "iron_saint" -> {
                drops.add(stacked("titan_gear", 4));
                if (random.nextDouble() < 0.40) drops.add(stacked("saint_alloy", 1));
            }
            case "mirewood_the_root_tyrant" -> {
                drops.add(stacked("living_bark", 4));
                if (random.nextDouble() < 0.40) drops.add(stacked("verdant_heart", 1));
            }
            case "corrupted_oathkeeper" -> {
                int essenceAmount = 2 + (random.nextDouble() < 0.25 ? 1 : 0);
                drops.add(stacked("corrupted_essence", essenceAmount));
                if (random.nextDouble() < 0.005) drops.add(stacked(SOUL_IMPRINT_ID, 1));
            }
            default -> {
            }
        }
        return drops;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRemovedOathglassClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int removed = removeLegacyOathglassStack(event.getCursor());
        if (removed > 0) {
            player.setItemOnCursor(null);
        }

        int currentRemoved = removeLegacyOathglassStack(event.getCurrentItem());
        if (currentRemoved > 0) {
            event.setCurrentItem(null);
            removed += currentRemoved;
        }

        boolean hotbarIsClickedSlot = event.getClickedInventory() == player.getInventory()
            && event.getSlot() == event.getHotbarButton();
        if (event.getHotbarButton() >= 0 && !hotbarIsClickedSlot) {
            ItemStack hotbar = player.getInventory().getItem(event.getHotbarButton());
            int hotbarRemoved = removeLegacyOathglassStack(hotbar);
            if (hotbarRemoved > 0) {
                player.getInventory().setItem(event.getHotbarButton(), null);
                removed += hotbarRemoved;
            }
        }

        if (removed <= 0) {
            return;
        }

        event.setCancelled(true);
        refundRemovedOathglass(player, removed);
        player.updateInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRemovedOathglassDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int removed = removeLegacyOathglassStack(event.getOldCursor());
        if (removed <= 0) {
            return;
        }

        event.setCancelled(true);
        player.setItemOnCursor(null);
        refundRemovedOathglass(player, removed);
        player.updateInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSoulImprintClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isSoulImprint(event.getCursor())) {
            return;
        }

        discoverSoulImprint(player);
        event.setCancelled(true);
        ItemStack imprint = event.getCursor().clone();
        if (event.getView().getTopInventory().getType() != InventoryType.CRAFTING
            || event.getClickedInventory() != player.getInventory()) {
            player.sendMessage(MessageUtil.warn("Use Soul Imprint from your inventory."));
            restoreSoulImprintCursor(player, imprint);
            return;
        }
        if (!isNormalSoulImprintClick(event.getClick())) {
            soulImprintConfirmations.remove(player.getUniqueId());
            player.sendMessage(MessageUtil.warn("Click one gear item three times with the Imprint."));
            restoreSoulImprintCursor(player, imprint);
            return;
        }

        ItemStack target = event.getCurrentItem();
        int targetSlot = event.getSlot();
        String validationMessage = validateMirrorTarget(target);
        if (validationMessage != null) {
            soulImprintConfirmations.remove(player.getUniqueId());
            player.sendMessage(MessageUtil.warn(validationMessage));
            restoreSoulImprintCursor(player, imprint);
            return;
        }

        SoulImprintConfirmation confirmation = nextSoulImprintConfirmation(player, targetSlot, target);
        if (confirmation.clicks() < SOUL_IMPRINT_REQUIRED_CLICKS) {
            int remaining = SOUL_IMPRINT_REQUIRED_CLICKS - confirmation.clicks();
            player.sendMessage(MessageUtil.warn("Click the same item " + remaining + " more " + (remaining == 1 ? "time" : "times") + ". The Imprint is safe."));
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.55f, 1.35f + confirmation.clicks() * 0.15f);
            restoreSoulImprintCursor(player, imprint);
            return;
        }

        MirrorResult result = mirrorItem(imprint, target);
        if (!result.success()) {
            soulImprintConfirmations.remove(player.getUniqueId());
            player.sendMessage(MessageUtil.warn(result.message()));
            restoreSoulImprintCursor(player, imprint);
            return;
        }

        int emptySlot = player.getInventory().firstEmpty();
        if (emptySlot < 0) {
            player.sendMessage(MessageUtil.warn("Make one empty inventory slot first."));
            restoreSoulImprintCursor(player, imprint);
            return;
        }

        soulImprintConfirmations.remove(player.getUniqueId());
        player.getInventory().setItem(emptySlot, result.copy());
        player.setItemOnCursor(result.remainingCursor());

        Location center = player.getLocation().add(0.0, 1.0, 0.0);
        player.getWorld().spawnParticle(Particle.END_ROD, center, 32, 0.45, 0.50, 0.45, 0.035);
        player.getWorld().spawnParticle(Particle.PORTAL, center, 42, 0.55, 0.60, 0.55, 0.08);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.35f);
        player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.8f, 1.1f);
        player.sendMessage(MessageUtil.success("Soul Imprint copied <white>" + escapeMiniMessage(readableItemName(target)) + "</white>."));
        Bukkit.broadcast(MessageUtil.prefixedRaw(
            "<gradient:#a78bfa:#22d3ee><bold>Soul Imprint:</bold></gradient> <white>"
                + escapeMiniMessage(player.getName())
                + "</white> <gray>copied</gray> <white>"
                + escapeMiniMessage(readableItemName(target))
                + "</white><gray>.</gray>"
        ));
        player.updateInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSoulImprintDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isSoulImprint(event.getOldCursor())) {
            return;
        }

        ItemStack imprint = event.getOldCursor().clone();
        event.setCancelled(true);
        soulImprintConfirmations.remove(player.getUniqueId());
        player.sendMessage(MessageUtil.warn("Click one gear item three times to use Soul Imprint."));
        restoreSoulImprintCursor(player, imprint);
    }

    @EventHandler
    public void onRelicPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        refundRemovedOathglassCompasses(player);
        int refreshed = refreshSeasonRelicStats(player.getInventory());
        refreshed += refreshSeasonRelicStats(player.getEnderChest());
        if (refreshed > 0) {
            player.updateInventory();
        }
        Bukkit.getScheduler().runTask(plugin, () -> detectSoulImprintHeld(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRelicHeldSlotChange(PlayerItemHeldEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> detectSoulImprintHeld(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRelicSwapHands(PlayerSwapHandItemsEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> detectSoulImprintHeld(event.getPlayer()));
    }

    private int refreshSeasonRelicStats(Inventory inventory) {
        int refreshed = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!refreshSeasonRelicStats(item)) {
                continue;
            }
            inventory.setItem(slot, item);
            refreshed++;
        }
        return refreshed;
    }

    private boolean refreshSeasonRelicStats(ItemStack item) {
        String id = relicId(item);
        RelicDefinition definition = relics.get(id);
        if (definition == null || (definition.kind() != RelicKind.WEAPON && definition.kind() != RelicKind.ARMOR)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        List<Component> preservedModifiers = CustomLoreUtil.managedModifierLore(meta.lore());

        String keyPrefix = "season_" + definition.id() + "_";
        for (Attribute attribute : Registry.ATTRIBUTE) {
            Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(attribute);
            if (modifiers == null || modifiers.isEmpty()) {
                continue;
            }
            for (AttributeModifier modifier : List.copyOf(modifiers)) {
                NamespacedKey key = modifier.getKey();
                if (plugin.getName().equalsIgnoreCase(key.getNamespace()) && key.getKey().startsWith(keyPrefix)) {
                    meta.removeAttributeModifier(attribute, modifier);
                }
            }
        }
        for (AttributeBonus bonus : definition.attributes()) {
            NamespacedKey key = new NamespacedKey(plugin, "season_" + definition.id() + "_" + bonus.attribute().getKey().getKey());
            meta.addAttributeModifier(
                bonus.attribute(),
                new AttributeModifier(key, bonus.amount(), bonus.operation(), bonus.slot())
            );
        }
        if (definition.maxDamage() > 0 && meta instanceof Damageable damageable) {
            damageable.setMaxDamage(definition.maxDamage());
            damageable.setDamage(Math.max(0, Math.min(damageable.getDamage(), definition.maxDamage() - 1)));
            meta = damageable;
        }
        if (definition.armorSetId() != null) {
            int currentUnbreaking = meta.getEnchantLevel(Enchantment.UNBREAKING);
            if (currentUnbreaking < 4) meta.addEnchant(Enchantment.UNBREAKING, 4, true);
        }
        List<Component> refreshedLore = new ArrayList<>(CustomLoreUtil.buildStyledLore(
            meta,
            definition.material(),
            definition.rarity().label(),
            definition.kind().label(),
            definition.topLore(),
            definition.sections()
        ));
        refreshedLore.addAll(preservedModifiers);
        meta.lore(CustomLoreUtil.normalizeLore(refreshedLore));
        CustomLoreUtil.applyStyledItemFlags(meta);
        item.setItemMeta(meta);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof SeasonMenuHolder holder)) {
            return;
        }
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
        ItemStack clicked = event.getCurrentItem();
        String action = readMenu(clicked, keyMenuAction);
        String value = readMenu(clicked, keyMenuValue);
        Bukkit.getScheduler().runTask(plugin, () -> handleSeasonMenuClick(player, holder, rawSlot, action, value));
    }

    private void handleSeasonMenuClick(Player player, SeasonMenuHolder holder, int rawSlot, String action, String value) {
        if (!player.isOnline()) {
            return;
        }
        if (rawSlot == CLOSE_SLOT && holder.view() == MenuView.HUB) {
            player.closeInventory();
            return;
        }
        if (rawSlot == BACK_SLOT) {
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
        if (holder.view() == MenuView.RELIC && rawSlot == CRAFT_SLOT) {
            craftRelic(player, holder.relicId());
            return;
        }

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
        if (!(event.getView().getTopInventory().getHolder(false) instanceof SeasonMenuHolder)) {
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRelicInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        ItemStack usedItem = event.getItem() != null ? event.getItem() : itemInHand(player, hand);
        if (isRemovedOathglassCompass(usedItem)) {
            event.setCancelled(true);
            refundRemovedOathglassCompasses(player);
            return;
        }

        String id = relicId(usedItem);
        if (id == null) {
            return;
        }
        RelicDefinition definition = relics.get(id);
        if (definition == null) {
            return;
        }
        if ("saints_ledger".equals(id) && usedItem != null) {
            ItemMeta ledgerMeta = usedItem.getItemMeta();
            if (ledgerMeta != null) {
                int uses = ledgerMeta.getPersistentDataContainer().getOrDefault(
                    keyConfessorLedgerUses,
                    PersistentDataType.INTEGER,
                    CONFESSOR_LEDGER_MAX_USES
                );
                applyLedgerUsesLore(ledgerMeta, uses);
                usedItem.setItemMeta(ledgerMeta);
            }
        }
        if (SOUL_IMPRINT_ID.equals(id)) {
            event.setCancelled(true);
            player.sendActionBar(MM.deserialize("<gray>Pick it up and click an uncorrupted item in your inventory.</gray>"));
            return;
        }
        if (definition.kind() == RelicKind.MATERIAL) {
            if (action == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                player.sendActionBar(MM.deserialize("<gray>Boss trophies are crafting materials, not placeable blocks.</gray>"));
            }
            return;
        }
        if (hand != EquipmentSlot.HAND
            && definition.activeAbility() != ActiveAbility.SAINT_WHETSTONE
            && definition.activeAbility() != ActiveAbility.SAINT_LEDGER) {
            return;
        }
        if (definition.activeAbility() == ActiveAbility.NONE) {
            return;
        }
        if (!requiresUseConfirmation(definition.activeAbility())
            && BedrockCompat.isBedrockPlayer(player)
            && !markBedrockRelicActivation(player)) {
            return;
        }
        event.setCancelled(true);
        requestUtilityActivation(player, definition, player.isSneaking(), hand);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRelicEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        ItemStack usedItem = itemInHand(player, hand);
        RelicDefinition definition = relics.get(relicId(usedItem));
        if (definition == null || definition.kind() == RelicKind.MATERIAL || definition.activeAbility() == ActiveAbility.NONE) {
            return;
        }
        if (hand != EquipmentSlot.HAND
            && definition.activeAbility() != ActiveAbility.SAINT_WHETSTONE
            && definition.activeAbility() != ActiveAbility.SAINT_LEDGER) {
            return;
        }
        if (!requiresUseConfirmation(definition.activeAbility())
            && BedrockCompat.isBedrockPlayer(player)
            && !markBedrockRelicActivation(player)) {
            return;
        }
        event.setCancelled(true);
        requestUtilityActivation(player, definition, player.isSneaking(), hand);
    }

    private ItemStack itemInHand(Player player, EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
    }

    public boolean activateHeldCrossplayAbility(Player player, boolean alternate) {
        if (player == null) {
            return false;
        }
        RelicDefinition definition = relics.get(relicId(player.getInventory().getItemInMainHand()));
        if (definition == null || definition.kind() == RelicKind.MATERIAL || definition.activeAbility() == ActiveAbility.NONE) {
            return false;
        }
        if (BedrockCompat.isBedrockPlayer(player) && !markBedrockRelicActivation(player)) {
            return true;
        }
        requestUtilityActivation(player, definition, alternate, EquipmentSlot.HAND);
        return true;
    }

    public boolean supportsCrossplayAbility(ItemStack item) {
        RelicDefinition definition = relics.get(relicId(item));
        return definition != null
            && definition.kind() != RelicKind.MATERIAL
            && definition.activeAbility() != ActiveAbility.NONE;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBedrockRelicSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        if (!BedrockCompat.isBedrockPlayer(player)) {
            return;
        }
        RelicDefinition definition = relics.get(relicId(player.getInventory().getItemInMainHand()));
        if (definition == null || definition.kind() == RelicKind.MATERIAL || definition.activeAbility() == ActiveAbility.NONE) {
            return;
        }
        if (!markBedrockRelicActivation(player)) {
            return;
        }
        requestUtilityActivation(player, definition, player.isSneaking(), EquipmentSlot.HAND);
    }

    private void requestUtilityActivation(Player player, RelicDefinition definition, boolean alternate, EquipmentSlot hand) {
        if (definition.activeAbility() == ActiveAbility.SAINT_LEDGER && !isDamagedItem(oppositeHandItem(player, hand))) {
            player.sendMessage(MessageUtil.warn("Put one damaged item in your other hand first."));
            return;
        }
        if (requiresUseConfirmation(definition.activeAbility())) {
            openRelicUseConfirmation(player, definition, alternate, hand);
            return;
        }
        activateUtility(player, definition, alternate, hand);
    }

    private boolean requiresUseConfirmation(ActiveAbility ability) {
        return ability == ActiveAbility.SAINT_LEDGER || ability == ActiveAbility.SAINT_WHETSTONE;
    }

    private void openRelicUseConfirmation(Player player, RelicDefinition definition, boolean alternate, EquipmentSlot hand) {
        Inventory inventory = Bukkit.createInventory(
            new RelicUseConfirmationHolder(player.getUniqueId(), definition.id(), alternate, hand),
            27,
            BedrockCompat.menuTitle(player, MM.deserialize("<gold><bold>Confirm " + definition.name() + "</bold></gold>"), "Confirm Use")
        );
        ItemStack filler = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, MenuItemUtil.INACTIVE_SLOT_LORE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(11, createGuiItem(Material.RED_STAINED_GLASS_PANE, "<red><bold>Cancel</bold></red>", List.of("<gray>Do not use the item.</gray>")));
        ItemStack held = itemInHand(player, hand);
        inventory.setItem(13, definition.id().equals(relicId(held)) ? held.clone() : createItemForPlayer(definition, player));
        inventory.setItem(15, createGuiItem(Material.LIME_STAINED_GLASS_PANE, "<green><bold>Confirm Use</bold></green>", List.of(
            definition.activeAbility() == ActiveAbility.SAINT_LEDGER
                ? "<gray>Spend one repair charge.</gray>"
                : "<gray>Consume the repair stone.</gray>",
            "<dark_gray>One menu click performs one use.</dark_gray>"
        )));
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRelicUseConfirmationClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof RelicUseConfirmationHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.playerId().equals(player.getUniqueId())) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot == 11) {
            player.closeInventory();
            return;
        }
        if (rawSlot != 15 || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) {
            return;
        }
        ItemStack held = holder.hand() == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
        RelicDefinition definition = relics.get(holder.relicId());
        if (definition == null || !holder.relicId().equals(relicId(held)) || !requiresUseConfirmation(definition.activeAbility())) {
            player.closeInventory();
            player.sendMessage(MessageUtil.warn("Keep the same item in the same hand until you confirm."));
            return;
        }
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> activateUtility(player, definition, holder.alternate(), holder.hand()));
    }

    @EventHandler
    public void onRelicPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        bedrockRelicActivationDebounces.remove(playerId);
        soulImprintConfirmations.remove(playerId);
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRelicTridentLaunch(PlayerLaunchProjectileEvent event) {
        if (!(event.getProjectile() instanceof Trident trident)) {
            return;
        }

        String id = relicId(event.getItemStack());
        RelicDefinition definition = relics.get(id);
        if (definition == null || definition.kind() != RelicKind.WEAPON || definition.material() != Material.TRIDENT) {
            return;
        }
        trident.getPersistentDataContainer().set(keyProjectileRelic, PersistentDataType.STRING, id);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRelicDamage(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        String id = projectileRelicId(event.getDamager());
        if (id == null && shouldResolveHeldRelic(event.getDamager() instanceof Player, AbilityDamageContext.isActive(attacker))) {
            id = relicId(attacker.getInventory().getItemInMainHand());
        }
        if (id == null) {
            return;
        }
        RelicDefinition definition = relics.get(id);
        if (definition == null || definition.kind() != RelicKind.WEAPON) {
            return;
        }
        applyWeaponEffect(attacker, target, definition, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSeasonalArmorBossDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        LivingEntity source = bossEncounterSource(event.getDamager());
        if (source == null || !isBossEncounterEntity(source)) {
            return;
        }

        double reduction = seasonalBossDamageReduction(player);
        if (reduction > 0.0) {
            event.setDamage(Math.max(0.0, event.getDamage() * (1.0 - reduction)));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFullSetPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.equals(target)) {
            return;
        }
        double bonus = fullSetPlayerDamageBonus(fullArmorSet(attacker));
        if (bonus > 0.0) {
            event.setDamage(event.getDamage() * (1.0 + bonus));
        }
    }

    private void tickPassives() {
        Set<UUID> activeTrueSightTargets = new LinkedHashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            detectSoulImprintHeld(player);
            applySetBonus(player, fullArmorSet(player));
            applyStandaloneArmorPassives(player);
            revealHiddenThreats(player, activeTrueSightTargets);
        }
        clearTrueSightGlow(activeTrueSightTargets);
    }

    private void applySetBonus(Player player, String setId) {
        if (setId == null) {
            return;
        }
        switch (setId) {
            case "crimson_guard" -> {
                applyPassivePotion(player, PotionEffectType.RESISTANCE, 0);
                if (player.getHealth() <= Math.max(6.0, maxHealth(player) * 0.35)) {
                    applyPassivePotion(player, PotionEffectType.STRENGTH, 1);
                    applyPassivePotion(player, PotionEffectType.REGENERATION, 0);
                }
            }
            case "widow_court" -> {
                applyPassivePotion(player, PotionEffectType.SPEED, 1);
                applyPassivePotion(player, PotionEffectType.JUMP_BOOST, 0);
                player.removePotionEffect(PotionEffectType.POISON);
            }
            case "ashen_saint" -> {
                applyPassivePotion(player, PotionEffectType.FIRE_RESISTANCE, 0);
                applyPassivePotion(player, PotionEffectType.HASTE, 1);
                applyPassivePotion(player, PotionEffectType.STRENGTH, 0);
            }
            case "tidebound" -> {
                applyPassivePotion(player, PotionEffectType.WATER_BREATHING, 0);
                if (isWet(player)) {
                    applyPassivePotion(player, PotionEffectType.DOLPHINS_GRACE, 0);
                    applyPassivePotion(player, PotionEffectType.SPEED, 1);
                    applyPassivePotion(player, PotionEffectType.REGENERATION, 0);
                }
            }
            case "riftwalker" -> {
                applyPassivePotion(player, PotionEffectType.SLOW_FALLING, 0);
                applyPassivePotion(player, PotionEffectType.SPEED, 1);
                applyPassivePotion(player, PotionEffectType.RESISTANCE, 0);
            }
            case "eclipse_mantle" -> {
                applyPassivePotion(player, PotionEffectType.NIGHT_VISION, PASSIVE_NIGHT_VISION_TICKS, 0);
                applyPassivePotion(player, PotionEffectType.RESISTANCE, 0);
                if (isInVeilDarkness(player)) {
                    applyPassivePotion(player, PotionEffectType.STRENGTH, 0);
                    applyPassivePotion(player, PotionEffectType.SPEED, 1);
                }
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
            case "crown_of_cinders" -> applyPassivePotion(player, PotionEffectType.FIRE_RESISTANCE, 0);
            case "graveveil_hood" -> applyPassivePotion(player, PotionEffectType.NIGHT_VISION, PASSIVE_NIGHT_VISION_TICKS, 0);
            case "bastion_pauldrons" -> applyPassivePotion(player, PotionEffectType.ABSORPTION, 0);
            case "sculkplate_harness" -> {
                player.removePotionEffect(PotionEffectType.DARKNESS);
                applyPassivePotion(player, PotionEffectType.RESISTANCE, 0);
            }
            case "stormcall_greaves" -> {
                applyPassivePotion(player, PotionEffectType.SPEED, 0);
                if (player.getWorld().hasStorm()) {
                    applyPassivePotion(player, PotionEffectType.HASTE, 0);
                }
            }
            case "rootwarden_greaves" -> {
                if (player.getWorld().getEnvironment() == World.Environment.NORMAL) {
                    applyPassivePotion(player, PotionEffectType.REGENERATION, 0);
                }
            }
            case "siren_treads" -> {
                applyPassivePotion(player, PotionEffectType.WATER_BREATHING, 0);
                if (isWet(player)) {
                    applyPassivePotion(player, PotionEffectType.DOLPHINS_GRACE, 0);
                }
            }
            case "voidstep_boots" -> applyPassivePotion(player, PotionEffectType.SLOW_FALLING, 0);
            case "glasswalker_boots" -> {
                Material below = player.getLocation().getBlock().getRelative(BlockFace.DOWN).getType();
                if (below == Material.ICE || below == Material.PACKED_ICE || below == Material.BLUE_ICE || below == Material.FROSTED_ICE) {
                    applyPassivePotion(player, PotionEffectType.SPEED, 1);
                }
            }
            case "oathkeeper_helm" -> {
                if (player.getHealth() <= Math.max(6.0, maxHealth(player) * 0.45)) {
                    applyPassivePotion(player, PotionEffectType.ABSORPTION, 1);
                    applyPassivePotion(player, PotionEffectType.RESISTANCE, 0);
                }
            }
            case "revelator_helm" -> {
                player.getWorld().spawnParticle(Particle.ENCHANT, player.getEyeLocation(), 2, 0.12, 0.08, 0.12, 0.02);
            }
            case "moonveil_mask" -> {
                applyPassivePotion(player, PotionEffectType.NIGHT_VISION, PASSIVE_NIGHT_VISION_TICKS, 0);
                player.removePotionEffect(PotionEffectType.BLINDNESS);
            }
            case "gloamstep_cloak" -> {
                if (player.isSneaking() && isInVeilDarkness(player)) {
                    applyPassivePotion(player, PotionEffectType.INVISIBILITY, 0);
                    applyPassivePotion(player, PotionEffectType.SPEED, 0);
                }
            }
            default -> {
            }
        }
    }

    private void revealHiddenThreats(Player viewer, Set<UUID> activeTargets) {
        if (!"revelator_helm".equals(relicId(viewer.getInventory().getHelmet()))) {
            return;
        }
        if (viewer.isDead() || viewer.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        double radiusSquared = TRUE_SIGHT_RADIUS * TRUE_SIGHT_RADIUS;
        for (Entity nearby : viewer.getNearbyEntities(TRUE_SIGHT_RADIUS, TRUE_SIGHT_RADIUS, TRUE_SIGHT_RADIUS)) {
            if (!(nearby instanceof LivingEntity target) || target instanceof Player || target.isDead()) {
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

    private boolean isTrueSightTarget(LivingEntity target) {
        return target.isInvisible() || target.hasPotionEffect(PotionEffectType.INVISIBILITY);
    }

    private void clearTrueSightGlow(Set<UUID> activeTargets) {
        trueSightGlowingTargets.removeIf(uuid -> {
            if (activeTargets.contains(uuid)) {
                return false;
            }
            Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof LivingEntity living && living.isGlowing()) {
                living.setGlowing(false);
            }
            return true;
        });
    }

    private boolean markBedrockRelicActivation(Player player) {
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        long blockedUntil = bedrockRelicActivationDebounces.getOrDefault(playerId, 0L);
        if (blockedUntil > now) {
            return false;
        }
        bedrockRelicActivationDebounces.put(playerId, now + BEDROCK_RELIC_ACTIVATION_DEBOUNCE_MS);
        return true;
    }

    private void activateUtility(Player player, RelicDefinition definition, boolean sneaking, EquipmentSlot hand) {
        String heldId = relicId(itemInHand(player, hand));
        if (!canActivateHeldRelic(definition.id(), heldId)) {
            player.sendMessage(MessageUtil.warn("Keep the same item in the same hand until it activates."));
            return;
        }
        if (!cooldownReady(player, definition.id(), definition.activeCooldownSeconds())) {
            return;
        }
        boolean success = switch (definition.activeAbility()) {
            case TEAM_BANNER -> activateBloodboundBanner(player);
            case EMBER_VIAL -> {
                applyPotion(player, PotionEffectType.FIRE_RESISTANCE, 90 * 20, 0);
                player.setFireTicks(0);
                player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0.0, 1.0, 0.0), 24, 0.5, 0.35, 0.5, 0.02);
                player.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.8f, 1.25f);
                yield true;
            }
            case WIDOW_ANTIDOTE -> {
                cleanseHarmfulEffects(player);
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0.0, 1.0, 0.0), 18, 0.35, 0.35, 0.35, 0.02);
                yield true;
            }
            case SAINT_WHETSTONE -> activateWhetstone(player, hand);
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
            case WARPED_KEY -> activateWarpedKey(player);
            case NULL_BELL -> activateNullBell(player);
            case RIFTWARD_LENS -> activateRiftwardLens(player);
            case GRAVETIDE_PHIAL -> activateGravetidePhial(player);
            case OATHKEEPER_CORD -> activateOathkeeperCord(player);
            case SAINT_LEDGER -> activateSaintLedger(player, hand);
            case VEILFLARE_LANTERN -> activateVeilflareLantern(player);
            case ECLIPSE_SEAL -> activateEclipseSeal(player);
            case BRIAR_SNARE -> activateBriarSnare(player);
            case NONE -> false;
        };
        if (success) {
            setCooldown(player, definition.id(), definition.activeCooldownSeconds());
            if (definition.activeAbility() == ActiveAbility.SAINT_WHETSTONE) {
                consumeRelicInHand(player, hand, definition.id());
            }
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
        player.sendActionBar(MM.deserialize("<gold>Veilbound Oath empowered <white>" + affected + "</white> ally" + (affected == 1 ? "" : "ies") + ".</gold>"));
        return true;
    }

    private boolean activateWhetstone(Player player, EquipmentSlot whetstoneHand) {
        PlayerInventory inventory = player.getInventory();
        EquipmentSlot hand = whetstoneHand == null ? EquipmentSlot.HAND : whetstoneHand;

        ItemStack oppositeHand = hand == EquipmentSlot.OFF_HAND ? inventory.getItemInMainHand() : inventory.getItemInOffHand();
        if (repairWhetstoneTarget(oppositeHand, player)) {
            return true;
        }

        if (hand == EquipmentSlot.HAND) {
            ItemStack[] armor = inventory.getArmorContents();
            for (int i = 0; i < armor.length; i++) {
                if (repairWhetstoneTarget(armor[i], player)) {
                    inventory.setArmorContents(armor);
                    return true;
                }
            }

            int heldSlot = inventory.getHeldItemSlot();
            for (int slot = 0; slot < 36; slot++) {
                if (slot == heldSlot) {
                    continue;
                }
                ItemStack item = inventory.getItem(slot);
                if (repairWhetstoneTarget(item, player)) {
                    inventory.setItem(slot, item);
                    return true;
                }
            }
        }

        player.sendMessage(MessageUtil.warn("Hold a damaged tool, weapon, or armor piece with the whetstone."));
        return false;
    }

    private boolean repairWhetstoneTarget(ItemStack item, Player player) {
        if (item == null || item.getType().isAir() || !(item.getItemMeta() instanceof Damageable damageable) || damageable.getDamage() <= 0) {
            return false;
        }
        int maxDamage = damageable.hasMaxDamage() ? damageable.getMaxDamage() : item.getType().getMaxDurability();
        if (maxDamage <= 0) {
            player.sendMessage(MessageUtil.warn("That item cannot be repaired by the whetstone."));
            return false;
        }
        int repair = whetstoneRepairAmount(maxDamage);
        damageable.setDamage(Math.max(0, damageable.getDamage() - repair));
        item.setItemMeta(damageable);
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0.0, 1.0, 0.0), 18, 0.35, 0.30, 0.35, 0.02);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.35f);
        player.sendActionBar(MM.deserialize("<gold>Confessor's Whetstone repaired your gear and crumbled.</gold>"));
        return true;
    }

    private void consumeRelicInHand(Player player, EquipmentSlot hand, String expectedId) {
        PlayerInventory inventory = player.getInventory();
        EquipmentSlot targetHand = hand == EquipmentSlot.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        ItemStack stack = targetHand == EquipmentSlot.OFF_HAND ? inventory.getItemInOffHand() : inventory.getItemInMainHand();
        if (stack == null || stack.getType().isAir() || !expectedId.equals(relicId(stack))) {
            return;
        }
        if (stack.getAmount() <= 1) {
            if (targetHand == EquipmentSlot.OFF_HAND) {
                inventory.setItemInOffHand(null);
            } else {
                inventory.setItemInMainHand(null);
            }
            return;
        }
        stack.setAmount(stack.getAmount() - 1);
        if (targetHand == EquipmentSlot.OFF_HAND) {
            inventory.setItemInOffHand(stack);
        } else {
            inventory.setItemInMainHand(stack);
        }
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

    private boolean activateWarpedKey(Player player) {
        RayTraceResult trace = player.rayTraceBlocks(13.0, FluidCollisionMode.NEVER);
        Location target = trace == null || trace.getHitBlock() == null
            ? player.getLocation().add(player.getLocation().getDirection().normalize().multiply(12.0))
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
        player.sendActionBar(MM.deserialize("<aqua>Veilbell cleansed <white>" + affected + "</white> oathbound soul" + (affected == 1 ? "" : "s") + ".</aqua>"));
        return affected > 0;
    }

    private boolean activateRiftwardLens(Player player) {
        int revealed = 0;
        for (LivingEntity target : player.getWorld().getNearbyLivingEntities(player.getLocation(), 48.0)) {
            if (target.equals(player) || target instanceof Player) {
                continue;
            }
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 20, 0, false, true, true));
            target.getWorld().spawnParticle(Particle.ENCHANT, target.getEyeLocation(), 16, 0.25, 0.25, 0.25, 0.15);
            revealed++;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.45f);
        player.sendActionBar(MM.deserialize("<light_purple>Veilsight Lens marked <white>" + revealed + "</white> non-player threat" + (revealed == 1 ? "" : "s") + ".</light_purple>"));
        return true;
    }

    private boolean activateGravetidePhial(Player player) {
        int affected = 0;
        Location center = player.getLocation().add(0.0, 0.75, 0.0);
        player.getWorld().spawnParticle(Particle.SOUL, center, 80, 2.4, 0.5, 2.4, 0.05);
        player.getWorld().spawnParticle(Particle.SCULK_SOUL, center, 32, 1.6, 0.35, 1.6, 0.05);
        for (LivingEntity target : player.getWorld().getNearbyLivingEntities(player.getLocation(), 9.0)) {
            if (target.equals(player) || (target instanceof Player other && isAllyOrSelf(player, other))) {
                continue;
            }
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 12 * 20, 1, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 12 * 20, 0, false, true, true));
            AbilityDamageContext.damage(player, target, 2.0);
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

    private boolean activateSaintLedger(Player player, EquipmentSlot ledgerHand) {
        ItemStack ledger = itemInHand(player, ledgerHand);
        if (!hasRemainingLedgerUse(ledger)) {
            player.sendMessage(MessageUtil.warn("This Ledger has no repairs remaining."));
            return false;
        }
        ItemStack target = oppositeHandItem(player, ledgerHand);
        if (!isDamagedItem(target) || !fullyRepairItem(target)) {
            player.sendMessage(MessageUtil.warn("Keep one damaged item in your other hand until you confirm."));
            return false;
        }
        int remainingUses = consumeConfessorLedgerUse(player, ledgerHand);
        player.giveExp(20);
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0.0, 1.0, 0.0), 45, 0.55, 0.55, 0.55, 0.25);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0.0, 1.0, 0.0), 24, 0.4, 0.5, 0.4, 0.03);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.9f, 1.0f);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.75f, 1.4f);
        player.updateInventory();
        player.sendActionBar(MM.deserialize(remainingUses > 0
            ? "<gold>Fully repaired one item. Ledger repairs remaining: <white>" + remainingUses + "</white>.</gold>"
            : "<gold>The Ledger completed its fourth full repair and faded away.</gold>"));
        return true;
    }

    private boolean hasRemainingLedgerUse(ItemStack ledger) {
        if (!"saints_ledger".equals(relicId(ledger))) {
            return false;
        }
        ItemMeta meta = ledger.getItemMeta();
        int uses = meta == null
            ? CONFESSOR_LEDGER_MAX_USES
            : meta.getPersistentDataContainer().getOrDefault(
                keyConfessorLedgerUses,
                PersistentDataType.INTEGER,
                CONFESSOR_LEDGER_MAX_USES
            );
        return uses > 0;
    }

    private int consumeConfessorLedgerUse(Player player, EquipmentSlot hand) {
        PlayerInventory inventory = player.getInventory();
        EquipmentSlot ledgerHand = hand == EquipmentSlot.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        ItemStack ledger = ledgerHand == EquipmentSlot.OFF_HAND ? inventory.getItemInOffHand() : inventory.getItemInMainHand();
        if (!"saints_ledger".equals(relicId(ledger))) {
            return 0;
        }
        ItemMeta meta = ledger.getItemMeta();
        int currentUses = meta == null
            ? CONFESSOR_LEDGER_MAX_USES
            : meta.getPersistentDataContainer().getOrDefault(keyConfessorLedgerUses, PersistentDataType.INTEGER, CONFESSOR_LEDGER_MAX_USES);
        int remainingUses = remainingLedgerUses(currentUses);
        if (remainingUses <= 0) {
            if (ledgerHand == EquipmentSlot.OFF_HAND) {
                inventory.setItemInOffHand(null);
            } else {
                inventory.setItemInMainHand(null);
            }
            return 0;
        }
        meta.getPersistentDataContainer().set(keyConfessorLedgerUses, PersistentDataType.INTEGER, remainingUses);
        applyLedgerUsesLore(meta, remainingUses);
        ledger.setItemMeta(meta);
        if (ledgerHand == EquipmentSlot.OFF_HAND) {
            inventory.setItemInOffHand(ledger);
        } else {
            inventory.setItemInMainHand(ledger);
        }
        return remainingUses;
    }

    static int remainingLedgerUses(int currentUses) {
        return Math.max(0, Math.min(CONFESSOR_LEDGER_MAX_USES, currentUses) - 1);
    }

    static boolean canActivateHeldRelic(String expectedId, String heldId) {
        return expectedId != null && expectedId.equals(heldId);
    }

    static boolean shouldResolveHeldRelic(boolean directPlayerDamage, boolean abilityDamage) {
        return directPlayerDamage && !abilityDamage;
    }

    private void applyLedgerUsesLore(ItemMeta meta, int remainingUses) {
        List<Component> lore = new ArrayList<>(CustomLoreUtil.buildStyledLore(
            Material.BOOK,
            "LEGENDARY",
            "UTILITY",
            List.of(
                "<gray><gold>Use:</gold> Right-click, then confirm a full repair.</gray>",
                "<gray><gold>Target:</gold> One damaged item in your other hand.</gray>",
                "<gray><gold>Uses:</gold> 4 successful repairs <dark_gray>•</dark_gray> <green>+20 XP each</green></gray>"
            ),
            List.of()
        ));
        lore.add(MM.deserialize("<gold><bold>Repairs Remaining:</bold></gold> <white>" + remainingUses + "/" + CONFESSOR_LEDGER_MAX_USES + "</white>"));
        meta.lore(CustomLoreUtil.normalizeLore(lore));
    }

    private ItemStack oppositeHandItem(Player player, EquipmentSlot ledgerHand) {
        return ledgerHand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInMainHand()
            : player.getInventory().getItemInOffHand();
    }

    private boolean isDamagedItem(ItemStack item) {
        return item != null
            && !item.getType().isAir()
            && item.getItemMeta() instanceof Damageable damageable
            && damageable.getDamage() > 0;
    }

    private boolean fullyRepairItem(ItemStack item) {
        if (!isDamagedItem(item)) {
            return false;
        }
        Damageable damageable = (Damageable) item.getItemMeta();
        damageable.setDamage(0);
        item.setItemMeta(damageable);
        return true;
    }

    private boolean activateVeilflareLantern(Player player) {
        int allies = 0;
        for (Player target : player.getWorld().getPlayers()) {
            if (!isAllyOrSelf(player, target) || target.getLocation().distanceSquared(player.getLocation()) > 10.0 * 10.0) {
                continue;
            }
            target.removePotionEffect(PotionEffectType.DARKNESS);
            target.removePotionEffect(PotionEffectType.BLINDNESS);
            applyPotion(target, PotionEffectType.NIGHT_VISION, 30 * 20, 0);
            applyPotion(target, PotionEffectType.RESISTANCE, 12 * 20, 0);
            target.getWorld().spawnParticle(Particle.END_ROD, target.getEyeLocation(), 14, 0.25, 0.35, 0.25, 0.02);
            allies++;
        }

        int marked = 0;
        for (LivingEntity target : player.getWorld().getNearbyLivingEntities(player.getLocation(), 12.0)) {
            if (target.equals(player) || target instanceof Player || target.isDead()) {
                continue;
            }
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 12 * 20, 0, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 10 * 20, 0, false, true, true));
            target.getWorld().spawnParticle(Particle.ENCHANT, target.getEyeLocation(), 12, 0.22, 0.25, 0.22, 0.12);
            marked++;
        }

        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.9f, 1.35f);
        player.sendActionBar(MM.deserialize("<aqua>Veilflare marked <white>" + marked + "</white> threat" + (marked == 1 ? "" : "s") + " and steadied <white>" + allies + "</white> ally" + (allies == 1 ? "" : "ies") + ".</aqua>"));
        return marked > 0 || allies > 0;
    }

    private boolean activateEclipseSeal(Player player) {
        boolean dark = isInVeilDarkness(player);
        applyPotion(player, PotionEffectType.RESISTANCE, 25 * 20, dark ? 1 : 0);
        applyPotion(player, PotionEffectType.STRENGTH, 25 * 20, 0);
        applyPotion(player, PotionEffectType.SPEED, 25 * 20, dark ? 1 : 0);
        if (dark) {
            applyPotion(player, PotionEffectType.ABSORPTION, 25 * 20, 1);
        }
        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0.0, 1.0, 0.0), 44, 0.45, 0.55, 0.45, 0.12);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, dark ? 0.65f : 1.15f);
        player.sendActionBar(MM.deserialize(dark ? "<dark_purple>Eclipse Seal woke in the dark.</dark_purple>" : "<light_purple>Eclipse Seal answered.</light_purple>"));
        return true;
    }

    private boolean activateBriarSnare(Player player) {
        int affected = 0;
        Location center = player.getLocation().add(0.0, 0.25, 0.0);
        player.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, center, 70, 2.8, 0.45, 2.8, 0.04);
        for (LivingEntity target : player.getWorld().getNearbyLivingEntities(player.getLocation(), 8.0)) {
            if (target.equals(player) || (target instanceof Player other && isAllyOrSelf(player, other))) {
                continue;
            }
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10 * 20, 3, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 10 * 20, 1, false, true, true));
            target.getWorld().spawnParticle(Particle.BLOCK, target.getLocation().add(0.0, 0.2, 0.0), 18, 0.35, 0.1, 0.35, 0.0, Material.MANGROVE_ROOTS.createBlockData());
            Vector drag = player.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0.0);
            if (drag.lengthSquared() > 1.0E-6) {
                target.setVelocity(target.getVelocity().add(drag.normalize().multiply(0.18)));
            }
            affected++;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ROOTED_DIRT_BREAK, 1.0f, 0.6f);
        return affected > 0;
    }

    private void applyWeaponEffect(Player attacker, LivingEntity target, RelicDefinition definition, EntityDamageByEntityEvent event) {
        if (target.equals(attacker)) {
            return;
        }
        if (isBossEncounterEntity(target)) {
            event.setDamage(event.getDamage() * bossDamageMultiplier(definition.rarity()));
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
                        AbilityDamageContext.damage(attacker, nearby, 3.0);
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
            case VEILPIERCER -> {
                boolean marked = target.hasPotionEffect(PotionEffectType.GLOWING) || target.isGlowing();
                event.setDamage(event.getDamage() + (marked ? 5.0 : 2.0));
                if (combatCooldownReady(attacker, definition.id(), 8)) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 8 * 20, 0, false, true, true));
                    target.getWorld().spawnParticle(Particle.CRIT, target.getEyeLocation(), 18, 0.18, 0.22, 0.18, 0.08);
                    setCooldown(attacker, definition.id(), 8);
                }
            }
            case HOLLOWSONG -> {
                if (combatCooldownReady(attacker, definition.id(), 9)) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 6 * 20, 1, false, true, true));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 6 * 20, 1, false, true, true));
                    target.getWorld().playSound(target.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.7f, 1.65f);
                    setCooldown(attacker, definition.id(), 9);
                }
                if (target.hasPotionEffect(PotionEffectType.WEAKNESS)) {
                    event.setDamage(event.getDamage() + 2.0);
                }
            }
            case STARFALL -> {
                if (combatCooldownReady(attacker, definition.id(), 12)) {
                    setCooldown(attacker, definition.id(), 12);
                    target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0.0, 1.0, 0.0), 34, 0.45, 0.55, 0.45, 0.04);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.55f, 1.55f);
                    for (LivingEntity nearby : target.getWorld().getNearbyLivingEntities(target.getLocation(), 4.5)) {
                        if (nearby.equals(attacker) || (nearby instanceof Player player && isAllyOrSelf(attacker, player))) {
                            continue;
                        }
                        AbilityDamageContext.damage(attacker, nearby, 4.0);
                        nearby.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 5 * 20, 1, false, true, true));
                    }
                }
            }
            case NONE -> {
            }
        }
        if (target instanceof Player) {
            event.setDamage(event.getDamage() * SEASON_WEAPON_PVP_MULTIPLIER);
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
        ItemStack reward = createRelicItem(definition.id());
        ItemStack[] nextStorage = cloneStorageContents(storage);
        for (RecipeIngredient ingredient : definition.recipe()) {
            removeIngredient(nextStorage, ingredient);
        }
        if (!canFitReward(nextStorage, reward)) {
            player.sendMessage(MessageUtil.warn("Clear enough inventory space before crafting <white>" + definition.name() + "</white>."));
            return 0;
        }
        inventory.setStorageContents(nextStorage);
        if (plugin.getItemAuditManager() != null) {
            plugin.getItemAuditManager().recordKnownAcquisition(player, reward, "season_craft", "Crafted from the Armory of the Veil.");
        }
        player.getInventory().addItem(reward);
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

    private ItemStack[] cloneStorageContents(ItemStack[] contents) {
        ItemStack[] clone = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            clone[i] = item == null || item.getType().isAir() ? null : item.clone();
        }
        return clone;
    }

    private boolean canFitReward(ItemStack[] storage, ItemStack reward) {
        if (reward == null || reward.getType().isAir()) {
            return false;
        }
        int remaining = reward.getAmount();
        int maxStack = Math.max(1, reward.getMaxStackSize());
        for (ItemStack item : storage) {
            if (remaining <= 0) {
                return true;
            }
            if (item == null || item.getType().isAir() || !item.isSimilar(reward)) {
                continue;
            }
            remaining -= Math.max(0, maxStack - item.getAmount());
        }
        for (ItemStack item : storage) {
            if (remaining <= 0) {
                return true;
            }
            if (item == null || item.getType().isAir()) {
                remaining -= maxStack;
            }
        }
        return remaining <= 0;
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

    private int refundRemovedOathglassCompasses(Player player) {
        if (player == null || !player.isOnline()) {
            return 0;
        }

        int removed = 0;
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            int stackAmount = removeLegacyOathglassStack(storage[i]);
            if (stackAmount <= 0) {
                continue;
            }
            storage[i] = null;
            removed += stackAmount;
        }
        inventory.setStorageContents(storage);

        int offhandAmount = removeLegacyOathglassStack(inventory.getItemInOffHand());
        if (offhandAmount > 0) {
            inventory.setItemInOffHand(null);
            removed += offhandAmount;
        }

        if (removed > 0) {
            refundRemovedOathglass(player, removed);
            player.updateInventory();
        }
        return removed;
    }

    private int removeLegacyOathglassStack(ItemStack item) {
        if (!isRemovedOathglassCompass(item)) {
            return 0;
        }
        return Math.max(1, item.getAmount());
    }

    private boolean isRemovedOathglassCompass(ItemStack item) {
        return REMOVED_OATHGLASS_COMPASS_ID.equals(rawRelicId(item));
    }

    private void refundRemovedOathglass(Player player, int amount) {
        if (amount <= 0) {
            return;
        }

        refundStack(player, createRelicItem("oathbound_plate"), amount * 3);
        refundStack(player, new ItemStack(Material.RECOVERY_COMPASS), amount);
        refundStack(player, new ItemStack(Material.AMETHYST_SHARD), amount * 16);
        player.sendMessage(MessageUtil.info("Oathglass Compass was retired, so its recipe materials were refunded."));
    }

    private void refundStack(Player player, ItemStack base, int amount) {
        if (player == null || base == null || base.getType().isAir() || amount <= 0) {
            return;
        }

        int maxStack = Math.max(1, base.getMaxStackSize());
        int remaining = amount;
        while (remaining > 0) {
            int giveAmount = Math.min(maxStack, remaining);
            ItemStack stack = base.clone();
            stack.setAmount(giveAmount);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            remaining -= giveAmount;
        }
    }

    private MirrorResult mirrorItem(ItemStack cursor, ItemStack target) {
        String validationMessage = validateMirrorTarget(target);
        if (validationMessage != null) {
            return MirrorResult.failure(validationMessage);
        }

        ItemStack copy = soulImprintedCopy(target);
        return MirrorResult.success(copy, consumeOneFromCursor(cursor));
    }

    private String validateMirrorTarget(ItemStack target) {
        if (target == null || target.getType().isAir()) {
            return "Click the item you want to copy.";
        }
        if (target.getAmount() != 1) {
            return "Split the target item to one first.";
        }
        if (isSoulImprint(target)) {
            return "Soul Imprint cannot copy itself.";
        }
        if (isSoulImprinted(target)) {
            return "Soul Imprinted items cannot be copied again.";
        }
        if (plugin.getCorruptionManager() != null && plugin.getCorruptionManager().isCorruptionLocked(target)) {
            return "Corrupted items cannot be mirrored.";
        }
        if (plugin.getLegendaryListener() != null) {
            String legendaryId = plugin.getLegendaryListener().legendaryId(target);
            if (legendaryId != null
                && plugin.getLegendaryListener().maxServerCopiesForLegendary(legendaryId) != Integer.MAX_VALUE) {
                return "Unique legendaries cannot be copied.";
            }
        }
        if (!isMirrorableEquipment(target.getType())) {
            return "Soul Imprint only copies armor, tools, and weapons.";
        }
        return null;
    }

    private SoulImprintConfirmation nextSoulImprintConfirmation(Player player, int targetSlot, ItemStack target) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        SoulImprintConfirmation current = soulImprintConfirmations.get(playerId);
        int nextClicks = 1;
        if (current != null
            && current.expiresAt() > now
            && current.slot() == targetSlot
            && target.isSimilar(current.snapshot())) {
            nextClicks = Math.min(SOUL_IMPRINT_REQUIRED_CLICKS, current.clicks() + 1);
        }

        SoulImprintConfirmation updated = new SoulImprintConfirmation(targetSlot, target.clone(), nextClicks, now + SOUL_IMPRINT_CONFIRM_MS);
        soulImprintConfirmations.put(playerId, updated);
        return updated;
    }

    private boolean isMirrorableEquipment(Material material) {
        return isArmor(material) || isToolOrWeapon(material);
    }

    private boolean isToolOrWeapon(Material material) {
        return Tag.ITEMS_SWORDS.isTagged(material)
            || Tag.ITEMS_AXES.isTagged(material)
            || Tag.ITEMS_PICKAXES.isTagged(material)
            || Tag.ITEMS_SHOVELS.isTagged(material)
            || Tag.ITEMS_HOES.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_WEAPON.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_TRIDENT.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_BOW.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_CROSSBOW.isTagged(material)
            || Tag.ITEMS_ENCHANTABLE_MACE.isTagged(material);
    }

    private boolean isArmor(Material material) {
        return Tag.ITEMS_HEAD_ARMOR.isTagged(material)
            || Tag.ITEMS_CHEST_ARMOR.isTagged(material)
            || Tag.ITEMS_LEG_ARMOR.isTagged(material)
            || Tag.ITEMS_FOOT_ARMOR.isTagged(material)
            || material == Material.ELYTRA;
    }

    private ItemStack consumeOneFromCursor(ItemStack cursor) {
        if (cursor == null || cursor.getType().isAir() || cursor.getAmount() <= 1) {
            return null;
        }
        ItemStack remaining = cursor.clone();
        remaining.setAmount(cursor.getAmount() - 1);
        return remaining;
    }

    private boolean isNormalSoulImprintClick(ClickType click) {
        return click == ClickType.LEFT || click == ClickType.RIGHT;
    }

    private void restoreSoulImprintCursor(Player player, ItemStack imprint) {
        player.setItemOnCursor(imprint == null || imprint.getType().isAir() ? null : imprint.clone());
        player.updateInventory();
    }

    private ItemStack soulImprintedCopy(ItemStack source) {
        ItemStack copy = source.clone();
        copy.setAmount(1);
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        if (lore.stream().map(PLAIN::serialize).noneMatch(line -> line.equalsIgnoreCase("Soul Imprint"))) {
            CustomLoreUtil.addSpacer(lore);
            lore.add(MM.deserialize("<gradient:#a78bfa:#f0abfc><bold>Soul Imprint</bold></gradient>")
                .decoration(TextDecoration.ITALIC, false));
        }
        meta.getPersistentDataContainer().set(keySoulImprinted, PersistentDataType.BYTE, (byte) 1);
        meta.lore(CustomLoreUtil.normalizeLore(lore));
        copy.setItemMeta(meta);
        return copy;
    }

    private String readableItemName(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "Unknown Item";
        }
        String relicName = displayNameFor(relicId(item));
        if (relicName != null && !relicName.isBlank()) {
            return relicName;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.displayName() != null) {
            String plain = PLAIN.serialize(meta.displayName()).trim();
            if (!plain.isBlank()) {
                return plain;
            }
        }
        return prettyName(item.getType().name());
    }

    private ItemStack createItemForPlayer(RelicDefinition definition, Player player) {
        ItemStack item = createItem(definition, true);
        if (hasHeldSoulImprint(player)) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        Component hiddenName = MM.deserialize("<obfuscated>Soul Imprint</obfuscated>");
        TextReplacementConfig replacement = TextReplacementConfig.builder()
            .matchLiteral("Soul Imprint")
            .replacement(hiddenName)
            .build();
        if (meta.displayName() != null) {
            meta.displayName(meta.displayName().replaceText(replacement));
        }
        if (meta.lore() != null) {
            meta.lore(meta.lore().stream().map(line -> line.replaceText(replacement)).toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createItem(RelicDefinition definition, boolean preview) {
        ItemStack item = new ItemStack(definition.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(CustomLoreUtil.displayName(definition.rarity(), definition.name()));
        ItemModelUtil.apply(meta, definition.id());
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            definition.material(),
            definition.rarity().label(),
            definition.kind().label(),
            definition.topLore(),
            definition.sections()
        ));
        meta.getPersistentDataContainer().set(keyRelicId, PersistentDataType.STRING, definition.id());
        if ("saints_ledger".equals(definition.id())) {
            meta.setMaxStackSize(1);
            meta.getPersistentDataContainer().set(keyConfessorLedgerUses, PersistentDataType.INTEGER, CONFESSOR_LEDGER_MAX_USES);
            applyLedgerUsesLore(meta, CONFESSOR_LEDGER_MAX_USES);
        }
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
            meta.addEnchant(Enchantment.UNBREAKING, definition.armorSetId() == null ? 1 : 4, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        if (preview) {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            CustomLoreUtil.addSpacer(lore);
            lore.add(MM.deserialize("<dark_gray>Click to view recipe and source.</dark_gray>"));
            meta.lore(lore);
        }
        if (meta.lore() != null) {
            meta.lore(CustomLoreUtil.normalizeLore(meta.lore()));
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
        ItemStack filler = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, MenuItemUtil.INACTIVE_SLOT_LORE);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (isFrameSlot(i, inventory.getSize())) {
                inventory.setItem(i, filler);
            }
        }
    }

    private boolean isFrameSlot(int slot, int size) {
        return slot < 9 || slot >= size - 9 || slot % 9 == 0 || slot % 9 == 8;
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
        lore.add("<gold>Boss Path Tier: <white>" + armorSetTier(setId) + "</white></gold>");
        lore.add("<gray>" + setBonusPlain(setId) + "</gray>");
        lore.add("<green>Full set: +" + balancePercent(fullSetPlayerDamageBonus(setId)) + " player damage.</green>");
        lore.add("<aqua>Full set Boss Ward: " + balancePercent((bossDamageReduction(rarity) * 4.0) + FULL_SET_BOSS_DAMAGE_REDUCTION) + ".</aqua>");
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
        List<String> visibleLore = MenuItemUtil.visibleMiniLore(name, loreLines);
        meta.displayName(MM.deserialize(MenuItemUtil.visibleMiniName(name)));
        if (!visibleLore.isEmpty()) {
            List<Component> lore = new ArrayList<>(visibleLore.size());
            for (String line : visibleLore) {
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
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String namespace = plugin.getName().toLowerCase(Locale.ROOT);
            if (meta.getPersistentDataContainer().getKeys().stream()
                .anyMatch(key -> namespace.equals(key.getNamespace()))) {
                return true;
            }
            if (meta.hasDisplayName() || meta.hasLore() || !meta.getEnchants().isEmpty()
                || meta.hasCustomModelDataComponent() || meta.hasItemModel()) {
                return true;
            }
            if (meta instanceof Damageable damageable && damageable.getDamage() > 0) {
                return true;
            }
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

    private void applyPassivePotion(Player player, PotionEffectType type, int amplifier) {
        applyPassivePotion(player, type, PASSIVE_EFFECT_TICKS, amplifier);
    }

    private void applyPassivePotion(Player player, PotionEffectType type, int durationTicks, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);
        if (current != null && current.getAmplifier() >= amplifier && current.getDuration() > durationTicks / 2) {
            return;
        }
        player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, true, false, true));
    }

    private boolean isAllyOrSelf(Player owner, Player target) {
        if (owner == null || target == null) {
            return false;
        }
        if (owner.equals(target)) {
            return true;
        }
        if (plugin.getDuelManager() != null && plugin.getDuelManager().areOpponents(owner.getUniqueId(), target.getUniqueId())) {
            return false;
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
        if (plugin.getBossManager() != null && plugin.getBossManager().blockHealingIfSuppressed(player, amount)) {
            return;
        }
        player.setHealth(Math.min(maxHealth(player), player.getHealth() + amount));
    }

    private double maxHealth(Player player) {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0 : attribute.getValue();
    }

    private boolean isWet(Entity entity) {
        return entity.isInWater() || entity.isInRain();
    }

    private boolean isInVeilDarkness(Player player) {
        long time = player.getWorld().getTime();
        boolean night = time >= 12500L && time <= 23500L;
        return night || player.getLocation().getBlock().getLightLevel() <= 7;
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

    private boolean isBossEncounterEntity(Entity entity) {
        return plugin.getBossManager() != null && plugin.getBossManager().isBossEncounterEntity(entity);
    }

    private LivingEntity bossEncounterSource(Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    private double seasonalBossDamageReduction(Player player) {
        double reduction = 0.0;
        for (ItemStack item : player.getInventory().getArmorContents()) {
            RelicDefinition definition = relics.get(relicId(item));
            if (definition != null && definition.kind() == RelicKind.ARMOR) {
                reduction += bossDamageReduction(definition.rarity());
            }
        }
        if (fullArmorSet(player) != null) {
            reduction += FULL_SET_BOSS_DAMAGE_REDUCTION;
        }
        return Math.min(MAX_BOSS_DAMAGE_REDUCTION, Math.max(0.0, reduction));
    }

    static double bossDamageMultiplier(CustomLoreUtil.Rarity rarity) {
        return switch (rarity) {
            case MYTHIC -> 1.40;
            case LEGENDARY -> 1.30;
            case EPIC -> 1.20;
            case RARE -> 1.12;
            default -> 1.08;
        };
    }

    static double bossDamageReduction(CustomLoreUtil.Rarity rarity) {
        return switch (rarity) {
            case MYTHIC -> 0.085;
            case LEGENDARY -> 0.070;
            case EPIC -> 0.055;
            case RARE -> 0.035;
            default -> 0.020;
        };
    }

    private static String balancePercent(double value) {
        double percent = Math.max(0.0, value) * 100.0;
        if (Math.abs(percent - Math.rint(percent)) < 0.001) {
            return Math.round(percent) + "%";
        }
        return String.format(Locale.US, "%.1f%%", percent);
    }

    private Location nearestSafeTeleport(Location target) {
        return LocationUtil.findNearestSafeStandingLocation(target, 2, 3);
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

    private static String escapeMiniMessage(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("<", "\\<");
    }

    private static Map<RelicCategory, List<RelicDefinition>> groupByCategory(Collection<RelicDefinition> definitions) {
        Map<RelicCategory, List<RelicDefinition>> out = new EnumMap<>(RelicCategory.class);
        for (RelicDefinition definition : definitions) {
            if (!isListedInArmoryCategory(definition)) {
                continue;
            }
            out.computeIfAbsent(definition.category(), ignored -> new ArrayList<>()).add(definition);
        }
        for (List<RelicDefinition> list : out.values()) {
            list.sort(Comparator
                .comparingInt(SeasonRelicManager::armoryTier)
                .thenComparing(RelicDefinition::name));
        }
        return out;
    }

    private static boolean isListedInArmoryCategory(RelicDefinition definition) {
        return definition != null && !SOUL_IMPRINT_ID.equals(definition.id());
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

        add(out, material("gilded_skull", Material.SKELETON_SKULL, "Veiled Skull", CustomLoreUtil.Rarity.RARE,
            "A trophy from the Marshal's broken command chain.", "The Veilbound Marshal"));
        add(out, material("oathbound_plate", Material.GOLD_INGOT, "Veilmarked Plate", CustomLoreUtil.Rarity.RARE,
            "Soft gold hammered around a name no one says twice.", "The Veilbound Marshal"));
        add(out, material("solar_ember", Material.BLAZE_POWDER, "Cinderveil Ember", CustomLoreUtil.Rarity.RARE,
            "A coal-bright fragment from Cindervale's last sunrise.", "Cindervale Arbalest"));
        add(out, material("widow_silk", Material.STRING, "Gloam Silk", CustomLoreUtil.Rarity.RARE,
            "Thread that tightens when it hears fear.", "The Gloam Matriarch"));
        add(out, material("crimson_rib", Material.BONE, "Nocturne Rib", CustomLoreUtil.Rarity.EPIC,
            "A red rib pulled from the Dominion's buried hymn.", "Noctyr the Veil Warden"));
        add(out, material("sculk_heart", Material.ECHO_SHARD, "Veil Heart", CustomLoreUtil.Rarity.EPIC,
            "It beats only when the holder lies.", "Noctyr the Veil Warden"));
        add(out, material("rift_lens", Material.ENDER_EYE, "Riftglass Lens", CustomLoreUtil.Rarity.EPIC,
            "A pupil that remembers impossible doors.", "Asterion the Rift Oracle"));
        add(out, material("void_halo", Material.NETHER_STAR, "Moonless Halo", CustomLoreUtil.Rarity.MYTHIC,
            "A crown for things that were never born.", "Asterion the Rift Oracle"));
        add(out, material("awakening_shard", Material.AMETHYST_SHARD, "Awakening Shard", CustomLoreUtil.Rarity.EPIC,
            "A bright shard used to wake potential inside worthy gear.", "Asterion the Rift Oracle"));
        add(out, material("abyssal_pearl", Material.PRISMARINE_CRYSTALS, "Depthveil Pearl", CustomLoreUtil.Rarity.EPIC,
            "An ocean-dark pearl with a pulse like thunder.", "Thalassa the Drowned Veil"));
        add(out, material("tideheart", Material.HEART_OF_THE_SEA, "Tideveil Heart", CustomLoreUtil.Rarity.MYTHIC,
            "The sea kept this heart until it learned your name.", "Thalassa the Drowned Veil"));
        add(out, material("titan_gear", Material.IRON_NUGGET, "Argent Gear", CustomLoreUtil.Rarity.EPIC,
            "A gear from a saint that forgot mercy.", "The Argent Confessor"));
        add(out, material("saint_alloy", Material.NETHERITE_SCRAP, "Confessor Alloy", CustomLoreUtil.Rarity.MYTHIC,
            "Metal that refuses to bend for kings.", "The Argent Confessor"));
        add(out, material("living_bark", Material.MANGROVE_ROOTS, "Briarwake Bark", CustomLoreUtil.Rarity.RARE,
            "Wood that still dreams of strangling the sun.", "The Briarveil Regent"));
        add(out, material("verdant_heart", Material.SPORE_BLOSSOM, "Briarheart", CustomLoreUtil.Rarity.EPIC,
            "A green heart, patient as a graveyard.", "The Briarveil Regent"));
        add(out, material("corrupted_essence", Material.RED_DYE, "Corrupted Essence", CustomLoreUtil.Rarity.MYTHIC,
            "A burning oath condensed into something that can stain steel.", "Corrupted Oathkeeper"));
        add(out, soulImprint());
        add(out, catalyst("corruption_stone", Material.AMETHYST_SHARD, "Corruption Stone", CustomLoreUtil.Rarity.EPIC,
            "A safer corruption catalyst for non-legendary, non-mythic gear.",
            List.of(relic("solar_ember", 3), relic("widow_silk", 3), relic("living_bark", 3), relic("abyssal_pearl", 2), mat(Material.AMETHYST_SHARD, 12))));
        add(out, orbRelic(VeilOrbManager.AGGRO_ORB_ID, Material.ECHO_SHARD, "Warden's Lure Orb", CustomLoreUtil.Rarity.EPIC,
            "An armor orb that makes bosses care about you.",
            List.of(
                CustomLoreUtil.section("Use", "+5 Boss Aggro", "<gray>Pick this up and click one armor piece in your inventory.</gray>"),
                CustomLoreUtil.section("Limit", "One Per Armor Piece", "<gray>It does not stack on the same item.</gray>")
            ),
            List.of(relic("titan_gear", 4), relic("sculk_heart", 1), mat(Material.ECHO_SHARD, 4), mat(Material.IRON_BLOCK, 2))));
        add(out, orbRelic(VeilOrbManager.STAT_ORB_ID, Material.ENDER_EYE, "Veilshift Orb", CustomLoreUtil.Rarity.EPIC,
            "Rerolls one bonus stat onto armor, weapons, or tools.",
            List.of(
                CustomLoreUtil.section("Use", "Random Gear Stat", "<gray>Click one gear item to roll one active stat line.</gray>"),
                CustomLoreUtil.section("Reroll", "Replaces Old Veilshift", "<gray>Using another orb changes the stat instead of stacking.</gray>")
            ),
            List.of(relic("rift_lens", 2), relic("living_bark", 2), mat(Material.AMETHYST_SHARD, 12), mat(Material.DIAMOND, 10))));
        add(out, orbRelic(VeilOrbManager.ENCHANT_ORB_ID, Material.EXPERIENCE_BOTTLE, "Runebloom Orb", CustomLoreUtil.Rarity.EPIC,
            "Fuel for pushing enchants past their normal edge.",
            List.of(
                CustomLoreUtil.section("Use", "Runic Loom Fuel", "<gray>Choose one enchant to raise by +1 or +2.</gray>"),
                CustomLoreUtil.section("Requirement", "4 Enchants", "<gray>The target item must already have at least four enchants.</gray>")
            ),
            List.of(relic("awakening_shard", 1), relic("rift_lens", 2), mat(Material.EXPERIENCE_BOTTLE, 32), mat(Material.LAPIS_LAZULI, 32))));
        add(out, deviceRelic(VeilOrbManager.SOLO_DEVICE_ID, Material.RECOVERY_COMPASS, "Lone Star Engine", CustomLoreUtil.Rarity.LEGENDARY,
            "A pocket engine that wakes up when you are truly fighting alone.",
            List.of(
                CustomLoreUtil.section("Passive", "Solo Momentum", "<gray>With an enemy within 34 blocks and no teammate within 32, gain Strength I, Speed I, and Resistance I.</gray>"),
                CustomLoreUtil.section("Kill Chain", "3 Stacks / 30 Seconds", "<gray>Solo player kills raise Speed, Strength, Resistance, and Absorption across three momentum stacks.</gray>")
            ),
            List.of(relic("saint_alloy", 1), relic("titan_gear", 5), mat(Material.RECOVERY_COMPASS, 1), mat(Material.DIAMOND_BLOCK, 2))));
        add(out, stationRelic(VeilOrbManager.RUNIC_LOOM_ID, Material.ENCHANTING_TABLE, "Runic Loom", CustomLoreUtil.Rarity.LEGENDARY,
            "A placeable table for controlled enchant upgrades.",
            List.of(
                CustomLoreUtil.section("Use", "Choose An Enchant", "<gray>Insert gear and one Runebloom Orb.</gray>"),
                CustomLoreUtil.section("Roll", "+1 or +2", "<gray>Each upgrade has even odds.</gray>")
            ),
            List.of(mat(Material.ENCHANTING_TABLE, 1), relic("awakening_shard", 1), relic("rift_lens", 2), mat(Material.LAPIS_BLOCK, 3), mat(Material.EXPERIENCE_BOTTLE, 16))));
        add(out, stationRelic(VeilOrbManager.FATE_CRUCIBLE_ID, Material.LODESTONE, "Fate Crucible", CustomLoreUtil.Rarity.MYTHIC,
            "A placeable table that doubles or deletes rare currency stacks.",
            List.of(
                CustomLoreUtil.section("Use", "50/50 Stack Gamble", "<gray>Accepts orbs and Soul Imprint.</gray>"),
                CustomLoreUtil.section("Risk", "All Or Nothing", "<gray>The entire inserted stack is doubled or destroyed.</gray>")
            ),
            List.of(mat(Material.LODESTONE, 1), relic("corrupted_essence", 1), mat(Material.ECHO_SHARD, 8), mat(Material.GOLD_BLOCK, 3), mat(Material.DIAMOND_BLOCK, 2))));

        add(out, weapon("ashen_verdict", Material.NETHERITE_SWORD, "Veilbrand Verdict", CustomLoreUtil.Rarity.LEGENDARY, 4.0, 0.20,
            WeaponEffect.ASHEN_VERDICT,
            "A judge's blade from the ash court.",
            List.of(mat(Material.NETHERITE_SWORD, 1), relic("solar_ember", 5), relic("gilded_skull", 1), mat(Material.BLAZE_ROD, 8), mat(Material.DIAMOND, 24))));
        add(out, weapon("widowfang", Material.DIAMOND_SWORD, "Gloamfang", CustomLoreUtil.Rarity.LEGENDARY, 3.0, 0.30,
            WeaponEffect.WIDOWFANG,
            "A quiet blade that waits for panic.",
            List.of(mat(Material.DIAMOND_SWORD, 1), relic("widow_silk", 5), mat(Material.FERMENTED_SPIDER_EYE, 12), mat(Material.OBSIDIAN, 16))));
        add(out, weapon("rift_pike", Material.TRIDENT, "Riftglass Pike", CustomLoreUtil.Rarity.MYTHIC, 4.5, 0.35,
            WeaponEffect.RIFT_PIKE,
            "A spear that pins distance to the wall.",
            List.of(mat(Material.TRIDENT, 1), relic("rift_lens", 5), relic("void_halo", 1), mat(Material.ENDER_PEARL, 16))));
        add(out, weapon("saintsplitter", Material.NETHERITE_AXE, "Confessor's Splitter", CustomLoreUtil.Rarity.LEGENDARY, 3.0, 0.15,
            WeaponEffect.SAINTSPLITTER,
            "A heavy axe from the machine chapel.",
            List.of(mat(Material.NETHERITE_AXE, 1), relic("titan_gear", 5), relic("saint_alloy", 1), mat(Material.IRON_BLOCK, 8))));
        add(out, weapon("tidebreaker", Material.DIAMOND_AXE, "Tideveil Breaker", CustomLoreUtil.Rarity.LEGENDARY, 3.5, 0.20,
            WeaponEffect.TIDEBREAKER,
            "An axe that becomes cruel in rain.",
            List.of(mat(Material.DIAMOND_AXE, 1), relic("abyssal_pearl", 5), relic("tideheart", 1), mat(Material.PRISMARINE, 32))));
        add(out, weapon("gravemourn", Material.NETHERITE_HOE, "Graveveil Reaper", CustomLoreUtil.Rarity.MYTHIC, 5.0, 0.35,
            WeaponEffect.GRAVEMOURN,
            "A reaping hook for debts the dead still owe.",
            List.of(mat(Material.NETHERITE_HOE, 1), relic("crimson_rib", 5), relic("verdant_heart", 1), mat(Material.SOUL_SAND, 32))));
        add(out, weapon("nullglass_rapier", Material.IRON_SWORD, "Nullveil Rapier", CustomLoreUtil.Rarity.EPIC, 2.5, 0.55,
            WeaponEffect.NULLGLASS,
            "A thin blade that cuts blessings first.",
            List.of(mat(Material.IRON_SWORD, 1), relic("rift_lens", 3), mat(Material.GLASS, 32), mat(Material.AMETHYST_SHARD, 16))));
        add(out, weapon("sunless_repeater", Material.CROSSBOW, "Moonless Repeater", CustomLoreUtil.Rarity.LEGENDARY, 0.0, 0.0,
            WeaponEffect.SUNLESS_REPEATER,
            "A crossbow loaded with nights that never ended.",
            List.of(mat(Material.CROSSBOW, 1), relic("sculk_heart", 2), relic("crimson_rib", 2), mat(Material.PHANTOM_MEMBRANE, 8))));
        add(out, weapon("thornwhisper", Material.BOW, "Briarwhisper", CustomLoreUtil.Rarity.EPIC, 0.0, 0.0,
            WeaponEffect.THORNWHISPER,
            "A bow strung with living roots.",
            List.of(mat(Material.BOW, 1), relic("living_bark", 5), relic("verdant_heart", 1), mat(Material.VINE, 16))));
        add(out, weapon("cindershard_dagger", Material.IRON_SWORD, "Cinderveil Dagger", CustomLoreUtil.Rarity.EPIC, 2.0, 0.65,
            WeaponEffect.CINDERSHARD,
            "A short knife for very close betrayals.",
            List.of(mat(Material.IRON_SWORD, 1), relic("solar_ember", 3), mat(Material.FLINT, 16), mat(Material.GOLD_INGOT, 8))));
        add(out, weapon("oathbreaker_mattock", Material.DIAMOND_PICKAXE, "Veilbreaker Mattock", CustomLoreUtil.Rarity.LEGENDARY, 3.5, 0.25,
            WeaponEffect.OATHBREAKER,
            "A war-pick for cracking shrines and stubborn monsters.",
            List.of(mat(Material.DIAMOND_PICKAXE, 1), relic("oathbound_plate", 3), relic("gilded_skull", 2), mat(Material.IRON_BLOCK, 4))));
        add(out, weapon("duskbell_mallet", Material.MACE, "Duskveil Mallet", CustomLoreUtil.Rarity.MYTHIC, 4.0, 0.20,
            WeaponEffect.DUSKBELL,
            "A bell-heavy mace that makes crowds remember silence.",
            List.of(mat(Material.MACE, 1), relic("void_halo", 1), relic("titan_gear", 6), mat(Material.BELL, 2), mat(Material.HEAVY_CORE, 1))));
        add(out, weapon("briarhook_saw", Material.NETHERITE_AXE, "Briarveil Saw", CustomLoreUtil.Rarity.LEGENDARY, 3.0, 0.25,
            WeaponEffect.BRIARHOOK,
            "An axe whose teeth keep chasing after the wound.",
            List.of(mat(Material.NETHERITE_AXE, 1), relic("living_bark", 6), relic("verdant_heart", 1), mat(Material.VINE, 24))));
        add(out, weapon("veilpiercer_glaive", Material.NETHERITE_SWORD, "Veilpiercer Glaive", CustomLoreUtil.Rarity.MYTHIC, 4.5, 0.25,
            WeaponEffect.VEILPIERCER,
            "A long blade that opens weak spots in the Veil.",
            List.of(mat(Material.NETHERITE_SWORD, 1), relic("rift_lens", 5), relic("sculk_heart", 2), relic("void_halo", 1), mat(Material.AMETHYST_SHARD, 24))));
        add(out, weapon("hollowsong_bow", Material.BOW, "Hollowsong Bow", CustomLoreUtil.Rarity.LEGENDARY, 0.0, 0.0,
            WeaponEffect.HOLLOWSONG,
            "A quiet bow that leaves enemies hearing the wrong world.",
            List.of(mat(Material.BOW, 1), relic("widow_silk", 8), relic("abyssal_pearl", 4), relic("rift_lens", 2), mat(Material.NOTE_BLOCK, 8))));
        add(out, weapon("starfall_mace", Material.MACE, "Starfall Mace", CustomLoreUtil.Rarity.MYTHIC, 5.0, 0.10,
            WeaponEffect.STARFALL,
            "A heavy core wrapped in moonless pressure.",
            List.of(mat(Material.MACE, 1), relic("void_halo", 1), relic("saint_alloy", 1), relic("tideheart", 1), mat(Material.HEAVY_CORE, 1))));

        addArmorSet(out, "crimson_guard", "Nocturne Guard", CustomLoreUtil.Rarity.LEGENDARY, Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
            "Armor for those who stand where the shrine screamed.", List.of(relic("crimson_rib", 4), relic("sculk_heart", 1), mat(Material.NETHERITE_INGOT, 1)));
        addArmorSet(out, "widow_court", "Gloam Court", CustomLoreUtil.Rarity.EPIC, Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
            "Courtwear from a queen who ate her crown.", List.of(relic("widow_silk", 4), mat(Material.DIAMOND, 6), mat(Material.COBWEB, 3)));
        addArmorSet(out, "ashen_saint", "Cinder Confessor", CustomLoreUtil.Rarity.LEGENDARY, Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
            "Sanctified armor from a chapel made of smoke.", List.of(relic("solar_ember", 3), relic("titan_gear", 3), mat(Material.NETHERITE_INGOT, 1)));
        addArmorSet(out, "tidebound", "Depthveil Pact", CustomLoreUtil.Rarity.EPIC, Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
            "A drowned pact for walking under stormwater.", List.of(relic("abyssal_pearl", 4), mat(Material.PRISMARINE_SHARD, 8)));
        addArmorSet(out, "riftwalker", "Riftveil Step", CustomLoreUtil.Rarity.MYTHIC, Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
            "A suit stitched along the seam between worlds.", List.of(relic("rift_lens", 4), mat(Material.ENDER_PEARL, 8)));
        addArmorSet(out, "eclipse_mantle", "Eclipse Mantle", CustomLoreUtil.Rarity.MYTHIC, Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
            "Endgame armor that gets sharper when the light drops.", List.of(relic("crimson_rib", 2), relic("rift_lens", 2), relic("titan_gear", 2), relic("abyssal_pearl", 2), relic("living_bark", 2), mat(Material.NETHERITE_INGOT, 1)));

        add(out, standaloneArmor("crown_of_cinders", Material.GOLDEN_HELMET, "Cinderveil Crown", CustomLoreUtil.Rarity.EPIC, EquipmentSlotGroup.HEAD,
            "A small crown for survivors of a burning throne.", ActiveAbility.NONE,
            List.of(relic("solar_ember", 4), mat(Material.GOLD_BLOCK, 3), mat(Material.FIRE_CHARGE, 8))));
        add(out, standaloneArmor("graveveil_hood", Material.LEATHER_HELMET, "Graveveil Hood", CustomLoreUtil.Rarity.RARE, EquipmentSlotGroup.HEAD,
            "A hood that keeps one eye in the dark.", ActiveAbility.NONE,
            List.of(relic("widow_silk", 4), mat(Material.PHANTOM_MEMBRANE, 4), mat(Material.LEATHER, 12))));
        add(out, standaloneArmor("bastion_pauldrons", Material.IRON_CHESTPLATE, "Veilguard Pauldrons", CustomLoreUtil.Rarity.EPIC, EquipmentSlotGroup.CHEST,
            "Iron shoulders that remember every shield wall.", ActiveAbility.NONE,
            List.of(relic("titan_gear", 4), mat(Material.IRON_BLOCK, 5), mat(Material.SHIELD, 1))));
        add(out, standaloneArmor("sculkplate_harness", Material.DIAMOND_CHESTPLATE, "Nocturne Harness", CustomLoreUtil.Rarity.LEGENDARY, EquipmentSlotGroup.CHEST,
            "A chestpiece that refuses the Warden's dark.", ActiveAbility.NONE,
            List.of(relic("sculk_heart", 2), mat(Material.DIAMOND_CHESTPLATE, 1), mat(Material.SCULK, 32))));
        add(out, standaloneArmor("stormcall_greaves", Material.CHAINMAIL_LEGGINGS, "Stormveil Greaves", CustomLoreUtil.Rarity.RARE, EquipmentSlotGroup.LEGS,
            "Chain greaves with thunder caught between links.", ActiveAbility.NONE,
            List.of(relic("solar_ember", 2), mat(Material.COPPER_BLOCK, 6), mat(Material.IRON_INGOT, 16))));
        add(out, standaloneArmor("rootwarden_greaves", Material.DIAMOND_LEGGINGS, "Briarwarden Greaves", CustomLoreUtil.Rarity.EPIC, EquipmentSlotGroup.LEGS,
            "Leg armor grown around a buried pulse.", ActiveAbility.NONE,
            List.of(relic("living_bark", 4), relic("verdant_heart", 1), mat(Material.DIAMOND_LEGGINGS, 1))));
        add(out, standaloneArmor("siren_treads", Material.DIAMOND_BOOTS, "Tideveil Treads", CustomLoreUtil.Rarity.EPIC, EquipmentSlotGroup.FEET,
            "Boots that learned to step on water's throat.", ActiveAbility.NONE,
            List.of(relic("abyssal_pearl", 4), mat(Material.DIAMOND_BOOTS, 1), mat(Material.KELP, 32))));
        add(out, standaloneArmor("voidstep_boots", Material.NETHERITE_BOOTS, "Riftstep Boots", CustomLoreUtil.Rarity.LEGENDARY, EquipmentSlotGroup.FEET,
            "Boots for falling through where the world forgot floor.", ActiveAbility.NONE,
            List.of(relic("rift_lens", 4), mat(Material.NETHERITE_BOOTS, 1), mat(Material.FEATHER, 16))));
        add(out, standaloneArmor("glasswalker_boots", Material.LEATHER_BOOTS, "Glassveil Boots", CustomLoreUtil.Rarity.RARE, EquipmentSlotGroup.FEET,
            "Soft boots for crossing things that should break.", ActiveAbility.NONE,
            List.of(mat(Material.LEATHER_BOOTS, 1), mat(Material.BLUE_ICE, 16), mat(Material.AMETHYST_SHARD, 8))));
        add(out, standaloneArmor("oathkeeper_helm", Material.IRON_HELMET, "Veilkeeper Helm", CustomLoreUtil.Rarity.EPIC, EquipmentSlotGroup.HEAD,
            "A helm that gets louder when blood gets low.", ActiveAbility.NONE,
            List.of(relic("oathbound_plate", 2), mat(Material.IRON_HELMET, 1), mat(Material.GOLDEN_APPLE, 8))));
        add(out, standaloneArmor("revelator_helm", Material.IRON_HELMET, "Veilsight Helm", CustomLoreUtil.Rarity.EPIC, EquipmentSlotGroup.HEAD,
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
        add(out, standaloneArmor("moonveil_mask", Material.LEATHER_HELMET, "Moonveil Mask", CustomLoreUtil.Rarity.LEGENDARY, EquipmentSlotGroup.HEAD,
            "A pale mask made for fighting through false night.",
            ActiveAbility.NONE,
            List.of(relic("rift_lens", 3), relic("widow_silk", 4), mat(Material.LEATHER_HELMET, 1), mat(Material.GLOWSTONE_DUST, 16))));
        add(out, standaloneArmor("gloamstep_cloak", Material.LEATHER_CHESTPLATE, "Gloamstep Cloak", CustomLoreUtil.Rarity.LEGENDARY, EquipmentSlotGroup.CHEST,
            "A cloak that almost disappears when the room gets quiet.",
            ActiveAbility.NONE,
            List.of(relic("widow_silk", 6), relic("rift_lens", 1), mat(Material.LEATHER_CHESTPLATE, 1), mat(Material.PHANTOM_MEMBRANE, 6))));

        add(out, utility("bloodbound_banner", Material.RED_BANNER, "Veilbound Banner", CustomLoreUtil.Rarity.LEGENDARY, ActiveAbility.TEAM_BANNER, 120,
            "Raises a short team battle oath around the caster.",
            List.of(relic("crimson_rib", 3), relic("oathbound_plate", 2), mat(Material.RED_BANNER, 1))));
        add(out, utility("ember_vial", Material.HONEY_BOTTLE, "Cinder Vial", CustomLoreUtil.Rarity.RARE, ActiveAbility.EMBER_VIAL, 90,
            "Swallow the last heat of Cindervale's burned sky.",
            List.of(relic("solar_ember", 2), mat(Material.GLASS_BOTTLE, 1), mat(Material.BLAZE_POWDER, 8))));
        add(out, utility("widow_antidote", Material.POTION, "Gloam Antidote", CustomLoreUtil.Rarity.RARE, ActiveAbility.WIDOW_ANTIDOTE, 75,
            "A bitter cure made from the poison that taught it.",
            List.of(relic("widow_silk", 2), mat(Material.MILK_BUCKET, 1), mat(Material.SPIDER_EYE, 8))));
        add(out, utility("saints_whetstone", Material.POLISHED_BLACKSTONE, "Confessor's Whetstone", CustomLoreUtil.Rarity.EPIC, ActiveAbility.SAINT_WHETSTONE, 0,
            "A single-use repair stone that restores half of an item's max durability.",
            List.of(relic("titan_gear", 2), mat(Material.GRINDSTONE, 1), mat(Material.POLISHED_BLACKSTONE, 8))));
        add(out, utility("abyssal_conch", Material.NAUTILUS_SHELL, "Depthveil Conch", CustomLoreUtil.Rarity.EPIC, ActiveAbility.ABYSSAL_CONCH, 100,
            "A shell that lets the drowned road open for you.",
            List.of(relic("abyssal_pearl", 4), mat(Material.NAUTILUS_SHELL, 1), mat(Material.PRISMARINE_CRYSTALS, 16))));
        add(out, utility("root_sigil", Material.FLOWER_BANNER_PATTERN, "Briar Sigil", CustomLoreUtil.Rarity.EPIC, ActiveAbility.ROOT_SIGIL, 120,
            "Calls nearby allies back from the edge of the grave.",
            List.of(relic("verdant_heart", 1), relic("living_bark", 4), mat(Material.SPORE_BLOSSOM, 1))));
        add(out, utility("titan_charm", Material.HEAVY_CORE, "Argent Charm", CustomLoreUtil.Rarity.LEGENDARY, ActiveAbility.TITAN_CHARM, 120,
            "A compact order to stop moving.",
            List.of(relic("saint_alloy", 1), relic("titan_gear", 4), mat(Material.IRON_BLOCK, 6))));
        add(out, utility("warped_key", Material.TRIAL_KEY, "Riftkey", CustomLoreUtil.Rarity.EPIC, ActiveAbility.WARPED_KEY, 20,
            "A short-range blink key with just enough mercy.",
            List.of(relic("rift_lens", 2), mat(Material.TRIAL_KEY, 1), mat(Material.ENDER_PEARL, 8))));
        add(out, utility("nullbell", Material.BELL, "Veilbell", CustomLoreUtil.Rarity.EPIC, ActiveAbility.NULL_BELL, 90,
            "A bell that rings the venom out of an oath.",
            List.of(relic("oathbound_plate", 4), relic("widow_silk", 3), mat(Material.BELL, 1), mat(Material.MILK_BUCKET, 1))));
        add(out, utility("riftward_lens", Material.SPYGLASS, "Veilsight Lens", CustomLoreUtil.Rarity.EPIC, ActiveAbility.RIFTWARD_LENS, 75,
            "A glass eye that catches cowards hiding between breaths.",
            List.of(relic("rift_lens", 2), mat(Material.SPYGLASS, 1), mat(Material.GLOWSTONE_DUST, 16), mat(Material.AMETHYST_SHARD, 8))));
        add(out, utility("gravetide_phial", Material.DRAGON_BREATH, "Gravetide Phial", CustomLoreUtil.Rarity.LEGENDARY, ActiveAbility.GRAVETIDE_PHIAL, 90,
            "A bottled undertow that remembers every body it carried.",
            List.of(relic("abyssal_pearl", 3), relic("crimson_rib", 1), mat(Material.DRAGON_BREATH, 1), mat(Material.SOUL_SAND, 12))));
        add(out, utility("oathkeeper_cord", Material.LEAD, "Veilkeeper Cord", CustomLoreUtil.Rarity.RARE, ActiveAbility.OATHKEEPER_CORD, 90,
            "A bright cord for pulling the living out of bad decisions.",
            List.of(relic("oathbound_plate", 2), mat(Material.LEAD, 1), mat(Material.FEATHER, 12), mat(Material.HONEYCOMB, 8))));
        add(out, utility("saints_ledger", Material.BOOK, "Confessor's Ledger", CustomLoreUtil.Rarity.LEGENDARY, ActiveAbility.SAINT_LEDGER, 0,
            "A machine-prayer that audits every dent in your armor.",
            List.of(relic("saint_alloy", 1), relic("titan_gear", 5), mat(Material.BOOK, 1), mat(Material.EXPERIENCE_BOTTLE, 16))));
        add(out, utility("veilflare_lantern", Material.SOUL_LANTERN, "Veilflare Lantern", CustomLoreUtil.Rarity.LEGENDARY, ActiveAbility.VEILFLARE_LANTERN, 120,
            "A lantern that burns lies out of the dark.",
            List.of(relic("sculk_heart", 1), relic("rift_lens", 3), mat(Material.SOUL_LANTERN, 1), mat(Material.GLOWSTONE_DUST, 24))));
        add(out, utility("eclipse_seal", Material.CLOCK, "Eclipse Seal", CustomLoreUtil.Rarity.MYTHIC, ActiveAbility.ECLIPSE_SEAL, 120,
            "A seal that hits harder when the sky or cave goes dark.",
            List.of(relic("void_halo", 1), relic("tideheart", 1), mat(Material.CLOCK, 1), mat(Material.GOLD_BLOCK, 4))));
        add(out, utility("briar_snare", Material.SPORE_BLOSSOM, "Briar Snare", CustomLoreUtil.Rarity.EPIC, ActiveAbility.BRIAR_SNARE, 60,
            "A thrown-root snare for stopping a push before it reaches you.",
            List.of(relic("living_bark", 4), relic("verdant_heart", 1), mat(Material.VINE, 16), mat(Material.SPORE_BLOSSOM, 1))));

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

    private static RelicDefinition catalyst(
        String id,
        Material material,
        String name,
        CustomLoreUtil.Rarity rarity,
        String line,
        List<RecipeIngredient> recipe
    ) {
        return new RelicDefinition(
            id, material, name, rarity, RelicKind.CATALYST, RelicCategory.MATERIALS,
            null, null, 0, WeaponEffect.NONE, ActiveAbility.NONE, 0,
            List.of("<gray>" + line + "</gray>"),
            List.of(
                CustomLoreUtil.section("Use", "Lower Corruption", "<gray>Use at a Corruption Anchor instead of Corrupted Essence.</gray>"),
                CustomLoreUtil.section("Roll", "50% x2 / 50% seal", "<gray>Never destroys or weakens the item, but always locks it.</gray>"),
                CustomLoreUtil.section("Limit", "No High-Tier Items", "<gray>Legendary and mythic items reject this stone.</gray>")
            ),
            List.of(), recipe, Set.of(commandToken(name))
        );
    }

    private static RelicDefinition orbRelic(
        String id,
        Material material,
        String name,
        CustomLoreUtil.Rarity rarity,
        String line,
        List<CustomLoreUtil.LoreSection> sections,
        List<RecipeIngredient> recipe
    ) {
        return new RelicDefinition(
            id, material, name, rarity, RelicKind.CATALYST, RelicCategory.MATERIALS,
            null, null, 0, WeaponEffect.NONE, ActiveAbility.NONE, 0,
            List.of("<gray>" + line + "</gray>"),
            sections,
            List.of(), recipe, Set.of(commandToken(name))
        );
    }

    private static RelicDefinition deviceRelic(
        String id,
        Material material,
        String name,
        CustomLoreUtil.Rarity rarity,
        String line,
        List<CustomLoreUtil.LoreSection> sections,
        List<RecipeIngredient> recipe
    ) {
        return new RelicDefinition(
            id, material, name, rarity, RelicKind.UTILITY, RelicCategory.UTILITIES,
            null, null, 0, WeaponEffect.NONE, ActiveAbility.NONE, 0,
            List.of("<gray>" + line + "</gray>"),
            sections,
            List.of(), recipe, Set.of(commandToken(name))
        );
    }

    private static RelicDefinition stationRelic(
        String id,
        Material material,
        String name,
        CustomLoreUtil.Rarity rarity,
        String line,
        List<CustomLoreUtil.LoreSection> sections,
        List<RecipeIngredient> recipe
    ) {
        List<CustomLoreUtil.LoreSection> allSections = new ArrayList<>(sections);
        allSections.add(CustomLoreUtil.section("Placement", "Placeable Table", "<gray>Place it where players should use this system.</gray>"));
        return new RelicDefinition(
            id, material, name, rarity, RelicKind.UTILITY, RelicCategory.UTILITIES,
            null, null, 0, WeaponEffect.NONE, ActiveAbility.NONE, 0,
            List.of("<gray>" + line + "</gray>"),
            allSections,
            List.of(), recipe, Set.of(commandToken(name))
        );
    }

    private static RelicDefinition soulImprint() {
        return new RelicDefinition(
            SOUL_IMPRINT_ID, Material.END_CRYSTAL, "Soul Imprint", CustomLoreUtil.Rarity.MYTHIC, RelicKind.UTILITY, RelicCategory.UTILITIES,
            null, null, 0, WeaponEffect.NONE, ActiveAbility.NONE, 0,
            List.of("<gray>A mirror-bright crystal that remembers one item perfectly.</gray>"),
            List.of(
                CustomLoreUtil.section("Use", "Three Click Confirm", "<gray>Pick this up and click the same gear piece three times.</gray>"),
                CustomLoreUtil.section("Copies", "Full Item Data", "<gray>Names, lore, attributes, enchants, custom stats, and item data are preserved.</gray>"),
                CustomLoreUtil.section("Limit", "Gear Only", "<gray>Only armor, tools, and weapons can be mirrored.</gray>"),
                CustomLoreUtil.section("Lock", "Soul Imprint", "<gray>The copy cannot be mirrored again or corrupted.</gray>")
            ),
            List.of(), List.of(), Set.of("mirror", "mirror_of_kalandra", "kalandra", "imprint")
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
            case MYTHIC -> 1.0;
            case LEGENDARY -> 0.75;
            case EPIC -> 0.50;
            default -> 0.25;
        };
        double tunedSpeed = speedBonus == 0.0 ? 0.0 : speedBonus + switch (rarity) {
            case MYTHIC -> 0.08;
            case LEGENDARY -> 0.06;
            case EPIC -> 0.04;
            default -> 0.02;
        };
        if (tunedDamage != 0.0) attributes.add(new AttributeBonus(Attribute.ATTACK_DAMAGE, tunedDamage, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        if (tunedSpeed != 0.0) attributes.add(new AttributeBonus(Attribute.ATTACK_SPEED, tunedSpeed, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        int bossBonus = (int) Math.round((bossDamageMultiplier(rarity) - 1.0) * 100.0);
        int pvpPenalty = (int) Math.round((1.0 - SEASON_WEAPON_PVP_MULTIPLIER) * 100.0);
        List<CustomLoreUtil.LoreSection> sections = new ArrayList<>();
        List<String> forgedStats = new ArrayList<>();
        if (tunedDamage != 0.0) forgedStats.add("+" + compactNumber(tunedDamage) + " Damage");
        if (tunedSpeed != 0.0) forgedStats.add("+" + compactNumber(tunedSpeed) + " Speed");
        if (!forgedStats.isEmpty()) {
            sections.add(CustomLoreUtil.section("Stats", String.join(" • ", forgedStats)));
        }
        sections.add(CustomLoreUtil.section("Combat", "+" + bossBonus + "% Boss • -" + pvpPenalty + "% PvP"));
        sections.add(CustomLoreUtil.section("Echo", effect.label(), effect.description()));
        return new RelicDefinition(
            id, material, name, rarity, RelicKind.WEAPON, RelicCategory.WEAPONS,
            null, null, material.getMaxDurability() > 0 ? Math.max(material.getMaxDurability() * 2, material.getMaxDurability() + 512) : 0,
            effect, ActiveAbility.NONE, 0,
            List.of("<gray>" + line + "</gray>"),
            sections,
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
        if (ability == ActiveAbility.SAINT_LEDGER) {
            return new RelicDefinition(
                id, material, name, rarity, RelicKind.UTILITY, RelicCategory.UTILITIES,
                null, null, 0, WeaponEffect.NONE, ability, cooldown,
                List.of(
                    "<gray><gold>Use:</gold> Right-click, then confirm a full repair.</gray>",
                    "<gray><gold>Target:</gold> One damaged item in your other hand.</gray>",
                    "<gray><gold>Uses:</gold> 4 successful repairs <dark_gray>•</dark_gray> <green>+20 XP each</green></gray>"
                ),
                List.of(), List.of(), recipe, Set.of(commandToken(name))
            );
        }
        List<CustomLoreUtil.LoreSection> sections = new ArrayList<>();
        String activation = ability == ActiveAbility.SAINT_WHETSTONE || ability == ActiveAbility.SAINT_LEDGER
            ? "<gray>Right-click while held. Gear in the opposite hand is checked first.</gray>"
            : "<gray>Right-click while held. Bedrock players can also use the combat shortcut.</gray>";
        sections.add(CustomLoreUtil.section("Activation", "Right Click", activation));
        sections.add(CustomLoreUtil.section("Ability", ability.label(), ability.description()));
        if (cooldown > 0) {
            sections.add(CustomLoreUtil.section("Cooldown", cooldown + " seconds", "<gray>Cooldowns keep working after relogging.</gray>"));
        }
        if (ability == ActiveAbility.SAINT_WHETSTONE) {
            sections.add(CustomLoreUtil.section("Use Limit", "Single Use", "<gray>Consumed only after it successfully repairs damaged gear.</gray>"));
        } else if (ability == ActiveAbility.SAINT_LEDGER) {
            sections.add(CustomLoreUtil.section("Use Limit", "4 Full Repairs", "<gray>Only successful repairs spend a charge. The fourth consumes the Ledger.</gray>"));
        }

        return new RelicDefinition(
            id, material, name, rarity, RelicKind.UTILITY, RelicCategory.UTILITIES,
            null, null, 0, WeaponEffect.NONE, ability, cooldown,
            List.of("<gray>" + line + "</gray>"),
            sections,
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
            null, equipmentSlotFromGroup(slot), material.getMaxDurability() > 0 ? material.getMaxDurability() * 4 : 0,
            WeaponEffect.NONE, ability, 0,
            List.of("<gray>" + line + "</gray>"),
            List.of(
                CustomLoreUtil.section("Stats", armorStatsLabel(attributes)),
                CustomLoreUtil.section("Passive", passiveName(id), passiveText(id)),
                CustomLoreUtil.section("Ward", balancePercent(bossDamageReduction(rarity)) + " Boss Damage Reduction")
            ),
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
        List<AttributeBonus> attributes = armorAttributes(slot, rarity, true);
        return new RelicDefinition(
            setId + "_" + piece,
            material,
            name,
            rarity,
            RelicKind.ARMOR,
            RelicCategory.ARMOR_SETS,
            setId,
            equipmentSlot,
            material.getMaxDurability() > 0 ? material.getMaxDurability() * 8 : 0,
            WeaponEffect.NONE,
            ActiveAbility.NONE,
            0,
            List.of("<gray>" + line + "</gray>"),
            List.of(
                CustomLoreUtil.section("Stats", armorStatsLabel(attributes)),
                CustomLoreUtil.section("Full Set", setTitle(setId), setBonusText(setId)),
                CustomLoreUtil.section("Ward", balancePercent(bossDamageReduction(rarity)) + " Boss Reduction / Piece"),
                CustomLoreUtil.section("Set Combat", "+" + balancePercent(fullSetPlayerDamageBonus(setId)) + " PvP • "
                    + balancePercent((bossDamageReduction(rarity) * 4.0) + FULL_SET_BOSS_DAMAGE_REDUCTION) + " Boss Ward")
            ),
            attributes,
            recipe,
            Set.of(commandToken(name), setId)
        );
    }

    private static List<AttributeBonus> armorAttributes(EquipmentSlotGroup slot, CustomLoreUtil.Rarity rarity, boolean setPiece) {
        boolean highTier = rarity.ordinal() >= CustomLoreUtil.Rarity.LEGENDARY.ordinal();
        double armor = setPiece
            ? (rarity == CustomLoreUtil.Rarity.MYTHIC ? 2.5 : highTier ? 2.0 : rarity == CustomLoreUtil.Rarity.EPIC ? 1.5 : 1.0)
            : (rarity == CustomLoreUtil.Rarity.MYTHIC ? 1.5 : highTier ? 1.25 : rarity == CustomLoreUtil.Rarity.EPIC ? 1.0 : 0.75);
        double toughness = rarity == CustomLoreUtil.Rarity.MYTHIC ? 1.5 : highTier ? 1.0 : rarity == CustomLoreUtil.Rarity.EPIC ? 0.75 : 0.5;
        List<AttributeBonus> attributes = new ArrayList<>();
        attributes.add(new AttributeBonus(Attribute.ARMOR, armor, AttributeModifier.Operation.ADD_NUMBER, slot));
        attributes.add(new AttributeBonus(Attribute.ARMOR_TOUGHNESS, toughness, AttributeModifier.Operation.ADD_NUMBER, slot));
        if (setPiece && highTier) {
            attributes.add(new AttributeBonus(
                Attribute.KNOCKBACK_RESISTANCE,
                rarity == CustomLoreUtil.Rarity.MYTHIC ? 0.04 : 0.03,
                AttributeModifier.Operation.ADD_NUMBER,
                slot
            ));
        }
        return List.copyOf(attributes);
    }

    private static String armorStatsLabel(List<AttributeBonus> attributes) {
        List<String> stats = new ArrayList<>();
        for (AttributeBonus bonus : attributes) {
            if (bonus.attribute() == Attribute.ARMOR) {
                stats.add("+" + compactNumber(bonus.amount()) + " Armor");
            } else if (bonus.attribute() == Attribute.ARMOR_TOUGHNESS) {
                stats.add("+" + compactNumber(bonus.amount()) + " Tough");
            } else if (bonus.attribute() == Attribute.KNOCKBACK_RESISTANCE) {
                stats.add("+" + compactNumber(bonus.amount() * 100.0) + "% KB");
            }
        }
        return stats.isEmpty() ? "No Added Stats" : String.join(" • ", stats);
    }

    static int whetstoneRepairAmount(int maxDamage) {
        return Math.max(1, Math.max(0, maxDamage) / 2);
    }

    static String compactNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.US, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
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
            case "crimson_guard" -> "Nocturne Guard";
            case "widow_court" -> "Gloam Court";
            case "ashen_saint" -> "Cinder Confessor";
            case "tidebound" -> "Depthveil Pact";
            case "riftwalker" -> "Riftveil Step";
            case "eclipse_mantle" -> "Eclipse Mantle";
            default -> "Veil Set";
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
            case "eclipse_mantle" -> "Night Vision and Resistance I. In darkness, gain Strength I and Speed II.";
            default -> "Wear every piece to wake the set.";
        };
    }

    static double fullSetPlayerDamageBonus(String setId) {
        return switch (setId == null ? "" : setId) {
            case "widow_court", "tidebound" -> 0.03;
            case "ashen_saint" -> 0.04;
            case "riftwalker", "crimson_guard" -> 0.05;
            case "eclipse_mantle" -> 0.06;
            default -> 0.0;
        };
    }

    public double equippedFullSetPlayerDamageBonus(Player player) {
        return player == null ? 0.0D : fullSetPlayerDamageBonus(fullArmorSet(player));
    }

    private static int armorSetTier(String setId) {
        return switch (setId == null ? "" : setId) {
            case "widow_court" -> 3;
            case "tidebound" -> 5;
            case "ashen_saint" -> 6;
            case "riftwalker" -> 7;
            case "crimson_guard" -> 9;
            case "eclipse_mantle" -> 10;
            default -> Integer.MAX_VALUE;
        };
    }

    private static int armoryTier(RelicDefinition definition) {
        if (definition == null) {
            return Integer.MAX_VALUE;
        }
        if (definition.armorSetId() != null) {
            return armorSetTier(definition.armorSetId());
        }
        return switch (definition.id()) {
            case "oathbreaker_mattock" -> 1;
            case "cindershard_dagger", "ashen_verdict" -> 2;
            case "widowfang" -> 3;
            case "thornwhisper", "briarhook_saw" -> 4;
            case "tidebreaker" -> 5;
            case "saintsplitter" -> 6;
            case "nullglass_rapier", "rift_pike" -> 7;
            case "hollowsong_bow" -> 8;
            case "sunless_repeater", "gravemourn", "veilpiercer_glaive" -> 9;
            case "duskbell_mallet", "starfall_mace" -> 10;
            default -> 50;
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
            case "revelator_helm" -> "Veilsight";
            case "moonveil_mask" -> "Moonlit Focus";
            case "gloamstep_cloak" -> "Gloamstep";
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
            case "revelator_helm" -> "<gray>Hidden non-player threats within 48 blocks glow while this helmet is worn.</gray>";
            case "moonveil_mask" -> "<gray>Refreshes Night Vision and clears Blindness.</gray>";
            case "gloamstep_cloak" -> "<gray>Sneaking in darkness grants brief Invisibility and Speed I.</gray>";
            default -> "<gray>A quiet relic effect.</gray>";
        };
    }

    public enum RelicCategory {
        WEAPONS(
            "weapons",
            "Veil Weapons",
            Material.NETHERITE_SWORD,
            "<gradient:#ff4d6d:#f97316><bold>Veil Weapons</bold></gradient>",
            List.of("<gray>Sixteen PvP-aware weapons and tools with cooldown-gated or conditional pressure.</gray>")
        ),
        ARMOR_SETS(
            "armor_sets",
            "Veil Sets",
            Material.NETHERITE_CHESTPLATE,
            "<gradient:#facc15:#ef4444><bold>Full Armor Sets</bold></gradient>",
            List.of("<gray>Six four-piece sets. The combo effect only wakes when the full set is worn.</gray>")
        ),
        ARMOR_PIECES(
            "armor_pieces",
            "Solitary Armor",
            Material.DIAMOND_HELMET,
            "<gradient:#67e8f9:#a78bfa><bold>Solitary Armor</bold></gradient>",
            List.of("<gray>Thirteen individual pieces with stand-alone passives.</gray>")
        ),
        UTILITIES(
            "utilities",
            "Veil Utility",
            Material.RECOVERY_COMPASS,
            "<gradient:#c084fc:#fb7185><bold>Utility Relics</bold></gradient>",
            List.of("<gray>Tactical tools, tables, travel, cleansing, repairs, and survival relics.</gray>")
        ),
        MATERIALS(
            "materials",
            "Boss Materials",
            Material.ECHO_SHARD,
            "<gradient:#94a3b8:#f8fafc><bold>Boss Materials</bold></gradient>",
            List.of("<gray>Trophies dropped by custom bosses and used in Veil recipes.</gray>")
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
        CATALYST("CATALYST"),
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
        ASHEN_VERDICT("Veilbrand Verdict", "<gray>+3.5 hit dmg • Burns 6s.</gray>"),
        WIDOWFANG("Venom Hook", "<gray>8s CD • Poison II 5s • Slow II 3.5s.</gray>"),
        RIFT_PIKE("Rift Pull", "<gray>10s CD • Pulls the target to you.</gray>"),
        SAINTSPLITTER("Iron Sentence", "<gray>+2.5 hit dmg • Weakness II 6s/9s CD.</gray>"),
        TIDEBREAKER("Storm Edge", "<gray>+1 hit dmg • +4 in water or rain.</gray>"),
        GRAVEMOURN("Soul Tithe", "<gray>+2 hit dmg • Heal 1.5 hearts/4s.</gray>"),
        NULLGLASS("Blessing Cut", "<gray>14s CD • Strip 1 buff • Weakness I 3.5s.</gray>"),
        SUNLESS_REPEATER("Sunless Bolt", "<gray>+3.5 bolt dmg • Pierce II • Wither I • Darkness 4s.</gray>"),
        THORNWHISPER("Rooted Shot", "<gray>+2.5 arrow dmg • Slow II 5s • Fatigue I 3s.</gray>"),
        CINDERSHARD("Back Cinder", "<gray>Backstab: +4.5 dmg • Speed II 3.5s.</gray>"),
        OATHBREAKER("Shrinebreaker", "<gray>Boss hits: +4 dmg • Weakness I + Fatigue I 5s/10s CD.</gray>"),
        DUSKBELL("Duskbell Shock", "<gray>14s CD • +3 area dmg • Slow I 3s.</gray>"),
        BRIARHOOK("Briar Chase", "<gray>Slow 3.5s • Under 35% HP: +2.5 dmg + Speed I.</gray>"),
        VEILPIERCER("Veil Pierce", "<gray>+2 hit dmg • +5 if marked (8s/8s CD).</gray>"),
        HOLLOWSONG("Hollow Note", "<gray>9s CD • Weakness II + Slow II 6s • +2 dmg vs weak.</gray>"),
        STARFALL("Starfall Burst", "<gray>12s CD • +4 area dmg • Slow II 5s (4.5 blocks).</gray>");

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
        TEAM_BANNER("Veilbound Oath", "<gray>You and nearby teammates gain Strength I, Resistance I, and Absorption I for 40s.</gray>"),
        EMBER_VIAL("Last Ember", "<gray>Extinguishes you and grants Fire Resistance for 90 seconds.</gray>"),
        WIDOW_ANTIDOTE("Black Antidote", "<gray>Removes all harmful potion effects from you.</gray>"),
        SAINT_WHETSTONE("Confessor Repair", "<gray>Repairs 50% of one damaged item's durability, then crumbles.</gray>"),
        ABYSSAL_CONCH("Depthveil Breath", "<gray>Grants Water Breathing for 120 seconds and Dolphin's Grace for 90 seconds.</gray>"),
        ROOT_SIGIL("Briar Mercy", "<gray>You and nearby teammates gain Regeneration II and Absorption I for 35s.</gray>"),
        TITAN_CHARM("Argent Brace", "<gray>Grants Resistance II and Absorption II for 35 seconds.</gray>"),
        WARPED_KEY("Warp Step", "<gray>Safely blinks up to 12 blocks toward where you are looking.</gray>"),
        NULL_BELL("Veilbell Peal", "<gray>Cleanses teammates within 8 blocks and grants Resistance I for 15s.</gray>"),
        RIFTWARD_LENS("Veilsight", "<gray>Non-player creatures within 48 blocks glow for 20s.</gray>"),
        GRAVETIDE_PHIAL("Gravetide Break", "<gray>Within 9 blocks: 2 damage, push, Slowness II, and Weakness I for 12s.</gray>"),
        OATHKEEPER_CORD("Veilkeeper Rush", "<gray>You and teammates within 10 blocks gain Speed II and Slow Falling for 25s.</gray>"),
        SAINT_LEDGER("Confessor's Ledger", "<gray>Four uses. Fully repairs the damaged item in your other hand and returns XP.</gray>"),
        VEILFLARE_LANTERN("Veilflare", "<gray>Allies within 10 blocks gain Night Vision (30s) and Resistance I (12s). Threats within 12 glow and weaken.</gray>"),
        ECLIPSE_SEAL("Eclipse Wake", "<gray>Gain Strength I, Speed I, and Resistance I for 25s. Darkness raises Speed and Resistance to II and adds Absorption II.</gray>"),
        BRIAR_SNARE("Briar Snare", "<gray>Pulls enemies within 8 blocks and applies Slowness IV and Mining Fatigue II for 10s.</gray>");

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

    private record SeasonMenuHolder(MenuView view, RelicCategory category, String relicId) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record RelicUseConfirmationHolder(UUID playerId, String relicId, boolean alternate, EquipmentSlot hand)
        implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record MirrorResult(boolean success, String message, ItemStack copy, ItemStack remainingCursor) {
        private static MirrorResult success(ItemStack copy, ItemStack remainingCursor) {
            return new MirrorResult(true, "", copy, remainingCursor);
        }

        private static MirrorResult failure(String message) {
            return new MirrorResult(false, message, null, null);
        }
    }

    private record SoulImprintConfirmation(int slot, ItemStack snapshot, int clicks, long expiresAt) {
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
