package me.rique.smpcore.player;

import io.papermc.paper.event.block.BlockPreDispenseEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerTradeEvent;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MenuDupeGuardListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Boat;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SMPCore-native replacement for the server's ClickVillagers installation.
 *
 * <p>The legacy namespace is deliberately retained so villagers, claims, and
 * portable villager items created by ClickVillagers 1.6.3 remain compatible.</p>
 */
@SuppressWarnings({"UnstableApiUsage", "deprecation"})
public final class VillagerManager implements Listener {

    public static final String PERMISSION_PICKUP = "clickvillagers.pickup";
    public static final String PERMISSION_PLACE = "clickvillagers.place";
    public static final String PERMISSION_HOPPER = "clickvillagers.hopper";
    public static final String PERMISSION_ANCHOR = "clickvillagers.anchor";
    public static final String PERMISSION_CLAIM = "clickvillagers.claim";
    public static final String PERMISSION_PARTNER = "clickvillagers.partner";
    public static final String PERMISSION_CHANGE_BIOME = "clickvillagers.change-biome";
    public static final String PERMISSION_BYPASS_CLAIMS = "clickvillagers.bypass-claims";
    public static final String PERMISSION_BYPASS_LIMITS = "clickvillagers.bypass-limits";
    public static final String PERMISSION_BYPASS_COOLDOWNS = "clickvillagers.bypass-cooldowns";
    public static final String PERMISSION_CONFIG = "clickvillagers.config";

    static final String LEGACY_NAMESPACE = "clickvillagers";
    static final NamespacedKey VILLAGER_ITEM_KEY = legacyKey("villager");
    static final NamespacedKey VILLAGER_TYPE_KEY = legacyKey("type");
    static final NamespacedKey VILLAGER_NBT_KEY = legacyKey("nbt");
    static final NamespacedKey DATA_VERSION_KEY = legacyKey("data_version");
    static final NamespacedKey OWNER_KEY = legacyKey("owner");
    static final NamespacedKey OWNER_NAME_KEY = legacyKey("owner_name");
    static final NamespacedKey TRADE_OPEN_KEY = legacyKey("trade");

    private static final int MENU_SIZE = 27;
    private static final int ANCHOR_DURATION_THRESHOLD = Integer.MAX_VALUE / 4;
    private static final Pattern LEGACY_LEVEL_WRAPPER = Pattern.compile("levels:\\{(.*?)}");
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final String DEFAULT_TEXTURE = "d14bff1a38c9154e5ec84ce5cf00c58768e068eb42b2d89a6bbd29787590106b";
    private static final String BABY_TEXTURE = "3d7788826b9ac4deaf383b387947085211447ed50fdc21bf71c230048dd5986f";
    private static final String ZOMBIE_TEXTURE = "e5e08a8776c1764c3fe6a6ddd412dfcb87f41331dad479ac96c21df4bf3ac89c";

    private final SMPCore plugin;
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, Set<String>> partners = new HashMap<>();
    private final Map<UUID, PendingPartnerInput> pendingPartnerInputs = new HashMap<>();
    private final File partnerFile;

    public VillagerManager(SMPCore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.partnerFile = new File(plugin.getDataFolder(), "villager-partners.yml");
        loadPartners();
    }

    public void shutdown() {
        pendingPartnerInputs.values().forEach(input -> input.timeoutTask().cancel());
        pendingPartnerInputs.clear();
        savePartners();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onVillagerInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity clicked = event.getRightClicked();
        if ((clicked instanceof Minecart || clicked instanceof Boat)
            && event.getPlayer().isSneaking()) {
            handleVehiclePlacement(event);
            return;
        }
        if (!(clicked instanceof LivingEntity villager) || !isSupportedVillager(villager)) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (villager instanceof ZombieVillager && !allowZombieVillagers()) {
            return;
        }
        if (!player.isSneaking()) {
            handleTradeInteraction(event, player, villager);
            return;
        }

        event.setCancelled(true);
        Material held = player.getInventory().getItemInMainHand().getType();
        if (held == Material.SHEARS) {
            toggleAnchor(player, villager);
        } else if (Tag.ITEMS_SHOVELS.isTagged(held)) {
            openClaimOrEditMenu(player, villager);
        } else if (hasOwner(villager)) {
            openEditMenu(player, villager);
        } else {
            pickUp(player, villager);
        }
    }

    private void handleTradeInteraction(
        PlayerInteractEntityEvent event,
        Player player,
        LivingEntity entity
    ) {
        if (!(entity instanceof Villager villager)) {
            return;
        }
        if (!canTrade(player, entity)) {
            event.setCancelled(true);
            player.sendMessage(Component.text(
                "This villager belongs to " + getOwnerName(entity) + ".",
                NamedTextColor.RED
            ));
            return;
        }
        if (villager.getRecipes().isEmpty()) {
            return;
        }

        // Own the complete open path. The offer list is repaired before Paper
        // creates the merchant container and sends it to either Java or Bedrock.
        event.setCancelled(true);
        normalizeOffers(villager);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !villager.isValid() || villager.isDead()) {
                return;
            }
            normalizeOffers(villager);
            player.openMerchant(villager, true);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    private void onTrade(PlayerTradeEvent event) {
        if (!infiniteTrades()) {
            return;
        }
        event.setIncreaseTradeUses(false);
        event.setRewardExp(true);
        AbstractVillager merchant = event.getVillager();
        if (merchant instanceof Villager villager) {
            MerchantRecipe trade = event.getTrade();
            int villagerExperience = repairedVillagerExperience(
                villager.getVillagerLevel(),
                trade.getVillagerExperience()
            );
            if (trade.getVillagerExperience() != villagerExperience) {
                MerchantRecipe repairedTrade = new MerchantRecipe(trade);
                repairedTrade.setVillagerExperience(villagerExperience);
                repairedTrade.setExperienceReward(true);
                event.setTrade(repairedTrade);
            }
            normalizeOffers(villager);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (villager.isValid() && !villager.isDead()) {
                    normalizeOffers(villager);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    private void onMerchantOpen(InventoryOpenEvent event) {
        if (!infiniteTrades()
            || !(event.getView().getTopInventory() instanceof MerchantInventory merchantInventory)
            || !(merchantInventory.getMerchant() instanceof Villager villager)) {
            return;
        }
        normalizeOffers(villager);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    private void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!infiniteTrades()) {
            return;
        }
        event.getEntities().forEach(entity -> {
            if (entity instanceof Villager villager) {
                normalizeOffers(villager);
            }
        });
    }

    @EventHandler(priority = EventPriority.NORMAL)
    private void onServerLoad(ServerLoadEvent event) {
        RepairResult result = repairLoadedVillagers();
        plugin.getLogger().info(
            "Native villager system checked " + result.checked()
                + " loaded villagers and repaired " + result.repaired() + "."
        );
    }

    public RepairResult repairLoadedVillagers() {
        int checked = 0;
        int repaired = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                checked++;
                if (normalizeOffers(villager)) {
                    repaired++;
                }
            }
        }
        return new RepairResult(checked, repaired);
    }

    /**
     * Exercises Paper's real villager recipe storage without leaving an entity
     * or item behind. This is intentionally available to the admin command so
     * deployments can verify the exact server build instead of trusting a unit
     * test compiled against the API.
     */
    public StockSelfTestResult runLiveStockSelfTest() {
        if (!infiniteTrades()) {
            return new StockSelfTestResult(false, "Infinite villager stock is disabled.");
        }
        World world = Bukkit.getWorlds().stream().findFirst().orElse(null);
        if (world == null) {
            return new StockSelfTestResult(false, "No loaded world is available.");
        }

        Villager probe = null;
        try {
            probe = world.spawn(world.getSpawnLocation(), Villager.class, villager -> {
                villager.setSilent(true);
                villager.setAI(false);
                villager.setInvulnerable(true);
                villager.setPersistent(false);
                villager.setProfession(Villager.Profession.FARMER);
            });
            MerchantRecipe exhausted = new MerchantRecipe(new ItemStack(Material.BREAD), 1);
            exhausted.addIngredient(new ItemStack(Material.EMERALD));
            exhausted.setUses(1);
            exhausted.setExperienceReward(false);
            exhausted.setVillagerExperience(0);
            probe.setRecipes(List.of(exhausted));

            boolean repaired = normalizeOffers(probe);
            List<MerchantRecipe> stored = probe.getRecipes();
            if (!repaired || stored.size() != 1) {
                return new StockSelfTestResult(false, "The exhausted offer was not replaced.");
            }
            MerchantRecipe verified = stored.getFirst();
            if (verified.getUses() != 0 || verified.getMaxUses() < 1_000_000) {
                return new StockSelfTestResult(
                    false,
                    "Paper returned uses=" + verified.getUses()
                        + ", maxUses=" + verified.getMaxUses() + "."
                );
            }
            if (!verified.hasExperienceReward()) {
                return new StockSelfTestResult(false, "The repaired offer still suppresses player XP.");
            }
            if (verified.getVillagerExperience() <= 0) {
                return new StockSelfTestResult(false, "The repaired offer still suppresses villager XP.");
            }
            if (verified.getIngredients().size() != 1
                || verified.getIngredients().getFirst().getType() != Material.EMERALD
                || verified.getResult().getType() != Material.BREAD) {
                return new StockSelfTestResult(false, "The offer contents changed during repair.");
            }
            return new StockSelfTestResult(
                true,
                "Paper stored the offer as fresh infinite stock with player and villager XP enabled."
            );
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Native villager live self-test failed", exception);
            return new StockSelfTestResult(false, exception.getClass().getSimpleName()
                + ": " + Objects.toString(exception.getMessage(), "no message"));
        } finally {
            if (probe != null) {
                probe.remove();
            }
        }
    }

    boolean normalizeOffers(Villager villager) {
        if (!infiniteTrades()) {
            return false;
        }
        List<MerchantRecipe> current = villager.getRecipes();
        if (current.isEmpty()) {
            return false;
        }
        boolean changed = false;
        List<MerchantRecipe> repaired = new ArrayList<>(current.size());
        for (MerchantRecipe recipe : current) {
            MerchantRecipe copy = copyWithFreshStock(recipe, villager.getVillagerLevel());
            changed |= offerNeedsRepair(recipe);
            repaired.add(copy);
        }
        if (changed) {
            villager.setRecipes(repaired);
        }
        return changed;
    }

    static MerchantRecipe copyWithFreshStock(MerchantRecipe recipe, int villagerLevel) {
        MerchantRecipe copy = new MerchantRecipe(recipe);
        copy.setUses(0);
        copy.setExperienceReward(true);
        copy.setVillagerExperience(repairedVillagerExperience(
            villagerLevel,
            copy.getVillagerExperience()
        ));
        // A second guard protects the merchant if another listener ignores
        // PlayerTradeEvent#setIncreaseTradeUses(false.
        copy.setMaxUses(Math.max(1_000_000, copy.getMaxUses()));
        return copy;
    }

    static boolean offerNeedsRepair(MerchantRecipe recipe) {
        return offerNeedsRepair(
            recipe.getUses(),
            recipe.getMaxUses(),
            recipe.hasExperienceReward(),
            recipe.getVillagerExperience()
        );
    }

    static boolean offerNeedsRepair(
        int uses,
        int maxUses,
        boolean hasExperienceReward,
        int villagerExperience
    ) {
        return stockNeedsRepair(uses, maxUses)
            || !hasExperienceReward
            || villagerExperience <= 0;
    }

    static int repairedVillagerExperience(int villagerLevel, int configuredExperience) {
        if (configuredExperience > 0) {
            return configuredExperience;
        }
        return switch (Math.clamp(villagerLevel, 1, 5)) {
            case 1 -> 2;
            case 2 -> 5;
            case 3 -> 10;
            case 4 -> 15;
            default -> 30;
        };
    }

    static boolean stockNeedsRepair(int uses, int maxUses) {
        return uses != 0 || maxUses < 1_000_000;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private void onPlace(BlockPlaceEvent event) {
        if (!isPortableVillager(event.getItemInHand())) {
            return;
        }
        PlayerInventory inventory = event.getPlayer().getInventory();
        EquipmentSlot slot = event.getHand();
        ItemStack actualItem = inventory.getItem(slot);
        if (!isPortableVillager(actualItem)) {
            return;
        }
        HeldPortable held = new HeldPortable(actualItem, slot);
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission(PERMISSION_PLACE)) {
            noPermission(player);
            return;
        }
        Block placed = event.getBlockPlaced();
        Location location = placed.getLocation().add(0.5D, 0.0D, 0.5D);
        location.setYaw(normalizeYaw(player.getLocation().getYaw() + 180.0F));
        try {
            LivingEntity spawned = spawnPortableVillager(held.item(), location);
            if (spawned instanceof Villager villager) {
                normalizeOffers(villager);
            }
            held.decrement(player.getInventory());
            playPlaceEffect(location, placed.getRelative(BlockFace.DOWN));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Component.text(
                "That villager could not be placed safely. The item was not consumed.",
                NamedTextColor.RED
            ));
            plugin.getLogger().log(Level.WARNING, "Could not place a portable villager", exception);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private void onPreDispense(BlockPreDispenseEvent event) {
        if (!enableDispensers() || !isPortableVillager(event.getItemStack())) {
            return;
        }
        BlockState state = event.getBlock().getState();
        if (!(state instanceof Dispenser dispenser)
            || !(state.getBlockData() instanceof Directional directional)) {
            return;
        }
        event.setCancelled(true);
        Location location = event.getBlock()
            .getRelative(directional.getFacing())
            .getLocation()
            .add(0.5D, 0.0D, 0.5D);
        try {
            LivingEntity spawned = spawnPortableVillager(event.getItemStack(), location);
            if (spawned instanceof Villager villager) {
                normalizeOffers(villager);
            }
            ItemStack remaining = event.getItemStack().clone();
            remaining.subtract();
            dispenser.getSnapshotInventory().setItem(
                event.getSlot(),
                remaining.getAmount() <= 0 ? new ItemStack(Material.AIR) : remaining
            );
            dispenser.update(true, false);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not dispense a portable villager", exception);
        }
    }

    private void handleVehiclePlacement(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        HeldPortable held = findPortableItem(player.getInventory());
        if (held == null || !player.hasPermission(PERMISSION_PLACE)) {
            return;
        }
        Entity vehicle = event.getRightClicked();
        if (!hasPassengerSpace(vehicle)) {
            return;
        }
        event.setCancelled(true);
        try {
            LivingEntity spawned = spawnPortableVillager(held.item(), vehicle.getLocation());
            if (spawned instanceof Villager villager) {
                normalizeOffers(villager);
            }
            if (!vehicle.addPassenger(spawned)) {
                spawned.remove();
                throw new IllegalArgumentException("Vehicle rejected villager passenger");
            }
            held.decrement(player.getInventory());
            Sound sound = vehicle instanceof Minecart
                ? Sound.BLOCK_METAL_BREAK
                : Sound.BLOCK_WOOD_BREAK;
            player.playSound(vehicle, sound, 1.0F, 0.5F);
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Component.text(
                "That villager could not be placed safely. The item was not consumed.",
                NamedTextColor.RED
            ));
            plugin.getLogger().log(Level.WARNING, "Could not place villager into vehicle", exception);
        }
    }

    private boolean hasPassengerSpace(Entity vehicle) {
        if (vehicle instanceof RideableMinecart || vehicle instanceof ChestBoat) {
            return vehicle.getPassengers().isEmpty();
        }
        return vehicle instanceof Boat && vehicle.getPassengers().size() < 2;
    }

    private void pickUp(Player player, LivingEntity villager) {
        if (!player.hasPermission(PERMISSION_PICKUP)
            && !(claimedVillagersBypassPermissions() && isOwner(villager, player))) {
            noPermission(player);
            return;
        }
        if (hasOwner(villager)
            && !isOwner(villager, player)
            && !player.hasPermission(PERMISSION_BYPASS_CLAIMS)) {
            player.sendMessage(Component.text(
                "This villager belongs to " + getOwnerName(villager) + ".",
                NamedTextColor.RED
            ));
            return;
        }
        if (!isOwner(villager, player) && hasCooldown(player)) {
            long seconds = Math.max(1L, (cooldownUntil.get(player.getUniqueId())
                - System.currentTimeMillis() + 999L) / 1000L);
            player.sendActionBar(Component.text(
                "Wait " + seconds + "s before moving another villager.",
                NamedTextColor.RED
            ));
            return;
        }
        try {
            ItemStack item = createPortableVillager(villager);
            giveOrDrop(player, item);
            Location location = villager.getLocation().clone().add(0.0D, 0.25D, 0.0D);
            villager.remove();
            location.getWorld().spawnParticle(Particle.SWEEP_ATTACK, location, 1);
            location.getWorld().playSound(location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.5F);
            player.sendActionBar(Component.text("Villager picked up.", NamedTextColor.GREEN));
            applyCooldown(player);
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Component.text(
                "The villager could not be saved safely, so it was left in place.",
                NamedTextColor.RED
            ));
            plugin.getLogger().log(Level.WARNING, "Could not serialize villager", exception);
        }
    }

    private ItemStack createPortableVillager(LivingEntity entity) {
        if (!isSupportedVillager(entity)) {
            throw new IllegalArgumentException("Unsupported entity type");
        }
        if (entity instanceof Villager villager) {
            normalizeOffers(villager);
        }
        EntitySnapshot snapshot = entity.createSnapshot();
        if (snapshot == null) {
            throw new IllegalArgumentException("Paper returned no entity snapshot");
        }
        ItemStack item = createVillagerHead(entity);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalArgumentException("Villager head has no item metadata");
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(VILLAGER_ITEM_KEY, PersistentDataType.BOOLEAN, true);
        data.set(
            VILLAGER_TYPE_KEY,
            PersistentDataType.STRING,
            entity instanceof ZombieVillager ? "ZOMBIE" : "VILLAGER"
        );
        data.set(VILLAGER_NBT_KEY, PersistentDataType.STRING, snapshot.getAsString());
        data.set(DATA_VERSION_KEY, PersistentDataType.INTEGER, Bukkit.getUnsafe().getDataVersion());
        item.setItemMeta(meta);
        return item;
    }

    private LivingEntity spawnPortableVillager(ItemStack item, Location location) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalArgumentException("Portable villager item has no metadata");
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        if (!data.has(VILLAGER_ITEM_KEY, PersistentDataType.BOOLEAN)) {
            throw new IllegalArgumentException("Item is not a portable villager");
        }
        String typeName = data.get(VILLAGER_TYPE_KEY, PersistentDataType.STRING);
        String nbt = data.get(VILLAGER_NBT_KEY, PersistentDataType.STRING);
        if (typeName == null || nbt == null || nbt.isBlank()) {
            throw new IllegalArgumentException("Portable villager data is incomplete");
        }
        EntityType type = "ZOMBIE".equalsIgnoreCase(typeName)
            ? EntityType.ZOMBIE_VILLAGER
            : EntityType.VILLAGER;
        String repairedNbt = appendEntityType(fixLegacySnapshot(nbt), type);
        try {
            EntitySnapshot snapshot = Bukkit.getEntityFactory().createEntitySnapshot(repairedNbt);
            Entity spawned = snapshot.createEntity(location);
            if (!(spawned instanceof LivingEntity living) || !isSupportedVillager(living)) {
                if (spawned != null) {
                    spawned.remove();
                }
                throw new IllegalArgumentException("Snapshot did not create a villager");
            }
            living.setVelocity(new Vector());
            return living;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Paper could not read the villager snapshot", exception);
        }
    }

    static String fixLegacySnapshot(String nbt) {
        Matcher matcher = LEGACY_LEVEL_WRAPPER.matcher(nbt);
        return matcher.replaceAll(matchResult -> Matcher.quoteReplacement(matchResult.group(1)));
    }

    private static String appendEntityType(String nbt, EntityType type) {
        String typeData = "id:\"" + type.getKey() + "\"";
        if (nbt.contains(typeData)) {
            return nbt;
        }
        int closingBrace = nbt.lastIndexOf('}');
        if (closingBrace < 0) {
            throw new IllegalArgumentException("Villager snapshot is malformed");
        }
        String prefix = nbt.substring(0, closingBrace).trim();
        if (!prefix.endsWith("{") && !prefix.endsWith(",")) {
            prefix += ",";
        }
        return prefix + typeData + nbt.substring(closingBrace);
    }

    public boolean isPortableVillager(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer()
            .has(VILLAGER_ITEM_KEY, PersistentDataType.BOOLEAN);
    }

    private HeldPortable findPortableItem(PlayerInventory inventory) {
        ItemStack mainHand = inventory.getItemInMainHand();
        if (isPortableVillager(mainHand)) {
            return new HeldPortable(mainHand, EquipmentSlot.HAND);
        }
        ItemStack offHand = inventory.getItemInOffHand();
        if (isPortableVillager(offHand)) {
            return new HeldPortable(offHand, EquipmentSlot.OFF_HAND);
        }
        return null;
    }

    private ItemStack createVillagerHead(LivingEntity entity) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Component.text(portableName(entity), NamedTextColor.YELLOW));
        List<Component> lore = new ArrayList<>();
        if (hasOwner(entity)) {
            lore.add(Component.text("Owner: " + getOwnerName(entity), NamedTextColor.GOLD));
        }
        if (isAnchored(entity)) {
            lore.add(Component.text("Anchored", NamedTextColor.DARK_AQUA));
        }
        if (hasOwner(entity) && !isTradeOpen(entity)) {
            lore.add(Component.text("Trading closed", NamedTextColor.RED));
        }
        if (entity instanceof Villager villager && showTrades() && !villager.getRecipes().isEmpty()) {
            lore.add(Component.empty());
            lore.add(Component.text("Trades", NamedTextColor.GRAY));
            int shown = 0;
            for (MerchantRecipe recipe : villager.getRecipes()) {
                if (shown++ >= 12) {
                    lore.add(Component.text(
                        "+" + (villager.getRecipes().size() - 12) + " more",
                        NamedTextColor.DARK_GRAY
                    ));
                    break;
                }
                lore.add(Component.text(formatTrade(recipe), NamedTextColor.GRAY));
            }
        }
        meta.lore(lore);
        setVillagerTexture(meta, entity);
        item.setItemMeta(meta);
        return item;
    }

    private String portableName(LivingEntity entity) {
        Component customName = entity.customName();
        if (customName != null) {
            return "\"" + PLAIN_TEXT.serialize(customName) + "\"";
        }
        boolean adult = ((Ageable) entity).isAdult();
        if (entity instanceof ZombieVillager) {
            return adult ? "Zombie Villager" : "Baby Zombie Villager";
        }
        if (!adult) {
            return "Baby Villager";
        }
        Villager.Profession profession = ((Villager) entity).getProfession();
        if (profession == Villager.Profession.NONE) {
            return "Villager";
        }
        return titleCase(profession.key().asMinimalString().replace('_', ' ')) + " Villager";
    }

    private String formatTrade(MerchantRecipe recipe) {
        String ingredients = recipe.getIngredients().stream()
            .map(this::formatItem)
            .reduce((left, right) -> left + " + " + right)
            .orElse("Nothing");
        return ingredients + " -> " + formatItem(recipe.getResult());
    }

    private String formatItem(ItemStack item) {
        if (item.getItemMeta() instanceof EnchantmentStorageMeta book
            && !book.getStoredEnchants().isEmpty()) {
            String enchantments = book.getStoredEnchants().entrySet().stream()
                .map(entry -> titleCase(entry.getKey().getKey().getKey().replace('_', ' '))
                    + " " + roman(entry.getValue()))
                .reduce((left, right) -> left + " + " + right)
                .orElse("Enchanted Book");
            return item.getAmount() + " " + enchantments;
        }
        ItemMeta meta = item.getItemMeta();
        Component displayName = meta == null ? null : meta.displayName();
        String name = displayName == null
            ? titleCase(item.getType().name().replace('_', ' '))
            : PLAIN_TEXT.serialize(displayName);
        return item.getAmount() + " " + name;
    }

    private void setVillagerTexture(SkullMeta meta, LivingEntity entity) {
        try {
            String texture;
            if (entity instanceof ZombieVillager) {
                texture = ZOMBIE_TEXTURE;
            } else if (!((Ageable) entity).isAdult()) {
                texture = BABY_TEXTURE;
            } else {
                texture = professionTextures().getOrDefault(
                    ((Villager) entity).getProfession(),
                    DEFAULT_TEXTURE
                );
            }
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
            profile.getTextures().setSkin(
                URI.create("https://textures.minecraft.net/texture/" + texture).toURL()
            );
            meta.setOwnerProfile(profile);
        } catch (Exception exception) {
            plugin.getLogger().warning(
                "Could not apply portable villager texture: " + exception.getMessage()
            );
        }
    }

    private void openClaimOrEditMenu(Player player, LivingEntity villager) {
        if (hasOwner(villager)) {
            openEditMenu(player, villager);
            return;
        }
        if (!player.hasPermission(PERMISSION_CLAIM)) {
            noPermission(player);
            return;
        }
        if (hasCooldown(player)) {
            player.sendActionBar(Component.text(
                "Wait before claiming another villager.",
                NamedTextColor.RED
            ));
            return;
        }
        Inventory menu = Bukkit.createInventory(
            new VillagerMenuHolder(villager.getUniqueId(), MenuView.CLAIM),
            MENU_SIZE,
            Component.text("Claim Villager")
        );
        fillMenu(menu);
        menu.setItem(13, menuItem(
            Material.GOLDEN_SHOVEL,
            "Claim Villager",
            NamedTextColor.GOLD,
            "Protect and manage this villager."
        ));
        player.openInventory(menu);
        player.playSound(villager, Sound.BLOCK_CHEST_OPEN, 1.0F, 0.8F);
    }

    private void openEditMenu(Player player, LivingEntity villager) {
        if (!isOwner(villager, player) && !player.hasPermission(PERMISSION_BYPASS_CLAIMS)) {
            player.sendMessage(Component.text(
                "This villager belongs to " + getOwnerName(villager) + ".",
                NamedTextColor.RED
            ));
            return;
        }
        Inventory menu = Bukkit.createInventory(
            new VillagerMenuHolder(villager.getUniqueId(), MenuView.EDIT),
            MENU_SIZE,
            Component.text(getOwnerName(villager) + "'s Villager")
        );
        fillMenu(menu);
        menu.setItem(10, menuItem(
            Material.PLAYER_HEAD,
            "Pick Up Villager",
            NamedTextColor.YELLOW,
            "Stores this exact villager safely."
        ));
        List<String> partnerLore = new ArrayList<>();
        partnerLore.add("Click, then type a player name.");
        partnerLore.add("Type the same name again to remove.");
        getPartners(getOwnerUuid(villager)).stream().sorted()
            .forEach(name -> partnerLore.add("Partner: " + name));
        menu.setItem(12, menuItem(
            Material.WRITABLE_BOOK,
            "Trading Partners",
            NamedTextColor.AQUA,
            partnerLore.toArray(String[]::new)
        ));
        menu.setItem(13, menuItem(
            isTradeOpen(villager) ? Material.EMERALD : Material.REDSTONE,
            isTradeOpen(villager) ? "Trading Open" : "Trading Closed",
            isTradeOpen(villager) ? NamedTextColor.GREEN : NamedTextColor.RED,
            "Click to toggle public trading."
        ));
        menu.setItem(14, menuItem(
            Material.BRUSH,
            "Change Biome",
            NamedTextColor.GREEN,
            "Changes the villager's appearance."
        ));
        menu.setItem(16, menuItem(
            Material.BARRIER,
            "Unclaim Villager",
            NamedTextColor.RED,
            "Removes ownership and protection."
        ));
        player.openInventory(menu);
        player.playSound(villager, Sound.BLOCK_CHEST_OPEN, 1.0F, 0.8F);
    }

    private void openBiomeMenu(Player player, LivingEntity villager) {
        Inventory menu = Bukkit.createInventory(
            new VillagerMenuHolder(villager.getUniqueId(), MenuView.BIOME),
            MENU_SIZE,
            Component.text("Change Villager Biome")
        );
        fillMenu(menu);
        menu.setItem(18, menuItem(
            Material.ARROW,
            "Back",
            NamedTextColor.YELLOW,
            "Return to villager settings."
        ));
        Villager.Type current = getVillagerType(villager);
        List<BiomeChoice> choices = biomeChoices();
        for (int i = 0; i < choices.size(); i++) {
            BiomeChoice choice = choices.get(i);
            menu.setItem(10 + i, menuItem(
                choice.icon(),
                choice.name() + (choice.type() == current ? " (Selected)" : ""),
                choice.type() == current ? NamedTextColor.GREEN : NamedTextColor.WHITE,
                "Click to apply."
            ));
        }
        player.openInventory(menu);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    private void onMenuClick(InventoryClickEvent event) {
        InventoryHolder rawHolder = event.getView().getTopInventory().getHolder(false);
        if (!(rawHolder instanceof VillagerMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
            || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        LivingEntity villager = findVillager(holder.villagerId());
        if (villager == null) {
            player.closeInventory();
            player.sendMessage(Component.text("That villager is no longer available.", NamedTextColor.RED));
            return;
        }
        int slot = event.getRawSlot();
        switch (holder.view()) {
            case CLAIM -> handleClaimMenu(player, villager, slot);
            case EDIT -> handleEditMenu(player, villager, slot);
            case BIOME -> handleBiomeMenu(player, villager, slot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    private void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof VillagerMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void handleClaimMenu(Player player, LivingEntity villager, int slot) {
        if (slot != 13 || hasOwner(villager)) {
            return;
        }
        if (!player.hasPermission(PERMISSION_CLAIM) || hasCooldown(player)) {
            player.closeInventory();
            return;
        }
        setOwner(villager, player);
        applyCooldown(player);
        player.playSound(player, Sound.BLOCK_ANVIL_DESTROY, 1.0F, 1.0F);
        player.sendMessage(Component.text("Villager claimed.", NamedTextColor.GREEN));
        openEditMenu(player, villager);
    }

    private void handleEditMenu(Player player, LivingEntity villager, int slot) {
        if (!isOwner(villager, player) && !player.hasPermission(PERMISSION_BYPASS_CLAIMS)) {
            player.closeInventory();
            return;
        }
        switch (slot) {
            case 10 -> {
                player.closeInventory();
                pickUp(player, villager);
            }
            case 12 -> beginPartnerInput(player, villager);
            case 13 -> {
                setTradeOpen(villager, !isTradeOpen(villager));
                player.playSound(
                    player,
                    isTradeOpen(villager) ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BASS,
                    1.0F,
                    1.0F
                );
                openEditMenu(player, villager);
            }
            case 14 -> {
                if (!player.hasPermission(PERMISSION_CHANGE_BIOME)) {
                    noPermission(player);
                    return;
                }
                openBiomeMenu(player, villager);
            }
            case 16 -> {
                removeOwner(villager);
                player.closeInventory();
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0F, 0.6F);
                player.sendMessage(Component.text("Villager unclaimed.", NamedTextColor.YELLOW));
            }
            default -> {
            }
        }
    }

    private void handleBiomeMenu(Player player, LivingEntity villager, int slot) {
        if (!isOwner(villager, player) && !player.hasPermission(PERMISSION_BYPASS_CLAIMS)) {
            player.closeInventory();
            return;
        }
        if (slot == 18) {
            openEditMenu(player, villager);
            return;
        }
        int index = slot - 10;
        List<BiomeChoice> choices = biomeChoices();
        if (index < 0 || index >= choices.size()) {
            return;
        }
        BiomeChoice choice = choices.get(index);
        setVillagerType(villager, choice.type());
        player.playSound(villager, Sound.BLOCK_COMPOSTER_FILL_SUCCESS, 1.0F, 0.5F);
        player.playSound(villager, Sound.BLOCK_AZALEA_PLACE, 1.0F, 1.5F);
        openBiomeMenu(player, villager);
    }

    private void beginPartnerInput(Player player, LivingEntity villager) {
        if (!player.hasPermission(PERMISSION_PARTNER)) {
            noPermission(player);
            return;
        }
        player.closeInventory();
        PendingPartnerInput existing = pendingPartnerInputs.remove(player.getUniqueId());
        if (existing != null) {
            existing.timeoutTask().cancel();
        }
        BukkitTask timeout = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (pendingPartnerInputs.remove(player.getUniqueId()) != null && player.isOnline()) {
                player.sendMessage(Component.text("Partner entry timed out.", NamedTextColor.GRAY));
            }
        }, 20L * 10L);
        pendingPartnerInputs.put(
            player.getUniqueId(),
            new PendingPartnerInput(villager.getUniqueId(), timeout)
        );
        player.sendMessage(Component.text(
            "Type a player name in chat. Type cancel to stop.",
            NamedTextColor.YELLOW
        ));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onPartnerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PendingPartnerInput pending = pendingPartnerInputs.remove(player.getUniqueId());
        if (pending == null) {
            return;
        }
        event.setCancelled(true);
        pending.timeoutTask().cancel();
        String input = PLAIN_TEXT.serialize(event.message()).trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> applyPartnerInput(
            player,
            pending.villagerId(),
            input
        ));
    }

    private void applyPartnerInput(Player player, UUID villagerId, String input) {
        if (!player.isOnline() || input.equalsIgnoreCase("cancel")) {
            return;
        }
        LivingEntity villager = findVillager(villagerId);
        if (villager == null || !isOwner(villager, player)) {
            player.sendMessage(Component.text("That villager is no longer available.", NamedTextColor.RED));
            return;
        }
        if (!isValidPartner(player, input)) {
            player.sendMessage(Component.text("That is not a valid previous player.", NamedTextColor.RED));
            return;
        }
        UUID owner = player.getUniqueId();
        Set<String> ownerPartners = partners.computeIfAbsent(owner, ignored -> new HashSet<>());
        String existing = ownerPartners.stream()
            .filter(name -> name.equalsIgnoreCase(input))
            .findFirst()
            .orElse(null);
        if (existing != null) {
            ownerPartners.remove(existing);
            player.sendMessage(Component.text(existing + " removed as a trading partner.", NamedTextColor.YELLOW));
        } else {
            if (ownerPartners.size() >= partnerLimit()
                && !player.hasPermission(PERMISSION_BYPASS_LIMITS)) {
                player.sendMessage(Component.text("You reached the trading partner limit.", NamedTextColor.RED));
                return;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(input);
            String storedName = target.getName() == null ? input : target.getName();
            ownerPartners.add(storedName);
            player.sendMessage(Component.text(storedName + " added as a trading partner.", NamedTextColor.GREEN));
        }
        savePartners();
    }

    private boolean isValidPartner(Player owner, String input) {
        if (input.length() < 3 || input.contains(" ") || input.equalsIgnoreCase(owner.getName())) {
            return false;
        }
        return !validatePartnerNames() || Bukkit.getOfflinePlayer(input).hasPlayedBefore();
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent event) {
        PendingPartnerInput pending = pendingPartnerInputs.remove(event.getPlayer().getUniqueId());
        if (pending != null) {
            pending.timeoutTask().cancel();
        }
        cooldownUntil.remove(event.getPlayer().getUniqueId());
    }

    private void toggleAnchor(Player player, LivingEntity villager) {
        if (!player.hasPermission(PERMISSION_ANCHOR)) {
            noPermission(player);
            return;
        }
        if (hasOwner(villager)
            && !isOwner(villager, player)
            && !player.hasPermission(PERMISSION_BYPASS_CLAIMS)) {
            player.sendMessage(Component.text(
                "This villager belongs to " + getOwnerName(villager) + ".",
                NamedTextColor.RED
            ));
            return;
        }
        Location location = villager.getLocation();
        if (isAnchored(villager)) {
            villager.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
            location.getWorld().spawnParticle(Particle.WAX_OFF, location, 10, 0.2D, 0.0D, 0.2D, 2.0D);
            player.sendActionBar(Component.text("Villager unanchored.", NamedTextColor.YELLOW));
        } else {
            villager.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SLOWNESS,
                Integer.MAX_VALUE,
                255,
                true,
                false,
                false
            ));
            location.getWorld().spawnParticle(Particle.WAX_ON, location, 10, 0.2D, 0.0D, 0.2D, 2.0D);
            player.sendActionBar(Component.text("Villager anchored.", NamedTextColor.GREEN));
        }
        location.getWorld().playSound(location, Sound.BLOCK_CHAIN_PLACE, 1.0F, 0.7F);
    }

    private boolean isAnchored(LivingEntity villager) {
        return villager.getActivePotionEffects().stream().anyMatch(effect ->
            effect.getType().equals(org.bukkit.potion.PotionEffectType.SLOWNESS)
                && effect.getAmplifier() >= 255
                && effect.getDuration() > ANCHOR_DURATION_THRESHOLD
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onClaimedDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity villager)
            || !isSupportedVillager(villager)
            || !hasOwner(villager)) {
            return;
        }
        if (villager instanceof Villager && claimedDamage()) {
            return;
        }
        if (villager instanceof ZombieVillager && !claimedZombiesPassive()) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.KILL
            && !claimedImmuneKillCommand()) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onClaimedZombieAttack(EntityDamageByEntityEvent event) {
        if (claimedZombiesPassive()
            && event.getDamager() instanceof ZombieVillager zombie
            && hasOwner(zombie)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onTransform(EntityTransformEvent event) {
        if (!(event.getEntity() instanceof LivingEntity original)
            || !(event.getTransformedEntity() instanceof LivingEntity transformed)
            || !isSupportedVillager(original)
            || !isSupportedVillager(transformed)) {
            return;
        }
        copyLegacyString(original, transformed, OWNER_KEY);
        copyLegacyString(original, transformed, OWNER_NAME_KEY);
        Boolean tradeOpen = original.getPersistentDataContainer()
            .get(TRADE_OPEN_KEY, PersistentDataType.BOOLEAN);
        if (tradeOpen != null) {
            transformed.getPersistentDataContainer()
                .set(TRADE_OPEN_KEY, PersistentDataType.BOOLEAN, tradeOpen);
        }
        if (isAnchored(original)) {
            transformed.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SLOWNESS,
                Integer.MAX_VALUE,
                255,
                true,
                false,
                false
            ));
        }
        if (transformed instanceof Villager villager) {
            plugin.getServer().getScheduler().runTask(plugin, () -> normalizeOffers(villager));
        }
    }

    private void copyLegacyString(LivingEntity from, LivingEntity to, NamespacedKey key) {
        String value = from.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (value != null) {
            to.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        }
    }

    private boolean canTrade(Player player, LivingEntity villager) {
        if (!hasOwner(villager) || isTradeOpen(villager)) {
            return true;
        }
        if (isOwner(villager, player) || player.hasPermission(PERMISSION_BYPASS_CLAIMS)) {
            return true;
        }
        UUID owner = getOwnerUuid(villager);
        return owner != null && getPartners(owner).stream()
            .anyMatch(name -> name.equalsIgnoreCase(player.getName()));
    }

    private boolean hasOwner(LivingEntity villager) {
        return getOwnerUuid(villager) != null;
    }

    private boolean isOwner(LivingEntity villager, Player player) {
        return player.getUniqueId().equals(getOwnerUuid(villager));
    }

    private UUID getOwnerUuid(LivingEntity villager) {
        String stored = villager.getPersistentDataContainer().get(OWNER_KEY, PersistentDataType.STRING);
        if (stored == null) {
            return null;
        }
        try {
            return UUID.fromString(stored);
        } catch (IllegalArgumentException malformed) {
            plugin.getLogger().warning(
                "Ignoring malformed villager owner UUID on " + villager.getUniqueId()
            );
            return null;
        }
    }

    private String getOwnerName(LivingEntity villager) {
        UUID uuid = getOwnerUuid(villager);
        if (uuid == null) {
            return "?";
        }
        String currentName = Bukkit.getOfflinePlayer(uuid).getName();
        if (currentName != null) {
            return currentName;
        }
        return villager.getPersistentDataContainer()
            .getOrDefault(OWNER_NAME_KEY, PersistentDataType.STRING, "?");
    }

    private void setOwner(LivingEntity villager, Player owner) {
        PersistentDataContainer data = villager.getPersistentDataContainer();
        data.set(OWNER_KEY, PersistentDataType.STRING, owner.getUniqueId().toString());
        data.set(OWNER_NAME_KEY, PersistentDataType.STRING, owner.getName());
        data.set(TRADE_OPEN_KEY, PersistentDataType.BOOLEAN, true);
    }

    private void removeOwner(LivingEntity villager) {
        PersistentDataContainer data = villager.getPersistentDataContainer();
        data.remove(OWNER_KEY);
        data.remove(OWNER_NAME_KEY);
        data.remove(TRADE_OPEN_KEY);
    }

    private boolean isTradeOpen(LivingEntity villager) {
        return villager.getPersistentDataContainer()
            .getOrDefault(TRADE_OPEN_KEY, PersistentDataType.BOOLEAN, true);
    }

    private void setTradeOpen(LivingEntity villager, boolean open) {
        villager.getPersistentDataContainer()
            .set(TRADE_OPEN_KEY, PersistentDataType.BOOLEAN, open);
    }

    private Set<String> getPartners(UUID owner) {
        if (owner == null) {
            return Set.of();
        }
        return Set.copyOf(partners.getOrDefault(owner, Set.of()));
    }

    private void loadPartners() {
        File source = partnerFile;
        if (!source.isFile()) {
            File pluginsDirectory = plugin.getDataFolder().getParentFile();
            File legacy = new File(pluginsDirectory, "ClickVillagers/data/partners.json");
            if (legacy.isFile()) {
                source = legacy;
            }
        }
        if (!source.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source);
        ConfigurationSection root = yaml.getConfigurationSection("partners");
        if (root == null) {
            root = yaml;
        }
        for (String rawUuid : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(rawUuid);
                partners.put(uuid, new HashSet<>(root.getStringList(rawUuid)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipped malformed villager partner owner: " + rawUuid);
            }
        }
        if (!source.equals(partnerFile)) {
            savePartners();
            plugin.getLogger().info("Migrated ClickVillagers partner data into SMPCore.");
        }
    }

    private void savePartners() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("format", 1);
        yaml.set("saved-at", Instant.now().toString());
        Map<String, List<String>> serialized = new LinkedHashMap<>();
        partners.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> serialized.put(
                entry.getKey().toString(),
                entry.getValue().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()
            ));
        yaml.createSection("partners", serialized);
        File parent = partnerFile.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            plugin.getLogger().severe("Could not create villager partner data directory.");
            return;
        }
        File temporary = new File(parent, partnerFile.getName() + ".next");
        try {
            yaml.save(temporary);
            Files.move(
                temporary.toPath(),
                partnerFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            );
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save villager partners", exception);
            if (temporary.isFile() && !temporary.delete()) {
                plugin.getLogger().warning("Could not remove temporary villager partner file.");
            }
        }
    }

    private LivingEntity findVillager(UUID uuid) {
        Entity entity = Bukkit.getEntity(uuid);
        return entity instanceof LivingEntity living && isSupportedVillager(living)
            ? living
            : null;
    }

    private boolean hasCooldown(Player player) {
        if (player.hasPermission(PERMISSION_BYPASS_COOLDOWNS)) {
            return false;
        }
        return cooldownUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    private void applyCooldown(Player player) {
        int seconds = Math.max(0, plugin.getConfig().getInt("villagers.cooldown-seconds", 2));
        if (seconds > 0) {
            cooldownUntil.put(
                player.getUniqueId(),
                System.currentTimeMillis() + seconds * 1000L
            );
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        PlayerInventory inventory = player.getInventory();
        if (inventory.getItemInMainHand().getType().isAir()) {
            inventory.setItemInMainHand(item);
            return;
        }
        inventory.addItem(item).values().forEach(remainder ->
            player.getWorld().dropItemNaturally(player.getLocation(), remainder)
        );
    }

    private void playPlaceEffect(Location location, Block below) {
        location.getWorld().playSound(location, Sound.ENTITY_PLAYER_ATTACK_WEAK, 1.0F, 0.5F);
        location.getWorld().spawnParticle(
            Particle.BLOCK,
            location,
            30,
            below.getBlockData()
        );
    }

    private static ItemStack menuItem(
        Material material,
        String name,
        NamedTextColor color,
        String... loreLines
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Component.text(name, color));
        meta.lore(List.of(loreLines).stream()
            .map(line -> Component.text(line, NamedTextColor.GRAY))
            .toList());
        item.setItemMeta(meta);
        return item;
    }

    private static void fillMenu(Inventory inventory) {
        ItemStack filler = menuItem(
            Material.GRAY_STAINED_GLASS_PANE,
            " ",
            NamedTextColor.DARK_GRAY
        );
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private static boolean isSupportedVillager(LivingEntity entity) {
        return entity instanceof Villager || entity instanceof ZombieVillager;
    }

    private static Villager.Type getVillagerType(LivingEntity entity) {
        return entity instanceof Villager villager
            ? villager.getVillagerType()
            : ((ZombieVillager) entity).getVillagerType();
    }

    private static void setVillagerType(LivingEntity entity, Villager.Type type) {
        if (entity instanceof Villager villager) {
            villager.setVillagerType(type);
        } else {
            ((ZombieVillager) entity).setVillagerType(type);
        }
    }

    private static List<BiomeChoice> biomeChoices() {
        return List.of(
            new BiomeChoice(Villager.Type.PLAINS, Material.OAK_SAPLING, "Plains"),
            new BiomeChoice(Villager.Type.DESERT, Material.DEAD_BUSH, "Desert"),
            new BiomeChoice(Villager.Type.JUNGLE, Material.JUNGLE_SAPLING, "Jungle"),
            new BiomeChoice(Villager.Type.SNOW, Material.FERN, "Snow"),
            new BiomeChoice(Villager.Type.SAVANNA, Material.ACACIA_SAPLING, "Savanna"),
            new BiomeChoice(Villager.Type.SWAMP, Material.BLUE_ORCHID, "Swamp"),
            new BiomeChoice(Villager.Type.TAIGA, Material.SPRUCE_SAPLING, "Taiga")
        );
    }

    private static Map<Villager.Profession, String> professionTextures() {
        return Map.ofEntries(
            Map.entry(Villager.Profession.FISHERMAN, "61d644761f706d31c99a593c8d5f7cbbd4372d73fbee8464f482fa6c139d97d4"),
            Map.entry(Villager.Profession.ARMORER, "f522db92f188ebc7713cf35b4cbaed1cfe2642a5986c3bde993f5cfb3727664c"),
            Map.entry(Villager.Profession.BUTCHER, "c6774d2df515eceae9eed291c1b40f94adf71df0ab81c7191402e1a45b3a2087"),
            Map.entry(Villager.Profession.CARTOGRAPHER, "94248dd0680305ad73b214e8c6b00094e27a4ddd8034676921f905130b858bdb"),
            Map.entry(Villager.Profession.CLERIC, "a8856eaafad96d76fa3b5edd0e3b5f45ee49a3067306ad94df9ab3bd5b2d142d"),
            Map.entry(Villager.Profession.FARMER, "d01e035a3d8d6126072bcbe52a97913ace93552a99995b5d4070d6783a31e909"),
            Map.entry(Villager.Profession.FLETCHER, "d831830a7bd3b1ab05beb98dc2f9fc5ea550b3cf649fd94d483da7cd39f7c063"),
            Map.entry(Villager.Profession.LEATHERWORKER, "f76cf8b7378e889395d538e6354a17a3de6b294bb6bf8db9c701951c68d3c0e6"),
            Map.entry(Villager.Profession.LIBRARIAN, "e66a53fc707ce1ff88a576ef40200ce8d49fae4acad1e3b3789c7d1cc1cc541a"),
            Map.entry(Villager.Profession.MASON, "2c02c3ffd5705ab488b305d57ff0168e26de70fd3f739e839661ab947dff37b1"),
            Map.entry(Villager.Profession.NITWIT, "35e799dbfaf98287dfbafce970612c8f075168977aacc30989d34a4a5fcdf429"),
            Map.entry(Villager.Profession.SHEPHERD, "19e04a752596f939f581930414561b175454d45a0506501e7d2488295a5d5de"),
            Map.entry(Villager.Profession.TOOLSMITH, "7dfa07fd1244eb8945f4ededd00426750b77ef5dfbaf03ed775633459ece415a"),
            Map.entry(Villager.Profession.WEAPONSMITH, "5e409b958bc4fe045e95d325e6e97a533137e33fec7042ac027b30bb693a9d42")
        );
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0F;
        return normalized < -180.0F
            ? normalized + 360.0F
            : normalized > 180.0F ? normalized - 360.0F : normalized;
    }

    private static String titleCase(String text) {
        StringBuilder result = new StringBuilder();
        for (String word : text.toLowerCase(Locale.ROOT).split(" ")) {
            if (word.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static String roman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(number);
        };
    }

    private static NamespacedKey legacyKey(String key) {
        NamespacedKey namespacedKey = NamespacedKey.fromString(LEGACY_NAMESPACE + ":" + key);
        return Objects.requireNonNull(namespacedKey, "Invalid legacy villager key " + key);
    }

    private static void noPermission(Player player) {
        player.sendMessage(Component.text("You do not have permission to do that.", NamedTextColor.RED));
    }

    private boolean infiniteTrades() {
        return plugin.getConfig().getBoolean("villagers.infinite-trades", true);
    }

    private boolean allowZombieVillagers() {
        return plugin.getConfig().getBoolean("villagers.allow-zombie-villagers", true);
    }

    private boolean claimedDamage() {
        return plugin.getConfig().getBoolean("villagers.claimed-damage", false);
    }

    private boolean claimedZombiesPassive() {
        return plugin.getConfig().getBoolean("villagers.claimed-zombies-passive", true);
    }

    private boolean claimedImmuneKillCommand() {
        return plugin.getConfig().getBoolean("villagers.claimed-immune-kill-command", true);
    }

    private boolean claimedVillagersBypassPermissions() {
        return plugin.getConfig().getBoolean(
            "villagers.claimed-villagers-bypass-permissions",
            false
        );
    }

    private boolean validatePartnerNames() {
        return plugin.getConfig().getBoolean("villagers.validate-partner-names", true);
    }

    private int partnerLimit() {
        return Math.max(1, plugin.getConfig().getInt("villagers.partner-limit", 10));
    }

    private boolean showTrades() {
        return plugin.getConfig().getBoolean("villagers.show-trades", true);
    }

    private boolean enableDispensers() {
        return plugin.getConfig().getBoolean("villagers.enable-dispensers", false);
    }

    public record RepairResult(int checked, int repaired) {
    }

    public record StockSelfTestResult(boolean passed, String detail) {
    }

    private enum MenuView {
        CLAIM,
        EDIT,
        BIOME
    }

    private record VillagerMenuHolder(UUID villagerId, MenuView view)
        implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record PendingPartnerInput(UUID villagerId, BukkitTask timeoutTask) {
    }

    private record BiomeChoice(Villager.Type type, Material icon, String name) {
    }

    private record HeldPortable(ItemStack item, EquipmentSlot slot) {
        private void decrement(PlayerInventory inventory) {
            ItemStack remaining = item.clone();
            remaining.subtract();
            inventory.setItem(
                slot,
                remaining.getAmount() <= 0 ? new ItemStack(Material.AIR) : remaining
            );
        }
    }
}
