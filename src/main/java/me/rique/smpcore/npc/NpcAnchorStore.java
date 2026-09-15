package me.rique.smpcore.npc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.UUID;

public final class NpcAnchorStore {

    public static final String NPC_TAG = "smpcore_npc";

    private static final NamespacedKey KEY_WORLD = key("npc_anchor_world");
    private static final NamespacedKey KEY_X = key("npc_anchor_x");
    private static final NamespacedKey KEY_Y = key("npc_anchor_y");
    private static final NamespacedKey KEY_Z = key("npc_anchor_z");
    private static final NamespacedKey KEY_YAW = key("npc_anchor_yaw");
    private static final NamespacedKey KEY_PITCH = key("npc_anchor_pitch");

    private NpcAnchorStore() {
    }

    public static boolean isManagedNpc(Entity entity) {
        return entity != null && hasManagedNpcTag(entity.getScoreboardTags());
    }

    static boolean hasManagedNpcTag(Collection<String> tags) {
        return tags != null && tags.contains(NPC_TAG);
    }

    public static void remember(Entity entity, Location anchor) {
        if (entity == null || anchor == null || anchor.getWorld() == null || !finite(anchor)) {
            return;
        }
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(KEY_WORLD, PersistentDataType.STRING, anchor.getWorld().getUID().toString());
        data.set(KEY_X, PersistentDataType.DOUBLE, anchor.getX());
        data.set(KEY_Y, PersistentDataType.DOUBLE, anchor.getY());
        data.set(KEY_Z, PersistentDataType.DOUBLE, anchor.getZ());
        data.set(KEY_YAW, PersistentDataType.FLOAT, anchor.getYaw());
        data.set(KEY_PITCH, PersistentDataType.FLOAT, anchor.getPitch());
    }

    public static Location read(Entity entity) {
        if (entity == null) {
            return null;
        }
        PersistentDataContainer data = entity.getPersistentDataContainer();
        String worldId = data.get(KEY_WORLD, PersistentDataType.STRING);
        Double x = data.get(KEY_X, PersistentDataType.DOUBLE);
        Double y = data.get(KEY_Y, PersistentDataType.DOUBLE);
        Double z = data.get(KEY_Z, PersistentDataType.DOUBLE);
        Float yaw = data.get(KEY_YAW, PersistentDataType.FLOAT);
        Float pitch = data.get(KEY_PITCH, PersistentDataType.FLOAT);
        if (worldId == null || x == null || y == null || z == null || yaw == null || pitch == null) {
            return null;
        }
        UUID worldUuid;
        try {
            worldUuid = UUID.fromString(worldId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        World world = Bukkit.getWorld(worldUuid);
        Location anchor = new Location(world, x, y, z, yaw, pitch);
        return world != null && finite(anchor) ? anchor : null;
    }

    private static boolean finite(Location location) {
        return Double.isFinite(location.getX())
            && Double.isFinite(location.getY())
            && Double.isFinite(location.getZ())
            && Float.isFinite(location.getYaw())
            && Float.isFinite(location.getPitch());
    }

    private static NamespacedKey key(String value) {
        NamespacedKey key = NamespacedKey.fromString("smpcore:" + value);
        if (key == null) {
            throw new IllegalStateException("Invalid SMPCore NPC anchor key: " + value);
        }
        return key;
    }
}
