package me.rique.smpcore.compat;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Selects the native Java or Geyser-safe body for each familiar per viewer. */
public final class BedrockFamiliarVisibilityManager implements Listener {

    private final SMPCore plugin;
    private final NamespacedKey javaVisualKey;
    private final NamespacedKey bedrockVisualKey;
    private final Set<UUID> javaVisuals = ConcurrentHashMap.newKeySet();
    private final Set<UUID> bedrockVisuals = ConcurrentHashMap.newKeySet();

    public BedrockFamiliarVisibilityManager(SMPCore plugin) {
        this.plugin = plugin;
        this.javaVisualKey = new NamespacedKey(plugin, "familiar_java_visual");
        this.bedrockVisualKey = new NamespacedKey(plugin, "familiar_bedrock_visual");
    }

    public void registerJavaVisual(Entity entity) {
        register(entity, javaVisualKey, bedrockVisualKey, javaVisuals, bedrockVisuals, false);
    }

    public void registerBedrockVisual(Entity entity) {
        register(entity, bedrockVisualKey, javaVisualKey, bedrockVisuals, javaVisuals, true);
    }

    public void unregisterVisual(UUID entityId) {
        if (entityId == null) return;
        javaVisuals.remove(entityId);
        bedrockVisuals.remove(entityId);
    }

    public void shutdown() {
        javaVisuals.clear();
        bedrockVisuals.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTrack(PlayerTrackEntityEvent event) {
        VisualKind kind = visualKind(event.getEntity());
        if (kind == VisualKind.NONE) return;
        boolean bedrockViewer = BedrockCompat.isBedrockPlayer(event.getPlayer());
        if (!shouldShow(bedrockViewer, kind == VisualKind.BEDROCK)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        scheduleViewerSync(event.getPlayer(), 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        scheduleViewerSync(event.getPlayer(), 1L);
    }

    private void scheduleViewerSync(Player joiningPlayer, long delayTicks) {
        UUID playerId = joiningPlayer.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) syncViewer(player);
        }, delayTicks);
    }

    static boolean shouldShow(boolean bedrockViewer, boolean bedrockVisual) {
        return bedrockViewer == bedrockVisual;
    }

    private void register(
        Entity entity,
        NamespacedKey marker,
        NamespacedKey oppositeMarker,
        Set<UUID> destination,
        Set<UUID> oppositeDestination,
        boolean bedrockVisual
    ) {
        if (entity == null || !entity.isValid()) return;
        entity.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().remove(oppositeMarker);
        entity.setVisibleByDefault(false);
        UUID entityId = entity.getUniqueId();
        oppositeDestination.remove(entityId);
        destination.add(entityId);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            setVisibility(viewer, entity, bedrockVisual);
        }
    }

    private void syncViewer(Player viewer) {
        syncViewer(viewer, javaVisuals, false);
        syncViewer(viewer, bedrockVisuals, true);
    }

    private void syncViewer(Player viewer, Set<UUID> visualIds, boolean bedrockVisual) {
        for (UUID entityId : new ArrayList<>(visualIds)) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity == null || !entity.isValid()) {
                visualIds.remove(entityId);
                continue;
            }
            setVisibility(viewer, entity, bedrockVisual);
        }
    }

    private void setVisibility(Player viewer, Entity entity, boolean bedrockVisual) {
        if (shouldShow(BedrockCompat.isBedrockPlayer(viewer), bedrockVisual)) {
            viewer.showEntity(plugin, entity);
        } else {
            viewer.hideEntity(plugin, entity);
        }
    }

    private VisualKind visualKind(Entity entity) {
        if (entity.getPersistentDataContainer().has(bedrockVisualKey, PersistentDataType.BYTE)) {
            return VisualKind.BEDROCK;
        }
        if (entity.getPersistentDataContainer().has(javaVisualKey, PersistentDataType.BYTE)) {
            return VisualKind.JAVA;
        }
        return VisualKind.NONE;
    }

    private enum VisualKind {
        JAVA,
        BEDROCK,
        NONE
    }
}
