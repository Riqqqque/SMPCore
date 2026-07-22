package me.rique.smpcore.item;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.npc.ReforgeNpcBridge;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class ReforgeManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String STONE_ID = "reforge_stone";

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final List<String> STONE_IDS = List.of(STONE_ID);
    private static final Set<String> REFORGE_LINE_PREFIXES = Set.of(
        "Reforge:",
        "Reforge Stats:",
        "Outgoing Damage:",
        "Damage Taken:",
        "Durability Loss:",
        "Projectile Damage:",
        "Draw Time:"
    );
    private static final int MENU_SIZE = 36;
    private static final int INFO_SLOT = 4;
    private static final int ITEM_LABEL_SLOT = 11;
    private static final int STONE_LABEL_SLOT = 15;
    private static final int FLOW_SLOT = 22;
    private static final int ITEM_SLOT = 20;
    private static final int STONE_SLOT = 24;
    private static final int CONFIRM_SLOT = 31;
    public static final String NPC_DISPLAY_NAME = "Brannik";
    public static final String NPC_NAMEPLATE = "<gradient:#f59e0b:#facc15><bold>Brannik</bold></gradient>";
    private static final Component NPC_NAME_COMPONENT = MM.deserialize(NPC_NAMEPLATE)
        .decoration(TextDecoration.ITALIC, false);
    private static final String DWARF_TEXTURE_VALUE = "ewogICJ0aW1lc3RhbXAiIDogMTc3OTgxMjYyMjg3MSwKICAicHJvZmlsZUlkIiA6ICIwNTljODIxYzhhODU0NGJiOWJiODVhOGMxNjVhYTc5YiIsCiAgInByb2ZpbGVOYW1lIiA6ICJoZWxsc3RydWNrZWR6IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2U2MDg0NzRhMzhmYjMzYTM5NWFiM2QxNjQyZmI1YmQ3YjAzZGQ4MzAyYzMwZjQ4ZWQ0ZjNmM2FjMjU5ZWFjY2IiCiAgICB9CiAgfQp9";
    private static final String DWARF_TEXTURE_SIGNATURE = "VNxusQxHRuL44bANTyDtmPqoc9uXviHoCuRHgFpQFP7qKFwHsj3yeBAA2ka2b48ae7PfnvnKXtWfD5RsZFB5W4YmmKUVVbrABhYzKf0z2qjcjDRanvAycLn47Rl32DWGEZIGJVh7j9h9EgGnApB5pghADQniRP6RzMFfvm8FFJ6bduKnX48SgQeaS8QQNhpXvXliA6CWjzfTb0irG3judeS6fSc2496R9PeE/bXmAeEplgYjjFXg8TQ1nLC6T5DNPWF9zHiXczAHG7ulSY3YQFbO1/EA0dI1+hMKEMbBst83VWi8AVt0o6iAGaTVdnbxMbOHiN2WaLu5YEiIaVS8mjT9PSIv574tq6yH+Ip8+ObUlV2F6wZswGxToWvnKJTun8cENjzn5vh4QhulLRwZrsQp7qBIAw4d84Y65gIKz67ML5revdBCvsz0H6d5gBZYDEZvKl4D1w0Qha8Wo0OE8Sm1VLqShWmxo9vgJ/J1wsmzxUVvXsjWdAr2aCROoGHDMgIqA/qaY1yLuYOjKlki6oGxDnZoqykcGMBR4QNFnf7r757I2fwmACmR42MMXz/SYRGrCSVXoZxI8OuTy9X+jdblQ+LqIGskoRhyvspxHROp+LlKW7MxrCdlnjfxdI9iFNcaz8q81ENp7F9qpYCCNg5B1+DaeDd9pSWN4cRaOgo=";
    private static final UUID DWARF_PROFILE_ID = UUID.fromString("8f289e20-89cc-4f0f-a18c-3b3aee2d81e7");
    private static final long NPC_OPEN_DEBOUNCE_MS = 450L;

    private final SMPCore plugin;
    private final NamespacedKey keyNpc;
    private final NamespacedKey keyStoneId;
    private final NamespacedKey keyReforgeId;
    private final NamespacedKey keyBaseName;
    private final NamespacedKey stoneRecipeKey;
    private final NamespacedKey oldRoughRecipeKey;
    private final NamespacedKey oldVeilRecipeKey;
    private final NamespacedKey oldCorruptedRecipeKey;
    private final Map<UUID, Long> nextNpcOpenAt = new ConcurrentHashMap<>();
    private ReforgeNpcBridge npcBridge;

    public ReforgeManager(SMPCore plugin) {
        this.plugin = plugin;
        this.keyNpc = new NamespacedKey(plugin, "reforge_dwarf_npc");
        this.keyStoneId = new NamespacedKey(plugin, "reforge_stone_id");
        this.keyReforgeId = new NamespacedKey(plugin, "reforge_id");
        this.keyBaseName = new NamespacedKey(plugin, "reforge_base_name");
        this.stoneRecipeKey = new NamespacedKey(plugin, STONE_ID);
        this.oldRoughRecipeKey = new NamespacedKey(plugin, "rough_reforge_stone");
        this.oldVeilRecipeKey = new NamespacedKey(plugin, "veil_reforge_stone");
        this.oldCorruptedRecipeKey = new NamespacedKey(plugin, "corrupted_reforge_stone");
    }

    public void start() {
        registerRecipes();
        tryEnableCitizensBridge();
        Bukkit.getOnlinePlayers().forEach(this::discoverRecipes);
        scheduleDwarfRefreshes();
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder(false) instanceof ReforgeMenuHolder) {
                returnInputSlots(player, top);
                player.closeInventory();
            }
        }
        Bukkit.removeRecipe(stoneRecipeKey);
        Bukkit.removeRecipe(oldRoughRecipeKey);
        Bukkit.removeRecipe(oldVeilRecipeKey);
        Bukkit.removeRecipe(oldCorruptedRecipeKey);
        if (npcBridge != null) {
            npcBridge.shutdown();
            npcBridge = null;
        }
        nextNpcOpenAt.clear();
    }

    public List<String> reforgeStoneIds() {
        return STONE_IDS;
    }

    public String displayNameForStone(String stoneId) {
        return normalizeStoneId(stoneId) == null ? null : "Reforge Stone";
    }

    public String reforgeStoneId(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String id = meta.getPersistentDataContainer().get(keyStoneId, PersistentDataType.STRING);
        return normalizeStoneId(id);
    }

    public boolean isReforgeStone(ItemStack item) {
        return reforgeStoneId(item) != null;
    }

    public ItemStack createReforgeStone(String stoneId) {
        if (normalizeStoneId(stoneId) == null) {
            return null;
        }

        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Reforge Stone", NamedTextColor.LIGHT_PURPLE)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.AMETHYST_SHARD,
            "RARE",
            "REFORGE STONE",
            List.of("<gray>Rerolls gear into one of 15 reforges.</gray>"),
            List.of(CustomLoreUtil.section("Use", NPC_DISPLAY_NAME,
                "<gray>Place this beside one valid gear item.</gray>",
                "<dark_gray>The previous reforge is replaced.</dark_gray>"))
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(keyStoneId, PersistentDataType.STRING, STONE_ID);
        item.setItemMeta(meta);
        return item;
    }

    public Entity spawnDwarf(Location location) {
        Location spawn = dwarfSpawnLocation(location);
        Entity citizensNpc = spawnCitizensDwarf(spawn);
        if (citizensNpc != null) {
            spawnEffects(citizensNpc.getLocation());
            plugin.getLogger().info("Spawned Citizens " + NPC_DISPLAY_NAME + " at " + locationSummary(citizensNpc.getLocation()) + ".");
            return citizensNpc;
        }

        ArmorStand armorStand = spawn.getWorld().spawn(spawn, ArmorStand.class, this::configureDwarf);
        spawnEffects(armorStand.getLocation());
        plugin.getLogger().info("Spawned " + NPC_DISPLAY_NAME + " at " + locationSummary(armorStand.getLocation()) + ".");
        return armorStand;
    }

    public int removeNearestDwarf(Location origin, double radius) {
        if (npcBridge != null) {
            int removed = npcBridge.removeNearestDwarf(origin, radius);
            if (removed > 0) {
                return removed;
            }
        }

        Entity nearest = findLegacyDwarfNpcs().stream()
            .filter(entity -> entity.getWorld().equals(origin.getWorld()))
            .filter(entity -> entity.getLocation().distanceSquared(origin) <= radius * radius)
            .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(origin)))
            .orElse(null);
        if (nearest == null) {
            return 0;
        }
        nearest.remove();
        return 1;
    }

    public List<Location> dwarfLocations() {
        List<Location> locations = new ArrayList<>();
        if (npcBridge != null) {
            locations.addAll(npcBridge.dwarfLocations());
        }
        locations.addAll(findLegacyDwarfNpcs().stream().map(Entity::getLocation).toList());
        return locations;
    }

    public void openReforgeMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(new ReforgeMenuHolder(player.getUniqueId()), MENU_SIZE, Component.text("Brannik's Reforge"));
        fillMenu(inventory);
        refreshMenu(inventory);
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.45f, 1.45f);
    }

    public void openReforgeMenuFromNpc(Player player) {
        if (player == null) {
            return;
        }
        if (!player.hasPermission("smpcore.reforge.use")) {
            player.sendMessage(MessageUtil.warn("You cannot use the reforger."));
            return;
        }

        long now = System.currentTimeMillis();
        Long nextOpenAt = nextNpcOpenAt.get(player.getUniqueId());
        if (nextOpenAt != null && nextOpenAt > now) {
            return;
        }
        nextNpcOpenAt.put(player.getUniqueId(), now + NPC_OPEN_DEBOUNCE_MS);
        if (plugin.getStoryService() != null) {
            plugin.getStoryService().onNpcInteract(player, "reforger");
        }
        openReforgeMenu(player);
    }

    public boolean hasReforge(ItemStack item) {
        return reforgeFor(item) != null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDwarfInteract(PlayerInteractEntityEvent event) {
        if (event instanceof PlayerArmorStandManipulateEvent) {
            return;
        }
        if (!isDwarfNpc(event.getRightClicked())) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        openReforgeMenuFromNpcNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDwarfArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (!isDwarfNpc(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        openReforgeMenuFromNpcNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDwarfDamage(EntityDamageEvent event) {
        if (isDwarfNpc(event.getEntity())) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Player player) || event.isCancelled()) {
            return;
        }
        double multiplier = 1.0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            ReforgeType reforge = reforgeFor(armor);
            if (reforge != null) {
                multiplier *= reforge.damageTakenMultiplier;
            }
        }
        if (Math.abs(multiplier - 1.0) > 0.001) {
            event.setDamage(event.getDamage() * clamp(multiplier, 0.65, 1.35));
        }
    }

    public int refreshDwarfs() {
        return refreshLoadedDwarfs();
    }

    public double equippedDamageTakenMultiplier(Player player) {
        if (player == null) return 1.0D;
        double multiplier = 1.0D;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            ReforgeType reforge = reforgeFor(armor);
            if (reforge != null) multiplier *= reforge.damageTakenMultiplier;
        }
        return clamp(multiplier, 0.65D, 1.35D);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onReforgedAttack(EntityDamageByEntityEvent event) {
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker == null) {
            return;
        }
        ReforgeType reforge = reforgeFor(activeAttackItem(attacker, event.getDamager()));
        if (reforge == null || Math.abs(reforge.outgoingDamageMultiplier - 1.0) <= 0.001) {
            return;
        }
        event.setDamage(event.getDamage() * reforge.outgoingDamageMultiplier);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onReforgedItemDamage(PlayerItemDamageEvent event) {
        ReforgeType reforge = reforgeFor(event.getItem());
        if (reforge == null || Math.abs(reforge.durabilityLossMultiplier - 1.0) <= 0.001) {
            return;
        }
        int damage = (int) Math.round(event.getDamage() * reforge.durabilityLossMultiplier);
        event.setDamage(Math.max(0, damage));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof ReforgeMenuHolder holder)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        if (!holder.playerId().equals(player.getUniqueId())) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        int rawSlot = event.getRawSlot();
        boolean clickedTop = rawSlot >= 0 && rawSlot < event.getView().getTopInventory().getSize();
        if (clickedTop) {
            handleTopClick(event, player, rawSlot);
            return;
        }

        if (event.isShiftClick() || event.getClick() == ClickType.DOUBLE_CLICK || event.getClick().isCreativeAction()) {
            event.setCancelled(true);
            if (event.isShiftClick()) {
                shiftMoveIntoMenu(player, event.getView().getTopInventory(), event);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> sanitizeAndRefresh(player, event.getView().getTopInventory()));
            }
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> sanitizeAndRefresh(player, event.getView().getTopInventory()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof ReforgeMenuHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    Bukkit.getScheduler().runTask(plugin, () -> sanitizeAndRefresh(player, event.getView().getTopInventory()));
                }
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof ReforgeMenuHolder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        returnInputSlots(player, event.getInventory());
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Inventory top = event.getEntity().getOpenInventory().getTopInventory();
        if (top.getHolder(false) instanceof ReforgeMenuHolder) {
            evacuateDeathInput(top, event.getDrops(), ITEM_SLOT);
            evacuateDeathInput(top, event.getDrops(), STONE_SLOT);
        }
    }

    private static void evacuateDeathInput(Inventory inventory, List<ItemStack> drops, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (item != null && !item.getType().isAir()) {
            drops.add(item.clone());
            inventory.setItem(slot, null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> discoverRecipes(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        returnOpenReforgeInputs(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        returnOpenReforgeInputs(event.getPlayer());
        nextNpcOpenAt.remove(event.getPlayer().getUniqueId());
    }

    private void returnOpenReforgeInputs(Player player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder(false) instanceof ReforgeMenuHolder) {
            returnInputSlots(player, top);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        if (!event.getPlugin().getName().equalsIgnoreCase("Citizens")) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            tryEnableCitizensBridge();
            scheduleDwarfRefreshes();
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            refreshDwarfNpc(entity);
        }
    }

    private void registerRecipes() {
        Bukkit.removeRecipe(stoneRecipeKey);
        Bukkit.removeRecipe(oldRoughRecipeKey);
        Bukkit.removeRecipe(oldVeilRecipeKey);
        Bukkit.removeRecipe(oldCorruptedRecipeKey);

        ShapedRecipe stone = new ShapedRecipe(stoneRecipeKey, createReforgeStone(STONE_ID));
        stone.shape(" A ", "ASA", " A ");
        stone.setIngredient('A', Material.AMETHYST_SHARD);
        stone.setIngredient('S', Material.SMOOTH_STONE);
        stone.setGroup("smpcore_reforge");
        Bukkit.addRecipe(stone);
    }

    private void discoverRecipes(Player player) {
        player.discoverRecipe(stoneRecipeKey);
    }

    private void configureDwarf(ArmorStand armorStand) {
        armorStand.customName(NPC_NAME_COMPONENT);
        armorStand.setCustomNameVisible(true);
        armorStand.setSmall(true);
        armorStand.setArms(true);
        armorStand.setBasePlate(false);
        armorStand.setMarker(false);
        armorStand.setVisible(true);
        armorStand.setInvulnerable(true);
        armorStand.setSilent(true);
        armorStand.setCollidable(false);
        armorStand.setGravity(false);
        armorStand.setCanPickupItems(false);
        armorStand.setPersistent(true);
        armorStand.setRemoveWhenFarAway(false);
        armorStand.addScoreboardTag("smpcore_npc");
        armorStand.addScoreboardTag("smpcore_reforge_npc");
        armorStand.setDisabledSlots(EquipmentSlot.values());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            for (ArmorStand.LockType lockType : ArmorStand.LockType.values()) {
                armorStand.addEquipmentLock(slot, lockType);
            }
        }
        equipDwarf(armorStand);
        armorStand.getPersistentDataContainer().set(keyNpc, PersistentDataType.BYTE, (byte) 1);
    }

    private void equipDwarf(ArmorStand armorStand) {
        EntityEquipment equipment = armorStand.getEquipment();
        if (equipment == null) {
            return;
        }
        equipment.setHelmet(createDwarfHead(), true);
        equipment.setChestplate(leatherArmor(Material.LEATHER_CHESTPLATE, Color.fromRGB(83, 54, 32)), true);
        equipment.setLeggings(leatherArmor(Material.LEATHER_LEGGINGS, Color.fromRGB(54, 43, 33)), true);
        equipment.setBoots(leatherArmor(Material.LEATHER_BOOTS, Color.fromRGB(42, 31, 23)), true);
        equipment.setItemInMainHand(new ItemStack(Material.MACE), true);
    }

    private ItemStack createDwarfHead() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            PlayerProfile profile = Bukkit.createProfile(DWARF_PROFILE_ID, "ReforgeDwarf");
            profile.setProperty(new ProfileProperty("textures", DWARF_TEXTURE_VALUE, DWARF_TEXTURE_SIGNATURE));
            skullMeta.setPlayerProfile(profile);
            skullMeta.displayName(NPC_NAME_COMPONENT);
            head.setItemMeta(skullMeta);
        }
        return head;
    }

    private ItemStack leatherArmor(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leatherMeta) {
            leatherMeta.setColor(color);
            leatherMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DYE);
            item.setItemMeta(leatherMeta);
        }
        return item;
    }

    private boolean isDwarfNpc(Entity entity) {
        if (entity == null) {
            return false;
        }
        Byte marker = entity.getPersistentDataContainer().get(keyNpc, PersistentDataType.BYTE);
        if (marker != null && marker == (byte) 1) {
            return true;
        }
        return entity.getScoreboardTags().contains("smpcore_reforge_npc");
    }

    private void openReforgeMenuFromNpcNextTick(Player player) {
        if (player == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                openReforgeMenuFromNpc(player);
            }
        });
    }

    private Entity spawnCitizensDwarf(Location location) {
        if (npcBridge == null) {
            return null;
        }
        try {
            return npcBridge.spawnDwarf(location);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Citizens reforger spawn failed; using armor stand fallback: " + ex.getMessage());
            return null;
        }
    }

    private Location dwarfSpawnLocation(Location origin) {
        if (origin == null || origin.getWorld() == null) {
            throw new IllegalArgumentException("Dwarf spawn location must have a world.");
        }

        Location spawn = origin.clone();
        spawn.setX(origin.getBlockX() + 0.5);
        spawn.setZ(origin.getBlockZ() + 0.5);
        spawn.setPitch(0.0f);
        spawn.setY(safeFloorY(spawn));
        return spawn;
    }

    private double safeFloorY(Location origin) {
        World world = origin.getWorld();
        int x = origin.getBlockX();
        int z = origin.getBlockZ();
        int startY = Math.min(world.getMaxHeight() - 1, Math.max(world.getMinHeight(), origin.getBlockY()));
        for (int y = startY; y >= world.getMinHeight(); y--) {
            Block floor = world.getBlockAt(x, y, z);
            if (!floor.getType().isSolid() || floor.isPassable()) {
                continue;
            }

            Block feet = floor.getRelative(BlockFace.UP);
            Block head = feet.getRelative(BlockFace.UP);
            if (feet.isPassable() && head.isPassable()) {
                return y + 1.0;
            }
            return origin.getY();
        }
        return origin.getY();
    }

    private String locationSummary(Location location) {
        return location.getWorld().getName()
            + " "
            + location.getBlockX()
            + ", "
            + location.getBlockY()
            + ", "
            + location.getBlockZ();
    }

    private List<Entity> findLegacyDwarfNpcs() {
        List<Entity> found = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isDwarfNpc(entity) && (entity instanceof ArmorStand || entity instanceof Villager)) {
                    found.add(entity);
                }
            }
        }
        return found;
    }

    private int refreshLoadedDwarfs() {
        int refreshed = 0;
        if (npcBridge != null) {
            refreshed += npcBridge.refreshLoadedNpcs();
        }
        for (Entity entity : new ArrayList<>(findLegacyDwarfNpcs())) {
            if (refreshDwarfNpc(entity)) {
                refreshed++;
            }
        }
        return refreshed;
    }

    private void scheduleDwarfRefreshes() {
        long[] delays = {1L, 40L, 120L};
        for (long delay : delays) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                int refreshed = refreshLoadedDwarfs();
                if (refreshed > 0) {
                    plugin.getLogger().info("Refreshed " + refreshed + " " + NPC_DISPLAY_NAME + " NPC(s).");
                }
            }, delay);
        }
    }

    private boolean refreshDwarfNpc(Entity entity) {
        if (!isDwarfNpc(entity)) {
            return false;
        }
        if (npcBridge != null && entity instanceof ArmorStand) {
            Location location = entity.getLocation();
            entity.remove();
            spawnDwarf(location);
            return true;
        }
        if (entity instanceof ArmorStand armorStand) {
            configureDwarf(armorStand);
            return true;
        }
        if (entity instanceof Villager) {
            Location location = entity.getLocation();
            entity.remove();
            spawnDwarf(location);
            return true;
        }
        return false;
    }

    private void tryEnableCitizensBridge() {
        if (npcBridge != null || !Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            return;
        }

        try {
            Class<?> bridgeClass = Class.forName("me.rique.smpcore.npc.CitizensReforgeBridge");
            npcBridge = (ReforgeNpcBridge) bridgeClass
                .getConstructor(SMPCore.class, ReforgeManager.class, NamespacedKey.class)
                .newInstance(plugin, this, keyNpc);
            plugin.getLogger().info("Citizens-backed " + NPC_DISPLAY_NAME + " NPCs enabled.");
        } catch (ReflectiveOperationException | LinkageError ex) {
            npcBridge = null;
            plugin.getLogger().warning("Citizens is installed but SMPCore could not hook reforger NPCs: " + ex.getMessage());
        }
    }

    private void spawnEffects(Location location) {
        World world = location.getWorld();
        world.playSound(location, Sound.BLOCK_SMITHING_TABLE_USE, 1.0f, 0.75f);
        world.playSound(location, Sound.ENTITY_VILLAGER_YES, 0.9f, 0.9f);
        world.spawnParticle(Particle.CRIT, location.clone().add(0.0, 0.8, 0.0), 28, 0.35, 0.45, 0.35, 0.02);
        world.spawnParticle(Particle.SMOKE, location.clone().add(0.0, 0.4, 0.0), 18, 0.25, 0.2, 0.25, 0.01);
    }

    private void fillMenu(Inventory inventory) {
        decorateMenu(inventory);
        inventory.setItem(ITEM_SLOT, null);
        inventory.setItem(STONE_SLOT, null);
        inventory.setItem(CONFIRM_SLOT, confirmItem(false, null));
    }

    private void decorateMenu(Inventory inventory) {
        ItemStack filler = menuItem(
            Material.BLACK_STAINED_GLASS_PANE,
            MenuItemUtil.visibleName(Component.empty()),
            MenuItemUtil.visibleLore(Component.empty(), List.of())
        );
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot != ITEM_SLOT && slot != STONE_SLOT && slot != CONFIRM_SLOT) {
                inventory.setItem(slot, filler);
            }
        }
        inventory.setItem(INFO_SLOT, menuItem(
            Material.SMITHING_TABLE,
            Component.text("Dwarven Reforge", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
            List.of(
                Component.text("Gear + Reforge Stone rolls one prefix.", NamedTextColor.GRAY),
                Component.text("Craft: 4 Amethyst around 1 Smooth Stone.", NamedTextColor.LIGHT_PURPLE),
                Component.text("Prefixes can be good or bad.", NamedTextColor.YELLOW),
                Component.text("Corruption-locked items cannot be reforged.", NamedTextColor.DARK_GRAY)
            )
        ));
        inventory.setItem(ITEM_LABEL_SLOT, menuItem(
            Material.ANVIL,
            Component.text("Gear Slot", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
            List.of(Component.text("One tool, weapon, or armor piece.", NamedTextColor.GRAY))
        ));
        inventory.setItem(FLOW_SLOT, menuItem(
            Material.HOPPER,
            Component.text("Reforge Path", NamedTextColor.DARK_AQUA).decorate(TextDecoration.BOLD),
            List.of(Component.text("Gear + stone, then confirm below.", NamedTextColor.GRAY))
        ));
        inventory.setItem(STONE_LABEL_SLOT, menuItem(
            Material.AMETHYST_SHARD,
            Component.text("Stone Slot", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD),
            List.of(
                Component.text("Uses one Reforge Stone.", NamedTextColor.GRAY),
                Component.text("4 Amethyst around Smooth Stone.", NamedTextColor.LIGHT_PURPLE)
            )
        ));
    }

    private void refreshMenu(Inventory inventory) {
        decorateMenu(inventory);
        ItemStack target = inventory.getItem(ITEM_SLOT);
        ItemStack stone = inventory.getItem(STONE_SLOT);
        boolean validTarget = isReforgeTarget(target);
        inventory.setItem(CONFIRM_SLOT, confirmItem(validTarget && isReforgeStone(stone), target));
    }

    private ItemStack confirmItem(boolean ready, ItemStack target) {
        List<Component> lore = new ArrayList<>();
        if (ready) {
            lore.add(Component.text("Rolls one of 15 prefixes.", NamedTextColor.GRAY));
            lore.add(Component.text("Consumes 1 Reforge Stone.", NamedTextColor.DARK_GRAY));
            lore.add(Component.text("This replaces the old reforge.", NamedTextColor.YELLOW));
        } else {
            lore.add(Component.text("Add one valid item and one reforge stone.", NamedTextColor.GRAY));
            if (target != null && !target.getType().isAir() && !isReforgeTarget(target)) {
                lore.add(Component.text("That item cannot be reforged.", NamedTextColor.RED));
            }
        }
        return menuItem(
            ready ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
            Component.text(ready ? "Reforge Item" : "Waiting for Items", ready ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                .decorate(TextDecoration.BOLD),
            lore
        );
    }

    private void handleTopClick(InventoryClickEvent event, Player player, int rawSlot) {
        Inventory top = event.getView().getTopInventory();
        if (rawSlot == CONFIRM_SLOT) {
            event.setCancelled(true);
            if (!isIntentionalMenuAction(event) || !MenuItemUtil.isVisibleItem(event.getCurrentItem())) {
                return;
            }
            executeReforge(player, top);
            return;
        }

        if (rawSlot != ITEM_SLOT && rawSlot != STONE_SLOT) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
            return;
        }

        if (event.isShiftClick() || isBlockedTopClick(event.getClick())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> sanitizeAndRefresh(player, top));
            return;
        }

        ItemStack cursor = event.getCursor();
        if (!isEmpty(cursor) && !isAllowedForSlot(rawSlot, cursor)) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.warn(rawSlot == ITEM_SLOT ? "That item cannot be reforged." : "Put a reforge stone there."));
            Bukkit.getScheduler().runTask(plugin, () -> sanitizeAndRefresh(player, top));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> sanitizeAndRefresh(player, top));
    }

    private boolean isBlockedTopClick(ClickType click) {
        return click == ClickType.DOUBLE_CLICK
            || click == ClickType.DROP
            || click == ClickType.CONTROL_DROP
            || click == ClickType.MIDDLE
            || click == ClickType.SWAP_OFFHAND
            || click == ClickType.WINDOW_BORDER_LEFT
            || click == ClickType.WINDOW_BORDER_RIGHT
            || click.isKeyboardClick()
            || click.isCreativeAction();
    }

    private boolean isIntentionalMenuAction(InventoryClickEvent event) {
        return event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT;
    }

    private void shiftMoveIntoMenu(Player player, Inventory top, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (isEmpty(clicked)) {
            return;
        }
        if (isReforgeStone(clicked)) {
            moveStoneIntoMenu(top, clicked);
            if (clicked.getAmount() <= 0) {
                event.setCurrentItem(null);
            }
        } else if (isReforgeTarget(clicked)) {
            if (isEmpty(top.getItem(ITEM_SLOT))) {
                top.setItem(ITEM_SLOT, clicked.clone());
                event.setCurrentItem(null);
            } else {
                player.sendMessage(MessageUtil.warn("The item slot is already full."));
            }
        } else {
            player.sendMessage(MessageUtil.warn("That item cannot be reforged."));
        }
        sanitizeAndRefresh(player, top);
        player.updateInventory();
    }

    private void moveStoneIntoMenu(Inventory top, ItemStack clicked) {
        ItemStack existing = top.getItem(STONE_SLOT);
        String clickedId = reforgeStoneId(clicked);
        if (isEmpty(existing)) {
            ItemStack moved = clicked.clone();
            top.setItem(STONE_SLOT, moved);
            clicked.setAmount(0);
            return;
        }
        if (!clickedId.equals(reforgeStoneId(existing)) || existing.getAmount() >= existing.getMaxStackSize()) {
            return;
        }
        int move = Math.min(clicked.getAmount(), existing.getMaxStackSize() - existing.getAmount());
        existing.setAmount(existing.getAmount() + move);
        clicked.setAmount(clicked.getAmount() - move);
    }

    private void sanitizeAndRefresh(Player player, Inventory top) {
        ItemStack target = top.getItem(ITEM_SLOT);
        if (!isEmpty(target) && !isReforgeTarget(target)) {
            top.setItem(ITEM_SLOT, null);
            returnOrDrop(player, target);
        }

        ItemStack stone = top.getItem(STONE_SLOT);
        if (!isEmpty(stone) && !isReforgeStone(stone)) {
            top.setItem(STONE_SLOT, null);
            returnOrDrop(player, stone);
        }

        refreshMenu(top);
    }

    private void executeReforge(Player player, Inventory inventory) {
        sanitizeAndRefresh(player, inventory);
        ItemStack target = inventory.getItem(ITEM_SLOT);
        ItemStack stone = inventory.getItem(STONE_SLOT);
        if (!isReforgeTarget(target) || !isReforgeStone(stone)) {
            player.sendMessage(MessageUtil.warn("Add one valid item and one reforge stone."));
            refreshMenu(inventory);
            return;
        }

        ReforgeType result = rollReforge();
        ItemStack reforged = target.clone();
        applyReforge(reforged, result);
        inventory.setItem(ITEM_SLOT, reforged);
        consumeOne(inventory, STONE_SLOT);
        refreshMenu(inventory);

        Location soundLocation = player.getLocation();
        player.getWorld().playSound(soundLocation, Sound.BLOCK_ANVIL_LAND, 0.8f, result.good ? 1.25f : 0.65f);
        player.getWorld().playSound(soundLocation, result.good ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_GRINDSTONE_USE, 0.8f, result.good ? 1.1f : 0.7f);
        player.getWorld().spawnParticle(result.good ? Particle.ENCHANT : Particle.SMOKE, soundLocation.clone().add(0.0, 1.0, 0.0), 36, 0.35, 0.45, 0.35, 0.02);
        player.sendMessage(result.good
            ? MessageUtil.success("Reforged into <white>" + result.displayName + "</white>.")
            : MessageUtil.warn("Reforged into <white>" + result.displayName + "</white>."));
    }

    private void applyReforge(ItemStack item, ReforgeType reforge) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String baseName = pdc.get(keyBaseName, PersistentDataType.STRING);
        if (baseName == null || baseName.isBlank()) {
            baseName = baseDisplayName(item, meta);
            pdc.set(keyBaseName, PersistentDataType.STRING, baseName);
        }
        pdc.set(keyReforgeId, PersistentDataType.STRING, reforge.id);
        meta.displayName(Component.text(reforge.displayName + " " + baseName, reforge.color)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(CustomLoreUtil.normalizeLore(rewriteReforgeLore(meta.lore(), reforge)));
        item.setItemMeta(meta);
    }

    private List<Component> rewriteReforgeLore(List<Component> currentLore, ReforgeType reforge) {
        List<Component> lore = CustomLoreUtil.removeManagedLines(currentLore, REFORGE_LINE_PREFIXES);
        lore.add(Component.text("Reforge: ", NamedTextColor.DARK_GRAY)
            .append(Component.text(reforge.displayName, reforge.color))
            .append(Component.text(" (" + reforge.shortBonus + ")", NamedTextColor.GRAY))
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Reforge Stats: ", NamedTextColor.DARK_GRAY)
            .append(Component.text("Dmg " + formatSignedPercent(reforge.outgoingDamageMultiplier - 1.0), statColor(reforge.outgoingDamageMultiplier, false)))
            .append(Component.text(" • Taken " + formatSignedPercent(reforge.damageTakenMultiplier - 1.0), statColor(reforge.damageTakenMultiplier, true)))
            .append(Component.text(" • Wear " + formatSignedPercent(reforge.durabilityLossMultiplier - 1.0), statColor(reforge.durabilityLossMultiplier, true)))
            .decoration(TextDecoration.ITALIC, false));
        return lore;
    }

    private NamedTextColor statColor(double multiplier, boolean lowerIsBetter) {
        double delta = multiplier - 1.0;
        return Math.abs(delta) <= 0.001
            ? NamedTextColor.GRAY
            : ((delta < 0.0) == lowerIsBetter ? NamedTextColor.GREEN : NamedTextColor.RED);
    }

    private Component reforgeStatLine(String label, double multiplier, boolean lowerIsBetter) {
        double delta = multiplier - 1.0;
        NamedTextColor color = Math.abs(delta) <= 0.001
            ? NamedTextColor.GRAY
            : ((delta < 0.0) == lowerIsBetter ? NamedTextColor.GREEN : NamedTextColor.RED);
        return Component.text(label + ": ", NamedTextColor.DARK_GRAY)
            .append(Component.text(formatSignedPercent(delta), color))
            .decoration(TextDecoration.ITALIC, false);
    }

    private String formatSignedPercent(double value) {
        long rounded = Math.round(value * 100.0);
        return rounded == 0 ? "unchanged" : (rounded > 0 ? "+" : "") + rounded + "%";
    }

    private String baseDisplayName(ItemStack item, ItemMeta meta) {
        Component displayName = meta.displayName();
        if (displayName != null) {
            return stripExistingPrefix(PLAIN.serialize(displayName).trim());
        }
        return prettyMaterialName(item.getType());
    }

    private String stripExistingPrefix(String name) {
        if (name == null || name.isBlank()) {
            return "Unknown Item";
        }
        for (ReforgeType reforge : ReforgeType.values()) {
            String prefix = reforge.displayName + " ";
            if (name.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return name.substring(prefix.length()).trim();
            }
        }
        return name;
    }

    private ReforgeType rollReforge() {
        ReforgeType[] pool = ReforgeType.POOL;
        return pool[ThreadLocalRandom.current().nextInt(pool.length)];
    }

    private ReforgeType reforgeFor(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String id = meta.getPersistentDataContainer().get(keyReforgeId, PersistentDataType.STRING);
        return ReforgeType.fromId(id);
    }

    private boolean isAllowedForSlot(int slot, ItemStack item) {
        return slot == ITEM_SLOT ? isReforgeTarget(item) : isReforgeStone(item);
    }

    private String normalizeStoneId(String id) {
        if (id == null) {
            return null;
        }
        return switch (id) {
            case STONE_ID,
                 "rough_reforge_stone",
                 "veil_reforge_stone",
                 "corrupted_reforge_stone" -> STONE_ID;
            default -> null;
        };
    }

    private boolean isReforgeTarget(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() != 1 || isReforgeStone(item)) {
            return false;
        }
        CorruptionManager corruptionManager = plugin.getCorruptionManager();
        if (corruptionManager != null && corruptionManager.isCorruptionLocked(item)) {
            return false;
        }
        Material material = item.getType();
        String name = material.name();
        return name.endsWith("_SWORD")
            || name.endsWith("_AXE")
            || name.endsWith("_PICKAXE")
            || name.endsWith("_SHOVEL")
            || name.endsWith("_HOE")
            || name.endsWith("_HELMET")
            || name.endsWith("_CHESTPLATE")
            || name.endsWith("_LEGGINGS")
            || name.endsWith("_BOOTS")
            || material == Material.BOW
            || material == Material.CROSSBOW
            || material == Material.TRIDENT
            || material == Material.MACE
            || material == Material.SHIELD
            || material == Material.ELYTRA
            || material == Material.TURTLE_HELMET
            || material == Material.SHEARS
            || material == Material.FISHING_ROD
            || material == Material.FLINT_AND_STEEL;
    }

    private Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private ItemStack activeAttackItem(Player attacker, Entity damager) {
        PlayerInventory inventory = attacker.getInventory();
        if (damager instanceof Projectile) {
            ItemStack main = inventory.getItemInMainHand();
            if (hasReforge(main)) {
                return main;
            }
            ItemStack offhand = inventory.getItemInOffHand();
            return hasReforge(offhand) ? offhand : main;
        }
        return inventory.getItemInMainHand();
    }

    private void consumeOne(Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (isEmpty(item)) {
            return;
        }
        if (item.getAmount() <= 1) {
            inventory.setItem(slot, null);
            return;
        }
        item.setAmount(item.getAmount() - 1);
    }

    private void returnInputSlots(Player player, Inventory inventory) {
        returnSlot(player, inventory, ITEM_SLOT);
        returnSlot(player, inventory, STONE_SLOT);
    }

    private void returnSlot(Player player, Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (isEmpty(item)) {
            return;
        }
        inventory.setItem(slot, null);
        returnOrDrop(player, item);
    }

    private void returnOrDrop(Player player, ItemStack item) {
        if (isEmpty(item)) {
            return;
        }
        player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private ItemStack menuItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        Component visibleName = MenuItemUtil.visibleName(name);
        List<Component> visibleLore = MenuItemUtil.visibleLore(name, lore);
        meta.displayName(visibleName.decoration(TextDecoration.ITALIC, false));
        if (!visibleLore.isEmpty()) {
            meta.lore(visibleLore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private String prettyMaterialName(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return out.toString();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum ReforgeType {
        TITANIC("titanic", "Titanic", NamedTextColor.DARK_RED, true, 1.12, 0.96, 0.90, "+12% damage, tougher"),
        SHARP("sharp", "Sharp", NamedTextColor.RED, true, 1.10, 1.00, 1.00, "+10% damage"),
        GUARDING("guarding", "Guarding", NamedTextColor.AQUA, true, 1.00, 0.88, 0.95, "-12% damage taken"),
        ENDURING("enduring", "Enduring", NamedTextColor.GREEN, true, 1.02, 0.97, 0.70, "30% less durability loss"),
        PRECISE("precise", "Precise", NamedTextColor.GOLD, true, 1.07, 0.98, 0.95, "+7% damage"),
        TEMPERED("tempered", "Tempered", NamedTextColor.BLUE, true, 1.04, 0.94, 0.85, "balanced upgrade"),
        SWIFT("swift", "Swift", NamedTextColor.YELLOW, true, 1.05, 1.00, 0.85, "+5% damage, steadier tools"),
        BLESSED("blessed", "Blessed", NamedTextColor.LIGHT_PURPLE, true, 1.03, 0.95, 0.80, "safer and sturdier"),
        BALANCED("balanced", "Balanced", NamedTextColor.WHITE, true, 1.03, 0.97, 0.95, "small all-around buff"),
        HEAVY("heavy", "Heavy", NamedTextColor.DARK_AQUA, false, 1.05, 1.04, 1.10, "+5% damage, less protection"),
        FRAGILE("fragile", "Fragile", NamedTextColor.DARK_GRAY, false, 1.03, 1.08, 1.35, "breaks faster"),
        WEAK("weak", "Weak", NamedTextColor.DARK_RED, false, 0.92, 1.03, 1.00, "-8% damage"),
        DULL("dull", "Dull", NamedTextColor.GRAY, false, 0.88, 1.00, 1.10, "-12% damage"),
        BRITTLE("brittle", "Brittle", NamedTextColor.DARK_PURPLE, false, 0.98, 1.12, 1.45, "takes more damage"),
        CLUMSY("clumsy", "Clumsy", NamedTextColor.YELLOW, false, 0.95, 1.06, 1.20, "slightly worse");

        private static final ReforgeType[] POOL = values();

        private final String id;
        private final String displayName;
        private final NamedTextColor color;
        private final boolean good;
        private final double outgoingDamageMultiplier;
        private final double damageTakenMultiplier;
        private final double durabilityLossMultiplier;
        private final String shortBonus;

        ReforgeType(
            String id,
            String displayName,
            NamedTextColor color,
            boolean good,
            double outgoingDamageMultiplier,
            double damageTakenMultiplier,
            double durabilityLossMultiplier,
            String shortBonus
        ) {
            this.id = id;
            this.displayName = displayName;
            this.color = color;
            this.good = good;
            this.outgoingDamageMultiplier = outgoingDamageMultiplier;
            this.damageTakenMultiplier = damageTakenMultiplier;
            this.durabilityLossMultiplier = durabilityLossMultiplier;
            this.shortBonus = shortBonus;
        }

        private static ReforgeType fromId(String id) {
            if (id == null) {
                return null;
            }
            for (ReforgeType reforge : values()) {
                if (reforge.id.equals(id)) {
                    return reforge;
                }
            }
            return null;
        }
    }

    private record ReforgeMenuHolder(UUID playerId) implements InventoryHolder, MenuDupeGuardListener.RecoveryTrackedMenuHolder {
        @Override public String recoverySurface() { return "Reforge Station"; }
        @Override public int[] recoverySlots() { return new int[] { ITEM_SLOT, STONE_SLOT }; }
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
