package me.rique.smpcore.spawn;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.npc.GuideNpcManager;
import me.rique.smpcore.npc.GuideNpcManager.GuideNpcType;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class SpawnLifeManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int DOG_MENU_SIZE = 27;
    private static final int ILLUSIONER_CURSE_DURATION_TICKS = 20 * 120;
    private static final int FETCH_SESSION_TIMEOUT_TICKS = 20 * 30;
    private static final int RETURN_HOME_TIMEOUT_TICKS = 20 * 15;
    private static final int FETCH_PATH_RETRY_TICKS = 10;
    private static final double FETCH_START_RANGE = 28.0D;
    private static final double FETCH_SESSION_RANGE = 40.0D;
    private static final double FETCH_PICKUP_RANGE_SQUARED = 1.75D * 1.75D;
    private static final double FETCH_RETURN_RANGE_SQUARED = 2.0D * 2.0D;
    private static final double AMBIENT_HEARING_RANGE_SQUARED = 20.0D * 20.0D;
    private static final long INTERACTION_COOLDOWN_MS = 1_800L;
    private static final long FEED_COOLDOWN_MS = 60_000L;
    private static final String ACTION_TAKE_STICK = "take_fetch_stick";
    private static final String ACTION_CLOSE = "close";

    private static final List<GuideNpcType> AMBIENT_TYPES = List.of(
        GuideNpcType.FETCH_HOUND,
        GuideNpcType.TOWN_CAT,
        GuideNpcType.TOWN_FOX,
        GuideNpcType.TOWN_PARROT,
        GuideNpcType.TOWN_BAKER,
        GuideNpcType.TOWN_MASON,
        GuideNpcType.TOWN_COURIER,
        GuideNpcType.TOWN_DOCKHAND,
        GuideNpcType.TOWN_SEAMSTRESS,
        GuideNpcType.TAVERN_HOST,
        GuideNpcType.TAVERN_REGULAR,
        GuideNpcType.TAVERN_TIPSY
    );

    private static final Map<GuideNpcType, List<String>> DIALOGUE = dialogue();

    private final SMPCore plugin;
    private final NamespacedKey keyFetchStick;
    private final NamespacedKey keyFetchOwner;
    private final NamespacedKey keyFetchToken;
    private final NamespacedKey keyMenuAction;
    private final NamespacedKey keyIllusionerFound;
    private final Map<UUID, PendingFetchDrop> pendingDrops = new HashMap<>();
    private final Map<UUID, FetchSession> fetchByDog = new HashMap<>();
    private final Map<UUID, UUID> fetchDogByPlayer = new HashMap<>();
    private final Map<InteractionKey, Long> nextInteractionAt = new HashMap<>();
    private final Map<InteractionKey, Long> nextFeedAt = new HashMap<>();
    private final Map<AmbientKey, Long> nextAmbientAt = new HashMap<>();
    private final Map<UUID, BukkitTask> illusionerDialogues = new HashMap<>();

    private SpawnLifeNavigator navigator;
    private BukkitTask fetchTask;
    private BukkitTask ambientTask;
    private int carriedStickSweepTicks;

    public SpawnLifeManager(SMPCore plugin) {
        this.plugin = plugin;
        this.keyFetchStick = new NamespacedKey(plugin, "fetch_stick");
        this.keyFetchOwner = new NamespacedKey(plugin, "fetch_stick_owner");
        this.keyFetchToken = new NamespacedKey(plugin, "fetch_stick_token");
        this.keyMenuAction = new NamespacedKey(plugin, "spawn_life_menu_action");
        this.keyIllusionerFound = new NamespacedKey(plugin, "hidden_illusioner_found");
    }

    public void start() {
        tryEnableCitizensNavigator();
        fetchTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickFetchSessions, 2L, 2L);
        ambientTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAmbientNpcs, 100L, 40L);
    }

    public void shutdown() {
        if (fetchTask != null) {
            fetchTask.cancel();
            fetchTask = null;
        }
        if (ambientTask != null) {
            ambientTask.cancel();
            ambientTask = null;
        }
        for (BukkitTask task : illusionerDialogues.values()) {
            task.cancel();
        }
        illusionerDialogues.clear();
        for (FetchSession session : new ArrayList<>(fetchByDog.values())) {
            restoreAndClose(session);
        }
        for (PendingFetchDrop pending : new ArrayList<>(pendingDrops.values())) {
            removePendingFetchItems(pending);
        }
        pendingDrops.clear();
        fetchByDog.clear();
        fetchDogByPlayer.clear();
        nextInteractionAt.clear();
        nextFeedAt.clear();
        nextAmbientAt.clear();
        navigator = null;

        for (Player player : Bukkit.getOnlinePlayers()) {
            removeFetchSticks(player);
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof DogMenuHolder) {
                player.closeInventory();
            }
        }
    }

    public void openFromNpc(Player player, GuideNpcType type) {
        openFromNpc(player, type, null);
    }

    public void openFromNpc(Player player, GuideNpcType type, Entity interactionTarget) {
        if (player == null || type == null || !type.isSpawnLife()) {
            return;
        }
        if (tryFeed(player, type, interactionTarget)) {
            return;
        }
        switch (type) {
            case FETCH_HOUND -> {
                speak(player, type);
                openDogMenu(player);
            }
            case HIDDEN_ILLUSIONER -> beginIllusionerEncounter(player);
            case TOWN_CAT, TOWN_FOX, TOWN_PARROT,
                 TOWN_BAKER, TOWN_MASON, TOWN_COURIER, TOWN_DOCKHAND, TOWN_SEAMSTRESS,
                 TAVERN_HOST, TAVERN_REGULAR, TAVERN_TIPSY -> speak(player, type);
            default -> {
            }
        }
    }

    private void openDogMenu(Player player) {
        Inventory menu = Bukkit.createInventory(new DogMenuHolder(player.getUniqueId()), DOG_MENU_SIZE,
            MM.deserialize("<gradient:#f59e0b:#fde68a><bold>Play Fetch with Biscuit</bold></gradient>"));
        ItemStack filler = menuItem(Material.BLACK_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, List.of(), null);
        for (int slot = 0; slot < menu.getSize(); slot++) {
            menu.setItem(slot, filler);
        }
        menu.setItem(10, menuItem(
            Material.BONE,
            "<gold><bold>Biscuit</bold></gold>",
            List.of(
                "<gray>Biscuit loves chasing his marked stick.</gray>",
                "<gray>Throw it somewhere he can reach.</gray>"
            ),
            null
        ));
        menu.setItem(13, menuItem(
            Material.STICK,
            "<yellow><bold>Take the Fetch Stick</bold></yellow>",
            List.of(
                "<white>Drop it within 28 blocks of Biscuit.</white>",
                "<gray>He will collect it and run back to you.</gray>",
                "",
                "<green>Click to take it.</green>"
            ),
            ACTION_TAKE_STICK
        ));
        menu.setItem(16, menuItem(
            Material.PAPER,
            "<aqua><bold>How Fetch Works</bold></aqua>",
            List.of(
                "<gray>1. Find a clear spot near Biscuit.</gray>",
                "<gray>2. Drop the marked stick.</gray>",
                "<gray>3. Wait for him to bring it back.</gray>",
                "",
                "<dark_gray>One use. It fades if you leave Biscuit.</dark_gray>"
            ),
            null
        ));
        menu.setItem(22, menuItem(Material.BARRIER, "<red>Close</red>", List.of(), ACTION_CLOSE));
        player.openInventory(menu);
        player.playSound(player.getLocation(), Sound.ENTITY_WOLF_AMBIENT, SoundCategory.NEUTRAL, 0.7F, 1.12F);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDogMenuClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder(false) instanceof DogMenuHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player) || !holder.playerId().equals(player.getUniqueId())) {
                event.getWhoClicked().closeInventory();
                return;
            }
            if (event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()
                || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) {
                return;
            }
            String action = actionOf(event.getCurrentItem());
            if (ACTION_CLOSE.equals(action)) {
                player.closeInventory();
            } else if (ACTION_TAKE_STICK.equals(action)) {
                giveMenuFetchStick(player);
            }
            return;
        }

        blockFetchStickStorage(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDogMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof DogMenuHolder) {
            event.setCancelled(true);
            return;
        }
        if (isFetchStick(event.getOldCursor())) {
            int topSize = event.getView().getTopInventory().getSize();
            if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFetchStickCraft(CraftItemEvent event) {
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (isFetchStick(ingredient)) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    player.sendMessage(MessageUtil.warn("Biscuit's stick is only for fetch."));
                }
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFetchStickDropValidate(PlayerDropItemEvent event) {
        Item dropped = event.getItemDrop();
        if (!isFetchStick(dropped.getItemStack())) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.getUniqueId().toString().equals(fetchOwner(dropped.getItemStack()))) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.warn("That fetch stick belongs to someone else."));
            return;
        }
        if (navigator == null) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.warn("Biscuit cannot play fetch until Citizens is ready."));
            return;
        }
        if (fetchDogByPlayer.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.warn("Finish the current throw first."));
            return;
        }

        GuideNpcManager guideNpcs = plugin.getGuideNpcManager();
        Entity dog = guideNpcs == null ? null : guideNpcs.nearestLoadedNpc(
            GuideNpcType.FETCH_HOUND,
            dropped.getLocation(),
            FETCH_START_RANGE
        );
        if (dog == null) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> removeFetchSticks(player));
            player.sendMessage(MM.deserialize("<gray><italic>The fetch stick fades when Biscuit is too far away.</italic></gray>"));
            return;
        }
        if (fetchByDog.containsKey(dog.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.warn("Biscuit is already chasing another stick."));
            return;
        }
        String token = fetchToken(dropped.getItemStack());
        if (token == null) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> removeFetchSticks(player));
            player.sendMessage(MessageUtil.warn("That fetch stick was damaged. Take another one from Biscuit."));
            return;
        }
        protectFetchDrop(dropped, player.getUniqueId());
        pendingDrops.put(dropped.getUniqueId(), new PendingFetchDrop(
            player.getUniqueId(),
            dog.getUniqueId(),
            dropped.getUniqueId(),
            dog.getLocation(),
            dropped.getLocation(),
            dropped.getVelocity(),
            dropped.getItemStack(),
            token
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFetchStickDropCommit(PlayerDropItemEvent event) {
        UUID itemId = event.getItemDrop().getUniqueId();
        PendingFetchDrop pending = pendingDrops.get(itemId);
        if (pending == null) {
            return;
        }
        if (event.isCancelled()) {
            pendingDrops.remove(itemId, pending);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            PendingFetchDrop committed = pendingDrops.remove(itemId);
            if (committed != null) {
                commitFetchDrop(committed);
            }
        });
    }

    private void commitFetchDrop(PendingFetchDrop pending) {
        Player player = Bukkit.getPlayer(pending.playerId());
        if (player == null || !player.isOnline()) {
            removePendingFetchItems(pending);
            return;
        }

        Entity dog = activeFetchDog(pending);
        if (dog == null) {
            removePendingFetchItems(pending);
            removeFetchSticks(player);
            player.sendMessage(MessageUtil.warn("Biscuit moved or unloaded. Take another stick when he is nearby."));
            return;
        }
        if (fetchDogByPlayer.containsKey(player.getUniqueId()) || fetchByDog.containsKey(dog.getUniqueId())) {
            removePendingFetchItems(pending);
            removeFetchSticks(player);
            player.sendMessage(MessageUtil.warn("Biscuit is already fetching. Wait for him to finish."));
            return;
        }

        removeFetchSticks(player);
        Item item = findPendingFetchItem(pending);
        if (item == null) {
            item = recreatePendingFetchItem(pending);
        }
        if (item == null || item.isDead() || !item.isInWorld()) {
            removePendingFetchItems(pending);
            plugin.getLogger().warning("Fetch item could not be committed for " + player.getName() + ".");
            player.sendMessage(MessageUtil.warn("The fetch stick vanished. Take another one from Biscuit."));
            return;
        }

        protectFetchDrop(item, player.getUniqueId());

        FetchSession session = new FetchSession(
            player.getUniqueId(),
            dog.getUniqueId(),
            item.getUniqueId(),
            pending.home(),
            FetchStage.TO_STICK
        );
        fetchByDog.put(dog.getUniqueId(), session);
        fetchDogByPlayer.put(player.getUniqueId(), dog.getUniqueId());
        player.sendActionBar(MM.deserialize("<gold>Biscuit runs after the stick!</gold>"));
        dog.getWorld().playSound(dog.getLocation(), Sound.ENTITY_WOLF_AMBIENT, SoundCategory.NEUTRAL, 0.85F, 1.25F);
        navigator.navigateTo(dog, item.getLocation());
    }

    private Entity activeFetchDog(PendingFetchDrop pending) {
        Entity dog = Bukkit.getEntity(pending.dogId());
        if (dog != null && dog.isValid() && dog.isInWorld()) {
            return dog;
        }
        GuideNpcManager guideNpcs = plugin.getGuideNpcManager();
        return guideNpcs == null ? null : guideNpcs.nearestLoadedNpc(
            GuideNpcType.FETCH_HOUND,
            pending.dropLocation(),
            FETCH_START_RANGE
        );
    }

    private Item findPendingFetchItem(PendingFetchDrop pending) {
        Entity direct = Bukkit.getEntity(pending.itemId());
        if (direct instanceof Item item && pendingFetchItemMatches(pending, item)) {
            return item;
        }
        World world = pending.dropLocation().getWorld();
        if (world == null) {
            return null;
        }
        return world.getNearbyEntities(pending.dropLocation(), 4.0D, 4.0D, 4.0D).stream()
            .filter(Item.class::isInstance)
            .map(Item.class::cast)
            .filter(item -> pendingFetchItemMatches(pending, item))
            .findFirst()
            .orElse(null);
    }

    private Item recreatePendingFetchItem(PendingFetchDrop pending) {
        World world = pending.dropLocation().getWorld();
        if (world == null) {
            return null;
        }
        ItemStack stack = pending.stack().clone();
        stack.setAmount(1);
        Item item = world.dropItem(pending.dropLocation(), stack);
        item.setVelocity(pending.velocity().clone());
        return pendingFetchItemMatches(pending, item) ? item : null;
    }

    private void removePendingFetchItems(PendingFetchDrop pending) {
        Entity direct = Bukkit.getEntity(pending.itemId());
        if (direct instanceof Item item && pendingFetchItemMatches(pending, item)) {
            item.remove();
        }
        World world = pending.dropLocation().getWorld();
        if (world == null) {
            return;
        }
        for (Entity nearby : world.getNearbyEntities(pending.dropLocation(), 6.0D, 6.0D, 6.0D)) {
            if (nearby instanceof Item item && pendingFetchItemMatches(pending, item)) {
                item.remove();
            }
        }
    }

    private boolean pendingFetchItemMatches(PendingFetchDrop pending, Item item) {
        if (item == null || item.isDead()) {
            return false;
        }
        ItemStack stack = item.getItemStack();
        return fetchIdentityMatches(
            pending.playerId().toString(),
            pending.token(),
            fetchOwner(stack),
            fetchToken(stack)
        );
    }

    private void protectFetchDrop(Item item, UUID owner) {
        item.setCanMobPickup(false);
        item.setCanPlayerPickup(false);
        item.setPickupDelay(Integer.MAX_VALUE);
        item.setUnlimitedLifetime(true);
        item.setWillAge(false);
        item.setInvulnerable(true);
        item.setOwner(owner);
        item.setThrower(owner);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        BukkitTask dialogue = illusionerDialogues.remove(event.getPlayer().getUniqueId());
        if (dialogue != null) {
            dialogue.cancel();
        }
        nextInteractionAt.keySet().removeIf(key -> key.playerId().equals(event.getPlayer().getUniqueId()));
        nextFeedAt.keySet().removeIf(key -> key.playerId().equals(event.getPlayer().getUniqueId()));
        UUID dogId = fetchDogByPlayer.get(event.getPlayer().getUniqueId());
        if (dogId != null) {
            FetchSession session = fetchByDog.get(dogId);
            if (session != null) {
                restoreAndClose(session);
            }
        }
        removeFetchSticks(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isFetchStick);
        removeFetchSticks(event.getPlayer());
        UUID dogId = fetchDogByPlayer.get(event.getPlayer().getUniqueId());
        if (dogId != null) {
            FetchSession session = fetchByDog.get(dogId);
            if (session != null) {
                restoreAndClose(session);
            }
        }
    }

    private void giveMenuFetchStick(Player player) {
        if (fetchDogByPlayer.containsKey(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("Biscuit is still fetching your current stick."));
            return;
        }
        if (hasFetchStick(player)) {
            player.sendMessage(MessageUtil.warn("You already have Biscuit's fetch stick."));
            return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            player.sendMessage(MessageUtil.warn("Make one empty inventory slot first."));
            return;
        }
        player.getInventory().addItem(createFetchStick(player.getUniqueId()));
        player.closeInventory();
        player.sendMessage(MM.deserialize("<gray><italic>Maybe I should find a clear spot near Biscuit and drop this.</italic></gray>"));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.7F, 1.3F);
    }

    private void tickFetchSessions() {
        carriedStickSweepTicks += 2;
        if (carriedStickSweepTicks >= 20) {
            carriedStickSweepTicks = 0;
            removeDistantCarriedFetchSticks();
        }
        if (fetchByDog.isEmpty()) {
            return;
        }
        for (FetchSession session : new ArrayList<>(fetchByDog.values())) {
            session.elapsedTicks += 2;
            Entity dog = Bukkit.getEntity(session.dogId);
            if (dog == null || !dog.isValid()) {
                closeMissingDog(session);
                continue;
            }
            switch (session.stage) {
                case TO_STICK -> tickTowardStick(session, dog);
                case TO_PLAYER -> tickTowardPlayer(session, dog);
                case RETURNING -> tickReturning(session, dog);
            }
        }
    }

    private void tickTowardStick(FetchSession session, Entity dog) {
        Item item = entityItem(session.itemId);
        Player player = Bukkit.getPlayer(session.playerId);
        if (item == null || player == null || !player.isOnline()) {
            failFetch(session, "Fetch ended early. Take another stick from Biscuit.");
            return;
        }
        if (!dog.getWorld().equals(item.getWorld()) || fetchPlayerOutOfRange(
            dog.getWorld().equals(player.getWorld()),
            dog.getWorld().equals(player.getWorld()) ? dog.getLocation().distanceSquared(player.getLocation()) : Double.POSITIVE_INFINITY
        )) {
            failFetch(session, "You went too far away, so the fetch stick faded.");
            return;
        }
        if (dog.getLocation().distanceSquared(item.getLocation()) <= FETCH_PICKUP_RANGE_SQUARED) {
            item.remove();
            session.itemId = null;
            session.stage = FetchStage.TO_PLAYER;
            session.elapsedTicks = 0;
            dog.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, dog.getLocation().add(0.0D, 0.7D, 0.0D), 8, 0.25D, 0.2D, 0.25D, 0.02D);
            dog.getWorld().playSound(dog.getLocation(), Sound.ENTITY_WOLF_PANT, SoundCategory.NEUTRAL, 0.9F, 1.2F);
            navigator.navigateTo(dog, player);
            return;
        }
        if (session.elapsedTicks >= FETCH_SESSION_TIMEOUT_TICKS) {
            failFetch(session, "Biscuit could not reach that throw. Take another stick to try again.");
        } else if (fetchPathRetryDue(
            session.elapsedTicks,
            item.isOnGround() || item.getVelocity().lengthSquared() <= 0.01D
        )) {
            navigator.navigateTo(dog, item.getLocation());
        }
    }

    private void tickTowardPlayer(FetchSession session, Entity dog) {
        Player player = Bukkit.getPlayer(session.playerId);
        if (player == null || !player.isOnline()) {
            restoreAndClose(session);
            return;
        }
        if (fetchPlayerOutOfRange(
            dog.getWorld().equals(player.getWorld()),
            dog.getWorld().equals(player.getWorld()) ? dog.getLocation().distanceSquared(player.getLocation()) : Double.POSITIVE_INFINITY
        )) {
            failFetch(session, "You went too far away, so the fetch stick faded.");
            return;
        }
        if (dog.getLocation().distanceSquared(player.getLocation()) <= FETCH_RETURN_RANGE_SQUARED) {
            fetchDogByPlayer.remove(player.getUniqueId(), dog.getUniqueId());
            player.sendActionBar(MM.deserialize("<gold>Biscuit made it back! <yellow>Woof woof!</yellow></gold>"));
            player.playSound(dog, Sound.ENTITY_WOLF_AMBIENT, SoundCategory.NEUTRAL, 1.0F, 1.25F);
            player.spawnParticle(Particle.HEART, dog.getLocation().add(0.0D, 0.8D, 0.0D), 5, 0.25D, 0.25D, 0.25D, 0.02D);
            beginReturnHome(session, dog);
            return;
        }
        if (session.elapsedTicks >= FETCH_SESSION_TIMEOUT_TICKS) {
            failFetch(session, "Biscuit got distracted. Take another stick to try again.");
        } else if (session.elapsedTicks % FETCH_PATH_RETRY_TICKS == 0) {
            navigator.navigateTo(dog, player);
        }
    }

    private void tickReturning(FetchSession session, Entity dog) {
        if (!dog.getWorld().equals(session.home.getWorld())) {
            finishReturnHome(session, dog);
            return;
        }
        if (dog.getLocation().distanceSquared(session.home) <= FETCH_RETURN_RANGE_SQUARED) {
            finishReturnHome(session, dog);
        } else if (session.elapsedTicks >= RETURN_HOME_TIMEOUT_TICKS) {
            finishReturnHome(session, dog);
        } else if (session.elapsedTicks % FETCH_PATH_RETRY_TICKS == 0) {
            if (!navigator.navigateTo(dog, session.home)) {
                finishReturnHome(session, dog);
            }
        }
    }

    private void beginReturnHome(FetchSession session, Entity dog) {
        session.stage = FetchStage.RETURNING;
        session.elapsedTicks = 0;
        navigator.cancel(dog);
        if (!dog.getWorld().equals(session.home.getWorld()) || !navigator.navigateTo(dog, session.home)) {
            finishReturnHome(session, dog);
        }
    }

    private void finishReturnHome(FetchSession session, Entity dog) {
        navigator.cancel(dog);
        // Always restore the exact spawn position and facing. Merely entering the
        // arrival radius allowed repeated games to slowly move Biscuit away.
        navigator.teleport(dog, session.home);
        closeSession(session);
    }

    private void failFetch(FetchSession session, String message) {
        Item item = entityItem(session.itemId);
        if (item != null) {
            item.remove();
        }
        session.itemId = null;
        Player player = Bukkit.getPlayer(session.playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(MessageUtil.warn(message));
        }
        fetchDogByPlayer.remove(session.playerId, session.dogId);
        Entity dog = Bukkit.getEntity(session.dogId);
        if (dog == null || !dog.isValid()) {
            closeSession(session);
            return;
        }
        session.stage = FetchStage.RETURNING;
        session.elapsedTicks = 0;
        beginReturnHome(session, dog);
    }

    private void restoreAndClose(FetchSession session) {
        Item item = entityItem(session.itemId);
        if (item != null) {
            item.remove();
        }
        Entity dog = Bukkit.getEntity(session.dogId);
        if (dog != null && dog.isValid() && navigator != null) {
            finishReturnHome(session, dog);
            return;
        }
        closeSession(session);
    }

    private void closeMissingDog(FetchSession session) {
        Item item = entityItem(session.itemId);
        if (item != null) {
            item.remove();
        }
        Player player = Bukkit.getPlayer(session.playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(MessageUtil.warn("Biscuit disappeared, so the fetch stick faded."));
        }
        closeSession(session);
    }

    private void closeSession(FetchSession session) {
        fetchByDog.remove(session.dogId, session);
        fetchDogByPlayer.remove(session.playerId, session.dogId);
    }

    private Item entityItem(UUID itemId) {
        if (itemId == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(itemId);
        return entity instanceof Item item && item.isValid() ? item : null;
    }

    private ItemStack createFetchStick(UUID owner) {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<gold><bold>Biscuit's Fetch Stick</bold></gold>").decoration(TextDecoration.ITALIC, false));
        meta.lore(CustomLoreUtil.wrapLoreLines(List.of(
            MM.deserialize("<gray>Drop near Biscuit to play fetch.</gray>").decoration(TextDecoration.ITALIC, false),
            MM.deserialize("<dark_gray>One use. Fades away from Biscuit.</dark_gray>").decoration(TextDecoration.ITALIC, false)
        )));
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(keyFetchStick, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(keyFetchOwner, PersistentDataType.STRING, owner.toString());
        meta.getPersistentDataContainer().set(keyFetchToken, PersistentDataType.STRING, UUID.randomUUID().toString());
        item.setItemMeta(meta);
        return item;
    }

    public boolean isFetchStick(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(keyFetchStick, PersistentDataType.BYTE);
    }

    private String fetchOwner(ItemStack item) {
        if (!isFetchStick(item)) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(keyFetchOwner, PersistentDataType.STRING);
    }

    private String fetchToken(ItemStack item) {
        if (!isFetchStick(item)) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(keyFetchToken, PersistentDataType.STRING);
    }

    private boolean hasFetchStick(Player player) {
        if (isFetchStick(player.getItemOnCursor())) {
            return true;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (isFetchStick(item)) {
                return true;
            }
        }
        return false;
    }

    private void removeDistantCarriedFetchSticks() {
        GuideNpcManager guideNpcs = plugin.getGuideNpcManager();
        if (guideNpcs == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!hasFetchStick(player)) {
                continue;
            }
            Entity dog = guideNpcs.nearestLoadedNpc(GuideNpcType.FETCH_HOUND, player.getLocation(), FETCH_START_RANGE);
            if (dog == null && removeFetchSticks(player) > 0) {
                player.sendActionBar(MM.deserialize("<gray>Biscuit is too far away. The fetch stick fades.</gray>"));
            }
        }
    }

    private int removeFetchSticks(Player player) {
        if (player == null) {
            return 0;
        }
        int removed = 0;
        if (isFetchStick(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            removed++;
        }
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isFetchStick(contents[slot])) {
                player.getInventory().setItem(slot, null);
                removed++;
            }
        }
        return removed;
    }

    private void blockFetchStickStorage(InventoryClickEvent event) {
        if (isFetchStick(event.getCursor()) && isBundle(event.getCurrentItem())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(MessageUtil.warn("Biscuit's fetch stick cannot be stored."));
            }
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean externalTop = event.getView().getTopInventory().getType() != InventoryType.CRAFTING
            && event.getView().getTopInventory().getType() != InventoryType.PLAYER;
        if (!externalTop) {
            return;
        }
        boolean blocked = false;
        if (event.getRawSlot() < topSize && isFetchStick(event.getCursor())) {
            event.setCancelled(true);
            blocked = true;
        } else if (event.isShiftClick() && event.getRawSlot() >= topSize && isFetchStick(event.getCurrentItem())) {
            event.setCancelled(true);
            blocked = true;
        } else if (event.getClick() == ClickType.NUMBER_KEY && event.getRawSlot() < topSize) {
            int hotbarButton = event.getHotbarButton();
            if (hotbarButton >= 0 && event.getWhoClicked() instanceof Player player
                && isFetchStick(player.getInventory().getItem(hotbarButton))) {
                event.setCancelled(true);
                blocked = true;
            }
        } else if (event.getClick() == ClickType.SWAP_OFFHAND && event.getRawSlot() < topSize
            && event.getWhoClicked() instanceof Player player && isFetchStick(player.getInventory().getItemInOffHand())) {
            event.setCancelled(true);
            blocked = true;
        }
        if (blocked && event.getWhoClicked() instanceof Player player) {
            player.sendMessage(MessageUtil.warn("Biscuit's fetch stick cannot be stored."));
        }
    }

    private boolean isBundle(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        String materialName = item.getType().name();
        return materialName.equals("BUNDLE") || materialName.endsWith("_BUNDLE");
    }

    private void beginIllusionerEncounter(Player player) {
        if (!markInteraction(player, GuideNpcType.HIDDEN_ILLUSIONER)) {
            return;
        }
        if (player.getPersistentDataContainer().has(keyIllusionerFound, PersistentDataType.BYTE)) {
            speakLine(player, "The Crooked One", "Still feeling steady? Shame.");
            player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_AMBIENT, SoundCategory.NEUTRAL, 0.65F, 0.72F);
            return;
        }
        if (illusionerDialogues.containsKey(player.getUniqueId())) {
            return;
        }

        BukkitTask task = new BukkitRunnable() {
            private int step;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    illusionerDialogues.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                switch (step++) {
                    case 0 -> speakLine(player, "The Crooked One", "Oh. Someone actually found me.");
                    case 1 -> speakLine(player, "The Crooked One", "Everyone watches the road. Nobody checks the dark corners.");
                    case 2 -> speakLine(player, "The Crooked One", "A reward? Certainly. Take this curse.");
                    default -> {
                        applyIllusionerCurse(player);
                        illusionerDialogues.remove(player.getUniqueId());
                        cancel();
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 35L);
        illusionerDialogues.put(player.getUniqueId(), task);
    }

    private void applyIllusionerCurse(Player player) {
        player.getPersistentDataContainer().set(keyIllusionerFound, PersistentDataType.BYTE, (byte) 1);
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.NAUSEA,
            ILLUSIONER_CURSE_DURATION_TICKS,
            4,
            false,
            true,
            true
        ));
        player.showTitle(Title.title(
            MM.deserialize("<dark_purple><bold>CURSED</bold></dark_purple>"),
            MM.deserialize("<light_purple>The room will stop moving in two minutes.</light_purple>"),
            Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(500))
        ));
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.HOSTILE, 1.0F, 0.58F);
        player.spawnParticle(Particle.WITCH, player.getLocation().add(0.0D, 1.0D, 0.0D), 55, 0.55D, 0.8D, 0.55D, 0.05D);
    }

    private void speak(Player player, GuideNpcType type) {
        if (!markInteraction(player, type)) {
            return;
        }
        List<String> lines = DIALOGUE.get(type);
        if (lines == null || lines.isEmpty()) {
            return;
        }
        String line = lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
        speakLine(player, type.displayName(), line);
        Sound sound = ambientSound(type);
        player.playSound(player.getLocation(), sound, SoundCategory.NEUTRAL, type == GuideNpcType.TOWN_PARROT ? 0.85F : 0.6F, 1.05F);
    }

    private void speakLine(Player player, String speaker, String line) {
        player.sendMessage(MM.deserialize(
            "<dark_gray>[<gold>" + MM.escapeTags(speaker) + "</gold>]</dark_gray> <white>" + MM.escapeTags(line) + "</white>"
        ));
    }

    private boolean tryFeed(Player player, GuideNpcType type, Entity interactionTarget) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!acceptsFeed(type, held.getType())) {
            return false;
        }
        if (held.hasItemMeta()) {
            player.sendMessage(MessageUtil.warn(type == GuideNpcType.FETCH_HOUND
                ? "Biscuit only eats ordinary bones."
                : "Miso only eats ordinary raw cod or salmon."));
            return true;
        }

        long now = System.currentTimeMillis();
        InteractionKey key = new InteractionKey(player.getUniqueId(), type);
        long readyAt = nextFeedAt.getOrDefault(key, 0L);
        long remainingSeconds = feedCooldownSeconds(readyAt, now);
        if (remainingSeconds > 0L) {
            String name = type == GuideNpcType.FETCH_HOUND ? "Biscuit" : "Miso";
            player.sendMessage(MessageUtil.info(name + " is still full. Try again in <white>" + remainingSeconds + "s</white>."));
            return true;
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            if (held.getAmount() <= 1) {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            } else {
                held.setAmount(held.getAmount() - 1);
            }
        }
        nextFeedAt.put(key, now + FEED_COOLDOWN_MS);
        playFeedReaction(player, type, interactionTarget);
        return true;
    }

    private void playFeedReaction(Player player, GuideNpcType type, Entity interactionTarget) {
        Entity animal = interactionTarget;
        if (animal == null || !animal.isValid() || animal.getWorld() != player.getWorld()) {
            GuideNpcManager guideNpcs = plugin.getGuideNpcManager();
            animal = guideNpcs == null ? null : guideNpcs.nearestLoadedNpc(type, player.getLocation(), 10.0D);
        }
        Location reactionAt = animal == null ? player.getLocation() : animal.getLocation();
        World world = reactionAt.getWorld();
        if (world == null) {
            return;
        }

        world.spawnParticle(Particle.HEART, reactionAt.clone().add(0.0D, 0.9D, 0.0D), 5, 0.28D, 0.22D, 0.28D, 0.02D);
        world.spawnParticle(Particle.HAPPY_VILLAGER, reactionAt.clone().add(0.0D, 0.55D, 0.0D), 8, 0.32D, 0.25D, 0.32D, 0.02D);
        world.playSound(reactionAt, Sound.ENTITY_GENERIC_EAT, SoundCategory.NEUTRAL, 0.75F, 1.08F);
        if (type == GuideNpcType.FETCH_HOUND) {
            world.playSound(reactionAt, Sound.ENTITY_WOLF_AMBIENT, SoundCategory.NEUTRAL, 0.85F, 1.24F);
            speakLine(player, "Biscuit", "Biscuit crunches the bone and thumps his tail against the floor.");
        } else {
            world.playSound(reactionAt, Sound.ENTITY_CAT_PURR, SoundCategory.NEUTRAL, 0.9F, 1.08F);
            speakLine(player, "Miso", "Miso eats the fish, purrs, and gives you a slow blink.");
        }
    }

    static boolean acceptsFeed(GuideNpcType type, Material material) {
        if (type == GuideNpcType.FETCH_HOUND) {
            return material == Material.BONE;
        }
        return type == GuideNpcType.TOWN_CAT && (material == Material.COD || material == Material.SALMON);
    }

    static long feedCooldownSeconds(long readyAt, long now) {
        if (readyAt <= now) {
            return 0L;
        }
        return Math.max(1L, (readyAt - now + 999L) / 1_000L);
    }

    private boolean markInteraction(Player player, GuideNpcType type) {
        long now = System.currentTimeMillis();
        InteractionKey key = new InteractionKey(player.getUniqueId(), type);
        if (nextInteractionAt.getOrDefault(key, 0L) > now) {
            return false;
        }
        nextInteractionAt.put(key, now + INTERACTION_COOLDOWN_MS);
        return true;
    }

    private void tickAmbientNpcs() {
        GuideNpcManager guideNpcs = plugin.getGuideNpcManager();
        if (guideNpcs == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Set<AmbientKey> seen = new HashSet<>();
        for (GuideNpcType type : AMBIENT_TYPES) {
            for (Location location : guideNpcs.locations(type)) {
                World world = location.getWorld();
                if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                    continue;
                }
                AmbientKey key = AmbientKey.of(type, location);
                seen.add(key);
                if (nextAmbientAt.getOrDefault(key, 0L) > now || !hasNearbyPlayer(location)) {
                    continue;
                }
                Sound sound = ambientSound(type);
                float volume = switch (type) {
                    case FETCH_HOUND, TOWN_CAT, TOWN_FOX, TOWN_PARROT -> 0.55F;
                    default -> 0.22F;
                };
                float pitch = 0.92F + ThreadLocalRandom.current().nextFloat() * 0.22F;
                world.playSound(location, sound, SoundCategory.AMBIENT, volume, pitch);
                nextAmbientAt.put(key, now + nextAmbientDelayMillis(type));
            }
        }
        nextAmbientAt.keySet().removeIf(key -> !seen.contains(key));
    }

    private boolean hasNearbyPlayer(Location location) {
        for (Player player : location.getWorld().getPlayers()) {
            if (player.isOnline() && player.getLocation().distanceSquared(location) <= AMBIENT_HEARING_RANGE_SQUARED) {
                return true;
            }
        }
        return false;
    }

    private Sound ambientSound(GuideNpcType type) {
        return switch (type) {
            case FETCH_HOUND -> Sound.ENTITY_WOLF_AMBIENT;
            case TOWN_CAT -> Sound.ENTITY_CAT_AMBIENT;
            case TOWN_FOX -> Sound.ENTITY_FOX_AMBIENT;
            case TOWN_PARROT -> Sound.ENTITY_PARROT_AMBIENT;
            case TOWN_BAKER -> Sound.ENTITY_VILLAGER_WORK_BUTCHER;
            case TOWN_MASON -> Sound.ENTITY_VILLAGER_WORK_MASON;
            case TOWN_COURIER -> Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER;
            case TOWN_DOCKHAND -> Sound.ENTITY_VILLAGER_WORK_FISHERMAN;
            case TOWN_SEAMSTRESS -> Sound.ENTITY_VILLAGER_WORK_SHEPHERD;
            case TAVERN_HOST -> Sound.ENTITY_VILLAGER_TRADE;
            case TAVERN_REGULAR -> Sound.BLOCK_NOTE_BLOCK_BELL;
            case TAVERN_TIPSY -> Sound.ENTITY_VILLAGER_AMBIENT;
            default -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
        };
    }

    static long nextAmbientDelayMillis(GuideNpcType type, long randomOffsetMillis) {
        long minimum = switch (type) {
            case FETCH_HOUND, TOWN_CAT, TOWN_FOX, TOWN_PARROT -> 18_000L;
            default -> 28_000L;
        };
        long spread = switch (type) {
            case FETCH_HOUND, TOWN_CAT, TOWN_FOX, TOWN_PARROT -> 20_000L;
            default -> 24_000L;
        };
        return minimum + Math.floorMod(randomOffsetMillis, spread + 1L);
    }

    private long nextAmbientDelayMillis(GuideNpcType type) {
        return nextAmbientDelayMillis(type, ThreadLocalRandom.current().nextLong());
    }

    static boolean fetchTimedOut(int elapsedTicks) {
        return elapsedTicks >= FETCH_SESSION_TIMEOUT_TICKS;
    }

    static boolean fetchPlayerOutOfRange(boolean sameWorld, double distanceSquared) {
        return !sameWorld || !Double.isFinite(distanceSquared) || distanceSquared > FETCH_SESSION_RANGE * FETCH_SESSION_RANGE;
    }

    static boolean fetchIdentityMatches(String expectedOwner, String expectedToken, String actualOwner, String actualToken) {
        return expectedOwner != null
            && expectedToken != null
            && expectedOwner.equals(actualOwner)
            && expectedToken.equals(actualToken);
    }

    static boolean fetchPathRetryDue(int elapsedTicks, boolean itemOnGround) {
        return itemOnGround && elapsedTicks > 0 && elapsedTicks % FETCH_PATH_RETRY_TICKS == 0;
    }

    private void tryEnableCitizensNavigator() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            plugin.getLogger().warning("Spawn-life NPC dialogue is available, but fetch navigation needs Citizens.");
            return;
        }
        try {
            Class<?> bridgeClass = Class.forName("me.rique.smpcore.spawn.CitizensSpawnLifeNavigator");
            navigator = (SpawnLifeNavigator) bridgeClass.getConstructor().newInstance();
            plugin.getLogger().info("Citizens-backed spawn-life navigation enabled.");
        } catch (ReflectiveOperationException | LinkageError ex) {
            navigator = null;
            plugin.getLogger().warning("Citizens fetch navigation could not start: " + ex.getMessage());
        }
    }

    private ItemStack menuItem(Material material, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        Component displayName = MM.deserialize(MenuItemUtil.visibleMiniName(name)).decoration(TextDecoration.ITALIC, false);
        meta.displayName(displayName);
        meta.lore(MenuItemUtil.visibleMiniLore(name, lore).stream()
            .map(line -> MM.deserialize(line).decoration(TextDecoration.ITALIC, false))
            .toList());
        if (action != null) {
            meta.getPersistentDataContainer().set(keyMenuAction, PersistentDataType.STRING, action);
        }
        item.setItemMeta(meta);
        return item;
    }

    private String actionOf(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(keyMenuAction, PersistentDataType.STRING);
    }

    private static Map<GuideNpcType, List<String>> dialogue() {
        Map<GuideNpcType, List<String>> lines = new EnumMap<>(GuideNpcType.class);
        lines.put(GuideNpcType.FETCH_HOUND, List.of(
            "Woof! Biscuit noses the stick basket toward you.",
            "Biscuit waits by your feet, tail already wagging.",
            "Woof woof! A clear patch nearby would be perfect.",
            "Biscuit looks at you, then at the stick, then back at you.",
            "One throw at a time. Biscuit takes this very seriously."
        ));
        lines.put(GuideNpcType.TOWN_CAT, List.of(
            "Mrrp.",
            "Miso stares at an empty corner, then at you.",
            "The fish stall is under strict supervision.",
            "Miso has inspected your boots and found them acceptable.",
            "A slow blink. Apparently that means you may stay.",
            "Miso is off duty until someone opens a tin of fish."
        ));
        lines.put(GuideNpcType.TOWN_FOX, List.of(
            "Yip! I wasn't in the bakery. Prove it.",
            "Pip has acquired one sock and no regrets.",
            "The berries were already missing.",
            "Pip heard something under the bridge. Pip is not checking.",
            "A quick yip, followed by an even quicker retreat.",
            "Pip has buried a treasure and forgotten where."
        ));
        lines.put(GuideNpcType.TOWN_PARROT, List.of(
            "Pretty bell! Bad bell! Snacks?",
            "Buttons says you're late! Buttons doesn't know for what!",
            "Squawk! Bram owes me a cracker!",
            "Buttons repeats your name badly, then looks pleased.",
            "Boss fight! Boss fight! ...No? Snacks, then.",
            "Squawk! The courier snores on the job!"
        ));
        lines.put(GuideNpcType.TOWN_BAKER, List.of(
            "Fresh rolls are ready. Ignore the slightly burned batch.",
            "Business is better when the rain lets up.",
            "If you see Bram, tell him I still want my mugs back.",
            "The honey loaves sell out first. You have been warned.",
            "I trade bread for local gossip, but the gossip has been stale lately.",
            "Mind the cooling rack. I only have two good oven mitts."
        ));
        lines.put(GuideNpcType.TOWN_MASON, List.of(
            "The west wall needs another day. Maybe two if it rains.",
            "That crack was already there.",
            "The bell keeps shaking dust out of my walls.",
            "Good stone is easy. Getting it up the hill is the hard part.",
            "If the gate leans any farther, I am billing the mayor.",
            "No, I cannot make your house lava-proof. I can make it less flammable."
        ));
        lines.put(GuideNpcType.TOWN_COURIER, List.of(
            "Road past the east gate is muddy again.",
            "I've got letters for people who never stay put.",
            "Mira calls it the Season of the Veil. I call it extra deliveries.",
            "Package for Malakar again. Heavy, humming, and definitely not suspicious.",
            "If you move houses, tell me before I carry the crate across town.",
            "The beach route is faster unless Corin has left boats everywhere."
        ));
        lines.put(GuideNpcType.TOWN_DOCKHAND, List.of(
            "Fog's thick by the water today.",
            "Harbor is quiet. Too quiet for my liking.",
            "No, I don't know what was in that crate.",
            "Corin says the tide is safe. Corin also fishes during thunderstorms.",
            "Tie your boat properly or the fox will claim it by morning.",
            "Something rang under the water last night. I stayed on the dock."
        ));
        lines.put(GuideNpcType.TOWN_SEAMSTRESS, List.of(
            "Everyone wants cloaks this season.",
            "Hold still. Your sleeve is twisted.",
            "Purple thread costs extra. Don't ask why.",
            "Boss armor is impressive until someone forgets to mend the straps.",
            "I can fix a cloak. I cannot fix your taste in capes.",
            "Bring dry fabric next time. The dockhand keeps doing this to me."
        ));
        lines.put(GuideNpcType.TAVERN_HOST, List.of(
            "Welcome in. Tables are up front; games and drinks are down the hall.",
            "Looking for Bram? Keep walking until you hear bottles clinking.",
            "The games are in the back. Try not to wager your boots.",
            "Grab any open chair. Yes, the hallway really is that long.",
            "Silas runs blackjack in the back room. Rook handles the other games.",
            "Empty hand for the chairs. We learned that after the third spilled stew.",
            "If the tables are full, watch a round before you join."
        ));
        lines.put(GuideNpcType.TAVERN_REGULAR, List.of(
            "I came in for one hand of cards. That was three hours ago.",
            "The dartboard is safe. The wall around it has seen better days.",
            "Bram says I have a tab. I say he has poor handwriting.",
            "The slots are honest. That's what the slots told me.",
            "Crown & Casks is easy until somebody remembers how to count.",
            "A tavern luck meal helps. It does not help enough to trust the slots.",
            "Silas never smiles when he has twenty. That is how you know."
        ));
        lines.put(GuideNpcType.TAVERN_TIPSY, List.of(
            "I am not drunk. The floor is simply unreliable.",
            "I won twice tonight. The other eleven rounds do not count.",
            "Tell Bram this mug followed me home.",
            "You have two faces. Both seem trustworthy.",
            "The hallway moved again. Keep an eye on it.",
            "I challenged the dartboard. It won on points.",
            "A full night's sleep fixes everything except my tab.",
            "I can stop whenever Bram runs out. He never runs out."
        ));
        return Map.copyOf(lines);
    }

    private enum FetchStage {
        TO_STICK,
        TO_PLAYER,
        RETURNING
    }

    private static final class FetchSession {
        private final UUID playerId;
        private final UUID dogId;
        private UUID itemId;
        private final Location home;
        private FetchStage stage;
        private int elapsedTicks;

        private FetchSession(UUID playerId, UUID dogId, UUID itemId, Location home, FetchStage stage) {
            this.playerId = playerId;
            this.dogId = dogId;
            this.itemId = itemId;
            this.home = home;
            this.stage = stage;
        }
    }

    private record PendingFetchDrop(
        UUID playerId,
        UUID dogId,
        UUID itemId,
        Location home,
        Location dropLocation,
        Vector velocity,
        ItemStack stack,
        String token
    ) {
        private PendingFetchDrop {
            home = home.clone();
            dropLocation = dropLocation.clone();
            velocity = velocity.clone();
            stack = stack.clone();
        }
    }

    private record InteractionKey(UUID playerId, GuideNpcType type) {
    }

    private record AmbientKey(GuideNpcType type, UUID worldId, int x, int y, int z) {
        private static AmbientKey of(GuideNpcType type, Location location) {
            return new AmbientKey(type, location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }

    private record DogMenuHolder(UUID playerId) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
