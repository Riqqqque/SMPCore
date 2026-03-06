package me.rique.smpcore.waystone;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Immutable waystone record anchored at the middle fence block.
 */
public record WaystoneEntry(String name, String world, int x, int y, int z) {

    public String key() {
        return key(world, x, y, z);
    }

    public static String key(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    public Location anchor(World w) {
        return new Location(w, x + 0.5, y, z + 0.5);
    }
}
