package me.rique.smpcore.npc;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.List;

public interface ReforgeNpcBridge {
    Entity spawnDwarf(Location location);
    int removeNearestDwarf(Location origin, double radius);
    List<Location> dwarfLocations();
    int refreshLoadedNpcs();
    void shutdown();
}
