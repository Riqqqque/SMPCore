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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerVisualListener implements Listener {

    private static final long SYNC_PERIOD_TICKS = 5L;
    private static final int GLOW_REFRESH_TICKS = 40;

    private final SMPCore plugin;
    private final NamespacedKey keyNameHologram;
    private final NamespacedKey keyNameHologramOwner;
    private final Map<UUID, UUID> nameDisplaysByPlayer = new ConcurrentHashMap<>();
    private final Set<UUID> teamGlowViewers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<UUID>> glowingTargetsByViewer = new ConcurrentHashMap<>();
    private BukkitTask task;
    private int glowTickCounter;

    public PlayerVisualListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyNameHologram = new NamespacedKey(plugin, "player_name_hologram");
        this.keyNameHologramOwner = new NamespacedKey(plugin, "player_name_hologram_owner");
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
        for (UUID viewerId : new ArrayList<>(glowingTargetsByViewer.keySet())) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) {
                clearViewerGlow(viewer);
            }
        }
        teamGlowViewers.clear();
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
            syncTeamGlow(player);
            player.sendMessage(MessageUtil.success("Teammate glow enabled. Only you can see it."));
            return;
        }

        teamGlowViewers.remove(playerId);
        clearViewerGlow(player);
        player.sendMessage(MessageUtil.info("Teammate glow disabled."));
    }

    public boolean isTeamGlowEnabled(Player player) {
        return teamGlowViewers.contains(player.getUniqueId());
    }

    private void tickVisuals() {
        syncNameDisplays();
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
        display.text(nameplateText(player));
        VisualRangeUtil.applyHologramRange(display);
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

    private void syncTeamGlow(Player viewer) {
        if (!teamGlowViewers.contains(viewer.getUniqueId())) {
            clearViewerGlow(viewer);
            return;
        }

        TeamManager teams = plugin.getTeamManager();
        if (teams == null || !teams.inTeam(viewer.getUniqueId())) {
            teamGlowViewers.remove(viewer.getUniqueId());
            clearViewerGlow(viewer);
            return;
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
        if (current == null) {
            return;
        }
        for (UUID targetId : current) {
            Player target = Bukkit.getPlayer(targetId);
            if (target != null) {
                clearFakeGlow(viewer, target);
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

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            syncNameDisplay(event.getPlayer());
            syncTeamGlow(event.getPlayer());
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        removeNameDisplay(playerId);
        teamGlowViewers.remove(playerId);
        glowingTargetsByViewer.remove(playerId);
        for (Set<UUID> targets : glowingTargetsByViewer.values()) {
            targets.remove(playerId);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
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
        Bukkit.getScheduler().runTask(plugin, () -> syncNameDisplay(event.getPlayer()));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        removeNameDisplay(event.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            syncNameDisplay(event.getPlayer());
            syncTeamGlow(event.getPlayer());
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                syncTeamGlow(viewer);
            }
        });
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getFrom().getWorld() == event.getTo().getWorld()) {
            return;
        }
        removeNameDisplay(event.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> syncNameDisplay(event.getPlayer()));
    }
}
