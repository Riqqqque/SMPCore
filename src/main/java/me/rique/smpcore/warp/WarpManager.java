package me.rique.smpcore.warp;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.AtomicYamlFile;
import me.rique.smpcore.util.LocationUtil;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.util.PluginCommandRoots;
import me.rique.smpcore.util.ScheduledDimensionPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class WarpManager {

    private static final Pattern COMMAND_NAME = Pattern.compile("[a-z0-9][a-z0-9_-]{0,23}");
    private static final Set<String> RESERVED_SUBCOMMANDS = Set.of("create", "delete", "help", "info", "list", "move");
    private static final int SAFE_HORIZONTAL_RADIUS = 2;
    private static final int SAFE_VERTICAL_RADIUS = 4;

    private final SMPCore plugin;
    private final File storageFile;
    private final Map<String, WarpPoint> warps = new LinkedHashMap<>();
    private final Map<String, Command> directCommands = new HashMap<>();
    private final Set<UUID> pendingTeleports = ConcurrentHashMap.newKeySet();
    private YamlConfiguration data = new YamlConfiguration();
    private boolean storageHealthy;

    public WarpManager(SMPCore plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "warps.yml");
    }

    public void start() {
        load();
        for (WarpPoint point : warps.values()) {
            if (!registerDirectCommand(point)) {
                plugin.getLogger().warning("Warp '" + point.displayName()
                    + "' loaded, but /" + point.commandName() + " is already used. Players can still use /warp "
                    + point.commandName() + ".");
            }
        }
    }

    public void shutdown() {
        CommandMap commandMap = Bukkit.getServer().getCommandMap();
        for (Command command : new ArrayList<>(directCommands.values())) {
            command.unregister(commandMap);
        }
        directCommands.clear();
        pendingTeleports.clear();
    }

    public boolean createWarp(Player admin, String rawName) {
        String name = normalizeName(rawName);
        if (!validateName(admin, name)) return false;
        if (!storageHealthy) {
            admin.sendMessage(MessageUtil.error("Warps cannot be changed until the warps.yml load error is fixed."));
            return false;
        }
        if (warps.containsKey(name) || data.contains(path(name))) {
            admin.sendMessage(MessageUtil.warn("That warp already exists. Use <white>/warp move " + name + "</white> to update it."));
            return false;
        }
        String collision = directCommandCollision(name);
        if (collision != null) {
            admin.sendMessage(MessageUtil.error("/<white>" + name + "</white> " + collision + " Choose another warp name."));
            return false;
        }

        WarpPoint point = WarpPoint.from(name, displayName(rawName, name), admin.getLocation());
        warps.put(name, point);
        if (!registerDirectCommand(point)) {
            warps.remove(name);
            admin.sendMessage(MessageUtil.error("/<white>" + name + "</white> became unavailable. Nothing was saved."));
            return false;
        }

        writePoint(point);
        if (!save()) {
            data.set(path(name), null);
            warps.remove(name);
            unregisterDirectCommand(name);
            admin.sendMessage(MessageUtil.error("The warp could not be saved safely. Nothing was changed."));
            return false;
        }

        refreshClientCommands();
        admin.sendMessage(MessageUtil.success("Created <white>" + point.displayName() + "</white>. Players can now use <aqua>/"
            + point.commandName() + "</aqua>."));
        return true;
    }

    public boolean moveWarp(Player admin, String rawName) {
        String name = normalizeName(rawName);
        WarpPoint previous = warps.get(name);
        if (previous == null) {
            admin.sendMessage(MessageUtil.error("Warp not found. Use <white>/warp list</white>."));
            return false;
        }
        if (!storageHealthy) {
            admin.sendMessage(MessageUtil.error("Warps cannot be changed until the warps.yml load error is fixed."));
            return false;
        }

        WarpPoint moved = WarpPoint.from(name, previous.displayName(), admin.getLocation());
        warps.put(name, moved);
        writePoint(moved);
        if (!save()) {
            warps.put(name, previous);
            writePoint(previous);
            admin.sendMessage(MessageUtil.error("The new location could not be saved. The old warp was kept."));
            return false;
        }

        admin.sendMessage(MessageUtil.success("Moved <white>" + moved.displayName() + "</white> to your current location."));
        return true;
    }

    public boolean deleteWarp(CommandSender sender, String rawName) {
        String name = normalizeName(rawName);
        WarpPoint previous = warps.get(name);
        if (previous == null) {
            sender.sendMessage(MessageUtil.error("Warp not found. Use <white>/warp list</white>."));
            return false;
        }
        if (!storageHealthy) {
            sender.sendMessage(MessageUtil.error("Warps cannot be changed until the warps.yml load error is fixed."));
            return false;
        }

        data.set(path(name), null);
        if (!save()) {
            writePoint(previous);
            sender.sendMessage(MessageUtil.error("The warp could not be deleted safely. It was kept."));
            return false;
        }

        warps.remove(name);
        unregisterDirectCommand(name);
        refreshClientCommands();
        sender.sendMessage(MessageUtil.success("Deleted <white>" + previous.displayName() + "</white>."));
        return true;
    }

    public void teleport(Player player, String rawName) {
        if (!player.hasPermission("smpcore.warp.use") && !player.hasPermission("smpcore.warp.admin")) {
            player.sendMessage(MessageUtil.error("You do not have permission to use warps."));
            return;
        }
        String name = normalizeName(rawName);
        WarpPoint point = warps.get(name);
        if (point == null) {
            player.sendMessage(MessageUtil.error("Warp not found. Use <white>/warp list</white>."));
            return;
        }
        if (isInCombat(player)) return;
        String restriction = activeTeleportRestriction(player);
        if (restriction != null) {
            player.sendMessage(MessageUtil.warn(restriction));
            return;
        }
        if (!pendingTeleports.add(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("Your last warp is still loading."));
            return;
        }

        Location destination = point.toLocation();
        if (destination == null || destination.getWorld() == null) {
            pendingTeleports.remove(player.getUniqueId());
            player.sendMessage(MessageUtil.error("That warp's world is not loaded. Tell an admin."));
            return;
        }
        World world = destination.getWorld();
        if (isScheduledDimensionLocked(player, world)) {
            pendingTeleports.remove(player.getUniqueId());
            return;
        }
        if (!player.hasPermission("smpcore.warp.admin") && !world.getWorldBorder().isInside(destination)) {
            pendingTeleports.remove(player.getUniqueId());
            player.sendMessage(MessageUtil.warn("That warp is outside the current world border."));
            return;
        }

        CompletableFuture<Void> chunkLoad;
        try {
            chunkLoad = loadSafetyChunks(world, destination);
        } catch (RuntimeException ex) {
            pendingTeleports.remove(player.getUniqueId());
            player.sendMessage(MessageUtil.error("That warp could not be loaded. Try again shortly."));
            plugin.getLogger().warning("Could not start warp chunk load for " + player.getName() + ": " + ex.getMessage());
            return;
        }
        chunkLoad.whenComplete((ignored, error) -> runSync(() -> {
            if (!player.isOnline()) {
                pendingTeleports.remove(player.getUniqueId());
                return;
            }
            if (error != null) {
                pendingTeleports.remove(player.getUniqueId());
                player.sendMessage(MessageUtil.error("That warp could not be loaded. Try again shortly."));
                return;
            }
            if (!point.equals(warps.get(name))) {
                pendingTeleports.remove(player.getUniqueId());
                player.sendMessage(MessageUtil.warn("That warp changed while loading. Please try again."));
                return;
            }
            if (isInCombat(player)) {
                pendingTeleports.remove(player.getUniqueId());
                return;
            }
            String currentRestriction = activeTeleportRestriction(player);
            if (currentRestriction != null) {
                pendingTeleports.remove(player.getUniqueId());
                player.sendMessage(MessageUtil.warn(currentRestriction));
                return;
            }
            if (isScheduledDimensionLocked(player, world)) {
                pendingTeleports.remove(player.getUniqueId());
                return;
            }

            Location safe = LocationUtil.findNearestSafeStandingLocation(
                destination,
                SAFE_HORIZONTAL_RADIUS,
                SAFE_VERTICAL_RADIUS
            );
            if (safe == null) {
                pendingTeleports.remove(player.getUniqueId());
                player.sendMessage(MessageUtil.error("That warp is blocked or unsafe. Tell an admin to move it."));
                return;
            }
            if (!player.hasPermission("smpcore.warp.admin") && !world.getWorldBorder().isInside(safe)) {
                pendingTeleports.remove(player.getUniqueId());
                player.sendMessage(MessageUtil.warn("That warp is outside the current world border."));
                return;
            }

            Location returnLocation = player.getLocation().clone();
            beginValidatedTeleport(player, point, safe, returnLocation, true);
        }));
    }

    private void beginValidatedTeleport(
        Player player,
        WarpPoint point,
        Location destination,
        Location returnLocation,
        boolean retryAvailable
    ) {
        UUID playerId = player.getUniqueId();
        if (!player.isOnline()) {
            pendingTeleports.remove(playerId);
            return;
        }
        if (!point.equals(warps.get(point.commandName()))) {
            pendingTeleports.remove(playerId);
            player.sendMessage(MessageUtil.warn("That warp changed while loading. Please try again."));
            return;
        }
        if (isInCombat(player)) {
            pendingTeleports.remove(playerId);
            return;
        }
        String restriction = activeTeleportRestriction(player);
        if (restriction != null) {
            pendingTeleports.remove(playerId);
            player.sendMessage(MessageUtil.warn(restriction));
            return;
        }

        boolean crossWorld = !player.getWorld().getUID().equals(destination.getWorld().getUID());
        if (crossWorld && !prepareCrossWorldTeleport(player)) {
            pendingTeleports.remove(playerId);
            player.sendMessage(MessageUtil.warn("Leave your seat or mount, then try the warp again."));
            return;
        }

        player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)
            .whenComplete((success, teleportError) -> runSync(() -> {
                if (!player.isOnline()) {
                    pendingTeleports.remove(playerId);
                    return;
                }
                if (teleportError == null && Boolean.TRUE.equals(success)) {
                    pendingTeleports.remove(playerId);
                    plugin.getPlayerManager().saveBackLocation(playerId, returnLocation);
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.65F, 1.15F);
                    player.sendMessage(MessageUtil.success("Warped to <white>" + point.displayName() + "</white>."));
                    return;
                }

                boolean combatBlocked = plugin.getCombatLogListener() != null
                    && plugin.getCombatLogListener().isInPlayerCombat(player);
                String currentRestriction = activeTeleportRestriction(player);
                if (shouldRetryCrossWorldTeleport(
                    crossWorld,
                    player.isOnline(),
                    combatBlocked,
                    currentRestriction != null,
                    retryAvailable
                )) {
                    Bukkit.getScheduler().runTaskLater(
                        plugin,
                        () -> beginValidatedTeleport(player, point, destination, returnLocation, false),
                        1L
                    );
                    return;
                }

                pendingTeleports.remove(playerId);
                if (combatBlocked) {
                    player.sendMessage(MessageUtil.warn("You cannot warp while in combat."));
                } else if (currentRestriction != null) {
                    player.sendMessage(MessageUtil.warn(currentRestriction));
                } else {
                    player.sendMessage(MessageUtil.error("Warp failed. Dismount and try again."));
                }
                String errorName = teleportError == null ? "none" : teleportError.getClass().getSimpleName();
                plugin.getLogger().warning(
                    "Warp teleport rejected for " + player.getName()
                        + " from=" + player.getWorld().getName()
                        + " to=" + destination.getWorld().getName()
                        + " crossWorld=" + crossWorld
                        + " vehicle=" + player.isInsideVehicle()
                        + " passengers=" + player.getPassengers().size()
                        + " error=" + errorName
                );
            }));
    }

    private static boolean prepareCrossWorldTeleport(Player player) {
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        if (!player.getPassengers().isEmpty()) {
            player.eject();
        }
        return !player.isInsideVehicle() && player.getPassengers().isEmpty();
    }

    private String activeTeleportRestriction(Player player) {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().blocksExternalTeleport(player)) {
            return "You cannot use warps during a duel.";
        }
        if ((plugin.getBossManager() != null && plugin.getBossManager().isActiveBossFight(player))
            || (plugin.getBossDungeonManager() != null && plugin.getBossDungeonManager().blocksExternalTeleport(player))) {
            return "You cannot leave an active boss fight.";
        }
        return null;
    }

    static boolean shouldRetryCrossWorldTeleport(
        boolean crossWorld,
        boolean online,
        boolean combatBlocked,
        boolean encounterBlocked,
        boolean retryAvailable
    ) {
        return crossWorld && online && !combatBlocked && !encounterBlocked && retryAvailable;
    }

    private boolean isScheduledDimensionLocked(Player player, World destination) {
        World.Environment from = player.getWorld().getEnvironment();
        World.Environment to = destination.getEnvironment();
        if (to != World.Environment.NETHER && to != World.Environment.THE_END) return false;
        boolean bypass = player.hasPermission("smpcore.startsmp.bypass-dimension-lock");
        boolean unlocked = plugin.getSmpStartManager() == null || plugin.getSmpStartManager().isDimensionUnlocked(to);
        if (!ScheduledDimensionPolicy.blocksTravel(from, to, bypass, unlocked)) return false;
        String dimension = to == World.Environment.NETHER ? "Nether" : "End";
        player.sendMessage(MessageUtil.warn("The <white>" + dimension + "</white> is still locked."));
        return true;
    }

    private static CompletableFuture<Void> loadSafetyChunks(World world, Location destination) {
        int minChunkX = (destination.getBlockX() - SAFE_HORIZONTAL_RADIUS) >> 4;
        int maxChunkX = (destination.getBlockX() + SAFE_HORIZONTAL_RADIUS) >> 4;
        int minChunkZ = (destination.getBlockZ() - SAFE_HORIZONTAL_RADIUS) >> 4;
        int maxChunkZ = (destination.getBlockZ() + SAFE_HORIZONTAL_RADIUS) >> 4;
        CompletableFuture<?>[] loads = new CompletableFuture<?>[(maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1)];
        int index = 0;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                loads[index++] = world.getChunkAtAsync(chunkX, chunkZ, false);
            }
        }
        return CompletableFuture.allOf(loads);
    }

    public void sendList(CommandSender sender) {
        if (warps.isEmpty()) {
            sender.sendMessage(MessageUtil.info("No public warps have been created yet."));
            return;
        }
        sender.sendMessage(MessageUtil.info("Public warps:"));
        sortedPoints().forEach(point -> sender.sendMessage(MessageUtil.parse(
            "<gray>- <click:run_command:'/" + point.commandName() + "'><hover:show_text:'<gray>Warp to "
                + point.displayName() + "</gray>'><aqua>/" + point.commandName() + "</aqua></hover></click></gray>"
        )));
    }

    public void sendInfo(CommandSender sender, String rawName) {
        WarpPoint point = warps.get(normalizeName(rawName));
        if (point == null) {
            sender.sendMessage(MessageUtil.error("Warp not found. Use <white>/warp list</white>."));
            return;
        }
        sender.sendMessage(MessageUtil.info("<white>" + point.displayName() + "</white>: <aqua>/" + point.commandName()
            + "</aqua> at <white>" + point.worldName() + " " + floor(point.x()) + ", " + floor(point.y()) + ", "
            + floor(point.z()) + "</white>."));
    }

    public List<String> commandNames() {
        return warps.keySet().stream().sorted().toList();
    }

    public boolean isWarpCommand(String root) {
        return root != null && warps.containsKey(normalizeName(root));
    }

    public int warpCount() {
        return warps.size();
    }

    static String normalizeName(String rawName) {
        if (rawName == null) return "";
        String normalized = rawName.trim().toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        return normalized;
    }

    static boolean isValidName(String normalizedName) {
        return normalizedName != null && COMMAND_NAME.matcher(normalizedName).matches();
    }

    static boolean isReservedName(String normalizedName) {
        return RESERVED_SUBCOMMANDS.contains(normalizedName) || PluginCommandRoots.contains(normalizedName);
    }

    private boolean validateName(CommandSender sender, String name) {
        if (!isValidName(name)) {
            sender.sendMessage(MessageUtil.error("Warp names must be 1-24 letters, numbers, dashes, or underscores, and start with a letter or number."));
            return false;
        }
        if (isReservedName(name)) {
            sender.sendMessage(MessageUtil.error("/<white>" + name + "</white> is reserved by another server command."));
            return false;
        }
        return true;
    }

    private String directCommandCollision(String name) {
        if (isReservedName(name)) return "is reserved by another server command.";
        Command existing = Bukkit.getServer().getCommandMap().getCommand(name);
        return existing == null ? null : "is already used by another command.";
    }

    private boolean registerDirectCommand(WarpPoint point) {
        String name = point.commandName();
        if (directCommands.containsKey(name) || directCommandCollision(name) != null) return false;
        DirectWarpCommand command = new DirectWarpCommand(this, point);
        CommandMap commandMap = Bukkit.getServer().getCommandMap();
        if (!commandMap.register(name, plugin.getName().toLowerCase(Locale.ROOT), command)) {
            command.unregister(commandMap);
            return false;
        }
        directCommands.put(name, command);
        return true;
    }

    private void unregisterDirectCommand(String name) {
        Command command = directCommands.remove(name);
        if (command != null) command.unregister(Bukkit.getServer().getCommandMap());
    }

    private void refreshClientCommands() {
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(Player::updateCommands));
    }

    private boolean isInCombat(Player player) {
        if (plugin.getCombatLogListener() == null || !plugin.getCombatLogListener().isInPlayerCombat(player)) return false;
        player.sendMessage(MessageUtil.warn("You cannot warp while in combat."));
        return true;
    }

    private void runSync(Runnable action) {
        if (!plugin.isEnabled()) return;
        if (Bukkit.isPrimaryThread()) action.run();
        else Bukkit.getScheduler().runTask(plugin, action);
    }

    private void load() {
        warps.clear();
        data = new YamlConfiguration();
        storageHealthy = true;
        if (!storageFile.isFile()) return;

        try {
            data.load(storageFile);
        } catch (IOException | InvalidConfigurationException ex) {
            storageHealthy = false;
            plugin.getLogger().severe("Could not load warps.yml: " + ex.getMessage());
            return;
        }

        ConfigurationSection section = data.getConfigurationSection("warps");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String name = normalizeName(key);
            String base = path(key);
            if (!isValidName(name) || isReservedName(name) || !name.equals(key)) {
                plugin.getLogger().warning("Skipped invalid warp key '" + key + "' in warps.yml.");
                continue;
            }
            WarpPoint point = readPoint(name, base);
            if (point == null) {
                plugin.getLogger().warning("Skipped incomplete warp '" + key + "' in warps.yml.");
                continue;
            }
            if (warps.putIfAbsent(name, point) != null) {
                plugin.getLogger().warning("Skipped duplicate warp '" + key + "' in warps.yml.");
            }
        }
    }

    private WarpPoint readPoint(String name, String base) {
        String worldName = data.getString(base + ".world-name", "").trim();
        String worldIdText = data.getString(base + ".world-id", "").trim();
        UUID worldId = null;
        if (!worldIdText.isEmpty()) {
            try {
                worldId = UUID.fromString(worldIdText);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        if (worldId == null && worldName.isEmpty()) return null;

        double x = data.getDouble(base + ".x", Double.NaN);
        double y = data.getDouble(base + ".y", Double.NaN);
        double z = data.getDouble(base + ".z", Double.NaN);
        float yaw = (float) data.getDouble(base + ".yaw", 0.0D);
        float pitch = (float) data.getDouble(base + ".pitch", 0.0D);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
            || !Float.isFinite(yaw) || !Float.isFinite(pitch)) return null;

        String display = data.getString(base + ".display-name", name).trim();
        if (display.isEmpty()) display = name;
        return new WarpPoint(name, display, worldId, worldName, x, y, z, yaw, pitch);
    }

    private void writePoint(WarpPoint point) {
        String base = path(point.commandName());
        data.set(base + ".display-name", point.displayName());
        data.set(base + ".world-id", point.worldId() == null ? null : point.worldId().toString());
        data.set(base + ".world-name", point.worldName());
        data.set(base + ".x", point.x());
        data.set(base + ".y", point.y());
        data.set(base + ".z", point.z());
        data.set(base + ".yaw", point.yaw());
        data.set(base + ".pitch", point.pitch());
    }

    private boolean save() {
        try {
            AtomicYamlFile.save(data, storageFile);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save warps.yml: " + ex.getMessage());
            return false;
        }
    }

    private List<WarpPoint> sortedPoints() {
        return warps.values().stream().sorted(Comparator.comparing(WarpPoint::commandName)).toList();
    }

    private static String path(String name) {
        return "warps." + name;
    }

    private static String displayName(String rawName, String fallback) {
        if (rawName == null) return fallback;
        String display = rawName.trim();
        while (display.startsWith("/")) display = display.substring(1);
        return display.isEmpty() ? fallback : display;
    }

    private static int floor(double coordinate) {
        return (int) Math.floor(coordinate);
    }

    private record WarpPoint(
        String commandName,
        String displayName,
        UUID worldId,
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
    ) {
        private static WarpPoint from(String commandName, String displayName, Location location) {
            World world = location.getWorld();
            return new WarpPoint(
                commandName,
                displayName,
                world == null ? null : world.getUID(),
                world == null ? "" : world.getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
            );
        }

        private Location toLocation() {
            World world = worldId == null ? null : Bukkit.getWorld(worldId);
            if (world == null && !worldName.isBlank()) world = Bukkit.getWorld(worldName);
            return world == null ? null : new Location(world, x, y, z, yaw, pitch);
        }
    }

    private static final class DirectWarpCommand extends Command {

        private final WarpManager manager;
        private final String warpName;

        private DirectWarpCommand(WarpManager manager, WarpPoint point) {
            super(point.commandName(), "Warp to " + point.displayName(), "/" + point.commandName(), List.of());
            this.manager = manager;
            this.warpName = point.commandName();
            setPermission("smpcore.warp.use");
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.error("Only players can use public warps."));
                return true;
            }
            if (args.length != 0) {
                player.sendMessage(MessageUtil.info("Use <white>/" + warpName + "</white> without extra words."));
                return true;
            }
            manager.teleport(player, warpName);
            return true;
        }
    }
}
