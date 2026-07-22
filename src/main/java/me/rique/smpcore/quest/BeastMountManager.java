package me.rique.smpcore.quest;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Donkey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.AbstractHorseInventory;
import org.bukkit.inventory.HorseInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class BeastMountManager implements Listener {

    private static final double RECALL_DISTANCE_SQUARED = 64.0D * 64.0D;
    private static final long SUMMON_COOLDOWN_MS = 2_000L;
    private static final String STORAGE_ROOT = "donkey-storage";

    private final SMPCore plugin;
    private final BeastwardenManager beastwarden;
    private final NamespacedKey ownerKey;
    private final NamespacedKey typeKey;
    private final NamespacedKey choiceKey;
    private final Map<UUID, UUID> activeMounts = new HashMap<>();
    private final Map<UUID, Long> nextSummonAt = new HashMap<>();
    private final File dataFile;
    private YamlConfiguration data;
    private BukkitTask validationTask;
    private final Set<UUID> queuedStorageSaves = ConcurrentHashMap.newKeySet();

    BeastMountManager(SMPCore plugin, BeastwardenManager beastwarden) {
        this.plugin = plugin;
        this.beastwarden = beastwarden;
        this.ownerKey = new NamespacedKey(plugin, "beast_mount_owner");
        this.typeKey = new NamespacedKey(plugin, "beast_mount_type");
        this.choiceKey = new NamespacedKey(plugin, "beast_mount_choice");
        this.dataFile = new File(plugin.getDataFolder(), "beastwarden-mounts.yml");
        this.data = YamlConfiguration.loadConfiguration(dataFile);
    }

    void start() {
        cleanupOrphans();
        validationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::validateMounts, 20L, 20L);
    }

    void shutdown() {
        if (validationTask != null) {
            validationTask.cancel();
            validationTask = null;
        }
        for (UUID ownerId : new ArrayList<>(activeMounts.keySet())) {
            recall(ownerId, false);
        }
        activeMounts.clear();
        nextSummonAt.clear();
        queuedStorageSaves.clear();
        saveNow();
    }

    MountType selectedType(Player player) {
        String raw = player.getPersistentDataContainer().get(choiceKey, PersistentDataType.STRING);
        return MountType.from(raw);
    }

    void selectType(Player player, MountType type) {
        if (player == null || type == null) {
            return;
        }
        recall(player, false);
        player.getPersistentDataContainer().set(choiceKey, PersistentDataType.STRING, type.id);
        player.sendMessage(MessageUtil.success("Your recalled steed is now a <white>" + type.displayName + "</white>."));
        player.playSound(player.getLocation(), Sound.ENTITY_HORSE_SADDLE, 0.75F, type == MountType.HORSE ? 1.1F : 0.85F);
    }

    boolean isActive(Player player) {
        return player != null && current(player.getUniqueId()) != null;
    }

    String status(Player player) {
        AbstractHorse active = player == null ? null : current(player.getUniqueId());
        if (active == null) {
            return "Recalled " + selectedType(player).displayName;
        }
        return "Summoned " + selectedType(player).displayName + " at "
            + active.getLocation().getBlockX() + ", " + active.getLocation().getBlockY() + ", " + active.getLocation().getBlockZ();
    }

    boolean toggle(Player player) {
        if (isActive(player)) {
            return recall(player, true);
        }
        return summon(player);
    }

    boolean summon(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (!beastwarden.hasFullArmorSet(player)) {
            player.sendMessage(MessageUtil.warn("Wear the full <white>Wildbound Regalia</white> to call your steed."));
            return false;
        }
        if (plugin.getBossDungeonManager() != null && plugin.getBossDungeonManager().isDungeonWorld(player.getWorld())) {
            player.sendMessage(MessageUtil.warn("Your steed cannot enter the boss dungeon."));
            return false;
        }
        AbstractHorse existing = current(player.getUniqueId());
        if (existing != null) {
            player.sendMessage(MessageUtil.info("Your steed is already nearby. Use <white>/steed recall</white> first."));
            return false;
        }
        long now = System.currentTimeMillis();
        long next = nextSummonAt.getOrDefault(player.getUniqueId(), 0L);
        if (next > now) {
            player.sendMessage(MessageUtil.warn("Wait a moment before calling your steed again."));
            return false;
        }
        nextSummonAt.put(player.getUniqueId(), now + SUMMON_COOLDOWN_MS);

        MountType type = selectedType(player);
        Location spawn = safeSpawn(player);
        AbstractHorse mount = type == MountType.DONKEY
            ? spawn.getWorld().spawn(spawn, Donkey.class, donkey -> configureDonkey(player, donkey))
            : spawn.getWorld().spawn(spawn, Horse.class, horse -> configureHorse(player, horse));
        activeMounts.put(player.getUniqueId(), mount.getUniqueId());
        player.getWorld().spawnParticle(Particle.CLOUD, mount.getLocation().add(0.0D, 0.8D, 0.0D), 18, 0.45D, 0.35D, 0.45D, 0.03D);
        player.playSound(player.getLocation(), type == MountType.HORSE ? Sound.ENTITY_HORSE_ANGRY : Sound.ENTITY_DONKEY_AMBIENT, 0.8F, 1.1F);
        player.sendMessage(MessageUtil.success("You called <white>" + mountName(player, type) + "</white>."));
        return true;
    }

    boolean recall(Player player, boolean notify) {
        return player != null && recall(player.getUniqueId(), notify);
    }

    private boolean recall(UUID ownerId, boolean notify) {
        AbstractHorse mount = current(ownerId);
        activeMounts.remove(ownerId);
        if (mount == null) {
            if (notify) {
                Player player = Bukkit.getPlayer(ownerId);
                if (player != null) player.sendMessage(MessageUtil.info("Your steed is already recalled."));
            }
            return false;
        }
        saveDonkeyStorage(ownerId, mount);
        clearStorage(mount);
        Location location = mount.getLocation();
        mount.eject();
        mount.remove();
        location.getWorld().spawnParticle(Particle.POOF, location.add(0.0D, 0.8D, 0.0D), 16, 0.35D, 0.35D, 0.35D, 0.02D);
        Player player = Bukkit.getPlayer(ownerId);
        if (notify && player != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_HORSE_BREATHE, 0.6F, 0.75F);
            player.sendMessage(MessageUtil.info("Your steed returned to the Beastwarden's stable."));
        }
        return true;
    }

    boolean ownsMount(Player player, Entity entity) {
        if (player == null || !(entity instanceof AbstractHorse)) {
            return false;
        }
        return player.getUniqueId().toString().equals(entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING));
    }

    boolean isManagedMount(Entity entity) {
        return entity instanceof AbstractHorse
            && entity.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        activeMounts.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        recall(event.getPlayer(), false);
        nextSummonAt.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!isActive(event.getPlayer()) || event.getTo() == null) return;
        if (event.getFrom().getWorld() != event.getTo().getWorld()
            || event.getFrom().distanceSquared(event.getTo()) > RECALL_DISTANCE_SQUARED) {
            recall(event.getPlayer(), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        recall(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!isManagedMount(event.getRightClicked())) {
            return;
        }
        if (!ownsMount(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.warn("That recalled steed belongs to another player."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        AbstractHorse mount = managedHolder(event.getInventory());
        if (mount == null || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!ownsMount(player, mount)) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.warn("Only the steed's owner can open this inventory."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        AbstractHorse mount = managedHolder(event.getView().getTopInventory());
        if (mount == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!ownsMount(player, mount)) {
            event.setCancelled(true);
            return;
        }
        int equipmentSlots = equipmentSlots(mount.getInventory());
        if (event.getRawSlot() >= 0 && event.getRawSlot() < equipmentSlots) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.warn("The steed's saddle and armor are bound in place."));
            return;
        }
        queueStorageSave(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        AbstractHorse mount = managedHolder(event.getView().getTopInventory());
        if (mount == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int equipmentSlots = equipmentSlots(mount.getInventory());
        if (!ownsMount(player, mount) || event.getRawSlots().stream().anyMatch(slot -> slot < equipmentSlots)) {
            event.setCancelled(true);
            return;
        }
        queueStorageSave(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        AbstractHorse mount = managedHolder(event.getInventory());
        if (mount != null) {
            UUID ownerId = ownerId(mount);
            if (ownerId != null) saveDonkeyStorage(ownerId, mount);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (managedHolder(event.getSource()) != null || managedHolder(event.getDestination()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (isManagedMount(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (isManagedMount(event.getMother()) || isManagedMount(event.getFather())) {
            event.setCancelled(true);
        }
    }

    private void validateMounts() {
        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(activeMounts.entrySet())) {
            Player owner = Bukkit.getPlayer(entry.getKey());
            AbstractHorse mount = current(entry.getKey());
            if (owner == null || !owner.isOnline() || owner.isDead() || mount == null
                || !beastwarden.hasFullArmorSet(owner)
                || plugin.getBossDungeonManager() != null && plugin.getBossDungeonManager().isDungeonWorld(owner.getWorld())
                || owner.getWorld() != mount.getWorld()
                || owner.getLocation().distanceSquared(mount.getLocation()) > RECALL_DISTANCE_SQUARED) {
                recall(entry.getKey(), owner != null && owner.isOnline());
                continue;
            }
            restoreEquipment(mount);
        }
    }

    private void configureHorse(Player owner, Horse horse) {
        configureBase(owner, horse, MountType.HORSE);
        Horse.Color[] colors = Horse.Color.values();
        Horse.Style[] styles = Horse.Style.values();
        int seed = owner.getUniqueId().hashCode();
        horse.setColor(colors[Math.floorMod(seed, colors.length)]);
        horse.setStyle(styles[Math.floorMod(seed >>> 8, styles.length)]);
        setAttribute(horse, Attribute.MAX_HEALTH, 30.0D);
        setAttribute(horse, Attribute.MOVEMENT_SPEED, 0.255D);
        horse.setJumpStrength(0.78D);
        horse.getInventory().setArmor(new ItemStack(Material.DIAMOND_HORSE_ARMOR));
    }

    private void configureDonkey(Player owner, Donkey donkey) {
        configureBase(owner, donkey, MountType.DONKEY);
        donkey.setCarryingChest(true);
        setAttribute(donkey, Attribute.MAX_HEALTH, 28.0D);
        setAttribute(donkey, Attribute.MOVEMENT_SPEED, 0.225D);
        donkey.setJumpStrength(0.68D);
        loadDonkeyStorage(owner.getUniqueId(), donkey);
    }

    private void configureBase(Player owner, AbstractHorse mount, MountType type) {
        mount.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        mount.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.id);
        mount.addScoreboardTag("smpcore_beast_mount");
        mount.customName(Component.text(mountName(owner, type), NamedTextColor.GOLD));
        mount.setCustomNameVisible(true);
        mount.setOwner(owner);
        mount.setTamed(true);
        mount.setDomestication(mount.getMaxDomestication());
        mount.setPersistent(false);
        mount.setRemoveWhenFarAway(false);
        mount.setCanPickupItems(false);
        mount.setCollidable(false);
        mount.setInvulnerable(true);
        mount.getInventory().setSaddle(new ItemStack(Material.SADDLE));
    }

    private void restoreEquipment(AbstractHorse mount) {
        AbstractHorseInventory inventory = mount.getInventory();
        if (inventory.getSaddle() == null || inventory.getSaddle().getType() != Material.SADDLE) {
            inventory.setSaddle(new ItemStack(Material.SADDLE));
        }
        if (inventory instanceof HorseInventory horseInventory
            && (horseInventory.getArmor() == null || horseInventory.getArmor().getType() != Material.DIAMOND_HORSE_ARMOR)) {
            horseInventory.setArmor(new ItemStack(Material.DIAMOND_HORSE_ARMOR));
        }
    }

    private void loadDonkeyStorage(UUID ownerId, Donkey donkey) {
        ItemStack[] storage = donkey.getInventory().getStorageContents();
        List<?> saved = data.getList(STORAGE_ROOT + "." + ownerId, List.of());
        ItemStack[] restored = new ItemStack[storage.length];
        for (int i = 0; i < restored.length && i < saved.size(); i++) {
            Object value = saved.get(i);
            if (value instanceof ItemStack item && !item.getType().isAir()) {
                restored[i] = item.clone();
            }
        }
        donkey.getInventory().setStorageContents(restored);
    }

    private void saveDonkeyStorage(UUID ownerId, AbstractHorse mount) {
        if (!(mount instanceof Donkey donkey) || !donkey.isValid()) {
            return;
        }
        ItemStack[] storage = donkey.getInventory().getStorageContents();
        List<ItemStack> copy = new ArrayList<>(storage.length);
        for (ItemStack item : storage) {
            copy.add(item == null || item.getType().isAir() ? null : item.clone());
        }
        data.set(STORAGE_ROOT + "." + ownerId, copy);
        saveNow();
    }

    private void clearStorage(AbstractHorse mount) {
        ItemStack[] storage = mount.getInventory().getStorageContents();
        Arrays.fill(storage, null);
        mount.getInventory().setStorageContents(storage);
    }

    private void queueStorageSave(UUID ownerId) {
        if (!queuedStorageSaves.add(ownerId)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            queuedStorageSaves.remove(ownerId);
            AbstractHorse mount = current(ownerId);
            if (mount != null) saveDonkeyStorage(ownerId, mount);
        });
    }

    private void saveNow() {
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Could not create plugin data directory");
            }
            File temporary = new File(dataFile.getParentFile(), dataFile.getName() + ".next");
            data.save(temporary);
            try {
                Files.move(temporary.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save Beastwarden donkey storage: " + ex.getMessage());
        }
    }

    private void cleanupOrphans() {
        for (World world : Bukkit.getWorlds()) {
            for (AbstractHorse horse : world.getEntitiesByClass(AbstractHorse.class)) {
                if (isManagedMount(horse)) {
                    horse.getInventory().clear();
                    horse.remove();
                }
            }
        }
    }

    private AbstractHorse current(UUID ownerId) {
        UUID entityId = activeMounts.get(ownerId);
        Entity entity = entityId == null ? null : Bukkit.getEntity(entityId);
        if (!(entity instanceof AbstractHorse horse) || !horse.isValid() || !isManagedMount(horse)) {
            activeMounts.remove(ownerId);
            return null;
        }
        return horse;
    }

    private AbstractHorse managedHolder(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        InventoryHolder holder = inventory.getHolder(false);
        return holder instanceof AbstractHorse horse && isManagedMount(horse) ? horse : null;
    }

    private UUID ownerId(AbstractHorse mount) {
        String raw = mount.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private int equipmentSlots(AbstractHorseInventory inventory) {
        return Math.max(0, inventory.getSize() - inventory.getStorageContents().length);
    }

    private Location safeSpawn(Player player) {
        Location origin = player.getLocation();
        for (int radius = 2; radius <= 4; radius++) {
            for (int offset = 0; offset < 8; offset++) {
                double angle = (Math.PI * 2.0D * offset) / 8.0D;
                Location candidate = origin.clone().add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
                if (candidate.getBlock().isPassable()
                    && candidate.clone().add(0.0D, 1.0D, 0.0D).getBlock().isPassable()
                    && candidate.clone().add(0.0D, -1.0D, 0.0D).getBlock().getType().isSolid()) {
                    candidate.setYaw(origin.getYaw());
                    candidate.setPitch(0.0F);
                    return candidate;
                }
            }
        }
        return origin.clone().add(0.0D, 0.15D, 0.0D);
    }

    private String mountName(Player owner, MountType type) {
        return owner.getName() + "'s " + (type == MountType.HORSE ? "Wildsteed" : "Trail Donkey");
    }

    private void setAttribute(AbstractHorse mount, Attribute attribute, double value) {
        AttributeInstance instance = mount.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
        if (attribute == Attribute.MAX_HEALTH) mount.setHealth(value);
    }

    enum MountType {
        HORSE("horse", "Wildsteed"),
        DONKEY("donkey", "Trail Donkey");

        private final String id;
        private final String displayName;

        MountType(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        String id() {
            return id;
        }

        String displayName() {
            return displayName;
        }

        static MountType from(String raw) {
            if (raw == null) return HORSE;
            String normalized = raw.toLowerCase(Locale.ROOT);
            return normalized.equals(DONKEY.id) ? DONKEY : HORSE;
        }
    }
}
