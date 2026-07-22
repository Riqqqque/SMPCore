package me.rique.smpcore.boss;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.audit.ItemAuditManager;
import me.rique.smpcore.season.SeasonRelicManager;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class BossTestLoadoutManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int[] LOADOUT_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};
    private static final int CLEAR_SLOT = 49;
    private static final int CLOSE_SLOT = 53;
    private static final List<LoadoutDefinition> LOADOUTS = createLoadouts();
    private static final Map<String, LoadoutDefinition> LOADOUTS_BY_BOSS = indexLoadouts();

    private final SMPCore plugin;
    private final NamespacedKey testLoadoutKey;
    private final Map<String, Enchantment> enchantments;

    public BossTestLoadoutManager(SMPCore plugin) {
        this.plugin = plugin;
        this.testLoadoutKey = new NamespacedKey(plugin, "boss_test_loadout");
        this.enchantments = loadEnchantments();
        validateRelicIds();
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof BossTestLoadoutHolder) {
                player.closeInventory();
            }
        }
    }

    public void openMenu(Player player) {
        if (player == null || !player.hasPermission("smpcore.dungeon.admin")) {
            return;
        }

        Inventory inventory = Bukkit.createInventory(
            new BossTestLoadoutHolder(player.getUniqueId()),
            54,
            BedrockCompat.menuTitle(
                player,
                MM.deserialize("<gradient:#ef4444:#facc15><bold>Boss Test Loadouts</bold></gradient>"),
                "Boss Test Loadouts"
            )
        );
        ItemStack filler = menuItem(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> </dark_gray>", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        inventory.setItem(4, menuItem(
            Material.ARMOR_STAND,
            "<gradient:#ef4444:#facc15><bold>Pre-Boss Test Kits</bold></gradient>",
            List.of(
                "<gray>Each kit matches gear available before that fight.</gray>",
                "<gray>Relics use their real stats, models, and abilities.</gray>",
                "<gray>Replaced items move to your inventory or your feet.</gray>",
                "<dark_gray>Test copies can be removed with /bossloadout clear.</dark_gray>"
            )
        ));

        List<BossManager.BossType> bosses = BossManager.BossType.progressionOrder();
        for (int i = 0; i < bosses.size() && i < LOADOUT_SLOTS.length; i++) {
            BossManager.BossType boss = bosses.get(i);
            LoadoutDefinition loadout = LOADOUTS_BY_BOSS.get(boss.id());
            inventory.setItem(LOADOUT_SLOTS[i], loadoutItem(player, boss, loadout));
        }

        inventory.setItem(CLEAR_SLOT, menuItem(
            Material.LAVA_BUCKET,
            "<red><bold>Clear Test Gear</bold></red>",
            List.of(
                "<gray>Removes only items created by this loadout tool.</gray>",
                "<yellow>Click to clean up your current test kit.</yellow>"
            )
        ));
        inventory.setItem(CLOSE_SLOT, menuItem(
            Material.BARRIER,
            "<red><bold>Close</bold></red>",
            List.of("<gray>Close this menu.</gray>")
        ));
        player.openInventory(inventory);
    }

    public boolean equipLoadout(Player player, BossManager.BossType boss) {
        if (player == null || boss == null || !player.hasPermission("smpcore.dungeon.admin")) {
            return false;
        }
        LoadoutDefinition definition = LOADOUTS_BY_BOSS.get(boss.id());
        if (definition == null) {
            player.sendMessage(MessageUtil.error("No test loadout is configured for that boss."));
            return false;
        }

        PreparedLoadout prepared;
        try {
            prepared = prepareLoadout(definition);
        } catch (IllegalStateException exception) {
            plugin.getLogger().severe("Could not create boss test loadout for " + boss.id() + ": " + exception.getMessage());
            player.sendMessage(MessageUtil.error("That test loadout could not be created. Check the server log."));
            return false;
        }

        recordAdminAcquisitions(player, prepared.allItems(), boss);
        clearTaggedItems(player);
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> displaced = displaceTargetSlots(inventory, prepared);

        inventory.setHelmet(prepared.armor().get(0));
        inventory.setChestplate(prepared.armor().get(1));
        inventory.setLeggings(prepared.armor().get(2));
        inventory.setBoots(prepared.armor().get(3));
        for (int i = 0; i < prepared.weapons().size(); i++) {
            inventory.setItem(i, prepared.weapons().get(i));
        }
        if (prepared.arrows() != null) {
            inventory.setItem(8, prepared.arrows());
        }
        inventory.setItemInOffHand(prepared.shield());
        inventory.setHeldItemSlot(0);

        int droppedStacks = restoreDisplacedItems(player, displaced);
        player.updateInventory();
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.8f, 1.1f);
        player.sendMessage(MessageUtil.success(
            "Equipped the Tier <white>" + boss.progressionTier() + "</white> kit for <white>"
                + boss.plainDisplayName() + "</white>."
        ));
        if (droppedStacks > 0) {
            player.sendMessage(MessageUtil.warn(
                "Your inventory was full, so <white>" + droppedStacks + "</white> replaced item stack(s) were placed at your feet."
            ));
        }
        return true;
    }

    public int clearTestGear(Player player) {
        if (player == null || !player.hasPermission("smpcore.dungeon.admin")) {
            return 0;
        }
        int removed = clearTaggedItems(player);
        player.updateInventory();
        if (removed == 0) {
            player.sendMessage(MessageUtil.info("You do not have any boss test gear to clear."));
        } else {
            player.sendMessage(MessageUtil.success("Removed <white>" + removed + "</white> boss test item stack(s)."));
        }
        return removed;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof BossTestLoadoutHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
            || !holder.playerId().equals(player.getUniqueId())
            || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize() || !MenuItemUtil.isVisibleItem(event.getCurrentItem())) {
            return;
        }

        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == CLEAR_SLOT) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> clearTestGear(player));
            return;
        }

        BossManager.BossType boss = bossForSlot(slot);
        if (boss == null) {
            return;
        }
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> equipLoadout(player, boss));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof BossTestLoadoutHolder)) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    static List<LoadoutDefinition> definitions() {
        return LOADOUTS;
    }

    private ItemStack loadoutItem(Player player, BossManager.BossType boss, LoadoutDefinition loadout) {
        if (loadout == null) {
            return menuItem(
                Material.BARRIER,
                boss.displayName(),
                List.of("<red>No test loadout is configured for this boss.</red>")
            );
        }
        String enchantLabel = loadout.protectionLevel() > 4
            ? "Protection " + loadout.protectionLevel() + " and upgraded weapons"
            : "Protection " + loadout.protectionLevel() + " and max vanilla weapon enchants";
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Tier:</gray> <white>" + boss.progressionTier() + "/" + LOADOUTS.size() + "</white>");
        lore.add("<gray>Recommended:</gray> <white>" + boss.recommendedGear() + "</white>");
        lore.add("<gray>Armor:</gray> <white>" + loadout.armorLabel() + "</white>");
        lore.add("<gray>Weapons:</gray>");
        for (ItemDefinition weapon : loadout.weapons()) {
            lore.add("<white>- " + displayName(weapon) + "</white>");
        }
        lore.add("<gray>Benchmark:</gray> <white>" + enchantLabel + "</white>");
        lore.add("<dark_gray>Includes an enchanted shield" + (loadout.hasRangedWeapon() ? " and 64 arrows" : "") + ".</dark_gray>");
        lore.add("<yellow>" + BedrockCompat.menuActionWord(player) + " to equip this pre-boss kit.</yellow>");
        return menuItem(
            boss.menuIcon(),
            boss.displayName(),
            lore
        );
    }

    private PreparedLoadout prepareLoadout(LoadoutDefinition definition) {
        List<ItemStack> armor = new ArrayList<>(4);
        for (ItemDefinition item : definition.armor()) {
            ItemStack stack = createItem(item);
            applyArmorEnchantments(stack, definition.protectionLevel());
            tag(stack, definition.bossId());
            armor.add(stack);
        }

        List<ItemStack> weapons = new ArrayList<>();
        boolean ranged = false;
        for (ItemDefinition item : definition.weapons()) {
            ItemStack stack = createItem(item);
            applyWeaponEnchantments(stack, definition.weaponEnchantLevel());
            tag(stack, definition.bossId());
            weapons.add(stack);
            ranged |= stack.getType() == Material.BOW || stack.getType() == Material.CROSSBOW;
        }

        ItemStack shield = new ItemStack(Material.SHIELD);
        addEnchant(shield, "unbreaking", 3);
        addEnchant(shield, "mending", 1);
        tag(shield, definition.bossId());

        ItemStack arrows = null;
        if (ranged) {
            arrows = new ItemStack(Material.ARROW, 64);
            tag(arrows, definition.bossId());
        }
        return new PreparedLoadout(List.copyOf(armor), List.copyOf(weapons), shield, arrows);
    }

    private ItemStack createItem(ItemDefinition definition) {
        if (definition.relicId() == null) {
            return new ItemStack(definition.material());
        }
        SeasonRelicManager relics = plugin.getSeasonRelicManager();
        ItemStack item = relics == null ? null : relics.createRelicItem(definition.relicId());
        if (item == null || item.getType().isAir()) {
            throw new IllegalStateException("missing relic " + definition.relicId());
        }
        return item;
    }

    private void applyArmorEnchantments(ItemStack item, int protectionLevel) {
        addEnchant(item, "protection", protectionLevel);
        addEnchant(item, "unbreaking", 3);
        addEnchant(item, "mending", 1);
        String material = item.getType().name();
        if (material.endsWith("_HELMET")) {
            addEnchant(item, "respiration", 3);
            addEnchant(item, "aqua_affinity", 1);
        } else if (material.endsWith("_BOOTS")) {
            addEnchant(item, "feather_falling", 4);
            addEnchant(item, "depth_strider", 3);
        }
    }

    private void applyWeaponEnchantments(ItemStack item, int damageLevel) {
        Material material = item.getType();
        String name = material.name();
        if (name.endsWith("_SWORD") || name.endsWith("_AXE")) {
            addEnchant(item, "sharpness", damageLevel);
        } else if (material == Material.BOW) {
            addEnchant(item, "power", damageLevel);
        } else if (material == Material.CROSSBOW) {
            addEnchant(item, "quick_charge", 3);
            addEnchant(item, "multishot", 1);
        } else if (material == Material.TRIDENT) {
            addEnchant(item, "impaling", 5);
            addEnchant(item, "loyalty", 3);
        } else if (name.endsWith("_PICKAXE") || name.endsWith("_HOE")) {
            addEnchant(item, "efficiency", 5);
        }
        addEnchant(item, "unbreaking", 3);
        addEnchant(item, "mending", 1);
    }

    private void addEnchant(ItemStack item, String enchantmentId, int level) {
        if (item == null || level <= 0) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        Enchantment enchantment = enchantments.get(enchantmentId);
        if (meta == null || enchantment == null) {
            return;
        }
        meta.addEnchant(enchantment, level, true);
        item.setItemMeta(meta);
    }

    private void tag(ItemStack item, String bossId) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(testLoadoutKey, PersistentDataType.STRING, bossId);
        item.setItemMeta(meta);
    }

    private int clearTaggedItems(Player player) {
        PlayerInventory inventory = player.getInventory();
        int removed = 0;

        ItemStack[] storage = inventory.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            if (isTagged(storage[i])) {
                storage[i] = null;
                removed++;
            }
        }
        inventory.setStorageContents(storage);

        ItemStack[] armor = inventory.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (isTagged(armor[i])) {
                armor[i] = null;
                removed++;
            }
        }
        inventory.setArmorContents(armor);

        if (isTagged(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
            removed++;
        }
        if (isTagged(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            removed++;
        }
        return removed;
    }

    private boolean isTagged(ItemStack item) {
        ItemMeta meta = item == null || item.getType().isAir() ? null : item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(testLoadoutKey, PersistentDataType.STRING);
    }

    private List<ItemStack> displaceTargetSlots(PlayerInventory inventory, PreparedLoadout loadout) {
        List<ItemStack> displaced = new ArrayList<>();
        displace(displaced, inventory.getHelmet());
        displace(displaced, inventory.getChestplate());
        displace(displaced, inventory.getLeggings());
        displace(displaced, inventory.getBoots());
        inventory.setHelmet(null);
        inventory.setChestplate(null);
        inventory.setLeggings(null);
        inventory.setBoots(null);

        for (int slot = 0; slot < loadout.weapons().size(); slot++) {
            displace(displaced, inventory.getItem(slot));
            inventory.setItem(slot, null);
        }
        if (loadout.arrows() != null) {
            displace(displaced, inventory.getItem(8));
            inventory.setItem(8, null);
        }
        displace(displaced, inventory.getItemInOffHand());
        inventory.setItemInOffHand(null);
        return displaced;
    }

    private void displace(List<ItemStack> displaced, ItemStack item) {
        if (item != null && !item.getType().isAir()) {
            displaced.add(item);
        }
    }

    private int restoreDisplacedItems(Player player, List<ItemStack> displaced) {
        int droppedStacks = 0;
        for (ItemStack item : displaced) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                droppedStacks++;
            }
        }
        return droppedStacks;
    }

    private void recordAdminAcquisitions(Player player, List<ItemStack> items, BossManager.BossType boss) {
        ItemAuditManager audit = plugin.getItemAuditManager();
        if (audit == null) {
            return;
        }
        for (ItemStack item : items) {
            audit.recordKnownAcquisition(
                player,
                item,
                "admin_boss_test_loadout",
                "Temporary pre-boss test gear for " + boss.plainDisplayName() + "."
            );
        }
    }

    private BossManager.BossType bossForSlot(int slot) {
        for (int i = 0; i < LOADOUT_SLOTS.length; i++) {
            if (LOADOUT_SLOTS[i] == slot) {
                List<BossManager.BossType> bosses = BossManager.BossType.progressionOrder();
                return i < bosses.size() ? bosses.get(i) : null;
            }
        }
        return null;
    }

    private String displayName(ItemDefinition definition) {
        if (definition.relicId() != null && plugin.getSeasonRelicManager() != null) {
            String name = plugin.getSeasonRelicManager().displayNameFor(definition.relicId());
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        String rawName = definition.material() == null ? definition.relicId() : definition.material().name();
        StringBuilder name = new StringBuilder();
        for (String part : rawName.toLowerCase(Locale.ROOT).split("_")) {
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return name.toString();
    }

    private ItemStack menuItem(Material material, String name, List<String> loreLines) {
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

    private Map<String, Enchantment> loadEnchantments() {
        Map<String, Enchantment> loaded = new LinkedHashMap<>();
        for (String id : List.of(
            "protection", "unbreaking", "mending", "respiration", "aqua_affinity",
            "feather_falling", "depth_strider", "sharpness", "power", "quick_charge",
            "multishot", "impaling", "loyalty", "efficiency"
        )) {
            Enchantment enchantment = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT)
                .get(NamespacedKey.minecraft(id));
            if (enchantment == null) {
                throw new IllegalStateException("Missing vanilla enchantment: " + id);
            }
            loaded.put(id, enchantment);
        }
        return Map.copyOf(loaded);
    }

    private void validateRelicIds() {
        SeasonRelicManager relics = plugin.getSeasonRelicManager();
        if (relics == null) {
            plugin.getLogger().severe("Boss test loadouts started before the Season relic registry was ready.");
            return;
        }
        for (LoadoutDefinition loadout : LOADOUTS) {
            for (ItemDefinition item : loadout.allDefinitions()) {
                if (item.relicId() != null && !relics.isRelicId(item.relicId())) {
                    plugin.getLogger().severe(
                        "Boss test loadout for " + loadout.bossId() + " references missing relic " + item.relicId() + "."
                    );
                }
            }
        }
    }

    private static Map<String, LoadoutDefinition> indexLoadouts() {
        Map<String, LoadoutDefinition> indexed = new LinkedHashMap<>();
        for (LoadoutDefinition loadout : LOADOUTS) {
            indexed.put(loadout.bossId(), loadout);
        }
        return Map.copyOf(indexed);
    }

    private static List<LoadoutDefinition> createLoadouts() {
        return List.of(
            loadout("yule_the_minion", "Enchanted diamond armor", diamondArmor(),
                List.of(vanilla(Material.DIAMOND_SWORD), vanilla(Material.BOW)), 4, 5),
            loadout("kael_the_ashen", "Enchanted diamond armor", diamondArmor(),
                List.of(relic("oathbreaker_mattock"), vanilla(Material.BOW)), 4, 5),
            loadout("vesper_the_widow_queen", "Cinderveil mix", List.of(
                    relic("crown_of_cinders"), vanilla(Material.DIAMOND_CHESTPLATE),
                    relic("stormcall_greaves"), vanilla(Material.DIAMOND_BOOTS)
                ), List.of(relic("cindershard_dagger"), relic("ashen_verdict")), 4, 5),
            loadout("mirewood_the_root_tyrant", "Gloam Court set", armorSet("widow_court"),
                List.of(relic("widowfang"), vanilla(Material.BOW)), 4, 5),
            loadout("nereida_the_abyss_mother", "Gloam Court set", armorSet("widow_court"),
                List.of(relic("briarhook_saw"), relic("thornwhisper")), 4, 5),
            loadout("iron_saint", "Depthveil Pact set", armorSet("tidebound"),
                List.of(relic("tidebreaker")), 4, 5),
            loadout("aurelion_the_rift_seraph", "Cinder Confessor set", armorSet("ashen_saint"),
                List.of(relic("saintsplitter")), 4, 5),
            loadout("morvessa_the_runebloom_witch", "Riftveil Step set", armorSet("riftwalker"),
                List.of(relic("nullglass_rapier"), relic("rift_pike")), 4, 5),
            loadout("voralith_the_crimson_warden", "Riftveil Step set", armorSet("riftwalker"),
                List.of(relic("hollowsong_bow"), relic("nullglass_rapier")), 5, 6),
            loadout("corrupted_oathkeeper", "Nocturne Guard set", armorSet("crimson_guard"),
                List.of(relic("veilpiercer_glaive"), relic("sunless_repeater"), relic("gravemourn")), 5, 6)
        );
    }

    private static LoadoutDefinition loadout(
        String bossId,
        String armorLabel,
        List<ItemDefinition> armor,
        List<ItemDefinition> weapons,
        int protectionLevel,
        int weaponEnchantLevel
    ) {
        return new LoadoutDefinition(bossId, armorLabel, armor, weapons, protectionLevel, weaponEnchantLevel);
    }

    private static List<ItemDefinition> diamondArmor() {
        return List.of(
            vanilla(Material.DIAMOND_HELMET),
            vanilla(Material.DIAMOND_CHESTPLATE),
            vanilla(Material.DIAMOND_LEGGINGS),
            vanilla(Material.DIAMOND_BOOTS)
        );
    }

    private static List<ItemDefinition> armorSet(String setId) {
        return List.of(
            relic(setId + "_helm"),
            relic(setId + "_chestplate"),
            relic(setId + "_leggings"),
            relic(setId + "_boots")
        );
    }

    private static ItemDefinition vanilla(Material material) {
        return new ItemDefinition(material, null);
    }

    private static ItemDefinition relic(String relicId) {
        return new ItemDefinition(null, relicId);
    }

    record ItemDefinition(Material material, String relicId) {
        ItemDefinition {
            if ((material == null) == (relicId == null || relicId.isBlank())) {
                throw new IllegalArgumentException("Define exactly one material or relic ID.");
            }
        }
    }

    record LoadoutDefinition(
        String bossId,
        String armorLabel,
        List<ItemDefinition> armor,
        List<ItemDefinition> weapons,
        int protectionLevel,
        int weaponEnchantLevel
    ) {
        LoadoutDefinition {
            Objects.requireNonNull(bossId, "bossId");
            Objects.requireNonNull(armorLabel, "armorLabel");
            armor = List.copyOf(armor);
            weapons = List.copyOf(weapons);
            if (armor.size() != 4) {
                throw new IllegalArgumentException(bossId + " must define helmet, chestplate, leggings, and boots.");
            }
            if (weapons.isEmpty() || weapons.size() > 7) {
                throw new IllegalArgumentException(bossId + " must define between one and seven weapons.");
            }
        }

        List<ItemDefinition> allDefinitions() {
            List<ItemDefinition> items = new ArrayList<>(armor.size() + weapons.size());
            items.addAll(armor);
            items.addAll(weapons);
            return List.copyOf(items);
        }

        boolean hasRangedWeapon() {
            return weapons.stream().anyMatch(item -> item.material() == Material.BOW
                || item.material() == Material.CROSSBOW
                || "thornwhisper".equals(item.relicId())
                || "hollowsong_bow".equals(item.relicId())
                || "sunless_repeater".equals(item.relicId()));
        }
    }

    private record PreparedLoadout(
        List<ItemStack> armor,
        List<ItemStack> weapons,
        ItemStack shield,
        ItemStack arrows
    ) {
        private List<ItemStack> allItems() {
            List<ItemStack> items = new ArrayList<>(armor.size() + weapons.size() + 2);
            items.addAll(armor);
            items.addAll(weapons);
            items.add(shield);
            if (arrows != null) {
                items.add(arrows);
            }
            return items;
        }
    }

    private record BossTestLoadoutHolder(UUID playerId)
        implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
