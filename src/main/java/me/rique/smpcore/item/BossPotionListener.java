package me.rique.smpcore.item;

import io.papermc.paper.potion.PotionMix;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.ItemModelUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class BossPotionListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int MENU_SIZE = 54;
    private static final int BACK_SLOT = 49;
    private static final int[] BREW_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final Material[] POTION_TYPES = {
        Material.POTION,
        Material.SPLASH_POTION,
        Material.LINGERING_POTION
    };

    private final SMPCore plugin;
    private final NamespacedKey keyBossPotionId;
    private final NamespacedKey keyAuthorizedBrewer;
    private final List<NamespacedKey> registeredMixes = new ArrayList<>();

    public BossPotionListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyBossPotionId = new NamespacedKey(plugin, "boss_potion_id");
        this.keyAuthorizedBrewer = new NamespacedKey(plugin, "boss_brew_authorized_player");
    }

    public void start() {
        registerPotionMixes();
    }

    public void shutdown() {
        for (NamespacedKey key : registeredMixes) {
            Bukkit.getPotionBrewer().removePotionMix(key);
        }
        registeredMixes.clear();
    }

    public boolean isBossPotion(ItemStack item) {
        if (item == null || item.getType().isAir() || !(item.getItemMeta() instanceof PotionMeta meta)) {
            return false;
        }
        return meta.getPersistentDataContainer().has(keyBossPotionId, PersistentDataType.STRING);
    }

    public void openPotionMenu(Player player) {
        if (!canBrewBossPotions(player)) {
            player.sendMessage(me.rique.smpcore.util.MessageUtil.warn("Complete Vespera's Moonlit Thesis before brewing boss potions."));
            return;
        }
        Inventory inventory = Bukkit.createInventory(
            new BossPotionMenuHolder(),
            MENU_SIZE,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#38bdf8:#fb7185><bold>Boss Brews</bold></gradient>"), "Boss Brews")
        );
        ItemStack filler = menuItem(Material.BLACK_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, MenuItemUtil.INACTIVE_SLOT_LORE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isFrameSlot(slot, inventory.getSize())) {
                inventory.setItem(slot, filler);
            }
        }

        inventory.setItem(4, menuItem(Material.BREWING_STAND, "<gradient:#38bdf8:#fb7185><bold>Boss Brews</bold></gradient>", List.of(
            "<gray>Stronger potions brewed from boss materials.</gray>",
            "<gray>Use an <white>Awkward Potion</white> plus one listed material.</gray>",
            "<dark_gray>Works with normal, splash, and lingering awkward potions.</dark_gray>"
        )));
        BossBrew[] brews = BossBrew.values();
        for (int i = 0; i < brews.length && i < BREW_SLOTS.length; i++) {
            inventory.setItem(BREW_SLOTS[i], brewPreview(brews[i]));
        }
        inventory.setItem(BACK_SLOT, menuItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to /menu.</gray>")));
        player.openInventory(inventory);
    }

    private static boolean isFrameSlot(int slot, int size) {
        return slot < 9 || slot >= size - 9 || slot % 9 == 0 || slot % 9 == 8;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof BossPotionMenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return;
        }
        if (rawSlot == BACK_SLOT) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                player.closeInventory();
                player.performCommand("menu");
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof BossPotionMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossIngredientClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory() instanceof BrewerInventory brewer) || !(event.getWhoClicked() instanceof Player player)) return;
        ItemStack inserted = bossIngredientInsertedBy(event);
        if (inserted == null) return;
        if (!canBrewBossPotions(player)) {
            event.setCancelled(true);
            player.sendMessage(me.rique.smpcore.util.MessageUtil.warn("Vespera has not taught you how to stabilize boss materials yet."));
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BREWING_STAND_BREW, 0.5F, 0.55F);
            return;
        }
        authorizeBrewer(brewer, player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossIngredientDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory() instanceof BrewerInventory brewer) || !(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getRawSlots().contains(3) || bossMaterialId(event.getOldCursor()) == null) return;
        if (!canBrewBossPotions(player)) {
            event.setCancelled(true);
            player.sendMessage(me.rique.smpcore.util.MessageUtil.warn("Vespera has not taught you how to stabilize boss materials yet."));
            return;
        }
        authorizeBrewer(brewer, player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAutomatedBossIngredient(InventoryMoveItemEvent event) {
        if (event.getDestination() instanceof BrewerInventory && bossMaterialId(event.getItem()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBossBrew(BrewEvent event) {
        if (bossMaterialId(event.getContents().getIngredient()) == null) return;
        Player owner = authorizedBrewer(event.getContents());
        if (owner != null && canBrewBossPotions(owner)) return;
        event.setCancelled(true);
        if (owner != null) {
            owner.sendMessage(me.rique.smpcore.util.MessageUtil.warn("That boss brew was stopped because the Moonlit Thesis is still locked."));
        }
    }

    private void registerPotionMixes() {
        shutdown();
        for (BossBrew brew : BossBrew.values()) {
            for (String materialId : brew.materialIds()) {
                for (Material potionType : POTION_TYPES) {
                    NamespacedKey key = new NamespacedKey(plugin, "boss_brew_" + brew.id() + "_" + materialId + "_" + potionType.name().toLowerCase(Locale.ROOT));
                    ItemStack result = createBossPotion(brew, potionType);
                    RecipeChoice input = PotionMix.createPredicateChoice(item -> isAwkwardPotion(item, potionType));
                    RecipeChoice ingredient = PotionMix.createPredicateChoice(item -> matchesBossMaterial(item, materialId));
                    Bukkit.getPotionBrewer().removePotionMix(key);
                    Bukkit.getPotionBrewer().addPotionMix(new PotionMix(key, result, input, ingredient));
                    registeredMixes.add(key);
                }
            }
        }
    }

    private boolean isAwkwardPotion(ItemStack item, Material expectedType) {
        if (item == null || item.getType() != expectedType || !(item.getItemMeta() instanceof PotionMeta meta)) {
            return false;
        }
        if (meta.getPersistentDataContainer().has(keyBossPotionId, PersistentDataType.STRING)) {
            return false;
        }
        return meta.getBasePotionType() == PotionType.AWKWARD;
    }

    private boolean matchesBossMaterial(ItemStack item, String materialId) {
        return plugin.getSeasonRelicManager() != null && materialId.equals(plugin.getSeasonRelicManager().relicId(item));
    }

    private boolean canBrewBossPotions(Player player) {
        return player != null && plugin.getWitchManager() != null && plugin.getWitchManager().hasBossBrewingUnlocked(player);
    }

    private String bossMaterialId(ItemStack item) {
        if (item == null || item.getType().isAir() || plugin.getSeasonRelicManager() == null) return null;
        String relicId = plugin.getSeasonRelicManager().relicId(item);
        if (relicId == null) return null;
        for (BossBrew brew : BossBrew.values()) {
            if (brew.materialIds().contains(relicId)) return relicId;
        }
        return null;
    }

    private ItemStack bossIngredientInsertedBy(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (event.isShiftClick() && event.getClickedInventory() != null && event.getClickedInventory() != top) {
            return bossMaterialId(event.getCurrentItem()) == null ? null : event.getCurrentItem();
        }
        if (event.getRawSlot() != 3) return null;
        if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
            ItemStack item = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
            return bossMaterialId(item) == null ? null : item;
        }
        if (event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND) {
            ItemStack item = event.getWhoClicked().getInventory().getItemInOffHand();
            return bossMaterialId(item) == null ? null : item;
        }
        if (event.getAction() == InventoryAction.PLACE_ALL
            || event.getAction() == InventoryAction.PLACE_ONE
            || event.getAction() == InventoryAction.PLACE_SOME
            || event.getAction() == InventoryAction.SWAP_WITH_CURSOR) {
            return bossMaterialId(event.getCursor()) == null ? null : event.getCursor();
        }
        return null;
    }

    private void authorizeBrewer(BrewerInventory inventory, Player player) {
        BrewingStand stand = inventory == null ? null : inventory.getHolder();
        if (stand == null) return;
        stand.getPersistentDataContainer().set(keyAuthorizedBrewer, PersistentDataType.STRING, player.getUniqueId().toString());
        stand.update(true, false);
    }

    private Player authorizedBrewer(BrewerInventory inventory) {
        BrewingStand stand = inventory == null ? null : inventory.getHolder();
        if (stand == null) return null;
        String raw = stand.getPersistentDataContainer().get(keyAuthorizedBrewer, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return Bukkit.getPlayer(UUID.fromString(raw));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ItemStack createBossPotion(BossBrew brew, Material potionType) {
        ItemStack item = new ItemStack(potionType);
        ItemMeta rawMeta = item.getItemMeta();
        if (!(rawMeta instanceof PotionMeta meta)) {
            return item;
        }
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.EPIC, brew.displayName()));
        ItemModelUtil.apply(meta, "boss_brew_" + brew.id());
        meta.setBasePotionType(PotionType.AWKWARD);
        meta.setColor(brew.color());
        meta.clearCustomEffects();
        for (BrewEffect effect : brew.effects()) {
            meta.addCustomEffect(new PotionEffect(effect.type(), effect.durationTicks(), effect.amplifier(), false, true, true), true);
        }
        meta.lore(buildLore(meta, brew));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(keyBossPotionId, PersistentDataType.STRING, brew.id());
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> buildLore(ItemMeta meta, BossBrew brew) {
        List<String> effects = new ArrayList<>();
        for (BrewEffect effect : brew.effects()) {
            effects.add("<gray>• <white>" + effect.label() + "</white></gray>");
        }
        return CustomLoreUtil.buildStyledLore(
            meta,
            Material.POTION,
            "EPIC",
            "BREW",
            List.of(
                "<gray>Base: <white>Awkward Potion</white></gray>",
                "<gray>Ingredient: <white>" + ingredientNames(brew) + "</white></gray>"
            ),
            List.of(CustomLoreUtil.section("Effects", "Brew Effects", effects.toArray(String[]::new)))
        );
    }

    private String ingredientNames(BossBrew brew) {
        List<String> names = new ArrayList<>();
        for (String materialId : brew.materialIds()) {
            String name = plugin.getSeasonRelicManager() == null ? null : plugin.getSeasonRelicManager().displayNameFor(materialId);
            names.add(name == null || name.isBlank() ? prettyName(materialId) : name);
        }
        return String.join(" or ", names).replace("<", "\\<");
    }

    private ItemStack brewPreview(BossBrew brew) {
        ItemStack item = createBossPotion(brew, Material.POTION);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
        lore.add(Component.empty());
        lore.add(MM.deserialize("<yellow>Brew in a normal brewing stand.</yellow>"));
        meta.lore(CustomLoreUtil.normalizeLore(lore));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack menuItem(Material material, String name, List<String> lore) {
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

    private String prettyName(String id) {
        if (id == null || id.isBlank()) {
            return "Unknown";
        }
        String[] parts = id.split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            out.append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private enum BossBrew {
        SUNFORGED_ICHOR(
            "sunforged_ichor",
            "Cinderveil Ichor",
            Color.fromRGB(255, 155, 45),
            List.of("solar_ember"),
            List.of(
                new BrewEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 60 * 20, 0, "Fire Resistance I - 20:00"),
                new BrewEffect(PotionEffectType.SPEED, 8 * 60 * 20, 2, "Speed III - 8:00"),
                new BrewEffect(PotionEffectType.HASTE, 8 * 60 * 20, 2, "Haste III - 8:00"),
                new BrewEffect(PotionEffectType.STRENGTH, 8 * 60 * 20, 0, "Strength I - 8:00")
            )
        ),
        DOMINION_BLOOD(
            "dominion_blood",
            "Nocturne Blood",
            Color.fromRGB(190, 30, 55),
            List.of("crimson_rib", "sculk_heart"),
            List.of(
                new BrewEffect(PotionEffectType.STRENGTH, 6 * 60 * 20, 2, "Strength III - 6:00"),
                new BrewEffect(PotionEffectType.RESISTANCE, 8 * 60 * 20, 1, "Resistance II - 8:00"),
                new BrewEffect(PotionEffectType.ABSORPTION, 6 * 60 * 20, 3, "Absorption IV - 6:00"),
                new BrewEffect(PotionEffectType.REGENERATION, 2 * 60 * 20, 1, "Regeneration II - 2:00")
            )
        ),
        RIFT_DRAUGHT(
            "rift_draught",
            "Riftglass Draught",
            Color.fromRGB(170, 90, 255),
            List.of("rift_lens", "void_halo"),
            List.of(
                new BrewEffect(PotionEffectType.SPEED, 5 * 60 * 20, 3, "Speed IV - 5:00"),
                new BrewEffect(PotionEffectType.JUMP_BOOST, 5 * 60 * 20, 2, "Jump Boost III - 5:00"),
                new BrewEffect(PotionEffectType.SLOW_FALLING, 8 * 60 * 20, 0, "Slow Falling I - 8:00"),
                new BrewEffect(PotionEffectType.INVISIBILITY, 2 * 60 * 20, 0, "Invisibility I - 2:00")
            )
        ),
        ABYSSAL_TONIC(
            "abyssal_tonic",
            "Depthveil Tonic",
            Color.fromRGB(35, 180, 220),
            List.of("abyssal_pearl", "tideheart"),
            List.of(
                new BrewEffect(PotionEffectType.WATER_BREATHING, 30 * 60 * 20, 0, "Water Breathing I - 30:00"),
                new BrewEffect(PotionEffectType.CONDUIT_POWER, 10 * 60 * 20, 0, "Conduit Power I - 10:00"),
                new BrewEffect(PotionEffectType.DOLPHINS_GRACE, 8 * 60 * 20, 2, "Dolphin's Grace III - 8:00"),
                new BrewEffect(PotionEffectType.NIGHT_VISION, 15 * 60 * 20, 0, "Night Vision I - 15:00")
            )
        ),
        VERDANT_ELIXIR(
            "verdant_elixir",
            "Briarheart Elixir",
            Color.fromRGB(70, 190, 90),
            List.of("living_bark", "verdant_heart"),
            List.of(
                new BrewEffect(PotionEffectType.REGENERATION, 3 * 60 * 20, 2, "Regeneration III - 3:00"),
                new BrewEffect(PotionEffectType.ABSORPTION, 8 * 60 * 20, 2, "Absorption III - 8:00"),
                new BrewEffect(PotionEffectType.SATURATION, 20 * 20, 0, "Saturation I - 0:20")
            )
        ),
        SAINTS_RESOLVE(
            "saints_resolve",
            "Confessor's Resolve",
            Color.fromRGB(210, 210, 175),
            List.of("gilded_skull", "oathbound_plate", "titan_gear", "saint_alloy"),
            List.of(
                new BrewEffect(PotionEffectType.RESISTANCE, 6 * 60 * 20, 2, "Resistance III - 6:00"),
                new BrewEffect(PotionEffectType.ABSORPTION, 8 * 60 * 20, 3, "Absorption IV - 8:00"),
                new BrewEffect(PotionEffectType.HEALTH_BOOST, 8 * 60 * 20, 1, "Health Boost II - 8:00"),
                new BrewEffect(PotionEffectType.REGENERATION, 2 * 60 * 20, 1, "Regeneration II - 2:00")
            )
        ),
        WIDOWSTEP_VIAL(
            "widowstep_vial",
            "Gloamstep Vial",
            Color.fromRGB(85, 180, 90),
            List.of("widow_silk"),
            List.of(
                new BrewEffect(PotionEffectType.INVISIBILITY, 5 * 60 * 20, 0, "Invisibility I - 5:00"),
                new BrewEffect(PotionEffectType.SPEED, 5 * 60 * 20, 2, "Speed III - 5:00"),
                new BrewEffect(PotionEffectType.JUMP_BOOST, 5 * 60 * 20, 2, "Jump Boost III - 5:00"),
                new BrewEffect(PotionEffectType.STRENGTH, 3 * 60 * 20, 0, "Strength I - 3:00")
            )
        );

        private final String id;
        private final String displayName;
        private final Color color;
        private final List<String> materialIds;
        private final List<BrewEffect> effects;

        BossBrew(String id, String name, Color color, List<String> materialIds, List<BrewEffect> effects) {
            this.id = id;
            this.displayName = name;
            this.color = color;
            this.materialIds = List.copyOf(materialIds);
            this.effects = List.copyOf(effects);
        }

        private String id() {
            return id;
        }

        private String displayName() {
            return displayName;
        }

        private Color color() {
            return color;
        }

        private List<String> materialIds() {
            return materialIds;
        }

        private List<BrewEffect> effects() {
            return effects;
        }
    }

    private record BrewEffect(PotionEffectType type, int durationTicks, int amplifier, String label) {
    }

    private record BossPotionMenuHolder() implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
