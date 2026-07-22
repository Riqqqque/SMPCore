package me.rique.smpcore.wild;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.config.ConfigManager;
import me.rique.smpcore.util.LocationUtil;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public final class WildTeleportManager implements Listener {

    private static final int WORLD_COORDINATE_LIMIT = 29_999_984;
    private static final int CANDIDATE_SAMPLES = 128;

    private final SMPCore plugin;
    private final NamespacedKey cooldownUntilKey;
    private final Map<UUID, Request> requests = new HashMap<>();
    private final ArrayDeque<Request> queue = new ArrayDeque<>();
    private int activeSearches;
    private boolean pumping;
    private boolean shuttingDown;

    public WildTeleportManager(SMPCore plugin) {
        this.plugin = plugin;
        this.cooldownUntilKey = new NamespacedKey(plugin, "wild_cooldown_until");
    }

    public boolean requestTeleport(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        ConfigManager config = plugin.getConfigManager();
        if (!config.wildEnabled) {
            player.sendMessage(MessageUtil.warn("Wild travel is disabled right now."));
            return false;
        }

        String restriction = activeRestriction(player);
        if (restriction != null) {
            player.sendMessage(MessageUtil.warn(restriction));
            return false;
        }

        if (requests.containsKey(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("Your wild destination is already being found."));
            return false;
        }

        long cooldownSeconds = cooldownSecondsRemaining(player, System.currentTimeMillis());
        if (cooldownSeconds > 0L) {
            player.sendMessage(MessageUtil.warn("You can use <white>/wild</white> again in <white>"
                + formatDuration(cooldownSeconds) + "</white>."));
            return false;
        }

        Request request = new Request(player.getUniqueId());
        requests.put(request.playerId, request);
        queue.addLast(request);
        boolean queued = activeSearches >= config.wildMaxConcurrentSearches;
        int queuePosition = queue.size();
        pumpQueue();

        if (queued && requests.get(request.playerId) == request && !request.active) {
            player.sendMessage(MessageUtil.info("Wild travel queued <white>#" + queuePosition
                + "</white>. Your destination will load shortly."));
        } else if (requests.get(request.playerId) == request) {
            player.sendMessage(MessageUtil.info("Searching for safe ground inside the world border..."));
        }
        return true;
    }

    public void shutdown() {
        shuttingDown = true;
        for (Request request : requests.values()) {
            request.cancelled = true;
            request.finished = true;
        }
        requests.clear();
        queue.clear();
        activeSearches = 0;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        cancel(event.getEntity());
    }

    private void cancel(Player player) {
        Request request = requests.get(player.getUniqueId());
        if (request == null || request.finished) {
            return;
        }
        request.cancelled = true;
        if (!request.active) {
            queue.remove(request);
            complete(request);
        }
    }

    private void pumpQueue() {
        if (pumping || shuttingDown) {
            return;
        }
        pumping = true;
        try {
            int maxConcurrent = plugin.getConfigManager().wildMaxConcurrentSearches;
            while (activeSearches < maxConcurrent && !queue.isEmpty()) {
                Request request = queue.removeFirst();
                if (request.cancelled || request.finished || requests.get(request.playerId) != request) {
                    continue;
                }
                Player player = Bukkit.getPlayer(request.playerId);
                if (player == null || !player.isOnline()) {
                    requests.remove(request.playerId, request);
                    continue;
                }
                request.active = true;
                activeSearches++;
                startSearch(request, player);
                maxConcurrent = plugin.getConfigManager().wildMaxConcurrentSearches;
            }
        } finally {
            pumping = false;
        }
    }

    private void startSearch(Request request, Player player) {
        String restriction = activeRestriction(player);
        if (restriction != null) {
            fail(request, player, restriction);
            return;
        }

        ConfigManager config = plugin.getConfigManager();
        World world = Bukkit.getWorld(config.wildWorld);
        if (world == null) {
            fail(request, player, "The wild world is not loaded. Tell an admin.");
            return;
        }
        if (world.getEnvironment() != World.Environment.NORMAL) {
            fail(request, player, "Wild travel must target an Overworld.");
            plugin.getLogger().warning("Wild travel rejected non-Overworld target '" + world.getName() + "'.");
            return;
        }

        request.world = world;
        request.borderPadding = config.wildBorderPadding;
        request.minimumSpawnDistance = config.wildMinimumSpawnDistance;
        request.maxAttempts = config.wildSearchAttempts;
        Location spawn = config.exactSpawnLocation();
        request.spawn = spawn != null && spawn.getWorld() != null && spawn.getWorld().getUID().equals(world.getUID())
            ? spawn
            : world.getSpawnLocation();
        tryNextCandidate(request, player);
    }

    private void tryNextCandidate(Request request, Player player) {
        if (!isCurrent(request)) {
            complete(request);
            return;
        }
        if (!player.isOnline()) {
            request.cancelled = true;
            complete(request);
            return;
        }

        String restriction = activeRestriction(player);
        if (restriction != null) {
            fail(request, player, restriction);
            return;
        }
        if (request.attempts >= request.maxAttempts) {
            if (request.lastLoadError != null) {
                plugin.getLogger().warning("Wild search exhausted for " + player.getName() + " after chunk load errors: "
                    + request.lastLoadError.getClass().getSimpleName() + ": " + request.lastLoadError.getMessage());
            }
            fail(request, player, "No safe wild location was found. Try again in a moment.");
            return;
        }

        WorldBorder border = request.world.getWorldBorder();
        SearchBounds bounds = searchBounds(
            border.getCenter().getX(),
            border.getCenter().getZ(),
            border.getSize(),
            request.borderPadding
        );
        if (bounds == null) {
            fail(request, player, "The current world border is too small for safe wild travel.");
            return;
        }

        double minimumDistanceSquared = (double) request.minimumSpawnDistance * request.minimumSpawnDistance;
        if (bounds.farthestDistanceSquared(request.spawn.getX(), request.spawn.getZ()) < minimumDistanceSquared) {
            fail(request, player, "The current world border is too small for the configured wild distance.");
            return;
        }

        Candidate candidate = chooseCandidate(bounds, request.spawn, request.minimumSpawnDistance, request.triedChunks);
        if (candidate == null) {
            fail(request, player, "No unused wild area is available inside the current border. Try again later.");
            return;
        }

        request.attempts++;
        request.triedChunks.add(chunkKey(candidate.x >> 4, candidate.z >> 4));
        CompletableFuture<?> chunkLoad;
        try {
            chunkLoad = request.world.getChunkAtAsync(candidate.x >> 4, candidate.z >> 4, true);
        } catch (RuntimeException ex) {
            request.lastLoadError = ex;
            tryNextCandidate(request, player);
            return;
        }

        chunkLoad.whenComplete((ignored, error) -> runSync(() -> handleLoadedCandidate(request, candidate, error)));
    }

    private void handleLoadedCandidate(Request request, Candidate candidate, Throwable error) {
        if (!isCurrent(request)) {
            complete(request);
            return;
        }
        Player player = Bukkit.getPlayer(request.playerId);
        if (player == null || !player.isOnline()) {
            request.cancelled = true;
            complete(request);
            return;
        }
        if (error != null) {
            request.lastLoadError = error;
            tryNextCandidate(request, player);
            return;
        }

        String restriction = activeRestriction(player);
        if (restriction != null) {
            fail(request, player, restriction);
            return;
        }

        WorldBorder border = request.world.getWorldBorder();
        SearchBounds currentBounds = searchBounds(
            border.getCenter().getX(),
            border.getCenter().getZ(),
            border.getSize(),
            request.borderPadding
        );
        if (currentBounds == null || !currentBounds.contains(candidate.x, candidate.z)) {
            tryNextCandidate(request, player);
            return;
        }

        Block floor = request.world.getHighestBlockAt(candidate.x, candidate.z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Location destination = new Location(
            request.world,
            candidate.x + 0.5D,
            floor.getY() + 1.0D,
            candidate.z + 0.5D,
            player.getYaw(),
            0.0F
        );
        if (!isSafeDestination(destination)
            || !border.isInside(destination)
            || (plugin.getSpawnProtectionListener() != null && plugin.getSpawnProtectionListener().isProtected(destination))) {
            tryNextCandidate(request, player);
            return;
        }

        beginTeleport(request, player, destination, player.getLocation().clone(), true);
    }

    private void beginTeleport(
        Request request,
        Player player,
        Location destination,
        Location returnLocation,
        boolean retryAvailable
    ) {
        if (!isCurrent(request) || !player.isOnline()) {
            complete(request);
            return;
        }
        String restriction = activeRestriction(player);
        if (restriction != null) {
            fail(request, player, restriction);
            return;
        }

        WorldBorder border = destination.getWorld().getWorldBorder();
        SearchBounds bounds = searchBounds(
            border.getCenter().getX(),
            border.getCenter().getZ(),
            border.getSize(),
            request.borderPadding
        );
        if (bounds == null || !bounds.contains(destination.getBlockX(), destination.getBlockZ())
            || !border.isInside(destination) || !isSafeDestination(destination)) {
            tryNextCandidate(request, player);
            return;
        }
        if (plugin.getSpawnProtectionListener() != null && plugin.getSpawnProtectionListener().isProtected(destination)) {
            tryNextCandidate(request, player);
            return;
        }
        if (!prepareTeleport(player)) {
            fail(request, player, "Leave your seat or mount, then try <white>/wild</white> again.");
            return;
        }

        try {
            player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)
                .whenComplete((success, teleportError) -> runSync(() -> {
                    if (!isCurrent(request)) {
                        complete(request);
                        return;
                    }
                    Player current = Bukkit.getPlayer(request.playerId);
                    if (current == null || !current.isOnline()) {
                        request.cancelled = true;
                        complete(request);
                        return;
                    }
                    if (teleportError == null && Boolean.TRUE.equals(success)) {
                        plugin.getPlayerManager().saveBackLocation(request.playerId, returnLocation);
                        setCooldown(current);
                        current.setFallDistance(0.0F);
                        current.playSound(current.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7F, 1.2F);
                        current.sendMessage(MessageUtil.success("Welcome to the wilds. <gray>(</gray><white>"
                            + destination.getBlockX() + ", " + destination.getBlockZ() + "</white><gray>)</gray>"));
                        complete(request);
                        return;
                    }

                    String currentRestriction = activeRestriction(current);
                    boolean crossWorld = !current.getWorld().getUID().equals(destination.getWorld().getUID());
                    if (retryAvailable && crossWorld && currentRestriction == null) {
                        Bukkit.getScheduler().runTaskLater(
                            plugin,
                            () -> beginTeleport(request, current, destination, returnLocation, false),
                            1L
                        );
                        return;
                    }

                    if (teleportError != null) {
                        plugin.getLogger().warning("Wild teleport rejected for " + current.getName() + ": "
                            + teleportError.getClass().getSimpleName() + ": " + teleportError.getMessage());
                    }
                    fail(request, current, currentRestriction == null
                        ? "Wild travel failed. Dismount and try again."
                        : currentRestriction);
                }));
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Could not start wild teleport for " + player.getName() + ": " + ex.getMessage());
            fail(request, player, "Wild travel failed. Try again in a moment.");
        }
    }

    private String activeRestriction(Player player) {
        if (player.isDead()) {
            return "You cannot use <white>/wild</white> while dead.";
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return "Leave spectator mode before using <white>/wild</white>.";
        }
        if (!player.isOp() && plugin.getSmpStartManager() != null && plugin.getSmpStartManager().isPreStartLocked()) {
            return "Wild travel unlocks when the SMP starts.";
        }
        if (plugin.getCombatLogListener() != null && plugin.getCombatLogListener().isInPlayerCombat(player)) {
            return "You cannot use <white>/wild</white> while in combat.";
        }
        if (plugin.getDuelManager() != null && plugin.getDuelManager().blocksExternalTeleport(player)) {
            return "You cannot use <white>/wild</white> during a duel.";
        }
        if ((plugin.getBossManager() != null && plugin.getBossManager().isActiveBossFight(player))
            || (plugin.getBossDungeonManager() != null && plugin.getBossDungeonManager().blocksExternalTeleport(player))) {
            return "You cannot leave an active boss fight.";
        }
        return null;
    }

    private long cooldownSecondsRemaining(Player player, long now) {
        if (player.hasPermission("smpcore.wild.cooldown.bypass")) {
            return 0L;
        }
        long until = player.getPersistentDataContainer().getOrDefault(cooldownUntilKey, PersistentDataType.LONG, 0L);
        if (until <= now || plugin.getConfigManager().wildCooldownSeconds <= 0) {
            if (until != 0L) {
                player.getPersistentDataContainer().remove(cooldownUntilKey);
            }
            return 0L;
        }
        long maximumUntil = now + (plugin.getConfigManager().wildCooldownSeconds * 1000L);
        if (until > maximumUntil) {
            until = maximumUntil;
            player.getPersistentDataContainer().set(cooldownUntilKey, PersistentDataType.LONG, until);
        }
        return Math.max(1L, (until - now + 999L) / 1000L);
    }

    private void setCooldown(Player player) {
        if (player.hasPermission("smpcore.wild.cooldown.bypass")) {
            player.getPersistentDataContainer().remove(cooldownUntilKey);
            return;
        }
        int seconds = plugin.getConfigManager().wildCooldownSeconds;
        if (seconds <= 0) {
            player.getPersistentDataContainer().remove(cooldownUntilKey);
            return;
        }
        player.getPersistentDataContainer().set(
            cooldownUntilKey,
            PersistentDataType.LONG,
            System.currentTimeMillis() + (seconds * 1000L)
        );
    }

    private void fail(Request request, Player player, String message) {
        if (player != null && player.isOnline() && message != null && !message.isBlank()) {
            player.sendMessage(MessageUtil.warn(message));
        }
        complete(request);
    }

    private void complete(Request request) {
        if (request == null || request.finished) {
            return;
        }
        request.finished = true;
        queue.remove(request);
        requests.remove(request.playerId, request);
        if (request.active) {
            request.active = false;
            activeSearches = Math.max(0, activeSearches - 1);
        }
        pumpQueue();
    }

    private boolean isCurrent(Request request) {
        return request != null
            && !shuttingDown
            && !request.cancelled
            && !request.finished
            && requests.get(request.playerId) == request;
    }

    private void runSync(Runnable action) {
        if (!plugin.isEnabled() || shuttingDown) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, action);
        }
    }

    static SearchBounds searchBounds(double centerX, double centerZ, double size, int padding) {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ) || !Double.isFinite(size) || size <= 0.0D) {
            return null;
        }
        double halfUsableSize = (size / 2.0D) - Math.max(0, padding);
        if (halfUsableSize < 1.0D) {
            return null;
        }

        double minimumX = Math.max(-WORLD_COORDINATE_LIMIT, centerX - halfUsableSize);
        double maximumX = Math.min(WORLD_COORDINATE_LIMIT, centerX + halfUsableSize);
        double minimumZ = Math.max(-WORLD_COORDINATE_LIMIT, centerZ - halfUsableSize);
        double maximumZ = Math.min(WORLD_COORDINATE_LIMIT, centerZ + halfUsableSize);
        int minBlockX = (int) Math.ceil(minimumX - 0.5D);
        int maxBlockX = (int) Math.floor(Math.nextDown(maximumX) - 0.5D);
        int minBlockZ = (int) Math.ceil(minimumZ - 0.5D);
        int maxBlockZ = (int) Math.floor(Math.nextDown(maximumZ) - 0.5D);
        if (minBlockX > maxBlockX || minBlockZ > maxBlockZ) {
            return null;
        }
        return new SearchBounds(minBlockX, maxBlockX, minBlockZ, maxBlockZ);
    }

    static Candidate chooseCandidate(
        SearchBounds bounds,
        Location spawn,
        int minimumSpawnDistance,
        Set<Long> triedChunks
    ) {
        if (bounds == null || spawn == null || triedChunks == null) {
            return null;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double minimumDistanceSquared = (double) Math.max(0, minimumSpawnDistance) * Math.max(0, minimumSpawnDistance);
        for (int sample = 0; sample < CANDIDATE_SAMPLES; sample++) {
            int x = random.nextInt(bounds.minX, bounds.maxX + 1);
            int z = random.nextInt(bounds.minZ, bounds.maxZ + 1);
            double dx = (x + 0.5D) - spawn.getX();
            double dz = (z + 0.5D) - spawn.getZ();
            if ((dx * dx) + (dz * dz) < minimumDistanceSquared) {
                continue;
            }
            if (triedChunks.contains(chunkKey(x >> 4, z >> 4))) {
                continue;
            }
            return new Candidate(x, z);
        }
        return null;
    }

    static boolean isSafeDestination(Location destination) {
        if (!LocationUtil.isSafeStandingLocation(destination)) {
            return false;
        }
        Block feet = destination.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block floor = feet.getRelative(BlockFace.DOWN);
        return floor.getType().isSolid()
            && !isUnsafeSurface(floor.getType())
            && !isUnsafeBody(feet.getType())
            && !isUnsafeBody(head.getType());
    }

    static boolean isUnsafeSurface(Material material) {
        return switch (material) {
            case LAVA, WATER, MAGMA_BLOCK, CAMPFIRE, SOUL_CAMPFIRE, CACTUS, POWDER_SNOW,
                POINTED_DRIPSTONE, FIRE, SOUL_FIRE, FROSTED_ICE, NETHER_PORTAL, END_PORTAL,
                END_GATEWAY, SWEET_BERRY_BUSH, WITHER_ROSE -> true;
            default -> false;
        };
    }

    static boolean isUnsafeBody(Material material) {
        return switch (material) {
            case LAVA, WATER, FIRE, SOUL_FIRE, POWDER_SNOW, CACTUS, POINTED_DRIPSTONE,
                SWEET_BERRY_BUSH, WITHER_ROSE, NETHER_PORTAL, END_PORTAL, END_GATEWAY, COBWEB -> true;
            default -> false;
        };
    }

    private static boolean prepareTeleport(Player player) {
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        if (!player.getPassengers().isEmpty()) {
            player.eject();
        }
        return !player.isInsideVehicle() && player.getPassengers().isEmpty();
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static String formatDuration(long totalSeconds) {
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes <= 0L) {
            return seconds + "s";
        }
        if (seconds == 0L) {
            return minutes + "m";
        }
        return minutes + "m " + seconds + "s";
    }

    static record SearchBounds(int minX, int maxX, int minZ, int maxZ) {
        boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        double farthestDistanceSquared(double x, double z) {
            double dx = Math.max(Math.abs((minX + 0.5D) - x), Math.abs((maxX + 0.5D) - x));
            double dz = Math.max(Math.abs((minZ + 0.5D) - z), Math.abs((maxZ + 0.5D) - z));
            return (dx * dx) + (dz * dz);
        }
    }

    static record Candidate(int x, int z) {
    }

    private static final class Request {
        private final UUID playerId;
        private final Set<Long> triedChunks = new HashSet<>();
        private World world;
        private Location spawn;
        private int borderPadding;
        private int minimumSpawnDistance;
        private int maxAttempts;
        private int attempts;
        private Throwable lastLoadError;
        private boolean active;
        private boolean cancelled;
        private boolean finished;

        private Request(UUID playerId) {
            this.playerId = playerId;
        }
    }
}
