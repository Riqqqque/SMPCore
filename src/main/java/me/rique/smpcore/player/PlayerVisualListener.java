package me.rique.smpcore.player;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.team.TeamManager;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.util.VisualRangeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerVisualListener implements Listener {

    private static final long SYNC_PERIOD_TICKS = 5L;
    private static final long PLAYER_FINDER_PERIOD_TICKS = 20L;
    private static final int GLOW_REFRESH_TICKS = 80;

    private final SMPCore plugin;
    private final NamespacedKey keyNameHologram;
    private final NamespacedKey keyNameHologramOwner;
    private final NamespacedKey keyTeamGlowMarker;
    private final NamespacedKey keyTeamGlowViewer;
    private final NamespacedKey keyTeamGlowTarget;
    private final Map<UUID, UUID> nameDisplaysByPlayer = new ConcurrentHashMap<>();
    private final Set<UUID> teamGlowViewers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<UUID>> glowingTargetsByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, UUID>> teamGlowMarkersByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> playerFinderHiddenTargetsByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, Component> lastNameplateTexts = new ConcurrentHashMap<>();
    private BukkitTask task;
    private int glowTickCounter;
    private int playerFinderTickCounter;

    public PlayerVisualListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyNameHologram = new NamespacedKey(plugin, "player_name_hologram");
        this.keyNameHologramOwner = new NamespacedKey(plugin, "player_name_hologram_owner");
        this.keyTeamGlowMarker = new NamespacedKey(plugin, "team_glow_marker");
        this.keyTeamGlowViewer = new NamespacedKey(plugin, "team_glow_viewer");
        this.keyTeamGlowTarget = new NamespacedKey(plugin, "team_glow_target");
    }

    public void start() {
        if (task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickVisuals, 1L, SYNC_PERIOD_TICKS);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        clearPlayerFinderDefense();
        for (UUID viewerId : new ArrayList<>(glowingTargetsByViewer.keySet())) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) {
                clearViewerGlow(viewer);
            }
        }
        teamGlowViewers.clear();
        removeAllTeamGlowMarkers();
        removeAllNameDisplays();
    }

    public boolean toggleTeamGlow(Player player) {
        if (teamGlowViewers.contains(player.getUniqueId())) {
            setTeamGlow(player, false);
            return false;
        }
        setTeamGlow(player, true);
        return true;
    }

    public void setTeamGlow(Player player, boolean enabled) {
        UUID playerId = player.getUniqueId();
        if (enabled) {
            TeamManager teams = plugin.getTeamManager();
            if (teams == null || !teams.inTeam(playerId)) {
                player.sendMessage(MessageUtil.error("Join a team before using teammate glow."));
                return;
            }
            teamGlowViewers.add(playerId);
            int highlighted = syncTeamGlow(player);
            player.sendMessage(MessageUtil.success("Teammate glow enabled. Only you can see it."));
            if (highlighted == 0) {
                player.sendMessage(MessageUtil.info("No online teammates in your current world are available to highlight yet."));
            } else {
                player.sendMessage(MessageUtil.info("Highlighted <white>" + highlighted + "</white> online teammate" + (highlighted == 1 ? "" : "s") + "."));
            }
            return;
        }

        teamGlowViewers.remove(playerId);
        clearViewerGlow(player);
        player.sendMessage(MessageUtil.info("Teammate glow disabled."));
    }

    public boolean isTeamGlowEnabled(Player player) {
        return teamGlowViewers.contains(player.getUniqueId());
    }

    public void refreshPlayerFinderDefense() {
        syncPlayerFinderDefense();
    }

    private void tickVisuals() {
        syncNameDisplays();
        syncTeamGlowMarkerPositions();
        playerFinderTickCounter += SYNC_PERIOD_TICKS;
        if (playerFinderTickCounter >= PLAYER_FINDER_PERIOD_TICKS) {
            playerFinderTickCounter = 0;
            syncPlayerFinderDefense();
        }
        glowTickCounter += SYNC_PERIOD_TICKS;
        if (glowTickCounter < 20) {
            return;
        }
        glowTickCounter = 0;
        for (UUID viewerId : new ArrayList<>(teamGlowViewers)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()) {
                teamGlowViewers.remove(viewerId);
                glowingTargetsByViewer.remove(viewerId);
                continue;
            }
            syncTeamGlow(viewer);
        }
    }

    private void syncPlayerFinderDefense() {
        if (plugin.getConfigManager() == null || !plugin.getConfigManager().playerFinderDefenseEnabled) {
            clearPlayerFinderDefense();
            return;
        }

        ArrayList<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        Set<UUID> onlineIds = new HashSet<>();
        for (Player player : players) {
            onlineIds.add(player.getUniqueId());
        }

        for (UUID viewerId : new ArrayList<>(playerFinderHiddenTargetsByViewer.keySet())) {
            if (!onlineIds.contains(viewerId)) {
                playerFinderHiddenTargetsByViewer.remove(viewerId);
            }
        }

        TeamManager teams = plugin.getTeamManager();
        for (Player viewer : players) {
            syncPlayerFinderDefense(viewer, players, teams);
        }
    }

    private void syncPlayerFinderDefense(Player viewer, ArrayList<Player> players, TeamManager teams) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }

        Set<UUID> desiredHidden = new HashSet<>();
        for (Player target : players) {
            if (shouldHideForPlayerFinderDefense(viewer, target, teams)) {
                desiredHidden.add(target.getUniqueId());
            }
        }

        UUID viewerId = viewer.getUniqueId();
        Set<UUID> hidden = playerFinderHiddenTargetsByViewer.computeIfAbsent(viewerId, ignored -> ConcurrentHashMap.newKeySet());
        for (UUID targetId : new ArrayList<>(hidden)) {
            if (!desiredHidden.contains(targetId)) {
                showPlayerFinderDefenseTarget(viewer, targetId);
                hidden.remove(targetId);
            }
        }

        for (Player target : players) {
            UUID targetId = target.getUniqueId();
            if (!desiredHidden.contains(targetId) || hidden.contains(targetId)) {
                continue;
            }
            viewer.hideEntity(plugin, target);
            hidden.add(targetId);
            clearFakeGlow(viewer, target);
            removeTeamGlowMarker(viewerId, targetId);
        }

        if (hidden.isEmpty()) {
            playerFinderHiddenTargetsByViewer.remove(viewerId);
        }
    }

    private boolean shouldHideForPlayerFinderDefense(Player viewer, Player target, TeamManager teams) {
        if (viewer == null || target == null || viewer.equals(target)) {
            return false;
        }
        if (!viewer.isOnline() || !target.isOnline() || target.isDead() || !target.isValid()) {
            return false;
        }
        if (viewer.getWorld() != target.getWorld() || target.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        if (isVanishedFromViewer(viewer, target)) {
            return false;
        }
        if (plugin.getConfigManager().playerFinderDefenseIgnoreOps && (viewer.isOp() || target.isOp())) {
            return false;
        }
        if (!plugin.getConfigManager().playerFinderDefenseHideSameTeam
            && teams != null
            && teams.sameTeam(viewer.getUniqueId(), target.getUniqueId())) {
            return false;
        }

        double closeRadius = plugin.getConfigManager().playerFinderDefenseAlwaysShowRadius;
        double distanceSquared = viewer.getLocation().distanceSquared(target.getLocation());
        if (distanceSquared <= closeRadius * closeRadius) {
            return false;
        }

        double lineOfSightRadius = plugin.getConfigManager().playerFinderDefenseLineOfSightRadius;
        if (distanceSquared <= lineOfSightRadius * lineOfSightRadius && viewer.hasLineOfSight(target)) {
            return false;
        }
        return true;
    }

    private boolean isVanishedFromViewer(Player viewer, Player target) {
        return plugin.getPlayerManager() != null
            && plugin.getPlayerManager().isVanished(target.getUniqueId())
            && !viewer.hasPermission("smpcore.vanish.see");
    }

    private void showPlayerFinderDefenseTarget(Player viewer, UUID targetId) {
        if (viewer == null || !viewer.isOnline() || targetId == null) {
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline() || isVanishedFromViewer(viewer, target)) {
            return;
        }
        viewer.showPlayer(plugin, target);
        viewer.showEntity(plugin, target);
    }

    private void clearPlayerFinderDefenseForViewer(Player viewer) {
        if (viewer == null) {
            return;
        }
        Set<UUID> hidden = playerFinderHiddenTargetsByViewer.remove(viewer.getUniqueId());
        if (hidden == null) {
            return;
        }
        for (UUID targetId : hidden) {
            showPlayerFinderDefenseTarget(viewer, targetId);
        }
    }

    private void clearPlayerFinderDefenseForTarget(UUID targetId) {
        if (targetId == null) {
            return;
        }
        for (UUID viewerId : new ArrayList<>(playerFinderHiddenTargetsByViewer.keySet())) {
            Set<UUID> hidden = playerFinderHiddenTargetsByViewer.get(viewerId);
            if (hidden == null || !hidden.remove(targetId)) {
                continue;
            }
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) {
                showPlayerFinderDefenseTarget(viewer, targetId);
            }
            if (hidden.isEmpty()) {
                playerFinderHiddenTargetsByViewer.remove(viewerId);
            }
        }
    }

    private void clearPlayerFinderDefense() {
        for (UUID viewerId : new ArrayList<>(playerFinderHiddenTargetsByViewer.keySet())) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null) {
                playerFinderHiddenTargetsByViewer.remove(viewerId);
                continue;
            }
            clearPlayerFinderDefenseForViewer(viewer);
        }
        playerFinderHiddenTargetsByViewer.clear();
    }

    private void syncNameDisplays() {
        Set<UUID> onlineIds = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            onlineIds.add(player.getUniqueId());
            syncNameDisplay(player);
        }

        for (UUID playerId : new ArrayList<>(nameDisplaysByPlayer.keySet())) {
            if (!onlineIds.contains(playerId)) {
                removeNameDisplay(playerId);
            }
        }
    }

    private void syncNameDisplay(Player player) {
        if (!shouldShowNameDisplay(player)) {
            removeNameDisplay(player.getUniqueId());
            return;
        }

        TextDisplay display = getOrCreateNameDisplay(player);
        if (display == null || !display.isValid()) {
            return;
        }

        Location target = nameLocation(player);
        if (display.getWorld() != target.getWorld()) {
            removeNameDisplay(player.getUniqueId());
            display = getOrCreateNameDisplay(player);
            if (display == null || !display.isValid()) {
                return;
            }
        }

        display.teleport(target);
        Component text = nameplateText(player);
        if (!Objects.equals(lastNameplateTexts.get(player.getUniqueId()), text) || !Objects.equals(display.text(), text)) {
            display.text(text);
            lastNameplateTexts.put(player.getUniqueId(), text);
        }
        player.hideEntity(plugin, display);
    }

    private TextDisplay getOrCreateNameDisplay(Player player) {
        UUID playerId = player.getUniqueId();
        UUID existingId = nameDisplaysByPlayer.get(playerId);
        Entity existing = existingId == null ? null : Bukkit.getEntity(existingId);
        if (existing instanceof TextDisplay display && display.isValid()) {
            return display;
        }
        if (existing != null) {
            existing.remove();
        }
        nameDisplaysByPlayer.remove(playerId);

        World world = player.getWorld();
        TextDisplay display = world.spawn(nameLocation(player), TextDisplay.class, textDisplay -> {
            textDisplay.text(nameplateText(player));
            textDisplay.setPersistent(false);
            textDisplay.setGravity(false);
            textDisplay.setInvulnerable(true);
            textDisplay.setBillboard(Display.Billboard.CENTER);
            textDisplay.setAlignment(TextDisplay.TextAlignment.CENTER);
            textDisplay.setSeeThrough(false);
            textDisplay.setShadowed(true);
            textDisplay.setDefaultBackground(false);
            textDisplay.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            textDisplay.setTextOpacity((byte) 255);
            textDisplay.setLineWidth(180);
            textDisplay.setTeleportDuration((int) SYNC_PERIOD_TICKS);
            textDisplay.setInterpolationDelay(0);
            textDisplay.setInterpolationDuration((int) SYNC_PERIOD_TICKS);
            VisualRangeUtil.applyHologramRange(textDisplay);
            textDisplay.getPersistentDataContainer().set(keyNameHologram, PersistentDataType.BYTE, (byte) 1);
            textDisplay.getPersistentDataContainer().set(keyNameHologramOwner, PersistentDataType.STRING, playerId.toString());
        });
        nameDisplaysByPlayer.put(playerId, display.getUniqueId());
        player.hideEntity(plugin, display);
        return display;
    }

    private boolean shouldShowNameDisplay(Player player) {
        if (player == null || !player.isOnline() || player.isDead() || !player.isValid()) {
            return false;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        if (plugin.getPlayerManager() != null && plugin.getPlayerManager().isVanished(player.getUniqueId())) {
            return false;
        }
        return !player.isInvisible() && !player.hasPotionEffect(PotionEffectType.INVISIBILITY);
    }

    private Component nameplateText(Player player) {
        TeamManager teams = plugin.getTeamManager();
        if (teams != null) {
            return teams.nameplateText(player);
        }
        Component displayName = player.displayName();
        return displayName == null ? Component.text(player.getName()) : displayName;
    }

    private Location nameLocation(Player player) {
        double yOffset = Math.max(1.85, player.getHeight() + 0.45);
        return player.getLocation().clone().add(0.0, yOffset, 0.0);
    }

    private void removeNameDisplay(UUID playerId) {
        if (playerId == null) {
            return;
        }
        UUID displayId = nameDisplaysByPlayer.remove(playerId);
        lastNameplateTexts.remove(playerId);
        Entity display = displayId == null ? null : Bukkit.getEntity(displayId);
        if (display != null) {
            display.remove();
        }
    }

    private void removeAllNameDisplays() {
        for (UUID playerId : new ArrayList<>(nameDisplaysByPlayer.keySet())) {
            removeNameDisplay(playerId);
        }
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getPersistentDataContainer().has(keyNameHologram, PersistentDataType.BYTE)) {
                    display.remove();
                }
            }
        }
    }

    private int syncTeamGlow(Player viewer) {
        if (!teamGlowViewers.contains(viewer.getUniqueId())) {
            clearViewerGlow(viewer);
            return 0;
        }

        TeamManager teams = plugin.getTeamManager();
        if (teams == null || !teams.inTeam(viewer.getUniqueId())) {
            teamGlowViewers.remove(viewer.getUniqueId());
            clearViewerGlow(viewer);
            return 0;
        }

        Set<UUID> desired = new HashSet<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!shouldGlowForViewer(viewer, target, teams)) {
                continue;
            }
            desired.add(target.getUniqueId());
            viewer.sendPotionEffectChange(
                target,
                new PotionEffect(PotionEffectType.GLOWING, GLOW_REFRESH_TICKS, 0, false, false, false)
            );
            syncTeamGlowMarker(viewer, target);
        }

        Set<UUID> current = glowingTargetsByViewer.computeIfAbsent(viewer.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
        for (UUID targetId : new ArrayList<>(current)) {
            if (!desired.contains(targetId)) {
                Player target = Bukkit.getPlayer(targetId);
                if (target != null) {
                    clearFakeGlow(viewer, target);
                }
                removeTeamGlowMarker(viewer.getUniqueId(), targetId);
                current.remove(targetId);
            }
        }
        current.addAll(desired);
        return desired.size();
    }

    private boolean shouldGlowForViewer(Player viewer, Player target, TeamManager teams) {
        if (viewer == null || target == null || viewer.equals(target)) {
            return false;
        }
        if (!viewer.isOnline() || !target.isOnline() || target.isDead() || !target.isValid()) {
            return false;
        }
        if (viewer.getWorld() != target.getWorld() || target.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        if (!viewer.canSee(target)) {
            return false;
        }
        return teams.sameTeam(viewer.getUniqueId(), target.getUniqueId());
    }

    private void clearViewerGlow(Player viewer) {
        Set<UUID> current = glowingTargetsByViewer.remove(viewer.getUniqueId());
        if (current != null) {
            for (UUID targetId : current) {
                Player target = Bukkit.getPlayer(targetId);
                if (target != null) {
                    clearFakeGlow(viewer, target);
                }
            }
        }
        removeTeamGlowMarkersForViewer(viewer.getUniqueId());
    }

    private void clearFakeGlow(Player viewer, Player target) {
        if (viewer == null || target == null || !viewer.isOnline()) {
            return;
        }
        if (target.isGlowing() || target.hasPotionEffect(PotionEffectType.GLOWING)) {
            return;
        }
        viewer.sendPotionEffectChangeRemove(target, PotionEffectType.GLOWING);
    }

    private void syncTeamGlowMarker(Player viewer, Player target) {
        Map<UUID, UUID> byTarget = teamGlowMarkersByViewer.computeIfAbsent(viewer.getUniqueId(), ignored -> new ConcurrentHashMap<>());
        UUID existingId = byTarget.get(target.getUniqueId());
        Entity existing = existingId == null ? null : Bukkit.getEntity(existingId);
        TextDisplay display = existing instanceof TextDisplay textDisplay && textDisplay.isValid() ? textDisplay : null;
        boolean created = false;
        if (display == null) {
            if (existing != null) {
                existing.remove();
            }
            display = target.getWorld().spawn(teamGlowMarkerLocation(target), TextDisplay.class, marker -> {
                marker.setPersistent(false);
                marker.setGravity(false);
                marker.setInvulnerable(true);
                marker.setBillboard(Display.Billboard.CENTER);
                marker.setAlignment(TextDisplay.TextAlignment.CENTER);
                marker.setSeeThrough(true);
                marker.setShadowed(true);
                marker.setDefaultBackground(false);
                marker.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                marker.setTextOpacity((byte) 255);
                marker.setLineWidth(160);
                marker.setViewRange(VisualRangeUtil.blocksToDisplayViewRange(VisualRangeUtil.HOLOGRAM_VIEW_RANGE_BLOCKS));
                marker.setGlowing(true);
                marker.setGlowColorOverride(Color.AQUA);
                marker.getPersistentDataContainer().set(keyTeamGlowMarker, PersistentDataType.BYTE, (byte) 1);
                marker.getPersistentDataContainer().set(keyTeamGlowViewer, PersistentDataType.STRING, viewer.getUniqueId().toString());
                marker.getPersistentDataContainer().set(keyTeamGlowTarget, PersistentDataType.STRING, target.getUniqueId().toString());
            });
            byTarget.put(target.getUniqueId(), display.getUniqueId());
            created = true;
        }

        display.teleport(teamGlowMarkerLocation(target));
        Component text = teamGlowText(target);
        if (!Objects.equals(display.text(), text)) {
            display.text(text);
        }
        if (created) {
            showTeamGlowMarkerOnlyToViewer(viewer.getUniqueId(), display);
        }
    }

    private void syncTeamGlowMarkerPositions() {
        TeamManager teams = plugin.getTeamManager();
        if (teams == null) {
            removeAllTeamGlowMarkers();
            glowingTargetsByViewer.clear();
            return;
        }
        for (UUID viewerId : new ArrayList<>(teamGlowMarkersByViewer.keySet())) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline() || !teamGlowViewers.contains(viewerId)) {
                removeTeamGlowMarkersForViewer(viewerId);
                continue;
            }
            Map<UUID, UUID> markers = teamGlowMarkersByViewer.get(viewerId);
            if (markers == null) {
                continue;
            }
            for (UUID targetId : new ArrayList<>(markers.keySet())) {
                Player target = Bukkit.getPlayer(targetId);
                Entity entity = Bukkit.getEntity(markers.get(targetId));
                if (!(entity instanceof TextDisplay display) || !shouldGlowForViewer(viewer, target, teams)) {
                    removeTeamGlowMarker(viewerId, targetId);
                    continue;
                }
                display.teleport(teamGlowMarkerLocation(target));
                Component text = teamGlowText(target);
                if (!Objects.equals(display.text(), text)) {
                    display.text(text);
                }
            }
        }
    }

    private Component teamGlowText(Player target) {
        Component targetName = target.displayName() == null ? Component.text(target.getName(), NamedTextColor.WHITE) : target.displayName();
        return Component.text("[ALLY] ", NamedTextColor.AQUA)
            .append(targetName)
            .append(Component.text(" [ALLY]", NamedTextColor.AQUA));
    }

    private Location teamGlowMarkerLocation(Player target) {
        return target.getLocation().clone().add(0.0, Math.max(2.25, target.getHeight() + 0.75), 0.0);
    }

    private void removeTeamGlowMarker(UUID viewerId, UUID targetId) {
        Map<UUID, UUID> markers = teamGlowMarkersByViewer.get(viewerId);
        if (markers == null) {
            return;
        }
        UUID markerId = markers.remove(targetId);
        Entity entity = markerId == null ? null : Bukkit.getEntity(markerId);
        if (entity != null) {
            entity.remove();
        }
        if (markers.isEmpty()) {
            teamGlowMarkersByViewer.remove(viewerId);
        }
    }

    private void removeTeamGlowMarkersForViewer(UUID viewerId) {
        Map<UUID, UUID> markers = teamGlowMarkersByViewer.remove(viewerId);
        if (markers == null) {
            return;
        }
        for (UUID markerId : markers.values()) {
            Entity entity = markerId == null ? null : Bukkit.getEntity(markerId);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    private void removeAllTeamGlowMarkers() {
        for (UUID viewerId : new ArrayList<>(teamGlowMarkersByViewer.keySet())) {
            removeTeamGlowMarkersForViewer(viewerId);
        }
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getPersistentDataContainer().has(keyTeamGlowMarker, PersistentDataType.BYTE)) {
                    display.remove();
                }
            }
        }
    }

    private void showTeamGlowMarkerOnlyToViewer(UUID viewerId, Entity display) {
        if (display == null) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(viewerId)) {
                online.showEntity(plugin, display);
            } else {
                online.hideEntity(plugin, display);
            }
        }
    }

    private void syncTeamGlowMarkerVisibilityFor(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        for (Map.Entry<UUID, Map<UUID, UUID>> entry : teamGlowMarkersByViewer.entrySet()) {
            boolean shouldSee = entry.getKey().equals(playerId);
            for (UUID markerId : entry.getValue().values()) {
                Entity marker = markerId == null ? null : Bukkit.getEntity(markerId);
                if (marker == null) {
                    continue;
                }
                if (shouldSee) {
                    player.showEntity(plugin, marker);
                } else {
                    player.hideEntity(plugin, marker);
                }
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            syncNameDisplay(event.getPlayer());
            syncTeamGlow(event.getPlayer());
            syncTeamGlowMarkerVisibilityFor(event.getPlayer());
            syncTeamGlowMarkerPositions();
            syncPlayerFinderDefense();
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        clearPlayerFinderDefenseForViewer(event.getPlayer());
        clearPlayerFinderDefenseForTarget(playerId);
        removeNameDisplay(playerId);
        teamGlowViewers.remove(playerId);
        glowingTargetsByViewer.remove(playerId);
        removeTeamGlowMarkersForViewer(playerId);
        for (Set<UUID> targets : glowingTargetsByViewer.values()) {
            targets.remove(playerId);
        }
        for (UUID viewerId : new ArrayList<>(teamGlowMarkersByViewer.keySet())) {
            removeTeamGlowMarker(viewerId, playerId);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        clearPlayerFinderDefenseForTarget(event.getPlayer().getUniqueId());
        removeNameDisplay(event.getPlayer().getUniqueId());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(event.getPlayer())) {
                continue;
            }
            clearFakeGlow(viewer, event.getPlayer());
            removeTeamGlowMarker(viewer.getUniqueId(), event.getPlayer().getUniqueId());
            Set<UUID> targets = glowingTargetsByViewer.get(viewer.getUniqueId());
            if (targets != null) {
                targets.remove(event.getPlayer().getUniqueId());
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            syncNameDisplay(event.getPlayer());
            syncPlayerFinderDefense();
        });
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        clearPlayerFinderDefenseForViewer(event.getPlayer());
        clearPlayerFinderDefenseForTarget(event.getPlayer().getUniqueId());
        removeNameDisplay(event.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            syncNameDisplay(event.getPlayer());
            syncTeamGlow(event.getPlayer());
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                syncTeamGlow(viewer);
            }
            syncPlayerFinderDefense();
        });
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (event.getFrom().getWorld() != to.getWorld()) {
            clearPlayerFinderDefenseForViewer(event.getPlayer());
            clearPlayerFinderDefenseForTarget(event.getPlayer().getUniqueId());
            removeNameDisplay(event.getPlayer().getUniqueId());
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            syncNameDisplay(event.getPlayer());
            syncPlayerFinderDefense();
        });
    }
}
