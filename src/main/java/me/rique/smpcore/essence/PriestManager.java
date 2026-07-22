package me.rique.smpcore.essence;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.npc.PriestNpcBridge;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PriestManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int MENU_SIZE = 36;
    private static final int BALANCE_SLOT = 4;
    private static final int INFO_SLOT = 22;
    private static final int CLOSE_SLOT = 31;
    private static final long NPC_OPEN_DEBOUNCE_MS = 450L;
    private static final long BOSS_WARD_DURATION_MILLIS = 30L * 60L * 1000L;
    private static final double BOSS_WARD_DAMAGE_MULTIPLIER = 1.12D;
    public static final String NPC_DISPLAY_NAME = "Father Aldren";
    public static final String NPC_NAMEPLATE = "<gradient:#7dd3fc:#c084fc><bold>Father Aldren</bold></gradient>";
    private static final Component NPC_NAME_COMPONENT = MM.deserialize(NPC_NAMEPLATE)
        .decoration(TextDecoration.ITALIC, false);
    private static final String PRIEST_TEXTURE_VALUE = "ewogICJ0aW1lc3RhbXAiIDogMTY1MDM5OTc4MzcyNiwKICAicHJvZmlsZUlkIiA6ICJmZTYxY2RiMjUyMTA0ODYzYTljY2E2ODAwZDRiMzgzZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJNeVNoYWRvd3MiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTZhYjY5ZDVhMGE5YjQzMGFmYWM4NWUyMWU4NjEzNGNiZmQ1NWU2NzQ5ZmJiMzlkZThhYzNkNTEwMmQ3NWE3ZCIKICAgIH0KICB9Cn0=";
    private static final String PRIEST_TEXTURE_SIGNATURE = "xu1Uv8JEfU9EqeBNeES3QvMNYR5pYWchlcO2aIskL0VUAElVWRYwWAI9YZDeuzGR6WYK3wYVLpNNnh/Fs+NOcZgTxpCj7O+BxHIkffvF/snkMoSTEfM0GKf0h5YVMJaoTS3R4IFNpSmYtrzb/vFnApfY67OLcpXjGZ2SR5ET6gZgKWddjWi8HvrvkM9L99IyOd1NEUbKZCr57IZB0tffP4TYz/cko+QSILc6awPD0zHVMdyVebjii1WBN+paDJSAfovODJRIp8EEkfHgPVm4xrfvvNa9nIqLzJXM7bLneSv1OHyYvdSXrYW1nAZDkC9CPb5qyDqkv3RSkY6Bhb22DcdIJ5Cr7ffY1GQyFKHggo3l7TeAPfBF+6IBskcqFrCn0zFeVtitN5uJzR6JnmNJtCZkyS+fLBUbVCriIRBQvp0e9YVnPMXwNZ2HmmxIyHg/BhDCmx+IvqQMxyvYUoIhZT6eWegy7fY8yn6ISauTjQftgSyfPXkeKZ0/EQE77JjBqGufXUvYDDZGvu0r8Lqvv5rqx+PkvA97UOK/kzUImAWNkUGtb4LgQ6/8wYgMLeFxh0dkAvRBxywWia8fBXeyu2Iyi6sr+ge3rGtfN2i1NjMlOjm/tx2GGyIPLJsGdX8z1KBN0iKKHoERkC4fxWiJFxXqk8YdoVx2omj36xnRIDo=";
    private static final UUID PRIEST_PROFILE_ID = UUID.fromString("fe61cdb2-5210-4863-a9cc-a6800d4b383e");
    private static final Set<String> BENEFICIAL_EFFECT_KEYS = Set.of(
        "absorption",
        "conduit_power",
        "dolphins_grace",
        "fire_resistance",
        "haste",
        "health_boost",
        "hero_of_the_village",
        "instant_health",
        "invisibility",
        "jump_boost",
        "luck",
        "night_vision",
        "regeneration",
        "resistance",
        "saturation",
        "slow_falling",
        "speed",
        "strength",
        "water_breathing"
    );

    private final SMPCore plugin;
    private final NamespacedKey keyNpc;
    private final NamespacedKey keyBossWardUntil;
    private final Map<UUID, Long> nextNpcOpenAt = new ConcurrentHashMap<>();
    private PriestNpcBridge npcBridge;

    public PriestManager(SMPCore plugin) {
        this.plugin = plugin;
        this.keyNpc = new NamespacedKey(plugin, "priest_npc");
        this.keyBossWardUntil = new NamespacedKey(plugin, "boss_ward_until");
    }

    public void start() {
        tryEnableCitizensBridge();
        schedulePriestRefreshes();
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder(false) instanceof PriestMenuHolder) {
                player.closeInventory();
            }
        }
        if (npcBridge != null) {
            npcBridge.shutdown();
            npcBridge = null;
        }
        nextNpcOpenAt.clear();
    }

    public Entity spawnPriest(Location location) {
        Location spawn = priestSpawnLocation(location);
        Entity citizensNpc = spawnCitizensPriest(spawn);
        if (citizensNpc != null) {
            spawnEffects(citizensNpc.getLocation());
            plugin.getLogger().info("Spawned Citizens " + NPC_DISPLAY_NAME + " at " + locationSummary(citizensNpc.getLocation()) + ".");
            return citizensNpc;
        }

        ArmorStand armorStand = spawn.getWorld().spawn(spawn, ArmorStand.class, this::configurePriest);
        spawnEffects(armorStand.getLocation());
        plugin.getLogger().info("Spawned " + NPC_DISPLAY_NAME + " at " + locationSummary(armorStand.getLocation()) + ".");
        return armorStand;
    }

    public int removeNearestPriest(Location origin, double radius) {
        if (npcBridge != null) {
            int removed = npcBridge.removeNearestPriest(origin, radius);
            if (removed > 0) {
                return removed;
            }
        }

        Entity nearest = findLegacyPriestNpcs().stream()
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

    public List<Location> priestLocations() {
        List<Location> locations = new ArrayList<>();
        if (npcBridge != null) {
            locations.addAll(npcBridge.priestLocations());
        }
        locations.addAll(findLegacyPriestNpcs().stream().map(Entity::getLocation).toList());
        return locations;
    }

    public int refreshPriests() {
        return refreshLoadedPriests();
    }

    public void openMenuFromNpc(Player player) {
        if (player == null) {
            return;
        }
        if (!player.hasPermission("smpcore.priest.use")) {
            player.sendMessage(MessageUtil.warn("You cannot use the priest."));
            return;
        }

        long now = System.currentTimeMillis();
        Long nextOpenAt = nextNpcOpenAt.get(player.getUniqueId());
        if (nextOpenAt != null && nextOpenAt > now) {
            return;
        }
        nextNpcOpenAt.put(player.getUniqueId(), now + NPC_OPEN_DEBOUNCE_MS);
        if (plugin.getStoryService() != null) {
            plugin.getStoryService().onNpcInteract(player, "priest");
        }
        openMenu(player);
    }

    public void openMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(new PriestMenuHolder(player.getUniqueId()), MENU_SIZE, Component.text("Aldren's Blessings"));
        fillMenu(inventory, player);
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.55f, 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPriestInteract(PlayerInteractEntityEvent event) {
        if (event instanceof PlayerArmorStandManipulateEvent) {
            return;
        }
        if (!isPriestNpc(event.getRightClicked())) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        openMenuFromNpcNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPriestArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (!isPriestNpc(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        openMenuFromNpcNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPriestDamage(EntityDamageEvent event) {
        if (isPriestNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof PriestMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!holder.playerId().equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }
        if (!MenuItemUtil.isVisibleItem(event.getCurrentItem())) {
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        Blessing blessing = Blessing.bySlot(rawSlot);
        if (blessing != null) {
            buyBlessing(player, blessing);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof PriestMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        nextNpcOpenAt.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        if (!event.getPlugin().getName().equalsIgnoreCase("Citizens")) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            tryEnableCitizensBridge();
            schedulePriestRefreshes();
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            refreshPriestNpc(entity);
        }
    }

    private void buyBlessing(Player player, Blessing blessing) {
        EssenceManager essence = plugin.getEssenceManager();
        if (essence == null) {
            player.sendMessage(MessageUtil.error("Essence is not ready yet."));
            return;
        }
        if (!essence.spend(player, blessing.cost, "priest_" + blessing.id)) {
            player.sendMessage(MessageUtil.warn("Need " + essence.formatted(blessing.cost) + " Essence."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.85f);
            queueOpenMenuRefresh(player);
            return;
        }

        blessing.apply(this, player);
        if (blessing == Blessing.BOSS_WARD) {
            player.sendMessage(MessageUtil.success("Boss Ward active: +" + bossWardDamagePercent() + "% boss damage for 30 minutes."));
        } else {
            player.sendMessage(MessageUtil.success("Bought " + blessing.displayName + "."));
        }
        playPurchaseEffects(player, blessing);
    }

    private void fillMenu(Inventory inventory, Player player) {
        ItemStack filler = menuItem(
            Material.BLACK_STAINED_GLASS_PANE,
            MenuItemUtil.visibleName(Component.empty()),
            MenuItemUtil.visibleLore(Component.empty(), List.of())
        );
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        inventory.setItem(BALANCE_SLOT, balanceItem(player));
        inventory.setItem(INFO_SLOT, infoItem());
        for (Blessing blessing : Blessing.values()) {
            inventory.setItem(blessing.slot, blessingItem(blessing, player));
        }
        inventory.setItem(CLOSE_SLOT, menuItem(
            Material.BARRIER,
            Component.text("Close", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
            List.of(Component.text("Close this menu.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
        ));
    }

    private ItemStack infoItem() {
        return menuItem(
            Material.BEACON,
            Component.text("Blessings", NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
            List.of(
                Component.text("Spend Essence for timed buffs.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Click once. Buffs apply right away.", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text("No refunds after purchase.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
            )
        );
    }

    private ItemStack balanceItem(Player player) {
        EssenceManager essence = plugin.getEssenceManager();
        String balance = essence == null ? "0" : essence.formattedBalance(player);
        return menuItem(
            Material.ECHO_SHARD,
            Component.text("Your Essence", NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
            List.of(
                Component.text(balance + " Essence", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Trade Essence for strong timed blessings.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            )
        );
    }

    private ItemStack blessingItem(Blessing blessing, Player player) {
        EssenceManager essence = plugin.getEssenceManager();
        long balance = essence == null ? 0L : essence.balance(player);
        String cost = essence == null ? Long.toString(blessing.cost) : essence.formatted(blessing.cost);
        boolean affordable = balance >= blessing.cost;
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(blessing.summary, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        if (blessing == Blessing.BOSS_WARD) {
            lore.add(Component.text(
                "Bonus: +" + bossWardDamagePercent() + "% boss damage",
                NamedTextColor.LIGHT_PURPLE
            ).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Duration: 30 minutes", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            long remaining = bossWardRemainingMillis(player);
            if (remaining > 0L) {
                lore.add(Component.text("Active: " + formatShortDuration(remaining) + " left", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            }
        }
        lore.add(Component.empty());
        lore.add(Component.text("Cost: " + cost + " Essence", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(
            affordable ? "Click once to buy." : "Not enough Essence.",
            affordable ? NamedTextColor.GREEN : NamedTextColor.RED
        ).decoration(TextDecoration.ITALIC, false));
        return menuItem(
            blessing.icon,
            Component.text(blessing.displayName, blessing.color).decorate(TextDecoration.BOLD),
            lore
        );
    }

    private ItemStack menuItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        Component visibleName = MenuItemUtil.visibleName(name);
        meta.displayName(visibleName.decoration(TextDecoration.ITALIC, false));
        meta.lore(MenuItemUtil.visibleLore(name, lore).stream()
            .map(line -> line.decoration(TextDecoration.ITALIC, false))
            .toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_DYE);
        item.setItemMeta(meta);
        return item;
    }

    public void refreshOpenMenu(Player player) {
        if (player == null || !player.isOnline()) return;
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder(false) instanceof PriestMenuHolder holder
            && holder.playerId().equals(player.getUniqueId())) {
            fillMenu(top, player);
            player.updateInventory();
        }
    }

    private void queueOpenMenuRefresh(Player player) {
        if (player == null) return;
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(playerId);
            if (online != null) refreshOpenMenu(online);
        });
    }

    public double bossWardDamageMultiplier(Player player) {
        return bossWardRemainingMillis(player) > 0L ? BOSS_WARD_DAMAGE_MULTIPLIER : 1.0D;
    }

    private void activateBossWard(Player player) {
        player.getPersistentDataContainer().set(
            keyBossWardUntil,
            PersistentDataType.LONG,
            System.currentTimeMillis() + BOSS_WARD_DURATION_MILLIS
        );
    }

    private long bossWardRemainingMillis(Player player) {
        if (player == null) {
            return 0L;
        }

        Long until = player.getPersistentDataContainer().get(keyBossWardUntil, PersistentDataType.LONG);
        if (until == null) {
            return 0L;
        }

        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0L) {
            player.getPersistentDataContainer().remove(keyBossWardUntil);
            return 0L;
        }
        return remaining;
    }

    private int bossWardDamagePercent() {
        return (int) Math.round((BOSS_WARD_DAMAGE_MULTIPLIER - 1.0D) * 100.0D);
    }

    private String formatShortDuration(long millis) {
        long totalSeconds = Math.max(1L, (millis + 999L) / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes <= 0L) {
            return seconds + "s";
        }
        if (seconds <= 0L) {
            return minutes + "m";
        }
        return minutes + "m " + seconds + "s";
    }

    private void configurePriest(ArmorStand armorStand) {
        armorStand.customName(NPC_NAME_COMPONENT);
        armorStand.setCustomNameVisible(true);
        armorStand.setSmall(false);
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
        armorStand.addScoreboardTag("smpcore_priest_npc");
        armorStand.setDisabledSlots(EquipmentSlot.values());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            for (ArmorStand.LockType lockType : ArmorStand.LockType.values()) {
                armorStand.addEquipmentLock(slot, lockType);
            }
        }
        equipPriest(armorStand);
        armorStand.getPersistentDataContainer().set(keyNpc, PersistentDataType.BYTE, (byte) 1);
    }

    private void equipPriest(ArmorStand armorStand) {
        EntityEquipment equipment = armorStand.getEquipment();
        if (equipment == null) {
            return;
        }
        equipment.setHelmet(createPriestHead(), true);
        equipment.setChestplate(leatherArmor(Material.LEATHER_CHESTPLATE, Color.fromRGB(235, 235, 220)), true);
        equipment.setLeggings(leatherArmor(Material.LEATHER_LEGGINGS, Color.fromRGB(32, 32, 36)), true);
        equipment.setBoots(leatherArmor(Material.LEATHER_BOOTS, Color.fromRGB(24, 24, 28)), true);
        equipment.setItemInMainHand(new ItemStack(Material.ENCHANTED_BOOK), true);
        equipment.setItemInOffHand(new ItemStack(Material.AMETHYST_SHARD), true);
    }

    private ItemStack createPriestHead() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            PlayerProfile profile = Bukkit.createProfile(PRIEST_PROFILE_ID, "VeilPriest");
            profile.setProperty(new ProfileProperty("textures", PRIEST_TEXTURE_VALUE, PRIEST_TEXTURE_SIGNATURE));
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

    private Entity spawnCitizensPriest(Location location) {
        if (npcBridge == null) {
            return null;
        }
        try {
            return npcBridge.spawnPriest(location);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Citizens priest spawn failed; using armor stand fallback: " + ex.getMessage());
            return null;
        }
    }

    private void tryEnableCitizensBridge() {
        if (npcBridge != null || !Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            return;
        }

        try {
            Class<?> bridgeClass = Class.forName("me.rique.smpcore.npc.CitizensPriestBridge");
            npcBridge = (PriestNpcBridge) bridgeClass
                .getConstructor(SMPCore.class, PriestManager.class, NamespacedKey.class)
                .newInstance(plugin, this, keyNpc);
            plugin.getLogger().info("Citizens-backed " + NPC_DISPLAY_NAME + " NPCs enabled.");
        } catch (ReflectiveOperationException | LinkageError ex) {
            npcBridge = null;
            plugin.getLogger().warning("Citizens is installed but SMPCore could not hook priest NPCs: " + ex.getMessage());
        }
    }

    private int refreshLoadedPriests() {
        int refreshed = 0;
        if (npcBridge != null) {
            refreshed += npcBridge.refreshLoadedNpcs();
        }
        for (Entity entity : new ArrayList<>(findLegacyPriestNpcs())) {
            if (refreshPriestNpc(entity)) {
                refreshed++;
            }
        }
        return refreshed;
    }

    private void schedulePriestRefreshes() {
        long[] delays = {1L, 40L, 120L};
        for (long delay : delays) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                int refreshed = refreshLoadedPriests();
                if (refreshed > 0) {
                    plugin.getLogger().info("Refreshed " + refreshed + " " + NPC_DISPLAY_NAME + " NPC(s).");
                }
            }, delay);
        }
    }

    private boolean refreshPriestNpc(Entity entity) {
        if (!isPriestNpc(entity)) {
            return false;
        }
        if (npcBridge != null && entity instanceof ArmorStand) {
            Location location = entity.getLocation();
            entity.remove();
            spawnPriest(location);
            return true;
        }
        if (entity instanceof ArmorStand armorStand) {
            configurePriest(armorStand);
            return true;
        }
        if (entity instanceof Villager) {
            Location location = entity.getLocation();
            entity.remove();
            spawnPriest(location);
            return true;
        }
        return false;
    }

    private List<Entity> findLegacyPriestNpcs() {
        List<Entity> found = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isPriestNpc(entity) && (entity instanceof ArmorStand || entity instanceof Villager)) {
                    found.add(entity);
                }
            }
        }
        return found;
    }

    private boolean isPriestNpc(Entity entity) {
        if (entity == null) {
            return false;
        }
        Byte marker = entity.getPersistentDataContainer().get(keyNpc, PersistentDataType.BYTE);
        if (marker != null && marker == (byte) 1) {
            return true;
        }
        return entity.getScoreboardTags().contains("smpcore_priest_npc");
    }

    private void openMenuFromNpcNextTick(Player player) {
        if (player == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                openMenuFromNpc(player);
            }
        });
    }

    private Location priestSpawnLocation(Location origin) {
        if (origin == null || origin.getWorld() == null) {
            throw new IllegalArgumentException("Priest spawn location must have a world.");
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

    private void playPurchaseEffects(Player player, Blessing blessing) {
        Location location = player.getLocation().add(0.0, 1.0, 0.0);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.45f);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.6f, 1.2f);
        player.getWorld().spawnParticle(Particle.ENCHANT, location, 26, 0.35, 0.55, 0.35, 0.03);
        player.getWorld().spawnParticle(blessing.successParticle, location, 18, 0.3, 0.45, 0.3, 0.02);
    }

    private void spawnEffects(Location location) {
        World world = location.getWorld();
        world.playSound(location, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.35f);
        world.playSound(location, Sound.ENTITY_VILLAGER_YES, 0.8f, 1.0f);
        world.spawnParticle(Particle.ENCHANT, location.clone().add(0.0, 1.0, 0.0), 36, 0.45, 0.7, 0.45, 0.03);
        world.spawnParticle(Particle.HAPPY_VILLAGER, location.clone().add(0.0, 0.8, 0.0), 12, 0.35, 0.5, 0.35, 0.01);
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

    private enum Blessing {
        MINER(10, "miners_blessing", Material.GOLDEN_PICKAXE, "Miner's Blessing", NamedTextColor.GOLD,
            90, "Haste III, Night Vision, Fire Resistance for 20 minutes.", Particle.CRIT,
            false, List.of(
                new PotionSpec(PotionEffectType.HASTE, 20 * 60, 2),
                new PotionSpec(PotionEffectType.NIGHT_VISION, 20 * 60, 0),
                new PotionSpec(PotionEffectType.FIRE_RESISTANCE, 20 * 60, 0)
            )),
        WARDEN(11, "wardens_prayer", Material.SHIELD, "Warden's Prayer", NamedTextColor.BLUE,
            110, "Resistance II for 12 minutes and Absorption IV for 6 minutes.", Particle.TOTEM_OF_UNDYING,
            false, List.of(
                new PotionSpec(PotionEffectType.RESISTANCE, 12 * 60, 1),
                new PotionSpec(PotionEffectType.ABSORPTION, 6 * 60, 3)
            )),
        SWIFT(12, "swift_pilgrimage", Material.FEATHER, "Swift Pilgrimage", NamedTextColor.GREEN,
            70, "Speed III, Jump Boost II, Slow Falling for 12 minutes.", Particle.CLOUD,
            false, List.of(
                new PotionSpec(PotionEffectType.SPEED, 12 * 60, 2),
                new PotionSpec(PotionEffectType.JUMP_BOOST, 12 * 60, 1),
                new PotionSpec(PotionEffectType.SLOW_FALLING, 12 * 60, 0)
            )),
        CLEANSE(13, "veil_cleanse", Material.MILK_BUCKET, "Veil Cleanse", NamedTextColor.WHITE,
            45, "Clears bad effects, then gives Regeneration II and Absorption II.", Particle.INSTANT_EFFECT,
            true, List.of(
                new PotionSpec(PotionEffectType.REGENERATION, 60, 1),
                new PotionSpec(PotionEffectType.ABSORPTION, 5 * 60, 1)
            )),
        HUNTER(14, "hunters_rite", Material.IRON_SWORD, "Hunter's Rite", NamedTextColor.RED,
            125, "Strength II for 10 minutes and Regeneration II for 45 seconds.", Particle.CRIT,
            false, List.of(
                new PotionSpec(PotionEffectType.STRENGTH, 10 * 60, 1),
                new PotionSpec(PotionEffectType.REGENERATION, 45, 1)
            )),
        TIDECALLER(15, "tidecallers_grace", Material.HEART_OF_THE_SEA, "Tidecaller's Grace", NamedTextColor.AQUA,
            65, "Conduit Power, Water Breathing, Dolphin's Grace for 15 minutes.", Particle.NAUTILUS,
            false, List.of(
                new PotionSpec(PotionEffectType.CONDUIT_POWER, 15 * 60, 0),
                new PotionSpec(PotionEffectType.WATER_BREATHING, 15 * 60, 0),
                new PotionSpec(PotionEffectType.DOLPHINS_GRACE, 15 * 60, 0)
            )),
        BOSS_WARD(16, "boss_ward", Material.NETHER_STAR, "Boss Ward", NamedTextColor.DARK_PURPLE,
            75, "Deals more damage to bosses for 30 minutes.", Particle.TOTEM_OF_UNDYING,
            false, List.of());

        private final int slot;
        private final String id;
        private final Material icon;
        private final String displayName;
        private final NamedTextColor color;
        private final long cost;
        private final String summary;
        private final Particle successParticle;
        private final boolean cleanse;
        private final List<PotionSpec> effects;

        Blessing(
            int slot,
            String id,
            Material icon,
            String displayName,
            NamedTextColor color,
            long cost,
            String summary,
            Particle successParticle,
            boolean cleanse,
            List<PotionSpec> effects
        ) {
            this.slot = slot;
            this.id = id;
            this.icon = icon;
            this.displayName = displayName;
            this.color = color;
            this.cost = cost;
            this.summary = summary;
            this.successParticle = successParticle;
            this.cleanse = cleanse;
            this.effects = effects;
        }

        private static Blessing bySlot(int slot) {
            for (Blessing blessing : values()) {
                if (blessing.slot == slot) {
                    return blessing;
                }
            }
            return null;
        }

        private void apply(PriestManager manager, Player player) {
            if (cleanse) {
                clearHarmfulEffects(player);
            }
            for (PotionSpec effect : effects) {
                player.addPotionEffect(effect.toEffect());
            }
            if (this == BOSS_WARD) {
                manager.activateBossWard(player);
            }
        }

        private static void clearHarmfulEffects(Player player) {
            for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
                NamespacedKey key = effect.getType().getKey();
                if (key != null && BENEFICIAL_EFFECT_KEYS.contains(key.getKey())) {
                    continue;
                }
                player.removePotionEffect(effect.getType());
            }
        }
    }

    private record PotionSpec(PotionEffectType type, int seconds, int amplifier) {
        private PotionEffect toEffect() {
            return new PotionEffect(type, seconds * 20, amplifier, false, true, true);
        }
    }

    private record PriestMenuHolder(UUID playerId) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
