package me.rique.smpcore.home;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Immutable value object representing one saved home.
 */
public record HomeEntry(
    String name,
    String world,
    double x, double y, double z,
    float yaw, float pitch
) {
    /** Rebuild a Bukkit Location from this record. Returns null if the world is unloaded. */
    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }
}
