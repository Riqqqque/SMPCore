package me.rique.smpcore.util;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Utility helpers for location-related operations.
 */
public final class LocationUtil {

    private LocationUtil() {}

    /**
     * Returns a location directly above the highest solid block at (x, z),
     * with yaw/pitch preserved from the original location.
     */
    public static Location getTopLocation(Location origin) {
        World world = origin.getWorld();
        int x = origin.getBlockX();
        int z = origin.getBlockZ();
        int y = world.getHighestBlockYAt(x, z);
        return new Location(world, x + 0.5, y + 1.0, z + 0.5,
                origin.getYaw(), origin.getPitch());
    }

    /**
     * Converts a Location to a compact string key suitable for map keys and DB storage.
     * Format: "world:x:y:z"
     */
    public static String toKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    /**
     * Serialise a Location (with yaw/pitch) to a storable string.
     * Format: "world:x:y:z:yaw:pitch"
     */
    public static String serialise(Location loc) {
        return loc.getWorld().getName()
                + ":" + loc.getX()
                + ":" + loc.getY()
                + ":" + loc.getZ()
                + ":" + loc.getYaw()
                + ":" + loc.getPitch();
    }

    /**
     * Returns whether two locations are in the same chunk.
     */
    public static boolean sameChunk(Location a, Location b) {
        return a.getWorld().equals(b.getWorld())
                && a.getChunk().getX() == b.getChunk().getX()
                && a.getChunk().getZ() == b.getChunk().getZ();
    }

    /**
     * Returns the block-centred location for a given block position.
     */
    public static Location centreOf(Location blockLoc) {
        return new Location(blockLoc.getWorld(),
                blockLoc.getBlockX() + 0.5,
                blockLoc.getBlockY(),
                blockLoc.getBlockZ() + 0.5);
    }
}
