package me.rique.smpcore.npc;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.VisualRangeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NpcHologramManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final long UPDATE_INTERVAL_TICKS = 5L;
    private static final long DUPLICATE_CLEANUP_INTERVAL_TICKS = 100L;
    private static final double DEFAULT_OFFSET = 0.38D;
    private static final int NAMEPLATE_LINE_WIDTH = 320;
    private static final String SCOREBOARD_TAG = "smpcore_npc_hologram";

    private final SMPCore plugin;
    private final NamespacedKey keyHologram;
    private final NamespacedKey keyOwner;
    private final Map<String, HologramState> holograms = new ConcurrentHashMap<>();
    private BukkitTask task;
    private boolean listenerRegistered;
    private long duplicateCleanupTicks;

    public NpcHologramManager(SMPCore plugin) {
        this.plugin = plugin;
        this.keyHologram = new NamespacedKey(plugin, "npc_hologram");
        this.keyOwner = new NamespacedKey(plugin, "npc_hologram_owner");
    }

    public void start() {
        if (task != null) {
            return;
        }
        if (!listenerRegistered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
        }
        removePluginHolograms();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, UPDATE_INTERVAL_TICKS);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (HologramState state : holograms.values()) {
            Entity display = Bukkit.getEntity(state.displayId());
            if (display != null) {
                display.remove();
            }
        }
        holograms.clear();
        removePluginHolograms();
    }

    public void show(Entity target, String ownerId, String nameplate) {
        show(target, ownerId, nameplate, DEFAULT_OFFSET);
    }

    public void show(Entity target, String ownerId, String nameplate, double verticalOffset) {
        if (target == null || !target.isValid() || ownerId == null || ownerId.isBlank() || nameplate == null || nameplate.isBlank()) {
            return;
        }

        Component text = MM.deserialize(nameplate).decoration(TextDecoration.ITALIC, false);
        HologramState current = holograms.get(ownerId);
        TextDisplay display = canonicalDisplay(ownerId, current == null ? null : current.displayId(), target.getWorld());
        if (display == null || !display.getWorld().equals(target.getWorld())) {
            if (display != null) {
                display.remove();
            }
            display = spawnDisplay(target, ownerId, text, verticalOffset);
        } else {
            configureDisplay(display, ownerId, text);
        }

        teleportIfNeeded(display, hologramLocation(target, verticalOffset));
        holograms.put(ownerId, new HologramState(target.getUniqueId(), display.getUniqueId(), text, verticalOffset));
    }

    public void hide(String ownerId) {
        if (ownerId == null) {
            return;
        }
        HologramState state = holograms.remove(ownerId);
        if (state != null) removeDisplay(state.displayId());
        removeOwnerDisplays(ownerId, null);
    }

    private void tick() {
        duplicateCleanupTicks += UPDATE_INTERVAL_TICKS;
        boolean cleanDuplicates = duplicateCleanupTicks >= DUPLICATE_CLEANUP_INTERVAL_TICKS;
        if (cleanDuplicates) {
            duplicateCleanupTicks = 0L;
        }
        Iterator<Map.Entry<String, HologramState>> iterator = holograms.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, HologramState> entry = iterator.next();
            HologramState state = entry.getValue();
            Entity target = Bukkit.getEntity(state.targetId());
            if (target == null || !target.isValid() || target.isDead()) {
                removeDisplay(state.displayId());
                iterator.remove();
                continue;
            }

            TextDisplay display = display(state.displayId());
            if (display == null || !display.getWorld().equals(target.getWorld())) {
                removeDisplay(state.displayId());
                display = spawnDisplay(target, entry.getKey(), state.text(), state.verticalOffset());
                entry.setValue(state.withDisplay(display.getUniqueId()));
            }
            if (cleanDuplicates) {
                removeNearbyDuplicates(target, entry.getKey(), display.getUniqueId());
            }
            teleportIfNeeded(display, hologramLocation(target, state.verticalOffset()));
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Entity entity : event.getChunk().getEntities()) {
                if (!(entity instanceof TextDisplay display) || !isPluginHologram(display)) continue;
                String ownerId = display.getPersistentDataContainer().get(keyOwner, PersistentDataType.STRING);
                HologramState state = ownerId == null ? null : holograms.get(ownerId);
                if (state == null || !display.getUniqueId().equals(state.displayId())) display.remove();
            }
        });
    }

    private TextDisplay spawnDisplay(Entity target, String ownerId, Component text, double verticalOffset) {
        World world = target.getWorld();
        return world.spawn(hologramLocation(target, verticalOffset), TextDisplay.class, display -> configureDisplay(display, ownerId, text));
    }

    private void configureDisplay(TextDisplay display, String ownerId, Component text) {
        display.text(text);
        display.setBillboard(Display.Billboard.CENTER);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setShadowed(true);
        display.setSeeThrough(false);
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.setTextOpacity((byte) 255);
        display.setLineWidth(NAMEPLATE_LINE_WIDTH);
        display.setBrightness(new Display.Brightness(15, 15));
        VisualRangeUtil.applyHologramRange(display, 32.0D);
        display.setPersistent(true);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.addScoreboardTag(SCOREBOARD_TAG);
        display.getPersistentDataContainer().set(keyHologram, PersistentDataType.BYTE, (byte) 1);
        display.getPersistentDataContainer().set(keyOwner, PersistentDataType.STRING, ownerId);
    }

    private Location hologramLocation(Entity target, double verticalOffset) {
        double height = Math.max(0.8D, target.getHeight());
        return target.getLocation().clone().add(0.0D, height + verticalOffset, 0.0D);
    }

    private TextDisplay display(UUID id) {
        if (id == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(id);
        return entity instanceof TextDisplay textDisplay && textDisplay.isValid() ? textDisplay : null;
    }

    private void removeDisplay(UUID id) {
        Entity entity = Bukkit.getEntity(id);
        if (entity != null) {
            entity.remove();
        }
    }

    private TextDisplay canonicalDisplay(String ownerId, UUID preferredId, World targetWorld) {
        TextDisplay preferred = display(preferredId);
        if (preferred != null && preferred.getWorld().equals(targetWorld)) {
            return preferred;
        }
        TextDisplay canonical = null;
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay candidate : world.getEntitiesByClass(TextDisplay.class)) {
                if (!ownerId.equals(candidate.getPersistentDataContainer().get(keyOwner, PersistentDataType.STRING))) continue;
                if (canonical == null && candidate.getWorld().equals(targetWorld)) canonical = candidate;
                else if (!candidate.equals(canonical)) candidate.remove();
            }
        }
        return canonical;
    }

    private void removeOwnerDisplays(String ownerId, UUID keepId) {
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay candidate : world.getEntitiesByClass(TextDisplay.class)) {
                if (ownerId.equals(candidate.getPersistentDataContainer().get(keyOwner, PersistentDataType.STRING))
                    && !candidate.getUniqueId().equals(keepId)) candidate.remove();
            }
        }
    }

    private void removeNearbyDuplicates(Entity target, String ownerId, UUID keepId) {
        for (Entity entity : target.getNearbyEntities(3.0D, 6.0D, 3.0D)) {
            if (entity instanceof TextDisplay display
                && ownerId.equals(display.getPersistentDataContainer().get(keyOwner, PersistentDataType.STRING))
                && !display.getUniqueId().equals(keepId)) display.remove();
        }
    }

    private void teleportIfNeeded(TextDisplay display, Location destination) {
        if (!display.getWorld().equals(destination.getWorld()) || display.getLocation().distanceSquared(destination) > 0.000001D) {
            display.teleport(destination);
        }
    }

    private boolean isPluginHologram(TextDisplay display) {
        return display.getPersistentDataContainer().has(keyHologram, PersistentDataType.BYTE)
            || display.getScoreboardTags().contains(SCOREBOARD_TAG);
    }

    private void removePluginHolograms() {
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (isPluginHologram(display)) {
                    display.remove();
                }
            }
        }
    }

    private record HologramState(UUID targetId, UUID displayId, Component text, double verticalOffset) {
        private HologramState withDisplay(UUID newDisplayId) {
            return new HologramState(targetId, newDisplayId, text, verticalOffset);
        }
    }
}
