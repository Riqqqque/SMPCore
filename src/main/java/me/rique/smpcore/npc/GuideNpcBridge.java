package me.rique.smpcore.npc;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.List;

public interface GuideNpcBridge {
    Entity spawn(GuideNpcManager.GuideNpcType type, Location location);

    int removeNearest(GuideNpcManager.GuideNpcType type, Location origin, double radius);

    List<Location> locations(GuideNpcManager.GuideNpcType type);

    int refreshLoadedNpcs();

    void shutdown();
}
