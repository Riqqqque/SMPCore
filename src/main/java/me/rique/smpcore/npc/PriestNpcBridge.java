package me.rique.smpcore.npc;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.List;

public interface PriestNpcBridge {
    Entity spawnPriest(Location location);
    int removeNearestPriest(Location origin, double radius);
    List<Location> priestLocations();
    int refreshLoadedNpcs();
    void shutdown();
}
