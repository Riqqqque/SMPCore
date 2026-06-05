package me.rique.smpcore.item;

import io.papermc.paper.potion.PotionMix;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
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
    private final List<NamespacedKey> registeredMixes = new ArrayList<>();

    public BossPotionListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyBossPotionId = new NamespacedKey(plugin, "boss_potion_id");
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
        Inventory inventory = Bukkit.createInventory(
            new BossPotionMenuHolder(),
            MENU_SIZE,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#38bdf8:#fb7185><bold>Boss Brews</bold></gradient>"), "Boss Brews")
        );
        ItemStack filler = menuItem(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> </dark_gray>", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) {
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BossPotionMenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory().getType() == InventoryType.PLAYER) {
            return;
        }
        if (event.getRawSlot() == BACK_SLOT) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> player.performCommand("menu"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BossPotionMenuHolder) {
            event.setCancelled(true);
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

    private ItemStack createBossPotion(BossBrew brew, Material potionType) {
        ItemStack item = new ItemStack(potionType);
        ItemMeta rawMeta = item.getItemMeta();
        if (!(rawMeta instanceof PotionMeta meta)) {
            return item;
        }
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.EPIC, brew.displayName()));
        meta.setBasePotionType(PotionType.AWKWARD);
        meta.setColor(brew.color());
        meta.clearCustomEffects();
        for (BrewEffect effect : brew.effects()) {
            meta.addCustomEffect(new PotionEffect(effect.type(), effect.durationTicks(), effect.amplifier(), false, true, true), true);
        }
        meta.lore(buildLore(brew));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(keyBossPotionId, PersistentDataType.STRING, brew.id());
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> buildLore(BossBrew brew) {
        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<dark_gray>EPIC BREW</dark_gray>"));
        lore.add(Component.empty());
        lore.add(MM.deserialize("<gray>Brew with:</gray> <white>Awkward Potion</white>"));
        lore.add(MM.deserialize("<gray>Ingredient:</gray> <white>" + ingredientNames(brew) + "</white>"));
        lore.add(Component.empty());
        lore.add(MM.deserialize("<gold><bold>Effects</bold></gold>"));
        for (BrewEffect effect : brew.effects()) {
            lore.add(MM.deserialize("<gray>- <white>" + effect.label() + "</white></gray>"));
        }
        return lore;
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
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack menuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(MM.deserialize(name));
        meta.lore(lore.stream().map(MM::deserialize).toList());
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
            "Sunforged Ichor",
            Color.fromRGB(255, 155, 45),
            List.of("solar_ember"),
            List.of(
                new BrewEffect(PotionEffectType.FIRE_RESISTANCE, 8 * 60 * 20, 0, "Fire Resistance I - 8:00"),
                new BrewEffect(PotionEffectType.SPEED, 3 * 60 * 20, 1, "Speed II - 3:00"),
                new BrewEffect(PotionEffectType.HASTE, 3 * 60 * 20, 1, "Haste II - 3:00")
            )
        ),
        DOMINION_BLOOD(
            "dominion_blood",
            "Dominion Blood",
            Color.fromRGB(190, 30, 55),
            List.of("crimson_rib", "sculk_heart"),
            List.of(
                new BrewEffect(PotionEffectType.STRENGTH, 3 * 60 * 20, 1, "Strength II - 3:00"),
                new BrewEffect(PotionEffectType.RESISTANCE, 3 * 60 * 20, 1, "Resistance II - 3:00"),
                new BrewEffect(PotionEffectType.ABSORPTION, 2 * 60 * 20, 1, "Absorption II - 2:00")
            )
        ),
        RIFT_DRAUGHT(
            "rift_draught",
            "Rift Draught",
            Color.fromRGB(170, 90, 255),
            List.of("rift_lens", "void_halo"),
            List.of(
                new BrewEffect(PotionEffectType.SPEED, 150 * 20, 2, "Speed III - 2:30"),
                new BrewEffect(PotionEffectType.JUMP_BOOST, 150 * 20, 1, "Jump Boost II - 2:30"),
                new BrewEffect(PotionEffectType.SLOW_FALLING, 150 * 20, 0, "Slow Falling I - 2:30")
            )
        ),
        ABYSSAL_TONIC(
            "abyssal_tonic",
            "Abyssal Tonic",
            Color.fromRGB(35, 180, 220),
            List.of("abyssal_pearl", "tideheart"),
            List.of(
                new BrewEffect(PotionEffectType.WATER_BREATHING, 8 * 60 * 20, 0, "Water Breathing I - 8:00"),
                new BrewEffect(PotionEffectType.CONDUIT_POWER, 4 * 60 * 20, 0, "Conduit Power I - 4:00"),
                new BrewEffect(PotionEffectType.DOLPHINS_GRACE, 3 * 60 * 20, 1, "Dolphin's Grace II - 3:00")
            )
        ),
        VERDANT_ELIXIR(
            "verdant_elixir",
            "Verdant Elixir",
            Color.fromRGB(70, 190, 90),
            List.of("living_bark", "verdant_heart"),
            List.of(
                new BrewEffect(PotionEffectType.REGENERATION, 60 * 20, 1, "Regeneration II - 1:00"),
                new BrewEffect(PotionEffectType.ABSORPTION, 3 * 60 * 20, 1, "Absorption II - 3:00")
            )
        ),
        SAINTS_RESOLVE(
            "saints_resolve",
            "Saint's Resolve",
            Color.fromRGB(210, 210, 175),
            List.of("gilded_skull", "oathbound_plate", "titan_gear", "saint_alloy"),
            List.of(
                new BrewEffect(PotionEffectType.RESISTANCE, 4 * 60 * 20, 1, "Resistance II - 4:00"),
                new BrewEffect(PotionEffectType.ABSORPTION, 3 * 60 * 20, 2, "Absorption III - 3:00"),
                new BrewEffect(PotionEffectType.HEALTH_BOOST, 4 * 60 * 20, 0, "Health Boost I - 4:00")
            )
        ),
        WIDOWSTEP_VIAL(
            "widowstep_vial",
            "Widowstep Vial",
            Color.fromRGB(85, 180, 90),
            List.of("widow_silk"),
            List.of(
                new BrewEffect(PotionEffectType.INVISIBILITY, 2 * 60 * 20, 0, "Invisibility I - 2:00"),
                new BrewEffect(PotionEffectType.SPEED, 2 * 60 * 20, 1, "Speed II - 2:00"),
                new BrewEffect(PotionEffectType.JUMP_BOOST, 2 * 60 * 20, 1, "Jump Boost II - 2:00")
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

    private record BossPotionMenuHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, MENU_SIZE, Component.empty());
        }
    }
}
