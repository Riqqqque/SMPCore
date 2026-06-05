package me.rique.smpcore.legendary;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MythicForgeListener implements Listener {

    public static final String MYTHIC_FORGE_ITEM_ID = "mythic_forge";
    public static final String ASCENDANT_CORE_ITEM_ID = "ascendant_core";

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Component MENU_TITLE =
        MM.deserialize("<gradient:#8a2be2:#ff4df0><bold>Mythic Forge</bold></gradient>");
    private static final int MENU_SIZE = 27;
    private static final int STATUS_SLOT = 4;
    private static final int LEFT_SLOT = 10;
    private static final int CATALYST_SLOT = 13;
    private static final int RIGHT_SLOT = 16;
    private static final int RESULT_SLOT = 22;

    private static final List<FusionRecipeView> FUSION_RECIPES = List.of(
        new FusionRecipeView("emerald_blade", "divine_axe_rhitta", "midas_sword"),
        new FusionRecipeView("wither_blade", "executioner_blade", "reapers_scythe"),
        new FusionRecipeView("blink_dagger", "hypnosis_staff", "shadow_blade"),
        new FusionRecipeView("hard_hitter", "warden_blade", "strength_sword"),
        new FusionRecipeView("ender_sword", "chrono_sword", "paradox_reaver"),
        new FusionRecipeView("frost_scythe", "trident_of_percy", "tempest_trident"),
        new FusionRecipeView("thors_hammer", "dash_mace", "stormfall_maul")
    );

    private final SMPCore plugin;
    private final NamespacedKey keyMythicForgeItem;
    private final NamespacedKey keyMythicForgeEntity;
    private final NamespacedKey keyMythicForgeHologram;
    private final NamespacedKey keyMythicForgeHologramOwner;
    private final NamespacedKey keyAscendantCore;
    private final NamespacedKey mythicForgeRecipeKey;
    private final NamespacedKey ascendantCoreRecipeKey;
    private final Map<UUID, UUID> forgeHolograms = new HashMap<>();
    private BukkitTask hologramTask;

    public MythicForgeListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyMythicForgeItem = new NamespacedKey(plugin, "mythic_forge_item");
        this.keyMythicForgeEntity = new NamespacedKey(plugin, "mythic_forge_entity");
        this.keyMythicForgeHologram = new NamespacedKey(plugin, "mythic_forge_hologram");
        this.keyMythicForgeHologramOwner = new NamespacedKey(plugin, "mythic_forge_hologram_owner");
        this.keyAscendantCore = new NamespacedKey(plugin, "ascendant_core");
        this.mythicForgeRecipeKey = new NamespacedKey(plugin, "mythic_forge_recipe");
        this.ascendantCoreRecipeKey = new NamespacedKey(plugin, "ascendant_core_recipe");
        registerRecipes();
    }

    public void reloadConfig() {
        registerRecipes();
        start();
    }

    public void start() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.discoverRecipe(mythicForgeRecipeKey);
                player.discoverRecipe(ascendantCoreRecipeKey);
            }
        });
        if (hologramTask == null) {
            hologramTask = Bukkit.getScheduler().runTaskTimer(plugin, this::syncForgeHolograms, 1L, 40L);
        }
    }

    public void shutdown() {
        if (hologramTask != null) {
            hologramTask.cancel();
            hologramTask = null;
        }
        for (UUID hologramId : new ArrayList<>(forgeHolograms.values())) {
            Entity hologram = Bukkit.getEntity(hologramId);
            if (hologram != null) {
                hologram.remove();
            }
        }
        forgeHolograms.clear();
        removeAllKnownForgeHolograms();
    }

    public boolean isCustomRecipeItemId(String itemId) {
        return MYTHIC_FORGE_ITEM_ID.equals(itemId) || ASCENDANT_CORE_ITEM_ID.equals(itemId);
    }

    public String displayNameFor(String itemId) {
        return switch (itemId) {
            case MYTHIC_FORGE_ITEM_ID -> "Mythic Forge";
            case ASCENDANT_CORE_ITEM_ID -> "Ascendant Core";
            default -> null;
        };
    }

    public ItemStack createCustomItem(String itemId) {
        return switch (itemId) {
            case MYTHIC_FORGE_ITEM_ID -> createMythicForgeItem();
            case ASCENDANT_CORE_ITEM_ID -> createAscendantCoreItem();
            default -> new ItemStack(Material.BARRIER);
        };
    }

    public ItemStack[] recipeMatrix(String itemId) {
        return switch (itemId) {
            case MYTHIC_FORGE_ITEM_ID -> mythicForgeRecipeMatrix();
            case ASCENDANT_CORE_ITEM_ID -> ascendantCoreRecipeMatrix();
            default -> new ItemStack[9];
        };
    }

    public ItemStack createMythicForgeItem() {
        ItemStack item = new ItemStack(Material.END_CRYSTAL);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.MYTHIC, "Mythic Forge"));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.END_CRYSTAL,
            CustomLoreUtil.Rarity.MYTHIC.label(),
            "FORGE",
            List.of("<gray>Place it to raise a stable fusion nexus.</gray>"),
            List.of(CustomLoreUtil.section(
                "Use",
                "Forge Mythics",
                "<gray>Combine <white>2 compatible legendaries</white></gray>",
                "<gray>with an <white>Ascendant Core</white>.</gray>",
                "<gray>Crafted from End relics and voidstone.</gray>",
                "<dark_gray>Right-click a placed forge to begin.</dark_gray>"
            ))
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(keyMythicForgeItem, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createAscendantCoreItem() {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.LEGENDARY, "Ascendant Core"));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.AMETHYST_SHARD,
            CustomLoreUtil.Rarity.LEGENDARY.label(),
            "CATALYST",
            List.of("<gray>The catalyst that binds two legends into one myth.</gray>"),
            List.of(CustomLoreUtil.section(
                "Use",
                "Mythic Fusion",
                "<gray>Consumed by the <white>Mythic Forge</white> on use.</gray>",
                "<gray>Refined from End relics and a <white>Nether Star</white>.</gray>"
            ))
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(keyAscendantCore, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isMythicForgeItemStack(ItemStack item) {
        return isMythicForgeItem(item);
    }

    public boolean isAscendantCoreItem(ItemStack item) {
        return isAscendantCore(item);
    }

    public ItemStack[] mythicForgeRecipeMatrix() {
        ItemStack[] matrix = new ItemStack[9];
        matrix[0] = new ItemStack(Material.END_CRYSTAL);
        matrix[1] = new ItemStack(Material.ECHO_SHARD);
        matrix[2] = new ItemStack(Material.END_CRYSTAL);
        matrix[3] = new ItemStack(Material.CRYING_OBSIDIAN);
        matrix[4] = new ItemStack(Material.NETHER_STAR);
        matrix[5] = new ItemStack(Material.CRYING_OBSIDIAN);
        matrix[6] = new ItemStack(Material.LODESTONE);
        matrix[7] = new ItemStack(Material.RESPAWN_ANCHOR);
        matrix[8] = new ItemStack(Material.LODESTONE);
        return matrix;
    }

    public ItemStack[] ascendantCoreRecipeMatrix() {
        ItemStack[] matrix = new ItemStack[9];
        matrix[0] = new ItemStack(Material.ECHO_SHARD);
        matrix[1] = new ItemStack(Material.AMETHYST_SHARD);
        matrix[2] = new ItemStack(Material.ECHO_SHARD);
        matrix[3] = new ItemStack(Material.END_CRYSTAL);
        matrix[4] = new ItemStack(Material.NETHER_STAR);
        matrix[5] = new ItemStack(Material.END_CRYSTAL);
        matrix[6] = new ItemStack(Material.CRYING_OBSIDIAN);
        matrix[7] = new ItemStack(Material.DRAGON_BREATH);
        matrix[8] = new ItemStack(Material.CRYING_OBSIDIAN);
        return matrix;
    }

    public List<FusionRecipeView> fusionRecipes() {
        return FUSION_RECIPES;
    }

    public FusionRecipeView fusionRecipeForOutput(String outputId) {
        if (outputId == null || outputId.isBlank()) {
            return null;
        }
        for (FusionRecipeView recipe : FUSION_RECIPES) {
            if (recipe.outputId().equalsIgnoreCase(outputId)) {
                return recipe;
            }
        }
        return null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipe(mythicForgeRecipeKey);
        event.getPlayer().discoverRecipe(ascendantCoreRecipeKey);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlaceForge(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!isMythicForgeItem(event.getItem())) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null || event.getBlockFace() != BlockFace.UP) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.error("Place the Mythic Forge on top of a solid block."));
            return;
        }

        Block base = clicked.getRelative(BlockFace.UP);
        Block head = base.getRelative(BlockFace.UP);
        if (!clicked.getType().isSolid() || !base.isEmpty() || !head.isEmpty()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.error("The Mythic Forge needs two clear blocks above the base."));
            return;
        }

        event.setCancelled(true);
        Location spawnLocation = clicked.getLocation().add(0.5, 1.0, 0.5);
        EnderCrystal crystal = clicked.getWorld().spawn(spawnLocation, EnderCrystal.class, entity -> {
            entity.setShowingBottom(false);
            entity.setInvulnerable(true);
            entity.setPersistent(true);
            entity.setBeamTarget(null);
            entity.getPersistentDataContainer().set(keyMythicForgeEntity, PersistentDataType.BYTE, (byte) 1);
        });
        ensureForgeHologram(crystal);
        updateForgeHologram(crystal);

        if (event.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE) {
            consumeSingleItem(event.getPlayer(), event.getHand(), event.getItem());
        }
        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.7f);
    }

    private void syncForgeHolograms() {
        Set<UUID> liveForges = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            for (EnderCrystal crystal : world.getEntitiesByClass(EnderCrystal.class)) {
                if (!isMythicForgeEntity(crystal) || !crystal.isValid()) {
                    continue;
                }
                liveForges.add(crystal.getUniqueId());
                updateForgeHologram(crystal);
            }
        }

        for (UUID forgeId : new ArrayList<>(forgeHolograms.keySet())) {
            if (!liveForges.contains(forgeId)) {
                destroyForgeHologram(forgeId);
            }
        }
        removeDuplicateOrphanForgeHolograms(liveForges);
    }

    private void ensureForgeHologram(EnderCrystal crystal) {
        UUID forgeId = crystal.getUniqueId();
        UUID hologramId = forgeHolograms.get(forgeId);
        Entity existing = hologramId == null ? null : Bukkit.getEntity(hologramId);
        if (existing instanceof TextDisplay display && display.isValid()) {
            return;
        }
        if (existing != null) {
            existing.remove();
        }
        forgeHolograms.remove(forgeId);

        TextDisplay display = crystal.getWorld().spawn(forgeHologramLocation(crystal), TextDisplay.class, textDisplay -> {
            textDisplay.setPersistent(false);
            textDisplay.setGravity(false);
            textDisplay.setBillboard(Display.Billboard.CENTER);
            textDisplay.setSeeThrough(true);
            textDisplay.setShadowed(true);
            textDisplay.setDefaultBackground(false);
            textDisplay.setBackgroundColor(Color.fromARGB(45, 34, 0, 54));
            textDisplay.setTextOpacity((byte) 255);
            textDisplay.setLineWidth(120);
            textDisplay.setViewRange(42.0f);
            textDisplay.setGlowing(true);
            textDisplay.setGlowColorOverride(Color.fromRGB(190, 80, 255));
            textDisplay.getPersistentDataContainer().set(keyMythicForgeHologram, PersistentDataType.BYTE, (byte) 1);
            textDisplay.getPersistentDataContainer().set(keyMythicForgeHologramOwner, PersistentDataType.STRING, forgeId.toString());
        });
        forgeHolograms.put(forgeId, display.getUniqueId());
    }

    private void updateForgeHologram(EnderCrystal crystal) {
        ensureForgeHologram(crystal);
        UUID hologramId = forgeHolograms.get(crystal.getUniqueId());
        Entity existing = hologramId == null ? null : Bukkit.getEntity(hologramId);
        if (!(existing instanceof TextDisplay display) || !display.isValid()) {
            return;
        }

        display.teleport(forgeHologramLocation(crystal));
        display.text(MM.deserialize(
            CustomLoreUtil.displayNameTag(CustomLoreUtil.Rarity.MYTHIC, "Mythic Forge")
                + "\n<gray>Right-click to fuse relics</gray>"
        ));
    }

    private Location forgeHologramLocation(EnderCrystal crystal) {
        return crystal.getLocation().clone().add(0.0, 2.15, 0.0);
    }

    private void destroyForgeHologram(UUID forgeId) {
        UUID hologramId = forgeHolograms.remove(forgeId);
        Entity hologram = hologramId == null ? null : Bukkit.getEntity(hologramId);
        if (hologram != null) {
            hologram.remove();
        }
    }

    private void removeDuplicateOrphanForgeHolograms(Set<UUID> liveForges) {
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (!isMythicForgeHologram(display)) {
                    continue;
                }
                UUID ownerId = mythicForgeHologramOwner(display);
                UUID trackedId = ownerId == null ? null : forgeHolograms.get(ownerId);
                if (ownerId == null || !liveForges.contains(ownerId) || trackedId == null || !trackedId.equals(display.getUniqueId())) {
                    display.remove();
                }
            }
        }
    }

    private void removeAllKnownForgeHolograms() {
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (isMythicForgeHologram(display)) {
                    display.remove();
                }
            }
        }
    }

    private boolean isMythicForgeHologram(Entity entity) {
        if (!(entity instanceof TextDisplay display)) {
            return false;
        }
        Byte tagged = display.getPersistentDataContainer().get(keyMythicForgeHologram, PersistentDataType.BYTE);
        return tagged != null && tagged == (byte) 1;
    }

    private UUID mythicForgeHologramOwner(TextDisplay display) {
        String owner = display.getPersistentDataContainer().get(keyMythicForgeHologramOwner, PersistentDataType.STRING);
        if (owner == null || owner.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(owner);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onForgeInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof EnderCrystal crystal) || !isMythicForgeEntity(crystal)) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (player.isSneaking()
            && player.hasPermission("smpcore.customitem.admin")
            && (hand == null || hand.getType() == Material.AIR)) {
            destroyForgeHologram(crystal.getUniqueId());
            crystal.remove();
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(createMythicForgeItem());
            leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            player.sendMessage(MessageUtil.success("Recovered the <white>Mythic Forge</white>."));
            return;
        }

        openForgeMenu(player, crystal.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onForgeDamage(EntityDamageEvent event) {
        if (isMythicForgeEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onForgePrime(ExplosionPrimeEvent event) {
        if (isMythicForgeEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof MythicForgeMenuHolder holder)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() == top) {
            event.setCancelled(true);
            if (event.getSlot() == RESULT_SLOT) {
                attemptFusion(player, top, holder.forgeId());
                return;
            }
            if (isInputSlot(event.getSlot())) {
                handleInputSlotClick(event, top);
                renderMenu(top);
            }
            return;
        }

        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return;
        }

        if (event.isShiftClick()) {
            ItemStack current = event.getCurrentItem();
            int targetSlot = findShiftTargetSlot(top, current);
            if (targetSlot >= 0) {
                event.setCancelled(true);
                moveSingleItemToForge(top, event.getClickedInventory(), event.getSlot(), targetSlot);
                renderMenu(top);
            }
            return;
        }

        if (event.getClick() == ClickType.DOUBLE_CLICK || event.getClick() == ClickType.NUMBER_KEY) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMenuDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MythicForgeMenuHolder)) {
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMenuClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof MythicForgeMenuHolder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        returnInput(player, event.getInventory(), LEFT_SLOT);
        returnInput(player, event.getInventory(), CATALYST_SLOT);
        returnInput(player, event.getInventory(), RIGHT_SLOT);

        LegendaryListener legendary = plugin.getLegendaryListener();
        if (legendary != null) {
            legendary.resyncLegendaryOwnership(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Inventory top = event.getPlayer().getOpenInventory().getTopInventory();
        if (!(top.getHolder() instanceof MythicForgeMenuHolder)) {
            return;
        }

        returnInput(event.getPlayer(), top, LEFT_SLOT);
        returnInput(event.getPlayer(), top, CATALYST_SLOT);
        returnInput(event.getPlayer(), top, RIGHT_SLOT);
    }

    private void registerRecipes() {
        Bukkit.removeRecipe(mythicForgeRecipeKey);
        Bukkit.removeRecipe(ascendantCoreRecipeKey);

        ShapedRecipe forgeRecipe = new ShapedRecipe(mythicForgeRecipeKey, createMythicForgeItem());
        forgeRecipe.shape("ECE", "ONO", "LRL");
        forgeRecipe.setIngredient('E', Material.END_CRYSTAL);
        forgeRecipe.setIngredient('C', Material.ECHO_SHARD);
        forgeRecipe.setIngredient('O', Material.CRYING_OBSIDIAN);
        forgeRecipe.setIngredient('N', Material.NETHER_STAR);
        forgeRecipe.setIngredient('L', Material.LODESTONE);
        forgeRecipe.setIngredient('R', Material.RESPAWN_ANCHOR);
        Bukkit.addRecipe(forgeRecipe);

        ShapedRecipe recipe = new ShapedRecipe(ascendantCoreRecipeKey, createAscendantCoreItem());
        recipe.shape("EAE", "CNC", "ODO");
        recipe.setIngredient('E', Material.ECHO_SHARD);
        recipe.setIngredient('A', Material.AMETHYST_SHARD);
        recipe.setIngredient('C', Material.END_CRYSTAL);
        recipe.setIngredient('N', Material.NETHER_STAR);
        recipe.setIngredient('O', Material.CRYING_OBSIDIAN);
        recipe.setIngredient('D', Material.DRAGON_BREATH);
        Bukkit.addRecipe(recipe);
    }

    private void openForgeMenu(Player player, UUID forgeId) {
        Inventory inventory = Bukkit.createInventory(
            new MythicForgeMenuHolder(forgeId),
            MENU_SIZE,
            BedrockCompat.menuTitle(player, MENU_TITLE, "Mythic Forge")
        );
        renderMenu(inventory);
        player.openInventory(inventory);
    }

    private void renderMenu(Inventory inventory) {
        ItemStack left = inventory.getItem(LEFT_SLOT);
        ItemStack catalyst = inventory.getItem(CATALYST_SLOT);
        ItemStack right = inventory.getItem(RIGHT_SLOT);

        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot == LEFT_SLOT || slot == CATALYST_SLOT || slot == RIGHT_SLOT) {
                inventory.setItem(slot, null);
                continue;
            }
            inventory.setItem(slot, filler);
        }

        inventory.setItem(LEFT_SLOT, left);
        inventory.setItem(CATALYST_SLOT, catalyst);
        inventory.setItem(RIGHT_SLOT, right);

        FusionRecipeView recipe = matchingRecipe(left, right);
        inventory.setItem(STATUS_SLOT, createStatusItem(recipe, catalyst));
        inventory.setItem(RESULT_SLOT, createResultItem(recipe, catalyst));
    }

    private ItemStack createStatusItem(FusionRecipeView recipe, ItemStack catalyst) {
        if (recipe == null) {
            return createGuiItem(
                Material.ENDER_EYE,
                "<light_purple><bold>Forge Alignment</bold></light_purple>",
                List.of(
                    "<gray>Insert <white>2 compatible legendaries</white></gray>",
                    "<gray>and an <white>Ascendant Core</white> to forge a mythic.</gray>"
                )
            );
        }

        LegendaryListener legendary = plugin.getLegendaryListener();
        String outputName = legendary == null ? recipe.outputId() : legendary.displayNameForLegendary(recipe.outputId());
        if (!isAscendantCore(catalyst)) {
            return createGuiItem(
                Material.AMETHYST_SHARD,
                "<gold><bold>Ascendant Core Needed</bold></gold>",
                List.of(
                    "<gray>This pairing is valid for</gray>",
                    "<gray><white>" + outputName + "</white>.</gray>",
                    "<gray>Add an <white>Ascendant Core</white> to complete the forge.</gray>"
                )
            );
        }

        LegendaryAltarManager altarManager = plugin.getLegendaryAltarManager();
        if (altarManager != null && altarManager.isLegendaryClaimed(recipe.outputId())) {
            return createGuiItem(
                Material.BARRIER,
                "<red><bold>Mythic Limit Reached</bold></red>",
                List.of("<gray><white>" + outputName + "</white> is already at its server limit.</gray>")
            );
        }

        return createGuiItem(
            Material.ENCHANTED_BOOK,
            "<green><bold>Forge Ready</bold></green>",
            List.of(
                "<gray>Output:</gray> <white>" + outputName + "</white>",
                "<gray>Click the result slot to forge it.</gray>",
                "<dark_gray>Use /mythics to inspect fusion rewards.</dark_gray>"
            )
        );
    }

    private ItemStack createResultItem(FusionRecipeView recipe, ItemStack catalyst) {
        if (recipe == null) {
            return createGuiItem(Material.BARRIER, "<red>No Matching Fusion</red>", List.of("<gray>Try a different legendary pairing.</gray>"));
        }

        LegendaryListener legendary = plugin.getLegendaryListener();
        if (legendary == null) {
            return createGuiItem(Material.BARRIER, "<red>Unavailable</red>", List.of());
        }

        String outputName = legendary.displayNameForLegendary(recipe.outputId());
        if (outputName == null || outputName.isBlank()) {
            return createGuiItem(Material.BARRIER, "<red>Unavailable</red>", List.of());
        }

        LegendaryAltarManager altarManager = plugin.getLegendaryAltarManager();
        if (altarManager != null && altarManager.isLegendaryClaimed(recipe.outputId())) {
            return createGuiItem(
                Material.BARRIER,
                "<red><bold>Mythic Limit Reached</bold></red>",
                List.of("<gray><white>" + outputName + "</white> is already at its server limit.</gray>")
            );
        }

        if (!isAscendantCore(catalyst)) {
            return createGuiItem(
                Material.AMETHYST_SHARD,
                "<gold><bold>Waiting On Catalyst</bold></gold>",
                List.of(
                    "<gray>Aligned Output:</gray> <white>" + outputName + "</white>",
                    "<gray>Add an <white>Ascendant Core</white> in the center slot.</gray>",
                    "<dark_gray>Use /mythics to view fusion details.</dark_gray>"
                )
            );
        }

        return createGuiItem(
            Material.NETHER_STAR,
            "<gradient:#ff4df0:#ffb000><bold>Forge Mythic</bold></gradient>",
            List.of(
                "<gray>Aligned Output:</gray> <white>" + outputName + "</white>",
                "<gray>Consumes both legendaries and the Ascendant Core.</gray>",
                "<dark_gray>Click to forge</dark_gray>"
            )
        );
    }

    private void handleInputSlotClick(InventoryClickEvent event, Inventory top) {
        int slot = event.getSlot();
        ItemStack cursor = event.getCursor();
        ItemStack current = top.getItem(slot);

        if (cursor == null || cursor.getType() == Material.AIR) {
            if (current == null || current.getType() == Material.AIR) {
                return;
            }
            top.setItem(slot, null);
            event.getWhoClicked().setItemOnCursor(current);
            return;
        }

        if (!acceptsItem(slot, cursor)) {
            return;
        }
        if (current != null && current.getType() != Material.AIR) {
            return;
        }

        ItemStack placed = cursor.clone();
        placed.setAmount(1);
        top.setItem(slot, placed);

        if (cursor.getAmount() <= 1) {
            event.getWhoClicked().setItemOnCursor(null);
        } else {
            cursor.setAmount(cursor.getAmount() - 1);
            event.getWhoClicked().setItemOnCursor(cursor);
        }
    }

    private int findShiftTargetSlot(Inventory top, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return -1;
        }
        if (isAscendantCore(item)) {
            return isEmpty(top.getItem(CATALYST_SLOT)) ? CATALYST_SLOT : -1;
        }
        if (!isAllowedFusionInput(item)) {
            return -1;
        }
        if (isEmpty(top.getItem(LEFT_SLOT))) {
            return LEFT_SLOT;
        }
        if (isEmpty(top.getItem(RIGHT_SLOT))) {
            return RIGHT_SLOT;
        }
        return -1;
    }

    private void moveSingleItemToForge(Inventory top, Inventory bottom, int bottomSlot, int targetSlot) {
        ItemStack current = bottom.getItem(bottomSlot);
        if (current == null || current.getType() == Material.AIR || !isEmpty(top.getItem(targetSlot))) {
            return;
        }

        ItemStack moved = current.clone();
        moved.setAmount(1);
        top.setItem(targetSlot, moved);

        if (current.getAmount() <= 1) {
            bottom.setItem(bottomSlot, null);
        } else {
            current.setAmount(current.getAmount() - 1);
            bottom.setItem(bottomSlot, current);
        }
    }

    private void attemptFusion(Player player, Inventory inventory, UUID forgeId) {
        ItemStack left = inventory.getItem(LEFT_SLOT);
        ItemStack catalyst = inventory.getItem(CATALYST_SLOT);
        ItemStack right = inventory.getItem(RIGHT_SLOT);
        FusionRecipeView recipe = matchingRecipe(left, right);

        if (recipe == null) {
            player.sendMessage(MessageUtil.error("That legendary pairing does not resonate with the forge."));
            return;
        }
        if (!isAscendantCore(catalyst)) {
            player.sendMessage(MessageUtil.error("You need an <white>Ascendant Core</white> to complete the fusion."));
            return;
        }

        LegendaryListener legendary = plugin.getLegendaryListener();
        LegendaryAltarManager altarManager = plugin.getLegendaryAltarManager();
        if (legendary == null || altarManager == null) {
            player.sendMessage(MessageUtil.error("Legendary systems are not ready yet."));
            return;
        }
        Entity forgeEntity = forgeId == null ? null : Bukkit.getEntity(forgeId);
        if (!(forgeEntity instanceof EnderCrystal crystal) || !crystal.isValid() || !isMythicForgeEntity(crystal)) {
            player.sendMessage(MessageUtil.error("This Mythic Forge is no longer stable."));
            player.closeInventory();
            return;
        }
        if (altarManager.isLegendaryClaimed(recipe.outputId())) {
            player.sendMessage(MessageUtil.error(
                "<white>" + legendary.displayNameForLegendary(recipe.outputId()) + "</white> is already at its server limit."
            ));
            renderMenu(inventory);
            return;
        }

        ItemStack reward = legendary.createLegendaryById(recipe.outputId());
        if (reward == null) {
            player.sendMessage(MessageUtil.error("That mythic output is not available right now."));
            return;
        }
        if (plugin.getItemAuditManager() != null) {
            plugin.getItemAuditManager().recordKnownAcquisition(
                player,
                reward,
                "mythic_forge",
                "Forged at the Mythic Forge from " + recipe.leftId() + " + " + recipe.rightId() + "."
            );
        }

        inventory.setItem(LEFT_SLOT, null);
        inventory.setItem(CATALYST_SLOT, null);
        inventory.setItem(RIGHT_SLOT, null);

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        legendary.resyncLegendaryOwnership(player);

        String outputName = legendary.displayNameForLegendary(recipe.outputId());
        Bukkit.broadcast(MessageUtil.prefixedRaw(
            "<light_purple><white>" + player.getName() + "</white> forged <white>" + outputName + "</white> at the Mythic Forge.</light_purple>"
        ));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.15f);
        renderMenu(inventory);
    }

    private void returnInput(Player player, Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        inventory.setItem(slot, null);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private FusionRecipeView matchingRecipe(ItemStack left, ItemStack right) {
        LegendaryListener legendary = plugin.getLegendaryListener();
        if (legendary == null) {
            return null;
        }
        String leftId = legendary.normalizeLegendaryId(legendary.legendaryId(left));
        String rightId = legendary.normalizeLegendaryId(legendary.legendaryId(right));
        if (leftId == null || rightId == null) {
            return null;
        }
        for (FusionRecipeView recipe : FUSION_RECIPES) {
            if (recipe.matches(leftId, rightId)) {
                return recipe;
            }
        }
        return null;
    }

    private boolean acceptsItem(int slot, ItemStack item) {
        if (slot == CATALYST_SLOT) {
            return isAscendantCore(item);
        }
        if (slot == LEFT_SLOT || slot == RIGHT_SLOT) {
            return isAllowedFusionInput(item);
        }
        return false;
    }

    private boolean isAllowedFusionInput(ItemStack item) {
        LegendaryListener legendary = plugin.getLegendaryListener();
        if (legendary == null) {
            return false;
        }
        String legendaryId = legendary.legendaryId(item);
        return legendaryId != null && legendary.isMythicForgeSourceLegendary(legendaryId);
    }

    private boolean isMythicForgeItem(ItemStack item) {
        if (item == null || item.getType() != Material.END_CRYSTAL) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte tagged = meta.getPersistentDataContainer().get(keyMythicForgeItem, PersistentDataType.BYTE);
        return tagged != null && tagged == (byte) 1;
    }

    private boolean isAscendantCore(ItemStack item) {
        if (item == null || item.getType() != Material.AMETHYST_SHARD) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte tagged = meta.getPersistentDataContainer().get(keyAscendantCore, PersistentDataType.BYTE);
        return tagged != null && tagged == (byte) 1;
    }

    private boolean isMythicForgeEntity(Entity entity) {
        if (!(entity instanceof EnderCrystal crystal)) {
            return false;
        }
        Byte tagged = crystal.getPersistentDataContainer().get(keyMythicForgeEntity, PersistentDataType.BYTE);
        return tagged != null && tagged == (byte) 1;
    }

    private boolean isInputSlot(int slot) {
        return slot == LEFT_SLOT || slot == CATALYST_SLOT || slot == RIGHT_SLOT;
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    private void consumeSingleItem(Player player, EquipmentSlot hand, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        if (item.getAmount() <= 1) {
            if (hand == EquipmentSlot.HAND) {
                player.getInventory().setItemInMainHand(null);
            } else if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            }
            return;
        }
        ItemStack next = item.clone();
        next.setAmount(item.getAmount() - 1);
        if (hand == EquipmentSlot.HAND) {
            player.getInventory().setItemInMainHand(next);
        } else if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(next);
        }
    }

    private ItemStack createGuiItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
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

    private record MythicForgeMenuHolder(UUID forgeId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public record FusionRecipeView(String leftId, String rightId, String outputId) {
        public boolean matches(String first, String second) {
            return (leftId.equals(first) && rightId.equals(second))
                || (leftId.equals(second) && rightId.equals(first));
        }
    }
}
