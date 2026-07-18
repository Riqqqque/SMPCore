package me.rique.smpcore.player;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.team.TeamManager;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.util.VisualRangeUtil;
import net.kyori.adventure.text.Component;
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
import org.bukkit.persistence.PersistentDataContainer;
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
import java.util.concurrent.atomic.AtomicBoolean;

public final class PlayerVisualListener implements Listener {

    private static final long SYNC_PERIOD_TICKS = 5L;
    private static final long PLAYER_FINDER_PERIOD_TICKS = 20L;
    private static final int GLOW_REFRESH_TICKS = 80;
    private static final int FOLLOWER_TELEPORT_DURATION_TICKS = smoothTeleportDuration(SYNC_PERIOD_TICKS);

    private final SMPCore plugin;
    private final NamespacedKey keyNameHologram;
    private final NamespacedKey keyNameHologramOwner;
    private final NamespacedKey keyTeamGlowMarker;
    private final Map<UUID, UUID> nameDisplaysByPlayer = new ConcurrentHashMap<>();
    private final Set<UUID> teamGlowViewers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<UUID>> glowingTargetsByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> playerFinderHiddenTargetsByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, Component> lastNameplateTexts = new ConcurrentHashMap<>();
    private final AtomicBoolean teamGlowRefreshQueued = new AtomicBoolean();
    private BukkitTask task;
    private int glowTickCounter;
    private int playerFinderTickCounter;

    public PlayerVisualListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyNameHologram = new NamespacedKey(plugin, "player_name_hologram");
        this.keyNameHologramOwner = new NamespacedKey(plugin, "player_name_hologram_owner");
        this.keyTeamGlowMarker = new NamespacedKey(plugin, "team_glow_marker");
    }

    public void start() {
        if (task != null) {
            return;
        }
        removeLegacyTeamGlowMarkers();
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
        removeLegacyTeamGlowMarkers();
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
            player.sendMessage(MessageUtil.success("Teammate outlines enabled. Only you can see them."));
            if (highlighted == 0) {
                player.sendMessage(MessageUtil.info("No visible teammates are online in your current world yet."));
            } else {
                player.sendMessage(MessageUtil.info("Outlined <white>" + highlighted + "</white> teammate" + (highlighted == 1 ? "" : "s") + " through walls."));
            }
            return;
        }

        teamGlowViewers.remove(playerId);
        clearViewerGlow(player);
        player.sendMessage(MessageUtil.info("Teammate outlines disabled."));
    }

    public boolean isTeamGlowEnabled(Player player) {
        return teamGlowViewers.contains(player.getUniqueId());
    }

    public void requestTeamGlowRefresh() {
        if (!teamGlowRefreshQueued.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            teamGlowRefreshQueued.set(false);
            syncEnabledTeamGlowViewers();
        });
    }

    public void refreshPlayerFinderDefense() {
        syncPlayerFinderDefense();
    }

    public void refreshPlayerConcealment(Player target) {
        if (target == null || !target.isOnline()) {
            return;
        }
        syncNameDisplay(target);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) {
                continue;
            }
            syncPlayerEntityVisibility(viewer, target);
            if (teamGlowViewers.contains(viewer.getUniqueId())) {
                syncTeamGlow(viewer);
            }
        }
    }

    public void clearTeleportVisuals(Player player) {
        if (player == null) {
            return;
        }
        clearTransientPlayerVisuals(player, player.getUniqueId(), true);
    }

    public void refreshTeleportVisuals(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        clearTransientPlayerVisuals(player, playerId, true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(playerId);
            if (online == null || !online.isOnline()) {
                return;
            }
            syncNameDisplay(online);
            syncTeamGlow(online);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                syncTeamGlow(viewer);
            }
            syncPlayerFinderDefense();
        });
    }

    private void tickVisuals() {
        syncNameDisplays();
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
        syncEnabledTeamGlowViewers();
    }

    private void syncEnabledTeamGlowViewers() {
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
        ArrayList<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (plugin.getConfigManager() == null || !plugin.getConfigManager().playerFinderDefenseEnabled) {
            clearPlayerFinderDefense();
            syncTabListVisibility(players);
            return;
        }

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
        syncTabListVisibility(players);
    }

    private void syncTabListVisibility(ArrayList<Player> players) {
        for (Player viewer : players) {
            if (viewer == null || !viewer.isOnline()) {
                continue;
            }
            for (Player target : players) {
                if (target == null || viewer.equals(target) || !target.isOnline()) {
                    continue;
                }
                syncPlayerEntityVisibility(viewer, target);
            }
        }
    }

    private void syncPlayerEntityVisibility(Player viewer, Player target) {
        if (viewer == null || target == null || viewer.equals(target) || !viewer.isOnline() || !target.isOnline()) {
            return;
        }
        if (isVanishedFromViewer(viewer, target)) {
            return;
        }

        Set<UUID> finderHidden = playerFinderHiddenTargetsByViewer.get(viewer.getUniqueId());
        boolean shouldHideEntity = isVeilAssassinFullyConcealed(target)
            || (finderHidden != null && finderHidden.contains(target.getUniqueId()));
        if (shouldHideEntity) {
            viewer.hideEntity(plugin, target);
        } else {
            if (!viewer.canSee(target)) {
                viewer.showPlayer(plugin, target);
            }
            if (!viewer.canSee((Entity) target)) {
                viewer.showEntity(plugin, target);
            }
        }

        if (!viewer.isListed(target)) {
            try {
                viewer.listPlayer(target);
            } catch (IllegalStateException ignored) {
                // Visibility changed between the checks; the next sync will retry if appropriate.
            }
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
        if (isVeilAssassinFullyConcealed(target)) {
            viewer.hideEntity(plugin, target);
            if (!viewer.isListed(target)) {
                try {
                    viewer.listPlayer(target);
                } catch (IllegalStateException ignored) {
                    // The regular visibility sync will retry next tick.
                }
            }
            return;
        }
        viewer.showPlayer(plugin, target);
        viewer.showEntity(plugin, target);
    }

    private boolean isVeilAssassinFullyConcealed(Player target) {
        return plugin.getSuperpowerManager() != null
            && plugin.getSuperpowerManager().isVeilAssassinFullyConcealed(target);
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

        followNameDisplay(player, display, target);
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
            textDisplay.setTeleportDuration(FOLLOWER_TELEPORT_DURATION_TICKS);
            textDisplay.setInterpolationDelay(0);
            textDisplay.setInterpolationDuration(0);
            VisualRangeUtil.applyHologramRange(textDisplay);
            textDisplay.getPersistentDataContainer().set(keyNameHologram, PersistentDataType.BYTE, (byte) 1);
            textDisplay.getPersistentDataContainer().set(keyNameHologramOwner, PersistentDataType.STRING, playerId.toString());
        });
        nameDisplaysByPlayer.put(playerId, display.getUniqueId());
        followNameDisplay(player, display, nameLocation(player));
        player.hideEntity(plugin, display);
        return display;
    }

    private void followNameDisplay(Player player, TextDisplay display, Location fallbackLocation) {
        if (display.getVehicle() == player) {
            return;
        }
        if (display.getVehicle() != null) {
            display.leaveVehicle();
        }
        if (display.getWorld() == player.getWorld()) {
            display.setTeleportDuration(0);
            if (display.teleport(player.getLocation()) && player.addPassenger(display)) {
                return;
            }
        }
        display.setTeleportDuration(FOLLOWER_TELEPORT_DURATION_TICKS);
        display.teleport(fallbackLocation);
    }

    static int smoothTeleportDuration(long updatePeriodTicks) {
        if (updatePeriodTicks >= 58L) {
            return 59;
        }
        return (int) Math.max(1L, updatePeriodTicks + 1L);
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
        if (isVeilAssassinFullyConcealed(player)) {
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

    private void clearTransientPlayerVisuals(Player player, UUID playerId) {
        clearTransientPlayerVisuals(player, playerId, false);
    }

    private void clearTransientPlayerVisuals(Player player, UUID playerId, boolean removeOrphanNameDisplays) {
        if (playerId == null) {
            return;
        }
        if (player != null) {
            clearPlayerFinderDefenseForViewer(player);
        }
        clearPlayerFinderDefenseForTarget(playerId);
        removeNameDisplay(playerId, removeOrphanNameDisplays);
    }

    private void removeNameDisplay(UUID playerId) {
        removeNameDisplay(playerId, false);
    }

    private void removeNameDisplay(UUID playerId, boolean removeOrphans) {
        if (playerId == null) {
            return;
        }
        UUID displayId = nameDisplaysByPlayer.remove(playerId);
        lastNameplateTexts.remove(playerId);
        Entity display = displayId == null ? null : Bukkit.getEntity(displayId);
        if (display != null) {
            display.remove();
        }
        if (!removeOrphans) {
            return;
        }
        String owner = playerId.toString();
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay orphan : world.getEntitiesByClass(TextDisplay.class)) {
                PersistentDataContainer pdc = orphan.getPersistentDataContainer();
                if (pdc.has(keyNameHologram, PersistentDataType.BYTE)
                    && owner.equals(pdc.get(keyNameHologramOwner, PersistentDataType.STRING))) {
                    orphan.remove();
                }
            }
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
        }

        Set<UUID> current = glowingTargetsByViewer.computeIfAbsent(viewer.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
        for (UUID targetId : new ArrayList<>(current)) {
            if (!desired.contains(targetId)) {
                Player target = Bukkit.getPlayer(targetId);
                if (target != null) {
                    clearFakeGlow(viewer, target);
                }
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
        return privateTeamGlowEligible(
            teams.sameTeam(viewer.getUniqueId(), target.getUniqueId()),
            isVeilAssassinFullyConcealed(target),
            viewer.canSee(target)
        );
    }

    static boolean privateTeamGlowEligible(boolean sameTeam, boolean concealed, boolean visibleToViewer) {
        return sameTeam && !concealed && visibleToViewer;
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

    private void removeLegacyTeamGlowMarkers() {
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getPersistentDataContainer().has(keyTeamGlowMarker, PersistentDataType.BYTE)) {
                    display.remove();
                }
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            syncNameDisplay(event.getPlayer());
            syncTeamGlow(event.getPlayer());
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
        for (Set<UUID> targets : glowingTargetsByViewer.values()) {
            targets.remove(playerId);
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
